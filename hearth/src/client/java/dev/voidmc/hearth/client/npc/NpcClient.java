package dev.voidmc.hearth.client.npc;

import dev.voidmc.hearth.npc.entity.FolkEntities;
import dev.voidmc.hearth.npc.talk.TalkPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import dev.voidmc.hearth.client.overlay.HearthScreens;
import dev.voidmc.hearth.client.overlay.HearthViews;
import net.minecraft.client.Minecraft;

/** Client side of the npc module: drawing folk (composed skins), and the conversation screen. */
public final class NpcClient {
	private NpcClient() {
	}

	public static void init() {
		EntityRendererRegistry.register(FolkEntities.FOLK, FolkRenderer::new);
		ClientTickEvents.END_CLIENT_TICK.register(mc -> FolkSkins.tick());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> mc.execute(() -> {
			FolkSkins.clear();
			FolkMarkers.clear();
		}));
		ClientPlayNetworking.registerGlobalReceiver(TalkPayloads.Markers.TYPE, (payload, ctx) -> FolkMarkers.accept(payload));
		ClientPlayNetworking.registerGlobalReceiver(TalkPayloads.Show.TYPE, (payload, ctx) -> {
			Minecraft mc = ctx.client();
			HearthViews.talk(payload);
			if (mc.gui.screen() instanceof ConversationScreen open && open.entityId() == payload.entity()) {
				open.update(payload);
			} else if (payload.open()) {
				// a host (the VOID Hub) shows it if it registered an opener; else the parchment screen
				HearthScreens.show(HearthScreens.TALK, () -> new ConversationScreen(payload));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(TalkPayloads.Detail.TYPE, (payload, ctx) -> HearthViews.talkDetail(payload));
		ClientPlayNetworking.registerGlobalReceiver(TalkPayloads.Close.TYPE, (payload, ctx) -> {
			Minecraft mc = ctx.client();
			HearthViews.talkClosed(payload.entity());
			if (mc.gui.screen() instanceof ConversationScreen open && open.entityId() == payload.entity()) {
				open.onClose();
			}
		});

		// the living world, part 2 (docs/LIVING_WORLD_2.md §11.2): each lane fills in its own entry point
		dev.voidmc.hearth.client.npc.travel.TravelClient.init(); // A1 carts
	}
}
