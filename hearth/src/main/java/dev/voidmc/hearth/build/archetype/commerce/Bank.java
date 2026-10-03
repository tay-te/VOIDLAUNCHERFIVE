package dev.voidmc.hearth.build.archetype.commerce;

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
import dev.voidmc.hearth.build.kit.Floors;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.civic.Adobe;
import dev.voidmc.hearth.build.kit.civic.Fam;
import dev.voidmc.hearth.build.kit.civic.Grounds;
import dev.voidmc.hearth.build.kit.civic.Halls;
import dev.voidmc.hearth.build.kit.civic.Portico;
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
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:commerce/bank}: the mint and bank, the most solid building on the square. A ground floor of dressed and
 * rusticated stone whatever the town builds in (barred windows, quoins, a string course), the culture's walls above
 * with arched windows, a low roof (a flat parapeted one with a little dome in the desert, a curved one in the karst);
 * before the door a portico of columns under a pediment in towns and cities, a heavy arched doorway in villages, the
 * town's gold banner over it. Inside, the banking hall with benches for customers and the counter with its grilles, the
 * minter's place behind it; the strongroom behind an iron door with its chests and bullion; upstairs the coin press
 * room with the furnace and the anvil, and the ledgers.
 *
 * <p>POIs: COUNTER and MARKET_STALL at the counter with the MINTER's JOB_SITE behind it, a second MINTER JOB_SITE at the
 * press upstairs, SEATs, STORAGE, LECTERN (the ledger), DOOR.
 *
 * <p>Params: {@code portico} (boolean, default by tier), {@code variant} (int).
 */
public final class Bank implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("commerce/bank"), ArchetypeCategory.COMMERCE, Set.of(JobType.MINTER), 0,
		9, 13, 9, 13, 9, 16, SettlementTier.VILLAGE, Set.of(), Set.of(ArchetypeInfo.SiteFlag.CENTRAL), List.of(), 1, 1.0);

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
		boolean portico = d.param("portico", tier >= 3 && w >= 9) && dp >= 10;
		boolean deepPortico = portico && dp >= 12;

		// --- the plan
		int x0 = w >= 11 ? 1 : 0;
		int x1 = w - 1 - x0;
		int cx = (x0 + x1) / 2;
		int zF = deepPortico ? 3 : portico ? 2 : raised ? 3 : 2;
		int z0 = zF;
		int z1 = dp - 1;
		int h0 = dp - zF >= 9 ? 5 : 6;
		int h1 = maxH >= 13 ? 4 : 3;
		boolean twoStoreys = dp - zF >= 9;
		int base = raised ? Grounds.stiltBase(d, x0, z0, x1, z1) : Grounds.raisedBase(d, x0, x1, z0);
		Body body = new Body(x0, z0, x1, z1, base, twoStoreys ? new int[]{h0, h1} : new int[]{h0}, false, false);
		d.body(x0, z0, x1, z1);
		int plate = body.plateY();
		d.eave(plate);
		int fy = body.floorY(1);

		// --- shell: a stone ground floor, the culture's walls above
		Storeys.ground(d, body, raised);
		Grounds.forecourt(d, 0, w - 1, z0 - 1, !raised && !portico);
		Facade fac = Facade.plan(d, body, cx, 1, 1, 0.8);
		Storeys.walls(d, body, fac, 0, body.storeys() - 1, null, true);
		Storeys.stoneStorey(d, body, 0);
		d.carve(x0 + 1, base, z0 + 1, x1 - 1, base + h0 - 2, z1 - 1);
		// rustication: the ground floor's courses grooved every other row (a darker course of the plinth stone)
		Mat found = d.mat(Role.FOUNDATION);
		for (Side s : body.sides(0)) {
			for (int i = 1; i < s.last(); i++) {
				for (int y = base + 1; y < base + h0 - 2; y += 2) {
					if (Openings.isWallBlock(d, s.x(i), y, s.z(i)) && i != 1 && i != s.last() - 1) {
						int fx = s.x(i);
						int fz = s.z(i);
						int fyy = y;
						d.inStage(StageKind.WALLS, () -> d.set(fx, fyy, fz, found.full()));
					}
				}
			}
		}
		// windows: barred slits below, arched windows above
		if (twoStoreys) {
			Storeys.windows(d, body, fac, 1, 1, Math.min(2, h1 - 2));
		}
		Side front = body.side(0, Dirs.FRONT);
		for (Side s : body.sides(0)) {
			for (Facade.Opening o : fac.openings(s.out())) {
				if (o.kind() != Facade.Kind.WINDOW || !s.contains(o.at()) || s.isCorner(s.index(o.at()))) {
					continue;
				}
				int x = s.x(s.index(o.at()));
				int z = s.z(s.index(o.at()));
				if (s.out() == Dirs.FRONT && Math.abs(x - cx) <= (portico ? 3 : 1)) {
					continue;
				}
				d.inStage(StageKind.OPENINGS, () -> {
					d.set(x, base + 2, z, Blocks.IRON_BARS.defaultBlockState());
					d.set(x, base + 3, z, Blocks.IRON_BARS.defaultBlockState());
				});
			}
		}
		// the door of state: double doors under an arch, or a portico of columns
		Openings.door(d, front, cx, base, 1, new Openings.DoorOpts(false, false, true));
		Mat accent = d.mat(Role.WALL_ACCENT);
		try (var s = d.stage(StageKind.WALLS)) {
			d.set(cx - 1, base + 2, z0, accent.stairsOrSlab(Dirs.LEFT, Half.TOP));
			d.set(cx + 1, base + 2, z0, accent.stairsOrSlab(Dirs.RIGHT, Half.TOP));
			d.set(cx, base + 3, z0, d.mat(Role.ACCENT).mainOr(Form.FULL, Blocks.CHISELED_STONE_BRICKS).defaultBlockState());
		}
		int skyline = plate;
		Signs.Arms arms = new Signs.Arms("yellow", List.of(new String[]{"circle", "orange"}, new String[]{"border", "black"}));
		if (portico) {
			int px0 = cx - (w >= 13 ? 4 : 3);
			int px1 = cx + (w >= 13 ? 4 : 3);
			int pz0 = deepPortico ? z0 - 2 : z0 - 1;
			skyline = Math.max(skyline, Portico.build(d, t, Math.max(0, px0), Math.min(w - 1, px1), pz0, z0 - 1, base, h0 - 1, t != Tradition.KARST));
			if (base >= 1 && !raised && pz0 - 1 >= 0) {
				Grounds.flight(d, px0, px1, pz0 - 1, base, base);
			}
		} else if (!raised && z0 - 1 >= 1 && x1 - x0 >= 8) {
			// a smaller bank still has its door of state: two columns and a pediment framing it (an aedicule)
			skyline = Math.max(skyline, Portico.build(d, t, cx - 2, cx + 2, z0 - 1, z0 - 1, base, Math.min(4, h0 - 1), t != Tradition.KARST));
			if (base >= 1) {
				Grounds.flight(d, cx - 2, cx + 2, z0 - 2, base, base);
			}
		} else {
			Halls.portal(d, front, cx, base, t, tier);
			if (base >= 1 && !raised) {
				Grounds.flight(d, cx - 1, cx + 1, z0 - 1, base, base);
			}
		}
		Grounds.approach(d, cx, base, z0 - 1);
		// the gold banner over the door
		int by = twoStoreys ? fy + 1 : base + 4;
		if (Openings.isWallBlock(d, cx, by, z0) && d.isEmpty(cx, by, z0 - 1) && (z0 - 1 >= 0 || by >= 3)) {
			Signs.wallBanner(d, cx, by, z0 - 1, Dirs.FRONT, arms.field(), arms.layers());
		}

		// --- the roof: low and heavy, a string course under it
		Halls.stringCourse(d, body.side(body.storeys() - 1, Dirs.FRONT), plate);
		if (t != Tradition.KARST && t != Tradition.STILT && t != Tradition.ADOBE) {
			Halls.cornice(d, body.topRect(), plate);
		}
		// a bank's roof is low and hipped behind its cornice, whatever the houses round it do
		RoofForm form = t == Tradition.ADOBE ? RoofForm.FLAT : t == Tradition.KARST ? RoofForm.PAGODA : RoofForm.HIP;
		Storeys.Top top;
		if (t == Tradition.ADOBE) {
			// a parapet crested over the door, the beam ends in a row; latticed screens before the upper windows
			int[] tr = body.topRect();
			int deck = Adobe.roof(d, tr[0], tr[1], tr[2], tr[3], plate, plate + 3 <= maxH - 1, true);
			top = new Storeys.Top(null, deck, deck + 2);
			if (twoStoreys) {
				Adobe.screens(d, body, 1);
			}
		} else {
			top = Storeys.roof(d, body, form, Direction.Axis.X, Math.min(1.0, Math.max(0.5, h.roof().pitch())), 1);
		}
		skyline = Math.max(skyline, top.ridge());
		if (form == RoofForm.FLAT) {
			// a little dome on a drum of lights over the strongroom
			int dz = Math.min(z1 - 3, Math.max(z0 + 3, (z0 + z1) / 2 + 1));
			if (top.deck() + 6 <= maxH - 1 && x1 - x0 >= 6) {
				skyline = Math.max(skyline, Adobe.dome(d, cx, dz, top.deck(), 1, maxH));
			}
		}

		// --- inside: the banking hall, the counter, the strongroom, the stair
		int ix0 = x0 + 1;
		int ix1 = x1 - 1;
		int iz0 = z0 + 1;
		int iz1 = z1 - 1;
		int counterZ = iz0 + 2;
		// the counter across the hall with grilles over it, the minter's table at its end, a gap at the other
		boolean gapLeft = d.chance(0.5);
		Mat wood = d.mat(Role.FURNITURE);
		int gap = gapLeft ? ix0 : ix1;
		int desk = gapLeft ? ix1 : ix0;
		for (int x = ix0; x <= ix1; x++) {
			if (x == gap) {
				continue;
			}
			if (x == desk) {
				Furniture.workstation(d, JobType.MINTER, x, base, counterZ, Dirs.BACK);
				continue;
			}
			try (var s = d.stage(StageKind.INTERIOR)) {
				d.set(x, base, counterZ, wood.has(Form.STAIRS) ? wood.stairsOrSlab(Dirs.BACK, Half.TOP) : wood.slab(SlabType.TOP));
				if ((x - ix0) % 2 == 1) {
					d.set(x, base + 1, counterZ, Blocks.IRON_BARS.defaultBlockState());
				} else if ((x - ix0) % 4 == 0) {
					d.set(x, base + 1, counterZ, Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.LIT, true));
				}
			}
		}
		int mx = cx == gap || cx == desk ? cx + (gapLeft ? 1 : -1) : cx;
		d.poi(PoiType.COUNTER, mx, base, counterZ + 1, Dirs.FRONT);
		d.poi(PoiType.MARKET_STALL, mx, base, counterZ - 1, Dirs.BACK);
		// the strongroom in a back corner
		int vw = Math.min(4, (ix1 - ix0 + 1) / 2);
		boolean vaultLeft = !gapLeft;
		int vx0 = vaultLeft ? ix0 : ix1 - vw + 1;
		int vx1 = vaultLeft ? ix0 + vw - 1 : ix1;
		int vz0 = iz1 - 1;
		if (vz0 - 1 > counterZ + 1) {
			strongroom(d, vx0, vz0 - 1, vx1, iz1 + 1, base, vaultLeft);
		}
		int[] land = null;
		int sx = vaultLeft ? ix1 : ix0;
		int sz = iz1;
		if (twoStoreys) {
			land = Storeys.flight(d, body, 0, sx, sz, Dirs.FRONT);
		}
		// benches for the customers, lanterns
		for (int x = ix0; x <= ix1; x += 3) {
			if (x == cx || !d.isEmpty(x, base, iz0) || x == gap) {
				continue;
			}
			Furniture.chair(d, x, base, iz0, Dirs.FRONT);
		}
		Halls.lanternUnder(d, cx, base, iz0 + 1, 2);
		Halls.lanternUnder(d, cx, base, counterZ + 2, 2);
		if (twoStoreys) {
			Floors.joists(d, ix0, iz0, ix1, iz1, fy - 1, Direction.Axis.Z, 2, base);
		}

		// --- the press room: upstairs, or behind the counter in a small bank
		int[] up = twoStoreys ? body.rect(1) : body.rect(0);
		int py = twoStoreys ? fy : base;
		Rooms.Room press = new Rooms.Room(up[0] + 1, twoStoreys ? up[1] + 1 : counterZ + 1, up[2] - 1, up[3] - 1, py, twoStoreys ? h1 - 1 : h0 - 1);
		if (land != null) {
			press.keep(land[0], land[1]);
			press.keepArea(sx, sz - h0 + 1, sx, sz);
		}
		int ax = vaultLeft ? up[2] - 1 : up[0] + 1;
		Direction in = vaultLeft ? Dirs.LEFT : Dirs.RIGHT;
		int az = twoStoreys ? up[1] + 1 : counterZ + 2;
		if (d.isEmpty(ax, py, az) && d.isEmpty(ax, py, az + 1) && az + 1 < up[3]) {
			try (var s = d.stage(StageKind.INTERIOR)) {
				d.set(ax, py, az, States.facing(Blocks.BLAST_FURNACE, in));
				d.set(ax, py, az + 1, States.facing(Blocks.ANVIL, Dirs.FRONT));
			}
			press.block(ax, az);
			press.block(ax, az + 1);
		}
		if (twoStoreys) {
			int lx = vaultLeft ? up[0] + 1 : up[2] - 1;
			int lz = up[1] + 1;
			if (d.isEmpty(lx, py, lz)) {
				d.inStage(StageKind.INTERIOR, () -> d.set(lx, py, lz, States.facing(Blocks.LECTERN, vaultLeft ? Dirs.RIGHT : Dirs.LEFT),
					BlockEntities.lectern("Ledger", "The Minter", List.of("Deposits and loans."))));
				d.poi(PoiType.LECTERN, lx + (vaultLeft ? 1 : -1), py, lz, vaultLeft ? Dirs.LEFT : Dirs.RIGHT);
				press.block(lx, lz);
			}
			Rooms.furnish(d, press, Rooms.Type.STORAGE, 0, JobType.NONE);
			Halls.lanternUnder(d, cx, fy, (up[1] + up[3]) / 2, 1);
		}

		// --- outside: lamps by the steps, a bench
		if (!raised) {
			Halls.lamp(d, Math.max(0, x0 - 1 + 1), 0, 0, 2);
			Halls.lamp(d, Math.min(w - 1, x1), 0, 0, 2);
		}
		if (h.snow()) {
			Garden.snowBanks(d, x0, z0, x1, z1, base, cx);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, skyline + 2);
	}

	/**
	 * The strongroom: walls of the plinth stone round {@code x0..x1 × z0..z1} (its own walls inside the bank's), an iron
	 * door with a button either side, chests of coin and bullion blocks on the floor, a lantern.
	 */
	static void strongroom(Draft d, int x0, int z0, int x1, int z1, int y, boolean left) {
		Mat stone = d.mat(Role.PLINTH);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : dev.voidmc.hearth.build.draft.Draft.ring(x0, z0, x1, z1)) {
				for (int yy = y; yy < y + 3; yy++) {
					if (d.isEmpty(c[0], yy, c[1])) {
						d.set(c[0], yy, c[1], stone.full());
					}
				}
			}
			// its own ceiling
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					if (d.isEmpty(x, y + 3, z)) {
						d.set(x, y + 3, z, stone.full());
					}
				}
			}
		}
		// the door in its front wall, toward the inner side
		int dx = left ? x1 - 1 : x0 + 1;
		d.inStage(StageKind.OPENINGS, () -> d.door(dx, y, z0, Blocks.IRON_DOOR, Dirs.BACK, DoorHingeSide.LEFT));
		try (var s = d.stage(StageKind.INTERIOR)) {
			int bx = left ? dx - 1 : dx + 1;
			d.set(bx, y + 1, z0 - 1, States.attached(Blocks.STONE_BUTTON, AttachFace.WALL, Dirs.FRONT));
			d.set(bx, y + 1, z0 + 1, States.attached(Blocks.STONE_BUTTON, AttachFace.WALL, Dirs.BACK));
			// chests and bullion inside (kept off the folk's lists: the strongroom is for the minter's key alone)
			int cx = left ? x0 + 1 : x1 - 1;
			d.set(cx, y, z1 - 1, States.facing(Blocks.CHEST, Dirs.FRONT), BlockEntities.container(List.of(new Object[]{"minecraft:gold_ingot", 16},
				new Object[]{"minecraft:emerald", 12}, new Object[]{"minecraft:gold_nugget", 32})));
			if (x1 - x0 >= 3) {
				d.set(left ? cx + 1 : cx - 1, y, z1 - 1, Blocks.GOLD_BLOCK.defaultBlockState());
			}
			if (z1 - z0 >= 3) {
				d.set(cx, y, z1 - 2, States.facing(Blocks.BARREL, Direction.UP));
			}
			d.set(cx, y + 1, z1 - 1, Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.LIT, true));
		}
	}
}
