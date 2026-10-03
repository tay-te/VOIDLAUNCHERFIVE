package dev.voidmc.hearth.build.archetype.civic;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.civic.Baths;
import dev.voidmc.hearth.build.kit.civic.Fam;
import dev.voidmc.hearth.build.kit.civic.Grounds;
import dev.voidmc.hearth.build.kit.civic.Halls;
import dev.voidmc.hearth.build.kit.civic.Portico;
import dev.voidmc.hearth.build.kit.civic.Storeys;
import dev.voidmc.hearth.build.kit.civic.Towers;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:civic/bathhouse}: a city's baths, warm pools and steam. Each tradition bathes its own way:
 * <ul>
 *   <li>the <b>Roman thermae</b> of the mediterranean (and the stone, timber, alpine, steppe and raised towns): a hall of
 *   columns round a long sunken pool on a mosaic floor, a hot pool in an apse at the back with steam rising through the
 *   grates, benches round the walls, a clerestory over the pool, a portico on the street;</li>
 *   <li>the <b>hammam</b> of the desert and the mesa: a square hot room under a great dome pierced with star-lights, the
 *   heated marble platform in its middle, basins in the walls, a cold pool to one side, little domes round about;</li>
 *   <li>the <b>onsen</b> of the karst: an open rock pool steaming in a walled garden of stone lanterns, a timber bath hall
 *   of lattice under a curved roof at the back;</li>
 *   <li>the <b>sauna</b> of the north: a log bath house with its tiers of benches round the hot stones, a plunge pool
 *   outside.</li>
 * </ul>
 *
 * <p>POIs: SEATs, a MEETING_POINT at the pool, STORAGE (towels and soap), DOOR. Params: {@code variant} (int).
 */
public final class Bathhouse implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("civic/bathhouse"), ArchetypeCategory.CIVIC, Set.of(), 0,
		11, 15, 11, 17, 8, 16, SettlementTier.CITY, Set.of(), Set.of(), List.of(), 1, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		Tradition t = Tradition.of(d);
		switch (t) {
			case ADOBE -> hammam(d, t);
			case KARST -> onsen(d, t);
			case LOG -> sauna(d, t);
			default -> thermae(d, t);
		}
	}

	// ------------------------------------------------------------------------------------------------ thermae

	static void thermae(Draft d, Tradition t) {
		int w = d.frontage();
		int dp = d.depth();
		int tier = d.tier();
		int maxH = d.maxHeight();
		Habits h = d.habits();
		boolean raised = t.raised();
		int x0 = 0;
		int x1 = w - 1;
		int cx = (x0 + x1) / 2;
		int zF = raised ? 3 : 2;
		int z0 = zF;
		int z1 = dp - 1;
		int hh = 6;
		int base = raised ? Grounds.stiltBase(d, x0, z0, x1, z1) : Grounds.raisedBase(d, x0, x1, z0);
		Body body = new Body(x0, z0, x1, z1, base, new int[]{hh}, false, false);
		d.body(x0, z0, x1, z1);
		int plate = body.plateY();
		d.eave(plate);
		Storeys.ground(d, body, raised);
		Grounds.forecourt(d, 0, w - 1, z0 - 1, false);
		Facade fac = Facade.plan(d, body, cx, 1, 1, 0.7);
		Storeys.walls(d, body, fac, 0, 0, null, true);
		// high windows round the hall, the door with a portico
		for (Side s : body.sides(0)) {
			for (Facade.Opening o : fac.openings(s.out())) {
				if (o.kind() == Facade.Kind.WINDOW && s.contains(o.at())) {
					Openings.window(d, s, o.at(), base + 3, o.width(), 2, Openings.WindowStyle.ARCHED, new Openings.WindowOpts(false, false, true, true));
				}
			}
		}
		Side front = body.side(0, Dirs.FRONT);
		Openings.door(d, front, cx, base, 1, new Openings.DoorOpts(false, false, true));
		int skyline = plate;
		if (!raised && z0 >= 2) {
			skyline = Math.max(skyline, Portico.build(d, t, cx - 2, cx + 2, z0 - 2 >= 0 ? z0 - 2 : 0, z0 - 1, base, 4, true));
		}
		if (base >= 1 && !raised) {
			Grounds.flight(d, cx - 2, cx + 2, z0 - 3 >= 0 ? z0 - 3 : z0 - 1, base, base);
		}
		Grounds.approach(d, cx, base, z0 - 1);
		// the floor: mosaic; the long pool down the middle, a colonnade round it
		int ix0 = x0 + 1;
		int ix1 = x1 - 1;
		int iz0 = z0 + 1;
		int iz1 = z1 - 1;
		Baths.mosaic(d, ix0, iz0, ix1, iz1, base - 1);
		int px0 = ix0 + 3;
		int px1 = ix1 - 3;
		int pz0 = iz0 + 3;
		int pz1 = iz1 - 4;
		d.poi(PoiType.MEETING_POINT, cx, base, iz0 + 1, Dirs.BACK);
		if (px1 - px0 >= 1 && pz1 - pz0 >= 2) {
			Baths.pool(d, px0, pz0, px1, pz1, base, 2);
			// columns at the pool's corners and along its sides
			Mat accent = d.mat(Role.WALL_ACCENT);
			try (var s = d.stage(StageKind.WALLS)) {
				for (int z = pz0 - 2; z <= pz1 + 2; z += 2) {
					for (int x : new int[]{px0 - 2, px1 + 2}) {
						if (x <= ix0 || x >= ix1) {
							continue;
						}
						d.set(x, base, z, accent.full());
						for (int y = base + 1; y < plate; y++) {
							d.set(x, y, z, Portico.column(1, 4) ? accent.full() : accent.full());
						}
						d.set(x, plate - 1, z, accent.full());
					}
				}
			}
		}
		// the hot pool at the back, steam through the grates beside it, benches round the walls
		int hz0 = iz1 - 1;
		int hx0 = cx - 1;
		int hx1 = cx + 1;
		Baths.pool(d, hx0, hz0, hx1, iz1, base, 1);
		Baths.steam(d, ix0 + 1, base, iz1);
		Baths.steam(d, ix1 - 1, base, iz1);
		Baths.steam(d, ix0 + 1, base, iz0 + 1);
		Baths.steam(d, ix1 - 1, base, iz0 + 1);
		Baths.bench(d, ix0, base, iz0 + 3, Dirs.BACK, Math.max(1, iz1 - iz0 - 5), Dirs.RIGHT);
		Baths.bench(d, ix1, base, iz0 + 3, Dirs.BACK, Math.max(1, iz1 - iz0 - 5), Dirs.LEFT);
		Furniture.chest(d, ix0, base, iz0, Dirs.RIGHT, List.of(new Object[]{"minecraft:white_wool", 16}, new Object[]{"minecraft:honeycomb", 4}));
		// the roof: the culture's, with a clerestory lantern over the pool
		RoofForm form = h.roof().pick(d.random(), RoofForm.GABLE, RoofForm.HIP, RoofForm.HALF_HIP);
		if (t == Tradition.MEDITERRANEAN) {
			form = RoofForm.GABLE;
		}
		Storeys.Top top = Storeys.roof(d, body, form, Direction.Axis.Z, Math.max(0.5, h.roof().pitch()), 4);
		skyline = Math.max(skyline, top.ridge());
		if (top.roof() != null) {
			skyline = Math.max(skyline, Library.lantern(d, t, cx, (z0 + z1) / 2, plate, top.roof(), maxH));
		}
		for (int z = iz0 + 2; z <= iz1 - 2; z += 4) {
			Halls.lanternUnder(d, ix0 + 2, base, z, 2);
			Halls.lanternUnder(d, ix1 - 2, base, z, 2);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, skyline + 2);
	}

	// ------------------------------------------------------------------------------------------------ hammam

	static void hammam(Draft d, Tradition t) {
		int w = d.frontage();
		int dp = d.depth();
		int maxH = d.maxHeight();
		int x0 = 0;
		int x1 = w - 1;
		int cx = (x0 + x1) / 2;
		int z0 = 2;
		int z1 = dp - 1;
		int hh = 5;
		int base = Grounds.raisedBase(d, x0, x1, z0);
		Body body = new Body(x0, z0, x1, z1, base, new int[]{hh}, false, false);
		d.body(x0, z0, x1, z1);
		int plate = body.plateY();
		d.eave(plate);
		Storeys.ground(d, body, false);
		Grounds.forecourt(d, 0, w - 1, z0 - 1, true);
		Facade fac = new Facade();
		Storeys.walls(d, body, fac, 0, 0, null, true);
		Side front = body.side(0, Dirs.FRONT);
		Halls.iwan(d, front, cx, base, plate + 3, 2);
		int ez = z0 >= 3 ? z0 - 2 : z0 - 1;
		if (base >= 1) {
			Grounds.flight(d, cx - 1, cx + 1, ez, base, base);
		}
		Grounds.approach(d, cx, base, ez);
		// small arched lights high in the walls
		for (Side s : body.sides(0)) {
			for (int i = 2; i < s.last() - 1; i += 3) {
				if (s.out() == Dirs.FRONT && Math.abs(s.x(i) - cx) <= 1) {
					continue;
				}
				Openings.window(d, s, s.column(i), base + 2, 1, 1, Openings.WindowStyle.ARCHED, new Openings.WindowOpts(false, false, false, true));
			}
		}
		int deck = Roofs.flat(d, x0, z0, x1, z1, plate, true);
		// the hot room's dome in the middle, star-lights in it; little domes over the corners
		int hcz = (z0 + z1) / 2 + 1;
		Fam stone = Fam.of(d, Role.ROOF, Form.FULL, StageKind.ROOF);
		if (stone == null) {
			stone = Fam.of("minecraft:smooth_sandstone", StageKind.ROOF);
		}
		int r = Math.min((x1 - x0) / 2 - 1, (z1 - z0) / 2 - 1);
		r = Math.min(r, 4);
		// a drum lifts the dome clear of the parapet, lights in its faces
		Towers.shaft(d, cx - r, hcz - r, 2 * r + 1, deck, deck + 1, t, deck + 1);
		for (Side s : Side.of(cx - r, hcz - r, cx + r, hcz + r)) {
			for (int i = 2; i < s.last() - 1; i += 2) {
				int fi = i;
				d.inStage(StageKind.OPENINGS, () -> d.set(s.x(fi), deck, s.z(fi), Blocks.GLASS.defaultBlockState()));
			}
		}
		double[] radii = r >= 4 ? new double[]{4.4, 4.2, 3.8, 3.2, 2.3, 1.0} : new double[]{3.4, 3.2, 2.6, 1.8, 0.8};
		int top = Towers.lathe(d, cx, hcz, deck + 2, radii, stone, maxH);
		// star-lights: glass in the dome's courses, in rings
		for (int ring = 1; ring <= 2; ring++) {
			int yy = deck + 2 + ring;
			double rr = radii[ring] - 0.7;
			for (int k = 0; k < 8; k++) {
				double a = k * Math.PI / 4 + ring * Math.PI / 8;
				int x = (int) Math.round(cx + Math.cos(a) * rr);
				int z = (int) Math.round(hcz + Math.sin(a) * rr);
				if (!d.isEmpty(x, yy, z)) {
					d.inStage(StageKind.OPENINGS, () -> d.set(x, yy, z, Blocks.GLASS.defaultBlockState()));
				}
			}
		}
		top = Towers.finial(d, cx, top + 1, hcz, maxH, false);
		for (int[] c : new int[][]{{x0 + 2, z1 - 2}, {x1 - 2, z1 - 2}}) {
			Towers.shaft(d, c[0] - 1, c[1] - 1, 3, deck, deck, t, -1);
			Towers.lathe(d, c[0], c[1], deck + 1, new double[]{1.6, 1.0, 0.4}, stone, maxH);
		}
		// inside: open the hot room up into the dome, the marble platform, basins, benches, the cold pool
		int ix0 = x0 + 1;
		int ix1 = x1 - 1;
		int iz0 = z0 + 1;
		int iz1 = z1 - 1;
		d.clearBox(cx - r + 1, plate, hcz - r + 1, cx + r - 1, deck + 1, hcz + r - 1);
		Fam marble = Fam.of("minecraft:smooth_quartz|minecraft:calcite", StageKind.INTERIOR);
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int z = hcz - 1; z <= hcz + 1; z++) {
				for (int x = cx - 1; x <= cx + 1; x++) {
					d.set(x, base, z, marble.slab(SlabType.BOTTOM));
				}
			}
		}
		d.poi(PoiType.MEETING_POINT, cx, base, hcz - 2, Dirs.BACK);
		d.poi(PoiType.SEAT, cx, base, hcz, Dirs.FRONT);
		Baths.steam(d, cx - 3 > ix0 ? cx - 3 : ix0 + 1, base, hcz + 2);
		Baths.steam(d, cx + 3 < ix1 ? cx + 3 : ix1 - 1, base, hcz + 2);
		// basins along the side walls, a bench between each
		for (int z = iz0 + 2; z <= iz1; z += 3) {
			for (int x : new int[]{ix0, ix1}) {
				if (d.isEmpty(x, base, z)) {
					int fx = x;
					int fz = z;
					d.inStage(StageKind.INTERIOR, () -> d.set(fx, base, fz, Blocks.WATER_CAULDRON.defaultBlockState()
						.setValue(BlockStateProperties.LEVEL_CAULDRON, 3)));
				}
				if (d.isEmpty(x, base, z + 1) && z + 1 <= iz1) {
					Baths.bench(d, x, base, z + 1, Dirs.BACK, 1, x == ix0 ? Dirs.RIGHT : Dirs.LEFT);
				}
			}
		}
		// the cold pool by the door
		if (ix1 - ix0 >= 6) {
			Baths.pool(d, ix1 - 2, iz0, ix1 - 1, iz0 + 1, base, 1);
		}
		Furniture.chest(d, ix0, base, iz0, Dirs.RIGHT, List.<Object[]>of(new Object[]{"minecraft:white_wool", 16}));
		Halls.lanternUnder(d, cx, base, hcz, 4);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, top + 2);
	}

	// ------------------------------------------------------------------------------------------------ onsen

	static void onsen(Draft d, Tradition t) {
		int w = d.frontage();
		int dp = d.depth();
		int maxH = d.maxHeight();
		int base = 0;
		int cx = (w - 1) / 2;
		// the garden wall round the plot, the gate in the front
		Groundwork.level(d, 0, 0, w - 1, dp - 1, base);
		List<int[]> run = new ArrayList<>();
		for (int[] c : Draft.ring(0, 0, w - 1, dp - 1)) {
			run.add(c);
		}
		Mat wall = d.mat(Role.PLINTH);
		Mat bamboo = d.mat(Role.FENCE);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : run) {
				if (c[1] == 0 && Math.abs(c[0] - cx) <= 1) {
					continue;
				}
				int g = Groundwork.surface(d, c[0], c[1]);
				d.set(c[0], g, c[1], wall.full());
				if (bamboo.has(Form.FENCE)) {
					d.set(c[0], g + 1, c[1], bamboo.fence());
					d.set(c[0], g + 2, c[1], bamboo.fence());
				}
			}
		}
		ChapelShrine.gate(d, cx, 0, base);
		d.entrance(cx, base, -1);
		d.poi(PoiType.DOOR, cx, base, -1, Dirs.BACK);
		Garden.path(d, Garden.line(cx, 1, cx, 3), base);
		// the bath hall along the back: lattice under a curved roof
		int hz0 = dp - 6;
		int hz1 = dp - 2;
		Body hall = new Body(1, hz0, w - 2, hz1, base, new int[]{4}, false, false);
		Storeys.ground(d, hall, false);
		Facade fac = Facade.plan(d, hall, cx, 1, 1, 1.0);
		Storeys.walls(d, hall, fac, 0, 0, null, false);
		Storeys.windows(d, hall, fac, 0, 0, 2);
		Openings.door(d, hall.side(0, Dirs.FRONT), cx, base, 1, new Openings.DoorOpts(true, false, false));
		Roofs.Opts o = Roofs.Opts.of(d).withEaves(2, 1);
		Roofs.Built rb = Roofs.build(d, 1, hz0, w - 2, hz1, hall.plateY(), RoofForm.PAGODA, Direction.Axis.X, o, false, false);
		int top = rb.roof().ridgeY;
		// inside the hall: a wooden tub pool, benches, a chest of towels
		Baths.pool(d, cx - 1, hz0 + 2, cx + 1, hz1 - 1, base, 1);
		Baths.bench(d, 2, base, hz0 + 1, Dirs.BACK, hz1 - hz0 - 1, Dirs.RIGHT);
		Furniture.chest(d, w - 3, base, hz1 - 1, Dirs.LEFT, List.<Object[]>of(new Object[]{"minecraft:white_wool", 12}));
		Halls.lanternUnder(d, cx, base, (hz0 + hz1) / 2, 1);
		// the rock pool in the garden, steam among its stones, stone lanterns, a maple
		double pcx = cx;
		double pcz = Math.max(4, (hz0 - 1) / 2.0 + 1);
		double rx = Math.max(1.5, Math.min(w / 2.0 - 3, 3.5));
		double rz = Math.max(1.2, Math.min((hz0 - 4) / 2.0 - 0.5, 2.6));
		Baths.rockPool(d, pcx, pcz, rx, rz, base);
		d.poi(PoiType.MEETING_POINT, cx, base, (int) Math.floor(pcz - rz - 1), Dirs.BACK);
		for (int[] c : new int[][]{{(int) (pcx - rx - 1), (int) pcz}, {(int) (pcx + rx + 1), (int) pcz}}) {
			if (c[0] > 1 && c[0] < w - 2 && d.isEmpty(c[0], base, c[1])) {
				try (var s = d.stage(StageKind.INTERIOR)) {
					d.set(c[0], base - 2, c[1], Blocks.STONE.defaultBlockState());
					d.set(c[0], base - 1, c[1], States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.SIGNAL_FIRE, false));
					d.set(c[0], base, c[1], Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState());
				}
			}
		}
		ChapelShrine.stoneLantern(d, 2, base, 2);
		ChapelShrine.stoneLantern(d, w - 3, base, 2);
		if (w >= 12 && d.isEmpty(w - 3, base, hz0 - 2)) {
			Garden.tree(d, w - 3, base, hz0 - 2, 3);
		}
		Garden.garden(d, 1, 1, 2, hz0 - 2, base);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, top + 2);
	}

	// ------------------------------------------------------------------------------------------------ sauna

	static void sauna(Draft d, Tradition t) {
		int w = d.frontage();
		int dp = d.depth();
		Habits h = d.habits();
		int x0 = 1;
		int x1 = w - 2;
		int cx = (x0 + x1) / 2;
		int z0 = 2;
		int z1 = Math.min(dp - 1, z0 + 9);
		int base = Grounds.raisedBase(d, x0, x1, z0);
		Body body = new Body(x0, z0, x1, z1, base, new int[]{5}, false, false);
		d.body(x0, z0, x1, z1);
		int plate = body.plateY();
		d.eave(plate);
		Storeys.ground(d, body, false);
		Grounds.forecourt(d, 0, w - 1, z0 - 1, false);
		Facade fac = Facade.plan(d, body, cx, 1, 1, 0.4);
		Storeys.walls(d, body, fac, 0, 0, null, false);
		Storeys.windows(d, body, fac, 0, 0, 1);
		Side front = body.side(0, Dirs.FRONT);
		Openings.door(d, front, cx, base, 1, new Openings.DoorOpts(true, true, true));
		if (base >= 1) {
			Grounds.flight(d, cx - 1, cx + 1, z0 - 1, base, base);
		}
		Grounds.approach(d, cx, base, z0 - 1);
		Storeys.Top top = Storeys.roof(d, body, RoofForm.GABLE, Direction.Axis.Z, 1.0, 2);
		// a partition: the changing room in front, the hot room behind
		int pz = z0 + 4;
		Mat wall = d.mat(Role.WALL);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = x0 + 1; x <= x1 - 1; x++) {
				for (int y = base; y < plate; y++) {
					d.set(x, y, pz, wall.log(Direction.Axis.X).orElse(wall.full()));
				}
			}
		}
		d.inStage(StageKind.OPENINGS, () -> d.door(cx, base, pz, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), Dirs.BACK,
			net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT));
		// the hot room: tiers of benches along the back and sides, the stove of hot stones in a corner
		Mat wood = d.mat(Role.FURNITURE);
		int iz1 = z1 - 1;
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int x = x0 + 1; x <= x1 - 1; x++) {
				d.set(x, base, iz1, wood.full());
				d.set(x, base + 1, iz1, wood.slab(SlabType.BOTTOM));
				if (iz1 - 1 > pz + 1) {
					d.set(x, base, iz1 - 1, wood.slab(SlabType.BOTTOM));
				}
			}
		}
		for (int x = x0 + 1; x <= x1 - 1; x += 2) {
			d.poi(PoiType.SEAT, x, base, iz1 - 1, Dirs.FRONT);
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			int sx = x0 + 1;
			int sz = pz + 1;
			d.set(sx, base, sz, States.facing(Blocks.FURNACE, Dirs.RIGHT).setValue(BlockStateProperties.LIT, true));
			d.set(sx, base + 1, sz, Blocks.COBBLESTONE_WALL.defaultBlockState());
			d.set(sx + 1, base, sz, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		}
		// the changing room: benches, pegs, towels
		Baths.bench(d, x0 + 1, base, z0 + 1, Dirs.BACK, 2, Dirs.RIGHT);
		d.poi(PoiType.MEETING_POINT, cx, base, z0 + 2, Dirs.BACK);
		Furniture.chest(d, x1 - 1, base, z0 + 1, Dirs.LEFT, List.of(new Object[]{"minecraft:white_wool", 12}, new Object[]{"minecraft:birch_log", 4}));
		Halls.lanternUnder(d, cx, base, z0 + 2, 1);
		// the plunge pool and the woodpile outside
		if (z1 + 3 < dp) {
			Baths.pool(d, cx - 1, z1 + 2, cx + 1, dp - 2, 0, 2);
		}
		Garden.woodpile(d, x0 - 1 >= 0 ? x0 - 1 : x1 + 1, 0, z0 + 1, Dirs.BACK, 3);
		if (top.roof() != null) {
			dev.voidmc.hearth.build.kit.Chimneys.stack(d, x0 + 1, pz + 1, base + 2, top.roof(), top.roof().ridgeY);
		}
		if (h.snow() || d.site().snowy()) {
			Garden.snowBanks(d, x0, z0, x1, z1, base, cx);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, top.ridge() + 3);
	}
}
