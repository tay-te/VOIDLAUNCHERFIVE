package dev.voidmc.hub;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmc.hub.layout.HubLayout;
import dev.voidmc.hub.mettle.MettleLink;
import dev.voidmc.hub.screen.HubInventoryScreen;
import dev.voidmc.hub.ui.HubView;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * VOID Hub: the VOID-drawn inventory, rendered with Ultralight over Minecraft's real menus.
 *
 * <ul>
 *   <li>Right Shift opens the hub on its Inventory page.</li>
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
		MettleLink.registerOpener(() -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null || !HubView.available()) {
				return false;
			}
			mc.gui.setScreen(new HubInventoryScreen(mc.player, "classes"));
			return true;
		});

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (openHub.consumeClick()) {
				if (mc.player != null && mc.gui.screen() == null && HubView.available()) {
					mc.gui.setScreen(new HubInventoryScreen(mc.player));
				}
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
		LOGGER.info("VOID Hub ready: R-Shift opens the hub; VOID inventory replaces vanilla's: {}", HubConfig.replaceInventory());
	}

	public static Minecraft mc() {
		return Minecraft.getInstance();
	}
}
