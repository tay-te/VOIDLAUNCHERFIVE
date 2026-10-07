package dev.voidmc.hearth.build.archetype.civic;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.sociallife.Venue;
import dev.voidmc.hearth.build.kit.sociallife.Fair;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:civic/tilt_yard}: the lists where knights run at each other for the town's holiday, outside the walls.
 * A long fenced field: down its length the tilt, the barrier the riders charge along either side of, painted posts with
 * the town's colours at its ends; on one side the stands, three tiers of benches stepping up, and at their middle the
 * lord's box under a canopy of canvas on posts, its high seat between banners; on the other side, at either end, the
 * knights' pavilions, canvas tents banded in the colour of the house that holds the tilt with a pennant on the pole,
 * the arms chest inside; at the far end the quintain, the pivoting target squires practise on; lamps at the corners, the way in through a gate from the street.
 *
 * <p>POIs: SEATs (the stands), THRONE (the lord's seat in the box), MEETING_POINT (the lists), STORAGE (the arms chests),
 * the DOOR (the gate). Params: {@code variant} (int).
 */
public final class TiltYard implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("civic/tilt_yard"), ArchetypeCategory.CIVIC, Set.of(), 0, 15, 21, 33, 45, 7, 10,
		SettlementTier.TOWN, Set.of(), Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 1, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int maxH = d.maxHeight();
		int base = 0;
		Groundwork.level(d, 0, 0, w - 1, dp - 1, base, true);

		// --- the plan across the field: the stands on the left, the lists, the pavilions on the right
		int sx0 = 1;
		int sx1 = 4;
		int lx0 = sx1 + 1;
		int lx1 = w - 6;
		int tilt = (lx0 + lx1) / 2;
		int z0 = 5;
		int z1 = dp - 6;
		Mat path = d.mat(Role.PATH);
		Mat frame = d.mat(Role.FRAME);
		Mat wood = d.mat(Role.FURNITURE);
		Mat roof = d.mat(Role.ROOF);

		// the track of the lists: beaten sand and earth
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 2; z <= dp - 3; z++) {
				for (int x = lx0; x <= lx1; x++) {
					if (d.site().isWater(x, z)) {
						continue;
					}
					double u = Noise.unit(d.seed(), x, 1, z);
					d.set(x, base - 1, z, u < 0.55 ? Blocks.COARSE_DIRT.defaultBlockState() : u < 0.8 ? path.full().at(x, base - 1, z)
						: Blocks.ROOTED_DIRT.defaultBlockState());
				}
			}
		}

		// --- the fence round the field, the gate from the street at the front of the lists
		List<int[]> ring = new ArrayList<>();
		for (int x = 0; x < w; x++) {
			ring.add(new int[]{x, 0});
		}
		for (int z = 1; z < dp; z++) {
			ring.add(new int[]{w - 1, z});
		}
		for (int x = w - 2; x >= 0; x--) {
			ring.add(new int[]{x, dp - 1});
		}
		for (int z = dp - 2; z >= 1; z--) {
			ring.add(new int[]{0, z});
		}
		int gateX = tilt;
		int gateAt = gateX;
		Garden.fence(d, ring, gateAt, Dirs.FRONT);
		// a wider way in: the gate's neighbours open too
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int x : new int[]{gateX - 1, gateX + 1}) {
				d.set(x, base, 0, Blocks.AIR.defaultBlockState());
			}
			for (int x = gateX - 1; x <= gateX + 1; x++) {
				for (int z = 0; z <= 2; z++) {
					d.set(x, base - 1, z, path.full());
				}
			}
		}
		d.entrance(gateX, base, -1);
		d.poi(PoiType.DOOR, gateX, base, -1, Dirs.BACK);
		for (int x : new int[]{gateX - 2, gateX + 2}) {
			Lighting.post(d, x, base, 1);
		}

		// --- the tilt: a barrier of rails down the middle of the lists, painted posts with the colours at its ends
		DyeColor[] colours = colours(d);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = z0 + 2; z <= z1 - 2; z++) {
				d.set(tilt, base, z, frame.fence());
			}
			for (int z : new int[]{z0 + 1, z1 - 1}) {
				d.set(tilt, base, z, frame.log(Direction.Axis.Y));
				d.set(tilt, base + 1, z, frame.log(Direction.Axis.Y));
				d.set(tilt, base + 2, z, frame.fence());
			}
		}
		Venue.banner(d, tilt, base + 1, z0, Dirs.FRONT, colours[0].getName(), List.of(), StageKind.EXTERIOR);
		Venue.banner(d, tilt, base + 1, z1, Dirs.BACK, colours[1].getName(), List.of(), StageKind.EXTERIOR);
		d.poi(PoiType.MEETING_POINT, tilt + 2, base, (z0 + z1) / 2, Dirs.LEFT);

		// --- the stands: three tiers stepping up from the lists, the lord's box in the middle under a canopy
		int mid = (z0 + z1) / 2;
		int boxZ0 = mid - 2;
		int boxZ1 = mid + 2;
		Mat found = d.mat(Role.FOUNDATION);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int z = z0; z <= z1; z++) {
				for (int tier = 0; tier < 3; tier++) {
					int x = sx1 - tier;
					// the tier's deck: boards on a frame, raised a block for each step back
					for (int y = base; y < base + tier; y++) {
						boolean post = (z - z0) % 4 == 0 || z == z1;
						d.set(x, y, z, post ? frame.log(Direction.Axis.Y) : wood.has(Form.SLAB) ? wood.full() : wood.full());
					}
				}
				// the back rail of the top tier
				d.set(sx0, base, z, frame.log(Direction.Axis.Y));
				d.set(sx0, base + 1, z, frame.log(Direction.Axis.Y));
				d.set(sx0, base + 2, z, (z - z0) % 4 == 0 ? frame.log(Direction.Axis.Y) : frame.fence());
				if ((z - z0) % 4 == 0) {
					d.set(sx0, base + 3, z, frame.fence());
					// a lantern on every fourth post of the back rail, lighting the stands and the way behind them
					if (base + 4 < maxH - 1) {
						d.set(sx0, base + 4, z, States.lantern(Blocks.LANTERN, false));
					}
				}
			}
		}
		for (int z = z0; z <= z1; z++) {
			boolean box = z >= boxZ0 && z <= boxZ1;
			for (int tier = 0; tier < 3; tier++) {
				int x = sx1 - tier;
				int y = base + tier;
				// gaps in the benches to climb the tiers (every sixth row), and the box's own seating
				if ((z - z0) % 6 == 3 && !box) {
					try (var s = d.stage(StageKind.INTERIOR)) {
						d.set(x, y, z, wood.stairs(Dirs.LEFT, Half.BOTTOM));
					}
					continue;
				}
				if (box && tier == 2 && z == mid) {
					throne(d, x, y, z);
					continue;
				}
				if (box && tier < 2) {
					// the box's front: a rail of the frame with the lord's colours
					if (tier == 0) {
						try (var s = d.stage(StageKind.INTERIOR)) {
							d.set(x, y, z, frame.fence());
						}
						continue;
					}
				}
				Venue.bench(d, x, y, z, Dirs.BACK, 1, Dirs.LEFT);
			}
		}
		// the canopy over the box: posts at its corners, a roof of cloth (the roofing where no cloth will do)
		int cy = base + 5;
		if (cy < maxH - 1) {
			try (var s = d.stage(StageKind.ROOF)) {
				for (int z : new int[]{boxZ0, boxZ1}) {
					for (int x : new int[]{sx0, sx1}) {
						for (int y = base; y < cy; y++) {
							if (d.isEmpty(x, y, z) || y >= base + 3 || x == sx1) {
								d.set(x, y, z, frame.fence());
							}
						}
					}
				}
				for (int z = boxZ0; z <= boxZ1; z++) {
					for (int x = sx0; x <= sx1; x++) {
						boolean edge = z == boxZ0 || z == boxZ1;
						// undyed canvas
						Block wool = Blocks.WOOL.pick(colours[2]);
						d.set(x, cy, z, wool.defaultBlockState());
					}
				}
				// the valance along the canopy's front, in the colour of the house that holds the tilt
				for (int z = boxZ0 + 1; z <= boxZ1 - 1; z++) {
					d.set(sx1, cy - 1, z, Blocks.WOOL.pick(colours[0]).defaultBlockState());
				}
			}
		}
		// --- the pavilions at either end on the right, the quintain at the far end of the lists
		int px = lx1 + 3;
		pavilion(d, px, base, z0 + 2, colours[0], colours[0], maxH);
		pavilion(d, px, base, z1 - 2, colours[0], colours[1], maxH);
		quintain(d, tilt + 2, base, dp - 4);
		// hay for the horses and a trough by each pavilion
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z : new int[]{z0 + 5, z1 - 5}) {
				if (Venue.standable(d, px, base, z)) {
					d.set(px, base, z, Blocks.HAY_BLOCK.defaultBlockState());
				}
			}
		}
		// lamps down the far side of the field, behind the pavilions
		for (int z = z0; z <= z1; z += 6) {
			if (Venue.standable(d, w - 2, base, z)) {
				Lighting.post(d, w - 2, base, z);
			}
		}
		// lamps at the stands' ends and down the pavilions' side
		for (int[] c : new int[][]{{sx1 + 1, z0 - 1}, {sx1 + 1, z1 + 1}, {px, mid}, {lx1 + 1, z0 - 1}, {lx1 + 1, z1 + 1}}) {
			if (Venue.standable(d, c[0], base, c[1])) {
				Lighting.post(d, c[0], base, c[1]);
			}
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, maxH - 1);
	}

	/**
	 * The colours of the day: the house that holds the tilt (its valances and pavilion bands), the challenger (his
	 * pennant and his end of the tilt), and undyed canvas for the cloth between; dyed as cloth was ({@link Fair#natural}).
	 */
	private static DyeColor[] colours(Draft d) {
		// the town's own colours where it has arms (the planner's banner param), else the day's by seed
		List<String> arms = dev.voidmc.hearth.build.kit.civic.Signs.armsColours(d);
		if (!arms.isEmpty()) {
			DyeColor a = DyeColor.byName(Fair.natural(arms.get(0)), DyeColor.RED);
			DyeColor b = arms.size() > 1 ? DyeColor.byName(Fair.natural(arms.get(1)), DyeColor.WHITE) : a == DyeColor.WHITE ? DyeColor.RED : DyeColor.WHITE;
			if (a == DyeColor.WHITE) {
				// the house's colour must show on the canvas
				a = b == DyeColor.WHITE ? DyeColor.RED : b;
				b = DyeColor.WHITE;
			}
			return new DyeColor[]{a, b, DyeColor.WHITE};
		}
		DyeColor[][] sets = {{DyeColor.RED, DyeColor.BLUE, DyeColor.WHITE}, {DyeColor.RED, DyeColor.YELLOW, DyeColor.WHITE},
			{DyeColor.BLUE, DyeColor.YELLOW, DyeColor.WHITE}, {DyeColor.GREEN, DyeColor.RED, DyeColor.WHITE}, {DyeColor.BLACK, DyeColor.YELLOW, DyeColor.WHITE}};
		return sets[(int) (Noise.unit(d.seed(), 4, 4, 4) * sets.length)];
	}

	/** The lord's high seat at the top of the box: a seat of the furniture wood between two banners, the THRONE. */
	private static void throne(Draft d, int x, int y, int z) {
		Mat wood = d.mat(Role.FURNITURE);
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(x, y, z, wood.stairs(Dirs.LEFT, Half.BOTTOM));
			for (int k : new int[]{-1, 1}) {
				d.set(x, y, z + k, wood.slab(SlabType.BOTTOM));
			}
		}
		d.poi(PoiType.THRONE, x, y, z, Dirs.RIGHT);
		d.poi(PoiType.SEAT, x, y, z, Dirs.RIGHT);
	}

	/**
	 * A knight's pavilion centred on (cx, y, cz): a round tent five across of undyed canvas two high under a band of the
	 * house's colour, its roof drawn in to a cap and a pole with the knight's pennant ({@code pennant}), the door toward the
	 * lists; inside the arms chest and a stand.
	 */
	private static void pavilion(Draft d, int cx, int y, int cz, DyeColor house, DyeColor pennant, int maxH) {
		Block canvas = Blocks.WOOL.pick(DyeColor.WHITE);
		Block band = Blocks.WOOL.pick(house);
		Mat frame = d.mat(Role.FRAME);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
					boolean wall = !corner && (Math.abs(dx) == 2 || Math.abs(dz) == 2);
					int x = cx + dx;
					int z = cz + dz;
					if (wall) {
						boolean door = dx == -2 && dz == 0;
						for (int yy = y; yy < y + 2; yy++) {
							d.set(x, yy, z, door ? Blocks.AIR.defaultBlockState() : canvas.defaultBlockState());
						}
						// the band at the eaves, dagged: colour and canvas by turns
						if (y + 2 < maxH) {
							d.set(x, y + 2, z, (((dx + dz) & 1) == 0 ? band : canvas).defaultBlockState());
						}
					}
					if (!corner && Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && y + 3 < maxH) {
						d.set(x, y + 3, z, (dx == 0 && dz == 0 ? band : canvas).defaultBlockState());
					}
				}
			}
			// the door's lintel and the pole through the cap
			if (y + 2 < maxH) {
				d.set(cx - 2, y + 2, cz, band.defaultBlockState());
			}
			for (int yy = y + 4; yy < Math.min(maxH - 1, y + 6); yy++) {
				d.set(cx, yy, cz, frame.fence());
			}
		}
		if (y + 5 < maxH - 1) {
			Venue.banner(d, cx + 1, y + 5, cz, Dirs.RIGHT, pennant.getName(), List.of(), StageKind.ROOF);
		}
		// inside: the arms chest against the back, a stand, a lantern
		Furniture.chest(d, cx + 1, y, cz, Dirs.LEFT, List.of(new Object[]{"minecraft:iron_sword", 1}, new Object[]{"minecraft:shield", 1},
			new Object[]{"minecraft:stick", 4}));
		d.poi(PoiType.STORAGE, cx, y, cz, Dirs.RIGHT);
		Furniture.barrel(d, cx + 1, y, cz + 1, Dirs.LEFT);
		Furniture.hangingLantern(d, cx, y + 3, cz, 0);
		Lighting.room(d, cx - 1, cz - 1, cx + 1, cz + 1, y);
	}

	/** The quintain: a post with a pivoting arm, a shield on one end and a sack on the other. */
	private static void quintain(Draft d, int x, int y, int z) {
		Mat frame = d.mat(Role.FRAME);
		if (!Venue.standable(d, x, y, z) || x + 1 >= d.frontage() || x - 1 < 0) {
			return;
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, frame.log(Direction.Axis.Y));
			d.set(x, y + 1, z, frame.log(Direction.Axis.Y));
			d.set(x, y + 2, z, frame.fence());
			d.set(x - 1, y + 2, z, frame.fence());
			d.set(x + 1, y + 2, z, frame.fence());
			d.set(x + 1, y + 1, z, Blocks.HAY_BLOCK.defaultBlockState());
		}
		// the shield on the post's face toward the riders
		Venue.banner(d, x, y + 1, z - 1, Dirs.FRONT, "red", List.of(), StageKind.EXTERIOR);
	}
}
