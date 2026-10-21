package dev.voidmc.hearth.economy.household;

import dev.voidmc.hearth.api.Resident;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.Nullable;

/**
 * A resident keeping a player's house (docs/LIVING_WORLD_2.md §8.2): {@code extra("servant")}, saved with their town. A view
 * over the tag; pure.
 */
public final class ServantState {
	public static final String KEY = "servant";

	private final CompoundTag tag;

	private ServantState(CompoundTag tag) {
		this.tag = tag;
	}

	public static @Nullable ServantState of(Resident r) {
		CompoundTag t = r.extra.getCompoundOrEmpty(KEY);
		return t.getString("player").isPresent() ? new ServantState(t) : null;
	}

	public static boolean serving(Resident r) {
		return r.extra.getCompoundOrEmpty(KEY).getString("player").isPresent();
	}

	public static ServantState hire(Resident r, UUID player, int plot, long day, long wage) {
		CompoundTag t = new CompoundTag();
		t.putString("player", player.toString());
		t.putInt("plot", plot);
		t.putLong("since", day);
		t.putLong("wage", wage);
		t.putLong("paid", day);
		t.putInt("unpaid", 0);
		r.extra.put(KEY, t);
		return new ServantState(t);
	}

	public static void release(Resident r) {
		r.extra.remove(KEY);
	}

	/** Whoever holds the house's deed: they serve the house, and so its owner. */
	public UUID player() {
		return UUID.fromString(this.tag.getStringOr("player", new UUID(0, 0).toString()));
	}

	public void setPlayer(UUID player) {
		this.tag.putString("player", player.toString());
	}

	public int plot() {
		return this.tag.getIntOr("plot", Resident.NO_PLOT);
	}

	public long since() {
		return this.tag.getLongOr("since", 0);
	}

	public long wage() {
		return this.tag.getLongOr("wage", 0);
	}

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
}
