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

	/** The sim's clock in ticks (the overworld's day clock): caravans' depart and arrive ticks are on it. */
	default long clock() {
		return level().getServer().overworld().getOverworldClockTime();
	}

	/** Live (adopted) settlements: those that exist as records and are simulated. */
	Collection<? extends Settlement> settlements();

	@Nullable Settlement settlement(long id);

	/** The settlement whose bounds contain {@code pos} (with a small margin), if any. */
	@Nullable Settlement settlementAt(BlockPos pos);

	/** The nearest live settlement to {@code pos} within {@code maxDistance} blocks of its center, if any. */
	@Nullable Settlement nearest(BlockPos pos, int maxDistance);

	<T> T data(HearthDataKey<T> key);

	void markDirty();

	/** The open spaces under the ground (Expanse realms), or null where the world has no such model. */
	default @Nullable UndergroundProbe underground() {
		return null;
	}

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

	/** Caravans on the road between live settlements now, soonest arrival first (LIVING_WORLD.md §7.6). */
	default List<CaravanView> caravans() {
		return List.of();
	}

	default @Nullable CaravanView caravan(long id) {
		for (CaravanView c : caravans()) {
			if (c.id() == id) {
				return c;
			}
		}
		return null;
	}

	/** An escort rides with the caravan: whatever the road held for it, it arrives. Returns whether it was on the road. */
	default boolean escortCaravan(long id) {
		return false;
	}

	/**
	 * The caravan is robbed now: its cargo is gone, the buyer pays nothing, {@code CARAVAN ROBBED} fires. Returns the cargo
	 * taken (by goods ordinal), or null if it was not on the road. Report the crime to the law yourself.
	 */
	default int @Nullable [] robCaravan(long id) {
		return null;
	}

	/**
	 * The caravan's party was overcome on the road near a player (killed, scattered): it is lost with its cargo
	 * ({@code CARAVAN ROBBED} if {@code robbedBy} is set, else {@code LOST}).
	 */
	default void caravanLost(long id, java.util.@Nullable UUID robbedBy) {
	}

	/**
	 * Whether every recorded step of the plot lying in this loaded chunk is in the world (the chunk applier has caught up),
	 * so a comparison of the plot with the world is fair. True where there is nothing to apply.
	 */
	default boolean written(Settlement settlement, int plotId, int chunkX, int chunkZ) {
		return true;
	}

	/**
	 * A caravan on the road.
	 *
	 * @param cargo    goods by ordinal
	 * @param value    bits the buyer pays on arrival
	 * @param escorted a player rides with it (an escort quest, whose own train of pack animals is the caravan on the road:
	 *                 the npc module does not put a second party there)
	 */
	record CaravanView(long id, long from, long to, int[] cargo, long value, long departTick, long arriveTick, BlockPos fromPos, BlockPos toPos,
		boolean escorted) {
		/** Where it is at a game tick (straight between the towns, at its pace). */
		public BlockPos positionAt(long tick) {
			double t = Math.clamp((tick - this.departTick) / (double) Math.max(1, this.arriveTick - this.departTick), 0.0, 1.0);
			return BlockPos.containing(this.fromPos.getX() + (this.toPos.getX() - this.fromPos.getX()) * t,
				this.fromPos.getY() + (this.toPos.getY() - this.fromPos.getY()) * t, this.fromPos.getZ() + (this.toPos.getZ() - this.fromPos.getZ()) * t);
		}

		/** How far along the road it is, 0 to 1. */
		public double progress(long tick) {
			return Math.clamp((tick - this.departTick) / (double) Math.max(1, this.arriveTick - this.departTick), 0.0, 1.0);
		}
	}

	/**
	 * @param settlementId the live settlement's id, or the id it will have when adopted
	 * @param live         whether it has been adopted (is in {@link #settlements()})
	 */
	record SiteInfo(long settlementId, String name, Culture culture, BlockPos center, SettlementTier tier, boolean live) {
	}
}
