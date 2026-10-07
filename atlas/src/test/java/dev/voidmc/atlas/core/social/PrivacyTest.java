package dev.voidmc.atlas.core.social;

import dev.voidmc.atlas.api.Privacy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Privacy scopes against the roster, and server hashes. */
class PrivacyTest {
	static Roster roster() {
		Roster r = new Roster();
		r.setFriends(List.of("friend", "both"));
		r.setParty(List.of("member", "both"));
		return r;
	}

	@Test
	void defaultIsParty() {
		assertEquals(Privacy.PARTY, Privacy.DEFAULT);
		assertEquals(Privacy.FRIENDS, Privacy.parse(" Friends "));
		assertEquals(Privacy.NOBODY, Privacy.parse("nobody"));
		assertEquals(Privacy.PARTY, Privacy.parse("everyone"));
		assertEquals(Privacy.PARTY, Privacy.parse(null));
		assertEquals("party", Privacy.PARTY.id());
	}

	@Test
	void friendsScopeAllowsFriendsAndPartyMembers() {
		Roster r = roster();
		assertTrue(r.mayShare(Privacy.FRIENDS, "friend"));
		assertTrue(r.mayShare(Privacy.FRIENDS, "member"));
		assertTrue(r.mayShare(Privacy.FRIENDS, "both"));
		assertFalse(r.mayShare(Privacy.FRIENDS, "stranger"));
	}

	@Test
	void partyScopeAllowsOnlyPartyMembers() {
		Roster r = roster();
		assertFalse(r.mayShare(Privacy.PARTY, "friend"));
		assertTrue(r.mayShare(Privacy.PARTY, "member"));
		assertTrue(r.mayShare(Privacy.PARTY, "both"));
		assertFalse(r.mayShare(Privacy.PARTY, "stranger"));
	}

	@Test
	void nobodyScopeAllowsNoOne() {
		Roster r = roster();
		for (String p : List.of("friend", "member", "both", "stranger")) {
			assertFalse(r.mayShare(Privacy.NOBODY, p), p);
		}
	}

	@Test
	void blankAndMissingPeersAreNeverAllowed() {
		Roster r = roster();
		r.setFriends(Arrays.asList("", " ", null, "x"));
		assertEquals(1, r.friends().size());
		assertFalse(r.mayShare(Privacy.FRIENDS, null));
		assertFalse(r.mayShare(Privacy.FRIENDS, " "));
		assertFalse(r.mayShare(null, "x"));
	}

	@Test
	void theRosterIsReplacedWhole() {
		Roster r = roster();
		r.setParty(List.of());
		assertFalse(r.mayShare(Privacy.PARTY, "member"));
		assertTrue(r.mayShare(Privacy.FRIENDS, "both"));
	}

	@Test
	void serverHashesIgnoreCaseSpacesAndTheDefaultPort() {
		String h = ServerHash.of("play.example.com");
		assertEquals(32, h.length());
		assertEquals(h, ServerHash.of("  Play.Example.COM:25565 "));
		assertEquals(h, ServerHash.of("play.example.com."));
		assertNotEquals(h, ServerHash.of("play.example.com:25566"));
		assertNotEquals(h, ServerHash.of("other.example.com"));
		assertEquals(ServerHash.of("[::1]"), ServerHash.of("[::1]:25565"));
		assertNull(ServerHash.of(" "));
		assertNull(ServerHash.of(null));
		assertFalse(h.contains("example"));
	}
}
