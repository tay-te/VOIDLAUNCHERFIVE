package dev.voidmc.fray.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.fray.motion.bake.BlockbenchImport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/**
 * The Blockbench axis mapping, verified against <b>real Blockbench exports</b> (stage 1 asked for this instead of a
 * hand-written sample): {@code src/main/blockbench/rig/bestiary/gallows_knight.bbmodel} is the project as Blockbench
 * 5.1.2 itself saved it, and {@code src/test/resources/blockbench/gallows_knight.java} /
 * {@code gallows_knight_animations.java} are what Blockbench's own "Export Java Entity" and "Export Animations" wrote
 * for it (tools/blockbench/bb.mjs roundtrip drives the installed app). bbImport must produce the same bones, pivots,
 * rotations, boxes and keyframes as Blockbench's exporter: every number, in the same order.
 */
class BlockbenchExportTest {
	private static final Path PROJECT = Path.of("../src/main/blockbench/rig/bestiary/gallows_knight.bbmodel");
	private static final Path EXPORTS = Path.of("src/test/resources/blockbench");

	record Box(int u, int v, boolean mirror, float[] xyz, float[] size, float inflate) {
	}

	record Part(String name, String parent, float[] offset, float[] rot, List<Box> boxes) {
	}

	private static final Pattern PART = Pattern.compile(
		"PartDefinition (\\w+) = (\\w+)\\.addOrReplaceChild\\(\"(\\w+)\", CubeListBuilder\\.create\\(\\)(.*?), PartPose\\.(offset|offsetAndRotation)\\(([^)]*)\\)\\);");
	private static final Pattern BOX = Pattern.compile(
		"\\.texOffs\\((-?\\d+), (-?\\d+)\\)(\\.mirror\\(\\))?\\.addBox\\(([-\\d.F, ]+), new CubeDeformation\\(([-\\d.]+)F\\)\\)");

	static float[] floats(String csv) {
		String[] p = csv.split(",");
		float[] out = new float[p.length];
		for (int i = 0; i < p.length; i++) {
			out[i] = Float.parseFloat(p[i].trim().replace("F", ""));
		}
		return out;
	}

	static List<Part> parseModel(String java) {
		List<Part> parts = new ArrayList<>();
		// A part with several boxes continues on the next lines with ".texOffs(...)": join them first.
		for (String line : java.replaceAll("\\R\\s*\\.", ".").split("\n")) {
			Matcher m = PART.matcher(line.trim());
			if (!m.find()) {
				continue;
			}
			List<Box> boxes = new ArrayList<>();
			Matcher b = BOX.matcher(m.group(4));
			while (b.find()) {
				float[] a = floats(b.group(4));
				boxes.add(new Box(Integer.parseInt(b.group(1)), Integer.parseInt(b.group(2)), b.group(3) != null, new float[] {a[0], a[1], a[2]},
					new float[] {a[3], a[4], a[5]}, Float.parseFloat(b.group(5))));
			}
			float[] pose = floats(m.group(6));
			float[] rot = pose.length > 3 ? new float[] {pose[3], pose[4], pose[5]} : new float[3];
			parts.add(new Part(m.group(3), m.group(2).equals("partdefinition") ? "" : m.group(2), new float[] {pose[0], pose[1], pose[2]}, rot, boxes));
		}
		return parts;
	}

	@Test
	void geometryMatchesBlockbenchsJavaExport() throws Exception {
		JsonObject project = JsonParser.parseString(Files.readString(PROJECT)).getAsJsonObject();
		List<String> report = new ArrayList<>();
		Rig rig = BlockbenchImport.convertRig(project, Identifier.fromNamespaceAndPath("bestiary", "gallows_knight"), null, report).rig();
		List<Part> parts = parseModel(Files.readString(EXPORTS.resolve("gallows_knight.java")));
		assertEquals(parts.size(), rig.bones(), "bone count (Blockbench's export: " + parts.stream().map(Part::name).toList() + ")");
		int boxes = 0;
		for (int i = 0; i < parts.size(); i++) {
			Part p = parts.get(i);
			String where = "bone " + i + " " + p.name();
			assertEquals(p.name(), rig.name(i), where + ": name and order");
			assertEquals(p.parent(), rig.parent[i] < 0 ? "" : rig.name(rig.parent[i]), where + ": parent");
			PartPose r = rig.rest[i];
			assertEquals(p.offset()[0], r.x, 1e-3, where + " offset x");
			assertEquals(p.offset()[1], r.y, 1e-3, where + " offset y");
			assertEquals(p.offset()[2], r.z, 1e-3, where + " offset z");
			// The exporter writes radians rounded to four places.
			assertEquals(p.rot()[0], r.xRot, 2e-4, where + " xRot");
			assertEquals(p.rot()[1], r.yRot, 2e-4, where + " yRot");
			assertEquals(p.rot()[2], r.zRot, 2e-4, where + " zRot");
			assertEquals(p.boxes().size(), rig.cubes[i].size(), where + ": boxes");
			for (int k = 0; k < p.boxes().size(); k++) {
				Box e = p.boxes().get(k);
				Rig.Cube c = rig.cubes[i].get(k);
				String bw = where + " box " + k;
				assertEquals(e.u(), c.u(), bw + " u");
				assertEquals(e.v(), c.v(), bw + " v");
				assertEquals(e.mirror(), c.mirror(), bw + " mirror");
				assertEquals(e.xyz()[0], c.x(), 1e-3, bw + " x");
				assertEquals(e.xyz()[1], c.y(), 1e-3, bw + " y");
				assertEquals(e.xyz()[2], c.z(), 1e-3, bw + " z");
				assertEquals(e.size()[0], c.dx(), 1e-3, bw + " dx");
				assertEquals(e.size()[1], c.dy(), 1e-3, bw + " dy");
				assertEquals(e.size()[2], c.dz(), 1e-3, bw + " dz");
				assertEquals(e.inflate(), c.inflate(), 1e-3, bw + " inflate");
				boxes++;
			}
		}
		assertTrue(boxes >= 40, "boxes compared: " + boxes);
		assertTrue(rig.names.contains("cage_cage_r1") && rig.helper[rig.bone("cage_cage_r1")], "rotated cubes become Blockbench's _r1 helper bones");
		assertEquals(3, rig.locators.size(), "hv_ locators: " + rig.locators.keySet());
		System.out.println("Blockbench export matched: " + parts.size() + " bones, " + boxes + " boxes; " + report);
	}

	private static final Pattern CHANNEL = Pattern.compile("^\"(\\w+)\", new AnimationChannel\\(AnimationChannel\\.Targets\\.(\\w+),");
	private static final Pattern KEY = Pattern.compile(
		"new Keyframe\\(([-\\d.]+)F, KeyframeAnimations\\.(\\w+)\\(([-\\d.F, ]+)\\), AnimationChannel\\.Interpolations\\.(\\w+)\\)");

	@Test
	void keyframesMatchBlockbenchsAnimationExport() throws Exception {
		JsonObject project = JsonParser.parseString(Files.readString(PROJECT)).getAsJsonObject();
		List<String> report = new ArrayList<>();
		Rig rig = BlockbenchImport.convertRig(project, Identifier.fromNamespaceAndPath("bestiary", "gallows_knight"), null, report).rig();
		RigJson.register(rig);
		Map<String, JsonObject> sources = BlockbenchImport.convertAnimations(project, rig.id.toString(), rig.names, report);
		Clip clip = ClipJson.parse(Identifier.fromNamespaceAndPath("bestiary", "gallows_knight/axis_probe"), sources.get("gallows_knight/axis_probe"));
		String java = Files.readString(EXPORTS.resolve("gallows_knight_animations.java"));
		int channels = 0, samples = 0;
		Map<String, Integer> seen = new LinkedHashMap<>();
		String[] chunks = java.split("\\.addAnimation\\(");
		for (int ci = 1; ci < chunks.length; ci++) {
			String chunk = chunks[ci];
			Matcher head = CHANNEL.matcher(chunk);
			if (!head.find()) {
				continue;
			}
			String bone = head.group(1), target = head.group(2);
			List<Float> times = new ArrayList<>();
			List<float[]> values = new ArrayList<>();
			List<Interp> interps = new ArrayList<>();
			Matcher k = KEY.matcher(chunk);
			while (k.find()) {
				times.add(Float.parseFloat(k.group(1)));
				values.add(floats(k.group(3)));
				interps.add(k.group(4).equals("CATMULLROM") ? Interp.CATMULLROM : Interp.LINEAR);
			}
			float[] t = new float[times.size()];
			float[] v = new float[times.size() * 3];
			for (int i = 0; i < t.length; i++) {
				t[i] = times.get(i);
				System.arraycopy(values.get(i), 0, v, i * 3, 3);
			}
			Channel exported = new Channel(t, v, interps.toArray(new Interp[0]));
			int b = rig.bone(bone);
			Channel ours = switch (target) {
				case "ROTATION" -> clip.rotation[b];
				case "POSITION" -> clip.position[b];
				default -> clip.scale[b];
			};
			assertTrue(ours != null, bone + " " + target + " converted");
			// Both play with vanilla's rules (the arriving key's interpolation); a Blockbench step is a key 1 ms early in
			// the export and a STEP key in ours, so the 2 ms around keys are skipped.
			float[] a = new float[3], c = new float[3];
			for (int i = 0; i <= 200; i++) {
				float time = clip.length * i / 200F;
				boolean nearKey = false;
				for (float kt : t) {
					nearKey |= Math.abs(time - kt) < 0.002F;
				}
				if (nearKey) {
					continue;
				}
				exported.sample(time, a, 0);
				ours.sample(time, c, 0);
				for (int ax = 0; ax < 3; ax++) {
					assertEquals(a[ax], c[ax], 1e-3, bone + " " + target + " axis " + ax + " at " + time + " s");
				}
				samples++;
			}
			seen.merge(bone, 1, Integer::sum);
			channels++;
		}
		assertEquals(4, channels, "channels in the export: " + seen);
		System.out.println("Blockbench animation export matched: " + channels + " channels, " + samples + " samples");
	}
}
