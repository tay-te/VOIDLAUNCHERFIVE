package dev.voidmc.hearth.build.archetype.commerce;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.industry.Cabin;
import dev.voidmc.hearth.build.kit.industry.Carts;
import dev.voidmc.hearth.build.kit.industry.Pois;
import dev.voidmc.hearth.build.kit.industry.Works;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Lifts;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.kit.trade.Shed;
import dev.voidmc.hearth.build.kit.trade.Signs;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * {@code hearth:commerce/caravan_yard}: where the caravans put up by the town gate. A broad gateway under a beam with
 * the yard's sign opens from the road into a walled or fenced yard of trodden earth; inside it the hitching rail where
 * the teams are tied, wagons drawn up in a row, covered with their canvas tilts or loaded open with barrels, sacks and
 * hay. Along the back the warehouse, its cart doors wide, a hoist over them, bales and barrels on the apron; down one
 * side the pens for the horses, mules and oxen with their troughs and hay racks under a lean-to; at the front corner
 * the trader's booth, a counter at its window where loads are reckoned and dues paid, the ledger on its lectern.
 *
 * <p>Job sites: TRADER at the booth's counter and the warehouse's goods; HAULER at the wagons and the apron. Storage:
 * the warehouse's chests and barrels. Hitching posts at the rail and in the pens; the gate; a counter.
 *
 * <p>Params: {@code roof}, {@code variant}.
 */
public final class CaravanYard implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("commerce/caravan_yard"), ArchetypeCategory.COMMERCE,
		Set.of(JobType.TRADER, JobType.HAULER), 0, 15, 21, 15, 21, 7, 11, SettlementTier.TOWN, Set.of(), Set.of(), List.of(), 1, 1.0);

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.HIP, RoofForm.GAMBREL, RoofForm.PAGODA, RoofForm.FLAT};

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		Pois pois = new Pois();
		int w = d.frontage();
		int dp = d.depth();
		int ex = Yard.entrance(d);
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		Yard.trodden(d, 0, 0, w - 1, dp - 1, 0);
		boolean pensLeft = d.chance(0.5);
		Direction toPens = pensLeft ? Dirs.LEFT : Dirs.RIGHT;
		RoofForm form = Hall.pick(d, FORMS);

		// the warehouse along the back
		int wd = dp >= 18 ? 7 : 6;
		int wx0 = pensLeft ? 5 : 1;
		int wx1 = pensLeft ? w - 2 : w - 6;
		int wz1 = dp - 1;
		int wz0 = wz1 - wd + 1;
		int doorCol = (wx0 + wx1) / 2;
		int[] heights = Hall.budget(d, wx0, wz0, wx1, wz1, 0, new int[]{5}, true, form, 1, Direction.Axis.X);
		Hall store = Hall.of(d, wx0, wz0, wx1, wz1, 0, heights, false, false);
		store.facade(-1, 1, 1, 0.3).ground(false).shell().openings(false);
		int bayW = 3;
		int bay = doorCol - 1;
		store.bay(Dirs.FRONT, bay, bayW, 3);
		d.poi(PoiType.DOOR, doorCol, 0, wz0 - 1, Dirs.BACK);
		store.roof(form, Direction.Axis.X, 1, null);
		int top = store.top;
		warehouse(d, pois, store, bay, bayW);
		// the hoist over the cart doors
		Side front = store.side(store.storeys() - 1, Dirs.FRONT);
		if (store.roof != null && store.storeys() >= 2) {
			int hy = store.floorY(store.storeys() - 1) + 1;
			Lifts.hoist(d, front, front.index(doorCol), hy, 1, 3, Blocks.HAY_BLOCK.defaultBlockState());
		}
		// bales and barrels on the apron either side of the doors
		Props.crates(d, bay - 2, 0, wz0 - 1, Dirs.LEFT, 2);
		Props.crates(d, bay + bayW + 1, 0, wz0 - 1, Dirs.RIGHT, 2);
		pois.siteIf(d, doorCol, 0, wz0 - 2, Dirs.BACK, JobType.HAULER);

		// the pens down one side: fenced, a shelter over the back, troughs and hay
		int px0 = pensLeft ? 0 : w - 4;
		int px1 = px0 + 3;
		int pz0 = 2;
		int pz1 = dp - 1;
		pens(d, pois, px0, pz0, px1, pz1, pensLeft);

		// the trader's booth at the front corner opposite the pens
		int bx0 = pensLeft ? w - 5 : 0;
		int bx1 = bx0 + 4;
		int bz0 = 1;
		int bz1 = 5;
		Hall booth = Cabin.build(d, bx0, bz0, bx1, bz1, 4, pensLeft ? Dirs.LEFT : Dirs.RIGHT, (bz0 + bz1) / 2, Hall.pick(d, RoofForm.HIP, RoofForm.GABLE,
			RoofForm.PAGODA, RoofForm.FLAT), Direction.Axis.Z, false);
		top = Math.max(top, booth.top);
		booth(d, pois, booth, pensLeft);

		// the wagons drawn up behind the booth and between the way in and the pens, poles toward the gate
		int k = 0;
		int wz = bz1 + 2;
		if (wz + 4 <= wz0 - 2) {
			int wx = pensLeft ? bx0 + 1 : bx1 - 2;
			Carts.wagon(d, wx, wz + 1, Dirs.BACK, true, Carts.canvas(d, k++));
			pois.siteIf(d, pensLeft ? wx - 2 : wx + 3, 0, wz + 2, pensLeft ? Dirs.RIGHT : Dirs.LEFT, JobType.HAULER);
		}
		int mx0 = pensLeft ? px1 + 2 : ex + 3;
		int mx1 = pensLeft ? ex - 3 : px0 - 2;
		if (mx1 - mx0 + 1 >= 4 && wz0 - 2 - 3 >= 4) {
			int wx = mx0 + 1;
			Carts.wagon(d, wx, wz0 - 6, Dirs.BACK, d.chance(0.5), Carts.canvas(d, k++));
			pois.siteIf(d, wx + (pensLeft ? 3 : -2), 0, wz0 - 5, pensLeft ? Dirs.LEFT : Dirs.RIGHT, JobType.HAULER);
		}
		// the hitching rail along the pens' fence on the yard side, the teams' places before it
		int railX = pensLeft ? px1 + 1 : px0 - 1;
		int posts = 0;
		int rz0 = pz0 + 1;
		int rz1 = Math.min(wz0 - 3, rz0 + 6);
		if (rz1 - rz0 >= 2 && Math.abs(railX - ex) > 1) {
			Carts.rail(d, railX, rz0, Dirs.BACK, rz1 - rz0 + 1);
			for (int z = rz0 + 1; z <= rz1; z += 2) {
				int sx = railX + (pensLeft ? 1 : -1);
				if (Pois.standable(d, sx, 0, z)) {
					pois.poi(PoiType.HITCHING_POST, sx, 0, z, pensLeft ? Dirs.LEFT : Dirs.RIGHT);
					posts++;
				}
			}
		}

		// the gateway: two posts and a beam over the way in, the sign hanging from it, the wall or fence round the yard
		gate(d, ex);
		List<int[]> gaps = new ArrayList<>();
		for (int x = ex - 1; x <= ex + 1; x++) {
			gaps.add(new int[]{x, 0});
		}
		Yard.enclose(d, 0, 0, w - 1, dp - 1, gaps);
		d.poi(PoiType.GATE, ex, 0, 0, Dirs.BACK);
		Works.lampPost(d, ex + 2, 0, 2);
		Works.lampPost(d, ex - 2, 0, wz0 - 3);
		// the yard's well-trodden way from the gate to the doors
		List<int[]> way = new ArrayList<>();
		for (int z = 0; z < wz0; z++) {
			way.add(new int[]{ex, z});
		}
		Garden.path(d, way, 0);
		if (posts == 0) {
			pois.poi(PoiType.HITCHING_POST, ex + 1, 0, 2, Dirs.FRONT);
		}
		Works.keepDoors(d);
		pois.commit(d, INFO.jobs().toArray(JobType[]::new), new int[]{ex, 0, 2}, new int[]{ex, 0, 3});
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, top + 1));
	}

	/** Inside the warehouse: barrel racks and crates along the walls, the chests of goods, the trader's tally. */
	private static void warehouse(Draft d, Pois pois, Hall h, int bay, int bayW) {
		Floorplan fp = h.floor(0).blockHoles(d);
		int[] in = h.inner(0);
		for (int c = bay; c < bay + bayW; c++) {
			fp.keep(c, in[1]);
		}
		int racks = 0;
		for (int[] c : fp.along(Dirs.BACK)) {
			if (racks < 6 && fp.take(c[0], c[1], null)) {
				Props.barrelRack(d, c[0], 0, c[1], Dirs.RIGHT, 1, 2, Dirs.FRONT);
				racks++;
			}
		}
		boolean chest = false;
		for (Direction wall : new Direction[]{Dirs.LEFT, Dirs.RIGHT}) {
			for (int[] c : fp.along(wall)) {
				if (c[1] == in[1] || c[1] == in[3]) {
					continue;
				}
				if (fp.take(c[0], c[1], wall.getOpposite())) {
					chest |= pois.store(d, c[0], 0, c[1], wall.getOpposite(), true, Works.items("minecraft:wheat", 64, "minecraft:leather", 16,
						"minecraft:iron_ingot", 16, "minecraft:white_wool", 32));
					break;
				}
			}
		}
		for (int[] c : fp.along(Dirs.LEFT)) {
			if (fp.take(c[0], c[1], Dirs.RIGHT)) {
				pois.site(c[0] + 1, 0, c[1], Dirs.LEFT, JobType.TRADER);
				Props.crates(d, c[0], 0, c[1], Dirs.BACK, 1);
				break;
			}
		}
		Lighting.room(d, fp.room());
	}

	/** The pens: fenced runs with gates to the yard, a lean-to over the back, hay racks and troughs. */
	private static void pens(Draft d, Pois pois, int x0, int z0, int x1, int z1, boolean left) {
		Mat fence = Works.timber(d);
		int gateX = left ? x1 : x0;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = z0; z <= z1; z++) {
				boolean gate = (z - z0) % 5 == 2;
				if (gate && fence.has(Form.FENCE_GATE)) {
					d.set(gateX, 0, z, fence.gate(left ? Dirs.RIGHT : Dirs.LEFT, false));
				} else if (fence.has(Form.FENCE)) {
					d.set(gateX, 0, z, fence.fence());
				}
			}
			for (int x = x0; x <= x1; x++) {
				if (fence.has(Form.FENCE) && d.isEmpty(x, 0, z0)) {
					d.set(x, 0, z0, fence.fence());
				}
			}
			// dividers between the runs
			for (int z = z0 + 5; z < z1; z += 5) {
				for (int x = x0; x <= x1; x++) {
					if (x != gateX && fence.has(Form.FENCE) && d.isEmpty(x, 0, z)) {
						d.set(x, 0, z, fence.fence());
					}
				}
			}
		}
		// a shelter of posts and a roof over the back of the pens
		int lz0 = Math.max(z0 + 1, z1 - 4);
		Shed.open(d, x0, lz0, x1, z1, 0, 3, Hall.pick(d, RoofForm.GABLE, RoofForm.HIP, RoofForm.PAGODA), Direction.Axis.Z, 1);
		// troughs and hay
		int inner = left ? x0 + 1 : x1 - 1;
		for (int z = z0 + 1; z < z1; z += 5) {
			if (d.isEmpty(inner, 0, z)) {
				Props.trough(d, inner, 0, z, Dirs.BACK, 1);
			}
			if (d.isEmpty(inner, 0, z + 1)) {
				try (var s = d.stage(StageKind.EXTERIOR)) {
					d.set(inner, 0, z + 1, Blocks.HAY_BLOCK.defaultBlockState());
				}
			}
			int hx = left ? x0 + 2 : x1 - 2;
			if (Pois.standable(d, hx, 0, z + 2)) {
				pois.poi(PoiType.HITCHING_POST, hx, 0, z + 2, left ? Dirs.LEFT : Dirs.RIGHT);
			}
		}
		// a lamp under the shelter, so the beasts' corner is never dark
		Lighting.room(d, x0, lz0, x1, z1, 0);
	}

	/** The booth: a counter at the window onto the yard, the ledger on its lectern, a strongbox, a stool. */
	private static void booth(Draft d, Pois pois, Hall h, boolean pensLeft) {
		Floorplan fp = h.floor(0).blockHoles(d);
		int[] in = h.inner(0);
		int doorZ = (in[1] + in[3]) / 2;
		int doorX = pensLeft ? in[0] : in[2];
		fp.keep(doorX, doorZ);
		// the lectern with the ledger at the back
		for (int[] c : fp.along(Dirs.BACK)) {
			if (fp.take(c[0], c[1], Dirs.FRONT)) {
				pois.station(d, Blocks.LECTERN, c[0], 0, c[1], Dirs.FRONT, JobType.TRADER);
				break;
			}
		}
		for (int[] c : fp.along(Dirs.FRONT)) {
			if (fp.take(c[0], c[1], Dirs.BACK)) {
				pois.store(d, c[0], 0, c[1], Dirs.BACK, true, Works.items("minecraft:paper", 32, "minecraft:emerald", 4));
				pois.poi(PoiType.COUNTER, c[0], 0, c[1] + 1, Dirs.FRONT);
				break;
			}
		}
		Lighting.room(d, fp.room());
	}

	/** The gateway: two posts either side of the way in, a beam over them and a little gabled roof along it, the sign hung under it. */
	private static void gate(Draft d, int ex) {
		Mat post = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat roof = d.mat(Role.ROOF);
		int h = Math.min(4, d.maxHeight() - 3);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int x : new int[]{ex - 2, ex + 2}) {
				if (!Works.in(d, x, 0)) {
					continue;
				}
				for (int y = 0; y < h; y++) {
					d.set(x, y, 0, post.log(Direction.Axis.Y));
				}
				// raking braces either side of each post
				if (Works.in(d, x, 1) && d.isEmpty(x, h - 1, 1) && beam.has(Form.STAIRS)) {
					d.set(x, h - 1, 1, beam.stairs(Dirs.FRONT, net.minecraft.world.level.block.state.properties.Half.TOP));
				}
			}
			for (int x = ex - 3; x <= ex + 3; x++) {
				if (Works.in(d, x, 0)) {
					d.set(x, h, 0, beam.log(Direction.Axis.X));
				}
			}
		}
		try (var s = d.stage(StageKind.ROOF)) {
			for (int x = ex - 3; x <= ex + 3; x++) {
				if (!Works.in(d, x, 0)) {
					continue;
				}
				if (d.allowed(x, h + 1, -1)) {
					d.set(x, h + 1, -1, roof.stairsOrSlab(Dirs.BACK, net.minecraft.world.level.block.state.properties.Half.BOTTOM));
				}
				d.set(x, h + 1, 1, roof.stairsOrSlab(Dirs.FRONT, net.minecraft.world.level.block.state.properties.Half.BOTTOM));
				d.set(x, h + 1, 0, roof.full());
				if (roof.has(Form.SLAB)) {
					d.set(x, h + 2, 0, roof.slab(net.minecraft.world.level.block.state.properties.SlabType.BOTTOM));
				}
			}
		}
		Signs.hang(d, ex, h - 1, 0, Dirs.FRONT, "caravan_yard", "Caravans");
		Props.hangLantern(d, ex, h - 1, 1);
	}
}
