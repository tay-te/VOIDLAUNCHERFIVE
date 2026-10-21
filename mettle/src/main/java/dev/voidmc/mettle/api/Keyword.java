package dev.voidmc.mettle.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;

/**
 * A card keyword and its set bonuses (docs/CARDS.md §6): two, four or six slotted cards sharing it give the bonus of that
 * tier. Data, synced, loaded from {@code data/<ns>/mettle/keyword/<id>.json}.
 *
 * @param colour the glyph colour (one neutral colour for every keyword, the user's answer of 2026-10-10)
 * @param sets   tier (2, 4, 6) → its bonus
 * @param momentum how much the deal leans toward this keyword for a player committed to it (docs/BUILD_FEEL.md §1.2):
 *               the scarce keywords most (Tide, Deep 1), the common ones not at all (0)
 */
public record Keyword(Component name, Component theme, int colour, Identifier icon, Map<Integer, SetBonus> sets, double momentum) {
	/**
	 * One tier of a set: what it says, its attribute modifiers (applied as {@code mettle:set/<keyword>/<tier>/<attr>}),
	 * and a behaviour id for what code does.
	 */
	public record SetBonus(Component description, List<Modifier> effects, Optional<String> behaviour, Optional<Component> summary) {
		public static final Codec<SetBonus> CODEC = RecordCodecBuilder.create(i -> i.group(
			ComponentSerialization.CODEC.fieldOf("description").forGetter(SetBonus::description),
			Modifier.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(SetBonus::effects),
			Codec.STRING.optionalFieldOf("behaviour").forGetter(SetBonus::behaviour),
			ComponentSerialization.CODEC.optionalFieldOf("summary").forGetter(SetBonus::summary)
		).apply(i, SetBonus::new));

		/** A tier without a plain-words summary line ("Your build" then uses its description). */
		public SetBonus(Component description, List<Modifier> effects, Optional<String> behaviour) {
			this(description, effects, behaviour, Optional.empty());
		}
	}

	/** One attribute modifier of a set bonus, optionally only while {@code when} holds (as trait effects). */
	public record Modifier(Identifier attribute, double amount, String operation, String when) {
		public static final Codec<Modifier> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("attribute").forGetter(Modifier::attribute),
			Codec.DOUBLE.fieldOf("amount").forGetter(Modifier::amount),
			Codec.STRING.optionalFieldOf("operation", "add_value").forGetter(Modifier::operation),
			Codec.STRING.optionalFieldOf("when", "always").forGetter(Modifier::when)
		).apply(i, Modifier::new));
	}

	private static final Codec<Integer> TIER = Codec.STRING.xmap(Integer::parseInt, String::valueOf);

	public static final Codec<Keyword> CODEC = RecordCodecBuilder.create(i -> i.group(
		ComponentSerialization.CODEC.fieldOf("name").forGetter(Keyword::name),
		ComponentSerialization.CODEC.optionalFieldOf("theme", Component.empty()).forGetter(Keyword::theme),
		Skill.HEX.optionalFieldOf("colour", 0xC9CCD1).forGetter(Keyword::colour),
		Identifier.CODEC.fieldOf("icon").forGetter(Keyword::icon),
		Codec.unboundedMap(TIER, SetBonus.CODEC).fieldOf("sets").forGetter(Keyword::sets),
		Codec.doubleRange(0, 10).optionalFieldOf("momentum", 0.0).forGetter(Keyword::momentum)
	).apply(i, Keyword::new));

	/** A keyword that never leans the deal. */
	public Keyword(Component name, Component theme, int colour, Identifier icon, Map<Integer, SetBonus> sets) {
		this(name, theme, colour, icon, sets, 0);
	}
}
