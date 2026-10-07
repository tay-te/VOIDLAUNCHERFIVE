package dev.voidmc.gloam.motes;

import dev.voidmc.gloam.air.AirSample;
import dev.voidmc.gloam.air.Wind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;

/**
 * Powder snow lifted off the ground by the wind: a faint streak skimming low over the snow, faster than the wind lets
 * the falling snow go, rising and settling a little as it goes.
 */
final class PowderMote extends Mote {
	private final float lift;

	PowderMote(ClientLevel level, double x, double y, double z, RandomSource random) {
		super(level, x, y, z, Motes.Kind.POWDER, random);
		this.quadSize = 0.35F + this.random.nextFloat() * 0.35F;
		this.lifetime = 24 + this.random.nextInt(24);
		this.fadeIn = 6;
		this.fadeOut = 14;
		this.peak = 0.35F + this.random.nextFloat() * 0.25F;
		this.lift = 0.004F + this.random.nextFloat() * 0.01F;
		this.misty = 1.25F;
	}

	@Override
	protected void step(AirSample air) {
		Wind wind = air.wind();
		double rise = this.lift * Math.sin(this.age * 0.25 + this.phase);
		this.steer(windX(wind, 1.1), rise, windZ(wind, 1.1), 0.25);
		this.move(this.xd, this.yd, this.zd);
		this.quadSize *= 1.01F;
	}
}
