package dev.voidmc.mettle.character.cards;

import dev.voidmc.mettle.api.CardService;
import dev.voidmc.mettle.api.CardView;
import dev.voidmc.mettle.api.MettleEvents;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardStore;
import dev.voidmc.mettle.core.CardsState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.DraftState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The card api (docs/CARDS.md §25): {@link CardService} for other mods, and the one place the card events on
 * {@link MettleEvents} are fired from, so every event carries a view made the same way.
 */
public final class CardApi implements CardService {
	static final CardApi INSTANCE = new CardApi();

	private CardApi() {
	}

	// ---------------------------------------------------------------- reading

	@Override
	public List<CardView> binder(ServerPlayer player) {
		CardsState cards = CardStore.get(player);
		return cards.cards().stream().map(c -> view(player, cards, c)).toList();
	}

	@Override
	public List<CardView> deck(ServerPlayer player, ResourceKey<Skill> skill) {
		CardsState cards = CardStore.get(player);
		return cards.deck(skill).stream().map(c -> view(player, cards, c)).toList();
	}

	@Override
	public List<CardView> hand(ServerPlayer player, ResourceKey<Skill> skill) {
		return offers(player, skill, CharacterStore.get(player).drafts().hand(skill));
	}

	@Override
	public Optional<CardView> card(MinecraftServer server, long id) {
		return Cards.ledger(server).get(id).map(c -> {
			ServerPlayer owner = server.getPlayerList().getPlayer(c.owner());
			boolean dormant = owner != null && CardStore.get(owner).isDormant(id);
			return CardViews.of(c, Cards.printId(server.registryAccess(), c), dormant, server.overworld().getGameTime());
		});
	}

	@Override
	public Map<Identifier, Integer> activeSets(ServerPlayer player) {
		return CardStore.get(player).tiers();
	}

	@Override
	public int setCount(ServerPlayer player, Identifier keyword) {
		return CardStore.get(player).sets().getOrDefault(keyword, 0);
	}

	@Override
	public double trait(ServerPlayer player, Identifier trait) {
		return CardStore.get(player).trait(trait);
	}

	@Override
	public float cooldownScale(ServerPlayer player) {
		return CardHooks.cooldownScale(player);
	}

	@Override
	public List<Identifier> mythics(ServerPlayer player) {
		List<Identifier> out = new ArrayList<>();
		CardStore.get(player).cards().forEach(c -> c.mythicId().ifPresent(out::add));
		return out;
	}

	@Override
	public java.util.Optional<net.minecraft.network.chat.Component> byname(ServerPlayer player) {
		return BuildFeel.byname(player);
	}

	@Override
	public List<Identifier> activeMythics(ServerPlayer player) {
		CardsState cards = CardStore.get(player);
		List<Identifier> out = new ArrayList<>();
		for (CardData c : cards.cards()) {
			if (c.slotted() && cards.activeRank(c.perk()) > 0) {
				c.mythicId().ifPresent(out::add);
			}
		}
		return out;
	}

	// ---------------------------------------------------------------- views

	static CardView view(ServerPlayer player, CardsState cards, CardData c) {
		return CardViews.of(c, Cards.printId(player.level().registryAccess(), c), cards.isDormant(c.id()),
			player.level().getServer().overworld().getGameTime());
	}

	/** A card as it is now in the player's synced cache (or the ledger, when it has left them). */
	static CardView view(ServerPlayer player, long id) {
		CardsState cards = CardStore.get(player);
		Optional<CardData> c = cards.card(id).or(() -> Cards.ledger(player.level().getServer()).get(id));
		return c.map(x -> view(player, cards, x)).orElseThrow();
	}

	static List<CardView> offers(ServerPlayer player, ResourceKey<Skill> skill, List<DraftState.Card> hand) {
		RegistryAccess registries = player.level().registryAccess();
		var perks = registries.lookupOrThrow(MettleRegistries.PERK);
		String name = player.getGameProfile().name();
		return hand.stream().map(o -> CardViews.offer(o, skill, (ResourceKey<Perk> k) -> perks.getOptional(k).map(Perk::skill), name)).toList();
	}

	// ---------------------------------------------------------------- the events

	/** {@code CARD_DRAWN} for every hand {@code after} holds that {@code before} didn't (the draft code calls this). */
	public static void drawn(ServerPlayer player, DraftState before, DraftState after) {
		for (Map.Entry<ResourceKey<Skill>, List<DraftState.Card>> e : after.hands().entrySet()) {
			if (CardViews.drawn(before.hand(e.getKey()), e.getValue())) {
				MettleEvents.CARD_DRAWN.invoker().drawn(player, e.getKey(), offers(player, e.getKey(), e.getValue()));
			}
		}
	}

	static void kept(ServerPlayer player, long id) {
		MettleEvents.CARD_KEPT.invoker().kept(player, view(player, id));
	}

	static void slotted(ServerPlayer player, long id) {
		MettleEvents.CARD_SLOTTED.invoker().slotted(player, view(player, id));
	}

	static void unslotted(ServerPlayer player, List<CardData> picked, boolean paid) {
		for (CardData c : picked) {
			MettleEvents.CARD_UNSLOTTED.invoker().unslotted(player, view(player, c.id()), c.place().deck().orElse(c.skill()), paid);
		}
	}

	static void fused(ServerPlayer player, long made, List<CardData> consumed) {
		List<CardView> gone = consumed.stream().map(c -> view(player, c.id())).toList();
		MettleEvents.CARD_FUSED.invoker().fused(player, view(player, made), gone);
	}

	static void traded(ServerPlayer from, ServerPlayer to, List<Long> ids) {
		for (long id : ids) {
			MettleEvents.CARD_TRADED.invoker().traded(from, to, view(to, id));
		}
	}

	static void mythicClaimed(ServerPlayer player, Identifier mythic, long id) {
		MettleEvents.MYTHIC_CLAIMED.invoker().claimed(player, mythic, view(player, id));
	}

	static void tiers(ServerPlayer player, Map<Identifier, Integer> before, Map<Identifier, Integer> after) {
		for (CardViews.TierChange t : CardViews.tierChanges(before, after)) {
			MettleEvents.SET_TIER_CHANGED.invoker().changed(player, t.keyword(), t.from(), t.to());
		}
	}
}
