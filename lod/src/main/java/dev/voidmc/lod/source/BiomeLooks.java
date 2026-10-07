package dev.voidmc.lod.source;

import dev.voidmc.lod.core.Colors;
import dev.voidmc.lod.core.ColumnSample;
import dev.voidmc.lod.core.Colors;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * What a biome looks like from a long way off, worked out once per biome, from the biome itself and from the
 * textures actually loaded rather than from a hand-kept table, so modded biomes and resource packs look
 * right without anyone describing them:
 *
 * <ul>
 *   <li>the ground: which blocks make the surface, the soil under it and the rock under that (grass on dirt
 *       on stone, sand on sandstone, snow, mycelium, banded terracotta), from the biome's tags, key,
 *       temperature and rainfall; and each block's colour from its own textures and tint
 *       ({@link TextureColors}), top face and side face apart, with each face's id in the world's
 *       {@link Materials} registry so close tiles can draw the textures themselves;</li>
 *   <li>the trees: read from the biome's vegetation features. Every tree feature it places (through any
 *       nesting of random selectors) contributes its expected count per chunk (from the count, noise
 *       count and rarity placements), its height (from its trunk placer), its leaves' colour, and whether
 *       it is a conifer (from its foliage placer). That gives a canopy height, a crown size, the fraction of
 *       ground the crowns cover and the share of them that are cones, which is what a forest is at a
 *       distance.</li>
 * </ul>
 *
 * <p>Every biome of the level is worked out when the source is made, on the client thread (texture colours
 * read sprite pixels), so the workers only ever read finished looks.
 */
public final class BiomeLooks {
	public enum Ground { GRASS, SAND, RED_SAND, BADLANDS, SNOW, SNOWCAP, MYCELIUM, STONE, MUD, GRAVEL, PODZOL, END }

	public record Look(Ground ground, int top, int side, int soil, int soilDepth, int rock, int[] bands, int bandHeight, int bed,
		int waterColor, float cover, int canopy, int leaves, float crown, float conifer, boolean snowy,
		int topMaterial, int sideMaterial, int soilMaterial, int rockMaterial, int[] bandMaterials, int leavesMaterial) {
		/** Puts this look into a sample: what the ground is made of, its water's tint, and its trees. */
		public void fill(ColumnSample out) {
			out.top = this.top;
			out.side = this.side;
			out.soil = this.soil;
			out.soilDepth = this.soilDepth;
			out.rock = this.rock;
			out.bands = this.bands;
			out.bandHeight = this.bandHeight;
			out.waterColor = this.waterColor;
			out.canopy = this.canopy;
			out.cover = this.cover;
			out.leaves = this.leaves;
			out.crown = this.crown;
			out.conifer = this.conifer;
			out.topMaterial = this.topMaterial;
			out.sideMaterial = this.sideMaterial;
			out.soilMaterial = this.soilMaterial;
			out.rockMaterial = this.rockMaterial;
			out.bandMaterials = this.bandMaterials;
			out.leavesMaterial = this.leavesMaterial;
		}
	}

	private final Map<Biome, Look> looks = new IdentityHashMap<>();
	private final LevelAccessor level;
	private final Materials materials;
	private final Map<String, List<TreeKinds.Kind>> serverTrees;
	/** Per biome, the top and side colour of each block its trees are made of. */
	private final Map<Biome, Map<net.minecraft.world.level.block.Block, int[]>> treeColors = new java.util.concurrent.ConcurrentHashMap<>();
	private final TextureColors textures = new TextureColors();
	private final Biome anyBiome;
	public final int snowTop;
	public final int snowSide;
	public final int stone;
	public final int sand;
	public final int sandstone;
	public final int gravel;
	public final int dirt;
	public final int salt;
	public final int basalt;
	public final int lava;
	public final int[] badlandsBands;
	/** The materials of the snow, stone and sand faces above, for sources that lay them over a biome's own ground. */
	public final int snowTopMaterial;
	public final int snowSideMaterial;
	public final int stoneTopMaterial;
	public final int stoneSideMaterial;
	public final int sandTopMaterial;
	public final int sandSideMaterial;
	private final int[] badlandsMaterials;

	public BiomeLooks(LevelAccessor level) {
		this(level, new Materials());
	}

	public BiomeLooks(LevelAccessor level, Materials materials) {
		this(level, materials, null);
	}

	/** @param serverTrees what trees each biome places, by biome name, as a server said (null: read from the biomes) */
	public BiomeLooks(LevelAccessor level, Materials materials, Map<String, List<TreeKinds.Kind>> serverTrees) {
		this.level = level;
		this.serverTrees = serverTrees;
		this.materials = materials;
		this.badlandsMaterials = this.materials(badlandsStrata(), false);
		var registry = level.registryAccess().lookupOrThrow(Registries.BIOME);
		List<Holder.Reference<Biome>> biomes = registry.listElements().toList();
		// before any look is worked out: a look may need another block's colour (badlands strata), read in any biome
		this.anyBiome = biomes.isEmpty() ? null : biomes.getFirst().value();
		for (Holder.Reference<Biome> holder : biomes) {
			this.looks.put(holder.value(), this.compute(holder));
		}
		this.snowTop = this.top(Blocks.SNOW.defaultBlockState());
		this.snowSide = this.side(Blocks.GRASS_BLOCK.defaultBlockState().setValue(BlockStateProperties.SNOWY, true));
		this.stone = this.top(Blocks.STONE.defaultBlockState());
		this.sand = this.top(Blocks.SAND.defaultBlockState());
		this.sandstone = this.side(Blocks.SANDSTONE.defaultBlockState());
		this.gravel = this.top(Blocks.GRAVEL.defaultBlockState());
		this.dirt = this.top(Blocks.DIRT.defaultBlockState());
		this.salt = Colors.mix(this.top(Blocks.CALCITE.defaultBlockState()), this.top(Blocks.CONCRETE_POWDER.white().defaultBlockState()), 0.3F);
		this.basalt = this.top(Blocks.BASALT.defaultBlockState());
		this.lava = this.top(Blocks.LAVA.defaultBlockState()) == 0x7F7F7F ? 0xD96F21 : this.top(Blocks.LAVA.defaultBlockState());
		this.badlandsBands = this.badlands();
		this.snowTopMaterial = materials.id(Blocks.SNOW.defaultBlockState(), true);
		this.snowSideMaterial = materials.id(Blocks.GRASS_BLOCK.defaultBlockState().setValue(BlockStateProperties.SNOWY, true), false);
		this.stoneTopMaterial = materials.id(Blocks.STONE.defaultBlockState(), true);
		this.stoneSideMaterial = materials.id(Blocks.STONE.defaultBlockState(), false);
		this.sandTopMaterial = materials.id(Blocks.SAND.defaultBlockState(), true);
		this.sandSideMaterial = materials.id(Blocks.SAND.defaultBlockState(), false);
	}

	/**
	 * The top and side colour of {@code block} as one of {@code biome}'s trees wears it (worked out when the looks were),
	 * or null if none of its trees is made of it.
	 */
	public int[] treeColors(Holder<Biome> biome, net.minecraft.world.level.block.Block block) {
		this.get(biome);
		Map<net.minecraft.world.level.block.Block, int[]> colors = this.treeColors.get(biome.value());
		return colors == null ? null : colors.get(block);
	}

	public Look get(Holder<Biome> biome) {
		Look look = this.looks.get(biome.value());
		if (look == null) {
			// a biome the registry did not list when the source was made: work it out now, once
			synchronized (this) {
				look = this.looks.get(biome.value());
				if (look == null) {
					look = this.compute(biome);
					this.looks.put(biome.value(), look);
				}
			}
		}
		return look;
	}

	private int top(BlockState state) {
		return this.textures.top(state, this.anyBiome());
	}

	/** The top face of an untinted block, from its textures. Client thread only: for sources being made. */
	public int colorOf(BlockState state) {
		return this.top(state);
	}

	private int side(BlockState state) {
		return this.textures.side(state, this.anyBiome());
	}

	private Biome anyBiome() {
		return this.anyBiome != null ? this.anyBiome : this.looks.keySet().iterator().next();
	}

	/** Vanilla's badlands strata: mostly plain terracotta, banded orange, yellow, brown, red, white and grey. */
	private static BlockState[] badlandsStrata() {
		BlockState t = Blocks.TERRACOTTA.defaultBlockState();
		BlockState o = Blocks.DYED_TERRACOTTA.orange().defaultBlockState();
		BlockState y = Blocks.DYED_TERRACOTTA.yellow().defaultBlockState();
		BlockState b = Blocks.DYED_TERRACOTTA.brown().defaultBlockState();
		BlockState r = Blocks.DYED_TERRACOTTA.red().defaultBlockState();
		BlockState w = Blocks.DYED_TERRACOTTA.white().defaultBlockState();
		BlockState g = Blocks.DYED_TERRACOTTA.lightGray().defaultBlockState();
		return new BlockState[]{t, o, o, t, y, t, t, b, t, r, t, o, w, g, t, o, t, t, y, y, t, r, b, t, o, t, w, t};
	}

	/** The strata's colours, from their textures. */
	private int[] badlands() {
		BlockState[] strata = badlandsStrata();
		int[] colors = new int[strata.length];
		for (int i = 0; i < strata.length; i++) {
			colors[i] = this.top(strata[i]);
		}
		return colors;
	}

	/** The material ids of a run of block faces: tops, or sides. */
	public int[] materials(BlockState[] states, boolean top) {
		int[] ids = new int[states.length];
		for (int i = 0; i < states.length; i++) {
			ids[i] = this.materials.id(states[i], top);
		}
		return ids;
	}

	private Look compute(Holder<Biome> holder) {
		Biome biome = holder.value();
		float temperature = biome.getBaseTemperature();
		boolean snowy = temperature < 0.15F;
		String path = holder.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
		Ground ground;
		if (holder.is(BiomeTags.IS_END)) {
			ground = Ground.END;
		} else if (holder.is(BiomeTags.IS_BADLANDS)) {
			ground = Ground.BADLANDS;
		} else if (holder.is(BiomeTags.IS_DEEP_OCEAN)) {
			ground = Ground.GRAVEL;
		} else if (holder.is(BiomeTags.IS_BEACH) || holder.is(BiomeTags.IS_OCEAN)) {
			ground = Ground.SAND;
		} else if (holder.is(BiomeTags.IS_RIVER)) {
			// a river biome is sand under its water only: its banks are grass, or snow where it freezes
			ground = snowy ? Ground.SNOW : Ground.GRASS;
		} else if (path.contains("desert") || !biome.hasPrecipitation() && temperature > 1.0F) {
			ground = Ground.SAND;
		} else if (path.contains("mushroom")) {
			ground = Ground.MYCELIUM;
		} else if (path.contains("mangrove")) {
			ground = Ground.MUD;
		} else if (path.contains("stony") || path.contains("stone_")) {
			ground = Ground.STONE;
		} else if (path.contains("gravel")) {
			ground = Ground.GRAVEL;
		} else if (snowy && (path.contains("peak") || path.contains("slope") || path.contains("glacier"))) {
			ground = Ground.SNOWCAP;
		} else if (snowy) {
			ground = Ground.SNOW;
		} else if (path.contains("old_growth") && (holder.is(BiomeTags.IS_TAIGA) || path.contains("taiga"))) {
			ground = Ground.PODZOL;
		} else {
			ground = Ground.GRASS;
		}

		BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
		BlockState dirt = Blocks.DIRT.defaultBlockState();
		BlockState stone = Blocks.STONE.defaultBlockState();
		int top, side, soil, rock, soilDepth = 3, bandHeight = 2;
		int[] bands = null;
		int[] bandMaterials = null;
		// the blocks behind the colours, for their material ids: where a colour is a mix, the larger share
		BlockState topBlock, sideBlock, soilBlock = dirt, rockBlock = stone;
		TextureColors t = this.textures;
		switch (ground) {
			case SAND -> {
				topBlock = sideBlock = soilBlock = Blocks.SAND.defaultBlockState();
				rockBlock = Blocks.SANDSTONE.defaultBlockState();
				top = side = soil = t.top(Blocks.SAND.defaultBlockState(), biome);
				rock = t.side(Blocks.SANDSTONE.defaultBlockState(), biome);
			}
			case RED_SAND -> {
				topBlock = sideBlock = soilBlock = Blocks.RED_SAND.defaultBlockState();
				rockBlock = Blocks.RED_SANDSTONE.defaultBlockState();
				top = side = soil = t.top(Blocks.RED_SAND.defaultBlockState(), biome);
				rock = t.side(Blocks.RED_SANDSTONE.defaultBlockState(), biome);
			}
			case BADLANDS -> {
				// red sand lies on the level tops; the slopes and walls are the strata
				topBlock = Blocks.RED_SAND.defaultBlockState();
				sideBlock = soilBlock = rockBlock = Blocks.TERRACOTTA.defaultBlockState();
				side = soil = rock = t.top(Blocks.TERRACOTTA.defaultBlockState(), biome);
				top = Colors.mix(t.top(Blocks.RED_SAND.defaultBlockState(), biome), side, 0.35F);
				bands = this.badlandsBands != null ? this.badlandsBands : this.badlands();
				bandMaterials = this.badlandsMaterials;
				soilDepth = 0;
			}
			case SNOW -> {
				topBlock = Blocks.SNOW.defaultBlockState();
				sideBlock = grass.setValue(BlockStateProperties.SNOWY, true);
				top = t.top(Blocks.SNOW.defaultBlockState(), biome);
				side = t.side(grass.setValue(BlockStateProperties.SNOWY, true), biome);
				soil = t.top(dirt, biome);
				rock = t.top(stone, biome);
			}
			case SNOWCAP -> {
				topBlock = sideBlock = soilBlock = Blocks.SNOW_BLOCK.defaultBlockState();
				top = side = soil = t.top(Blocks.SNOW_BLOCK.defaultBlockState(), biome);
				rock = t.top(stone, biome);
				soilDepth = 2;
			}
			case MYCELIUM -> {
				topBlock = sideBlock = Blocks.MYCELIUM.defaultBlockState();
				top = t.top(Blocks.MYCELIUM.defaultBlockState(), biome);
				side = t.side(Blocks.MYCELIUM.defaultBlockState(), biome);
				soil = t.top(dirt, biome);
				rock = t.top(stone, biome);
			}
			case STONE -> {
				topBlock = sideBlock = soilBlock = stone;
				top = side = soil = rock = t.top(stone, biome);
			}
			case END -> {
				topBlock = sideBlock = soilBlock = rockBlock = Blocks.END_STONE.defaultBlockState();
				top = side = soil = rock = t.top(Blocks.END_STONE.defaultBlockState(), biome);
			}
			case MUD -> {
				topBlock = sideBlock = soilBlock = Blocks.MUD.defaultBlockState();
				top = side = soil = t.top(Blocks.MUD.defaultBlockState(), biome);
				rock = t.top(stone, biome);
			}
			case GRAVEL -> {
				topBlock = sideBlock = soilBlock = Blocks.GRAVEL.defaultBlockState();
				top = side = soil = t.top(Blocks.GRAVEL.defaultBlockState(), biome);
				rock = t.top(stone, biome);
			}
			case PODZOL -> {
				// old growth taiga: podzol, coarse dirt, moss and grass in patches
				topBlock = sideBlock = Blocks.PODZOL.defaultBlockState();
				top = Colors.mix(t.top(Blocks.PODZOL.defaultBlockState(), biome), t.top(grass, biome), 0.45F);
				side = Colors.mix(t.side(Blocks.PODZOL.defaultBlockState(), biome), t.side(grass, biome), 0.45F);
				soil = t.top(dirt, biome);
				rock = t.top(stone, biome);
			}
			default -> {
				topBlock = sideBlock = grass;
				top = t.top(grass, biome);
				side = t.side(grass, biome);
				soil = t.top(dirt, biome);
				rock = t.top(stone, biome);
			}
		}
		int bed = switch (ground) {
			case SAND, RED_SAND, BADLANDS, GRAVEL, MUD -> top;
			default -> t.top(Blocks.GRAVEL.defaultBlockState(), biome);
		};
		if (holder.is(BiomeTags.IS_RIVER)) {
			bed = t.top(Blocks.SAND.defaultBlockState(), biome);
		}

		Trees trees = new Trees(biome);
		// a server's biomes come without their features: it says what trees they place (net.LodNet.Hello)
		String name = holder.unwrapKey().map(k -> k.identifier().toString()).orElse("");
		List<TreeKinds.Kind> kinds = this.serverTrees != null && this.serverTrees.containsKey(name) ? this.serverTrees.get(name)
			: TreeKinds.of(biome, this.level);
		Map<net.minecraft.world.level.block.Block, int[]> colors = new IdentityHashMap<>();
		for (TreeKinds.Kind kind : kinds) {
			trees.add(kind);
			// each block of its trees as the chunks draw it, for the trees placed block by block (TreeDecorator)
			colors.computeIfAbsent(kind.leaves().getBlock(), b -> {
				int c = this.textures.top(b.defaultBlockState(), biome);
				return new int[]{c, c};
			});
			if (kind.trunk() != null) {
				colors.computeIfAbsent(kind.trunk().getBlock(), b -> new int[]{this.textures.top(b.defaultBlockState(), biome),
					this.textures.side(b.defaultBlockState(), biome)});
			}
		}
		this.treeColors.put(biome, colors);
		int water = Colors.multiply(0xFFFFFF, biome.getWaterColor());
		Materials ids = this.materials;
		return new Look(ground, top, side, soil, soilDepth, rock, bands, bandHeight, bed, water, trees.cover(), trees.height(),
			trees.color(), trees.crown(), trees.conifer(), snowy, ids.id(topBlock, true), ids.id(sideBlock, false), ids.id(soilBlock, false),
			ids.id(rockBlock, false), bandMaterials, ids.id(trees.leaves(), true));
	}

	/** Accumulates the tree features a biome places, weighted by how many of each it places per chunk. */
	private final class Trees {
		private final Biome biome;
		/** How many trees per chunk wear each kind of leaves. */
		private final Map<BlockState, Double> leaves = new IdentityHashMap<>();
		private double perChunk;
		private double conifers;
		private double crownArea;
		private double height;
		private double radius;
		private double r;
		private double g;
		private double b;

		Trees(Biome biome) {
			this.biome = biome;
		}

		/** One kind of tree the biome places: {@code count} a chunk, wearing {@code leaves}. */
		void add(TreeKinds.Kind kind) {
			double count = kind.count();
			double height = kind.height();
			double radius = 2 + height / 7.0;
			this.leaves.merge(kind.leaves(), count, Double::sum);
			int leaves = this.leafColor(kind.leaves());
			this.perChunk += count;
			if (kind.conifer()) {
				this.conifers += count;
			}
			this.crownArea += count * Math.PI * radius * radius;
			this.height += count * height;
			this.radius += count * radius;
			this.r += count * Colors.r(leaves);
			this.g += count * Colors.g(leaves);
			this.b += count * Colors.b(leaves);
		}

		private int leafColor(BlockState state) {
			// leaves shade each other: a crown is darker than one leaf block in full light
			return Colors.scale(BiomeLooks.this.textures.top(state, this.biome), 0.82F);
		}

		/** The leaves most of the biome's trees wear: what a crown's texture is. */
		BlockState leaves() {
			BlockState best = Blocks.OAK_LEAVES.defaultBlockState();
			double most = 0;
			for (Map.Entry<BlockState, Double> e : this.leaves.entrySet()) {
				if (e.getValue() > most) {
					most = e.getValue();
					best = e.getKey();
				}
			}
			return best;
		}

		float cover() {
			// crown area per chunk over the chunk's 256 blocks; crowns overlap, so it saturates
			return (float) (1 - Math.exp(-this.crownArea / 256.0));
		}

		int height() {
			return this.perChunk <= 0 ? 0 : (int) Math.round(this.height / this.perChunk);
		}

		float crown() {
			return this.perChunk <= 0 ? 2.5F : (float) (this.radius / this.perChunk);
		}

		float conifer() {
			return this.perChunk <= 0 ? 0 : (float) (this.conifers / this.perChunk);
		}

		int color() {
			if (this.perChunk <= 0) {
				return Colors.scale(BiomeLooks.this.textures.top(Blocks.OAK_LEAVES.defaultBlockState(), this.biome), 0.82F);
			}
			return Colors.rgb((float) (this.r / this.perChunk), (float) (this.g / this.perChunk), (float) (this.b / this.perChunk));
		}
	}
}
