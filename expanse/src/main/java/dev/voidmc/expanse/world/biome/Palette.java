package dev.voidmc.expanse.world.biome;

import dev.voidmc.expanse.Expanse;
import java.util.Map;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * The greens of the temperate land. Vanilla colours its grass and leaves from a climate colour map whose temperate
 * corner is a bright, saturated lime, brighter still under a shader pack; Bohemia's greens are olive and a little
 * grey, yellower in the fields and darker in the conifers. So vanilla's temperate biomes take muted greens of their
 * own here (the Expanse's set theirs in their data): a forest's crowns read darker than the meadows round them, and
 * from far off the land is the green of a real countryside, not a lawn.
 */
public final class Palette {
	/** Grass and foliage per biome. */
	private static final Map<ResourceKey<Biome>, int[]> GREENS = Map.of(
		Biomes.PLAINS, new int[]{0x8AA856, 0x6F9A3C},
		Biomes.SUNFLOWER_PLAINS, new int[]{0x8AA856, 0x6F9A3C},
		Biomes.MEADOW, new int[]{0x7FA45C, 0x689640},
		Biomes.FOREST, new int[]{0x6F9C4C, 0x4E8632},
		Biomes.FLOWER_FOREST, new int[]{0x73A050, 0x528A36},
		Biomes.BIRCH_FOREST, new int[]{0x7AA25A, 0x5F9142},
		Biomes.OLD_GROWTH_BIRCH_FOREST, new int[]{0x7AA25A, 0x5F9142},
		Biomes.TAIGA, new int[]{0x789C70, 0x5A8A56},
		Biomes.OLD_GROWTH_PINE_TAIGA, new int[]{0x7A9A68, 0x5A8650},
		Biomes.OLD_GROWTH_SPRUCE_TAIGA, new int[]{0x789868, 0x58844E});

	private Palette() {
	}

	public static void init() {
		BiomeModifications.create(Expanse.id("palette")).add(ModificationPhase.POST_PROCESSING,
			selection -> GREENS.containsKey(selection.getBiomeKey()),
			(selection, context) -> {
				int[] g = GREENS.get(selection.getBiomeKey());
				context.getEffects().setGrassColorOverride(g[0]);
				context.getEffects().setFoliageColorOverride(g[1]);
			});
	}
}
