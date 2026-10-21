package dev.voidmc.fray.client.combat.hud;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * World → GUI coordinates through the main camera (position, yaw, pitch, the vertical FOV with its modifiers), for
 * HUD marks anchored on creatures (nameplates, the lock-on reticle, damage numbers). View bobbing is ignored.
 */
public final class Project {
	private static double px, py, pz, fx, fy, fz, rx, rz, ux, uy, uz, focal, halfW, halfH;

	private Project() {
	}

	/** Call once a frame before {@link #point}. */
	public static void setup(Minecraft mc, int guiW, int guiH) {
		Camera cam = mc.gameRenderer.mainCamera();
		Vec3 p = cam.position();
		px = p.x;
		py = p.y;
		pz = p.z;
		double yaw = Math.toRadians(cam.yRot());
		double pitch = Math.toRadians(cam.xRot());
		fx = -Math.sin(yaw) * Math.cos(pitch);
		fy = -Math.sin(pitch);
		fz = Math.cos(yaw) * Math.cos(pitch);
		rx = -Math.cos(yaw);
		rz = -Math.sin(yaw);
		// up = right × forward
		ux = 0 * fz - rz * fy;
		uy = rz * fx - rx * fz;
		uz = rx * fy - 0 * fx;
		halfW = guiW / 2.0;
		halfH = guiH / 2.0;
		focal = halfH / Math.tan(Math.toRadians(Math.max(10, cam.getFov())) / 2);
	}

	/** GUI x, y and depth, or null behind the camera. */
	public static float @Nullable [] point(double x, double y, double z) {
		double dx = x - px, dy = y - py, dz = z - pz;
		double depth = dx * fx + dy * fy + dz * fz;
		if (depth < 0.2) {
			return null;
		}
		double sx = dx * rx + dz * rz;
		double sy = dx * ux + dy * uy + dz * uz;
		return new float[] {(float) (halfW + sx / depth * focal), (float) (halfH - sy / depth * focal), (float) depth};
	}

	/** Yaw of a world point relative to the camera's look, degrees: 0 ahead, positive to the right. */
	public static float bearing(double x, double z) {
		double dx = x - px, dz = z - pz;
		double ahead = dx * fx + dz * fz;
		double side = dx * rx + dz * rz;
		return (float) Math.toDegrees(Math.atan2(side, ahead));
	}
}
