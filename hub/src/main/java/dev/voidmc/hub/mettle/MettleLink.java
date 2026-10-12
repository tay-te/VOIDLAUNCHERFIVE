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
	private static final Map<String, String> ICONS = new HashMap<>();

	private MettleLink() {
	}

	private static void look() {
		if (looked) {
			return;
		}
		looked = true;
		if (!FabricLoader.getInstance().isModLoaded("mettle")) {
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
			VoidHub.LOGGER.warn("VOID Hub: Mettle is installed but its overlay API did not match; the Classes and Gear pages stay off", e);
			build = null;
		}
	}

	/** Mettle is installed and speaks the overlay API this hub was written against. */
	public static boolean present() {
		look();
		return build != null;
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
		try {
			return String.valueOf(build.invoke(Minecraft.getInstance()));
		} catch (Throwable t) {
			VoidHub.LOGGER.error("VOID Hub: Mettle state failed", t);
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
				default -> {
				}
			}
		} catch (RuntimeException e) {
			VoidHub.LOGGER.warn("VOID Hub: Mettle {}({}) could not be read", op, arg, e);
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
		String hit = ICONS.get(id);
		if (hit != null || ICONS.containsKey(id)) {
			return hit;
		}
		String url = null;
		Identifier key = Identifier.tryParse(id);
		if (key != null) {
			Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(key);
			if (res.isPresent()) {
				try (InputStream in = res.get().open()) {
					url = "data:image/png;base64," + Base64.getEncoder().encodeToString(upscale(in.readAllBytes()));
				} catch (Exception e) {
					VoidHub.LOGGER.warn("VOID Hub: could not read {}", id, e);
				}
			}
		}
		ICONS.put(id, url);
		return url;
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

	/** Resource reloads replace textures. */
	public static void clearIcons() {
		ICONS.clear();
	}
}
