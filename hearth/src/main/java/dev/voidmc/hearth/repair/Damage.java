package dev.voidmc.hearth.repair;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.api.SettlementEvents;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * One damaged blueprint block waiting to be mended: where it is, which plot and stage it belongs to, what did it, and
 * where it stands in the repair pipeline. Saved with the settlement (in {@link RepairQueue}); claims and caches are not.
 */
public final class Damage {
	/** Mended in the record by abstract labour; written into the world when its chunk is loaded. */
	public static final int MENDED = 1;
	/** On a deeded plot: waits for its owner to pay (docs/LIVING_WORLD.md §2). */
	public static final int NEEDS_PAYMENT = 2;
	/** The owner paid; it is mended like any other. */
	public static final int PAID = 4;
	/** The blueprint has air here: a block left in a doorway or on a street, to be cleared. */
	public static final int LITTER = 8;
	/** The law has been told about it already. */
	public static final int REPORTED = 16;

	static final Codec<SettlementEvents.Damaged.Cause> CAUSE_CODEC = Codec.STRING.xmap(
		s -> {
			try {
				return SettlementEvents.Damaged.Cause.valueOf(s.toUpperCase(java.util.Locale.ROOT));
			} catch (IllegalArgumentException e) {
				return SettlementEvents.Damaged.Cause.OTHER;
			}
		},
		c -> c.name().toLowerCase(java.util.Locale.ROOT));

	public static final Codec<Damage> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.LONG.fieldOf("pos").forGetter(d -> d.pos),
		Codec.INT.fieldOf("plot").forGetter(d -> d.plot),
		Codec.INT.fieldOf("stage").forGetter(d -> d.stage),
		Codec.INT.optionalFieldOf("index", -1).forGetter(d -> d.index),
		CAUSE_CODEC.optionalFieldOf("cause", SettlementEvents.Damaged.Cause.OTHER).forGetter(d -> d.cause),
		Codec.LONG.optionalFieldOf("day", 0L).forGetter(d -> d.day),
		UUIDUtil.STRING_CODEC.optionalFieldOf("offender").forGetter(d -> Optional.ofNullable(d.offender)),
		Codec.INT.optionalFieldOf("flags", 0).forGetter(d -> d.flags)
	).apply(i, (pos, plot, stage, index, cause, day, offender, flags) -> new Damage(pos, plot, stage, index, cause, day, offender.orElse(null), flags)));

	public final long pos;
	public final int plot;
	public final int stage;
	/** The step's index within its stage, or -1 until it is looked up (the first time a builder claims it). */
	public int index;
	public final SettlementEvents.Damaged.Cause cause;
	/** The sim day it happened. */
	public final long day;
	/** The player who did it, if one did. */
	public final @Nullable UUID offender;
	public int flags;

	// not saved
	@Nullable UUID claimedBy;
	long claimedAt;
	long deferUntil;
	@Nullable BlockState expected;

	public Damage(long pos, int plot, int stage, int index, SettlementEvents.Damaged.Cause cause, long day, @Nullable UUID offender, int flags) {
		this.pos = pos;
		this.plot = plot;
		this.stage = stage;
		this.index = index;
		this.cause = cause;
		this.day = day;
		this.offender = offender;
		this.flags = flags;
	}

	public BlockPos blockPos() {
		return BlockPos.of(this.pos);
	}

	public boolean has(int flag) {
		return (this.flags & flag) != 0;
	}

	public void set(int flag, boolean on) {
		this.flags = on ? this.flags | flag : this.flags & ~flag;
	}

	/** Whether it is held back for its owner's coin. */
	public boolean awaitingPayment() {
		return has(NEEDS_PAYMENT) && !has(PAID);
	}

	/** Claimed by a builder whose claim has not run out. */
	public boolean claimed(long now, int claimTicks) {
		return this.claimedBy != null && now - this.claimedAt < claimTicks;
	}

	public @Nullable UUID claimedBy() {
		return this.claimedBy;
	}

	public long claimedAt() {
		return this.claimedAt;
	}

	/** The blueprint's block here, once looked up (not saved: the blueprint is the truth). */
	public @Nullable BlockState expected() {
		return this.expected;
	}

	public void setExpected(@Nullable BlockState state) {
		this.expected = state;
	}

	/** Keeps a builder's claim fresh while they work on it. */
	public void touch(long now) {
		this.claimedAt = now;
	}

	/** Returns it to the queue. */
	public void release() {
		this.claimedBy = null;
	}

	public void defer(long until) {
		this.deferUntil = until;
	}

	public boolean deferred(long now) {
		return now < this.deferUntil;
	}

	@Override
	public String toString() {
		return "Damage[" + blockPos().toShortString() + " plot " + this.plot + "/" + this.stage + ":" + this.index + ", " + this.cause + ", flags "
			+ this.flags + "]";
	}
}
