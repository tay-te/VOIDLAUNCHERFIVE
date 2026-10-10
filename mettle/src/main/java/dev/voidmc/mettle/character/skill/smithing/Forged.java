package dev.voidmc.mettle.character.skill.smithing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * The smith's stamp on a crafted (or reforged) item, component {@code mettle:forged}: who made it and which of their
 * Smithing perks went into it. The stamp is the truth; durability, attribute modifiers, the Masterwork name and a
 * shield's block function are derived from it by {@link Forging#refresh}, so they follow the item through a netherite
 * upgrade and show in every tooltip. Weight (Light Hand, Light) is read from it by the burden module's modifier list.
 *
 * @param smith   the maker's name, as it was when the item was made
 * @param marks   the perks folded into it, in the order they were applied
 * @param reforge a Reforge at a smithing table ({@link Reforge#NONE} when never reforged)
 */
public record Forged(String smith, List<Mark> marks, Reforge reforge, java.util.Map<String, Double> traits) {
	public static final Codec<Forged> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.optionalFieldOf("smith", "").forGetter(Forged::smith),
		Mark.CODEC.listOf().optionalFieldOf("marks", List.of()).forGetter(Forged::marks),
		Reforge.CODEC.optionalFieldOf("reforge", Reforge.NONE).forGetter(Forged::reforge),
		Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("traits", java.util.Map.of()).forGetter(Forged::traits)
	).apply(i, Forged::new));

	public Forged {
		marks = List.copyOf(marks);
		traits = java.util.Map.copyOf(traits);
	}

	public Forged(String smith, List<Mark> marks, Reforge reforge) {
		this(smith, marks, reforge, java.util.Map.of());
	}

	/**
	 * A Smithing card trait folded in when it was made (docs/CARDS.md 4.2): tempered (durability), edged (damage),
	 * lightened (weight), quenched (dig speed) as fractions; proofing as toughness.
	 */
	public double trait(String name) {
		return traits.getOrDefault(name, 0.0);
	}

	public boolean has(Mark mark) {
		return marks.contains(mark);
	}

	public Forged withReforge(Reforge next) {
		return new Forged(smith, marks, next, traits);
	}

	public Forged withMark(Mark mark) {
		if (has(mark)) {
			return this;
		}
		List<Mark> next = new ArrayList<>(marks);
		next.add(mark);
		return new Forged(smith, next, reforge, traits);
	}

	/** A Smithing perk folded into a crafted item. */
	public enum Mark implements StringRepresentable {
		/** Light Hand: armour weighs 25 % less. */
		LIGHT_HAND,
		/** Riveted: armour and shields last 30 % longer. */
		RIVETED,
		/** Proofed: +1 armour. */
		PROOFED,
		/** Shieldwright: raises instantly, wears half as fast. */
		SHIELDWRIGHT,
		/** Forged: tools and weapons last 25 % longer and carry the maker's name. */
		FORGED,
		/** Keen Edge: +1 damage. */
		KEEN_EDGE,
		/** True Temper: digs 15 % faster. */
		TRUE_TEMPER,
		/** Folded Steel: iron gear lasts twice as long. */
		FOLDED_STEEL,
		/** Masterwork: +1 to the main stat, a third more durability, a gold name. */
		MASTERWORK,
		/** Brittle Edge (cursed): +2 damage, half the durability. */
		BRITTLE_EDGE,
		/** Slapdash (cursed): a quarter less durability. */
		SLAPDASH;

		public static final Codec<Mark> CODEC = StringRepresentable.fromEnum(Mark::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** Reforge (mastery): one per armour piece; reforging again replaces it. */
	public enum Reforge implements StringRepresentable {
		NONE,
		/** A feather: half weight. */
		LIGHT,
		/** An ingot: +50 % durability. */
		STURDY,
		/** An amethyst shard: +1 armour toughness. */
		WARDED;

		public static final Codec<Reforge> CODEC = StringRepresentable.fromEnum(Reforge::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}
}
