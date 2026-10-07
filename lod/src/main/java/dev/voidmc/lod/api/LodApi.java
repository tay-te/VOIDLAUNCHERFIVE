package dev.voidmc.lod.api;

import dev.voidmc.lod.VoidLod;
import dev.voidmc.lod.core.BiomeIds;
import dev.voidmc.lod.core.TileBuilder;
import dev.voidmc.lod.core.TileTerrain;
import dev.voidmc.lod.source.Materials;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.LoggerFactory;

/**
 * VOID LOD for other mods, client side, read only: the far terrain it draws for this player, tile by tile
 * ({@link VisibleTerrain}). Nothing is kept for it until something listens, and nothing is handed out that the LOD did
 * not draw. Safe to call whether or not VOID LOD is drawing; reference this class only when the {@code void_lod} mod
 * is loaded.
 */
public final class LodApi {
	private static final List<VisibleTerrainListener> LISTENERS = new CopyOnWriteArrayList<>();
	private static final ExecutorService OUT = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "VOID LOD API");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});

	private LodApi() {
	}

	/**
	 * The far terrain's depth as a shader pack drew it, for mods that draw over the pack's finished picture (far air,
	 * clouds): {@code glTexture} is a GL depth texture, {@code width} x {@code height}, holding the LOD's depth in the
	 * perspective of {@code projection} (the game's field of view with the LOD's own near and far planes, not the main
	 * one), seen through {@code modelView}; {@code reversed}: the nearer surface is the greater depth, 0 where there is no
	 * far terrain. Valid during the frame it was handed out in; the texture is the LOD's, not to be kept or written.
	 */
	public record FarDepth(int glTexture, org.joml.Matrix4f projection, org.joml.Matrix4f modelView, boolean reversed, int width, int height) {
	}

	private static final List<java.util.function.Consumer<FarDepth>> PACK_FRAME = new CopyOnWriteArrayList<>();
	private static volatile FarDepth farDepth;

	/**
	 * Calls {@code listener} on the render thread right after a shader pack has finished each frame in which it drew the
	 * far terrain (its composite passes done), with that frame's far depth. Closing the handle stops it.
	 */
	public static AutoCloseable onPackFrame(java.util.function.Consumer<FarDepth> listener) {
		PACK_FRAME.add(listener);
		return () -> PACK_FRAME.remove(listener);
	}

	/** This frame's far depth, once the pack has finished it; null before then, or when no pack drew the far terrain. */
	public static FarDepth farDepth() {
		return farDepth;
	}

	/** A new frame begins: the last one's depth is gone. Render thread. */
	public static void packFrameStarted() {
		farDepth = null;
	}

	/** The pack has finished a frame with the far terrain in it. Render thread. */
	public static void packFrameFinished(FarDepth depth) {
		farDepth = depth;
		for (java.util.function.Consumer<FarDepth> listener : PACK_FRAME) {
			try {
				listener.accept(depth);
			} catch (RuntimeException e) {
				LoggerFactory.getLogger("VOID LOD").warn("VOID LOD: a pack frame listener failed", e);
			}
		}
	}

	/**
	 * Calls {@code listener} for every far tile VOID LOD draws for this player: the tiles drawn now (those built since
	 * anything first listened), then each tile as it is first drawn, and again whenever it is rebuilt. Called on VOID
	 * LOD's own thread, one tile at a time. Closing the returned handle stops it. Listen early (at client start) to be
	 * told of every tile; a tile built before anything listened is told of once it is next rebuilt.
	 */
	public static AutoCloseable addVisibleTerrainListener(VisibleTerrainListener listener) {
		LISTENERS.add(listener);
		TileBuilder.captureTerrain = true;
		VoidLod lod = VoidLod.get();
		if (lod != null) {
			lod.engine().resendTerrain();
		}
		return () -> {
			LISTENERS.remove(listener);
			if (LISTENERS.isEmpty()) {
				TileBuilder.captureTerrain = false;
			}
		};
	}

	/**
	 * Whether the far terrain around the player is complete for now: every tile the view wants is built and drawn (and
	 * handed to the listeners), with nothing left in the queue, the world's trees included. Turns false again as the
	 * player moves on. A map can treat the areas no tile covered by then as beyond the LOD's reach.
	 */
	public static boolean complete() {
		VoidLod lod = VoidLod.get();
		if (lod == null || !lod.isEnabled()) {
			return false;
		}
		var engine = lod.engine();
		return engine.source() != null && engine.tileCount() > 0 && engine.idle() && engine.queued() == 0 && lod.treesPending() == 0;
	}

	/** Whether VOID LOD is loaded and drawing in this level (false: no tiles will come). */
	public static boolean active() {
		VoidLod lod = VoidLod.get();
		return lod != null && lod.isEnabled() && lod.engine().source() != null;
	}

	private static volatile float hazeThickness = 1.0F;
	private static volatile Shade shade;

	/**
	 * Light laid over the far land from above: a picture whose red channel, times two, scales the sunlight on the land
	 * under each texel (0.5 leaves it as it is, less shades it, more brightens it), {@code size} blocks square with its
	 * corner at world ({@code originX}, {@code originZ}), applied with {@code strength} 0..1. It comes in past the chunks
	 * (which do not have it) and goes with the sun.
	 */
	public record Shade(com.mojang.renderpearl.api.textures.GpuTextureView texture, double originX, double originZ, double size, float strength) {
	}

	/**
	 * Lays {@code shade} over the far land from the next frame on (a mod drawing cloud shadows), or takes it off with
	 * null. Render thread; the texture must stay open until it is replaced or taken off.
	 */
	public static void setShade(Shade shade) {
		LodApi.shade = shade;
	}

	/** The light laid over the far land ({@link #setShade}), or null. */
	public static Shade shade() {
		return shade;
	}

	/**
	 * Makes VOID LOD's aerial haze {@code thickness} times as thick as it would have it (1: its own), for a mod that keeps
	 * the weather: hazier over a humid lowland in summer, clearer in the washed air after rain. Held until set again;
	 * clamped to 0.25..4.
	 */
	public static void setHazeThickness(float thickness) {
		hazeThickness = Float.isFinite(thickness) ? Math.clamp(thickness, 0.25F, 4.0F) : 1.0F;
	}

	/** How many times as thick as its own the haze is ({@link #setHazeThickness}). */
	public static float hazeThickness() {
		return hazeThickness;
	}

	/** How far VOID LOD draws the far land, in blocks from the camera (0 while it is not drawing). */
	public static double radius() {
		VoidLod lod = VoidLod.get();
		return lod != null && active() ? lod.radius : 0;
	}

	/** Hands {@code tiles}, just drawn in {@code dimension}, to the listeners. Render thread; the work is done off it. */
	public static void publish(ResourceKey<Level> dimension, List<TileTerrain> tiles, Materials materials) {
		if (LISTENERS.isEmpty() || tiles.isEmpty()) {
			return;
		}
		OUT.execute(() -> {
			for (TileTerrain t : tiles) {
				VisibleTerrain tile = convert(dimension, t, materials);
				for (VisibleTerrainListener listener : LISTENERS) {
					try {
						listener.onTerrain(tile);
					} catch (RuntimeException e) {
						LoggerFactory.getLogger("VOID LOD").warn("VOID LOD: a terrain listener failed", e);
					}
				}
			}
		});
	}

	@SuppressWarnings("unchecked")
	private static VisibleTerrain convert(ResourceKey<Level> dimension, TileTerrain t, Materials materials) {
		int n = t.size * t.size;
		Map<BlockState, Integer> blockIndex = new HashMap<>();
		List<BlockState> blockPalette = new ArrayList<>();
		Map<Integer, Integer> biomeIndex = new HashMap<>();
		List<ResourceKey<Biome>> biomePalette = new ArrayList<>();
		byte[] blocks = new byte[n];
		byte[] biomes = new byte[n];
		for (int c = 0; c < n; c++) {
			Materials.Face face = materials == null ? null : materials.face(t.materials[c]);
			if (face == null) {
				blocks[c] = -1;
			} else {
				Integer at = blockIndex.get(face.state());
				if (at == null && blockPalette.size() < 127) {
					at = blockPalette.size();
					blockPalette.add(face.state());
					blockIndex.put(face.state(), at);
				}
				blocks[c] = at == null ? -1 : at.byteValue();
			}
			String biome = BiomeIds.name(t.biomes[c]);
			if (biome == null) {
				biomes[c] = -1;
			} else {
				Integer at = biomeIndex.get(t.biomes[c]);
				if (at == null && biomePalette.size() < 127) {
					at = biomePalette.size();
					biomePalette.add(ResourceKey.create(Registries.BIOME, Identifier.parse(biome)));
					biomeIndex.put(t.biomes[c], at);
				}
				biomes[c] = at == null ? -1 : at.byteValue();
			}
		}
		return new VisibleTerrain(dimension, t.revision, t.minX, t.minZ, t.cell, t.size, t.heights.clone(), t.colors.clone(),
			blockPalette.toArray(BlockState[]::new), blocks, biomePalette.toArray(ResourceKey[]::new), biomes, t.seen.clone());
	}
}
