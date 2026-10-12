package dev.voidmc.reel.shot;

import dev.voidmc.reel.math.Vec3;

/** Where the camera is and how it looks at one instant: angles in degrees, fov the vertical field of view. */
public record CameraState(Vec3 pos, double yaw, double pitch, double roll, double fov) {
	public CameraState withAngles(double yaw, double pitch, double roll) {
		return new CameraState(this.pos, yaw, pitch, roll, this.fov);
	}
}
