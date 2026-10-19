package dev.voidmc.hearth.client.overlay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.voidmc.hearth.law.net.LawPayloads;
import dev.voidmc.hearth.quest.model.PlayerQuests;
import dev.voidmc.hearth.quest.model.QuestInstance;
import dev.voidmc.hearth.quest.model.QuestStatus;
import dev.voidmc.hearth.social.dice.DiceGame;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

/** HearthState's pieces (docs/OVERLAY.md): the shape a host binds to, built from plain payloads. */
class HearthStateJsonTest {
	static {
		OverlayFixtures.boot();
	}

	private static final HearthStateJson.Lookups LOOK = new HearthStateJson.Lookups(e -> e == OverlayFixtures.AGNES || e == 77 ? "data:image/png;base64,AAA" : null,
		e -> e == OverlayFixtures.AGNES ? "00000000000000aa" : "", t -> t.equals("minecraft:spruce_log") ? 9 : 0);

	@Test
	void coinsAreTheFewestCrownsMarksAndBits() {
		JsonObject c = HearthStateJson.coins(454);
		assertEquals(454, c.get("total").getAsLong());
		assertEquals(4, c.get("crowns").getAsLong());
		assertEquals(5, c.get("marks").getAsLong());
		assertEquals(4, c.get("bits").getAsLong());
		assertFalse(c.get("text").getAsString().isEmpty());
		assertEquals(0, HearthStateJson.coins(0).get("crowns").getAsLong());
	}

	@Test
	void talkCarriesTheResidentCardAndEachOptionsFacts() {
		JsonObject t = HearthStateJson.talk(OverlayFixtures.talk(), OverlayFixtures.detail(), null, LOOK);
		JsonObject r = t.getAsJsonObject("resident");
		assertEquals("Agnes Garland", r.get("name").getAsString());
		assertEquals("Bradfield", r.get("town").getAsString());
		assertEquals("commerce/tavern", r.get("placeKey").getAsString());
		assertEquals("Tavern", r.get("place").getAsString());
		assertEquals(3, r.getAsJsonArray("traits").size());
		assertEquals("generous", r.getAsJsonArray("traits").get(0).getAsJsonObject().get("id").getAsString());
		assertEquals(18, r.get("opinion").getAsInt());
		assertEquals("likes", r.get("opinionWord").getAsString());
		assertEquals("quest:rescue", r.getAsJsonObject("memory").get("kind").getAsString());
		assertEquals("data:image/png;base64,AAA", r.get("portrait").getAsString());
		assertEquals("00000000000000aa", r.get("portraitKey").getAsString());

		JsonObject st = t.getAsJsonObject("standing");
		assertEquals(24, st.get("value").getAsInt());
		assertEquals("known", st.get("title").getAsString());
		assertEquals("friend", st.getAsJsonObject("next").get("title").getAsString());
		assertEquals(40, st.getAsJsonObject("next").get("at").getAsInt());
		assertEquals("evening", t.getAsJsonObject("time").get("part").getAsString());
		assertEquals("Back again?", t.get("line").getAsString());

		JsonArray options = t.getAsJsonArray("options");
		assertEquals(6, options.size());
		JsonObject persuade = options.get(1).getAsJsonObject();
		assertEquals(2, persuade.get("key").getAsInt());
		assertEquals("persuade", persuade.get("kind").getAsString());
		assertEquals(62, persuade.get("odds").getAsInt());
		assertEquals("mettle:speech", persuade.getAsJsonObject("skill").get("id").getAsString());
		assertEquals(6, persuade.getAsJsonObject("skill").get("level").getAsInt());
		assertTrue(persuade.get("cost").isJsonNull());
		JsonObject bribe = options.get(2).getAsJsonObject();
		assertEquals(3, bribe.getAsJsonObject("cost").get("marks").getAsLong());
		JsonObject room = options.get(3).getAsJsonObject();
		assertTrue(room.get("locked").getAsBoolean());
		assertEquals("Needs Friend of Bradfield · you are Known", room.get("lockedReason").getAsString());
		assertEquals(1, room.getAsJsonObject("cost").get("crowns").getAsLong());
		assertTrue(persuade.get("lockedReason").isJsonNull());
		assertEquals("train", options.get(4).getAsJsonObject().get("kind").getAsString());
		JsonObject bye = options.get(5).getAsJsonObject();
		assertEquals("leave", bye.get("kind").getAsString());
		assertEquals(0, bye.get("key").getAsInt(), "goodbye is always key 0");
	}

	@Test
	void talkWithoutTheDetailStillLists() {
		JsonObject t = HearthStateJson.talk(OverlayFixtures.talk(), null, null, HearthStateJson.Lookups.NONE);
		JsonObject first = t.getAsJsonArray("options").get(0).getAsJsonObject();
		assertEquals("talk", first.get("kind").getAsString());
		assertEquals(-1, first.get("odds").getAsInt());
		assertTrue(t.getAsJsonObject("resident").get("portrait").isJsonNull());
		assertTrue(t.getAsJsonObject("standing").get("next").isJsonNull());
		// with no detail nothing is known to be "leave", so keys simply count on
		assertEquals(6, t.getAsJsonArray("options").get(5).getAsJsonObject().get("key").getAsInt());
	}

	@Test
	void theLimitBandShowsOnlyWithEnoughSpeech() {
		JsonObject hidden = HearthStateJson.market(3, OverlayFixtures.market(OverlayFixtures.haggle(-1, -1, 2)), LOOK);
		JsonObject h = hidden.getAsJsonObject("haggle");
		assertTrue(h.get("band").isJsonNull());
		assertEquals(5, h.get("offersLeft").getAsInt());
		assertEquals(24, h.getAsJsonObject("word").get("total").getAsLong());
		assertEquals(19, h.getAsJsonObject("offer").get("total").getAsLong());

		JsonObject shown = HearthStateJson.market(3, OverlayFixtures.market(OverlayFixtures.haggle(16, 22, 9)), LOOK);
		JsonObject band = shown.getAsJsonObject("haggle").getAsJsonObject("band");
		assertEquals(16, band.getAsJsonObject("low").get("total").getAsLong());
		assertEquals(22, band.getAsJsonObject("high").get("total").getAsLong());
	}

	@Test
	void marketRowsAndMerchant() {
		JsonObject m = HearthStateJson.market(3, OverlayFixtures.market(OverlayFixtures.haggle(-1, -1, 0)), LOOK);
		assertEquals(3, m.get("containerId").getAsInt());
		assertEquals(4, m.getAsJsonObject("purse").get("crowns").getAsLong());
		JsonArray sells = m.getAsJsonArray("sells");
		assertEquals(3, sells.size());
		JsonObject iron = sells.get(1).getAsJsonObject();
		assertEquals("minecraft:iron_ingot", iron.get("item").getAsString());
		assertEquals(8, iron.get("lot").getAsInt());
		assertTrue(iron.get("haggling").getAsBoolean());
		JsonObject arrows = sells.get(2).getAsJsonObject();
		assertFalse(arrows.get("canTrade").getAsBoolean());
		assertTrue(arrows.getAsJsonObject("flags").get("cannotAfford").getAsBoolean());
		assertTrue(m.getAsJsonArray("buys").get(0).getAsJsonObject().getAsJsonObject("flags").get("needed").getAsBoolean());
		JsonObject merchant = m.getAsJsonObject("merchant");
		assertEquals("Martin Coyle", merchant.get("name").getAsString());
		assertEquals("content", merchant.get("moodWord").getAsString());
		assertEquals("data:image/png;base64,AAA", merchant.get("portrait").getAsString());
		assertEquals(2, merchant.getAsJsonArray("traits").size());
	}

	@Test
	void diceMasksBecomeFlagsPerDie() {
		JsonObject d = HearthStateJson.dice(5, OverlayFixtures.dice(DiceGame.Phase.PLAYER_ROLLED, 0b100000, true, true, 1));
		assertEquals("player_rolled", d.get("phase").getAsString());
		JsonArray dice = d.getAsJsonArray("dice");
		assertEquals(6, dice.size());
		assertTrue(dice.get(5).getAsJsonObject().get("kept").getAsBoolean());
		assertTrue(dice.get(1).getAsJsonObject().get("selected").getAsBoolean());
		assertFalse(dice.get(0).getAsJsonObject().get("scoring").getAsBoolean());
		assertEquals("carried", d.get("loaded").getAsString());
		assertEquals(3, d.getAsJsonArray("stakes").size());
		assertEquals("Edda", d.getAsJsonObject("opponent").get("name").getAsString());
	}

	@Test
	void pocketsCountWhatWasTaken() {
		JsonObject p = HearthStateJson.pickpocket(OverlayFixtures.pocket(LawPayloads.Pocket.OPEN));
		assertEquals(1, p.get("taken").getAsInt());
		assertTrue(p.getAsJsonArray("items").get(1).getAsJsonObject().get("empty").getAsBoolean());
		assertEquals("open", p.get("state").getAsString());
		assertEquals("caught", HearthStateJson.pickpocket(OverlayFixtures.pocket(LawPayloads.Pocket.CAUGHT)).get("state").getAsString());
	}

	@Test
	void theSentenceCountsDownBetweenUpdates() {
		LawPayloads.Status jailed = new LawPayloads.Status("Bradfield", 0, false, 0, 400, true, false);
		JsonObject law = HearthStateJson.law(LawPayloads.Hint.NONE, jailed, 5_000, new LawPayloads.Stealth(true, 0.3F, false));
		assertEquals(300, law.getAsJsonObject("status").get("jailTicks").getAsLong());
		assertEquals(15, law.getAsJsonObject("status").get("jailSeconds").getAsLong());
		assertTrue(law.get("hint").isJsonNull());
		assertTrue(law.getAsJsonObject("stealth").get("active").getAsBoolean());
		LawPayloads.Hint owned = new LawPayloads.Hint(Component.literal("Owned by Edda Miller"), false, 3, true, false, false);
		assertEquals(3, HearthStateJson.law(owned, LawPayloads.Status.CLEAR, 0, new LawPayloads.Stealth(false, 0, false)).getAsJsonObject("hint")
			.get("lockTier").getAsInt());
	}

	@Test
	void theJournalListsTheTrackedQuestFirstAndCountsTheFinished() {
		PlayerQuests pq = new PlayerQuests();
		QuestInstance a = OverlayFixtures.quest("Rats in the cellar", QuestStatus.ACTIVE, 12);
		QuestInstance b = OverlayFixtures.quest("Timber for the mill", QuestStatus.ACTIVE, 3);
		pq.active.add(a);
		pq.active.add(b);
		pq.tracked = b.id;
		pq.finish(OverlayFixtures.quest("Old work", QuestStatus.ACTIVE, 0), QuestStatus.COMPLETED, 3);
		JsonObject j = HearthStateJson.journal(pq, 1000, new BlockPos(100, 64, 30), LOOK);
		JsonArray active = j.getAsJsonArray("active");
		assertEquals("Timber for the mill", active.get(0).getAsJsonObject().get("title").getAsString());
		assertTrue(active.get(0).getAsJsonObject().get("tracked").getAsBoolean());
		JsonObject first = active.get(0).getAsJsonObject();
		JsonObject objective = first.getAsJsonArray("objectives").get(0).getAsJsonObject();
		assertEquals(3, objective.get("have").getAsInt());
		assertEquals(24, objective.get("need").getAsInt());
		assertEquals(9, objective.get("carried").getAsInt());
		assertEquals(30, first.get("distance").getAsInt(), "a delivery points at the hand-in");
		assertEquals(2, first.getAsJsonObject("reward").getAsJsonObject("coins").get("crowns").getAsLong());
		assertEquals(3, first.getAsJsonObject("reward").get("standing").getAsInt());
		assertTrue(first.get("deadline").getAsBoolean());
		assertEquals(1, j.getAsJsonObject("finished").get("completed").getAsInt());
	}

	@Test
	void theQuestHudPointsTheCompass() {
		PlayerQuests pq = new PlayerQuests();
		QuestInstance q = OverlayFixtures.quest("Timber for the mill", QuestStatus.ACTIVE, 3);
		pq.active.add(q);
		assertTrue(HearthStateJson.questHud(pq, 0, BlockPos.ZERO, 0, LOOK).isJsonNull(), "nothing tracked: no HUD");
		pq.tracked = q.id;
		// the hand-in is 100 blocks east; looking south (yaw 0) it is a quarter turn to the left
		JsonObject hud = HearthStateJson.questHud(pq, 0, BlockPos.ZERO, 0, LOOK).getAsJsonObject();
		assertEquals(100, hud.get("distance").getAsInt());
		assertEquals(-90, hud.get("bearing").getAsInt());
		assertEquals("Bring 24 × Spruce Log", hud.get("step").getAsString());
	}

	@Test
	void confrontCarriesTheChargeAndEachChoicesFacts() {
		LawPayloads.ConfrontDetail d = new LawPayloads.ConfrontDetail(55, "arrest", "hearth.npc.job.guard", java.util.List.of("brave", "grumpy"), -12, 24,
			"known", "Bradfield", "hearth.offence.theft", 1, "", java.util.List.of(new LawPayloads.ChoiceInfo("pay", "pay", -1, 140, -1),
			new LawPayloads.ChoiceInfo("bribe", "bribe", 55, 60, -1), new LawPayloads.ChoiceInfo("run", "run", -1, -1, -1)));
		JsonObject c = HearthStateJson.confront(OverlayFixtures.confront(), d, LOOK);
		assertEquals("arrest", c.get("kind").getAsString());
		assertEquals("dislikes", c.getAsJsonObject("folk").get("opinionWord").getAsString());
		assertEquals("hearth.offence.theft", c.getAsJsonObject("charge").get("offenceKey").getAsString());
		JsonObject bribe = c.getAsJsonArray("choices").get(1).getAsJsonObject();
		assertEquals(55, bribe.get("odds").getAsInt());
		assertEquals(6, bribe.getAsJsonObject("cost").get("marks").getAsLong());
		assertEquals(1, c.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("cost").get("crowns").getAsLong());
		assertEquals(24, c.getAsJsonObject("standing").get("value").getAsInt());
	}

	@Test
	void confrontNumbersItsChoices() {
		JsonObject c = HearthStateJson.confront(OverlayFixtures.confront(), null, LOOK);
		assertEquals(1, c.get("bounty").getAsJsonObject().get("crowns").getAsLong());
		JsonArray choices = c.getAsJsonArray("choices");
		assertEquals(3, choices.get(2).getAsJsonObject().get("key").getAsInt());
		assertFalse(choices.get(1).getAsJsonObject().get("enabled").getAsBoolean());
	}

	@Test
	void everyWordingHelperCoversItsRange() {
		assertEquals("hates", HearthStateJson.opinionWord(-80));
		assertEquals("neutral", HearthStateJson.opinionWord(0));
		assertEquals("loves", HearthStateJson.opinionWord(90));
		for (int minute = 0; minute < 1440; minute += 30) {
			assertTrue(Set.of("dawn", "morning", "midday", "afternoon", "evening", "night").contains(HearthStateJson.partOfDay(minute)));
		}
		assertEquals("glad", HearthStateJson.moodWord(60));
		assertEquals("bitter", HearthStateJson.moodWord(-60));
	}
}
