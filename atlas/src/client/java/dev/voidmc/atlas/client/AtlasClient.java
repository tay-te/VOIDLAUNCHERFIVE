package dev.voidmc.atlas.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmc.atlas.api.AtlasApi;
import dev.voidmc.atlas.api.AtlasPlugin;
import dev.voidmc.atlas.api.Discovery;
import dev.voidmc.atlas.api.MapLayer;
import dev.voidmc.atlas.api.Marker;
import dev.voidmc.atlas.api.MarkerGlyph;
import dev.voidmc.atlas.api.MarkerQuery;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
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
 * hand-toned palette with stepped relief; a minimap; waypoints; the structures found in singleplayer; and an API
 * ({@link AtlasApi}) for other mods' markers and for embedding the map (the VOID Hub).
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
	/** The last slice band the player was in, per dimension (for the underground and Nether views). */
	private final ConcurrentHashMap<String, Integer> bands = new ConcurrentHashMap<>();
	private final boolean logStats = Boolean.getBoolean("atlas.logStats");
	private AtlasApiImpl api;

	public static AtlasClient get() {
		return instance;
	}

	@Override
	public void onInitializeClient() {
		instance = this;
		this.settings = AtlasSettings.load(FabricLoader.getInstance().getConfigDir().resolve("atlas.json"));
		this.markers.registerLayer(new MapLayer(MapLayer.TERRAIN, "Terrain", true, 0));
		this.markers.registerLayer(new MapLayer(MapLayer.STRUCTURES, "Structures", true, 10));
		this.markers.registerLayer(new MapLayer(MapLayer.WAYPOINTS, "Waypoints", true, 20));
		this.markers.registerLayer(new MapLayer(MapLayer.PLAYERS, "Players", true, 30));
		this.markers.registerProvider("atlas:discoveries", -30, this::discoveryMarkers);
		this.markers.registerProvider("atlas:waypoints", 20, this::waypointMarkers);
		this.markers.registerProvider("atlas:players", 30, this::playerMarkers);
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
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ID, "minimap"), this.minimap::extract);

		for (var container : FabricLoader.getInstance().getEntrypointContainers("atlas", AtlasPlugin.class)) {
			try {
				container.getEntrypoint().onAtlasReady(this.markers);
				LOGGER.info("VOID Atlas: {} added its markers", container.getProvider().getMetadata().getId());
			} catch (Throwable t) {
				LOGGER.warn("VOID Atlas: {}'s atlas entrypoint failed: {}", container.getProvider().getMetadata().getId(), t.toString());
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

	/** Work still waiting: chunks to read, regions to draw, tiles to upload. */
	public int busy() {
		int n = this.ingest == null ? 0 : this.ingest.backlog();
		if (this.session != null) {
			n += this.session.workers.getQueue().size();
		}
		if (this.tiles != null) {
			n += this.tiles.queued();
		}
		return n;
	}

	// --- the frame and the tick ------------------------------------------------------------------------------------

	/** The start of every frame, on the render thread: hand chunks to the workers, upload finished tiles. */
	public void frame() {
		Minecraft mc = Minecraft.getInstance();
		if (this.tiles != null) {
			this.tiles.beginFrame();
			this.tiles.upload(1_200_000L);
		}
		LocalPlayer p = mc.player;
		if (this.ingest != null && p != null && mc.level == this.level) {
			this.ingest.frame(p.getX(), this.scanY(p), p.getZ());
			this.bands.put(this.ingest.dimension().id, this.ingest.band());
		}
	}

	/**
	 * The height whose slice is read and shown underground: the player's feet when they are underground (or in the
	 * Nether); otherwise a little below the surface they stand on, so the underground view shows the caves beneath.
	 */
	private double scanY(LocalPlayer p) {
		ClientLevel level = (ClientLevel) p.level();
		double y = p.getY();
		if (!level.dimensionType().hasCeiling()) {
			int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getBlockX(), p.getBlockZ());
			if (y > surface - 6) {
				y = surface - 24;
			}
		}
		return y;
	}

	/** The slice band shown for a dimension's underground view. */
	public int sliceBand(String dimension) {
		Integer band = this.bands.get(dimension);
		if (band != null) {
			return band;
		}
		return dimension.equals("minecraft:the_nether") ? 4 : 2;
	}

	private void tick(Minecraft mc) {
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
		if (p != null && this.session != null) {
			if (p.isDeadOrDying()) {
				if (!this.deathRecorded) {
					this.deathRecorded = true;
					this.session.waypoints.addDeath(p.getBlockX(), p.getBlockY(), p.getBlockZ(), p.level().dimension().identifier().toString(),
						System.currentTimeMillis());
					this.session.saveSoon(false);
				}
			} else {
				this.deathRecorded = false;
			}
			if (this.ticks % 40 == 0) {
				this.discover(mc, p);
			}
		}
		if (this.session != null && this.ticks % (20 * 30) == 0) {
			this.session.saveSoon(false);
		}
		if (this.logStats && this.ticks % 100 == 0 && this.session != null) {
			LOGGER.info("Atlas: {} chunks read ({} ms each), {} regions drawn, {} tiles resident in {} pages, {} uploads ({} MB), backlog {}",
				Perf.SCANNED.get(), String.format(Locale.ROOT, "%.3f", Perf.SCAN_NANOS.get() / 1e6 / Math.max(1, Perf.SCANNED.get())), Perf.RENDERS.get(),
				this.tiles == null ? 0 : this.tiles.residentTiles(), this.tiles == null ? 0 : this.tiles.pages(), Perf.uploads, Perf.uploadBytes >> 20, this.busy());
		}
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
			this.session = new MapSession(key, dir, this.settings.workerThreads, change -> {
				TileCache cache = this.tiles;
				if (cache != null) {
					cache.regionChanged(change);
				}
			});
			this.rebuildLooks(mc);
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
		if (this.session != null) {
			this.session.close();
			this.session = null;
		}
		this.ingest = null;
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

	public static String discoveryMarkerId(Discovery d) {
		return "atlas:disc:" + d.id();
	}

	public @Nullable Waypoint waypointFor(String markerId) {
		if (this.session == null || !markerId.startsWith("atlas:wp:")) {
			return null;
		}
		return this.session.waypoints.get(markerId.substring("atlas:wp:".length()));
	}

	public @Nullable Discovery discoveryFor(String markerId) {
		if (this.session == null || !markerId.startsWith("atlas:disc:")) {
			return null;
		}
		String id = markerId.substring("atlas:disc:".length());
		for (Discovery d : this.session.discoveries.all()) {
			if (d.id().equals(id)) {
				return d;
			}
		}
		return null;
	}

	private void waypointMarkers(MarkerQuery q, java.util.function.Consumer<Marker> out) {
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
				Marker.Style.WAYPOINT, w.rgb(), null));
		}
	}

	private void discoveryMarkers(MarkerQuery q, java.util.function.Consumer<Marker> out) {
		if (this.session == null) {
			return;
		}
		for (Discovery d : this.session.discoveries.all()) {
			if (!d.dimension().equals(q.dimension())) {
				continue;
			}
			String subtitle = d.source() + "  ·  " + d.category() + (d.underground() ? "  ·  UNDERGROUND" : "");
			out.accept(new Marker(discoveryMarkerId(d), MapLayer.STRUCTURES, d.name(), "", subtitle, d.x() + 0.5, d.y(), d.z() + 0.5, d.dimension(),
				MarkerPainter.glyphFor(d.category(), d.underground()), Marker.Style.STRUCTURE, 0, null));
		}
	}

	private void playerMarkers(MarkerQuery q, java.util.function.Consumer<Marker> out) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null || !mc.level.dimension().identifier().toString().equals(q.dimension())) {
			return;
		}
		for (Player other : mc.level.players()) {
			if (other == mc.player || other.isInvisible()) {
				continue;
			}
			out.accept(new Marker("atlas:player:" + other.getUUID(), MapLayer.PLAYERS, other.getName().getString(), "", "PLAYER", other.getX(), other.getY(),
				other.getZ(), q.dimension(), MarkerGlyph.DOT, Marker.Style.PLAYER, 0, null));
		}
	}

	// --- discoveries (singleplayer) ---------------------------------------------------------------------------------

	/** How close the player must come to a structure's bounds to discover it. */
	private static final int DISCOVER_REACH = 48;

	private void discover(Minecraft mc, LocalPlayer p) {
		var server = mc.getSingleplayerServer();
		MapSession session = this.session;
		if (server == null || session == null) {
			return;
		}
		var dimension = p.level().dimension();
		String dimId = dimension.identifier().toString();
		int px = p.getBlockX();
		int pz = p.getBlockZ();
		server.execute(() -> {
			ServerLevel level = server.getLevel(dimension);
			if (level == null) {
				return;
			}
			var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
			int pcx = px >> 4;
			int pcz = pz >> 4;
			int r = (DISCOVER_REACH >> 4) + 1;
			java.util.Set<Long> seenStarts = new java.util.HashSet<>();
			for (int dz = -r; dz <= r; dz++) {
				for (int dx = -r; dx <= r; dx++) {
					LevelChunk chunk = level.getChunkSource().getChunkNow(pcx + dx, pcz + dz);
					if (chunk == null) {
						continue;
					}
					for (var e : chunk.getAllReferences().entrySet()) {
						Structure structure = e.getKey();
						for (long startKey : e.getValue()) {
							if (!seenStarts.add(startKey * 31 + System.identityHashCode(structure))) {
								continue;
							}
							ChunkAccess startChunk = level.getChunk(ChunkPos.getX(startKey), ChunkPos.getZ(startKey), ChunkStatus.STRUCTURE_STARTS, false);
							if (startChunk == null) {
								continue;
							}
							StructureStart start = startChunk.getStartForStructure(structure);
							if (start == null || !start.isValid()) {
								continue;
							}
							this.consider(level, registry, structure, start, px, pz, dimId, session);
						}
					}
				}
			}
		});
	}

	private void consider(ServerLevel level, net.minecraft.core.Registry<Structure> registry, Structure structure, StructureStart start, int px, int pz,
		String dimId, MapSession session) {
		BoundingBox bb = start.getBoundingBox();
		double dx = Math.max(Math.max(bb.minX() - px, 0), px - bb.maxX());
		double dz = Math.max(Math.max(bb.minZ() - pz, 0), pz - bb.maxZ());
		if (Math.hypot(dx, dz) > DISCOVER_REACH) {
			return;
		}
		var key = registry.getResourceKey(structure);
		if (key.isEmpty()) {
			return;
		}
		String id = key.get().identifier().toString();
		if (StructureNames.hidden(id)) {
			return;
		}
		int cx = (bb.minX() + bb.maxX()) / 2;
		int cz = (bb.minZ() + bb.maxZ()) / 2;
		int cy = (bb.minY() + bb.maxY()) / 2;
		boolean village = registry.wrapAsHolder(structure).is(StructureTags.VILLAGE);
		int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, cx, cz);
		boolean underground = !level.dimensionType().hasCeiling() && bb.maxY() < surface - 4;
		Discovery d = new Discovery(id, StructureNames.name(id), StructureNames.category(id, village), cx, underground ? cy : Math.max(cy, bb.minY()),
			cz, dimId, System.currentTimeMillis(), underground);
		if (session.discoveries.add(d)) {
			LOGGER.info("VOID Atlas: discovered {} ({}) at {}, {}", d.name(), id, cx, cz);
			session.saveSoon(false);
		}
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

	public @Nullable Ingest ingest() {
		return this.ingest;
	}
}
