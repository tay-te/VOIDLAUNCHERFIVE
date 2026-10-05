package dev.voidmc.hearth.gametest;

import dev.voidmc.hearth.sim.dev.DevHarness;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

/**
 * The server-side harnesses (the sim's {@code -Dhearth.dev.*}, the living world's {@code hearth.dev.living}, and the
 * standing, law and repair self-checks) run in the client's integrated server: a world on the dev seed, the player parked
 * high and far from the towns so they stay abstract, waiting until the sim's harness says it is done. For machines where
 * the dedicated server cannot be started. {@code -Dhearth.tour=living} runs only this.
 */
public class LivingRun implements FabricClientGameTest {
	private static final long MINUTES = Long.getLong("hearth.living.minutes", 45);

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Tours.wanted("living")) {
			return;
		}
		context.runOnClient(mc -> mc.options.renderDistance().set(4));
		try (TestSingleplayerContext world = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> {
				s.setSeed("20261003");
				s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
				s.setAllowCommands(true);
			})
			.create()) {
			// out of every town's reach (embodiment is 128 blocks from a town's bounds): towns stay abstract and cheap
			world.getServer().runCommand("gamemode spectator @a");
			world.getServer().runCommand("tp @a 30000 300 30000");
			long until = System.currentTimeMillis() + MINUTES * 60_000;
			while (!DevHarness.finished && System.currentTimeMillis() < until) {
				context.waitTick();
			}
			// let the other modules' self-checks finish what they started
			long extra = System.currentTimeMillis() + Long.getLong("hearth.living.after", 60_000L);
			while (System.currentTimeMillis() < extra) {
				context.waitTick();
			}
			System.out.println("[hearth living] harness finished: " + DevHarness.finished);
		}
	}
}
