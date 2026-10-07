package dev.voidmc.atlas.client.data;

import dev.voidmc.atlas.core.RegionStore;
import dev.voidmc.atlas.core.TileRenderer;
import dev.voidmc.atlas.core.detect.Detections;
import dev.voidmc.atlas.core.landmarks.AreaIndex;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * One dimension's map: its folder, its layers (the surface, and the underground in 8-block slices), what its chunks
 * hold of villages ({@link Detections}), and its named areas ({@link AreaIndex}).
 */
public final class DimensionData {
	public final MapSession session;
	public final String id;
	public final RegionStore files;
	/** Whether it has a ceiling (the Nether): then it has no surface layer, only underground bands. */
	public final boolean ceiling;
	public final Detections detections = new Detections();
	public final AreaIndex areas = new AreaIndex();
	/** The cave biomes seen underground, for their names on the underground map. */
	public final dev.voidmc.atlas.core.under.CaveAreas caves = new dev.voidmc.atlas.core.under.CaveAreas();
	private final ConcurrentHashMap<String, LayerData> layers = new ConcurrentHashMap<>();
	/** The underground bands with anything explored in them, on disk or this session. */
	private final NavigableSet<Integer> bands = new ConcurrentSkipListSet<>();

	DimensionData(MapSession session, String id, Path dir, boolean ceiling) {
		this.session = session;
		this.id = id;
		this.files = new RegionStore(dir, session::warn);
		this.ceiling = ceiling;
		// which bands have been explored: read off the disk on a worker, never on the render thread
		session.workers.execute(() -> {
			if (!Files.isDirectory(dir)) {
				return;
			}
			try (var list = Files.list(dir)) {
				list.map(p -> p.getFileName().toString()).filter(n -> n.startsWith(UNDER)).forEach(n -> {
					try {
						this.bands.add(underBand(n));
					} catch (RuntimeException ignored) {
						// not one of ours
					}
				});
			} catch (IOException ignored) {
				// nothing explored yet
			}
		});
	}

	public static final String SURFACE = "surface";
	/** Underground slices: {@code under_<band>}, the {@value #BAND} blocks from {@code band * BAND} up. */
	public static final String UNDER = "under_";
	public static final int BAND = dev.voidmc.atlas.core.under.Reveal.BAND;

	public static String underLayer(int band) {
		return UNDER + band;
	}

	public static int underBand(String layer) {
		return Integer.parseInt(layer.substring(UNDER.length()));
	}

	public static int bandOf(int y) {
		return Math.floorDiv(y, BAND);
	}

	/** The highest block of a band: where its view looks down from. */
	public static int bandTop(int band) {
		return band * BAND + BAND - 1;
	}

	public LayerData layer(String name) {
		return this.layers.computeIfAbsent(name, n -> {
			boolean under = n.startsWith(UNDER);
			if (under) {
				this.bands.add(underBand(n));
			}
			TileRenderer.Mode mode = n.equals(SURFACE) ? TileRenderer.Mode.SURFACE : n.equals("far") || n.equals("farc") ? TileRenderer.Mode.FAR
				: n.equals("shared") ? TileRenderer.Mode.SHARED : TileRenderer.Mode.CAVE;
			return new LayerData(this, n, mode,
				under ? bandTop(underBand(n)) : Integer.MIN_VALUE, this.session.nextLayerUid());
		});
	}

	public LayerData under(int band) {
		return this.layer(underLayer(band));
	}

	public LayerData layerIfOpen(String name) {
		return this.layers.get(name);
	}

	public Collection<LayerData> layers() {
		return this.layers.values();
	}

	/** The explored bands, lowest first. */
	public NavigableSet<Integer> bands() {
		return this.bands;
	}
}
