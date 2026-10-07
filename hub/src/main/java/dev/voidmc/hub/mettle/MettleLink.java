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
