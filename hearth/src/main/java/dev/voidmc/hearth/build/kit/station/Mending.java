package dev.voidmc.hearth.build.kit.station;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

/**
 * The look of a home kept up by hand on no money (docs/LIVING_WORLD.md §8.1, the poor): walls mended in patches where
 * the daub fell out (packed mud, a board nailed over a hole, a few stones pushed in at the foot), a roof patched with
 * whatever was to hand. Patches come in clusters of one material each (a patch is a repair, not a speckle), keep clear
 * of the corners and the frame, and stay inside the culture's own materials or plain natural ones (mud, boards, rubble),
 * so a mended hovel reads poor but cared for, never ugly.
 */
public final class Mending {
	private Mending() {
	}

	/**
	 * The patches a wall of this construction is mended with: rubble and mud for daub and render, mud and mud brick for
	 * adobe, boards for timber, stone for stone.
	 */
	public static Paint[] patchesFor(Draft d, WallKind kind) {
		Paint mud = Paint.of(Blocks.PACKED_MUD);
		Paint mudBrick = Paint.of(Blocks.MUD_BRICKS);
		Paint boards = d.mat(Role.DOOR).full();
		Paint rubble = d.mat(Role.FOUNDATION).full();
		return switch (kind) {
			case TIMBER_FRAME, PLASTER -> new Paint[]{mud, boards, mud};
			case ADOBE -> new Paint[]{mud, mudBrick};
			case STONE -> new Paint[]{rubble, mud};
			case BRICK -> new Paint[]{mudBrick, rubble};
			case LOG -> new Paint[]{d.mat(Role.FLOOR).full(), boards};
			case BAMBOO, PLANK -> new Paint[]{d.mat(Role.FLOOR).full(), boards};
		};
	}

	/**
	 * A wall paint mended in patches: {@code base} where the wall is sound, one of {@code patches} (the same one through
	 * a whole patch) over about {@code share} of it. The patches are clusters a few blocks across on the wall's face,
	 * fixed by the building's seed and {@code salt}.
	 */
	public static Paint mended(Draft d, Paint base, long salt, double share, Paint... patches) {
		if (patches.length == 0 || share <= 0) {
			return base;
		}
		long seed = Noise.hash(d.seed(), 0x4E9D + salt);
		double cut = 1 - share;
		return new Paint() {
			@Override
			public @Nullable BlockState at(int x, int y, int z) {
				if (patched(seed, x, y, z, cut)) {
					int k = (int) Math.min(patches.length - 1, Noise.value(seed ^ 0x51, x * 0.18, y * 0.18, z * 0.18) * patches.length);
					BlockState s = patches[k].at(x, y, z);
					if (s != null) {
						return s;
					}
				}
				return base.at(x, y, z);
			}

			@Override
			public @Nullable StageKind stage() {
				return base.stage();
			}
		};
	}

	/** Whether (x, y, z) falls in a patch of the clustered field cut at {@code cut}. */
	static boolean patched(long seed, int x, int y, int z, double cut) {
		// value noise on a lattice a little over a block wide, so a patch is two to four blocks across on either wall plane
		double n = Noise.value(seed, x * 0.55 + 0.31, y * 0.7 + 0.17, z * 0.55 + 0.43);
		return n > cut;
	}

	/**
	 * Mends a rasterised roof in patches: over about {@code share} of its surface, clusters of shingles (stairs, slabs,
	 * blocks) are relaid in another material of the same shape: the style's second roof material where it has one, or
	 * the thatch's, else moss on full blocks. The ridge and the eave row are left as they were (they are what keeps the
	 * roof's line). Returns how many cells were relaid.
	 */
	public static int roof(Draft d, Roofs.Roof roof, long salt, double share) {
		List<Block> alternates = new ArrayList<>();
		var roofEntries = d.mat(Role.ROOF).palette().entries();
		Block main = d.mat(Role.ROOF).main(dev.voidmc.hearth.build.style.Form.STAIRS);
		for (var e : d.style().palette(Role.ROOF, d.tier(), d.variant()).entries()) {
			Block b = e.material().block(dev.voidmc.hearth.build.style.Form.STAIRS);
			if (b != null && b != main && !alternates.contains(b)) {
				alternates.add(b);
			}
		}
		if (roofEntries.isEmpty() && alternates.isEmpty()) {
			return 0;
		}
		long seed = Noise.hash(d.seed(), 0x2F0F + salt);
		double cut = 1 - share;
		int n = 0;
		for (int z = roof.z0; z <= roof.z1; z++) {
			for (int x = roof.x0; x <= roof.x1; x++) {
				int y = roof.surfaceY(x, z);
				if (y == Integer.MIN_VALUE || y >= roof.ridgeY || y <= roof.y0 || !patched(seed, x, y, z, cut)) {
					continue;
				}
				BlockState s = d.get(x, y, z);
				if (s == null) {
					continue;
				}
				BlockState r = relay(s, alternates);
				if (r != null) {
					d.restate(x, y, z, r);
					n++;
				}
			}
		}
		return n;
	}

	/** The same shape in the first alternate material that can be cut so, or moss for a full block; null if nothing fits. */
	private static @Nullable BlockState relay(BlockState s, List<Block> stairs) {
		Block b = s.getBlock();
		if (b instanceof StairBlock) {
			for (Block alt : stairs) {
				if (alt instanceof StairBlock && alt != b) {
					return alt.defaultBlockState().setValue(StairBlock.FACING, s.getValue(StairBlock.FACING)).setValue(StairBlock.HALF, s.getValue(StairBlock.HALF));
				}
			}
			return null;
		}
		if (b instanceof SlabBlock) {
			for (Block alt : stairs) {
				Block slab = slabOf(alt);
				if (slab != null && slab != b) {
					return slab.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, s.getValue(BlockStateProperties.SLAB_TYPE));
				}
			}
			return null;
		}
		if (s.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO)) {
			return Blocks.MOSS_BLOCK.defaultBlockState();
		}
		return null;
	}

	private static @Nullable Block slabOf(Block stairs) {
		var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(stairs);
		String path = key.getPath();
		if (!path.endsWith("_stairs")) {
			return null;
		}
		var id = net.minecraft.resources.Identifier.fromNamespaceAndPath(key.getNamespace(), path.substring(0, path.length() - "_stairs".length()) + "_slab");
		return net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(id) ? net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(id) : null;
	}
}
