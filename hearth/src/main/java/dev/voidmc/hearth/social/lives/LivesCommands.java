package dev.voidmc.hearth.social.lives;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.sim.lives.LivesBook;
import dev.voidmc.hearth.sim.lives.LivesDev;
import dev.voidmc.hearth.sim.lives.LivesStats;
import dev.voidmc.hearth.sim.lives.LivesView;
import dev.voidmc.hearth.sim.lives.TownLives;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code /hearth lives here} (the town's named characters, feuds and talk), {@code /hearth lives stats} (what the lives
 * cost) and, for game masters, {@code /hearth lives notable <role> [name]}, {@code /hearth lives beat <role> <beat>}
 * (tours and testing).
 */
final class LivesCommands {
	private static final SimpleCommandExceptionType NOT_IN_TOWN = new SimpleCommandExceptionType(Component.translatable("hearth.social.command.not_in_town"));
	private static final SimpleCommandExceptionType NO_ONE = new SimpleCommandExceptionType(Component.literal("Nobody here fits that."));

	private LivesCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("hearth").then(Commands.literal("lives")
			.then(Commands.literal("stats").executes(c -> {
				c.getSource().sendSuccess(() -> Component.literal(LivesStats.line()), false);
				c.getSource().sendSuccess(() -> Component.literal(LivesFolk.overheardLine()), false);
				return 1;
			}))
			.then(Commands.literal("here").executes(LivesCommands::here))
			.then(Commands.literal("notable").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("role", StringArgumentType.word()).executes(c -> notable(c, ""))
					.then(Commands.argument("name", StringArgumentType.greedyString()).executes(c -> notable(c, StringArgumentType.getString(c, "name"))))))
			.then(Commands.literal("beat").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("role", StringArgumentType.word()).then(Commands.argument("beat", StringArgumentType.word()).executes(LivesCommands::beat))))));
	}

	private static int here(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Settlement s = here(c.getSource());
		List<String> lines = new ArrayList<>();
		lines.add(s.name() + ": " + LivesView.notables(s).size() + " named, " + LivesView.town(s).feuds.size() + " feuds");
		for (TownLives.Notable n : LivesView.notables(s)) {
			Resident r = s.resident(n.resident);
			Resident p = n.partner == null ? null : s.resident(n.partner);
			lines.add("  " + (r == null ? "?" : r.fullName()) + " — " + n.role + " at " + n.beat + " (since day " + n.since + ")" + (p == null ? "" : ", with "
				+ p.givenName) + (n.byName.isEmpty() ? "" : ", moved by " + n.byName));
		}
		for (TownLives.Feud f : LivesView.town(s).feuds) {
			Resident a = s.resident(f.headA);
			Resident b = s.resident(f.headB);
			lines.add("  feud: " + (a == null ? "?" : a.fullName()) + " / " + (b == null ? "?" : b.fullName()) + " over " + f.cause + ", heat " + f.heat);
		}
		for (TownLives.News n : LivesView.news(s)) {
			lines.add("  news: day " + n.day() + " " + n.kind() + " " + String.join(", ", n.names()));
		}
		StringBuilder st = new StringBuilder("  counts:");
		for (Map.Entry<String, Integer> e : new java.util.TreeMap<>(LivesView.stats(s)).entrySet()) {
			st.append(' ').append(e.getKey()).append('=').append(e.getValue());
		}
		lines.add(st.toString());
		for (String l : lines) {
			c.getSource().sendSuccess(() -> Component.literal(l), false);
		}
		return lines.size();
	}

	private static int notable(CommandContext<CommandSourceStack> c, String name) throws CommandSyntaxException {
		Settlement s = here(c.getSource());
		String role = StringArgumentType.getString(c, "role");
		if (LivesBook.get().role(role) == null) {
			throw NO_ONE.create();
		}
		BlockPos at = BlockPos.containing(c.getSource().getPosition());
		Resident best = null;
		for (Resident r : s.residents()) {
			boolean named = !name.isEmpty() && (r.fullName().equalsIgnoreCase(name) || r.givenName.equalsIgnoreCase(name));
			if (named || name.isEmpty() && LivesDev.fits(s, r, role)) {
				if (named || best == null) {
					best = r;
				}
				if (named) {
					break;
				}
			}
		}
		if (best == null) {
			throw NO_ONE.create();
		}
		TownLives.Notable n = LivesDev.make(s, best, role);
		Resident who = best;
		c.getSource().sendSuccess(() -> Component.literal(who.fullName() + " is now the " + role + (n == null ? "" : " (" + n.beat + ")") + " near "
			+ at.toShortString()), true);
		return 1;
	}

	private static int beat(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Settlement s = here(c.getSource());
		String role = StringArgumentType.getString(c, "role");
		String beat = StringArgumentType.getString(c, "beat");
		for (TownLives.Notable n : LivesView.notables(s)) {
			if (n.role.equals(role) && LivesDev.beat(s, n, beat)) {
				c.getSource().sendSuccess(() -> Component.literal(role + " now at " + beat), true);
				return 1;
			}
		}
		throw NO_ONE.create();
	}

	private static Settlement here(CommandSourceStack src) throws CommandSyntaxException {
		ServerLevel level = src.getLevel();
		HearthWorld w = HearthWorld.of(level);
		BlockPos at = BlockPos.containing(src.getPosition());
		Settlement s = w == null ? null : w.settlementAt(at);
		if (s == null && w != null) {
			s = w.nearest(at, 400);
		}
		if (s == null) {
			throw NOT_IN_TOWN.create();
		}
		return s;
	}
}
