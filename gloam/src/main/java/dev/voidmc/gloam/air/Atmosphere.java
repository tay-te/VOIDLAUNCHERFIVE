package dev.voidmc.gloam.air;

import dev.voidmc.gloam.Gloam;
import dev.voidmc.gloam.GloamSettings;
import dev.voidmc.gloam.bridge.BridgeWorld;
import dev.voidmc.gloam.bridge.RainShadow;
import dev.voidmc.gloam.compat.Expanse;
import dev.voidmc.gloam.mixin.BiomeManagerAccessor;
import dev.voidmc.gloam.mixin.LevelAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The air where the camera is, worked out once a tick on the client and read by everything else in Gloam
 * ({@link #here()}). It keeps the only history the air needs (how wet the ground still is) and smooths what the land
 * gives the air, so walking over a biome edge or the lip of a valley eases the sky over instead of switching it.
 */
public final class Atmosphere {
	/** How far around the camera the land is read to tell a valley from a ridge, in blocks. */
	private static final int RING = 96;

	private ClientLevel level;
	private Air air;
	private AirSample here = AirSample.CLEAR;
	private float temperature = Climate.TEMPERATE.temperature(), downfall = Climate.TEMPERATE.downfall();
	private float lowness = Climate.TEMPERATE.lowness(), water;
	private float wetness, indoors;
	/** Expanse's wetness of the land at the camera (NaN without it), read with the land and eased toward every tick. */
	private float countryDownfall = Float.NaN;
	private int ticks;

	/** The air at the camera this tick ({@link AirSample#CLEAR} outside a world). */
	public AirSample here() {
		return this.here;
	}

	/** The model of this world's air (null outside a world), for whoever needs it somewhere other than the camera. */
	public Air air() {
		return this.air;
	}

	/** The level the air is for. */
	public ClientLevel level() {
		return this.level;
	}

	/** Vanilla's own rain level, before Gloam makes it regional. */
	public static float vanillaRain(ClientLevel level, float partialTick) {
		LevelAccessor raw = (LevelAccessor) level;
		return Mth.lerp(partialTick, raw.gloam$oRainLevel(), raw.gloam$rainLevel());
	}

	/** Vanilla's own thunder level (under its own rain), before Gloam makes it follow the storms. */
	public static float vanillaThunder(ClientLevel level, float partialTick) {
		LevelAccessor raw = (LevelAccessor) level;
		return Mth.lerp(partialTick, raw.gloam$oThunderLevel(), raw.gloam$thunderLevel()) * vanillaRain(level, partialTick);
	}

	/** Called once a client tick. */
	public void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null) {
			this.level = null;
			this.air = null;
			this.here = AirSample.CLEAR;
			return;
		}
		if (level != this.level) {
			this.level = level;
			long seed = ((BiomeManagerAccessor) level.getBiomeManager()).gloam$biomeZoomSeed();
			this.air = new Air(seed, GloamSettings.get().seasonDays);
			this.ticks = 0;
			this.wetness = 0;
		}
		Camera camera = mc.gameRenderer.mainCamera();
		Vec3 eye = camera.isInitialized() ? camera.position() : mc.player != null ? mc.player.getEyePosition() : Vec3.ZERO;
		BlockPos at = BlockPos.containing(eye);
		boolean first = this.ticks++ == 0;
		float ease = first ? 1.0F : 0.05F;

		Biome biome = level.getBiome(at).value();
		this.temperature += (biome.getBaseTemperature() - this.temperature) * ease;
		if (first || this.ticks % 10 == 0) {
			this.readLand(level, at, first);
		}
		float downfall = Float.isNaN(this.countryDownfall) ? biome.climateSettings.downfall() : this.countryDownfall;
		this.downfall += (downfall - this.downfall) * ease;
		this.indoors += (this.sky(level, at) - this.indoors) * (first ? 1.0F : 0.1F);
		Climate climate = new Climate(this.temperature, this.downfall, this.lowness, this.water);

		long clock = level.getOverworldClockTime();
		long gameTime = level.getGameTime();
		float year = this.air.year(clock);
		// the fronts, leaned on by the land's rain shadows as the bridge's weather tiles hold them (last tick's wind), so the
		// rain here, its sound and the shader pack's clouds agree
		float front = level.dimensionType().hasSkyLight()
			? RainShadow.front(this.air.front(eye.x, eye.z, gameTime, year, this.downfall), BridgeWorld.leanAt(eye.x, eye.z, this.here.wind())) : 0;
		float vanilla = vanillaRain(level, 1.0F);
		boolean precipitation = level.canHaveWeather() && biome.hasPrecipitation();
		float rain = precipitation ? Air.rain(vanilla, front, GloamSettings.get().showers) : 0;
		float storm = Math.max(Air.storm(front, year), vanillaThunder(level, 1.0F) * Noise.smoothstep(0.3F, 0.6F, front));
		// the ground soaks up rain in a few minutes and dries over most of a day, faster in the heat
		float drying = 1.0F / (12000.0F / (0.5F + Math.max(0, this.temperature)));
		this.wetness = rain > this.wetness ? Math.min(rain, this.wetness + rain / 1200.0F) : Math.max(0, this.wetness - drying);
		float mist = level.dimensionType().hasSkyLight() ? Air.mist(clock, year, climate, this.wetness) : 0;
		float ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
		this.here = new AirSample(year, Season.of(year), Air.warmth(year), Air.hour(clock), this.air.wind(gameTime, storm), front,
			Air.cloudCover(front, rain), rain, storm, mist, ground + Air.mistDepth(mist), this.wetness, climate, this.indoors);
	}

	/**
	 * How far below the land around it the camera's ground lies, and how near open water: from Expanse's countries
	 * where it has them (they know the valley before its chunks load, and the rain shadows), else off the heightmaps.
	 */
	private void readLand(ClientLevel level, BlockPos at, boolean first) {
		Expanse.Sampler country = Expanse.sampler(level);
		if (country != null) {
			Expanse.Land land = country.at(at.getX(), at.getZ());
			float ease = first ? 1.0F : 0.25F;
			// open water the heightmaps see (a pond, a flooded pit) still counts
			float water = Math.max(land.water(), this.waterNearby(level, at));
			this.lowness += (land.lowness() - this.lowness) * ease;
			this.water += (water - this.water) * ease;
			this.countryDownfall = land.downfall();
			return;
		}
		this.countryDownfall = Float.NaN;
		this.readHeightmaps(level, at, first);
	}

	/** Whether the camera's ground is water: the quick check where Expanse gives the rest. */
	private float waterNearby(ClientLevel level, BlockPos at) {
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
		return level.getFluidState(new BlockPos(at.getX(), ground - 1, at.getZ())).is(FluidTags.WATER) ? 1 : 0;
	}

	/** The land's shape and water read off the heightmaps of the loaded chunks round the camera. */
	private void readHeightmaps(ClientLevel level, BlockPos at, boolean first) {
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
		float around = 0;
		int wet = 0, samples = 0;
		for (int i = 0; i < 16; i++) {
			double angle = i * Math.PI / 8;
			for (int r : new int[]{RING / 3, RING}) {
				int x = at.getX() + (int) (Math.cos(angle) * r), z = at.getZ() + (int) (Math.sin(angle) * r);
				if (!level.hasChunk(x >> 4, z >> 4)) {
					continue;
				}
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				around += top;
				samples++;
				if (r < RING && level.getFluidState(new BlockPos(x, top - 1, z)).is(FluidTags.WATER)) {
					wet++;
				}
			}
		}
		if (samples == 0) {
			return;
		}
		float lowness = Noise.smoothstep(0.0F, 24.0F, around / samples - ground);
		float water = level.getFluidState(new BlockPos(at.getX(), ground - 1, at.getZ())).is(FluidTags.WATER) ? 1 : Math.min(1, wet / 5.0F);
		float ease = first ? 1.0F : 0.25F;
		this.lowness += (lowness - this.lowness) * ease;
		this.water += (water - this.water) * ease;
	}

	/** How enclosed the camera is: 0 under open sky, 1 with no sky in sight around it. */
	private float sky(ClientLevel level, BlockPos at) {
		if (!level.dimensionType().hasSkyLight()) {
			return 1;
		}
		int open = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (level.canSeeSky(at.offset(dx * 3, 1, dz * 3))) {
					open++;
				}
			}
		}
		return 1 - open / 9.0F;
	}

	public static Atmosphere get() {
		return Gloam.get().atmosphere();
	}
}
