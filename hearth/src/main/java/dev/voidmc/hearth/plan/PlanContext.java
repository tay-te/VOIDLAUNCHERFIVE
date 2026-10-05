package dev.voidmc.hearth.plan;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.plan.catalogue.Catalogue;
import dev.voidmc.hearth.plan.grid.Occupancy;
import dev.voidmc.hearth.plan.model.District;
import dev.voidmc.hearth.plan.model.PlanState;
import dev.voidmc.hearth.plan.model.QueuedProject;
import dev.voidmc.hearth.plan.terrain.Ground;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * Everything one planning call works with: the settlement, its plan state and occupancy grid, the terrain through the
 * cache, the catalogue, the random, the config. Made per call and never shared between threads.
 */
public final class PlanContext {
	public final Settlement settlement;
	public final PlanState state;
	public final Ground ground;
	public final RandomSource random;
	public final Catalogue catalogue;
	public final PlanConfig config;
	public final Culture culture;
	public final Identifier style;
	public final SettlementTier tier;
	public final int population;
	public Occupancy occ;
	/** While planning streets ahead: a new lane that meets a planned (unbuilt) road joins it, as it would a built one. */
	public boolean joinPlanned;
	/** Points a frontage search keeps away from, as {x, z, radius} (farmsteads keep their distance from each other). */
	public final java.util.List<int[]> avoid = new java.util.ArrayList<>();
	/** Whether this call has already routed an approach (one per call keeps the worst case cheap). */
	public boolean routed;
	/** Buildings (not roads) queued since the counter was last reset: whether a need was really met. */
	public int buildings;
	/** {@code System.nanoTime()} after which frontage searches give up (live calls only; see {@link PlanConfig#liveTimeLimitMs}). */
	public long deadline = Long.MAX_VALUE;

	public boolean pastDeadline() {
		return this.deadline != Long.MAX_VALUE && System.nanoTime() > this.deadline;
	}

	/**
	 * Whether the road network may still grow: about twenty blocks of road per resident (and a few hundred to start), so
	 * a settlement that cannot place what it wants does not lay roads for ever. The count runs a little ahead of the
	 * people who live there now: newcomers only come while beds are free, and beds need frontage, so a cap on today's
	 * head count alone would freeze a town whose roads have no frontage left (seen on steep harbour land).
	 */
	public boolean roadsMayGrow() {
		// an expansive settlement's tracks run far between its farmsteads
		int perHead = this.state.layout.expansive ? 45 : 20;
		int ahead = Math.max(6, this.population / 5);
		return this.state.graph.townLength() < (this.state.layout.expansive ? 700 : 300) + perHead * (this.population + ahead);
	}

	/**
	 * Whether another cart track out to a working may be laid: they lie outside the town's own budget but have one of
	 * their own (a few hundred blocks, more as the town grows), so a town never paves its whole territory.
	 */
	public boolean haulMayGrow() {
		return this.state.graph.haulLength() < 360 + 6 * this.population;
	}

	public PlanContext(Settlement settlement, PlanState state, TerrainProbe terrain, RandomSource random, Catalogue catalogue, PlanConfig config) {
		this.settlement = settlement;
		this.state = state;
		this.ground = new Ground(terrain);
		this.random = random;
		this.catalogue = catalogue;
		this.config = config;
		this.culture = settlement.culture();
		this.style = catalogue.style(this.culture);
		this.tier = settlement.tier();
		this.population = settlement.population();
		this.occ = state.occupancy != null ? state.occupancy : new Occupancy();
	}

	public int heartX() {
		return this.state.heartX;
	}

	public int heartZ() {
		return this.state.heartZ;
	}

	public double distanceToHeart(int x, int z) {
		double dx = x - this.state.heartX;
		double dz = z - this.state.heartZ;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/**
	 * The dense core's radius: the layout's scale times the root of the population (with a floor, so a hamlet has a
	 * core), scaled by the config.
	 */
	public double coreRadius() {
		return coreRadiusAt(this.population);
	}

	/** The core's radius the town will have at a population: what things meant to stay outside it plan for. */
	public double coreRadiusAt(int population) {
		return Math.max(18, this.state.layout.coreScale * Math.sqrt(Math.max(6, population)) * this.config.densityScale());
	}

	/**
	 * How far the town's homes and workshops reach from the heart (farms and roads aside): where the edge of town is,
	 * so fields go beyond it rather than beside the square. The far end of the town proper: buildings that stand apart on
	 * purpose (workings and the workshops by them, monasteries, hostels at the gate, shrines, watch houses, the lord's
	 * lodge, a hermit's hut) are left out, and a few outliers do not count (the 92nd percentile of the rest). Computed
	 * once per call.
	 */
	public double extent() {
		if (this.extent < 0) {
			it.unimi.dsi.fastutil.doubles.DoubleArrayList d = new it.unimi.dsi.fastutil.doubles.DoubleArrayList();
			for (var p : this.settlement.plots()) {
				var info = this.catalogue.info(p.spec().archetype());
				if (info == null || !townProper(info)) {
					continue;
				}
				var s = p.spec();
				d.add(distanceToHeart(s.origin().getX() + s.sizeX() / 2, s.origin().getZ() + s.sizeZ() / 2));
			}
			if (d.isEmpty()) {
				this.extent = 0;
			} else {
				double[] a = d.toDoubleArray();
				java.util.Arrays.sort(a);
				this.extent = a.length < 8 ? a[a.length - 1] : a[(int) Math.floor(0.92 * (a.length - 1))];
			}
		}
		return this.extent;
	}

	/** Whether a building is of the town proper (for its extent), not one that stands apart on purpose. */
	private static boolean townProper(ArchetypeInfo info) {
		var c = info.category();
		if (info.flags().contains(ArchetypeInfo.SiteFlag.OUTSKIRTS)) {
			return false;
		}
		switch (c) {
			case HOUSING, WORKPLACE, COMMERCE, CIVIC, STORAGE -> {
			}
			default -> {
				return false;
			}
		}
		var id = info.id();
		return !(id.equals(dev.voidmc.hearth.plan.catalogue.Archetypes.SHRINE) || id.equals(dev.voidmc.hearth.plan.catalogue.Archetypes.HOSTEL)
			|| id.equals(dev.voidmc.hearth.plan.catalogue.Archetypes.CARAVANSERAI) || id.equals(dev.voidmc.hearth.plan.catalogue.Archetypes.CARAVAN_YARD)
			|| id.equals(dev.voidmc.hearth.plan.catalogue.Archetypes.SAWMILL) || id.equals(dev.voidmc.hearth.plan.catalogue.Archetypes.QUARRY)
			|| id.equals(dev.voidmc.hearth.plan.catalogue.Archetypes.MASONRY) || id.equals(dev.voidmc.hearth.plan.catalogue.Archetypes.TANNERY)
			|| id.equals(dev.voidmc.hearth.plan.catalogue.Archetypes.GLASSWORKS) || id.equals(dev.voidmc.hearth.plan.catalogue.Archetypes.DYE_WORKS)
			|| id.equals(dev.voidmc.hearth.plan.catalogue.Archetypes.CEMETERY));
	}

	private double extent = -1;
	private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap waterDistances = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();

	/** Distance to water from a column, to three blocks (memoised per call on a 3-block grid: frontage searches ask often). */
	public int waterDistance(int x, int z) {
		long key = ((long) Math.floorDiv(x, 3) << 32) ^ (Math.floorDiv(z, 3) & 0xFFFFFFFFL);
		int v = this.waterDistances.getOrDefault(key, -1);
		if (v < 0) {
			v = dev.voidmc.hearth.plan.growth.Parcels.waterDistance(this.ground, x, z);
			this.waterDistances.put(key, v);
		}
		return v;
	}

	/** The radius of the wall line a walled town keeps (0 if it has none): the town keeps inside it, farms outside. */
	public double wallLine() {
		if (this.wallLine < 0) {
			double r = 0;
			for (var e : this.state.graph.edges()) {
				if (e.role == dev.voidmc.hearth.plan.model.RoadEdge.Role.WALL_LINE) {
					var d = e.dense();
					double sum = 0;
					for (int i = 0; i < d.length(); i++) {
						sum += distanceToHeart(d.xs()[i], d.zs()[i]);
					}
					r = sum / Math.max(1, d.length());
				}
			}
			this.wallLine = r;
		}
		return this.wallLine;
	}

	private double wallLine = -1;

	/** 0 core, 1 inner town, 2 outskirts, 3 beyond: the density band a column lies in. */
	public int zone(int x, int z) {
		double wall = wallLine();
		if (wall > 0) {
			// a walled town is all town inside its wall
			double w = distanceToHeart(x, z) / wall;
			return w < 0.5 ? 0 : w < 1.0 ? 1 : w < 1.6 ? 2 : 3;
		}
		double d = distanceToHeart(x, z) / coreRadius();
		if (d < 0.55) {
			return 0;
		}
		if (d < 1.0) {
			return 1;
		}
		if (d < 1.7) {
			return 2;
		}
		return 3;
	}

	public @Nullable District district(int x, int z) {
		District best = null;
		double bestScore = Double.MAX_VALUE;
		for (District d : this.state.districts) {
			double s = d.score(x, z);
			if (s < bestScore) {
				bestScore = s;
				best = d;
			}
		}
		return best;
	}

	public @Nullable ArchetypeInfo info(Identifier id) {
		return this.catalogue.info(id);
	}

	/** What the land round the settlement gives it: sites, carrying capacity, wind, local materials (made once per call). */
	public dev.voidmc.hearth.plan.resource.Territory territory() {
		if (this.territory == null) {
			this.territory = new dev.voidmc.hearth.plan.resource.Territory(this);
		}
		return this.territory;
	}

	private dev.voidmc.hearth.plan.resource.@Nullable Territory territory;
	/** Housing by station as this call last read it (set when {@code Needs.stations} is first asked), or null. */
	public dev.voidmc.hearth.plan.society.@Nullable StationLedger ledger;
	/** The {@code local} materials every plot of this call gets instead of the territory's (a deep town: its realm's). */
	public net.minecraft.nbt.@Nullable CompoundTag localOverride;
	/** Params every plot of this call carries (a deep town's plots: its {@code realm}). */
	public net.minecraft.nbt.@Nullable CompoundTag extraParams;

	/** Queues a project behind those already decided, with the territory's materials ({@code local}) on it. */
	public void queue(PlotSpec spec, int replaces, String reason) {
		spec = withLocal(spec);
		this.state.queue.add(new QueuedProject(spec, replaces, reason));
		if (!spec.archetype().getPath().startsWith("infra/")) {
			this.buildings++;
		}
	}

	/** The spec with the {@code local} param (LIVING_WORLD.md §8.5) if it has none and the land gives any. */
	public PlotSpec withLocal(PlotSpec spec) {
		net.minecraft.nbt.CompoundTag local = this.localOverride != null ? this.localOverride : territory().local();
		boolean arms = flies(spec.archetype());
		if (local == null && this.extraParams == null && !arms) {
			return spec;
		}
		net.minecraft.nbt.CompoundTag params = spec.params();
		boolean changed = false;
		int maxHeight = spec.maxHeight();
		if (this.ground.probe() instanceof dev.voidmc.hearth.plan.deep.RealmTerrain realm && !spec.archetype().getPath().startsWith("infra/")) {
			// a deep plot: the realm's character, and the vault over it, which the building keeps one clear of
			// (requests/b-deep.md §1-2)
			if (!params.contains("character")) {
				params.putString("character", realm.realm().character());
				changed = true;
			}
			if (!params.contains("ceiling_y")) {
				int ceiling = realm.ceilingOver(spec.origin().getX(), spec.origin().getZ(), spec.origin().getX() + spec.sizeX() - 1,
					spec.origin().getZ() + spec.sizeZ() - 1, spec.floorY());
				params.putInt("ceiling_y", ceiling);
				maxHeight = Math.max(1, Math.min(maxHeight, ceiling - spec.floorY() - 1));
				changed = true;
			}
		}
		if (arms && !params.contains("banner")) {
			params.putString("banner", banner());
			changed = true;
		}
		if (this.extraParams != null) {
			for (String key : this.extraParams.keySet()) {
				if (!params.contains(key)) {
					params.put(key, this.extraParams.get(key).copy());
					changed = true;
				}
			}
		}
		if (local != null && !params.contains("local")) {
			params.put("local", local.copy());
			changed = true;
		}
		if (!changed) {
			return spec;
		}
		return new PlotSpec(spec.archetype(), spec.style(), spec.seed(), spec.origin(), spec.sizeX(), spec.sizeZ(), spec.facing(), maxHeight,
			spec.tier(), params);
	}

	/** Whether a plot hangs the town's banner (civic, commercial, defensive, law, noble and landmark buildings). */
	static boolean flies(net.minecraft.resources.Identifier archetype) {
		String path = archetype.getPath();
		return path.startsWith("civic/") || path.startsWith("commerce/") || path.startsWith("defense/") || path.startsWith("law/")
			|| path.startsWith("noble/") || path.startsWith("landmark/") || path.equals("landscape/plaza") || path.equals("landscape/festival_ground");
	}

	private static final String[] FIELDS = {"blue", "red", "green", "yellow", "white", "black", "purple", "orange", "light_blue", "brown"};
	private static final String[] CHARGES = {"cross", "straight_cross", "border", "stripe_center", "stripe_middle", "half_horizontal", "half_vertical",
		"triangle_bottom", "triangle_top", "diagonal_left", "rhombus", "circle", "stripe_top", "stripe_bottom", "bricks", "curly_border"};

	/**
	 * The settlement's coat of arms, chosen once from its heart (stable for its whole life), in the form the civic
	 * architects read: {@code "field;colour:pattern;colour:pattern"} with vanilla colour and pattern names
	 * (requests/arch-civic.md §1: every building of a town flies the same flag).
	 */
	public String banner() {
		long h = (this.state.heartX * 0x9E3779B97F4A7C15L) ^ (this.state.heartZ * 0xC2B2AE3D27D4EB4FL) ^ 0x5DEECE66DL;
		h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
		int field = (int) Math.floorMod(h, FIELDS.length);
		int c1 = (int) Math.floorMod(h >>> 8, FIELDS.length);
		if (c1 == field) {
			c1 = (c1 + 4) % FIELDS.length;
		}
		String p1 = CHARGES[(int) Math.floorMod(h >>> 16, CHARGES.length)];
		StringBuilder b = new StringBuilder(FIELDS[field]).append(';').append(FIELDS[c1]).append(':').append(p1);
		if (((h >>> 24) & 3) != 0) {
			int c2 = (int) Math.floorMod(h >>> 32, FIELDS.length);
			if (c2 == c1) {
				c2 = (c2 + 3) % FIELDS.length;
			}
			String p2 = (((h >>> 40) & 1) == 0) ? "border" : "stripe_top";
			if (!p2.equals(p1)) {
				b.append(';').append(FIELDS[c2]).append(':').append(p2);
			}
		}
		return b.toString();
	}

	public long nextSeed() {
		return this.random.nextLong();
	}

	/**
	 * A stable pseudo-random value in [0, 1) for a column, from the settlement's heart: variety in plot sizes and scores
	 * that does not depend on the order positions are examined in (so it cannot drift with the random's state).
	 */
	public double hash(int x, int z, int salt) {
		long h = (this.state.heartX * 0x9E3779B97F4A7C15L) ^ (this.state.heartZ * 0xC2B2AE3D27D4EB4FL) ^ (x * 0x165667B19E3779F9L)
			^ (z * 0xD6E8FEB86659FD93L) ^ (salt * 0x27D4EB2F165667C5L);
		h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
		h = (h ^ (h >>> 29)) * 0x94D049BB133111EBL;
		h ^= h >>> 32;
		return (h >>> 11) * 0x1.0p-53;
	}
}
