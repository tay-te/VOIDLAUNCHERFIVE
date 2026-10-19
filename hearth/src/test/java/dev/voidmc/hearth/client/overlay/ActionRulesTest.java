package dev.voidmc.hearth.client.overlay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.economy.bank.BankView;
import dev.voidmc.hearth.economy.net.EconomyActionPayload;
import dev.voidmc.hearth.law.net.LawPayloads;
import dev.voidmc.hearth.quest.model.PlayerQuests;
import dev.voidmc.hearth.quest.model.QuestInstance;
import dev.voidmc.hearth.quest.model.QuestStatus;
import dev.voidmc.hearth.quest.net.QuestPayloads;
import dev.voidmc.hearth.social.dice.DiceGame;
import dev.voidmc.hearth.social.dice.DicePayloads;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

/** HearthActions sends nothing a screen could not have asked for; the server checks everything again anyway. */
class ActionRulesTest {
	static {
		OverlayFixtures.boot();
	}

	@Test
	void onlyShownUnlockedOptionsArePicked() {
		assertTrue(ActionRules.pickable(OverlayFixtures.talk(), "standing:ask/persuade"));
		assertFalse(ActionRules.pickable(OverlayFixtures.talk(), "social:room"), "locked");
		assertFalse(ActionRules.pickable(OverlayFixtures.talk(), "standing:flatter"), "not shown");
		assertFalse(ActionRules.pickable(null, "npc:bye"), "no conversation");
		assertFalse(ActionRules.pickable(OverlayFixtures.talk(), null));
	}

	@Test
	void trainingFindsTheLessonForASkill() {
		assertEquals("mettle:train/speech", ActionRules.trainingOption(OverlayFixtures.talk(), OverlayFixtures.detail(), "mettle:speech"));
		assertEquals("mettle:train/speech", ActionRules.trainingOption(OverlayFixtures.talk(), null, "speech"));
		assertNull(ActionRules.trainingOption(OverlayFixtures.talk(), OverlayFixtures.detail(), "mettle:blades"));
		assertNull(ActionRules.trainingOption(null, null, "speech"));
	}

	@Test
	void marketCallsNeedARowOrAnOpenHaggle() {
		var withHaggle = OverlayFixtures.market(OverlayFixtures.haggle(-1, -1, 0));
		assertTrue(ActionRules.marketRow(withHaggle, 1));
		assertTrue(ActionRules.marketRow(withHaggle, 10_000));
		assertFalse(ActionRules.marketRow(withHaggle, 7));
		assertTrue(ActionRules.haggleOffer(withHaggle, 18));
		assertFalse(ActionRules.haggleOffer(withHaggle, -1));
		assertFalse(ActionRules.haggleOffer(withHaggle, ActionRules.MAX_BITS + 1));
		assertFalse(ActionRules.haggleOffer(OverlayFixtures.market(null), 18), "no haggle under way");
	}

	@Test
	void amountsArePositiveOrAll() {
		assertTrue(ActionRules.amount(1));
		assertTrue(ActionRules.amount(EconomyActionPayload.ALL));
		assertFalse(ActionRules.amount(0));
		assertFalse(ActionRules.amount(-5));
		assertFalse(ActionRules.amount(ActionRules.MAX_BITS + 1));
	}

	@Test
	void bankCallsNameListedPlotsAndContracts() {
		BankView v = new BankView("Bradfield", BankView.TAB_BANK, 1250, 454, 9000, 2, 10, 40, 60, 24, true,
			List.of(new BankView.PlotOffer(12, "house/cottage", true, 7, 9, 2, 800, 10, 0)), Component.empty(), true,
			List.of(new BankView.ProjectOffer(30, "civic/chapel", true, 1, 4, 600, 100, 20, 80, false)),
			List.of(new BankView.ContractOffer(4, "wood", 64, 0, 300, 5, 0)));
		assertTrue(ActionRules.plot(v, 12));
		assertTrue(ActionRules.plot(v, 30));
		assertFalse(ActionRules.plot(v, 13));
		assertTrue(ActionRules.contract(v, 4));
		assertFalse(ActionRules.contract(v, 5));
		assertFalse(ActionRules.plot(null, 12));
	}

	@Test
	void diceCallsFollowThePhase() {
		var staking = OverlayFixtures.dice(DiceGame.Phase.STAKE, 0, false, false, 0);
		assertTrue(ActionRules.diceStake(staking, 2));
		assertFalse(ActionRules.diceStake(staking, 3));
		assertFalse(ActionRules.diceToggle(staking, 0), "no dice to pick while staking");
		var rolled = OverlayFixtures.dice(DiceGame.Phase.PLAYER_ROLLED, 0b100000, true, false, 1);
		assertTrue(ActionRules.diceToggle(rolled, 1));
		assertFalse(ActionRules.diceToggle(rolled, 5), "set aside already");
		assertFalse(ActionRules.diceToggle(rolled, 6), "no seventh die");
		assertTrue(ActionRules.dice(rolled, DicePayloads.Action.ROLL));
		assertFalse(ActionRules.dice(rolled, DicePayloads.Action.BANK), "the table says it cannot bank");
		assertFalse(ActionRules.dice(rolled, DicePayloads.Action.PASS));
		assertTrue(ActionRules.dice(rolled, DicePayloads.Action.LOADED));
		assertTrue(ActionRules.dice(OverlayFixtures.dice(DiceGame.Phase.PLAYER_BUST, 0, false, false, 0), DicePayloads.Action.PASS));
		assertTrue(ActionRules.dice(OverlayFixtures.dice(DiceGame.Phase.OVER, 0, false, false, 0), DicePayloads.Action.AGAIN));
		assertFalse(ActionRules.dice(OverlayFixtures.dice(DiceGame.Phase.OVER, 0, false, false, 0), DicePayloads.Action.LOADED));
	}

	@Test
	void pocketsAndConfrontations() {
		LawPayloads.Pocket open = OverlayFixtures.pocket(LawPayloads.Pocket.OPEN);
		assertTrue(ActionRules.pocketSlot(open, 0));
		assertFalse(ActionRules.pocketSlot(open, 1), "already taken");
		assertFalse(ActionRules.pocketSlot(open, 3));
		assertFalse(ActionRules.pocketSlot(OverlayFixtures.pocket(LawPayloads.Pocket.CAUGHT), 0), "caught: hands off");
		assertTrue(ActionRules.confrontChoice(OverlayFixtures.confront(), "pay"));
		assertFalse(ActionRules.confrontChoice(OverlayFixtures.confront(), "bribe"), "greyed out");
		assertFalse(ActionRules.confrontChoice(OverlayFixtures.confront(), "fight"));
	}

	@Test
	void questCallsNameQuestsOnTheBoardOrInTheJournal() {
		QuestInstance open = OverlayFixtures.quest("Rats", QuestStatus.OFFERED, 0);
		QuestInstance locked = OverlayFixtures.quest("Bandits", QuestStatus.OFFERED, 0);
		UUID handIn = UUID.randomUUID();
		QuestPayloads.Board board = new QuestPayloads.Board(1L, BlockPos.ZERO, "Bradfield", "village", 24,
			List.of(new QuestPayloads.Board.Entry(open, ""), new QuestPayloads.Board.Entry(locked, "Needs standing 40")), List.of(handIn), List.of(), "Known");
		assertTrue(ActionRules.boardAccept(board, open.id));
		assertFalse(ActionRules.boardAccept(board, locked.id), "locked");
		assertFalse(ActionRules.boardAccept(board, UUID.randomUUID()));
		assertTrue(ActionRules.boardHandIn(board, handIn));
		assertFalse(ActionRules.boardHandIn(board, open.id));
		PlayerQuests pq = new PlayerQuests();
		pq.active.add(open);
		assertTrue(ActionRules.activeQuest(pq, open.id));
		assertFalse(ActionRules.activeQuest(pq, locked.id));
		assertFalse(ActionRules.activeQuest(pq, null));
	}
}
