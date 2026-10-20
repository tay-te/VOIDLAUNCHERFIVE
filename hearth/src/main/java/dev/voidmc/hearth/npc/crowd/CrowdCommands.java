package dev.voidmc.hearth.npc.crowd;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.NpcService.FolkHandle;
import dev.voidmc.hearth.api.Settlement;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /hearth crowds}: {@code stats} (what the crowds did and what it cost), {@code sense} (what folk notice about you
 * now), {@code shutters} (this town's, known and closed), {@code brawl} (the two folk nearest you come to blows: a dev
 * tool).
 */
final class CrowdCommands {
	private CrowdCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("hearth").then(Commands.literal("crowds")
			.then(Commands.literal("stats").executes(CrowdCommands::stats))
			.then(Commands.literal("sense").executes(CrowdCommands::sense))
			.then(Commands.literal("shutters").executes(CrowdCommands::shutters))
			.then(Commands.literal("brawl").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(CrowdCommands::brawl))));
	}

	private static int stats(CommandContext<CommandSourceStack> c) {
		c.getSource().sendSuccess(() -> Component.literal(CrowdStats.line()), false);
		return 1;
	}

	private static int sense(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		List<Stimuli.Sensed> sensed = Crowds.director().sense(p);
		StringBuilder sb = new StringBuilder("folk notice:");
		for (Stimuli.Sensed s : sensed) {
			sb.append(' ').append(s.stimulus().id()).append(" (").append(s.salience()).append(')');
		}
		if (sensed.isEmpty()) {
			sb.append(" nothing");
		}
		c.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
		return sensed.size();
	}

	private static int shutters(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		Settlement s = town(p);
		if (s == null) {
			c.getSource().sendFailure(Component.literal("not in a settlement"));
			return 0;
		}
		int[] n = Crowds.shutters().count((ServerLevel) p.level(), s);
		boolean closed = Crowds.shutters().closed(s, (ServerLevel) p.level());
		c.getSource().sendSuccess(() -> Component.literal(s.name() + ": " + n[0] + " shutters known, " + n[1] + " closed (" + (closed ? "shut" : "open")
			+ " now), " + Crowds.shutters().scannedPlots() + " plots scanned"), false);
		return n[1];
	}

	private static int brawl(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		Settlement s = town(p);
		NpcService npc = HearthServices.npc();
		if (s == null || npc == null) {
			c.getSource().sendFailure(Component.literal("not in a settlement"));
			return 0;
		}
		List<FolkHandle> two = new ArrayList<>();
		for (FolkHandle h : npc.folkNear((ServerLevel) p.level(), p.position(), 24)) {
			if (h.settlement().id() == s.id() && h.awake() && h.resident().age == dev.voidmc.hearth.api.AgeStage.ADULT
				&& h.resident().job != dev.voidmc.hearth.api.JobType.GUARD && CrowdDirector.available(h)) {
				two.add(h);
				if (two.size() == 2) {
					break;
				}
			}
		}
		if (two.size() < 2 || !Crowds.brawls().start(s, two.get(0).resident().id, two.get(1).resident().id, "dev")) {
			c.getSource().sendFailure(Component.literal("no two folk free to brawl near you"));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal(two.get(0).resident().fullName() + " and " + two.get(1).resident().fullName() + " come to blows"),
			false);
		return 1;
	}

	private static Settlement town(ServerPlayer p) {
		HearthWorld w = HearthWorld.of(p.level());
		return w == null ? null : w.settlementAt(p.blockPosition());
	}
}
