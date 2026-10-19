package dev.voidmc.hearth.gametest.render;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.TerrainProbe;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Locale;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A stand-in for Expanse's vegetation round a previewed town (ART_REVIEW_2.md item 30), so the edge where the town
 * meets the countryside can be judged in {@code townPreview}. Expanse's own foliage is world-generation features that
 * need a level, which the offline renderer does not have; this draws the same kind of land by the same habitats: woods
 * in copses with meadow between them (temperate reads as Bohemia: broadleaf and birch stands, grass and flower drifts),
 * spruce in the north and the mountains, olive-dark scrub and lone trees in the Mediterranean, thorn and acacia on the
 * steppe, dead bushes on sand, jungle and bamboo in the wet tropics, hanging willows in the bayou. On Expanse's Earth
 * model ({@code -Pterrain=earth}) the probe's biome picks the habitat; on the synthetic terrains, the culture.
 *
 * <p>Only natural ground is dressed: nothing within two blocks of anything a plot placed, no water, nothing over a
 * block already standing. It is a picture, not a check: the floating-piece probe ({@link TownFloat}) builds its town
 * without it.
 */
final class TownCountry {
	private TownCountry() {
	}

	private enum Habitat {
		/** Broadleaf woods in copses (oak, birch), meadow between. */
		WOODS,
		/** Spruce stands, ferns. */
		CONIFER,
		/** Grass and flowers, a lone tree here and there. */
		MEADOW,
		/** Lone oaks and dark scrub on dry grass. */
		SCRUB,
		/** Acacia and dead bushes on dry grass. */
		SAVANNA,
		/** Dead bushes and the odd cactus. */
		SAND,
		/** Jungle trees, bushes and bamboo. */
		JUNGLE,
		/** Oaks hung with vines over wet ground. */
		SWAMP,
		/** Cherry groves. */
		BLOSSOM
	}

	/** Dresses the natural ground of the window [x0..x1] x [z0..z1] of a town scene; {@code owner} as {@link TownScene#build}. */
	static void dress(Voxels v, Long2IntOpenHashMap owner, TerrainProbe t, Culture culture, int x0, int z0, int x1, int z1) {
		// columns a plot touched, grown by two: the town keeps its yards and street edges clear
		LongOpenHashSet built = new LongOpenHashSet();
		for (long k : owner.keySet()) {
			int x = Voxels.kx(k);
			int z = Voxels.kz(k);
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					built.add(col(x + dx, z + dz));
				}
			}
		}
		// trees first, one candidate per 4x4 cell at a jittered spot, so stands are dense but trunks never touch
		for (int cx = Math.floorDiv(x0, 4); cx <= Math.floorDiv(x1, 4); cx++) {
			for (int cz = Math.floorDiv(z0, 4); cz <= Math.floorDiv(z1, 4); cz++) {
				int x = cx * 4 + (int) (hash(cx, cz, 11) * 4);
				int z = cz * 4 + (int) (hash(cx, cz, 12) * 4);
				// a crown reaches three blocks: the trunk stands four clear of anything built
				if (x < x0 + 2 || x > x1 - 2 || z < z0 + 2 || z > z1 - 2 || t.isWater(x, z) || built.contains(col(x, z)) || built.contains(col(x + 2, z))
					|| built.contains(col(x - 2, z)) || built.contains(col(x, z + 2)) || built.contains(col(x, z - 2))) {
					continue;
				}
				Habitat h = habitat(t, culture, x, z);
				double wood = wood(x, z);
				double r = hash(cx, cz, 13);
				if (r < treeChance(h, wood)) {
					tree(v, h, x, t.groundY(x, z), z, hash(cx, cz, 14));
				}
			}
		}
		// then the ground cover on every open natural column
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if (built.contains(col(x, z)) || t.isWater(x, z)) {
					continue;
				}
				int g = t.groundY(x, z);
				BlockState top = v.get(x, g, z);
				BlockState soil = v.get(x, g - 1, z);
				if (top != null && !top.isAir() && !top.is(Blocks.SNOW) || soil == null) {
					continue;
				}
				BlockState plant = cover(habitat(t, culture, x, z), soil, x, z);
				if (plant != null) {
					v.set(x, g, z, plant);
				}
			}
		}
	}

	private static Habitat habitat(TerrainProbe t, Culture c, int x, int z) {
		ResourceKey<Biome> b = t.biome(x, z);
		if (b != null) {
			String p = b.identifier().getPath().toLowerCase(Locale.ROOT);
			if (p.contains("cherry")) {
				return Habitat.BLOSSOM;
			}
			if (p.contains("taiga") || p.contains("snowy") || p.contains("grove") || p.contains("pine") || p.contains("spruce")) {
				return Habitat.CONIFER;
			}
			if (p.contains("jungle") || p.contains("bamboo") || p.contains("rainforest")) {
				return Habitat.JUNGLE;
			}
			if (p.contains("swamp") || p.contains("mangrove") || p.contains("marsh") || p.contains("bayou")) {
				return Habitat.SWAMP;
			}
			if (p.contains("desert") || p.contains("dune") || p.contains("beach")) {
				return Habitat.SAND;
			}
			if (p.contains("savanna") || p.contains("badlands") || p.contains("steppe")) {
				return Habitat.SAVANNA;
			}
			if (p.contains("forest") || p.contains("wood") || p.contains("glade")) {
				return Habitat.WOODS;
			}
			if (p.contains("plains") || p.contains("meadow")) {
				return Habitat.MEADOW;
			}
		}
		return switch (c) {
			case NORTHERN, ALPINE, TUNDRA -> Habitat.CONIFER;
			case MEDITERRANEAN -> Habitat.SCRUB;
			case STEPPE, MESA -> Habitat.SAVANNA;
			case DESERT -> Habitat.SAND;
			case TROPICAL, KARST -> Habitat.JUNGLE;
			case BAYOU -> Habitat.SWAMP;
			case WISTERIA -> Habitat.BLOSSOM;
			case MARITIME, MOORLAND -> Habitat.MEADOW;
			default -> Habitat.WOODS;
		};
	}

	/** How wooded a spot is, 0..1, in patches some twenty blocks across: copses and the clearings between them. */
	private static double wood(int x, int z) {
		return 0.65 * smooth(x, z, 24, 21) + 0.35 * smooth(x, z, 9, 22);
	}

	private static double treeChance(Habitat h, double wood) {
		return switch (h) {
			case WOODS, CONIFER, JUNGLE -> wood > 0.55 ? 0.9 : wood > 0.45 ? 0.35 : 0.04;
			case BLOSSOM, SWAMP -> wood > 0.55 ? 0.6 : 0.06;
			case MEADOW -> wood > 0.7 ? 0.5 : 0.02;
			case SCRUB -> wood > 0.6 ? 0.3 : 0.06;
			case SAVANNA -> 0.05;
			case SAND -> 0.0;
		};
	}

	private static BlockState cover(Habitat h, BlockState soil, int x, int z) {
		boolean grass = soil.is(Blocks.GRASS_BLOCK);
		boolean sand = soil.is(Blocks.SAND) || soil.is(Blocks.RED_SAND) || soil.is(Blocks.COARSE_DIRT);
		double r = hash(x, z, 31);
		double drift = smooth(x, z, 6, 32);
		if (sand) {
			return r < 0.025 ? Blocks.DEAD_BUSH.defaultBlockState() : h == Habitat.SAND && r > 0.997 ? Blocks.CACTUS.defaultBlockState() : null;
		}
		if (!grass) {
			return null;
		}
		return switch (h) {
			case WOODS -> r < 0.3 ? Blocks.SHORT_GRASS.defaultBlockState() : drift > 0.7 && r < 0.42 ? flower(x, z) : r > 0.985 ? Blocks.FERN.defaultBlockState() : null;
			case MEADOW -> r < 0.4 ? Blocks.SHORT_GRASS.defaultBlockState() : drift > 0.62 && r < 0.55 ? flower(x, z) : null;
			case CONIFER -> r < 0.18 ? Blocks.FERN.defaultBlockState() : r < 0.3 ? Blocks.SHORT_GRASS.defaultBlockState() : r > 0.99 ? Blocks.SWEET_BERRY_BUSH.defaultBlockState() : null;
			case SCRUB -> r < 0.25 ? Blocks.SHORT_DRY_GRASS.defaultBlockState() : drift > 0.75 && r < 0.3 ? Blocks.AZALEA.defaultBlockState() : null;
			case SAVANNA -> r < 0.35 ? Blocks.SHORT_DRY_GRASS.defaultBlockState() : r < 0.37 ? Blocks.DEAD_BUSH.defaultBlockState() : null;
			case SAND -> r < 0.03 ? Blocks.DEAD_BUSH.defaultBlockState() : null;
			case JUNGLE -> r < 0.35 ? Blocks.SHORT_GRASS.defaultBlockState() : r < 0.45 ? Blocks.FERN.defaultBlockState() : drift > 0.8 && r < 0.5 ? Blocks.BAMBOO.defaultBlockState() : null;
			case SWAMP -> r < 0.3 ? Blocks.SHORT_GRASS.defaultBlockState() : r < 0.36 ? Blocks.FERN.defaultBlockState() : null;
			case BLOSSOM -> r < 0.3 ? Blocks.SHORT_GRASS.defaultBlockState() : drift > 0.55 && r < 0.45 ? Blocks.PINK_PETALS.defaultBlockState() : null;
		};
	}

	/** A meadow flower, one kind to a drift. */
	private static BlockState flower(int x, int z) {
		Block[] kinds = {Blocks.DANDELION, Blocks.POPPY, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.AZURE_BLUET};
		return kinds[(int) (smooth(x, z, 7, 33) * kinds.length * 0.999)].defaultBlockState();
	}

	// ------------------------------------------------------------------------------------------- trees

	private static void tree(Voxels v, Habitat h, int x, int y, int z, double r) {
		switch (h) {
			case CONIFER -> spruce(v, x, y, z, 7 + (int) (r * 5));
			case WOODS -> {
				if (r < 0.3) {
					round(v, x, y, z, 5 + (int) (r * 6), Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES, 1);
				} else if (r > 0.92) {
					spruce(v, x, y, z, 8 + (int) (r * 3));
				} else {
					round(v, x, y, z, 4 + (int) (r * 4), Blocks.OAK_LOG, Blocks.OAK_LEAVES, 2);
				}
			}
			case MEADOW -> round(v, x, y, z, 4 + (int) (r * 3), Blocks.OAK_LOG, Blocks.OAK_LEAVES, 2);
			case SCRUB -> {
				if (r < 0.5) {
					bush(v, x, y, z, Blocks.OAK_LOG, Blocks.AZALEA_LEAVES);
				} else {
					round(v, x, y, z, 3 + (int) (r * 2), Blocks.DARK_OAK_LOG, Blocks.AZALEA_LEAVES, 2);
				}
			}
			case SAVANNA -> acacia(v, x, y, z, 4 + (int) (r * 2));
			case JUNGLE -> {
				if (r < 0.4) {
					bush(v, x, y, z, Blocks.JUNGLE_LOG, Blocks.JUNGLE_LEAVES);
				} else {
					round(v, x, y, z, 6 + (int) (r * 5), Blocks.JUNGLE_LOG, Blocks.JUNGLE_LEAVES, 3);
				}
			}
			case SWAMP -> {
				int top = round(v, x, y, z, 5 + (int) (r * 2), Blocks.OAK_LOG, Blocks.OAK_LEAVES, 3);
				for (int[] d : new int[][]{{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
					for (int dy = 1; dy <= 2 + (int) (hash(x + d[0], z + d[1], 41) * 2); dy++) {
						put(v, x + d[0], top - 1 - dy, z + d[1], Blocks.VINE.defaultBlockState());
					}
				}
			}
			case BLOSSOM -> round(v, x, y, z, 4 + (int) (r * 2), Blocks.CHERRY_LOG, Blocks.CHERRY_LEAVES, 3);
			case SAND -> {
			}
		}
	}

	/** A broadleaf: a trunk and a lumpy round crown of the given radius; returns the crown's top. */
	private static int round(Voxels v, int x, int y, int z, int height, Block log, Block leaves, int radius) {
		for (int i = 0; i < height; i++) {
			put(v, x, y + i, z, log.defaultBlockState());
		}
		int c = y + height - 1;
		for (int dy = -radius + 1; dy <= radius; dy++) {
			int rr = dy == radius ? Math.max(1, radius - 1) : radius;
			for (int dx = -rr; dx <= rr; dx++) {
				for (int dz = -rr; dz <= rr; dz++) {
					int d2 = dx * dx + dz * dz + Math.max(0, dy) * Math.max(0, dy);
					if (d2 > rr * rr + 1 || d2 == rr * rr + 1 && hash(x + dx, z + dz + dy * 31, 42) < 0.6) {
						continue;
					}
					put(v, x + dx, c + dy, z + dz, leaves.defaultBlockState());
				}
			}
		}
		return c + radius;
	}

	/** A spruce: a tall trunk in tiers of shrinking rings. */
	private static void spruce(Voxels v, int x, int y, int z, int height) {
		for (int i = 0; i < height; i++) {
			put(v, x, y + i, z, Blocks.SPRUCE_LOG.defaultBlockState());
		}
		BlockState leaf = Blocks.SPRUCE_LEAVES.defaultBlockState();
		for (int i = 2; i <= height; i++) {
			int fromTop = height - i;
			int rr = fromTop <= 1 ? (fromTop == 0 ? 0 : 1) : (fromTop % 2 == 0 ? 1 : Math.min(3, 1 + fromTop / 3));
			for (int dx = -rr; dx <= rr; dx++) {
				for (int dz = -rr; dz <= rr; dz++) {
					if (Math.abs(dx) + Math.abs(dz) > rr + (rr > 1 ? 1 : 0)) {
						continue;
					}
					put(v, x + dx, y + i, z + dz, leaf);
				}
			}
		}
		put(v, x, y + height, z, leaf);
	}

	/** An acacia: a leaning trunk under a flat crown. */
	private static void acacia(Voxels v, int x, int y, int z, int height) {
		int lean = hash(x, z, 51) < 0.5 ? 1 : -1;
		int tx = x;
		for (int i = 0; i < height; i++) {
			if (i == height / 2) {
				tx += lean;
			}
			put(v, tx, y + i, z, Blocks.ACACIA_LOG.defaultBlockState());
		}
		BlockState leaf = Blocks.ACACIA_LEAVES.defaultBlockState();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (Math.abs(dx) + Math.abs(dz) <= 3) {
					put(v, tx + dx, y + height, z + dz, leaf);
				}
				if (Math.abs(dx) + Math.abs(dz) <= 1) {
					put(v, tx + dx, y + height + 1, z + dz, leaf);
				}
			}
		}
	}

	/** A low bush: one log under a mound of leaves. */
	private static void bush(Voxels v, int x, int y, int z, Block log, Block leaves) {
		put(v, x, y, z, log.defaultBlockState());
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				put(v, x + dx, y + (dx == 0 && dz == 0 ? 1 : 0), z + dz, leaves.defaultBlockState());
			}
		}
	}

	/** Sets a block only into air or nothing: a tree never cuts into the ground or anything built. */
	private static void put(Voxels v, int x, int y, int z, BlockState s) {
		BlockState cur = v.get(x, y, z);
		if (cur == null || cur.isAir() || cur.is(Blocks.SNOW) || cur.canBeReplaced() && cur.getFluidState().isEmpty()) {
			v.set(x, y, z, s);
		}
	}

	private static long col(int x, int z) {
		return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
	}

	private static double smooth(int x, int z, int cell, long salt) {
		int i = Math.floorDiv(x, cell);
		int j = Math.floorDiv(z, cell);
		double fx = (x - i * cell + 0.5) / cell;
		double fz = (z - j * cell + 0.5) / cell;
		fx = fx * fx * (3 - 2 * fx);
		fz = fz * fz * (3 - 2 * fz);
		double a = hash(i, j, salt);
		double b = hash(i + 1, j, salt);
		double c = hash(i, j + 1, salt);
		double d = hash(i + 1, j + 1, salt);
		return (a + (b - a) * fx) * (1 - fz) + (c + (d - c) * fx) * fz;
	}

	private static double hash(int x, int z, long salt) {
		long h = x * 0x632BE59BD9B4E019L ^ z * 0x9E3779B97F4A7C15L ^ salt * 0xD1B54A32D192ED03L;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return (h >>> 11) * 0x1.0p-53;
	}
}
