package dev.voidmc.fray.client;

import dev.voidmc.fray.motion.Clip;
import dev.voidmc.fray.motion.ClipSampler;
import dev.voidmc.fray.motion.PoseBuffer;
import dev.voidmc.fray.motion.Rig;
import org.jspecify.annotations.Nullable;

/**
 * One animated entity on the client (ANIMATION.md §7.1). Layers, bottom to top:
 * <ol>
 * <li>{@link #BASE}: an idle loop (client-side, never sent), fading out as the entity walks;</li>
 * <li>{@link #LOCO}: a walk loop synced to vanilla's walk cycle (client-side), fading in with walk speed;</li>
 * <li>{@link #ACTION}: the server's clip (or the local player's predicted one); while the entity walks it goes upper-body
 * only by the clip's {@code move_mask};</li>
 * <li>{@link #ADD}: additive reactions (hit flinches) summed on top, each fading by itself.</li>
 * </ol>
 * Each override layer crossfades from its previous clip over the new clip's {@code blend_in}; an action fades out over
 * {@code blend_out} before its end. Time is in server ticks as this client sees them ({@code level.getGameTime() +
 * partialTick}), so a clip started at server tick S is at {@code now - S} here: late by exactly the latency that delays
 * the entity's movement too.
 *
 * <p>The sample is cached per rendered frame: Iris's shadow pass re-extracts every entity, and the second extraction
 * reuses the first's pose instead of sampling again.
 */
public final class Animator {
	public static final int BASE = 0;
	public static final int ACTION = 1;
	public static final int LOCO = 2;
	public static final int ADD = 3;
	private static final int[] ORDER = {BASE, LOCO, ACTION, ADD};
	/** Additive reactions that may overlap. */
	private static final int ADD_SLOTS = 3;

	static final class Slot {
		@Nullable Clip clip;
		double start;
		float speed = 1;
		/** When a stop was asked for (ticks), or NaN. */
		double stopAt = Double.NaN;
		int stopTicks;

		void set(Slot o) {
			this.clip = o.clip;
			this.start = o.start;
			this.speed = o.speed;
			this.stopAt = o.stopAt;
			this.stopTicks = o.stopTicks;
		}
	}

	static final class Layer {
		final Slot cur = new Slot();
		final Slot prev = new Slot();
	}

	final Layer[] layers = {new Layer(), new Layer(), new Layer(), new Layer()};
	private final Slot[] additive = {new Slot(), new Slot(), new Slot()};
	PoseBuffer buffer = new PoseBuffer(Rig.HUMANOID);
	private final float[] scratch = new float[3];
	private float[] mask = new float[Rig.HUMANOID.bones()];
	/** Studio and tests: sample the ACTION and ADD layers at this many ticks after their start (NaN: live). */
	public double frozenElapsed = Double.NaN;
	/**
	 * Hit-stop's hold (COMBAT.md §5.2): until {@code holdUntil} every layer is sampled at {@code holdAt} (and vanilla's walk
	 * position as it was), so the pose stops dead while the clips keep their start ticks; after it the pose rejoins the
	 * clips' (the server's) timeline. Animator time (client ticks); NaN: not held.
	 */
	private double holdAt = Double.NaN, holdUntil = Double.NaN;
	private float holdWalkPos = Float.NaN, holdWalk;
	private double frozen = Double.NaN;
	/** The last time this animator was sampled (client ticks), for pruning. */
	long lastUsed;
	/** The frame and time of the cached sample. */
	long cachedFrame = -1;
	double cachedNow = Double.NaN;
	@Nullable PoseBuffer cached;

	public void play(int layer, Clip clip, double start, float speed) {
		if (layer == ADD) {
			// The oldest slot gives way.
			Slot best = this.additive[0];
			for (Slot s : this.additive) {
				if (s.clip == null) {
					best = s;
					break;
				}
				if (s.start < best.start) {
					best = s;
				}
			}
			best.clip = clip;
			best.start = start;
			best.speed = speed;
			best.stopAt = Double.NaN;
			this.ensureRig(clip.rig);
			this.cachedFrame = -1;
			return;
		}
		Layer l = this.layers[layer];
		if (l.cur.clip != null) {
			l.prev.set(l.cur);
		}
		l.cur.clip = clip;
		l.cur.start = start;
		l.cur.speed = speed;
		l.cur.stopAt = Double.NaN;
		this.ensureRig(clip.rig);
		this.cachedFrame = -1;
	}

	private void ensureRig(Rig rig) {
		if (this.buffer.rig != rig && this.buffer.bones != rig.bones()) {
			this.buffer = new PoseBuffer(rig);
			this.mask = new float[rig.bones()];
		}
		this.buffer.rig = rig;
	}

	public void stop(int layer, double now, int blendTicks) {
		Slot s = this.layers[layer].cur;
		if (s.clip != null && Double.isNaN(s.stopAt)) {
			s.stopAt = now;
			s.stopTicks = Math.max(1, blendTicks);
		}
		this.cachedFrame = -1;
	}

	/**
	 * Holds the whole pose from {@code now} until {@code until} (animator time, ticks): hit-stop. A hold already running
	 * keeps its pose and is extended; one that ended starts afresh. First person, the camera channels and third person all
	 * read the held time, so the local player's own arms freeze with everyone else.
	 */
	public void hold(double now, double until) {
		if (!(now < this.holdUntil)) {
			this.holdAt = now;
			this.holdWalkPos = Float.NaN;
			this.holdUntil = until;
		} else {
			this.holdUntil = Math.max(this.holdUntil, until);
		}
		this.cachedFrame = -1;
	}

	/** Ends a hold now (tests, the studio). */
	public void release() {
		this.holdAt = this.holdUntil = Double.NaN;
		this.cachedFrame = -1;
	}

	/** Whether the pose is held at {@code now}. */
	public boolean held(double now) {
		return now < this.holdUntil;
	}

	/** The time the layers are sampled at for {@code now}: the hold's moment while held, else {@code now}. */
	public double time(double now) {
		return now < this.holdUntil ? this.holdAt : now;
	}

	/** Moves an action's start (prediction re-based on the server's ack). */
	public void rebase(int layer, double start) {
		this.layers[layer].cur.start = start;
		this.cachedFrame = -1;
	}

	public @Nullable Clip clip(int layer) {
		return this.layers[layer].cur.clip;
	}

	/** Whether any layer (or additive slot) is playing this clip. */
	public boolean playing(net.minecraft.resources.Identifier id) {
		for (Layer l : this.layers) {
			if (l.cur.clip != null && l.cur.clip.id.equals(id)) {
				return true;
			}
		}
		for (Slot s : this.additive) {
			if (s.clip != null && s.clip.id.equals(id)) {
				return true;
			}
		}
		return false;
	}

	public double start(int layer) {
		return this.layers[layer].cur.start;
	}

	/** Ticks into the ACTION clip at {@code now} (the frozen time when frozen), or NaN without an action. */
	public double actionElapsed(double now) {
		Slot s = this.layers[ACTION].cur;
		if (s.clip == null) {
			return Double.NaN;
		}
		return Double.isNaN(this.frozenElapsed) ? (this.time(now) - s.start) * s.speed : this.frozenElapsed;
	}

	/**
	 * The ACTION clip's weight at {@code now} (its blend in from the start, its blend out before the end or after a stop),
	 * for first person's crossfade with vanilla's held item; 0 without an action.
	 */
	public float actionWeight(double now) {
		Slot s = this.layers[ACTION].cur;
		if (s.clip == null) {
			return 0;
		}
		this.frozen = this.frozenElapsed;
		return this.envelope(s, this.time(now));
	}

	/** How far the ACTION clip has blended in over the previous one (0..1, smoothstepped). */
	public float actionBlendIn(double now) {
		Slot s = this.layers[ACTION].cur;
		this.frozen = this.frozenElapsed;
		return smooth(this.blendIn(s, this.time(now)));
	}

	/** The ACTION layer's previous clip while it is still blending out under the current one, or null. */
	public @Nullable Clip previousAction(double now) {
		Layer l = this.layers[ACTION];
		if (l.prev.clip == null || l.cur.clip == null) {
			return null;
		}
		this.frozen = Double.NaN;
		return this.envelope(l.prev, this.time(now)) > 0 && this.blendIn(l.cur, this.time(now)) < 1 ? l.prev.clip : null;
	}

	/** Ticks into the previous ACTION clip at {@code now}. */
	public double previousElapsed(double now) {
		Slot s = this.layers[ACTION].prev;
		return (this.time(now) - s.start) * s.speed;
	}

	public boolean idle() {
		for (Layer l : this.layers) {
			if (l.cur.clip != null) {
				return false;
			}
		}
		for (Slot s : this.additive) {
			if (s.clip != null) {
				return false;
			}
		}
		return true;
	}

	/** Back-compatible: no locomotion input. */
	public @Nullable PoseBuffer sample(double now, float baseWeight) {
		return this.sample(now, 0, 1 - baseWeight);
	}

	/**
	 * Samples every layer at {@code now} (ticks) into the animator's buffer. {@code walkPos} and {@code walk} (0..1) are
	 * vanilla's walk animation position and speed from the render state. Returns the buffer, or null when nothing
	 * carries weight.
	 */
	public @Nullable PoseBuffer sample(double now, float walkPos, float walk) {
		if (now < this.holdUntil) {
			now = this.holdAt;
			if (Float.isNaN(this.holdWalkPos)) {
				this.holdWalkPos = walkPos;
				this.holdWalk = walk;
			}
			walkPos = this.holdWalkPos;
			walk = this.holdWalk;
		}
		PoseBuffer buf = this.buffer;
		buf.clear();
		float moving = smooth(Math.min(1, walk * 4));
		boolean hasLoco = this.layers[LOCO].cur.clip != null;
		for (int i : ORDER) {
			if (i == ADD) {
				this.frozen = this.frozenElapsed;
				for (Slot s : this.additive) {
					if (s.clip == null) {
						continue;
					}
					float w = this.envelope(s, now);
					if (w <= 0 && this.ended(s, now)) {
						s.clip = null;
					} else {
						this.accumulate(s, now, w, null, 0);
					}
				}
				continue;
			}
			Layer l = this.layers[i];
			float layerWeight = switch (i) {
				case BASE -> hasLoco ? 1 : 1 - moving;
				case LOCO -> moving;
				default -> 1;
			};
			this.frozen = i == ACTION ? this.frozenElapsed : Double.NaN;
			float[] m = null;
			if (i == ACTION && l.cur.clip != null && l.cur.clip.moveMask != null && moving > 0) {
				// Moving: the action goes upper-body only (legs keep walking underneath).
				Clip c = l.cur.clip;
				m = this.mask;
				for (int b = 0; b < m.length && b < c.mask.length; b++) {
					m[b] = c.mask[b] + (c.moveMask[b] - c.mask[b]) * moving;
				}
			}
			if (l.prev.clip != null) {
				float w = this.envelope(l.prev, now);
				// The previous clip is done once the current one has fully blended in.
				if (w <= 0 || this.blendIn(l.cur, now) >= 1) {
					l.prev.clip = null;
				} else {
					this.accumulate(l.prev, now, w * layerWeight, null, walkPos);
				}
			}
			if (l.cur.clip != null) {
				float w = this.envelope(l.cur, now);
				if (w <= 0 && this.ended(l.cur, now)) {
					l.cur.clip = null;
				} else {
					this.accumulate(l.cur, now, w * layerWeight, m, walkPos);
				}
			}
		}
		return buf.active ? buf : null;
	}

	private static float smooth(float w) {
		return w * w * (3 - 2 * w);
	}

	private void accumulate(Slot s, double now, float w, float @Nullable [] mask, float walkPos) {
		if (w <= 0 || s.clip.rig != this.buffer.rig) {
			return;
		}
		float t;
		if (s.clip.syncWalk > 0 && Double.isNaN(this.frozen)) {
			// One cycle of the clip per syncWalk units of vanilla's walk position: arms swing in step with the legs.
			float cycles = walkPos / s.clip.syncWalk;
			t = (cycles - (float) Math.floor(cycles)) * s.clip.length;
		} else {
			double elapsedTicks = Double.isNaN(this.frozen) ? (now - s.start) * s.speed : this.frozen;
			t = s.clip.localTime((float) (elapsedTicks / 20.0));
		}
		ClipSampler.accumulate(s.clip, t, w, mask, this.buffer, this.scratch);
	}

	private float blendIn(Slot s, double now) {
		if (s.clip == null) {
			return 1;
		}
		double e = Double.isNaN(this.frozen) ? (now - s.start) * s.speed : this.frozen;
		return s.clip.blendIn <= 0 ? 1 : (float) clamp(e / s.clip.blendIn);
	}

	private boolean ended(Slot s, double now) {
		double e = (now - s.start) * s.speed;
		if (!Double.isNaN(s.stopAt) && now >= s.stopAt + s.stopTicks) {
			return true;
		}
		return Double.isNaN(this.frozen) && s.clip.loop == Clip.Loop.NONE && e >= s.clip.length * 20;
	}

	/** The slot's weight now: blend in from its start, out before its end (or after a stop), smoothstepped. */
	private float envelope(Slot s, double now) {
		Clip c = s.clip;
		double e = Double.isNaN(this.frozen) ? (now - s.start) * s.speed : this.frozen;
		if (e < 0) {
			// Started in this client's future (prediction ahead of the clock): hold the first pose.
			e = 0;
		}
		double w = c.blendIn <= 0 ? 1 : clamp(e / c.blendIn);
		if (c.loop == Clip.Loop.NONE && Double.isNaN(this.frozen)) {
			double left = c.length * 20 - e;
			w = Math.min(w, c.blendOut <= 0 ? (left > 0 ? 1 : 0) : clamp(left / c.blendOut));
		}
		if (!Double.isNaN(s.stopAt)) {
			w = Math.min(w, clamp((s.stopAt + s.stopTicks - now) / s.stopTicks));
		}
		return smooth((float) w);
	}

	private static double clamp(double v) {
		return v < 0 ? 0 : v > 1 ? 1 : v;
	}
}
