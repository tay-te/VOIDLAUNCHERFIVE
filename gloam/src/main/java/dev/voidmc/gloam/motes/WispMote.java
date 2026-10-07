package dev.voidmc.gloam.motes;

import dev.voidmc.gloam.air.AirSample;
import dev.voidmc.gloam.air.Wind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * A veil of ground mist: a wide, very faint sheet lying a little above the ground or water, creeping with a fraction
 * of the wind, spreading slowly, fading in and out over many seconds so the mist seems to gather and thin rather than
 * puff. It thins as the camera comes close, so a veil never hangs in front of the eyes as a flat card, and goes when
 * the mist lifts or it drifts above the mist's top. Drawn through {@link MoteLayers#MIST}, which keeps its faintest
 * edges.
 */
final class WispMote extends Mote {
	private final float carry;

	/** @param strength how opaque its thickest part is, 0..1 (a few tenths: many veils overlap) */
	WispMote(ClientLevel level, double x, double y, double z, RandomSource random, float strength) {
		super(level, x, y, z, Motes.Kind.WISP, random);
		this.quadSize = 2.4F + this.random.nextFloat() * 2.6F;
		this.lifetime = 500 + this.random.nextInt(500);
		this.fadeIn = 90;
		this.fadeOut = 120;
		this.peak = strength;
		this.carry = 0.15F + this.random.nextFloat() * 0.12F;
		this.misty = 0.97F + this.random.nextFloat() * 0.06F;
	}

	@Override
	protected void step(AirSample air) {
		Wind wind = air.wind();
		double vy = 0.0015 * Math.sin(this.age * 0.012 + this.phase);
		this.steer(windX(wind, this.carry), vy, windZ(wind, this.carry), 0.01);
		this.move(this.xd, this.yd, this.zd);
		this.quadSize *= 1.0003F;
		if (air.mist() < 0.03 || this.y > air.mistTop() + 1) {
			this.endWithin(this.fadeOut);
		}
	}

	@Override
	protected float fade() {
		// above the eye counts double, so no veil hangs overhead like a cloud: mist lies around and below the camera
		Vec3 eye = Motes.camera();
		double dy = this.y - eye.y;
		double d = Math.sqrt(Mth.square(this.x - eye.x) + Mth.square(this.z - eye.z) + dy * dy * (dy > 0 ? 4 : 1));
		float near = (float) Math.clamp((d - this.quadSize * 0.5) / (this.quadSize * 1.2), 0, 1);
		return super.fade() * near * near * (3 - 2 * near);
	}
}
