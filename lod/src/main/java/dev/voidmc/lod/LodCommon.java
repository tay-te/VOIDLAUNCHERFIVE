package dev.voidmc.lod;

import dev.voidmc.lod.net.LodNet;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * VOID LOD on both sides: registers its payloads, and on a server with Expanse answers the clients' requests for the
 * land past their view ({@code net.FactsService}). Everything that draws is the client entrypoint's ({@link VoidLod}).
 */
public final class LodCommon implements ModInitializer {
	@Override
	public void onInitialize() {
		LodNet.init();
		if (FabricLoader.getInstance().isModLoaded("expanse")) {
			dev.voidmc.lod.net.FactsService.init();
		}
	}
}
