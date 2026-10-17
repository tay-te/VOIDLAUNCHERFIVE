package dev.voidmc.reel.studio;

import dev.voidmc.reel.client.ControlServer;
import dev.voidmc.reel.client.Director;
import dev.voidmc.reel.client.WelkinDesk;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

/**
 * The VOID Welkin studio (-Pwelkin.studio=true, run by tools/wstudio.sh): one world kept open on the control port
 * (-Preel.control) so a shader pack fix is checked by edit, POST /reload, POST /shoot, in seconds.
 *
 * <p>It is set up the way GloamShowcase's lighting bench shot its views, so studio frames line up with the bench's:
 * seed 8675309 (welkin.seed), render distance 12 (welkin.distance), clouds fancy, HUD hidden, FOV 70, vsync off, the
 * day and the weather stopped, the player a spectator. Gloam's gloam.test.* and VOID LOD's void.lod.* properties pass
 * through from Gradle. Before it says ready (GET /studio), it stands at the warm-up view (welkin.warm.tp at
 * welkin.warm.time, the bench's plain at 16:30 by default) until the chunks and the far tiles round it are drawn.
 * Once ready the world's ticks stay frozen between shots (welkin.hold, default true), and a shot that moves or changes
 * the clock or the weather runs them a settle's worth first (see WelkinDesk). It stays open
 * welkin.minutes (default 180), or until POST /quit.
 */
public class WelkinStudio implements FabricClientGameTest {
	private static final int FOREVER = Integer.MAX_VALUE / 2;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Boolean.getBoolean("welkin.studio")) {
			return;
		}
		long begin = System.currentTimeMillis();
		long seed = Long.getLong("welkin.seed", 8675309L);
		int distance = Integer.getInteger("welkin.distance", 12);
		int minutes = Integer.getInteger("welkin.minutes", 180);
		String warmTp = System.getProperty("welkin.warm.tp", "tp @a -1009 148 -1386 180.0 6.0");
		long warmTime = Long.getLong("welkin.warm.time", 154500L);
		if (System.getProperty("reel.control") == null) {
			throw new IllegalStateException("the Welkin studio needs a control port (-Preel.control=7351)");
		}
		WelkinDesk.state("world");
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
			mc.options.renderDistance().set(distance);
			mc.options.enableVsync().set(false);
			mc.options.framerateLimit().set(260);
			mc.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
			mc.options.pauseOnLostFocus = false;
			mc.options.fov().set(70);
			// Fabric's game tests switch the clouds off; the bench had them as a player does
			mc.options.cloudStatus().set(CloudStatus.FANCY);
		});
		try (TestSingleplayerContext world = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> {
				s.setSeed(Long.toString(seed));
				s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
				s.setAllowCommands(true);
			})
			.create()) {
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("gamerule advance_weather false");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("gamemode spectator @a");
			world.getServer().runCommand("time set " + warmTime);
			world.getServer().runCommand(warmTp);
			System.out.printf(Locale.ROOT, "[welkin] studio world seed %d, distance %d, warming at %s (time %d) after %.1f s%n", seed, distance, warmTp, warmTime,
				(System.currentTimeMillis() - begin) / 1000.0);
			WelkinDesk.state("settling");
			long settleStart = System.currentTimeMillis(), until = settleStart + 600_000;
			int streak = 0;
			while (System.currentTimeMillis() < until && streak < 5) {
				context.waitTicks(10);
				streak = context.computeOnClient(WelkinDesk::viewReady) ? streak + 1 : 0;
			}
			String loading = context.computeOnClient(WelkinDesk::loading);
			System.out.printf(Locale.ROOT, "[welkin] studio settled in %.1f s (%s): %s%n", (System.currentTimeMillis() - settleStart) / 1000.0,
				streak >= 5 ? "ready" : "timed out", loading);
			if (!"false".equals(System.getProperty("welkin.hold"))) {
				// the world holds still between shots (WelkinDesk: the clouds and the sway stay put across a reload)
				world.getServer().runCommand("tick freeze");
			}
			// what the held world is settled for (its water ran while the studio warmed up) and the game time it holds at
			WelkinDesk.ready(warmTp, warmTime, "clear", world.getServer().computeOnServer(server -> server.overworld().getGameTime()));
			WelkinDesk.state("ready");
			System.out.printf(Locale.ROOT, "[welkin] studio ready %.1f s after launch, open for %d min; POST /quit to close%n",
				(System.currentTimeMillis() - begin) / 1000.0, minutes);
			long close = System.currentTimeMillis() + minutes * 60_000L;
			context.waitFor(mc -> ControlServer.quitRequested() || System.currentTimeMillis() > close, FOREVER);
			WelkinDesk.state("closing");
			context.runOnClient(mc -> Director.get().stop(mc));
		}
		// the port's dispatcher is not a daemon thread: left open, it would keep the game from exiting
		ControlServer.stop();
	}
}
