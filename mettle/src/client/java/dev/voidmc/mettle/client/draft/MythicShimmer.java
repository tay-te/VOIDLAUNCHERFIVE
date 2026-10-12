package dev.voidmc.mettle.client.draft;

import dev.voidmc.mettle.character.draft.CardCues;
import dev.voidmc.mettle.client.MettleClientOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * The Mythic card's idle shimmer (docs/SOUNDS.md §3): a seamless loop held while the card is hovered, faded in and out
 * a tick at a time. It stops itself when the screen closes. While the game is paused nothing ticks it, so it then starts
 * at full volume and is stopped outright ({@link CardAudio} sees to that).
 */
final class MythicShimmer extends AbstractTickableSoundInstance {
	private final float full;
	private final float stepIn;
	private final float stepOut;
	private float level;
	private boolean releasing;

	MythicShimmer(CardCues.Loop loop, boolean paused) {
		super(SoundEvent.createVariableRangeEvent(loop.id()), SoundSource.UI, SoundInstance.createUnseededRandom());
		this.full = loop.volume();
		this.stepIn = 50f / Math.max(50, loop.fadeInMs());
		this.stepOut = 50f / Math.max(50, loop.fadeOutMs());
		this.looping = true;
		this.delay = 0;
		this.attenuation = SoundInstance.Attenuation.NONE;
		this.relative = true;
		this.level = paused ? 1f : this.stepIn;
		this.volume = this.full * this.level * MettleClientOptions.cardSoundsScale();
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

	void release() {
		this.releasing = true;
	}

	void hold() {
		this.releasing = false;
	}

	boolean releasing() {
		return this.releasing;
	}

	@Override
	public void tick() {
		if (Minecraft.getInstance().gui.screen() == null) {
			this.releasing = true;
		}
		this.level = this.releasing ? this.level - this.stepOut : Math.min(1f, this.level + this.stepIn);
		if (this.level <= 0f) {
			stop();
			return;
		}
		this.volume = this.full * this.level * MettleClientOptions.cardSoundsScale();
	}
}
