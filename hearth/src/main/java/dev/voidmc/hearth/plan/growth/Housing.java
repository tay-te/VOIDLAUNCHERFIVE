package dev.voidmc.hearth.plan.growth;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.ArchetypeInfo.SiteFlag;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.catalogue.Catalogue;
import dev.voidmc.hearth.plan.growth.Parcels.Placement;
import dev.voidmc.hearth.plan.model.District;
import dev.voidmc.hearth.plan.model.LayoutType;
import dev.voidmc.hearth.plan.model.RoadEdge;
import dev.voidmc.hearth.plan.society.Nobility;
import dev.voidmc.hearth.plan.society.StationLedger;
import dev.voidmc.hearth.plan.society.Stations;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Homes for people without beds, by station (LIVING_WORLD.md §8.1). The planner picks the station shortest of homes of
 * its own kind, finds where that station lives — the poor downwind by the tannery and the fields, peasants in crofts by
 * the fields, artisans over their workshops on the high street and the crafts lane by the water, merchants by the
 * market, the clergy by the chapel, scholars and officials by the library and the hall, the gentry on the hill, the lord
 * in his keep — reads the density band there, and only then chooses the house: attached rows on tight frontages in the
 * core (rowhouses, then townhouses and tenements as the tier rises, shophouses on the main street), the culture's own
 * detached house in the inner town, farmhouses at the edge, stilt houses along the boardwalks, cliff dwellings on
 * steep ground — each weighted by how well it suits the station. Every home carries {@code station} and
 * {@code household} params. In dense layouts a crowded core is rebuilt taller instead (see {@link Upgrades}).
 */
public final class Housing {
	private Housing() {
	}

	/** Where a station lives: how its homes are placed, in which quarter, near what. */
	record Home(Placement placement, District.@Nullable Kind district, @Nullable Siting siting) {
	}

	public static boolean plan(PlanContext ctx, Needs needs, int deficit) {
		LayoutType layout = ctx.state.layout;
		double vertical = Grid.verticalBias(ctx) * ctx.config.verticalBias();
		StationLedger ledger = ctx.config.living().stations() ? needs.stations(ctx) : null;
		Station station = ledger == null ? null : ledger.pick(ctx, ctx.config.housingMargin());
		if (station == Station.LORD) {
			// the lord's household lives in his seat (the keep, the castle, the palace), which the nobility plans
			if (ctx.config.living().nobility() && Nobility.seat(ctx)) {
				return true;
			}
			station = Station.GENTRY;
		}
		boolean upgrades = station == null || station == Station.MERCHANT || station == Station.ARTISAN || station == Station.POOR && ctx.tier == SettlementTier.CITY;
		if (upgrades && ctx.tier.ordinal() >= SettlementTier.TOWN.ordinal() && ctx.random.nextDouble() < 0.18 * vertical
			&& Upgrades.plan(ctx, needs, true)) {
			return true;
		}
		boolean commoner = station == null || station == Station.POOR || station == Station.PEASANT || station == Station.ARTISAN;
		if (layout == LayoutType.STILT && commoner && ctx.random.nextDouble() < 0.65) {
			ArchetypeInfo stilt = ctx.catalogue.resolve(Archetypes.STILT_HOUSE, ctx.culture, ctx.tier);
			if (stilt != null && stilt.flags().contains(SiteFlag.ON_WATER)
				&& Growth.placeBuilding(ctx, stilt, Placement.ON_WATER, null, "housing: " + deficit + " without beds", homeParams(ctx, station, ledger))) {
				return true;
			}
		}
		// a cliff town houses its commoners in the quarter climbing the face behind the square (CliffFace) first
		if (layout == LayoutType.CLIFF && commoner && ctx.random.nextDouble() < 0.8
			&& Stacks.face(ctx, homeParams(ctx, station, ledger), "housing: a cliff dwelling in the face for " + deficit + " without beds")) {
			return true;
		}
		boolean farmer = station == null || station == Station.PEASANT || station == Station.POOR && ctx.tier.ordinal() < SettlementTier.TOWN.ordinal();
		if ((layout == LayoutType.FARMSTEADS || ctx.state.signature.fieldFan) && ctx.population >= 6 && farmer) {
			// an expansive settlement keeps a small hamlet at its heart and spreads its homes as farmsteads
			int core = 0;
			for (var p : ctx.settlement.plots()) {
				ArchetypeInfo info = ctx.info(p.spec().archetype());
				if (info != null && info.category() == ArchetypeCategory.HOUSING
					&& ctx.distanceToHeart(p.spec().origin().getX(), p.spec().origin().getZ()) < ctx.coreRadius()) {
					core++;
				}
			}
			double chance = layout == LayoutType.FARMSTEADS ? (core >= 6 + ctx.population / 14 ? 0.95 : 0.45) : 0.15;
			if (ctx.random.nextDouble() < chance && Farms.farmstead(ctx, homeParams(ctx, Station.PEASANT, ledger))) {
				return true;
			}
		}
		Home home = home(ctx, station);
		if (home.placement == Placement.HOME || home.placement == Placement.CENTRAL || home.placement == Placement.MAIN) {
			// the core's street rows run on before the town looks for new frontage: a home of the town's own folk goes onto
			// the end of a row near the heart where one has room (ART_REVIEW_2 item 3)
			ArchetypeInfo terrace = terraceHome(ctx, station);
			if (terrace != null) {
				Parcels.Request tr = request(terrace, home, station).withGap(0);
				int units = Math.max(2, Math.min(4, (int) Math.ceil(deficit / (double) Math.max(1, terrace.beds())) + 1));
				if (continueRow(ctx, terrace, tr, units, reason(station, terrace, deficit, ledger == null || station == null ? null : ledger.head(station)),
					station, ledger)) {
					return true;
				}
			}
		}
		ArchetypeInfo probe = probe(ctx, station);
		if (probe == null) {
			Growth.why("no house of the culture");
			return false;
		}
		Parcels.Request req = request(probe, home, station);
		Parcels.Parcel site = Parcels.best(ctx, req, null);
		if (site == null && home.placement == Placement.SITED) {
			// nothing by the chapel or the hall: in the station's quarter, else wherever homes go
			home = new Home(Placement.CIVIC, home.district, null);
			req = request(probe, home, station);
			site = Parcels.best(ctx, req, null);
		}
		boolean grew = false;
		if (site == null) {
			grew = home.placement == Placement.HOME ? Expand.town(ctx) : Expand.toward(ctx, home.placement);
			site = Parcels.best(ctx, req, null);
			if (site == null && home.placement != Placement.HOME) {
				home = new Home(Placement.HOME, home.district, null);
				req = request(probe, home, station);
				site = Parcels.best(ctx, req, null);
			}
			if (site == null && home.placement != Placement.CROFTS && home.placement != Placement.HOVELS) {
				// the streets are full and will not grow: newcomers settle along the roads out of town, by the fields, and the
				// town spreads along them (a town that answered nothing here stalled at pop = beds, requests/w5-tour.md §1)
				home = new Home(Placement.CROFTS, home.district, null);
				req = request(probe, home, station);
				site = Parcels.best(ctx, req, null);
				if (site == null && Expand.toward(ctx, Placement.CROFTS)) {
					grew = true;
					site = Parcels.best(ctx, req, null);
				}
			}
			if (site == null) {
				Growth.why(grew ? "no frontage by the new road" : ctx.roadsMayGrow() ? "no frontage, and no road could grow" : "no frontage, roads at their budget");
				return grew;
			}
		}
		// fill in near the heart before spreading out along the main roads (the town's own houses; not the edge-dwellers')
		boolean central = home.placement == Placement.HOME || home.placement == Placement.CENTRAL || home.placement == Placement.MAIN;
		if (central && !Grid.expansive(ctx)
			&& ctx.distanceToHeart(site.x0 + site.sizeX / 2, site.z0 + site.sizeZ / 2) > ctx.coreRadius() * Growth.reach(home.placement)
			&& Expand.town(ctx)) {
			grew = true;
			Parcels.Parcel nearer = Parcels.best(ctx, req, null);
			if (nearer != null && (!Parcels.stillClear(ctx, site) || ctx.distanceToHeart(nearer.x0, nearer.z0) < ctx.distanceToHeart(site.x0, site.z0))) {
				site = nearer;
			} else if (!Parcels.stillClear(ctx, site)) {
				return true;
			}
		}
		// a merchant who finds no room by the market buys out a house in the core and rebuilds it (for the station
		// shortest of homes, which is his)
		if (station == Station.MERCHANT && ctx.tier.ordinal() >= SettlementTier.VILLAGE.ordinal()
			&& ctx.distanceToHeart(site.x0 + site.sizeX / 2, site.z0 + site.sizeZ / 2) > ctx.coreRadius() && Upgrades.plan(ctx, needs, true)) {
			return true;
		}
		// dense towns rebuild a crowded core taller before they sprawl
		if (upgrades && site.zone >= 2 && (layout.dense || vertical > 1.2) && ctx.tier.ordinal() >= SettlementTier.VILLAGE.ordinal()
			&& Upgrades.plan(ctx, needs, true)) {
			return true;
		}
		RoadEdge front = ctx.state.graph.edge(site.edge);
		ArchetypeInfo info = choose(ctx, site, front, station);
		if (info == null && station != null) {
			info = choose(ctx, site, front, null);
		}
		if (info == null) {
			Growth.why("no house suits the place");
			return grew;
		}
		Resident next = ledger == null || station == null ? null : ledger.head(station);
		String reason = reason(station, info, deficit, next);
		boolean attached = terraced(ctx, info, site.zone, front.role);
		int edge = site.edge;
		Parcels.Request placed = request(info, home, station);
		if (attached) {
			// a street wall: rows of three to five, the next row run on from the end of the last (ART_REVIEW_2 item 3)
			int units = Math.max(3, Math.min(5, (int) Math.ceil(deficit / (double) Math.max(1, info.beds())) + 2));
			if (central && continueRow(ctx, info, placed.withGap(0), units, reason, station, ledger)) {
				return true;
			}
			if (row(ctx, info, placed.withGap(0), edge, units, reason, station, ledger)) {
				return true;
			}
		}
		Parcels.Parcel p = Parcels.best(ctx, placed, e -> e == edge);
		if (p == null) {
			p = Parcels.best(ctx, placed, null);
		}
		if (p == null) {
			Growth.why("the house does not fit");
			return grew;
		}
		Build.building(ctx, info, p, reason, homeParams(ctx, station, ledger));
		return true;
	}

	/** Where a station's homes go. */
	static Home home(PlanContext ctx, @Nullable Station station) {
		if (station == null) {
			return new Home(Placement.HOME, District.Kind.RESIDENTIAL, null);
		}
		var t = ctx.territory();
		return switch (station) {
			// in a city some of the poor rent rooms in the tenements of the core
			case POOR -> ctx.tier == SettlementTier.CITY && ctx.random.nextDouble() < 0.2 && ctx.catalogue.has(Archetypes.TENEMENT)
				? new Home(Placement.HOME, District.Kind.POOR, null) : new Home(Placement.HOVELS, District.Kind.POOR, null);
			case PEASANT -> new Home(Placement.CROFTS, District.Kind.RESIDENTIAL, null);
			case ARTISAN -> new Home(Placement.MAIN, District.Kind.CRAFTS, null);
			case MERCHANT -> new Home(Placement.CENTRAL, District.Kind.MARKET, null);
			case CLERGY -> near(t.first(Archetypes.CHAPEL, Archetypes.CATHEDRAL, Archetypes.TEMPLE, Archetypes.CONVENT, Archetypes.MONASTERY));
			case SCHOLAR -> near(t.first(Archetypes.LIBRARY, Archetypes.UNIVERSITY, Archetypes.SCHOOL, Archetypes.OBSERVATORY));
			case OFFICIAL -> near(t.first(Archetypes.TOWN_HALL, Archetypes.COURTHOUSE, Archetypes.GUARDHOUSE, Archetypes.KEEP));
			case GENTRY, LORD -> new Home(Placement.RICH, District.Kind.RICH, null);
		};
	}

	private static Home near(int @Nullable [] at) {
		if (at == null) {
			return new Home(Placement.CIVIC, District.Kind.CIVIC, null);
		}
		return new Home(Placement.SITED, District.Kind.CIVIC, Siting.near(at[0], at[1], 40).withMain(0.6));
	}

	private static Parcels.Request request(ArchetypeInfo info, Home home, @Nullable Station station) {
		Parcels.Request req = new Parcels.Request(info, home.placement, home.district);
		if (home.siting != null) {
			req = req.sited(home.siting);
		}
		return station == null ? req : req.withSizeBias(Stations.sizeBias(station)).forStation(station);
	}

	/** The house used to find where the next home goes: the station's own, else a plain detached house of the culture. */
	private static @Nullable ArchetypeInfo probe(PlanContext ctx, @Nullable Station station) {
		if (station != null) {
			for (Identifier id : Stations.homes(station)) {
				ArchetypeInfo info = ctx.catalogue.info(id);
				if (info != null && info.category() == ArchetypeCategory.HOUSING && Catalogue.allowed(info, ctx.culture, ctx.tier)
					&& !info.flags().contains(SiteFlag.ON_WATER)) {
					return info;
				}
			}
		}
		return generic(ctx);
	}

	/** A plain detached house of the culture, used to find where the next home goes. */
	private static @Nullable ArchetypeInfo generic(PlanContext ctx) {
		for (var id : List.of(Archetypes.COTTAGE, Archetypes.HUT, Archetypes.ROUND_HOUSE, Archetypes.LONGHOUSE)) {
			ArchetypeInfo info = ctx.catalogue.resolve(id, ctx.culture, ctx.tier);
			if (info != null && info.category() == ArchetypeCategory.HOUSING) {
				return info;
			}
		}
		List<ArchetypeInfo> all = Growth.allowed(ctx, ArchetypeCategory.HOUSING);
		return all.isEmpty() ? null : all.get(0);
	}

	/**
	 * The params a station's home carries: {@code station}, and {@code household} — the trade of the family it is built
	 * for ({@code job}) and who heads it ({@code head}, a resident id the sim can move in when it is built).
	 */
	static @Nullable CompoundTag homeParams(PlanContext ctx, @Nullable Station station, @Nullable StationLedger ledger) {
		if (station == null) {
			return null;
		}
		CompoundTag params = new CompoundTag();
		params.putString("station", station.getSerializedName());
		Resident head = ledger == null ? null : ledger.head(station);
		CompoundTag household = new CompoundTag();
		if (head != null) {
			household.putString("job", head.job.getSerializedName());
			household.putString("head", head.id.toString());
			household.putString("name", head.familyName);
			ledger.name(head);
		} else if (ledger != null && ledger.commonJob(station) != null) {
			// no family named yet: the trade such a family most likely follows
			household.putString("job", ledger.commonJob(station).getSerializedName());
		}
		if (!household.isEmpty()) {
			params.put("household", household);
		}
		return params;
	}

	private static String reason(@Nullable Station station, ArchetypeInfo info, int deficit, @Nullable Resident head) {
		if (station == null) {
			return "housing: " + deficit + " without beds";
		}
		String job = head == null ? "" : head.job.getSerializedName();
		return "housing: a " + Build.shortName(info) + " for a " + station.getSerializedName() + (job.isEmpty() || job.equals("none") ? "" : " " + job)
			+ " household (" + deficit + " without beds)";
	}

	/** The house for a place, by its density band, its street and its ground, weighted by the station it is for. */
	static @Nullable ArchetypeInfo choose(PlanContext ctx, Parcels.Parcel site, RoadEdge front, @Nullable Station station) {
		return choose(ctx, site, front, station, i -> true);
	}

	/** {@link #choose(PlanContext, Parcels.Parcel, RoadEdge, Station)} among the houses {@code which} allows. */
	static @Nullable ArchetypeInfo choose(PlanContext ctx, Parcels.Parcel site, RoadEdge front, @Nullable Station station,
		java.util.function.Predicate<ArchetypeInfo> which) {
		List<ArchetypeInfo> items = new ArrayList<>();
		List<Double> weights = new ArrayList<>();
		LayoutType layout = ctx.state.layout;
		int zone = site.zone;
		SettlementTier tier = ctx.tier;
		boolean main = front.role == RoadEdge.Role.ARM || front.role == RoadEdge.Role.SQUARE;
		double slope = ctx.ground.slope(site.x0 + site.sizeX / 2, site.z0 + site.sizeZ / 2, 4);
		int raised = site.floorY - ctx.state.heartY;
		double vertical = Grid.verticalBias(ctx) * ctx.config.verticalBias();
		// a town's inner streets are lined with rows, not only its core (the street wall, ART_REVIEW_2 item 3)
		boolean townStreet = zone <= 1 && tier.ordinal() >= SettlementTier.TOWN.ordinal() && street(front.role);
		for (ArchetypeInfo info : Growth.allowed(ctx, ArchetypeCategory.HOUSING)) {
			if (!which.test(info)) {
				continue;
			}
			double w = info.weight();
			var id = info.id();
			if (id.equals(Archetypes.HUT)) {
				w *= tier == SettlementTier.CAMP ? 2.5 : tier == SettlementTier.HAMLET ? 0.5 : station == Station.POOR ? 0.4 : 0;
			} else if (id.equals(Archetypes.COTTAGE)) {
				w *= switch (zone) {
					case 0 -> layout.dense ? 0.15 : tier.ordinal() >= SettlementTier.TOWN.ordinal() ? 0.3 : 0.9;
					case 1 -> layout.dense ? 0.35 : townStreet ? 0.6 : 1.6;
					case 2 -> 1.3;
					default -> 0.8;
				};
			} else if (id.equals(Archetypes.ROWHOUSE)) {
				// a town's main streets are lined with rows through the inner town too, not only in the core
				w *= zone == 0 ? 2.2 : zone == 1 && layout.dense ? 2.4 : townStreet ? 1.8 : zone == 1 ? 0.25 : 0;
			} else if (id.equals(Archetypes.TOWNHOUSE)) {
				w *= (zone == 0 ? 2.6 : zone == 1 && layout.dense ? 1.2 : townStreet ? 1.0 : 0) * vertical;
			} else if (id.equals(Archetypes.TENEMENT)) {
				w *= (zone == 0 ? 1.4 : 0) * vertical;
			} else if (id.equals(Archetypes.SHOPHOUSE)) {
				w *= main && zone <= 1 ? 1.6 : 0;
			} else if (id.equals(Archetypes.FARMHOUSE)) {
				// farmhouses belong out among the fields, never in a town's streets
				w *= layout.dense ? 0 : Grid.nucleated(ctx) ? (zone >= 2 ? 3 : 0.05) : layout.expansive ? (zone >= 2 ? 3 : 0.4) : zone >= 3 ? 1.2 : 0;
			} else if (id.equals(Archetypes.MANOR)) {
				w *= station == Station.GENTRY ? 1 : 0;
			} else if (id.equals(Archetypes.STILT_HOUSE) || id.equals(Archetypes.HERMIT_HUT)) {
				w = 0;
			} else if (id.equals(Archetypes.TOWER_HOUSE)) {
				// a tower to live in is a rare thing (a merchant's on a crag): the skyline is the church's and the hall's
				w *= zone <= 1 ? 0.3 + (raised > 5 ? 0.6 : 0) * vertical : 0.05;
			} else if (id.equals(Archetypes.CLIFF_DWELLING)) {
				w *= slope > 0.45 || layout == LayoutType.CLIFF ? 3.5 : slope > 0.25 ? 1.2 : 0.05;
			} else if (id.equals(Archetypes.HOVEL) || id.equals(Archetypes.SHACK)) {
				w *= zone == 0 ? (layout.dense || tier == SettlementTier.CITY ? 0.3 : 0.05) : zone == 1 ? 0.5 : 1.2;
			} else if (id.equals(Archetypes.CROFT)) {
				w *= zone == 0 ? 0.1 : zone == 1 ? 0.6 : 1.3;
			} else if (id.equals(Archetypes.MERCHANT_HOUSE)) {
				w *= zone == 0 ? 1.6 : zone == 1 ? 1.2 : 0.3;
			} else if (id.equals(Archetypes.SCRIBE_HOUSE)) {
				w *= zone <= 1 ? 1.2 : 0.4;
			} else if (id.equals(Archetypes.RECTORY) || id.equals(Archetypes.REEVE_HOUSE)) {
				w *= zone <= 2 ? 1.0 : 0.4;
			} else if (!info.cultures().isEmpty()) {
				// the culture's own house: longhouses, round houses, courtyard houses
				w *= zone == 0 ? 0.8 : 2.2;
			}
			if (w > 0 && info.flags().contains(SiteFlag.ON_WATER)) {
				w = 0;
			}
			if (w > 0 && station != null) {
				w *= Stations.affinity(station, id);
				// in a city's core the poor rent rooms in tenements and live in the shacks of the back lanes, not in its rows
				if (station == Station.POOR && zone == 0 && !id.equals(Archetypes.TENEMENT) && !id.equals(Archetypes.SHACK)) {
					w *= 0.1;
				}
			}
			if (w > 0) {
				items.add(info);
				weights.add(w);
			}
		}
		return Growth.draw(ctx, items, weights);
	}

	/**
	 * Whether a house stands in a street row here, sharing its side walls (gap 0, {@code party_left/right}): an attachable
	 * house in the core, in the inner town of a dense layout, and along the streets of a town's inner town (ART_REVIEW_2
	 * item 3: the street wall). Everywhere else town homes stand detached.
	 */
	public static boolean terraced(PlanContext ctx, ArchetypeInfo info, int zone, RoadEdge.Role front) {
		if (!info.flags().contains(SiteFlag.ATTACHABLE)) {
			return false;
		}
		if (zone == 0 || ctx.state.layout.dense && zone <= 1) {
			return true;
		}
		return zone == 1 && ctx.tier.ordinal() >= SettlementTier.TOWN.ordinal() && street(front);
	}

	/** A town street a row can line (not a farm or cart track, a door path or a boardwalk). */
	static boolean street(RoadEdge.Role role) {
		return switch (role) {
			case TRACK, HAUL, PATH, BOARDWALK -> false;
			default -> true;
		};
	}

	/** An attachable home of the town for a station, as the core would choose it; null if the culture has none. */
	private static @Nullable ArchetypeInfo terraceHome(PlanContext ctx, @Nullable Station station) {
		if (ctx.tier.ordinal() < SettlementTier.VILLAGE.ordinal() || ctx.state.squareEdge < 0) {
			return null;
		}
		Parcels.Parcel core = new Parcels.Parcel();
		core.x0 = ctx.heartX();
		core.z0 = ctx.heartZ();
		core.sizeX = 1;
		core.sizeZ = 1;
		core.floorY = ctx.state.heartY;
		core.zone = 0;
		RoadEdge square = ctx.state.graph.edge(ctx.state.squareEdge);
		return choose(ctx, core, square, station, i -> i.flags().contains(SiteFlag.ATTACHABLE));
	}

	/** Row ends looked at per call, nearest the heart first. */
	private static final int ROW_ENDS = 24;

	/**
	 * Runs a street row on: up to {@code units} attached houses flush against the free side of a row house already
	 * standing (or planned) in the terraced part of the town, on the same street, its front on the same line, the first
	 * sharing its wall with the old one ({@code party_left}/{@code party_right} toward it), each further one beside the
	 * last. The rows of a town's core so close into a continuous street wall instead of pairs with grass between them.
	 */
	static boolean continueRow(PlanContext ctx, ArchetypeInfo info, Parcels.Request req, int units, String reason, @Nullable Station station,
		@Nullable StationLedger ledger) {
		List<dev.voidmc.hearth.api.PlotSpec> near = new ArrayList<>();
		List<dev.voidmc.hearth.api.PlotSpec> all = new ArrayList<>();
		for (var plot : ctx.settlement.plots()) {
			all.add(plot.spec());
		}
		for (var q : ctx.state.queue) {
			all.add(q.spec());
		}
		for (var s : all) {
			ArchetypeInfo i = ctx.info(s.archetype());
			if (i == null || !i.flags().contains(SiteFlag.ATTACHABLE) || i.category() != ArchetypeCategory.HOUSING || s.params().contains("stacked_on")) {
				continue;
			}
			if (ctx.zone(s.origin().getX() + s.sizeX() / 2, s.origin().getZ() + s.sizeZ() / 2) <= 1) {
				near.add(s);
			}
		}
		near.sort(java.util.Comparator.comparingDouble((dev.voidmc.hearth.api.PlotSpec s) -> ctx.distanceToHeart(s.origin().getX() + s.sizeX() / 2,
			s.origin().getZ() + s.sizeZ() / 2)).thenComparingLong(dev.voidmc.hearth.api.PlotSpec::seed));
		int looked = 0;
		for (var s : near) {
			if (looked >= ROW_ENDS) {
				break;
			}
			int[] ent = dev.voidmc.hearth.plan.grid.PlotGeometry.entrance(s);
			int[] at = dev.voidmc.hearth.plan.road.Roads.edgeAt(ctx, ent[0], ent[1]);
			if (at == null) {
				continue;
			}
			RoadEdge front = ctx.state.graph.edge(at[0]);
			if (!terraced(ctx, info, ctx.zone(ent[0], ent[1]), front.role) || front.role == RoadEdge.Role.TRACK || front.role == RoadEdge.Role.HAUL) {
				continue;
			}
			looked++;
			Direction facing = s.facing();
			boolean alongX = facing.getAxis() == Direction.Axis.Z;
			int depth = Math.max(info.minDepth(), Math.min(info.maxDepth(), alongX ? s.sizeZ() : s.sizeX()));
			for (int side = 0; side < 2; side++) {
				// side 0: the row runs on to the old house's right, so the old house is on the new ones' left
				Direction step = side == 0 ? facing.getClockWise() : facing.getCounterClockWise();
				List<Parcels.Parcel> row = new ArrayList<>();
				int[] last = {s.origin().getX(), s.origin().getZ(), s.origin().getX() + s.sizeX() - 1, s.origin().getZ() + s.sizeZ() - 1};
				int lastFloor = s.floorY();
				while (row.size() < units) {
					int f = info.minFrontage() + (int) Math.round((info.maxFrontage() - info.minFrontage()) * (0.15 + 0.35 * ctx.hash(last[0], last[1], 29 + row.size())));
					int sizeX = alongX ? f : depth;
					int sizeZ = alongX ? depth : f;
					int x0;
					int z0;
					if (alongX) {
						x0 = step.getStepX() > 0 ? last[2] + 1 : last[0] - sizeX;
						// the fronts in one line: the front row where the old house has its own
						z0 = facing == Direction.SOUTH ? last[3] - sizeZ + 1 : last[1];
					} else {
						z0 = step.getStepZ() > 0 ? last[3] + 1 : last[1] - sizeZ;
						x0 = facing == Direction.EAST ? last[2] - sizeX + 1 : last[0];
					}
					Parcels.Parcel next = Parcels.fitAt(ctx, req, at[0], x0, z0, sizeX, sizeZ, facing);
					if (next == null || Math.abs(next.floorY - lastFloor) > 2) {
						break;
					}
					ctx.occ.markRect(next.x0, next.z0, next.x1(), next.z1(), dev.voidmc.hearth.plan.grid.Cell.RESERVED);
					row.add(next);
					last = new int[]{next.x0, next.z0, next.x1(), next.z1()};
					lastFloor = next.floorY;
				}
				for (Parcels.Parcel p : row) {
					ctx.occ.clearRect(p.x0, p.z0, p.x1(), p.z1(), dev.voidmc.hearth.plan.grid.Cell.RESERVED);
				}
				if (row.isEmpty()) {
					continue;
				}
				String toward = side == 0 ? "party_left" : "party_right";
				String onward = side == 0 ? "party_right" : "party_left";
				for (int i = 0; i < row.size(); i++) {
					CompoundTag params = homeParams(ctx, station, ledger);
					if (params == null) {
						params = new CompoundTag();
					}
					// each touches the one before it (the first, the old house) and, but for the last, the next
					params.putBoolean(toward, true);
					params.putBoolean(onward, i < row.size() - 1);
					params.putInt("row", row.size() + 1);
					Build.building(ctx, info, row.get(i), reason + " (the row runs on)", params);
				}
				return true;
			}
		}
		return false;
	}

	static boolean row(PlanContext ctx, ArchetypeInfo info, Parcels.Request req, int edge, int units, String reason) {
		return row(ctx, info, req, edge, units, reason, null, null);
	}

	/** A row of a deep town's dwellings, each with the same params (the miners' households). */
	public static boolean deepRow(PlanContext ctx, ArchetypeInfo info, Parcels.Request req, int edge, int units, String reason,
		@Nullable CompoundTag params) {
		return rowWith(ctx, info, req, edge, units, reason, () -> params == null ? new CompoundTag() : params.copy());
	}

	/**
	 * A row of attached houses along a street: the first where the frontage search puts it, the rest flush beside it
	 * to its right (then left) while the street runs on, each floor meeting the street at its own door (a row steps
	 * down a hill), party walls between neighbours in the row. Each house of the row is a household of the station.
	 */
	static boolean row(PlanContext ctx, ArchetypeInfo info, Parcels.Request req, int edge, int units, String reason, @Nullable Station station,
		@Nullable StationLedger ledger) {
		return rowWith(ctx, info, req, edge, units, reason, () -> {
			CompoundTag p = homeParams(ctx, station, ledger);
			return p == null ? new CompoundTag() : p;
		});
	}

	private static boolean rowWith(PlanContext ctx, ArchetypeInfo info, Parcels.Request req, int edge, int units, String reason,
		java.util.function.Supplier<CompoundTag> paramsFor) {
		Parcels.Parcel first = Parcels.best(ctx, req, e -> e == edge);
		if (first == null) {
			return false;
		}
		List<Parcels.Parcel> row = new ArrayList<>();
		row.add(first);
		// a tentative claim, so units do not overlap each other while the row is laid out
		ctx.occ.markRect(first.x0, first.z0, first.x1(), first.z1(), dev.voidmc.hearth.plan.grid.Cell.RESERVED);
		Direction right = first.facing.getClockWise();
		for (int side = 0; side < 2 && row.size() < units; side++) {
			Direction step = side == 0 ? right : right.getOpposite();
			Parcels.Parcel last = side == 0 ? row.get(row.size() - 1) : row.get(0);
			while (row.size() < units) {
				int f = info.minFrontage() + (int) Math.round((info.maxFrontage() - info.minFrontage()) * (0.15 + 0.35 * ctx.hash(last.x0, last.z0, row.size())));
				boolean alongX = first.facing.getAxis() == Direction.Axis.Z;
				int sizeX = alongX ? f : last.sizeX;
				int sizeZ = alongX ? last.sizeZ : f;
				int x0 = last.x0;
				int z0 = last.z0;
				if (alongX) {
					x0 = step.getStepX() > 0 ? last.x1() + 1 : last.x0 - sizeX;
				} else {
					z0 = step.getStepZ() > 0 ? last.z1() + 1 : last.z0 - sizeZ;
				}
				Parcels.Parcel next = Parcels.fitAt(ctx, req, edge, x0, z0, sizeX, sizeZ, first.facing);
				if (next == null || Math.abs(next.floorY - last.floorY) > 3) {
					break;
				}
				ctx.occ.markRect(next.x0, next.z0, next.x1(), next.z1(), dev.voidmc.hearth.plan.grid.Cell.RESERVED);
				if (side == 0) {
					row.add(next);
				} else {
					row.add(0, next);
				}
				last = next;
			}
		}
		for (Parcels.Parcel p : row) {
			ctx.occ.clearRect(p.x0, p.z0, p.x1(), p.z1(), dev.voidmc.hearth.plan.grid.Cell.RESERVED);
		}
		if (row.size() < 2) {
			return false;
		}
		// order the row from the building's own left to its right, so party walls line up
		boolean ascending = right.getStepX() + right.getStepZ() > 0;
		row.sort((a, b) -> ascending ? Integer.compare(a.x0 + a.z0, b.x0 + b.z0) : Integer.compare(b.x0 + b.z0, a.x0 + a.z0));
		for (int i = 0; i < row.size(); i++) {
			CompoundTag params = paramsFor.get();
			params.putBoolean("party_left", i > 0);
			params.putBoolean("party_right", i < row.size() - 1);
			params.putInt("row", row.size());
			Build.building(ctx, info, row.get(i), reason + (i == 0 ? "" : " (row)"), params);
		}
		return true;
	}
}
