package dev.voidmc.hearth.standing.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.hearth.standing.StandingEngine;
import dev.voidmc.hearth.standing.StandingModule;
import dev.voidmc.hearth.standing.appearance.Look;
import dev.voidmc.hearth.standing.model.Companies;
import dev.voidmc.hearth.standing.model.Company;
import dev.voidmc.hearth.standing.model.Memories;
import dev.voidmc.hearth.standing.model.RegionData;
import dev.voidmc.hearth.standing.net.StandingNetwork;
import dev.voidmc.hearth.standing.net.StandingPayloads;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * {@code /hearth standing} (the reputation page, for everyone), {@code /hearth company …} (companies, for everyone when
 * the server allows them), and game-master tools under {@code /hearth standing}: {@code show}, {@code set}, {@code deed},
 * {@code opinions}, {@code gossip}.
 */
public final class StandingCommands {
	private static final SimpleCommandExceptionType NO_SETTLEMENT = new SimpleCommandExceptionType(Component.translatable("hearth.standing.command.no_settlement"));
	private static final SimpleCommandExceptionType NOT_RUNNING = new SimpleCommandExceptionType(Component.translatable("hearth.standing.command.not_running"));

	private static final SuggestionProvider<CommandSourceStack> SETTLEMENTS = (c, b) -> {
		StandingEngine e = StandingModule.engine();
		return SharedSuggestionProvider.suggest(e == null ? List.of() : e.host().settlements().stream().map(s -> StringArgumentType.escapeIfRequired(s.name())).toList(), b);
	};
	private static final SuggestionProvider<CommandSourceStack> COMPANIES = (c, b) -> {
		StandingEngine e = StandingModule.engine();
		return SharedSuggestionProvider.suggest(e == null ? List.of() : e.host().region().companies().stream().map(Company::name).toList(), b);
	};

	private StandingCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("hearth")
			.then(Commands.literal("standing")
				.executes(StandingCommands::page)
				.then(Commands.literal("show")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.argument("player", EntityArgument.player()).executes(c -> show(c, EntityArgument.getPlayer(c, "player")))))
				.then(Commands.literal("set")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.argument("player", EntityArgument.player())
						.then(Commands.argument("value", IntegerArgumentType.integer(-100, 100))
							.executes(c -> set(c, null))
							.then(Commands.argument("settlement", StringArgumentType.string()).suggests(SETTLEMENTS)
								.executes(c -> set(c, StringArgumentType.getString(c, "settlement")))))))
				.then(Commands.literal("deed")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.argument("player", EntityArgument.player())
						.then(Commands.argument("kind", StringArgumentType.string())
							.then(Commands.argument("weight", IntegerArgumentType.integer(-100, 100))
								.executes(c -> deed(c, true))
								.then(Commands.argument("witnessed", BoolArgumentType.bool())
									.executes(c -> deed(c, BoolArgumentType.getBool(c, "witnessed"))))))))
				.then(Commands.literal("opinions")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.argument("player", EntityArgument.player()).executes(StandingCommands::opinions)))
				.then(Commands.literal("gossip")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.executes(StandingCommands::gossip)))
			.then(Commands.literal("company")
				.executes(c -> companyInfo(c, null))
				.then(Commands.literal("form").then(Commands.argument("name", StringArgumentType.greedyString()).executes(StandingCommands::form)))
				.then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player()).executes(StandingCommands::invite)))
				.then(Commands.literal("join").then(Commands.argument("name", StringArgumentType.greedyString()).suggests(COMPANIES).executes(StandingCommands::join)))
				.then(Commands.literal("leave").executes(StandingCommands::leave))
				.then(Commands.literal("list").executes(StandingCommands::companies))
				.then(Commands.literal("info").then(Commands.argument("name", StringArgumentType.greedyString()).suggests(COMPANIES)
					.executes(c -> companyInfo(c, StringArgumentType.getString(c, "name")))))));
	}

	private static StandingEngine engine() throws CommandSyntaxException {
		StandingEngine e = StandingModule.engine();
		if (e == null) {
			throw NOT_RUNNING.create();
		}
		return e;
	}

	// ---------------------------------------------------------------- the page

	private static int page(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		engine();
		if (ServerPlayNetworking.canSend(player, StandingPayloads.Page.TYPE)) {
			StandingNetwork.sendPage(player);
			return 1;
		}
		return show(c, player);
	}

	/** The page as chat lines (for consoles, and clients without the mod). */
	private static int show(CommandContext<CommandSourceStack> c, ServerPlayer player) throws CommandSyntaxException {
		StandingEngine e = engine();
		StandingPayloads.Page page = StandingNetwork.page(e, player);
		CommandSourceStack src = c.getSource();
		src.sendSuccess(() -> Component.translatable("hearth.standing.page.title", player.getDisplayName()).withStyle(ChatFormatting.GOLD), false);
		if (page.towns().isEmpty()) {
			src.sendSuccess(() -> Component.translatable("hearth.standing.page.none").withStyle(ChatFormatting.GRAY), false);
		}
		for (StandingPayloads.TownRow r : page.towns()) {
			src.sendSuccess(() -> Component.literal("  " + r.name() + ": ")
				.append(Component.translatable("hearth.title." + r.title()).withStyle(ChatFormatting.YELLOW))
				.append(Component.literal(" (" + signed(r.standing()) + ")" + (r.distance() >= 0 ? ", " + r.distance() + " m" : ""))
					.withStyle(ChatFormatting.GRAY)), false);
		}
		src.sendSuccess(() -> Component.translatable("hearth.standing.page.fame", page.fame()).append(Component.literal(String.format(Locale.ROOT,
			"  charisma %+.2f, menace %.2f", page.charisma(), page.menace()))).withStyle(ChatFormatting.GRAY), false);
		if (!page.company().isEmpty()) {
			src.sendSuccess(() -> Component.translatable("hearth.standing.page.company", page.company(), String.join(", ", page.members()))
				.withStyle(ChatFormatting.GRAY), false);
		}
		return page.towns().size();
	}

	// ---------------------------------------------------------------- game-master tools

	private static int set(CommandContext<CommandSourceStack> c, @Nullable String name) throws CommandSyntaxException {
		StandingEngine e = engine();
		ServerPlayer player = EntityArgument.getPlayer(c, "player");
		int value = IntegerArgumentType.getInteger(c, "value");
		Settlement s = name == null ? near(c.getSource(), player.blockPosition()) : byName(e, name);
		e.set(s, player.getUUID(), value);
		c.getSource().sendSuccess(() -> Component.translatable("hearth.standing.command.set", player.getDisplayName(), s.name(), signed(value),
			Component.translatable(e.title(s, player.getUUID()).translationKey())), true);
		return 1;
	}

	private static int deed(CommandContext<CommandSourceStack> c, boolean witnessed) throws CommandSyntaxException {
		StandingEngine e = engine();
		ServerPlayer player = EntityArgument.getPlayer(c, "player");
		String kind = StringArgumentType.getString(c, "kind");
		int weight = IntegerArgumentType.getInteger(c, "weight");
		Settlement s = near(c.getSource(), player.blockPosition());
		int before = e.standing(s, player.getUUID());
		int queued = e.host().region().road().size();
		e.deed(new StandingService.Deed(player.getUUID(), s.id(), kind, weight, null, player.blockPosition(), witnessed));
		int after = e.standing(s, player.getUUID());
		int news = e.host().region().road().size() - queued;
		c.getSource().sendSuccess(() -> Component.translatable("hearth.standing.command.deed", kind, signed(weight), s.name(), signed(before), signed(after),
			news), true);
		return 1;
	}

	private static int opinions(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		StandingEngine e = engine();
		ServerPlayer player = EntityArgument.getPlayer(c, "player");
		Settlement s = near(c.getSource(), player.blockPosition());
		UUID id = player.getUUID();
		record Row(Resident r, int opinion, int memories) {
		}
		List<Row> rows = new ArrayList<>();
		for (Resident r : s.residents()) {
			int o = e.opinion(s, r, id);
			int m = (int) Memories.of(r).all().stream().filter(x -> x.player().equals(id)).count();
			rows.add(new Row(r, o, m));
		}
		rows.sort(Comparator.comparingInt((Row r) -> -Math.abs(r.opinion - e.standing(s, id) / 4)).thenComparing(r -> r.r.fullName()));
		CommandSourceStack src = c.getSource();
		src.sendSuccess(() -> Component.translatable("hearth.standing.command.opinions", s.name(), player.getDisplayName(), signed(e.standing(s, id)))
			.withStyle(ChatFormatting.GOLD), false);
		for (Row r : rows.subList(0, Math.min(12, rows.size()))) {
			src.sendSuccess(() -> Component.literal("  " + r.r.fullName() + " (" + r.r.job.getSerializedName() + "): " + signed(r.opinion)
				+ (r.memories > 0 ? ", " + r.memories + " memories" : "")).withStyle(ChatFormatting.GRAY), false);
		}
		Look look = e.look(player);
		src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "  looks: charisma %+.2f, menace %.2f %s", look.charisma(), look.menace(),
			look.notes())).withStyle(ChatFormatting.DARK_GRAY), false);
		return rows.size();
	}

	private static int gossip(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		StandingEngine e = engine();
		RegionData region = e.host().region();
		long today = e.host().day();
		CommandSourceStack src = c.getSource();
		List<RegionData.Travelling> road = region.road();
		src.sendSuccess(() -> Component.translatable("hearth.standing.command.gossip", road.size(), region.held().size(), today).withStyle(ChatFormatting.GOLD), false);
		for (RegionData.Travelling t : road.subList(0, Math.min(15, road.size()))) {
			Settlement target = e.host().settlement(t.target());
			src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "  %s: %s %s (%+.1f) %s → %s, arrives day %d (%+d)", t.kind(),
				t.playerName().isEmpty() ? "?" : t.playerName(), "", t.strength(), t.originName(), target == null ? "#" + t.target() : target.name(), t.arrival(),
				t.arrival() - today)).withStyle(ChatFormatting.GRAY), false);
		}
		return road.size();
	}

	// ---------------------------------------------------------------- companies

	private static int form(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		StandingEngine e = engine();
		ServerPlayer player = c.getSource().getPlayerOrException();
		String name = StringArgumentType.getString(c, "name");
		return report(c, e, Companies.form(e.host().region(), player.getUUID(), name, e.host().day(), StandingModule.config().companies()), name.trim());
	}

	private static int invite(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		StandingEngine e = engine();
		ServerPlayer player = c.getSource().getPlayerOrException();
		ServerPlayer guest = EntityArgument.getPlayer(c, "player");
		Companies.Result r = Companies.invite(e.host().region(), player.getUUID(), guest.getUUID(), StandingModule.config().companies());
		Company company = e.host().region().companyOf(player.getUUID());
		if (r.ok && company != null) {
			guest.sendSystemMessage(Component.translatable("hearth.standing.company.invitation", player.getDisplayName(), company.name(), company.name())
				.withStyle(ChatFormatting.GOLD));
		}
		return report(c, e, r, guest.getDisplayName().getString());
	}

	private static int join(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		StandingEngine e = engine();
		ServerPlayer player = c.getSource().getPlayerOrException();
		String name = StringArgumentType.getString(c, "name");
		Companies.Result r = Companies.join(e.host().region(), player.getUUID(), name, StandingModule.config().companies());
		Company company = e.host().region().company(name);
		if (r.ok && company != null) {
			tellMembers(c, company, player, Component.translatable("hearth.standing.company.member_joined", player.getDisplayName(), company.name()));
		}
		return report(c, e, r, company == null ? name : company.name());
	}

	private static int leave(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		StandingEngine e = engine();
		ServerPlayer player = c.getSource().getPlayerOrException();
		Company was = e.host().region().companyOf(player.getUUID());
		Companies.Result r = Companies.leave(e.host().region(), player.getUUID());
		if (r == Companies.Result.LEFT && was != null) {
			tellMembers(c, was, player, Component.translatable("hearth.standing.company.member_left", player.getDisplayName(), was.name()));
		}
		return report(c, e, r, was == null ? "" : was.name());
	}

	private static int companies(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		StandingEngine e = engine();
		CommandSourceStack src = c.getSource();
		if (!StandingModule.config().companies().enabled()) {
			src.sendFailure(Component.translatable(Companies.Result.DISABLED.key()));
			return 0;
		}
		List<Company> all = new ArrayList<>(e.host().region().companies());
		src.sendSuccess(() -> Component.translatable("hearth.standing.company.list", all.size()).withStyle(ChatFormatting.GOLD), false);
		for (Company co : all) {
			src.sendSuccess(() -> Component.literal("  " + co.name() + " (" + co.members().size() + ")").withStyle(ChatFormatting.GRAY), false);
		}
		return all.size();
	}

	private static int companyInfo(CommandContext<CommandSourceStack> c, @Nullable String name) throws CommandSyntaxException {
		StandingEngine e = engine();
		CommandSourceStack src = c.getSource();
		RegionData region = e.host().region();
		Company co = name != null ? region.company(name) : src.getPlayer() == null ? null : region.companyOf(src.getPlayer().getUUID());
		if (co == null) {
			src.sendFailure(Component.translatable(name == null ? Companies.Result.NOT_MEMBER.key() : Companies.Result.NO_SUCH_COMPANY.key(), name == null ? "" : name));
			return 0;
		}
		List<String> names = new ArrayList<>();
		for (UUID m : co.members()) {
			String n = e.host().playerName(m);
			names.add(n.isEmpty() ? m.toString().substring(0, 8) : n);
		}
		Company shown = co;
		src.sendSuccess(() -> Component.translatable("hearth.standing.company.info", shown.name(), String.join(", ", names),
			(int) Math.round(StandingModule.config().companies().share() * 100)).withStyle(ChatFormatting.GOLD), false);
		return co.members().size();
	}

	private static int report(CommandContext<CommandSourceStack> c, StandingEngine e, Companies.Result r, String arg) {
		if (r.ok) {
			e.host().regionDirty();
			c.getSource().sendSuccess(() -> Component.translatable(r.key(), arg).withStyle(ChatFormatting.GREEN), false);
			return 1;
		}
		c.getSource().sendFailure(Component.translatable(r.key(), arg));
		return 0;
	}

	private static void tellMembers(CommandContext<CommandSourceStack> c, Company co, ServerPlayer except, Component message) {
		for (UUID m : co.members()) {
			ServerPlayer p = c.getSource().getServer().getPlayerList().getPlayer(m);
			if (p != null && p != except) {
				p.sendSystemMessage(message.copy().withStyle(ChatFormatting.GOLD));
			}
		}
	}

	// ---------------------------------------------------------------- lookups

	private static Settlement near(CommandSourceStack src, BlockPos pos) throws CommandSyntaxException {
		HearthWorld w = HearthWorld.of(src.getLevel());
		Settlement s = w == null ? null : w.settlementAt(pos);
		if (s == null && w != null) {
			s = w.nearest(pos, 256);
		}
		if (s == null) {
			throw NO_SETTLEMENT.create();
		}
		return s;
	}

	private static Settlement byName(StandingEngine e, String name) throws CommandSyntaxException {
		String key = name.trim().toLowerCase(Locale.ROOT);
		for (Settlement s : e.host().settlements()) {
			if (s.name().toLowerCase(Locale.ROOT).equals(key) || Long.toString(s.id()).equals(key)) {
				return s;
			}
		}
		throw NO_SETTLEMENT.create();
	}

	static String signed(int v) {
		return v > 0 ? "+" + v : Integer.toString(v);
	}
}
