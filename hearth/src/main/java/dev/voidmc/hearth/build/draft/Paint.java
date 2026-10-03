package dev.voidmc.hearth.build.draft;

import dev.voidmc.hearth.api.StageKind;
import java.util.function.UnaryOperator;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * What to put at a position: a block state as a function of local coordinates. Style roles hand out paints that mix
 * their materials by position ({@code d.mat(Role.WALL).full()}); a fixed state is {@code Paint.of(state)}. A null
 * result leaves the position alone, so a paint can also be a mask.
 *
 * <p>A paint may carry a {@link #stage()}: the stage a role places in by default. The draft uses the stage set with
 * {@link Draft#stage(StageKind)} first, then the paint's, then a guess from the block.
 */
@FunctionalInterface
public interface Paint {
	@Nullable BlockState at(int x, int y, int z);

	/** The stage this paint's blocks belong to, or null to let the draft decide. */
	default @Nullable StageKind stage() {
		return null;
	}

	static Paint of(BlockState state) {
		return (x, y, z) -> state;
	}

	static Paint of(Block block) {
		BlockState s = block.defaultBlockState();
		return (x, y, z) -> s;
	}

	/** The same blocks, placed in the given stage. */
	default Paint inStage(StageKind stage) {
		Paint self = this;
		return new Paint() {
			@Override
			public @Nullable BlockState at(int x, int y, int z) {
				return self.at(x, y, z);
			}

			@Override
			public StageKind stage() {
				return stage;
			}
		};
	}

	/** The same positions with each state changed (rotated, a property set). Keeps the stage. */
	default Paint map(UnaryOperator<BlockState> f) {
		Paint self = this;
		return new Paint() {
			@Override
			public @Nullable BlockState at(int x, int y, int z) {
				BlockState s = self.at(x, y, z);
				return s == null ? null : f.apply(s);
			}

			@Override
			public @Nullable StageKind stage() {
				return self.stage();
			}
		};
	}

	/** This paint where it answers, otherwise the other. */
	default Paint orElse(Paint other) {
		Paint self = this;
		return new Paint() {
			@Override
			public @Nullable BlockState at(int x, int y, int z) {
				BlockState s = self.at(x, y, z);
				return s != null ? s : other.at(x, y, z);
			}

			@Override
			public @Nullable StageKind stage() {
				return self.stage() != null ? self.stage() : other.stage();
			}
		};
	}

	/** Only where the predicate holds. */
	default Paint where(Mask mask) {
		Paint self = this;
		return new Paint() {
			@Override
			public @Nullable BlockState at(int x, int y, int z) {
				return mask.test(x, y, z) ? self.at(x, y, z) : null;
			}

			@Override
			public @Nullable StageKind stage() {
				return self.stage();
			}
		};
	}

	@FunctionalInterface
	interface Mask {
		boolean test(int x, int y, int z);
	}
}
