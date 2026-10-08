package dev.voidmc.expanse.world.terrain;

import java.util.List;

/**
 * The great mines: where a realm lies under a high mountain, men once sank a shaft into the mountain's flank to reach
 * it. A pithead stands high on the slope (a timber headframe over the shaft on a deck, a spoil heap tipped down the
 * hill), and from it the shaft drops straight down, five blocks square, ladder up one wall and the winding chain down
 * the middle, two hundred blocks and more through the rock. Every thirty blocks or so a level opens off it: a timbered
 * station round the shaft, and drifts running out from it along the veins, five blocks wide and five high, framed every
 * four blocks, rails down the middle, the faces where they stopped still showing ore. At the foot the great drift runs
 * on level into the realm's wall and breaks out high on its slopes: the way the miners found the deep.
 *
 * <p>One mine at most in each {@value #CELL}-block cell, about half of them: only where a realm (not a fire realm, nor
 * under a buried sea) has high ground ({@code y} 130 and up) beside it, open and steady enough for a pithead. The
 * shapes are carved with the passages ({@link CavernModel#fine}); what stands in them is placed by {@link MineLife}.
 */
public final class GreatMines {
	/** One great mine at most in each cell this wide. */
	public static final int CELL = 2400;
	/** How far a mine reaches from its shaft, at most. */
	static final int REACH = 200;
	/** The shaft's half-width: open five blocks square. */
	static final float SHAFT = 2.5F;
	/** A level's station round the shaft: open eleven blocks square. */
	static final float STATION = 5.5F;
	/** A station's height (open blocks). */
	static final int STATION_HEIGHT = 5;

	private GreatMines() {
	}

	/**
	 * One drift: a gallery running straight out from the shaft along the x axis ({@code alongX}) or the z axis, toward
	 * {@code sign}. Open from the shaft out to {@code length} blocks, {@code half} blocks either side of its line,
	 * from {@code floor} (the lowest open block) up {@code height} blocks. {@code toRealm} for the great drift at the
	 * foot, the one that breaks into the realm.
	 */
	public record Drift(boolean alongX, int sign, int floor, int length, int half, int height, boolean toRealm) {
		/** How far along the drift (x, z) lies, from the shaft's centre (cx, cz). */
		int along(int dx, int dz) {
			return this.alongX ? this.sign * dx : this.sign * dz;
		}

		/** How far across the drift's line (x, z) lies. */
		int across(int dx, int dz) {
			return this.alongX ? dz : dx;
		}
	}

	/**
	 * A great mine: its shaft at (x, z), from the pithead's ground {@code ground} down to {@code bottom} (the great
	 * drift's floor); the realm it reaches, centred at (realmX, realmZ); its drifts (the great drift first).
	 */
	public record Mine(boolean exists, int x, int z, int ground, int bottom, int realmX, int realmZ, List<Drift> drifts) {
		static final Mine NONE = new Mine(false, 0, 0, 0, 0, 0, 0, List.of());

		/** The great drift, into the realm. */
		public Drift great() {
			return this.drifts.getFirst();
		}

		/** Where the great drift ends, inside the realm: x. */
		public int mouthX() {
			Drift d = this.great();
			return d.alongX() ? this.x + d.sign() * d.length() : this.x;
		}

		/** Where the great drift ends, inside the realm: z. */
		public int mouthZ() {
			Drift d = this.great();
			return d.alongX() ? this.z : this.z + d.sign() * d.length();
		}
	}

	/**
	 * Whether the mine reaches column (x, z) at all; if it does, widens the column's band of fine spaces to take it in.
	 */
	static boolean reach(Mine m, int x, int z, CavernModel.Info out) {
		int dx = x - m.x();
		int dz = z - m.z();
		int ch = Math.max(Math.abs(dx), Math.abs(dz));
		float low = Float.MAX_VALUE;
		float high = -Float.MAX_VALUE;
		if (ch <= 3) {
			low = m.bottom();
			high = m.ground() + 5;
		}
		for (Drift d : m.drifts()) {
			int along = d.along(dx, dz);
			if (ch <= STATION + 1 || Math.abs(d.across(dx, dz)) <= d.half() + 1 && along >= -d.half() - 1 && along <= d.length() + 1) {
				low = Math.min(low, d.floor());
				high = Math.max(high, d.floor() + Math.max(d.height(), STATION_HEIGHT));
			}
		}
		if (low > high) {
			return false;
		}
		out.fine = true;
		out.fineLow = Math.min(out.fineLow, low);
		out.fineHigh = Math.max(out.fineHigh, high);
		return true;
	}

	/** How far (blocks) the point is inside the mine's open space, as {@link CavernModel#inside}. */
	static float inside(Mine m, int x, int y, int z) {
		int dx = x - m.x();
		int dz = z - m.z();
		int ch = Math.max(Math.abs(dx), Math.abs(dz));
		// the shaft, from the foot to the open air over the pithead
		float best = Math.min(Math.min(SHAFT - ch, y - (m.bottom() - 0.5F)), m.ground() + 4.5F - y);
		for (Drift d : m.drifts()) {
			float vertical = Math.min(y - (d.floor() - 0.5F), d.floor() + d.height() - 0.5F - y);
			if (ch < STATION + 1) {
				float station = Math.min(Math.min(STATION - ch, y - (d.floor() - 0.5F)), d.floor() + STATION_HEIGHT - 0.5F - y);
				best = Math.max(best, station);
			}
			int along = d.along(dx, dz);
			float in = Math.min(Math.min(d.half() + 0.5F - Math.abs(d.across(dx, dz)), along + 0.5F), d.length() + 0.5F - along);
			best = Math.max(best, Math.min(in, vertical));
		}
		return best;
	}
}
