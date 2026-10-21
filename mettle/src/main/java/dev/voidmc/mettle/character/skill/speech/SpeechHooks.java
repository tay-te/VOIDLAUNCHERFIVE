package dev.voidmc.mettle.character.skill.speech;

import com.mojang.serialization.Codec;
import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.core.MettleAttributes;
import dev.voidmc.mettle.api.MettleEvents;
import dev.voidmc.mettle.api.MettleServices;
import dev.voidmc.mettle.burden.WeightPerks;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.cards.CardHooks;
import dev.voidmc.mettle.character.draft.Cursed;
import dev.voidmc.mettle.character.skill.SpeechSkill;
import dev.voidmc.mettle.character.skill.survival.Camp;
import dev.voidmc.mettle.compat.HearthLink;
import dev.voidmc.mettle.core.net.MettlePayloads;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * Speech's Mettle-side behaviours (docs/BUILD_FEEL.md §14.2), reached from {@code mixin.speech} and Fabric events:
 * Speech XP without Hearth (villager trades, cures, Hero of the Village, piglin barters), Haggler's and Haggling's villager
 * prices, Glib's gossip, Barterer and Bartering, Hero's Welcome and Heroic, the XP perks (Gregarious, Demagogue's price,
 * allies' Rallying and Commanding Voice), coin weight (Ledger-Keeper, Coinwise), Caravan Master on the road, Tavern-Keeper's
 * stamp and Appraiser's prices. Nothing here reaches Hearth (rule a: that is {@code compat.hearth}'s).
 */
public final class SpeechHooks {
	/** "Cooked by" (Tavern-Keeper): the cook's name on food taken from a furnace or smoker. */
	public static final DataComponentType<String> COOKED_BY = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Mettle.id("cooked_by"),
		DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());

	private static final Identifier CARAVAN = Mettle.id("speech/caravan_master");
	private static final String RESTOCKED = "mettle_barterer_restocked";

	/**
	 * Appraiser at a Hearth market: what this settlement pays and asks for a stack (set by {@code compat.hearth} when
	 * Hearth's economy is there); empty away from a market.
	 */
	public static volatile BiFunction<ServerPlayer, ItemStack, Optional<Component>> marketAppraiser = (p, s) -> Optional.empty();

	private SpeechHooks() {
	}

	public static void init() {
		MettleEvents.MODIFY_XP.register((player, skill, xp) -> xp * xpFactor(player, skill.equals(SpeechSkill.SPEECH)));
		MettleServices.addWeightModifier((player, stack) -> WeightPerks.coin(stack) ? coinWeight(player) : 1f);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0) {
				for (ServerPlayer p : server.getPlayerList().getPlayers()) {
					caravan(p);
				}
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(MettlePayloads.Appraise.TYPE, (payload, context) -> appraise(context.player(), payload.stack()));
	}

	public static void clear() {
	}

	public static void forget(UUID player) {
	}

	// ---------------------------------------------------------------- XP

	/** Gregarious on Speech XP, Demagogue's price on everything else, allies' Rallying and Commanding Voice on all of it. */
	public static double xpFactor(ServerPlayer player, boolean speech) {
		double allies = 0;
		for (ServerPlayer other : player.level().players()) {
			if (other != player && other.distanceToSqr(player) <= SpeechRules.ALLY_RANGE * SpeechRules.ALLY_RANGE && CardHooks.helps(other, player)) {
				allies += CardHooks.pct(other, "speech/rallying");
				allies += Perks.has(other, SpeechSkill.COMMANDING_VOICE) ? SpeechRules.COMMANDING_VOICE_ALLY_XP : 0;
			}
		}
		double demagogue = Perks.has(player, SpeechSkill.DEMAGOGUE) ? Cursed.down(SpeechRules.DEMAGOGUE_XP, Cursed.hex(player)) : 1;
		return SpeechRules.xp(speech, CardHooks.pct(player, "speech/gregarious"), demagogue, allies);
	}

	/** AbstractVillager#notifyTrade: Speech XP for a trade, Market Regular's tally (without Hearth), Barterer's restock. */
	public static void traded(AbstractVillager trader, MerchantOffer offer) {
		if (!(trader.getTradingPlayer() instanceof ServerPlayer player)) {
			return;
		}
		int emeralds = emeralds(offer.getCostA()) + emeralds(offer.getCostB()) + emeralds(offer.getResult());
		SpeechSkill.award(player, "villager_trade", SpeechRules.villagerTradeXp(emeralds));
		if (!HearthLink.hearth()) {
			SpeechSkill.tally(player, SpeechSkill.MARKET_REGULAR_COUNTER, SpeechRules.MARKET_REGULAR_VILLAGER);
		}
		if (trader instanceof WanderingTrader && offer.isOutOfStock() && Perks.has(player, SpeechSkill.BARTERER) && !trader.entityTags().contains(RESTOCKED)) {
			trader.addTag(RESTOCKED);
			trader.getOffers().forEach(MerchantOffer::resetUses);
			SpeechSkill.chip(player, SpeechSkill.BARTERER, "restock");
		}
	}

	private static int emeralds(ItemStack stack) {
		return stack.is(Items.EMERALD) ? stack.getCount() : stack.is(Items.EMERALD_BLOCK) ? stack.getCount() * 9 : 0;
	}

	/** Villager#onReputationEventFrom, a trade: Glib grows the trading gossip faster (without Hearth). */
	public static void gossip(Villager villager, Entity source) {
		if (HearthLink.hearth() || !(source instanceof ServerPlayer player)) {
			return;
		}
		double more = SpeechRules.gossip(Perks.rank(player, SpeechSkill.GLIB)) * 2;
		int whole = (int) Math.floor(more);
		if (player.getRandom().nextDouble() < more - whole) {
			whole++;
		}
		if (whole > 0) {
			villager.getGossips().add(player.getUUID(), GossipType.TRADING, whole);
		}
	}

	/**
	 * Villager#updateSpecialPrices: what an offer of {@code cost} emeralds comes down by for this player: Haggler, and
	 * Haggling (a trait) without Hearth (with Hearth it is market edge, the price channel).
	 */
	public static int villagerCut(Player player, int cost) {
		int cut = SpeechRules.villagerCut(Perks.rank(player, SpeechSkill.HAGGLER), cost);
		if (!HearthLink.hearth()) {
			cut += (int) Math.round(cost * CardHooks.pct(player, "speech/haggling"));
		}
		return cut;
	}

	/** CuredZombieVillagerTrigger#trigger: a cure teaches Speech; without Hearth it counts toward Golden Mouth. */
	public static void cured(ServerPlayer player) {
		SpeechSkill.award(player, "cure", SpeechRules.CURE_XP);
		if (!HearthLink.hearth()) {
			SpeechSkill.tally(player, SpeechSkill.GOLDEN_MOUTH_COUNTER, SpeechRules.GOLDEN_MOUTH_CURE);
		}
	}

	/** PiglinAi#getBarterResponseItems: a barter teaches the nearest player Speech; Barterer and Bartering add one more item. */
	public static List<ItemStack> bartered(Piglin piglin, List<ItemStack> items) {
		Optional<Player> near = piglin.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER);
		if (near.isEmpty() || !(near.get() instanceof ServerPlayer player) || items.isEmpty()) {
			return items;
		}
		SpeechSkill.award(player, "barter", SpeechRules.BARTER_XP);
		double chance = (Perks.has(player, SpeechSkill.BARTERER) ? SpeechRules.BARTERER_CHANCE : 0) + CardHooks.pct(player, "speech/bartering");
		if (chance > 0 && player.getRandom().nextDouble() < chance) {
			// One more of the first thing given, as a plain stack (never contents or enchantments).
			ItemStack extra = new ItemStack(items.getFirst().getItem(), 1);
			if (!extra.isEmpty()) {
				List<ItemStack> out = new ArrayList<>(items);
				out.add(extra);
				SpeechSkill.chip(player, SpeechSkill.BARTERER, "barterer");
				return out;
			}
		}
		return items;
	}

	/** LivingEntity#addEffect (from CardHooks.effect): Hero of the Village teaches Speech; Hero's Welcome and Heroic lengthen it. */
	public static MobEffectInstance hero(ServerPlayer player, MobEffectInstance effect) {
		if (!effect.getEffect().equals(MobEffects.HERO_OF_THE_VILLAGE) || effect.isInfiniteDuration()) {
			return effect;
		}
		SpeechSkill.award(player, "hero", SpeechRules.HERO_XP);
		boolean welcome = Perks.has(player, SpeechSkill.HEROS_WELCOME);
		double heroic = CardHooks.pct(player, "speech/heroic");
		int ticks = SpeechRules.heroTicks(effect.getDuration(), welcome, heroic);
		if (ticks == effect.getDuration()) {
			return effect;
		}
		if (welcome) {
			SpeechSkill.chip(player, SpeechSkill.HEROS_WELCOME, "heros_welcome");
		}
		return new MobEffectInstance(effect.getEffect(), ticks, effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon());
	}

	// ---------------------------------------------------------------- weight, the road, the kitchen

	/** Coins' weight for this player (both sides). */
	public static float coinWeight(Player player) {
		return SpeechRules.coinWeight(Perks.has(player, SpeechSkill.LEDGER_KEEPER), CardHooks.pct(player, "speech/coinwise"));
	}

	/** Caravan Master: on a path or road, you and allies within 8 blocks of a holder carry 10 % more. Once a second. */
	private static void caravan(ServerPlayer player) {
		AttributeInstance carry = player.getAttribute(MettleAttributes.CARRY_CAPACITY);
		if (carry == null) {
			return;
		}
		boolean want = false;
		if (Camp.onPath(player)) {
			for (ServerPlayer holder : player.level().players()) {
				if ((holder == player || holder.distanceToSqr(player) <= SpeechRules.ALLY_RANGE * SpeechRules.ALLY_RANGE && CardHooks.helps(holder, player))
					&& Perks.has(holder, SpeechSkill.CARAVAN_MASTER)) {
					want = true;
					break;
				}
			}
		}
		if (want != carry.hasModifier(CARAVAN)) {
			if (want) {
				carry.addTransientModifier(new AttributeModifier(CARAVAN, SpeechRules.CARAVAN_CARRY, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			} else {
				carry.removeModifier(CARAVAN);
			}
		}
	}

	/** FurnaceResultSlot#checkTakeAchievements: Tavern-Keeper puts the cook's name on food taken from a furnace or smoker. */
	public static void cooked(ServerPlayer player, ItemStack stack) {
		if (!stack.isEmpty() && food(stack) && !stack.has(COOKED_BY) && Perks.has(player, SpeechSkill.TAVERN_KEEPER)) {
			stack.set(COOKED_BY, player.getGameProfile().name());
		}
	}

	public static boolean cookedBy(ItemStack stack, Player player) {
		String cook = stack.get(COOKED_BY);
		return cook != null && cook.equals(player.getGameProfile().name());
	}

	// ---------------------------------------------------------------- kinds of goods (Provisioner, Appraising)

	public static boolean food(ItemStack stack) {
		return stack.has(DataComponents.FOOD);
	}

	public static boolean drinkOrPotion(ItemStack stack) {
		return stack.has(DataComponents.POTION_CONTENTS) || stack.is(Items.MILK_BUCKET) || stack.is(Items.HONEY_BOTTLE);
	}

	public static boolean gear(ItemStack stack) {
		return stack.has(DataComponents.TOOL) || stack.has(DataComponents.WEAPON) || stack.has(DataComponents.MAX_DAMAGE)
			|| stack.get(DataComponents.EQUIPPABLE) != null && stack.get(DataComponents.EQUIPPABLE).slot().isArmor();
	}

	// ---------------------------------------------------------------- Appraiser

	/** Appraiser's tooltip asked: the market here (Hearth), else the nearest villager who buys it. */
	public static void appraise(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty() || !Perks.has(player, SpeechSkill.APPRAISER)) {
			return;
		}
		Component line = line(player, stack);
		ServerPlayNetworking.send(player, new MettlePayloads.Appraised(stack.copy(), line));
	}

	public static Component line(ServerPlayer player, ItemStack stack) {
		Optional<Component> market = marketAppraiser.apply(player, stack);
		if (market.isPresent()) {
			return market.get();
		}
		Villager nearest = null;
		double best = 32 * 32;
		for (Villager v : player.level().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(32))) {
			double d = v.distanceToSqr(player);
			if (d < best) {
				best = d;
				nearest = v;
			}
		}
		if (nearest != null) {
			for (MerchantOffer offer : nearest.getOffers()) {
				if (offer.getCostA().is(stack.getItem()) && offer.getResult().is(Items.EMERALD) && offer.getCostA().getCount() > 0) {
					int pays = stack.getCount() / offer.getCostA().getCount() * offer.getResult().getCount();
					return Component.translatable("mettle.speech.appraise.villager", pays);
				}
			}
		}
		return Component.translatable("mettle.speech.appraise.none");
	}
}
