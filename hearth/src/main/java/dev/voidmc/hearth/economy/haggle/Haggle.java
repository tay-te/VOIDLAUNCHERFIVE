package dev.voidmc.hearth.economy.haggle;

import dev.voidmc.hearth.economy.EconomyConfig;

/**
 * One haggle over one deal, as pure arithmetic (docs/LIVING_WORLD.md §5: "push too hard and the merchant sours").
 *
 * <p>The merchant posts a price (their asking price when the player buys, their offer when the player sells) and has a
 * limit at heart they will not go past, hidden from the player: {@code room} below the asking price, or above the offer
 * (never more than they can pay). The player names a price; the merchant
 * <ul>
 *   <li>takes it if it is at least as good for them as their own last word, or within a fifth of their room of it;</li>
 *   <li>meets it part way if it is within their limit — less far as their patience wears thin;</li>
 *   <li>is offended if it is past their limit, losing patience in proportion to how far past, and budges only a little
 *       toward their limit.</li>
 * </ul>
 * Every counter costs patience; when it runs out the merchant walks away from the deal and is sour with the player for
 * a while. The player can take the merchant's last word at any time. Deterministic, so it can be tested and cannot be
 * gamed by retrying the same offer.
 */
public final class Haggle {
	/** After this many offers a merchant has had enough, whatever their patience. */
	static final int MAX_ROUNDS = 12;
	/** An offer this close to the merchant's last word (as a share of their room) closes the deal. */
	static final double CLOSE_ENOUGH = 0.2;

	public enum State {
		OPEN, DEAL, WALKED
	}

	/** What the merchant just did, for their line in the trade screen. */
	public enum Event {
		START, COUNTER, OFFENDED, FIRM, DEAL, WALKED
	}

	private final boolean playerBuys;
	private final long list;
	private final long limit;
	private final double startPatience;
	private final EconomyConfig.Haggle cfg;
	private double patience;
	private long counter;
	private long lastOffer;
	private int rounds;
	private State state = State.OPEN;
	private Event last = Event.START;
	private long agreed;

	private Haggle(boolean playerBuys, long list, long limit, double patience, EconomyConfig.Haggle cfg) {
		this.playerBuys = playerBuys;
		this.list = list;
		this.limit = limit;
		this.startPatience = Math.max(1, patience);
		this.patience = this.startPatience;
		this.cfg = cfg;
		this.counter = list;
		this.lastOffer = list;
	}

	/**
	 * @param playerBuys whether the player is buying (the merchant would like more) or selling (the merchant would like to
	 *                   pay less)
	 * @param list       the posted price of the whole deal, in bits
	 * @param room       how far the merchant will go at heart, as a share of the price ({@link Temper#room})
	 * @param patience   how much haggling they will bear ({@link Temper#patience})
	 * @param cash       the most the merchant can pay (selling only; ignored when the player buys)
	 */
	public static Haggle start(boolean playerBuys, long list, double room, double patience, long cash, EconomyConfig.Haggle cfg) {
		long l = Math.max(1, list);
		long limit = playerBuys ? Math.max(1, (long) Math.floor(l * (1 - room))) : Math.max(l, Math.min(cash, (long) Math.ceil(l * (1 + room))));
		return new Haggle(playerBuys, l, limit, patience, cfg);
	}

	/** How good a price is for the merchant: more is better when the player buys, less when the player sells. */
	private long worth(long price) {
		return this.playerBuys ? price : -price;
	}

	/** Moves a price {@code by} bits toward the player's side. */
	private long towardPlayer(long price, long by) {
		return this.playerBuys ? price - by : price + by;
	}

	/** The player names a price for the whole deal. */
	public Event offer(long bits) {
		if (this.state != State.OPEN) {
			return this.last;
		}
		long offer = Math.max(0, bits);
		this.lastOffer = offer;
		this.rounds++;
		long width = Math.max(1, Math.abs(this.list - this.limit));
		if (worth(offer) >= worth(this.counter)) {
			return deal(this.counter);
		}
		long gap = Math.abs(this.counter - offer);
		if (worth(offer) >= worth(this.limit)) {
			if (gap <= Math.max(1, Math.round(width * CLOSE_ENOUGH))) {
				return deal(offer);
			}
			double concede = Math.max(0.15, 0.5 * this.patience / this.startPatience);
			long next = towardPlayer(this.counter, (long) Math.ceil(gap * concede));
			this.counter = worth(next) < worth(offer) ? offer : next;
			this.patience -= this.cfg.roundCost();
			this.last = Event.COUNTER;
		} else {
			double outrage = Math.abs(this.limit - offer) / (double) Math.max(1, this.list);
			this.patience -= this.cfg.roundCost() + this.cfg.outrageCost() * outrage;
			long room = Math.abs(this.counter - this.limit);
			if (room == 0) {
				this.last = Event.FIRM;
			} else {
				this.counter = towardPlayer(this.counter, Math.max(1, (long) Math.ceil(room * 0.25)));
				this.last = Event.OFFENDED;
			}
		}
		if (this.patience <= 0 || this.rounds >= MAX_ROUNDS) {
			this.state = State.WALKED;
			this.last = Event.WALKED;
		}
		return this.last;
	}

	/** The player takes the merchant's last word. */
	public Event accept() {
		if (this.state != State.OPEN) {
			return this.last;
		}
		return deal(this.counter);
	}

	private Event deal(long price) {
		this.agreed = price;
		this.state = State.DEAL;
		this.last = Event.DEAL;
		return this.last;
	}

	public boolean playerBuys() {
		return this.playerBuys;
	}

	public long list() {
		return this.list;
	}

	/** The merchant's last word. */
	public long counter() {
		return this.counter;
	}

	public long lastOffer() {
		return this.lastOffer;
	}

	/** The agreed price (when {@link #state} is {@link State#DEAL}). */
	public long agreed() {
		return this.agreed;
	}

	public State state() {
		return this.state;
	}

	public Event last() {
		return this.last;
	}

	/** Patience left, 0..100. */
	public int patience() {
		return (int) Math.max(0, Math.min(100, Math.round(100 * this.patience / this.startPatience)));
	}

	/** For tests: the limit the merchant will not pass. */
	long limit() {
		return this.limit;
	}

	/** Offers the player may still make before the merchant has had enough, whatever their patience. */
	public int offersLeft() {
		return this.state == State.OPEN ? Math.max(0, MAX_ROUNDS - this.rounds) : 0;
	}

	/** The Speech level from which a talker reads the merchant's limit as a band (docs/HUB_UI.md: hidden below it, KCD-style). */
	public static final int BAND_SPEECH = 5;
	/** The Speech level at which the band has closed on the limit itself. */
	public static final int EXACT_SPEECH = 15;

	/**
	 * Where a talker of this Speech guesses the merchant's limit lies: {@code {low, high}} in bits around the hidden limit,
	 * as wide as 30 % of the deal's price at {@link #BAND_SPEECH} and closing to the limit itself at {@link #EXACT_SPEECH};
	 * null below {@link #BAND_SPEECH} (the band stays hidden). The limit's place inside the band is fixed by the deal, so
	 * looking again tells nothing new.
	 */
	public static long @org.jspecify.annotations.Nullable [] band(long list, long limit, int speech) {
		if (speech < BAND_SPEECH) {
			return null;
		}
		double share = 0.03 * Math.max(0, EXACT_SPEECH - Math.min(EXACT_SPEECH, speech));
		long width = Math.round(Math.max(1, list) * share);
		if (width <= 0) {
			return new long[] {limit, limit};
		}
		long mix = list * 0x9E3779B97F4A7C15L ^ limit * 0xC2B2AE3D27D4EB4FL;
		mix ^= mix >>> 29;
		double at = ((mix & 0xFFFF) / 65535.0) * 0.8 + 0.1;  // the limit sits 10–90 % of the way across the band
		long low = Math.max(0, limit - Math.round(width * at));
		return new long[] {low, low + width};
	}

	/** {@link #band(long, long, int)} for this haggle. */
	public long @org.jspecify.annotations.Nullable [] band(int speech) {
		return band(this.list, this.limit, speech);
	}
}
