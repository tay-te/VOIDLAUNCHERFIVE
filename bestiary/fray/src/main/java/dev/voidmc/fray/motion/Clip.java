package dev.voidmc.fray.motion;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * A clip, parsed (ANIMATION.md §5.1). Channels keep the file's units: rotations in degrees (vanilla's
 * {@code KeyframeAnimations.degreeVec} convention), positions in pixels with Y up (converted as vanilla's
 * {@code posVec} does), scale as a factor. {@link ClipSampler} converts while sampling. Per-bone arrays are indexed by
 * the clip's {@link Rig}.
 *
 * <p>The server-only parts of a {@code .motion.json} (markers, locators, hits, hurt volumes, windows, damage) are kept
 * here for the bake and are not written into the client clip.
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

	/** How the clip mixes with the layers under it (ANIMATION.md §7.2). */
	public enum Blend {
		/** {@code lerp(under, rest + clip, weight)}: locomotion and actions. */
		OVERRIDE,
		/** {@code under + clip × weight}: hit reactions, breathing, recoil (vanilla's {@code KeyframeAnimation.apply}). */
		ADDITIVE;

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
	 * First-person data (ANIMATION.md §7.4): a framing (where the arms sit in view), an offset (view blocks) and pitch,
	 * camera kick channels (degrees; position in blocks, Y up), and per-bone rotation and position curves that replace
	 * the third-person ones in first person only. {@code rotation}/{@code position} are indexed by bone.
	 */
	public record FirstPerson(String framing, float[] offset, float pitch, @Nullable Channel camera, @Nullable Channel cameraPos,
		@Nullable Channel[] rotation, @Nullable Channel[] position, boolean hideArms) {
	}

	public final Identifier id;
	public final Rig rig;
	public final float length;
	public final Loop loop;
	public final Blend blend;
	public final int blendIn;
	public final int blendOut;
	/** Per bone weight (0..1); bones the file doesn't name are 1. */
	public final float[] mask;
	/**
	 * Per bone weight while the entity walks (the action layer goes upper-body only, ANIMATION.md §7.1), or null: the
	 * walk weight lerps the mask toward this.
	 */
	public final float @Nullable [] moveMask;
	/** Walk-synced loops: the clip plays one cycle per this many units of vanilla's walk position (0: by time). */
	public final float syncWalk;
	/** Per bone: rotation, position, scale channels (null when the clip doesn't animate it). */
	public final @Nullable Channel[] rotation;
	public final @Nullable Channel[] position;
	public final @Nullable Channel[] scale;
	public final @Nullable FirstPerson fp;
	public final List<Cue> cues;
	public final List<Marker> markers;
	public final Map<String, Locator> locators;
	public final List<String> hits;
	/** Hurt volumes (weak points) by locator name, with the tick window they exist in ({@code [from, to]}, inclusive). */
	public final Map<String, int[]> hurts;
	/** Named gameplay windows in ticks ({@code [from, to]} inclusive): iframes, parry, chain... (ANIM_EVENTS.md §2). */
	public final Map<String, int[]> windows;
	/** Bake checks the clip asks for ({@code ground}, {@code fp_framing}); failures stop the build. */
	public final List<String> checks;
	public final float modelScale;
	public final float damage;
	/**
	 * Root motion for the local player (players move client-side in Minecraft): {@code [from, to, blocks]} seconds and
	 * distance along the facing, plus an optional direction in degrees relative to the facing ({@code 0} ahead,
	 * {@code 90} to the left, {@code -90} right, {@code 180} back), or null.
	 */
	public final float @Nullable [] motion;

	public Clip(Identifier id, Rig rig, float length, Loop loop, Blend blend, int blendIn, int blendOut, float[] mask, float @Nullable [] moveMask,
		float syncWalk, Channel[] rotation, Channel[] position, Channel[] scale, @Nullable FirstPerson fp, List<Cue> cues, List<Marker> markers,
		Map<String, Locator> locators, List<String> hits, Map<String, int[]> hurts, Map<String, int[]> windows, List<String> checks, float modelScale,
		float damage, float @Nullable [] motion) {
		this.id = id;
		this.rig = rig;
		this.length = length;
		this.loop = loop;
		this.blend = blend;
		this.blendIn = blendIn;
		this.blendOut = blendOut;
		this.mask = mask;
		this.moveMask = moveMask;
		this.syncWalk = syncWalk;
		this.rotation = rotation;
		this.position = position;
		this.scale = scale;
		this.fp = fp;
		this.cues = cues;
		this.markers = markers;
		this.locators = locators;
		this.hits = hits;
		this.hurts = hurts;
		this.windows = windows;
		this.checks = checks;
		this.modelScale = modelScale;
		this.damage = damage;
		this.motion = motion;
	}

	/** The same clip with other server-only parts (the bake: curves from the canonical client file, the rest from the source). */
	public Clip withServerParts(Clip source) {
		return new Clip(source.id, this.rig, this.length, this.loop, this.blend, this.blendIn, this.blendOut, this.mask, this.moveMask, this.syncWalk,
			this.rotation, this.position, this.scale, this.fp, this.cues, source.markers, source.locators, source.hits, source.hurts, source.windows,
			source.checks, source.modelScale, source.damage, this.motion);
	}

	public int bones() {
		return this.rig.bones();
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
