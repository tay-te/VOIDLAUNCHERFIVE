package dev.voidmc.hearth.api;

import net.minecraft.util.StringRepresentable;

/** How big a settlement has grown, by population. The planner builds bigger and taller as the tier rises. */
public enum SettlementTier implements StringRepresentable {
	CAMP("camp", 0),
	HAMLET("hamlet", 6),
	VILLAGE("village", 20),
	TOWN("town", 60),
	CITY("city", 150);

	public static final StringRepresentable.EnumCodec<SettlementTier> CODEC = StringRepresentable.fromEnum(SettlementTier::values);

	private final String name;
	/** The population at which a settlement reaches this tier. */
	public final int minPopulation;

	SettlementTier(String name, int minPopulation) {
		this.name = name;
		this.minPopulation = minPopulation;
	}

	public static SettlementTier forPopulation(int population) {
		SettlementTier tier = CAMP;
		for (SettlementTier t : values()) {
			if (population >= t.minPopulation) {
				tier = t;
			}
		}
		return tier;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}

	public String translationKey() {
		return "hearth.tier." + this.name;
	}
}
