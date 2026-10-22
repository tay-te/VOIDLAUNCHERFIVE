package dev.voidmc.fray.motion;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Reads clips ({@code .motion.json} sources and baked client clips: the same format, the source with extra
 * server-only keys) and writes the canonical client clip. Common code with no Minecraft registries, so the Gradle bake
 * runs it as a plain {@code main}. The clip's rig must be registered first ({@link RigJson#register}); the humanoid
 * always is.
 *
 * <p>Source keys: {@code format id rig length loop blend blend_in blend_out mask move_mask sync_walk bones fp motion
 * cues} (client) and {@code markers locators hits hurts windows checks model_scale damage} (server, bake).
 */
public final class ClipJson {
	public static final int FORMAT = 1;

	private ClipJson() {
	}

	public static Clip parse(Identifier id, String json) {
		return parse(id, JsonParser.parseString(json).getAsJsonObject());
	}

	public static Clip parse(Identifier fallbackId, JsonObject o) {
		int format = o.has("format") ? o.get("format").getAsInt() : FORMAT;
		if (format != FORMAT) {
			throw new IllegalArgumentException(fallbackId + ": clip format " + format + ", this Fray reads " + FORMAT);
		}
		Identifier id = o.has("id") ? Identifier.parse(o.get("id").getAsString()) : fallbackId;
		Rig rig = RigJson.require(Identifier.parse(o.has("rig") ? o.get("rig").getAsString() : HumanoidRig.ID.toString()));
		int n = rig.bones();
		float length = o.get("length").getAsFloat();
		Clip.Loop loop = o.has("loop") ? (o.get("loop").isJsonPrimitive() && o.get("loop").getAsJsonPrimitive().isBoolean()
			? (o.get("loop").getAsBoolean() ? Clip.Loop.LOOP : Clip.Loop.NONE) : Clip.Loop.parse(o.get("loop").getAsString())) : Clip.Loop.NONE;
		Clip.Blend blend = o.has("blend") ? Clip.Blend.valueOf(o.get("blend").getAsString().toUpperCase(Locale.ROOT)) : Clip.Blend.OVERRIDE;
		int blendIn = o.has("blend_in") ? o.get("blend_in").getAsInt() : 3;
		int blendOut = o.has("blend_out") ? o.get("blend_out").getAsInt() : 4;
		float[] mask = new float[n];
		Arrays.fill(mask, 1);
		if (o.has("mask")) {
			for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("mask").entrySet()) {
				mask[rig.bone(e.getKey())] = e.getValue().getAsFloat();
			}
		}
		float[] moveMask = null;
		if (o.has("move_mask")) {
			moveMask = mask.clone();
			for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("move_mask").entrySet()) {
				moveMask[rig.bone(e.getKey())] = e.getValue().getAsFloat();
			}
		}
		float syncWalk = o.has("sync_walk") ? o.get("sync_walk").getAsFloat() : 0;
		Channel[] rot = new Channel[n];
		Channel[] pos = new Channel[n];
		Channel[] scl = new Channel[n];
		if (o.has("bones")) {
			for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("bones").entrySet()) {
				int bone = rig.bone(e.getKey());
				if (rig.helper[bone]) {
					throw new IllegalArgumentException(id + ": bone " + e.getKey() + " is a cube-rotation helper of " + rig.id + " and can't be animated");
				}
				JsonObject b = e.getValue().getAsJsonObject();
				for (String key : b.keySet()) {
					if (!key.equals("rotation") && !key.equals("position") && !key.equals("scale")) {
						throw new IllegalArgumentException(id + ": bone " + e.getKey() + " has an unknown channel '" + key + "'");
					}
				}
				rot[bone] = b.has("rotation") ? channel(b.getAsJsonArray("rotation")) : null;
				pos[bone] = b.has("position") ? channel(b.getAsJsonArray("position")) : null;
				scl[bone] = b.has("scale") ? channel(b.getAsJsonArray("scale")) : null;
			}
		}
		Clip.FirstPerson fp = null;
		if (o.has("fp")) {
			JsonObject f = o.getAsJsonObject("fp");
			Channel[] fpRot = new Channel[n];
			Channel[] fpPos = new Channel[n];
			if (f.has("bones")) {
				for (Map.Entry<String, JsonElement> e : f.getAsJsonObject("bones").entrySet()) {
					JsonObject b = e.getValue().getAsJsonObject();
					int bone = rig.bone(e.getKey());
					fpRot[bone] = b.has("rotation") ? channel(b.getAsJsonArray("rotation")) : null;
					fpPos[bone] = b.has("position") ? channel(b.getAsJsonArray("position")) : null;
				}
			}
			JsonObject cam = f.has("camera") ? f.getAsJsonObject("camera") : null;
			fp = new Clip.FirstPerson(f.has("framing") ? f.get("framing").getAsString() : "fray:default", f.has("offset") ? vec(f.getAsJsonArray("offset")) : new float[3],
				f.has("pitch") ? f.get("pitch").getAsFloat() : 0, cam != null && cam.has("rotation") ? channel(cam.getAsJsonArray("rotation")) : null,
				cam != null && cam.has("position") ? channel(cam.getAsJsonArray("position")) : null, fpRot, fpPos,
				f.has("hide_arms") && f.get("hide_arms").getAsBoolean(), f.has("arms") && f.get("arms").getAsString().equals("both"),
				f.has("swing") ? f.get("swing").getAsFloat() : 1);
		}
		List<Clip.Cue> cues = new ArrayList<>();
		if (o.has("cues")) {
			for (JsonElement e : o.getAsJsonArray("cues")) {
				JsonArray a = e.getAsJsonArray();
				cues.add(new Clip.Cue(a.get(0).getAsFloat(), a.get(1).getAsString(), a.size() > 2 ? a.get(2).getAsString() : ""));
			}
		}
		List<Clip.Marker> markers = new ArrayList<>();
		if (o.has("markers")) {
			for (JsonElement e : o.getAsJsonArray("markers")) {
				JsonArray a = e.getAsJsonArray();
				markers.add(new Clip.Marker(a.get(0).getAsFloat(), a.get(1).getAsString(), a.size() > 2 ? a.get(2).getAsString() : ""));
			}
		}
		Map<String, Clip.Locator> locators = new LinkedHashMap<>();
		if (o.has("locators")) {
			for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("locators").entrySet()) {
				JsonObject l = e.getValue().getAsJsonObject();
				float[] a = vec(l.getAsJsonArray("a"));
				locators.put(e.getKey(), new Clip.Locator(e.getKey(), rig.bone(l.get("bone").getAsString()), a,
					l.has("b") ? vec(l.getAsJsonArray("b")) : a.clone(), l.has("radius") ? l.get("radius").getAsFloat() : 0.3F));
			}
		}
		List<String> hits = new ArrayList<>();
		if (o.has("hits")) {
			for (JsonElement e : o.getAsJsonArray("hits")) {
				hits.add(e.getAsString());
			}
		} else {
			hits.addAll(locators.keySet());
		}
		Map<String, int[]> hurts = new LinkedHashMap<>();
		if (o.has("hurts")) {
			for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("hurts").entrySet()) {
				hurts.put(e.getKey(), ticks(e.getValue().getAsJsonArray()));
			}
		}
		// Hit and hurt volumes may name the rig's own locators (authored in Blockbench as hv_ locators).
		for (String h : concat(hits, hurts.keySet())) {
			if (!locators.containsKey(h) && rig.locators.containsKey(h)) {
				Rig.Locator rl = rig.locators.get(h);
				locators.put(h, new Clip.Locator(h, rl.bone(), rl.a(), rl.b(), rl.radius()));
			}
		}
		Map<String, int[]> windows = new LinkedHashMap<>();
		if (o.has("windows")) {
			for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("windows").entrySet()) {
				windows.put(e.getKey(), ticks(e.getValue().getAsJsonArray()));
			}
		}
		List<String> checks = new ArrayList<>();
		if (o.has("checks")) {
			for (JsonElement e : o.getAsJsonArray("checks")) {
				checks.add(e.getAsString());
			}
		}
		float modelScale = o.has("model_scale") ? o.get("model_scale").getAsFloat() : rig.modelScale;
		float damage = o.has("damage") ? o.get("damage").getAsFloat() : 0;
		float[] motion = null;
		if (o.has("motion")) {
			JsonArray m = o.getAsJsonArray("motion");
			motion = new float[] {m.get(0).getAsFloat(), m.get(1).getAsFloat(), m.get(2).getAsFloat(), m.size() > 3 ? m.get(3).getAsFloat() : 0};
		}
		return new Clip(id, rig, length, loop, blend, blendIn, blendOut, mask, moveMask, syncWalk, rot, pos, scl, fp, List.copyOf(cues), List.copyOf(markers),
			locators, List.copyOf(hits), hurts, windows, List.copyOf(checks), modelScale, damage, motion);
	}

	private static List<String> concat(List<String> a, java.util.Collection<String> b) {
		List<String> out = new ArrayList<>(a);
		out.addAll(b);
		return out;
	}

	private static int[] ticks(JsonArray a) {
		return new int[] {a.get(0).getAsInt(), a.get(1).getAsInt()};
	}

	private static float[] vec(JsonArray a) {
		return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
	}

	private static Channel channel(JsonArray keys) {
		int n = keys.size();
		float[] t = new float[n];
		float[] v = new float[n * 3];
		Interp[] in = new Interp[n];
		for (int i = 0; i < n; i++) {
			JsonArray k = keys.get(i).getAsJsonArray();
			t[i] = k.get(0).getAsFloat();
			JsonArray xyz = k.get(1).getAsJsonArray();
			v[i * 3] = xyz.get(0).getAsFloat();
			v[i * 3 + 1] = xyz.get(1).getAsFloat();
			v[i * 3 + 2] = xyz.get(2).getAsFloat();
			in[i] = k.size() > 2 ? Interp.parse(k.get(2).getAsString()) : Interp.LINEAR;
		}
		return new Channel(t, v, in);
	}

	// ------------------------------------------------------------------ writing

	/**
	 * The canonical client clip: what the client plays, without the server-only keys, in a fixed layout so the same clip
	 * always gives the same bytes (and the same hash).
	 */
	public static String writeClient(Clip c) {
		Rig rig = c.rig;
		StringBuilder s = new StringBuilder(1024);
		s.append("{\n");
		s.append("  \"format\": ").append(FORMAT).append(",\n");
		s.append("  \"id\": \"").append(c.id).append("\",\n");
		s.append("  \"rig\": \"").append(rig.id).append("\",\n");
		s.append("  \"length\": ").append(num(c.length)).append(",\n");
		s.append("  \"loop\": \"").append(c.loop.id()).append("\",\n");
		if (c.blend != Clip.Blend.OVERRIDE) {
			s.append("  \"blend\": \"").append(c.blend.id()).append("\",\n");
		}
		s.append("  \"blend_in\": ").append(c.blendIn).append(", \"blend_out\": ").append(c.blendOut).append(",\n");
		s.append("  \"mask\": ").append(maskJson(rig, c.mask, null)).append(",\n");
		if (c.moveMask != null) {
			s.append("  \"move_mask\": ").append(maskJson(rig, c.moveMask, c.mask)).append(",\n");
		}
		if (c.syncWalk > 0) {
			s.append("  \"sync_walk\": ").append(num(c.syncWalk)).append(",\n");
		}
		s.append("  \"bones\": {");
		boolean first = true;
		for (int b = 0; b < rig.bones(); b++) {
			if (c.rotation[b] == null && c.position[b] == null && c.scale[b] == null) {
				continue;
			}
			s.append(first ? "\n" : ",\n").append("    \"").append(rig.name(b)).append("\": {");
			boolean firstCh = true;
			String[] names = {"rotation", "position", "scale"};
			Channel[] chs = {c.rotation[b], c.position[b], c.scale[b]};
			for (int k = 0; k < 3; k++) {
				if (chs[k] != null) {
					s.append(firstCh ? "\n" : ",\n").append("      \"").append(names[k]).append("\": ");
					writeChannel(s, chs[k]);
					firstCh = false;
				}
			}
			s.append("\n    }");
			first = false;
		}
		s.append(first ? "},\n" : "\n  },\n");
		if (c.fp != null) {
			Clip.FirstPerson f = c.fp;
			s.append("  \"fp\": { \"framing\": \"").append(f.framing()).append("\", \"offset\": [").append(num(f.offset()[0])).append(", ")
				.append(num(f.offset()[1])).append(", ").append(num(f.offset()[2])).append("], \"pitch\": ").append(num(f.pitch()));
			if (f.hideArms()) {
				s.append(", \"hide_arms\": true");
			}
			if (f.bothArms()) {
				s.append(", \"arms\": \"both\"");
			}
			if (f.swing() != 1) {
				s.append(", \"swing\": ").append(num(f.swing()));
			}
			if (f.camera() != null || f.cameraPos() != null) {
				s.append(",\n    \"camera\": {");
				if (f.camera() != null) {
					s.append(" \"rotation\": ");
					writeChannel(s, f.camera());
				}
				if (f.cameraPos() != null) {
					s.append(f.camera() != null ? ", \"position\": " : " \"position\": ");
					writeChannel(s, f.cameraPos());
				}
				s.append(" }");
			}
			boolean anyBone = false;
			for (int b = 0; b < rig.bones(); b++) {
				Channel r = f.rotation() == null ? null : f.rotation()[b];
				Channel p = f.position() == null ? null : f.position()[b];
				if (r == null && p == null) {
					continue;
				}
				s.append(anyBone ? ",\n      \"" : ",\n    \"bones\": {\n      \"").append(rig.name(b)).append("\": {");
				if (r != null) {
					s.append(" \"rotation\": ");
					writeChannel(s, r);
				}
				if (p != null) {
					s.append(r != null ? ", \"position\": " : " \"position\": ");
					writeChannel(s, p);
				}
				s.append(" }");
				anyBone = true;
			}
			if (anyBone) {
				s.append("\n    }");
			}
			s.append(" },\n");
		}
		if (c.motion != null) {
			s.append("  \"motion\": [").append(num(c.motion[0])).append(", ").append(num(c.motion[1])).append(", ").append(num(c.motion[2]));
			if (c.motion[3] != 0) {
				s.append(", ").append(num(c.motion[3]));
			}
			s.append("],\n");
		}
		s.append("  \"cues\": [");
		for (int i = 0; i < c.cues.size(); i++) {
			Clip.Cue cue = c.cues.get(i);
			s.append(i == 0 ? "" : ", ").append('[').append(num(cue.time())).append(", \"").append(cue.kind()).append("\", \"").append(cue.arg()).append("\"]");
		}
		s.append("]\n}\n");
		return s.toString();
	}

	private static String maskJson(Rig rig, float[] mask, float @Nullable [] base) {
		StringBuilder s = new StringBuilder("{");
		boolean first = true;
		for (int b = 0; b < rig.bones(); b++) {
			if (base == null ? mask[b] != 1 : mask[b] != base[b]) {
				s.append(first ? " " : ", ").append('"').append(rig.name(b)).append("\": ").append(num(mask[b]));
				first = false;
			}
		}
		return s.append(first ? "}" : " }").toString();
	}

	private static void writeChannel(StringBuilder s, Channel ch) {
		s.append('[');
		for (int i = 0; i < ch.keys(); i++) {
			s.append(i == 0 ? "" : ", ").append('[').append(num(ch.times[i])).append(", [").append(num(ch.values[i * 3])).append(", ")
				.append(num(ch.values[i * 3 + 1])).append(", ").append(num(ch.values[i * 3 + 2])).append("], \"").append(ch.interps[i].id()).append("\"]");
		}
		s.append(']');
	}

	/** Up to four decimals, no trailing zeros, never "-0". */
	public static String num(double v) {
		if (Double.isNaN(v) || Double.isInfinite(v)) {
			throw new IllegalArgumentException("not a number: " + v);
		}
		BigDecimal d = new BigDecimal(v).setScale(4, RoundingMode.HALF_EVEN).stripTrailingZeros();
		if (d.signum() == 0) {
			return "0";
		}
		String s = d.toPlainString();
		return s.toLowerCase(Locale.ROOT);
	}

	public static byte[] utf8(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}

	/** Lowercase hex SHA-256 of some bytes. */
	public static String sha256(byte[] bytes) {
		try {
			java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
			return java.util.HexFormat.of().formatHex(md.digest(bytes));
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	public static @Nullable String opt(JsonObject o, String key) {
		return o.has(key) ? o.get(key).getAsString() : null;
	}
}
