package dev.voidmc.fray.motion.bake;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.fray.motion.Clip;
import dev.voidmc.fray.motion.ClipJson;
import dev.voidmc.fray.motion.Rig;
import dev.voidmc.fray.motion.RigJson;
import dev.voidmc.fray.motion.Timeline;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The bake (Gradle task {@code motionBake}):
 * <ul>
 * <li>every rig project {@code <rigs>/<namespace>/<path>.bbmodel} (Blockbench, geometry) becomes
 * {@code assets/<namespace>/fray/rig/<path>.json} (canonical, see {@link RigJson}) and
 * {@code assets/<namespace>/textures/entity/<path>.png};</li>
 * <li>every {@code <src>/<namespace>/<path>.motion.json} becomes {@code assets/<namespace>/fray/clip/<path>.json}, the
 * client clip in the canonical layout of {@link ClipJson#writeClient}, and {@code data/<namespace>/fray/timeline/<path>.json},
 * the server timeline carrying the SHA-256 of the client clip's bytes (and of its custom rig's file);</li>
 * <li>a source with {@code "mirror_of": "<path>"} is that clip mirrored left to right (bones swapped, Y and Z
 * rotations and X positions negated), with its own keys on top;</li>
 * <li>a report: phases, windows, hit-volume reach, the lowest point of the body (ground check), first-person screen
 * coverage, warnings. A clip that doesn't validate, or fails a check it asks for, fails the build.</li>
 * </ul>
 */
public final class MotionBake {
	private MotionBake() {
	}

	/** {@code <motion src> <out> <report> [<rig src>]}. */
	public static void main(String[] args) throws IOException {
		Path src = Path.of(args[0]);
		Path out = Path.of(args[1]);
		Path report = Path.of(args[2]);
		Path rigs = args.length > 3 ? Path.of(args[3]) : null;
		Result r = bakeAll(src, rigs, out);
		Files.createDirectories(report.getParent());
		Files.write(report, r.lines(), StandardCharsets.UTF_8);
		for (String l : r.lines()) {
			System.out.println(l);
		}
		// -Dfray.bake.lenient=true (the studio while iterating): failed checks are reported, not fatal.
		if (!r.failures().isEmpty() && !Boolean.getBoolean("fray.bake.lenient")) {
			throw new IllegalStateException("motionBake: " + r.failures().size() + " check(s) failed:\n  " + String.join("\n  ", r.failures()));
		}
	}

	public record Baked(Clip clip, String clientJson, String clipHash, Timeline timeline) {
	}

	public record BakedRig(Rig rig, String json, byte @Nullable [] png) {
	}

	public record Result(Map<Identifier, Baked> clips, Map<Identifier, BakedRig> rigs, List<String> lines, List<String> warnings, List<String> failures) {
	}

	/** Bakes everything; writes the files under {@code out} when it isn't null (the studio bakes in memory). */
	public static Result bakeAll(Path src, @Nullable Path rigSrc, @Nullable Path out) throws IOException {
		List<String> lines = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		List<String> failures = new ArrayList<>();
		Map<Identifier, BakedRig> rigs = new LinkedHashMap<>();
		if (rigSrc != null && Files.isDirectory(rigSrc)) {
			for (Path file : list(rigSrc, ".bbmodel")) {
				Identifier id = idOf(rigSrc, file, ".bbmodel");
				List<String> rep = new ArrayList<>();
				Identifier tex = Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/entity/" + id.getPath() + ".png");
				JsonObject project = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
				// What Blockbench has no field for (shadow, model scale) sits beside the project in <name>.fray.json.
				Path side = file.resolveSibling(file.getFileName().toString().replace(".bbmodel", ".fray.json"));
				if (Files.exists(side)) {
					project.add("fray", JsonParser.parseString(Files.readString(side, StandardCharsets.UTF_8)));
				}
				BlockbenchImport.RigResult rr = BlockbenchImport.convertRig(project, id, tex, rep);
				String json = RigJson.write(rr.rig());
				Rig rig = RigJson.parse(json, ClipJson.sha256(ClipJson.utf8(json)));
				RigJson.register(rig);
				rigs.put(id, new BakedRig(rig, json, rr.texturePng()));
				lines.addAll(rep);
				if (out != null) {
					write(out.resolve("assets/" + id.getNamespace() + "/fray/rig/" + id.getPath() + ".json"), json);
					if (rr.texturePng() != null) {
						Path png = out.resolve("assets/" + id.getNamespace() + "/textures/entity/" + id.getPath() + ".png");
						Files.createDirectories(png.getParent());
						Files.write(png, rr.texturePng());
					}
				}
			}
		}
		Map<Identifier, Baked> clips = new LinkedHashMap<>();
		List<Path> files = list(src, ".motion.json");
		for (Path file : files) {
			Path rel = src.relativize(file);
			if (rel.getNameCount() < 2) {
				throw new IllegalArgumentException(file + ": motion sources live in <namespace>/<path>.motion.json");
			}
			Identifier id = idOf(src, file, ".motion.json");
			Baked baked;
			try {
				baked = bake(id, resolve(src, id.getNamespace(), Files.readString(file, StandardCharsets.UTF_8), 0), warnings);
			} catch (RuntimeException e) {
				throw new IllegalArgumentException("motionBake: " + rel + ": " + e.getMessage(), e);
			}
			if (out != null) {
				write(out.resolve("assets/" + id.getNamespace() + "/fray/clip/" + id.getPath() + ".json"), baked.clientJson);
				write(out.resolve("data/" + id.getNamespace() + "/fray/timeline/" + id.getPath() + ".json"), baked.timeline.write());
			}
			lines.add(describe(baked.clip, baked.timeline));
			lines.add(checks(baked.clip, warnings, failures));
			clips.put(id, baked);
		}
		lines.add(0, "Fray motion bake: " + clips.size() + " clips from " + src + ", " + rigs.size() + " custom rigs");
		for (String w : warnings) {
			lines.add("WARNING " + w);
		}
		for (String f : failures) {
			lines.add("FAILED " + f);
		}
		return new Result(clips, rigs, lines, warnings, failures);
	}

	private static List<Path> list(Path root, String suffix) throws IOException {
		try (Stream<Path> walk = Files.walk(root)) {
			return walk.filter(p -> p.getFileName().toString().endsWith(suffix)).sorted().toList();
		}
	}

	private static Identifier idOf(Path root, Path file, String suffix) {
		Path rel = root.relativize(file);
		String ns = rel.getName(0).toString();
		String path = rel.subpath(1, rel.getNameCount()).toString().replace('\\', '/');
		return Identifier.fromNamespaceAndPath(ns, path.substring(0, path.length() - suffix.length()));
	}

	/**
	 * A source with {@code mirror_of} becomes that source mirrored, with its own keys on top; one with {@code same_as} is
	 * that source as it is (an alias under another id), with its own keys on top.
	 */
	static String resolve(Path src, String ns, String source, int depth) throws IOException {
		JsonObject o = JsonParser.parseString(source).getAsJsonObject();
		if (o.has("same_as")) {
			if (depth > 4) {
				throw new IllegalArgumentException("same_as chain too deep");
			}
			JsonObject base = JsonParser.parseString(resolve(src, ns, Files.readString(src.resolve(ns).resolve(o.get("same_as").getAsString() + ".motion.json"),
				StandardCharsets.UTF_8), depth + 1)).getAsJsonObject();
			base.remove("id");
			for (Map.Entry<String, JsonElement> e : o.entrySet()) {
				if (!e.getKey().equals("same_as")) {
					base.add(e.getKey(), e.getValue());
				}
			}
			return base.toString();
		}
		if (!o.has("mirror_of")) {
			return source;
		}
		if (depth > 4) {
			throw new IllegalArgumentException("mirror_of chain too deep");
		}
		String of = o.get("mirror_of").getAsString();
		Path base = src.resolve(ns).resolve(of + ".motion.json");
		JsonObject mirrored = Mirror.mirror(JsonParser.parseString(resolve(src, ns, Files.readString(base, StandardCharsets.UTF_8), depth + 1)).getAsJsonObject());
		for (Map.Entry<String, JsonElement> e : o.entrySet()) {
			if (!e.getKey().equals("mirror_of")) {
				mirrored.add(e.getKey(), e.getValue());
			}
		}
		return mirrored.toString();
	}

	/** Parses a source, writes its client clip, hashes it and bakes the timeline from the client clip as parsed back. */
	public static Baked bake(Identifier id, String source, List<String> warnings) {
		Clip parsed = ClipJson.parse(id, source);
		JsonObject src = JsonParser.parseString(source).getAsJsonObject();
		if (src.has("ground") && (src.get("ground").getAsString().equals("plant") || src.get("ground").getAsString().equals("floor"))) {
			parsed = ClipJson.parse(id, Ground.plant(parsed, src, src.get("ground").getAsString().equals("floor")).toString());
		}
		String clientJson = ClipJson.writeClient(parsed);
		String hash = ClipJson.sha256(ClipJson.utf8(clientJson));
		// The timeline is baked from the source (it has the markers and locators), but the curves it samples must be the
		// client's: check they survive the round trip through the canonical file.
		Clip client = ClipJson.parse(id, clientJson);
		String again = ClipJson.writeClient(client);
		if (!again.equals(clientJson)) {
			throw new IllegalStateException(id + ": the client clip doesn't round-trip");
		}
		// Bake from exactly the curves the client will read (the canonical file rounds to four decimals), with the
		// source's server-only parts.
		Clip forBake = client.withServerParts(parsed);
		Timeline timeline = TimelineBaker.bake(forBake, hash, warnings);
		Checks.euler(forBake, warnings);
		return new Baked(forBake, clientJson, hash, timeline);
	}

	/**
	 * Fray's custom first person is on (the client option {@code fpAnimations}; {@code -Dfray.fpAnimations=true} here).
	 * Off by default: the first-person checks {@code fp_framing} and {@code fp_match} are skipped.
	 */
	public static final boolean FP_ANIMATIONS = Boolean.getBoolean("fray.fpAnimations");

	/** The looks checks' report line; failures for the checks the clip asked for. */
	public static String checks(Clip clip, List<String> warnings, List<String> failures) {
		StringBuilder s = new StringBuilder("      checks:");
		if (clip.blend == Clip.Blend.ADDITIVE) {
			return s.append(" (additive: none)").toString();
		}
		Checks.Ground g = Checks.ground(clip);
		s.append(String.format(Locale.ROOT, " lowest %.3f blocks at %.2f s", g.lowest(), g.atSeconds()));
		if (g.lowest() < -Checks.GROUND_TOLERANCE) {
			String msg = String.format(Locale.ROOT, "%s: the body goes %.1f px below the ground at %.2f s", clip.id, -g.lowest() * 16, g.atSeconds());
			if (clip.checks.contains("ground")) {
				failures.add(msg);
			} else if (clip.motion != null || clip.rotation[0] != null) {
				warnings.add(msg);
			}
		}
		if (clip.rig.humanoid && clip.id.getPath().startsWith("player/") && Checks.drawsArms(clip)) {
			Checks.Reach r = Checks.reach(clip);
			s.append(String.format(Locale.ROOT, " | hand reach: max %.2f, outside the strike %.2f, mean %.2f blocks", r.max(), r.maxOutside(), r.mean()));
			if (r.ticksOver() > 0) {
				warnings.add(String.format(Locale.ROOT, "%s: the right hand is more than %.2f blocks from the chest for %d ticks outside the strike (max %.2f)", clip.id,
					Checks.REACH_LIMIT, r.ticksOver(), r.maxOutside()));
			}
		}
		if (!FP_ANIMATIONS && (Checks.drawsArms(clip) || FirstPersonMatch.applies(clip) || clip.checks.contains("fp_match"))) {
			// Custom first person is parked (the client's fpAnimations option, off): first person draws vanilla's hands,
			// so its framing and its match with third person aren't checked. -Dfray.fpAnimations=true checks them again.
			return s.append(" | first person: vanilla (fpAnimations off: fp_framing, fp_match skipped)").toString();
		}
		if (Checks.drawsArms(clip)) {
			Checks.Framing f = Checks.firstPerson(clip);
			s.append(String.format(Locale.ROOT, " | first person: arm and blade cover up to %.0f %% of the screen at %.2f s, crosshair covered %d ticks outside the strike, arm's far end on screen %d ticks%s",
				f.maxCoverage() * 100, f.atSeconds(), f.crosshairTicks(), f.armEndTicks(), f.throughCamera() ? " (a hand reaches the camera)" : ""));
			if (f.armEndTicks() > 0) {
				String msg = String.format(Locale.ROOT, "%s: the first-person arm ends on screen for %d ticks (it must run off the edge)", clip.id, f.armEndTicks());
				if (clip.checks.contains("fp_framing")) {
					failures.add(msg);
				} else {
					warnings.add(msg);
				}
			}
			if (f.crosshairTicks() > 0) {
				String msg = String.format(Locale.ROOT, "%s: first person covers the crosshair for %d ticks outside the strike", clip.id, f.crosshairTicks());
				if (clip.checks.contains("fp_framing")) {
					failures.add(msg);
				} else {
					warnings.add(msg);
				}
			}
			if (clip.checks.contains("fp_framing") && (f.overLimit() || f.throughCamera())) {
				failures.add(String.format(Locale.ROOT, "%s: first-person arm and blade cover %.0f %% of the screen at %.2f s%s (limit %.0f %%, %.0f %% on the strike)", clip.id,
					f.maxCoverage() * 100, f.atSeconds(), f.throughCamera() ? " and reach the camera" : "", Checks.FP_LIMIT * 100, Checks.FP_LIMIT_STRIKE * 100));
			}
		}
		if (FirstPersonMatch.applies(clip)) {
			FirstPersonMatch.Result m = FirstPersonMatch.run(clip);
			s.append(" | ").append(m.line());
			for (String p : m.problems()) {
				String msg = clip.id + ": first and third person disagree: " + p;
				if (clip.checks.contains("fp_match")) {
					failures.add(msg);
				} else {
					warnings.add(msg);
				}
			}
			String plot = System.getProperty("fray.fp.plot", "");
			if (!plot.isEmpty()) {
				try {
					FirstPersonMatch.plot(clip, m, java.nio.file.Path.of(plot, clip.id.getPath().replace('/', '_') + ".png"));
				} catch (IOException e) {
					warnings.add("fp plot: " + e);
				}
			}
		} else if (clip.checks.contains("fp_match")) {
			failures.add(clip.id + ": fp_match needs first-person rig curves (fp.bones.item_r)");
		}
		return s.toString();
	}

	private static void write(Path file, String text) throws IOException {
		Files.createDirectories(file.getParent());
		Files.writeString(file, text, StandardCharsets.UTF_8);
	}

	public static String describe(Clip clip, Timeline t) {
		StringBuilder s = new StringBuilder();
		s.append(String.format(Locale.ROOT, "%-38s %3d ticks %-5s hash %s", clip.id, t.ticks, clip.loop.id(), t.clipHash.substring(0, 12)));
		if (!clip.rig.humanoid) {
			s.append(" rig ").append(clip.rig.id);
		}
		for (int[] st : t.strikes) {
			s.append(String.format(Locale.ROOT, " | active %d-%d", st[0], st[1] - 1));
		}
		if (t.hasPhases()) {
			s.append(String.format(Locale.ROOT, " (windup 0-%d, recovery %d-%d)", t.activeStart - 1, t.strikes[t.strikes.length - 1][1], t.ticks - 1));
		}
		for (Map.Entry<String, int[]> w : t.windows.entrySet()) {
			s.append(" | ").append(w.getKey()).append(' ').append(w.getValue()[0]).append('-').append(w.getValue()[1]);
		}
		if (t.tellFrom >= 0) {
			s.append(" | tell ").append(t.tellColour).append(" from ").append(t.tellFrom);
		}
		for (Map.Entry<String, Timeline.Track> e : t.volumes.entrySet()) {
			float reach = 0, lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
			Timeline.Track tr = e.getValue();
			for (int i = 0; i < tr.caps().length; i++) {
				if (!t.isActive(tr.from() + i)) {
					continue;
				}
				Timeline.Capsule c = tr.caps()[i];
				reach = Math.max(reach, c.reach());
				lo = Math.min(lo, Math.min(c.ay(), c.by()) - c.r());
				hi = Math.max(hi, Math.max(c.ay(), c.by()) + c.r());
			}
			s.append(String.format(Locale.ROOT, " | %s reach %.2f, height %.2f-%.2f", e.getKey(), reach, lo, hi));
			for (int i = 0; i < tr.caps().length; i++) {
				Timeline.Capsule c = tr.caps()[i];
				s.append(String.format(Locale.ROOT, "%n      tick %2d%s a(%5.2f %5.2f %5.2f) b(%5.2f %5.2f %5.2f) r %.2f", tr.from() + i, t.isActive(tr.from() + i) ? "*" : " ",
					c.ax(), c.ay(), c.az(), c.bx(), c.by(), c.bz(), c.r()));
			}
		}
		for (Map.Entry<String, Timeline.Track> e : t.hurtVolumes.entrySet()) {
			s.append(String.format(Locale.ROOT, " | hurt %s ticks %d-%d", e.getKey(), e.getValue().from(), e.getValue().from() + e.getValue().caps().length - 1));
		}
		return s.toString();
	}

	/**
	 * {@code "ground": "plant"}: the body's lowest point is put on the ground at every tick (half ticks for short clips) by
	 * re-keying the root bone's Y position: feet never sink into the floor or float above it, whatever the knees, the
	 * crouch or a roll do. The corrected root channel is what the client plays and what the hit volumes are baked from.
	 */
	static final class Ground {
		private Ground() {
		}

		/** {@code floor}: only lifts what sinks (hops stay in the air); {@code plant}: the lowest point always on the ground. */
		static JsonObject plant(Clip clip, JsonObject src, boolean floorOnly) {
			int n = Math.max(2, Math.round(clip.length * 40));
			Posed posed = new Posed(clip.rig, clip.modelScale);
			float[] base = new float[3];
			JsonArray keys = new JsonArray();
			dev.voidmc.fray.motion.Channel root = clip.position[0];
			for (int i = 0; i <= n; i++) {
				float t = clip.length * i / n;
				float low = posed.at(clip, t).lowest(0);
				if (root != null) {
					root.sample(t, base, 0);
				} else {
					base[0] = base[1] = base[2] = 0;
				}
				// lowest is in entity blocks; the root's file position is in model pixels with Y up.
				float up = (floorOnly ? Math.max(0, -low) : -low) * 16 / clip.modelScale;
				JsonArray key = new JsonArray();
				key.add(t);
				JsonArray v = new JsonArray();
				v.add(base[0]);
				v.add(base[1] + up);
				v.add(base[2]);
				key.add(v);
				key.add("catmullrom");
				keys.add(key);
			}
			JsonObject out = src.deepCopy();
			JsonObject bones = out.has("bones") ? out.getAsJsonObject("bones") : new JsonObject();
			String rootName = clip.rig.name(0);
			JsonObject rootBone = bones.has(rootName) ? bones.getAsJsonObject(rootName) : new JsonObject();
			rootBone.add("position", keys);
			bones.add(rootName, rootBone);
			out.add("bones", bones);
			out.remove("ground");
			return out;
		}
	}

	/** Left-right mirroring of a motion source (model space mirrors across X). */
	static final class Mirror {
		private Mirror() {
		}

		static JsonObject mirror(JsonObject o) {
			JsonObject m = o.deepCopy();
			if (m.has("bones")) {
				m.add("bones", bones(m.getAsJsonObject("bones")));
			}
			if (m.has("fp") && m.getAsJsonObject("fp").has("bones")) {
				JsonObject fp = m.getAsJsonObject("fp");
				// First-person curves are in camera space: mirroring across the camera's X turns them the same way.
				fp.add("bones", bones(fp.getAsJsonObject("bones")));
				if (fp.has("camera") && fp.getAsJsonObject("camera").has("rotation")) {
					// Camera yaw and roll flip; pitch stays.
					fp.getAsJsonObject("camera").add("rotation", keys(fp.getAsJsonObject("camera").getAsJsonArray("rotation"), 1, -1, -1));
				}
			}
			for (String k : new String[] {"mask", "move_mask"}) {
				if (m.has(k)) {
					JsonObject in = m.getAsJsonObject(k), outM = new JsonObject();
					for (Map.Entry<String, JsonElement> e : in.entrySet()) {
						outM.add(Rig.mirrorName(e.getKey()), e.getValue());
					}
					m.add(k, outM);
				}
			}
			if (m.has("locators")) {
				JsonObject in = m.getAsJsonObject("locators"), outL = new JsonObject();
				for (Map.Entry<String, JsonElement> e : in.entrySet()) {
					JsonObject l = e.getValue().getAsJsonObject().deepCopy();
					l.addProperty("bone", Rig.mirrorName(l.get("bone").getAsString()));
					for (String ab : new String[] {"a", "b"}) {
						if (l.has(ab)) {
							JsonArray v = l.getAsJsonArray(ab);
							v.set(0, new com.google.gson.JsonPrimitive(-v.get(0).getAsFloat()));
						}
					}
					outL.add(Rig.mirrorName(e.getKey()), l);
				}
				m.add("locators", outL);
			}
			if (m.has("hits")) {
				JsonArray h = new JsonArray();
				for (JsonElement e : m.getAsJsonArray("hits")) {
					h.add(Rig.mirrorName(e.getAsString()));
				}
				m.add("hits", h);
			}
			if (m.has("motion")) {
				JsonArray mo = m.getAsJsonArray("motion");
				if (mo.size() > 3) {
					mo.set(3, new com.google.gson.JsonPrimitive(-mo.get(3).getAsFloat()));
				}
			}
			m.remove("id");
			return m;
		}

		private static JsonObject bones(JsonObject in) {
			JsonObject out = new JsonObject();
			for (Map.Entry<String, JsonElement> e : in.entrySet()) {
				JsonObject b = e.getValue().getAsJsonObject(), nb = new JsonObject();
				if (b.has("rotation")) {
					nb.add("rotation", keys(b.getAsJsonArray("rotation"), 1, -1, -1));
				}
				if (b.has("position")) {
					nb.add("position", keys(b.getAsJsonArray("position"), -1, 1, 1));
				}
				if (b.has("scale")) {
					nb.add("scale", b.get("scale"));
				}
				out.add(Rig.mirrorName(e.getKey()), nb);
			}
			return out;
		}

		private static JsonArray keys(JsonArray keys, float sx, float sy, float sz) {
			JsonArray out = new JsonArray();
			for (JsonElement ke : keys) {
				JsonArray k = ke.getAsJsonArray().deepCopy();
				JsonArray v = k.get(1).getAsJsonArray();
				JsonArray nv = new JsonArray();
				nv.add(v.get(0).getAsFloat() * sx + 0F);
				nv.add(v.get(1).getAsFloat() * sy + 0F);
				nv.add(v.get(2).getAsFloat() * sz + 0F);
				k.set(1, nv);
				out.add(k);
			}
			return out;
		}
	}
}
