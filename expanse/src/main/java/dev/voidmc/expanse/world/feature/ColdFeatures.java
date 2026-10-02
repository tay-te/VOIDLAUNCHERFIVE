package dev.voidmc.expanse.world.feature;

import dev.voidmc.expanse.Expanse;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/** The feature types of the cold and temperate biomes (registered from {@code ColdBlocks.init}). */
public final class ColdFeatures {
	private ColdFeatures() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, Expanse.id("bog_pool"), BogPoolFeature.CODEC);
	}
}
