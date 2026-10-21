package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.repair.RepairRuntime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A burnt house as the world shows it (docs/LIVING_WORLD_2.md §3.1 "Fire and rebuilding", §3.2): a fire decided in the
 * books (an abstract town's fire, a raid's barn, an embodied fire shown only as smoke) leaves the plot's blackened shell
 * the next time its chunks are about a player — the roof and the upper timber gone, beams left charred — and every block
 * of it is damage the repair module's builders put back from the blueprint, as they mend any fire. Applied once a burnt
 * plot (the book marks it), only in loaded chunks, only over the plot's own blueprint blocks still as built.
 *
 * <p>(The design's {@code burnt} flavour of the build module's decay pass would draw the same shell into the plot's
 * spec; until the build module has it, the shell is laid here, which the repairs undo just the same.)
 */
final class BurntShell {
	/** The most blocks one shell takes (the top of a large barn's roof is enough to read as burnt). */
	static final int MAX_BLOCKS = 600;

	private BurntShell() {
	}

	/** Lays the shell of a burnt plot in the world; returns how many blocks it changed (0 if the chunks are not all here). */
	static int apply(HearthWorld w, Settlement s, PlotView plot, double share, long seed) {
		ServerLevel level = w.level();
		if (!HearthServices.hasBlueprints()) {
			return 0;
		}
		dev.voidmc.hearth.api.Blueprint bp;
		try {
			bp = HearthServices.blueprints().blueprint(plot.spec(), w.terrain());
		} catch (RuntimeException e) {
			return 0;
		}
		var box = bp.bounds();
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				if (level.getChunkSource().getChunkNow(cx, cz) == null) {
					return 0; // the whole shell or none of it
				}
			}
		}
		// the burnable blocks the blueprint placed that still stand as built
		List<long[]> burnable = new ArrayList<>();
		for (int stage = 0; stage < bp.stageCount(); stage++) {
			for (int i = 0; i < bp.stepCount(stage); i++) {
				bp.step(stage, i, (x, y, z, state, tag) -> {
					if (!state.isAir() && state.ignitedByLava()) {
						burnable.add(new long[]{BlockPos.asLong(x, y, z), y});
					}
				});
			}
		}
		if (burnable.isEmpty()) {
			return 0;
		}
		// the fire took the top first: the roof, then the upper floors
		burnable.sort(Comparator.comparingLong((long[] a) -> -a[1]));
		int gone = (int) Math.min(MAX_BLOCKS, Math.round(burnable.size() * Math.min(0.9, share)));
		SplittableRandom r = new SplittableRandom(seed);
		RepairRuntime rt = RepairRuntime.current();
		int changed = 0;
		for (int k = 0; k < burnable.size() && k < gone + gone / 3; k++) {
			BlockPos p = BlockPos.of(burnable.get(k)[0]);
			BlockState before = level.getBlockState(p);
			if (!before.ignitedByLava()) {
				continue; // not as built any more (a player's change, mended already)
			}
			// the top is gone; below it, a third of the timber is left charred
			BlockState after = k < gone ? Blocks.AIR.defaultBlockState() : charred(before, r);
			if (after == before) {
				continue;
			}
			level.setBlock(p, after, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			if (rt != null) {
				try {
					rt.tracker.changed(level, p, before, after, SettlementEvents.Damaged.Cause.FIRE, null);
				} catch (RuntimeException e) {
					Hearth.LOG.debug("events: the repairs were not told of a burnt block", e);
				}
			}
			changed++;
		}
		return changed;
	}

	/** Timber left standing black: logs and beams to dark wood, planks to dark planks, thatch and wool to nothing. */
	static BlockState charred(BlockState s, SplittableRandom r) {
		if (s.hasProperty(BlockStateProperties.AXIS)) {
			return Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, s.getValue(BlockStateProperties.AXIS));
		}
		String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
		if (name.endsWith("_planks")) {
			return r.nextInt(4) == 0 ? Blocks.COAL_BLOCK.defaultBlockState() : Blocks.DARK_OAK_PLANKS.defaultBlockState();
		}
		if (name.endsWith("_stairs") || name.endsWith("_slab") || name.endsWith("_fence") || name.contains("wool") || name.contains("hay")
			|| name.contains("carpet")) {
			return Blocks.AIR.defaultBlockState();
		}
		return s;
	}
}
