package dev.voidmc.atlas.core.social;

import dev.voidmc.atlas.api.Privacy;
import com.google.gson.JsonObject;
import dev.voidmc.atlas.api.AtlasSocialTransport;
import dev.voidmc.atlas.core.waypoints.SharedWaypoint;
import dev.voidmc.atlas.core.waypoints.WaypointShare;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Waypoints sent to friends over the transport, on topic {@value AtlasSocialTransport#TOPIC_WAYPOINT}: UTF-8 JSON of
 * at most {@value #MAX_PAYLOAD_BYTES} bytes, {@code {"t":"atlas.waypoint","v":1,"player":id,"server":hash,
 * "code":"atlas-wp:1:..."}}, where {@code code} is the same {@link WaypointShare} code players paste in chat, so
 * there is one waypoint format. Receivers check sender, privacy and server like every other social message.
 */
public final class WaypointMessages {
	public static final String TYPE = "atlas.waypoint";
	public static final int VERSION = 1;
	public static final int MAX_PAYLOAD_BYTES = WaypointShare.MAX_CODE_LENGTH + 1024;

	private WaypointMessages() {
	}

	/** One message: who sent it, on which server, and the waypoints. */
	public record Message(String player, String server, List<SharedWaypoint> waypoints) {
		public Message {
			Objects.requireNonNull(player, "player");
			Objects.requireNonNull(server, "server");
			waypoints = List.copyOf(waypoints);
		}
	}

	public static byte[] encode(Message m) {
		JsonObject o = new JsonObject();
		o.addProperty("t", TYPE);
		o.addProperty("v", VERSION);
		o.addProperty("player", m.player());
		o.addProperty("server", m.server());
		o.addProperty("code", WaypointShare.encode(m.waypoints()));
		return o.toString().getBytes(StandardCharsets.UTF_8);
	}

	/** Reads a message; throws {@link IOException} if it is damaged, too large or not one. */
	public static Message decode(byte[] payload) throws IOException {
		JsonObject o = SocialJson.read(payload, MAX_PAYLOAD_BYTES, TYPE, VERSION);
		String player = SocialJson.id(o, "player", FriendPositions.MAX_ID);
		String server = SocialJson.id(o, "server", 128);
		String code = SocialJson.string(o, "code");
		if (player == null || server == null || code == null) {
			throw new IOException("missing fields");
		}
		try {
			return new Message(player, server, WaypointShare.decode(code));
		} catch (IllegalArgumentException e) {
			throw new IOException(e.getMessage());
		}
	}

	/**
	 * Whether a message the transport says came from {@code fromPlayer} should be shown to this player: from someone
	 * else, allowed by {@code privacy}, sent by who it says, and about the same server.
	 */
	public static Received check(String localPlayer, String localServer, Privacy privacy, Roster roster, String fromPlayer, byte[] payload) {
		if (fromPlayer == null || fromPlayer.equals(localPlayer)) {
			return Received.FROM_SELF;
		}
		if (!roster.mayShare(privacy, fromPlayer)) {
			return Received.NOT_ALLOWED;
		}
		if (localServer == null) {
			return Received.OTHER_SERVER;
		}
		if (payload == null || payload.length > MAX_PAYLOAD_BYTES) {
			return Received.TOO_LARGE;
		}
		Message m;
		try {
			m = decode(payload);
		} catch (IOException e) {
			return Received.MALFORMED;
		}
		if (!m.player().equals(fromPlayer)) {
			return Received.WRONG_SENDER;
		}
		return m.server().equals(localServer) ? Received.ACCEPTED : Received.OTHER_SERVER;
	}
}
