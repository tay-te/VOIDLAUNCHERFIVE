package dev.voidmc.hearth.build.style;

import dev.voidmc.hearth.api.Culture;
import java.io.Reader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every known style. Starts with the styles shipped in the jar (read straight from the classpath, so the offline preview
 * and unit tests have them without a data pack load) and is replaced wholesale on each data reload, so a data pack can
 * restyle a culture or add styles. Thread-safe: readers see one immutable snapshot.
 */
public final class StyleRegistry {
	private static final Logger LOG = LoggerFactory.getLogger("hearth");
	/** The data folder styles live in. */
	public static final String DIRECTORY = "hearth_style";

	private volatile Map<Identifier, Style> styles = Map.of();
	private final AtomicInteger generation = new AtomicInteger();

	/** A registry holding the built-in style of every culture. */
	public static StyleRegistry builtin() {
		StyleRegistry r = new StyleRegistry();
		Map<Identifier, Style> m = new HashMap<>();
		for (Culture c : Culture.values()) {
			Identifier id = styleId(c);
			Style s = StyleLoader.fromClasspath(id);
			if (s != null) {
				m.put(id, s);
			} else {
				LOG.warn("no built-in style for culture {}", c.getSerializedName());
			}
		}
		r.styles = Map.copyOf(m);
		return r;
	}

	/** The id of a culture's default style, {@code hearth:<culture>}. */
	public static Identifier styleId(Culture culture) {
		return Identifier.fromNamespaceAndPath("hearth", culture.getSerializedName());
	}

	public @Nullable Style get(Identifier id) {
		return this.styles.get(id);
	}

	/**
	 * The style for an id, falling back to the culture of the same name and then to temperate, so a stale or mistyped
	 * spec still builds something sensible rather than failing world generation.
	 */
	public Style getOrDefault(Identifier id) {
		Style s = this.styles.get(id);
		if (s != null) {
			return s;
		}
		Culture c = Culture.CODEC.byName(id.getPath());
		if (c != null) {
			s = this.styles.get(styleId(c));
			if (s != null) {
				return s;
			}
		}
		s = this.styles.get(styleId(Culture.TEMPERATE));
		if (s == null) {
			throw new IllegalStateException("no styles loaded");
		}
		return s;
	}

	public Collection<Style> all() {
		return this.styles.values();
	}

	/** Bumped whenever the styles change, so caches of blueprints built with old styles can be dropped. */
	public int generation() {
		return this.generation.get();
	}

	/** Replaces the styles with those of a data reload (built-ins stay for any culture the data no longer has). */
	public void reload(ResourceManager manager) {
		Map<Identifier, Style> m = new HashMap<>(builtin().styles);
		Map<Identifier, Resource> found = manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json"));
		for (Map.Entry<Identifier, Resource> e : found.entrySet()) {
			Identifier file = e.getKey();
			String path = file.getPath().substring(DIRECTORY.length() + 1, file.getPath().length() - ".json".length());
			Identifier id = Identifier.fromNamespaceAndPath(file.getNamespace(), path);
			try (Reader reader = e.getValue().openAsReader()) {
				m.put(id, StyleLoader.parse(id, reader));
			} catch (Exception ex) {
				LOG.error("style {} could not be loaded: {}", id, ex.getMessage());
			}
		}
		this.styles = Map.copyOf(m);
		this.generation.incrementAndGet();
		LOG.info("loaded {} building styles", m.size());
	}
}
