package dev.voidmc.gloam.bridge;

import dev.voidmc.gloam.GloamSettings;
import dev.voidmc.gloam.air.Air;
import dev.voidmc.gloam.air.AirSample;
import dev.voidmc.gloam.air.Atmosphere;
import dev.voidmc.gloam.air.Noise;
import dev.voidmc.gloam.air.Wind;
import dev.voidmc.gloam.bridge.Contract.Slot;
import dev.voidmc.gloam.compat.Expanse;
import dev.voidmc.gloam.compat.ExpanseCaves;
import dev.voidmc.gloam.compat.Lod;
import dev.voidmc.gloam.far.FarAir;
import dev.voidmc.gloam.far.FarLand;
import dev.voidmc.gloam.season.SeasonColours;
import dev.voidmc.gloam.season.Seasons;
import dev.voidmc.gloam.sky.Haze;
import dev.voidmc.gloam.sky.Night;
import dev.voidmc.gloam.sky.Palette;
import dev.voidmc.gloam.sky.Twilight;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;

/**
 * The rest of the world's state for the VOID Welkin bridge (milestones M2 to M5), next to {@link Bridge}'s near land:
 * <ul>
 *   <li>the weather tiles W0 and W1 ({@link WeatherTiles}), with the rain shadow, which also leans the camera's own
 *       front ({@link #leanAt}), so they run with or without a pack;</li>
 *   <li>the far land tiles F0 and F1 ({@link FarTiles}), from Expanse's countries and VOID LOD's far land;</li>
 *   <li>the underground tile U ({@link UndergroundTile}), while the eye is underground;</li>
 *   <li>the uniforms {@link Bridge} does not set: {@code void_seasonState}, {@code void_maxStorm}, {@code void_haze},
 *       {@code void_hazeKind}, {@code void_climate}, {@code void_shelter}, {@code void_sky}, {@code void_cloudLayers},
 *       {@code void_underground}, {@code void_cave}, {@code void_farCentre}, {@code void_ugCentre}, and the per-frame
 *       {@code void_wind} and {@code void_weatherShift} ({@link #windNow}, {@link #weatherShiftNow}).</li>
 * </ul>
 * All on the client thread (which is the render thread), except the tiles' work, which runs on one worker of its own.
 * {@link Bridge#tick} calls {@link #values} and {@link #write} once a tick.
 */
public final class BridgeWorld {
	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "VOID Gloam bridge tiles");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});
	/** Lines a tick: W's rebuild (2 x 256) goes up over 4 ticks; F's fill as it comes. */
	private static final int WEATHER_LINES = 2 * Contract.TILE / WeatherTiles.SPREAD_FRAMES, FAR_LINES = 64;
	/** U's job budget with Expanse's caverns (cells), on the worker. */
	private static final int UG_BUDGET = 2048;

	static final WeatherTiles WEATHER = new WeatherTiles();
	static final FarTiles FAR = new FarTiles();
	static final UndergroundTile UNDERGROUND = new UndergroundTile();

	private static ClientLevel level;
	private static long ticks;
	// the land probes, kept per source so the tiles' caches live as long as the land does
	private static Expanse.RawSampler probeOf;
	private static LandRaster.Probe probe;
	private static ClientCaves clientCaves;
	// eased values
	private static float landHaze, landDust, temperature, canopy, roof, buried, ugDepth, ugOpen, glow, bats, drip, river;
	private static String hint;
	private static boolean first = true;
	private static float depthTarget;
	// the wind of the last two ticks, for the per-frame value
	private static Wind windBefore = Wind.CALM, windNow = Wind.CALM;
	private static Vector4f windOut = new Vector4f(1, 0, 0, 0), shiftOut = new Vector4f();
	private static final float[] SHIFT = new float[4];
	// the centres of the tiles as written
	private static int w0x, w0z, w1x, w1z, fx, fz, ux, uz;
	private static int mask;

	private BridgeWorld() {
	}

	// --------------------------------------------------------------------------------------------- once a tick

	/**
	 * Client thread, once a tick, from {@link Bridge}'s values (with or without a pack): sets this part's uniforms and
	 * keeps the weather tiles going.
	 */
	public static void values(Minecraft mc, ClientLevel lv) {
		Atmosphere atmosphere = Atmosphere.get();
		AirSample air = atmosphere.here();
		Air model = atmosphere.air();
		if (model == null || lv == null) {
			return;
		}
		if (lv != level) {
			reset();
			level = lv;
		}
		GloamSettings settings = GloamSettings.get();
		Camera camera = mc.gameRenderer.mainCamera();
		Vec3 eye = camera.isInitialized() ? camera.position() : mc.player != null ? mc.player.getEyePosition() : Vec3.ZERO;
		BlockPos at = BlockPos.containing(eye);
		long clock = lv.getOverworldClockTime();
		long gameTime = lv.getGameTime();
		boolean sky = lv.dimensionType().hasSkyLight();
		int sea = lv.getSeaLevel();
		float ease = first ? 1.0F : 0.05F;
		boolean scan = first || ticks % 10 == 0;
		ticks++;

		windBefore = windNow;
		windNow = air.wind();

		// the year on the land, and the snowline
		Holder<Biome> biome = lv.getBiome(at);
		float base = biome.value().getBaseTemperature();
		SeasonColours colours = Seasons.colours();
		boolean seasons = colours.on();
		float cold = seasons ? colours.cold() : 0, summer = seasons ? Math.max(0, colours.warmth()) : 0;
		Uniforms.set(Slot.SEASON_STATE, seasons ? colours.bare() : 0, seasons ? colours.turned() : 0, cold, SeasonSnow.height(base, sea, cold, summer));

		Uniforms.set(Slot.MAX_STORM, WEATHER.maxStorm());

		// the land's haze, eased over biome edges as the sky eases it; the country's every half second
		Expanse.RawSampler raw = Expanse.raw(lv);
		Expanse.Raw here = null;
		if (scan) {
			here = raw == null ? null : raw.at(at.getX(), at.getZ());
			hint = here == null ? null : here.haze();
		}
		Haze.Entry entry = biome.unwrapKey().map(k -> Haze.BIOMES.get(k.identifier())).orElse(null);
		Haze.Entry hinted = hint == null ? null : Haze.HINTS.get(hint);
		float biomeHaze = entry == null ? 0 : Haze.haziness(entry.reach());
		float hintHaze = hinted == null ? 0 : Haze.haziness(hinted.reach());
		Haze.Entry hazier = biomeHaze >= hintHaze ? entry : hinted;
		landHaze += (Math.max(biomeHaze, hintHaze) - landHaze) * ease;
		landDust += ((hazier != null && hazier.kind() == Haze.Kind.DUST ? 1 : 0) - landDust) * ease;
		float outdoors = 1 - air.indoors();
		float haze = sky ? landHaze * Haze.daily(landHaze, air.hour()) * (float) settings.haze * outdoors : 0;
		// void_haze.x is the haziness itself, 0..1 on Haze's log scale (after the hour's thinning and the setting): the
		// pack's haze, far weather and sky modules all read it so (wave-2 cross-check). AirReadout.logExtinction(x)
		// converts it to ln(extinction per block) where a module wants that.
		float inside = Noise.smoothstep(air.mistTop() + 4, air.mistTop() - 4, (float) eye.y);
		Uniforms.set(Slot.HAZE, Math.clamp(haze, 0F, 1F), landDust, AirReadout.curtain(air.mist(), inside, outdoors, haze),
			AirReadout.farThickness(air.climate().downfall(), air.warmth(), air.overcast(), air.wetness(), air.cloudCover(), air.mist(), settings.farHaze));
		int ground = lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
		int kind = hint != null ? Contract.hazeKind(hint) : entry != null && entry.kind() == Haze.Kind.DUST ? 5
			: TileCodec.hazeKind(TileCodec.temperature(base), air.climate().downfall(), air.climate().water(), air.climate().lowness(), ground - sea,
				biome.is(BiomeTags.IS_FOREST));
		Uniforms.set(Slot.HAZE_KIND, sky ? kind : 0);

		// the climate, on the tiles' scale: Expanse's temperature where it has one, so the camera matches N1
		if (here != null || raw == null) {
			float t = here != null ? here.temperature() : TileCodec.temperature(air.climate().temperature());
			temperature += (t - temperature) * (first ? 1.0F : 0.25F);
		}
		Uniforms.set(Slot.CLIMATE, temperature, air.climate().downfall(), air.climate().lowness(), air.climate().water());

		// shelter: the sky the camera sees, leaves overhead, a roof close above, the hill over a cave
		if (scan) {
			shelter(lv, eye, first);
		}
		Uniforms.set(Slot.SHELTER, air.indoors(), canopy, roof, buried);

		// dawn and dusk, in the palette's steps, as the sky's colour layers had them
		float cover = Math.max(air.cloudCover(), lv.getRainLevel(1.0F));
		float clear = Night.clear(cover);
		Uniforms.set(Slot.SKY, sky ? Palette.step(Twilight.golden(clock) * clear, 3) : 0, sky ? Palette.step(Twilight.blue(clock) * clear, 3) : 0,
			AirReadout.vivid(Twilight.vivid(model.seed(), clock, cover)), AirReadout.sunsetStep(clock));

		// the cloud decks for this world's height (gloam.far.maxY tries a tall world's layers on a lower one)
		float cloudHeight = lv.environmentAttributes().getValue(EnvironmentAttributes.CLOUD_HEIGHT, eye);
		int[] layers = AirReadout.cloudLayers(cloudHeight, Integer.getInteger("gloam.far.maxY", lv.getMaxY()), settings.farCloudLift);
		Uniforms.set(Slot.CLOUD_LAYERS, layers[0], layers[1], layers[2], layers[3]);

		// underground: how deep under the land the eye is, and what the caves say there
		depthTarget = 0;
		if (sky) {
			float over = ground - (float) eye.y;
			depthTarget = lv.canSeeSky(at) ? 0 : Noise.smoothstep(4.0F, 32.0F, over);
		}
		ugDepth += (depthTarget - ugDepth) * (first ? 1.0F : 0.1F);
		UndergroundTile.Eye cave = depthTarget > 0 ? UNDERGROUND.eye() : UndergroundTile.Eye.NONE;
		float caveEase = first ? 1.0F : 0.1F;
		ugOpen += (cave.openness() - ugOpen) * caveEase;
		glow += ((cave.glowworms() ? 1 : 0) - glow) * caveEase;
		bats += ((cave.bats() ? 1 : 0) - bats) * caveEase;
		drip += ((cave.drip() ? 1 : 0) - drip) * caveEase;
		river += (cave.river() - river) * caveEase;
		// void_underground and void_cave come from the soundscape's cave survey (gloam_sound's Hollow), so the fog and
		// the sound beds agree and the openness is the ring's (the pack's ugReachOpen assumes it), not the eye
		// sample's floor distance. The U job's own eye values above stay as a fallback for a Gloam without it.
		dev.voidmc.gloam.sound.Hollow hollow = dev.voidmc.gloam.sound.Soundscape.caves();
		if (hollow != null) {
			float[] u = hollow.undergroundUniform(), c = hollow.caveUniform();
			Uniforms.set(Slot.UNDERGROUND, u[0], u[1], u[2], u[3]);
			Uniforms.set(Slot.CAVE, c[0], c[1], c[2], c[3]);
		} else {
			Uniforms.set(Slot.UNDERGROUND, ugDepth, ugOpen, cave.realm(), cave.water());
			Uniforms.set(Slot.CAVE, glow, bats, drip, river);
		}

		// the weather tiles run with or without a pack: the camera's rain leans on their land too
		if (sky && settings.enabled) {
			double reach = Math.max(Lod.active() ? Lod.radius() * 1.15 : 0, mc.options.cloudRange().get() * 16.0);
			WEATHER.tick(new WeatherTiles.Inputs(model, gameTime, air.year(), Atmosphere.vanillaRain(lv, 1.0F), Atmosphere.vanillaThunder(lv, 1.0F),
				settings.showers, air.wind().x(), air.wind().z(), eye.x, eye.z, air.climate().downfall(), probe(lv, raw, sea), reach), WORKER);
		}
		first = false;
	}

	/**
	 * Client thread, once a tick (pack on or not, so the tiles are whole when one comes on): starts the far and
	 * underground jobs while a pack is on, writes whatever the workers finished into the texture's bytes, and the tiles'
	 * centres into the header. Returns the header's tile-mask bits for W0, W1, F0, F1 and U.
	 */
	public static int write(Minecraft mc, ClientLevel lv, byte[] bytes, LandTiles.Dirty dirty, boolean packOn) {
		if (lv == null || lv != level) {
			return 0;
		}
		Atmosphere atmosphere = Atmosphere.get();
		Camera camera = mc.gameRenderer.mainCamera();
		Vec3 eye = camera.isInitialized() ? camera.position() : mc.player != null ? mc.player.getEyePosition() : Vec3.ZERO;
		boolean sky = lv.dimensionType().hasSkyLight();
		if (packOn && sky) {
			SeasonColours colours = Seasons.colours();
			float cold = colours.on() ? colours.cold() : 0, summer = colours.on() ? Math.max(0, colours.warmth()) : 0;
			FarLand land = Lod.LOADED ? FarAir.land() : null;
			FAR.tick(new FarTiles.Inputs(lv, Expanse.raw(lv), land, eye.x, eye.z, atmosphere.here().wetness(), cold, summer, lv.getSeaLevel(), ticks),
				WORKER);
			if (depthTarget > 0 && ticks % UndergroundTile.JOB_TICKS == 0) {
				UndergroundTile.CaveSource caves = ExpanseCaves.source(lv);
				if (caves != null) {
					UNDERGROUND.schedule(new UndergroundTile.Inputs(lv, caves, eye.x, eye.y, eye.z, UG_BUDGET), WORKER);
				} else {
					if (clientCaves == null || clientCaves.level != lv) {
						clientCaves = new ClientCaves(lv);
					}
					UNDERGROUND.schedule(new UndergroundTile.Inputs(lv, clientCaves, eye.x, eye.y, eye.z, ClientCaves.BUDGET), Runnable::run);
				}
			}
		}
		WEATHER.queue.drain(bytes, dirty, WEATHER_LINES, BridgeWorld::written);
		FAR.queue.drain(bytes, dirty, FAR_LINES, BridgeWorld::written);
		UNDERGROUND.queue.drain(bytes, dirty, Integer.MAX_VALUE, BridgeWorld::written);
		Contract.centre(bytes, Contract.W0, w0x, w0z);
		Contract.centre(bytes, Contract.W1, w1x, w1z);
		Contract.centre(bytes, Contract.F0, fx, fz);
		Contract.centre(bytes, Contract.F1, fx, fz);
		Contract.centre(bytes, Contract.U, ux, uz);
		return mask;
	}

	/** A strip went into the texture: its tile's centre is the strip's now, and the uniforms follow. */
	private static void written(TileStrip s) {
		int bits = 1 << s.tileA() | (s.tileB() >= 0 ? 1 << s.tileB() : 0);
		mask = s.clear() ? mask & ~bits : mask | bits;
		switch (s.tileA()) {
			case Contract.W0 -> {
				w0x = s.centreX();
				w0z = s.centreZ();
			}
			case Contract.W1 -> {
				w1x = s.centreX();
				w1z = s.centreZ();
			}
			case Contract.F0 -> {
				fx = s.centreX();
				fz = s.centreZ();
				Uniforms.set(Slot.FAR_CENTRE, fx, fz);
			}
			case Contract.U -> {
				ux = s.centreX();
				uz = s.centreZ();
				Uniforms.set(Slot.UG_CENTRE, ux * TileCodec.UG_CELL, s.base(), uz * TileCodec.UG_CELL);
			}
			default -> {
			}
		}
	}

	/** Leaves overhead, a roof close above and the hill over a cave, as the soundscape's survey reads them. */
	private static void shelter(ClientLevel lv, Vec3 eye, boolean at) {
		int ex = (int) Math.floor(eye.x), ez = (int) Math.floor(eye.z);
		if (!lv.hasChunk(ex >> 4, ez >> 4)) {
			return;
		}
		int leaves = 0;
		for (int dx = -2; dx <= 2; dx += 2) {
			for (int dz = -2; dz <= 2; dz += 2) {
				int top = lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ex + dx, ez + dz);
				int crown = lv.getHeight(Heightmap.Types.MOTION_BLOCKING, ex + dx, ez + dz);
				if (crown > top && crown > eye.y + 1) {
					leaves++;
				}
			}
		}
		float cover = lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ex, ez) - (float) eye.y;
		float r = cover > 1 ? 1 - Noise.smoothstep(10, 20, cover) : 0;
		float b = Noise.smoothstep(8, 24, cover);
		float e = at ? 1.0F : 0.5F;
		canopy += (leaves / 9.0F - canopy) * e;
		roof += (r - roof) * e;
		buried += (b - buried) * e;
	}

	/** The land probe for the weather tiles: Expanse's countries, else VOID LOD's far land, else none. */
	private static LandRaster.Probe probe(ClientLevel lv, Expanse.RawSampler raw, int sea) {
		if (raw != null) {
			if (raw != probeOf || probe == null) {
				probeOf = raw;
				// Expanse's far-view model: the lattices are 512 and 4096 blocks, and reach some 150 km round
				probe = (x, z, out) -> {
					Expanse.Raw r = raw.far(x, z);
					out[0] = r.coastDistance() < 0 ? Math.max(r.altitude(), sea) : r.altitude();
					out[1] = r.humidity();
				};
			}
			return probe;
		}
		FarLand land = Lod.LOADED ? FarAir.land() : null;
		if (land == null || !land.known()) {
			return null;
		}
		if (probeOf != null || probe == null) {
			probeOf = null;
			probe = (x, z, out) -> out[0] = land.groundAt(x, z, Float.NaN);
		}
		return probe;
	}

	// --------------------------------------------------------------------------------------------- reads

	/**
	 * The land's lean on the front at the camera ({@link RainShadow}), for {@code Atmosphere}: the rain shadow the
	 * weather tiles hold, so the camera's rain, its sound and the pack's clouds agree. 0 before the first rebuild.
	 */
	public static float leanAt(double x, double z, Wind wind) {
		return WEATHER.leanAt(x, z, wind.x(), wind.z());
	}

	/** {@code void_wind} this frame: the last two ticks' wind, interpolated. Render thread. */
	public static Vector4f windNow() {
		float t = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Wind a = windBefore, b = windNow;
		float x = a.x() + (b.x() - a.x()) * t, z = a.z() + (b.z() - a.z()) * t;
		float len = (float) Math.sqrt(x * x + z * z);
		if (len > 1e-4F) {
			x /= len;
			z /= len;
		} else {
			x = b.x();
			z = b.z();
		}
		float speed = a.speed() + (b.speed() - a.speed()) * t, gust = a.gust() + (b.gust() - a.gust()) * t;
		Vector4f v = windOut;
		if (v.x != x || v.y != z || v.z != speed || v.w != gust) {
			windOut = v = new Vector4f(x, z, speed, gust);
		}
		return v;
	}

	/** {@code void_weatherShift} this frame, from the game time and the partial tick. Render thread. */
	public static Vector4f weatherShiftNow() {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel lv = mc.level;
		if (lv == null) {
			return shiftOut;
		}
		double seconds = (lv.getGameTime() + (double) mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)) / 20.0;
		float[] s = TileCodec.weatherShift(seconds, SHIFT);
		Vector4f v = shiftOut;
		if (v.x != s[0] || v.y != s[1] || v.z != s[2] || v.w != s[3]) {
			shiftOut = v = new Vector4f(s[0], s[1], s[2], s[3]);
		}
		return v;
	}

	/** The tick values of the per-frame uniforms, for the header's mirror (coarser, but enough for the fallback). */
	public static void mirrorFrameValues() {
		Wind w = windNow;
		Uniforms.set(Slot.WIND, w.x(), w.z(), w.speed(), w.gust());
		ClientLevel lv = level;
		if (lv != null) {
			float[] s = TileCodec.weatherShift(lv.getGameTime() / 20.0, SHIFT);
			Uniforms.set(Slot.WEATHER_SHIFT, s[0], s[1], s[2], s[3]);
		}
	}

	/** Forgets the world. Client thread. */
	public static void reset() {
		level = null;
		first = true;
		hint = null;
		landHaze = landDust = temperature = canopy = roof = buried = ugDepth = ugOpen = glow = bats = drip = river = 0;
		depthTarget = 0;
		windBefore = windNow = Wind.CALM;
		mask = 0;
		probeOf = null;
		probe = null;
		clientCaves = null;
		WEATHER.reset();
		FAR.reset();
		UNDERGROUND.reset();
	}

	/** The header's tile-mask bits this part holds (W0, W1, F0, F1, U). */
	static int mask() {
		return mask;
	}

	/** One line for the showcase's log. */
	public static String stats() {
		return String.format(Locale.ROOT, "%s; %s; %s; tiles %s", WEATHER.stats(), FAR.stats(), UNDERGROUND.stats(), Integer.toBinaryString(mask));
	}
}
