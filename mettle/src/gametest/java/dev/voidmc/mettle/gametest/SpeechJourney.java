package dev.voidmc.mettle.gametest;

import com.google.gson.JsonObject;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.character.FeatCounters;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.character.cards.Cards;
import dev.voidmc.mettle.character.skill.SpeechSkill;
import dev.voidmc.mettle.character.skill.speech.SpeechHooks;
import dev.voidmc.mettle.character.skill.speech.SpeechRules;
import dev.voidmc.mettle.client.overlay.MettleActions;
import dev.voidmc.mettle.client.overlay.MettleState;
import dev.voidmc.mettle.compat.HearthLink;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardStore;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.Curves;
import dev.voidmc.mettle.core.DraftState;
import dev.voidmc.mettle.core.EffectsApplier;
import dev.voidmc.mettle.core.MettleAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Speech played for real (docs/BUILD_FEEL.md §14.2), with or without VOID Hearth: a Speech level from real XP and a card
 * kept from its draft through the overlay; a villager trade's XP; Haggler's cut on a dear offer and Glib's gossip
 * (without Hearth); a cure's XP; a piglin barter's XP and Barterer's extra item; Hero's Welcome's three days; Tavern-Keeper's
 * stamp from a furnace; Caravan Master on a path; Ledger-Keeper's weightless coin; and the overlay's Speech block. The
 * character, inventory and the mobs are put back after. Hearth's own reactions are HearthJourney's.
 */
final class SpeechJourney {
	private static final String ID = SpeechSkill.SPEECH.identifier().toString();
	private static final String TAG = "speech_t";
	private static final double EPS = 0.01;

	private SpeechJourney() {
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext world) {
		TestServerContext server = world.getServer();
		List<ItemStack> inventory = on(server, p -> {
			List<ItemStack> copy = new ArrayList<>();
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				copy.add(p.getInventory().getItem(i).copy());
			}
			return copy;
		});
		Set<Long> cardsBefore = on(server, p -> {
			Set<Long> ids = new HashSet<>();
			CardStore.get(p).cards().forEach(c -> ids.add(c.id()));
			return ids;
		});
		Map<Long, CardData.Place> slotted = on(server, p -> {
			Map<Long, CardData.Place> places = new HashMap<>();
			CardStore.get(p).slotted().forEach(card -> places.put(card.id(), card.place()));
			Cards.unslotAll(p);
			return places;
		});
		CharacterState saved = on(server, p -> {
			CharacterState s = CharacterStore.get(p);
			CharacterStore.set(p, s.withPerks(Map.of()));
			EffectsApplier.apply(p);
			return s;
		});
		Map<String, Long> counters = on(server, p -> new HashMap<>(Map.of(SpeechSkill.GOLDEN_MOUTH_COUNTER, FeatCounters.get(p, SpeechSkill.GOLDEN_MOUTH_COUNTER),
			SpeechSkill.MARKET_REGULAR_COUNTER, FeatCounters.get(p, SpeechSkill.MARKET_REGULAR_COUNTER))));
		server.runCommand("effect clear @a");
		try {
			levelAndDraft(context, server);
			villagers(context, server);
			barter(context, server);
			hero(server);
			kitchenRoadAndPurse(context, server);
			overlay(context, server);
		} finally {
			server.runCommand("kill @e[tag=" + TAG + "]");
			check(on(server, p -> {
				for (CardData c : List.copyOf(CardStore.get(p).cards())) {
					if (!cardsBefore.contains(c.id())) {
						Cards.ledger(p.level().getServer()).update(c.id(), x -> x.withPlace(CardData.Place.RELEASED));
					}
				}
				CharacterStore.set(p, saved);
				EffectsApplier.apply(p);
				slotted.forEach((id, place) -> Cards.ledger(p.level().getServer()).update(id, x -> x.withPlace(place)));
				Cards.sync(p);
				counters.forEach((k, v) -> FeatCounters.add(p, k, v - FeatCounters.get(p, k)));
				for (int i = 0; i < inventory.size(); i++) {
					p.getInventory().setItem(i, inventory.get(i));
				}
				p.removeEffect(MobEffects.HERO_OF_THE_VILLAGE);
				return null;
			}));
			context.waitTicks(5);
		}
	}

	// ---------------------------------------------------------------- a level, a draft, a card kept

	private static void levelAndDraft(ClientGameTestContext context, TestServerContext server) {
		check(on(server, p -> {
			Progress.award(p, SpeechSkill.SPEECH, Curves.skillXpFor(2) / multiplier(p) + 0.5);
			DraftState d = CharacterStore.get(p).drafts();
			if (CharacterStore.get(p).level(SpeechSkill.SPEECH) < 2) {
				return "Speech is level " + CharacterStore.get(p).level(SpeechSkill.SPEECH) + " after real XP, expected 2";
			}
			if (d.queued(SpeechSkill.SPEECH) < 1 || d.hand(SpeechSkill.SPEECH).isEmpty()) {
				return "Speech drafts " + d.queued(SpeechSkill.SPEECH) + ", hand " + d.hand(SpeechSkill.SPEECH);
			}
			for (DraftState.Card c : d.hand(SpeechSkill.SPEECH)) {
				if (!c.perk().identifier().getPath().startsWith("speech/") && !c.wildcard()) {
					return "a Speech hand dealt " + c.perk().identifier();
				}
			}
			return null;
		}));
		context.waitTicks(3);
		check(context.computeOnClient(mc -> {
			for (var e : MettleState.build(mc).getAsJsonArray("skills")) {
				JsonObject s = e.getAsJsonObject();
				if (s.get("id").getAsString().equals(ID)) {
					if (!s.get("released").getAsBoolean() || !"#E8C46A".equalsIgnoreCase(s.get("colour").getAsString())) {
						return "Speech in the overlay: " + s.get("released") + " " + s.get("colour");
					}
					return s.has("hand") && s.getAsJsonArray("hand").size() > 0 ? null : "the overlay shows Speech without its hand: " + s;
				}
			}
			return "Speech is not in the overlay's skills";
		}));
		ResourceKey<Perk> kept = on(server, p -> CharacterStore.get(p).drafts().hand(SpeechSkill.SPEECH).getFirst().perk());
		int queued = on(server, p -> CharacterStore.get(p).drafts().queued(SpeechSkill.SPEECH));
		context.runOnClient(mc -> MettleActions.pickCard(ID, 0));
		context.waitTicks(5);
		check(on(server, p -> {
			if (CardStore.get(p).cards().stream().noneMatch(c -> c.perk().equals(kept))) {
				return "keeping " + kept.identifier() + " from the Speech draft made no card";
			}
			return CharacterStore.get(p).drafts().queued(SpeechSkill.SPEECH) == queued - 1 ? null : "the kept draft was not spent";
		}));
		context.takeScreenshot("mettle_speech_draft");
		// The kept card goes out of the deck again: the numbers below are the perks' alone.
		check(on(server, p -> {
			Cards.unslotAll(p);
			return null;
		}));
	}

	// ---------------------------------------------------------------- villagers: a trade, a price, gossip, a cure

	private static void villagers(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("execute as @p at @s run summon villager ~3 ~ ~ {Tags:[\"" + TAG + "\",\"" + TAG + "_v\"],NoAI:1b,PersistenceRequired:1b}");
		server.runCommand("execute as @p at @s run summon zombie_villager ~-3 ~ ~ {Tags:[\"" + TAG + "\",\"" + TAG + "_z\"],NoAI:1b,PersistenceRequired:1b}");
		context.waitTicks(3);
		check(on(server, p -> {
			Villager v = entity(p, Villager.class, "v");
			// A trade of 5 emeralds for bread: 3 + 5 XP (the first of its kind, untapered).
			MerchantOffer offer = new MerchantOffer(new ItemCost(Items.EMERALD, 5), new ItemStack(Items.BREAD), 12, 1, 0.05f);
			MerchantOffers offers = new MerchantOffers();
			offers.add(offer);
			v.setOffers(offers);
			long regular = FeatCounters.get(p, SpeechSkill.MARKET_REGULAR_COUNTER);
			double before = xp(p);
			v.setTradingPlayer(p);
			v.notifyTrade(offer);
			v.setTradingPlayer(null);
			double gained = xp(p) - before;
			if (Math.abs(gained - SpeechRules.villagerTradeXp(5) * multiplier(p)) > EPS) {
				return "a 5-emerald villager trade gave " + gained + " Speech XP, expected " + SpeechRules.villagerTradeXp(5) + " × multipliers";
			}
			long tally = FeatCounters.get(p, SpeechSkill.MARKET_REGULAR_COUNTER) - regular;
			if (tally != (HearthLink.hearth() ? 0 : SpeechRules.MARKET_REGULAR_VILLAGER)) {
				return "a villager trade counted " + tally + " toward Market Regular (Hearth " + HearthLink.hearth() + ")";
			}

			// Haggler III on a 10-emerald offer: 3 off.
			grant(p, SpeechSkill.HAGGLER, 3);
			MerchantOffer dear = new MerchantOffer(new ItemCost(Items.EMERALD, 10), new ItemStack(Items.DIAMOND), 12, 1, 0.05f);
			MerchantOffer cheap = new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.APPLE), 12, 1, 0.05f);
			MerchantOffers two = new MerchantOffers();
			two.add(dear);
			two.add(cheap);
			v.setOffers(two);
			try {
				var m = Villager.class.getDeclaredMethod("updateSpecialPrices", net.minecraft.world.entity.player.Player.class);
				m.setAccessible(true);
				m.invoke(v, p);
			} catch (ReflectiveOperationException e) {
				return "could not price the villager's offers: " + e;
			}
			if (dear.getCostA().getCount() != 7 || cheap.getCostA().getCount() != 3) {
				return "Haggler III: a 10-emerald offer costs " + dear.getCostA().getCount() + " (expected 7), a 3-emerald one " + cheap.getCostA().getCount();
			}

			// Glib III without Hearth: a trade's gossip grows 75 % faster (2 → 3 or 4).
			grant(p, SpeechSkill.GLIB, 3);
			int g0 = v.getGossips().getReputation(p.getUUID(), t -> t == GossipType.TRADING);
			v.onReputationEventFrom(ReputationEventType.TRADE, p);
			int grown = v.getGossips().getReputation(p.getUUID(), t -> t == GossipType.TRADING) - g0;
			int want = HearthLink.hearth() ? 2 : 3;
			if (grown < want || grown > (HearthLink.hearth() ? 2 : 4)) {
				return "Glib III: a trade's gossip grew by " + grown + " (Hearth " + HearthLink.hearth() + ")";
			}

			// A cure: 60 XP, and without Hearth 10 toward Golden Mouth (five cures).
			ZombieVillager z = entity(p, ZombieVillager.class, "z");
			long golden = FeatCounters.get(p, SpeechSkill.GOLDEN_MOUTH_COUNTER);
			before = xp(p);
			CriteriaTriggers.CURED_ZOMBIE_VILLAGER.trigger(p, z, v);
			gained = xp(p) - before;
			if (Math.abs(gained - SpeechRules.CURE_XP * multiplier(p)) > EPS) {
				return "a cure gave " + gained + " Speech XP, expected 60 × multipliers";
			}
			long counted = FeatCounters.get(p, SpeechSkill.GOLDEN_MOUTH_COUNTER) - golden;
			return counted == (HearthLink.hearth() ? 0 : SpeechRules.GOLDEN_MOUTH_CURE) ? null : "a cure counted " + counted + " toward Golden Mouth";
		}));
		context.waitTicks(2);
	}

	// ---------------------------------------------------------------- a piglin barter

	private static void barter(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("execute as @p at @s run summon piglin ~ ~ ~4 {Tags:[\"" + TAG + "\",\"" + TAG + "_p\"],NoAI:1b,PersistenceRequired:1b,IsImmuneToZombification:1b}");
		context.waitTicks(3);
		check(on(server, p -> {
			Piglin piglin = entity(p, Piglin.class, "p");
			piglin.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER, p);
			java.lang.reflect.Method barter;
			try {
				barter = net.minecraft.world.entity.monster.piglin.PiglinAi.class.getDeclaredMethod("getBarterResponseItems", Piglin.class);
				barter.setAccessible(true);
			} catch (ReflectiveOperationException e) {
				return "no PiglinAi.getBarterResponseItems: " + e;
			}
			double before = xp(p);
			int plain = items(barter, piglin);
			double gained = xp(p) - before;
			if (Math.abs(gained - SpeechRules.BARTER_XP * multiplier(p)) > EPS || plain < 1) {
				return "a barter gave " + gained + " Speech XP and " + plain + " stacks";
			}
			// Barterer: one more item a quarter of the time (60 barters: at least one).
			grant(p, SpeechSkill.BARTERER, 1);
			int more = 0;
			for (int i = 0; i < 60; i++) {
				List<ItemStack> got = list(barter, piglin);
				ItemStack last = got.getLast();
				if (got.size() >= 2 && last.getCount() == 1 && last.getComponentsPatch().isEmpty() && last.is(got.getFirst().getItem())) {
					more++;
				}
			}
			return more >= 3 && more <= 35 ? null : "Barterer added an item to " + more + " barters of 60, expected about 15";
		}));
	}

	private static int items(java.lang.reflect.Method barter, Piglin piglin) {
		return list(barter, piglin).size();
	}

	@SuppressWarnings("unchecked")
	private static List<ItemStack> list(java.lang.reflect.Method barter, Piglin piglin) {
		try {
			return (List<ItemStack>) barter.invoke(null, piglin);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	// ---------------------------------------------------------------- Hero of the Village

	private static void hero(TestServerContext server) {
		check(on(server, p -> {
			double before = xp(p);
			MobEffectInstance plain = SpeechHooks.hero(p, new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 48_000, 0));
			if (plain.getDuration() != 48_000 || Math.abs(xp(p) - before - SpeechRules.HERO_XP * multiplier(p)) > EPS) {
				return "Hero of the Village without Hero's Welcome: " + plain.getDuration() + " ticks, " + (xp(p) - before) + " XP";
			}
			grant(p, SpeechSkill.HEROS_WELCOME, 1);
			MobEffectInstance welcome = SpeechHooks.hero(p, new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 48_000, 0));
			return welcome.getDuration() == SpeechRules.HEROS_WELCOME_TICKS ? null : "Hero's Welcome made it " + welcome.getDuration() + " ticks";
		}));
	}

	// ---------------------------------------------------------------- the kitchen, the road, the purse

	private static void kitchenRoadAndPurse(ClientGameTestContext context, TestServerContext server) {
		check(on(server, p -> {
			// Tavern-Keeper: food taken from a furnace carries the cook's name; other things and others' food don't.
			ItemStack bread = new ItemStack(Items.COOKED_BEEF, 4);
			new FurnaceResultSlot(p, new SimpleContainer(3), 2, 0, 0).onTake(p, bread);
			if (bread.has(SpeechHooks.COOKED_BY)) {
				return "food was stamped without Tavern-Keeper";
			}
			grant(p, SpeechSkill.TAVERN_KEEPER, 1);
			new FurnaceResultSlot(p, new SimpleContainer(3), 2, 0, 0).onTake(p, bread);
			ItemStack ingot = new ItemStack(Items.IRON_INGOT);
			new FurnaceResultSlot(p, new SimpleContainer(3), 2, 0, 0).onTake(p, ingot);
			if (!p.getGameProfile().name().equals(bread.get(SpeechHooks.COOKED_BY)) || ingot.has(SpeechHooks.COOKED_BY)) {
				return "Tavern-Keeper's stamp: " + bread.get(SpeechHooks.COOKED_BY) + " on the beef, " + ingot.get(SpeechHooks.COOKED_BY) + " on the ingot";
			}
			if (!SpeechHooks.cookedBy(bread, p)) {
				return "cooked-by-you not read back";
			}
			// Ledger-Keeper: coins weigh nothing.
			if (SpeechHooks.coinWeight(p) != 1f) {
				return "coins weigh " + SpeechHooks.coinWeight(p) + " before Ledger-Keeper";
			}
			grant(p, SpeechSkill.LEDGER_KEEPER, 1);
			if (SpeechHooks.coinWeight(p) != 0f) {
				return "Ledger-Keeper: coins weigh " + SpeechHooks.coinWeight(p);
			}
			// Caravan Master on a path: 10 % more carry.
			grant(p, SpeechSkill.CARAVAN_MASTER, 1);
			return null;
		}));
		double base = on(server, p -> p.getAttributeValue(MettleAttributes.CARRY_CAPACITY));
		// The block under the player: a dirt path, then what it was (a path is lower, so the place is kept, not re-read).
		Object[] under = on(server, p -> {
			net.minecraft.core.BlockPos at = p.getOnPos();
			Object[] o = {at, p.level().getBlockState(at)};
			p.level().setBlockAndUpdate(at, net.minecraft.world.level.block.Blocks.DIRT_PATH.defaultBlockState());
			return o;
		});
		context.waitTicks(30);
		check(on(server, p -> {
			double now = p.getAttributeValue(MettleAttributes.CARRY_CAPACITY);
			boolean on = p.getAttribute(MettleAttributes.CARRY_CAPACITY).hasModifier(dev.voidmc.mettle.Mettle.id("speech/caravan_master"));
			return on && now > base + 1e-6 ? null : "Caravan Master on a path: carry " + base + " → " + now + " (modifier " + on + ")";
		}));
		check(on(server, p -> {
			p.level().setBlockAndUpdate((net.minecraft.core.BlockPos) under[0], (net.minecraft.world.level.block.state.BlockState) under[1]);
			return null;
		}));
		context.waitTicks(30);
		check(on(server, p -> Math.abs(p.getAttributeValue(MettleAttributes.CARRY_CAPACITY) - base) < 1e-6 ? null
			: "Caravan Master's carry stayed off the path: " + p.getAttributeValue(MettleAttributes.CARRY_CAPACITY)));
	}

	// ---------------------------------------------------------------- the overlay

	private static void overlay(ClientGameTestContext context, TestServerContext server) {
		int[] want = on(server, p -> {
			grant(p, SpeechSkill.COMMANDING_VOICE, 1);
			return new int[] {SpeechSkill.effectiveLevel(p), SpeechSkill.effectivePresence(p), CharacterStore.get(p).level(SpeechSkill.SPEECH)};
		});
		context.waitTicks(5);
		check(context.computeOnClient(mc -> {
			JsonObject s = MettleState.build(mc).getAsJsonObject("speech");
			if (s == null) {
				return "the overlay has no speech block";
			}
			if (s.get("effectiveLevel").getAsInt() != want[0] || s.get("effectivePresence").getAsInt() != want[1] || s.get("level").getAsInt() != want[2]) {
				return "the overlay's Speech: " + s + ", expected effective Speech " + want[0] + ", Presence " + want[1];
			}
			if (want[0] != want[2] + 3) {
				return "Glib III should make Speech count 3 higher: " + want[0] + " vs " + want[2];
			}
			return s.get("coinWeight").getAsInt() == 0 && s.get("hearth").getAsBoolean() == HearthLink.hearth() ? null : "the overlay's Speech block: " + s;
		}));
	}

	// ---------------------------------------------------------------- helpers

	private static void grant(ServerPlayer p, Identifier perk, int rank) {
		CharacterState state = CharacterStore.get(p);
		Map<ResourceKey<Perk>, Integer> perks = new HashMap<>(state.perks());
		perks.put(Perks.key(perk), rank);
		CharacterStore.set(p, state.withPerks(perks));
		EffectsApplier.apply(p);
		if (Perks.rank(p, perk) != rank) {
			throw new AssertionError("Speech journey: " + perk + " was not granted at rank " + rank);
		}
	}

	private static <E extends Entity> E entity(ServerPlayer p, Class<E> type, String tag) {
		for (Entity e : ((ServerLevel) p.level()).getAllEntities()) {
			if (type.isInstance(e) && e.entityTags().contains(TAG + "_" + tag)) {
				return type.cast(e);
			}
		}
		throw new AssertionError("Speech journey: no " + tag + " entity");
	}

	/** What one base XP of Speech becomes: favoured and Wits, cursed cards, then every MODIFY_XP listener (all linear). */
	private static double multiplier(ServerPlayer p) {
		CharacterState state = CharacterStore.get(p);
		double m = Progress.multiplier(state, CharacterStore.background(p.level().registryAccess(), state), SpeechSkill.SPEECH)
			* dev.voidmc.mettle.character.draft.Cursed.cardXp(p, state, SpeechSkill.SPEECH);
		return dev.voidmc.mettle.api.MettleEvents.MODIFY_XP.invoker().modify(p, SpeechSkill.SPEECH, m);
	}

	private static double xp(ServerPlayer p) {
		return CharacterStore.get(p).xp(SpeechSkill.SPEECH);
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static <T> T on(TestServerContext server, Function<ServerPlayer, T> f) {
		return server.computeOnServer(s -> f.apply(player(s)));
	}

	private static void check(String problem) {
		if (problem != null) {
			throw new AssertionError("Speech journey: " + problem);
		}
	}
}
