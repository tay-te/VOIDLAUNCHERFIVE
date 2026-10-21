package dev.voidmc.hearth.client.overlay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.hearth.npc.companion.CompanionGear;
import dev.voidmc.hearth.social.lives.LivesPayloads;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The living-world screens' state (docs/OVERLAY.md {@code household}, {@code people}, {@code talk.stranger},
 * {@code reputation.retinue}, {@code reputation.towns[].burgher}, {@code contest}, {@code arrival}, {@code personTrack};
 * Figma section "Hearth - Living world screens (explore)", picked 2026-10-21): what the server sent, with coin as crowns,
 * marks and bits, portraits from looks and words for numbers. Pure apart from translation: tested from plain JSON.
 */
public final class LivingWorldJson {
	private LivingWorldJson() {
	}

	/** One stack in the player's hands for "Take this" (an inventory index: 0–8 the hotbar, 40 the off hand). */
	public record Hand(int slot, ItemStack stack) {
	}

	// ---------------------------------------------------------------- shared

	static @Nullable JsonObject parse(@Nullable String json) {
		if (json == null || json.isEmpty()) {
			return null;
		}
		try {
			JsonElement e = JsonParser.parseString(json);
			return e.isJsonObject() ? e.getAsJsonObject() : null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	static long getLong(JsonObject o, String key, long dflt) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsLong() : dflt;
	}

	static String getString(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}

	static JsonArray array(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
	}

	/** {@code portrait} and {@code portraitKey} from a look's 16 hex digits (the {@code look} the server sent; "" none). */
	static void portrait(JsonObject into, String lookHex, LongFunction<@Nullable String> ofKey) {
		String url = null;
		String key = "";
		if (!lookHex.isEmpty()) {
			try {
				long k = Long.parseUnsignedLong(lookHex, 16);
				url = ofKey.apply(k);
				key = url == null ? "" : lookHex;
			} catch (NumberFormatException e) {
				url = null;
			}
		}
		into.add("portrait", url == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(url));
		into.addProperty("portraitKey", key);
	}

	static JsonArray traitNames(JsonArray ids) {
		List<String> list = new ArrayList<>();
		for (JsonElement e : ids) {
			list.add(e.getAsString());
		}
		return HearthStateJson.traits(list);
	}

	/** 0–4 for a companion's loyalty, −100…100 ({@code leaveBelow}: under it they go home). */
	public static int loyaltyStep(int loyalty, int leaveBelow) {
		return loyalty < leaveBelow ? 0 : loyalty <= -20 ? 1 : loyalty < 20 ? 2 : loyalty < 60 ? 3 : 4;
	}

	// ---------------------------------------------------------------- the household (F2, F3)

	/**
	 * {@code state.household}: the summary the server sent ({@code npc/place_summary}) with every wage as coin, faces,
	 * words, the roster's totals, what the player holds ({@code hands}) and the companion whose gear screen is open.
	 */
	public static JsonElement household(@Nullable JsonObject raw, LongFunction<@Nullable String> ofKey, List<Hand> hands, @Nullable String gearFor) {
		if (raw == null) {
			return JsonNull.INSTANCE;
		}
		JsonObject o = raw.deepCopy();
		long daily = 0;
		int count = 0;
		for (JsonElement e : array(o, "companions")) {
			JsonObject c = e.getAsJsonObject();
			long wage = getLong(c, "wage", 0);
			daily += wage;
			count++;
			c.add("wageCoins", HearthStateJson.coins(wage));
			portrait(c, getString(c, "look"), ofKey);
			c.add("traitNames", traitNames(array(c, "traits")));
			int step = loyaltyStep((int) getLong(c, "loyalty", 0), (int) getLong(c, "leaveBelow", -60));
			c.addProperty("loyaltyStep", step);
			c.addProperty("loyaltyWord", HearthStateJson.tr("hearth.household.loyalty." + step));
			String orders = getString(c, "orders");
			c.addProperty("ordersText", Component.translatableWithFallback("hearth.household.orders." + orders, orders).getString());
			long since = getLong(c, "since", 0);
			long today = getLong(c, "today", since);
			long paid = getLong(c, "paidDay", today);
			c.addProperty("servedDays", Math.max(0, today - since + 1));
			c.addProperty("paidDays", Math.max(0, Math.min(today, paid) - since + 1));
			c.addProperty("opinionWord", HearthStateJson.opinionWord((int) getLong(c, "opinion", 0)));
			String weapon = "";
			for (JsonElement s : array(c, "slots")) {
				JsonObject slot = s.getAsJsonObject();
				if (getString(slot, "slot").equals("mainhand")) {
					weapon = getString(slot, "name");
				}
			}
			c.addProperty("weapon", weapon);
		}
		for (JsonElement e : array(o, "houses")) {
			JsonObject h = e.getAsJsonObject();
			for (JsonElement s : array(h, "servants")) {
				JsonObject sv = s.getAsJsonObject();
				long wage = getLong(sv, "wage", 0);
				daily += wage;
				count++;
				sv.add("wageCoins", HearthStateJson.coins(wage));
				portrait(sv, getString(sv, "look"), ofKey);
				String duty = getString(sv, "duty");
				sv.addProperty("dutyText", duty.isEmpty() ? "" : Component.translatableWithFallback("hearth.household.duty." + duty, duty).getString());
				long since = getLong(sv, "since", 0);
				long today = getLong(sv, "today", since);
				long paid = getLong(sv, "paidDay", today);
				sv.addProperty("servedDays", Math.max(0, today - since + 1));
				sv.addProperty("paidDays", Math.max(0, Math.min(today, paid) - since + 1));
			}
		}
		o.addProperty("inService", count);
		o.add("dailyWages", HearthStateJson.coins(daily));
		JsonArray held = new JsonArray();
		for (Hand hand : hands) {
			JsonObject j = HearthStateJson.item(hand.stack());
			j.addProperty("slot", hand.slot());
			j.addProperty("empty", hand.stack().isEmpty());
			EquipmentSlot gives = hand.stack().isEmpty() ? null : CompanionGear.slotFor(hand.stack());
			j.addProperty("gives", gives == null ? "" : gives.getSerializedName());
			held.add(j);
		}
		o.add("hands", held);
		o.add("gearFor", gearFor == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(gearFor));
		return o;
	}

	// ---------------------------------------------------------------- people (F4)

	/** {@code state.people}: the named characters the player has met, with each one's story in full. */
	public static JsonElement people(LivesPayloads.@Nullable People p, LongFunction<@Nullable String> ofKey, LivesPayloads.@Nullable Followed followed) {
		if (p == null) {
			return JsonNull.INSTANCE;
		}
		JsonArray rows = new JsonArray();
		int moved = 0;
		java.util.Set<String> towns = new java.util.LinkedHashSet<>();
		for (LivesPayloads.Row r : p.rows()) {
			JsonObject o = new JsonObject();
			o.addProperty("name", r.name());
			o.addProperty("given", r.given());
			o.addProperty("roleId", r.role());
			o.addProperty("role", Component.translatable("hearth.lives.role." + r.role()).getString());
			o.addProperty("town", r.town());
			o.addProperty("thread", r.thread());
			o.addProperty("beat", r.beat());
			o.addProperty("story", Component.translatable("hearth.lives.story." + r.thread() + "." + r.beat(), r.given()).getString());
			o.addProperty("ended", r.ended());
			o.addProperty("opinion", r.relation());
			o.addProperty("opinionWord", HearthStateJson.opinionWord(r.relation()));
			o.addProperty("yours", r.by());
			towns.add(r.town());
			if (r.by()) {
				moved++;
			}
			JsonObject d = parse(r.detail());
			if (d != null) {
				for (String k : d.keySet()) {
					if (!k.equals("look") && !k.equals("traits")) {
						o.add(k, d.get(k));
					}
				}
				portrait(o, getString(d, "look"), ofKey);
				o.add("traits", traitNames(array(d, "traits")));
				JsonElement chose = d.get("chose");
				if (chose != null && chose.isJsonObject()) {
					long cost = getLong(chose.getAsJsonObject(), "cost", 0);
					chose.getAsJsonObject().add("costCoins", cost > 0 ? HearthStateJson.coins(cost) : JsonNull.INSTANCE);
				}
				for (JsonElement c : array(o, "choices")) {
					long cost = getLong(c.getAsJsonObject(), "cost", 0);
					c.getAsJsonObject().add("costCoins", cost > 0 ? HearthStateJson.coins(cost) : JsonNull.INSTANCE);
				}
				for (JsonElement f : array(o, "find")) {
					JsonObject w = f.getAsJsonObject();
					w.addProperty("fromText", clock((int) getLong(w, "from", 0)));
					w.addProperty("toText", clock((int) getLong(w, "to", 0)));
				}
				if (followed != null && followed.active()) {
					o.addProperty("following", followed.resident().equals(getString(d, "id")));
				}
			} else {
				o.add("id", JsonNull.INSTANCE);
				o.add("portrait", JsonNull.INSTANCE);
				o.addProperty("portraitKey", "");
			}
			if (!o.has("following")) {
				o.addProperty("following", false);
			}
			rows.add(o);
		}
		JsonObject out = new JsonObject();
		out.add("rows", rows);
		out.addProperty("met", rows.size());
		out.addProperty("towns", towns.size());
		out.addProperty("moved", moved);
		return out;
	}

	static String clock(int minute) {
		int m = Math.floorMod(minute, 1440);
		return String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60);
	}

	/** {@code state.personTrack}: the named character followed (the People page's waypoint), or null. */
	public static JsonElement personTrack(LivesPayloads.@Nullable Followed f, @Nullable BlockPos from, float yaw) {
		if (f == null || !f.active()) {
			return JsonNull.INSTANCE;
		}
		JsonObject o = new JsonObject();
		o.addProperty("id", f.resident());
		o.addProperty("name", f.name());
		o.addProperty("town", f.town());
		o.addProperty("placed", f.placed());
		o.add("pos", HearthStateJson.pos(f.pos()));
		o.addProperty("distance", HearthStateJson.distance(from, f.pos()));
		if (from != null) {
			double dx = f.pos().getX() + 0.5 - from.getX();
			double dz = f.pos().getZ() + 0.5 - from.getZ();
			double bearing = Math.toDegrees(Math.atan2(-dx, dz)) - yaw;
			o.addProperty("bearing", (int) Math.round(HearthStateJson.wrap(bearing)));
		} else {
			o.add("bearing", JsonNull.INSTANCE);
		}
		return o;
	}

	// ---------------------------------------------------------------- the stranger (F1)

	/** {@code talk.stranger}: the stranger card the server sent, with the demand and the purse as coin; null for residents. */
	public static JsonElement stranger(@Nullable String json) {
		JsonObject card = parse(json);
		if (card == null) {
			return JsonNull.INSTANCE;
		}
		if (card.has("demand")) {
			card.add("demandCoins", HearthStateJson.coins(getLong(card, "demand", 0)));
		}
		if (card.has("carried")) {
			card.add("carriedCoins", HearthStateJson.coins(getLong(card, "carried", 0)));
		}
		String kind = getString(card, "kind");
		card.addProperty("kindName", Component.translatableWithFallback("hearth.roads.kind." + kind, HearthStateJson.pretty(kind)).getString());
		return card;
	}

	// ---------------------------------------------------------------- reputation (F5)

	/**
	 * Adds the retinue (a card per lord served) and each town's burgher status to the reputation page, from the household
	 * summary: {@code reputation.retinue[]} and {@code reputation.towns[].burgher} (null where not sworn).
	 */
	public static void placeOnReputation(JsonObject reputation, @Nullable JsonObject household) {
		JsonArray retinue = new JsonArray();
		JsonArray burgherOf = new JsonArray();
		if (household != null) {
			for (JsonElement e : array(household, "retinue")) {
				JsonObject r = e.getAsJsonObject().deepCopy();
				JsonArray rights = new JsonArray();
				for (JsonElement id : array(r, "rights")) {
					JsonObject j = new JsonObject();
					j.addProperty("id", id.getAsString());
					j.addProperty("text", Component.translatableWithFallback("hearth.retinue.right." + id.getAsString(), id.getAsString()).getString());
					rights.add(j);
				}
				r.add("rights", rights);
				String why = getString(r, "why");
				Object arg = why.equals("standing") ? getLong(r, "knightStanding", 70) : Math.max(0, getLong(r, "needed", 0) - getLong(r, "commissions", 0));
				r.addProperty("whyText", why.isEmpty() || why.equals("ok") ? "" : Component.translatableWithFallback("hearth.retinue.why." + why, why, arg)
					.getString());
				retinue.add(r);
			}
			burgherOf = array(household, "burgherOf");
		}
		reputation.add("retinue", retinue);
		for (JsonElement t : array(reputation, "towns")) {
			JsonObject town = t.getAsJsonObject();
			JsonObject sworn = null;
			for (JsonElement b : burgherOf) {
				JsonObject bo = b.getAsJsonObject();
				if (getLong(bo, "settlementId", Long.MIN_VALUE) == getLong(town, "id", Long.MAX_VALUE)
					|| !bo.has("settlementId") && getString(bo, "town").equals(getString(town, "name"))) {
					sworn = bo;
				}
			}
			town.add("burgher", sworn == null ? JsonNull.INSTANCE : burgher(sworn));
		}
	}

	static JsonObject burgher(JsonObject b) {
		JsonObject o = b.deepCopy();
		long today = getLong(o, "today", 0);
		long since = getLong(o, "since", today);
		o.addProperty("swornDays", Math.max(0, today - since));
		double dues = o.has("dues") ? o.get("dues").getAsDouble() : 0.05;
		JsonArray rights = new JsonArray();
		for (JsonElement id : array(o, "rights")) {
			JsonObject j = new JsonObject();
			j.addProperty("id", id.getAsString());
			j.addProperty("text", Component.translatableWithFallback("hearth.burgher.right." + id.getAsString(), id.getAsString(),
				Math.round(dues * 100)).getString());
			rights.add(j);
		}
		o.add("rights", rights);
		return o;
	}

	// ---------------------------------------------------------------- the HUD lines (F6, F7)

	/** {@code state.contest}: the contest line (null when folded away): the server's line plus {@code purseCoins}. */
	public static JsonElement contest(@Nullable JsonObject line) {
		if (line == null) {
			return JsonNull.INSTANCE;
		}
		JsonObject o = line.deepCopy();
		o.add("purseCoins", HearthStateJson.coins(getLong(o, "purse", 0)));
		return o;
	}

	/** {@code state.arrival}: today's events in the town the player is in, as the line under the banner shows them. */
	public static JsonElement arrival(long settlementId, String town, @Nullable String json, boolean showing) {
		if (json == null) {
			return JsonNull.INSTANCE;
		}
		JsonArray events;
		try {
			JsonElement e = JsonParser.parseString(json);
			events = e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
		} catch (RuntimeException ex) {
			events = new JsonArray();
		}
		JsonObject o = new JsonObject();
		o.addProperty("settlementId", settlementId);
		o.addProperty("town", town);
		o.addProperty("showing", showing);
		o.add("events", events);
		return o;
	}
}
