package dev.voidmc.atlas.api;

import java.util.Locale;
import java.util.Objects;

/**
 * Where a marker or a place comes from, shown on it as a source chip:
 *
 * <ul>
 *   <li>{@link Kind#SEEN}: Atlas worked it out from what the client was sent (blocks, the map) — works anywhere,
 *   vanilla servers included;</li>
 *   <li>{@link Kind#EXACT}: the game's own structure data (singleplayer, where the integrated server is at hand);</li>
 *   <li>{@link Kind#SERVER}: a server told the client about it (a VOID server: {@link #voidServer()});</li>
 *   <li>{@link Kind#PLUGIN}: another mod's provider ({@code id} is the provider's id, like {@code hearth:settlements});</li>
 *   <li>{@link Kind#PLAYER}: the player made it (a waypoint).</li>
 * </ul>
 *
 * @param kind the kind of source
 * @param id   who: {@code atlas} for Atlas's own, a server's tag, or a provider id
 */
public record Source(Kind kind, String id) {
	public enum Kind {
		SEEN, EXACT, SERVER, PLUGIN, PLAYER
	}

	public static final Source SEEN_BY_ATLAS = new Source(Kind.SEEN, "atlas");
	public static final Source EXACT_BY_ATLAS = new Source(Kind.EXACT, "atlas");
	public static final Source BY_PLAYER = new Source(Kind.PLAYER, "atlas");
	/** The tag a VOID server's places carry. */
	public static final String VOID_SERVER = "void";

	public Source {
		Objects.requireNonNull(kind, "kind");
		id = id == null || id.isBlank() ? "atlas" : id;
	}

	public static Source seen() {
		return SEEN_BY_ATLAS;
	}

	public static Source exact() {
		return EXACT_BY_ATLAS;
	}

	public static Source player() {
		return BY_PLAYER;
	}

	/** A place a VOID server told the client about. */
	public static Source voidServer() {
		return new Source(Kind.SERVER, VOID_SERVER);
	}

	public static Source server(String tag) {
		return new Source(Kind.SERVER, tag);
	}

	/** Another mod's marker or place; {@code providerId} like {@code hearth:settlements}. */
	public static Source plugin(String providerId) {
		return new Source(Kind.PLUGIN, providerId);
	}

	/** The chip's text: SEEN, EXACT, VOID SERVER, a server's tag, the providing mod's id ("HEARTH"), YOU. */
	public String label() {
		return switch (this.kind) {
			case SEEN -> "SEEN";
			case EXACT -> "EXACT";
			case SERVER -> this.id.equals(VOID_SERVER) ? "VOID SERVER" : this.id.toUpperCase(Locale.ROOT) + " SERVER";
			case PLUGIN -> {
				int colon = this.id.indexOf(':');
				yield (colon > 0 ? this.id.substring(0, colon) : this.id).toUpperCase(Locale.ROOT);
			}
			case PLAYER -> "YOU";
		};
	}
}
