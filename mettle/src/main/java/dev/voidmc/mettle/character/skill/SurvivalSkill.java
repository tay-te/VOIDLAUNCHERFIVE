package dev.voidmc.mettle.character.skill;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.character.skill.survival.Camp;
import dev.voidmc.mettle.character.skill.survival.Discovery;
import dev.voidmc.mettle.character.skill.survival.Food;
import dev.voidmc.mettle.character.skill.survival.Foraging;
import dev.voidmc.mettle.character.skill.survival.GeneralFeats;
import dev.voidmc.mettle.character.skill.survival.Hunting;
import dev.voidmc.mettle.character.skill.survival.Repetition;
import dev.voidmc.mettle.character.skill.survival.Tracker;
import dev.voidmc.mettle.character.skill.survival.Weatherwise;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * The Survival tree (TREES.md §9) and the general feats (§12), which live here because Survival is their home skill.
 * XP comes from hunting, foraging, cooking and discovery (BALANCE.md §3); the perk definitions are data in
 * {@code data/mettle/mettle/perk/survival/} and {@code perk/general/}; the behaviours live in {@code skill.survival},
 * reached from the {@code mixin.survival} hooks through the entry points in those classes.
 *
 * <p>What counts as a wild plant, cooked or wild food, game meat, a hide, big game, a road or a herb is data (tags under
 * {@code mettle:survival/…}), so packs and the other VOID mods can widen it.
 */
public final class SurvivalSkill {
	public static final ResourceKey<Skill> SURVIVAL = Progress.skill("survival");

	public static final Identifier GATHERER = Mettle.id("survival/gatherer");
	public static final Identifier FIELD_COOK = Mettle.id("survival/field_cook");
	public static final Identifier QUIET_APPROACH = Mettle.id("survival/quiet_approach");
	public static final Identifier SKINNER = Mettle.id("survival/skinner");
	public static final Identifier STALKER = Mettle.id("survival/stalker");
	public static final Identifier SIXTH_SENSE = Mettle.id("survival/sixth_sense");
	public static final Identifier IRON_STOMACH = Mettle.id("survival/iron_stomach");
	public static final Identifier CAMPFIRE_REST = Mettle.id("survival/campfire_rest");
	public static final Identifier PATHFINDER = Mettle.id("survival/pathfinder");
	public static final Identifier SELF_SUFFICIENT = Mettle.id("survival/self_sufficient");
	public static final Identifier TRACKER = Mettle.id("survival/tracker");
	public static final Identifier LAY_OF_THE_LAND = Mettle.id("survival/lay_of_the_land");
	public static final Identifier LIVING_OFF_THE_LAND = Mettle.id("survival/living_off_the_land");
	public static final Identifier WEATHERWISE = Mettle.id("survival/weatherwise");
	public static final Identifier BIG_GAME = Mettle.id("survival/big_game");
	public static final Identifier TRAILBLAZER = Mettle.id("survival/trailblazer");
	public static final Identifier POULTICE = Mettle.id("survival/poultice");

	// General feats (TREES.md §12), filed under Survival.
	public static final Identifier NINE_LIVES = Mettle.id("general/nine_lives");
	public static final Identifier SEEN_IT_ALL = Mettle.id("general/seen_it_all");
	public static final Identifier PACK_HORSE = Mettle.id("general/pack_horse");
	public static final Identifier SEASONED = Mettle.id("general/seasoned");
	public static final Identifier JACK_OF_ALL_TRADES = Mettle.id("general/jack_of_all_trades");

	// Feat counters
	public static final String BIOMES_VISITED = "biomes_visited";
	public static final String BIG_GAME_KILLS = "big_game_kills";
	public static final String NEAR_DEATHS = "near_deaths";
	public static final String LANDMARK_KINDS = "landmark_kinds";
	public static final String OVERBURDENED_METRES = "overburdened_metres";
	public static final String LIFE_TICKS = "life_ticks";
	public static final String SKILLS_AT_FIVE = "skills_at_five";

	// Tags (data/mettle/tags/…/survival/)
	public static final TagKey<Block> WILD_PLANTS = TagKey.create(Registries.BLOCK, Mettle.id("survival/wild_plants"));
	public static final TagKey<Block> FORAGE_COVER = TagKey.create(Registries.BLOCK, Mettle.id("survival/forage_cover"));
	public static final TagKey<Block> PATHS = TagKey.create(Registries.BLOCK, Mettle.id("survival/paths"));
	public static final TagKey<Item> HERBS = TagKey.create(Registries.ITEM, Mettle.id("survival/herbs"));
	public static final TagKey<Item> COOKED_FOOD = TagKey.create(Registries.ITEM, Mettle.id("survival/cooked_food"));
	public static final TagKey<Item> WILD_FOOD = TagKey.create(Registries.ITEM, Mettle.id("survival/wild_food"));
	public static final TagKey<Item> RAW_FOOD = TagKey.create(Registries.ITEM, Mettle.id("survival/raw_food"));
	public static final TagKey<Item> GAME_MEAT = TagKey.create(Registries.ITEM, Mettle.id("survival/game_meat"));
	public static final TagKey<Item> HIDES = TagKey.create(Registries.ITEM, Mettle.id("survival/hides"));
	public static final TagKey<EntityType<?>> BIG_GAME_ANIMALS = TagKey.create(Registries.ENTITY_TYPE, Mettle.id("survival/big_game"));
	public static final TagKey<Structure> NOTABLE = TagKey.create(Registries.STRUCTURE, Mettle.id("survival/notable"));
	/** Expanse's eight landmarks (requested from Expanse; absent until it ships the tag). */
	public static final TagKey<Structure> EXPANSE_LANDMARKS = TagKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath("expanse", "landmarks"));

	private static final Map<UUID, Map<String, Repetition>> REPETITION = new HashMap<>();
	private static final Map<UUID, Map<Identifier, Long>> LAST_CHIP = new HashMap<>();

	private SurvivalSkill() {
	}

	public static void init() {
		Hunting.init();
		Foraging.init();
		Food.init();
		Camp.init();
		Discovery.init();
		GeneralFeats.init();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int tick = server.getTickCount();
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				Hunting.tick(player, tick);
				if (tick % 5 == 0) {
					Camp.tick(player, tick);
					GeneralFeats.tick(player, tick);
				}
				if (tick % 10 == 0) {
					Tracker.tick(player, tick);
				}
				// Staggered, so a full server spreads its discovery checks over the 40 ticks.
				if ((tick + player.getId()) % Discovery.INTERVAL == 0) {
					Discovery.check(player);
				}
			}
			if (tick % Weatherwise.INTERVAL == 0) {
				Weatherwise.tick(server);
			}
		});
		ServerPlayerEvents.LEAVE.register(player -> forget(player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			REPETITION.clear();
			LAST_CHIP.clear();
			Hunting.clear();
			Camp.clear();
			Tracker.clear();
			GeneralFeats.clear();
			Discovery.clear();
			server.getPlayerList().getPlayers().forEach(p -> forget(p.getUUID()));
		});
	}

	/** Awards Survival XP after the anti-farm taper for this kind of action. */
	public static void award(ServerPlayer player, String kind, double base) {
		if (base <= 0 || player.isCreative() || player.isSpectator()) {
			return;
		}
		Repetition rep = REPETITION.computeIfAbsent(player.getUUID(), u -> new HashMap<>()).computeIfAbsent(kind, k -> new Repetition());
		Progress.award(player, SURVIVAL, base * rep.next(player.level().getGameTime()) * dev.voidmc.mettle.character.cards.CardHooks.discoveryXp(player, kind));
	}

	/** A feedback chip, {@code mettle.feedback.survival.<key>}. */
	public static void chip(ServerPlayer player, Identifier perk, String key, Object... args) {
		SignaturePerks.feedback(player, perk, Component.translatable("mettle.feedback.survival." + key, args));
	}

	/** The same, at most once per {@code ticks} for this perk (passive perks that would otherwise chatter). */
	public static void chipThrottled(ServerPlayer player, Identifier perk, int ticks, String key, Object... args) {
		long now = player.level().getGameTime();
		Map<Identifier, Long> last = LAST_CHIP.computeIfAbsent(player.getUUID(), u -> new HashMap<>());
		Long before = last.get(perk);
		if (before == null || now - before >= ticks || now < before) {
			last.put(perk, now);
			chip(player, perk, key, args);
		}
	}

	public static void forget(UUID player) {
		REPETITION.remove(player);
		LAST_CHIP.remove(player);
		Hunting.forget(player);
		Camp.forget(player);
		Tracker.forget(player);
		GeneralFeats.forget(player);
		Discovery.forget(player);
		Foraging.forget(player);
		Weatherwise.forget(player);
	}
}
