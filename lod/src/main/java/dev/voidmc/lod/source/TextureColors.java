package dev.voidmc.lod.source;

import dev.voidmc.lod.core.Colors;
import dev.voidmc.lod.mixin.SpriteContentsAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/**
 * What a block face looks like from a long way off: the average of the textures its model actually draws
 * on that face, with the block's own tint for a given biome. Read from the loaded models and atlas, so a
 * resource pack's grass, or a mod's leaves, colour the far terrain the way they colour the chunks.
 *
 * <p>A face can be several layers (grass block sides are dirt with a tinted fringe on top): the first is the
 * base, and each later one covers it by its own opacity. Averages are taken in linear light, the way
 * mipmaps blend a texture down to one pixel.
 *
 * <p>Reads sprite pixels, so it is used on the client thread only, when a terrain source is made; the
 * results are plain colours the workers can share.
 */
public final class TextureColors {
	private record Layer(float r, float g, float b, float coverage, int tintIndex) {
	}

	private final Map<TextureAtlasSprite, Layer> sprites = new HashMap<>();
	private final Map<BlockState, List<Layer>[]> faces = new HashMap<>();
	private final BiomeTint tint = new BiomeTint();
	private final BlockColors blockColors = Minecraft.getInstance().getBlockColors();

	/** The top face of {@code state} in {@code biome}, 0xRRGGBB. */
	public int top(BlockState state, Biome biome) {
		return this.resolve(state, 0, biome);
	}

	/** A side face of {@code state} in {@code biome}, 0xRRGGBB. */
	public int side(BlockState state, Biome biome) {
		return this.resolve(state, 1, biome);
	}

	private int resolve(BlockState state, int which, Biome biome) {
		List<Layer> layers = this.faces.computeIfAbsent(state, this::layers)[which];
		if (layers.isEmpty()) {
			return 0x7F7F7F;
		}
		float r = 0, g = 0, b = 0;
		for (int i = 0; i < layers.size(); i++) {
			Layer l = layers.get(i);
			float lr = l.r, lg = l.g, lb = l.b;
			if (l.tintIndex >= 0) {
				int c = this.tint(state, l.tintIndex, biome);
				lr *= linear(Colors.r(c));
				lg *= linear(Colors.g(c));
				lb *= linear(Colors.b(c));
			}
			float a = i == 0 ? 1 : l.coverage;
			r += (lr - r) * a;
			g += (lg - g) * a;
			b += (lb - b) * a;
		}
		return Colors.rgb(srgb(r), srgb(g), srgb(b));
	}

	private int tint(BlockState state, int index, Biome biome) {
		BlockTintSource source = this.blockColors.getTintSource(state, index);
		if (source == null) {
			return 0xFFFFFF;
		}
		this.tint.biome = biome;
		// vanilla's own tint, whatever a mod does to the chunks' (the season goes on per tile: LodApi.setSeasons)
		dev.voidmc.lod.api.LodApi.baking(true);
		try {
			return source.colorInWorld(state, this.tint, BlockPos.ZERO) & 0xFFFFFF;
		} catch (RuntimeException e) {
			return source.color(state) & 0xFFFFFF;
		} finally {
			dev.voidmc.lod.api.LodApi.baking(false);
		}
	}

	@SuppressWarnings("unchecked")
	private List<Layer>[] layers(BlockState state) {
		List<Layer>[] out = new List[]{new ArrayList<Layer>(), new ArrayList<Layer>()};
		BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
		List<BlockStateModelPart> parts = new ArrayList<>();
		model.collectParts(RandomSource.create(42), parts);
		for (BlockStateModelPart part : parts) {
			this.collect(part, Direction.UP, out[0]);
			this.collect(part, Direction.NORTH, out[1]);
		}
		for (List<Layer> face : out) {
			if (face.isEmpty()) {
				// no full face that way (a cross model, a slab seen from the side): the particle texture
				face.add(this.average(model.particleMaterial().sprite(), -1));
			}
		}
		return out;
	}

	private void collect(BlockStateModelPart part, Direction direction, List<Layer> out) {
		for (BakedQuad quad : part.getQuads(direction)) {
			out.add(this.average(quad.materialInfo().sprite(), quad.materialInfo().tintIndex()));
		}
	}

	private Layer average(TextureAtlasSprite sprite, int tintIndex) {
		Layer l = this.sprites.computeIfAbsent(sprite, TextureColors::measure);
		return tintIndex == l.tintIndex ? l : new Layer(l.r, l.g, l.b, l.coverage, tintIndex);
	}

	private static Layer measure(TextureAtlasSprite sprite) {
		SpriteContents contents = sprite.contents();
		var image = ((SpriteContentsAccessor) contents).voidLod$originalImage();
		int w = Math.min(contents.width(), image.getWidth());
		int h = Math.min(contents.height(), image.getHeight());
		double r = 0, g = 0, b = 0, alpha = 0;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int argb = image.getPixel(x, y);
				float a = (argb >>> 24) / 255.0F;
				if (a <= 0) {
					continue;
				}
				r += linear((argb >> 16) & 0xFF) * a;
				g += linear((argb >> 8) & 0xFF) * a;
				b += linear(argb & 0xFF) * a;
				alpha += a;
			}
		}
		if (alpha <= 0) {
			return new Layer(0.2F, 0.2F, 0.2F, 0, -1);
		}
		return new Layer((float) (r / alpha), (float) (g / alpha), (float) (b / alpha), (float) (alpha / (w * h)), -1);
	}

	/** An sRGB channel, 0..255, in linear light. */
	public static float linear(int c) {
		float v = c / 255.0F;
		return v <= 0.04045F ? v / 12.92F : (float) Math.pow((v + 0.055F) / 1.055F, 2.4);
	}

	/** A linear channel back in sRGB, 0..255. */
	public static float srgb(float l) {
		float v = l <= 0.0031308F ? l * 12.92F : (float) (1.055 * Math.pow(l, 1 / 2.4) - 0.055);
		return v * 255;
	}

	/** Answers a tint source's question about the colour at a position with one biome's colours. */
	private static final class BiomeTint implements BlockAndTintGetter {
		Biome biome;

		@Override
		public CardinalLighting cardinalLighting() {
			return CardinalLighting.DEFAULT;
		}

		@Override
		public LevelLightEngine getLightEngine() {
			return LevelLightEngine.EMPTY;
		}

		@Override
		public int getBlockTint(BlockPos pos, ColorResolver color) {
			return color.getColor(this.biome, pos.getX(), pos.getZ());
		}

		@Override
		public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
			return null;
		}

		@Override
		public BlockState getBlockState(BlockPos pos) {
			return Blocks.AIR.defaultBlockState();
		}

		@Override
		public FluidState getFluidState(BlockPos pos) {
			return Fluids.EMPTY.defaultFluidState();
		}

		@Override
		public int getHeight() {
			return 384;
		}

		@Override
		public int getMinY() {
			return -64;
		}
	}
}
