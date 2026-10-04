package dev.voidmc.hearth.build.archetype.commerce;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Chimneys;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Floors;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Porches;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.civic.Adobe;
import dev.voidmc.hearth.build.kit.civic.Arches;
import dev.voidmc.hearth.build.kit.civic.Grounds;
import dev.voidmc.hearth.build.kit.civic.Halls;
import dev.voidmc.hearth.build.kit.civic.Signs;
import dev.voidmc.hearth.build.kit.civic.Storeys;
import dev.voidmc.hearth.build.kit.civic.Trade;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:commerce/inn}: the coaching inn on the main road, the broadest front in the street. Its range runs the
 * whole width of the plot, two storeys over a lofty ground floor (three where the plot is broad and the sky allows), the
 * ground floor in stone wherever the culture builds in timber, the upper storeys jettied over the street in the timber
 * towns; its long roof carries a cross gable over the tap-room door, dormers and a stack at each gable end. Through one
 * side of the ground floor runs the carriage arch (voussoirs and keystone, guard stones at its foot, the yard gates
 * folded back against the passage walls, a lantern in the vault) to the stable yard behind. Beside it the tap room shows
 * itself to the street: tall windows, a bay, the door under the inn's painted sign hung from a wrought bracket, its name
 * on a board along the front, a settle, casks of the house's ale and a barrel table outside; within, the bar with its
 * kegs racked behind, tables and chairs, the great hearth, lanterns overhead. Upstairs the guest rooms open off a
 * corridor along the back, the gallery looks over the yard, and the stable stands across the yard with its stalls,
 * hay, trough and loft.
 *
 * <p>By culture: alpine and karst inns carry a balcony across the upper front; the dry south a flat roof with a shaded
 * terrace, latticed screens, a striped awning and a palm in the yard; the mediterranean a vine pergola over the front
 * terrace; the bayou and the tropics raise it on stilts with a stair to the street and no carriage arch.
 *
 * <p>POIs: COUNTER and MARKET_STALL at the bar and the INNKEEPER's JOB_SITE behind it, SEATs and TABLEs, the HEARTH,
 * BEDs, STORAGE, the DOOR.
 *
 * <p>Params:
 * <ul>
 *   <li>{@code arch_right} (boolean): the carriage arch on the right (seen from the street) (default by seed)</li>
 *   <li>{@code name} (int): which of the inn names the sign shows (default by seed)</li>
 *   <li>{@code storeys} (int, 2–3): storeys including the ground floor (default 3 where the plot is 13+ wide and 13+
 *   deep and the height allows, else 2)</li>
 *   <li>{@code variant} (int): the style variant</li>
 * </ul>
 */
public final class Inn implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("commerce/inn"), ArchetypeCategory.COMMERCE, Set.of(JobType.INNKEEPER), 4,
		11, 15, 11, 17, 11, 22, SettlementTier.VILLAGE, Set.of(), Set.of(), List.of(), 2, 1.0);

	/** Inn names: a translation key suffix and the English of the sign's two lines. */
	public static final String[][] NAMES = {
		{"red_lion", "The Red", "Lion"}, {"golden_goose", "The Golden", "Goose"}, {"green_man", "The Green", "Man"},
		{"wheatsheaf", "The", "Wheatsheaf"}, {"plough", "The", "Plough"}, {"bell", "The Bell", "Inn"},
		{"crown", "The Crown", "Inn"}, {"white_hart", "The White", "Hart"}, {"rising_sun", "The Rising", "Sun"},
		{"black_horse", "The Black", "Horse"}, {"three_barrels", "Three", "Barrels"}, {"lantern", "The Lantern", "Rest"},
		{"pilgrims", "Pilgrims'", "Rest"}, {"crossed_keys", "The Crossed", "Keys"}, {"blue_boar", "The Blue", "Boar"},
		{"traveller", "Traveller's", "Rest"}};

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int maxH = d.maxHeight();
		Habits h = d.habits();
		Tradition t = Tradition.of(d);
		boolean flat = t.flatRoofed();
		boolean karst = t == Tradition.KARST;
		boolean arch = !t.raised();
		boolean timberTown = t == Tradition.TIMBER || t == Tradition.LOG;

		// --- the plan: the range across the whole front, a forecourt before it, the yard behind
		int x0 = 0;
		int x1 = w - 1;
		int md = dp >= 13 ? 8 : 7;
		int zF;
		if (t.raised()) {
			zF = Math.min(dp - md, 1 + Grounds.stiltBase(d, x0, 1, x1, md));
		} else {
			// two rows of forecourt for the settle, the casks and the sign's bracket where the yard keeps three rows
			zF = dp - md - 2 >= 3 || !Grounds.gentle(d, x0, -1, x1, 0, 0) ? 2 : 1;
		}
		int zM = zF + md - 1;
		int h0 = md == 8 ? 5 : 4;
		boolean broad = w >= 13 && md == 8 && !flat && !t.raised();
		// three storeys where the sky leaves room for a whole-pitch roof over them
		int storeys = Math.clamp(d.param("storeys", broad && maxH >= h0 + 8 + 8 ? 3 : 2), 2, 3);
		if (storeys == 3 && (maxH < h0 + 8 + 5 || md < 8)) {
			storeys = 2;
		}
		boolean archRight = d.param("arch_right", d.chance(0.5));
		// the arch three wide inside a side wall; the tap room on the other side of a partition
		int px0 = archRight ? x1 - 3 : x0 + 1;
		int px1 = px0 + 2;
		int part = archRight ? px0 - 1 : px1 + 1;
		int tx0 = arch ? (archRight ? x0 + 1 : part + 1) : x0 + 1;
		int tx1 = arch ? (archRight ? part - 1 : x1 - 1) : x1 - 1;
		int base = t.raised() ? Grounds.stiltBase(d, x0, zF, x1, zM) : 0;
		int[] heights = storeys == 3 ? new int[]{h0, 4, 4} : new int[]{h0, 4};
		Body body = new Body(x0, zF, x1, zM, base, heights, false, false);
		// timber towns jetty over the street: the top storey of a three-storey inn (the carriage arch keeps its voussoirs
		// clear beneath), the first floor where there is no arch
		boolean jetty = timberTown && zF >= 1 && (storeys == 3 || !arch) && d.chance(Math.max(0.65, h.walls().jetty()));
		if (jetty) {
			body.jetty(storeys == 3 ? 2 : 1, Dirs.FRONT);
		}
		d.body(x0, zF, x1, zM);
		int plate = body.plateY();
		d.eave(plate);
		int top = storeys - 1;

		// --- ground and shell: a stone ground floor under timber, the culture's own walls elsewhere
		Storeys.ground(d, body, t.raised());
		Grounds.forecourt(d, 0, w - 1, zF - 1, !t.raised());
		int doorX = (tx0 + tx1) / 2;
		// the stair climbs along the tap room's outer end; the door must not open onto its foot
		int sx0 = archRight ? tx0 : tx1;
		Facade fac = Facade.plan(d, body, doorX, 1, w >= 13 ? 2 : 1, 0.75);
		Integer placed = doorOf(fac);
		if (placed == null || placed == sx0 || placed < tx0 || placed > tx1) {
			fac = Facade.plan(d, body, sx0 + (sx0 == tx0 ? 2 : -2), 1, w >= 13 ? 2 : 1, 0.75);
			placed = doorOf(fac);
		}
		if (placed != null && placed >= tx0 && placed <= tx1 && placed != sx0) {
			doorX = placed;
		} else if (doorX == sx0) {
			doorX = sx0 + (sx0 == tx0 ? 1 : -1);
		}
		WallKind[] kinds = timberTown || t == Tradition.ALPINE ? Storeys.stoneBase(d, storeys) : null;
		Storeys.walls(d, body, fac, 0, top, kinds, false);
		if (arch) {
			Grounds.cut(d, px0, zF, px1, zM, base);
		}
		Side front = body.side(0, Dirs.FRONT);
		int archTop = base + h0 - 1;
		Mat wall = d.mat(Role.WALL);
		Mat frame = d.mat(Role.FRAME);
		if (arch) {
			carriageArch(d, body, px0, px1, part, base, archTop, zF, zM, t);
		}
		// windows: tall ones in the tap room's front, every window above
		Storeys.windows(d, body, fac, 1, top, 2);
		if (Adobe.is(d)) {
			Adobe.screens(d, body, 1);
		}
		int winH = Math.min(3, h0 - 2);
		for (Side s : body.sides(0)) {
			for (Facade.Opening o : fac.openings(s.out())) {
				if (o.kind() != Facade.Kind.WINDOW || !s.contains(o.at())) {
					continue;
				}
				boolean nearArch = arch && (s.axis() == Direction.Axis.X && o.at() + o.width() >= px0 - 1 && o.at() <= px1 + 1
					|| s.out() == (archRight ? Dirs.RIGHT : Dirs.LEFT));
				if (!nearArch) {
					Adobe.window(d, s, o.at(), base + 1, o.width(), s.out() == Dirs.FRONT ? winH : Math.min(2, winH), Openings.WindowStyle.DRESSED);
				}
			}
		}
		Openings.door(d, front, doorX, base, 1, new Openings.DoorOpts(true, true, true));
		// a side door from the passage into the tap room, at the passage's back end clear of the kegs along the partition
		int sideDoorZ = zM - 1;
		if (arch) {
			d.inStage(StageKind.OPENINGS, () -> d.door(part, base, sideDoorZ, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR),
				archRight ? Dirs.LEFT : Dirs.RIGHT, DoorHingeSide.LEFT));
		}

		// --- the roof: the culture's form over the range, a cross gable over the tap-room door, dormers, two stacks
		RoofForm form = flat ? RoofForm.FLAT : karst ? RoofForm.PAGODA
			: h.roof().pick(d.random(), RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.HIP, RoofForm.GAMBREL);
		Roofs.Roof roof;
		int skyline;
		if (form == RoofForm.FLAT || form == RoofForm.PAGODA) {
			Storeys.Top tp = Storeys.roof(d, body, form, Direction.Axis.X, Math.max(1.0, h.roof().pitch()), 1, true);
			roof = tp.roof();
			skyline = tp.ridge();
			if (form == RoofForm.FLAT) {
				Halls.roofTerrace(d, body.topRect(), tp.deck(), !archRight);
				skyline = tp.deck() + 3;
			}
		} else {
			roof = pitched(d, body, form, doorX, px0, px1, archRight);
			skyline = roof == null ? plate + 2 : roof.ridgeY;
		}

		// --- stairs: ground to the first floor along the tap room's gable wall, climbing to the back; on a three-storey
		// inn a second flight beside it climbs back toward the front
		int sx = sx0;
		int in = sx == tx0 ? 1 : -1;
		Stairs.straight(d, sx, base, zF + 1, Dirs.BACK, h0, 1);
		int fy = body.floorY(1);
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int i = 1; i < h0; i++) {
				d.set(sx, fy - 1, zF + 1 + i, Blocks.AIR.defaultBlockState());
			}
			d.set(sx, fy - 1, zF + h0, d.mat(Role.FURNITURE).stairs(Dirs.BACK, Half.BOTTOM));
		}
		int hallW = storeys == 3 ? 2 : 1;
		int s2x = sx + in;
		if (storeys == 3) {
			int fy2 = body.floorY(2);
			Stairs.straight(d, s2x, fy, zM - 2, Dirs.FRONT, 4, 1);
			try (var s = d.stage(StageKind.FLOORS)) {
				for (int i = 1; i < 4; i++) {
					d.set(s2x, fy2 - 1, zM - 2 - i, Blocks.AIR.defaultBlockState());
				}
				d.set(s2x, fy2 - 1, zM - 2 - 3, d.mat(Role.FURNITURE).stairs(Dirs.FRONT, Half.BOTTOM));
			}
		}
		// the stair hall upstairs: a wall between it and the first room, on every upper floor
		int railX = sx + in * hallW;
		Mat gable = d.mat(Role.GABLE);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int k = 1; k <= top; k++) {
				int fk = body.floorY(k);
				for (int z = body.rect(k)[1] + 1; z <= zM - 2; z++) {
					for (int y = fk; y < fk + 3; y++) {
						d.set(railX, y, z, gable.full());
					}
				}
			}
		}

		// --- the tap room
		int iz0 = zF + 1;
		int iz1 = zM - 1;
		// the bar along the partition, its kegs racked against it
		int barX = archRight ? tx1 - 2 : tx0 + 2;
		int barZ0 = iz0 + 1;
		int barLen = Math.min(4, iz1 - barZ0);
		if (tx1 - tx0 >= 4 && barLen >= 3) {
			Trade.bar(d, barX, base, barZ0, Dirs.BACK, barLen, archRight ? Dirs.LEFT : Dirs.RIGHT, 2);
		}
		// the hearth on the gable wall at the back corner, its stack
		int hz = iz1;
		int hx = archRight ? tx0 + 1 : tx1 - 1;
		Furniture.hearth(d, hx, base, iz1, Dirs.FRONT, false);
		if (roof != null) {
			Chimneys.stack(d, hx, hz + 1 <= zM ? zM : hz, base + 2, roof, roof.ridgeY + 1);
			// and a second stack at the far gable, from the guest rooms' fire
			int ox = archRight ? x1 : x0;
			if (roof.covers(ox, zM - 2)) {
				Chimneys.stack(d, ox, zM - 2, body.floorY(1), roof, roof.ridgeY + 1);
			}
		}
		// tables with chairs in the open floor, each piece only where the room stays walkable
		Rooms.Room tap = new Rooms.Room(tx0, iz0, tx1, iz1, base, h0 - 1);
		tap.keep(doorX, iz0);
		if (arch) {
			tap.keep(archRight ? tx1 : tx0, sideDoorZ);
		}
		tap.keepArea(sx, iz0, sx, zF + h0);
		if (d.isEmpty(sx + in, base, iz0)) {
			tap.keep(sx + in, iz0);
		}
		for (int z = iz0; z <= iz1; z++) {
			for (int x = tx0; x <= tx1; x++) {
				if (!d.isEmpty(x, base, z)) {
					tap.block(x, z);
				}
			}
		}
		// the bar's customer side and the hearth's front stay free
		for (int z = iz0; z <= iz1; z++) {
			for (int lx : new int[]{barX - 1, barX + 1}) {
				if (d.isEmpty(lx, base, z)) {
					tap.keep(lx, z);
				}
			}
		}
		if (d.isEmpty(hx, base, iz1 - 1)) {
			tap.keep(hx, iz1 - 1);
		}
		int seats = 0;
		for (int z = iz0 + 2; z <= iz1 - 1; z += 3) {
			for (int x = Math.min(tx0, tx1) + 1; x <= Math.max(tx0, tx1) - 1; x += 3) {
				if (!tap.claim(x, z)) {
					continue;
				}
				Furniture.table(d, x, base, z);
				for (Direction dir : Dirs.HORIZONTAL) {
					int cx2 = x + Dirs.dx(dir);
					int cz2 = z + Dirs.dz(dir);
					if (d.isEmpty(cx2, base, cz2) && tap.claim(cx2, cz2)) {
						Furniture.chair(d, cx2, base, cz2, dir);
						seats++;
					}
				}
			}
		}
		// a tap room too cramped for a table still has a settle or two against its walls
		for (int z = iz1; z >= iz0 && seats < 2; z--) {
			for (int x = Math.min(tx0, tx1); x <= Math.max(tx0, tx1) && seats < 2; x++) {
				Direction wallward = z == iz1 ? Dirs.BACK : x == tx0 ? Dirs.LEFT : x == tx1 ? Dirs.RIGHT : null;
				if (wallward != null && d.isEmpty(x, base, z) && d.isEmpty(x, base + 1, z) && tap.claim(x, z)) {
					Furniture.chair(d, x, base, z, wallward);
					seats++;
				}
			}
		}
		Rooms.furnish(d, tap, Rooms.Type.HALL, 0, JobType.NONE);
		Floors.joists(d, tx0, iz0, tx1, iz1, fy - 1, Direction.Axis.X, 2, base);
		// lanterns over the tables and the bar, hung from the joists, so the windows glow at night
		for (int lx : new int[]{(tx0 + tx1) / 2, barX}) {
			for (int lz = iz0 + 2; lz <= iz1 - 1; lz += 3) {
				if (lx != sx && d.isEmpty(lx, base + 2, lz)) {
					Halls.lanternUnder(d, lx, base, lz, 1);
				}
			}
		}

		// --- upstairs: on every floor the corridor at the back, rooms along the street
		int beds = 0;
		Mat cloth = d.mat(Role.CLOTH);
		for (int k = 1; k <= top; k++) {
			int fk = body.floorY(k);
			int cz = zM - 1;
			int wallZ = zM - 2;
			int[] up = body.rect(k);
			int rx0;
			int rx1;
			if (archRight) {
				rx0 = railX + 1;
				rx1 = up[2] - 1;
			} else {
				rx0 = up[0] + 1;
				rx1 = railX - 1;
			}
			List<int[]> rooms = rooms(rx0, rx1);
			try (var s = d.stage(StageKind.WALLS)) {
				for (int x = rx0; x <= rx1; x++) {
					for (int y = fk; y < fk + 3; y++) {
						d.set(x, y, wallZ, gable.full());
					}
				}
				for (int r = 0; r + 1 < rooms.size(); r++) {
					int px = rooms.get(r)[1] + 1;
					for (int z = up[1] + 1; z < wallZ; z++) {
						for (int y = fk; y < fk + 3; y++) {
							d.set(px, y, z, gable.full());
						}
					}
				}
			}
			for (int[] r : rooms) {
				int dx = (r[0] + r[1]) / 2;
				d.inStage(StageKind.OPENINGS, () -> d.door(dx, fk, wallZ, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), Dirs.FRONT,
					DoorHingeSide.LEFT));
				Rooms.Room room = new Rooms.Room(r[0], up[1] + 1, r[1], wallZ - 1, fk, 3);
				room.keep(dx, wallZ - 1);
				int want = r[1] - r[0] + 1 >= 3 ? 2 : 1;
				Rooms.furnish(d, room, Rooms.Type.BEDROOM, want, JobType.NONE);
				beds += want;
			}
			// the corridor: a runner, lanterns
			try (var s = d.stage(StageKind.INTERIOR)) {
				for (int x = Math.min(rx0, sx); x <= Math.max(rx1, sx); x++) {
					if (d.isEmpty(x, fk, cz) && d.isSolid(x, fk - 1, cz) && cloth.has(Form.CARPET)) {
						d.set(x, fk, cz, cloth.carpet());
					}
				}
			}
			int chestX = archRight ? up[2] - 1 : up[0] + 1;
			if (k == 1) {
				Furniture.chest(d, chestX, fk, cz, archRight ? Dirs.LEFT : Dirs.RIGHT, List.of(new Object[]{"minecraft:white_wool", 6},
					new Object[]{"minecraft:candle", 4}));
			}
			Halls.lanternUnder(d, (rx0 + rx1) / 2, fk, cz, 1);
			Halls.lanternUnder(d, sx, fk, cz, 1);
		}

		// --- outside: the sign on its bracket, the name board, the culture's touches, the yard and the stable
		boolean level = !t.raised() && Grounds.gentle(d, x0, 0, x1, zF - 1, base);
		List<String[]> nameLines = name(d);
		int signX = signX(doorX, archRight);
		if (signX >= x0 + 1 && signX <= x1 - 1) {
			if (!bracketSign(d, signX, base, zF, nameLines)) {
				int sy = Math.max(3, base + 3);
				int sz = zF - 1;
				if (d.isEmpty(signX, sy, sz) && (sz >= 0 || sy >= 3)) {
					Signs.hangingSign(d, signX, sy, sz, archRight ? Dirs.LEFT : Dirs.RIGHT, nameLines);
				}
			}
		}
		nameBoard(d, front, tx0, tx1, doorX, signX, base + h0 - 2, zF, nameLines);
		if (level) {
			// a bay window to the street, clear of the door, the sign's bracket and the arch
			for (int c0 : new int[]{doorX + 2, doorX - 3}) {
				if (c0 > tx0 && c0 + 1 < tx1 && Math.abs(c0 - doorX) >= 2 && Math.abs(c0 + 1 - doorX) >= 2 && Math.abs(c0 - signX) >= 1
					&& Math.abs(c0 + 1 - signX) >= 1) {
					Porches.bay(d, front, c0, c0 + 1, base);
					break;
				}
			}
		}
		if ((t == Tradition.ALPINE || karst || t == Tradition.STILT) && !jetty) {
			Side upF = body.side(1, Dirs.FRONT);
			Porches.balcony(d, upF, upF.column(1), upF.column(upF.last() - 1), fy);
		}
		if (t == Tradition.MEDITERRANEAN && zF >= 1) {
			pergola(d, tx0, tx1, zF - 1, base);
		}
		if (Adobe.is(d) && level && zF >= 1 && h0 >= 4) {
			// a striped awning shading the tap room's front
			Adobe.awning(d, front, front.index(tx0), front.index(tx1), base + 3);
		}
		if (level && zF >= 1) {
			outside(d, front, doorX, signX, tx0, tx1, zF - 1, base, archRight);
			if (zF >= 2) {
				barrelTable(d, doorX, signX, tx0, tx1, zF - 2, base, archRight);
			}
		}
		Grounds.approach(d, doorX, base, zF - 1);
		if (level) {
			Halls.lamp(d, archRight ? px0 - 1 : px1 + 1, 0, 0, 2);
		}

		int yz0 = zM + 1;
		if (arch && dp - 1 - yz0 >= 3) {
			// a gallery along the yard side of the first floor, a door out to it from the corridor's end
			Side upBack = body.side(1, Dirs.BACK);
			Porches.balcony(d, upBack, upBack.column(1), upBack.column(upBack.last() - 1), fy);
			int[] up = body.rect(1);
			int gx = archRight ? up[2] - 1 : up[0] + 1;
			if (Openings.isWallBlock(d, gx, fy, zM) && d.isEmpty(gx, fy, zM - 1)) {
				d.inStage(StageKind.OPENINGS, () -> d.door(gx, fy, zM, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), Dirs.BACK,
					DoorHingeSide.LEFT));
			}
		}
		if (arch && dp - 1 - yz0 >= 1) {
			yard(d, x0, yz0, x1, dp - 1, archRight, px0, px1, base);
			// a palm in the yard's far corner in the dry south
			int palmX = archRight ? x0 + 1 : x1 - 1;
			int palmZ = Math.min(dp - 2, yz0 + 1);
			if (Adobe.is(d) && dp - 1 - yz0 >= 3 && d.isEmpty(palmX, base, palmZ) && d.isEmpty(palmX, base + 1, palmZ) && d.isSolid(palmX, base - 1, palmZ)) {
				Adobe.palm(d, palmX, base, palmZ, 5);
			}
			// the way from the arch into the yard and out to the street
			Grounds.approach(d, (px0 + px1) / 2, base, zF);
		}
		if (h.snow()) {
			Garden.snowBanks(d, x0, zF, x1, zM, base, doorX);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(maxH - 1, skyline + 3));
	}

	/**
	 * The pitched roof over the range: ridge along the street, eaves from the habits; a cross gable over the tap-room
	 * door bay (pitched up to just under the main ridge) where the slope is a whole block, and a dormer over the arch on
	 * front and back where the slope is long enough to take one.
	 */
	private static Roofs.Roof pitched(Draft d, Body body, RoofForm form, int doorX, int px0, int px1, boolean archRight) {
		int[] tr = body.topRect();
		int plate = body.plateY();
		Roofs.Opts o = Roofs.Opts.of(d);
		int span = tr[3] - tr[1] + 1;
		double half = span / 2.0 + o.eaves();
		int room = d.maxHeight() - 1 - 1 - plate;
		if (plate + 1 + 0.5 * half + 1 > d.maxHeight() - 1) {
			Roofs.flat(d, tr[0], tr[1], tr[2], tr[3], plate, plate + 2 <= d.maxHeight() - 1);
			return null;
		}
		// the culture's own pitch: low and broad in the mountains and the south, a whole pitch elsewhere
		double want = d.habits().roof().pitch() < 0.75 ? d.habits().roof().pitch() : Math.max(1.0, d.habits().roof().pitch());
		double p = Math.min(want, Math.max(0.5, (room - 1.0) / Math.max(1.0, half)));
		p = p >= 1.0 ? Math.floor(p) : 0.5;
		o = o.withPitch(p);
		Roofs.Plan plan = Roofs.plan(d, tr[0], tr[1], tr[2], tr[3], plate, form, Direction.Axis.X, o, false, false);
		int top = body.storeys() - 1;
		Side topFront = body.side(top, Dirs.FRONT);
		boolean jettied = body.outset(top, Dirs.FRONT) > 0;
		boolean gabled = false;
		if (jettied && p >= 1.0 && form != RoofForm.GAMBREL && form != RoofForm.MANSARD) {
			// over a jettied top storey, a five-wide gable over the door where it stands clear of the arch's bay
			int col = doorX - 2;
			boolean clearOfArch = col + 4 < px0 || col > px1;
			if (clearOfArch && topFront.contains(col) && topFront.contains(col + 4) && topFront.index(col) >= 1 && topFront.index(col + 4) <= topFront.last() - 1) {
				plan.crossGable(topFront, col, 5, d);
				gabled = true;
			}
		}
		if (!gabled && !jettied && p >= 1.0 && form != RoofForm.GAMBREL && form != RoofForm.MANSARD && half >= 4.5) {
			// otherwise a pair of dormers lighting the roof over the guest rooms, well apart
			int n = topFront.length();
			for (int i : new int[]{n / 4, n - 1 - n / 4}) {
				if (i >= 2 && i <= n - 3) {
					plan.dormer(topFront, topFront.column(i), 1, plate + 1);
				}
			}
		}
		return plan.draw(d).roof();
	}

	/**
	 * The carriage arch: a stone arch front and back whatever the walls (voussoirs, a keystone), the passage cobbled and
	 * cleared, a partition wall beside it, the yard's gates folded back against the passage walls, guard stones at the
	 * arch's foot to keep the wheels off its jambs, a lantern hung in the vault.
	 */
	private static void carriageArch(Draft d, Body body, int px0, int px1, int part, int base, int archTop, int zF, int zM, Tradition t) {
		Side front = body.side(0, Dirs.FRONT);
		Side back = body.side(0, Dirs.BACK);
		Arches.Kind ak = t == Tradition.KARST ? Arches.Kind.BRACKETED : Arches.kind(t) == Arches.Kind.POINTED ? Arches.Kind.POINTED
			: Arches.Kind.ROUND;
		Paint pierP = Arches.pierPaint(d, ak);
		Mat wall = d.mat(Role.WALL);
		Mat frame = d.mat(Role.FRAME);
		Paint voussoir = Arches.timber(ak) ? null : d.mat(Role.WALL_ACCENT).full();
		Arches.arcade(d, front, new int[]{front.index(px0 - 1), front.index(px1 + 1)}, base, archTop, ak, pierP, voussoir);
		Arches.arcade(d, back, new int[]{back.index(px0 - 1), back.index(px1 + 1)}, base, archTop, ak, pierP, voussoir);
		if (!Arches.timber(ak)) {
			d.inStage(StageKind.WALLS, () -> d.set((px0 + px1) / 2, archTop, zF, d.mat(Role.ACCENT).mainOr(Form.FULL, Blocks.CHISELED_STONE_BRICKS)
				.defaultBlockState()));
		}
		d.clearBox(px0, base, zF + 1, px1, archTop - 1, zM - 1);
		Halls.floor(d, px0, zF, px1, zM, base - 1, true);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = zF + 1; z <= zM - 1; z++) {
				for (int y = base; y < archTop; y++) {
					d.set(part, y, z, Halls.masonry(d) ? wall.full() : (z - zF) % 3 == 0 ? frame.log(Direction.Axis.Y) : wall.full());
				}
			}
		}
		// the vault's ceiling boards and a lantern hung from them
		Floors.boards(d, px0, zF + 1, px1, zM - 1, archTop);
		Halls.lanternUnder(d, (px0 + px1) / 2, base, (zF + zM) / 2, 1);
		// the yard gates folded back flat against the passage walls inside the front arch
		Mat gate = d.mat(Role.FENCE).has(Form.FENCE_GATE) ? d.mat(Role.FENCE) : d.mat(Role.FRAME);
		try (var s = d.stage(StageKind.OPENINGS)) {
			for (int gx : new int[]{px0, px1}) {
				if (d.isEmpty(gx, base, zF + 1) && gate.has(Form.FENCE_GATE)) {
					Direction toward = gx == px0 ? Dirs.RIGHT : Dirs.LEFT;
					d.set(gx, base, zF + 1, gate.gate(toward, true));
				}
			}
		}
		// guard stones at the arch's foot on the street side
		Mat guard = d.mat(Role.WALL_ACCENT).has(Form.WALL) ? d.mat(Role.WALL_ACCENT) : d.mat(Role.FOUNDATION);
		if (zF >= 1) {
			try (var s = d.stage(StageKind.EXTERIOR)) {
				for (int gx : new int[]{px0 - 1, px1 + 1}) {
					if (d.isEmpty(gx, base, zF - 1) && d.isSolid(gx, base - 1, zF - 1)) {
						d.set(gx, base, zF - 1, guard.has(Form.WALL) ? guard.wall() : guard.slab(SlabType.BOTTOM));
					}
				}
			}
		}
	}

	/**
	 * The inn's sign hung from a wrought bracket: two posts of fence run out from the front wall a block under the first
	 * floor, an upside-down stair braces the arm against the wall, and the painted sign hangs on its chains from the arm's
	 * end. Needs two clear rows of forecourt; answers whether it was hung.
	 */
	static boolean bracketSign(Draft d, int x, int base, int zF, List<String[]> lines) {
		if (zF < 2) {
			return false;
		}
		int ay = base + 3;
		int z1 = zF - 1;
		int z2 = zF - 2;
		if (!d.isSolid(x, ay, zF) || !d.isEmpty(x, ay, z1) || !d.isEmpty(x, ay, z2) || !d.isEmpty(x, ay - 1, z2) || !d.isEmpty(x, ay - 1, z1)) {
			return false;
		}
		Mat iron = d.mat(Role.FURNITURE);
		Paint arm = iron.has(Form.FENCE) ? iron.fence() : d.mat(Role.FRAME).fence();
		Block sign = iron.mainOr(Form.HANGING_SIGN, Blocks.SPRUCE_HANGING_SIGN);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, ay, z1, arm);
			d.set(x, ay, z2, arm);
			d.set(x, ay - 1, z1, iron.stairsOrSlab(Dirs.BACK, Half.TOP));
			// the board turned along the street, read by those coming either way
			d.set(x, ay - 1, z2, sign.defaultBlockState().trySetValue(BlockStateProperties.ROTATION_16, 4)
				.trySetValue(BlockStateProperties.ATTACHED, false), Signs.signTag(lines, "black"));
		}
		return true;
	}

	/**
	 * The inn's name painted along the front: a row of wall signs on the band over the tap-room windows, one word to a
	 * sign, where three cells of plain wall run clear of the door and the sign's bracket.
	 */
	static void nameBoard(Draft d, Side front, int tx0, int tx1, int doorX, int signX, int y, int zF, List<String[]> lines) {
		if (zF < 1) {
			return;
		}
		String[] l1 = lines.get(1);
		String[] l2 = lines.get(2);
		List<String[]> words = new ArrayList<>();
		for (String part : (l1[1] + " " + l2[1]).trim().split(" +")) {
			words.add(new String[]{part});
		}
		if (words.size() > 3) {
			return;
		}
		Block sign = d.mat(Role.FURNITURE).mainOr(Form.WALL_SIGN, Blocks.SPRUCE_WALL_SIGN);
		// the widest run of plain wall at y between the door and the ends of the tap room
		int bestA = -1;
		int bestLen = 0;
		int run = 0;
		for (int x = Math.min(tx0, tx1); x <= Math.max(tx0, tx1); x++) {
			boolean ok = Math.abs(x - doorX) >= 1 && x != signX && Openings.isWallBlock(d, x, y, zF) && d.isEmpty(x, y, zF - 1);
			run = ok ? run + 1 : 0;
			if (run > bestLen) {
				bestLen = run;
				bestA = x - run + 1;
			}
		}
		if (bestLen < words.size()) {
			return;
		}
		int start = bestA + (bestLen - words.size()) / 2;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int k = 0; k < words.size(); k++) {
				List<String[]> sl = List.of(new String[]{"", ""}, new String[]{"", words.get(k)[0]}, new String[]{"", ""}, new String[]{"", ""});
				d.set(start + k, y, zF - 1, States.facing(sign, Dirs.FRONT), Signs.signTag(sl, "white"));
			}
		}
	}

	/**
	 * What stands outside an inn's door: a settle against the wall on one side, casks of the house's ale on the other
	 * (a lantern on the top one), each only where the street row is free and level.
	 */
	static void outside(Draft d, Side front, int doorX, int signX, int tx0, int tx1, int z, int y, boolean archRight) {
		int away = archRight ? -1 : 1;
		// the settle: two seats side by side, on the side away from the arch
		for (int x = doorX + 2 * away; x != doorX + 5 * away; x += away) {
			int nx = x + away;
			if (x < tx0 || x > tx1 || nx < tx0 || nx > tx1) {
				break;
			}
			if (free(d, x, y, z) && free(d, nx, y, z)) {
				Furniture.chair(d, x, y, z, Dirs.BACK);
				Furniture.chair(d, nx, y, z, Dirs.BACK);
				break;
			}
		}
		// the casks toward the arch, clear of the sign
		for (int x = doorX - 2 * away; x != doorX - 5 * away; x -= away) {
			if (x < tx0 || x > tx1 || x == signX) {
				continue;
			}
			if (free(d, x, y, z)) {
				int fx = x;
				d.inStage(StageKind.EXTERIOR, () -> {
					d.set(fx, y, z, States.facing(Blocks.BARREL, Direction.UP));
					d.set(fx, y + 1, z, States.lantern(d.mat(Role.LIGHT).mainOr(Form.FULL, Blocks.LANTERN), false));
				});
				break;
			}
		}
	}

	/** A barrel stood on end for a table in the forecourt's outer row, a stool either side, away from the door's path. */
	static void barrelTable(Draft d, int doorX, int signX, int tx0, int tx1, int z, int y, boolean archRight) {
		int away = archRight ? -1 : 1;
		for (int x = doorX + 3 * away; Math.abs(x - doorX) <= 6; x += away) {
			int a = x - 1;
			int b = x + 1;
			if (a < tx0 || b > tx1) {
				break;
			}
			if (Math.abs(a - doorX) < 2 || x == signX || a == signX || b == signX) {
				continue;
			}
			if (free(d, x, y, z) && free(d, a, y, z) && free(d, b, y, z)) {
				try (var s = d.stage(StageKind.EXTERIOR)) {
					d.set(x, y, z, States.facing(Blocks.BARREL, Direction.UP));
					d.set(x, y + 1, z, Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.LIT, true));
				}
				Furniture.chair(d, a, y, z, Dirs.LEFT);
				Furniture.chair(d, b, y, z, Dirs.RIGHT);
				d.poi(dev.voidmc.hearth.api.PoiType.TABLE, x, y, z - 1 >= 0 ? z - 1 : z, Dirs.BACK);
				return;
			}
		}
	}

	private static boolean free(Draft d, int x, int y, int z) {
		return d.isEmpty(x, y, z) && d.isEmpty(x, y + 1, z) && d.isSolid(x, y - 1, z) && d.allowed(x, y + 1, z);
	}

	private static Integer doorOf(Facade f) {
		return f.openings(Dirs.FRONT).stream().filter(o -> o.kind() == Facade.Kind.DOOR).map(Facade.Opening::at).findFirst().orElse(null);
	}

	/** Where the sign hangs: two along from the door, toward the arch. */
	static int signX(int doorX, boolean archRight) {
		return doorX + (archRight ? 2 : -2);
	}

	/** Rooms across {@code x0..x1}: runs of three (or four, or two at the end) with a partition between each. */
	static List<int[]> rooms(int x0, int x1) {
		List<int[]> out = new ArrayList<>();
		int n = x1 - x0 + 1;
		int count = Math.max(1, (n + 1) / 4);
		int x = x0;
		for (int k = 0; k < count; k++) {
			int left = x1 - x + 1 - (count - k - 1) * 4;
			int width = k == count - 1 ? x1 - x + 1 : Math.min(3, left);
			if (width < 2) {
				break;
			}
			out.add(new int[]{x, x + width - 1});
			x += width + 1;
		}
		return out;
	}

	/** The sign's lines: the inn's name in two lines (translatable) with a flourish above and below. */
	static List<String[]> name(Draft d) {
		int n = d.param("name", (int) (Noise.unit(d.seed(), 0x1AA, 0, 0) * NAMES.length));
		String[] nm = NAMES[Math.floorMod(n, NAMES.length)];
		return List.of(new String[]{"", "~"}, new String[]{"hearth.build.civic.inn." + nm[0] + ".1", nm[1]},
			new String[]{"hearth.build.civic.inn." + nm[0] + ".2", nm[2]}, new String[]{"", "~"});
	}

	/** A vine pergola over the terrace in front: posts, a trellis of fences and leaves, a table under it. */
	static void pergola(Draft d, int x0, int x1, int z, int y) {
		Mat post = d.mat(Role.FRAME);
		if (z < 0) {
			return;
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int x = x0; x <= x1; x++) {
				if (d.isEmpty(x, y + 2, z) && (x == x0 || x == x1) && d.isEmpty(x, y, z)) {
					for (int yy = y; yy <= y + 2; yy++) {
						d.set(x, yy, z, post.log(Direction.Axis.Y));
					}
				}
				if (d.isEmpty(x, y + 3, z)) {
					d.set(x, y + 3, z, (x & 1) == 0 ? Paint.of(States.leaves(Blocks.AZALEA_LEAVES)).inStage(StageKind.EXTERIOR) : d.mat(Role.BEAM).log(
						Direction.Axis.X));
				}
			}
		}
	}

	/**
	 * The stable yard: earth and gravel, the stable along the back (posts along its front, a boarded back wall, a shed
	 * roof falling to the yard, stalls of fences with hay and water, and on a deep yard a hay loft over them with its loft
	 * door and a bale on the hoist), a woodpile and casks against the far side, a water butt by the arch.
	 */
	static void yard(Draft d, int x0, int z0, int x1, int z1, boolean archRight, int px0, int px1, int base) {
		Groundwork.level(d, x0, z0, x1, z1, base);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					if (d.site().isWater(x, z) || !d.isEmpty(x, base, z)) {
						continue;
					}
					double u = Noise.value(d.seed() ^ 0x5AD, x / 2.0, 0, z / 2.0);
					d.set(x, base - 1, z, (u < 0.45 ? Blocks.COARSE_DIRT : u < 0.7 ? Blocks.GRAVEL : Blocks.DIRT_PATH).defaultBlockState());
				}
			}
		}
		int depth = z1 - z0 + 1;
		int sx0 = archRight ? x0 : x0 + 4;
		int sx1 = archRight ? x1 - 4 : x1;
		if (depth >= 4 && sx1 - sx0 >= 4) {
			stable(d, sx0, Math.max(z0 + 1, z1 - 3), sx1, z1, base);
		}
		// a woodpile and casks along the far side, a water butt by the arch
		int farX = archRight ? x0 : x1;
		if (depth >= 3) {
			Garden.woodpile(d, farX, base, z0, Dirs.BACK, Math.min(2, depth - 3));
		}
		int ax = archRight ? px0 - 1 : px1 + 1;
		if (d.isEmpty(ax, base, z0) && Groundwork.surface(d, ax, z0) == base) {
			Garden.waterButt(d, ax, base, z0);
		}
		Garden.barrels(d, archRight ? x0 + 1 : x1 - 1, base, z0, archRight ? Dirs.RIGHT : Dirs.LEFT, 1);
	}

	/**
	 * The stable across the back of the yard: a boarded building under its own gable, ridge along the yard, posts at the
	 * corners and between the stalls; on the yard side each stall opens over a half door (a gate) with the hay rack and
	 * the water trough within; a hay door in a gablet over the middle with a bale hung on its beam where the roof is high
	 * enough, a lantern by the door.
	 */
	static void stable(Draft d, int x0, int z0, int x1, int z1, int base) {
		Mat frame = d.mat(Role.FRAME);
		Mat boards = d.mat(Role.FLOOR);
		Mat gateMat = d.mat(Role.FENCE).has(Form.FENCE_GATE) ? d.mat(Role.FENCE) : d.mat(Role.FRAME);
		int wallTop = base + 2;
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					boolean edgeX = x == x0 || x == x1;
					boolean edgeZ = z == z0 || z == z1;
					if (!edgeX && !edgeZ) {
						continue;
					}
					boolean post = edgeX && edgeZ || z == z0 && (x - x0) % 3 == 0;
					for (int y = base; y <= wallTop; y++) {
						d.set(x, y, z, post ? frame.log(Direction.Axis.Y) : boards.full());
					}
				}
			}
			// a sill beam over the stall openings
			for (int x = x0 + 1; x < x1; x++) {
				d.set(x, wallTop, z0, d.mat(Role.BEAM).log(Direction.Axis.X));
			}
		}
		d.carve(x0 + 1, base, z0 + 1, x1 - 1, wallTop, z1 - 1);
		Floors.boards(d, x0 + 1, z0 + 1, x1 - 1, z1 - 1, base - 1);
		// the stalls: between the posts on the yard side, an opening over a half door, hay and water within
		try (var s = d.stage(StageKind.OPENINGS)) {
			for (int x = x0 + 1; x < x1; x++) {
				if ((x - x0) % 3 == 0) {
					continue;
				}
				boolean doorCell = (x - x0) % 3 == 1;
				d.set(x, base + 1, z0, Blocks.AIR.defaultBlockState());
				d.set(x, base, z0, doorCell && gateMat.has(Form.FENCE_GATE) ? gateMat.gate(Dirs.FRONT, false) : Paint.of(Blocks.AIR.defaultBlockState()));
			}
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int x = x0 + 1; x < x1; x++) {
				if (z1 - 1 <= z0) {
					break;
				}
				if ((x - x0) % 3 == 1) {
					d.set(x, base, z1 - 1, Blocks.HAY_BLOCK.defaultBlockState());
				} else if ((x - x0) % 3 == 2) {
					d.set(x, base, z1 - 1, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
				} else if (d.isEmpty(x, base, z1 - 1)) {
					d.set(x, base, z1 - 1, d.mat(Role.RAILING).has(Form.FENCE) ? d.mat(Role.RAILING).fence().at(x, base, z1 - 1) : boards.full().at(x, base, z1 - 1));
				}
			}
		}
		// the roof: a gable along the yard, a block of eave all round
		double pitch = 1.0;
		Roofs.Field f = Roofs.gable(x0 - 1, z0 - 1, x1 + 1, z1 + 1, Direction.Axis.X, Roofs.Profile.straight(pitch));
		int ex0 = Math.max(0, x0 - 1);
		int ex1 = Math.min(d.frontage() - 1, x1 + 1);
		int ez1 = Math.min(d.depth() - 1, z1 + 1);
		Roofs.Roof r = Roofs.raster(d, f, wallTop + 1, ex0, z0 - 1, ex1, ez1, d.mat(Role.ROOF), d.mat(Role.ROOF_TRIM), d.habits().roof().ridge());
		// the gable ends boarded up to the roof
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x : new int[]{x0, x1}) {
				for (int z = z0; z <= z1; z++) {
					if (!r.covers(x, z)) {
						continue;
					}
					for (int y = wallTop + 1; y < r.bottomY(x, z); y++) {
						d.set(x, y, z, boards.full());
					}
				}
			}
		}
		// a hay door in the yard-side slope over the middle stall: a gablet's face with a bale on its beam
		int mx = (x0 + x1) / 2;
		if ((mx - x0) % 3 == 0) {
			mx++;
		}
		int hy = wallTop + 1;
		if (r.covers(mx, z0) && r.surfaceY(mx, z0 + 1) >= hy + 1 && d.allowed(mx, hy + 1, z0)) {
			try (var s = d.stage(StageKind.WALLS)) {
				d.set(mx - 1, hy, z0, boards.full());
				d.set(mx + 1, hy, z0, boards.full());
				d.set(mx, hy, z0, Blocks.HAY_BLOCK.defaultBlockState());
				d.set(mx - 1, hy + 1, z0, d.mat(Role.ROOF).stairsOrSlab(Dirs.RIGHT, Half.BOTTOM));
				d.set(mx + 1, hy + 1, z0, d.mat(Role.ROOF).stairsOrSlab(Dirs.LEFT, Half.BOTTOM));
				d.set(mx, hy + 1, z0, d.mat(Role.ROOF).full());
			}
		}
		Halls.lanternUnder(d, x0 + 1, base, z0 + 1, 0);
	}
}
