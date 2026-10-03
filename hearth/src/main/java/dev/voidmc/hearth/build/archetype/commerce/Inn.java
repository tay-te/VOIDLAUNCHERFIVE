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
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * {@code hearth:commerce/inn}: the coaching inn on the main road. Its range along the street holds the tap room (the bar
 * with its kegs, tables, a big hearth, a bay window to the street) beside a carriage arch that runs through to the yard;
 * upstairs, over the arch too, the guest rooms open off a corridor at the back, reached by a stair from the tap room.
 * Behind lies the stable yard: a lean-to of stalls with hay and a trough, a woodpile, a cart. A painted sign hangs over
 * the street with the inn's name. Timber cultures jetty the upper floor; alpine and karst inns carry balconies; desert
 * and mesa ones a flat roof and an arcade on the yard; mediterranean ones a vine pergola over a front terrace.
 *
 * <p>POIs: COUNTER and MARKET_STALL at the bar and the INNKEEPER's JOB_SITE behind it, SEATs and TABLEs, the HEARTH,
 * BEDs, STORAGE, the DOOR.
 *
 * <p>Params:
 * <ul>
 *   <li>{@code arch_right} (boolean): the carriage arch on the right (seen from the street) (default by seed)</li>
 *   <li>{@code roof} (string, a {@code RoofForm} name)</li>
 *   <li>{@code name} (int): which of the inn names the sign shows (default by seed)</li>
 *   <li>{@code variant} (int): the style variant</li>
 * </ul>
 */
public final class Inn implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("commerce/inn"), ArchetypeCategory.COMMERCE, Set.of(JobType.INNKEEPER), 4,
		11, 15, 11, 15, 11, 17, SettlementTier.VILLAGE, Set.of(), Set.of(), List.of(), 2, 1.0);

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
		int tier = d.tier();
		int maxH = d.maxHeight();
		Habits h = d.habits();
		Tradition t = Tradition.of(d);
		boolean jetty = (t == Tradition.TIMBER || t == Tradition.LOG) && d.chance(Math.max(0.5, h.walls().jetty()));
		boolean flat = t.flatRoofed();

		// --- the plan
		int x0 = 0;
		int x1 = w - 1;
		int md = dp >= 13 ? 8 : 7;
		// a raised inn needs rows in front to come down from its door
		int zF = t.raised() ? Math.min(dp - md, 1 + Grounds.stiltBase(d, x0, 1, x1, md))
			: Grounds.gentle(d, x0, -1, x1, 0, 0) ? 1 : 2;
		int zM = zF + md - 1;
		int h0 = md == 8 ? 5 : 4;
		boolean archRight = d.param("arch_right", d.chance(0.5));
		boolean arch = !t.raised();
		// the arch three wide inside a side wall; the tap room on the other side of a partition
		int px0 = archRight ? x1 - 3 : x0 + 1;
		int px1 = px0 + 2;
		int part = archRight ? px0 - 1 : px1 + 1;
		int tx0 = arch ? (archRight ? x0 + 1 : part + 1) : x0 + 1;
		int tx1 = arch ? (archRight ? part - 1 : x1 - 1) : x1 - 1;
		int base = t.raised() ? Grounds.stiltBase(d, x0, zF, x1, zM) : 0;
		Body body = new Body(x0, zF, x1, zM, base, new int[]{h0, 4}, false, false);
		if (jetty) {
			body.jetty(1, Dirs.FRONT);
		}
		d.body(x0, zF, x1, zM);
		int plate = body.plateY();
		d.eave(plate);

		// --- ground and shell
		Storeys.ground(d, body, t.raised());
		Grounds.forecourt(d, 0, w - 1, zF - 1, !t.raised());
		int doorX = (tx0 + tx1) / 2;
		// the stair climbs along the tap room's outer end; the door must not open onto its foot
		int sx0 = archRight ? tx0 : tx1;
		Facade fac = Facade.plan(d, body, doorX, 1, 1, 0.7);
		Integer placed = doorOf(fac);
		if (placed == null || placed == sx0 || placed < tx0 || placed > tx1) {
			fac = Facade.plan(d, body, sx0 + (sx0 == tx0 ? 2 : -2), 1, 1, 0.7);
			placed = doorOf(fac);
		}
		if (placed != null && placed >= tx0 && placed <= tx1 && placed != sx0) {
			doorX = placed;
		} else if (doorX == sx0) {
			doorX = sx0 + (sx0 == tx0 ? 1 : -1);
		}
		Storeys.walls(d, body, fac, 0, 1, null, false);
		if (arch) {
			Grounds.cut(d, px0, zF, px1, zM, base);
		}
		// the carriage arch through the ground floor, the partition beside it
		Side front = body.side(0, Dirs.FRONT);
		Side back = body.side(0, Dirs.BACK);
		int archTop = base + h0 - 1;
		// a stone carriage arch whatever the walls (timber over a stone arch is the coaching inn's face)
		Arches.Kind ak = t == Tradition.KARST ? Arches.Kind.BRACKETED : Arches.kind(t) == Arches.Kind.POINTED ? Arches.Kind.POINTED
			: Arches.Kind.ROUND;
		Paint pierP = Arches.pierPaint(d, ak);
		Mat wall = d.mat(Role.WALL);
		Mat frame = d.mat(Role.FRAME);
		if (arch) {
			Paint voussoir = Arches.timber(ak) ? null : d.mat(Role.WALL_ACCENT).full();
			Arches.arcade(d, front, new int[]{front.index(px0 - 1), front.index(px1 + 1)}, base, archTop, ak, pierP, voussoir);
			Arches.arcade(d, back, new int[]{back.index(px0 - 1), back.index(px1 + 1)}, base, archTop, ak, pierP, voussoir);
			// a keystone over the arch's crown and a lantern hung in the passage
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
		}
		// ground-floor windows away from the arch; every window above
		Storeys.windows(d, body, fac, 1, 1, 2);
		if (Adobe.is(d)) {
			Adobe.screens(d, body, 1);
		}
		for (Side s : body.sides(0)) {
			for (Facade.Opening o : fac.openings(s.out())) {
				if (o.kind() != Facade.Kind.WINDOW || !s.contains(o.at())) {
					continue;
				}
				boolean nearArch = arch && (s.axis() == Direction.Axis.X && o.at() + o.width() >= px0 - 1 && o.at() <= px1 + 1
					|| s.out() == (archRight ? Dirs.RIGHT : Dirs.LEFT));
				if (!nearArch) {
					Adobe.window(d, s, o.at(), base + 1, o.width(), Math.min(2, h0 - 2), Openings.WindowStyle.DRESSED);
				}
			}
		}
		Openings.door(d, front, doorX, base, 1, new Openings.DoorOpts(true, true, true));
		// a side door from the arch into the tap room
		Side arcSide = Side.of(part, zF, part, zM, archRight ? Dirs.RIGHT : Dirs.LEFT);
		// at the passage's back end, clear of the kegs racked along the partition
		int sideDoorZ = zM - 1;
		if (arch) {
			d.inStage(StageKind.OPENINGS, () -> d.door(part, base, sideDoorZ, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR),
				archRight ? Dirs.LEFT : Dirs.RIGHT, net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT));
		}

		// --- the roof: the culture's form over the range, dormers where it is pitched
		RoofForm form = flat ? RoofForm.FLAT : t == Tradition.KARST ? RoofForm.PAGODA
			: h.roof().pick(d.random(), RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.HIP, RoofForm.GAMBREL);
		Storeys.Top top = Storeys.roof(d, body, form, Direction.Axis.X, Math.max(1.0, h.roof().pitch()), 1, true);
		int skyline = top.ridge();
		if (form == RoofForm.FLAT) {
			Halls.roofTerrace(d, body.topRect(), top.deck(), !archRight);
			skyline = top.deck() + 3;
		}

		// --- the stair from the tap room up to the corridor (along the gable wall, climbing to the back)
		int sx = sx0;
		Stairs.straight(d, sx, base, zF + 1, Dirs.BACK, h0, 1);
		int fy = body.floorY(1);
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int i = 1; i < h0; i++) {
				d.set(sx, fy - 1, zF + 1 + i, Blocks.AIR.defaultBlockState());
			}
			d.set(sx, fy - 1, zF + h0, d.mat(Role.FURNITURE).stairs(Dirs.BACK, Half.BOTTOM));
		}
		// the stair hall upstairs: a wall between the well and the first room
		int railX = archRight ? sx + 1 : sx - 1;
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = body.rect(1)[1] + 1; z <= zM - 2; z++) {
				for (int y = fy; y < fy + 3; y++) {
					d.set(railX, y, z, d.mat(Role.GABLE).full());
				}
			}
		}

		// --- the tap room
		int iz0 = zF + 1;
		int iz1 = zM - 1;
		int lane = archRight ? tx1 - 1 : tx0 + 1;
		// the bar along the partition, its kegs racked against it
		int barX = archRight ? tx1 - 2 : tx0 + 2;
		int barZ0 = iz0 + 1;
		int barLen = Math.min(4, iz1 - barZ0);
		if (tx1 - tx0 >= 4 && barLen >= 3) {
			Trade.bar(d, barX, base, barZ0, Dirs.BACK, barLen, archRight ? Dirs.LEFT : Dirs.RIGHT, 2);
		}
		// the hearth on the gable wall at the back corner, its chimney
		int hz = iz1;
		int hx = archRight ? tx0 + 1 : tx1 - 1;
		Furniture.hearth(d, archRight ? tx0 + 1 : tx1 - 1, base, iz1, Dirs.FRONT, false);
		if (top.roof() != null) {
			Chimneys.stack(d, hx, hz + 1 <= zM ? zM : hz, base + 2, top.roof(), top.roof().ridgeY + 1);
		}
		// tables with chairs in the open floor, each piece only where the room stays walkable
		Rooms.Room tap = new Rooms.Room(tx0, iz0, tx1, iz1, base, h0 - 1);
		tap.keep(doorX, iz0);
		if (arch) {
			tap.keep(archRight ? tx1 : tx0, sideDoorZ);
		}
		tap.keepArea(sx, iz0, sx, zF + h0);
		if (d.isEmpty(sx + (sx == tx0 ? 1 : -1), base, iz0)) {
			tap.keep(sx + (sx == tx0 ? 1 : -1), iz0);
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

		// --- upstairs: the corridor at the back, rooms along the street
		int cz = zM - 1;
		int wallZ = zM - 2;
		int[] up = body.rect(1);
		int rx0 = archRight ? tx0 + 1 : up[0] + 1;
		int rx1 = archRight ? up[2] - 1 : tx1 - 1;
		if (archRight) {
			rx0 = sx + 2;
			rx1 = up[2] - 1;
		} else {
			rx0 = up[0] + 1;
			rx1 = sx - 2;
		}
		List<int[]> rooms = rooms(rx0, rx1);
		Mat gable = d.mat(Role.GABLE);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = rx0; x <= rx1; x++) {
				for (int y = fy; y < fy + 3; y++) {
					d.set(x, y, wallZ, gable.full());
				}
			}
			for (int k = 0; k + 1 < rooms.size(); k++) {
				int px = rooms.get(k)[1] + 1;
				for (int z = up[1] + 1; z < wallZ; z++) {
					for (int y = fy; y < fy + 3; y++) {
						d.set(px, y, z, gable.full());
					}
				}
			}
		}
		int beds = 0;
		for (int[] r : rooms) {
			int dx = (r[0] + r[1]) / 2;
			d.inStage(StageKind.OPENINGS, () -> d.door(dx, fy, wallZ, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), Dirs.FRONT,
				net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT));
			Rooms.Room room = new Rooms.Room(r[0], up[1] + 1, r[1], wallZ - 1, fy, 3);
			room.keep(dx, wallZ - 1);
			int want = r[1] - r[0] + 1 >= 3 ? 2 : 1;
			Rooms.furnish(d, room, Rooms.Type.BEDROOM, want, JobType.NONE);
			beds += want;
		}
		// the corridor: a runner, a lantern
		Mat cloth = d.mat(Role.CLOTH);
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int x = Math.min(rx0, sx); x <= Math.max(rx1, sx); x++) {
				if (d.isEmpty(x, fy, cz) && d.isSolid(x, fy - 1, cz) && cloth.has(Form.CARPET)) {
					d.set(x, fy, cz, cloth.carpet());
				}
			}
		}
		Furniture.chest(d, archRight ? up[2] - 1 : up[0] + 1, fy, cz, archRight ? Dirs.LEFT : Dirs.RIGHT, List.of(new Object[]{"minecraft:white_wool", 6},
			new Object[]{"minecraft:candle", 4}));
		// the gable end beyond the stair: the innkeeper's linen and a bench
		Halls.lanternUnder(d, (rx0 + rx1) / 2, fy, cz, 1);

		// --- outside: the sign, the culture's touches, the yard and the stable
		List<String[]> nameLines = name(d);
		int signX = signX(doorX, archRight);
		if (signX >= x0 + 1 && signX <= x1 - 1) {
			int sy = Math.max(3, base + 3);
			int sz = body.rect(1)[1] - 1;
			if (sy < fy) {
				sz = zF - 1;
			}
			if (d.isEmpty(signX, sy, sz) && (sz >= 0 || sy >= 3)) {
				Signs.hangingSign(d, signX, sy, sz, archRight ? Dirs.LEFT : Dirs.RIGHT, nameLines);
			}
		}
		boolean level = !t.raised() && Grounds.gentle(d, x0, 0, x1, zF - 1, base);
		if (level) {
			// a bay window to the street, clear of the door and the arch
			for (int c0 : new int[]{doorX + 2, doorX - 3}) {
				if (c0 > tx0 && c0 + 1 < tx1 && Math.abs(c0 - doorX) >= 2 && Math.abs(c0 + 1 - doorX) >= 2 && Math.abs(c0 - signX(doorX, archRight)) >= 1
					&& Math.abs(c0 + 1 - signX(doorX, archRight)) >= 1) {
					Porches.bay(d, front, c0, c0 + 1, base);
					break;
				}
			}
		}
		if ((t == Tradition.ALPINE || t == Tradition.KARST || t == Tradition.STILT) && !jetty) {
			Side upF = body.side(1, Dirs.FRONT);
			Porches.balcony(d, upF, upF.column(1), upF.column(upF.last() - 1), fy);
		}
		if (t == Tradition.MEDITERRANEAN && zF >= 1) {
			pergola(d, tx0, tx1, zF - 1, base);
		}
		Grounds.approach(d, doorX, base, zF - 1);
		if (level) {
			Halls.lamp(d, archRight ? px0 - 1 : px1 + 1, 0, 0, 2);
		}

		int yz0 = zM + 1;
		if (arch && dp - 1 - yz0 >= 3) {
			// a gallery along the yard side of the guest floor, a door out to it from the corridor's end
			Side upBack = body.side(1, Dirs.BACK);
			Porches.balcony(d, upBack, upBack.column(1), upBack.column(upBack.last() - 1), fy);
			int gx = archRight ? up[2] - 1 : up[0] + 1;
			if (Openings.isWallBlock(d, gx, fy, zM) && d.isEmpty(gx, fy, cz)) {
				d.inStage(StageKind.OPENINGS, () -> d.door(gx, fy, zM, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), Dirs.BACK,
					net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT));
			}
		}
		if (arch && dp - 1 - yz0 >= 1) {
			yard(d, x0, yz0, x1, dp - 1, archRight, px0, px1, base);
			// the way from the arch into the yard and out to the street
			Grounds.approach(d, (px0 + px1) / 2, base, zF);
		}
		if (h.snow()) {
			Garden.snowBanks(d, x0, zF, x1, zM, base, doorX);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, skyline + 2);
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
	 * The stable yard: earth and gravel, a lean-to of stalls along the back with hay and a trough, a woodpile and a cart
	 * against the far side, the boundary along the open side.
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
		if (depth >= 4) {
			// the stable: posts along its front, a back wall, a shed roof falling to the yard, stalls of fences
			int sz0 = z1 - 2;
			Mat frame = d.mat(Role.FRAME);
			Mat boards = d.mat(Role.FLOOR);
			Mat fence = d.mat(Role.RAILING);
			int sx0 = archRight ? x0 : x0 + 4;
			int sx1 = archRight ? x1 - 4 : x1;
			if (sx1 - sx0 >= 3) {
				try (var s = d.stage(StageKind.FRAME)) {
					for (int x = sx0; x <= sx1; x++) {
						boolean post = (x - sx0) % 3 == 0 || x == sx1;
						for (int y = base; y <= base + 2; y++) {
							d.set(x, y, z1, x == sx0 || x == sx1 ? frame.log(Direction.Axis.Y) : boards.full());
							if (post) {
								d.set(x, y, sz0, frame.log(Direction.Axis.Y));
							}
						}
						for (int z = sz0 + 1; z < z1; z++) {
							if (x == sx0 || x == sx1) {
								for (int y = base; y <= base + 2; y++) {
									d.set(x, y, z, boards.full());
								}
							} else if (post && fence.has(Form.FENCE)) {
								d.set(x, base, z, fence.fence());
							}
						}
					}
				}
				Roofs.Field lean = Roofs.shed(sx0, sz0 - 1, sx1, z1, Dirs.BACK, Roofs.Profile.straight(0.5));
				Roofs.raster(d, lean, base + 3, sx0, sz0 - 1, sx1, z1, d.mat(Role.ROOF), d.mat(Role.ROOF_TRIM), Habits.Ridge.SLAB);
				try (var s = d.stage(StageKind.INTERIOR)) {
					for (int x = sx0 + 1; x < sx1; x++) {
						if ((x - sx0) % 3 == 1 && d.isEmpty(x, base, z1 - 1)) {
							d.set(x, base, z1 - 1, Blocks.HAY_BLOCK.defaultBlockState());
						} else if ((x - sx0) % 3 == 2 && d.isEmpty(x, base, z1 - 1)) {
							d.set(x, base, z1 - 1, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
						}
					}
				}
			}
		}
		// a woodpile and a cart of barrels along the far side, a trough by the arch
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
}
