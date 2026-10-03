package dev.voidmc.lod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The LOD's settings, kept in {@code config/void_lod.json}. A quality preset sets the reach, the two detail
 * budgets, the worker threads and the GPU memory budget together; changing any of those by hand makes the
 * preset Custom, and in the file a preset other than Custom wins over the values written under it, so
 * choosing a preset there is one edit.
 *
 * <p>The file is read value by value: a missing, misspelt or out-of-range value falls back to its default or
 * is clamped, and the rest of the file still counts. System properties ({@code void.lod.radius} and the
 * others, as dev runs and the game tests pass them) override the file for the session without being
 * written back.
 */
public final class LodSettings {
	private static final Logger LOGGER = LoggerFactory.getLogger("VOID LOD");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	static final int CORES = Runtime.getRuntime().availableProcessors();

	public static final int MIN_RADIUS = 1024;
	public static final int MAX_RADIUS = 32768;
	public static final double MIN_DETAIL = 1;
	public static final double MAX_DETAIL = 32;
	public static final int MAX_HAZE = 131072;
	public static final int MAX_THREADS = 16;
	public static final int MIN_BUDGET = 64;
	public static final int MAX_BUDGET = 8192;

	/**
	 * Quality presets, from integrated graphics on a laptop to a strong desktop GPU. What sets the GPU cost is
	 * the detail budget (the cell count goes with its square); the reach costs little past a few kilometres,
	 * since each doubling adds one coarse level, but the memory and the build time grow with it.
	 */
	public enum Preset {
		LOW("low"),
		MEDIUM("medium"),
		HIGH("high"),
		ULTRA("ultra"),
		CUSTOM("custom");

		private final String name;

		Preset(String name) {
			this.name = name;
		}

		public String serializedName() {
			return this.name;
		}

		public String translationKey() {
			return "options.void_lod.preset." + this.name;
		}

		/** The preset called {@code name}, or null. */
		public static Preset byName(String name) {
			for (Preset preset : values()) {
				if (preset.name.equalsIgnoreCase(name.trim())) {
					return preset;
				}
			}
			return null;
		}
	}

	public Preset preset = Preset.HIGH;
	public boolean enabled = true;
	/** How far the LOD reaches, in blocks. */
	public int radius;
	/** The most pixels a cell may cover before its tile splits, near and in the smooth far field: lower is sharper and heavier. */
	public double detail;
	public double farDetail;
	/** How far the air carries at sea level, in blocks; 0 for three quarters of the reach. */
	public int haze;
	/** The first level drawn smooth; the levels below it are blocky. */
	public int smoothFrom = 2;
	public int threads;
	/** MB of GPU memory before the least recently used tiles are evicted. */
	public int budget;
	/** Whether the detail (and if need be the reach) follows what the machine can afford: {@link dev.voidmc.lod.core.AutoTune}. */
	public boolean autoTune = true;
	/** Auto-tune's targets, in milliseconds a frame: the LOD's GPU time, and its render-thread time. */
	public double gpuTarget = 1.5;
	public double prepareTarget = 0.5;

	public LodSettings() {
		this.apply(Preset.HIGH);
	}

	/** Sets everything a preset controls. Custom leaves the values as they are. */
	public void apply(Preset preset) {
		int half = Math.max(1, CORES / 2);
		switch (preset) {
			case LOW -> this.set(4096, 10, 10, 1, 192);
			case MEDIUM -> this.set(8192, 7, 7, Math.min(2, half), 320);
			case HIGH -> this.set(8192, 5, 5, Math.min(4, half), 512);
			case ULTRA -> this.set(16384, 4, 3, Math.min(6, half), 1024);
			case CUSTOM -> {
			}
		}
		this.preset = preset;
	}

	private void set(int radius, double detail, double farDetail, int threads, int budget) {
		this.radius = radius;
		this.detail = detail;
		this.farDetail = farDetail;
		this.threads = threads;
		this.budget = budget;
	}

	/** The preset whose values these are, or Custom. */
	public Preset matching() {
		LodSettings probe = new LodSettings();
		for (Preset preset : Preset.values()) {
			if (preset == Preset.CUSTOM) {
				continue;
			}
			probe.apply(preset);
			if (probe.radius == this.radius && probe.detail == this.detail && probe.farDetail == this.farDetail && probe.threads == this.threads
				&& probe.budget == this.budget) {
				return preset;
			}
		}
		return Preset.CUSTOM;
	}

	public LodSettings copy() {
		LodSettings copy = new LodSettings();
		copy.preset = this.preset;
		copy.enabled = this.enabled;
		copy.set(this.radius, this.detail, this.farDetail, this.threads, this.budget);
		copy.haze = this.haze;
		copy.smoothFrom = this.smoothFrom;
		copy.autoTune = this.autoTune;
		copy.gpuTarget = this.gpuTarget;
		copy.prepareTarget = this.prepareTarget;
		return copy;
	}

	// ---- the file -----------------------------------------------------------------------------------------

	/** The settings in {@code file}, or the defaults where it is missing or unreadable. */
	public static LodSettings load(Path file) {
		LodSettings settings = new LodSettings();
		if (!Files.isRegularFile(file)) {
			return settings;
		}
		try {
			JsonElement root = JsonParser.parseString(Files.readString(file));
			if (root.isJsonObject()) {
				settings.read(root.getAsJsonObject());
			} else {
				LOGGER.warn("VOID LOD: {} is not a JSON object; using the default settings", file);
			}
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("VOID LOD: could not read {}; using the default settings ({})", file, e.toString());
		}
		return settings;
	}

	void read(JsonObject json) {
		JsonElement name = json.get("preset");
		Preset preset = name != null && name.isJsonPrimitive() ? Preset.byName(name.getAsString()) : null;
		this.enabled = bool(json.get("enabled"), this.enabled);
		this.radius = (int) number(json.get("radius"), this.radius, MIN_RADIUS, MAX_RADIUS);
		this.detail = number(json.get("detail"), this.detail, MIN_DETAIL, MAX_DETAIL);
		this.farDetail = number(json.get("farDetail"), this.detail, MIN_DETAIL, MAX_DETAIL);
		this.haze = haze(number(json.get("haze"), this.haze, 0, MAX_HAZE));
		this.smoothFrom = (int) number(json.get("smoothFrom"), this.smoothFrom, 0, 10);
		this.threads = (int) number(json.get("threads"), this.threads, 1, MAX_THREADS);
		this.budget = (int) number(json.get("budget"), this.budget, MIN_BUDGET, MAX_BUDGET);
		this.autoTune = bool(json.get("autoTune"), this.autoTune);
		this.gpuTarget = number(json.get("gpuTarget"), this.gpuTarget, 0.1, 50);
		this.prepareTarget = number(json.get("prepareTarget"), this.prepareTarget, 0.05, 50);
		if (preset != null) {
			// a named preset wins over the values written under it
			this.apply(preset);
		} else {
			// no preset named (a file written by hand): the values are what was meant
			this.preset = this.matching();
		}
	}

	/** Writes the settings to {@code file}, through a temporary file so a crash never leaves half of one. */
	public void save(Path file) {
		JsonObject json = new JsonObject();
		json.addProperty("preset", this.preset.serializedName());
		json.addProperty("enabled", this.enabled);
		json.addProperty("radius", this.radius);
		json.addProperty("detail", this.detail);
		json.addProperty("farDetail", this.farDetail);
		json.addProperty("haze", this.haze);
		json.addProperty("smoothFrom", this.smoothFrom);
		json.addProperty("threads", this.threads);
		json.addProperty("budget", this.budget);
		json.addProperty("autoTune", this.autoTune);
		json.addProperty("gpuTarget", this.gpuTarget);
		json.addProperty("prepareTarget", this.prepareTarget);
		try {
			Files.createDirectories(file.toAbsolutePath().getParent());
			Path temp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(temp, GSON.toJson(json) + System.lineSeparator());
			try {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			LOGGER.warn("VOID LOD: could not save the settings to {}", file, e);
		}
	}

	// ---- system properties --------------------------------------------------------------------------------

	/**
	 * Overrides the settings with the {@code void.lod.*} properties that are set: {@code preset}, {@code radius},
	 * {@code detail}, {@code farDetail} (the same as {@code detail} if only that is given), {@code haze},
	 * {@code smoothFrom}, {@code threads}, {@code budget}, {@code enabled} and {@code autoTune}.
	 */
	public void override(UnaryOperator<String> properties) {
		String preset = properties.apply("void.lod.preset");
		if (preset != null) {
			Preset named = Preset.byName(preset);
			if (named != null) {
				this.apply(named);
			}
		}
		String detail = properties.apply("void.lod.detail");
		String farDetail = properties.apply("void.lod.farDetail");
		this.radius = (int) number(properties.apply("void.lod.radius"), this.radius, MIN_RADIUS, MAX_RADIUS);
		this.detail = number(detail, this.detail, MIN_DETAIL, MAX_DETAIL);
		this.farDetail = number(farDetail != null ? farDetail : detail, this.farDetail, MIN_DETAIL, MAX_DETAIL);
		this.haze = haze(number(properties.apply("void.lod.haze"), this.haze, 0, MAX_HAZE));
		this.smoothFrom = (int) number(properties.apply("void.lod.smoothFrom"), this.smoothFrom, 0, 10);
		this.threads = (int) number(properties.apply("void.lod.threads"), this.threads, 1, MAX_THREADS);
		this.budget = (int) number(properties.apply("void.lod.budget"), this.budget, MIN_BUDGET, MAX_BUDGET);
		this.enabled = bool(properties.apply("void.lod.enabled"), this.enabled);
		this.autoTune = bool(properties.apply("void.lod.autoTune"), this.autoTune);
		if (this.preset != Preset.CUSTOM) {
			this.preset = this.matching();
		}
	}

	// ---- reading values, forgivingly ----------------------------------------------------------------------

	/** A haze reach of a few blocks is a typo, not a wish to see nothing: under 256 it means the default. */
	private static int haze(double blocks) {
		return blocks < 256 ? 0 : (int) blocks;
	}

	private static double number(JsonElement value, double fallback, double min, double max) {
		if (value == null || !value.isJsonPrimitive()) {
			return fallback;
		}
		return number(value.getAsString(), fallback, min, max);
	}

	private static double number(String value, double fallback, double min, double max) {
		if (value == null) {
			return fallback;
		}
		try {
			double v = Double.parseDouble(value.trim());
			return Double.isFinite(v) ? Math.max(min, Math.min(max, v)) : fallback;
		} catch (NumberFormatException e) {
			LOGGER.warn("VOID LOD: ignoring setting value '{}', not a number", value);
			return fallback;
		}
	}

	private static boolean bool(JsonElement value, boolean fallback) {
		return value != null && value.isJsonPrimitive() ? bool(value.getAsString(), fallback) : fallback;
	}

	private static boolean bool(String value, boolean fallback) {
		if (value == null) {
			return fallback;
		}
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "true", "on", "yes", "1" -> true;
			case "false", "off", "no", "0" -> false;
			default -> fallback;
		};
	}
}
