package dev.voidmc.hearth.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.quest.model.ObjectiveKind;
import dev.voidmc.hearth.quest.model.QuestType;
import dev.voidmc.hearth.quest.template.Amount;
import dev.voidmc.hearth.quest.template.QuestTemplate;
import dev.voidmc.hearth.quest.template.TemplateParser;
import dev.voidmc.hearth.quest.template.TemplateValidator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TemplateTest {
	/** Vanilla ids against the real registries; Expanse counts as loaded (its ids are allowed by requires_mods). */
	private static final TemplateValidator.Lookup VANILLA = new TemplateValidator.Lookup() {
		@Override
		public boolean item(Identifier id) {
			return BuiltInRegistries.ITEM.containsKey(id);
		}

		@Override
		public boolean entity(Identifier id) {
			return BuiltInRegistries.ENTITY_TYPE.containsKey(id);
		}

		@Override
		public boolean modLoaded(String modId) {
			return true;
		}
	};

	@BeforeAll
	static void boot() {
		QuestTestKit.bootstrap();
	}

	@Test
	void shippedTemplatesParseAndValidate() throws Exception {
		Map<Identifier, QuestTemplate> templates = QuestTestKit.shippedTemplates();
		assertTrue(templates.size() >= 30, "at least 30 templates, got " + templates.size());
		for (QuestTemplate t : templates.values()) {
			List<String> problems = TemplateValidator.validate(t, VANILLA);
			assertTrue(problems.isEmpty(), t.id() + ": " + problems);
		}
	}

	@Test
	void shippedTemplatesCoverEveryTypeAndCulture() throws Exception {
		Map<Identifier, QuestTemplate> templates = QuestTestKit.shippedTemplates();
		Set<QuestType> types = EnumSet.noneOf(QuestType.class);
		templates.values().forEach(t -> types.add(t.type()));
		assertEquals(EnumSet.allOf(QuestType.class), types, "every quest type has a template");
		for (Culture culture : Culture.values()) {
			for (Goods goods : Goods.values()) {
				boolean covered = templates.values().stream().anyMatch(t -> t.when().trigger() == QuestType.Trigger.STALLED
					&& (t.when().goods().isEmpty() || t.when().goods().contains(goods)) && t.weightFor(culture) > 0);
				assertTrue(covered, "a supply template covers " + goods + " for " + culture);
			}
			long offered = templates.values().stream().filter(t -> t.when().trigger() == QuestType.Trigger.NONE && t.weightFor(culture) > 0).count();
			assertTrue(offered >= 15, culture + " has " + offered + " everyday templates");
		}
	}

	@Test
	void parsesAFullTemplate() {
		QuestTemplate t = QuestTestKit.parse("""
			{
				"type": "gathering",
				"weight": 4,
				"when": {"cultures": {"tundra": 3, "*": 0.5}, "min_tier": "hamlet", "min_reputation": 5, "requires_mods": ["expanse"]},
				"giver": {"jobs": ["tailor", "herder"]},
				"objectives": [{"kind": "deliver", "items": ["minecraft:brown_wool"], "count": [16, 32], "label": "Bring {count} {item}"}],
				"text": {"title": ["Wool for {settlement}", "Shear a mammoth"], "summary": "{giver} wants wool."},
				"reward": {"coin": [10, 20], "market_premium": 1.2, "items": [{"item": "minecraft:bread", "count": 3}], "reputation": 2},
				"deadline_days": 2, "cooldown_days": 5, "repeatable": false, "turn_in": "giver"
			}""");
		assertEquals(QuestType.GATHERING, t.type());
		assertEquals(3.0 * 4, t.weightFor(Culture.TUNDRA), 1e-9);
		assertEquals(0.5 * 4, t.weightFor(Culture.DESERT), 1e-9);
		assertEquals(SettlementTier.HAMLET, t.when().minTier());
		assertEquals(5, t.when().minReputation());
		assertEquals(List.of("expanse"), t.when().requiresMods());
		assertEquals(ObjectiveKind.DELIVER, t.objectives().getFirst().kind());
		assertEquals(16, t.objectives().getFirst().count().min());
		assertEquals(32, t.objectives().getFirst().count().max());
		assertEquals(2, t.text().title().size());
		assertEquals(1, t.text().summary().size());
		assertEquals(1.2, t.reward().marketPremium(), 1e-9);
		assertFalse(t.repeatable());
		assertEquals(QuestType.Trigger.NONE, t.when().trigger());
		assertTrue(TemplateValidator.validate(t, TemplateValidator.Lookup.ANY).isEmpty());
	}

	@Test
	void supplyTemplatesDefaultToTheStalledTrigger() {
		QuestTemplate t = QuestTestKit.parse("""
			{"type": "supply", "objectives": [{"kind": "deliver", "item": "$goods_item", "count": "$missing*0.5"}], "text": {"title": "x"}}""");
		assertEquals(QuestType.Trigger.STALLED, t.when().trigger());
		Amount a = t.objectives().getFirst().count();
		assertEquals("missing", a.variable());
		assertEquals(12, a.resolve(RandomSource.create(1), Map.of("missing", 24)));
		assertTrue(TemplateValidator.validate(t, TemplateValidator.Lookup.ANY).isEmpty());
	}

	@Test
	void errorsNameTheField() {
		TemplateParser.TemplateException e = assertThrows(TemplateParser.TemplateException.class, () -> QuestTestKit.parse("""
			{"type": "bounty", "objectives": [{"kind": "kill", "entity": "monster", "count": "lots"}], "text": {"title": "x"}}"""));
		assertEquals("objectives[0].count", e.path());
		e = assertThrows(TemplateParser.TemplateException.class, () -> QuestTestKit.parse("""
			{"type": "treasure", "objectives": [], "text": {"title": "x"}}"""));
		assertEquals("type", e.path());
		e = assertThrows(TemplateParser.TemplateException.class, () -> QuestTestKit.parse("""
			{"type": "courier", "objectives": [{"kind": "courier"}]}"""));
		assertEquals("text", e.path());
		e = assertThrows(TemplateParser.TemplateException.class, () -> QuestTestKit.parse("""
			{"type": "courier", "when": {"cultures": ["atlantis"]}, "objectives": [{"kind": "courier"}], "text": {"title": "x"}}"""));
		assertEquals("when.cultures", e.path());
	}

	@Test
	void validatorCatchesTemplatesThatCannotBind() {
		QuestTemplate goodsOutsideStall = QuestTestKit.parse("""
			{"type": "gathering", "objectives": [{"kind": "deliver", "item": "$goods_item"}], "text": {"title": "x"}}""");
		assertFalse(TemplateValidator.validate(goodsOutsideStall, TemplateValidator.Lookup.ANY).isEmpty());

		QuestTemplate stallWithoutGoods = QuestTestKit.parse("""
			{"type": "supply", "objectives": [{"kind": "deliver", "item": "minecraft:stone"}], "text": {"title": "x"}}""");
		assertFalse(TemplateValidator.validate(stallWithoutGoods, TemplateValidator.Lookup.ANY).isEmpty());

		QuestTemplate badPlaceholder = QuestTestKit.parse("""
			{"type": "courier", "objectives": [{"kind": "courier"}], "text": {"title": "To {destination}"}}""");
		assertTrue(TemplateValidator.validate(badPlaceholder, TemplateValidator.Lookup.ANY).stream().anyMatch(p -> p.contains("{destination}")));

		QuestTemplate beastWithoutLair = QuestTestKit.parse("""
			{"type": "bounty", "when": {"trigger": "none"}, "objectives": [{"kind": "slay", "entity": "minecraft:zombie", "name": "Bob"}],
			 "text": {"title": "x"}}""");
		assertFalse(TemplateValidator.validate(beastWithoutLair, TemplateValidator.Lookup.ANY).isEmpty());

		QuestTemplate unknownItem = QuestTestKit.parse("""
			{"type": "gathering", "objectives": [{"kind": "deliver", "item": "minecraft:unobtainium"}], "text": {"title": "x"}}""");
		assertFalse(TemplateValidator.validate(unknownItem, VANILLA).isEmpty());
	}

	@Test
	void templatesForAbsentModsAreDisabledNotBroken() {
		QuestTemplate t = QuestTestKit.parse("""
			{"type": "gathering", "when": {"requires_mods": ["expanse"]},
			 "objectives": [{"kind": "deliver", "item": "expanse:venison"}], "text": {"title": "x"}}""");
		TemplateValidator.Lookup noExpanse = new TemplateValidator.Lookup() {
			@Override
			public boolean item(Identifier id) {
				return VANILLA.item(id);
			}

			@Override
			public boolean entity(Identifier id) {
				return VANILLA.entity(id);
			}

			@Override
			public boolean modLoaded(String modId) {
				return !modId.equals("expanse");
			}
		};
		assertFalse(TemplateValidator.enabled(t, noExpanse));
		assertTrue(TemplateValidator.validate(t, noExpanse).isEmpty(), "an Expanse id is fine in a template that requires Expanse");
	}

	@Test
	void textFillsPlaceholdersAndBearings() {
		assertEquals("Mara needs 12 × Spruce Log in Ashford",
			QuestText.fill("{giver} needs {count} × {item} in {settlement}", Map.of("giver", "Mara", "count", "12", "item", "Spruce Log", "settlement",
				"Ashford")));
		assertEquals("The village council asks {nobody}", QuestText.fill("{giver} asks {nobody}", Map.of("giver", "the village council")));
		assertEquals("north", QuestText.direction(0, 0, 0, -100));
		assertEquals("south-east", QuestText.direction(0, 0, 100, 100));
		assertEquals("west", QuestText.direction(0, 0, -100, 5));
		assertEquals(List.of("a", "b_c"), QuestText.placeholders("x {a} y {b_c} {not valid}"));
	}
}
