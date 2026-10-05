package dev.voidmc.mettle.character.skill.athletics;

import dev.voidmc.mettle.api.MettleServices;
import dev.voidmc.mettle.character.FeatCounters;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.character.skill.AthleticsSkill;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;

/**
 * Athletics XP from the distance stats vanilla keeps anyway (ServerPlayer#checkMovementStatistics): sprinting, swimming
 * (sprint-swimming and diving) and climbing. Vanilla only counts these on foot or in the water, never in a boat, a cart
 * or on elytra, which is the rule. The load band multiplies, the minute cap limits, and XP is paid in whole points so
 * the character record doesn't resync every tick. Also counts the metres for Ironlung.
 */
public final class AthleticsXp {
	public static final String IRONLUNG_COUNTER = "athletics_swim_underwater_cm";

	private AthleticsXp() {
	}

	public static void stat(ServerPlayer player, Stat<?> stat, int count) {
		if (count <= 0 || stat.getType() != Stats.CUSTOM || !(stat.getValue() instanceof Identifier id)) {
			return;
		}
		AthleticsRules.Move move = move(id);
		if (move == null) {
			return;
		}
		AthleticsState state = AthleticsState.of(player);
		if (move == AthleticsRules.Move.SWIM && player.isEyeInFluid(FluidTags.WATER) && !Perks.has(player, AthleticsSkill.IRONLUNG)) {
			state.underwaterCm += count;
			if (state.underwaterCm >= AthleticsRules.IRONLUNG_FLUSH_CM) {
				long cm = state.underwaterCm;
				state.underwaterCm = 0;
				FeatCounters.add(player, IRONLUNG_COUNTER, cm);
			}
		}
		if (player.isPassenger() || player.isFallFlying() || player.isCreative() || player.isSpectator()) {
			return;
		}
		state.pendingXp += AthleticsRules.xp(move, count, MettleServices.burden().band(player));
		if (state.pendingXp >= 1) {
			double paid = state.meter.take(state.pendingXp, player.level().getGameTime());
			state.pendingXp = 0;
			if (paid > 0) {
				Progress.award(player, AthleticsSkill.ATHLETICS, paid);
			}
		}
	}

	private static AthleticsRules.Move move(Identifier id) {
		if (id.equals(Stats.SPRINT_ONE_CM)) {
			return AthleticsRules.Move.SPRINT;
		}
		if (id.equals(Stats.SWIM_ONE_CM) || id.equals(Stats.WALK_UNDER_WATER_ONE_CM)) {
			return AthleticsRules.Move.SWIM;
		}
		return id.equals(Stats.CLIMB_ONE_CM) ? AthleticsRules.Move.CLIMB : null;
	}
}
