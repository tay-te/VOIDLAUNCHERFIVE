package dev.voidmc.expanse.world.feature;

import com.mojang.serialization.MapCodec;
import dev.voidmc.expanse.Expanse;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.feature.Feature;

/** The feature types of the warm and dry biomes; their configurations are data, from tools/worldgen_warm.py. */
public final class WarmFeatures {
	private WarmFeatures() {
	}

	public static void init() {
		register("saguaro", SaguaroFeature.CODEC);
		register("termite_spire", TermiteSpireFeature.CODEC);
		register("field_wall", FieldWallFeature.CODEC);
		register("plant_rows", PlantRowsFeature.CODEC);
		register("hoodoo", HoodooFeature.CODEC);
	}

	private static void register(String name, MapCodec<? extends Feature> codec) {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, Expanse.id(name), codec);
	}
}
