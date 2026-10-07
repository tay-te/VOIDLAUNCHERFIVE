package dev.voidmc.gloam.motes;

import dev.voidmc.gloam.air.AirSample;
import dev.voidmc.gloam.air.Wind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;

/**
 * A speck of pollen or dust hanging in sunlit air: it rides the breeze, wanders on the smallest eddies, rises and
 * sinks a little on the warmth off the ground, and glints as it turns in the light.
 */
final class PollenMote extends Mote {
	private final float glint;

	/** @param dust a grain of dust over bare ground (greyer, larger) rather than pollen over a meadow */
	PollenMote(ClientLevel level, double x, double y, double z, RandomSource random, boolean dust) {
		super(level, x, y, z, Motes.Kind.POLLEN, random);
		this.quadSize = (dust ? 0.026F : 0.022F) + this.random.nextFloat() * 0.014F;
		this.lifetime = 140 + this.random.nextInt(140);
		this.fadeIn = 30;
		this.fadeOut = 40;
		this.peak = 0.7F + this.random.nextFloat() * 0.3F;
		this.glint = 0.15F + this.random.nextFloat() * 0.2F;
		float v = this.random.nextFloat() * 0.06F;
		if (dust) {
			this.setColor(0.90F - v, 0.86F - v, 0.78F - v);
		} else {
			this.setColor(1.0F, 0.96F - v, 0.80F - v);
		}
	}

	@Override
	protected void step(AirSample air) {
		Wind wind = air.wind();
		double wander = 0.012;
		double vx = windX(wind, 0.8) + (this.random.nextDouble() - 0.5) * wander * 4;
		double vz = windZ(wind, 0.8) + (this.random.nextDouble() - 0.5) * wander * 4;
		double vy = 0.004 * Math.sin(this.age * 0.035 + this.phase) + (this.random.nextDouble() - 0.5) * wander;
		this.steer(vx, vy, vz, 0.04);
		this.move(this.xd, this.yd, this.zd);
		if ((this.age & 7) == 0 && this.buried()) {
			this.remove();
		}
	}

	@Override
	protected float fade() {
		// turning in the light: brighter and dimmer as it tumbles
		return super.fade() * (0.65F + 0.35F * (float) Math.sin(this.age * this.glint + this.phase));
	}
}
