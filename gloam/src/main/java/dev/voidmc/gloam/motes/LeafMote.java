package dev.voidmc.gloam.motes;

import dev.voidmc.gloam.air.AirSample;
import dev.voidmc.gloam.air.Wind;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Quaternionf;

/**
 * A leaf falling from the canopy: it sinks at about half a metre a second, swinging from side to side like a pendulum
 * (along a line of its own, so it swings in still air too) and pausing and diving as real leaves do, drifting a little
 * off its line, spinning and flipping over (the quad tilts away from the eye and back, so it flashes thin and broad),
 * and carried off by the wind (less of it under the trees than in the open). On the ground it lies still for a few
 * seconds and fades; on water it floats a little way and sinks.
 */
final class LeafMote extends Mote {
	private final float flutter, sink, carry, flipRate;
	/** The line it swings along and the way it drifts, as unit vectors on the ground, and how far it swings. */
	private final float swingX, swingZ, driftX, driftZ, swing;
	private float spin, flip, oFlip;
	private boolean landed;

	/** @param argb the leaf's colour: the tint of the leaves it fell from, turned in autumn */
	LeafMote(ClientLevel level, double x, double y, double z, RandomSource random, int argb) {
		super(level, x, y, z, Motes.Kind.LEAF, random);
		this.hasPhysics = true;
		this.setSize(0.1F, 0.05F);
		this.quadSize = 0.13F + this.random.nextFloat() * 0.06F;
		this.lifetime = 700 + this.random.nextInt(400);
		this.fadeIn = 6;
		this.fadeOut = 30;
		this.flutter = 0.08F + this.random.nextFloat() * 0.08F;
		this.sink = 0.017F + this.random.nextFloat() * 0.016F;
		float a = this.random.nextFloat() * (float) (Math.PI * 2);
		this.swingX = Mth.cos(a);
		this.swingZ = Mth.sin(a);
		float b = a + (this.random.nextFloat() - 0.5F) * 2.0F;
		float drift = 0.004F + this.random.nextFloat() * 0.01F;
		this.driftX = Mth.cos(b) * drift;
		this.driftZ = Mth.sin(b) * drift;
		this.swing = 0.035F + this.random.nextFloat() * 0.03F;
		this.carry = 0.35F + this.random.nextFloat() * 0.25F;
		this.spin = (this.random.nextFloat() - 0.5F) * 0.3F;
		this.flipRate = 0.12F + this.random.nextFloat() * 0.2F;
		this.roll = this.random.nextFloat() * (float) (Math.PI * 2);
		this.oRoll = this.roll;
		this.setColor(argb);
	}

	@Override
	protected void step(AirSample air) {
		if (this.landed) {
			return;
		}
		Wind wind = air.wind();
		float sway = (float) Math.sin(this.age * this.flutter + this.phase);
		// gusts reach each leaf a little differently
		double gust = 1 + 0.4 * Math.sin(this.age * 0.07 + this.phase * 2);
		// the swing: out along its line and back (cos, so it is fastest through the bottom of the arc, where it sinks most)
		double side = Math.cos(this.age * this.flutter + this.phase) * this.swing;
		double vx = windX(wind, this.carry * gust) + this.swingX * side + this.driftX;
		double vz = windZ(wind, this.carry * gust) + this.swingZ * side + this.driftZ;
		// a leaf rocking edge-on drops fast, lying flat it floats
		double vy = -this.sink * (0.45 + 1.1 * (1 - sway * sway));
		this.steer(vx, vy, vz, 0.16);
		BlockPos at = BlockPos.containing(this.x, this.y, this.z);
		if (this.level.getFluidState(at).is(FluidTags.WATER)) {
			// afloat: drift slowly with the wind, then sink
			this.yd = 0;
			this.xd *= 0.3;
			this.zd *= 0.3;
			this.endWithin(60);
		}
		this.move(this.xd, this.yd, this.zd);
		this.roll += this.spin * (1 + Math.abs(sway));
		this.oFlip = this.flip;
		// tilting up to 70 degrees either way, never past edge-on (the back of a particle is not drawn)
		this.flip = 1.2F * (float) Math.sin(this.age * this.flipRate + this.phase);
		if (this.onGround) {
			this.landed = true;
			this.xd = this.yd = this.zd = 0;
			this.spin = 0;
			this.oFlip = this.flip;
			this.endWithin(70 + this.random.nextInt(110));
		}
	}

	/** Whether it has come down (it then lies still and fades). */
	boolean landed() {
		return this.landed;
	}

	@Override
	public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
		Quaternionf rotation = new Quaternionf();
		this.getFacingCameraMode().setRotation(rotation, camera, partialTick);
		rotation.rotateZ(Mth.lerp(partialTick, this.oRoll, this.roll));
		rotation.rotateX(Mth.lerp(partialTick, this.oFlip, this.flip));
		this.extractRotatedQuad(state, camera, rotation, partialTick);
	}
}
