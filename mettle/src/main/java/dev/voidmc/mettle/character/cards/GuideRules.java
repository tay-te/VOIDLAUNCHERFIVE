package dev.voidmc.mettle.character.cards;

import dev.voidmc.mettle.core.BuildState.Guide;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The guide for new characters (docs/BUILD_FEEL.md §4) as a state machine free of players and worlds: ten short hints,
 * one at a time, each once, never in a fight (5 s calm), each stepping aside when the player does the thing it points at
 * (or on ×, or after its time). Only characters made after build feel shipped get it; Skip turns it off.
 */
public final class GuideRules {
	/** How long a timed hint stays up, in game ticks (6 s). */
	public static final long SHORT = 120;
	/** The longest any hint stays up (the Focus offer waits for a button, but not for ever). */
	public static final long LONGEST = 1200;
	/** Calm: no harm taken for this many ticks (5 s). */
	public static final long CALM = 100;
	public static final int HINTS = 10;

	private GuideRules() {
	}

	/** The ten hints and what each is anchored to (for the overlay, OVERLAY.md "Build feel"). */
	public enum Hint {
		XP("xp_chip"), HAND("hand"), KEEP_SLOT("keep_slot"), SLOTTED("deck_row"), KEYWORD("keyword_badge"), MAKES_SET("card_preview"),
		FIRST_SET("set_tracker"), CANT_SLOT("card"), SWAPS("free_swaps"), BOON("boon_badge");

		public final String anchor;

		Hint(String anchor) {
			this.anchor = anchor;
		}

		/** 1–10, as BUILD_FEEL.md §4 numbers them. */
		public int number() {
			return ordinal() + 1;
		}

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Hint of(int number) {
			return number >= 1 && number <= HINTS ? values()[number - 1] : null;
		}
	}

	/**
	 * What the character is doing now, read from its state each second.
	 *
	 * @param anyXp            a skill has gained XP
	 * @param handOpen         a draft hand is open
	 * @param cardsOwned       cards owned (any place)
	 * @param slotted          cards slotted
	 * @param keywordHovered   the overlay reported a hover on a keyword badge (guideAdvance "keyword")
	 * @param handMakesSet     no set is active yet and a card in an open hand would make the first one
	 * @param anySet           a set is active
	 * @param cantSlot         a binder card needs a higher skill than the character has
	 * @param fullDeckBetter   a deck is full and its binder holds a card that could go in
	 * @param boons            boons waiting
	 * @param calm             no harm taken in the last 5 s
	 */
	public record Facts(boolean anyXp, boolean handOpen, int cardsOwned, int slotted, boolean keywordHovered, boolean handMakesSet, boolean anySet,
		boolean cantSlot, boolean fullDeckBetter, int boons, boolean calm) {
	}

	/** Whether a hint's moment has come. */
	static boolean due(Hint h, Guide g, Facts f) {
		return switch (h) {
			case XP -> f.anyXp();
			case HAND -> f.handOpen() && f.cardsOwned() == 0;
			case KEEP_SLOT -> g.seen(Hint.HAND.number()) && f.handOpen() && f.cardsOwned() == 0;
			case SLOTTED -> f.slotted() > 0;
			case KEYWORD -> f.keywordHovered();
			case MAKES_SET -> f.handMakesSet();
			case FIRST_SET -> f.anySet();
			case CANT_SLOT -> f.cantSlot();
			case SWAPS -> f.fullDeckBetter();
			case BOON -> f.boons() > 0;
		};
	}

	/** Whether the hint up now has done its job (the player did the thing), so it steps aside. */
	static boolean done(Hint h, Facts f, long age) {
		if (age >= LONGEST) {
			return true;
		}
		return switch (h) {
			case XP, SLOTTED, KEYWORD, CANT_SLOT, SWAPS -> age >= SHORT;
			case HAND -> f.cardsOwned() > 0 || !f.handOpen() || age >= SHORT;
			case KEEP_SLOT -> f.cardsOwned() > 0 || !f.handOpen();
			case MAKES_SET -> !f.handMakesSet();
			case FIRST_SET -> false;
			case BOON -> f.boons() == 0;
		};
	}

	/** One step at game time {@code now}: the hint up steps aside when done; then, if calm, the next due hint comes up. */
	public static Guide step(Guide g, Facts f, long now) {
		if (!g.on() || g.skipped()) {
			return g.current() == 0 ? g : with(g, 0, g.since());
		}
		Hint up = Hint.of(g.current());
		if (up != null && done(up, f, now - g.since())) {
			g = with(g, 0, now);
			up = null;
		}
		if (up != null || !f.calm()) {
			return g;
		}
		for (Hint h : Hint.values()) {
			if (!g.seen(h.number()) && due(h, g, f)) {
				List<Integer> seen = new ArrayList<>(g.seen());
				seen.add(h.number());
				return new Guide(true, seen, h.number(), now, false, g.firstHand());
			}
		}
		return g;
	}

	/** The player answered the hint up ("×", the Focus offer's buttons, the Deck opened, a badge unhovered): it goes. */
	public static Guide dismiss(Guide g, long now) {
		return g.current() == 0 ? g : with(g, 0, now);
	}

	/** "Skip the guide": every hint off for good. */
	public static Guide skip(Guide g) {
		return new Guide(g.on(), g.seen(), 0, g.since(), true, g.firstHand());
	}

	/**
	 * The guide for a character seen for the first time since build feel: on only for a new one (no skill XP, no cards);
	 * a migrated character and anyone with three cards slotted never sees it.
	 */
	public static Guide decide(double totalXp, int cards, int slotted) {
		boolean fresh = totalXp <= 0 && cards == 0 && slotted < 3;
		return new Guide(fresh, List.of(), 0, 0, false, !fresh);
	}

	private static Guide with(Guide g, int current, long since) {
		return new Guide(g.on(), g.seen(), current, since, g.skipped(), g.firstHand());
	}
}
