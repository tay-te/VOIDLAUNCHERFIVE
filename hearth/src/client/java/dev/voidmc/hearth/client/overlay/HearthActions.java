package dev.voidmc.hearth.client.overlay;

import dev.voidmc.hearth.economy.bank.BankMenu;
import dev.voidmc.hearth.economy.market.MarketMenu;
import dev.voidmc.hearth.economy.net.EconomyActionPayload;
import dev.voidmc.hearth.law.net.LawPayloads;
import dev.voidmc.hearth.npc.talk.TalkPayloads;
import dev.voidmc.hearth.quest.net.QuestPayloads;
import dev.voidmc.hearth.quest.service.QuestStore;
import dev.voidmc.hearth.social.dice.DiceMenu;
import dev.voidmc.hearth.social.dice.DicePayloads;
import dev.voidmc.hearth.standing.net.StandingPayloads;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.Nullable;

/**
 * The calls a host's pages make (docs/OVERLAY.md §actions). Each one only sends a packet Hearth's own screens send; the
 * server re-validates everything (ownership, distance, coin, standing, whose turn). Before sending, {@link ActionRules}
 * drops a call that cannot be right for what is on screen. Every call returns whether something was sent. Arguments are
 * plain strings and numbers so a bridge can pass them through. Client thread.
 */
public final class HearthActions {
	private HearthActions() {
	}

	private static boolean send(CustomPacketPayload payload) {
		if (Minecraft.getInstance().getConnection() == null || !ClientPlayNetworking.canSend(payload.type())) {
			return false;
		}
		ClientPlayNetworking.send(payload);
		return true;
	}

	private static @Nullable LocalPlayer player() {
		return Minecraft.getInstance().player;
	}

	private static @Nullable UUID uuid(@Nullable String id) {
		if (id == null) {
			return null;
		}
		try {
			return UUID.fromString(id.trim());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	// ---------------------------------------------------------------- talk

	/** Picks an option of the open conversation by its id ({@code talk.options[].id}); locked ones send nothing. */
	public static boolean choose(String optionId) {
		TalkPayloads.Show show = HearthViews.talk();
		if (!ActionRules.pickable(show, optionId)) {
			return false;
		}
		return send(new TalkPayloads.Choose(show.entity(), optionId));
	}

	/**
	 * Pays and trains a skill with the trainer in the open conversation, inline (docs/HUB_UI.md decisions): picks their
	 * lesson option for {@code skill} ({@code mettle:speech} or {@code speech}); the lesson's fee is charged by the server.
	 */
	public static boolean train(String skill) {
		String id = ActionRules.trainingOption(HearthViews.talk(), HearthViews.talkDetail(), skill);
		return id != null && choose(id);
	}

	// ---------------------------------------------------------------- closing

	/**
	 * The host closed an event screen ({@code talk}, {@code confront}, {@code lockpick}, {@code pickpocket}, {@code market},
	 * {@code bank}, {@code dice}, {@code board}, {@code journal}, {@code reputation}): Hearth tells the server what its own
	 * screen would have (the conversation ends, a confrontation is walked away from, the lock or the pockets are left, the
	 * menu closes). It never closes a screen itself: call it from the host screen's close. Returns whether anything was sent.
	 */
	public static boolean close(String screen) {
		boolean sent = false;
		LocalPlayer player = player();
		switch (screen) {
			case HearthScreens.TALK -> {
				TalkPayloads.Show show = HearthViews.talk();
				if (show != null) {
					sent = send(new TalkPayloads.End(show.entity()));
					HearthViews.talkClosed(show.entity());
				}
			}
			case HearthScreens.CONFRONT -> {
				LawPayloads.Confront c = HearthViews.confront();
				if (c != null) {
					sent = send(new LawPayloads.ConfrontChoice(c.entity(), ""));
					HearthViews.confrontClosed(c.entity());
				}
			}
			case HearthScreens.LOCKPICK -> {
				HearthViews.Lockpick l = HearthViews.lockpick();
				if (l != null && "picking".equals(l.state())) {
					sent = send(new LawPayloads.LockpickEvent(l.start().session(), LawPayloads.LockpickEvent.CANCEL, 0));
				}
				HearthViews.lockpickClosed();
			}
			case HearthScreens.PICKPOCKET -> {
				LawPayloads.Pocket p = HearthViews.pocket();
				if (p != null && p.state() == LawPayloads.Pocket.OPEN) {
					sent = send(new LawPayloads.PocketAction(p.session(), LawPayloads.PocketAction.LEAVE, -1));
				}
				HearthViews.pocketClosed();
			}
			case HearthScreens.MARKET, HearthScreens.BANK, HearthScreens.DICE -> {
				if (player != null && (player.containerMenu instanceof MarketMenu || player.containerMenu instanceof BankMenu
					|| player.containerMenu instanceof DiceMenu)) {
					// as LocalPlayer.closeContainer, without closing the screen: the host closes its own
					var connection = Minecraft.getInstance().getConnection();
					if (connection != null) {
						connection.send(new net.minecraft.network.protocol.game.ServerboundContainerClosePacket(player.containerMenu.containerId));
						sent = true;
					}
					player.containerMenu = player.inventoryMenu;
				}
			}
			case HearthScreens.BOARD -> HearthViews.board(null);
			default -> {
			}
		}
		HearthScreens.ended(screen);
		return sent;
	}

	// ---------------------------------------------------------------- market

	private static @Nullable MarketMenu market() {
		LocalPlayer p = player();
		return p != null && p.containerMenu instanceof MarketMenu m ? m : null;
	}

	/** Buys or sells one lot of a row ({@code market.sells[].id} / {@code market.buys[].id}); {@code bulk}: a stack, or all you carry. */
	public static boolean trade(int row, boolean bulk) {
		MarketMenu m = market();
		if (m == null || !ActionRules.marketRow(m.view(), row)) {
			return false;
		}
		return send(new EconomyActionPayload(m.containerId, EconomyActionPayload.TRADE, row, bulk ? 1 : 0));
	}

	/** Starts haggling over a row (one lot, or {@code bulk} a stack); the merchant's opening word arrives as {@code market.haggle}. */
	public static boolean haggle(int row, boolean bulk) {
		MarketMenu m = market();
		if (m == null || !ActionRules.marketRow(m.view(), row)) {
			return false;
		}
		return send(new EconomyActionPayload(m.containerId, EconomyActionPayload.HAGGLE_START, row, bulk ? 1 : 0));
	}

	/** Names a price in bits for the whole deal under haggle. */
	public static boolean haggleOffer(long bits) {
		MarketMenu m = market();
		if (m == null || !ActionRules.haggleOffer(m.view(), bits)) {
			return false;
		}
		return send(new EconomyActionPayload(m.containerId, EconomyActionPayload.HAGGLE_OFFER, m.view().haggle().row(), bits));
	}

	/** Takes the merchant's last word. */
	public static boolean haggleAccept() {
		MarketMenu m = market();
		if (m == null || m.view().haggle() == null) {
			return false;
		}
		return send(new EconomyActionPayload(m.containerId, EconomyActionPayload.HAGGLE_ACCEPT, m.view().haggle().row(), 0));
	}

	/** Walks away from the haggle (no harm done). */
	public static boolean haggleCancel() {
		MarketMenu m = market();
		if (m == null || m.view().haggle() == null) {
			return false;
		}
		return send(new EconomyActionPayload(m.containerId, EconomyActionPayload.HAGGLE_CANCEL, m.view().haggle().row(), 0));
	}

	// ---------------------------------------------------------------- bank

	private static @Nullable BankMenu bank() {
		LocalPlayer p = player();
		return p != null && p.containerMenu instanceof BankMenu b ? b : null;
	}

	private static boolean bankAction(int action, int index, long amount) {
		BankMenu b = bank();
		return b != null && send(new EconomyActionPayload(b.containerId, action, index, amount));
	}

	/** Puts bits of carried coin on deposit ({@code -1}: all of it). */
	public static boolean deposit(long bits) {
		BankMenu b = bank();
		return b != null && b.view().hasBank() && ActionRules.amount(bits) && bankAction(EconomyActionPayload.DEPOSIT, 0, bits);
	}

	/** Takes bits off deposit as coin ({@code -1}: all of it). */
	public static boolean withdraw(long bits) {
		BankMenu b = bank();
		return b != null && b.view().hasBank() && ActionRules.amount(bits) && bankAction(EconomyActionPayload.WITHDRAW, 0, bits);
	}

	/** Sells emeralds to the money changer. */
	public static boolean sellEmeralds(int count) {
		BankMenu b = bank();
		return b != null && b.view().hasBank() && count > 0 && bankAction(EconomyActionPayload.SELL_EMERALDS, 0, count);
	}

	/** Buys emeralds from the money changer. */
	public static boolean buyEmeralds(int count) {
		BankMenu b = bank();
		return b != null && b.view().hasBank() && count > 0 && bankAction(EconomyActionPayload.BUY_EMERALDS, 0, count);
	}

	/** Buys a plot, house or business ({@code bank.forSale[].plot}). */
	public static boolean buyPlot(int plot) {
		BankMenu b = bank();
		return b != null && ActionRules.plot(b.view(), plot) && bankAction(EconomyActionPayload.BUY_PLOT, plot, 0);
	}

	/** Hires an idle resident into your business at a plot. */
	public static boolean hire(int plot) {
		BankMenu b = bank();
		return b != null && ActionRules.plot(b.view(), plot) && bankAction(EconomyActionPayload.HIRE, plot, 0);
	}

	/** Gives bits toward a public work ({@code bank.projects[].plot}). */
	public static boolean fund(int plot, long bits) {
		BankMenu b = bank();
		return b != null && ActionRules.plot(b.view(), plot) && ActionRules.amount(bits) && bits > 0 && bankAction(EconomyActionPayload.FUND, plot, bits);
	}

	/** Takes a supply contract. */
	public static boolean acceptContract(int id) {
		BankMenu b = bank();
		return b != null && ActionRules.contract(b.view(), id) && bankAction(EconomyActionPayload.ACCEPT_CONTRACT, id, 0);
	}

	/** Delivers what you carry toward a contract. */
	public static boolean deliver(int id) {
		BankMenu b = bank();
		return b != null && ActionRules.contract(b.view(), id) && bankAction(EconomyActionPayload.DELIVER, id, 0);
	}

	// ---------------------------------------------------------------- quests

	/** Takes a quest from the open notice board. */
	public static boolean accept(String questId) {
		QuestPayloads.Board b = HearthViews.board();
		UUID id = uuid(questId);
		if (!ActionRules.boardAccept(b, id)) {
			return false;
		}
		return send(new QuestPayloads.BoardAction(b.settlementId(), b.boardPos(), QuestPayloads.BoardAction.Action.ACCEPT, Optional.of(id)));
	}

	/** Hands a quest in at the open notice board. */
	public static boolean handIn(String questId) {
		QuestPayloads.Board b = HearthViews.board();
		UUID id = uuid(questId);
		if (!ActionRules.boardHandIn(b, id)) {
			return false;
		}
		return send(new QuestPayloads.BoardAction(b.settlementId(), b.boardPos(), QuestPayloads.BoardAction.Action.HAND_IN, Optional.of(id)));
	}

	/** Asks the open notice board again (a fresh {@code board} arrives). */
	public static boolean refreshBoard() {
		QuestPayloads.Board b = HearthViews.board();
		return b != null && send(new QuestPayloads.BoardAction(b.settlementId(), b.boardPos(), QuestPayloads.BoardAction.Action.REFRESH, Optional.empty()));
	}

	/** Tracks one of your quests (the HUD tracker and compass follow it). */
	public static boolean track(String questId) {
		return journal(questId, QuestPayloads.JournalAction.Action.TRACK);
	}

	/** Abandons one of your quests. */
	public static boolean abandon(String questId) {
		return journal(questId, QuestPayloads.JournalAction.Action.ABANDON);
	}

	/**
	 * Tracks the quarry of a wanted poster on the open notice board ({@code board.wanted[].player}), quest-like: the
	 * tracked quest is let go, and {@code state.wantedTrack} (and the map) follow them. The same poster again untracks.
	 */
	public static boolean trackPoster(String player) {
		QuestPayloads.Board board = HearthViews.board();
		if (!ActionRules.posterListed(board, player)) {
			return false;
		}
		return send(new QuestPayloads.PosterAction(board.settlementId(), player.trim()));
	}

	/** Stops tracking the wanted poster ({@code state.wantedTrack} goes null). */
	public static boolean untrackPoster() {
		QuestPayloads.PosterTrack t = HearthViews.posterTrack();
		return t != null && send(new QuestPayloads.PosterAction(t.settlementId(), ""));
	}

	private static boolean journal(String questId, QuestPayloads.JournalAction.Action action) {
		LocalPlayer p = player();
		UUID id = uuid(questId);
		if (p == null || !ActionRules.activeQuest(QuestStore.get(p), id)) {
			return false;
		}
		return send(new QuestPayloads.JournalAction(id, action));
	}

	// ---------------------------------------------------------------- the law

	/** Answers a guard or witness ({@code confront.choices[].id}); "" walks away (as {@link #close} does). */
	public static boolean confront(String choiceId) {
		LawPayloads.Confront c = HearthViews.confront();
		if (c == null) {
			return false;
		}
		if (choiceId == null || choiceId.isEmpty()) {
			return close(HearthScreens.CONFRONT);
		}
		return ActionRules.confrontChoice(c, choiceId) && send(new LawPayloads.ConfrontChoice(c.entity(), choiceId));
	}

	/** Pickpocketing: starts lifting the item in a pocket slot ({@code pickpocket.items[].slot}). */
	public static boolean lift(int slot) {
		LawPayloads.Pocket p = HearthViews.pocket();
		return ActionRules.pocketSlot(p, slot) && send(new LawPayloads.PocketAction(p.session(), LawPayloads.PocketAction.LIFT, slot));
	}

	/** Pickpocketing: stops the lift under way (the hand comes back empty, the mark is none the wiser). */
	public static boolean stopLifting() {
		LawPayloads.Pocket p = HearthViews.pocket();
		return p != null && p.state() == LawPayloads.Pocket.OPEN && send(new LawPayloads.PocketAction(p.session(), LawPayloads.PocketAction.STOP, -1));
	}

	/** Pickpocketing: slips away with what you have (as {@link #close}). */
	public static boolean slipAway() {
		return close(HearthScreens.PICKPOCKET);
	}

	/** Lockpicking: gives up on the lock (the pick and pins themselves are played natively, {@link HearthScreens#nativeScreen}). */
	public static boolean stopPicking() {
		return close(HearthScreens.LOCKPICK);
	}

	// ---------------------------------------------------------------- dice

	private static @Nullable DiceMenu dice() {
		LocalPlayer p = player();
		return p != null && p.containerMenu instanceof DiceMenu d ? d : null;
	}

	private static boolean diceAction(int action, int arg) {
		DiceMenu d = dice();
		return d != null && send(new DicePayloads.Action(d.containerId, action, arg));
	}

	/** Puts up one of the offered stakes ({@code dice.stakes[index]}) and starts the game. */
	public static boolean diceStake(int index) {
		DiceMenu d = dice();
		return d != null && ActionRules.diceStake(d.view(), index) && diceAction(DicePayloads.Action.STAKE, index);
	}

	/** Picks or unpicks a die to set aside ({@code dice.dice[].index}). */
	public static boolean diceToggle(int die) {
		DiceMenu d = dice();
		return d != null && ActionRules.diceToggle(d.view(), die) && diceAction(DicePayloads.Action.TOGGLE, die);
	}

	/** Sets the picked dice aside and rolls the rest. */
	public static boolean diceRoll() {
		DiceMenu d = dice();
		return d != null && ActionRules.dice(d.view(), DicePayloads.Action.ROLL) && diceAction(DicePayloads.Action.ROLL, 0);
	}

	/** Banks the turn's points. */
	public static boolean diceBank() {
		DiceMenu d = dice();
		return d != null && ActionRules.dice(d.view(), DicePayloads.Action.BANK) && diceAction(DicePayloads.Action.BANK, 0);
	}

	/** Passes the dice after a bust. */
	public static boolean dicePass() {
		DiceMenu d = dice();
		return d != null && ActionRules.dice(d.view(), DicePayloads.Action.PASS) && diceAction(DicePayloads.Action.PASS, 0);
	}

	/** Plays the loaded dice you carry ({@code true}) or puts them away. */
	public static boolean diceLoaded(boolean play) {
		DiceMenu d = dice();
		return d != null && ActionRules.dice(d.view(), DicePayloads.Action.LOADED) && diceAction(DicePayloads.Action.LOADED, play ? 1 : 0);
	}

	/** Another game, when this one is over. */
	public static boolean diceAgain() {
		DiceMenu d = dice();
		return d != null && ActionRules.dice(d.view(), DicePayloads.Action.AGAIN) && diceAction(DicePayloads.Action.AGAIN, 0);
	}

	// ---------------------------------------------------------------- standing and settings

	/** Asks for the reputation page (it arrives as {@code reputation}; the opener runs if it is not shown yet). */
	public static boolean requestReputation() {
		return send(StandingPayloads.RequestPage.INSTANCE);
	}

	/** How conversations are drawn: {@code card} (the portrait card, default) or {@code cinematic} (the bar). Saved. */
	public static boolean setConversationStyle(String style) {
		return HearthClientOptions.setConversationStyle(style);
	}
}
