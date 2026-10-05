package dev.voidmc.atlas.client.data;

import dev.voidmc.atlas.api.DiscoveryState;
import dev.voidmc.atlas.api.Place;
import dev.voidmc.atlas.api.Source;
import dev.voidmc.atlas.api.Tier;
import dev.voidmc.atlas.client.Perf;
import dev.voidmc.atlas.core.Coords;
import dev.voidmc.atlas.core.RegionRaw;
import dev.voidmc.atlas.core.RegionStore;
import dev.voidmc.atlas.core.landmarks.AreaIndex;
import dev.voidmc.atlas.core.landmarks.RegionAnalysis;
import dev.voidmc.atlas.core.landmarks.RegionFeatures;
import dev.voidmc.atlas.core.names.BiomeFamily;
import dev.voidmc.atlas.core.places.PlaceBook;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Natural landmarks and named areas, worked out from the surface map on the workers: a region is analysed
 * ({@link RegionAnalysis}) a little while after it was last drawn, its findings are kept beside it on disk
 * ({@code r.x.z.feat}), its peaks, waterfalls and ravines become places, and the dimension's areas (regions of one
 * biome, lakes, rivers) are joined again and named. Everything here happens off the client thread.
 */
public final class Landmarks {
	/** How long a region must be quiet (not drawn again) before it is analysed. */
	private static final long QUIET_MS = 8_000;
	private static final long AREAS_DELAY_MS = 1_500;

	/** Names places: Atlas's generator, unless another mod has a name for it. */
	public interface Namer {
		String name(String dimension, String kind, int biome, int x, int z, long area);
	}

	private final MapSession session;
	private final Map<Long, Long> due = new ConcurrentHashMap<>();
	private final Set<String> areasDue = ConcurrentHashMap.newKeySet();
	private final Map<Integer, Boolean> leaves = new ConcurrentHashMap<>();
	private final Map<Integer, BiomeFamily> families = new ConcurrentHashMap<>();
	private volatile Namer namer;
	private final RegionAnalysis.Lookup lookup = new RegionAnalysis.Lookup() {
		@Override
		public boolean leaves(int block) {
			return Landmarks.this.leaves.computeIfAbsent(block, b -> {
				String n = Landmarks.this.session.blockNames.name(b);
				return n != null && (n.endsWith("_leaves") || n.endsWith(":mangrove_roots"));
			});
		}

		@Override
		public BiomeFamily family(int biome) {
			return Landmarks.this.families.computeIfAbsent(biome, b -> {
				String n = Landmarks.this.session.biomeNames.name(b);
				return n == null ? BiomeFamily.OTHER : BiomeFamily.of(n);
			});
		}
	};

	Landmarks(MapSession session) {
		this.session = session;
	}

	public void setNamer(Namer namer) {
		this.namer = namer;
	}

	public RegionAnalysis.Lookup lookup() {
		return this.lookup;
	}

	private static Path featFile(DimensionData d, int rx, int rz) {
		return d.files.root().resolve(DimensionData.SURFACE).resolve("r." + rx + "." + rz + ".feat");
	}

	/** A dimension was opened: reads what was found in it before, so its landmarks and areas are there at once. */
	void opened(DimensionData d) {
		if (d.ceiling) {
			return;
		}
		this.session.workers.execute(() -> {
			Path dir = d.files.root().resolve(DimensionData.SURFACE);
			if (Files.isDirectory(dir)) {
				try (var list = Files.list(dir)) {
					for (Path p : (Iterable<Path>) list.filter(f -> f.getFileName().toString().endsWith(".feat"))::iterator) {
						String[] parts = p.getFileName().toString().split("\\.");
						try {
							int rx = Integer.parseInt(parts[1]);
							int rz = Integer.parseInt(parts[2]);
							d.areas.put(RegionFeatures.decode(rx, rz, Files.readAllBytes(p)));
						} catch (IOException | RuntimeException e) {
							// analysed again when next drawn
						}
					}
				} catch (IOException ignored) {
					// nothing analysed yet
				}
			}
			// the areas named last time keep their ids (so their names) as the land is joined up again
			List<AreaIndex.Area> before = new ArrayList<>();
			for (Place p : this.session.places.all()) {
				if (p.dimension().equals(d.id) && p.isLabel()) {
					long cells = (long) (4.0 * p.radius() * p.radius() / (RegionFeatures.CELL * RegionFeatures.CELL));
					before.add(new AreaIndex.Area(p.id(), p.kind(), 0, cells, p.x(), p.z(), p.tier() == Tier.MAJOR));
				}
			}
			// the biggest first: when areas have grown together, the biggest keeps its id (and its name)
			before.sort((a, b) -> Long.compare(b.cells(), a.cells()));
			d.areas.remember(before);
			this.rebuildAreas(d);
		});
	}

	/** A surface region was drawn again (any thread): analyse it once it has been quiet a while. */
	void regionDrawn(LayerData layer, int rx, int rz) {
		if (layer.dimension.ceiling || !layer.name.equals(DimensionData.SURFACE)) {
			return;
		}
		long key = Coords.pack(rx, rz);
		long when = System.currentTimeMillis() + QUIET_MS;
		if (this.due.put(key, when) == null) {
			this.schedule(layer.dimension, rx, rz, QUIET_MS);
		}
	}

	private void schedule(DimensionData d, int rx, int rz, long delay) {
		if (this.session.closed()) {
			return;
		}
		this.session.workers.schedule(() -> {
			long key = Coords.pack(rx, rz);
			Long when = this.due.get(key);
			if (when == null) {
				return;
			}
			long wait = when - System.currentTimeMillis();
			if (wait > 50) {
				this.schedule(d, rx, rz, wait);
				return;
			}
			this.due.remove(key);
			this.analyse(d, rx, rz);
		}, delay, TimeUnit.MILLISECONDS);
	}

	/** Analyses one region now (worker thread). */
	public void analyse(DimensionData d, int rx, int rz) {
		LayerData surface = d.layer(DimensionData.SURFACE);
		RegionRaw[] around = surface.around(rx, rz);
		if (around[4] == null) {
			return;
		}
		long t0 = System.nanoTime();
		RegionFeatures f = RegionAnalysis.analyse(around, this.lookup);
		Perf.ANALYSE_NANOS.addAndGet(System.nanoTime() - t0);
		Perf.ANALYSED.incrementAndGet();
		d.areas.put(f);
		byte[] bytes = f.encode();
		this.session.io.execute(() -> {
			try {
				RegionStore.write(featFile(d, rx, rz), bytes);
			} catch (IOException e) {
				this.session.warn("Atlas: could not save the landmarks of region " + rx + "," + rz + ": " + e);
			}
		});
		this.publishPoints(d, f);
		if (this.areasDue.add(d.id)) {
			this.session.workers.schedule(() -> {
				this.areasDue.remove(d.id);
				this.rebuildAreas(d);
			}, AREAS_DELAY_MS, TimeUnit.MILLISECONDS);
		}
	}

	private String name(DimensionData d, String id, String kind, int biome, int x, int z, long area) {
		Place known = this.session.places.get(id);
		if (known != null && !known.name().isEmpty()) {
			return known.name();
		}
		Namer n = this.namer;
		return n == null ? "" : n.name(d.id, kind, biome, x, z, area);
	}

	private void publishPoints(DimensionData d, RegionFeatures f) {
		List<Place> places = new ArrayList<>();
		long now = System.currentTimeMillis();
		for (RegionFeatures.Point p : f.points) {
			String kind = switch (p.kind()) {
				case PEAK -> Place.PEAK;
				case WATERFALL -> Place.WATERFALL;
				case RAVINE -> Place.RAVINE;
			};
			String id = kind + "@" + p.x() + "," + p.z();
			Tier tier = p.kind() == RegionFeatures.PointKind.RAVINE ? Tier.MINOR : Tier.MAJOR;
			String category = p.kind() == RegionFeatures.PointKind.WATERFALL ? "WATER" : "NATURAL";
			int radius = p.kind() == RegionFeatures.PointKind.RAVINE ? p.length() / 2 : 0;
			Place place = new Place(id, kind, this.name(d, id, kind, p.biome(), p.x(), p.z(), 0), category, tier, DiscoveryState.SIGHTED, Source.seen(), p.x(),
				p.y(), p.z(), d.id, false, radius, now, 0);
			places.add(place);
		}
		this.session.places.replaceGroup(d.id, PlaceBook.regionGroup(f.rx, f.rz), places, now);
	}

	/** Joins the dimension's patches into areas again and names the new ones (worker thread). */
	public void rebuildAreas(DimensionData d) {
		long t0 = System.nanoTime();
		List<AreaIndex.Area> areas = d.areas.rebuild();
		List<Place> places = new ArrayList<>(areas.size());
		long now = System.currentTimeMillis();
		for (AreaIndex.Area a : areas) {
			String category = a.kind().equals(Place.REGION) ? "REGION" : "WATER";
			places.add(new Place(a.id(), a.kind(), this.name(d, a.id(), a.kind(), a.biome(), a.labelX(), a.labelZ(), a.blocks()), category,
				a.major() ? Tier.MAJOR : Tier.MINOR, DiscoveryState.SIGHTED, Source.seen(), a.labelX(), 64, a.labelZ(), d.id, false, a.radius(), now, 0));
		}
		this.session.places.replaceGroup(d.id, PlaceBook.AREAS, places, now);
		Perf.AREA_NANOS.addAndGet(System.nanoTime() - t0);
	}

	/** Analyses every region of a dimension whose findings are missing (for the tour, after a flight). */
	public void analyseAll(DimensionData d) {
		LayerData surface = d.layer(DimensionData.SURFACE);
		for (long key : surface.regionKeys()) {
			int rx = Coords.unpackX(key);
			int rz = Coords.unpackZ(key);
			this.due.remove(key);
			this.analyse(d, rx, rz);
		}
		if (surface.regionCount() > 0) {
			this.rebuildAreas(d);
		}
	}

	/** Regions waiting to be analysed. */
	public int backlog() {
		return this.due.size() + this.areasDue.size();
	}
}
