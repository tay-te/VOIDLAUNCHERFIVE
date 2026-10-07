package dev.voidmc.gloam.season;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import dev.voidmc.gloam.Gloam;
import dev.voidmc.gloam.mixin.SpriteContentsAccessor;
import dev.voidmc.gloam.mixin.TextureAtlasSpriteAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The broadleaf crowns thinning in late autumn and bare through winter, without touching the world: the leaf sprites
 * themselves are redrawn in the block atlas, so every leaf block of those trees (in the chunks, through Sodium and a
 * shader pack alike, which all sample the same atlas) shows it at once, with no chunk to rebuild. More and more of a
 * sprite's leaf pixels are cut out as the crowns thin; in winter what is left is a few twigs: thin branching lines and a
 * scatter of buds, which over a crown read as the grey-brown haze of a bare wood with the sky through it. Mipmaps are
 * made again from the redrawn sprite, as vanilla makes them.
 *
 * <p>The tinted sprites (oak, dark oak, birch) take their colour from the season's tint ({@link SeasonColours}: twig
 * grey-brown in winter), so only their pixels are cut. Expanse's broadleaves carry their colour in the texture, so they
 * are recoloured here too: beech turns copper and willow yellow in autumn, while its maples, aspens and larches, drawn in
 * their autumn colours, are green until the autumn comes. Needle trees keep their needles (the larch, which sheds them,
 * is the exception).
 */
final class BareCrowns {
	/** How many steps the thinning and turning are drawn in: a sprite is redrawn only when a step changes. */
	private static final int STEPS = 24;
	/** The share of a sprite's leaf pixels left on a bare crown, besides its twigs. */
	private static final float BUDS = 0.04F;

	/**
	 * One kind of crown: its block, and for an untinted texture the green it shows in spring and summer (0: as drawn) and
	 * the colours it turns in autumn (null: as drawn).
	 */
	private record Kind(String block, boolean tinted, int summer, int[] autumn) {
	}

	private static final List<Kind> KINDS = List.of(
		new Kind("minecraft:oak_leaves", true, 0, null),
		new Kind("minecraft:dark_oak_leaves", true, 0, null),
		new Kind("minecraft:birch_leaves", true, 0, null),
		new Kind("minecraft:vine", true, 0, null),
		new Kind("expanse:beech_leaves", false, 0, new int[]{0xB8642A, 0xC98235, 0x9A4E28, 0xB07A30}),
		new Kind("expanse:willow_leaves", false, 0, new int[]{0xC8B048, 0xB09A3C, 0xBFA850}),
		new Kind("expanse:maple_leaves", false, 0x5F8C35, null),
		new Kind("expanse:orange_maple_leaves", false, 0x6B9038, null),
		new Kind("expanse:aspen_leaves", false, 0x86A848, null),
		new Kind("expanse:larch_leaves", false, 0x7FA850, null));

	/** One crown's sprite as it is now: the sprite it was drawn into, its own pixels, and the step it shows. */
	private static final class Crown {
		final Kind kind;
		final Block block;
		TextureAtlasSprite sprite;
		NativeImage original;
		boolean[] twigs;
		int shown = -1;

		Crown(Kind kind, Block block) {
			this.kind = kind;
			this.block = block;
		}
	}

	/** Sprites redrawn a tick at most. */
	private static final int PER_TICK = 2;

	private final List<Crown> crowns = new ArrayList<>();
	private int redrawn;
	/** Whether a crown still waits for its redraw (call {@link #update} again next tick). */
	boolean pending;
	private long uploads;
	private long uploadNanos;

	BareCrowns() {
		for (Kind kind : KINDS) {
			BuiltInRegistries.BLOCK.getOptional(Identifier.parse(kind.block())).filter(b -> b != Blocks.AIR)
				.ifPresent(block -> this.crowns.add(new Crown(kind, block)));
		}
	}

	/**
	 * Draws each crown's sprite for {@code colours} (or as loaded, with {@code on} false), where its step changed or its
	 * atlas was made again since (a resource reload). Render thread.
	 */
	void update(Minecraft mc, SeasonColours colours, boolean on) {
		this.redrawn = 0;
		this.pending = false;
		for (Crown crown : this.crowns) {
			try {
				this.update(mc, crown, colours, on);
			} catch (RuntimeException e) {
				Gloam.LOGGER.warn("VOID Gloam: could not redraw the {} crowns", crown.kind.block(), e);
				this.crowns.remove(crown);
				return;
			}
		}
	}

	private void update(Minecraft mc, Crown crown, SeasonColours colours, boolean on) {
		TextureAtlasSprite sprite = mc.getModelManager().getBlockStateModelSet().get(crown.block.defaultBlockState()).particleMaterial().sprite();
		if (sprite != crown.sprite) {
			// a new atlas: these are its pixels as loaded
			if (crown.original != null) {
				crown.original.close();
			}
			crown.sprite = sprite;
			crown.shown = -1;
			SpriteContents contents = sprite.contents();
			if (contents.isAnimated() || contents.transparency().isOpaque()) {
				// an animated sprite has frames this does not draw; an opaque one is drawn without cut-outs
				crown.original = null;
				return;
			}
			NativeImage image = ((SpriteContentsAccessor) contents).gloam$originalImage();
			crown.original = new NativeImage(contents.width(), contents.height(), false);
			for (int y = 0; y < contents.height(); y++) {
				for (int x = 0; x < contents.width(); x++) {
					crown.original.setPixel(x, y, image.getPixel(x, y));
				}
			}
			crown.twigs = twigs(contents.width(), contents.height(), crown.kind.block().hashCode());
		}
		if (crown.original == null) {
			return;
		}
		float bare = on ? colours.bare() : 0;
		float turned = on ? colours.turned() : 0;
		float green = on && crown.kind.summer() != 0 ? green(colours) : 0;
		int step = on ? 1 + Math.round(bare * STEPS) + (STEPS + 1) * (Math.round(turned * STEPS) + (STEPS + 1) * Math.round(green * STEPS)) : 0;
		if (step == crown.shown) {
			return;
		}
		if (this.redrawn++ >= PER_TICK) {
			// the rest next tick: a step's redraws are spread so none costs a frame more than a fraction of a millisecond
			this.pending = true;
			return;
		}
		long t0 = System.nanoTime();
		crown.shown = step;
		this.draw(mc, crown, bare, turned, green);
		this.uploads++;
		this.uploadNanos += System.nanoTime() - t0;
	}

	/** The spring and summer green of a crown drawn in its autumn colours (maple, aspen, larch), or 0 for any other block. */
	static int summer(net.minecraft.world.level.block.state.BlockState state) {
		if (state == null) {
			return 0;
		}
		String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
		for (Kind kind : KINDS) {
			if (kind.summer() != 0 && kind.block().equals(name)) {
				return kind.summer();
			}
		}
		return 0;
	}

	/** How much of their summer green the crowns drawn in autumn colours show: all spring and summer, going as they turn. */
	static float green(SeasonColours colours) {
		if (!colours.on()) {
			return 0;
		}
		float y = colours.year();
		return y < 0.5F ? 1 : y < 0.71F ? 1 - colours.turned() : 0;
	}

	/**
	 * A colour of a crown drawn in its autumn colours ({@code rgb}, as drawn) as it looks now: its {@code summer} green
	 * (at the drawn colour's brightness) until autumn, and grey-brown twigs as it goes bare.
	 */
	static int drawnAutumn(int rgb, int summer, SeasonColours colours) {
		if (summer == 0) {
			return rgb;
		}
		float lift = luma(rgb) / Math.max(1, luma(summer));
		int c = SeasonColours.mix(rgb, SeasonColours.scale(summer, lift), green(colours));
		return SeasonColours.mix(c, SeasonColours.scale(0x6E6157, Math.min(1.3F, lift)), colours.bare() * 0.8F);
	}

	private void draw(Minecraft mc, Crown crown, float bare, float turned, float green) {
		TextureAtlasSprite sprite = crown.sprite;
		SpriteContents contents = sprite.contents();
		int w = contents.width(), h = contents.height();
		NativeImage image = new NativeImage(w, h, false);
		Kind kind = crown.kind;
		int seed = kind.block().hashCode();
		float average = luma(crown.original);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int argb = crown.original.getPixel(x, y);
				boolean twig = crown.twigs[x + y * w];
				float r = Hash.unit(Hash.of(seed, x, y), 1);
				// thinning: the leaf pixels go in a fixed random order, so a sprite only ever loses more as the crown thins
				boolean leaf = (argb >>> 24) > 0 && r >= bare * (1 - BUDS);
				if (!leaf && !(twig && bare > 0.25F)) {
					image.setPixel(x, y, argb & 0x00FFFFFF);
					continue;
				}
				int rgb = argb & 0xFFFFFF;
				if (!kind.tinted()) {
					float lift = luma(rgb) / Math.max(1, average);
					if (green > 0) {
						rgb = SeasonColours.mix(rgb, SeasonColours.scale(kind.summer(), lift), green);
					}
					if (turned > 0 && kind.autumn() != null) {
						int[] palette = kind.autumn();
						int to = palette[(Hash.of(seed + 1, x >> 1, y >> 1) & 0x7FFFFFFF) % palette.length];
						rgb = SeasonColours.mix(rgb, SeasonColours.scale(to, lift), turned);
					}
				}
				if (!leaf) {
					// a twig: dark wood, grey under a tint, grey-brown where the texture carries the colour
					rgb = kind.tinted() ? 0xA8A8A8 : SeasonColours.scale(0x7A6B60, 0.85F + 0.3F * r);
				}
				image.setPixel(x, y, 0xFF000000 | rgb);
			}
		}
		this.upload(mc, sprite, image);
	}

	/** Writes {@code image} (taken over: closed here) into the sprite's place in its atlas, with its mipmaps and border. */
	private void upload(Minecraft mc, TextureAtlasSprite sprite, NativeImage image) {
		SpriteContents contents = sprite.contents();
		SpriteContentsAccessor raw = (SpriteContentsAccessor) contents;
		int levels = raw.gloam$byMipLevel().length;
		NativeImage[] mips = MipmapGenerator.generateMipLevels(contents.name(), new NativeImage[]{image}, levels - 1, raw.gloam$mipmapStrategy(),
			raw.gloam$alphaCutoffBias(), contents.transparency());
		AbstractTexture atlas = mc.getTextureManager().getTexture(sprite.atlasLocation());
		GpuTexture texture = atlas.getTexture();
		int padding = ((TextureAtlasSpriteAccessor) sprite).gloam$padding();
		var encoder = RenderSystem.getDevice().createCommandEncoder();
		try {
			for (int level = 0; level < mips.length && level < texture.getMipLevels(); level++) {
				NativeImage mip = mips[level];
				int pw = (contents.width() + 2 * padding) >> level, ph = (contents.height() + 2 * padding) >> level;
				int pad = Math.max(0, (pw - mip.getWidth()) / 2);
				try (NativeImage padded = new NativeImage(pw, ph, false)) {
					for (int y = 0; y < ph; y++) {
						for (int x = 0; x < pw; x++) {
							padded.setPixel(x, y, mip.getPixel(Math.clamp(x - pad, 0, mip.getWidth() - 1), Math.clamp(y - pad, 0, mip.getHeight() - 1)));
						}
					}
					encoder.writeToTexture(texture, padded, level, 0, sprite.getX() >> level, sprite.getY() >> level);
				}
			}
		} finally {
			for (NativeImage mip : mips) {
				mip.close();
			}
		}
	}

	/**
	 * A few thin twigs across a sprite: short crooked strokes, each in its own direction with a side shoot or two, that
	 * wrap at the edges and so make a scribbled network over a crown rather than lines repeating block after block.
	 */
	private static boolean[] twigs(int w, int h, int seed) {
		boolean[] out = new boolean[w * h];
		int strokes = Math.max(3, w / 4);
		for (int b = 0; b < strokes; b++) {
			int hash = Hash.of(seed, b, 17);
			float x = Hash.unit(hash, 0) * w, y = Hash.unit(hash, 1) * h;
			double angle = Hash.unit(hash, 2) * Math.PI * 2;
			int length = w / 3 + (int) (Hash.unit(hash, 3) * w / 3);
			for (int s = 0; s < length; s++) {
				out[Math.floorMod((int) x, w) + Math.floorMod((int) y, h) * w] = true;
				int step = Hash.of(seed + b, s, 31);
				angle += (Hash.unit(step, 0) - 0.5F) * 0.9F;
				x += (float) Math.cos(angle);
				y += (float) Math.sin(angle);
				if (Hash.unit(step, 1) < 0.15F) {
					// a side shoot, off at an angle
					double side = angle + (Hash.unit(step, 2) < 0.5F ? 0.9 : -0.9);
					float sx = x, sy = y;
					for (int t = 0; t < 2 + (int) (Hash.unit(step, 3) * 3); t++) {
						sx += (float) Math.cos(side);
						sy += (float) Math.sin(side);
						out[Math.floorMod((int) sx, w) + Math.floorMod((int) sy, h) * w] = true;
					}
				}
			}
		}
		return out;
	}

	private static float luma(int rgb) {
		return SeasonColours.luma(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF);
	}

	private static float luma(NativeImage image) {
		double sum = 0;
		int n = 0;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int argb = image.getPixel(x, y);
				if ((argb >>> 24) > 0) {
					sum += luma(argb & 0xFFFFFF);
					n++;
				}
			}
		}
		return n == 0 ? 128 : (float) (sum / n);
	}

	String stats() {
		return String.format(java.util.Locale.ROOT, "crowns %d sprites, %d redraws, %.2f ms each", this.crowns.size(), this.uploads,
			this.uploads == 0 ? 0 : this.uploadNanos / 1e6 / this.uploads);
	}
}
