package dev.voidmc.hearth.build.kit.learning;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The instruments of an observatory, built from blocks: a telescope on its mount, an armillary sphere of green bronze on
 * a stone stand, a celestial globe, a sundial with its gnomon, a quadrant. Bronze is waxed copper (it keeps its colour),
 * the stands the style's dressed stone. Each stands on what is below the cell given and checks the plot itself.
 */
public final class Instruments {
	private Instruments() {
	}

	private static BlockState block(String id, Block fallback) {
		return States.block(id, fallback).defaultBlockState();
	}

	/** The bronze of the instruments: weathered, waxed so it keeps its green. */
	public static BlockState bronze() {
		return block("minecraft:waxed_oxidized_copper", Blocks.IRON_BLOCK);
	}

	/** A lattice of bronze: the rings of an armillary sphere. */
	public static BlockState rings() {
		return block("minecraft:waxed_oxidized_copper_grate", Blocks.IRON_BARS);
	}

	/** A copper rod pointing {@code facing} (a telescope's tube, a polar axis). */
	public static BlockState rod(Direction facing) {
		Block b = States.block("minecraft:waxed_lightning_rod", States.block("minecraft:lightning_rod", Blocks.END_ROD));
		return b.defaultBlockState().trySetValue(BlockStateProperties.FACING, facing);
	}

	/**
	 * A telescope at (x, y, z): a timber mount, the tube of two copper rods stepping up toward {@code facing} (the second a
	 * block ahead and a block higher, so the cell in front stays walkable under it). Needs (x, y + 1, z) and the cell ahead
	 * at y + 2 free.
	 */
	public static void telescope(Draft d, int x, int y, int z, Direction facing) {
		if (!Sacra.ok(d, x, y + 2, z)) {
			return;
		}
		int ax = x + Dirs.dx(facing);
		int az = z + Dirs.dz(facing);
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(x, y, z, Sacra.fence(d));
			d.set(x, y + 1, z, rod(facing));
			if (Sacra.ok(d, ax, y + 2, az) && d.isEmpty(ax, y + 2, az)) {
				d.set(ax, y + 2, az, rod(facing));
			}
		}
		d.poi(PoiType.VIEWPOINT, x - Dirs.dx(facing), y, z - Dirs.dz(facing), facing);
	}

	/** An armillary sphere at (x, y, z): a stand of the dressed stone, the rings of bronze, the polar axis over them. */
	public static void armillary(Draft d, int x, int y, int z) {
		if (!Sacra.ok(d, x, y + 2, z)) {
			return;
		}
		Stone st = Stone.dressing(d);
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(x, y, z, st.wallOrFull());
			d.set(x, y + 1, z, rings());
			d.set(x, y + 2, z, rod(Direction.UP));
		}
	}

	/** A celestial globe at (x, y, z): a timber stand and the globe of bronze on it. */
	public static void globe(Draft d, int x, int y, int z) {
		if (!Sacra.ok(d, x, y + 1, z)) {
			return;
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(x, y, z, Sacra.fence(d));
			d.set(x, y + 1, z, block("minecraft:waxed_weathered_copper", Blocks.IRON_BLOCK));
		}
	}

	/**
	 * A sundial over the 3 × 3 round (x, y, z), y the standing level: a dial of slabs of the dressed stone (or smooth
	 * stone) on a step, the gnomon in the middle a stair rising toward the back (north in the frame's sense).
	 */
	public static void sundial(Draft d, int x, int y, int z) {
		Stone st = Stone.dressing(d);
		Paint dial = st.has(dev.voidmc.hearth.build.style.Form.SLAB) ? st.slab(SlabType.BOTTOM) : Paint.of(Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dx = -1; dx <= 1; dx++) {
					int px = x + dx;
					int pz = z + dz;
					if (!Sacra.ok(d, px, y, pz) || !d.isEmpty(px, y, pz)) {
						continue;
					}
					d.set(px, y, pz, dx == 0 && dz == 0 ? st.stairsOrSlab(Dirs.BACK, Half.BOTTOM) : dial);
				}
			}
		}
	}

	/** A mural quadrant at (x, y, z): a quarter arc of the dressed stone rising and turning toward {@code toward}. */
	public static void quadrant(Draft d, int x, int y, int z, Direction toward) {
		Stone st = Stone.dressing(d);
		int ax = x + Dirs.dx(toward);
		int az = z + Dirs.dz(toward);
		if (!Sacra.ok(d, x, y + 2, z) || !Sacra.ok(d, ax, y + 2, az)) {
			return;
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, st.full());
			d.set(x, y + 1, z, st.full());
			d.set(x, y + 2, z, st.stairsOrSlab(toward.getOpposite(), Half.TOP));
			if (d.isEmpty(ax, y + 2, az)) {
				d.set(ax, y + 2, az, st.has(dev.voidmc.hearth.build.style.Form.SLAB) ? st.slab(SlabType.TOP) : st.full());
			}
			if (d.isEmpty(x, y + 3, z) && Sacra.ok(d, x, y + 3, z)) {
				d.set(x, y + 3, z, rod(Direction.UP));
			}
		}
	}
}
