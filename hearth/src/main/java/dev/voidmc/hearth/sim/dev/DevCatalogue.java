package dev.voidmc.hearth.sim.dev;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.BlueprintService;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.growth.Needs;
import dev.voidmc.hearth.plan.testing.BoxBlueprint;
import dev.voidmc.hearth.plan.testing.CatalogueDouble;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * A whole §6.1 catalogue for dev runs while the build module is incomplete: the planner's {@link CatalogueDouble}
 * archetypes, built as walkable stand-ins (doors, windows, beds, workstations, ladders between storeys, a notice board on
 * the square; roads, bridges and terraces exactly on the corridors the planner reserved), so a town can be grown end to
 * end today.
 *
 * <p>With a {@code real} service (dev switch {@code -Dhearth.dev.fillCatalogue=true}), every archetype the real service
 * registers is the real one, and only the archetypes it lacks are stand-ins: a town looks more real with every archetype
 * the architects land. Never installed in a normal run with a build module registered. Thread-safe.
 */
public final class DevCatalogue implements BlueprintService {
	private final @Nullable BlueprintService real;
	private final CatalogueDouble stand = new CatalogueDouble();
	private final Map<Identifier, ArchetypeInfo> infos = new LinkedHashMap<>();
	private final Map<PlotSpec, Blueprint> cache = new ConcurrentHashMap<>();

	public DevCatalogue(@Nullable BlueprintService real) {
		this.real = real;
		if (real != null) {
			for (ArchetypeInfo info : real.archetypes()) {
				this.infos.put(info.id(), info);
			}
		}
		for (ArchetypeInfo info : this.stand.archetypes()) {
			this.infos.putIfAbsent(info.id(), info);
		}
		// phase 3 (LIVING_WORLD.md §8.5): the excavations and the chains' workshops, until the architects land them
		for (ArchetypeInfo info : PlaceholderExcavations.infos()) {
			this.infos.putIfAbsent(info.id(), info);
		}
		for (ArchetypeInfo info : workshops()) {
			this.infos.putIfAbsent(info.id(), info);
		}
	}

	/** The production chains' workshops of LIVING_WORLD.md §8.5, as plain stand-in buildings with their trades' job sites. */
	static java.util.List<ArchetypeInfo> workshops() {
		java.util.List<ArchetypeInfo> out = new java.util.ArrayList<>();
		Object[][] table = {
			{"work/logging_camp", JobType.FORESTER}, {"work/charcoal_kiln", JobType.CHARCOAL_BURNER}, {"work/brick_kiln", JobType.KILN_WORKER},
			{"work/glassworks", JobType.GLASSMAKER}, {"work/smelter", JobType.SMELTER}, {"work/tannery", JobType.TANNER},
			{"work/butcher", JobType.BUTCHER}, {"work/chandler", JobType.CHANDLER}, {"work/dye_works", JobType.DYER},
			{"work/paper_mill", JobType.PAPERMAKER}, {"work/apiary", JobType.BEEKEEPER}, {"work/salt_works", JobType.SALTER},
			{"work/shipyard", JobType.BOATWRIGHT}, {"commerce/caravan_yard", JobType.HAULER}};
		for (Object[] row : table) {
			Identifier id = dev.voidmc.hearth.Hearth.id((String) row[0]);
			ArchetypeCategory cat = ((String) row[0]).startsWith("commerce/") ? ArchetypeCategory.COMMERCE : ArchetypeCategory.WORKPLACE;
			out.add(new ArchetypeInfo(id, cat, java.util.Set.of((JobType) row[1]), 0, 7, 11, 7, 11, 5, 8, dev.voidmc.hearth.api.SettlementTier.HAMLET,
				java.util.Set.of(), java.util.Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS), java.util.List.of(), 0, 1.0));
		}
		return out;
	}

	/** How many archetypes are the real ones (the rest are stand-ins). */
	public int realCount() {
		return this.real == null ? 0 : this.real.archetypes().size();
	}

	public int size() {
		return this.infos.size();
	}

	@Override
	public Blueprint blueprint(PlotSpec spec, TerrainProbe terrain) {
		if (this.real != null && this.real.archetype(spec.archetype()) != null) {
			return this.real.blueprint(spec, terrain);
		}
		Blueprint bp = this.cache.get(spec);
		if (bp == null) {
			bp = standIn(spec, terrain);
			if (this.cache.size() > 4096) {
				this.cache.clear();
			}
			this.cache.put(spec, bp);
		}
		return bp;
	}

	@Override
	public boolean prepare(PlotSpec spec, TerrainProbe terrain) {
		// stand-ins are cheap: only the real ones are worth making in the background
		return this.real == null || this.real.archetype(spec.archetype()) == null || this.real.prepare(spec, terrain);
	}

	private Blueprint standIn(PlotSpec spec, TerrainProbe terrain) {
		ArchetypeInfo info = this.infos.get(spec.archetype());
		if (info == null) {
			throw new IllegalArgumentException("unknown archetype " + spec.archetype());
		}
		Identifier id = spec.archetype();
		if (PlaceholderExcavations.handles(id)) {
			return PlaceholderExcavations.generate(spec, terrain);
		}
		if (id.equals(PlaceholderBlueprints.YARD)) {
			return PlaceholderBlueprints.generate(spec, terrain);
		}
		if (id.equals(PlaceholderBlueprints.WELL)) {
			return PlaceholderBlueprints.generate(spec, terrain);
		}
		if (id.equals(Archetypes.ROAD) || id.equals(Archetypes.STAIRS)) {
			return PlaceholderBlueprints.street(spec, terrain);
		}
		if (id.equals(Archetypes.PLAZA)) {
			return PlaceholderBlueprints.plaza(spec, terrain);
		}
		if (info.category() == ArchetypeCategory.FARM && !id.equals(Archetypes.PASTURE) && !id.equals(Archetypes.ORCHARD)) {
			return PlaceholderBlueprints.field(spec, terrain);
		}
		if (building(info, id)) {
			int beds = Needs.estimateBeds(info, spec);
			Map<JobType, Integer> jobs = new LinkedHashMap<>();
			int slots = Needs.jobSlots(info, spec);
			for (JobType job : info.jobs()) {
				jobs.put(job, Math.max(1, slots / Math.max(1, info.jobs().size())));
			}
			return PlaceholderBlueprints.building(spec, terrain, info, beds, jobs, BoxBlueprint.heightFor(spec, info));
		}
		// roads, stairs, bridges, terraces, walkways, docks, walls, gardens, parks, furniture: the planner's own boxes,
		// laid exactly on what it reserved
		return this.stand.blueprint(spec, terrain);
	}

	/** Things with walls, a door and rooms. */
	private static boolean building(ArchetypeInfo info, Identifier id) {
		if (Archetypes.isLinear(id) || id.equals(Archetypes.WALKWAY) || id.equals(Archetypes.TERRACE)) {
			return false;
		}
		return switch (info.category()) {
			case HOUSING, WORKPLACE, COMMERCE, CIVIC, STORAGE, LANDMARK -> info.minFrontage() >= 5 && info.minDepth() >= 5;
			case DEFENSE -> !id.getPath().endsWith("wall") && !id.getPath().endsWith("palisade");
			default -> false;
		};
	}

	@Override
	public @Nullable ArchetypeInfo archetype(Identifier id) {
		return this.infos.get(id);
	}

	@Override
	public Collection<ArchetypeInfo> archetypes() {
		return Collections.unmodifiableCollection(this.infos.values());
	}

	@Override
	public Identifier style(Culture culture) {
		return this.real != null ? this.real.style(culture) : this.stand.style(culture);
	}
}
