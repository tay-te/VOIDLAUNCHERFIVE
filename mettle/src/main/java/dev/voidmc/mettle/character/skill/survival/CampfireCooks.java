package dev.voidmc.mettle.character.skill.survival;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Added to CampfireBlockEntity (by mixin.survival; a duck interface must live outside the mixin package): who loaded each slot, and (for the client tour) a way to skip the 30 s wait. */
public interface CampfireCooks {
	@Nullable UUID[] mettle$cooks();

	void mettle$finishCooking();

	/** One tick more of cooking for a slot (Fireside, a card trait). */
	void mettle$hurry(int slot);
}
