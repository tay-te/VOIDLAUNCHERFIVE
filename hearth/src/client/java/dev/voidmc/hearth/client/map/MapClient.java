package dev.voidmc.hearth.client.map;

import dev.voidmc.hearth.map.MapPayload;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Client side of the map module: the settlements the player knows of, as the server last sent them, for VOID Atlas's map
 * (the plugin in src/atlas reads them from here). Without Atlas nothing is registered, so the server never sends the list.
 * Names nothing of Atlas.
 */
public final class MapClient {
	private static volatile List<MapPayload.Entry> settlements = List.of();
	private static volatile long version;

	private MapClient() {
	}

	public static void init() {
		if (!FabricLoader.getInstance().isModLoaded("atlas")) {
			return;
		}
		ClientPlayNetworking.registerGlobalReceiver(MapPayload.TYPE, (payload, context) -> {
			settlements = payload.settlements();
			version++;
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			settlements = List.of();
			version++;
		});
	}

	/** The settlements the player knows of (sighted or discovered), in every dimension. */
	public static List<MapPayload.Entry> settlements() {
		return settlements;
	}

	/** Changes whenever {@link #settlements} does, for caches. */
	public static long version() {
		return version;
	}
}
