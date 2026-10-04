package dev.voidmc.hearth.law.folk;

import com.mojang.serialization.Codec;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.law.justice.SpeechCheck;
import dev.voidmc.hearth.law.stealth.Detection;
import java.util.List;
import java.util.Set;

/**
 * A resident's character as the law reads it. Traits are the npc module's ({@code Resident.extra("npc").traits}, a list
 * of names such as {@code brave}, {@code greedy}); this reads the names without depending on the npc module's classes,
 * and treats a resident with none as ordinary.
 */
public final class FolkTraits {
	private static final Codec<List<String>> NAMES = Codec.STRING.listOf();

	private final Set<String> traits;
	private final JobType job;
	private final AgeStage age;

	private FolkTraits(Set<String> traits, JobType job, AgeStage age) {
		this.traits = traits;
		this.job = job;
		this.age = age;
	}

	public static FolkTraits of(Resident resident) {
		List<String> names = resident.extra.getCompoundOrEmpty("npc").read("traits", NAMES).orElse(List.of());
		return new FolkTraits(Set.copyOf(names), resident.job, resident.age);
	}

	public boolean has(String trait) {
		return this.traits.contains(trait);
	}

	public boolean brave() {
		return has("brave") || isGuard();
	}

	public boolean cautious() {
		return has("cautious");
	}

	public boolean isGuard() {
		return this.job == JobType.GUARD || this.job == JobType.JAILER;
	}

	public boolean child() {
		return this.age == AgeStage.CHILD;
	}

	/** How sharp-eyed they are (guards and the curious more, dreamers and the lazy less). */
	public Detection.Observer observer() {
		double a = 1.0;
		if (isGuard()) {
			a *= 1.25;
		}
		if (has("curious")) {
			a *= 1.15;
		}
		if (has("cautious")) {
			a *= 1.1;
		}
		if (has("dreamy")) {
			a *= 0.8;
		}
		if (has("lazy")) {
			a *= 0.9;
		}
		if (child()) {
			a *= 0.9;
		}
		return new Detection.Observer(a);
	}

	public SpeechCheck.Listener listener() {
		return new SpeechCheck.Listener(has("brave"), has("cautious"), has("greedy"), has("generous"), has("pious"), isGuard());
	}

	/** How a witness takes it: the brave confront, the cautious and the young run for help, the rest raise the cry and go. */
	public Reaction reaction() {
		if (isGuard()) {
			return Reaction.ARREST;
		}
		if (child() || has("cautious") || has("shy")) {
			return Reaction.FLEE;
		}
		if (has("brave")) {
			return Reaction.CONFRONT;
		}
		return Reaction.SHOUT;
	}

	public enum Reaction {
		/** A guard: the offender is wanted at once. */
		ARREST,
		/** Walks up and demands it back, then goes to the guard. */
		CONFRONT,
		/** Cries out and goes to fetch the guard. */
		SHOUT,
		/** Runs to the guard (or home) as fast as they can. */
		FLEE
	}
}
