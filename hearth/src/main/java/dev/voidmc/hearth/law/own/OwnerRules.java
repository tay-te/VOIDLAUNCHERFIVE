package dev.voidmc.hearth.law.own;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.LawService.Owner;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import java.util.Comparator;
import java.util.function.ToIntFunction;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Who owns a plot (docs/LIVING_WORLD.md §4): a household its home, a worker their workshop and field, the settlement
 * its halls, stores and walls, a player what they bought; streets, squares and gardens are public. Pure over the
 * settlement record.
 */
public final class OwnerRules {
	/** Oldest adults first, then by id, so the same person always answers for a household. */
	private static final Comparator<Resident> HEAD = Comparator.<Resident>comparingInt(r -> r.age == AgeStage.CHILD ? 1 : 0)
		.thenComparingLong(r -> r.bornDay)
		.thenComparing(r -> r.id);

	private OwnerRules() {
	}

	/** The owner of a plot, or null if it is public or nobody's. */
	public static @Nullable Owner owner(Settlement settlement, PlotView plot) {
		if (plot.owner() != null) {
			return new Owner(settlement.id(), null, plot.owner(), Owner.Kind.PLAYER);
		}
		if (plot.state() == PlotState.PLANNED) {
			return null;
		}
		Identifier archetype = plot.spec().archetype();
		ArchetypeCategory category = category(archetype);
		return switch (category) {
			case INFRASTRUCTURE, LANDSCAPE -> null;
			case HOUSING, NOBLE -> household(settlement, plot);
			case UNDERGROUND -> archetype.getPath().contains("dwelling") ? household(settlement, plot) : settlementOwned(settlement, plot);
			case WORKPLACE, FARM, COMMERCE, EXTRACTION -> {
				Resident worker = head(settlement, plot.id(), r -> r.work);
				if (worker != null) {
					yield new Owner(settlement.id(), worker.id, null, Owner.Kind.WORKPLACE);
				}
				yield settlementOwned(settlement, plot);
			}
			case CIVIC, STORAGE, DEFENSE, LANDMARK, LAW -> settlementOwned(settlement, plot);
		};
	}

	private static @Nullable Owner household(Settlement settlement, PlotView plot) {
		Resident head = head(settlement, plot.id(), r -> r.home);
		if (head != null) {
			return new Owner(settlement.id(), head.id, null, Owner.Kind.HOUSEHOLD);
		}
		// An empty house still belongs to the town (it is for sale); a ruin nobody lives in is anybody's.
		return settlementOwned(settlement, plot);
	}

	private static @Nullable Owner settlementOwned(Settlement settlement, PlotView plot) {
		return plot.state() == PlotState.ABANDONED ? null : new Owner(settlement.id(), null, null, Owner.Kind.SETTLEMENT);
	}

	/** The resident who answers for a plot as home or work, or null. */
	public static @Nullable Resident head(Settlement settlement, int plotId, ToIntFunction<Resident> link) {
		Resident best = null;
		for (Resident r : settlement.residents()) {
			if (link.applyAsInt(r) == plotId && (best == null || HEAD.compare(r, best) < 0)) {
				best = r;
			}
		}
		return best;
	}

	/** The archetype's category: from the build module when it knows the archetype, otherwise from its id. */
	public static ArchetypeCategory category(Identifier archetype) {
		if (HearthServices.hasBlueprints()) {
			ArchetypeInfo info = HearthServices.blueprints().archetype(archetype);
			if (info != null) {
				return info.category();
			}
		}
		return categoryOf(archetype.getPath());
	}

	/** The category an archetype id implies ({@code house/cottage} → housing), for archetypes the build module does not know. */
	public static ArchetypeCategory categoryOf(String path) {
		int slash = path.indexOf('/');
		String family = slash < 0 ? path : path.substring(0, slash);
		return switch (family) {
			case "house" -> ArchetypeCategory.HOUSING;
			case "work" -> ArchetypeCategory.WORKPLACE;
			case "farm" -> ArchetypeCategory.FARM;
			case "commerce" -> ArchetypeCategory.COMMERCE;
			case "civic" -> ArchetypeCategory.CIVIC;
			case "storage" -> ArchetypeCategory.STORAGE;
			case "defense" -> ArchetypeCategory.DEFENSE;
			case "landmark" -> ArchetypeCategory.LANDMARK;
			case "law" -> ArchetypeCategory.LAW;
			case "noble" -> ArchetypeCategory.NOBLE;
			case "extract" -> ArchetypeCategory.EXTRACTION;
			case "deep" -> ArchetypeCategory.UNDERGROUND;
			case "landscape" -> ArchetypeCategory.LANDSCAPE;
			default -> ArchetypeCategory.INFRASTRUCTURE;
		};
	}

	/** Whether a plot of this category is public ground (streets, squares, gardens), never anybody's property. */
	public static boolean isPublic(ArchetypeCategory category) {
		return category == ArchetypeCategory.INFRASTRUCTURE || category == ArchetypeCategory.LANDSCAPE;
	}
}
