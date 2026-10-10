package dev.voidmc.mettle.character.draft;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.Background;
import dev.voidmc.mettle.api.MettleEvents;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.moment.Moments;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.character.cards.CardRules;
import dev.voidmc.mettle.character.cards.Cards;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardStore;
import dev.voidmc.mettle.core.CardsState;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.DraftState;
import dev.voidmc.mettle.core.DraftState.Card;
import dev.voidmc.mettle.core.EffectsApplier;
import dev.voidmc.mettle.core.net.MettlePayloads;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The perk draft on the server (docs/DRAFT.md): every skill level queues a draft, a hand of up to three cards is dealt
 * for it from the player's seed, the player keeps one card or spends a reroll on a new hand. Server thread; the client
 * only asks ({@link MettlePayloads.PickCard}, {@link MettlePayloads.Reroll}) and reads the synced record.
 */
public final class Drafts {
	/** Why a pick or a reroll was refused (lang {@code mettle.draft.refused.<name>}). */
	public enum Refusal {
		/** No draft waiting in that skill. */
		NO_DRAFT,
		/** No card at that place in the hand. */
		NO_CARD,
		/** The card stopped being good (learned another way, a Mastery taken): a fresh hand was dealt, free. */
		STALE,
		NO_REROLLS,
		/** The binder holds 300 cards: the draft waits (docs/CARDS.md §7.5). */
		BINDER_FULL;

		public String translationKey() {
			return "mettle.draft.refused." + name().toLowerCase(Locale.ROOT);
		}
	}

	private Drafts() {
	}

	public static void init() {
		// Migrate old saves (unspent points become drafts, drafted perks cards) and deal any hand that is due.
		ServerPlayerEvents.JOIN.register(Drafts::refresh);
		ServerPlayNetworking.registerGlobalReceiver(MettlePayloads.KeepCard.TYPE, (payload, context) ->
			keep(context.player(), payload.skill(), payload.index(), payload.slot() ? Cards.Keep.SLOT : Cards.Keep.BINDER)
				.ifPresent(r -> refused(context.player(), payload.skill(), r)));
		ServerPlayNetworking.registerGlobalReceiver(MettlePayloads.PickCard.TYPE, (payload, context) ->
			pick(context.player(), payload.skill(), payload.index()).ifPresent(r -> refused(context.player(), payload.skill(), r)));
		ServerPlayNetworking.registerGlobalReceiver(MettlePayloads.Reroll.TYPE, (payload, context) ->
			reroll(context.player(), payload.skill()).ifPresent(r -> refused(context.player(), payload.skill(), r)));
	}

	// ---------------------------------------------------------------- reading (both sides)

	public static DraftRules.Deck deck(RegistryAccess registries) {
		Map<ResourceKey<Perk>, Perk> all = new HashMap<>();
		registries.lookupOrThrow(MettleRegistries.PERK).listElements().forEach(r -> all.put(r.key(), r.value()));
		return new DraftRules.Deck(all);
	}

	/** The character as {@link DraftRules} reads it: skill levels, governing attributes and ranks from {@code state}. */
	public static DraftRules.Sheet sheet(RegistryAccess registries, CharacterState state) {
		Registry<Skill> skills = registries.lookupOrThrow(MettleRegistries.SKILL);
		Background background = CharacterStore.background(registries, state);
		return new DraftRules.Sheet() {
			@Override
			public int level(ResourceKey<Skill> skill) {
				return state.level(skill);
			}

			@Override
			public int attribute(ResourceKey<Skill> skill) {
				return skills.getOptional(skill).map(s -> state.attribute(s.attribute(), background)).orElse(0);
			}

			@Override
			public int rank(ResourceKey<Perk> perk) {
				return state.rank(perk);
			}
		};
	}

	/** Drafts waiting in a skill, from the synced record (0 for a character not yet migrated). */
	public static int queued(Player player, ResourceKey<Skill> skill) {
		return CharacterStore.get(player).drafts().queued(skill);
	}

	/** Drafts waiting in every released skill. */
	public static int queuedReleased(Player player) {
		DraftState d = CharacterStore.get(player).drafts();
		Registry<Skill> skills = player.level().registryAccess().lookupOrThrow(MettleRegistries.SKILL);
		int n = 0;
		for (Holder.Reference<Skill> ref : skills.listElements().toList()) {
			if (ref.value().released()) {
				n += d.queued(ref.key());
			}
		}
		return n;
	}

	// ---------------------------------------------------------------- the record

	/** The player's character with drafts, migrating an old save first (unspent perk points become drafts). */
	public static CharacterState ensure(ServerPlayer player) {
		CharacterState state = CharacterStore.get(player);
		if (state.draft().isPresent()) {
			return state;
		}
		RegistryAccess registries = player.level().registryAccess();
		DraftRules.Deck deck = deck(registries);
		Map<ResourceKey<Skill>, Integer> levels = new HashMap<>();
		List<ResourceKey<Skill>> skills = registries.lookupOrThrow(MettleRegistries.SKILL).listElements().map(Holder.Reference::key).toList();
		skills.forEach(s -> levels.put(s, state.level(s)));
		DraftState drafts = DraftRules.migrate(DraftRules.seed(player.getUUID()), skills, levels, state.perks(), deck);
		if (drafts.totalQueued() > 0) {
			Mettle.LOGGER.info("{}: unspent perk points became drafts {}", player.getName().getString(), drafts.queued());
		}
		CharacterState next = state.withDraft(drafts);
		CharacterStore.set(player, next);
		return next;
	}

	/** Deals every hand that is due and re-deals hands holding a card that went bad (or a v1 hand from before cards). */
	public static void refresh(ServerPlayer player) {
		CharacterState state = dev.voidmc.mettle.character.cards.Cards.ensure(player);
		Settled settled = settle(player.level().registryAccess(), state, CardStore.get(player),
			id -> dev.voidmc.mettle.character.cards.Mythics.drawable(player.level().getServer(), id));
		if (!settled.state().equals(state)) {
			CharacterStore.set(player, settled.state());
		}
	}

	/**
	 * What {@link #refresh} does, on a state rather than a player.
	 *
	 * @param spent v1 only (a spent deck's drafts became rerolls); cards v2 never spends a deck, so it is always empty
	 */
	public record Settled(CharacterState state, Map<ResourceKey<Skill>, Integer> spent) {
	}

	public static Settled settle(RegistryAccess registries, CharacterState state, CardsState cards) {
		return settle(registries, state, cards, id -> false);
	}

	/** {@link #settle}, with whether each drawn Mythic still waits in the world (the server's ledger knows). */
	public static Settled settle(RegistryAccess registries, CharacterState state, CardsState cards,
		java.util.function.Predicate<net.minecraft.resources.Identifier> mythicWaits) {
		DraftState d = state.drafts();
		DraftRules.Deck deck = deck(registries);
		CardRules.Sheet sheet = Cards.sheet(registries, state, cards);
		CardRules.Data data = Cards.data(registries);
		Set<ResourceKey<Skill>> skills = new HashSet<>(d.queued().keySet());
		skills.addAll(d.hands().keySet());
		List<ResourceKey<Skill>> order = skills.stream().sorted(Comparator.comparing(k -> k.identifier().toString())).toList();
		for (ResourceKey<Skill> skill : order) {
			List<Card> hand = d.hand(skill);
			if (d.queued(skill) <= 0) {
				d = d.withHand(skill, List.of());
				continue;
			}
			if (!hand.isEmpty() && hand.stream().allMatch(c -> CardRules.valid(c, sheet, deck))) {
				continue;
			}
			List<Card> cardsDealt = new ArrayList<>(CardRules.deal(skill, sheet, deck, data, DraftRules.random(d.seed(), d.dealt()), d.recent(), Set.of()));
			// The Mythic roll (CARDS.md §3.5): once per draft, from skill 15, while the skill's drawn Mythic waits.
			Optional<net.minecraft.resources.Identifier> drawn = dev.voidmc.mettle.character.cards.Mythics.drawnOf(registries, skill);
			if (!cardsDealt.isEmpty() && drawn.isPresent() && mythicWaits.test(drawn.get())
				&& dev.voidmc.mettle.character.cards.Mythics.rolls(d.seed(), d.dealt(), sheet.level(skill))) {
				if (cardsDealt.size() >= DraftRules.HAND) {
					cardsDealt.set(DraftRules.HAND - 1, dev.voidmc.mettle.character.cards.Mythics.offer(drawn.get()));
				} else {
					cardsDealt.add(dev.voidmc.mettle.character.cards.Mythics.offer(drawn.get()));
				}
			}
			d = cardsDealt.isEmpty() ? d.withHand(skill, List.of()) : d.nextDeal().withHand(skill, cardsDealt);
		}
		return new Settled(state.withDraft(d), Map.of());
	}

	// ---------------------------------------------------------------- what changes it

	/**
	 * A skill went from level {@code before} to {@code after}: a draft for each level (and a reroll for a level that deals
	 * none, when {@link DraftRules#HALF_RATE_FROM} is on), and its hand is dealt if none is.
	 */
	public static void levelUp(ServerPlayer player, ResourceKey<Skill> skill, int before, int after) {
		if (after <= before) {
			return;
		}
		CharacterState state = ensure(player);
		int drafts = DraftRules.draftsFor(before, after);
		DraftState d = state.drafts().addQueued(skill, drafts);
		d = d.withRerolls(DraftRules.addRerolls(d.rerolls(), after - before - drafts));
		CharacterStore.set(player, state.withDraft(d));
		refresh(player);
	}

	/** Grants rerolls (a character level, a trainer's lesson, a feat), up to the cap. */
	public static void addRerolls(ServerPlayer player, int count) {
		if (count <= 0) {
			return;
		}
		CharacterState state = ensure(player);
		DraftState d = state.drafts();
		CharacterStore.set(player, state.withDraft(d.withRerolls(DraftRules.addRerolls(d.rerolls(), count))));
	}

	/** {@code state} with a skill moved to the front of the recently used ones (wildcards come from those). */
	public static CharacterState used(CharacterState state, ResourceKey<Skill> skill) {
		return state.draft().map(d -> state.withDraft(d.used(skill))).orElse(state);
	}

	/** {@code state} with refunded drafted ranks back as drafts of their skills (a respec); call {@link #refresh} after. */
	public static CharacterState returnDrafts(RegistryAccess registries, CharacterState state, Map<ResourceKey<Perk>, Integer> removed) {
		DraftState d = state.drafts();
		for (Map.Entry<ResourceKey<Skill>, Integer> e : DraftRules.returned(removed, deck(registries)).entrySet()) {
			d = d.addQueued(e.getKey(), e.getValue());
		}
		return state.withDraft(d);
	}

	/**
	 * Keeps the {@code index}-th card of a skill's hand (the old pick): it becomes a card, slotted when a legal slot is
	 * free, else in the binder (docs/CARDS.md §5.2); the next draft (if any) is dealt.
	 */
	public static Optional<Refusal> pick(ServerPlayer player, ResourceKey<Skill> skill, int index) {
		return keep(player, skill, index, Cards.Keep.AUTO);
	}

	/** Keeps a card of a skill's hand into the binder, a slot, or a slot when one is free (§5.2 "Keep" / "Keep &amp; slot"). */
	public static Optional<Refusal> keep(ServerPlayer player, ResourceKey<Skill> skill, int index, Cards.Keep how) {
		CharacterState state = Cards.ensure(player);
		DraftState d = state.drafts();
		List<Card> hand = d.hand(skill);
		if (d.queued(skill) <= 0 || hand.isEmpty()) {
			return Optional.of(Refusal.NO_DRAFT);
		}
		if (index < 0 || index >= hand.size()) {
			return Optional.of(Refusal.NO_CARD);
		}
		if (!Cards.binderRoom(player)) {
			return Optional.of(Refusal.BINDER_FULL);
		}
		RegistryAccess registries = player.level().registryAccess();
		DraftRules.Deck deck = deck(registries);
		Card card = hand.get(index);
		if (card.rarity() == DraftState.Rarity.MYTHIC) {
			// A drawn Mythic: claimed in the world the moment it is kept (CARDS.md §9.3).
			net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.fromNamespaceAndPath(card.perk().identifier().getNamespace(),
				card.perk().identifier().getPath().substring("mythic/".length()));
			if (!dev.voidmc.mettle.character.cards.Mythics.drawable(player.level().getServer(), id)) {
				CharacterStore.set(player, state.withDraft(d.withHand(skill, List.of())));
				refresh(player);
				return Optional.of(Refusal.STALE);
			}
			CharacterStore.set(player, state.withDraft(d.addQueued(skill, -1).withHand(skill, List.of())));
			dev.voidmc.mettle.character.cards.Mythics.grant(player, id, false);
			refresh(player);
			return Optional.empty();
		}
		if (!CardRules.valid(card, Cards.sheet(registries, state, CardStore.get(player)), deck)) {
			refresh(player);
			return Optional.of(Refusal.STALE);
		}
		Perk perk = deck.get(card.perk()).orElseThrow();
		CharacterStore.set(player, state.withDraft(d.addQueued(skill, -1).withHand(skill, List.of())));
		CardData kept = Cards.keep(player, card, how);
		refresh(player);
		Moments.perk(player, card.perk(), perk, card.rank());
		Component text = Component.translatable(kept.slotted() ? "mettle.cards.kept_slotted" : "mettle.cards.kept", perk.name(),
			Component.translatable("mettle.draft.rarity." + card.rarity().getSerializedName()), Cards.printId(registries, kept));
		SignaturePerks.feedback(player, card.perk().identifier(), text);
		Mettle.LOGGER.info("{} kept {} {} ({}{}) for {} as card {}{}", player.getName().getString(), card.perk().identifier(), card.rank(),
			card.rarity().getSerializedName(), card.wildcard() ? ", wildcard" : "", skill.identifier(), kept.id(), kept.slotted() ? ", slotted" : "");
		return Optional.empty();
	}

	/** Spends a reroll: the skill's hand is dealt again, avoiding the cards it held while others are left. */
	public static Optional<Refusal> reroll(ServerPlayer player, ResourceKey<Skill> skill) {
		CharacterState state = Cards.ensure(player);
		DraftState d = state.drafts();
		List<Card> hand = d.hand(skill);
		if (d.queued(skill) <= 0 || hand.isEmpty()) {
			return Optional.of(Refusal.NO_DRAFT);
		}
		if (d.rerolls() <= 0) {
			return Optional.of(Refusal.NO_REROLLS);
		}
		RegistryAccess registries = player.level().registryAccess();
		Set<ResourceKey<Perk>> avoid = new HashSet<>();
		hand.forEach(c -> avoid.add(c.perk()));
		List<Card> cards = CardRules.deal(skill, Cards.sheet(registries, state, CardStore.get(player)), deck(registries), Cards.data(registries),
			DraftRules.random(d.seed(), d.dealt()), d.recent(), avoid);
		if (cards.isEmpty()) {
			refresh(player);
			return Optional.of(Refusal.STALE);
		}
		// A Mythic offer survives rerolls: the other two slots are dealt again (CARDS.md §3.5).
		Optional<Card> mythic = hand.stream().filter(c -> c.rarity() == DraftState.Rarity.MYTHIC).findFirst();
		if (mythic.isPresent()) {
			List<Card> kept = new ArrayList<>(cards.subList(0, Math.min(cards.size(), DraftRules.HAND - 1)));
			kept.add(mythic.get());
			cards = kept;
		}
		DraftState next = d.withRerolls(d.rerolls() - 1).nextDeal().withHand(skill, cards);
		CharacterStore.set(player, state.withDraft(next));
		SignaturePerks.feedback(player, Mettle.id("draft/" + skill.identifier().getPath()),
			Component.translatable(next.rerolls() == 1 ? "mettle.draft.rerolled.one" : "mettle.draft.rerolled", skillName(player, skill), next.rerolls()));
		return Optional.empty();
	}

	private static void refused(ServerPlayer player, ResourceKey<Skill> skill, Refusal refusal) {
		SignaturePerks.feedback(player, Mettle.id("draft/" + skill.identifier().getPath()), Component.translatable(refusal.translationKey()));
	}

	private static Component skillName(Player player, ResourceKey<Skill> skill) {
		return player.level().registryAccess().lookupOrThrow(MettleRegistries.SKILL).getOptional(skill).map(Skill::name)
			.orElse(Component.literal(skill.identifier().getPath()));
	}

	/** The cards of a hand as perk ids, for commands and logs. */
	public static List<String> describe(List<Card> hand) {
		List<String> out = new ArrayList<>();
		for (Card c : hand) {
			out.add(c.perk().identifier() + " " + c.rank() + " " + c.rarity().getSerializedName() + (c.wildcard() ? " wild" : ""));
		}
		return out;
	}

	/** Gives drafts directly (ops and tests): {@code count} more in a skill. */
	public static void give(ServerPlayer player, ResourceKey<Skill> skill, int count) {
		CharacterState state = ensure(player);
		CharacterStore.set(player, state.withDraft(state.drafts().addQueued(skill, count)));
		refresh(player);
	}

	/** Sets the rerolls directly (ops and tests). */
	public static void setRerolls(ServerPlayer player, int count) {
		CharacterState state = ensure(player);
		CharacterStore.set(player, state.withDraft(state.drafts().withRerolls(Math.clamp(count, 0, DraftRules.MAX_REROLLS))));
	}

	/** Perks drafted (not earned) a player holds in a skill, counting ranks: every one was a card. */
	public static int draftedRanks(Player player, ResourceKey<Skill> skill) {
		CharacterState state = CharacterStore.get(player);
		Registry<Perk> perks = Perks.registry(player);
		int n = 0;
		for (Map.Entry<ResourceKey<Perk>, Integer> e : state.perks().entrySet()) {
			Optional<Perk> p = perks.getOptional(e.getKey());
			if (p.isPresent() && p.get().kind().drafted() && p.get().skill().equals(skill)) {
				n += e.getValue();
			}
		}
		return n;
	}
}
