package dev.voidmc.hearth.npc.crowd;

import dev.voidmc.hearth.Hearth;
import java.util.Locale;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * What a folk does about a stimulus (docs/LIVING_WORLD_2.md §0.3 bands 200–239): each is a folk behaviour at its own
 * priority, so a scatter outranks a gawk and both yield to witnesses (250), guards on duty (320) and the routine's own
 * flight from monsters.
 */
public enum Reaction {
	/** Stop, turn, wave; a few cheer; children follow. */
	HAIL(205, 12),
	/** A remark (a guard on armour, a merchant eyeing it, a word on blood); a guard may walk over first. */
	REMARK(208, 10),
	/** Gather in a ring at 4–6 blocks (the cautious further) and watch. */
	GAWK(215, 16),
	/** Cross the lane away from a distrusted player, muttering. */
	MUTTER(218, 10),
	/** Run indoors and shut the door behind them. */
	SHUN(220, 16),
	/** Step back 4–8 blocks from a drawn weapon; mothers pull children close. */
	SCATTER(225, 8);

	public static final Reaction[] VALUES = values();

	private final int priority;
	private final double range;

	Reaction(int priority, double range) {
		this.priority = priority;
		this.range = range;
	}

	public int priority() {
		return this.priority;
	}

	/** Folk further from the player than this do not react this way. */
	public double range() {
		return this.range;
	}

	public Identifier id() {
		return Hearth.id("crowd/" + name().toLowerCase(Locale.ROOT));
	}

	/** How a folk in a role reacts to a stimulus at a distance, or null if they let it pass. */
	public static @Nullable Reaction of(Stimulus s, Role role, double distance) {
		Reaction r = switch (s) {
			case OUTLAW -> role == Role.GUARD ? null : SHUN; // guards attack outlaws on sight (the law module)
			case BODY -> role == Role.GUARD ? REMARK : GAWK;
			case FIGHT -> role == Role.GUARD ? null : GAWK;
			case BLADE -> role == Role.GUARD ? REMARK : SCATTER;
			case HERO -> HAIL;
			case BLOODIED, PROWL, FRIEND -> REMARK;
			case ARMOUR -> role == Role.GUARD || role == Role.MERCHANT ? REMARK : null;
			case DISTRUSTED -> role == Role.GUARD ? null : MUTTER;
		};
		if (r == null) {
			return null;
		}
		double range = r == REMARK && role == Role.GUARD ? 16 : r.range;
		return distance <= range ? r : null;
	}

	/** At most this many folk react to one player's stimulus this way at once. */
	public static int limit(Stimulus s, Reaction r) {
		return switch (r) {
			case REMARK -> 1;
			case HAIL -> 6;
			case MUTTER -> 4;
			case GAWK -> s == Stimulus.BODY ? 8 : 12;
			case SHUN, SCATTER -> 12;
		};
	}
}
