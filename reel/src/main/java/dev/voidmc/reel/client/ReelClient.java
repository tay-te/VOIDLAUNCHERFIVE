package dev.voidmc.reel.client;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.voidmc.reel.Reel;
import dev.voidmc.reel.shot.CameraState;
import dev.voidmc.reel.shot.Shot;
import dev.voidmc.reel.shot.ShotException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * The client side of Reel: the /reel commands, the spectator body that follows the camera (so the server loads
 * chunks where the camera is), and, with -Dreel.control=&lt;port&gt;, the local control port that tools/reel.sh
 * drives.
 *
 * <pre>
 * /reel play &lt;shot&gt;              real-time preview, nothing captured
 * /reel render &lt;shot&gt;            every frame, encoded if ffmpeg is about
 * /reel stills &lt;shot&gt; [count]    a few frames spread over the shot, and a contact sheet
 * /reel stop
 * /reel key                      logs (and appends to shots/_keys.jsonl) a key for where you stand and look
 * /reel shots                    lists the shots
 * </pre>
 */
public final class ReelClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Director director = Director.get();
		director.listen(message -> {
			Minecraft mc = Minecraft.getInstance();
			mc.execute(() -> {
				if (mc.player != null) {
					mc.player.sendSystemMessage(Component.literal("[reel] " + message));
				}
			});
		});
		ClientTickEvents.START_CLIENT_TICK.register(ReelClient::followCamera);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(ClientCommands.literal("reel")
			.then(ClientCommands.literal("play").then(ClientCommands.argument("shot", StringArgumentType.word())
				.executes(c -> withShot(c, shot -> director.play(Minecraft.getInstance(), shot)))))
			.then(ClientCommands.literal("render").then(ClientCommands.argument("shot", StringArgumentType.word())
				.executes(c -> withShot(c, shot -> director.start(Minecraft.getInstance(), RenderJob.movie(shot, Reel.renders(), null, null, null, true))))))
			.then(ClientCommands.literal("stills").then(ClientCommands.argument("shot", StringArgumentType.word())
				.executes(c -> withShot(c, shot -> director.start(Minecraft.getInstance(), RenderJob.stills(shot, Reel.renders(), 8, null, null))))
				.then(ClientCommands.argument("count", IntegerArgumentType.integer(1, 64))
					.executes(c -> withShot(c, shot -> director.start(Minecraft.getInstance(),
						RenderJob.stills(shot, Reel.renders(), IntegerArgumentType.getInteger(c, "count"), null, null)))))))
			.then(ClientCommands.literal("stop").executes(c -> {
				director.stop(Minecraft.getInstance());
				return 1;
			}))
			.then(ClientCommands.literal("key").executes(c -> key(c.getSource(), null))
				.then(ClientCommands.argument("lookDistance", DoubleArgumentType.doubleArg(1, 4096))
					.executes(c -> key(c.getSource(), DoubleArgumentType.getDouble(c, "lookDistance")))))
			.then(ClientCommands.literal("shots").executes(c -> {
				c.getSource().sendFeedback(Component.literal("[reel] " + String.join(", ", Reel.shotNames()) + " in " + Reel.shots()));
				return 1;
			}))));

		String port = System.getProperty("reel.control");
		if (port != null) {
			try {
				ControlServer.start(Integer.parseInt(port));
			} catch (IOException | NumberFormatException e) {
				Reel.LOG.error("[reel] control port {} failed", port, e);
			}
		}
	}

	private interface ShotAction {
		void run(Shot shot);
	}

	private static int withShot(CommandContext<FabricClientCommandSource> c, ShotAction action) {
		String name = StringArgumentType.getString(c, "shot");
		try {
			action.run(Reel.shot(name));
			return 1;
		} catch (IOException | ShotException e) {
			c.getSource().sendError(Component.literal("[reel] " + name + ": " + e.getMessage()));
			return 0;
		}
	}

	/** A key line for where the player's eye is and how it looks, optionally with the point looked at. */
	private static int key(FabricClientCommandSource source, Double lookDistance) {
		LocalPlayer player = source.getPlayer();
		Vec3 eye = player.getEyePosition();
		String line = String.format(Locale.ROOT, "{\"pos\": [%.2f, %.2f, %.2f], \"yaw\": %.1f, \"pitch\": %.1f, \"fov\": %d", eye.x, eye.y, eye.z,
			player.getYRot(), player.getXRot(), Minecraft.getInstance().options.fov().get());
		if (lookDistance != null) {
			Vec3 at = eye.add(player.getLookAngle().scale(lookDistance));
			line += String.format(Locale.ROOT, ", \"look\": [%.1f, %.1f, %.1f]", at.x, at.y, at.z);
		}
		line += "}";
		try {
			Files.writeString(Reel.shots().resolve("_keys.jsonl"), line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			Reel.LOG.warn("[reel] could not append the key", e);
		}
		Reel.LOG.info("[reel] key {}", line);
		source.sendFeedback(Component.literal("[reel] " + line));
		return 1;
	}

	/** Keeps the (spectator) player's body at the camera, so the chunks around the camera are the ones loaded. */
	private static void followCamera(Minecraft mc) {
		CameraState state = Director.get().camera();
		LocalPlayer player = mc.player;
		if (state == null || player == null || !Stage.follows(player)) {
			return;
		}
		player.snapTo(state.pos().x(), state.pos().y() - player.getEyeHeight(), state.pos().z(), (float) state.yaw(), (float) state.pitch());
		player.setDeltaMovement(Vec3.ZERO);
	}
}
