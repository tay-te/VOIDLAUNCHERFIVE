package dev.voidmc.hearth.build.kit.landmark;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.blueprint.MaterialBill;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Material;
import dev.voidmc.hearth.build.style.Palette;
import dev.voidmc.hearth.build.style.Role;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * A material for monumental work, with the cuts masonry needs (full, stairs, slab, wall). Most of the time it is simply
 * a style role ({@link #of}); {@link #masonry} and {@link #dressing} pick the style's best stone for the walls and the
 * dressings of a landmark: a culture that builds its houses of timber, logs or boards still raises its tower in the
 * stone of its plinths and footings, and a style with no stone at all (palm wood, willow boards) falls back to a
 * weathered vanilla stone mix so a lighthouse or a pyramid never comes out in planks by accident.
 *
 * <p>Every paint answers in any form: a material that cannot cut a form gives its full block (a wall post of plaster is a
 * plaster block), so callers ask {@link #has} where the difference matters.
 */
public final class Stone {
	/** A grey, weathered stone mix for styles without any stone role: stone bricks with moss and cracks, rubble low. */
	private static final Palette FALLBACK = new Palette(List.of(
		new Palette.Entry(Material.of("minecraft:stone_bricks"), 3, 0, 0, 0, 0),
		new Palette.Entry(Material.of("minecraft:mossy_stone_bricks"), 1.2, 1.5, 0, 0, 0),
		new Palette.Entry(Material.of("minecraft:cracked_stone_bricks"), 0.5, 0.5, 0, 0, 0),
		new Palette.Entry(Material.of("minecraft:mossy_cobblestone"), 0.4, 2, 0, 0, 0)));
	private static final Palette FALLBACK_DRESSED = new Palette(List.of(
		new Palette.Entry(Material.of("minecraft:stone_bricks"), 3, 0, 0, 0, 0),
		new Palette.Entry(Material.of("minecraft:mossy_stone_bricks"), 0.6, 1, 0, 0, 0)));

	private static final Palette COPPER = new Palette(List.of(
		new Palette.Entry(Material.of("minecraft:waxed_oxidized_cut_copper"), 3, 0, 0, 0, 0),
		new Palette.Entry(Material.of("minecraft:waxed_weathered_cut_copper"), 1, 0, 0, 0, 0)));

	private final Draft draft;
	private final @Nullable Mat mat;
	private final Palette palette;
	private final long seed;
	private final StageKind stage;
	/** Only the role's main material where it cuts the form asked for (crisp quoins and courses, not speckled ones). */
	private final boolean single;

	private Stone(Draft draft, @Nullable Mat mat, Palette palette, long seed, StageKind stage) {
		this(draft, mat, palette, seed, stage, false);
	}

	private Stone(Draft draft, @Nullable Mat mat, Palette palette, long seed, StageKind stage, boolean single) {
		this.draft = draft;
		this.mat = mat;
		this.palette = palette;
		this.seed = seed;
		this.stage = stage;
		this.single = single;
	}

	/** This stone laid in its main material only (falling back to the mix for cuts the main one lacks). */
	public Stone crisp() {
		return new Stone(this.draft, this.mat, this.palette, this.seed, this.stage, true);
	}

	/**
	 * Sheet for domes, onions and cupolas: the roof covering where it is masonry or metal (tile, brick, slate,
	 * sandstone), else patinated copper, as a wooden-roofed town would sheath its domes.
	 */
	public static Stone sheet(Draft d) {
		Block b = d.mat(Role.ROOF).main(Form.FULL);
		boolean wood = b != null && MaterialBill.timber(b) || d.habits().roof().thatch();
		if (!wood) {
			return of(d, Role.ROOF);
		}
		return new Stone(d, null, COPPER, Noise.hash(d.seed(), 0xC0FE), StageKind.ROOF);
	}

	/** Patinated copper sheet. */
	public static Stone copper(Draft d) {
		return new Stone(d, null, COPPER, Noise.hash(d.seed(), 0xC0FF), StageKind.ROOF);
	}

	/**
	 * The walling of a tower's panels between its piers: the style's own walls wherever they are masonry or render of
	 * any kind (white plaster between stone piers in a timber-framing town), else its masonry.
	 */
	public static Stone panel(Draft d) {
		if (stony(d, Role.WALL)) {
			return of(d, Role.WALL);
		}
		// a timber-framing town's infill is a render whatever its block is made of: white panels between stone piers
		Block w = d.mat(Role.WALL).main(Form.FULL);
		if (d.habits().walls().kind() == WallKind.TIMBER_FRAME && w != null && !d.mat(Role.WALL).has(Form.LOG)) {
			return of(d, Role.WALL);
		}
		return masonry(d);
	}

	/**
	 * Ashlar for the walls of a great church: the style's own masonry walls where it has them, otherwise its dressed
	 * plinth stone before rubble footings.
	 */
	public static Stone ashlar(Draft d) {
		if (masonryWalls(d) && stony(d, Role.WALL)) {
			return of(d, Role.WALL);
		}
		for (Role r : new Role[]{Role.PLINTH, Role.WALL_ACCENT, Role.FOUNDATION, Role.CHIMNEY}) {
			if (stony(d, r)) {
				return of(d, r);
			}
		}
		return new Stone(d, null, FALLBACK, Noise.hash(d.seed(), 0x570E), StageKind.WALLS);
	}

	/** A style role as a stone. */
	public static Stone of(Draft d, Role role) {
		Mat m = d.mat(role);
		return new Stone(d, m, m.palette(), d.seed(), role.stage());
	}

	/** Whether a role's main material is masonry (stone, brick, terracotta, mud): not wood, wool or glass. */
	public static boolean stony(Draft d, Role role) {
		Block b = d.mat(role).main(Form.FULL);
		if (b == null) {
			return false;
		}
		return MaterialBill.masonry(b);
	}

	/** Whether the style builds its walls in masonry or render (stone, brick, plaster, adobe). */
	public static boolean masonryWalls(Draft d) {
		WallKind k = d.habits().walls().kind();
		return k == WallKind.STONE || k == WallKind.BRICK || k == WallKind.PLASTER || k == WallKind.ADOBE;
	}

	/**
	 * The main walling of a landmark: the style's own walls where they are masonry or render, otherwise the first stony
	 * role of plinth, foundation, wall accent, chimney; otherwise the fallback mix.
	 */
	public static Stone masonry(Draft d) {
		if (masonryWalls(d) && stony(d, Role.WALL)) {
			return of(d, Role.WALL);
		}
		// the first stony role that differs from the dressing, so quoins and courses stand out from the walling
		Block dressing = dressing(d).main(Form.FULL);
		Role first = null;
		for (Role r : new Role[]{Role.PLINTH, Role.FOUNDATION, Role.WALL_ACCENT, Role.CHIMNEY}) {
			if (stony(d, r)) {
				if (first == null) {
					first = r;
				}
				if (d.mat(r).main(Form.FULL) != dressing) {
					return of(d, r);
				}
			}
		}
		if (first != null) {
			return of(d, first);
		}
		return new Stone(d, null, FALLBACK, Noise.hash(d.seed(), 0x570E), StageKind.WALLS);
	}

	/** Dressed stone for quoins, string courses, arches and tracery: the wall accent if it is masonry, else as masonry. */
	public static Stone dressing(Draft d) {
		for (Role r : new Role[]{Role.WALL_ACCENT, Role.PLINTH, Role.FOUNDATION, Role.CHIMNEY}) {
			if (stony(d, r)) {
				return of(d, r).crisp();
			}
		}
		return new Stone(d, null, FALLBACK_DRESSED, Noise.hash(d.seed(), 0x570F), StageKind.WALLS);
	}

	/** Footing stone: the foundation role if stony, else masonry. */
	public static Stone footing(Draft d) {
		return stony(d, Role.FOUNDATION) ? of(d, Role.FOUNDATION) : masonry(d);
	}

	/** A fixed material (or a mix of them, equal weights) as a stone, for the few parts a culture paints the same everywhere. */
	public static Stone fixed(Draft d, StageKind stage, String... materials) {
		List<Palette.Entry> entries = new java.util.ArrayList<>();
		for (String m : materials) {
			entries.add(new Palette.Entry(Material.of(m), 1, 0, 0, 0, 0));
		}
		return new Stone(d, null, new Palette(entries), Noise.hash(d.seed(), materials.length * 31L + materials[0].hashCode()), stage);
	}

	/** Whether this stone is a style role (not the fallback). */
	public boolean isRole() {
		return this.mat != null;
	}

	public boolean has(Form form) {
		return this.palette.has(form);
	}

	/** The main block in a form, or null. */
	public @Nullable Block main(Form form) {
		if (this.mat != null) {
			return this.mat.main(form);
		}
		return this.palette.main().block(form);
	}

	/** The block at a position in a form (full block where the form is missing). */
	public Block block(Form form, int x, int y, int z) {
		if (this.single) {
			Block b = this.palette.main().block(form);
			if (b != null) {
				return b;
			}
		}
		if (this.mat != null) {
			return this.mat.block(form, x, y, z);
		}
		Block b = this.palette.pick(form, this.seed, x, y, z, lowness(x, y, z), 0, 0, 0, 0.6);
		if (b == null) {
			b = this.palette.pick(Form.FULL, this.seed, x, y, z, lowness(x, y, z), 0, 0, 0, 0.6);
		}
		return b != null ? b : Blocks.STONE_BRICKS;
	}

	private double lowness(int x, int y, int z) {
		int above = y - this.draft.site().ground(x, z);
		return Math.clamp(1.0 - above / 2.5, 0, 1);
	}

	private Paint paint(Form form, java.util.function.UnaryOperator<BlockState> props) {
		StageKind st = this.stage;
		return new Paint() {
			@Override
			public BlockState at(int x, int y, int z) {
				return props.apply(block(form, x, y, z).defaultBlockState());
			}

			@Override
			public StageKind stage() {
				return st;
			}
		};
	}

	public Paint full() {
		return this.mat != null && !this.single ? this.mat.full() : paint(Form.FULL, s -> s);
	}

	public Paint stairs(Direction facing, Half half) {
		if (this.mat != null && !this.single) {
			return this.mat.stairs(facing, half);
		}
		return paint(Form.STAIRS, s -> s.trySetValue(BlockStateProperties.HORIZONTAL_FACING, facing).trySetValue(BlockStateProperties.HALF, half));
	}

	public Paint slab(SlabType type) {
		if (this.mat != null && !this.single) {
			return this.mat.slab(type);
		}
		return paint(Form.SLAB, s -> s.trySetValue(BlockStateProperties.SLAB_TYPE, type));
	}

	public Paint wall() {
		return this.mat != null && !this.single ? this.mat.wall() : paint(Form.WALL, s -> s);
	}

	/** A wall post if the stone has one, else a full block. */
	public Paint wallOrFull() {
		return has(Form.WALL) ? wall() : full();
	}

	/** Stairs if the stone has them, else a slab of the same half, else the full block. */
	public Paint stairsOrSlab(Direction facing, Half half) {
		if (has(Form.STAIRS)) {
			return stairs(facing, half);
		}
		if (has(Form.SLAB)) {
			return slab(half == Half.TOP ? SlabType.TOP : SlabType.BOTTOM);
		}
		return full();
	}

	/** A slab if the stone has one, else nothing (null paint answers leave the cell alone). */
	public Paint slabOrNothing(SlabType type) {
		return has(Form.SLAB) ? slab(type) : (x, y, z) -> null;
	}
}
