package dev.voidmc.hearth.quest.model;

import net.minecraft.util.StringRepresentable;

/** What one objective of a quest asks, and so what drives its progress on the server. */
public enum ObjectiveKind implements StringRepresentable {
	/** Hand in items at the turn-in (partial deliveries count). Target: an item id or {@code #tag}. */
	DELIVER("deliver", true),
	/** Kill mobs, optionally near a place and at night. Target: an entity id, {@code #tag} or {@code monster}. */
	KILL("kill", false),
	/** Kill one named beast that appears at a place when the player comes near. Target: the entity id. */
	SLAY("slay", false),
	/** Go to a place. May grant an item there (a lost keepsake, a page). */
	REACH("reach", false),
	/** Find a structure: step inside its bounds. Target: the structure id. Grants a proof of the visit. */
	EXPLORE("explore", false),
	/** Stand in one of a set of biomes (Expanse's realms underground). Target: comma-separated biome ids. */
	BIOME("biome", false),
	/** Carry a sealed parcel to another settlement and hand it over there. */
	COURIER("courier", true),
	/** Lead a pack animal to another settlement; it must arrive alive. */
	ESCORT("escort", false),
	/** Find a good site for a new hamlet at a distance band from the settlement. */
	SCOUT("scout", false),
	/** Speak with a particular resident (a message to pass on). */
	TALK("talk", false);

	public static final StringRepresentable.EnumCodec<ObjectiveKind> CODEC = StringRepresentable.fromEnum(ObjectiveKind::values);

	private final String name;
	private final boolean handIn;

	ObjectiveKind(String name, boolean handIn) {
		this.name = name;
		this.handIn = handIn;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}

	/** Progress happens by handing something over (at a board or to a resident), not out in the world. */
	public boolean handIn() {
		return this.handIn;
	}
}
