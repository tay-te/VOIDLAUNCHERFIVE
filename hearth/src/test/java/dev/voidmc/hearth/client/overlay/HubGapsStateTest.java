package dev.voidmc.hearth.client.overlay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.voidmc.hearth.economy.bank.BankLedger;
import dev.voidmc.hearth.economy.bank.BankView;
import dev.voidmc.hearth.economy.market.MarketView;
import dev.voidmc.hearth.law.net.LawPayloads;
import dev.voidmc.hearth.quest.net.QuestPayloads;
import dev.voidmc.hearth.social.dice.DiceView;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

/** The Hub's 2026-10-19 gaps (docs/OVERLAY.md): faces by look, bare notes, "he takes it", the ledger, tracked posters. */
class HubGapsStateTest {
	static {
		OverlayFixtures.boot();
	}

	private static final long LOOK_KEY = 0x0000_1234_0000_0102L;
	/** Folk 77 is loaded; anyone else only has the look the server sent. */
	private static final HearthStateJson.Lookups LOOK = new HearthStateJson.Lookups(e -> e == 77 ? "data:image/png;base64,AAA" : null,
		e -> e == 77 ? "00000000000000aa" : "", t -> 0, k -> k == LOOK_KEY ? "data:image/png;base64,LOOK" : null);

	@Test
	void aMerchantAwayFromTheCounterStillHasAFace() {
		MarketView v = new MarketView("Bradfield", "temperate", 24, 454, 1250, Component.literal("\"Have a look at what we have.\""), Component.empty(),
			List.of(), List.of(), true, null, new MarketView.Merchant("Wenna Marrow", "hearth.npc.job.merchant", -1, List.of(), 0, 0, LOOK_KEY));
		JsonObject m = HearthStateJson.market(3, v, LOOK);
		JsonObject merchant = m.getAsJsonObject("merchant");
		assertEquals("data:image/png;base64,LOOK", merchant.get("portrait").getAsString());
		assertEquals("0000123400000102", merchant.get("portraitKey").getAsString());
		// the note is bare speech: the page adds its own quote marks
		assertEquals("Have a look at what we have.", m.get("note").getAsString());
		// a loaded folk wins over the look
		JsonObject here = HearthStateJson.market(3, OverlayFixtures.market(null), LOOK).getAsJsonObject("merchant");
		assertEquals("data:image/png;base64,AAA", here.get("portrait").getAsString());
	}

	@Test
	void quotesComeOffOnlyWhenTheyWrapTheWholeLine() {
		assertEquals("Move along.", HearthStateJson.unquoted("\"Move along.\""));
		assertEquals("Move along.", HearthStateJson.unquoted("“Move along.”"));
		assertEquals("\"a\" and \"b\"", HearthStateJson.unquoted("\"a\" and \"b\""));
		assertEquals("plain", HearthStateJson.unquoted("plain"));
		assertEquals("\"", HearthStateJson.unquoted("\""));
	}

	@Test
	void diceAndPocketsCarryTheFolksFace() {
		DiceView d = new DiceView(0, "Edda", "bold", 20, 2000, 0, 0, 0, 1, new int[] {1, 2, 3, 4, 5, 6}, 0, 0, 0, 0, 0, 1, List.of(0, 10),
			Component.empty(), Component.empty(), -1, false, 0, 454, true, false, -1, LOOK_KEY);
		JsonObject opp = HearthStateJson.dice(5, d, LOOK).getAsJsonObject("opponent");
		assertEquals("data:image/png;base64,LOOK", opp.get("portrait").getAsString());
		assertEquals(-1, opp.get("entity").getAsInt());
		LawPayloads.Pocket p = new LawPayloads.Pocket(9, "Ulrich", false, List.of(new ItemStack(Items.APPLE)), 0.4F, 0.01F, -1, 0, 0, 0, 77, -1L);
		JsonObject pocket = HearthStateJson.pickpocket(p, LOOK);
		assertEquals("data:image/png;base64,AAA", pocket.get("portrait").getAsString());
		assertEquals(77, pocket.get("entity").getAsInt());
		assertTrue(HearthStateJson.pickpocket(p).get("portrait").isJsonNull());
	}

	@Test
	void theHaggleSaysHowLikelyHeTakesAnOffer() {
		JsonObject h = HearthStateJson.market(3, OverlayFixtures.market(OverlayFixtures.haggle(16, 22, 9)), LOOK).getAsJsonObject("haggle");
		JsonArray takes = h.getAsJsonArray("takes");
		assertTrue(takes.size() >= 2);
		long prev = Long.MIN_VALUE;
		for (int i = 0; i < takes.size(); i++) {
			long bits = takes.get(i).getAsJsonObject().get("bits").getAsLong();
			assertTrue(bits > prev, "ascending");
			prev = bits;
		}
		JsonObject last = takes.get(takes.size() - 1).getAsJsonObject();
		assertEquals(24, last.get("bits").getAsLong(), "up to his word");
		assertEquals(100, last.get("odds").getAsInt());
		assertEquals(16, takes.get(0).getAsJsonObject().get("bits").getAsLong(), "from the band's far end");
		int lastOffer = h.get("takesLast").getAsInt();
		assertTrue(lastOffer >= 0 && lastOffer <= 100);
	}

	@Test
	void theBankKeepsALedger() {
		BankView v = new BankView("Bradfield", 0, 500, 120, 4000, 0, 10, 50, 60, 24, true, List.of(), Component.empty(), true, List.of(), List.of(),
			List.of(new BankLedger.Line(12, BankLedger.DEPOSIT, -200, 200, "Bradfield", ""),
				new BankLedger.Line(11, BankLedger.BUY_EMERALDS, -120, 0, "Ashford", "2")));
		JsonArray ledger = HearthStateJson.bank(4, v).getAsJsonArray("ledger");
		assertEquals(2, ledger.size());
		JsonObject first = ledger.get(0).getAsJsonObject();
		assertEquals("deposit", first.get("kind").getAsString());
		assertEquals(-200, first.getAsJsonObject("purse").get("total").getAsLong());
		assertEquals(200, first.getAsJsonObject("deposit").get("total").getAsLong());
		assertFalse(first.get("text").getAsString().isEmpty());
		assertEquals("2", ledger.get(1).getAsJsonObject().get("subjectName").getAsString());
		// an older view without the ledger sends an empty one
		BankView old = new BankView("Bradfield", 0, 500, 120, 4000, 0, 10, 50, 60, 24, true, List.of(), Component.empty(), true, List.of(), List.of());
		assertEquals(0, HearthStateJson.bank(4, old).getAsJsonArray("ledger").size());
	}

	@Test
	void aTrackedPosterIsFollowedLikeAQuest() {
		String quarry = "4f9c3a52-0000-0000-0000-000000000001";
		QuestPayloads.Board board = new QuestPayloads.Board(1L, BlockPos.ZERO, "Bradfield", "village", 24, List.of(), List.of(),
			List.of(new QuestPayloads.Board.Poster("Rook", "3 marks", false, "hearth.offence.theft", quarry),
				new QuestPayloads.Board.Poster("Old", "", true)), "Known");
		JsonArray wanted = HearthStateJson.board(board, null, 0, null, LOOK, quarry).getAsJsonArray("wanted");
		assertTrue(wanted.get(0).getAsJsonObject().get("tracked").getAsBoolean());
		assertEquals(quarry, wanted.get(0).getAsJsonObject().get("player").getAsString());
		assertFalse(wanted.get(1).getAsJsonObject().get("tracked").getAsBoolean());
		assertTrue(ActionRules.posterListed(board, quarry));
		assertFalse(ActionRules.posterListed(board, ""));
		assertFalse(ActionRules.posterListed(board, "4f9c3a52-0000-0000-0000-000000000002"));

		QuestPayloads.PosterTrack t = new QuestPayloads.PosterTrack(true, quarry, "Rook", 30, false, "hearth.offence.theft", 1L, "Bradfield", true, true,
			new BlockPos(16, 64, 48));
		JsonObject w = HearthStateJson.wantedTrack(t, new BlockPos(16, 64, 0), 0).getAsJsonObject();
		assertEquals("Rook", w.get("name").getAsString());
		assertEquals(48, w.get("distance").getAsInt());
		assertEquals(0, w.get("bearing").getAsInt(), "straight ahead at yaw 0 (south)");
		assertEquals(3, w.getAsJsonObject("owed").get("marks").getAsLong());
		JsonObject away = HearthStateJson.wantedTrack(new QuestPayloads.PosterTrack(true, quarry, "Rook", 30, false, "", 1L, "Bradfield", false, false,
			BlockPos.ZERO), null, 0).getAsJsonObject();
		assertTrue(away.get("near").isJsonNull());
		assertEquals(-1, away.get("distance").getAsInt());
		assertTrue(HearthStateJson.wantedTrack(QuestPayloads.PosterTrack.NONE, null, 0).isJsonNull());
		assertTrue(HearthStateJson.wantedTrack(null, null, 0).isJsonNull());
	}
}
