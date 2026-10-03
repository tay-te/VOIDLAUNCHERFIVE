package dev.voidmc.hearth.npc.person;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.ResidentFactory;
import dev.voidmc.hearth.api.Settlement;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * The NPC module's {@link ResidentFactory}: culture-specific names from {@link NameBank}, a looks genome
 * ({@link Genome}), two or three traits, and family links. Deterministic for a given random, so a settlement populated
 * from a seed gets the same people every time.
 *
 * <p>Children take a parent's family name and inherit their colouring; the parents are recorded as each other's
 * spouse (if neither has one) and the child is added to both their children.
 */
public final class HearthResidentFactory implements ResidentFactory {
	/** Sim days per year of a person's life, for birth days: a child is 0–15, an adult 16–59, an elder 60–85. */
	public static final int DAYS_PER_YEAR = 8;

	private final LongSupplier today;

	/** @param today the current sim day (whole days since the world began) */
	public HearthResidentFactory(LongSupplier today) {
		this.today = today;
	}

	@Override
	public Resident create(Settlement settlement, AgeStage age, RandomSource random, @Nullable Resident parentA, @Nullable Resident parentB) {
		Culture culture = settlement.culture();
		NameBank.Names names = NameBank.of(culture);
		boolean female = random.nextBoolean();
		String given = names.given(female, random);
		String family;
		int appearance;
		if (parentA != null || parentB != null) {
			Resident a = parentA != null ? parentA : parentB;
			Resident b = parentB != null ? parentB : parentA;
			// The family name usually comes down from the first parent; some families take the other.
			Resident namer = random.nextInt(5) == 0 && !b.familyName.isEmpty() ? b : a;
			family = namer.familyName.isEmpty() ? names.family(random) : namer.familyName;
			appearance = Genome.inherit(a.appearance, b.appearance, culture, random);
		} else {
			family = names.family(random);
			appearance = Genome.random(culture, random);
		}
		// No two living siblings share a given name.
		if (parentA != null) {
			for (int tries = 0; tries < 6 && siblingHasName(settlement, parentA, given); tries++) {
				given = names.given(female, random);
			}
		}

		long bornDay = this.today.getAsLong() - (long) yearsOld(age, random) * DAYS_PER_YEAR - random.nextInt(DAYS_PER_YEAR);
		UUID id = new UUID(random.nextLong(), random.nextLong());
		Resident resident = new Resident(id, given, family, female, age, bornDay, JobType.NONE, Resident.NO_PLOT, Resident.NO_PLOT,
			appearance, new CompoundTag());

		NpcData data = NpcData.of(resident);
		data.setTraits(rollTraits(random));
		data.addMood(random.nextInt(21) - 5);
		if (parentA != null || parentB != null) {
			List<UUID> parents = new ArrayList<>(2);
			for (Resident p : new Resident[]{parentA, parentB}) {
				if (p != null && !parents.contains(p.id)) {
					parents.add(p.id);
					NpcData.of(p).addChild(id);
				}
			}
			data.setParents(parents);
			if (parentA != null && parentB != null && parentA != parentB) {
				NpcData da = NpcData.of(parentA);
				NpcData db = NpcData.of(parentB);
				if (da.spouse().isEmpty() && db.spouse().isEmpty()) {
					da.setSpouse(parentB.id);
					db.setSpouse(parentA.id);
				}
			}
		}
		return resident;
	}

	/** Two traits, sometimes three, never both sides of a pair. */
	public static Set<Trait> rollTraits(RandomSource random) {
		Set<Trait> traits = EnumSet.noneOf(Trait.class);
		int count = random.nextInt(4) == 0 ? 3 : 2;
		for (int tries = 0; traits.size() < count && tries < 20; tries++) {
			Trait t = Trait.VALUES[random.nextInt(Trait.VALUES.length)];
			if (!traits.contains(t) && (t.opposite() == null || !traits.contains(t.opposite()))) {
				traits.add(t);
			}
		}
		return traits;
	}

	private static int yearsOld(AgeStage age, RandomSource random) {
		return switch (age) {
			case CHILD -> random.nextInt(14) + 1;
			case ADULT -> 17 + random.nextInt(42);
			case ELDER -> 61 + random.nextInt(24);
		};
	}

	private static boolean siblingHasName(Settlement settlement, Resident parent, String given) {
		for (UUID child : NpcData.of(parent).children()) {
			Resident sibling = settlement.resident(child);
			if (sibling != null && sibling.givenName.equals(given)) {
				return true;
			}
		}
		return false;
	}
}
