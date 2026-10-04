package dev.voidmc.hearth.standing.speech;

import net.minecraft.util.StringRepresentable;

/**
 * The four ways to talk someone round (docs/LIVING_WORLD.md §5), each with the memory it leaves: a successful threat
 * still frightens, a refused bribe offends.
 */
public enum SpeechKind implements StringRepresentable {
	PERSUADE("persuade", 3, -2),
	INTIMIDATE("intimidate", -4, -8),
	BRIBE("bribe", 5, -5),
	FLATTER("flatter", 6, -3);

	public static final StringRepresentable.EnumCodec<SpeechKind> CODEC = StringRepresentable.fromEnum(SpeechKind::values);

	private final String name;
	/** The memory (weight) a success leaves with the resident. */
	public final int successMemory;
	/** The memory a failure leaves. */
	public final int failureMemory;

	SpeechKind(String name, int successMemory, int failureMemory) {
		this.name = name;
		this.successMemory = successMemory;
		this.failureMemory = failureMemory;
	}

	/** The deed kind its memories are filed under ({@code standing:persuade}). */
	public String memoryKind(boolean success) {
		return "standing:" + this.name + (success ? "" : "_failed");
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}

	public String translationKey() {
		return "hearth.standing.speech." + this.name;
	}
}
