package dev.voidmc.gloam.motes;

import dev.voidmc.gloam.air.AirSample;
import dev.voidmc.gloam.air.Atmosphere;
import dev.voidmc.gloam.air.Wind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3fc;

/**
 * One mote: a particle that moves itself with the air at the camera ({@link AirSample}) instead of the vanilla
 * particle's fixed gravity and friction, fades in and out over its life, and fades out at once when the motes are
 * switched off. Subclasses say how it moves ({@link #step}).
 */
abstract class Mote extends SingleQuadParticle {
	/** How much more opaque the soft motes are drawn under a shader pack. */
	private static final float PACK_BODY = 2.5F;
	/** Ticks a mote takes to vanish once the motes are switched off. */
	private static final int VANISH = 8;

	final Motes.Kind kind;
	/** The alpha at full strength, before fading in and out. */
	protected float peak = 1.0F;
	/** Ticks to fade in at the start of its life and out at the end. */
	protected int fadeIn = 10, fadeOut = 20;
	/** A random phase, so motes spawned together do not sway in step. */
	protected final float phase;
	/**
	 * A soft mote (mist, breath, spray) takes the colour of the air ({@link Motes#mist()}) times this shade, at full
	 * brightness; under a shader pack it is white times the shade and lit like any particle, the pack's light being its
	 * own. Zero for the others, which keep their colour and the light where they are.
	 */
	protected float misty;
	private int vanishing = -1;

	protected Mote(ClientLevel level, double x, double y, double z, Motes.Kind kind, RandomSource random) {
		super(level, x, y, z, kind.sprite(random));
		this.kind = kind;
		this.hasPhysics = false;
		this.gravity = 0;
		this.friction = 1;
		this.alpha = 0;
		this.phase = this.random.nextFloat() * (float) (Math.PI * 2);
	}

	/** A mote of {@code kind} with nothing to go on (for /particle): green leaves, a middling mist. */
	static Mote standard(Motes.Kind kind, ClientLevel level, double x, double y, double z, RandomSource random) {
		return switch (kind) {
			case LEAF -> new LeafMote(level, x, y, z, random, 0xFF59AE30);
			case POLLEN -> new PollenMote(level, x, y, z, random, false);
			case FIREFLY -> new FireflyMote(level, x, y, z, random);
			case MIST -> new MistMote(level, x, y, z, random, 4.0F, 0.15F, 0.5F);
			case BREATH -> new BreathMote(level, x, y, z, random, 0, 0, 0);
			case SNOW -> new SnowMote(level, x, y, z, random);
			case SPRAY -> new SprayMote(level, x, y, z, random);
			case POWDER -> new PowderMote(level, x, y, z, random);
			case VEIL -> new MistMote(level, x, y, z, random, 4.0F, 0.15F, 0.5F);
		};
	}

	/** Starts the mote partway through its life, already faded in: for filling empty air at once, not over seconds. */
	void begunAgo(int ticks) {
		this.age = Math.max(0, Math.min(ticks, this.lifetime - this.fadeOut - 1));
	}

	@Override
	public void tick() {
		long start = System.nanoTime();
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
		this.oRoll = this.roll;
		if (this.vanishing < 0 && !Motes.active()) {
			this.vanishing = VANISH;
		}
		if (this.age++ >= this.lifetime || this.vanishing == 0) {
			this.remove();
		} else {
			this.step(Atmosphere.get().here());
			if (this.misty > 0) {
				Vector3fc air = Motes.pack() ? ONE : Motes.mist();
				// clamped: the packed colour wraps past 1 into the next channel
				this.setColor(Math.min(1, air.x() * this.misty), Math.min(1, air.y() * this.misty), Math.min(1, air.z() * this.misty));
			}
			float a = this.peak * this.fade();
			if (this.misty > 0 && Motes.pack()) {
				// a pack lights the soft motes like any particle (dim in the low light of dawn, where mist shows most)
				// instead of in the bright colour of the air, so they need more body to show at all
				a *= PACK_BODY;
			}
			a = this.finish(a);
			if (this.vanishing > 0) {
				a *= this.vanishing-- / (float) VANISH;
			}
			a = Math.clamp(a, 0, 1);
			// pixel-true (docs/STYLE.md): a mote fades in and out in a few flat steps, not smoothly
			this.alpha = this.stepped() ? Math.round(a * ALPHA_STEPS) / (float) ALPHA_STEPS : a;
		}
		Motes.timeParticle(System.nanoTime() - start);
	}

	/** How many flat steps a mote's alpha takes between nothing and full. */
	private static final int ALPHA_STEPS = 4;

	/** Whether this mote's alpha is drawn in steps (not when it carries something else, as the mist's optical depth). */
	protected boolean stepped() {
		return true;
	}

	/** The alpha to draw with, given {@code a} (its strength times its fade): for motes that draw alpha as something else. */
	protected float finish(float a) {
		return a;
	}

	/** Moves the mote one tick through the air {@code air}. */
	protected abstract void step(AirSample air);

	/** 0..1, how far faded in it is: up over {@link #fadeIn}, down over {@link #fadeOut}. */
	protected float fade() {
		float in = this.fadeIn <= 0 ? 1 : Math.min(1, (float) this.age / this.fadeIn);
		float out = this.fadeOut <= 0 ? 1 : Math.min(1, (float) (this.lifetime - this.age) / this.fadeOut);
		return Math.min(in, out);
	}

	/** Ends the mote's life within {@code ticks}, fading out as it goes. */
	protected void endWithin(int ticks) {
		this.lifetime = Math.min(this.lifetime, this.age + ticks);
	}

	/** Eases the velocity toward (vx, vy, vz), closing {@code ease} of the gap each tick: how much inertia it has. */
	protected void steer(double vx, double vy, double vz, double ease) {
		this.xd += (vx - this.xd) * ease;
		this.yd += (vy - this.yd) * ease;
		this.zd += (vz - this.zd) * ease;
	}

	/** The wind's velocity along x in blocks per tick, times {@code carry} (how much of it carries this mote). */
	protected static double windX(Wind wind, double carry) {
		return wind.x() * wind.now() / 20.0 * carry;
	}

	/** The wind's velocity along z in blocks per tick, times {@code carry}. */
	protected static double windZ(Wind wind, double carry) {
		return wind.z() * wind.now() / 20.0 * carry;
	}

	/** Whether the mote is inside something solid or in a fluid. */
	protected boolean buried() {
		BlockState state = this.level.getBlockState(BlockPos.containing(this.x, this.y, this.z));
		return state.isSolid() || !state.getFluidState().isEmpty();
	}

	double x() {
		return this.x;
	}

	double z() {
		return this.z;
	}

	/** The squared distance to the camera. */
	protected double toCamera() {
		return Motes.camera().distanceToSqr(this.x, this.y, this.z);
	}

	protected static final Vector3fc ONE = new org.joml.Vector3f(1);

	@Override
	protected Layer getLayer() {
		return this.misty > 0 ? MoteLayers.MIST : Layer.TRANSLUCENT;
	}

	@Override
	public int getLightCoords(float partialTick) {
		return this.misty > 0 && !Motes.pack() ? LightCoordsUtil.FULL_BRIGHT : super.getLightCoords(partialTick);
	}

	/** Sets the colour from a packed ARGB colour. */
	protected void setColor(int argb) {
		this.setColor((argb >> 16 & 0xFF) / 255.0F, (argb >> 8 & 0xFF) / 255.0F, (argb & 0xFF) / 255.0F);
	}
}
