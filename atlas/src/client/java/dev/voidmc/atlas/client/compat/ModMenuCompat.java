package dev.voidmc.atlas.client.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.voidmc.atlas.client.AtlasClient;
import dev.voidmc.atlas.client.ui.SettingsScreen;

/** Atlas's settings from Mod Menu's list, when Mod Menu is installed (only then is this class loaded). */
public final class ModMenuCompat implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> new SettingsScreen(AtlasClient.get(), parent);
	}
}
