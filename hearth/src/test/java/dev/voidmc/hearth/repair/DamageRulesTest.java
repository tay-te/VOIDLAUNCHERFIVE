package dev.voidmc.hearth.repair;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DamageRulesTest {
	@BeforeAll
	static void boot() {
		RepairTestKit.boot();
	}

	@Test
	void livingInAHouseIsNotDamage() {
		BlockState closed = Blocks.OAK_DOOR.defaultBlockState();
		BlockState open = closed.setValue(DoorBlock.OPEN, true);
		assertTrue(DamageRules.sameBlock(closed, open), "an open door is the same door");
		assertFalse(DamageRules.isDamage(closed, closed, open), "opening a door breaks nothing");
		assertTrue(DamageRules.sameBlock(Blocks.COPPER_BLOCK.weathering().unaffected().defaultBlockState(), Blocks.COPPER_BLOCK.weathering().weathered().defaultBlockState()), "copper weathers");
		assertTrue(DamageRules.sameBlock(Blocks.COPPER_BLOCK.weathering().unaffected().defaultBlockState(), Blocks.COPPER_BLOCK.waxed().exposed().defaultBlockState()), "and is waxed");
		assertFalse(DamageRules.sameBlock(Blocks.OAK_PLANKS.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState()));
	}

	@Test
	void whatGrowsOrIsWorkedIsNeverDamage() {
		for (BlockState s : new BlockState[]{Blocks.AIR.defaultBlockState(), Blocks.WHEAT.defaultBlockState(), Blocks.FARMLAND.defaultBlockState(),
			Blocks.SHORT_GRASS.defaultBlockState(), Blocks.OAK_LEAVES.defaultBlockState(), Blocks.WATER.defaultBlockState(), Blocks.FIRE.defaultBlockState(),
			Blocks.CAKE.defaultBlockState(), Blocks.LIGHT.defaultBlockState(), Blocks.SNOW.defaultBlockState(), Blocks.POPPY.defaultBlockState(),
			Blocks.OAK_SAPLING.defaultBlockState(), Blocks.SCAFFOLDING.defaultBlockState()}) {
			assertTrue(DamageRules.volatileBlock(s), s + " comes and goes");
		}
		for (BlockState s : new BlockState[]{Blocks.OAK_PLANKS.defaultBlockState(), Blocks.GLASS_PANE.defaultBlockState(), Blocks.LANTERN.defaultBlockState(),
			Blocks.CANDLE.defaultBlockState(), Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState(), Blocks.OAK_DOOR.defaultBlockState(), Blocks.DIRT.defaultBlockState()}) {
			assertFalse(DamageRules.volatileBlock(s), s + " is part of the building");
		}
	}

	@Test
	void damageIsABlueprintBlockLost() {
		BlockState glass = Blocks.GLASS_PANE.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		assertTrue(DamageRules.isDamage(glass, glass, air), "a smashed window");
		assertTrue(DamageRules.isDamage(glass, glass, Blocks.FIRE.defaultBlockState()), "a burned frame");
		assertFalse(DamageRules.isDamage(glass, Blocks.DIAMOND_BLOCK.defaultBlockState(), air), "a player's own block taken away is not ours");
		assertFalse(DamageRules.isDamage(Blocks.WHEAT.defaultBlockState(), Blocks.WHEAT.defaultBlockState(), air), "a harvest");
	}

	@Test
	void repairsOverwriteOnlyWhatNobodyPlaced() {
		assertTrue(DamageRules.restorable(Blocks.AIR.defaultBlockState()));
		assertTrue(DamageRules.restorable(Blocks.FIRE.defaultBlockState()));
		assertTrue(DamageRules.restorable(Blocks.WATER.defaultBlockState()));
		assertTrue(DamageRules.restorable(Blocks.SHORT_GRASS.defaultBlockState()));
		assertTrue(DamageRules.restorable(Blocks.OAK_LEAVES.defaultBlockState()), "wild leaves");
		assertFalse(DamageRules.restorable(Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true)),
			"placed leaves are someone's");
		assertFalse(DamageRules.restorable(Blocks.CHEST.defaultBlockState()), "never a chest");
		assertFalse(DamageRules.restorable(Blocks.DIAMOND_BLOCK.defaultBlockState()), "never a player's build");
	}

	@Test
	void inspectionsFindHolesNotChanges() {
		BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
		assertTrue(DamageRules.missing(planks, Blocks.AIR.defaultBlockState()));
		assertFalse(DamageRules.missing(planks, planks));
		assertFalse(DamageRules.missing(planks, Blocks.STONE_BRICKS.defaultBlockState()), "someone built something there: not ours to undo");
		assertFalse(DamageRules.missing(Blocks.WHEAT.defaultBlockState(), Blocks.AIR.defaultBlockState()));
	}

	@Test
	void litterIsSolidStuffButNotSomeonesChest() {
		assertTrue(DamageRules.litterBlock(Blocks.COBBLESTONE.defaultBlockState()));
		assertTrue(DamageRules.litterBlock(Blocks.DIRT.defaultBlockState()), "a dirt pillar in a doorway");
		assertFalse(DamageRules.litterBlock(Blocks.CHEST.defaultBlockState()));
		assertFalse(DamageRules.litterBlock(Blocks.SNOW.defaultBlockState()), "weather");
		assertFalse(DamageRules.litterBlock(Blocks.AIR.defaultBlockState()));
		assertFalse(DamageRules.litterBlock(Blocks.WATER.defaultBlockState()));
	}
}
