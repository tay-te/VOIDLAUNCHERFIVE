package dev.voidmc.hearth.build.archetype.landmark;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Chimneys;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.landmark.Details;
import dev.voidmc.hearth.build.kit.landmark.Grounds;
import dev.voidmc.hearth.build.kit.landmark.Shapes;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import dev.voidmc.hearth.build.terrain.Site;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:landmark/lighthouse}: a light on the rocks at the water's edge. A round tower in its livery (white and
 * banded red or black as a day-mark, or plain stone) rises from a mound of weathered rock, a flared plinth at its foot,
 * little windows climbing it with the stair inside; at the top a corbelled gallery with an iron railing round a glazed
 * lantern room where the light burns, under a cap and a vane. In front stands the keeper's cottage (a bed, a hearth, the
 * log on its lectern, a chest of charts) and, where the water reaches the plot, a plank jetty on posts with mooring
 * posts and a lamp at its end. The gallery is a viewpoint; the jetty a dock.
 *
 * <p>Params (all optional): {@code bands} (boolean: a banded livery), {@code variant}.
 */
public final class Lighthouse implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("landmark/lighthouse"), ArchetypeCategory.LANDMARK, Set.of(), 1,
		11, 15, 13, 21, 26, 40, SettlementTier.TOWN,
		Set.of(Culture.MARITIME, Culture.TROPICAL, Culture.MEDITERRANEAN, Culture.NORTHERN),
		Set.of(ArchetypeInfo.SiteFlag.VERTICAL, ArchetypeInfo.SiteFlag.WATERFRONT), List.of(), 1, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int top = d.maxHeight() - 1;
		int r = 3;
		boolean side = w >= 13;
		boolean towerLeft = d.chance(0.5);
		int xt = side ? (towerLeft ? r + 1 : w - 2 - r) : (w - 1) / 2;
		int zt = Math.min(dp - 2 - r, Math.max(r + 6, dp / 2 + 1));
		d.body(xt - r, zt - r, xt + r, zt + r);
		Stone body = livery(d);
		Paint band = bandPaint(d, !(Stone.masonryWalls(d) && Stone.stony(d, Role.WALL)));
		boolean bands = band != null && d.param("bands", d.chance(0.75));
		Stone dress = Stone.dressing(d);
		Stone rock = Stone.fixed(d, StageKind.FOUNDATION, "minecraft:stone", "minecraft:andesite", "minecraft:cobblestone", "minecraft:mossy_cobblestone",
			"minecraft:stone");
		Stone sheet = Stone.sheet(d);
		Paint glass = d.mat(Role.GLASS).pane();

		// the rocks round the tower, out into the water (the tower's door is the main entrance)
		Grounds.podium(d, 0, 0, w - 1, Math.min(dp - 1, zt - r - 1), 0, true);
		rocks(d, xt, zt, r, rock);

		// the tower: a flared plinth, the shaft in its livery, windows climbing it
		// the gallery deck on a whole turn of the stair, so its last step lands by the lantern room's door
		int yG = Math.min(top - 8, Math.max(16, (int) Math.round(top * 0.68)));
		yG -= yG % 4;
		List<int[]> ring = Shapes.ring(xt, zt, r);
		List<int[]> disc = Shapes.disc(xt, zt, r);
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int[] c : disc) {
				for (int y = Math.min(-1, d.site().ground(c[0], c[1]) - 2); y <= -1; y++) {
					d.set(c[0], y, c[1], rock.full());
				}
			}
		}
		try (var s = d.stage(StageKind.WALLS)) {
			for (int y = 0; y < yG; y++) {
				boolean striped = bands && y >= 3 && ((y - 3) / 4) % 2 == 1;
				for (int[] c : ring) {
					d.set(c[0], y, c[1], y < 2 ? dress.full() : striped ? band : body.full());
				}
			}
		}
		Details.ringCourse(d, xt, zt, r, 1, dress);
		// headroom over the doorstep
		d.set(xt, 1, zt - r - 1, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
		for (int[] c : disc) {
			d.carve(c[0], 0, c[1], c[0], yG - 1, c[1]);
		}
		// windows: one every few rows, turning round the tower with the stair
		Direction[] turn = {Dirs.FRONT, Dirs.RIGHT, Dirs.BACK, Dirs.LEFT};
		try (var s = d.stage(StageKind.OPENINGS)) {
			int k = 1;
			for (int y = 5; y + 2 < yG - 1; y += 4, k++) {
				Direction dir = turn[k % 4];
				int wx = xt + Dirs.dx(dir) * r;
				int wz = zt + Dirs.dz(dir) * r;
				d.set(wx, y, wz, glass);
				d.set(wx, y + 1, wz, glass);
			}
		}
		// a course at mid-height, the watch room's windows on the four faces under the gallery
		Details.ringCourse(d, xt, zt, r, yG / 2, dress);
		try (var s = d.stage(StageKind.OPENINGS)) {
			for (Direction dir : Dirs.HORIZONTAL) {
				int wx = xt + Dirs.dx(dir) * r;
				int wz = zt + Dirs.dz(dir) * r;
				d.set(wx, yG - 4, wz, glass);
				d.set(wx, yG - 3, wz, glass);
			}
		}
		// the gallery: a corbelled ring with an iron railing, the deck over the shaft
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : Shapes.disc(xt, zt, r)) {
				d.set(c[0], yG - 1, c[1], dress.full());
			}
		}
		Details.ringCourse(d, xt, zt, r, yG - 2, dress);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : Shapes.ring(xt, zt, r + 1)) {
				if (d.allowed(c[0], yG - 1, c[1])) {
					d.set(c[0], yG - 1, c[1], dress.has(Form.SLAB) ? dress.slab(SlabType.TOP) : dress.full());
					d.set(c[0], yG, c[1], Paint.of(Blocks.IRON_BARS.defaultBlockState()));
				}
			}
		}
		// the lantern room: a glazed ring round the light, a cap, a vane
		int lr = r - 1;
		try (var s = d.stage(StageKind.OPENINGS)) {
			for (int y = yG; y < yG + 3; y++) {
				for (int[] c : Shapes.ring(xt, zt, lr)) {
					boolean mullion = Math.abs(c[0] - xt) == Math.abs(c[1] - zt);
					d.set(c[0], y, c[1], mullion ? Paint.of(Blocks.IRON_BARS.defaultBlockState()) : Paint.of(Blocks.GLASS.defaultBlockState()));
				}
			}
			// a door out to the gallery
			int dx = xt;
			int dz = zt - lr;
			d.set(dx, yG, dz, Blocks.AIR.defaultBlockState());
			d.set(dx, yG + 1, dz, Blocks.AIR.defaultBlockState());
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(xt, yG, zt, Blocks.SEA_LANTERN.defaultBlockState());
			d.set(xt, yG + 1, zt, Blocks.SEA_LANTERN.defaultBlockState());
		}
		int peak;
		try (var s = d.stage(StageKind.ROOF)) {
			for (int[] c : Shapes.disc(xt, zt, lr)) {
				d.set(c[0], yG + 3, c[1], sheet.full());
			}
			Details.ringCourse(d, xt, zt, lr, yG + 3, sheet);
			Shapes.turned(d, xt, zt, yG + 4, new double[]{1.6, 1.0, 0.4}, sheet.full(), sheet);
			peak = yG + 7;
			if (peak <= top) {
				d.set(xt, peak, zt, Details.rod());
			}
		}
		d.poi(PoiType.VIEWPOINT, xt, yG, zt - r, Dirs.FRONT);
		d.poi(PoiType.VIEWPOINT, xt, yG, zt + r, Dirs.BACK);

		// the door at the foot, the spiral stair round a post up to the lantern room
		Side towerFront = new Side(Dirs.FRONT, xt, zt - r, 1);
		try (var s = d.stage(StageKind.OPENINGS)) {
			Details.arch(d, towerFront, 0, 1, 0, 3, dress, glass);
		}
		Openings.door(d, towerFront, xt, 0, 1, new Openings.DoorOpts(true, true, true));
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int[] c : disc) {
				d.set(c[0], -1, c[1], dress.full());
			}
		}
		Shapes.spiral(d, xt, zt, 0, yG, 1, dress, dress.full());
		Groundwork.approach(d, xt, 0, zt - r - 1);

		// the keeper's cottage
		cottage(d, side, towerLeft, xt, zt - r - 1);
		// the jetty, where the water comes into the plot
		jetty(d, xt, zt, r);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(top, peak + 1));
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	/** The tower's body: the style's own masonry walls where they are white or stone, else white render. */
	private static Stone livery(Draft d) {
		if (Stone.masonryWalls(d) && Stone.stony(d, Role.WALL)) {
			return Stone.of(d, Role.WALL);
		}
		return Stone.fixed(d, StageKind.WALLS, "minecraft:white_concrete");
	}

	/**
	 * The bands: a day-mark painted as real ones are, red or black on the tower's white (black only where the town's
	 * cloth is black and not red, or now and then by seed; a stone tower is always banded red, black would not show).
	 */
	private static Paint bandPaint(Draft d, boolean white) {
		boolean red = false;
		boolean black = false;
		for (var e : d.mat(Role.CLOTH).palette().entries()) {
			Block wool = e.material().block(Form.FULL);
			if (wool == null) {
				continue;
			}
			String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(wool).getPath();
			red |= name.equals("red_wool");
			black |= name.equals("black_wool");
		}
		boolean dark = white && (black && !red || !red && !black && d.chance(0.3));
		return Paint.of(Blocks.CONCRETE.pick(dark ? net.minecraft.world.item.DyeColor.BLACK : net.minecraft.world.item.DyeColor.RED).defaultBlockState());
	}

	/** A mound of weathered rock round the tower's foot, highest against it, running out into any water. */
	private static void rocks(Draft d, int xt, int zt, int r, Stone rock) {
		int reach = r + 3;
		try (var s = d.stage(StageKind.EARTHWORK)) {
			for (int z = zt - reach; z <= zt + reach; z++) {
				for (int x = xt - reach; x <= xt + reach; x++) {
					if (x < 0 || z < 0 || x >= d.frontage() || z >= d.depth()) {
						continue;
					}
					double dist = Math.hypot(x - xt, z - zt) + Noise.unit(d.seed(), x, 9, z) * 1.3;
					if (dist > reach) {
						continue;
					}
					int g = d.site().ground(x, z);
					int topRow = dist < r + 1.5 ? -1 : dist < r + 2.5 ? -2 : -3;
					topRow = Math.max(topRow, Math.min(-1, g - 1));
					int wy = d.site().water(x, z);
					if (wy != Site.NONE) {
						topRow = Math.max(topRow, Math.min(-1, wy - 1));
					}
					for (int y = Math.min(g - 2, topRow); y <= topRow; y++) {
						d.set(x, y, z, rock.full());
					}
					for (int y = topRow + 1; y < g && y < 0; y++) {
						d.set(x, y, z, Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
	}

	/** The keeper's cottage: a small gabled house before the tower, a bed, a hearth, the log, a chest. */
	private static void cottage(Draft d, boolean side, boolean towerLeft, int xt, int zMax) {
		int w = d.frontage();
		int cw = 7;
		int cd = Math.min(6, zMax);
		if (cd < 5) {
			return;
		}
		int cx0;
		if (side) {
			cx0 = towerLeft ? w - cw : 0;
		} else {
			cx0 = xt + 2 <= w - cw ? xt + 2 : 0;
			if (cx0 + cw > w) {
				return;
			}
		}
		int cx1 = cx0 + cw - 1;
		int cz0 = Math.max(1, zMax - cd + 1);
		int cz1 = zMax;
		if (!side && xt >= cx0 - 1 && xt <= cx1 + 1) {
			// in front of a centred tower there is room only beside its path
			return;
		}
		Mat wall = d.mat(Role.WALL);
		Mat frame = d.mat(Role.FRAME);
		Grounds.footings(d, cx0, cz0, cx1, cz1, -1);
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = cz0 + 1; z < cz1; z++) {
				for (int x = cx0 + 1; x < cx1; x++) {
					d.set(x, -1, z, d.mat(Role.FLOOR).full());
				}
			}
		}
		for (Side s : Side.of(cx0, cz0, cx1, cz1)) {
			for (int i = 0; i < s.length(); i++) {
				for (int y = 0; y <= 3; y++) {
					boolean corner = s.isCorner(i);
					Paint p = y == 0 ? d.mat(Role.PLINTH).full() : corner ? frame.log(Direction.Axis.Y) : y == 3 ? d.mat(Role.BEAM).log(s.axis()) : wall.full();
					try (var sc = d.stage(corner || y == 3 ? StageKind.FRAME : StageKind.WALLS)) {
						d.set(s.x(i), y, s.z(i), p);
					}
				}
			}
		}
		d.carve(cx0 + 1, 0, cz0 + 1, cx1 - 1, 2, cz1 - 1);
		Side fr = Side.of(cx0, cz0, cx1, cz1, Dirs.FRONT);
		int door = cx0 + 2;
		Openings.door(d, fr, door, 0, 1, Openings.DoorOpts.BACK);
		Openings.window(d, fr, cx1 - 2, 1, 1, 1, Openings.WindowStyle.DRESSED);
		Side back = Side.of(cx0, cz0, cx1, cz1, Dirs.BACK);
		Openings.window(d, back, back.column(back.middle()), 1, 1, 1, Openings.WindowStyle.PLAIN);
		Roofs.Roof roof = Roofs.build(d, cx0, cz0, cx1, cz1, 3, Habits.RoofForm.GABLE, Direction.Axis.X, Roofs.Opts.of(d), false, false).roof();
		// the hearth against the end wall, its chimney; a bed, the log, a chest of charts
		Furniture.hearth(d, cx1 - 1, 0, cz1 - 1, Dirs.LEFT, false);
		Chimneys.stack(d, cx1 - 1, cz1 - 1, 2, roof, roof.ridgeY + 1);
		Rooms.Room room = new Rooms.Room(cx0 + 1, cz0 + 1, cx1 - 1, cz1 - 1, 0, 2);
		room.keep(door, cz0 + 1);
		room.block(cx1 - 1, cz1 - 1);
		room.keep(cx1 - 2, cz1 - 1);
		Rooms.furnish(d, room, Rooms.Type.BEDROOM, 1, JobType.NONE);
		try (var s = d.stage(StageKind.INTERIOR)) {
			if (d.isEmpty(cx0 + 1, 0, cz1 - 1)) {
				d.set(cx0 + 1, 0, cz1 - 1, States.facing(Blocks.LECTERN, Dirs.RIGHT));
				d.poi(PoiType.LECTERN, cx0 + 2, 0, cz1 - 1, Dirs.LEFT);
			}
		}
		Rooms.furnish(d, room, Rooms.Type.STORAGE, 0, JobType.NONE);
		Groundwork.approach(d, door, 0, cz0 - 1);
		if (cz0 >= 2 && Grounds.standable(d, cx1, 0, cz0 - 1)) {
			Garden.barrels(d, cx1, 0, cz0 - 1, Dirs.LEFT, 2);
		}
	}

	/** A plank jetty out over the water behind the rocks, on posts, with mooring posts and a lamp at its end. */
	private static void jetty(Draft d, int xt, int zt, int r) {
		int w = d.frontage();
		int dp = d.depth();
		// the water nearest the tower, behind it or beside it
		int bestX = -1;
		int bestZ = -1;
		double best = Double.MAX_VALUE;
		for (int z = 0; z < dp; z++) {
			for (int x = 0; x < w; x++) {
				if (!d.site().isWater(x, z) || d.site().waterDepth(x, z) < 1) {
					continue;
				}
				double dd = Math.hypot(x - xt, z - zt);
				if (dd > r + 2 && dd < best) {
					best = dd;
					bestX = x;
					bestZ = z;
				}
			}
		}
		if (bestX < 0) {
			return;
		}
		Mat deck = d.mat(Role.FLOOR);
		Mat post = d.mat(Role.STILT);
		Mat fence = d.mat(Role.FENCE);
		int wy = d.site().water(bestX, bestZ);
		int deckY = Math.max(0, wy) - 1;
		// a straight run from the rocks toward that water, then on out as far as the water goes (inside the plot)
		int dx = Integer.signum(bestX - xt);
		int dz = Integer.signum(bestZ - zt);
		if (Math.abs(bestX - xt) > Math.abs(bestZ - zt)) {
			dz = 0;
		} else {
			dx = 0;
		}
		int x = xt + dx * (r + 1);
		int z = zt + dz * (r + 1);
		int laid = 0;
		int lastX = x;
		int lastZ = z;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			while (x >= 0 && z >= 0 && x < w && z < dp && laid < 12) {
				for (int k = -1; k <= 1; k++) {
					int px = x + (dz != 0 ? k : 0);
					int pz = z + (dx != 0 ? k : 0);
					if (px < 0 || pz < 0 || px >= w || pz >= dp) {
						continue;
					}
					d.set(px, deckY, pz, deck.full());
					d.set(px, deckY + 1, pz, Blocks.AIR.defaultBlockState());
					if (k != 0 && laid % 3 == 0) {
						int g = d.site().ground(px, pz);
						for (int y = Math.min(deckY - 1, g - 2); y < deckY; y++) {
							d.set(px, y, pz, post.log(Direction.Axis.Y));
						}
						d.set(px, deckY + 1, pz, fence.fence());
					}
				}
				lastX = x;
				lastZ = z;
				x += dx;
				z += dz;
				laid++;
			}
		}
		if (laid >= 2) {
			Garden.lampPost(d, lastX + (dz != 0 ? 1 : 0), deckY + 1, lastZ + (dx != 0 ? 1 : 0), 2, null);
			d.poi(PoiType.DOCK, lastX, deckY + 1, lastZ, dx != 0 ? (dx > 0 ? Dirs.RIGHT : Dirs.LEFT) : (dz > 0 ? Dirs.BACK : Dirs.FRONT));
		}
	}
}
