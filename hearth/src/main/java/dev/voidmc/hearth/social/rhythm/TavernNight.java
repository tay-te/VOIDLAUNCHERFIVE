package dev.voidmc.hearth.social.rhythm;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.FolkFacts;
import dev.voidmc.hearth.social.calendar.TownCalendar;
import dev.voidmc.hearth.social.folk.Music;
import dev.voidmc.hearth.social.life.Venues;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Tavern nights (docs/LIVING_WORLD_2.md §2.3), from 18:30 to 23:30 at the inns and taverns: tonight's drinkers
 * ({@link RhythmSchedule#drinkers}) sit at the tables with their mugs, drink, toast and cheer when a tune ends; two of them
 * arm-wrestle now and then; at closing they weave home. Priority 45 for the drinkers; {@link Hands} (55) puts the musician
 * at their instrument and sends the innkeeper round the tables, who rings the hand bell at closing. A round bought by a
 * player ({@link #raiseMugs}) has every drinker in that tavern raise a mug to them.
 */
public final class TavernNight implements NpcService.FolkBehaviour {
	public static final Identifier ID = Hearth.id("rhythm/tavern_night");
	public static final int PRIORITY = 45;
	/** Minutes after closing the last drinkers take to get home. */
	static final int HOMEWARD = 20;

	/** Tavern (plot id) → the game time its tune last ended. */
	static final Map<Long, Long> TUNE_ENDED = new HashMap<>();
	/** Tavern (settlement, plot) → a round bought: the game time and the player's name. */
	static final Map<Long, Round> ROUNDS = new HashMap<>();

	record Round(long time, String player) {
	}

	private final Map<UUID, State> states = new HashMap<>();

	enum Mode {
		WALK, SEATED, HOMEWARD
	}

	static final class State {
		final long tavern;
		@Nullable Poi seat;
		BlockPos stand;
		/** The tavern's seats in the order they are filled (a free one is looked for there when the own seat is taken). */
		List<Poi> seats = List.of();
		int retries;
		final BlockPos table;
		final @Nullable BlockPos home;
		final boolean wrestler;
		final boolean musician;
		Mode mode = Mode.WALK;
		long nextMove;
		long nextAct;
		long cheeredAt;
		long toastedAt;
		int beat;
		int sway;
		boolean sat;
		@Nullable BlockPos lastPos;

		State(long tavern, @Nullable Poi seat, BlockPos stand, BlockPos table, @Nullable BlockPos home, boolean wrestler, boolean musician) {
			this.tavern = tavern;
			this.seat = seat;
			this.stand = stand;
			this.table = table;
			this.home = home;
			this.wrestler = wrestler;
			this.musician = musician;
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

	static long key(Settlement s, PlotView tavern) {
		return s.id() * 4096 + tavern.id();
	}

	/** Whether the tavern is open now, or its last drinkers are still on their way home. */
	static boolean open(RhythmData.Tavern t, int minute, boolean homeward) {
		int close = homeward ? (t.close() + HOMEWARD) % (24 * 60) : t.close();
		return RhythmFolk.within(minute, t.open(), close);
	}

	@Override
	public boolean wants(NpcService.FolkHandle folk) {
		long t0 = RhythmFolk.t0();
		try {
			Settlement s = folk.settlement();
			if (!Rhythm.config().rhythm().enabled() || !Rhythm.config().tavern().enabled() || !s.embodied() || !folk.awake()) {
				return false;
			}
			if (!open(RhythmData.of(s.culture()).tavern(), RhythmFolk.minute(s), true)) {
				return false;
			}
			return RhythmSchedule.drinkers(s, RhythmFolk.today(s)).contains(folk.resident().id) && !RhythmSchedule.taverns(s).isEmpty();
		} finally {
			RhythmFolk.t1(t0);
		}
	}

	/** Tonight's drinkers in a settlement's taverns, in a fixed order: who sits where. */
	static List<Resident> order(Settlement s, long day) {
		List<Resident> out = new ArrayList<>();
		java.util.Set<UUID> d = RhythmSchedule.drinkers(s, day);
		for (Resident r : s.residents()) {
			if (d.contains(r.id)) {
				out.add(r);
			}
		}
		out.sort(Comparator.comparingLong(r -> TownCalendar.mix(r.id.getLeastSignificantBits() ^ day)));
		return out;
	}

	@Override
	public void start(NpcService.FolkHandle folk) {
		Settlement s = folk.settlement();
		long day = RhythmFolk.today(s);
		List<PlotView> taverns = RhythmSchedule.taverns(s);
		if (taverns.isEmpty()) {
			return;
		}
		List<Resident> order = order(s, day);
		// (by id: the brain's resident need not be the very object in the settlement's list)
		int i = 0;
		for (int k = 0; k < order.size(); k++) {
			if (order.get(k).id.equals(folk.resident().id)) {
				i = k;
				break;
			}
		}
		// each to the tavern nearest home; seats by the order of the night among those who go there
		PlotView tavern = nearest(s, taverns, folk.resident());
		int nth = 0;
		for (int k = 0; k < i; k++) {
			if (nearest(s, taverns, order.get(k)) == tavern) {
				nth++;
			}
		}
		List<Poi> tables = Venues.inPlot(s.pois(PoiType.TABLE), tavern);
		List<Poi> seats = seatOrder(Venues.inPlot(s.pois(PoiType.SEAT), tavern), tables);
		ServerLevel level = (ServerLevel) folk.entity().level();
		// the first seat in the tavern's order that no drinker here now is bound for (those who drink tonight but are not
		// about take none: the seats go to whoever is in the room)
		Poi seat = null;
		for (Poi p : seats) {
			if (!claimed(p, folk.resident().id)) {
				seat = p;
				break;
			}
		}
		BlockPos table = tables.isEmpty() ? Venues.middle(tavern.spec()) : Venues.nearest(Venues.positions(tables), seat != null ? seat.pos()
			: Venues.middle(tavern.spec()));
		BlockPos stand = seat != null ? seat.pos() : Venues.standFor(level, new Venues.Venue(table, table, List.of(table), 2), folk.resident().id, 45);
		BlockPos home = WashBehaviour.door(s, folk.resident());
		// the first two at the first table of a tavern arm-wrestle, one night in three (seats go round the tables: the
		// first table's second seat is the one after a seat at every table)
		boolean wrestler = (nth == 0 || nth == Math.max(1, Math.min(tables.size(), seats.size() - 1))) && Math.floorMod(TownCalendar.mix(day ^ tavern.id()), 3L) == 0;
		// no entertainer of the town's own: the first drinker in (the cheerful before the rest) takes up the instrument
		boolean musician = Rhythm.musician(s, tavern) == null && claimTune(key(s, tavern), day, folk.resident(),
			nth == 0 || FolkFacts.has(folk.resident(), "cheerful") || FolkFacts.has(folk.resident(), "sociable"));
		State st = new State(key(s, tavern), musician ? null : seat, musician ? Venues.standFor(level, new Venues.Venue(table, table, List.of(table), 2),
			folk.resident().id, 7) : stand, table, home, wrestler && !musician, musician);
		st.seats = seats;
		this.states.put(folk.resident().id, st);
		folk.hold(new ItemStack(Items.HONEY_BOTTLE));
		RhythmFolk.walk(folk, st.stand, 0.6);
		st.nextMove = folk.gameTime() + 60;
		st.nextAct = folk.gameTime() + 100 + Math.floorMod(folk.resident().id.hashCode(), 100);
	}

	/**
	 * A tavern's seats in the order the night fills them: the first seat at each table, then the second at each, and so
	 * on (nearest the table first), so two drinkers already sit at two tables (docs/LIVING_WORLD_2.md §2.8: two tables in
	 * use). Seats with no table come last.
	 */
	static List<Poi> seatOrder(List<Poi> seats, List<Poi> tables) {
		if (tables.isEmpty() || seats.isEmpty()) {
			return seats;
		}
		List<List<Poi>> byTable = new ArrayList<>();
		for (int k = 0; k < tables.size(); k++) {
			byTable.add(new ArrayList<>());
		}
		for (Poi seat : seats) {
			int best = 0;
			double bd = Double.MAX_VALUE;
			for (int k = 0; k < tables.size(); k++) {
				double d = seat.pos().distSqr(tables.get(k).pos());
				if (d < bd) {
					bd = d;
					best = k;
				}
			}
			byTable.get(best).add(seat);
		}
		for (int k = 0; k < tables.size(); k++) {
			BlockPos t = tables.get(k).pos();
			byTable.get(k).sort(Comparator.comparingDouble(p -> p.pos().distSqr(t)));
		}
		List<Poi> out = new ArrayList<>(seats.size());
		for (int round = 0; out.size() < seats.size(); round++) {
			for (List<Poi> g : byTable) {
				if (round < g.size()) {
					out.add(g.get(round));
				}
			}
		}
		return out;
	}

	/** Whether another drinker of the night is bound for (or sits in) this seat. */
	private boolean claimed(Poi p, UUID who) {
		for (Map.Entry<UUID, State> e : this.states.entrySet()) {
			if (!e.getKey().equals(who) && e.getValue().seat != null && e.getValue().seat.pos().equals(p.pos())) {
				return true;
			}
		}
		return false;
	}

	/** Drinkers tonight, for the tour: how many are seated, and how many have a seat to go to. */
	public int[] seatedAndBound() {
		int sat = 0;
		int bound = 0;
		for (State st : this.states.values()) {
			if (st.sat) {
				sat++;
			}
			if (st.seat != null) {
				bound++;
			}
		}
		return new int[]{sat, bound, this.states.size()};
	}

	/** The first seat of the tavern nobody sits in and no other drinker is bound for, or null. */
	private @Nullable Poi freeSeat(ServerLevel level, UUID who, State st) {
		for (Poi p : st.seats) {
			if (p == st.seat) {
				continue;
			}
			boolean claimed = false;
			for (Map.Entry<UUID, State> e : this.states.entrySet()) {
				if (!e.getKey().equals(who) && e.getValue().seat != null && e.getValue().seat.pos().equals(p.pos())) {
					claimed = true;
					break;
				}
			}
			if (claimed || !level.isLoaded(p.pos())) {
				continue;
			}
			if (level.getEntitiesOfClass(dev.voidmc.hearth.npc.entity.Folk.class, new net.minecraft.world.phys.AABB(p.pos()).inflate(0.2),
				o -> o.folkPose() == dev.voidmc.hearth.npc.entity.FolkPose.SIT).isEmpty()) {
				return p;
			}
		}
		return null;
	}

	static PlotView nearest(Settlement s, List<PlotView> taverns, Resident r) {
		BlockPos home = WashBehaviour.door(s, r);
		BlockPos from = home != null ? home : s.center();
		PlotView best = taverns.get(0);
		double bd = Double.MAX_VALUE;
		for (PlotView p : taverns) {
			double d = Venues.middle(p.spec()).distSqr(from);
			if (d < bd) {
				bd = d;
				best = p;
			}
		}
		return best;
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
		long t = folk.gameTime();
		RhythmData.Tavern hours = RhythmData.of(s.culture()).tavern();
		int minute = RhythmFolk.minute(s);
		if ((t & 63) == 0 && !wants(folk)) {
			return false;
		}
		if (!open(hours, minute, false) && st.mode != Mode.HOMEWARD) {
			if (st.sat) {
				folk.standUp();
				st.sat = false;
			}
			st.mode = Mode.HOMEWARD;
			st.nextMove = 0;
			if (TownCalendar.chance(folk.resident().id.hashCode(), t, 4) < 0.3) {
				RhythmBarks.say(folk, RhythmBarks.HOMEWARD, Map.of(), false);
			}
		}
		ServerLevel level = (ServerLevel) folk.entity().level();
		switch (st.mode) {
			case WALK -> {
				// stuck a few blocks short (a bench the path will not end on, a crowded doorway): the last steps to the seat
				BlockPos here = folk.entity().blockPosition();
				boolean stuck = false;
				if (t >= st.nextMove) {
					// checked every walk order (80 ticks): no block gained since the last one
					stuck = here.equals(st.lastPos) && RhythmFolk.near(folk, st.stand, 6);
					st.lastPos = here;
				}
				if (stuck || RhythmFolk.near(folk, st.stand, st.seat != null ? 1.6 : 2.0)) {
					Poi other;
					if (st.seat != null && folk.sit(st.seat.pos(), st.seat.facing())) {
						st.mode = Mode.SEATED;
						st.sat = true;
					} else if (!st.musician && st.retries < 3 && (other = freeSeat(level, folk.resident().id, st)) != null) {
						// somebody is in the seat: the next free one in the tavern
						st.retries++;
						st.seat = other;
						st.stand = other.pos();
						st.lastPos = null;
						RhythmFolk.walk(folk, st.stand, 0.6);
						st.nextMove = t + 80;
					} else {
						folk.stop();
						st.mode = Mode.SEATED;
					}
				} else if (t >= st.nextMove) {
					RhythmFolk.walk(folk, st.stand, 0.6);
					st.nextMove = t + 80;
				}
			}
			case SEATED -> seated(folk, st, level, s, t);
			case HOMEWARD -> {
				// the last drinkers weave home: a step to one side, a step to the other
				if (st.home == null || RhythmFolk.near(folk, st.home, 3)) {
					return false;
				}
				if (t >= st.nextMove) {
					st.sway++;
					BlockPos here = folk.entity().blockPosition();
					int dx = st.home.getX() - here.getX();
					int dz = st.home.getZ() - here.getZ();
					double len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
					int stride = (int) Math.min(5, len);
					int side = st.sway % 2 == 0 ? 1 : -1;
					BlockPos next = here.offset((int) Math.round(dx / len * stride - dz / len * side * 1.3), 0,
						(int) Math.round(dz / len * stride + dx / len * side * 1.3));
					BlockPos ok = len < 6 ? st.home : Venues.standable(level, next);
					RhythmFolk.walk(folk, ok == null ? st.home : ok, 0.42);
					st.nextMove = t + 40;
				}
			}
		}
		return true;
	}

	private static void seated(NpcService.FolkHandle folk, State st, ServerLevel level, Settlement s, long t) {
		if (st.seat == null) {
			folk.lookAt(st.table);
		}
		if (st.musician) {
			if (t % 5 == 0) {
				play(level, folk, s, st);
			}
			return;
		}
		Round round = ROUNDS.get(st.tavern);
		if (round != null && round.time() > st.toastedAt && t - round.time() < 200) {
			st.toastedAt = round.time();
			folk.swing();
			pose(folk, st, "cheer", 40);
			RhythmBarks.say(folk, RhythmBarks.ROUND, Map.of("player", round.player()), true);
			return;
		}
		Long ended = TUNE_ENDED.get(st.tavern);
		if (ended != null && ended > st.cheeredAt && t - ended < 60) {
			st.cheeredAt = ended;
			pose(folk, st, "cheer", 30);
			folk.swing();
			if (TownCalendar.chance(folk.resident().id.hashCode(), t, 6) < 0.25) {
				RhythmBarks.say(folk, RhythmBarks.CHEER, Map.of(), false);
			}
			return;
		}
		if (t < st.nextAct) {
			return;
		}
		double roll = TownCalendar.chance(folk.resident().id.hashCode(), t, 45);
		if (st.wrestler && roll < 0.35) {
			pose(folk, st, "craft", 160);
			level.playSound(null, st.table, SoundEvents.WOOD_HIT, SoundSource.NEUTRAL, 0.4F, 0.8F);
			RhythmBarks.say(folk, RhythmBarks.ARM_WRESTLE, Map.of(), false);
		} else if (roll < 0.6) {
			folk.swing(); // a pull at the mug
			level.playSound(null, folk.entity().blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.NEUTRAL, 0.35F, 1.0F);
		} else if (roll < 0.8) {
			pose(folk, st, "talk", 40);
			RhythmBarks.say(folk, RhythmBarks.TOAST, Map.of("lord", Rhythm.lordName(s)), false);
		}
		st.nextAct = t + 160 + Math.floorMod(TownCalendar.mix(t ^ folk.resident().id.hashCode()), 220L);
	}

	/** A gesture, unless they sit: a seated drinker keeps their seat (a raised mug is the swing). */
	private static void pose(NpcService.FolkHandle folk, State st, String pose, int ticks) {
		if (!st.sat) {
			folk.pose(pose, ticks);
		}
	}

	/** The tune of the night: a beat every five ticks; a pause and a cheer between tunes. */
	static void play(ServerLevel level, NpcService.FolkHandle folk, Settlement s, State st) {
		int len = 48;
		int cycle = len + 10;
		int b = Math.floorMod(st.beat++, cycle);
		if (b >= len) {
			if (b == len) {
				TUNE_ENDED.put(st.tavern, folk.gameTime());
				folk.pose("wave", 20);
			}
			return;
		}
		int tune = (int) Math.floorMod(TownCalendar.mix(st.tavern + st.beat / cycle), 4L);
		RhythmData.Tavern t = RhythmData.of(s.culture()).tavern();
		Music.play(level, folk.entity().position(), s.culture(), tune, b, t.instrument());
		if (b % 8 == 0) {
			folk.swing();
		}
	}

	/** A player bought a round in this tavern: its drinkers raise their mugs to them. */
	static void raiseMugs(Settlement s, PlotView tavern, String player, long gameTime) {
		ROUNDS.put(key(s, tavern), new Round(gameTime, player));
	}

	/** Tavern → {day, the drinker playing tonight}: one musician a tavern, the first to arrive. */
	static final Map<Long, Object[]> PLAYING = new HashMap<>();

	static boolean claimTune(long tavern, long day, Resident r, boolean keen) {
		Object[] cur = PLAYING.get(tavern);
		if (cur != null && (long) cur[0] == day) {
			return r.id.equals(cur[1]);
		}
		if (!keen && Math.floorMod(r.id.getLeastSignificantBits(), 2L) == 0) {
			return false; // the shy half leave it to someone else, unless nobody comes who will
		}
		PLAYING.put(tavern, new Object[] {day, r.id});
		return true;
	}

	static void releaseTune(long tavern, UUID who) {
		Object[] cur = PLAYING.get(tavern);
		if (cur != null && who.equals(cur[1])) {
			PLAYING.remove(tavern);
		}
	}

	@Override
	public void stop(NpcService.FolkHandle folk) {
		State st = this.states.remove(folk.resident().id);
		if (st != null) {
			if (st.musician) {
				releaseTune(st.tavern, folk.resident().id);
			}
			if (st.sat) {
				folk.standUp();
			}
			folk.pose("none", 0);
			folk.hold(ItemStack.EMPTY);
		}
	}

	/**
	 * The innkeeper going round the tables and ringing the hand bell at closing, and the town's entertainer at their
	 * instrument (priority 55: over their own evening's work).
	 */
	public static final class Hands implements NpcService.FolkBehaviour {
		public static final Identifier ID = Hearth.id("rhythm/tavern_hands");
		public static final int PRIORITY = 55;
		private final Map<UUID, State> states = new HashMap<>();
		private final Map<UUID, Long> rang = new HashMap<>();

		@Override
		public Identifier id() {
			return ID;
		}

		@Override
		public int priority() {
			return PRIORITY;
		}

		static @Nullable PlotView tavernOf(Settlement s, Resident r) {
			List<PlotView> taverns = RhythmSchedule.taverns(s);
			if (taverns.isEmpty()) {
				return null;
			}
			for (PlotView p : taverns) {
				if (p.id() == r.work) {
					return p;
				}
			}
			return r.job == JobType.ENTERTAINER ? taverns.get((int) Math.floorMod(r.id.getLeastSignificantBits(), (long) taverns.size())) : null;
		}

		@Override
		public boolean wants(NpcService.FolkHandle folk) {
			long t0 = RhythmFolk.t0();
			try {
				Resident r = folk.resident();
				if (r.job != JobType.INNKEEPER && r.job != JobType.ENTERTAINER || r.age == AgeStage.CHILD) {
					return false;
				}
				Settlement s = folk.settlement();
				if (!Rhythm.config().rhythm().enabled() || !Rhythm.config().tavern().enabled() || !s.embodied() || !folk.awake()) {
					return false;
				}
				if (!open(RhythmData.of(s.culture()).tavern(), RhythmFolk.minute(s), false)) {
					return false;
				}
				return tavernOf(s, r) != null && !RhythmSchedule.drinkers(s, RhythmFolk.today(s)).isEmpty();
			} finally {
				RhythmFolk.t1(t0);
			}
		}

		@Override
		public void start(NpcService.FolkHandle folk) {
			Settlement s = folk.settlement();
			PlotView tavern = tavernOf(s, folk.resident());
			if (tavern == null) {
				return;
			}
			ServerLevel level = (ServerLevel) folk.entity().level();
			List<Poi> tables = Venues.inPlot(s.pois(PoiType.TABLE), tavern);
			BlockPos table = tables.isEmpty() ? Venues.middle(tavern.spec()) : tables.get(0).pos();
			boolean musician = folk.resident().job == JobType.ENTERTAINER;
			List<Poi> stages = Venues.inPlot(s.pois(PoiType.STAGE), tavern);
			BlockPos stand = musician && !stages.isEmpty() ? stages.get(0).pos()
				: Venues.standFor(level, new Venues.Venue(table, table, List.of(table), 2), folk.resident().id, 55);
			State st = new State(key(s, tavern), null, stand, table, null, false, musician);
			this.states.put(folk.resident().id, st);
			folk.hold(musician ? ItemStack.EMPTY : new ItemStack(Items.HONEY_BOTTLE));
			RhythmFolk.walk(folk, stand, 0.6);
			st.nextMove = folk.gameTime() + 60;
			st.nextAct = folk.gameTime() + 200;
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
				Settlement s = folk.settlement();
				RhythmData.Tavern hours = RhythmData.of(s.culture()).tavern();
				int minute = RhythmFolk.minute(s);
				ServerLevel level = (ServerLevel) folk.entity().level();
				if (!st.musician && Math.floorMod(hours.close() - minute, 24 * 60) <= 5) {
					// closing: the hand bell
					Long last = this.rang.get(folk.resident().id);
					if (last == null || t - last > 2400) {
						this.rang.put(folk.resident().id, t);
						folk.swing();
						level.playSound(null, folk.entity().blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 0.9F, 1.9F);
						RhythmBarks.say(folk, RhythmBarks.CLOSING, Map.of(), true);
					}
				}
				if ((t & 63) == 0 && !wants(folk)) {
					return false;
				}
				if (!RhythmFolk.near(folk, st.stand, 1.8)) {
					if (t >= st.nextMove) {
						RhythmFolk.walk(folk, st.stand, 0.55);
						st.nextMove = t + 80;
					}
					return true;
				}
				folk.stop();
				if (st.musician) {
					if (t % 5 == 0) {
						play(level, folk, s, st);
					}
					return true;
				}
				folk.lookAt(st.table);
				if (t >= st.nextAct) {
					// round the tables: to another one, a mug set down
					folk.swing();
					st.nextAct = t + 240 + Math.floorMod(TownCalendar.mix(t), 200L);
					PlotView tavern = tavernOf(s, folk.resident());
					if (tavern != null) {
						List<Poi> tables = Venues.inPlot(s.pois(PoiType.TABLE), tavern);
						if (!tables.isEmpty()) {
							BlockPos next = tables.get((int) Math.floorMod(TownCalendar.mix(t ^ 77), (long) tables.size())).pos();
							BlockPos stand = Venues.standFor(level, new Venues.Venue(next, next, List.of(next), 1), folk.resident().id, (int) (t % 7));
							this.states.put(folk.resident().id, new State(st.tavern, null, stand, next, null, false, false));
							RhythmFolk.walk(folk, stand, 0.5);
						}
					}
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
	}

	static void clear() {
		TUNE_ENDED.clear();
		PLAYING.clear();
		ROUNDS.clear();
	}
}
