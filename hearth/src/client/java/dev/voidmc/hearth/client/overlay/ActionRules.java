package dev.voidmc.hearth.client.overlay;

import dev.voidmc.hearth.economy.bank.BankView;
import dev.voidmc.hearth.economy.market.MarketView;
import dev.voidmc.hearth.economy.net.EconomyActionPayload;
import dev.voidmc.hearth.law.net.LawPayloads;
import dev.voidmc.hearth.npc.talk.TalkPayloads;
import dev.voidmc.hearth.quest.model.PlayerQuests;
import dev.voidmc.hearth.quest.net.QuestPayloads;
import dev.voidmc.hearth.social.dice.DiceGame;
import dev.voidmc.hearth.social.dice.DiceView;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The checks {@link HearthActions} makes before sending anything: a page's call that cannot be right for what the
 * screen shows (a locked option, a row that is not there, a negative offer, a die out of range) sends nothing. They are
 * only a courtesy: the server checks everything again (ownership, distance, coin, standing). Pure.
 */
public final class ActionRules {
	/** The largest amount of bits one call may name (a malformed page cannot ask for more than a world could hold). */
	public static final long MAX_BITS = 1_000_000_000L;

	private ActionRules() {
	}

	/** An option shown in the open conversation and not locked. */
	public static boolean pickable(TalkPayloads.@Nullable Show show, @Nullable String id) {
		if (show == null || id == null) {
			return false;
		}
		for (TalkPayloads.Option o : show.options()) {
			if (o.id().equals(id)) {
				return !o.locked();
			}
		}
		return false;
	}

	/** The open conversation's training option for a skill ({@code mettle:speech} or {@code speech}), or null. */
	public static @Nullable String trainingOption(TalkPayloads.@Nullable Show show, TalkPayloads.@Nullable Detail detail, @Nullable String skill) {
		if (show == null || skill == null || skill.isBlank()) {
			return null;
		}
		String full = skill.contains(":") ? skill : "mettle:" + skill;
		for (TalkPayloads.Option o : show.options()) {
			TalkPayloads.OptionInfo info = detail == null ? null : detail.info(o.id());
			boolean lesson = o.id().equals("mettle:train/" + full.substring(full.indexOf(':') + 1))
				|| info != null && "train".equals(info.kind()) && full.equals(info.skill()) && !o.id().startsWith("mettle:teach/")
					&& !o.id().startsWith("mettle:rebuild/");
			if (lesson && !o.locked()) {
				return o.id();
			}
		}
		return null;
	}

	/** A row the market shows now (to buy from or sell to). */
	public static boolean marketRow(@Nullable MarketView view, int row) {
		if (view == null) {
			return false;
		}
		return view.sells().stream().anyMatch(o -> o.id() == row) || view.buys().stream().anyMatch(o -> o.id() == row);
	}

	/** An offer for the deal under haggle: a haggle is open and the bits are 0…{@link #MAX_BITS}. */
	public static boolean haggleOffer(@Nullable MarketView view, long bits) {
		return view != null && view.haggle() != null && bits >= 0 && bits <= MAX_BITS;
	}

	/** An amount of bits to deposit, withdraw or give: positive (at most {@link #MAX_BITS}), or "all". */
	public static boolean amount(long bits) {
		return bits == EconomyActionPayload.ALL || bits > 0 && bits <= MAX_BITS;
	}

	/** A plot the bank lists now. */
	public static boolean plot(@Nullable BankView view, int plot) {
		return view != null && (view.plots().stream().anyMatch(p -> p.plot() == plot) || view.projects().stream().anyMatch(p -> p.plot() == plot));
	}

	/** A contract the bank lists now. */
	public static boolean contract(@Nullable BankView view, int id) {
		return view != null && view.contracts().stream().anyMatch(c -> c.id() == id);
	}

	/** A quest the board offers that this player may take (no lock). */
	public static boolean boardAccept(QuestPayloads.@Nullable Board board, @Nullable UUID quest) {
		if (board == null || quest == null) {
			return false;
		}
		return board.entries().stream().anyMatch(e -> e.quest().id.equals(quest) && e.lock().isEmpty());
	}

	/** A quest the board takes in. */
	public static boolean boardHandIn(QuestPayloads.@Nullable Board board, @Nullable UUID quest) {
		return board != null && quest != null && board.handIns().contains(quest);
	}

	/** One of the player's active quests. */
	public static boolean activeQuest(@Nullable PlayerQuests quests, @Nullable UUID quest) {
		return quests != null && quest != null && quests.get(quest) != null;
	}

	/** A confrontation choice shown and enabled. */
	public static boolean confrontChoice(LawPayloads.@Nullable Confront confront, @Nullable String id) {
		return confront != null && id != null && confront.choices().stream().anyMatch(c -> c.id().equals(id) && c.enabled());
	}

	/** A pocket slot holding something, while the pickpocketing is open. */
	public static boolean pocketSlot(LawPayloads.@Nullable Pocket pocket, int slot) {
		return pocket != null && pocket.state() == LawPayloads.Pocket.OPEN && slot >= 0 && slot < pocket.items().size()
			&& !pocket.items().get(slot).isEmpty();
	}

	/** One of the offered stakes, while the stake is being chosen. */
	public static boolean diceStake(@Nullable DiceView view, int index) {
		return view != null && view.phaseEnum() == DiceGame.Phase.STAKE && index >= 0 && index < view.stakes().size();
	}

	/** A die on the table that is not set aside, after the player's roll. */
	public static boolean diceToggle(@Nullable DiceView view, int die) {
		return view != null && view.phaseEnum() == DiceGame.Phase.PLAYER_ROLLED && die >= 0 && die < view.dice().length
			&& (view.keptMask() & 1 << die) == 0;
	}

	/** A dice call by phase: rolling and banking when the table allows it, passing after a bust, again when it is over. */
	public static boolean dice(@Nullable DiceView view, int action) {
		if (view == null) {
			return false;
		}
		DiceGame.Phase phase = view.phaseEnum();
		return switch (action) {
			case dev.voidmc.hearth.social.dice.DicePayloads.Action.ROLL -> view.canRoll();
			case dev.voidmc.hearth.social.dice.DicePayloads.Action.BANK -> view.canBank();
			case dev.voidmc.hearth.social.dice.DicePayloads.Action.PASS -> phase == DiceGame.Phase.PLAYER_BUST;
			case dev.voidmc.hearth.social.dice.DicePayloads.Action.AGAIN -> phase == DiceGame.Phase.OVER;
			case dev.voidmc.hearth.social.dice.DicePayloads.Action.LOADED -> view.loaded() > 0 && phase != DiceGame.Phase.OVER;
			default -> false;
		};
	}
}
