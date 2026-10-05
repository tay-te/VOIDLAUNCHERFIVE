package dev.voidmc.mettle.character.skill.heavy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

class HeavyRulesTest {
	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void rootsMatchTheirTooltips() {
		assertEquals(1.15f, HeavyRules.heft(3), 1e-6);
		assertEquals(1.30f, HeavyRules.feller(2), 1e-6);
		assertEquals(1.0f, HeavyRules.feller(0), 1e-6);
	}

	@Test
	void rhythmBuildsToSixtyPercentAndBreaksAfterThreeSeconds() {
		int streak = 0;
		long gap = Long.MAX_VALUE;
		float[] expected = {0.15f, 0.30f, 0.45f, 0.60f, 0.60f, 0.60f};
		for (float e : expected) {
			streak = HeavyRules.nextStreak(streak, gap);
			gap = 20;
			assertEquals(e, HeavyRules.rhythm(streak, gap), 1e-6);
		}
		assertEquals(0f, HeavyRules.rhythm(streak, HeavyRules.RHYTHM_WINDOW + 1), 1e-6);
		assertEquals(1, HeavyRules.nextStreak(streak, HeavyRules.RHYTHM_WINDOW + 1));
		assertEquals(0f, HeavyRules.rhythm(0, 0), 1e-6);
	}

	@Test
	void berserkerIsBelowFortyPercent() {
		assertTrue(HeavyRules.berserk(7.9f, 20f));
		assertFalse(HeavyRules.berserk(8f, 20f));
		assertFalse(HeavyRules.berserk(0f, 20f));
		assertEquals(2.0f, HeavyRules.critMultiplier(true), 1e-6);
		assertEquals(1.5f, HeavyRules.critMultiplier(false), 1e-6);
	}

	@Test
	void timberFollowsTrunkAndBranchesUpwardOnly() {
		BlockPos base = new BlockPos(0, 64, 0);
		Set<BlockPos> logs = new HashSet<>();
		for (int y = 65; y <= 70; y++) {
			logs.add(new BlockPos(0, y, 0));
		}
		logs.add(new BlockPos(1, 69, 1));   // a diagonal branch
		logs.add(new BlockPos(2, 70, 2));
		logs.add(new BlockPos(0, 63, 0));   // a root below the cut: stays
		logs.add(new BlockPos(9, 66, 0));   // the next tree over: out of reach
		List<BlockPos> trunk = HeavyRules.trunk(base, logs::contains, HeavyRules.TIMBER_LIMIT);
		assertEquals(8, trunk.size());
		assertEquals(65, trunk.getFirst().getY());
		assertFalse(trunk.contains(new BlockPos(0, 63, 0)));
		assertFalse(trunk.contains(new BlockPos(9, 66, 0)));
	}

	@Test
	void timberStopsAtItsLimit() {
		BlockPos base = new BlockPos(0, 0, 0);
		List<BlockPos> trunk = HeavyRules.trunk(base, p -> p.getX() == 0 && p.getZ() == 0 && p.getY() > 0, HeavyRules.TIMBER_LIMIT);
		assertEquals(HeavyRules.TIMBER_LIMIT, trunk.size());
	}

	@Test
	void aTreeNeedsACrownAWallDoesNot() {
		List<BlockPos> trunk = List.of(new BlockPos(0, 1, 0), new BlockPos(0, 2, 0));
		Set<BlockPos> leaves = Set.of(new BlockPos(1, 2, 0), new BlockPos(-1, 2, 0), new BlockPos(0, 3, 0));
		assertTrue(HeavyRules.hasCrown(trunk, p -> leaves.contains(p.immutable())));
		assertFalse(HeavyRules.hasCrown(trunk, p -> false));
		assertFalse(HeavyRules.hasCrown(trunk, p -> p.immutable().equals(new BlockPos(1, 2, 0))));
	}
}
