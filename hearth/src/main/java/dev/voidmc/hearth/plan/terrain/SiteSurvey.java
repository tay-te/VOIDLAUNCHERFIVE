package dev.voidmc.hearth.plan.terrain;

import dev.voidmc.hearth.plan.model.Site;

/**
 * Reads the land round a settlement's heart once, at founding, from coarse samples (a few thousand columns, not the
 * whole square): the lie of the ground, its relief, what water is near and which way it runs, the rise a chapel would
 * stand on, the summit, cliffs and headlands, and how much of it can be built on.
 */
public final class SiteSurvey {
	private static final int[] DX8 = {1, 1, 0, -1, -1, -1, 0, 1};
	private static final int[] DZ8 = {0, 1, 1, 1, 0, -1, -1, -1};

	private SiteSurvey() {
	}

	public static Site survey(Ground g, int cx, int cz) {
		int sea = g.seaLevel();
		// the heart stands on dry land: move it ashore if the centre is in the water
		int hx = cx;
		int hz = cz;
		if (g.isWater(cx, cz)) {
			int best = Integer.MAX_VALUE;
			for (int r = 2; r <= 48 && best == Integer.MAX_VALUE; r += 2) {
				for (int a = 0; a < 32; a++) {
					double ang = a * Math.PI / 16;
					int x = cx + (int) Math.round(r * StrictMath.cos(ang));
					int z = cz + (int) Math.round(r * StrictMath.sin(ang));
					if (!g.isWater(x, z) && !g.isWater(x + 2, z) && !g.isWater(x - 2, z) && !g.isWater(x, z + 2) && !g.isWater(x, z - 2)) {
						int d = (x - cx) * (x - cx) + (z - cz) * (z - cz);
						if (d < best) {
							best = d;
							hx = x;
							hz = z;
						}
					}
				}
			}
		}
		int hy = g.ground(hx, hz);

		// the lie of the land: a plane fitted to the ground within 40 blocks
		double sxx = 0;
		double szz = 0;
		double sxz = 0;
		double sxh = 0;
		double szh = 0;
		double sh = 0;
		int n = 0;
		for (int dz = -40; dz <= 40; dz += 4) {
			for (int dx = -40; dx <= 40; dx += 4) {
				if (dx * dx + dz * dz > 1600) {
					continue;
				}
				double h = g.surface(hx + dx, hz + dz);
				sxx += dx * dx;
				szz += dz * dz;
				sxz += dx * dz;
				sxh += dx * h;
				szh += dz * h;
				sh += h;
				n++;
			}
		}
		double mean = sh / n;
		sxh -= 0;
		double det = sxx * szz - sxz * sxz;
		float gradX = det == 0 ? 0 : (float) ((sxh * szz - szh * sxz) / det);
		float gradZ = det == 0 ? 0 : (float) ((szh * sxx - sxh * sxz) / det);

		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		for (int dz = -64; dz <= 64; dz += 4) {
			for (int dx = -64; dx <= 64; dx += 4) {
				if (dx * dx + dz * dz > 4096) {
					continue;
				}
				int h = g.ground(hx + dx, hz + dz);
				lo = Math.min(lo, h);
				hi = Math.max(hi, h);
			}
		}

		// water: where, how much, which way it runs
		int water = 0;
		int samples = 0;
		int nearest = Integer.MAX_VALUE;
		int wx = hx;
		int wz = hz;
		int seaLike = 0;
		for (int dz = -128; dz <= 128; dz += 4) {
			for (int dx = -128; dx <= 128; dx += 4) {
				int d2 = dx * dx + dz * dz;
				if (d2 > 128 * 128) {
					continue;
				}
				samples++;
				int w = g.water(hx + dx, hz + dz);
				if (w == dev.voidmc.hearth.api.TerrainProbe.NO_WATER) {
					continue;
				}
				water++;
				if (w <= sea + 1) {
					seaLike++;
				}
				if (d2 < nearest) {
					nearest = d2;
					wx = hx + dx;
					wz = hz + dz;
				}
			}
		}
		Site.WaterKind kind = Site.WaterKind.NONE;
		float axis = 0;
		int waterDistance = water == 0 ? Integer.MAX_VALUE : (int) Math.sqrt(nearest);
		if (water > 0 && waterDistance < 112) {
			// principal axis of the water near its nearest point
			double mx = 0;
			double mz = 0;
			int m = 0;
			for (int dz = -40; dz <= 40; dz += 2) {
				for (int dx = -40; dx <= 40; dx += 2) {
					if (g.isWater(wx + dx, wz + dz)) {
						mx += dx;
						mz += dz;
						m++;
					}
				}
			}
			mx /= m;
			mz /= m;
			double cxx = 0;
			double czz = 0;
			double cxz = 0;
			for (int dz = -40; dz <= 40; dz += 2) {
				for (int dx = -40; dx <= 40; dx += 2) {
					if (g.isWater(wx + dx, wz + dz)) {
						cxx += (dx - mx) * (dx - mx);
						czz += (dz - mz) * (dz - mz);
						cxz += (dx - mx) * (dz - mz);
					}
				}
			}
			axis = (float) (0.5 * StrictMath.atan2(2 * cxz, cxx - czz));
			double tr = cxx + czz;
			double dif = Math.sqrt((cxx - czz) * (cxx - czz) + 4 * cxz * cxz);
			double elong = tr <= 0 ? 1 : (tr + dif) / Math.max(1e-6, tr - dif);
			double fraction = water / (double) samples;
			if (seaLike > water * 0.7 && fraction > 0.18) {
				kind = Site.WaterKind.COAST;
				// the shore runs across the way to the water's mass
				double ang = StrictMath.atan2(mz + (wz - hz), mx + (wx - hx));
				axis = (float) (ang + Math.PI / 2);
			} else if (elong > 3.5) {
				kind = Site.WaterKind.RIVER;
			} else {
				kind = Site.WaterKind.LAKE;
				double ang = StrictMath.atan2(wz - hz, wx - hx);
				axis = (float) (ang + Math.PI / 2);
			}
		}

		// the rise: prominent, dry, not too far
		int riseX = hx;
		int riseZ = hz;
		int riseY = hy;
		double riseScore = -1e9;
		for (int dz = -72; dz <= 72; dz += 4) {
			for (int dx = -72; dx <= 72; dx += 4) {
				int d2 = dx * dx + dz * dz;
				if (d2 > 72 * 72 || d2 < 14 * 14) {
					continue;
				}
				int x = hx + dx;
				int z = hz + dz;
				if (g.isWater(x, z) || g.slope(x, z, 3) > 0.45) {
					continue;
				}
				int h = g.ground(x, z);
				double around = 0;
				for (int k = 0; k < 8; k++) {
					around += g.surface(x + DX8[k] * 14, z + DZ8[k] * 14);
				}
				around /= 8;
				double score = (h - around) * 1.5 + (h - hy) * 0.35 - Math.sqrt(d2) * 0.04;
				if (score > riseScore) {
					riseScore = score;
					riseX = x;
					riseZ = z;
					riseY = h;
				}
			}
		}

		int summitX = hx;
		int summitZ = hz;
		int summitY = hy;
		for (int dz = -176; dz <= 176; dz += 8) {
			for (int dx = -176; dx <= 176; dx += 8) {
				int d2 = dx * dx + dz * dz;
				if (d2 > 176 * 176) {
					continue;
				}
				int x = hx + dx;
				int z = hz + dz;
				if (g.isWater(x, z)) {
					continue;
				}
				int h = g.ground(x, z);
				if (h > summitY || (h == summitY && d2 < (summitX - hx) * (summitX - hx) + (summitZ - hz) * (summitZ - hz))) {
					summitY = h;
					summitX = x;
					summitZ = z;
				}
			}
		}

		// a cliff near the heart: a big drop over a short run
		boolean cliff = false;
		float cliffAngle = 0;
		int cliffDrop = 0;
		for (int dz = -48; dz <= 48; dz += 4) {
			for (int dx = -48; dx <= 48; dx += 4) {
				if (dx * dx + dz * dz > 48 * 48) {
					continue;
				}
				int x = hx + dx;
				int z = hz + dz;
				int h = g.ground(x, z);
				for (int k = 0; k < 8; k += 2) {
					int drop = h - g.ground(x + DX8[k] * 8, z + DZ8[k] * 8);
					if (drop > cliffDrop) {
						cliffDrop = drop;
						cliffAngle = (float) StrictMath.atan2(DZ8[k], DX8[k]);
					}
				}
			}
		}
		cliff = cliffDrop >= 12;

		int good = 0;
		int all = 0;
		for (int dz = -96; dz <= 96; dz += 8) {
			for (int dx = -96; dx <= 96; dx += 8) {
				if (dx * dx + dz * dz > 96 * 96) {
					continue;
				}
				all++;
				int x = hx + dx;
				int z = hz + dz;
				if (!g.isWater(x, z) && g.slope(x, z, 4) < 0.4) {
					good++;
				}
			}
		}

		boolean hasHeadland = false;
		int headX = 0;
		int headZ = 0;
		if (kind == Site.WaterKind.COAST || kind == Site.WaterKind.LAKE) {
			int bestWet = 4;
			int bestD = Integer.MAX_VALUE;
			for (int dz = -128; dz <= 128; dz += 8) {
				for (int dx = -128; dx <= 128; dx += 8) {
					int d2 = dx * dx + dz * dz;
					if (d2 > 128 * 128) {
						continue;
					}
					int x = hx + dx;
					int z = hz + dz;
					if (g.isWater(x, z) || g.slope(x, z, 3) > 0.6) {
						continue;
					}
					int wet = 0;
					for (int k = 0; k < 8; k++) {
						if (g.isWater(x + DX8[k] * 18, z + DZ8[k] * 18)) {
							wet++;
						}
					}
					if (wet > bestWet || (wet == bestWet && wet > 4 && d2 < bestD)) {
						bestWet = wet;
						bestD = d2;
						hasHeadland = true;
						headX = x;
						headZ = z;
					}
				}
			}
		}

		return new Site(hx, hy, hz, gradX, gradZ, hi - lo, kind, wx, wz, axis, waterDistance, riseX, riseY, riseZ, summitX, summitY, summitZ,
			cliff, cliffAngle, cliffDrop, all == 0 ? 0 : good / (float) all, hasHeadland, headX, headZ);
	}
}
