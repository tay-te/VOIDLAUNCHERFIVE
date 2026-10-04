package dev.voidmc.hearth.standing.speech;

import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.standing.StandingConfig;
import dev.voidmc.hearth.standing.StandingEngine;
import dev.voidmc.hearth.standing.StandingModule;
import dev.voidmc.hearth.standing.appearance.Look;
import dev.voidmc.hearth.standing.model.FolkFacts;
import dev.voidmc.hearth.standing.model.Memories;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Speech checks for any module (docs/STANDING.md, "Speech checks"): persuade, intimidate, bribe and flatter a resident,
 * with odds from the player's standing, the resident's opinion, how the player looks and the resident's traits. The law
 * module uses it for guards and witnesses, the economy for haggling, the quest module for talking a giver round.
 *
 * <pre>
 *   SpeechOdds.Odds odds = SpeechCheck.odds(player, town, resident, SpeechKind.PERSUADE, 0.2, 0);
 *   options.add(new Option(id, SpeechCheck.label(odds, Component.literal("Surely you can let it pass?")), 300));
 *   ...
 *   SpeechCheck.Outcome o = SpeechCheck.attempt(player, town, resident, SpeechKind.PERSUADE, 0.2, 0);
 *   if (o.success()) { ... }
 * </pre>
 *
 * Works without the standing module's service (odds then come from the sim's reputation and a neutral look). Server
 * thread only. A check never takes coin: for bribes the caller charges the player on success (KCD's rule: a refused
 * bribe stays in your purse).
 */
public final class SpeechCheck {
	private SpeechCheck() {
	}

	/**
	 * @param chance the odds it was rolled at
	 * @param roll   the roll, 0…1 (success when below the chance)
	 */
	public record Outcome(SpeechKind kind, boolean success, double chance, double roll) {
		public int percent() {
			return (int) Math.round(this.chance * 100);
		}
	}

	/**
	 * The odds of a check now.
	 *
	 * @param difficulty how hard the ask is: 0 an easy favour, 0.2 a real favour, 0.5 asking them to break the law
	 * @param bribeBits  coin offered (bribes only; compared with {@link #expectedBribe})
	 */
	public static SpeechOdds.Odds odds(ServerPlayer player, Settlement settlement, Resident resident, SpeechKind kind, double difficulty, long bribeBits) {
		StandingEngine engine = StandingModule.engine();
		StandingConfig cfg = StandingModule.config();
		int standing;
		int opinion;
		Look look;
		if (engine != null) {
			standing = engine.standing(settlement, player.getUUID());
			opinion = engine.opinion(settlement, resident, player.getUUID());
			look = engine.look(player);
		} else {
			standing = settlement.reputation(player.getUUID());
			opinion = standing / 4;
			look = Look.NEUTRAL;
		}
		long expected = expectedBribe(resident, difficulty);
		double ratio = kind == SpeechKind.BRIBE ? bribeBits / (double) Math.max(1, expected) : 0;
		SpeechOdds.Inputs in = new SpeechOdds.Inputs(standing, opinion, look.charisma(), look.menace(), FolkFacts.traits(resident), FolkFacts.mood(resident),
			resident.station, resident.job, difficulty, ratio, recentTries(engine, resident, player, kind));
		return SpeechOdds.of(kind, in, cfg.speech().minChance(), cfg.speech().maxChance());
	}

	/**
	 * Rolls a check and leaves its memory with the resident (a threat sours them even when it works; a refused bribe
	 * offends). Marks the settlement dirty.
	 */
	public static Outcome attempt(ServerPlayer player, Settlement settlement, Resident resident, SpeechKind kind, double difficulty, long bribeBits) {
		SpeechOdds.Odds odds = odds(player, settlement, resident, kind, difficulty, bribeBits);
		double roll = player.getRandom().nextDouble();
		boolean success = roll < odds.chance();
		StandingEngine engine = StandingModule.engine();
		if (engine != null && StandingModule.config().speech().consequences()) {
			engine.remember(resident, player.getUUID(), kind.memoryKind(success), success ? kind.successMemory : kind.failureMemory);
			settlement.markDirty();
		}
		return new Outcome(kind, success, odds.chance(), roll);
	}

	/** What the resident expects to be paid to look the other way, in bits (station, difficulty and the server's multiplier). */
	public static long expectedBribe(Resident resident, double difficulty) {
		double m = StandingModule.config().speech().bribeMultiplier();
		return Math.max(1, Math.round(SpeechOdds.expectedBribe(resident.station) * m * (1 + 3 * Math.max(0, difficulty))));
	}

	/** "[Persuade 62%] text": an option label with the odds shown, coloured by how good they are. */
	public static MutableComponent label(SpeechOdds.Odds odds, Component text) {
		int pct = odds.percent();
		ChatFormatting colour = pct >= 66 ? ChatFormatting.DARK_GREEN : pct >= 40 ? ChatFormatting.GOLD : ChatFormatting.DARK_RED;
		return Component.literal("[").append(Component.translatable(odds.kind().translationKey())).append(" " + pct + "%] ").withStyle(colour)
			.append(text.copy().withStyle(ChatFormatting.RESET));
	}

	/** Tries of this kind on this resident today (each makes the next one harder). */
	private static int recentTries(@Nullable StandingEngine engine, Resident resident, ServerPlayer player, SpeechKind kind) {
		if (engine == null) {
			return 0;
		}
		long today = engine.host().day();
		Memories m = Memories.of(resident);
		int n = 0;
		if (m.has(player.getUUID(), kind.memoryKind(true), today)) {
			n++;
		}
		if (m.has(player.getUUID(), kind.memoryKind(false), today)) {
			n++;
		}
		return n;
	}
}
