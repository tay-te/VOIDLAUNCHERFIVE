package dev.voidmc.mettle.client.moment;

import dev.voidmc.mettle.character.moment.MomentKind;
import dev.voidmc.mettle.client.MettleClientOptions;
import dev.voidmc.mettle.client.draft.CardAudio;

/**
 * The sounds of build feel's milestones (docs/BUILD_FEEL.md §2.4), all reused from the card cues and the moment chimes
 * (SOUNDS.md): a first card or Signature the deck slot; a set the set chord (a 6-set with the Mythic shimmer as its
 * tail); a byname tier the set chord with the level chime (a bell from tier II); a first Temper or Inscription the Epic
 * pick sweetener; a ready forge the feat fanfare. With card sounds off, the plain moment chimes stand in.
 */
final class MilestoneSounds {
	private MilestoneSounds() {
	}

	static void play(MomentKind kind, int tier) {
		boolean cards = MettleClientOptions.cardSoundsScale() > 0f;
		switch (kind) {
			case CARD, SIGNATURE -> {
				if (cards) {
					CardAudio.cue("slot", 0);
				} else {
					MomentSounds.play(MomentKind.LEARNED);
				}
			}
			case SET -> {
				if (cards) {
					CardAudio.cue("set_complete", 0);
					if (tier >= 6) {
						CardAudio.cue("hover.mythic", 1300);
					}
				} else {
					MomentSounds.play(MomentKind.MASTERY);
				}
			}
			case BYNAME -> {
				if (cards) {
					CardAudio.cue("set_complete", 0);
				}
				MomentSounds.play(tier >= 2 ? MomentKind.TIER : MomentKind.LEVEL);
			}
			case BOON -> {
				if (cards) {
					CardAudio.cue("pick.epic", 0);
				} else {
					MomentSounds.play(MomentKind.LEARNED);
				}
			}
			case FORGE_READY -> MomentSounds.play(MomentKind.FEAT);
			default -> MomentSounds.play(kind);
		}
	}
}
