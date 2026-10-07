package dev.voidmc.gloam.motes;

import dev.voidmc.gloam.air.AirSample;
import dev.voidmc.gloam.air.Wind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;

/**
 * A fine grain of snow driven by the wind: it falls slowly and goes almost wholly with the wind, swirling on the
 * gusts, so in a strong wind the snow streams sideways past the camera. It stops at the first thing it meets.
 */
final class SnowMote extends Mote {
	private final float fall, swirl;

	SnowMote(ClientLevel level, double x, double y, double z, RandomSource random) {
		super(level, x, y, z, Motes.Kind.SNOW, random);
		this.quadSize = 0.016F + this.random.nextFloat() * 0.016F;
		this.lifetime = 50 + this.random.nextInt(50);
		this.fadeIn = 6;
		this.fadeOut = 12;
		this.peak = 0.75F + this.random.nextFloat() * 0.25F;
		this.fall = 0.03F + this.random.nextFloat() * 0.035F;
		this.swirl = 0.15F + this.random.nextFloat() * 0.2F;
		this.setColor(0.95F, 0.96F, 1.0F);
	}

	@Override
	protected void step(AirSample air) {
		Wind wind = air.wind();
		double eddy = Math.sin(this.age * this.swirl + this.phase) * 0.03 * (0.5 + wind.gust());
		this.steer(windX(wind, 1.0) - wind.z() * eddy, -this.fall + eddy * 0.5, windZ(wind, 1.0) + wind.x() * eddy, 0.12);
		this.move(this.xd, this.yd, this.zd);
		if ((this.age & 3) == 0 && this.buried()) {
			this.remove();
		}
	}
}
