package dev.voidmc.hearth.build.kit.civic;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.light.Grid;
import dev.voidmc.hearth.build.kit.light.Spaces;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The hollows under the roofs nobody can get into: the space inside a spire or a helm over its belfry, the loft over a
 * vault, a stage of a tower between its landings that no ladder serves. Such a space is walked by nobody, holds nothing
 * and is dark; a builder leaves no floor in it to stand on. Every unreached, empty, roofed space at or above the eaves
 * (four cells or more, read as {@link Falls} walks) is packed with the roof's own boarding, or the walling, from its floor
 * to its head, so no floor is left there.
 */
public final class Hollows {
	private Hollows() {
	}

	/** Packs every unreachable empty hollow under the roofs (see the class comment). Call before {@link Falls#guard}. */
	public static void pack(Draft d) {
		Paint core = core(d);
		if (core == null) {
			return;
		}
		int eave = d.eaveY();
		for (int round = 0; round < 4; round++) {
			Falls.Walk wk = Falls.walk(d);
			Grid g = wk.g();
			Spaces sp = wk.sp();
			LongOpenHashSet cells = new LongOpenHashSet();
			for (int y = Math.max(eave - 1, g.y0 + 1); y < g.y0 + g.sy - 1 && y < d.maxHeight(); y++) {
				for (int z = 0; z < d.depth(); z++) {
					for (int x = 0; x < d.frontage(); x++) {
						if (sp.walkable(x, y, z) && !sp.reached(x, y, z) && sp.roofed(x, y, z) && !sp.wet(x, y, z)) {
							cells.add(BlockPos.asLong(x, y, z));
						}
					}
				}
			}
			boolean any = false;
			LongOpenHashSet seen = new LongOpenHashSet();
			for (long k0 : cells.toLongArray()) {
				if (seen.contains(k0)) {
					continue;
				}
				LongArrayList comp = new LongArrayList();
				java.util.ArrayDeque<Long> q = new java.util.ArrayDeque<>();
				q.add(k0);
				seen.add(k0);
				while (!q.isEmpty()) {
					long k = q.poll();
					comp.add(k);
					for (Direction dir : Dirs.HORIZONTAL) {
						long n = BlockPos.asLong(BlockPos.getX(k) + Dirs.dx(dir), BlockPos.getY(k), BlockPos.getZ(k) + Dirs.dz(dir));
						if (cells.contains(n) && seen.add(n)) {
							q.add(n);
						}
					}
				}
				if (comp.size() < 4 || furnished(d, g, comp)) {
					continue;
				}
				// packed from its floor to the underside of whatever closes it over (at most a few rows)
				try (var s = d.stage(StageKind.ROOF)) {
					for (long k : comp) {
						int x = BlockPos.getX(k);
						int y = BlockPos.getY(k);
						int z = BlockPos.getZ(k);
						for (int yy = y; yy < y + 8 && d.isEmpty(x, yy, z) && Spaces.passable(g.at(x, yy, z)) && d.allowed(x, yy, z); yy++) {
							if (!sp.roofed(x, yy, z)) {
								break;
							}
							d.set(x, yy, z, core);
							any = true;
						}
					}
				}
			}
			if (!any) {
				return;
			}
		}
	}

	/** Anything used or dressed in the hollow or beside it (a bell, a lamp, a chest): then it is somebody's, left alone. */
	private static boolean furnished(Draft d, Grid g, LongArrayList comp) {
		for (long k : comp) {
			int x = BlockPos.getX(k);
			int y = BlockPos.getY(k);
			int z = BlockPos.getZ(k);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					for (int dy = -1; dy <= 2; dy++) {
						var s = g.at(x + dx, y + dy, z + dz);
						if (g.placed(x + dx, y + dy, z + dz) && (Nooks.furniture(s, Math.max(0, dy)) && !(s.getBlock() instanceof net.minecraft.world.level.block.StairBlock)
							&& !(s.getBlock() instanceof net.minecraft.world.level.block.SlabBlock) && !(s.getBlock() instanceof net.minecraft.world.level.block.FenceBlock)
							&& !(s.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock)
							|| s.getBlock() instanceof net.minecraft.world.level.block.LanternBlock || s.getBlock() instanceof net.minecraft.world.level.block.BellBlock
							|| s.getBlock() instanceof net.minecraft.world.level.block.LadderBlock || s.getBlock() instanceof net.minecraft.world.level.block.DoorBlock)) {
							return true;
						}
					}
				}
			}
		}
		for (Draft.LocalPoi p : d.pois()) {
			for (long k : comp) {
				if (Math.abs(p.x() - BlockPos.getX(k)) <= 1 && Math.abs(p.z() - BlockPos.getZ(k)) <= 1 && Math.abs(p.y() - BlockPos.getY(k)) <= 1) {
					return true;
				}
			}
		}
		return false;
	}

	/** The packing: the roof's boarding (its full block), else the walling. */
	private static Paint core(Draft d) {
		for (Role r : new Role[]{Role.CEILING, Role.ROOF, Role.WALL}) {
			if (d.mat(r).has(Form.FULL)) {
				return d.mat(r).full();
			}
		}
		return null;
	}
}
