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
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.civic.Halls;
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
				// a lantern on each painted post, so the lists are lit for the evening's feast
				d.set(tilt, base + 3, z, States.lantern(Blocks.LANTERN, false));
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
			// the house's banners on the box's front posts, toward the lists
			for (int z : new int[]{boxZ0, boxZ1}) {
				Venue.banner(d, sx1 + 1, base + 3, z, Dirs.RIGHT, colours[0].getName(), List.of(), StageKind.EXTERIOR);
			}
		}
		// --- the pavilions at either end on the right, the quintain at the far end of the lists
		// (inside the fence, a block off it)
		int px = w - 4;
		Tent kind = Tent.of(d);
		int tents = 0;
		for (int[] tz : new int[][]{{z0 + 2, 0}, {z1 - 2, 1}}) {
			if (!fits(d, px - 2, tz[0] - 3, px + 2, tz[0] + 3, base)) {
				continue;
			}
			tents++;
			tent(d, kind, px, base, tz[0], colours[0], colours[tz[1]], maxH);
			// the horses' trough and the squire's grindstone by the tent's door
			int wx = px - 3;
			int wz = tz[0] + (tz[1] == 0 ? 2 : -2);
			if (Venue.standable(d, wx, base, wz)) {
				d.inStage(StageKind.EXTERIOR, () -> d.set(wx, base, wz, Blocks.WATER_CAULDRON.defaultBlockState()
					.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_CAULDRON, 3)));
			}
			int gz = tz[0] + (tz[1] == 0 ? -2 : 2);
			if (Venue.standable(d, wx, base, gz)) {
				d.inStage(StageKind.EXTERIOR, () -> d.set(wx, base, gz, States.facing(Blocks.GRINDSTONE, Dirs.LEFT)));
			}
		}
		// no ground level enough for a tent: the arms chest and a barrel stand in the open on the tents' side, by the lists
		if (tents == 0) {
			armsChest(d, px, base, z0, z1);
		}
		// the common folk's low stand along the far side, between the tents, facing the tilt
		for (int z = z0 + 7; z <= z1 - 7; z++) {
			if (Venue.standable(d, lx1 + 1, base, z) && (z - z0) % 6 != 3) {
				Venue.bench(d, lx1 + 1, base, z, Dirs.BACK, 1, Dirs.RIGHT);
			}
		}
		// the judges' table across the lists' near end, by the gate
		int jz = z0 - 2;
		if (jz >= 3 && Venue.standable(d, tilt - 3, base, jz) && Venue.standable(d, tilt - 2, base, jz)) {
			Venue.trestle(d, tilt - 3, base, jz, Dirs.RIGHT, 2);
			for (int x : new int[]{tilt - 3, tilt - 2}) {
				if (Venue.standable(d, x, base, jz - 1)) {
					Furniture.chair(d, x, base, jz - 1, Dirs.FRONT);
				}
			}
		}
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
		dev.voidmc.hearth.build.kit.civic.Halls.finish(d);
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

	/** How a culture pitches the knights' tents. */
	enum Tent {
		/** A ridge tent of canvas, its gables to the lists' ends, guyed to stakes (the west and the north). */
		RIDGE,
		/** A round felt yurt under a cone (the steppe's horse games). */
		YURT,
		/** A square pavilion on red posts under a tiered canvas roof (the karst's archery ground). */
		PAVILION,
		/** An open shade of striped cloth on four poles over a carpet (the desert's riding ground). */
		SHADE,
		/** A ramada: four posts, a roof of poles laid across and brush heaped on them, a wall of wattle at its back (the mesa). */
		RAMADA;

		static Tent of(Draft d) {
			return switch (dev.voidmc.hearth.build.kit.civic.Tradition.of(d)) {
				case STEPPE -> YURT;
				case KARST -> PAVILION;
				case ADOBE -> dev.voidmc.hearth.build.kit.civic.Pueblo.is(d) ? RAMADA : SHADE;
				default -> RIDGE;
			};
		}
	}

	/**
	 * The arms chest where no tent could be pitched: on the first standable cell of the tents' side (column {@code px} and
	 * its neighbours, from the near end) with a standable cell before it toward the lists, a barrel beside it.
	 */
	private static void armsChest(Draft d, int px, int y, int z0, int z1) {
		for (int z = z0; z <= z1; z++) {
			for (int x : new int[]{px, px + 1, px - 1, px + 2}) {
				if (x - 1 < 0 || x >= d.frontage() || !Venue.standable(d, x, y, z) || !Venue.standable(d, x - 1, y, z)) {
					continue;
				}
				Furniture.chest(d, x, y, z, Dirs.LEFT, List.of(new Object[]{"minecraft:iron_sword", 1}, new Object[]{"minecraft:shield", 1},
					new Object[]{"minecraft:stick", 4}));
				if (z + 1 <= z1 && Venue.standable(d, x, y, z + 1)) {
					Furniture.barrel(d, x, y, z + 1, Dirs.LEFT);
				}
				return;
			}
		}
	}

	/** Every cell of the rectangle is level ground at y (nothing pitched over a drop or water). */
	private static boolean fits(Draft d, int x0, int z0, int x1, int z1, int y) {
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				if (x < 0 || z < 0 || x >= d.frontage() || z >= d.depth() || !Venue.standable(d, x, y, z)) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * A knight's tent centred on (cx, y, cz), its door toward the lists (the left), in the culture's manner ({@link Tent}):
	 * canvas banded in the colour of the house that holds the tilt, the knight's pennant ({@code pennant}) on its pole;
	 * inside the arms chest, a barrel and a lantern.
	 */
	private static void tent(Draft d, Tent kind, int cx, int y, int cz, DyeColor house, DyeColor pennant, int maxH) {
		Block canvas = Blocks.WOOL.pick(kind == Tent.YURT ? DyeColor.LIGHT_GRAY : DyeColor.WHITE);
		Block band = Blocks.WOOL.pick(house);
		Mat frame = d.mat(Role.FRAME);
		int top = y + 4;
		if (top + 2 >= maxH) {
			return;
		}
		try (var s = d.stage(StageKind.WALLS)) {
			switch (kind) {
				case RIDGE -> {
					// the walls a block high, the roof's two pitches over them in canvas, the eaves banded, gables at the ends
					for (int dx = -2; dx <= 2; dx++) {
						for (int dz = -2; dz <= 2; dz++) {
							int x = cx + dx;
							int z = cz + dz;
							boolean side = Math.abs(dz) == 2;
							boolean end = Math.abs(dx) == 2;
							boolean door = dx == -2 && dz == 0;
							if ((side || end) && !door) {
								d.set(x, y, z, canvas.defaultBlockState());
							}
							if (side) {
								d.set(x, y + 1, z, band.defaultBlockState());
							} else if (Math.abs(dz) == 1) {
								d.set(x, y + 2, z, canvas.defaultBlockState());
								if (end) {
									d.set(x, y + 1, z, canvas.defaultBlockState());
								}
							} else {
								d.set(x, y + 3, z, canvas.defaultBlockState());
								if (end && !door) {
									d.set(x, y + 1, z, canvas.defaultBlockState());
									d.set(x, y + 2, z, canvas.defaultBlockState());
								}
							}
						}
					}
					d.set(cx - 2, y + 2, cz, band.defaultBlockState());
					// the poles through the ridge's ends, guy stakes out on the long sides
					for (int dx : new int[]{-2, 2}) {
						d.set(cx + dx, y + 4, cz, frame.fence());
					}
					for (int dx : new int[]{-1, 1}) {
						for (int dz : new int[]{-3, 3}) {
							if (Venue.standable(d, cx + dx, y, cz + dz)) {
								d.set(cx + dx, y, cz + dz, frame.fence());
							}
						}
					}
				}
				case YURT -> {
					// felt walls round a ring two high, the door, a cone of felt to the smoke ring
					java.util.Set<Long> ring = dev.voidmc.hearth.build.kit.landmark.Shapes.oct(cx, cz, 2, 1);
					for (long k : ring) {
						int x = (int) (k >> 32);
						int z = (int) k;
						boolean rim = false;
						for (Direction dir : Dirs.HORIZONTAL) {
							rim |= !ring.contains(dev.voidmc.hearth.build.kit.landmark.Shapes.cell(x + Dirs.dx(dir), z + Dirs.dz(dir)));
						}
						if (rim) {
							boolean door = x == cx - 2 && z == cz;
							for (int yy = y; yy < y + 2; yy++) {
								if (!door) {
									d.set(x, yy, z, (yy == y + 1 && ((x + z) & 1) == 0 ? band : canvas).defaultBlockState());
								}
							}
							d.set(x, y + 2, z, canvas.defaultBlockState());
						} else if (Math.abs(x - cx) + Math.abs(z - cz) <= 1) {
							d.set(x, y + 3, z, (x == cx && z == cz ? band : canvas).defaultBlockState());
						} else {
							d.set(x, y + 2, z, canvas.defaultBlockState());
						}
					}
					d.set(cx, y + 4, cz, frame.fence());
				}
				case PAVILION -> {
					// red posts at the corners, canvas between them two high, a tiered roof drawn in to a cap
					Paint post = frame.log(Direction.Axis.Y);
					for (int dx = -2; dx <= 2; dx++) {
						for (int dz = -2; dz <= 2; dz++) {
							int x = cx + dx;
							int z = cz + dz;
							boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
							boolean wall = Math.abs(dx) == 2 || Math.abs(dz) == 2;
							boolean door = dx == -2 && dz == 0;
							if (wall) {
								for (int yy = y; yy < y + 2; yy++) {
									if (corner) {
										d.set(x, yy, z, post);
									} else if (!door) {
										d.set(x, yy, z, canvas.defaultBlockState());
									}
								}
								d.set(x, y + 2, z, (corner ? band : canvas).defaultBlockState());
							} else {
								d.set(x, y + 3, z, (dx == 0 && dz == 0 ? band : canvas).defaultBlockState());
							}
						}
					}
					d.set(cx, y + 4, cz, frame.fence());
				}
				case RAMADA -> {
					// four posts two high, the vigas across them, brush (hay and leaves) over, a wattle screen at the back
					Paint post = frame.log(Direction.Axis.Y);
					for (int dx : new int[]{-2, 2}) {
						for (int dz : new int[]{-2, 2}) {
							for (int yy = y; yy < y + 3; yy++) {
								d.set(cx + dx, yy, cz + dz, post);
							}
						}
					}
					for (int dz = -2; dz <= 2; dz++) {
						for (int dx = -2; dx <= 2; dx++) {
							d.set(cx + dx, y + 3, cz + dz, frame.log(Direction.Axis.X));
							if (Math.abs(dz) <= 1 && Math.abs(dx) <= 1) {
								d.set(cx + dx, y + 4, cz + dz, (((dx + dz) & 1) == 0 ? Blocks.HAY_BLOCK : Blocks.OAK_LEAVES).defaultBlockState()
									.trySetValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
							}
						}
						if (Math.abs(dz) <= 1) {
							for (int yy = y; yy < y + 2; yy++) {
								d.set(cx + 2, yy, cz + dz, frame.fence());
							}
						}
					}
					top = y + 5;
				}
				case SHADE -> {
					// four poles, a cloth roof in stripes of the house's colour and canvas, a carpet on the ground
					for (int dx : new int[]{-2, 2}) {
						for (int dz : new int[]{-2, 2}) {
							for (int yy = y; yy < y + 3; yy++) {
								d.set(cx + dx, yy, cz + dz, frame.fence());
							}
						}
					}
					for (int dx = -2; dx <= 2; dx++) {
						for (int dz = -2; dz <= 2; dz++) {
							d.set(cx + dx, y + 3, cz + dz, ((dz & 1) == 0 ? band : canvas).defaultBlockState());
							if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && !(dx == 1 && dz >= 0)) {
								d.set(cx + dx, y, cz + dz, Blocks.CARPET.pick(house).defaultBlockState());
							}
						}
					}
					d.set(cx, y + 4, cz, frame.fence());
				}
			}
		}
		Venue.banner(d, cx + 1, top, cz, Dirs.RIGHT, pennant.getName(), List.of(), StageKind.ROOF);
		// inside: the arms chest against the back, a barrel, a lantern
		Furniture.chest(d, cx + 1, y, cz, Dirs.LEFT, List.of(new Object[]{"minecraft:iron_sword", 1}, new Object[]{"minecraft:shield", 1},
			new Object[]{"minecraft:stick", 4}));
		d.poi(PoiType.STORAGE, cx, y, cz, Dirs.RIGHT);
		Furniture.barrel(d, cx + 1, y, cz + 1, Dirs.LEFT);
		Halls.lanternUnder(d, cx, y, cz, 0);
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
