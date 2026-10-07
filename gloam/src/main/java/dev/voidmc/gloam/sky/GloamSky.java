package dev.voidmc.gloam.sky;

import dev.voidmc.gloam.GloamSettings;
import dev.voidmc.gloam.air.AirSample;
import dev.voidmc.gloam.air.Atmosphere;
import dev.voidmc.gloam.air.Noise;
import dev.voidmc.gloam.mixin.ClientLevelAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

/**
 * The sky and the light: Gloam's environment-attribute layers (dark nights by the moon and the clouds, the colours of
 * dawn and dusk, overcast skies under the fronts, mist in the fog distances) and the regional rain.
 *
 * <p>Everything that draws the sky, the fog and the light reads the attributes through the camera's probe, once a
 * tick: vanilla's sky, fog and lightmap, VOID LOD's haze (its {@code FogRendererMixin}), and the uniforms Iris hands a
 * shader pack. So the layers here are time-based layers that read numbers worked out once a tick from the air at the
 * camera ({@link #tick}); a layer is a few multiplies. They sit on top of the dimension's, the biomes', the
 * timelines' and the weather's, and only in dimensions with sky light: the Nether and the End keep their own air.
 *
 * <p>The rain: {@code ClientLevel.getRainLevel} answers with the rain the fronts bring to the camera
 * ({@link AirSample#rain}) instead of vanilla's world-wide level ({@code LevelWeatherMixin}), so vanilla's rain and
 * snow, the rain's sound, its fog, the grey it gives the sky, and a shader pack's {@code rainStrength} all follow the
 * passing showers. The thunder's darkening follows the storm in the front overhead. The server's weather, and with it
 * every gameplay rule, stays vanilla's.
 */
public final class GloamSky {
	/** A clear moonlit night's light: silver with the blue of the sky in it, not vanilla's violet. */
	private static final Vector3fc MOONLIGHT = new Vector3f(0.60F, 0.68F, 1.0F);
	/** Moonlight through thin cloud: a flat grey with a little blue. */
	private static final Vector3fc CLOUDED_MOONLIGHT = new Vector3f(0.64F, 0.67F, 0.78F);
	/** The colour of a clear sky lit by a full moon, a deep blue rather than vanilla's black. */
	private static final Vector3fc MOONLIT_SKY = new Vector3f(0.022F, 0.034F, 0.072F);
	/** The blue hour's tint on the sky: the red goes first, the blue stays. */
	private static final Vector3fc BLUE_HOUR = new Vector3f(0.78F, 0.89F, 1.12F);
	/** The golden hour's tint on the haze over the land: the low sun's light through a lot of air. */
	private static final Vector3fc GOLDEN_HAZE = new Vector3f(1.10F, 0.93F, 0.72F);

	// What the sky is doing, worked out once a client tick from the air at the camera, for the level it is for.
	private static ClientLevel level;
	/** Whether Gloam's light and colour layers apply this tick (Gloam and its sky on, in that level, with sky light). */
	private static boolean lit;
	/** Whether the rain at the camera is Gloam's this tick. */
	private static boolean regional;
	private static float night, dark, skyLight, ambient, cloud, cover, moon, stars, golden, blue, mist, mistEnd;
	/** The haze of the land round the camera (0..1, eased over biome edges), how much of it is dust, and the fog it makes. */
	private static float landHaze, landDust, haze, hazeEnd, curtain;
	private static ClientLevel hazeLevel;
	private static float sunsetAlpha;
	private static Vector3fc sunset = new Vector3f(1, 0.6F, 0.3F);
	private static float rain, oRain, thunder, oThunder, storm;

	private GloamSky() {
	}

	/** Client start. */
	public static void init() {
		// after the atmosphere's own tick (registered first), so the sky reads this tick's air
		ClientTickEvents.END_CLIENT_TICK.register(GloamSky::tick);
	}

	/** Works out what the sky is doing from the air at the camera: every layer reads this until the next tick. */
	private static void tick(Minecraft mc) {
		ClientLevel lv = mc.level;
		GloamSettings settings = GloamSettings.get();
		Atmosphere atmosphere = Atmosphere.get();
		boolean on = lv != null && settings.enabled && settings.sky && atmosphere.level() == lv;
		level = lv;
		oRain = rain;
		oThunder = thunder;
		if (!on) {
			lit = false;
			regional = false;
			if (lv != null) {
				// so the rain eases from vanilla's level when Gloam comes back on
				rain = Atmosphere.vanillaRain(lv, 1.0F);
				thunder = Atmosphere.vanillaThunder(lv, 1.0F);
			}
			return;
		}
		AirSample air = atmosphere.here();
		regional = settings.regionalRain && lv.canHaveWeather();
		if (regional) {
			rain = air.rain();
			// the dark of a thunderstorm, where the front overhead has one
			thunder = air.storm() * air.rain();
		} else {
			rain = Atmosphere.vanillaRain(lv, 1.0F);
			thunder = Atmosphere.vanillaThunder(lv, 1.0F);
		}
		storm = air.storm();

		lit = lv.dimensionType().hasSkyLight();
		if (!lit) {
			return;
		}
		Camera camera = mc.gameRenderer.mainCamera();
		Vec3 eye = camera.isInitialized() ? camera.position() : mc.player != null ? mc.player.getEyePosition() : Vec3.ZERO;
		long clock = lv.getOverworldClockTime();
		MoonPhase phase = lv.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, eye);
		float outdoors = 1 - air.indoors();
		float strength = (float) settings.darkNights;

		moon = DimensionType.MOON_BRIGHTNESS_PER_PHASE[phase.index()];
		cover = air.overcast();
		float clear = Night.clear(cover);
		// how grey the fronts' cloud makes the sky, on top of whatever rain vanilla's weather layer already greys it
		// for: a scattered sky stays blue between its clouds, a covered one goes grey
		cloud = Noise.smoothstep(0.3F, 0.95F, air.cloudCover()) * (1 - rain);
		night = Twilight.night(clock);
		dark = Twilight.dark(clock);
		float light = Night.light(moon, cover);
		skyLight = Night.scale(light, dark, strength);
		// the ambient floor of the light, in sight of the sky only, so caves stay as dark as vanilla made them
		ambient = Night.scale(0.3F + 0.7F * light, dark, strength * outdoors);
		stars = Night.stars(moon, cover);
		golden = Twilight.golden(clock) * clear;
		blue = Twilight.blue(clock) * clear;

		float vivid = Twilight.vivid(atmosphere.air().seed(), clock, cover);
		float[] c = Twilight.colour(clock, vivid);
		Vector3f band = new Vector3f(c[0], c[1], c[2]);
		// a closing overcast greys the band and hides most of it (rain's own share vanilla's weather layer takes)
		float closed = Noise.smoothstep(0.55F, 0.95F, air.cloudCover()) * (1 - rain);
		float grey = band.x * 0.3F + band.y * 0.59F + band.z * 0.11F;
		band.lerp(new Vector3f(grey, grey, grey), 0.7F * closed);
		sunset = band;
		sunsetAlpha = (1 - 0.7F * closed) * Math.min(1.25F, vivid);

		// the fog closes in while the camera stands in the mist, not when it looks down on it from above
		float inside = Noise.smoothstep(air.mistTop() + 4, air.mistTop() - 4, (float) eye.y);
		mist = inside * outdoors * Noise.smoothstep(0.04F, 0.35F, air.mist());
		mistEnd = Mth.lerp(air.mist(), 360.0F, 56.0F);
		float mistCurtain = mist * (0.3F + 0.7F * air.mist());

		// the land's own haze, eased over biome edges at the pace the atmosphere eases the climate
		Haze.Entry entry = lv.getBiome(BlockPos.containing(eye)).unwrapKey().map(key -> Haze.BIOMES.get(key.identifier())).orElse(null);
		float ease = hazeLevel == lv ? 0.05F : 1.0F;
		hazeLevel = lv;
		landHaze += ((entry == null ? 0 : Haze.haziness(entry.reach())) - landHaze) * ease;
		landDust += ((entry != null && entry.kind() == Haze.Kind.DUST ? 1 : 0) - landDust) * ease;
		haze = landHaze * Haze.daily(landHaze, air.hour()) * (float) settings.haze * outdoors;
		hazeEnd = Haze.reach(haze, 80.0F);
		curtain = Math.min(0.8F, mistCurtain + Haze.curtain(haze));
	}

	/** Adds Gloam's layers to a client level's environment attributes, as the level is made. */
	public static void addLayers(EnvironmentAttributeSystem.Builder builder, ClientLevel forLevel) {
		if (!forLevel.dimensionType().hasSkyLight()) {
			return;
		}
		builder.addTimeBasedLayer(EnvironmentAttributes.SKY_LIGHT_FACTOR, (factor, tick) -> {
			if (!on(forLevel) || ((ClientLevelAccessor) forLevel).gloam$skyFlashTime() > 0) {
				return factor;
			}
			// a heavy overcast takes a little off the daylight too
			return factor * skyLight * (1 - 0.15F * cloud * (1 - night));
		});
		builder.addTimeBasedLayer(EnvironmentAttributes.SKY_LIGHT_COLOR, (colour, tick) -> {
			if (!on(forLevel) || night <= 0) {
				return colour;
			}
			Vector3f moonlight = new Vector3f(MOONLIGHT).lerp(CLOUDED_MOONLIGHT, 1 - Night.clear(cover));
			float bright = Math.max(colour.x(), Math.max(colour.y(), colour.z()));
			return new Vector3f(colour).lerp(moonlight.mul(bright), night * 0.85F);
		});
		builder.addTimeBasedLayer(EnvironmentAttributes.AMBIENT_LIGHT_COLOR, (colour, tick) -> on(forLevel) ? ARGB.scaleRGB(colour, ambient) : colour);
		builder.addTimeBasedLayer(EnvironmentAttributes.STAR_BRIGHTNESS, (brightness, tick) -> on(forLevel) ? Math.min(1.0F, brightness * stars) : brightness);
		builder.addTimeBasedLayer(EnvironmentAttributes.SKY_COLOR, (colour, tick) -> on(forLevel) ? sky(colour) : colour);
		builder.addTimeBasedLayer(EnvironmentAttributes.FOG_COLOR, (colour, tick) -> on(forLevel) ? fog(colour) : colour);
		builder.addTimeBasedLayer(EnvironmentAttributes.CLOUD_COLOR, (colour, tick) -> on(forLevel) ? clouds(colour) : colour);
		builder.addTimeBasedLayer(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, (colour, tick) -> {
			if (!on(forLevel) || colour.w() <= 0) {
				return colour;
			}
			return new Vector4f(sunset.x(), sunset.y(), sunset.z(), Math.clamp(colour.w() * sunsetAlpha, 0.0F, 1.0F));
		});
		builder.addTimeBasedLayer(EnvironmentAttributes.FOG_START_DISTANCE, (start, tick) -> {
			if (!on(forLevel) || curtain <= 0) {
				return start;
			}
			// mist and a hazy land's murk hang round the camera the way rain does: the fog starts behind it, so even
			// the near land is veiled. VOID LOD reads a start behind the camera as a curtain that does not thin with
			// height (its rain curtain, RAIN_OFFSET blocks per unit), which is what standing in a mist bank is; it
			// fades the far land over kilometres rather than walling it
			return start - 160.0F * curtain;
		});
		builder.addTimeBasedLayer(EnvironmentAttributes.FOG_END_DISTANCE, (end, tick) -> {
			if (!on(forLevel)) {
				return end;
			}
			// never further than it was; VOID LOD's haze follows this distance, down to a floor of its own, so the far
			// land still shows through as a horizon
			float e = end;
			if (mist > 0) {
				e = Math.min(e, Mth.lerp(mist, end, mistEnd));
			}
			if (haze > 0) {
				e = Math.min(e, Mth.lerp(Math.min(1, haze * 3), end, hazeEnd));
			}
			return e;
		});
	}

	private static boolean on(ClientLevel forLevel) {
		return lit && forLevel == level;
	}

	/** The sky overhead: the blue hour after sunset, a moonlit blue at night, greyed under the fronts' cloud. */
	private static Vector3fc sky(Vector3fc colour) {
		Vector3f c = new Vector3f(colour);
		if (blue > 0) {
			c.mul(new Vector3f(1).lerp(BLUE_HOUR, blue));
		}
		if (golden > 0) {
			// the sky pales toward the low sun's side through the golden hour
			float grey = luma(c);
			c.lerp(new Vector3f(grey * 1.04F, grey, grey * 0.94F), 0.18F * golden);
		}
		if (night > 0) {
			c.max(new Vector3f(MOONLIT_SKY).mul(night * moon * Night.clear(cover)));
		}
		c = grey(c, 0.72F, 0.75F * cloud);
		return clamp(c);
	}

	/** The haze the land fades into: gold in the golden hour, cool in the blue hour, grey under cloud, pale in mist, dark on a black night. */
	private static Vector3fc fog(Vector3fc colour) {
		Vector3f c = new Vector3f(colour);
		if (golden > 0) {
			float grey = luma(c);
			c.lerp(new Vector3f(GOLDEN_HAZE).mul(grey), 0.32F * golden);
		}
		if (blue > 0) {
			c.mul(new Vector3f(1).lerp(new Vector3f(0.86F, 0.92F, 1.08F), blue));
		}
		c = grey(c, 0.85F, 0.6F * cloud);
		float pale = Math.max(0.4F * mist, 0.3F * haze * (1 - landDust));
		if (pale > 0) {
			// mist and damp air: a pale grey
			float grey = luma(c);
			c.lerp(new Vector3f(grey * 1.08F + 0.02F), pale);
		}
		if (haze * landDust > 0) {
			// dust: a warm, dry haze
			float grey = luma(c);
			c.lerp(new Vector3f(grey * 1.08F, grey * 0.97F, grey * 0.82F), 0.4F * haze * landDust);
		}
		c.mul(Mth.lerp(dark, 1.0F, 0.25F + 0.75F * skyLight));
		return clamp(c);
	}

	/** The clouds: darker and greyer under a heavy front, unlit on a black night. */
	private static Vector4fc clouds(Vector4fc colour) {
		Vector4f c = new Vector4f(colour);
		float grey = luma(new Vector3f(c.x, c.y, c.z)) * Mth.lerp(cloud, 1.0F, 0.62F);
		float f = 0.75F * cloud;
		c.x += (grey - c.x) * f;
		c.y += (grey - c.y) * f;
		c.z += (grey - c.z) * f;
		float dim = Mth.lerp(dark, 1.0F, 0.3F + 0.7F * skyLight);
		return new Vector4f(c.x * dim, c.y * dim, c.z * dim, c.w);
	}

	/** Blends a colour toward its own grey, darkened to {@code brightness}, by {@code factor} (vanilla's BlendToGray). */
	private static Vector3f grey(Vector3f c, float brightness, float factor) {
		if (factor <= 0) {
			return c;
		}
		float grey = luma(c) * brightness;
		return c.lerp(new Vector3f(grey), factor);
	}

	private static float luma(Vector3fc c) {
		return c.x() * 0.3F + c.y() * 0.59F + c.z() * 0.11F;
	}

	private static Vector3f clamp(Vector3f c) {
		return c.set(Math.clamp(c.x, 0.0F, 1.0F), Math.clamp(c.y, 0.0F, 1.0F), Math.clamp(c.z, 0.0F, 1.0F));
	}

	// ------------------------------------------------------------------------------------------------------ the rain

	/**
	 * The rain level the client sees ({@code ClientLevel.getRainLevel}): the rain at the camera while Gloam's sky is on,
	 * vanilla's otherwise. {@link Atmosphere#vanillaRain} reads vanilla's past this.
	 */
	public static float rainLevel(ClientLevel forLevel, float partialTick, float vanilla) {
		return regional && forLevel == level ? Mth.lerp(partialTick, oRain, rain) : vanilla;
	}

	/** The thunder level the client sees ({@code ClientLevel.getThunderLevel}): the storm overhead, under its rain. */
	public static float thunderLevel(ClientLevel forLevel, float partialTick, float vanilla) {
		return regional && forLevel == level ? Mth.lerp(partialTick, oThunder, thunder) : vanilla;
	}

	/**
	 * Whether a lightning flash may light the sky: only where a storm is overhead. A bolt the server strikes far off
	 * under a clear sky here still shows and is heard, but does not flash the whole sky.
	 */
	public static boolean flashes(ClientLevel forLevel) {
		return !regional || forLevel != level || storm > 0.25F;
	}
}
