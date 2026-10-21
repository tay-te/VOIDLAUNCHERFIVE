package dev.voidmc.fray.client;

import dev.voidmc.fray.Fray;
import dev.voidmc.fray.motion.MotionPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Fray's client entrypoint: the clip library, the login answer, the motion packets and the per-tick bookkeeping. */
public final class FrayClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientClips.load(null);
		ClientConfigurationNetworking.registerGlobalReceiver(MotionPayloads.Hello.TYPE, (payload, context) -> {
			MotionPayloads.Hello mine = ClientClips.hello();
			if (!payload.clipSet().equals(mine.clipSet())) {
				Fray.LOGGER.warn("the server's clip set is {} ({} clips), this client's {} ({} clips): the server will refuse the login",
					payload.clipSet(), payload.clips(), mine.clipSet(), mine.clips());
			}
			context.responseSender().sendPacket(mine);
		});
		ClientPlayNetworking.registerGlobalReceiver(MotionPayloads.Play.TYPE, (payload, context) -> ClientMotion.onPlay(payload));
		ClientPlayNetworking.registerGlobalReceiver(MotionPayloads.Stop.TYPE, (payload, context) -> ClientMotion.onStop(payload));
		ClientPlayNetworking.registerGlobalReceiver(MotionPayloads.ActionAck.TYPE, (payload, context) -> ClientMotion.onAck(payload));
		ClientMotion.init();
		ClientTickEvents.END_CLIENT_TICK.register(ClientMotion::tick);
		ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> ClientMotion.remove(entity.getId()));
	}
}
