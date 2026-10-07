package dev.voidmc.lod;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.VideoMode;
import com.mojang.renderpearl.api.commands.RenderPass;
import dev.voidmc.lod.compat.Shaders;
import dev.voidmc.lod.core.GeneratorCache;
import dev.voidmc.lod.core.AutoTune;
import dev.voidmc.lod.core.ColumnFiles;
import dev.voidmc.lod.core.ColumnStore;
import dev.voidmc.lod.core.IngestSource;
import dev.voidmc.lod.core.LodEngine;
import dev.voidmc.lod.core.TerrainSource;
import dev.voidmc.lod.core.TileBuilder;
import dev.voidmc.lod.gui.LodDebugEntry;
import dev.voidmc.lod.gui.LodSettingsScreen;
import dev.voidmc.lod.render.LodRenderer;
import dev.voidmc.lod.source.ChunkIngest;
import dev.voidmc.lod.source.MaterialNames;
import dev.voidmc.lod.source.RemoteSource;
import dev.voidmc.lod.source.StructureOverlay;
import dev.voidmc.lod.source.StructureSites;
import dev.voidmc.lod.source.Materials;
import dev.voidmc.lod.source.WorldgenSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * VOID LOD: the land past the render distance, out to the horizon.
 *
 * <p>The terrain comes from the world's own generator, sampled one column per LOD cell without building
 * a chunk (and from Expanse's terrain model directly, when the world is an Earth world), so the view is
 * complete the moment a world loads, not once someone has walked it. Over that goes what the client has
 * really seen: every chunk it loads is read into a column store ({@link ChunkIngest}), so builds, dug-out land
 * and structures stay in the far view once their chunks are out of range, kept on disk with the world (or per
 * server) for the next visit. On a server, where there is no generator, that is all the LOD draws.
 *
 * <p> The settings ({@link LodSettings}) live in
 * {@code config/void_lod.json} and on a settings screen (buttons in Options and Video Settings, and a key,
 * unbound by default), where every change applies at once; {@code void.lod.*} system properties override the file for
 * a session. Auto-tune ({@link AutoTune}) keeps the detail within what the machine affords. F8 toggles it.
 */
public final class VoidLod implements ClientModInitializer {
	public static final String ID = "void_lod";
	public static final Logger LOGGER = LoggerFactory.getLogger("VOID LOD");

	private static VoidLod instance;

	/** What is in effect this frame: the settings, as far as auto-tune lets them ({@link #apply}). The reach, in blocks. */
	public double radius;
	/** The most pixels a cell may cover before its tile splits. */
	public double detail;
	/**
	 * The same budget for the smooth far field. It defaults to the near one: the shader's block columns carry the
	 * far detail, so a finer far mesh only sharpens silhouettes, for a third more quads.
	 */
	public double farDetail;
	/** How far the air carries at sea level before the haze is two thirds of the view: by default, 3/4 of the radius. */
	public double hazeReach;
	/** How fast the haze thins with height: it is a third as thick this many blocks above sea level. */
	public static final float HAZE_SCALE_HEIGHT = 160;
	/**
	 * How far the air carries in full rain, at any height, on top of the haze: two thirds hidden at 660 blocks
	 * (and a fifth already at the camera, from vanilla's 160 blocks of rain fog before it), the middle distance
	 * of vanilla's rain fog, which ends 768 blocks out. Mirrored in {@code lod.vsh}.
	 */
	public static final float RAIN_REACH = 660;
	/** Vanilla's rain fog starts this many blocks behind the camera. */
	public static final float RAIN_OFFSET = 160;
	public int threads;
	/** The settings in effect (system properties included), and the same as the file has them: changes go to both. */
	private LodSettings settings;
	private LodSettings saved;
	private Path settingsFile;
	private boolean dirty;
	private final AutoTune tuner = new AutoTune();
	/** The frame time the game is held to (a frame limit, or vsync), 0 if it runs free: auto-tune leaves a game that holds it alone. */
	private double capMillis;
	private boolean enabled;
	/** Whether the chunks the client loads go into the far view (builds, edits, a server's world) and onto disk. */
	public final boolean ingestChunks = Boolean.parseBoolean(System.getProperty("void.lod.ingest", "true"));
	/** Client-thread time each frame may spend reading arrived chunks, when any are waiting. */
	private static final long INGEST_NANOS = 300_000L;
	/** How long a world's LOD waits for the land saved near the player before it starts building without it. */
	private static final long LOAD_WAIT_NANOS = 3_000_000_000L;
	private static final long AROUND_NANOS = 2_000_000_000L;
	private static final long SAVE_NANOS = 60_000_000_000L;
	private LodEngine engine;
	private LodRenderer renderer;
	private ClientLevel level;
	private long sourceVersion;
	private ChunkIngest ingest;
	private ColumnFiles files;
	/** A new world's source, held back while the land saved near the player is read, so the first tiles have it. */
	private TerrainSource waiting;
	private CompletableFuture<Void> loading;
	private long loadingSince;
	private long aroundNanos;
	private long savedNanos;
	private KeyMapping toggle;
	private KeyMapping openSettings;
	private int resets;
	private int ticks;
	private final boolean logStats = Boolean.getBoolean("void.lod.logStats");
	/** This frame's haze for the shader, from the fog mixin: 1 / reach (0 for vanilla's fog), 1 / scale height, base y, rain (0..1). */
	private final float[] haze = new float[4];
	private final float[] sky = new float[12];
	/** This frame's sun for the shader: its direction, and how much of the far relief it lights (0 at night). */
	private final float[] sun = new float[4];
	/** Whether this frame's fog (water, lava, blindness) ends before the chunks do, hiding the LOD entirely. */
	private boolean fogHidesLod;

	public static VoidLod get() {
		return instance;
	}

	@Override
	public void onInitializeClient() {
		instance = this;
		this.settingsFile = FabricLoader.getInstance().getConfigDir().resolve("void_lod.json");
		boolean found = Files.isRegularFile(this.settingsFile);
		this.saved = LodSettings.load(this.settingsFile);
		if (!found) {
			// written once with the defaults, so there is a file to find and edit
			this.saved.save(this.settingsFile);
		}
		this.settings = this.saved.copy();
		this.settings.override(System::getProperty);
		this.enabled = this.settings.enabled;
		// no smooth levels at all, whatever the coarsest level now is (the setting's "never" predates the levels past 32 km)
		TileBuilder.smoothFrom = this.settings.smoothFrom >= LodSettings.BLOCKY_EVERYWHERE ? Integer.MAX_VALUE : this.settings.smoothFrom;
		// a server with VOID LOD describes the land past the view (net.FactsService)
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(dev.voidmc.lod.net.LodNet.Hello.TYPE,
			(hello, context) -> this.hello(hello));
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(dev.voidmc.lod.net.LodNet.TreeChunk.TYPE,
			(trees, context) -> {
				RemoteSource remote = this.remote;
				if (remote != null) {
					remote.receiveTrees(trees);
				}
			});
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(dev.voidmc.lod.net.LodNet.TileFacts.TYPE,
			(facts, context) -> {
				RemoteSource remote = this.remote;
				if (remote != null) {
					remote.receive(facts);
				}
			});
		this.engine = new LodEngine(this.settings.threads, (long) this.settings.budget << 20);
		this.apply();
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "lod"));
		this.toggle = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.void_lod.toggle", InputConstants.KEY_F8, category));
		this.openSettings = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.void_lod.settings", InputConstants.UNKNOWN.getValue(), category));
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
			ChunkIngest ingest = this.ingest;
			if (ingest != null && level == this.level) {
				ingest.loaded(chunk.getPos().x(), chunk.getPos().z());
			}
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> this.closeFiles(true));
		LodDebugEntry.register();
		LOGGER.info("VOID LOD: {} preset, radius {} blocks, detail {} px per cell ({} far), {} worker threads, auto-tune {}",
			this.settings.preset.serializedName(), (int) this.radius, this.detail, this.farDetail, this.threads, this.settings.autoTune ? "on" : "off");
	}

	private void tick(Minecraft mc) {
		while (this.toggle.consumeClick()) {
			this.enabled = !this.enabled;
			if (mc.player != null) {
				mc.player.sendOverlayMessage(Component.literal("VOID LOD " + (this.enabled ? "on" : "off")));
			}
		}
		while (this.openSettings.consumeClick()) {
			if (mc.gui.screen() == null) {
				mc.gui.setScreen(new LodSettingsScreen(null));
			}
		}
		if (mc.level != this.level) {
			this.level = mc.level;
			this.remote = null;
			this.resets++;
			this.closeFiles(false);
			this.ingest = null;
			this.waiting = null;
			this.loading = null;
			this.engine.reset(null, this.gpu());
			this.joinedNanos = System.nanoTime();
			this.horizonReported = false;
			TerrainSource source = this.createSource(mc);
			if (this.files != null && mc.player != null) {
				// read the saved land around the player first, for at most a few seconds
				this.waiting = source;
				this.loading = this.files.loadAround(mc.player.getX(), mc.player.getZ(), this.radius);
				this.loadingSince = System.nanoTime();
			} else {
				this.engine.reset(source, this.gpu());
			}
			this.tuner.reset();
			this.apply();
		}
		long now = System.nanoTime();
		this.sizeGeneratorCaches(now);
		this.adaptThreads(now);
		this.reportHorizon(now);
		StructureSites sites = this.sites;
		if (sites != null && mc.player != null) {
			sites.centre(mc.player.getX(), mc.player.getZ());
		}
		dev.voidmc.lod.source.TreeDecorator trees = this.trees;
		if (trees != null && mc.player != null) {
			trees.centre(mc.player.getX(), mc.player.getZ());
		}
		// what the newly drawn tiles show, for other mods that listen (dev.voidmc.lod.api)
		if (TileBuilder.captureTerrain && mc.level != null) {
			dev.voidmc.lod.api.LodApi.publish(mc.level.dimension(), this.engine.drainTerrain(), this.materials);
		}
		if (this.loading != null && (this.loading.isDone() || now - this.loadingSince > LOAD_WAIT_NANOS)) {
			this.loading = null;
			this.engine.reset(this.waiting, this.gpu());
			this.waiting = null;
		}
		if (this.files != null && mc.player != null && now - this.aroundNanos > AROUND_NANOS) {
			// regions come in as the player nears them, and go once far beyond the LOD's reach
			this.aroundNanos = now;
			this.files.loadAround(mc.player.getX(), mc.player.getZ(), this.radius);
			this.files.unloadBeyond(mc.player.getX(), mc.player.getZ(), this.radius * 1.5 + ColumnStore.REGION_BLOCKS);
			if (now - this.savedNanos > SAVE_NANOS) {
				this.savedNanos = now;
				this.files.saveChanged();
			}
		}
		if (this.hearth != null && mc.player != null && now - this.hearthNanos > AROUND_NANOS) {
			this.hearthNanos = now;
			this.pollHearth(mc.player.getX(), mc.player.getZ());
		}
		if (++this.ticks % 20 == 0) {
			this.capMillis = frameCap(mc);
		}
		if (this.logStats && this.ticks % 100 == 0) {
			LOGGER.info("{} | resets {}, dropped {}, frame {}", this.stats(), this.resets, this.engine.jobsDropped.get(), this.engine.frame());
		}
	}

	/** The frame time the game is held to, in milliseconds: its frame limit, or vsync at the display's rate; 0 if it runs free. */
	private static double frameCap(Minecraft mc) {
		double cap = 0;
		int limit = mc.getFramerateLimitTracker().getFramerateLimit();
		if (limit < 260) {
			// 260 is vanilla's "unlimited"
			cap = 1000.0 / limit;
		}
		if (mc.options.enableVsync().get()) {
			VideoMode mode = mc.getWindow().getActiveVideoMode();
			if (mode != null && mode.getRefreshRate() > 0) {
				cap = Math.max(cap, 1000.0 / mode.getRefreshRate());
			}
		}
		return cap;
	}

	// ---- settings -----------------------------------------------------------------------------------------

	/** The settings in effect, system properties included. Change them through {@link #change}. */
	public LodSettings settings() {
		return this.settings;
	}

	public AutoTune tuner() {
		return this.tuner;
	}

	/**
	 * Changes the settings: in effect from the next frame, and written to the file at the next {@link #save}. A
	 * new reach or detail starts auto-tune over from the settings as chosen.
	 */
	public void change(Consumer<LodSettings> edit) {
		LodSettings before = this.settings.copy();
		edit.accept(this.settings);
		edit.accept(this.saved);
		this.dirty = true;
		if (before.radius != this.settings.radius || before.detail != this.settings.detail || before.farDetail != this.settings.farDetail
			|| before.autoTune != this.settings.autoTune) {
			this.tuner.reset();
		}
		this.apply();
	}

	/** Writes the settings file, if anything changed since it was last written. */
	public void save() {
		if (this.dirty) {
			this.dirty = false;
			this.saved.save(this.settingsFile);
		}
	}

	/**
	 * Puts the settings into effect, as far as auto-tune lets them: the reach and the detail budgets the next
	 * selection uses, the haze, the worker threads and the memory budget. None of it needs a tile rebuilt: the
	 * selection picks other levels of the same tiles, and the rest is per frame.
	 */
	private void apply() {
		LodSettings s = this.settings;
		boolean tuned = s.autoTune;
		this.radius = Math.max(LodSettings.MIN_RADIUS, s.radius * (tuned ? this.tuner.reachScale() : 1));
		this.detail = s.detail * (tuned ? this.tuner.detailScale() : 1);
		this.farDetail = s.farDetail * (tuned ? this.tuner.detailScale() : 1);
		this.hazeReach = s.haze > 0 ? s.haze : this.radius * 0.75;
		this.threads = s.threads;
		this.engine.setThreads(s.threads);
		this.engine.setBudget((long) s.budget << 20);
		this.tuner.setTargets(s.gpuTarget, s.prepareTarget);
		this.generatorBusy = false;
		this.idleSince = System.nanoTime();
	}

	/** How often the worker count is reconsidered, and the queue past which more workers may join in. */
	private static final long ADAPT_NANOS = 2_000_000_000L;
	private static final int ADAPT_QUEUE = 32;
	/** Below this share of the machine's CPU busy, a long queue gets more workers. */
	private static final double ADAPT_LOAD = 0.65;
	private long adaptedNanos;
	private final com.sun.management.OperatingSystemMXBean os =
		java.lang.management.ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class);

	/**
	 * More workers while a long queue meets an idle machine (a first visit, a long flight), up to twice the preset's
	 * and never more than all but two cores; the preset's again once the queue is short or the machine busy, so the
	 * game's own threads (the server generating chunks, the renderer) keep what they need. Workers run at minimum
	 * priority either way.
	 */
	private void adaptThreads(long now) {
		if (now - this.adaptedNanos < ADAPT_NANOS) {
			return;
		}
		this.adaptedNanos = now;
		int threads = this.settings.threads;
		if (this.engine.queued() > ADAPT_QUEUE && this.os != null) {
			double load = this.os.getCpuLoad();
			if (load >= 0 && load < ADAPT_LOAD) {
				threads = Math.max(threads, Math.min(Math.max(threads * 2, threads + 2), LodSettings.CORES - 2));
			}
		}
		if (threads != this.engine.threads()) {
			this.engine.setThreads(threads);
		}
	}

	/** The structures stood on unexplored land, or null (a server, structures off). */
	public StructureSites sites() {
		return this.sites;
	}

	/** The material registry of the current source: what the API turns material ids into blocks with. */
	private Materials materials;

	/** The current level's material registry (block faces by id), shared by its sources and the renderer; null without one. */
	public Materials materials() {
		return this.materials;
	}

	/** When this level was joined, and whether the time its horizon took to build has been logged. */
	private long joinedNanos;
	private boolean horizonReported;

	/** Logs, once a level, how long its horizon took to build from joining: what a player waits to see it whole. */
	private void reportHorizon(long now) {
		LodEngine e = this.engine;
		if (this.horizonReported || this.waiting != null || e.source() == null || e.tileCount() == 0 || e.queued() > 0 || !e.idle()) {
			return;
		}
		this.horizonReported = true;
		GeneratorCache cache = this.generatorCache;
		LOGGER.info(String.format(Locale.ROOT, "VOID LOD: horizon built in %.1f s, %d tiles, radius %d%s", (now - this.joinedNanos) / 1e9,
			e.tileCount(), (int) this.radius, cache == null ? "" : " (" + cache.stats() + ")"));
	}

	/** Whether the generator's caches are sized for building (see {@link WorldgenSource#reach}), and since when the queue is empty. */
	private boolean generatorBusy;
	private long idleSince;

	/**
	 * Sizes the generator's caches to the reach while tiles are being built, and lets them go once the queue has been
	 * empty for a few seconds: on a long horizon they hold hundreds of megabytes that nothing needs once it is built.
	 */
	private void sizeGeneratorCaches(long now) {
		TerrainSource source = this.waiting != null ? this.waiting : this.engine.source();
		if (source instanceof IngestSource seen) {
			source = seen.fallback();
		}
		if (source instanceof StructureOverlay overlay) {
			source = overlay.generator();
		}
		if (source instanceof GeneratorCache cache) {
			source = cache.generator();
		}
		if (!(source instanceof WorldgenSource generator)) {
			return;
		}
		// still building: work queued, or this level's horizon not built once yet (joining, loading the saved land)
		boolean queued = this.engine.queued() > 0 || this.waiting != null || !this.horizonReported;
		if (queued) {
			this.idleSince = now;
		}
		boolean busy = queued || now - this.idleSince < 5_000_000_000L;
		if (busy != this.generatorBusy) {
			this.generatorBusy = busy;
			generator.reach(this.radius, busy);
		}
	}

	/**
	 * The terrain source for the level the player is in, or null where there is none to be had: the loaded chunks
	 * over the generator, the generator alone with ingestion off, the loaded chunks alone on a server.
	 */
	private TerrainSource createSource(Minecraft mc) {
		if (mc.level == null || mc.level.dimensionType().hasCeiling()) {
			// a ceiling hides the distance
			return null;
		}
		Materials materials = new Materials();
		this.materials = materials;
		TileBuilder.packMaterials = new dev.voidmc.lod.source.PackMaterials(materials);
		TerrainSource generator = this.createGenerator(mc, materials);
		Path dir = this.saveDir(mc);
		if (generator instanceof WorldgenSource w && dir != null && w.decorator() instanceof dev.voidmc.lod.source.TreeDecorator trees) {
			// the world's trees kept beside its generated land, worked out once
			trees.keepIn(dir.resolve("trees"), this.generatorIdentity);
		}
		if (generator != null && dir != null && GENERATOR_CACHE) {
			// the generator's answers kept in the world's folder: a horizon seen before is read, not generated
			this.generatorCache = new GeneratorCache(generator, dir.resolve("generated"), new MaterialNames(materials), this.generatorIdentity);
			generator = this.generatorCache;
		}
		if (generator != null && this.serverLevel != null && STRUCTURES) {
			// the world's villages, towers and manors stood on the land nobody has loaded yet
			this.sites = new StructureSites(this.serverLevel);
			this.sites.onKnown(this.engine::invalidate);
			if (mc.player != null) {
				this.sites.centre(mc.player.getX(), mc.player.getZ());
			}
			generator = new StructureOverlay(generator, this.sites);
		}
		if (generator != null && this.serverLevel != null) {
			// VOID Hearth's towns, where it runs: drawn from its blueprints, so no chunk loads for them
			dev.voidmc.lod.source.HearthFootprints footprints = dev.voidmc.lod.source.HearthFootprints.of(this.serverLevel);
			if (footprints != null) {
				this.hearth = footprints;
				this.hearthVersions.clear();
				generator = new dev.voidmc.lod.source.HearthOverlay(generator, footprints);
			}
		}
		if (!this.ingestChunks) {
			return generator;
		}
		ColumnStore store = new ColumnStore();
		store.setListener(this.engine::invalidate);
		this.ingest = new ChunkIngest(mc.level, store, materials);
		if (mc.player != null) {
			// chunks that arrived before this world's LOD was set up
			this.ingest.loadedAround(mc.player.chunkPosition().x(), mc.player.chunkPosition().z(), mc.options.getEffectiveRenderDistance() + 2);
		}
		if (dir != null) {
			this.files = new ColumnFiles(dir, store, new MaterialNames(materials));
		}
		LOGGER.info("VOID LOD: reading the loaded chunks of {} into the far view{}, saved in {}", mc.level.dimension().identifier(),
			generator == null ? " (no generator here: only what has been seen is drawn)" : " over the generator's terrain", dir);
		return this.withTrees(new IngestSource(store, generator));
	}

	/** The world's generator, sampled without building chunks: only where the server is ours (singleplayer, a LAN host). */
	private TerrainSource createGenerator(Minecraft mc, Materials materials) {
		var server = mc.getSingleplayerServer();
		if (server == null) {
			// on a remote server the generator and seed are the server's: it describes its land if it runs VOID LOD
			dev.voidmc.lod.net.LodNet.Hello hello = this.serverHello;
			if (hello != null && hello.version() == dev.voidmc.lod.net.LodNet.VERSION && FabricLoader.getInstance().isModLoaded("expanse")
				&& hello.dimension().equals(mc.level.dimension().identifier().toString())) {
				this.generatorIdentity = remoteIdentity(mc, hello);
				this.remote = new RemoteSource(mc.level, hello, materials, ++this.sourceVersion);
				this.remote.onFacts(this.engine::invalidate);
				LOGGER.info("VOID LOD: the server describes the land past the view of {}", hello.dimension());
				return this.remote;
			}
			return null;
		}
		ServerLevel serverLevel = server.getLevel(mc.level.dimension());
		if (serverLevel == null) {
			return null;
		}
		this.serverLevel = serverLevel;
		long version = ++this.sourceVersion;
		this.generatorIdentity = identity(mc, serverLevel);
		if (FabricLoader.getInstance().isModLoaded("expanse")) {
			WorldgenSource earth = dev.voidmc.lod.source.ExpanseSource.tryCreate(serverLevel, version, materials);
			if (earth != null) {
				LOGGER.info("VOID LOD: sampling Expanse's Earth terrain model for {}", mc.level.dimension().identifier());
				return earth;
			}
		}
		LOGGER.info("VOID LOD: sampling the world generator for {}", mc.level.dimension().identifier());
		return new WorldgenSource(serverLevel, version, materials);
	}

	/**
	 * Where a level's seen land is kept: in the world's own save in singleplayer ({@code void_lod/<dimension>}), so
	 * it travels with the world; under the game directory otherwise, per server address and world (the hashed seed
	 * the server shares, so a server that starts a new world does not show the old one's land).
	 */
	private Path saveDir(Minecraft mc) {
		var dimension = mc.level.dimension().identifier();
		var server = mc.getSingleplayerServer();
		if (server != null) {
			return server.getWorldPath(LevelResource.ROOT).resolve(ID).resolve(dimension.getNamespace()).resolve(dimension.getPath()).normalize();
		}
		ServerData data = mc.getCurrentServer();
		if (data == null || data.ip == null || data.ip.isBlank()) {
			return null;
		}
		String address = data.ip.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
		String world = Long.toHexString(((dev.voidmc.lod.mixin.BiomeManagerAccessor) mc.level.getBiomeManager()).voidLod$biomeZoomSeed());
		return mc.gameDirectory.toPath().resolve(ID).resolve("servers").resolve(address).resolve(world).resolve(dimension.getNamespace())
			.resolve(dimension.getPath());
	}

	/**
	 * Saves the current level's seen land in the background; when the game is closing, waits for every save still
	 * under way (the last world's included: leaving it may have been this very tick).
	 */
	private void closeFiles(boolean wait) {
		ColumnFiles files = this.files;
		this.files = null;
		if (files != null) {
			files.close();
		}
		StructureSites sites = this.sites;
		this.sites = null;
		this.hearth = null;
		this.serverLevel = null;
		if (sites != null) {
			sites.close();
		}
		GeneratorCache cache = this.generatorCache;
		this.generatorCache = null;
		CompletableFuture<Void> cacheSaved = cache == null ? CompletableFuture.completedFuture(null) : cache.close();
		if (wait) {
			try {
				cacheSaved.get(15, TimeUnit.SECONDS);
			} catch (Exception e) {
				LOGGER.warn("VOID LOD: could not finish saving the generator's answers", e);
			}
		}
		if (wait) {
			try {
				ColumnFiles.flush().get(15, TimeUnit.SECONDS);
			} catch (Exception e) {
				LOGGER.warn("VOID LOD: could not finish saving the far view", e);
			}
		}
	}

	/** What the server said it can describe ({@code net.LodNet.Hello}), and the source made of it. */
	private volatile dev.voidmc.lod.net.LodNet.Hello serverHello;
	private RemoteSource remote;

	/** Tiles asked of the server and not answered yet: 0 where it describes nothing. */
	public int remotePending() {
		RemoteSource remote = this.remote;
		return remote == null ? 0 : remote.pending();
	}

	/** The server offers its land (client thread): the source is made again with it, next frame. */
	private void hello(dev.voidmc.lod.net.LodNet.Hello hello) {
		this.serverHello = hello;
		if (Minecraft.getInstance().getSingleplayerServer() == null && this.remote == null) {
			// as if the level had changed: the source is made again, now with the server's facts
			this.level = null;
		}
	}

	/** The identity of a server's described land: its address, the dimension and its strata, the mods, the packs. */
	private static long remoteIdentity(Minecraft mc, dev.voidmc.lod.net.LodNet.Hello hello) {
		StringBuilder b = new StringBuilder("remote|").append(SAMPLING_VERSION);
		var data = mc.getCurrentServer();
		b.append('|').append(data == null ? "" : data.ip).append('|').append(hello.dimension()).append('|').append(hello.seaLevel())
			.append('|').append(String.join(",", hello.strata()));
		for (String mod : new String[]{"minecraft", ID, "expanse"}) {
			FabricLoader.getInstance().getModContainer(mod).ifPresent(c -> b.append('|').append(mod).append(' ')
				.append(c.getMetadata().getVersion().getFriendlyString()));
		}
		b.append('|').append(String.join(",", mc.getResourcePackRepository().getSelectedIds()));
		long h = 1125899906842597L;
		for (int i = 0; i < b.length(); i++) {
			h = 31 * h + b.charAt(i);
		}
		return h;
	}

	/** {@code -Dvoid.lod.generatorCache=false} samples the generator every time, as before the cache. */
	private static final boolean GENERATOR_CACHE = !"false".equals(System.getProperty("void.lod.generatorCache"));
	/** Bumped whenever the sources' answers change for the same generator, seed and mods (the cache's pages then go). */
	private static final int SAMPLING_VERSION = 2;
	private GeneratorCache generatorCache;
	private long generatorIdentity;
	/** {@code -Dvoid.lod.structures=false} leaves the world's structures off the land nobody has loaded. */
	private static final boolean STRUCTURES = !"false".equals(System.getProperty("void.lod.structures"));
	private StructureSites sites;
	/** VOID Hearth's towns, where it runs, and the version of each 1024-block region the tiles were built with. */
	private dev.voidmc.lod.source.HearthOverlay.Footprints hearth;
	private final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap hearthVersions = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
	{
		this.hearthVersions.defaultReturnValue(-1);
	}
	private long hearthNanos;
	/** How far out Hearth's towns are watched for changes: past this a town is a few pixels, rebuilt when its tile is. */
	private static final double HEARTH_REACH = 16384;

	/** The regions around the player whose towns changed since last asked: their tiles are built again. Render thread. */
	private void pollHearth(double px, double pz) {
		dev.voidmc.lod.source.HearthOverlay.Footprints f = this.hearth;
		int reach = (int) Math.ceil(Math.min(this.radius, HEARTH_REACH) / 1024);
		int cx = (int) Math.floor(px) >> 10, cz = (int) Math.floor(pz) >> 10;
		for (int rz = cz - reach; rz <= cz + reach; rz++) {
			for (int rx = cx - reach; rx <= cx + reach; rx++) {
				long key = (long) rx << 32 ^ rz & 0xFFFFFFFFL;
				long v = f.version(rx, rz);
				long was = this.hearthVersions.put(key, v);
				// a region seen for the first time with towns in it may have been built before Hearth's world was up
				if (was != v && (was != -1 || v != 0)) {
					this.engine.invalidate(rx << 10, rz << 10, (rx + 1) << 10, (rz + 1) << 10, 1024 * 1024);
				}
			}
		}
	}
	/** Chunks whose trees are still to be placed in the background (0 without the world's trees). */
	public int treesPending() {
		dev.voidmc.lod.source.TreeDecorator trees = this.trees;
		return trees == null ? 0 : trees.queued();
	}

	/** The world's trees, placed in the background for the close tiles, or null. */
	private dev.voidmc.lod.source.TreeDecorator trees;

	/**
	 * Hands the world's trees the source to stand them on (whole, the seen chunks included) and the engine to tell when a
	 * tile's trees are ready, so its close levels are built again with them.
	 */
	private TerrainSource withTrees(TerrainSource source) {
		this.trees = source.decorator() instanceof dev.voidmc.lod.source.TreeDecorator t ? t : null;
		if (this.trees != null) {
			this.trees.ground((x, z, out) -> source.sample(x, z, 1, out));
			this.trees.onReady((minX, minZ, maxX, maxZ) -> this.engine.refresh(minX, minZ, maxX, maxZ, TileBuilder.treeLevels - 1));
		}
		return source;
	}
	private ServerLevel serverLevel;

	/**
	 * What the generator is, as far as its answers go: the seed, the dimension and its generator, the versions of the
	 * game and of the mods that shape and colour the land, and the resource packs, whose textures colour it.
	 */
	private static long identity(Minecraft mc, ServerLevel level) {
		StringBuilder b = new StringBuilder();
		b.append(SAMPLING_VERSION).append('|').append(level.getSeed()).append('|').append(level.dimension().identifier())
			.append('|').append(level.getChunkSource().getGenerator().getClass().getName());
		for (String mod : new String[]{"minecraft", ID, "expanse"}) {
			FabricLoader.getInstance().getModContainer(mod).ifPresent(c -> b.append('|').append(mod).append(' ')
				.append(c.getMetadata().getVersion().getFriendlyString()));
		}
		b.append('|').append(String.join(",", mc.getResourcePackRepository().getSelectedIds()));
		long h = 1125899906842597L;
		for (int i = 0; i < b.length(); i++) {
			h = 31 * h + b.charAt(i);
		}
		return h;
	}

	/** A block changed in a client level: from the client level mixin. */
	public void blockChanged(ClientLevel level, BlockPos pos) {
		ChunkIngest ingest = this.ingest;
		if (ingest != null && level == this.level) {
			ingest.changed(pos.getX(), pos.getZ());
		}
	}

	public ChunkIngest ingest() {
		return this.ingest;
	}

	public ColumnFiles files() {
		return this.files;
	}

	private LodEngine.Gpu gpu() {
		LodRenderer r = this.renderer;
		return r != null ? r : new LodEngine.Gpu() {
			@Override
			public boolean upload(dev.voidmc.lod.core.Tile tile, java.nio.ByteBuffer vertices) {
				return false;
			}

			@Override
			public void free(dev.voidmc.lod.core.Tile tile) {
			}
		};
	}

	public boolean active() {
		return this.enabled && this.engine != null && this.engine.source() != null;
	}

	public LodEngine engine() {
		return this.engine;
	}

	public LodRenderer renderer() {
		return this.renderer;
	}

	/** Turns the LOD on or off for the session (F8, the game tests); the settings screen saves it as well. */
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	/**
	 * How far vanilla's chunks reach, in blocks: the LOD starts where they stop. Where the chunks dissolve into it
	 * ({@link ChunkFade}), it is drawn whole under the ring they dissolve over, and fades in under the chunks before it.
	 */
	public static double clipRadius(Minecraft mc) {
		int distance = mc.options.getEffectiveRenderDistance();
		if (ChunkFade.on() && ChunkFade.fadesEdge(distance)) {
			return Math.max(0, ChunkFade.edge(distance) - ChunkFade.EDGE - 16);
		}
		return Math.max(0, distance * 16 - 24);
	}

	/** Where the chunks' haze meets the LOD's ({@code mixin.FogRendererMixin}): the middle of the seam. */
	public static double seamRadius(Minecraft mc) {
		int distance = mc.options.getEffectiveRenderDistance();
		if (ChunkFade.on() && ChunkFade.fadesEdge(distance)) {
			return ChunkFade.edge(distance) - ChunkFade.EDGE / 2.0;
		}
		return clipRadius(mc);
	}

	// ---- called from the mixins, on the render thread -------------------------------------------------------

	public void prepare(CameraRenderState camera, float sunAngle) {
		ChunkIngest ingest = this.ingest;
		if (ingest != null) {
			// with the LOD toggled off too: what changes meanwhile should be there when it comes back
			ingest.run(INGEST_NANOS);
		}
		// the chunks dissolve into the LOD where it is drawn behind them with vanilla's own chunk shaders
		ChunkFade.update(this.active() && !Shaders.packDraws() && !Shaders.drawOverPack(), Minecraft.getInstance().options.getEffectiveRenderDistance());
		if (!this.active()) {
			return;
		}
		if (this.renderer == null) {
			this.renderer = new LodRenderer(this.engine);
		}
		// where the sun is (vanilla's sky turns it about the x axis), and how much it lights: none below the horizon
		this.sun[0] = -(float) Math.sin(sunAngle);
		this.sun[1] = (float) Math.cos(sunAngle);
		this.sun[2] = 0;
		this.sun[3] = Math.max(0, Math.min(1, this.sun[1] * 4));
		// a shader pack that draws the LOD itself needs its tiles in Distant Horizons' layout: built again when that changes
		boolean pack = Shaders.packDraws();
		if (pack != TileBuilder.packFormat) {
			TileBuilder.packFormat = pack;
			this.engine.reset(this.engine.source(), this.gpu());
		}
		this.renderer.setPackMode(pack);
		if (pack) {
			// a pack's tiles have the seasons baked in
			dev.voidmc.lod.api.LodApi.bakeSeasons();
		}
		this.renderer.prepare(camera, this.radius, clipRadius(Minecraft.getInstance()), 16, this.detail, this.farDetail, this.haze, this.sun, this.sky);
		LodRenderer r = this.renderer;
		if (this.settings.autoTune && r.lastTiles > 0 && !this.fogHidesLod
			&& this.tuner.sample(System.nanoTime(), this.tunedGpuMillis(r), r.lastPrepareNanos / 1e6, this.engine.queued() > 0, this.capMillis)) {
			this.apply();
			LOGGER.info(String.format(Locale.ROOT, "VOID LOD auto-tune: step %d, detail %.1f px (%.1f far), radius %d (GPU %.2f ms, prepare %.2f ms)",
				this.tuner.step(), this.detail, this.farDetail, (int) this.radius, this.tuner.gpu, this.tuner.prepareMillis));
		}
	}

	public void draw(RenderPass pass) {
		if (this.active() && this.renderer != null && !this.fogHidesLod) {
			this.renderer.draw(pass);
		}
	}

	/** Draws over the shader pack's finished picture in the main target (see {@link LodRenderer#drawOver}). */
	/**
	 * Has the shader pack draw the prepared frame through its Distant Horizons programs, or only clear the LOD's depth
	 * when there is nothing to draw (off, or hidden by fog), so the pack sees no far terrain.
	 */
	public void drawPack() {
		LodRenderer r = this.renderer;
		Shaders.drawPack(this.active() && r != null && !this.fogHidesLod ? r.packFrame : null);
		if (r != null) {
			// the pack's programs draw the tiles: their time is the LOD's GPU cost, for F3 and auto-tune
			r.gpuMillis = Shaders.packGpuMillis();
		}
	}

	/**
	 * The GPU time auto-tune holds to its target. Through a shader pack's programs the same tiles cost about twice what
	 * they do through ours (its lighting is the user's choice, as it is on the chunks), so half of it counts: the pack
	 * gets the detail the LOD would have without it.
	 */
	private double tunedGpuMillis(LodRenderer r) {
		return r.packMode() && r.gpuMillis > 0 ? r.gpuMillis * 0.5 : r.gpuMillis;
	}

	/** This frame's tiles for the shader pack, or null when there is nothing for it to draw. */
	public dev.voidmc.lod.render.PackFrame packFrame() {
		LodRenderer r = this.renderer;
		return this.active() && r != null && !this.fogHidesLod ? r.packFrame : null;
	}

	public void drawOverPack() {
		if (this.active() && this.renderer != null && !this.fogHidesLod) {
			this.renderer.drawOver(Minecraft.getInstance().gameRenderer.mainRenderTarget());
		}
	}

	/**
	 * Sets this frame's aerial haze: {@code reach} blocks at height {@code base} (0 for none, and vanilla's own
	 * fog instead), and how hard it rains (0..1), which hangs a curtain of {@link #RAIN_REACH} over it.
	 */
	/**
	 * Sets what vanilla's open-air fog colour is made of this frame (see {@code lodinfo.glsl}): the base fog colour, the
	 * sun's side, the sunrise colour and strength, the sky colour and its share. A strength of 0 leaves the haze vanilla's
	 * fog colour in every direction.
	 */
	public void setSky(float r, float g, float b, float sunX, float sr, float sg, float sb, float strength, float kr, float kg, float kb,
		float skyShare) {
		float[] s = this.sky;
		s[0] = r;
		s[1] = g;
		s[2] = b;
		s[3] = sunX;
		s[4] = sr;
		s[5] = sg;
		s[6] = sb;
		s[7] = strength;
		s[8] = kr;
		s[9] = kg;
		s[10] = kb;
		s[11] = skyShare;
	}

	public void setHaze(float reach, float base, float rain, boolean hidden) {
		this.haze[0] = reach > 0 ? 1.0F / reach : 0;
		this.haze[1] = 1.0F / HAZE_SCALE_HEIGHT;
		this.haze[2] = base;
		this.haze[3] = rain;
		this.fogHidesLod = hidden;
	}

	public String stats() {
		LodEngine e = this.engine;
		LodRenderer r = this.renderer;
		long built = e.tilesBuilt.get();
		long samples = TileBuilder.SAMPLES.get();
		StringBuilder out = new StringBuilder(String.format(Locale.ROOT,
			"LOD %s: %d cached, %d queued, %d built (%.2f ms avg, %.2f sampling, %.0f%% of samples reused), GPU %.1f MB",
			this.active() ? "on" : "off", e.tileCount(), e.queued(), built, built == 0 ? 0 : e.buildNanos.get() / 1e6 / built,
			built == 0 ? 0 : TileBuilder.SAMPLE_NANOS.get() / 1e6 / built, samples == 0 ? 0 : 100.0 * TileBuilder.SAMPLES_REUSED.get() / samples,
			e.gpuBytes() / 1048576.0));
		if (r != null) {
			out.append(String.format(Locale.ROOT,
				" | %d tiles drawn (%d more behind the horizon), %,d quads, prepare %.2f ms (engine %.2f, staging %.2f, buffers %.2f), GPU draw %.2f ms",
				r.lastTiles, r.lastOccluded, r.lastQuads, r.lastPrepareNanos / 1e6, r.lastEngineNanos / 1e6, r.lastStagingNanos / 1e6,
				r.lastBufferNanos / 1e6, r.gpuMillis));
		}
		if (this.tuner.step() > 0) {
			out.append(String.format(Locale.ROOT, " | auto-tune step %d: detail %.1f px, radius %d", this.tuner.step(), this.detail, (int) this.radius));
		}
		if (WorldgenSource.TIMING) {
			out.append(String.format(Locale.ROOT, " | source: model %.1f s, biomes %.1f s, total %.1f s",
				WorldgenSource.TIMES.get(0) / 1e9, WorldgenSource.TIMES.get(1) / 1e9, WorldgenSource.TIMES.get(2) / 1e9));
		}
		TerrainSource source = e.source();
		if (source instanceof IngestSource seen) {
			source = seen.fallback();
		}
		if (source instanceof StructureOverlay overlay) {
			out.append(" | ").append(overlay.sites().stats());
			source = overlay.generator();
		}
		if (source instanceof GeneratorCache cache) {
			out.append(" | ").append(cache.stats());
			source = cache.generator();
		}
		if (source instanceof RemoteSource remoteSource) {
			out.append(" | ").append(remoteSource.stats());
		}
		if (source instanceof WorldgenSource w) {
			out.append(w.diagnostics());
			if (w.trees() != null) {
				out.append(" | ").append(w.trees().stats()).append(String.format(Locale.ROOT, ", %.1f s standing them",
					dev.voidmc.lod.core.TileBuilder.TREE_NANOS.get() / 1e9));
			}
		}
		ChunkIngest ingest = this.ingest;
		if (ingest != null) {
			out.append(String.format(Locale.ROOT, " | ingest: %d chunks (%d unchanged, %.3f ms avg, %.2f max), %d queued, %d known, %d surfaces",
				ingest.chunks, ingest.unchanged, ingest.chunks == 0 ? 0 : ingest.nanos / 1e6 / ingest.chunks, ingest.maxNanos / 1e6,
				ingest.queued(), ingest.store().chunkCount(), ingest.store().surfaceCount()));
			out.append(String.format(Locale.ROOT, ", %d rebuilt, %d empty", e.rebuilt, e.tilesEmpty.get()));
		}
		ColumnFiles files = this.files;
		if (files != null) {
			out.append(String.format(Locale.ROOT, " | files: read %d regions (%.1f KB, %.0f ms), wrote %d (%d chunks, %.1f KB, %.0f ms)",
				files.regionsRead.get(), files.bytesRead.get() / 1024.0, files.readNanos.get() / 1e6, files.regionsWritten.get(),
				files.chunksWritten.get(), files.bytesWritten.get() / 1024.0, files.writeNanos.get() / 1e6));
		}
		return out.toString();
	}
}
