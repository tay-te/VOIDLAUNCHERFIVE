package dev.voidmc.hearth.build.kit.landmark;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.BlockEntities;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Walls;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The small carved and fitted parts that make monumental walls read as built by hand: string courses and drip mouldings,
 * Lombard bands of little arches, round-headed openings with dressed voussoirs and jambs, paired and triple lights with
 * colonnettes, pinnacles, bells hung from beams, clock dials laid in blocks, stone lanterns and banners. Everything is
 * drawn on a {@link Side} (cell 0 at the left, seen from outside) so the four faces of a tower take the same calls.
 */
public final class Details {
	private Details() {
	}

	private static boolean ok(Draft d, int x, int y, int z) {
		return Walls.inPlot(d, x, z) || z == -1 && y >= 3 && x >= 0 && x < d.frontage();
	}

	// ------------------------------------------------------------------------------------------------ mouldings

	/**
	 * A string course: upside-down stairs one block out from cells {@code [i0..i1]} of a wall at height y, their backs
	 * against it (a drip moulding). Returns the number placed.
	 */
	public static int course(Draft d, Side s, int i0, int i1, int y, Stone st) {
		int n = 0;
		for (int i = i0; i <= i1; i++) {
			int x = s.outX(i, 1);
			int z = s.outZ(i, 1);
			if (ok(d, x, y, z) && d.isEmpty(x, y, z)) {
				d.set(x, y, z, st.stairsOrSlab(s.out().getOpposite(), Half.TOP));
				n++;
			}
		}
		return n;
	}

	/**
	 * A Lombard band: a row of little arches one block out from cells {@code [i0..i1]} at height y, under a course at
	 * y + 1: upside-down stairs turned alternately along the wall, so each pair opens a small arch and closes a corbel.
	 */
	public static void corbelTable(Draft d, Side s, int i0, int i1, int y, Stone st) {
		if (!st.has(Form.STAIRS)) {
			return;
		}
		for (int i = i0; i <= i1; i++) {
			int x = s.outX(i, 1);
			int z = s.outZ(i, 1);
			if (!ok(d, x, y, z) || !d.isEmpty(x, y, z)) {
				continue;
			}
			Direction f = ((i - i0) & 1) == 0 ? s.along().getOpposite() : s.along();
			d.set(x, y, z, st.stairs(f, Half.TOP));
		}
	}

	/** Corbels: an upside-down stair one block out under cell i at height y (a beam end, a machicolation's bracket). */
	public static void corbel(Draft d, Side s, int i, int y, Stone st) {
		int x = s.outX(i, 1);
		int z = s.outZ(i, 1);
		if (ok(d, x, y, z) && d.isEmpty(x, y, z)) {
			d.set(x, y, z, st.stairsOrSlab(s.out().getOpposite(), Half.TOP));
		}
	}

	// ------------------------------------------------------------------------------------------------ openings

	/**
	 * A round-headed opening in a wall: cells {@code [i0, i0 + width)} from y up {@code height} rows, filled with
	 * {@code fill} (glass, air, a lattice) and shouldered at the head by upside-down stairs when two or more wide; dressed
	 * jambs and a ring of voussoirs over the head in {@code dress} where the wall stands.
	 *
	 * @param fill null for an open arch (air)
	 */
	public static void arch(Draft d, Side s, int i0, int width, int y, int height, Stone dress, Paint fill) {
		Paint in = fill != null ? fill : Paint.of(Blocks.AIR.defaultBlockState());
		for (int c = 0; c < width; c++) {
			int i = i0 + c;
			for (int yy = y; yy < y + height; yy++) {
				d.set(s.x(i), yy, s.z(i), in);
			}
		}
		int top = y + height - 1;
		if (width >= 2 && height >= 2 && dress.has(Form.STAIRS)) {
			d.set(s.x(i0), top, s.z(i0), dress.stairs(s.along().getOpposite(), Half.TOP));
			d.set(s.x(i0 + width - 1), top, s.z(i0 + width - 1), dress.stairs(s.along(), Half.TOP));
		}
		// voussoirs over the head and dressed jambs either side, where the wall is
		for (int c = -1; c <= width; c++) {
			int i = i0 + c;
			if (i < 0 || i > s.last()) {
				continue;
			}
			int x = s.x(i);
			int z = s.z(i);
			if (solidAt(d, x, top + 1, z)) {
				d.set(x, top + 1, z, dress.full());
			}
			if (c == -1 || c == width) {
				for (int yy = y; yy <= top; yy += 2) {
					if (solidAt(d, x, yy, z)) {
						d.set(x, yy, z, dress.full());
					}
				}
			}
		}
	}

	/**
	 * Paired or triple lights under one arch (bifora, trifora): {@code lights} openings one wide and {@code height}
	 * high, parted by colonnettes (wall posts), the whole run shouldered at its ends. Needs {@code 2 * lights - 1} cells
	 * from i0.
	 */
	public static void lights(Draft d, Side s, int i0, int lights, int y, int height, Stone dress, Paint fill) {
		int width = 2 * lights - 1;
		arch(d, s, i0, width, y, height, dress, fill);
		for (int k = 1; k < lights; k++) {
			int i = i0 + 2 * k - 1;
			for (int yy = y; yy < y + height - 1; yy++) {
				d.set(s.x(i), yy, s.z(i), dress.wallOrFull());
			}
			// a little capital under the head
			d.set(s.x(i), y + height - 1, s.z(i), dress.full());
		}
	}

	/** A narrow slit (a loophole or a stair light): air or glass, a dressed head and sill. */
	public static void slit(Draft d, Side s, int i, int y, int height, Stone dress, Paint fill) {
		for (int yy = y; yy < y + height; yy++) {
			d.set(s.x(i), yy, s.z(i), fill != null ? fill : Paint.of(Blocks.AIR.defaultBlockState()));
		}
		if (solidAt(d, s.x(i), y + height, s.z(i))) {
			d.set(s.x(i), y + height, s.z(i), dress.full());
		}
		if (solidAt(d, s.x(i), y - 1, s.z(i))) {
			d.set(s.x(i), y - 1, s.z(i), dress.full());
		}
	}

	/** A course of upside-down stairs round a disc of radius r at height y (a drum's cornice). */
	public static void ringCourse(Draft d, double cx, double cz, double r, int y, Stone st) {
		java.util.Set<Long> disc = new java.util.HashSet<>();
		for (int[] c : Shapes.disc(cx, cz, r)) {
			disc.add(Shapes.cell(c[0], c[1]));
		}
		for (int[] c : Shapes.around(disc)) {
			if (!d.allowed(c[0], y, c[1]) || !d.isEmpty(c[0], y, c[1])) {
				continue;
			}
			Direction in = Tower.towardShape(disc, c[0], c[1]);
			if (in != null) {
				d.set(c[0], y, c[1], st.stairsOrSlab(in, Half.TOP));
			}
		}
	}

	/** Whether a solid wall block is drawn at a cell. */
	public static boolean solidAt(Draft d, int x, int y, int z) {
		return d.isSolid(x, y, z);
	}

	// ------------------------------------------------------------------------------------------------ crowning

	/**
	 * A pinnacle standing on (x, y, z): a dressed base block, a shaft of wall posts {@code height} tall, and a finial
	 * (a slab or a rod). Returns the y above it.
	 */
	public static int pinnacle(Draft d, int x, int y, int z, int height, Stone st, boolean rod) {
		if (!ok(d, x, y, z)) {
			return y;
		}
		d.set(x, y, z, st.full());
		int yy = y + 1;
		for (int k = 0; k < height && yy < d.maxHeight(); k++, yy++) {
			d.set(x, yy, z, st.wallOrFull());
		}
		if (yy < d.maxHeight()) {
			if (rod) {
				d.set(x, yy, z, rod());
			} else if (st.has(Form.SLAB)) {
				d.set(x, yy, z, st.slab(SlabType.BOTTOM));
			}
			yy++;
		}
		return yy;
	}

	/** A copper rod pointing up (a finial, a lightning conductor). */
	public static BlockState rod() {
		Block b = States.block("minecraft:waxed_lightning_rod", States.block("minecraft:lightning_rod", Blocks.END_ROD));
		return b.defaultBlockState().trySetValue(BlockStateProperties.FACING, Direction.UP);
	}

	/** A finial at (x, y, z): a rod, or a gilded ball on a rod, as tall as allowed. Returns the top y. */
	public static int finial(Draft d, int x, int y, int z, boolean gilded) {
		int yy = y;
		if (yy >= d.maxHeight()) {
			return yy;
		}
		if (gilded && yy + 1 < d.maxHeight()) {
			d.set(x, yy, z, Blocks.GOLD_BLOCK.defaultBlockState());
			yy++;
		}
		d.set(x, yy, z, rod());
		return yy;
	}

	// ------------------------------------------------------------------------------------------------ bells and clocks

	/** A bell hung from the ceiling block at {@code y + 1}, swinging across {@code facing}'s axis. */
	public static void bell(Draft d, int x, int y, int z, Direction facing) {
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(x, y, z, Blocks.BELL.defaultBlockState().setValue(BlockStateProperties.BELL_ATTACHMENT, BellAttachType.CEILING)
				.setValue(BlockStateProperties.HORIZONTAL_FACING, facing));
		}
	}

	/**
	 * A clock dial laid in a wall on side {@code s} centred on cell {@code ic} at height {@code yc}: a white face inside
	 * a rim, set in a square panel of dressed stone, with hour marks and two hands showing a time of the seed's
	 * choosing. Radius 2 (5 × 5) or 3 (7 × 7).
	 */
	public static void clock(Draft d, Side s, int ic, int yc, int radius, Stone panel, long salt) {
		BlockState dial = Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState();
		BlockState hand = Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.BLACK).defaultBlockState();
		BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
		Block rimBlock = d.mat(Role.ROOF_TRIM).main(Form.FULL);
		BlockState rim = rimBlock != null ? rimBlock.defaultBlockState() : Blocks.POLISHED_DEEPSLATE.defaultBlockState();
		double rr = radius + 0.45;
		try (var st = d.stage(StageKind.WALLS)) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int di = -radius; di <= radius; di++) {
					int i = ic + di;
					if (i < 0 || i > s.last()) {
						continue;
					}
					int x = s.x(i);
					int z = s.z(i);
					int y = yc + dy;
					double r = Math.hypot(di, dy);
					if (r > rr) {
						d.set(x, y, z, panel.full());
					} else if (radius >= 3 && r > radius - 0.55) {
						d.set(x, y, z, rim);
					} else {
						d.set(x, y, z, dial);
					}
				}
			}
			// hour marks at 12, 3, 6, 9 on the rim's inside (big dials only: a small one shows just its hands)
			if (radius >= 3) {
				int m = radius - 1;
				for (int[] o : new int[][]{{0, m}, {m, 0}, {0, -m}, {-m, 0}}) {
					d.set(s.x(ic + o[0]), yc + o[1], s.z(ic + o[0]), gold);
				}
			}
			// the hands: a time chosen per dial (the minute hand on a quarter, the hour hand where it falls)
			int hour = (int) (Noise.unit(d.seed() ^ salt, ic, yc, s.out().ordinal()) * 12);
			int quarter = (int) (Noise.unit(d.seed() ^ salt ^ 0x51, ic, yc, s.out().ordinal()) * 4);
			double minuteAngle = quarter * Math.PI / 2;
			double hourAngle = (hour + quarter / 4.0) * Math.PI / 6;
			hand(d, s, ic, yc, minuteAngle, radius >= 4 ? radius - 2 : radius >= 3 ? 2 : 1, hand);
			hand(d, s, ic, yc, hourAngle, radius >= 4 ? 2 : 1, hand);
			d.set(s.x(ic), yc, s.z(ic), radius >= 3 ? gold : hand);
		}
	}

	private static void hand(Draft d, Side s, int ic, int yc, double angle, int length, BlockState block) {
		for (int k = 1; k <= length; k++) {
			int di = (int) Math.round(Math.sin(angle) * k);
			int dy = (int) Math.round(Math.cos(angle) * k);
			d.set(s.x(ic + di), yc + dy, s.z(ic + di), block);
		}
	}

	// ------------------------------------------------------------------------------------------------ furniture of a sacred place

	/** A stone lantern standing at (x, y, z): a plinth, a post, the light, a cap. */
	public static void stoneLantern(Draft d, int x, int y, int z, Stone st) {
		Block lantern = d.mat(Role.LIGHT).mainOr(Form.FULL, Blocks.LANTERN);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, st.full());
			d.set(x, y + 1, z, st.wallOrFull());
			d.set(x, y + 2, z, States.lantern(lantern, false));
			d.set(x, y + 3, z, st.has(Form.SLAB) ? st.slab(SlabType.BOTTOM) : st.full());
		}
	}

	/** A banner hung flat on the wall behind (x, y, z), facing {@code out}, in the style's cloth with a pattern by seed. */
	public static void wallBanner(Draft d, int x, int y, int z, Direction out, int salt) {
		Mat cloth = d.mat(Role.CLOTH);
		var entries = cloth.palette().entries();
		Block banner = entries.get(Math.floorMod(salt, entries.size())).material().block(Form.WALL_BANNER);
		if (banner == null) {
			banner = cloth.main(Form.WALL_BANNER);
		}
		if (banner == null) {
			return;
		}
		String second = colourOf(entries.get(Math.floorMod(salt + 1, entries.size())).material().block(Form.FULL));
		String[] patterns = {"stripe_center", "cross", "border", "triangle_bottom", "rhombus", "half_horizontal", "stripe_middle", "circle"};
		String pat = patterns[(int) (Noise.unit(d.seed(), salt, 3, 7) * patterns.length)];
		List<String[]> layers = List.of(new String[]{pat, second}, new String[]{"border", second});
		Block b = banner;
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(x, y, z, States.facing(b, out), BlockEntities.banner(layers));
		}
	}

	/** A standing banner on the block below. */
	public static void banner(Draft d, int x, int y, int z, int salt) {
		Mat cloth = d.mat(Role.CLOTH);
		var entries = cloth.palette().entries();
		Block banner = entries.get(Math.floorMod(salt, entries.size())).material().block(Form.BANNER);
		if (banner == null) {
			return;
		}
		String second = colourOf(entries.get(Math.floorMod(salt + 1, entries.size())).material().block(Form.FULL));
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, banner.defaultBlockState(), BlockEntities.banner(List.of(new String[]{"stripe_center", second}, new String[]{"border", second})));
		}
	}

	/** The dye colour name of a wool block ("white" for anything else). */
	static String colourOf(Block wool) {
		if (wool == null) {
			return "white";
		}
		String p = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(wool).getPath();
		return p.endsWith("_wool") ? p.substring(0, p.length() - "_wool".length()) : "white";
	}

	/** Candles on a block (1–4, lit), for altars and shrines. */
	public static BlockState candles(int n, boolean lit) {
		return Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, Math.clamp(n, 1, 4)).setValue(BlockStateProperties.LIT, lit);
	}
}
