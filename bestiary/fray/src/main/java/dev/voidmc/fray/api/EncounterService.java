package dev.voidmc.fray.api;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * Boss encounters: Fray's own boss bar with phases, Break meter and status meters, encounter banners, participants and
 * damage shares. Bosses take poise damage into the Break meter; full = Broken for 60 ticks.
 *
 * @since 0.1.0 (2026-10-21)
 */
public interface EncounterService {
	Encounter start(Mob boss, Component name, @Nullable Component subtitle, float[] phaseThresholds);

	/**
	 * As {@link #start(Mob, Component, Component, float[])}; {@code announce} false resumes a fight (after a relog, an
	 * unload or a restart) without the start banner.
	 *
	 * @since 0.3.0 (2026-10-22)
	 */
	default Encounter start(Mob boss, Component name, @Nullable Component subtitle, float[] phaseThresholds, boolean announce) {
		return this.start(boss, name, subtitle, phaseThresholds);
	}

	@Nullable Encounter of(Mob boss);

	/** Adds to the Break meter (a fraction of its size). */
	void addBreak(Mob boss, float fraction);

	void end(Mob boss, Encounter.Outcome outcome);

	List<Encounter> active();

	/** True while the player takes part in a boss fight (Mettle's boss rules, Hearth's tracker fold). */
	boolean inBossFight(ServerPlayer player);
}
