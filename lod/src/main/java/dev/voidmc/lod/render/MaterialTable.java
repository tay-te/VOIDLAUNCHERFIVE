package dev.voidmc.lod.render;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.voidmc.lod.core.Colors;
import dev.voidmc.lod.mixin.SpriteContentsAccessor;
import dev.voidmc.lod.source.Materials;
import dev.voidmc.lod.source.TextureColors;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.system.MemoryUtil;

/**
 * The block textures behind the world's material ids ({@link Materials}), for the shader: per id, where its face's
 * sprites lie in the block atlas and how their colours relate to the face's average colour.
 *
 * <p>A textured face is its vertex colour times its texture over the texture's own average. The vertex colour stays
 * the truth (the biome's tint, snow, shading, occlusion, variation), and the texture only adds the pattern, which
 * averages out to one: far away, where the mipmaps have melted a block into its average, a face is exactly its
 * vertex colour again. The average is the one the mipmaps converge to, the linear mean of every texel, taken in
 * the atlas's own encoding as the shader multiplies, so a tint cancels out exactly. A face drawn in two layers (a
 * grass block's side: dirt, then a fringe the biome tints) samples both, the overlay cut out over the base, with
 * the block's default tint: the biome's own tint is in the vertex colour, as a whole.
 *
 * <p>Two RGBA32UI texels per id, one per layer, so a plain face costs the shader a single read: the sprite's
 * rectangle in atlas texels (corner, then size, sixteen bits each), its colour scale (its tint over the face's
 * average, three 4.12 fixed-point channels) and the face's flags (1: the block turns its top at random, as vanilla's
 * rotated variants of grass, sand and dirt do; 2: an overlay follows). An id it cannot resolve stays zero, and is
 * drawn untextured.
 *
 * <p>Ids are handed out once and never reused, so the table only grows: new ids are resolved on the render thread
 * as they appear, and every id again when the atlas or the models are reloaded, or the world changes.
 */
final class MaterialTable implements AutoCloseable {
	private static final int ENTRY_BYTES = 32;

	private final GpuDevice device;
	private GpuBuffer buffer;
	private int capacity;
	private int resolved;
	private Materials materials;
	private GpuTextureView atlas;
	private BlockStateModelSet models;
	/** Per sprite: its texels' linear mean, their mean weighted by opacity, and its opacity. */
	private final Map<TextureAtlasSprite, float[]> averages = new HashMap<>();

	MaterialTable(GpuDevice device) {
		this.device = device;
		this.allocate(256);
	}

	GpuBuffer buffer() {
		return this.buffer;
	}

	/** The block atlas the table points into. */
	GpuTextureView atlas() {
		return this.atlas;
	}

	/** Brings the table up to date with {@code materials} (null: no world). Outside a render pass. */
	void update(Materials materials, CommandEncoder encoder) {
		Minecraft mc = Minecraft.getInstance();
		GpuTextureView atlas = mc.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
		BlockStateModelSet models = mc.getModelManager().getBlockStateModelSet();
		if (materials != this.materials || atlas != this.atlas || models != this.models) {
			// a new world, or reloaded resources: every sprite may have moved
			this.materials = materials;
			this.atlas = atlas;
			this.models = models;
			this.averages.clear();
			this.resolved = 1;
		}
		int size = materials == null ? 1 : materials.size();
		if (size <= this.resolved) {
			return;
		}
		if (size > this.capacity) {
			this.buffer.close();
			this.allocate(Math.max(size, this.capacity * 2));
			this.resolved = 1;
		}
		int from = this.resolved;
		ByteBuffer data = MemoryUtil.memAlloc((size - from) * ENTRY_BYTES);
		for (int id = from; id < size; id++) {
			this.entry(materials.face(id), data);
		}
		data.flip();
		encoder.writeToBuffer(this.buffer.slice((long) from * ENTRY_BYTES, data.remaining()), data);
		MemoryUtil.memFree(data);
		this.resolved = size;
	}

	private void allocate(int capacity) {
		this.capacity = capacity;
		ByteBuffer zeros = MemoryUtil.memCalloc(capacity * ENTRY_BYTES);
		this.buffer = this.device.createBuffer(() -> "VOID LOD materials", GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER | GpuBuffer.USAGE_COPY_DST, zeros);
		MemoryUtil.memFree(zeros);
	}

	/** One id's texels: the face's top or side as the model draws it, base layer first. */
	private void entry(Materials.Face face, ByteBuffer out) {
		int start = out.position();
		for (int i = 0; i < ENTRY_BYTES / 4; i++) {
			out.putInt(start + i * 4, 0);
		}
		out.position(start + ENTRY_BYTES);
		if (face == null) {
			return;
		}
		BlockState state = face.state();
		BlockStateModel model = this.models.get(state);
		List<BlockStateModelPart> parts = new ArrayList<>();
		model.collectParts(RandomSource.create(42), parts);
		TextureAtlasSprite base = null, overlay = null;
		int baseTint = -1, overlayTint = -1;
		for (BlockStateModelPart part : parts) {
			for (BakedQuad quad : part.getQuads(face.top() ? Direction.UP : Direction.NORTH)) {
				TextureAtlasSprite sprite = quad.materialInfo().sprite();
				if (base == null) {
					base = sprite;
					baseTint = quad.materialInfo().tintIndex();
				} else if (overlay == null && sprite != base) {
					overlay = sprite;
					overlayTint = quad.materialInfo().tintIndex();
				}
			}
		}
		if (base == null) {
			// no full face that way (a snow layer's top, a cross model): the particle texture, as TextureColors does
			base = model.particleMaterial().sprite();
		}
		if (!TextureAtlas.LOCATION_BLOCKS.equals(base.atlasLocation())) {
			return;
		}
		if (overlay != null && !TextureAtlas.LOCATION_BLOCKS.equals(overlay.atlasLocation())) {
			overlay = null;
		}
		// does the block turn its top at random, as grass, sand and dirt do? Then the shader turns it block by block.
		boolean turns = false;
		for (int seed = 0; face.top() && seed < 8 && !turns; seed++) {
			List<BlockStateModelPart> other = new ArrayList<>();
			model.collectParts(RandomSource.create(seed), other);
			turns = !other.equals(parts);
		}

		// The face's average as the shader composes it, texel times tint in the atlas's own encoding: the base, then
		// the overlay over it by its opacity. Each texture's own average is what its mipmaps converge to.
		float[] b = this.average(base);
		int bt = tint(state, baseTint);
		float r = TextureColors.srgb(b[0]) * Colors.r(bt);
		float g = TextureColors.srgb(b[1]) * Colors.g(bt);
		float bl = TextureColors.srgb(b[2]) * Colors.b(bt);
		int ot = 0xFFFFFF;
		if (overlay != null) {
			float[] o = this.average(overlay);
			ot = tint(state, overlayTint);
			r += (TextureColors.srgb(o[3]) * Colors.r(ot) - r) * o[6];
			g += (TextureColors.srgb(o[4]) * Colors.g(ot) - g) * o[6];
			bl += (TextureColors.srgb(o[5]) * Colors.b(ot) - bl) * o[6];
		}
		// in 0..1, never quite black
		float ar = Math.max(r / 65025.0F, 1 / 255.0F);
		float ag = Math.max(g / 65025.0F, 1 / 255.0F);
		float ab = Math.max(bl / 65025.0F, 1 / 255.0F);

		out.position(start);
		// leaves: the gaps in their texture show the leaves behind, in their shade (vanilla draws them cut out)
		boolean leaves = state.is(net.minecraft.tags.BlockTags.LEAVES);
		this.layer(base, bt, ar, ag, ab, (turns ? 1 : 0) | (overlay != null ? 2 : 0) | (leaves ? 4 : 0), out);
		if (overlay != null) {
			this.layer(overlay, ot, ar, ag, ab, 0, out);
		}
		out.position(start + ENTRY_BYTES);
	}

	/** One layer's texel: where its sprite is, and its tint over the face's average {@code ar, ag, ab}. */
	private void layer(TextureAtlasSprite sprite, int tint, float ar, float ag, float ab, int flags, ByteBuffer out) {
		int x = Math.round(sprite.getU0() * this.atlas.getWidth(0));
		int y = Math.round(sprite.getV0() * this.atlas.getHeight(0));
		out.putInt(x | y << 16).putInt(sprite.contents().width() | sprite.contents().height() << 16);
		out.putInt(fixed(Colors.r(tint) / 255.0F / ar) | fixed(Colors.g(tint) / 255.0F / ag) << 16);
		out.putInt(fixed(Colors.b(tint) / 255.0F / ab) | flags << 16);
	}

	private static int fixed(float scale) {
		return Math.min(0xFFFF, Math.round(scale * 4096));
	}

	/** The block's default tint for a layer (a grass block's is the plains' green), or white for an untinted one. */
	private static int tint(BlockState state, int tintIndex) {
		if (tintIndex < 0) {
			return 0xFFFFFF;
		}
		BlockTintSource source = Minecraft.getInstance().getBlockColors().getTintSource(state, tintIndex);
		return source == null ? 0xFFFFFF : source.color(state) & 0xFFFFFF;
	}

	private float[] average(TextureAtlasSprite sprite) {
		return this.averages.computeIfAbsent(sprite, MaterialTable::measure);
	}

	private static float[] measure(TextureAtlasSprite sprite) {
		SpriteContents contents = sprite.contents();
		var image = ((SpriteContentsAccessor) contents).voidLod$originalImage();
		int w = Math.min(contents.width(), image.getWidth());
		int h = Math.min(contents.height(), image.getHeight());
		double r = 0, g = 0, b = 0, wr = 0, wg = 0, wb = 0, alpha = 0;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int argb = image.getPixel(x, y);
				float lr = TextureColors.linear((argb >> 16) & 0xFF);
				float lg = TextureColors.linear((argb >> 8) & 0xFF);
				float lb = TextureColors.linear(argb & 0xFF);
				// every texel counts toward what the mipmaps converge to, transparent ones too (their colour is
				// their opaque neighbours', filled in for mipmapping)
				r += lr;
				g += lg;
				b += lb;
				float a = (argb >>> 24) / 255.0F;
				wr += lr * a;
				wg += lg * a;
				wb += lb * a;
				alpha += a;
			}
		}
		int n = Math.max(1, w * h);
		double wa = Math.max(alpha, 1e-6);
		return new float[]{(float) (r / n), (float) (g / n), (float) (b / n), (float) (wr / wa), (float) (wg / wa), (float) (wb / wa),
			(float) (alpha / n)};
	}

	@Override
	public void close() {
		this.buffer.close();
	}
}
