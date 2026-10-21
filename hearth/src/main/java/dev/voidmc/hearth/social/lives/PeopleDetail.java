package dev.voidmc.hearth.social.lives;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.economy.property.PropertyNames;
import dev.voidmc.hearth.npc.look.ResidentLooks;
import dev.voidmc.hearth.sim.lives.Life;
import dev.voidmc.hearth.sim.lives.LivesBook;
import dev.voidmc.hearth.sim.lives.LivesView;
import dev.voidmc.hearth.sim.lives.TownLives;
import dev.voidmc.hearth.social.Hooks;
import dev.voidmc.hearth.social.life.TownLife;
import dev.voidmc.hearth.social.life.Venues;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The People page's detail for one named character (docs/LIVING_WORLD_2.md §10 F4, Figma "People A, list and story"):
 * their face and trade, their story's beats as they went (with the choice that moved it), where it may go from here, the
 * relations it touches, where to find them through the day — and the page's "Waypoint": following a character, the way a
 * wanted poster is followed ({@link LivesPayloads.Track}, {@link LivesPayloads.Followed}). Server builds; text in the
 * server's language. Added 2026-10-21 (docs/OVERLAY.md {@code people}).
 */
public final class PeopleDetail {
	/** Followed characters' positions go to the nearest this many blocks. */
	static final int GRID = 8;

	private record Follow(UUID resident, long settlementId) {
	}

	private static final Map<UUID, Follow> FOLLOWS = new HashMap<>();
	private static final Map<UUID, LivesPayloads.Followed> SENT = new HashMap<>();
	private static int ticks;

	private PeopleDetail() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(LivesPayloads.Track.TYPE, LivesPayloads.Track.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(LivesPayloads.Followed.TYPE, LivesPayloads.Followed.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(LivesPayloads.Track.TYPE, (payload, ctx) -> track(ctx.player(), payload.resident()));
		ServerTickEvents.END_SERVER_TICK.register(PeopleDetail::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			FOLLOWS.remove(handler.player.getUUID());
			SENT.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FOLLOWS.clear();
			SENT.clear();
		});
	}

	// ---------------------------------------------------------------- the detail

	/** The detail JSON (docs/OVERLAY.md {@code people.rows[]}). */
	static JsonObject of(ServerPlayer player, Settlement s, Resident r, TownLives.Notable n) {
		JsonObject o = new JsonObject();
		o.addProperty("id", r.id.toString());
		o.addProperty("settlementId", s.id());
		long look = ResidentLooks.key(s, r);
		o.addProperty("look", look == ResidentLooks.NONE ? "" : String.format(Locale.ROOT, "%016x", look));
		o.addProperty("job", Component.translatable("hearth.npc.job." + r.job.getSerializedName()).getString());
		PlotView work = r.work == Resident.NO_PLOT ? null : s.plot(r.work);
		o.addProperty("place", work == null ? "" : PropertyNames.lower(work.spec().archetype().getPath()).getString());
		JsonArray traits = new JsonArray();
		List<String> ids = new ArrayList<>();
		for (dev.voidmc.hearth.npc.person.Trait t : dev.voidmc.hearth.npc.person.NpcData.of(r).traits()) {
			ids.add(t.getSerializedName());
		}
		java.util.Collections.sort(ids);
		ids.forEach(traits::add);
		o.add("traits", traits);
		o.addProperty("since", n.since);
		HearthWorld w = TownLife.world(s);
		o.addProperty("today", w == null ? 0 : TownLife.today(w, s));
		o.addProperty("following", n.resident.equals(followed(player)));

		LivesBook.Thread thread = LivesBook.get().thread(n.thread);
		// the beats as they went, the one now last
		JsonArray beats = new JsonArray();
		List<String> path = new ArrayList<>(n.path);
		path.add(n.beat);
		for (int i = 0; i < path.size(); i++) {
			String b = path.get(i);
			LivesBook.Beat beat = thread == null ? null : thread.beat(b);
			JsonObject j = new JsonObject();
			j.addProperty("id", b);
			j.addProperty("text", story(n.thread, b, r.givenName));
			j.addProperty("now", i == path.size() - 1);
			j.addProperty("end", beat != null && beat.end());
			beats.add(j);
		}
		o.add("beats", beats);

		// the choice that moved it last
		if (!n.chose.isEmpty()) {
			JsonObject c = new JsonObject();
			c.addProperty("id", n.chose);
			c.addProperty("label", Component.translatableWithFallback("hearth.lives.option." + n.thread + "." + n.chose, n.chose).getString());
			c.addProperty("cost", choiceCost(thread, n.chose));
			c.addProperty("by", n.byName);
			c.addProperty("yours", player.getUUID().equals(n.by));
			o.add("chose", c);
		} else {
			o.add("chose", com.google.gson.JsonNull.INSTANCE);
		}

		// what may be done now, and where it may go
		LivesBook.Beat now = thread == null ? null : thread.beat(n.beat);
		JsonArray choices = new JsonArray();
		JsonArray next = new JsonArray();
		Set<String> seen = new LinkedHashSet<>();
		if (now != null && !now.end()) {
			for (LivesBook.Choice c : now.choices()) {
				JsonObject j = new JsonObject();
				j.addProperty("id", c.id());
				j.addProperty("label", Component.translatableWithFallback("hearth.lives.option." + n.thread + "." + c.id(), c.id()).getString());
				j.addProperty("cost", c.cost());
				j.addProperty("check", c.speech() == null ? "" : c.speech());
				j.addProperty("quest", c.quest() != null);
				j.addProperty("to", c.to());
				choices.add(j);
				if (seen.add(c.to())) {
					next.add(ending(thread, n.thread, c.to(), r.givenName, "choice", c.id(), -1));
				}
			}
			for (LivesBook.Next x : now.next()) {
				if (seen.add(x.to())) {
					next.add(ending(thread, n.thread, x.to(), r.givenName, x.flag() != null && x.flag().startsWith("chose:") ? "choice" : "time", "",
						x.after()));
				}
			}
		}
		o.add("choices", choices);
		o.add("next", next);

		// the relations the story touches: the one it is about first, then their strongest
		JsonArray relations = new JsonArray();
		Set<UUID> listed = new LinkedHashSet<>();
		if (n.partner != null) {
			Resident p = s.resident(n.partner);
			if (p != null) {
				Life.Relation rel = LivesView.relation(s, r, p.id);
				relations.add(relation(player, p, rel == null ? "" : rel.bond().name().toLowerCase(Locale.ROOT), true));
				listed.add(p.id);
			}
		}
		List<Life.Relation> rels = new ArrayList<>(LivesView.relations(s, r));
		rels.sort((a, b) -> Integer.compare(Math.abs(b.strength()), Math.abs(a.strength())));
		for (Life.Relation rel : rels) {
			if (relations.size() >= 3) {
				break;
			}
			if (rel.bond() == Life.Bond.ACQUAINTANCE || rel.bond() == Life.Bond.NONE || !listed.add(rel.other())) {
				continue;
			}
			Resident p = s.resident(rel.other());
			if (p != null) {
				relations.add(relation(player, p, rel.bond().name().toLowerCase(Locale.ROOT), false));
			}
		}
		o.add("relations", relations);

		// where to find them: their role's windows for this beat, and their workplace and home
		JsonArray find = new JsonArray();
		LivesBook.Role role = LivesBook.get().role(n.role);
		if (role != null) {
			for (LivesBook.Window win : role.schedule()) {
				if (!win.beats().isEmpty() && !win.beats().contains(n.beat)) {
					continue;
				}
				JsonObject j = new JsonObject();
				j.addProperty("days", win.days());
				j.addProperty("from", win.from());
				j.addProperty("to", win.to());
				String what = win.leisure() != null ? win.leisure() : win.where() != null ? win.where() : win.activity();
				j.addProperty("what", what.toLowerCase(Locale.ROOT));
				j.addProperty("text", Component.translatableWithFallback("hearth.lives.find." + what.toLowerCase(Locale.ROOT),
					what.toLowerCase(Locale.ROOT).replace('_', ' ')).getString());
				find.add(j);
			}
		}
		o.add("find", find);
		PlotView home = r.home == Resident.NO_PLOT ? null : s.plot(r.home);
		o.addProperty("home", home == null ? "" : PropertyNames.lower(home.spec().archetype().getPath()).getString());
		return o;
	}

	private static String story(String thread, String beat, String given) {
		return Component.translatableWithFallback("hearth.lives.story." + thread + "." + beat, beat.replace('_', ' '), given).getString();
	}

	private static long choiceCost(LivesBook.@Nullable Thread thread, String choice) {
		if (thread == null) {
			return 0;
		}
		for (LivesBook.Beat b : thread.beats().values()) {
			for (LivesBook.Choice c : b.choices()) {
				if (c.id().equals(choice)) {
					return c.cost();
				}
			}
		}
		return 0;
	}

	private static JsonObject ending(LivesBook.@Nullable Thread thread, String threadId, String to, String given, String via, String choice,
		int afterDays) {
		LivesBook.Beat b = thread == null ? null : thread.beat(to);
		JsonObject j = new JsonObject();
		j.addProperty("beat", to);
		j.addProperty("text", story(threadId, to, given));
		j.addProperty("end", b != null && b.end());
		j.addProperty("via", via);
		j.addProperty("choice", choice);
		j.addProperty("afterDays", afterDays);
		return j;
	}

	private static JsonObject relation(ServerPlayer player, Resident p, String bond, boolean partner) {
		JsonObject j = new JsonObject();
		j.addProperty("id", p.id.toString());
		j.addProperty("name", p.fullName());
		j.addProperty("bond", bond);
		j.addProperty("partner", partner);
		j.addProperty("opinion", Hooks.opinion(p.id, player));
		return j;
	}

	// ---------------------------------------------------------------- following one

	/** Who the player follows, or null. */
	static @Nullable UUID followed(ServerPlayer player) {
		Follow f = FOLLOWS.get(player.getUUID());
		return f == null ? null : f.resident();
	}

	/** Follows a named character the player has met ({@code resident}: a UUID string), or stops (empty, or the same again). */
	static void track(ServerPlayer player, String resident) {
		UUID id = null;
		try {
			id = resident == null || resident.isEmpty() ? null : UUID.fromString(resident);
		} catch (IllegalArgumentException e) {
			return;
		}
		Follow was = FOLLOWS.get(player.getUUID());
		if (id == null || was != null && was.resident().equals(id)) {
			if (FOLLOWS.remove(player.getUUID()) != null) {
				send(player, LivesPayloads.Followed.NONE);
				LivesFolk.sendPeople(player);
			}
			return;
		}
		Settlement s = metIn(player, id);
		if (s == null) {
			return;
		}
		FOLLOWS.put(player.getUUID(), new Follow(id, s.id()));
		update(player);
		LivesFolk.sendPeople(player);
	}

	/** The town where the player met the named character {@code id}, or null (not met, not a character, not this world). */
	private static @Nullable Settlement metIn(ServerPlayer player, UUID id) {
		if (!(player.level() instanceof ServerLevel level)) {
			return null;
		}
		HearthWorld w = HearthWorld.of(level);
		if (w == null) {
			return null;
		}
		for (Settlement s : w.settlements()) {
			TownLives.Notable n = LivesView.notable(s, id);
			if (n != null && n.met.contains(player.getUUID())) {
				return s;
			}
		}
		return null;
	}

	private static void tick(MinecraftServer server) {
		if (FOLLOWS.isEmpty() || ++ticks % 20 != 7) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Follow>> it = FOLLOWS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Follow> e = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p == null) {
				it.remove();
				SENT.remove(e.getKey());
				continue;
			}
			update(p);
		}
	}

	private static void update(ServerPlayer player) {
		Follow f = FOLLOWS.get(player.getUUID());
		if (f == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		HearthWorld w = HearthWorld.of(level);
		Settlement s = w == null ? null : w.settlement(f.settlementId());
		Resident r = s == null ? null : s.resident(f.resident());
		if (r == null) {
			// gone (dead, moved away): the waypoint goes with them
			FOLLOWS.remove(player.getUUID());
			send(player, LivesPayloads.Followed.NONE);
			return;
		}
		NpcService.FolkHandle h = Hooks.folk(r.id);
		BlockPos pos;
		boolean placed;
		if (h != null && h.entity().level() == level) {
			BlockPos b = h.entity().blockPosition();
			pos = new BlockPos(Math.floorDiv(b.getX(), GRID) * GRID + GRID / 2, b.getY(), Math.floorDiv(b.getZ(), GRID) * GRID + GRID / 2);
			placed = true;
		} else {
			PlotView plot = r.work != Resident.NO_PLOT ? s.plot(r.work) : r.home != Resident.NO_PLOT ? s.plot(r.home) : null;
			pos = plot != null ? Venues.middle(plot.spec()) : s.center();
			placed = false;
		}
		send(player, new LivesPayloads.Followed(true, r.id.toString(), r.fullName(), s.name(), placed, pos));
	}

	private static void send(ServerPlayer player, LivesPayloads.Followed f) {
		if (f.equals(SENT.get(player.getUUID()))) {
			return;
		}
		SENT.put(player.getUUID(), f);
		if (ServerPlayNetworking.canSend(player, LivesPayloads.Followed.TYPE)) {
			ServerPlayNetworking.send(player, f);
		}
	}
}
