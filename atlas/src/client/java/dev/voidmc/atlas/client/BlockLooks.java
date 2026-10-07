package dev.voidmc.atlas.client;

import dev.voidmc.atlas.client.mixin.SpriteContentsAccessor;
import dev.voidmc.atlas.core.Material;
import dev.voidmc.atlas.core.NamePalette;
import dev.voidmc.atlas.core.Oklab;
import dev.voidmc.atlas.core.TileRenderer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

/**
 * What each block looks like from above: the average of the textures its model draws on its top face (in linear
 * light, the way a texture mips down to one pixel), each layer tinted for the column's biome by the block's own tint
 * source (so grass and leaves take the biome's colours, and a resource pack's grass colours the map), and the block's
 * {@link Material} for the palette.
 *
 * <p>Sprite pixels are read once, on the client thread, for every block in the registry when a world is joined (and
 * again after a resource reload); after that the workers only combine plain numbers.
 */
public final class BlockLooks implements TileRenderer.Looks {
	private record Layer(float r, float g, float b, float coverage, int tintIndex) {
	}

	private record Look(BlockState state, Material material, Layer[] layers, int mapColour) {
	}

	private final Map<Block, Look> looks;
	private final NamePalette blockNames;
	private final NamePalette biomeNames;
	private final Registry<Biome> biomes;
	private final BlockColors blockColors;
	private final AtomicReferenceArray<Look> byId = new AtomicReferenceArray<>(NamePalette.MAX + 1);
	private final AtomicReferenceArray<Biome> biomeById = new AtomicReferenceArray<>(NamePalette.MAX + 1);
	private final ThreadLocal<BiomeTint> tint = ThreadLocal.withInitial(BiomeTint::new);
	private static final Look UNKNOWN = new Look(Blocks.STONE.defaultBlockState(), Material.OTHER, new Layer[]{new Layer(0.2F, 0.2F, 0.2F, 1, -1)}, 0x707070);
	public final long buildNanos;

	private BlockLooks(Map<Block, Look> looks, NamePalette blockNames, NamePalette biomeNames, Registry<Biome> biomes, long buildNanos) {
		this.looks = looks;
		this.blockNames = blockNames;
		this.biomeNames = biomeNames;
		this.biomes = biomes;
		this.blockColors = Minecraft.getInstance().getBlockColors();
		this.buildNanos = buildNanos;
	}

	/** Reads every block's textures. Client thread only (sprite pixels). */
	public static BlockLooks build(NamePalette blockNames, NamePalette biomeNames, Registry<Biome> biomes) {
		long t0 = System.nanoTime();
		Map<TextureAtlasSprite, Layer> sprites = new IdentityHashMap<>();
		Map<Block, Look> looks = new IdentityHashMap<>();
		var models = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
		RandomSource random = RandomSource.create(42);
		for (Block block : BuiltInRegistries.BLOCK) {
			BlockState state = block.defaultBlockState();
			Layer[] layers;
			try {
				BlockStateModel model = models.get(state);
				List<BlockStateModelPart> parts = new ArrayList<>();
				random.setSeed(42);
				model.collectParts(random, parts);
				List<Layer> top = new ArrayList<>();
				for (BlockStateModelPart part : parts) {
					for (BakedQuad quad : part.getQuads(Direction.UP)) {
						Layer l = sprites.computeIfAbsent(quad.materialInfo().sprite(), BlockLooks::measure);
						top.add(new Layer(l.r, l.g, l.b, l.coverage, quad.materialInfo().tintIndex()));
					}
				}
				if (top.isEmpty()) {
					// no full top face (a cross, a slab's side): the particle texture
					Layer l = sprites.computeIfAbsent(model.particleMaterial().sprite(), BlockLooks::measure);
					top.add(new Layer(l.r, l.g, l.b, 1, tintedParticle(block) ? 0 : -1));
				}
				layers = top.toArray(new Layer[0]);
			} catch (RuntimeException e) {
				layers = UNKNOWN.layers;
			}
			int mapColour;
			try {
				mapColour = state.getMapColor(null, BlockPos.ZERO).col;
			} catch (RuntimeException e) {
				mapColour = 0x707070;
			}
			looks.put(block, new Look(state, classify(block, state), layers, mapColour));
		}
		return new BlockLooks(looks, blockNames, biomeNames, biomes, System.nanoTime() - t0);
	}

	/** Leaves and plants whose particle texture is grey and tinted in the world. */
	private static boolean tintedParticle(Block block) {
		BlockState s = block.defaultBlockState();
		return s.is(BlockTags.LEAVES) || block == Blocks.GRASS_BLOCK || block == Blocks.VINE;
	}

	private static Layer measure(TextureAtlasSprite sprite) {
		SpriteContents contents = sprite.contents();
		var image = ((SpriteContentsAccessor) contents).atlas$originalImage();
		int w = Math.min(contents.width(), image.getWidth());
		int h = Math.min(contents.height(), image.getHeight());
		double r = 0, g = 0, b = 0, alpha = 0;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int argb = image.getPixel(x, y);
				float a = (argb >>> 24) / 255.0F;
				if (a <= 0) {
					continue;
				}
				r += Oklab.linear(argb >> 16) * a;
				g += Oklab.linear(argb >> 8) * a;
				b += Oklab.linear(argb) * a;
				alpha += a;
			}
		}
		if (alpha <= 0) {
			return new Layer(0.2F, 0.2F, 0.2F, 0, -1);
		}
		return new Layer((float) (r / alpha), (float) (g / alpha), (float) (b / alpha), (float) (alpha / (w * h)), -1);
	}

	/** The palette material of a block, from its tags, fluid, name and map colour. */
	static Material classify(Block block, BlockState state) {
		FluidState fluid = state.getFluidState();
		if (block == Blocks.WATER || (!fluid.isEmpty() && fluid.is(FluidTags.WATER) && !state.isSolid())) {
			return Material.WATER;
		}
		if (block == Blocks.LAVA || (!fluid.isEmpty() && fluid.is(FluidTags.LAVA))) {
			return Material.LAVA;
		}
		Identifier id = BuiltInRegistries.BLOCK.getKey(block);
		String name = id.getPath();
		if (state.is(BlockTags.LEAVES) || name.endsWith("_leaves") || name.equals("azalea") || name.equals("flowering_azalea")) {
			return Material.FOLIAGE;
		}
		if (state.is(BlockTags.GRASS_BLOCKS) || block == Blocks.GRASS_BLOCK || block == Blocks.MOSS_BLOCK || block == Blocks.MOSS_CARPET
			|| name.contains("grass_block") || name.endsWith("_moss")) {
			return Material.GRASS;
		}
		if (block == Blocks.SNOW || block == Blocks.SNOW_BLOCK || block == Blocks.POWDER_SNOW || state.is(BlockTags.SNOW) || name.contains("snow")) {
			return Material.SNOW;
		}
		if (state.is(BlockTags.ICE) || name.endsWith("ice")) {
			return Material.ICE;
		}
		if (state.is(BlockTags.NYLIUM) || state.is(BlockTags.WART_BLOCKS) || name.contains("nylium") || name.contains("wart_block")) {
			return Material.NYLIUM;
		}
		if (name.contains("soul_s")) {
			return Material.SOUL;
		}
		if (name.contains("basalt") || name.contains("blackstone")) {
			return Material.BASALT;
		}
		if (state.is(BlockTags.BASE_STONE_NETHER) || name.contains("netherrack") || name.contains("magma") || name.contains("nether_brick")) {
			return Material.NETHER;
		}
		if (name.startsWith("end_stone") || name.contains("purpur")) {
			return Material.ENDSTONE;
		}
		if (state.is(BlockTags.SAND) || name.contains("sand")) {
			return Material.SAND;
		}
		if (name.contains("terracotta") || state.is(BlockTags.TERRACOTTA)) {
			return Material.TERRACOTTA;
		}
		if (block == Blocks.GRAVEL || name.contains("gravel")) {
			return Material.GRAVEL;
		}
		if (state.is(BlockTags.MUD) || name.equals("mud") || name.contains("mangrove_roots")) {
			return Material.MUD;
		}
		if (block == Blocks.CLAY || name.equals("clay")) {
			return Material.CLAY;
		}
		if (state.is(BlockTags.DIRT) || name.contains("dirt") || name.contains("podzol") || name.contains("farmland") || name.contains("mycelium")
			|| name.contains("path")) {
			return Material.DIRT;
		}
		if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS) || name.endsWith("_log") || name.endsWith("_wood") || name.contains("planks")
			|| name.contains("_stem") || name.contains("bamboo") || name.contains("hay") || name.contains("thatch")) {
			return Material.WOOD;
		}
		if (state.is(BlockTags.STONE_BRICKS) || name.contains("brick") || name.contains("polished") || name.contains("tile") || name.contains("concrete")
			|| name.contains("wool") || name.contains("glass") || name.contains("copper") || name.contains("iron") || name.contains("chiseled")
			|| name.contains("cut_") || name.contains("smooth_") || name.contains("pillar") || name.contains("stairs") || name.contains("slab")
			|| name.contains("roof") || name.contains("shingle")) {
			return Material.BUILT;
		}
		if (state.is(BlockTags.BASE_STONE_OVERWORLD) || name.contains("stone") || name.contains("deepslate") || name.contains("andesite")
			|| name.contains("diorite") || name.contains("granite") || name.contains("tuff") || name.contains("calcite") || name.contains("slate")
			|| name.contains("marble") || name.contains("limestone") || name.contains("chalk") || name.contains("ore") || name.contains("cobble")
			|| name.contains("rock") || name.contains("boulder") || name.contains("dripstone")) {
			return Material.STONE;
		}
		if (state.is(BlockTags.FLOWERS) || state.is(BlockTags.CROPS) || state.is(BlockTags.SAPLINGS) || !state.isSolid()) {
			return Material.PLANT;
		}
		MapColor mc = state.getMapColor(null, BlockPos.ZERO);
		if (mc == MapColor.STONE || mc == MapColor.DEEPSLATE) {
			return Material.STONE;
		}
		if (mc == MapColor.WOOD || mc == MapColor.PODZOL) {
			return Material.WOOD;
		}
		if (mc == MapColor.PLANT || mc == MapColor.GRASS) {
			return Material.GRASS;
		}
		return Material.OTHER;
	}

	// --- Looks, for the workers ----------------------------------------------------------------------------------

	private Look look(int id) {
		Look look = this.byId.get(id);
		if (look == null) {
			String name = this.blockNames.name(id);
			int far = dev.voidmc.atlas.core.FarColour.colour(name);
			if (far >= 0) {
				// terrain seen from afar: a colour as VOID LOD drew it, untinted
				look = new Look(Blocks.STONE.defaultBlockState(), Material.OTHER, new Layer[]{new Layer(Oklab.linear(far >> 16), Oklab.linear(far >> 8),
					Oklab.linear(far), 1, -1)}, far);
				this.byId.set(id, look);
				return look;
			}
			Block block = name == null ? null : BuiltInRegistries.BLOCK.getOptional(Identifier.tryParse(name)).orElse(null);
			look = block == null ? UNKNOWN : this.looks.getOrDefault(block, UNKNOWN);
			this.byId.set(id, look);
		}
		return look;
	}

	private @Nullable Biome biome(int id) {
		Biome biome = this.biomeById.get(id);
		if (biome == null) {
			String name = this.biomeNames.name(id);
			Identifier key = name == null ? null : Identifier.tryParse(name);
			biome = key == null ? null : this.biomes.getOptional(key).orElse(null);
			if (biome != null) {
				this.biomeById.set(id, biome);
			}
		}
		return biome;
	}

	@Override
	public Material material(int block) {
		return block == 0 ? Material.OTHER : this.look(block).material;
	}

	@Override
	public int colour(int blockId, int biomeId) {
		Look look = this.look(blockId);
		Biome biome = this.biome(biomeId);
		float r = 0, g = 0, b = 0;
		for (int i = 0; i < look.layers.length; i++) {
			Layer l = look.layers[i];
			float lr = l.r, lg = l.g, lb = l.b;
			if (l.tintIndex >= 0 && biome != null) {
				int c = this.tint(look.state, l.tintIndex, biome);
				lr *= Oklab.linear(c >> 16);
				lg *= Oklab.linear(c >> 8);
				lb *= Oklab.linear(c);
			}
			float a = i == 0 ? 1 : l.coverage;
			r += (lr - r) * a;
			g += (lg - g) * a;
			b += (lb - b) * a;
		}
		return Oklab.srgb(r) << 16 | Oklab.srgb(g) << 8 | Oklab.srgb(b);
	}

	@Override
	public int water(int biomeId) {
		Biome biome = this.biome(biomeId);
		return biome == null ? 0x3F76E4 : biome.getWaterColor() & 0xFFFFFF;
	}

	/**
	 * Writes what every known block looks like in every known biome (tab separated: block, biome, material, colour) and
	 * every biome's water, so the palette can be tuned offline on real maps ({@code ./gradlew atlasPreview}).
	 */
	public void dump(java.nio.file.Path file) throws java.io.IOException {
		StringBuilder b = new StringBuilder();
		int blocks = this.blockNames.size();
		int biomes = this.biomeNames.size();
		for (int bi = 1; bi < biomes; bi++) {
			b.append("water\t").append(this.biomeNames.name(bi)).append('\t').append(String.format("%06x", this.water(bi))).append('\n');
		}
		for (int id = 1; id < blocks; id++) {
			Look look = this.look(id);
			for (int bi = 1; bi < biomes; bi++) {
				b.append(this.blockNames.name(id)).append('\t').append(this.biomeNames.name(bi)).append('\t').append(look.material.name()).append('\t')
					.append(String.format("%06x", this.colour(id, bi))).append('\n');
				if (look.layers.length > 0 && look.layers[0].tintIndex < 0 && look.layers.length == 1) {
					// untinted: the same in every biome, one line is enough
					b.setLength(b.length());
					break;
				}
			}
		}
		java.nio.file.Files.createDirectories(file.getParent());
		java.nio.file.Files.writeString(file, b.toString());
	}

	/** The biome's name for the readout ("minecraft:dark_forest"), or null. */
	@Override
	public String biomeName(int id) {
		return this.biomeNames.name(id);
	}

	@Override
	public String blockName(int id) {
		return this.blockNames.name(id);
	}

	@Override
	public int grass(int biomeId) {
		Biome biome = this.biome(biomeId);
		return biome == null ? dev.voidmc.atlas.core.MapArt.REF_GRASS : biome.getGrassColor(0, 0) & 0xFFFFFF;
	}

	@Override
	public int foliage(int biomeId) {
		Biome biome = this.biome(biomeId);
		return biome == null ? dev.voidmc.atlas.core.MapArt.REF_FOLIAGE : biome.getFoliageColor() & 0xFFFFFF;
	}

	/** A biome's climate for the biome view's hover: {temperature, rainfall}, or null. */
	public float @Nullable [] climate(int biomeId) {
		Biome biome = this.biome(biomeId);
		if (biome == null) {
			return null;
		}
		float rain = Float.NaN;
		try {
			for (var f : Biome.class.getDeclaredFields()) {
				if (f.getType().getSimpleName().equals("ClimateSettings")) {
					f.setAccessible(true);
					Object c = f.get(biome);
					var m = c.getClass().getDeclaredMethod("downfall");
					m.setAccessible(true);
					rain = (Float) m.invoke(c);
					break;
				}
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			// no rainfall to tell
		}
		return new float[]{biome.getBaseTemperature(), rain};
	}

	private int tint(BlockState state, int index, Biome biome) {
		BlockTintSource source = this.blockColors.getTintSource(state, index);
		if (source == null) {
			return 0xFFFFFF;
		}
		BiomeTint getter = this.tint.get();
		getter.biome = biome;
		try {
			return source.colorInWorld(state, getter, BlockPos.ZERO) & 0xFFFFFF;
		} catch (RuntimeException e) {
			return source.color(state) & 0xFFFFFF;
		}
	}

	/** Answers a tint source's question about the colour at a position with one biome's colours. */
	private static final class BiomeTint implements BlockAndTintGetter {
		Biome biome;

		@Override
		public CardinalLighting cardinalLighting() {
			return CardinalLighting.DEFAULT;
		}

		@Override
		public LevelLightEngine getLightEngine() {
			return LevelLightEngine.EMPTY;
		}

		@Override
		public int getBlockTint(BlockPos pos, ColorResolver color) {
			return color.getColor(this.biome, pos.getX(), pos.getZ());
		}

		@Override
		public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
			return null;
		}

		@Override
		public BlockState getBlockState(BlockPos pos) {
			return Blocks.AIR.defaultBlockState();
		}

		@Override
		public FluidState getFluidState(BlockPos pos) {
			return Fluids.EMPTY.defaultFluidState();
		}

		@Override
		public int getHeight() {
			return 384;
		}

		@Override
		public int getMinY() {
			return -64;
		}
	}
}
