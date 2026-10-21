package dev.voidmc.hearth.npc.companion;

import dev.voidmc.hearth.api.Resident;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.jspecify.annotations.Nullable;

/**
 * A resident hired as a player's companion (docs/LIVING_WORLD_2.md §8.2): {@code extra("companion")} on the resident, saved
 * with their settlement. A view over the tag; changes go straight into it (the caller marks the settlement dirty).
 * Pure: no game state, so the wage cycle and the orders are unit-tested on plain residents.
 */
public final class CompanionState {
	public static final String KEY = "companion";

	/** Where they keep to: at the player's heels, a few blocks back, or where they were told to wait. */
	public enum Follow {
		FOLLOW, DISTANCE, WAIT;

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		static Follow byId(String id) {
			for (Follow f : values()) {
				if (f.id().equals(id)) {
					return f;
				}
			}
			return FOLLOW;
		}
	}

	private final CompoundTag tag;

	private CompanionState(CompoundTag tag) {
		this.tag = tag;
	}

	/** The resident's companion state, or null when they serve nobody. */
	public static @Nullable CompanionState of(Resident r) {
		CompoundTag t = r.extra.getCompoundOrEmpty(KEY);
		return t.getString("player").isPresent() ? new CompanionState(t) : null;
	}

	/** Whether the resident is anyone's companion. */
	public static boolean hired(Resident r) {
		return r.extra.getCompoundOrEmpty(KEY).getString("player").isPresent();
	}

	/** Makes the resident a companion of {@code player} from {@code day}, at {@code wage} bits a day (paid for the first day). */
	public static CompanionState hire(Resident r, UUID player, long day, long wage) {
		CompoundTag t = new CompoundTag();
		t.putString("player", player.toString());
		t.putLong("since", day);
		t.putLong("wage", wage);
		t.putLong("paid", day);
		t.putInt("unpaid", 0);
		t.putString("follow", Follow.FOLLOW.id());
		t.putBoolean("fight", true);
		t.putInt("loyalty", 0);
		r.extra.put(KEY, t);
		return new CompanionState(t);
	}

	/** The resident serves nobody any more (their gear is handed back or dropped by the caller first). */
	public static void release(Resident r) {
		r.extra.remove(KEY);
	}

	public UUID player() {
		return UUID.fromString(this.tag.getStringOr("player", new UUID(0, 0).toString()));
	}

	public long since() {
		return this.tag.getLongOr("since", 0);
	}

	public long wage() {
		return this.tag.getLongOr("wage", 0);
	}

	/** The last sim day of their home town they were paid for. */
	public long paidDay() {
		return this.tag.getLongOr("paid", 0);
	}

	public void setPaidDay(long day) {
		this.tag.putLong("paid", day);
	}

	public int unpaid() {
		return this.tag.getIntOr("unpaid", 0);
	}

	public void setUnpaid(int days) {
		this.tag.putInt("unpaid", days);
	}

	public Follow follow() {
		return Follow.byId(this.tag.getStringOr("follow", "follow"));
	}

	public void setFollow(Follow f) {
		this.tag.putString("follow", f.id());
	}

	/** Fight what the player fights (true) or hold back (false). */
	public boolean fight() {
		return this.tag.getBooleanOr("fight", true);
	}

	public void setFight(boolean fight) {
		this.tag.putBoolean("fight", fight);
	}

	/** Where they were told to wait (packed block pos), or null. */
	public net.minecraft.core.@Nullable BlockPos waitAt() {
		return this.tag.getLong("wait").map(net.minecraft.core.BlockPos::of).orElse(null);
	}

	public void setWaitAt(net.minecraft.core.@Nullable BlockPos pos) {
		if (pos == null) {
			this.tag.remove("wait");
		} else {
			this.tag.putLong("wait", pos.asLong());
		}
	}

	/** How they feel about serving, −100…100. */
	public int loyalty() {
		return this.tag.getIntOr("loyalty", 0);
	}

	public void addLoyalty(int delta) {
		this.tag.putInt("loyalty", Math.max(-100, Math.min(100, loyalty() + delta)));
	}

	/** Game tick since which the player has been out of their reach (another dimension), or -1. */
	public long apartSince() {
		return this.tag.getLongOr("apart", -1L);
	}

	public void setApartSince(long tick) {
		if (tick < 0) {
			this.tag.remove("apart");
		} else {
			this.tag.putLong("apart", tick);
		}
	}

	/** The gear the player gave them (item stacks as saved by {@link CompanionGear}). */
	public ListTag gear() {
		return this.tag.getListOrEmpty("gear");
	}

	public void setGear(ListTag gear) {
		if (gear.isEmpty()) {
			this.tag.remove("gear");
		} else {
			this.tag.put("gear", gear);
		}
	}

	/** Game tick of their last remark on the road (a companion talks now and then, not all the time). */
	public long lastRemark() {
		return this.tag.getLongOr("remark", 0L);
	}

	public void setLastRemark(long tick) {
		this.tag.putLong("remark", tick);
	}
}
