package dev.voidmc.hearth.build.kit.extraction;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.RailShape;

/**
 * An adit: a level driven into the land from the open, the way miners have always reached ore under a hillside
 * (docs/LIVING_WORLD.md §7.3). It runs straight back along the plot's axis column {@code c}, three wide; its profile is
 * a miner's — the sides three high, the crown four, so the timber sets stand square with their caps a man's reach
 * overhead. Where the land in front gives no cover (level ground, the foot of a gentle slope) the way in is an open
 * cutting that goes down a block a step until two courses of the land stand over the crown; there the portal is set:
 * a doubled timber frame with its hood, dry-stone wing walls holding the cutting's sides. Underground the drift keeps
 * its level while the cover holds and dips a step where the land over it falls away.
 *
 * <p>The drift is driven in <b>segments</b>, each a part of the blueprint (one gallery's worth of weeks): the cuts slice
 * by slice as the heading advances (each slice its own order key, top course first), then the timber sets every three
 * blocks, then the rails and lanterns; a stall opened into one side (the ore followed off the level) and the
 * {@code WORK_FACE} points at the heading and at the stall's end where the miners stand. The cutting and the portal go
 * in with the first segment.
 */
public final class Drift {
	/** The interior's side height (y L..L+2 at the sides; the crown is cut one higher at the axis). */
	public static final int SIDE = 3;

	private final Draft d;
	private final Rock rock;
	/** The axis column (the drift is c - 1 .. c + 1). */
	public final int c;
	/** The first row of the drift (behind the yard). */
	public final int z0;
	/** The last row cut (inclusive), or z0 - 1 if none. */
	public int end;
	/** The first row with cover over the crown: the portal (end + 1 if the drift never goes under). */
	public int portal;
	/** The standing level of each row from z0 (index z - z0). */
	private final int[] level;
	private final int lo;
	private final int hi;

	/**
	 * Plans an adit along column {@code c} from row {@code z0} (its yard standing at level 0 in front) to at most row
	 * {@code zMax}, no deeper than {@code minLevel}, the drift's sides kept to columns {@code lo..hi} (stalls included).
	 */
	public Drift(Draft d, Rock rock, int c, int z0, int zMax, int minLevel, int lo, int hi) {
		this.d = d;
		this.rock = rock;
		this.c = c;
		this.z0 = z0;
		this.lo = lo;
		this.hi = hi;
		this.level = new int[Math.max(0, zMax - z0 + 1)];
		int l = 0;
		this.end = z0 - 1;
		this.portal = Integer.MAX_VALUE;
		for (int z = z0; z <= zMax; z++) {
			if (wet(z)) {
				break;
			}
			int g = cover(z);
			// keep the level while the land stands two courses over the crown (one, once under); else down a step
			int need = this.portal == Integer.MAX_VALUE ? 6 : 5;
			int next = g - l >= need ? l : Math.max(l - 1, minLevel);
			if (this.portal != Integer.MAX_VALUE && g - next < 5) {
				// the land over the drift falls away faster than it can dip: the drift stops short of daylight
				break;
			}
			if (this.portal == Integer.MAX_VALUE && this.d.site().ground(c, z) < next - 1 && z > z0 + 1) {
				// the land falls away from the street faster than the cutting can follow: no embankment out over it
				break;
			}
			boolean stepped = next != l || z == z0 && next != 0;
			l = next;
			this.level[z - z0] = l;
			this.end = z;
			// the portal stands on a level stretch: a step down under its lantern and doubled set is a step nobody can take
			// upright, so the cutting runs on a row at the new level before the hill closes over it
			if (this.portal == Integer.MAX_VALUE && g - l >= 6 && !stepped) {
				this.portal = z;
			}
		}
		if (this.portal == Integer.MAX_VALUE) {
			this.portal = this.end + 1;
		}
	}

	/** The lowest natural ground over the drift's three columns at row z. */
	private int cover(int z) {
		return this.d.site().minGround(this.c - 1, z, this.c + 1, z);
	}

	/** Water within two of the drift's columns (a level is never driven under a river or a lake). */
	private boolean wet(int z) {
		return this.d.site().anyWater(Math.max(0, this.c - 3), Math.max(0, z - 2), Math.min(this.d.frontage() - 1, this.c + 3),
			Math.min(this.d.depth() - 1, z + 2));
	}

	/** The standing level at row z (0 in front of the drift). */
	public int level(int z) {
		return z < this.z0 ? 0 : this.level[Math.min(z, this.end) - this.z0];
	}

	/** Whether row z of the drift lies under the land (behind the portal). */
	public boolean under(int z) {
		return z >= this.portal && z <= this.end;
	}

	/** Rows of the drift underground. */
	public int underLength() {
		return Math.max(0, this.end - this.portal + 1);
	}

	/** Whether a row carries a timber set (the portal, then every third row; never the last row cut, the heading). */
	public boolean set(int z) {
		return z >= this.portal && z <= this.end && (z - this.portal) % 3 == 0;
	}

	// ------------------------------------------------------------------------------------------- the driving

	/**
	 * Drives the drift from row {@code za} to {@code zb} (inclusive) in part {@code part}: the cuts slice by slice, the
	 * sets, rails and lanterns behind them, a stall on side {@code stallSide} (±1, 0: none), the work faces. The first
	 * segment ({@code za == z0}) brings the cutting and the portal.
	 */
	public void drive(int part, int za, int zb, int stallSide, JobType job) {
		Mat frame = this.d.mat(Role.FRAME);
		Mat beam = this.d.mat(Role.BEAM);
		BlockState air = Blocks.AIR.defaultBlockState();
		int[] stall = stallSide == 0 ? null : stall(za, zb, stallSide);
		try (var ps = this.d.campaign(part)) {
			try (var st = this.d.stage(StageKind.EARTHWORK)) {
				int key = 0;
				for (int z = za; z <= zb; z++, key++) {
					int l = level(z);
					try (var o = this.d.order(key)) {
						for (int x = this.c - 1; x <= this.c + 1; x++) {
							int g = this.d.site().ground(x, z);
							// in the open the cutting is cleared to the sky (of the yard's trodden floor too, where it runs through it)
							int top = under(z) ? l + (x == this.c ? SIDE + 1 : SIDE) : Math.max(g, l + SIDE + 1);
							if (!under(z)) {
								for (int y = top; y < this.d.maxHeight(); y++) {
									BlockState was = this.d.get(x, y, z);
									if (was != null && !was.isAir()) {
										top = y + 1;
									}
								}
							}
							top = Math.min(top, this.d.maxHeight());
							for (int y = top - 1; y >= l; y--) {
								if (y < g || this.d.get(x, y, z) != null) {
									this.d.set(x, y, z, air);
								}
							}
							// the open cutting on low ground: its floor made up where the land dips under it
							if (!under(z) && g < l) {
								try (var f = this.d.stage(StageKind.FOUNDATION)) {
									for (int y = g; y <= l - 1; y++) {
										this.d.set(x, y, z, this.rock.rough().defaultBlockState());
									}
								}
							}
						}
						if (stall != null && z >= stall[1] && z <= stall[3]) {
							for (int x = stall[0]; x <= stall[2]; x++) {
								for (int y = l + SIDE - 1; y >= l; y--) {
									this.d.set(x, y, z, air);
								}
							}
						}
					}
				}
				// the treads where the floor steps down (cut into the rock left standing) at the sides; the rails take the axis
				try (var o = this.d.order(key)) {
					// the first step down off the yard's floor, where the cutting starts below it
					if (za == this.z0 && this.z0 >= 1 && level(this.z0) < 0) {
						for (int x : new int[]{this.c - 1, this.c + 1}) {
							this.d.set(x, -1, this.z0 - 1, States.stairs(this.rock.stairs(), Dirs.FRONT, Half.BOTTOM));
						}
					}
					for (int z = Math.max(za, this.z0); z <= zb; z++) {
						if (z + 1 <= this.end && level(z + 1) < level(z)) {
							for (int x : new int[]{this.c - 1, this.c + 1}) {
								this.d.set(x, level(z) - 1, z, States.stairs(this.rock.stairs(), Dirs.FRONT, Half.BOTTOM));
							}
						}
					}
				}
			}
			if (za == this.z0) {
				portal(frame, beam);
				parapet();
			}
			// the timber sets: two posts and a cap, the cap across the crown
			try (var st = this.d.stage(StageKind.FRAME)) {
				for (int z = Math.max(za, this.portal + 1); z <= zb; z++) {
					if (!set(z) || z == this.end) {
						continue;
					}
					timberSet(frame, beam, z);
				}
			}
			try (var st = this.d.stage(StageKind.EXTERIOR)) {
				// the rails along the axis, ascending where the floor steps
				for (int z = za == this.z0 && za > 0 ? za - 1 : za; z <= zb; z++) {
					int l = level(z);
					RailShape shape = z - 1 >= this.z0 - 1 && level(z - 1) > l ? RailShape.ASCENDING_SOUTH : RailShape.NORTH_SOUTH;
					this.d.set(this.c, l, z, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, shape));
				}
				// lanterns under every other cap, and at the heading's last set
				Block lantern = this.d.mat(Role.LIGHT).mainOr(Form.FULL, Blocks.LANTERN);
				for (int z = za; z <= zb; z++) {
					if (set(z) && z != this.end && (z - this.portal) % 6 == 0 && this.d.isEmpty(this.c, level(z) + 2, z)) {
						this.d.set(this.c, level(z) + 2, z, States.lantern(lantern, true));
					}
				}
				if (stall != null) {
					int sx = stallSide > 0 ? stall[2] : stall[0];
					int sz = (stall[1] + stall[3]) / 2;
					int l = level(sz);
					// a lantern hung from a peg in the stall's roof, at its mouth
					int mx = stallSide > 0 ? stall[0] : stall[2];
					if (this.d.isEmpty(mx, l + SIDE - 1, sz)) {
						this.d.set(mx, l + SIDE - 1, sz, States.lantern(lantern, true));
					}
					this.d.poi(PoiType.WORK_FACE, sx, l, sz, stallSide > 0 ? Dirs.RIGHT : Dirs.LEFT, job);
				}
			}
			// the heading: where the miners stand at the end of the segment, facing the rock
			this.d.poi(PoiType.WORK_FACE, this.c, level(zb), zb, Dirs.BACK, job);
		}
	}

	/**
	 * A set at row z: posts let into the sides to the cap, the cap across the crown resting on them. The posts stand in
	 * the rock's line, not in the gallery: the whole three-wide floor stays clear, the rails down the middle and a way
	 * either side of them for the miners (folk will not walk a rail they did not start on, so a set that left only the
	 * track between its posts would shut them out of the drift).
	 */
	private void timberSet(Mat frame, Mat beam, int z) {
		int l = level(z);
		for (int y = l - 1; y < l + SIDE; y++) {
			for (int x : new int[]{this.c - 2, this.c + 2}) {
				if (x >= 0 && x < this.d.frontage()) {
					this.d.set(x, y, z, frame.log(Direction.Axis.Y));
				}
			}
		}
		for (int x = Math.max(0, this.c - 2); x <= Math.min(this.d.frontage() - 1, this.c + 2); x++) {
			this.d.set(x, l + SIDE, z, beam.log(Direction.Axis.X));
		}
	}

	/**
	 * The portal: the set at the first covered row doubled by one standing in the cutting before it, a hood of boards
	 * over the outer cap, dry-stone wing walls lining the cutting's sides for three rows out.
	 */
	private void portal(Mat frame, Mat beam) {
		if (this.portal > this.end) {
			return;
		}
		int l = level(this.portal);
		int out = this.portal - 1;
		boolean outer = out >= 0 && level(out) == l && (out >= this.z0 || free(out, l));
		try (var st = this.d.stage(StageKind.FRAME)) {
			timberSet(frame, beam, this.portal);
			if (outer) {
				timberSet(frame, beam, out);
			}
		}
		if (outer) {
			// the headwall: dry stone laid up against the hill's face either side of the portal, the hood resting on it
			try (var st = this.d.stage(StageKind.FOUNDATION)) {
				for (int x = Math.max(this.lo, this.c - 3); x <= Math.min(this.hi, this.c + 3); x++) {
					// in the yard's back row only where the bank stands over the yard, never on anyone's standing place
					if (Math.abs(x - this.c) <= 1 || out < this.z0 && this.d.site().ground(x, out) <= l || standing(x, out)) {
						continue;
					}
					int top = Math.clamp(this.d.site().ground(x, this.portal), l + SIDE + 1, l + SIDE + 3);
					// on its footing: down to the land where the land beside the portal lies lower than the floor
					int foot = Math.min(l - 1, this.d.site().ground(x, out));
					for (int y = foot; y < top && y < this.d.maxHeight(); y++) {
						BlockState was = this.d.get(x, y, out);
						if (was != null && !was.isAir()) {
							continue;
						}
						double u = Noise.unit(this.d.seed() ^ 0xD5A2, x, y, out);
						this.d.set(x, y, out, (u < 0.75 ? this.rock.rough() : this.rock.natural()).defaultBlockState());
					}
				}
			}
			try (var st = this.d.stage(StageKind.ROOF)) {
				for (int x = this.c - 2; x <= this.c + 2; x++) {
					if (this.d.allowed(x, l + SIDE + 1, out) && x >= this.lo && x <= this.hi && this.d.isEmpty(x, l + SIDE + 1, out)) {
						this.d.set(x, l + SIDE + 1, out, beam.stairsOrSlab(Dirs.BACK, Half.BOTTOM));
					}
				}
			}
		}
		// the wing walls: rough stone laid against the cutting's sides, up to the land
		try (var st = this.d.stage(StageKind.FOUNDATION)) {
			for (int z = Math.max(this.z0, this.portal - 3); z < this.portal; z++) {
				int lz = level(z);
				for (int x : new int[]{this.c - 2, this.c + 2}) {
					int g = this.d.site().ground(x, z);
					for (int y = lz - 1; y < Math.min(g, lz + SIDE + 1) && y < this.d.maxHeight(); y++) {
						double u = Noise.unit(this.d.seed() ^ 0xD5A1, x, y, z);
						this.d.set(x, y, z, (u < 0.7 ? this.rock.rough() : this.rock.natural()).defaultBlockState());
					}
				}
			}
		}
		// the portal's lantern, under the outer cap
		Block lantern = this.d.mat(Role.LIGHT).mainOr(Form.FULL, Blocks.LANTERN);
		try (var st = this.d.stage(StageKind.EXTERIOR)) {
			this.d.set(this.c, l + 2, this.portal, States.lantern(lantern, true));
		}
	}

	/**
	 * The cutting's rims: where the land beside the open cutting stands three or more over its floor, a course of the
	 * rough stone is laid on the rim (the wing walls carried up a course past the turf), so nobody walking the hillside
	 * steps off into the cutting; the same over the headwall where the hill stands high over the hood.
	 */
	private void parapet() {
		int last = Math.min(this.portal - 1, this.end);
		// a dry-stone wall's coping (a wall block: too high to step onto), or a post-and-rail fence where the style has none
		Mat stone = this.d.mat(Role.FOUNDATION);
		dev.voidmc.hearth.build.draft.Paint guard = stone.has(Form.WALL) ? stone.wall() : dev.voidmc.hearth.build.kit.trade.Hall.woodFence(this.d).fence();
		try (var st = this.d.stage(StageKind.FOUNDATION)) {
			for (int z = this.z0; z <= last; z++) {
				int l = level(z);
				for (int x = this.c - 1; x <= this.c + 1; x++) {
					// the rims beside the cutting and across its end where it stops in the open
					for (int[] o : new int[][]{{-1, 0}, {1, 0}, {0, 1}}) {
						int nx = x + o[0];
						int nz = z + o[1];
						boolean inCut = Math.abs(nx - this.c) <= 1 && nz <= last;
						if (inCut || nz >= this.portal && this.portal <= this.end || nx < 0 || nx >= this.d.frontage() || nz >= this.d.depth()) {
							continue;
						}
						int g = this.d.site().ground(nx, nz);
						if (g - l >= 3 && g < this.d.maxHeight() && this.d.isEmpty(nx, g, nz)) {
							this.d.set(nx, g, nz, guard);
						}
					}
				}
			}
			if (this.portal <= this.end && this.portal - 1 >= this.z0) {
				// a rail along the hood: from the hill or the tip beside the portal the hood is a ledge over the cutting
				int l = level(this.portal);
				int out = this.portal - 1;
				int hy = l + SIDE + 2;
				for (int x = Math.max(0, this.c - 2); x <= Math.min(this.d.frontage() - 1, this.c + 2); x++) {
					BlockState hood = this.d.get(x, hy - 1, out);
					if (hy < this.d.maxHeight() && hood != null && !hood.isAir() && this.d.isEmpty(x, hy, out)) {
						this.d.set(x, hy, out, guard);
					}
				}
			}
			if (this.portal <= this.end) {
				int l = level(this.portal);
				for (int x = Math.max(0, this.c - 3); x <= Math.min(this.d.frontage() - 1, this.c + 3); x++) {
					int g = this.d.site().ground(x, this.portal);
					// the hood is at l + SIDE + 1 over the outer row: a drop of four or more onto it, or onto the cutting
					if (g - (l + SIDE + 2) >= 3 && g < this.d.maxHeight() && this.d.isEmpty(x, g, this.portal)) {
						this.d.set(x, g, this.portal, guard);
					}
				}
			}
		}
	}

	/** Whether a POI stands in a column, or in front of one (where folk stand to use it). */
	private boolean standing(int x, int z) {
		for (Draft.LocalPoi p : this.d.pois()) {
			if (Math.abs(p.x() - x) + Math.abs(p.z() - z) <= 1) {
				return true;
			}
		}
		return false;
	}

	/** Whether the yard row in front of the portal is free for the outer set (the gallery's three columns, its height). */
	private boolean free(int z, int l) {
		for (int x = this.c - 1; x <= this.c + 1; x++) {
			for (int y = l; y <= l + SIDE; y++) {
				BlockState was = this.d.get(x, y, z);
				if (was != null && !was.isAir()) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * A stall off the drift between rows za and zb on side {@code side}: {x0, z0, x1, z1}, two rows long and up to two
	 * deep, where the land covers it; null if there is no room.
	 */
	private int[] stall(int za, int zb, int side) {
		// the two rows between sets nearest the segment's middle
		int zs = Math.max(Math.max(za, this.portal + 1), (za + zb) / 2 - 1);
		while ((zs - this.portal) % 3 != 1) {
			zs++;
		}
		if (zs + 1 > zb || zs + 1 > this.end) {
			return null;
		}
		int l = level(zs);
		if (level(zs + 1) != l) {
			return null;
		}
		int x0 = side > 0 ? this.c + 2 : Math.max(this.lo, this.c - 3);
		int x1 = side > 0 ? Math.min(this.hi, this.c + 3) : this.c - 2;
		if (x0 > x1 || x0 < this.lo || x1 > this.hi) {
			return null;
		}
		for (int z = zs; z <= zs + 1; z++) {
			for (int x = x0; x <= x1; x++) {
				if (this.d.site().ground(x, z) < l + SIDE + 1 || set(z)) {
					return null;
				}
			}
		}
		return new int[]{x0, zs, x1, zs + 1};
	}

	/** The rows from the drift's start to its end split in segments of about {@code len} (the first runs to past the portal). */
	public List<int[]> segments(int len) {
		List<int[]> out = new ArrayList<>();
		if (this.end < this.z0) {
			return out;
		}
		int under = underLength();
		int n = Math.max(1, Math.round(under / (float) len));
		if (under >= 4) {
			n = Math.max(2, n);
		}
		int start = this.z0;
		for (int k = 0; k < n; k++) {
			int zb = k == n - 1 ? this.end : this.portal + (int) Math.round((k + 1) * under / (double) n) - 1;
			if (zb < start) {
				continue;
			}
			out.add(new int[]{start, zb});
			start = zb + 1;
		}
		return out;
	}
}
