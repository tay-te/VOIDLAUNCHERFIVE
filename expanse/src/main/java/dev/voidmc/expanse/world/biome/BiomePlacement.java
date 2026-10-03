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
 *  snowy taiga                              T0 H2                           Pine Heath
 *  snowy taiga (and its plateau)            T0 H3-4                         Larch Taiga
 *  taiga                                    T0 H4                           Boreal Muskeg
 *  forest, eroded uplands (erosion 0-3)     T1 H2                           Maple Highlands
 *  meadow (plateau)                         T1 H2-3                         Maple Highlands
 *  forest, lowlands (erosion 4-6)           T1 H2                           Bluebell Woods
 *  plains                                   T2 H1                           Aspen Parkland
 *  plains, lowlands (erosion 4-6)           T3 H2                           Olive Groves
 *  plains, eroded uplands (erosion 0-3)     T3 H2                           Monsoon Forest
 *  savanna / savanna plateau                T3 H0 (Amber Steppe keeps H1)   Ghost Gum Outback
 *  badlands / wooded badlands               T4 H2-4                         Saguaro Flats
 *  desert, inland (not the coast)           T4 H0 (Opal Dunes keeps H1-2)   Saguaro Flats
 *  mangrove swamp                           T3-4 (erosion 6)                Kapok Rainforest
 *  beach (positive weirdness, low slice)    T3                              Coral Coast
 * </pre>
 */
public final class BiomePlacement {
	private BiomePlacement() {
	}

	public static Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> wrap(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> out) {
		landforms(out);
		return entry -> out.accept(Pair.of(entry.getFirst(), remap(entry.getFirst(), entry.getSecond())));
	}

	/**
	 * The landform biomes ({@link dev.voidmc.expanse.world.terrain.Landform}): one point each, at the
	 * weirdness the Earth terrain gives that landform's columns and nowhere else, open to any temperature,
	 * humidity, continentalness and erosion. Natural weirdness never comes near, so these points only ever
	 * win on the landform itself; on other terrain generators they are never reached.
	 */
	private static void landforms(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> out) {
		ResourceKey<Biome>[] biomes = ExpanseBiomes.LANDFORMS;
		Climate.Parameter any = Climate.Parameter.span(-3.0F, 3.0F);
		for (int k = 1; k < biomes.length; k++) {
			float w = dev.voidmc.expanse.world.terrain.Landform.weirdness(k);
			out.accept(Pair.of(Climate.parameters(any, any, any, any, Climate.Parameter.point(0.0F), Climate.Parameter.span(w - 0.2F, w + 0.2F), 0.0F),
				biomes[k]));
		}
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
		// --- cold & temperate biomes ---
		// The boreal belt runs dry to wet across T0: Frostbloom Tundra (H0-1), Pine Heath, Larch Taiga, Boreal
		// Muskeg. Snowy taiga keeps its negative-weirdness half, taiga everything but T0 H4's variant half.
		if (b == Biomes.SNOWY_TAIGA && t == 0) {
			return h <= 2 ? ExpanseBiomes.PINE_HEATH : ExpanseBiomes.LARCH_TAIGA;
		}
		if (b == Biomes.TAIGA && t == 0 && h == 4) {
			return ExpanseBiomes.BOREAL_MUSKEG;
		}
		// T1 H2's forest splits by erosion: the eroded uplands (bands 0-3, mountains and their foothills on
		// the Earth terrain) go to the maples, the flat lowlands (bands 4-6) to the bluebell woods. The
		// plateau meadows of T1 H2-3 are uplands too.
		if (b == Biomes.FOREST && t == 1 && h == 2) {
			return band(p.erosion(), -0.78F, -0.375F, -0.2225F, 0.05F, 0.45F, 0.55F) >= 4
				? ExpanseBiomes.BLUEBELL_WOODS : ExpanseBiomes.MAPLE_HIGHLANDS;
		}
		if (b == Biomes.MEADOW && t == 1 && (h == 2 || h == 3)) {
			return ExpanseBiomes.MAPLE_HIGHLANDS;
		}
		if (b == Biomes.PLAINS && t == 2 && h == 1) {
			return ExpanseBiomes.ASPEN_PARKLAND;
		}
		// --- end cold & temperate biomes ---
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
		// --- warm & dry biomes ---
		// T3 H2's plains (the variant half of the warm forest band, free) splits by erosion: the lowlands
		// (bands 4-6) are olive groves, the eroded uplands (bands 0-3) monsoon forest.
		if (b == Biomes.PLAINS && t == 3 && h == 2) {
			return band(p.erosion(), -0.78F, -0.375F, -0.2225F, 0.05F, 0.45F, 0.55F) >= 4
				? ExpanseBiomes.OLIVE_GROVES : ExpanseBiomes.MONSOON_FOREST;
		}
		// The driest savanna (H0, middle and plateau) is the outback. Ahead of the savanna branch below,
		// which keeps H1 for Amber Steppe.
		if ((b == Biomes.SAVANNA || b == Biomes.SAVANNA_PLATEAU) && h == 0) {
			return ExpanseBiomes.GHOST_GUM_OUTBACK;
		}
		// Saguaros: the badlands' free variant half (badlands H2, wooded badlands H3-4; eroded badlands lives
		// only in this half and keeps it), and the driest inland desert (T4 H0, not the coast). The desert
		// part must stay ahead of the desert branch below, which keeps H1-2 for Opal Dunes.
		if ((b == Biomes.BADLANDS || b == Biomes.WOODED_BADLANDS) && t == 4
			|| b == Biomes.DESERT && t == 4 && h == 0 && band(p.continentalness(), -0.19F, -0.11F) != 1) {
			return ExpanseBiomes.SAGUARO_FLATS;
		}
		// The mangroves' variant half (T3-4, erosion band 6: the flattest hot lowlands): floodplain rainforest.
		if (b == Biomes.MANGROVE_SWAMP) {
			return ExpanseBiomes.KAPOK_RAINFOREST;
		}
		// The warm beaches' variant half (the low slice puts beaches in both halves; Palm Coast has the other).
		if (b == Biomes.BEACH && t == 3) {
			return ExpanseBiomes.CORAL_COAST;
		}
		// --- end warm & dry biomes ---
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
