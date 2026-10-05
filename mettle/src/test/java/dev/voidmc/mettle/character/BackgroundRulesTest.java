package dev.voidmc.mettle.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.mettle.api.Background;
import dev.voidmc.mettle.api.CharacterAttribute;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Skill;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

class BackgroundRulesTest {
	static {
		// Background's codec touches item registries, so vanilla must be bootstrapped before the fixtures below.
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static final ResourceKey<Skill> MINING = skill("mining");
	private static final ResourceKey<Skill> BLADES = skill("blades");
	private static final ResourceKey<Skill> ALCHEMY = skill("alchemy");

	private static final Function<ResourceKey<Skill>, Optional<Skill>> SKILLS = key -> {
		if (key.equals(MINING) || key.equals(BLADES)) {
			return Optional.of(new Skill(Component.literal(key.identifier().getPath()), CharacterAttribute.STRENGTH, 0, true));
		}
		if (key.equals(ALCHEMY)) {
			return Optional.of(new Skill(Component.literal("alchemy"), CharacterAttribute.WITS, 0, false));
		}
		return Optional.empty();
	};

	private static final Background RANGER = background(Map.of(CharacterAttribute.AGILITY, 3, CharacterAttribute.VITALITY, 1), 0, 0);
	private static final Background WANDERER = background(Map.of(), 4, 2);

	@Test
	void fixedBackgroundIgnoresPointsAndPicks() {
		BackgroundRules.Result r = BackgroundRules.validate(RANGER, Map.of(CharacterAttribute.STRENGTH, 10), List.of(MINING), SKILLS);
		assertTrue(r.ok());
		assertTrue(r.spent().isEmpty(), "a stale client must not add points");
		assertTrue(r.favoured().isEmpty());
	}

	@Test
	void wandererNeedsAllFourPointsAndTwoSkills() {
		assertEquals(BackgroundRules.Reason.POINTS_NOT_PLACED,
			BackgroundRules.validate(WANDERER, Map.of(CharacterAttribute.STRENGTH, 3), List.of(MINING, BLADES), SKILLS).rejected().orElseThrow());
		assertEquals(BackgroundRules.Reason.SKILLS_NOT_PICKED,
			BackgroundRules.validate(WANDERER, Map.of(CharacterAttribute.STRENGTH, 4), List.of(MINING), SKILLS).rejected().orElseThrow());
		BackgroundRules.Result ok = BackgroundRules.validate(WANDERER,
			Map.of(CharacterAttribute.STRENGTH, 1, CharacterAttribute.AGILITY, 2, CharacterAttribute.WITS, 1, CharacterAttribute.PRESENCE, 0),
			List.of(MINING, BLADES), SKILLS);
		assertTrue(ok.ok());
		assertEquals(Map.of(CharacterAttribute.STRENGTH, 1, CharacterAttribute.AGILITY, 2, CharacterAttribute.WITS, 1), ok.spent(), "zeros are dropped");
	}

	@Test
	void wandererRejectsBadPointsAndSkills() {
		assertEquals(BackgroundRules.Reason.POINTS_INVALID,
			BackgroundRules.validate(WANDERER, Map.of(CharacterAttribute.STRENGTH, 5), List.of(MINING, BLADES), SKILLS).rejected().orElseThrow());
		assertEquals(BackgroundRules.Reason.POINTS_INVALID,
			BackgroundRules.validate(WANDERER, Map.of(CharacterAttribute.STRENGTH, 6, CharacterAttribute.AGILITY, -2), List.of(MINING, BLADES), SKILLS).rejected().orElseThrow());
		assertEquals(BackgroundRules.Reason.SKILL_INVALID,
			BackgroundRules.validate(WANDERER, Map.of(CharacterAttribute.STRENGTH, 4), List.of(MINING, ALCHEMY), SKILLS).rejected().orElseThrow(), "unreleased");
		assertEquals(BackgroundRules.Reason.SKILL_INVALID,
			BackgroundRules.validate(WANDERER, Map.of(CharacterAttribute.STRENGTH, 4), List.of(MINING, MINING), SKILLS).rejected().orElseThrow(), "duplicate");
		assertEquals(BackgroundRules.Reason.SKILL_INVALID,
			BackgroundRules.validate(WANDERER, Map.of(CharacterAttribute.STRENGTH, 4), List.of(MINING, skill("nope")), SKILLS).rejected().orElseThrow(), "unknown");
	}

	private static ResourceKey<Skill> skill(String path) {
		return ResourceKey.create(MettleRegistries.SKILL, Identifier.fromNamespaceAndPath("mettle", path));
	}

	private static Background background(Map<CharacterAttribute, Integer> attributes, int freePoints, int freeFavoured) {
		return new Background(Component.literal("b"), Component.literal("t"), 0, attributes, List.of(), freePoints, freeFavoured,
			Optional.empty(), List.of(), Component.empty());
	}
}
