package dev.voidmc.hearth.npc.work;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.ResourceSite;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Excavation plots (docs/LIVING_WORLD.md §7.3, §8.5): quarry pits, mines and shafts, clay and sand pits, plantations —
 * blueprints whose steps are cuts (air) plus the props, rails, ladders, lanterns and saplings that go with them, worked
 * stage by stage for weeks. Builders leave them alone; the gatherer whose trade fits works them, under the same contract
 * builders keep (the next step, read at the moment of placing, then {@code advanceConstruction}).
 *
 * <p>Which trade works which kind:
 * <pre>
 *   MINER         ore, specialty (mine, shaft), then stone
 *   QUARRIER      stone (quarry pit), then ore
 *   CLAY_DIGGER   clay, sand (clay pit, sand pit)
 *   SALTER        salt
 *   FORESTER      woods (plantation)
 * </pre>
 */
public final class Extraction {
	private Extraction() {
	}

	/** Whether a plot is an excavation: its archetype is {@code EXTRACTION}, or (before the build module knows it) an {@code extract/} id. */
	public static boolean is(PlotView plot) {
		Identifier id = plot.spec().archetype();
		if (id.getPath().startsWith("extract/")) {
			return true;
		}
		if (!HearthServices.hasBlueprints()) {
			return false;
		}
		ArchetypeInfo info = HearthServices.blueprints().archetype(id);
		return info != null && info.category() == ArchetypeCategory.EXTRACTION;
	}

	/** The kind of resource an excavation works: its {@code kind} param, else read from the archetype's name. */
	public static ResourceSite.@Nullable Kind kind(PlotView plot) {
		String param = plot.spec().params().getStringOr("kind", "");
		for (ResourceSite.Kind k : ResourceSite.Kind.values()) {
			if (k.getSerializedName().equals(param)) {
				return k;
			}
		}
		return kindOf(plot.spec().archetype().getPath());
	}

	/** The kind an excavation archetype works, by its name ({@code extract/quarry_pit} → stone). */
	public static ResourceSite.@Nullable Kind kindOf(String archetypePath) {
		String name = archetypePath.substring(archetypePath.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
		if (name.contains("quarry")) {
			return ResourceSite.Kind.STONE;
		}
		if (name.contains("mine") || name.contains("shaft") || name.contains("adit")) {
			return ResourceSite.Kind.ORE;
		}
		if (name.contains("clay")) {
			return ResourceSite.Kind.CLAY;
		}
		if (name.contains("sand")) {
			return ResourceSite.Kind.SAND;
		}
		if (name.contains("salt")) {
			return ResourceSite.Kind.SALT;
		}
		if (name.contains("plantation") || name.contains("woodlot")) {
			return ResourceSite.Kind.WOODS;
		}
		return null;
	}

	/**
	 * How well a trade fits a kind of excavation: 2 its own, 1 it will work if nothing of its own is open, 0 not at all.
	 */
	public static int fit(JobType job, ResourceSite.@Nullable Kind kind) {
		if (kind == null) {
			return job == JobType.MINER || job == JobType.QUARRIER ? 1 : 0;
		}
		return switch (job) {
			case MINER -> kind == ResourceSite.Kind.ORE || kind == ResourceSite.Kind.SPECIALTY ? 2 : kind == ResourceSite.Kind.STONE ? 1 : 0;
			case QUARRIER -> kind == ResourceSite.Kind.STONE ? 2 : kind == ResourceSite.Kind.ORE ? 1 : 0;
			case CLAY_DIGGER -> kind == ResourceSite.Kind.CLAY || kind == ResourceSite.Kind.SAND ? 2 : 0;
			case SALTER -> kind == ResourceSite.Kind.SALT ? 2 : 0;
			case FORESTER -> kind == ResourceSite.Kind.WOODS ? 2 : 0;
			default -> 0;
		};
	}

	/** Open for work now: under construction (cutting), its stage's goods in hand. */
	public static boolean workable(PlotView plot) {
		return plot.state() == PlotState.BUILDING && !plot.waitingForGoods() && is(plot);
	}

	/** Where gatherers stand to work an excavation: its blueprint's {@code WORK_FACE} POIs. */
	public static List<Poi> workFaces(Blueprint blueprint) {
		List<Poi> out = new ArrayList<>();
		for (Poi p : blueprint.pois()) {
			if (p.type() == PoiType.WORK_FACE) {
				out.add(p);
			}
		}
		return out;
	}

	/** The excavation's store (a spoil heap, a crate at the pit head), if its blueprint has one. */
	public static @Nullable Poi storage(Blueprint blueprint) {
		for (Poi p : blueprint.pois()) {
			if (p.type() == PoiType.STORAGE) {
				return p;
			}
		}
		return null;
	}
}
