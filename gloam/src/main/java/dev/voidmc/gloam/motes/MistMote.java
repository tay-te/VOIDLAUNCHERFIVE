package dev.voidmc.gloam.motes;

import dev.voidmc.gloam.air.AirSample;
import dev.voidmc.gloam.air.Wind;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.util.Mth;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3fc;

/**
 * A sheet of ground mist: a wide, soft-edged patch lying flat a little above the ground or water. Many of them overlap
 * at a few heights, more close to the ground than above it, so together they make one low, layered veil that is
 * densest in the lowest blocks; each creeps with a little of the wind and gathers and thins over most of a minute.
 *
 * <p>A sheet is drawn the way the far mist over VOID LOD is ({@link MoteLayers#GROUND}): its alpha is the optical depth
 * of the slab of mist it stands for, and the shader makes it as opaque as the eye's path through that slab is long, so
 * a sheet is a faint veil seen from above and a white band seen across, and the near mist runs on into the far banks.
 * <p>A shader pack draws particles with its own program, which treats a flat translucent quad like glass or water
 * (refracting what is behind it) and cannot do the per-pixel path, so seen edge-on the sheets turn into hard lines.
 * Under a pack each sheet is drawn instead as an upright veil standing on the ground (a wide, low, streaky sprite of
 * its own, feathered to nothing well inside the quad), its bottom lifted to where the feathering meets the ground so
 * the terrain never cuts it off flat.
 */
final class MistMote extends Mote {
	/** How much more body the sheets carry under a pack, which lights them dimly at dawn. */
	private static final float PACK_BODY = 4.0F;
	private static final FacingCameraMode FLAT = (target, camera, partialTick) -> target.rotationX((float) (-Math.PI / 2));

	private final float tau, carry, packSize, packLift;
	private final TextureAtlasSprite veil;

	/**
	 * @param size the sheet's half-width, in blocks
	 * @param tau  the optical depth of the slab of mist it stands for, looked straight down through
	 * @param above how high above the ground it lies, in blocks
	 */
	MistMote(ClientLevel level, double x, double y, double z, RandomSource random, float size, float tau, float above) {
		super(level, x, y, z, Motes.Kind.MIST, random);
		this.quadSize = size;
		this.tau = tau;
		this.lifetime = 700 + this.random.nextInt(700);
		this.fadeIn = 140;
		this.fadeOut = 180;
		this.carry = 0.08F + this.random.nextFloat() * 0.08F;
		this.roll = this.random.nextFloat() * (float) (Math.PI * 2);
		this.oRoll = this.roll;
		this.packSize = Math.min(size * 0.85F, 9.0F);
		// the veil's body reaches a third of the quad's half-height down: stand it so its feathering meets the ground
		this.packLift = Math.max(0, 0.22F * this.packSize - above);
		this.veil = Motes.Kind.VEIL.sprite(random);
	}

	@Override
	protected void step(AirSample air) {
		Wind wind = air.wind();
		this.steer(windX(wind, this.carry), 0, windZ(wind, this.carry), 0.01);
		this.move(this.xd, this.yd, this.zd);
		this.roll += 0.0004F;
		if (air.mist() < 0.03 || this.y > air.mistTop() + 1) {
			this.endWithin(this.fadeOut);
		}
		Vector3fc colour = Motes.pack() ? ONE : Motes.cloud();
		this.setColor(colour.x(), colour.y(), colour.z());
	}

	@Override
	protected float finish(float a) {
		float strength = this.tau * a / this.peak;
		if (!Motes.pack()) {
			return strength;
		}
		// an upright veil thins as the camera comes close, and above the eye counts double, so none hangs in front of
		// the eyes as a card or overhead like a cloud
		Vec3 eye = Motes.camera();
		double dy = this.y + this.packLift - eye.y;
		double d = Math.sqrt(Mth.square(this.x - eye.x) + Mth.square(this.z - eye.z) + dy * dy * (dy > 0 ? 4 : 1));
		float near = (float) Math.clamp((d - this.packSize * 0.5) / (this.packSize * 1.2), 0, 1);
		return Math.min(0.75F, strength * PACK_BODY) * near * near * (3 - 2 * near);
	}

	@Override
	public FacingCameraMode getFacingCameraMode() {
		return Motes.pack() ? FacingCameraMode.LOOKAT_Y : FLAT;
	}

	@Override
	protected float getU0() {
		return Motes.pack() ? this.veil.getU0() : super.getU0();
	}

	@Override
	protected float getU1() {
		return Motes.pack() ? this.veil.getU1() : super.getU1();
	}

	@Override
	protected float getV0() {
		return Motes.pack() ? this.veil.getV0() : super.getV0();
	}

	@Override
	protected float getV1() {
		return Motes.pack() ? this.veil.getV1() : super.getV1();
	}

	@Override
	public float getQuadSize(float partialTick) {
		return Motes.pack() ? this.packSize : this.quadSize;
	}

	@Override
	public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
		if (!Motes.pack()) {
			super.extract(state, camera, partialTick);
			return;
		}
		Quaternionf rotation = new Quaternionf();
		FacingCameraMode.LOOKAT_Y.setRotation(rotation, camera, partialTick);
		Vec3 eye = camera.position();
		float x = (float) (Mth.lerp(partialTick, this.xo, this.x) - eye.x());
		float y = (float) (Mth.lerp(partialTick, this.yo, this.y) + this.packLift - eye.y());
		float z = (float) (Mth.lerp(partialTick, this.zo, this.z) - eye.z());
		this.extractRotatedQuad(state, rotation, x, y, z, partialTick);
	}

	@Override
	protected Layer getLayer() {
		return MoteLayers.GROUND;
	}

	@Override
	public int getLightCoords(float partialTick) {
		return Motes.pack() ? super.getLightCoords(partialTick) : LightCoordsUtil.FULL_BRIGHT;
	}
}
