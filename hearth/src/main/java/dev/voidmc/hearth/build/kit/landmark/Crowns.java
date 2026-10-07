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
import java.util.List;
import java.util.Set;
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

	/**
	 * The materials a crown is made of; {@code sheet}, when not null, re-clads needles, domes and onions (copper over a
	 * town of shingle roofs, or by choice).
	 */
	public record Mats(Mat roof, Mat trim, Stone dress, Mat frame, Stone sheet) {
		public static Mats of(Draft d, Stone dress) {
			return new Mats(d.mat(Role.ROOF), d.mat(Role.ROOF_TRIM), dress, d.mat(Role.FRAME), null);
		}

		/**
		 * With spires and domes in the town's sheet (its roofing, or dark slate over timber roofs), copper on a share of
		 * them only where copper belongs to the culture's skyline: the Hanseatic coast and the Tyrolean mountains.
		 */
		public static Mats coppered(Draft d, Stone dress, double chance) {
			Stone sheet = Stone.sheet(d);
			boolean wood = !Stone.stony(d, Role.ROOF) && !d.habits().roof().thatch();
			dev.voidmc.hearth.api.Culture c = d.style().culture();
			boolean copperLand = c == null || c == dev.voidmc.hearth.api.Culture.MARITIME || c == dev.voidmc.hearth.api.Culture.ALPINE;
			if (!wood && copperLand && d.chance(chance)) {
				sheet = Stone.copper(d);
			}
			return new Mats(d.mat(Role.ROOF), d.mat(Role.ROOF_TRIM), dress, d.mat(Role.FRAME), sheet);
		}
	}

	/**
	 * Draws a crown over the square {@code [x0..x1] × [z0..z1]} from row y; nothing above {@code top} (inclusive).
	 * Returns the highest row used.
	 */
	public static int crown(Draft d, Kind kind, int x0, int z0, int x1, int z1, int y, int top, Mats m) {
		try (var s = d.stage(StageKind.ROOF)) {
			int peak = switch (kind) {
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
			// the hollow inside a spire, a dome or a cap, sealed off over the deck: a rubble core, not a dark void
			if (kind != Kind.PARAPET) {
				dev.voidmc.hearth.build.kit.refine.Voids.seal(d, x0 - 2, y, z0 - 2, x1 + 2, peak, z1 + 2, Stone.footing(d).full());
			}
			return peak;
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
			case SPIRE, OCTAGON -> Math.max(11, (int) Math.round(height * 0.4));
			case BROACH -> Math.max(10, (int) Math.round(height * 0.38));
			case STAVE -> Math.max(9, (int) Math.round(height * 0.3));
			case ONION -> Math.max(11, Math.min(17, (int) Math.round(height * 0.33)));
			case PYRAMID -> (int) Math.round((r + 1) * 0.75) + 3;
			case PAGODA -> (int) Math.round(r + 2) + 3;
			case DOME -> (r >= 3 ? 3 : 2) + (int) Math.ceil(r * 1.35) + 4;
			case CUPOLA -> (size >= 7 ? 5 : 4) + (r >= 3 ? 3 : 2) + (int) Math.ceil(r * 1.35) + 4;
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
		// an even square: the needle stands on the odd square inside it (half a block off the axis reads as true)
		if (((x1 - x0) & 1) == 1) {
			x1--;
			z1--;
		}
		double r = (x1 - x0) / 2.0;
		double cx = (x0 + x1) / 2.0;
		double cz = (z0 + z1) / 2.0;
		Stone skin = skin(d, m);
		Paint post = skin.has(Form.WALL) ? skin.wall() : m.dress.has(Form.WALL) ? m.dress.wall() : Paint.of(Details.rod());
		int y0 = y;
		double rr = r;
		if (!parapet && r >= 2) {
			// a small tower: the needle rises from an octagon on the cornice, pinnacles standing in the four corners it leaves
			for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
				top0 = Math.max(top0, Details.pinnacle(d, c[0], y, c[1], octagonal ? 1 : 2, m.dress, !octagonal));
			}
		}
		int rows = Math.max(6, top - y0);
		List<Set<Long>> ladder = Shapes.ladder(cx, cz, rr);
		if (!parapet && r >= 2 && ladder.size() > 1) {
			// start the spire's outline at the octagon so the corners stay free for the pinnacles
			int peak = Shapes.needle(d, cx, cz, rr, y0, rows, skin.full(), skin, post, 1);
			bands(d, cx, cz, rr, y0, rows, m, octagonal);
			return Math.max(top0, peak);
		}
		int peak = Shapes.needle(d, cx, cz, rr, y0, rows, skin.full(), skin, post, 0);
		bands(d, cx, cz, rr, y0, rows, m, octagonal);
		// lucarnes: a little gabled light at the foot of the four cardinal faces of a broad spire
		if (r >= 2.5) {
			lucarnes(d, (int) Math.round(cx), (int) Math.round(cz), x0, z0, x1, z1, y0, m, skin);
		}
		return Math.max(top0, peak);
	}

	/** The covering of a spire, dome or onion: the sheet where the town clads them (copper), else the roof. */
	static Stone skin(Draft d, Mats m) {
		return m.sheet != null ? m.sheet : Stone.of(d, Role.ROOF);
	}

	/**
	 * What an onion or a helm is sheeted in, one material laid crisp (a mottled sheet reads as moss or a tree crown): in
	 * a Bohemian town dark slate, copper only on the odd baroque onion; in the mountains copper on half of them, else the
	 * roof's dark shingles; elsewhere the sheet asked for (copper unmixed), else the roof where it cuts stairs, else slate.
	 */
	public static Stone bulbSkin(Draft d, @org.jspecify.annotations.Nullable Stone sheet) {
		dev.voidmc.hearth.api.Culture c = d.style().culture();
		// blackened sheet: polished deepslate, smooth (the cracked tiles' texture on a small curved form reads as a heap)
		Stone slate = Stone.fixed(d, StageKind.ROOF, "minecraft:polished_deepslate");
		Stone copper = Stone.copper(d).crisp();
		long h = dev.voidmc.hearth.build.draft.Noise.hash(d.seed(), 0x0410);
		double roll = (h >>> 11) * 0x1.0p-53;
		if (c == dev.voidmc.hearth.api.Culture.TEMPERATE || c == dev.voidmc.hearth.api.Culture.WISTERIA) {
			return roll < 0.12 && d.tier() >= 4 ? copper : slate;
		}
		Stone roof = Stone.of(d, Role.ROOF);
		boolean roofCuts = roof.has(Form.STAIRS) && !d.habits().roof().thatch();
		if (c == dev.voidmc.hearth.api.Culture.ALPINE) {
			return roll < 0.5 || !roofCuts ? copper : roof.crisp();
		}
		if (sheet != null) {
			return coppery(sheet) ? copper : sheet.crisp();
		}
		return roofCuts ? roof.crisp() : slate;
	}

	/** Whether a sheet is copper. */
	static boolean coppery(Stone s) {
		Block b = s.main(Form.FULL);
		return b != null && net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath().contains("copper");
	}

	/**
	 * The lantern on the point of an onion or a helm at (x, y, z): a neck of wall post, a little bulb of the sheet, a
	 * gilded ball and the rod, as many as fit under {@code top}. Returns the highest row drawn.
	 */
	public static int lantern(Draft d, int x, int y, int z, int top, Stone sheet, Stone dress) {
		if (y + 3 <= top) {
			d.set(x, y, z, sheet.has(Form.WALL) ? sheet.wall() : dress.wallOrFull());
			d.set(x, y + 1, z, sheet.full());
			return Details.finial(d, x, y + 2, z, true);
		}
		if (y + 1 <= top) {
			return Details.finial(d, x, y, z, y + 1 <= top);
		}
		return y - 1;
	}

	/**
	 * A helm on a small square (three to seven wide) from row y: a bell-cast cap of the sheet ({@link Shapes#helmRows})
	 * with its lantern and gilded ball. Returns the highest row used.
	 */
	public static int helm(Draft d, double cx, double cz, double r, int y, int top, Stone sheet, Stone dress) {
		List<Set<Long>> rows = Shapes.helmRows(cx, cz, r);
		while (rows.size() > 2 && y + rows.size() + 1 > top) {
			rows.remove(1);
		}
		int yt;
		try (var s = d.stage(StageKind.ROOF)) {
			yt = Shapes.stack(d, cx, cz, y, rows, sheet.full(), sheet);
			Set<Long> last = rows.get(rows.size() - 1);
			long c = last.iterator().next();
			if (last.size() == 1) {
				return Math.max(yt, lantern(d, (int) (c >> 32), yt + 1, (int) c, top, sheet, dress));
			}
		}
		return yt;
	}

	/**
	 * A band round a broad octagonal needle a third of the way up, in the dressing (or the trim): the course a mason lays
	 * where a spire's ribs are tied, which reads from far away as a ring of light on a dark slope.
	 */
	static void bands(Draft d, double cx, double cz, double r, int y0, int rows, Mats m, boolean octagonal) {
		if (!octagonal || r < 2) {
			return;
		}
		int yb = y0 + Math.max(2, rows / 3);
		for (int x = (int) Math.floor(cx - r - 1); x <= (int) Math.ceil(cx + r + 1); x++) {
			for (int z = (int) Math.floor(cz - r - 1); z <= (int) Math.ceil(cz + r + 1); z++) {
				Draft.Cell c = d.cell(x, yb, z);
				if (c == null || c.state().isAir() || c.stage() != StageKind.ROOF || !c.state().isCollisionShapeFullBlock(
					net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO)) {
					continue;
				}
				// a face cell: open to the air on some side
				boolean face = false;
				for (Direction dir : Dirs.HORIZONTAL) {
					if (d.isEmpty(x + Dirs.dx(dir), yb, z + Dirs.dz(dir))) {
						face = true;
						break;
					}
				}
				if (face) {
					d.set(x, yb, z, m.dress.full());
				}
			}
		}
	}

	/**
	 * Little gabled lights at the foot of a spire's four cardinal faces: on the axis through each face, the outermost
	 * cell of the needle two rows up becomes a dark louvre under a little gable of the skin's stairs.
	 */
	static void lucarnes(Draft d, int cx, int cz, int x0, int z0, int x1, int z1, int y, Mats m, Stone skin) {
		int ly = y + 2;
		for (Direction out : Dirs.HORIZONTAL) {
			int dx = Dirs.dx(out);
			int dz = Dirs.dz(out);
			int x = cx;
			int z = cz;
			// walk out along the axis to the face
			while (d.isSolid(x + dx, ly, z + dz) && Math.abs(x + dx - cx) + Math.abs(z + dz - cz) < 12) {
				x += dx;
				z += dz;
			}
			if (x == cx && z == cz || !d.isSolid(x, ly + 1, z)) {
				continue;
			}
			Direction along = Dirs.right(out);
			d.set(x, ly, z, m.frame.trapdoor(out, Half.BOTTOM, true));
			if (skin.has(Form.STAIRS)) {
				d.set(x + dx, ly + 1, z + dz, skin.stairs(out.getOpposite(), Half.BOTTOM));
				d.set(x + dx, ly, z + dz, skin.stairs(out.getOpposite(), Half.TOP));
				for (int k : new int[]{-1, 1}) {
					int sx = x + dx + Dirs.dx(along) * k;
					int sz = z + dz + Dirs.dz(along) * k;
					if (d.allowed(sx, ly, sz) && d.isEmpty(sx, ly, sz) && d.isSolid(sx - dx, ly, sz - dz)) {
						d.set(sx, ly, sz, skin.stairs(k < 0 ? along : along.getOpposite(), Half.BOTTOM));
					}
				}
			}
		}
	}

	/** An octagonal spire on broaches straight off the walls (no parapet). */
	static int broach(Draft d, int x0, int z0, int x1, int z1, int y, int top, Mats m) {
		if (((x1 - x0) & 1) == 1) {
			x1--;
			z1--;
		}
		double r = (x1 - x0) / 2.0;
		double cx = (x0 + x1) / 2.0;
		double cz = (z0 + z1) / 2.0;
		Stone skin = skin(d, m);
		Paint post = skin.has(Form.WALL) ? skin.wall() : m.dress.has(Form.WALL) ? m.dress.wall() : Paint.of(Details.rod());
		// straight off the walls: the square's corners carried up on broaches into the octagon, then the needle
		int peak = Shapes.needle(d, cx, cz, r, y, Math.max(6, top - y), skin.full(), skin, post, 0);
		if (r >= 2.5) {
			lucarnes(d, (int) Math.round(cx), (int) Math.round(cz), x0, z0, x1, z1, y, m, skin);
		}
		return peak;
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
		Stone sheet = bulbSkin(d, m.sheet != null ? m.sheet : Stone.sheet(d));
		if (r <= 3.5 && r >= 1.5) {
			return smallOnion(d, cx, cz, Shapes.snap(cx, r), y, top, m, sheet);
		}
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

	/**
	 * An onion on a small tower (five to eight wide): a drum of the dressing with lights on its faces, then the bulb laid
	 * outline by outline ({@link Shapes#onionRows}), a gilded ball and a rod.
	 */
	static int smallOnion(Draft d, double cx, double cz, double r, int y, int top, Mats m, Stone sheet) {
		double dr = r - 1;
		Set<Long> drum = Shapes.oct(cx, cz, dr, dr >= 2 ? 1 : 0);
		int drumH = 2;
		for (int k = 0; k < drumH; k++) {
			for (long c : drum) {
				int x = (int) (c >> 32);
				int z = (int) c;
				if (!onRim(drum, x, z)) {
					continue;
				}
				boolean light = k == 0 && (Math.abs(x - cx) < 0.6 || Math.abs(z - cz) < 0.6);
				d.set(x, y + k, z, light ? Paint.of(Blocks.GLASS_PANE.defaultBlockState()) : m.dress.full());
			}
		}
		List<Set<Long>> rows = Shapes.onionRows(cx, cz, r);
		int room = top - (y + drumH) - 3;
		while (rows.size() > room && rows.size() > 4) {
			// short of height: the point a cell shorter first, then a held row of the bulb
			int n = rows.size();
			rows.remove(rows.get(n - 1).equals(rows.get(n - 2)) ? n - 1 : n > 6 ? 3 : n - 1);
		}
		int yt = Shapes.stack(d, cx, cz, y + drumH, rows, sheet.full(), sheet);
		Set<Long> last = rows.get(rows.size() - 1);
		long c = last.iterator().next();
		int fx = (int) (c >> 32);
		int fz = (int) c;
		if (last.size() == 1) {
			return Math.max(yt, lantern(d, fx, yt + 1, fz, top, sheet, m.dress));
		}
		return Math.max(yt, Details.finial(d, fx, yt + 1, fz, false));
	}

	/** A dome on a small square (five to eight wide), laid outline by outline ({@link Shapes#domeRows}). */
	static int smallDome(Draft d, double cx, double cz, double r, int y, int top, Mats m, Stone sheet) {
		List<Set<Long>> rows = Shapes.domeRows(cx, cz, r);
		while (rows.size() > top - y - 2 && rows.size() > 2) {
			rows.remove(1);
		}
		int yt = Shapes.stack(d, cx, cz, y, rows, sheet.full(), sheet);
		Set<Long> last = rows.get(rows.size() - 1);
		long c = last.iterator().next();
		int fx = (int) (c >> 32);
		int fz = (int) c;
		if (last.size() == 1 && yt + 3 <= top) {
			// a little lantern: a post, a cap, the gilded ball
			d.set(fx, yt + 1, fz, m.dress.wallOrFull());
			d.set(fx, yt + 2, fz, m.dress.has(Form.SLAB) ? m.dress.slab(SlabType.BOTTOM) : m.dress.full());
			return Math.max(yt + 2, Details.finial(d, fx, yt + 3, fz, true));
		}
		return Math.max(yt, Details.finial(d, fx, yt + 1, fz, false));
	}

	private static boolean onRim(Set<Long> s, int x, int z) {
		for (Direction dir : Dirs.HORIZONTAL) {
			if (!s.contains(Shapes.cell(x + Dirs.dx(dir), z + Dirs.dz(dir)))) {
				return true;
			}
		}
		return false;
	}

	/** A drum with lights, a dome over it, a lantern on top. */
	static int dome(Draft d, int x0, int z0, int x1, int z1, int y, int top, Mats m) {
		double cx = (x0 + x1) / 2.0;
		double cz = (z0 + z1) / 2.0;
		double r = (x1 - x0) / 2.0 - 0.5;
		Stone sheet = m.sheet != null ? m.sheet : Stone.sheet(d);
		int drumH = r >= 3 ? 3 : 2;
		for (int k = 0; k < drumH; k++) {
			for (int[] c : Shapes.ring(cx, cz, r)) {
				boolean light = k >= 1 && k < drumH && (Math.abs(c[0] - cx) < 0.6 || Math.abs(c[1] - cz) < 0.6) && drumH >= 3;
				d.set(c[0], y + k, c[1], light ? Paint.of(Blocks.GLASS_PANE.defaultBlockState()) : m.dress.full());
			}
		}
		Details.ringCourse(d, cx, cz, r, y + drumH - 1, m.dress);
		int yb = y + drumH;
		if (r <= 3.6) {
			return smallDome(d, cx, cz, Shapes.snap(cx, r), yb, top, m, sheet);
		}
		// a little pointed, as the great domes are: taller than a hemisphere, the profile drawn in toward the lantern
		int h = (int) Math.ceil(r * 1.35) + 1;
		h = Math.min(h, top - yb - 2);
		double[] radii = new double[Math.max(2, h)];
		for (int i = 0; i < radii.length; i++) {
			double t = (double) i / radii.length;
			radii[i] = Math.max(0.45, (r + 0.2) * Math.pow(Math.max(0, 1 - t * t), 0.62) - 0.15);
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

	/**
	 * An open cupola: posts at the corners (and mid-sides on a wide square) carrying an entablature over open arches, a
	 * bell hung inside, and a dome on its own drum over the whole square.
	 */
	static int cupola(Draft d, int x0, int z0, int x1, int z1, int y, int top, Mats m) {
		int size = x1 - x0 + 1;
		int postH = size >= 7 ? 4 : 3;
		int xm = (x0 + x1) / 2;
		int zm = (z0 + z1) / 2;
		for (int[] c : Draft.ring(x0, z0, x1, z1)) {
			boolean corner = isCorner(c, x0, z0, x1, z1);
			boolean mid = size >= 9 && (c[0] == xm || c[1] == zm);
			if (corner || mid) {
				for (int k = 0; k < postH; k++) {
					d.set(c[0], y + k, c[1], m.dress.full());
				}
			} else {
				d.set(c[0], y, c[1], m.dress.wallOrFull());
			}
		}
		int ye = y + postH;
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				d.set(x, ye, z, m.dress.full());
			}
		}
		// the arches' shoulders under the entablature, beside each post
		if (m.dress.has(Form.STAIRS)) {
			for (Side sd : Side.of(x0, z0, x1, z1)) {
				for (int i = 1; i < sd.last(); i++) {
					boolean postLeft = i - 1 == 0 || size >= 9 && sd.column(i - 1) == (sd.axis() == Direction.Axis.X ? xm : zm);
					boolean postRight = i + 1 == sd.last() || size >= 9 && sd.column(i + 1) == (sd.axis() == Direction.Axis.X ? xm : zm);
					if (postLeft) {
						d.set(sd.x(i), ye - 1, sd.z(i), m.dress.stairs(sd.along().getOpposite(), Half.TOP));
					} else if (postRight) {
						d.set(sd.x(i), ye - 1, sd.z(i), m.dress.stairs(sd.along(), Half.TOP));
					}
				}
			}
		}
		Details.bell(d, xm, ye - 1, zm, Dirs.FRONT);
		int peak = size >= 5 ? dome(d, x0, z0, x1, z1, ye + 1, top, m) : pyramid(d, x0, z0, x1, z1, ye + 1, top, m, 1, 1.0);
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
