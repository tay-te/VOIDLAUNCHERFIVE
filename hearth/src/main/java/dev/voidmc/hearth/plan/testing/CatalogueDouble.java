package dev.voidmc.hearth.plan.testing;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.BlueprintService;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.plan.catalogue.DefaultCatalogue;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * A stand-in build module for the planner's tests and previews: the §6.1 catalogue's {@link ArchetypeInfo}s and plain
 * box blueprints ({@link BoxBlueprint}). A filter can hide archetypes to check the planner falls back to what is
 * actually registered. Blueprints need the game's blocks, so call {@code Bootstrap.bootStrap()} before asking for one.
 */
public final class CatalogueDouble implements BlueprintService {
	private final Map<Identifier, ArchetypeInfo> infos = new LinkedHashMap<>();

	public CatalogueDouble() {
		this(i -> true);
	}

	public CatalogueDouble(Predicate<ArchetypeInfo> filter) {
		for (ArchetypeInfo info : DefaultCatalogue.all()) {
			if (filter.test(info)) {
				this.infos.put(info.id(), info);
			}
		}
	}

	public static CatalogueDouble without(List<Identifier> hidden) {
		return new CatalogueDouble(i -> !hidden.contains(i.id()));
	}

	@Override
	public Blueprint blueprint(PlotSpec spec, TerrainProbe terrain) {
		ArchetypeInfo info = this.infos.get(spec.archetype());
		if (info == null) {
			throw new IllegalArgumentException("unknown archetype " + spec.archetype());
		}
		return new BoxBlueprint(spec, info, terrain);
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
		return Identifier.fromNamespaceAndPath("hearth", culture.getSerializedName());
	}
}
