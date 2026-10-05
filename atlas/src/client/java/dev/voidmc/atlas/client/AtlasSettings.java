package dev.voidmc.atlas.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Atlas's settings, in {@code config/atlas.json}; every change applies at once and is saved when the settings close. */
public final class AtlasSettings {
	public enum Corner {
		TOP_RIGHT("Top right"), TOP_LEFT("Top left"), BOTTOM_RIGHT("Bottom right"), BOTTOM_LEFT("Bottom left");

		public final String label;

		Corner(String label) {
			this.label = label;
		}
	}

	public enum Size {
		SMALL("Small", 136), MEDIUM("Medium", 168), LARGE("Large", 208);

		public final String label;
		/** Side of the minimap's map, in design pixels. */
		public final float side;

		Size(String label, float side) {
			this.label = label;
			this.side = side;
		}
	}

	/** Zoom steps, physical pixels per block: whole numbers are pixel-crisp, the fractions use the mip levels. */
	public static final double[] ZOOMS = {1.0 / 16, 1.0 / 8, 0.25, 0.5, 1, 2, 3, 4, 6, 8, 12, 16};

	public boolean minimap = true;
	public Corner minimapCorner = Corner.TOP_RIGHT;
	public Size minimapSize = Size.MEDIUM;
	public int minimapZoom = 5; // 2 px per block
	public boolean minimapRotates = false;
	public boolean minimapCoordinates = true;
	/** Draw the world live behind the open map (costs a world render every frame) instead of a still of it. */
	public boolean liveBackdrop = false;
	public int workerThreads = Math.max(1, Math.min(3, Runtime.getRuntime().availableProcessors() / 4));
	/** GPU pages of 16 tiles each (16 MB each). */
	public int gpuPages = 4;
	public int mapZoom = 5; // 2 px per block
	public Set<String> hiddenLayers = new HashSet<>();
	/** Kinds of place switched off in the legend (by {@code Kinds.Kind} name). */
	public Set<String> hiddenKinds = new HashSet<>();
	/** The compass strip at the top of the HUD (off by default; it and the minimap can both be on). */
	public boolean compass = false;
	/** The place being tracked (its marker id), shown on the compass and the minimap's rim; null for none. */
	public String tracked;
	/** Bumped whenever the legend changes, so the place markers are made again. */
	public transient int legendVersion;

	private transient Path file;

	public static AtlasSettings load(Path file) {
		AtlasSettings s = null;
		if (Files.isRegularFile(file)) {
			try {
				s = gson().fromJson(Files.readString(file, StandardCharsets.UTF_8), AtlasSettings.class);
			} catch (IOException | RuntimeException e) {
				AtlasClient.LOGGER.warn("Atlas: {} is unreadable ({}); using the defaults", file, e.toString());
			}
		}
		if (s == null) {
			s = new AtlasSettings();
		}
		s.file = file;
		s.sanitise();
		return s;
	}

	private void sanitise() {
		if (this.minimapCorner == null) {
			this.minimapCorner = Corner.TOP_RIGHT;
		}
		if (this.minimapSize == null) {
			this.minimapSize = Size.MEDIUM;
		}
		if (this.hiddenLayers == null) {
			this.hiddenLayers = new HashSet<>();
		}
		if (this.hiddenKinds == null) {
			this.hiddenKinds = new HashSet<>();
		}
		this.minimapZoom = Math.max(0, Math.min(ZOOMS.length - 1, this.minimapZoom));
		this.mapZoom = Math.max(0, Math.min(ZOOMS.length - 1, this.mapZoom));
		this.workerThreads = Math.max(1, Math.min(8, this.workerThreads));
		this.gpuPages = Math.max(1, Math.min(16, this.gpuPages));
	}

	public void save() {
		if (this.file == null) {
			return;
		}
		try {
			Files.createDirectories(this.file.getParent());
			Files.writeString(this.file, gson().toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			AtlasClient.LOGGER.warn("Atlas: could not save {}: {}", this.file, e.toString());
		}
	}

	private static Gson gson() {
		return new GsonBuilder().setPrettyPrinting().create();
	}

	public boolean layerVisible(String id) {
		return !this.hiddenLayers.contains(id);
	}

	public boolean kindVisible(String kind) {
		return !this.hiddenKinds.contains(kind);
	}

	public void toggleKind(String kind) {
		if (!this.hiddenKinds.remove(kind)) {
			this.hiddenKinds.add(kind);
		}
		this.legendVersion++;
	}

	public void toggleLayer(String id) {
		if (!this.hiddenLayers.remove(id)) {
			this.hiddenLayers.add(id);
		}
	}
}
