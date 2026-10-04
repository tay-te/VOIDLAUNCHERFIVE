package dev.voidmc.hearth.standing;

import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.hearth.standing.model.RegionData;
import dev.voidmc.hearth.standing.rules.DeedRule;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * What the standing engine needs from the game, so the engine itself is plain logic that tests can drive with in-memory
 * settlements: the calendar, the settlements, the atlas, the region data, the folk who saw a deed, and the ways to tell
 * players and other modules what happened. {@link GameHost} is the real one.
 */
public interface StandingHost {
	/** The sim calendar (days). */
	long day();

	@Nullable Settlement settlement(long id);

	/** Every live settlement (for the reputation page; rare). */
	Collection<? extends Settlement> settlements();

	/** Towns, live or not yet settled, within {@code radius} of the settlement, nearest first. */
	List<HearthWorld.SiteInfo> sitesNear(Settlement origin, int radius);

	RegionData region();

	void regionDirty();

	/** Residents (ids) of the deed's settlement who saw it: embodied, awake, in range, with the player in sight. */
	List<UUID> witnesses(StandingService.Deed deed, int radius, int max);

	/** Fires {@code SettlementEvents.STANDING_CHANGED}. */
	void standingChanged(Settlement settlement, UUID player, StandingService.Title from, StandingService.Title to);

	/** Tells the player (if online) that word of them reached a town. */
	void gossipArrived(UUID player, Settlement town, String from, int delta);

	/** The player's name, as folk would say it. */
	String playerName(UUID player);

	/** Whether the law module has made the player an outlaw in the settlement's region. */
	boolean outlawByLaw(UUID player, long settlementId);

	/** Something a deed does besides standing (a bath); the player may be offline. */
	void action(StandingService.Deed deed, DeedRule rule);
}
