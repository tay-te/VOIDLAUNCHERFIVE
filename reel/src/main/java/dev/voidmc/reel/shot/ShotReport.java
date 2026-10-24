package dev.voidmc.reel.shot;

import dev.voidmc.reel.math.Angles;
import dev.voidmc.reel.math.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A shot's numbers and a camera operator's warnings, worked out without the game: how far and how fast the camera
 * travels, how fast it turns (in frame widths a second, which is what decides whether a pan strobes), and whether
 * any of it lurches. Read it before filming; it is what /check and pathPreview print.
 *
 * <p>It also gives the speed and turn the camera enters and leaves with: two shots cut together read as one move
 * when the second enters at about the speed the first leaves at.
 */
public record ShotReport(Shot shot, double length, double meanSpeed, double maxSpeed, double maxSpeedAt, double maxTurn, double maxTurnAt,
	double maxPan, double maxRoll, double maxZoom, double maxAccel, double maxAccelAt, double entrySpeed, double entryTurn, double exitSpeed,
	double exitTurn, List<String> warnings) {

	/** A pan faster than this many frame widths a second strobes visibly at 60 fps without motion blur. */
	static final double JUDDER_PAN = 1.0 / 3;
	static final double WHIP_PAN = 1.0;
	static final double LURCH = 40;

	public static ShotReport of(Shot shot) {
		int steps = Math.max(2, (int) Math.ceil(shot.duration() * shot.fps() * 4));
		double dt = shot.duration() / steps;
		double length = 0;
		double maxSpeed = 0;
		double maxSpeedAt = 0;
		double maxTurn = 0;
		double maxTurnAt = 0;
		double maxPan = 0;
		double maxRoll = 0;
		double maxZoom = 0;
		double maxAccel = 0;
		double maxAccelAt = 0;
		CameraState last = shot.camera(0);
		double lastSpeed = 0;
		double entrySpeed = 0;
		double entryTurn = 0;
		double exitSpeed = 0;
		double exitTurn = 0;
		for (int i = 1; i <= steps; i++) {
			double t = i * dt;
			CameraState now = shot.camera(t);
			double d = now.pos().distance(last.pos());
			length += d;
			double speed = d / dt;
			if (speed > maxSpeed) {
				maxSpeed = speed;
				maxSpeedAt = t;
			}
			if (i > 1) {
				double accel = Math.abs(speed - lastSpeed) / dt;
				if (accel > maxAccel) {
					maxAccel = accel;
					maxAccelAt = t;
				}
			}
			lastSpeed = speed;
			Vec3 a = Angles.direction(last.yaw(), last.pitch());
			Vec3 b = Angles.direction(now.yaw(), now.pitch());
			double dot = Math.clamp(a.x() * b.x() + a.y() * b.y() + a.z() * b.z(), -1, 1);
			double turn = Math.toDegrees(Math.acos(dot)) / dt;
			if (turn > maxTurn) {
				maxTurn = turn;
				maxTurnAt = t;
			}
			if (i == 1) {
				entrySpeed = speed;
				entryTurn = turn;
			}
			exitSpeed = speed;
			exitTurn = turn;
			double hfov = Math.toDegrees(2 * Math.atan(Math.tan(Math.toRadians(now.fov()) / 2) * shot.aspect()));
			maxPan = Math.max(maxPan, turn / hfov);
			maxRoll = Math.max(maxRoll, Math.abs(now.roll() - last.roll()) / dt);
			maxZoom = Math.max(maxZoom, Math.abs(now.fov() - last.fov()) / dt);
			last = now;
		}

		List<String> warnings = new ArrayList<>();
		boolean blurred = shot.render().samples() > 1;
		if (maxPan > WHIP_PAN) {
			warnings.add(String.format(Locale.ROOT, "whip pan: %.2f frame widths/s around %.1fs; fine if meant, otherwise slow the turn", maxPan, maxTurnAt));
		} else if (maxPan > JUDDER_PAN && !blurred) {
			warnings.add(String.format(Locale.ROOT, "pan of %.2f frame widths/s around %.1fs will judder without motion blur: slow it, or set render.samples to 8",
				maxPan, maxTurnAt));
		}
		if (maxAccel > LURCH) {
			warnings.add(String.format(Locale.ROOT, "lurch: speed changes by %.0f blocks/s/s around %.1fs; space the keys more evenly or use even timing",
				maxAccel, maxAccelAt));
		}
		if (maxZoom > 25) {
			warnings.add(String.format(Locale.ROOT, "fast zoom: %.0f degrees/s", maxZoom));
		}
		if (shot.duration() < 2) {
			warnings.add("under two seconds: very short for a cinematic shot");
		}
		return new ShotReport(shot, length, length / shot.duration(), maxSpeed, maxSpeedAt, maxTurn, maxTurnAt, maxPan, maxRoll, maxZoom, maxAccel,
			maxAccelAt, entrySpeed, entryTurn, exitSpeed, exitTurn, List.copyOf(warnings));
	}

	public String text() {
		StringBuilder b = new StringBuilder();
		b.append(String.format(Locale.ROOT, "%s: %.1fs at %d fps (%d frames), %dx%d%n", this.shot.name(), this.shot.duration(), this.shot.fps(),
			this.shot.frames(), this.shot.width(), this.shot.height()));
		b.append("  ").append(this.shot.rig().describe()).append('\n');
		b.append(String.format(Locale.ROOT, "  travel %.1f blocks, mean %.1f b/s, top %.1f b/s at %.1fs%n", this.length, this.meanSpeed, this.maxSpeed,
			this.maxSpeedAt));
		b.append(String.format(Locale.ROOT, "  turn up to %.1f deg/s at %.1fs (%.2f frame widths/s), roll %.1f deg/s, zoom %.1f deg/s%n", this.maxTurn,
			this.maxTurnAt, this.maxPan, this.maxRoll, this.maxZoom));
		b.append(String.format(Locale.ROOT, "  enters at %.1f b/s turning %.1f deg/s, leaves at %.1f b/s turning %.1f deg/s%n", this.entrySpeed,
			this.entryTurn, this.exitSpeed, this.exitTurn));
		CameraState first = this.shot.camera(0);
		CameraState end = this.shot.camera(this.shot.duration());
		b.append(String.format(Locale.ROOT, "  from %s yaw %.1f pitch %.1f fov %.1f%n", first.pos(), first.yaw(), first.pitch(), first.fov()));
		b.append(String.format(Locale.ROOT, "  to   %s yaw %.1f pitch %.1f fov %.1f%n", end.pos(), end.yaw(), end.pitch(), end.fov()));
		if (this.warnings.isEmpty()) {
			b.append("  no warnings\n");
		}
		for (String w : this.warnings) {
			b.append("  ! ").append(w).append('\n');
		}
		return b.toString();
	}
}
