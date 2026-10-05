package com.terraformersmc.modmenu.api;

/**
 * Compile-time stand-in for Mod Menu's API (Atlas does not depend on Mod Menu). Excluded from the jar: when Mod Menu
 * is installed its own interface is the one loaded; when it is not, nothing ever loads Atlas's "modmenu" entrypoint.
 */
public interface ModMenuApi {
	default ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> null;
	}
}
