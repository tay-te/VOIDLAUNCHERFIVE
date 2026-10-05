package dev.voidmc.atlas.core.waypoints;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Reads JourneyMap waypoints so players can bring them into Atlas.
 *
 * <p>What is known for certain: JourneyMap 5.x keeps one JSON file per waypoint at
 * {@code <game dir>/journeymap/data/sp/<world>/waypoints/<id>.json} (single player) or {@code .../data/mp/<server>/...}
 * (multiplayer), like
 * {@code {"id":"Home_100,64,-200","name":"Home","icon":"waypoint-normal.png","x":100,"y":64,"z":-200,"r":255,"g":0,
 * "b":0,"enable":true,"type":"Normal","origin":"journeymap","dimensions":["minecraft:overworld"],"persistent":true}}.
 * Older 5.x files give the dimensions as numbers ({@code [0, -1]}). A {@code "type"} of {@code "Death"} is a death
 * point. The world folder's name is what tells worlds apart.
 *
 * <p>What is a best guess: later JourneyMap versions (6.x) are believed to keep a world's waypoints together in one
 * JSON file rather than one per waypoint, and may name fields differently. That format has not been checked against
 * real files, so this reader is deliberately forgiving rather than exact: every {@code .json} file in a
 * {@code waypoints} folder is read, and a file may hold one waypoint object, an array of them, an object with a
 * {@code "waypoints"} array or map, or a map of id to waypoint. In a waypoint, the position may be top-level
 * {@code x}/{@code y}/{@code z} or inside a {@code "pos"} or {@code "position"} object; the colour may be
 * {@code r}/{@code g}/{@code b} or one {@code "color"}/{@code "colour"} RGB number; enabled may be {@code "enable"} or
 * {@code "enabled"}; the dimension may be the first of {@code "dimensions"} or a {@code "dimension"} or
 * {@code "primaryDimension"} value. If 6.x turns out to differ, its waypoints are skipped, not misread: anything
 * without a numeric x and z is ignored.
 *
 * <p>A waypoint shown in several dimensions is imported once, in the first one listed (the one its position belongs
 * to). Files that will not parse, and waypoints outside the world, are skipped. Nothing here throws on bad input.
 */
public final class JourneyMapImport {
	private static final Consumer<String> QUIET = s -> {
	};

	private JourneyMapImport() {
	}

	/** Every JourneyMap waypoint under the game directory, grouped by world folder (alphabetically) with repeats removed. */
	public static List<ImportedWaypoint> scan(Path gameDir) {
		return scan(gameDir, QUIET);
	}

	/** As {@link #scan(Path)}, telling {@code warn} about each folder, file or waypoint it had to skip. */
	public static List<ImportedWaypoint> scan(Path gameDir, Consumer<String> warn) {
		Path dir = ImportFiles.gameDir(gameDir);
		List<ImportedWaypoint> all = new ArrayList<>();
		if (dir == null) {
			return all;
		}
		Path data = dir.resolve("journeymap").resolve("data");
		for (String kind : new String[]{"sp", "mp"}) {
			for (Path world : ImportFiles.list(data.resolve(kind), Files::isDirectory, warn)) {
				Path waypoints = world.resolve("waypoints");
				for (Path file : ImportFiles.list(waypoints, JourneyMapImport::isJson, warn)) {
					all.addAll(parseFile(file, world.getFileName().toString(), warn));
				}
			}
		}
		List<ImportedWaypoint> out = ImportFiles.dedupe(all);
		List<ImportedWaypoint> grouped = new ArrayList<>(out.size());
		ImportedWaypoint.byWorld(out).values().forEach(grouped::addAll);
		return grouped;
	}

	/** The waypoints in one JourneyMap JSON file, filed under {@code world}. */
	public static List<ImportedWaypoint> parseFile(Path file, String world, Consumer<String> warn) {
		String text = ImportFiles.read(file, warn);
		if (text == null) {
			return new ArrayList<>();
		}
		return parse(text, world, file.toString(), warn);
	}

	/** The waypoints in the text of one JourneyMap JSON file. {@code where} names the file in warnings. */
	public static List<ImportedWaypoint> parse(String json, String world, String where, Consumer<String> warn) {
		List<ImportedWaypoint> out = new ArrayList<>();
		JsonElement root;
		try {
			root = JsonParser.parseString(json);
		} catch (RuntimeException | StackOverflowError e) {
			warn.accept("Skipped " + where + ": not valid JSON");
			return out;
		}
		collect(root, world, where, warn, out, 0);
		return out;
	}

	private static void collect(JsonElement e, String world, String where, Consumer<String> warn, List<ImportedWaypoint> out, int depth) {
		if (e == null || depth > 3) {
			return;
		}
		if (e.isJsonArray()) {
			for (JsonElement item : e.getAsJsonArray()) {
				collect(item, world, where, warn, out, depth + 1);
			}
			return;
		}
		if (!e.isJsonObject()) {
			return;
		}
		JsonObject o = e.getAsJsonObject();
		if (looksLikeWaypoint(o)) {
			ImportedWaypoint w = read(o, world);
			if (w == null) {
				warn.accept("Skipped a waypoint in " + where + ": not one Atlas can read");
			} else {
				out.add(w);
			}
			return;
		}
		if (o.has("waypoints")) {
			collect(o.get("waypoints"), world, where, warn, out, depth + 1);
			return;
		}
		for (Map.Entry<String, JsonElement> entry : o.entrySet()) {
			if (entry.getValue().isJsonObject()) {
				collect(entry.getValue(), world, where, warn, out, depth + 1);
			}
		}
	}

	private static boolean looksLikeWaypoint(JsonObject o) {
		return o.has("x") && o.has("z") || o.has("pos") && o.get("pos").isJsonObject() || o.has("position") && o.get("position").isJsonObject();
	}

	/** One waypoint object, or null when it lacks a usable position. */
	static ImportedWaypoint read(JsonObject o, String world) {
		JsonObject pos = o;
		for (String key : new String[]{"pos", "position"}) {
			if (!o.has("x") && o.has(key) && o.get(key).isJsonObject()) {
				pos = o.getAsJsonObject(key);
			}
		}
		Long x = number(pos.get("x"));
		Long z = number(pos.get("z"));
		if (x == null || z == null || !ImportFiles.inWorld(x, z)) {
			return null;
		}
		Long yl = number(pos.get("y"));
		Integer y = yl == null || Math.abs(yl) > CoordParser.Y_LIMIT ? null : yl.intValue();

		int colour = AtlasColours.INK;
		Long r = number(o.get("r"));
		Long g = number(o.get("g"));
		Long b = number(o.get("b"));
		if (r != null && g != null && b != null) {
			colour = AtlasColours.nearest((channel(r) << 16) | (channel(g) << 8) | channel(b));
		} else {
			Long rgb = number(o.has("color") ? o.get("color") : o.get("colour"));
			if (rgb != null) {
				colour = AtlasColours.nearest((int) (rgb & 0xFFFFFF));
			}
		}

		boolean enabled = bool(o.has("enable") ? o.get("enable") : o.get("enabled"), true);
		String type = string(o.get("type"));
		boolean death = type != null && type.toLowerCase(Locale.ROOT).contains("death");

		String dimension = null;
		JsonElement dims = o.get("dimensions");
		if (dims != null && dims.isJsonArray()) {
			JsonArray arr = dims.getAsJsonArray();
			for (int i = 0; i < arr.size() && dimension == null; i++) {
				dimension = dimension(arr.get(i));
			}
		}
		for (String key : new String[]{"primaryDimension", "dimension", "dim"}) {
			if (dimension == null) {
				dimension = dimension(o.get(key));
			}
		}
		return new ImportedWaypoint(ImportedWaypoint.Source.JOURNEYMAP, world, string(o.get("name")), x.intValue(), y, z.intValue(),
			dimension == null ? DimensionIds.OVERWORLD : dimension, colour, death, !enabled);
	}

	private static String dimension(JsonElement e) {
		if (e == null || !e.isJsonPrimitive()) {
			return null;
		}
		JsonPrimitive p = e.getAsJsonPrimitive();
		if (p.isNumber()) {
			return DimensionIds.normalise(Long.toString(p.getAsLong()));
		}
		return DimensionIds.normalise(p.getAsString());
	}

	private static Long number(JsonElement e) {
		if (e == null || !e.isJsonPrimitive()) {
			return null;
		}
		JsonPrimitive p = e.getAsJsonPrimitive();
		try {
			double d = p.isNumber() ? p.getAsDouble() : p.isString() ? Double.parseDouble(p.getAsString().strip()) : Double.NaN;
			if (!Double.isFinite(d) || Math.abs(d) > 1e15) {
				return null;
			}
			return (long) Math.floor(d);
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private static String string(JsonElement e) {
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}

	private static boolean bool(JsonElement e, boolean fallback) {
		if (e == null || !e.isJsonPrimitive()) {
			return fallback;
		}
		JsonPrimitive p = e.getAsJsonPrimitive();
		if (p.isBoolean()) {
			return p.getAsBoolean();
		}
		if (p.isString()) {
			return !p.getAsString().strip().equalsIgnoreCase("false");
		}
		return fallback;
	}

	private static int channel(long v) {
		return (int) Math.max(0, Math.min(255, v));
	}

	private static boolean isJson(Path p) {
		return Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json");
	}
}
