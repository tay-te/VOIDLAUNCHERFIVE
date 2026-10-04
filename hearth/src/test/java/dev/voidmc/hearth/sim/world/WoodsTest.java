package dev.voidmc.hearth.sim.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.sim.TestSupport;
import dev.voidmc.hearth.sim.land.OreModel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

/** The woodcutter's replanting and the ore estimate. */
class WoodsTest {
	@Test
	void everyTreeIsReplantedWithItsOwnKind() {
		TestSupport.boot();
		assertEquals(Blocks.BIRCH_SAPLING.defaultBlockState(), Woodcutter.sapling(Identifier.withDefaultNamespace("birch_log")));
		assertEquals(Blocks.SPRUCE_SAPLING.defaultBlockState(), Woodcutter.sapling(Identifier.withDefaultNamespace("spruce_wood")));
		assertEquals(Blocks.MANGROVE_PROPAGULE.defaultBlockState(), Woodcutter.sapling(Identifier.withDefaultNamespace("mangrove_log")));
		assertEquals(Blocks.PALE_OAK_SAPLING.defaultBlockState(), Woodcutter.sapling(Identifier.withDefaultNamespace("pale_oak_log")));
		assertNull(Woodcutter.sapling(Identifier.withDefaultNamespace("crimson_stem")), "no sapling, no planting");
		assertNull(Woodcutter.sapling(Identifier.fromNamespaceAndPath("nomod", "odd_log")), "an unknown tree is not guessed at");
	}

	@Test
	void oreFollowsTheBandsAndTheLand() {
		float[] plain = {1, 1, 1, 1};
		float[] badlands = {1, 1, 1, 4};
		OreModel.Estimate low = OreModel.estimate(40, 48, plain, false);
		OreModel.Estimate high = OreModel.estimate(200, 48, plain, false);
		assertTrue(low.ore() > high.ore(), "metal ores lie deep: " + low.ore() + " vs " + high.ore());
		assertTrue(OreModel.estimate(40, 48, badlands, false).ore() > low.ore(), "gold in the badlands");
		assertTrue(OreModel.estimate(40, 48, plain, true).ore() > low.ore() * 1.3, "cave walls show their veins");
		assertTrue(low.fuel() > 0, "coal");
		assertEquals(OreModel.RAW_GOLD, OreModel.estimate(-20, 30, new float[] {1, 0.1F, 0.1F, 1}, false).main());
	}
}
