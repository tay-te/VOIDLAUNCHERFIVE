package dev.voidmc.mettle.client.marksmanship;

import dev.voidmc.mettle.character.skill.marksmanship.Draw;
import dev.voidmc.mettle.character.skill.marksmanship.HuntersMark;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.entity.Entity;

/**
 * Marksmanship on the client: Hunter's Mark outlines (the marked entities and when each fades) and forgetting
 * per-world state on disconnect. Draw-speed visuals are mixins that ask {@link Draw}.
 */
public final class MarksmanshipClient {
	/** Hunter's Mark's outline, an amber that reads against both day sky and night. */
	public static final int MARK_COLOR = 0xFFB347;

	private static boolean ready;
	/** Entity id → client game time the outline fades. Concurrent: the client game test reads it from its own thread. */
	private static final Map<Integer, Long> MARKS = new ConcurrentHashMap<>();

	private MarksmanshipClient() {
	}

	/** Safe to call more than once (a client mixin calls it until MettleClient does). */
	public static void init() {
		if (ready) {
			return;
		}
		ready = true;
		ClientPlayNetworking.registerGlobalReceiver(HuntersMark.Mark.TYPE, (payload, context) -> {
			if (context.client().level != null) {
				MARKS.put(payload.entity(), context.client().level.getGameTime() + payload.ticks());
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			MARKS.clear();
			Draw.forgetClient();
		});
	}

	/** Whether Hunter's Mark outlines this entity for the local player right now. */
	public static boolean marked(Entity entity) {
		if (MARKS.isEmpty()) {
			return false;
		}
		Long until = MARKS.get(entity.getId());
		if (until == null) {
			return false;
		}
		if (!entity.isAlive() || until < entity.level().getGameTime()) {
			MARKS.remove(entity.getId());
			return false;
		}
		return true;
	}
}
