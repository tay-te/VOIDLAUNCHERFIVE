package dev.voidmc.lod;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.renderpearl.api.commands.RenderPass;
import dev.voidmc.lod.core.LodEngine;
import dev.voidmc.lod.core.TerrainSource;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * VOID LOD: the land past the render distance, out to the horizon.
 *
 * <p>The terrain comes from the world's own generator, sampled one column per LOD cell without building
 * a chunk (and from Expanse's terrain model directly, when the world is an Earth world), so the view is
 * complete the moment a world loads, not once someone has walked it. Settings are system properties for
 * now: {@code void.lod.radius} (blocks, default 8192), {@code void.lod.detail} (the most pixels a cell may
 * cover before its tile splits, default 5), {@code void.lod.threads}, {@code void.lod.budget} (MB of GPU
 * memory, default 512) and {@code void.lod.enabled}. F8 toggles it.
 */
public final class VoidLod implements ClientModInitializer {
	public static final String ID = "void_lod";
	public static final Logger LOGGER = LoggerFactory.getLogger("VOID LOD");

	private static VoidLod instance;

	public final double radius = Double.parseDouble(System.getProperty("void.lod.radius", "8192"));
	public final double detail = Double.parseDouble(System.getProperty("void.lod.detail", "5"));
	public final int threads = Integer.getInteger("void.lod.threads",
		Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2)));
	private boolean enabled = Boolean.parseBoolean(System.getProperty("void.lod.enabled", "true"));
	private LodEngine engine;
	private LodRenderer renderer;
	private ClientLevel level;
	private long sourceVersion;
	private KeyMapping toggle;

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
		LOGGER.info("VOID LOD: radius {} blocks, detail {} px per cell, {} worker threads", (int) this.radius, this.detail, this.threads);
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
			this.engine.reset(this.createSource(mc), this.gpu());
		}
	}

	/** The terrain source for the level the player is in, or null where there is none to be had. */
	private TerrainSource createSource(Minecraft mc) {
		if (mc.level == null || mc.level.dimensionType().hasCeiling()) {
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

	public void prepare(CameraRenderState camera) {
		if (!this.active()) {
			return;
		}
		if (this.renderer == null) {
			this.renderer = new LodRenderer(this.engine);
		}
		this.renderer.prepare(camera, this.radius, clipRadius(Minecraft.getInstance()), 16, this.detail);
	}

	public void draw(RenderPass pass) {
		if (this.active() && this.renderer != null) {
			this.renderer.draw(pass);
		}
	}

	public String stats() {
		LodEngine e = this.engine;
		LodRenderer r = this.renderer;
		long built = e.tilesBuilt.get();
		return String.format(Locale.ROOT, "LOD %s: %d tiles drawn, %,d quads, %d cached, %d queued, %d built (%.2f ms avg), GPU %.1f MB, prepare %.2f ms",
			this.active() ? "on" : "off", r == null ? 0 : r.lastTiles, r == null ? 0 : r.lastQuads, e.tileCount(), e.queued(), built,
			built == 0 ? 0 : e.buildNanos.get() / 1e6 / built, e.gpuBytes() / 1048576.0, r == null ? 0 : r.lastPrepareNanos / 1e6);
	}
}
