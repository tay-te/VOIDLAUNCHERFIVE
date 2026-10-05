package dev.voidmc.mettle.character;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import dev.voidmc.mettle.api.Background;
import dev.voidmc.mettle.api.CharacterAttribute;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /mettle info [player]} for anyone about themself, op for others; {@code /mettle background reset|set} for ops.
 */
public final class CharacterCommands {
	private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(id -> Component.translatable("mettle.command.unknown_background", String.valueOf(id)));

	private CharacterCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
		dispatcher.register(Commands.literal("mettle")
			.then(Commands.literal("info")
				.executes(c -> info(c, c.getSource().getPlayerOrException()))
				.then(Commands.argument("player", EntityArgument.player())
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.executes(c -> info(c, EntityArgument.getPlayer(c, "player")))))
			.then(Commands.literal("background")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("reset")
					.executes(c -> reset(c, List.of(c.getSource().getPlayerOrException())))
					.then(Commands.argument("players", EntityArgument.players())
						.executes(c -> reset(c, EntityArgument.getPlayers(c, "players")))))
				.then(Commands.literal("set")
					.then(Commands.argument("background", ResourceKeyArgument.key(MettleRegistries.BACKGROUND))
						.executes(c -> set(c, List.of(c.getSource().getPlayerOrException())))
						.then(Commands.argument("players", EntityArgument.players())
							.executes(c -> set(c, EntityArgument.getPlayers(c, "players"))))))));
	}

	private static int info(CommandContext<CommandSourceStack> c, ServerPlayer player) {
		CharacterState state = CharacterStore.get(player);
		Background background = CharacterStore.background(player.level().registryAccess(), state);
		Component name = background == null ? Component.translatable("mettle.command.undecided") : background.name();
		StringBuilder attributes = new StringBuilder();
		for (CharacterAttribute a : CharacterAttribute.values()) {
			if (!attributes.isEmpty()) {
				attributes.append("  ");
			}
			attributes.append(a.displayName().getString()).append(' ').append(state.attribute(a, background));
		}
		c.getSource().sendSuccess(() -> Component.translatable("mettle.command.info", player.getDisplayName(), name, attributes.toString()), false);
		return 1;
	}

	private static int reset(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) {
		players.forEach(Backgrounds::reset);
		c.getSource().sendSuccess(() -> Component.translatable("mettle.command.reset", players.size()), true);
		return players.size();
	}

	private static int set(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) throws CommandSyntaxException {
		ResourceKey<Background> key = ResourceKeyArgument.getRegistryKey(c, "background", MettleRegistries.BACKGROUND, UNKNOWN);
		if (c.getSource().registryAccess().lookupOrThrow(MettleRegistries.BACKGROUND).getOptional(key).isEmpty()) {
			throw UNKNOWN.create(key.identifier());
		}
		players.forEach(p -> Backgrounds.force(p, key));
		c.getSource().sendSuccess(() -> Component.translatable("mettle.command.set", players.size(), key.identifier().toString()), true);
		return players.size();
	}
}
