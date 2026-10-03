package dev.voidmc.lod;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.renderpearl.api.commands.RenderPass;
import dev.voidmc.lod.core.LodEngine;
import dev.voidmc.lod.core.TerrainSource;
import dev.voidmc.lod.core.TileBuilder;
import dev.voidmc.lod.render.LodRenderer;
import dev.voidmc.lod.source.WorldgenSource;
import java.util.Locale;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * VOID LOD: the land past the render distance, out to the horizon.
 *
 * <p>The terrain comes from the world's own generator, sampled one column per LOD cell without building
 * a chunk (and from Expanse's terrain model directly, when the world is an Earth world), so the view is
 * complete the moment a world loads, not once someone has walked it. Settings are system properties for
 * now: {@code void.lod.radius} (blocks, default 8192), {@code void.lod.detail} (the most pixels a cell may
 * cover before its tile splits, default 5), {@code void.lod.haze} (how far the air carries at sea level,
 * default half the radius), {@code void.lod.smoothFrom} (the first level drawn smooth, default 2),
 * {@code void.lod.threads}, {@code void.lod.budget} (MB of GPU memory, default 512) and
 * {@code void.lod.enabled}. F8 toggles it.
 */
public final class VoidLod implements ClientModInitializer {
	public static final String ID = "void_lod";
	public static final Logger LOGGER = LoggerFactory.getLogger("VOID LOD");

	private static VoidLod instance;

	public final double radius = Double.parseDouble(System.getProperty("void.lod.radius", "8192"));
	public final double detail = Double.parseDouble(System.getProperty("void.lod.detail", "5"));
	/** The same budget for the smooth far field, which is cheap enough to keep finer. */
	public final double farDetail = Double.parseDouble(System.getProperty("void.lod.farDetail", "3"));
	/** How far the air carries at sea level before the haze is two thirds of the view: by default, 3/4 of the radius. */
	public final double hazeReach = Double.parseDouble(System.getProperty("void.lod.haze", String.valueOf(this.radius * 0.75)));
	/** How fast the haze thins with height: it is a third as thick this many blocks above sea level. */
	public static final float HAZE_SCALE_HEIGHT = 160;
	public final int threads = Integer.getInteger("void.lod.threads",
		Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2)));
	private boolean enabled = Boolean.parseBoolean(System.getProperty("void.lod.enabled", "true"));
	private LodEngine engine;
	private LodRenderer renderer;
	private ClientLevel level;
	private long sourceVersion;
	private KeyMapping toggle;
	private int resets;
	private int ticks;
	private final boolean logStats = Boolean.getBoolean("void.lod.logStats");
	/** This frame's haze for the shader, from the fog mixin: 1 / reach (0 for vanilla's fog), 1 / scale height, base y, rain. */
	private final float[] haze = new float[4];
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
		this.engine = new LodEngine(this.threads, Long.getLong("void.lod.budget", 512L) << 20);
		this.toggle = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.void_lod.toggle", InputConstants.KEY_F8,
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "lod"))));
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		LOGGER.info("VOID LOD: radius {} blocks, detail {} px per cell ({} far), {} worker threads", (int) this.radius, this.detail, this.farDetail,
			this.threads);
	}

	private void tick(Minecraft mc) {
		while (this.toggle.consumeClick()) {
			this.enabled = !this.enabled;
			if (mc.player != null) {
				mc.player.sendOverlayMessage(Component.literal("VOID LOD " + (this.enabled ? "on" : "off")));
			}
		}
		if (mc.level != this.level) {
			this.level = mc.level;
			this.resets++;
			this.engine.reset(this.createSource(mc), this.gpu());
		}
		if (this.logStats && ++this.ticks % 100 == 0) {
			LOGGER.info("{} | resets {}, dropped {}, frame {}", this.stats(), this.resets, this.engine.jobsDropped.get(), this.engine.frame());
		}
	}

	/** The terrain source for the level the player is in, or null where there is none to be had. */
	private TerrainSource createSource(Minecraft mc) {
		if (mc.level == null || mc.level.dimensionType().hasCeiling() || mc.level.dimension() == Level.END) {
			// a ceiling hides the distance; the End is islands over the void, which a heightfield cannot draw
			return null;
		}
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
			WorldgenSource earth = dev.voidmc.lod.source.ExpanseSource.tryCreate(serverLevel, version);
			if (earth != null) {
				LOGGER.info("VOID LOD: sampling Expanse's Earth terrain model for {}", mc.level.dimension().identifier());
				return earth;
			}
		}
		LOGGER.info("VOID LOD: sampling the world generator for {}", mc.level.dimension().identifier());
		return new WorldgenSource(serverLevel, version);
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

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	/** How far vanilla's chunks reach, in blocks: the LOD starts where they stop. */
	public static double clipRadius(Minecraft mc) {
		return Math.max(0, mc.options.getEffectiveRenderDistance() * 16 - 24);
	}

	// ---- called from the mixins, on the render thread -------------------------------------------------------

	public void prepare(CameraRenderState camera, float sunAngle) {
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
		this.renderer.prepare(camera, this.radius, clipRadius(Minecraft.getInstance()), 16, this.detail, this.farDetail, this.haze, this.sun);
	}

	public void draw(RenderPass pass) {
		if (this.active() && this.renderer != null && !this.fogHidesLod) {
			this.renderer.draw(pass);
		}
	}

	/**
	 * Sets this frame's aerial haze: {@code reach} blocks at height {@code base} (0 for none, and vanilla's own
	 * fog instead), plus {@code offset} blocks of haze before the first (rain's).
	 */
	public void setHaze(float reach, float base, float offset, boolean hidden) {
		this.haze[0] = reach > 0 ? 1.0F / reach : 0;
		this.haze[1] = 1.0F / HAZE_SCALE_HEIGHT;
		this.haze[2] = base;
		this.haze[3] = offset;
		this.fogHidesLod = hidden;
	}

	public String stats() {
		LodEngine e = this.engine;
		LodRenderer r = this.renderer;
		long built = e.tilesBuilt.get();
		StringBuilder out = new StringBuilder(String.format(Locale.ROOT,
			"LOD %s: %d cached, %d queued, %d built (%.2f ms avg, %.2f sampling), GPU %.1f MB",
			this.active() ? "on" : "off", e.tileCount(), e.queued(), built, built == 0 ? 0 : e.buildNanos.get() / 1e6 / built,
			built == 0 ? 0 : TileBuilder.SAMPLE_NANOS.get() / 1e6 / built, e.gpuBytes() / 1048576.0));
		if (r != null) {
			out.append(String.format(Locale.ROOT,
				" | %d tiles drawn, %,d quads, prepare %.2f ms (engine %.2f, staging %.2f, buffers %.2f), GPU draw %.2f ms",
				r.lastTiles, r.lastQuads, r.lastPrepareNanos / 1e6, r.lastEngineNanos / 1e6, r.lastStagingNanos / 1e6,
				r.lastBufferNanos / 1e6, r.gpuMillis));
		}
		if (WorldgenSource.TIMING) {
			out.append(String.format(Locale.ROOT, " | source: model %.1f s, biomes %.1f s, total %.1f s",
				WorldgenSource.TIMES.get(0) / 1e9, WorldgenSource.TIMES.get(1) / 1e9, WorldgenSource.TIMES.get(2) / 1e9));
		}
		if (e.source() instanceof WorldgenSource w) {
			out.append(w.diagnostics());
		}
		return out.toString();
	}
}
