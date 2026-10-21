package dev.voidmc.fray.combat.math;

import dev.voidmc.fray.api.StatusType;

/**
 * Status build-up meters (CONTRACTS.md §6.1): hits add build-up; a full meter procs its status and empties. Meters
 * drain 10 % of their size a second after 2 s without new build-up. Values are in build-up points; the threshold is
 * passed in (base 100 × (1 + status resistance)).
 */
public final class MeterSet {
	public static final float BASE = 100;
	public static final int DRAIN_DELAY = 40;
	public static final float DRAIN_PER_TICK = 0.10F / 20F;

	public final float[] value = new float[StatusType.METERED.length];
	public final float[] size = new float[StatusType.METERED.length];
	public final long[] lastBuild = new long[StatusType.METERED.length];

	public MeterSet() {
		java.util.Arrays.fill(this.size, BASE);
	}

	public static float threshold(float statusResist) {
		return threshold(BASE, statusResist);
	}

	/** {@code base × (1 + resistance)}, the resistance floored at -0.9 (a meter never shrinks below a tenth). */
	public static float threshold(float base, float statusResist) {
		return base * (1 + Math.max(-0.9F, statusResist));
	}

	/**
	 * Wet and Oiled interplay (MAGIC.md §14): Wet makes shock and frost build ×1.5 and fire ×0.5; Oiled doubles fire.
	 */
	public static float reactionScale(StatusType status, boolean wet, boolean oiled) {
		float m = 1;
		if (wet) {
			if (status == StatusType.SHOCK || status == StatusType.CHILL) {
				m *= 1.5F;
			} else if (status == StatusType.BURN) {
				m *= 0.5F;
			}
		}
		if (oiled && status == StatusType.BURN) {
			m *= 2;
		}
		return m;
	}

	/** Adds build-up; returns true if the meter filled (it is then emptied: the caller procs the status). */
	public boolean add(StatusType status, float amount, float threshold, long now) {
		int i = status.meterIndex();
		if (i < 0 || amount <= 0) {
			return false;
		}
		this.size[i] = threshold;
		this.value[i] += amount;
		this.lastBuild[i] = now;
		if (this.value[i] >= threshold) {
			this.value[i] = 0;
			return true;
		}
		return false;
	}

	public float fraction(StatusType status) {
		int i = status.meterIndex();
		return i < 0 || this.size[i] <= 0 ? 0 : Math.min(1, this.value[i] / this.size[i]);
	}

	/** Chill stacks: one per full third of the meter (0-2; a full meter is Frozen instead). */
	public int chillStacks() {
		return Math.min(2, (int) (this.fraction(StatusType.CHILL) * 3));
	}

	/** One tick of draining. Returns true if any meter changed. */
	public boolean tick(long now) {
		return this.tick(now, DRAIN_DELAY, DRAIN_PER_TICK);
	}

	/** One tick of draining with the tuning's delay and rate (a fraction of the meter's size a tick). */
	public boolean tick(long now, int drainDelay, float drainPerTick) {
		boolean changed = false;
		for (int i = 0; i < this.value.length; i++) {
			if (this.value[i] > 0 && now - this.lastBuild[i] >= drainDelay) {
				this.value[i] = Math.max(0, this.value[i] - this.size[i] * drainPerTick);
				changed = true;
			}
		}
		return changed;
	}

	public boolean empty() {
		for (float v : this.value) {
			if (v > 0) {
				return false;
			}
		}
		return true;
	}

	/** Meters as bytes 0-255 (the network form). */
	public byte[] bytes() {
		byte[] b = new byte[this.value.length];
		for (int i = 0; i < b.length; i++) {
			b[i] = (byte) Math.round(Math.min(1, this.size[i] <= 0 ? 0 : this.value[i] / this.size[i]) * 255);
		}
		return b;
	}
}
