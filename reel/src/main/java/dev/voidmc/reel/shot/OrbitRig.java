package dev.voidmc.reel.shot;

import dev.voidmc.reel.math.Angles;
import dev.voidmc.reel.math.Easing;
import dev.voidmc.reel.math.Ramps;
import dev.voidmc.reel.math.Vec3;
import java.util.Locale;

/**
 * The camera circling a point, always facing it: a reveal around a tower, a slow turn about a village. "from" is the
 * compass direction the camera faces at the start, as a yaw (0 south, 90 west, 180 north, -90 east), and "sweep"
 * how far it turns, positive clockwise seen from above. Radius, height (above the centre) and the height of the
 * aim point may each be one number or [start, end], eased with the turn, so an orbit can also close in or rise.
 * With ramps in place of an ease, the turn runs at a steady rate between them, and a ramp of 0 starts or ends the
 * shot mid-turn.
 */
public final class OrbitRig implements Rig {
	public record Range(double from, double to) {
		public double at(double k) {
			return this.from + (this.to - this.from) * k;
		}

		public static Range of(double v) {
			return new Range(v, v);
		}
	}

	private final Vec3 center;
	private final Range radius;
	private final Range height;
	private final Range aim;
	private final double from;
	private final double sweep;
	private final double duration;
	private final Easing ease;
	private final Ramps ramps;
	private final double fov;

	public OrbitRig(Vec3 center, Range radius, Range height, Range aim, double from, double sweep, double duration, Easing ease, double fov) {
		this(center, radius, height, aim, from, sweep, duration, ease, null, fov);
	}

	/** ramps, when given, replace the ease. */
	public OrbitRig(Vec3 center, Range radius, Range height, Range aim, double from, double sweep, double duration, Easing ease, Ramps ramps,
		double fov) {
		if (ramps != null && ramps.in() + ramps.out() > duration + 1e-9) {
			throw new IllegalArgumentException(String.format(Locale.ROOT, "ramps of %.2f s and %.2f s are longer than the %.2f s shot", ramps.in(),
				ramps.out(), duration));
		}
		this.center = center;
		this.radius = radius;
		this.height = height;
		this.aim = aim;
		this.from = from;
		this.sweep = sweep;
		this.duration = duration;
		this.ease = ease;
		this.ramps = ramps;
		this.fov = fov;
	}

	@Override
	public CameraState at(double t) {
		double k = this.progress(t);
		double facing = this.from + this.sweep * k;
		Vec3 back = Angles.direction(facing, 0).scale(-this.radius.at(k));
		Vec3 pos = this.center.add(back).add(new Vec3(0, this.height.at(k), 0));
		Vec3 target = this.center.add(new Vec3(0, this.aim.at(k), 0));
		return new CameraState(pos, Angles.yawTo(pos, target), Angles.pitchTo(pos, target), 0, this.fov);
	}

	/** How far through the turn the camera is at time t, from 0 to 1. */
	private double progress(double t) {
		if (this.duration <= 0) {
			return 1;
		}
		if (this.ramps == null) {
			return this.ease.apply(t / this.duration);
		}
		double whole = Ramps.run(this.duration, this.duration, this.ramps.in(), this.ramps.out());
		return whole <= 0 ? 1 : Ramps.run(t, this.duration, this.ramps.in(), this.ramps.out()) / whole;
	}

	@Override
	public String describe() {
		String pacing = this.ramps == null ? this.ease.name().toLowerCase(Locale.ROOT)
			: String.format(Locale.ROOT, "ramps in %.1fs out %.1fs", this.ramps.in(), this.ramps.out());
		return String.format(Locale.ROOT, "orbit: around %s, radius %.0f-%.0f, %.0f deg from %.0f, %s", this.center, this.radius.from(),
			this.radius.to(), this.sweep, this.from, pacing);
	}
}
