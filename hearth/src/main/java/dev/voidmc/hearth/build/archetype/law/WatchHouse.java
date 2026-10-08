package dev.voidmc.hearth.build.archetype.law;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.BlockEntities;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Floors;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.civic.Pueblo;
import dev.voidmc.hearth.build.kit.civic.Grounds;
import dev.voidmc.hearth.build.kit.civic.Halls;
import dev.voidmc.hearth.build.kit.civic.Signs;
import dev.voidmc.hearth.build.kit.civic.Storeys;
import dev.voidmc.hearth.build.kit.civic.Towers;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.kit.lawhouse.Fenestration;
import dev.voidmc.hearth.build.kit.lawhouse.Masonry;
import dev.voidmc.hearth.build.kit.lawhouse.Timber;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * {@code hearth:law/watch_house}: the watch's post at a gate into town, a small tower house. Two stout storeys of the
 * town's masonry (the guard room, the bunks), then the lookout: in the pitched-roof traditions a timber storey jettied
 * out over the walls on every side like a hoarding, opened by wide lookouts with a rail, under a steep pyramid roof with
 * the alarm bell hung beneath it; in the flat-roofed south a roof terrace behind a crenellated parapet with a little
 * sentry pavilion. A bench by the door for the watch, the town's banner over it, a lamp.
 *
 * <p>Inside: the guard room (the grindstone and the rack of arms, a table and stool, a chest, the stair up), the bunks
 * above, and the lookout reached by a ladder through a hatch.
 *
 * <p>POIs: JOB_SITE (GUARD), GUARD_POST (by the door, in the lookout), VIEWPOINT (the lookout), BED, STORAGE, SEAT, DOOR.
 *
 * <p>Params: {@code variant} (int).
 */
public final class WatchHouse implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("law/watch_house"), ArchetypeCategory.LAW, Set.of(JobType.GUARD), 2,
		8, 10, 8, 10, 16, 20, SettlementTier.VILLAGE, Set.of(), Set.of(ArchetypeInfo.SiteFlag.VERTICAL), List.of(), 6, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int maxH = d.maxHeight();
		Tradition t = Tradition.of(d);
		boolean flat = t == Tradition.ADOBE || t == Tradition.MEDITERRANEAN;
		int x0 = 1;
		int x1 = w - 2;
		int cx = (x0 + x1) / 2;
		int z0 = 1;
		int z1 = dp - 2;
		int base = Grounds.raisedBase(d, x0, x1, z0);
		int[] heights = flat ? new int[]{4, 4} : new int[]{4, 4, 4};
		Body body = new Body(x0, z0, x1, z1, base, heights, false, false);
		if (!flat) {
			for (Direction side : Dirs.HORIZONTAL) {
				body.jetty(2, side);
			}
		}
		d.body(x0, z0, x1, z1);
		int plate = body.plateY();
		d.eave(plate);
		Stone dress = Stone.dressing(d);
		Stone wall = Masonry.walling(d);
		Stone plinth = Masonry.plinth(d);
		Timber tim = Timber.frame(d);

		// --- the masonry storeys
		Storeys.ground(d, body, false);
		Grounds.forecourt(d, 0, w - 1, z0 - 1, true);
		Masonry.storey(d, body, 0, wall, dress, plinth, true);
		Masonry.storey(d, body, 1, wall, dress, plinth, true);
		int fy1 = body.floorY(1);
		Side front = body.side(0, Dirs.FRONT);
		Openings.door(d, front, cx, base, 1, new Openings.DoorOpts(true, true, true));
		d.poi(PoiType.GUARD_POST, cx - 1, base, z0 - 1, Dirs.FRONT);
		// barred loopholes below, small windows above
		for (Side s : body.sides(0)) {
			if (s.out() == Dirs.FRONT) {
				continue;
			}
			Fenestration.barred(d, s, s.middle(), base + 1, 2, dress);
		}
		for (Side s : body.sides(1)) {
			Fenestration.framed(d, s, s.middle(), 1, fy1 + 1, 2, dress, false, false, false);
		}
		// the town's banner over the door, a bench beside it for the watch
		Signs.Arms arms = Signs.arms(d);
		if (Openings.isWallBlock(d, cx, fy1 + 1, z0) && d.isEmpty(cx, fy1 + 1, z0 - 1)) {
			Signs.wallBanner(d, cx, fy1 + 1, z0 - 1, Dirs.FRONT, arms.field(), arms.layers());
		}
		int bx = cx + 2;
		if (bx <= x1 && d.isEmpty(bx, base, z0 - 1) && Groundwork.surface(d, bx, z0 - 1) == base) {
			Furniture.chair(d, bx, base, z0 - 1, Dirs.BACK);
		}

		// --- the lookout and the roof
		int skyline;
		int lookY;
		if (flat) {
			Storeys.Top top = Storeys.roof(d, body, RoofForm.FLAT, Direction.Axis.X, 1.0, 1);
			Towers.battlements(d, x0, z0, x1, z1, top.deck());
			lookY = top.deck();
			// (the mesa's watch keeps its roof for the stepped room of the pueblo, its terrace the lookout)
			skyline = Pueblo.is(d) ? lookY + 1 : sentryPavilion(d, stairLeft0(d) ? x1 - 3 : x0 + 1, z1 - 3, lookY, tim);
		} else {
			int fy2 = body.floorY(2);
			lookY = fy2;
			int[] r2 = body.rect(2);
			// the jettied lookout in timber: posts at the corners and middles, a girt at the sill, open between, a rail
			lookout(d, r2, fy2, plate, tim);
			RoofForm form = t == Tradition.KARST ? RoofForm.PAGODA : RoofForm.HIP;
			Storeys.Top top = Storeys.roof(d, body, form, Direction.Axis.X, 1.0, 1);
			skyline = top.ridge();
			if (top.roof() != null) {
				skyline = Math.max(skyline, Towers.finial(d, (r2[0] + r2[2]) / 2, top.roof().ridgeY + 1, (r2[1] + r2[3]) / 2, maxH - 1, false));
			}
			// the alarm bell under the roof in the middle of the lookout
			int bcx = (r2[0] + r2[2]) / 2;
			int bcz = (r2[1] + r2[3]) / 2;
			try (var st = d.stage(StageKind.FRAME)) {
				for (int x = r2[0] + 1; x < r2[2]; x++) {
					d.set(x, plate, bcz, tim.beam(Direction.Axis.X));
				}
			}
			if (d.isEmpty(bcx, plate - 1, bcz)) {
				Towers.bell(d, bcx, plate - 1, bcz, Direction.Axis.X);
			}
		}
		Towers.lookout(d, cx, lookY, z0 + 1, Dirs.FRONT, true);

		// --- the guard room: the stair up (a switchback in a corner), the grindstone, the rack of arms, a table
		int ix0 = x0 + 1;
		int ix1 = x1 - 1;
		int iz0 = z0 + 1;
		int iz1 = z1 - 1;
		boolean stairLeft = !stairLeft0(d);
		int sx = stairLeft ? ix0 : ix1;
		Direction across = stairLeft ? Dirs.RIGHT : Dirs.LEFT;
		Stairs.switchback(d, sx, base, iz1 - 2, Dirs.BACK, 4, across);
		Rooms.Room guard = new Rooms.Room(ix0, iz0, ix1, iz1, base, 3);
		guard.keep(cx, iz0);
		guard.keepArea(sx, iz1 - 2, sx + Dirs.dx(across), iz1);
		guard.keep(sx, iz1 - 3);
		guard.keep(sx + Dirs.dx(across), iz1 - 3);
		int gx = stairLeft ? ix1 : ix0;
		if (guard.claim(gx, iz0)) {
			Furniture.workstation(d, JobType.GUARD, gx, base, iz0, Dirs.BACK);
			guard.keep(gx, iz0 + 1);
		}
		// the rack of arms on the side wall
		var shelf = d.mat(Role.FURNITURE).main(Form.SHELF);
		int rz = iz0 + 1;
		int rx = stairLeft ? x1 : x0;
		if (shelf != null && Openings.isWallBlock(d, rx, base + 1, rz)) {
			d.inStage(StageKind.INTERIOR, () -> d.set(rx, base + 1, rz, States.facing(shelf, stairLeft ? Dirs.LEFT : Dirs.RIGHT), BlockEntities.shelf(List.of(
				new Object[]{"minecraft:iron_sword", 1, 0}, new Object[]{"minecraft:crossbow", 1, 1}, new Object[]{"minecraft:shield", 1, 2}))));
		}
		Rooms.furnish(d, guard, Rooms.Type.LIVING, 0, JobType.NONE);
		Lighting.room(d, guard);
		// --- the bunks
		Rooms.Room bunks = new Rooms.Room(ix0, iz0, ix1, iz1, fy1, 3);
		int[] land = {sx + Dirs.dx(across), iz1 - 3};
		bunks.keep(land[0], land[1]);
		bunks.keepArea(sx, iz1 - 2, sx + Dirs.dx(across), iz1);
		// the ladder to the lookout through a hatch, in the corner over the stair's head
		int lx = stairLeft ? ix1 : ix0;
		int lz = iz1;
		bunks.keep(lx, lz);
		if (!flat) {
			Stairs.ladder(d, lx, lz, fy1, body.floorY(2) - 1, Dirs.FRONT);
		} else {
			Stairs.ladder(d, lx, lz, fy1, plate, Dirs.FRONT);
		}
		bunks.keep(lx, lz - 1);
		Rooms.furnish(d, bunks, Rooms.Type.BEDROOM, 2, JobType.NONE);
		Lighting.room(d, bunks);
		if (!flat) {
			int[] r2 = body.rect(2);
			Lighting.room(d, r2[0] + 1, r2[1] + 1, r2[2] - 1, r2[3] - 1, body.floorY(2));
		}
		if (base >= 1) {
			Grounds.flight(d, cx - 1, cx + 1, z0 - 1, base, base);
		}
		Grounds.approach(d, cx, base, z0 - 1);
		Halls.lamp(d, 0, 0, 0, 2);
		if (d.habits().snow()) {
			Garden.snowBanks(d, x0, z0, x1, z1, base, cx);
		}
		// the mesa's pueblo: a stepped room over the flat roof, a ladder up to its terrace
		int pueblo = Pueblo.is(d) ? Pueblo.crown(d) : Integer.MIN_VALUE;
		dev.voidmc.hearth.build.kit.civic.Halls.finish(d);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.max(Math.min(maxH - 1, skyline + 2), pueblo + 2));
	}

	/**
	 * The lookout storey over the rectangle r at standing level y, its plate at {@code plate}: boards on the joists
	 * oversailing the walls below, timber posts at the corners and the middle of each side, a girt at sill height and a
	 * rail of fences between the posts, open above the rail to look out every way.
	 */
	static void lookout(Draft d, int[] r, int y, int plate, Timber t) {
		Floors.boards(d, r[0], r[1], r[2], r[3], y - 1);
		try (var st = d.stage(StageKind.FRAME)) {
			for (Side s : Side.of(r[0], r[1], r[2], r[3])) {
				int mid = s.middle();
				for (int i = 0; i < s.length(); i++) {
					int x = s.x(i);
					int z = s.z(i);
					boolean post = s.isCorner(i) || i == mid || s.length() % 2 == 0 && i == mid + 1;
					// the jetty's brackets under the oversailing sill
					if (!post) {
						d.set(x, y - 1, z, t.beam(s.axis()));
					}
					for (int yy = y; yy <= plate; yy++) {
						if (post || yy == plate) {
							d.set(x, yy, z, yy == plate && !post ? t.beam(s.axis()) : t.post());
						} else if (yy == y) {
							d.set(x, yy, z, t.fence());
						} else {
							d.set(x, yy, z, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
						}
					}
				}
			}
		}
		// brackets under the oversail on every side
		try (var st = d.stage(StageKind.FRAME)) {
			for (Side s : Side.of(r[0], r[1], r[2], r[3])) {
				for (int i = 1; i < s.last(); i += 2) {
					int x = s.x(i) - Dirs.dx(s.out());
					int z = s.z(i) - Dirs.dz(s.out());
					if (d.isEmpty(x, y - 2, z) || d.isAbsent(x, y - 2, z)) {
						continue;
					}
					int ox = s.x(i);
					int oz = s.z(i);
					if (d.isEmpty(ox, y - 2, oz)) {
						d.set(ox, y - 2, oz, t.stairs(s.out().getOpposite(), Half.TOP));
					}
				}
			}
		}
		d.carve(r[0] + 1, y, r[1] + 1, r[2] - 1, plate - 1, r[3] - 1);
	}

	/** Which side the stair climbs (drawn before the plan needs it, so it is decided once by the seed's first draw). */
	static boolean stairLeft0(Draft d) {
		return (d.seed() & 1) == 0;
	}

	/** A little sentry pavilion on a roof deck: four posts and a hipped cap of the roof over a 3 x 3 corner. Returns the top. */
	static int sentryPavilion(Draft d, int x0, int z0, int deck, Timber t) {
		int x1 = x0 + 2;
		int z1 = z0 + 2;
		try (var st = d.stage(StageKind.FRAME)) {
			for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
				for (int y = deck; y < deck + 3; y++) {
					d.set(c[0], y, c[1], t.post());
				}
			}
		}
		return Towers.cap(d, x0, z0, 3, deck + 2, Towers.Cap.PYRAMID, d.maxHeight() - 1);
	}
}
