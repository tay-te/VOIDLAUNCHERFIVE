package dev.voidmc.hearth.social.rhythm;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.life.TownLife;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code /hearth rhythm status} (what the rhythm is doing in embodied towns, its timings) and, for game masters,
 * {@code /hearth rhythm day <rest|wash|plain|calendar>}: hold a rest day or a wash day (or neither) in the town here for
 * today, or give it back to its calendar (tours and testing).
 */
final class RhythmCommands {
	private static final SimpleCommandExceptionType NOT_IN_TOWN = new SimpleCommandExceptionType(Component.translatable("hearth.social.command.not_in_town"));

	private RhythmCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("hearth").then(Commands.literal("rhythm")
			.then(Commands.literal("status").executes(c -> {
				for (String line : RhythmDirector.stats()) {
					c.getSource().sendSuccess(() -> Component.literal(line), false);
				}
				return 1;
			}))
			.then(Commands.literal("day").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("rest").executes(c -> force(c, RhythmCalendar.Forced.REST)))
				.then(Commands.literal("wash").executes(c -> force(c, RhythmCalendar.Forced.WASH)))
				.then(Commands.literal("plain").executes(c -> force(c, RhythmCalendar.Forced.PLAIN)))
				.then(Commands.literal("calendar").executes(c -> force(c, null))))));
	}

	private static int force(CommandContext<CommandSourceStack> c, RhythmCalendar.Forced kind) throws CommandSyntaxException {
		Settlement s = here(c);
		hold(s, kind);
		c.getSource().sendSuccess(() -> Component.literal(s.name() + ": " + (kind == null ? "back to its calendar" : kind.name().toLowerCase(java.util.Locale.ROOT)
			+ " day today")), true);
		return 1;
	}

	/** Holds a day in one town (null: its calendar again) and forgets what was worked out for today. */
	static void hold(Settlement s, RhythmCalendar.Forced kind) {
		RhythmCalendar.force(s.id(), TownLife.today(s), kind);
		Service.forget(s.id());
		RhythmSchedule.clear();
		WashBehaviour.clear();
		TownLife.refresh(s);
	}

	private static Settlement here(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerLevel level = c.getSource().getLevel();
		HearthWorld w = HearthWorld.of(level);
		BlockPos at = BlockPos.containing(c.getSource().getPosition());
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
