package dev.voidmc.lod;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.VideoMode;
import com.mojang.renderpearl.api.commands.RenderPass;
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
		TileBuilder.smoothFrom = this.settings.smoothFrom;
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
			this.resets++;
			this.closeFiles(false);
			this.ingest = null;
			this.waiting = null;
			this.loading = null;
			this.engine.reset(null, this.gpu());
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
		TerrainSource generator = this.createGenerator(mc, materials);
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
		Path dir = this.saveDir(mc);
		if (dir != null) {
			this.files = new ColumnFiles(dir, store, new MaterialNames(materials));
		}
		LOGGER.info("VOID LOD: reading the loaded chunks of {} into the far view{}, saved in {}", mc.level.dimension().identifier(),
			generator == null ? " (no generator here: only what has been seen is drawn)" : " over the generator's terrain", dir);
		return new IngestSource(store, generator);
	}

	/** The world's generator, sampled without building chunks: only where the server is ours (singleplayer, a LAN host). */
	private TerrainSource createGenerator(Minecraft mc, Materials materials) {
		var server = mc.getSingleplayerServer();
		if (server == null) {
			// on a remote server the generator and seed are the server's: nothing to sample
			return null;
		}
		ServerLevel serverLevel = server.getLevel(mc.level.dimension());
		if (serverLevel == null) {
			return null;
		}
		long version = ++this.sourceVersion;
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
		if (wait) {
			try {
				ColumnFiles.flush().get(15, TimeUnit.SECONDS);
			} catch (Exception e) {
				LOGGER.warn("VOID LOD: could not finish saving the far view", e);
			}
		}
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

	/** How far vanilla's chunks reach, in blocks: the LOD starts where they stop. */
	public static double clipRadius(Minecraft mc) {
		return Math.max(0, mc.options.getEffectiveRenderDistance() * 16 - 24);
	}

	// ---- called from the mixins, on the render thread -------------------------------------------------------

	public void prepare(CameraRenderState camera, float sunAngle) {
		ChunkIngest ingest = this.ingest;
		if (ingest != null) {
			// with the LOD toggled off too: what changes meanwhile should be there when it comes back
			ingest.run(INGEST_NANOS);
		}
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
		this.renderer.prepare(camera, this.radius, clipRadius(Minecraft.getInstance()), 16, this.detail, this.farDetail, this.haze, this.sun, this.sky);
		LodRenderer r = this.renderer;
		if (this.settings.autoTune && r.lastTiles > 0 && !this.fogHidesLod
			&& this.tuner.sample(System.nanoTime(), r.gpuMillis, r.lastPrepareNanos / 1e6, this.engine.queued() > 0, this.capMillis)) {
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
		if (source instanceof WorldgenSource w) {
			out.append(w.diagnostics());
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
