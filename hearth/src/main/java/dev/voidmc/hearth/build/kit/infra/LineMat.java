package dev.voidmc.hearth.build.kit.infra;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
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
 * A style role for infrastructure: like {@link dev.voidmc.hearth.build.draft.Mat}, but mixed in <b>world</b>
 * coordinates with a seed that belongs to the culture rather than the plot, and always in the style's base dressing.
 * A long road is several plots, issued months apart; drawn this way its paving runs on across the joints without a
 * seam, its lamps are of one make, and one plot never comes out in a different variant's kerbs than the next.
 * Directions given to the forms are world directions (turned to the plot's local labels here).
 */
public final class LineMat {
	private final Draft d;
	private final Lay lay;
	private final Role role;
	private final Palette palette;
	private final long seed;
	/** A fixed lowness (walked surfaces do not gather moss), or NaN to measure it from the ground. */
	private final double low;

	private LineMat(Draft d, Lay lay, Role role, Palette palette, long seed, double low) {
		this.d = d;
		this.lay = lay;
		this.role = role;
		this.palette = palette;
		this.seed = seed;
		this.low = low;
	}

	/**
	 * The same role keeping only its {@code n} heaviest materials (a street's setts without the odd smooth stone of a
	 * wall's mix; one stone for every step of a flight).
	 */
	public LineMat top(int n) {
		java.util.List<Palette.Entry> es = new java.util.ArrayList<>(this.palette.entries());
		if (es.size() <= n) {
			return this;
		}
		java.util.List<Palette.Entry> sorted = new java.util.ArrayList<>(es);
		sorted.sort((a, b) -> Double.compare(b.weight(), a.weight()));
		java.util.List<Palette.Entry> keep = new java.util.ArrayList<>();
		for (Palette.Entry e : es) {
			if (sorted.indexOf(e) < n) {
				keep.add(e);
			}
		}
		return new LineMat(this.d, this.lay, this.role, new Palette(keep), this.seed, this.low);
	}

	/** The same role with a fixed weathering (0 clean, 1 as at the foot of a wall): road surfaces are walked clean. */
	public LineMat weathered(double lowness) {
		return new LineMat(this.d, this.lay, this.role, this.palette, this.seed, lowness);
	}

	/** The role at the draft's tier, base dressing, seeded by culture. */
	public static LineMat of(Lay lay, Role role) {
		return of(lay, role, lay.draft().tier());
	}

	/** The role at a given tier (a road dressed above or below its plot's tier). */
	public static LineMat of(Lay lay, Role role, int tier) {
		Draft d = lay.draft();
		long seed = Noise.hash(d.style().id().toString().hashCode() * 0x9E3779B97F4A7C15L, 0x11F0 + role.ordinal());
		Palette p = d.style().palette(role, tier, -1);
		return new LineMat(d, lay, role, role.perBuilding() ? p.chooseOne(seed) : p, seed, Double.NaN);
	}

	public Role role() {
		return this.role;
	}

	public boolean has(Form form) {
		return this.palette.has(form);
	}

	/** The block of the role's main material in a form, or null. */
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

	public Block mainOr(Form form, Block fallback) {
		Block b = main(form);
		return b != null ? b : fallback;
	}

	/** The block at a world position in a form (falls back to the full block, then stone). */
	public Block block(Form form, int wx, int wy, int wz) {
		double low = this.low;
		if (Double.isNaN(low)) {
			int g = this.lay.ground(wx, wz);
			low = Math.clamp(1.0 - (wy - g) / 2.5, 0, 1);
		}
		Block b = this.palette.pick(form, this.seed, wx, wy, wz, low, 0, 0, 0, this.role.patchiness());
		if (b == null) {
			b = this.palette.pick(Form.FULL, this.seed, wx, wy, wz, low, 0, 0, 0, this.role.patchiness());
		}
		return b != null ? b : Blocks.STONE;
	}

	private Paint paint(Form form, java.util.function.UnaryOperator<BlockState> props) {
		StageKind stage = this.role.stage();
		Lay l = this.lay;
		return new Paint() {
			@Override
			public BlockState at(int x, int y, int z) {
				int wx = l.wx(x, z);
				int wz = l.wz(x, z);
				return props.apply(block(form, wx, y + l.floorY(), wz).defaultBlockState());
			}

			@Override
			public StageKind stage() {
				return stage;
			}
		};
	}

	public Paint full() {
		return paint(Form.FULL, s -> s);
	}

	public Paint form(Form form) {
		return paint(form, s -> s);
	}

	/** A stair whose high back faces the world direction {@code facing}. */
	public Paint stairs(Direction facing, Half half) {
		Direction lf = this.lay.local(facing);
		return paint(Form.STAIRS, s -> s.trySetValue(BlockStateProperties.HORIZONTAL_FACING, lf).trySetValue(BlockStateProperties.HALF, half));
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

	/** A fence where the role has fences, else a wall post. */
	public Paint post() {
		return has(Form.FENCE) ? fence() : has(Form.WALL) ? wall() : full();
	}

	/** A log laid along a world axis. */
	public Paint log(Direction.Axis worldAxis) {
		Direction.Axis a = this.lay.local(worldAxis);
		return paint(Form.LOG, s -> s.trySetValue(BlockStateProperties.AXIS, a));
	}

	public Paint strippedLog(Direction.Axis worldAxis) {
		Direction.Axis a = this.lay.local(worldAxis);
		return paint(Form.STRIPPED_LOG, s -> s.trySetValue(BlockStateProperties.AXIS, a));
	}

	public Paint wood() {
		return paint(Form.WOOD, s -> s.trySetValue(BlockStateProperties.AXIS, Direction.Axis.Y));
	}

	public Paint gate(Direction facing, boolean open) {
		Direction lf = this.lay.local(facing);
		return paint(Form.FENCE_GATE, s -> s.trySetValue(BlockStateProperties.HORIZONTAL_FACING, lf).trySetValue(BlockStateProperties.OPEN, open));
	}

	/** A trapdoor; {@code facing} a world direction (open ones stand against the side opposite it). */
	public Paint trapdoor(Direction facing, Half half, boolean open) {
		Direction lf = this.lay.local(facing);
		return paint(Form.TRAPDOOR, s -> s.trySetValue(BlockStateProperties.HORIZONTAL_FACING, lf).trySetValue(BlockStateProperties.HALF, half)
			.trySetValue(BlockStateProperties.OPEN, open));
	}

	/** A stair if the role cuts stairs, else a slab, else the full block. */
	public Paint stairsOrSlab(Direction facing, Half half) {
		if (has(Form.STAIRS)) {
			return stairs(facing, half);
		}
		if (has(Form.SLAB)) {
			return slab(half == Half.TOP ? SlabType.TOP : SlabType.BOTTOM);
		}
		return full();
	}

	/** A slab if the role cuts slabs, else null (callers decide what a half step becomes without one). */
	public @Nullable Paint slabOrNull(SlabType type) {
		return has(Form.SLAB) ? slab(type) : null;
	}

	/** A full block, or a log standing on end for a log edge (a lamp's footing in a log-edged road). */
	public Paint edgeFull(Dress.EdgeKind kind) {
		return kind == Dress.EdgeKind.LOG && has(Form.LOG) ? log(Direction.Axis.Y) : full();
	}

	/** The registry path of the role's main full block ("cobblestone"), for telling materials apart. */
	public String mainName() {
		Block b = main(Form.FULL);
		return b == null ? "" : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath();
	}

	/** Whether the role's main material is timber (planks and logs): boardwalks, stilt roads. */
	public boolean timber() {
		Block b = main(Form.FULL);
		if (b == null) {
			return false;
		}
		// by name, not by tag: tags are not bound offline (previews, tests), and a blueprint must not depend on them
		String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath();
		return path.endsWith("_planks") || path.endsWith("_log") || path.endsWith("_wood") || path.equals("bamboo_block");
	}
}
