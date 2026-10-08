package dev.voidmc.hearth.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.QuestType;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.quest.gen.GenerationContext;
import dev.voidmc.hearth.quest.gen.GoodsItems;
import dev.voidmc.hearth.quest.gen.QuestGenerator;
import dev.voidmc.hearth.quest.model.Objective;
import dev.voidmc.hearth.quest.model.ObjectiveKind;
import dev.voidmc.hearth.quest.model.QuestInstance;
import dev.voidmc.hearth.quest.model.QuestStatus;
import dev.voidmc.hearth.quest.model.SettlementQuests;
import dev.voidmc.hearth.quest.template.QuestTemplate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GeneratorTest {
	private static Collection<QuestTemplate> templates;

	@BeforeAll
	static void load() throws Exception {
		QuestTestKit.bootstrap();
		templates = QuestTestKit.shippedTemplates().values();
	}

	@Test
	void stalledStoneBecomesASupplyQuestForConcreteStone() {
		SimpleSettlement s = QuestTestKit.village(1, "Ashford", Culture.MOORLAND, BlockPos.ZERO);
		SettlementQuests data = new SettlementQuests();
		data.addNeed(new SettlementQuests.StalledNeed(3, Goods.STONE, 100, 9, "cottage"));
		GenerationContext ctx = QuestTestKit.context(s, data);
		int added = QuestGenerator.postNeeds(ctx, templates);
		assertEquals(1, added);
		QuestInstance q = data.offers.getFirst();
		assertEquals(QuestType.SUPPLY, q.type);
		Objective o = q.objectives.getFirst();
		assertEquals(ObjectiveKind.DELIVER, o.kind);
		GoodsItems.Choice choice = GoodsItems.find(Identifier.parse(o.target));
		assertNotNull(choice, "asks for a known stone item: " + o.target);
		assertEquals(Goods.STONE, GoodsItems.goodsOf(choice.item()));
		assertEquals(Math.min(QuestConfig.DEFAULTS.supplyMaxItems(), Mth.positiveCeilDiv(100, choice.units())), o.count,
			"enough items to cover the shortfall, up to the cap");
		assertEquals("stone", o.data.getStringOr(Objective.Keys.GOODS, ""));
		assertEquals(3, o.data.getIntOr(Objective.Keys.PLOT, -1));
		assertTrue(q.text.title().contains("cottage") || q.text.summary().contains("cottage"), "the text names what is being built: " + q.text);
		assertTrue(q.reward.coin() > 0, "paid above market");
		assertFalse(q.text.title().contains("{"), "placeholders filled: " + q.text.title());
		// Asking again does not duplicate the offer.
		assertEquals(0, QuestGenerator.postNeeds(ctx, templates));
	}

	@Test
	void supplyUsesTheSettlementsOwnMaterials() {
		SimpleSettlement s = QuestTestKit.village(2, "Kettlewell", Culture.DESERT, BlockPos.ZERO);
		SettlementQuests data = new SettlementQuests();
		data.addNeed(new SettlementQuests.StalledNeed(0, Goods.WOOD, 40, 9, "courtyard house"));
		QuestGenerator.postNeeds(QuestTestKit.context(s, data), templates);
		String item = data.offers.getFirst().objectives.getFirst().target;
		assertTrue(item.equals("minecraft:acacia_log") || item.equals("expanse:palm_log"), "desert towns build in acacia or palm: " + item);
	}

	@Test
	void deliveringFinishesTheSupplyQuest() {
		SimpleSettlement s = QuestTestKit.village(3, "Oakhurst", Culture.TEMPERATE, BlockPos.ZERO);
		SettlementQuests data = new SettlementQuests();
		data.addNeed(new SettlementQuests.StalledNeed(0, Goods.GLASS, 10, 9, "chapel"));
		QuestGenerator.postNeeds(QuestTestKit.context(s, data), templates);
		QuestInstance q = data.offers.getFirst().copy();
		q.status = QuestStatus.ACTIVE;
		Objective o = q.objectives.getFirst();
		FakeInventory inv = new FakeInventory(Map.of(o.target, 4));
		List<QuestProgress.Delivered> first = QuestProgress.handIn(q, inv);
		assertEquals(4, first.getFirst().amount());
		assertEquals(QuestStatus.ACTIVE, q.status, "partial delivery keeps it going");
		assertNull(QuestProgress.settle(q));
		inv.items.put(o.target, 64);
		QuestProgress.handIn(q, inv);
		assertTrue(o.done());
		assertEquals(64 - (o.count - 4), inv.items.get(o.target), "takes only what is still wanted");
		assertEquals(QuestStatus.READY, QuestProgress.settle(q));
	}

	@Test
	void anEmptyNeedListPostsNoSupply() {
		SimpleSettlement s = QuestTestKit.village(4, "Larkspur", Culture.ALPINE, BlockPos.ZERO);
		SettlementQuests data = new SettlementQuests();
		QuestGenerator.refresh(QuestTestKit.context(s, data), templates, true);
		assertTrue(data.offers.stream().noneMatch(q -> q.type == QuestType.SUPPLY));
		assertFalse(data.offers.isEmpty(), "the board fills with everyday quests");
		assertTrue(data.offers.size() <= QuestConfig.DEFAULTS.offersPerBoard());
	}

	@Test
	void monsterPressurePostsABounty() {
		SimpleSettlement s = QuestTestKit.village(5, "Thornwick", Culture.TEMPERATE, BlockPos.ZERO);
		SettlementQuests data = new SettlementQuests();
		data.pressure = QuestConfig.DEFAULTS.pressureThreshold() + 3;
		QuestGenerator.refresh(QuestTestKit.context(s, data), templates, true);
		QuestInstance bounty = data.offers.stream().filter(q -> q.type == QuestType.BOUNTY
			&& q.objectives.getFirst().kind == ObjectiveKind.KILL).findFirst().orElse(null);
		assertNotNull(bounty, "a night bounty is posted: " + data.offers);
		assertEquals(0, data.pressure, "posting spends the pressure");
		assertTrue(bounty.objectives.getFirst().data.getBooleanOr(Objective.Keys.NIGHT, false)
			|| !bounty.objectives.getFirst().target.isEmpty());
	}

	@Test
	void couriersNeedAnotherSettlement() {
		SimpleSettlement home = QuestTestKit.village(6, "Millbrook", Culture.TEMPERATE, BlockPos.ZERO);
		QuestTemplate courier = templates.stream().filter(t -> t.id().getPath().equals("courier/parcel")).findFirst().orElseThrow();
		assertNull(QuestGenerator.bind(courier, QuestTestKit.context(home, new SettlementQuests()), null), "no destination, no courier");

		SimpleSettlement far = QuestTestKit.village(7, "Harrowgate", Culture.TEMPERATE, new BlockPos(600, 70, -800));
		GenerationContext ctx = QuestTestKit.context(home, new SettlementQuests(), 10, List.of(far), new ArrayList<>());
		QuestInstance q = QuestGenerator.bind(courier, ctx, null);
		assertNotNull(q);
		Objective o = q.objectives.getFirst();
		assertEquals(ObjectiveKind.COURIER, o.kind);
		assertEquals(far.id(), o.settlement);
		assertTrue(q.text.summary().contains("Harrowgate"));
		assertTrue(q.text.summary().contains("north-east"), "gives a bearing: " + q.text.summary());
	}

	@Test
	void explorationAsksForASearchThenUsesTheResult() {
		SimpleSettlement s = QuestTestKit.village(8, "Fernhollow", Culture.KARST, BlockPos.ZERO);
		SettlementQuests data = new SettlementQuests();
		QuestTemplate karst = templates.stream().filter(t -> t.id().getPath().equals("exploration/karst_temple")).findFirst().orElseThrow();
		List<List<Identifier>> searches = new ArrayList<>();
		assertNull(QuestGenerator.bind(karst, QuestTestKit.context(s, data, 10, List.of(), searches), null));
		assertEquals(List.of(List.of(Identifier.parse("expanse:karst_pagoda"))), searches, "a search is queued off the hot path");

		data.putTarget(new SettlementQuests.LocatedTarget(Identifier.parse("expanse:karst_pagoda"), true, new BlockPos(900, 80, 100),
			new BlockPos(860, 60, 60), new BlockPos(940, 120, 140), 10));
		QuestInstance q = QuestGenerator.bind(karst, QuestTestKit.context(s, data, 10, List.of(), new ArrayList<>()), null);
		assertNotNull(q);
		Objective o = q.objectives.getFirst();
		assertEquals(ObjectiveKind.EXPLORE, o.kind);
		assertTrue(o.data.getBooleanOr(Objective.Keys.GRANT_REQUIRED, false), "bring back proof");
		assertTrue(QuestProgress.insideBox(o, new BlockPos(900, 90, 100)));
		assertFalse(QuestProgress.insideBox(o, new BlockPos(700, 90, 100)));
	}

	@Test
	void rewardsNeverExceedWhatTheTreasuryCanPromise() {
		SimpleSettlement poor = QuestTestKit.village(9, "Poorhaven", Culture.TEMPERATE, BlockPos.ZERO);
		poor.withdraw(poor.treasury() - 50);
		SettlementQuests data = new SettlementQuests();
		data.addNeed(new SettlementQuests.StalledNeed(0, Goods.METAL, 200, 9, "smithy"));
		GenerationContext ctx = QuestTestKit.context(poor, data);
		QuestGenerator.refresh(ctx, templates, true);
		long promised = data.offers.stream().mapToLong(q -> q.reward.coin()).sum();
		assertTrue(promised <= Math.floor(50 * QuestConfig.DEFAULTS.treasuryShare()), "promised " + promised + " from a treasury of 50");
		assertEquals(0, QuestGenerator.budget(ctx), "nothing left to promise");
	}

	@Test
	void personalGiftsFollowTheGiversTaste() {
		SimpleSettlement s = QuestTestKit.village(10, "Ashby", Culture.TEMPERATE, BlockPos.ZERO);
		QuestTemplate gift = templates.stream().filter(t -> t.id().getPath().equals("personal/gift")).findFirst().orElseThrow();
		QuestInstance q = QuestGenerator.bind(gift, QuestTestKit.context(s, new SettlementQuests()), null);
		assertNotNull(q);
		assertTrue(q.giver.resident().isPresent(), "a resident asks, not the council");
		assertTrue(q.rules.exclusive());
		assertFalse(q.objectives.getFirst().target.isEmpty());
	}

	@Test
	void boardsAreTheSameForEveryoneOnADay() {
		SimpleSettlement a = QuestTestKit.village(11, "Same", Culture.MARITIME, BlockPos.ZERO);
		SimpleSettlement b = QuestTestKit.village(11, "Same", Culture.MARITIME, BlockPos.ZERO);
		SettlementQuests da = new SettlementQuests();
		SettlementQuests db = new SettlementQuests();
		QuestGenerator.refresh(QuestTestKit.context(a, da), templates, true);
		QuestGenerator.refresh(QuestTestKit.context(b, db), templates, true);
		assertEquals(da.offers.stream().map(q -> q.template).toList(), db.offers.stream().map(q -> q.template).toList());
	}

	@Test
	void offersExpireAndCooldownsHold() {
		SimpleSettlement s = QuestTestKit.village(12, "Brook", Culture.TEMPERATE, BlockPos.ZERO);
		SettlementQuests data = new SettlementQuests();
		QuestGenerator.refresh(QuestTestKit.context(s, data, 10, List.of(), new ArrayList<>()), templates, true);
		List<Identifier> first = data.offers.stream().map(q -> q.template).toList();
		QuestGenerator.refresh(QuestTestKit.context(s, data, 11, List.of(), new ArrayList<>()), templates, false);
		for (QuestInstance q : data.offers) {
			if (q.timing.offeredDay() == 11) {
				assertFalse(first.contains(q.template), "a template on cooldown is not offered again the next day: " + q.template);
			}
		}
		QuestGenerator.refresh(QuestTestKit.context(s, data, 10 + QuestConfig.DEFAULTS.offerLifetimeDays() + 1, List.of(), new ArrayList<>()),
			templates, false);
		assertTrue(data.offers.stream().noneMatch(q -> q.timing.offeredDay() == 10), "day-10 offers have come down");
	}

	/** A pretend inventory: item id → count. */
	static final class FakeInventory implements QuestProgress.ItemSource {
		final Map<String, Integer> items;

		FakeInventory(Map<String, Integer> items) {
			this.items = new java.util.HashMap<>(items);
		}

		@Override
		public int count(String target) {
			return this.items.getOrDefault(target, 0);
		}

		@Override
		public int take(String target, int max) {
			int have = this.items.getOrDefault(target, 0);
			int n = Math.min(have, max);
			this.items.put(target, have - n);
			return n;
		}
	}
}
