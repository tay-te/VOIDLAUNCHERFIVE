package dev.voidmc.hearth.npc.service;

import net.minecraft.util.Mth;

/**
 * The geometry of noticing: is something inside a folk's field of view and range. Pure arithmetic (unit-tested); line of
 * sight, light, sneaking and armour are weighed elsewhere (line of sight by the entity's sensing, the rest by the law
 * module, docs/LIVING_WORLD.md §4).
 */
public final class Perception {
	private Perception() {
	}

	/**
	 * Whether a point offset {@code (dx, dy, dz)} from a folk's eyes lies within their sight.
	 *
	 * @param headYaw   the folk's head yaw in degrees (Minecraft's convention: 0 faces +z, 90 faces -x)
	 * @param pitch     the head pitch in degrees (positive looks down)
	 * @param range     how far they can see
	 * @param fov       the full width of their field of view in degrees (360 sees all round)
	 * @param nearSense closer than this they notice whatever the direction (a hand at the pocket, a step behind them)
	 */
	public static boolean inSight(float headYaw, float pitch, double dx, double dy, double dz, double range, double fov, double nearSense) {
		double d2 = dx * dx + dy * dy + dz * dz;
		if (d2 > range * range) {
			return false;
		}
		if (d2 <= nearSense * nearSense || fov >= 360.0) {
			return true;
		}
		double d = Math.sqrt(d2);
		// The direction they face, as a unit vector.
		float yawRad = headYaw * Mth.DEG_TO_RAD;
		float pitchRad = pitch * Mth.DEG_TO_RAD;
		double cosPitch = Math.cos(pitchRad);
		double lx = -Math.sin(yawRad) * cosPitch;
		double ly = -Math.sin(pitchRad);
		double lz = Math.cos(yawRad) * cosPitch;
		double cos = (lx * dx + ly * dy + lz * dz) / d;
		return cos >= Math.cos(Math.toRadians(fov / 2.0));
	}
}
