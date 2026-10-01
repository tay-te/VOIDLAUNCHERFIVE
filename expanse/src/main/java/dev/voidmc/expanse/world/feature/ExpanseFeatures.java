package dev.voidmc.expanse.world.feature;

import com.mojang.serialization.MapCodec;
import dev.voidmc.expanse.Expanse;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.feature.Feature;

public final class ExpanseFeatures {
	private ExpanseFeatures() {
	}

	public static void init() {
		register("karst_pillar", KarstPillarFeature.CODEC);
		register("natural_arch", NaturalArchFeature.CODEC);
		register("crystal_outcrop", CrystalOutcropFeature.CODEC);
		register("dunes", DuneFeature.CODEC);
		Registry.register(BuiltInRegistries.PLACEMENT_MODIFIER_TYPE, Expanse.id("clear_of_structures"), ClearOfStructuresFilter.CODEC);
	}

	private static void register(String name, MapCodec<? extends Feature> codec) {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, Expanse.id(name), codec);
	}
}
