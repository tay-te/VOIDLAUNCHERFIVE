package dev.voidmc.mettle.character.cards;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.voidmc.mettle.api.Talent;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardStore;
import dev.voidmc.mettle.core.CardsState;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.DraftState;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Talents from chat (docs/BUILD_FEEL.md §13), until the Hub draws them: {@code /mettle talents} lists the row, the talent
 * binder and the hand; {@code keep <1-3>}, {@code reroll}, {@code slot <card> [slot]}, {@code unslot <card>}. Ops:
 * {@code give <talent> [rarity] [value]}, {@code drafts <n>}, {@code wake} (opens the waking window).
 */
public final class TalentCommands {
	private TalentCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("mettle").then(Commands.literal("talents")
			.executes(c -> show(c, me(c)))
			.then(Commands.literal("keep").then(Commands.argument("n", IntegerArgumentType.integer(1, 3))
				.executes(c -> answer(c, Talents.keep(me(c), IntegerArgumentType.getInteger(c, "n") - 1)))))
			.then(Commands.literal("reroll").executes(c -> answer(c, Talents.reroll(me(c)))))
			.then(Commands.literal("slot").then(Commands.argument("card", StringArgumentType.word())
				.executes(c -> answer(c, Talents.slot(me(c), CardCommands.find(me(c), StringArgumentType.getString(c, "card")).id(), -1)))
				.then(Commands.argument("slot", IntegerArgumentType.integer(1, 10)).executes(c -> answer(c, Talents.slot(me(c),
					CardCommands.find(me(c), StringArgumentType.getString(c, "card")).id(), IntegerArgumentType.getInteger(c, "slot") - 1))))))
			.then(Commands.literal("unslot").then(Commands.argument("card", StringArgumentType.word())
				.executes(c -> answer(c, Talents.unslot(me(c), CardCommands.find(me(c), StringArgumentType.getString(c, "card")).id())))))
			.then(Commands.literal("give").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("talent", StringArgumentType.word())
					.executes(c -> give(c, null, 0))
					.then(Commands.argument("rarity", StringArgumentType.word())
						.executes(c -> give(c, StringArgumentType.getString(c, "rarity"), 0))
						.then(Commands.argument("value", DoubleArgumentType.doubleArg(0, 1000))
							.executes(c -> give(c, StringArgumentType.getString(c, "rarity"), DoubleArgumentType.getDouble(c, "value")))))))
			.then(Commands.literal("drafts").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("n", IntegerArgumentType.integer(0, 100)).executes(c -> {
					ServerPlayer p = me(c);
					CharacterState state = Cards.ensure(p);
					DraftState d = state.drafts();
					CharacterStore.set(p, state.withDraft(d.withTalent(d.talent().withQueued(IntegerArgumentType.getInteger(c, "n")).withHand(java.util.List.of()))));
					Talents.refresh(p);
					return 1;
				})))
			.then(Commands.literal("wake").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c -> {
				Talents.openWake(me(c));
				return 1;
			}))));
	}

	private static ServerPlayer me(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		return c.getSource().getPlayerOrException();
	}

	private static int answer(CommandContext<CommandSourceStack> c, Optional<CardRules.Refusal> no) {
		if (no.isPresent()) {
			c.getSource().sendFailure(Component.translatable(no.get().translationKey()));
			return 0;
		}
		return 1;
	}

	private static int give(CommandContext<CommandSourceStack> c, String rarity, double value) throws CommandSyntaxException {
		ServerPlayer p = me(c);
		String t = StringArgumentType.getString(c, "talent");
		Identifier id = t.contains(":") ? Identifier.tryParse(t) : Identifier.fromNamespaceAndPath("mettle", t);
		if (id == null || Talents.def(p.level().registryAccess(), id).isEmpty()) {
			c.getSource().sendFailure(Component.literal("No talent " + t));
			return 0;
		}
		DraftState.Rarity r = null;
		if (rarity != null) {
			try {
				r = DraftState.Rarity.valueOf(rarity.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				c.getSource().sendFailure(Component.literal("No rarity " + rarity + " (common, rare, epic)"));
				return 0;
			}
		}
		CardData card = Talents.give(p, id, r, value, true);
		c.getSource().sendSuccess(() -> Component.literal("Gave " + Cards.printId(p.level().registryAccess(), card) + " " + id + " " + card.rarity().getSerializedName()
			+ " " + TalentRules.value(card) + (card.slotted() ? " (in the row)" : "")), false);
		return 1;
	}

	private static int show(CommandContext<CommandSourceStack> c, ServerPlayer p) {
		CharacterState state = Cards.ensure(p);
		CardsState cards = CardStore.get(p);
		var registries = p.level().registryAccess();
		int slots = TalentRules.slots(state.characterLevel());
		c.getSource().sendSuccess(() -> Component.literal("Talents: " + cards.talent().slotted().size() + " / " + slots + " slots · drafts waiting "
			+ state.drafts().talent().queued() + " · rerolls " + state.drafts().rerolls()), false);
		for (CardData card : cards.talent().cards()) {
			Optional<Talent> def = card.talentId().flatMap(id -> Talents.def(registries, id));
			c.getSource().sendSuccess(() -> Component.literal((card.slotted() ? "  [" + (card.place().index() + 1) + "] " : "  ") + Cards.printId(registries, card) + " ")
				.append(def.map(Talent::name).orElse(Component.literal("?"))).append(" (" + card.rarity().getSerializedName() + ") ")
				.append(def.map(d -> d.line(TalentRules.value(card))).orElse(Component.empty())), false);
		}
		var hand = state.drafts().talent().hand();
		for (int i = 0; i < hand.size(); i++) {
			DraftState.Card o = hand.get(i);
			int n = i + 1;
			Optional<Talent> def = Talents.def(registries, TalentRules.id(o.perk()));
			double v = o.traits().isEmpty() ? 0 : o.traits().getFirst().value();
			c.getSource().sendSuccess(() -> Component.literal("  hand " + n + ": ").append(def.map(Talent::name).orElse(Component.literal("?")))
				.append(" (" + o.rarity().getSerializedName() + ") ").append(def.map(d -> d.line(v)).orElse(Component.empty())), false);
		}
		return 1;
	}
}
