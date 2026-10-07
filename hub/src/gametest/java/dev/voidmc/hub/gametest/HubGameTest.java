package dev.voidmc.hub.gametest;

import dev.voidmc.hub.HubConfig;
import dev.voidmc.hub.screen.HubChestScreen;
import dev.voidmc.hub.screen.HubInventoryScreen;
import dev.voidmc.hub.ui.HubStats;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

/**
 * The VOID Hub in-game test. One entry point, three modes ({@code -Dvoidhub.test}):
 *
 * <ul>
 *   <li>{@code functional} — real clicks through the real input path, asserting the real inventory
 *       (see {@link FunctionalTest});</li>
 *   <li>{@code perf} — the regression gate (see {@link PerfTest});</li>
 *   <li>{@code smoke} — open both screens and photograph them;</li>
 *   <li>{@code all} (default) — functional, then perf.</li>
 * </ul>
 */
public class HubGameTest implements FabricClientGameTest {

	public static final int WIDTH = Integer.getInteger("voidhub.test.width", 1920);
	public static final int HEIGHT = Integer.getInteger("voidhub.test.height", 1080);

	@Override
	public void runTest(ClientGameTestContext context) {
		String mode = System.getProperty("voidhub.test", "all");
		context.runOnClient(mc -> {
			mc.options.enableVsync().set(false);
			mc.options.framerateLimit().set(260);  // 260 = unlimited
			mc.options.inactivityFpsLimit().set(InactivityFpsLimit.AFK);
			mc.options.pauseOnLostFocus = false;
			mc.options.guiScale().set(0);
		});
		context.getInput().resizeWindow(WIDTH, HEIGHT);
		HubConfig.setReplaceInventory(true);

		try (TestSingleplayerContext world = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> {
				s.setSeed("8675309");
				s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
				s.setAllowCommands(true);
			})
			.create()) {
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("gamerule advance_weather false");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("time set noon");
			TestWorld.Spot spot = TestWorld.build(world);
			TestWorld.fillPlayer(world);
			world.getConnection().waitForChunksRender();
			context.waitTicks(20);
			quiet(context);

			try {
				if (mode.equals("smoke")) {
					this.smoke(context, world, spot);
				}
				if (mode.equals("functional") || mode.equals("all")) {
					new FunctionalTest(context, world, spot).run();
					new MapTest(context, world).run();
				}
				if (mode.equals("map")) {
					new MapTest(context, world).run();
				}
				if (mode.equals("perf") || mode.equals("all")) {
					TestWorld.fillPlayer(world);
					context.waitTicks(5);
					quiet(context);
					new PerfTest(context, world, spot).run();
				}
			} catch (Throwable t) {
				halt(t);
				throw t;
			}
			halt(null);
		}
	}

	/**
	 * The gate's own tasks end the game here, results already written: closing the test world
	 * sometimes deadlocks in IntegratedServer.halt under the client game test, which would hold the
	 * task (and the machine's game slot) until a timeout. Off unless {@code voidhub.test.halt}.
	 */
	private static void halt(Throwable failure) {
		if (!Boolean.getBoolean("voidhub.test.halt")) {
			return;
		}
		if (failure != null) {
			System.err.println("[voidhub] FAILED: " + failure);
			failure.printStackTrace();
		}
		System.out.println("[voidhub] test done, halting the game (" + (failure == null ? "pass" : "fail") + ")");
		System.out.flush();
		System.err.flush();
		Runtime.getRuntime().halt(failure == null ? 0 : 1);
	}

	/**
	 * Holds the third hotbar slot (as in the design) and clears the advancement / recipe toasts
	 * that filling the inventory raised: they would sit over the screenshots and cost frames in
	 * whichever scenario happened to be measured while they slid.
	 */
	static void quiet(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			mc.player.getInventory().setSelectedSlot(2);
			mc.gui.toastManager().clear();
		});
		context.waitTicks(2);
	}

	private void smoke(ClientGameTestContext context, TestSingleplayerContext world, TestWorld.Spot spot) {
		Ui ui = new Ui(context, world);
		ui.pressInventory();
		context.waitForScreen(HubInventoryScreen.class);
		context.waitTicks(10);
		ui.parkMouse();
		context.waitTicks(2);
		context.takeScreenshot("voidhub_inventory");
		ui.hoverSlot(36);
		context.waitTicks(3);
		context.takeScreenshot("voidhub_inventory_hover_sword");
		context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
		ui.openChest(spot.chest());
		context.waitForScreen(HubChestScreen.class);
		context.waitTicks(10);
		ui.hoverSlot(11);
		context.waitTicks(3);
		context.takeScreenshot("voidhub_chest");
		System.out.println("[voidhub] init " + HubStats.INSTANCE.initTotalNs / 1e6 + " ms");
	}
}
