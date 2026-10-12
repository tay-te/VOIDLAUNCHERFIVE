package dev.voidmc.reel.math;

import java.util.Locale;

/** A point or direction in world space, in blocks. Reel's own, so the shot code runs without the game. */
public record Vec3(double x, double y, double z) {
	public static final Vec3 ZERO = new Vec3(0, 0, 0);

	public Vec3 add(Vec3 o) {
		return new Vec3(this.x + o.x, this.y + o.y, this.z + o.z);
	}

	public Vec3 sub(Vec3 o) {
		return new Vec3(this.x - o.x, this.y - o.y, this.z - o.z);
	}

	public Vec3 scale(double k) {
		return new Vec3(this.x * k, this.y * k, this.z * k);
	}

	public double length() {
		return Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
	}

	public double distance(Vec3 o) {
		return this.sub(o).length();
	}

	public Vec3 lerp(Vec3 o, double t) {
		return new Vec3(this.x + (o.x - this.x) * t, this.y + (o.y - this.y) * t, this.z + (o.z - this.z) * t);
	}

	@Override
	public String toString() {
		return String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)", this.x, this.y, this.z);
	}
}
