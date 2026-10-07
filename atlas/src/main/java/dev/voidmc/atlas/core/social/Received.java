package dev.voidmc.atlas.core.social;

import dev.voidmc.atlas.api.Privacy;
/** What happened to a message from another player. Everything but {@link #ACCEPTED} means it was dropped. */
public enum Received {
	/** Read and kept. */
	ACCEPTED,
	/** It came from this player. */
	FROM_SELF,
	/** Privacy does not allow this peer (not a friend or party member, or sharing is off). */
	NOT_ALLOWED,
	/** It is about another server than the one this player is on (or this player is on none). */
	OTHER_SERVER,
	/** The sender named inside is not the one the transport says sent it. */
	WRONG_SENDER,
	/** Bigger than any message of its kind may be; not read. */
	TOO_LARGE,
	/** Damaged, cut short, from a newer format, or nonsense. */
	MALFORMED,
	/** Older than what is already known from that sender. */
	OUT_OF_ORDER,
	/** Well formed, but there is no room left to keep it. */
	FULL
}
