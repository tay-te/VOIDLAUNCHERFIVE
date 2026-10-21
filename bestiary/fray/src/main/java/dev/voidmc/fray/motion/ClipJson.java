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
 * runs it as a plain {@code main}.
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
		Identifier rig = Identifier.parse(o.has("rig") ? o.get("rig").getAsString() : HumanoidRig.ID.toString());
		if (!rig.equals(HumanoidRig.ID)) {
			throw new IllegalArgumentException(id + ": rig " + rig + " (the prototype knows only " + HumanoidRig.ID + ")");
		}
		float length = o.get("length").getAsFloat();
		Clip.Loop loop = o.has("loop") ? (o.get("loop").isJsonPrimitive() && o.get("loop").getAsJsonPrimitive().isBoolean()
			? (o.get("loop").getAsBoolean() ? Clip.Loop.LOOP : Clip.Loop.NONE) : Clip.Loop.parse(o.get("loop").getAsString())) : Clip.Loop.NONE;
		int blendIn = o.has("blend_in") ? o.get("blend_in").getAsInt() : 3;
		int blendOut = o.has("blend_out") ? o.get("blend_out").getAsInt() : 4;
		float[] mask = new float[HumanoidRig.BONES];
		Arrays.fill(mask, 1);
		if (o.has("mask")) {
			for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("mask").entrySet()) {
				mask[HumanoidRig.bone(e.getKey())] = e.getValue().getAsFloat();
			}
		}
		Channel[] rot = new Channel[HumanoidRig.BONES];
		Channel[] pos = new Channel[HumanoidRig.BONES];
		Channel[] scl = new Channel[HumanoidRig.BONES];
		if (o.has("bones")) {
			for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("bones").entrySet()) {
				int bone = HumanoidRig.bone(e.getKey());
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
			Channel[] fpRot = new Channel[HumanoidRig.BONES];
			if (f.has("bones")) {
				for (Map.Entry<String, JsonElement> e : f.getAsJsonObject("bones").entrySet()) {
					fpRot[HumanoidRig.bone(e.getKey())] = channel(e.getValue().getAsJsonObject().getAsJsonArray("rotation"));
				}
			}
			fp = new Clip.FirstPerson(f.has("framing") ? f.get("framing").getAsString() : "fray:default", f.has("offset") ? vec(f.getAsJsonArray("offset")) : new float[3],
				f.has("pitch") ? f.get("pitch").getAsFloat() : 0, f.has("camera") ? channel(f.getAsJsonObject("camera").getAsJsonArray("rotation")) : null, fpRot);
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
				locators.put(e.getKey(), new Clip.Locator(e.getKey(), HumanoidRig.bone(l.get("bone").getAsString()), vec(l.getAsJsonArray("a")),
					vec(l.getAsJsonArray("b")), l.has("radius") ? l.get("radius").getAsFloat() : 0.3F));
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
		float modelScale = o.has("model_scale") ? o.get("model_scale").getAsFloat() : 1;
		float damage = o.has("damage") ? o.get("damage").getAsFloat() : 0;
		float[] motion = o.has("motion") ? vec(o.getAsJsonArray("motion")) : null;
		return new Clip(id, rig, length, loop, blendIn, blendOut, mask, rot, pos, scl, fp, List.copyOf(cues), List.copyOf(markers), locators,
			List.copyOf(hits), modelScale, damage, motion);
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
		StringBuilder s = new StringBuilder(1024);
		s.append("{\n");
		s.append("  \"format\": ").append(FORMAT).append(",\n");
		s.append("  \"id\": \"").append(c.id).append("\",\n");
		s.append("  \"rig\": \"").append(c.rig).append("\",\n");
		s.append("  \"length\": ").append(num(c.length)).append(",\n");
		s.append("  \"loop\": \"").append(c.loop.id()).append("\",\n");
		s.append("  \"blend_in\": ").append(c.blendIn).append(", \"blend_out\": ").append(c.blendOut).append(",\n");
		s.append("  \"mask\": {");
		boolean first = true;
		for (int b = 0; b < HumanoidRig.BONES; b++) {
			if (c.mask[b] != 1) {
				s.append(first ? " " : ", ").append('"').append(HumanoidRig.NAMES.get(b)).append("\": ").append(num(c.mask[b]));
				first = false;
			}
		}
		s.append(first ? "},\n" : " },\n");
		s.append("  \"bones\": {");
		first = true;
		for (int b = 0; b < HumanoidRig.BONES; b++) {
			if (c.rotation[b] == null && c.position[b] == null && c.scale[b] == null) {
				continue;
			}
			s.append(first ? "\n" : ",\n").append("    \"").append(HumanoidRig.NAMES.get(b)).append("\": {");
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
			s.append("  \"fp\": { \"framing\": \"").append(c.fp.framing()).append("\", \"offset\": [").append(num(c.fp.offset()[0])).append(", ")
				.append(num(c.fp.offset()[1])).append(", ").append(num(c.fp.offset()[2])).append("], \"pitch\": ").append(num(c.fp.pitch()));
			if (c.fp.camera() != null) {
				s.append(", \"camera\": { \"rotation\": ");
				writeChannel(s, c.fp.camera());
				s.append(" }");
			}
			boolean anyBone = false;
			for (int b = 0; b < HumanoidRig.BONES; b++) {
				if (c.fp.rotation() != null && c.fp.rotation()[b] != null) {
					s.append(anyBone ? ",\n      \"" : ",\n    \"bones\": {\n      \"").append(HumanoidRig.NAMES.get(b)).append("\": { \"rotation\": ");
					writeChannel(s, c.fp.rotation()[b]);
					s.append(" }");
					anyBone = true;
				}
			}
			if (anyBone) {
				s.append("\n    }");
			}
			s.append(" },\n");
		}
		if (c.motion != null) {
			s.append("  \"motion\": [").append(num(c.motion[0])).append(", ").append(num(c.motion[1])).append(", ").append(num(c.motion[2])).append("],\n");
		}
		s.append("  \"cues\": [");
		for (int i = 0; i < c.cues.size(); i++) {
			Clip.Cue cue = c.cues.get(i);
			s.append(i == 0 ? "" : ", ").append('[').append(num(cue.time())).append(", \"").append(cue.kind()).append("\", \"").append(cue.arg()).append("\"]");
		}
		s.append("]\n}\n");
		return s.toString();
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
