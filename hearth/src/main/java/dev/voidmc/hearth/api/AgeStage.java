package dev.voidmc.hearth.api;

import net.minecraft.util.StringRepresentable;

public enum AgeStage implements StringRepresentable {
	CHILD("child"),
	ADULT("adult"),
	ELDER("elder");

	public static final StringRepresentable.EnumCodec<AgeStage> CODEC = StringRepresentable.fromEnum(AgeStage::values);

	private final String name;

	AgeStage(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}
}
