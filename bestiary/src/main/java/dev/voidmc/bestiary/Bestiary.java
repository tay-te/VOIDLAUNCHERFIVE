package dev.voidmc.bestiary;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.voidmc.fray.motion.MotionServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * VOID Bestiary. Stage 0 carries the animation prototype's content (the clips in {@code src/main/motion}) and the
 * studio commands:
 * <pre>
 * /bestiary motion play &lt;targets&gt; &lt;clip&gt; [speed]   start a clip on the server (timeline, hit volumes, broadcast)
 * /bestiary motion stop &lt;targets&gt;
 * </pre>
 */
public final class Bestiary implements ModInitializer {
	public static final String ID = "bestiary";
	public static final Logger LOGGER = LoggerFactory.getLogger("VOID Bestiary");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(ID, path);
	}

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal(ID)
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("motion")
				.then(Commands.literal("play")
					.then(Commands.argument("targets", EntityArgument.entities())
						.then(Commands.argument("clip", IdentifierArgument.id())
							.executes(c -> play(c, 1))
							.then(Commands.argument("speed", FloatArgumentType.floatArg(0.1F, 4F))
								.executes(c -> play(c, FloatArgumentType.getFloat(c, "speed")))))))
				.then(Commands.literal("stop")
					.then(Commands.argument("targets", EntityArgument.entities())
						.executes(c -> {
							int n = 0;
							for (Entity e : EntityArgument.getEntities(c, "targets")) {
								if (e instanceof LivingEntity le) {
									MotionServer.stop(le, 4);
									n++;
								}
							}
							int stopped = n;
							c.getSource().sendSuccess(() -> Component.literal("stopped " + stopped), false);
							return n;
						}))))));
	}

	private static int play(CommandContext<CommandSourceStack> c, float speed) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		Identifier clip = IdentifierArgument.getId(c, "clip");
		if (MotionServer.timeline(clip) == null) {
			c.getSource().sendFailure(Component.literal("no clip " + clip));
			return 0;
		}
		int n = 0;
		for (Entity e : EntityArgument.getEntities(c, "targets")) {
			if (e instanceof LivingEntity le && MotionServer.play(le, clip, speed)) {
				n++;
			}
		}
		int played = n;
		c.getSource().sendSuccess(() -> Component.literal("playing " + clip + " on " + played), false);
		return n;
	}
}
