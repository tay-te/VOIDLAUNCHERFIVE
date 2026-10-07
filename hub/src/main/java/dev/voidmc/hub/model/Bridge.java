package dev.voidmc.hub.model;

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

	public sealed interface Message permits Ready, Close, Filter, Sort, MoveAll, Segment, Turn, Page, Waypoint, Invalid {
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

	/** A page of the hub's pages island: "map", "inventory", "quests", "mods" or "friends". */
	public record Page(String value) implements Message {
	}

	/** The world strip's "Waypoint here" pill. */
	public record Waypoint() implements Message {
	}

	public record Invalid(String reason) implements Message {
	}

	static final Set<String> FILTERS = Set.of("all", "tools", "blocks", "food");
	static final Set<String> SORT_REGIONS = Set.of("container", "storage");
	static final Set<String> MOVE_TARGETS = Set.of("inventory", "container");
	static final Set<String> SEGMENTS = Set.of("inventory", "chest", "classes");
	static final Set<String> PAGES = Set.of("map", "inventory", "quests", "mods", "friends");

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
			case "page" -> oneOf(m, "value", PAGES) instanceof String v ? new Page(v) : new Invalid("page value");
			case "waypoint" -> new Waypoint();
			case "turn" -> m.get("deg") instanceof Double d && Double.isFinite(d) ? new Turn(d) : new Invalid("turn deg");
			default -> new Invalid("unknown type " + t);
		};
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
