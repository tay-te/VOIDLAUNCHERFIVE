package dev.voidmc.mettle.character.cards;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.voidmc.mettle.Mettle;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Every lever of cards v2 (docs/CARDS.md §15, BALANCE.md §11), read from {@code config/mettle/cards.json} at server start
 * (written with the defaults when missing). A missing field keeps its default; a value out of its range is set back into
 * it with a warning. Read-only after loading: the rules read {@link #get()}.
 *
 * <p>The server sends it to each client on join ({@code mettle:cards/config}); on the client's own thread {@link #get()}
 * then answers with the server's copy, so the overlay and the client's CardRules sums use the server's numbers. In a
 * single-player world both threads hold the same numbers; a client on a server without Mettle keeps the defaults.
 */
public final class CardConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Gson WIRE = new Gson();
	private static volatile CardConfig current = new CardConfig();
	/** The server's config as the client received it, and the client thread that reads it (null: the defaults). */
	private static volatile CardConfig client;
	private static volatile Thread clientThread;

	/** The binder's hard ceiling: the whole cache is one synced attachment, about 520 bytes a card (Fabric's limit is ~1 MB). */
	public static final int BINDER_CEILING = 1500;

	// Deck slots (§5.1): from skill 2, base + one every three levels, at most max; +1 at Wits 8, +2 at Wits 14.
	public int slots_from = 2;
	public int slots_base = 3;
	public int slots_every = 3;
	public int slots_max = 8;
	public int[] wits_slots = {8, 14};

	// Rarity at skill level L (§3.2), in percent: base + slope × L for Common, Rare, Epic.
	public double[] rarity_odds = {70, -1.5, 24, 1, 6, 0.5};
	// A perk you own is dealt at this weight per copy owned, never below the floor (§3.1).
	public double duplicate_weight = 0.5;
	public double duplicate_floor = 0.25;

	// Traits (§4): +1.5 % of a value per point of its attribute above 3; a card's value is capped at its max × the
	// card-cap boost (so a broad 12 % reaches 15 %); one trait's total at twice the card cap.
	public double trait_boost_per_point = 0.015;
	public double card_cap_boost = 1.25;
	public double trait_total_cap = 2.0;

	// Sets (§6): three active; pins change once a day.
	public int active_sets = 3;
	public int set_pin_cooldown_days = 1;

	// Rebuilds (§7): fees by requirement band 2–5, 6–11, 12–17, 18–20 in bits; a Mythic's fee; emeralds without Hearth
	// (the same four bands, then the Mythic).
	public int[] rebuild_fees = {40, 120, 350, 900};
	public int mythic_fee = 1000;
	public int[] rebuild_emeralds = {1, 3, 8, 20, 24};
	public double city_master_scar_chance = 0.5;
	public int free_swaps_max = 3;
	public int free_rebuild_below_level = 5;

	// Trading (§8) and the binder (§7.5).
	public int attune_ticks = 24000;
	public int trade_range = 8;
	public int trade_countdown_s = 3;
	public int binder_max = 300;
	public int release_per_reroll = 3;

	// Mythics (§9).
	public double mythic_draw_chance = 0.005;
	public int mythic_draw_from = 15;
	public int mythic_slots = 2;
	public int far_horizon_blocks = 10000;

	public static CardConfig get() {
		CardConfig c = client;
		if (c != null && Thread.currentThread() == clientThread) {
			return c;
		}
		return current;
	}

	/** The config as sent to clients (JSON). */
	public static String wire() {
		return WIRE.toJson(current);
	}

	/** The client got the server's config (client thread): {@link #get()} answers with it on that thread. */
	public static void receive(String json) {
		CardConfig read;
		try {
			read = WIRE.fromJson(json, CardConfig.class);
		} catch (RuntimeException e) {
			Mettle.LOGGER.warn("the server's card config could not be read, using the defaults: {}", e.toString());
			read = new CardConfig();
		}
		clientThread = Thread.currentThread();
		client = read == null ? new CardConfig() : read.validated();
		revision++;
	}

	/** The client left the server: back to the defaults. */
	public static void forgetServer() {
		client = null;
		clientThread = null;
		revision++;
	}

	private static volatile long revision;

	/** Bumps when the client's copy changes (for MettleState.revision). */
	public static long clientRevision() {
		return revision;
	}

	/** This config with every value in its range (warnings name what was changed). */
	CardConfig validated() {
		if (this.binder_max < 1 || this.binder_max > BINDER_CEILING) {
			int was = this.binder_max;
			this.binder_max = Math.clamp(this.binder_max, 1, BINDER_CEILING);
			Mettle.LOGGER.warn("config/mettle/cards.json: binder_max {} is out of range (1 to {}: the whole binder is synced as one packet); using {}", was,
				BINDER_CEILING, this.binder_max);
		}
		this.slots_max = clampWarn("slots_max", this.slots_max, 1, 20);
		this.free_swaps_max = clampWarn("free_swaps_max", this.free_swaps_max, 0, 1000);
		this.active_sets = clampWarn("active_sets", this.active_sets, 0, 10);
		this.mythic_slots = clampWarn("mythic_slots", this.mythic_slots, 0, 10);
		this.attune_ticks = clampWarn("attune_ticks", this.attune_ticks, 0, Integer.MAX_VALUE);
		this.trade_countdown_s = clampWarn("trade_countdown_s", this.trade_countdown_s, 0, 60);
		this.release_per_reroll = clampWarn("release_per_reroll", this.release_per_reroll, 1, 9);
		if (this.wits_slots == null || this.wits_slots.length < 2) {
			Mettle.LOGGER.warn("config/mettle/cards.json: wits_slots needs two values; using the defaults");
			this.wits_slots = new CardConfig().wits_slots;
		}
		if (this.rarity_odds == null || this.rarity_odds.length < 6) {
			Mettle.LOGGER.warn("config/mettle/cards.json: rarity_odds needs six values; using the defaults");
			this.rarity_odds = new CardConfig().rarity_odds;
		}
		if (this.rebuild_fees == null || this.rebuild_fees.length < 4) {
			Mettle.LOGGER.warn("config/mettle/cards.json: rebuild_fees needs four values; using the defaults");
			this.rebuild_fees = new CardConfig().rebuild_fees;
		}
		if (this.rebuild_emeralds == null || this.rebuild_emeralds.length < 5) {
			Mettle.LOGGER.warn("config/mettle/cards.json: rebuild_emeralds needs five values; using the defaults");
			this.rebuild_emeralds = new CardConfig().rebuild_emeralds;
		}
		return this;
	}

	private static int clampWarn(String name, int value, int min, int max) {
		if (value < min || value > max) {
			int to = Math.clamp(value, min, max);
			Mettle.LOGGER.warn("config/mettle/cards.json: {} {} is out of range ({} to {}); using {}", name, value, min, max, to);
			return to;
		}
		return value;
	}

	/** For tests: put a config in place (and {@link #reset} after). */
	public static void use(CardConfig config) {
		current = config;
	}

	public static void reset() {
		current = new CardConfig();
	}

	/** Reads {@code <dir>/mettle/cards.json}, writing the defaults first when it is missing. */
	public static void load(Path configDir) {
		Path file = configDir.resolve("mettle").resolve("cards.json");
		try {
			if (Files.notExists(file)) {
				Files.createDirectories(file.getParent());
				try (Writer w = Files.newBufferedWriter(file)) {
					GSON.toJson(new CardConfig(), w);
				}
			}
			try (Reader r = Files.newBufferedReader(file)) {
				CardConfig read = GSON.fromJson(r, CardConfig.class);
				current = read == null ? new CardConfig() : read.validated();
			}
		} catch (IOException | RuntimeException e) {
			Mettle.LOGGER.warn("config/mettle/cards.json could not be read, using the defaults: {}", e.toString());
			current = new CardConfig();
		}
	}
}
