package dev.voidmc.hearth.api;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** People with lives (docs/LIVING_WORLD_2.md §4): {@link HearthServices#lives()}; the sim module's lives (lane B2). */
public interface LivesService {
	/** The settlement's named characters. */
	List<Notable> notables(Settlement s);

	/** How {@code a} stands to {@code b} (kin, friend, rival, sweetheart...), or null if they are nothing to each other. */
	@Nullable Relation relation(Settlement s, UUID a, UUID b);

	List<Feud> feuds(Settlement s);

	/** Something happened in a resident's life ({@code kind}: wed, widowed, apprenticed, shamed...), maybe by a player. */
	void lifeEvent(Settlement s, UUID resident, String kind, @Nullable UUID player);

	/** A named character: their role, the thread of their story and where it stands. */
	record Notable(UUID resident, String role, String thread, String beat) {
	}

	record Relation(String kind, int strength) {
	}

	/** A quarrel between two households, by their heads. {@code since} is the sim day it began. */
	record Feud(UUID headA, UUID headB, String cause, long since) {
	}
}
