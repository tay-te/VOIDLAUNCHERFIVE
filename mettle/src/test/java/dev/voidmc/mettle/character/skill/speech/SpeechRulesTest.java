package dev.voidmc.mettle.character.skill.speech;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.mettle.character.skill.speech.SpeechRules.Holdings;
import dev.voidmc.mettle.compat.HearthKeys;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Speech's rules (docs/BUILD_FEEL.md §14.2): XP without Hearth, the effective Speech and Presence (never past 20, only the
 * listed sources), every bonus key with its value, the market edge and the price by kind of goods, the fallbacks, Golden
 * Mouth's top-up, the Mettle-only numbers.
 */
class SpeechRulesTest {
	/** A character: the fields by name, the rest empty. */
	static Holdings with(int level, int presence, int haggler, int glib, double persuasive, boolean voice, boolean honeyed, boolean commanding,
		boolean demagogue, boolean manners, boolean charmer, boolean secondWord) {
		return new Holdings(level, presence, haggler, glib, 0, persuasive, 0, 0, 0, 0, 0, false, charmer, commanding, secondWord, false, false, voice, honeyed,
			honeyed ? SpeechRules.HONEYED_LIES_COST : 0, demagogue, manners, false, 0, 0, false);
	}

	@Test
	void xpWithoutHearth() {
		assertEquals(3, SpeechRules.villagerTradeXp(0));
		assertEquals(3 + 12, SpeechRules.villagerTradeXp(12), "1 per emerald");
		assertEquals(60, SpeechRules.CURE_XP);
		assertEquals(100, SpeechRules.HERO_XP);
		assertEquals(1, SpeechRules.BARTER_XP);
	}

	@Test
	void effectiveSpeechAndPresence() {
		Holdings none = Holdings.NONE;
		assertEquals(0, SpeechRules.effectiveSpeech(none, true));
		assertEquals(3, SpeechRules.effectivePresence(none, true));
		Holdings glib = with(5, 8, 0, 3, 2, false, false, false, false, false, false, false);
		assertEquals(5 + 3 + 2, SpeechRules.effectiveSpeech(glib, true), "Glib III and Persuasive 2");
		Holdings all = with(15, 18, 0, 3, 2, true, true, true, true, true, true, true);
		assertEquals(20, SpeechRules.effectiveSpeech(all, false), "never past 20");
		assertEquals(20, SpeechRules.effectivePresence(all, false), "never past 20");
		Holdings courtier = with(6, 9, 0, 0, 0, true, false, true, false, true, false, false);
		assertEquals(6 + 4, SpeechRules.effectiveSpeech(courtier, true), "Voice of the Realm: Speech +4");
		assertEquals(9 + 2 + 2 + 2, SpeechRules.effectivePresence(courtier, true), "Commanding Voice, Voice, The Courtier's Manners");
		Holdings demagogue = with(0, 5, 0, 0, 0, false, false, false, true, false, false, false);
		assertEquals(9, SpeechRules.effectivePresence(demagogue, true));
		// Only the listed sources add, over many random characters.
		Random r = new Random(7);
		for (int i = 0; i < 20_000; i++) {
			int level = r.nextInt(21);
			int presence = 1 + r.nextInt(20);
			Holdings h = new Holdings(level, presence, r.nextInt(4), r.nextInt(4), r.nextDouble() * 0.05, r.nextInt(5), r.nextInt(7), r.nextDouble() * 0.3,
				r.nextDouble() * 0.2, r.nextDouble() * 0.2, r.nextDouble() * 0.3, r.nextBoolean(), r.nextBoolean(), r.nextBoolean(), r.nextBoolean(),
				r.nextBoolean(), r.nextBoolean(), r.nextBoolean(), r.nextBoolean(), 0.05, r.nextBoolean(), r.nextBoolean(), r.nextBoolean(), r.nextDouble() * 0.1,
				r.nextDouble() * 0.1, r.nextBoolean());
			boolean bonus = r.nextBoolean();
			int speech = SpeechRules.effectiveSpeech(h, bonus);
			int want = Math.min(20, level + new int[] {0, 1, 2, 3}[h.glib()] + (int) h.persuasive() + (h.voice() ? 4 : 0) + (h.honeyedLies() ? 4 : 0)
				+ (!bonus && h.secondWord() ? 1 : 0));
			assertEquals(want, speech);
			int pres = SpeechRules.effectivePresence(h, bonus);
			assertTrue(pres >= presence && pres <= 20);
			int wantP = Math.min(20, presence + (h.commandingVoice() ? 2 : 0) + (h.voice() ? 2 : 0) + (h.demagogue() ? 4 : 0) + (h.manners() ? 2 : 0)
				+ (!bonus && h.charmer() ? 1 : 0));
			assertEquals(Math.max(presence, wantP), pres);
		}
	}

	@Test
	void everyBonusKey() {
		Holdings h = new Holdings(10, 10, 0, 0, 0, 0, 2, 0.12, 0.06, 0.05, 0.08, true, true, false, true, true, true, false, false, 0, false, false, false, 0, 0,
			false);
		assertEquals(5 + 2, SpeechRules.bonus(h, HearthKeys.HAGGLE_BAND), 1e-9, "Shrewd Eye 5 and Shrewd 2");
		assertEquals(0.12, SpeechRules.bonus(h, HearthKeys.HAGGLE_PATIENCE), 1e-9, "Patient");
		assertEquals(1, SpeechRules.bonus(h, HearthKeys.HAGGLE_NEVER_SOUR), "Merchant Prince");
		assertEquals(0.06, SpeechRules.bonus(h, HearthKeys.SPEECH + "persuade"), 1e-9, "Smooth");
		assertEquals(0.06, SpeechRules.bonus(h, HearthKeys.SPEECH + "flatter"), 1e-9, "Smooth");
		assertEquals(0.05, SpeechRules.bonus(h, HearthKeys.SPEECH + "intimidate"), 1e-9, "Imposing");
		assertEquals(0, SpeechRules.bonus(h, HearthKeys.SPEECH + "bribe"), "nothing answers a bribe");
		assertEquals(1, SpeechRules.bonus(h, HearthKeys.SPEECH_RETRY), "Second Word");
		assertEquals(0.25 + 0.08, SpeechRules.bonus(h, HearthKeys.STANDING_GAIN), 1e-9, "Charmer and Charming");
		assertEquals(1, SpeechRules.bonus(h, HearthKeys.GREET_HERO), "Hero's Welcome");
		assertEquals(0, SpeechRules.bonus(h, HearthKeys.STEALTH_SIGHT), "a key Speech doesn't answer");
		assertEquals(0, SpeechRules.bonus(h, "nonsense"));
		for (String k : HearthKeys.ALL) {
			assertEquals(0, SpeechRules.bonus(Holdings.NONE, k), k + " with nothing held");
		}
		// Shrewd counts whole levels only.
		Holdings shrewd = new Holdings(0, 3, 0, 0, 0, 0, 2.5, 0, 0, 0, 0, false, false, false, false, false, false, false, false, 0, false, false, false, 0, 0, false);
		assertEquals(2, SpeechRules.bonus(shrewd, HearthKeys.HAGGLE_BAND));
	}

	@Test
	void theMarketEdgeAndGoods() {
		Holdings haggler = new Holdings(0, 3, 3, 0, 0.02, 0, 0, 0, 0, 0, 0, false, false, false, false, true, false, false, true, 0.05, false, false, false, 0, 0,
			false);
		assertEquals(0.06 + 0.02 + 0.05 - 0.05, SpeechRules.edge(haggler, true), 1e-9, "Haggler III, Haggling 2 %, Merchant Prince, Honeyed Lies' cost");
		assertEquals(0.06 + 0.02 + 0.05 - 0.05 + 0.02, SpeechRules.edge(haggler, false), 1e-9, "and never-sour's fallback edge without keys");
		assertEquals(0, SpeechRules.edge(Holdings.NONE, false));
		Holdings goods = new Holdings(0, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false, false, false, false, false, false, false, 0, false, false, true, 0.05, 0.06,
			true);
		assertEquals(0.9 * 0.95, SpeechRules.kindFactor(goods, true, false, false, false, true), 1e-9, "food bought: Provisioner and Provisioning");
		assertEquals(1.1, SpeechRules.kindFactor(goods, true, false, false, false, false), 1e-9, "food sold: Provisioner");
		assertEquals(1.1 * 1.2, SpeechRules.kindFactor(goods, true, false, false, true, false), 1e-9, "food you cooked: and Tavern-Keeper");
		assertEquals(0.9 * 0.95, SpeechRules.kindFactor(goods, false, true, false, false, true), 1e-9, "a potion bought");
		assertEquals(1.06, SpeechRules.kindFactor(goods, false, false, true, false, false), 1e-9, "a tool sold: Appraising");
		assertEquals(1, SpeechRules.kindFactor(goods, false, false, true, false, true), 1e-9, "a tool bought: nothing");
		assertEquals(1, SpeechRules.kindFactor(goods, false, false, false, false, false), 1e-9, "stone: nothing");
	}

	@Test
	void goldenMouthAndMettleOnlyNumbers() {
		assertEquals(30, SpeechRules.goldenMouthTopUp(15, false), 1e-9, "a talk check's third → full");
		assertEquals(13, SpeechRules.goldenMouthTopUp(7, true), 1e-9, "a law check's 7 → 20");
		assertEquals(0, SpeechRules.goldenMouthTopUp(0, false));
		assertEquals(0, SpeechRules.villagerCut(3, 3), "only offers of 4 emeralds or more");
		assertEquals(3, SpeechRules.villagerCut(3, 4));
		assertEquals(1, SpeechRules.villagerCut(1, 20));
		assertEquals(0.5, SpeechRules.gossip(2), 1e-9);
		assertEquals(72_000, SpeechRules.heroTicks(48_000, true, 0), "Hero's Welcome: three days");
		assertEquals(90_000, SpeechRules.heroTicks(48_000, true, 0.25), "and Heroic on top");
		assertEquals(60_000, SpeechRules.heroTicks(48_000, false, 0.25));
		assertEquals(1.1 * 1.05, SpeechRules.xp(true, 0.1, 0.8, 0.05), 1e-9, "Gregarious and allies on Speech");
		assertEquals(0.8 * 1.05, SpeechRules.xp(false, 0.1, 0.8, 0.05), 1e-9, "Demagogue's price on the rest");
		assertEquals(0f, SpeechRules.coinWeight(true, 0.3), "Ledger-Keeper: coins weigh nothing");
		assertEquals(0.7f, SpeechRules.coinWeight(false, 0.3), 1e-6, "Coinwise");
	}
}
