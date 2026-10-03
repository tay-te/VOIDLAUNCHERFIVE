package dev.voidmc.lod.source;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.RandomBooleanSelectorFeature;
import net.minecraft.world.level.levelgen.feature.RandomSelectorFeature;
import net.minecraft.world.level.levelgen.feature.SimpleRandomSelectorFeature;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.WeightedPlacedFeature;
import net.minecraft.world.level.levelgen.feature.WeightedRandomSelectorFeature;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.NoiseThresholdCountPlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.minecraft.world.level.material.MapColor;

/**
 * What a biome looks like from a long way off, worked out once per biome from the biome itself rather
 * than from a hand-kept table, so modded biomes look right without anyone describing them:
 *
 * <ul>
 *   <li>the ground: sand, terracotta, snow, mycelium or grass in the biome's own tint, from its tags,
 *       temperature and rainfall;</li>
 *   <li>the trees: read from the biome's vegetation features. Every tree feature it places (through any
 *       nesting of random selectors) contributes its expected count per chunk (from the count, noise
 *       count and rarity placements), its height (from its trunk placer), its crown (from its foliage
 *       placer) and its leaves' colour. That gives a canopy height and the fraction of ground it covers,
 *       which is what a forest is at a distance.</li>
 * </ul>
 */
public final class BiomeLooks {
	public enum Ground { GRASS, SAND, RED_SAND, TERRACOTTA, SNOW, MYCELIUM, STONE, MUD }

	public record Look(Ground ground, int groundColor, int waterColor, float cover, int canopy, int canopyColor, boolean snowy) {
	}

	private static final int SAND = 0xD9CC9A;
	private static final int RED_SAND = 0xB8642A;
	private static final int TERRACOTTA = 0x9A5A3A;
	private static final int SNOW = 0xF4F8FA;
	private static final int MYCELIUM = 0x76666C;
	private static final int STONE = 0x7D7D7D;
	private static final int MUD = 0x4C4552;

	private final Map<Holder<Biome>, Look> looks = new ConcurrentHashMap<>();
	private final LevelAccessor level;

	public BiomeLooks(LevelAccessor level) {
		this.level = level;
	}

	public Look get(Holder<Biome> biome) {
		Look look = this.looks.get(biome);
		if (look == null) {
			look = this.compute(biome);
			this.looks.put(biome, look);
		}
		return look;
	}

	private Look compute(Holder<Biome> holder) {
		Biome biome = holder.value();
		float temperature = biome.getBaseTemperature();
		boolean snowy = temperature < 0.15F;
		Ground ground;
		if (holder.is(BiomeTags.IS_BADLANDS)) {
			ground = Ground.TERRACOTTA;
		} else if (holder.is(BiomeTags.IS_BEACH) || !biome.hasPrecipitation() && temperature > 1.0F) {
			ground = Ground.SAND;
		} else if (holder.is(BiomeTags.IS_OCEAN) || holder.is(BiomeTags.IS_RIVER)) {
			ground = Ground.SAND;
		} else if (snowy && !holder.is(BiomeTags.IS_TAIGA) && !holder.is(BiomeTags.IS_FOREST)) {
			ground = Ground.SNOW;
		} else if (holder.unwrapKey().map(k -> k.identifier().getPath().contains("mushroom")).orElse(false)) {
			ground = Ground.MYCELIUM;
		} else {
			ground = Ground.GRASS;
		}
		int groundColor = switch (ground) {
			// the grass texture is grey under its tint and about two thirds as bright as the tint itself
			case GRASS -> scale(biome.getGrassColor(0, 0), 0.72F);
			case SAND -> SAND;
			case RED_SAND -> RED_SAND;
			case TERRACOTTA -> TERRACOTTA;
			case SNOW -> SNOW;
			case MYCELIUM -> MYCELIUM;
			case STONE -> STONE;
			case MUD -> MUD;
		};
		Trees trees = new Trees(scale(biome.getFoliageColor(), 0.62F));
		var steps = biome.getGenerationSettings().features();
		int vegetal = GenerationStep.Decoration.VEGETAL_DECORATION.ordinal();
		if (steps.size() > vegetal) {
			for (Holder<PlacedFeature> placed : steps.get(vegetal)) {
				trees.visit(placed.value(), 1.0, 0);
			}
		}
		return new Look(ground, groundColor, scale(biome.getWaterColor(), 0.78F), trees.cover(), trees.height(), trees.color(), snowy);
	}

	/** Accumulates the tree features a biome places, weighted by how many of each it places per chunk. */
	private final class Trees {
		private final RandomSource random = RandomSource.create(42);
		private final int tint;
		private double perChunk;
		private double crownArea;
		private double height;
		private double r;
		private double g;
		private double b;

		Trees(int tint) {
			this.tint = tint;
		}

		void visit(PlacedFeature placed, double weight, int depth) {
			if (depth > 6) {
				return;
			}
			double count = weight * expectedCount(placed);
			if (count <= 0) {
				return;
			}
			Feature feature = placed.feature().value();
			switch (feature) {
				case TreeFeature tree -> this.tree(tree, count);
				case RandomSelectorFeature sel -> {
					double rest = 1;
					for (WeightedPlacedFeature w : sel.features()) {
						double p = rest * w.chance();
						this.visit(w.feature().value(), count * p, depth + 1);
						rest -= p;
					}
					this.visit(sel.defaultFeature().value(), count * Math.max(0, rest), depth + 1);
				}
				case SimpleRandomSelectorFeature sel -> {
					int n = sel.features().size();
					for (Holder<PlacedFeature> f : sel.features()) {
						this.visit(f.value(), count / n, depth + 1);
					}
				}
				case RandomBooleanSelectorFeature sel -> {
					this.visit(sel.featureTrue().value(), count / 2, depth + 1);
					this.visit(sel.featureFalse().value(), count / 2, depth + 1);
				}
				case WeightedRandomSelectorFeature sel -> {
					int total = 0;
					for (var w : sel.features().unwrap()) {
						total += w.weight();
					}
					for (var w : sel.features().unwrap()) {
						this.visit(w.value().value(), count * w.weight() / Math.max(1, total), depth + 1);
					}
				}
				default -> {
				}
			}
		}

		private void tree(TreeFeature tree, double count) {
			int h = 0;
			for (int i = 0; i < 8; i++) {
				h += tree.trunkPlacer().getTreeHeight(this.random);
			}
			double height = h / 8.0 + 1;
			double radius = 2 + height / 7.0;
			int leaves = this.leafColor(tree);
			this.perChunk += count;
			this.crownArea += count * Math.PI * radius * radius;
			this.height += count * height;
			this.r += count * ((leaves >> 16) & 0xFF);
			this.g += count * ((leaves >> 8) & 0xFF);
			this.b += count * (leaves & 0xFF);
		}

		private int leafColor(TreeFeature tree) {
			try {
				BlockState state = tree.foliageProvider().value().getState(BiomeLooks.this.level, this.random, BlockPos.ZERO);
				MapColor color = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
				// tinted leaves report the generic plant colour: those take the biome's foliage colour
				if (color == MapColor.PLANT || color == MapColor.GRASS || color == MapColor.NONE) {
					return this.tint;
				}
				return scale(color.col, 0.85F);
			} catch (RuntimeException e) {
				return this.tint;
			}
		}

		float cover() {
			// crown area per chunk over the chunk's 256 blocks; crowns overlap, so it saturates
			return (float) (1 - Math.exp(-this.crownArea / 256.0));
		}

		int height() {
			return this.perChunk <= 0 ? 0 : (int) Math.round(this.height / this.perChunk);
		}

		int color() {
			if (this.perChunk <= 0) {
				return this.tint;
			}
			return (int) (this.r / this.perChunk) << 16 | (int) (this.g / this.perChunk) << 8 | (int) (this.b / this.perChunk);
		}
	}

	/** Trees placed per chunk by one placed feature, on average: its count and rarity placements multiplied. */
	private static double expectedCount(PlacedFeature placed) {
		double count = 1;
		for (PlacementModifier m : placed.placement()) {
			switch (m) {
				case CountPlacement c -> count *= mean(c.count());
				case NoiseThresholdCountPlacement n -> count *= (n.belowNoise() + n.aboveNoise()) / 2.0;
				case RarityFilter r -> count /= Math.max(1, r.chance());
				default -> {
				}
			}
		}
		return count;
	}

	private static double mean(IntProvider p) {
		return (p.minInclusive() + p.maxInclusive()) / 2.0;
	}

	static int scale(int rgb, float f) {
		int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * f));
		int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * f));
		int b = Math.min(255, (int) ((rgb & 0xFF) * f));
		return r << 16 | g << 8 | b;
	}

	static int snow() {
		return SNOW;
	}

	static int stone() {
		return STONE;
	}

	static int sand() {
		return SAND;
	}
}
