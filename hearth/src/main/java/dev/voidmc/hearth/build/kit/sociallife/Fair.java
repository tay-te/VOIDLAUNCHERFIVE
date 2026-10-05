package dev.voidmc.hearth.build.kit.sociallife;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.civic.Signs;
import dev.voidmc.hearth.build.kit.civic.Trade;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The furniture of a festival green: the culture's centrepiece (the Bohemian maypole, a stripped spruce with its green
 * crown and garland and the town's ribbons; the north's midsummer pole with its crossbar and hoops; a beacon on its stone
 * stand; a field of lantern poles; the steppe's horsetail standard over a wrestling ring; a wine vat under a pergola; a
 * bonfire), the musicians' stage under its canopy, booths under canvas awnings, a garlanded arch over the way in.
 *
 * <p>Colour: a fair is undyed canvas with one colour of the house that holds it (a valance, the ribbons, the banners),
 * dyed as cloth was dyed, in madder, woad or weld ({@link #natural}); awnings are striped only where the south stripes
 * them (the mediterranean and the desert).
 */
public final class Fair {
	private Fair() {
	}

	/** What stands at the heart of a culture's festival ground. */
	public enum Centre {
		MAYPOLE, MIDSUMMER_POLE, BEACON, LANTERNS, STANDARD, VAT, BONFIRE, OBELISK
	}

	public static Centre centre(Draft d) {
		var c = d.style().culture();
		if (c == null) {
			return Centre.MAYPOLE;
		}
		return switch (c) {
			case TEMPERATE, ALPINE, WISTERIA -> Centre.MAYPOLE;
			case NORTHERN -> Centre.MIDSUMMER_POLE;
			case TUNDRA, TROPICAL, BAYOU -> Centre.BONFIRE;
			case MOORLAND, MARITIME -> Centre.BEACON;
			case KARST -> Centre.LANTERNS;
			case STEPPE -> Centre.STANDARD;
			case MEDITERRANEAN -> Centre.VAT;
			case DESERT, MESA -> Centre.OBELISK;
		};
	}

	/**
	 * The centrepiece at (x, y, z) on the ground, at most {@code room} blocks tall, facing {@code front}. Returns its top
	 * y. Everything is held up by what is under it.
	 */
	public static int centrepiece(Draft d, Centre c, int x, int y, int z, int room, Direction front) {
		return switch (c) {
			case MAYPOLE -> maypole(d, x, y, z, room, false);
			case MIDSUMMER_POLE -> maypole(d, x, y, z, room, true);
			case BEACON -> beacon(d, x, y, z, room);
			case LANTERNS -> lanternPoles(d, x, y, z, room);
			case STANDARD -> standard(d, x, y, z, room);
			case VAT -> vat(d, x, y, z, front);
			case BONFIRE -> bonfire(d, x, y, z, true);
			case OBELISK -> obelisk(d, x, y, z, room);
		};
	}

	/**
	 * The maypole: a tall stripped trunk on a stone step, its crown left green (a little fir top) and a garland of leaves
	 * round it below the crown, the town's ribbons (banners) hanging from the garland on four sides; or, {@code midsummer},
	 * the north's pole: a crossbar under the top and two leafy hoops hung from its ends.
	 */
	public static int maypole(Draft d, int x, int y, int z, int room, boolean midsummer) {
		int h = Math.max(6, Math.min(14, room - 1));
		int top = y + h - 1;
		BlockState trunk = States.axis(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y);
		BlockState leaf = States.leaves(d.style().culture() == dev.voidmc.hearth.api.Culture.WISTERIA ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.SPRUCE_LEAVES);
		Mat stone = d.mat(Role.FOUNDATION);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y - 1, z, stone.full());
			for (int yy = y; yy <= top; yy++) {
				d.set(x, yy, z, trunk);
			}
			// a wedge of stones round its foot
			for (Direction dir : Dirs.HORIZONTAL) {
				d.set(x + Dirs.dx(dir), y, z + Dirs.dz(dir), stone.has(Form.SLAB) ? stone.slab(SlabType.BOTTOM) : stone.full());
			}
			if (!midsummer) {
				// the green crown
				d.set(x, top + 1, z, leaf);
				for (Direction dir : Dirs.HORIZONTAL) {
					d.set(x + Dirs.dx(dir), top, z + Dirs.dz(dir), leaf);
				}
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx != 0 || dz != 0) {
							d.set(x + dx, top - 1, z + dz, leaf);
						}
					}
				}
				// the garland: a ring of leaves two under the crown, the ribbons hanging from it
				int gy = top - 3;
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx != 0 || dz != 0) {
							d.set(x + dx, gy, z + dz, leaf);
						}
					}
				}
				List<String> colours = naturalColours(d);
				int k = 0;
				for (Direction dir : Dirs.HORIZONTAL) {
					String col = colours.isEmpty() ? "red" : colours.get(k++ % colours.size());
					Block banner = States.block("minecraft:" + col + "_wall_banner", States.block("minecraft:white_wall_banner", Blocks.AIR));
					// the ribbon hangs on the trunk's face just under the garland
					d.set(x + Dirs.dx(dir), gy - 1, z + Dirs.dz(dir), banner.defaultBlockState().trySetValue(BlockStateProperties.HORIZONTAL_FACING, dir),
						dev.voidmc.hearth.build.draft.BlockEntities.banner(List.of()));
				}
				return top + 1;
			}
			// midsummer: the crossbar and the two hoops of leaves hung from its ends
			int by = top - 1;
			for (int k = -2; k <= 2; k++) {
				if (k != 0) {
					d.set(x + k, by, z, States.axis(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
				}
			}
			for (int k : new int[]{-2, 2}) {
				for (int yy = by - 1; yy >= by - 3; yy--) {
					d.set(x + k, yy, z, yy == by - 2 ? Blocks.AIR.defaultBlockState() : leaf);
				}
				d.set(x + k - 1, by - 2, z, leaf);
				d.set(x + k + 1, by - 2, z, leaf);
			}
			// leaves wound up the pole
			for (int yy = y + 2; yy < by - 1; yy += 2) {
				Direction dir = Dirs.HORIZONTAL[(yy - y) / 2 % 4];
				d.set(x + Dirs.dx(dir), yy, z + Dirs.dz(dir), leaf);
			}
			d.set(x, top + 1, z, leaf);
			return top + 1;
		}
	}

	/** A beacon: a stone stand two high with a fire basket on top (a campfire in a ring of iron), a step round its foot. */
	public static int beacon(Draft d, int x, int y, int z, int room) {
		Mat stone = d.mat(Role.FOUNDATION);
		int h = Math.min(3, room - 3);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y - 1, z, stone.full());
			for (int yy = y; yy < y + h; yy++) {
				d.set(x, yy, z, stone.full());
			}
			for (Direction dir : Dirs.HORIZONTAL) {
				d.set(x + Dirs.dx(dir), y, z + Dirs.dz(dir), stone.has(Form.STAIRS) ? stone.stairs(dir.getOpposite(), Half.BOTTOM) : stone.full());
				d.set(x + Dirs.dx(dir), y + h, z + Dirs.dz(dir), Blocks.IRON_BARS.defaultBlockState());
			}
			d.set(x, y + h, z, States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.SIGNAL_FIRE, false));
		}
		return y + h;
	}

	/** Lantern poles in a ring round (x, z) (radius 3), a lantern hung from an arm on each, one taller in the middle. */
	public static int lanternPoles(Draft d, int x, int y, int z, int room) {
		Mat post = d.mat(Role.FRAME);
		int h = Math.min(5, room - 2);
		int top = y;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] o : new int[][]{{0, 0, 2}, {3, 0, 0}, {-3, 0, 0}, {0, 3, 0}, {0, -3, 0}}) {
				int px = x + o[0];
				int pz = z + o[1];
				int ph = h + o[2];
				if (px < 0 || pz < 0 || px >= d.frontage() || pz >= d.depth() || y + ph + 1 >= d.maxHeight()) {
					continue;
				}
				d.set(px, y - 1, pz, d.mat(Role.FOUNDATION).full());
				for (int yy = y; yy < y + ph; yy++) {
					d.set(px, yy, pz, post.log(Direction.Axis.Y).orElse(post.full()));
				}
				d.set(px, y + ph, pz, d.mat(Role.ROOF).slab(SlabType.BOTTOM).orElse(post.full()));
				for (Direction dir : Dirs.HORIZONTAL) {
					int ax = px + Dirs.dx(dir);
					int az = pz + Dirs.dz(dir);
					if (o[2] > 0 || dir == Dirs.FRONT || dir == Dirs.BACK) {
						d.set(ax, y + ph - 1, az, post.fence().orElse(Paint.of(Blocks.SPRUCE_FENCE.defaultBlockState())));
						d.set(ax, y + ph - 2, az, States.lantern(Blocks.LANTERN, true));
					}
				}
				top = Math.max(top, y + ph);
			}
		}
		return top;
	}

	/** The steppe's standard: a tall pole with a horsetail (a dark banner) under a gilded tip, over a ring of stones. */
	public static int standard(Draft d, int x, int y, int z, int room) {
		int h = Math.min(9, room - 2);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y - 1, z, d.mat(Role.FOUNDATION).full());
			for (int yy = y; yy < y + h; yy++) {
				d.set(x, yy, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
			}
			d.set(x, y + h, z, States.block("minecraft:lightning_rod", Blocks.IRON_BARS).defaultBlockState());
			for (Direction dir : new Direction[]{Dirs.FRONT, Dirs.BACK}) {
				// two arms of the crossbar under the tip, a tail of dark cloth from each (wool over a banner)
				int ax = x + Dirs.dx(dir);
				int az = z + Dirs.dz(dir);
				d.set(ax, y + h - 2, az, Blocks.DARK_OAK_FENCE.defaultBlockState());
				d.set(ax, y + h - 3, az, Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.BROWN).defaultBlockState());
			}
			// the wrestling ring: a circle of stones round it
			for (int[] c : dev.voidmc.hearth.build.draft.Draft.ellipseCells(x + 0.5 - 0.5, z, 4, 4, false)) {
				if (c[0] >= 0 && c[1] >= 0 && c[0] < d.frontage() && c[1] < d.depth() && d.isEmpty(c[0], y, c[1])) {
					d.set(c[0], y - 1, c[1], Blocks.COARSE_DIRT.defaultBlockState());
				}
			}
		}
		return y + h;
	}

	/** The vintage: a great vat (barrels round a cauldron of grape-dark water) under a vine pergola. */
	public static int vat(Draft d, int x, int y, int z, Direction front) {
		Mat post = d.mat(Role.FRAME);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
			for (Direction dir : Dirs.HORIZONTAL) {
				d.set(x + Dirs.dx(dir), y, z + Dirs.dz(dir), States.facing(Blocks.BARREL, Direction.UP));
			}
			for (int[] k : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
				for (int yy = y; yy < y + 3; yy++) {
					d.set(x + k[0], yy, z + k[1], post.log(Direction.Axis.Y).orElse(post.full()));
				}
			}
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					boolean beam = dz == -2 || dz == 2 || dx == -2 || dx == 2;
					BlockState leaf = States.leaves(Blocks.AZALEA_LEAVES);
					if (beam && Math.abs(dx) == 2 && Math.abs(dz) == 2) {
						continue;
					}
					if (beam) {
						d.set(x + dx, y + 3, z + dz, d.mat(Role.BEAM).log(Math.abs(dz) == 2 ? Direction.Axis.X : Direction.Axis.Z));
					} else if ((dx + dz & 1) == 0) {
						d.set(x + dx, y + 3, z + dz, leaf);
					}
				}
			}
			for (int[] k : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
				d.set(x + k[0], y + 3, z + k[1], post.log(Direction.Axis.Y).orElse(post.full()));
			}
		}
		Lighting.chandelier(d, x + 1, z + 1, y + 3, y + 2);
		return y + 3;
	}

	/** A bonfire: logs leaned round a fire in a ring of stones (the fire lit; a big one in the middle is a pile of logs). */
	public static int bonfire(Draft d, int x, int y, int z, boolean big) {
		Mat stone = d.mat(Role.FOUNDATION);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y - 1, z, stone.full());
			d.set(x, y, z, States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.SIGNAL_FIRE, false));
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dz == 0) {
						continue;
					}
					int px = x + dx;
					int pz = z + dz;
					if (dx != 0 && dz != 0) {
						d.set(px, y, pz, stone.has(Form.WALL) ? stone.wall() : stone.full());
					} else if (big) {
						d.set(px, y, pz, States.axis(Blocks.SPRUCE_LOG, dx != 0 ? Direction.Axis.X : Direction.Axis.Z));
					} else {
						d.set(px, y, pz, stone.slab(SlabType.BOTTOM).orElse(stone.full()));
					}
				}
			}
			if (big) {
				d.set(x, y + 1, z, States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.SIGNAL_FIRE, false));
			}
		}
		return y + (big ? 1 : 0);
	}

	/** The desert's festival stone: a tapering obelisk of the culture's stone with a brazier on a stand either side. */
	public static int obelisk(Draft d, int x, int y, int z, int room) {
		Mat stone = d.mat(Role.WALL_ACCENT);
		Mat found = d.mat(Role.FOUNDATION);
		int h = Math.min(7, room - 2);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y - 1, z, found.full());
			for (int yy = y; yy < y + h; yy++) {
				d.set(x, yy, z, yy == y + h - 1 ? (stone.has(Form.WALL) ? stone.wall() : stone.full()) : stone.full());
			}
			for (Direction dir : Dirs.HORIZONTAL) {
				d.set(x + Dirs.dx(dir), y, z + Dirs.dz(dir), stone.stairsOrSlab(dir.getOpposite(), Half.BOTTOM));
			}
		}
		for (int k : new int[]{-3, 3}) {
			Lighting.post(d, x + k, y, z);
		}
		return y + h;
	}

	/**
	 * A booth: a counter with wares across its front (the keeper behind it) between two posts, a canvas awning over it
	 * with a valance of the fair's colour (striped canvas and colour in the south), a crate and a barrel at the back. {@code x, z} is the front-left corner seen from the
	 * customers, {@code toCustomers} the way the booth faces; three wide, three deep. Registers COUNTER and MARKET_STALL.
	 */
	public static void booth(Draft d, int x, int y, int z, Direction toCustomers) {
		Direction back = toCustomers.getOpposite();
		Direction right = Dirs.left(toCustomers);
		Mat post = d.mat(Role.FRAME);
		Mat cloth = d.mat(Role.CLOTH);
		Mat wood = d.mat(Role.FURNITURE);
		int[][] posts = {at(x, z, right, 0, back, 0), at(x, z, right, 2, back, 0), at(x, z, right, 0, back, 2), at(x, z, right, 2, back, 2)};
		try (var s = d.stage(StageKind.FRAME)) {
			for (int[] p : posts) {
				for (int yy = y; yy < y + 3; yy++) {
					d.set(p[0], yy, p[1], post.log(Direction.Axis.Y).orElse(post.full()));
				}
			}
		}
		// the counter: the middle of the front row
		int[] c = at(x, z, right, 1, back, 0);
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(c[0], y, c[1], wood.slab(SlabType.TOP));
		}
		Trade.wares(d, c[0], y + 1, c[1]);
		d.poi(PoiType.COUNTER, c[0] + Dirs.dx(back), y, c[1] + Dirs.dz(back), toCustomers);
		d.poi(PoiType.MARKET_STALL, c[0] + Dirs.dx(toCustomers), y, c[1] + Dirs.dz(toCustomers), back);
		// stock at the back between the back posts
		int[] stock = at(x, z, right, 1, back, 2);
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(stock[0], y, stock[1], States.facing(Blocks.BARREL, toCustomers));
		}
		// the awning: a lean-to of canvas rising toward the back, its front edge a valance of the fair's one colour
		// (canvas and colour in stripes where the south stripes its awnings)
		boolean stripes = striped(d);
		net.minecraft.world.item.DyeColor canvas = net.minecraft.world.item.DyeColor.WHITE;
		net.minecraft.world.item.DyeColor dyed = net.minecraft.world.item.DyeColor.byName(houseColour(d), net.minecraft.world.item.DyeColor.RED);
		try (var s = d.stage(StageKind.ROOF)) {
			for (int i = -1; i <= 3; i++) {
				for (int k = 0; k <= 2; k++) {
					int[] p = at(x, z, right, i, back, k);
					if (p[0] < 0 || p[0] >= d.frontage() || p[1] >= d.depth() || p[1] < -1) {
						continue;
					}
					int ry = y + 3;
					net.minecraft.world.item.DyeColor dye = stripes ? ((i & 1) == 0 ? canvas : dyed) : k == 0 ? dyed : canvas;
					Paint m = Paint.of(Blocks.WOOL.pick(dye).defaultBlockState());
					Paint slab = Paint.of(States.slab(Blocks.WOOL_SLAB.pick(dye), SlabType.BOTTOM));
					if (k == 0) {
						d.set(p[0], ry, p[1], slab);
					} else {
						d.set(p[0], ry, p[1], m);
						if (k == 2 && i >= 0 && i <= 2) {
							d.set(p[0], ry + 1, p[1], slab);
						}
					}
				}
			}
		}
		// a lantern under the awning over the counter
		int[] l = at(x, z, right, 1, back, 1);
		Lighting.chandelier(d, l[0], l[1], y + 3, y + 2);
	}

	private static int[] at(int x, int z, Direction right, int along, Direction back, int deep) {
		return new int[]{x + Dirs.dx(right) * along + Dirs.dx(back) * deep, z + Dirs.dz(right) * along + Dirs.dz(back) * deep};
	}

	/**
	 * The musicians' stage on the green: a boarded platform {@code w} wide and {@code dpt} deep from its front-left corner
	 * (x, z) seen from the audience, facing {@code toAudience}, on footings to the ground; a boarded back screen hung with
	 * the culture's banners, corner posts and a roof (shingles on a little gable in the timber cultures, cloth elsewhere),
	 * lanterns under it. Registers STAGE and the entertainer's job site. Returns the top y.
	 */
	public static int stage(Draft d, int x, int y, int z, int w, int dpt, Direction toAudience) {
		Direction back = toAudience.getOpposite();
		Direction right = Dirs.left(toAudience);
		Mat found = d.mat(Role.FOUNDATION);
		Mat post = d.mat(Role.FRAME);
		Mat boards = d.mat(Role.GABLE).has(Form.FULL) ? d.mat(Role.GABLE) : d.mat(Role.FLOOR);
		int[] a = at(x, z, right, 0, back, 0);
		int[] b = at(x, z, right, w - 1, back, dpt - 1);
		int x0 = Math.min(a[0], b[0]);
		int x1 = Math.max(a[0], b[0]);
		int z0 = Math.min(a[1], b[1]);
		int z1 = Math.max(a[1], b[1]);
		// footings under the platform
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int zz = z0; zz <= z1; zz++) {
				for (int xx = x0; xx <= x1; xx++) {
					int g = d.site().ground(xx, zz);
					for (int yy = Math.min(g, y) - 1; yy < y; yy++) {
						d.set(xx, yy, zz, found.full());
					}
				}
			}
		}
		// the back screen behind the back row, three high, boarded
		try (var s = d.stage(StageKind.WALLS)) {
			for (int i = 0; i < w; i++) {
				int[] p = at(x, z, right, i, back, dpt);
				if (p[0] < 0 || p[1] < 0 || p[0] >= d.frontage() || p[1] >= d.depth()) {
					continue;
				}
				int g = d.site().ground(p[0], p[1]);
				for (int yy = Math.min(g, y) - 1; yy < y + 4; yy++) {
					d.set(p[0], yy, p[1], yy < y ? found.full() : i == 0 || i == w - 1 ? post.log(Direction.Axis.Y).orElse(post.full()) : boards.full());
				}
			}
		}
		Venue.stage(d, x0, z0, x1, z1, y, toAudience, true);
		// front posts and the roof
		int[] fl = at(x, z, right, 0, back, 0);
		int[] fr = at(x, z, right, w - 1, back, 0);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int[] p : new int[][]{fl, fr}) {
				for (int yy = y + 1; yy < y + 4; yy++) {
					d.set(p[0], yy, p[1], post.fence().orElse(post.log(Direction.Axis.Y)));
				}
			}
		}
		int ry = y + 4;
		boolean timber = Tradition.of(d) == Tradition.TIMBER || Tradition.of(d) == Tradition.LOG || Tradition.of(d) == Tradition.ALPINE;
		int rx0 = Math.max(0, x0 - 1);
		int rx1 = Math.min(d.frontage() - 1, x1 + 1);
		int rz0 = Math.max(0, z0 - 1);
		int rz1 = Math.min(d.depth() - 1, z1 + 1);
		Roofs.Roof r;
		if (timber && d.mat(Role.ROOF).has(Form.STAIRS)) {
			Direction.Axis ridge = toAudience.getAxis() == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z;
			r = Roofs.raster(d, Roofs.gable(rx0, rz0, rx1, rz1, ridge, Roofs.Profile.straight(1.0)), ry, rx0, rz0, rx1, rz1, d.mat(Role.ROOF),
				d.mat(Role.ROOF_TRIM), d.habits().roof().ridge());
		} else {
			Mat cloth = d.mat(Role.CLOTH);
			r = Roofs.raster(d, Roofs.shed(rx0, rz0, rx1, rz1, back, Roofs.Profile.straight(0.5)), ry, rx0, rz0, rx1, rz1, cloth, cloth, Habits.Ridge.SLAB);
			// canvas, the low edge toward the audience a valance of the fair's colour
			String colour = houseColour(d);
			int lowX = Dirs.dx(toAudience);
			int lowZ = Dirs.dz(toAudience);
			int ex = lowX > 0 ? rx1 : rx0;
			int ez = lowZ > 0 ? rz1 : rz0;
			Roofs.Roof canopy = r;
			recloth(d, rx0, rz0, rx1, rz1, canopy, (cx, cz) -> lowX != 0 && cx == ex || lowZ != 0 && cz == ez ? colour : "white");
		}
		// lanterns under the roof over the front corners
		for (int[] p : new int[][]{fl, fr}) {
			int lx = p[0] + Dirs.dx(back);
			int lz = p[1] + Dirs.dz(back);
			if (r.covers(lx, lz) && r.bottomY(lx, lz) > y + 3) {
				Lighting.chandelier(d, lx, lz, r.bottomY(lx, lz), y + 3);
			}
		}
		return r.ridgeY;
	}

	/** A garlanded arch over the way in: two poles at x0 and x1 in row z, a beam of leaves across, banners on the poles. */
	public static void arch(Draft d, int x0, int x1, int y, int z) {
		Mat post = d.mat(Role.FRAME);
		BlockState leaf = States.leaves(Blocks.OAK_LEAVES);
		int top = y + 4;
		if (top + 1 >= d.maxHeight()) {
			return;
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int x : new int[]{x0, x1}) {
				d.set(x, y - 1, z, d.mat(Role.FOUNDATION).full());
				for (int yy = y; yy <= top; yy++) {
					d.set(x, yy, z, post.log(Direction.Axis.Y).orElse(post.full()));
				}
			}
			for (int x = x0 + 1; x < x1; x++) {
				d.set(x, top, z, (x - x0) % 2 == 0 ? Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState() : leaf);
			}
		}
		List<String> colours = naturalColours(d);
		int k = 0;
		for (int x : new int[]{x0, x1}) {
			String col = colours.isEmpty() ? "red" : colours.get(k++ % colours.size());
			Venue.banner(d, x, top - 1, z - 1 >= 0 || top - 1 >= 3 ? z - 1 : z, Dirs.FRONT, col, List.of(), StageKind.EXTERIOR);
		}
		Lighting.chandelier(d, (x0 + x1) / 2, z, top, top - 1);
	}

	/**
	 * A banner post: a post of the frame's timber three high on a stone, a standing banner of the culture's colours on
	 * its top, facing {@code facing}.
	 */
	public static void bannerPost(Draft d, int x, int y, int z, Direction facing, int k) {
		Mat post = d.mat(Role.FRAME);
		// the town's colours where it has arms (the planner's banner param), else the style's cloth
		List<String> colours = Signs.armsColours(d);
		if (colours.isEmpty()) {
			colours = naturalColours(d);
		}
		String col = colours.isEmpty() ? "red" : colours.get(Math.floorMod(k, colours.size()));
		Block banner = States.block("minecraft:" + col + "_banner", States.block("minecraft:white_banner", Blocks.AIR));
		int rot = switch (facing) {
			case SOUTH -> 0;
			case WEST -> 4;
			case NORTH -> 8;
			default -> 12;
		};
		if (y + 4 >= d.maxHeight()) {
			return;
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y - 1, z, d.mat(Role.FOUNDATION).full());
			for (int yy = y; yy < y + 3; yy++) {
				d.set(x, yy, z, post.log(Direction.Axis.Y).orElse(post.full()));
			}
			if (banner != Blocks.AIR) {
				d.set(x, y + 3, z, banner.defaultBlockState().trySetValue(BlockStateProperties.ROTATION_16, rot),
					dev.voidmc.hearth.build.draft.BlockEntities.banner(List.<String[]>of(new String[]{"minecraft:stripe_bottom",
						colours.size() > 1 ? colours.get(Math.floorMod(k + 1, colours.size())) : "white"})));
			}
		}
	}

	/** Hay bales and barrels in a little heap at (x, y, z): seats and the look of a feast day. */
	public static void heap(Draft d, int x, int y, int z) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int k = 0; k < 3; k++) {
				int px = x + (k == 1 ? 1 : 0);
				int pz = z + (k == 2 ? 1 : 0);
				double u = Noise.unit(d.seed() ^ 0x4EA9, px, y, pz);
				if (!d.isEmpty(px, y, pz)) {
					continue;
				}
				d.set(px, y, pz, u < 0.6 ? Blocks.HAY_BLOCK.defaultBlockState() : States.facing(Blocks.BARREL, Direction.UP));
			}
		}
	}

	/**
	 * A dye colour as cloth was dyed before the bright synthetics: madder red, woad blue, weld yellow, their green, the
	 * undyed and the dark colours stand; light blue and cyan go to woad, lime to green, magenta, pink, purple and orange to
	 * madder.
	 */
	public static String natural(String colour) {
		return switch (colour) {
			case "light_blue", "cyan" -> "blue";
			case "lime" -> "green";
			case "magenta", "pink", "purple", "orange" -> "red";
			default -> colour;
		};
	}

	/** The style's cloth colours made natural, in order, without repeats (white if none). */
	public static List<String> naturalColours(Draft d) {
		List<String> out = new java.util.ArrayList<>();
		for (String c : Signs.clothColours(d)) {
			String n = natural(c);
			if (!out.contains(n)) {
				out.add(n);
			}
		}
		return out;
	}

	/** The one dyed colour a fair or a stall wears: the first of the style's cloth that is dyed, made natural; else madder. */
	public static String houseColour(Draft d) {
		for (String c : naturalColours(d)) {
			if (!c.equals("white") && !c.equals("light_gray") && !c.equals("gray")) {
				return c;
			}
		}
		return "red";
	}

	/** Whether the culture's markets stripe their awnings: the mediterranean and the desert do; everywhere else is canvas. */
	public static boolean striped(Draft d) {
		var c = d.style().culture();
		return c == dev.voidmc.hearth.api.Culture.MEDITERRANEAN || c == dev.voidmc.hearth.api.Culture.DESERT;
	}

	/**
	 * Restates a cloth roof's wool over x0..x1 × z0..z1 in the colour {@code colour} names for each column (keeping every
	 * block's shape: slab, stair or full block), so a canopy rastered in the style's cloth becomes canvas, a valance or a
	 * stripe. Columns the function answers null for are left as they are.
	 */
	public static void recloth(Draft d, int x0, int z0, int x1, int z1, Roofs.Roof roof, java.util.function.BiFunction<Integer, Integer, String> colour) {
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				if (!roof.covers(x, z)) {
					continue;
				}
				String c = colour.apply(x, z);
				if (c == null) {
					continue;
				}
				net.minecraft.world.item.DyeColor dye = net.minecraft.world.item.DyeColor.byName(c, net.minecraft.world.item.DyeColor.WHITE);
				for (int y = roof.bottomY(x, z); y <= roof.surfaceY(x, z); y++) {
					BlockState st = d.get(x, y, z);
					if (st == null || st.isAir() || !net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath().contains("wool")) {
						continue;
					}
					d.restate(x, y, z, wool(st, dye));
				}
			}
		}
	}

	/** Wool of a colour in the shape of {@code like} (a slab, a stair or a full block). */
	public static BlockState wool(BlockState like, net.minecraft.world.item.DyeColor dye) {
		if (like.hasProperty(BlockStateProperties.SLAB_TYPE)) {
			return Blocks.WOOL_SLAB.pick(dye).withPropertiesOf(like);
		}
		if (like.hasProperty(BlockStateProperties.STAIRS_SHAPE)) {
			return Blocks.WOOL_STAIRS.pick(dye).withPropertiesOf(like);
		}
		return Blocks.WOOL.pick(dye).defaultBlockState();
	}

	/** Whether the culture's festival is held at night (the light matters more). */
	public static boolean night(Draft d) {
		var c = d.style().culture();
		return c == dev.voidmc.hearth.api.Culture.NORTHERN || c == dev.voidmc.hearth.api.Culture.TUNDRA || c == dev.voidmc.hearth.api.Culture.MOORLAND
			|| c == dev.voidmc.hearth.api.Culture.DESERT || c == dev.voidmc.hearth.api.Culture.KARST;
	}

	/** The entertainer's job when a stage stands somewhere else than the archetype draws it. */
	static JobType entertainer() {
		return JobType.ENTERTAINER;
	}
}
