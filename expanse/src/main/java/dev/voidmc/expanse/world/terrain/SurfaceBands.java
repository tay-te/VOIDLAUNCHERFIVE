package dev.voidmc.expanse.world.terrain;

import dev.voidmc.expanse.Expanse;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.TilingMode;
import net.minecraft.world.level.levelgen.densityfunction.generator.ConstantFunction;
import net.minecraft.world.level.levelgen.densityfunction.generator.GradientFunction;
import net.minecraft.world.level.levelgen.densityfunction.generator.NoiseFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.BinaryFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.CacheFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.ClampFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.InterpolatedFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.RangeChoiceFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.UnaryFunction;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.MaterialRules;
import net.minecraft.world.level.levelgen.material.condition.AbovePreliminarySurfaceCondition;
import net.minecraft.world.level.levelgen.material.condition.BiomeCondition;
import net.minecraft.world.level.levelgen.material.condition.HoleCondition;
import net.minecraft.world.level.levelgen.material.condition.MaterialCondition;
import net.minecraft.world.level.levelgen.material.condition.NoiseThresholdCondition;
import net.minecraft.world.level.levelgen.material.condition.NotCondition;
import net.minecraft.world.level.levelgen.material.condition.SteepCondition;
import net.minecraft.world.level.levelgen.material.condition.StoneDepthCondition;
import net.minecraft.world.level.levelgen.material.condition.TemperatureCondition;
import net.minecraft.world.level.levelgen.material.condition.VerticalGradientCondition;
import net.minecraft.world.level.levelgen.material.condition.WaterCondition;
import net.minecraft.world.level.levelgen.material.condition.YCondition;
import net.minecraft.world.level.levelgen.material.rule.BandlandsRule;
import net.minecraft.world.level.levelgen.material.rule.BlockRule;
import net.minecraft.world.level.levelgen.material.rule.ConditionRule;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.rule.OreVeinRule;
import net.minecraft.world.level.levelgen.material.rule.SequenceRule;
import org.jspecify.annotations.Nullable;

/**
 * Where the material rules can change a block of the Earth's stone, so that its surface pass leaves the rest
 * alone. Vanilla's pass ({@code MaterialSystem.buildSurface}) walks every block of every column, top to
 * bottom, and asks the whole rule of each block of stone what it becomes; the Earth is 704 blocks tall and
 * its mountains stand on 600 of stone, nearly all of which stays stone. Two things are cut, and neither can
 * change a block:
 *
 * <ul>
 *   <li><b>Deep stone.</b> Below a column's preliminary surface (the {@code above_preliminary_surface}
 *       condition, false there), only the rules outside that condition are left: in the Earth's
 *       {@code minecraft:overworld} the bedrock floor, the two ore veins and {@code overworld/underground}.
 *       Those are read here, for each height, from the rule itself: a vertical gradient is false at and above
 *       its {@code false_at_and_above}, a vein gives nothing where its density's outer {@code range_choice}
 *       on y falls to a constant at or below zero, a biome test is false where the region's possible biomes
 *       hold none of its biomes (as it compiles), and anything not understood counts as able to give a block.
 *       Where nothing can, the rule would return null, which leaves the block as it is; so it is not asked.
 *       That holds next to caves too (it assumes nothing about stone depth, water or biome). On the Earth this
 *       is all the stone from y 50 up to a few blocks under the ground (the preliminary surface, less eight,
 *       plus the column's soil depth), which in the mountains is most of the column.</li>
 *   <li><b>The veins' density.</b> When the rule is compiled for a chunk, each ore vein samples its density
 *       and richness over the whole chunk, up to the top of its highest section, which is most of the pass's
 *       time in the mountains. They are read only where a vein can be (above that the density is the
 *       constant again, and the richness is read only where the density is above zero), so they are sampled
 *       from the bottom of the chunk to the top of the highest vein instead. The values in that range are the
 *       very same floats: every function in the veins' trees fills a volume column by column from the bottom
 *       up, so a volume that is a shorter prefix in y of the other (same bottom, same x and z) holds the same
 *       value at each block (interpolation cells keep their grid, Perlin octaves recompute their corners
 *       from the cell alone). This is only done when every function in those trees is of a kind known to do
 *       that, and every rule and condition in the whole tree is a vanilla one (only veins ask for a filled
 *       volume).</li>
 * </ul>
 *
 * Wired in by the mixins on {@code NoiseBasedChunkGenerator} (only for the {@code expanse:earth} settings),
 * {@code MaterialSystem} and {@code MaterialRuleContext}; other worlds keep vanilla's pass as it is.
 */
public final class SurfaceBands {
	/** The Earth's noise settings, whose generator's surface pass this is for. */
	public static final ResourceKey<NoiseGeneratorSettings> EARTH = ResourceKey.create(Registries.NOISE_SETTINGS, Expanse.id("earth"));
	/** The analysis of the last rule asked about; there is one rule per world, so this is nearly always a hit. */
	private static volatile Analysis last;

	/** Marks the material system of a world whose surface pass is the Earth's. */
	public interface Earthly {
		void expanse$markEarth();
	}

	/** The bands a chunk's rule context is compiled and walked with; null for vanilla's pass. */
	public interface Banded {
		@Nullable SurfaceBands expanse$bands();

		void expanse$setBands(@Nullable SurfaceBands bands);
	}

	private final int minY;
	/** By height from {@link #minY}: true where a rule may give stone below the preliminary surface another block. */
	private final boolean[] deep;
	/** The highest height a vein's density is read at, or {@link Integer#MAX_VALUE} to sample as vanilla does. */
	private final int prefillTop;

	private SurfaceBands(int minY, boolean[] deep, int prefillTop) {
		this.minY = minY;
		this.deep = deep;
		this.prefillTop = prefillTop;
	}

	/** The bands for {@code rule} in a chunk with these possible biomes, heights {@code minY} to {@code maxY}. */
	public static SurfaceBands of(MaterialRule rule, @Nullable Set<Holder<Biome>> possibleBiomes, WorldGenerationContext gen,
		int minY, int maxY) {
		Analysis a = last;
		if (a == null || a.rule != rule || a.genMinY != gen.getMinGenY() || a.genDepth != gen.getGenDepth() || a.seaLevel != gen.seaLevel()
			|| a.minY != minY || a.maxY != maxY) {
			a = new Analysis(rule, gen, minY, maxY);
			last = a;
		}
		return a.bands(possibleBiomes);
	}

	/** Whether no rule can change stone at {@code y} below the preliminary surface. */
	public boolean quiet(int y) {
		int i = y - this.minY;
		return i >= 0 && i < this.deep.length && !this.deep[i];
	}

	/**
	 * A vein's filled density over the part of {@code full} it is read in, or null to fill all of it as vanilla
	 * does. Same as vanilla's getter but for the volume: inside it the buffer's value, outside it the function
	 * sampled at the block (which is all a vein reads above its band: its density's constant).
	 */
	public MaterialRules.@Nullable DensityGetter prefill(MaterialRuleContext context, DensityVolume full, Supplier<DensitySampler.Bound> sampler) {
		if (full.stepBlockX() != 1 || full.stepBlockY() != 1 || full.stepBlockZ() != 1 || this.prefillTop >= full.maxBlockY()) {
			return null;
		}
		long height = (long) this.prefillTop - full.minBlockY() + 1;
		if (height < 2) {
			return null;  // a volume one block tall takes other paths through interpolation and slices
		}
		int sizeY = (int) height;
		DensityVolume volume = new DensityVolume(full.sizeX(), sizeY, full.sizeZ(), full.minBlockX(), full.minBlockY(), full.minBlockZ());
		DensitySampler.Bound bound = sampler.get();
		DensityBuffer buffer = DensityBuffer.createUnpooled(volume.size());
		bound.sampleVolume(buffer, volume);
		return () -> {
			int index = volume.indexOfBlock(context.blockX(), context.blockY(), context.blockZ());
			return index == -1 ? bound.sampleValue(context.blockX(), context.blockY(), context.blockZ()) : buffer.get(index);
		};
	}

	/** What one rule tree allows in one world height, and the bands for each set of possible biomes seen. */
	private static final class Analysis {
		private final MaterialRule rule;
		/** The first chunk's; its anchors resolve the same in every chunk with the same depth and sea level. */
		private final WorldGenerationContext gen;
		private final int genMinY;
		private final int genDepth;
		private final int seaLevel;
		private final int minY;
		private final int maxY;
		/** Each vein's heights where its density can be above zero; null where that is not known (anywhere). */
		private final Map<OreVeinRule, boolean @Nullable []> veins = new IdentityHashMap<>();
		private final int prefillTop;
		private final Map<Set<Holder<Biome>>, SurfaceBands> byBiomes = new ConcurrentHashMap<>();
		private volatile @Nullable SurfaceBands anyBiome;

		Analysis(MaterialRule rule, WorldGenerationContext gen, int minY, int maxY) {
			this.rule = rule;
			this.gen = gen;
			this.genMinY = gen.getMinGenY();
			this.genDepth = gen.getGenDepth();
			this.seaLevel = gen.seaLevel();
			this.minY = minY;
			this.maxY = maxY;
			Scan scan = new Scan();
			scan.rule(rule);
			int top = Integer.MIN_VALUE;
			boolean narrow = scan.vanilla;
			for (OreVeinRule vein : scan.veins) {
				boolean[] band = this.band(vein);
				this.veins.put(vein, band);
				if (band == null || !prefixStable(vein.density()) || !prefixStable(vein.richness())) {
					narrow = false;
					continue;
				}
				for (int i = band.length - 1; i >= 0; i--) {
					if (band[i]) {
						top = Math.max(top, minY + i);
						break;
					}
				}
			}
			this.prefillTop = narrow && !scan.veins.isEmpty() ? top : Integer.MAX_VALUE;
		}

		SurfaceBands bands(@Nullable Set<Holder<Biome>> possibleBiomes) {
			if (possibleBiomes == null) {
				SurfaceBands b = this.anyBiome;
				if (b == null) {
					b = this.compute(null);
					this.anyBiome = b;
				}
				return b;
			}
			SurfaceBands b = this.byBiomes.get(possibleBiomes);
			if (b == null) {
				b = this.compute(possibleBiomes);
				if (this.byBiomes.size() > 1024) {
					this.byBiomes.clear();
				}
				this.byBiomes.put(new HashSet<>(possibleBiomes), b);
			}
			return b;
		}

		private SurfaceBands compute(@Nullable Set<Holder<Biome>> possibleBiomes) {
			boolean[] deep = new boolean[this.maxY - this.minY + 1];
			for (int y = this.minY; y <= this.maxY; y++) {
				deep[y - this.minY] = this.mayGive(this.rule, y, possibleBiomes);
			}
			return new SurfaceBands(this.minY, deep, this.prefillTop);
		}

		/** Whether {@code rule} may return a block for stone at {@code y} under the preliminary surface. */
		private boolean mayGive(MaterialRule rule, int y, @Nullable Set<Holder<Biome>> possibleBiomes) {
			return switch (rule) {
				case MaterialRule.HolderHolder h -> this.mayGive(h.holder().value(), y, possibleBiomes);
				case SequenceRule s -> {
					for (MaterialRule r : s.sequence()) {
						if (this.mayGive(r, y, possibleBiomes)) {
							yield true;
						}
					}
					yield false;
				}
				case ConditionRule c -> this.truth(c.ifTrue(), y, possibleBiomes) != Truth.FALSE && this.mayGive(c.thenRun(), y, possibleBiomes);
				case OreVeinRule o -> {
					boolean[] band = this.veins.get(o);
					yield band == null || band[y - this.minY];
				}
				default -> true;
			};
		}

		/** A condition for stone at {@code y} under the preliminary surface, as its compiled form would answer. */
		private Truth truth(MaterialCondition condition, int y, @Nullable Set<Holder<Biome>> possibleBiomes) {
			return switch (condition) {
				case MaterialCondition.HolderHolder h -> this.truth(h.holder().value(), y, possibleBiomes);
				case AbovePreliminarySurfaceCondition _ -> Truth.FALSE;
				case NotCondition n -> this.truth(n.target(), y, possibleBiomes).not();
				case VerticalGradientCondition v -> {
					if (y <= v.trueAtAndBelow().resolveY(this.gen)) {
						yield Truth.TRUE;
					}
					yield y >= v.falseAtAndAbove().resolveY(this.gen) ? Truth.FALSE : Truth.MAYBE;
				}
				case YCondition c when !c.addStoneDepth() && c.surfaceDepthMultiplier() == 0 ->
					y >= c.anchor().resolveY(this.gen) ? Truth.TRUE : Truth.FALSE;
				case BiomeCondition b -> possibleBiomes == null ? Truth.MAYBE : biomeTruth(b, possibleBiomes);
				default -> Truth.MAYBE;
			};
		}

		/** As {@link BiomeCondition#compile} decides from the possible biomes: never, always, or per block. */
		private static Truth biomeTruth(BiomeCondition b, Set<Holder<Biome>> possibleBiomes) {
			boolean never = true;
			for (Holder<Biome> biome : b.biomes()) {
				if (possibleBiomes.contains(biome)) {
					never = false;
					break;
				}
			}
			if (never) {
				return Truth.FALSE;
			}
			for (Holder<Biome> possible : possibleBiomes) {
				if (!b.biomes().contains(possible)) {
					return Truth.MAYBE;
				}
			}
			return Truth.TRUE;
		}

		/**
		 * The heights where a vein's density can be above zero, so that it can give a block: those where its outer
		 * {@code range_choice} on y is in range, worked out as its gradient computes y. Null if the density is not of
		 * that shape, or its out-of-range value is not a constant at or below zero.
		 */
		private boolean @Nullable [] band(OreVeinRule vein) {
			boolean[] band = new boolean[this.maxY - this.minY + 1];
			if (SharedConstants.DEBUG_DISABLE_ORE_VEINS) {
				return band;  // compiled to give nothing
			}
			if (SharedConstants.DEBUG_ORE_VEINS) {
				return null;  // gives air where there is no vein
			}
			if (!(inline(vein.density()) instanceof RangeChoiceFunction choice)
				|| !(inline(choice.whenOutOfRange()) instanceof ConstantFunction(float outside)) || !(outside <= 0.0F)
				|| !(inline(choice.input()) instanceof GradientFunction g) || g.axis() != Direction.Axis.Y || g.tiling() != TilingMode.CLAMP_TO_EDGE) {
				return null;
			}
			// GradientFunction's clamped sampler, step for step
			int coordinateRange = g.toCoordinate() - g.fromCoordinate();
			float coordinateFactor = (g.toValue() - g.fromValue()) / coordinateRange;
			int minCoordinate = Math.min(g.fromCoordinate(), g.toCoordinate());
			int maxCoordinate = Math.max(g.fromCoordinate(), g.toCoordinate());
			for (int y = this.minY; y <= this.maxY; y++) {
				float value = g.fromValue() + (Mth.clamp(y, minCoordinate, maxCoordinate) - g.fromCoordinate()) * coordinateFactor;
				band[y - this.minY] = value >= choice.minInclusive() && value < choice.maxExclusive();
			}
			return band;
		}
	}

	private enum Truth {
		TRUE, FALSE, MAYBE;

		Truth not() {
			return this == TRUE ? FALSE : this == FALSE ? TRUE : MAYBE;
		}
	}

	/** The whole rule tree: its veins, and whether every rule and condition in it is one of vanilla's. */
	private static final class Scan {
		final Set<OreVeinRule> veins = Collections.newSetFromMap(new IdentityHashMap<>());
		private final Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		boolean vanilla = true;

		void rule(MaterialRule rule) {
			if (!this.seen.add(rule)) {
				return;
			}
			switch (rule) {
				case MaterialRule.HolderHolder h -> this.rule(h.holder().value());
				case SequenceRule s -> s.sequence().forEach(this::rule);
				case ConditionRule c -> {
					this.condition(c.ifTrue());
					this.rule(c.thenRun());
				}
				case OreVeinRule o -> this.veins.add(o);
				case BlockRule _, BandlandsRule _ -> { }
				default -> this.vanilla = false;
			}
		}

		void condition(MaterialCondition condition) {
			switch (condition) {
				case MaterialCondition.HolderHolder h -> this.condition(h.holder().value());
				case NotCondition n -> this.condition(n.target());
				case AbovePreliminarySurfaceCondition _, BiomeCondition _, HoleCondition _, NoiseThresholdCondition _, SteepCondition _,
					StoneDepthCondition _, TemperatureCondition _, VerticalGradientCondition _, WaterCondition _, YCondition _ -> { }
				default -> this.vanilla = false;
			}
		}
	}

	private static DensityFunction inline(DensityFunction f) {
		while (f instanceof DensityFunctions.HolderHolder(Holder<DensityFunction> h)) {
			f = h.value();
		}
		return f;
	}

	/**
	 * Whether every function in {@code f} fills a volume so that a shorter prefix of it in y holds the same values:
	 * all of these work element by element, or column by column upward (noise, interpolation), and the compiler only
	 * adds caches and slices, which do too.
	 */
	private static boolean prefixStable(DensityFunction f) {
		if (!(f instanceof ConstantFunction || f instanceof GradientFunction || f instanceof NoiseFunction || f instanceof RangeChoiceFunction
			|| f instanceof BinaryFunction || f instanceof UnaryFunction || f instanceof ClampFunction || f instanceof CacheFunction
			|| f instanceof InterpolatedFunction || f instanceof DensityFunctions.HolderHolder)) {
			return false;
		}
		boolean[] stable = {true};
		f.rewriteChildren(child -> {
			if (stable[0] && !prefixStable(child)) {
				stable[0] = false;
			}
			return child;
		});
		return stable[0];
	}
}
