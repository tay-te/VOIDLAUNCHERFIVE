package dev.voidmc.fray.client.combat.feel;

import dev.voidmc.fray.client.combat.FrayClientOptions;
import dev.voidmc.fray.combat.math.Feel;
import net.minecraft.client.Minecraft;

/**
 * Camera shake (COMBAT.md §5): up to eight decaying impulses, each pushing the view along the hit's direction with a
 * smaller wobble across it, summed and capped at {@link Feel#MAX_SHAKE} degrees. Scaled by the player's intensity
 * option (0-100 %, default 60 %; off switch) and by vanilla's accessibility "Damage Tilt" slider (at 0 there is no
 * shake). Turns the camera only, never the player's look.
 */
public final class CameraShake {
	private static final int MAX = 8;
	private static final long[] START = new long[MAX];
	private static final float[] AMP = new float[MAX], TAU = new float[MAX], UX = new float[MAX], UY = new float[MAX], PHASE = new float[MAX];
	private static int count;
	private static final float[] OUT = new float[2];

	/** The last frame's offset as the camera took it (yaw, pitch in degrees; tests). */
	public static float lastYaw, lastPitch;
	/** Cost (ns) and frames (perf). */
	public static long nanos, frames;
	/** Impulses accepted (tests). */
	public static int added;

	private CameraShake() {
	}

	/** The player's shake scale: option × intensity × vanilla's Damage Tilt (accessibility); 0 when off. */
	public static float scale() {
		FrayClientOptions o = FrayClientOptions.get();
		if (!o.cameraShake) {
			return 0;
		}
		Minecraft mc = Minecraft.getInstance();
		double tilt = mc.options == null ? 1 : mc.options.damageTiltStrength().get();
		return (float) (Math.max(0, Math.min(1, o.shakeIntensity)) * Math.max(0, Math.min(1, tilt)));
	}

	/** Adds an impulse of {@code degrees} peak (before the player's scale), {@code weight} 0..1, along (ux, uy). */
	public static void add(float degrees, float weight, float ux, float uy) {
		float amp = degrees * scale();
		if (amp < 0.01F) {
			return;
		}
		long now = System.nanoTime();
		int slot = -1;
		for (int i = 0; i < count; i++) {
			if ((now - START[i]) / 1e9F > 4 * TAU[i]) {
				slot = i;
				break;
			}
		}
		if (slot < 0) {
			if (count < MAX) {
				slot = count++;
			} else {
				// Full: the weakest gives way.
				slot = 0;
				for (int i = 1; i < MAX; i++) {
					if (AMP[i] < AMP[slot]) {
						slot = i;
					}
				}
			}
		}
		START[slot] = now;
		AMP[slot] = amp;
		TAU[slot] = Feel.tau(weight);
		UX[slot] = ux;
		UY[slot] = uy;
		PHASE[slot] = (float) (now % 6283 / 1000.0);
		added++;
	}

	/** This frame's offset (yaw, pitch degrees), or null when still. Called once a frame by the camera. */
	public static float[] frame() {
		frames++;
		if (count == 0) {
			lastYaw = lastPitch = 0;
			return null;
		}
		long t0 = System.nanoTime();
		float yaw = 0, pitch = 0;
		int live = 0;
		for (int i = 0; i < count; i++) {
			float t = (t0 - START[i]) / 1e9F;
			if (t > 4 * TAU[i]) {
				continue;
			}
			live++;
			float a = Feel.along(t, TAU[i]) * AMP[i];
			float c = Feel.across(t, TAU[i], PHASE[i]) * AMP[i];
			yaw += UX[i] * a - UY[i] * c;
			pitch += UY[i] * a + UX[i] * c;
		}
		if (live == 0) {
			count = 0;
		}
		OUT[0] = yaw;
		OUT[1] = pitch;
		Feel.clamp(OUT);
		nanos += System.nanoTime() - t0;
		if (live == 0) {
			lastYaw = lastPitch = 0;
			return null;
		}
		return OUT;
	}

	/** What the camera actually turned by this frame. */
	public static void applied(float yaw, float pitch) {
		lastYaw = yaw;
		lastPitch = pitch;
	}

	public static boolean active() {
		return count > 0;
	}

	public static void clear() {
		count = 0;
		lastYaw = lastPitch = 0;
	}
}
