package dev.voidmc.expanse.world.biome;

import com.mojang.datafixers.util.Pair;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

/**
 * Where the Expanse biomes go in the overworld.
 *
 * <p>Vanilla lays the overworld out as a table: temperature (5 bands) by humidity (5 bands), each
 * cell naming a biome, with a second "variant" table used where the weirdness noise is positive.
 * Most cells of the variant table are empty — the same biome on both sides of weirdness zero. Those
 * empty variant cells are where these biomes live: each takes the positive-weirdness half of one
 * vanilla biome's cell, so it borders its vanilla parent and no vanilla biome disappears.
 *
 * <p>The rewrite works on the finished entries, not the tables, so the result is the same whether the
 * entry came from the middle, plateau, slope or peak picker — a Cloud Forest is the plateau of a warm
 * humid jungle wherever the builder put one.
 *
 * <pre>
 *  vanilla biome (weirdness > 0)            climate cell                    becomes
 *  snowy plains                             T0                              Frostbloom Tundra
 *  plains / meadow                          T1 H1                           Heather Moor
 *  forest                                   T2 H2                           Wisteria Vale
 *  taiga                                    T1 H3                           Redwood Giants
 *  dark forest                              T2 H4                           Lumen Grove
 *  swamp                                    any                             Willow Bayou
 *  savanna / savanna plateau                any                             Amber Steppe
 *  desert                                   T4 H0-2                         Opal Dunes
 *  desert                                   T4 H3-4                         Jade Karst
 *  forest / jungle (plateau)                T3                              Cloud Forest
 *  stony peaks                              T3                              Prismatic Peaks
 *  jagged / frozen peaks (either sign)      T2                              Verdant Peaks
 *  beach (negative weirdness; warm coasts)  T3, and T4's desert beaches     Palm Coast
 * </pre>
 */
public final class BiomePlacement {
	private BiomePlacement() {
	}

	public static Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> wrap(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> out) {
		return entry -> out.accept(Pair.of(entry.getFirst(), remap(entry.getFirst(), entry.getSecond())));
	}

	static ResourceKey<Biome> remap(Climate.ParameterPoint p, ResourceKey<Biome> b) {
		int t = band(p.temperature(), -0.45F, -0.15F, 0.2F, 0.55F);
		int h = band(p.humidity(), -0.35F, -0.1F, 0.1F, 0.3F);
		boolean variant = p.weirdness().min() >= 0L;   // the positive half; valleys straddle zero and are left alone
		boolean original = p.weirdness().max() <= 0L;

		if (b == Biomes.JAGGED_PEAKS || b == Biomes.FROZEN_PEAKS) {
			return t == 2 ? ExpanseBiomes.VERDANT_PEAKS : b;
		}
		if (original) {
			if (b == Biomes.BEACH && t == 3) {
				return ExpanseBiomes.PALM_COAST;
			}
			if (b == Biomes.DESERT && t == 4 && isBeachSlot(p)) {
				return ExpanseBiomes.PALM_COAST;
			}
			return b;
		}
		if (!variant) {
			return b;
		}
		if (b == Biomes.SNOWY_PLAINS) {
			return ExpanseBiomes.FROSTBLOOM_TUNDRA;
		}
		if ((b == Biomes.PLAINS || b == Biomes.MEADOW) && t == 1 && h == 1) {
			return ExpanseBiomes.HEATHER_MOOR;
		}
		if (b == Biomes.FOREST && t == 2 && h == 2) {
			return ExpanseBiomes.WISTERIA_VALE;
		}
		if (b == Biomes.TAIGA && t == 1 && h == 3) {
			return ExpanseBiomes.REDWOOD_GIANTS;
		}
		if (b == Biomes.DARK_FOREST) {
			return ExpanseBiomes.LUMEN_GROVE;
		}
		if (b == Biomes.SWAMP) {
			return ExpanseBiomes.WILLOW_BAYOU;
		}
		if (b == Biomes.SAVANNA || b == Biomes.SAVANNA_PLATEAU) {
			return ExpanseBiomes.AMBER_STEPPE;
		}
		if (b == Biomes.DESERT && t == 4) {
			return h <= 2 ? ExpanseBiomes.OPAL_DUNES : ExpanseBiomes.JADE_KARST;
		}
		if ((b == Biomes.FOREST || b == Biomes.JUNGLE) && t == 3) {
			// At T3 with positive weirdness the middle picker gives plains and sparse/bamboo jungle, so a
			// forest or jungle here can only have come from the plateau picker.
			return ExpanseBiomes.CLOUD_FOREST;
		}
		if (b == Biomes.STONY_PEAKS && t == 3) {
			return ExpanseBiomes.PRISMATIC_PEAKS;
		}
		return b;
	}

	/** Vanilla's beach entries: coast continentalness, erosion band 4 or 6. */
	private static boolean isBeachSlot(Climate.ParameterPoint p) {
		float cMin = Climate.unquantizeCoord(p.continentalness().min());
		float cMax = Climate.unquantizeCoord(p.continentalness().max());
		if (Math.abs(cMin - -0.19F) > 1.0E-3F || Math.abs(cMax - -0.11F) > 1.0E-3F) {
			return false;
		}
		float eMin = Climate.unquantizeCoord(p.erosion().min());
		return Math.abs(eMin - 0.05F) < 1.0E-3F || Math.abs(eMin - 0.55F) < 1.0E-3F;
	}

	/** Which of vanilla's bands an entry's range falls in, judged by its midpoint. */
	private static int band(Climate.Parameter param, float... edges) {
		float mid = (Climate.unquantizeCoord(param.min()) + Climate.unquantizeCoord(param.max())) / 2.0F;
		int i = 0;
		while (i < edges.length && mid >= edges[i]) {
			i++;
		}
		return i;
	}
}
