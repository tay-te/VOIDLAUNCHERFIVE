package dev.voidmc.hearth.client.quest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.quest.QuestHooks;
import dev.voidmc.hearth.quest.net.QuestPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Client side of the quest module: the board and journal screens, the J key, the HUD objective tracker with its
 * waypoint arrow, and toasts. The client decides nothing: it shows the player's synced quests and the boards the server
 * sends, and sends back intents.
 */
public final class QuestClient {
	private static KeyMapping journalKey;

	private QuestClient() {
	}

	public static void init() {
		journalKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hearth.journal", InputConstants.KEY_J,
			KeyMapping.Category.register(Hearth.id("quests"))));
		QuestHooks.setOpenJournal(QuestClient::openJournal);
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (journalKey.consumeClick()) {
				if (mc.player != null && mc.gui.screen() == null) {
					openJournal();
				}
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(QuestPayloads.Board.TYPE, (payload, context) -> {
			Minecraft mc = context.client();
			if (mc.gui.screen() instanceof NoticeBoardScreen open && open.isFor(payload)) {
				open.update(payload);
			} else {
				mc.gui.setScreen(new NoticeBoardScreen(payload));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(QuestPayloads.Toast.TYPE, (payload, context) ->
			context.client().gui.toastManager().addToast(new QuestToast(payload)));
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, Hearth.id("quest/tracker"), new QuestHud());
	}

	static boolean isJournalKey(net.minecraft.client.input.KeyEvent event) {
		return journalKey != null && journalKey.matches(event);
	}

	static void openJournal() {
		Minecraft.getInstance().gui.setScreen(new JournalScreen());
	}
}
