package dev.voidmc.bestiary.api;

import java.util.List;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

/**
 * Boss music and sound cues (BESTIARY.md §8.2: "a cue for Gloam's soundscape"). Bestiary fires a cue when a fight starts,
 * at each new phase and when it ends; Gloam (or anyone) listens and plays its own music. Until a listener says it
 * handled the cue, Bestiary plays a placeholder (vanilla sounds) to the players in the fight.
 *
 * <p>Cues: {@code start}, {@code phase_2} (and {@code phase_3}...), {@code victory}, {@code reset}.
 *
 * @since 0.2.0 (2026-10-22)
 */
public final class BossMusic {
	/** Return true when the cue was handled (the placeholder then stays quiet). */
	@FunctionalInterface
	public interface Cue {
		boolean cue(Mob boss, String cue, List<ServerPlayer> players);
	}

	public static final Event<Cue> CUE = EventFactory.createArrayBacked(Cue.class, ls -> (boss, cue, players) -> {
		boolean handled = false;
		for (Cue l : ls) {
			handled |= l.cue(boss, cue, players);
		}
		return handled;
	});

	private BossMusic() {
	}
}
