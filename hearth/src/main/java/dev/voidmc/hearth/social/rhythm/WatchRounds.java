package dev.voidmc.hearth.social.rhythm;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.calendar.TownCalendar;
import dev.voidmc.hearth.social.life.Venues;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The night watch (docs/LIVING_WORLD_2.md §2.3), priority 70: from the curfew to dawn the guards on night duty walk their
 * round — from the gate to the square and along the lanes past the doors — a torch in hand and real light where they walk
 * ({@link WatchLights}). They call the hours ("Eleven o'clock, and all's well!") and challenge anyone abroad ("Who goes
 * there?"). A watchman who sees a crime is a guard, and the law's guard behaviour (higher) takes him.
 */
public final class WatchRounds implements NpcService.FolkBehaviour {
	public static final Identifier ID = Hearth.id("rhythm/watch");
	public static final int PRIORITY = 70;
	static final String[] HOURS = {"Twelve", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven"};
	static final long CHALLENGE_TICKS = 20 * 120;

	private final Map<UUID, State> states = new HashMap<>();
	private static final Map<String, Long> CHALLENGED = new HashMap<>();
	/** Watchmen walking now (for the lights' sweep). */
	static final java.util.Set<UUID> WALKING = new java.util.HashSet<>();

	static final class State {
		final List<BlockPos> round;
		int leg;
		long nextMove;
		long nextLook;
		int lastHour = -1;
		boolean curfewSaid;

		State(List<BlockPos> round) {
			this.round = round;
		}
	}

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public int priority() {
		return PRIORITY;
	}

	@Override
	public boolean wants(NpcService.FolkHandle folk) {
		long t0 = RhythmFolk.t0();
		try {
			Resident r = folk.resident();
			if (r.job != JobType.GUARD && r.job != JobType.JAILER) {
				return false;
			}
			Settlement s = folk.settlement();
			if (!Rhythm.config().rhythm().enabled() || !s.embodied() || !folk.awake()) {
				return false;
			}
			if (!RhythmData.of(s.culture()).watch().covers(RhythmFolk.minute(s))) {
				return false;
			}
			NpcService npc = HearthServices.npc();
			return npc == null || npc.onDuty(s, r);
		} finally {
			RhythmFolk.t1(t0);
		}
	}

	/** A watchman's round tonight: the gate, the square, then doors along the lanes in a ring, and back to the gate. */
	static List<BlockPos> round(Settlement s, UUID watchman, long day) {
		BlockPos square = Venues.square(s);
		List<Poi> gates = s.pois(PoiType.GATE);
		long h = TownCalendar.mix(watchman.getLeastSignificantBits() ^ day);
		BlockPos gate = gates.isEmpty() ? square : gates.get((int) Math.floorMod(h, (long) gates.size())).pos();
		List<BlockPos> doors = new ArrayList<>(Venues.positions(s.pois(PoiType.DOOR)));
		// the doors on this watchman's side of town, round the square by angle
		double start = (h >>> 8 & 0xFFFF) / 65536.0 * Math.PI * 2;
		doors.removeIf(d -> d.distSqr(square) > 64 * 64);
		doors.sort(Comparator.comparingDouble(d -> Math.floorMod((long) (1000 * (Math.atan2(d.getZ() - square.getZ(), d.getX() - square.getX()) - start + 20 * Math.PI)),
			(long) (1000 * 2 * Math.PI))));
		List<BlockPos> out = new ArrayList<>();
		out.add(gate);
		out.add(square);
		int step = Math.max(1, doors.size() / 6);
		for (int i = 0; i < doors.size() && out.size() < 8; i += step) {
			out.add(doors.get(i));
		}
		out.add(square);
		return out;
	}

	@Override
	public void start(NpcService.FolkHandle folk) {
		Settlement s = folk.settlement();
		State st = new State(round(s, folk.resident().id, RhythmFolk.today(s)));
		st.leg = (int) Math.floorMod(folk.resident().id.getMostSignificantBits(), (long) st.round.size());
		this.states.put(folk.resident().id, st);
		WALKING.add(folk.resident().id);
		folk.hold(new ItemStack(Items.TORCH));
		st.lastHour = RhythmFolk.minute(s) / 60;
		ServerLevel level = (ServerLevel) folk.entity().level();
		BlockPos to = Venues.standFor(level, new Venues.Venue(st.round.get(st.leg), st.round.get(st.leg), List.of(st.round.get(st.leg)), 2),
			folk.resident().id, st.leg);
		folk.moveTo(to, 0.45);
		st.nextMove = folk.gameTime() + 100;
	}

	@Override
	public boolean tick(NpcService.FolkHandle folk) {
		long t0 = RhythmFolk.t0();
		try {
			return step(folk);
		} finally {
			RhythmFolk.t1(t0);
		}
	}

	private boolean step(NpcService.FolkHandle folk) {
		State st = this.states.get(folk.resident().id);
		if (st == null) {
			return false;
		}
		long t = folk.gameTime();
		if ((t & 63) == 0 && !wants(folk)) {
			return false;
		}
		Settlement s = folk.settlement();
		ServerLevel level = (ServerLevel) folk.entity().level();
		BlockPos target = st.round.get(st.leg);
		if (RhythmFolk.near(folk, target, 3) || t >= st.nextMove + 400) {
			// next leg; a pause to look about first
			st.leg = (st.leg + 1) % st.round.size();
			target = st.round.get(st.leg);
			BlockPos to = Venues.standFor(level, new Venues.Venue(target, target, List.of(target), 2), folk.resident().id, st.leg);
			folk.moveTo(to, 0.45);
			st.nextMove = t + 100;
		} else if (t >= st.nextMove) {
			folk.moveTo(Venues.standFor(level, new Venues.Venue(target, target, List.of(target), 2), folk.resident().id, st.leg), 0.45);
			st.nextMove = t + 100;
		}
		if ((t % 20) == 0) {
			WatchLights.carry(level, s, folk.resident().id, folk.entity().blockPosition().above());
		}
		int minute = RhythmFolk.minute(s);
		int hour = minute / 60;
		if (hour != st.lastHour) {
			st.lastHour = hour;
			String call = HOURS[hour % 12];
			RhythmBarks.say(folk, RhythmBarks.WATCH_CALL, Map.of("hour", call, "hour_lower", call.toLowerCase(java.util.Locale.ROOT)), true);
		}
		RhythmData data = RhythmData.of(s.culture());
		if (!st.curfewSaid && RhythmFolk.within(minute, data.curfew(), data.curfew() + 20)) {
			Player p = RhythmFolk.nearestPlayer(folk, 12, pl -> pl.level().canSeeSky(pl.blockPosition()));
			if (p != null) {
				st.curfewSaid = true;
				folk.lookAt(p);
				RhythmBarks.say(folk, RhythmBarks.CURFEW, Map.of(), false);
			}
		}
		if (t >= st.nextLook) {
			st.nextLook = t + 40;
			Player p = RhythmFolk.nearestPlayer(folk, 10, pl -> pl.isShiftKeyDown() || pl.level().canSeeSky(pl.blockPosition()));
			if (p instanceof ServerPlayer sp && folk.canSee(sp)) {
				String key = s.id() + "/" + sp.getUUID();
				Long last = CHALLENGED.get(key);
				if (last == null || t - last > CHALLENGE_TICKS) {
					CHALLENGED.put(key, t);
					folk.stop();
					folk.lookAt(sp);
					folk.pose("wave", 30);
					RhythmBarks.say(folk, RhythmBarks.CHALLENGE, Map.of("player", sp.getName().getString()), true);
					st.nextMove = t + 60;
					if (CHALLENGED.size() > 2048) {
						CHALLENGED.clear();
					}
				}
			}
		}
		return true;
	}

	@Override
	public void stop(NpcService.FolkHandle folk) {
		this.states.remove(folk.resident().id);
		WALKING.remove(folk.resident().id);
		if (folk.entity().level() instanceof ServerLevel level) {
			WatchLights.douse(level, folk.settlement(), folk.resident().id);
		}
		folk.hold(ItemStack.EMPTY);
	}

	static Set<UUID> walking() {
		return WALKING;
	}
}
