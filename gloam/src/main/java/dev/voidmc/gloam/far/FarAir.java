package dev.voidmc.gloam.far;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.commands.RenderPass;
import dev.voidmc.gloam.GloamSettings;
import dev.voidmc.gloam.air.Air;
import dev.voidmc.gloam.air.AirSample;
import dev.voidmc.gloam.air.Atmosphere;
import dev.voidmc.gloam.air.Noise;
import dev.voidmc.gloam.compat.Expanse;
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
 * The air past the render distance, over VOID LOD's far terrain, and the clouds: Minecraft's clouds laid out by the
 * fronts out to the horizon ({@link CloudGrid}, drawn by vanilla's own renderer), rain curtains under storms and
 * showers kilometres off, and morning mist lying in the far valleys. All of it comes from the same model as the air at
 * the camera ({@link Air}): the clouds from the fronts, the curtains from where they rain, the mist from each far place's
 * own climate.
 */
public final class FarAir {
	private static final FarAir INSTANCE = new FarAir();

	private final WeatherMap weather = new WeatherMap();
	private final FarLand land = new FarLand();
	private final FarMist mist = new FarMist();
	private final FarRain rain = new FarRain();
	private final CloudGrid grid = new CloudGrid();
	private CloudDeck deck;
	/** How far Gloam's clouds reach this frame (0 while vanilla's are drawn), for the clouds' fog. */
	private double cloudReach;
	private float cloudBase;
	private CloudGrid.Layers layers;
	/** The sun's side and height and the sunrise colour for the clouds' low-sun light; [5] 0 with a shader pack. */
	private final float[] sunLight = new float[6];
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
			Lod.onPackFrame(INSTANCE::drawOverPack);
		}
	}

	/** Whether Gloam's cells stand in for vanilla's in vanilla's cloud renderer this frame. */
	public static boolean replacesClouds() {
		return INSTANCE.replacing;
	}

	/** How far Gloam's clouds reach this frame, in blocks (0 while vanilla's are drawn), so their fog can reach as far. */
	public static double cloudReach() {
		return INSTANCE.replacing ? INSTANCE.cloudReach : 0;
	}

	/**
	 * Vanilla's cloud renderer prepares its clouds (CloudRendererMixin): the places of Gloam's cells for this frame, from
	 * the camera and the game time vanilla was given. Outside any pass. Whether there is anything to draw.
	 */
	public static boolean prepareClouds(int color, net.minecraft.world.phys.Vec3 camera, long gameTime, float partialTicks) {
		CloudDeck deck = INSTANCE.deck;
		if (deck == null) {
			return false;
		}
		double seconds = (gameTime + partialTicks) / 20.0;
		double fx = camera.x - Air.FRONT_DRIFT * seconds, fz = camera.z - Air.FRONT_DRIFT * 0.2 * seconds;
		deck.prepare(fx, fz, camera.y - INSTANCE.cloudBase, color, INSTANCE.sunLight);
		return deck.anything();
	}

	/** Vanilla's cloud renderer draws its clouds with {@code pipeline}: Gloam's cells instead. */
	public static void drawClouds(RenderPass pass, com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline) {
		CloudDeck deck = INSTANCE.deck;
		if (deck != null) {
			deck.draw(pass, pipeline);
		}
	}

	/** Whether the far air is wanted at all: Gloam on, the far air on, a level with an open sky. */
	private static boolean wanted(ClientLevel level) {
		GloamSettings s = GloamSettings.get();
		return s.enabled && s.far && level != null && Atmosphere.get().air() != null && level.dimensionType().hasSkyLight()
			&& !level.dimensionType().hasCeiling();
	}

	/**
	 * Whether the mist and the curtains are drawn: always without a shader pack; with one, only when VOID LOD hands over
	 * the far land's depth after the pack's picture (they are drawn over it then).
	 */
	private static boolean belowClouds() {
		return !Shaders.packInUse() || (Lod.LOADED && GloamSettings.get().farOverPack);
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
		if (r != null && !Shaders.packInUse()) {
			r.draw(pass);
		}
	}

	/** Draws the far air over a shader pack's finished picture, against the far land's depth the pack drew. */
	private void drawOverPack(Lod.PackDepth depth) {
		FarRenderer r = this.renderer;
		if (r == null || !Shaders.packInUse() || this.camera == null) {
			return;
		}
		Shaders.withoutPackShaders(() -> r.drawOverPack(Minecraft.getInstance().gameRenderer.mainRenderTarget(), depth.glTexture(), depth.projection(),
			this.camera, depth.width(), depth.height(), this.fog));
	}

	/** This frame's camera projection, for the far depth's reprojection. */
	private org.joml.Matrix4f camera;

	/**
	 * Draws the far air in a pass of its own over the main target, for vanilla's improved transparency, whose clouds and
	 * weather are drawn in passes the far air cannot join: after them, tested against the world's depth.
	 */
	public static void drawOwnPass(RenderTarget target) {
		FarRenderer r = INSTANCE.renderer;
		if (r == null || !r.anything() || Shaders.packInUse()) {
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
		return String.format(Locale.ROOT, "far air: GPU %.2f ms (mist and curtains), prepare %.3f ms, %d mist quads, %d curtains, max storm %.2f, %s, %d draws",
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

	/**
	 * The cloud decks Gloam's clouds are laid out in, in world y: {low, main, high}, -1 for a deck the world has none of;
	 * null while Gloam is not drawing the clouds (vanilla's one deck at its cloud height then).
	 */
	public static int[] cloudDecks() {
		CloudGrid.Layers l = INSTANCE.layers;
		return l == null || !INSTANCE.replacing ? null : new int[]{l.low(), l.main(), l.high()};
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
			this.grid.reset();
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
		Expanse.Sampler country = Expanse.sampler(level);
		this.weather.update(air, cx, cz, state.gameTime, year, here.climate().downfall(), country);
		WeatherMap.Picture map = this.weather.current();
		if (map != null && map != this.stormsOf) {
			this.stormsOf = map;
			this.maxStorm = maxStorm(map, WeatherMap.frameX(cx, state.gameTime), WeatherMap.frameZ(cz, state.gameTime), reach, year);
		}

		// the clouds: vanilla's renderer draws Gloam's cells (as vanilla draws them: Iris forces them off for a shader pack
		// that draws clouds of its own, which then keeps its own)
		CloudStatus status = mc.options.getCloudStatus();
		boolean clouds = settings.farClouds && map != null && status != CloudStatus.OFF && ARGB.alpha(state.cloudColor) > 0;
		this.replacing = settings.farClouds;
		float vanilla = Atmosphere.vanillaRain(level, partial);
		float thunder = level.getThunderLevel(partial);
		// the cloud layers, fitted to the world's height (or one deck farCloudLift over vanilla's, set by hand)
		// (gloam.far.maxY lays the clouds out as for a world that tall, to try a tall world's layers on a lower one)
		CloudGrid.Layers layers = CloudGrid.Layers.of(state.cloudHeight, Integer.getInteger("gloam.far.maxY", level.getMaxY()), settings.farCloudLift);
		float base = layers.base();
		this.cloudBase = base;
		this.layers = layers;
		// the low sun's light on the clouds, without a shader pack (a pack lights them itself)
		float sunAngle0 = state.skyRenderState.sunAngle;
		org.joml.Vector4fc rise = state.skyRenderState.sunriseAndSunsetColor;
		this.sunLight[0] = -(float) Math.sin(sunAngle0);
		this.sunLight[1] = (float) Math.cos(sunAngle0);
		this.sunLight[2] = rise == null ? 1 : rise.x();
		this.sunLight[3] = rise == null ? 1 : rise.y();
		this.sunLight[4] = rise == null ? 1 : rise.z();
		this.sunLight[5] = rise == null || Shaders.packInUse() ? 0 : rise.w();
		// the deck reaches a little past the far land (inside the far plane, which VOID LOD puts at 1.5 times its radius),
		// thinning into the sky all the way out
		this.cloudReach = lodRadius > 0 ? lodRadius * 1.45 : reach;
		double fx = WeatherMap.frameX(cx, state.gameTime), fz = WeatherMap.frameZ(cz, state.gameTime);
		if (this.replacing && map != null) {
			double near = Math.min(mc.options.cloudRange().get() * 16.0, 1536);
			this.grid.update(new CloudGrid.Input(map, air.seed(), year, vanilla, settings.showers, thunder, fx, fz, cx - fx, cz - fz, cy, layers,
				this.land, near, this.cloudReach, status == CloudStatus.FAST));
		}
		CloudGrid.Mesh cells = this.grid.take();
		if (cells != null) {
			try {
				if (this.deck == null) {
					this.deck = new CloudDeck();
				}
				this.deck.accept(cells);
			} finally {
				cells.free();
			}
		}
		this.cloudState = String.format(Locale.ROOT, "clouds %s (%s, colour %08x, layers %s, %d faces in %d draws)", clouds ? "on" : "off", status,
			state.cloudColor, layers, this.deck == null ? 0 : this.deck.faceCount(), this.deck == null ? 0 : this.deck.groupCount());

		// the far land and its mist
		boolean mistOn = settings.farMist && this.land.known();
		if (mistOn) {
			this.land.trim(cx, cz, reach * 1.2);
			this.mist.update(this.land, air, cx, cz, Math.min(reach, Math.max(lodRadius, 2048)), clock, here.wetness(), country);
		}
		FarMist.Mesh mesh = this.mist.take();
		if (mesh != null) {
			this.mistAnchorX = mesh.anchorX();
			this.mistAnchorZ = mesh.anchorZ();
		}

		// the curtains
		boolean rainOn = settings.farRain && map != null;
		if (rainOn) {
			this.rain.build(map, this.land, cx, cy, cz, ticks, reach, base, vanilla, settings.showers, year, thunder, air.seed());
		}
		this.curtains = rainOn ? this.rain.count() : 0;

		// The cloud shadows on VOID LOD's land, from the same cells: their picture lies in the fronts' frame, carried along
		// with them, and offset along the sun's ray from the clouds down to the ground. (A shader pack draws the far land
		// with its own programs, which take no shade.)
		boolean shadows = clouds && lodRadius > 0 && !Shaders.packInUse() && this.deck != null && this.deck.hasShade();
		if (shadows) {
			double seconds = ticks / 20.0;
			float sunAngle = state.skyRenderState.sunAngle;
			double sunX = -Math.sin(sunAngle), sunY = Math.max(0.3, Math.cos(sunAngle));
			double offset = -sunX / sunY * (layers.main() - (level.getSeaLevel() + 30));
			this.setShade(true, this.deck.shadeX() + Air.FRONT_DRIFT * seconds + offset, this.deck.shadeZ() + Air.FRONT_DRIFT * 0.2 * seconds,
				this.deck.shadeSize());
		} else {
			this.setShade(false, 0, 0, 0);
		}
		this.camera = new org.joml.Matrix4f(camera.projectionMatrix);
		this.fillInfo(state, camera, map, reach, clip, base, vanilla, settings.showers, year, thunder, ticks, 0, 0, 0, level.getSeaLevel() + 30);
		try {
			if (belowClouds()) {
				this.renderer.prepare(this.info, mesh, this.rain, mistOn, rainOn);
			} else {
				this.renderer.skip();
			}
		} finally {
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
			Lod.setShade(this.deck.shadeView(), x, z, size, 1.0F);
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
		// FarMap (w: over a shader pack, whose sky the far air dissolves into instead of vanilla's fog colour)
		double fx = cx - Air.FRONT_DRIFT * (ticks / 20.0), fz = cz - Air.FRONT_DRIFT * 0.2 * (ticks / 20.0);
		if (map != null) {
			b.putFloat((float) (fx - map.originX())).putFloat((float) (fz - map.originZ())).putFloat(1.0F / (WeatherMap.SIZE * WeatherMap.TEXEL))
				.putFloat(Shaders.packInUse() ? 1 : 0);
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
