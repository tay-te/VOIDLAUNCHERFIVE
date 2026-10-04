package dev.voidmc.hearth.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.api.StandingService.Title;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.quest.gen.Chains;
import dev.voidmc.hearth.quest.gen.GenerationContext;
import dev.voidmc.hearth.quest.gen.GoodsItems;
import dev.voidmc.hearth.quest.gen.Prospecting;
import dev.voidmc.hearth.quest.gen.QuestGenerator;
import dev.voidmc.hearth.quest.gen.TownState;
import dev.voidmc.hearth.quest.model.Objective;
import dev.voidmc.hearth.quest.model.ObjectiveKind;
import dev.voidmc.hearth.quest.model.QuestInstance;
import dev.voidmc.hearth.quest.model.QuestType;
import dev.voidmc.hearth.quest.model.SettlementQuests;
import dev.voidmc.hearth.quest.model.SettlementQuests.Happening;
import dev.voidmc.hearth.quest.service.QuestTracker;
import dev.voidmc.hearth.quest.service.Standings;
import dev.voidmc.hearth.quest.template.QuestTemplate;
import dev.voidmc.hearth.quest.template.TemplateValidator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The living world's quests (docs/LIVING_WORLD.md §4–§7): contracts, happenings, the officials' state, standing, the law. */
class LivingWorldTest {
	private static Collection<QuestTemplate> templates;
	private static Map<Identifier, QuestTemplate> byId;

	@BeforeAll
	static void load() throws Exception {
		QuestTestKit.bootstrap();
		byId = QuestTestKit.shippedTemplates();
		templates = byId.values();
	}

	/** A settlement with resource sites, as the sim's survey would give it. */
	static final class SurveyedSettlement extends SimpleSettlement {
		final List<ResourceSite> sites = new ArrayList<>();

		SurveyedSettlement(long id, String name, Culture culture, BlockPos center) {
			super(id, name, culture, center, -100);
		}

		@Override
		public List<ResourceSite> sites() {
			return this.sites;
		}
	}

	private static SurveyedSettlement town(long id, Culture culture) {
		SurveyedSettlement s = new SurveyedSettlement(id, "Town" + id, culture, new BlockPos((int) id * 10, 64, 0));
		SimpleSettlement v = QuestTestKit.village(id, "x", culture, BlockPos.ZERO);
		v.residents().forEach(s::addResident);
		s.deposit(10_000);
		return s;
	}

	private static SimpleSettlement.SimplePlot plot(SimpleSettlement s, String archetype, BlockPos origin) {
		SimpleSettlement.SimplePlot p = s.addPlot(new PlotSpec(Hearth.id(archetype), Hearth.id("temperate"), 1L, origin, 9, 11, Direction.SOUTH, 12, 1,
			new CompoundTag()));
		p.state = PlotState.BUILT;
		return p;
	}

	// ------------------------------------------------------------------ goods and contracts

	@Test
	void everyGoodHasItemsInEveryCultureAndTheyExist() {
		for (Culture culture : Culture.values()) {
			for (Goods goods : Goods.values()) {
				assertFalse(GoodsItems.choices(culture, goods).isEmpty(), goods + " in " + culture);
			}
		}
		for (Identifier item : GoodsItems.allItems()) {
			if (item.getNamespace().equals("minecraft")) {
				assertTrue(BuiltInRegistries.ITEM.containsKey(item), "a vanilla goods item that does not exist: " + item);
			}
		}
		// salt has only Expanse's salt block: without Expanse, no salt item, and the quest is not made rather than asking for air
		assertNull(GoodsItems.pick(Culture.TEMPERATE, Goods.SALT, net.minecraft.util.RandomSource.create(1), id -> !id.getNamespace().equals("expanse")));
	}

	@Test
	void everyGoodHasASupplyAndAContractTemplate() {
		for (Goods goods : Goods.values()) {
			assertTrue(templates.stream().anyMatch(t -> t.when().trigger() == QuestType.Trigger.SHORTAGE
				&& (t.when().goods().isEmpty() || t.when().goods().contains(goods))), "a contract covers " + goods);
		}
	}

	@Test
	void workshopsShortOfInputsPostStandingContracts() {
		SurveyedSettlement s = town(10, Culture.TEMPERATE);
		s.addResident(QuestTestKit.resident(new UUID(10, 99), "Smelt", JobType.SMELTER, AgeStage.ADULT));
		List<SettlementQuests.StalledNeed> shortages = Chains.shortages(s, 16, 5);
		assertTrue(shortages.stream().anyMatch(n -> n.goods() == Goods.ORE && n.source().equals("smelter") && n.missing() == 16),
			"the smelter wants ore: " + shortages);
		assertTrue(shortages.stream().allMatch(SettlementQuests.StalledNeed::contract));

		// well stocked, the smelter wants nothing
		s.stockpile().add(Goods.ORE, 12);
		assertFalse(Chains.shortages(s, 16, 5).stream().anyMatch(n -> n.goods() == Goods.ORE), "12 of 16 is more than half: not short");

		s.stockpile().take(Goods.ORE, 12);
		SettlementQuests data = new SettlementQuests();
		GenerationContext ctx = QuestTestKit.context(s, data);
		QuestGenerator.refresh(ctx, templates, true);
		List<QuestInstance> contracts = data.offers.stream().filter(q -> q.type == QuestType.CONTRACT).toList();
		assertEquals(QuestConfig.Living.DEFAULTS.contractOffers(), contracts.size(), "contracts up to the cap: " + data.offers);
		for (QuestInstance q : contracts) {
			Objective o = q.objectives.getFirst();
			assertFalse(o.data.getStringOr(Objective.Keys.SOURCE, "").isEmpty(), "a contract names its workshop");
			assertTrue(o.data.contains(Objective.Keys.PLOT));
			assertFalse(q.text.title().contains("{"), q.text.title());
			assertTrue(q.reward.coin() > 0);
		}
		// a delivery satisfies the workshop's need, not a stalled build's
		Objective o = contracts.getFirst().objectives.getFirst();
		Goods goods = Goods.valueOf(o.data.getStringOr(Objective.Keys.GOODS, "").toUpperCase());
		int before = data.needs.stream().filter(n -> n.goods() == goods && n.contract()).mapToInt(SettlementQuests.StalledNeed::missing).sum();
		data.satisfy(o.data.getIntOr(Objective.Keys.PLOT, -1), goods, o.data.getStringOr(Objective.Keys.SOURCE, ""), before);
		assertTrue(data.needs.stream().noneMatch(n -> n.goods() == goods && n.contract()), "met in full, the need is gone");
	}

	@Test
	void guildContractsWantAFriend() {
		QuestTemplate guild = byId.get(Identifier.parse("hearth:contract/guild_order"));
		assertNotNull(guild);
		assertEquals(Title.FRIEND, guild.when().minTitle());
	}

	// ------------------------------------------------------------------ happenings

	@Test
	void aFireCallsTheBucketBrigadeToTheBurningPlot() {
		SurveyedSettlement s = town(11, Culture.TEMPERATE);
		SimpleSettlement.SimplePlot house = plot(s, "house/cottage", new BlockPos(120, 64, 30));
		SettlementQuests data = new SettlementQuests();
		Happening h = data.addHappening(Happening.FIRE, 5, house.id(), new BlockPos(124, 64, 35), -1, 9, "", "");
		GenerationContext ctx = QuestTestKit.context(s, data);
		assertEquals(1, QuestGenerator.postEvents(ctx, templates));
		QuestInstance q = data.offers.getFirst();
		assertEquals(QuestType.DISASTER, q.type);
		assertEquals(h.id(), SettlementQuests.happeningOf(q));
		Objective o = q.objectives.getFirst();
		assertEquals(ObjectiveKind.EXTINGUISH, o.kind);
		BlockPos min = BlockPos.of(o.data.getLongOr(Objective.Keys.BOX_MIN, 0L));
		BlockPos max = BlockPos.of(o.data.getLongOr(Objective.Keys.BOX_MAX, 0L));
		assertTrue(min.getX() <= 120 && max.getX() >= 129 && min.getZ() <= 30 && max.getZ() >= 41, "the box covers the plot: " + min + " " + max);
		assertTrue(q.text.title().contains("cottage"), q.text.title());
		assertFalse(q.rules.exclusive(), "the whole town can join the brigade");
		// answered once
		assertEquals(0, QuestGenerator.postEvents(ctx, templates));
		// resolved, the offer comes down
		assertTrue(data.resolve(h.id()));
		assertTrue(data.offers.isEmpty());
	}

	@Test
	void aCollapseAsksForPropsThenARescueOfTheTrappedMiners() {
		SurveyedSettlement s = town(12, Culture.ALPINE);
		s.addResident(QuestTestKit.resident(new UUID(12, 90), "Dig", JobType.MINER, AgeStage.ADULT));
		SimpleSettlement.SimplePlot mine = plot(s, "extract/mine", new BlockPos(200, 70, 0));
		s.addPoi(new dev.voidmc.hearth.api.Poi(dev.voidmc.hearth.api.PoiType.WORK_FACE, new BlockPos(230, 58, 4), Direction.NORTH, JobType.MINER));
		SettlementQuests data = new SettlementQuests();
		data.addHappening(Happening.COLLAPSE, 5, mine.id(), QuestGenerator.plotPlace(s, mine), -1, 1, "ore", "Dig Tester");
		QuestGenerator.postEvents(QuestTestKit.context(s, data), templates);
		QuestInstance q = data.offers.getFirst();
		assertTrue(q.rules.sequential());
		assertEquals(ObjectiveKind.DELIVER, q.objectives.get(0).kind);
		Objective rescue = q.objectives.get(1);
		assertEquals(ObjectiveKind.RESCUE, rescue.kind);
		assertEquals(new BlockPos(230, 58, 4), rescue.pos, "the rescue points at the mine's working face");
		assertEquals(1, rescue.data.getListOrEmpty(QuestGenerator.VICTIMS).size(), "the trapped miner, by id");
		assertTrue(rescue.label.contains("Dig Tester"), rescue.label);
		assertTrue(q.rules.exclusive());
	}

	@Test
	void aDepartingCaravanWantsAnEscortAndHighwaymenWaitOnTheRoad() {
		SurveyedSettlement s = town(13, Culture.TEMPERATE);
		SimpleSettlement other = QuestTestKit.village(14, "Farbridge", Culture.TEMPERATE, new BlockPos(800, 64, 0));
		SettlementQuests data = new SettlementQuests();
		data.addHappening(Happening.CARAVAN_DEPARTED, 5, -1, s.center(), other.id(), 120, "ore", "ore and timber");
		GenerationContext ctx = QuestTestKit.context(s, data, 10, List.of(other), new ArrayList<>());
		assertEquals(1, QuestGenerator.postEvents(ctx, templates));
		QuestInstance q = data.offers.getFirst();
		assertEquals(QuestType.CARAVAN, q.type);
		Objective escort = q.objectives.stream().filter(o -> o.kind == ObjectiveKind.ESCORT).findFirst().orElseThrow();
		assertEquals(other.id(), escort.settlement, "the caravan goes where the sim's caravan goes");
		assertEquals(3, escort.data.getIntOr(Objective.Keys.CARAVAN, 0));
		assertEquals("ore", escort.data.getStringOr(Objective.Keys.CARGO, ""));
		assertEquals("robbery", escort.data.getStringOr(Objective.Keys.CRIME, ""), "killing the caravan's animals is robbery");
		Objective ambush = q.objectives.stream().filter(o -> o.kind == ObjectiveKind.AMBUSH).findFirst().orElseThrow();
		assertEquals(other.center().asLong(), ambush.data.getLongOr(Objective.Keys.ROUTE_TO, 0L));
		assertTrue(ambush.pos.getX() > 100 && ambush.pos.getX() < 760, "the highwaymen wait partway along: " + ambush.pos);
		assertTrue(q.text.summary().contains("Farbridge") && q.text.summary().contains("ore and timber"), q.text.summary());

		// on the road: nothing until past half way, then a little ahead of the player
		assertNull(QuestTracker.alongRoute(ambush, new BlockPos(300, 64, 5)));
		BlockPos at = QuestTracker.alongRoute(ambush, new BlockPos(600, 64, 10));
		assertNotNull(at);
		assertTrue(at.getX() > 600, "ahead of the player toward the destination: " + at);
		assertNull(QuestTracker.alongRoute(ambush, new BlockPos(600, 64, 400)), "far off the road: no ambush");
	}

	@Test
	void aRobbedCaravanSendsYouAfterTheRobbersOrTheCargo() {
		SurveyedSettlement s = town(15, Culture.STEPPE);
		SimpleSettlement other = QuestTestKit.village(16, "Dunmere", Culture.STEPPE, new BlockPos(0, 64, 900));
		SettlementQuests data = new SettlementQuests();
		data.addHappening(Happening.CARAVAN_ROBBED, 5, -1, new BlockPos(0, Objective.SURFACE_Y, 450), other.id(), 80, "wool", "wool and leather");
		QuestGenerator.postEvents(QuestTestKit.context(s, data, 10, List.of(other), new ArrayList<>()), templates);
		QuestInstance q = data.offers.getFirst();
		assertEquals(QuestType.CARAVAN, q.type);
		Objective ambush = q.objectives.getFirst();
		assertEquals(ObjectiveKind.AMBUSH, ambush.kind);
		assertTrue(Math.abs(ambush.pos.getZ() - 450) < 200, "the robbers are on the road between the towns: " + ambush.pos);
	}

	@Test
	void aSpentQuarryAsksForANewOneOfTheSameKind() {
		SurveyedSettlement s = town(17, Culture.MOORLAND);
		SettlementQuests data = new SettlementQuests();
		data.addHappening(Happening.DEPLETED, 5, -1, new BlockPos(40, Objective.SURFACE_Y, 40), -1, 400, "stone", "stone");
		QuestGenerator.postEvents(QuestTestKit.context(s, data), templates);
		QuestInstance q = data.offers.getFirst();
		Objective o = q.objectives.getFirst();
		assertEquals(ObjectiveKind.PROSPECT, o.kind);
		assertEquals("stone", o.data.getStringOr(Objective.Keys.RESOURCE, ""));
		assertTrue(q.text.title().contains("quarry"), q.text.title());
		assertTrue(o.data.getIntOr(Objective.Keys.MAX_DISTANCE, 0) > o.data.getIntOr(Objective.Keys.MIN_DISTANCE, 0));
	}

	@Test
	void aDecliningTownAsksForHelp() {
		SurveyedSettlement s = town(18, Culture.TEMPERATE);
		SimpleSettlement other = QuestTestKit.village(19, "Yarrow", Culture.TEMPERATE, new BlockPos(600, 64, 0));
		SettlementQuests data = new SettlementQuests();
		data.decliningSince = 4;
		data.addHappening(Happening.DECLINING, 5, -1, s.center(), -1, 1, "", "the mine is spent");
		QuestGenerator.postEvents(QuestTestKit.context(s, data, 10, List.of(other), new ArrayList<>()), templates);
		QuestInstance q = data.offers.getFirst();
		Objective o = q.objectives.getFirst();
		assertTrue(o.kind == ObjectiveKind.PAY || o.kind == ObjectiveKind.COURIER, "a donation or a letter for new trade: " + o.kind);
		if (o.kind == ObjectiveKind.PAY) {
			assertTrue(o.count > 0);
			assertTrue(o.label.contains("bits"), "the sum is written out: " + o.label);
		}
	}

	@Test
	void happeningsMergeAndAreForgotten() {
		SettlementQuests data = new SettlementQuests();
		Happening a = data.addHappening(Happening.FIRE, 1, 3, BlockPos.ZERO, -1, 4, "", "");
		Happening b = data.addHappening(Happening.FIRE, 1, 3, BlockPos.ZERO, -1, 5, "", "");
		assertEquals(a.id(), b.id());
		assertEquals(9, b.amount());
		assertEquals(1, data.happenings.size());
		data.forgetOlderThan(10, 4);
		assertTrue(data.happening(a.id()).resolved());
		for (int i = 0; i < 20; i++) {
			data.addHappening(Happening.BLAST, i, i, BlockPos.ZERO, -1, 1, "", "");
		}
		assertEquals(SettlementQuests.HAPPENING_LIMIT, data.happenings.size(), "bounded");
	}

	// ------------------------------------------------------------------ the officials and the town's state

	@Test
	void townStateReadsTheTown() {
		SurveyedSettlement s = town(20, Culture.TEMPERATE);
		SettlementQuests data = new SettlementQuests();
		TownState state = TownState.of(s, data, 0);
		assertFalse(state.test("stalled"));
		assertTrue(state.test("!stalled"));
		assertTrue(state.test("job:smith"));
		assertFalse(state.test("job:miner"));
		assertFalse(state.test("site:ore"));
		data.addNeed(new SettlementQuests.StalledNeed(1, Goods.STONE, 10, 1, "cottage"));
		data.crime = TownState.CRIME_THRESHOLD;
		s.sites.add(new ResourceSite(1, ResourceSite.Kind.ORE, BlockPos.ZERO, BoundingBox.fromCorners(BlockPos.ZERO, BlockPos.ZERO), 100, 0, 0, new float[0],
			null, -1));
		state = TownState.of(s, data, 7);
		assertTrue(state.all(List.of("stalled", "crime", "damaged", "depleted")));
		assertFalse(state.test("site:ore"), "a spent site is not a site");
		assertNull(TownState.problem("job:reeve"));
		assertNull(TownState.problem("!site:clay"));
		assertNotNull(TownState.problem("job:wizard"));
		assertNotNull(TownState.problem("haunted"));
	}

	@Test
	void theMasterBuildersLineFollowsTheStalledBuilds() {
		SurveyedSettlement s = town(21, Culture.TEMPERATE);
		SettlementQuests data = new SettlementQuests();
		GenerationContext ctx = QuestTestKit.context(s, data);
		QuestTemplate haul = byId.get(Identifier.parse("hearth:official/builder/haul_stone"));
		assertNotNull(haul);
		assertEquals(List.of("stalled"), haul.when().requires());
		// quiet town: the builder's line does not come up
		for (int i = 0; i < 6; i++) {
			data.offers.clear();
			data.cooldowns.clear();
			QuestGenerator.refresh(QuestTestKit.context(s, data, 20 + i, List.of(), new ArrayList<>()), List.of(haul), true);
			assertTrue(data.offers.isEmpty(), "no stall, no hauling");
		}
		data.addNeed(new SettlementQuests.StalledNeed(1, Goods.STONE, 10, 1, "cottage"));
		QuestGenerator.refresh(ctx, List.of(haul), true);
		assertEquals(1, data.offers.size());
		QuestInstance q = data.offers.getFirst();
		assertEquals(QuestType.OFFICIAL, q.type);
		assertEquals("builder", q.giver.job(), "the master builder gives it, not a passer-by");
		// later steps: prerequisites and a title
		QuestTemplate finishing = byId.get(Identifier.parse("hearth:official/builder/finishing"));
		assertEquals(List.of(Identifier.parse("hearth:official/builder/scaffold_timber")), finishing.when().prerequisites());
		assertEquals(Title.FRIEND, finishing.when().minTitle());
	}

	@Test
	void everyOfficialHasALineOfAtLeastThreeSteps() {
		for (String official : List.of("reeve", "guard", "priest", "builder", "lord")) {
			long steps = templates.stream().filter(t -> t.id().getPath().startsWith("official/" + official + "/")).count();
			assertTrue(steps >= 3, official + " has " + steps + " steps");
			assertTrue(templates.stream().filter(t -> t.id().getPath().startsWith("official/" + official + "/"))
				.anyMatch(t -> !t.when().prerequisites().isEmpty()), official + "'s line is a chain");
		}
	}

	@Test
	void everyHappeningHasAnAnswer() {
		for (String kind : Happening.KINDS) {
			assertTrue(templates.stream().anyMatch(t -> t.when().events().contains(kind)), "a template answers " + kind);
		}
	}

	// ------------------------------------------------------------------ standing and the law

	@Test
	void titlesFollowStandingAndGateQuests() {
		assertEquals(Title.OUTLAW, Standings.titleOf(-80, false));
		assertEquals(Title.DISTRUSTED, Standings.titleOf(-20, false));
		assertEquals(Title.STRANGER, Standings.titleOf(0, false));
		assertEquals(Title.KNOWN, Standings.titleOf(20, false));
		assertEquals(Title.FRIEND, Standings.titleOf(40, false));
		assertEquals(Title.BURGHER, Standings.titleOf(20, true));
		assertEquals(Title.HERO, Standings.titleOf(90, true));
		assertTrue(Standings.meets(Title.BURGHER, Title.FRIEND), "a burgher is trusted as a friend");
		assertFalse(Standings.meets(Title.KNOWN, Title.FRIEND));
		assertTrue(Standings.meets(Title.OUTLAW, null));
	}

	@Test
	void clearingYourNameIsOnlyForTheWantedAndTheFenceTakesAnyone() {
		long onlyWanted = templates.stream().filter(t -> t.when().wanted() == QuestTemplate.Wanted.ONLY).count();
		assertTrue(onlyWanted >= 2, "ways to clear your name: " + onlyWanted);
		QuestTemplate fence = byId.get(Identifier.parse("hearth:crime/rob_caravan"));
		assertEquals(QuestTemplate.Wanted.ALLOW, fence.when().wanted());
		assertEquals(List.of(JobType.FENCE), fence.giverJobs());
		assertEquals("robbery", fence.objectives().getFirst().crime());
	}

	@Test
	void anOutlawWarrantPaysMoreAlive() {
		SurveyedSettlement s = town(22, Culture.TEMPERATE);
		SettlementQuests data = new SettlementQuests();
		QuestTemplate warrant = byId.get(Identifier.parse("hearth:law/outlaw_warrant"));
		QuestInstance q = QuestGenerator.bind(warrant, QuestTestKit.context(s, data), null);
		assertNotNull(q);
		Objective o = q.objectives.getFirst();
		assertEquals(ObjectiveKind.APPREHEND, o.kind);
		assertTrue(o.data.getBooleanOr(Objective.Keys.LETHAL, false), "dead or alive");
		assertEquals(2.0, q.extra.getDoubleOr(QuestInstance.Extra.ALIVE_BONUS, 0), 1e-9);
		double tierPay = new double[]{0.6, 0.8, 1.0, 1.3, 1.7}[s.tier().ordinal()];
		assertTrue(q.reward.coin() >= Math.round(120 * tierPay * 2.0), "the board offers the alive price (base × 2): " + q.reward.coin());
		assertEquals("hearth:quest/outlaw_brought_in", q.extra.getStringOr(QuestInstance.Extra.DEED, ""));
	}

	@Test
	void thiefTakingWaitsAtTheMarketAtNight() {
		SurveyedSettlement s = town(23, Culture.TEMPERATE);
		s.addPoi(new dev.voidmc.hearth.api.Poi(dev.voidmc.hearth.api.PoiType.MARKET_STALL, new BlockPos(7, 64, 9), Direction.NORTH, JobType.MERCHANT));
		QuestInstance q = QuestGenerator.bind(byId.get(Identifier.parse("hearth:law/thief_taking")), QuestTestKit.context(s, new SettlementQuests()), null);
		assertNotNull(q);
		Objective o = q.objectives.getFirst();
		assertEquals(ObjectiveKind.APPREHEND, o.kind);
		assertEquals(new BlockPos(7, 64, 9), o.pos);
		assertTrue(o.data.getBooleanOr(Objective.Keys.AT_NIGHT, false));
		assertFalse(o.data.getBooleanOr(Objective.Keys.LETHAL, false), "the court wants the thief alive");
		assertTrue(o.data.getBooleanOr(Objective.Keys.GRANT_REQUIRED, false), "and the stolen goods back");
	}

	@Test
	void prospectingRules() {
		assertTrue(Prospecting.prospectable(ResourceSite.Kind.STONE));
		assertTrue(Prospecting.prospectable(ResourceSite.Kind.ORE));
		assertFalse(Prospecting.prospectable(ResourceSite.Kind.FISH));
		Prospecting.Rule ore = Prospecting.rule(ResourceSite.Kind.ORE);
		assertFalse(Prospecting.found(ore, ore.need() - 1, 5));
		assertTrue(Prospecting.found(ore, ore.need(), ore.exposed()));
		// (tags are not bound in unit tests; the clay rule names its blocks)
		assertTrue(Prospecting.rule(ResourceSite.Kind.CLAY).matches().test(net.minecraft.world.level.block.Blocks.CLAY.defaultBlockState()));
		assertFalse(Prospecting.rule(ResourceSite.Kind.CLAY).matches().test(net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState()));
	}

	// ------------------------------------------------------------------ validation and saving

	@Test
	void validatorChecksTheLivingWorldFields() {
		QuestTemplate badState = QuestTestKit.parse("""
			{"type": "official", "when": {"requires": ["job:wizard"]}, "objectives": [{"kind": "courier"}], "text": {"title": "x"}}""");
		assertTrue(TemplateValidator.validate(badState, TemplateValidator.Lookup.ANY).stream().anyMatch(p -> p.contains("wizard")));
		QuestTemplate eventWithout = QuestTestKit.parse("""
			{"type": "disaster", "objectives": [{"kind": "extinguish", "place": "event"}], "text": {"title": "x"}}""");
		assertFalse(TemplateValidator.validate(eventWithout, TemplateValidator.Lookup.ANY).isEmpty(), "an event template names its events");
		QuestTemplate badCrime = QuestTestKit.parse("""
			{"type": "crime", "objectives": [{"kind": "ambush", "entity": "minecraft:llama", "distance": [10, 20], "crime": "jaywalking"}],
			 "text": {"title": "x"}}""");
		assertTrue(TemplateValidator.validate(badCrime, TemplateValidator.Lookup.ANY).stream().anyMatch(p -> p.contains("jaywalking")));
		QuestTemplate badPlace = QuestTestKit.parse("""
			{"type": "law", "objectives": [{"kind": "apprehend", "entity": "minecraft:pillager", "name": "X", "place": "poi:throne_room"}],
			 "text": {"title": "x"}}""");
		assertTrue(TemplateValidator.validate(badPlace, TemplateValidator.Lookup.ANY).stream().anyMatch(p -> p.contains("throne_room")));
		QuestTemplate contractWithoutShortage = QuestTestKit.parse("""
			{"type": "contract", "when": {"trigger": "stalled"}, "objectives": [{"kind": "deliver", "item": "$goods_item"}], "text": {"title": "x"}}""");
		assertFalse(TemplateValidator.validate(contractWithoutShortage, TemplateValidator.Lookup.ANY).isEmpty());
	}

	@Test
	void livingStateSavesAndLoads() {
		SettlementQuests data = new SettlementQuests();
		data.crime = 5;
		data.decliningSince = 12;
		data.addNeed(new SettlementQuests.StalledNeed(4, Goods.ORE, 20, 3, "smelter", "smelter"));
		Happening h = data.addHappening(Happening.CARAVAN_ROBBED, 7, -1, new BlockPos(1, Objective.SURFACE_Y, 2), 99L, 40, "wool", "wool and leather");
		SurveyedSettlement s = town(24, Culture.TEMPERATE);
		SimpleSettlement other = QuestTestKit.village(25, "Far", Culture.TEMPERATE, new BlockPos(700, 64, 0));
		data.happenings.set(0, new Happening(h.id(), h.kind(), h.day(), h.plot(), h.pos(), other.id(), h.amount(), h.goods(), h.detail(), false));
		QuestGenerator.postEvents(QuestTestKit.context(s, data, 10, List.of(other), new ArrayList<>()), templates);
		var json = SettlementQuests.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
		SettlementQuests back = SettlementQuests.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(5, back.crime);
		assertEquals(12, back.decliningSince);
		assertEquals("smelter", back.needs.getFirst().source());
		assertEquals(Happening.CARAVAN_ROBBED, back.happenings.getFirst().kind());
		assertEquals(Objective.SURFACE_Y, back.happenings.getFirst().pos().getY());
		assertEquals(data.offers.size(), back.offers.size());
		if (!back.offers.isEmpty()) {
			assertEquals(data.offers.getFirst().extra.getStringOr(QuestInstance.Extra.DEED, "?"),
				back.offers.getFirst().extra.getStringOr(QuestInstance.Extra.DEED, "!"));
			assertEquals(h.id(), SettlementQuests.happeningOf(back.offers.getFirst()));
		}
		// a phase 2 save (no living fields) still loads
		SettlementQuests old = SettlementQuests.CODEC.parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(
			"{\"offers\": [], \"needs\": [{\"plot\": 1, \"goods\": \"stone\", \"missing\": 5}]}")).getOrThrow();
		assertEquals("", old.needs.getFirst().source());
		assertFalse(old.declining());
		assertEquals(Mth.clamp(old.nextHappening, 1, 1), old.nextHappening);
	}
}
