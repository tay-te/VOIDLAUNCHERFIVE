package dev.voidmc.hub.hearth;

import dev.voidmc.hub.VoidHub;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;

/**
 * VOID Hearth (settlements, folk, coin, quests and the law), when it is installed.
 *
 * <p>The hub never links against Hearth: it reaches Hearth's client overlay API ({@code
 * dev.voidmc.hearth.client.overlay}, hearth/docs/OVERLAY.md on branch hearth-ui) through method handles looked up once,
 * behind {@code FabricLoader.isModLoaded("hearth")}. Every handle is optional: a Hearth without one of them keeps that
 * one thing off (an action does nothing, an opener stays Hearth's own screen) and the rest works. Render thread only.
 */
public final class HearthLink {

	private static final String OVERLAY = "dev.voidmc.hearth.client.overlay.";
	/** Every event screen Hearth can hand a host (OVERLAY.md §Openers). */
	public static final List<String> SCREENS = List.of("talk", "market", "bank", "board", "journal", "confront", "lockpick", "pickpocket", "dice",
		"reputation", "gear");

	private static boolean looked;
	private static MethodHandle build;
	private static MethodHandle revision;
	private static MethodHandle hosted;
	private static MethodHandle nativeScreen;
	private static MethodHandle setOpener;
	private static Class<?> actions;
	private static Class<?> screens;
	/** Tests: behave as if Hearth were missing. */
	public static volatile boolean forceAbsent;
	private static boolean stateFailed;

	private HearthLink() {
	}

	private static void look() {
		if (looked) {
			return;
		}
		looked = true;
		if (!FabricLoader.getInstance().isModLoaded("hearth")) {
			VoidHub.LOGGER.info("VOID Hub: VOID Hearth is not installed; the Quests page and Hearth's screens stay off");
			return;
		}
		MethodHandles.Lookup lookup = MethodHandles.publicLookup();
		try {
			Class<?> state = Class.forName(OVERLAY + "HearthState");
			actions = Class.forName(OVERLAY + "HearthActions");
			screens = Class.forName(OVERLAY + "HearthScreens");
			build = lookup.findStatic(state, "build", MethodType.methodType(Class.forName("com.google.gson.JsonObject")));
			revision = lookup.findStatic(state, "revision", MethodType.methodType(long.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			VoidHub.LOGGER.warn("VOID Hub: VOID Hearth {} is installed but its overlay API did not match ({}: {}); Hearth keeps its own screens",
				FabricLoader.getInstance().getModContainer("hearth").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?"),
				e.getClass().getSimpleName(), e.getMessage());
			build = null;
			return;
		}
		hosted = optional(lookup, screens, "hosted", MethodType.methodType(String.class));
		nativeScreen = optional(lookup, screens, "nativeScreen", MethodType.methodType(Screen.class));
		setOpener = optional(lookup, screens, "setOpener", MethodType.methodType(void.class, String.class, BooleanSupplier.class));
		VoidHub.LOGGER.info("VOID Hub: VOID Hearth linked; its screens open in the hub");
	}

	private static MethodHandle optional(MethodHandles.Lookup lookup, Class<?> owner, String name, MethodType type) {
		try {
			return lookup.findStatic(owner, name, type);
		} catch (ReflectiveOperationException | LinkageError e) {
			VoidHub.LOGGER.warn("VOID Hub: this Hearth has no {}.{}; that part stays off", owner.getSimpleName(), name);
			return null;
		}
	}

	/** Hearth is installed and speaks the overlay API this hub was written against. */
	public static boolean present() {
		look();
		return build != null && !forceAbsent;
	}

	/**
	 * Hearth's whole page state (OVERLAY.md §state) as Hearth built it, a Gson tree whose {@code toString} is the JSON
	 * text; null without Hearth. A Hearth whose state throws is logged once and switched off.
	 */
	public static Object stateTree() {
		if (!present()) {
			return null;
		}
		try {
			return build.invoke();
		} catch (Throwable t) {
			if (!stateFailed) {
				stateFailed = true;
				VoidHub.LOGGER.error("VOID Hub: VOID Hearth's state failed; its screens are switched off", t);
			}
			build = null;
			return null;
		}
	}

	/** Hearth's change counter ({@code HearthState.revision()}), 0 without Hearth. */
	public static long revision() {
		if (!present()) {
			return 0;
		}
		try {
			return (long) revision.invoke();
		} catch (Throwable t) {
			VoidHub.LOGGER.warn("VOID Hub: VOID Hearth's revision() failed ({}); its state is rebuilt every tick instead", t.toString());
			return System.nanoTime();
		}
	}

	/** The event screen the hub is showing for Hearth now, or null. */
	public static String hosted() {
		if (!present() || hosted == null) {
			return null;
		}
		try {
			return (String) hosted.invoke();
		} catch (Throwable t) {
			return null;
		}
	}

	/** The Java screen Hearth made for the hosted event (never set as Minecraft's screen), or null. */
	public static Screen nativeScreen() {
		if (!present() || nativeScreen == null) {
			return null;
		}
		try {
			return (Screen) nativeScreen.invoke();
		} catch (Throwable t) {
			return null;
		}
	}

	/** Registers the hub's opener for one of Hearth's screens ({@link #SCREENS}); false if this Hearth cannot take it. */
	public static boolean registerOpener(String screen, BooleanSupplier opener) {
		if (!present()) {
			return false;
		}
		try {
			if (setOpener != null) {
				setOpener.invoke(screen, opener);
				return true;
			}
			// An older Hearth with only the named setters.
			String name = "set" + Character.toUpperCase(screen.charAt(0)) + screen.substring(1) + "Opener";
			if ("board".equals(screen)) {
				name = "setNoticeBoardOpener";
			}
			MethodHandles.publicLookup().findStatic(screens, name, MethodType.methodType(void.class, BooleanSupplier.class)).invoke(opener);
			return true;
		} catch (Throwable t) {
			VoidHub.LOGGER.warn("VOID Hub: could not register the {} opener with Hearth ({}); Hearth's own screen stays", screen, t.toString());
			return false;
		}
	}

	// ---- actions ---------------------------------------------------------------------------------------------------

	/** HearthActions methods by name and parameter types, looked up on first use; a missing one is remembered. */
	private static final Map<String, Optional<MethodHandle>> ACTIONS = new HashMap<>();

	private static MethodHandle action(String name, Class<?>... params) {
		String key = name + params.length;
		Optional<MethodHandle> hit = ACTIONS.get(key);
		if (hit == null) {
			hit = Optional.empty();
			try {
				hit = Optional.of(MethodHandles.publicLookup().findStatic(actions, name, MethodType.methodType(boolean.class, params)));
			} catch (ReflectiveOperationException | LinkageError e) {
				VoidHub.LOGGER.warn("VOID Hub: this Hearth has no HearthActions.{}({} args); that action stays off", name, params.length);
			}
			ACTIONS.put(key, hit);
		}
		return hit.orElse(null);
	}

	/** Tests: the last action passed to Hearth ("op arg") and whether Hearth sent a packet for it. */
	public static volatile String lastAct = "";
	public static volatile boolean lastSent;

	/**
	 * One action from a Hearth page: {@code op} is a {@code HearthActions} method, {@code arg} its arguments in one
	 * string, already shape-checked by {@code Bridge.HEARTH_OPS} ({@code "12#1"}, a uuid, an option id). Hearth's
	 * ActionRules and its server decide; the answer arrives as state. Returns whether a packet went out.
	 */
	public static boolean act(String op, String arg) {
		if (!present()) {
			return false;
		}
		String[] p = arg.split("#", -1);
		Object r = null;
		try {
			r = switch (op) {
				case "choose", "train", "close", "accept", "handIn", "track", "trackPoster", "abandon", "confront", "setConversationStyle", "companionGear",
					"trackPerson" -> invoke(action(op, String.class), arg);
				case "companionOrder", "companionTake" -> invoke(action(op, String.class, String.class), p[0], p[1]);
				case "companionLend" -> invoke(action(op, String.class, int.class), p[0], Integer.parseInt(p[1]));
				case "trade", "haggle" -> invoke(action(op, int.class, boolean.class), Integer.parseInt(p[0]), "1".equals(p[1]));
				case "haggleOffer", "deposit", "withdraw" -> invoke(action(op, long.class), Long.parseLong(arg));
				case "sellEmeralds", "buyEmeralds", "buyPlot", "hire", "acceptContract", "deliver", "lift", "diceStake", "diceToggle" ->
					invoke(action(op, int.class), Integer.parseInt(arg));
				case "fund" -> invoke(action(op, int.class, long.class), Integer.parseInt(p[0]), Long.parseLong(p[1]));
				case "diceLoaded" -> invoke(action(op, boolean.class), "1".equals(arg));
				case "haggleAccept", "haggleCancel", "refreshBoard", "stopLifting", "slipAway", "stopPicking", "diceRoll", "diceBank", "dicePass",
					"diceAgain", "requestReputation", "untrackPoster", "refreshHousehold", "untrackPerson" -> invoke(action(op));
				default -> null;
			};
		} catch (RuntimeException e) {
			VoidHub.LOGGER.warn("VOID Hub: Hearth {}({}) could not be read", op, arg, e);
		}
		lastAct = op + " " + arg;
		lastSent = Boolean.TRUE.equals(r);
		return lastSent;
	}

	private static Object invoke(MethodHandle handle, Object... args) {
		if (handle == null) {
			return null;
		}
		try {
			return handle.invokeWithArguments(args);
		} catch (Throwable t) {
			VoidHub.LOGGER.error("VOID Hub: Hearth call failed", t);
			return null;
		}
	}

	/** The hub closed an event screen: Hearth tells its server what its own screen would have. */
	public static void close(String screen) {
		act("close", screen);
	}

	// ---- the lockpick's native screen (its model and step drive the pick; the hub draws it) -------------------------

	/** What the hub reads of Hearth's LockpickScreen; null when this Hearth's screen does not have it. */
	public record LockParts(Field model, Field picks, Field finished, Field flash, Field flashColor, Method step, Method pick, Method rotation,
		Method strain, Method bound, Method setPins, Method spec, Method pins) {
	}

	private static LockParts lockParts;
	private static boolean lockLooked;

	/** The lockpick screen's parts, looked up once (they are private: read with reflection, every one optional as a set). */
	public static LockParts lockParts(Screen screen) {
		if (lockLooked || screen == null) {
			return lockParts;
		}
		lockLooked = true;
		try {
			Class<?> c = screen.getClass();
			Field model = field(c, "model");
			Class<?> m = model.getType();
			Method spec = m.getMethod("spec");
			lockParts = new LockParts(model, field(c, "picks"), field(c, "finished"), field(c, "flash"), field(c, "flashColor"),
				method(c, "step", int.class, int.class), m.getMethod("pick"), m.getMethod("rotation"), m.getMethod("strain"), m.getMethod("bound"),
				m.getMethod("setPins"), spec, spec.getReturnType().getMethod("pins"));
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			VoidHub.LOGGER.warn("VOID Hub: Hearth's lockpick screen is not the one this hub knows ({}); lockpicking keeps Hearth's own screen", e.toString());
			lockParts = null;
		}
		return lockParts;
	}

	/** Whether the hub can play Hearth's lockpicking itself (else Hearth's own screen shows it). */
	public static boolean canHostLockpick() {
		return lockParts(nativeScreen()) != null;
	}

	/** A private field of a Hearth screen (or its superclasses), made readable. */
	public static Field field(Class<?> c, String name) throws NoSuchFieldException {
		for (Class<?> k = c; k != null; k = k.getSuperclass()) {
			try {
				Field f = k.getDeclaredField(name);
				f.setAccessible(true);
				return f;
			} catch (NoSuchFieldException e) {
				// up
			}
		}
		throw new NoSuchFieldException(name);
	}

	/** A private method of a Hearth screen, made callable. */
	public static Method method(Class<?> c, String name, Class<?>... params) throws NoSuchMethodException {
		for (Class<?> k = c; k != null; k = k.getSuperclass()) {
			try {
				Method m = k.getDeclaredMethod(name, params);
				m.setAccessible(true);
				return m;
			} catch (NoSuchMethodException e) {
				// up
			}
		}
		throw new NoSuchMethodException(name);
	}
}
