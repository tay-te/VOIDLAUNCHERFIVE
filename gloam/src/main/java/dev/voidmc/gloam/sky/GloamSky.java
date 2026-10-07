package dev.voidmc.gloam.sky;

import dev.voidmc.gloam.GloamSettings;
import dev.voidmc.gloam.air.AirSample;
import dev.voidmc.gloam.air.Atmosphere;
import dev.voidmc.gloam.air.Noise;
import dev.voidmc.gloam.compat.Expanse;
import dev.voidmc.gloam.far.FarAir;
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
	// What the sky is doing, worked out once a client tick from the air at the camera, for the level it is for.
	private static ClientLevel level;
	/** Whether Gloam's light and colour layers apply this tick (Gloam and its sky on, in that level, with sky light). */
	private static boolean lit;
	/** Whether the rain at the camera is Gloam's this tick. */
	private static boolean regional;
	/** {@link #cloud} and {@link #dark} in the palette's steps, for the colours. */
	private static float cloudStep, darkStep, mistStep, hazeStep;
	private static float night, dark, skyLight, ambient, cloud, cover, moon, stars, golden, blue, mist, mistEnd;
	/** The haze of the land round the camera (0..1, eased over biome edges), how much of it is dust, and the fog it makes. */
	private static float landHaze, landDust, haze, hazeEnd, curtain;
	private static ClientLevel hazeLevel;
	/** The haze hint of Expanse's country at the camera (null without one), read every half second. */
	private static Haze.Entry hint;
	private static int hintTicks;
	private static float sunsetAlpha;
	private static Vector3fc sunset = Palette.ORANGE;
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
		Camera camera = mc.gameRenderer.mainCamera();
		Vec3 eye = camera.isInitialized() ? camera.position() : mc.player != null ? mc.player.getEyePosition() : Vec3.ZERO;
		// how much of the fronts' cloud is above the camera, and whether the rain cloud is: on a summit above the main
		// deck the sky is blue over a sea of cloud and it does not rain
		float decks = lv.environmentAttributes().getValue(EnvironmentAttributes.CLOUD_HEIGHT, eye);
		float overhead = overhead(FarAir.cloudDecks(), decks, (float) eye.y);
		float wetHere = rainBelow(FarAir.cloudDecks(), decks, (float) eye.y);
		regional = settings.regionalRain && lv.canHaveWeather();
		if (regional) {
			rain = air.rain() * wetHere;
			// the dark of a thunderstorm, where the front overhead has one
			thunder = air.storm() * rain;
		} else {
			rain = Atmosphere.vanillaRain(lv, 1.0F);
			thunder = Atmosphere.vanillaThunder(lv, 1.0F);
		}
		storm = air.storm();

		lit = lv.dimensionType().hasSkyLight();
		if (!lit) {
			return;
		}
		long clock = lv.getOverworldClockTime();
		MoonPhase phase = lv.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, eye);
		float outdoors = 1 - air.indoors();
		float strength = (float) settings.darkNights;

		moon = DimensionType.MOON_BRIGHTNESS_PER_PHASE[phase.index()];
		float cloudCover = air.cloudCover() * overhead;
		cover = Math.max(cloudCover, rain);
		float clear = Night.clear(cover);
		// how grey the fronts' cloud makes the sky, on top of whatever rain vanilla's weather layer already greys it
		// for: a scattered sky stays blue between its clouds, a covered one goes grey
		cloud = Noise.smoothstep(0.3F, 0.95F, cloudCover) * (1 - rain);
		night = Twilight.night(clock);
		dark = Twilight.dark(clock);
		float light = Night.light(moon, cover);
		skyLight = Night.scale(light, dark, strength);
		// the ambient floor of the light, in sight of the sky only, so caves stay as dark as vanilla made them
		ambient = Night.scale(0.3F + 0.7F * light, dark, strength * outdoors);
		stars = Night.stars(moon, cover);
		// the colour work moves in flat steps (docs/STYLE.md): a few levels of each, not a smooth slide
		golden = Palette.step(Twilight.golden(clock) * clear, 3);
		blue = Palette.step(Twilight.blue(clock) * clear, 3);
		cloudStep = Palette.step(cloud, 3);
		darkStep = Palette.step(dark, 4);

		// the band round the sun: the palette's step for the hour, as strong as this day's evening is fiery, hidden in
		// steps as an overcast closes in (rain's own share vanilla's weather layer takes)
		float vivid = Twilight.vivid(atmosphere.air().seed(), clock, cover);
		float closed = Palette.step(Noise.smoothstep(0.55F, 0.95F, cloudCover) * (1 - rain), 3);
		sunset = Twilight.colour(clock);
		sunsetAlpha = (1 - 0.75F * closed) * Math.min(1.25F, vivid);

		// the fog closes in while the camera stands in the mist, not when it looks down on it from above
		float inside = Noise.smoothstep(air.mistTop() + 4, air.mistTop() - 4, (float) eye.y);
		mist = inside * outdoors * Noise.smoothstep(0.04F, 0.35F, air.mist());
		mistEnd = Mth.lerp(air.mist(), 360.0F, 56.0F);
		float mistCurtain = mist * (0.3F + 0.7F * air.mist());

		// the land's own haze, eased over biome edges at the pace the atmosphere eases the climate
		Haze.Entry entry = lv.getBiome(BlockPos.containing(eye)).unwrapKey().map(key -> Haze.BIOMES.get(key.identifier())).orElse(null);
		float ease = hazeLevel == lv ? 0.05F : 1.0F;
		if (hazeLevel != lv || hintTicks++ % 10 == 0) {
			// and the country's: a humid lowland, misty lakes, a damp forest, dust, or clear mountain air
			Expanse.Sampler country = Expanse.sampler(lv);
			hint = country == null ? null : Haze.HINTS.get(country.at((int) Math.floor(eye.x), (int) Math.floor(eye.z)).haze());
		}
		hazeLevel = lv;
		// the hazier of the biome's own and the country's, so a cloud forest stays murky inside a clear country
		float biomeHaze = entry == null ? 0 : Haze.haziness(entry.reach());
		float hintHaze = hint == null ? 0 : Haze.haziness(hint.reach());
		Haze.Entry hazier = biomeHaze >= hintHaze ? entry : hint;
		landHaze += (Math.max(biomeHaze, hintHaze) - landHaze) * ease;
		landDust += ((hazier != null && hazier.kind() == Haze.Kind.DUST ? 1 : 0) - landDust) * ease;
		haze = landHaze * Haze.daily(landHaze, air.hour()) * (float) settings.haze * outdoors;
		hazeEnd = Haze.reach(haze, 80.0F);
		curtain = Math.min(0.8F, mistCurtain + Haze.curtain(haze));
		mistStep = Palette.step(mist, 3);
		hazeStep = Palette.step(haze, 3);
	}

	/** What the sky is doing, for the showcase's log and debugging. */
	public static String describe() {
		return String.format(java.util.Locale.ROOT, "sky %s: night %.2f, light %.2f, cloud %.2f, golden %.2f, blue %.2f, mist fog %.2f, haze %.2f (land %.2f, dust %.2f, hint %s), curtain %.2f",
			lit ? "on" : "off", night, skyLight, cloud, golden, blue, mist, haze, landHaze, landDust, hint == null ? "none" : hint, curtain);
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
			// silver-blue under a clear sky, slate blue under cloud: one of the two, never a grey between
			Vector3fc moonlight = Night.clear(cover) > 0.5F ? Palette.MOONLIGHT : Palette.CLOUDED_MOONLIGHT;
			float bright = Math.max(colour.x(), Math.max(colour.y(), colour.z()));
			return new Vector3f(colour).lerp(new Vector3f(moonlight).mul(bright), Palette.step(night, 3) * 0.85F);
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

	/**
	 * How much of the fronts' cloud lies above a camera at {@code y}, 0..1: all of it under the lowest deck, only the
	 * high deck's share over the main deck. {@code decks} are Gloam's cloud decks ({low, main, high}, -1 for none) or
	 * null for vanilla's one deck at {@code vanilla}.
	 */
	static float overhead(int[] decks, float vanilla, float y) {
		if (decks == null) {
			return above(vanilla, y);
		}
		// how much of the cover each deck holds: the main deck most, a thin high veil least
		float[] share = decks[2] >= 0 ? new float[]{0.35F, 0.45F, 0.2F} : decks[0] >= 0 ? new float[]{0.4F, 0.6F, 0} : new float[]{0, 1, 0};
		float sum = 0;
		for (int i = 0; i < 3; i++) {
			if (decks[i] >= 0) {
				sum += share[i] * above(decks[i], y);
			}
		}
		return sum;
	}

	/** Whether the rain cloud (the main deck, or vanilla's one) is above a camera at {@code y}: 1 under it, 0 over it. */
	static float rainBelow(int[] decks, float vanilla, float y) {
		return above(decks == null ? vanilla : decks[1], y);
	}

	/** 1 for a deck at {@code deck} well above {@code y}, 0 well below, a 20-block ease through it (decks are 4 thick). */
	private static float above(float deck, float y) {
		return Noise.smoothstep(y - 8, y + 12, deck);
	}

	private static boolean on(ClientLevel forLevel) {
		return lit && forLevel == level;
	}

	/**
	 * The sky overhead: dusk blue in the blue hour, paler gold toward the low sun, a moonlit navy at night, a clean
	 * blue-grey under the fronts' cloud; each in a few steps.
	 */
	private static Vector3fc sky(Vector3fc colour) {
		Vector3f c = new Vector3f(colour);
		if (blue > 0) {
			c.lerp(Palette.tone(Palette.DUSK_BLUE, c, 1.0F), 0.55F * blue);
		}
		if (golden > 0) {
			c.lerp(Palette.tone(Palette.GOLDEN_HAZE, c, 1.05F), 0.2F * golden);
		}
		if (night > 0) {
			c.max(new Vector3f(Palette.MOONLIT_SKY).mul(Palette.step(night * moon * Night.clear(cover), 3)));
		}
		if (cloudStep > 0) {
			c.lerp(Palette.tone(Palette.OVERCAST_SKY, c, 0.8F), 0.8F * cloudStep);
		}
		return clamp(c);
	}

	/**
	 * The haze the land fades into: gold in the golden hour, dusk blue in the blue hour, a cool grey under cloud, pale
	 * in mist and damp air, sand over dusty land, darker on a black night; each in a few steps.
	 */
	private static Vector3fc fog(Vector3fc colour) {
		Vector3f c = new Vector3f(colour);
		if (golden > 0) {
			c.lerp(Palette.tone(Palette.GOLDEN_HAZE, c, 1.0F), 0.4F * golden);
		}
		if (blue > 0) {
			c.lerp(Palette.tone(Palette.DUSK_BLUE, c, 1.0F), 0.3F * blue);
		}
		if (cloudStep > 0) {
			c.lerp(Palette.tone(Palette.OVERCAST_FOG, c, 0.9F), 0.7F * cloudStep);
		}
		float pale = Math.max(0.45F * mistStep, 0.35F * hazeStep * (1 - landDust));
		if (pale > 0) {
			c.lerp(Palette.tone(Palette.MIST, c, 1.1F).add(0.02F, 0.02F, 0.02F), pale);
		}
		if (hazeStep * landDust > 0) {
			c.lerp(Palette.tone(Palette.DUST, c, 1.05F), 0.45F * hazeStep * landDust);
		}
		c.mul(Mth.lerp(darkStep, 1.0F, 0.25F + 0.75F * skyLight));
		return clamp(c);
	}

	/** The clouds: slate under a heavy front, unlit on a black night; in steps. */
	private static Vector4fc clouds(Vector4fc colour) {
		Vector3f rgb = new Vector3f(colour.x(), colour.y(), colour.z());
		if (cloudStep > 0) {
			rgb.lerp(Palette.tone(Palette.STORM_CLOUD, rgb, Mth.lerp(cloudStep, 1.0F, 0.7F)), 0.75F * cloudStep);
		}
		rgb.mul(Mth.lerp(darkStep, 1.0F, 0.3F + 0.7F * skyLight));
		return new Vector4f(Math.clamp(rgb.x, 0, 1), Math.clamp(rgb.y, 0, 1), Math.clamp(rgb.z, 0, 1), colour.w());
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
