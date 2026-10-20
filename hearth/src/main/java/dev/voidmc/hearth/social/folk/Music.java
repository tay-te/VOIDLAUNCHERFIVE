package dev.voidmc.hearth.social.folk;

import dev.voidmc.hearth.api.Culture;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Tunes for festival musicians: short folk melodies on the note block's instruments, an instrument for each culture,
 * a drum on the beat. A tune is a list of semitones above F#3 (the note block's lowest), -1 for a rest.
 */
public final class Music {
	static final int[][] TUNES = {
		{12, 14, 16, 12, 16, 17, 19, -1, 19, 21, 19, 17, 16, 12, 14, -1, 12, 14, 16, 16, 14, 12, 11, 12},
		{7, 12, 12, 14, 16, 14, 12, 11, 9, 7, -1, 7, 9, 11, 12, 14, 12, 11, 9, 11, 7, -1, -1, -1},
		{16, 16, 19, 16, 14, 12, 14, 16, 12, -1, 9, 12, 14, 16, 19, 21, 19, 16, 14, 12, -1, 12, 14, 12},
		{4, 7, 11, 12, 11, 7, 4, -1, 6, 9, 12, 14, 12, 9, 6, -1, 7, 11, 14, 16, 14, 11, 7, -1}
	};

	private Music() {
	}

	public static Holder<SoundEvent> instrument(Culture c) {
		return switch (c) {
			case TEMPERATE, DESERT -> SoundEvents.NOTE_BLOCK_HARP;
			case WISTERIA, KARST -> SoundEvents.NOTE_BLOCK_CHIME;
			case NORTHERN, ALPINE, MOORLAND, MESA -> SoundEvents.NOTE_BLOCK_FLUTE;
			case TUNDRA -> SoundEvents.NOTE_BLOCK_BELL;
			case MARITIME, MEDITERRANEAN -> SoundEvents.NOTE_BLOCK_GUITAR;
			case TROPICAL -> SoundEvents.NOTE_BLOCK_XYLOPHONE;
			case STEPPE, BAYOU -> SoundEvents.NOTE_BLOCK_BANJO;
		};
	}

	static Holder<SoundEvent> named(String name, Culture fallback) {
		return switch (name) {
			case "harp" -> SoundEvents.NOTE_BLOCK_HARP;
			case "flute" -> SoundEvents.NOTE_BLOCK_FLUTE;
			case "guitar" -> SoundEvents.NOTE_BLOCK_GUITAR;
			case "banjo" -> SoundEvents.NOTE_BLOCK_BANJO;
			case "bell" -> SoundEvents.NOTE_BLOCK_BELL;
			case "chime" -> SoundEvents.NOTE_BLOCK_CHIME;
			case "xylophone" -> SoundEvents.NOTE_BLOCK_XYLOPHONE;
			case "bass" -> SoundEvents.NOTE_BLOCK_BASS;
			case "didgeridoo" -> SoundEvents.NOTE_BLOCK_DIDGERIDOO;
			case "pling" -> SoundEvents.NOTE_BLOCK_PLING;
			case "bit" -> SoundEvents.NOTE_BLOCK_BIT;
			case "iron_xylophone" -> SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE;
			case "cow_bell" -> SoundEvents.NOTE_BLOCK_COW_BELL;
			default -> instrument(fallback);
		};
	}

	/** The note block's pitch for a semitone (0–24). */
	public static float pitch(int semitone) {
		return (float) Math.pow(2.0, (semitone - 12) / 12.0);
	}

	/**
	 * Plays beat {@code beat} of tune {@code tune} at {@code at} (call every few ticks). Returns false on a rest.
	 */
	public static boolean play(ServerLevel level, Vec3 at, Culture culture, int tune, int beat) {
		return play(level, at, culture, tune, beat, "");
	}

	/** As above on a named note-block instrument ({@code flute}, {@code guitar}...; empty: the culture's). */
	public static boolean play(ServerLevel level, Vec3 at, Culture culture, int tune, int beat, String instrument) {
		Holder<SoundEvent> sound = instrument.isEmpty() ? instrument(culture) : named(instrument, culture);
		int[] t = TUNES[Math.floorMod(tune, TUNES.length)];
		int note = t[Math.floorMod(beat, t.length)];
		if (beat % 4 == 0) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_BASEDRUM, SoundSource.RECORDS, 0.9F, 1.0F);
		}
		if (note < 0) {
			return false;
		}
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.RECORDS, 1.6F, pitch(note));
		level.sendParticles(ParticleTypes.NOTE, at.x, at.y + 2.2, at.z, 0, note / 24.0, 0, 0, 1);
		return true;
	}
}
