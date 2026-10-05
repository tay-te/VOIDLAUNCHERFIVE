package dev.voidmc.mettle.character.skill.athletics;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.LoadBand;
import dev.voidmc.mettle.api.MettleServices;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.AthleticsSkill;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The movement perks: Runner, Courier's Stride, Hauler's Stride, Steady Pace, Vaulter and Spring as attribute modifiers
 * that follow the sprint, Marathon and Hauler's Stride on the sprint gate, Swimmer's stroke, Spring's push and the
 * sprint hunger. Movement is predicted by the client, so all of this runs on both sides from the same synced perks
 * and load band; the server's copy of the modifiers is what everyone else sees.
 *
 * <p>The modifiers carry their own ids ({@code mettle:athletics/*}), outside the perk applier's {@code mettle:perk/*},
 * because they come and go with the sprint.
 */
public final class Movement {
	public static final Identifier SPRINT_SPEED = Mettle.id("athletics/sprint");
	public static final Identifier HAULER_SPEED = Mettle.id("athletics/haulers_stride");
	public static final Identifier STEADY_SPEED = Mettle.id("athletics/steady_pace");
	public static final Identifier VAULTER_STEP = Mettle.id("athletics/vaulter");
	public static final Identifier SPRING_JUMP = Mettle.id("athletics/spring");

	private Movement() {
	}

	/** Once a tick for each player, on each side: the stride timer, then the modifiers. */
	public static void tick(Player player) {
		AthleticsState state = AthleticsState.of(player);
		boolean sprinting = sprinting(player);
		state.sprintTicks = sprinting ? state.sprintTicks + 1 : 0;
		boolean stride = sprinting && state.sprintTicks >= AthleticsRules.STRIDE_TICKS && Perks.has(player, AthleticsSkill.COURIERS_STRIDE);
		if (stride && !state.stride && player instanceof ServerPlayer sp) {
			AthleticsSkill.chip(sp, AthleticsSkill.COURIERS_STRIDE, "couriers_stride");
		}
		state.stride = stride;
		refresh(player);
		if (sprinting && player instanceof ServerPlayer sp) {
			sprintChips(sp, state);
		}
	}

	/** Sets the movement modifiers this moment calls for. Cheap when nothing changed. */
	public static void refresh(Player player) {
		AthleticsState state = AthleticsState.of(player);
		boolean sprinting = sprinting(player);
		LoadBand band = MettleServices.burden().band(player);
		set(player, Attributes.MOVEMENT_SPEED, SPRINT_SPEED,
			sprinting ? AthleticsRules.sprintSpeed(Perks.rank(player, AthleticsSkill.RUNNER), state.stride) : 0,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player, Attributes.MOVEMENT_SPEED, HAULER_SPEED,
			sprinting && band == LoadBand.OVERBURDENED && Perks.has(player, AthleticsSkill.HAULERS_STRIDE) ? AthleticsRules.HAULERS_SPRINT : 0,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player, Attributes.MOVEMENT_SPEED, STEADY_SPEED,
			band == LoadBand.BURDENED && Perks.has(player, AthleticsSkill.STEADY_PACE) ? AthleticsRules.steadyPaceSpeed(Perks.has(player, AthleticsSkill.OX)) : 0,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player, Attributes.STEP_HEIGHT, VAULTER_STEP, sprinting && Perks.has(player, AthleticsSkill.VAULTER) ? AthleticsRules.VAULTER_STEP : 0,
			AttributeModifier.Operation.ADD_VALUE);
		set(player, Attributes.JUMP_STRENGTH, SPRING_JUMP, sprinting && Perks.has(player, AthleticsSkill.SPRING) ? AthleticsRules.SPRING_JUMP : 0,
			AttributeModifier.Operation.ADD_VALUE);
	}

	private static boolean sprinting(Player player) {
		return player.isSprinting() && !player.isPassenger() && !player.isSpectator();
	}

	/** Marathon and Hauler's Stride at work: a chip, at most once a minute each. */
	private static void sprintChips(ServerPlayer player, AthleticsState state) {
		long now = player.level().getGameTime();
		if (!player.getFoodData().hasEnoughFood() && !player.getAbilities().mayfly && now - state.marathonChipAt >= 1200
			&& Perks.has(player, AthleticsSkill.MARATHON)) {
			state.marathonChipAt = now;
			AthleticsSkill.chip(player, AthleticsSkill.MARATHON, "marathon");
		}
		if (MettleServices.burden().band(player) == LoadBand.OVERBURDENED && now - state.haulerChipAt >= 1200
			&& Perks.has(player, AthleticsSkill.HAULERS_STRIDE)) {
			state.haulerChipAt = now;
			AthleticsSkill.chip(player, AthleticsSkill.HAULERS_STRIDE, "haulers_stride");
		}
	}

	// ---------------------------------------------------------------- mixin entry points

	/** Player#hasEnoughFoodToDoExhaustiveManoeuvres, after Marathon and Hauler's Stride. Asked by the client. */
	public static boolean canSprint(Player player, boolean original) {
		if (original) {
			return true;
		}
		return AthleticsRules.canSprint(false, player.getFoodData().hasEnoughFood() || player.getAbilities().mayfly,
			MettleServices.burden().band(player), Perks.has(player, AthleticsSkill.MARATHON), Perks.has(player, AthleticsSkill.HAULERS_STRIDE));
	}

	/** The hunger of a sprinted stretch or a sprint-jump (server). */
	public static float sprintExhaustion(Player player, float amount) {
		if (amount <= 0) {
			return amount;
		}
		return AthleticsRules.sprintExhaustion(amount, Perks.has(player, AthleticsSkill.MARATHON), AthleticsState.of(player).stride);
	}

	/** Swimmer: the stroke in LivingEntity#travelInWater. */
	public static float swimSpeed(LivingEntity entity, float speed) {
		return entity instanceof Player player && Perks.has(player, AthleticsSkill.SWIMMER) ? speed * AthleticsRules.SWIMMER_SPEED : speed;
	}

	/** Spring: a sprint-jump carries further (after vanilla's own sprint push). */
	public static void sprintJump(LivingEntity entity) {
		if (entity instanceof Player player && Perks.has(player, AthleticsSkill.SPRING)) {
			float angle = player.getYRot() * Mth.DEG_TO_RAD;
			player.addDeltaMovement(new Vec3(-Mth.sin(angle) * AthleticsRules.SPRING_PUSH, 0.0, Mth.cos(angle) * AthleticsRules.SPRING_PUSH));
		}
	}

	private static void set(Player player, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation op) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		AttributeModifier current = instance.getModifier(id);
		if (amount == 0) {
			if (current != null) {
				instance.removeModifier(id);
			}
			return;
		}
		if (current == null || current.amount() != amount || current.operation() != op) {
			instance.removeModifier(id);
			instance.addTransientModifier(new AttributeModifier(id, amount, op));
		}
	}
}
