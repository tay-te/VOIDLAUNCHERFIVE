package dev.voidmc.hearth.gametest.plan;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.BlueprintService;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The real build module's archetypes over the planner's catalogue double: towns are planned with the whole catalogue,
 * and drawn with real buildings wherever an architect has made one and boxes everywhere else. Preview only.
 */
public final class LayeredBlueprints implements BlueprintService {
	private final BlueprintService real;
	private final BlueprintService fill;
	private final Map<Identifier, ArchetypeInfo> infos = new LinkedHashMap<>();

	public LayeredBlueprints(BlueprintService real, BlueprintService fill) {
		this.real = real;
		this.fill = fill;
		for (ArchetypeInfo i : fill.archetypes()) {
			this.infos.put(i.id(), i);
		}
		for (ArchetypeInfo i : real.archetypes()) {
			this.infos.put(i.id(), i);
		}
	}

	public boolean isReal(Identifier archetype) {
		return this.real.archetype(archetype) != null;
	}

	@Override
	public Blueprint blueprint(PlotSpec spec, TerrainProbe terrain) {
		return (isReal(spec.archetype()) ? this.real : this.fill).blueprint(spec, terrain);
	}

	@Override
	public @Nullable ArchetypeInfo archetype(Identifier id) {
		return this.infos.get(id);
	}

	@Override
	public Collection<ArchetypeInfo> archetypes() {
		return this.infos.values();
	}

	@Override
	public Identifier style(Culture culture) {
		return this.real.style(culture);
	}
}
