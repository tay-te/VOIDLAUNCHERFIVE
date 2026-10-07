package dev.voidmc.mettle.client.moment;

import dev.voidmc.mettle.character.moment.MomentKind;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * The chimes for progress moments, played locally (UI sound category, not positional). Short note-block arpeggios a
 * couple of ticks apart, so a level-up rises; volumes are kept low because they can come often early on.
 */
final class MomentSounds {
	private record Note(long dueTick, SoundEvent sound, float pitch, float volume) {
	}

	private static final List<Note> QUEUE = new ArrayList<>();
	private static long tick;

	/** Semitones above the note block's F♯ as a pitch multiplier. */
	private static float semi(int n) {
		return (float) Math.pow(2, n / 12.0);
	}

	private MomentSounds() {
	}

	static void play(MomentKind kind) {
		SoundEvent chime = SoundEvents.NOTE_BLOCK_CHIME.value();
		SoundEvent bell = SoundEvents.NOTE_BLOCK_BELL.value();
		switch (kind) {
			case LEVEL -> {
				// A soft rising triad over an amethyst shimmer.
				at(0, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 0.9f);
				at(0, chime, semi(0), 0.26f);
				at(2, chime, semi(4), 0.26f);
				at(4, chime, semi(7), 0.28f);
			}
			case TIER -> {
				// Brighter: up to the octave, a bell on top, the level-up sparkle underneath.
				at(0, SoundEvents.PLAYER_LEVELUP, 1.5f, 0.22f);
				at(0, chime, semi(0), 0.3f);
				at(2, chime, semi(4), 0.3f);
				at(4, chime, semi(7), 0.32f);
				at(6, chime, semi(12), 0.34f);
				at(6, bell, semi(12), 0.18f);
			}
			case CHARACTER -> {
				at(0, SoundEvents.PLAYER_LEVELUP, 1.2f, 0.28f);
				at(3, chime, semi(7), 0.2f);
			}
			case LEARNED -> {
				// A click, then a single high chime.
				at(0, SoundEvents.UI_BUTTON_CLICK.value(), 1.35f, 0.18f);
				at(1, chime, semi(9), 0.24f);
			}
			case MASTERY -> {
				at(0, SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.2f);
				at(1, chime, semi(4), 0.26f);
				at(3, chime, semi(9), 0.28f);
				at(3, bell, semi(4), 0.16f);
			}
			case FEAT -> at(0, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.32f);
			case SYNERGY -> at(0, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.12f, 0.32f);
			case TEACHING -> {
				at(0, SoundEvents.BOOK_PAGE_TURN, 1.0f, 0.8f);
				at(4, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.94f, 0.3f);
			}
		}
		flush();
	}

	private static void at(int delayTicks, SoundEvent sound, float pitch, float volume) {
		QUEUE.add(new Note(tick + delayTicks, sound, pitch, volume));
	}

	static void tick() {
		tick++;
		flush();
	}

	private static void flush() {
		Minecraft mc = Minecraft.getInstance();
		for (Iterator<Note> it = QUEUE.iterator(); it.hasNext(); ) {
			Note n = it.next();
			if (n.dueTick() <= tick) {
				it.remove();
				mc.getSoundManager().play(SimpleSoundInstance.forUI(n.sound(), n.pitch(), n.volume()));
			}
		}
	}

	static void clear() {
		QUEUE.clear();
	}
}
