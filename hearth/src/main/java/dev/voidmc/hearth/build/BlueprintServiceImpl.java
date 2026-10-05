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

	private final ArchetypeRegistry archetypes;
	private final StyleRegistry styles;
	private final int capacity;
	private final Map<Key, BlueprintImpl> cache;
	private final Set<String> reported = ConcurrentHashMap.newKeySet();

	public BlueprintServiceImpl(ArchetypeRegistry archetypes, StyleRegistry styles) {
		this(archetypes, styles, DEFAULT_CACHE);
	}

	public BlueprintServiceImpl(ArchetypeRegistry archetypes, StyleRegistry styles, int capacity) {
		this.archetypes = archetypes;
		this.styles = styles;
		this.capacity = capacity;
		this.cache = new LinkedHashMap<>(64, 0.75F, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<Key, BlueprintImpl> eldest) {
				return size() > BlueprintServiceImpl.this.capacity;
			}
		};
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

	/** {@link #blueprint} with the implementation type (for tools and tests). */
	public BlueprintImpl compiled(PlotSpec spec, TerrainProbe terrain) {
		Key key = new Key(spec, terrain, this.styles.generation());
		synchronized (this.cache) {
			BlueprintImpl b = this.cache.get(key);
			if (b != null) {
				return b;
			}
		}
		BlueprintImpl b = Compiler.compile(draw(spec, terrain));
		synchronized (this.cache) {
			this.cache.put(key, b);
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
		Site site = new Site(spec, terrain);
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
