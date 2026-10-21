package dev.voidmc.mettle.compat.hearth;

import dev.voidmc.hearth.api.MarketEvents;
import dev.voidmc.hearth.api.QuestEvents;
import dev.voidmc.hearth.api.QuestType;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.MettleEvents;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.character.skill.Combat;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * XP for what the player does in Hearth's towns: a quest completed ({@link HearthRules#questXp}) and business done at a
 * market counter ({@link HearthRules#tradeXp}). Hearth's own XP reports (checks, haggles, locks, pockets) come through
 * {@link MettlePlayerSkills#award}. Failed and abandoned quests give nothing.
 */
final class HearthXp {
	private static final List<ResourceKey<Skill>> COMBAT_SKILLS = List.of(Combat.BLADES, Combat.HEAVY_WEAPONS, Combat.MARKSMANSHIP);

	/** Combat XP since the player's last fighting quest ended: which weapon they "used most on it". */
	private static final Map<UUID, Map<ResourceKey<Skill>, Double>> FOUGHT = new HashMap<>();
	/** Crowns of business today, per player: {sim day, crowns}. */
	private static final Map<UUID, double[]> BUSINESS = new HashMap<>();

	private HearthXp() {
	}

	static void init() {
		QuestEvents.COMPLETED.register(HearthXp::questCompleted);
		MarketEvents.TRADED.register(HearthXp::traded);
		MettleEvents.XP_GAINED.register((player, skill, xp) -> {
			if (COMBAT_SKILLS.contains(skill)) {
				FOUGHT.computeIfAbsent(player.getUUID(), u -> new HashMap<>()).merge(skill, xp, Double::sum);
			}
		});
	}

	static void forget(UUID player) {
		FOUGHT.remove(player);
		BUSINESS.remove(player);
	}

	private static void questCompleted(ServerPlayer player, @Nullable Settlement settlement, QuestType type, Identifier template, SettlementTier tier) {
		HearthRules.QuestXp reward = HearthRules.questXp(type.getSerializedName(), template.getPath());
		boolean fighting = HearthRules.COMBAT.equals(reward.skill());
		ResourceKey<Skill> skill = fighting ? foughtWith(player) : MettlePlayerSkills.skillKey(player, reward.skill());
		if (skill != null) {
			Mettle.LOGGER.debug("{} completed {} ({}): {} XP in {}", player.getName().getString(), template, type.getSerializedName(), reward.xp(), skill.identifier());
			Progress.award(player, skill, reward.xp());
		}
		if (fighting) {
			// After the award: the quest's own combat XP isn't fighting done towards the next one.
			FOUGHT.remove(player.getUUID());
		}
	}

	/** The combat skill that earned the most XP since the last fighting quest; else the player's best of the three. */
	private static ResourceKey<Skill> foughtWith(ServerPlayer player) {
		Map<ResourceKey<Skill>, Double> fought = FOUGHT.get(player.getUUID());
		ResourceKey<Skill> best = null;
		double most = 0;
		if (fought != null) {
			for (Map.Entry<ResourceKey<Skill>, Double> e : fought.entrySet()) {
				if (e.getValue() > most) {
					most = e.getValue();
					best = e.getKey();
				}
			}
		}
		if (best != null) {
			return best;
		}
		CharacterState state = CharacterStore.get(player);
		best = Combat.BLADES;
		for (ResourceKey<Skill> s : COMBAT_SKILLS) {
			if (state.xp(s) > state.xp(best)) {
				best = s;
			}
		}
		return best;
	}

	private static void traded(ServerPlayer player, Settlement settlement, ItemStack item, int count, long bits, boolean playerBuys) {
		if (bits <= 0) {
			return;
		}
		long day = Trainers.day(player);
		double[] today = BUSINESS.computeIfAbsent(player.getUUID(), u -> new double[] {day, 0});
		if (today[0] != day) {
			today[0] = day;
			today[1] = 0;
		}
		double crowns = bits / (double) HearthRules.BITS_PER_CROWN;
		// Ledger-Keeper (Speech): business never tapers.
		boolean ledger = dev.voidmc.mettle.character.Perks.has(player, dev.voidmc.mettle.character.skill.SpeechSkill.LEDGER_KEEPER);
		double xp = ledger ? crowns * HearthRules.SPEECH_XP_PER_CROWN : HearthRules.tradeXp(today[1], crowns);
		today[1] += crowns;
		if (xp > 0) {
			Progress.award(player, MettlePrices.SPEECH, xp);
		}
	}
}
