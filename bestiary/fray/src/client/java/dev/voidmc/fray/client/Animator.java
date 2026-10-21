package dev.voidmc.fray.client;

import dev.voidmc.fray.motion.Clip;
import dev.voidmc.fray.motion.ClipSampler;
import dev.voidmc.fray.motion.PoseBuffer;
import org.jspecify.annotations.Nullable;

/**
 * One animated entity on the client (ANIMATION.md §7.1). The prototype has two override layers: BASE (an idle or
 * locomotion loop, weighted down as the entity walks) and ACTION (the server's clip). Each layer crossfades from its
 * previous clip over the new clip's {@code blend_in}; an action fades out over {@code blend_out} before its end.
 *
 * <p>Time is in server ticks as this client sees them ({@code level.getGameTime() + partialTick}), so a clip started
 * at server tick S is at {@code now - S} here: late by exactly the latency that delays the entity's movement too.
 */
public final class Animator {
	public static final int BASE = 0;
	public static final int ACTION = 1;

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

	final Layer[] layers = {new Layer(), new Layer()};
	final PoseBuffer buffer = new PoseBuffer();
	private final float[] scratch = new float[3];
	/** Studio and tests: sample the ACTION layer at this many ticks after its start instead of the clock (NaN: live). */
	public double frozenElapsed = Double.NaN;
	private double frozen = Double.NaN;
	/** The last time this animator was sampled (client ticks), for pruning. */
	long lastUsed;

	public void play(int layer, Clip clip, double start, float speed) {
		Layer l = this.layers[layer];
		if (l.cur.clip != null) {
			l.prev.set(l.cur);
		}
		l.cur.clip = clip;
		l.cur.start = start;
		l.cur.speed = speed;
		l.cur.stopAt = Double.NaN;
	}

	public void stop(int layer, double now, int blendTicks) {
		Slot s = this.layers[layer].cur;
		if (s.clip != null && Double.isNaN(s.stopAt)) {
			s.stopAt = now;
			s.stopTicks = Math.max(1, blendTicks);
		}
	}

	/** Moves an action's start (prediction re-based on the server's ack). */
	public void rebase(int layer, double start) {
		this.layers[layer].cur.start = start;
	}

	public @Nullable Clip clip(int layer) {
		return this.layers[layer].cur.clip;
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
		return Double.isNaN(this.frozenElapsed) ? (now - s.start) * s.speed : this.frozenElapsed;
	}

	public boolean idle() {
		return this.layers[BASE].cur.clip == null && this.layers[ACTION].cur.clip == null;
	}

	/**
	 * Samples every layer at {@code now} (ticks) into the animator's buffer. {@code baseWeight} scales the BASE layer
	 * (an idle fades out while walking). Returns the buffer, or null when nothing carries weight.
	 */
	public @Nullable PoseBuffer sample(double now, float baseWeight) {
		PoseBuffer buf = this.buffer;
		buf.clear();
		for (int i = 0; i < this.layers.length; i++) {
			Layer l = this.layers[i];
			float layerWeight = i == BASE ? baseWeight : 1;
			this.frozen = i == ACTION ? this.frozenElapsed : Double.NaN;
			if (l.prev.clip != null) {
				float w = this.envelope(l.prev, now);
				// The previous clip is done once the current one has fully blended in.
				if (w <= 0 || this.blendIn(l.cur, now) >= 1) {
					l.prev.clip = null;
				} else {
					this.accumulate(l.prev, now, w * layerWeight);
				}
			}
			if (l.cur.clip != null) {
				float w = this.envelope(l.cur, now);
				if (w <= 0 && this.ended(l.cur, now)) {
					l.cur.clip = null;
				} else {
					this.accumulate(l.cur, now, w * layerWeight);
				}
			}
		}
		return buf.active ? buf : null;
	}

	private void accumulate(Slot s, double now, float w) {
		if (w <= 0) {
			return;
		}
		double elapsedTicks = Double.isNaN(this.frozen) ? (now - s.start) * s.speed : this.frozen;
		ClipSampler.accumulate(s.clip, s.clip.localTime((float) (elapsedTicks / 20.0)), w, this.buffer, this.scratch);
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
		return (float) (w * w * (3 - 2 * w));
	}

	private static double clamp(double v) {
		return v < 0 ? 0 : v > 1 ? 1 : v;
	}
}
