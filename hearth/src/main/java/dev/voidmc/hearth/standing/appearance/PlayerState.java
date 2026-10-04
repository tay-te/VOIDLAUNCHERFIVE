package dev.voidmc.hearth.standing.appearance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The little the standing module remembers about a player's body (attachment {@code hearth:standing/player}): when they
 * last washed (a swim, rain does not count), when they last had a proper bath, and when they last got blood on them.
 * Game times in ticks; {@link Long#MIN_VALUE} for never. Immutable; changes attach a new value.
 */
public record PlayerState(long washed, long bathed, long blooded) {
	public static final PlayerState FRESH = new PlayerState(Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE);

	public static final Codec<PlayerState> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.LONG.optionalFieldOf("washed", Long.MIN_VALUE).forGetter(PlayerState::washed),
		Codec.LONG.optionalFieldOf("bathed", Long.MIN_VALUE).forGetter(PlayerState::bathed),
		Codec.LONG.optionalFieldOf("blooded", Long.MIN_VALUE).forGetter(PlayerState::blooded)
	).apply(i, PlayerState::new));

	public PlayerState washedAt(long time) {
		return new PlayerState(time, this.bathed, this.blooded);
	}

	public PlayerState bathedAt(long time) {
		return new PlayerState(time, time, this.blooded);
	}

	public PlayerState bloodiedAt(long time) {
		return new PlayerState(this.washed, this.bathed, time);
	}

	/** 1 just washed … 0 after {@code dirtyAfterTicks} without washing. A player never seen washing counts as fairly clean. */
	public double cleanliness(long now, long dirtyAfterTicks) {
		if (this.washed == Long.MIN_VALUE) {
			return 0.7;
		}
		long since = Math.max(0, now - this.washed);
		return Math.max(0, 1 - since / (double) Math.max(1, dirtyAfterTicks));
	}

	public boolean bathed(long now, long bonusTicks) {
		return this.bathed != Long.MIN_VALUE && now - this.bathed < bonusTicks;
	}

	/** Blood shows until it is washed off or {@code bloodTicks} pass. */
	public boolean bloodied(long now, long bloodTicks) {
		return this.blooded != Long.MIN_VALUE && now - this.blooded < bloodTicks && (this.washed == Long.MIN_VALUE || this.washed < this.blooded);
	}
}
