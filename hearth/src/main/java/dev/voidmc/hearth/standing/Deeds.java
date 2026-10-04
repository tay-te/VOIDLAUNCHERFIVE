package dev.voidmc.hearth.standing;

/**
 * Deed kinds the standing module itself understands, and the ones its default table (data/hearth/hearth_standing/deeds)
 * describes, for modules reporting through {@code StandingService.deed}. Kinds are free strings: anything not in the
 * table falls back to its namespace's {@code <ns>:default} rule, then to the global default.
 */
public final class Deeds {
	/**
	 * Sets a player's standing in a town outright to {@code weight}, once: the sim hands its old reputation numbers over
	 * with it. Ignored if the standing module already has a number for that player there.
	 */
	public static final String SEED = "hearth:seed";
	/**
	 * Moves the town's standing by exactly {@code weight}: nothing else (no opinions, news or fame). What
	 * {@code Settlement.addReputation} becomes once the sim delegates it here.
	 */
	public static final String ADJUST = "hearth:adjust";

	// Reported by other modules (see the default table for what each does).
	public static final String QUEST_COMPLETED = "quest:completed";
	public static final String QUEST_FAILED = "quest:failed";
	public static final String QUEST_ABANDONED = "quest:abandoned";
	public static final String TRADE = "economy:trade";
	public static final String DONATION = "economy:donation";
	public static final String PROPERTY = "economy:property";
	public static final String HAGGLE_SOURED = "economy:haggle_soured";
	public static final String REPAIR_HELPED = "repair:helped";
	public static final String REPAIR_FUNDED = "repair:funded";
	public static final String BATHED = "social:bathed";
	public static final String GIFT = "npc:gift";
	public static final String RESCUE = "npc:rescue";

	private Deeds() {
	}

	/** The law module's kind for an offence: {@code law:<offence>}. */
	public static String law(dev.voidmc.hearth.api.LawService.Offence offence) {
		return "law:" + offence.getSerializedName();
	}
}
