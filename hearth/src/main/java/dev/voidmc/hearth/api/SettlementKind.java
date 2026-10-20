package dev.voidmc.hearth.api;

import net.minecraft.util.StringRepresentable;

/** What a settlement record is: a growing town, or a post in the wild with a fixed programme (docs/LIVING_WORLD_2.md §7). */
public enum SettlementKind implements StringRepresentable {
	TOWN("town"),
	/** An inn, toll gate, hospice, hermitage...: {@link Settlement#postType()} says which; it never grows past its programme. */
	POST("post");

	public static final StringRepresentable.EnumCodec<SettlementKind> CODEC = StringRepresentable.fromEnum(SettlementKind::values);

	private final String name;

	SettlementKind(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}
}
