package dev.voidmc.hearth.npc.crowd;

import dev.voidmc.hearth.api.StandingService.Title;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * A reaction the director has asked of one folk: picked up by the reaction's behaviour on its next poll, kept going while
 * the director refreshes it, over when {@link #until} passes or the behaviour has done its part.
 */
public final class Order {
	public final UUID folk;
	/** The player reacted to, or null for a spectacle (a brawl, a trial, a body). */
	public final @Nullable UUID player;
	public final Stimulus stimulus;
	public final Reaction reaction;
	public final Role role;
	/** This folk's place among those reacting so (0 barks first). */
	public final int rank;
	public final long issued;
	/** The player's title in town, for barks. */
	public final @Nullable Title title;
	/** The bark category (a spectacle's own, or the stimulus's). */
	public final String category;
	/** What is watched (a fight, a body, a spectacle), or null to react to the player where they stand. */
	public @Nullable BlockPos origin;
	/** A guard walks over first (a blade drawn too long, a body). */
	public boolean approach;
	public long until;
	/** The behaviour has finished (or was cut short): not to be started again. */
	public boolean done;
	public boolean started;

	public Order(UUID folk, @Nullable UUID player, Stimulus stimulus, Reaction reaction, Role role, int rank, long issued, long until,
		@Nullable Title title, String category) {
		this.folk = folk;
		this.player = player;
		this.stimulus = stimulus;
		this.reaction = reaction;
		this.role = role;
		this.rank = rank;
		this.issued = issued;
		this.until = until;
		this.title = title;
		this.category = category;
	}

	public boolean live(long now) {
		return !this.done && now <= this.until;
	}
}
