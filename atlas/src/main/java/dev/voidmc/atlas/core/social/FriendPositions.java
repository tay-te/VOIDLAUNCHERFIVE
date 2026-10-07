package dev.voidmc.atlas.core.social;

import dev.voidmc.atlas.api.Privacy;
import com.google.gson.JsonObject;
import dev.voidmc.atlas.api.AtlasSocialTransport;
import dev.voidmc.atlas.core.waypoints.CoordParser;
import dev.voidmc.atlas.core.waypoints.DimensionIds;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Where friends are: reads their position messages, keeps the latest from each, and hands out markers that fade after
 * {@value #FADE_AFTER_MS} ms without news and vanish after {@value #DROP_AFTER_MS} ms. It also decides when this
 * player's own position is worth sending: at most once every {@value #SEND_EVERY_MS} ms, and then only after moving
 * more than a block, changing dimension, or {@value #RESEND_IDLE_MS} ms of standing still.
 *
 * <p>Only positions from peers this player's {@link Privacy} allows, and from the same server (by
 * {@link ServerHash hash}), are kept; a peer who stops being allowed disappears from the next snapshot. Ages are
 * measured from when a message arrived, not from the sender's clock.
 *
 * <p>The message, on topic {@value AtlasSocialTransport#TOPIC_POSITION}, is UTF-8 JSON of at most
 * {@value #MAX_PAYLOAD_BYTES} bytes: {@code {"t":"atlas.position","v":1,"player":id,"name":name,
 * "dim":"minecraft:overworld","x":12.5,"y":64.0,"z":-80.25,"yaw":90.0,"server":hash,"ts":epochMillis}}. Unknown
 * fields are ignored. Thread-safe.
 */
public final class FriendPositions {
	public static final String TYPE = "atlas.position";
	public static final int VERSION = 1;
	public static final int MAX_PAYLOAD_BYTES = 4096;
	public static final long FADE_AFTER_MS = 10_000;
	public static final long DROP_AFTER_MS = 30_000;
	public static final long SEND_EVERY_MS = 1_000;
	public static final long RESEND_IDLE_MS = 10_000;
	/** Blocks of movement that make a new position worth sending. */
	public static final double MOVE_THRESHOLD = 1.0;
	public static final int MAX_NAME = 32;
	public static final int MAX_ID = 64;
	/** The most friends followed at once. */
	public static final int MAX_TRACKED = 256;

	/**
	 * One position message.
	 *
	 * @param player    the sender's player id
	 * @param name      the sender's display name
	 * @param dimension a dimension id
	 * @param yaw       degrees, -180 to 180
	 * @param server    the {@link ServerHash} of the server the sender is on
	 * @param timestamp the sender's clock, epoch milliseconds
	 */
	public record Position(String player, String name, String dimension, double x, double y, double z, float yaw, String server, long timestamp) {
		public Position {
			Objects.requireNonNull(player, "player");
			name = name == null || name.isBlank() ? player : cut(name.strip(), MAX_NAME);
			dimension = DimensionIds.orOverworld(dimension);
			yaw = wrapYaw(yaw);
		}
	}

	/**
	 * A friend to draw on the map.
	 *
	 * @param ageMs how long since their last position arrived
	 */
	public record Marker(String player, String name, String dimension, double x, double y, double z, float yaw, long ageMs) {
		/** Whether the position is old enough to draw faded. */
		public boolean faded() {
			return this.ageMs >= FADE_AFTER_MS;
		}

		/** 1 while fresh, then falling evenly to 0 when the marker is dropped. */
		public float opacity() {
			if (this.ageMs <= FADE_AFTER_MS) {
				return 1f;
			}
			return (float) Math.max(0.0, 1.0 - (double) (this.ageMs - FADE_AFTER_MS) / (DROP_AFTER_MS - FADE_AFTER_MS));
		}
	}

	private record Entry(Position position, long receivedAt) {
	}

	private final String localPlayer;
	private final Roster roster;
	private final Map<String, Entry> latest = new LinkedHashMap<>();
	private String server;
	private Privacy privacy = Privacy.DEFAULT;
	private Position lastSent;
	private long lastSentAt;

	public FriendPositions(String localPlayer, Roster roster) {
		this.localPlayer = Objects.requireNonNull(localPlayer, "localPlayer");
		this.roster = Objects.requireNonNull(roster, "roster");
	}

	/**
	 * Where this player is and how much they share: {@code serverHash} null when not on a server (nothing is sent or
	 * kept then). Positions from another server, or from peers no longer allowed, are forgotten.
	 */
	public synchronized void local(String serverHash, Privacy privacy) {
		this.server = serverHash == null || serverHash.isBlank() ? null : serverHash;
		this.privacy = privacy == null ? Privacy.DEFAULT : privacy;
		this.latest.values().removeIf(e -> !e.position.server().equals(this.server) || !this.roster.mayShare(this.privacy, e.position.player()));
		if (this.privacy == Privacy.NOBODY || this.server == null) {
			this.lastSent = null;
		}
	}

	/** Reads one message the transport says came from {@code fromPlayer}, arriving at {@code now}. */
	public synchronized Received receive(String fromPlayer, byte[] payload, long now) {
		if (fromPlayer == null || fromPlayer.equals(this.localPlayer)) {
			return Received.FROM_SELF;
		}
		if (!this.roster.mayShare(this.privacy, fromPlayer)) {
			return Received.NOT_ALLOWED;
		}
		if (this.server == null) {
			return Received.OTHER_SERVER;
		}
		if (payload == null || payload.length > MAX_PAYLOAD_BYTES) {
			return Received.TOO_LARGE;
		}
		Position p;
		try {
			p = decode(payload);
		} catch (IOException e) {
			return Received.MALFORMED;
		}
		if (!p.player().equals(fromPlayer)) {
			return Received.WRONG_SENDER;
		}
		if (!p.server().equals(this.server)) {
			return Received.OTHER_SERVER;
		}
		Entry old = this.latest.get(fromPlayer);
		if (old != null && p.timestamp() < old.position.timestamp()) {
			return Received.OUT_OF_ORDER;
		}
		if (old == null && this.latest.size() >= MAX_TRACKED) {
			prune(now);
			if (this.latest.size() >= MAX_TRACKED) {
				return Received.FULL;
			}
		}
		this.latest.put(fromPlayer, new Entry(p, now));
		return Received.ACCEPTED;
	}

	/** The friends to draw at {@code now}, by name; positions older than {@value #DROP_AFTER_MS} ms are forgotten. */
	public synchronized List<Marker> snapshot(long now) {
		prune(now);
		List<Marker> out = new ArrayList<>(this.latest.size());
		for (Entry e : this.latest.values()) {
			Position p = e.position;
			if (!this.roster.mayShare(this.privacy, p.player())) {
				continue;
			}
			out.add(new Marker(p.player(), p.name(), p.dimension(), p.x(), p.y(), p.z(), p.yaw(), Math.max(0, now - e.receivedAt)));
		}
		out.sort(Comparator.comparing((Marker m) -> m.name().toLowerCase(Locale.ROOT)).thenComparing(Marker::player));
		return out;
	}

	/** Forgets one friend's position (they left, or were unfriended). */
	public synchronized void forget(String player) {
		this.latest.remove(player);
	}

	public synchronized void clear() {
		this.latest.clear();
	}

	/**
	 * This player's position as a message, when it is time to send one; null otherwise (too soon, not moved, sharing
	 * off, or not on a server). Publish what it returns to {@value AtlasSocialTransport#TOPIC_POSITION}.
	 */
	public synchronized byte[] outgoing(String name, String dimension, double x, double y, double z, float yaw, long now) {
		if (this.privacy == Privacy.NOBODY || this.server == null) {
			return null;
		}
		Position me = new Position(this.localPlayer, name, dimension, x, y, z, yaw, this.server, now);
		if (this.lastSent != null) {
			long since = now - this.lastSentAt;
			if (since >= 0 && since < SEND_EVERY_MS) {
				return null;
			}
			Position last = this.lastSent;
			double dx = me.x() - last.x();
			double dy = me.y() - last.y();
			double dz = me.z() - last.z();
			boolean moved = dx * dx + dy * dy + dz * dz > MOVE_THRESHOLD * MOVE_THRESHOLD
				|| !me.dimension().equals(last.dimension())
				|| !me.server().equals(last.server());
			if (!moved && since >= 0 && since < RESEND_IDLE_MS) {
				return null;
			}
		}
		this.lastSent = me;
		this.lastSentAt = now;
		return encode(me);
	}

	/** Feeds every position message the transport delivers into this, timed by {@code clock}. */
	public AutoCloseable listen(AtlasSocialTransport transport, LongSupplier clock) {
		return transport.subscribe(AtlasSocialTransport.TOPIC_POSITION, (from, payload) -> receive(from, payload, clock.getAsLong()));
	}

	/** The message for a position. */
	public static byte[] encode(Position p) {
		JsonObject o = new JsonObject();
		o.addProperty("t", TYPE);
		o.addProperty("v", VERSION);
		o.addProperty("player", p.player());
		o.addProperty("name", p.name());
		o.addProperty("dim", p.dimension());
		o.addProperty("x", round(p.x(), 100));
		o.addProperty("y", round(p.y(), 100));
		o.addProperty("z", round(p.z(), 100));
		o.addProperty("yaw", round(p.yaw(), 10));
		o.addProperty("server", p.server());
		o.addProperty("ts", p.timestamp());
		return o.toString().getBytes(StandardCharsets.UTF_8);
	}

	/** Reads a position message; throws {@link IOException} if it is damaged, too large or not one. */
	public static Position decode(byte[] payload) throws IOException {
		JsonObject o = SocialJson.read(payload, MAX_PAYLOAD_BYTES, TYPE, VERSION);
		String player = SocialJson.id(o, "player", MAX_ID);
		String server = SocialJson.id(o, "server", 128);
		String dim = DimensionIds.normalise(SocialJson.string(o, "dim"));
		Double x = SocialJson.number(o, "x");
		Double y = SocialJson.number(o, "y");
		Double z = SocialJson.number(o, "z");
		Double yaw = SocialJson.number(o, "yaw");
		Long ts = SocialJson.whole(o, "ts");
		if (player == null || server == null || dim == null || x == null || y == null || z == null || ts == null) {
			throw new IOException("missing fields");
		}
		if (Math.abs(x) > CoordParser.XZ_LIMIT || Math.abs(z) > CoordParser.XZ_LIMIT || Math.abs(y) > CoordParser.Y_LIMIT) {
			throw new IOException("position outside the world");
		}
		return new Position(player, SocialJson.string(o, "name"), dim, x, y, z, yaw == null ? 0f : (float) (double) yaw, server, ts);
	}

	private void prune(long now) {
		Iterator<Entry> it = this.latest.values().iterator();
		while (it.hasNext()) {
			if (now - it.next().receivedAt >= DROP_AFTER_MS) {
				it.remove();
			}
		}
	}

	private static double round(double v, int per) {
		return Math.round(v * per) / (double) per;
	}

	private static float wrapYaw(float yaw) {
		if (!Float.isFinite(yaw)) {
			return 0f;
		}
		float w = (float) (((yaw + 180.0) % 360.0 + 360.0) % 360.0 - 180.0);
		return w;
	}

	static String cut(String s, int max) {
		if (s.length() <= max) {
			return s;
		}
		int end = max;
		if (Character.isHighSurrogate(s.charAt(end - 1))) {
			end--;
		}
		return s.substring(0, end).strip();
	}
}
