package dev.voidmc.mettle.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.core.Curves;
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

class PerkRulesTest {
	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static final ResourceKey<Skill> MINING = ResourceKey.create(MettleRegistries.SKILL, Identifier.fromNamespaceAndPath("mettle", "mining"));
	private final Map<ResourceKey<Perk>, Perk> tree = new HashMap<>();
	private final ResourceKey<Perk> root = add("root", Perk.Kind.ROOT, 1, "", 3, 1, "any");
	private final ResourceKey<Perk> a2 = add("a2", Perk.Kind.PATH, 2, "a", 1, 2, "any");
	private final ResourceKey<Perk> a2b = add("a2b", Perk.Kind.PATH, 2, "a", 1, 2, "any");
	private final ResourceKey<Perk> a3 = add("a3", Perk.Kind.PATH, 3, "a", 1, 3, "any");
	private final ResourceKey<Perk> b2 = add("b2", Perk.Kind.PATH, 2, "b", 1, 2, "any");
	private final ResourceKey<Perk> masteryA = add("ma", Perk.Kind.MASTERY, 4, "", 1, 5, "a");
	private final ResourceKey<Perk> masteryAny = add("many", Perk.Kind.MASTERY, 4, "", 1, 5, "any");
	private final ResourceKey<Perk> feat = add("feat", Perk.Kind.FEAT, 0, "", 1, 0, "any");

	@Test
	void curvesMatchTheBalanceSheet() {
		assertEquals(10, Curves.skillXpFor(1));
		assertEquals(4571, Curves.skillXpFor(10));
		assertEquals(28889, Curves.skillXpFor(20));
		assertEquals(0, Curves.skillLevel(9));
		assertEquals(5, Curves.skillLevel(723));
		assertEquals(20, Curves.skillLevel(1e9));
		assertEquals(1, Curves.characterLevel(0));
		assertEquals(10, Curves.characterLevel(4002));
	}

	@Test
	void rootsRankUpThenStop() {
		assertTrue(PerkRules.check(root, tree.get(root), view(2, 3, 1, Map.of())).isEmpty());
		assertEquals(PerkRules.Reason.SKILL_TOO_LOW, reason(root, view(1, 3, 5, Map.of())));
		assertEquals(PerkRules.Reason.MAX_RANK, reason(root, view(20, 20, 5, Map.of(root, 3))));
		assertEquals(PerkRules.Reason.NOT_BOUGHT, reason(feat, view(20, 20, 20, Map.of())));
	}

	@Test
	void tiersNeedSkillAttributeAndPath() {
		assertEquals(PerkRules.Reason.ATTRIBUTE_TOO_LOW, reason(a2, view(6, 5, 5, Map.of())));
		assertTrue(PerkRules.check(a2, tree.get(a2), view(6, 6, 2, Map.of())).isEmpty());
		assertEquals(PerkRules.Reason.NEEDS_PATH_PERK, reason(a3, view(12, 10, 5, Map.of(b2, 1))), "tier III needs its own path");
		assertTrue(PerkRules.check(a3, tree.get(a3), view(12, 10, 3, Map.of(a2, 1))).isEmpty());
		assertEquals(PerkRules.Reason.NO_POINTS, reason(a3, view(12, 10, 2, Map.of(a2, 1))));
	}

	@Test
	void oneMasteryAndItsSideCounts() {
		Map<ResourceKey<Perk>, Integer> twoA = Map.of(a2, 1, a2b, 1);
		Map<ResourceKey<Perk>, Integer> mixed = Map.of(a2, 1, b2, 1);
		assertTrue(PerkRules.check(masteryA, tree.get(masteryA), view(18, 14, 5, twoA)).isEmpty());
		assertEquals(PerkRules.Reason.NEEDS_TWO_PATH_PERKS, reason(masteryA, view(18, 14, 5, mixed)), "the left mastery wants its own path");
		assertTrue(PerkRules.check(masteryAny, tree.get(masteryAny), view(18, 14, 5, mixed)).isEmpty(), "the middle one takes any two");
		Map<ResourceKey<Perk>, Integer> withMastery = new HashMap<>(twoA);
		withMastery.put(masteryA, 1);
		assertEquals(PerkRules.Reason.MASTERY_TAKEN, reason(masteryAny, view(18, 14, 5, withMastery)));
	}

	private PerkRules.Reason reason(ResourceKey<Perk> key, PerkRules.TreeView view) {
		return PerkRules.check(key, this.tree.get(key), view).orElseThrow();
	}

	private PerkRules.TreeView view(int skill, int attribute, int points, Map<ResourceKey<Perk>, Integer> held) {
		return new PerkRules.TreeView(skill, attribute, points, held, this.tree);
	}

	private ResourceKey<Perk> add(String id, Perk.Kind kind, int tier, String path, int ranks, int cost, String side) {
		ResourceKey<Perk> key = ResourceKey.create(MettleRegistries.PERK, Identifier.fromNamespaceAndPath("mettle", "mining/" + id));
		this.tree.put(key, new Perk(MINING, kind, tier, path, ranks, cost, side, Component.literal(id), Component.literal(id), List.of(),
			Optional.empty(), Optional.empty(), Map.of(), List.of(), 0));
		return key;
	}
}
