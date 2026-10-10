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
 * (written with the defaults when missing). A missing field keeps its default. Read-only after loading: the rules read
 * {@link #get()}.
 */
public final class CardConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static CardConfig current = new CardConfig();

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
		return current;
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
				current = read == null ? new CardConfig() : read;
			}
		} catch (IOException | RuntimeException e) {
			Mettle.LOGGER.warn("config/mettle/cards.json could not be read, using the defaults: {}", e.toString());
			current = new CardConfig();
		}
	}
}
