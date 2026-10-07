package dev.voidmc.atlas.core.social;

import dev.voidmc.atlas.api.Privacy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.atlas.api.AtlasSocialTransport;
import dev.voidmc.atlas.core.social.FriendPositions.Marker;
import dev.voidmc.atlas.core.social.FriendPositions.Position;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Friends' positions: the message, privacy, server matching, staleness, the send throttle and hostile input. */
class FriendPositionsTest {
	static final String SERVER = ServerHash.of("play.example.com");
	static final String OTHER = ServerHash.of("other.example.com");

	private static Position at(String player, double x, long ts) {
		return new Position(player, player.toUpperCase(), "minecraft:overworld", x, 64, 0, 0f, SERVER, ts);
	}

	private static byte[] bytes(String json) {
		return json.getBytes(StandardCharsets.UTF_8);
	}

	private static FriendPositions positions(String me, Roster roster, Privacy privacy) {
		FriendPositions p = new FriendPositions(me, roster);
		p.local(SERVER, privacy);
		return p;
	}

	@Test
	void theMessageRoundTripsWithTheDocumentedFields() throws IOException {
		Position p = new Position("u-1", "Steve", "nether", 12.504, 64, -80.256, 450f, SERVER, 1_790_000_000_123L);
		byte[] msg = FriendPositions.encode(p);
		JsonObject o = JsonParser.parseString(new String(msg, StandardCharsets.UTF_8)).getAsJsonObject();
		assertEquals(Set.of("t", "v", "player", "name", "dim", "x", "y", "z", "yaw", "server", "ts"), o.keySet());
		assertEquals("atlas.position", o.get("t").getAsString());
		assertEquals(1, o.get("v").getAsInt());
		assertEquals("minecraft:the_nether", o.get("dim").getAsString());

		Position back = FriendPositions.decode(msg);
		assertEquals("u-1", back.player());
		assertEquals("Steve", back.name());
		assertEquals("minecraft:the_nether", back.dimension());
		assertEquals(12.5, back.x(), 1e-9);
		assertEquals(-80.26, back.z(), 1e-9);
		assertEquals(90f, back.yaw(), 1e-4);
		assertEquals(SERVER, back.server());
		assertEquals(1_790_000_000_123L, back.timestamp());
	}

	@Test
	void threePlayersSeeEachOtherOverLoopback() {
		LoopbackTransport.Hub hub = new LoopbackTransport.Hub();
		Roster roster = new Roster();
		roster.setParty(List.of("alice", "bob", "carol"));
		AtomicLong clock = new AtomicLong(1_000);
		FriendPositions[] views = new FriendPositions[3];
		LoopbackTransport[] ends = new LoopbackTransport[3];
		String[] names = {"alice", "bob", "carol"};
		for (int i = 0; i < 3; i++) {
			ends[i] = hub.join(names[i]);
			views[i] = positions(names[i], roster, Privacy.PARTY);
			views[i].listen(ends[i], clock::get);
		}
		for (int i = 0; i < 3; i++) {
			byte[] out = views[i].outgoing(names[i], "minecraft:overworld", i * 100, 70, 5, 0f, clock.get());
			ends[i].publish(AtlasSocialTransport.TOPIC_POSITION, out);
		}
		hub.deliver();
		for (int i = 0; i < 3; i++) {
			List<Marker> seen = views[i].snapshot(clock.get());
			int me = i;
			assertEquals(2, seen.size(), names[i]);
			assertTrue(seen.stream().noneMatch(m -> m.player().equals(names[me])), "no marker for yourself");
		}
		Marker bob = views[0].snapshot(clock.get()).stream().filter(m -> m.player().equals("bob")).findFirst().orElseThrow();
		assertEquals(100, bob.x(), 1e-9);
		assertEquals(70, bob.y(), 1e-9);
		assertEquals("minecraft:overworld", bob.dimension());
		assertEquals(0, bob.ageMs());
	}

	@Test
	void privacyFiltersEachScope() {
		Roster roster = PrivacyTest.roster();
		String[] peers = {"friend", "member", "both", "stranger"};
		// expected acceptance per scope, in the order of peers
		boolean[][] allowed = {
			{true, true, true, false},   // FRIENDS
			{false, true, true, false},  // PARTY
			{false, false, false, false} // NOBODY
		};
		Privacy[] scopes = {Privacy.FRIENDS, Privacy.PARTY, Privacy.NOBODY};
		for (int s = 0; s < scopes.length; s++) {
			FriendPositions view = positions("me", roster, scopes[s]);
			for (int p = 0; p < peers.length; p++) {
				Received r = view.receive(peers[p], FriendPositions.encode(at(peers[p], p, 1)), 0);
				assertEquals(allowed[s][p] ? Received.ACCEPTED : Received.NOT_ALLOWED, r, scopes[s] + " " + peers[p]);
			}
			int expected = 0;
			for (boolean a : allowed[s]) {
				expected += a ? 1 : 0;
			}
			assertEquals(expected, view.snapshot(0).size(), scopes[s].toString());
		}
	}

	@Test
	void aPeerWhoLeavesThePartyVanishesAtOnce() {
		Roster roster = new Roster();
		roster.setParty(List.of("bob"));
		FriendPositions view = positions("me", roster, Privacy.PARTY);
		assertEquals(Received.ACCEPTED, view.receive("bob", FriendPositions.encode(at("bob", 0, 1)), 0));
		assertEquals(1, view.snapshot(0).size());
		roster.setParty(List.of());
		assertTrue(view.snapshot(0).isEmpty());
	}

	@Test
	void onlyTheSameServerIsAccepted() {
		Roster roster = new Roster();
		roster.setParty(List.of("bob"));
		FriendPositions view = positions("me", roster, Privacy.PARTY);
		Position elsewhere = new Position("bob", "Bob", "minecraft:overworld", 0, 64, 0, 0f, OTHER, 1);
		assertEquals(Received.OTHER_SERVER, view.receive("bob", FriendPositions.encode(elsewhere), 0));
		assertEquals(Received.ACCEPTED, view.receive("bob", FriendPositions.encode(at("bob", 0, 1)), 0));

		view.local(OTHER, Privacy.PARTY);
		assertTrue(view.snapshot(0).isEmpty(), "moving server forgets positions from the old one");

		view.local(null, Privacy.PARTY);
		assertEquals(Received.OTHER_SERVER, view.receive("bob", FriendPositions.encode(at("bob", 0, 2)), 0));
	}

	@Test
	void positionsFadeAfterTenSecondsAndDropAfterThirty() {
		Roster roster = new Roster();
		roster.setParty(List.of("bob"));
		FriendPositions view = positions("me", roster, Privacy.PARTY);
		view.receive("bob", FriendPositions.encode(at("bob", 0, 1)), 1_000);

		Marker fresh = view.snapshot(6_000).get(0);
		assertEquals(5_000, fresh.ageMs());
		assertTrue(!fresh.faded());
		assertEquals(1f, fresh.opacity());

		Marker fading = view.snapshot(11_000).get(0);
		assertTrue(fading.faded());
		assertEquals(1f, fading.opacity(), 1e-6);

		assertEquals(0.5f, view.snapshot(21_000).get(0).opacity(), 1e-6);
		assertEquals(1, view.snapshot(30_999).size());
		assertTrue(view.snapshot(31_000).isEmpty(), "dropped after 30 s");
		assertTrue(view.snapshot(1_000).isEmpty(), "and forgotten, not just hidden");
	}

	@Test
	void olderMessagesDoNotReplaceNewerOnes() {
		Roster roster = new Roster();
		roster.setParty(List.of("bob"));
		FriendPositions view = positions("me", roster, Privacy.PARTY);
		assertEquals(Received.ACCEPTED, view.receive("bob", FriendPositions.encode(at("bob", 10, 2_000)), 0));
		assertEquals(Received.OUT_OF_ORDER, view.receive("bob", FriendPositions.encode(at("bob", 5, 1_000)), 0));
		assertEquals(10, view.snapshot(0).get(0).x(), 1e-9);
	}

	@Test
	void ownPositionsAreThrottledToOneHertzAndOnlyWhenMoved() {
		FriendPositions view = positions("me", new Roster(), Privacy.PARTY);
		assertNotNull(view.outgoing("Me", "minecraft:overworld", 0, 64, 0, 0f, 0), "the first is always sent");
		assertNull(view.outgoing("Me", "minecraft:overworld", 50, 64, 0, 0f, 500), "never more than 1 Hz");
		assertNull(view.outgoing("Me", "minecraft:overworld", 0.5, 64, 0.5, 0f, 1_000), "under a block moved");
		assertNotNull(view.outgoing("Me", "minecraft:overworld", 2, 64, 0, 0f, 1_000), "moved more than a block");
		assertNull(view.outgoing("Me", "minecraft:overworld", 2, 64, 0, 0f, 5_000));
		assertNull(view.outgoing("Me", "minecraft:overworld", 2, 64, 0, 0f, 10_999));
		assertNotNull(view.outgoing("Me", "minecraft:overworld", 2, 64, 0, 0f, 11_000), "standing still, every 10 s");
		assertNotNull(view.outgoing("Me", "minecraft:the_nether", 2, 64, 0, 0f, 12_000), "a new dimension counts as moving");

		view.local(SERVER, Privacy.NOBODY);
		assertNull(view.outgoing("Me", "minecraft:overworld", 900, 64, 0, 0f, 60_000), "nothing when sharing is off");
		view.local(null, Privacy.PARTY);
		assertNull(view.outgoing("Me", "minecraft:overworld", 900, 64, 0, 0f, 70_000), "nothing off a server");
		view.local(SERVER, Privacy.PARTY);
		assertNotNull(view.outgoing("Me", "minecraft:overworld", 900, 64, 0, 0f, 70_100));
	}

	@Test
	void aSenderCannotSpeakForSomeoneElse() {
		Roster roster = new Roster();
		roster.setParty(List.of("bob", "carol"));
		FriendPositions view = positions("me", roster, Privacy.PARTY);
		assertEquals(Received.WRONG_SENDER, view.receive("carol", FriendPositions.encode(at("bob", 0, 1)), 0));
		assertEquals(Received.FROM_SELF, view.receive("me", FriendPositions.encode(at("me", 0, 1)), 0));
		assertTrue(view.snapshot(0).isEmpty());
	}

	@Test
	void damagedAndHostileMessagesAreRejected() {
		Roster roster = new Roster();
		roster.setParty(List.of("bob"));
		FriendPositions view = positions("me", roster, Privacy.PARTY);
		String ok = "\"player\":\"bob\",\"dim\":\"minecraft:overworld\",\"x\":1,\"y\":64,\"z\":2,\"server\":\"" + SERVER + "\",\"ts\":5";
		assertEquals(Received.ACCEPTED, view.receive("bob", bytes("{\"t\":\"atlas.position\",\"v\":1," + ok + ",\"extra\":[1,2]}"), 0));
		List<byte[]> bad = List.of(
			new byte[0],
			bytes("not json at all {"),
			new byte[] {(byte) 0xC3, (byte) 0x28},
			bytes("[1,2,3]"),
			bytes("{\"t\":\"atlas.waypoint\",\"v\":1," + ok + "}"),
			bytes("{\"t\":\"atlas.position\",\"v\":2," + ok + "}"),
			bytes("{\"t\":\"atlas.position\"," + ok + "}"),
			bytes("{\"t\":\"atlas.position\",\"v\":1,\"player\":\"bob\",\"server\":\"" + SERVER + "\",\"ts\":5}"),
			bytes("{\"t\":\"atlas.position\",\"v\":1," + ok.replace("\"x\":1", "\"x\":40000000") + "}"),
			bytes("{\"t\":\"atlas.position\",\"v\":1," + ok.replace("\"x\":1", "\"x\":NaN") + "}"),
			bytes("{\"t\":\"atlas.position\",\"v\":1," + ok.replace("\"x\":1", "\"x\":\"1\"") + "}"),
			bytes("{\"t\":\"atlas.position\",\"v\":1," + ok.replace("minecraft:overworld", "not a dim!") + "}"),
			bytes("[".repeat(3000)));
		for (byte[] b : bad) {
			assertEquals(Received.MALFORMED, view.receive("bob", b, 0), new String(b, StandardCharsets.ISO_8859_1));
		}
		assertEquals(Received.TOO_LARGE, view.receive("bob", bytes("{\"pad\":\"" + "x".repeat(5000) + "\"}"), 0));
		assertThrows(IOException.class, () -> FriendPositions.decode(bytes("{\"pad\":\"" + "x".repeat(5000) + "\"}")));
		assertEquals(1, view.snapshot(0).size());
	}

	@Test
	void namesAreCutAndBlankNamesFallBackToTheId() {
		Position p = new Position("bob", "x".repeat(100), "minecraft:overworld", 0, 0, 0, 0f, SERVER, 0);
		assertEquals(FriendPositions.MAX_NAME, p.name().length());
		assertEquals("bob", new Position("bob", " ", null, 0, 0, 0, 0f, SERVER, 0).name());
	}

	@Test
	void snapshotsAreSortedByName() {
		Roster roster = new Roster();
		roster.setParty(List.of("u3", "u1", "u2"));
		FriendPositions view = positions("me", roster, Privacy.PARTY);
		view.receive("u3", FriendPositions.encode(new Position("u3", "carol", null, 0, 0, 0, 0f, SERVER, 1)), 0);
		view.receive("u1", FriendPositions.encode(new Position("u1", "Alice", null, 0, 0, 0, 0f, SERVER, 1)), 0);
		view.receive("u2", FriendPositions.encode(new Position("u2", "bob", null, 0, 0, 0, 0f, SERVER, 1)), 0);
		assertEquals(List.of("Alice", "bob", "carol"), view.snapshot(0).stream().map(Marker::name).collect(Collectors.toList()));
	}
}
