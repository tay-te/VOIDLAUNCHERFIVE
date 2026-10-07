package dev.voidmc.gloam.motes;

import dev.voidmc.gloam.air.AirSample;
import dev.voidmc.gloam.air.Wind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;

/**
 * A breath in the cold: a small puff pushed out of the mouth that slows at once, swells, rises a little, is taken by
 * the wind and is gone in under two seconds.
 */
final class BreathMote extends Mote {
	private final float swell;

	BreathMote(ClientLevel level, double x, double y, double z, RandomSource random, double xd, double yd, double zd) {
		super(level, x, y, z, Motes.Kind.BREATH, random);
		this.quadSize = 0.03F + this.random.nextFloat() * 0.02F;
		this.swell = 0.10F + this.random.nextFloat() * 0.05F;
		this.lifetime = 26 + this.random.nextInt(14);
		this.fadeIn = 3;
		this.fadeOut = 22;
		this.peak = 0.5F + this.random.nextFloat() * 0.15F;
		this.xd = xd;
		this.yd = yd;
		this.zd = zd;
		this.roll = this.random.nextFloat() * (float) (Math.PI * 2);
		this.oRoll = this.roll;
		// breath is lit vapour, whiter than the air around it
		this.misty = 1.3F;
	}

	/** Thins the puff for air only just cold enough to show breath ({@code cold} 0..1). */
	void thin(float cold) {
		this.peak *= 0.45F + 0.55F * cold;
	}

	/**
	 * A breath close to the camera (one's own) fills much of the view, so it thins the nearer it is, and never hangs in
	 * front of the eye as a white blob. Under a shader pack the soft motes get more body (the pack lights them dimly),
	 * but in full daylight the pack lights a breath white: there it stays a faint wisp.
	 */
	@Override
	protected float finish(float a) {
		float near = (float) Math.clamp((Math.sqrt(this.toCamera()) - 0.4) / 1.2, 0, 1);
		if (!Motes.pack()) {
			return a * (0.45F + 0.55F * near);
		}
		return Math.min(a, 0.3F) * (0.35F + 0.65F * near);
	}

	@Override
	protected void step(AirSample air) {
		Wind wind = air.wind();
		this.steer(windX(wind, 0.3), 0.008, windZ(wind, 0.3), 0.1);
		this.move(this.xd, this.yd, this.zd);
		this.quadSize += (this.swell - this.quadSize) * 0.09F;
		this.roll += 0.01F;
	}
}
