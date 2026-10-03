package dev.voidmc.hearth.build.draft;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Palette;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * A style role bound to a draft: hands out {@link Paint}s that mix the role's materials by position (weathered low,
 * mossy on the north, accents in clusters) in any {@link Form}. Get one with {@link Draft#mat(Role)}.
 *
 * <pre>
 *   Mat roof = d.mat(Role.ROOF);
 *   d.set(x, y, z, roof.stairs(Dirs.BACK, Half.BOTTOM));
 *   d.fill(0, -1, 0, 6, -1, 6, d.mat(Role.FLOOR).full());
 * </pre>
 */
public final class Mat {
	private final Draft draft;
	private final Role role;
	private final Palette palette;
	private final long seed;

	Mat(Draft draft, Role role, Palette palette, long seed) {
		this.draft = draft;
		this.role = role;
		this.palette = palette;
		this.seed = seed;
	}

	public Role role() {
		return this.role;
	}

	public Palette palette() {
		return this.palette;
	}

	/** Whether the role can be cut in this form by any of its materials. */
	public boolean has(Form form) {
		return this.palette.has(form);
	}

	/** The block of the role's main material in a form (for one-off choices: the door, the bed colour), or null. */
	public @Nullable Block main(Form form) {
		Block b = this.palette.main().block(form);
		if (b == null) {
			for (var e : this.palette.entries()) {
				b = e.material().block(form);
				if (b != null) {
					return b;
				}
			}
		}
		return b;
	}

	/** {@link #main} or a fallback. */
	public Block mainOr(Form form, Block fallback) {
		Block b = main(form);
		return b != null ? b : fallback;
	}

	/** The block at a position in a form; falls back to the full block, then to stone, so it never fails. */
	public Block block(Form form, int x, int y, int z) {
		Block b = this.palette.pick(form, this.seed, x, y, z, this.draft.lowness(x, y, z), this.draft.highness(y), this.draft.northness(x, z), 0,
			this.role.patchiness());
		if (b == null) {
			this.draft.noteMissing(this.role, form);
			b = this.palette.pick(Form.FULL, this.seed, x, y, z, 0, 0, 0, 0);
		}
		return b != null ? b : Blocks.STONE;
	}

	private Paint paint(Form form, java.util.function.UnaryOperator<BlockState> props) {
		StageKind stage = this.role.stage();
		return new Paint() {
			@Override
			public BlockState at(int x, int y, int z) {
				return props.apply(block(form, x, y, z).defaultBlockState());
			}

			@Override
			public StageKind stage() {
				return stage;
			}
		};
	}

	/** Full blocks (planks for a wood, wool for a dye). */
	public Paint full() {
		return paint(Form.FULL, s -> s);
	}

	/** Any form with its default state. */
	public Paint form(Form form) {
		return paint(form, s -> s);
	}

	public Paint stairs(Direction facing, Half half) {
		return paint(Form.STAIRS, s -> s.trySetValue(BlockStateProperties.HORIZONTAL_FACING, facing).trySetValue(BlockStateProperties.HALF, half));
	}

	public Paint slab(SlabType type) {
		return paint(Form.SLAB, s -> s.trySetValue(BlockStateProperties.SLAB_TYPE, type));
	}

	public Paint wall() {
		return paint(Form.WALL, s -> s);
	}

	public Paint fence() {
		return paint(Form.FENCE, s -> s);
	}

	public Paint gate(Direction facing, boolean open) {
		return paint(Form.FENCE_GATE, s -> s.trySetValue(BlockStateProperties.HORIZONTAL_FACING, facing).trySetValue(BlockStateProperties.OPEN, open));
	}

	public Paint log(Direction.Axis axis) {
		return paint(Form.LOG, s -> s.trySetValue(BlockStateProperties.AXIS, axis));
	}

	public Paint wood() {
		return paint(Form.WOOD, s -> s.trySetValue(BlockStateProperties.AXIS, Direction.Axis.Y));
	}

	public Paint strippedLog(Direction.Axis axis) {
		return paint(Form.STRIPPED_LOG, s -> s.trySetValue(BlockStateProperties.AXIS, axis));
	}

	public Paint strippedWood() {
		return paint(Form.STRIPPED_WOOD, s -> s.trySetValue(BlockStateProperties.AXIS, Direction.Axis.Y));
	}

	public Paint trapdoor(Direction facing, Half half, boolean open) {
		return paint(Form.TRAPDOOR, s -> s.trySetValue(BlockStateProperties.HORIZONTAL_FACING, facing).trySetValue(BlockStateProperties.HALF, half)
			.trySetValue(BlockStateProperties.OPEN, open));
	}

	public Paint pane() {
		return paint(Form.PANE, s -> s);
	}

	public Paint plate() {
		return paint(Form.PRESSURE_PLATE, s -> s);
	}

	public Paint carpet() {
		return paint(Form.CARPET, s -> s);
	}

	/** Leaves (persistent) of a leaf role. */
	public Paint leaves() {
		return paint(Form.FULL, s -> s.trySetValue(BlockStateProperties.PERSISTENT, true).trySetValue(BlockStateProperties.DISTANCE, 1));
	}

	/** A stair if the role has stairs, otherwise a bottom slab, otherwise the full block. */
	public Paint stairsOrSlab(Direction facing, Half half) {
		if (has(Form.STAIRS)) {
			return stairs(facing, half);
		}
		if (has(Form.SLAB)) {
			return slab(half == Half.TOP ? SlabType.TOP : SlabType.BOTTOM);
		}
		return full();
	}
}
