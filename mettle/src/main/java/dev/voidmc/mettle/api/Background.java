package dev.voidmc.mettle.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * A background (CHARACTER.md §1): where a character starts, never a limit. Data, so servers can add or rebalance them.
 *
 * @param attributes   points added to the base of 3 in each attribute
 * @param favoured     skills that learn 25 % faster; empty when the player picks them ({@code freeFavoured})
 * @param freePoints   attribute points the player places themself (the Wanderer's 4)
 * @param freeFavoured how many released skills the player picks to favour (the Wanderer's 2)
 * @param kit          what the character starts with; also what the picker shows
 */
public record Background(
	Component name,
	Component tagline,
	int order,
	Map<CharacterAttribute, Integer> attributes,
	List<ResourceKey<Skill>> favoured,
	int freePoints,
	int freeFavoured,
	Optional<Signature> signature,
	List<ItemStackTemplate> kit,
	Component kitSummary
) {
	public static final Codec<Background> CODEC = RecordCodecBuilder.create(i -> i.group(
		ComponentSerialization.CODEC.fieldOf("name").forGetter(Background::name),
		ComponentSerialization.CODEC.fieldOf("tagline").forGetter(Background::tagline),
		Codec.INT.optionalFieldOf("order", 0).forGetter(Background::order),
		Codec.unboundedMap(CharacterAttribute.CODEC, Codec.intRange(0, CharacterAttribute.MAX)).optionalFieldOf("attributes", Map.of()).forGetter(Background::attributes),
		ResourceKey.codec(MettleRegistries.SKILL).listOf().optionalFieldOf("favoured", List.of()).forGetter(Background::favoured),
		Codec.intRange(0, 20).optionalFieldOf("free_points", 0).forGetter(Background::freePoints),
		Codec.intRange(0, 4).optionalFieldOf("free_favoured", 0).forGetter(Background::freeFavoured),
		Signature.CODEC.optionalFieldOf("signature").forGetter(Background::signature),
		ItemStackTemplate.CODEC.listOf().optionalFieldOf("kit", List.of()).forGetter(Background::kit),
		ComponentSerialization.CODEC.optionalFieldOf("kit_summary", Component.empty()).forGetter(Background::kitSummary)
	).apply(i, Background::new));

	/** The background's own bonus in an attribute (not counting points the player placed). */
	public int bonus(CharacterAttribute attribute) {
		return this.attributes.getOrDefault(attribute, 0);
	}

	/** The 48×24 pixel-art scene shown on the background's card. */
	public static Identifier art(ResourceKey<Background> key) {
		return key.identifier().withPath(p -> "textures/gui/background/" + p + ".png");
	}

	/** The one perk only this background has. {@code perk} names the behaviour the character module implements. */
	public record Signature(Identifier perk, Component name, Component description) {
		public static final Codec<Signature> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("perk").forGetter(Signature::perk),
			ComponentSerialization.CODEC.fieldOf("name").forGetter(Signature::name),
			ComponentSerialization.CODEC.fieldOf("description").forGetter(Signature::description)
		).apply(i, Signature::new));
	}
}
