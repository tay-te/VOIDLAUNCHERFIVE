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
 * The event line on arriving in a town (docs/LIVING_WORLD_2.md §10 F7: "A wedding at the chapel"): a player who walks
 * into a town with an event on today gets the day's events under the arrival banner ({@link EventPayloads.ArrivalEvents},
 * at most two), or one quiet line in chat from a server whose client cannot take it — what, where and when. Once a town
 * a day per player; checked every two seconds.
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
			if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p, EventPayloads.ArrivalEvents.TYPE)) {
				// the line under the arrival banner (F7) in place of the chat line
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new EventPayloads.ArrivalEvents(s.id(), lines(w, s).toString()));
				continue;
			}
			EventRule rule = EventRules.of(e.kind());
			String venue = rule == null ? "square" : rule.venue();
			p.sendSystemMessage(Component.translatable("hearth.events.arrival", Component.translatable(e.kind().translationKey()),
				Component.translatable("hearth.events.venue." + venue), EventDialogue.clock(e.start())).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
	}

	/**
	 * Today's events not yet over in a town for the line under the arrival banner (F7): a kind once, at most two, major
	 * ones first, then by start: {@code [{kind, text, when, tone, venue, start, begun}]}.
	 */
	static com.google.gson.JsonArray lines(HearthWorld w, Settlement s) {
		long now = TownLife.now(w, s);
		long today = TownLife.today(w, s);
		java.util.List<Scheduled> all = new java.util.ArrayList<>();
		for (Scheduled e : s.data(EventBook.KEY).scheduled) {
			if (e.day() == today && e.phase() != Scheduled.Phase.ENDED && e.end() > now && e.kind() != EventKind.SCENE) {
				all.add(e);
			}
		}
		all.sort((a, b) -> a.kind().major != b.kind().major ? (a.kind().major ? -1 : 1) : Long.compare(a.start(), b.start()));
		com.google.gson.JsonArray out = new com.google.gson.JsonArray();
		java.util.Set<EventKind> kinds = java.util.EnumSet.noneOf(EventKind.class);
		for (Scheduled e : all) {
			if (out.size() >= 2 || !kinds.add(e.kind())) {
				continue;
			}
			EventRule rule = EventRules.of(e.kind());
			String venue = rule == null ? "square" : rule.venue();
			String what = Component.translatable(e.kind().translationKey()).getString() + " "
				+ Component.translatable("hearth.events.venue." + venue).getString();
			com.google.gson.JsonObject o = new com.google.gson.JsonObject();
			o.addProperty("kind", e.kind().getSerializedName());
			o.addProperty("text", what.isEmpty() ? what : Character.toUpperCase(what.charAt(0)) + what.substring(1));
			boolean begun = e.start() <= now;
			o.addProperty("when", begun ? Component.translatable("hearth.events.arrival.now").getString()
				: Component.translatable("hearth.events.arrival.from", EventDialogue.clock(e.start())).getString());
			o.addProperty("tone", tone(e.kind()));
			o.addProperty("venue", venue);
			o.addProperty("start", EventDialogue.clock(e.start()));
			o.addProperty("begun", begun);
			out.add(o);
		}
		return out;
	}

	/** The dot's colour on the arrival line: {@code join} (gold: you can join in), {@code trouble} (red), {@code witness} (grey). */
	static String tone(EventKind kind) {
		return switch (kind) {
			case WEDDING, FAIR, TOURNAMENT, CHRISTENING, TOPPING_OUT, TROUPE, HORSE_FAIR, SLAUGHTER_DAY, RUNAWAY_PIG, LOST_CHILD, CART_OVERTURNED -> "join";
			case TAX, RAID, FIRE, EXECUTION, PILLORY, WELL_QUARREL -> "trouble";
			default -> "witness";
		};
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
