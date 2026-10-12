package dev.voidmc.reel.shot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.voidmc.reel.math.Angles;
import dev.voidmc.reel.math.Easing;
import dev.voidmc.reel.math.Vec3;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads a shot file. Errors name the field they are about ("camera.keys[2].pos: expected [x, y, z]"), and unknown
 * fields are errors too, so a typo fails loudly instead of quietly doing nothing. The format is in docs/SHOTS.md.
 */
public final class ShotParser {
	public static final double DEFAULT_FOV = 70;

	private ShotParser() {
	}

	public static Shot read(Path file) throws IOException {
		String name = file.getFileName().toString().replaceFirst("\\.json$", "");
		return parse(Files.readString(file), name);
	}

	public static Shot parse(String json, String fallbackName) {
		JsonObject root;
		try {
			root = JsonParser.parseString(json).getAsJsonObject();
		} catch (JsonParseException | IllegalStateException e) {
			throw new ShotException("", "not a JSON object: " + e.getMessage());
		}
		known(root, "", Set.of("name", "fps", "duration", "size", "world", "camera", "shake", "render", "notes"));
		String name = root.has("name") ? string(root, "name", "") : fallbackName;
		int fps = (int) number(root, "fps", "", 60);
		if (fps < 1 || fps > 240) {
			throw new ShotException("fps", "must be 1-240, was " + fps);
		}
		int width = 1920;
		int height = 1080;
		if (root.has("size")) {
			double[] size = numbers(root.get("size"), "size", 2);
			width = (int) size[0];
			height = (int) size[1];
		}

		JsonObject camera = object(root, "camera", "");
		known(camera, "camera", Set.of("rig", "keys", "timing", "ease", "look", "face", "fov", "lens", "orbit"));
		Easing ease = camera.has("ease") ? ease(camera, "camera") : Easing.IN_OUT_SINE;
		double fov = fov(camera, "camera", DEFAULT_FOV);
		String rigName = camera.has("rig") ? word(camera, "rig", "camera") : (camera.has("orbit") ? "orbit" : "path");

		Double duration = root.has("duration") ? number(root, "duration", "", 0) : null;
		Rig rig;
		switch (rigName) {
			case "path" -> {
				List<PathRig.Key> keys = keys(camera);
				boolean allTimed = keys.stream().allMatch(k -> k.t() != null);
				boolean anyTimed = keys.stream().anyMatch(k -> k.t() != null);
				String timingName = camera.has("timing") ? word(camera, "timing", "camera") : (allTimed && keys.size() > 1 ? "keyed" : "even");
				PathRig.Timing timing = switch (timingName) {
					case "even" -> PathRig.Timing.EVEN;
					case "keyed" -> PathRig.Timing.KEYED;
					default -> throw new ShotException("camera.timing", "must be \"even\" or \"keyed\", was \"" + timingName + "\"");
				};
				if (timing == PathRig.Timing.KEYED) {
					if (!allTimed) {
						throw new ShotException("camera.keys", "keyed timing needs a \"t\" on every key");
					}
					for (int i = 1; i < keys.size(); i++) {
						if (!(keys.get(i).t() > keys.get(i - 1).t())) {
							throw new ShotException("camera.keys[" + i + "].t", "key times must increase");
						}
					}
					if (duration == null) {
						duration = keys.getLast().t();
					}
				} else if (anyTimed && !camera.has("timing")) {
					throw new ShotException("camera.keys", "some keys have \"t\" and some do not: time every key, or none (even timing)");
				}
				if (duration == null) {
					throw new ShotException("duration", "missing: how many seconds the shot lasts");
				}
				Vec3 look = camera.has("look") ? vec(camera.get("look"), "camera.look") : null;
				boolean facePath = false;
				if (camera.has("face")) {
					String face = word(camera, "face", "camera");
					if (!face.equals("path")) {
						throw new ShotException("camera.face", "only \"path\" is known, was \"" + face + "\"");
					}
					facePath = true;
				}
				try {
					rig = new PathRig(keys, duration, timing, ease, look, facePath, fov);
				} catch (IllegalArgumentException e) {
					throw new ShotException("camera", e.getMessage());
				}
			}
			case "orbit" -> {
				if (duration == null) {
					throw new ShotException("duration", "missing: how many seconds the shot lasts");
				}
				JsonObject orbit = object(camera, "orbit", "camera");
				String at = "camera.orbit";
				known(orbit, at, Set.of("center", "radius", "height", "aim", "from", "sweep"));
				rig = new OrbitRig(vec(orbit.get("center"), at + ".center"), range(orbit, "radius", at, null), range(orbit, "height", at, 0.0),
					range(orbit, "aim", at, 0.0), number(orbit, "from", at, 0), number(orbit, "sweep", at, 90), duration, ease, fov);
			}
			default -> throw new ShotException("camera.rig", "must be \"path\" or \"orbit\", was \"" + rigName + "\"");
		}
		if (duration <= 0) {
			throw new ShotException("duration", "must be more than 0");
		}

		Shake shake = null;
		if (root.has("shake")) {
			JsonObject s = object(root, "shake", "");
			known(s, "shake", Set.of("amount", "speed", "seed"));
			shake = new Shake(number(s, "amount", "shake", 0.3), number(s, "speed", "shake", 0.25), (long) number(s, "seed", "shake", 1));
		}

		Shot.World world = Shot.World.UNCHANGED;
		if (root.has("world")) {
			JsonObject w = object(root, "world", "");
			known(w, "world", Set.of("time", "timeRate", "weather", "tick"));
			String weather = w.has("weather") ? word(w, "weather", "world") : null;
			if (weather != null && !Set.of("clear", "rain", "thunder").contains(weather)) {
				throw new ShotException("world.weather", "must be clear, rain or thunder, was \"" + weather + "\"");
			}
			String tick = w.has("tick") ? word(w, "tick", "world") : "run";
			if (!tick.equals("run") && !tick.equals("freeze")) {
				throw new ShotException("world.tick", "must be \"run\" or \"freeze\", was \"" + tick + "\"");
			}
			world = new Shot.World(w.has("time") ? number(w, "time", "world", 0) : null, number(w, "timeRate", "world", 1), weather, tick.equals("freeze"));
		}

		Shot.Render render = Shot.Render.DEFAULT;
		if (root.has("render")) {
			JsonObject r = object(root, "render", "");
			known(r, "render", Set.of("shutter", "samples", "preroll"));
			render = new Shot.Render(number(r, "shutter", "render", 180), (int) number(r, "samples", "render", 1), number(r, "preroll", "render", 0));
			if (render.samples() < 1 || render.samples() > 64) {
				throw new ShotException("render.samples", "must be 1-64");
			}
		}
		return new Shot(name, fps, duration, width, height, world, rig, shake, render);
	}

	private static List<PathRig.Key> keys(JsonObject camera) {
		if (!camera.has("keys") || !camera.get("keys").isJsonArray() || camera.getAsJsonArray("keys").isEmpty()) {
			throw new ShotException("camera.keys", "a path needs a list of at least one key");
		}
		JsonArray array = camera.getAsJsonArray("keys");
		List<PathRig.Key> keys = new ArrayList<>();
		for (int i = 0; i < array.size(); i++) {
			String at = "camera.keys[" + i + "]";
			if (!array.get(i).isJsonObject()) {
				throw new ShotException(at, "expected an object");
			}
			JsonObject k = array.get(i).getAsJsonObject();
			known(k, at, Set.of("pos", "t", "yaw", "pitch", "roll", "fov", "lens", "look", "note"));
			if (!k.has("pos")) {
				throw new ShotException(at + ".pos", "missing");
			}
			keys.add(new PathRig.Key(vec(k.get("pos"), at + ".pos"), optional(k, "t", at), optional(k, "yaw", at), optional(k, "pitch", at),
				optional(k, "roll", at), k.has("fov") || k.has("lens") ? fov(k, at, 0) : null, k.has("look") ? vec(k.get("look"), at + ".look") : null));
		}
		return keys;
	}

	private static double fov(JsonObject o, String at, double fallback) {
		if (o.has("fov") && o.has("lens")) {
			throw new ShotException(at, "give fov or lens, not both");
		}
		if (o.has("lens")) {
			double mm = number(o, "lens", at, 35);
			if (mm < 4 || mm > 1200) {
				throw new ShotException(at + ".lens", "focal length must be 4-1200 mm");
			}
			return Angles.lensToFov(mm);
		}
		double fov = number(o, "fov", at, fallback);
		if (fov < 1 || fov > 170) {
			throw new ShotException(at + ".fov", "must be 1-170 degrees");
		}
		return fov;
	}

	private static Easing ease(JsonObject o, String at) {
		try {
			return Easing.parse(string(o, "ease", at));
		} catch (IllegalArgumentException e) {
			throw new ShotException(at + ".ease", e.getMessage());
		}
	}

	private static OrbitRig.Range range(JsonObject o, String key, String at, Double fallback) {
		if (!o.has(key)) {
			if (fallback == null) {
				throw new ShotException(at + "." + key, "missing");
			}
			return OrbitRig.Range.of(fallback);
		}
		JsonElement e = o.get(key);
		if (e.isJsonArray()) {
			double[] v = numbers(e, at + "." + key, 2);
			return new OrbitRig.Range(v[0], v[1]);
		}
		return OrbitRig.Range.of(number(o, key, at, 0));
	}

	private static void known(JsonObject o, String at, Set<String> fields) {
		for (String key : o.keySet()) {
			if (!fields.contains(key)) {
				throw new ShotException(join(at, key), "unknown field (known: " + String.join(", ", fields.stream().sorted().toList()) + ")");
			}
		}
	}

	private static JsonObject object(JsonObject o, String key, String at) {
		if (!o.has(key) || !o.get(key).isJsonObject()) {
			throw new ShotException(join(at, key), "expected an object");
		}
		return o.getAsJsonObject(key);
	}

	private static String string(JsonObject o, String key, String at) {
		JsonElement e = o.get(key);
		if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
			throw new ShotException(join(at, key), "expected a string");
		}
		return e.getAsString().trim();
	}

	/** A string naming one of a fixed set of choices, compared without case. */
	private static String word(JsonObject o, String key, String at) {
		return string(o, key, at).toLowerCase(Locale.ROOT);
	}

	private static double number(JsonObject o, String key, String at, double fallback) {
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull()) {
			return fallback;
		}
		if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
			throw new ShotException(join(at, key), "expected a number");
		}
		return e.getAsDouble();
	}

	private static Double optional(JsonObject o, String key, String at) {
		return o.has(key) ? number(o, key, at, 0) : null;
	}

	private static double[] numbers(JsonElement e, String at, int count) {
		if (e == null || !e.isJsonArray() || e.getAsJsonArray().size() != count) {
			throw new ShotException(at, "expected " + count + " numbers in a list");
		}
		double[] out = new double[count];
		for (int i = 0; i < count; i++) {
			JsonElement v = e.getAsJsonArray().get(i);
			if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber()) {
				throw new ShotException(at + "[" + i + "]", "expected a number");
			}
			out[i] = v.getAsDouble();
		}
		return out;
	}

	private static Vec3 vec(JsonElement e, String at) {
		if (e == null) {
			throw new ShotException(at, "missing");
		}
		double[] v = numbers(e, at, 3);
		return new Vec3(v[0], v[1], v[2]);
	}

	private static String join(String at, String key) {
		return at.isEmpty() ? key : at + "." + key;
	}
}
