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
			"{\"type\":\"moveAll\",\"to\":7}", "{\"type\":\"turn\",\"deg\":\"left\"}", "{\"type\":\"close\"} trailing")) {
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
