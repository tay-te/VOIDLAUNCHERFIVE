package dev.voidmc.hearth.sim.land;

import dev.voidmc.hearth.sim.chains.BiomeTable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
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

/**
 * Reads what a biome's land gives (docs/LIVING_WORLD.md §7.1), once per biome, from the biome itself rather than from a
 * hand-kept table, so modded biomes are surveyed sensibly without anyone describing them:
 * <ul>
 *   <li><b>woods</b>: every tree feature the biome places (through any nesting of random selectors) contributes its
 *   expected count per chunk, its crown and its trunk's log — the same reading VOID LOD uses for its forest blanket — so a
 *   redwood valley and a birch hillside are told apart by density and species;</li>
 *   <li><b>the rest</b>: the {@code hearth_sim/land} table by biome id, then by tag, then the biome's climate and tags
 *   (badlands rock, beaches of sand, river banks of clay, fertile temperate plains, open grassland).</li>
 * </ul>
 * Thread-safe; the cache is cleared when a server starts (holders belong to one server's registries).
 */
public final class BiomeResources {
	private static final Map<Holder<Biome>, BiomeLand> CACHE = new ConcurrentHashMap<>();
	private static volatile @org.jspecify.annotations.Nullable BiomeTable table;

	private BiomeResources() {
	}

	/** New data (server start, data pack reload): forget what was worked out with the old. */
	public static void reset(BiomeTable newTable) {
		table = newTable;
		CACHE.clear();
	}

	public static BiomeLand of(Holder<Biome> biome) {
		BiomeLand land = CACHE.get(biome);
		if (land == null) {
			BiomeTable t = table;
			land = compute(biome, t != null ? t : dev.voidmc.hearth.sim.chains.SimData.current().land());
			CACHE.put(biome, land);
		}
		return land;
	}

	static BiomeLand compute(Holder<Biome> holder, BiomeTable t) {
		Biome biome = holder.value();
		String key = holder.unwrapKey().map(k -> k.identifier().toString()).orElse("?");
		// the table: the biome's own entry over its tags' entries
		BiomeTable.Entry entry = BiomeTable.Entry.NONE;
		for (Map.Entry<Identifier, BiomeTable.Entry> e : t.tags().entrySet()) {
			if (holder.is(TagKey.create(Registries.BIOME, e.getKey()))) {
				entry = e.getValue().over(entry);
			}
		}
		Identifier id = holder.unwrapKey().map(k -> k.identifier()).orElse(null);
		if (id != null && t.biomes().containsKey(id)) {
			entry = t.biomes().get(id).over(entry);
		}

		float temperature = biome.getBaseTemperature();
		boolean wet = biome.hasPrecipitation();
		boolean snowy = temperature < 0.15F;
		boolean desert = !wet && temperature > 1.0F;
		boolean water = holder.is(BiomeTags.IS_OCEAN) || holder.is(BiomeTags.IS_RIVER) || holder.is(BiomeTags.IS_DEEP_OCEAN);
		boolean beach = holder.is(BiomeTags.IS_BEACH);
		boolean badlands = holder.is(BiomeTags.IS_BADLANDS);
		boolean mountain = holder.is(BiomeTags.IS_MOUNTAIN) || holder.is(ConventionalBiomeTags.IS_MOUNTAIN);

		Trees trees = new Trees();
		var steps = biome.getGenerationSettings().features();
		int vegetal = GenerationStep.Decoration.VEGETAL_DECORATION.ordinal();
		if (steps.size() > vegetal) {
			for (Holder<PlacedFeature> placed : steps.get(vegetal)) {
				trees.visit(placed.value(), 1.0, 0);
			}
		}

		float cover = entry.trees().map(Double::floatValue).orElse(trees.cover());
		Identifier wood = entry.wood().orElse(trees.species());
		Identifier rock = entry.rock().orElse(badlands ? Identifier.withDefaultNamespace("terracotta") : desert ? Identifier.withDefaultNamespace("sandstone")
			: BiomeLand.STONE);
		Identifier sand = entry.sand().orElse(badlands ? Identifier.withDefaultNamespace("red_sand") : BiomeLand.SAND);
		Identifier clay = entry.clay().orElse(BiomeLand.CLAY);
		// fertile: temperate, watered, not sand or snow; open: grass without much forest
		float fertility = entry.fertility().map(Double::floatValue).orElseGet(() -> {
			if (snowy || desert || badlands || water || beach) {
				return snowy ? 0.1F : 0.05F;
			}
			float f = temperature < 0.3F ? 0.35F : temperature > 1.2F ? 0.5F : 0.8F;
			return wet ? f : f * 0.5F;
		});
		float pasture = entry.pasture().map(Double::floatValue).orElseGet(() -> {
			if (desert || badlands || water || beach || temperature < 0.05F) {
				return 0.05F;
			}
			return Math.max(0.1F, 0.9F - cover);
		});
		boolean rocky = entry.rocky().orElse(badlands || mountain || holder.is(BiomeTags.IS_HILL) && temperature < 0.3F);
		float[] ore = {1, 1, 1, 1};
		String[] names = {"coal", "iron", "copper", "gold"};
		for (int k = 0; k < names.length; k++) {
			ore[k] = entry.ore().getOrDefault(names[k], 1.0).floatValue();
		}
		// sandy ground: deserts, dunes and beaches, or any land the table gives sand that is too poor to farm
		boolean sandy = desert || beach || badlands || entry.sand().isPresent() && !water && fertility < 0.2F;
		return new BiomeLand(key, cover, wood, rock, sand, clay, fertility, pasture, rocky, entry.salt().orElse(false), sandy, water, snowy, ore,
			entry.specialties());
	}

	/** Accumulates the tree features a biome places, weighted by how many of each it places per chunk. */
	private static final class Trees {
		private final RandomSource random = RandomSource.create(42);
		private double crownArea;
		private final Map<Identifier, Double> logs = new HashMap<>();

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
			this.crownArea += count * Math.PI * radius * radius;
			Identifier log = trunk(tree);
			if (log != null) {
				// tall trees give more timber
				this.logs.merge(log, count * height, Double::sum);
			}
		}

		private Identifier trunk(TreeFeature tree) {
			try {
				BlockState state = tree.trunkProvider().value().getState(null, this.random, BlockPos.ZERO);
				return BuiltInRegistries.BLOCK.getKey(state.getBlock());
			} catch (RuntimeException e) {
				return null;
			}
		}

		float cover() {
			// crown area per chunk over the chunk's 256 blocks; crowns overlap, so it saturates
			return (float) (1 - Math.exp(-this.crownArea / 256.0));
		}

		Identifier species() {
			Identifier best = BiomeLand.OAK;
			double most = 0;
			for (Map.Entry<Identifier, Double> e : this.logs.entrySet()) {
				if (e.getValue() > most) {
					most = e.getValue();
					best = e.getKey();
				}
			}
			return best;
		}
	}

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
