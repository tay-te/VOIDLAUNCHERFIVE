package dev.voidmc.hearth.client.npc;

import dev.voidmc.hearth.npc.entity.FolkEntities;
import dev.voidmc.hearth.npc.talk.TalkPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;

/** Client side of the npc module: drawing folk (composed skins), and the conversation screen. */
public final class NpcClient {
	private NpcClient() {
	}

	public static void init() {
		EntityRendererRegistry.register(FolkEntities.FOLK, FolkRenderer::new);
		ClientTickEvents.END_CLIENT_TICK.register(mc -> FolkSkins.tick());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> mc.execute(FolkSkins::clear));
		ClientPlayNetworking.registerGlobalReceiver(TalkPayloads.Show.TYPE, (payload, ctx) -> {
			Minecraft mc = ctx.client();
			if (mc.gui.screen() instanceof ConversationScreen open && open.entityId() == payload.entity()) {
				open.update(payload);
			} else if (payload.open()) {
				mc.gui.setScreen(new ConversationScreen(payload));
			}
		});
	}
}
