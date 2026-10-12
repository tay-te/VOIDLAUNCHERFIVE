package dev.voidmc.mettle.character.cards;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.draft.Drafts;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardStore;
import dev.voidmc.mettle.core.CardsState;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.DraftState;
import dev.voidmc.mettle.core.DraftState.Card;
import dev.voidmc.mettle.core.DraftState.Rarity;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

/**
 * Cards from chat (docs/CARDS.md §16 wave A), until the Hub draws them: {@code /mettle cards} lists the binder and decks,
 * {@code show <id>}, {@code slot <id> [slot]}, {@code unslot <id>} (a free way; ops: always), {@code pin [keywords…]}.
 * Ops: {@code give <perk> [rarity] [players]}, {@code roll <skill> [n]} (offers a hand would deal, nothing kept),
 * {@code swaps <n> [players]}, {@code clear [players]}. A card is named by its number or its printed id.
 */
public final class CardCommands {
	private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(id -> Component.translatable("mettle.command.unknown", String.valueOf(id)));
	private static final DynamicCommandExceptionType NO_CARD = new DynamicCommandExceptionType(id -> Component.translatable("mettle.cards.no_such", String.valueOf(id)));

	private CardCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("mettle")
			.then(Commands.literal("trade")
				.executes(c -> 1)
				.then(Commands.literal("accept").executes(c -> trade(c, Trades.accept(c.getSource().getPlayerOrException()))))
				.then(Commands.literal("cancel").executes(c -> trade(c, Trades.cancel(c.getSource().getPlayerOrException()))))
				.then(Commands.literal("lock").executes(c -> trade(c, Trades.lock(c.getSource().getPlayerOrException()))))
				.then(Commands.literal("confirm").executes(c -> trade(c, Trades.confirm(c.getSource().getPlayerOrException()))))
				.then(Commands.literal("card").then(Commands.argument("card", StringArgumentType.word()).executes(c ->
					trade(c, Trades.offerCard(c.getSource().getPlayerOrException(), find(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "card")).id())))))
				.then(Commands.literal("item").executes(c -> trade(c, Trades.offerItem(c.getSource().getPlayerOrException(), -1))))
				.then(Commands.literal("coin").then(Commands.argument("bits", com.mojang.brigadier.arguments.LongArgumentType.longArg(0)).executes(c ->
					trade(c, Trades.setCoin(c.getSource().getPlayerOrException(), com.mojang.brigadier.arguments.LongArgumentType.getLong(c, "bits"))))))
				.then(Commands.argument("player", EntityArgument.player()).executes(c ->
					trade(c, Trades.request(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "player"))))))
			.then(Commands.literal("cards")
				.executes(c -> list(c, c.getSource().getPlayerOrException()))
				.then(Commands.literal("show")
					.then(Commands.argument("card", StringArgumentType.word()).executes(CardCommands::show)))
				.then(Commands.literal("slot")
					.then(Commands.argument("card", StringArgumentType.word())
						.executes(c -> slot(c, -1))
						.then(Commands.argument("slot", IntegerArgumentType.integer(1, 12)).executes(c -> slot(c, IntegerArgumentType.getInteger(c, "slot") - 1)))))
				.then(Commands.literal("unslot")
					.then(Commands.argument("card", StringArgumentType.word()).executes(CardCommands::unslot)))
				.then(Commands.literal("rebuild")
					.then(Commands.argument("cards", StringArgumentType.greedyString()).executes(CardCommands::rebuild)))
				.then(Commands.literal("release")
					.then(Commands.argument("cards", StringArgumentType.greedyString()).executes(CardCommands::release)))
				.then(Commands.literal("fuse")
					.then(Commands.argument("cards", StringArgumentType.greedyString()).executes(c -> {
						ServerPlayer p = c.getSource().getPlayerOrException();
						return trade2(c, Rebuilds.fuse(p, cards(p, StringArgumentType.getString(c, "cards"))));
					})))
				.then(Commands.literal("close").executes(c -> {
					Rebuilds.close(c.getSource().getPlayerOrException());
					return 1;
				}))
				.then(Commands.literal("pin")
					.executes(c -> pin(c, ""))
					.then(Commands.argument("keywords", StringArgumentType.greedyString()).executes(c -> pin(c, StringArgumentType.getString(c, "keywords")))))
				.then(Commands.literal("give")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.argument("perk", ResourceKeyArgument.key(MettleRegistries.PERK))
						.executes(c -> give(c, null, List.of(c.getSource().getPlayerOrException())))
						.then(Commands.argument("rarity", StringArgumentType.word())
							.executes(c -> give(c, rarity(c), List.of(c.getSource().getPlayerOrException())))
							.then(Commands.argument("players", EntityArgument.players())
								.executes(c -> give(c, rarity(c), EntityArgument.getPlayers(c, "players")))))))
				.then(Commands.literal("roll")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.argument("skill", ResourceKeyArgument.key(MettleRegistries.SKILL))
						.executes(c -> roll(c, 1))
						.then(Commands.argument("hands", IntegerArgumentType.integer(1, 10)).executes(c -> roll(c, IntegerArgumentType.getInteger(c, "hands"))))))
				.then(Commands.literal("slotin")
					.then(Commands.argument("card", StringArgumentType.word())
						.then(Commands.argument("deck", ResourceKeyArgument.key(MettleRegistries.SKILL)).executes(c -> {
							ServerPlayer p = c.getSource().getPlayerOrException();
							return trade2(c, Cards.slot(p, find(p, StringArgumentType.getString(c, "card")).id(), -1,
								ResourceKeyArgument.getRegistryKey(c, "deck", MettleRegistries.SKILL, UNKNOWN)));
						}))))
				.then(Commands.literal("mythic")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.literal("grant").then(Commands.argument("mythic", ResourceKeyArgument.key(MettleRegistries.MYTHIC))
						.executes(c -> mythicGrant(c, List.of(c.getSource().getPlayerOrException())))
						.then(Commands.argument("players", EntityArgument.players()).executes(c -> mythicGrant(c, EntityArgument.getPlayers(c, "players"))))))
					.then(Commands.literal("move").then(Commands.argument("card", com.mojang.brigadier.arguments.LongArgumentType.longArg(1))
						.then(Commands.argument("player", EntityArgument.player()).executes(CardCommands::mythicMove))))
					.then(Commands.literal("reset").then(Commands.argument("mythic", ResourceKeyArgument.key(MettleRegistries.MYTHIC)).executes(CardCommands::mythicReset))))
				.then(Commands.literal("swaps")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.argument("count", IntegerArgumentType.integer(0, 3))
						.executes(c -> swaps(c, List.of(c.getSource().getPlayerOrException())))
						.then(Commands.argument("players", EntityArgument.players()).executes(c -> swaps(c, EntityArgument.getPlayers(c, "players"))))))
				.then(Commands.literal("clear")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.executes(c -> clear(c, List.of(c.getSource().getPlayerOrException())))
					.then(Commands.argument("players", EntityArgument.players()).executes(c -> clear(c, EntityArgument.getPlayers(c, "players")))))));
	}

	private static int trade2(CommandContext<CommandSourceStack> c, Optional<CardRules.Refusal> why) {
		if (why.isPresent()) {
			c.getSource().sendFailure(Component.translatable(why.get().translationKey()));
			return 0;
		}
		return 1;
	}

	private static int mythicGrant(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) throws CommandSyntaxException {
		var key = ResourceKeyArgument.getRegistryKey(c, "mythic", MettleRegistries.MYTHIC, UNKNOWN);
		int n = 0;
		for (ServerPlayer p : players) {
			if (Mythics.grant(p, key.identifier(), true).isPresent()) {
				n++;
				dev.voidmc.mettle.Mettle.LOGGER.info("op {} granted the Mythic {} to {}", c.getSource().getTextName(), key.identifier(), p.getName().getString());
			}
		}
		int granted = n;
		c.getSource().sendSuccess(() -> Component.translatable("mettle.mythic.granted", key.identifier().toString(), granted), true);
		return n;
	}

	/** Moves a Mythic card to another player (an op's fix): owner, history and the Hall of Firsts follow. */
	private static int mythicMove(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		long id = com.mojang.brigadier.arguments.LongArgumentType.getLong(c, "card");
		ServerPlayer to = EntityArgument.getPlayer(c, "player");
		var server = c.getSource().getServer();
		var ledger = Cards.ledger(server);
		var card = ledger.get(id).filter(CardData::mythic);
		if (card.isEmpty()) {
			throw NO_CARD.create(String.valueOf(id));
		}
		UUID from = card.get().owner();
		ledger.update(id, x -> TradeRules.received(x, to.getUUID(), to.getGameProfile().name(), Cards.day(server), 0));
		card.get().mythicId().ifPresent(m -> ledger.first(m.getPath()).ifPresent(h -> ledger.claim(m.getPath(),
			new CardData.Holder(to.getUUID(), to.getGameProfile().name(), h.day()))));
		ServerPlayer old = server.getPlayerList().getPlayer(from);
		if (old != null) {
			Cards.sync(old);
		}
		Cards.sync(to);
		dev.voidmc.mettle.Mettle.LOGGER.info("op {} moved Mythic card {} to {}", c.getSource().getTextName(), id, to.getName().getString());
		c.getSource().sendSuccess(() -> Component.translatable("mettle.mythic.moved", id, to.getName()), true);
		return 1;
	}

	/** Resets a Mythic: its cards leave the world and the first (or draw) can be earned again. */
	private static int mythicReset(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		var key = ResourceKeyArgument.getRegistryKey(c, "mythic", MettleRegistries.MYTHIC, UNKNOWN);
		var server = c.getSource().getServer();
		var ledger = Cards.ledger(server);
		ledger.unclaim(key.identifier().getPath());
		int n = 0;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			for (CardData card : ledger.owned(p.getUUID())) {
				if (card.mythicId().map(key.identifier()::equals).orElse(false)) {
					ledger.update(card.id(), x -> x.withPlace(CardData.Place.RELEASED));
					n++;
				}
			}
			Cards.sync(p);
		}
		int gone = n;
		dev.voidmc.mettle.Mettle.LOGGER.info("op {} reset the Mythic {} ({} cards released)", c.getSource().getTextName(), key.identifier(), gone);
		c.getSource().sendSuccess(() -> Component.translatable("mettle.mythic.reset", key.identifier().toString(), gone), true);
		return 1;
	}

	private static int trade(CommandContext<CommandSourceStack> c, Optional<TradeRules.Refusal> why) {
		if (why.isPresent()) {
			c.getSource().sendFailure(Component.translatable(why.get().translationKey()));
			return 0;
		}
		return 1;
	}

	/** A card of the player's by number ({@code 12}) or printed id ({@code MN-7Q4K-2F}). */
	static CardData find(ServerPlayer player, String text) throws CommandSyntaxException {
		CardsState state = CardStore.get(player);
		String t = text.trim().toUpperCase(Locale.ROOT);
		for (CardData c : state.cards()) {
			if (String.valueOf(c.id()).equals(t) || Cards.printId(player.level().registryAccess(), c).equals(t)) {
				return c;
			}
		}
		throw NO_CARD.create(text);
	}

	private static Rarity rarity(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		String r = StringArgumentType.getString(c, "rarity");
		for (Rarity x : Rarity.values()) {
			if (x.getSerializedName().equalsIgnoreCase(r)) {
				return x;
			}
		}
		throw UNKNOWN.create(r);
	}

	/** One chat line for a card: id, rarity-coloured name, keywords, traits, where it is. Click shows it. */
	static MutableComponent line(ServerPlayer player, CardData card) {
		RegistryAccess registries = player.level().registryAccess();
		CardsState state = CardStore.get(player);
		Optional<Perk> perk = CardText.perk(registries, card.perk());
		MutableComponent out = Component.literal(Cards.printId(registries, card) + " ").withStyle(ChatFormatting.DARK_GRAY);
		if (card.mythic()) {
			var m = card.mythicId().flatMap(id -> Mythics.def(registries, id));
			out.append(m.map(x -> x.name().copy()).orElse(Component.literal("?")).withStyle(s -> s.withColor(TextColor.fromRgb(card.rarity().colour)).withBold(true)));
			m.ifPresent(x -> out.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY)).append(x.effect().copy().withStyle(ChatFormatting.GRAY)));
			out.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY)).append((card.slotted()
				? Component.translatable("mettle.cards.where.slotted", card.place().index() + 1) : Component.translatable("mettle.cards.where.mythic"))
				.copy().withStyle(card.slotted() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
			return out;
		}
		out.append(perk.map(p -> CardText.name(p, card.rank())).orElse(Component.literal(card.perk().identifier().toString()))
			.withStyle(s -> s.withColor(TextColor.fromRgb(card.rarity().colour)).withBold(true)));
		out.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY)).append(CardText.keywords(registries, card.keywords()).withStyle(ChatFormatting.GRAY));
		if (!card.traits().isEmpty()) {
			out.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY)).append(CardText.traits(registries, card.traits()).withStyle(ChatFormatting.WHITE));
		}
		Component where = card.slotted()
			? Component.translatable(state.isDormant(card.id()) ? "mettle.cards.where.dormant" : "mettle.cards.where.slotted", card.place().index() + 1)
			: Component.translatable("mettle.cards.where.binder", card.needs());
		out.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY)).append(where.copy().withStyle(card.slotted() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
		String cmd = card.slotted() ? "/mettle cards show " + card.id() : "/mettle cards slot " + card.id();
		return out.withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand(cmd))
			.withHoverEvent(new HoverEvent.ShowText(Component.translatable(card.slotted() ? "mettle.cards.click_show" : "mettle.cards.click_slot"))));
	}

	private static int list(CommandContext<CommandSourceStack> c, ServerPlayer player) {
		Cards.ensure(player);
		CardsState state = CardStore.get(player);
		CharacterState character = CharacterStore.get(player);
		RegistryAccess registries = player.level().registryAccess();
		c.getSource().sendSystemMessage(Component.translatable("mettle.cards.header", state.cards().size(), CardConfig.get().binder_max, state.freeSwaps(),
			CardConfig.get().free_swaps_max).withStyle(ChatFormatting.BOLD));
		var skills = registries.lookupOrThrow(MettleRegistries.SKILL);
		skills.listElements().sorted(java.util.Comparator.comparingInt(r -> r.value().order())).forEach(ref -> {
			List<CardData> deck = state.deck(ref.key());
			int slots = CardRules.slots(character.level(ref.key()), Cards.wits(registries, character));
			if (slots == 0 && deck.isEmpty()) {
				return;
			}
			c.getSource().sendSystemMessage(Component.translatable("mettle.cards.deck", ref.value().name(), deck.size(), slots));
			deck.forEach(card -> c.getSource().sendSystemMessage(Component.literal("  ").append(line(player, card))));
		});
		List<CardData> binder = state.cards().stream().filter(x -> !x.slotted()).toList();
		c.getSource().sendSystemMessage(Component.translatable("mettle.cards.binder", binder.size()));
		binder.forEach(card -> c.getSource().sendSystemMessage(Component.literal("  ").append(line(player, card))));
		if (!state.tiers().isEmpty()) {
			MutableComponent sets = Component.translatable("mettle.cards.sets");
			state.tiers().forEach((k, tier) -> sets.append(" ").append(CardText.keyword(registries, k)).append(" " + tier));
			c.getSource().sendSystemMessage(sets);
		}
		// Whole-character ceilings this deck would pass (docs/CARDS.md §14): said plainly, so nobody counts on more.
		var perks = registries.lookupOrThrow(MettleRegistries.PERK);
		Ceilings.capped(Ceilings.Build.of(state, perks::getOptional)).forEach((stat, potential) ->
			c.getSource().sendSystemMessage(Ceilings.line(stat, potential).copy().withStyle(ChatFormatting.GOLD)));
		return state.cards().size();
	}

	private static int show(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		CardData card = find(player, StringArgumentType.getString(c, "card"));
		RegistryAccess registries = player.level().registryAccess();
		CardRules.Attributes attributes = Cards.attributes(registries, CharacterStore.get(player));
		c.getSource().sendSystemMessage(line(player, card));
		CardText.perk(registries, card.perk()).ifPresent(p -> c.getSource().sendSystemMessage(Component.literal("  ")
			.append(dev.voidmc.mettle.character.PerkText.description(p, card.rank()).copy().withStyle(ChatFormatting.GRAY))));
		for (CardData.TraitRoll t : card.traits()) {
			registries.lookupOrThrow(MettleRegistries.TRAIT).getOptional(t.trait()).ifPresent(def -> c.getSource().sendSystemMessage(Component.literal("  ")
				.append(CardText.trait(registries, t, Optional.of(CardRules.effective(def, t.value(), attributes))).withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
				.append(CardText.sentence(def, CardRules.effective(def, t.value(), attributes)).copy().withStyle(ChatFormatting.GRAY))));
		}
		c.getSource().sendSystemMessage(Component.translatable("mettle.cards.history", card.drawn().name(), Cards.skillName(player, card.skill()), card.drawn().level(),
			card.drawn().day(), card.history().owners(), card.scars()).withStyle(ChatFormatting.DARK_GRAY));
		return 1;
	}

	private static int slot(CommandContext<CommandSourceStack> c, int index) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		CardData card = find(player, StringArgumentType.getString(c, "card"));
		Optional<CardRules.Refusal> why = Cards.slot(player, card.id(), index);
		if (why.isPresent()) {
			c.getSource().sendFailure(Component.translatable(why.get().translationKey()));
			return 0;
		}
		return 1;
	}

	private static int unslot(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		CardData card = find(player, StringArgumentType.getString(c, "card"));
		Optional<CardRules.Refusal> why = Cards.unslot(player, List.of(card.id()), Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(c.getSource()));
		if (why.isPresent()) {
			c.getSource().sendFailure(Component.translatable(why.get().translationKey()));
			return 0;
		}
		return 1;
	}

	private static List<Long> cards(ServerPlayer player, String words) throws CommandSyntaxException {
		List<Long> ids = new ArrayList<>();
		for (String w : words.trim().split("[\\s,]+")) {
			if (!w.isBlank()) {
				ids.add(find(player, w).id());
			}
		}
		return ids;
	}

	private static int rebuild(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Optional<CardRules.Refusal> why = Rebuilds.rebuild(player, cards(player, StringArgumentType.getString(c, "cards")));
		if (why.isPresent()) {
			c.getSource().sendFailure(Component.translatable(why.get().translationKey()));
			return 0;
		}
		Rebuilds.list(player);
		return 1;
	}

	private static int release(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Optional<CardRules.Refusal> why = Rebuilds.release(player, cards(player, StringArgumentType.getString(c, "cards")));
		if (why.isPresent()) {
			c.getSource().sendFailure(Component.translatable(why.get().translationKey()));
			return 0;
		}
		return 1;
	}

	private static int pin(CommandContext<CommandSourceStack> c, String words) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		List<Identifier> keywords = new ArrayList<>();
		for (String w : words.trim().split("\\s+")) {
			if (!w.isBlank()) {
				keywords.add(w.contains(":") ? Identifier.parse(w) : Identifier.fromNamespaceAndPath("mettle", w.toLowerCase(Locale.ROOT)));
			}
		}
		Optional<CardRules.Refusal> why = Cards.pin(player, keywords);
		if (why.isPresent()) {
			c.getSource().sendFailure(Component.translatable(why.get().translationKey()));
			return 0;
		}
		return 1;
	}

	private static int give(CommandContext<CommandSourceStack> c, Rarity rarity, Collection<ServerPlayer> players) throws CommandSyntaxException {
		ResourceKey<Perk> perk = ResourceKeyArgument.getRegistryKey(c, "perk", MettleRegistries.PERK, UNKNOWN);
		for (ServerPlayer p : players) {
			CardData card = Cards.give(p, perk, rarity, false);
			c.getSource().sendSuccess(() -> Component.translatable("mettle.cards.given", p.getName()).append(" ").append(line(p, card)), true);
		}
		return players.size();
	}

	private static int roll(CommandContext<CommandSourceStack> c, int hands) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ResourceKey<Skill> skill = ResourceKeyArgument.getRegistryKey(c, "skill", MettleRegistries.SKILL, UNKNOWN);
		CharacterState state = Cards.ensure(player);
		RegistryAccess registries = player.level().registryAccess();
		DraftState d = state.drafts();
		Random random = new Random(System.nanoTime());
		for (int h = 0; h < hands; h++) {
			List<Card> hand = CardRules.deal(skill, Cards.sheet(registries, state, CardStore.get(player)), Drafts.deck(registries), Cards.data(registries),
				random, d.recent(), Set.of());
			c.getSource().sendSystemMessage(Component.translatable("mettle.cards.roll", h + 1));
			for (Card card : hand) {
				Optional<Perk> perk = CardText.perk(registries, card.perk());
				c.getSource().sendSystemMessage(Component.literal("  ")
					.append(perk.map(p -> CardText.name(p, card.rank())).orElse(Component.literal("?")).withStyle(s -> s.withColor(TextColor.fromRgb(card.rarity().colour))))
					.append(" · ").append(CardText.keywords(registries, card.keywords()))
					.append(" · ").append(CardText.traits(registries, card.traits()))
					.append(Component.translatable("mettle.cards.needs", card.needs()).withStyle(ChatFormatting.DARK_GRAY)));
			}
		}
		return hands;
	}

	private static int swaps(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) {
		int n = IntegerArgumentType.getInteger(c, "count");
		players.forEach(p -> Cards.setFreeSwaps(p, n));
		c.getSource().sendSuccess(() -> Component.translatable("mettle.cards.swaps_set", n, players.size()), true);
		return players.size();
	}

	private static int clear(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) {
		int total = 0;
		for (ServerPlayer p : players) {
			total += Cards.clear(p);
		}
		int t = total;
		c.getSource().sendSuccess(() -> Component.translatable("mettle.cards.cleared", t, players.size()), true);
		return players.size();
	}

	/** Used by the draft's chat lines: a rolled offer's keywords, traits and requirement after the perk's name. */
	public static MutableComponent offer(RegistryAccess registries, Card card) {
		MutableComponent out = Component.empty();
		if (!card.rolled()) {
			return out;
		}
		out.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY)).append(CardText.keywords(registries, card.keywords()).withStyle(ChatFormatting.GRAY));
		if (!card.traits().isEmpty()) {
			out.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY)).append(CardText.traits(registries, card.traits()).withStyle(ChatFormatting.WHITE));
		}
		out.append(Component.translatable("mettle.cards.needs", card.needs()).withStyle(ChatFormatting.DARK_GRAY));
		return out;
	}
}
