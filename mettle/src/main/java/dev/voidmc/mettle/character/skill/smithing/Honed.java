package dev.voidmc.mettle.character.skill.smithing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Honing (mastery), component {@code mettle:honed}: a fresh edge from the grindstone that wears off with use. A weapon
 * deals +2 damage for {@link SmithingRules#HONE_HITS} hits; a tool digs 30 % faster for {@link SmithingRules#HONE_BLOCKS}
 * blocks. When {@code left} reaches 0 the component goes and the item is as it was.
 *
 * @param weapon whether it is a weapon edge (counts hits) or a tool edge (counts blocks)
 * @param left   hits or blocks left
 */
public record Honed(boolean weapon, int left) {
	public static final Codec<Honed> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.BOOL.fieldOf("weapon").forGetter(Honed::weapon),
		Codec.INT.fieldOf("left").forGetter(Honed::left)
	).apply(i, Honed::new));

	public static Honed fresh(boolean weapon) {
		return new Honed(weapon, weapon ? SmithingRules.HONE_HITS : SmithingRules.HONE_BLOCKS);
	}

	public boolean full() {
		return left >= (weapon ? SmithingRules.HONE_HITS : SmithingRules.HONE_BLOCKS);
	}
}
