package dev.voidmc.hearth.npc.crowd;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import java.util.Locale;

/** Who a folk is in a crowd, for how they react and which barks fit them (§5.7). Pure. */
public enum Role {
	GUARD, MERCHANT, MOTHER, CHILD, ELDER, FOLK;

	public static final Role[] VALUES = values();

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/**
	 * The role of a resident.
	 *
	 * @param mother an adult woman with a child at home (the caller knows the town's households)
	 */
	public static Role of(Resident r, boolean mother) {
		if (r.age == AgeStage.CHILD) {
			return CHILD;
		}
		if (r.job == JobType.GUARD || r.job == JobType.JAILER || r.job == JobType.BAILIFF) {
			return GUARD;
		}
		if (r.age == AgeStage.ELDER) {
			return ELDER;
		}
		if (merchant(r.job)) {
			return MERCHANT;
		}
		return mother ? MOTHER : FOLK;
	}

	public static boolean merchant(JobType job) {
		return switch (job) {
			case MERCHANT, TRADER, PEDLAR, INNKEEPER, BAKER, BUTCHER, TAILOR, CHANDLER, BREWER, SMITH, FENCE -> true;
			default -> false;
		};
	}

	public static Role byId(String id) {
		for (Role r : VALUES) {
			if (r.id().equals(id)) {
				return r;
			}
		}
		throw new IllegalArgumentException("unknown crowd role " + id);
	}
}
