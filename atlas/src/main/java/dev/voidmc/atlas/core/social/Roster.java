package dev.voidmc.atlas.core.social;

import dev.voidmc.atlas.api.Privacy;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Who this player's friends are and who is in their party, as the transport side last said (Atlas does not keep
 * friends lists itself). Player ids are compared exactly. Thread-safe: the sets are replaced whole, never changed.
 */
public final class Roster {
	private volatile Set<String> friends = Set.of();
	private volatile Set<String> party = Set.of();

	/** Replaces the friends list. */
	public void setFriends(Collection<String> players) {
		this.friends = copy(players);
	}

	/** Replaces the party (empty when not in one). This player's own id may be in it; it is never shared with. */
	public void setParty(Collection<String> players) {
		this.party = copy(players);
	}

	public boolean isFriend(String player) {
		return player != null && this.friends.contains(player);
	}

	public boolean inParty(String player) {
		return player != null && this.party.contains(player);
	}

	public Set<String> friends() {
		return this.friends;
	}

	public Set<String> party() {
		return this.party;
	}

	/**
	 * Whether this player, sharing at {@code mine}, may share with {@code peer} (and accept what the peer shares):
	 * {@link Privacy#FRIENDS} allows friends and party members, {@link Privacy#PARTY} only party members, and
	 * {@link Privacy#NOBODY} no one.
	 */
	public boolean mayShare(Privacy mine, String peer) {
		if (peer == null || peer.isBlank() || mine == null) {
			return false;
		}
		return switch (mine) {
			case FRIENDS -> isFriend(peer) || inParty(peer);
			case PARTY -> inParty(peer);
			case NOBODY -> false;
		};
	}

	private static Set<String> copy(Collection<String> players) {
		if (players == null || players.isEmpty()) {
			return Set.of();
		}
		Set<String> out = new LinkedHashSet<>();
		for (String p : players) {
			if (p != null && !p.isBlank()) {
				out.add(p.strip());
			}
		}
		return Set.copyOf(out);
	}
}
