package dev.voidmc.hearth.social.calendar;

import dev.voidmc.hearth.api.Culture;
import org.jspecify.annotations.Nullable;

/**
 * The feast days of the year. Every settlement keeps midsummer, the harvest feast and the day it was founded; each
 * culture has a festival of its own. A festival names what folk carry, how the square is dressed and whether it is a
 * night festival (lit by fire and lanterns, beginning later and running past midnight).
 */
public enum Festival {
	MIDSUMMER("midsummer", "minecraft:sunflower", Dressing.POLES, false),
	HARVEST("harvest", "minecraft:wheat", Dressing.HAY, false),
	FOUNDERS("founders", "minecraft:cake", Dressing.POLES, false),
	// the cultures' own
	MAY_DAY("may_day", "minecraft:allium", Dressing.POLES, false),
	BLOSSOM("blossom", "minecraft:pink_petals", Dressing.LANTERNS, false),
	MIDWINTER("midwinter", "minecraft:torch", Dressing.BONFIRE, true),
	CATTLE_DRIVE("cattle_drive", "minecraft:bell", Dressing.POLES, false),
	LONG_NIGHT("long_night", "minecraft:lantern", Dressing.BONFIRE, true),
	BEACONS("beacons", "minecraft:torch", Dressing.BONFIRE, true),
	BOATS("boats", "minecraft:cod", Dressing.POLES, false),
	CARNIVAL("carnival", "minecraft:feather", Dressing.POLES, false),
	STARS("stars", "minecraft:lantern", Dressing.LANTERNS, true),
	VINTAGE("vintage", "minecraft:sweet_berries", Dressing.HAY, false),
	GAMES("games", "minecraft:stick", Dressing.POLES, false),
	LANTERNS("lanterns", "minecraft:lantern", Dressing.LANTERNS, true),
	CRAWFISH("crawfish", "minecraft:cooked_cod", Dressing.BONFIRE, false),
	SUNFEAST("sunfeast", "minecraft:sunflower", Dressing.POLES, false);

	/** How the square is dressed while the festival lasts. */
	public enum Dressing {
		/** Tall poles with the town's colours and a lantern on top. */
		POLES,
		/** Hay bales crowned with carved pumpkins. */
		HAY,
		/** A bonfire at the heart and poles round it. */
		BONFIRE,
		/** Lantern posts. */
		LANTERNS
	}

	private final String name;
	/** What folk carry. */
	public final String held;
	public final Dressing dressing;
	/** Begins later and runs past midnight. */
	public final boolean night;

	Festival(String name, String held, Dressing dressing, boolean night) {
		this.name = name;
		this.held = held;
		this.dressing = dressing;
		this.night = night;
	}

	public String id() {
		return this.name;
	}

	public String translationKey() {
		return "hearth.social.festival." + this.name;
	}

	/** The culture's own festival, if it has one. */
	public static @Nullable Festival ofCulture(Culture culture) {
		return switch (culture) {
			case TEMPERATE -> MAY_DAY;
			case WISTERIA -> BLOSSOM;
			case NORTHERN -> MIDWINTER;
			case ALPINE -> CATTLE_DRIVE;
			case TUNDRA -> LONG_NIGHT;
			case MOORLAND -> BEACONS;
			case MARITIME -> BOATS;
			case TROPICAL -> CARNIVAL;
			case DESERT -> STARS;
			case MEDITERRANEAN -> VINTAGE;
			case STEPPE -> GAMES;
			case KARST -> LANTERNS;
			case BAYOU -> CRAWFISH;
			case MESA -> SUNFEAST;
		};
	}

	public static @Nullable Festival byId(String id) {
		for (Festival f : values()) {
			if (f.name.equals(id)) {
				return f;
			}
		}
		return null;
	}
}
