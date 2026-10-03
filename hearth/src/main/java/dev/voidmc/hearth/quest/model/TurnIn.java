package dev.voidmc.hearth.quest.model;

import net.minecraft.util.StringRepresentable;

/** How a finished quest is handed in. */
public enum TurnIn implements StringRepresentable {
	/** Back to the giver, or at their settlement's notice board. */
	GIVER("giver"),
	/** Completes the moment the last objective is done (the reward is paid wherever the player is). */
	AUTO("auto");

	public static final StringRepresentable.EnumCodec<TurnIn> CODEC = StringRepresentable.fromEnum(TurnIn::values);

	private final String name;

	TurnIn(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}
}
