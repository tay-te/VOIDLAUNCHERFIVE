package dev.voidmc.gloam.bridge;

import dev.voidmc.gloam.Gloam;
import dev.voidmc.gloam.GloamSettings;
import dev.voidmc.gloam.air.Air;
import dev.voidmc.gloam.air.AirSample;
import dev.voidmc.gloam.air.Atmosphere;
import dev.voidmc.gloam.bridge.Contract.Slot;
import dev.voidmc.gloam.compat.Expanse;
import dev.voidmc.gloam.compat.Shaders;
import dev.voidmc.gloam.sky.GloamSky;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * The bridge to the VOID Welkin shader pack (contract version {@value Contract#VERSION}): Gloam works out the world's
 * state and hands it to the pack through two channels, the {@code void_*} uniforms ({@link Uniforms}, added to Iris by
 * {@code CommonUniformsMixin}) and one data texture, {@code gloam:pack/data} ({@link DataTexture}), whose header
 * mirrors the uniforms in case the mixin ever stops applying. The pack draws; Gloam only says what is there.
 *
 * <p>Once a client tick (after the atmosphere and the sky): the uniforms' values, the camera's 16 m cell for the near
 * land tiles (worked out on a worker thread, {@link LandTiles}), the header, and the upload of whatever changed. With
 * no shader pack on, nothing is uploaded and the worker rests.
 */
public final class Bridge {
	public static final Identifier TEXTURE = Gloam.id("pack/data");
	/** How often the rolling refresh redoes a row of the near tiles: 8 rows a second. */
	private static final long REFRESH_NANOS = 125_000_000L;
	private static final boolean LOD = FabricLoader.getInstance().isModLoaded("void_lod");

	private static DataTexture texture;
	private static final LandTiles LAND = new LandTiles();
	private static Thread worker;
	private static volatile boolean running, packOn;
	private static volatile int targetX, targetZ;
	private static volatile LandTiles.CellSource source;
	private static volatile float wetness;
	private static Expanse.RawSampler sourceOf;
	private static int seaLevel = 63;

	private static boolean wasOn, welkin, warned;
	private static int frame, ticksOn, tiles;
	private static int centreX, centreZ;
	private static final float[] LIGHT = new float[4];
	/** Upload statistics since the last {@link #stats}: bytes, calls, ticks, nanoseconds; and the most in one tick. */
	private static long sentBytes, sentCalls, sentTicks, sentNanos, peakBytes;
	private static long workerNanos, workerStrips;

	private Bridge() {
	}

	/** Client start. */
	public static void init() {
		ClientLifecycleEvents.CLIENT_STARTED.register(Bridge::register);
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> running = false);
		// after the atmosphere's and the sky's own ticks (registered first), so it reads this tick's air
		ClientTickEvents.END_CLIENT_TICK.register(Bridge::tick);
	}

	/** The texture goes in before any shader pack's pipeline is built, so the pack finds it by name. */
	private static void register(Minecraft mc) {
		try {
			texture = new DataTexture();
			mc.getTextureManager().register(TEXTURE, texture);
			Gloam.LOGGER.info("VOID Gloam: the shader-pack bridge's texture {} is registered ({}x{})", TEXTURE, Contract.WIDTH, Contract.HEIGHT);
		} catch (RuntimeException e) {
			texture = null;
			Gloam.LOGGER.warn("VOID Gloam: could not make the shader-pack bridge's texture", e);
			return;
		}
		running = true;
		worker = new Thread(Bridge::work, "VOID Gloam bridge");
		worker.setDaemon(true);
		worker.setPriority(Thread.NORM_PRIORITY - 1);
		worker.start();
	}

	/** Whether the shader pack in use is VOID Welkin, which draws the ground mist itself. */
	public static boolean welkin() {
		return welkin;
	}

	// ------------------------------------------------------------------------------------------------ the worker

	private static void work() {
		long nextRefresh = 0;
		while (running) {
			try {
				LandTiles.CellSource s = source;
				if (s == null || !packOn) {
					if (s == null) {
						LAND.reset();
					}
					Thread.sleep(100);
					continue;
				}
				long t0 = System.nanoTime();
				boolean did = LAND.step(s, targetX, targetZ, false);
				if (!did && t0 >= nextRefresh) {
					did = LAND.step(s, targetX, targetZ, true);
					nextRefresh = t0 + REFRESH_NANOS;
				}
				if (did) {
					workerNanos += System.nanoTime() - t0;
					workerStrips++;
				} else {
					Thread.sleep(20);
				}
			} catch (InterruptedException e) {
				return;
			} catch (RuntimeException e) {
				Gloam.LOGGER.warn("VOID Gloam: the shader-pack bridge's land worker failed; it starts over", e);
				LAND.reset();
				try {
					Thread.sleep(1000);
				} catch (InterruptedException ie) {
					return;
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ the tick

	private static void tick(Minecraft mc) {
		if (texture == null) {
			return;
		}
		ClientLevel level = mc.level;
		boolean on = GloamSettings.get().enabled && Shaders.packInUse();
		String name = on ? Shaders.packName() : null;
		welkin = on && name != null && name.toLowerCase(Locale.ROOT).contains("welkin");
		packOn = on;
		int more = 0;
		if (level == null || !GloamSettings.get().enabled || Atmosphere.get().level() != level) {
			source = null;
			sourceOf = null;
			tiles = 0;
			BridgeWorld.reset();
		} else {
			values(mc, level);
			// the weather, far land and underground tiles (written while no pack is on too, so they are whole when one comes on)
			more = BridgeWorld.write(mc, level, texture.bytes, texture::mark, on);
		}
		if (!on) {
			wasOn = false;
			ticksOn = 0;
			return;
		}
		if (!wasOn) {
			// uploads were skipped while no pack was on: everything goes up again
			texture.all();
			wasOn = true;
		}
		if (++ticksOn == 200 && Uniforms.ENABLED && Uniforms.injected == 0 && !warned) {
			warned = true;
			Gloam.LOGGER.warn("VOID Gloam: Iris never asked for the void_* uniforms (its CommonUniforms changed?); the pack reads the data texture's header instead");
		}
		long t0 = System.nanoTime();
		LandTiles.Strip strip;
		while ((strip = LAND.poll()) != null) {
			LandTiles.write(texture.bytes, strip, texture::mark);
			centreX = strip.centreX();
			centreZ = strip.centreZ();
			tiles = strip.clear() ? tiles & ~3 : tiles | 3;
		}
		Uniforms.set(Slot.NEAR_CENTRE, centreX, centreZ);
		byte[] b = texture.bytes;
		int flags = (sourceOf != null ? Contract.FLAG_EXPANSE : 0) | (LOD ? Contract.FLAG_LOD : 0)
			| (mc.getSingleplayerServer() != null ? Contract.FLAG_SINGLEPLAYER : 0) | (Uniforms.injected > 0 ? Contract.FLAG_UNIFORMS : 0);
		Contract.header(b, flags, frame++);
		Contract.centre(b, Contract.N0, centreX, centreZ);
		Contract.centre(b, Contract.N1, centreX, centreZ);
		Contract.masks(b, tiles | more, Uniforms.LIVE);
		BridgeWorld.mirrorFrameValues();
		Uniforms.mirror(b);
		texture.markHeader();
		texture.upload();
		long dt = System.nanoTime() - t0;
		sentBytes += texture.lastBytes;
		sentCalls += texture.lastCalls;
		sentTicks++;
		sentNanos += dt;
		peakBytes = Math.max(peakBytes, texture.lastBytes);
	}

	/** This tick's uniforms, and where the near tiles should be centred. */
	private static void values(Minecraft mc, ClientLevel level) {
		Atmosphere atmosphere = Atmosphere.get();
		AirSample air = atmosphere.here();
		Air model = atmosphere.air();
		if (model == null) {
			return;
		}
		long seed = model.seed();
		long clock = level.getOverworldClockTime();
		boolean sky = level.dimensionType().hasSkyLight();
		Uniforms.set(Slot.SEED, (int) (seed ^ seed >>> 32) & 0x7FFFFFFF);
		Uniforms.set(Slot.YEAR, air.year());
		Uniforms.set(Slot.WARMTH, air.warmth());
		Uniforms.set(Slot.SEASON, air.season().ordinal());
		Uniforms.set(Slot.WIND, air.wind().x(), air.wind().z(), air.wind().speed(), air.wind().gust());
		Uniforms.set(Slot.FRONT, air.front());
		Uniforms.set(Slot.CLOUD_COVER, air.cloudCover());
		Uniforms.set(Slot.RAIN, level.getRainLevel(1.0F));
		Uniforms.set(Slot.SHOWERS, GloamSettings.get().showers ? Air.showers(air.front()) : 0);
		Uniforms.set(Slot.STORM, air.storm());
		Uniforms.set(Slot.MIST, air.mist());
		Uniforms.set(Slot.MIST_TOP, air.mistTop());
		Uniforms.set(Slot.MIST_TIME, sky ? Air.mistTime(clock, air.year()) : 0);
		Uniforms.set(Slot.WETNESS, air.wetness());
		GloamSky.light(LIGHT);
		// the star factor (Night.stars, up to 1.5) goes over as the contract's 0..1 star amount
		Uniforms.set(Slot.LIGHT, LIGHT[0], LIGHT[1] / 1.5F, LIGHT[2], LIGHT[3]);
		// the rest of section 2.2, and the weather tiles
		BridgeWorld.values(mc, level);

		Camera camera = mc.gameRenderer.mainCamera();
		Vec3 eye = camera.isInitialized() ? camera.position() : mc.player != null ? mc.player.getEyePosition() : Vec3.ZERO;
		targetX = Contract.cell(eye.x, Contract.NEAR_CELL);
		targetZ = Contract.cell(eye.z, Contract.NEAR_CELL);
		wetness = air.wetness();
		seaLevel = level.getSeaLevel();
		Expanse.RawSampler raw = Expanse.raw(level);
		if (raw != sourceOf) {
			sourceOf = raw;
			int sea = seaLevel;
			source = raw == null ? null : (cx, cz) -> LandTiles.encode(raw.at(cx * Contract.NEAR_CELL + 8, cz * Contract.NEAR_CELL + 8), sea, wetness);
		}
	}

	/** The bridge's state and costs since the last call, for the showcase's log. */
	public static String stats() {
		if (texture == null) {
			return "bridge: no texture";
		}
		Minecraft mc = Minecraft.getInstance();
		boolean registered = mc.getTextureManager().getTexture(TEXTURE) == texture;
		String sample = "none";
		if ((tiles & 1) != 0 && mc.level != null) {
			Vec3 eye = mc.gameRenderer.mainCamera().position();
			int cx = Contract.cell(eye.x, Contract.NEAR_CELL), cz = Contract.cell(eye.z, Contract.NEAR_CELL);
			byte[] b = texture.bytes;
			int o0 = Contract.offset(Contract.texelX(Contract.N0, cx), Contract.texelY(Contract.N0, cz));
			int o1 = Contract.offset(Contract.texelX(Contract.N1, cx), Contract.texelY(Contract.N1, cz));
			sample = String.format(Locale.ROOT, "ground y=%.1f lowness %d water %d, potential %d temp %d humid %d haze %d flags %d",
				Contract.fromH16(b[o0] & 255, b[o0 + 1] & 255), b[o0 + 2] & 255, b[o0 + 3] & 255,
				b[o1] & 255, b[o1 + 1] & 255, b[o1 + 2] & 255, (b[o1 + 3] & 255) >> 4, b[o1 + 3] & 15);
		}
		long ticks = Math.max(1, sentTicks);
		String s = String.format(Locale.ROOT,
			"bridge: pack %s (welkin %s), texture %s, uniforms %s (asked %d times), centre %d,%d (%s), cells %d, worker %.2f ms a strip, "
				+ "upload %.1f KB and %.1f calls a tick (peak %.1f KB), %.3f ms a tick; at the camera: %s",
			Shaders.packName(), welkin, registered ? "registered" : "MISSING", Uniforms.ENABLED ? "on" : "off", Uniforms.injected,
			centreX, centreZ, LAND.filling() ? "filling" : "full", LAND.cells(), workerStrips == 0 ? 0 : workerNanos / 1e6 / workerStrips,
			sentBytes / 1024.0 / ticks, sentCalls / (double) ticks, peakBytes / 1024.0, sentNanos / 1e6 / ticks, sample) + "; " + BridgeWorld.stats()
			+ "; header tile mask " + Integer.toBinaryString(0x80 | tiles | BridgeWorld.mask()).substring(1) + " (U W1 W0 F1 F0 N1 N0)";
		sentBytes = sentCalls = sentTicks = sentNanos = peakBytes = 0;
		return s;
	}
}
