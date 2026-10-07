package dev.voidmc.atlas.core.social;

import dev.voidmc.atlas.api.Privacy;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.atlas.api.AtlasSocialTransport;
import dev.voidmc.atlas.api.AtlasSocialTransport.Presence;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The loopback hub (two and three players, topics, queueing, leaving) and the no-op default. */
class TransportTest {
	private record Got(String to, String from, String text) {
	}

	private static byte[] text(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}

	@Test
	void deliversToEveryOtherSubscriberButNeverBackToTheSender() throws Exception {
		LoopbackTransport.Hub hub = new LoopbackTransport.Hub();
		List<Got> got = new ArrayList<>();
		for (String p : List.of("alice", "bob", "carol")) {
			hub.join(p).subscribe(AtlasSocialTransport.TOPIC_POSITION, (from, data) -> got.add(new Got(p, from, new String(data, StandardCharsets.UTF_8))));
		}
		LoopbackTransport alice = hub.join("alice");
		alice.subscribe(AtlasSocialTransport.TOPIC_POSITION, (from, data) -> got.add(new Got("alice", from, "x")));
		// alice joined again: her first end is closed, her second is the one that publishes
		alice.publish(AtlasSocialTransport.TOPIC_POSITION, text("hi"));
		assertTrue(got.isEmpty(), "nothing arrives before the hub delivers");
		assertEquals(1, hub.pending());
		assertEquals(2, hub.deliver());
		assertEquals(List.of(new Got("bob", "alice", "hi"), new Got("carol", "alice", "hi")), got);
		assertEquals(0, hub.pending());
		assertEquals(2, hub.bytesPublished("alice"));
	}

	@Test
	void twoPlayersTalkBothWays() {
		LoopbackTransport.Hub hub = new LoopbackTransport.Hub();
		LoopbackTransport a = hub.join("a");
		LoopbackTransport b = hub.join("b");
		List<String> atA = new ArrayList<>();
		List<String> atB = new ArrayList<>();
		a.subscribe("waypoint", (from, data) -> atA.add(from + ":" + new String(data, StandardCharsets.UTF_8)));
		b.subscribe("waypoint", (from, data) -> atB.add(from + ":" + new String(data, StandardCharsets.UTF_8)));
		a.publish("waypoint", text("1"));
		b.publish("waypoint", text("2"));
		hub.deliver();
		assertEquals(List.of("b:2"), atA);
		assertEquals(List.of("a:1"), atB);
	}

	@Test
	void topicsMatchExactlyOrByPrefixStar() {
		assertTrue(AtlasSocialTransport.matches("tiles:*", "tiles:minecraft:overworld:3,-2"));
		assertTrue(AtlasSocialTransport.matches("position", "position"));
		assertFalse(AtlasSocialTransport.matches("position", "positions"));
		assertFalse(AtlasSocialTransport.matches("tiles:*", "position"));
		assertEquals("tiles:minecraft:the_nether:-1,4", AtlasSocialTransport.tilesTopic("minecraft:the_nether", -1, 4));

		LoopbackTransport.Hub hub = new LoopbackTransport.Hub();
		LoopbackTransport a = hub.join("a");
		List<String> topics = new ArrayList<>();
		hub.join("b").subscribe("tiles:*", (from, data) -> topics.add(new String(data, StandardCharsets.UTF_8)));
		a.publish(AtlasSocialTransport.tilesTopic("minecraft:overworld", 0, 0), text("t"));
		a.publish(AtlasSocialTransport.TOPIC_POSITION, text("p"));
		hub.deliver();
		assertEquals(List.of("t"), topics);
	}

	@Test
	void closingStopsDeliveryAndLeavingStopsEverything() throws Exception {
		LoopbackTransport.Hub hub = new LoopbackTransport.Hub();
		LoopbackTransport a = hub.join("a");
		LoopbackTransport b = hub.join("b");
		List<String> got = new ArrayList<>();
		AutoCloseable sub = b.subscribe("position", (from, data) -> got.add(from));
		a.publish("position", text("1"));
		sub.close();
		hub.deliver();
		assertTrue(got.isEmpty(), "a closed subscription gets nothing, even what was queued before");

		b.subscribe("position", (from, data) -> got.add(from));
		b.close();
		assertFalse(b.available());
		b.publish("position", text("ignored"));
		a.publish("position", text("2"));
		hub.deliver();
		assertTrue(got.isEmpty());
		assertEquals(0, hub.bytesPublished("b"));
		assertEquals(List.of("a"), hub.members());
	}

	@Test
	void payloadsAreCopiedNotShared() {
		LoopbackTransport.Hub hub = new LoopbackTransport.Hub();
		LoopbackTransport a = hub.join("a");
		List<byte[]> got = new ArrayList<>();
		hub.join("b").subscribe("position", (from, data) -> got.add(data));
		byte[] sent = text("abc");
		a.publish("position", sent);
		sent[0] = 'z';
		hub.deliver();
		assertArrayEquals(text("abc"), got.get(0));
	}

	@Test
	void messagesPublishedWhileDeliveringWaitForTheNextRound() {
		LoopbackTransport.Hub hub = new LoopbackTransport.Hub();
		LoopbackTransport a = hub.join("a");
		LoopbackTransport b = hub.join("b");
		List<String> atA = new ArrayList<>();
		b.subscribe("ping", (from, data) -> b.publish("pong", data));
		a.subscribe("pong", (from, data) -> atA.add(from));
		a.publish("ping", text("1"));
		assertEquals(1, hub.deliver());
		assertTrue(atA.isEmpty());
		assertEquals(1, hub.pending());
		hub.deliverAll(5);
		assertEquals(List.of("b"), atA);
	}

	@Test
	void presenceIsRecorded() {
		LoopbackTransport.Hub hub = new LoopbackTransport.Hub();
		LoopbackTransport a = hub.join("a");
		Presence p = new Presence(true, true, ServerHash.of("play.example.com"), "minecraft:overworld", Privacy.FRIENDS);
		a.presence(p);
		assertEquals(p, hub.presenceOf("a"));
		assertEquals(Privacy.DEFAULT, new Presence(true, false, null, null, null).privacy());
		assertEquals(Privacy.PARTY, Presence.menus(null).privacy());
	}

	@Test
	void noopIsNeverAvailableAndDoesNothing() throws Exception {
		NoopTransport t = NoopTransport.INSTANCE;
		assertFalse(t.available());
		t.publish("position", text("x"));
		t.presence(Presence.menus(Privacy.NOBODY));
		t.subscribe("position", (from, data) -> {
			throw new AssertionError("never called");
		}).close();
	}
}
