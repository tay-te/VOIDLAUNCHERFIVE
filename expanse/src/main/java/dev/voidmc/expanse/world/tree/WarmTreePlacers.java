package dev.voidmc.expanse.world.tree;

import com.mojang.serialization.MapCodec;
import dev.voidmc.expanse.Expanse;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;

/** The trunk placers of the warm-country trees (olive, ghost gum, teak, flame tree, mesquite, kapok, sea almond). */
public final class WarmTreePlacers {
	public static final TrunkPlacerType<ForkTrunkPlacer> FORK_TRUNK = trunk("fork_trunk_placer", ForkTrunkPlacer.CODEC);
	public static final TrunkPlacerType<KapokTrunkPlacer> KAPOK_TRUNK = trunk("kapok_trunk_placer", KapokTrunkPlacer.CODEC);
	public static final TrunkPlacerType<TierTrunkPlacer> TIER_TRUNK = trunk("tier_trunk_placer", TierTrunkPlacer.CODEC);

	private WarmTreePlacers() {
	}

	public static void init() {
	}

	private static <P extends TrunkPlacer> TrunkPlacerType<P> trunk(String name, MapCodec<P> codec) {
		return Registry.register(BuiltInRegistries.TRUNK_PLACER_TYPE, Expanse.id(name), new TrunkPlacerType<>(codec));
	}
}
