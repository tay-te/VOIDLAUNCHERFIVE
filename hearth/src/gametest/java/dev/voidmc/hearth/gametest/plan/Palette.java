package dev.voidmc.hearth.gametest.plan;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.ArchetypeInfo.SiteFlag;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import net.minecraft.resources.Identifier;

/** Colours of the settlement previews: plots by category (walls and roofs), roads by kind, the land. */
final class Palette {
	static final int WATER_SHALLOW = 0x5E9ED8;
	static final int WATER_DEEP = 0x24508C;
	static final int TERRACE = 0xB9A888;
	static final int TERRACE_EDGE = 0x6E5A40;
	static final int OUTLINE = 0x2A2018;

	private Palette() {
	}

	/** The roof (top) colour of a plot in the previews. */
	static int roof(Identifier id, ArchetypeInfo info) {
		if (id.equals(Archetypes.PLAZA)) {
			return 0xCEC6B4;
		}
		if (id.equals(Archetypes.WELL)) {
			return 0x7A8EA8;
		}
		if (id.equals(Archetypes.FOUNTAIN)) {
			return 0x6EB0E0;
		}
		if (id.equals(Archetypes.LAMP_POST)) {
			return 0xF0D060;
		}
		if (id.equals(Archetypes.STATUE)) {
			return 0xB4B4B4;
		}
		if (id.equals(Archetypes.BELL)) {
			return 0xC8A040;
		}
		if (id.equals(Archetypes.GARDEN)) {
			return 0x5E9E4A;
		}
		if (id.equals(Archetypes.PARK)) {
			return 0x74B058;
		}
		if (id.equals(Archetypes.FIELD)) {
			return 0xDDBE55;
		}
		if (id.equals(Archetypes.PASTURE)) {
			return 0x9CC46A;
		}
		if (id.equals(Archetypes.ORCHARD)) {
			return 0x4E8A3A;
		}
		if (id.equals(Archetypes.PADDY)) {
			return 0x74B8A6;
		}
		if (id.equals(Archetypes.TERRACE_FIELD)) {
			return 0xAEB24E;
		}
		if (id.equals(Archetypes.FARMHOUSE)) {
			return 0x8A6440;
		}
		if (id.equals(Archetypes.MARKET_STALL)) {
			return 0xC0506A;
		}
		return switch (info.category()) {
			case HOUSING -> info.flags().contains(SiteFlag.VERTICAL) ? 0x74302A : info.flags().contains(SiteFlag.ATTACHABLE) ? 0x9A3E2E : 0xB0583A;
			case WORKPLACE -> 0x6A5236;
			case COMMERCE -> 0x8E3B66;
			case CIVIC -> 0x3E5A82;
			case LANDMARK -> 0xD0A030;
			case STORAGE -> 0x7A6448;
			case DEFENSE -> 0x5E5E58;
			case FARM -> 0xC8B060;
			case LANDSCAPE -> 0x6AAA50;
			case INFRASTRUCTURE -> 0xA09888;
			case EXTRACTION -> 0x8C8478;
			case LAW -> 0x4A3A5A;
			case NOBLE -> 0x6A2A3A;
			case UNDERGROUND -> 0x5A6A7A;
		};
	}

	/** Wall colour (the sides in the isometric view). */
	static int wall(ArchetypeInfo info) {
		return switch (info.category()) {
			case HOUSING -> info.flags().contains(SiteFlag.VERTICAL) ? 0xD2C0A0 : 0xE0CEA8;
			case WORKPLACE -> 0xA8875A;
			case COMMERCE -> 0xE4D2A4;
			case CIVIC -> 0xDAD6CE;
			case LANDMARK -> 0xEEE6CE;
			case STORAGE -> 0x9E8C6C;
			case DEFENSE -> 0x8C8C86;
			default -> 0xB0A890;
		};
	}

	static int road(String kind, Identifier archetype) {
		if (archetype.equals(Archetypes.BRIDGE)) {
			return "boardwalk".equals(kind) ? 0xA27E50 : 0x84643E;
		}
		if (archetype.equals(Archetypes.DOCK)) {
			return 0x6E5434;
		}
		if (archetype.equals(Archetypes.WALL)) {
			return 0x6C6C66;
		}
		if (archetype.equals(Archetypes.PALISADE)) {
			return 0x6A4A2A;
		}
		if (archetype.equals(Archetypes.WALKWAY)) {
			return 0x9A7A50;
		}
		if (archetype.equals(Archetypes.STAIRS)) {
			return 0xB4ACA0;
		}
		return switch (kind) {
			case "highway" -> 0xB8AE98;
			case "street" -> 0xC4BEB0;
			case "alley" -> 0xA89C88;
			case "path" -> 0xB89C6C;
			default -> 0xCCBC9C;
		};
	}

	/** Land by height above the sea: lowland green to upland olive, rock grey, snow. */
	static int land(int y, int sea, float temperature) {
		double t = (y - sea) / 60.0;
		int low = temperature < 0.15 ? 0xDCE4E8 : temperature > 1.5 ? 0xD8C48A : 0x7CA556;
		int mid = temperature > 1.5 ? 0xC09A60 : 0x9AA060;
		int high = 0x8E8A80;
		if (t < 0.5) {
			return lerp(low, mid, Math.max(0, t) / 0.5);
		}
		if (t < 1.2) {
			return lerp(mid, high, (t - 0.5) / 0.7);
		}
		return lerp(high, 0xF2F4F6, Math.min(1, (t - 1.2) / 0.6));
	}

	static int lerp(int a, int b, double t) {
		t = Math.max(0, Math.min(1, t));
		int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
		return (r << 16) | (g << 8) | bl;
	}

	static int shade(int rgb, double f) {
		int r = (int) Math.max(0, Math.min(255, ((rgb >> 16) & 255) * f));
		int g = (int) Math.max(0, Math.min(255, ((rgb >> 8) & 255) * f));
		int b = (int) Math.max(0, Math.min(255, (rgb & 255) * f));
		return (r << 16) | (g << 8) | b;
	}

	static boolean labelled(ArchetypeInfo info) {
		ArchetypeCategory c = info.category();
		return c == ArchetypeCategory.CIVIC || c == ArchetypeCategory.LANDMARK || c == ArchetypeCategory.COMMERCE && !info.id().equals(Archetypes.MARKET_STALL)
			|| info.id().equals(Archetypes.BUILDERS_YARD) || info.id().equals(Archetypes.WATERMILL) || info.id().equals(Archetypes.WINDMILL)
			|| info.id().equals(Archetypes.MANOR) || info.id().equals(Archetypes.GATEHOUSE);
	}
}
