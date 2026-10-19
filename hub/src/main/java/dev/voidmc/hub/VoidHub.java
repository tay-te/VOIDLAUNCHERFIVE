package dev.voidmc.hub;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmc.hub.layout.HubLayout;
import dev.voidmc.hub.map.HubMap;
import dev.voidmc.hub.mettle.MettleLink;
import dev.voidmc.hub.screen.HubInventoryScreen;
import dev.voidmc.hub.ui.HubView;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * VOID Hub: the VOID-drawn inventory, rendered with Ultralight over Minecraft's real menus.
 *
 * <ul>
 *   <li>Right Shift opens the hub on the page it was left on: at first the Map (VOID Atlas's map,
 *       when Atlas is installed), else the Inventory. Tab switches pages.</li>
 *   <li>While {@link HubConfig#replaceInventory()} is on (the default for this prototype), E and
 *       opening a chest show the VOID screens instead of vanilla's. Every other container keeps its
 *       vanilla screen.</li>
 * </ul>
 */
public final class VoidHub implements ClientModInitializer {

	public static final String MOD_ID = "voidhub";
	public static final Logger LOGGER = LoggerFactory.getLogger("VOID Hub");

	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "hub"));
	public static KeyMapping openHub;

	private boolean prewarmed;

	@Override
	public void onInitializeClient() {
		openHub = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.voidhub.open", InputConstants.Type.KEYBOARD, InputConstants.KEY_RSHIFT, CATEGORY));
		// VOID Mettle's K opens the hub's Classes page instead of its fallback screen.
		MettleLink.registerPickerOpener(() -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null || !HubView.available()) {
				return false;
			}
			mc.gui.setScreen(new HubInventoryScreen(mc.player, "classes"));
			return true;
		});
		MettleLink.registerOpener(() -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null || !HubView.available()) {
				return false;
			}
			mc.gui.setScreen(new HubInventoryScreen(mc.player, "classes"));
			return true;
		});

		// VOID Hearth's screens (talk, guard, market, bank, dice, lockpicking, pickpocketing, journal, board, reputation)
		// open in the hub: every opener registered once, here.
		dev.voidmc.hub.hearth.HearthHost.register();

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (openHub.consumeClick()) {
				if (mc.player != null && mc.gui.screen() == null && HubView.available()) {
					mc.gui.setScreen(new HubInventoryScreen(mc.player, HubInventoryScreen.rShiftPage()));
				}
			}
			// A Card Shrine was used: Mettle opens no screen of its own, the hub opens on Classes, where the page
			// shows the shrine while the sitting lasts.
			if (MettleLink.sittingJustOpened() && mc.player != null && mc.gui.screen() == null && HubView.available()) {
				mc.gui.setScreen(new HubInventoryScreen(mc.player, "classes"));
			}
			// Pay Ultralight's start-up (natives, renderer, page load) while the world loads in,
			// not on the first key press.
			if (!this.prewarmed && mc.player != null && HubConfig.prewarm()) {
				this.prewarmed = true;
				var w = mc.getWindow();
				HubLayout l = HubLayout.inventory(w.getWidth(), w.getHeight(), w.getGuiScale());
				HubView.prewarm(w.getWidth(), w.getHeight(), l.u, l.toJson());
			}
		});
		// A new world gets a new map (Atlas's map of it) and opens on the first page again.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
			HubMap.reset();
			HubInventoryScreen.resetLastPage();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> HubMap.reset());
		// F3+T and pack changes: the card strips and Mettle's icons are read again from the new resources.
		net.fabricmc.fabric.api.resource.v1.ResourceLoader.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES).registerReloadListener(
			Identifier.fromNamespaceAndPath(MOD_ID, "card_art"),
			(net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> {
				dev.voidmc.hub.screen.CardFx.clearTextures();
				MettleLink.clearIcons();
			});
		LOGGER.info("VOID Hub ready: R-Shift opens the hub; VOID inventory replaces vanilla's: {}; Map page: {}", HubConfig.replaceInventory(),
			HubMap.available() ? "VOID Atlas" : "stand-in (VOID Atlas not installed)");
	}

	public static Minecraft mc() {
		return Minecraft.getInstance();
	}
}
