package dev.voidmc.atlas.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmc.atlas.api.AtlasApi;
import dev.voidmc.atlas.api.AtlasPlugin;
import dev.voidmc.atlas.api.DiscoveryState;
import dev.voidmc.atlas.api.MapLayer;
import dev.voidmc.atlas.api.Marker;
import dev.voidmc.atlas.api.MarkerGlyph;
import dev.voidmc.atlas.api.MarkerQuery;
import dev.voidmc.atlas.api.Place;
import dev.voidmc.atlas.api.Source;
import dev.voidmc.atlas.api.Tier;
import dev.voidmc.atlas.api.Waypoint;
import dev.voidmc.atlas.client.data.DimensionData;
import dev.voidmc.atlas.client.data.Ingest;
import dev.voidmc.atlas.client.data.LayerData;
import dev.voidmc.atlas.client.data.MapSession;
import dev.voidmc.atlas.client.mixin.BiomeManagerAccessor;
import dev.voidmc.atlas.client.render.TileCache;
import dev.voidmc.atlas.client.ui.AtlasScreen;
import dev.voidmc.atlas.client.ui.MapPage;
import dev.voidmc.atlas.client.ui.MarkerPainter;
import dev.voidmc.atlas.client.ui.MinimapHud;
import dev.voidmc.atlas.client.ui.SettingsScreen;
import dev.voidmc.atlas.core.Coords;
import dev.voidmc.atlas.core.MarkerRegistry;
import dev.voidmc.atlas.core.RegionRaw;
import dev.voidmc.atlas.core.StructureNames;
import dev.voidmc.atlas.core.names.NameGen;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * VOID Atlas, on the client: a map of everything the player has seen, kept per world and dimension, drawn in a muted
 * hand-toned palette with stepped relief; a minimap; waypoints; the places it finds (structures from their blocks on
 * any server and from the game's data in singleplayer, peaks, lakes, rivers, waterfalls, cave mouths, ravines, named
 * regions), each unknown, sighted or discovered; an underground map of only what the player has been near; and an API
 * ({@link AtlasApi}) for other mods' markers, places and names, and for embedding the map (the VOID Hub).
 *
 * <p>Keys: M opens the map, N toggles the minimap, B drops a waypoint, = and - zoom the minimap; the settings key is
 * unbound (the map has a settings button).
 */
public final class AtlasClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("VOID Atlas");
	public static final String ID = "atlas";
	private static AtlasClient instance;

	private AtlasSettings settings;
	private final MarkerRegistry markers = new MarkerRegistry(LOGGER::warn);
	private @Nullable TileCache tiles;
	private @Nullable MapSession session;
	private @Nullable Ingest ingest;
	private @Nullable ClientLevel level;
	private @Nullable Object modelSet;
	private KeyMapping openMap;
	private KeyMapping toggleMinimap;
	private KeyMapping dropWaypoint;
	private KeyMapping zoomIn;
	private KeyMapping zoomOut;
	private KeyMapping openSettings;
	private int ticks;
	private boolean deathRecorded;
	private final MinimapHud minimap = new MinimapHud(this);
	private final dev.voidmc.atlas.client.ui.HudOverlays overlays = new dev.voidmc.atlas.client.ui.HudOverlays(this);
	/** The last band the player was in underground, per dimension (for the underground and Nether views). */
	private final ConcurrentHashMap<String, Integer> bands = new ConcurrentHashMap<>();
	private final boolean logStats = Boolean.getBoolean("atlas.logStats");
	private AtlasApiImpl api;
	private AtlasPlacesImpl places;
	private boolean underground;
	/** The place markers last made, and the snapshot they were made from (remade only when it changes). */
	private List<Place> markersFrom = List.of();
	private List<Marker> placeMarkers = List.of();
	private java.util.Map<String, Place> markerPlaces = java.util.Map.of();
	private String markersDim = "";
	private int markersLegend = -1;

	/**
	 * The performance gate's vanilla baseline ({@code -Datlas.baseline=true}): Atlas loads but does nothing — no
	 * session, no chunk reading, no HUD — so a run measures the game alone.
	 */
	public static final boolean BASELINE = Boolean.getBoolean("atlas.baseline");

	public static AtlasClient get() {
		return instance;
	}

	@Override
	public void onInitializeClient() {
		instance = this;
		this.settings = AtlasSettings.load(FabricLoader.getInstance().getConfigDir().resolve("atlas.json"));
		this.markers.registerLayer(new MapLayer(MapLayer.TERRAIN, "Terrain", true, 0));
		this.markers.registerLayer(new MapLayer(MapLayer.NAMES, "Names", true, 5));
		this.markers.registerLayer(new MapLayer(MapLayer.LANDMARKS, "Landmarks", true, 10));
		this.markers.registerLayer(new MapLayer(MapLayer.PLACES, "Places", true, 12));
		this.markers.registerLayer(new MapLayer(MapLayer.WAYPOINTS, "Waypoints", true, 20));
		this.markers.registerLayer(new MapLayer(MapLayer.PLAYERS, "Friends", true, 30));
		this.markers.registerLayer(new MapLayer(MapLayer.FLUIDS, "Water & lava", true, 31));
		this.markers.registerProvider("atlas:places", -30, this::placeMarkers);
		this.markers.registerProvider("atlas:waypoints", 20, this::waypointMarkers);
		this.markers.registerProvider("atlas:players", 30, this::playerMarkers);
		this.places = new AtlasPlacesImpl(this);
		this.api = new AtlasApiImpl(this);

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "atlas"));
		this.openMap = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.atlas.map", InputConstants.KEY_M, category));
		this.toggleMinimap = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.atlas.minimap", InputConstants.KEY_N, category));
		this.dropWaypoint = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.atlas.waypoint", InputConstants.KEY_B, category));
		this.zoomIn = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.atlas.zoom_in", InputConstants.KEY_EQUALS, category));
		this.zoomOut = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.atlas.zoom_out", InputConstants.KEY_MINUS, category));
		this.openSettings = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.atlas.settings", InputConstants.UNKNOWN.getValue(), category));

		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
			if (this.ingest != null && level == this.level) {
				this.ingest.loaded(chunk);
			}
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> this.closeSession());
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ID, "minimap"), (g, d) -> {
			if (!BASELINE) {
				this.minimap.extract(g, d);
			}
		});
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ID, "discoveries"), (g, d) -> {
			if (!BASELINE) {
				this.overlays.extract(g, d);
			}
		});

		for (var container : FabricLoader.getInstance().getEntrypointContainers("atlas", AtlasPlugin.class)) {
			String mod = container.getProvider().getMetadata().getId();
			try {
				AtlasPlugin plugin = container.getEntrypoint();
				plugin.onAtlasReady(this.markers);
				plugin.onAtlasPlaces(this.places);
				LOGGER.info("VOID Atlas: {} added its markers and places", mod);
			} catch (Throwable t) {
				LOGGER.warn("VOID Atlas: {}'s atlas entrypoint failed: {}", mod, t.toString());
			}
		}
		LOGGER.info("VOID Atlas ready: M opens the map, N toggles the minimap, B drops a waypoint ({} worker threads)", this.settings.workerThreads);
	}

	// --- accessors -------------------------------------------------------------------------------------------------

	public AtlasSettings settings() {
		return this.settings;
	}

	public MarkerRegistry markersRegistry() {
		return this.markers;
	}

	public AtlasPlacesImpl places() {
		return this.places;
	}

	public @Nullable MapSession session() {
		return this.session;
	}

	public AtlasApiImpl api() {
		return this.api;
	}

	public KeyMapping mapKey() {
		return this.openMap;
	}

	/** The GPU tile cache, made on first use (render thread). */
	public TileCache tiles() {
		if (this.tiles == null) {
			this.tiles = new TileCache(this.settings.gpuPages);
		}
		return this.tiles;
	}

	public List<Marker> collect(MarkerQuery query) {
		return this.markers.collect(query);
	}

	/** Work still waiting: chunks to read, regions to draw and analyse, tiles to upload. */
	public int busy() {
		int n = this.ingest == null ? 0 : this.ingest.backlog();
		if (this.session != null) {
			n += this.session.workers.getQueue().size() + this.session.workers.getActiveCount();
		}
		if (this.tiles != null) {
			n += this.tiles.queued();
		}
		return n;
	}

	/** Whether the player is under the ground (or in a dimension with a roof), as of this frame. */
	public boolean underground() {
		return this.underground;
	}

	// --- the frame and the tick ------------------------------------------------------------------------------------

	/** The start of every frame, on the render thread: hand chunks to the workers, upload finished tiles. */
	public void frame() {
		Minecraft mc = Minecraft.getInstance();
		if (this.tiles != null) {
			this.tiles.beginFrame();
			long t0 = System.nanoTime();
			this.tiles.upload(1_200_000L);
			Perf.upload(System.nanoTime() - t0);
		}
		LocalPlayer p = mc.player;
		if (this.ingest != null && p != null && mc.level == this.level) {
			this.underground = isUnderground(p);
			this.ingest.frame(p.getX(), p.getY(), p.getZ(), this.underground);
			if (this.ingest.band() != Integer.MIN_VALUE) {
				this.bands.put(this.ingest.dimension().id, this.ingest.band());
			}
		}
	}

	/**
	 * Whether the player is under the ground: in a dimension with a roof, or with at least a few blocks of something
	 * that is not leaves over their head (under a tree, or a roof, is not underground).
	 */
	private static boolean isUnderground(LocalPlayer p) {
		ClientLevel level = (ClientLevel) p.level();
		if (level.dimensionType().hasCeiling()) {
			return true;
		}
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getBlockX(), p.getBlockZ());
		return p.getY() < surface - 6;
	}

	/** The band shown by a dimension's underground view: the player's, or the last one they were in. */
	public int sliceBand(String dimension) {
		Integer band = this.bands.get(dimension);
		if (band != null) {
			return band;
		}
		if (this.session != null) {
			DimensionData d = this.session.dimensionIfOpen(dimension);
			if (d != null && !d.bands().isEmpty()) {
				// the explored band nearest the player's height
				LocalPlayer p = Minecraft.getInstance().player;
				int at = DimensionData.bandOf(p == null ? 0 : p.getBlockY());
				Integer below = d.bands().floor(at);
				return below != null ? below : d.bands().first();
			}
		}
		return dimension.equals("minecraft:the_nether") ? 4 : 2;
	}

	/** Shows a band in a dimension's underground view (the map's depth control). */
	public void showBand(String dimension, int band) {
		this.bands.put(dimension, band);
	}

	private void tick(Minecraft mc) {
		if (BASELINE) {
			return;
		}
		long t0 = System.nanoTime();
		try {
			this.tickInner(mc);
		} finally {
			Perf.tick(System.nanoTime() - t0);
		}
	}

	private void tickInner(Minecraft mc) {
		this.ticks++;
		if (mc.level != this.level) {
			this.levelChanged(mc);
		}
		if (this.session != null && this.modelSet != mc.getModelManager().getBlockStateModelSet()) {
			// resources reloaded: read the textures again
			this.rebuildLooks(mc);
		}
		while (this.openMap.consumeClick()) {
			if (mc.gui.screen() == null && mc.level != null) {
				this.openMap();
			}
		}
		while (this.toggleMinimap.consumeClick()) {
			this.settings.minimap = !this.settings.minimap;
			this.settings.save();
		}
		while (this.zoomIn.consumeClick()) {
			this.settings.minimapZoom = Math.min(AtlasSettings.ZOOMS.length - 1, this.settings.minimapZoom + 1);
		}
		while (this.zoomOut.consumeClick()) {
			this.settings.minimapZoom = Math.max(0, this.settings.minimapZoom - 1);
		}
		while (this.dropWaypoint.consumeClick()) {
			this.dropWaypointHere(mc);
		}
		while (this.openSettings.consumeClick()) {
			if (mc.gui.screen() == null) {
				this.openSettings(null);
			}
		}
		LocalPlayer p = mc.player;
		MapSession session = this.session;
		if (p != null && session != null) {
			if (p.isDeadOrDying()) {
				if (!this.deathRecorded) {
					this.deathRecorded = true;
					session.waypoints.addDeath(p.getBlockX(), p.getBlockY(), p.getBlockZ(), p.level().dimension().identifier().toString(),
						System.currentTimeMillis());
					session.saveSoon(false);
				}
			} else {
				this.deathRecorded = false;
			}
			String dim = p.level().dimension().identifier().toString();
			if (this.ticks % 40 == 0) {
				this.discover(mc, p);
			}
			if (this.ticks % 10 == 0) {
				long now = System.currentTimeMillis();
				int view = mc.options.getEffectiveRenderDistance() * 16;
				session.places.proximity(dim, p.getX(), p.getEyeY(), p.getZ(), view, now);
				DimensionData d = session.dimensionIfOpen(dim);
				if (d != null) {
					String region = d.areas.regionAt(p.getBlockX(), p.getBlockZ());
					if (region != null) {
						session.places.enter(region, now);
					}
					String water = d.areas.waterAt(p.getBlockX(), p.getBlockZ());
					if (water != null) {
						session.places.enter(water, now);
					}
				}
			}
			if (this.ticks % 20 == 5) {
				this.places.poll(dim);
			}
			if (this.ticks % 20 == 0) {
				session.places.setGameDay(p.level().getOverworldClockTime() / 24000 + 1);
			}
			for (Place changed : session.places.drainChanges()) {
				this.places.notify(changed);
				if (changed.isDiscovered() && changed.dimension().equals(dim) && !changed.kind().equals(Place.RIVER)
					&& !changed.kind().equals(Place.CAVE_MOUTH) && !changed.kind().equals(Place.RAVINE)) {
					// the design's toast, once, as the player comes within reach (or walks into a region)
					this.overlays.discovered(changed);
				}
				session.saveSoon(false);
			}
		}
		if (session != null && this.ticks % (20 * 30) == 0) {
			session.saveSoon(false);
		}
		if (this.logStats && this.ticks % 100 == 0 && session != null) {
			LOGGER.info("Atlas: {} chunks read ({} ms each), {} detected ({} ms each), {} reveals ({} ms each), {} regions drawn, {} analysed, {} places, "
					+ "{} tiles resident in {} pages, {} uploads ({} MB), backlog {}",
				Perf.SCANNED.get(), ms(Perf.SCAN_NANOS.get(), Perf.SCANNED.get()), Perf.DETECTED.get(), ms(Perf.DETECT_NANOS.get(), Perf.DETECTED.get()),
				Perf.REVEALS.get(), ms(Perf.REVEAL_NANOS.get(), Perf.REVEALS.get()), Perf.RENDERS.get(), Perf.ANALYSED.get(), session.places.size(),
				this.tiles == null ? 0 : this.tiles.residentTiles(), this.tiles == null ? 0 : this.tiles.pages(), Perf.uploads, Perf.uploadBytes >> 20,
				this.busy());
		}
	}

	private static String ms(long nanos, long n) {
		return String.format(Locale.ROOT, "%.3f", nanos / 1e6 / Math.max(1, n));
	}

	private void levelChanged(Minecraft mc) {
		ClientLevel next = mc.level;
		this.level = next;
		this.ingest = null;
		if (next == null) {
			this.closeSession();
			return;
		}
		String key = worldKey(mc);
		if (this.session == null || !this.session.worldKey.equals(key)) {
			this.closeSession();
			Path dir = mc.gameDirectory.toPath().resolve("atlas").resolve(key);
			long seed = ((BiomeManagerAccessor) next.getBiomeManager()).atlas$biomeZoomSeed();
			this.session = new MapSession(key, dir, this.settings.workerThreads, NameGen.seedHash(key.startsWith("sp/") ? "sp" : key, seed), change -> {
				TileCache cache = this.tiles;
				if (cache != null) {
					cache.regionChanged(change);
				}
			});
			MapSession s = this.session;
			s.places.setExactAvailable(mc.getSingleplayerServer() != null);
			s.landmarks.setNamer((dimension, kind, biome, x, z, area) -> this.places.name(s, dimension, kind, biome, x, z, area));
			this.rebuildLooks(mc);
			// the GPU pages are made now, with the world loading, rather than in the middle of a frame later
			this.tiles().reserve();
			LOGGER.info("VOID Atlas: mapping {} into {}", key, dir);
		}
		this.ingest = new Ingest(this.session, next);
		LocalPlayer p = mc.player;
		if (p != null) {
			this.ingest.loadedAround(p.getBlockX() >> 4, p.getBlockZ() >> 4, mc.options.getEffectiveRenderDistance() + 1);
		}
	}

	private void rebuildLooks(Minecraft mc) {
		if (this.session == null || mc.level == null) {
			return;
		}
		this.modelSet = mc.getModelManager().getBlockStateModelSet();
		BlockLooks looks = BlockLooks.build(this.session.blockNames, this.session.biomeNames, mc.level.registryAccess().lookupOrThrow(Registries.BIOME));
		this.session.setLooks(looks);
		LOGGER.info("VOID Atlas: read the textures of every block in {} ms", String.format(Locale.ROOT, "%.1f", looks.buildNanos / 1e6));
	}

	private void closeSession() {
		this.exactStarts.clear();
		this.exactStates.clear();
		this.exactChunks.clear();
		if (this.session != null) {
			this.session.close();
			this.session = null;
		}
		this.ingest = null;
		this.markersFrom = List.of();
		this.placeMarkers = List.of();
		if (this.tiles != null) {
			this.tiles.clear();
		}
	}

	/** Where a world's map is kept: {@code sp/<save folder>} or {@code mp/<address>/<world seed hash>}. */
	static String worldKey(Minecraft mc) {
		var server = mc.getSingleplayerServer();
		if (server != null) {
			Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
			return "sp/" + sanitise(root.getFileName() == null ? "world" : root.getFileName().toString());
		}
		ServerData data = mc.getCurrentServer();
		String address = data == null || data.ip == null || data.ip.isBlank() ? "unknown" : data.ip.trim();
		long seed = mc.level == null ? 0 : ((BiomeManagerAccessor) mc.level.getBiomeManager()).atlas$biomeZoomSeed();
		return "mp/" + sanitise(address) + "/" + Long.toHexString(seed);
	}

	private static String sanitise(String s) {
		String out = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
		return out.isEmpty() ? "_" : out;
	}

	/** A block changed somewhere the client can see (client thread). */
	public static void blockChanged(ClientLevel level, BlockPos pos) {
		AtlasClient a = instance;
		if (a != null && a.ingest != null && level == a.level) {
			a.ingest.changed(pos.getX(), pos.getZ());
		}
	}

	// --- markers Atlas provides itself -------------------------------------------------------------------------------

	public static String waypointMarkerId(Waypoint w) {
		return "atlas:wp:" + w.id();
	}

	public static String placeMarkerId(Place p) {
		return "atlas:place:" + p.id();
	}

	public @Nullable Waypoint waypointFor(String markerId) {
		if (this.session == null || !markerId.startsWith("atlas:wp:")) {
			return null;
		}
		return this.session.waypoints.get(markerId.substring("atlas:wp:".length()));
	}

	public @Nullable Place placeFor(String markerId) {
		if (this.session == null || !markerId.startsWith("atlas:place:")) {
			return null;
		}
		return this.session.places.get(markerId.substring("atlas:place:".length()));
	}

	private void waypointMarkers(MarkerQuery q, Consumer<Marker> out) {
		if (this.session == null) {
			return;
		}
		for (Waypoint w : this.session.waypoints.in(q.dimension())) {
			if (!w.visible()) {
				continue;
			}
			boolean death = w.kind() == Waypoint.Kind.DEATH;
			out.accept(new Marker(waypointMarkerId(w), MapLayer.WAYPOINTS, w.name(), w.y() < 40 ? "Y " + MapPage.minus(w.y()) : "",
				death ? "DEATH POINT" : "WAYPOINT", w.x() + 0.5, w.y(), w.z() + 0.5, w.dimension(), death ? MarkerGlyph.CROSS : MarkerGlyph.PIN,
				Marker.Style.WAYPOINT, w.rgb(), null, Source.player(), Tier.MAJOR));
		}
	}

	/**
	 * The places the player knows of, as markers: a place only sighted is a "?" tile labelled "Sighted"; a landmark is a
	 * major tile, a place to explore a minor one; regions, lakes and rivers are names on the land (a lake is also a
	 * landmark tile once zoomed in). Kinds switched off in the legend are left out. Made again only when the places or
	 * the legend change (the map and minimap ask every frame).
	 */
	private void placeMarkers(MarkerQuery q, Consumer<Marker> out) {
		MapSession session = this.session;
		if (session == null) {
			return;
		}
		List<Place> visible = session.places.visible(q.dimension());
		List<Marker> made;
		synchronized (this) {
			if (visible != this.markersFrom || !q.dimension().equals(this.markersDim) || this.markersLegend != this.settings.legendVersion) {
				List<Marker> list = new ArrayList<>(visible.size());
				java.util.Map<String, Place> byId = new java.util.HashMap<>();
				DimensionData d = session.dimensionIfOpen(q.dimension());
				java.util.Map<String, Place> names = new java.util.HashMap<>();
				for (Place p : visible) {
					if (p.kind().equals(Place.REGION)) {
						names.put(p.id(), p);
					}
				}
				for (Place p : visible) {
					if (!this.settings.kindVisible(dev.voidmc.atlas.client.ui.Kinds.legend(p).name())) {
						continue;
					}
					String region = d == null || p.isLabel() ? null : d.areas.regionAt(p.x(), p.z());
					Place regionPlace = region == null ? null : names.get(region);
					for (Marker m : placeMarkers(p, regionPlace == null ? null : regionPlace.name())) {
						list.add(m);
						byId.put(m.id(), p);
					}
				}
				this.markersFrom = visible;
				this.markersDim = q.dimension();
				this.markersLegend = this.settings.legendVersion;
				this.placeMarkers = list;
				this.markerPlaces = byId;
			}
			made = this.placeMarkers;
		}
		boolean all = q.purpose() == MarkerQuery.Purpose.LIST;
		for (Marker m : made) {
			if (all || q.contains(m.x(), m.z())) {
				out.accept(m);
			}
		}
	}

	/** The place a marker of Atlas's own stands for, or null. */
	public @Nullable Place placeOf(Marker m) {
		return this.markerPlaces.get(m.id());
	}

	/** A place's markers: its tile, its name on the land, or both (a lake). */
	public static List<Marker> placeMarkers(Place p, @Nullable String region) {
		List<Marker> out = new ArrayList<>(2);
		String subtitle = subtitle(p, region);
		if (p.isLabel()) {
			out.add(new Marker("atlas:label:" + p.id(), MapLayer.NAMES, p.name(), "", subtitle, p.x() + 0.5, p.y(), p.z() + 0.5, p.dimension(),
				MarkerGlyph.DOT, Marker.Style.LABEL, 0, null, p.source(), p.tier()));
			if (!p.kind().equals(Place.LAKE)) {
				return out;
			}
		}
		boolean sighted = dev.voidmc.atlas.client.ui.Kinds.sighted(p);
		boolean major = dev.voidmc.atlas.client.ui.Kinds.major(p);
		String name = sighted ? "Sighted" : p.name();
		String meta = p.kind().equals(Place.PEAK) ? String.valueOf(p.y()) : "";
		out.add(new Marker(placeMarkerId(p), major ? MapLayer.LANDMARKS : MapLayer.PLACES, name, meta, subtitle, p.x() + 0.5, p.y(), p.z() + 0.5,
			p.dimension(), dev.voidmc.atlas.client.ui.Kinds.glyph(p), Marker.Style.STRUCTURE, 0, null, p.source(), major ? Tier.MAJOR : Tier.MINOR));
		return out;
	}

	public static Marker placeMarker(Place p) {
		List<Marker> all = placeMarkers(p, null);
		return all.get(all.size() - 1);
	}

	/** The caps line of a place: what it is, and where (its region, or its height underground). */
	public static String subtitle(Place p, @Nullable String region) {
		String what = dev.voidmc.atlas.client.ui.Kinds.of(p).word;
		if (p.underground()) {
			return what + "  ·  Y " + MapPage.minus(p.y());
		}
		return region == null || region.isEmpty() ? what : what + "  ·  " + region.toUpperCase(Locale.ROOT);
	}

	public static String subtitle(Place p) {
		return subtitle(p, null);
	}

	private void playerMarkers(MarkerQuery q, Consumer<Marker> out) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null || !mc.level.dimension().identifier().toString().equals(q.dimension())) {
			return;
		}
		for (Player other : mc.level.players()) {
			if (other == mc.player || other.isInvisible()) {
				continue;
			}
			out.accept(new Marker("atlas:player:" + other.getUUID(), MapLayer.PLAYERS, other.getName().getString(), "", "PLAYER", other.getX(), other.getY(),
				other.getZ(), q.dimension(), MarkerGlyph.DOT, Marker.Style.PLAYER, 0, null, Source.seen(), Tier.MAJOR));
		}
	}

	// --- exact structure data (singleplayer) ------------------------------------------------------------------------

	/**
	 * In singleplayer the integrated server knows exactly where every structure is: every structure start in the chunks
	 * round the player (as far as they can see) is filed with its bounds. One on the surface is sighted within view
	 * and discovered within reach; one underground stays unknown until the player is near (no x-ray). Exact data wins
	 * over what was worked out from the blocks ({@link dev.voidmc.atlas.core.places.PlaceBook#exact}).
	 */
	/** A structure start the integrated server knows of: its bounds, and how far the player has come with it. */
	private record ExactStart(String dimension, String id, String family, int[] box, boolean underground) {
	}

	/** The structure starts found so far this session, by dimension and id; and the chunks already read for them. */
	private final ConcurrentHashMap<String, ExactStart> exactStarts = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, DiscoveryState> exactStates = new ConcurrentHashMap<>();
	private final java.util.Set<Long> exactChunks = ConcurrentHashMap.newKeySet();

	private void discover(Minecraft mc, LocalPlayer p) {
		var server = mc.getSingleplayerServer();
		MapSession session = this.session;
		if (server == null || session == null) {
			return;
		}
		var dimension = p.level().dimension();
		String dimId = dimension.identifier().toString();
		int px = p.getBlockX();
		int py = p.getBlockY();
		int pz = p.getBlockZ();
		int view = mc.options.getEffectiveRenderDistance();
		long dimSalt = (long) dimId.hashCode() << 40;
		// on the server: read only the chunks not read before (a few new ones as the player moves), for new starts
		server.execute(() -> {
			ServerLevel level = server.getLevel(dimension);
			if (level == null) {
				return;
			}
			var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
			int pcx = px >> 4;
			int pcz = pz >> 4;
			for (int dz = -view; dz <= view; dz++) {
				for (int dx = -view; dx <= view; dx++) {
					long chunkKey = dimSalt ^ ChunkPos.pack(pcx + dx, pcz + dz);
					if (this.exactChunks.contains(chunkKey)) {
						continue;
					}
					LevelChunk chunk = level.getChunkSource().getChunkNow(pcx + dx, pcz + dz);
					if (chunk == null) {
						continue;
					}
					this.exactChunks.add(chunkKey);
					for (var e : chunk.getAllReferences().entrySet()) {
						Structure structure = e.getKey();
						var key = registry.getResourceKey(structure);
						if (key.isEmpty()) {
							continue;
						}
						String id = key.get().identifier().toString();
						if (StructureNames.hidden(id)) {
							continue;
						}
						for (long startKey : e.getValue()) {
							String startId = dimId + "|" + id + "|" + startKey;
							if (this.exactStarts.containsKey(startId)) {
								continue;
							}
							ChunkAccess startChunk = level.getChunk(ChunkPos.getX(startKey), ChunkPos.getZ(startKey), ChunkStatus.STRUCTURE_STARTS, false);
							StructureStart start = startChunk == null ? null : startChunk.getStartForStructure(structure);
							if (start == null || !start.isValid()) {
								continue;
							}
							this.exactStarts.put(startId, this.exactStart(level, registry, structure, start, id, dimId));
						}
					}
				}
			}
		});
		// on the client: move the known starts on by distance, telling the place book only when something changed
		long now = System.currentTimeMillis();
		int viewBlocks = view * 16;
		for (var entry : this.exactStarts.entrySet()) {
			ExactStart s = entry.getValue();
			if (!s.dimension.equals(dimId)) {
				continue;
			}
			DiscoveryState state = exactState(s, px, py, pz, viewBlocks);
			DiscoveryState before = this.exactStates.get(entry.getKey());
			if (before != null && !state.atLeast(before) || before == state) {
				continue;
			}
			this.exactStates.put(entry.getKey(), before == null ? state : DiscoveryState.max(before, state));
			Place place = session.places.exact(dimId, s.id, s.family, s.box, s.underground, state, now);
			if (place != null && state == DiscoveryState.DISCOVERED) {
				LOGGER.info("VOID Atlas: discovered {} ({}) at {}, {}", place.name(), s.id, place.x(), place.z());
			}
		}
	}

	/** A start's bounds and family, and whether it lies underground (by its middle: a mineshaft is a thing of the deep). Server thread. */
	private ExactStart exactStart(ServerLevel level, net.minecraft.core.Registry<Structure> registry, Structure structure, StructureStart start, String id,
		String dimId) {
		BoundingBox bb = start.getBoundingBox();
		int cx = (bb.minX() + bb.maxX()) / 2;
		int cz = (bb.minZ() + bb.maxZ()) / 2;
		boolean village = registry.wrapAsHolder(structure).is(StructureTags.VILLAGE);
		int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, cx, cz);
		boolean underground = !level.dimensionType().hasCeiling() && ((bb.minY() + bb.maxY()) / 2 < surface - 10 || bb.maxY() < surface - 4);
		String family = village ? "minecraft:village" : StructureNames.family(id);
		return new ExactStart(dimId, id, family, new int[]{bb.minX(), bb.minY(), bb.minZ(), bb.maxX(), bb.maxY(), bb.maxZ()}, underground);
	}

	/** How far the player has come with a structure: as for every place (sighted in view or near underground, discovered within reach). */
	private static DiscoveryState exactState(ExactStart s, int px, int py, int pz, int viewBlocks) {
		int[] b = s.box;
		double dx = Math.max(Math.max(b[0] - px, 0), px - b[3]);
		double dz = Math.max(Math.max(b[2] - pz, 0), pz - b[5]);
		double dy = Math.max(Math.max(b[1] - py, 0), py - b[4]);
		double flat = Math.hypot(dx, dz);
		if (s.underground) {
			double d = Math.sqrt(flat * flat + dy * dy);
			return d <= dev.voidmc.atlas.core.places.PlaceBook.UNDERGROUND_REACH ? DiscoveryState.DISCOVERED
				: d <= dev.voidmc.atlas.core.places.PlaceBook.UNDERGROUND_SIGHT ? DiscoveryState.SIGHTED : DiscoveryState.UNKNOWN;
		}
		return flat <= dev.voidmc.atlas.core.places.PlaceBook.SURFACE_REACH ? DiscoveryState.DISCOVERED
			: flat <= viewBlocks ? DiscoveryState.SIGHTED : DiscoveryState.UNKNOWN;
	}

	// --- helpers for the screens -------------------------------------------------------------------------------------

	/** The biome's readable name and its source tag ("EXPANSE"; empty for vanilla). */
	public String[] biomeAt(BlockPos pos) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return new String[]{"", ""};
		}
		var key = mc.level.getBiome(pos).unwrapKey();
		if (key.isEmpty()) {
			return new String[]{"Unknown", ""};
		}
		Identifier id = key.get().identifier();
		String name = titleCase(id.getPath());
		String source = id.getNamespace().equals("minecraft") ? "" : id.getNamespace().toUpperCase(Locale.ROOT);
		return new String[]{name, source};
	}

	private long regionHereKey = Long.MIN_VALUE;
	private long regionHereVersion = -1;
	private @Nullable Place regionHere;

	/** The named region the player stands in ("Larkspur Meadow"), or null (looked up again when they move a block). */
	public @Nullable Place regionHere() {
		MapSession session = this.session;
		LocalPlayer p = Minecraft.getInstance().player;
		if (session == null || p == null) {
			return null;
		}
		long key = Coords.pack(p.getBlockX(), p.getBlockZ()) ^ p.level().dimension().hashCode();
		long version = session.places.version();
		if (key == this.regionHereKey && version == this.regionHereVersion) {
			return this.regionHere;
		}
		this.regionHereKey = key;
		this.regionHereVersion = version;
		DimensionData d = session.dimensionIfOpen(p.level().dimension().identifier().toString());
		String id = d == null ? null : d.areas.regionAt(p.getBlockX(), p.getBlockZ());
		this.regionHere = id == null ? null : session.places.get(id);
		return this.regionHere;
	}

	/** "woodland_glades" to "Woodland Glades". */
	public static String titleCase(String path) {
		StringBuilder b = new StringBuilder();
		for (String word : path.replace('/', '_').split("_")) {
			if (word.isEmpty()) {
				continue;
			}
			if (!b.isEmpty()) {
				b.append(' ');
			}
			b.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return b.toString();
	}

	/** The map's height at a column (the top of the land, or the floor of the slice), or the player's height if unknown. */
	public int heightAt(@Nullable LayerData layer, int x, int z) {
		LocalPlayer p = Minecraft.getInstance().player;
		int fallback = p == null ? 64 : p.getBlockY();
		if (layer == null) {
			return fallback;
		}
		RegionRaw raw = layer.raw(Coords.region(x), Coords.region(z), false);
		if (raw == null) {
			return fallback;
		}
		int i = RegionRaw.index(Coords.inRegion(x), Coords.inRegion(z));
		if (!raw.known(i) || (raw.flags[i] & RegionRaw.ROCK) != 0) {
			return fallback;
		}
		return raw.height[i] + 1;
	}

	/** Whether the player may teleport from the map: an operator (or a singleplayer world with cheats) in creative or spectator. */
	public boolean canTeleport() {
		LocalPlayer p = Minecraft.getInstance().player;
		return p != null && (p.isCreative() || p.isSpectator()) && p.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	public void teleport(String dimension, int x, int y, int z) {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null || !this.canTeleport()) {
			return;
		}
		String here = p.level().dimension().identifier().toString();
		String tp = "tp @s " + x + " " + y + " " + z;
		p.connection.sendCommand(here.equals(dimension) ? tp : "execute in " + dimension + " run " + tp);
	}

	private void dropWaypointHere(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || this.session == null) {
			return;
		}
		int n = this.session.waypoints.all().size() + 1;
		Waypoint w = Waypoint.create("Waypoint " + n, p.getBlockX(), p.getBlockY(), p.getBlockZ(), p.level().dimension().identifier().toString(),
			n % Waypoint.COLOURS.length);
		this.session.waypoints.put(w);
		this.session.saveSoon(false);
		p.sendOverlayMessage(Component.literal("Waypoint " + n + " added  ·  M opens the map"));
	}

	public void openMap() {
		Minecraft.getInstance().gui.setScreen(new AtlasScreen(this));
	}

	public void openSettings(net.minecraft.client.gui.screens.@Nullable Screen parent) {
		Minecraft.getInstance().gui.setScreen(new SettingsScreen(this, parent));
	}

	/** Every dimension's layers currently open, for the tests and the stats. */
	public List<LayerData> openLayers() {
		List<LayerData> out = new ArrayList<>();
		if (this.session != null) {
			for (DimensionData d : this.session.dimensions()) {
				out.addAll(d.layers());
			}
		}
		return out;
	}

	public dev.voidmc.atlas.client.ui.HudOverlays overlays() {
		return this.overlays;
	}

	public @Nullable Ingest ingest() {
		return this.ingest;
	}
}
