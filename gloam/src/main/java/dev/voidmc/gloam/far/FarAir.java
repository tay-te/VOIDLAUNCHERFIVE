package dev.voidmc.gloam.far;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.commands.RenderPass;
import dev.voidmc.gloam.GloamSettings;
import dev.voidmc.gloam.air.Air;
import dev.voidmc.gloam.air.AirSample;
import dev.voidmc.gloam.air.Atmosphere;
import dev.voidmc.gloam.air.Noise;
import dev.voidmc.gloam.compat.Lod;
import dev.voidmc.gloam.compat.Shaders;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryUtil;

/**
 * The air past the render distance, over VOID LOD's far terrain: the cloud deck out to the horizon, storms and rain
 * curtains seen kilometres off, and morning mist lying in the far valleys. All of it comes from the same model as the
 * air at the camera ({@link Air}): the clouds from the fronts, the curtains from where they rain, the mist from each far
 * place's own climate.
 *
 * <p>Gloam's cloud deck replaces vanilla's clouds (vanilla's stop at the cloud distance and ignore the weather): it keeps
 * the cloud option (off, fast: one soft layer, fancy: heaps with depth), {@code CLOUD_HEIGHT} as its base and
 * {@code CLOUD_COLOR} as its sunlit colour. With a shader pack on, the pack draws its own sky and clouds, and the far air
 * stays out of its picture.
 */
public final class FarAir {
	private static final FarAir INSTANCE = new FarAir();

	private final WeatherMap weather = new WeatherMap();
	private final FarLand land = new FarLand();
	private final FarMist mist = new FarMist();
	private final FarRain rain = new FarRain();
	private final ByteBuffer info = MemoryUtil.memAlloc(FarRenderer.INFO_BYTES);
	private FarRenderer renderer;
	private ClientLevel level;
	private WeatherMap.Picture stormsOf;
	/** The strongest storm in the current weather map within reach: how high the clouds can tower in view. */
	private float maxStorm;
	private int mistAnchorX, mistAnchorZ;
	/** Whether Gloam draws the clouds this frame, in place of vanilla's (read by the cloud renderer mixin). */
	private boolean replacing;
	// statistics
	private double prepareMillis;
	private int curtains;
	private String cloudState = "";

	/** This frame's terrain fog, which the deck's half-resolution pass draws with. */
	private com.mojang.renderpearl.api.buffers.GpuBufferSlice fog;

	private FarAir() {
	}

	/** Client start. Reference VOID LOD's classes only when it is loaded. */
	public static void init() {
		if (Lod.LOADED) {
			INSTANCE.land.listen();
		}
	}

	/** Whether Gloam's cloud deck stands in for vanilla's clouds this frame. */
	public static boolean replacesClouds() {
		return INSTANCE.replacing;
	}

	/** Whether the far air is wanted at all: Gloam on, the far air on, a level with an open sky, no shader pack. */
	private static boolean wanted(ClientLevel level) {
		GloamSettings s = GloamSettings.get();
		return s.enabled && s.far && level != null && Atmosphere.get().air() != null && level.dimensionType().hasSkyLight()
			&& !level.dimensionType().hasCeiling() && !Shaders.packInUse();
	}

	/**
	 * Works out and uploads this frame's far air. Called by {@code LevelRendererFarMixin} while the frame graph is built,
	 * before any pass runs (as VOID LOD prepares its own).
	 */
	public static void prepare(LevelRenderState state, com.mojang.renderpearl.api.buffers.GpuBufferSlice fog) {
		INSTANCE.fog = fog;
		INSTANCE.prepareFrame(state);
	}

	/** Draws the far air into vanilla's main pass, before the weather (where vanilla draws its clouds). */
	public static void draw(RenderPass pass) {
		FarRenderer r = INSTANCE.renderer;
		if (r != null) {
			r.draw(pass);
		}
	}

	/**
	 * Draws the far air in a pass of its own over the main target, for vanilla's improved transparency, whose clouds and
	 * weather are drawn in passes the far air cannot join: after them, tested against the world's depth.
	 */
	public static void drawOwnPass(RenderTarget target) {
		FarRenderer r = INSTANCE.renderer;
		if (r == null || !r.anything()) {
			return;
		}
		var encoder = com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder();
		try (RenderPass pass = encoder.createRenderPass(() -> "VOID Gloam far air", target.getColorTextureView(), Optional.empty(),
			target.getDepthTextureView(), OptionalDouble.empty())) {
			com.mojang.blaze3d.systems.RenderSystem.bindDefaultUniforms(pass);
			r.draw(pass);
		}
	}

	/** One line on the far air's cost and content, for the logs and the showcase. */
	public static String stats() {
		FarRenderer r = INSTANCE.renderer;
		return String.format(Locale.ROOT, "far air: GPU %.2f ms, prepare %.3f ms, %d mist quads, %d curtains, max storm %.2f, %s, %d draws",
			r == null ? -1 : r.gpuMillis, INSTANCE.prepareMillis, r == null ? 0 : r.mistQuads(), INSTANCE.curtains, INSTANCE.maxStorm,
			INSTANCE.cloudState, r == null ? 0 : r.draws);
	}

	/** The front the far air's weather map holds over world (x, z) now (for the showcase), or -1 without one. */
	public static float frontAt(double x, double z) {
		WeatherMap.Picture map = INSTANCE.weather.current();
		ClientLevel level = INSTANCE.level;
		if (map == null || level == null) {
			return -1;
		}
		long t = level.getGameTime();
		return map.at(WeatherMap.frameX(x, t), WeatherMap.frameZ(z, t));
	}

	/** The far land the air has been told of (for the showcase: whether VOID LOD's tiles reached it). */
	public static boolean landKnown() {
		return INSTANCE.land.known();
	}

	private void prepareFrame(LevelRenderState state) {
		long t0 = System.nanoTime();
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level != this.level) {
			this.level = level;
			this.weather.reset();
			this.land.reset();
			this.mist.reset();
			this.stormsOf = null;
		}
		CameraRenderState camera = state.cameraRenderState;
		boolean open = camera.fogType == FogType.NONE && !camera.entityRenderState.doesMobEffectBlockSky;
		if (!wanted(level) || !open) {
			this.replacing = false;
			this.setHaze(1.0F);
			this.setShade(false, 0, 0, 0);
			if (this.renderer != null) {
				this.renderer.skip();
			}
			return;
		}
		if (this.renderer == null) {
			this.renderer = new FarRenderer();
		}
		GloamSettings settings = GloamSettings.get();
		Air air = Atmosphere.get().air();
		AirSample here = Atmosphere.get().here();
		this.setHaze(1.0F + (hazeThickness(here) - 1.0F) * (float) settings.farHaze);
		double cx = camera.pos.x, cy = camera.pos.y, cz = camera.pos.z;
		double ticks = state.gameTime + state.worldPartialTicks;
		long clock = level.getOverworldClockTime();
		float year = air.year(clock);
		float partial = state.worldPartialTicks;

		double lodRadius = Lod.active() ? Lod.radius() : 0;
		int renderDistance = mc.options.getEffectiveRenderDistance() * 16;
		double reach = Math.max(lodRadius * 1.15, mc.options.cloudRange().get() * 16.0);
		double clip = lodRadius > 0 ? Math.max(0, renderDistance - 24) : renderDistance;

		// the fronts' picture
		this.weather.update(air, cx, cz, state.gameTime, year, here.climate().downfall());
		WeatherMap.Picture map = this.weather.current();
		if (map != null && map != this.stormsOf) {
			this.stormsOf = map;
			this.maxStorm = maxStorm(map, WeatherMap.frameX(cx, state.gameTime), WeatherMap.frameZ(cz, state.gameTime), reach, year);
		}

		CloudStatus status = mc.options.getCloudStatus();
		boolean clouds = settings.farClouds && map != null && status != CloudStatus.OFF && ARGB.alpha(state.cloudColor) > 0;
		this.replacing = settings.farClouds;
		boolean fast = status == CloudStatus.FAST;
		this.cloudState = String.format(Locale.ROOT, "clouds %s (%s, colour %08x, base %.0f, map %s)", clouds ? "on" : "off", status,
			state.cloudColor, state.cloudHeight, map != null);

		// the far land and its mist
		boolean mistOn = settings.farMist && this.land.known();
		if (mistOn) {
			this.land.trim(cx, cz, reach * 1.2);
			this.mist.update(this.land, air, cx, cz, Math.min(reach, Math.max(lodRadius, 2048)), clock, here.wetness());
		}
		FarMist.Mesh mesh = this.mist.take();
		if (mesh != null) {
			this.mistAnchorX = mesh.anchorX();
			this.mistAnchorZ = mesh.anchorZ();
		}

		// the curtains
		float vanilla = Atmosphere.vanillaRain(level, partial);
		float thunder = level.getThunderLevel(partial);
		boolean rainOn = settings.farRain && map != null;
		float base = state.cloudHeight + (float) settings.farCloudLift;
		if (rainOn) {
			this.rain.build(map, this.land, cx, cy, cz, ticks, reach, base, vanilla, settings.showers, year, thunder, air.seed());
		}
		this.curtains = rainOn ? this.rain.count() : 0;

		// the cloud shadows' picture: square around the camera, its texels fixed to the world as the camera moves
		boolean shadows = clouds && lodRadius > 0;
		double shadeSize = 2 * Math.min(Math.max(lodRadius, 4096), 16384);
		double texel = shadeSize / FarRenderer.SHADE_SIZE;
		double shadeX = Math.floor((cx - shadeSize / 2) / texel) * texel, shadeZ = Math.floor((cz - shadeSize / 2) / texel) * texel;
		this.fillInfo(state, camera, map, reach, clip, base, vanilla, settings.showers, year, thunder, ticks, shadeX - cx, shadeZ - cz, shadeSize,
			level.getSeaLevel() + 30);
		ByteBuffer texels = this.weather.takeUpload();
		try {
			var target = mc.gameRenderer.mainRenderTarget();
			this.renderer.prepare(this.info, texels, mesh, this.rain, clouds, fast, mistOn, rainOn, shadows, this.fog, target.width, target.height);
			this.setShade(shadows, shadeX, shadeZ, shadeSize);
		} finally {
			if (texels != null) {
				MemoryUtil.memFree(texels);
			}
			if (mesh != null) {
				MemoryUtil.memFree(mesh.vertices());
			}
		}
		double ms = (System.nanoTime() - t0) / 1e6;
		this.prepareMillis = this.prepareMillis == 0 ? ms : this.prepareMillis * 0.95 + ms * 0.05;
	}

	/**
	 * How thick VOID LOD's haze is for the air at the camera, against its own: a humid lowland in summer is hazier (the
	 * distances full of air), winter and high cold air clearer, an overcast greyer, the air washed by recent rain under
	 * a clearing sky clearest, and the morning mist thickens the low haze while it lies.
	 */
	static float hazeThickness(AirSample here) {
		float humid = Math.clamp(here.climate().downfall(), 0, 1);
		float thick = 1.0F + 0.4F * humid * Math.max(0, here.warmth()) - 0.2F * Math.max(0, -here.warmth());
		thick *= 1.0F + 0.25F * here.overcast();
		thick *= 1.0F - 0.3F * here.wetness() * (1.0F - here.cloudCover());
		thick += 0.5F * here.mist();
		return Math.clamp(thick, 0.6F, 2.0F);
	}

	private float haze = 1.0F;
	private boolean shading;

	/** Lays the cloud shadows' picture over VOID LOD's land, or takes it off. */
	private void setShade(boolean on, double x, double z, double size) {
		if (on) {
			Lod.setShade(this.renderer.shadeView(), x, z, size, 1.0F);
			this.shading = true;
		} else if (this.shading) {
			Lod.setShade(null, 0, 0, 0, 0);
			this.shading = false;
		}
	}

	private void setHaze(float thickness) {
		if (thickness != this.haze) {
			this.haze = thickness;
			Lod.setHazeThickness(thickness);
		}
	}

	/** Fills the uniforms ({@code farinfo.glsl}). */
	private void fillInfo(LevelRenderState state, CameraRenderState camera, WeatherMap.Picture map, double reach, double clip, float base, float vanilla,
		boolean showers, float year, float thunder, double ticks, double shadeX, double shadeZ, double shadeSize, float ground) {
		ByteBuffer b = this.info;
		b.clear();
		camera.viewRotationMatrix.get(0, b);
		b.position(64);
		double cx = camera.pos.x, cy = camera.pos.y, cz = camera.pos.z;
		// FarCamera
		b.putFloat((float) cy).putFloat((float) ((ticks / 20.0) % 3600.0)).putFloat((float) clip).putFloat((float) reach);
		// FarAnchor
		b.putFloat((float) (cx - this.mistAnchorX)).putFloat((float) cy).putFloat((float) (cz - this.mistAnchorZ)).putFloat((float) (reach * 0.6));
		// FarSun: vanilla's sky turns the sun about the x axis
		float angle = state.skyRenderState.sunAngle;
		float sunX = -(float) Math.sin(angle), sunY = (float) Math.cos(angle);
		b.putFloat(sunX).putFloat(sunY).putFloat(0).putFloat(Math.clamp(sunY * 4 + 0.2F, 0, 1));
		// FarCloudColor
		int color = state.cloudColor;
		b.putFloat(ARGB.red(color) / 255.0F).putFloat(ARGB.green(color) / 255.0F).putFloat(ARGB.blue(color) / 255.0F).putFloat(ARGB.alpha(color) / 255.0F);
		// FarSunrise
		Vector4fc sunrise = state.skyRenderState.sunriseAndSunsetColor;
		if (sunrise != null) {
			b.putFloat(sunrise.x()).putFloat(sunrise.y()).putFloat(sunrise.z()).putFloat(sunrise.w());
		} else {
			b.putFloat(0).putFloat(0).putFloat(0).putFloat(0);
		}
		// FarLayer: the highest a storm in view tops the base by; fancy or fast; the sun's side
		b.putFloat(base).putFloat(160 + 1240 * this.maxStorm).putFloat(1).putFloat(sunX >= 0 ? 1 : -1);
		// FarWeather
		b.putFloat(vanilla).putFloat(showers ? 1 : 0).putFloat(Math.max(0, Air.warmth(year))).putFloat(thunder);
		// FarMap
		double fx = cx - Air.FRONT_DRIFT * (ticks / 20.0), fz = cz - Air.FRONT_DRIFT * 0.2 * (ticks / 20.0);
		if (map != null) {
			b.putFloat((float) (fx - map.originX())).putFloat((float) (fz - map.originZ())).putFloat(1.0F / (WeatherMap.SIZE * WeatherMap.TEXEL)).putFloat(0);
		} else {
			b.putFloat(0).putFloat(0).putFloat(0).putFloat(0);
		}
		// FarNoise
		b.putFloat((float) wrap(fx)).putFloat((float) wrap(fz)).putFloat((float) wrap(cx)).putFloat((float) wrap(cz));
		// FarShade
		b.putFloat((float) shadeX).putFloat((float) shadeZ).putFloat((float) shadeSize).putFloat(ground);
		b.flip();
	}

	private static double wrap(double v) {
		return v - Math.floor(v / 65536.0) * 65536.0;
	}

	/** The strongest storm within {@code reach} of (fx, fz) in the fronts' frame. */
	private static float maxStorm(WeatherMap.Picture map, double fx, double fz, double reach, float year) {
		float best = 0;
		for (int z = 0; z < WeatherMap.SIZE; z += 2) {
			for (int x = 0; x < WeatherMap.SIZE; x += 2) {
				double px = map.originX() + (x + 0.5) * WeatherMap.TEXEL - fx, pz = map.originZ() + (z + 0.5) * WeatherMap.TEXEL - fz;
				if (px * px + pz * pz <= reach * reach) {
					best = Math.max(best, Air.storm(map.fronts()[z * WeatherMap.SIZE + x], year));
				}
			}
		}
		return Noise.smoothstep(0.0F, 0.5F, best);
	}
}
