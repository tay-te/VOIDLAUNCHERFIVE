package dev.voidmc.reel.studio;

import dev.voidmc.reel.Reel;
import dev.voidmc.reel.client.ControlServer;
import dev.voidmc.reel.client.Director;
import dev.voidmc.reel.client.Encoder;
import dev.voidmc.reel.client.RenderJob;
import dev.voidmc.reel.shot.Shot;
import dev.voidmc.reel.shot.ShotReport;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The studio: the game run as a client game test, where Fabric's harness ticks the integrated server in lockstep
 * with the client, so a filmed world moves exactly as it would in real time however slowly frames are drawn. It
 * makes a world (-Preel.seed, Earth terrain with -Pexpanse), then:
 *
 * <ul>
 * <li>-Preel.shots=a,b films each shot (-Preel.mode=stills for contact sheets instead of movies);</li>
 * <li>-Preel.studio=&lt;minutes&gt; stays open that long on the control port (-Preel.control, default 7341) for
 * tools/reel.sh, until POST /quit.</li>
 * </ul>
 *
 * <p>Run it through tools/studio.sh, which holds one of the machine's game slots while it runs.
 */
public class ReelStudio implements FabricClientGameTest {
	private static final long SEED = Long.getLong("reel.seed", 8675309L);
	private static final int DISTANCE = Integer.getInteger("reel.distance", 16);
	private static final int FOREVER = Integer.MAX_VALUE / 2;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (Boolean.getBoolean("welkin.studio")) {
			return; // the VOID Welkin studio runs instead (WelkinStudio)
		}
		List<String> shots = Arrays.stream(System.getProperty("reel.shots", "").split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
		boolean stills = "stills".equals(System.getProperty("reel.mode"));
		int studioMinutes = Integer.getInteger("reel.studio", 0);
		if (studioMinutes > 0 && System.getProperty("reel.control") == null) {
			try {
				ControlServer.start(7341);
			} catch (java.io.IOException e) {
				throw new IllegalStateException("control port 7341 is taken", e);
			}
		}
		context.runOnClient(mc -> mc.options.renderDistance().set(DISTANCE));
		try (TestSingleplayerContext world = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> {
				s.setSeed(Long.toString(SEED));
				s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
				s.setAllowCommands(true);
			})
			.create()) {
			world.getServer().runCommand("gamerule advance_weather false");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("gamemode spectator @a");
			String spawn = world.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				BlockPos at = level.getRespawnData().globalPos().pos();
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ());
				return String.format(Locale.ROOT, "%d %d %d (ground %d)", at.getX(), at.getY(), at.getZ(), top);
			});
			System.out.println("[reel] studio world seed " + SEED + ", spawn " + spawn);
			context.waitTicks(40);

			for (String name : shots) {
				Shot shot;
				try {
					shot = Reel.shot(name);
				} catch (Exception e) {
					System.out.println("[reel] skipped " + name + ": " + e.getMessage());
					continue;
				}
				System.out.print(ShotReport.of(shot).text());
				RenderJob job = stills ? RenderJob.stills(shot, Reel.renders(), 8, null, null)
					: RenderJob.movie(shot, Reel.renders(), null, null, null, true);
				film(context, job);
			}

			if (studioMinutes > 0) {
				long until = System.currentTimeMillis() + studioMinutes * 60_000L;
				System.out.println("[reel] studio open for " + studioMinutes + " min on the control port; POST /quit to close");
				context.waitFor(mc -> ControlServer.quitRequested() || System.currentTimeMillis() > until, FOREVER);
				context.runOnClient(mc -> Director.get().stop(mc));
			}
		}
		// let the last encode finish before the game closes
		long deadline = System.currentTimeMillis() + 600_000;
		while (Encoder.running() > 0 && System.currentTimeMillis() < deadline) {
			context.waitTicks(20);
		}
	}

	private static void film(ClientGameTestContext context, RenderJob job) {
		System.out.println("[reel] filming " + job.describe());
		context.runOnClient(mc -> Director.get().start(mc, job));
		context.waitFor(mc -> !Director.get().busy(), FOREVER);
	}
}
