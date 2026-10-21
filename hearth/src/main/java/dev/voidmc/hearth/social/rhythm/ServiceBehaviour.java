package dev.voidmc.hearth.social.rhythm;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.folk.Music;
import dev.voidmc.hearth.social.life.Venues;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Mass on the rest day (docs/LIVING_WORLD_2.md §2.3), priority 85: the congregation walks to the chapel when the bell
 * peals, sits in the pews (the lord and the elders in front), stands for the chant; the priest at the altar says the
 * sermon a line at a time and leads the chant; afterwards they linger at the door a quarter of an hour, and the priest
 * greets the players who come often. Who comes is {@link Service#attending}.
 */
public final class ServiceBehaviour implements NpcService.FolkBehaviour {
	public static final Identifier ID = Hearth.id("rhythm/service");
	public static final int PRIORITY = 85;

	private final Map<UUID, State> states = new HashMap<>();

	static final class State {
		final boolean officiant;
		final @Nullable Poi seat;
		final BlockPos stand;
		final BlockPos porch;
		final BlockPos altar;
		boolean seated;
		long nextMove;
		long nextLine;
		int beat;

		State(boolean officiant, @Nullable Poi seat, BlockPos stand, BlockPos porch, BlockPos altar) {
			this.officiant = officiant;
			this.seat = seat;
			this.stand = stand;
			this.porch = porch;
			this.altar = altar;
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

	static Service.Part partNow(Settlement s) {
		return Service.part(RhythmData.of(s.culture()).service(), RhythmFolk.minute(s));
	}

	@Override
	public boolean wants(NpcService.FolkHandle folk) {
		long t0 = RhythmFolk.t0();
		try {
			Settlement s = folk.settlement();
			if (!Rhythm.config().rhythm().enabled() || !Rhythm.config().service().enabled() || !s.embodied() || !folk.awake()) {
				return false;
			}
			if (partNow(s) == Service.Part.NONE) {
				return false;
			}
			Service.Day d = Service.day(s, RhythmFolk.today(s));
			return d.church() != null && (d.attending().contains(folk.resident().id) || folk.resident().id.equals(d.officiant()));
		} finally {
			RhythmFolk.t1(t0);
		}
	}

	@Override
	public void start(NpcService.FolkHandle folk) {
		Settlement s = folk.settlement();
		Service.Day d = Service.day(s, RhythmFolk.today(s));
		Service.Church c = d.church();
		if (c == null) {
			return;
		}
		ServerLevel level = (ServerLevel) folk.entity().level();
		Resident r = folk.resident();
		boolean leads = r.id.equals(d.officiant());
		Integer seatIndex = leads ? null : takeSeat(s, d, r);
		Poi seat = seatIndex == null ? null : c.seats().get(seatIndex);
		BlockPos stand;
		if (leads) {
			BlockPos ok = Venues.standable(level, c.altar());
			stand = ok == null ? c.altar() : ok;
		} else if (seat != null) {
			stand = seat.pos();
		} else {
			// no pew left (or an open-air blessing): stand in the nave or round the square, facing the altar
			BlockPos nave = c.plot() == null ? c.altar() : Venues.middle(c.plot().spec()).atY(c.altar().getY());
			stand = Venues.standFor(level, new Venues.Venue(nave, c.altar(), List.of(nave), c.open() ? 5 : 3), r.id, 85);
		}
		BlockPos porch = Venues.standFor(level, new Venues.Venue(c.door(), c.door(), List.of(c.door()), 4), r.id, 22);
		State st = new State(leads, seat, stand, porch, c.altar());
		this.states.put(r.id, st);
		folk.hold(leads ? new ItemStack(Items.BOOK) : r.job == JobType.LORD ? ItemStack.EMPTY : ItemStack.EMPTY);
		RhythmFolk.walk(folk, stand, 0.55);
		st.nextMove = folk.gameTime() + 60;
		st.nextLine = folk.gameTime() + 100 + Math.floorMod(r.id.hashCode(), 60);
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
		Settlement s = folk.settlement();
		Service.Part part = partNow(s);
		long t = folk.gameTime();
		if (part == Service.Part.NONE || (t & 63) == 0 && !wants(folk)) {
			return false;
		}
		ServerLevel level = (ServerLevel) folk.entity().level();
		RhythmData data = RhythmData.of(s.culture());
		BlockPos goal = part == Service.Part.PORCH ? st.porch : st.stand;
		if (part == Service.Part.PORCH && st.seated) {
			folk.standUp();
			st.seated = false;
		}
		if (!st.seated && !RhythmFolk.near(folk, goal, st.seat != null && part != Service.Part.PORCH ? 1.6 : 2.2)) {
			if (t >= st.nextMove) {
				RhythmFolk.walk(folk, goal, part == Service.Part.PORCH ? 0.45 : 0.55);
				st.nextMove = t + 80;
			}
			return true;
		}
		switch (part) {
			case GATHER, SERMON -> {
				if (st.officiant) {
					folk.stop();
					folk.lookAt(st.porch);
					if (part == Service.Part.SERMON && t >= st.nextLine) {
						folk.pose("talk", 60);
						folk.swing();
						RhythmBarks.say(folk, RhythmBarks.SERMON, Map.of("service", serviceName(data)), true);
						st.nextLine = t + Rhythm.config().service().sermonTicks();
					}
				} else if (st.seat != null) {
					if (!st.seated) {
						st.seated = folk.sit(st.seat.pos(), st.seat.facing());
						if (!st.seated) {
							folk.lookAt(st.altar);
						}
					}
				} else {
					folk.stop();
					folk.lookAt(st.altar);
				}
			}
			case CHANT -> {
				if (st.seated) {
					folk.standUp();
					st.seated = false;
				}
				folk.stop();
				folk.lookAt(st.officiant ? st.porch : st.altar);
				if ((t & 15) == 0) {
					folk.pose("pray", 20);
				}
				if (st.officiant && t % 6 == 0) {
					chant(level, st.altar, data, st.beat++);
				}
			}
			case PORCH -> {
				folk.stop();
				if (t >= st.nextLine) {
					st.nextLine = t + 160 + Math.floorMod(folk.resident().id.hashCode() + t, 200L);
					Player near = RhythmFolk.nearestPlayer(folk, 8, p -> true);
					if (st.officiant && near instanceof ServerPlayer sp && Rhythm.regular(s, folk.resident(), sp)) {
						folk.lookAt(sp);
						folk.pose("wave", 30);
						RhythmBarks.say(folk, RhythmBarks.REGULAR, Map.of("player", sp.getName().getString(), "service", serviceName(data)), false);
					} else {
						folk.pose("talk", 40);
						RhythmBarks.say(folk, RhythmBarks.PORCH, Map.of("service", serviceName(data)), false);
					}
				}
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	/** One note of the culture's chant at the altar; a rest every so often. */
	static void chant(ServerLevel level, BlockPos altar, RhythmData data, int beat) {
		List<Integer> notes = data.service().chant();
		if (notes.isEmpty()) {
			return;
		}
		int note = notes.get(Math.floorMod(beat, notes.size()));
		if (note < 0) {
			return;
		}
		double x = altar.getX() + 0.5;
		double y = altar.getY() + 1.5;
		double z = altar.getZ() + 0.5;
		level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_FLUTE, SoundSource.RECORDS, 1.2F, Music.pitch(note));
		level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_FLUTE, SoundSource.RECORDS, 0.8F, Music.pitch(Math.max(0, note - 12)));
		if (beat % 4 == 0) {
			level.sendParticles(ParticleTypes.NOTE, x, y + 1.0, z, 0, note / 24.0, 0, 0, 1);
		}
	}

	static String serviceName(RhythmData data) {
		return switch (data.service().name()) {
			case "blessing" -> "the blessing";
			case "prayers" -> "evening prayers";
			case "offering" -> "the offering";
			default -> data.service().name();
		};
	}

	/** Town → {day, seat index → who sits there}: the pews as folk come in. */
	private static final Map<Long, Object[]> PEWS = new HashMap<>();

	/**
	 * A pew for one coming in: their own (the lord and the elders in front) if it is free, else the first free one not kept
	 * for someone of the town who is here and has not sat yet. Null: the pews are full, they stand.
	 */
	@SuppressWarnings("unchecked")
	static synchronized @Nullable Integer takeSeat(Settlement s, Service.Day d, Resident r) {
		int n = d.church() == null ? 0 : d.church().seats().size();
		if (n == 0) {
			return null;
		}
		Object[] cur = PEWS.get(s.id());
		if (cur == null || (long) cur[0] != d.day()) {
			cur = new Object[] {d.day(), new HashMap<Integer, UUID>()};
			PEWS.put(s.id(), cur);
		}
		Map<Integer, UUID> taken = (Map<Integer, UUID>) cur[1];
		for (Map.Entry<Integer, UUID> e : taken.entrySet()) {
			if (e.getValue().equals(r.id)) {
				return e.getKey();
			}
		}
		Integer own = d.seatOf().get(r.id);
		if (own != null && !taken.containsKey(own)) {
			taken.put(own, r.id);
			return own;
		}
		Map<Integer, UUID> keptFor = new HashMap<>();
		for (Map.Entry<UUID, Integer> e : d.seatOf().entrySet()) {
			keptFor.put(e.getValue(), e.getKey());
		}
		for (int i = 0; i < n; i++) {
			UUID holder = keptFor.get(i);
			if (!taken.containsKey(i) && (holder == null || !s.isResidentEmbodied(holder))) {
				taken.put(i, r.id);
				return i;
			}
		}
		return null;
	}

	static synchronized void leaveSeat(Settlement s, UUID who) {
		Object[] cur = PEWS.get(s.id());
		if (cur != null) {
			@SuppressWarnings("unchecked")
			Map<Integer, UUID> taken = (Map<Integer, UUID>) cur[1];
			taken.values().removeIf(who::equals);
		}
	}

	static synchronized void clearPews() {
		PEWS.clear();
	}

	@Override
	public void stop(NpcService.FolkHandle folk) {
		State st = this.states.remove(folk.resident().id);
		leaveSeat(folk.settlement(), folk.resident().id);
		if (st != null) {
			if (st.seated) {
				folk.standUp();
			}
			folk.pose("none", 0);
			folk.hold(ItemStack.EMPTY);
		}
	}
}
