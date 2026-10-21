package dev.voidmc.mettle.client.legend;

import dev.voidmc.mettle.character.cards.legend.LegendPayloads;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** The client half of the sixteen drawn Mythics of build feel (docs/BUILD_FEEL.md §5.4): the Long Watch's outline, the Cat's Way. */
public final class LegendClient {
	/** The Long Watch: entity id → the client game time its outline fades. */
	private static final Map<Integer, Long> WATCHED = new HashMap<>();

	private LegendClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(LegendPayloads.Watch.TYPE, (payload, context) -> {
			Minecraft mc = context.client();
			if (mc.level == null) {
				return;
			}
			long until = mc.level.getGameTime() + payload.ticks();
			WATCHED.values().removeIf(at -> at < mc.level.getGameTime());
			for (int id : payload.entities()) {
				WATCHED.put(id, until);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> WATCHED.clear());
		CatsWay.init();
	}

	/** Whether the Long Watch outlines this entity for the local player now. */
	public static boolean watched(Entity entity) {
		if (WATCHED.isEmpty()) {
			return false;
		}
		Long until = WATCHED.get(entity.getId());
		return until != null && entity.level().getGameTime() <= until && entity.isAlive();
	}
}
