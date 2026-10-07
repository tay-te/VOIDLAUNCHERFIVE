package dev.voidmc.mettle.character.respec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.mettle.api.CharacterAttribute;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

class RespecRulesTest {
	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static final ResourceKey<Skill> MINING = skill("mining");
	private static final ResourceKey<Skill> BLADES = skill("blades");

	private final Map<ResourceKey<Perk>, Perk> perks = new HashMap<>();
	private final ResourceKey<Perk> root = add(MINING, "mining/root", Perk.Kind.ROOT, 1);
	private final ResourceKey<Perk> pathA = add(MINING, "mining/a2", Perk.Kind.PATH, 2);
	private final ResourceKey<Perk> pathA3 = add(MINING, "mining/a3", Perk.Kind.PATH, 3);
	private final ResourceKey<Perk> mastery = add(MINING, "mining/mastery", Perk.Kind.MASTERY, 5);
	private final ResourceKey<Perk> feat = add(MINING, "mining/feat", Perk.Kind.FEAT, 0);
	private final ResourceKey<Perk> teaching = add(MINING, "mining/teaching", Perk.Kind.TEACHING, 0);
	private final ResourceKey<Perk> synergy = add(MINING, "synergy/mining_smithing", Perk.Kind.SYNERGY, 0);
	private final ResourceKey<Perk> bladesRoot = add(BLADES, "blades/root", Perk.Kind.ROOT, 1);
	private final ResourceKey<Perk> removed = ResourceKey.create(MettleRegistries.PERK, Identifier.fromNamespaceAndPath("mettle", "mining/gone"));

	@Test
	void forgettingRefundsEveryBoughtPerkOfThatTreeOnly() {
		Map<ResourceKey<Perk>, Integer> held = Map.of(root, 3, pathA, 1, pathA3, 1, mastery, 1, feat, 1, teaching, 1, synergy, 1, bladesRoot, 2, removed, 1);
		RespecRules.Forgetting result = RespecRules.forget(held, k -> Optional.ofNullable(this.perks.get(k)), MINING);
		// roots 3 × 1 + path 2 + path 3 + mastery 5
		assertEquals(13, result.points());
		assertEquals(4, result.perks());
		assertTrue(result.mastery());
		assertFalse(result.nothing());
		assertEquals(Map.of(feat, 1, teaching, 1, synergy, 1, bladesRoot, 2, removed, 1), result.kept());
	}

	@Test
	void forgettingAnUntouchedTreeIsRefused() {
		Map<ResourceKey<Perk>, Integer> held = Map.of(feat, 1, bladesRoot, 1);
		RespecRules.Forgetting result = RespecRules.forget(held, k -> Optional.ofNullable(this.perks.get(k)), MINING);
		assertTrue(result.nothing());
		assertEquals(0, result.points());
		assertFalse(result.mastery());
		assertEquals(held, result.kept());
	}

	@Test
	void unbindingReturnsEveryPlacedPointToBePlacedAgain() {
		Map<CharacterAttribute, Integer> spent = Map.of(CharacterAttribute.STRENGTH, 5, CharacterAttribute.WITS, 2);
		assertEquals(7, RespecRules.placed(spent));
		assertEquals(0, RespecRules.placed(Map.of()));
		// Level 8, all seven level points placed: nothing waits. Unbound: all seven wait again.
		assertEquals(0, RespecRules.unplaced(8, 7, 0, 0));
		assertEquals(7, RespecRules.unplaced(8, 0, 0, 0));
		// A Wanderer at level 3 placed 4 free + 2 level points; unbound, all six wait again (and a perk's bonus point).
		assertEquals(0, RespecRules.unplaced(3, 6, 4, 0));
		assertEquals(7, RespecRules.unplaced(3, 0, 4, 1));
	}

	private ResourceKey<Perk> add(ResourceKey<Skill> skill, String id, Perk.Kind kind, int cost) {
		ResourceKey<Perk> key = ResourceKey.create(MettleRegistries.PERK, Identifier.fromNamespaceAndPath("mettle", id));
		this.perks.put(key, new Perk(skill, kind, kind == Perk.Kind.MASTERY ? 4 : 1, "a", kind == Perk.Kind.ROOT ? 3 : 1, cost, "any",
			Component.literal(id), Component.literal(id), List.of(), Optional.empty(), Optional.empty(), Map.of(), List.of(), 0));
		return key;
	}

	private static ResourceKey<Skill> skill(String id) {
		return ResourceKey.create(MettleRegistries.SKILL, Identifier.fromNamespaceAndPath("mettle", id));
	}
}
