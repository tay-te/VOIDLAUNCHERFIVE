package dev.voidmc.lod.source;

import dev.voidmc.expanse.world.terrain.EarthChunkGenerator;
import dev.voidmc.expanse.world.terrain.TerrainModel;
import dev.voidmc.lod.core.ColumnSample;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;

/**
 * The fast path for Expanse's Earth worlds: the terrain model answers a column (ground height, rivers,
 * crater lakes, landform, climate) in a couple of microseconds, against the tens a generic noise walk
 * costs, so the whole horizon can be built before a player has finished looking at it.
 *
 * <p>Everything the model knows shows up at a distance: rivers are cut to their own water level and carry
 * it across the plate (and still show as a thread of water where they are narrower than a cell), crater
 * lakes and lava fill volcano tops, canyon walls carry the world's own strata, salt flats are white, and
 * snow lies where the biome's own temperature, falling with height, lays it in the chunks, so a mountain range is
 * white at the top for the same reason in the LOD as in the chunks. Above the tree line young
 * ranges are bare rock, the forest thinning out below it rather than stopping at a contour.
 *
 * <p>Only loaded when Expanse is: nothing else references this class.
 */
public final class ExpanseSource extends WorldgenSource {
	private final TerrainModel model;
	private final ExpanseColours colours;
	private final ThreadLocal<ExpanseFacts> facts = ThreadLocal.withInitial(ExpanseFacts::new);

	private ExpanseSource(ServerLevel level, TerrainModel model, long version, Materials materials) {
		super(level, version, materials);
		this.model = model;
		this.colours = new ExpanseColours(this.looks, materials, this.seaLevel, this.strata, this.strataMaterials);
		NoiseSurface surface = null;
		try {
			// -Dvoid.lod.exactGround=false: the model's height next to the chunks too
			surface = "false".equals(System.getProperty("void.lod.exactGround")) ? null : NoiseSurface.of(this.randomState);
		} catch (RuntimeException | LinkageError e) {
			org.slf4j.LoggerFactory.getLogger("VOID LOD").warn("VOID LOD: cannot read the generator's density; the near ground follows the model's height", e);
		}
		this.surface = surface;
	}

	/** The cells from which on tiles sample the coarse model ({@link #reach}). */
	private volatile int farCell = FAR_CELL;
	/** Expanse's coarse model for the land past 32 km, or null if this Expanse has none. */
	private final FarModel far = FarModel.find();

	/** The ground block for block as the chunks lay it ({@link NoiseSurface}), or null: the model's height. */
	private final NoiseSurface surface;

	/** A built plate's size in memory, about: its terrain grids at 32-block cells, 4 km and more across. */
	private static final long PLATE_BYTES = 2L << 20;
	/** How far out tiles sample the full model; their cells are 256 blocks and wider past it ({@link #FAR_CELL}). */
	static final double PLATE_REACH = 36_000;
	/** Cells this wide or wider sample Expanse's coarse model, which builds no plates. */
	static final int FAR_CELL = 256;
	/** How far apart plates are, about (Expanse's plate spacing). */
	private static final double PLATE_SPACING = 4096;

	/**
	 * Keeps every plate the horizon samples built at once, as far as a sixth of the heap allows: the plates within the
	 * reach and a ring of them past it, which its borders blend with. Building a tile then never waits for a plate
	 * that another tile pushed out a moment before. Once the horizon is built they go back to the model's default, a
	 * few dozen: the meshes are on the GPU, and only new tiles need the plates again.
	 */
	@Override
	public void reach(double radius, boolean building) {
		// past the plates' reach, the tiles there go to the coarse model from their cell size on
		this.farCell = radius > PLATE_REACH ? FAR_CELL / 2 : FAR_CELL;
		int plates = building ? platesFor(radius) : 0;
		if (plates != this.reserved) {
			this.reserved = plates;
			org.slf4j.LoggerFactory.getLogger("VOID LOD").debug("VOID LOD: keeping {} of Expanse's plates built (heap {} MB)", Math.max(40, plates),
				Runtime.getRuntime().maxMemory() >> 20);
		}
		this.model.reservePlates(plates);
	}

	private int reserved = -1;

	/** How many plates to keep built for a horizon {@code radius} blocks out (see {@link #reach}). */
	public static int platesFor(double radius) {
		// past the plate reach the tiles' cells are wide enough for the coarse model, which builds no plates
		double r = Math.min(radius, PLATE_REACH) + 2 * PLATE_SPACING;
		// half as many again: plates are not a regular grid, and the game's own chunk generation shares the cache
		int plates = (int) Math.ceil(1.5 * Math.PI * r * r / (PLATE_SPACING * PLATE_SPACING));
		int affordable = (int) (Runtime.getRuntime().maxMemory() / 6 / PLATE_BYTES);
		// -Dvoid.lod.plates=n: as many as asked, for measuring what the cache costs
		Integer asked = Integer.getInteger("void.lod.plates");
		return asked != null ? asked : Math.min(plates, affordable);
	}

	@Override
	public String diagnostics() {
		return ", Earth plates built " + TerrainModel.BUILDS.get() + (this.surface == null ? "" : this.surface.stats());
	}

	/** The Earth source for a level, or null if the level is not generated by Expanse's Earth terrain. */
	public static WorldgenSource tryCreate(ServerLevel level, long version, Materials materials) {
		if (level.getChunkSource().getGenerator() instanceof EarthChunkGenerator earth) {
			return new ExpanseSource(level, earth.model(level.getChunkSource().randomState()), version, materials);
		}
		return null;
	}

	@Override
	public void sample(int x, int z, int cell, ColumnSample out) {
		if (!TIMING) {
			this.sample(x, z, cell, out, 0);
			return;
		}
		long t0 = System.nanoTime();
		this.sample(x, z, cell, out, t0);
		TIMES.addAndGet(2, System.nanoTime() - t0);
	}

	private void sample(int x, int z, int cell, ColumnSample out, long t0) {
		if (cell >= this.farCell && this.far != null) {
			// past 32 km: the coarse model, which builds no plates
			ExpanseFacts f = this.facts.get();
			// the coarser the cell, the farther out it is: the skyline rises from the mean toward the high ground
			float toPeak = Math.min(0.6F, 0.2F * (1 + Integer.numberOfTrailingZeros(cell) - Integer.numberOfTrailingZeros(this.farCell)));
			Holder<Biome> biome = this.far.sample(this.model, this.biomes, x, z, this.seaLevel, toPeak, f);
			f.geometry(cell, this.seaLevel);
			if (biome == null) {
				biome = this.biome(x, f.biomeY(this.seaLevel), z, cell);
			}
			this.colours.colour(f, biome, x, z, cell, 0, out);
			return;
		}
		// Read everything from the column before anything else samples the model: its column is reused.
		ExpanseFacts f = this.facts.get().of(this.model.sample(x, z));
		if (TIMING) {
			TIMES.addAndGet(0, System.nanoTime() - t0);
		}
		if (cell <= 4) {
			// where the rock crops out: a noise some ten blocks across, which wider cells average away
			f.outcrop = (float) this.model.outcrop(x, z);
		}
		if (cell <= 2 && this.surface != null) {
			// next to the chunks, the ground as the chunks lay it, block for block
			int air = this.surface.firstAir(x, z, f.height);
			if (air != Integer.MIN_VALUE) {
				f.height = air;
			}
		}
		f.geometry(cell, this.seaLevel);
		long t2 = TIMING ? System.nanoTime() : 0;
		Holder<Biome> biome = this.biome(x, f.biomeY(this.seaLevel), z, cell);
		if (TIMING) {
			TIMES.addAndGet(1, System.nanoTime() - t2);
		}
		this.colours.colour(f, biome, x, z, cell, this.bandOffset(x, z), out);
	}
}
