package dev.voidmc.gloam.motes;

import dev.voidmc.gloam.air.AirSample;
import dev.voidmc.gloam.air.Wind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;

/**
 * Spray at the foot of a waterfall: a puff of fine water thrown out and up from where the fall lands, slowing,
 * swelling and drifting downwind as it fades.
 */
final class SprayMote extends Mote {
	private final float swell;

	SprayMote(ClientLevel level, double x, double y, double z, RandomSource random) {
		super(level, x, y, z, Motes.Kind.SPRAY, random);
		this.quadSize = 0.25F + this.random.nextFloat() * 0.25F;
		this.swell = 0.8F + this.random.nextFloat() * 0.7F;
		this.lifetime = 30 + this.random.nextInt(35);
		this.fadeIn = 4;
		this.fadeOut = 26;
		this.peak = 0.28F + this.random.nextFloat() * 0.17F;
		double angle = this.random.nextDouble() * Math.PI * 2;
		double out = 0.03 + this.random.nextDouble() * 0.06;
		this.xd = Math.cos(angle) * out;
		this.zd = Math.sin(angle) * out;
		this.yd = 0.02 + this.random.nextDouble() * 0.05;
		this.roll = this.random.nextFloat() * (float) (Math.PI * 2);
		this.oRoll = this.roll;
		this.misty = 1.02F;
	}

	@Override
	protected void step(AirSample air) {
		Wind wind = air.wind();
		this.steer(windX(wind, 0.4), 0.004, windZ(wind, 0.4), 0.06);
		this.move(this.xd, this.yd, this.zd);
		this.quadSize += (this.swell - this.quadSize) * 0.06F;
	}
}
