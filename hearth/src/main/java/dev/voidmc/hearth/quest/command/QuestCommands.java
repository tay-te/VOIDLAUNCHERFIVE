package dev.voidmc.hearth.quest.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.QuestType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.quest.gen.QuestGenerator;
import dev.voidmc.hearth.quest.model.Objective;
import dev.voidmc.hearth.quest.model.PlayerQuests;
import dev.voidmc.hearth.quest.model.QuestInstance;
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
 *   happen &lt;kind&gt;                     something happens to the settlement here (fire, collapse, caravan_robbed...)
 *   crime &lt;amount&gt;                    add recent crime here (the guard captain's line and thief-taking follow)
 *   contracts                         the workshops here short of inputs
 * </pre>
 *
 * <p>And for every player: {@code /hearth heed <offer>}, the link in a town's call for help (a fire, a collapse).
 */
public final class QuestCommands {
	private QuestCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("hearth").then(Commands.literal("heed")
			.then(Commands.argument("offer", net.minecraft.commands.arguments.UuidArgument.uuid())
				.executes(c -> dev.voidmc.hearth.quest.service.LivingQuests.heed(c.getSource().getPlayerOrException(),
					net.minecraft.commands.arguments.UuidArgument.getUuid(c, "offer")) ? 1 : 0))));
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
			.then(Commands.literal("happen")
				.then(Commands.argument("kind", StringArgumentType.word())
					.suggests((c, b) -> SharedSuggestionProvider.suggest(SettlementQuests.Happening.KINDS, b))
					.executes(QuestCommands::happen)))
			.then(Commands.literal("crime")
				.then(Commands.argument("amount", IntegerArgumentType.integer(1, 100)).executes(QuestCommands::crime)))
			.then(Commands.literal("contracts").executes(QuestCommands::contracts))
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
			c.getSource().sendSuccess(() -> Component.literal("  " + (n.contract() ? "contract: " : "need: ") + n.missing() + " "
				+ n.goods().getSerializedName() + " for " + (n.contract() ? n.source() : "plot " + n.plot()) + " (" + n.place() + ")")
				.withStyle(ChatFormatting.DARK_AQUA), false);
		}
		for (SettlementQuests.Happening h : data.happenings) {
			if (!h.resolved()) {
				c.getSource().sendSuccess(() -> Component.literal("  happening #" + h.id() + ": " + h.kind() + " (day " + h.day() + ", plot " + h.plot()
					+ (h.detail().isEmpty() ? "" : ", " + h.detail()) + ")").withStyle(ChatFormatting.LIGHT_PURPLE), false);
			}
		}
		if (data.crime > 0 || data.declining()) {
			c.getSource().sendSuccess(() -> Component.literal("  crime " + data.crime + (data.declining() ? ", declining since day " + data.decliningSince : ""))
				.withStyle(ChatFormatting.LIGHT_PURPLE), false);
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
		SettlementQuests.StalledNeed need = null;
		SettlementQuests.Happening happening = null;
		QuestType.Trigger trigger = t.when().trigger();
		if (trigger == QuestType.Trigger.STALLED || trigger == QuestType.Trigger.SHORTAGE) {
			boolean contract = trigger == QuestType.Trigger.SHORTAGE;
			for (SettlementQuests.StalledNeed n : data.needs) {
				if (n.contract() == contract && (t.when().goods().isEmpty() || t.when().goods().contains(n.goods())) && need == null) {
					need = n;
				}
			}
			if (need == null) {
				Goods goods = t.when().goods().isEmpty() ? Goods.WOOD : t.when().goods().iterator().next();
				need = new SettlementQuests.StalledNeed(-1, goods, 64, QuestWorlds.day(level), contract ? "workshop" : "new building",
					contract ? "smith" : "");
			}
		} else if (trigger == QuestType.Trigger.EVENT) {
			for (SettlementQuests.Happening h : data.happenings) {
				if (!h.resolved() && t.when().events().contains(h.kind()) && happening == null) {
					happening = h;
				}
			}
			if (happening == null) {
				happening = synthesize(level, s, data, t.when().events().getFirst(), net.minecraft.core.BlockPos.containing(c.getSource().getPosition()));
			}
			if (happening == null) {
				c.getSource().sendFailure(Component.literal("Template " + id + " answers " + t.when().events() + ", which cannot happen here"));
				return 0;
			}
		}
		QuestInstance q = QuestGenerator.bind(t, QuestManager.generationContext(level, s, data), need, happening);
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

	private static int happen(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Settlement s = requireHere(c.getSource());
		ServerLevel level = c.getSource().getLevel();
		String kind = StringArgumentType.getString(c, "kind");
		if (!SettlementQuests.Happening.KINDS.contains(kind)) {
			throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("Unknown happening " + kind + " (one of "
				+ SettlementQuests.Happening.KINDS + ")")).create();
		}
		SettlementQuests.Happening h = synthesize(level, s, QuestStore.board(s), kind, net.minecraft.core.BlockPos.containing(c.getSource().getPosition()));
		if (h == null) {
			c.getSource().sendFailure(Component.literal(kind + " cannot happen here (no other settlement for a caravan?)"));
			return 0;
		}
		return board(c, false);
	}

	/**
	 * Makes a happening for the dev commands as the events would: a fire or collapse at the plot nearest the player (a mine
	 * for a collapse when there is one), a caravan to or from the nearest other settlement, a site of the kind most like
	 * the land spent. Returns the happening (and its offer is posted), or null if it cannot happen here.
	 */
	private static SettlementQuests.@Nullable Happening synthesize(ServerLevel level, Settlement s, SettlementQuests data, String kind,
		net.minecraft.core.BlockPos at) {
		dev.voidmc.hearth.api.PlotView plot = null;
		double best = Double.MAX_VALUE;
		for (dev.voidmc.hearth.api.PlotView p : s.plots()) {
			boolean mine = p.spec().archetype().getPath().startsWith("extract/");
			double d = p.spec().origin().distSqr(at) - (kind.equals(SettlementQuests.Happening.COLLAPSE) && mine ? 1e12 : 0);
			if (d < best) {
				best = d;
				plot = p;
			}
		}
		net.minecraft.core.BlockPos pos = plot == null ? at : dev.voidmc.hearth.quest.gen.QuestGenerator.plotPlace(s, plot);
		int plotId = plot == null ? -1 : plot.id();
		Settlement other = null;
		for (Settlement o : QuestWorlds.all(level)) {
			if (o.id() != s.id() && (other == null || o.center().distSqr(s.center()) < other.center().distSqr(s.center()))) {
				other = o;
			}
		}
		String victims = s.residents().stream().filter(r -> r.age != dev.voidmc.hearth.api.AgeStage.CHILD).limit(2).map(Resident::fullName)
			.reduce((a, b) -> a + " and " + b).orElse("");
		switch (kind) {
			case SettlementQuests.Happening.CARAVAN_DEPARTED, SettlementQuests.Happening.CARAVAN_ROBBED, SettlementQuests.Happening.CARAVAN_LOST -> {
				if (other == null) {
					return null;
				}
				net.minecraft.core.BlockPos mid = kind.equals(SettlementQuests.Happening.CARAVAN_DEPARTED) ? s.center()
					: new net.minecraft.core.BlockPos((s.center().getX() + other.center().getX()) / 2, dev.voidmc.hearth.quest.model.Objective.SURFACE_Y,
					(s.center().getZ() + other.center().getZ()) / 2);
				dev.voidmc.hearth.quest.service.LivingQuests.happen(level, s, kind, -1, mid, other.id(), 96, Goods.ORE.getSerializedName(), "ore and timber",
					false);
			}
			case SettlementQuests.Happening.DEPLETED -> dev.voidmc.hearth.quest.service.LivingQuests.happen(level, s, kind, -1,
				new net.minecraft.core.BlockPos(at.getX(), dev.voidmc.hearth.quest.model.Objective.SURFACE_Y, at.getZ()), -1, 400,
				Goods.STONE.getSerializedName(), "stone", false);
			case SettlementQuests.Happening.COLLAPSE -> dev.voidmc.hearth.quest.service.LivingQuests.happen(level, s, kind, plotId, pos, -1, 2,
				Goods.ORE.getSerializedName(), victims, true);
			case SettlementQuests.Happening.FIRE -> dev.voidmc.hearth.quest.service.LivingQuests.happen(level, s, kind, plotId, pos, -1, 12, "", "", true);
			case SettlementQuests.Happening.DECLINING -> {
				data.decliningSince = QuestWorlds.day(level);
				dev.voidmc.hearth.quest.service.LivingQuests.happen(level, s, kind, -1, s.center(), -1, 1, "", "the mine is spent", false);
			}
			default -> dev.voidmc.hearth.quest.service.LivingQuests.happen(level, s, kind, plotId, pos, -1, 6, "", "", false);
		}
		SettlementQuests.Happening made = data.happenings.isEmpty() ? null : data.happenings.getFirst();
		return made != null && made.kind().equals(kind) ? made : null;
	}

	private static int crime(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Settlement s = requireHere(c.getSource());
		SettlementQuests data = QuestStore.board(s);
		data.crime = Math.min(100, data.crime + IntegerArgumentType.getInteger(c, "amount"));
		s.markDirty();
		QuestManager.board(c.getSource().getLevel(), s, true);
		return board(c, false);
	}

	private static int contracts(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Settlement s = requireHere(c.getSource());
		List<SettlementQuests.StalledNeed> shortages = dev.voidmc.hearth.quest.gen.Chains.shortages(s, QuestManager.config().living().contractStock(),
			QuestWorlds.day(c.getSource().getLevel()));
		c.getSource().sendSuccess(() -> Component.literal(s.name() + ": " + shortages.size() + " workshops short").withStyle(ChatFormatting.GOLD), false);
		for (SettlementQuests.StalledNeed n : shortages) {
			c.getSource().sendSuccess(() -> Component.literal("  " + n.place() + " (" + n.source() + "): short " + n.missing() + " "
				+ n.goods().getSerializedName() + ", " + s.stockpile().get(n.goods()) + " in store").withStyle(ChatFormatting.DARK_AQUA), false);
		}
		return shortages.size();
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
