package dev.voidmc.hearth.client.overlay;

import dev.voidmc.hearth.Hearth;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * Where Hearth's event-opened screens open (docs/OVERLAY.md §Openers). A host that draws Hearth in its own UI (the VOID
 * Hub) registers an opener per screen; when the event comes (right-clicking a folk, a guard stopping the player, a
 * menu opening...), Hearth calls it, and opens its own Java screen only when no opener is registered or it returns false.
 * The state the host draws is {@link HearthState#build}'s. Hosts find this class reflectively, so neither mod needs the
 * other to build.
 *
 * <p>The Java screen for the event is made before the opener runs and stays reachable through {@link #nativeScreen}
 * while the host shows the event, so a host may embed its native parts (the minigames' moving pins, pick and dice:
 * docs/HUB_UI.md says those stay native) by forwarding its render and input calls. Client thread.
 */
public final class HearthScreens {
	public static final String TALK = "talk";
	public static final String MARKET = "market";
	public static final String BANK = "bank";
	public static final String BOARD = "board";
	public static final String JOURNAL = "journal";
	public static final String CONFRONT = "confront";
	public static final String LOCKPICK = "lockpick";
	public static final String PICKPOCKET = "pickpocket";
	public static final String DICE = "dice";
	public static final String REPUTATION = "reputation";
	/** The Companion gear screen (F3, 2026-10-21): {@code state.household.gearFor}. Host-only: Hearth has no Java screen for it. */
	public static final String GEAR = "gear";

	private static final Map<String, BooleanSupplier> OPENERS = new ConcurrentHashMap<>();
	private static @Nullable String hosted;
	private static @Nullable Screen nativeScreen;

	private HearthScreens() {
	}

	/** Talking to a folk (right-click): {@code state.talk}. */
	public static void setTalkOpener(@Nullable BooleanSupplier opener) {
		set(TALK, opener);
	}

	/** A market or shop counter ({@code state.market}); the Hearth menu stays open under the host's screen. */
	public static void setMarketOpener(@Nullable BooleanSupplier opener) {
		set(MARKET, opener);
	}

	/** A bank or town hall counter ({@code state.bank}); the Hearth menu stays open under the host's screen. */
	public static void setBankOpener(@Nullable BooleanSupplier opener) {
		set(BANK, opener);
	}

	/** A settlement's notice board ({@code state.board}). */
	public static void setNoticeBoardOpener(@Nullable BooleanSupplier opener) {
		set(BOARD, opener);
	}

	/** The quest journal (the J key, the journal item): {@code state.journal}. */
	public static void setJournalOpener(@Nullable BooleanSupplier opener) {
		set(JOURNAL, opener);
	}

	/** A guard or witness confronts the player ({@code state.confront}). Esc is not "close": answer or run. */
	public static void setConfrontOpener(@Nullable BooleanSupplier opener) {
		set(CONFRONT, opener);
	}

	/** Lockpicking ({@code state.lockpick}); the pick and pins are drawn by {@link #nativeScreen}. */
	public static void setLockpickOpener(@Nullable BooleanSupplier opener) {
		set(LOCKPICK, opener);
	}

	/** Pickpocketing ({@code state.pickpocket}). */
	public static void setPickpocketOpener(@Nullable BooleanSupplier opener) {
		set(PICKPOCKET, opener);
	}

	/** A dice table ({@code state.dice}); the Hearth menu stays open under the host's screen. */
	public static void setDiceOpener(@Nullable BooleanSupplier opener) {
		set(DICE, opener);
	}

	/** The reputation page (the K key, {@code /hearth standing}): {@code state.reputation}. */
	public static void setReputationOpener(@Nullable BooleanSupplier opener) {
		set(REPUTATION, opener);
	}

	/**
	 * The Companion gear screen (F3): "Let me see your gear" said to a companion (or {@code HearthActions.companionGear}).
	 * {@code state.household.gearFor} names the companion; {@code close("gear")} on close. Without an opener the
	 * conversation's answer lists their gear, as before.
	 */
	public static void setGearOpener(@Nullable BooleanSupplier opener) {
		set(GEAR, opener);
	}

	/** Any of the above by name ({@link #TALK}...); null removes it. Unknown names are ignored. */
	public static void setOpener(String screen, @Nullable BooleanSupplier opener) {
		set(screen, opener);
	}

	private static void set(String screen, @Nullable BooleanSupplier opener) {
		if (!known(screen)) {
			return;
		}
		if (opener == null) {
			OPENERS.remove(screen);
		} else {
			OPENERS.put(screen, opener);
		}
	}

	public static boolean known(String screen) {
		return switch (screen) {
			case TALK, MARKET, BANK, BOARD, JOURNAL, CONFRONT, LOCKPICK, PICKPOCKET, DICE, REPUTATION, GEAR -> true;
			default -> false;
		};
	}

	public static boolean hasOpener(String screen) {
		return OPENERS.containsKey(screen);
	}

	/** The event screen the host is showing now (it took the last open of it), or null. */
	public static @Nullable String hosted() {
		return hosted;
	}

	/**
	 * The Java screen made for the event the host is showing ({@link #hosted}), never set as Minecraft's screen: a host may
	 * size it ({@code init(w, h)}) and forward rendering and input to draw its native parts. Null when nothing is hosted.
	 */
	public static @Nullable Screen nativeScreen() {
		return nativeScreen;
	}

	/**
	 * Hearth opens an event screen: the host's if an opener takes it, else {@code fallback}'s, set as the screen. Returns
	 * whether the host took it.
	 */
	public static boolean show(String screen, Supplier<? extends Screen> fallback) {
		Screen own = fallback.get();
		if (offer(screen, own)) {
			return true;
		}
		Minecraft.getInstance().gui.setScreen(own);
		return false;
	}

	/**
	 * Offers an event screen to its host, with the Java screen made for it. Returns whether the host took it (then
	 * {@link #hosted} and {@link #nativeScreen} say so); false leaves showing {@code own} to the caller.
	 */
	public static boolean offer(String screen, Screen own) {
		BooleanSupplier opener = OPENERS.get(screen);
		if (opener == null) {
			return false;
		}
		String wasHosted = hosted;
		Screen wasNative = nativeScreen;
		hosted = screen;
		nativeScreen = own;
		boolean took;
		try {
			took = opener.getAsBoolean();
		} catch (RuntimeException e) {
			Hearth.LOG.warn("the {} opener failed; showing Hearth's own screen", screen, e);
			took = false;
		}
		if (!took) {
			hosted = wasHosted;
			nativeScreen = wasNative;
		}
		HearthViews.bump();
		return took;
	}

	/**
	 * Offers a host-only screen (one Hearth draws no Java screen for, such as {@link #GEAR}) to its opener. Returns whether
	 * a host took it; {@link #nativeScreen} stays null for it.
	 */
	public static boolean offerHosted(String screen) {
		BooleanSupplier opener = OPENERS.get(screen);
		if (opener == null) {
			return false;
		}
		String wasHosted = hosted;
		Screen wasNative = nativeScreen;
		hosted = screen;
		nativeScreen = null;
		boolean took;
		try {
			took = opener.getAsBoolean();
		} catch (RuntimeException e) {
			Hearth.LOG.warn("the {} opener failed", screen, e);
			took = false;
		}
		if (!took) {
			hosted = wasHosted;
			nativeScreen = wasNative;
		}
		HearthViews.bump();
		return took;
	}

	/** The host stopped showing {@code screen} (it closed): forget it. */
	public static void ended(String screen) {
		if (screen.equals(hosted)) {
			hosted = null;
			nativeScreen = null;
			HearthViews.bump();
		}
	}

	/** The screen of type {@code type} that shows the event now: Minecraft's own screen, else the host's embedded native one. */
	public static <T extends Screen> @Nullable T live(Class<T> type) {
		Screen current = Minecraft.getInstance().gui.screen();
		if (type.isInstance(current)) {
			return type.cast(current);
		}
		return type.isInstance(nativeScreen) ? type.cast(nativeScreen) : null;
	}

	/** Leaving a world. */
	public static void reset() {
		hosted = null;
		nativeScreen = null;
	}
}
