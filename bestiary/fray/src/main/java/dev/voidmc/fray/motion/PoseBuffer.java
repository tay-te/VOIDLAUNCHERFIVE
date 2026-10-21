package dev.voidmc.fray.motion;

import java.util.Arrays;

/**
 * A sampled pose for one rig: per bone, the override layers' offset from the rest pose (rotation in radians, position
 * in model pixels with vanilla's Y-down, scale as an offset from 1) with a weight per channel, and the additive
 * layers' summed offsets. Applying it sets each part to {@code lerp(under, rest + offset, weight) + additive}, where
 * "under" is whatever the model's own {@code setupAnim} produced (vanilla rigs) or the rest pose (custom rigs)
 * (ANIMATION.md §7.2).
 *
 * <p>Several override layers fold into one buffer ({@link #combine}): with weights w1 then w2 the result is
 * {@code lerp(lerp(under, rest+o1, w1), rest+o2, w2)}, stored as one offset and one weight.
 */
public final class PoseBuffer {
	public static final int STRIDE = 18;
	public static final int ROT = 0, POS = 3, SCALE = 6, W_ROT = 9, W_POS = 10, W_SCALE = 11, ADD_ROT = 12, ADD_POS = 15;

	public final float[] v;
	public final int bones;
	/** The rig this buffer was sampled for (a model of another rig ignores it). */
	public Rig rig;
	/** True when some channel carries weight; a buffer that isn't active is never put on a render state. */
	public boolean active;

	public PoseBuffer(Rig rig) {
		this.rig = rig;
		this.bones = rig.bones();
		this.v = new float[this.bones * STRIDE];
	}

	/** A humanoid buffer (tests, the prototype's call sites). */
	public PoseBuffer() {
		this(Rig.HUMANOID);
	}

	public void clear() {
		Arrays.fill(this.v, 0);
		this.active = false;
	}

	public float weight(int bone, int channelWeight) {
		return this.v[bone * STRIDE + channelWeight];
	}

	/** Folds three values {@code o} with weight {@code w} over what the buffer holds for one bone's channel. */
	public void combine(int bone, int channel, int weightSlot, float ox, float oy, float oz, float w) {
		if (w <= 0) {
			return;
		}
		int base = bone * STRIDE;
		float W = this.v[base + weightSlot];
		float nW = 1 - (1 - W) * (1 - w);
		if (nW <= 1e-6F) {
			return;
		}
		float keep = W * (1 - w) / nW;
		float take = w / nW;
		int c = base + channel;
		this.v[c] = this.v[c] * keep + ox * take;
		this.v[c + 1] = this.v[c + 1] * keep + oy * take;
		this.v[c + 2] = this.v[c + 2] * keep + oz * take;
		this.v[base + weightSlot] = nW;
		this.active = true;
	}

	/** Adds {@code o × w} to a bone's additive rotation or position ({@link #ADD_ROT}, {@link #ADD_POS}). */
	public void add(int bone, int slot, float ox, float oy, float oz, float w) {
		if (w <= 0) {
			return;
		}
		int c = bone * STRIDE + slot;
		this.v[c] += ox * w;
		this.v[c + 1] += oy * w;
		this.v[c + 2] += oz * w;
		this.active = true;
	}

	public void copyFrom(PoseBuffer o) {
		if (o.v.length != this.v.length) {
			throw new IllegalArgumentException("pose buffers of different rigs");
		}
		System.arraycopy(o.v, 0, this.v, 0, this.v.length);
		this.active = o.active;
		this.rig = o.rig;
	}
}
