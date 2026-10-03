package dev.voidmc.hearth.quest.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.quest.gen.QuestGenerator;
import dev.voidmc.hearth.quest.model.Objective;
import dev.voidmc.hearth.quest.model.PlayerQuests;
import dev.voidmc.hearth.quest.model.QuestInstance;
import dev.voidmc.hearth.quest.model.QuestType;
import dev.voidmc.hearth.quest.model.SettlementQuests;
import dev.voidmc.hearth.quest.service.QuestManager;
import dev.voidmc.hearth.quest.service.QuestStore;
import dev.voidmc.hearth.quest.template.QuestTemplate;
import dev.voidmc.hearth.quest.template.QuestTemplates;
import dev.voidmc.hearth.quest.world.QuestSandbox;
import dev.voidmc.hearth.quest.world.QuestWorlds;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * {@code /hearth quest ...} (permission 2): inspect and drive quests, and a sandbox to try the whole loop without the
 * sim module.
 *
 * <pre>
 *   list [player]                     a player's active quests and history
 *   board [refresh]                   the offers of the settlement you stand in
 *   give &lt;template&gt; [player]          make a quest from a template here and give it
 *   complete [player]                 complete the tracked quest (objectives and all)
 *   abandon [player]                  abandon the tracked quest
 *   templates                         the loaded templates by type
 *   sandbox [culture] [population]    an in-memory town round you, with a stalled cottage and a notice board
 *   stall &lt;goods&gt; &lt;units&gt;            the settlement here stalls for goods (supply quests follow)
 *   pressure &lt;amount&gt;                 add night monster pressure here (a bounty follows at the threshold)
 *   standing &lt;amount&gt;                 set your standing here
 *   talk &lt;resident&gt; [option]          converse through the dialogue providers in chat (the npc module's screen stands in)
 * </pre>
 */
public final class QuestCommands {
	private QuestCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("hearth").then(Commands.literal("quest")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("list")
				.executes(c -> list(c, c.getSource().getPlayerOrException()))
				.then(Commands.argument("player", EntityArgument.player()).executes(c -> list(c, EntityArgument.getPlayer(c, "player")))))
			.then(Commands.literal("board")
				.executes(c -> board(c, false))
				.then(Commands.literal("refresh").executes(c -> board(c, true))))
			.then(Commands.literal("give")
				.then(Commands.argument("template", IdentifierArgument.id())
					.suggests((c, b) -> SharedSuggestionProvider.suggestResource(QuestTemplates.all().stream().map(QuestTemplate::id), b))
					.executes(c -> give(c, c.getSource().getPlayerOrException()))
					.then(Commands.argument("player", EntityArgument.player()).executes(c -> give(c, EntityArgument.getPlayer(c, "player"))))))
			.then(Commands.literal("complete")
				.executes(c -> complete(c, c.getSource().getPlayerOrException()))
				.then(Commands.argument("player", EntityArgument.player()).executes(c -> complete(c, EntityArgument.getPlayer(c, "player")))))
			.then(Commands.literal("abandon")
				.executes(c -> abandon(c, c.getSource().getPlayerOrException()))
				.then(Commands.argument("player", EntityArgument.player()).executes(c -> abandon(c, EntityArgument.getPlayer(c, "player")))))
			.then(Commands.literal("templates").executes(QuestCommands::templates))
			.then(Commands.literal("sandbox")
				.executes(c -> sandbox(c, Culture.TEMPERATE, 16))
				.then(Commands.argument("culture", StringArgumentType.word())
					.suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(Culture.values()).map(Culture::getSerializedName), b))
					.executes(c -> sandbox(c, culture(c), 16))
					.then(Commands.argument("population", IntegerArgumentType.integer(1, 200))
						.executes(c -> sandbox(c, culture(c), IntegerArgumentType.getInteger(c, "population"))))))
			.then(Commands.literal("stall")
				.then(Commands.argument("goods", StringArgumentType.word())
					.suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(Goods.values()).map(Goods::getSerializedName), b))
					.then(Commands.argument("units", IntegerArgumentType.integer(1, 10000)).executes(QuestCommands::stall))))
			.then(Commands.literal("pressure")
				.then(Commands.argument("amount", IntegerArgumentType.integer(1, 1000)).executes(QuestCommands::pressure)))
			.then(Commands.literal("standing")
				.then(Commands.argument("amount", IntegerArgumentType.integer(-100, 100)).executes(QuestCommands::standing)))
			.then(Commands.literal("talk")
				.then(Commands.argument("resident", StringArgumentType.string())
					.suggests((c, b) -> {
						Settlement s = here(c.getSource());
						return SharedSuggestionProvider.suggest(s == null ? List.<String>of()
							: s.residents().stream().map(r -> "\"" + r.fullName() + "\"").toList(), b);
					})
					.executes(c -> talk(c, null))
					.then(Commands.argument("option", StringArgumentType.greedyString())
						.executes(c -> talk(c, StringArgumentType.getString(c, "option"))))))));
	}

	private static Culture culture(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		String name = StringArgumentType.getString(c, "culture");
		for (Culture culture : Culture.values()) {
			if (culture.getSerializedName().equals(name.toLowerCase(Locale.ROOT))) {
				return culture;
			}
		}
		throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("Unknown culture " + name)).create();
	}

	private static @Nullable Settlement here(CommandSourceStack source) {
		return QuestWorlds.at(source.getLevel(), net.minecraft.core.BlockPos.containing(source.getPosition()));
	}

	private static Settlement requireHere(CommandSourceStack source) throws CommandSyntaxException {
		Settlement s = here(source);
		if (s == null) {
			s = QuestWorlds.nearest(source.getLevel(), net.minecraft.core.BlockPos.containing(source.getPosition()), 256);
		}
		if (s == null) {
			throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.translatable("hearth.quest.command.nosettlement")).create();
		}
		return s;
	}

	private static int list(CommandContext<CommandSourceStack> c, ServerPlayer player) {
		PlayerQuests pq = QuestStore.get(player);
		c.getSource().sendSuccess(() -> Component.literal(player.getScoreboardName() + ": " + pq.active.size() + " active, " + pq.history.size()
			+ " in history").withStyle(ChatFormatting.GOLD), false);
		for (QuestInstance q : pq.active) {
			MutableComponent line = Component.literal((q.id.equals(pq.tracked) ? "▶ " : "  ") + q.text.title()).withStyle(ChatFormatting.YELLOW)
				.append(Component.literal("  [" + q.type.getSerializedName() + ", " + q.status.getSerializedName() + ", " + q.settlementName + "]")
					.withStyle(ChatFormatting.GRAY));
			c.getSource().sendSuccess(() -> line, false);
			for (Objective o : q.objectives) {
				c.getSource().sendSuccess(() -> Component.literal("     " + (o.done() ? "✔ " : "• ") + o.label + " " + o.progress + "/" + o.count)
					.withStyle(o.done() ? ChatFormatting.DARK_GREEN : ChatFormatting.GRAY), false);
			}
		}
		return pq.active.size();
	}

	private static int board(CommandContext<CommandSourceStack> c, boolean refresh) throws CommandSyntaxException {
		Settlement s = requireHere(c.getSource());
		ServerLevel level = c.getSource().getLevel();
		SettlementQuests data = QuestManager.board(level, s, refresh);
		c.getSource().sendSuccess(() -> Component.literal(s.name() + " (" + s.tier().getSerializedName() + ", " + s.culture().getSerializedName()
			+ "): " + data.offers.size() + " offers, " + data.needs.size() + " needs, pressure " + data.pressure + ", treasury " + s.treasury())
			.withStyle(ChatFormatting.GOLD), false);
		for (QuestInstance q : data.offers) {
			c.getSource().sendSuccess(() -> Component.literal("  " + q.text.title()).withStyle(ChatFormatting.YELLOW)
				.append(Component.literal("  — " + q.giver.name() + ", " + q.reward.text() + (q.takenBy.isEmpty() ? "" : ", taken by " + q.takenBy.size()))
					.withStyle(ChatFormatting.GRAY)), false);
		}
		for (SettlementQuests.StalledNeed n : data.needs) {
			c.getSource().sendSuccess(() -> Component.literal("  need: " + n.missing() + " " + n.goods().getSerializedName() + " for plot " + n.plot()
				+ " (" + n.place() + ")").withStyle(ChatFormatting.DARK_AQUA), false);
		}
		return data.offers.size();
	}

	private static int give(CommandContext<CommandSourceStack> c, ServerPlayer player) throws CommandSyntaxException {
		Identifier id = IdentifierArgument.getId(c, "template");
		QuestTemplate t = QuestTemplates.get(id);
		if (t == null) {
			throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("No quest template " + id)).create();
		}
		Settlement s = requireHere(c.getSource());
		ServerLevel level = c.getSource().getLevel();
		SettlementQuests data = QuestManager.board(level, s, false);
		SettlementQuests.StalledNeed need = data.needs.isEmpty() ? null : data.needs.getFirst();
		if (t.when().trigger() == QuestType.Trigger.STALLED && need == null) {
			need = new SettlementQuests.StalledNeed(-1, t.when().goods().isEmpty() ? Goods.WOOD : t.when().goods().iterator().next(), 64,
				QuestWorlds.day(level), "new building");
		}
		QuestInstance q = QuestGenerator.bind(t, QuestManager.generationContext(level, s, data), need);
		if (q == null) {
			c.getSource().sendFailure(Component.literal("Template " + id + " cannot be bound here now (no destination, landmark or resident?)"));
			return 0;
		}
		data.offers.add(q);
		s.markDirty();
		return QuestManager.accept(player, s, q.id) ? 1 : 0;
	}

	private static int complete(CommandContext<CommandSourceStack> c, ServerPlayer player) {
		QuestInstance q = QuestStore.get(player).trackedOrFirst();
		if (q == null) {
			c.getSource().sendFailure(Component.literal(player.getScoreboardName() + " has no active quest"));
			return 0;
		}
		QuestManager.completeNow(player, q.id);
		return 1;
	}

	private static int abandon(CommandContext<CommandSourceStack> c, ServerPlayer player) {
		QuestInstance q = QuestStore.get(player).trackedOrFirst();
		if (q == null) {
			c.getSource().sendFailure(Component.literal(player.getScoreboardName() + " has no active quest"));
			return 0;
		}
		return QuestManager.abandon(player, q.id) ? 1 : 0;
	}

	private static int templates(CommandContext<CommandSourceStack> c) {
		Map<QuestType, List<String>> byType = new EnumMap<>(QuestType.class);
		for (QuestTemplate t : QuestTemplates.all()) {
			byType.computeIfAbsent(t.type(), k -> new ArrayList<>()).add(t.id().getPath());
		}
		c.getSource().sendSuccess(() -> Component.literal(QuestTemplates.all().size() + " quest templates").withStyle(ChatFormatting.GOLD), false);
		byType.forEach((type, ids) -> c.getSource().sendSuccess(() -> Component.literal("  " + type.getSerializedName() + " (" + ids.size() + "): ")
			.withStyle(ChatFormatting.YELLOW).append(Component.literal(String.join(", ", ids)).withStyle(ChatFormatting.GRAY)), false));
		return QuestTemplates.all().size();
	}

	private static int sandbox(CommandContext<CommandSourceStack> c, Culture culture, int population) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Direction facing = player.getDirection();
		SimpleSettlement s = QuestSandbox.create(c.getSource().getLevel(), player.blockPosition(), facing, culture, population);
		c.getSource().sendSuccess(() -> Component.translatable("hearth.quest.command.sandbox", s.name(), s.population(),
			QuestStore.board(s).offers.size()).withStyle(ChatFormatting.GOLD), false);
		return 1;
	}

	private static int stall(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Settlement s = requireHere(c.getSource());
		String name = StringArgumentType.getString(c, "goods");
		Goods goods = null;
		for (Goods g : Goods.values()) {
			if (g.getSerializedName().equals(name)) {
				goods = g;
			}
		}
		if (goods == null) {
			throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("Unknown goods " + name)).create();
		}
		int plot = s.plots().isEmpty() ? -1 : s.plots().iterator().next().id();
		String place = s.plots().isEmpty() ? "new building" : readable(s.plots().iterator().next().spec().archetype());
		QuestManager.onStalled(c.getSource().getLevel(), s, new SettlementQuests.StalledNeed(plot, goods, IntegerArgumentType.getInteger(c, "units"),
			QuestWorlds.day(c.getSource().getLevel()), place));
		return board(c, false);
	}

	private static int pressure(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Settlement s = requireHere(c.getSource());
		SettlementQuests data = QuestStore.board(s);
		data.pressure += IntegerArgumentType.getInteger(c, "amount");
		s.markDirty();
		QuestManager.board(c.getSource().getLevel(), s, true);
		return board(c, false);
	}

	private static int standing(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Settlement s = requireHere(c.getSource());
		int target = IntegerArgumentType.getInteger(c, "amount");
		s.addReputation(player.getUUID(), target - s.reputation(player.getUUID()));
		s.markDirty();
		c.getSource().sendSuccess(() -> Component.literal("Standing in " + s.name() + ": " + s.reputation(player.getUUID())), false);
		return 1;
	}

	/** A conversation in chat: every provider's options for the resident, clickable; an option picks it. */
	private static int talk(CommandContext<CommandSourceStack> c, @Nullable String option) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Settlement s = requireHere(c.getSource());
		String name = StringArgumentType.getString(c, "resident");
		Resident resident = null;
		for (Resident r : s.residents()) {
			if (r.fullName().equalsIgnoreCase(name) || r.givenName.equalsIgnoreCase(name)) {
				resident = r;
			}
		}
		if (resident == null) {
			throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("No resident " + name + " in " + s.name())).create();
		}
		if (option != null) {
			for (DialogueProvider p : DialogueProvider.providers()) {
				if (p.select(player, s, resident, option)) {
					break;
				}
			}
		}
		List<DialogueProvider.Option> options = new ArrayList<>();
		for (DialogueProvider p : DialogueProvider.providers()) {
			p.collect(player, s, resident, options);
		}
		options.sort(java.util.Comparator.comparingInt(DialogueProvider.Option::order));
		Resident r = resident;
		c.getSource().sendSuccess(() -> Component.literal(r.fullName() + " (" + r.job.getSerializedName() + ")").withStyle(ChatFormatting.GOLD), false);
		for (DialogueProvider.Option o : options) {
			String command = "/hearth quest talk \"" + r.fullName() + "\" " + o.id();
			MutableComponent line = Component.literal("  » ").withStyle(ChatFormatting.DARK_GRAY).append(o.label().copy().withStyle(s2 -> s2
				.withColor(ChatFormatting.AQUA).withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(o.id())))));
			c.getSource().sendSuccess(() -> line, false);
		}
		if (options.isEmpty()) {
			c.getSource().sendSuccess(() -> Component.literal("  (nothing to say)").withStyle(ChatFormatting.GRAY), false);
		}
		return options.size();
	}

	private static String readable(Identifier archetype) {
		String path = archetype.getPath();
		return path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
	}
}
