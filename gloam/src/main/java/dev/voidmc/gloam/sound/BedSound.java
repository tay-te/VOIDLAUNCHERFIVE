package dev.voidmc.gloam.sound;

import dev.voidmc.gloam.Gloam;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * One bed playing: a looping clip on the ambient category whose volume, pitch and (for a positional bed) direction
 * ease toward what {@link Soundscape} asks each tick, so a bed fades in and out over a few seconds and never pops. It
 * starts silent and stops itself once it has been silent for a few seconds, giving its channel back.
 *
 * <p>Attenuation is off: the distance is already in the volume {@link Mix} chose. A positional bed sits a few blocks
 * from the camera in the direction of its source, so the sound engine pans it there; the others play at the listener.
 */
final class BedSound extends AbstractSoundInstance implements TickableSoundInstance {
	private static final float RISE = 0.04F, FALL = 0.06F;
	private static final int QUIET_TICKS = 60;

	private final Bed bed;
	private float target, targetPitch = 1;
	private double offsetX, offsetZ, wantX, wantZ;
	private Vec3 eye = Vec3.ZERO;
	private int quiet;
	private boolean stopped;

	BedSound(Bed bed, Vec3 eye) {
		super(Gloam.id("bed." + bed.key()), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
		this.bed = bed;
		this.looping = true;
		this.delay = 0;
		this.volume = 0;
		this.attenuation = SoundInstance.Attenuation.NONE;
		this.relative = !bed.positional();
		this.eye = eye;
		this.place();
	}

	Bed bed() {
		return this.bed;
	}

	/** The volume now (before the ambient slider). */
	float volumeNow() {
		return this.volume;
	}

	float target() {
		return this.target;
	}

	/** What the bed should ease toward: its volume, its pitch, and where its source lies from {@code eye}. */
	void aim(float volume, float pitch, Vec3 eye, float offsetX, float offsetZ) {
		this.target = volume;
		this.targetPitch = pitch;
		this.eye = eye;
		this.wantX = offsetX;
		this.wantZ = offsetZ;
	}

	@Override
	public void tick() {
		float rate = this.target > this.volume ? RISE : FALL;
		float step = (this.target - this.volume) * rate;
		// a small fixed step as well, so a fade really reaches its end instead of creeping toward it
		step += Math.signum(this.target - this.volume) * 0.0005F;
		this.volume = Math.abs(this.target - this.volume) <= Math.abs(step) ? this.target : this.volume + step;
		this.pitch += (this.targetPitch - this.pitch) * 0.05F;
		this.offsetX += (this.wantX - this.offsetX) * 0.1;
		this.offsetZ += (this.wantZ - this.offsetZ) * 0.1;
		this.place();
		if (this.volume <= 0.001F && this.target <= 0.001F) {
			if (++this.quiet > QUIET_TICKS) {
				this.stop();
			}
		} else {
			this.quiet = 0;
		}
	}

	private void place() {
		if (!this.relative) {
			this.x = this.eye.x + this.offsetX;
			this.y = this.eye.y;
			this.z = this.eye.z + this.offsetZ;
		}
	}

	/** Stops it now (leaving the world); the engine lets it go on its next tick. */
	void stop() {
		this.stopped = true;
		this.looping = false;
	}

	@Override
	public boolean isStopped() {
		return this.stopped;
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}
}
