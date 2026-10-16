package dev.voidmc.hearth.build.kit.civic;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.light.Grid;
import dev.voidmc.hearth.build.kit.light.Spaces;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The roofed corners nobody furnished: every enclosed, roofed space of the finished drawing that folk walk into (a passage
 * under the eaves against the bank, a corner of the cloister walk, the room between the stilts) with nothing in it gets
 * what such a place holds: a rain butt at the dead end of a narrow passage, a bench against the wall of a wider one, a
 * storage jar among the posts under a floor. Read
 * the way the structure audit reads rooms (walkable, roofed, walled round, at least six cells).
 */
public final class Nooks {
	private Nooks() {
	}

	/** Furnishes every empty roofed space (see the class comment). Call before {@link Falls#guard}. */
	public static void furnish(Draft d) {
		Falls.Walk wk = Falls.walk(d);
		Grid g = wk.g();
		Spaces sp = wk.sp();
		Set<Long> pois = new HashSet<>();
		for (Draft.LocalPoi p : d.pois()) {
			for (int dy = -1; dy <= 1; dy++) {
				pois.add(BlockPos.asLong(p.x(), p.y() + dy, p.z()));
			}
		}
		LongOpenHashSet room = new LongOpenHashSet();
		for (int y = g.y0 + 1; y < g.y0 + g.sy - 1 && y < d.maxHeight(); y++) {
			for (int z = 0; z < d.depth(); z++) {
				for (int x = 0; x < d.frontage(); x++) {
					if (!sp.reached(x, y, z) || sp.wet(x, y, z) || sp.doorway(x, y, z) || !sp.roofed(x, y, z) || !sp.enclosed(x, y, z)) {
						continue;
					}
					BlockState under = g.at(x, y - 1, z);
					if (g.placed(x, y - 1, z) && !(under.getBlock() instanceof StairBlock) && furniture(under, 0)) {
						continue;
					}
					room.add(BlockPos.asLong(x, y, z));
				}
			}
		}
		LongOpenHashSet seen = new LongOpenHashSet();
		for (long k0 : room.toLongArray()) {
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
					if (room.contains(n) && seen.add(n)) {
						q.add(n);
					}
				}
			}
			if (comp.size() < 6) {
				continue;
			}
			if (!used(g, comp, pois)) {
				place(d, g, comp);
			} else {
				// a walk, a passage or a long hall furnished at one end only: banners on its walls wherever nothing is near
				// (a lamp post set in it later must not leave a stretch of it a bare room of its own)
				benches(d, g, comp, pois);
			}
		}
	}

	/** A walk or a passage: no cell with more than two neighbours in it, at least ten cells long. */
	private static boolean narrow(LongArrayList comp) {
		LongOpenHashSet set = new LongOpenHashSet(comp);
		for (long k : comp) {
			int n = 0;
			for (Direction dir : Dirs.HORIZONTAL) {
				if (set.contains(BlockPos.asLong(BlockPos.getX(k) + Dirs.dx(dir), BlockPos.getY(k), BlockPos.getZ(k) + Dirs.dz(dir)))) {
					n++;
				}
			}
			if (n > 2) {
				return false;
			}
		}
		return comp.size() >= 10;
	}

	/**
	 * Banners of the house's colours on the walls of a walk wherever nothing used hangs or stands within four cells (a
	 * banner takes no floor: the walk stays one walk).
	 */
	private static void benches(Draft d, Grid g, LongArrayList comp, Set<Long> pois) {
		java.util.List<long[]> placed = new java.util.ArrayList<>();
		for (long k : comp) {
			int x = BlockPos.getX(k);
			int y = BlockPos.getY(k);
			int z = BlockPos.getZ(k);
			boolean near = false;
			for (int dx = -4; dx <= 4 && !near; dx++) {
				for (int dz = -4; dz <= 4 && !near; dz++) {
					for (int dy = 0; dy <= 1 && !near; dy++) {
						near = g.placed(x + dx, y + dy, z + dz) && furniture(g.at(x + dx, y + dy, z + dz), dy);
					}
				}
			}
			for (long[] p : placed) {
				near |= Math.abs(p[0] - x) <= 4 && Math.abs(p[2] - z) <= 4 && p[1] == y;
			}
			if (!near && banner(d, g, x, y, z)) {
				placed.add(new long[]{x, y, z});
			}
		}
	}

	/** A banner of the house's colours on a wall beside (x, y, z), at head height; whether one went up. */
	private static boolean banner(Draft d, Grid g, int x, int y, int z) {
		if (!d.isEmpty(x, y + 1, z) || !Spaces.passable(g.at(x, y + 1, z))) {
			return false;
		}
		for (Direction dir : Dirs.HORIZONTAL) {
			int nx = x + Dirs.dx(dir);
			int nz = z + Dirs.dz(dir);
			BlockState w = g.at(nx, y + 1, nz);
			if (g.placed(nx, y + 1, nz) && w.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
				&& !(g.at(nx, y, nz).getBlock() instanceof DoorBlock)) {
				java.util.List<String> colours = Signs.clothColours(d);
				String field = colours.get(Math.floorMod(x * 7 + z * 3, colours.size()));
				String charge = colours.size() > 1 ? colours.get((colours.indexOf(field) + 1) % colours.size()) : "white";
				Signs.wallBanner(d, x, y + 1, z, dir.getOpposite(), field, java.util.List.<String[]>of(new String[]{"stripe_center", charge}));
				return true;
			}
		}
		return false;
	}

	/** Something in the room or beside it that dresses it or is used (the audit's furniture: a place to stand is not enough). */
	private static boolean used(Grid g, LongArrayList comp, Set<Long> pois) {
		for (long k : comp) {
			int x = BlockPos.getX(k);
			int y = BlockPos.getY(k);
			int z = BlockPos.getZ(k);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					for (int dy = 0; dy <= 1; dy++) {
						if (g.placed(x + dx, y + dy, z + dz) && furniture(g.at(x + dx, y + dy, z + dz), dy)) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	/** A rain butt at a narrow passage's dead end, else a bench against a wall, else a storage jar at the end of the space. */
	private static void place(Draft d, Grid g, LongArrayList comp) {
		LongOpenHashSet set = new LongOpenHashSet(comp);
		long butt = Long.MIN_VALUE;
		long bench = Long.MIN_VALUE;
		long end = Long.MIN_VALUE;
		long corner = Long.MIN_VALUE;
		Direction benchOut = null;
		boolean narrow = true;
		for (long k : comp) {
			int x = BlockPos.getX(k);
			int y = BlockPos.getY(k);
			int z = BlockPos.getZ(k);
			int inRoom = 0;
			int walls = 0;
			Direction wallDir = null;
			boolean door = false;
			for (Direction dir : Dirs.HORIZONTAL) {
				int nx = x + Dirs.dx(dir);
				int nz = z + Dirs.dz(dir);
				if (set.contains(BlockPos.asLong(nx, y, nz))) {
					inRoom++;
				}
				BlockState s = g.at(nx, y, nz);
				if (s.getBlock() instanceof DoorBlock || g.at(nx, y - 1, nz).getBlock() instanceof DoorBlock) {
					door = true;
				}
				if (!Spaces.passable(s) && !Spaces.passable(g.at(nx, y + 1, nz))) {
					walls++;
					wallDir = dir;
				}
			}
			if (inRoom >= 3) {
				narrow = false;
			}
			if (door || !d.isEmpty(x, y, z) || !d.isEmpty(x, y + 1, z)) {
				continue;
			}
			if (inRoom <= 1 && end == Long.MIN_VALUE) {
				end = k;
			}
			// (a corner of an open space: two ways on at most)
			if (inRoom == 2 && corner == Long.MIN_VALUE) {
				corner = k;
			}
			// (a dead end: one way on, walls round the rest)
			if (inRoom == 1 && walls >= 2 && butt == Long.MIN_VALUE) {
				butt = k;
			}
			if (walls >= 1 && inRoom >= 2 && bench == Long.MIN_VALUE) {
				bench = k;
				benchOut = wallDir.getOpposite();
			}
		}
		// (a small room keeps every cell: a banner on its wall, no floor taken)
		if (comp.size() < 7) {
			for (long k : comp) {
				if (banner(d, g, BlockPos.getX(k), BlockPos.getY(k), BlockPos.getZ(k))) {
					return;
				}
			}
			return;
		}
		if (narrow && butt != Long.MIN_VALUE) {
			int x = BlockPos.getX(butt);
			int y = BlockPos.getY(butt);
			int z = BlockPos.getZ(butt);
			d.inStage(StageKind.INTERIOR, () -> d.set(x, y, z, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3)));
			return;
		}
		// (in the open among posts or arches: a storage jar stood at the end of the dry ground, or in a corner)
		if (bench == Long.MIN_VALUE && (end != Long.MIN_VALUE || corner != Long.MIN_VALUE)) {
			long at = end != Long.MIN_VALUE ? end : corner;
			int x = BlockPos.getX(at);
			int y = BlockPos.getY(at);
			int z = BlockPos.getZ(at);
			d.inStage(StageKind.INTERIOR, () -> d.set(x, y, z, Blocks.DECORATED_POT.defaultBlockState()));
			return;
		}
		if (bench != Long.MIN_VALUE) {
			int x = BlockPos.getX(bench);
			int y = BlockPos.getY(bench);
			int z = BlockPos.getZ(bench);
			Mat wood = d.mat(Role.FURNITURE);
			Direction out = benchOut;
			if (wood.has(Form.STAIRS)) {
				// (a stair's back is toward its facing: the seat faces out of the wall)
				d.inStage(StageKind.INTERIOR, () -> d.set(x, y, z, wood.stairs(out.getOpposite(), Half.BOTTOM)));
			} else {
				d.inStage(StageKind.INTERIOR, () -> d.set(x, y, z, wood.slab(SlabType.BOTTOM)));
			}
		}
	}

	/** The structure audit's furniture: what a person uses or sits at, and what dresses a room. */
	static boolean furniture(BlockState s, int dy) {
		Block b = s.getBlock();
		if (s.isAir() || b instanceof DoorBlock || b instanceof net.minecraft.world.level.block.IronBarsBlock || b instanceof LadderBlock
			|| b instanceof net.minecraft.world.level.block.FenceGateBlock || b instanceof net.minecraft.world.level.block.LiquidBlock || b instanceof TrapDoorBlock && dy > 0) {
			return false;
		}
		String p = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath();
		if (b instanceof BedBlock || b instanceof CarpetBlock || p.endsWith("cauldron") || p.endsWith("anvil")) {
			return true;
		}
		switch (p) {
			case "crafting_table", "furnace", "blast_furnace", "smoker", "grindstone", "stonecutter", "loom", "cartography_table", "fletching_table",
				 "smithing_table", "brewing_stand", "composter", "lectern", "chest", "trapped_chest", "barrel" -> {
				return true;
			}
			default -> {
			}
		}
		if (p.contains("shelf") || p.endsWith("_table") || p.endsWith("pot") || p.contains("banner") || p.endsWith("_sign") || p.equals("jukebox")
			|| p.equals("note_block") || p.equals("bell") || p.endsWith("campfire") || p.contains("pressure_plate") || p.endsWith("cake")
			|| p.contains("candle") || p.equals("hay_block") || p.endsWith("_head") || p.endsWith("_skull") || p.equals("beehive") || p.equals("bee_nest")) {
			return true;
		}
		return dy == 0 && (b instanceof StairBlock || b instanceof SlabBlock && s.getValue(SlabBlock.TYPE) == SlabType.TOP || b instanceof FenceBlock
			|| b instanceof TrapDoorBlock);
	}
}
