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
import net.minecraft.util.StringRepresentable;

/**
 * A perk in a skill tree, or an earned one (TREES.md). Data, synced to clients so the overlay can draw the tree.
 *
 * <p>Bought perks: {@code ROOT} (tier 1, ranked), {@code PATH} (tier 2 or 3, on path A or B) and {@code MASTERY}
 * (tier 4, one per skill). Earned perks cost nothing: {@code FEAT} (a deed, see {@code feat}), {@code SYNERGY} (two
 * skills at a level, see {@code synergy}) and {@code TEACHING} (taught or read).
 *
 * @param skill       the tree it belongs to (a synergy lists its first skill here)
 * @param ranks       how many times it can be bought (roots 3, others 1)
 * @param cost        points per rank
 * @param side        for masteries: which path's perks it needs ({@code "a"}, {@code "b"} or {@code "any"})
 * @param description one plain sentence; ranked perks use {@code %s} for the rank value from {@code rankValues}
 * @param effects     attribute modifiers it adds per rank (behaviours are code, keyed by the perk id)
 */
public record Perk(
	ResourceKey<Skill> skill,
	Kind kind,
	int tier,
	String path,
	int ranks,
	int cost,
	String side,
	Component name,
	Component description,
	List<String> rankValues,
	Optional<Component> hint,
	Optional<Feat> feat,
	Map<ResourceKey<Skill>, Integer> synergy,
	List<AttributeEffect> effects,
	int order
) {
	public enum Kind implements StringRepresentable {
		ROOT, PATH, MASTERY, FEAT, SYNERGY, TEACHING;

		public static final StringRepresentable.EnumCodec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		/** Bought with points (and refunded by a respec); the rest are earned and kept. */
		public boolean bought() {
			return this == ROOT || this == PATH || this == MASTERY;
		}
	}

	/** What earns a feat: a counter (see {@code character.FeatCounters}) reaching a number. */
	public record Feat(String counter, long target) {
		public static final Codec<Feat> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("counter").forGetter(Feat::counter),
			Codec.LONG.fieldOf("target").forGetter(Feat::target)
		).apply(i, Feat::new));
	}

	/** An attribute modifier per rank, e.g. Pack Mule's +8 kg carry capacity. */
	public record AttributeEffect(Identifier attribute, List<Double> amounts, String operation) {
		public static final Codec<AttributeEffect> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("attribute").forGetter(AttributeEffect::attribute),
			Codec.DOUBLE.listOf().fieldOf("amounts").forGetter(AttributeEffect::amounts),
			Codec.STRING.optionalFieldOf("operation", "add_value").forGetter(AttributeEffect::operation)
		).apply(i, AttributeEffect::new));
	}

	public static final Codec<Perk> CODEC = RecordCodecBuilder.create(i -> i.group(
		ResourceKey.codec(MettleRegistries.SKILL).fieldOf("skill").forGetter(Perk::skill),
		Kind.CODEC.fieldOf("kind").forGetter(Perk::kind),
		Codec.intRange(0, 4).optionalFieldOf("tier", 0).forGetter(Perk::tier),
		Codec.STRING.optionalFieldOf("path", "").forGetter(Perk::path),
		Codec.intRange(1, 5).optionalFieldOf("ranks", 1).forGetter(Perk::ranks),
		Codec.intRange(0, 10).optionalFieldOf("cost", 0).forGetter(Perk::cost),
		Codec.STRING.optionalFieldOf("side", "any").forGetter(Perk::side),
		ComponentSerialization.CODEC.fieldOf("name").forGetter(Perk::name),
		ComponentSerialization.CODEC.fieldOf("description").forGetter(Perk::description),
		Codec.STRING.listOf().optionalFieldOf("rank_values", List.of()).forGetter(Perk::rankValues),
		ComponentSerialization.CODEC.optionalFieldOf("hint").forGetter(Perk::hint),
		Feat.CODEC.optionalFieldOf("feat").forGetter(Perk::feat),
		Codec.unboundedMap(ResourceKey.codec(MettleRegistries.SKILL), Codec.intRange(1, 20)).optionalFieldOf("synergy", Map.of()).forGetter(Perk::synergy),
		AttributeEffect.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(Perk::effects),
		Codec.INT.optionalFieldOf("order", 0).forGetter(Perk::order)
	).apply(i, Perk::new));

	/** The perk's 16×16 pixel-art icon. */
	public static Identifier icon(ResourceKey<Perk> key) {
		String path = key.identifier().getPath();
		return key.identifier().withPath("textures/gui/perk/" + path.substring(path.lastIndexOf('/') + 1) + ".png");
	}
}
