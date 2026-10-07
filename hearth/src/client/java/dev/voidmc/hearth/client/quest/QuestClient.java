package dev.voidmc.hearth.client.quest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.quest.QuestHooks;
import dev.voidmc.hearth.quest.model.Objective;
import dev.voidmc.hearth.quest.model.QuestInstance;
import dev.voidmc.hearth.quest.model.QuestStatus;
import dev.voidmc.hearth.quest.net.QuestPayloads;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

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
		QuestClientTour.install();
	}

	static boolean isJournalKey(net.minecraft.client.input.KeyEvent event) {
		return journalKey != null && journalKey.matches(event);
	}

	static void openJournal() {
		Minecraft.getInstance().gui.setScreen(new JournalScreen());
	}

	/**
	 * The player's tracked quest as other client code sees it (the map partner, client/map): where it wants them, what
	 * is left. Read from the synced quests, as the HUD tracker is.
	 *
	 * @param target     where to go now (the open objective's place, or the town to hand in), or null where the quest
	 *                   names no place (hunt anywhere, find a biome)
	 * @param handIn     whether the work is done and only the hand-in is left
	 * @param objectives the open objectives as lines ("Bring 24 × Spruce Log (12/24)"), or the hand-in line
	 * @param step       the step the player is on: the first open objective's label, or the hand-in line
	 * @param progress   how far along that step is ({@code count} 0 or 1: a step that is done or not, no count shown)
	 */
	public record Tracked(UUID id, String title, @Nullable BlockPos target, boolean handIn, String settlement, List<String> objectives, String step,
		int progress, int count) {
	}

	/** The tracked quest of a player (the client's own, synced), or null if none is tracked. */
	public static @Nullable Tracked tracked(Player player) {
		var pq = dev.voidmc.hearth.quest.service.QuestStore.get(player);
		if (pq.tracked == null) {
			return null;
		}
		QuestInstance q = pq.get(pq.tracked);
		if (q == null) {
			return null;
		}
		boolean handIn = q.status == QuestStatus.READY;
		List<String> lines = new ArrayList<>();
		Objective open = handIn ? null : q.firstOpen();
		if (open == null) {
			lines.add(Component.translatable("hearth.quest.hud.return", NoticeBoardScreen.cap(q.giver.name()), q.settlementName).getString());
		} else {
			for (Objective o : q.currentObjectives()) {
				lines.add(QuestUi.objectiveLine(o, null));
			}
		}
		String step = open == null ? lines.get(0) : open.label;
		return new Tracked(q.id, q.text.title(), QuestUi.waypoint(q), handIn, q.settlementName == null ? "" : q.settlementName, lines, step,
			open == null ? 0 : open.progress, open == null ? 0 : open.count);
	}
}
