package dev.voidmc.gloam.motes;

import dev.voidmc.gloam.air.AirSample;
import dev.voidmc.gloam.air.Wind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * A firefly: a small warm point that wanders slowly a little above the grass and flashes, dark between flashes as
 * real fireflies are. A flash comes on and goes out in a step or two and lasts about a second, every two to six seconds,
 * and the firefly climbs a little as it flashes (the "J" a courting Photinus draws). Lit, it is full bright.
 */
final class FireflyMote extends Mote {
	private int dark, flash, flashLength;
	private int turn;
	private final float size;
	private double headX, headZ, height, ground;

	FireflyMote(ClientLevel level, double x, double y, double z, RandomSource random) {
		super(level, x, y, z, Motes.Kind.FIREFLY, random);
		// the light is the middle half of the sprite (2x2 of 4x4 pixels)
		this.size = 0.12F + this.random.nextFloat() * 0.04F;
		this.quadSize = this.size;
		this.lifetime = 600 + this.random.nextInt(600);
		this.fadeIn = 0;
		this.fadeOut = 0;
		this.dark = this.random.nextInt(120);
		this.height = 0.5 + this.random.nextDouble() * 1.6;
		this.ground = y - this.height;
	}

	@Override
	protected void step(AirSample air) {
		if (this.flash > 0) {
			this.flash--;
			if (this.flash == 0) {
				this.dark = 30 + this.random.nextInt(55);
			}
		} else if (--this.dark <= 0) {
			this.flashLength = 12 + this.random.nextInt(11);
			this.flash = this.flashLength;
		}
		if (--this.turn <= 0) {
			this.turn = 20 + this.random.nextInt(50);
			double angle = this.random.nextDouble() * Math.PI * 2;
			double speed = 0.006 + this.random.nextDouble() * 0.018;
			this.headX = Math.cos(angle) * speed;
			this.headZ = Math.sin(angle) * speed;
			this.height = Math.clamp(this.height + (this.random.nextDouble() - 0.5) * 0.8, 0.3, 2.4);
			this.ground = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(this.x), (int) Math.floor(this.z));
		}
		Wind wind = air.wind();
		double vy = Math.clamp((this.ground + this.height - this.y) * 0.03, -0.02, 0.02);
		if (this.flash > 0) {
			vy += 0.012;
		}
		this.steer(this.headX + windX(wind, 0.08), vy, this.headZ + windZ(wind, 0.08), 0.06);
		this.move(this.xd, this.yd, this.zd);
		// a little larger the further off, so a distant one still reads as a point of light and not a lost pixel
		this.quadSize = this.size * (1 + (float) Math.sqrt(this.toCamera()) / 18);
		if ((this.age & 7) == 0 && this.buried()) {
			this.remove();
		}
	}

	/** On and off in steps (docs/STYLE.md): a tick at half as a flash comes on and as it goes out, full between. */
	@Override
	protected float fade() {
		if (this.flash <= 0) {
			return 0;
		}
		int t = this.flashLength - this.flash;
		return t < 1 || this.flash <= 2 ? 0.5F : 1.0F;
	}

	@Override
	protected Layer getLayer() {
		return Motes.pack() ? Layer.TRANSLUCENT : MoteLayers.GLOW;
	}

	@Override
	public int getLightCoords(float partialTick) {
		return LightCoordsUtil.FULL_BRIGHT;
	}
}
