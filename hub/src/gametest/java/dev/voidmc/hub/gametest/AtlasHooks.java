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

	@SuppressWarnings("unchecked")
	static Class<? extends Screen> atlasScreenClass() {
		try {
			return (Class<? extends Screen>) Class.forName(ATLAS_SCREEN);
		} catch (ClassNotFoundException e) {
			throw new AssertionError("VOID Atlas's map screen is not " + ATLAS_SCREEN, e);
		}
	}
}
