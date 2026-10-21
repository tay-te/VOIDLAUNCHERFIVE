package dev.voidmc.hearth.sim.settlement;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.sim.lives.Life;
import dev.voidmc.hearth.sim.lives.Lives;
import dev.voidmc.hearth.sim.lives.LivesClaims;
import dev.voidmc.hearth.sim.lives.LivesView;
import dev.voidmc.hearth.sim.lives.TownLives;
import dev.voidmc.hearth.social.SocialConfig;
import dev.voidmc.hearth.social.SocialLedger;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.life.FamilyLife;
import dev.voidmc.hearth.social.life.Matchmaker;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The 200-day run of docs/LIVING_WORLD_2.md §4.8: a town grown by the real sim (the real planner and blueprints, the
 * economy's books) with the lives stepped each day after the clock (the claims in the clock's job assignment) and the
 * social module's weddings. Once it is a village with its workshops standing (day 180), its next 200 days must bring at
 * least one apprentice made master, one feud, one sweethearts' wedding, and named characters whose threads move on.
 * Then the same town's lives are stepped on their own, warm, for what a day costs (§4.4: ≤ 30 µs per 150 people).
 */
class LivesHarnessTest {
	static final int GROW = 180;
	static final int WINDOW = 200;
	private static boolean registered;

	@BeforeAll
	static void claims() {
		if (!ResidentClaims.active()) {
			ResidentClaims.register(LivesClaims.INSTANCE);
			registered = true;
		}
		Matchmaker.sweethearts(Life::sweetheart);
	}

	@AfterAll
	static void forget() {
		if (registered) {
			ResidentClaims.clear();
		}
		Matchmaker.sweethearts(r -> null);
	}

	@Test
	void twoHundredDaysOfLivesInAVillage() {
		GrowthHarness h = GrowthHarness.found("flat", 3, true);
		SimSettlement town = h.town;
		SocialConfig.Family family = SocialConfig.DEFAULTS.family();
		StringBuilder log = new StringBuilder();
		Map<String, Integer> before = new HashMap<>();
		SettlementTier tierAt = null;
		for (int d = 1; d <= GROW + WINDOW; d++) {
			h.day();
			SocialLedger ledger = town.data(SocialModule.LEDGER);
			FamilyLife.day(town, ledger, d, family, p -> {
			});
			Lives.step(town, d);
			if (d == GROW) {
				before.putAll(LivesView.stats(town));
				tierAt = town.tier();
			}
			if (d % 25 == 0) {
				log.append(String.format(Locale.ROOT, "day %3d: %3d souls %-7s %s%n", d, town.population(), town.tier().getSerializedName(),
					new TreeMap<>(LivesView.stats(town))));
			}
		}
		TownLives lives = LivesView.town(town);
		Map<String, Integer> st = lives.stats.all();
		StringBuilder who = new StringBuilder();
		for (TownLives.Notable n : lives.notables) {
			Resident r = town.resident(n.resident);
			who.append(String.format(Locale.ROOT, "  %s: %s at %s, path %s%n", r == null ? "?" : r.fullName(), n.role, n.beat, n.path));
		}
		String report = log + who.toString();
		System.out.print(report);
		assertTrue(tierAt != null && tierAt.ordinal() >= SettlementTier.VILLAGE.ordinal(), "a village by day " + GROW + "\n" + report);
		assertTrue(delta(st, before, "masters_from_apprentices") >= 1, "an apprentice made master in the 200 days\n" + report);
		assertTrue(delta(st, before, "feuds") >= 1, "a feud in the 200 days\n" + report);
		assertTrue(delta(st, before, "sweetheart_weddings") >= 1, "a sweethearts' wedding in the 200 days\n" + report);
		assertTrue(!lives.notables.isEmpty() && delta(st, before, "beats") >= lives.notables.size(), "threads that have moved on\n" + report);

		// what a day of lives costs in this town, warm (as on a server stepping many towns)
		int warm = 3000;
		long t0 = 0;
		for (int d = GROW + WINDOW + 1; d <= GROW + WINDOW + warm; d++) {
			if (d == GROW + WINDOW + warm / 3) {
				t0 = System.nanoTime();
			}
			Lives.step(town, d);
		}
		double us = (System.nanoTime() - t0) / 1e3 / (warm - warm / 3 + 1);
		System.out.printf(Locale.ROOT, "[lives harness] %d people: %.1f us a sim day warm, %.1f us per 150 people (budget 30)%n", town.population(), us,
			us * 150.0 / Math.max(1, town.population()));
	}

	private static int delta(Map<String, Integer> now, Map<String, Integer> before, String key) {
		return now.getOrDefault(key, 0) - before.getOrDefault(key, 0);
	}
}
