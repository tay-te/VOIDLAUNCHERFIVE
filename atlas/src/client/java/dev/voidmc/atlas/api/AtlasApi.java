package dev.voidmc.atlas.api;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * VOID Atlas's client API. Stable: methods are only ever added. Everything here is for the client thread.
 *
 * <p>Reach it with {@link #get()} once the game has started (Atlas initialises with the other client mods), or check
 * {@code FabricLoader.getInstance().isModLoaded("atlas")} first if Atlas is optional for you. To add markers, prefer
 * the {@code "atlas"} entrypoint ({@link AtlasPlugin}), which needs no load-order care.
 *
 * <ul>
 *   <li><b>Markers:</b> {@link #markers()} registers providers and layers; {@link #collect} returns every marker
 *   (Atlas's own and every provider's) as plain data.</li>
 *   <li><b>Data:</b> {@link #waypoints()}, {@link #discoveries()} and {@link #places()} for the current world.</li>
 *   <li><b>Drawing:</b> {@link #renderMap} draws a map view anywhere (a thumbnail, a card); {@link #createMapWidget}
 *   gives the whole interactive map page for a screen of your own (the VOID Hub).</li>
 * </ul>
 */
public interface AtlasApi {
	/** The API; throws if Atlas has not initialised (it is not installed, or this is called too early). */
	static AtlasApi get() {
		AtlasApi api = Holder.api;
		if (api == null) {
			throw new IllegalStateException("VOID Atlas is not initialised");
		}
		return api;
	}

	/** Whether Atlas is present and initialised. */
	static boolean available() {
		return Holder.api != null;
	}

	/** Atlas's version, like {@code 0.1.0}. */
	String version();

	AtlasMarkers markers();

	/** Every marker for a query: Atlas's own (waypoints, discoveries, players) and every provider's. */
	List<Marker> collect(MarkerQuery query);

	/** The current world's waypoints, every dimension. */
	List<Waypoint> waypoints();

	/** Adds a waypoint to the current world; returns it (null outside a world). */
	@Nullable Waypoint addWaypoint(String name, int x, int y, int z, String dimension);

	boolean removeWaypoint(String id);

	/** The structures discovered in the current world (on any server: what was worked out from the blocks counts too). */
	List<Discovery> discoveries();

	/**
	 * The current world's places (structures, landmarks, named regions, each sighted or discovered) and the hooks for
	 * other mods' places and names.
	 */
	AtlasPlaces places();

	/**
	 * A share code for waypoints ({@code atlas-wp:1:...}): copy-paste safe, versioned; what VOID friends (or a chat
	 * message) carry to another player's Atlas.
	 */
	String shareCode(List<Waypoint> waypoints);

	/** Offers a shared waypoint code to the player as a card (Accept / Ignore); nothing is added without Accept. */
	void offerShared(String code, String from);

	/** The maps kept for the server the player is on (worlds behind one address told apart), and notices about them. */
	AtlasWorlds worlds();

	/** A surface view centred on the player at the map's current zoom (or on 0, 0 outside a world). */
	MapView playerView();

	/**
	 * Draws a map view into a rectangle (GUI coordinates) with rounded corners: tiles, markers and the player, as the
	 * full map draws them. Cheap enough to call every frame.
	 */
	void renderMap(GuiGraphicsExtractor graphics, int x, int y, int width, int height, MapView view);

	/** The whole interactive map page, for a screen of your own. */
	MapWidget createMapWidget();

	/** Opens the full map screen. */
	void openMap();

	/** Opens Atlas's settings, returning to {@code parent} when done. */
	void openSettings(@Nullable Screen parent);

	/** Where the implementation registers itself. Not API. */
	final class Holder {
		static volatile AtlasApi api;

		private Holder() {
		}

		public static void set(AtlasApi impl) {
			api = impl;
		}
	}
}
