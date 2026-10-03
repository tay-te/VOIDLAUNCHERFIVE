package dev.voidmc.hearth.api;

import net.minecraft.util.StringRepresentable;

/**
 * What a resident does for a living. Workplaces (archetypes) declare the jobs they provide, the sim turns each worker
 * into production or labour while the settlement is abstract, and the NPC module gives each job its embodied routine.
 */
public enum JobType implements StringRepresentable {
	NONE("none"),
	/** Raises and finishes buildings, roads and walls: the trade that makes a settlement grow. */
	BUILDER("builder"),
	FARMER("farmer"),
	/** Fells and replants trees outside the settlement: wood. */
	FORESTER("forester"),
	/** Quarries stone and digs ore: stone, clay, metal ore. */
	QUARRIER("quarrier"),
	FISHER("fisher"),
	/** Keeps sheep, cattle, goats, llamas: food, cloth. */
	HERDER("herder"),
	HUNTER("hunter"),
	/** Planks, furniture, doors, carts. */
	CARPENTER("carpenter"),
	/** Bricks, dressed stone, glass and terracotta. */
	MASON("mason"),
	/** Metal, tools, and the coin dies. */
	SMITH("smith"),
	BAKER("baker"),
	BREWER("brewer"),
	/** Runs the inn: beds and meals for travellers, rumours for the player. */
	INNKEEPER("innkeeper"),
	/** Keeps a shop or a market stall: buys and sells with the player. */
	MERCHANT("merchant"),
	/** Mints coin and keeps the treasury and deposits. */
	MINTER("minter"),
	GUARD("guard"),
	PRIEST("priest"),
	SCHOLAR("scholar"),
	/** Carries goods and letters between settlements along the roads. */
	COURIER("courier"),
	TAILOR("tailor");

	public static final StringRepresentable.EnumCodec<JobType> CODEC = StringRepresentable.fromEnum(JobType::values);

	private final String name;

	JobType(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}

	public String translationKey() {
		return "hearth.job." + this.name;
	}
}
