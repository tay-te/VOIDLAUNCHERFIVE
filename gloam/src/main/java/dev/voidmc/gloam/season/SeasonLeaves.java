package dev.voidmc.gloam.season;

import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * The tints of the temperate trees' leaves, tree by tree. Oak and dark oak take their biome's foliage colour, which the
 * season already turns ({@link SeasonResolvers}); here each tree then gets its own hue, depth and brightness about it,
 * from the crown-sized cell of a jittered grid it stands in ({@link Hash#cell}), so an autumn wood is red, orange, gold
 * and late yellow-green trees side by side, not one flat colour. Birch and spruce wear one colour everywhere in vanilla:
 * birch turns bright yellow, spruce keeps its needles and darkens a little in the cold.
 *
 * <p>Registered in place of vanilla's (through Fabric's registry, which Sodium follows: these blocks are then coloured
 * per block, as vanilla colours them, rather than by Sodium's per-vertex blend). Item colours and VOID LOD's texture
 * colours ({@code LodApi.bakingColours}) stay vanilla's.
 */
final class SeasonLeaves {
	/** Vanilla's constant leaf colours (BlockColors.createDefault). */
	static final int SPRUCE = 0x619961, BIRCH = 0x80A755;
	/** How wide one crown's cell is, in blocks: a broadleaf crown is five to seven across. */
	static final int CROWN = 6;
	/** The climate the constant-coloured trees are taken to grow in (their tint does not see the biome). */
	private static final float TEMPERATE = 0.6F, WET = 0.6F;

	private SeasonLeaves() {
	}

	static void register() {
		BlockTintSource foliage = BlockTintSources.foliage();
		BlockColorRegistry.register(List.of(new Broadleaf(foliage)), Blocks.OAK_LEAVES, Blocks.DARK_OAK_LEAVES);
		BlockColorRegistry.register(List.of(new Constant(BlockTintSources.constant(0xFF000000 | BIRCH), BIRCH, true)), Blocks.BIRCH_LEAVES);
		BlockColorRegistry.register(List.of(new Constant(BlockTintSources.constant(0xFF000000 | SPRUCE), SPRUCE, false)), Blocks.SPRUCE_LEAVES);
	}

	/** Whether {@code block}'s tint is one of these, so its colour is already the tree's own. */
	static boolean seasonal(Block block) {
		return block == Blocks.OAK_LEAVES || block == Blocks.DARK_OAK_LEAVES || block == Blocks.BIRCH_LEAVES || block == Blocks.SPRUCE_LEAVES;
	}

	/** One tree's own colour, from its biome's colour this time of year and the cell of the crown at {@code pos}. */
	static int tree(SeasonColours colours, int seasoned, BlockPos pos) {
		float[] v = variant(Hash.variant(pos.getX(), pos.getZ()));
		// within the far land's variant, each crown a little its own
		int h = Hash.cell(0x7EE5, pos.getX(), pos.getZ(), CROWN);
		return colours.tree(seasoned & 0xFFFFFF, v[0] + (Hash.unit(h, 0) - 0.5F) * 0.08F, v[1] + (Hash.unit(h, 1) - 0.5F) * 0.2F,
			v[2] + (Hash.unit(h >>> 4, 2) - 0.5F) * 0.2F);
	}

	/**
	 * The four kinds of tree a wood's crowns come in ({@link SeasonColours#tree}'s numbers): in autumn red, orange, gold, and
	 * one late and still yellow-green. Near and far take them by the same hash ({@link Hash#variant}, VOID LOD's
	 * {@code seasonVariant}), so the far crowns match the near tree for tree.
	 */
	private static final float[][] VARIANTS = {{0.18F, 0.55F, 0.45F}, {0.5F, 0.6F, 0.55F}, {0.78F, 0.45F, 0.62F}, {0.95F, 0.5F, 0.55F}};

	static float[] variant(int v) {
		return VARIANTS[v & 3];
	}

	/** Oak and dark oak: the biome's (seasoned) foliage colour, made the tree's own. */
	private record Broadleaf(BlockTintSource vanilla) implements BlockTintSource {
		@Override
		public int color(BlockState state) {
			return this.vanilla.color(state);
		}

		@Override
		public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
			int c = this.vanilla.colorInWorld(state, level, pos);
			SeasonColours colours = Seasons.colours();
			if (!colours.on() || Seasons.lodBaking()) {
				return c;
			}
			return c & 0xFF000000 | tree(colours, colours.bareTwigs(c & 0xFFFFFF), pos);
		}

		@Override
		public Set<Property<?>> relevantProperties() {
			return this.vanilla.relevantProperties();
		}
	}

	/** Birch (bright yellow in autumn) and spruce (green all year): one colour everywhere, then the tree's own. */
	private record Constant(BlockTintSource vanilla, int base, boolean birch) implements BlockTintSource {
		@Override
		public int color(BlockState state) {
			return this.vanilla.color(state);
		}

		@Override
		public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
			SeasonColours colours = Seasons.colours();
			if (!colours.on() || Seasons.lodBaking()) {
				return this.vanilla.colorInWorld(state, level, pos);
			}
			int c = this.birch ? colours.birch(this.base, TEMPERATE, WET) : colours.conifer(this.base, TEMPERATE, WET);
			return 0xFF000000 | (this.birch ? tree(colours, c, pos) : c);
		}

		@Override
		public Set<Property<?>> relevantProperties() {
			return this.vanilla.relevantProperties();
		}
	}
}
