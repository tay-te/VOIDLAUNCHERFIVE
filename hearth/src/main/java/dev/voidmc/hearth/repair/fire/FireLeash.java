package dev.voidmc.hearth.repair.fire;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.sim.LivingWorldStats;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The leash on a scripted fire (docs/LIVING_WORLD_2.md §3.3, "Live fire"; §12 risk 5): a town event lights a fire on one
 * timber building's roof, and the leash watches that plot's box and the band round it every 10 ticks, snuffing any flame
 * outside the box + {@link #MARGIN} — so it never spreads into the next house or a player's build. The brigade and the
 * player put it out (the repair module's fire watch, as for any fire); what burned is real damage the builders repair. It
 * looks at a section's palette first, so a section with no flame costs one check (§3.4: ≤ 0.1 ms a tick while a fire
 * burns). The core ({@link #pass}) is pure over {@link Cells}, so it is tested without a world.
 */
public final class FireLeash {
	/** Flames may burn this far outside the plot's box; beyond, they are snuffed. */
	public static final int MARGIN = 2;
	/** How far past the margin the leash looks (a fire spreads at most a few blocks a tick of its own). */
	public static final int REACH = 6;
	public static final int PASS_TICKS = 10;
	private static final List<Leash> LEASHES = new ArrayList<>();
	private static final LivingWorldStats.Timer TIMER = LivingWorldStats.timer("fire leash");
	private static long snuffed;

	private FireLeash() {
	}

	/** The cells a leash looks at: a world, or a test's grid. */
	public interface Cells {
		/** Whether any cell of the section (16³, by section coordinates) may hold a flame: a palette check. */
		boolean mayBurn(int sx, int sy, int sz);

		boolean fire(int x, int y, int z);

		void snuff(int x, int y, int z);
	}

	/** One fire on a leash: the box it may burn in, until when, in which world. */
	public record Leash(ServerLevel level, BoundingBox box, long until) {
	}

	/** The box + margin a fire in this plot box may burn in. */
	public static BoundingBox allowed(BoundingBox box) {
		return box.inflatedBy(MARGIN);
	}

	/**
	 * One pass of the leash: every flame in the box + margin + reach that lies outside the box + margin is snuffed.
	 * Returns how many.
	 */
	public static int pass(Cells cells, BoundingBox box) {
		BoundingBox ok = allowed(box);
		BoundingBox look = box.inflatedBy(MARGIN + REACH);
		int n = 0;
		for (int sx = SectionPos.blockToSectionCoord(look.minX()); sx <= SectionPos.blockToSectionCoord(look.maxX()); sx++) {
			for (int sz = SectionPos.blockToSectionCoord(look.minZ()); sz <= SectionPos.blockToSectionCoord(look.maxZ()); sz++) {
				for (int sy = SectionPos.blockToSectionCoord(look.minY()); sy <= SectionPos.blockToSectionCoord(look.maxY()); sy++) {
					if (!cells.mayBurn(sx, sy, sz)) {
						continue;
					}
					int x0 = Math.max(look.minX(), sx << 4);
					int x1 = Math.min(look.maxX(), (sx << 4) + 15);
					int y0 = Math.max(look.minY(), sy << 4);
					int y1 = Math.min(look.maxY(), (sy << 4) + 15);
					int z0 = Math.max(look.minZ(), sz << 4);
					int z1 = Math.min(look.maxZ(), (sz << 4) + 15);
					for (int x = x0; x <= x1; x++) {
						for (int z = z0; z <= z1; z++) {
							for (int y = y0; y <= y1; y++) {
								if (!ok.isInside(x, y, z) && cells.fire(x, y, z)) {
									cells.snuff(x, y, z);
									n++;
								}
							}
						}
					}
				}
			}
		}
		return n;
	}

	/** Every flame inside the box + margin + reach goes out (the fire's time is up, or the scene ended). */
	public static int putOut(Cells cells, BoundingBox box) {
		BoundingBox look = box.inflatedBy(MARGIN + REACH);
		int n = 0;
		for (int x = look.minX(); x <= look.maxX(); x++) {
			for (int z = look.minZ(); z <= look.maxZ(); z++) {
				for (int y = look.minY(); y <= look.maxY(); y++) {
					if (cells.fire(x, y, z)) {
						cells.snuff(x, y, z);
						n++;
					}
				}
			}
		}
		return n;
	}

	// ---------------------------------------------------------------- in the world

	/** Puts a fire in a plot's box on the leash until {@code until} (game time); returns the leash. */
	public static Leash hold(ServerLevel level, BoundingBox plotBox, long until) {
		Leash l = new Leash(level, plotBox, until);
		LEASHES.add(l);
		return l;
	}

	/** Lets a fire go: every flame left in and round its box is put out. */
	public static void release(Leash l) {
		LEASHES.remove(l);
		putOut(cells(l.level()), l.box());
	}

	public static boolean active() {
		return !LEASHES.isEmpty();
	}

	/** Whether a fire still burns in a leashed box (its flames, from the palettes). */
	public static boolean burning(Leash l) {
		Cells c = cells(l.level());
		BoundingBox look = allowed(l.box());
		for (int x = look.minX(); x <= look.maxX(); x++) {
			for (int z = look.minZ(); z <= look.maxZ(); z++) {
				for (int y = look.minY(); y <= look.maxY(); y++) {
					if (c.fire(x, y, z)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** Every 10 ticks, every leash: flames beyond its margin are snuffed; a leash past its time puts its fire out. */
	public static void tick(MinecraftServer server) {
		if (LEASHES.isEmpty() || server.overworld().getGameTime() % PASS_TICKS != 0) {
			return;
		}
		long t0 = System.nanoTime();
		for (Leash l : List.copyOf(LEASHES)) {
			try {
				if (l.level().getGameTime() >= l.until()) {
					release(l);
				} else {
					snuffed += pass(cells(l.level()), l.box());
				}
			} catch (RuntimeException e) {
				Hearth.LOG.warn("fire leash: a pass failed; the fire is put out", e);
				LEASHES.remove(l);
			}
		}
		TIMER.add(System.nanoTime() - t0);
	}

	public static void clear() {
		for (Leash l : List.copyOf(LEASHES)) {
			release(l);
		}
		LEASHES.clear();
	}

	public static String stats() {
		return "fire leash: " + LEASHES.size() + " fires held, " + snuffed + " flames snuffed beyond their margin, " + String.format(java.util.Locale.ROOT,
			"%.3f ms a pass", TIMER.averageMs());
	}

	/** A world's cells: palettes of loaded chunks only (an unloaded chunk cannot burn). */
	static Cells cells(ServerLevel level) {
		return new Cells() {
			@Override
			public boolean mayBurn(int sx, int sy, int sz) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(sx, sz);
				if (chunk == null) {
					return false;
				}
				int idx = chunk.getSectionIndexFromSectionY(sy);
				if (idx < 0 || idx >= chunk.getSectionsCount()) {
					return false;
				}
				LevelChunkSection section = chunk.getSection(idx);
				return !section.hasOnlyAir() && section.maybeHas(s -> s.getBlock() instanceof BaseFireBlock);
			}

			@Override
			public boolean fire(int x, int y, int z) {
				BlockPos p = new BlockPos(x, y, z);
				return level.isLoaded(p) && level.getBlockState(p).getBlock() instanceof BaseFireBlock;
			}

			@Override
			public void snuff(int x, int y, int z) {
				level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		};
	}

	/**
	 * Lights a fire on the roof of a building: on the highest flammable blocks over the middle of its box (at most
	 * {@code count}). Returns the flames lit.
	 */
	public static int light(ServerLevel level, BoundingBox box, int count) {
		int lit = 0;
		int cx = (box.minX() + box.maxX()) / 2;
		int cz = (box.minZ() + box.maxZ()) / 2;
		int[][] offsets = {{0, 0}, {1, 0}, {-1, 1}, {0, -1}, {2, 1}, {-2, -1}};
		for (int[] o : offsets) {
			if (lit >= count) {
				break;
			}
			int x = cx + o[0];
			int z = cz + o[1];
			BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
			if (top.getY() > box.maxY() + 1 || top.getY() <= box.minY() || !level.isLoaded(top)) {
				continue;
			}
			BlockState roof = level.getBlockState(top.below());
			BlockState here = level.getBlockState(top);
			if (here.isAir() && roof.ignitedByLava()) {
				level.setBlock(top, BaseFireBlock.getState(level, top), Block.UPDATE_ALL);
				lit++;
			}
		}
		return lit;
	}
}
