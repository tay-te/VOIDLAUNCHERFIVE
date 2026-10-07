package dev.voidmc.hub.map;

import dev.voidmc.hub.VoidHub;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;

/**
 * VOID Atlas, optionally. Atlas is never required: without it the Map page shows a stand-in and
 * nothing here loads a single Atlas class. With it, the map widget is made the first time the Map
 * page draws (not when the hub opens, not at start-up) and then kept, so every later open shows the
 * same map at once, where the player left it; it is dropped when the player joins a world.
 *
 * <p>Client thread only.
 */
public final class HubMap {

	private static final boolean INSTALLED = FabricLoader.getInstance().isModLoaded("atlas");
	private static MapHost host;
	private static boolean failed;
	/** What making the widget cost the last time (ns), and how many were made (perf report). */
	public static volatile long lastCreateNs;
	public static volatile int created;

	private HubMap() {
	}

	/** Atlas is installed (and has not failed): the Map page hosts its map. */
	public static boolean available() {
		return INSTALLED && !failed;
	}

	/** The map, made on first call; null without Atlas or if Atlas could not make one. */
	public static MapHost host() {
		if (host == null && available()) {
			long t0 = System.nanoTime();
			try {
				// The only reference to the Atlas-facing class: resolved here, so never without Atlas.
				host = dev.voidmc.hub.map.atlas.AtlasMapHost.create();
			} catch (Throwable t) {
				failed = true;
				VoidHub.LOGGER.error("VOID Hub: VOID Atlas is installed but its map could not be hosted; the Map page shows the stand-in", t);
				return null;
			}
			lastCreateNs = System.nanoTime() - t0;
			created++;
			VoidHub.LOGGER.info("VOID Hub: the Map page hosts VOID Atlas {} (widget made in {} ms)", host.version(),
				String.format(Locale.ROOT, "%.2f", lastCreateNs / 1e6));
		}
		return host;
	}

	/** The map if it has been made, without making it. */
	public static MapHost existing() {
		return host;
	}

	/** A world was joined: the next Map page gets a fresh map. */
	public static void reset() {
		host = null;
		if (available()) {
			dev.voidmc.hub.map.atlas.AtlasMapHost.forgetShared();
		}
	}

	/**
	 * At start-up, with Atlas installed: the Map page and Atlas's own map screen (M) share one map
	 * state (see {@code AtlasMapHost}).
	 */
	public static void install() {
		if (available()) {
			try {
				dev.voidmc.hub.map.atlas.AtlasMapHost.installSync();
			} catch (Throwable t) {
				VoidHub.LOGGER.warn("VOID Hub: could not share the map state with VOID Atlas's map screen", t);
			}
		}
	}
}
