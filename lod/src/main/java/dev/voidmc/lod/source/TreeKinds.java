package dev.voidmc.lod.source;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.RandomBooleanSelectorFeature;
import net.minecraft.world.level.levelgen.feature.RandomSelectorFeature;
import net.minecraft.world.level.levelgen.feature.SimpleRandomSelectorFeature;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.WeightedPlacedFeature;
import net.minecraft.world.level.levelgen.feature.WeightedRandomSelectorFeature;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.MegaPineFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.PineFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.SpruceFoliagePlacer;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.NoiseThresholdCountPlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RarityFilter;

/**
 * The trees a biome places, read from its vegetation features: every tree feature it places (through any random
 * selectors), weighted by how many per chunk, with its height, its leaves and whether it is a conifer. No textures:
 * a server works this out too, for clients whose biomes came without their features (they are not synced), and the
 * client colours the leaves itself ({@link BiomeLooks}).
 */
public final class TreeKinds {
	/**
	 * One kind of tree: {@code count} a chunk on average, {@code height} blocks tall, wearing {@code leaves} on a trunk of
	 * {@code trunk} (null where a server described it: only the leaves travel).
	 */
	public record Kind(BlockState leaves, double count, double height, boolean conifer, BlockState trunk) {
		public Kind(BlockState leaves, double count, double height, boolean conifer) {
			this(leaves, count, height, conifer, null);
		}
	}

	private final RandomSource random = RandomSource.create(42);
	private final LevelAccessor level;
	/** By leaves, height and shape: the same tree reached through several selectors is one kind. */
	private final Map<String, Kind> kinds = new LinkedHashMap<>();

	private TreeKinds(LevelAccessor level) {
		this.level = level;
	}

	public static List<Kind> of(Biome biome, LevelAccessor level) {
		TreeKinds trees = new TreeKinds(level);
		var steps = biome.getGenerationSettings().features();
		int vegetal = GenerationStep.Decoration.VEGETAL_DECORATION.ordinal();
		if (steps.size() > vegetal) {
			for (Holder<PlacedFeature> placed : steps.get(vegetal)) {
				trees.visit(placed.value(), 1.0, 0);
			}
		}
		return new ArrayList<>(trees.kinds.values());
	}

	/** Whether {@code placed} places trees, directly or through any of its random selectors. */
	static boolean hasTree(PlacedFeature placed) {
		return hasTree(placed.feature().value(), 0);
	}

	private static boolean hasTree(Feature feature, int depth) {
		if (depth > 6) {
			return false;
		}
		return switch (feature) {
			case TreeFeature tree -> true;
			case RandomSelectorFeature sel -> sel.features().stream().anyMatch(w -> hasTree(w.feature().value().feature().value(), depth + 1))
				|| hasTree(sel.defaultFeature().value().feature().value(), depth + 1);
			case SimpleRandomSelectorFeature sel -> sel.features().stream().anyMatch(f -> hasTree(f.value().feature().value(), depth + 1));
			case RandomBooleanSelectorFeature sel -> hasTree(sel.featureTrue().value().feature().value(), depth + 1)
				|| hasTree(sel.featureFalse().value().feature().value(), depth + 1);
			case WeightedRandomSelectorFeature sel -> sel.features().unwrap().stream().anyMatch(w -> hasTree(w.value().value().feature().value(), depth + 1));
			default -> false;
		};
	}

	private void visit(PlacedFeature placed, double weight, int depth) {
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
		BlockState leaves = this.leafState(tree);
		BlockState trunk = this.trunkState(tree);
		boolean conifer = isConifer(tree);
		String key = leaves + "|" + trunk + "|" + height + "|" + conifer;
		this.kinds.merge(key, new Kind(leaves, count, height, conifer, trunk),
			(a, b) -> new Kind(a.leaves(), a.count() + b.count(), a.height(), a.conifer(), a.trunk()));
	}

	private BlockState leafState(TreeFeature tree) {
		try {
			return tree.foliageProvider().value().getState(this.level, this.random, BlockPos.ZERO);
		} catch (RuntimeException e) {
			return Blocks.OAK_LEAVES.defaultBlockState();
		}
	}

	private BlockState trunkState(TreeFeature tree) {
		try {
			return tree.trunkProvider().value().getState(this.level, this.random, BlockPos.ZERO);
		} catch (RuntimeException e) {
			return Blocks.OAK_LOG.defaultBlockState();
		}
	}

	/** What a modded tree's placers are called when it is a conifer. */
	private static final List<String> CONIFER_WORDS = List.of("spruce", "pine", "fir", "conifer", "redwood", "sequoia", "larch", "cedar", "cypress",
		"hemlock", "taiga");

	/**
	 * Whether a tree is a conifer, a cone of needles (spruce, pine, redwood) rather than a dome of leaves (oak,
	 * birch, acacia, jungle). Vanilla's foliage placers say so by their kind; a modded tree by the names of its
	 * placers, its trunk's included, since some mods shape the crown in the trunk placer (Expanse's redwoods).
	 */
	private static boolean isConifer(TreeFeature tree) {
		FoliagePlacer foliage = tree.foliagePlacer();
		if (foliage instanceof SpruceFoliagePlacer || foliage instanceof PineFoliagePlacer || foliage instanceof MegaPineFoliagePlacer) {
			return true;
		}
		String names = tree.trunkPlacer().getClass().getSimpleName();
		if (!foliage.getClass().getName().startsWith("net.minecraft.")) {
			names += " " + foliage.getClass().getSimpleName();
		}
		names = names.toLowerCase(Locale.ROOT);
		for (String word : CONIFER_WORDS) {
			if (names.contains(word)) {
				return true;
			}
		}
		return false;
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
}
