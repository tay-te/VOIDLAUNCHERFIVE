package dev.voidmc.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hub.model.Bridge;
import dev.voidmc.hub.model.Json;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The JS bridge: what the page sends decodes to typed messages; nothing malformed escapes. */
class BridgeTest {

	@Test
	void decodesEveryPageMessage() {
		assertInstanceOf(Bridge.Ready.class, Bridge.decode("{\"type\":\"ready\"}"));
		assertInstanceOf(Bridge.Close.class, Bridge.decode("{\"type\":\"close\"}"));
		assertEquals(new Bridge.Filter("tools"), Bridge.decode("{\"type\":\"filter\",\"value\":\"tools\"}"));
		assertEquals(new Bridge.Sort("container"), Bridge.decode("{\"type\":\"sort\",\"region\":\"container\"}"));
		assertEquals(new Bridge.MoveAll("inventory"), Bridge.decode("{\"type\":\"moveAll\",\"to\":\"inventory\"}"));
		assertEquals(new Bridge.Segment("classes"), Bridge.decode("{ \"type\" : \"segment\" , \"value\" : \"classes\" }"));
		assertEquals(new Bridge.Turn(-12.5), Bridge.decode("{\"type\":\"turn\",\"deg\":-12.5}"));
		assertEquals(new Bridge.Segment("gear"), Bridge.decode("{\"type\":\"segment\",\"value\":\"gear\"}"));
		assertEquals(new Bridge.MettleCall("learnPerk", "mettle:mining/prospector"),
			Bridge.decode("{\"type\":\"mettle\",\"op\":\"learnPerk\",\"arg\":\"mettle:mining/prospector\"}"));
		assertEquals(new Bridge.MettleCall("spendAttribute", "strength"), Bridge.decode("{\"type\":\"mettle\",\"op\":\"spendAttribute\",\"arg\":\"strength\"}"));
		// Unknown operations and anything that is not an id are refused before they reach Mettle.
		assertInstanceOf(Bridge.Invalid.class, Bridge.decode("{\"type\":\"mettle\",\"op\":\"respec\",\"arg\":\"x\"}"));
		assertInstanceOf(Bridge.Invalid.class, Bridge.decode("{\"type\":\"mettle\",\"op\":\"learnPerk\",\"arg\":\"Mettle:Bad Id\"}"));
		assertInstanceOf(Bridge.Invalid.class, Bridge.decode("{\"type\":\"mettle\",\"op\":\"learnPerk\"}"));
		assertEquals(new Bridge.Page("map"), Bridge.decode("{\"type\":\"page\",\"value\":\"map\"}"));
		assertInstanceOf(Bridge.Waypoint.class, Bridge.decode("{\"type\":\"waypoint\"}"));
	}

	@Test
	void cardActionsKeepTheirShapes() {
		String[][] ok = {{"pickCard", "mettle:mining#2"}, {"keepCard", "mettle:blades#0#1"}, {"reroll", "mettle:mining"}, {"slotCard", "12"},
			{"slotCard", "12#3"}, {"slotCard", "7#-1#mettle:blades"}, {"rebuildCards", "12,15"}, {"fuseCards", "3,8,9"}, {"releaseCards", "3,8,9"},
			{"unslotCards", "12"}, {"pinSets", ""}, {"pinSets", "mettle:swift,mettle:keen"}, {"closeSitting", ""}, {"tradeRequest", "Bree"},
			{"tradeOfferCard", "44"}, {"tradeOfferItem", "-1"}, {"tradeTakeBackItem", "0"}, {"tradeCoin", "250"}, {"tradeLock", ""},
			{"cardSound", "hover#mettle:mining#2"}, {"cardSound", "deal#mettle:mining"}, {"cardSound", "unhover#"}, {"cardSound", "pick#mythic"},
			{"claimMythic", "mettle:blades"}, {"setCardSounds", "0"}, {"setCardSounds", "1"}, {"setCardSoundsVolume", "0"},
			{"setCardSoundsVolume", "45"}, {"setCardSoundsVolume", "100"}};
		for (String[] c : ok) {
			assertEquals(new Bridge.MettleCall(c[0], c[1]), Bridge.decode(Json.object().put("type", "mettle").put("op", c[0]).put("arg", c[1]).end()), c[0] + " " + c[1]);
		}
		assertEquals(new Bridge.MettleCall("tradeAccept", ""), Bridge.decode("{\"type\":\"mettle\",\"op\":\"tradeAccept\"}"));
		String[][] bad = {{"pickCard", "mettle:mining#3"}, {"pickCard", "mettle:mining"}, {"slotCard", "12#x"}, {"rebuildCards", "12;15"},
			{"rebuildCards", "1,2,3,4,5,6,7,8,9,10,11,12,13"}, {"pinSets", "a:b,c:d,e:f,g:h"}, {"closeSitting", "x"}, {"tradeRequest", "Bree Smith"},
			{"tradeOfferItem", "-2"}, {"tradeCoin", "-5"}, {"cardSound", "explode#"}, {"cardSound", "hover#<script>"},
			{"claimMythic", "mettle:blades#1"}, {"claimMythic", ""}, {"setCardSounds", "true"}, {"setCardSounds", ""},
			{"setCardSoundsVolume", "101"}, {"setCardSoundsVolume", "-1"}, {"setCardSoundsVolume", "05"}, {"setCardSoundsVolume", "50.5"}};
		for (String[] c : bad) {
			assertInstanceOf(Bridge.Invalid.class, Bridge.decode(Json.object().put("type", "mettle").put("op", c[0]).put("arg", c[1]).end()), c[0] + " " + c[1]);
		}
		assertEquals(new Bridge.MettleUi(true, false), Bridge.decode("{\"type\":\"mettleUi\",\"modal\":true,\"text\":false}"));
	}

	@Test
	void buildFeelActionsAndQuestionsKeepTheirShapes() {
		String[][] ok = {{"setFocus", "mettle:deep"}, {"setFocus", ""}, {"temperCard", "12#mettle:mining/deepborn"}, {"inscribeCard", "12#mettle:deep"},
			{"boonReroll", "mettle:mining"}, {"setSignature", "44"}, {"clearSignature", "mettle:mining"}, {"takeByname", ""}, {"wearByname", "3"},
			{"forgeMythic", "mettle:stonesong"}, {"forgeLegend", ""}, {"guideSkip", ""}, {"guideAdvance", "dismiss"}, {"guideAdvance", "not_now"},
			{"guideAdvance", "keyword"}, {"momentShown", "boon##mettle:mining#0"}, {"momentShown", "set#mettle:deep##2"}, {"momentShown", "byname###3"}};
		for (String[] c : ok) {
			assertEquals(new Bridge.MettleCall(c[0], c[1]), Bridge.decode(Json.object().put("type", "mettle").put("op", c[0]).put("arg", c[1]).end()), c[0] + " " + c[1]);
		}
		String[][] bad = {{"setFocus", "Deep Keyword"}, {"temperCard", "12"}, {"temperCard", "x#mettle:mining/deepborn"}, {"inscribeCard", "12#<b>"},
			{"boonReroll", ""}, {"setSignature", "12,13"}, {"wearByname", "-1"}, {"wearByname", "1234"}, {"forgeMythic", ""}, {"forgeLegend", "x"},
			{"guideAdvance", "explode"}, {"guideSkip", "1"}, {"takeByname", "mettle:x"}, {"momentShown", "Boon#x#y#1"}, {"momentShown", "set#mettle:deep#2"},
			{"momentShown", "set#<b>##1"}};
		for (String[] c : bad) {
			assertInstanceOf(Bridge.Invalid.class, Bridge.decode(Json.object().put("type", "mettle").put("op", c[0]).put("arg", c[1]).end()), c[0] + " " + c[1]);
		}
		assertEquals(new Bridge.MettleAsk("previewTemper", "12#mettle:mining/deepborn", 7),
			Bridge.decode("{\"type\":\"mettleAsk\",\"op\":\"previewTemper\",\"arg\":\"12#mettle:mining/deepborn\",\"req\":7}"));
		assertEquals(new Bridge.MettleAsk("boonOptions", "12", 0), Bridge.decode("{\"type\":\"mettleAsk\",\"op\":\"boonOptions\",\"arg\":\"12\",\"req\":0}"));
		assertEquals(new Bridge.MettleAsk("previewSlot", "9", 3), Bridge.decode("{\"type\":\"mettleAsk\",\"op\":\"previewSlot\",\"arg\":\"9\",\"req\":3}"));
		for (String bad2 : new String[] {"{\"type\":\"mettleAsk\",\"op\":\"temperCard\",\"arg\":\"12#mettle:x\",\"req\":1}",
			"{\"type\":\"mettleAsk\",\"op\":\"previewTemper\",\"arg\":\"12\",\"req\":1}", "{\"type\":\"mettleAsk\",\"op\":\"boonOptions\",\"arg\":\"12\"}",
			"{\"type\":\"mettleAsk\",\"op\":\"boonOptions\",\"arg\":\"12\",\"req\":1.5}", "{\"type\":\"mettleAsk\",\"op\":\"boonOptions\",\"arg\":\"12\",\"req\":-1}"}) {
			assertInstanceOf(Bridge.Invalid.class, Bridge.decode(bad2), bad2);
		}
	}

	@Test
	void nativeCardLayersAreChecked() {
		Bridge.Message m = Bridge.decode("{\"type\":\"fx\",\"items\":[{\"i\":\"g1c0-e\",\"k\":\"edge-rare\",\"x\":120,\"y\":199,\"c\":\"#4DA3F5\",\"a\":0.4,"
			+ "\"d\":240},{\"i\":\"mfoil\",\"k\":\"foil\",\"x\":1,\"y\":2,\"clip\":[1,2,3,4]}]}");
		Bridge.Fx fx = assertInstanceOf(Bridge.Fx.class, m);
		assertEquals(2, fx.items().size());
		Bridge.FxItem e = fx.items().getFirst();
		assertEquals("edge-rare", e.kind());
		assertEquals(0x4DA3F5, e.colour());
		assertEquals(0.4, e.alpha(), 1e-9);
		assertEquals(240, e.delay(), 1e-9);
		assertEquals(0xFFFFFF, fx.items().get(1).colour());
		assertEquals(4, fx.items().get(1).clip()[3], 1e-9);
		assertEquals(new Bridge.Fx(List.of()), Bridge.decode("{\"type\":\"fx\",\"items\":[]}"));
		for (String bad : List.of("{\"type\":\"fx\"}", "{\"type\":\"fx\",\"items\":[{\"i\":\"a\",\"k\":\"explode\"}]}",
			"{\"type\":\"fx\",\"items\":[{\"i\":\"A B\",\"k\":\"rect\"}]}", "{\"type\":\"fx\",\"items\":[{\"i\":\"a\",\"k\":\"rect\",\"x\":1e9}]}",
			"{\"type\":\"fx\",\"items\":[{\"i\":\"a\",\"k\":\"rect\",\"c\":\"red\"}]}", "{\"type\":\"fx\",\"items\":[{\"i\":\"a\",\"k\":\"foil\",\"clip\":[1,2]}]}")) {
			assertInstanceOf(Bridge.Invalid.class, Bridge.decode(bad), bad);
		}
	}

	@Test
	void pageItemWellsAreChecked() {
		Bridge.Items it = assertInstanceOf(Bridge.Items.class,
			Bridge.decode("{\"type\":\"items\",\"items\":[{\"id\":\"minecraft:diamond\",\"n\":5,\"x\":120.5,\"y\":343,\"w\":45,\"h\":45}]}"));
		assertEquals(new Bridge.ItemIcon("minecraft:diamond", 5, 120.5, 343, 45, 45), it.items().getFirst());
		assertEquals(new Bridge.Items(List.of()), Bridge.decode("{\"type\":\"items\",\"items\":[]}"));
		for (String bad : List.of("{\"type\":\"items\"}", "{\"type\":\"items\",\"items\":[{\"id\":\"Diamond\",\"n\":1,\"x\":0,\"y\":0,\"w\":1,\"h\":1}]}",
			"{\"type\":\"items\",\"items\":[{\"id\":\"minecraft:diamond\",\"n\":-1,\"x\":0,\"y\":0,\"w\":1,\"h\":1}]}",
			"{\"type\":\"items\",\"items\":[{\"id\":\"minecraft:diamond\",\"n\":1,\"x\":0,\"y\":0,\"w\":0,\"h\":1}]}",
			"{\"type\":\"items\",\"items\":[{\"id\":\"minecraft:diamond\",\"n\":1,\"x\":1e9,\"y\":0,\"w\":1,\"h\":1}]}")) {
			assertInstanceOf(Bridge.Invalid.class, Bridge.decode(bad), bad);
		}
	}

	@Test
	void whatTheWriterEncodesTheDecoderReads() {
		String msg = Json.object().put("type", "filter").put("value", "food").end();
		assertEquals(new Bridge.Filter("food"), Bridge.decode(msg));
		msg = Json.object().put("type", "moveAll").put("to", "container").end();
		assertEquals(new Bridge.MoveAll("container"), Bridge.decode(msg));
	}

	@Test
	void rejectsWithoutThrowing() {
		for (String bad : List.of("", "not json", "{", "[]", "{}", "{\"type\":1}", "{\"type\":\"explode\"}",
			"{\"type\":\"filter\",\"value\":\"weapons\"}", "{\"type\":\"filter\"}", "{\"type\":\"sort\",\"region\":\"world\"}",
			"{\"type\":\"moveAll\",\"to\":7}", "{\"type\":\"page\",\"value\":\"settings\"}", "{\"type\":\"page\"}", "{\"type\":\"turn\",\"deg\":\"left\"}", "{\"type\":\"close\"} trailing")) {
			assertInstanceOf(Bridge.Invalid.class, Bridge.decode(bad), bad);
		}
	}

	@Test
	void repliesTellThePageWhatHappened() {
		Map<String, Object> ok = Json.parseObject(Bridge.reply(new Bridge.Close()));
		assertEquals(Boolean.TRUE, ok.get("ok"));
		Map<String, Object> err = Json.parseObject(Bridge.reply(Bridge.decode("{\"type\":\"nope\"}")));
		assertEquals(Boolean.FALSE, err.get("ok"));
		assertTrue(((String) err.get("error")).contains("nope"));
	}

	@Test
	void callWrapsAMessageForEvaluateScript() {
		String state = Json.object().put("type", "state").put("hover", 36).end();
		assertEquals("hub.apply({\"type\":\"state\",\"hover\":36})", Bridge.call(state));
	}

	@Test
	void hearthActionsKeepTheirShapes() {
		String[][] ok = {{"choose", "npc:news"}, {"choose", "mettle:train/speech"}, {"train", "mettle:speech"}, {"close", "talk"}, {"trade", "12#1"},
			{"haggle", "3#0"}, {"haggleOffer", "190"}, {"haggleAccept", ""}, {"deposit", "-1"}, {"withdraw", "250"}, {"sellEmeralds", "1"},
			{"buyPlot", "7"}, {"fund", "4#10"}, {"accept", "41cb1892-0888-d08d-6dfd-14c4edf1cdab"}, {"confront", ""}, {"confront", "pay"},
			{"lift", "5"}, {"diceToggle", "3"}, {"diceLoaded", "1"}, {"requestReputation", ""}, {"setConversationStyle", "cinematic"},
			{"trackPoster", "5eedf00d-1234-4bcd-9ef0-123456789abc"}, {"untrackPoster", ""}};
		for (String[] c : ok) {
			assertEquals(new Bridge.HearthCall(c[0], c[1]), Bridge.decode(Json.object().put("type", "hearth").put("op", c[0]).put("arg", c[1]).end()), c[0]);
		}
		// A number where a string is expected is read as its whole value.
		assertEquals(new Bridge.HearthCall("lift", "2"), Bridge.decode("{\"type\":\"hearth\",\"op\":\"lift\",\"arg\":2}"));
		String[][] bad = {{"choose", ""}, {"choose", "npc news"}, {"close", "inventory"}, {"trade", "12"}, {"haggleOffer", "-5"}, {"deposit", "1.5"},
			{"sellEmeralds", "0"}, {"accept", "not-a-uuid"}, {"diceToggle", "6"}, {"setConversationStyle", "ledger"}, {"pay", "1"}, {"lift", "1.5"},
			{"trackPoster", ""}, {"trackPoster", "Jory Flint"}, {"untrackPoster", "x"}};
		for (String[] c : bad) {
			assertInstanceOf(Bridge.Invalid.class, Bridge.decode(Json.object().put("type", "hearth").put("op", c[0]).put("arg", c[1]).end()), c[0] + " " + c[1]);
		}
		assertEquals(new Bridge.HearthUi(true, false), Bridge.decode("{\"type\":\"hearthUi\",\"modal\":true}"));
		Bridge.Message fx = Bridge.decode("{\"type\":\"hearthFx\",\"items\":[{\"i\":\"h-dice-0\",\"k\":\"dice\",\"x\":10,\"y\":20,\"w\":568,\"h\":300}],"
			+ "\"stacks\":[{\"id\":\"minecraft:bread\",\"n\":4,\"x\":1,\"y\":2,\"w\":36,\"h\":36}]}");
		Bridge.HearthFx h = assertInstanceOf(Bridge.HearthFx.class, fx);
		assertEquals(1, h.items().size());
		assertEquals("minecraft:bread", h.stacks().getFirst().item());
		// Card kinds are not Hearth's, and Hearth's are not the cards'.
		assertInstanceOf(Bridge.Invalid.class, Bridge.decode("{\"type\":\"hearthFx\",\"items\":[{\"i\":\"a\",\"k\":\"edge-rare\"}]}"));
		assertInstanceOf(Bridge.Invalid.class, Bridge.decode("{\"type\":\"fx\",\"items\":[{\"i\":\"a\",\"k\":\"dice\"}]}"));
	}

	@Test
	void jsonRoundTrips() {
		String s = Json.object().put("a", "x\"y").put("n", 1.25).put("i", 7).put("b", true).putInts("arr", new int[] {1, 2})
			.putStrings("s", List.of("p", "q")).raw("o", "{\"k\":null}").end();
		Map<String, Object> m = Json.parseObject(s);
		assertEquals("x\"y", m.get("a"));
		assertEquals(1.25, m.get("n"));
		assertEquals(7.0, m.get("i"));
		assertEquals(Boolean.TRUE, m.get("b"));
		assertEquals(List.of(1.0, 2.0), m.get("arr"));
		assertEquals(List.of("p", "q"), m.get("s"));
		assertTrue(((Map<?, ?>) m.get("o")).containsKey("k"));
		assertEquals("1", Json.number(1.0));
		assertEquals("0.333333", Json.number(1.0 / 3));
		assertEquals("0", Json.number(Double.NaN));
		assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":}"));
	}
}
