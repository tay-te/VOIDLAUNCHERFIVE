package dev.voidmc.hearth.repair;

import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Puts one damaged block back exactly as the blueprint has it, with the edit safety of the sim's applier: it overwrites
 * only air, plants, fluids, fire and natural ground (never a player's blocks, never a block entity that is not the
 * plot's own), restores both halves of doors and beds together, loads the step's block-entity data (sign text, banner
 * patterns), and lets neighbours (fences, panes, walls) reconnect. Litter entries are cleared instead, dropping the
 * block as an item so nothing a player owned is destroyed.
 */
public final class Restorer {
	public enum Result {
		/** Put back (or litter cleared). */
		PLACED,
		/** Already as the blueprint has it (someone else mended it). */
		SAME,
		/** Something not ours and not natural stands there: left alone, and the entry dropped. */
		REFUSED,
		/** Not now: its chunk is not loaded, or what holds it up is still missing. */
		DEFERRED,
		/** The plot or its blueprint is gone: the entry is dropped. */
		GONE
	}

	private Restorer() {
	}

	/** The blueprint's block for an entry, looked up (and cached on it). Null if the plot no longer stands there. */
	public static @Nullable BlockState expected(RepairRuntime rt, Settlement s, PlotLocator.Source source, Damage d) {
		if (d.expected() != null) {
			return d.expected();
		}
		PlotView plot = s.plot(d.plot);
		if (plot == null || !PlotLocator.standing(plot)) {
			return null;
		}
		BlockState e = rt.locator.cellOf(s, source, plot, d.pos);
		d.setExpected(e);
		return e;
	}

	/**
	 * Restores an entry in the world.
	 *
	 * @param effects sounds and particles (a builder at work), or silent (abstract repair applied on chunk load)
	 */
	public static Result restore(RepairRuntime rt, ServerLevel level, Settlement s, PlotLocator.Source source, Damage d, boolean effects) {
		PlotView plot = s.plot(d.plot);
		if (plot == null || !PlotLocator.standing(plot)) {
			return Result.GONE;
		}
		BlockState expected = expected(rt, s, source, d);
		if (expected == null) {
			return Result.GONE;
		}
		BlockPos pos = d.blockPos();
		if (!level.isLoaded(pos)) {
			return Result.DEFERRED;
		}
		BlockState current = level.getBlockState(pos);
		if (d.has(Damage.LITTER)) {
			if (current.isAir() || current.canBeReplaced()) {
				return Result.SAME;
			}
			if (!DamageRules.litterBlock(current) && !DamageRules.restorable(current)) {
				return Result.REFUSED;
			}
			rt.tracker.beginSelf();
			try {
				level.destroyBlock(pos, true);
			} finally {
				rt.tracker.endSelf();
			}
			return Result.PLACED;
		}
		if (DamageRules.sameBlock(expected, current)) {
			return Result.SAME;
		}
		if (!DamageRules.restorable(current)) {
			return Result.REFUSED;
		}
		// doors, tall plants and beds go back whole, the half that stands on the ground first
		BlockPos main = pos;
		BlockState mainState = expected;
		BlockPos second = partner(pos, expected);
		BlockState secondState = second == null ? null : rt.locator.cellOf(s, source, plot, second.asLong());
		if (secondState == null || !level.isLoaded(second)) {
			second = null;
			secondState = null;
		} else if (expected.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
			&& expected.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
			main = second;
			mainState = secondState;
			second = pos;
			secondState = expected;
		}
		BlockState mainNow = level.getBlockState(main);
		boolean mainSame = DamageRules.sameBlock(mainState, mainNow);
		if (!mainSame && !DamageRules.restorable(mainNow)) {
			return Result.REFUSED;
		}
		if (!mainSame && !mainState.canSurvive(level, main)) {
			return Result.DEFERRED;
		}
		CompoundTag tag = expected.hasBlockEntity() ? blockEntityTag(source, s, plot, d) : null;
		rt.tracker.beginSelf();
		try {
			if (!mainSame) {
				place(level, main, mainState, mainState.hasBlockEntity() ? tag : null);
			}
			if (second != null) {
				BlockState there = level.getBlockState(second);
				if (!DamageRules.sameBlock(secondState, there) && DamageRules.restorable(there)) {
					place(level, second, secondState, secondState.hasBlockEntity() ? tag : null);
				}
				BlockPos other = second.equals(pos) ? main : second;
				RepairQueue q = s.data(RepairModule.QUEUE);
				if (q.remove(other.asLong()) != null) {
					s.markDirty();
				}
				secondState.updateNeighbourShapes(level, second, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
			}
			mainState.updateNeighbourShapes(level, main, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
		} finally {
			rt.tracker.endSelf();
		}
		if (effects) {
			SoundType sound = expected.getSoundType();
			level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
			Vec3 c = Vec3.atCenterOf(pos);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, expected), c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.02);
		}
		return Result.PLACED;
	}

	/** Clears litter with the break effect, for builders. */
	public static void breakEffect(ServerLevel level, BlockPos pos, BlockState state) {
		level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
	}

	private static void place(ServerLevel level, BlockPos pos, BlockState state, @Nullable CompoundTag tag) {
		level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE);
		if (tag != null) {
			BlockEntity be = level.getBlockEntity(pos);
			if (be != null) {
				try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(be.problemPath(), RepairModule.LOG)) {
					be.loadWithComponents(TagValueInput.create(reporter, level.registryAccess(), tag));
					// (not be.setChanged(): its comparator update reads the neighbours, which may be in a chunk not loaded)
					level.blockEntityChanged(pos);
					level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	/** The other half of a door, tall plant or bed, which goes back with it. */
	static @Nullable BlockPos partner(BlockPos pos, BlockState state) {
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
			return state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
		}
		if (state.getBlock() instanceof BedBlock && state.hasProperty(BlockStateProperties.BED_PART)) {
			Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
			return state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT ? pos.relative(facing) : pos.relative(facing.getOpposite());
		}
		return null;
	}

	/** The step's block-entity data (sign text, a lectern's book), read from the blueprint: worth a walk of one stage. */
	private static @Nullable CompoundTag blockEntityTag(PlotLocator.Source source, Settlement s, PlotView plot, Damage d) {
		Blueprint bp = source.blueprint(s, plot);
		if (bp == null) {
			return null;
		}
		BlockPos pos = d.blockPos();
		if (d.index < 0) {
			d.index = PlotLocator.resolveIndex(bp, d.stage, pos);
		}
		if (d.index < 0) {
			return null;
		}
		CompoundTag[] out = new CompoundTag[1];
		bp.step(d.stage, d.index, (x, y, z, state, tag) -> {
			if (x == pos.getX() && y == pos.getY() && z == pos.getZ()) {
				out[0] = tag;
			}
		});
		return out[0];
	}
}
