package dev.voidmc.hearth.build.kit.landmark;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * What crowns a tower, the part read from a hundred blocks away and on the far horizon: needle spires (square,
 * octagonal, broached) with corner pinnacles, low tiled pyramids on brackets, onion domes on drums, domes with lanterns,
 * open cupolas, crenellated parapets with a flag, the stacked shingle caps of a stave belfry, and curved hip roofs with
 * lifted corners. Each draws on a square (the tower's outline at the cornice) from the row above the cornice and keeps
 * under the plot's height, scaling its pitch down if it must. Each returns the highest row it used.
 */
public final class Crowns {
	private Crowns() {
	}

	public enum Kind {
		/** A square needle spire, steep, with pinnacles at the corners of a parapet. */
		SPIRE,
		/** An octagonal needle rising from behind a parapet, pinnacles in the corners. */
		OCTAGON,
		/** An octagonal needle on broaches straight off the walls. */
		BROACH,
		/** A low tiled pyramid on deep bracketed eaves (campanile, chalet tower). */
		PYRAMID,
		/** An onion dome on an octagonal drum, a lantern and a little onion on top. */
		ONION,
		/** A drum and a dome with a lantern. */
		DOME,
		/** An open cupola on posts with a little dome over it. */
		CUPOLA,
		/** A crenellated parapet round a deck, a flag in the middle. */
		PARAPET,
		/** Stacked shingle caps with a louvred drum between (stave belfry). */
		STAVE,
		/** A curved hip with lifted corners (karst, wisteria). */
		PAGODA
	}

	/** The materials a crown is made of. */
	public record Mats(Mat roof, Mat trim, Stone dress, Mat frame) {
		public static Mats of(Draft d, Stone dress) {
			return new Mats(d.mat(Role.ROOF), d.mat(Role.ROOF_TRIM), dress, d.mat(Role.FRAME));
		}
	}

	/**
	 * Draws a crown over the square {@code [x0..x1] × [z0..z1]} from row y; nothing above {@code top} (inclusive).
	 * Returns the highest row used.
	 */
	public static int crown(Draft d, Kind kind, int x0, int z0, int x1, int z1, int y, int top, Mats m) {
		try (var s = d.stage(StageKind.ROOF)) {
			return switch (kind) {
				case SPIRE -> spire(d, x0, z0, x1, z1, y, top, m, false);
				case OCTAGON -> spire(d, x0, z0, x1, z1, y, top, m, true);
				case BROACH -> broach(d, x0, z0, x1, z1, y, top, m);
				case PYRAMID -> pyramid(d, x0, z0, x1, z1, y, top, m, 1, 0.75);
				case ONION -> onion(d, x0, z0, x1, z1, y, top, m);
				case DOME -> dome(d, x0, z0, x1, z1, y, top, m);
				case CUPOLA -> cupola(d, x0, z0, x1, z1, y, top, m);
				case PARAPET -> parapet(d, x0, z0, x1, z1, y, top, m, true);
				case STAVE -> stave(d, x0, z0, x1, z1, y, top, m);
				case PAGODA -> pagoda(d, x0, z0, x1, z1, y, top, m);
			};
		}
	}

	/**
	 * Rows a crown of a kind takes over a square {@code size} wide, on a tower {@code height} tall: spires, stave caps and
	 * onions stretch to a share of the height; domes, cupolas, pyramids and curved hips are as tall as their width makes
	 * them. Towers lay out their stages under this.
	 */
	public static int rows(Kind k, int size, int height) {
		double r = (size - 1) / 2.0;
		return switch (k) {
			case SPIRE, OCTAGON -> Math.max(9, (int) Math.round(height * 0.34));
			case BROACH -> Math.max(8, (int) Math.round(height * 0.32));
			case STAVE -> Math.max(9, (int) Math.round(height * 0.3));
			case ONION -> Math.max(11, Math.min(17, (int) Math.round(height * 0.33)));
			case PYRAMID -> (int) Math.round((r + 1) * 0.75) + 3;
			case PAGODA -> (int) Math.round(r + 2) + 3;
			case DOME -> (r >= 3 ? 3 : 2) + (int) Math.ceil(r * 1.05) + 4;
			case CUPOLA -> 5 + (size >= 7 ? (size - 2 >= 7 ? 3 : 2) + (int) Math.ceil((size - 3) / 2.0 * 1.05) + 4 : (int) Math.round(r * 0.75) + 3);
			case PARAPET -> 3;
		};
	}

	/** The pitch that brings a roof of half-width {@code r} to at most {@code room} rows above its eave row. */
	static double fit(double want, double r, int room) {
		if (r <= 0) {
			return want;
		}
		return Math.max(0.5, Math.min(want, (room - 1) / Math.max(1.0, r)));
	}

	/** A needle (square or octagonal) behind a parapet with corner pinnacles. */
	static int spire(Draft d, int x0, int z0, int x1, int z1, int y, int top, Mats m, boolean octagonal) {
		boolean parapet = x1 - x0 >= 6;
		int top0 = y;
		if (parapet) {
			// a parapet of dressed stone, its coping cut in merlons, pinnacles at the corners
			for (int[] c : Draft.ring(x0, z0, x1, z1)) {
				d.set(c[0], y, c[1], m.dress.full());
				boolean merlon = ((c[0] + c[1]) & 1) == 0;
				if (merlon && m.dress.has(Form.SLAB)) {
					d.set(c[0], y + 1, c[1], m.dress.slab(SlabType.BOTTOM));
				}
			}
			int ph = Math.max(2, Math.min(4, (top - y) / 5));
			for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
				top0 = Math.max(top0, Details.pinnacle(d, c[0], y + 1, c[1], ph, m.dress, true));
			}
			x0++;
			z0++;
			x1--;
			z1--;
		}
		double r = (x1 - x0) / 2.0;
		int room = top - y - 1;
		double pitch = fit(5.0, r + 0.5, room);
		double cx = (x0 + x1) / 2.0;
		double cz = (z0 + z1) / 2.0;
		Roofs.Field f = octagonal ? Shapes.octagon(cx, cz, r, pitch) : Shapes.pyramid(x0, z0, x1, z1, pitch);
		Roofs.Roof roof = Roofs.raster(d, f, y, x0, z0, x1, z1, m.roof, m.trim, Habits.Ridge.FULL);
		Shapes.support(d, roof, m.roof.full());
		if (octagonal && !parapet) {
			for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
				top0 = Math.max(top0, Details.pinnacle(d, c[0], y, c[1], 1, m.dress, false));
			}
		}
		// lucarnes: a little gabled light at the foot of the four cardinal faces
		if (r >= 2.5) {
			lucarnes(d, roof, (int) Math.round(cx), (int) Math.round(cz), x0, z0, x1, z1, y, m);
		}
		int peak = roof.ridgeY;
		int fx = (int) Math.round(cx);
		int fz = (int) Math.round(cz);
		int fin = Details.finial(d, fx, peak + 1, fz, false);
		return Math.max(top0, Math.max(peak, fin));
	}

	/** Little gabled openings at the foot of a spire's four faces: a dark light under a pair of stairs. */
	static void lucarnes(Draft d, Roofs.Roof roof, int cx, int cz, int x0, int z0, int x1, int z1, int y, Mats m) {
		for (Side s : Side.of(x0, z0, x1, z1)) {
			int i = s.index(s.axis() == Direction.Axis.X ? cx : cz);
			if (i < 1 || i > s.last() - 1) {
				continue;
			}
			// one block in from the spire's foot on this face
			int x = s.x(i) + Dirs.dx(s.out().getOpposite());
			int z = s.z(i) + Dirs.dz(s.out().getOpposite());
			int sy = roof.surfaceY(x, z);
			if (sy == Integer.MIN_VALUE || sy < y + 2) {
				continue;
			}
			int ly = y + 1;
			// the light: a dark recess in the face, a little roof of stairs over it
			d.set(s.x(i), ly, s.z(i), m.frame.trapdoor(s.out(), Half.BOTTOM, true));
			d.set(s.x(i), ly + 1, s.z(i), m.roof.stairsOrSlab(s.out().getOpposite(), Half.BOTTOM));
			for (int k : new int[]{-1, 1}) {
				int ii = i + k;
				d.set(s.x(ii), ly + 1, s.z(ii), m.roof.stairsOrSlab(k < 0 ? s.along() : s.along().getOpposite(), Half.BOTTOM));
			}
		}
	}

	/** An octagonal spire on broaches straight off the walls (no parapet). */
	static int broach(Draft d, int x0, int z0, int x1, int z1, int y, int top, Mats m) {
		double r = (x1 - x0) / 2.0;
		double pitch = fit(4.5, r + 0.5, top - y - 1);
		Roofs.Roof roof = Roofs.raster(d, Shapes.broach(x0, z0, x1, z1, pitch), y, x0, z0, x1, z1, m.roof, m.trim, Habits.Ridge.FULL);
		Shapes.support(d, roof, m.roof.full());
		int fx = (x0 + x1) / 2;
		int fz = (z0 + z1) / 2;
		return Math.max(roof.ridgeY, Details.finial(d, fx, roof.ridgeY + 1, fz, false));
	}

	/** A low pyramid on eaves with brackets under them. */
	static int pyramid(Draft d, int x0, int z0, int x1, int z1, int y, int top, Mats m, int eaves, double pitch) {
		int ex0 = Math.max(0, x0 - eaves);
		int ez0 = Math.max(-1, z0 - eaves);
		int ex1 = Math.min(d.frontage() - 1, x1 + eaves);
		int ez1 = Math.min(d.depth() - 1, z1 + eaves);
		double r = (x1 - x0) / 2.0 + eaves;
		double p = fit(pitch, r, top - y - 1);
		Roofs.Field f = Shapes.pyramid(x0 - eaves, z0 - eaves, x1 + eaves, z1 + eaves, p);
		Roofs.Roof roof = Roofs.raster(d, f, y, ex0, ez0, ex1, ez1, m.roof, m.trim, Habits.Ridge.FULL);
		Shapes.support(d, roof, m.roof.full());
		// brackets under the eaves, every other cell
		if (eaves >= 1 && m.trim.has(Form.STAIRS)) {
			for (Side s : Side.of(x0, z0, x1, z1)) {
				for (int i = 0; i < s.length(); i += 2) {
					int ox = s.outX(i, 1);
					int oz = s.outZ(i, 1);
					if (d.allowed(ox, y - 1, oz) && d.isEmpty(ox, y - 1, oz)) {
						d.set(ox, y - 1, oz, m.trim.stairs(s.out().getOpposite(), Half.TOP));
					}
				}
			}
		}
		int fx = (x0 + x1) / 2;
		int fz = (z0 + z1) / 2;
		return Math.max(roof.ridgeY, Details.finial(d, fx, roof.ridgeY + 1, fz, false));
	}

	/** An onion dome on an octagonal drum: neck, bulb, an ogee tip, a little lantern and a second onion, a rod. */
	static int onion(Draft d, int x0, int z0, int x1, int z1, int y, int top, Mats m) {
		double cx = (x0 + x1) / 2.0;
		double cz = (z0 + z1) / 2.0;
		double r = (x1 - x0) / 2.0;
		Stone sheet = Stone.sheet(d);
		// the drum: an octagonal ring of the wall stone with little lights, a cornice over it
		int drumH = 2;
		double dr = r - 1.0;
		for (int k = 0; k < drumH; k++) {
			for (int[] c : Shapes.ring(cx, cz, dr)) {
				boolean light = k == 0 && (Math.abs(c[0] - cx) < 0.6 || Math.abs(c[1] - cz) < 0.6);
				d.set(c[0], y + k, c[1], light ? Paint.of(Blocks.GLASS_PANE.defaultBlockState()) : k == drumH - 1 ? m.dress.full() : m.dress.full());
			}
		}
		Details.ringCourse(d, cx, cz, dr, y + drumH - 1, m.dress);
		int yb = y + drumH;
		// the bulb: out over the drum, then in to an ogee point
		double[] profile = dr >= 2
			? new double[]{-0.7, 0.3, 0.9, 1.15, 1.1, 0.85, 0.4, -0.2, -0.9, -1.6, -2.2, -2.6}
			: new double[]{-0.4, 0.8, 1.0, 0.5, -0.1, -0.55};
		int rows = Math.min(profile.length, Math.max(4, top - yb - 4));
		double[] radii = new double[rows];
		for (int i = 0; i < rows; i++) {
			// squeeze the profile into the rows there are
			double t = rows == profile.length ? i : i * (profile.length - 1.0) / (rows - 1.0);
			int a = (int) Math.floor(t);
			int b = Math.min(profile.length - 1, a + 1);
			double v = profile[a] + (profile[b] - profile[a]) * (t - a);
			radii[i] = Math.max(0.4, dr + v);
		}
		Shapes.turned(d, cx, cz, yb, radii, sheet.full(), sheet);
		int yl = yb + rows;
		// the lantern: a short post with a little onion, then a gilded ball and the rod
		int fx = (int) Math.round(cx);
		int fz = (int) Math.round(cz);
		Block fence = m.frame.main(Form.FENCE);
		if (yl + 3 <= top) {
			d.set(fx, yl, fz, fence != null ? fence.defaultBlockState() : m.dress.wallOrFull().at(fx, yl, fz));
			d.set(fx, yl + 1, fz, sheet.full());
			d.set(fx, yl + 2, fz, sheet.has(Form.SLAB) ? sheet.slab(SlabType.BOTTOM) : sheet.full());
			return Math.max(yl + 2, Details.finial(d, fx, yl + 3, fz, true));
		}
		return Math.max(yl - 1, Details.finial(d, fx, yl, fz, false));
	}

	/** A drum with lights, a dome over it, a lantern on top. */
	static int dome(Draft d, int x0, int z0, int x1, int z1, int y, int top, Mats m) {
		double cx = (x0 + x1) / 2.0;
		double cz = (z0 + z1) / 2.0;
		double r = (x1 - x0) / 2.0 - 0.5;
		Stone sheet = Stone.sheet(d);
		int drumH = r >= 3 ? 3 : 2;
		for (int k = 0; k < drumH; k++) {
			for (int[] c : Shapes.ring(cx, cz, r)) {
				boolean light = k >= 1 && k < drumH && (Math.abs(c[0] - cx) < 0.6 || Math.abs(c[1] - cz) < 0.6) && drumH >= 3;
				d.set(c[0], y + k, c[1], light ? Paint.of(Blocks.GLASS_PANE.defaultBlockState()) : m.dress.full());
			}
		}
		Details.ringCourse(d, cx, cz, r, y + drumH - 1, m.dress);
		int yb = y + drumH;
		int h = (int) Math.ceil(r * 1.05) + 1;
		h = Math.min(h, top - yb - 2);
		double[] radii = new double[Math.max(2, h)];
		for (int i = 0; i < radii.length; i++) {
			double t = (double) i / radii.length;
			radii[i] = (r + 0.2) * Math.sqrt(Math.max(0, 1 - t * t)) - 0.15;
		}
		Shapes.turned(d, cx, cz, yb, radii, sheet.full(), sheet);
		int fx = (int) Math.round(cx);
		int fz = (int) Math.round(cz);
		int yl = yb + radii.length;
		if (yl + 2 <= top) {
			d.set(fx, yl, fz, m.dress.wallOrFull());
			d.set(fx, yl + 1, fz, m.dress.full());
			return Math.max(yl + 1, Details.finial(d, fx, yl + 2, fz, true));
		}
		return Math.max(yl - 1, Details.finial(d, fx, yl, fz, false));
	}

	/** An open cupola on four posts over a deck, a bell under its roof, a little dome or pyramid on top. */
	static int cupola(Draft d, int x0, int z0, int x1, int z1, int y, int top, Mats m) {
		// a balustrade round the deck
		for (int[] c : Draft.ring(x0, z0, x1, z1)) {
			d.set(c[0], y, c[1], m.dress.wallOrFull());
		}
		int ix0 = x0 + 1;
		int iz0 = z0 + 1;
		int ix1 = x1 - 1;
		int iz1 = z1 - 1;
		if (ix1 - ix0 < 2) {
			ix0 = x0;
			iz0 = z0;
			ix1 = x1;
			iz1 = z1;
		}
		int postH = 3;
		for (int[] c : new int[][]{{ix0, iz0}, {ix1, iz0}, {ix0, iz1}, {ix1, iz1}}) {
			for (int k = 0; k < postH; k++) {
				d.set(c[0], y + k, c[1], m.dress.wallOrFull());
			}
		}
		// the entablature ring and the roof
		int ye = y + postH;
		for (int[] c : Draft.ring(ix0, iz0, ix1, iz1)) {
			d.set(c[0], ye, c[1], m.dress.full());
		}
		for (int z = iz0 + 1; z < iz1; z++) {
			for (int x = ix0 + 1; x < ix1; x++) {
				d.set(x, ye, z, m.dress.full());
			}
		}
		Details.bell(d, (ix0 + ix1) / 2, ye - 1, (iz0 + iz1) / 2, Dirs.FRONT);
		double r = (ix1 - ix0) / 2.0;
		int peak;
		if (ix1 - ix0 >= 4) {
			peak = dome(d, ix0, iz0, ix1, iz1, ye + 1, top, m);
		} else {
			peak = pyramid(d, ix0, iz0, ix1, iz1, ye + 1, top, m, 1, 1.0);
		}
		return Math.max(peak, ye);
	}

	/** A crenellated parapet round a deck with a flag in the middle; the deck is a viewpoint. */
	static int parapet(Draft d, int x0, int z0, int x1, int z1, int y, int top, Mats m, boolean flag) {
		for (int[] c : Draft.ring(x0, z0, x1, z1)) {
			d.set(c[0], y, c[1], m.dress.full());
			boolean merlon = ((c[0] + c[1]) & 1) == 0 || isCorner(c, x0, z0, x1, z1);
			if (merlon && y + 1 <= top) {
				d.set(c[0], y + 1, c[1], m.dress.full());
			}
		}
		int peak = y + 1;
		if (flag) {
			int fx = (x0 + x1) / 2;
			int fz = (z0 + z1) / 2;
			Block fence = m.frame.main(Form.FENCE);
			int h = Math.min(4, top - y - 1);
			for (int k = 0; k < h; k++) {
				d.set(fx, y + k, fz, fence != null ? fence.defaultBlockState() : Blocks.OAK_FENCE.defaultBlockState());
			}
			if (y + h <= top) {
				Details.banner(d, fx, y + h, fz, 0);
				peak = Math.max(peak, y + h + 1);
			}
		}
		return peak;
	}

	/** Whether a ring cell is a corner of the rectangle. */
	public static boolean isCorner(int[] c, int x0, int z0, int x1, int z1) {
		return (c[0] == x0 || c[0] == x1) && (c[1] == z0 || c[1] == z1);
	}

	/** A stave belfry's caps: a skirt of shingles, a louvred drum, a steep cap, a rod. */
	static int stave(Draft d, int x0, int z0, int x1, int z1, int y, int top, Mats m) {
		// the skirt: a low roof over the square, capped where the drum stands
		int ix0 = x0 + 1;
		int iz0 = z0 + 1;
		int ix1 = x1 - 1;
		int iz1 = z1 - 1;
		Roofs.Field skirt = Shapes.outside(Shapes.capped(Shapes.pyramid(x0 - 1, z0 - 1, x1 + 1, z1 + 1, 1.0), 2.5), ix0, iz0, ix1, iz1);
		int ex0 = Math.max(0, x0 - 1);
		int ez0 = Math.max(-1, z0 - 1);
		int ex1 = Math.min(d.frontage() - 1, x1 + 1);
		int ez1 = Math.min(d.depth() - 1, z1 + 1);
		Roofs.raster(d, skirt, y - 1, ex0, ez0, ex1, ez1, m.roof, m.trim, Habits.Ridge.SLAB);
		// the drum: posts at the corners, louvres (trapdoors) between
		Mat frame = m.frame;
		int dh = 3;
		for (Side s : Side.of(ix0, iz0, ix1, iz1)) {
			for (int i = 0; i < s.length(); i++) {
				for (int k = 0; k < dh; k++) {
					int yy = y + k;
					if (s.isCorner(i)) {
						d.set(s.x(i), yy, s.z(i), frame.log(Direction.Axis.Y));
					} else if (k == dh - 1) {
						d.set(s.x(i), yy, s.z(i), frame.log(s.axis()));
					} else {
						d.set(s.x(i), yy, s.z(i), frame.has(Form.TRAPDOOR) ? frame.trapdoor(s.out().getOpposite(), Half.TOP, true) : frame.fence());
					}
				}
			}
		}
		int yc = y + dh;
		double r = (ix1 - ix0) / 2.0 + 1;
		double pitch = fit(2.5, r, top - yc - 1);
		Roofs.Roof cap = Roofs.raster(d, Shapes.pyramid(ix0 - 1, iz0 - 1, ix1 + 1, iz1 + 1, pitch), yc, Math.max(0, ix0 - 1), Math.max(-1, iz0 - 1),
			Math.min(d.frontage() - 1, ix1 + 1), Math.min(d.depth() - 1, iz1 + 1), m.roof, m.trim, Habits.Ridge.FULL);
		Shapes.support(d, cap, m.roof.full());
		int fx = (x0 + x1) / 2;
		int fz = (z0 + z1) / 2;
		return Math.max(cap.ridgeY, Details.finial(d, fx, cap.ridgeY + 1, fz, false));
	}

	/** A curved hip roof with lifted, lantern-hung corners and a finial of stacked discs. */
	static int pagoda(Draft d, int x0, int z0, int x1, int z1, int y, int top, Mats m) {
		int e = 2;
		int ex0 = Math.max(0, x0 - e);
		int ez0 = Math.max(-1, z0 - e);
		int ex1 = Math.min(d.frontage() - 1, x1 + e);
		int ez1 = Math.min(d.depth() - 1, z1 + e);
		double r = (x1 - x0) / 2.0 + e;
		double pitch = fit(1.0, r, top - y - 2);
		Roofs.Field f = Roofs.flared(Roofs.hip(x0 - e, z0 - e, x1 + e, z1 + e, Roofs.Profile.curved(pitch)), x0 - e, z0 - e, x1 + e, z1 + e, 1, 2.5);
		Roofs.Roof roof = Roofs.raster(d, f, y - 1, ex0, ez0, ex1, ez1, m.roof, m.trim, Habits.Ridge.FULL);
		Shapes.support(d, roof, m.roof.full());
		tips(d, roof, ex0, ez0, ex1, ez1, m);
		int fx = (x0 + x1) / 2;
		int fz = (z0 + z1) / 2;
		return Math.max(roof.ridgeY, Details.finial(d, fx, roof.ridgeY + 1, fz, false));
	}

	/** Up-turned eave tips at the corners of a rasterised hip (a block and a stair rising outward, a lantern under). */
	public static void tips(Draft d, Roofs.Roof r, int x0, int z0, int x1, int z1, Mats m) {
		Block lantern = d.mat(Role.LIGHT).mainOr(Form.FULL, Blocks.LANTERN);
		for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
			int x = c[0];
			int z = c[1];
			if (!r.covers(x, z)) {
				continue;
			}
			int y = r.surfaceY(x, z);
			if (!d.allowed(x, y + 1, z)) {
				continue;
			}
			Direction outX = x == x0 ? Dirs.LEFT : Dirs.RIGHT;
			d.set(x, y, z, m.roof.full());
			d.set(x, y + 1, z, m.roof.stairsOrSlab(outX.getOpposite(), Half.BOTTOM));
			if (d.isAbsent(x, y - 1, z) && y - 1 > d.site().ground(x, z) + 2 && d.allowed(x, y - 1, z)) {
				d.inStage(StageKind.EXTERIOR, () -> d.set(x, y - 1, z, States.lantern(lantern, true)));
			}
		}
	}

	/** Registers a viewpoint on a tower's top deck or belfry floor. */
	public static void viewpoint(Draft d, int x, int y, int z, Direction look) {
		d.poi(PoiType.VIEWPOINT, x, y, z, look);
	}
}
