package dev.voidmc.atlas.core;

/**
 * What a world's own generator says lies under a column (VOID Expanse's caverns: a buried sea, a realm's lake, an
 * underground river, a lava lake), to tint cave water the player has already revealed. It never reveals anything: only
 * pixels the map already knows are water or lava take it.
 */
public interface CavernWater {
	int NONE = 0, RIVER = 1, SEA = 2, LAKE = 3, LAVA = 4;

	/** The kind of cavern water at (x, z) whose surface is near {@code y} (within a few blocks), or {@link #NONE}. */
	int at(int x, int z, int y);
}
