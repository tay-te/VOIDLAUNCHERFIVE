package dev.voidmc.mettle.gametest;

import dev.voidmc.mettle.api.CardService;
import dev.voidmc.mettle.api.CardView;
import dev.voidmc.mettle.api.MettleEvents;
import dev.voidmc.mettle.api.MettleServices;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.character.cards.Cards;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardStore;
import dev.voidmc.mettle.core.CardsState;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.Curves;
import dev.voidmc.mettle.core.DraftState;
import dev.voidmc.mettle.core.EffectsApplier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The card api for other mods (docs/CARDS.md §25), in the real game. {@link #listen} registers a listener on every card
 * event before the card journeys run, checking each event's arguments as it fires; {@link #run} then checks that every
 * event fired during those journeys (a level's hand, a keep, slots, a free swap and a rebuild, a fusion, a trade, a
 * Mythic, the Swift set) and reads a fresh deck back through {@link CardService}. The character is put back.
 */
final class CardServiceJourney {
	private static final List<String> EVENTS = List.of("CARD_DRAWN", "CARD_KEPT", "CARD_SLOTTED", "CARD_UNSLOTTED", "CARD_FUSED", "CARD_TRADED",
		"MYTHIC_CLAIMED", "SET_TIER_CHANGED");
	private static final ResourceKey<Skill> ATHLETICS = Progress.skill("athletics");
	private static final Identifier SWIFT = Identifier.fromNamespaceAndPath("mettle", "swift");
	private static final Map<String, Integer> FIRED = new ConcurrentHashMap<>();
	private static final List<String> PROBLEMS = new ArrayList<>();
	private static final List<String> TIERS = new ArrayList<>();
	private static boolean listening;

	private CardServiceJourney() {
	}

	/** Listens to every card event from here on (once per game: Fabric events can't be unregistered). */
	static void listen() {
		if (listening) {
			return;
		}
		listening = true;
		MettleEvents.CARD_DRAWN.register((player, skill, hand) -> {
			fired("CARD_DRAWN");
			expect(!hand.isEmpty() && hand.stream().allMatch(c -> c.offer() && c.id() == 0), "CARD_DRAWN with a hand that isn't offers: " + hand);
		});
		MettleEvents.CARD_KEPT.register((player, card) -> {
			fired("CARD_KEPT");
			expect(card.id() > 0 && !card.offer() && !card.code().isEmpty() && (card.place() == CardView.Place.BINDER || card.slotted()),
				"CARD_KEPT with " + card);
		});
		MettleEvents.CARD_SLOTTED.register((player, card) -> {
			fired("CARD_SLOTTED");
			expect(card.slotted() && card.deck().isPresent() && card.slot() >= 0, "CARD_SLOTTED with a card that isn't slotted: " + card);
		});
		MettleEvents.CARD_UNSLOTTED.register((player, card, deck, paid) -> {
			fired("CARD_UNSLOTTED");
			fired(paid ? "CARD_UNSLOTTED paid" : "CARD_UNSLOTTED free");
			expect(card.place() == CardView.Place.BINDER && deck != null, "CARD_UNSLOTTED with " + card + " from " + deck);
		});
		MettleEvents.CARD_FUSED.register((player, result, consumed) -> {
			fired("CARD_FUSED");
			expect(consumed.size() == 3 && consumed.stream().allMatch(c -> c.place() == CardView.Place.RELEASED)
				&& result.rarity().ordinal() == consumed.getFirst().rarity().ordinal() + 1, "CARD_FUSED " + consumed + " into " + result);
		});
		MettleEvents.CARD_TRADED.register((from, to, card) -> {
			fired("CARD_TRADED");
			expect(card.owner().map(to.getUUID()::equals).orElse(false) && card.attuning() && card.owners() >= 2,
				"CARD_TRADED " + card + " is not " + to.getName().getString() + "'s, attuning");
		});
		MettleEvents.MYTHIC_CLAIMED.register((player, mythic, card) -> {
			fired("MYTHIC_CLAIMED");
			expect(card.isMythic() && card.mythic().map(mythic::equals).orElse(false) && card.owner().map(player.getUUID()::equals).orElse(false),
				"MYTHIC_CLAIMED " + mythic + " with " + card);
		});
		MettleEvents.SET_TIER_CHANGED.register((player, keyword, from, to) -> {
			fired("SET_TIER_CHANGED");
			synchronized (TIERS) {
				TIERS.add(keyword.getPath() + " " + from + ">" + to);
			}
			expect(from != to && (from == 0 || from == 2 || from == 4 || from == 6) && (to == 0 || to == 2 || to == 4 || to == 6),
				"SET_TIER_CHANGED " + keyword + " " + from + " > " + to);
		});
	}

	static void run(TestSingleplayerContext world) {
		var server = world.getServer();
		// Every event fired while the card journeys played, with the arguments it should have.
		check(server.computeOnServer(s -> {
			List<String> missing = new ArrayList<>();
			for (String e : EVENTS) {
				if (FIRED.getOrDefault(e, 0) == 0) {
					missing.add(e);
				}
			}
			for (String e : List.of("CARD_UNSLOTTED paid", "CARD_UNSLOTTED free")) {
				if (FIRED.getOrDefault(e, 0) == 0) {
					missing.add(e);
				}
			}
			synchronized (PROBLEMS) {
				if (!PROBLEMS.isEmpty()) {
					return "card events with wrong arguments: " + PROBLEMS.subList(0, Math.min(5, PROBLEMS.size()));
				}
			}
			return missing.isEmpty() ? null : "card events that never fired in the card journeys: " + missing + " (fired " + FIRED + ")";
		}));

		// A fresh deck read back through the service, and the Swift set's tier moving both ways.
		CharacterState[] saved = new CharacterState[1];
		List<Long> made = new ArrayList<>();
		try {
			check(server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				saved[0] = Cards.ensure(p);
				CharacterStore.set(p, saved[0].withXp(ATHLETICS, Curves.skillXpFor(8)));
				EffectsApplier.apply(p);
				synchronized (TIERS) {
					TIERS.clear();
				}
				int slotted = FIRED.getOrDefault("CARD_SLOTTED", 0);
				for (String perk : new String[] {"athletics/runner", "athletics/steady_pace"}) {
					CardData c = Cards.giveExact(p, new DraftState.Card(Perks.key(Identifier.fromNamespaceAndPath("mettle", perk)), 1, DraftState.Rarity.COMMON,
						false, List.of(SWIFT), List.of(), 6));
					made.add(c.id());
					if (CardStore.get(p).deck(ATHLETICS).stream().noneMatch(x -> x.perk().equals(c.perk()))) {
						var no = Cards.slot(p, c.id(), -1);
						if (no.isPresent()) {
							return "could not slot " + perk + ": " + no.get();
						}
					}
				}
				CardService api = MettleServices.cards();
				CardsState cards = CardStore.get(p);
				if (!(api instanceof dev.voidmc.mettle.character.cards.CardApi)) {
					return "MettleServices.cards() is still the neutral stand-in";
				}
				if (FIRED.getOrDefault("CARD_SLOTTED", 0) <= slotted) {
					return "slotting fired no CARD_SLOTTED";
				}
				if (api.binder(p).size() != cards.cards().size()) {
					return "the service's binder holds " + api.binder(p).size() + " cards, the player " + cards.cards().size();
				}
				List<Long> deck = api.deck(p, ATHLETICS).stream().map(CardView::id).toList();
				List<Long> want = cards.deck(ATHLETICS).stream().map(CardData::id).toList();
				if (!deck.equals(want)) {
					return "the service's Athletics deck " + deck + ", the player's " + want;
				}
				for (long id : made) {
					var v = api.card(s, id);
					if (v.isEmpty() || !v.get().code().equals(Cards.printId(p.level().registryAccess(), cards.card(id).orElseThrow()))) {
						return "card " + id + " through the service: " + v;
					}
				}
				if (!api.activeSets(p).equals(cards.tiers()) || !api.setActive(p, SWIFT, 2) || api.setCount(p, SWIFT) < 2) {
					return "Swift through the service: sets " + api.activeSets(p) + ", count " + api.setCount(p, SWIFT);
				}
				if (Math.abs(api.cooldownScale(p) - dev.voidmc.mettle.character.skill.defense.Cooldowns.scale(p)) > 1e-6) {
					return "cooldown scale " + api.cooldownScale(p) + " against " + dev.voidmc.mettle.character.skill.defense.Cooldowns.scale(p);
				}
				if (!api.hand(p, ATHLETICS).stream().allMatch(CardView::offer)) {
					return "a hand through the service holds something that isn't an offer";
				}
				synchronized (TIERS) {
					if (TIERS.stream().noneMatch(t -> t.startsWith("swift 0>"))) {
						return "Swift becoming active fired no SET_TIER_CHANGED: " + TIERS;
					}
				}
				return null;
			}));
			check(server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				int unslotted = FIRED.getOrDefault("CARD_UNSLOTTED", 0);
				List<Long> slotted = made.stream().filter(id -> CardStore.get(p).card(id).map(CardData::slotted).orElse(false)).toList();
				Cards.unslot(p, slotted, true).ifPresent(r -> PROBLEMS.add("op unslot refused: " + r));
				if (FIRED.getOrDefault("CARD_UNSLOTTED", 0) < unslotted + slotted.size()) {
					return "unslotting " + slotted.size() + " cards fired " + (FIRED.getOrDefault("CARD_UNSLOTTED", 0) - unslotted) + " CARD_UNSLOTTED";
				}
				synchronized (TIERS) {
					return MettleServices.cards().setActive(p, SWIFT, 2) || TIERS.stream().noneMatch(t -> t.startsWith("swift ") && t.endsWith(">0"))
						? "Swift going away: active " + MettleServices.cards().activeSets(p) + ", events " + TIERS : null;
				}
			}));
		} finally {
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				made.forEach(id -> Cards.ledger(s).update(id, x -> x.withPlace(CardData.Place.RELEASED)));
				if (saved[0] != null) {
					CharacterStore.set(p, saved[0]);
				}
				Cards.sync(p);
			});
		}
	}

	private static void fired(String event) {
		FIRED.merge(event, 1, Integer::sum);
	}

	private static void expect(boolean ok, String problem) {
		if (!ok) {
			synchronized (PROBLEMS) {
				PROBLEMS.add(problem);
			}
		}
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static void check(String problem) {
		if (problem != null) {
			throw new AssertionError(problem);
		}
	}
}
