package dev.voidmc.fray.motion;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Rig files ({@code assets/<ns>/fray/rig/<path>.json}) and the rig registry. The format is vanilla's own model
 * definition written as data, so a rig file reads like Blockbench's Java export of the same project:
 *
 * <pre>
 * { "format": 1, "id": "bestiary:gallows_knight", "texture": "bestiary:textures/entity/gallows_knight.png",
 *   "texture_size": [128, 128], "model_scale": 1, "shadow": 0.9,
 *   "bones": [ { "name": "pelvis", "parent": "", "offset": [0, 9, 0], "rotation": [0, 0, 0],
 *                "cubes": [[-4, -2, -2.5, 8, 4, 5, 0, 0, 0, 0]] }, ... ],
 *   "locators": { "hook": { "bone": "hook", "a": [0, 0, 0], "b": [0, 6, -2], "radius": 0.3 } } }
 * </pre>
 * {@code offset} is {@code PartPose}'s offset (pixels, relative to the parent), {@code rotation} its angles in degrees,
 * each cube {@code [x, y, z, dx, dy, dz, u, v, inflate, mirror]} is {@code texOffs(u, v).addBox(x, y, z, dx, dy, dz,
 * inflate)}. Bones are listed parents first.
 *
 * <p>{@code "vanilla": true} marks a vanilla-bound rig (a vanilla model's own parts as data, written by hand as
 * {@code src/main/blockbench/rig/<ns>/<name>.rig.json}): see {@link Rig#vanilla}.
 */
public final class RigJson {
	public static final int FORMAT = 1;
	private static final Map<Identifier, Rig> RIGS = new ConcurrentHashMap<>();

	static {
		RIGS.put(Rig.HUMANOID.id, Rig.HUMANOID);
	}

	private RigJson() {
	}

	// ------------------------------------------------------------------ registry

	public static void register(Rig rig) {
		RIGS.put(rig.id, rig);
	}

	public static @Nullable Rig get(Identifier id) {
		return RIGS.get(id);
	}

	public static Rig require(Identifier id) {
		Rig r = RIGS.get(id);
		if (r == null) {
			throw new IllegalArgumentException("unknown rig " + id + " (known: " + RIGS.keySet() + ")");
		}
		return r;
	}

	public static Map<Identifier, Rig> all() {
		return Map.copyOf(RIGS);
	}

	// ------------------------------------------------------------------ reading

	public static Rig parse(String json, String hash) {
		return parse(JsonParser.parseString(json).getAsJsonObject(), hash);
	}

	@SuppressWarnings("unchecked")
	public static Rig parse(JsonObject o, String hash) {
		int format = o.has("format") ? o.get("format").getAsInt() : FORMAT;
		if (format != FORMAT) {
			throw new IllegalArgumentException("rig format " + format + ", this Fray reads " + FORMAT);
		}
		Identifier id = Identifier.parse(o.get("id").getAsString());
		JsonArray bones = o.getAsJsonArray("bones");
		int n = bones.size();
		List<String> names = new ArrayList<>(n);
		int[] parent = new int[n];
		PartPose[] rest = new PartPose[n];
		boolean[] helper = new boolean[n];
		List<Rig.Cube>[] cubes = new List[n];
		for (int i = 0; i < n; i++) {
			JsonObject b = bones.get(i).getAsJsonObject();
			String name = b.get("name").getAsString();
			if (names.contains(name)) {
				throw new IllegalArgumentException(id + ": two bones named " + name);
			}
			names.add(name);
			String p = b.has("parent") ? b.get("parent").getAsString() : "";
			parent[i] = p.isEmpty() ? -1 : names.indexOf(p);
			if (!p.isEmpty() && parent[i] < 0) {
				throw new IllegalArgumentException(id + ": bone " + name + " names parent " + p + " before it is defined");
			}
			float[] off = b.has("offset") ? vec(b.getAsJsonArray("offset")) : new float[3];
			float[] rot = b.has("rotation") ? vec(b.getAsJsonArray("rotation")) : new float[3];
			rest[i] = new PartPose().setPos(off[0], off[1], off[2]);
			rest[i].xRot = (float) Math.toRadians(rot[0]);
			rest[i].yRot = (float) Math.toRadians(rot[1]);
			rest[i].zRot = (float) Math.toRadians(rot[2]);
			helper[i] = b.has("helper") && b.get("helper").getAsBoolean();
			cubes[i] = new ArrayList<>();
			if (b.has("cubes")) {
				for (JsonElement ce : b.getAsJsonArray("cubes")) {
					JsonArray c = ce.getAsJsonArray();
					cubes[i].add(new Rig.Cube(c.get(0).getAsFloat(), c.get(1).getAsFloat(), c.get(2).getAsFloat(), c.get(3).getAsFloat(), c.get(4).getAsFloat(),
						c.get(5).getAsFloat(), c.get(6).getAsInt(), c.get(7).getAsInt(), c.size() > 8 ? c.get(8).getAsFloat() : 0,
						c.size() > 9 && c.get(9).getAsInt() != 0));
				}
			}
		}
		Map<String, Rig.Locator> locators = new LinkedHashMap<>();
		if (o.has("locators")) {
			for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("locators").entrySet()) {
				JsonObject l = e.getValue().getAsJsonObject();
				int bone = names.indexOf(l.get("bone").getAsString());
				if (bone < 0) {
					throw new IllegalArgumentException(id + ": locator " + e.getKey() + " on unknown bone " + l.get("bone").getAsString());
				}
				float[] a = vec(l.getAsJsonArray("a"));
				float[] b = l.has("b") ? vec(l.getAsJsonArray("b")) : a.clone();
				locators.put(e.getKey(), new Rig.Locator(e.getKey(), bone, a, b, l.has("radius") ? l.get("radius").getAsFloat() : 0.3F));
			}
		}
		JsonArray ts = o.has("texture_size") ? o.getAsJsonArray("texture_size") : null;
		boolean vanilla = o.has("vanilla") && o.get("vanilla").getAsBoolean();
		return new Rig(id, names, parent, rest, false, vanilla, helper, cubes, o.has("texture") ? Identifier.parse(o.get("texture").getAsString()) : null,
			ts == null ? 64 : ts.get(0).getAsInt(), ts == null ? 64 : ts.get(1).getAsInt(), o.has("model_scale") ? o.get("model_scale").getAsFloat() : 1,
			o.has("shadow") ? o.get("shadow").getAsFloat() : 0.5F, locators, hash);
	}

	private static float[] vec(JsonArray a) {
		return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
	}

	// ------------------------------------------------------------------ writing

	/** The canonical rig file: fixed layout, so the same rig always gives the same bytes and hash. */
	public static String write(Rig r) {
		StringBuilder s = new StringBuilder(4096);
		s.append("{\n  \"format\": ").append(FORMAT).append(",\n");
		s.append("  \"id\": \"").append(r.id).append("\",\n");
		if (r.vanilla) {
			s.append("  \"vanilla\": true,\n");
		}
		if (r.texture != null) {
			s.append("  \"texture\": \"").append(r.texture).append("\",\n");
		}
		s.append("  \"texture_size\": [").append(r.textureWidth).append(", ").append(r.textureHeight).append("],\n");
		s.append("  \"model_scale\": ").append(ClipJson.num(r.modelScale)).append(",\n");
		s.append("  \"shadow\": ").append(ClipJson.num(r.shadowRadius)).append(",\n");
		s.append("  \"bones\": [");
		for (int i = 0; i < r.bones(); i++) {
			PartPose p = r.rest[i];
			s.append(i == 0 ? "\n" : ",\n").append("    { \"name\": \"").append(r.name(i)).append("\", \"parent\": \"")
				.append(r.parent[i] < 0 ? "" : r.name(r.parent[i])).append("\", \"offset\": [").append(ClipJson.num(p.x)).append(", ")
				.append(ClipJson.num(p.y)).append(", ").append(ClipJson.num(p.z)).append("], \"rotation\": [").append(ClipJson.num(Math.toDegrees(p.xRot)))
				.append(", ").append(ClipJson.num(Math.toDegrees(p.yRot))).append(", ").append(ClipJson.num(Math.toDegrees(p.zRot))).append(']');
			if (r.helper[i]) {
				s.append(", \"helper\": true");
			}
			s.append(", \"cubes\": [");
			for (int k = 0; k < r.cubes[i].size(); k++) {
				Rig.Cube c = r.cubes[i].get(k);
				s.append(k == 0 ? "" : ", ").append('[').append(ClipJson.num(c.x())).append(", ").append(ClipJson.num(c.y())).append(", ")
					.append(ClipJson.num(c.z())).append(", ").append(ClipJson.num(c.dx())).append(", ").append(ClipJson.num(c.dy())).append(", ")
					.append(ClipJson.num(c.dz())).append(", ").append(c.u()).append(", ").append(c.v()).append(", ").append(ClipJson.num(c.inflate()))
					.append(", ").append(c.mirror() ? 1 : 0).append(']');
			}
			s.append("] }");
		}
		s.append("\n  ],\n  \"locators\": {");
		boolean first = true;
		for (Rig.Locator l : r.locators.values()) {
			s.append(first ? "\n" : ",\n").append("    \"").append(l.name()).append("\": { \"bone\": \"").append(r.name(l.bone())).append("\", \"a\": ")
				.append(arr(l.a())).append(", \"b\": ").append(arr(l.b())).append(", \"radius\": ").append(ClipJson.num(l.radius())).append(" }");
			first = false;
		}
		s.append(first ? "}\n}\n" : "\n  }\n}\n");
		return s.toString();
	}

	private static String arr(float[] v) {
		return "[" + ClipJson.num(v[0]) + ", " + ClipJson.num(v[1]) + ", " + ClipJson.num(v[2]) + "]";
	}
}
