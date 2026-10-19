package dev.voidmc.hub.hearth;

import dev.voidmc.hub.VoidHub;
import dev.voidmc.hub.screen.HubEventScreen;
import dev.voidmc.hub.screen.HubInventoryScreen;
import dev.voidmc.hub.ui.HubView;
import net.minecraft.client.Minecraft;

/**
 * Where Hearth's screens open in the hub (hearth/docs/OVERLAY.md §Openers), registered once at startup:
 *
 * <ul>
 *   <li>the journal, the notice board and the reputation page are segments of the hub's Quests page;</li>
 *   <li>a conversation, a guard, a market, bank or dice table, lockpicking and pickpocketing open the lighter
 *       {@link HubEventScreen}.</li>
 * </ul>
 *
 * Each opener returns false (Hearth shows its own screen) when the hub cannot: Ultralight is unavailable, no player, or,
 * for lockpicking, a Hearth whose lockpick screen the hub cannot drive.
 */
public final class HearthHost {

	private HearthHost() {
	}

	/** Registers every opener with Hearth (a no-op without Hearth). */
	public static void register() {
		if (!HearthLink.present()) {
			return;
		}
		int n = 0;
		for (String screen : HearthLink.SCREENS) {
			if (HearthLink.registerOpener(screen, () -> open(screen))) {
				n++;
			}
		}
		VoidHub.LOGGER.info("VOID Hub: {} of Hearth's {} screens open in the hub", n, HearthLink.SCREENS.size());
	}

	/** Opens one of Hearth's screens in the hub; false leaves it to Hearth's own screen. */
	public static boolean open(String screen) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !HubView.available()) {
			return false;
		}
		switch (screen) {
			case "journal", "board", "reputation" -> {
				if (mc.gui.screen() instanceof HubInventoryScreen hub) {
					hub.showQuests(screen);  // already open: switch to the Quests page's segment
				} else {
					HubInventoryScreen hub = new HubInventoryScreen(mc.player, HubInventoryScreen.QUESTS);
					hub.questSegment(screen);
					mc.gui.setScreen(hub);
				}
				return true;
			}
			case "lockpick" -> {
				if (!HearthLink.canHostLockpick()) {
					return false;
				}
			}
			default -> {
			}
		}
		if (mc.gui.screen() instanceof HubEventScreen open && open.name().equals(screen)) {
			return true;  // the same event again (a new line, a new view): the open screen shows it
		}
		mc.gui.setScreen(new HubEventScreen(screen));
		return true;
	}
}
