package dev.voidmc.atlas.client.data;

import dev.voidmc.atlas.core.RegionStore;
import dev.voidmc.atlas.core.TileRenderer;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/** One dimension's map: its folder and its layers. */
public final class DimensionData {
	public final MapSession session;
	public final String id;
	public final RegionStore files;
	/** Whether it has a ceiling (the Nether): then it has no surface layer, only slices. */
	public final boolean ceiling;
	private final ConcurrentHashMap<String, LayerData> layers = new ConcurrentHashMap<>();

	DimensionData(MapSession session, String id, Path dir, boolean ceiling) {
		this.session = session;
		this.id = id;
		this.files = new RegionStore(dir, session::warn);
		this.ceiling = ceiling;
	}

	public static final String SURFACE = "surface";

	/** The name of the underground slice that holds block height {@code y}: slices are 16 blocks. */
	public static String caveLayer(int y) {
		return "cave_" + Math.floorDiv(y, 16);
	}

	public static int caveBand(String layer) {
		return Integer.parseInt(layer.substring(5));
	}

	public LayerData layer(String name) {
		return this.layers.computeIfAbsent(name, n -> new LayerData(this, n, n.equals(SURFACE) ? TileRenderer.Mode.SURFACE : TileRenderer.Mode.CAVE,
			this.session.nextLayerUid()));
	}

	public LayerData layerIfOpen(String name) {
		return this.layers.get(name);
	}

	public Collection<LayerData> layers() {
		return this.layers.values();
	}
}
