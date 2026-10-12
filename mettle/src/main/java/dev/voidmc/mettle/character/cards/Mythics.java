package dev.voidmc.mettle.character.cards;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.MettleEvents;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Mythic;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardStore;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.DraftState;
import dev.voidmc.mettle.core.net.MettlePayloads;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Mythics (docs/CARDS.md §9): twenty single named cards. Seven are world firsts (one in the world, ever), five legendary
 * deeds (anyone, once each, numbered), eight drawn (one per skill, 0.5 % a draft from skill 15, once per world). They
 * are never released, scarred, dropped or sold; the only way to lose one is to trade it. The world is told when one is
 * earned, the Hall of Firsts keeps who holds what, and every player's Mythics are public ({@code mettle:character/mythics}).
 */
public final class Mythics {
	/** The Mythics a player holds, synced to everyone (name cards, chat). Not saved: rebuilt from the ledger. */
	public static final AttachmentType<List<Identifier>> PUBLIC = AttachmentRegistry.create(Mettle.id("character/mythics"), b -> b
		.syncWith(ByteBufCodecs.<io.netty.buffer.ByteBuf, Identifier>list().apply(Identifier.STREAM_CODEC).cast(), AttachmentSyncPredicate.all()));

	/** The bosses whose first kill is a world first, and their Mythic. */
	private static final Map<EntityType<?>, String> BOSSES = Map.of(EntityTypes.ENDER_DRAGON, "dragonfall", EntityTypes.WARDEN, "wardens_hush",
		EntityTypes.WITHER, "witherbane", EntityTypes.ELDER_GUARDIAN, "elders_eye");
	/** Mobs spawned by eggs, commands or spawners carry this tag and never count (§9.2). */
	public static final String UNNATURAL = "mettle.unnatural";
	private static final Map<LivingEntity, Map<UUID, Float>> FIGHTS = new IdentityHashMap<>();

	private Mythics() {
	}

	static void init() {
		PUBLIC.toString();
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (BOSSES.containsKey(entity.getType()) && source.getEntity() instanceof ServerPlayer p && taken > 0) {
				FIGHTS.computeIfAbsent(entity, e -> new HashMap<>()).merge(p.getUUID(), taken, Float::sum);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(Mythics::bossDied);
		// A boss that leaves without dying (unloaded, despawned, discarded) takes its fight with it.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof LivingEntity living) {
				FIGHTS.remove(living);
			}
		});
		MettleEvents.SKILL_LEVEL_UP.register((player, skill, from, to) -> {
			if (to >= 20) {
				first(player, "first_of_masters");
			}
			if (to >= 15 && allFifteen(player)) {
				deed(player, "polymath");
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 40 == 0) {
				server.getPlayerList().getPlayers().forEach(Mythics::watch);
			}
		});
		ServerPlayerEvents.JOIN.register(p -> {
			hall(p);
			Journey.started(p);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) {
				Journey.died(newPlayer);
			}
		});
	}

	// ---------------------------------------------------------------- reading

	public static ResourceKey<Perk> key(Identifier mythic) {
		return MythicRules.key(mythic);
	}

	public static Optional<Mythic> def(RegistryAccess registries, Identifier id) {
		return registries.lookupOrThrow(MettleRegistries.MYTHIC).getOptional(id);
	}

	/** Whether the player has this Mythic slotted and working (both sides: read from the synced cache). */
	public static boolean active(net.minecraft.world.entity.player.Player player, String id) {
		return CardStore.get(player).activeRank(key(Mettle.id(id))) > 0;
	}

	/** The drawn Mythic of a skill, if it has one. */
	public static Optional<Identifier> drawnOf(RegistryAccess registries, ResourceKey<Skill> skill) {
		return registries.lookupOrThrow(MettleRegistries.MYTHIC).listElements()
			.filter(r -> r.value().source() == Mythic.Source.DRAWN && r.value().skill().map(skill::equals).orElse(false))
			.map(r -> r.key().identifier()).findFirst();
	}

	// ---------------------------------------------------------------- earning

	/** A world first: only the first in the world gets it. Creative and spectator players never do. */
	public static boolean first(ServerPlayer player, String id) {
		if (player.isCreative() || player.isSpectator()) {
			return false;
		}
		CardLedger ledger = Cards.ledger(player.level().getServer());
		if (ledger.first(id).isPresent()) {
			return false;
		}
		return grant(player, Mettle.id(id), false).isPresent();
	}

	/** A legendary deed: anyone may earn it, once each. */
	public static boolean deed(ServerPlayer player, String id) {
		if (player.isCreative() || player.isSpectator()) {
			return false;
		}
		if (Cards.ledger(player.level().getServer()).deeds(id).contains(player.getUUID())) {
			return false;
		}
		return grant(player, Mettle.id(id), false).isPresent();
	}

	/**
	 * Makes a Mythic card for the player and tells the world. World firsts and drawn Mythics are claimed in the Hall of
	 * Firsts (refused when already claimed, unless {@code op}); deeds are numbered.
	 */
	public static Optional<CardData> grant(ServerPlayer player, Identifier id, boolean op) {
		MinecraftServer server = player.level().getServer();
		RegistryAccess registries = player.level().registryAccess();
		Optional<Mythic> def = def(registries, id);
		if (def.isEmpty()) {
			return Optional.empty();
		}
		CardLedger ledger = Cards.ledger(server);
		Cards.ensure(player);
		Mythic m = def.get();
		CardData.Holder holder = new CardData.Holder(player.getUUID(), player.getGameProfile().name(), Cards.day(server));
		Component how;
		if (m.source() == Mythic.Source.DEED) {
			if (!op && ledger.deeds(id.getPath()).contains(player.getUUID())) {
				return Optional.empty();
			}
			int n = ledger.deed(id.getPath(), player.getUUID());
			how = Component.translatable("mettle.mythic.copy", ordinal(n));
		} else {
			if (!op && ledger.first(id.getPath()).isPresent()) {
				return Optional.empty();
			}
			ledger.claim(id.getPath(), holder);
			how = Component.translatable(m.source() == Mythic.Source.DRAWN ? "mettle.mythic.how.drawn" : "mettle.mythic.how.first");
		}
		ResourceKey<Skill> skill = m.skill().orElse(ResourceKey.create(MettleRegistries.SKILL, Mettle.id("mythic")));
		DraftState.Card offer = new DraftState.Card(key(id), 1, DraftState.Rarity.MYTHIC, false, List.of(), List.of(), 0);
		CharacterState state = CharacterStore.get(player);
		CardData card = Cards.create(player, ledger, offer, state.level(skill), CardData.How.MYTHIC, CardData.Place.BINDER);
		Cards.sync(player);
		CardApi.mythicClaimed(player, id, card.id());
		server.getPlayerList().broadcastSystemMessage(Component.translatable(m.source() == Mythic.Source.DRAWN ? "mettle.mythic.world.drawn"
			: "mettle.mythic.world.earned", player.getName(), m.name().copy().withStyle(ChatFormatting.GOLD), how), false);
		SignaturePerks.feedback(player, Mettle.id("mythic/" + id.getPath()), Component.translatable("mettle.mythic.yours", m.name(), how));
		ServerPlayNetworking.send(player, new MettlePayloads.MythicReveal(id, how.getString()));
		server.getPlayerList().getPlayers().forEach(Mythics::hall);
		Mettle.LOGGER.info("{} earned the Mythic {} ({}, card {})", player.getName().getString(), id, how.getString(), card.id());
		return Optional.of(card);
	}

	private static String ordinal(int n) {
		int t = n % 100;
		String s = t >= 11 && t <= 13 ? "th" : switch (n % 10) {
			case 1 -> "st";
			case 2 -> "nd";
			case 3 -> "rd";
			default -> "th";
		};
		return n + s;
	}

	/** Whether the drawn Mythic of a skill still waits in its deck (§3.5). */
	public static boolean drawable(MinecraftServer server, Identifier id) {
		return Cards.ledger(server).first(id.getPath()).isEmpty();
	}

	public static boolean rolls(long seed, int dealt, int level) {
		return MythicRules.rolls(seed, dealt, level);
	}

	public static DraftState.Card offer(Identifier id) {
		return MythicRules.offer(id);
	}

	// ---------------------------------------------------------------- triggers

	private static void bossDied(LivingEntity killed, net.minecraft.world.damagesource.DamageSource source) {
		Map<UUID, Float> fight = FIGHTS.remove(killed);
		String mythic = BOSSES.get(killed.getType());
		// A one-hit kill dies inside hurtServer, before AFTER_DAMAGE: the killing blow is then the whole fight.
		if (mythic != null && fight == null && source.getEntity() instanceof ServerPlayer killer) {
			fight = Map.of(killer.getUUID(), killed.getMaxHealth());
		}
		if (mythic == null || fight == null || killed.entityTags().contains(UNNATURAL) || killed.entityTags().contains(dev.voidmc.mettle.character.skill.Combat.SPAWNER_TAG)) {
			return;
		}
		MinecraftServer server = killed.level().getServer();
		ServerPlayer credited = credit(server, fight, source);
		if (credited == null) {
			return;
		}
		first(credited, mythic);
		if (killed.getType() == EntityTypes.WARDEN && unclad(credited)) {
			deed(credited, "unclad");
		}
	}

	static ServerPlayer credit(MinecraftServer server, Map<UUID, Float> fight, net.minecraft.world.damagesource.DamageSource source) {
		UUID best = MythicRules.creditOf(fight, source.getEntity() == null ? new UUID(0, 0) : source.getEntity().getUUID());
		ServerPlayer p = best == null ? null : server.getPlayerList().getPlayer(best);
		return p == null || p.isCreative() || p.isSpectator() ? null : p;
	}

	static boolean unclad(LivingEntity player) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.isArmor() && !player.getItemBySlot(slot).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private static boolean allFifteen(ServerPlayer player) {
		CharacterState state = CharacterStore.get(player);
		for (Holder.Reference<Skill> ref : player.level().registryAccess().lookupOrThrow(MettleRegistries.SKILL).listElements().toList()) {
			if (ref.value().released() && state.level(ref.key()) < 15) {
				return false;
			}
		}
		return true;
	}

	/** Every 2 s: the far horizon, a hundred days, a hundred kilometres on foot, a raid won alone. */
	private static void watch(ServerPlayer player) {
		if (player.isCreative() || player.isSpectator()) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		if (player.level().dimension() == Level.OVERWORLD) {
			var spawn = server.overworld().getRespawnData().pos();
			double dx = player.getX() - spawn.getX();
			double dz = player.getZ() - spawn.getZ();
			if (Math.sqrt(dx * dx + dz * dz) >= CardConfig.get().far_horizon_blocks) {
				first(player, "far_horizon");
			}
		}
		if (Cards.day(server) - Journey.since(player) >= 100) {
			deed(player, "hundredth_dawn");
		}
		var stats = player.getStats();
		long cm = (long) stats.getValue(Stats.CUSTOM.get(Stats.WALK_ONE_CM)) + stats.getValue(Stats.CUSTOM.get(Stats.SPRINT_ONE_CM))
			+ stats.getValue(Stats.CUSTOM.get(Stats.SWIM_ONE_CM)) + stats.getValue(Stats.CUSTOM.get(Stats.CROUCH_ONE_CM))
			+ stats.getValue(Stats.CUSTOM.get(Stats.WALK_ON_WATER_ONE_CM)) + stats.getValue(Stats.CUSTOM.get(Stats.WALK_UNDER_WATER_ONE_CM));
		if (cm >= 10_000_000L) {
			deed(player, "long_walker");
		}
	}

	/** A raid won (Hero of the Village given): the first is The Open Hand without Hearth; alone, The Lone Banner. */
	static void heroOfTheVillage(ServerPlayer player) {
		first(player, "open_hand");
		boolean alone = player.level().players().stream().noneMatch(p -> p != player && p.distanceToSqr(player) <= 96 * 96);
		if (alone) {
			deed(player, "lone_banner");
		}
	}

	static boolean isHero(net.minecraft.world.effect.MobEffectInstance effect) {
		return effect.getEffect().equals(MobEffects.HERO_OF_THE_VILLAGE);
	}

	// ---------------------------------------------------------------- what everyone sees

	/** Rebuilds the player's public list of Mythics from their cards (after any change to them). */
	static void publish(ServerPlayer player) {
		List<Identifier> held = new ArrayList<>();
		CardStore.get(player).cards().forEach(c -> c.mythicId().ifPresent(held::add));
		List<Identifier> before = player.getAttached(PUBLIC);
		if (!held.equals(before == null ? List.of() : before)) {
			if (held.isEmpty()) {
				player.removeAttached(PUBLIC);
			} else {
				player.setAttached(PUBLIC, List.copyOf(held));
			}
		}
	}

	/** The Hall of Firsts for one player's client: every claimed first and draw, every deed's copies. */
	static void hall(ServerPlayer player) {
		CardLedger ledger = Cards.ledger(player.level().getServer());
		List<MettlePayloads.HallEntry> entries = new ArrayList<>();
		player.level().registryAccess().lookupOrThrow(MettleRegistries.MYTHIC).listElements().forEach(r -> {
			String id = r.key().identifier().getPath();
			Optional<CardData.Holder> h = ledger.first(id);
			entries.add(new MettlePayloads.HallEntry(r.key().identifier(), h.map(CardData.Holder::name).orElse(""), h.map(CardData.Holder::day).orElse(-1L),
				ledger.deeds(id).size()));
		});
		ServerPlayNetworking.send(player, new MettlePayloads.HallOfFirsts(entries));
	}

	/** How long a player has lived (for Hundredth Dawn): the in-game day they last came back from death. */
	static final class Journey {
		static final AttachmentType<Long> ALIVE_SINCE = AttachmentRegistry.create(Mettle.id("character/alive_since"), b -> b
			.persistent(com.mojang.serialization.Codec.LONG).copyOnDeath());

		private Journey() {
		}

		static void started(ServerPlayer player) {
			if (player.getAttached(ALIVE_SINCE) == null) {
				player.setAttached(ALIVE_SINCE, Cards.day(player.level().getServer()));
			}
		}

		static void died(ServerPlayer player) {
			player.setAttached(ALIVE_SINCE, Cards.day(player.level().getServer()));
		}

		static long since(ServerPlayer player) {
			Long d = player.getAttached(ALIVE_SINCE);
			return d == null ? Cards.day(player.level().getServer()) : d;
		}
	}
}
