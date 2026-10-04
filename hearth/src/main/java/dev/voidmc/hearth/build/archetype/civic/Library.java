package dev.voidmc.hearth.build.archetype.civic;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.BlockEntities;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.civic.Arches;
import dev.voidmc.hearth.build.kit.civic.Fam;
import dev.voidmc.hearth.build.kit.civic.Grounds;
import dev.voidmc.hearth.build.kit.civic.Halls;
import dev.voidmc.hearth.build.kit.civic.Portico;
import dev.voidmc.hearth.build.kit.civic.Sacred;
import dev.voidmc.hearth.build.kit.civic.Signs;
import dev.voidmc.hearth.build.kit.civic.Storeys;
import dev.voidmc.hearth.build.kit.civic.Towers;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:civic/library}: the town's library, one tall reading room under its roof. Bookshelves line the walls to
 * the height of a gallery that runs round three sides at mid-height, reached by a stair, with more shelves above it and
 * a railing along its edge; reading tables with chairs and lecterns stand on the floor under hanging lanterns; two tiers
 * of tall windows light it, and a glazed lantern on the ridge lets the sky in. A portico or a portal on steps fronts it.
 * The desert builds it square under a dome with a drum of lights, the karst as a scholar's pavilion of lattice under a
 * curved roof.
 *
 * <p>POIs: the SCHOLARs' JOB_SITEs and LECTERNs at the lecterns, SEATs and TABLEs, STORAGE, DOOR, a VIEWPOINT on the
 * gallery.
 *
 * <p>Params: {@code variant} (int).
 */
public final class Library implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("civic/library"), ArchetypeCategory.CIVIC, Set.of(JobType.SCHOLAR), 0,
		9, 13, 11, 15, 12, 20, SettlementTier.TOWN, Set.of(), Set.of(), List.of(), 1, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int tier = d.tier();
		int maxH = d.maxHeight();
		Habits h = d.habits();
		Tradition t = Tradition.of(d);
		boolean raised = t.raised();
		boolean portico = !raised && dp >= 13 && w >= 11;

		int x0 = w >= 11 ? 1 : 0;
		int x1 = w - 1 - x0;
		int cx = (x0 + x1) / 2;
		int zF = portico ? 3 : 2;
		int z0 = zF;
		int z1 = dp - 1;
		int hh = 9;
		int base = raised ? Grounds.stiltBase(d, x0, z0, x1, z1) : Grounds.raisedBase(d, x0, x1, z0);
		Body body = new Body(x0, z0, x1, z1, base, new int[]{hh}, false, false);
		d.body(x0, z0, x1, z1);
		int plate = body.plateY();
		d.eave(plate);
		int gy = base + 4;

		// --- shell: one tall storey, a string course at the gallery's floor
		Storeys.ground(d, body, raised);
		Grounds.forecourt(d, 0, w - 1, z0 - 1, !raised && !portico);
		boolean masonry = Halls.masonry(d);
		Facade fac = new Facade();
		int[] bays = Arches.piers(z0, z1, 3);
		for (int k = 1; k < bays.length - 1; k++) {
			fac.post(Dirs.LEFT, bays[k]);
			fac.post(Dirs.RIGHT, bays[k]);
		}
		int[] fb = Arches.piers(x0, x1, 3);
		for (int k = 1; k < fb.length - 1; k++) {
			fac.post(Dirs.FRONT, fb[k]);
			fac.post(Dirs.BACK, fb[k]);
		}
		Storeys.walls(d, body, fac, 0, 0, null, true);
		for (Side s : body.sides(0)) {
			Halls.stringCourse(d, s, gy - 1);
		}
		// two tiers of windows in every bay of the sides and the back; the front gets the door
		for (Side s : body.sides(0)) {
			int[] p = s.axis() == Direction.Axis.X ? fb : bays;
			for (int k = 0; k + 1 < p.length; k++) {
				int mid = (p[k] + p[k + 1]) / 2;
				if (s.out() == Dirs.FRONT && Math.abs(mid - cx) <= 1) {
					continue;
				}
				if (!s.contains(mid) || s.isCorner(s.index(mid))) {
					continue;
				}
				Openings.WindowStyle st = h.windows().lattice() ? Openings.WindowStyle.LATTICE : Openings.WindowStyle.PLAIN;
				Openings.window(d, s, mid, base + 1, 1, 2, st, new Openings.WindowOpts(false, false, true, true));
				if (h.windows().lattice()) {
					Openings.window(d, s, mid, gy + 1, 1, 3, st, new Openings.WindowOpts(false, false, false, true));
				} else {
					Sacred.lancet(d, s, mid, gy + 1, 1, 3, false);
				}
			}
		}
		Side front = body.side(0, Dirs.FRONT);
		int skyline = plate;
		if (portico) {
			skyline = Math.max(skyline, Portico.build(d, t, cx - 3, cx + 3, z0 - 2, z0 - 1, base, 4, t != Tradition.KARST && t != Tradition.ADOBE));
			Openings.door(d, front, cx, base, 1, new Openings.DoorOpts(false, false, true));
			if (base >= 1 && !raised) {
				Grounds.flight(d, cx - 3, cx + 3, z0 - 3, base, base);
			}
		} else {
			Halls.portal(d, front, cx, base, t, tier);
			if (base >= 1 && !raised) {
				Grounds.flight(d, cx - 1, cx + 1, z0 - 1, base, base);
			}
		}
		Sacred.rose(d, front, front.index(cx), gy + 2);
		Grounds.approach(d, cx, base, z0 - 1);

		// --- the roof: the culture's, with a glazed lantern on the ridge (a dome with a drum of lights in the desert)
		RoofForm form = t == Tradition.ADOBE ? RoofForm.FLAT : t == Tradition.KARST ? RoofForm.PAGODA
			: h.roof().pick(d.random(), RoofForm.GABLE, RoofForm.HIP, RoofForm.HALF_HIP);
		Storeys.Top top = Storeys.roof(d, body, form, Direction.Axis.Z, Math.max(1.0, h.roof().pitch()), 5);
		skyline = Math.max(skyline, top.ridge());
		int cz = (z0 + z1) / 2;
		if (form == RoofForm.FLAT) {
			Towers.shaft(d, cx - 2, cz - 2, 5, top.deck(), top.deck() + 1, t, -1);
			for (Side s : Side.of(cx - 2, cz - 2, cx + 2, cz + 2)) {
				int ry = top.deck() + 1;
				d.inStage(StageKind.OPENINGS, () -> d.set(s.x(2), ry, s.z(2), d.mat(Role.GLASS).pane()));
			}
			Fam dome = Fam.of(d, Role.ROOF, Form.FULL, StageKind.ROOF);
			if (dome != null) {
				skyline = Math.max(skyline, Towers.lathe(d, cx, cz, top.deck() + 2, new double[]{2.6, 2.3, 1.7, 0.9}, dome, maxH));
			}
		} else if (top.roof() != null) {
			skyline = Math.max(skyline, lantern(d, t, cx, cz, plate, top.roof(), maxH));
		}

		// --- the gallery round three sides (two wide: shelves and a walk), a rail of trapdoors on its edge, its stair
		int ix0 = x0 + 1;
		int ix1 = x1 - 1;
		int iz0 = z0 + 1;
		int iz1 = z1 - 1;
		Mat floor = d.mat(Role.FLOOR);
		Mat beam = d.mat(Role.BEAM);
		Mat shutter = d.mat(Role.SHUTTER);
		int sz0 = iz0 + 1;
		int gz0 = sz0 + 4;
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = gz0; z <= iz1; z++) {
				for (int x = ix0; x <= ix1; x++) {
					boolean gallery = x <= ix0 + 1 || x >= ix1 - 1 || z >= iz1 - 1;
					if (gallery) {
						d.set(x, gy - 1, z, floor.slab(SlabType.TOP));
					}
				}
			}
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int z = gz0 + 1; z < iz1 - 1; z++) {
				d.set(ix0 + 1, gy, z, shutter.trapdoor(Dirs.LEFT, Half.BOTTOM, true));
				d.set(ix1 - 1, gy, z, shutter.trapdoor(Dirs.RIGHT, Half.BOTTOM, true));
			}
			for (int x = ix0 + 2; x <= ix1 - 2; x++) {
				d.set(x, gy, iz1 - 1, shutter.trapdoor(Dirs.BACK, Half.BOTTOM, true));
			}
		}
		try (var s = d.stage(StageKind.FRAME)) {
			for (int z = gz0 + 1; z < iz1 - 1; z += 2) {
				d.set(ix0 + 1, gy - 2, z, beam.stairsOrSlab(Dirs.LEFT, Half.TOP));
				d.set(ix1 - 1, gy - 2, z, beam.stairsOrSlab(Dirs.RIGHT, Half.TOP));
			}
		}
		// the stair along the left wall from the front, climbing back to the gallery's walk
		int sx = ix0 + 1;
		Stairs.straight(d, sx, base, sz0, Dirs.BACK, 4, 1);
		d.poi(PoiType.VIEWPOINT, ix1 - 1, gy, iz1 - 2, Dirs.LEFT);

		// --- shelves on both levels between the windows, lecterns, tables
		for (int lvl = 0; lvl < 2; lvl++) {
			int y = lvl == 0 ? base : gy;
			for (Side s : Side.of(ix0, iz0, ix1, iz1)) {
				if (s.out() == Dirs.FRONT) {
					continue;
				}
				for (int i = 0; i < s.length(); i++) {
					int x = s.x(i);
					int z = s.z(i);
					int wx = x + Dirs.dx(s.out());
					int wz = z + Dirs.dz(s.out());
					if (!d.isEmpty(x, y, z) || !Openings.isWallBlock(d, wx, y + 1, wz)) {
						continue;
					}
					if (lvl == 0 && z < iz0 + 1 || lvl == 1 && (z < gz0 || x == sx && z < gz0 + 1)) {
						continue;
					}
					int fx = x;
					int fz = z;
					try (var st = d.stage(StageKind.INTERIOR)) {
						d.set(fx, y, fz, Blocks.BOOKSHELF.defaultBlockState());
						if (d.isEmpty(fx, y + 1, fz) && Openings.isWallBlock(d, wx, y + 2, wz)) {
							d.set(fx, y + 1, fz, Blocks.BOOKSHELF.defaultBlockState());
						}
						if (lvl == 0 && d.isEmpty(fx, y + 2, fz) && Openings.isWallBlock(d, wx, y + 2, wz) && y + 2 < gy - 1) {
							d.set(fx, y + 2, fz, Blocks.CHISELED_BOOKSHELF.defaultBlockState().trySetValue(
								net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, s.out().getOpposite()));
						}
					}
				}
			}
		}
		// the reading floor: the lecterns at the back (the scholars' places), tables with chairs down the middle
		int lz = iz1 - 1;
		int lecterns = 0;
		for (int x : new int[]{cx - 1, cx + 1, cx - 2, cx + 2, cx}) {
			if (lecterns < 2 && d.isEmpty(x, base, lz) && d.isEmpty(x, base, lz - 1) && d.isEmpty(x, base + 1, lz - 1)) {
				int fx = x;
				d.inStage(StageKind.INTERIOR, () -> d.set(fx, base, lz, States.facing(Blocks.LECTERN, Dirs.FRONT),
					BlockEntities.lectern("A History of the Town", "The Scholar", List.of("In the beginning there was a hearth."))));
				d.poi(PoiType.JOB_SITE, x, base, lz - 1, Dirs.BACK, JobType.SCHOLAR);
				d.poi(PoiType.LECTERN, x, base, lz - 1, Dirs.BACK);
				lecterns++;
			}
		}
		int rx0 = ix0 + 2;
		int rx1 = ix1 - 2;
		for (int z = iz0 + 2; z <= lz - 3; z += 3) {
			for (int x = rx0 + (rx1 - rx0) % 2; x <= rx1; x += Math.max(2, rx1 - rx0)) {
				if (d.isEmpty(x, base, z)) {
					Furniture.table(d, x, base, z);
					for (Direction dir : new Direction[]{Dirs.FRONT, Dirs.BACK}) {
						int cx2 = x + Dirs.dx(dir);
						int cz2 = z + Dirs.dz(dir);
						if (d.isEmpty(cx2, base, cz2)) {
							Furniture.chair(d, cx2, base, cz2, dir);
						}
					}
				}
			}
		}
		Furniture.chest(d, ix1, gy, iz1, Dirs.LEFT, List.of(new Object[]{"minecraft:paper", 32}, new Object[]{"minecraft:book", 8},
			new Object[]{"minecraft:ink_sac", 4}));
		for (int z = iz0 + 2; z <= iz1 - 2; z += 3) {
			Halls.lanternUnder(d, cx, gy, z, 3);
		}
		// a globe of the world in a banner on the back wall over the lecterns
		int by = gy - 2;
		if (d.isEmpty(cx, by, iz1) && Openings.isWallBlock(d, cx, by, z1)) {
			Signs.wallBanner(d, cx, by, iz1, Dirs.FRONT, "light_blue", List.of(new String[]{"globe", "green"}, new String[]{"border", "brown"}));
		}
		if (!raised) {
			Halls.lamp(d, Math.max(0, cx - 4), 0, 0, 2);
			Halls.lamp(d, Math.min(w - 1, cx + 4), 0, 0, 2);
		}
		if (h.snow()) {
			Garden.snowBanks(d, x0, z0, x1, z1, base, cx);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, skyline + 2);
	}

	/** A glazed lantern on the ridge: a shaft of three through the roof, glass all round, a little cap. Returns its top. */
	static int lantern(Draft d, Tradition t, int cx, int cz, int plate, dev.voidmc.hearth.build.kit.Roofs.Roof roof, int maxH) {
		int ridge = roof.ridgeY;
		int x0 = cx - 1;
		int z0 = cz - 1;
		if (ridge + 4 > maxH - 1) {
			return ridge;
		}
		Towers.shaft(d, x0, z0, 3, plate + 1, ridge + 2, t, -1);
		for (Side s : Side.of(x0, z0, x0 + 2, z0 + 2)) {
			for (int y = ridge; y <= ridge + 1; y++) {
				int yy = y;
				d.inStage(StageKind.OPENINGS, () -> d.set(s.x(1), yy, s.z(1), d.mat(Role.GLASS).pane()));
			}
		}
		d.carve(x0 + 1, plate + 1, z0 + 1, x0 + 1, ridge + 1, z0 + 1);
		return Towers.cap(d, x0, z0, 3, ridge + 2, Towers.cap(t, false) == Towers.Cap.SPIRE ? Towers.Cap.PYRAMID : Towers.cap(t, false), maxH);
	}
}
