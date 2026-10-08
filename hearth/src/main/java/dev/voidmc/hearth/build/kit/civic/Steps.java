package dev.voidmc.hearth.build.kit.civic;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.light.Grid;
import dev.voidmc.hearth.build.kit.light.Spaces;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * A step wherever folk climb a whole block out of doors: the finished drawing is walked (as {@link Falls} walks it) and
 * every place where a walked yard, base or terrace stands a full block over the ground beside it with no stair between
 * gets one, as a builder sets a step there: a stair in the low cell climbing to the high one. A player then walks up
 * instead of jumping. Doorways, flights and ladders already there and the cells beside them are left as they are.
 */
public final class Steps {
	private Steps() {
	}

	/** Sets a step at every full-block rise folk walk out of doors (see the class comment). Call before {@link Falls#guard}. */
	public static void cut(Draft d) {
		Falls.Walk wk = Falls.walk(d);
		Grid g = wk.g();
		Spaces sp = wk.sp();
		Set<Long> done = new HashSet<>();
		for (int y = g.y0 + 2; y < g.y0 + g.sy - 1 && y < d.maxHeight(); y++) {
			for (int z = 0; z < d.depth(); z++) {
				for (int x = 0; x < d.frontage(); x++) {
					if (!sp.reached(x, y, z) || sp.wet(x, y, z) || sp.doorway(x, y, z)) {
						continue;
					}
					// the high side: a floor of a whole block (paving, a base, boards; not furniture, not a wall's head)
					Draft.Cell under = d.cell(x, y - 1, z);
					BlockState u = g.at(x, y - 1, z);
					if (!full(u) || under != null && (under.stage() == StageKind.INTERIOR || under.stage() == StageKind.WALLS || under.stage() == StageKind.ROOF
						|| under.stage() == StageKind.FRAME || under.stage() == StageKind.OPENINGS)) {
						continue;
					}
					for (Direction dir : Dirs.HORIZONTAL) {
						int ax = x - Dirs.dx(dir);
						int az = z - Dirs.dz(dir);
						// the low side: walked a block lower, on a whole block too (a slab or a stair there is a step already)
						if (!sp.reached(ax, y - 1, az) || sp.reached(ax, y, az) || !full(g.at(ax, y - 2, az)) || sp.wet(ax, y - 1, az)) {
							continue;
						}
						// (out of doors only, clear of doors and of any stair, slab or ladder already there: a flight is not cut
						// into)
						boolean roofed = sp.roofed(x, y, z) || sp.roofed(ax, y - 1, az);
						if (roofed || nearEntrance(d, ax, az) || nearDoor(g, x, y, z) || nearDoor(g, ax, y - 1, az) || nearStep(g, ax, y - 1, az) || nearStep(g, x, y, z)
							|| !done.add(BlockPos.asLong(x, y, z))) {
							continue;
						}
						Paint step = stair(d, dir, roofed);
						if (step == null) {
							continue;
						}
						boolean inPlot = ax >= 0 && az >= 0 && ax < d.frontage() && az < d.depth();
						int fy = y;
						int fx = x;
						int fz = z;
						if (inPlot && d.isEmpty(ax, y - 1, az) && d.isEmpty(ax, y, az) && Spaces.passable(g.at(ax, y + 1, az)) && d.allowed(ax, y - 1, az)) {
							// a stair in the low cell, climbing to the high one
							d.inStage(roofed ? StageKind.FLOORS : StageKind.EXTERIOR, () -> d.set(ax, fy - 1, az, step));
						}
						break;
					}
				}
			}
		}
	}

	/** A stair climbing toward {@code dir}: of the floor's boards indoors, of the paving (or the footing) out of doors. */
	private static Paint stair(Draft d, Direction dir, boolean roofed) {
		Mat m = roofed ? d.mat(Role.FLOOR) : d.mat(Role.PAVING);
		if (m.has(Form.STAIRS)) {
			return m.stairs(dir, Half.BOTTOM);
		}
		Mat alt = roofed ? d.mat(Role.FURNITURE) : d.mat(Role.FOUNDATION);
		return alt.has(Form.STAIRS) || alt.has(Form.SLAB) ? alt.stairsOrSlab(dir, Half.BOTTOM) : null;
	}

	private static boolean full(BlockState s) {
		return s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
	}

	/** A registered way in within two cells of (x, z) (its threshold is lit and kept as it is). */
	private static boolean nearEntrance(Draft d, int x, int z) {
		for (int[] e : d.entrances()) {
			if (Math.abs(e[0] - x) <= 2 && Math.abs(e[2] - z) <= 2) {
				return true;
			}
		}
		return false;
	}

	/** A stair, a slab or a ladder within a cell of (x, z), from the feet's level down two. */
	private static boolean nearStep(Grid g, int x, int y, int z) {
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -2; dy <= 1; dy++) {
					var b = g.at(x + dx, y + dy, z + dz).getBlock();
					if (b instanceof net.minecraft.world.level.block.StairBlock || b instanceof net.minecraft.world.level.block.SlabBlock
						|| b instanceof net.minecraft.world.level.block.LadderBlock) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** A door within a cell of (x, z), at the feet's level or the one under it. */
	private static boolean nearDoor(Grid g, int x, int y, int z) {
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 0; dy++) {
					if (g.at(x + dx, y + dy, z + dz).getBlock() instanceof DoorBlock) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
