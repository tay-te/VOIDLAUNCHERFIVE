package dev.voidmc.hearth.client.overlay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.voidmc.hearth.economy.bank.BankView;
import dev.voidmc.hearth.economy.coin.Change;
import dev.voidmc.hearth.economy.coin.Coin;
import dev.voidmc.hearth.economy.coin.CoinFormat;
import dev.voidmc.hearth.economy.haggle.Haggle;
import dev.voidmc.hearth.economy.market.MarketView;
import dev.voidmc.hearth.economy.property.PropertyNames;
import dev.voidmc.hearth.law.net.LawPayloads;
import dev.voidmc.hearth.npc.talk.TalkPayloads;
import dev.voidmc.hearth.quest.model.Objective;
import dev.voidmc.hearth.quest.model.PlayerQuests;
import dev.voidmc.hearth.quest.model.QuestInstance;
import dev.voidmc.hearth.quest.model.QuestReward;
import dev.voidmc.hearth.quest.model.QuestStatus;
import dev.voidmc.hearth.quest.net.QuestPayloads;
import dev.voidmc.hearth.social.dice.DiceGame;
import dev.voidmc.hearth.social.dice.DiceView;
import dev.voidmc.hearth.standing.net.StandingPayloads;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.function.LongFunction;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The pieces of {@link HearthState#build}, as pure functions of what the server sent (docs/OVERLAY.md has the schema):
 * every text already translated, every amount of coin as {@link #coins}. Nothing here reads the game, so it can be built
 * and tested from plain payloads.
 */
public final class HearthStateJson {
	private HearthStateJson() {
	}

	/** Looks things up that only the running client knows: portraits by entity id, what the player carries of an item. */
	public record Lookups(IntFunction<@Nullable String> portrait, IntFunction<String> portraitKey, ToIntFunction<String> carried,
		LongFunction<@Nullable String> lookPortrait) {
		/** No portraits, nothing carried (tests, a world not loaded). */
		public static final Lookups NONE = new Lookups(e -> null, e -> "", t -> 0);

		/** Without portraits by look (a resident whose folk is not loaded then has none). */
		public Lookups(IntFunction<@Nullable String> portrait, IntFunction<String> portraitKey, ToIntFunction<String> carried) {
			this(portrait, portraitKey, carried, k -> null);
		}
	}

	// ---------------------------------------------------------------- shared shapes

	/** {@code {total, crowns, marks, bits, text}}: an amount in bits as the fewest coins, and in words. */
	public static JsonObject coins(long bits) {
		JsonObject o = new JsonObject();
		long total = Math.max(0, bits);
		long[] c = Change.fewest(total);
		o.addProperty("total", bits);
		o.addProperty("crowns", c[Coin.CROWN.ordinal()]);
		o.addProperty("marks", c[Coin.MARK.ordinal()]);
		o.addProperty("bits", c[Coin.BIT.ordinal()]);
		o.addProperty("text", CoinFormat.format(bits).getString());
		return o;
	}

	/** {@code coins(bits)}, or null for a negative "none". */
	static JsonElement coinsOrNull(long bits) {
		return bits < 0 ? JsonNull.INSTANCE : coins(bits);
	}

	static String text(@Nullable Component c) {
		return c == null ? "" : c.getString();
	}

	static JsonElement textOrNull(@Nullable Component c) {
		return c == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(c.getString());
	}

	/**
	 * Speech without the quote marks around it ({@code "Have a look."} → {@code Have a look.}): Hearth's own screens draw
	 * some lines quoted, while the state's lines are bare and a page adds its own marks (docs/OVERLAY.md).
	 */
	public static String unquoted(String line) {
		String t = line.strip();
		if (t.length() >= 2) {
			char a = t.charAt(0);
			char b = t.charAt(t.length() - 1);
			if (a == '"' && b == '"' || a == '\u201C' && b == '\u201D' || a == '\u00AB' && b == '\u00BB' || a == '\u201E' && b == '\u201C') {
				String inner = t.substring(1, t.length() - 1);
				// only one quoted stretch: "a" and "b" keeps its marks
				if (inner.indexOf(a) < 0 && inner.indexOf(b) < 0) {
					return inner.strip();
				}
			}
		}
		return t;
	}

	static String tr(String key) {
		return Component.translatable(key).getString();
	}

	static JsonObject item(ItemStack stack) {
		JsonObject o = new JsonObject();
		o.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
		o.addProperty("name", stack.isEmpty() ? "" : stack.getHoverName().getString());
		o.addProperty("count", stack.getCount());
		return o;
	}

	static JsonElement pos(@Nullable BlockPos p) {
		if (p == null) {
			return JsonNull.INSTANCE;
		}
		JsonObject o = new JsonObject();
		o.addProperty("x", p.getX());
		o.addProperty("y", p.getY());
		o.addProperty("z", p.getZ());
		return o;
	}

	static int distance(@Nullable BlockPos from, @Nullable BlockPos to) {
		if (from == null || to == null) {
			return -1;
		}
		double dx = to.getX() - from.getX();
		double dz = to.getZ() - from.getZ();
		return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
	}

	static JsonObject portrait(JsonObject into, int entity, Lookups look) {
		return portrait(into, entity, -1L, look);
	}

	/**
	 * {@code portrait} and {@code portraitKey}: from the folk in the client's world, else from the look the server sent
	 * ({@code lookKey}, a {@code FolkLook.key()}, −1 none), so a resident out of view still has their face.
	 */
	static JsonObject portrait(JsonObject into, int entity, long lookKey, Lookups look) {
		String url = entity < 0 ? null : look.portrait().apply(entity);
		String key = entity < 0 || url == null ? "" : look.portraitKey().apply(entity);
		if (url == null && lookKey != -1L) {
			url = look.lookPortrait().apply(lookKey);
			key = url == null ? "" : String.format(Locale.ROOT, "%016x", lookKey);
		}
		into.add("portrait", url == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(url));
		into.addProperty("portraitKey", key);
		return into;
	}

	static JsonArray traits(List<String> ids) {
		JsonArray a = new JsonArray();
		for (String t : ids) {
			JsonObject o = new JsonObject();
			o.addProperty("id", t);
			o.addProperty("name", Component.translatableWithFallback("hearth.npc.trait." + t, pretty(t)).getString());
			a.add(o);
		}
		return a;
	}

	static String pretty(String id) {
		String s = id.substring(id.lastIndexOf(':') + 1);
		s = s.substring(s.lastIndexOf('/') + 1).replace('_', ' ');
		return s.isEmpty() ? id : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
	}

	/** {@code hates | dislikes | neutral | likes | loves}, as the standing screens word an opinion. */
	public static String opinionWord(int opinion) {
		return opinion <= -50 ? "hates" : opinion <= -10 ? "dislikes" : opinion < 10 ? "neutral" : opinion < 50 ? "likes" : "loves";
	}

	/** {@code bitter | glum | even | content | glad}: a resident's mood, −100…100, in a word. */
	public static String moodWord(int mood) {
		return mood <= -40 ? "bitter" : mood <= -10 ? "glum" : mood < 10 ? "even" : mood < 40 ? "content" : "glad";
	}

	/** {@code dawn | morning | midday | afternoon | evening | night} for a minute of the day. */
	public static String partOfDay(int minute) {
		int h = Math.floorMod(minute, 1440) / 60;
		return h < 5 ? "night" : h < 8 ? "dawn" : h < 11 ? "morning" : h < 14 ? "midday" : h < 18 ? "afternoon" : h < 22 ? "evening" : "night";
	}

	static String titleName(String title) {
		return title.isEmpty() ? "" : Component.translatableWithFallback("hearth.title." + title, pretty(title)).getString();
	}

	// ---------------------------------------------------------------- talk

	/**
	 * {@code state.talk}: the conversation as the server last showed it, with the resident card and each option's facts
	 * when the detail payload arrived (a server from before 2026-10-19 sends none: the card's numbers are then 0 and every
	 * option's kind is inferred as "talk").
	 */
	public static JsonObject talk(TalkPayloads.Show show, TalkPayloads.@Nullable Detail detail, StandingPayloads.@Nullable Talk standing,
		Lookups look) {
		JsonObject o = new JsonObject();
		o.addProperty("entity", show.entity());
		JsonObject resident = new JsonObject();
		resident.addProperty("name", show.name());
		resident.addProperty("jobKey", show.title());
		resident.addProperty("job", tr(show.title()));
		resident.addProperty("town", show.settlement());
		String place = detail == null ? "" : detail.place();
		resident.addProperty("placeKey", place);
		resident.addProperty("place", place.isEmpty() ? "" : PropertyNames.building(place).getString());
		resident.add("traits", traits(detail == null ? List.of() : detail.traits()));
		int mood = detail == null ? 0 : detail.mood();
		resident.addProperty("mood", mood);
		resident.addProperty("moodWord", moodWord(mood));
		int opinion = detail != null ? detail.opinion() : standing != null ? standing.opinion() : 0;
		resident.addProperty("opinion", opinion);
		resident.addProperty("opinionWord", opinionWord(opinion));
		if (detail != null && !detail.memoryKind().isEmpty()) {
			JsonObject m = new JsonObject();
			m.addProperty("kind", detail.memoryKind());
			m.addProperty("weight", detail.memoryWeight());
			String key = "hearth.standing.memory." + detail.memoryKind().replace(':', '.').replace('/', '.');
			m.addProperty("text", Component.translatableWithFallback(key, pretty(detail.memoryKind())).getString());
			resident.add("memory", m);
		} else {
			resident.add("memory", JsonNull.INSTANCE);
		}
		portrait(resident, show.entity(), look);
		o.add("resident", resident);

		JsonObject st = new JsonObject();
		st.addProperty("town", show.settlement());
		st.addProperty("value", detail == null ? 0 : detail.standing());
		String title = detail == null ? "" : detail.title();
		st.addProperty("title", title);
		st.addProperty("titleName", titleName(title));
		if (detail != null && !detail.nextTitle().isEmpty()) {
			JsonObject next = new JsonObject();
			next.addProperty("title", detail.nextTitle());
			next.addProperty("titleName", titleName(detail.nextTitle()));
			next.addProperty("at", detail.nextAt());
			st.add("next", next);
		} else {
			st.add("next", JsonNull.INSTANCE);
		}
		o.add("standing", st);

		JsonObject time = new JsonObject();
		int minute = detail == null ? -1 : detail.minute();
		time.addProperty("minute", minute);
		time.addProperty("part", minute < 0 ? "" : partOfDay(minute));
		o.add("time", time);
		o.addProperty("speech", detail == null ? 0 : detail.speech());

		o.addProperty("line", show.speech().getString());
		o.addProperty("lastCheck", standing == null ? "" : standing.result().getString());

		JsonArray options = new JsonArray();
		List<String> ids = new java.util.ArrayList<>(show.options().size());
		for (TalkPayloads.Option opt : show.options()) {
			ids.add(opt.id());
		}
		int[] keys = dev.voidmc.hearth.npc.talk.OptionKeys.assign(ids, id -> {
			TalkPayloads.OptionInfo i = detail == null ? null : detail.info(id);
			return i == null ? null : i.kind();
		});
		int index = 0;
		for (TalkPayloads.Option opt : show.options()) {
			TalkPayloads.OptionInfo info = detail == null ? null : detail.info(opt.id());
			String kind = info == null ? opt.id().equals(dev.voidmc.hearth.npc.talk.NpcDialogue.BYE) ? "leave" : "talk" : info.kind();
			JsonObject e = new JsonObject();
			e.addProperty("key", keys[index++]);
			e.addProperty("id", opt.id());
			e.addProperty("label", opt.label().getString());
			e.addProperty("hint", text(opt.hint()));
			e.addProperty("tooltip", text(opt.tooltip()));
			e.addProperty("kind", kind);
			e.addProperty("odds", info == null ? -1 : info.odds());
			e.add("cost", info == null ? JsonNull.INSTANCE : coinsOrNull(info.cost()));
			if (info != null && !info.skill().isEmpty()) {
				JsonObject sk = new JsonObject();
				sk.addProperty("id", info.skill());
				String path = info.skill().substring(info.skill().indexOf(':') + 1);
				String ns = info.skill().contains(":") ? info.skill().substring(0, info.skill().indexOf(':')) : "mettle";
				sk.addProperty("name", Component.translatableWithFallback(ns + ".skill." + path, pretty(path)).getString());
				sk.addProperty("level", info.skillLevel());
				e.add("skill", sk);
			} else {
				e.add("skill", JsonNull.INSTANCE);
			}
			e.addProperty("locked", opt.locked());
			e.add("lockedReason", !opt.locked() ? JsonNull.INSTANCE : textOrNull(opt.tooltip() != null ? opt.tooltip() : opt.hint()));
			options.add(e);
		}
		o.add("options", options);
		return o;
	}

	// ---------------------------------------------------------------- quests

	/** One quest: on the board, in the journal, or tracked. */
	public static JsonObject quest(QuestInstance q, @Nullable UUID tracked, long gameTime, @Nullable BlockPos from, Lookups look) {
		JsonObject o = new JsonObject();
		o.addProperty("id", q.id.toString());
		o.addProperty("type", q.type.getSerializedName());
		o.addProperty("template", q.template.toString());
		o.addProperty("title", q.text.title());
		o.addProperty("summary", q.text.summary());
		o.addProperty("story", q.text.offer());
		o.addProperty("progressText", q.text.progress());
		o.addProperty("completeText", q.text.complete());
		JsonObject giver = new JsonObject();
		giver.addProperty("name", q.giver.name());
		giver.addProperty("job", q.giver.job().isEmpty() ? "" : Component.translatableWithFallback("hearth.npc.job." + q.giver.job(), pretty(q.giver.job())).getString());
		o.add("giver", giver);
		o.addProperty("town", q.settlementName == null ? "" : q.settlementName);
		o.addProperty("settlementId", q.settlementId);
		o.addProperty("state", q.status.getSerializedName());
		o.addProperty("minStanding", q.rules.minReputation());
		JsonArray objectives = new JsonArray();
		List<Objective> current = q.currentObjectives();
		for (Objective ob : q.objectives) {
			JsonObject j = new JsonObject();
			j.addProperty("kind", ob.kind.getSerializedName());
			j.addProperty("text", ob.label);
			j.addProperty("have", ob.progress);
			j.addProperty("need", ob.count);
			j.addProperty("done", ob.done());
			j.addProperty("current", current.contains(ob));
			j.addProperty("optional", false);
			j.addProperty("carried", ob.kind == dev.voidmc.hearth.quest.model.ObjectiveKind.DELIVER && !ob.done() ? look.carried().applyAsInt(ob.target) : 0);
			objectives.add(j);
		}
		o.add("objectives", objectives);
		boolean deadline = q.hasDeadline();
		o.addProperty("deadline", deadline);
		long left = deadline ? Math.max(0, q.timing.deadlineTick() - gameTime) : -1;
		o.addProperty("ticksLeft", left);
		o.addProperty("timeLeft", deadline ? Component.translatable("hearth.quest.timeleft", left / 24000, (left % 24000) / 1000).getString() : "");
		QuestReward r = q.reward;
		JsonObject reward = new JsonObject();
		reward.add("coins", coins(r.coin()));
		reward.addProperty("standing", r.reputation());
		JsonArray items = new JsonArray();
		for (QuestReward.ItemReward it : r.items()) {
			JsonObject i = new JsonObject();
			i.addProperty("item", it.item().toString());
			i.addProperty("count", it.count());
			items.add(i);
		}
		reward.add("items", items);
		reward.addProperty("text", r.text());
		o.add("reward", reward);
		o.addProperty("tracked", q.id.equals(tracked));
		BlockPos target = waypoint(q);
		o.add("waypoint", pos(target));
		o.addProperty("distance", distance(from, target));
		return o;
	}

	/** Where a quest wants the player now (as the HUD tracker's arrow): the open objective's place, or the hand-in. */
	public static @Nullable BlockPos waypoint(QuestInstance q) {
		if (q.status == QuestStatus.READY) {
			return q.turnInPos;
		}
		Objective o = q.firstOpen();
		if (o == null) {
			return q.turnInPos;
		}
		return switch (o.kind) {
			case DELIVER, PAY -> q.turnInPos;
			case KILL, BIOME, SCOUT, PROSPECT, HUNT -> null;
			default -> o.pos;
		};
	}

	/** {@code state.journal}: the active quests (tracked first), and the finished ones with counts. */
	public static JsonObject journal(PlayerQuests pq, long gameTime, @Nullable BlockPos from, Lookups look) {
		JsonObject o = new JsonObject();
		o.addProperty("tracked", pq.tracked == null ? null : pq.tracked.toString());
		JsonArray active = new JsonArray();
		for (QuestInstance q : pq.active) {
			if (q.id.equals(pq.tracked)) {
				active.add(quest(q, pq.tracked, gameTime, from, look));
			}
		}
		for (QuestInstance q : pq.active) {
			if (!q.id.equals(pq.tracked)) {
				active.add(quest(q, pq.tracked, gameTime, from, look));
			}
		}
		o.add("active", active);
		JsonObject finished = new JsonObject();
		int completed = 0;
		int failed = 0;
		JsonArray recent = new JsonArray();
		for (PlayerQuests.QuestRecord r : pq.history) {
			if (r.outcome() == QuestStatus.COMPLETED) {
				completed++;
			} else {
				failed++;
			}
			JsonObject j = new JsonObject();
			j.addProperty("title", r.title());
			j.addProperty("type", r.type().getSerializedName());
			j.addProperty("town", r.settlement());
			j.addProperty("giver", r.giver());
			j.addProperty("outcome", r.outcome().getSerializedName());
			j.addProperty("day", r.day());
			j.add("reward", coins(r.coin()));
			recent.add(j);
		}
		finished.addProperty("completed", completed);
		finished.addProperty("failed", failed);
		finished.addProperty("total", pq.history.size());
		finished.add("recent", recent);
		o.add("finished", finished);
		return o;
	}

	/**
	 * {@code state.questHud}: the tracked quest as the HUD tracker shows it, with the compass's bearing (degrees clockwise
	 * from where the player looks, −180…180) and distance, or null when nothing is tracked.
	 */
	public static JsonElement questHud(PlayerQuests pq, long gameTime, @Nullable BlockPos from, float yaw, Lookups look) {
		if (pq.tracked == null) {
			return JsonNull.INSTANCE;
		}
		QuestInstance q = pq.get(pq.tracked);
		if (q == null) {
			return JsonNull.INSTANCE;
		}
		JsonObject o = new JsonObject();
		o.addProperty("id", q.id.toString());
		o.addProperty("title", q.text.title());
		o.addProperty("town", q.settlementName == null ? "" : q.settlementName);
		boolean handIn = q.status == QuestStatus.READY || q.firstOpen() == null;
		o.addProperty("handIn", handIn);
		JsonArray lines = new JsonArray();
		Objective open = handIn ? null : q.firstOpen();
		if (open == null) {
			lines.add(Component.translatable("hearth.quest.hud.return", q.giver.name(), q.settlementName == null ? "" : q.settlementName).getString());
		} else {
			for (Objective ob : q.currentObjectives()) {
				lines.add(ob.count > 1 ? ob.label + " (" + ob.progress + "/" + ob.count + ")" : ob.label);
			}
		}
		o.add("objectives", lines);
		o.addProperty("step", open == null ? lines.get(0).getAsString() : open.label);
		o.addProperty("progress", open == null ? 0 : open.progress);
		o.addProperty("count", open == null ? 0 : open.count);
		BlockPos target = waypoint(q);
		o.add("target", pos(target));
		o.addProperty("distance", distance(from, target));
		if (from != null && target != null) {
			double angle = Math.toDegrees(Math.atan2(-(target.getX() - from.getX()), target.getZ() - from.getZ()));
			o.addProperty("bearing", (int) Math.round(wrap(angle - yaw)));
		} else {
			o.add("bearing", JsonNull.INSTANCE);
		}
		o.addProperty("timeLeft", q.hasDeadline()
			? Component.translatable("hearth.quest.timeleft", Math.max(0, q.timing.deadlineTick() - gameTime) / 24000,
				(Math.max(0, q.timing.deadlineTick() - gameTime) % 24000) / 1000).getString() : "");
		return o;
	}

	/**
	 * {@code state.wantedTrack}: the wanted poster the player tracks (docs/OVERLAY.md), where its quarry was last placed
	 * (to {@link QuestPayloads.PosterTrack#GRID} blocks) with the compass's bearing and distance, or null.
	 */
	public static JsonElement wantedTrack(QuestPayloads.@Nullable PosterTrack t, @Nullable BlockPos from, float yaw) {
		if (t == null || !t.active()) {
			return JsonNull.INSTANCE;
		}
		JsonObject o = new JsonObject();
		o.addProperty("player", t.player());
		o.addProperty("name", t.name());
		o.add("owed", coins(t.owed()));
		o.addProperty("outlaw", t.outlaw());
		o.addProperty("crimeKey", t.crime());
		o.addProperty("crime", t.crime().isEmpty() ? "" : tr(t.crime()));
		o.addProperty("settlementId", t.settlementId());
		o.addProperty("town", t.town());
		o.addProperty("online", t.online());
		BlockPos near = t.located() ? t.near() : null;
		o.add("near", pos(near));
		o.addProperty("distance", distance(from, near));
		if (from != null && near != null) {
			double angle = Math.toDegrees(Math.atan2(-(near.getX() - from.getX()), near.getZ() - from.getZ()));
			o.addProperty("bearing", (int) Math.round(wrap(angle - yaw)));
		} else {
			o.add("bearing", JsonNull.INSTANCE);
		}
		return o;
	}

	static double wrap(double degrees) {
		double d = degrees % 360;
		if (d > 180) {
			d -= 360;
		} else if (d < -180) {
			d += 360;
		}
		return d;
	}

	/** {@code state.board}: a settlement's notice board as this player sees it. */
	public static JsonObject board(QuestPayloads.Board b, @Nullable PlayerQuests mine, long gameTime, @Nullable BlockPos from, Lookups look) {
		return board(b, mine, gameTime, from, look, null);
	}

	/** {@link #board} marking the wanted poster the player tracks ({@code wanted[].tracked}; {@code trackedPoster} a UUID string). */
	public static JsonObject board(QuestPayloads.Board b, @Nullable PlayerQuests mine, long gameTime, @Nullable BlockPos from, Lookups look,
		@Nullable String trackedPoster) {
		JsonObject o = new JsonObject();
		o.addProperty("settlementId", b.settlementId());
		o.add("pos", pos(b.boardPos()));
		o.addProperty("town", b.settlementName());
		o.addProperty("tier", b.tier());
		o.addProperty("reputation", b.reputation());
		o.addProperty("title", b.title());
		JsonArray posts = new JsonArray();
		int open = 0;
		int locked = 0;
		for (QuestPayloads.Board.Entry e : b.entries()) {
			JsonObject p = new JsonObject();
			boolean taken = mine != null && mine.get(e.quest().id) != null;
			String state = taken ? "taken" : e.lock().isEmpty() ? "open" : "locked";
			if (state.equals("open")) {
				open++;
			} else if (state.equals("locked")) {
				locked++;
			}
			p.addProperty("state", state);
			p.addProperty("lock", e.lock());
			p.add("quest", quest(e.quest(), mine == null ? null : mine.tracked, gameTime, from, look));
			posts.add(p);
		}
		o.add("posts", posts);
		JsonArray handIns = new JsonArray();
		for (UUID id : b.handIns()) {
			handIns.add(id.toString());
		}
		o.add("handIns", handIns);
		JsonArray wanted = new JsonArray();
		for (QuestPayloads.Board.Poster p : b.posters()) {
			JsonObject w = new JsonObject();
			w.addProperty("name", p.name());
			w.addProperty("owed", p.owed());
			w.addProperty("outlaw", p.outlaw());
			w.addProperty("crimeKey", p.crime());
			w.addProperty("crime", p.crime().isEmpty() ? "" : tr(p.crime()));
			w.addProperty("player", p.player());
			w.addProperty("tracked", !p.player().isEmpty() && p.player().equals(trackedPoster));
			wanted.add(w);
		}
		o.add("wanted", wanted);
		JsonObject counts = new JsonObject();
		counts.addProperty("open", open);
		counts.addProperty("locked", locked);
		counts.addProperty("wanted", b.posters().size());
		counts.addProperty("handIns", b.handIns().size());
		o.add("counts", counts);
		return o;
	}

	// ---------------------------------------------------------------- market and bank

	private static JsonObject flags(int f) {
		JsonObject o = new JsonObject();
		o.addProperty("needed", (f & MarketView.NEEDED) != 0);
		o.addProperty("townCannotPay", (f & MarketView.TOWN_CANNOT_PAY) != 0);
		o.addProperty("noRoom", (f & MarketView.NO_ROOM) != 0);
		o.addProperty("cannotAfford", (f & MarketView.CANNOT_AFFORD) != 0);
		o.addProperty("refused", (f & MarketView.REFUSED) != 0);
		o.addProperty("stolen", (f & MarketView.STOLEN) != 0);
		o.addProperty("fenced", (f & MarketView.FENCED) != 0);
		o.addProperty("sour", (f & MarketView.SOUR) != 0);
		return o;
	}

	private static JsonArray rows(List<MarketView.Offer> offers, MarketView.@Nullable HaggleView haggle) {
		JsonArray a = new JsonArray();
		for (MarketView.Offer r : offers) {
			JsonObject o = new JsonObject();
			o.addProperty("id", r.id());
			JsonObject it = item(r.stack());
			o.addProperty("item", it.get("item").getAsString());
			o.addProperty("name", it.get("name").getAsString());
			o.addProperty("lot", r.stack().getCount());
			o.add("price", coins(r.price()));
			o.addProperty("available", r.available());
			o.addProperty("owned", r.owned());
			o.addProperty("goods", r.goods());
			o.add("flags", flags(r.flags()));
			o.addProperty("canTrade", r.available() > 0);
			o.addProperty("haggling", haggle != null && haggle.row() == r.id());
			a.add(o);
		}
		return a;
	}

	/** {@code state.market}: the counter as the server last sent it (the open menu's view). */
	public static JsonObject market(int containerId, MarketView v, Lookups look) {
		JsonObject o = new JsonObject();
		o.addProperty("containerId", containerId);
		o.addProperty("town", v.settlement());
		o.addProperty("culture", v.culture());
		o.addProperty("shop", v.shop());
		o.addProperty("reputation", v.reputation());
		o.add("purse", coins(v.balance()));
		o.add("till", coins(v.treasury()));
		o.addProperty("note", unquoted(v.note().getString()));
		o.addProperty("status", v.status().getString());
		boolean sour = v.sells().stream().anyMatch(r -> r.has(MarketView.SOUR)) || v.buys().stream().anyMatch(r -> r.has(MarketView.SOUR));
		o.addProperty("sour", sour);
		MarketView.Merchant m = v.merchant();
		if (m != null) {
			JsonObject j = new JsonObject();
			j.addProperty("name", m.name());
			j.addProperty("jobKey", m.job());
			j.addProperty("job", tr(m.job()));
			j.addProperty("entity", m.entity());
			j.add("traits", traits(m.traits()));
			j.addProperty("mood", m.mood());
			j.addProperty("moodWord", moodWord(m.mood()));
			j.addProperty("opinion", m.opinion());
			j.addProperty("opinionWord", opinionWord(m.opinion()));
			j.addProperty("sour", sour);
			portrait(j, m.entity(), m.look(), look);
			o.add("merchant", j);
		} else {
			o.add("merchant", JsonNull.INSTANCE);
		}
		o.add("sells", rows(v.sells(), v.haggle() != null && v.haggle().playerBuys() ? v.haggle() : null));
		o.add("buys", rows(v.buys(), v.haggle() != null && !v.haggle().playerBuys() ? v.haggle() : null));
		MarketView.HaggleView h = v.haggle();
		if (h != null) {
			JsonObject j = new JsonObject();
			j.addProperty("row", h.row());
			JsonObject it = item(h.stack());
			j.addProperty("item", it.get("item").getAsString());
			j.addProperty("name", it.get("name").getAsString());
			j.addProperty("count", h.stack().getCount());
			j.addProperty("playerBuys", h.playerBuys());
			j.add("list", coins(h.list()));
			j.add("word", coins(h.counter()));
			j.add("offer", coins(h.lastOffer()));
			j.addProperty("patience", h.patience());
			j.addProperty("offersLeft", h.offersLeft());
			j.addProperty("line", h.line().getString());
			if (h.bandShown()) {
				JsonObject band = new JsonObject();
				band.add("low", coins(h.bandLow()));
				band.add("high", coins(h.bandHigh()));
				j.add("band", band);
			} else {
				j.add("band", JsonNull.INSTANCE);
			}
			j.addProperty("speech", h.speech());
			j.addProperty("bandSpeech", Haggle.BAND_SPEECH);
			j.addProperty("exactSpeech", Haggle.EXACT_SPEECH);
			j.add("takes", takes(h));
			j.addProperty("takesLast", h.lastOffer() == h.list() ? -1
				: Haggle.takesOdds(h.playerBuys(), h.list(), h.counter(), h.bandLow(), h.bandHigh(), h.lastOffer()));
			o.add("haggle", j);
		} else {
			o.add("haggle", JsonNull.INSTANCE);
		}
		return o;
	}

	static String tab(int tab) {
		return switch (tab) {
			case BankView.TAB_PROPERTY -> "property";
			case BankView.TAB_WORKS -> "works";
			case BankView.TAB_CONTRACTS -> "contracts";
			default -> "bank";
		};
	}

	/** {@code state.bank}: the bank or town hall counter (the open menu's view). */
	public static JsonObject bank(int containerId, BankView v) {
		JsonObject o = new JsonObject();
		o.addProperty("containerId", containerId);
		o.addProperty("town", v.settlement());
		o.addProperty("tab", tab(v.tab()));
		o.addProperty("hasBank", v.hasBank());
		o.addProperty("hasWorks", v.hasWorks());
		o.add("deposit", coins(v.deposit()));
		o.add("carried", coins(v.carried()));
		o.add("treasury", coins(v.treasury()));
		JsonObject em = new JsonObject();
		em.addProperty("carried", v.emeralds());
		em.addProperty("reserve", v.emeraldReserve());
		em.add("buy", coins(v.emeraldBuy()));
		em.add("sell", coins(v.emeraldSell()));
		o.add("emeralds", em);
		o.addProperty("reputation", v.reputation());
		o.addProperty("status", v.status().getString());
		JsonArray owned = new JsonArray();
		JsonArray forSale = new JsonArray();
		for (BankView.PlotOffer p : v.plots()) {
			JsonObject j = new JsonObject();
			j.addProperty("plot", p.plot());
			j.addProperty("archetype", p.building());
			j.addProperty("name", PropertyNames.building(p.building()).getString());
			j.addProperty("built", p.built());
			j.addProperty("sizeX", p.sizeX());
			j.addProperty("sizeZ", p.sizeZ());
			j.addProperty("storeys", p.storeys());
			j.add("price", coins(p.price()));
			j.addProperty("minStanding", p.minReputation());
			j.addProperty("yours", p.has(BankView.PlotOffer.YOURS));
			j.addProperty("lowStanding", p.has(BankView.PlotOffer.LOW_STANDING));
			j.addProperty("cannotAfford", p.has(BankView.PlotOffer.CANNOT_AFFORD));
			j.addProperty("business", p.has(BankView.PlotOffer.BUSINESS));
			j.addProperty("canHire", p.has(BankView.PlotOffer.CAN_HIRE));
			j.add("income", coins(p.income()));
			j.addProperty("workers", p.workers());
			(p.has(BankView.PlotOffer.YOURS) ? owned : forSale).add(j);
		}
		o.add("owned", owned);
		o.add("forSale", forSale);
		JsonArray projects = new JsonArray();
		for (BankView.ProjectOffer p : v.projects()) {
			JsonObject j = new JsonObject();
			j.addProperty("plot", p.plot());
			j.addProperty("archetype", p.building());
			j.addProperty("name", PropertyNames.building(p.building()).getString());
			j.addProperty("rising", p.rising());
			j.addProperty("stage", p.stage());
			j.addProperty("stages", p.stages());
			j.add("cost", coins(p.cost()));
			j.add("given", coins(p.given()));
			j.add("yours", coins(p.yours()));
			j.add("fund", coins(p.fund()));
			j.addProperty("stalled", p.stalled());
			projects.add(j);
		}
		o.add("projects", projects);
		JsonArray contracts = new JsonArray();
		for (BankView.ContractOffer c : v.contracts()) {
			JsonObject j = new JsonObject();
			j.addProperty("id", c.id());
			j.addProperty("goods", c.goods());
			j.addProperty("goodsName", Component.translatableWithFallback("hearth.economy.goods." + c.goods(), pretty(c.goods())).getString());
			j.addProperty("units", c.units());
			j.addProperty("delivered", c.delivered());
			j.add("pays", coins(c.total()));
			j.addProperty("daysLeft", c.daysLeft());
			j.addProperty("taken", c.has(BankView.ContractOffer.TAKEN));
			j.addProperty("carrying", c.has(BankView.ContractOffer.CARRYING));
			contracts.add(j);
		}
		o.add("contracts", contracts);
		JsonArray ledger = new JsonArray();
		for (dev.voidmc.hearth.economy.bank.BankLedger.Line l : v.ledger()) {
			ledger.add(ledgerLine(l));
		}
		o.add("ledger", ledger);
		return o;
	}

	/** One {@code bank.ledger} line: what moved, where, about what, and the line in words. */
	static JsonObject ledgerLine(dev.voidmc.hearth.economy.bank.BankLedger.Line l) {
		JsonObject j = new JsonObject();
		j.addProperty("day", l.day());
		j.addProperty("kind", l.kind());
		j.add("purse", coins(l.purse()));
		j.add("deposit", coins(l.deposit()));
		j.addProperty("town", l.town());
		j.addProperty("subject", l.subject());
		String subjectName = switch (l.kind()) {
			case "buy_plot", "hire", "fund" -> l.subject().isEmpty() ? "" : PropertyNames.building(l.subject()).getString();
			case "deliver" -> l.subject().isEmpty() ? ""
				: Component.translatableWithFallback("hearth.economy.goods." + l.subject(), pretty(l.subject())).getString();
			default -> l.subject();
		};
		j.addProperty("subjectName", subjectName);
		// the amount that matters to the player: what left or reached the purse, else the deposit's change
		long moved = l.kind().equals("deposit") || l.kind().equals("withdraw") ? Math.abs(l.deposit()) : Math.abs(l.purse());
		j.addProperty("text", Component.translatableWithFallback("hearth.economy.bank.ledger." + l.kind(), l.kind() + " %s",
			CoinFormat.format(moved), subjectName).getString());
		return j;
	}

	/** How many offers {@link #takes} lists at most. */
	static final int TAKES_POINTS = 17;

	/**
	 * {@code haggle.takes}: "he takes it" odds for offers from the far end of the merchant's possible limit up to his word,
	 * ascending by bits; a page reads the point at or below its offer (or interpolates).
	 */
	static JsonArray takes(MarketView.HaggleView h) {
		long list = Math.max(1, h.list());
		long counter = h.counter();
		long far;
		if (h.bandShown()) {
			far = h.playerBuys() ? h.bandLow() : h.bandHigh();
		} else {
			far = h.playerBuys() ? Math.max(0, (long) Math.floor(list * 0.4)) : (long) Math.ceil(list * 1.6);
		}
		long lo = Math.min(far, counter);
		long hi = Math.max(far, counter);
		JsonArray out = new JsonArray();
		long last = Long.MIN_VALUE;
		for (int k = 0; k < TAKES_POINTS; k++) {
			long bits = lo + Math.round((hi - lo) * (k / (double) (TAKES_POINTS - 1)));
			if (bits == last) {
				continue;
			}
			last = bits;
			JsonObject p = new JsonObject();
			p.addProperty("bits", bits);
			p.addProperty("odds", Haggle.takesOdds(h.playerBuys(), h.list(), counter, h.bandLow(), h.bandHigh(), bits));
			out.add(p);
		}
		return out;
	}

	// ---------------------------------------------------------------- the law

	/**
	 * {@code state.confront}: a guard or witness stops the player, with their card, the charge and each choice's facts when
	 * the detail payload arrived (else those are empty and every choice's kind is its id).
	 */
	public static JsonObject confront(LawPayloads.Confront c, LawPayloads.@Nullable ConfrontDetail d, Lookups look) {
		JsonObject o = new JsonObject();
		o.addProperty("entity", c.entity());
		o.addProperty("kind", d == null ? "" : d.kind());
		o.addProperty("name", c.name());
		o.addProperty("line", c.speech().getString());
		o.add("bounty", coins(c.bounty()));
		JsonObject who = new JsonObject();
		who.addProperty("name", c.name());
		who.addProperty("jobKey", d == null ? "" : d.job());
		who.addProperty("job", d == null ? "" : tr(d.job()));
		who.add("traits", traits(d == null ? List.of() : d.traits()));
		int opinion = d == null ? 0 : d.opinion();
		who.addProperty("opinion", opinion);
		who.addProperty("opinionWord", opinionWord(opinion));
		portrait(who, c.entity(), look);
		o.add("folk", who);
		JsonObject charge = new JsonObject();
		String offence = d == null ? "" : d.offence();
		charge.addProperty("offenceKey", offence);
		charge.addProperty("offence", offence.isEmpty() ? "" : tr(offence));
		charge.addProperty("offences", d == null ? 0 : d.offences());
		charge.addProperty("witnesses", d == null ? "" : d.witnesses());
		charge.addProperty("town", d == null ? "" : d.town());
		o.add("charge", charge);
		JsonObject st = new JsonObject();
		st.addProperty("town", d == null ? "" : d.town());
		st.addProperty("value", d == null ? 0 : d.standing());
		st.addProperty("title", d == null ? "" : d.title());
		st.addProperty("titleName", d == null ? "" : titleName(d.title()));
		o.add("standing", st);
		JsonArray choices = new JsonArray();
		int key = 0;
		for (LawPayloads.Choice ch : c.choices()) {
			LawPayloads.ChoiceInfo info = d == null ? null : d.info(ch.id());
			JsonObject j = new JsonObject();
			j.addProperty("key", ++key);
			j.addProperty("id", ch.id());
			j.addProperty("label", ch.label().getString());
			j.addProperty("enabled", ch.enabled());
			j.addProperty("kind", info == null ? ch.id() : info.kind());
			j.addProperty("odds", info == null ? -1 : info.odds());
			j.add("cost", info == null ? JsonNull.INSTANCE : coinsOrNull(info.cost()));
			j.addProperty("minutes", info == null ? -1 : info.minutes());
			choices.add(j);
		}
		o.add("choices", choices);
		portrait(o, c.entity(), look);
		return o;
	}

	/** {@code state.lockpick}: the attempt's discrete state (pins and pick move natively). */
	public static JsonObject lockpick(HearthViews.Lockpick l) {
		JsonObject o = new JsonObject();
		LawPayloads.LockpickStart s = l.start();
		o.addProperty("session", s.session());
		o.addProperty("what", s.what().getString());
		o.addProperty("tier", s.tier());
		o.addProperty("tierKey", "hearth.law.lock.tier." + s.tier());
		o.addProperty("skill", s.skill());
		o.addProperty("picks", l.picks());
		o.addProperty("state", l.state());
		return o;
	}

	/** {@code state.pickpocket}: the pockets, the mark's awareness and the lift under way (rates let a host animate natively). */
	public static JsonObject pickpocket(LawPayloads.Pocket p) {
		return pickpocket(p, Lookups.NONE);
	}

	/** {@link #pickpocket(LawPayloads.Pocket)} with the victim's face ({@code entity}, {@code portrait}, {@code portraitKey}). */
	public static JsonObject pickpocket(LawPayloads.Pocket p, Lookups look) {
		JsonObject o = new JsonObject();
		o.addProperty("session", p.session());
		o.addProperty("victim", p.victim());
		o.addProperty("entity", p.entity());
		portrait(o, p.entity(), p.look(), look);
		o.addProperty("asleep", p.asleep());
		JsonArray items = new JsonArray();
		int taken = 0;
		for (int i = 0; i < p.items().size(); i++) {
			ItemStack s = p.items().get(i);
			JsonObject j = item(s);
			j.addProperty("slot", i);
			j.addProperty("empty", s.isEmpty());
			if (s.isEmpty()) {
				taken++;
			}
			items.add(j);
		}
		o.add("items", items);
		o.addProperty("taken", taken);
		o.addProperty("awareness", p.awareness());
		o.addProperty("awarenessRate", p.awarenessRate());
		o.addProperty("lifting", p.lifting());
		o.addProperty("liftProgress", p.liftProgress());
		o.addProperty("liftRate", p.liftRate());
		o.addProperty("state", switch (p.state()) {
			case LawPayloads.Pocket.CAUGHT -> "caught";
			case LawPayloads.Pocket.DONE -> "done";
			default -> "open";
		});
		return o;
	}

	/** {@code state.law}: the law HUD's facts (the HUD itself stays native). */
	public static JsonObject law(LawPayloads.Hint hint, LawPayloads.Status status, long statusAgeMs, LawPayloads.Stealth stealth) {
		JsonObject o = new JsonObject();
		if (hint.owner().getString().isEmpty()) {
			o.add("hint", JsonNull.INSTANCE);
		} else {
			JsonObject h = new JsonObject();
			h.addProperty("owner", hint.owner().getString());
			h.addProperty("animal", hint.animal());
			h.addProperty("lockTier", hint.lockTier());
			h.addProperty("locked", hint.locked());
			h.addProperty("hasKey", hint.hasKey());
			h.addProperty("yours", hint.yours());
			o.add("hint", h);
		}
		JsonObject s = new JsonObject();
		s.addProperty("town", status.settlement());
		s.add("bounty", coins(status.bounty()));
		s.addProperty("wanted", status.bounty() > 0);
		s.addProperty("outlaw", status.outlaw());
		s.addProperty("suspicion", status.suspicion());
		long jail = Math.max(0, status.jailTicks() - Math.max(0, statusAgeMs) / 50);
		s.addProperty("jailTicks", jail);
		s.addProperty("jailSeconds", (jail + 19) / 20);
		s.addProperty("pillory", status.pillory());
		s.addProperty("wantedElsewhere", status.wantedElsewhere());
		o.add("status", s);
		JsonObject st = new JsonObject();
		st.addProperty("active", stealth.active());
		st.addProperty("visibility", stealth.visibility());
		st.addProperty("noticed", stealth.noticed());
		o.add("stealth", st);
		return o;
	}

	// ---------------------------------------------------------------- dice and standing

	/** {@code state.dice}: the table (the open menu's view); the dice tumble natively. */
	public static JsonObject dice(int containerId, DiceView v) {
		return dice(containerId, v, Lookups.NONE);
	}

	/** {@link #dice(int, DiceView)} with the opponent's face ({@code opponent.entity}, {@code portrait}, {@code portraitKey}). */
	public static JsonObject dice(int containerId, DiceView v, Lookups look) {
		JsonObject o = new JsonObject();
		o.addProperty("containerId", containerId);
		DiceGame.Phase[] phases = DiceGame.Phase.values();
		o.addProperty("phase", v.phase() >= 0 && v.phase() < phases.length ? phases[v.phase()].name().toLowerCase(Locale.ROOT) : "over");
		JsonObject opp = new JsonObject();
		opp.addProperty("name", v.folkName());
		opp.addProperty("temper", v.personality());
		opp.addProperty("entity", v.folkEntity());
		portrait(opp, v.folkEntity(), v.folkLook(), look);
		o.add("opponent", opp);
		o.add("stake", coins(v.stake()));
		o.addProperty("target", v.target());
		JsonObject scores = new JsonObject();
		scores.addProperty("player", v.playerScore());
		scores.addProperty("folk", v.folkScore());
		o.add("scores", scores);
		o.addProperty("turnPoints", v.turnPoints());
		o.addProperty("turn", v.turn());
		JsonArray dice = new JsonArray();
		for (int i = 0; i < v.dice().length; i++) {
			JsonObject d = new JsonObject();
			int bit = 1 << i;
			d.addProperty("index", i);
			d.addProperty("face", v.dice()[i]);
			d.addProperty("kept", (v.keptMask() & bit) != 0);
			d.addProperty("selected", (v.selectedMask() & bit) != 0);
			d.addProperty("scoring", (v.scoringMask() & bit) != 0);
			d.addProperty("rolled", (v.rolledMask() & bit) != 0);
			dice.add(d);
		}
		o.add("dice", dice);
		o.addProperty("selectionScore", v.selectionScore());
		o.addProperty("rollSerial", v.rollSerial());
		JsonArray stakes = new JsonArray();
		for (Integer s : v.stakes()) {
			stakes.add(coins(s));
		}
		o.add("stakes", stakes);
		o.addProperty("line", v.line().getString());
		o.addProperty("status", v.status().getString());
		o.addProperty("winner", v.winner());
		o.addProperty("cheatCaught", v.cheatCaught());
		o.addProperty("loaded", v.loaded() == 2 ? "inPlay" : v.loaded() == 1 ? "carried" : "none");
		o.add("purse", coins(v.balance()));
		o.addProperty("canRoll", v.canRoll());
		o.addProperty("canBank", v.canBank());
		return o;
	}

	/** {@code state.reputation}: the reputation page (fame, looks, company, every town known). */
	public static JsonObject reputation(StandingPayloads.Page p) {
		JsonObject o = new JsonObject();
		o.addProperty("fame", p.fame());
		o.addProperty("renown", p.renown());
		int tier = p.fame() < 5 ? 0 : p.fame() < 20 ? 1 : p.fame() < 50 ? 2 : p.fame() < 80 ? 3 : 4;
		o.addProperty("fameWord", tr("hearth.standing.fame." + tier));
		o.addProperty("renownWord", tier == 0 || p.renown() == 0 ? "" : tr(p.renown() > 0 ? "hearth.standing.renown.good" : "hearth.standing.renown.bad"));
		o.addProperty("charisma", p.charisma());
		float c = p.charisma();
		o.addProperty("charismaWord", tr("hearth.standing.charisma."
			+ (c < -0.5F ? "repellent" : c < -0.15F ? "offputting" : c < 0.15F ? "plain" : c < 0.45F ? "pleasant" : "charming")));
		o.addProperty("menace", p.menace());
		float m = p.menace();
		o.addProperty("menaceWord", tr("hearth.standing.menace." + (m < 0.2F ? "harmless" : m < 0.45F ? "wary" : m < 0.7F ? "dangerous" : "terrifying")));
		JsonArray looks = new JsonArray();
		for (String n : p.notes()) {
			looks.add(tr("hearth.standing.look." + n));
		}
		o.add("looks", looks);
		o.addProperty("company", p.company());
		JsonArray members = new JsonArray();
		p.members().forEach(members::add);
		o.add("members", members);
		o.addProperty("companies", p.companies());
		JsonArray towns = new JsonArray();
		for (StandingPayloads.TownRow t : p.towns()) {
			JsonObject j = new JsonObject();
			j.addProperty("id", t.id());
			j.addProperty("name", t.name());
			j.addProperty("culture", t.culture());
			j.addProperty("tier", t.tier());
			j.addProperty("value", t.standing());
			j.addProperty("title", t.title());
			j.addProperty("titleName", titleName(t.title()));
			j.addProperty("owns", t.owner());
			j.addProperty("distance", t.distance());
			j.addProperty("here", t.here());
			towns.add(j);
		}
		o.add("towns", towns);
		return o;
	}
}
