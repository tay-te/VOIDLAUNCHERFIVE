package dev.voidmc.hearth.api;

import java.util.UUID;
import net.minecraft.core.BlockPos;

/** Crowds that react (docs/LIVING_WORLD_2.md §5): {@link HearthServices#crowds()}; the npc module's crowds (lane A3). */
public interface CrowdService {
	/** Two residents come to blows ({@code why}: a feud, drink, an insult); false if it cannot happen now. */
	boolean brawl(Settlement s, UUID a, UUID b, String why);

	/** Something worth watching at {@code at} for {@code seconds}: up to {@code maxOnlookers} folk gather and gawk. */
	void spectacle(Settlement s, BlockPos at, String kind, int seconds, int maxOnlookers);

	/** Whether the town has shut its doors and shutters (a raid, a monster, an outlaw about). */
	boolean shuttered(Settlement s);

	/**
	 * Whether a player's blow on a resident is brawling rather than assault: bare fists, in a brawl that resident is in
	 * (docs/LIVING_WORLD_2.md §5.1). The law's assault watch asks before it files a crime.
	 */
	default boolean brawling(java.util.UUID player, java.util.UUID resident) {
		return false;
	}
}
