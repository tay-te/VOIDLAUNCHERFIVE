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
		SMALL("S", 144), MEDIUM("M", 200), LARGE("L", 256);

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
	// --- minimap ---------------------------------------------------------------------------------------------------
	public enum Shape {
		SQUARE("Square"), ROUND("Round");

		public final String label;

		Shape(String label) {
			this.label = label;
		}
	}

	public Shape minimapShape = Shape.SQUARE;
	/** Underground, the minimap shows the slice round the player (where the server allows it). */
	public boolean minimapAutoCave = true;
	/** Hide the minimap with the debug screen (F3) as well as with the HUD (F1). */
	public boolean minimapHideWithDebug = true;
	/** Other players on the minimap (off by default; on a server only where it opts in to radar; see docs/SERVER_RULES.md). */
	public boolean radarPlayers = false;
	/** Mobs on the minimap (likewise). */
	public boolean radarMobs = false;

	// --- waypoints -------------------------------------------------------------------------------------------------
	/** A beam of light and a distance label over each visible waypoint, in the world. */
	public boolean waypointBeams = true;
	/** Beams fade out to nothing by this distance (blocks); 0 for no limit. */
	public int beamDistance = 3000;
	/** Death points go after this many minutes (0: kept until deleted or reached). */
	public int deathMinutes = 10;
	/** Death points go once the player is back within {@link #DEATH_REACH} blocks of them. */
	public boolean deathClearsOnReach = true;
	public static final int DEATH_REACH = 8;
	/** How long a temporary waypoint (from chat, a share, quick-add) lasts, in minutes: 5, 10, 30 or 60. */
	public int temporaryMinutes = 10;
	/** Quick-add's "Within N m": 64, 128, 256 or 512. */
	public int quickAddWithin = 256;
	/** The colour quick-add starts with: the last one used. */
	public int lastColour = 0;
	/** Coordinates in chat become clickable (a temporary waypoint). */
	public boolean chatCoordinates = true;

	// --- storage, look ---------------------------------------------------------------------------------------------
	/** The most disk Atlas's maps may take, in MB (0: no cap); least recently viewed far regions go first. */
	public int diskCapMb = 4096;
	/** The map's look: following the world's time of day (night from 12,500 ticks), always day, or always night. */
	public enum MapLook {
		FOLLOW("Follow time"), DAY("Always day"), NIGHT("Always night");

		public final String label;

		MapLook(String label) {
			this.label = label;
		}
	}

	public MapLook mapLook = MapLook.FOLLOW;
	/** Every waypoint colour has its own pin head shape (colour-blind safe); on by default. */
	public boolean shapeMarkers = true;
	/** How hard Atlas works: Auto (it watches the frame time and its own cost and scales back), Quality, Light. */
	public enum Performance {
		AUTO("Auto"), QUALITY("Quality"), LIGHT("Light");

		public final String label;

		Performance(String label) {
			this.label = label;
		}
	}

	public Performance performance = Performance.AUTO;
	/** Beams are hidden for 10 s after the player hits or is hit (PvP-safe). */
	public boolean beamsHideInCombat = false;
	/** Which of the first-run presets was last applied (null: none, the defaults). */
	public String preset;

	/** The first-run presets: a whole set of settings, applied at once. */
	public enum Preset {
		/** The minimap and nothing else in the HUD. */
		MINIMAL("Minimal", "The minimap only: no beams, no compass, no radar"),
		/** Everything on. */
		EXPLORER("Explorer", "Everything on: beams, compass, radar, cave view"),
		/** Nothing that could be called an advantage in a fight: no radar, no cave view, no beams in combat. */
		PVP_SAFE("PvP-safe", "No radar, no cave view, beams hidden in combat");

		public final String label;
		public final String detail;

		Preset(String label, String detail) {
			this.label = label;
			this.detail = detail;
		}
	}

	/** Applies a preset: every setting it covers changes together (one saved file, one legend change). */
	public synchronized void apply(Preset p) {
		switch (p) {
			case MINIMAL -> {
				this.minimap = true;
				this.waypointBeams = false;
				this.compass = false;
				this.radarPlayers = false;
				this.radarMobs = false;
				this.minimapAutoCave = true;
				this.beamsHideInCombat = false;
			}
			case EXPLORER -> {
				this.minimap = true;
				this.waypointBeams = true;
				this.compass = true;
				this.radarPlayers = true;
				this.radarMobs = true;
				this.minimapAutoCave = true;
				this.chatCoordinates = true;
				this.beamsHideInCombat = false;
			}
			case PVP_SAFE -> {
				this.minimap = true;
				this.waypointBeams = true;
				this.beamsHideInCombat = true;
				this.compass = false;
				this.radarPlayers = false;
				this.radarMobs = false;
				this.minimapAutoCave = false;
			}
		}
		this.preset = p.name();
		this.legendVersion++;
		this.save();
	}

	/** The first-run hint has been shown. */
	public boolean firstRunHintShown = false;
	/** The import from other map mods has been offered (it is offered once). */
	public boolean importOffered = false;

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
		if (this.performance == null) {
			this.performance = Performance.AUTO;
		}
		if (this.mapLook == null) {
			this.mapLook = MapLook.FOLLOW;
		}
		this.lastColour = Math.floorMod(this.lastColour, 8);
		if (this.quickAddWithin != 64 && this.quickAddWithin != 128 && this.quickAddWithin != 256 && this.quickAddWithin != 512) {
			this.quickAddWithin = 256;
		}
		if (this.minimapShape == null) {
			this.minimapShape = Shape.SQUARE;
		}
		this.beamDistance = Math.max(0, Math.min(100_000, this.beamDistance));
		this.deathMinutes = Math.max(0, Math.min(7 * 24 * 60, this.deathMinutes));
		this.temporaryMinutes = Math.max(1, Math.min(7 * 24 * 60, this.temporaryMinutes));
		this.diskCapMb = Math.max(0, this.diskCapMb);
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
