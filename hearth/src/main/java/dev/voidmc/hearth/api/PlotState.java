package dev.voidmc.hearth.api;

import net.minecraft.util.StringRepresentable;

public enum PlotState implements StringRepresentable {
	/** Chosen and staked out; nothing built yet. */
	PLANNED("planned"),
	/** Some stages done, some to go. */
	BUILDING("building"),
	BUILT("built"),
	/** Built once, then left (its residents gone, or replaced by an upgrade that has not started). */
	ABANDONED("abandoned"),
	/** Being taken down so something bigger can stand there. */
	DEMOLISHING("demolishing");

	public static final StringRepresentable.EnumCodec<PlotState> CODEC = StringRepresentable.fromEnum(PlotState::values);

	private final String name;

	PlotState(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}
}
