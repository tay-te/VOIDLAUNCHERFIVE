package dev.voidmc.hearth.build;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.BlueprintService;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.blueprint.Compiler;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.style.Style;
import dev.voidmc.hearth.build.style.StyleRegistry;
import dev.voidmc.hearth.build.terrain.Site;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The build module's {@link BlueprintService}: looks up the archetype and style, draws, compiles, and caches. The cache is
 * a bounded LRU keyed by spec, the terrain probe (by identity) and the style generation, so a data reload that restyles
 * a culture regenerates its buildings. Generation happens outside the lock; two threads asking for the same new spec at
 * once may both generate it (harmlessly: the result is the same) rather than one waiting on the other.
 */
public final class BlueprintServiceImpl implements BlueprintService {
	private static final Logger LOG = LoggerFactory.getLogger("hearth");
	/** Default number of blueprints kept (a few towns' worth; a large house is ~30 KB). */
	public static final int DEFAULT_CACHE = 512;
	/**
	 * The most steps the cache holds in all: a castle has a hundred times a cottage's steps, so a count alone lets a cache
	 * of great buildings outgrow the heap.
	 */
	public static final long STEP_BUDGET = 3_000_000L;

	private final ArchetypeRegistry archetypes;
	private final StyleRegistry styles;
	private final int capacity;
	/** Steps held by the cached blueprints. */
	private long cachedSteps;
	private final Map<Key, BlueprintImpl> cache;
	private final Set<String> reported = ConcurrentHashMap.newKeySet();
	/** Specs being made in the background ({@link #prepare}), and those that failed there (made inline next time). */
	private final Set<Key> pending = ConcurrentHashMap.newKeySet();
	private final Set<Key> failed = ConcurrentHashMap.newKeySet();

	/**
	 * The background makers: two daemon threads a little below normal priority, shared by every service in the JVM. Their
	 * queue is unbounded but holds each spec once ({@link #pending}).
	 */
	private static final java.util.concurrent.ExecutorService MAKERS = java.util.concurrent.Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "hearth-blueprints");
		t.setDaemon(true);
		t.setPriority(Thread.NORM_PRIORITY - 1);
		return t;
	});

	public BlueprintServiceImpl(ArchetypeRegistry archetypes, StyleRegistry styles) {
		this(archetypes, styles, DEFAULT_CACHE);
	}

	public BlueprintServiceImpl(ArchetypeRegistry archetypes, StyleRegistry styles, int capacity) {
		this.archetypes = archetypes;
		this.styles = styles;
		this.capacity = capacity;
		this.cache = new LinkedHashMap<>(64, 0.75F, true);
	}

	/**
	 * Cache hits and misses, and what the misses cost (draw and compile), with those on the server thread apart: a
	 * blueprint made there holds the tick up. For the dev harness, {@code /hearth sim stats} and the tours.
	 */
	public static final class Stats {
		public static final java.util.concurrent.atomic.LongAdder HITS = new java.util.concurrent.atomic.LongAdder();
		public static final java.util.concurrent.atomic.LongAdder MISSES = new java.util.concurrent.atomic.LongAdder();
		public static final java.util.concurrent.atomic.LongAdder NANOS = new java.util.concurrent.atomic.LongAdder();
		public static final java.util.concurrent.atomic.LongAdder SERVER_MISSES = new java.util.concurrent.atomic.LongAdder();
		public static final java.util.concurrent.atomic.LongAdder SERVER_NANOS = new java.util.concurrent.atomic.LongAdder();
		public static final java.util.concurrent.atomic.AtomicLong SERVER_MAX = new java.util.concurrent.atomic.AtomicLong();
		/** Specs handed to the background makers ({@link #prepare}). */
		public static final java.util.concurrent.atomic.LongAdder BACKGROUND = new java.util.concurrent.atomic.LongAdder();

		private Stats() {
		}

		static void record(long nanos) {
			MISSES.increment();
			NANOS.add(nanos);
			if ("Server thread".equals(Thread.currentThread().getName())) {
				SERVER_MISSES.increment();
				SERVER_NANOS.add(nanos);
				SERVER_MAX.accumulateAndGet(nanos, Math::max);
			}
		}

		public static String line() {
			long m = MISSES.sum();
			long sm = SERVER_MISSES.sum();
			return String.format(java.util.Locale.ROOT,
				"blueprints: %d cache hits, %d made (%.1f ms avg), %d handed to the background; on the server thread %d made, %.1f ms avg, %.1f ms max, %.0f ms in all",
				HITS.sum(), m, m == 0 ? 0 : NANOS.sum() / 1e6 / m, BACKGROUND.sum(), sm, sm == 0 ? 0 : SERVER_NANOS.sum() / 1e6 / sm, SERVER_MAX.get() / 1e6,
				SERVER_NANOS.sum() / 1e6);
		}
	}

	private record Key(PlotSpec spec, TerrainProbe probe, int generation) {
		@Override
		public boolean equals(Object o) {
			return o instanceof Key k && k.generation == this.generation && k.probe == this.probe && k.spec.equals(this.spec);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.spec, System.identityHashCode(this.probe), this.generation);
		}
	}

	@Override
	public Blueprint blueprint(PlotSpec spec, TerrainProbe terrain) {
		return compiled(spec, terrain);
	}

	@Override
	public boolean prepare(PlotSpec spec, TerrainProbe terrain) {
		Key key = new Key(spec, terrain, this.styles.generation());
		synchronized (this.cache) {
			// (a lookup, so it is the most recently used: its caller is about to ask for it)
			if (this.cache.get(key) != null) {
				return true;
			}
		}
		if (this.archetypes.get(spec.archetype()) == null || this.failed.contains(key)) {
			return true;
		}
		if (this.pending.add(key)) {
			Stats.BACKGROUND.increment();
			try {
				MAKERS.execute(() -> {
					try {
						compiled(spec, terrain);
					} catch (RuntimeException e) {
						if (this.failed.size() < 4096) {
							this.failed.add(key);
						}
					} finally {
						this.pending.remove(key);
					}
				});
			} catch (java.util.concurrent.RejectedExecutionException e) {
				this.pending.remove(key);
				return true;
			}
		}
		return false;
	}

	/** Specs waiting for a background maker or being made. */
	public static int pendingCount(BlueprintServiceImpl s) {
		return s.pending.size();
	}

	/** {@link #blueprint} with the implementation type (for tools and tests). */
	public BlueprintImpl compiled(PlotSpec spec, TerrainProbe terrain) {
		Key key = new Key(spec, terrain, this.styles.generation());
		synchronized (this.cache) {
			BlueprintImpl b = this.cache.get(key);
			if (b != null) {
				Stats.HITS.increment();
				return b;
			}
		}
		long t0 = System.nanoTime();
		BlueprintImpl b = Compiler.compile(draw(spec, terrain));
		Stats.record(System.nanoTime() - t0);
		synchronized (this.cache) {
			BlueprintImpl old = this.cache.put(key, b);
			this.cachedSteps += b.totalSteps() - (old == null ? 0 : old.totalSteps());
			// evict the least recently used past the count or the step budget (keeping the newest)
			var it = this.cache.values().iterator();
			while (this.cache.size() > 1 && (this.cache.size() > this.capacity || this.cachedSteps > STEP_BUDGET) && it.hasNext()) {
				BlueprintImpl eldest = it.next();
				if (eldest == b) {
					continue;
				}
				this.cachedSteps -= eldest.totalSteps();
				it.remove();
			}
		}
		return b;
	}

	/**
	 * Draws a spec without compiling or caching it (previews and checks look at the draft itself).
	 *
	 * @throws IllegalArgumentException if the archetype is unknown
	 */
	public Draft draw(PlotSpec spec, TerrainProbe terrain) {
		Archetype a = this.archetypes.get(spec.archetype());
		if (a == null) {
			throw new IllegalArgumentException("unknown archetype " + spec.archetype());
		}
		Style style = this.styles.getOrDefault(spec.style());
		Site site = new Site(spec, a.terrain(spec, terrain));
		Draft d = new Draft(spec, style, site);
		try {
			// the house as first built (on part of its plot when it will grow: docs/buildings/age.md)
			dev.voidmc.hearth.build.age.Age.draw(a, d, style, terrain);
			// what time has done to it: additions over generations, then decay (docs/buildings/age.md)
			dev.voidmc.hearth.build.age.Age.apply(a.info(), d);
		} catch (RuntimeException e) {
			// a broken generator must not take world generation down with it: log once per kind, build nothing
			if (this.reported.add(spec.archetype() + "/" + spec.style())) {
				LOG.error("archetype {} failed in style {} (seed {}, {}x{} facing {})", spec.archetype(), spec.style(), spec.seed(), spec.sizeX(),
					spec.sizeZ(), spec.facing(), e);
			}
			if (Boolean.getBoolean("hearth.dev.build.strict")) {
				throw e;
			}
			d = new Draft(spec, style, site);
		}
		if (!d.missingForms().isEmpty() && this.reported.add("forms/" + spec.style() + "/" + d.missingForms())) {
			LOG.warn("style {} has no material for {} (used full blocks)", spec.style(), d.missingForms());
		}
		return d;
	}

	public void clearCache() {
		synchronized (this.cache) {
			this.cache.clear();
			this.cachedSteps = 0;
		}
	}

	public int cacheSize() {
		synchronized (this.cache) {
			return this.cache.size();
		}
	}

	@Override
	public @Nullable ArchetypeInfo archetype(Identifier id) {
		Archetype a = this.archetypes.get(id);
		return a == null ? null : a.info();
	}

	@Override
	public java.util.List<Dressing> dressing(PlotSpec spec, TerrainProbe terrain, String kind, long day) {
		dev.voidmc.hearth.build.age.Dressings.Kind k;
		try {
			k = dev.voidmc.hearth.build.age.Dressings.Kind.byName(kind);
		} catch (IllegalArgumentException e) {
			return java.util.List.of();
		}
		if (this.archetypes.get(spec.archetype()) == null || !dev.voidmc.hearth.build.age.Dressings.applies(spec, k)) {
			return java.util.List.of();
		}
		java.util.List<Dressing> out = new java.util.ArrayList<>();
		for (var s : dev.voidmc.hearth.build.age.Dressings.steps(spec, blueprint(spec, terrain), k, day, terrain)) {
			out.add(new Dressing(s.pos(), s.state(), s.tag()));
		}
		return out;
	}

	@Override
	public Collection<ArchetypeInfo> archetypes() {
		return this.archetypes.infos();
	}

	@Override
	public Identifier style(Culture culture) {
		return StyleRegistry.styleId(culture);
	}

	public ArchetypeRegistry registry() {
		return this.archetypes;
	}

	public StyleRegistry styles() {
		return this.styles;
	}
}
