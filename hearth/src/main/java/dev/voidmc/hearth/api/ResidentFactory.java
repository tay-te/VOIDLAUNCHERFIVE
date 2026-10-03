package dev.voidmc.hearth.api;

import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * Makes new people: names from the culture, looks, personality. The sim module calls it when a settlement grows (births,
 * settlers arriving) and when it first populates a settlement found in world generation. The sim registers a plain
 * default; the NPC module replaces it with the real one.
 */
@FunctionalInterface
public interface ResidentFactory {
	/**
	 * @param parentA a parent (a child takes the family name and looks partly from parents), or null for a newcomer
	 * @param parentB the other parent, or null
	 */
	Resident create(Settlement settlement, AgeStage age, RandomSource random, @Nullable Resident parentA, @Nullable Resident parentB);
}
