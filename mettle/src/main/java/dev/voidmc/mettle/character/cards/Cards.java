package dev.voidmc.mettle.character.cards;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.Background;
import dev.voidmc.mettle.api.CharacterAttribute;
import dev.voidmc.mettle.api.MettleEvents;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.draft.DraftRules;
import dev.voidmc.mettle.character.draft.Drafts;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardStore;
import dev.voidmc.mettle.core.CardsState;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.DraftState;
import dev.voidmc.mettle.core.DraftState.Card;
import dev.voidmc.mettle.core.DraftState.Rarity;
import dev.voidmc.mettle.core.EffectsApplier;
import dev.voidmc.mettle.core.net.MettlePayloads;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Cards v2 on the server (docs/CARDS.md): the ledger, keeping drafted cards, slotting and unslotting, pinning sets, the
 * v1 → v2 migration, and keeping each player's cache and derived state (active perks, dormancy, traits, sets) in step.
 * Server thread; the client only asks ({@link MettlePayloads.CardAction}) and reads its synced cache.
 */
public final class Cards {
	/** How a card left a slot (§7.4): the free ways of wave A, then the rebuild of wave B. */
	public enum Way {
		/** Below character level 5 every rebuild is free. */
		LOW_LEVEL,
		/** Only dormant cards: free. */
		DORMANT,
		/** The first rebuild of a deck after the v2 migration. */
		FREE_REBUILD,
		/** Free swaps, one per card. */
		FREE_SWAP,
		/** A rebuild at a trainer or Card Shrine (draught, fee, scar). */
		REBUILD,
		/** An op's command, or a respec. */
		OP
	}

	private Cards() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			CardConfig.load(FabricLoader.getInstance().getConfigDir());
			int back = ledger(server).recoverTrades();
			if (back > 0) {
				Mettle.LOGGER.info("cards: {} cards left in trades went back to their owners", back);
			}
		});
		ServerPlayerEvents.JOIN.register(Cards::join);
		MettleEvents.CHARACTER_LEVEL_UP.register((player, from, to) -> addFreeSwaps(player, to - from));
		EffectsApplier.BEFORE.add(Cards::derive);
		EffectsApplier.AFTER.add(CardEffects::apply);
		ServerPlayNetworking.registerGlobalReceiver(MettlePayloads.CardAction.TYPE, (payload, context) -> act(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(MettlePayloads.Updraft.TYPE, (payload, context) -> CardHooks.updraft(context.player()));
		CardEffects.init();
		CardHooks.init();
		CardBlocks.init();
		Rebuilds.init();
		Trades.init();
		Mythics.init();
		MythicEffects.init();
	}

	// ---------------------------------------------------------------- reading

	public static CardLedger ledger(MinecraftServer server) {
		return CardLedger.get(server);
	}

	public static CardsState state(Player player) {
		return CardStore.get(player);
	}

	public static CardRules.Data data(RegistryAccess registries) {
		return RegistryCardData.of(registries);
	}

	/** The character as {@link CardRules} deals for it: skill levels, governing attributes, cards owned per perk. */
	public static CardRules.Sheet sheet(RegistryAccess registries, CharacterState state, CardsState cards) {
		Registry<Skill> skills = registries.lookupOrThrow(MettleRegistries.SKILL);
		Background background = CharacterStore.background(registries, state);
		Map<ResourceKey<Perk>, Integer> owned = new HashMap<>();
		cards.cards().forEach(c -> owned.merge(c.perk(), 1, Integer::sum));
		return new CardRules.Sheet() {
			@Override
			public int level(ResourceKey<Skill> skill) {
				return state.level(skill);
			}

			@Override
			public int attribute(ResourceKey<Skill> skill) {
				return skills.getOptional(skill).map(s -> state.attribute(s.attribute(), background)).orElse(0);
			}

			@Override
			public int owned(ResourceKey<Perk> perk) {
				return owned.getOrDefault(perk, 0);
			}
		};
	}

	public static CardRules.Attributes attributes(RegistryAccess registries, CharacterState state) {
		Background background = CharacterStore.background(registries, state);
		return a -> state.attribute(a, background);
	}

	public static int wits(RegistryAccess registries, CharacterState state) {
		return state.attribute(CharacterAttribute.WITS, CharacterStore.background(registries, state));
	}

	/** The in-game day (for card history and the once-a-day pin). */
	public static long day(MinecraftServer server) {
		return server.overworld().getOverworldClockTime() / 24000L;
	}

	public static String printId(RegistryAccess registries, CardData card) {
		return CardRules.printId(data(registries).prefix(card.skill()), card.id());
	}

	// ---------------------------------------------------------------- keeping the cache in step

	private static void join(ServerPlayer player) {
		ensure(player);
		sync(player);
	}

	/**
	 * The character with drafts and cards: an old save goes through the draft migration (DRAFT.md §7) and then the v2
	 * migration (§12) once, before anything else counts.
	 */
	public static CharacterState ensure(ServerPlayer player) {
		CharacterState state = Drafts.ensure(player);
		if (CardStore.get(player).version() == 0) {
			migrate(player);
			state = CharacterStore.get(player);
		}
		return state;
	}

	/**
	 * Rebuilds the player's cache from the ledger (an id the ledger gives to someone else is dropped and logged; a card
	 * it gives this player is put back), works out what the deck makes active, and re-applies effects.
	 */
	public static void sync(ServerPlayer player) {
		CardsState cache = CardStore.get(player);
		CardRules.Reconciled r = CardRules.reconcile(cache.cards(), ledger(player.level().getServer()).owned(player.getUUID()));
		if (r.changed()) {
			if (!r.dropped().isEmpty()) {
				Mettle.LOGGER.warn("{}: cached cards {} belong to someone else in the ledger; dropped", player.getName().getString(), r.dropped());
			}
			if (!r.restored().isEmpty() && !cache.cards().isEmpty()) {
				Mettle.LOGGER.warn("{}: cards {} were missing from the cache; put back", player.getName().getString(), r.restored());
			}
		}
		if (!r.cards().equals(cache.cards())) {
			CardStore.set(player, cache.withCards(r.cards()));
		}
		EffectsApplier.apply(player);
		Mythics.publish(player);
	}

	/** Works out dormancy, active perks, trait totals and sets from the cache; sets it when anything changed. */
	public static void derive(ServerPlayer player) {
		RegistryAccess registries = player.level().registryAccess();
		CharacterState state = CharacterStore.get(player);
		CardsState cards = CardStore.get(player);
		Registry<Perk> perks = registries.lookupOrThrow(MettleRegistries.PERK);
		CardRules.Derived d = CardRules.derive(cards.cards(), state::level, wits(registries, state), attributes(registries, state),
			perks::getOptional, data(registries), cards.pins(), cards.setOrder());
		CardsState next = cards.withDerived(d.order(), d.active(), d.dormant(), d.traits(), d.sets(), d.tiers());
		if (!next.equals(cards)) {
			CardStore.set(player, next);
		}
	}

	// ---------------------------------------------------------------- the v1 → v2 migration (§12)

	/**
	 * Each drafted perk held becomes one card; a rank-r root gives back r − 1 drafts; the decks are filled deepest
	 * first; stored v1 hands are dealt again; three free swaps and a free first rebuild of each deck. Earned perks stay.
	 */
	public static void migrate(ServerPlayer player) {
		RegistryAccess registries = player.level().registryAccess();
		CharacterState state = CharacterStore.get(player);
		DraftRules.Deck deck = Drafts.deck(registries);
		CardRules.Migration m = CardRules.migrate(state.perks(), deck, data(registries), state.drafts().seed() ^ player.getUUID().getLeastSignificantBits(),
			state::level, wits(registries, state));
		CardLedger ledger = ledger(player.level().getServer());
		List<CardData> made = new ArrayList<>();
		for (CardRules.Migrated c : m.cards()) {
			CardData.Place place = c.slotted() ? CardData.Place.slotted(c.def().skill(), c.index()) : CardData.Place.BINDER;
			CardData card = create(player, ledger, c.card(), state.level(c.def().skill()), CardData.How.MIGRATED, place);
			made.add(card);
		}
		// The drafted perks are cards now; earned perks (feats, synergies, teachings, signature) stay learned.
		Map<ResourceKey<Perk>, Integer> kept = new HashMap<>();
		state.perks().forEach((k, v) -> {
			if (deck.get(k).map(p -> !p.kind().drafted()).orElse(true)) {
				kept.put(k, v);
			}
		});
		DraftState d = state.drafts();
		for (Map.Entry<ResourceKey<Skill>, Integer> e : m.drafts().entrySet()) {
			d = d.addQueued(e.getKey(), e.getValue());
		}
		// Stored v1 hands are dealt again under v2 rules.
		for (ResourceKey<Skill> skill : List.copyOf(d.hands().keySet())) {
			d = d.withHand(skill, List.of());
		}
		CharacterStore.set(player, state.withPerks(kept).withDraft(d));
		CardsState cards = CardStore.get(player).withVersion(1);
		if (!made.isEmpty()) {
			List<ResourceKey<Skill>> skills = registries.lookupOrThrow(MettleRegistries.SKILL).listElements().map(Holder.Reference::key).toList();
			cards = cards.withFreeSwaps(Math.max(cards.freeSwaps(), CardConfig.get().free_swaps_max)).withFreeRebuilds(skills);
			Mettle.LOGGER.info("{}: {} drafted perks became cards ({} slotted), {} drafts came back", player.getName().getString(), made.size(),
				made.stream().filter(CardData::slotted).count(), m.drafts().values().stream().mapToInt(Integer::intValue).sum());
		}
		CardStore.set(player, cards.withCards(ledger.owned(player.getUUID())));
		Drafts.refresh(player);
		EffectsApplier.apply(player);
		if (!made.isEmpty()) {
			SignaturePerks.feedback(player, Mettle.id("cards/migrated"), Component.translatable("mettle.cards.migrated", made.size()));
		}
	}

	/** Makes a card from an offer and puts it in the ledger. */
	static CardData create(ServerPlayer player, CardLedger ledger, Card offer, int level, CardData.How how, CardData.Place place) {
		long id = ledger.allocate();
		Perk perk = player.level().registryAccess().lookupOrThrow(MettleRegistries.PERK).getValueOrThrow(offer.perk());
		long day = day(player.level().getServer());
		String name = player.getGameProfile().name();
		long scarSeed = new Random(CharacterStore.get(player).drafts().seed() * 31 + id).nextLong();
		CardData card = new CardData(id, perk.skill(), offer.perk(), offer.rarity(), offer.rank(), offer.keywords(), offer.traits(), offer.needs(),
			new CardData.Drawn(player.getUUID(), name, level, day, System.currentTimeMillis(), how), player.getUUID(),
			CardData.History.first(new CardData.Holder(player.getUUID(), name, day)), 0, scarSeed, place, 0L);
		ledger.add(card);
		return card;
	}

	// ---------------------------------------------------------------- keeping a card from a draft

	/** What to do with a kept card: the binder, a slot, or a slot when a legal one is free (the old pick). */
	public enum Keep {
		BINDER, SLOT, AUTO
	}

	/** Whether the binder has room for one more card. */
	public static boolean binderRoom(Player player) {
		return CardStore.get(player).cards().size() < CardConfig.get().binder_max;
	}

	/**
	 * A kept offer becomes a card in the binder (or a deck); returns it. The caller (the draft) has already checked the
	 * offer and the binder's room.
	 */
	public static CardData keep(ServerPlayer player, Card offer, Keep keep) {
		CharacterState state = ensure(player);
		Perk perk = player.level().registryAccess().lookupOrThrow(MettleRegistries.PERK).getValueOrThrow(offer.perk());
		CardData card = create(player, ledger(player.level().getServer()), offer, state.level(perk.skill()),
			offer.wildcard() ? CardData.How.WILDCARD : CardData.How.DRAFT, CardData.Place.BINDER);
		sync(player);
		if (keep != Keep.BINDER) {
			Optional<CardRules.Refusal> no = slot(player, card.id(), -1);
			if (no.isPresent() && keep == Keep.SLOT) {
				refused(player, no.get());
			}
		}
		MettleEvents.PERK_LEARNED.invoker().learned(player, offer.perk(), offer.rank());
		return ledger(player.level().getServer()).get(card.id()).orElse(card);
	}

	// ---------------------------------------------------------------- slotting and unslotting

	/** Puts a binder card in slot {@code index} (-1: the first free) of its skill's deck. Free, anywhere (§5.2). */
	public static Optional<CardRules.Refusal> slot(ServerPlayer player, long id, int index) {
		return slot(player, id, index, null);
	}

	/** {@link #slot} into a chosen deck: a Mythic fits any deck (§5.2); other cards always go in their own skill's. */
	public static Optional<CardRules.Refusal> slot(ServerPlayer player, long id, int index, @org.jspecify.annotations.Nullable ResourceKey<Skill> deckSkill) {
		CharacterState state = ensure(player);
		CardsState cards = CardStore.get(player);
		Optional<CardData> card = cards.card(id);
		if (card.isEmpty()) {
			return Optional.of(CardRules.Refusal.NO_CARD);
		}
		RegistryAccess registries = player.level().registryAccess();
		ResourceKey<Skill> skill = card.get().mythic() && deckSkill != null ? deckSkill : card.get().skill();
		if (card.get().mythic() && deckSkill == null && registries.lookupOrThrow(MettleRegistries.SKILL).getOptional(skill).isEmpty()) {
			return Optional.of(CardRules.Refusal.NO_SLOT);
		}
		List<CardData> deck = cards.deck(skill);
		int level = state.level(skill);
		int wits = wits(registries, state);
		long now = player.level().getServer().overworld().getGameTime();
		Registry<Perk> perks = registries.lookupOrThrow(MettleRegistries.PERK);
		int extra = CardRules.extraSlots(cards.cards());
		Optional<CardRules.Refusal> no = CardRules.canSlot(card.get(), index, deck, level, wits, now, perks::getOptional, cards.slotted(), extra);
		if (no.isPresent()) {
			return no;
		}
		int base = CardRules.slots(level, wits);
		int at = index < 0 ? CardRules.firstFree(deck, base == 0 ? 0 : base + extra) : index;
		ledger(player.level().getServer()).update(id, c -> c.withPlace(CardData.Place.slotted(skill, at)));
		sync(player);
		perks.getOptional(card.get().perk()).ifPresent(p -> SignaturePerks.feedback(player, Mettle.id("cards/" + skill.identifier().getPath()),
			Component.translatable("mettle.cards.slotted", p.name(), skillName(player, skill), at + 1)));
		card.get().mythicId().flatMap(m -> Mythics.def(registries, m)).ifPresent(m -> SignaturePerks.feedback(player,
			Mettle.id("cards/" + skill.identifier().getPath()), Component.translatable("mettle.cards.slotted", m.name(), skillName(player, skill), at + 1)));
		return Optional.empty();
	}

	/** How unslotting these cards would be paid for now, without a rebuild place: a free way, or empty. */
	public static Optional<Way> freeWay(ServerPlayer player, List<CardData> cards) {
		CardsState state = CardStore.get(player);
		if (CharacterStore.get(player).characterLevel() < CardConfig.get().free_rebuild_below_level) {
			return Optional.of(Way.LOW_LEVEL);
		}
		long live = cards.stream().filter(c -> !state.isDormant(c.id())).count();
		if (live == 0) {
			return Optional.of(Way.DORMANT);
		}
		if (!cards.isEmpty() && state.freeRebuilds().contains(cards.getFirst().skill())) {
			return Optional.of(Way.FREE_REBUILD);
		}
		if (state.freeSwaps() >= live) {
			return Optional.of(Way.FREE_SWAP);
		}
		return Optional.empty();
	}

	/**
	 * Takes slotted cards of one deck back to the binder (one sitting, §7). Wave A pays only by the free ways (§7.4);
	 * {@code op} skips the cost (ops, respecs). Refused when no free way covers the sitting.
	 */
	public static Optional<CardRules.Refusal> unslot(ServerPlayer player, List<Long> ids, boolean op) {
		ensure(player);
		CardsState state = CardStore.get(player);
		List<CardData> picked = new ArrayList<>();
		for (long id : new HashSet<>(ids)) {
			Optional<CardData> c = state.card(id);
			if (c.isEmpty()) {
				return Optional.of(CardRules.Refusal.NO_CARD);
			}
			if (!c.get().slotted()) {
				return Optional.of(CardRules.Refusal.NOT_SLOTTED);
			}
			picked.add(c.get());
		}
		if (picked.isEmpty()) {
			return Optional.of(CardRules.Refusal.NO_CARD);
		}
		Set<ResourceKey<Skill>> decks = new HashSet<>();
		picked.forEach(c -> decks.add(c.place().deck().orElse(c.skill())));
		if (decks.size() > 1) {
			return Optional.of(CardRules.Refusal.MIXED_SKILLS);
		}
		Way way = op ? Way.OP : freeWay(player, picked).orElse(null);
		if (way == null) {
			// At a shrine or a trainer, the paid rebuild (draught, fees, scars); elsewhere nothing pays.
			return Rebuilds.sitting(player).isPresent() ? Rebuilds.rebuild(player, ids) : Optional.of(CardRules.Refusal.NO_FREE_WAY);
		}
		take(player, picked, way);
		return Optional.empty();
	}

	/** Moves the cards to the binder and pays the way (swaps or the free rebuild are used up). */
	static void take(ServerPlayer player, List<CardData> picked, Way way) {
		CardsState state = CardStore.get(player);
		CardLedger ledger = ledger(player.level().getServer());
		picked.forEach(c -> ledger.update(c.id(), x -> x.withPlace(CardData.Place.BINDER)));
		long live = picked.stream().filter(c -> !state.isDormant(c.id())).count();
		CardsState next = CardStore.get(player);
		ResourceKey<Skill> skill = picked.getFirst().place().deck().orElse(picked.getFirst().skill());
		if (way == Way.FREE_SWAP) {
			next = next.withFreeSwaps(next.freeSwaps() - (int) live);
		} else if (way == Way.FREE_REBUILD) {
			List<ResourceKey<Skill>> left = new ArrayList<>(next.freeRebuilds());
			left.remove(skill);
			next = next.withFreeRebuilds(left);
		}
		CardStore.set(player, next);
		sync(player);
		if (way != Way.OP) {
			SignaturePerks.feedback(player, Mettle.id("cards/" + skill.identifier().getPath()),
				Component.translatable("mettle.cards.unslotted." + way.name().toLowerCase(java.util.Locale.ROOT), picked.size(), skillName(player, skill),
					CardStore.get(player).freeSwaps()));
		}
		Mettle.LOGGER.info("{} took {} cards out of {} ({})", player.getName().getString(), picked.size(), skill.identifier(), way);
	}

	/** Every slotted card back to the binder, unscarred and free (op respec, background reset, free respec, §7.6). */
	public static void unslotAll(ServerPlayer player) {
		CardsState state = CardStore.get(player);
		CardLedger ledger = ledger(player.level().getServer());
		boolean any = false;
		for (CardData c : state.slotted()) {
			ledger.update(c.id(), x -> x.withPlace(CardData.Place.BINDER));
			any = true;
		}
		if (any) {
			sync(player);
		}
	}

	// ---------------------------------------------------------------- sets

	/** Pins up to three keywords as the active sets (empty: back to the three with most cards). Once an in-game day. */
	public static Optional<CardRules.Refusal> pin(ServerPlayer player, List<Identifier> keywords) {
		ensure(player);
		CardsState state = CardStore.get(player);
		var registry = player.level().registryAccess().lookupOrThrow(MettleRegistries.KEYWORD);
		List<Identifier> distinct = keywords.stream().distinct().toList();
		if (distinct.size() > CardConfig.get().active_sets || distinct.stream().anyMatch(k -> registry.getOptional(k).isEmpty())) {
			return Optional.of(CardRules.Refusal.BAD_PINS);
		}
		long today = day(player.level().getServer());
		if (state.pinDay() >= 0 && today - state.pinDay() < CardConfig.get().set_pin_cooldown_days && !distinct.equals(state.pins())) {
			return Optional.of(CardRules.Refusal.PINS_TODAY);
		}
		CardStore.set(player, state.withPins(distinct, today));
		EffectsApplier.apply(player);
		return Optional.empty();
	}

	// ---------------------------------------------------------------- free swaps

	/** Free swaps earned (a character level), up to the cap. */
	public static void addFreeSwaps(ServerPlayer player, int n) {
		if (n <= 0) {
			return;
		}
		CardsState state = CardStore.get(player);
		int next = Math.min(CardConfig.get().free_swaps_max, state.freeSwaps() + n);
		if (next != state.freeSwaps()) {
			CardStore.set(player, state.withFreeSwaps(next));
		}
	}

	public static void setFreeSwaps(ServerPlayer player, int n) {
		CardStore.set(player, CardStore.get(player).withFreeSwaps(Math.clamp(n, 0, CardConfig.get().free_swaps_max)));
	}

	// ---------------------------------------------------------------- ops and tests

	/** Rolls a card of a perk and gives it (ops and tests); {@code rarity} null rolls it as a draft would. */
	public static CardData give(ServerPlayer player, ResourceKey<Perk> key, Rarity rarity, boolean slotIt) {
		CharacterState state = ensure(player);
		RegistryAccess registries = player.level().registryAccess();
		Perk perk = registries.lookupOrThrow(MettleRegistries.PERK).getValueOrThrow(key);
		Random random = new Random(state.drafts().seed() ^ ledger(player.level().getServer()).nextId() * 0x9E3779B97F4A7C15L);
		Card offer = rarity == null
			? CardRules.roll(key, perk, state.level(perk.skill()), data(registries), random, false)
			: CardRules.roll(key, perk, rarity, data(registries), random, false);
		CardData card = create(player, ledger(player.level().getServer()), offer, state.level(perk.skill()), CardData.How.GIVEN, CardData.Place.BINDER);
		sync(player);
		if (slotIt) {
			slot(player, card.id(), -1).ifPresent(r -> refused(player, r));
		}
		return ledger(player.level().getServer()).get(card.id()).orElse(card);
	}

	/** Gives an exact card (tests): the offer as it is. */
	public static CardData giveExact(ServerPlayer player, Card offer) {
		CharacterState state = ensure(player);
		Perk perk = player.level().registryAccess().lookupOrThrow(MettleRegistries.PERK).getValueOrThrow(offer.perk());
		CardData card = create(player, ledger(player.level().getServer()), offer, state.level(perk.skill()), CardData.How.GIVEN, CardData.Place.BINDER);
		sync(player);
		return card;
	}

	/** Releases every card a player owns (ops and tests: a clean binder). Ids are never reused. */
	public static int clear(ServerPlayer player) {
		CardLedger ledger = ledger(player.level().getServer());
		List<CardData> owned = ledger.owned(player.getUUID());
		owned.forEach(c -> ledger.update(c.id(), x -> x.withPlace(CardData.Place.RELEASED)));
		sync(player);
		return owned.size();
	}

	// ---------------------------------------------------------------- requests from the client

	private static void act(ServerPlayer player, MettlePayloads.CardAction a) {
		Optional<CardRules.Refusal> no = switch (a.action()) {
			case SLOT -> a.cards().isEmpty() ? Optional.of(CardRules.Refusal.NO_CARD) : slot(player, a.cards().getFirst(), a.index(),
				a.keywords().isEmpty() ? null : ResourceKey.create(MettleRegistries.SKILL, a.keywords().getFirst()));
			case UNSLOT -> unslot(player, a.cards(), false);
			case PIN -> pin(player, a.keywords());
			case RELEASE -> Rebuilds.release(player, a.cards());
			case REBUILD -> Rebuilds.rebuild(player, a.cards());
			case FUSE -> Optional.of(CardRules.Refusal.NOT_AT_SHRINE);
			case CLOSE -> {
				Rebuilds.close(player);
				yield Optional.empty();
			}
		};
		no.ifPresent(r -> refused(player, r));
	}

	/** A chip when a trait did something worth saying (Parrying turned a hit, Echoing took an ore), at most every 5 s. */
	static void feedback(ServerPlayer player, String trait) {
		long now = player.level().getGameTime();
		Long last = LAST_TRAIT_CHIP.put(player.getUUID().toString() + trait, now);
		if (last != null && now - last < 100) {
			return;
		}
		Identifier id = Mettle.id(trait);
		player.level().registryAccess().lookupOrThrow(MettleRegistries.TRAIT).getOptional(id).ifPresent(t ->
			SignaturePerks.feedback(player, Mettle.id("trait/" + trait), t.name()));
	}

	private static final Map<String, Long> LAST_TRAIT_CHIP = new HashMap<>();

	/** A chip when a 6-card set fires (Kindled, Bodyguard, Pact, Rally Cry), at most every 5 s. */
	static void setFeedback(ServerPlayer player, String keyword) {
		long now = player.level().getGameTime();
		Long last = LAST_TRAIT_CHIP.put(player.getUUID().toString() + "set/" + keyword, now);
		if (last != null && now - last < 100) {
			return;
		}
		player.level().registryAccess().lookupOrThrow(MettleRegistries.KEYWORD).getOptional(Mettle.id(keyword)).ifPresent(k ->
			SignaturePerks.feedback(player, Mettle.id("set/" + keyword), Component.translatable("mettle.cards.set_fired", k.name())));
	}

	static void refused(ServerPlayer player, CardRules.Refusal refusal) {
		SignaturePerks.feedback(player, Mettle.id("cards/refused"), Component.translatable(refusal.translationKey()));
	}

	static Component skillName(Player player, ResourceKey<Skill> skill) {
		return player.level().registryAccess().lookupOrThrow(MettleRegistries.SKILL).getOptional(skill).map(Skill::name)
			.orElse(Component.literal(skill.identifier().getPath()));
	}
}
