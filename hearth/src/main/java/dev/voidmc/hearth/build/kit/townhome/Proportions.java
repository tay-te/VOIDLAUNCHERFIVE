package dev.voidmc.hearth.build.kit.townhome;

import dev.voidmc.hearth.build.draft.Draft;

/**
 * How tall a small building may stand for its width (art review 22): a house or a workshop standing free reads as a
 * tower once it rises much more than one and a half times its frontage, so a narrow plot alone gets fewer storeys under
 * its roof. In a row (a party wall either side) the street carries the height and the rule does not apply.
 */
public final class Proportions {
	/** Ground to ridge, at most this many times the width, for a building that stands free. */
	public static final double DETACHED = 1.6;

	private Proportions() {
	}

	/** Neither side shares a wall: the building is seen whole. */
	public static boolean detached(Draft d) {
		return !d.partyLocalLeft() && !d.partyLocalRight();
	}

	/** The tallest, ground to ridge, a free-standing building {@code width} wide should stand. */
	public static int tallest(int width) {
		return (int) Math.floor(DETACHED * width);
	}

	/**
	 * How far the culture's roof rises over a span of {@code span} walls (gable to gable across it, the eaves included):
	 * the half span at the culture's pitch, rounded up.
	 */
	public static int rise(Draft d, int span) {
		return (int) Math.ceil((span / 2.0 + 0.5) * d.habits().roof().pitch());
	}

	/**
	 * Storeys of {@code sh} a building {@code width} wide may carry, at most {@code want} and at least one, so that with
	 * {@code over} more above them (a knee, the roof, a raised floor) it stands within {@link #tallest}; {@code want}
	 * unchanged when it shares a wall.
	 */
	public static int storeys(Draft d, int width, int want, int sh, int over) {
		if (!detached(d)) {
			return want;
		}
		return Math.clamp((tallest(width) - over) / sh, 1, Math.max(1, want));
	}
}
