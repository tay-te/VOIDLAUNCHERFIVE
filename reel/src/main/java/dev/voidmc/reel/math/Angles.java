package dev.voidmc.reel.math;

/**
 * Minecraft's view angles, in degrees: yaw 0 faces south (+z) and turns clockwise seen from above (90 faces west,
 * -x); pitch is positive looking down.
 */
public final class Angles {
	private Angles() {
	}

	public static double wrap(double degrees) {
		double d = degrees % 360;
		if (d >= 180) {
			d -= 360;
		} else if (d < -180) {
			d += 360;
		}
		return d;
	}

	/** Rewrites a run of yaws so each differs from the one before by at most 180: the camera turns the short way. */
	public static double[] unwrap(double[] yaws) {
		double[] out = yaws.clone();
		for (int i = 1; i < out.length; i++) {
			out[i] = out[i - 1] + wrap(out[i] - out[i - 1]);
		}
		return out;
	}

	public static double yawTo(Vec3 from, Vec3 to) {
		Vec3 d = to.sub(from);
		return Math.toDegrees(Math.atan2(-d.x(), d.z()));
	}

	public static double pitchTo(Vec3 from, Vec3 to) {
		Vec3 d = to.sub(from);
		return Math.toDegrees(-Math.atan2(d.y(), Math.sqrt(d.x() * d.x() + d.z() * d.z())));
	}

	public static Vec3 direction(double yaw, double pitch) {
		double y = Math.toRadians(yaw);
		double p = Math.toRadians(pitch);
		return new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
	}

	/** The vertical field of view of a lens of the given focal length on a full-frame (36 x 24 mm) sensor. */
	public static double lensToFov(double millimetres) {
		return Math.toDegrees(2 * Math.atan(12 / millimetres));
	}
}
