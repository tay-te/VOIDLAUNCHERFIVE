package dev.voidmc.hearth.build.archetype.deep;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.BlockEntities;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.deep.DeepManner;
import dev.voidmc.hearth.build.kit.deep.DeepSite;
import dev.voidmc.hearth.build.kit.deep.Glow;
import dev.voidmc.hearth.build.kit.deep.Rock;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:deep/hall}: the moot hall of the deep town, where its reeve sits (LIVING_WORLD.md §8.2), cut into the
 * realm's wall or standing on the deep square. A basilica of the realm's stone: a tall nave under a pitched roof on
 * tie beams, lower aisles under lean-to roofs, an arcade of dressed piers and arches between them, a clerestory of
 * windows over the aisle roofs and buttresses down the sides; at the front two towers (a stair to a lookout in the
 * broader halls) and between them a frontispiece standing proud — a deep arched porch, a carved tympanum with the
 * realm's glow at its heart, a rose window in a gable of the culture's own finish. Inside: a runner from the doors to the
 * dais at the far end, the reeve's high table and chair and his lectern on it, long tables and benches down the nave, a
 * great hearth in an aisle, chests of the town's records, wheels of lanterns from the tie beams, sconces on the piers.
 * The roof follows the culture: steep (Bohemia, the north), flared (the karst), hipped (the south and the tropics),
 * domed over a flat-roofed nave (the desert and the steppe), flat behind parapets (the mesa).
 *
 * <p>Params: {@code realm}, {@code ceiling_y} (the hall keeps one clear of the vault; its towers stand to the top of the
 * plot), {@code character}, {@code local} ({@link DeepSite}); {@code variant}.
 */
public final class DeepHall implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("deep/hall"), ArchetypeCategory.UNDERGROUND, Set.of(JobType.REEVE), 0,
		15, 23, 17, 27, 12, 20, SettlementTier.VILLAGE, Set.of(), Set.of(), List.of(), 1, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public TerrainProbe terrain(PlotSpec spec, TerrainProbe probe) {
		return DeepSite.probeFor(spec, probe);
	}

	/** The hall's plan. */
	private static final class Plan {
		int w;
		int dp;
		int h;
		/** Outer walls, column lines, nave. */
		int bx0;
		int bx1;
		int xa;
		int xb;
		int c;
		int zf;
		int zb;
		int t;
		int ha;
		int hn;
		int towerTop;
		int zc0;
		int[] cols;
		boolean stairTower;
		String roof;
		double pitch;
		/** The towers' caps: a dark helm (Bohemia), a copper onion (the Alps), crenels on squat towers (the moors, the coasts). */
		String cap;
	}

	@Override
	public void draw(Draft d) {
		int top = form(d);
		// whatever the plan: the hill cut back round it, a kerb wherever its ground ends at a fall
		HallParts.cut(d, top);
		HallParts.kerb(d, 3);
	}

	/** Draws the culture's hall; returns the highest level its cut reaches. */
	private int form(Draft d) {
		DeepSite ds = DeepSite.of(d);
		Glow g = Glow.of(ds.character());
		DeepManner manner = DeepManner.of(d);
		int h = Math.max(11, Math.min(d.maxHeight(), ds.top() + 1));
		switch (manner.hallForm()) {
			case "stave", "longhouse" -> {
				HallStave.draw(d, g, manner, h);
				return h;
			}
			case "pavilion" -> {
				HallPavilion.draw(d, g, h);
				return h;
			}
			case "pueblo" -> {
				HallPueblo.draw(d, g, h);
				return h;
			}
			case "loggia" -> {
				HallLoggia.draw(d, g, h);
				return h;
			}
			case "stilt" -> {
				HallStilt.draw(d, g, h);
				return h;
			}
			case "court", "sahel" -> {
				HallCourt.draw(d, g, h, manner.hallForm().equals("sahel"));
				return h;
			}
			default -> {
			}
		}
		Plan p = plan(d, ds, manner);
		d.body(p.bx0, p.zf, p.bx1, p.zb);

		ground(d, p);
		shell(d, p);
		arcade(d, p);
		Roofs.Roof nave = roofs(d, p, manner);
		towers(d, p, manner, g);
		front(d, p, g, manner, nave);
		sides(d, p);
		interior(d, p, g);
		apron(d, p, g);
		Groundwork.clear(d, 0, 0, p.w - 1, p.dp - 1, Math.min(d.maxHeight() - 1, p.towerTop + 2));
		return p.towerTop + 2;
	}

	private static Plan plan(Draft d, DeepSite ds, DeepManner manner) {
		Plan p = new Plan();
		p.w = d.frontage();
		p.dp = d.depth();
		p.h = Math.max(11, Math.min(d.maxHeight(), ds.top() + 1));
		p.bx0 = 1;
		p.bx1 = p.w - 2;
		int a = p.w >= 19 ? 3 : 2;
		p.xa = p.bx0 + a + 1;
		p.xb = p.bx1 - a - 1;
		p.c = p.w / 2;
		p.t = p.w >= 19 ? 5 : 3;
		p.zf = 2;
		p.zb = p.dp - 1;
		p.stairTower = p.t == 5;
		p.roof = manner.hallRoof();
		p.pitch = p.roof.equals("steep") ? 1.0 : p.roof.equals("flat") || p.roof.equals("dome") ? 0 : 0.75;
		p.ha = p.h >= 17 ? 6 : 5;
		int half = (p.xb - p.xa) / 2 + 1;
		int rise = (int) Math.ceil(half * p.pitch) + 1;
		p.hn = Math.clamp(p.h - 6 - rise, p.ha + 3, p.ha + 5);
		if (p.hn + rise > p.h - 5 && p.pitch > 0.5) {
			p.pitch = 0.5;
			rise = (int) Math.ceil(half * p.pitch) + 1;
			p.hn = Math.clamp(p.h - 6 - rise, p.ha + 3, p.ha + 5);
		}
		p.cap = switch (manner.culture()) {
			case TEMPERATE -> "helm";
			case ALPINE -> "onion";
			case MOORLAND, MARITIME -> "crenel";
			default -> "spire";
		};
		// squat towers stand a little over the nave's eaves; the rest to the top of the plot
		p.towerTop = p.cap.equals("crenel") ? Math.min(p.h - 1, Math.max(p.hn + 4, 11)) : p.h - 1;
		// the arcade's piers: every third row from behind the towers to before the dais
		p.zc0 = p.t + 1;
		int n = Math.max(2, (p.zb - 2 - p.zc0) / 3 + 1);
		p.cols = new int[n];
		for (int i = 0; i < n; i++) {
			p.cols[i] = p.zc0 + 3 * i;
		}
		return p;
	}

	// ------------------------------------------------------------------------------------------------- the shell

	private static void ground(Draft d, Plan p) {
		Groundwork.level(d, 0, 0, p.w - 1, 1, 0, true);
		Groundwork.footings(d, 0, 1, p.w - 1, p.zb, -1);
		Mat paving = d.mat(Role.PAVING);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 0; z < p.zf; z++) {
				for (int x = 0; x < p.w; x++) {
					d.set(x, -1, z, paving.full());
				}
			}
		}
	}

	/** Outer walls (a plinth, a rough base course, dressed stone above), the floor, the aisles' and nave's air. */
	private static void shell(Draft d, Plan p) {
		Mat wall = Rock.dressing(d);
		Mat dark = d.mat(Role.WALL);
		Mat plinth = d.mat(Role.PLINTH);
		Mat paving = d.mat(Role.PAVING);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = p.zf; z <= p.zb; z++) {
				for (int x = p.bx0; x <= p.bx1; x++) {
					boolean edge = x == p.bx0 || x == p.bx1 || z == p.zb || z == p.zf && x > p.bx0 + p.t - 1 && x < p.bx1 - p.t + 1;
					if (!edge) {
						continue;
					}
					int top = x == p.bx0 || x == p.bx1 ? p.ha - 1 : p.hn;
					if (z == p.zb && x > p.xa - 1 && x < p.xb + 1) {
						top = p.hn;
					} else if (z == p.zb) {
						top = p.ha - 1;
					}
					boolean corner = (x == p.bx0 || x == p.bx1) && z == p.zb;
					for (int y = -1; y <= top; y++) {
						boolean band = y == 0 || y == p.ha - 1 || corner && (y & 1) == 1;
						d.set(x, y, z, y == -1 ? plinth.full() : band ? dark.full() : wall.full());
					}
				}
			}
		}
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = p.zf + 1; z < p.zb; z++) {
				for (int x = p.bx0 + 1; x < p.bx1; x++) {
					d.set(x, -1, z, paving.full());
				}
			}
		}
		d.carve(p.bx0 + 1, 0, p.zf + 1, p.bx1 - 1, p.ha - 1, p.zb - 1);
		d.carve(p.xa, 0, p.zf + 1, p.xb, p.hn - 1, p.zb - 1);
	}

	/**
	 * The arcade: dressed piers on the column lines with plinths and capitals, round arches between them, the
	 * clerestory wall above with a window over each arch; tie beams across the nave at each pier.
	 */
	private static void arcade(Draft d, Plan p) {
		Mat dress = d.mat(Role.PLINTH).has(Form.STAIRS) ? d.mat(Role.PLINTH) : d.mat(Role.WALL);
		Mat plinth = d.mat(Role.PLINTH);
		Mat wall = Rock.dressing(d);
		Mat beam = d.mat(Role.BEAM);
		Mat glass = d.mat(Role.GLASS);
		int last = p.cols[p.cols.length - 1];
		for (int x : new int[]{p.xa, p.xb}) {
			try (var s = d.stage(StageKind.WALLS)) {
				for (int z = p.zf + 1; z < p.zb; z++) {
					boolean pier = false;
					for (int cz : p.cols) {
						pier |= cz == z;
					}
					if (z < p.zc0) {
						continue;
					}
					if (z > last) {
						// past the last pier the line is solid to the back wall (the dais's walls)
						for (int y = -1; y <= p.hn; y++) {
							d.set(x, y, z, y == -1 ? plinth.full() : wall.full());
						}
						continue;
					}
					if (pier) {
						for (int y = -1; y <= p.ha - 3; y++) {
							d.set(x, y, z, y == -1 ? plinth.full().at(x, y, z) : y == p.ha - 3 ? Rock.carved(d).defaultBlockState() : dress.full().at(x, y, z));
						}
					} else {
						// the arch over the gap: shoulders turned to the piers either side
						boolean afterPier = false;
						for (int cz : p.cols) {
							afterPier |= cz == z - 1;
						}
						d.set(x, p.ha - 2, z, dress.stairsOrSlab(afterPier ? Dirs.FRONT : Dirs.BACK, Half.TOP));
					}
					for (int y = pier ? p.ha - 2 : p.ha - 1; y <= p.hn; y++) {
						d.set(x, y, z, y == p.ha - 1 ? dress.full() : wall.full());
					}
				}
			}
			// a clerestory window over each arch, above the aisle roof
			int wy0 = p.ha + (p.xa - p.bx0) / 2 + 2;
			try (var s = d.stage(StageKind.OPENINGS)) {
				for (int i = 0; i + 1 < p.cols.length; i++) {
					for (int z = p.cols[i] + 1; z < p.cols[i + 1]; z++) {
						for (int y = wy0; y < p.hn; y++) {
							d.set(x, y, z, glass.pane());
						}
					}
				}
			}
		}
		// tie beams across the nave at each pier, the wheels hang from them
		try (var s = d.stage(StageKind.FRAME)) {
			for (int cz : p.cols) {
				for (int x = p.xa + 1; x < p.xb; x++) {
					d.set(x, p.hn, cz, beam.log(Direction.Axis.X));
				}
			}
		}
	}

	/** The nave's roof by the culture (or a deck behind a parapet), the aisles' lean-tos, gables filled. */
	private static Roofs.Roof roofs(Draft d, Plan p, DeepManner manner) {
		Mat sheet = d.mat(Role.SHEET).has(Form.STAIRS) ? d.mat(Role.SHEET) : d.mat(Role.ROOF);
		Mat roofM = d.mat(Role.ROOF);
		Mat trim = d.mat(Role.ROOF_TRIM);
		Mat wall = d.mat(Role.WALL);
		int x0 = p.xa - 1;
		int x1 = p.xb + 1;
		int z0 = p.zf - 1;
		int z1 = p.zb + (p.zb + 1 < p.dp ? 1 : 0);
		int eave = p.hn + 1;
		Roofs.Roof r;
		if (p.pitch <= 0) {
			// a deck over the nave behind a parapet (and a dome over its middle where the culture domes)
			try (var s = d.stage(StageKind.ROOF)) {
				for (int z = p.zf; z <= p.zb; z++) {
					for (int x = p.xa; x <= p.xb; x++) {
						boolean edge = x == p.xa || x == p.xb || z == p.zf || z == p.zb;
						d.set(x, eave, z, roofM.full());
						if (edge && eave + 1 < d.maxHeight()) {
							d.set(x, eave + 1, z, (x + z) % 2 == 0 ? wall.full() : Rock.dressing(d).slab(SlabType.BOTTOM));
						}
					}
				}
			}
			r = Roofs.raster(d, (x, z) -> Double.NaN, eave, x0, z0, x1, z1, roofM, trim, Habits.Ridge.SLAB);
			if (p.roof.equals("dome")) {
				int cz = (p.zf + p.zb) / 2;
				double rad = Math.min((p.xb - p.xa) / 2.0 - 0.5, 4.5);
				int rise = Math.max(2, Math.min(p.h - 2 - eave, (int) Math.round(rad)));
				Roofs.Field dome = Roofs.dome(p.c, cz, rad, rise / (rad + 0.5));
				Roofs.Roof dr = Roofs.raster(d, dome, eave + 1, p.xa + 1, cz - 5, p.xb - 1, cz + 5, sheet, trim, Habits.Ridge.SLAB);
				// the drum the dome stands on: its rim walled down to the deck
				Mat dress = Rock.dressing(d);
				try (var s = d.stage(StageKind.ROOF)) {
					for (int z = dr.z0; z <= dr.z1; z++) {
						for (int x = dr.x0; x <= dr.x1; x++) {
							if (!dr.covers(x, z)) {
								continue;
							}
							boolean rim = !dr.covers(x + 1, z) || !dr.covers(x - 1, z) || !dr.covers(x, z + 1) || !dr.covers(x, z - 1);
							for (int y = eave + 1; rim && y < dr.bottomY(x, z); y++) {
								d.set(x, y, z, dress.full());
							}
						}
					}
				}
			}
		} else {
			Roofs.Profile prof = p.roof.equals("flared") ? Roofs.Profile.curved(p.pitch) : Roofs.Profile.straight(p.pitch);
			Roofs.Field f = p.roof.equals("hip") ? Roofs.hip(x0, z0, x1, z1, prof) : Roofs.gable(x0, z0, x1, z1, Direction.Axis.Z, prof);
			if (p.roof.equals("flared")) {
				f = Roofs.flared(f, x0, z0, x1, z1, 1.0, 3);
			}
			r = Roofs.raster(d, f, eave - 1, x0, z0, x1, z1, sheet, trim, Habits.Ridge.TRIM);
			// gable ends filled in the culture's own finish (the front one carries the rose window)
			Paint gable = DeepManner.home(d, Role.WALL).orElse(Rock.dressing(d).full());
			try (var s = d.stage(StageKind.WALLS)) {
				for (int z : new int[]{p.zf, p.zb}) {
					for (int x = p.xa; x <= p.xb; x++) {
						int under = r.bottomY(x, z);
						for (int y = p.hn + 1; y < under; y++) {
							d.set(x, y, z, gable);
						}
					}
				}
			}
		}
		if (p.pitch > 0 && !p.cap.equals("spire")) {
			// the nave in the culture's own covering (red tile, shingles, slate): copper stays on the aisles and the onions
			DeepManner.rehome(d, 0, eave - 1, 0, p.w - 1, d.maxHeight() - 1, p.dp - 1, sheet, Role.ROOF);
		} else if (p.pitch > 0) {
			Rock.copper(d, 0, eave - 1, 0, p.w - 1, d.maxHeight() - 1, p.dp - 1, sheet, Rock.weathering(d));
		} else if (p.roof.equals("dome")) {
			Rock.copper(d, p.xa, eave + 1, p.zf, p.xb, d.maxHeight() - 1, p.zb, sheet, Rock.weathering(d));
		}
		// the aisles' lean-tos, rising to the clerestory
		for (int side = 0; side < 2; side++) {
			int ax0 = side == 0 ? p.bx0 - 1 : p.xb + 1;
			int ax1 = side == 0 ? p.xa - 1 : p.bx1 + 1;
			Direction upTo = side == 0 ? Dirs.RIGHT : Dirs.LEFT;
			Roofs.Field shed = Roofs.shed(Math.max(0, ax0), p.t + 1, Math.min(p.w - 1, ax1), p.zb, upTo, Roofs.Profile.straight(0.5));
			Roofs.raster(d, shed, p.ha, Math.max(0, ax0), p.t + 1, Math.min(p.w - 1, ax1), p.zb, sheet, trim, Habits.Ridge.SLAB);
			Rock.copper(d, Math.max(0, ax0), p.ha, p.t + 1, Math.min(p.w - 1, ax1), p.ha + 4, p.zb, sheet, Rock.weathering(d));
		}
		return r;
	}

	// ------------------------------------------------------------------------------------------------- towers

	/**
	 * The two towers at the front corners: dressed quoins, slit windows climbing them, a belfry stage of arched openings,
	 * a corbelled parapet; a cap by the culture (a steep pyramid of the sheet, a flared one, a little dome, crenels).
	 * The broader halls' towers hold a stair of slabs to a lookout.
	 */
	private static void towers(Draft d, Plan p, DeepManner manner, Glow g) {
		Mat wall = Rock.dressing(d);
		Mat dress = d.mat(Role.PLINTH).has(Form.STAIRS) ? d.mat(Role.PLINTH) : d.mat(Role.WALL);
		Mat plinth = d.mat(Role.PLINTH);
		Mat sheet = d.mat(Role.SHEET).has(Form.STAIRS) ? d.mat(Role.SHEET) : d.mat(Role.ROOF);
		Mat trim = d.mat(Role.ROOF_TRIM);
		boolean capped = !p.roof.equals("flat") && !p.cap.equals("crenel");
		int capH = !capped ? 0 : p.cap.equals("helm") || p.cap.equals("onion") ? (p.t == 5 ? 4 : 3) : p.t == 5 ? 3 : 2;
		int wallTop = p.towerTop - capH - (capped ? 0 : 1);
		for (int side = 0; side < 2; side++) {
			int tx0 = side == 0 ? p.bx0 : p.bx1 - p.t + 1;
			int tx1 = tx0 + p.t - 1;
			int tz0 = 1;
			int tz1 = p.t;
			try (var s = d.stage(StageKind.WALLS)) {
				for (int z = tz0; z <= tz1; z++) {
					for (int x = tx0; x <= tx1; x++) {
						boolean edge = x == tx0 || x == tx1 || z == tz0 || z == tz1;
						boolean corner = (x == tx0 || x == tx1) && (z == tz0 || z == tz1);
						if (!edge && p.stairTower) {
							continue;
						}
						for (int y = -1; y <= wallTop; y++) {
							BlockState b = y == -1 ? plinth.full().at(x, y, z) : corner && (y & 1) == 0 ? dress.full().at(x, y, z) : wall.full().at(x, y, z);
							d.set(x, y, z, b);
						}
					}
				}
			}
			// slits on the front and the outer face, the belfry's arches near the top
			int outX = side == 0 ? tx0 : tx1;
			Direction outDir = side == 0 ? Dirs.LEFT : Dirs.RIGHT;
			int mx = (tx0 + tx1) / 2;
			int mz = (tz0 + tz1) / 2;
			int belfry = wallTop - 3;
			// (glazed: the spiral climbs past them, and an open slit beside a step is a way out over the street)
			try (var s = d.stage(StageKind.OPENINGS)) {
				for (int y = 4; y < belfry - 1; y += 4) {
					if (p.stairTower) {
						d.set(mx, y, tz0, d.mat(Role.GLASS).pane());
						d.set(mx, y + 1, tz0, d.mat(Role.GLASS).pane());
						d.set(outX, y + 2, mz, d.mat(Role.GLASS).pane());
						d.set(outX, y + 3, mz, d.mat(Role.GLASS).pane());
					}
				}
			}
			if (p.stairTower) {
				// the belfry: an arched opening a side, a lantern hung in it
				for (Direction face : new Direction[]{Dirs.FRONT, outDir, side == 0 ? Dirs.RIGHT : Dirs.LEFT}) {
					int fx = face == Dirs.FRONT ? mx : face == Dirs.LEFT ? tx0 : tx1;
					int fz = face == Dirs.FRONT ? tz0 : mz;
					try (var s = d.stage(StageKind.OPENINGS)) {
						d.set(fx, belfry, fz, Blocks.AIR.defaultBlockState());
						d.set(fx, belfry + 1, fz, Blocks.AIR.defaultBlockState());
					}
					Direction along = Dirs.right(face.getOpposite());
					try (var s = d.stage(StageKind.WALLS)) {
						d.set(fx - Dirs.dx(along), belfry + 1, fz - Dirs.dz(along), dress.stairsOrSlab(along.getOpposite(), Half.TOP));
						d.set(fx + Dirs.dx(along), belfry + 1, fz + Dirs.dz(along), dress.stairsOrSlab(along, Half.TOP));
						d.set(fx, belfry + 2, fz, dress.full());
						d.set(fx, belfry - 1, fz, dress.slab(SlabType.TOP));
					}
				}
				// the floor of the lookout, the stair up to it
				int lookout = belfry;
				try (var s = d.stage(StageKind.FLOORS)) {
					for (int z = tz0 + 1; z < tz1; z++) {
						for (int x = tx0 + 1; x < tx1; x++) {
							d.set(x, -1, z, plinth.full());
							d.set(x, lookout - 1, z, d.mat(Role.FLOOR).full());
						}
					}
				}
				d.carve(tx0 + 1, 0, tz0 + 1, tx1 - 1, wallTop - 1, tz1 - 1);
				try (var s = d.stage(StageKind.FLOORS)) {
					for (int z = tz0 + 1; z < tz1; z++) {
						for (int x = tx0 + 1; x < tx1; x++) {
							d.set(x, wallTop, z, d.mat(Role.CEILING).full());
						}
					}
				}
				// the door from the aisle at the tower's back; the spiral's foot starts beside its inside cell, so the way in
				// stands clear to the head (its first turn passes four over the door) and the spiral cuts its own way
				// through the lookout floor
				Block door = d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR);
				int dx = side == 0 ? tx1 - 1 : tx0 + 1;
				Stairs.spiral(d, mx, mz, 0, lookout, side == 0 ? 4 : 6);
				d.air(dx, 0, tz1);
				d.air(dx, 1, tz1);
				d.inStage(StageKind.OPENINGS, () -> d.door(dx, 0, tz1, door, Dirs.FRONT, DoorHingeSide.LEFT));
				// the lookout's standing place two cells on from the spiral's head, where its floor is whole
				int[][] ring = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
				int[] vp = ring[((side == 0 ? 4 : 6) + 2 * lookout + 2) % 8];
				d.poi(PoiType.VIEWPOINT, mx + vp[0], lookout, mz + vp[1], Dirs.FRONT);
				Lighting.chandelier(d, mx, mz, wallTop, Math.max(lookout + 2, wallTop - 1));
				for (int y = 3; y < lookout - 2; y += 5) {
					Lighting.sconce(d, mx + (side == 0 ? 1 : -1), y + 1, tz1 - 1, Dirs.BACK);
				}
			}
			// a corbelled parapet round the top of the shaft
			try (var s = d.stage(StageKind.WALLS)) {
				for (int x = tx0; x <= tx1; x++) {
					for (int z = tz0; z <= tz1; z++) {
						boolean edge = x == tx0 || x == tx1 || z == tz0 || z == tz1;
						if (!edge) {
							continue;
						}
						if (z == tz0 && d.allowed(x, wallTop - 1, z - 1)) {
							d.set(x, wallTop - 1, z - 1, dress.stairsOrSlab(Dirs.BACK, Half.TOP));
						}
						int ox = side == 0 ? x - 1 : x + 1;
						if ((side == 0 ? x == tx0 : x == tx1) && ox >= 0 && ox < p.w) {
							d.set(ox, wallTop - 1, z, dress.stairsOrSlab(side == 0 ? Dirs.RIGHT : Dirs.LEFT, Half.TOP));
						}
					}
				}
			}
			// the cap
			try (var s = d.stage(StageKind.ROOF)) {
				if (!capped) {
					for (int x = tx0; x <= tx1; x++) {
						for (int z = tz0; z <= tz1; z++) {
							boolean edge = x == tx0 || x == tx1 || z == tz0 || z == tz1;
							d.set(x, wallTop, z, edge ? wall.full() : d.mat(Role.ROOF).full());
							if (edge && ((x + z) & 1) == 0 && wallTop + 1 < d.maxHeight()) {
								d.set(x, wallTop + 1, z, dress.full());
							}
						}
					}
					if (wallTop + 1 < d.maxHeight()) {
						d.set(mx, wallTop + 1, mz, States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.SIGNAL_FIRE, false));
					}
				}
			}
			if (capped) {
				Roofs.Field cap = p.cap.equals("helm") ? Roofs.hip(tx0, tz0, tx1, tz1, Roofs.Profile.straight(2.0))
					: p.cap.equals("onion") ? Roofs.dome(mx, mz, p.t / 2.0 + 0.5, 1.1) : switch (p.roof) {
					case "dome" -> Roofs.dome(mx, mz, p.t / 2.0, 0.9);
					case "flared" -> Roofs.flared(Roofs.hip(tx0 - 1, tz0 - 1, tx1 + 1, tz1 + 1, Roofs.Profile.curved(1.2)), tx0 - 1, tz0 - 1, tx1 + 1, tz1 + 1,
						1.0, 2);
					default -> Roofs.hip(tx0, tz0, tx1, tz1, Roofs.Profile.straight(1.4));
				};
				int cx0 = Math.max(0, tx0 - 1);
				int cx1 = Math.min(p.w - 1, tx1 + 1);
				Mat capM = p.cap.equals("helm") ? d.mat(Role.ROOF) : sheet;
				Roofs.Roof capR = Roofs.raster(d, cap, wallTop + 1, cx0, Math.max(0, tz0 - 1), cx1, tz1 + 1, capM, trim, Habits.Ridge.FULL);
				if (p.cap.equals("onion") || p.cap.equals("helm")) {
					HallParts.solid(d, capR, wallTop + 1, capM.full());
				}
				if (!p.cap.equals("helm")) {
					Rock.copper(d, cx0, wallTop + 1, Math.max(0, tz0 - 1), cx1, d.maxHeight() - 1, tz1 + 1, sheet, Rock.weathering(d));
				}
				// a finial of the realm's glow on the point
				int top = capR.ridgeY + 1;
				if (top < d.maxHeight() && d.isAbsent(mx, top, mz) && d.isSolid(mx, top - 1, mz)) {
					try (var s = d.stage(StageKind.ROOF)) {
						d.set(mx, top, mz, g.lamp.defaultBlockState());
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------- the front

	/**
	 * The frontispiece between the towers: a seven-wide frame of dressed stone standing proud, a deep porch under a
	 * round arch with a keystone of the realm's glow, triple doors, a carved tympanum, a rose window in the gable over it.
	 */
	private static void front(Draft d, Plan p, Glow g, DeepManner manner, Roofs.Roof nave) {
		Mat dress = d.mat(Role.PLINTH).has(Form.STAIRS) ? d.mat(Role.PLINTH) : d.mat(Role.WALL);
		Mat plinth = d.mat(Role.PLINTH);
		int c = p.c;
		int zp = p.zf - 1;
		int half = Math.min(3, (p.xb - p.xa) / 2);
		int top = Math.min(p.hn - 1, 7);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = c - half; x <= c + half; x++) {
				boolean pier = x == c - half || x == c + half;
				for (int y = -1; y <= top; y++) {
					if (pier || y >= top - 1) {
						d.set(x, y, zp, y == -1 ? plinth.full() : dress.full());
					}
				}
			}
			// the arch: shoulders inside the frame under its head, a keystone of the realm's glow
			d.set(c - half + 1, top - 2, zp, dress.stairsOrSlab(Dirs.LEFT, Half.TOP));
			d.set(c + half - 1, top - 2, zp, dress.stairsOrSlab(Dirs.RIGHT, Half.TOP));
			d.set(c, top - 1, zp, g.accent.defaultBlockState());
			d.set(c, top, zp, Rock.carved(d).defaultBlockState());
			// a little gable of the cornice over the frame
			for (int x = c - half; x <= c + half; x++) {
				if (d.allowed(x, top + 1, zp)) {
					d.set(x, top + 1, zp, dress.stairsOrSlab(x < c ? Dirs.RIGHT : x > c ? Dirs.LEFT : Dirs.BACK, Half.BOTTOM));
				}
			}
		}
		// the doors: three leaves under a carved tympanum
		Block door = d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR);
		int dw = half >= 3 ? 1 : 0;
		for (int x = c - dw; x <= c + dw; x++) {
			int fx = x;
			d.air(fx, 0, p.zf);
			d.air(fx, 1, p.zf);
			DoorHingeSide hinge = x < c ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT;
			d.inStage(StageKind.OPENINGS, () -> d.door(fx, 0, p.zf, door, Dirs.FRONT, hinge));
		}
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = c - dw - 1; x <= c + dw + 1; x++) {
				for (int y = 2; y <= 4 && y < top - 1; y++) {
					boolean jamb = x == c - dw - 1 || x == c + dw + 1;
					BlockState b = jamb ? dress.full().at(x, y, p.zf) : y == 3 && x == c ? g.accent.defaultBlockState() : Rock.carved(d).defaultBlockState();
					d.set(x, y, p.zf, b);
				}
				if (x == c - dw - 1 || x == c + dw + 1) {
					d.set(x, 0, p.zf, dress.full());
					d.set(x, 1, p.zf, dress.full());
				}
			}
		}
		d.entrance(c, 0, zp);
		d.poi(PoiType.DOOR, c, 0, zp, Dirs.BACK);
		// lanterns hung in the porch either side of the doors
		Lighting.chandelier(d, c - half + 1, zp, top - 2, top - 3);
		Lighting.chandelier(d, c + half - 1, zp, top - 2, top - 3);
		// the rose window in the gable: a ring of panes round the realm's glow, dressed round
		int ry = Math.max(top + 3, p.hn - 1);
		Mat glass = d.mat(Role.GLASS);
		if (ry + 1 < nave.bottomY(c, p.zf) || p.pitch <= 0 && ry + 1 <= p.hn) {
			try (var s = d.stage(StageKind.OPENINGS)) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dy = -1; dy <= 1; dy++) {
						d.set(c + dx, ry + dy, p.zf, dx == 0 && dy == 0 ? g.lamp.defaultBlockState() : glass.pane().at(c + dx, ry + dy, p.zf));
					}
				}
			}
			try (var s = d.stage(StageKind.WALLS)) {
				for (int[] o : new int[][]{{-2, 0}, {2, 0}, {0, -2}, {0, 2}}) {
					d.set(c + o[0], ry + o[1], p.zf, dress.full());
				}
			}
		}
		// the facade between towers and frame: a window over each aisle door's bay
		for (int x : new int[]{(p.bx0 + p.t + c - half) / 2, (p.bx1 - p.t + 1 + c + half) / 2}) {
			if (x > p.bx0 + p.t - 1 && x < p.bx1 - p.t + 1 && Math.abs(x - c) > half) {
				Rock.window(d, x, 2, p.zf, Dirs.FRONT, 1, 3, false);
			}
		}
	}

	/** Buttresses down the sides at each pier, tall arched windows between them, the back wall's window over the dais. */
	private static void sides(Draft d, Plan p) {
		Mat dress = d.mat(Role.PLINTH).has(Form.STAIRS) ? d.mat(Role.PLINTH) : d.mat(Role.WALL);
		Mat wall = d.mat(Role.WALL);
		for (int side = 0; side < 2; side++) {
			int wx = side == 0 ? p.bx0 : p.bx1;
			int ox = side == 0 ? 0 : p.w - 1;
			Direction out = side == 0 ? Dirs.LEFT : Dirs.RIGHT;
			try (var s = d.stage(StageKind.WALLS)) {
				for (int cz : p.cols) {
					for (int y = -1; y < p.ha - 1; y++) {
						d.set(ox, y, cz, y < p.ha - 2 ? wall.full() : dress.stairsOrSlab(out.getOpposite(), Half.BOTTOM));
					}
				}
			}
			for (int i = 0; i + 1 < p.cols.length; i++) {
				int z = (p.cols[i] + p.cols[i + 1]) / 2;
				Rock.window(d, wx, 1, z, out, 1, Math.min(3, p.ha - 3), true);
			}
		}
		// the back: a tall window over the dais unless the hall is cut into the rock behind
		if (d.site().maxGround(p.xa, p.zb + 1, p.xb, p.zb + 1) < 3) {
			Rock.window(d, p.c, 3, p.zb, Dirs.BACK, 1, Math.max(2, Math.min(5, p.hn - 4)), false);
		}
	}

	// ------------------------------------------------------------------------------------------------- inside

	/**
	 * The hall's furnishing: the runner to the dais, the reeve's high table, chair and lectern on the dais, long tables and
	 * benches down the nave, a great hearth in an aisle wall with its stack through the lean-to, record chests, light.
	 */
	private static void interior(Draft d, Plan p, Glow g) {
		Mat furniture = d.mat(Role.FURNITURE);
		Mat dress = Rock.dressing(d);
		int c = p.c;
		int last = p.cols[p.cols.length - 1];
		// the dais: the nave's last bays raised a step, stairs across its front
		// three rows at least: the step, the high table and the reeve's chair each have their own (a long hall whose last
		// pier stands near the back wall carries the dais in over it)
		int dz0 = Math.min(Math.max(last + 1, p.zb - 4), p.zb - 3);
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = dz0; z < p.zb; z++) {
				for (int x = p.xa + 1; x < p.xb; x++) {
					d.set(x, 0, z, dress.full());
				}
			}
			for (int x = p.xa + 1; x < p.xb; x++) {
				d.set(x, 0, dz0 - 1, dress.stairsOrSlab(Dirs.BACK, Half.BOTTOM));
			}
		}
		// the high table across the dais, the reeve's chair behind it, his lectern beside
		int tz = Math.min(p.zb - 2, dz0 + 1);
		int th = p.xb - p.xa - 1 >= 9 ? 2 : 1;
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int x = c - th; x <= c + th; x++) {
				d.set(x, 1, tz, x == c - th || x == c + th ? furniture.fence() : furniture.slab(SlabType.TOP));
			}
		}
		d.poi(PoiType.TABLE, c, 1, tz - 1, Dirs.BACK);
		if (tz + 1 < p.zb) {
			try (var s = d.stage(StageKind.INTERIOR)) {
				d.set(c, 1, tz + 1, furniture.stairsOrSlab(Dirs.BACK, Half.BOTTOM));
			}
			d.poi(PoiType.SEAT, c, 1, tz + 1, Dirs.FRONT);
			d.poi(PoiType.THRONE, c, 1, tz + 1, Dirs.FRONT);
		}
		// the reeve's lectern beside the table, where he keeps the town's book
		int lx = c + th + 1 < p.xb ? c + th + 1 : c - th - 1;
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(lx, 1, tz, States.facing(Blocks.LECTERN, Dirs.FRONT));
		}
		d.poi(PoiType.JOB_SITE, lx, 1, tz - 1, Dirs.BACK, JobType.REEVE);
		d.poi(PoiType.LECTERN, lx, 1, tz - 1, Dirs.BACK);
		// the town chest on the dais beside the reeve's chair: the deep's dues and the miners' tallies
		int ox = lx > c ? c - th - 1 : c + th + 1;
		if (tz + 1 < p.zb && ox > p.xa && ox < p.xb) {
			try (var s = d.stage(StageKind.INTERIOR)) {
				d.set(ox, 1, tz + 1, States.facing(Blocks.CHEST, Dirs.FRONT), BlockEntities.container(List.of(new Object[]{"minecraft:gold_ingot", 6},
					new Object[]{"minecraft:raw_gold", 9}, new Object[]{"minecraft:emerald", 4}, new Object[]{"minecraft:paper", 8})));
			}
			d.poi(PoiType.STRONGBOX, ox, 1, tz, Dirs.BACK);
		}
		Lighting.candles(d, c - th, 2, tz, 3);
		Lighting.candles(d, c + th, 2, tz, 2);
		// banners of the house's cloth behind the dais
		Block banner = d.mat(Role.CLOTH).main(Form.WALL_BANNER);
		if (banner != null) {
			try (var s = d.stage(StageKind.INTERIOR)) {
				for (int x : new int[]{c - 2, c + 2}) {
					if (x > p.xa && x < p.xb && d.isAbsent(x, 4, p.zb - 1)) {
						d.set(x, 4, p.zb - 1, States.facing(banner, Dirs.FRONT));
					}
				}
			}
		}
		// the runner from the doors to the dais
		Mat cloth = d.mat(Role.CLOTH);
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int z = p.zf + 1; z < dz0 - 1; z++) {
				d.set(c, 0, z, cloth.carpet());
			}
		}
		d.poi(PoiType.MEETING_POINT, c, 0, (p.zf + dz0) / 2, Dirs.BACK);
		// long tables and benches down the nave
		int nw = p.xb - p.xa - 1;
		int[] tables = nw >= 7 ? new int[]{c - 2, c + 2} : new int[]{c + 2 < p.xb - 1 ? c + 2 : c - 2};
		for (int tx : tables) {
			int z0 = p.zf + 4;
			int z1 = dz0 - 3;
			if (z1 - z0 < 2) {
				continue;
			}
			Furniture.longTable(d, tx, 0, z0, Dirs.BACK, z1 - z0 + 1);
			for (int z = z0; z <= z1; z++) {
				for (int sx : new int[]{tx - 1, tx + 1}) {
					if (sx > p.xa && sx < p.xb && sx != c) {
						try (var s = d.stage(StageKind.INTERIOR)) {
							d.set(sx, 0, z, furniture.stairsOrSlab(sx < tx ? Dirs.LEFT : Dirs.RIGHT, Half.BOTTOM));
						}
						if ((z - z0) % 2 == 0) {
							d.poi(PoiType.SEAT, sx, 0, z, sx < tx ? Dirs.RIGHT : Dirs.LEFT);
						}
					}
				}
				if ((z - z0) % 3 == 1) {
					Lighting.candles(d, tx, 1, z, 2);
				}
			}
		}
		// the great hearth in the left aisle's wall, its stack up through the lean-to
		int hz = (p.cols[0] + p.cols[Math.min(1, p.cols.length - 1)]) / 2 + 3;
		if (hz >= p.zb - 1) {
			hz = p.zb - 2;
		}
		int hx = p.bx0 + 1;
		Furniture.hearth(d, hx, 0, hz, Dirs.RIGHT, true);
		Mat chimney = d.mat(Role.CHIMNEY);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int y = 1; y <= p.ha + 2; y++) {
				d.set(p.bx0, y, hz, chimney.full());
				if (y >= 2) {
					d.set(hx, y, hz, chimney.full());
				}
			}
		}
		if (d.allowed(p.bx0, p.ha + 3, hz)) {
			try (var s = d.stage(StageKind.ROOF)) {
				d.set(p.bx0, p.ha + 3, hz, States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.SIGNAL_FIRE, false));
			}
		}
		Lighting.flank(d, hx, 2, hz, Dirs.LEFT, 2);
		// the town's records: chests and shelves along the right aisle
		int rx = p.bx1 - 1;
		for (int i = 0; i + 1 < p.cols.length; i += 2) {
			int z = p.cols[i] + 1;
			Furniture.chest(d, rx, 0, z, Dirs.LEFT, List.of(new Object[]{"minecraft:paper", 12}, new Object[]{"minecraft:book", 3},
				new Object[]{"minecraft:gold_nugget", 9}));
			if (z + 1 < p.cols[i + 1]) {
				Furniture.bookshelf(d, rx, 0, z + 1);
			}
		}
		// light: wheels from the tie beams down the nave, sconces on the piers facing the aisles
		for (int i = 0; i < p.cols.length; i++) {
			int cz = p.cols[i];
			if (cz >= dz0 - 1) {
				continue;
			}
			int hub = Math.max(4, p.hn - 3);
			if (p.xb - p.xa - 1 >= 5) {
				Lighting.wheel(d, c, cz, p.hn, hub);
			} else {
				Lighting.chandelier(d, c, cz, p.hn, hub);
			}
		}
		for (int cz : p.cols) {
			for (int side = 0; side < 2; side++) {
				int x = side == 0 ? p.xa - 1 : p.xb + 1;
				if (d.isAbsent(x, 2, cz) && d.isAbsent(x, 3, cz)) {
					Lighting.sconce(d, x, 2, cz, side == 0 ? Dirs.RIGHT : Dirs.LEFT);
				}
			}
		}
		Lighting.room(d, p.xa + 1, p.zf + 1, p.xb - 1, dz0 - 2, 0);
		Lighting.room(d, p.xa + 1, dz0, p.xb - 1, p.zb - 1, 1);
		Lighting.room(d, p.bx0 + 1, p.t + 1, p.xa - 1, p.zb - 1, 0);
		Lighting.room(d, p.xb + 1, p.t + 1, p.bx1 - 1, p.zb - 1, 0);
	}

	/** The apron before the hall: paved, a lamp post a side, the notice board by the porch. */
	private static void apron(Draft d, Plan p, Glow g) {
		int z = 0;
		for (int x : new int[]{p.c - 4, p.c + 4}) {
			if (x >= 0 && x < p.w && d.isAbsent(x, 0, z)) {
				Rock.lampPost(d, x, 0, z, Noise.unit(d.seed(), x, 0, z) < 0.5, g);
			}
		}
		// the notice board on the frontispiece's pier
		int nx = p.c + Math.min(3, (p.xb - p.xa) / 2) + 1;
		if (nx < p.w && d.isAbsent(nx, 1, 1)) {
			try (var s = d.stage(StageKind.EXTERIOR)) {
				d.set(nx, 1, 1, States.facing(d.mat(Role.FURNITURE).mainOr(Form.WALL_SIGN, Blocks.SPRUCE_WALL_SIGN), Dirs.FRONT),
					BlockEntities.sign(List.of("", "Moot today", "at the bell", ""), "black", false));
			}
			d.poi(PoiType.NOTICE_BOARD, nx, 0, 0, Dirs.BACK);
		}
	}
}
