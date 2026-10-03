package dev.voidmc.hearth.plan.catalogue;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.BlueprintService;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.SettlementTier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The archetypes the planner may propose: exactly what the registered {@link BlueprintService} lists (the build module
 * implements the catalogue gradually), or the §6.1 defaults when no build module is registered. Immutable; lists are
 * sorted by id so every choice made from them is deterministic.
 */
public final class Catalogue {
	private static volatile @Nullable Catalogue cached;
	private static volatile @Nullable Object cachedSource;
	private static volatile int cachedSize = -1;

	private final Map<Identifier, ArchetypeInfo> byId = new HashMap<>();
	private final Map<ArchetypeCategory, List<ArchetypeInfo>> byCategory = new EnumMap<>(ArchetypeCategory.class);
	private final @Nullable BlueprintService service;

	public Catalogue(Collection<ArchetypeInfo> infos, @Nullable BlueprintService service) {
		this.service = service;
		List<ArchetypeInfo> sorted = new ArrayList<>(infos);
		sorted.sort(Comparator.comparing(i -> i.id().toString()));
		for (ArchetypeInfo info : sorted) {
			this.byId.put(info.id(), info);
			this.byCategory.computeIfAbsent(info.category(), c -> new ArrayList<>()).add(info);
		}
	}

	/** The catalogue of the registered build module (or the defaults), cached while the service's list is unchanged. */
	public static Catalogue current() {
		if (!HearthServices.hasBlueprints()) {
			Catalogue c = cached;
			if (c == null || cachedSource != DefaultCatalogue.class) {
				c = new Catalogue(DefaultCatalogue.all(), null);
				cached = c;
				cachedSource = DefaultCatalogue.class;
			}
			return c;
		}
		BlueprintService service = HearthServices.blueprints();
		Collection<ArchetypeInfo> infos = service.archetypes();
		Catalogue c = cached;
		if (c == null || cachedSource != service || cachedSize != infos.size()) {
			c = new Catalogue(infos, service);
			cached = c;
			cachedSource = service;
			cachedSize = infos.size();
		}
		return c;
	}

	public @Nullable ArchetypeInfo info(Identifier id) {
		return this.byId.get(id);
	}

	public boolean has(Identifier id) {
		return this.byId.containsKey(id);
	}

	public List<ArchetypeInfo> category(ArchetypeCategory category) {
		return this.byCategory.getOrDefault(category, Collections.emptyList());
	}

	/** The style a culture builds in: the build module's answer, or {@code hearth:<culture>}. */
	public Identifier style(Culture culture) {
		if (this.service != null) {
			try {
				return this.service.style(culture);
			} catch (RuntimeException e) {
				// a build module without a style for this culture yet: fall through to the conventional id
			}
		}
		return Identifier.fromNamespaceAndPath("hearth", culture.getSerializedName());
	}

	/** Whether a settlement of this culture and tier may build this archetype at all. */
	public static boolean allowed(ArchetypeInfo info, Culture culture, SettlementTier tier) {
		return info.suits(culture) && tier.ordinal() >= info.minTier().ordinal();
	}

	/**
	 * The archetype to build for {@code wanted}: itself if it is registered and allowed, otherwise the nearest allowed
	 * archetype of the same category — one sharing a job first, then one of similar size — or null to skip the need.
	 */
	public @Nullable ArchetypeInfo resolve(Identifier wanted, Culture culture, SettlementTier tier) {
		ArchetypeInfo info = this.byId.get(wanted);
		if (info != null && allowed(info, culture, tier)) {
			return info;
		}
		ArchetypeInfo template = info != null ? info : defaultInfo(wanted);
		if (template == null) {
			return null;
		}
		ArchetypeInfo best = null;
		double bestScore = Double.MAX_VALUE;
		for (ArchetypeInfo c : category(template.category())) {
			if (!allowed(c, culture, tier) || c.id().equals(wanted)) {
				continue;
			}
			boolean sharesJob = !Collections.disjoint(c.jobs(), template.jobs());
			if (!template.jobs().isEmpty() && !sharesJob) {
				continue;
			}
			double size = Math.abs(c.minFrontage() + c.maxFrontage() - template.minFrontage() - template.maxFrontage())
				+ Math.abs(c.minDepth() + c.maxDepth() - template.minDepth() - template.maxDepth());
			double score = size + (c.flags().equals(template.flags()) ? 0 : 6);
			if (score < bestScore) {
				bestScore = score;
				best = c;
			}
		}
		return best;
	}

	/** Allowed archetypes providing workplaces for a job, best weight first. */
	public List<ArchetypeInfo> forJob(JobType job, Culture culture, SettlementTier tier) {
		List<ArchetypeInfo> out = new ArrayList<>();
		for (ArchetypeInfo info : this.byId.values()) {
			if (info.jobs().contains(job) && allowed(info, culture, tier) && info.category() != ArchetypeCategory.HOUSING) {
				out.add(info);
			}
		}
		out.sort(Comparator.comparingDouble((ArchetypeInfo i) -> -i.weight()).thenComparing(i -> i.id().toString()));
		return out;
	}

	private static @Nullable ArchetypeInfo defaultInfo(Identifier id) {
		for (ArchetypeInfo info : DefaultCatalogue.all()) {
			if (info.id().equals(id)) {
				return info;
			}
		}
		return null;
	}
}
