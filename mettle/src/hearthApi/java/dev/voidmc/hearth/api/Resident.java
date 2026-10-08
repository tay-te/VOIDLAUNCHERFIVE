package dev.voidmc.hearth.api;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Stub of Hearth's resident record: only the members Mettle reads (see package-info; the constructor is the tour's). */
public final class Resident {
	public static final int NO_PLOT = -1;

	public final UUID id;
	public String givenName;
	public String familyName;
	public AgeStage age;
	public JobType job;

	public Resident(UUID id, String givenName, String familyName, boolean female, AgeStage age, long bornDay, JobType job,
		int home, int work, int appearance, CompoundTag extra) {
		throw new UnsupportedOperationException("Hearth api stub");
	}

	public String fullName() {
		throw new UnsupportedOperationException("Hearth api stub");
	}
}
