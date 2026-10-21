package dev.voidmc.fray.motion.bake;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.voidmc.fray.motion.ClipJson;
import dev.voidmc.fray.motion.HumanoidRig;
import dev.voidmc.fray.motion.PartPose;
import dev.voidmc.fray.motion.Rig;
import dev.voidmc.fray.motion.RigJson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Blockbench → Fray (ANIMATION.md §6). Reads a {@code .bbmodel} (Blockbench's own project file, format 4.x or 5.x,
 * "Modded Entity") and converts it the way <b>Blockbench's own Java exporter</b> does (Blockbench 5.1.2,
 * {@code js/formats/java/modded_entity.js}; verified against real exports in {@code BlockbenchExportTest}):
 *
 * <ul>
 * <li><b>Geometry → rig file</b> ({@link #convertRig}): a group becomes a bone with
 * {@code PartPose.offsetAndRotation(-(ox - pox), -(oy - poy) [+24 for top-level groups], oz - poz, -rx, -ry, rz)}
 * (the project's "flip Y" option, on by default; without it Y is not negated); a cube in a group becomes
 * {@code texOffs(uv).addBox(gox - to.x, -from.y - size.y + goy, from.z - goz, size)}; a cube with its own rotation goes
 * into a helper bone {@code <cube>_r1} at the cube's origin, as Blockbench does; loose cubes go into {@code bb_main}. The
 * first texture is written out as the rig's PNG. Locators named {@code hv_<volume>_a@<radius>} / {@code hv_<volume>_b}
 * become capsules on their group's bone.</li>
 * <li><b>Animations → {@code .motion.json}</b> ({@link #convertAnimations}): rotation {@code (x, y, z)} becomes
 * {@code degreeVec(-x, -y, z)} and position {@code (x, y, z)} {@code posVec(-x, y, z)} (the exporter's
 * {@code addKeyframe}); a key with two data points (pre and post) becomes two keys 1 ms apart; a step key holds its value
 * until the next key; a bezier key is sampled into linear keys every 1/40 s along Blockbench's own curve (the exporter
 * itself would write it as linear). Gameplay markers are instruction keyframes on the Effects track
 * ({@code windup_end}, {@code active_end}, {@code tell orange}).</li>
 * </ul>
 * Molang expressions in keyframes are not evaluated (a number is required).
 */
public final class BlockbenchImport {
	private BlockbenchImport() {
	}

	/** {@code bbImport}: {@code <file.bbmodel> <motion src root> <rig id or fray:humanoid> <namespace>}. */
	public static void main(String[] args) throws IOException {
		Path bb = Path.of(args[0]);
		Path outRoot = Path.of(args[1]);
		String rig = args[2];
		String ns = args[3];
		List<String> report = new ArrayList<>();
		String text = Files.readString(bb, StandardCharsets.UTF_8);
		JsonObject root = JsonParser.parseString(text).getAsJsonObject();
		List<String> bones;
		if (rig.equals(HumanoidRig.ID.toString())) {
			bones = HumanoidRig.NAMES;
		} else {
			bones = convertRig(root, Identifier.parse(rig), Identifier.parse(rig), report).rig().names;
		}
		Map<String, JsonObject> sources = convertAnimations(root, rig, bones, report);
		for (Map.Entry<String, JsonObject> e : sources.entrySet()) {
			Path out = outRoot.resolve(ns).resolve(e.getKey() + ".motion.json");
			Files.createDirectories(out.getParent());
			Files.writeString(out, new GsonBuilder().setPrettyPrinting().create().toJson(e.getValue()) + "\n", StandardCharsets.UTF_8);
			report.add("wrote " + out);
		}
		report.forEach(System.out::println);
	}

	// ------------------------------------------------------------------ project structure (4.x and 5.x)

	/** The project's groups in Blockbench's {@code getAllGroups} order (depth first), with their parents and children. */
	record Group(String uuid, String name, float[] origin, float[] rotation, @Nullable Group parent, List<Object> children) {
	}

	record Project(List<Group> groups, Map<String, JsonObject> elements, Map<String, Group> parentOf, List<JsonObject> loose, boolean flipY,
		int textureWidth, int textureHeight) {
	}

	static Project project(JsonObject root) {
		Map<String, JsonObject> groupDefs = new HashMap<>();
		if (root.has("groups")) {
			for (JsonElement g : root.getAsJsonArray("groups")) {
				groupDefs.put(g.getAsJsonObject().get("uuid").getAsString(), g.getAsJsonObject());
			}
		}
		Map<String, JsonObject> elements = new LinkedHashMap<>();
		if (root.has("elements")) {
			for (JsonElement el : root.getAsJsonArray("elements")) {
				JsonObject o = el.getAsJsonObject();
				elements.put(o.get("uuid").getAsString(), o);
			}
		}
		List<Group> groups = new ArrayList<>();
		Map<String, Group> parentOf = new HashMap<>();
		List<JsonObject> loose = new ArrayList<>();
		if (root.has("outliner")) {
			walk(root.getAsJsonArray("outliner"), null, groupDefs, elements, groups, parentOf, loose);
		}
		boolean flipY = !root.has("modded_entity_flip_y") || root.get("modded_entity_flip_y").getAsBoolean();
		JsonObject res = root.has("resolution") ? root.getAsJsonObject("resolution") : null;
		return new Project(groups, elements, parentOf, loose, flipY, res == null ? 64 : res.get("width").getAsInt(), res == null ? 64 : res.get("height").getAsInt());
	}

	private static void walk(JsonArray children, @Nullable Group parent, Map<String, JsonObject> defs, Map<String, JsonObject> elements, List<Group> groups,
		Map<String, Group> parentOf, List<JsonObject> loose) {
		for (JsonElement c : children) {
			if (c.isJsonPrimitive()) {
				String uuid = c.getAsString();
				if (parent != null) {
					parentOf.put(uuid, parent);
					parent.children().add(uuid);
				} else if (elements.containsKey(uuid)) {
					loose.add(elements.get(uuid));
				}
				continue;
			}
			JsonObject o = c.getAsJsonObject();
			String uuid = o.get("uuid").getAsString();
			// 5.x keeps the group's fields in "groups" and only uuid + children in the outliner; 4.x has them inline.
			JsonObject def = defs.getOrDefault(uuid, o);
			Group g = new Group(uuid, def.get("name").getAsString(), vec(def, "origin"), vec(def, "rotation"), parent, new ArrayList<>());
			if (def.has("export") && !def.get("export").getAsBoolean()) {
				continue;
			}
			groups.add(g);
			if (parent != null) {
				parentOf.put(uuid, parent);
				parent.children().add(g);
			}
			if (o.has("children")) {
				walk(o.getAsJsonArray("children"), g, defs, elements, groups, parentOf, loose);
			}
		}
	}

	private static float[] vec(JsonObject o, String key) {
		if (!o.has(key)) {
			return new float[3];
		}
		JsonArray a = o.getAsJsonArray(key);
		return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
	}

	// ------------------------------------------------------------------ geometry

	public record RigResult(Rig rig, byte @Nullable [] texturePng) {
	}

	/** The project's geometry as a rig (Blockbench's Java export, as data). */
	@SuppressWarnings("unchecked")
	public static RigResult convertRig(JsonObject root, Identifier id, @Nullable Identifier texture, List<String> report) {
		Project p = project(root);
		List<String> names = new ArrayList<>();
		List<Integer> parents = new ArrayList<>();
		List<PartPose> rest = new ArrayList<>();
		List<Boolean> helper = new ArrayList<>();
		List<List<Rig.Cube>> cubes = new ArrayList<>();
		Map<Group, Integer> index = new HashMap<>();
		// Loose cubes: Blockbench's catch bone bb_main (origin 0 0 0), appended after the real groups.
		List<Group> all = new ArrayList<>(p.groups());
		Group bbMain = null;
		if (!p.loose().isEmpty()) {
			bbMain = new Group("bb_main", "bb_main", new float[3], new float[3], null, new ArrayList<>(p.loose().stream().map(e -> (Object) e.get("uuid").getAsString()).toList()));
			all.add(bbMain);
		}
		for (Group g : all) {
			String name = boneName(g.name());
			if (names.contains(name)) {
				throw new IllegalArgumentException(id + ": two bones named " + name + " (Blockbench group names must be unique)");
			}
			int me = names.size();
			index.put(g, me);
			names.add(name);
			parents.add(g.parent() == null ? -1 : index.get(g.parent()));
			rest.add(offsetAndRotation(g.origin(), g.parent() == null ? null : g.parent().origin(), g.rotation(), p.flipY()));
			helper.add(false);
			List<Rig.Cube> mine = new ArrayList<>();
			cubes.add(mine);
			int sub = 0;
			for (Object child : g.children()) {
				if (!(child instanceof String uuid)) {
					continue;
				}
				JsonObject el = p.elements().get(uuid);
				if (el == null || !"cube".equals(el.has("type") ? el.get("type").getAsString() : "cube")) {
					continue;
				}
				if (el.has("export") && !el.get("export").getAsBoolean()) {
					continue;
				}
				float[] rot = vec(el, "rotation");
				if (rot[0] == 0 && rot[1] == 0 && rot[2] == 0) {
					mine.add(cube(el, g.origin(), p.flipY()));
				} else {
					// Blockbench's rotation subgroup: a helper bone at the cube's origin carrying its rotation.
					float[] origin = vec(el, "origin");
					String hn = boneName(el.has("name") ? el.get("name").getAsString() : "cube") + "_r" + (++sub);
					names.add(hn);
					parents.add(me);
					rest.add(offsetAndRotation(origin, g.origin(), rot, p.flipY()));
					helper.add(true);
					List<Rig.Cube> hc = new ArrayList<>();
					hc.add(cube(el, origin, p.flipY()));
					cubes.add(hc);
				}
			}
		}
		// Locators: hv_<volume>_a@radius / hv_<volume>_b in a group.
		Map<String, Rig.Locator> locators = locators(p, names, report);
		int n = names.size();
		int[] parent = new int[n];
		boolean[] help = new boolean[n];
		List<Rig.Cube>[] cubeArr = new List[n];
		for (int i = 0; i < n; i++) {
			parent[i] = parents.get(i);
			help[i] = helper.get(i);
			cubeArr[i] = cubes.get(i);
		}
		byte[] png = null;
		if (root.has("textures") && !root.getAsJsonArray("textures").isEmpty()) {
			JsonObject t = root.getAsJsonArray("textures").get(0).getAsJsonObject();
			if (t.has("source") && t.get("source").getAsString().startsWith("data:image/png;base64,")) {
				png = Base64.getDecoder().decode(t.get("source").getAsString().substring("data:image/png;base64,".length()));
			}
		}
		Rig partial = new Rig(id, names, parent, rest.toArray(new PartPose[0]), false, help, cubeArr, texture, p.textureWidth(), p.textureHeight(), 1, 0.5F,
			locators, "");
		// Settings Blockbench has no field for (shadow, scale) come from the project's "fray" block when present.
		float scale = 1, shadow = 0.5F;
		if (root.has("fray")) {
			JsonObject f = root.getAsJsonObject("fray");
			scale = f.has("model_scale") ? f.get("model_scale").getAsFloat() : 1;
			shadow = f.has("shadow") ? f.get("shadow").getAsFloat() : 0.5F;
		}
		Rig shaped = new Rig(id, names, parent, partial.rest, false, help, cubeArr, texture, p.textureWidth(), p.textureHeight(), scale, shadow, locators, "");
		String hash = ClipJson.sha256(ClipJson.utf8(RigJson.write(shaped)));
		report.add("rig " + id + ": " + n + " bones (" + count(help) + " cube-rotation helpers), " + total(cubeArr) + " cubes, " + locators.size() + " locators");
		return new RigResult(new Rig(id, names, parent, partial.rest, false, help, cubeArr, texture, p.textureWidth(), p.textureHeight(), scale, shadow, locators,
			hash), png);
	}

	private static int count(boolean[] b) {
		int n = 0;
		for (boolean x : b) {
			n += x ? 1 : 0;
		}
		return n;
	}

	private static int total(List<Rig.Cube>[] c) {
		int n = 0;
		for (List<Rig.Cube> l : c) {
			n += l.size();
		}
		return n;
	}

	/** The exporter's bone pose: offset from the parent's origin, X negated, Y negated (+24 at the top) with flip Y. */
	static PartPose offsetAndRotation(float[] origin, float @Nullable [] parentOrigin, float[] rotation, boolean flipY) {
		float x = origin[0], y = origin[1], z = origin[2];
		if (parentOrigin != null) {
			x -= parentOrigin[0];
			y -= parentOrigin[1];
			z -= parentOrigin[2];
		}
		x = -x;
		if (flipY) {
			y = -y;
			if (parentOrigin == null) {
				y += 24;
			}
		}
		PartPose p = new PartPose().setPos(x, y, z);
		p.xRot = (float) Math.toRadians(-rotation[0]);
		p.yRot = (float) Math.toRadians(-rotation[1]);
		p.zRot = (float) Math.toRadians(rotation[2]);
		return p;
	}

	/** The exporter's addBox for a cube in a group whose origin is {@code g}. */
	static Rig.Cube cube(JsonObject el, float[] g, boolean flipY) {
		float[] from = vec(el, "from"), to = vec(el, "to");
		float sx = to[0] - from[0], sy = to[1] - from[1], sz = to[2] - from[2];
		float x = g[0] - to[0];
		float y = flipY ? -from[1] - sy + g[1] : from[1] - g[1];
		float z = from[2] - g[2];
		int u = 0, v = 0;
		if (el.has("uv_offset")) {
			JsonArray uv = el.getAsJsonArray("uv_offset");
			u = uv.get(0).getAsInt();
			v = uv.get(1).getAsInt();
		}
		float inflate = el.has("inflate") ? el.get("inflate").getAsFloat() : 0;
		boolean mirror = el.has("mirror_uv") && el.get("mirror_uv").getAsBoolean();
		return new Rig.Cube(x, y, z, sx, sy, sz, u, v, inflate, mirror);
	}

	private static Map<String, Rig.Locator> locators(Project p, List<String> bones, List<String> report) {
		Map<String, float[][]> ends = new LinkedHashMap<>();
		Map<String, String> boneOf = new HashMap<>();
		Map<String, Float> radius = new HashMap<>();
		for (JsonObject l : p.elements().values()) {
			if (!l.has("type") || !l.get("type").getAsString().equals("locator")) {
				continue;
			}
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
			Group group = p.parentOf().get(l.get("uuid").getAsString());
			if (group == null) {
				report.add("locator " + n + " is not inside a bone group");
				continue;
			}
			float[] pos = vec(l, "position");
			float[] o = group.origin();
			// Blockbench absolute (Y up, feet at 0) → the bone's frame in model pixels: (-(x-ox), -(y-oy), z-oz) with flip Y.
			float[] local = {-(pos[0] - o[0]), p.flipY() ? -(pos[1] - o[1]) : pos[1] - o[1], pos[2] - o[2]};
			ends.computeIfAbsent(vol, k -> new float[2][])[isA ? 0 : 1] = local;
			boneOf.put(vol, boneName(group.name()));
			if (isA || !radius.containsKey(vol)) {
				radius.put(vol, r);
			}
		}
		Map<String, Rig.Locator> out = new LinkedHashMap<>();
		for (Map.Entry<String, float[][]> e : ends.entrySet()) {
			float[][] ab = e.getValue();
			if (ab[0] == null || ab[1] == null) {
				report.add("hit volume " + e.getKey() + " needs both _a and _b locators");
				continue;
			}
			int bone = bones.indexOf(boneOf.get(e.getKey()));
			if (bone < 0) {
				report.add("hit volume " + e.getKey() + " is on " + boneOf.get(e.getKey()) + ", not a bone of this rig");
				continue;
			}
			out.put(e.getKey(), new Rig.Locator(e.getKey(), bone, ab[0], ab[1], radius.get(e.getKey())));
		}
		return out;
	}

	// ------------------------------------------------------------------ animations

	/** Every animation in the project as a motion source, keyed by its path ("zombie/taunt"). */
	public static Map<String, JsonObject> convertAnimations(String bbmodel, String rig, List<String> report) {
		return convertAnimations(JsonParser.parseString(bbmodel).getAsJsonObject(), rig, HumanoidRig.NAMES, report);
	}

	/** Back-compatible name of {@link #convertAnimations(String, String, List)}. */
	public static Map<String, JsonObject> convert(String bbmodel, String rig, List<String> report) {
		return convertAnimations(bbmodel, rig, report);
	}

	public static Map<String, JsonObject> convertAnimations(JsonObject root, String rig, List<String> bones, List<String> report) {
		String format = root.has("meta") && root.getAsJsonObject("meta").has("model_format") ? root.getAsJsonObject("meta").get("model_format").getAsString() : "?";
		if (!format.equals("modded_entity")) {
			report.add("note: model format '" + format + "' (expected modded_entity); converting with modded entity axes anyway");
		}
		Project p = project(root);
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
			JsonObject outBones = new JsonObject();
			JsonArray markers = new JsonArray();
			JsonObject anims = anim.has("animators") ? anim.getAsJsonObject("animators") : new JsonObject();
			for (Map.Entry<String, JsonElement> e : anims.entrySet()) {
				JsonObject animator = e.getValue().getAsJsonObject();
				String type = animator.has("type") ? animator.get("type").getAsString() : "bone";
				if (type.equals("effect")) {
					markers(animator, markers);
					continue;
				}
				String bone = boneName(animator.get("name").getAsString());
				if (!bones.contains(bone)) {
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
					channels.add(channel, channel(keys, channel, name, bone, report));
				}
				if (!channels.isEmpty()) {
					outBones.add(bone, channels);
				}
			}
			src.add("bones", outBones);
			if (!markers.isEmpty()) {
				src.add("markers", markers);
			}
			if (rig.equals(HumanoidRig.ID.toString())) {
				JsonObject locs = new JsonObject();
				for (Rig.Locator l : locators(p, bones, report).values()) {
					JsonObject v = new JsonObject();
					v.addProperty("bone", bones.get(l.bone()));
					v.add("a", arr(l.a()));
					v.add("b", arr(l.b()));
					v.addProperty("radius", l.radius());
					locs.add(l.name(), v);
				}
				if (!locs.isEmpty()) {
					src.add("locators", locs);
				}
			}
			out.put(path, src);
			report.add("converted " + name + " -> " + path + " (" + outBones.size() + " bones, " + markers.size() + " markers)");
		}
		return out;
	}

	private static void markers(JsonObject animator, JsonArray markers) {
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
	}

	/** One channel's keys in vanilla's convention, as Blockbench's {@code addKeyframe} writes them. */
	private static JsonArray channel(List<JsonObject> keys, String channel, String anim, String bone, List<String> report) {
		JsonArray arr = new JsonArray();
		String nextInterp = null;
		for (int i = 0; i < keys.size(); i++) {
			JsonObject k = keys.get(i);
			float time = k.get("time").getAsFloat();
			String interp = k.has("interpolation") ? k.get("interpolation").getAsString() : "linear";
			JsonArray dps = k.getAsJsonArray("data_points");
			float[] pre = convert(channel, dps.get(0).getAsJsonObject(), anim);
			// The segment arriving here: Blockbench's previous key decides a step (it holds until this key).
			// (after a sampled bezier segment the last sample arrives linearly)
			String prevInterp = i == 0 ? "" : keys.get(i - 1).has("interpolation") ? keys.get(i - 1).get("interpolation").getAsString() : "linear";
			String arrive = nextInterp != null ? nextInterp : prevInterp.equals("bezier") || interp.equals("step") || interp.equals("bezier") ? "linear" : interp;
			add(arr, time, pre, arrive);
			nextInterp = null;
			if (dps.size() > 1) {
				add(arr, time + 0.001F, convert(channel, dps.get(1).getAsJsonObject(), anim), "step");
			}
			if (interp.equals("step")) {
				nextInterp = "step";
			}
			if (interp.equals("bezier") && i + 1 < keys.size()) {
				// Sample Blockbench's own curve (its getBezierLerp) into linear keys every 1/40 s.
				JsonObject next = keys.get(i + 1);
				float t1 = next.get("time").getAsFloat();
				float[] a = dps.size() > 1 ? convert(channel, dps.get(1).getAsJsonObject(), anim) : pre;
				float[] b = convert(channel, next.getAsJsonArray("data_points").get(0).getAsJsonObject(), anim);
				int steps = Math.max(2, Math.round((t1 - time) * 40));
				for (int s = 1; s < steps; s++) {
					float t = time + (t1 - time) * s / steps;
					float[] v = new float[3];
					for (int ax = 0; ax < 3; ax++) {
						float sign = signOf(channel, ax);
						v[ax] = sign * bezier(time, sign * a[ax], handle(k, "bezier_right_time", ax, 0.1F), handle(k, "bezier_right_value", ax, 0), t1, sign * b[ax],
							handle(next, "bezier_left_time", ax, -0.1F), handle(next, "bezier_left_value", ax, 0), t);
					}
					add(arr, t, v, "linear");
				}
				report.add(anim + ": " + bone + " " + channel + " bezier from " + time + " s sampled into " + (steps - 1) + " linear keys");
			}
		}
		return arr;
	}

	private static float signOf(String channel, int axis) {
		return switch (channel) {
			case "rotation" -> axis < 2 ? -1 : 1;
			case "position" -> axis == 0 ? -1 : 1;
			default -> 1;
		};
	}

	private static float handle(JsonObject k, String key, int axis, float dflt) {
		if (!k.has(key)) {
			return dflt;
		}
		return k.getAsJsonArray(key).get(axis).getAsFloat();
	}

	/** Blockbench's bezier segment value at time {@code t} (Blockbench values, not converted). */
	static float bezier(float t0, float v0, float rt, float rv, float t1, float v1, float lt, float lv, float t) {
		float gap = t1 - t0;
		rt = Math.clamp(rt, 0, gap);
		lt = Math.clamp(lt, -gap, 0);
		float x0 = t0, x1 = t0 + rt, x2 = t1 + lt, x3 = t1;
		float y0 = v0, y1 = v0 + rv, y2 = v1 + lv, y3 = v1;
		// Solve x(s) = t by bisection (x is monotonic for clamped handles), then y(s).
		float lo = 0, hi = 1;
		for (int i = 0; i < 40; i++) {
			float s = (lo + hi) / 2;
			float x = cubic(x0, x1, x2, x3, s);
			if (x < t) {
				lo = s;
			} else {
				hi = s;
			}
		}
		return cubic(y0, y1, y2, y3, (lo + hi) / 2);
	}

	private static float cubic(float p0, float p1, float p2, float p3, float s) {
		float u = 1 - s;
		return u * u * u * p0 + 3 * u * u * s * p1 + 3 * u * s * s * p2 + s * s * s * p3;
	}

	private static float[] convert(String channel, JsonObject dp, String anim) {
		float x = number(dp.get("x"), anim), y = number(dp.get("y"), anim), z = number(dp.get("z"), anim);
		return switch (channel) {
			case "rotation" -> new float[] {-x, -y, z};
			case "position" -> new float[] {-x, y, z};
			default -> new float[] {x, y, z};
		};
	}

	private static void add(JsonArray arr, float time, float[] v, String interp) {
		JsonArray key = new JsonArray();
		key.add(time);
		key.add(arr(v));
		key.add(interp);
		arr.add(key);
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
