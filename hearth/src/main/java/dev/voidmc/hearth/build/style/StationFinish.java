package dev.voidmc.hearth.build.style;

import com.google.gson.JsonObject;
import dev.voidmc.hearth.api.Station;
import java.util.EnumMap;
import java.util.Map;

/**
 * How a household's station dresses its house within its culture (docs/LIVING_WORLD.md §8.1): the poor build rough,
 * patch their roofs and let the moss in; merchants dress their stone and glaze their windows; the lord's house is carved,
 * clean and hung with copper. Applied only when the plot's {@code station} param names a station (an implied station
 * chooses a sub-style but does not move the finish, which the planner's tier already carries).
 *
 * @param finish      tiers added to the plot's tier when its materials are chosen (a merchant's house at tier 2 wears
 *                    the style's tier-3 materials); the plot's own tier, which sizes the building, is unchanged
 * @param weathering  moss and cracks: above 1 more ({@link Palette#weathered}), below 1 cleaner
 * @param patchedRoof the roof mended in patches of another material
 * @param localShare  how much of the territory's material the household builds with (1: all of it; lords import)
 * @param roles       roles the station dresses its own way (a lord's copper lanterns), over everything else
 */
public record StationFinish(int finish, double weathering, boolean patchedRoof, double localShare, Map<Role, Palette> roles) {
	public static final StationFinish NONE = new StationFinish(0, 1, false, 0.85, Map.of());

	public StationFinish {
		roles = Map.copyOf(roles);
	}

	/** The defaults every style starts from. */
	public static StationFinish defaults(Station s) {
		return switch (s) {
			case POOR -> new StationFinish(-2, 1.9, true, 1.0, Map.of());
			case PEASANT -> new StationFinish(-1, 1.35, false, 1.0, Map.of());
			case ARTISAN -> new StationFinish(0, 1.0, false, 0.85, Map.of());
			case MERCHANT -> new StationFinish(1, 0.6, false, 0.6, Map.of());
			case CLERGY, SCHOLAR -> new StationFinish(1, 0.75, false, 0.7, Map.of());
			case OFFICIAL -> new StationFinish(1, 0.8, false, 0.7, Map.of());
			case GENTRY -> new StationFinish(2, 0.45, false, 0.45, Map.of());
			case LORD -> new StationFinish(2, 0.3, false, 0.35, Map.of(Role.LIGHT, Palette.of(Material.of("minecraft:copper_lantern|minecraft:lantern"))));
		};
	}

	/** The finish tier for a plot tier. */
	public int tier(int plotTier) {
		return Math.clamp(plotTier + this.finish, 0, 4);
	}

	/** A style's override over the defaults: any key present replaces the default's, roles are merged. */
	static StationFinish parse(JsonObject o, StationFinish d, Map<Role, Palette> roles) {
		Map<Role, Palette> merged = new EnumMap<>(Role.class);
		merged.putAll(d.roles);
		merged.putAll(roles);
		return new StationFinish(Habits.Json.intOr(o, "finish", d.finish), Habits.Json.doubleOr(o, "weathering", d.weathering),
			Habits.Json.boolOr(o, "patched_roof", d.patchedRoof), Habits.Json.doubleOr(o, "local_share", d.localShare), merged);
	}
}
