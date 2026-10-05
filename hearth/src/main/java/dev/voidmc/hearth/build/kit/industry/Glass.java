package dev.voidmc.hearth.build.kit.industry;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.style.Form;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The glassmaker's things: the round furnace with its glory holes glowing on three sides and its flue rising through
 * the roof, the annealing oven along a wall, marvers and blowpipe racks, racks of sheet glass catching the light in the
 * yard, and the cullet heap of broken glass by the wall. The glass is forest glass: clear with a green cast, the odd
 * sheet of blue.
 */
public final class Glass {
	private Glass() {
	}

	/** A furnace drawn: its centre, the cells its gaffers stand in at the glory holes (each {x, z, facing-index}). */
	public record Furnace(int cx, int cz, int r, int top, List<int[]> holes) {
	}

	/**
	 * A round furnace on centre (cx, cz): radius 2 (five across, its corners rounded off) or 1 (three across), three
	 * courses and a domed crown of {@code body}, glory holes (lit furnaces facing out) at chest height toward the
	 * {@code sides} given, the fire's mouth at the floor toward the first of them. Returns the furnace.
	 */
	public static Furnace furnace(Draft d, int cx, int cz, int r, Fam body, Direction... sides) {
		List<int[]> holes = new ArrayList<>();
		try (var s = d.stage(StageKind.WALLS)) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					boolean corner = Math.abs(dx) == r && Math.abs(dz) == r && r >= 2;
					if (corner) {
						continue;
					}
					int x = cx + dx;
					int z = cz + dz;
					for (int y = 0; y <= 1; y++) {
						d.set(x, y, z, body.full());
					}
					boolean edge = Math.abs(dx) == r || Math.abs(dz) == r;
					boolean innerCorner = Math.abs(dx) == r - 1 && Math.abs(dz) == r - 1 && r >= 2;
					// the dome: the rim steps in, the crown closes
					if (edge) {
						Direction in = Dirs.of(Math.abs(dx) == r ? -Integer.signum(dx) : 0, Math.abs(dz) == r ? -Integer.signum(dz) : 0);
						if (in != null) {
							d.set(x, 2, z, body.stairsOrSlab(in, Half.BOTTOM));
						}
					} else if (innerCorner) {
						d.set(x, 2, z, body.full());
						d.set(x, 3, z, body.stairsOrSlab(Dirs.of(-Integer.signum(dx), 0), Half.BOTTOM));
					} else {
						d.set(x, 2, z, body.full());
						d.set(x, 3, z, body.full());
					}
				}
			}
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (Direction side : sides) {
				int hx = cx + Dirs.dx(side) * r;
				int hz = cz + Dirs.dz(side) * r;
				d.set(hx, 1, hz, States.facing(Blocks.FURNACE, side).trySetValue(BlockStateProperties.LIT, true));
				holes.add(new int[]{hx + Dirs.dx(side), hz + Dirs.dz(side), side.get2DDataValue()});
			}
			if (sides.length > 0) {
				int fx = cx + Dirs.dx(sides[0]) * r;
				int fz = cz + Dirs.dz(sides[0]) * r;
				d.set(fx, 0, fz, States.facing(Blocks.BLAST_FURNACE, sides[0]).trySetValue(BlockStateProperties.LIT, true));
			}
		}
		return new Furnace(cx, cz, r, r >= 2 ? 3 : 2, holes);
	}

	/**
	 * A rack of sheet glass along {@code run} from (x, z): posts at both ends, two rows of panes standing between them,
	 * clear and green, a cap rail over them. {@code length} cells long, panes {@code height} high.
	 */
	public static void sheets(Draft d, int x, int y, int z, Direction run, int length, int height, Mat wood) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int i = 0; i < length; i++) {
				int px = x + Dirs.dx(run) * i;
				int pz = z + Dirs.dz(run) * i;
				if (!Works.in(d, px, pz)) {
					continue;
				}
				boolean end = i == 0 || i == length - 1;
				for (int k = 0; k < height; k++) {
					if (end) {
						d.set(px, y + k, pz, wood.log(Direction.Axis.Y));
					} else {
						double u = Noise.unit(d.seed() ^ 0x6A55, px, y + k, pz);
						Block pane = u < 0.55 ? Blocks.GLASS_PANE : u < 0.85 ? Blocks.STAINED_GLASS_PANE.pick(DyeColor.GREEN)
							: Blocks.STAINED_GLASS_PANE.pick(DyeColor.LIGHT_BLUE);
						d.set(px, y + k, pz, pane.defaultBlockState());
					}
				}
				d.set(px, y + height, pz, wood.has(Form.SLAB) ? wood.slab(SlabType.BOTTOM) : wood.full());
			}
		}
	}

	/** The cullet heap: broken glass of every shade heaped against a wall. */
	public static Block[] cullet() {
		return new Block[]{Blocks.GLASS, Blocks.STAINED_GLASS.pick(DyeColor.GREEN), Blocks.GLASS, Blocks.STAINED_GLASS.pick(DyeColor.LIME),
			Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE), Blocks.GLASS};
	}

	/**
	 * A marver: a slab of polished stone on a log, where the gather is rolled. Its worker stands toward {@code user}.
	 */
	public static void marver(Draft d, int x, int y, int z) {
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(x, y, z, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE, SlabType.TOP));
		}
	}

	/** A rack of blowpipes: iron rods standing against a wall, two high. */
	public static void pipes(Draft d, int x, int y, int z) {
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(x, y, z, Blocks.IRON_BARS.defaultBlockState());
			d.set(x, y + 1, z, Blocks.IRON_BARS.defaultBlockState());
		}
	}
}
