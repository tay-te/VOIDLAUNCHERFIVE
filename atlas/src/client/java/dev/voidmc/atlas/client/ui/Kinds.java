package dev.voidmc.atlas.client.ui;

import dev.voidmc.atlas.api.DiscoveryState;
import dev.voidmc.atlas.api.MarkerGlyph;
import dev.voidmc.atlas.api.Place;
import dev.voidmc.atlas.api.Source;
import dev.voidmc.atlas.api.Tier;
import dev.voidmc.atlas.core.StructureNames;
import java.util.Locale;

/**
 * The kinds of place as the map and its legend show them (the design's "Legend" tab): each with its glyph, its name in
 * the legend, and its group — landmarks, always labelled; places to explore, labelled from 1:1; and the places sighted
 * but not visited yet. Any place maps to one of them, so the legend's toggles cover everything on the map.
 */
public final class Kinds {
	public enum Group {
		LANDMARKS("LANDMARKS  ·  ALWAYS LABELLED"), PLACES("PLACES TO EXPLORE  ·  LABELS FROM 1:1"), SIGHTED("NOT VISITED YET");

		public final String heading;

		Group(String heading) {
			this.heading = heading;
		}
	}

	public enum Kind {
		VILLAGES("Villages", "VILLAGE", MarkerGlyph.VILLAGE, Group.LANDMARKS),
		TEMPLES("Temples", "TEMPLE", MarkerGlyph.TEMPLE, Group.LANDMARKS),
		MONUMENTS("Monuments", "MONUMENT", MarkerGlyph.MONUMENT, Group.LANDMARKS),
		MANSIONS("Mansions", "MANSION", MarkerGlyph.MANSION, Group.LANDMARKS),
		ANCIENT_CITIES("Ancient cities", "ANCIENT CITY", MarkerGlyph.ANCIENT_CITY, Group.LANDMARKS),
		STRONGHOLDS("Strongholds", "STRONGHOLD", MarkerGlyph.TOWER, Group.LANDMARKS),
		FORTS("Outposts and forts", "FORT", MarkerGlyph.KEEP, Group.LANDMARKS),
		TRIAL_CHAMBERS("Trial chambers", "TRIAL CHAMBERS", MarkerGlyph.VAULT, Group.LANDMARKS),
		LANDMARKS("Other landmarks", "LANDMARK", MarkerGlyph.TOWER, Group.LANDMARKS),
		PEAKS("Peaks", "PEAK", MarkerGlyph.PEAK, Group.LANDMARKS),
		LAKES("Lakes", "LAKE", MarkerGlyph.LAKE, Group.LANDMARKS),
		WATERFALLS("Waterfalls", "WATERFALL", MarkerGlyph.WATERFALL, Group.LANDMARKS),
		PORTALS("Ruined portals", "RUINED PORTAL", MarkerGlyph.PORTAL, Group.PLACES),
		WRECKS("Shipwrecks", "SHIPWRECK", MarkerGlyph.WRECK, Group.PLACES),
		CAVES("Cave mouths", "CAVE", MarkerGlyph.CAVE, Group.PLACES),
		RAVINES("Ravines", "RAVINE", MarkerGlyph.CAVE, Group.PLACES),
		GEODES("Geodes", "GEODE", MarkerGlyph.GEM, Group.PLACES),
		DUNGEONS("Dungeons", "DUNGEON", MarkerGlyph.VAULT, Group.PLACES),
		MINESHAFTS("Mineshafts", "MINESHAFT", MarkerGlyph.MINESHAFT, Group.PLACES),
		TRAIL_RUINS("Trail ruins", "TRAIL RUINS", MarkerGlyph.TRAIL, Group.PLACES),
		RUINS("Ruins and camps", "RUIN", MarkerGlyph.DOT, Group.PLACES),
		REGIONS("Regions", "REGION", MarkerGlyph.DOT, Group.LANDMARKS),
		RIVERS("Rivers", "RIVER", MarkerGlyph.LAKE, Group.LANDMARKS),
		SIGHTED("Sighted", "UNKNOWN", MarkerGlyph.UNKNOWN, Group.SIGHTED);

		public final String label;
		/** The caps word in rows and cards ("SHIPWRECK"). */
		public final String word;
		public final MarkerGlyph glyph;
		public final Group group;

		Kind(String label, String word, MarkerGlyph glyph, Group group) {
			this.label = label;
			this.word = word;
			this.glyph = glyph;
			this.group = group;
		}
	}

	private Kinds() {
	}

	/** The kind a place is shown as (not counting whether it is only sighted). */
	public static Kind of(Place p) {
		String k = p.kind();
		switch (k) {
			case Place.PEAK:
				return Kind.PEAKS;
			case Place.LAKE:
				return Kind.LAKES;
			case Place.RIVER:
				return Kind.RIVERS;
			case Place.WATERFALL:
				return Kind.WATERFALLS;
			case Place.CAVE_MOUTH:
				return Kind.CAVES;
			case Place.RAVINE:
				return Kind.RAVINES;
			case Place.REGION:
				return Kind.REGIONS;
			case "atlas:geode":
				return Kind.GEODES;
			case "atlas:dungeon":
				return Kind.DUNGEONS;
			default:
				break;
		}
		String f = StructureNames.family(k);
		String path = f.substring(f.indexOf(':') + 1);
		if (p.category().equals("VILLAGE") || path.contains("village") || path.contains("hamlet") || path.contains("settlement")) {
			return Kind.VILLAGES;
		}
		if (path.contains("pyramid") || path.contains("temple") || path.contains("shrine") || path.contains("pagoda") || path.contains("monastery")) {
			return Kind.TEMPLES;
		}
		if (path.equals("monument")) {
			return Kind.MONUMENTS;
		}
		if (path.contains("mansion") || path.contains("manor")) {
			return Kind.MANSIONS;
		}
		if (path.equals("ancient_city")) {
			return Kind.ANCIENT_CITIES;
		}
		if (path.equals("stronghold")) {
			return Kind.STRONGHOLDS;
		}
		if (path.contains("outpost") || path.contains("fortress") || path.contains("bastion") || path.contains("citadel") || path.equals("end_city")) {
			return Kind.FORTS;
		}
		if (path.equals("trial_chambers")) {
			return Kind.TRIAL_CHAMBERS;
		}
		if (path.contains("ruined_portal")) {
			return Kind.PORTALS;
		}
		if (path.contains("shipwreck")) {
			return Kind.WRECKS;
		}
		if (path.contains("mineshaft")) {
			return Kind.MINESHAFTS;
		}
		if (path.equals("trail_ruins")) {
			return Kind.TRAIL_RUINS;
		}
		if (p.category().equals("DUNGEON")) {
			return Kind.DUNGEONS;
		}
		return p.tier() == Tier.MAJOR || p.category().equals("LANDMARK") ? Kind.LANDMARKS : Kind.RUINS;
	}

	/** The glyph a place is drawn with: a "?" while it is only sighted. */
	public static MarkerGlyph glyph(Place p) {
		return sighted(p) ? MarkerGlyph.UNKNOWN : of(p).glyph;
	}

	/**
	 * Whether a place shows as sighted, a hollow "?": a structure seen and not yet visited. Natural landmarks are never
	 * a "?" — the map itself shows the peak, the lake, the falls — so they keep their glyph until they are visited.
	 */
	public static boolean sighted(Place p) {
		return p.state() != DiscoveryState.DISCOVERED && !p.isNatural();
	}

	/**
	 * The legend entry whose toggle a place answers to: "Sighted" while it is not visited (so all the "?"s go with one
	 * toggle), its kind once it is.
	 */
	public static Kind legend(Place p) {
		return sighted(p) ? Kind.SIGHTED : of(p);
	}

	/** Whether a place is a major landmark on the map (the design's tiers), whatever its own tier says. */
	public static boolean major(Place p) {
		Kind k = of(p);
		return k.group == Group.LANDMARKS;
	}

	/** Where a place comes from, in a row's caps line: SEEN BY YOU, MINECRAFT, NATURAL, EXPANSE, VOID SERVER, HEARTH. */
	public static String origin(Place p) {
		if (sighted(p) && p.source().kind() == Source.Kind.SEEN) {
			return "SEEN BY YOU";
		}
		return switch (p.source().kind()) {
			case SERVER, PLUGIN -> p.source().label();
			default -> p.isNatural() ? "NATURAL" : p.kind().substring(0, Math.max(0, p.kind().indexOf(':'))).toUpperCase(Locale.ROOT);
		};
	}
}
