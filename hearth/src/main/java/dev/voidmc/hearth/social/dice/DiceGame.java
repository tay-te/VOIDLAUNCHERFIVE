package dev.voidmc.hearth.social.dice;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * One game of tavern dice between a player (seat 0) and a folk (seat 1), as a pure state machine: the server's menu
 * drives it and shows its state; every roll comes from the server's random, so the client never decides anything.
 *
 * <p>A turn: roll the dice not yet set aside; if nothing scores, the turn's points are lost (a bust). Otherwise set
 * aside at least one scoring die, then roll the rest or bank the turn. Setting aside all six ("hot dice") lets you roll
 * all six again. First to the target wins.
 */
public final class DiceGame {
	public static final int PLAYER = 0;
	public static final int FOLK = 1;

	public enum Phase {
		/** The player is choosing what to play for. */
		STAKE,
		/** The player rolled and must set dice aside, then roll on or bank. */
		PLAYER_ROLLED,
		/** The player's roll scored nothing; waiting for them to pass the dice. */
		PLAYER_BUST,
		/** The folk is playing its turn, one step at a time. */
		FOLK_TURN,
		/** The game is over ({@link #winner()}, or caught cheating). */
		OVER
	}

	/** Something that happened, for the menu to put into words and sounds. */
	public record Event(Kind kind, int seat, int value) {
		public enum Kind {
			ROLLED, BUST, SET_ASIDE, HOT_DICE, BANKED, WON, CHEAT_CAUGHT
		}
	}

	private final RandomGenerator random;
	private final DicePersonality folk;
	private final int target;
	private final double loadedBias;
	private final double detectBase;

	private Phase phase = Phase.STAKE;
	private final int[] dice = new int[Farkle.DICE];
	/** Dice set aside earlier in this turn (they are not rolled again). */
	private final boolean[] kept = new boolean[Farkle.DICE];
	/** Dice rolled in the last roll (for the animation). */
	private int rolledMask;
	/** The player's current pick among the free dice. */
	private int selected;
	private final int[] scores = new int[2];
	private int turnPoints;
	private int turn = PLAYER;
	private int winner = -1;
	private boolean cheatCaught;
	private boolean loaded;
	private int loadedRolls;
	private int rollSerial;
	/** The folk's decision waiting to be carried out (shown as its selection first). */
	private DiceAi.Decision pending;
	private boolean folkNeedsRoll;
	/** The folk's turn is over (a bust or a bank) and the table shows it for a beat before the player rolls. */
	private boolean folkHandOver;
	private final List<Event> events = new ArrayList<>();

	/**
	 * @param loadedBias chance a loaded die that lands badly tumbles again (0 turns cheating off)
	 * @param detectBase base chance per loaded roll that the folk notices
	 */
	public DiceGame(RandomGenerator random, DicePersonality folk, int target, double loadedBias, double detectBase) {
		this.random = random;
		this.folk = folk;
		this.target = target;
		this.loadedBias = loadedBias;
		this.detectBase = detectBase;
	}

	// ---- reading

	public Phase phase() {
		return this.phase;
	}

	public int die(int i) {
		return this.dice[i];
	}

	public int[] dice() {
		return this.dice.clone();
	}

	public boolean kept(int i) {
		return this.kept[i];
	}

	public int keptMask() {
		int m = 0;
		for (int i = 0; i < Farkle.DICE; i++) {
			if (this.kept[i]) {
				m |= 1 << i;
			}
		}
		return m;
	}

	public int selectedMask() {
		return this.selected;
	}

	public int rolledMask() {
		return this.rolledMask;
	}

	public int rollSerial() {
		return this.rollSerial;
	}

	public int score(int seat) {
		return this.scores[seat];
	}

	public int turnPoints() {
		return this.turnPoints;
	}

	public int turn() {
		return this.turn;
	}

	public int target() {
		return this.target;
	}

	public int winner() {
		return this.winner;
	}

	public boolean cheatCaught() {
		return this.cheatCaught;
	}

	public boolean loaded() {
		return this.loaded;
	}

	public DicePersonality personality() {
		return this.folk;
	}

	/** The score of the current pick, or {@link Farkle#INVALID}; 0 when nothing is picked. */
	public int selectionScore() {
		return Farkle.scoreMask(this.dice, this.selected);
	}

	/** Which free dice can score (bit per die), for highlighting. */
	public int scoringMask() {
		int[] free = freeFaces();
		int sub = Farkle.scoringMask(free);
		return expand(sub);
	}

	public boolean canRollOn() {
		return this.phase == Phase.PLAYER_ROLLED && this.selected != 0 && selectionScore() > 0;
	}

	public boolean canBank() {
		return canRollOn();
	}

	/** Events since the last call. */
	public List<Event> drainEvents() {
		List<Event> out = new ArrayList<>(this.events);
		this.events.clear();
		return out;
	}

	// ---- the player's moves

	/** The stake is down: the player opens. */
	public void begin() {
		if (this.phase != Phase.STAKE) {
			return;
		}
		startTurn(PLAYER);
	}

	/** Loaded dice in hand (or put away) for the player's rolls. */
	public void setLoaded(boolean loaded) {
		this.loaded = loaded && this.loadedBias > 0;
	}

	/** Picks or unpicks a free die. */
	public boolean toggle(int i) {
		if (this.phase != Phase.PLAYER_ROLLED || i < 0 || i >= Farkle.DICE || this.kept[i]) {
			return false;
		}
		this.selected ^= 1 << i;
		return true;
	}

	/** Sets the pick aside and rolls the rest. */
	public boolean rollOn() {
		if (!canRollOn()) {
			return false;
		}
		setAside(PLAYER);
		roll(PLAYER);
		return true;
	}

	/** Sets the pick aside and banks the turn. */
	public boolean bank() {
		if (!canBank()) {
			return false;
		}
		setAside(PLAYER);
		bankTurn(PLAYER);
		return true;
	}

	/** After a bust, passes the dice to the folk. */
	public boolean passDice() {
		if (this.phase != Phase.PLAYER_BUST) {
			return false;
		}
		startTurn(FOLK);
		return true;
	}

	/** The player leaves the table: the folk wins. */
	public void forfeit() {
		if (this.phase != Phase.OVER) {
			this.winner = FOLK;
			this.phase = Phase.OVER;
		}
	}

	// ---- the folk's turn

	/**
	 * One visible step of the folk's turn: a roll; or picking dice (shown before they are moved); or setting them aside
	 * and rolling on or banking. Returns false if it is not the folk's turn.
	 */
	public boolean folkStep() {
		if (this.phase != Phase.FOLK_TURN) {
			return false;
		}
		if (this.folkHandOver) {
			this.folkHandOver = false;
			startTurn(PLAYER);
			return true;
		}
		if (this.folkNeedsRoll) {
			this.folkNeedsRoll = false;
			roll(FOLK);
			return true;
		}
		if (this.pending == null) {
			int[] free = freeFaces();
			DiceAi.Decision d = DiceAi.decide(free, Farkle.DICE - free.length, this.turnPoints, this.scores[FOLK], this.scores[PLAYER], this.target,
				this.folk, this.random);
			this.pending = d;
			this.selected = expand(d.keep());
			return true;
		}
		DiceAi.Decision d = this.pending;
		this.pending = null;
		setAside(FOLK);
		if (d.bank()) {
			bankTurn(FOLK);
		} else {
			this.folkNeedsRoll = true;
		}
		return true;
	}

	// ---- inside

	private void startTurn(int seat) {
		this.turn = seat;
		this.turnPoints = 0;
		this.selected = 0;
		this.pending = null;
		this.folkHandOver = false;
		java.util.Arrays.fill(this.kept, false);
		if (seat == FOLK) {
			this.phase = Phase.FOLK_TURN;
			this.folkNeedsRoll = true;
		} else {
			roll(PLAYER);
		}
	}

	private void roll(int seat) {
		int mask = 0;
		for (int i = 0; i < Farkle.DICE; i++) {
			if (!this.kept[i]) {
				int face = 1 + this.random.nextInt(6);
				if (seat == PLAYER && this.loaded && face != 1 && face != 5 && this.random.nextDouble() < this.loadedBias) {
					face = 1 + this.random.nextInt(6); // the weight tips a bad face over once
				}
				this.dice[i] = face;
				mask |= 1 << i;
			}
		}
		this.rolledMask = mask;
		this.rollSerial++;
		this.selected = 0;
		this.events.add(new Event(Event.Kind.ROLLED, seat, mask));
		if (seat == PLAYER && this.loaded) {
			double chance = this.detectBase + this.folk.perceptiveness + 0.015 * this.loadedRolls;
			this.loadedRolls++;
			if (this.random.nextDouble() < chance) {
				this.cheatCaught = true;
				this.winner = FOLK;
				this.phase = Phase.OVER;
				this.events.add(new Event(Event.Kind.CHEAT_CAUGHT, seat, 0));
				return;
			}
		}
		if (!Farkle.scores(freeFaces())) {
			this.events.add(new Event(Event.Kind.BUST, seat, this.turnPoints));
			this.turnPoints = 0;
			if (seat == PLAYER) {
				this.phase = Phase.PLAYER_BUST;
			} else {
				this.folkHandOver = true;
			}
			return;
		}
		this.phase = seat == PLAYER ? Phase.PLAYER_ROLLED : Phase.FOLK_TURN;
	}

	private void setAside(int seat) {
		int s = selectionScore();
		this.turnPoints += s;
		for (int i = 0; i < Farkle.DICE; i++) {
			if ((this.selected >> i & 1) != 0) {
				this.kept[i] = true;
			}
		}
		this.events.add(new Event(Event.Kind.SET_ASIDE, seat, s));
		this.selected = 0;
		boolean all = true;
		for (boolean k : this.kept) {
			all &= k;
		}
		if (all) {
			java.util.Arrays.fill(this.kept, false);
			this.events.add(new Event(Event.Kind.HOT_DICE, seat, this.turnPoints));
		}
	}

	private void bankTurn(int seat) {
		this.scores[seat] += this.turnPoints;
		this.events.add(new Event(Event.Kind.BANKED, seat, this.turnPoints));
		this.turnPoints = 0;
		if (this.scores[seat] >= this.target) {
			this.winner = seat;
			this.phase = Phase.OVER;
			this.events.add(new Event(Event.Kind.WON, seat, this.scores[seat]));
			return;
		}
		if (seat == FOLK) {
			this.folkHandOver = true;
		} else {
			startTurn(FOLK);
		}
	}

	/** The faces of the dice not set aside, in order. */
	private int[] freeFaces() {
		int n = 0;
		for (boolean k : this.kept) {
			if (!k) {
				n++;
			}
		}
		int[] out = new int[n];
		int j = 0;
		for (int i = 0; i < Farkle.DICE; i++) {
			if (!this.kept[i]) {
				out[j++] = this.dice[i];
			}
		}
		return out;
	}

	/** A mask over the free dice to a mask over all six. */
	private int expand(int freeMask) {
		int out = 0;
		int j = 0;
		for (int i = 0; i < Farkle.DICE; i++) {
			if (!this.kept[i]) {
				if ((freeMask >> j & 1) != 0) {
					out |= 1 << i;
				}
				j++;
			}
		}
		return out;
	}
}
