package dev.voidmc.mettle.character.skill.survival;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.SurvivalSkill;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Life on the road: Campfire Rest, Pathfinder (faster on paths, unharmed by berry bushes), Self-Sufficient, and the
 * Poultice teaching. Pathfinder's speed is a modifier of its own ({@code mettle:survival/pathfinder/movement_speed}),
 * on while standing on a path or road.
 */
public final class Camp {
	public static final Identifier PATHFINDER_SPEED = Mettle.id("survival/pathfinder/movement_speed");

	private static final Map<UUID, Vec3> RESTING_AT = new HashMap<>();
	private static final Map<UUID, Integer> RESTED = new HashMap<>();
	/** Poultice: ticks of healing left, and when it can be used again. */
	private static final Map<UUID, Integer> POULTICE_LEFT = new HashMap<>();
	private static final Map<UUID, Long> POULTICE_READY = new HashMap<>();

	private Camp() {
	}

	public static void init() {
		UseItemCallback.EVENT.register((player, level, hand) -> poultice(player, hand));
		// Sneak-using herbs at a block would plant them; while a poultice is wanted, bind the wound instead.
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> poultice(player, hand));
	}

	// ---------------------------------------------------------------- Self-Sufficient and Pathfinder (mixins)

	/** Player#causeFoodExhaustion. */
	public static float exhaustion(Player player, float amount) {
		if (amount <= 0) {
			return amount;
		}
		// Wolf's Hunger (cursed) drains faster; Self-Sufficient slower. They multiply.
		float scaled = amount * dev.voidmc.mettle.character.draft.Cursed.hunger(player);
		return Perks.has(player, SurvivalSkill.SELF_SUFFICIENT) ? scaled * SurvivalRules.SELF_SUFFICIENT : scaled;
	}

	/** SweetBerryBushBlock#entityInside: Pathfinder walks through berry bushes unslowed and unscratched (both sides). */
	public static boolean ignoresBerryBush(Entity entity) {
		return entity instanceof Player player && Perks.has(player, SurvivalSkill.PATHFINDER);
	}

	public static boolean onPath(Player player) {
		return player.onGround() && player.level().getBlockState(player.getOnPos()).is(SurvivalSkill.PATHS);
	}

	// ---------------------------------------------------------------- every five ticks

	public static void tick(ServerPlayer player, int tick) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			boolean want = onPath(player) && Perks.has(player, SurvivalSkill.PATHFINDER);
			if (want != speed.hasModifier(PATHFINDER_SPEED)) {
				if (want) {
					speed.addTransientModifier(new AttributeModifier(PATHFINDER_SPEED, SurvivalRules.PATHFINDER_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
				} else {
					speed.removeModifier(PATHFINDER_SPEED);
				}
			}
		}
		Integer left = POULTICE_LEFT.get(player.getUUID());
		if (left != null) {
			// 8 health over the poultice's time, in even steps.
			player.heal(8f * 5 / SurvivalRules.POULTICE_TICKS);
			if (left <= 5) {
				POULTICE_LEFT.remove(player.getUUID());
			} else {
				POULTICE_LEFT.put(player.getUUID(), left - 5);
			}
		}
		if (tick % 20 == 0) {
			campfireRest(player);
		}
	}

	private static void campfireRest(ServerPlayer player) {
		UUID id = player.getUUID();
		Vec3 last = RESTING_AT.put(id, player.position());
		boolean still = last != null && last.distanceToSqr(player.position()) < 0.01;
		if (!still || player.getHealth() >= player.getMaxHealth() || !Perks.has(player, SurvivalSkill.CAMPFIRE_REST) || !nearLitCampfire(player)) {
			RESTED.remove(id);
			return;
		}
		int rested = RESTED.merge(id, 20, Integer::sum);
		if (rested % SurvivalRules.CAMPFIRE_REST_INTERVAL == 0) {
			player.heal(SurvivalRules.CAMPFIRE_REST_HEAL);
			if (rested == SurvivalRules.CAMPFIRE_REST_INTERVAL) {
				SurvivalSkill.chip(player, SurvivalSkill.CAMPFIRE_REST, "campfire_rest");
			}
		}
	}

	private static boolean nearLitCampfire(ServerPlayer player) {
		BlockPos at = player.blockPosition();
		int r = SurvivalRules.CAMPFIRE_RANGE;
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-r, -1, -r), at.offset(r, 1, r))) {
			BlockState s = player.level().getBlockState(p);
			if (s.is(BlockTags.CAMPFIRES) && s.hasProperty(CampfireBlock.LIT) && s.getValue(CampfireBlock.LIT)) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- Poultice (teaching)

	private static InteractionResult poultice(Player player, InteractionHand hand) {
		ItemStack herbs = player.getItemInHand(hand);
		if (!player.isShiftKeyDown() || !herbs.is(SurvivalSkill.HERBS) || herbs.getCount() < SurvivalRules.POULTICE_HERBS
			|| !(player.getHealth() < player.getMaxHealth() || player.hasEffect(MobEffects.POISON)) || !Perks.has(player, SurvivalSkill.POULTICE)) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		long now = sp.level().getGameTime();
		long ready = POULTICE_READY.getOrDefault(sp.getUUID(), 0L);
		if (now < ready) {
			SurvivalSkill.chipThrottled(sp, SurvivalSkill.POULTICE, 20, "poultice.wait", (ready - now + 19) / 20);
			return InteractionResult.FAIL;
		}
		POULTICE_READY.put(sp.getUUID(), now + SurvivalRules.POULTICE_COOLDOWN);
		if (!sp.isCreative()) {
			herbs.shrink(SurvivalRules.POULTICE_HERBS);
		}
		sp.removeEffect(MobEffects.POISON);
		POULTICE_LEFT.put(sp.getUUID(), SurvivalRules.POULTICE_TICKS);
		sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.8f, 0.8f);
		SurvivalSkill.chip(sp, SurvivalSkill.POULTICE, "poultice");
		return InteractionResult.SUCCESS;
	}

	/** Test hook: ticks of poultice healing still to come. */
	public static int poulticeLeft(ServerPlayer player) {
		return POULTICE_LEFT.getOrDefault(player.getUUID(), 0);
	}

	public static void forget(UUID player) {
		RESTING_AT.remove(player);
		RESTED.remove(player);
		POULTICE_LEFT.remove(player);
		POULTICE_READY.remove(player);
	}

	public static void clear() {
		RESTING_AT.clear();
		RESTED.clear();
		POULTICE_LEFT.clear();
		POULTICE_READY.clear();
	}
}
