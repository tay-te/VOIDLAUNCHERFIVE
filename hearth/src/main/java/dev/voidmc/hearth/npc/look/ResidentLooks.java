package dev.voidmc.hearth.npc.look;

import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.person.Office;
import org.jspecify.annotations.Nullable;

/**
 * A resident's look as the {@link FolkLook#key()} their folk would show, worked out from the resident alone, so a screen
 * about someone whose folk is not in the client's world (a shopkeeper away from the counter, a dice partner just out of
 * view) can still carry their portrait (docs/OVERLAY.md, {@code portrait}). Server side; cheap enough per screen.
 */
public final class ResidentLooks {
	/** No look (sent as such; the client then has no portrait). */
	public static final long NONE = -1L;

	private ResidentLooks() {
	}

	public static FolkLook of(Settlement settlement, Resident resident) {
		return new FolkLook(resident.appearance, settlement.culture(), resident.job, resident.age, resident.female, resident.station,
			Office.of(settlement, resident));
	}

	/** {@link FolkLook#key()} of the resident's look, or {@link #NONE} when either is missing. */
	public static long key(@Nullable Settlement settlement, @Nullable Resident resident) {
		if (settlement == null || resident == null) {
			return NONE;
		}
		try {
			return of(settlement, resident).key();
		} catch (RuntimeException e) {
			return NONE;
		}
	}
}
