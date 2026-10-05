package dev.voidmc.mettle.character.skill.smithing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import dev.voidmc.mettle.character.skill.smithing.Forged.Mark;
import dev.voidmc.mettle.character.skill.smithing.Forged.Reforge;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The Smithing tree's numbers against TREES.md §10. */
class SmithingRulesTest {
	private static Forged of(Reforge reforge, Mark... marks) {
		return new Forged("Smith", List.of(marks), reforge);
	}

	@Test
	void lightHandAndLightReforgeLowerWeight() {
		assertEquals(1f, SmithingRules.weight(of(Reforge.NONE)));
		assertEquals(0.75f, SmithingRules.weight(of(Reforge.NONE, Mark.LIGHT_HAND)), 1e-6);
		assertEquals(0.5f, SmithingRules.weight(of(Reforge.LIGHT)), 1e-6);
		assertEquals(0.375f, SmithingRules.weight(of(Reforge.LIGHT, Mark.LIGHT_HAND, Mark.RIVETED)), 1e-6);
		// Other marks and reforges don't touch weight.
		assertEquals(1f, SmithingRules.weight(of(Reforge.STURDY, Mark.RIVETED, Mark.PROOFED, Mark.MASTERWORK)));
	}

	@Test
	void durabilityBonusesAdd() {
		assertEquals(240, SmithingRules.maxDamage(240, of(Reforge.NONE)));
		assertEquals(312, SmithingRules.maxDamage(240, of(Reforge.NONE, Mark.RIVETED)));           // +30 %
		assertEquals(313, SmithingRules.maxDamage(250, of(Reforge.NONE, Mark.FORGED)));            // +25 %, rounded
		assertEquals(500, SmithingRules.maxDamage(250, of(Reforge.NONE, Mark.FOLDED_STEEL)));      // twice
		assertEquals(320, SmithingRules.maxDamage(240, of(Reforge.NONE, Mark.MASTERWORK)));        // a third more
		assertEquals(360, SmithingRules.maxDamage(240, of(Reforge.STURDY)));                       // +50 %
		// Iron chestplate (240) by a full Armourer with Folded Steel and a Masterwork, then made Sturdy: 1 + .3 + 1 + 1/3 + .5
		assertEquals(752, SmithingRules.maxDamage(240, of(Reforge.STURDY, Mark.RIVETED, Mark.FOLDED_STEEL, Mark.MASTERWORK)));
		assertEquals(0, SmithingRules.maxDamage(0, of(Reforge.STURDY, Mark.RIVETED)));
	}

	@Test
	void masterworkIsOneInTenAndStable() {
		for (long smith : new long[] {0L, 42L, -7_331_998_120_441L, Long.MAX_VALUE}) {
			int hits = 0;
			int n = 200_000;
			for (long i = 0; i < n; i++) {
				if (SmithingRules.masterwork(smith, i)) {
					hits++;
				}
			}
			double rate = hits / (double) n;
			assertTrue(rate > 0.095 && rate < 0.105, "smith " + smith + " masterwork rate " + rate);
		}
		// The same piece rolls the same, however often it is asked (a shift-click asks twice).
		for (long i = 0; i < 1000; i++) {
			assertEquals(SmithingRules.masterwork(99L, i), SmithingRules.masterwork(99L, i));
		}
	}

	@Test
	void anvilDiscountsNeverGoBelowOne() {
		// Tinker III on a 5-level repair with 3 levels of prior work.
		assertEquals(2, SmithingRules.anvilCost(5, 3, 3, false));
		// A Hundred Hammers drops the prior work too.
		assertEquals(2, SmithingRules.anvilCost(5, 3, 0, true));
		assertEquals(1, SmithingRules.anvilCost(5, 3, 3, true));
		assertEquals(1, SmithingRules.anvilCost(1, 0, 3, false));
		// A job "Too Expensive" at 42 levels with 39 of prior work becomes a 3-level job.
		assertEquals(3, SmithingRules.anvilCost(42, 39, 0, true));
	}

	@Test
	void salvageGivesUpToHalfTheMetal() {
		assertEquals(36, SmithingRules.salvage(8, 0, 240));    // iron chestplate, unworn: 4 ingots' worth
		assertEquals(18, SmithingRules.salvage(8, 120, 240));  // half worn
		assertEquals(0, SmithingRules.salvage(8, 240, 240));
		assertEquals(13, SmithingRules.salvage(3, 0, 250));    // iron pickaxe: 1.5 ingots
		assertEquals(0, SmithingRules.salvage(0, 0, 250));
	}

	@Test
	void shieldwrightHalvesWear() {
		assertEquals(0.5f, SmithingRules.shieldWear(1f));
	}

	@Test
	void stampSurvivesItsCodec() {
		Forged forged = of(Reforge.WARDED, Mark.PROOFED, Mark.MASTERWORK);
		var json = Forged.CODEC.encodeStart(JsonOps.INSTANCE, forged).getOrThrow();
		assertEquals(forged, Forged.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
		Honed honed = Honed.fresh(true);
		assertEquals(60, honed.left());
		assertEquals(300, Honed.fresh(false).left());
		assertEquals(honed, Honed.CODEC.parse(JsonOps.INSTANCE, Honed.CODEC.encodeStart(JsonOps.INSTANCE, honed).getOrThrow()).getOrThrow());
	}
}
