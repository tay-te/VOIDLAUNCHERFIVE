package dev.voidmc.hearth.social.dice;

import dev.voidmc.hearth.api.LawService;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.Hooks;
import dev.voidmc.hearth.social.SocialConfig;
import dev.voidmc.hearth.social.SocialLedger;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.SocialItems;
import dev.voidmc.hearth.social.calendar.SocialTime;
import dev.voidmc.hearth.social.life.TownLife;
import java.util.List;
import java.util.SplittableRandom;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The dice table, server side: a {@link DiceGame} between the player and a folk, with the stake held in escrow
 * (the player's coin and the folk's, from the town's purse) and paid out to the winner. The folk's turn plays itself a
 * step at a time as the server ticks the open menu. Leaving mid-game forfeits the pot. Client side the menu only holds
 * the last {@link DiceView}.
 */
public class DiceMenu extends AbstractContainerMenu {
	/** Ticks between the folk's moves, so the player can follow them. */
	static final int FOLK_STEP_TICKS = 22;
	private static final double MAX_DISTANCE_SQR = 8 * 8;

	private DiceView view;
	// server side
	private final @Nullable ServerPlayer player;
	private final @Nullable Settlement settlement;
	private final @Nullable Resident folk;
	private final @Nullable Vec3 anchor;
	private @Nullable DiceGame game;
	private List<Integer> stakes = List.of();
	private long stake;
	private boolean escrow;
	private boolean settled;
	private int folkTimer;
	private Component line = Component.empty();
	private Component status = Component.empty();
	private final SplittableRandom random;

	/** Client side. */
	public DiceMenu(int containerId, Inventory inventory, DiceView view) {
		super(DiceTables.MENU, containerId);
		this.view = view;
		this.player = null;
		this.settlement = null;
		this.folk = null;
		this.anchor = null;
		this.random = new SplittableRandom();
	}

	/** Server side. */
	DiceMenu(int containerId, ServerPlayer player, Settlement settlement, Resident folk, List<Integer> stakes) {
		super(DiceTables.MENU, containerId);
		this.player = player;
		this.settlement = settlement;
		this.folk = folk;
		this.anchor = player.position();
		this.stakes = stakes;
		this.random = new SplittableRandom(player.level().getRandom().nextLong());
		SocialConfig.Dice cfg = SocialModule.config().dice();
		this.game = new DiceGame(this.random, DiceTables.personality(folk), cfg.target(), cfg.loadedBias(), cfg.detectBase());
		this.line = DiceTables.say(this.game.personality(), "start", this.random);
		this.view = buildView();
	}

	public DiceView view() {
		return this.view;
	}

	public void setView(DiceView view) {
		this.view = view;
	}

	/** Builds the view of the table as it stands. */
	DiceView buildView() {
		DiceGame g = this.game;
		if (g == null || this.folk == null || this.player == null) {
			return this.view;
		}
		int loaded = !SocialItems.carriesLoadedDice(this.player) ? 0 : g.loaded() ? 2 : 1;
		return new DiceView(g.phase().ordinal(), this.folk.fullName(), g.personality().id(), this.stake, g.target(), g.score(DiceGame.PLAYER),
			g.score(DiceGame.FOLK), g.turnPoints(), g.turn(), g.dice(), g.keptMask(), g.selectedMask(), g.scoringMask(), g.selectionScore(),
			g.rolledMask(), g.rollSerial(), this.stakes, this.line, this.status, g.winner(), g.cheatCaught(), loaded, Hooks.balance(this.player),
			g.canRollOn(), g.canBank());
	}

	/** A click from the client, on the server thread. */
	public void handle(ServerPlayer p, DicePayloads.Action a) {
		DiceGame g = this.game;
		if (g == null || p != this.player) {
			return;
		}
		switch (a.action()) {
			case DicePayloads.Action.STAKE -> {
				if (g.phase() == DiceGame.Phase.STAKE && a.arg() >= 0 && a.arg() < this.stakes.size() && putUp(this.stakes.get(a.arg()))) {
					g.begin();
					afterMove();
				}
			}
			case DicePayloads.Action.TOGGLE -> {
				if (g.toggle(a.arg())) {
					click(0.9F);
				}
			}
			case DicePayloads.Action.ROLL -> {
				if (g.rollOn()) {
					afterMove();
				}
			}
			case DicePayloads.Action.BANK -> {
				if (g.bank()) {
					afterMove();
				}
			}
			case DicePayloads.Action.PASS -> {
				if (g.passDice()) {
					afterMove();
				}
			}
			case DicePayloads.Action.LOADED -> {
				if (g.phase() != DiceGame.Phase.OVER) {
					g.setLoaded(a.arg() == 1 && SocialItems.carriesLoadedDice(p));
				}
			}
			case DicePayloads.Action.AGAIN -> {
				if (g.phase() == DiceGame.Phase.OVER && !g.cheatCaught()) {
					String refusal = DiceTables.refusal(p, this.settlement, this.folk);
					if (refusal != null) {
						this.status = Component.translatable(refusal);
					} else {
						SocialConfig.Dice cfg = SocialModule.config().dice();
						this.game = new DiceGame(this.random, g.personality(), cfg.target(), cfg.loadedBias(), cfg.detectBase());
						this.stakes = DiceTables.stakes(p, this.settlement, this.folk);
						this.escrow = false;
						this.settled = false;
						this.stake = 0;
						this.line = DiceTables.say(g.personality(), "again", this.random);
						this.status = Component.empty();
					}
				}
			}
			default -> {
			}
		}
		send();
	}

	/** Puts the stakes on the table: the player's coin and the folk's (from the town's purse). */
	private boolean putUp(long bits) {
		if (this.player == null || this.settlement == null) {
			return false;
		}
		if (bits > 0) {
			if (Hooks.economy() == null || this.settlement.treasury() < bits) {
				return false;
			}
			if (!Hooks.charge(this.player, bits, this.folk, this.settlement)) {
				this.status = Component.translatable("hearth.social.dice.cannot_afford");
				return false;
			}
			this.settlement.withdraw(bits);
			this.settlement.markDirty();
		}
		this.stake = bits;
		this.escrow = true;
		return true;
	}

	/** After any move: words for what happened, and the purse if the game is over. */
	private void afterMove() {
		DiceGame g = this.game;
		if (g == null) {
			return;
		}
		for (DiceGame.Event e : g.drainEvents()) {
			describe(e);
		}
		if (g.phase() == DiceGame.Phase.OVER) {
			settle();
		}
	}

	private void describe(DiceGame.Event e) {
		boolean folkSide = e.seat() == DiceGame.FOLK;
		String who = this.folk == null ? "?" : this.folk.givenName;
		DicePersonality pers = this.game == null ? DicePersonality.SHREWD : this.game.personality();
		switch (e.kind()) {
			case ROLLED -> click(1.0F);
			case BUST -> {
				this.status = folkSide ? Component.translatable("hearth.social.dice.status.folk_bust", who, e.value())
					: Component.translatable("hearth.social.dice.status.player_bust", e.value());
				this.line = DiceTables.say(pers, folkSide ? "folk_bust" : "player_bust", this.random);
			}
			case SET_ASIDE -> {
				if (folkSide) {
					this.status = Component.translatable("hearth.social.dice.status.folk_keeps", who, e.value());
				}
			}
			case HOT_DICE -> {
				this.status = Component.translatable("hearth.social.dice.status.hot", e.value());
				if (!folkSide) {
					this.line = DiceTables.say(pers, "player_hot", this.random);
				}
			}
			case BANKED -> {
				this.status = folkSide ? Component.translatable("hearth.social.dice.status.folk_banks", who, e.value())
					: Component.translatable("hearth.social.dice.status.player_banks", e.value());
				if (folkSide) {
					this.line = DiceTables.say(pers, "folk_bank", this.random);
				}
				ping(folkSide ? 0.8F : 1.4F);
			}
			case WON -> {
				this.status = folkSide ? Component.translatable("hearth.social.dice.status.folk_wins", who, Hooks.coins(this.stake * 2))
					: Component.translatable("hearth.social.dice.status.player_wins", Hooks.coins(this.stake * 2));
				this.line = DiceTables.say(pers, folkSide ? "folk_win" : "player_win", this.random);
			}
			case CHEAT_CAUGHT -> {
				this.status = Component.translatable("hearth.social.dice.status.cheat", who);
				this.line = DiceTables.say(pers, "cheat", this.random);
			}
		}
	}

	/** Pays the pot to the winner (once). */
	private void settle() {
		DiceGame g = this.game;
		if (this.settled || g == null || this.player == null || this.settlement == null || this.folk == null) {
			return;
		}
		this.settled = true;
		if (!this.escrow) {
			return;
		}
		long pot = this.stake * 2;
		long day = SocialTime.day(TownLife.now(this.settlement));
		SocialLedger ledger = this.settlement.data(SocialModule.LEDGER);
		if (g.winner() == DiceGame.PLAYER) {
			Hooks.pay(this.player, pot);
			ledger.addDiceNet(this.folk.id, day, (int) -this.stake);
		} else {
			this.settlement.deposit(pot);
			ledger.addDiceNet(this.folk.id, day, (int) this.stake);
		}
		if (g.cheatCaught()) {
			SocialConfig.Dice cfg = SocialModule.config().dice();
			ledger.addDiceNet(this.folk.id, day, 1_000_000); // will not play you again today
			Hooks.deed(this.player, this.settlement, "social:dice_cheat", cfg.cheatStanding(), this.folk.id, this.player.blockPosition(), true);
			if (cfg.cheatReport()) {
				Hooks.report(new LawService.Crime(LawService.Offence.THEFT, this.player.getUUID(), this.settlement.id(), this.player.blockPosition(),
					this.folk.id, Math.max(1, this.stake), true, this.player.level().getGameTime()));
			}
			SocialItems.confiscateLoadedDice(this.player);
		}
		this.escrow = false;
		this.settlement.markDirty();
	}

	/** Server tick of the open menu: the folk plays its turn. */
	@Override
	public void broadcastChanges() {
		super.broadcastChanges();
		DiceGame g = this.game;
		if (g == null || this.player == null || g.phase() != DiceGame.Phase.FOLK_TURN) {
			return;
		}
		if (++this.folkTimer < FOLK_STEP_TICKS) {
			return;
		}
		this.folkTimer = 0;
		g.folkStep();
		if (this.folk != null) {
			DiceTables.folkMoved(this.folk.id);
		}
		afterMove();
		send();
	}

	private void send() {
		if (this.player == null) {
			return;
		}
		this.view = buildView();
		ServerPlayNetworking.send(this.player, new DicePayloads.View(this.containerId, this.view));
	}

	private void click(float pitch) {
		if (this.player != null) {
			this.player.level().playSound(null, this.player.blockPosition(), SoundEvents.BONE_BLOCK_HIT, SoundSource.PLAYERS, 0.6F, pitch);
		}
	}

	private void ping(float pitch) {
		if (this.player != null) {
			this.player.level().playSound(null, this.player.blockPosition(), SoundEvents.CHAIN_STEP, SoundSource.PLAYERS, 0.7F, pitch);
		}
	}

	@Override
	public void removed(Player p) {
		super.removed(p);
		DiceGame g = this.game;
		if (this.player == null || g == null) {
			return;
		}
		if (g.phase() != DiceGame.Phase.STAKE && g.phase() != DiceGame.Phase.OVER) {
			g.forfeit(); // leaving the table loses the pot
			settle();
		}
		if (this.folk != null) {
			DiceTables.ended(this.folk.id);
		}
	}

	@Override
	public ItemStack quickMoveStack(Player p, int slotIndex) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player p) {
		return this.anchor == null || p.isAlive() && p.position().distanceToSqr(this.anchor) <= MAX_DISTANCE_SQR;
	}
}
