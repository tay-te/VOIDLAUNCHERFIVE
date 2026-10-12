package dev.voidmc.reel.shot;

import dev.voidmc.reel.math.Noise;

/**
 * Handheld drift laid over a move: amount is the largest yaw wander in degrees (pitch gets 70% of it, roll 40%),
 * speed roughly how many wanders a second. Around 0.3 at 0.25 reads as a person holding a camera very still;
 * past 1.5 it reads as running.
 */
public record Shake(double amount, double speed, long seed) {
	public CameraState apply(CameraState state, double t) {
		if (this.amount <= 0) {
			return state;
		}
		double x = t * this.speed;
		double yaw = new Noise(this.seed).at(x) * this.amount;
		double pitch = new Noise(this.seed + 1).at(x) * this.amount * 0.7;
		double roll = new Noise(this.seed + 2).at(x * 0.8) * this.amount * 0.4;
		return state.withAngles(state.yaw() + yaw, state.pitch() + pitch, state.roll() + roll);
	}
}
