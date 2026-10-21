package dev.voidmc.hearth.api;

import net.minecraft.util.StringRepresentable;

/**
 * A household's place in society (docs/LIVING_WORLD.md §8.1). It decides what home a household wants — so the planner
 * builds hovels by the tannery, artisans' houses over their workshops, merchants' houses by the market and the lord's
 * keep above it all — how folk dress and talk, and how they treat the player. It follows the household's work and purse:
 * prosperity moves a family up, ruin moves it down.
 */
public enum Station implements StringRepresentable {
	POOR("poor"),
	PEASANT("peasant"),
	ARTISAN("artisan"),
	MERCHANT("merchant"),
	CLERGY("clergy"),
	SCHOLAR("scholar"),
	OFFICIAL("official"),
	GENTRY("gentry"),
	LORD("lord");

	public static final StringRepresentable.EnumCodec<Station> CODEC = StringRepresentable.fromEnum(Station::values);

	private final String name;

	Station(String name) {
		this.name = name;
	}

	/** The station a job usually brings, before purse and family move it. */
	public static Station of(JobType job) {
		return switch (job) {
			case NONE, HAULER, CLAY_DIGGER, CHARCOAL_BURNER, BANDIT, PEDLAR, SERVANT -> POOR;
			case CARTER, PILGRIM -> PEASANT;
			case MONK, HERMIT -> CLERGY;
			case TOLLKEEPER, BAILIFF -> OFFICIAL;
			case FARMER, FORESTER, QUARRIER, MINER, FISHER, HERDER, HUNTER, BEEKEEPER, SALTER, BUILDER -> PEASANT;
			case CARPENTER, MASON, SMITH, BAKER, BREWER, TAILOR, KILN_WORKER, GLASSMAKER, SMELTER, TANNER, BUTCHER, CHANDLER,
				PAPERMAKER, DYER, BOATWRIGHT, HEALER, ENTERTAINER, BATH_KEEPER, STABLEMASTER, INNKEEPER, FENCE -> ARTISAN;
			case MERCHANT, MINTER, TRADER, COURIER, MESSENGER -> MERCHANT;
			case PRIEST -> CLERGY;
			case SCHOLAR -> SCHOLAR;
			case GUARD, REEVE, JUDGE, JAILER -> OFFICIAL;
			case STEWARD -> GENTRY;
			case LORD -> LORD;
		};
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}

	public String translationKey() {
		return "hearth.station." + this.name;
	}
}
