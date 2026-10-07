package dev.voidmc.gloam;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Gloam's settings, kept in {@code config/gloam.json}. The file is read value by value: a missing, misspelt or
 * out-of-range value falls back to its default or is clamped, and the rest still counts. {@code gloam.<name>} system
 * properties (as the dev runs and game tests pass them) override the file for the session without being written back.
 */
public final class GloamSettings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static GloamSettings current = new GloamSettings();

	/** Everything Gloam does, on or off. */
	public boolean enabled = true;

	// the sky and the light
	/** Gloam's sky layers: dark nights, dawn and dusk, overcast under the fronts, mist in the fog. */
	public boolean sky = true;
	/** How dark a moonless, cloudy night gets, 0 (vanilla) .. 1 (truly dark: you need a light). */
	public double darkNights = 0.85;
	/** Whether the fronts bring passing showers under a clear vanilla sky (only their look and sound; vanilla's weather rules). */
	public boolean showers = true;
	/** How many Minecraft days one season lasts. */
	public int seasonDays = 8;

	// the air
	/** How many motes (leaves, pollen, fireflies, mist wisps) fill the air, 0 none .. 2 twice the default. */
	public double motes = 1.0;

	// the sound
	/** The soundscape's volume, 0 off .. 1; on top of Minecraft's ambient volume slider. */
	public double sound = 1.0;

	// the far air, over VOID LOD
	/** The air over VOID LOD's far terrain: the cloud deck, far storms, far mist. */
	public boolean far = true;
	/** Gloam's cloud deck, following the fronts out to VOID LOD's horizon, in place of vanilla's clouds (the cloud option still says off, fast or fancy). */
	public boolean farClouds = true;
	/** Rain curtains under far showers and storms. */
	public boolean farRain = true;
	/** Morning mist lying in the far valleys. */
	public boolean farMist = true;
	/** How much the air thickens or thins VOID LOD's haze (humid lowland summers hazier, clear air after rain), 0 (as VOID LOD has it) .. 1. */
	public double farHaze = 1.0;
	/**
	 * How far above {@code CLOUD_HEIGHT} Gloam's cloud deck sits, in blocks. Vanilla's 192 leaves today's mountains
	 * (and Expanse's) standing in the clouds and the far land hidden behind the deck from every peak; real fair-weather
	 * clouds float far higher above the land.
	 */
	public double farCloudLift = 240;

	public static GloamSettings get() {
		return current;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("gloam.json");
	}

	/** Reads the file (writing it with the defaults if there is none), then the system properties over it. */
	public static void load() {
		GloamSettings settings = new GloamSettings();
		Path file = file();
		JsonObject json = null;
		if (Files.exists(file)) {
			try {
				JsonElement read = JsonParser.parseString(Files.readString(file));
				json = read.isJsonObject() ? read.getAsJsonObject() : null;
			} catch (IOException | RuntimeException e) {
				Gloam.LOGGER.warn("VOID Gloam: could not read {}, using the defaults: {}", file, e.toString());
			}
		}
		if (json != null) {
			settings.read(json);
		}
		current = settings;
		if (json == null) {
			settings.save();
		}
		settings.overrides();
	}

	/** Writes the settings to the file. */
	public void save() {
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(this.write()));
		} catch (IOException e) {
			Gloam.LOGGER.warn("VOID Gloam: could not write {}: {}", file(), e.toString());
		}
	}

	private void read(JsonObject json) {
		this.enabled = bool(json, "enabled", this.enabled);
		this.sky = bool(json, "sky", this.sky);
		this.darkNights = number(json, "darkNights", this.darkNights, 0, 1);
		this.showers = bool(json, "showers", this.showers);
		this.seasonDays = (int) number(json, "seasonDays", this.seasonDays, 1, 120);
		this.motes = number(json, "motes", this.motes, 0, 2);
		this.sound = number(json, "sound", this.sound, 0, 1);
		this.far = bool(json, "far", this.far);
		this.farClouds = bool(json, "farClouds", this.farClouds);
		this.farRain = bool(json, "farRain", this.farRain);
		this.farMist = bool(json, "farMist", this.farMist);
		this.farHaze = number(json, "farHaze", this.farHaze, 0, 1);
		this.farCloudLift = number(json, "farCloudLift", this.farCloudLift, 0, 512);
	}

	private JsonObject write() {
		JsonObject json = new JsonObject();
		json.addProperty("enabled", this.enabled);
		json.addProperty("sky", this.sky);
		json.addProperty("darkNights", this.darkNights);
		json.addProperty("showers", this.showers);
		json.addProperty("seasonDays", this.seasonDays);
		json.addProperty("motes", this.motes);
		json.addProperty("sound", this.sound);
		json.addProperty("far", this.far);
		json.addProperty("farClouds", this.farClouds);
		json.addProperty("farRain", this.farRain);
		json.addProperty("farMist", this.farMist);
		json.addProperty("farHaze", this.farHaze);
		json.addProperty("farCloudLift", this.farCloudLift);
		return json;
	}

	private void overrides() {
		JsonObject props = new JsonObject();
		for (String name : new String[]{"enabled", "sky", "darkNights", "showers", "seasonDays", "motes", "sound", "far", "farClouds", "farRain", "farMist", "farHaze", "farCloudLift"}) {
			String value = System.getProperty("gloam." + name);
			if (value != null) {
				String v = value.trim().toLowerCase(Locale.ROOT);
				if (v.equals("true") || v.equals("false")) {
					props.addProperty(name, Boolean.parseBoolean(v));
				} else {
					try {
						props.addProperty(name, Double.parseDouble(v));
					} catch (NumberFormatException e) {
						Gloam.LOGGER.warn("VOID Gloam: ignoring gloam.{}={}", name, value);
					}
				}
			}
		}
		this.read(props);
	}

	private static boolean bool(JsonObject json, String key, boolean fallback) {
		JsonElement e = json.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : fallback;
	}

	private static double number(JsonObject json, String key, double fallback, double min, double max) {
		JsonElement e = json.get(key);
		if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		double v = e.getAsDouble();
		return Double.isFinite(v) ? Math.clamp(v, min, max) : fallback;
	}
}
