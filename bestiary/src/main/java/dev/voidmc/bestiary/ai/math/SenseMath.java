package dev.voidmc.bestiary.ai.math;

/**
 * Perception maths (BESTIARY.md §3): sight range by light, night, sneaking and invisibility; the sight cone; close
 * sense; hearing by a noise's radius. Pure maths (JUnit: {@code AiMathTest}).
 */
public final class SenseMath {
	/** Anything within this many blocks is noticed whatever the cone. */
	public static final float CLOSE_SENSE = 3;

	private SenseMath() {
	}

	/**
	 * How far a target can be seen: the profile's range (the night range at night unless it sees in the dark), half in
	 * light 4 or less (unless it sees in the dark), ×0.6 sneaking, ×0.1 invisible, × any stealth factor.
	 */
	public static float sightRange(float range, float nightRange, boolean night, boolean seesInDark, int light, boolean sneaking, boolean invisible,
		float stealth) {
		float r = night && !seesInDark ? Math.min(range, nightRange) : range;
		if (light <= 4 && !seesInDark) {
			r *= 0.5F;
		}
		if (sneaking) {
			r *= 0.6F;
		}
		if (invisible) {
			r *= 0.1F;
		}
		return r * Math.max(0, stealth);
	}

	/** True when (dx, dz) from the observer lies within {@code fov} degrees centred on {@code yawDeg} (Minecraft's yaw). */
	public static boolean inCone(float yawDeg, double dx, double dz, float fov) {
		if (fov >= 360) {
			return true;
		}
		double to = Math.toDegrees(Math.atan2(dz, dx)) - 90;
		double off = wrap(to - yawDeg);
		return Math.abs(off) <= fov / 2;
	}

	public static double wrap(double deg) {
		double d = deg % 360;
		if (d >= 180) {
			d -= 360;
		}
		if (d < -180) {
			d += 360;
		}
		return d;
	}

	/** Whether a noise of {@code radius} at {@code distance} is heard by an observer with {@code hearing} (1 normal). */
	public static boolean hears(float radius, double distance, float hearing) {
		return distance <= radius * Math.max(0, hearing);
	}

	/** Footstep noise radius: sprinting 10, walking 4, sneaking 1, still 0 (× Burdened and the like). */
	public static float footsteps(boolean moving, boolean sprinting, boolean sneaking, float factor) {
		if (!moving) {
			return 0;
		}
		float r = sneaking ? 1 : sprinting ? 10 : 4;
		return r * Math.max(0, factor);
	}
}
