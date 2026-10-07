package dev.voidmc.atlas.api;

import java.util.function.BiConsumer;

/**
 * How Atlas talks to other players: friends' positions, shared waypoints and party-shared explored terrain. Atlas
 * never hard-codes a transport; the default ({@code NoopTransport}) is not {@link #available()}, so every friend
 * feature stays hidden until a real transport is installed (the planned one is the launcher's localhost WebSocket
 * bridge, which forwards to a backend).
 *
 * <p>Messages are published to <em>topics</em>, and the transport delivers each one to the other players who should
 * see it, saying who sent it. The transport is a dumb pipe: it does not need to read payloads, and it has no
 * backpressure, so Atlas limits its own rates (positions at about 1 Hz, terrain under a bytes-per-second budget).
 * Privacy is also enforced by Atlas on both ends; a backend may route by {@link Presence#privacy()} as well.
 *
 * <p>Topics and their payloads:
 * <ul>
 *   <li>{@value #TOPIC_POSITION}: where a player is, about once a second. JSON, UTF-8.</li>
 *   <li>{@value #TOPIC_WAYPOINT}: waypoints sent to friends. JSON, UTF-8.</li>
 *   <li>{@code tiles:<dimension>:<rx>,<rz>} (see {@link #tilesTopic}): explored terrain of one region, as compact
 *   binary (deflated, with its own name table and checksum).</li>
 * </ul>
 * Every payload names its sender inside it; receivers drop a message whose inner sender is not the one the
 * transport says sent it, so the transport's sender id must be the authenticated one.
 *
 * <p>For a bridge carrying this over JSON (messages discriminated by a {@code t} field), the suggested envelope is:
 * {@code {"t":"atlas.publish","topic":..,"data":<base64>}} up, {@code {"t":"atlas.message","topic":..,"from":..,
 * "data":<base64>}} down, {@code {"t":"atlas.subscribe","topic":..}} and {@code {"t":"atlas.unsubscribe","topic":..}},
 * and {@code {"t":"atlas.presence","online":..,"inWorld":..,"server":..,"dim":..,"privacy":"friends"|"party"|"nobody"}}.
 * Payloads are base64 even when they are JSON, so the bridge never has to parse them.
 */
public interface AtlasSocialTransport {
	/** Positions, about 1 Hz per player. */
	String TOPIC_POSITION = "position";
	/** Waypoints sent to friends. */
	String TOPIC_WAYPOINT = "waypoint";
	/** The start of every terrain topic. */
	String TILES_PREFIX = "tiles:";

	/**
	 * Sends a payload to whoever is subscribed to the topic (never back to this player). Must not block for long. The
	 * transport may keep the array: Atlas never changes one after publishing it.
	 */
	void publish(String topic, byte[] payload);

	/**
	 * Calls {@code handler(fromPlayer, payload)} for every message other players publish to the topic, until the
	 * returned handle is closed. A topic ending in {@code *} matches every topic that starts with what comes before
	 * the star (so {@code tiles:*} is all terrain). Handlers may be called on any thread.
	 */
	AutoCloseable subscribe(String topic, BiConsumer<String, byte[]> handler);

	/** Tells the transport (and through it, friends) what this player is doing. */
	void presence(Presence state);

	/** Whether there is anyone to talk to; when false, Atlas hides every friend feature. */
	boolean available();

	/** The player's own id on this transport (what others see as {@code from}); null while unknown (nothing is sent). */
	default String localPlayer() {
		return null;
	}

	/** The player's friends' ids (the launcher's friends list); may change at any time. */
	default java.util.Collection<String> friends() {
		return java.util.List.of();
	}

	/** The ids of the players in the player's party now. */
	default java.util.Collection<String> party() {
		return java.util.List.of();
	}

	/** The terrain topic for one region: {@code tiles:<dimension>:<rx>,<rz>}, like {@code tiles:minecraft:overworld:3,-2}. */
	static String tilesTopic(String dimension, int rx, int rz) {
		return TILES_PREFIX + dimension + ":" + rx + "," + rz;
	}

	/** Whether a topic matches a subscription pattern: equal, or the pattern ends in {@code *} and the topic starts with the rest. */
	static boolean matches(String pattern, String topic) {
		if (pattern == null || topic == null) {
			return false;
		}
		if (pattern.endsWith("*")) {
			return topic.startsWith(pattern.substring(0, pattern.length() - 1));
		}
		return pattern.equals(topic);
	}

	/**
	 * What this player is doing, for friends lists and for routing.
	 *
	 * @param online     false to appear offline (nothing is shared then)
	 * @param inWorld    true while in a world, false in menus
	 * @param serverHash which server, as a hash of its address (never the address itself); null when not on a server
	 * @param dimension  the dimension id, like {@code minecraft:overworld}; null when not in a world
	 * @param privacy    who may see this player's position and terrain
	 */
	record Presence(boolean online, boolean inWorld, String serverHash, String dimension, Privacy privacy) {
		public Presence {
			privacy = privacy == null ? Privacy.DEFAULT : privacy;
		}

		/** Online, in no world. */
		public static Presence menus(Privacy privacy) {
			return new Presence(true, false, null, null, privacy);
		}
	}
}
