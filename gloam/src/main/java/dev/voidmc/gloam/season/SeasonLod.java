package dev.voidmc.gloam.season;

import dev.voidmc.gloam.compat.Lod;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The season on VOID LOD's far land, so it turns with the chunks and no seam shows where they meet. VOID LOD bakes its
 * colours as vanilla tints them ({@code LodApi.bakingColours}: the resolvers give vanilla's then) and asks this, tile
 * by tile, what each grass and leaf colour looks like now ({@code LodApi.Seasons}). A far colour is a texture's average
 * times the biome's tint, so the season goes on as the tint's change: the colour times (season's tint / vanilla's tint),
 * per channel in linear light, which is exactly what the chunks show for the same texture. Only the green in a colour is
 * turned (snow, sand and rock stay as they are; a mixed colour turns by how green it is). The ratios are worked out once
 * per biome and season step.
 *
 * <p>Touches VOID LOD's classes only when it is loaded ({@link Lod#LOADED}).
 */
final class SeasonLod {
	private SeasonLod() {
	}

	/** Hands VOID LOD the hook (once) and asks it to rebuild its tiles in the current colours. Render thread. */
	static void recolour() {
		if (Lod.LOADED) {
			Bridge.recolour();
		}
	}

	/** Whether VOID LOD is working out block colours on this thread (so the tints give vanilla's). */
	static boolean baking() {
		return Lod.LOADED && Bridge.baking();
	}

	private static final class Bridge {
		private static boolean installed;

		static void recolour() {
			if (!installed) {
				installed = true;
				dev.voidmc.lod.api.LodApi.setSeasons(Hook.INSTANCE);
			} else {
				dev.voidmc.lod.api.LodApi.recolour();
			}
		}

		static boolean baking() {
			return dev.voidmc.lod.api.LodApi.bakingColours();
		}
	}

	/** What a biome's grass and leaves turn by this season step, as per-channel factors in linear light. */
	private record Ratios(SeasonColours colours, float[] grass, float[] broadleaf, float[] birch, float[] conifer) {
	}

	/** What a material is, as far as its leaves go. */
	private static final byte UNKNOWN = 0, BROAD = 1, BIRCH = 2, NEEDLES = 3, OTHER = 4, DRAWN = 5;

	private static final class Hook implements dev.voidmc.lod.api.LodApi.Seasons {
		static final Hook INSTANCE = new Hook();
		private static final float[] LINEAR = new float[256];

		static {
			for (int i = 0; i < 256; i++) {
				float v = i / 255.0F;
				LINEAR[i] = v <= 0.04045F ? v / 12.92F : (float) Math.pow((v + 0.055F) / 1.055F, 2.4);
			}
		}

		private volatile AtomicReferenceArray<Ratios> ratios = new AtomicReferenceArray<>(256);
		private volatile AtomicReferenceArray<Biome> biomes = new AtomicReferenceArray<>(256);
		private volatile byte[] leaves = new byte[1024];
		private Object level;

		@Override
		public int recolour(int kind, int rgb, int biome, int material, float conifer) {
			SeasonColours colours = Seasons.lodColours();
			if (!colours.on()) {
				return rgb;
			}
			boolean ground = kind == GROUND_TOP || kind == GROUND_SIDE;
			byte leaf = ground ? OTHER : this.leaf(material);
			if (leaf == DRAWN) {
				// a crown drawn in its autumn colours (maple, aspen, larch): green until autumn, as the chunks show it
				return SeasonalLeaves.drawnAutumn(rgb, SeasonalLeaves.summer(material(material)), colours);
			}
			float green = greenness(rgb);
			if (green <= 0 || !ground && leaf == OTHER) {
				return rgb;
			}
			Ratios r = this.ratios(biome, colours);
			if (ground) {
				// a grass block's side is mostly dirt, with the tinted fringe along its top
				return apply(rgb, r.grass, r.grass, 0, kind == GROUND_SIDE ? green * 0.5F : green);
			}
			float needles = conifer >= 0 ? conifer : leaf == NEEDLES ? 1 : 0;
			return apply(rgb, leaf == BIRCH ? r.birch : r.broadleaf, r.conifer, needles, green);
		}

		/** {@code rgb} times the factors {@code a} (blended toward {@code b} by {@code t}), applied by {@code amount}. */
		private static int apply(int rgb, float[] a, float[] b, float t, float amount) {
			int out = 0;
			for (int c = 0; c < 3; c++) {
				int shift = 16 - 8 * c;
				float f = a[c] + (b[c] - a[c]) * t;
				f = 1 + (f - 1) * amount;
				float v = LINEAR[rgb >> shift & 0xFF] * f;
				out |= srgb(v) << shift;
			}
			return out;
		}

		private static int srgb(float l) {
			l = Math.clamp(l, 0, 1);
			float v = l <= 0.0031308F ? l * 12.92F : (float) (1.055 * Math.pow(l, 1 / 2.4) - 0.055);
			return Math.round(v * 255);
		}

		/** How green a colour is, 0 (grey, white, brown, sand) .. 1. */
		private static float greenness(int rgb) {
			int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
			return Math.clamp((g - Math.max(r, b) - 4) / 20.0F, 0, 1);
		}

		private Ratios ratios(int id, SeasonColours colours) {
			this.check();
			AtomicReferenceArray<Ratios> all = this.ratios;
			if (id < 0 || id >= all.length()) {
				if (id >= 0 && id < 65536) {
					this.grow(id);
					all = this.ratios;
				} else {
					return this.compute(null, colours);
				}
			}
			Ratios r = all.get(id);
			if (r == null || r.colours != colours) {
				r = this.compute(this.biome(id), colours);
				all.set(id, r);
			}
			return r;
		}

		private Ratios compute(Biome biome, SeasonColours colours) {
			float t = biome == null ? 0.8F : biome.getBaseTemperature();
			float d = biome == null ? 0.4F : biome.climateSettings.downfall();
			int grass = biome == null ? 0x91BD59 : biome.getGrassColor(0, 0) & 0xFFFFFF;
			int foliage = biome == null ? 0x77AB2F : biome.getFoliageColor() & 0xFFFFFF;
			return new Ratios(colours, ratio(grass, colours.grass(grass, t, d, 0)), ratio(foliage, colours.foliage(foliage, t, d)),
				ratio(SeasonLeaves.BIRCH, colours.birch(SeasonLeaves.BIRCH, 0.6F, 0.6F)), ratio(SeasonLeaves.SPRUCE, colours.conifer(SeasonLeaves.SPRUCE, t, d)));
		}

		private static float[] ratio(int from, int to) {
			float[] f = new float[3];
			for (int c = 0; c < 3; c++) {
				int shift = 16 - 8 * c;
				f[c] = Math.min(4, LINEAR[to >> shift & 0xFF] / Math.max(0.004F, LINEAR[from >> shift & 0xFF]));
			}
			return f;
		}

		private Biome biome(int id) {
			Biome b = id < this.biomes.length() ? this.biomes.get(id) : null;
			if (b == null) {
				String name = dev.voidmc.lod.api.LodApi.biomeName(id);
				var level = Minecraft.getInstance().level;
				if (name == null || level == null) {
					return null;
				}
				b = level.registryAccess().lookupOrThrow(Registries.BIOME).getValue(Identifier.parse(name));
				if (b != null && id < this.biomes.length()) {
					this.biomes.set(id, b);
				}
			}
			return b;
		}

		private static BlockState material(int material) {
			return dev.voidmc.lod.api.LodApi.material(material);
		}

		private byte leaf(int material) {
			if (material <= 0) {
				return UNKNOWN;
			}
			byte[] known = this.leaves;
			if (material < known.length && known[material] != UNKNOWN) {
				return known[material];
			}
			BlockState state = dev.voidmc.lod.api.LodApi.material(material);
			byte kind;
			if (state == null) {
				kind = UNKNOWN;
			} else {
				String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
				if (state.is(Blocks.BIRCH_LEAVES)) {
					kind = BIRCH;
				} else if (SeasonalLeaves.summer(state) != 0) {
					kind = DRAWN;
				} else if (path.contains("spruce") || path.contains("pine") || path.contains("fir") || path.contains("redwood") || path.contains("cedar")) {
					kind = NEEDLES;
				} else if (path.contains("leaves") || path.contains("vine")) {
					kind = BROAD;
				} else {
					kind = OTHER;
				}
			}
			if (kind != UNKNOWN) {
				if (material >= known.length) {
					known = java.util.Arrays.copyOf(known, Math.max(material + 1, known.length * 2));
				}
				known[material] = kind;
				this.leaves = known;
			}
			return kind;
		}

		/** A new level: its biomes and materials are its own. */
		private void check() {
			Object level = Minecraft.getInstance().level;
			if (level != this.level) {
				synchronized (this) {
					if (level != this.level) {
						this.level = level;
						this.ratios = new AtomicReferenceArray<>(256);
						this.biomes = new AtomicReferenceArray<>(256);
						this.leaves = new byte[1024];
					}
				}
			}
		}

		private synchronized void grow(int id) {
			if (id < this.ratios.length()) {
				return;
			}
			int n = Math.max(id + 1, this.ratios.length() * 2);
			AtomicReferenceArray<Ratios> r = new AtomicReferenceArray<>(n);
			AtomicReferenceArray<Biome> b = new AtomicReferenceArray<>(n);
			for (int i = 0; i < this.ratios.length(); i++) {
				r.set(i, this.ratios.get(i));
				b.set(i, this.biomes.get(i));
			}
			this.biomes = b;
			this.ratios = r;
		}
	}
}
