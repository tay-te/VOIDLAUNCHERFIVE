package dev.voidmc.hearth.social.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.item.Item;

/**
 * A short paid job a player took in a settlement: bring in part of the harvest, or carry a load of stone from the
 * quarry to the builders. Held in memory for as long as it lasts (minutes); see {@link WageTasks}.
 *
 * @param giverName who gave it, for the HUD line
 * @param deadline  game time it must be done by
 */
public final class WageTask {
	public enum Kind {
		HARVEST, HAUL
	}

	public final Kind kind;
	public final long settlementId;
	public final UUID giver;
	public final String giverName;
	public final int target;
	public final long deadline;
	/** HARVEST: crops brought in so far; HAUL: unused. */
	public int progress;
	/** HARVEST: what the crops gave, by produce item, so the hand-in asks for what was gathered. */
	public final Map<Item, Integer> produce = new HashMap<>();
	/** HAUL: the item carried. */
	public final Item load;

	public WageTask(Kind kind, long settlementId, UUID giver, String giverName, int target, long deadline, Item load) {
		this.kind = kind;
		this.settlementId = settlementId;
		this.giver = giver;
		this.giverName = giverName;
		this.target = target;
		this.deadline = deadline;
		this.load = load;
	}

	public boolean done() {
		return this.kind == Kind.HARVEST && this.progress >= this.target;
	}

	/** Wage for a harvest hand-in of {@code handed} crops (never more than were brought in), or a haul. */
	public static long wage(Kind kind, int handed, int progress, int perCrop, int perLoad) {
		return kind == Kind.HARVEST ? (long) Math.min(handed, progress) * perCrop : perLoad;
	}
}
