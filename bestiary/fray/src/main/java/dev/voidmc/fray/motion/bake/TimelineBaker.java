package dev.voidmc.fray.motion.bake;

import dev.voidmc.fray.motion.Clip;
import dev.voidmc.fray.motion.ClipSampler;
import dev.voidmc.fray.motion.HumanoidRig;
import dev.voidmc.fray.motion.HumanoidSolver;
import dev.voidmc.fray.motion.PartPose;
import dev.voidmc.fray.motion.PoseBuffer;
import dev.voidmc.fray.motion.Timeline;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Bakes a clip into its server timeline: phases from the clip's markers ({@code windup_end}, {@code active_end}), events
 * from the other markers, and a hit capsule per ACTIVE tick for each locator in {@code hits}, placed by sampling the
 * clip with {@link ClipSampler} and posing the rig with {@link HumanoidSolver}: the same code the client draws with.
 */
public final class TimelineBaker {
	private TimelineBaker() {
	}

	public static Timeline bake(Clip clip, String clipHash, List<String> warnings) {
		int ticks = clip.lengthTicks();
		float tW = clip.marker("windup_end");
		float tA = clip.marker("active_end");
		int start = -1, end = -1;
		if (tW >= 0 || tA >= 0) {
			if (tW < 0 || tA < 0) {
				throw new IllegalArgumentException(clip.id + ": a clip with phases needs both windup_end and active_end markers");
			}
			start = ticksAt(tW);
			end = ticksAt(tA);
			if (!(start >= 1 && end > start && end <= ticks)) {
				throw new IllegalArgumentException(clip.id + ": phases out of order (active ticks " + start + ".." + (end - 1) + " of " + ticks + ")");
			}
			if (clip.blendIn > start) {
				warnings.add(clip.id + ": blend_in " + clip.blendIn + " runs into ACTIVE (tick " + start + "): the client would still be blending in");
			}
			if (clip.blendOut > ticks - end) {
				warnings.add(clip.id + ": blend_out " + clip.blendOut + " starts before ACTIVE ends");
			}
		} else if (!clip.hits.isEmpty() && !clip.locators.isEmpty()) {
			warnings.add(clip.id + ": has locators but no phases, so no hit volumes");
		}
		for (String h : clip.hits) {
			if (!clip.locators.containsKey(h)) {
				throw new IllegalArgumentException(clip.id + ": hit '" + h + "' names no locator");
			}
		}
		List<Timeline.Event> events = new ArrayList<>();
		int tellFrom = -1;
		String tellColour = "";
		for (Clip.Marker m : clip.markers) {
			switch (m.name()) {
				case "windup_end", "active_end" -> {
				}
				case "tell" -> {
					tellFrom = ticksAt(m.time());
					tellColour = m.arg().isEmpty() ? "orange" : m.arg();
					events.add(new Timeline.Event(tellFrom, "tell", tellColour));
				}
				default -> events.add(new Timeline.Event(ticksAt(m.time()), m.name(), m.arg()));
			}
		}
		Map<String, Timeline.Capsule[]> volumes = new LinkedHashMap<>();
		Map<String, Timeline.Capsule> from = new LinkedHashMap<>();
		if (start >= 0) {
			for (String h : clip.hits) {
				events.add(new Timeline.Event(start, "hit_start", h));
				events.add(new Timeline.Event(end - 1, "hit_end", h));
				Timeline.Capsule[] caps = new Timeline.Capsule[end - start];
				for (int k = start; k < end; k++) {
					caps[k - start] = capsuleAt(clip, clip.locators.get(h), k);
				}
				volumes.put(h, caps);
				from.put(h, capsuleAt(clip, clip.locators.get(h), start - 1));
			}
		}
		events.sort((a, b) -> Integer.compare(a.tick(), b.tick()));
		return new Timeline(clip.id, clipHash, ticks, clip.modelScale, start, end, List.copyOf(events), volumes, from, clip.damage, tellFrom, tellColour);
	}

	/** The tick a marker time falls in: the first tick whose time is at or after it. */
	public static int ticksAt(float seconds) {
		return (int) Math.ceil(seconds * 20 - 1e-4);
	}

	/** The locator's capsule at tick {@code k}, in entity-local blocks, the clip at full weight over the rest pose. */
	public static Timeline.Capsule capsuleAt(Clip clip, Clip.Locator loc, int k) {
		PoseBuffer buf = new PoseBuffer();
		ClipSampler.accumulate(clip, clip.localTime(k / 20F), 1, buf, new float[3]);
		PartPose[] rest = HumanoidRig.restPose();
		PartPose[] parts = HumanoidRig.restPose();
		HumanoidSolver solver = new HumanoidSolver();
		solver.solve(parts, rest, buf);
		Matrix4f root = new Matrix4f();
		boolean hasRoot = solver.rootTransform(buf, root);
		Vector3f a = HumanoidSolver.partToModel(parts[loc.bone()], loc.a()[0], loc.a()[1], loc.a()[2], new Vector3f());
		Vector3f b = HumanoidSolver.partToModel(parts[loc.bone()], loc.b()[0], loc.b()[1], loc.b()[2], new Vector3f());
		HumanoidSolver.modelToEntity(a, hasRoot ? root : null, clip.modelScale, a);
		HumanoidSolver.modelToEntity(b, hasRoot ? root : null, clip.modelScale, b);
		return new Timeline.Capsule(a.x, a.y, a.z, b.x, b.y, b.z, loc.radius());
	}
}
