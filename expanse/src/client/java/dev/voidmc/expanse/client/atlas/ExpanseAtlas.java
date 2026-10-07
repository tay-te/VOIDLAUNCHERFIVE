package dev.voidmc.expanse.client.atlas;

import dev.voidmc.atlas.api.AtlasMarkers;
import dev.voidmc.atlas.api.AtlasPlaces;
import dev.voidmc.atlas.api.AtlasPlugin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.core.registries.Registries;

/**
 * Expanse on the VOID Atlas map (the {@code "atlas"} entrypoint, called only when Atlas is installed): in an Expanse
 * world the map writes the lands' own names ({@link ExpanseNames}) in place of plain biome names. A world is an
 * Expanse world when its biomes include Expanse's (what the server sends, so this holds on servers too); elsewhere the
 * names are left to Atlas.
 */
public final class ExpanseAtlas implements AtlasPlugin {
	private static volatile boolean expanseWorld;

	@Override
	public void onAtlasReady(AtlasMarkers markers) {
	}

	@Override
	public void onAtlasPlaces(AtlasPlaces places) {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> expanseWorld = handler.registryAccess().lookupOrThrow(Registries.BIOME)
			.keySet().stream().anyMatch(id -> id.getNamespace().equals("expanse")));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> expanseWorld = false);
		places.registerNameProvider("expanse:names", 0,
			q -> expanseWorld ? ExpanseNames.name(q.kind(), q.biome(), q.x(), q.z(), q.area(), q.seedHash()) : null);
	}
}
