package dev.voidmc.mettle.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.StringRepresentable;

/**
 * A talent (docs/BUILD_FEEL.md §13): a general card with no keyword and no skill, a modest gift (more carry, a heart,
 * longer breath, a little luck). Data, synced, loaded from {@code data/<ns>/mettle/talent/<id>.json}. A talent card is a
 * card in the world ledger (skill {@code mettle:talent}, perk key {@code mettle:talent/<id>}) holding one roll of its
 * value; slotted, its value is active as the trait {@code mettle:talent/<id>}.
 *
 * @param family      Body, Motion, Hands, Mind or Fortune (the glyph where a skill card shows its skill)
 * @param min         the lowest value it rolls, in its unit
 * @param max         the highest
 * @param step        values round to this
 * @param unit        {@code percent} ("9 %") or {@code flat} (the description says what)
 * @param fixed       a talent with no range and one rarity ({@code rare}: Light Step, Light Sleeper; {@code epic}: Packrat)
 * @param ceiling     the CARDS.md §14 stat it counts toward ({@code movement}, {@code taken}, {@code extra_drop}), if any
 * @param effect      an attribute modifier (value × scale) or a behaviour the code handles, as a trait's
 * @param name        "Fleet"
 * @param description one plain sentence with {@code %s} for the value
 */
public record Talent(
	Family family,
	double min,
	double max,
	double step,
	String unit,
	Optional<String> fixed,
	Optional<String> ceiling,
	Trait.Effect effect,
	Component name,
	Component description
) {
	public enum Family implements StringRepresentable {
		BODY, MOTION, HANDS, MIND, FORTUNE;

		public static final StringRepresentable.EnumCodec<Family> CODEC = StringRepresentable.fromEnum(Family::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public static final Codec<Talent> CODEC = RecordCodecBuilder.create(i -> i.group(
		Family.CODEC.fieldOf("family").forGetter(Talent::family),
		Codec.DOUBLE.optionalFieldOf("min", 1.0).forGetter(Talent::min),
		Codec.DOUBLE.optionalFieldOf("max", 1.0).forGetter(Talent::max),
		Codec.DOUBLE.optionalFieldOf("step", 1.0).forGetter(Talent::step),
		Codec.STRING.optionalFieldOf("unit", "percent").forGetter(Talent::unit),
		Codec.STRING.optionalFieldOf("fixed").forGetter(Talent::fixed),
		Codec.STRING.optionalFieldOf("ceiling").forGetter(Talent::ceiling),
		Trait.Effect.CODEC.fieldOf("effect").forGetter(Talent::effect),
		ComponentSerialization.CODEC.fieldOf("name").forGetter(Talent::name),
		ComponentSerialization.CODEC.fieldOf("description").forGetter(Talent::description)
	).apply(i, Talent::new));

	public boolean percent() {
		return "percent".equals(this.unit);
	}

	/** Whether it has no range (Light Step, Light Sleeper, Packrat): its value is always 1. */
	public boolean isFixed() {
		return this.fixed.isPresent();
	}

	/** A value as the card prints it: {@code "9 %"}, {@code "1.5"}. */
	public String format(double value) {
		String n = value == Math.rint(value) ? String.valueOf((long) Math.rint(value))
			: String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
		return percent() ? n + " %" : n;
	}

	/** The card's line: the description with its value ({@code "+3 % movement speed."}), or as written for a fixed one. */
	public Component line(double value) {
		if (!isFixed() && this.description.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t) {
			String v = format(value);
			return Component.translatable(t.getKey(), v, v, v);
		}
		return this.description;
	}
}
