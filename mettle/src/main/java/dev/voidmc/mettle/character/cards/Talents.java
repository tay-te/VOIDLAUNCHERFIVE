package dev.voidmc.mettle.character.cards;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.MettleEvents;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Talent;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardStore;
import dev.voidmc.mettle.core.CardsState;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.DraftState;
import dev.voidmc.mettle.core.MettleSecret;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Talents on the server (docs/BUILD_FEEL.md §13): the talent draft (queued at character level 3 and every 4 after, a hand
 * of three dealt from the player's secret seed, keep one or spend a reroll from the shared pool), the talent row (slots
 * by character level, one card per talent, a better copy takes a worse one's slot), rearranging it (free below the
 * low-level line, for 60 s after waking from a night's sleep, else a free swap), and the effects that need the world
 * (Light Sleeper, Cold Blood's chill). Talent cards live in the world ledger like every card and are committed with the
 * player's file when kept; they are bound: never traded, listed or put in a skill deck.
 */
public final class Talents {
	/** When each sleeping player lay down (game time), for "a full night". */
	private static final Map<UUID, Long> ASLEEP = new HashMap<>();

	private Talents() {
	}

	static void init() {
		MettleEvents.CHARACTER_LEVEL_UP.register((player, from, to) -> refresh(player));
		ServerPlayerEvents.JOIN.register(Talents::refresh);
		ServerPlayerEvents.LEAVE.register(p -> ASLEEP.remove(p.getUUID()));
		EntitySleepEvents.START_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayer p) {
				ASLEEP.put(p.getUUID(), p.level().getGameTime());
			}
		});
		EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayer p) {
				woke(p);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0) {
				server.getPlayerList().getPlayers().forEach(Talents::second);
			}
		});
	}

	// ---------------------------------------------------------------- reading (both sides)

	/** Every talent in the registry, by id. */
	public static List<TalentRules.Entry> all(RegistryAccess registries) {
		List<TalentRules.Entry> out = new ArrayList<>();
		registries.lookupOrThrow(MettleRegistries.TALENT).listElements().forEach(r -> out.add(new TalentRules.Entry(r.key().identifier(), r.value())));
		out.sort(java.util.Comparator.comparing(e -> e.id().toString()));
		return out;
	}

	public static Optional<Talent> def(RegistryAccess registries, Identifier id) {
		return registries.lookupOrThrow(MettleRegistries.TALENT).getOptional(id);
	}

	/** A talent's slotted value for a player (0 when not in the row), in its unit. */
	public static double value(Player player, String talent) {
		return CardStore.get(player).trait(Mettle.id("talent/" + talent));
	}

	public static boolean has(Player player, String talent) {
		return value(player, talent) > 0;
	}

	// ---------------------------------------------------------------- the talent draft

	/**
	 * Queues the talent drafts the character level earned and not yet paid (an old character gets all of its once), and
	 * deals the next hand when one is due.
	 */
	public static void refresh(ServerPlayer player) {
		CharacterState state = Cards.ensure(player);
		DraftState d = state.drafts();
		DraftState.TalentDraft t = d.talent();
		int level = state.characterLevel();
		int earned = TalentRules.draftsBetween(t.upTo(), level);
		if (earned > 0 || t.upTo() < level) {
			t = t.withQueued(t.queued() + earned).withUpTo(Math.max(t.upTo(), level));
			if (earned > 0) {
				Mettle.LOGGER.info("{}: {} talent drafts queued (character level {})", player.getName().getString(), earned, level);
				SignaturePerks.feedback(player, Mettle.id("talent/draft"), Component.translatable(earned == 1 ? "mettle.talent.draft_ready"
					: "mettle.talent.drafts_ready", earned));
			}
		}
		if (t.queued() > 0 && t.hand().isEmpty()) {
			t = deal(player, state, t);
		}
		if (!t.equals(d.talent())) {
			CharacterStore.set(player, state.withDraft(d.withTalent(t)));
		}
	}

	private static DraftState.TalentDraft deal(ServerPlayer player, CharacterState state, DraftState.TalentDraft t) {
		RegistryAccess registries = player.level().registryAccess();
		Map<Identifier, Integer> owned = TalentRules.owned(CardStore.get(player).talent().cards());
		Random random = new Random(MettleSecret.hash(state.drafts().seed(), "talent", t.dealt()));
		List<DraftState.Card> hand = TalentRules.deal(all(registries), id -> owned.getOrDefault(id, 0), state.characterLevel(), random);
		return t.dealtHand(hand);
	}

	/** Keeps card {@code index} of the talent hand: a talent card in the ledger, into the row when a slot is free or it is better. */
	public static Optional<CardRules.Refusal> keep(ServerPlayer player, int index) {
		CharacterState state = Cards.ensure(player);
		DraftState d = state.drafts();
		DraftState.TalentDraft t = d.talent();
		if (t.queued() <= 0 || t.hand().isEmpty()) {
			return Optional.of(CardRules.Refusal.NO_TALENT_DRAFT);
		}
		if (index < 0 || index >= t.hand().size()) {
			return Optional.of(CardRules.Refusal.NO_CARD);
		}
		DraftState.Card offer = t.hand().get(index);
		MinecraftServer server = player.level().getServer();
		CardLedger ledger = Cards.ledger(server);
		// The draft is spent first, so the same offer can never be kept twice.
		CharacterStore.set(player, state.withDraft(d.withTalent(t.withQueued(t.queued() - 1).withHand(List.of()))));
		CardData card = Cards.create(player, ledger, offer, state.characterLevel(), CardData.How.DRAFT, CardData.Place.BINDER);
		Cards.sync(player);
		boolean slotted = slot(player, card.id(), -1, true).isEmpty();
		refresh(player);
		LedgerFile.commit(server, player);
		Identifier id = TalentRules.id(offer.perk());
		Component name = def(player.level().registryAccess(), id).map(Talent::name).orElse(Component.literal(id.getPath()));
		SignaturePerks.feedback(player, Mettle.id("talent/" + id.getPath()), Component.translatable(slotted ? "mettle.talent.kept_slotted" : "mettle.talent.kept", name,
			Component.translatable("mettle.draft.rarity." + offer.rarity().getSerializedName()), Cards.printId(player.level().registryAccess(), card)));
		Mettle.LOGGER.info("{} kept the talent {} ({}, {}) as card {}{}", player.getName().getString(), id, offer.rarity().getSerializedName(),
			TalentRules.value(card), card.id(), slotted ? ", in the row" : "");
		return Optional.empty();
	}

	/** Deals the talent hand again for a reroll from the shared pool. */
	public static Optional<CardRules.Refusal> reroll(ServerPlayer player) {
		CharacterState state = Cards.ensure(player);
		DraftState d = state.drafts();
		DraftState.TalentDraft t = d.talent();
		if (t.queued() <= 0 || t.hand().isEmpty()) {
			return Optional.of(CardRules.Refusal.NO_TALENT_DRAFT);
		}
		if (d.rerolls() <= 0) {
			return Optional.of(CardRules.Refusal.NO_REROLLS);
		}
		DraftState.TalentDraft next = deal(player, state, t);
		CharacterStore.set(player, state.withDraft(d.withRerolls(d.rerolls() - 1).withTalent(next)));
		SignaturePerks.feedback(player, Mettle.id("talent/draft"), Component.translatable("mettle.talent.rerolled", d.rerolls() - 1));
		return Optional.empty();
	}

	// ---------------------------------------------------------------- the row

	/** Puts a talent card in row slot {@code index} (-1: the first free). Free; a better copy takes a worse one's slot. */
	public static Optional<CardRules.Refusal> slot(ServerPlayer player, long id, int index) {
		return slot(player, id, index, false);
	}

	private static Optional<CardRules.Refusal> slot(ServerPlayer player, long id, int index, boolean quiet) {
		CharacterState state = Cards.ensure(player);
		CardsState cards = CardStore.get(player);
		Optional<CardData> card = cards.talent().card(id);
		if (card.isEmpty()) {
			return Optional.of(cards.card(id).isPresent() ? CardRules.Refusal.NOT_A_TALENT : CardRules.Refusal.NO_CARD);
		}
		int slots = TalentRules.slots(state.characterLevel());
		List<CardData> row = cards.talent().slotted();
		Optional<CardRules.Refusal> no = TalentRules.canSlot(card.get(), index, row, slots);
		if (no.isPresent()) {
			return no;
		}
		TalentRules.Slotting s = TalentRules.slotting(card.get(), index, row, slots).orElseThrow();
		CardLedger ledger = Cards.ledger(player.level().getServer());
		s.replaces().ifPresent(old -> ledger.update(old.id(), x -> x.withPlace(CardData.Place.BINDER)));
		ledger.update(id, x -> x.withPlace(CardData.Place.slotted(CardData.TALENT, s.at())));
		Cards.sync(player);
		if (!quiet) {
			SignaturePerks.feedback(player, Mettle.id("talent/row"), Component.translatable(s.replaces().isPresent() ? "mettle.talent.upgraded" : "mettle.talent.slotted",
				name(player, card.get()), s.at() + 1));
		}
		return Optional.empty();
	}

	/** Takes a talent card out of the row: free below the low-level line or while the waking window is open, else a free swap. */
	public static Optional<CardRules.Refusal> unslot(ServerPlayer player, long id) {
		CharacterState state = Cards.ensure(player);
		CardsState cards = CardStore.get(player);
		Optional<CardData> card = cards.talent().card(id);
		if (card.isEmpty()) {
			return Optional.of(CardRules.Refusal.NO_CARD);
		}
		if (!card.get().slotted()) {
			return Optional.of(CardRules.Refusal.NOT_SLOTTED);
		}
		long now = player.level().getServer().overworld().getGameTime();
		Optional<TalentRules.Way> way = TalentRules.rearrange(state.characterLevel(), cards.talent().awake(now), cards.freeSwaps());
		if (way.isEmpty()) {
			return Optional.of(CardRules.Refusal.TALENT_LOCKED);
		}
		Cards.ledger(player.level().getServer()).update(id, x -> x.withPlace(CardData.Place.BINDER));
		if (way.get() == TalentRules.Way.FREE_SWAP) {
			CardStore.set(player, CardStore.get(player).withFreeSwaps(cards.freeSwaps() - 1));
		}
		Cards.sync(player);
		spillPockets(player);
		SignaturePerks.feedback(player, Mettle.id("talent/row"), Component.translatable("mettle.talent.unslotted." + way.get().name().toLowerCase(java.util.Locale.ROOT),
			name(player, card.get()), CardStore.get(player).freeSwaps()));
		return Optional.empty();
	}

	/** Packrat left the row: whatever sat in the belt pocket it gave goes back to the inventory (or the ground). */
	static void spillPockets(ServerPlayer player) {
		dev.voidmc.mettle.gear.GearInventory.of(player).spillHidden(dev.voidmc.mettle.api.GearSlot.BELT);
	}

	static Component name(Player player, CardData card) {
		return card.talentId().flatMap(id -> def(player.level().registryAccess(), id)).map(Talent::name).orElse(Component.literal("?"));
	}

	// ---------------------------------------------------------------- waking (§13.1)

	/** Woke: after a full night's sleep the row may be rearranged freely for a while ("Reconsider your talents"). */
	static void woke(ServerPlayer player) {
		Long since = ASLEEP.remove(player.getUUID());
		if (since == null) {
			return;
		}
		long gameTime = player.level().getServer().overworld().getGameTime();
		long until = TalentRules.wakeUntil(gameTime - since, player.level().getOverworldClockTime(), gameTime);
		if (until <= 0 || CharacterStore.get(player).characterLevel() < CardConfig.get().talent_draft_from) {
			return;
		}
		CardStore.set(player, CardStore.get(player).withWakeUntil(until));
		SignaturePerks.feedback(player, Mettle.id("talent/wake"), Component.translatable("mettle.talent.reconsider", CardConfig.get().talent_swap_on_wake_s));
		Mettle.LOGGER.info("{} woke from a night's sleep: the talent row may be rearranged for {} s", player.getName().getString(),
			CardConfig.get().talent_swap_on_wake_s);
	}

	/** Opens the waking window now (ops and tests). */
	public static void openWake(ServerPlayer player) {
		long gameTime = player.level().getServer().overworld().getGameTime();
		CardStore.set(player, CardStore.get(player).withWakeUntil(gameTime + CardConfig.get().talent_swap_on_wake_s * 20L));
	}

	/** Once a second: Light Sleeper wakes you when a hostile comes near the bed; Cold Blood slows the chill. */
	private static void second(ServerPlayer player) {
		if (player.isSleeping() && has(player, "light_sleeper")) {
			BlockPos bed = player.getSleepingPos().orElse(player.blockPosition());
			double r = TalentRules.LIGHT_SLEEPER_BLOCKS;
			boolean near = !player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new AABB(bed).inflate(r), m -> m instanceof Enemy && m.isAlive()).isEmpty();
			if (near) {
				player.stopSleepInBed(true, true);
				SignaturePerks.feedback(player, Mettle.id("talent/light_sleeper"), Component.translatable("mettle.talent.light_sleeper.woke"));
			}
		}
		double cold = value(player, "cold_blood") / 100.0;
		if (cold > 0 && player.getTicksFrozen() > 0 && player.isInPowderSnow) {
			// Vanilla adds one tick of chill a tick in powder snow; take back that share of the last second's.
			int back = (int) Math.floor(20 * cold + player.getRandom().nextDouble());
			player.setTicksFrozen(Math.max(0, player.getTicksFrozen() - back));
		}
	}

	/** Light Sleeper: phantoms leave the player be one night in two (the PhantomSpawner check). */
	public static boolean phantomsSpare(ServerPlayer player) {
		return has(player, "light_sleeper") && Math.floorMod(Cards.day(player.level().getServer()), 2) == 1;
	}

	/** Homebody: within 64 blocks of the bed or respawn point (in that dimension). */
	public static boolean home(ServerPlayer player) {
		var respawn = player.getRespawnConfig();
		if (respawn == null) {
			var spawn = player.level().getServer().overworld().getRespawnData();
			return player.level().dimension() == net.minecraft.world.level.Level.OVERWORLD
				&& spawn.pos().distSqr(player.blockPosition()) <= TalentRules.HOMEBODY_BLOCKS * TalentRules.HOMEBODY_BLOCKS;
		}
		return respawn.respawnData().dimension() == player.level().dimension()
			&& respawn.respawnData().pos().distSqr(player.blockPosition()) <= TalentRules.HOMEBODY_BLOCKS * TalentRules.HOMEBODY_BLOCKS;
	}

	/**
	 * At a Card Shrine: three binder cards of one talent and rarity (checked by {@link CardRules#canFuse}) become one of
	 * the next rarity, its value rolled in that rarity's band, never below the best of the three. Free.
	 */
	static Optional<CardRules.Refusal> fuse(ServerPlayer player, List<CardData> picked) {
		CardData first = picked.getFirst();
		Optional<DraftState.Rarity> into = CardRules.fusesInto(first.rarity());
		Optional<Talent> def = first.talentId().flatMap(id -> def(player.level().registryAccess(), id));
		if (into.isEmpty() || def.isEmpty() || def.get().isFixed()) {
			return Optional.of(CardRules.Refusal.TOP_RARITY);
		}
		MinecraftServer server = player.level().getServer();
		Identifier id = first.talentId().orElseThrow();
		Random random = new Random(MettleSecret.hash(MettleSecret.salt(server), "fuse_talent", first.id(), picked.get(1).id(), picked.get(2).id()));
		double best = picked.stream().mapToDouble(TalentRules::value).max().orElse(0);
		double v = Math.max(best, TalentRules.value(def.get(), into.get(), random));
		v = Math.min(v, TalentRules.band(def.get(), into.get())[1]);
		DraftState.Card offer = new DraftState.Card(first.perk(), 1, into.get(), false, List.of(), List.of(new CardData.TraitRoll(TalentRules.trait(id), v)), 0);
		CardLedger ledger = Cards.ledger(server);
		picked.forEach(c -> ledger.update(c.id(), x -> x.withPlace(CardData.Place.RELEASED)));
		CardData made = Cards.create(player, ledger, offer, CharacterStore.get(player).characterLevel(), CardData.How.FUSED, CardData.Place.BINDER);
		Cards.sync(player);
		LedgerFile.commit(server, player);
		SignaturePerks.feedback(player, Mettle.id("talent/fused"), Component.translatable("mettle.talent.fused", name(player, made),
			Component.translatable("mettle.draft.rarity." + made.rarity().getSerializedName())));
		Mettle.LOGGER.info("{} fused talent cards {} into {} ({})", player.getName().getString(), picked.stream().map(CardData::id).toList(), made.id(),
			made.rarity().getSerializedName());
		return Optional.empty();
	}

	// ---------------------------------------------------------------- ops and tests

	/** Gives a talent card (ops and tests): {@code rarity} null rolls it by character level. */
	public static CardData give(ServerPlayer player, Identifier talent, DraftState.Rarity rarity, double value, boolean slotIt) {
		CharacterState state = Cards.ensure(player);
		Talent def = def(player.level().registryAccess(), talent).orElseThrow(() -> new IllegalArgumentException("no talent " + talent));
		Random random = new Random(MettleSecret.hash(MettleSecret.salt(player.level().getServer()), "give_talent", state.drafts().seed(),
			Cards.ledger(player.level().getServer()).nextId()));
		DraftState.Card offer = TalentRules.roll(talent, def, state.characterLevel(), random);
		if (rarity != null || value > 0) {
			DraftState.Rarity r = rarity != null ? rarity : offer.rarity();
			double v = value > 0 ? value : TalentRules.value(def, r, random);
			offer = new DraftState.Card(offer.perk(), 1, r, false, List.of(), List.of(new CardData.TraitRoll(TalentRules.trait(talent), v)), 0);
		}
		CardData card = Cards.create(player, Cards.ledger(player.level().getServer()), offer, state.characterLevel(), CardData.How.GIVEN, CardData.Place.BINDER);
		Cards.sync(player);
		if (slotIt) {
			slot(player, card.id(), -1, true).ifPresent(r -> Cards.refused(player, r));
		}
		return Cards.ledger(player.level().getServer()).get(card.id()).orElse(card);
	}
}
