package dev.voidmc.hearth.api;

import java.util.Collection;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** The build module: archetypes, styles, and blueprints made from plot specs. Registered in {@link HearthServices}. */
public interface BlueprintService {
	/**
	 * The blueprint for a spec on a terrain. Deterministic (same spec and terrain, same blueprint, on any thread, in any
	 * session) and cached, so callers ask freely instead of holding on to blueprints.
	 *
	 * @throws IllegalArgumentException if the archetype is unknown
	 */
	Blueprint blueprint(PlotSpec spec, TerrainProbe terrain);

	@Nullable ArchetypeInfo archetype(Identifier id);

	Collection<ArchetypeInfo> archetypes();

	/** The style id a settlement of this culture builds in (variants within it are the archetype's business). */
	Identifier style(Culture culture);
}
