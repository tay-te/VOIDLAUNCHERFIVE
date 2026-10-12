package dev.voidmc.mettle.character.cards;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.Keyword;
import dev.voidmc.mettle.api.Trait;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/** The ceilings of docs/CARDS.md §14 as the hooks apply them, the physics behind "X higher", and what extra drops copy. */
class CeilingsTest {
	static {
		TestCards.PERKS.size();
	}

	@Test
	void clampsHoldTheCeilings() {
		assertAll(
			() -> assertEquals(0, Ceilings.movementCorrection(0.30), 1e-12),
			() -> assertEquals(-0.12, Ceilings.movementCorrection(0.47), 1e-12),
			() -> assertEquals(1.2, Ceilings.melee(1.0, 1.2, false), 1e-12, "under the ceiling a hit keeps its multiplier"),
			() -> assertEquals(1.6, 1.25 * Ceilings.melee(1.25, 2.0, false), 1e-12),
			() -> assertEquals(1.6, 1.1 * Ceilings.melee(1.1, 3.0, true) * Ceilings.TWIN_EDGE, 1e-12, "the Twin Edge's quarter counts"),
			() -> assertEquals(0.55, Ceilings.taken(0.3), 1e-12),
			() -> assertEquals(0.8, Ceilings.taken(0.8), 1e-12),
			() -> assertEquals(2.2, 1.5 * Ceilings.dig(1.5, 2.0), 1e-12),
			() -> assertEquals(0.5, 0.74 * Ceilings.dropScale(0.74), 1e-12),
			() -> assertEquals(1, Ceilings.dropScale(0.3), 1e-12));
	}

	@Test
	void jumpsStayUnderTwoBlocks() {
		// Vanilla 0.42 is untouched; anything stronger is held to the ceiling (jump boost potions keep their own part).
		assertEquals(0.42f, Ceilings.jumpPower(0.42f, 0.42, 0f), 1e-6);
		float capped = Ceilings.jumpPower(0.7f, 0.7, 0f);
		assertTrue(CardPhysics.apex(capped) < 2.0, "a capped jump peaks at " + CardPhysics.apex(capped));
		assertTrue(CardPhysics.apex(Ceilings.JUMP_STRENGTH) > 1.85, "the ceiling still leaves room for Springing and Spring");
		assertEquals(0.1f + 0.53f, Ceilings.jumpPower(0.8f, 0.7, 0.1f), 1e-6, "a potion's part stays");
	}

	@Test
	void promisesBecomeTheirPhysics() {
		List<Executable> checks = new ArrayList<>();
		for (double higher : new double[] {0.04, 0.10, 0.125, 0.25}) {
			double s = CardPhysics.jumpStrengthFor(higher);
			double got = CardPhysics.apex(CardPhysics.JUMP * (1 + s)) / CardPhysics.apex(CardPhysics.JUMP) - 1;
			checks.add(() -> assertEquals(higher, got, 0.002, "a jump " + higher + " higher"));
		}
		for (double faster : new double[] {0.06, 0.10, 0.15, 0.375}) {
			double e = CardPhysics.waterEfficiencyFor(faster);
			double got = CardPhysics.swimSpeed(e) / CardPhysics.swimSpeed(0) - 1;
			checks.add(() -> assertEquals(faster, got, 0.002, "swimming " + faster + " faster"));
		}
		checks.add(() -> assertEquals(0.3, CardPhysics.attributeAmount("minecraft:movement_speed", "add_multiplied_base", 0.3), 1e-12));
		assertAll(checks);
	}

	@Test
	void cardModifiersAreKnown() {
		assertAll(
			() -> assertTrue(Ceilings.fromCards(Mettle.id("trait/athletics/fleet"))),
			() -> assertTrue(Ceilings.fromCards(Mettle.id("set/swift/2"))),
			() -> assertTrue(Ceilings.fromCards(Mettle.id("trait_timed/feral"))),
			() -> assertTrue(Ceilings.fromCards(Mettle.id("mythic_timed/unclad"))),
			() -> assertTrue(Ceilings.fromCards(Mettle.id("perk/athletics/featherweight/movement_speed")), "a cursed card's own effect"),
			() -> assertFalse(Ceilings.fromCards(Mettle.id("perk/athletics/runner/movement_speed")), "an ordinary perk"),
			() -> assertFalse(Ceilings.fromCards(Mettle.id("attr/agility/movement_speed"))),
			() -> assertFalse(Ceilings.fromCards(Identifier.fromNamespaceAndPath("minecraft", "sprinting"))));
		AttributeInstance speed = new AttributeInstance(Attributes.MOVEMENT_SPEED, i -> { });
		speed.addTransientModifier(new AttributeModifier(Mettle.id("attr/agility/movement_speed"), 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		speed.addTransientModifier(new AttributeModifier(Mettle.id("trait/athletics/fleet"), 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		speed.addTransientModifier(new AttributeModifier(Mettle.id("set/swift/2"), 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		assertEquals(0.5, Ceilings.cardSum(speed), 1e-12);
		assertEquals(1.6 / 1.1, Ceilings.cardFactor(speed), 1e-12);
		AttributeInstance attack = new AttributeInstance(Attributes.ATTACK_DAMAGE, i -> { });
		attack.addTransientModifier(new AttributeModifier(Mettle.id("perk/blades/reckless_edge/attack_damage"), 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		attack.addTransientModifier(new AttributeModifier(Mettle.id("set/brutal/2"), 0.04, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		assertEquals(1.04 * 1.2, Ceilings.cardFactor(attack), 1e-12);
	}

	/** Every attribute source on cards that adds movement, melee damage or dig speed is one the ceilings count. */
	@Test
	void everySourceIsCounted() {
		List<Executable> checks = new ArrayList<>();
		TestCards.TRAITS.forEach((id, t) -> {
			Trait.Effect e = t.effect();
			String attribute = e.attribute().map(Identifier::getPath).orElse("");
			String path = id.getPath();
			if (attribute.equals("movement_speed")) {
				checks.add(() -> assertTrue(Ceilings.MOVEMENT_TRAITS.contains(path), path + " adds movement but the ceiling doesn't count it"));
			}
			if (attribute.equals("attack_damage")) {
				checks.add(() -> assertTrue(Ceilings.MELEE_TRAITS.contains(path), path + " adds melee damage but the ceiling doesn't count it"));
			}
			if (attribute.equals("block_break_speed")) {
				checks.add(() -> assertTrue(Ceilings.DIG_TRAITS.contains(path), path + " adds dig speed but the ceiling doesn't count it"));
			}
		});
		Set<String> counted = Set.of("swift/2/movement_speed", "brutal/2/attack_damage");
		TestCards.KEYWORDS.forEach((id, k) -> k.sets().forEach((tier, bonus) -> {
			for (Keyword.Modifier m : bonus.effects()) {
				String a = m.attribute().getPath();
				if (a.equals("movement_speed") || a.equals("attack_damage") || a.equals("block_break_speed")) {
					String what = id.getPath() + "/" + tier + "/" + a;
					checks.add(() -> assertTrue(counted.contains(what), what + " is not counted by the ceilings"));
				}
			}
		}));
		checks.add(() -> assertEquals(0.03, TestCards.KEYWORDS.get(TestCards.id("swift")).sets().get(2).effects().getFirst().amount(), 1e-12));
		checks.add(() -> assertEquals(0.04, TestCards.KEYWORDS.get(TestCards.id("brutal")).sets().get(2).effects().getFirst().amount(), 1e-12));
		assertAll(checks);
	}

	// What extra drops may copy (CardHooks.plain) needs bound item components: the tour checks it (CardEffectsJourney).

	@Test
	void magpieSkipsVaults() {
		assertTrue(CardHooks.magpieTable(table("chests/simple_dungeon")));
		assertTrue(CardHooks.magpieTable(table("chests/trial_chambers/corridor")));
		assertFalse(CardHooks.magpieTable(table("chests/trial_chambers/reward")));
		assertFalse(CardHooks.magpieTable(table("chests/trial_chambers/reward_ominous_rare")));
		assertFalse(CardHooks.magpieTable(table("spawners/trial_chamber/consumables")));
		assertFalse(CardHooks.magpieTable(table("blocks/stone")));
	}

	private static net.minecraft.core.Holder<net.minecraft.world.level.storage.loot.LootTable> table(String path) {
		return net.minecraft.core.Holder.Reference.createStandAlone(null,
			net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, Identifier.withDefaultNamespace(path)));
	}
}
