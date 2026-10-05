package dev.voidmc.hearth.plan.deep;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.UndergroundProbe;
import dev.voidmc.hearth.plan.HearthPlanner;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.PlanModule;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.catalogue.Catalogue;
import dev.voidmc.hearth.plan.grid.Cell;
import dev.voidmc.hearth.plan.growth.Build;
import dev.voidmc.hearth.plan.growth.Expand;
import dev.voidmc.hearth.plan.growth.Growth;
import dev.voidmc.hearth.plan.growth.Needs;
import dev.voidmc.hearth.plan.growth.Parcels;
import dev.voidmc.hearth.plan.growth.Parcels.Placement;
import dev.voidmc.hearth.plan.growth.Siting;
import dev.voidmc.hearth.plan.layout.Founder;
import dev.voidmc.hearth.plan.model.LayoutType;
import dev.voidmc.hearth.plan.model.PlanState;
import dev.voidmc.hearth.plan.model.Reservation;
import dev.voidmc.hearth.plan.model.RoadEdge;
import dev.voidmc.hearth.plan.model.RoadGraph;
import dev.voidmc.hearth.plan.model.RoadKind;
import dev.voidmc.hearth.plan.resource.Materials;
import dev.voidmc.hearth.plan.road.Roads;
import dev.voidmc.hearth.plan.terrain.Ground;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.Nullable;

/**
 * Towns in the realms (LIVING_WORLD.md §8.2). When a settlement's mine has reached a realm — a shaft sunk into one, or a
 * working mine over a realm in its territory — the planner founds a deep town on the realm's floor: a lift from the
 * surface down to it ({@code deep/lift}), a little square at the lift's foot with the deep hall on it, streets along the
 * realm's wall and out across its floor, dwellings cut into the wall's foot, a forge, a market and glow gardens. Every
 * deep plot carries {@code realm} and stands at the realm floor ({@code origin.y}); its {@code local} materials are the
 * realm's own rock and glow (glowcaps, amber, prismite).
 *
 * <p>The deep town is planned with the same machinery as the surface — frontage along its own roads, rows, terraces,
 * plots cut into slopes — on its own plan state ({@code PlanState.deep}) and occupancy, over the realm floor
 * ({@link RealmTerrain}), through a view of the settlement that shows only its plots ({@link LevelView}).
 */
public final class DeepTown {
	private static final int SQUARE_HALF = 4;

	private DeepTown() {
	}

	/** The deep needs of a settlement: found the deep town, then grow it. */
	public static void needs(PlanContext ctx, Needs needs, List<Growth.Need> out) {
		if (!ctx.config.living().deepTowns() || ctx.tier.ordinal() < SettlementTier.VILLAGE.ordinal() || !ctx.catalogue.has(Archetypes.DEEP_LIFT)) {
			return;
		}
		UndergroundProbe u = PlanModule.underground(ctx.ground.probe());
		if (u == null) {
			return;
		}
		if (ctx.state.deep == null) {
			UndergroundProbe.Realm realm = target(ctx, u);
			if (realm != null) {
				out.add(Growth.need("deep/found", 190, c -> found(c, u, realm)));
			}
			return;
		}
		if (ctx.state.deep.queue.isEmpty()) {
			out.add(Growth.need("deep/grow", 210, c -> grow(c, u)));
		}
	}

	/**
	 * The realm a settlement's miners have reached: under a working shaft or mine, or the nearest under its territory
	 * once it has a working mine. Null if none.
	 */
	static UndergroundProbe.@Nullable Realm target(PlanContext ctx, UndergroundProbe u) {
		boolean mining = false;
		for (PlotView p : ctx.settlement.plots()) {
			var id = p.spec().archetype();
			if (!id.equals(Archetypes.MINE) && !id.equals(Archetypes.SHAFT) || p.state() == PlotState.PLANNED) {
				continue;
			}
			mining = true;
			PlotSpec s = p.spec();
			UndergroundProbe.Realm r = u.realm(s.origin().getX() + s.sizeX() / 2, s.origin().getZ() + s.sizeZ() / 2);
			if (r != null) {
				return r;
			}
		}
		if (!mining) {
			return null;
		}
		int reach = Math.max(160, ctx.settlement.territoryRadius());
		UndergroundProbe.Realm best = null;
		double bestD = Double.MAX_VALUE;
		for (UndergroundProbe.Realm r : u.realmsNear(ctx.heartX(), ctx.heartZ(), reach + 200)) {
			double d = Math.hypot(r.center().getX() - ctx.heartX(), r.center().getZ() - ctx.heartZ()) - r.radius();
			if (d < reach && d < bestD) {
				bestD = d;
				best = r;
			}
		}
		return best;
	}

	/** The population a deep town is planned for: a share of the town's folk. */
	static int target(PlanContext ctx) {
		return Math.max(6, Math.min(120, (int) Math.round(LevelView.unwrap(ctx.settlement).population() * ctx.config.living().deepShare())));
	}

	/**
	 * Founds the deep town: the lift on the surface fronting a road, over open, level floor in the realm near the mine;
	 * the deep square at its foot, the deep hall, the first streets. True if anything was queued (the lift, or the cart
	 * track out to where it will stand).
	 */
	static boolean found(PlanContext ctx, UndergroundProbe u, UndergroundProbe.Realm realm) {
		ArchetypeInfo lift = ctx.catalogue.info(Archetypes.DEEP_LIFT);
		if (lift == null || !Catalogue.allowed(lift, ctx.culture, ctx.tier)) {
			return false;
		}
		RealmTerrain rt = RealmTerrain.of(ctx.ground.probe(), u, realm);
		Ground floor = new Ground(rt);
		int[] spot = liftSpot(ctx, rt, floor, realm);
		if (spot == null) {
			return false;
		}
		// the lift's head on the surface, fronting a road, over open floor
		Parcels.Request req = new Parcels.Request(lift, Placement.SITED, null).sited(Siting.near(spot[0], spot[1], 24)).withSize(lift.minFrontage(),
			lift.minDepth());
		Parcels.Parcel head = null;
		for (Parcels.Parcel p : Parcels.find(ctx, req, null, 12)) {
			if (liftFits(rt, floor, p) && (head == null || p.score > head.score)) {
				head = p;
			}
		}
		if (head == null) {
			return !dev.voidmc.hearth.plan.resource.Extraction.accessNear(ctx, spot[0], spot[1], 14)
				&& Roads.haul(ctx, spot[0], spot[1], RoadEdge.Role.HAUL, RoadKind.PATH, 3, false, "road: a cart track out to the lift down to the realm") != null;
		}
		// the deep square at the lift's foot, out from the wall the town is cut into
		int lx = head.x0 + head.sizeX / 2;
		int lz = head.z0 + head.sizeZ / 2;
		double[] wall = wallward(rt, lx, lz);
		Direction in = cardinal(-(int) Math.round(wall[0] * 100), -(int) Math.round(wall[1] * 100));
		// the square's near side takes in the column at the lift's lower door
		int[] door = dev.voidmc.hearth.plan.grid.PlotGeometry.entrance(new PlotSpec(lift.id(), ctx.style, 0, new BlockPos(head.x0, 0, head.z0), head.sizeX,
			head.sizeZ, in, 1, 0, new CompoundTag()));
		int hx = door[0] + in.getStepX() * SQUARE_HALF;
		int hz = door[1] + in.getStepZ() * SQUARE_HALF;
		if (!rt.open(hx, hz)) {
			return false;
		}
		int bottom = floor.ground(lx, lz);
		PlanState d = new PlanState();
		d.founded = true;
		d.layout = LayoutType.CLIFF;
		d.heartX = hx;
		d.heartZ = hz;
		d.heartY = floor.ground(hx, hz);
		d.plazaHalf = SQUARE_HALF;
		d.lastTier = ctx.tier;
		ctx.state.deep = d;
		ctx.state.deepRealm = realm.id();

		// the lift: from the realm floor up through the rock to its head on the street
		CompoundTag params = new CompoundTag();
		params.putLong("realm", realm.id());
		params.putInt("top_y", head.streetY);
		params.putInt("bottom_y", bottom);
		params.putInt("street_y", head.streetY);
		params.putInt("deep_street_y", d.heartY);
		params.putString("deep_facing", in.getSerializedName());
		params.putString("character", realm.character());
		// where the lift's tower meets the rock of the vault (requests/b-deep.md §1)
		params.putInt("ceiling_y", rt.ceilingOver(head.x0, head.z0, head.x0 + head.sizeX - 1, head.z0 + head.sizeZ - 1, bottom));
		PlotSpec spec = new PlotSpec(lift.id(), ctx.style, ctx.nextSeed(), new BlockPos(head.x0, bottom, head.z0), head.sizeX, head.sizeZ, head.facing,
			Math.max(lift.minHeight(), head.streetY - bottom + 10), Build.finish(ctx, lift, 2, null), params);
		Roads.issue(ctx, spec, "the deep: a lift down to the " + realm.character().replace('_', ' ') + " realm, where the miners broke through");

		// the deep town's own plan: its square, the first streets along the wall and out across the floor, the hall
		PlanContext dc = context(ctx, u);
		if (dc == null) {
			return true;
		}
		dev.voidmc.hearth.plan.grid.PlotGeometry.mark(dc.occ, spec, dc.catalogue);
		int h = SQUARE_HALF;
		d.reservations.add(new Reservation("square", hx - h, hz - h, hx + h, hz + h, Cell.PLAZA));
		dc.occ.markRect(hx - h, hz - h, hx + h, hz + h, Cell.PLAZA);
		RoadGraph.Node heart = d.graph.addNode(hx, d.heartY, hz, RoadGraph.NodeKind.HEART);
		Founder.squareEdge(dc, heart.id());
		// streets along the wall's foot either way, and one out across the floor
		double along = StrictMath.atan2(wall[1], wall[0]) + Math.PI / 2;
		Founder.arm(dc, heart.id(), along, 80, 3, RoadKind.LANE, RoadEdge.Role.ARM, 22);
		Founder.arm(dc, heart.id(), along + Math.PI, 80, 3, RoadKind.LANE, RoadEdge.Role.ARM, 22);
		Founder.arm(dc, heart.id(), StrictMath.atan2(-wall[1], -wall[0]), Math.min(120, realm.radius()), 3, RoadKind.STREET, RoadEdge.Role.ARM, 18);
		for (RoadEdge e : new ArrayList<>(d.graph.edges())) {
			if (e.built > 0) {
				dev.voidmc.hearth.plan.growth.Grid.planAhead(dc, e, 0, e.built);
			}
		}
		ArchetypeInfo hall = Growth.exactInfo(dc, Archetypes.DEEP_HALL);
		if (hall != null) {
			Growth.placeBuilding(dc, hall, Placement.CIVIC, null, "the deep: the hall of the deep town on its square", deepParams(null));
		}
		ctx.buildings += dc.buildings;
		return true;
	}

	/**
	 * The direction to the nearest wall of the realm from a point on its floor (a unit vector), and how far it is: the
	 * deep town is cut into the wall's foot, its streets along it.
	 */
	static double[] wallward(RealmTerrain rt, int x, int z) {
		double best = Double.MAX_VALUE;
		double bx = 0;
		double bz = 1;
		for (int k = 0; k < 16; k++) {
			double a = k * Math.PI / 8;
			double dx = StrictMath.cos(a);
			double dz = StrictMath.sin(a);
			int d = 4;
			while (d <= 72 && rt.open(x + (int) Math.round(dx * d), z + (int) Math.round(dz * d))) {
				d += 4;
			}
			if (d < best) {
				best = d;
				bx = dx;
				bz = dz;
			}
		}
		return new double[]{bx, bz, best};
	}

	/** A spot in the realm for the lift: open, level floor near the wall's foot (the town is cut into the wall), near the mine. */
	private static int @Nullable [] liftSpot(PlanContext ctx, RealmTerrain rt, Ground floor, UndergroundProbe.Realm realm) {
		int[] anchor = ctx.territory().first(Archetypes.SHAFT, Archetypes.MINE);
		int ax = anchor == null ? ctx.heartX() : anchor[0];
		int az = anchor == null ? ctx.heartZ() : anchor[1];
		int cx = realm.center().getX();
		int cz = realm.center().getZ();
		int r = Math.max(24, realm.radius());
		int[] best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (int dz = -r; dz <= r; dz += 10) {
			for (int dx = -r; dx <= r; dx += 10) {
				int x = cx + dx;
				int z = cz + dz;
				// within reach of the town's mine, over open floor, under dry land a lift can stand on
				if (Math.hypot(x - ax, z - az) > 200 || !rt.open(x, z) || ctx.ground.isWater(x, z)) {
					continue;
				}
				// the lift is sunk through rock: where the land dips almost to the realm floor the vault would be open
				// sky, and a lift of a few blocks is no lift
				if (ctx.ground.ground(x, z) - floor.ground(x, z) < 30) {
					continue;
				}
				// a floor a town can stand on, some twenty blocks out from the wall's foot
				int[] range = floor.range(x - 8, z - 8, x + 8, z + 8, 3);
				if (range[1] - range[0] > 6) {
					continue;
				}
				double wallDist = wallward(rt, x, z)[2];
				if (wallDist < 12) {
					continue;
				}
				double score = -Math.abs(wallDist - 22) * 3 - Math.hypot(x - ax, z - az) * 0.15 - (range[1] - range[0]) * 3;
				if (score > bestScore) {
					bestScore = score;
					best = new int[]{x, z};
				}
			}
		}
		return best;
	}

	/** The lift's footprint stands over open floor with room at its foot. */
	private static boolean liftFits(RealmTerrain rt, Ground floor, Parcels.Parcel p) {
		for (int z = p.z0; z <= p.z1(); z += 2) {
			for (int x = p.x0; x <= p.x1(); x += 2) {
				if (!rt.open(x, z)) {
					return false;
				}
			}
		}
		int[] range = floor.range(p.x0, p.z0, p.x1(), p.z1(), 2);
		// and its head stands on a street well above the vault: the lift is sunk through rock
		return range[1] - range[0] <= 3 && p.streetY - range[1] >= 30;
	}

	private static Direction cardinal(int dx, int dz) {
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx >= 0 ? Direction.EAST : Direction.WEST;
		}
		return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/**
	 * The planning context of the deep town, or null if its realm is gone from the probe: the deep view, the deep plan
	 * state, the realm floor as ground, the realm's materials and the {@code realm} param on every plot. Its occupancy is
	 * brought up to date with the deep plots.
	 */
	public static @Nullable PlanContext context(PlanContext ctx, UndergroundProbe u) {
		PlanState d = ctx.state.deep;
		if (d == null) {
			return null;
		}
		UndergroundProbe.Realm realm = u.realm(d.heartX, d.heartZ);
		if (realm == null || realm.id() != ctx.state.deepRealm) {
			return null;
		}
		LevelView view = LevelView.deep(LevelView.unwrap(ctx.settlement), ctx.state, target(ctx));
		PlanContext dc = new PlanContext(view, d, RealmTerrain.of(ctx.ground.probe(), u, realm), ctx.random, ctx.catalogue, ctx.config);
		dc.localOverride = Materials.realm(realm.character());
		CompoundTag extra = new CompoundTag();
		extra.putLong("realm", realm.id());
		dc.extraParams = extra;
		dc.deadline = ctx.deadline;
		HearthPlanner.reconcileLevel(dc);
		return dc;
	}

	/** The deep town grows: its hall, homes for its share of the town, a forge, glow gardens, a market, streets. */
	static boolean grow(PlanContext ctx, UndergroundProbe u) {
		PlanContext dc = context(ctx, u);
		if (dc == null) {
			return false;
		}
		Needs n = Needs.of(dc);
		int folk = dc.population;
		boolean any = false;
		ArchetypeInfo hall = Growth.exactInfo(dc, Archetypes.DEEP_HALL);
		if (hall != null && n.count(Archetypes.DEEP_HALL) == 0) {
			any = Growth.placeBuilding(dc, hall, Placement.CIVIC, null, "the deep: the hall of the deep town on its square", deepParams(null));
		}
		int beds = 0;
		for (PlotView p : dc.settlement.plots()) {
			ArchetypeInfo info = dc.info(p.spec().archetype());
			if (info != null && info.beds() > 0 && p.state() == PlotState.BUILT && info.category() == dev.voidmc.hearth.api.ArchetypeCategory.UNDERGROUND) {
				beds += Needs.estimateBeds(info, p.spec());
			}
		}
		ArchetypeInfo dwelling = Growth.exactInfo(dc, Archetypes.DEEP_DWELLING);
		int short_ = folk - beds - n.plannedBeds;
		if (!any && dwelling != null && short_ > 0) {
			any = dwellings(dc, dwelling, short_);
		}
		ArchetypeInfo forge = Growth.exactInfo(dc, Archetypes.DEEP_FORGE);
		if (!any && forge != null && n.count(Archetypes.DEEP_FORGE) < 1 + folk / 30 && folk >= 8) {
			any = Growth.placeBuilding(dc, forge, Placement.MAIN, null, "the deep: a forge by the ore", null);
		}
		ArchetypeInfo garden = Growth.exactInfo(dc, Archetypes.DEEP_GLOW_GARDEN);
		// glow gardens feed part of the deep; the rest comes down the lift from the town's fields
		if (!any && garden != null && n.food < folk / 2) {
			any = Growth.placeBuilding(dc, garden, Placement.EDGE, null, "the deep: a glow garden on the realm floor", null);
		}
		ArchetypeInfo market = Growth.exactInfo(dc, Archetypes.DEEP_MARKET);
		if (!any && market != null && n.count(Archetypes.DEEP_MARKET) < 1 && folk >= 14) {
			any = Growth.placeBuilding(dc, market, Placement.CENTRAL, null, "the deep: a market under the vault", null);
		}
		ctx.buildings += dc.buildings;
		return any;
	}

	/** Dwellings cut into the realm's wall: rows near the square, the rest along the streets at the wall's foot. */
	private static boolean dwellings(PlanContext dc, ArchetypeInfo info, int short_) {
		// toward the wall from the square, on the rising rubble the dwellings are cut into
		double[] wall = dc.ground.probe() instanceof RealmTerrain rt ? wallward(rt, dc.heartX(), dc.heartZ()) : new double[]{0, 0, 0};
		int fx = dc.heartX() + (int) Math.round(wall[0] * Math.max(0, wall[2] - 8));
		int fz = dc.heartZ() + (int) Math.round(wall[1] * Math.max(0, wall[2] - 8));
		Parcels.Request req = new Parcels.Request(info, Placement.HOME, null).sited(Siting.near(fx, fz, 70).withHeight(0.6)).forStation(null);
		Parcels.Parcel site = Parcels.best(dc, req, null);
		if (site == null) {
			return Expand.town(dc);
		}
		CompoundTag params = deepParams("miner");
		if (info.flags().contains(dev.voidmc.hearth.api.ArchetypeInfo.SiteFlag.ATTACHABLE) && site.zone <= 1) {
			int units = Math.max(2, Math.min(5, short_ / Math.max(1, info.beds()) + 1));
			if (dev.voidmc.hearth.plan.growth.Housing.deepRow(dc, info, req.withGap(0), site.edge, units, "the deep: dwellings cut into the realm wall",
				params)) {
				return true;
			}
		}
		Build.building(dc, info, site, "the deep: a dwelling cut into the realm wall", params);
		return true;
	}

	/** Params of a deep home: the miners' households (their station follows their work). */
	private static @Nullable CompoundTag deepParams(@Nullable String job) {
		if (job == null) {
			return null;
		}
		CompoundTag p = new CompoundTag();
		p.putString("station", "peasant");
		CompoundTag household = new CompoundTag();
		household.putString("job", job);
		p.put("household", household);
		return p;
	}
}
