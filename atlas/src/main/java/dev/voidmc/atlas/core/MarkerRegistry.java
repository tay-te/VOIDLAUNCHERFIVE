package dev.voidmc.atlas.core;

import dev.voidmc.atlas.api.AtlasMarkers;
import dev.voidmc.atlas.api.MapLayer;
import dev.voidmc.atlas.api.Marker;
import dev.voidmc.atlas.api.MarkerProvider;
import dev.voidmc.atlas.api.MarkerQuery;
import dev.voidmc.atlas.api.Source;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * The marker providers and layers, ordered, with every provider call fenced off: a provider that throws loses only
 * its own markers for that call, and after {@link AtlasMarkers#MAX_FAILURES} failures in a row it is switched off
 * (and reported once through {@code warn}). Registration may happen from any thread; collection is meant for the
 * client thread.
 */
public final class MarkerRegistry implements AtlasMarkers {
	private static final class Entry {
		final String id;
		final int priority;
		final MarkerProvider provider;
		int failures;
		boolean disabled;

		Entry(String id, int priority, MarkerProvider provider) {
			this.id = id;
			this.priority = priority;
			this.provider = provider;
		}
	}

	private static final Comparator<Entry> ORDER = Comparator.<Entry>comparingInt(e -> e.priority).thenComparing(e -> e.id);

	private final List<Entry> entries = new ArrayList<>();
	private final List<MapLayer> layers = new ArrayList<>();
	private final Consumer<String> warn;
	private volatile Entry[] snapshot = new Entry[0];

	public MarkerRegistry(Consumer<String> warn) {
		this.warn = warn;
	}

	/** Tracks and untracks markers for other mods (the client sets it); null: tracking is not available. */
	public volatile java.util.function.Predicate<String> tracker;
	public volatile Consumer<String> untracker;

	@Override
	public boolean track(String markerId) {
		var t = this.tracker;
		return t != null && markerId != null && t.test(markerId);
	}

	@Override
	public void untrack(String markerId) {
		var u = this.untracker;
		if (u != null && markerId != null) {
			u.accept(markerId);
		}
	}

	@Override
	public synchronized void registerProvider(String id, int priority, MarkerProvider provider) {
		if (id == null || provider == null) {
			throw new IllegalArgumentException("a provider needs an id and an implementation");
		}
		this.entries.removeIf(e -> e.id.equals(id));
		this.entries.add(new Entry(id, priority, provider));
		this.entries.sort(ORDER);
		this.snapshot = this.entries.toArray(new Entry[0]);
	}

	@Override
	public synchronized void unregisterProvider(String id) {
		if (this.entries.removeIf(e -> e.id.equals(id))) {
			this.snapshot = this.entries.toArray(new Entry[0]);
		}
	}

	@Override
	public synchronized void registerLayer(MapLayer layer) {
		this.layers.removeIf(l -> l.id().equals(layer.id()));
		this.layers.add(layer);
		this.layers.sort(Comparator.comparingInt(MapLayer::order).thenComparing(MapLayer::id));
	}

	@Override
	public synchronized List<MapLayer> layers() {
		return List.copyOf(this.layers);
	}

	/** The providers' ids, in call order, with whether each is still on. */
	public synchronized List<String> providers() {
		return this.entries.stream().map(e -> e.id + (e.disabled ? " (off)" : "")).toList();
	}

	@Override
	public List<Marker> collect(MarkerQuery query) {
		List<Marker> out = new ArrayList<>();
		this.collect(query, out::add);
		return out;
	}

	/** Streams every marker into {@code out}, provider by provider, in order. */
	public void collect(MarkerQuery query, Consumer<Marker> out) {
		for (Entry e : this.snapshot) {
			if (e.disabled) {
				continue;
			}
			List<Marker> mine = new ArrayList<>();
			try {
				e.provider.collect(query, m -> {
					if (m != null) {
						mine.add(m);
					}
				});
				e.failures = 0;
			} catch (Throwable t) {
				e.failures++;
				if (e.failures >= MAX_FAILURES) {
					e.disabled = true;
					this.warn.accept("Atlas: marker provider " + e.id + " failed " + e.failures + " times in a row and is switched off: " + t);
				}
				continue;
			}
			// only a provider that finished delivers: half a provider's markers would be worse than none; a marker that
			// does not say where it comes from is credited to its provider (its source chip)
			for (Marker m : mine) {
				out.accept(m.source() == null ? m.withSource(Source.plugin(e.id)) : m);
			}
		}
	}
}
