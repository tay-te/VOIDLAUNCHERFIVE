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
 */
public record Keyword(Component name, Component theme, int colour, Identifier icon, Map<Integer, SetBonus> sets) {
	/**
	 * One tier of a set: what it says, its attribute modifiers (applied as {@code mettle:set/<keyword>/<tier>/<attr>}),
	 * and a behaviour id for what code does.
	 */
	public record SetBonus(Component description, List<Modifier> effects, Optional<String> behaviour) {
		public static final Codec<SetBonus> CODEC = RecordCodecBuilder.create(i -> i.group(
			ComponentSerialization.CODEC.fieldOf("description").forGetter(SetBonus::description),
			Modifier.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(SetBonus::effects),
			Codec.STRING.optionalFieldOf("behaviour").forGetter(SetBonus::behaviour)
		).apply(i, SetBonus::new));
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
		Codec.unboundedMap(TIER, SetBonus.CODEC).fieldOf("sets").forGetter(Keyword::sets)
	).apply(i, Keyword::new));
}
