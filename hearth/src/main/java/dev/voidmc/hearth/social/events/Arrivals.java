package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.life.TownLife;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The event line on arriving in a town (docs/LIVING_WORLD_2.md §10 F7: "A wedding at the chapel"): until the arrival
 * banner's line is designed in Figma, a player who walks into a town with an event on today gets one quiet line in chat
 * — what, where and when. Once a town a day per player; checked every two seconds.
 */
final class Arrivals {
	private static final Map<UUID, Long> IN = new HashMap<>();
	private static final Map<UUID, String> TOLD = new HashMap<>();

	private Arrivals() {
	}

	static void clear() {
		IN.clear();
		TOLD.clear();
	}

	static void tick(MinecraftServer server) {
		if (server.overworld().getGameTime() % 40 != 11) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!(p.level() instanceof ServerLevel level)) {
				continue;
			}
			HearthWorld w = HearthWorld.of(level);
			Settlement s = w == null ? null : w.settlementAt(p.blockPosition());
			Long was = IN.put(p.getUUID(), s == null ? 0L : s.id());
			if (s == null || was != null && was == s.id()) {
				continue;
			}
			Scheduled e = next(w, s);
			if (e == null) {
				continue;
			}
			String key = s.id() + "/" + e.day();
			if (key.equals(TOLD.get(p.getUUID()))) {
				continue;
			}
			TOLD.put(p.getUUID(), key);
			EventRule rule = EventRules.of(e.kind());
			String venue = rule == null ? "square" : rule.venue();
			p.sendSystemMessage(Component.translatable("hearth.events.arrival", Component.translatable(e.kind().translationKey()),
				Component.translatable("hearth.events.venue." + venue), EventDialogue.clock(e.start())).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
	}

	/** Today's next event not yet over in a town (a major one first), or null. */
	static Scheduled next(HearthWorld w, Settlement s) {
		long now = TownLife.now(w, s);
		long today = TownLife.today(w, s);
		Scheduled best = null;
		for (Scheduled e : s.data(EventBook.KEY).scheduled) {
			if (e.day() != today || e.phase() == Scheduled.Phase.ENDED || e.end() <= now || e.kind() == EventKind.SCENE) {
				continue;
			}
			if (best == null || e.kind().major && !best.kind().major || e.kind().major == best.kind().major && e.start() < best.start()) {
				best = e;
			}
		}
		return best;
	}
}
