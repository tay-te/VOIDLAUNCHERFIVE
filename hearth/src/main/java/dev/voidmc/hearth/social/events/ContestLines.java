package dev.voidmc.hearth.social.events;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.voidmc.hearth.api.Settlement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The contest line (docs/LIVING_WORLD_2.md §10 F6, Figma "Contest line under the quest HUD"): what an entrant of the fair's
 * archery contest or the tournament's melee sees under the quest HUD while at the range or the ring — the arrows as they
 * land and the table, or the rounds won and lost — and the result once it is settled. Half a second's look at the runs;
 * a player gets a packet only when their line changed. Server thread. 2026-10-21.
 */
final class ContestLines {
	/** At the range or the ring: within this many blocks of the mark. */
	static final int NEAR = 40;
	private static final Map<UUID, String> SENT = new HashMap<>();

	private ContestLines() {
	}

	static void clear() {
		SENT.clear();
	}

	static void tick(MinecraftServer server) {
		if (server.overworld().getGameTime() % 10 != 3) {
			return;
		}
		Map<UUID, JsonObject> lines = new HashMap<>();
		for (SceneRun run : Scenes.allRuns()) {
			if (run.ended) {
				continue;
			}
			if (run.event.kind() == EventKind.FAIR && FairScene.open(run)) {
				for (UUID id : FairScene.entrants(run).keySet()) {
					ServerPlayer p = server.getPlayerList().getPlayer(id);
					if (p != null && near(p, run, run.venue("range"))) {
						lines.put(id, archery(run, id, "open"));
					}
				}
			} else if (run.event.kind() == EventKind.TOURNAMENT) {
				Set<UUID> in = new HashSet<>();
				List<UUID> queue = run.get("melee_queue");
				if (queue != null) {
					in.addAll(queue);
				}
				for (UUID id : in) {
					ServerPlayer p = server.getPlayerList().getPlayer(id);
					if (p != null && near(p, run, run.venue("ring"))) {
						lines.put(id, melee(run, id, "open"));
					}
				}
			}
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			JsonObject line = lines.get(p.getUUID());
			send(p, line == null ? "" : line.toString());
		}
		SENT.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
	}

	private static boolean near(ServerPlayer p, SceneRun run, BlockPos mark) {
		return p.level() == run.level() && p.blockPosition().distSqr(mark) <= (long) NEAR * NEAR;
	}

	/** The archery line for an entrant: {@code state} open, won or lost. */
	static JsonObject archery(SceneRun run, UUID player, String state) {
		JsonObject o = base(run, "archery", state);
		FairScene.Entrant me = FairScene.entrants(run).get(player);
		o.addProperty("score", me == null ? 0 : me.score);
		o.addProperty("shots", me == null ? 0 : me.shots);
		o.addProperty("arrows", 10);
		JsonArray hits = new JsonArray();
		if (me != null) {
			me.arrows.forEach(hits::add);
		}
		o.add("hits", hits);
		double share = Math.min(1, Math.max(0, (run.minute - run.rule.param("contest_from", 30)) / Math.max(1, run.rule.param("contest_to", 240)
			- run.rule.param("contest_from", 30))));
		if (!state.equals("open")) {
			share = 1;
		}
		List<JsonObject> table = new ArrayList<>();
		for (Outcomes.Archer a : Outcomes.archers(run.event)) {
			JsonObject t = new JsonObject();
			t.addProperty("name", a.name());
			t.addProperty("score", Math.round(a.score() * share));
			t.addProperty("you", false);
			table.add(t);
		}
		for (Map.Entry<UUID, FairScene.Entrant> e : FairScene.entrants(run).entrySet()) {
			JsonObject t = new JsonObject();
			t.addProperty("name", e.getValue().name);
			t.addProperty("score", e.getValue().score);
			t.addProperty("you", e.getKey().equals(player));
			table.add(t);
		}
		table.sort((a, b) -> Long.compare(b.get("score").getAsLong(), a.get("score").getAsLong()));
		JsonArray rows = new JsonArray();
		int place = 0;
		for (JsonObject t : table) {
			t.addProperty("place", ++place);
			rows.add(t);
		}
		o.add("table", rows);
		o.addProperty("purse", (long) run.rule.param("purse", 30));
		return o;
	}

	/** The melee line for an entrant: {@code state} open, won or lost. */
	static JsonObject melee(SceneRun run, UUID player, String state) {
		JsonObject o = base(run, "melee", state);
		Map<UUID, int[]> m = run.get("melee_rounds");
		int[] r = m == null ? null : m.get(player);
		int won = r == null ? 0 : r[0];
		int lost = r == null ? 0 : r[1];
		o.addProperty("won", won);
		o.addProperty("lost", lost);
		o.addProperty("rounds", 3);
		o.addProperty("round", Math.min(3, won + lost + (state.equals("open") ? 1 : 0)));
		TourneyScene.Champion c = run.get("champion_ctl");
		boolean fighting = c != null && player.equals(c.foe);
		o.addProperty("fighting", fighting);
		Long pauseUntil = run.get("pause_until");
		o.addProperty("between", pauseUntil != null && run.level().getGameTime() < pauseUntil);
		o.addProperty("yieldAt", 0.25);
		o.addProperty("purse", (long) run.rule.param("purse", 40));
		return o;
	}

	private static JsonObject base(SceneRun run, String kind, String state) {
		JsonObject o = new JsonObject();
		Settlement s = run.settlement();
		o.addProperty("kind", kind);
		o.addProperty("event", run.event.kind().getSerializedName());
		o.addProperty("eventName", net.minecraft.network.chat.Component.translatable(run.event.kind().translationKey()).getString());
		o.addProperty("town", s == null ? "" : s.name());
		o.addProperty("state", state);
		return o;
	}

	/** The result, sent at once (the client holds it a few seconds before the line folds away). */
	static void result(ServerPlayer p, JsonObject line) {
		send(p, line.toString());
	}

	private static void send(ServerPlayer p, String json) {
		String was = SENT.get(p.getUUID());
		if (json.equals(was) || was == null && json.isEmpty()) {
			return;
		}
		SENT.put(p.getUUID(), json);
		if (ServerPlayNetworking.canSend(p, EventPayloads.ContestLine.TYPE)) {
			ServerPlayNetworking.send(p, new EventPayloads.ContestLine(json));
		}
	}
}
