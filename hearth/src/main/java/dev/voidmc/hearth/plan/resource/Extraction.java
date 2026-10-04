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
 * When a working is dug out (built: all its stages cut) and the site still holds something, another may open there.
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

	/** Whether the town has a site in reach for this archetype that is not being worked by one already. */
	public static boolean available(PlanContext ctx, Identifier id) {
		return site(ctx, id) != null;
	}

	/** The site an archetype would go to next: the nearest workable site of its kind with room for another working. */
	public static @Nullable ResourceSite site(PlanContext ctx, Identifier id) {
		ResourceSite.Kind kind = kind(id);
		if (kind == null || !ctx.config.living().extraction()) {
			return null;
		}
		Map<Integer, Integer> active = active(ctx, id);
		for (ResourceSite s : ctx.territory().workable(kind)) {
			if (active.getOrDefault(s.id(), 0) < PER_SITE) {
				return s;
			}
		}
		return null;
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
		ResourceSite site = site(ctx, info.id());
		if (site == null) {
			return false;
		}
		Identifier id = info.id();
		ArchetypeInfo use = info;
		Face face = null;
		if (id.equals(Archetypes.MINE)) {
			face = hillside(ctx, site);
			if (face == null) {
				// ore under flat ground: a shaft with its headframe instead of an adit
				ArchetypeInfo shaft = ctx.catalogue.info(Archetypes.SHAFT);
				if (shaft == null || !Catalogue.allowed(shaft, ctx.culture, ctx.tier)) {
					return false;
				}
				use = shaft;
			}
		}
		int[] target = target(ctx, use, site, face);
		if (target == null) {
			// all of the site lies under the town
			return false;
		}
		Siting siting = Siting.near(target[0], target[1], reach(use)).withHeight(id.equals(Archetypes.QUARRY_PIT) ? 0.3 : 0);
		Parcels.Request req = new Parcels.Request(use, Placement.SITED, null).sited(siting);
		if (face != null) {
			int depth = Math.min(use.maxDepth(), Math.max(use.minDepth(), 22 + (int) (ctx.hash(site.id(), 0, 91) * 14)));
			req = req.withSize(-1, depth);
		}
		Parcels.Parcel best = null;
		List<Parcels.Parcel> found = Parcels.find(ctx, req, null, 14);
		if (dev.voidmc.hearth.plan.growth.Growth.trace) {
			for (Parcels.Parcel p : found) {
				dev.voidmc.hearth.Hearth.LOG.info("[plan]     candidate {},{} {}x{} d {} fits {}", p.x0, p.z0, p.sizeX, p.sizeZ,
					(int) Math.hypot(p.x0 + p.sizeX / 2 - target[0], p.z0 + p.sizeZ / 2 - target[1]), fits(ctx, use, site, target, face, p));
			}
		}
		for (Parcels.Parcel p : found) {
			if (!fits(ctx, use, site, target, face, p)) {
				continue;
			}
			if (best == null || p.score > best.score) {
				best = p;
			}
		}
		if (dev.voidmc.hearth.plan.growth.Growth.trace) {
			dev.voidmc.hearth.Hearth.LOG.info("[plan]   {} at site {} {} target {},{} face {} best {}", use.id().getPath(), site.id(), site.kind(), target[0], target[1],
				face, best == null ? null : best.x0 + "," + best.z0);
		}
		if (best == null) {
			// no road out there yet: a cart track to the site (no stairs; carts and haulers use it); if a road already comes
			// near, the site is simply full for now
			if (ctx.distanceToHeart(target[0], target[1]) > ctx.config.living().haulMaxDistance() || accessNear(ctx, target[0], target[1], 14)) {
				return false;
			}
			return Roads.haul(ctx, target[0], target[1], RoadEdge.Role.HAUL, RoadKind.PATH, 3, false,
				"road: a cart track out to the " + site.kind().getSerializedName() + " for the " + Build.shortName(use)) != null;
		}
		Build.building(ctx, use, best, reason(use, site), params(ctx, use, site, best));
		return true;
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
		params.putString("rock", rock[0].contains("|") ? rock[0].substring(rock[0].lastIndexOf('|') + 1) : rock[0]);
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
				params.putLong("realm", realm.id());
			}
		} else if (id.equals(Archetypes.CLAY_PIT) || id.equals(Archetypes.SAND_PIT)) {
			params.putInt("depth", 3 + (int) (ctx.hash(site.id(), 3, 97) * 2));
		}
		if (site.speciality() != null) {
			params.putString("speciality", site.speciality().toString());
		}
		return params;
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
		return Math.max(future * 1.35 + 8, Math.min(ctx.extent() + 10, future * 2.2));
	}

	/**
	 * The point a working is placed at, on the part of its site outside the town: the most exposed rock for a quarry, the
	 * bank for a clay or sand pit or the salt pans, the wood's edge toward the town for a logging camp or woodlot, the site
	 * itself otherwise. Null if none of the site lies out of town.
	 */
	private static int @Nullable [] target(PlanContext ctx, ArchetypeInfo info, ResourceSite site, @Nullable Face face) {
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
				if (d < minD || g.isWater(x, z)) {
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
				if (g.isWater(x, z) || ctx.distanceToHeart(x, z) < minD) {
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
