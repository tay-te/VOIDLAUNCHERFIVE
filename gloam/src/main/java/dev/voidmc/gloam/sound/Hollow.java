package dev.voidmc.gloam.sound;

import dev.voidmc.gloam.air.Noise;

/**
 * The underground around the camera, as the soundscape hears it: gathered by {@link CaveSurvey} from a ring of
 * {@link CaveProbe}s every 24 ticks and eased between surveys by {@link Caves}, the way {@link Surroundings} is for the
 * land above. The fractions are 0..1; the direction fields are where a sound's source lies, as an offset from the
 * camera in blocks (up to 8 long, shorter when it is all around).
 *
 * <p>The bridge reads the same values for the pack: {@link #undergroundUniform()} is {@code void_underground} and
 * {@link #caveUniform()} is {@code void_cave} (design section 2.2), so the sound and the fog never disagree.
 *
 * @param under     how far the camera is into the underground: 0 under the sky (or at a cave mouth that sees it), 1
 *                  well inside
 * @param cover     the ground over the camera, in blocks (the surface heightmap minus the eye)
 * @param space     the open space at the eye, in blocks (0 in rock, 2 to 4 in a passage, tens in a great cavern)
 * @param vault     the height from floor to roof at the eye, in blocks
 * @param openness  how open the space around is (the mean space of the ring), 0 a tight passage .. 1 a great cavern
 * @param river     how near a cavern river is (its water within about 48 blocks, level with the eye)
 * @param great     how far that river is one of the great rivers
 * @param sea       how near a buried sea is (within about 72 blocks)
 * @param lake      how near a realm's lake is (within about 40 blocks)
 * @param lava      how near lava is (within about 24 blocks)
 * @param glowworms how much of the space around has glowworm silk on its roof
 * @param bats      how much of it has bats roosting (or real bats about)
 * @param drip      how much of it drips
 * @param mine      how much of it is one of the great mines
 * @param realm     the realm the eye is in, a {@link CaveKinds} realm id
 * @param water     the kind of the nearest cavern water, a {@link CaveKinds} water id
 */
public record Hollow(float under, float cover, float space, float vault, float openness, float river, float great, float sea, float lake, float lava,
	float glowworms, float bats, float drip, float mine, float riverX, float riverZ, float seaX, float seaZ, float batsX, float batsZ, int realm,
	int water) {
	/** Not underground at all: what the soundscape assumes under the sky and before its first survey. */
	public static final Hollow NONE = new Hollow(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, CaveKinds.REALM_NONE,
		CaveKinds.WATER_NONE);

	/** This hollow eased a fraction {@code t} of the way toward {@code to}; the realm and water ids switch at once. */
	public Hollow toward(Hollow to, float t) {
		return new Hollow(lerp(this.under, to.under, t), lerp(this.cover, to.cover, t), lerp(this.space, to.space, t), lerp(this.vault, to.vault, t),
			lerp(this.openness, to.openness, t), lerp(this.river, to.river, t), lerp(this.great, to.great, t), lerp(this.sea, to.sea, t),
			lerp(this.lake, to.lake, t), lerp(this.lava, to.lava, t), lerp(this.glowworms, to.glowworms, t), lerp(this.bats, to.bats, t),
			lerp(this.drip, to.drip, t), lerp(this.mine, to.mine, t), lerp(this.riverX, to.riverX, t), lerp(this.riverZ, to.riverZ, t),
			lerp(this.seaX, to.seaX, t), lerp(this.seaZ, to.seaZ, t), lerp(this.batsX, to.batsX, t), lerp(this.batsZ, to.batsZ, t), to.realm,
			to.water);
	}

	/** How deep under the land the camera is, for the pack's fog: 0 at a cave mouth .. 1 about 48 blocks down. */
	public float depthFactor() {
		return this.under * Noise.smoothstep(4, 48, this.cover);
	}

	/** How near a great river is: {@code void_cave.w}. */
	public float greatNearness() {
		return this.river * this.great;
	}

	/** {@code void_underground}: depth factor, openness at the eye, realm id, water kind id. */
	public float[] undergroundUniform() {
		return new float[]{this.depthFactor(), this.under * this.openness, this.realm, this.water};
	}

	/** {@code void_cave}: glowworms, bats, drip, great-river nearness, each 0..1 and already faded by {@link #under}. */
	public float[] caveUniform() {
		return new float[]{this.under * this.glowworms, this.under * this.bats, this.under * this.drip, this.under * this.greatNearness()};
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}
}
