package dev.voidmc.mettle.character.skill.survival;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.LoadBand;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.MettleServices;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.FeatCounters;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.SurvivalSkill;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * The general feats' tallies (TREES.md §12) and Nine Lives' speed. Seen It All is counted by {@link Discovery}; its
 * attribute point and Jack of All Trades' faster XP are applied where those numbers are made (see the requests file).
 */
public final class GeneralFeats {
	public static final Identifier NINE_LIVES_SPEED = Mettle.id("survival/nine_lives/movement_speed");

	private static final Map<UUID, Long> NEAR_DEATH_AT = new HashMap<>();
	private static final Map<UUID, Vec3> WALKED_FROM = new HashMap<>();
	private static final Map<UUID, Double> WALKED = new HashMap<>();

	private GeneralFeats() {
	}

	public static void init() {
		// Nine Lives: down to half a heart or less, and still alive after the hit.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseTaken, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && player.isAlive() && taken > 0 && player.getHealth() <= SurvivalRules.NINE_LIVES_HEALTH
				&& !player.isCreative()) {
				long now = player.level().getGameTime();
				Long last = NEAR_DEATH_AT.get(player.getUUID());
				if (last == null || now - last >= SurvivalRules.NINE_LIVES_COOLDOWN || now < last) {
					NEAR_DEATH_AT.put(player.getUUID(), now);
					FeatCounters.add(player, SurvivalSkill.NEAR_DEATHS, 1);
				}
			}
		});
	}

	/** Every five ticks. */
	public static void tick(ServerPlayer player, int tick) {
		// Nine Lives: below three hearts you move 20 % faster.
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			boolean want = player.getHealth() < SurvivalRules.NINE_LIVES_BELOW && player.isAlive() && Perks.has(player, SurvivalSkill.NINE_LIVES);
			if (want != speed.hasModifier(NINE_LIVES_SPEED)) {
				if (want) {
					speed.addTransientModifier(new AttributeModifier(NINE_LIVES_SPEED, SurvivalRules.NINE_LIVES_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
				} else {
					speed.removeModifier(NINE_LIVES_SPEED);
				}
			}
		}
		if (tick % 20 == 0) {
			packHorse(player);
		}
		if (tick % 100 == 0) {
			seasoned(player);
			jackOfAllTrades(player);
		}
	}

	/** Pack Horse: metres walked on foot while Overburdened or Pinned. */
	private static void packHorse(ServerPlayer player) {
		Vec3 now = player.position();
		Vec3 before = WALKED_FROM.put(player.getUUID(), now);
		if (before == null || Perks.has(player, SurvivalSkill.PACK_HORSE)) {
			return;
		}
		LoadBand band = MettleServices.burden().band(player);
		boolean heavy = band == LoadBand.OVERBURDENED || band == LoadBand.PINNED;
		boolean onFoot = !player.isPassenger() && !player.isFallFlying() && !player.getAbilities().flying && !player.isCreative();
		double step = SurvivalRules.packHorseStep(now.x - before.x, now.z - before.z, onFoot, heavy);
		if (step <= 0) {
			return;
		}
		double total = WALKED.merge(player.getUUID(), step, Double::sum);
		if (total >= 1) {
			long whole = (long) Math.floor(total);
			WALKED.put(player.getUUID(), total - whole);
			FeatCounters.add(player, SurvivalSkill.OVERBURDENED_METRES, whole);
		}
	}

	/** Seasoned: the current life's length, from vanilla's own "time since death" statistic. */
	private static void seasoned(ServerPlayer player) {
		if (Perks.has(player, SurvivalSkill.SEASONED)) {
			return;
		}
		int alive = player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_DEATH));
		long delta = alive - FeatCounters.get(player, SurvivalSkill.LIFE_TICKS);
		if (delta != 0) {
			FeatCounters.add(player, SurvivalSkill.LIFE_TICKS, delta);
		}
	}

	/** Jack of All Trades: how many of the released (wave 1) skills are at level 5. */
	private static void jackOfAllTrades(ServerPlayer player) {
		if (Perks.has(player, SurvivalSkill.JACK_OF_ALL_TRADES)) {
			return;
		}
		CharacterState state = CharacterStore.get(player);
		int count = 0;
		for (Holder.Reference<Skill> skill : player.level().registryAccess().lookupOrThrow(MettleRegistries.SKILL).listElements().toList()) {
			if (skill.value().released() && state.level(skill.key()) >= SurvivalRules.JACK_LEVEL) {
				count++;
			}
		}
		long delta = count - FeatCounters.get(player, SurvivalSkill.SKILLS_AT_FIVE);
		if (delta != 0) {
			FeatCounters.add(player, SurvivalSkill.SKILLS_AT_FIVE, delta);
		}
	}

	// ---------------------------------------------------------------- hooks for the numbers other code makes

	/** Jack of All Trades: XP multiplier for a skill (Progress#multiplier). */
	public static double jackBonus(CharacterState state, net.minecraft.resources.ResourceKey<Skill> skill) {
		return state.rank(Perks.key(SurvivalSkill.JACK_OF_ALL_TRADES)) > 0 && state.level(skill) < SurvivalRules.JACK_BELOW ? SurvivalRules.JACK_BONUS : 1.0;
	}

	/** Seen It All: attribute points it adds (Perks#attributePoints). */
	public static int bonusAttributePoints(net.minecraft.world.entity.player.Player player) {
		return Perks.has(player, SurvivalSkill.SEEN_IT_ALL) ? 1 : 0;
	}

	public static void forget(UUID player) {
		NEAR_DEATH_AT.remove(player);
		WALKED_FROM.remove(player);
		WALKED.remove(player);
	}

	public static void clear() {
		NEAR_DEATH_AT.clear();
		WALKED_FROM.clear();
		WALKED.clear();
	}
}
