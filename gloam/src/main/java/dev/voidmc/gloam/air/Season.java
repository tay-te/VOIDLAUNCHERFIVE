package dev.voidmc.gloam.air;

/** The four seasons, in the order the year runs through them. */
public enum Season {
	SPRING("spring"),
	SUMMER("summer"),
	AUTUMN("autumn"),
	WINTER("winter");

	private final String name;

	Season(String name) {
		this.name = name;
	}

	public String serializedName() {
		return this.name;
	}

	/** The season {@code year} (0..1, 0 the first day of spring) falls in. */
	public static Season of(float year) {
		return values()[Math.floorMod((int) Math.floor(year * 4), 4)];
	}
}
