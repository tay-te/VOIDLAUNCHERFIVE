package dev.voidmc.gloam.sound;

/**
 * The soundscape's ambient beds: each one looping clip ({@code assets/gloam/sounds/bed/<key>.ogg}, made by
 * {@code tools/gen_sounds.py}) whose volume {@link Mix} chooses from the air and the land. A positional bed is heard
 * from the direction its source lies in (the brook, the shore, the pond); the rest are all around.
 *
 * <p>The outdoor beds come first and {@link Mix} sets them; the underground beds after them ({@link #underground()})
 * and {@link CaveMix} sets those.
 */
public enum Bed {
	WIND_OPEN("wind_open", 0.55F, false),
	WIND_LEAVES("wind_leaves", 0.7F, false),
	WIND_HIGH("wind_high", 0.6F, false),
	RAIN_GROUND("rain_ground", 0.6F, false),
	RAIN_LEAVES("rain_leaves", 0.7F, false),
	RAIN_ROOF("rain_roof", 0.65F, false),
	DAWN_CHORUS("dawn_chorus", 0.5F, false),
	BIRDS_DAY("birds_day", 0.45F, false),
	CRICKETS("crickets", 0.35F, false),
	FROGS("frogs", 0.45F, true),
	CICADAS("cicadas", 0.3F, false),
	STREAM("stream", 0.65F, true),
	WAVES("waves", 0.6F, true),
	// ---- underground (CaveMix): from Expanse's caverns around the camera
	DRIP_NEAR("drip_near", 0.55F, false),
	DRIP_HALL("drip_hall", 0.6F, false),
	BATS("bats", 0.4F, true),
	UNDER_RIVER("under_river", 0.75F, true),
	BURIED_SEA("buried_sea", 0.65F, true),
	MINE_CREAK("mine_creak", 0.5F, false),
	CAVERN_WIND("cavern_wind", 0.55F, false);

	private final String key;
	private final float gain;
	private final boolean positional;

	Bed(String key, float gain, boolean positional) {
		this.key = key;
		this.gain = gain;
		this.positional = positional;
	}

	/** The clip's name, as in sounds.json ({@code bed.<key>}) and the logs. */
	public String key() {
		return this.key;
	}

	/** The bed's loudness at full strength, which balances the clips against each other. */
	public float gain() {
		return this.gain;
	}

	/** Whether the bed is one of the caverns' ({@link CaveMix}) rather than the land's ({@link Mix}). */
	public boolean underground() {
		return this.ordinal() >= DRIP_NEAR.ordinal();
	}

	/** Whether the bed is heard from the direction of what makes it, rather than all around. */
	public boolean positional() {
		return this.positional;
	}
}
