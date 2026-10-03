package dev.voidmc.expanse.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.voidmc.expanse.Expanse;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code /expanse atlas [radius] [blocksPerPixel]} — writes a biome-and-elevation map of the area round
 * the caller to {@code <world>/expanse_atlas/}. It runs off the server thread: sampling a few hundred
 * thousand columns takes a while, and the world keeps ticking meanwhile.
 */
public final class ExpanseCommands {
	private ExpanseCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("expanse")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("atlas")
				.executes(c -> atlas(c, 4096, 16))
				.then(Commands.argument("radius", IntegerArgumentType.integer(256, 32768))
					.executes(c -> atlas(c, IntegerArgumentType.getInteger(c, "radius"), Math.max(4, IntegerArgumentType.getInteger(c, "radius") / 256)))
					.then(Commands.argument("blocksPerPixel", IntegerArgumentType.integer(1, 256))
						.executes(c -> atlas(c, IntegerArgumentType.getInteger(c, "radius"), IntegerArgumentType.getInteger(c, "blocksPerPixel")))))));
	}

	private static int atlas(CommandContext<CommandSourceStack> c, int radius, int step) {
		CommandSourceStack source = c.getSource();
		ServerLevel level = source.getLevel();
		int x = (int) source.getPosition().x;
		int z = (int) source.getPosition().z;
		source.sendSuccess(() -> Component.literal("Charting " + (radius * 2) + " blocks square…"), false);
		Thread worker = new Thread(() -> {
			try {
				Atlas.Result r = Atlas.render(level, x, z, radius, step);
				source.getServer().execute(() -> source.sendSuccess(
					() -> Component.translatable("commands.expanse.atlas.done", r.image().toString()), false));
			} catch (Exception e) {
				Expanse.LOG.error("Atlas failed", e);
				source.getServer().execute(() -> source.sendFailure(Component.literal("Atlas failed: " + e)));
			}
		}, "expanse-atlas");
		worker.setDaemon(true);
		worker.start();
		return 1;
	}
}
