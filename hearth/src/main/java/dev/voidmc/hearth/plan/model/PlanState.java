package dev.voidmc.hearth.plan.model;

import com.mojang.serialization.Codec;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.plan.grid.Occupancy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.TreeMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.Nullable;

/**
 * The planner's memory of one settlement, kept under {@code hearth:plan/state} (settlement scope): the layout and
 * signature chosen at founding, what the site survey found, the road network, districts, land held back, and the
 * projects decided but not yet handed out. Saved as one compound (int arrays for geometry, so a city's plan is a few
 * kilobytes).
 *
 * <p>The occupancy grid is not saved: it is rebuilt from the settlement's plots and this state on first use and
 * whenever the sim changed plots behind the planner's back (PLANNING.md, "State"), so it can never go stale.
 */
public final class PlanState {
	public static final int VERSION = 1;
	public static final Codec<PlanState> CODEC = CompoundTag.CODEC.xmap(PlanState::load, PlanState::save);

	public boolean founded;
	public LayoutType layout = LayoutType.CROSSROADS;
	public Signature signature = Signature.NONE;
	public @Nullable Site site;
	public int heartX;
	public int heartY;
	public int heartZ;
	/** Half the side of the main square (the square spans heart ± this). */
	public int plazaHalf;
	/** The square's road-network edge (its perimeter, frontage for plots), or -1. */
	public int squareEdge = -1;
	public RoadGraph graph = new RoadGraph();
	public final List<District> districts = new ArrayList<>();
	public final List<Reservation> reservations = new ArrayList<>();
	public final Deque<QueuedProject> queue = new ArrayDeque<>();
	/** Specs handed out by the last call, checked against the settlement's plots on the next. */
	public final List<PlotSpec> issued = new ArrayList<>();
	public SettlementTier lastTier = SettlementTier.CAMP;
	/** 0 none, 1 palisade, 2 stone wall. */
	public int wallStage;
	public int wallRadius;
	/** Projects handed out so far (seeds, logs). */
	public long serial;
	/** Calls to {@code next} so far: needs that could not be placed rest for a few calls. */
	public long calls;
	/** Needs that could not be placed, resting until the serial passes the value (sorted, so saves are stable). */
	public final TreeMap<String, Long> cooldowns = new TreeMap<>();
	/** How many times in a row a need could not be placed: it rests longer each time (saved, so plans stay the same). */
	public final TreeMap<String, Integer> failures = new TreeMap<>();
	/** The castle hill (x, z): the most commanding ground within reach, chosen at town tier and kept; null until then. */
	public int @Nullable [] castleHill;
	/** The deep town's own plan (its roads, square, queue), on its realm's floor; null until one is founded. */
	public @Nullable PlanState deep;
	/** The id of the realm the deep town stands in. */
	public long deepRealm;
	/** Which level each plot is on (packed with a bit of its spec's seed), by plot id; never saved. */
	public final transient java.util.Map<Integer, Integer> levels = new java.util.HashMap<>();

	/** Rebuilt on demand from plots and this state; see the class comment. */
	public transient @Nullable Occupancy occupancy;
	/** Plot ids the occupancy grid already shows. */
	public transient int @Nullable [] knownPlots;
	/**
	 * Distance to water per three-block cell ({@code PlanContext.waterDistance}), kept between calls: a pure function of
	 * the land under {@link #waterProbe}, which frontage searches ask for thousands of times a call (never saved).
	 */
	public final transient it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap waterDistances = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
	/** The probe {@link #waterDistances} were measured on. */
	public transient @Nullable Object waterProbe;
	/** What each plot is for housing by station, by plot id (derived from its spec; never saved). */
	public final transient java.util.Map<Integer, dev.voidmc.hearth.plan.society.StationLedger.HomeFacts> homeFacts = new java.util.HashMap<>();

	public void invalidate() {
		this.occupancy = null;
		this.knownPlots = null;
		if (this.deep != null) {
			this.deep.invalidate();
		}
	}

	public @Nullable Reservation reservation(String tag) {
		for (Reservation r : this.reservations) {
			if (r.tag().equals(tag)) {
				return r;
			}
		}
		return null;
	}

	public void removeReservation(String tag) {
		this.reservations.removeIf(r -> r.tag().equals(tag));
	}

	private CompoundTag save() {
		CompoundTag t = new CompoundTag();
		t.putInt("version", VERSION);
		t.putBoolean("founded", this.founded);
		t.putString("layout", this.layout.getSerializedName());
		t.putString("signature", this.signature.getSerializedName());
		if (this.site != null) {
			t.put("site", this.site.save());
		}
		t.putIntArray("heart", new int[]{this.heartX, this.heartY, this.heartZ, this.plazaHalf, this.squareEdge});
		t.put("roads", this.graph.save());
		ListTag ds = new ListTag();
		for (District d : this.districts) {
			CompoundTag c = new CompoundTag();
			c.putString("kind", d.kind().getSerializedName());
			c.putIntArray("at", new int[]{d.x(), d.z()});
			c.putDouble("weight", d.weight());
			ds.add(c);
		}
		t.put("districts", ds);
		CompoundTag rs = new CompoundTag();
		for (Reservation r : this.reservations) {
			rs.putIntArray(r.tag(), r.pack());
		}
		t.put("reserved", rs);
		ListTag q = new ListTag();
		for (QueuedProject p : this.queue) {
			q.add(p.save());
		}
		t.put("queue", q);
		ListTag is = new ListTag();
		for (PlotSpec s : this.issued) {
			is.add(PlotSpec.CODEC.encodeStart(NbtOps.INSTANCE, s).getOrThrow());
		}
		t.put("issued", is);
		t.putString("tier", this.lastTier.getSerializedName());
		t.putIntArray("wall", new int[]{this.wallStage, this.wallRadius});
		t.putLong("serial", this.serial);
		t.putLong("calls", this.calls);
		CompoundTag cd = new CompoundTag();
		this.cooldowns.forEach(cd::putLong);
		t.put("cooldowns", cd);
		if (!this.failures.isEmpty()) {
			CompoundTag f = new CompoundTag();
			this.failures.forEach(f::putInt);
			t.put("failures", f);
		}
		if (this.castleHill != null) {
			t.putIntArray("castle_hill", this.castleHill);
		}
		if (this.deep != null) {
			t.put("deep", this.deep.save());
			t.putLong("deep_realm", this.deepRealm);
		}
		return t;
	}

	private static PlanState load(CompoundTag t) {
		PlanState s = new PlanState();
		s.founded = t.getBooleanOr("founded", false);
		s.layout = LayoutType.byName(t.getStringOr("layout", "crossroads"));
		s.signature = Signature.byName(t.getStringOr("signature", "none"));
		s.site = t.getCompound("site").map(Site::load).orElse(null);
		int[] heart = Nbt.ints(t, "heart").orElse(new int[5]);
		s.heartX = heart[0];
		s.heartY = heart[1];
		s.heartZ = heart[2];
		s.plazaHalf = heart.length > 3 ? heart[3] : 0;
		s.squareEdge = heart.length > 4 ? heart[4] : -1;
		s.graph = RoadGraph.load(t.getCompoundOrEmpty("roads"));
		for (Tag d : t.getListOrEmpty("districts")) {
			d.asCompound().ifPresent(c -> {
				int[] at = Nbt.ints(c, "at").orElse(new int[2]);
				s.districts.add(new District(District.Kind.byName(c.getStringOr("kind", "residential")), at[0], at[1], c.getDoubleOr("weight", 1)));
			});
		}
		CompoundTag rs = t.getCompoundOrEmpty("reserved");
		for (String key : rs.keySet().stream().sorted().toList()) {
			Nbt.ints(rs, key).ifPresent(p -> s.reservations.add(Reservation.unpack(key, p)));
		}
		for (Tag q : t.getListOrEmpty("queue")) {
			q.asCompound().ifPresent(c -> s.queue.add(QueuedProject.load(c)));
		}
		for (Tag i : t.getListOrEmpty("issued")) {
			PlotSpec.CODEC.parse(NbtOps.INSTANCE, i).result().ifPresent(s.issued::add);
		}
		String tier = t.getStringOr("tier", "camp");
		for (SettlementTier st : SettlementTier.values()) {
			if (st.getSerializedName().equals(tier)) {
				s.lastTier = st;
			}
		}
		int[] wall = Nbt.ints(t, "wall").orElse(new int[2]);
		s.wallStage = wall[0];
		s.wallRadius = wall.length > 1 ? wall[1] : 0;
		s.serial = t.getLongOr("serial", 0);
		s.calls = t.getLongOr("calls", 0);
		CompoundTag cd = t.getCompoundOrEmpty("cooldowns");
		for (String key : cd.keySet()) {
			s.cooldowns.put(key, cd.getLongOr(key, 0));
		}
		CompoundTag fs = t.getCompoundOrEmpty("failures");
		for (String key : fs.keySet()) {
			s.failures.put(key, fs.getIntOr(key, 0));
		}
		s.castleHill = Nbt.ints(t, "castle_hill").filter(a -> a.length >= 2).orElse(null);
		s.deep = t.getCompound("deep").map(PlanState::load).orElse(null);
		s.deepRealm = t.getLongOr("deep_realm", 0);
		return s;
	}
}
