package dev.voidmc.hearth.client.overlay;

import dev.voidmc.hearth.npc.talk.Conversations;
import dev.voidmc.hearth.npc.talk.TalkPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * The host side's upkeep (docs/OVERLAY.md): what Hearth's own screens check every tick while they show an event, done
 * here while a host shows it instead (a conversation ends when the folk is gone or out of earshot), and forgetting
 * everything on leaving a world.
 */
public final class HearthOverlay {
	private HearthOverlay() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(HearthOverlay::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> mc.execute(() -> {
			HearthViews.reset();
			HearthScreens.reset();
			Portraits.clear();
		}));
	}

	private static void tick(Minecraft mc) {
		if (!HearthScreens.TALK.equals(HearthScreens.hosted())) {
			return;
		}
		TalkPayloads.Show talk = HearthViews.talk();
		if (talk == null || mc.player == null || mc.level == null) {
			return;
		}
		Entity e = mc.level.getEntity(talk.entity());
		if (e == null || !e.isAlive() || e.distanceToSqr(mc.player) > Conversations.RANGE * Conversations.RANGE) {
			// as the parchment screen does: the conversation ends (state.talk goes null and the host closes)
			HearthActions.close(HearthScreens.TALK);
		}
	}
}
