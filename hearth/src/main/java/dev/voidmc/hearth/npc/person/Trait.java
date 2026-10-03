package dev.voidmc.hearth.npc.person;

import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

/**
 * A side of someone's character. Traits shift a resident's day (the industrious rise early, the sociable spend their
 * evenings at the inn), how they talk to the player, and how they react to danger. Most come in opposing pairs and a
 * person has at most one of each pair.
 */
public enum Trait implements StringRepresentable {
	INDUSTRIOUS("industrious"),
	LAZY("lazy"),
	SOCIABLE("sociable"),
	SHY("shy"),
	BRAVE("brave"),
	CAUTIOUS("cautious"),
	CHEERFUL("cheerful"),
	GRUMPY("grumpy"),
	GREEDY("greedy"),
	GENEROUS("generous"),
	PIOUS("pious"),
	CURIOUS("curious"),
	/** Loves news and passes it on: knows more rumours. */
	GOSSIP("gossip"),
	/** Never far from a book or a view: lingers at viewpoints and lecterns. */
	DREAMY("dreamy");

	public static final StringRepresentable.EnumCodec<Trait> CODEC = StringRepresentable.fromEnum(Trait::values);
	public static final Trait[] VALUES = values();

	private final String name;

	Trait(String name) {
		this.name = name;
	}

	/** The trait this one excludes, or null for traits without an opposite. */
	public @Nullable Trait opposite() {
		return switch (this) {
			case INDUSTRIOUS -> LAZY;
			case LAZY -> INDUSTRIOUS;
			case SOCIABLE -> SHY;
			case SHY -> SOCIABLE;
			case BRAVE -> CAUTIOUS;
			case CAUTIOUS -> BRAVE;
			case CHEERFUL -> GRUMPY;
			case GRUMPY -> CHEERFUL;
			case GREEDY -> GENEROUS;
			case GENEROUS -> GREEDY;
			default -> null;
		};
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}

	public String translationKey() {
		return "hearth.npc.trait." + this.name;
	}
}
