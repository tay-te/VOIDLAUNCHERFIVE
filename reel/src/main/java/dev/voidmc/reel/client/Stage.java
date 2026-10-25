package dev.voidmc.reel.client;

import dev.voidmc.reel.Reel;
import dev.voidmc.reel.math.Vec3;
import dev.voidmc.reel.shot.CameraState;
import dev.voidmc.reel.shot.Shot;
import java.util.Locale;
import net.minecraft.client.CameraType;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.GameType;

/**
 * Sets the world and the client up for filming and puts everything back afterwards: no HUD, no view bob or hurt
 * tilt, no dynamic FOV, no chunk fade-in, first person, a spectator body that follows the camera so chunks load
 * around it, and the shot's time of day and weather (through the integrated server: shots are filmed in single
 * player).
 */
final class Stage {
	private double fovEffect;
	private boolean bob;
	private double tilt;
	private double screenEffects;
	private double chunkFade;
	private boolean pauseOnLostFocus;
	private boolean vsync;
	private int framerate;
	private InactivityFpsLimit inactivity;
	private CameraType cameraType;
	private boolean hudWasHidden;
	private GameType gameType;
	private boolean performing;
	private boolean timeTouched;
	private boolean frozeWorld;

	/** Saves what filming changes, then changes it. */
	void enter(Minecraft mc, Shot shot, CameraState first, boolean freeze) {
		Options o = mc.options;
		this.fovEffect = o.fovEffectScale().get();
		this.bob = o.bobView().get();
		this.tilt = o.damageTiltStrength().get();
		this.screenEffects = o.screenEffectScale().get();
		this.chunkFade = o.chunkSectionFadeInTime().get();
		this.pauseOnLostFocus = o.pauseOnLostFocus;
		this.vsync = o.enableVsync().get();
		this.framerate = o.framerateLimit().get();
		this.inactivity = o.inactivityFpsLimit().get();
		this.cameraType = o.getCameraType();
		this.hudWasHidden = mc.gui.hud.isHidden();
		this.gameType = mc.gameMode != null ? mc.gameMode.getPlayerMode() : GameType.CREATIVE;

		o.fovEffectScale().set(0.0);
		o.bobView().set(false);
		o.damageTiltStrength().set(0.0);
		o.screenEffectScale().set(0.0);
		o.chunkSectionFadeInTime().set(0.0);
		o.pauseOnLostFocus = false;
		o.enableVsync().set(false);
		o.framerateLimit().set(260);
		o.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
		o.setCameraType(CameraType.FIRST_PERSON);
		if (!this.hudWasHidden) {
			mc.gui.hud.toggle();
		}

		Vec3 p = first.pos();
		double feet = p.y() - (mc.player != null ? mc.player.getEyeHeight() : 1.62);
		// a recorded movie's body is an invisible flier rather than a spectator: towns (Hearth) bring their people out
		// only around a player who is in the world
		this.performing = ActorRecorder.armed();
		server(mc, this.performing ? "gamemode creative @a" : "gamemode spectator @a",
			String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f %.2f %.2f", p.x(), feet, p.z(), first.yaw(), first.pitch()));
		if (this.performing) {
			server(mc, "effect give @a minecraft:invisibility infinite 0 true");
		}
		Shot.World world = shot.world();
		if (world.weather() != null) {
			server(mc, "gamerule advance_weather false", "weather " + world.weather());
		}
		if (world.time() != null) {
			// the server's clock stops at the shot's start; the director sets the client's to the exact instant of every frame
			this.timeTouched = true;
			server(mc, "time set " + (long) Math.floor(world.time()), "time pause");
		}
		this.frozeWorld = freeze;
		if (freeze) {
			server(mc, "tick freeze");
		}
	}

	void unfreeze(Minecraft mc) {
		if (this.frozeWorld) {
			this.frozeWorld = false;
			server(mc, "tick unfreeze");
		}
	}

	void freeze(Minecraft mc) {
		if (!this.frozeWorld) {
			this.frozeWorld = true;
			server(mc, "tick freeze");
		}
	}

	void leave(Minecraft mc) {
		Options o = mc.options;
		o.fovEffectScale().set(this.fovEffect);
		o.bobView().set(this.bob);
		o.damageTiltStrength().set(this.tilt);
		o.screenEffectScale().set(this.screenEffects);
		o.chunkSectionFadeInTime().set(this.chunkFade);
		o.pauseOnLostFocus = this.pauseOnLostFocus;
		o.enableVsync().set(this.vsync);
		o.framerateLimit().set(this.framerate);
		o.inactivityFpsLimit().set(this.inactivity);
		o.setCameraType(this.cameraType);
		if (mc.gui.hud.isHidden() != this.hudWasHidden) {
			mc.gui.hud.toggle();
		}
		this.unfreeze(mc);
		if (this.timeTouched) {
			server(mc, "time resume");
		}
		if (this.performing) {
			this.performing = false;
			server(mc, "effect clear @a minecraft:invisibility");
		}
		server(mc, "gamemode " + this.gameType.getName() + " @a");
	}

	/** Whether the player is the body that follows the camera: a spectator, or a recorded movie's invisible flier. */
	static boolean follows(net.minecraft.client.player.LocalPlayer player) {
		if (player.isSpectator()) {
			return true;
		}
		if (ActorRecorder.armed() && player.isCreative()) {
			player.getAbilities().flying = true;
			return true;
		}
		return false;
	}

	/** Runs commands on the integrated server as its console, quietly; does nothing (but say so) in multiplayer. */
	static void server(Minecraft mc, String... commands) {
		IntegratedServer server = mc.getSingleplayerServer();
		if (server == null) {
			Reel.LOG.warn("[reel] not in single player: skipped {}", String.join("; ", commands));
			return;
		}
		server.execute(() -> {
			var source = server.createCommandSourceStack().withSuppressedOutput();
			for (String command : commands) {
				server.getCommands().performPrefixedCommand(source, command);
			}
		});
	}
}
