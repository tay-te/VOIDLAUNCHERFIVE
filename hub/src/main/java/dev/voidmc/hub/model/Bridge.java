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

	public sealed interface Message permits Ready, Close, Filter, Sort, MoveAll, Segment, Turn, Page, Waypoint, Tip, MettleCall, MettleAsk, MettleChoose, MettleUi, Fx,
		Items, HearthCall, HearthUi, HearthFx, Invalid {
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

	/** Where the page drew the item tooltip (CSS px; w 0 = hidden): Java leaves the stacks under it out. */
	public record Tip(double x, double y, double w, double h) implements Message {
	}

	/**
	 * A VOID Mettle action from the Classes page ({@link #METTLE_OPS}: the op and the shape its one string argument
	 * must have, mettle/docs/OVERLAY.md). Mettle's server validates every action; the bridge only checks shapes, so a
	 * broken page can never pass Mettle something it would not take from its own overlay.
	 */
	public record MettleCall(String op, String arg) implements Message {
	}

	/**
	 * A question to VOID Mettle's client rules from the Classes page ({@link #METTLE_ASKS}: build feel's previews,
	 * mettle/docs/OVERLAY.md "Build feel"): nothing is sent to the server; the render thread asks Mettle and posts the
	 * answer back as {@code {type: "mettleAnswer", req, op, arg, result}}. {@code req} is the page's request number.
	 */
	public record MettleAsk(String op, String arg, int req) implements Message {
	}

	/**
	 * The Classes page says what owns the keyboard: {@code modal} (a draft, reveal or moment is up: Esc closes it,
	 * not the window; 1-3 pick) and {@code text} (a search field takes typing).
	 */
	public record MettleUi(boolean modal, boolean text) implements Message {
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

	/** A page of the hub's pages island: "map", "inventory", "quests", "mods" or "friends". */
	public record Page(String value) implements Message {
	}

	/** The world strip's "Waypoint here" pill. */
	public record Waypoint() implements Message {
	}

	public record Invalid(String reason) implements Message {
	}

	/**
	 * The page's animated layers, drawn natively over the page by {@code CardFx} (the page itself stays still: every
	 * looping strip, glow and lift is Java's). The whole set each time it changes; an item keeps its start time across
	 * messages by its {@code id}. Positions and sizes in CSS px; {@code colour} ARGB-less RGB; {@code delay} ms.
	 */
	public record Fx(List<FxItem> items) implements Message {
	}

	/** One animated layer: kind (FX_KINDS), anchor box, tint, alpha, start delay, two kind-specific numbers, a clip. */
	public record FxItem(String id, String kind, double x, double y, double w, double h, int colour, double alpha, double delay, double p, double q,
		double[] clip) {
	}

	static final Set<String> FX_KINDS = Set.of("edge-common", "edge-rare", "edge-epic", "edge-cursed", "edge-wildcard", "edge-hot", "embers-sparse",
		"embers-dense", "flare", "bfront", "ash", "back", "my-edge", "crack", "burst", "foil", "set-rays", "set-sparks", "hover-common", "hover-rare",
		"hover-epic", "hover-cursed", "hover-wildcard", "mote-white", "mote-gold", "mote-cyan", "mote-pink", "rays", "ring", "bloom", "prism", "rect",
		"lift", "pulse");
	private static final java.util.regex.Pattern FX_ID = java.util.regex.Pattern.compile("[a-z0-9_-]{1,32}");
	private static final java.util.regex.Pattern RGB = java.util.regex.Pattern.compile("#[0-9A-Fa-f]{6}");

	private static Message fx(Map<String, Object> m) {
		return fx(m, FX_KINDS, 128);
	}

	private static Message fx(Map<String, Object> m, Set<String> kinds, int max) {
		if (!(m.get("items") instanceof List<?> list) || list.size() > max) {
			return new Invalid("fx items");
		}
		java.util.ArrayList<FxItem> out = new java.util.ArrayList<>();
		for (Object o : list) {
			if (!(o instanceof Map<?, ?> it) || !(it.get("i") instanceof String id) || !FX_ID.matcher(id).matches() || !(it.get("k") instanceof String k)
				|| !kinds.contains(k)) {
				return new Invalid("fx item");
			}
			double[] v = new double[8];
			String[] keys = {"x", "y", "w", "h", "a", "d", "p", "q"};
			double[] dflt = {0, 0, 0, 0, 1, 0, 0, 0};
			for (int i = 0; i < keys.length; i++) {
				Object n = it.get(keys[i]);
				if (n == null) {
					v[i] = dflt[i];
				} else if (n instanceof Double d && Double.isFinite(d) && Math.abs(d) <= 100_000) {
					v[i] = d;
				} else {
					return new Invalid("fx " + keys[i]);
				}
			}
			int colour = 0xFFFFFF;
			if (it.get("c") != null) {
				if (!(it.get("c") instanceof String c) || !RGB.matcher(c).matches()) {
					return new Invalid("fx colour");
				}
				colour = Integer.parseInt(c.substring(1), 16);
			}
			double[] clip = null;
			if (it.get("clip") != null) {
				if (!(it.get("clip") instanceof List<?> cl) || cl.size() != 4) {
					return new Invalid("fx clip");
				}
				clip = new double[4];
				for (int i = 0; i < 4; i++) {
					if (!(cl.get(i) instanceof Double d) || !Double.isFinite(d) || Math.abs(d) > 100_000) {
						return new Invalid("fx clip");
					}
					clip[i] = d;
				}
			}
			out.add(new FxItem(id, k, v[0], v[1], v[2], v[3], colour, Math.max(0, Math.min(1, v[4])), v[5], v[6], v[7], clip));
		}
		return new Fx(List.copyOf(out));
	}

	/**
	 * Item stacks the page shows in its own wells (the Classes page's trade): Minecraft draws them natively at these
	 * rects, as it draws the inventory's stacks at the layout's wells. The whole set each time it changes.
	 */
	public record Items(List<ItemIcon> items) implements Message {
	}

	/** One stack: an item id, its count, and the well it sits in (CSS px, page coordinates). */
	public record ItemIcon(String item, int count, double x, double y, double w, double h) {
	}

	private static final java.util.regex.Pattern ITEM_ID = java.util.regex.Pattern.compile("[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,128}");

	private static Message items(Map<String, Object> m) {
		if (!(m.get("items") instanceof List<?> list) || list.size() > 32) {
			return new Invalid("items");
		}
		java.util.ArrayList<ItemIcon> out = new java.util.ArrayList<>();
		for (Object o : list) {
			if (!(o instanceof Map<?, ?> it) || !(it.get("id") instanceof String id) || !ITEM_ID.matcher(id).matches()) {
				return new Invalid("items id");
			}
			double[] v = new double[5];
			String[] keys = {"n", "x", "y", "w", "h"};
			for (int i = 0; i < keys.length; i++) {
				if (!(it.get(keys[i]) instanceof Double d) || !Double.isFinite(d) || Math.abs(d) > 100_000) {
					return new Invalid("items " + keys[i]);
				}
				v[i] = d;
			}
			if (v[0] < 0 || v[0] > 99_999 || v[3] <= 0 || v[4] <= 0) {
				return new Invalid("items size");
			}
			out.add(new ItemIcon(id, (int) v[0], v[1], v[2], v[3], v[4]));
		}
		return new Items(List.copyOf(out));
	}


	// ---- VOID Hearth (hearth/docs/OVERLAY.md on branch hearth-ui) -------------------------------------------------------

	/**
	 * A VOID Hearth action from a Hearth page ({@link #HEARTH_OPS}: the {@code HearthActions} call and the shape its one
	 * string argument must have; two arguments are joined by {@code #}). Hearth's {@code ActionRules} and its server
	 * validate everything again; the bridge only checks shapes.
	 */
	public record HearthCall(String op, String arg) implements Message {
	}

	/** A Hearth page says what owns the keyboard: {@code modal} (a popover is up: Esc closes it) and {@code text}. */
	public record HearthUi(boolean modal, boolean text) implements Message {
	}

	/**
	 * A Hearth event screen's native parts (the minigames' moving parts, docs/HUB_UI.md): the rects the page reserved for
	 * them ({@link #HEARTH_FX_KINDS}) and item stacks Minecraft draws in the page's wells. The whole set each time.
	 */
	public record HearthFx(List<FxItem> items, List<ItemIcon> stacks) implements Message {
	}

	/** The rects a Hearth page reserves for native parts: dice, the lock and its strain, awareness, the hand ring, a lift. */
	static final Set<String> HEARTH_FX_KINDS = Set.of("dice", "lock", "strain", "awareness", "hand", "liftfill");

	static final String SCREEN_NAMES = "talk|market|bank|board|journal|confront|lockpick|pickpocket|dice|reputation";
	private static final String UUID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
	/** Conversation option and confrontation choice ids ("npc:news", "mettle:train/speech"). */
	private static final String OPTION = "[A-Za-z0-9_.:/+-]{1,128}";
	private static final String INT = "\\d{1,9}";
	private static final String BITS = "\\d{1,12}";

	/** Every Hearth action a page may ask for and the exact shape of its argument (empty for none). */
	static final Map<String, java.util.regex.Pattern> HEARTH_OPS = Map.ofEntries(
		op("choose", OPTION),
		op("train", "[a-z0-9_.-]+(?::[a-z0-9_./-]+)?"),
		op("close", SCREEN_NAMES),
		op("trade", INT + "#[01]"),
		op("haggle", INT + "#[01]"),
		op("haggleOffer", BITS),
		op("haggleAccept", ""),
		op("haggleCancel", ""),
		op("deposit", "-1|" + BITS),
		op("withdraw", "-1|" + BITS),
		op("sellEmeralds", "[1-9]\\d{0,4}"),
		op("buyEmeralds", "[1-9]\\d{0,4}"),
		op("buyPlot", INT),
		op("hire", INT),
		op("fund", INT + "#" + BITS),
		op("acceptContract", INT),
		op("deliver", INT),
		op("accept", UUID),
		op("handIn", UUID),
		op("refreshBoard", ""),
		op("track", UUID),
		op("trackPoster", UUID),
		op("untrackPoster", ""),
		op("abandon", UUID),
		op("confront", "(?:" + OPTION + ")?"),
		op("lift", "\\d{1,2}"),
		op("stopLifting", ""),
		op("slipAway", ""),
		op("stopPicking", ""),
		op("diceStake", "\\d{1,2}"),
		op("diceToggle", "[0-5]"),
		op("diceRoll", ""),
		op("diceBank", ""),
		op("dicePass", ""),
		op("diceLoaded", "[01]"),
		op("diceAgain", ""),
		op("requestReputation", ""),
		op("setConversationStyle", "card|cinematic"));

	private static Message hearth(Map<String, Object> m) {
		if (!(m.get("op") instanceof String op) || !HEARTH_OPS.containsKey(op)) {
			return new Invalid("hearth op");
		}
		Object a = m.get("arg");
		String arg = a == null ? "" : a instanceof String s ? s : a instanceof Double d && d == Math.floor(d) && Math.abs(d) < 1e12 ? String.valueOf(d.longValue()) : null;
		if (arg == null || arg.length() > 256 || !HEARTH_OPS.get(op).matcher(arg).matches()) {
			return new Invalid("hearth " + op + " arg");
		}
		return new HearthCall(op, arg);
	}

	private static Message hearthFx(Map<String, Object> m) {
		Map<String, Object> layers = new java.util.HashMap<>();
		layers.put("items", m.get("items") == null ? List.of() : m.get("items"));
		Message f = fx(layers, HEARTH_FX_KINDS, 32);
		if (!(f instanceof Fx fx)) {
			return f;
		}
		Map<String, Object> st = new java.util.HashMap<>();
		st.put("items", m.get("stacks") == null ? List.of() : m.get("stacks"));
		Message s = items(st);
		if (!(s instanceof Items it)) {
			return s;
		}
		return new HearthFx(fx.items(), it.items());
	}

	static final Set<String> FILTERS = Set.of("all", "tools", "blocks", "food");
	static final Set<String> SORT_REGIONS = Set.of("container", "storage");
	/** Ids and attribute names: namespaced lowercase paths, nothing longer than a real one. */
	static final java.util.regex.Pattern METTLE_ARG = java.util.regex.Pattern.compile("[a-z0-9_.-]+(:[a-z0-9_./-]+)?");
	private static final String ID = "[a-z0-9_.-]+(?::[a-z0-9_./-]+)?";
	/** Card ids (the ledger's numbers), one to twelve of them, comma-separated. */
	private static final String IDS = "\\d{1,18}(?:,\\d{1,18}){0,11}";
	/**
	 * Every Mettle action the page may ask for and the exact shape of its argument (empty for none). One string per
	 * call keeps the bridge thin; {@code MettleLink.act} splits it.
	 */
	static final Map<String, java.util.regex.Pattern> METTLE_OPS = Map.ofEntries(
		op("learnPerk", ID),
		op("spendAttribute", ID),
		op("pickCard", ID + "#[0-2]"),
		op("keepCard", ID + "#[0-2]#[01]"),
		op("claimMythic", ID),
		op("reroll", ID),
		op("slotCard", "\\d{1,18}(?:#-?\\d{1,2}(?:#" + ID + ")?)?"),
		op("unslotCards", IDS),
		op("rebuildCards", IDS),
		op("releaseCards", IDS),
		op("fuseCards", IDS),
		op("pinSets", "(?:" + ID + "(?:," + ID + "){0,2})?"),
		op("closeSitting", ""),
		op("tradeRequest", "[A-Za-z0-9_]{1,16}"),
		op("tradeAccept", ""),
		op("tradeOfferCard", "\\d{1,18}"),
		op("tradeTakeBackCard", "\\d{1,18}"),
		op("tradeOfferItem", "-1|\\d{1,2}"),
		op("tradeTakeBackItem", "\\d"),
		op("tradeCoin", "\\d{1,12}"),
		op("tradeLock", ""),
		op("tradeConfirm", ""),
		op("tradeCancel", ""),
		op("setCardSounds", "[01]"),
		op("setCardSoundsVolume", "100|[1-9]?\\d"),
		op("cardSound", "(?:deal|hover|unhover|mythic_reveal|slot|set_complete|stop|pick|burn)#[a-z0-9_:./#-]{0,64}"),
		// Build feel (Mettle 0.3.0, BUILD_FEEL.md): Focus, boons, Signatures, bynames, the forge, the guide.
		op("setFocus", "(?:" + ID + ")?"),
		op("temperCard", "\\d{1,18}#" + ID),
		op("inscribeCard", "\\d{1,18}#" + ID),
		op("boonReroll", ID),
		op("setSignature", "\\d{1,18}"),
		op("clearSignature", ID),
		op("takeByname", ""),
		op("wearByname", "\\d{1,3}"),
		op("forgeMythic", ID),
		op("forgeLegend", ""),
		op("guideSkip", ""),
		op("guideAdvance", "dismiss|deck|keyword|unhover|not_now|focus"));

	/** Build feel's questions (answered on the client, nothing sent): the card id, and for the two previews the trait or keyword. */
	static final Map<String, java.util.regex.Pattern> METTLE_ASKS = Map.ofEntries(
		op("boonOptions", "\\d{1,18}"),
		op("previewSlot", "\\d{1,18}"),
		op("previewTemper", "\\d{1,18}#" + ID),
		op("previewInscribe", "\\d{1,18}#" + ID));

	private static Map.Entry<String, java.util.regex.Pattern> op(String name, String argShape) {
		return Map.entry(name, java.util.regex.Pattern.compile(argShape));
	}

	private static Message mettle(Map<String, Object> m) {
		if (!(m.get("op") instanceof String op) || !METTLE_OPS.containsKey(op)) {
			return new Invalid("mettle op");
		}
		Object a = m.get("arg");
		String arg = a == null ? "" : a instanceof String s ? s : null;
		if (arg == null || arg.length() > 256 || !METTLE_OPS.get(op).matcher(arg).matches()) {
			return new Invalid("mettle " + op + " arg");
		}
		return new MettleCall(op, arg);
	}

	private static Message mettleAsk(Map<String, Object> m) {
		if (!(m.get("op") instanceof String op) || !METTLE_ASKS.containsKey(op)) {
			return new Invalid("mettleAsk op");
		}
		if (!(m.get("arg") instanceof String arg) || arg.length() > 256 || !METTLE_ASKS.get(op).matcher(arg).matches()) {
			return new Invalid("mettleAsk " + op + " arg");
		}
		if (!(m.get("req") instanceof Double d) || d != Math.floor(d) || d < 0 || d > 1_000_000_000) {
			return new Invalid("mettleAsk req");
		}
		return new MettleAsk(op, arg, d.intValue());
	}
	static final Set<String> MOVE_TARGETS = Set.of("inventory", "container");
	static final Set<String> SEGMENTS = Set.of("inventory", "chest", "gear", "classes");
	static final Set<String> PAGES = Set.of("map", "inventory", "quests", "mods", "friends");

	private static Message tip(Map<String, Object> m) {
		double[] v = new double[4];
		String[] k = {"x", "y", "w", "h"};
		for (int i = 0; i < 4; i++) {
			if (!(m.get(k[i]) instanceof Double d) || !Double.isFinite(d) || Math.abs(d) > 100_000) {
				return new Invalid("tip " + k[i]);
			}
			v[i] = d;
		}
		return v[2] < 0 || v[3] < 0 ? new Invalid("tip size") : new Tip(v[0], v[1], v[2], v[3]);
	}

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
			case "tip" -> tip(m);
			case "page" -> oneOf(m, "value", PAGES) instanceof String v ? new Page(v) : new Invalid("page value");
			case "waypoint" -> new Waypoint();
			case "turn" -> m.get("deg") instanceof Double d && Double.isFinite(d) ? new Turn(d) : new Invalid("turn deg");
			case "mettleChoose" -> choose(m);
			case "mettle" -> mettle(m);
			case "mettleAsk" -> mettleAsk(m);
			case "fx" -> fx(m);
			case "items" -> items(m);
			case "mettleUi" -> new MettleUi(Boolean.TRUE.equals(m.get("modal")), Boolean.TRUE.equals(m.get("text")));
			case "hearth" -> hearth(m);
			case "hearthUi" -> new HearthUi(Boolean.TRUE.equals(m.get("modal")), Boolean.TRUE.equals(m.get("text")));
			case "hearthFx" -> hearthFx(m);
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
