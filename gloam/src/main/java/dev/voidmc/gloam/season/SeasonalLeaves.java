package dev.voidmc.gloam.season;

import dev.voidmc.gloam.GloamSettings;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.SimpleUnbakedExtraModel;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The broadleaf crowns through the year as the blocks they are drawn with, swapped on the client: each broadleaf leaf
 * block's model (oak, dark oak, birch, jungle, acacia and mangrove where the land has seasons, Expanse's beech, willow,
 * maples, aspen and larch; never a conifer) is wrapped so
 * that, block by block, it draws one of:
 * <ul>
 *   <li>its own leaf cube (summer);</li>
 *   <li>for Expanse's leaves, which carry their colour in the texture, the cube in its other season's texture: beech
 *       copper and willow yellow in autumn, the maples, aspen and larch (drawn in their autumn colours) green until the
 *       autumn comes ({@code tools/gen_seasons.py} makes those textures from Expanse's own);</li>
 *   <li>a thinning crown: a few twigs and small clumps of leaves (late autumn as the leaves fall, early spring as they
 *       come);</li>
 *   <li>a bare crown: two crossed planes and a flat one of thin branching twigs, mostly clear, in half its blocks (the
 *       rest drawn clear), so a winter crown is a light tracery with the sky and the ground through it.</li>
 * </ul>
 * Which one depends on how bare the season has the crowns ({@link SeasonColours#bare}) and on each tree and each block
 * (a tree turns as one, roughly, with its blocks a little apart), so a wood thins over a few days rather than at once.
 * The twigs are tinted like the leaves (the season's twig grey-brown, tree by tree; see {@link SeasonLeaves}). The choice
 * is made as a chunk is meshed (vanilla through Fabric's renderer, Sodium through its own; both ask the model for a
 * block's quads with its position), so a season step re-meshes the chunks ({@link Remesh}). Items, particles and
 * position-less queries (VOID LOD reading what leaves look like) keep the leaf cube. With fast (opaque) leaves the cube
 * stays.
 */
final class SeasonalLeaves {
	/** What an Expanse crown's texture does through the year. */
	private enum Shift { NONE, GREEN_UNTIL_AUTUMN, AUTUMN_TEXTURE }

	/** One kind of broadleaf crown: its block, whether its leaves are tinted, its texture's shift, and its summer green (for the far land). */
	private record Species(String block, boolean tinted, Shift shift, int summer) {
	}

	private static final List<Species> SPECIES = List.of(
		new Species("minecraft:oak_leaves", true, Shift.NONE, 0),
		new Species("minecraft:dark_oak_leaves", true, Shift.NONE, 0),
		new Species("minecraft:birch_leaves", true, Shift.NONE, 0),
		// leafy where it is warm and wet, but bare in a temperate land's winter like any broadleaf (Expanse grows them there)
		new Species("minecraft:jungle_leaves", true, Shift.NONE, 0),
		new Species("minecraft:acacia_leaves", true, Shift.NONE, 0),
		new Species("minecraft:mangrove_leaves", true, Shift.NONE, 0),
		new Species("expanse:beech_leaves", false, Shift.AUTUMN_TEXTURE, 0),
		new Species("expanse:willow_leaves", false, Shift.AUTUMN_TEXTURE, 0),
		new Species("expanse:maple_leaves", false, Shift.GREEN_UNTIL_AUTUMN, 0x5F8C35),
		new Species("expanse:orange_maple_leaves", false, Shift.GREEN_UNTIL_AUTUMN, 0x6B9038),
		new Species("expanse:aspen_leaves", false, Shift.GREEN_UNTIL_AUTUMN, 0x86A848),
		new Species("expanse:larch_leaves", false, Shift.GREEN_UNTIL_AUTUMN, 0x7FA850));

	private static final int SUMMER = 0, ALT = 1, TUFTS_SPRING = 2, TUFTS_AUTUMN = 3, TWIGS = 4, NOTHING = 5;
	/** The share of a bare crown's leaf blocks that hold twigs; the rest are clear, so the crown is airy, not a thicket. */
	private static final float TWIGGED = 0.5F;

	/** A species' extra models, by look (null where it has none). */
	private record Looks(Species species, List<ExtraModelKey<BlockStateModel>> keys) {
	}

	private static final Map<Block, Looks> LOOKS = new IdentityHashMap<>();

	private SeasonalLeaves() {
	}

	static void register() {
		ModelLoadingPlugin.register(context -> {
			LOOKS.clear();
			for (Species species : SPECIES) {
				Block block = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(species.block())).orElse(Blocks.AIR);
				if (block == Blocks.AIR) {
					continue;
				}
				String name = Identifier.parse(species.block()).getPath();
				String twigs = species.tinted() ? "twigs" : "twigs_bark";
				String[] models = {null, species.shift() == Shift.NONE ? null : name + "_alt", name + "_tufts_spring", name + "_tufts_autumn", twigs};
				java.util.ArrayList<ExtraModelKey<BlockStateModel>> keys = new java.util.ArrayList<>();
				for (String model : models) {
					if (model == null) {
						keys.add(null);
						continue;
					}
					ExtraModelKey<BlockStateModel> key = ExtraModelKey.create(() -> "gloam seasons " + model);
					context.addModel(key, SimpleUnbakedExtraModel.blockStateModel(Identifier.fromNamespaceAndPath("gloam", "block/seasons/" + model)));
					keys.add(key);
				}
				LOOKS.put(block, new Looks(species, keys));
			}
			context.modifyBlockModelAfterBake().register((model, c) -> {
				Looks looks = LOOKS.get(c.state().getBlock());
				return looks == null ? model : new Seasonal(model, looks);
			});
		});
	}

	/** Which look the block at (x, y, z) of {@code species} wears this time of year. */
	private static int look(Species species, int x, int y, int z) {
		SeasonColours c = Seasons.colours();
		if (!c.on() || !seasonal(x, y, z)) {
			return SUMMER;
		}
		// a tree turns as one, near enough (the crown's cell, as its tint), its blocks a little apart
		float u = 0.6F * Hash.unit(Hash.cell(0x7EE5, x, z, SeasonLeaves.CROWN), 3) + 0.4F * Hash.unit(Hash.of(0xB1A5, x * 31 + y, z), 1);
		float bare = c.bare();
		if (bare > 0 && GloamSettings.get().bareTrees && Minecraft.getInstance().options.cutoutLeaves().get()) {
			float tufts = 1.6F * bare, twigs = 1.6F * bare - 0.6F;
			if (u < twigs) {
				return Hash.unit(Hash.of(0x7A16, x, y * 131 + z), 2) < TWIGGED ? TWIGS : NOTHING;
			}
			if (u < tufts) {
				return c.year() < 0.5F ? TUFTS_SPRING : TUFTS_AUTUMN;
			}
		}
		return switch (species.shift()) {
			case GREEN_UNTIL_AUTUMN -> u < green(c) ? ALT : SUMMER;
			case AUTUMN_TEXTURE -> u < c.turned() ? ALT : SUMMER;
			default -> SUMMER;
		};
	}

	/** Each biome's answer to {@link #seasonal}, worked out once. */
	private static final java.util.concurrent.ConcurrentHashMap<net.minecraft.world.level.biome.Biome, Boolean> SEASONAL = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * Whether the land at (x, y, z) has seasons its crowns follow: not a hot or a wet tropical one, where its leaves stay
	 * green (as its tint does: {@link SeasonColours#seasonality}). Asked of the client level from the meshing threads,
	 * which read its biomes as vanilla's meshing does.
	 */
	private static boolean seasonal(int x, int y, int z) {
		var level = Minecraft.getInstance().level;
		if (level == null) {
			return true;
		}
		var biome = level.getBiome(new BlockPos(x, y, z)).value();
		return SEASONAL.computeIfAbsent(biome, b -> SeasonColours.seasonality(b.getBaseTemperature(), b.climateSettings.downfall()) >= 0.45F);
	}

	/** A leaf block's model, drawing the look the season gives it at its place. */
	private static final class Seasonal extends WrapperBlockStateModel {
		private final Looks looks;
		private volatile BlockStateModel[] models;

		Seasonal(BlockStateModel wrapped, Looks looks) {
			super(wrapped);
			this.looks = looks;
		}

		/** The model for a look, from the extra models baked with this one (the leaf cube where there is none). */
		private BlockStateModel model(int look) {
			BlockStateModel[] all = this.models;
			if (all == null) {
				all = new BlockStateModel[5];
				var manager = Minecraft.getInstance().getModelManager();
				for (int i = 0; i < 5; i++) {
					ExtraModelKey<BlockStateModel> key = this.looks.keys().get(i);
					BlockStateModel m = key == null ? null : manager.getModel(key);
					all[i] = m == null ? this.wrapped : m;
				}
				this.models = all;
			}
			return all[look];
		}

		/** The model at {@code pos}, or null for none (a clear block in a bare crown). */
		private @Nullable BlockStateModel at(BlockPos pos) {
			int look = look(this.looks.species(), pos.getX(), pos.getY(), pos.getZ());
			return look == SUMMER ? this.wrapped : look == NOTHING ? null : this.model(look);
		}

		@Override
		public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
			Predicate<@Nullable Direction> cullTest) {
			BlockStateModel model = this.at(pos);
			if (model != null) {
				model.emitQuads(emitter, level, pos, state, random, cullTest);
			}
		}

		@Override
		public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
			BlockStateModel model = this.at(pos);
			if (model == null) {
				return "gloam:clear crown block";
			}
			Object key = model.createGeometryKey(level, pos, state, random);
			return key == null ? null : model == this.wrapped ? key : List.of(model, key);
		}

		@Override
		public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
			BlockStateModel model = this.at(pos);
			return (model == null ? this.wrapped : model).materialFlags(level, pos, state, random);
		}

		@Override
		public boolean hasMaterialFlag(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, int flag) {
			BlockStateModel model = this.at(pos);
			return (model == null ? this.wrapped : model).hasMaterialFlag(level, pos, state, random, flag);
		}
	}

	// ------------------------------------------------------------------------------------- crowns drawn in autumn

	/** The spring and summer green of a crown drawn in its autumn colours (maple, aspen, larch), or 0 for any other block. */
	static int summer(@Nullable BlockState state) {
		if (state == null) {
			return 0;
		}
		String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
		for (Species species : SPECIES) {
			if (species.summer() != 0 && species.block().equals(name)) {
				return species.summer();
			}
		}
		return 0;
	}

	/** How much of their summer green the crowns drawn in autumn colours show: all spring and summer, going as they turn. */
	static float green(SeasonColours colours) {
		if (!colours.on()) {
			return 0;
		}
		float y = colours.year();
		return y < 0.5F ? 1 : y < 0.71F ? 1 - colours.turned() : 0;
	}

	/**
	 * A colour of a crown drawn in its autumn colours ({@code rgb}, as drawn) as it looks now: its {@code summer} green
	 * (at the drawn colour's brightness) until autumn, and grey-brown twigs as it goes bare.
	 */
	static int drawnAutumn(int rgb, int summer, SeasonColours colours) {
		if (summer == 0) {
			return rgb;
		}
		float lift = luma(rgb) / Math.max(1, luma(summer));
		int c = SeasonColours.mix(rgb, SeasonColours.scale(summer, lift), green(colours));
		return SeasonColours.mix(c, SeasonColours.scale(0x7E6C5A, Math.min(1.3F, lift)), colours.bare() * 0.8F);
	}

	private static float luma(int rgb) {
		return SeasonColours.luma(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF);
	}
}
