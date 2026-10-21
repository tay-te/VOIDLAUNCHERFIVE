package dev.voidmc.mettle.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;

/**
 * A card trait (docs/CARDS.md §4): a small effect from one skill's pool, rolled onto a card with a value between
 * {@code min} and {@code max}. Data, synced, loaded from {@code data/<ns>/mettle/trait/<skill>/<id>.json}.
 *
 * @param kind        which attribute boosts it (might: Strength, finesse: Agility, endurance: Vitality, fortune: Presence)
 * @param scope       broad traits (all digging, any damage) roll at most 12 %, narrow ones at most 25 %
 * @param min         the lowest value it rolls, in its unit (percent traits in percent: 5 means 5 %)
 * @param step        values round to this (1 for percent, 0.5 for blocks, hearts and seconds)
 * @param unit        how the value reads: {@code percent} ("9 %") or {@code flat} (a plain number; the description says what)
 * @param effect      an attribute modifier (sized value × scale), or a behaviour id handled in code
 * @param description one plain sentence with {@code %s} for the value
 * @param arsenal     Smithing only: the sentence when VOID Arsenal is loaded (§10)
 * @param requires    rolled only while this holds ({@code hearth_bonus}: the loaded VOID Hearth reads bonus keys,
 *                    docs/BUILD_FEEL.md §14), so no card carries a trait that does nothing; empty: always
 * @param boosted     whether its attribute boosts it (§4.3); whole-level traits (Speech's Persuasive, Shrewd) are not
 */
public record Trait(
	ResourceKey<Skill> skill,
	Kind kind,
	Scope scope,
	double min,
	double max,
	double step,
	String unit,
	Effect effect,
	Component name,
	Component description,
	Optional<Component> arsenal,
	Optional<String> requires,
	boolean boosted
) {
	/** {@link #requires}: the loaded VOID Hearth reads {@code PlayerSkills.bonus} keys. */
	public static final String HEARTH_BONUS = "hearth_bonus";

	public Trait(ResourceKey<Skill> skill, Kind kind, Scope scope, double min, double max, double step, String unit, Effect effect, Component name,
		Component description, Optional<Component> arsenal) {
		this(skill, kind, scope, min, max, step, unit, effect, name, description, arsenal, Optional.empty(), true);
	}

	public enum Kind implements StringRepresentable {
		MIGHT(CharacterAttribute.STRENGTH),
		FINESSE(CharacterAttribute.AGILITY),
		ENDURANCE(CharacterAttribute.VITALITY),
		FORTUNE(CharacterAttribute.PRESENCE);

		public static final StringRepresentable.EnumCodec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

		/** The attribute that boosts traits of this kind. */
		public final CharacterAttribute attribute;

		Kind(CharacterAttribute attribute) {
			this.attribute = attribute;
		}

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public enum Scope implements StringRepresentable {
		BROAD, NARROW;

		public static final StringRepresentable.EnumCodec<Scope> CODEC = StringRepresentable.fromEnum(Scope::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/**
	 * What a trait does: either an attribute modifier of {@code value × scale} (optionally only while {@code when} holds:
	 * {@code holding_blade}, {@code holding_heavy}, {@code sprinting}, {@code on_path}, {@code bloodied}…), or a
	 * {@code behaviour} the code looks up by id.
	 */
	public record Effect(Optional<Identifier> attribute, String operation, double scale, String when, Optional<String> behaviour) {
		public static final Codec<Effect> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.optionalFieldOf("attribute").forGetter(Effect::attribute),
			Codec.STRING.optionalFieldOf("operation", "add_value").forGetter(Effect::operation),
			Codec.DOUBLE.optionalFieldOf("scale", 1.0).forGetter(Effect::scale),
			Codec.STRING.optionalFieldOf("when", "always").forGetter(Effect::when),
			Codec.STRING.optionalFieldOf("behaviour").forGetter(Effect::behaviour)
		).apply(i, Effect::new));

		public static Effect behaviour(String id) {
			return new Effect(Optional.empty(), "add_value", 1.0, "always", Optional.of(id));
		}
	}

	public static final Codec<Trait> CODEC = RecordCodecBuilder.create(i -> i.group(
		ResourceKey.codec(MettleRegistries.SKILL).fieldOf("skill").forGetter(Trait::skill),
		Kind.CODEC.fieldOf("kind").forGetter(Trait::kind),
		Scope.CODEC.optionalFieldOf("scope", Scope.NARROW).forGetter(Trait::scope),
		Codec.DOUBLE.fieldOf("min").forGetter(Trait::min),
		Codec.DOUBLE.fieldOf("max").forGetter(Trait::max),
		Codec.DOUBLE.optionalFieldOf("step", 1.0).forGetter(Trait::step),
		Codec.STRING.optionalFieldOf("unit", "percent").forGetter(Trait::unit),
		Effect.CODEC.fieldOf("effect").forGetter(Trait::effect),
		ComponentSerialization.CODEC.fieldOf("name").forGetter(Trait::name),
		ComponentSerialization.CODEC.fieldOf("description").forGetter(Trait::description),
		ComponentSerialization.CODEC.optionalFieldOf("arsenal").forGetter(Trait::arsenal),
		Codec.STRING.optionalFieldOf("requires").forGetter(Trait::requires),
		Codec.BOOL.optionalFieldOf("boosted", true).forGetter(Trait::boosted)
	).apply(i, Trait::new));

	public boolean percent() {
		return "percent".equals(this.unit);
	}

	/** A value as the card prints it: {@code "9 %"}, {@code "1.5"}. */
	public String format(double value) {
		String n = value == Math.rint(value) ? String.valueOf((long) Math.rint(value)) : String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
		return percent() ? n + " %" : n;
	}
}
