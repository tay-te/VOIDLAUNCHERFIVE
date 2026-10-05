package dev.voidmc.atlas.client;

import dev.voidmc.atlas.api.AtlasMarkers;
import dev.voidmc.atlas.api.AtlasPlaces;
import dev.voidmc.atlas.api.Place;
import dev.voidmc.atlas.api.PlaceProvider;
import dev.voidmc.atlas.api.RegionNameProvider;
import dev.voidmc.atlas.api.RegionNameQuery;
import dev.voidmc.atlas.api.Source;
import dev.voidmc.atlas.client.data.MapSession;
import dev.voidmc.atlas.core.names.BiomeFamily;
import dev.voidmc.atlas.core.names.NameGen;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The places API: other mods' place providers and namers, places reported by servers, and listeners, all fenced off
 * so a broken one never breaks the map (a provider that throws three times in a row is switched off).
 */
public final class AtlasPlacesImpl implements AtlasPlaces {
	private record Entry<T>(String id, int priority, T provider, int[] failures) {
	}

	private final AtlasClient atlas;
	private final CopyOnWriteArrayList<Entry<PlaceProvider>> providers = new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<Entry<RegionNameProvider>> namers = new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<Consumer<Place>> listeners = new CopyOnWriteArrayList<>();

	AtlasPlacesImpl(AtlasClient atlas) {
		this.atlas = atlas;
	}

	private static <T> void put(CopyOnWriteArrayList<Entry<T>> list, String id, int priority, T provider) {
		if (id == null || provider == null) {
			throw new IllegalArgumentException("a provider needs an id and an implementation");
		}
		list.removeIf(e -> e.id.equals(id));
		list.add(new Entry<>(id, priority, provider, new int[1]));
		List<Entry<T>> sorted = new ArrayList<>(list);
		sorted.sort(Comparator.<Entry<T>>comparingInt(e -> e.priority).thenComparing(e -> e.id));
		list.clear();
		list.addAll(sorted);
	}

	@Override
	public void registerPlaceProvider(String id, int priority, PlaceProvider provider) {
		put(this.providers, id, priority, provider);
	}

	@Override
	public void unregisterPlaceProvider(String id) {
		this.providers.removeIf(e -> e.id.equals(id));
	}

	@Override
	public void registerNameProvider(String id, int priority, RegionNameProvider provider) {
		put(this.namers, id, priority, provider);
	}

	@Override
	public void unregisterNameProvider(String id) {
		this.namers.removeIf(e -> e.id.equals(id));
	}

	@Override
	public void report(Place place) {
		MapSession session = this.atlas.session();
		if (session != null && place != null) {
			session.places.report(place, System.currentTimeMillis());
			session.saveSoon(false);
		}
	}

	@Override
	public void forget(String placeId) {
		MapSession session = this.atlas.session();
		if (session != null && session.places.forget(placeId)) {
			session.saveSoon(false);
		}
	}

	@Override
	public List<Place> places(String dimension) {
		MapSession session = this.atlas.session();
		return session == null ? List.of() : session.places.visible(dimension);
	}

	@Override
	public void addListener(Consumer<Place> listener) {
		if (listener != null) {
			this.listeners.add(listener);
		}
	}

	/** Tells the listeners about places sighted or discovered (client thread). */
	void notify(Place place) {
		for (Consumer<Place> l : this.listeners) {
			try {
				l.accept(place);
			} catch (Throwable t) {
				AtlasClient.LOGGER.warn("VOID Atlas: a place listener failed: {}", t.toString());
			}
		}
	}

	/** Asks every place provider for a dimension's places and files them (client thread, about once a second). */
	void poll(String dimension) {
		MapSession session = this.atlas.session();
		if (session == null || this.providers.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		for (Entry<PlaceProvider> e : this.providers) {
			if (e.failures[0] >= AtlasMarkers.MAX_FAILURES) {
				continue;
			}
			List<Place> got = new ArrayList<>();
			try {
				e.provider.collect(dimension, p -> {
					if (p != null) {
						got.add(p);
					}
				});
				e.failures[0] = 0;
			} catch (Throwable t) {
				if (++e.failures[0] >= AtlasMarkers.MAX_FAILURES) {
					AtlasClient.LOGGER.warn("VOID Atlas: place provider {} failed {} times in a row and is switched off: {}", e.id, e.failures[0], t.toString());
				}
				continue;
			}
			for (Place p : got) {
				// credited to the provider unless it says where it comes from
				Place credited = p.source().equals(Source.seen()) ? p.withSource(Source.plugin(e.id)) : p;
				session.places.report(credited, now);
			}
		}
	}

	/**
	 * A name for a natural place or a region: the first provider's that has one, or Atlas's own generated name. Worker
	 * thread.
	 */
	String name(MapSession session, String dimension, String kind, int biome, int x, int z, long area) {
		String biomeName = session.biomeNames.name(biome);
		String biomeId = biomeName == null ? "minecraft:plains" : biomeName;
		if (!this.namers.isEmpty()) {
			RegionNameQuery q = new RegionNameQuery(dimension, kind, biomeId, x, z, area, session.seedHash);
			for (Entry<RegionNameProvider> e : this.namers) {
				if (e.failures[0] >= AtlasMarkers.MAX_FAILURES) {
					continue;
				}
				try {
					String name = e.provider.name(q);
					e.failures[0] = 0;
					if (name != null && !name.isBlank()) {
						return name.strip();
					}
				} catch (Throwable t) {
					if (++e.failures[0] >= AtlasMarkers.MAX_FAILURES) {
						AtlasClient.LOGGER.warn("VOID Atlas: name provider {} failed {} times in a row and is switched off: {}", e.id, e.failures[0], t.toString());
					}
				}
			}
		}
		NameGen.Feature feature = switch (kind) {
			case Place.LAKE -> NameGen.Feature.LAKE;
			case Place.RIVER -> NameGen.Feature.RIVER;
			case Place.PEAK -> NameGen.Feature.PEAK;
			case Place.WATERFALL -> NameGen.Feature.WATERFALL;
			case Place.CAVE_MOUTH -> NameGen.Feature.CAVE;
			case Place.RAVINE -> NameGen.Feature.RAVINE;
			default -> NameGen.Feature.REGION;
		};
		// the id of a place is where it was first found: a stable seed for its name
		long id = (long) x * 0x9E3779B97F4A7C15L ^ (long) z * 0xC2B2AE3D27D4EB4FL ^ kind.hashCode() ^ (long) dimension.hashCode() << 32;
		return NameGen.name(session.seedHash, feature, BiomeFamily.of(biomeId), id);
	}
}
