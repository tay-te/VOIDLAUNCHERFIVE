package dev.voidmc.hearth.plan.resource;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.api.UndergroundProbe;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.PlanModule;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.catalogue.Catalogue;
import dev.voidmc.hearth.plan.growth.Build;
import dev.voidmc.hearth.plan.growth.Growth;
import dev.voidmc.hearth.plan.growth.Parcels;
import dev.voidmc.hearth.plan.growth.Parcels.Placement;
import dev.voidmc.hearth.plan.growth.Siting;
import dev.voidmc.hearth.plan.model.QueuedProject;
import dev.voidmc.hearth.plan.model.RoadEdge;
import dev.voidmc.hearth.plan.model.RoadKind;
import dev.voidmc.hearth.plan.road.Roads;
import dev.voidmc.hearth.plan.terrain.Ground;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Where a town works its land (LIVING_WORLD.md §7.2–7.3): quarry pits on its rock, mines into its ore hills (a shaft where
 * the ore lies under flat ground), clay and sand pits on its banks, woodlots and the logging camp at its woods, the salt
 * works on its pans — each on a resource site from the survey, at the end of a cart track out from the town, with the
 * site, its kind, the host rock, how deep or how far the cuts reach and which way the face advances as params (§8.5).
 * When a working is dug out (built: all its stages cut) and the town still wants its goods, the next opens beside it on
 * the same site while the site holds something, else at the next site ({@link #counted}).
 */
public final class Extraction {
	/** Extraction plots (and site-bound workplaces) at one site at once. */
	private static final int PER_SITE = 1;

	private Extraction() {
	}

	/** The kind of site an archetype works, or null if it is not bound to a site. */
	public static ResourceSite.@Nullable Kind kind(Identifier id) {
		if (id.equals(Archetypes.QUARRY_PIT)) {
			return ResourceSite.Kind.STONE;
		}
		if (id.equals(Archetypes.MINE) || id.equals(Archetypes.SHAFT)) {
			return ResourceSite.Kind.ORE;
		}
		if (id.equals(Archetypes.CLAY_PIT)) {
			return ResourceSite.Kind.CLAY;
		}
		if (id.equals(Archetypes.SAND_PIT)) {
			return ResourceSite.Kind.SAND;
		}
		if (id.equals(Archetypes.PLANTATION) || id.equals(Archetypes.LOGGING_CAMP)) {
			return ResourceSite.Kind.WOODS;
		}
		if (id.equals(Archetypes.SALT_WORKS)) {
			return ResourceSite.Kind.SALT;
		}
		return null;
	}

	/**
	 * Whether a working of this archetype is dug out for good once built (a quarry pit, a mine or shaft, a clay or sand pit),
	 * so the next is opened when it is: not a logging camp, woodlot or salt works, which keep working where they stand.
	 */
	public static boolean digsOut(Identifier id) {
		ResourceSite.Kind kind = kind(id);
		return kind != null && kind != ResourceSite.Kind.WOODS && kind != ResourceSite.Kind.SALT;
	}

	/** {@code -Dhearth.dev.plan.reopen=false}: workings dug out still count, as before wave 7 (to compare). */
	static final boolean REOPEN = !"false".equals(System.getProperty("hearth.dev.plan.reopen"));

	/** The archetypes counted together with this one (a mine and a shaft both work the ore). */
	private static boolean sameWork(Identifier a, Identifier b) {
		return a.equals(b) || (a.equals(Archetypes.MINE) || a.equals(Archetypes.SHAFT)) && (b.equals(Archetypes.MINE) || b.equals(Archetypes.SHAFT));
	}

	/**
	 * How many of a program entry's workings the town has for its count: all of them, standing or not, except that a
	 * working dug out (built) no longer counts while the town still wants what it gave and its sites hold more: then the
	 * next is opened, at the same site beside the old cut (a new bench, a new face, a wider pit) or at the next site, so a
	 * town always has a face being cut for its diggers (requests/w7-tour.md §3: one excavation open across 71 settlements,
	 * every other worked out and nothing reopening them). {@code have} is the needs' count of the archetype itself.
	 */
	public static int counted(PlanContext ctx, Identifier id, int have) {
		if (!REOPEN || !digsOut(id) || !ctx.config.living().extraction()) {
			return have;
		}
		int open = 0;
		int out = 0;
		for (PlotView p : ctx.settlement.plots()) {
			if (!sameWork(id, p.spec().archetype()) || p.state() == PlotState.DEMOLISHING) {
				continue;
			}
			if (p.state() == PlotState.BUILT || p.state() == PlotState.ABANDONED) {
				// (one given up, built over or with no blueprint, is no face either)
				out++;
			} else {
				open++;
			}
		}
		for (QueuedProject q : ctx.state.queue) {
			if (sameWork(id, q.spec().archetype())) {
				open++;
			}
		}
		if (out == 0 || !wanted(ctx, id) && !employs(ctx, id) || out >= GENERATIONS * entryCount(ctx, id)) {
			return Math.max(have, open);
		}
		// one face at a time for the crew: wanted again only when none is open or queued
		return open > 0 ? Math.max(have, open) : 0;
	}

	/**
	 * How many times over its program's count a work is dug out and followed by the next before the town stops opening
	 * new ones: each working's successor, and then no more, so a city does not pock its hills with pits (MiniSim, which
	 * digs as fast as it builds, opened eighty in 300 days with no bound). A working cut deeper bench by bench in place
	 * would keep a face open for good; until the archetypes do that, a long-lived town's faces close in the end.
	 */
	static final int GENERATIONS = 2;

	/** The program's count for this work at the town's size (a mine and a shaft count as the mine), at least 1. */
	private static int entryCount(PlanContext ctx, Identifier id) {
		for (dev.voidmc.hearth.plan.growth.Program.Entry e : dev.voidmc.hearth.plan.growth.Program.ENTRIES) {
			if (sameWork(id, e.id())) {
				return Math.max(1, e.count().applyAsInt(Math.max(ctx.population, e.minPop())));
			}
		}
		return 1;
	}

	/**
	 * Whether the town has folk in the trade that digs this working (quarriers, miners, clay diggers): a worked-out pit keeps
	 * its job sites, so its crew stays, and they want a face to cut while a site of its kind holds rock, however much
	 * stone is in store (the live tour's towns sat on thousands from their quarriers' surface work, so a reopening gated on
	 * the stores alone never came, requests/w7-tour.md §3).
	 */
	static boolean employs(PlanContext ctx, Identifier id) {
		dev.voidmc.hearth.api.JobType job = trade(id);
		if (job == null) {
			return false;
		}
		for (dev.voidmc.hearth.api.Resident r : ctx.settlement.residents()) {
			if (r.job == job) {
				return true;
			}
		}
		return false;
	}

	/** The trade that digs a working, or null. */
	static dev.voidmc.hearth.api.@Nullable JobType trade(Identifier id) {
		ResourceSite.Kind kind = kind(id);
		if (kind == null) {
			return null;
		}
		return switch (kind) {
			case STONE -> dev.voidmc.hearth.api.JobType.QUARRIER;
			case ORE -> dev.voidmc.hearth.api.JobType.MINER;
			case CLAY, SAND -> dev.voidmc.hearth.api.JobType.CLAY_DIGGER;
			default -> null;
		};
	}

	/**
	 * Whether the town still draws on what a working gives: its stores of the goods (stone, ore, clay, sand) are under what a
	 * town of its size keeps in hand. A town sitting on a mountain of rubble opens no new pit.
	 */
	public static boolean wanted(PlanContext ctx, Identifier id) {
		ResourceSite.Kind kind = kind(id);
		if (kind == null) {
			return false;
		}
		dev.voidmc.hearth.api.Goods goods = switch (kind) {
			case STONE -> dev.voidmc.hearth.api.Goods.STONE;
			case ORE -> dev.voidmc.hearth.api.Goods.ORE;
			case CLAY -> dev.voidmc.hearth.api.Goods.CLAY;
			case SAND -> dev.voidmc.hearth.api.Goods.SAND;
			default -> null;
		};
		if (goods == null) {
			return false;
		}
		int keep = goods == dev.voidmc.hearth.api.Goods.STONE ? 400 + 4 * ctx.population : 160 + 2 * ctx.population;
		return ctx.settlement.stockpile().get(goods) < keep;
	}

	/** Whether the town has a site in reach for this archetype that is not being worked by one already. */
	public static boolean available(PlanContext ctx, Identifier id) {
		return site(ctx, id) != null;
	}

	/** The site an archetype would go to next: the nearest workable site of its kind with room for another working. */
	public static @Nullable ResourceSite site(PlanContext ctx, Identifier id) {
		List<ResourceSite> sites = sites(ctx, id, 1);
		return sites.isEmpty() ? null : sites.get(0);
	}

	/** The nearest {@code max} workable sites of its kind with room for another working, nearest first. */
	private static List<ResourceSite> sites(PlanContext ctx, Identifier id, int max) {
		ResourceSite.Kind kind = kind(id);
		if (kind == null || !ctx.config.living().extraction()) {
			return List.of();
		}
		Map<Integer, Integer> active = active(ctx, id);
		Map<Integer, Integer> out = digsOut(id) ? dugOut(ctx, id) : Map.of();
		List<ResourceSite> found = new java.util.ArrayList<>();
		for (ResourceSite s : ctx.territory().workable(kind)) {
			if (active.getOrDefault(s.id(), 0) < PER_SITE && out.getOrDefault(s.id(), 0) < DUG_PER_SITE) {
				found.add(s);
				if (found.size() >= max) {
					break;
				}
			}
		}
		return found;
	}

	/** Sites looked at in turn for a working whose nearest site lies under the town. */
	private static final int SITES_TRIED = 4;

	/**
	 * Whether a crew waits for its next face: a working of this work was dug out, none is open or queued, and the town has
	 * folk in its trade. Its need rests only a few calls after a try that found no room, as the vital needs do.
	 */
	public static boolean faceWanted(PlanContext ctx, Identifier id) {
		if (!digsOut(id)) {
			return false;
		}
		boolean out = false;
		for (PlotView p : ctx.settlement.plots()) {
			if (sameWork(id, p.spec().archetype())) {
				if (p.state() == PlotState.PLANNED || p.state() == PlotState.BUILDING) {
					return false;
				}
				out |= p.state() == PlotState.BUILT || p.state() == PlotState.ABANDONED;
			}
		}
		for (QueuedProject q : ctx.state.queue) {
			if (sameWork(id, q.spec().archetype())) {
				return false;
			}
		}
		return out && employs(ctx, id) && dugOut(ctx, id).values().stream().mapToInt(Integer::intValue).sum() < GENERATIONS * entryCount(ctx, id);
	}

	/** Whether a working of this work was dug out at this site before (the next is a new bench beside the old cut). */
	private static boolean reopening(PlanContext ctx, Identifier id, ResourceSite site) {
		return digsOut(id) && dugOut(ctx, id).getOrDefault(site.id(), 0) > 0;
	}

	/**
	 * Workings dug out at one site after which the next opens at another: a quarry grows bench by bench beside its old
	 * cuts, but not without end, so a town that keeps a face open for its diggers spreads its scars over its sites.
	 */
	static final int DUG_PER_SITE = 3;

	/** Workings of this work (a mine and a shaft together) dug out or given up, by site id. */
	private static Map<Integer, Integer> dugOut(PlanContext ctx, Identifier id) {
		Map<Integer, Integer> out = new HashMap<>();
		for (PlotView p : ctx.settlement.plots()) {
			if (sameWork(id, p.spec().archetype()) && (p.state() == PlotState.BUILT || p.state() == PlotState.ABANDONED)) {
				out.merge(p.spec().params().getIntOr("site", -1), 1, Integer::sum);
			}
		}
		return out;
	}

	/** Unfinished workings of an archetype by site id (a dug-out working leaves its site free for the next). */
	private static Map<Integer, Integer> active(PlanContext ctx, Identifier id) {
		Map<Integer, Integer> out = new HashMap<>();
		for (PlotView p : ctx.settlement.plots()) {
			if (!p.spec().archetype().equals(id) || p.state() == PlotState.DEMOLISHING) {
				continue;
			}
			boolean done = p.state() == PlotState.BUILT && kind(id) != ResourceSite.Kind.WOODS && !id.equals(Archetypes.SALT_WORKS);
			if (!done) {
				out.merge(p.spec().params().getIntOr("site", -1), 1, Integer::sum);
			}
		}
		for (QueuedProject q : ctx.state.queue) {
			if (q.spec().archetype().equals(id)) {
				out.merge(q.spec().params().getIntOr("site", -1), 1, Integer::sum);
			}
		}
		return out;
	}

	/**
	 * Plans an extraction archetype (or a site-bound workplace: the logging camp, the salt works) at its site: the plot
	 * fronting a road there, or, if no road reaches the site yet, the cart track out to it. True if anything was queued.
	 */
	public static boolean place(PlanContext ctx, ArchetypeInfo info) {
		List<ResourceSite> sites = sites(ctx, info.id(), SITES_TRIED);
		if (sites.isEmpty()) {
			Growth.why("no workable site");
			return false;
		}
		// the nearest sites whose rock lies out of town, each with the working it takes (an adit, else a shaft) and where it
		// goes: an ore site under tomorrow's streets, or one no track reaches, is passed over for the next (a temperate town
		// of 340 by six ore sites opened no mine in 400 days, every call stopped at the nearest, requests/w7-tour.md §3).
		// A plot that fits by a road already there is looked for at each site's best point first, then at a pit's second
		// point; a live call out of time stops looking (the rest wait for the next call)
		List<Spot> spots = new java.util.ArrayList<>();
		List<Integer> places = new java.util.ArrayList<>();
		List<Parcels.Parcel> found = new java.util.ArrayList<>();
		for (int pass = 0; pass < 2; pass++) {
			for (ResourceSite s : sites) {
				if (!spots.isEmpty() && ctx.pastDeadline()) {
					break;
				}
				Spot spot = pass == 0 ? spot(ctx, info, s, null) : second(ctx, s, spots);
				if (spot == null) {
					continue;
				}
				spots.add(spot);
				Choice best = spot.best(ctx, found);
				places.add(found.size());
				if (best != null) {
					Build.building(ctx, best.use, best.parcel, reason(best.use, spot.site), params(ctx, best.use, spot.site, best.parcel));
					return true;
				}
			}
		}
		if (spots.isEmpty()) {
			// all of each site lies under the town
			Growth.why("the site lies under the town");
			return false;
		}
		// else a cart track out to one (one route a call): the nearest first, and after each try that would not route the
		// next (the need's failures turn the list; a cliff between the town and its nearest ore kept every try at it)
		int turn = ctx.state.failures.getOrDefault("program/" + info.id().getPath(), 0);
		for (int k = 0; k < spots.size(); k++) {
			int i = (k + turn) % spots.size();
			Spot spot = spots.get(i);
			int[] target = spot.target;
			if (ctx.distanceToHeart(target[0], target[1]) > ctx.config.living().haulMaxDistance()) {
				Growth.why("the site is beyond hauling");
				continue;
			}
			if (accessNear(ctx, target[0], target[1], 14) && !(reopening(ctx, info.id(), spot.site) && !accessNear(ctx, target[0], target[1], 5))) {
				// a road comes near already: the site is simply full for now
				Growth.why("no plot fits at the site (" + places.get(i) + " places by the track)");
				continue;
			}
			// (the next working at a site cut before: its track ends at the old cut, whose ground the new one may not take, so a
			// short spur runs on from it to the fresh rock; once one comes within a few blocks of the target this stops)
			ResourceSite site = spot.site;
			String what = site.kind() == ResourceSite.Kind.ORE ? "ore" : site.kind().getSerializedName();
			if (Roads.haul(ctx, target[0], target[1], RoadEdge.Role.HAUL, RoadKind.PATH, 3, false,
				"road: a cart track out to the " + what + " for the " + Build.shortName(spot.use)) != null) {
				return true;
			}
			// too steep for carts all the way: a track with steps where it must (pack animals and porters)
			ctx.routed = false;
			if (Roads.haul(ctx, target[0], target[1], RoadEdge.Role.HAUL, RoadKind.PATH, 3, true,
				"road: a track with steps up to the " + what + " for the " + Build.shortName(spot.use)) != null) {
				return true;
			}
			Growth.why(ctx.haulMayGrow() ? "no track could reach the site" : "cart tracks at their budget");
			return false;
		}
		return false;
	}

	/** A site, the working it takes there (an adit, else a shaft; the archetype itself otherwise), its hillside and target. */
	private record Spot(ResourceSite site, ArchetypeInfo use, @Nullable Face face, int[] target) {
		/** The best plot by a road already there, or null (the shaft by the track when no adit lies along it). */
		@Nullable Choice best(PlanContext ctx, List<Parcels.Parcel> found) {
			Parcels.Parcel best = Extraction.best(ctx, this.use, this.site, this.target, this.face, found);
			if (best != null) {
				return new Choice(best, this.use);
			}
			if (this.face != null && accessNear(ctx, this.target[0], this.target[1], 14)) {
				// the track is at the hill's foot but no adit lies along it or across its end with its back into the slope (a
				// track that comes down the hill to the mouth has only the contour beside it): a shaft with its headframe by the
				// track instead, so a town by an ore hill still mines it (requests/w5-tour.md §3)
				ArchetypeInfo shaft = ctx.catalogue.info(Archetypes.SHAFT);
				if (shaft != null && Catalogue.allowed(shaft, ctx.culture, ctx.tier)) {
					Parcels.Parcel p = Extraction.best(ctx, shaft, this.site, this.target, null, found);
					if (p != null) {
						return new Choice(p, shaft);
					}
				}
			}
			return null;
		}
	}

	/** A plot for a working and the archetype it is. */
	private record Choice(Parcels.Parcel parcel, ArchetypeInfo use) {
	}

	/** Where a working of this archetype would go at a site, or null if all of the site lies under the town. */
	private static @Nullable Spot spot(PlanContext ctx, ArchetypeInfo info, ResourceSite site, int @Nullable [] apartFrom) {
		ArchetypeInfo use = info;
		Face face = null;
		if (info.id().equals(Archetypes.MINE)) {
			face = hillside(ctx, site);
			if (face == null) {
				// ore under flat ground: a shaft with its headframe instead of an adit
				ArchetypeInfo shaft = ctx.catalogue.info(Archetypes.SHAFT);
				if (shaft == null || !Catalogue.allowed(shaft, ctx.culture, ctx.tier)) {
					Growth.why("no hillside for an adit, and no shaft allowed");
					return null;
				}
				use = shaft;
			}
		}
		int[] target = target(ctx, use, site, face, apartFrom);
		return target == null ? null : new Spot(site, use, face, target);
	}

	/**
	 * A pit's second point at a site, well away from its first, so a site whose best point has no room by the track (a bank
	 * between the river and an old pit) is still worked from its other end; none for an adit (its hillside is its point).
	 */
	private static @Nullable Spot second(PlanContext ctx, ResourceSite site, List<Spot> spots) {
		for (Spot first : spots) {
			if (first.site == site) {
				if (first.face != null) {
					return null;
				}
				int[] target = target(ctx, first.use, site, null, first.target);
				return target == null ? null : new Spot(site, first.use, null, target);
			}
		}
		return null;
	}

	/** How far a site's second target stands from its first. */
	private static final int SECOND_TARGET_APART = 24;
	/**
	 * The best plot for a working at its target, or null: along the roads near it and, for an adit, also across the end of a
	 * track there. {@code found} gets what was looked at (for the reason when nothing fits).
	 */
	private static Parcels.@Nullable Parcel best(PlanContext ctx, ArchetypeInfo use, ResourceSite site, int[] target, @Nullable Face face,
		List<Parcels.Parcel> found) {
		Siting siting = Siting.near(target[0], target[1], reach(use)).withHeight(use.id().equals(Archetypes.QUARRY_PIT) ? 0.3 : 0);
		Parcels.Request req = new Parcels.Request(use, Placement.SITED, null).sited(siting);
		if (face != null) {
			int depth = Math.min(use.maxDepth(), Math.max(use.minDepth(), 22 + (int) (ctx.hash(site.id(), 0, 91) * 14)));
			req = req.withSize(-1, depth);
		}
		found.clear();
		found.addAll(Parcels.find(ctx, req, null, 14));
		if (face != null) {
			found.addAll(Parcels.atEnds(ctx, req, target[0], target[1], 14));
		}
		Parcels.Parcel best = null;
		for (Parcels.Parcel p : found) {
			boolean fits = fits(ctx, use, site, target, face, p);
			if (dev.voidmc.hearth.plan.growth.Growth.trace) {
				dev.voidmc.hearth.Hearth.LOG.info("[plan]     candidate {},{} {}x{} d {} fits {}", p.x0, p.z0, p.sizeX, p.sizeZ,
					(int) Math.hypot(p.x0 + p.sizeX / 2 - target[0], p.z0 + p.sizeZ / 2 - target[1]), fits);
			}
			if (fits && (best == null || p.score > best.score)) {
				best = p;
			}
		}
		if (dev.voidmc.hearth.plan.growth.Growth.trace) {
			dev.voidmc.hearth.Hearth.LOG.info("[plan]   {} at site {} {} target {},{} face {} best {}", use.id().getPath(), site.id(), site.kind(), target[0], target[1],
				face, best == null ? null : best.x0 + "," + best.z0);
		}
		return best;
	}

	/** Whether a road, bridge or plaza column lies within {@code r} blocks of (x, z) (sampled every two blocks). */
	public static boolean accessNear(PlanContext ctx, int x, int z, int r) {
		for (int dz = -r; dz <= r; dz += 2) {
			for (int dx = -r; dx <= r; dx += 2) {
				if (dx * dx + dz * dz <= r * r && dev.voidmc.hearth.plan.grid.Cell.isAccess(ctx.occ.get(x + dx, z + dz))) {
					return true;
				}
			}
		}
		return false;
	}

	/** How far from the site's working point the plot may stand. */
	private static double reach(ArchetypeInfo info) {
		return info.id().equals(Archetypes.LOGGING_CAMP) || info.id().equals(Archetypes.SALT_WORKS) ? 28 : 22;
	}

	private static boolean fits(PlanContext ctx, ArchetypeInfo info, ResourceSite site, int[] target, @Nullable Face face, Parcels.Parcel p) {
		int cx = p.x0 + p.sizeX / 2;
		int cz = p.z0 + p.sizeZ / 2;
		if (Math.hypot(cx - target[0], cz - target[1]) > reach(info) + Math.max(p.sizeX, p.sizeZ) / 2.0 + 6) {
			return false;
		}
		// a working stands out of town, beyond where the streets will reach
		if (ctx.distanceToHeart(cx, cz) < minDistance(ctx) * 0.85) {
			return false;
		}
		if (face != null) {
			// an adit runs into the hill: the ground must rise from its mouth to its far end
			Ground g = ctx.ground;
			int[] front = frontMid(p);
			int[] back = backMid(p);
			if (g.ground(back[0], back[1]) - g.ground(front[0], front[1]) < 5) {
				return false;
			}
		}
		return true;
	}

	/** The params of a working (LIVING_WORLD.md §8.5): site, kind, rock, depth or length, face. */
	private static CompoundTag params(PlanContext ctx, ArchetypeInfo info, ResourceSite site, Parcels.Parcel p) {
		CompoundTag params = new CompoundTag();
		params.putInt("site", site.id());
		params.putString("kind", site.kind().getSerializedName());
		Identifier id = info.id();
		Ground g = ctx.ground;
		int cx = p.x0 + p.sizeX / 2;
		int cz = p.z0 + p.sizeZ / 2;
		String[] rock = Materials.rock(g.probe().biome(cx, cz), g.ground(cx, cz), g.seaLevel(), g.probe().temperature(cx, cz), ctx.hash(site.id(), 1, 93));
		String rockId = rock[0].contains("|") ? rock[0].substring(rock[0].lastIndexOf('|') + 1) : rock[0];
		Identifier surveyed = site.material();
		if (surveyed != null && (site.kind() == ResourceSite.Kind.STONE || site.kind() == ResourceSite.Kind.CLAY || site.kind() == ResourceSite.Kind.SAND)
			&& knownNamespace(surveyed.getNamespace())) {
			rockId = surveyed.toString(); // the survey's own reading of the face (requests/p3-sim.md §1)
		}
		params.putString("rock", rockId);
		Direction face = p.facing.getOpposite();
		params.putString("face", face.getSerializedName());
		if (id.equals(Archetypes.QUARRY_PIT)) {
			int depth = 10 + Math.min(14, ctx.population / 12) + (int) (ctx.hash(site.id(), 2, 95) * 4);
			if (ctx.waterDistance(cx, cz) < 24) {
				// near water the pit stops above the water table, or it floods
				depth = Math.min(depth, Math.max(4, p.floorY - g.seaLevel() - 1));
			}
			params.putInt("depth", depth);
		} else if (id.equals(Archetypes.MINE)) {
			params.putInt("length", Math.max(8, p.depth() - 5));
		} else if (id.equals(Archetypes.SHAFT)) {
			params.putInt("depth", shaftDepth(ctx, cx, cz, p.floorY));
			UndergroundProbe.Realm realm = realmUnder(ctx, cx, cz);
			if (realm != null) {
				// the shaft is sunk into a realm (it stands on the surface: "realm" is for plots down there)
				params.putLong("realm_below", realm.id());
			}
		} else if (id.equals(Archetypes.CLAY_PIT) || id.equals(Archetypes.SAND_PIT)) {
			params.putInt("depth", 3 + (int) (ctx.hash(site.id(), 3, 97) * 2));
		}
		if (site.speciality() != null) {
			params.putString("speciality", site.speciality().toString());
		}
		return params;
	}

	/** Whether blocks of this namespace exist in this game (vanilla, or a mod that is loaded: Expanse's rocks need Expanse). */
	private static boolean knownNamespace(String ns) {
		if (ns.equals("minecraft")) {
			return true;
		}
		try {
			return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(ns);
		} catch (Throwable t) {
			return false;
		}
	}

	/** How far down a shaft reaches: to the realm floor if a realm lies under it, else down to where the ore is richest. */
	static int shaftDepth(PlanContext ctx, int x, int z, int floorY) {
		UndergroundProbe.Realm realm = realmUnder(ctx, x, z);
		if (realm != null) {
			UndergroundProbe u = PlanModule.underground(ctx.ground.probe());
			int f = u == null ? realm.floorY() : u.floorY(x, z, realm.floorY());
			if (f != Integer.MIN_VALUE && f < floorY - 8) {
				return floorY - f;
			}
		}
		return Math.max(16, Math.min(96, floorY - Math.max(ctx.ground.probe().minY() + 12, 8)));
	}

	private static UndergroundProbe.@Nullable Realm realmUnder(PlanContext ctx, int x, int z) {
		UndergroundProbe u = PlanModule.underground(ctx.ground.probe());
		return u == null ? null : u.realm(x, z);
	}

	private static String reason(ArchetypeInfo info, ResourceSite site) {
		String what = site.kind() == ResourceSite.Kind.WOODS ? "woods" : site.kind() == ResourceSite.Kind.ORE ? "ore" : site.kind().getSerializedName();
		return "resources: a " + Build.shortName(info) + " to work the " + what + " (site " + site.id() + ")";
	}

	/**
	 * How far out a working must stand: past the core and, as the town fills out, past its houses — the part of a site
	 * under the town is built over, and no one quarries the market square.
	 */
	static double minDistance(PlanContext ctx) {
		// planned for the town it is becoming (at least a town of a hundred), so it is not soon swallowed by its streets
		double future = ctx.coreRadiusAt(Math.max(150, ctx.population * 2));
		return Math.max(future * 1.9 + 8, Math.min(ctx.extent() + 10, future * 2.6));
	}

	/**
	 * The point a working is placed at, on the part of its site outside the town: the most exposed rock for a quarry, the
	 * bank for a clay or sand pit or the salt pans, the wood's edge toward the town for a logging camp or woodlot, the site
	 * itself otherwise. Null if none of the site lies out of town.
	 */
	private static int @Nullable [] target(PlanContext ctx, ArchetypeInfo info, ResourceSite site, @Nullable Face face, int @Nullable [] apartFrom) {
		if (face != null) {
			return new int[]{face.x, face.z};
		}
		Identifier id = info.id();
		Ground g = ctx.ground;
		double minD = minDistance(ctx);
		int sx = site.center().getX();
		int sz = site.center().getZ();
		var a = site.area();
		int step = Math.max(4, Math.min(a.getXSpan(), a.getZSpan()) / 8);
		boolean bank = id.equals(Archetypes.CLAY_PIT) || id.equals(Archetypes.SAND_PIT) || id.equals(Archetypes.SALT_WORKS);
		boolean wood = id.equals(Archetypes.LOGGING_CAMP) || id.equals(Archetypes.PLANTATION);
		int[] best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (int z = a.minZ(); z <= a.maxZ(); z += step) {
			for (int x = a.minX(); x <= a.maxX(); x += step) {
				double d = ctx.distanceToHeart(x, z);
				if (d < minD || g.isWater(x, z) || !dev.voidmc.hearth.plan.grid.Cell.isBuildable(ctx.occ.get(x, z))
					|| apartFrom != null && Math.hypot(x - apartFrom[0], z - apartFrom[1]) < SECOND_TARGET_APART) {
					// (not where an old working has cut already: the next bench opens on the rock beside it)
					continue;
				}
				double toCentre = Math.hypot(x - sx, z - sz);
				double score;
				if (bank) {
					score = -ctx.waterDistance(x, z) * 4 - d * 0.1 - toCentre * 0.05;
				} else if (wood && id.equals(Archetypes.PLANTATION)) {
					// the woodlot is planted behind the camp, in the ground the fellers have cleared
					int[] camp = ctx.territory().nearest(Archetypes.LOGGING_CAMP, x, z);
					double fromCamp = camp == null ? 30 : Math.hypot(x - camp[0], z - camp[1]);
					score = -Math.abs(fromCamp - 30) - toCentre * 0.3 - Math.abs(d - minD - 40) * 0.2;
				} else if (wood) {
					// felling starts at the edge nearest the town and the camp hauls the shortest way
					score = -d - toCentre * 0.2;
				} else if (id.equals(Archetypes.QUARRY_PIT)) {
					score = g.slope(x, z, 3) * 12 + (g.ground(x, z) - ctx.state.heartY) * 0.1 - toCentre * 0.15 - d * 0.03;
				} else {
					score = -toCentre * 0.5 - d * 0.05;
				}
				if (score > bestScore) {
					bestScore = score;
					best = new int[]{x, z};
				}
			}
		}
		return best;
	}

	/** A hillside to drive an adit into: its foot and the downhill direction. */
	private record Face(int x, int z, Direction downhill) {
	}

	/**
	 * The best hillside in a site, out of town: the column whose ground rises most over the next sixteen blocks uphill,
	 * if enough to tunnel into.
	 */
	private static @Nullable Face hillside(PlanContext ctx, ResourceSite site) {
		Ground g = ctx.ground;
		double minD = minDistance(ctx);
		var a = site.area();
		Face best = null;
		int bestRise = 7;
		int step = Math.max(6, Math.min(a.getXSpan(), a.getZSpan()) / 6);
		for (int z = a.minZ(); z <= a.maxZ(); z += step) {
			for (int x = a.minX(); x <= a.maxX(); x += step) {
				if (g.isWater(x, z) || ctx.distanceToHeart(x, z) < minD || !dev.voidmc.hearth.plan.grid.Cell.isBuildable(ctx.occ.get(x, z))) {
					continue;
				}
				for (Direction up : Direction.Plane.HORIZONTAL) {
					int rise = g.ground(x + up.getStepX() * 16, z + up.getStepZ() * 16) - g.ground(x, z);
					int near = g.ground(x + up.getStepX() * 4, z + up.getStepZ() * 4) - g.ground(x, z);
					if (rise > bestRise && near >= 1) {
						bestRise = rise;
						// stand the mouth a few blocks out from the foot of the slope
						best = new Face(x - up.getStepX() * 3, z - up.getStepZ() * 3, up.getOpposite());
					}
				}
			}
		}
		return best;
	}

	private static int[] frontMid(Parcels.Parcel p) {
		return switch (p.facing) {
			case NORTH -> new int[]{p.x0 + p.sizeX / 2, p.z0};
			case SOUTH -> new int[]{p.x0 + p.sizeX / 2, p.z1()};
			case WEST -> new int[]{p.x0, p.z0 + p.sizeZ / 2};
			default -> new int[]{p.x1(), p.z0 + p.sizeZ / 2};
		};
	}

	private static int[] backMid(Parcels.Parcel p) {
		return switch (p.facing) {
			case NORTH -> new int[]{p.x0 + p.sizeX / 2, p.z1()};
			case SOUTH -> new int[]{p.x0 + p.sizeX / 2, p.z0};
			case WEST -> new int[]{p.x1(), p.z0 + p.sizeZ / 2};
			default -> new int[]{p.x0, p.z0 + p.sizeZ / 2};
		};
	}

	/** Sites a town of this size wants worked, with what it would build there (for the needs list). */
	public static List<Identifier> workings() {
		return List.of(Archetypes.LOGGING_CAMP, Archetypes.QUARRY_PIT, Archetypes.MINE, Archetypes.CLAY_PIT, Archetypes.SAND_PIT, Archetypes.SALT_WORKS,
			Archetypes.PLANTATION);
	}
}
