package dev.voidmc.fray.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.voidmc.fray.motion.bake.BlockbenchImport;
import dev.voidmc.fray.motion.bake.MotionBake;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/** The bake's validation tests (ANIMATION.md §6): clips bake, phases are ordered, volumes reach, hashes are stable. */
class BakeTest {
	private static final Path MOTION = Path.of("../src/main/motion");

	private static MotionBake.Baked bake(String path) throws Exception {
		String src = Files.readString(MOTION.resolve("bestiary/" + path + ".motion.json"));
		return MotionBake.bake(Identifier.fromNamespaceAndPath("bestiary", path), src, new ArrayList<>());
	}

	@Test
	void clawPhasesAndVolumes() throws Exception {
		MotionBake.Baked b = bake("zombie/claw");
		Timeline t = b.timeline();
		assertEquals(26, t.ticks);
		assertEquals(11, t.activeStart);
		assertEquals(14, t.activeEnd);
		assertEquals(3, t.tellFrom);
		Timeline.Track track = t.volumes.get("claw_r");
		// The track starts the tick before ACTIVE (where the first sweep starts).
		assertEquals(10, track.from());
		Timeline.Capsule[] caps = java.util.Arrays.copyOfRange(track.caps(), 1, track.caps().length);
		assertEquals(3, caps.length);
		float reach = 0;
		for (Timeline.Capsule c : caps) {
			reach = Math.max(reach, c.reach());
		}
		System.out.println(MotionBake.describe(b.clip(), t));
		// The claw reaches past arm's length but not to a second block.
		assertTrue(reach > 0.9 && reach < 1.8, "reach " + reach);
		// At the top of the windup (tick 11) the claw is overhead; by tick 13 it is in front, lower down.
		assertTrue(caps[0].by() > 2.0, "tick 11 b.y " + caps[0].by());
		assertTrue(caps[2].bz() > 0.5, "tick 13 b.z " + caps[2].bz());
		assertTrue(caps[2].by() < caps[0].by());
	}

	@Test
	void everyClipBakesAndHashesAreStable() throws Exception {
		Path rigs = Path.of("../src/main/blockbench/rig");
		MotionBake.Result one = MotionBake.bakeAll(MOTION, rigs, null);
		MotionBake.Result two = MotionBake.bakeAll(MOTION, rigs, null);
		assertTrue(one.clips().size() >= 20, "clips: " + one.clips().keySet());
		assertTrue(one.failures().isEmpty(), "checks the clips ask for: " + one.failures());
		for (Map.Entry<Identifier, MotionBake.Baked> e : one.clips().entrySet()) {
			String path = e.getKey().toString();
			MotionBake.Baked a = e.getValue(), b = two.clips().get(e.getKey());
			assertEquals(a.clipHash(), b.clipHash(), path);
			assertEquals(a.timeline().write(), b.timeline().write(), path);
			// The client clip and the timeline agree on length.
			assertEquals(a.clip().lengthTicks(), a.timeline().ticks, path);
			// The timeline written and read back is the same timeline.
			assertEquals(a.timeline().write(), Timeline.parse(a.timeline().write()).write(), path);
		}
	}

	@Test
	void blockbenchImport() throws Exception {
		String bb = Files.readString(Path.of("../src/main/blockbench/zombie_taunt.bbmodel"));
		List<String> report = new ArrayList<>();
		Map<String, JsonObject> out = BlockbenchImport.convert(bb, "fray:humanoid", report);
		report.forEach(System.out::println);
		JsonObject src = out.get("zombie/taunt_slam");
		var rarm = src.getAsJsonObject("bones").getAsJsonObject("right_arm").getAsJsonArray("rotation");
		// Blockbench (150, 0, 30) at 0.5 s becomes vanilla's convention (-150, 0, 30).
		var k = rarm.get(1).getAsJsonArray();
		assertEquals(0.5, k.get(0).getAsDouble(), 1e-6);
		assertEquals(-150, k.get(1).getAsJsonArray().get(0).getAsDouble(), 1e-6);
		assertEquals(30, k.get(1).getAsJsonArray().get(2).getAsDouble(), 1e-6);
		assertEquals(3, src.getAsJsonArray("markers").size());
		var slam = src.getAsJsonObject("locators").getAsJsonObject("slam_r");
		assertEquals("right_arm", slam.get("bone").getAsString());
		assertEquals(-1, slam.getAsJsonArray("a").get(0).getAsDouble(), 1e-6);
		assertEquals(5, slam.getAsJsonArray("a").get(1).getAsDouble(), 1e-6);
		// And it bakes: two hit volumes, phases from the instruction keyframes.
		MotionBake.Baked baked = MotionBake.bake(Identifier.fromNamespaceAndPath("bestiary", "zombie/taunt_slam"), src.toString(), new ArrayList<>());
		assertEquals(18, baked.timeline().activeStart);
		assertEquals(21, baked.timeline().activeEnd);
		assertEquals(2, baked.timeline().volumes.size());
	}
}
