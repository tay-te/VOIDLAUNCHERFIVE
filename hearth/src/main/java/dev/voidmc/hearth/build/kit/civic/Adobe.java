package dev.voidmc.hearth.build.kit.civic;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The public buildings of the dry south (desert, mesa): flat roofs behind parapets with raised corners and a stepped
 * crest over the front, the ends of the roof beams in a tidy row under the wall head, wooden spouts; latticed wooden
 * screens projecting before the upper windows; striped cloth awnings; domes lifted on drums of lights; palms.
 */
public final class Adobe {
	private Adobe() {
	}

	/**
	 * A flat roof over walls {@code x0..x1 × z0..z1} whose top course is {@code plate}: a deck inside, a parapet coped
	 * with the wall's dressed stone, its corners raised a block (and, with {@code crest}, a stepped crest over the
	 * front's middle), wooden spouts on the sides and back, and the beam ends in a row under the wall head. Returns the
	 * deck's standing level.
	 */
	public static int roof(Draft d, int x0, int z0, int x1, int z1, int plate, boolean parapet, boolean crest) {
		Mat deck = d.mat(Role.ROOF);
		Mat wall = d.mat(Role.WALL);
		Mat coping = d.mat(Role.WALL_ACCENT).has(Form.SLAB) ? d.mat(Role.WALL_ACCENT) : d.mat(Role.ROOF_TRIM);
		// the dressings in the style's accents: a frieze of the accent tile along the wall head over the front (all round a
		// building of standing), the raised corners and the crest in the wall's dressed stone
		Mat frieze = d.mat(Role.ACCENT);
		Mat dressed = d.mat(Role.WALL_ACCENT);
		boolean allRound = d.tier() >= 3;
		int y = plate + 1;
		int cx = (x0 + x1) / 2;
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = z0 + 1; z < z1; z++) {
				for (int x = x0 + 1; x < x1; x++) {
					d.set(x, plate, z, deck.full());
				}
			}
			for (int[] c : Draft.ring(x0, z0, x1, z1)) {
				boolean corner = (c[0] == x0 || c[0] == x1) && (c[1] == z0 || c[1] == z1);
				boolean front = c[1] == z0;
				// the frieze: accent tiles alternating with the wall stone, a band of colour under the parapet
				boolean tile = (front || allRound) && !corner && ((c[0] + c[1]) & 1) == 0 && frieze.has(Form.FULL);
				d.set(c[0], plate, c[1], tile ? frieze.full() : corner ? dressed.full() : wall.full());
				if (!parapet || !d.allowed(c[0], y, c[1])) {
					continue;
				}
				int off = Math.abs(c[0] - cx);
				boolean crested = crest && front && off <= 2 && x1 - x0 >= 8;
				int h = corner ? 2 : crested ? (off <= 1 ? 2 + (off == 0 ? 1 : 0) : 2) : 1;
				for (int k = 0; k < h; k++) {
					if (d.allowed(c[0], y + k, c[1])) {
						d.set(c[0], y + k, c[1], corner || crested ? dressed.full() : wall.full());
					}
				}
				if (d.allowed(c[0], y + h, c[1])) {
					d.set(c[0], y + h, c[1], coping.slab(SlabType.BOTTOM));
				}
			}
			// spouts: a wooden trough out through the parapet's foot, two to a side
			if (parapet) {
				for (Side side : Side.of(x0, z0, x1, z1)) {
					if (side.out() == Dirs.FRONT) {
						continue;
					}
					for (int i : new int[]{side.length() / 3, side.length() - 1 - side.length() / 3}) {
						int ox = side.outX(i, 1);
						int oz = side.outZ(i, 1);
						if (i > 0 && i < side.last() && inPlot(d, ox, y, oz) && d.isEmpty(ox, y, oz)) {
							d.set(ox, y, oz, d.mat(Role.BEAM).stairsOrSlab(side.out(), Half.TOP));
						}
					}
				}
			}
		}
		vigas(d, x0, z0, x1, z1, plate - 1);
		return y;
	}

	/**
	 * The roof beams' ends through the wall head at row {@code y}, every other block along each side, only on sides
	 * where nearly the whole row can show (a broken row reads as blocks stuck on at random).
	 */
	public static void vigas(Draft d, int x0, int z0, int x1, int z1, int y) {
		Mat beam = d.mat(Role.BEAM);
		for (Side side : Side.of(x0, z0, x1, z1)) {
			int want = 0;
			int can = 0;
			for (int i = 2; i < side.length() - 2; i += 2) {
				want++;
				if (vigaFits(d, side, i, y)) {
					can++;
				}
			}
			if (want == 0 || can * 4 < want * 3) {
				continue;
			}
			try (var s = d.stage(StageKind.ROOF)) {
				for (int i = 2; i < side.length() - 2; i += 2) {
					if (vigaFits(d, side, i, y)) {
						d.set(side.outX(i, 1), y, side.outZ(i, 1), beam.strippedLog(side.out().getAxis()).orElse(beam.log(side.out().getAxis())));
					}
				}
			}
		}
	}

	private static boolean vigaFits(Draft d, Side side, int i, int y) {
		int ox = side.outX(i, 1);
		int oz = side.outZ(i, 1);
		return Openings.isWallBlock(d, side.x(i), y, side.z(i)) && inPlot(d, ox, y, oz) && d.isEmpty(ox, y, oz) && d.isEmpty(ox, y - 1, oz)
			&& d.isEmpty(ox, y + 1, oz);
	}

	private static boolean inPlot(Draft d, int x, int y, int z) {
		return x >= 0 && x < d.frontage() && z < d.depth() && (z >= 0 || z == -1 && y >= 3) && d.allowed(x, y, z);
	}

	/**
	 * A latticed wooden screen (a mashrabiya) projecting a block before the window in wall {@code s} at cell
	 * {@code i}, whose glass is at {@code y} and {@code y + 1}: a bracket under it, the lattice, a little sloping hood.
	 */
	public static boolean screen(Draft d, Side s, int i, int y) {
		int ox = s.outX(i, 1);
		int oz = s.outZ(i, 1);
		for (int yy = y - 1; yy <= y + 2; yy++) {
			// the window's own sill (a stair or slab under it) gives way to the screen's bracket
			BlockState there = d.get(ox, yy, oz);
			boolean sill = yy == y - 1 && there != null && (there.getBlock() instanceof net.minecraft.world.level.block.StairBlock
				|| there.getBlock() instanceof net.minecraft.world.level.block.SlabBlock);
			if (!inPlot(d, ox, yy, oz) || !d.isEmpty(ox, yy, oz) && !sill) {
				return false;
			}
		}
		Mat beam = d.mat(Role.BEAM);
		Mat shutter = d.mat(Role.SHUTTER);
		Direction out = s.out();
		try (var st = d.stage(StageKind.EXTERIOR)) {
			d.set(ox, y - 1, oz, beam.stairsOrSlab(out.getOpposite(), Half.TOP));
			d.set(ox, y, oz, shutter.trapdoor(out.getOpposite(), Half.BOTTOM, true));
			d.set(ox, y + 1, oz, shutter.trapdoor(out.getOpposite(), Half.BOTTOM, true));
			d.set(ox, y + 2, oz, beam.stairsOrSlab(out.getOpposite(), Half.BOTTOM));
		}
		return true;
	}

	/**
	 * Screens before the two-high windows of storey {@code k} (not the ground floor's, which would crowd the street),
	 * on every side but party walls.
	 */
	public static void screens(Draft d, Body b, int k) {
		int y = b.floorY(k) + 1;
		for (Side s : b.sides(k)) {
			for (int i = 1; i < s.last(); i++) {
				if (glass(d.get(s.x(i), y, s.z(i))) && glass(d.get(s.x(i), y + 1, s.z(i)))) {
					screen(d, s, i, y);
				}
			}
		}
	}

	private static boolean glass(BlockState s) {
		if (s == null || s.isAir()) {
			return false;
		}
		Block b = s.getBlock();
		return b instanceof IronBarsBlock && b != Blocks.IRON_BARS || b instanceof TransparentBlock;
	}

	/**
	 * A striped cloth awning over cells {@code i0..i1} of wall {@code s} at row {@code y}: slabs of the style's cloth
	 * in alternating colours a block out from the wall.
	 */
	public static void awning(Draft d, Side s, int i0, int i1, int y) {
		List<String> cloth = Signs.clothColours(d);
		DyeColor a = dye(cloth.get(0));
		DyeColor b = dye(cloth.size() > 1 ? cloth.get(1) : "white");
		try (var st = d.stage(StageKind.EXTERIOR)) {
			for (int i = Math.max(0, i0); i <= Math.min(s.last(), i1); i++) {
				int ox = s.outX(i, 1);
				int oz = s.outZ(i, 1);
				if (inPlot(d, ox, y, oz) && d.isEmpty(ox, y, oz)) {
					Block slab = Blocks.WOOL_SLAB.pick((i & 1) == 0 ? a : b);
					d.set(ox, y, oz, Paint.of(slab.defaultBlockState().trySetValue(net.minecraft.world.level.block.SlabBlock.TYPE, SlabType.TOP)));
				}
			}
		}
	}

	private static DyeColor dye(String name) {
		DyeColor c = DyeColor.byName(name, DyeColor.WHITE);
		return c == null ? DyeColor.WHITE : c;
	}

	/**
	 * A dome lifted on a drum: a square drum {@code 2r + 1} wide (r 1 or 2) rising two blocks off the deck with lights
	 * in its faces, then the dome of the roof's stone. Returns its top.
	 */
	public static int dome(Draft d, int cx, int cz, int deck, int r, int maxH) {
		// the dome in the style's accent where it cuts into stairs (glazed and coloured where the style says so), else the
		// dressed stone, else the roof's
		Fam fam = null;
		for (Role role : new Role[]{Role.ACCENT, Role.WALL_ACCENT, Role.ROOF}) {
			Fam f = Fam.of(d, role, Form.FULL, StageKind.ROOF);
			if (f != null && f.has(Form.STAIRS)) {
				fam = f;
				break;
			}
		}
		if (fam == null) {
			fam = Fam.of(d, Role.ROOF, Form.FULL, StageKind.ROOF);
		}
		if (fam == null) {
			return deck;
		}
		int size = 2 * r + 1;
		Towers.shaft(d, cx - r, cz - r, size, deck, deck + 1, Tradition.ADOBE, deck + 1);
		for (Side s : Side.of(cx - r, cz - r, cx + r, cz + r)) {
			int mid = s.middle();
			d.inStage(StageKind.OPENINGS, () -> d.set(s.x(mid), deck, s.z(mid), Blocks.GLASS.defaultBlockState()));
		}
		double[] radii = r >= 2 ? new double[]{3.1, 2.9, 2.5, 1.8, 0.6} : new double[]{2.1, 1.8, 1.2, 0.5};
		int top = Towers.lathe(d, cx, cz, deck + 2, radii, fam, maxH);
		return Math.max(top, Towers.finial(d, cx, top + 1, cz, maxH, false));
	}

	/** A date palm: a slender trunk leaning nowhere, a crown of fronds drooping round it. */
	public static void palm(Draft d, int x, int y, int z, int height) {
		Mat log = d.mat(Role.TREE_LOG);
		Mat leaves = d.mat(Role.TREE_LEAVES);
		int top = y + height;
		if (top + 1 > d.maxHeight() - 1) {
			return;
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int k = 0; k < height; k++) {
				d.set(x, y + k, z, log.log(Direction.Axis.Y).orElse(log.full()));
			}
			d.setIfAbsent(x, top, z, leaves.leaves());
			for (Direction dir : Dirs.HORIZONTAL) {
				for (int r = 1; r <= 2; r++) {
					int lx = x + Dirs.dx(dir) * r;
					int lz = z + Dirs.dz(dir) * r;
					int ly = r == 1 ? top : top - 1;
					if (lx >= 0 && lz >= 0 && lx < d.frontage() && lz < d.depth()) {
						d.setIfAbsent(lx, ly, lz, leaves.leaves());
					}
				}
				Direction side = Dirs.right(dir);
				int lx = x + Dirs.dx(dir) + Dirs.dx(side);
				int lz = z + Dirs.dz(dir) + Dirs.dz(side);
				if (lx >= 0 && lz >= 0 && lx < d.frontage() && lz < d.depth()) {
					d.setIfAbsent(lx, top - 1, lz, leaves.leaves());
				}
			}
		}
	}

	/** A window as {@link Openings#window}, but without window boxes or shutters in this manner (screens instead). */
	public static void window(Draft d, Side s, int col, int y, int width, int height, Openings.WindowStyle style) {
		if (is(d)) {
			Openings.window(d, s, col, y, width, height, style, new Openings.WindowOpts(false, false, style != Openings.WindowStyle.SLIT, true));
		} else {
			Openings.window(d, s, col, y, width, height, style);
		}
	}

	/** Whether to build in this manner. */
	public static boolean is(Draft d) {
		return Tradition.of(d) == Tradition.ADOBE;
	}
}
