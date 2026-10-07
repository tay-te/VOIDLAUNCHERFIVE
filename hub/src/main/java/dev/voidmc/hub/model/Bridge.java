package dev.voidmc.hub.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The JS bridge, both directions.
 *
 * <p>Java to page: {@link #call} wraps a message (layout, state, detail) in the one function the
 * page exposes, {@code hub.apply(json)}, for {@code View.evaluateScript}.
 *
 * <p>Page to Java: the page calls {@code window.__void_native(JSON.stringify(msg))}; the binding
 * hands the string to the view's message handler, {@link #decode} turns it into a typed message,
 * and the handler's return value ({@link #reply}) is what the page's call returns. Anything that
 * does not decode is {@link Invalid}, never an exception: a broken page must not take the render
 * thread down.
 */
public final class Bridge {

	private Bridge() {
	}

	public sealed interface Message permits Ready, Close, Filter, Sort, MoveAll, Segment, Turn, MettleCall, MettleChoose, Invalid {
	}

	/** The page finished loading and installed {@code hub.apply}. */
	public record Ready() implements Message {
	}

	/** The close island was clicked. */
	public record Close() implements Message {
	}

	/** A storage filter chip: all / tools / blocks / food. */
	public record Filter(String value) implements Message {
	}

	/** Sort a region: "container" (the chest) or "storage" (the player's 27). */
	public record Sort(String region) implements Message {
	}

	/** Move everything: to "inventory" (from the chest) or to "container" (from the player). */
	public record MoveAll(String to) implements Message {
	}

	/** A window segment: "inventory", "chest" or "classes". */
	public record Segment(String value) implements Message {
	}

	/** The player model was dragged: yaw change in degrees. */
	public record Turn(double degrees) implements Message {
	}

	/**
	 * A VOID Mettle action from the Classes page: {@code learnPerk} (arg: perk id) or {@code spendAttribute} (arg:
	 * attribute name). The server validates both; the page only asks.
	 */
	public record MettleCall(String op, String arg) implements Message {
	}

	/**
	 * The Classes page's background picker: begin as {@code background}, placing {@code spent} free attribute points
	 * and favouring {@code favoured} skills (Wanderer). Shapes only are checked here; Mettle's server applies its rules.
	 */
	public record MettleChoose(String background, Map<String, Integer> spent, List<String> favoured) implements Message {
		/** OVERLAY.md's chooseBackground request. */
		public String json() {
			Json.Writer sp = Json.object();
			this.spent.forEach(sp::put);
			StringBuilder fav = new StringBuilder("[");
			for (int i = 0; i < this.favoured.size(); i++) {
				fav.append(i > 0 ? "," : "").append(Json.quote(this.favoured.get(i)));
			}
			return Json.object().put("background", this.background).raw("spent", sp.end()).raw("favoured", fav.append(']').toString()).end();
		}
	}

	public record Invalid(String reason) implements Message {
	}

	static final Set<String> FILTERS = Set.of("all", "tools", "blocks", "food");
	static final Set<String> SORT_REGIONS = Set.of("container", "storage");
	static final Set<String> METTLE_OPS = Set.of("learnPerk", "spendAttribute");
	/** Ids and attribute names: namespaced lowercase paths, nothing longer than a real one. */
	static final java.util.regex.Pattern METTLE_ARG = java.util.regex.Pattern.compile("[a-z0-9_.-]+(:[a-z0-9_./-]+)?");
	static final Set<String> MOVE_TARGETS = Set.of("inventory", "container");
	static final Set<String> SEGMENTS = Set.of("inventory", "chest", "gear", "classes");

	public static Message decode(String json) {
		Map<String, Object> m;
		try {
			m = Json.parseObject(json);
		} catch (RuntimeException e) {
			return new Invalid("malformed: " + e.getMessage());
		}
		Object type = m.get("type");
		if (!(type instanceof String t)) {
			return new Invalid("no type");
		}
		return switch (t) {
			case "ready" -> new Ready();
			case "close" -> new Close();
			case "filter" -> oneOf(m, "value", FILTERS) instanceof String v ? new Filter(v) : new Invalid("filter value");
			case "sort" -> oneOf(m, "region", SORT_REGIONS) instanceof String v ? new Sort(v) : new Invalid("sort region");
			case "moveAll" -> oneOf(m, "to", MOVE_TARGETS) instanceof String v ? new MoveAll(v) : new Invalid("moveAll target");
			case "segment" -> oneOf(m, "value", SEGMENTS) instanceof String v ? new Segment(v) : new Invalid("segment value");
			case "turn" -> m.get("deg") instanceof Double d && Double.isFinite(d) ? new Turn(d) : new Invalid("turn deg");
			case "mettleChoose" -> choose(m);
			case "mettle" -> oneOf(m, "op", METTLE_OPS) instanceof String op && m.get("arg") instanceof String a && a.length() <= 96
				&& METTLE_ARG.matcher(a).matches() ? new MettleCall(op, a) : new Invalid("mettle call");
			default -> new Invalid("unknown type " + t);
		};
	}

	static final Set<String> ATTRIBUTES = Set.of("strength", "agility", "vitality", "wits", "presence");

	private static Message choose(Map<String, Object> m) {
		if (!(m.get("background") instanceof String bg) || bg.length() > 96 || !METTLE_ARG.matcher(bg).matches()) {
			return new Invalid("mettleChoose background");
		}
		java.util.LinkedHashMap<String, Integer> spent = new java.util.LinkedHashMap<>();
		if (m.get("spent") instanceof Map<?, ?> sp) {
			for (Map.Entry<?, ?> e : sp.entrySet()) {
				if (!(e.getKey() instanceof String a) || !ATTRIBUTES.contains(a) || !(e.getValue() instanceof Double d) || d < 0 || d > 20 || d != Math.floor(d)) {
					return new Invalid("mettleChoose spent");
				}
				spent.put(a, d.intValue());
			}
		}
		java.util.ArrayList<String> favoured = new java.util.ArrayList<>();
		if (m.get("favoured") instanceof List<?> fl) {
			if (fl.size() > 8) {
				return new Invalid("mettleChoose favoured");
			}
			for (Object o : fl) {
				if (!(o instanceof String s) || s.length() > 96 || !METTLE_ARG.matcher(s).matches()) {
					return new Invalid("mettleChoose favoured");
				}
				favoured.add(s);
			}
		}
		return new MettleChoose(bg, spent, favoured);
	}

	private static Object oneOf(Map<String, Object> m, String key, Set<String> allowed) {
		Object v = m.get(key);
		return v instanceof String s && allowed.contains(s) ? s : null;
	}

	/** What {@code __void_native} returns to the page. */
	public static String reply(Message handled) {
		if (handled instanceof Invalid invalid) {
			return Json.object().put("ok", false).put("error", invalid.reason()).end();
		}
		return Json.object().put("ok", true).end();
	}

	/** The script that delivers one message to the page. */
	public static String call(String messageJson) {
		return "hub.apply(" + messageJson + ")";
	}
}
