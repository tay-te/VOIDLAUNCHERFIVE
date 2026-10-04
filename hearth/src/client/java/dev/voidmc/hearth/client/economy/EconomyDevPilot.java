package dev.voidmc.hearth.client.economy;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.economy.bank.BankMenu;
import dev.voidmc.hearth.economy.bank.BankView;
import dev.voidmc.hearth.economy.market.Market;
import dev.voidmc.hearth.economy.market.MarketMenu;
import dev.voidmc.hearth.economy.net.EconomyActionPayload;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Dev tooling, off unless the environment variable {@code HEARTH_DEV_ECONOMY=shots} is set: drives a dev client through
 * the economy without anyone at the keyboard — makes a creative test world, fills the hotbar with coins, opens the
 * sandbox market, buys, reports a builders' shortage and sells stone into it, opens the bank and buys a deed, then (phase 3)
 * opens the baker's and the tailor's shops, haggles (an insult, then a fair offer), visits the fence, buys a business,
 * funds a building and takes a supply contract — and saves
 * a screenshot of each step to {@code run/screenshots/economy/}, then quits.
 *
 * <pre>HEARTH_DEV_ECONOMY=shots ./gradlew runClient</pre>
 */
public final class EconomyDevPilot {
	private static final int SETTLE = 30;
	private final List<Step> steps = new ArrayList<>();
	private int step;
	private int wait;
	private boolean worldRequested;

	private EconomyDevPilot() {
		script();
	}

	public static void initIfRequested() {
		if ("shots".equals(System.getenv("HEARTH_DEV_ECONOMY")) || Boolean.getBoolean("hearth.dev.economy.shots")) {
			EconomyDevPilot pilot = new EconomyDevPilot();
			ClientTickEvents.END_CLIENT_TICK.register(pilot::tick);
			Hearth.LOG.info("Economy dev pilot: on");
		}
	}

	private void script() {
		run(40, c -> {
			c.getTutorial().setStep(TutorialSteps.NONE);
			command(c, "gamemode survival");
			command(c, "time set noon");
			command(c, "weather clear");
			String[] slots = {"hotbar.0 with hearth:copper_bit 1", "hotbar.1 with hearth:copper_bit 5", "hotbar.2 with hearth:copper_bit 20",
				"hotbar.3 with hearth:copper_bit 64", "hotbar.4 with hearth:iron_mark 1", "hotbar.5 with hearth:iron_mark 6",
				"hotbar.6 with hearth:iron_mark 30", "hotbar.7 with hearth:gold_crown 64", "hotbar.8 with hearth:coin_pouch",
				"inventory.0 with hearth:gold_crown 1", "inventory.1 with hearth:gold_crown 4", "inventory.2 with hearth:gold_crown 12",
				"inventory.3 with minecraft:cobblestone 64", "inventory.4 with minecraft:cobblestone 64", "inventory.5 with minecraft:oak_log 32",
				"inventory.6 with minecraft:emerald 10", "inventory.7 with minecraft:bread 12"};
			for (String s : slots) {
				command(c, "item replace entity @s " + s);
			}
		});
		run(20, c -> command(c, "hearth money give @s 3c4m7b"));
		run(SETTLE, c -> c.gui.setScreen(new InventoryScreen(c.player)));
		shot("inventory");
		run(5, c -> c.gui.setScreen(null));
		run(10, c -> command(c, "hearth economy sandbox karst"));
		shot("market_karst");
		run(5, c -> trade(c, 0, true));
		shot("market_after_buy");
		run(5, c -> c.player.closeContainer());
		run(5, c -> command(c, "hearth economy sandbox stall stone 600"));
		run(10, c -> command(c, "hearth economy sandbox"));
		shot("market_builders");
		run(5, c -> trade(c, Market.BUY_ID_BASE, true));
		shot("market_sold_to_builders");
		run(5, c -> c.player.closeContainer());
		run(5, c -> command(c, "hearth economy sandbox desert"));
		shot("market_desert");
		run(5, c -> c.player.closeContainer());
		run(5, c -> command(c, "hearth economy sandbox bank"));
		shot("bank");
		run(5, c -> bank(c, EconomyActionPayload.DEPOSIT, 0, 100));
		run(5, c -> bank(c, EconomyActionPayload.SELL_EMERALDS, 0, 4));
		shot("bank_after_deposit_and_changer");
		run(5, c -> c.player.closeContainer());
		run(5, c -> command(c, "hearth economy sandbox property"));
		run(SETTLE, c -> {
			if (c.gui.screen() instanceof BankScreen screen) {
				screen.devSelect(0);
			}
		});
		shot("property_selected");
		run(5, c -> {
			if (c.player.containerMenu instanceof BankMenu menu && !menu.view().plots().isEmpty()) {
				BankView.PlotOffer first = menu.view().plots().getFirst();
				bank(c, EconomyActionPayload.BUY_PLOT, first.plot(), 0);
			}
		});
		shot("property_bought");
		run(5, c -> c.player.closeContainer());

		// phase 3: shops with their own stock and till, haggling, the fence, businesses, works and contracts
		run(5, c -> command(c, "hearth economy sandbox shop 2"));
		shot("shop_bakery");
		run(5, c -> market(c, EconomyActionPayload.HAGGLE_START, 0, 1));
		shot("haggle_open");
		run(5, c -> haggleOffer(c, 0.45));
		shot("haggle_insulted");
		run(5, c -> haggleOffer(c, 0.97));
		shot("haggle_struck");
		run(5, c -> c.player.closeContainer());
		run(5, c -> command(c, "hearth economy sandbox shop 3"));
		shot("shop_tailor");
		run(5, c -> c.player.closeContainer());
		run(5, c -> command(c, "hearth economy sandbox shop 6"));
		shot("shop_fence");
		run(5, c -> c.player.closeContainer());
		run(5, c -> command(c, "hearth economy sandbox property"));
		run(SETTLE, c -> {
			if (c.gui.screen() instanceof BankScreen screen && c.player.containerMenu instanceof BankMenu menu) {
				List<BankView.PlotOffer> plots = menu.view().plots();
				for (int i = 0; i < plots.size(); i++) {
					if (plots.get(i).has(BankView.PlotOffer.BUSINESS)) {
						screen.devSelect(i);
						break;
					}
				}
			}
		});
		shot("property_business");
		run(5, c -> {
			if (c.player.containerMenu instanceof BankMenu menu) {
				menu.view().plots().stream().filter(p -> p.has(BankView.PlotOffer.BUSINESS)).findFirst()
					.ifPresent(p -> bank(c, EconomyActionPayload.BUY_PLOT, p.plot(), 0));
			}
		});
		shot("business_bought");
		run(5, c -> c.player.closeContainer());
		run(5, c -> command(c, "hearth economy sandbox works"));
		run(SETTLE, c -> {
			if (c.gui.screen() instanceof BankScreen screen) {
				screen.devTab(BankView.TAB_WORKS, 0);
			}
		});
		shot("works");
		run(5, c -> {
			if (c.player.containerMenu instanceof BankMenu menu && !menu.view().projects().isEmpty()) {
				bank(c, EconomyActionPayload.FUND, menu.view().projects().getFirst().plot(), 100);
			}
		});
		run(SETTLE, c -> {
			if (c.gui.screen() instanceof BankScreen screen) {
				screen.devTab(BankView.TAB_WORKS, 0);
			}
		});
		shot("works_funded");
		run(5, c -> c.player.closeContainer());
		run(5, c -> command(c, "hearth economy sandbox contracts"));
		run(SETTLE, c -> {
			if (c.gui.screen() instanceof BankScreen screen) {
				screen.devTab(BankView.TAB_CONTRACTS, 0);
			}
		});
		shot("contracts");
		run(5, c -> {
			if (c.player.containerMenu instanceof BankMenu menu && !menu.view().contracts().isEmpty()) {
				bank(c, EconomyActionPayload.ACCEPT_CONTRACT, menu.view().contracts().getFirst().id(), 0);
			}
		});
		run(SETTLE, c -> {
			if (c.gui.screen() instanceof BankScreen screen) {
				screen.devTab(BankView.TAB_CONTRACTS, 0);
			}
		});
		shot("contract_taken");
		run(5, c -> c.player.closeContainer());
		run(5, c -> {
			command(c, "hearth economy sandbox days 30");
			command(c, "hearth economy sandbox shops");
			command(c, "hearth economy sandbox purses");
		});
		run(10, c -> c.gui.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false)));
		shot("chat_shops_purses");
		run(5, c -> c.gui.setScreen(null));
		run(5, c -> {
			command(c, "hearth money balance");
			command(c, "hearth market \"Sandbox Desert\" prices");
		});
		run(10, c -> c.gui.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false)));
		shot("chat_prices");
		run(5, c -> c.gui.setScreen(null));
		run(5, c -> {
			command(c, "clear @s");
			for (String table : new String[] {"minecraft:chests/buried_treasure", "minecraft:chests/ancient_city", "minecraft:chests/shipwreck_treasure",
				"minecraft:chests/simple_dungeon", "expanse:chests/micro/lost_chest", "expanse:chests/caravanserai_hidden"}) {
				command(c, "loot give @s loot " + table);
			}
		});
		run(SETTLE, c -> c.gui.setScreen(new InventoryScreen(c.player)));
		shot("loot");
		run(10, Minecraft::stop);
	}

	private void run(int delay, Consumer<Minecraft> action) {
		this.steps.add(new Step(delay, action));
	}

	private void shot(String name) {
		run(SETTLE - 2, c -> c.gui.toastManager().clear());
		run(2, c -> Screenshot.takeScreenshot(c.gameRenderer.mainRenderTarget(), image -> {
			try {
				Path dir = c.gameDirectory.toPath().resolve("screenshots").resolve("economy");
				Files.createDirectories(dir);
				image.writeToFile(dir.resolve(name + ".png"));
				Hearth.LOG.info("Economy dev pilot: saved {}", name);
			} catch (Exception e) {
				Hearth.LOG.error("Economy dev pilot: could not save {}", name, e);
			} finally {
				image.close();
			}
		}));
	}

	private void tick(Minecraft c) {
		if (c.level == null || c.player == null) {
			if (!this.worldRequested && c.gui.screen() instanceof TitleScreen) {
				this.worldRequested = true;
				createWorld(c);
			}
			return;
		}
		if (this.step >= this.steps.size()) {
			return;
		}
		Step s = this.steps.get(this.step);
		if (++this.wait < s.delay) {
			return;
		}
		this.wait = 0;
		this.step++;
		try {
			s.action.accept(c);
		} catch (RuntimeException e) {
			Hearth.LOG.error("Economy dev pilot: step {} failed", this.step, e);
		}
	}

	private static void createWorld(Minecraft c) {
		CreateWorldScreen.openFresh(c, () -> c.gui.setScreen(new TitleScreen()));
		if (!(c.gui.screen() instanceof CreateWorldScreen screen)) {
			Hearth.LOG.error("Economy dev pilot: no create-world screen");
			return;
		}
		WorldCreationUiState ui = screen.getUiState();
		ui.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
		ui.setAllowCommands(true);
		ui.setSeed("4242");
		for (GuiEventListener child : screen.children()) {
			if (child instanceof Button b && b.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals("selectWorld.create")) {
				b.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
				return;
			}
		}
		Hearth.LOG.error("Economy dev pilot: no create button");
	}

	private static void command(Minecraft c, String command) {
		c.player.connection.sendCommand(command);
	}

	private static void trade(Minecraft c, int id, boolean bulk) {
		if (c.player.containerMenu instanceof MarketMenu menu) {
			ClientPlayNetworking.send(new EconomyActionPayload(menu.containerId, EconomyActionPayload.TRADE, id, bulk ? 1 : 0));
		}
	}

	private static void market(Minecraft c, int action, int index, long amount) {
		if (c.player.containerMenu instanceof MarketMenu menu) {
			ClientPlayNetworking.send(new EconomyActionPayload(menu.containerId, action, index, amount));
		}
	}

	/** Offers a share of the merchant's last word in the haggle under way. */
	private static void haggleOffer(Minecraft c, double share) {
		if (c.player.containerMenu instanceof MarketMenu menu && menu.view().haggle() != null) {
			long offer = Math.max(1, Math.round(menu.view().haggle().counter() * share));
			ClientPlayNetworking.send(new EconomyActionPayload(menu.containerId, EconomyActionPayload.HAGGLE_OFFER, 0, offer));
		}
	}

	private static void bank(Minecraft c, int action, int index, long amount) {
		if (c.player.containerMenu instanceof BankMenu menu) {
			ClientPlayNetworking.send(new EconomyActionPayload(menu.containerId, action, index, amount));
		}
	}

	private record Step(int delay, Consumer<Minecraft> action) {
	}
}
