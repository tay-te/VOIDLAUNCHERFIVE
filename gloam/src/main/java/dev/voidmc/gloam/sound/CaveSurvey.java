package dev.voidmc.gloam.sound;

import dev.voidmc.gloam.air.Noise;

/**
 * Gathers the underground around the camera from {@link CaveProbe}s, one probe a tick so no tick pays for more than
 * one: the eye itself, and a ring of sixteen points level with it (eight 12 blocks out, eight 28 blocks out between
 * them). A survey takes {@link #CYCLE} ticks, the eye asked every third tick, and then {@link #result} turns what it
 * found into a {@link Hollow}. Pure: no world, so it is unit-tested ({@code CaveSurveyTest}).
 */
public final class CaveSurvey {
	/** The slot of the eye in {@link #next()}. */
	public static final int EYE = -1;
	static final int RING = 16;
	/** Ticks per survey: the sixteen ring points, with the eye before every two of them. */
	public static final int CYCLE = RING / 2 * 3;
	private static final float[] DX = new float[RING], DZ = new float[RING];
	private static final int[] PLAN = new int[CYCLE];

	/** How far each kind of water is heard across the caverns, in blocks. */
	static final float RIVER_REACH = 48, SEA_REACH = 72, LAKE_REACH = 40, LAVA_REACH = 24;
	/** Weight of the eye among the probes (the ring's are 1 each). */
	private static final float EYE_WEIGHT = 3;

	static {
		for (int i = 0; i < RING; i++) {
			boolean outer = i >= RING / 2;
			double angle = (i % (RING / 2)) * Math.PI / 4 + (outer ? Math.PI / 8 : 0);
			float r = outer ? 28 : 12;
			DX[i] = (float) (Math.cos(angle) * r);
			DZ[i] = (float) (Math.sin(angle) * r);
		}
		// interleave the near and the far ring so a part-done survey is still spread all round
		int[] order = {0, 8, 4, 12, 2, 10, 6, 14, 1, 9, 5, 13, 3, 11, 7, 15};
		for (int k = 0, j = 0; k < CYCLE; k++) {
			PLAN[k] = k % 3 == 0 ? EYE : order[j++];
		}
	}

	private final CaveProbe[] ring = new CaveProbe[RING];
	private CaveProbe eye;
	private int step;

	/** The slot the next probe should be taken at: {@link #EYE}, or a ring index for {@link #offsetX}/{@link #offsetZ}. */
	public int next() {
		return PLAN[this.step];
	}

	/** The x offset (blocks) from the eye of a slot. */
	public static float offsetX(int slot) {
		return slot == EYE ? 0 : DX[slot];
	}

	/** The z offset (blocks) from the eye of a slot. */
	public static float offsetZ(int slot) {
		return slot == EYE ? 0 : DZ[slot];
	}

	/**
	 * Files the probe for the slot {@link #next()} named ({@code null} if it could not be asked, such as an unloaded
	 * chunk) and moves on. Returns true when that finished a survey, so {@link #result} has a whole ring to read.
	 */
	public boolean put(CaveProbe probe) {
		int slot = PLAN[this.step];
		if (slot == EYE) {
			this.eye = probe;
		} else {
			this.ring[slot] = probe;
		}
		this.step = (this.step + 1) % CYCLE;
		return this.step == 0;
	}

	/** Forgets every probe (a new world, or back under the sky). */
	public void clear() {
		this.eye = null;
		java.util.Arrays.fill(this.ring, null);
		this.step = 0;
	}

	/**
	 * What the probes say together.
	 *
	 * @param eyeY     the camera's height, to tell water level with it from water far below
	 * @param cover    the ground over the camera (the surface heightmap minus the eye), blocks
	 * @param skyLight the sky light at the eye, 0..15: a cave mouth that still sees the sky is not underground yet
	 * @param batsSeen real bats flying near the camera, 0..1 (they count as a roost)
	 */
	public Hollow result(double eyeY, float cover, int skyLight, float batsSeen) {
		return gather(this.eye, this.ring, eyeY, cover, skyLight, batsSeen);
	}

	static Hollow gather(CaveProbe eye, CaveProbe[] ring, double eyeY, float cover, int skyLight, float batsSeen) {
		boolean underground = eye != null ? eye.underground() : cover > 3;
		float under = (underground ? 1 : 0) * Noise.smoothstep(2, 10, cover) * (1 - Noise.smoothstep(6, 14, skyLight));
		if (under <= 0) {
			return Hollow.NONE;
		}
		float weights = 0, spaceSum = 0, glow = 0, bats = 0, drip = 0, mine = 0;
		float river = 0, riverGreat = 0, riverWeight = 0, sea = 0, lake = 0, lava = 0;
		float rx = 0, rz = 0, rw = 0, sx = 0, sz = 0, sw = 0, bx = 0, bz = 0, bw = 0;
		int nearestKind = CaveKinds.WATER_NONE;
		float nearest = 0;
		for (int i = -1; i < RING; i++) {
			CaveProbe p = i < 0 ? eye : ring[i];
			if (p == null) {
				continue;
			}
			float w = i < 0 ? EYE_WEIGHT : 1;
			float ox = i < 0 ? 0 : DX[i], oz = i < 0 ? 0 : DZ[i];
			float reach = (float) Math.sqrt(ox * ox + oz * oz);
			// for pointing: the eye's own reading has no direction, so it counts as if it were all round at the ring
			float span = Math.max(reach, 12);
			weights += w;
			spaceSum += w * Math.min(p.space(), 32);
			glow += p.glowworms() ? w : 0;
			drip += p.drip() ? w : 0;
			mine += p.mine() ? w : 0;
			if (p.bats()) {
				bats += w;
				bx += ox;
				bz += oz;
				bw += span;
			}
			if (p.water() != CaveKinds.WATER_NONE) {
				// across: the probe's own distance, and a little for how far out the probe is; up and down: water far
				// below or above the eye (another level of the caverns) is hardly heard through the rock
				float across = p.waterDistance() + 0.5F * reach;
				float level = p.waterSurface() == Integer.MIN_VALUE ? 0 : (float) Math.abs(eyeY - p.waterSurface());
				float height = 1 - Noise.smoothstep(8, 48, level);
				float near = switch (p.water()) {
					case CaveKinds.RIVER -> 1 - Noise.smoothstep(0, RIVER_REACH, across);
					case CaveKinds.SEA -> 1 - Noise.smoothstep(0, SEA_REACH, across);
					case CaveKinds.LAKE -> 1 - Noise.smoothstep(0, LAKE_REACH, across);
					default -> 1 - Noise.smoothstep(0, LAVA_REACH, across);
				} * height;
				if (near <= 0) {
					continue;
				}
				switch (p.water()) {
					case CaveKinds.RIVER -> {
						river = Math.max(river, near);
						riverGreat += near * p.greatRiver();
						riverWeight += near;
						rx += ox * near;
						rz += oz * near;
						rw += near * span;
					}
					case CaveKinds.SEA, CaveKinds.LAKE -> {
						if (p.water() == CaveKinds.SEA) {
							sea = Math.max(sea, near);
						} else {
							lake = Math.max(lake, near);
						}
						sx += ox * near;
						sz += oz * near;
						sw += near * span;
					}
					default -> lava = Math.max(lava, near);
				}
				if (near > nearest) {
					nearest = near;
					nearestKind = p.water();
				}
			}
		}
		if (weights == 0) {
			return new Hollow(under, cover, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, CaveKinds.REALM_NONE, CaveKinds.WATER_NONE);
		}
		float space = eye == null ? 0 : eye.space();
		float vault = eye == null || eye.space() <= 0 ? 0 : Math.max(0, eye.ceiling() - eye.floor());
		float openness = Noise.smoothstep(1.5F, 14, spaceSum / weights);
		float batShare = Math.max(bats / weights, Math.clamp(batsSeen, 0, 1));
		return new Hollow(under, cover, space, vault, openness, river, riverWeight > 0 ? riverGreat / riverWeight : 0, sea, lake, lava,
			glow / weights, batShare, drip / weights, mine / weights, toward(rx, rw), toward(rz, rw), toward(sx, sw), toward(sz, sw),
			toward(bx, bw), toward(bz, bw), eye == null ? CaveKinds.REALM_NONE : eye.realm(), nearestKind);
	}

	/**
	 * A source's offset from the weighted sum of the probes' offsets over the weighted sum of their lengths: 8 blocks
	 * long when it all lies one way, shorter when it is spread round (the way {@code Land} points the brook).
	 */
	private static float toward(float sum, float lengths) {
		return lengths > 0 ? Math.clamp(8 * sum / lengths, -8, 8) : 0;
	}
}
