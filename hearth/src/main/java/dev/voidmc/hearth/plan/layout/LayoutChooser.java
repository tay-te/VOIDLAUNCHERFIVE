package dev.voidmc.hearth.plan.layout;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.plan.model.LayoutType;
import dev.voidmc.hearth.plan.model.Signature;
import dev.voidmc.hearth.plan.model.Site;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * Chooses a settlement's layout and signature at founding: each culture's preferences, weighed by what the land
 * allows (a stilt town needs water, a cliff town a cliff, a ring town flat ground), then drawn by chance, so the same
 * culture on the same kind of land still makes different towns. An Expanse land names its signature settlement
 * outright (ARCHITECTURE.md §7.1) and pulls the layout toward it.
 */
public final class LayoutChooser {
	/** Forces the layout chosen on this thread (previews and tests), or null for the normal choice. */
	public static final ThreadLocal<LayoutType> FORCED = new ThreadLocal<>();

	private LayoutChooser() {
	}

	public record Choice(LayoutType layout, Signature signature) {
	}

	public static Choice choose(Culture culture, Site site, @Nullable ResourceKey<Biome> biome, RandomSource random) {
		Signature signature = signature(culture, site, biome);
		LayoutType forced = FORCED.get();
		if (forced != null) {
			random.nextDouble();
			return new Choice(forced, signature);
		}
		Map<LayoutType, Double> w = new EnumMap<>(LayoutType.class);
		for (LayoutType t : LayoutType.values()) {
			w.put(t, cultureWeight(culture, t) * landWeight(t, site));
		}
		switch (signature) {
			case KARST_CLIFF, PUEBLO -> boost(w, LayoutType.CLIFF, site.cliff() || site.relief() > 24 ? 6 : 1.5);
			case STILT_TOWN, LAGOON -> boost(w, LayoutType.STILT, 6);
			case OASIS -> boost(w, LayoutType.WALLED, 4);
			case HILL_TOWN -> boost(w, LayoutType.HILLSIDE, 5);
			case HARBOUR -> boost(w, LayoutType.LINEAR, 4);
			case ALPINE -> {
				boost(w, LayoutType.MOUNTAIN, 3);
				boost(w, LayoutType.HILLSIDE, 3);
			}
			case MOOR -> boost(w, LayoutType.FARMSTEADS, 2.5);
			case RING_TOWN, STEADING -> boost(w, LayoutType.RING, 6);
			case GARDEN_TOWN -> boost(w, LayoutType.LINEAR, 2);
			default -> {
			}
		}
		double total = 0;
		for (double v : w.values()) {
			total += v;
		}
		double pick = random.nextDouble() * total;
		LayoutType chosen = LayoutType.CROSSROADS;
		for (LayoutType t : LayoutType.values()) {
			pick -= w.get(t);
			if (pick <= 0 && w.get(t) > 0) {
				chosen = t;
				break;
			}
		}
		return new Choice(chosen, signature);
	}

	private static void boost(Map<LayoutType, Double> w, LayoutType t, double factor) {
		w.put(t, Math.max(w.get(t), 0.2) * factor);
	}

	/** The signature settlement of the land, by Expanse biome where there is one, else by culture and land. */
	static Signature signature(Culture culture, Site site, @Nullable ResourceKey<Biome> biome) {
		if (biome != null && biome.identifier().getNamespace().equals("expanse")) {
			switch (biome.identifier().getPath()) {
				case "jade_karst" -> {
					return Signature.KARST_CLIFF;
				}
				case "redwood_giants" -> {
					return Signature.FOREST_HALL;
				}
				case "willow_bayou", "boreal_muskeg" -> {
					return Signature.STILT_TOWN;
				}
				case "opal_dunes", "saguaro_flats" -> {
					return Signature.OASIS;
				}
				case "painted_canyons" -> {
					return Signature.PUEBLO;
				}
				case "olive_groves" -> {
					return Signature.HILL_TOWN;
				}
				case "fjordlands" -> {
					return Signature.HARBOUR;
				}
				case "verdant_peaks", "maple_highlands", "cloud_forest" -> {
					return Signature.ALPINE;
				}
				case "heather_moor" -> {
					return Signature.MOOR;
				}
				case "amber_steppe" -> {
					return Signature.RING_TOWN;
				}
				case "wisteria_vale" -> {
					return Signature.GARDEN_TOWN;
				}
				case "frostbloom_tundra" -> {
					return Signature.STEADING;
				}
				case "palm_coast", "coral_coast" -> {
					return Signature.LAGOON;
				}
				default -> {
				}
			}
		}
		boolean water = site.hasWater() && site.waterDistance() < 64;
		return switch (culture) {
			case KARST -> site.cliff() || site.relief() > 24 ? Signature.KARST_CLIFF : Signature.NONE;
			case BAYOU -> water ? Signature.STILT_TOWN : Signature.NONE;
			case TROPICAL -> water ? Signature.LAGOON : Signature.NONE;
			case DESERT -> Signature.OASIS;
			case MESA -> site.cliff() || site.relief() > 20 ? Signature.PUEBLO : Signature.OASIS;
			case MEDITERRANEAN -> site.slope() > 0.12 ? Signature.HILL_TOWN : Signature.NONE;
			case MARITIME -> water ? Signature.HARBOUR : Signature.NONE;
			case ALPINE -> Signature.ALPINE;
			case MOORLAND -> Signature.MOOR;
			case STEPPE -> Signature.RING_TOWN;
			case WISTERIA -> Signature.GARDEN_TOWN;
			case TUNDRA -> Signature.STEADING;
			case NORTHERN -> Signature.FOREST_HALL;
			default -> Signature.NONE;
		};
	}

	static double cultureWeight(Culture c, LayoutType t) {
		return switch (c) {
			case TEMPERATE -> switch (t) {
				case CROSSROADS -> 3;
				case LINEAR -> 2;
				case FARMSTEADS -> 2;
				case WALLED -> 1.5;
				case HILLSIDE -> 1;
				default -> 0.2;
			};
			case WISTERIA -> switch (t) {
				case CROSSROADS -> 2;
				case LINEAR -> 3;
				case HILLSIDE -> 1;
				case WALLED -> 0.8;
				default -> 0.1;
			};
			case NORTHERN -> switch (t) {
				case CROSSROADS -> 2;
				case FARMSTEADS -> 1.5;
				case LINEAR -> 1.5;
				case WALLED -> 0.6;
				case HILLSIDE -> 0.6;
				default -> 0.1;
			};
			case ALPINE -> switch (t) {
				case HILLSIDE -> 3;
				case MOUNTAIN -> 3;
				case CROSSROADS -> 1;
				case FARMSTEADS -> 0.6;
				default -> 0.1;
			};
			case TUNDRA -> switch (t) {
				case RING -> 3;
				case FARMSTEADS -> 1;
				case CROSSROADS -> 0.6;
				default -> 0.1;
			};
			case MOORLAND -> switch (t) {
				case FARMSTEADS -> 3;
				case CROSSROADS -> 2;
				case HILLSIDE -> 1;
				case LINEAR -> 0.8;
				default -> 0.1;
			};
			case MARITIME -> switch (t) {
				case LINEAR -> 3;
				case HILLSIDE -> 1.5;
				case WALLED -> 1;
				case CROSSROADS -> 0.8;
				case CLIFF -> 0.6;
				default -> 0.1;
			};
			case TROPICAL -> switch (t) {
				case STILT -> 3;
				case LINEAR -> 2;
				case RING -> 1;
				case CROSSROADS -> 0.6;
				default -> 0.1;
			};
			case DESERT -> switch (t) {
				case WALLED -> 3;
				case CROSSROADS -> 1;
				case RING -> 0.5;
				case LINEAR -> 0.5;
				default -> 0.1;
			};
			case MEDITERRANEAN -> switch (t) {
				case HILLSIDE -> 3;
				case WALLED -> 2;
				case LINEAR -> 1;
				case CROSSROADS -> 0.8;
				default -> 0.1;
			};
			case STEPPE -> switch (t) {
				case RING -> 3;
				case FARMSTEADS -> 1.5;
				case CROSSROADS -> 1;
				default -> 0.1;
			};
			case KARST -> switch (t) {
				case CLIFF -> 3;
				case HILLSIDE -> 2;
				case STILT -> 1;
				case LINEAR -> 1;
				default -> 0.1;
			};
			case BAYOU -> switch (t) {
				case STILT -> 4;
				case LINEAR -> 1;
				case CROSSROADS -> 0.4;
				default -> 0.05;
			};
			case MESA -> switch (t) {
				case CLIFF -> 3;
				case WALLED -> 2;
				case CROSSROADS -> 0.5;
				default -> 0.1;
			};
		};
	}

	/** How well the land suits a layout, 0 when it cannot be built there at all. */
	static double landWeight(LayoutType t, Site site) {
		double slope = site.slope();
		boolean water = site.hasWater() && site.waterDistance() < 56;
		return switch (t) {
			case STILT -> water ? 1.5 : 0;
			case LINEAR -> water ? 2 : slope > 0.08 ? 0.9 : 0.6;
			case HILLSIDE -> slope < 0.1 ? 0.15 : slope < 0.7 ? 2 : 1;
			case MOUNTAIN -> slope > 0.45 || site.relief() > 44 ? 3 : 0;
			case CLIFF -> site.cliff() ? 3 : slope > 0.5 ? 1 : 0.05;
			case FARMSTEADS -> slope < 0.15 && site.buildable() > 0.6 ? 1.5 : 0.3;
			case CROSSROADS -> slope < 0.2 ? 1.2 : 0.35;
			case WALLED -> slope < 0.35 ? 1 : 0.5;
			case RING -> slope < 0.1 ? 1.5 : 0.3;
		};
	}
}
