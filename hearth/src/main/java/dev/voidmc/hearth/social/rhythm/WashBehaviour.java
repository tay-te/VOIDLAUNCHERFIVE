package dev.voidmc.hearth.social.rhythm;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.calendar.TownCalendar;
import dev.voidmc.hearth.social.life.Venues;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Wash day (docs/LIVING_WORLD_2.md §2.3), priority 55: on the day after the rest day, from 08:00 to noon, one adult from
 * a quarter of the households (three at least, where there are three) takes the bundle to the town's wash place — the
 * river bank, the lake shore, the well or the fountain, by the culture's liking — kneels at the edge and scrubs, with
 * splashes and the washers' gossip. Without a wash place they scrub in a tub at their own door.
 */
public final class WashBehaviour implements NpcService.FolkBehaviour {
	public static final Identifier ID = Hearth.id("rhythm/wash");
	public static final int PRIORITY = 55;
	/** Trades kept at their post even on wash day. */
	static final java.util.Set<JobType> KEPT = java.util.EnumSet.of(JobType.GUARD, JobType.JAILER, JobType.PRIEST, JobType.LORD, JobType.INNKEEPER,
		JobType.STEWARD, JobType.JUDGE, JobType.REEVE, JobType.MONK);

	private static final Map<Long, Washers> DAYS = new LinkedHashMap<>();
	private final Map<UUID, State> states = new HashMap<>();

	record Washers(long day, int residents, Map<UUID, Integer> order) {
	}

	static final class State {
		final BlockPos bank;
		final BlockPos water;
		long nextMove;
		long nextScrub;

		State(BlockPos bank, BlockPos water) {
			this.bank = bank;
			this.water = water;
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

	/** Pure: today's washers, in order (one a household, from a share of households; at least {@code least}). */
	public static Map<UUID, Integer> pick(Iterable<Resident> residents, long day, double share, int least) {
		Map<Integer, Resident> byHome = new HashMap<>();
		for (Resident r : residents) {
			if (r.age != AgeStage.ADULT || r.home == Resident.NO_PLOT || KEPT.contains(r.job)) {
				continue;
			}
			Resident cur = byHome.get(r.home);
			if (cur == null || TownCalendar.mix(r.id.getLeastSignificantBits() ^ day) < TownCalendar.mix(cur.id.getLeastSignificantBits() ^ day)) {
				byHome.put(r.home, r);
			}
		}
		List<Map.Entry<Integer, Resident>> homes = new ArrayList<>(byHome.entrySet());
		homes.sort(Comparator.comparingDouble(e -> TownCalendar.chance(e.getKey(), day, 55)));
		int want = Math.max(Math.min(least, homes.size()), (int) Math.round(homes.size() * share));
		Map<UUID, Integer> out = new LinkedHashMap<>();
		for (int i = 0; i < want && i < homes.size(); i++) {
			out.put(homes.get(i).getValue().id, i);
		}
		return out;
	}

	static synchronized Map<UUID, Integer> washers(Settlement s, long day) {
		Washers w = DAYS.get(s.id());
		if (w != null && w.day == day && w.residents == s.population()) {
			return w.order;
		}
		RhythmConfig.Wash cfg = Rhythm.config().wash();
		Map<UUID, Integer> order = HearthServices.calendar().washDay(s, day) ? pick(s.residents(), day, cfg.households(), cfg.least()) : Map.of();
		DAYS.put(s.id(), new Washers(day, s.population(), order));
		if (DAYS.size() > 512) {
			DAYS.remove(DAYS.keySet().iterator().next());
		}
		return order;
	}

	/** The homes of today's washers (for the laundry lines). */
	static List<PlotView> homes(Settlement s, long day) {
		List<PlotView> out = new ArrayList<>();
		Map<UUID, Integer> w = washers(s, day);
		for (Resident r : s.residents()) {
			if (w.containsKey(r.id)) {
				PlotView p = s.plot(r.home);
				if (p != null && p.state() == dev.voidmc.hearth.api.PlotState.BUILT) {
					out.add(p);
				}
			}
		}
		return out;
	}

	@Override
	public boolean wants(NpcService.FolkHandle folk) {
		long t0 = RhythmFolk.t0();
		try {
			Settlement s = folk.settlement();
			if (!Rhythm.config().rhythm().enabled() || !Rhythm.config().wash().enabled() || !s.embodied() || !folk.awake()
				|| folk.resident().age != AgeStage.ADULT) {
				return false;
			}
			RhythmData.Wash wash = RhythmData.of(s.culture()).wash();
			if (!RhythmFolk.within(RhythmFolk.minute(s), wash.start(), wash.end())) {
				return false;
			}
			return washers(s, RhythmFolk.today(s)).containsKey(folk.resident().id);
		} finally {
			RhythmFolk.t1(t0);
		}
	}

	@Override
	public void start(NpcService.FolkHandle folk) {
		Settlement s = folk.settlement();
		Integer i = washers(s, RhythmFolk.today(s)).get(folk.resident().id);
		ServerLevel level = (ServerLevel) folk.entity().level();
		List<WashPlaces.Spot> spots = WashPlaces.of(s);
		BlockPos bank;
		BlockPos water;
		if (!spots.isEmpty()) {
			// two or three to a spot, so they talk while they scrub
			WashPlaces.Spot sp = spots.get(Math.floorMod(i == null ? 0 : i / 3, Math.min(spots.size(), 4)));
			bank = Venues.standFor(level, new Venues.Venue(sp.bank(), sp.water(), List.of(sp.bank()), 2), folk.resident().id, 55);
			water = sp.water();
		} else {
			BlockPos door = door(s, folk.resident());
			bank = door == null ? folk.entity().blockPosition() : door;
			water = bank;
		}
		State st = new State(bank, water);
		this.states.put(folk.resident().id, st);
		folk.hold(new ItemStack(Items.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE)));
		folk.moveTo(bank, 0.5);
		st.nextMove = folk.gameTime() + 80;
		st.nextScrub = folk.gameTime() + 40;
	}

	static @Nullable BlockPos door(Settlement s, Resident r) {
		PlotView home = r.home == Resident.NO_PLOT ? null : s.plot(r.home);
		if (home == null) {
			return null;
		}
		List<Poi> doors = Venues.inPlot(s.pois(PoiType.DOOR), home);
		return doors.isEmpty() ? Venues.middle(home.spec()) : doors.get(0).pos();
	}

	@Override
	public boolean tick(NpcService.FolkHandle folk) {
		long t0 = RhythmFolk.t0();
		try {
			State st = this.states.get(folk.resident().id);
			if (st == null) {
				return false;
			}
			long t = folk.gameTime();
			if ((t & 63) == 0 && !wants(folk)) {
				return false;
			}
			if (!RhythmFolk.near(folk, st.bank, 1.8)) {
				if (t >= st.nextMove) {
					folk.moveTo(st.bank, 0.5);
					st.nextMove = t + 100;
				}
				return true;
			}
			folk.stop();
			folk.lookAt(st.water);
			if (t >= st.nextScrub) {
				folk.pose("tend", 70);
				folk.swing();
				ServerLevel level = (ServerLevel) folk.entity().level();
				double x = st.water.getX() + 0.5;
				double y = st.water.getY() + 1.0;
				double z = st.water.getZ() + 0.5;
				level.sendParticles(ParticleTypes.SPLASH, x, y, z, 6, 0.3, 0.05, 0.3, 0.1);
				level.playSound(null, x, y, z, st.water.equals(st.bank) ? SoundEvents.BUCKET_EMPTY : SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 0.35F,
					1.2F + level.getRandom().nextFloat() * 0.3F);
				if (TownCalendar.chance(folk.resident().id.hashCode(), t, 9) < 0.12) {
					RhythmBarks.say(folk, RhythmBarks.WASHER, Map.of(), false);
				}
				st.nextScrub = t + 60 + Math.floorMod(TownCalendar.mix(t ^ folk.resident().id.hashCode()), 40L);
			}
			return true;
		} finally {
			RhythmFolk.t1(t0);
		}
	}

	@Override
	public void stop(NpcService.FolkHandle folk) {
		if (this.states.remove(folk.resident().id) != null) {
			folk.pose("none", 0);
			folk.hold(ItemStack.EMPTY);
		}
	}

	static synchronized void clear() {
		DAYS.clear();
	}
}
