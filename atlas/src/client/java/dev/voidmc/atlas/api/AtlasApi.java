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
	 * Tracks a marker by its id (a Hearth quest step, a settlement): the compass, the minimap's rim, one beam and the
	 * map's hairline show it, until the player tracks something else or its mod stops showing it. False if no marker
	 * of that id is on the map now.
	 */
	boolean track(String markerId);

	/** The player's journal of the world whose map is open (read only): firsts, distance, depth, explored areas. */
	AtlasJournal journal();

	/**
	 * Hands Atlas the way to reach the player's friends and party (the VOID Hub's or launcher's bridge), if no plugin
	 * gave one. Friend features show while it is {@link AtlasSocialTransport#available() available}.
	 */
	void useSocialTransport(AtlasSocialTransport transport);

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

	/** A waypoint's marker id (to select it on a map widget, or track it). */
	default String markerId(Waypoint waypoint) {
		return "atlas:wp:" + waypoint.id();
	}

	/** Whether a key press is the player's map key (M unless changed): a host can close on it as Atlas's map does. */
	default boolean isMapKey(net.minecraft.client.input.KeyEvent event) {
		return false;
	}

	/** Room your HUD keeps on the screen, which Atlas's HUD then keeps out of (see {@link AtlasHud}). */
	default AtlasHud hud() {
		return new AtlasHud() {
		};
	}
}
