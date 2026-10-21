package dev.voidmc.fray.motion.bake;

import dev.voidmc.fray.motion.Clip;
import dev.voidmc.fray.motion.Timeline;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.joml.Vector3f;

/**
 * Bakes a clip into its server timeline: strikes from the clip's markers (each {@code windup_end} pairs with the next
 * {@code active_end}), named windows, events from the other markers, and per-tick capsules for each hit volume (every
 * tick from the one before the first strike to the end of the last) and each hurt volume (its window), placed by
 * {@link Posed}: the same sampler and solvers the client draws with.
 */
public final class TimelineBaker {
	private TimelineBaker() {
	}

	public static Timeline bake(Clip clip, String clipHash, List<String> warnings) {
		int ticks = clip.lengthTicks();
		List<Float> wEnds = new ArrayList<>();
		List<Float> aEnds = new ArrayList<>();
		for (Clip.Marker m : clip.markers) {
			if (m.name().equals("windup_end")) {
				wEnds.add(m.time());
			} else if (m.name().equals("active_end")) {
				aEnds.add(m.time());
			}
		}
		wEnds.sort(Float::compare);
		aEnds.sort(Float::compare);
		if (wEnds.size() != aEnds.size()) {
			throw new IllegalArgumentException(clip.id + ": " + wEnds.size() + " windup_end markers but " + aEnds.size() + " active_end markers");
		}
		int[][] strikes = new int[wEnds.size()][];
		int prevEnd = 0;
		for (int i = 0; i < strikes.length; i++) {
			int s = ticksAt(wEnds.get(i)), e = ticksAt(aEnds.get(i));
			if (!(s >= 1 && e > s && e <= ticks && s >= prevEnd)) {
				throw new IllegalArgumentException(clip.id + ": strike " + (i + 1) + " out of order (active ticks " + s + ".." + (e - 1) + " of " + ticks + ")");
			}
			strikes[i] = new int[] {s, e};
			prevEnd = e;
		}
		if (strikes.length > 0) {
			if (clip.blendIn > strikes[0][0]) {
				warnings.add(clip.id + ": blend_in " + clip.blendIn + " runs into ACTIVE (tick " + strikes[0][0] + "): the client would still be blending in");
			}
			if (clip.blendOut > ticks - strikes[strikes.length - 1][1]) {
				warnings.add(clip.id + ": blend_out " + clip.blendOut + " starts before ACTIVE ends");
			}
		} else if (!clip.hits.isEmpty() && !clip.locators.isEmpty()) {
			warnings.add(clip.id + ": has locators but no phases, so no hit volumes");
		}
		for (String h : clip.hits) {
			if (!clip.locators.containsKey(h)) {
				throw new IllegalArgumentException(clip.id + ": hit '" + h + "' names no locator (clip or rig)");
			}
			Clip.Locator loc = clip.locators.get(h);
			if (clip.rig.humanoid && clip.rotation[loc.bone()] == null && strikes.length > 0) {
				warnings.add(clip.id + ": hit volume " + h + " sits on " + clip.rig.name(loc.bone())
					+ ", which the clip doesn't rotate: on vanilla rigs the pose underneath shows through (ANIMATION.md risk 4)");
			}
		}
		for (String h : clip.hurts.keySet()) {
			if (!clip.locators.containsKey(h)) {
				throw new IllegalArgumentException(clip.id + ": hurt volume '" + h + "' names no locator (clip or rig)");
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
					tellColour = m.arg().isEmpty() ? "white" : m.arg();
					if (!List.of("white", "orange", "red", "purple").contains(tellColour)) {
						throw new IllegalArgumentException(clip.id + ": tell colour '" + tellColour + "' (white, orange, red or purple)");
					}
					events.add(new Timeline.Event(tellFrom, "tell", tellColour));
				}
				default -> events.add(new Timeline.Event(ticksAt(m.time()), m.name(), m.arg()));
			}
		}
		Map<String, int[]> windows = new LinkedHashMap<>();
		for (Map.Entry<String, int[]> w : clip.windows.entrySet()) {
			int from = w.getValue()[0], to = w.getValue()[1] < 0 ? ticks - 1 : w.getValue()[1];
			if (from < 0 || to < from || to >= ticks) {
				throw new IllegalArgumentException(clip.id + ": window " + w.getKey() + " [" + from + ", " + to + "] outside the clip's " + ticks + " ticks");
			}
			windows.put(w.getKey(), new int[] {from, to});
			events.add(new Timeline.Event(from, "window_open", w.getKey()));
			events.add(new Timeline.Event(to, "window_close", w.getKey()));
		}
		Map<String, Timeline.Track> volumes = new LinkedHashMap<>();
		if (strikes.length > 0) {
			int from = strikes[0][0] - 1, to = strikes[strikes.length - 1][1];
			for (String h : clip.hits) {
				for (int[] s : strikes) {
					events.add(new Timeline.Event(s[0], "hit_start", h));
					events.add(new Timeline.Event(s[1] - 1, "hit_end", h));
				}
				Timeline.Capsule[] caps = new Timeline.Capsule[to - from];
				for (int k = from; k < to; k++) {
					caps[k - from] = capsuleAt(clip, clip.locators.get(h), k);
				}
				volumes.put(h, new Timeline.Track(from, caps));
			}
		}
		Map<String, Timeline.Track> hurts = new LinkedHashMap<>();
		for (Map.Entry<String, int[]> h : clip.hurts.entrySet()) {
			int from = Math.max(0, h.getValue()[0]), to = h.getValue()[1] < 0 ? ticks - 1 : Math.min(ticks - 1, h.getValue()[1]);
			Timeline.Capsule[] caps = new Timeline.Capsule[to - from + 1];
			for (int k = from; k <= to; k++) {
				caps[k - from] = capsuleAt(clip, clip.locators.get(h.getKey()), k);
			}
			hurts.put(h.getKey(), new Timeline.Track(from, caps));
			events.add(new Timeline.Event(from, "vulnerable", h.getKey()));
		}
		events.sort((a, b) -> Integer.compare(a.tick(), b.tick()));
		return new Timeline(clip.id, clipHash, clip.rig.id, clip.rig.hash, ticks, clip.modelScale, strikes, List.copyOf(events), volumes, hurts, windows,
			clip.damage, tellFrom, tellColour);
	}

	/** The tick a marker time falls in: the first tick whose time is at or after it. */
	public static int ticksAt(float seconds) {
		return (int) Math.ceil(seconds * 20 - 1e-4);
	}

	/** The locator's capsule at tick {@code k}, in entity-local blocks, the clip at full weight over the rest pose. */
	public static Timeline.Capsule capsuleAt(Clip clip, Clip.Locator loc, int k) {
		Posed posed = new Posed(clip.rig, clip.modelScale).at(clip, k / 20F);
		Vector3f a = posed.point(loc.bone(), loc.a()[0], loc.a()[1], loc.a()[2], new Vector3f());
		Vector3f b = posed.point(loc.bone(), loc.b()[0], loc.b()[1], loc.b()[2], new Vector3f());
		return new Timeline.Capsule(a.x, a.y, a.z, b.x, b.y, b.z, loc.radius());
	}
}
