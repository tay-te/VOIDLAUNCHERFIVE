package dev.voidmc.hearth.sim.settlement;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.ResidentFactory;
import dev.voidmc.hearth.api.Settlement;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * The default {@link ResidentFactory}: a name from the culture's plain list, a family name from a parent, and a looks
 * seed. The NPC module replaces it with folk who have personalities. Stateless and thread-safe (histories call it on
 * world generation threads); ids come from the given random so a history makes the same people every time.
 */
public final class PlainResidents implements ResidentFactory {
	public static final PlainResidents INSTANCE = new PlainResidents();

	@Override
	public Resident create(Settlement settlement, AgeStage age, RandomSource random, @Nullable Resident parentA, @Nullable Resident parentB) {
		boolean female = random.nextBoolean();
		String family = parentB != null ? parentB.familyName : parentA != null ? parentA.familyName : Names.family(settlement.culture(), random);
		int looks = random.nextInt();
		if (parentA != null && parentB != null) {
			// half the looks from each parent, a little of their own
			looks = (parentA.appearance & 0x00FF_FF00) ^ (parentB.appearance & 0xFF00_00FF) ^ (looks & 0x0F0F_0F0F);
		}
		return new Resident(new UUID(random.nextLong(), random.nextLong()), Names.given(settlement.culture(), female, random), family, female, age,
			0, JobType.NONE, Resident.NO_PLOT, Resident.NO_PLOT, looks, new CompoundTag());
	}
}
