package dev.voidmc.hearth.client.overlay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.hearth.social.lives.LivesPayloads;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

/** The living-world screens' state (docs/OVERLAY.md, 2026-10-21): household, gear, people, stranger, retinue, HUD lines. */
class LivingWorldStateTest {
	static {
		OverlayFixtures.boot();
	}

	private static final String LOOK = "0000123400000102";

	private static JsonObject json(String s) {
		return JsonParser.parseString(s).getAsJsonObject();
	}

	private static final String SUMMARY = """
		{ "companions": [ { "id": "11111111-1111-1111-1111-111111111111", "name": "Sigrun Halvar", "job": "Farmer", "home": "Ulfdal",
		    "wage": 240, "wageText": "2 marks, 4 bits", "unpaidDays": 0, "orders": "follow", "fight": true, "loyalty": 64, "embodied": true,
		    "gear": [ { "slot": "chest", "item": "Iron Chestplate" } ],
		    "settlementId": 7, "look": "0000123400000102", "traits": [ "brave", "plain_spoken" ], "since": 10, "paidDay": 12, "today": 12,
		    "unpaidLimit": 3, "leaveBelow": -40, "opinion": 22, "waitAt": null, "pos": { "x": 1, "y": 64, "z": 2 }, "where": "", "near": true,
		    "armor": 6, "lastLine": "Your enemies are mine.", "lastWhere": "",
		    "slots": [ { "slot": "head", "item": "", "name": "", "count": 0, "empty": true },
		               { "slot": "chest", "item": "minecraft:iron_chestplate", "name": "Iron Chestplate", "count": 1, "empty": false },
		               { "slot": "mainhand", "item": "minecraft:iron_sword", "name": "Iron Sword", "count": 1, "empty": false } ] } ],
		  "companionsMax": 2,
		  "houses": [ { "settlementId": 7, "plot": 3, "servantsMax": 3, "town": "Ulfdal", "building": "longhouse", "larder": 11, "larderSize": 16,
		    "larderShort": false, "servants": [ { "id": "22222222-2222-2222-2222-222222222222", "look": "", "job": "Labourer", "duty": "larder",
		    "since": 5, "paidDay": 11, "today": 12, "unpaidLimit": 3, "name": "Hild Aslaug", "wage": 120, "wageText": "1 mark, 2 bits", "unpaidDays": 1 } ] } ],
		  "retinue": [ { "seat": "Ulfdal", "rank": "sworn", "rankName": "Sworn man", "commissions": 4, "feat": false, "next": "Household knight",
		    "seatId": 7, "lord": "Jarl Einar", "rankId": "sworn", "standing": 72, "knightStanding": 70, "needed": 6, "knighthood": true,
		    "demoted": false, "since": 2, "why": "commissions", "ranks": [], "rights": [ "lodging", "toll_free", "colours" ] } ],
		  "burgherOf": [ { "town": "Ulfdal", "since": 0, "settlementId": 7, "today": 12, "dues": 0.05, "rights": [ "dues", "council" ],
		    "council": { "sitting": false, "lastSitting": -1, "dueFrom": 12, "table": [], "lastChoice": "" } } ] }
		""";

	@Test
	void theRosterHasWagesAsCoinFacesAndWords() {
		List<LivingWorldJson.Hand> hands = List.of(new LivingWorldJson.Hand(0, new ItemStack(Items.IRON_HELMET)),
			new LivingWorldJson.Hand(1, new ItemStack(Items.BREAD, 6)), new LivingWorldJson.Hand(40, ItemStack.EMPTY));
		JsonObject h = LivingWorldJson.household(json(SUMMARY), k -> k == Long.parseUnsignedLong(LOOK, 16) ? "data:image/png;base64,F" : null, hands,
			"11111111-1111-1111-1111-111111111111").getAsJsonObject();
		JsonObject c = h.getAsJsonArray("companions").get(0).getAsJsonObject();
		assertEquals(240, c.getAsJsonObject("wageCoins").get("total").getAsLong());
		assertEquals(2, c.getAsJsonObject("wageCoins").get("crowns").getAsInt());
		assertEquals(4, c.getAsJsonObject("wageCoins").get("marks").getAsInt());
		assertEquals("data:image/png;base64,F", c.get("portrait").getAsString());
		assertEquals(LOOK, c.get("portraitKey").getAsString());
		assertEquals(4, c.get("loyaltyStep").getAsInt());
		assertEquals(3, c.get("servedDays").getAsInt());
		assertEquals(3, c.get("paidDays").getAsInt());
		assertEquals("Iron Sword", c.get("weapon").getAsString());
		assertEquals(2, c.getAsJsonArray("traitNames").size());
		JsonObject sv = h.getAsJsonArray("houses").get(0).getAsJsonObject().getAsJsonArray("servants").get(0).getAsJsonObject();
		assertTrue(sv.get("portrait").isJsonNull());
		assertEquals(7, sv.get("paidDays").getAsInt());
		assertEquals(8, sv.get("servedDays").getAsInt());
		assertEquals(2, h.get("inService").getAsInt());
		assertEquals(360, h.getAsJsonObject("dailyWages").get("total").getAsLong());
		// "Take this": a helmet goes on the head, bread to nobody, the empty off hand is listed empty
		JsonArray held = h.getAsJsonArray("hands");
		assertEquals("head", held.get(0).getAsJsonObject().get("gives").getAsString());
		assertEquals("", held.get(1).getAsJsonObject().get("gives").getAsString());
		assertTrue(held.get(2).getAsJsonObject().get("empty").getAsBoolean());
		assertEquals(40, held.get(2).getAsJsonObject().get("slot").getAsInt());
		assertEquals("11111111-1111-1111-1111-111111111111", h.get("gearFor").getAsString());
		// the server's own keys stay as they were (additions only)
		assertEquals("2 marks, 4 bits", c.get("wageText").getAsString());
		assertTrue(LivingWorldJson.household(null, k -> null, List.of(), null).isJsonNull());
	}

	@Test
	void loyaltyComesInFiveSteps() {
		assertEquals(0, LivingWorldJson.loyaltyStep(-50, -40));
		assertEquals(1, LivingWorldJson.loyaltyStep(-30, -40));
		assertEquals(2, LivingWorldJson.loyaltyStep(0, -40));
		assertEquals(3, LivingWorldJson.loyaltyStep(40, -40));
		assertEquals(4, LivingWorldJson.loyaltyStep(90, -40));
	}

	@Test
	void theReputationPageGetsRetinueAndBurgherCards() {
		JsonObject rep = json("""
			{ "towns": [ { "id": 7, "name": "Ulfdal", "value": 72 }, { "id": 9, "name": "Ashford", "value": 12 } ] }""");
		LivingWorldJson.placeOnReputation(rep, json(SUMMARY));
		JsonObject r = rep.getAsJsonArray("retinue").get(0).getAsJsonObject();
		assertEquals("Jarl Einar", r.get("lord").getAsString());
		assertEquals(3, r.getAsJsonArray("rights").size());
		assertEquals("lodging", r.getAsJsonArray("rights").get(0).getAsJsonObject().get("id").getAsString());
		assertFalse(r.get("whyText").getAsString().isEmpty());
		JsonObject ulfdal = rep.getAsJsonArray("towns").get(0).getAsJsonObject();
		assertEquals(12, ulfdal.getAsJsonObject("burgher").get("swornDays").getAsInt());
		assertEquals(2, ulfdal.getAsJsonObject("burgher").getAsJsonArray("rights").size());
		assertTrue(rep.getAsJsonArray("towns").get(1).getAsJsonObject().get("burgher").isJsonNull());
		// no summary yet: no retinue, nobody sworn
		JsonObject bare = json("{ \"towns\": [ { \"id\": 7, \"name\": \"Ulfdal\" } ] }");
		LivingWorldJson.placeOnReputation(bare, null);
		assertEquals(0, bare.getAsJsonArray("retinue").size());
		assertTrue(bare.getAsJsonArray("towns").get(0).getAsJsonObject().get("burgher").isJsonNull());
	}

	@Test
	void peopleCarryTheirStoryAndTheWaypoint() {
		String detail = """
			{ "id": "33333333-3333-3333-3333-333333333333", "settlementId": 7, "look": "0000123400000102", "job": "Smith", "place": "forge",
			  "traits": [ "stubborn" ], "since": 3, "today": 12, "following": false,
			  "beats": [ { "id": "drinking", "text": "Drinks every night.", "now": false, "end": false },
			             { "id": "sworn_off", "text": "Has sworn off the drink.", "now": true, "end": true } ],
			  "chose": { "id": "pay", "label": "Pay off his debt", "cost": 240, "by": "Tayte", "yours": true },
			  "choices": [ { "id": "talk", "label": "Talk", "cost": 0, "check": "persuade", "quest": false, "to": "sworn_off" } ],
			  "next": [], "relations": [ { "id": "44444444-4444-4444-4444-444444444444", "name": "Eirik", "bond": "apprentice", "partner": true,
			  "opinion": 30 } ], "find": [ { "days": "", "from": 720, "to": 780, "what": "work", "text": "at work" } ], "home": "cottage" }""";
		LivesPayloads.People p = new LivesPayloads.People(List.of(
			new LivesPayloads.Row("Harald Smed", "Harald", "drunk_smith", "Ulfdal", "drunk_smith", "sworn_off", true, 38, true, detail),
			new LivesPayloads.Row("Gunnhild", "Gunnhild", "widow_at_well", "Ulfdal", "widow", "grieving", false, 22, false)));
		LivesPayloads.Followed f = new LivesPayloads.Followed(true, "33333333-3333-3333-3333-333333333333", "Harald Smed", "Ulfdal", true,
			new BlockPos(100, 64, 100));
		JsonObject people = LivingWorldJson.people(p, k -> "data:image/png;base64,P", f).getAsJsonObject();
		JsonObject harald = people.getAsJsonArray("rows").get(0).getAsJsonObject();
		assertEquals("33333333-3333-3333-3333-333333333333", harald.get("id").getAsString());
		assertEquals("data:image/png;base64,P", harald.get("portrait").getAsString());
		assertEquals(2, harald.getAsJsonArray("beats").size());
		assertEquals(240, harald.getAsJsonObject("chose").getAsJsonObject("costCoins").get("total").getAsLong());
		assertTrue(harald.getAsJsonArray("choices").get(0).getAsJsonObject().get("costCoins").isJsonNull());
		assertEquals("12:00", harald.getAsJsonArray("find").get(0).getAsJsonObject().get("fromText").getAsString());
		assertTrue(harald.get("following").getAsBoolean());
		assertEquals("likes", harald.get("opinionWord").getAsString());
		// a row from an older server: the list's own keys, no detail
		JsonObject gunnhild = people.getAsJsonArray("rows").get(1).getAsJsonObject();
		assertTrue(gunnhild.get("id").isJsonNull());
		assertFalse(gunnhild.get("following").getAsBoolean());
		assertEquals(2, people.get("met").getAsInt());
		assertEquals(1, people.get("towns").getAsInt());
		assertEquals(1, people.get("moved").getAsInt());
		// the waypoint: where they are, how far, which way
		JsonObject track = LivingWorldJson.personTrack(f, new BlockPos(100, 64, 0), 0).getAsJsonObject();
		assertEquals(100, track.get("distance").getAsInt());
		assertEquals(0, track.get("bearing").getAsInt());
		assertTrue(LivingWorldJson.personTrack(LivesPayloads.Followed.NONE, BlockPos.ZERO, 0).isJsonNull());
		assertTrue(ActionRules.personListed(p, "33333333-3333-3333-3333-333333333333"));
		assertFalse(ActionRules.personListed(p, "44444444-4444-4444-4444-444444444444"));
	}

	@Test
	void aPeopleRowSurvivesTheWire() {
		LivesPayloads.Row row = new LivesPayloads.Row("A", "A", "gossip", "T", "gossip", "talking", false, 3, false, "{\"id\":\"x\"}");
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY);
		LivesPayloads.People.CODEC.encode(buf, new LivesPayloads.People(List.of(row)));
		assertEquals(row, LivesPayloads.People.CODEC.decode(buf).rows().get(0));
		assertEquals("", new LivesPayloads.Row("A", "A", "r", "T", "t", "b", false, 0, false).detail());
	}

	@Test
	void theStrangerCardShowsTheDemandInCoin() {
		JsonObject card = LivingWorldJson.stranger("""
			{ "kind": "bandit", "hooded": true, "demand": 1105, "carried": 4421, "band": 5, "inSight": 2, "bows": 1 }""").getAsJsonObject();
		assertTrue(card.get("hooded").getAsBoolean());
		assertEquals(11, card.getAsJsonObject("demandCoins").get("crowns").getAsInt());
		assertEquals(5, card.getAsJsonObject("demandCoins").get("bits").getAsInt());
		assertEquals(4421, card.getAsJsonObject("carriedCoins").get("total").getAsLong());
		assertFalse(card.get("kindName").getAsString().isEmpty());
		assertTrue(LivingWorldJson.stranger(null).isJsonNull());
		assertTrue(LivingWorldJson.stranger("not json").isJsonNull());
	}

	@Test
	void hudLinesPassThroughWithCoin() {
		JsonElement c = LivingWorldJson.contest(json("{ \"kind\": \"archery\", \"state\": \"open\", \"purse\": 400, \"hits\": [7, 0, 10] }"));
		assertEquals(4, c.getAsJsonObject().getAsJsonObject("purseCoins").get("crowns").getAsInt());
		assertTrue(LivingWorldJson.contest(null).isJsonNull());
		JsonObject a = LivingWorldJson.arrival(7, "Ulfdal", "[{\"kind\":\"wedding\",\"text\":\"A wedding in the chapel\",\"tone\":\"join\"}]", true)
			.getAsJsonObject();
		assertEquals(1, a.getAsJsonArray("events").size());
		assertTrue(a.get("showing").getAsBoolean());
		assertNull(LivingWorldJson.parse(""));
	}

	@Test
	void householdCallsAreCheckedAgainstTheRoster() {
		JsonObject h = json(SUMMARY);
		assertTrue(ActionRules.companionListed(h, "11111111-1111-1111-1111-111111111111"));
		assertFalse(ActionRules.companionListed(h, "22222222-2222-2222-2222-222222222222")); // a servant takes no orders
		assertFalse(ActionRules.companionListed(null, "11111111-1111-1111-1111-111111111111"));
		assertTrue(ActionRules.ORDERS.contains("wait"));
		assertFalse(ActionRules.ORDERS.contains("lend"));
		assertTrue(ActionRules.handSlot(0) && ActionRules.handSlot(8) && ActionRules.handSlot(40));
		assertFalse(ActionRules.handSlot(9) || ActionRules.handSlot(-1) || ActionRules.handSlot(36));
		assertTrue(ActionRules.GEAR_SLOTS.contains("offhand"));
	}

	@Test
	void theGearScreenIsAKnownHostOnlyScreen() {
		assertTrue(HearthScreens.known(HearthScreens.GEAR));
		assertFalse(HearthScreens.offerHosted(HearthScreens.GEAR)); // no opener: nothing taken
	}
}
