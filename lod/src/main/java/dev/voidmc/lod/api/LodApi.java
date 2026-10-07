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
	 * LOD's own thread, one tile at a time. Closing the returned handle stops it. A tile drawn but built before anything
	 * listened is built again to be told of ({@link #complete} waits for it).
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
		return engine.source() != null && engine.tileCount() > 0 && engine.idle() && engine.queued() == 0 && engine.terrainMissing() == 0
			&& lod.treesPending() == 0;
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

	/**
	 * The far land through the seasons, for a mod that turns the near chunks through them: each grass and leaf face of
	 * the far land is given a season class as its tile is built ({@link #classify}), and the colours of the classes are a
	 * small table set at any time ({@link #setSeasonTints}) that the shader applies as it draws: a season step, or a
	 * smooth blend between seasons frame by frame, costs one upload and no rebuilding. (Through a shader pack, whose
	 * vertex layout has no room for it, the table is baked into the tiles instead, and they are rebuilt when it has
	 * moved on far enough.)
	 */
	public interface Seasons {
		/** The top face of the ground (grass, or whatever the cell's surface is). */
		int GROUND_TOP = 0;
		/** A side face of the ground's top block (the grass block's side). */
		int GROUND_SIDE = 1;
		/** Leaves: a cell's crowns, or one leaf block of a tree stood on a near tile. */
		int LEAVES = 2;
		/** Classes there are, 0 included (0: the colour never changes). */
		int CLASSES = 32;
		/** Variants per class: each crown takes one by where it stands ({@link LodApi#seasonVariant}). */
		int VARIANTS = 4;

		/**
		 * The season class of a {@code kind} face: 0 for one whose colour never changes (stone, sand, water, a desert),
		 * else 1..31, the mod's own classes ("temperate broadleaf leaves", "steppe grass"...). Called on VOID LOD's
		 * workers as tiles are built, so it must be quick and thread-safe; answers are remembered per kind, biome,
		 * material and conifer share (in eighths), so it is asked rarely.
		 *
		 * @param biome    the cell's biome, a {@link LodApi#biomeName} id (-1 unknown)
		 * @param material what the face is made of, a {@link LodApi#material} id (0 unknown)
		 * @param conifer  the share of the cell's trees that are needle trees, 0..1, or -1 for one leaf block (go by its
		 *                 material)
		 */
		int classify(int kind, int biome, int material, float conifer);
	}

	private static volatile Seasons seasons;
	/** The season table: per class and variant, (r, g, b, amount, brightness); null: as vanilla tints it. */
	private static volatile float[] seasonTints;
	private static volatile int seasonVersion;

	/**
	 * Sets what decides the far land's season classes (null: none, every face as vanilla tints it). The tiles are built
	 * again once, in the background, to carry their classes. Render thread.
	 */
	public static void setSeasons(Seasons seasons) {
		if (LodApi.seasons == seasons) {
			return;
		}
		LodApi.seasons = seasons;
		rebuildAround();
	}

	/** What decides the far land's season classes, or null. */
	public static Seasons seasons() {
		return seasons;
	}

	/**
	 * Sets the season table: {@link Seasons#CLASSES} x {@link Seasons#VARIANTS} entries (class-major, variant-minor), each
	 * five floats (r, g, b, amount, brightness), class 0 ignored. A classed face's colour becomes
	 * {@code mix(rgb, target * lum(rgb) / lum(target), amount) * brightness} (lum: Rec. 709 luminance): toward the
	 * target, keeping its own light and shade, then brightened or darkened (bare winter crowns, straw). Null: as vanilla
	 * tints it. Any thread; cheap enough to call every frame.
	 */
	public static void setSeasonTints(float[] table) {
		if (table != null && table.length < Seasons.CLASSES * Seasons.VARIANTS * 5) {
			throw new IllegalArgumentException("a season table has " + Seasons.CLASSES * Seasons.VARIANTS * 5 + " floats");
		}
		seasonTints = table == null ? null : table.clone();
		seasonVersion++;
	}

	/** The season table as last set (not to be changed), or null. */
	public static float[] seasonTints() {
		return seasonTints;
	}

	/** Bumped with every {@link #setSeasonTints}. */
	public static int seasonVersion() {
		return seasonVersion;
	}

	/**
	 * Which of a class's {@link Seasons#VARIANTS} the crowns standing at block (x, z) take: a hash of the 12-block square
	 * they stand in. The same for the near chunks, if the mod uses it there too, so near and far crowns agree tree for tree.
	 */
	public static int seasonVariant(int x, int z) {
		int h = Math.floorDiv(x, 12) * 0x1B873593 ^ Math.floorDiv(z, 12) * 0x5BD1E995;
		h ^= h >>> 15;
		h *= 0x2C1B3C6D;
		h ^= h >>> 12;
		return h & 3;
	}

	/**
	 * Colour {@code rgb} (0xRRGGBB) as the season table has it for a face of season code {@code code} (class in the low five
	 * bits, variant in the two above), for the tiles that bake it (through a shader pack). The shader does the same.
	 */
	public static int seasonColour(int rgb, int code, float[] table) {
		int cls = code & 31;
		if (cls == 0 || table == null) {
			return rgb;
		}
		int e = (cls * Seasons.VARIANTS + (code >> 5 & 3)) * 5;
		float r = (rgb >> 16 & 255) / 255.0F, g = (rgb >> 8 & 255) / 255.0F, b = (rgb & 255) / 255.0F;
		float tr = table[e], tg = table[e + 1], tb = table[e + 2], amount = table[e + 3], bright = table[e + 4];
		float lum = 0.2126F * r + 0.7152F * g + 0.0722F * b;
		float k = lum / Math.max(0.2126F * tr + 0.7152F * tg + 0.0722F * tb, 1e-3F);
		r = (r + (tr * k - r) * amount) * bright;
		g = (g + (tg * k - g) * amount) * bright;
		b = (b + (tb * k - b) * amount) * bright;
		return Math.round(Math.clamp(r, 0, 1) * 255) << 16 | Math.round(Math.clamp(g, 0, 1) * 255) << 8 | Math.round(Math.clamp(b, 0, 1) * 255);
	}

	/** The table the tiles built through a shader pack have baked in, and when it was baked. */
	private static float[] bakedTints;
	private static long bakedNanos;
	/** How far the table must move on (any float) before tiles that bake it are built again, and how often at most. */
	private static final float BAKE_STEP = 0.04F;
	private static final long BAKE_NANOS = 20_000_000_000L;

	/**
	 * Each frame while a shader pack draws the far land (render thread): its tiles bake the season table in, so once the
	 * table has moved on far enough they are built again, at most every {@value #BAKE_NANOS} ns.
	 */
	public static void bakeSeasons() {
		float[] table = seasonTints;
		if (seasons == null || table == bakedTints) {
			return;
		}
		long now = System.nanoTime();
		if (now - bakedNanos < BAKE_NANOS) {
			return;
		}
		boolean moved = bakedTints == null || table == null;
		for (int i = 0; !moved && i < table.length; i++) {
			moved = Math.abs(table[i] - bakedTints[i]) > BAKE_STEP;
		}
		if (moved) {
			bakedTints = table;
			bakedNanos = now;
			rebuildAround();
		}
	}

	/** Builds the far land's tiles around the camera again, keeping what they know of the land. Render thread. */
	static void rebuildAround() {
		VoidLod lod = VoidLod.get();
		if (lod == null || lod.engine().source() == null) {
			return;
		}
		var mc = net.minecraft.client.Minecraft.getInstance();
		if (mc.gameRenderer == null) {
			return;
		}
		var eye = mc.gameRenderer.mainCamera().position();
		int r = (int) (lod.radius + 1024);
		int x = (int) eye.x, z = (int) eye.z;
		lod.engine().refresh(x - r, z - r, x + r, z + r, dev.voidmc.lod.core.TileKey.MAX_LEVEL);
	}

	/** Threads working out what blocks look like for the far land ({@link #bakingColours}), and how many there are. */
	private static final ThreadLocal<Boolean> BAKING = new ThreadLocal<>();
	private static volatile int bakers;

	/**
	 * Whether this thread is working out what blocks look like for the far land right now (their textures and vanilla's
	 * tints): a mod whose tints follow the seasons gives vanilla's own here; the season comes through {@link #setSeasons}.
	 */
	public static boolean bakingColours() {
		return bakers > 0 && BAKING.get() == Boolean.TRUE;
	}

	/** Marks this thread as working out block colours for the far land ({@code true}), or done with it. */
	public static synchronized void baking(boolean on) {
		if (on == (BAKING.get() == Boolean.TRUE)) {
			return;
		}
		BAKING.set(on ? Boolean.TRUE : null);
		bakers += on ? 1 : -1;
	}

	/** The block behind a material id the far land's faces carry, or null. */
	public static BlockState material(int id) {
		VoidLod lod = VoidLod.get();
		Materials materials = lod == null ? null : lod.materials();
		Materials.Face face = materials == null ? null : materials.face(id);
		return face == null ? null : face.state();
	}

	/** The registry name of a biome id the far land's cells carry ({@code minecraft:plains}), or null. */
	public static String biomeName(int id) {
		return BiomeIds.name(id);
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
