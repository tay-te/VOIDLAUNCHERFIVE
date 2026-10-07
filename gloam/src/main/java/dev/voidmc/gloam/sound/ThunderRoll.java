package dev.voidmc.gloam.sound;

import dev.voidmc.gloam.Gloam;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * A distant roll of thunder, played once from a direction somewhere off in the storm (one of the
 * {@code gloam:thunder} clips at random). No attenuation: its distance is in its volume and in the clip itself.
 */
final class ThunderRoll extends AbstractSoundInstance {
	ThunderRoll(Vec3 eye, float volume, RandomSource random) {
		super(Gloam.id("thunder"), SoundSource.AMBIENT, random);
		double angle = random.nextDouble() * Math.PI * 2;
		this.x = eye.x + Math.cos(angle) * 40;
		this.y = eye.y + 20;
		this.z = eye.z + Math.sin(angle) * 40;
		this.volume = volume;
		this.pitch = 0.8F + random.nextFloat() * 0.35F;
		this.attenuation = SoundInstance.Attenuation.NONE;
		this.relative = false;
	}
}
