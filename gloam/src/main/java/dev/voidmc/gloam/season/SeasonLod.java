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
 * colours as vanilla tints them ({@code LodApi.bakingColours}: the resolvers give vanilla's then), stamps each grass and
 * leaf face with a season class this hook picks ({@link Hook#classify}: temperate grass, broadleaf crowns, birch, beech
 * and so on), and turns each class by a small table at draw time ({@code LodApi.setSeasonTints}), which this sets from
 * the year every few ticks: nothing is rebuilt as the year turns, and switching Gloam off takes the table away at once.
 *
 * <p>A class's entry is worked out from a typical colour of its kind ({@code base}) and what {@link SeasonColours} makes
 * of it now ({@code seasoned}): the target hue is the seasoned colour, the brightness the ratio of their brightnesses,
 * so a far face, a texture's average times its biome's tint, comes out as that texture times the season's tint, which
 * is what the chunks show. The amount is how far the season has moved the colour, so in summer each biome keeps its
 * own greens. Crowns take one of four variants by where they stand ({@code LodApi.seasonVariant}, the same hash the
 * near crowns use: {@link SeasonLeaves#tree}), so a far autumn wood is red, orange, gold and late green, tree for tree as
 * near. Bare winter crowns are the season's twig grey-brown, lifted as the sky and land show through them.
 *
 * <p>Touches VOID LOD's classes only when it is loaded ({@link Lod#LOADED}).
 */
final class SeasonLod {
	/** The classes (0: never changes). Ground faces turn by their land's wetness and warmth; crowns by their kind. */
	static final int GRASS_DRY = 1, GRASS_WET = 2, GRASS_WARM = 3, SIDE_DRY = 4, SIDE_WET = 5, SIDE_WARM = 6, BROADLEAF = 7, BROADLEAF_WARM = 8,
		MIXED = 9, NEEDLES = 10, BIRCH = 11, BEECH = 12, WILLOW = 13, MAPLE = 14, ORANGE_MAPLE = 15, ASPEN = 16, LARCH = 17;
	private static final int CLASSES = 32, VARIANTS = 4;
	/**
	 * A bare crown seen from afar is a haze of twigs with the sky and the land through it: lighter than the summer's
	 * shaded green, as light again as the twigs' own colour, by this much.
	 */
	private static final float BARE_LIFT = 1.45F;

	private SeasonLod() {
	}

	/** Hands VOID LOD the hook (once per level); nothing without VOID LOD. Render thread. */
	static void install() {
		if (Lod.LOADED) {
			Bridge.install();
		}
	}

	/** Sets the far land's season table for {@code colours} (off: vanilla's colours); nothing without VOID LOD. */
	static void tints(SeasonColours colours) {
		if (Lod.LOADED) {
			Bridge.tints(colours.on() ? table(colours) : null);
		}
	}

	/** Whether VOID LOD is working out block colours on this thread (so the tints give vanilla's). */
	static boolean baking() {
		return Lod.LOADED && Bridge.baking();
	}

	private static final class Bridge {
		static void install() {
			dev.voidmc.lod.api.LodApi.setSeasons(Hook.INSTANCE);
		}

		static void tints(float[] table) {
			dev.voidmc.lod.api.LodApi.setSeasonTints(table);
		}

		static boolean baking() {
			return dev.voidmc.lod.api.LodApi.bakingColours();
		}
	}

	// ------------------------------------------------------------------------------------------------- the table

	/** The season table for {@code c}: per class and variant, (r, g, b, amount, brightness). */
	static float[] table(SeasonColours c) {
		float[] t = new float[CLASSES * VARIANTS * 5];
		grass(t, GRASS_DRY, SIDE_DRY, 0x91BD59, 0.8F, 0.4F, c);
		grass(t, GRASS_WET, SIDE_WET, 0x79C05A, 0.7F, 0.8F, c);
		grass(t, GRASS_WARM, SIDE_WARM, 0xA8B858, 1.2F, 0.35F, c);
		for (int v = 0; v < VARIANTS; v++) {
			float[] tree = SeasonLeaves.variant(v);
			int broad = c.foliage(0x77AB2F, 0.8F, 0.4F);
			entry(t, BROADLEAF, v, 0x77AB2F, crown(c, broad, tree), 1);
			entry(t, BROADLEAF_WARM, v, 0x7AB038, crown(c, c.foliage(0x7AB038, 1.2F, 0.4F), tree), 1);
			int needles = c.conifer(SeasonLeaves.SPRUCE, 0.25F, 0.8F);
			entry(t, NEEDLES, v, SeasonLeaves.SPRUCE, needles, 1);
			int mixedBase = SeasonColours.mix(0x77AB2F, SeasonLeaves.SPRUCE, 0.5F);
			entry(t, MIXED, v, mixedBase, SeasonColours.mix(crown(c, broad, tree), needles, 0.5F), 1);
			entry(t, BIRCH, v, SeasonLeaves.BIRCH, crown(c, c.birch(SeasonLeaves.BIRCH, 0.6F, 0.6F), tree), 1);
			// Expanse's untinted crowns: their own colour (a texture's average) turned as the near ones' textures are
			untinted(t, BEECH, v, 0x48852C, SeasonColours.mix(0x48852C, 0xB26A2E, c.turned()), c, tree);
			untinted(t, WILLOW, v, 0x89AE53, SeasonColours.mix(0x89AE53, 0xC0A845, c.turned()), c, tree);
			untinted(t, MAPLE, v, 0xB63C23, SeasonalLeaves.drawnAutumn(0xB63C23, 0x5F8C35, c), c, tree);
			untinted(t, ORANGE_MAPLE, v, 0xCB7724, SeasonalLeaves.drawnAutumn(0xCB7724, 0x6B9038, c), c, tree);
			untinted(t, ASPEN, v, 0xBEAA36, SeasonalLeaves.drawnAutumn(0xBEAA36, 0x86A848, c), c, tree);
			untinted(t, LARCH, v, 0xCEA23A, SeasonalLeaves.drawnAutumn(0xCEA23A, 0x7FA850, c), c, tree);
		}
		return t;
	}

	private static void grass(float[] t, int top, int side, int base, float temperature, float downfall, SeasonColours c) {
		int s = c.grass(base, temperature, downfall, 0);
		for (int v = 0; v < VARIANTS; v++) {
			entry(t, top, v, base, s, 1);
			// a grass block's side is mostly dirt, with the tinted fringe along its top
			entry(t, side, v, base, s, 0.5F);
		}
	}

	/** A crown this time of year, its tree's own about it, and lighter as it goes bare (the sky and land show through). */
	private static int crown(SeasonColours c, int seasoned, float[] tree) {
		int own = c.tree(seasoned, tree[0], tree[1], tree[2]);
		return SeasonColours.scale(own, 1 + (BARE_LIFT - 1) * c.bare());
	}

	private static void untinted(float[] t, int cls, int v, int base, int seasoned, SeasonColours c, float[] tree) {
		int own = SeasonColours.scale(seasoned, 1 + (tree[2] - 0.5F) * 0.12F);
		entry(t, cls, v, base, SeasonColours.scale(own, 1 + (BARE_LIFT - 1) * c.bare()), 1);
	}

	/**
	 * Fills an entry: toward {@code seasoned}'s hue, by how far it is from {@code base} (all the way once it differs much),
	 * and brightened by their ratio; {@code strength} scales both (a grass block's side turns half as much).
	 */
	private static void entry(float[] t, int cls, int v, int base, int seasoned, float strength) {
		int e = (cls * VARIANTS + v) * 5;
		float lb = lum(base), ls = lum(seasoned);
		int d = Math.abs((base >> 16 & 255) - (seasoned >> 16 & 255)) + Math.abs((base >> 8 & 255) - (seasoned >> 8 & 255))
			+ Math.abs((base & 255) - (seasoned & 255));
		t[e] = (seasoned >> 16 & 255) / 255.0F;
		t[e + 1] = (seasoned >> 8 & 255) / 255.0F;
		t[e + 2] = (seasoned & 255) / 255.0F;
		t[e + 3] = strength * Math.min(1, d / 50.0F);
		t[e + 4] = 1 + (ls / Math.max(1, lb) - 1) * strength;
	}

	private static float lum(int rgb) {
		return SeasonColours.luma(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255);
	}

	// ------------------------------------------------------------------------------------------------- the classes

	private static final class Hook implements dev.voidmc.lod.api.LodApi.Seasons {
		static final Hook INSTANCE = new Hook();

		private volatile AtomicReferenceArray<Biome> biomes = new AtomicReferenceArray<>(256);
		private Object level;

		@Override
		public int classify(int kind, int biome, int material, float conifer) {
			this.check();
			BlockState state = material > 0 ? dev.voidmc.lod.api.LodApi.material(material) : null;
			Biome b = this.biome(biome);
			float temperature = b == null ? 0.8F : b.getBaseTemperature();
			float downfall = b == null ? 0.4F : b.climateSettings.downfall();
			float seasonality = SeasonColours.seasonality(temperature, downfall);
			if (kind == GROUND_TOP || kind == GROUND_SIDE) {
				if (state == null || !state.is(Blocks.GRASS_BLOCK) || seasonality < 0.35F) {
					return 0;
				}
				boolean side = kind == GROUND_SIDE;
				if (seasonality < 0.8F) {
					return side ? SIDE_WARM : GRASS_WARM;
				}
				return downfall >= 0.6F ? side ? SIDE_WET : GRASS_WET : side ? SIDE_DRY : GRASS_DRY;
			}
			// leaves: by what they are, else by the share of needle trees
			String path = state == null ? "" : BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
			if (state != null && state.is(Blocks.BIRCH_LEAVES)) {
				return BIRCH;
			}
			switch (path) {
				case "beech_leaves" -> {
					return BEECH;
				}
				case "willow_leaves" -> {
					return WILLOW;
				}
				case "maple_leaves" -> {
					return MAPLE;
				}
				case "orange_maple_leaves" -> {
					return ORANGE_MAPLE;
				}
				case "aspen_leaves" -> {
					return ASPEN;
				}
				case "larch_leaves" -> {
					return LARCH;
				}
				default -> {
				}
			}
			boolean needles = path.contains("spruce") || path.contains("pine") || path.contains("fir") || path.contains("redwood") || path.contains("cedar");
			float share = conifer >= 0 ? conifer : needles ? 1 : 0;
			if (share >= 0.65F) {
				return NEEDLES;
			}
			if (seasonality < 0.35F || !path.isEmpty() && !path.contains("leaves")) {
				return 0;
			}
			if (share >= 0.3F) {
				return MIXED;
			}
			return seasonality < 0.8F ? BROADLEAF_WARM : BROADLEAF;
		}

		private Biome biome(int id) {
			if (id < 0) {
				return null;
			}
			AtomicReferenceArray<Biome> all = this.biomes;
			Biome b = id < all.length() ? all.get(id) : null;
			if (b == null) {
				String name = dev.voidmc.lod.api.LodApi.biomeName(id);
				var level = Minecraft.getInstance().level;
				if (name == null || level == null) {
					return null;
				}
				b = level.registryAccess().lookupOrThrow(Registries.BIOME).getValue(Identifier.parse(name));
				if (b != null) {
					if (id >= all.length()) {
						synchronized (this) {
							if (id >= this.biomes.length()) {
								AtomicReferenceArray<Biome> grown = new AtomicReferenceArray<>(Math.max(id + 1, this.biomes.length() * 2));
								for (int i = 0; i < this.biomes.length(); i++) {
									grown.set(i, this.biomes.get(i));
								}
								this.biomes = grown;
							}
							all = this.biomes;
						}
					}
					all.set(id, b);
				}
			}
			return b;
		}

		/** A new level: its biomes are its own. */
		private void check() {
			Object level = Minecraft.getInstance().level;
			if (level != this.level) {
				synchronized (this) {
					if (level != this.level) {
						this.level = level;
						this.biomes = new AtomicReferenceArray<>(256);
					}
				}
			}
		}
	}
}
