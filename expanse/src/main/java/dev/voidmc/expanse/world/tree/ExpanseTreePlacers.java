package dev.voidmc.expanse.world.tree;

import com.mojang.serialization.MapCodec;
import dev.voidmc.expanse.Expanse;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;

public final class ExpanseTreePlacers {
	public static final TrunkPlacerType<RedwoodTrunkPlacer> REDWOOD_TRUNK = trunk("redwood_trunk_placer", RedwoodTrunkPlacer.CODEC);
	public static final TrunkPlacerType<CrownTrunkPlacer> CROWN_TRUNK = trunk("crown_trunk_placer", CrownTrunkPlacer.CODEC);
	public static final TrunkPlacerType<PalmTrunkPlacer> PALM_TRUNK = trunk("palm_trunk_placer", PalmTrunkPlacer.CODEC);
	public static final TrunkPlacerType<BaobabTrunkPlacer> BAOBAB_TRUNK = trunk("baobab_trunk_placer", BaobabTrunkPlacer.CODEC);
	public static final TrunkPlacerType<SpiralTrunkPlacer> SPIRAL_TRUNK = trunk("spiral_trunk_placer", SpiralTrunkPlacer.CODEC);

	public static final FoliagePlacerType<ClumpFoliagePlacer> CLUMP_FOLIAGE = foliage("clump_foliage_placer", ClumpFoliagePlacer.CODEC);
	public static final FoliagePlacerType<DroopingFoliagePlacer> DROOPING_FOLIAGE = foliage("drooping_foliage_placer", DroopingFoliagePlacer.CODEC);
	public static final FoliagePlacerType<PalmFoliagePlacer> PALM_FOLIAGE = foliage("palm_foliage_placer", PalmFoliagePlacer.CODEC);

	public static final TreeDecoratorType<HangingCascadeDecorator> HANGING_CASCADE = Registry.register(BuiltInRegistries.TREE_DECORATOR_TYPE,
		Expanse.id("hanging_cascade"), new TreeDecoratorType<>(HangingCascadeDecorator.CODEC));

	private ExpanseTreePlacers() {
	}

	public static void init() {
	}

	private static <P extends TrunkPlacer> TrunkPlacerType<P> trunk(String name, MapCodec<P> codec) {
		return Registry.register(BuiltInRegistries.TRUNK_PLACER_TYPE, Expanse.id(name), new TrunkPlacerType<>(codec));
	}

	private static <P extends FoliagePlacer> FoliagePlacerType<P> foliage(String name, MapCodec<P> codec) {
		return Registry.register(BuiltInRegistries.FOLIAGE_PLACER_TYPE, Expanse.id(name), new FoliagePlacerType<>(codec));
	}
}
