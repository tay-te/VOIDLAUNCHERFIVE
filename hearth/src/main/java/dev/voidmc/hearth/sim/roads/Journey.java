package dev.voidmc.hearth.sim.roads;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.api.TravelService.TravelKind;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;

/**
 * A journey on the roads (docs/LIVING_WORLD_2.md §0.2): who goes, from where to where, along which roads, when they set
 * out and at what pace. Where the party is at any tick is a pure function of this record ({@link #along}): it walks by
 * day and stops from dusk to dawn, so a party is always exactly where the books say, whether anyone is watching or not.
 *
 * @param path      the roads in order, each id negated where it is walked from its {@code b} end
 * @param length    blocks along the whole path (out, for a round trip)
 * @param depart    on the sim clock
 * @param pace      blocks a tick while walking
 * @param arrive    when it ends (back home, for a round trip)
 * @param roundTrip out along the path and back again (patrols, carters, pilgrims, market goers)
 * @param turn      for a round trip, the tick it turns back (it stays at the far end until then)
 * @param waylay    the tick it is waylaid by bandits (decided in the books), or {@link Long#MAX_VALUE}
 * @param band      the band that waylays it, or 0
 * @param outcome   what the band does: {@code robbed}, {@code killed}, {@code driven_off}, or ""
 */
public record Journey(long id, TravelKind kind, long from, long to, long[] path, double length, long depart, double pace, long arrive,
	List<UUID> residents, boolean roundTrip, long turn, long waylay, long band, String outcome, CompoundTag details) {

	/** Minecraft's day: walking from dawn ({@link #DAWN}) to dusk ({@link #DUSK}), stopped between. */
	public static final long DAY = 24000;
	/** 5:00 in the morning. */
	public static final long DAWN = 23000;
	/** Half past six in the evening. */
	public static final long DUSK = 12500;
	/** Walking ticks in a day. */
	public static final long WALKING = DAY - DAWN + DUSK;

	static final Codec<TravelKind> KIND = Codec.STRING.xmap(s -> {
		for (TravelKind k : TravelKind.values()) {
			if (k.name().equalsIgnoreCase(s)) {
				return k;
			}
		}
		return TravelKind.PEDLAR;
	}, k -> k.name().toLowerCase(java.util.Locale.ROOT));

	public static final Codec<Journey> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.LONG.fieldOf("id").forGetter(Journey::id),
		KIND.fieldOf("kind").forGetter(Journey::kind),
		Codec.LONG.fieldOf("from").forGetter(Journey::from),
		Codec.LONG.fieldOf("to").forGetter(Journey::to),
		Codec.LONG_STREAM.xmap(LongStream::toArray, LongStream::of).fieldOf("path").forGetter(Journey::path),
		Codec.DOUBLE.fieldOf("length").forGetter(Journey::length),
		Codec.LONG.fieldOf("depart").forGetter(Journey::depart),
		Codec.DOUBLE.fieldOf("pace").forGetter(Journey::pace),
		Codec.LONG.fieldOf("arrive").forGetter(Journey::arrive),
		UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("residents", List.of()).forGetter(Journey::residents),
		Codec.BOOL.optionalFieldOf("round_trip", false).forGetter(Journey::roundTrip),
		Codec.LONG.optionalFieldOf("turn", 0L).forGetter(Journey::turn),
		Codec.LONG.optionalFieldOf("waylay", Long.MAX_VALUE).forGetter(Journey::waylay),
		Codec.LONG.optionalFieldOf("band", 0L).forGetter(Journey::band),
		Codec.STRING.optionalFieldOf("outcome", "").forGetter(Journey::outcome),
		CompoundTag.CODEC.optionalFieldOf("details", new CompoundTag()).forGetter(Journey::details)
	).apply(i, Journey::new));

	// ---------------------------------------------------------------- time on the road (pure)

	/** Whether a tick of the day is walking time. */
	public static boolean walking(long tick) {
		long t = Math.floorMod(tick, DAY);
		return t >= DAWN || t < DUSK;
	}

	/** Walking ticks from tick 0 to {@code tick} (the clock's own day: 0 is six in the morning). */
	static long walkedSinceZero(long tick) {
		long days = Math.floorDiv(tick, DAY);
		long t = Math.floorMod(tick, DAY);
		// each day walks [0, DUSK) and [DAWN, DAY)
		long within = Math.min(t, DUSK) + Math.max(0, t - DAWN);
		return days * WALKING + within;
	}

	/** Walking ticks between two ticks. */
	public static long walkingBetween(long from, long to) {
		return to <= from ? 0 : walkedSinceZero(to) - walkedSinceZero(from);
	}

	/** The tick by which {@code walk} walking ticks have passed after {@code from}. */
	public static long afterWalking(long from, long walk) {
		long target = walkedSinceZero(from) + Math.max(0, walk);
		long days = Math.floorDiv(target, WALKING);
		long within = target - days * WALKING;
		long t = within <= DUSK ? within : DAWN + (within - DUSK);
		long at = days * DAY + t;
		return Math.max(from, at);
	}

	/** Blocks along the path at a tick (back toward the start after the turn, for a round trip). */
	public double along(long tick) {
		if (tick <= this.depart) {
			return 0;
		}
		if (!this.roundTrip) {
			return Math.min(this.length, this.pace * walkingBetween(this.depart, Math.min(tick, this.arrive)));
		}
		double out = Math.min(this.length, this.pace * walkingBetween(this.depart, tick));
		if (tick <= this.turn) {
			return out;
		}
		double back = this.pace * walkingBetween(this.turn, Math.min(tick, this.arrive));
		return Math.max(0, this.length - back);
	}

	/** Whether it is walking back home (a round trip after its turn). */
	public boolean returning(long tick) {
		return this.roundTrip && tick > this.turn;
	}

	/** Whether it is stopped for the night (or at the far end of a round trip, waiting for its turn). */
	public boolean resting(long tick) {
		return !walking(tick) || this.roundTrip && tick < this.turn && along(tick) >= this.length - 1e-6;
	}

	/** When a journey of {@code length} blocks at {@code pace} leaving at {@code depart} arrives (night stops included). */
	public static long arrival(long depart, double length, double pace) {
		return afterWalking(depart, (long) Math.ceil(length / Math.max(1e-6, pace)));
	}

	/** The pace that takes {@code length} blocks from {@code depart} to {@code arrive} (the books' arrival). */
	public static double paceFor(long depart, long arrive, double length) {
		long walk = walkingBetween(depart, arrive);
		return walk <= 0 ? length : length / walk;
	}

	public Journey withWaylay(long tick, long band, String outcome) {
		return new Journey(this.id, this.kind, this.from, this.to, this.path, this.length, this.depart, this.pace, this.arrive, this.residents, this.roundTrip,
			this.turn, tick, band, outcome, this.details);
	}

	public Journey withArrive(long arrive) {
		return new Journey(this.id, this.kind, this.from, this.to, this.path, this.length, this.depart, this.pace, arrive, this.residents, this.roundTrip,
			this.turn, this.waylay, this.band, this.outcome, this.details);
	}

	/** Paces in blocks a tick (§1.2): walking 0.9 blocks a second, carts 0.8, riders 2.4. */
	public static final double WALK = 0.9 / 20;
	public static final double CART = 0.8 / 20;
	public static final double RIDE = 2.4 / 20;
}
