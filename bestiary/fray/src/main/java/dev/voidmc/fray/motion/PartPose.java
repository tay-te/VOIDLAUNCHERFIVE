package dev.voidmc.fray.motion;

/**
 * A mutable copy of a {@code ModelPart}'s pose fields (position in model pixels, rotation in radians, scale), so the
 * pose maths can run on the server, where {@code ModelPart} doesn't exist.
 */
public final class PartPose {
	public float x, y, z;
	public float xRot, yRot, zRot;
	public float xScale = 1, yScale = 1, zScale = 1;

	public PartPose setPos(float x, float y, float z) {
		this.x = x;
		this.y = y;
		this.z = z;
		return this;
	}

	public PartPose set(PartPose o) {
		this.x = o.x;
		this.y = o.y;
		this.z = o.z;
		this.xRot = o.xRot;
		this.yRot = o.yRot;
		this.zRot = o.zRot;
		this.xScale = o.xScale;
		this.yScale = o.yScale;
		this.zScale = o.zScale;
		return this;
	}

	@Override
	public String toString() {
		return String.format(java.util.Locale.ROOT, "pos(%.2f %.2f %.2f) rot(%.1f %.1f %.1f)", this.x, this.y, this.z, Math.toDegrees(this.xRot),
			Math.toDegrees(this.yRot), Math.toDegrees(this.zRot));
	}
}
