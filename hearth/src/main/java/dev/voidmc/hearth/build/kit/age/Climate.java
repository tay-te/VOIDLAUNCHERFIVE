package dev.voidmc.hearth.build.kit.age;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Noise;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/**
 * What grows over a building left to itself in each culture's country: the soil a rotted floor turns to, the weeds and
 * bushes, the tree that seeds itself in a roofless room, whether ivy climbs the walls and moss creeps up them. All
 * vanilla and all natural (the surface rule): a temperate ruin goes green with ivy, oak and ferns, a northern one with
 * spruce, moss and berries, a desert one silts up with sand, dead bushes and dry grass.
 */
public enum Climate {
	/** Oak and birch country: ivy, ferns and brambles, moss on the north side. */
	TEMPERATE(true, true, Blocks.GRASS_BLOCK, new Block[]{Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.MOSS_BLOCK, Blocks.GRASS_BLOCK},
		new Block[]{Blocks.OAK_LEAVES, Blocks.BIRCH_LEAVES, Blocks.OAK_LEAVES}, new Block[]{Blocks.OAK_LOG, Blocks.BIRCH_LOG, Blocks.OAK_LOG},
		new Object[]{Blocks.SHORT_GRASS, 5, Blocks.FERN, 2, Blocks.BUSH, 2, Blocks.TALL_GRASS, 2, Blocks.WILDFLOWERS, 1, Blocks.LARGE_FERN, 0.6,
			Blocks.POPPY, 0.3, Blocks.DANDELION, 0.3}),
	/** Spruce woods: moss, ferns, berries; ivy only here and there. */
	BOREAL(true, true, Blocks.GRASS_BLOCK, new Block[]{Blocks.PODZOL, Blocks.MOSS_BLOCK, Blocks.COARSE_DIRT, Blocks.MOSS_BLOCK},
		new Block[]{Blocks.SPRUCE_LEAVES}, new Block[]{Blocks.SPRUCE_LOG},
		new Object[]{Blocks.FERN, 4, Blocks.SHORT_GRASS, 3, Blocks.LARGE_FERN, 1.5, Blocks.SWEET_BERRY_BUSH, 1, Blocks.BUSH, 1}),
	/** The treeless north: moss and grass, no ivy, no trees. */
	TUNDRA(false, true, Blocks.GRASS_BLOCK, new Block[]{Blocks.MOSS_BLOCK, Blocks.COARSE_DIRT, Blocks.GRASS_BLOCK},
		new Block[0], new Block[0], new Object[]{Blocks.SHORT_GRASS, 3, Blocks.FERN, 1, Blocks.SWEET_BERRY_BUSH, 0.4}),
	/** Heath and bog: ferns and bracken, moss, a birch at most. */
	MOOR(true, true, Blocks.GRASS_BLOCK, new Block[]{Blocks.COARSE_DIRT, Blocks.MOSS_BLOCK, Blocks.PODZOL, Blocks.GRASS_BLOCK},
		new Block[]{Blocks.BIRCH_LEAVES}, new Block[]{Blocks.BIRCH_LOG},
		new Object[]{Blocks.FERN, 3, Blocks.SHORT_GRASS, 3, Blocks.BUSH, 2, Blocks.LARGE_FERN, 1.2, Blocks.TALL_GRASS, 0.6}),
	/** Sand country: the ruin silts up, dead bushes and dry grass, no moss, no ivy. */
	DRY(false, false, Blocks.SAND, new Block[]{Blocks.SAND, Blocks.SAND, Blocks.COARSE_DIRT},
		new Block[0], new Block[0], new Object[]{Blocks.SHORT_DRY_GRASS, 4, Blocks.DEAD_BUSH, 3, Blocks.TALL_DRY_GRASS, 1.5}),
	/** Red sand and rock: as the desert, in red. */
	MESA(false, false, Blocks.RED_SAND, new Block[]{Blocks.RED_SAND, Blocks.COARSE_DIRT, Blocks.RED_SAND},
		new Block[0], new Block[0], new Object[]{Blocks.SHORT_DRY_GRASS, 4, Blocks.DEAD_BUSH, 3, Blocks.TALL_DRY_GRASS, 1}),
	/** Olive and vine country: dry grass and scrub, a little ivy in the shade. */
	SEMI(true, false, Blocks.GRASS_BLOCK, new Block[]{Blocks.COARSE_DIRT, Blocks.COARSE_DIRT, Blocks.GRASS_BLOCK, Blocks.ROOTED_DIRT},
		new Block[]{Blocks.AZALEA_LEAVES, Blocks.OAK_LEAVES}, new Block[]{Blocks.OAK_LOG},
		new Object[]{Blocks.SHORT_DRY_GRASS, 2, Blocks.SHORT_GRASS, 2, Blocks.BUSH, 1.5, Blocks.TALL_DRY_GRASS, 1, Blocks.WILDFLOWERS, 0.6}),
	/** The grass sea: dry grass and the odd acacia. */
	STEPPE(false, false, Blocks.GRASS_BLOCK, new Block[]{Blocks.COARSE_DIRT, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT},
		new Block[]{Blocks.ACACIA_LEAVES}, new Block[]{Blocks.ACACIA_LOG},
		new Object[]{Blocks.SHORT_DRY_GRASS, 3, Blocks.TALL_DRY_GRASS, 2, Blocks.SHORT_GRASS, 2, Blocks.BUSH, 0.8}),
	/** Jungle: vines over everything, ferns, jungle trees. */
	JUNGLE(true, true, Blocks.GRASS_BLOCK, new Block[]{Blocks.ROOTED_DIRT, Blocks.MOSS_BLOCK, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT},
		new Block[]{Blocks.JUNGLE_LEAVES}, new Block[]{Blocks.JUNGLE_LOG},
		new Object[]{Blocks.FERN, 3, Blocks.LARGE_FERN, 2, Blocks.SHORT_GRASS, 2, Blocks.TALL_GRASS, 2, Blocks.BUSH, 2}),
	/** Karst hills: bamboo and ferns, jungle green. */
	KARST(true, true, Blocks.GRASS_BLOCK, new Block[]{Blocks.ROOTED_DIRT, Blocks.MOSS_BLOCK, Blocks.GRASS_BLOCK},
		new Block[]{Blocks.JUNGLE_LEAVES, Blocks.AZALEA_LEAVES}, new Block[]{Blocks.JUNGLE_LOG},
		new Object[]{Blocks.FERN, 3, Blocks.SHORT_GRASS, 2, Blocks.LARGE_FERN, 1.5, Blocks.BUSH, 1.5}),
	/** Swamp: vines, moss and mud, oaks. */
	SWAMP(true, true, Blocks.GRASS_BLOCK, new Block[]{Blocks.MUD, Blocks.MOSS_BLOCK, Blocks.GRASS_BLOCK, Blocks.ROOTED_DIRT},
		new Block[]{Blocks.OAK_LEAVES, Blocks.MANGROVE_LEAVES}, new Block[]{Blocks.OAK_LOG},
		new Object[]{Blocks.SHORT_GRASS, 3, Blocks.FERN, 2, Blocks.TALL_GRASS, 2, Blocks.BUSH, 1});

	/** Whether ivy climbs walls here. */
	public final boolean vines;
	/** Whether moss creeps over masonry here (and moss carpets lie on ledges). */
	public final boolean moss;
	/** The ground of the yard. */
	public final Block ground;
	/** What a rotted floor or a silted room turns to, in patches. */
	private final Block[] soils;
	private final Block[] leaves;
	private final Block[] logs;
	private final Block[] plants;
	private final double[] weights;

	Climate(boolean vines, boolean moss, Block ground, Block[] soils, Block[] leaves, Block[] logs, Object[] plants) {
		this.vines = vines;
		this.moss = moss;
		this.ground = ground;
		this.soils = soils;
		this.leaves = leaves;
		this.logs = logs;
		this.plants = new Block[plants.length / 2];
		this.weights = new double[plants.length / 2];
		for (int i = 0; i < plants.length; i += 2) {
			this.plants[i / 2] = (Block) plants[i];
			this.weights[i / 2] = ((Number) plants[i + 1]).doubleValue();
		}
	}

	/** The climate of a culture's country. */
	public static Climate of(@Nullable Culture c) {
		if (c == null) {
			return TEMPERATE;
		}
		return switch (c) {
			case TEMPERATE, WISTERIA, MARITIME -> TEMPERATE;
			case NORTHERN, ALPINE -> BOREAL;
			case TUNDRA -> TUNDRA;
			case MOORLAND -> MOOR;
			case DESERT -> DRY;
			case MESA -> MESA;
			case MEDITERRANEAN -> SEMI;
			case STEPPE -> STEPPE;
			case TROPICAL -> JUNGLE;
			case KARST -> KARST;
			case BAYOU -> SWAMP;
		};
	}

	/** The climate a draft's building stands in (its culture's). */
	public static Climate of(Draft d) {
		return of(d.style().root().culture());
	}

	/** Whether trees and leafy bushes seed themselves here. */
	public boolean trees() {
		return this.leaves.length > 0;
	}

	/** The soil for a rotted or silted cell (patchy by position). */
	public Block soil(long seed, int x, int y, int z) {
		double u = Noise.value(seed, x / 2.3, y / 2.3, z / 2.3);
		return this.soils[Math.min(this.soils.length - 1, (int) (stretch(u) * this.soils.length))];
	}

	/** The leaves of this building's self-sown trees (one kind per building). */
	public @Nullable Block leaves(long seed) {
		return this.leaves.length == 0 ? null : this.leaves[(int) Math.floorMod(Noise.hash(seed, 0x1EAF), this.leaves.length)];
	}

	/** The log of this building's self-sown trees (matching {@link #leaves}). */
	public @Nullable Block log(long seed) {
		return this.logs.length == 0 ? null : this.logs[(int) Math.floorMod(Noise.hash(seed, 0x1EAF), this.logs.length)];
	}

	/** A weed for a cell, by weight. */
	public Block plant(double u) {
		double total = 0;
		for (double w : this.weights) {
			total += w;
		}
		double t = u * total;
		for (int i = 0; i < this.plants.length; i++) {
			t -= this.weights[i];
			if (t < 0) {
				return this.plants[i];
			}
		}
		return this.plants[this.plants.length - 1];
	}

	/** Value noise pushed away from its middle, so patches read as patches. */
	public static double stretch(double u) {
		return Math.clamp((u - 0.5) * 1.9 + 0.5, 0, 0.999);
	}
}
