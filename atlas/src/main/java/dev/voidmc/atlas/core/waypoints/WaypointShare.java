package dev.voidmc.atlas.core.waypoints;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The text form VOID players paste to each other to share waypoints: {@code atlas-wp:1:<data>}, where {@code 1} is
 * the format version and the data is compact JSON in URL-safe base64 without padding, so it survives chat, Discord
 * and forum posts (no spaces, quotes, colons or slashes). One code can carry one waypoint or a whole list.
 *
 * <p>The JSON is {@code {"w":[{"n":name,"x":..,"y":..,"z":..,"d":dim,"c":colour,"t":note,"e":expiry}]}}. {@code y},
 * {@code t} and {@code e} are left out when absent and {@code c} when it is 0. The vanilla dimensions are written as
 * {@code o}, {@code n} and {@code e} and left out entirely for the overworld; other dimensions are written as their id.
 *
 * <p>Decoding forgives whitespace around the code and inside the data (line breaks from pasting), ignores fields it
 * does not know (so version 1 can grow), and refuses: a code from a newer format version (with a message saying to
 * update Atlas), more than {@value #MAX_WAYPOINTS} waypoints, a code longer than {@value #MAX_CODE_LENGTH} characters,
 * and anything that is not valid base64 or JSON. Names are cut to {@value SharedWaypoint#MAX_NAME} characters and
 * positions are held to the world: x and z within {@value CoordParser#XZ_LIMIT}, y within {@value CoordParser#Y_LIMIT}.
 */
public final class WaypointShare {
	public static final String PREFIX = "atlas-wp:";
	public static final int VERSION = 1;
	public static final int MAX_WAYPOINTS = 256;
	/** Room for {@value #MAX_WAYPOINTS} waypoints with full names and notes, and not much more. */
	public static final int MAX_CODE_LENGTH = 160_000;

	private static final Pattern CODE = Pattern.compile("atlas-wp:\\d{1,4}:[A-Za-z0-9_-]+");

	private WaypointShare() {
	}

	/** Why a share code could not be read. The message is fit to show to the player. */
	public static final class ShareFormatException extends IllegalArgumentException {
		private final boolean newerVersion;

		ShareFormatException(String message, boolean newerVersion) {
			super(message);
			this.newerVersion = newerVersion;
		}

		ShareFormatException(String message) {
			this(message, false);
		}

		/** True when the code came from a newer Atlas than this one, rather than being damaged. */
		public boolean newerVersion() {
			return this.newerVersion;
		}
	}

	/** The share code for one waypoint. */
	public static String encode(SharedWaypoint waypoint) {
		return encode(List.of(waypoint));
	}

	/** The share code for a list of 1 to {@value #MAX_WAYPOINTS} waypoints, in order. */
	public static String encode(List<SharedWaypoint> waypoints) {
		if (waypoints == null || waypoints.isEmpty()) {
			throw new IllegalArgumentException("Nothing to share");
		}
		if (waypoints.size() > MAX_WAYPOINTS) {
			throw new IllegalArgumentException("Too many waypoints to share at once (" + waypoints.size() + "; the most is " + MAX_WAYPOINTS + ")");
		}
		JsonArray list = new JsonArray();
		for (SharedWaypoint w : waypoints) {
			JsonObject o = new JsonObject();
			o.addProperty("n", w.name());
			o.addProperty("x", w.x());
			if (w.y() != null) {
				o.addProperty("y", w.y());
			}
			o.addProperty("z", w.z());
			String dim = shortDimension(w.dimension());
			if (dim != null) {
				o.addProperty("d", dim);
			}
			if (w.colour() != 0) {
				o.addProperty("c", w.colour());
			}
			if (w.note() != null) {
				o.addProperty("t", w.note());
			}
			if (w.expiresAt() != null) {
				o.addProperty("e", w.expiresAt());
			}
			list.add(o);
		}
		JsonObject root = new JsonObject();
		root.add("w", list);
		byte[] json = root.toString().getBytes(StandardCharsets.UTF_8);
		return PREFIX + VERSION + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(json);
	}

	/** Whether the text looks like a share code (after trimming), without fully reading it. */
	public static boolean looksLikeCode(String text) {
		return text != null && text.strip().regionMatches(true, 0, PREFIX, 0, PREFIX.length());
	}

	/** The share codes inside a longer text, such as a chat message, in order. Each is a substring of the text. */
	public static List<String> findCodes(String text) {
		List<String> codes = new ArrayList<>();
		if (text == null) {
			return codes;
		}
		Matcher m = CODE.matcher(text);
		while (m.find()) {
			codes.add(m.group());
		}
		return codes;
	}

	/** Reads a share code. Throws {@link ShareFormatException}, with a message for the player, if it cannot. */
	public static List<SharedWaypoint> decode(String code) {
		if (code == null) {
			throw new ShareFormatException("No waypoint code given");
		}
		String s = code.strip();
		if (s.length() > MAX_CODE_LENGTH) {
			throw new ShareFormatException("That waypoint code is too long");
		}
		if (!s.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
			throw new ShareFormatException("Not an Atlas waypoint code (it should start with " + PREFIX + ")");
		}
		int colon = s.indexOf(':', PREFIX.length());
		if (colon < 0) {
			throw new ShareFormatException("That waypoint code is cut short");
		}
		String versionText = s.substring(PREFIX.length(), colon).strip();
		int version;
		try {
			version = Integer.parseInt(versionText);
		} catch (NumberFormatException e) {
			throw new ShareFormatException("That waypoint code is damaged (bad version '" + clip(versionText) + "')");
		}
		if (version > VERSION) {
			throw new ShareFormatException("That waypoint code was made by a newer version of Atlas (format " + version
				+ "; this one reads up to " + VERSION + "). Update Atlas to read it.", true);
		}
		if (version < 1) {
			throw new ShareFormatException("That waypoint code is damaged (bad version " + version + ")");
		}
		String data = s.substring(colon + 1).replaceAll("\\s+", "");
		if (data.isEmpty()) {
			throw new ShareFormatException("That waypoint code is cut short");
		}
		byte[] bytes;
		try {
			bytes = Base64.getUrlDecoder().decode(data);
		} catch (IllegalArgumentException e) {
			throw new ShareFormatException("That waypoint code is damaged (it has characters a code cannot have, or was cut short)");
		}
		String json;
		try {
			json = StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(bytes))
				.toString();
		} catch (CharacterCodingException e) {
			throw new ShareFormatException("That waypoint code is damaged (not text inside)");
		}
		return read(json);
	}

	private static List<SharedWaypoint> read(String json) {
		JsonElement root;
		try {
			root = JsonParser.parseString(json);
		} catch (JsonParseException | IllegalStateException | StackOverflowError e) {
			throw new ShareFormatException("That waypoint code is damaged (the data inside does not read)");
		}
		if (!root.isJsonObject() || !root.getAsJsonObject().has("w") || !root.getAsJsonObject().get("w").isJsonArray()) {
			throw new ShareFormatException("That waypoint code is damaged (no waypoints inside)");
		}
		JsonArray list = root.getAsJsonObject().getAsJsonArray("w");
		if (list.isEmpty()) {
			throw new ShareFormatException("That waypoint code holds no waypoints");
		}
		if (list.size() > MAX_WAYPOINTS) {
			throw new ShareFormatException("That waypoint code holds too many waypoints (" + list.size() + "; the most is " + MAX_WAYPOINTS + ")");
		}
		List<SharedWaypoint> out = new ArrayList<>(list.size());
		for (int i = 0; i < list.size(); i++) {
			JsonElement e = list.get(i);
			if (!e.isJsonObject()) {
				throw new ShareFormatException("That waypoint code is damaged (waypoint " + (i + 1) + " does not read)");
			}
			JsonObject o = e.getAsJsonObject();
			Long x = number(o, "x");
			Long z = number(o, "z");
			if (x == null || z == null) {
				throw new ShareFormatException("That waypoint code is damaged (waypoint " + (i + 1) + " has no position)");
			}
			Long y = number(o, "y");
			Long colour = number(o, "c");
			Long expiry = number(o, "e");
			String dim = string(o, "d");
			out.add(new SharedWaypoint(
				string(o, "n"),
				(int) clamp(x, CoordParser.XZ_LIMIT),
				y == null ? null : (int) clamp(y, CoordParser.Y_LIMIT),
				(int) clamp(z, CoordParser.XZ_LIMIT),
				longDimension(dim),
				colour == null ? 0 : (int) Math.floorMod(colour, 8L),
				string(o, "t"),
				expiry));
		}
		return out;
	}

	private static Long number(JsonObject o, String key) {
		JsonElement e = o.get(key);
		if (e == null || !e.isJsonPrimitive()) {
			return null;
		}
		JsonPrimitive p = e.getAsJsonPrimitive();
		if (!p.isNumber()) {
			return null;
		}
		String text = p.getAsString();
		if (text.matches("-?\\d{1,18}")) {
			// whole numbers are read exactly, so expiry times keep every millisecond
			return Long.parseLong(text);
		}
		double d = p.getAsDouble();
		if (!Double.isFinite(d)) {
			return null;
		}
		return (long) Math.floor(Math.max(Long.MIN_VALUE, Math.min(Long.MAX_VALUE, d)));
	}

	private static String string(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
	}

	private static long clamp(long v, int limit) {
		return Math.max(-limit, Math.min(limit, v));
	}

	/** The dimension as written in a code: null (left out) for the overworld, a letter for the other vanilla ones. */
	private static String shortDimension(String id) {
		String canon = DimensionIds.normalise(id);
		if (canon == null) {
			return id;
		}
		return switch (canon) {
			case DimensionIds.OVERWORLD -> null;
			case DimensionIds.NETHER -> "n";
			case DimensionIds.END -> "e";
			default -> canon;
		};
	}

	private static String longDimension(String written) {
		if (written == null || written.isBlank()) {
			return DimensionIds.OVERWORLD;
		}
		return switch (written) {
			case "o" -> DimensionIds.OVERWORLD;
			case "n" -> DimensionIds.NETHER;
			case "e" -> DimensionIds.END;
			default -> {
				String id = DimensionIds.normalise(written);
				yield id == null ? DimensionIds.OVERWORLD : id;
			}
		};
	}

	private static String clip(String s) {
		return s.length() > 12 ? s.substring(0, 12) + "..." : s;
	}
}
