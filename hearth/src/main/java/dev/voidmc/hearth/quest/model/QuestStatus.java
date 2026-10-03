package dev.voidmc.hearth.quest.model;

import net.minecraft.util.StringRepresentable;

/** Where a quest instance is in its life. */
public enum QuestStatus implements StringRepresentable {
	/** On a board or in a resident's mouth, not yet taken. */
	OFFERED("offered"),
	/** Taken by a player; objectives in progress. */
	ACTIVE("active"),
	/** Every objective done; waiting to be turned in to the giver or at the board. */
	READY("ready"),
	COMPLETED("completed"),
	FAILED("failed"),
	ABANDONED("abandoned");

	public static final StringRepresentable.EnumCodec<QuestStatus> CODEC = StringRepresentable.fromEnum(QuestStatus::values);

	private final String name;

	QuestStatus(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}

	public boolean finished() {
		return this == COMPLETED || this == FAILED || this == ABANDONED;
	}

	public boolean inProgress() {
		return this == ACTIVE || this == READY;
	}
}
