package dev.voidmc.fray.motion.bake;

import dev.voidmc.fray.motion.Clip;
import dev.voidmc.fray.motion.HumanoidRig;
import dev.voidmc.fray.motion.PoseBuffer;

/**
 * First-person rules shared by the client's {@code FirstPersonBody} and the bake's framing check, so the check measures
 * what is drawn: the camera is the player's real eye, so the virtual torso (which would swing the arms through it) and
 * the root don't move the arms unless the clip's {@code fp} block animates the torso itself.
 */
public final class FirstPersonFrame {
	private FirstPersonFrame() {
	}

	public static void torso(PoseBuffer buf, Clip.FirstPerson fp) {
		boolean own = fp.rotation() != null && fp.rotation()[HumanoidRig.TORSO] != null;
		if (!own) {
			int o = HumanoidRig.TORSO * PoseBuffer.STRIDE;
			buf.v[o + PoseBuffer.W_ROT] = 0;
			buf.v[o + PoseBuffer.W_POS] = 0;
			for (int k = 0; k < 3; k++) {
				buf.v[o + PoseBuffer.ADD_ROT + k] = 0;
				buf.v[o + PoseBuffer.ADD_POS + k] = 0;
			}
		}
	}
}
