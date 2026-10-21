package dev.voidmc.fray.motion;

/**
 * Samples a clip into a {@link PoseBuffer}, converting the file's units to model units on the way: degrees to radians
 * ({@code degreeVec}), pixel positions with Y up to model space ({@code posVec}: Y negated), scale factors to offsets
 * ({@code scaleVec}: minus one). The same method serves the client every frame and the bake every tick.
 */
public final class ClipSampler {
	private static final float DEG = (float) (Math.PI / 180.0);

	private ClipSampler() {
	}

	/**
	 * Folds the clip at {@code t} seconds (already wrapped by the clip's loop mode) with {@code weight} over the buffer:
	 * override clips by {@link PoseBuffer#combine}, additive ones by {@link PoseBuffer#add}. {@code mask} (per bone, may be
	 * null for the clip's own) scales each bone. {@code scratch} is a caller-owned float[3], so sampling allocates
	 * nothing.
	 */
	public static void accumulate(Clip clip, float t, float weight, float[] mask, PoseBuffer out, float[] scratch) {
		if (weight <= 0) {
			return;
		}
		boolean additive = clip.blend == Clip.Blend.ADDITIVE;
		int n = Math.min(clip.bones(), out.bones);
		for (int b = 0; b < n; b++) {
			float w = weight * (mask == null ? clip.mask[b] : mask[b]);
			if (w <= 0) {
				continue;
			}
			Channel r = clip.rotation[b];
			if (r != null) {
				r.sample(t, scratch, 0);
				if (additive) {
					out.add(b, PoseBuffer.ADD_ROT, scratch[0] * DEG, scratch[1] * DEG, scratch[2] * DEG, w);
				} else {
					out.combine(b, PoseBuffer.ROT, PoseBuffer.W_ROT, scratch[0] * DEG, scratch[1] * DEG, scratch[2] * DEG, w);
				}
			}
			Channel p = clip.position[b];
			if (p != null) {
				p.sample(t, scratch, 0);
				if (additive) {
					out.add(b, PoseBuffer.ADD_POS, scratch[0], -scratch[1], scratch[2], w);
				} else {
					out.combine(b, PoseBuffer.POS, PoseBuffer.W_POS, scratch[0], -scratch[1], scratch[2], w);
				}
			}
			Channel s = clip.scale[b];
			if (s != null && !additive) {
				s.sample(t, scratch, 0);
				out.combine(b, PoseBuffer.SCALE, PoseBuffer.W_SCALE, scratch[0] - 1, scratch[1] - 1, scratch[2] - 1, w);
			}
		}
	}

	public static void accumulate(Clip clip, float t, float weight, PoseBuffer out, float[] scratch) {
		accumulate(clip, t, weight, null, out, scratch);
	}
}
