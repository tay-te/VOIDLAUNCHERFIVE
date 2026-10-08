package dev.voidmc.mettle.compat.hearth;

import com.mojang.serialization.Codec;
import dev.voidmc.mettle.Mettle;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.player.Player;

/**
 * Which trainers taught the player on which sim day: one lesson a day per trainer. A small persistent attachment
 * ({@code mettle:compat/trainer_sessions}, unsynced, kept on death) that only ever holds today's lessons, so it stays a
 * handful of entries. Registered only with Hearth (a world opened without it drops the attachment, which forgets only
 * today's lessons).
 */
final class TrainerSessions {
	static final AttachmentType<Map<UUID, Long>> SESSIONS = AttachmentRegistry.create(Mettle.id("compat/trainer_sessions"), b -> b
		.persistent(Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG))
		.copyOnDeath());

	private TrainerSessions() {
	}

	static void init() {
	}

	static boolean taughtToday(Player player, UUID trainer, long day) {
		Map<UUID, Long> sessions = player.getAttached(SESSIONS);
		return sessions != null && sessions.getOrDefault(trainer, Long.MIN_VALUE) == day;
	}

	static void record(Player player, UUID trainer, long day) {
		Map<UUID, Long> next = new HashMap<>();
		Map<UUID, Long> sessions = player.getAttached(SESSIONS);
		if (sessions != null) {
			// Yesterday's lessons no longer matter: only today's are kept.
			sessions.forEach((t, d) -> {
				if (d == day) {
					next.put(t, d);
				}
			});
		}
		next.put(trainer, day);
		player.setAttached(SESSIONS, Map.copyOf(next));
	}
}
