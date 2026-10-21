package dev.voidmc.hub.mettle;

import dev.voidmc.hub.VoidHub;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * VOID Mettle (classes, skills, weight and gear), when it is installed.
 *
 * <p>The hub never links against Mettle: it reaches Mettle's client overlay API
 * ({@code dev.voidmc.mettle.client.overlay}, mettle/docs/OVERLAY.md) through method handles looked up once, so the
 * hub builds and runs with or without it. Everything here is render thread only.
 *
 * <p>Gear slots follow mettle/docs/BURDEN.md §6: the player's {@code InventoryMenu} gains the three worn slots and the
 * pocket views after vanilla's 46, always present, switched off ({@code Slot#isActive}) when the worn item has fewer
 * pockets.
 */
public final class MettleLink {

	public static final int BACK = 46;
	public static final int BELT = 47;
	public static final int QUIVER = 48;
	public static final int PACK_FIRST = 49;
	public static final int PACK_SLOTS = 27;
	public static final int BELT_FIRST = 76;
	public static final int BELT_SLOTS = 6;
	public static final int QUIVER_FIRST = 82;
	public static final int QUIVER_SLOTS = 4;
	/** Slots an InventoryMenu has with Mettle's gear in it. */
	public static final int MENU_SIZE = 86;

	private static final String OVERLAY = "dev.voidmc.mettle.client.overlay.";
	private static boolean looked;
	private static MethodHandle build;
	private static MethodHandle learnPerk;
	private static MethodHandle spendAttribute;
	private static MethodHandle setOpener;
	private static MethodHandle setPickerOpener;
	private static MethodHandle chooseBackground;
	private static MethodHandle parseJson;
	/** Icon data URLs by id; read by the state worker (MettleSync) and the render thread. */
	private static final Map<String, Optional<String>> ICONS = new java.util.concurrent.ConcurrentHashMap<>();
	/** Texels (the source width) of each icon read, so the page can draw it on whole device pixels. */
	private static final Map<String, Integer> TEXELS = new java.util.concurrent.ConcurrentHashMap<>();
	/** Bumped by every resource reload: icons are read again and the page is sent them again. */
	private static volatile int iconEpoch;
	/** Tests: behave as if Mettle were missing or did not link (an older, incompatible jar). */
	public static volatile boolean forceAbsent;
	private static boolean stateFailed;

	private MettleLink() {
	}

	private static void look() {
		if (looked) {
			return;
		}
		looked = true;
		if (!FabricLoader.getInstance().isModLoaded("mettle")) {
			VoidHub.LOGGER.info("VOID Hub: VOID Mettle is not installed; the Gear and Classes pages stay off");
			return;
		}
		try {
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			Class<?> state = Class.forName(OVERLAY + "MettleState");
			Class<?> actions = Class.forName(OVERLAY + "MettleActions");
			Class<?> screens = Class.forName(OVERLAY + "MettleScreens");
			Class<?> json = Class.forName("com.google.gson.JsonObject");
			build = lookup.findStatic(state, "build", MethodType.methodType(json, Minecraft.class));
			learnPerk = lookup.findStatic(actions, "learnPerk", MethodType.methodType(void.class, String.class));
			spendAttribute = lookup.findStatic(actions, "spendAttribute", MethodType.methodType(void.class, String.class));
			setOpener = lookup.findStatic(screens, "setCharacterOpener", MethodType.methodType(void.class, BooleanSupplier.class));
			chooseBackground = lookup.findStatic(actions, "chooseBackground", MethodType.methodType(void.class, json));
			parseJson = lookup.findStatic(Class.forName("com.google.gson.JsonParser"), "parseString", MethodType.methodType(Class.forName("com.google.gson.JsonElement"), String.class));
			try {
				setPickerOpener = lookup.findStatic(screens, "setPickerOpener", MethodType.methodType(void.class, BooleanSupplier.class));
			} catch (NoSuchMethodException older) {
				setPickerOpener = null;  // an older Mettle: its own picker screen stays
			}
		} catch (ReflectiveOperationException | LinkageError e) {
			// An older or newer Mettle: one line saying what did not match, no stack trace on every open.
			VoidHub.LOGGER.warn("VOID Hub: VOID Mettle {} is installed but its overlay API did not match ({}: {}); the Gear and Classes pages stay off",
				FabricLoader.getInstance().getModContainer("mettle").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?"),
				e.getClass().getSimpleName(), e.getMessage());
			build = null;
			return;
		}
		VoidHub.LOGGER.info("VOID Hub: VOID Mettle linked; the Gear and Classes pages are on");
	}

	/** Mettle is installed and speaks the overlay API this hub was written against. */
	public static boolean present() {
		look();
		return build != null && !forceAbsent;
	}

	/** Whether this menu carries Mettle's gear slots (the player's inventory menu with Mettle loaded). */
	public static boolean hasGear(AbstractContainerMenu menu) {
		return present() && menu.slots.size() >= MENU_SIZE;
	}

	/** Mettle's state message (OVERLAY.md "the mettle state"), as JSON text; null without Mettle. */
	public static String state() {
		if (!present()) {
			return null;
		}
		Object tree = stateTree();
		return tree == null ? null : String.valueOf(tree);
	}

	/**
	 * Mettle's state as Mettle built it (a Gson tree), on the render thread: Mettle reads the client's game state. Its
	 * text ({@code toString}) may be written on another thread, the tree is not touched again. Null without Mettle; a
	 * Mettle whose state throws is logged once and switched off (the pages hide) instead of failing every open.
	 */
	public static Object stateTree() {
		if (!present()) {
			return null;
		}
		try {
			return build.invoke(Minecraft.getInstance());
		} catch (Throwable t) {
			if (!stateFailed) {
				stateFailed = true;
				VoidHub.LOGGER.error("VOID Hub: VOID Mettle's state failed; the Gear and Classes pages are switched off", t);
			}
			build = null;
			return null;
		}
	}

	public static void learnPerk(String id) {
		call(learnPerk, id);
	}

	public static void spendAttribute(String attribute) {
		call(spendAttribute, attribute);
	}

	/**
	 * Begins the character as a background: {@code request} is OVERLAY.md's chooseBackground JSON
	 * ({@code {background, spent, favoured}}), already validated by the bridge. The server answers in the state's
	 * {@code result} and phase.
	 */
	public static void chooseBackground(String request) {
		if (!present()) {
			return;
		}
		try {
			Object element = parseJson.invoke(request);
			Object object = element.getClass().getMethod("getAsJsonObject").invoke(element);
			chooseBackground.invoke(object);
		} catch (Throwable t) {
			VoidHub.LOGGER.error("VOID Hub: Mettle background choice failed", t);
		}
	}

	/** The first-join (or operator-reset) background picker opens on the hub's Classes page. */
	public static void registerPickerOpener(BooleanSupplier opener) {
		if (present() && setPickerOpener != null) {
			try {
				setPickerOpener.invoke(opener);
			} catch (Throwable t) {
				VoidHub.LOGGER.error("VOID Hub: could not register the picker opener with Mettle", t);
			}
		}
	}

	/** K opens the hub's Classes page instead of Mettle's fallback screen. */
	public static void registerOpener(BooleanSupplier opener) {
		if (present()) {
			try {
				setOpener.invoke(opener);
			} catch (Throwable t) {
				VoidHub.LOGGER.error("VOID Hub: could not register the Classes opener with Mettle", t);
			}
		}
	}

	// ---- cards v2 (mettle/docs/OVERLAY.md "Cards v2", SOUNDS.md §5) ------------------------------------------------

	/** MettleActions methods by name and parameter types, looked up on first use; a missing one is remembered. */
	private static final Map<String, Optional<MethodHandle>> ACTIONS = new HashMap<>();

	private static MethodHandle action(String name, Class<?>... params) {
		String key = name + params.length;
		Optional<MethodHandle> hit = ACTIONS.get(key);
		if (hit == null) {
			hit = Optional.empty();
			try {
				hit = Optional.of(MethodHandles.publicLookup().findStatic(Class.forName(OVERLAY + "MettleActions"), name,
					MethodType.methodType(void.class, params)));
			} catch (ReflectiveOperationException | LinkageError e) {
				VoidHub.LOGGER.warn("VOID Hub: this Mettle has no MettleActions.{}({} args); that action stays off", name, params.length);
			}
			ACTIONS.put(key, hit);
		}
		return hit.orElse(null);
	}

	/**
	 * One action from the Classes page: {@code op} is a {@code MettleActions} method, {@code arg} its arguments in one
	 * string, already shape-checked by {@code Bridge.METTLE_OPS} ({@code "mettle:mining#2"}, {@code "12,15"},
	 * {@code "hover#epic"}). Mettle's server decides; Mettle answers with a feedback chip in the state.
	 */
	public static void act(String op, String arg) {
		if (!present()) {
			return;
		}
		String[] p = arg.split("#", -1);
		try {
			switch (op) {
				case "learnPerk" -> learnPerk(arg);
				case "spendAttribute" -> spendAttribute(arg);
				case "pickCard" -> invoke(action("pickCard", String.class, int.class), p[0], Integer.parseInt(p[1]));
				case "keepCard" -> invoke(action("keepCard", String.class, int.class, boolean.class), p[0], Integer.parseInt(p[1]), "1".equals(p[2]));
				case "reroll" -> invoke(action("reroll", String.class), arg);
				case "claimMythic" -> invoke(action("claimMythic", String.class), arg);
				case "slotCard" -> {
					if (p.length == 1) {
						invoke(action("slotCard", String.class), p[0]);
					} else if (p.length == 2) {
						invoke(action("slotCard", String.class, int.class), p[0], Integer.parseInt(p[1]));
					} else {
						invoke(action("slotCard", String.class, int.class, String.class), p[0], Integer.parseInt(p[1]), p[2]);
					}
				}
				case "unslotCards", "rebuildCards", "releaseCards", "fuseCards", "pinSets", "tradeRequest", "tradeOfferCard", "tradeTakeBackCard" ->
					invoke(action(op, String.class), arg);
				case "closeSitting", "tradeAccept", "tradeLock", "tradeConfirm", "tradeCancel" -> invoke(action(op));
				case "tradeOfferItem", "tradeTakeBackItem" -> invoke(action(op, int.class), Integer.parseInt(arg));
				case "tradeCoin" -> invoke(action(op, long.class), Long.parseLong(arg));
				// The client's card sound option (SOUNDS.md §8): on or off, and 0-100 %.
				case "setCardSounds" -> invoke(action(op, boolean.class), "1".equals(arg));
				case "setCardSoundsVolume" -> invoke(action(op, int.class), Integer.parseInt(arg));
				case "cardSound" -> {
					int hash = arg.indexOf('#');
					invoke(action("cardSound", String.class, String.class), arg.substring(0, hash), arg.substring(hash + 1));
				}
				// Build feel (Mettle 0.3.0): an older Mettle has none of these, and each one stays off on its own.
				case "setFocus", "boonReroll", "setSignature", "clearSignature", "forgeMythic", "guideAdvance" -> invoke(action(op, String.class), arg);
				case "temperCard", "inscribeCard" -> invoke(action(op, String.class, String.class), p[0], p[1]);
				case "takeByname", "forgeLegend", "guideSkip" -> invoke(action(op));
				case "wearByname" -> invoke(action(op, int.class), Integer.parseInt(arg));
				case "keepTalent" -> invoke(action(op, int.class), Integer.parseInt(arg));
				case "rerollTalents" -> invoke(action(op));
				case "unslotTalent" -> invoke(action(op, String.class), arg);
				case "slotTalent" -> {
					if (p.length == 2) {
						invoke(action(op, String.class, int.class), p[0], Integer.parseInt(p[1]));
					} else {
						invoke(action(op, String.class), p[0]);
					}
				}
				case "wearTitle" -> {
					if (p.length == 2) {
						invoke(action(op, String.class, boolean.class), p[0], "1".equals(p[1]));
					} else {
						invoke(action(op, String.class), p[0]);
					}
				}
				case "momentShown" -> momentShown(p[0], p[1], p[2], Integer.parseInt(p[3]));
				default -> {
				}
			}
		} catch (RuntimeException e) {
			VoidHub.LOGGER.warn("VOID Hub: Mettle {}({}) could not be read", op, arg, e);
		}
	}

	/** MettleActions questions (String results) by name and arity, looked up on first use; a missing one is remembered. */
	private static final Map<String, Optional<MethodHandle>> QUERIES = new HashMap<>();

	private static MethodHandle query(String name, int args) {
		String key = name + args;
		Optional<MethodHandle> hit = QUERIES.get(key);
		if (hit == null) {
			hit = Optional.empty();
			try {
				Class<?>[] params = new Class<?>[args];
				java.util.Arrays.fill(params, String.class);
				hit = Optional.of(MethodHandles.publicLookup().findStatic(Class.forName(OVERLAY + "MettleActions"), name,
					MethodType.methodType(String.class, params)));
			} catch (ReflectiveOperationException | LinkageError e) {
				VoidHub.LOGGER.warn("VOID Hub: this Mettle has no MettleActions.{}({} args); that preview stays off", name, args);
			}
			QUERIES.put(key, hit);
		}
		return hit.orElse(null);
	}

	/**
	 * One of build feel's questions from the Classes page ({@code Bridge.METTLE_ASKS}, already shape-checked): Mettle works
	 * the answer out from the synced cards with its own rules and sends nothing (OVERLAY.md: boonOptions, previewTemper,
	 * previewInscribe, previewSlot). Render thread. The answer is Mettle's JSON object as text, or null (no Mettle, an
	 * older one without the call, or an answer that is not an object).
	 */
	public static String ask(String op, String arg) {
		if (!present()) {
			return null;
		}
		String[] p = arg.split("#", -1);
		MethodHandle h = switch (op) {
			case "boonOptions", "previewSlot" -> query(op, 1);
			case "previewTemper", "previewInscribe" -> p.length == 2 ? query(op, 2) : null;
			default -> null;
		};
		if (h == null) {
			return null;
		}
		try {
			Object out = p.length == 2 && !op.equals("boonOptions") && !op.equals("previewSlot") ? h.invoke(p[0], p[1]) : h.invoke(arg);
			String json = out == null ? null : out.toString().trim();
			return json != null && json.startsWith("{") && json.endsWith("}") ? json : null;
		} catch (Throwable t) {
			VoidHub.LOGGER.warn("VOID Hub: Mettle {}({}) failed: {}", op, arg, t.toString());
			return null;
		}
	}

	// ---- milestones shown once (BUILD_FEEL.md §2.4) -------------------------------------------------------------------

	private static boolean queueLooked;
	private static Object hudQueue;
	private static java.lang.reflect.Field queuePending;
	private static java.lang.reflect.Field queueCurrent;

	/** Mettle's HUD toast queue (MomentClient.QUEUE) and its fields, looked up once; false on a Mettle without it. */
	private static boolean lookQueue() {
		if (!queueLooked) {
			queueLooked = true;
			try {
				java.lang.reflect.Field q = Class.forName("dev.voidmc.mettle.client.moment.MomentClient").getDeclaredField("QUEUE");
				q.setAccessible(true);
				hudQueue = q.get(null);
				queuePending = hudQueue.getClass().getDeclaredField("pending");
				queuePending.setAccessible(true);
				queueCurrent = hudQueue.getClass().getDeclaredField("current");
				queueCurrent.setAccessible(true);
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				VoidHub.LOGGER.warn("VOID Hub: VOID Mettle's HUD toast queue could not be reached ({}); a milestone the page showed may toast again", e.toString());
				hudQueue = null;
			}
		}
		return hudQueue != null;
	}

	private static boolean matches(Object entry, String kind, String perk, String skill, int tier) throws ReflectiveOperationException {
		Object k = entry.getClass().getMethod("kind").invoke(entry);
		String name = (String) k.getClass().getMethod("serializedName").invoke(k);
		Object ep = entry.getClass().getMethod("perk").invoke(entry);
		Object es = entry.getClass().getMethod("skill").invoke(entry);
		int et = (int) entry.getClass().getMethod("tier").invoke(entry);
		return name.equals(kind) && et == tier && (perk.isEmpty() ? ep == null || ep.toString().isEmpty() : perk.equals(String.valueOf(ep)))
			&& (skill.isEmpty() || es == null || skill.equals(String.valueOf(es)));
	}

	/**
	 * The Classes page showed this milestone (its toast or banner): Mettle's HUD toast, which holds milestones while a
	 * screen is open and would show it after the hub closes, drops it, so each milestone shows once. Render thread (the
	 * client thread Mettle's queue lives on).
	 */
	static void momentShown(String kind, String perk, String skill, int tier) {
		if (!lookQueue()) {
			return;
		}
		try {
			java.util.Deque<?> pending = (java.util.Deque<?>) queuePending.get(hudQueue);
			pending.removeIf(e -> {
				try {
					return matches(e, kind, perk, skill, tier);
				} catch (ReflectiveOperationException ex) {
					return false;
				}
			});
			Object cur = queueCurrent.get(hudQueue);
			if (cur != null && matches(cur, kind, perk, skill, tier)) {
				queueCurrent.set(hudQueue, null);
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			VoidHub.LOGGER.warn("VOID Hub: could not drop a shown milestone from Mettle's HUD toasts: {}", e.toString());
		}
	}

	/** Tests: the milestones of a kind waiting in (or showing from) Mettle's HUD toast queue; -1 when unreadable. */
	public static int hudMilestones(String kind) {
		if (!present() || !lookQueue()) {
			return -1;
		}
		try {
			int n = 0;
			java.util.List<Object> all = new java.util.ArrayList<>((java.util.Deque<?>) queuePending.get(hudQueue));
			Object cur = queueCurrent.get(hudQueue);
			if (cur != null) {
				all.add(cur);
			}
			for (Object e : all) {
				Object k = e.getClass().getMethod("kind").invoke(e);
				if (kind.equals(k.getClass().getMethod("serializedName").invoke(k))) {
					n++;
				}
			}
			return n;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return -1;
		}
	}

	private static void invoke(MethodHandle handle, Object... args) {
		if (handle == null) {
			return;
		}
		try {
			handle.invokeWithArguments(args);
		} catch (Throwable t) {
			VoidHub.LOGGER.error("VOID Hub: Mettle call failed", t);
		}
	}

	// ---- change detection (MettleSync) ------------------------------------------------------------------------------

	/** What MettleState.build reads, looked up once: client stores, the card overlay's and the state's own statics. */
	private static boolean revLooked;
	private static MethodHandle revCharacter;
	private static MethodHandle revCards;
	private static MethodHandle revVersion;
	private static MethodHandle revCardSounds;
	private static MethodHandle revCardVolume;
	private static java.lang.reflect.Field[] revFields = new java.lang.reflect.Field[0];
	private static Object revCarry;

	private static void lookRevision() {
		if (revLooked) {
			return;
		}
		revLooked = true;
		MethodHandles.Lookup lookup = MethodHandles.publicLookup();
		try {
			Class<?> player = net.minecraft.world.entity.player.Player.class;
			revCharacter = lookup.findStatic(Class.forName("dev.voidmc.mettle.core.CharacterStore"), "get", MethodType.methodType(Class.forName("dev.voidmc.mettle.core.CharacterState"), player));
			revCards = lookup.findStatic(Class.forName("dev.voidmc.mettle.core.CardStore"), "get", MethodType.methodType(Class.forName("dev.voidmc.mettle.core.CardsState"), player));
			Class<?> options = Class.forName("dev.voidmc.mettle.client.MettleClientOptions");
			revCardSounds = lookup.findStatic(options, "cardSounds", MethodType.methodType(boolean.class));
			revCardVolume = lookup.findStatic(options, "cardSoundsVolume", MethodType.methodType(int.class));
			java.util.List<java.lang.reflect.Field> fields = new java.util.ArrayList<>();
			for (String[] f : new String[][] {{"MettleCards", "sitting"}, {"MettleCards", "trade"}, {"MettleCards", "hall"}, {"MettleCards", "reveal"},
				{"MettleState", "RECENT"}, {"MettleState", "MOMENTS"}, {"MettleState", "lastResult"}, {"MettleState", "pickerByOperator"}}) {
				java.lang.reflect.Field field = Class.forName(OVERLAY + f[0]).getDeclaredField(f[1]);
				field.setAccessible(true);
				fields.add(field);
			}
			revFields = fields.toArray(new java.lang.reflect.Field[0]);
			revCarry = Class.forName("dev.voidmc.mettle.core.MettleAttributes").getField("CARRY_CAPACITY").get(null);
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			VoidHub.LOGGER.warn("VOID Hub: could not read VOID Mettle's state inputs ({}); its state is rebuilt every half second instead", e.toString());
			revCharacter = null;
		}
		// A Mettle with its own change counter (MettleState.revision(): it changes whenever build's output would) is trusted
		// alone; an older one is read through the signature below.
		for (String name : new String[] {"revision", "version"}) {
			for (Class<?> type : new Class<?>[] {long.class, int.class}) {
				try {
					if (revVersion == null) {
						revVersion = lookup.findStatic(Class.forName(OVERLAY + "MettleState"), name, MethodType.methodType(type));
					}
				} catch (ReflectiveOperationException | LinkageError e) {
					// not this one
				}
			}
		}
	}

	/**
	 * A cheap signature of everything Mettle's state message is built from: it changes whenever the message could. The
	 * client's character and card records are immutable and replaced on every sync, so their identities say when they
	 * changed; with them the card overlay's sitting, trade, hall and reveal, the feedback chips and moments, the card
	 * sound option, the carry capacity and the gear the load is weighed from (the menu's stacks). Time enters as
	 * buckets: a game minute (cards attune by the minute) and, while a trade is open, the second (its countdown).
	 * Microseconds, render thread; {@code MettleSync} rebuilds the state when it changes.
	 */
	public static long signature(Minecraft mc) {
		if (!present() || mc.player == null || mc.level == null) {
			return 0;
		}
		lookRevision();
		long time = mc.level.getGameTime();
		if (revCharacter == null) {
			return time / 10;  // the inputs could not be read: as before, a rebuild every 10 ticks
		}
		long h = 17;
		try {
			var p = mc.player;
			h = h * 31 + System.identityHashCode(revCharacter.invoke(p));
			h = h * 31 + System.identityHashCode(revCards.invoke(p));
			h = h * 31 + ((boolean) revCardSounds.invoke() ? 1 : 0);
			h = h * 31 + (int) revCardVolume.invoke();
			boolean trading = false;
			for (int i = 0; i < revFields.length; i++) {
				Object v = revFields[i].get(null);
				if (v instanceof java.util.Deque<?> d) {
					h = h * 31 + d.size();
					h = h * 31 + System.identityHashCode(d.peekLast());
				} else {
					h = h * 31 + System.identityHashCode(v);
				}
				if (i == 1 && v != null) {
					trading = true;
				}
			}
			@SuppressWarnings("unchecked")
			var carry = (net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>) revCarry;
			h = h * 31 + Double.hashCode(p.getAttributes().hasAttribute(carry) ? p.getAttributeValue(carry) : 0);
			h = h * 31 + (p.isCreative() ? 1 : 0) + (p.isSpectator() ? 2 : 0);
			var slots = p.inventoryMenu.slots;
			for (int i = 0; i < slots.size(); i++) {
				net.minecraft.world.item.ItemStack st = slots.get(i).getItem();
				if (!st.isEmpty()) {
					h = h * 31 + i;
					h = h * 31 + net.minecraft.world.item.ItemStack.hashItemAndComponents(st);
					h = h * 31 + st.getCount();
				}
			}
			h = h * 31 + time / 1200;
			if (trading) {
				h = h * 31 + time / 20;
			}
		} catch (Throwable t) {
			VoidHub.LOGGER.warn("VOID Hub: reading VOID Mettle's state inputs failed ({}); rebuilding every half second instead", t.toString());
			revCharacter = null;
			return time / 10;
		}
		return h;
	}

	/**
	 * Mettle's own change counter ({@code MettleState.revision()}, Mettle 0.2.0, mettle/docs/OVERLAY.md): it goes up
	 * whenever {@code build}'s output may have changed, including the ages it shows (four times a second while a chip,
	 * moment or reveal shows, once a second while a card attunes). Long.MIN_VALUE without it (an older Mettle: the
	 * {@link #signature} alone decides).
	 */
	public static long revision(Minecraft mc) {
		if (!present() || mc.player == null) {
			return Long.MIN_VALUE;
		}
		lookRevision();
		if (revVersion != null) {
			try {
				return ((Number) revVersion.invoke()).longValue();
			} catch (Throwable t) {
				VoidHub.LOGGER.warn("VOID Hub: VOID Mettle's MettleState.revision() failed ({}); reading its inputs instead", t.toString());
				revVersion = null;
			}
		}
		return Long.MIN_VALUE;
	}

	private static java.lang.reflect.Field sittingField;
	private static boolean sittingLooked;
	private static boolean sittingWas;

	/**
	 * True once when a Card Shrine (or trainer) sitting opens: Mettle remembers the open sitting in
	 * {@code MettleCards.sitting} but opens no screen of its own, so the hub opens on the shrine. A field read, cheap
	 * enough for every tick; false forever on a Mettle without it.
	 */
	public static boolean sittingJustOpened() {
		if (!present()) {
			return false;
		}
		if (!sittingLooked) {
			sittingLooked = true;
			try {
				sittingField = Class.forName(OVERLAY + "MettleCards").getDeclaredField("sitting");
				sittingField.setAccessible(true);
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				sittingField = null;
			}
		}
		if (sittingField == null) {
			return false;
		}
		boolean open;
		try {
			open = sittingField.get(null) != null;
		} catch (IllegalAccessException e) {
			return false;
		}
		boolean opened = open && !sittingWas;
		sittingWas = open;
		return opened;
	}

	private static void call(MethodHandle handle, String arg) {
		if (!present()) {
			return;
		}
		try {
			handle.invoke(arg);
		} catch (Throwable t) {
			VoidHub.LOGGER.error("VOID Hub: Mettle call failed", t);
		}
	}

	/**
	 * A texture from the game's resources as a {@code data:} URL for the page (perk and skill icons, background
	 * art): the page has no access to other mods' assets. Cached; null when missing.
	 */
	public static String icon(String id) {
		Optional<String> hit = ICONS.get(id);
		if (hit != null) {
			return hit.orElse(null);
		}
		String url = null;
		Identifier key = Identifier.tryParse(id);
		if (key != null) {
			Optional<Resource> res;
			try {
				res = Minecraft.getInstance().getResourceManager().getResource(key);
			} catch (RuntimeException e) {
				return null;  // the resource manager is being swapped (a reload): try again next time, cache nothing
			}
			if (res.isPresent()) {
				try (InputStream in = res.get().open()) {
					byte[] png = in.readAllBytes();
					TEXELS.put(id, width(png));
					url = "data:image/png;base64," + Base64.getEncoder().encodeToString(upscale(png));
				} catch (Exception e) {
					VoidHub.LOGGER.warn("VOID Hub: could not read {}", id, e);
					return null;
				}
			}
		}
		ICONS.put(id, Optional.ofNullable(url));
		return url;
	}

	/** The source width in texels of an icon read by {@link #icon}, or 0. */
	public static int texels(String id) {
		return TEXELS.getOrDefault(id, 0);
	}

	/** Changes with every resource reload (icons are read again). */
	public static int iconEpoch() {
		return iconEpoch;
	}

	private static int width(byte[] png) {
		// PNG: the IHDR width, big-endian, at byte 16.
		return png.length > 24 ? (png[16] & 0xFF) << 24 | (png[17] & 0xFF) << 16 | (png[18] & 0xFF) << 8 | png[19] & 0xFF : 0;
	}

	/**
	 * Pixel art enlarged by whole multiples, nearest-neighbour: Ultralight smooths images it scales and has no
	 * {@code image-rendering: pixelated}, so the page gets a big crisp copy and only ever shrinks it a little.
	 */
	static byte[] upscale(byte[] png) throws java.io.IOException {
		try (com.mojang.blaze3d.platform.NativeImage src = com.mojang.blaze3d.platform.NativeImage.read(png)) {
			int w = src.getWidth();
			int h = src.getHeight();
			int k = Math.max(1, 128 / Math.max(w, h));
			if (k == 1) {
				return png;
			}
			try (com.mojang.blaze3d.platform.NativeImage big = new com.mojang.blaze3d.platform.NativeImage(w * k, h * k, false)) {
				for (int y = 0; y < h * k; y++) {
					for (int x = 0; x < w * k; x++) {
						big.setPixel(x, y, src.getPixel(x / k, y / k));
					}
				}
				java.nio.file.Path tmp = java.nio.file.Files.createTempFile("voidhub-icon", ".png");
				try {
					big.writeToFile(tmp);
					return java.nio.file.Files.readAllBytes(tmp);
				} finally {
					java.nio.file.Files.deleteIfExists(tmp);
				}
			}
		}
	}

	/** Resource reloads replace textures: icons are read again, and sent to the page again. */
	public static void clearIcons() {
		ICONS.clear();
		TEXELS.clear();
		iconEpoch++;
	}
}
