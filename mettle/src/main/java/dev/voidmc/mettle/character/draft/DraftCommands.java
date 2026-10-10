package dev.voidmc.mettle.character.draft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.PerkText;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.DraftState;
import dev.voidmc.mettle.core.DraftState.Card;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

/**
 * The draft from chat, until the overlay draws it (docs/DRAFT.md §9): {@code /mettle draft} shows every hand (click a card
 * to keep it), {@code /mettle draft pick <skill> <1–3>} and {@code /mettle draft reroll <skill>}. Ops:
 * {@code /mettle draft give <skill> <n> [players]} and {@code /mettle draft rerolls <n> [players]}.
 */
public final class DraftCommands {
	private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(id -> Component.translatable("mettle.command.unknown", String.valueOf(id)));

	private DraftCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("mettle")
			.then(Commands.literal("draft")
				.executes(c -> show(c, c.getSource().getPlayerOrException()))
				.then(Commands.literal("pick")
					.then(Commands.argument("skill", ResourceKeyArgument.key(MettleRegistries.SKILL))
						.then(Commands.argument("card", IntegerArgumentType.integer(1, DraftRules.HAND))
							.executes(DraftCommands::pick))))
				.then(Commands.literal("reroll")
					.then(Commands.argument("skill", ResourceKeyArgument.key(MettleRegistries.SKILL))
						.executes(DraftCommands::reroll)))
				.then(Commands.literal("give")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.argument("skill", ResourceKeyArgument.key(MettleRegistries.SKILL))
						.then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
							.executes(c -> give(c, List.of(c.getSource().getPlayerOrException())))
							.then(Commands.argument("players", EntityArgument.players())
								.executes(c -> give(c, EntityArgument.getPlayers(c, "players")))))))
				.then(Commands.literal("rerolls")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.argument("count", IntegerArgumentType.integer(0, DraftRules.MAX_REROLLS))
						.executes(c -> rerolls(c, List.of(c.getSource().getPlayerOrException())))
						.then(Commands.argument("players", EntityArgument.players())
							.executes(c -> rerolls(c, EntityArgument.getPlayers(c, "players"))))))));
	}

	private static int show(CommandContext<CommandSourceStack> c, ServerPlayer player) {
		Drafts.refresh(player);
		CharacterState state = Drafts.ensure(player);
		DraftState d = state.drafts();
		c.getSource().sendSystemMessage(Component.translatable("mettle.draft.header", d.totalQueued(), d.rerolls()).withStyle(ChatFormatting.BOLD));
		var skills = player.level().registryAccess().lookupOrThrow(MettleRegistries.SKILL);
		var perks = Perks.registry(player);
		d.queued().keySet().stream().sorted(Comparator.comparing(k -> k.identifier().toString())).forEach(skillKey -> {
			Component name = skills.getOptional(skillKey).map(Skill::name).orElse(Component.literal(skillKey.identifier().getPath()));
			List<Card> hand = d.hand(skillKey);
			c.getSource().sendSystemMessage(Component.translatable(hand.isEmpty() ? "mettle.draft.skill_waiting" : "mettle.draft.skill", name, d.queued(skillKey)));
			for (int i = 0; i < hand.size(); i++) {
				c.getSource().sendSystemMessage(line(skillKey, i, hand.get(i), perks.getOptional(hand.get(i).perk()), skills, player.level().registryAccess()));
			}
			if (!hand.isEmpty() && d.rerolls() > 0) {
				String cmd = "/mettle draft reroll " + skillKey.identifier();
				c.getSource().sendSystemMessage(Component.literal("   ").append(Component.translatable("mettle.draft.reroll_button", d.rerolls())
					.withStyle(s -> s.withColor(ChatFormatting.GRAY).withClickEvent(new ClickEvent.RunCommand(cmd)))));
			}
		});
		return 1;
	}

	private static Component line(ResourceKey<Skill> skill, int index, Card card, Optional<Perk> perk,
		net.minecraft.core.Registry<Skill> skills, net.minecraft.core.RegistryAccess registries) {
		String cmd = "/mettle draft pick " + skill.identifier() + " " + (index + 1);
		MutableComponent out = Component.literal("  [" + (index + 1) + "] ").withStyle(ChatFormatting.GRAY);
		MutableComponent name = perk.map(p -> p.name().copy()).orElse(Component.literal(card.perk().identifier().toString()));
		if (perk.isPresent() && perk.get().ranks() > 1) {
			name.append(" " + dev.voidmc.mettle.character.cards.CardText.roman(card.rank()));
		}
		out.append(name.withStyle(s -> s.withColor(TextColor.fromRgb(card.rarity().colour)).withBold(true)));
		out.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
			.append(Component.translatable("mettle.draft.rarity." + card.rarity().getSerializedName()).withStyle(s -> s.withColor(TextColor.fromRgb(card.rarity().colour))));
		if (card.wildcard() && perk.isPresent()) {
			Component from = skills.getOptional(perk.get().skill()).map(Skill::name).orElse(Component.empty());
			out.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY)).append(Component.translatable("mettle.draft.wildcard", from).withStyle(ChatFormatting.GOLD));
		}
		out.append(dev.voidmc.mettle.character.cards.CardCommands.offer(registries, card));
		if (perk.isPresent()) {
			out.append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY)).append(PerkText.description(perk.get(), card.rank()).copy().withStyle(ChatFormatting.GRAY));
			perk.get().downside().ifPresent(down -> out.append(Component.literal(" ").append(Component.translatable("mettle.draft.but", down)).withStyle(ChatFormatting.RED)));
		}
		return out.withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand(cmd)).withHoverEvent(new HoverEvent.ShowText(Component.translatable("mettle.draft.click_to_keep"))));
	}

	private static int pick(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ResourceKey<Skill> skill = ResourceKeyArgument.getRegistryKey(c, "skill", MettleRegistries.SKILL, UNKNOWN);
		Optional<Drafts.Refusal> why = Drafts.pick(player, skill, IntegerArgumentType.getInteger(c, "card") - 1);
		if (why.isPresent()) {
			c.getSource().sendFailure(Component.translatable(why.get().translationKey()));
			return 0;
		}
		return 1;
	}

	private static int reroll(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ResourceKey<Skill> skill = ResourceKeyArgument.getRegistryKey(c, "skill", MettleRegistries.SKILL, UNKNOWN);
		Optional<Drafts.Refusal> why = Drafts.reroll(player, skill);
		if (why.isPresent()) {
			c.getSource().sendFailure(Component.translatable(why.get().translationKey()));
			return 0;
		}
		return show(c, player);
	}

	private static int give(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) throws CommandSyntaxException {
		ResourceKey<Skill> skill = ResourceKeyArgument.getRegistryKey(c, "skill", MettleRegistries.SKILL, UNKNOWN);
		int count = IntegerArgumentType.getInteger(c, "count");
		players.forEach(p -> Drafts.give(p, skill, count));
		c.getSource().sendSuccess(() -> Component.translatable("mettle.draft.given", count, skill.identifier().toString(), players.size()), true);
		return players.size();
	}

	private static int rerolls(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) {
		int count = IntegerArgumentType.getInteger(c, "count");
		players.forEach(p -> Drafts.setRerolls(p, count));
		c.getSource().sendSuccess(() -> Component.translatable("mettle.draft.rerolls_set", count, players.size()), true);
		return players.size();
	}
}
