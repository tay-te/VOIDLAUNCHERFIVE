package dev.voidmc.hearth.build.style;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.build.terrain.Site;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Who and where a building is, as far as its dress is concerned: what the {@link When} conditions of sub-styles and
 * regional styles are tested against. Read once per plot from its spec and the ground under it.
 *
 * <p><b>Station.</b> The plot's {@code station} param (a {@link Station} name) when the planner gives one
 * ({@link #explicitStation()}: it also shifts the finish, see {@link StationFinish}); otherwise the station the archetype
 * and its district imply (a manor is the gentry's, a chapel the clergy's, a house in the rich district a merchant's), which
 * only chooses the dress.
 *
 * <p><b>Purposes</b> group archetypes across categories: {@code home} (housing), {@code workshop} (workplaces, extraction),
 * {@code religious} (chapels, temples, cathedral, pagoda, monastery, convent, shrine, rectory, cemetery), {@code noble}
 * (the noble category and the manor), {@code fort} (defence, keeps and castles, guardhouse, jail, watch house),
 * {@code public} (civic, law, landmarks), {@code trade} (commerce), {@code farmstead} (farms, barns, granaries),
 * {@code grand} (landmarks, noble seats, the manor, the town hall), {@code underground} (the deep category).
 *
 * <p><b>Landforms</b> come from the ground: {@code flat}, {@code slope} (2+ blocks of relief over the plot),
 * {@code steep} (5+), {@code water} (water on or beside the plot), {@code coast} (water at sea level), {@code river}
 * (water above it), {@code lowland} (floor within 10 of the sea), {@code highland} (40+ above it), {@code mountain}
 * (90+), {@code cold} (it snows), {@code underground} (a {@code realm} or {@code underground} param, or an
 * underground archetype); the param {@code landform} (comma list) adds the planner's own ({@code tepui},
 * {@code fjord}...). The param {@code biome} stands in for the probe's biome (previews, tests, the planner's choice).
 */
public record StyleContext(@Nullable Culture culture, String archetype, @Nullable ArchetypeCategory category, Set<String> purposes, int tier,
	@Nullable Station station, boolean explicitStation, @Nullable String district, @Nullable Identifier biome, Set<String> landforms,
	@Nullable String region) {

	/** A context that knows nothing: only unconditional dressings and conditions on nothing match it. */
	public static final StyleContext NONE = new StyleContext(null, "", null, Set.of(), 2, null, false, null, null, Set.of(), null);

	public StyleContext {
		purposes = Set.copyOf(purposes);
		landforms = Set.copyOf(landforms);
	}

	/** Whether the archetype is of a category or purpose (by its serialized name). */
	public boolean isA(String categoryOrPurpose) {
		return this.category != null && this.category.getSerializedName().equals(categoryOrPurpose) || this.purposes.contains(categoryOrPurpose);
	}

	/** The context of a plot. */
	public static StyleContext of(PlotSpec spec, Site site, @Nullable Culture culture) {
		CompoundTag p = spec.params();
		String path = spec.archetype().getPath();
		ArchetypeCategory category = category(path);
		Set<String> purposes = purposes(path, category);
		String district = p.contains("district") ? p.getStringOr("district", "").toLowerCase(Locale.ROOT) : null;
		Station station = null;
		boolean explicit = false;
		if (p.contains("station")) {
			station = Station.CODEC.byName(p.getStringOr("station", "").toLowerCase(Locale.ROOT));
			explicit = station != null;
		}
		if (station == null) {
			station = implied(path, category, purposes, district, spec.tier());
		}
		Identifier biome = site.biome();
		if (p.contains("biome")) {
			biome = Identifier.tryParse(p.getStringOr("biome", ""));
		}
		Set<String> landforms = landforms(site, spec, category);
		String region = p.contains("region") ? p.getStringOr("region", "").toLowerCase(Locale.ROOT) : null;
		return new StyleContext(culture, path, category, purposes, Math.clamp(spec.tier(), 0, 4), station, explicit, district, biome, landforms, region);
	}

	/** The category an archetype's id names by convention ({@code house/...} housing, {@code civic/...} civic...). */
	public static @Nullable ArchetypeCategory category(String path) {
		int slash = path.indexOf('/');
		String head = slash < 0 ? path : path.substring(0, slash);
		return switch (head) {
			case "house" -> ArchetypeCategory.HOUSING;
			case "work" -> ArchetypeCategory.WORKPLACE;
			case "farm" -> ArchetypeCategory.FARM;
			case "commerce" -> ArchetypeCategory.COMMERCE;
			case "civic" -> ArchetypeCategory.CIVIC;
			case "storage" -> ArchetypeCategory.STORAGE;
			case "defense" -> ArchetypeCategory.DEFENSE;
			case "infra" -> ArchetypeCategory.INFRASTRUCTURE;
			case "landscape" -> ArchetypeCategory.LANDSCAPE;
			case "landmark" -> ArchetypeCategory.LANDMARK;
			case "extract" -> ArchetypeCategory.EXTRACTION;
			case "law" -> ArchetypeCategory.LAW;
			case "noble" -> ArchetypeCategory.NOBLE;
			case "deep" -> ArchetypeCategory.UNDERGROUND;
			default -> null;
		};
	}

	private static boolean any(String path, String... names) {
		for (String n : names) {
			if (When.glob(n, path)) {
				return true;
			}
		}
		return false;
	}

	static Set<String> purposes(String path, @Nullable ArchetypeCategory c) {
		Set<String> out = new HashSet<>();
		if (c == ArchetypeCategory.HOUSING) {
			out.add("home");
		}
		if (c == ArchetypeCategory.WORKPLACE || c == ArchetypeCategory.EXTRACTION) {
			out.add("workshop");
		}
		if (any(path, "civic/chapel*", "civic/monastery", "civic/convent", "civic/shrine", "civic/cemetery", "landmark/cathedral", "landmark/temple",
			"landmark/pagoda", "house/rectory")) {
			out.add("religious");
		}
		if (c == ArchetypeCategory.NOBLE || path.equals("house/manor")) {
			out.add("noble");
		}
		if (c == ArchetypeCategory.DEFENSE || any(path, "noble/keep", "noble/castle", "civic/guardhouse", "law/jail", "law/watch_house")) {
			out.add("fort");
		}
		if (c == ArchetypeCategory.CIVIC || c == ArchetypeCategory.LAW || c == ArchetypeCategory.LANDMARK) {
			out.add("public");
		}
		if (c == ArchetypeCategory.COMMERCE) {
			out.add("trade");
		}
		if (c == ArchetypeCategory.FARM || any(path, "work/barn", "storage/granary", "noble/stables")) {
			out.add("farmstead");
		}
		if (c == ArchetypeCategory.LANDMARK || any(path, "noble/keep", "noble/castle", "noble/palace", "house/manor", "civic/town_hall")) {
			out.add("grand");
		}
		if (c == ArchetypeCategory.UNDERGROUND) {
			out.add("underground");
		}
		return out;
	}

	/**
	 * The station a plot's archetype and district imply when the planner gives none: homes by their kind or their
	 * district (crofts and cottages by the fields, artisans' houses on the crafts streets, merchants' by the market and
	 * in the rich quarter), public buildings by who keeps them. Null where nothing is implied (an ordinary house in an
	 * ordinary street, roads, squares).
	 */
	static @Nullable Station implied(String path, @Nullable ArchetypeCategory c, Set<String> purposes, @Nullable String district, int tier) {
		switch (path) {
			case "house/hovel", "house/shack", "house/hermit_hut", "civic/almshouse" -> {
				return Station.POOR;
			}
			case "house/croft", "house/hut" -> {
				return Station.PEASANT;
			}
			case "house/merchant_house", "house/shophouse", "commerce/counting_house", "commerce/bank" -> {
				return Station.MERCHANT;
			}
			case "house/manor", "noble/hunting_lodge", "noble/stables" -> {
				return Station.GENTRY;
			}
			case "house/reeve_house", "civic/town_hall", "civic/guardhouse" -> {
				return Station.OFFICIAL;
			}
			case "house/rectory" -> {
				return Station.CLERGY;
			}
			case "house/scribe_house", "civic/library", "civic/school", "civic/university", "civic/observatory", "work/alchemist_tower" -> {
				return Station.SCHOLAR;
			}
			default -> {
			}
		}
		if (c == ArchetypeCategory.NOBLE) {
			return Station.LORD;
		}
		if (purposes.contains("religious")) {
			return Station.CLERGY;
		}
		if (c == ArchetypeCategory.LAW || c == ArchetypeCategory.DEFENSE) {
			return Station.OFFICIAL;
		}
		if (c == ArchetypeCategory.FARM) {
			return Station.PEASANT;
		}
		if (c == ArchetypeCategory.WORKPLACE) {
			return path.equals("work/barn") || path.equals("work/hunters_lodge") || path.equals("work/quarry") ? Station.PEASANT : Station.ARTISAN;
		}
		if (c == ArchetypeCategory.COMMERCE) {
			return path.equals("commerce/inn") ? Station.ARTISAN : Station.MERCHANT;
		}
		if (c == ArchetypeCategory.HOUSING && district != null) {
			return switch (district) {
				case "rich" -> tier >= 3 ? Station.GENTRY : Station.MERCHANT;
				case "market" -> Station.MERCHANT;
				case "crafts", "harbour" -> Station.ARTISAN;
				case "farms" -> Station.PEASANT;
				default -> null;
			};
		}
		return null;
	}

	static Set<String> landforms(Site site, PlotSpec spec, @Nullable ArchetypeCategory c) {
		Set<String> out = new HashSet<>();
		int w = site.frontage();
		int dp = site.depth();
		int relief = site.maxGround(0, 0, w - 1, dp - 1) - site.minGround(0, 0, w - 1, dp - 1);
		out.add(relief >= 5 ? "steep" : relief >= 2 ? "slope" : "flat");
		if (relief >= 5) {
			out.add("slope");
		}
		int m = Site.MARGIN;
		boolean coast = false;
		boolean water = false;
		for (int z = -m; z < dp + m; z++) {
			for (int x = -m; x < w + m; x++) {
				int wy = site.water(x, z);
				if (wy != Site.NONE) {
					water = true;
					if (wy - site.seaLevel() >= 0 && wy - site.seaLevel() <= 1) {
						coast = true;
					}
				}
			}
		}
		if (water) {
			out.add("water");
			out.add(coast ? "coast" : "river");
		}
		int aboveSea = -site.seaLevel();
		if (aboveSea <= 10) {
			out.add("lowland");
		}
		if (aboveSea >= 40) {
			out.add("highland");
		}
		if (aboveSea >= 90) {
			out.add("mountain");
		}
		if (site.snowy()) {
			out.add("cold");
		}
		CompoundTag p = spec.params();
		if (c == ArchetypeCategory.UNDERGROUND || p.contains("realm") || p.getBooleanOr("underground", false)) {
			out.add("underground");
		}
		if (p.contains("landform")) {
			for (String l : p.getStringOr("landform", "").split(",")) {
				if (!l.isBlank()) {
					out.add(l.trim().toLowerCase(Locale.ROOT));
				}
			}
		}
		return out;
	}
}
