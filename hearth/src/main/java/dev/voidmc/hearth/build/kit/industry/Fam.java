package dev.voidmc.hearth.build.kit.industry;

import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.style.Form;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * A material family the works build their fires in: either a style role ({@link Mat}, mixed by position) or a fixed
 * block and its cuts (the brick of a brick kiln is brick in every culture: it is what the kiln makes).
 */
public interface Fam {
	Paint full();

	@Nullable Paint stairs(Direction facing, Half half);

	@Nullable Paint slab(SlabType type);

	@Nullable Paint wall();

	boolean has(Form form);

	/** A stair if the family has stairs, otherwise a slab, otherwise the full block. */
	default Paint stairsOrSlab(Direction facing, Half half) {
		if (has(Form.STAIRS)) {
			return stairs(facing, half);
		}
		if (has(Form.SLAB)) {
			return slab(half == Half.TOP ? SlabType.TOP : SlabType.BOTTOM);
		}
		return full();
	}

	/** A style role as a family. */
	static Fam of(Mat m) {
		return new Fam() {
			@Override
			public Paint full() {
				return m.full();
			}

			@Override
			public Paint stairs(Direction facing, Half half) {
				return m.stairs(facing, half);
			}

			@Override
			public Paint slab(SlabType type) {
				return m.slab(type);
			}

			@Override
			public Paint wall() {
				return m.wall();
			}

			@Override
			public boolean has(Form form) {
				return m.has(form);
			}
		};
	}

	/** A fixed block and its cuts (any may be null). */
	static Fam of(Block full, @Nullable Block stairs, @Nullable Block slab, @Nullable Block wall) {
		return new Fam() {
			@Override
			public Paint full() {
				return Paint.of(full.defaultBlockState());
			}

			@Override
			public @Nullable Paint stairs(Direction facing, Half half) {
				return stairs == null ? null : Paint.of(States.stairs(stairs, facing, half));
			}

			@Override
			public @Nullable Paint slab(SlabType type) {
				return slab == null ? null : Paint.of(States.slab(slab, type));
			}

			@Override
			public @Nullable Paint wall() {
				return wall == null ? null : Paint.of(wall.defaultBlockState());
			}

			@Override
			public boolean has(Form form) {
				return switch (form) {
					case FULL -> true;
					case STAIRS -> stairs != null;
					case SLAB -> slab != null;
					case WALL -> wall != null;
					default -> false;
				};
			}
		};
	}

	/** Fired brick. */
	static Fam brick() {
		return of(net.minecraft.world.level.block.Blocks.BRICKS, net.minecraft.world.level.block.Blocks.BRICK_STAIRS,
			net.minecraft.world.level.block.Blocks.BRICK_SLAB, net.minecraft.world.level.block.Blocks.BRICK_WALL);
	}

	/** Sun-dried mud brick. */
	static Fam mudBrick() {
		return of(net.minecraft.world.level.block.Blocks.MUD_BRICKS, net.minecraft.world.level.block.Blocks.MUD_BRICK_STAIRS,
			net.minecraft.world.level.block.Blocks.MUD_BRICK_SLAB, net.minecraft.world.level.block.Blocks.MUD_BRICK_WALL);
	}
}
