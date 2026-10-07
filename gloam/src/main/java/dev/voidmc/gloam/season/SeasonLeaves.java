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
 * The tints of the leaves vanilla colours the same everywhere: birch turns bright gold in autumn (tree by tree, by the
 * same four palette variants and 12-block hash as the broadleaf crowns, {@link SeasonResolvers}), spruce keeps its
 * needles and darkens a little in the cold. Oak and dark oak take their biome's foliage colour, which the resolvers turn.
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
		BlockColorRegistry.register(List.of(new Constant(BlockTintSources.constant(0xFF000000 | BIRCH), BIRCH, true)), Blocks.BIRCH_LEAVES);
		BlockColorRegistry.register(List.of(new Constant(BlockTintSources.constant(0xFF000000 | SPRUCE), SPRUCE, false)), Blocks.SPRUCE_LEAVES);
	}

	/** Whether {@code block}'s tint is one of these, so its colour is already the tree's own. */
	static boolean seasonal(Block block) {
		return block == Blocks.BIRCH_LEAVES || block == Blocks.SPRUCE_LEAVES;
	}

	/** Birch (bright gold in autumn, tree by tree) and spruce (green all year): one colour everywhere in vanilla. */
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
			int c = this.birch ? colours.birch(this.base, TEMPERATE, WET, Hash.variant(pos.getX(), pos.getZ())) : colours.conifer(this.base, TEMPERATE, WET);
			return 0xFF000000 | c;
		}

		@Override
		public Set<Property<?>> relevantProperties() {
			return this.vanilla.relevantProperties();
		}
	}
}
