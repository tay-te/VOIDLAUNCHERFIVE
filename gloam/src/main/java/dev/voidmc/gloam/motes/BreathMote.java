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

	@Override
	protected void step(AirSample air) {
		Wind wind = air.wind();
		this.steer(windX(wind, 0.3), 0.008, windZ(wind, 0.3), 0.1);
		this.move(this.xd, this.yd, this.zd);
		this.quadSize += (this.swell - this.quadSize) * 0.09F;
		this.roll += 0.01F;
	}
}
