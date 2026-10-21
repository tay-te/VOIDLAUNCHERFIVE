package dev.voidmc.fray.motion;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * A clip, parsed (ANIMATION.md Â§5.1). Channels keep the file's units: rotations in degrees (vanilla's
 * {@code KeyframeAnimations.degreeVec} convention), positions in pixels with Y up (converted as vanilla's
 * {@code posVec} does), scale as a factor. {@link ClipSampler} converts while sampling.
 *
 * <p>The server-only parts of a {@code .motion.json} (markers, locators, hits, damage) are kept here for the bake and are
 * not written into the client clip.
 */
public final class Clip {
	public enum Loop {
		NONE, LOOP, HOLD;

		public static Loop parse(String s) {
			return switch (s) {
				case "none", "once", "false" -> NONE;
				case "loop", "true" -> LOOP;
				case "hold" -> HOLD;
				default -> throw new IllegalArgumentException("unknown loop mode " + s);
			};
		}

		public String id() {
			return this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public record Marker(float time, String name, String arg) {
	}

	public record Cue(float time, String kind, String arg) {
	}

	/** A capsule from {@code a} to {@code b} (model pixels in the bone's frame), {@code radius} in blocks. */
	public record Locator(String name, int bone, float[] a, float[] b, float radius) {
	}

	/**
	 * First-person data: a framing (where the arms sit in view), an offset (view blocks) and pitch, a camera kick channel
	 * (degrees), and per-bone rotation curves that replace the third-person ones in first person only (ANIMATION.md §7.4:
	 * "optional per-clip fp curves override the framing where a clip needs it"). {@code rotation} is indexed by bone.
	 */
	public record FirstPerson(String framing, float[] offset, float pitch, @Nullable Channel camera, @Nullable Channel[] rotation) {
	}

	public final Identifier id;
	public final Identifier rig;
	public final float length;
	public final Loop loop;
	public final int blendIn;
	public final int blendOut;
	/** Per bone weight (0..1); bones the file doesn't name are 1. */
	public final float[] mask;
	/** Per bone: rotation, position, scale channels (null when the clip doesn't animate it). */
	public final @Nullable Channel[] rotation;
	public final @Nullable Channel[] position;
	public final @Nullable Channel[] scale;
	public final @Nullable FirstPerson fp;
	public final List<Cue> cues;
	public final List<Marker> markers;
	public final Map<String, Locator> locators;
	public final List<String> hits;
	public final float modelScale;
	public final float damage;
	/**
	 * Root motion for the local player (players move client-side in Minecraft): {@code [from, to, blocks]} seconds and
	 * distance along the facing, or null. Creatures' root motion is the server's (stage 1).
	 */
	public final float @Nullable [] motion;

	public Clip(Identifier id, Identifier rig, float length, Loop loop, int blendIn, int blendOut, float[] mask, Channel[] rotation, Channel[] position,
		Channel[] scale, @Nullable FirstPerson fp, List<Cue> cues, List<Marker> markers, Map<String, Locator> locators, List<String> hits,
		float modelScale, float damage, float @Nullable [] motion) {
		this.id = id;
		this.rig = rig;
		this.length = length;
		this.loop = loop;
		this.blendIn = blendIn;
		this.blendOut = blendOut;
		this.mask = mask;
		this.rotation = rotation;
		this.position = position;
		this.scale = scale;
		this.fp = fp;
		this.cues = cues;
		this.markers = markers;
		this.locators = locators;
		this.hits = hits;
		this.modelScale = modelScale;
		this.damage = damage;
		this.motion = motion;
	}

	public int lengthTicks() {
		return Math.max(1, Math.round(this.length * 20));
	}

	/** The time of the first marker with this name, or -1. */
	public float marker(String name) {
		for (Marker m : this.markers) {
			if (m.name().equals(name)) {
				return m.time();
			}
		}
		return -1;
	}

	/** Seconds into the clip at {@code elapsed} seconds since it started, by its loop mode (never past the end). */
	public float localTime(float elapsed) {
		if (elapsed <= 0) {
			return 0;
		}
		if (this.loop == Loop.LOOP && this.length > 0) {
			return elapsed % this.length;
		}
		return Math.min(elapsed, this.length);
	}

	public boolean finished(float elapsed) {
		return this.loop == Loop.NONE && elapsed >= this.length;
	}
}
