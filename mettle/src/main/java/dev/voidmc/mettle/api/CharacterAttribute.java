package dev.voidmc.mettle.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/** The five attributes. Everyone starts at {@link #BASE}; a background and character levels add to it. */
public enum CharacterAttribute implements StringRepresentable {
	STRENGTH("strength"),
	AGILITY("agility"),
	VITALITY("vitality"),
	WITS("wits"),
	PRESENCE("presence");

	public static final int BASE = 3;
	public static final int MIN = 1;
	public static final int MAX = 20;

	public static final StringRepresentable.EnumCodec<CharacterAttribute> CODEC = StringRepresentable.fromEnum(CharacterAttribute::values);
	public static final StreamCodec<ByteBuf, CharacterAttribute> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Enum::ordinal);

	private final String id;

	CharacterAttribute(String id) {
		this.id = id;
	}

	@Override
	public String getSerializedName() {
		return this.id;
	}

	public Component displayName() {
		return Component.translatable("mettle.attribute." + this.id);
	}
}
