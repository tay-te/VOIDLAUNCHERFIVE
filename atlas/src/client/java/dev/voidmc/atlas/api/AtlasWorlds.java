package dev.voidmc.atlas.api;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The maps Atlas keeps for the server the player is on: one per distinct world behind its address (a proxy's lobbies
 * and games, a server's worlds), told apart by their terrain and the signals the game gives, never mixed. Atlas picks
 * the right one by itself; when it cannot be sure it starts a new one and asks, through {@link #question()}, whether
 * it is the same as one before. Client thread.
 */
public interface AtlasWorlds {
	/** A map of a world. */
	record WorldInfo(String id, String name, String server, boolean current, long lastUsed, int places) {
	}

	/** Atlas started a new map but it may be an old world: "keep separate" or "same as before" ({@code candidate}). */
	record Question(String newWorld, String candidate, String candidateName, String server, long askedAt) {
	}

	/** Something the player should hear about, quietly (the design's islands). */
	record Notice(Kind kind, String title, String detail) {
		public enum Kind {
			/** A new world was found behind this address (sure). */
			NEW_WORLD,
			/** A new world may be an old one: see {@link AtlasWorlds#question()}. */
			UNSURE_WORLD,
			/** A file was damaged and was restored from a backup. */
			RESTORED,
			/** The server turned features off (or on). */
			SERVER_RULES,
			/** Waypoints were found in other map mods' files. */
			IMPORT_AVAILABLE
		}
	}

	/** The map being shown and added to, or empty while Atlas is still working out which world this is. */
	Optional<WorldInfo> current();

	/** Every map kept for the current server (or the singleplayer world), the current one first. */
	List<WorldInfo> list();

	/** Renames a map (the player's own name for it). */
	void rename(String id, String name);

	/** The open question, if Atlas is not sure the world is new. */
	Optional<Question> question();

	/**
	 * Answers the open question: {@code sameAsBefore} merges the new map into the earlier one (its waypoints and
	 * what was mapped since) and goes on there; otherwise the new map is kept as a world of its own.
	 */
	void answer(boolean sameAsBefore);

	/** Hears about new worlds, questions, restored files and server rules. */
	void addListener(Consumer<Notice> listener);
}
