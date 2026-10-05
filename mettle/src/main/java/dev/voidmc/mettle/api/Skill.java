package dev.voidmc.mettle.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * A skill (CHARACTER.md §3). {@code released} is false for skills whose wave has not shipped: they are shown dimmed as
 * "coming" and cannot be favoured. {@code paths} names the tree's two paths ({@code "a"} and {@code "b"}).
 */
public record Skill(Component name, CharacterAttribute attribute, int order, boolean released, Map<String, Component> paths) {
	public Skill(Component name, CharacterAttribute attribute, int order, boolean released) {
		this(name, attribute, order, released, Map.of());
	}

	public static final Codec<Skill> CODEC = RecordCodecBuilder.create(i -> i.group(
		ComponentSerialization.CODEC.fieldOf("name").forGetter(Skill::name),
		CharacterAttribute.CODEC.fieldOf("attribute").forGetter(Skill::attribute),
		Codec.INT.optionalFieldOf("order", 0).forGetter(Skill::order),
		Codec.BOOL.optionalFieldOf("released", true).forGetter(Skill::released),
		Codec.unboundedMap(Codec.STRING, ComponentSerialization.CODEC).optionalFieldOf("paths", Map.of()).forGetter(Skill::paths)
	).apply(i, Skill::new));

	/** The skill's 16×16 pixel-art icon. */
	public static Identifier icon(ResourceKey<Skill> key) {
		return key.identifier().withPath(p -> "textures/gui/skill/" + p + ".png");
	}
}
