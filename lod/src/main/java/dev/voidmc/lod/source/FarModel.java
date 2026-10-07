package dev.voidmc.lod.source;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import org.slf4j.LoggerFactory;

/**
 * Expanse's coarse terrain model ({@code TerrainModel.sampleFar}, with {@code EarthBiomeSource.surfaceBiome}): the
 * land past 32 km, for cells 256 blocks and wider, without building the full plates the near land needs (2 MB each,
 * thousands of them that far out). Found by name, so an Expanse without it still works: the LOD then samples the full
 * model there too.
 */
final class FarModel {
	private static final String MODEL = "dev.voidmc.expanse.world.terrain.TerrainModel";
	private static final String COLUMN = MODEL + "$FarColumn";
	private static final String BIOMES = "dev.voidmc.expanse.world.biome.EarthBiomeSource";

	private final Method sample;
	private final Constructor<?> column;
	private final Method surfaceBiome;
	private final Field height, water, ocean, landform, coast, uplift;
	/** The rock under the soil, if this Expanse says (null: stone). */
	private final Field rock;
	/** The cell's high ground (about its 90th percentile), if this Expanse says (null: its mean is all there is). */
	private final Field peak;
	private final ThreadLocal<Object> columns;

	private FarModel(Method sample, Constructor<?> column, Method surfaceBiome, Class<?> type) throws ReflectiveOperationException {
		this.sample = sample;
		this.column = column;
		this.surfaceBiome = surfaceBiome;
		this.height = type.getField("height");
		this.water = type.getField("water");
		this.ocean = type.getField("ocean");
		this.landform = type.getField("landform");
		this.coast = type.getField("coast");
		this.uplift = type.getField("uplift");
		Field rock;
		try {
			rock = type.getField("rock");
		} catch (NoSuchFieldException e) {
			rock = null;
		}
		this.rock = rock;
		Field peak;
		try {
			peak = type.getField("peak");
		} catch (NoSuchFieldException e) {
			peak = null;
		}
		this.peak = peak;
		this.columns = ThreadLocal.withInitial(() -> {
			try {
				return this.column.newInstance();
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		});
	}

	/** The coarse model, or null if this Expanse has none. */
	static FarModel find() {
		try {
			Class<?> model = Class.forName(MODEL);
			Class<?> type = Class.forName(COLUMN);
			Method sample = model.getMethod("sampleFar", int.class, int.class, type);
			Method biome = Class.forName(BIOMES).getMethod("surfaceBiome", BiomeSource.class, type);
			FarModel far = new FarModel(sample, type.getConstructor(), biome, type);
			LoggerFactory.getLogger("VOID LOD").info("VOID LOD: past 32 km, sampling Expanse's coarse model");
			return far;
		} catch (ReflectiveOperationException | LinkageError e) {
			return null;
		}
	}

	/**
	 * Fills {@code f} with the coarse column at (x, z), and returns its surface biome (null: ask the biome source). Dry land
	 * stands {@code toPeak} of the way from the cell's mean height to its high ground: from far off a cell's ridges hide
	 * its valleys, so the skyline is the high ground's, and ranges read against the sky as they do in the chunks.
	 */
	Holder<Biome> sample(Object model, BiomeSource biomes, int x, int z, int seaLevel, float toPeak, ExpanseFacts f) {
		Object c = this.columns.get();
		try {
			this.sample.invoke(model, x, z, c);
			float height = ((Number) this.height.get(c)).floatValue();
			float water = ((Number) this.water.get(c)).floatValue();
			boolean ocean = this.ocean.getBoolean(c);
			if (this.peak != null && toPeak > 0 && !ocean && Float.isNaN(water)) {
				float peak = ((Number) this.peak.get(c)).floatValue();
				if (peak > height) {
					height += toPeak * (peak - height);
				}
			}
			f.height = height;
			f.riverDist = Float.MAX_VALUE;
			f.riverHalfWidth = 0;
			f.nearRiver = false;
			f.inChannel = false;
			// a lake's surface where the model has one, the sea where it is sea (geometry() lays the sea itself)
			f.waterTop = ocean ? seaLevel : Float.isNaN(water) ? Integer.MIN_VALUE : (int) Math.ceil(water);
			f.bed = (int) Math.floor(height);
			f.landform = ((Number) this.landform.get(c)).intValue();
			f.rock = this.rock == null ? 0 : ((Number) this.rock.get(c)).intValue();
			f.outcrop = 0;
			f.crater = Float.NEGATIVE_INFINITY;
			f.craterLava = false;
			f.fjord = false;
			f.coast = ((Number) this.coast.get(c)).floatValue();
			f.uplift = ((Number) this.uplift.get(c)).floatValue();
			if (!ocean && !Float.isNaN(water) && water > height) {
				// a lake: drawn as the channel of a river is, its water at its own level
				f.inChannel = true;
				f.riverHalfWidth = Float.MAX_VALUE;
				f.bed = (int) Math.floor(height);
			}
			@SuppressWarnings("unchecked")
			Holder<Biome> biome = (Holder<Biome>) this.surfaceBiome.invoke(null, biomes, c);
			return biome;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
