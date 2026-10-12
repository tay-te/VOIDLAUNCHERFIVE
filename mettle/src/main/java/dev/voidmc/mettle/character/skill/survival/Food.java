package dev.voidmc.mettle.character.skill.survival;

import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.SurvivalSkill;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Eating and cooking: Field Cook and Living off the Land make food fill more, Iron Stomach takes the sting out of raw
 * meat, and food taken from a furnace or smoker, or popped off a campfire you loaded, teaches Survival.
 */
public final class Food {
	private Food() {
	}

	public static void init() {
	}

	/** FoodProperties#onConsume: what one bite of {@code stack} gives this player. */
	public static FoodProperties fill(Player player, ItemStack stack, FoodProperties food) {
		boolean cooked = stack.is(SurvivalSkill.COOKED_FOOD);
		boolean wild = stack.is(SurvivalSkill.WILD_FOOD);
		// Hearty (a card trait): any food fills more.
		double hearty = dev.voidmc.mettle.character.cards.CardHooks.pct(player, "survival/hearty");
		if (hearty > 0) {
			food = new FoodProperties(SurvivalRules.nutrition(food.nutrition(), 1 + hearty), (float) (food.saturation() * (1 + hearty)), food.canAlwaysEat());
		}
		if (cooked && player instanceof ServerPlayer sp) {
			dev.voidmc.mettle.character.cards.CardHooks.ateCooked(sp);
		}
		if (!cooked && !wild) {
			return food;
		}
		int fieldCook = cooked ? Perks.rank(player, SurvivalSkill.FIELD_COOK) : 0;
		boolean living = wild && Perks.has(player, SurvivalSkill.LIVING_OFF_THE_LAND);
		double m = SurvivalRules.fillMultiplier(cooked, fieldCook, wild, living);
		if (m == 1.0) {
			return food;
		}
		if (player instanceof ServerPlayer sp) {
			if (living) {
				SurvivalSkill.chipThrottled(sp, SurvivalSkill.LIVING_OFF_THE_LAND, 200, "wild_food");
			} else {
				SurvivalSkill.chipThrottled(sp, SurvivalSkill.FIELD_COOK, 200, "field_cook", Math.round((m - 1) * 100));
			}
		}
		return new FoodProperties(SurvivalRules.nutrition(food.nutrition(), m), (float) (food.saturation() * m), food.canAlwaysEat());
	}

	/** Cook's (a card trait): food you cook comes out one extra some of the time. */
	private static void extraCooked(ServerPlayer player, ItemStack stack, int count) {
		double chance = dev.voidmc.mettle.character.cards.CardHooks.pct(player, "survival/cooks");
		// Only plain food is copied (nothing with data of its own).
		if (!dev.voidmc.mettle.character.cards.CardHooks.plain(stack)) {
			return;
		}
		int extra = 0;
		for (int i = 0; i < count && chance > 0; i++) {
			if (player.getRandom().nextDouble() < chance) {
				extra++;
			}
		}
		if (extra > 0) {
			ItemStack more = stack.copyWithCount(extra);
			if (!player.getInventory().add(more)) {
				player.drop(more, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
		}
	}

	/** ApplyStatusEffectsConsumeEffect#apply: Iron Stomach shrugs off what raw meat, rotten flesh and spider eyes do. */
	public static boolean ironStomach(LivingEntity user, ItemStack stack) {
		if (user instanceof Player player && stack.is(SurvivalSkill.RAW_FOOD) && Perks.has(player, SurvivalSkill.IRON_STOMACH)) {
			if (player instanceof ServerPlayer sp) {
				SurvivalSkill.chipThrottled(sp, SurvivalSkill.IRON_STOMACH, 200, "iron_stomach");
			}
			return true;
		}
		return false;
	}

	/** FurnaceResultSlot: a player took {@code count} of {@code stack} out of a furnace, smoker or blast furnace. */
	public static void taken(Player player, ItemStack stack, int count) {
		if (player instanceof ServerPlayer sp && count > 0 && stack.has(DataComponents.FOOD)) {
			SurvivalSkill.award(sp, "cook", SurvivalRules.XP_COOKED * count);
			extraCooked(sp, stack, count);
		}
	}

	/** CampfireBlockEntity#cookTick: food a player put on the fire came off cooked. */
	public static void campfireCooked(MinecraftServer server, @Nullable UUID cook, ItemStack result) {
		if (cook == null || !result.has(DataComponents.FOOD)) {
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(cook);
		if (player != null) {
			SurvivalSkill.award(player, "cook", SurvivalRules.XP_COOKED * result.getCount());
			extraCooked(player, result, result.getCount());
		}
	}
}
