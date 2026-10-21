package dev.voidmc.fray.motion.bake;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.voidmc.fray.motion.HumanoidRig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Blockbench → {@code .motion.json} (ANIMATION.md §6): reads a {@code .bbmodel} (Blockbench's own project file, format 4.x,
 * "Modded Entity") and writes one motion source per animation.
 *
 * <ul>
 * <li>Bones: each animator's group name, normalised to the rig's snake_case ({@code RightArm}, {@code rightArm} →
 * {@code right_arm}); groups the rig doesn't know are reported and skipped.</li>
 * <li>Units: Blockbench shows a modded entity turned 180° about Z from Minecraft's model space (Y up, X mirrored), as its
 * Java exporter writes it: rotation {@code (x, y, z)} → {@code (-x, -y, z)} degrees and position {@code (x, y, z)} →
 * {@code (-x, y, z)} pixels (Y stays up: our format flips it as vanilla's {@code posVec}).</li>
 * <li>Interpolation: linear, catmullrom and step carry over; bezier becomes catmullrom (reported).</li>
 * <li>Gameplay markers are instruction keyframes on the Effects track ({@code windup_end}, {@code active_end},
 * {@code tell orange}): first word the name, the rest the argument.</li>
 * <li>Hit volumes are locators named {@code hv_<name>_a@<radius>} and {@code hv_<name>_b} inside the bone group.</li>
 * </ul>
 * Molang expressions in keyframes are not evaluated (a number is required).
 */
public final class BlockbenchImport {
	private BlockbenchImport() {
	}

	public static void main(String[] args) throws IOException {
		Path bb = Path.of(args[0]);
		Path outRoot = Path.of(args[1]);
		String rig = args[2];
		String ns = args[3];
		List<String> report = new ArrayList<>();
		Map<String, JsonObject> sources = convert(Files.readString(bb, StandardCharsets.UTF_8), rig, report);
		for (Map.Entry<String, JsonObject> e : sources.entrySet()) {
			Path out = outRoot.resolve(ns).resolve(e.getKey() + ".motion.json");
			Files.createDirectories(out.getParent());
			Files.writeString(out, new GsonBuilder().setPrettyPrinting().create().toJson(e.getValue()) + "\n", StandardCharsets.UTF_8);
			report.add("wrote " + out);
		}
		report.forEach(System.out::println);
	}

	/** Every animation in the project as a motion source, keyed by its path ("zombie/taunt"). */
	public static Map<String, JsonObject> convert(String bbmodel, String rig, List<String> report) {
		JsonObject root = JsonParser.parseString(bbmodel).getAsJsonObject();
		String format = root.has("meta") && root.getAsJsonObject("meta").has("model_format") ? root.getAsJsonObject("meta").get("model_format").getAsString() : "?";
		if (!format.equals("modded_entity")) {
			report.add("note: model format '" + format + "' (expected modded_entity); converting with modded entity axes anyway");
		}
		// Groups by uuid (name, origin) and locators by uuid (name, position) with their parent group.
		Map<String, JsonObject> groups = new HashMap<>();
		Map<String, String> parentOf = new HashMap<>();
		if (root.has("outliner")) {
			walk(root.getAsJsonArray("outliner"), null, groups, parentOf);
		}
		Map<String, JsonObject> locators = new LinkedHashMap<>();
		if (root.has("elements")) {
			for (JsonElement el : root.getAsJsonArray("elements")) {
				JsonObject o = el.getAsJsonObject();
				if (o.has("type") && o.get("type").getAsString().equals("locator")) {
					locators.put(o.get("uuid").getAsString(), o);
				}
			}
		}
		Map<String, JsonObject> out = new LinkedHashMap<>();
		if (!root.has("animations")) {
			report.add("no animations in the project");
			return out;
		}
		for (JsonElement ae : root.getAsJsonArray("animations")) {
			JsonObject anim = ae.getAsJsonObject();
			String name = anim.get("name").getAsString();
			String path = name.startsWith("animation.") ? name.substring("animation.".length()) : name;
			path = path.replace('.', '/').toLowerCase(Locale.ROOT);
			JsonObject src = new JsonObject();
			src.addProperty("format", 1);
			src.addProperty("rig", rig);
			src.addProperty("length", anim.get("length").getAsFloat());
			String loop = anim.has("loop") ? anim.get("loop").getAsString() : "once";
			src.addProperty("loop", loop.equals("loop") ? "loop" : loop.equals("hold") ? "hold" : "none");
			JsonObject bones = new JsonObject();
			JsonArray markers = new JsonArray();
			JsonObject anims = anim.has("animators") ? anim.getAsJsonObject("animators") : new JsonObject();
			for (Map.Entry<String, JsonElement> e : anims.entrySet()) {
				JsonObject animator = e.getValue().getAsJsonObject();
				String type = animator.has("type") ? animator.get("type").getAsString() : "bone";
				if (type.equals("effect")) {
					for (JsonElement ke : animator.getAsJsonArray("keyframes")) {
						JsonObject k = ke.getAsJsonObject();
						if (!k.get("channel").getAsString().equals("timeline")) {
							continue;
						}
						for (JsonElement dp : k.getAsJsonArray("data_points")) {
							String script = dp.getAsJsonObject().get("script").getAsString().trim();
							for (String line : script.split("[;\n]")) {
								line = line.trim();
								if (line.isEmpty()) {
									continue;
								}
								String[] words = line.split("\\s+", 2);
								JsonArray m = new JsonArray();
								m.add(k.get("time").getAsFloat());
								m.add(words[0]);
								if (words.length > 1) {
									m.add(words[1]);
								}
								markers.add(m);
							}
						}
					}
					continue;
				}
				String bone = boneName(animator.get("name").getAsString());
				if (!HumanoidRig.NAMES.contains(bone)) {
					report.add(name + ": skipped group '" + animator.get("name").getAsString() + "' (no such bone in " + rig + ")");
					continue;
				}
				JsonObject channels = new JsonObject();
				for (String channel : new String[] {"rotation", "position", "scale"}) {
					List<JsonObject> keys = new ArrayList<>();
					for (JsonElement ke : animator.getAsJsonArray("keyframes")) {
						if (ke.getAsJsonObject().get("channel").getAsString().equals(channel)) {
							keys.add(ke.getAsJsonObject());
						}
					}
					if (keys.isEmpty()) {
						continue;
					}
					keys.sort((a, b) -> Float.compare(a.get("time").getAsFloat(), b.get("time").getAsFloat()));
					JsonArray arr = new JsonArray();
					for (JsonObject k : keys) {
						JsonObject dp = k.getAsJsonArray("data_points").get(0).getAsJsonObject();
						float x = number(dp.get("x"), name), y = number(dp.get("y"), name), z = number(dp.get("z"), name);
						float[] v = switch (channel) {
							case "rotation" -> new float[] {-x, -y, z};
							case "position" -> new float[] {-x, y, z};
							default -> new float[] {x, y, z};
						};
						String interp = k.has("interpolation") ? k.get("interpolation").getAsString() : "linear";
						if (interp.equals("bezier")) {
							report.add(name + ": " + bone + " " + channel + " key at " + k.get("time").getAsFloat() + " is bezier; imported as catmullrom");
							interp = "catmullrom";
						}
						JsonArray key = new JsonArray();
						key.add(k.get("time").getAsFloat());
						JsonArray vec = new JsonArray();
						for (float f : v) {
							vec.add(f == 0 ? 0F : f);
						}
						key.add(vec);
						key.add(interp);
						arr.add(key);
					}
					channels.add(channel, arr);
				}
				if (!channels.isEmpty()) {
					bones.add(bone, channels);
				}
			}
			src.add("bones", bones);
			if (!markers.isEmpty()) {
				src.add("markers", markers);
			}
			JsonObject locs = locators(locators, groups, parentOf, report);
			if (!locs.isEmpty()) {
				src.add("locators", locs);
			}
			out.put(path, src);
			report.add("converted " + name + " -> " + path + " (" + bones.size() + " bones, " + markers.size() + " markers, " + locs.size() + " hit volumes)");
		}
		return out;
	}

	private static void walk(JsonArray children, String parent, Map<String, JsonObject> groups, Map<String, String> parentOf) {
		for (JsonElement c : children) {
			if (c.isJsonPrimitive()) {
				// An element (cube or locator) by uuid.
				if (parent != null) {
					parentOf.put(c.getAsString(), parent);
				}
				continue;
			}
			JsonObject g = c.getAsJsonObject();
			String uuid = g.get("uuid").getAsString();
			groups.put(uuid, g);
			if (parent != null) {
				parentOf.put(uuid, parent);
			}
			if (g.has("children")) {
				walk(g.getAsJsonArray("children"), uuid, groups, parentOf);
			}
		}
	}

	/** Pairs {@code hv_<name>_a@r} / {@code hv_<name>_b} locators into capsules in their bone's frame (model pixels). */
	private static JsonObject locators(Map<String, JsonObject> locators, Map<String, JsonObject> groups, Map<String, String> parentOf, List<String> report) {
		Map<String, float[][]> ends = new LinkedHashMap<>();
		Map<String, String> boneOf = new HashMap<>();
		Map<String, Float> radius = new HashMap<>();
		for (JsonObject l : locators.values()) {
			String n = l.get("name").getAsString();
			if (!n.startsWith("hv_")) {
				continue;
			}
			float r = 0.3F;
			int at = n.indexOf('@');
			if (at >= 0) {
				r = Float.parseFloat(n.substring(at + 1));
				n = n.substring(0, at);
			}
			boolean isA = n.endsWith("_a");
			if (!isA && !n.endsWith("_b")) {
				report.add("locator " + n + ": name it hv_<volume>_a@<radius> or hv_<volume>_b");
				continue;
			}
			String vol = n.substring(3, n.length() - 2);
			String groupId = parentOf.get(l.get("uuid").getAsString());
			JsonObject group = groupId == null ? null : groups.get(groupId);
			if (group == null) {
				report.add("locator " + n + " is not inside a bone group");
				continue;
			}
			String bone = boneName(group.get("name").getAsString());
			JsonArray pos = l.getAsJsonArray("position");
			JsonArray origin = group.getAsJsonArray("origin");
			// Blockbench absolute (Y up, feet at 0) → the bone's frame in model pixels: (-(x-ox), -(y-oy), z-oz).
			float[] local = {-(pos.get(0).getAsFloat() - origin.get(0).getAsFloat()), -(pos.get(1).getAsFloat() - origin.get(1).getAsFloat()),
				pos.get(2).getAsFloat() - origin.get(2).getAsFloat()};
			ends.computeIfAbsent(vol, k -> new float[2][])[isA ? 0 : 1] = local;
			boneOf.put(vol, bone);
			if (isA || !radius.containsKey(vol)) {
				radius.put(vol, r);
			}
		}
		JsonObject out = new JsonObject();
		for (Map.Entry<String, float[][]> e : ends.entrySet()) {
			if (e.getValue()[0] == null || e.getValue()[1] == null) {
				report.add("hit volume " + e.getKey() + " needs both _a and _b locators");
				continue;
			}
			JsonObject v = new JsonObject();
			v.addProperty("bone", boneOf.get(e.getKey()));
			v.add("a", arr(e.getValue()[0]));
			v.add("b", arr(e.getValue()[1]));
			v.addProperty("radius", radius.get(e.getKey()));
			out.add(e.getKey(), v);
		}
		return out;
	}

	private static JsonArray arr(float[] v) {
		JsonArray a = new JsonArray();
		for (float f : v) {
			a.add(f == 0 ? 0F : f);
		}
		return a;
	}

	private static float number(JsonElement e, String anim) {
		if (e == null) {
			return 0;
		}
		JsonPrimitive p = e.getAsJsonPrimitive();
		if (p.isNumber()) {
			return p.getAsFloat();
		}
		String s = p.getAsString().trim();
		if (s.isEmpty()) {
			return 0;
		}
		try {
			return Float.parseFloat(s);
		} catch (NumberFormatException ex) {
			throw new IllegalArgumentException(anim + ": keyframe value '" + s + "' is a Molang expression; Fray needs numbers (bake it in Blockbench first)");
		}
	}

	/** {@code RightArm}, {@code rightArm}, {@code right arm} → {@code right_arm}. */
	public static String boneName(String group) {
		String s = group.trim().replace(' ', '_').replace('-', '_');
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (Character.isUpperCase(c) && i > 0 && s.charAt(i - 1) != '_') {
				b.append('_');
			}
			b.append(Character.toLowerCase(c));
		}
		return b.toString();
	}
}
