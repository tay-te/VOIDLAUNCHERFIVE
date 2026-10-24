package dev.voidmc.reel.shot;

import dev.voidmc.reel.math.Angles;
import dev.voidmc.reel.math.ArcLength;
import dev.voidmc.reel.math.Easing;
import dev.voidmc.reel.math.Monotone;
import dev.voidmc.reel.math.Pace;
import dev.voidmc.reel.math.Ramps;
import dev.voidmc.reel.math.Spline;
import dev.voidmc.reel.math.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * The camera on a curve through keyed positions (a dolly, crane, flyover or a locked-off frame with one key).
 *
 * <p>Timing is "even" unless every key has a time or the move is paced: the camera covers equal distance in equal
 * time, shaped by the ease, so the move glides however the keys are spaced. "keyed" passes each key at its time
 * instead. "paced" runs at the speeds keys ask for, ramps in and out as the shot says (or not at all, to cut in or out
 * moving) and holds at keys: see {@link Pace}. Everything else a key may set (angles, field of view, a point to look
 * at) is carried along the path by key, so a key's values hold exactly where the camera passes its position.
 *
 * <p>Where it looks, first match wins: keys with "look" points (the camera keeps them centred, a moving target);
 * the shot's one "look" point; "face": "path" (along the direction of travel); keyed yaw and pitch.
 */
public final class PathRig implements Rig {
	/** One key of the move: only pos is required. speed (blocks a second) and hold (seconds) are for paced moves. */
	public record Key(Vec3 pos, Double t, Double yaw, Double pitch, Double roll, Double fov, Vec3 look, Double speed, double hold) {
		public Key(Vec3 pos, Double t, Double yaw, Double pitch, Double roll, Double fov, Vec3 look) {
			this(pos, t, yaw, pitch, roll, fov, look, null, 0);
		}
	}

	public enum Timing { EVEN, KEYED, PACED }

	public enum Facing { KEYS, LOOK_POINT, PATH, KEYED_LOOK }

	private final List<Key> keys;
	private final double duration;
	private final Timing timing;
	private final Easing ease;
	private final Facing facing;
	private final Vec3 lookPoint;
	private final Spline spline;
	private final ArcLength arc;
	private final Monotone timeToKey;
	private final Pace pace;
	private final Monotone yaw;
	private final Monotone pitch;
	private final Monotone roll;
	private final Monotone fov;
	private final Monotone lookX;
	private final Monotone lookY;
	private final Monotone lookZ;

	public PathRig(List<Key> keys, double duration, Timing timing, Easing ease, Vec3 lookPoint, boolean facePath, double defaultFov) {
		this(keys, duration, timing, ease, null, lookPoint, facePath, defaultFov);
	}

	/**
	 * A paced move takes ramps and may leave duration null (it then comes from the keys' speeds); the other timings
	 * take an ease and a duration.
	 */
	public PathRig(List<Key> keys, Double duration, Timing timing, Easing ease, Ramps ramps, Vec3 lookPoint, boolean facePath, double defaultFov) {
		if (keys.isEmpty()) {
			throw new IllegalArgumentException("a path needs at least one key");
		}
		this.keys = List.copyOf(keys);
		this.timing = timing;
		this.ease = ease;
		this.lookPoint = lookPoint;
		this.spline = new Spline(keys.stream().map(Key::pos).toList());
		this.arc = new ArcLength(this.spline);

		if (timing == Timing.KEYED) {
			double[] times = new double[keys.size()];
			double[] index = new double[keys.size()];
			for (int i = 0; i < keys.size(); i++) {
				times[i] = keys.get(i).t();
				index[i] = i;
			}
			this.timeToKey = keys.size() > 1 ? new Monotone(times, index) : null;
		} else {
			this.timeToKey = null;
		}

		if (timing == Timing.PACED) {
			int n = keys.size();
			boolean still = this.arc.total() < 1e-6;
			double[] keyAt = new double[n];
			Double[] speeds = new Double[n];
			double[] holds = new double[n];
			for (int i = 0; i < n; i++) {
				keyAt[i] = still ? i : this.arc.distanceAt(i);
				speeds[i] = keys.get(i).speed();
				holds[i] = keys.get(i).hold();
				if (still && speeds[i] != null && n > 1) {
					throw new IllegalArgumentException("key " + i + " sets a speed, but the camera stays in one place: pace a pan with holds and ramps");
				}
			}
			this.pace = new Pace(keyAt, speeds, holds, ramps == null ? Ramps.DEFAULT : ramps, duration);
			this.duration = this.pace.duration();
		} else {
			if (duration == null) {
				throw new IllegalArgumentException("the shot needs a duration");
			}
			this.pace = null;
			this.duration = duration;
		}

		this.yaw = channel(Key::yaw, true);
		this.pitch = channel(Key::pitch, false);
		this.roll = channel(Key::roll, false);
		Monotone keyedFov = channel(Key::fov, false);
		this.fov = keyedFov != null ? keyedFov : new Monotone(new double[] {0}, new double[] {defaultFov});
		this.lookX = channel(k -> k.look() == null ? null : k.look().x(), false);
		this.lookY = channel(k -> k.look() == null ? null : k.look().y(), false);
		this.lookZ = channel(k -> k.look() == null ? null : k.look().z(), false);

		if (this.lookX != null) {
			this.facing = Facing.KEYED_LOOK;
		} else if (lookPoint != null) {
			this.facing = Facing.LOOK_POINT;
		} else if (facePath) {
			this.facing = Facing.PATH;
		} else if (this.yaw != null) {
			this.facing = Facing.KEYS;
		} else {
			throw new IllegalArgumentException("the camera has nowhere to look: give keys a yaw and pitch or a look point, "
				+ "or the camera a look point or \"face\": \"path\"");
		}
	}

	/** A curve over key index through the keys that set this value; null if none do. */
	private Monotone channel(Function<Key, Double> value, boolean angle) {
		List<Double> xs = new ArrayList<>();
		List<Double> ys = new ArrayList<>();
		for (int i = 0; i < this.keys.size(); i++) {
			Double v = value.apply(this.keys.get(i));
			if (v != null) {
				xs.add((double) i);
				ys.add(v);
			}
		}
		if (xs.isEmpty()) {
			return null;
		}
		double[] y = ys.stream().mapToDouble(Double::doubleValue).toArray();
		return new Monotone(xs.stream().mapToDouble(Double::doubleValue).toArray(), angle ? Angles.unwrap(y) : y);
	}

	/** The spline parameter (key index, fractional) the camera has reached at time t. */
	public double keyAt(double t) {
		int last = this.keys.size() - 1;
		if (last == 0) {
			return 0;
		}
		if (this.timing == Timing.KEYED) {
			return this.timeToKey.at(t);
		}
		if (this.timing == Timing.PACED) {
			double s = this.pace.distanceAt(t);
			return this.arc.total() < 1e-6 ? s : this.arc.parameterAt(s);
		}
		double progress = this.ease.apply(this.duration <= 0 ? 1 : t / this.duration);
		if (this.arc.total() < 1e-6) {
			return progress * last;   // keys at one spot: a locked-off camera that pans or zooms through them
		}
		return this.arc.parameterAt(progress * this.arc.total());
	}

	@Override
	public CameraState at(double t) {
		double u = this.keyAt(t);
		Vec3 pos = this.spline.at(u);
		double y;
		double p;
		switch (this.facing) {
			case KEYED_LOOK -> {
				Vec3 target = new Vec3(this.lookX.at(u), this.lookY.at(u), this.lookZ.at(u));
				y = Angles.yawTo(pos, target);
				p = Angles.pitchTo(pos, target);
			}
			case LOOK_POINT -> {
				y = Angles.yawTo(pos, this.lookPoint);
				p = Angles.pitchTo(pos, this.lookPoint);
			}
			case PATH -> {
				int last = this.keys.size() - 1;
				double du = 0.01;
				Vec3 ahead = this.spline.at(Math.min(last, u + du));
				Vec3 behind = this.spline.at(Math.max(0, u - du));
				y = Angles.yawTo(behind, ahead);
				p = this.pitch != null ? this.pitch.at(u) : Angles.pitchTo(behind, ahead);
			}
			default -> {
				y = this.yaw.at(u);
				p = this.pitch != null ? this.pitch.at(u) : 0;
			}
		}
		double r = this.roll != null ? this.roll.at(u) : 0;
		return new CameraState(pos, y, p, r, this.fov.at(u));
	}

	public Spline spline() {
		return this.spline;
	}

	public double length() {
		return this.arc.total();
	}

	public double duration() {
		return this.duration;
	}

	@Override
	public String describe() {
		String pacing = this.timing == Timing.PACED ? this.pace.describe()
			: this.timing.name().toLowerCase(Locale.ROOT) + " timing, " + this.ease.name().toLowerCase(Locale.ROOT);
		return String.format(Locale.ROOT, "path: %d keys, %.1f blocks, %s, facing %s", this.keys.size(), this.arc.total(), pacing,
			this.facing.name().toLowerCase(Locale.ROOT));
	}
}
