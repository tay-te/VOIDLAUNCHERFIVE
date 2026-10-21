package dev.voidmc.mettle.character.cards;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.core.BuildState;
import dev.voidmc.mettle.core.CharacterStore;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Build feel from chat (docs/BUILD_FEEL.md §10), until the Hub draws it: {@code /mettle build} shows the byname, Focus,
 * boons and shards; {@code focus <keyword|none>}, {@code temper <card> <trait>}, {@code inscribe <card> <keyword>},
 * {@code boon reroll <skill>}, {@code signature <card>}, {@code byname [n]}, {@code forge <mythic|legend>},
 * {@code guide skip}, {@code title <deed|none> [instead]}. Ops: {@code shards <n> [players]}, {@code boons <skill> <n> [players]}, {@code guide on|off [players]}.
 */
public final class BuildCommands {
	private BuildCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("mettle").then(Commands.literal("build")
			.executes(c -> show(c, c.getSource().getPlayerOrException()))
			.then(Commands.literal("focus").then(Commands.argument("keyword", StringArgumentType.word()).executes(c -> {
				String k = StringArgumentType.getString(c, "keyword");
				return answer(c, BuildFeel.focus(me(c), "none".equals(k) ? Optional.empty() : Optional.ofNullable(id(k))));
			})))
			.then(Commands.literal("temper").then(Commands.argument("card", StringArgumentType.word()).then(Commands.argument("trait", StringArgumentType.word())
				.executes(c -> answer(c, BuildFeel.temper(me(c), CardCommands.find(me(c), StringArgumentType.getString(c, "card")).id(),
					id(StringArgumentType.getString(c, "trait"))))))))
			.then(Commands.literal("inscribe").then(Commands.argument("card", StringArgumentType.word()).then(Commands.argument("keyword", StringArgumentType.word())
				.executes(c -> answer(c, BuildFeel.inscribe(me(c), CardCommands.find(me(c), StringArgumentType.getString(c, "card")).id(),
					id(StringArgumentType.getString(c, "keyword"))))))))
			.then(Commands.literal("boon").then(Commands.literal("reroll").then(Commands.argument("skill", ResourceKeyArgument.key(MettleRegistries.SKILL))
				.executes(c -> answer(c, BuildFeel.boonReroll(me(c), ResourceKeyArgument.getRegistryKey(c, "skill", MettleRegistries.SKILL,
					new com.mojang.brigadier.exceptions.DynamicCommandExceptionType(x -> Component.literal(String.valueOf(x))))))))))
			.then(Commands.literal("signature").then(Commands.argument("card", StringArgumentType.word()).executes(c ->
				answer(c, BuildFeel.signature(me(c), CardCommands.find(me(c), StringArgumentType.getString(c, "card")).id(), Optional.empty())))))
			.then(Commands.literal("byname")
				.executes(c -> answer(c, BuildFeel.wearByname(me(c), -1)))
				.then(Commands.argument("n", IntegerArgumentType.integer(1, 100)).executes(c -> answer(c, BuildFeel.wearByname(me(c), IntegerArgumentType.getInteger(c, "n") - 1)))))
			.then(Commands.literal("title")
				.then(Commands.literal("none").executes(c -> answer(c, Deeds.wear(me(c), Optional.empty(), false))))
				.then(Commands.argument("deed", StringArgumentType.word())
					.executes(c -> answer(c, Deeds.wear(me(c), Optional.of(id(StringArgumentType.getString(c, "deed"))), false)))
					.then(Commands.literal("instead").executes(c -> answer(c, Deeds.wear(me(c), Optional.of(id(StringArgumentType.getString(c, "deed"))), true))))))
			.then(Commands.literal("forge").then(Commands.argument("what", StringArgumentType.greedyString()).executes(c ->
				answer(c, BuildFeel.forge(me(c), StringArgumentType.getString(c, "what").trim())))))
			.then(Commands.literal("guide")
				.then(Commands.literal("skip").executes(c -> answer(c, BuildFeel.guideSkip(me(c)))))
				.then(Commands.literal("on").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.argument("players", EntityArgument.players()).executes(c -> guide(EntityArgument.getPlayers(c, "players"), true))))
				.then(Commands.literal("off").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.argument("players", EntityArgument.players()).executes(c -> guide(EntityArgument.getPlayers(c, "players"), false)))))
			.then(Commands.literal("shards").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("n", IntegerArgumentType.integer(0, 100000))
					.executes(c -> shards(List.of(me(c)), IntegerArgumentType.getInteger(c, "n")))
					.then(Commands.argument("players", EntityArgument.players())
						.executes(c -> shards(EntityArgument.getPlayers(c, "players"), IntegerArgumentType.getInteger(c, "n"))))))
			.then(Commands.literal("boons").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("skill", ResourceKeyArgument.key(MettleRegistries.SKILL))
					.then(Commands.argument("n", IntegerArgumentType.integer(0, 1000))
						.executes(c -> boons(c, List.of(me(c))))
						.then(Commands.argument("players", EntityArgument.players()).executes(c -> boons(c, EntityArgument.getPlayers(c, "players")))))))));
	}

	private static ServerPlayer me(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		return c.getSource().getPlayerOrException();
	}

	private static Identifier id(String text) {
		Identifier id = Identifier.tryParse(text);
		return id == null ? Identifier.fromNamespaceAndPath("mettle", "none") : text.contains(":") ? id : Identifier.fromNamespaceAndPath("mettle", text);
	}

	private static int answer(CommandContext<CommandSourceStack> c, Optional<CardRules.Refusal> no) {
		if (no.isPresent()) {
			c.getSource().sendFailure(Component.translatable(no.get().translationKey()));
			return 0;
		}
		return 1;
	}

	private static int show(CommandContext<CommandSourceStack> c, ServerPlayer p) {
		BuildState b = BuildFeel.get(p);
		Component byname = BuildFeel.byname(p).orElse(Component.literal("-"));
		c.getSource().sendSuccess(() -> Component.literal("Byname: ").append(byname).append(" (tier " + b.byname().tier() + ") · Focus: "
			+ b.focus().map(Identifier::toString).orElse("-") + " · boons: " + CharacterStore.get(p).drafts().boons() + " · shards "
			+ b.shards() + " / " + ForgeRules.cost(b.forges()) + " · forges " + b.forges() + " · legend " + b.legend()
			+ " · titles " + b.titles().held() + " wearing " + b.titles().worn().map(Identifier::toString).orElse("-")
			+ " · guide " + (b.guide().on() && !b.guide().skipped() ? "hint " + b.guide().current() + ", seen " + b.guide().seen() : "off")), false);
		return 1;
	}

	private static int shards(Collection<ServerPlayer> players, int n) {
		for (ServerPlayer p : players) {
			Cards.ensure(p);
			BuildFeel.set(p, BuildFeel.get(p).withShards(n));
			BuildFeel.forgeReady(p);
		}
		return players.size();
	}

	private static int boons(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) throws CommandSyntaxException {
		var skill = ResourceKeyArgument.getRegistryKey(c, "skill", MettleRegistries.SKILL,
			new com.mojang.brigadier.exceptions.DynamicCommandExceptionType(x -> Component.literal(String.valueOf(x))));
		int n = IntegerArgumentType.getInteger(c, "n");
		for (ServerPlayer p : players) {
			var state = Cards.ensure(p);
			var d = state.drafts();
			CharacterStore.set(p, state.withDraft(d.addBoons(skill, n - d.boons(skill))));
		}
		return players.size();
	}

	private static int guide(Collection<ServerPlayer> players, boolean on) {
		for (ServerPlayer p : players) {
			BuildState b = BuildFeel.get(p);
			BuildFeel.set(p, b.withGuide(on ? new BuildState.Guide(true, List.of(), 0, 0, false, false) : BuildState.Guide.OFF).withOnce("guide/decided"));
		}
		return players.size();
	}
}
