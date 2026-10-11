package dev.voidmc.gloam.sound;

/**
 * One point of the underground as the soundscape asks about it: Expanse's {@code ExpanseUnderground.Here} in Gloam's
 * terms (ids from {@link CaveKinds} instead of strings), or the same read from the blocks where Expanse has nothing to
 * say (a dedicated server's client, another world generator).
 *
 * @param underground   whether the point lies under the land (more than three blocks down)
 * @param space         how far (blocks) the point is inside open space: 0 in the rock, 2 to 4 in a passage, tens in a
 *                      realm or over a great river
 * @param floor         the floor under the point (y)
 * @param ceiling       the roof over it (y)
 * @param water         the nearest cavern water's kind, a {@link CaveKinds} water id
 * @param waterSurface  its surface (y), {@link Integer#MIN_VALUE} for none
 * @param waterDistance blocks across to it (0 over it, 1000 for none)
 * @param realm         the realm the point is in, a {@link CaveKinds} realm id
 * @param greatRiver    0..1, how far the river here is one of the great rivers
 * @param glowworms     glowworm silk hangs from the roof here
 * @param bats          bats roost here
 * @param drip          water drips from the roof here
 * @param mine          the point is in or by one of the great mines
 */
public record CaveProbe(boolean underground, float space, int floor, int ceiling, int water, int waterSurface, float waterDistance, int realm,
	float greatRiver, boolean glowworms, boolean bats, boolean drip, boolean mine) {
	/** Solid rock under the land: no space, no water, nothing living. */
	public static CaveProbe rock(int y) {
		return new CaveProbe(true, 0, y, y, CaveKinds.WATER_NONE, Integer.MIN_VALUE, 1000, CaveKinds.REALM_NONE, 0, false, false, false, false);
	}

	/** Whether there is cavern water (not lava) to hear. */
	public boolean wet() {
		return this.water != CaveKinds.WATER_NONE && this.water != CaveKinds.LAVA;
	}
}
