package dev.voidmc.hearth.build.kit.civic;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Material;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * A block family that no style role names: the quartz of a column, the gold of a vault, the blue of a chapel dome. Cut
 * like a style material ({@link Material} syntax, fallback chains included) into full blocks, stairs, slabs and walls,
 * handed out as {@link Paint}s in a fixed stage. Use sparingly and only for what is not regional (metals, the fittings of
 * a bank or a bath); regional colour comes from the style's roles, or from a role's own material recut (see
 * {@link #of(Draft, dev.voidmc.hearth.build.style.Role, Form)}).
 */
public final class Fam {
	private static final Map<String, Material> CACHE = new ConcurrentHashMap<>();

	private final Material material;
	private final StageKind stage;

	private Fam(Material material, StageKind stage) {
		this.material = material;
		this.stage = stage;
	}

	/** A family by material spec ({@code minecraft:quartz_block}, {@code dye:blue}, {@code a|b} fallbacks). */
	public static Fam of(String spec, StageKind stage) {
		return new Fam(CACHE.computeIfAbsent(spec, Material::of), stage);
	}

	/**
	 * The family of the block a style role answers in some form, recut: the role's main block in {@code form}, taken as a
	 * material of its own (the concrete of a dye role as a family with stairs and slabs).
	 */
	public static @Nullable Fam of(Draft d, dev.voidmc.hearth.build.style.Role role, Form form, StageKind stage) {
		Block b = d.mat(role).main(form);
		if (b == null) {
			return null;
		}
		return of(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).toString(), stage);
	}

	public boolean has(Form form) {
		return this.material.has(form);
	}

	public @Nullable Block block(Form form) {
		return this.material.block(form);
	}

	/** The block in a form, falling back to the full block, then stone. */
	public Block blockOr(Form form) {
		Block b = this.material.block(form);
		if (b == null) {
			b = this.material.block(Form.FULL);
		}
		return b != null ? b : Blocks.STONE;
	}

	private Paint paint(BlockState s) {
		StageKind st = this.stage;
		return new Paint() {
			@Override
			public BlockState at(int x, int y, int z) {
				return s;
			}

			@Override
			public StageKind stage() {
				return st;
			}
		};
	}

	public Paint full() {
		return paint(blockOr(Form.FULL).defaultBlockState());
	}

	public Paint form(Form form) {
		return paint(blockOr(form).defaultBlockState());
	}

	/** A stair (or a slab, or the full block, where the family has none). */
	public Paint stairs(Direction facing, Half half) {
		if (has(Form.STAIRS)) {
			return paint(blockOr(Form.STAIRS).defaultBlockState().trySetValue(BlockStateProperties.HORIZONTAL_FACING, facing)
				.trySetValue(BlockStateProperties.HALF, half));
		}
		if (has(Form.SLAB)) {
			return slab(half == Half.TOP ? SlabType.TOP : SlabType.BOTTOM);
		}
		return full();
	}

	public Paint slab(SlabType type) {
		if (!has(Form.SLAB)) {
			return full();
		}
		return paint(blockOr(Form.SLAB).defaultBlockState().trySetValue(BlockStateProperties.SLAB_TYPE, type));
	}

	/** A wall post (or the full block). */
	public Paint wall() {
		return has(Form.WALL) ? paint(blockOr(Form.WALL).defaultBlockState()) : full();
	}

	public Paint log(Direction.Axis axis) {
		return paint(blockOr(Form.LOG).defaultBlockState().trySetValue(BlockStateProperties.AXIS, axis));
	}

	/** The same shape in this family: a stair stays a stair (facing, half), a slab a slab, a wall a wall. */
	public BlockState recut(BlockState s) {
		Block b = s.getBlock();
		if (s.hasProperty(BlockStateProperties.STAIRS_SHAPE) && has(Form.STAIRS)) {
			return blockOr(Form.STAIRS).withPropertiesOf(s);
		}
		if (s.hasProperty(BlockStateProperties.SLAB_TYPE) && has(Form.SLAB)) {
			return blockOr(Form.SLAB).withPropertiesOf(s);
		}
		if (b instanceof net.minecraft.world.level.block.WallBlock && has(Form.WALL)) {
			return blockOr(Form.WALL).withPropertiesOf(s);
		}
		if (s.hasProperty(BlockStateProperties.STAIRS_SHAPE) || s.hasProperty(BlockStateProperties.SLAB_TYPE)) {
			// no such cut in this family: a full block keeps the volume rather than leaving a hole
			return s.hasProperty(BlockStateProperties.SLAB_TYPE) && s.getValue(BlockStateProperties.SLAB_TYPE) != SlabType.DOUBLE && has(Form.SLAB)
				? blockOr(Form.SLAB).withPropertiesOf(s) : blockOr(Form.FULL).defaultBlockState();
		}
		return blockOr(Form.FULL).defaultBlockState();
	}

	/**
	 * Restates every drawn, non-air cell of a box in this family, keeping shapes (stairs stay stairs): a dome rasterised
	 * in the roof role becomes a blue one, a run of roof stairs becomes stone. Cells keep their stage.
	 */
	public void recolor(Draft d, int x0, int y0, int z0, int x1, int y1, int z1, java.util.function.Predicate<BlockState> which) {
		for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
					BlockState s = d.get(x, y, z);
					if (s == null || s.isAir() || !which.test(s)) {
						continue;
					}
					d.restate(x, y, z, recut(s));
				}
			}
		}
	}

	@Override
	public String toString() {
		return this.material.spec();
	}
}
