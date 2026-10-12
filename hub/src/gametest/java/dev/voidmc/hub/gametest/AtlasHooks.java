package dev.voidmc.hub.gametest;

import dev.voidmc.atlas.api.AtlasApi;
import net.minecraft.client.gui.screens.Screen;

/**
 * The test's direct calls into VOID Atlas (its own full map screen, for the perf comparison). Only
 * loaded when Atlas is installed: every caller checks {@code HubMap.available()} first.
 */
final class AtlasHooks {

	static final String ATLAS_SCREEN = "dev.voidmc.atlas.client.ui.AtlasScreen";

	private AtlasHooks() {
	}

	/** Atlas's own map screen (M), on the client thread. */
	static void openAtlasMap() {
		AtlasApi.get().openMap();
	}

	/**
	 * Atlas's "live backdrop" setting. Off (Atlas's default), its map screen draws the world once,
	 * keeps a still of it and then skips the world entirely; on, it draws the live world behind the
	 * map every frame, as the hub does behind every page. Reflective: not API.
	 */
	static boolean setLiveBackdrop(boolean live) {
		try {
			Class<?> client = Class.forName("dev.voidmc.atlas.client.AtlasClient");
			Object atlas = client.getMethod("get").invoke(null);
			Object settings = client.getMethod("settings").invoke(atlas);
			var f = settings.getClass().getField("liveBackdrop");
			boolean was = f.getBoolean(settings);
			f.setBoolean(settings, live);
			return was;
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("could not set Atlas's live backdrop", e);
		}
	}

	@SuppressWarnings("unchecked")
	static Class<? extends Screen> atlasScreenClass() {
		try {
			return (Class<? extends Screen>) Class.forName(ATLAS_SCREEN);
		} catch (ClassNotFoundException e) {
			throw new AssertionError("VOID Atlas's map screen is not " + ATLAS_SCREEN, e);
		}
	}
}
