package dev.voidmc.hearth.api;

import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/** The settlements of one dimension, and its shared Hearth state. Implemented by the sim module; server thread only. */
public interface HearthWorld {
	/** The Hearth state of a level, or null if Hearth does not run there (it runs in the overworld). */
	static @Nullable HearthWorld of(ServerLevel level) {
		return HearthServices.world(level);
	}

	ServerLevel level();

	TerrainProbe terrain();

	/** The sim calendar: whole days since the world began (the overworld's day count). */
	long day();

	/** Live (adopted) settlements: those that exist as records and are simulated. */
	Collection<? extends Settlement> settlements();

	@Nullable Settlement settlement(long id);

	/** The settlement whose bounds contain {@code pos} (with a small margin), if any. */
	@Nullable Settlement settlementAt(BlockPos pos);

	/** The nearest live settlement to {@code pos} within {@code maxDistance} blocks of its center, if any. */
	@Nullable Settlement nearest(BlockPos pos, int maxDistance);

	<T> T data(HearthDataKey<T> key);

	void markDirty();

	/**
	 * Founds a live settlement at {@code at}: the planner lays it out and it is built by the runtime path (§5.1). Used for
	 * hamlets founded by settlers from a crowded town and for sites a player scouted.
	 *
	 * @param culture null to take it from the land
	 * @param parent  the settlement its first settlers come from, or null
	 * @param reason  a short why, for logs and the settlement's history ("scouted by Alex for Ashbrook")
	 * @return the new settlement, or null if it cannot be founded there ({@link #whyNotFound} says why)
	 */
	@Nullable Settlement found(BlockPos at, @Nullable Culture culture, @Nullable Settlement parent, String reason);

	/** Why a settlement cannot be founded at {@code at} (under water, too steep, too near another), or null if it can. */
	@Nullable String whyNotFound(BlockPos at);

	/**
	 * Settlements live or historic (planned by the atlas but not yet adopted) within {@code radius} blocks, nearest first,
	 * for rumours, maps and quests. Answers from cached region plans; regions not yet planned may be missing (they are
	 * planned off the server thread and appear on a later call).
	 */
	List<SiteInfo> knownSites(BlockPos near, int radius);

	/**
	 * @param settlementId the live settlement's id, or the id it will have when adopted
	 * @param live         whether it has been adopted (is in {@link #settlements()})
	 */
	record SiteInfo(long settlementId, String name, Culture culture, BlockPos center, SettlementTier tier, boolean live) {
	}
}
