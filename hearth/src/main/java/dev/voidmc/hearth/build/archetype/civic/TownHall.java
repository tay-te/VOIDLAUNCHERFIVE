package dev.voidmc.hearth.build.archetype.civic;

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
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.Half;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Chimneys;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Floors;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Porches;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Walls;
import dev.voidmc.hearth.build.kit.civic.Adobe;
import dev.voidmc.hearth.build.kit.civic.Arches;
import dev.voidmc.hearth.build.kit.civic.Bohemian;
import dev.voidmc.hearth.build.kit.civic.Grounds;
import dev.voidmc.hearth.build.kit.civic.Halls;
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

/**
 * {@code hearth:civic/town_hall}: the seat of a town's council on its square. An open loggia of arches along the
 * ground floor shelters the door and the notice board; over it the council chamber, tall-windowed, opens onto a balcony
 * hung with the town's banners; a tower rises from the middle of the front (timber, alpine, log and raised traditions:
 * a clock tower with a spire, an onion or a pyramid) or from a front corner (stone, mediterranean and adobe ones: a
 * campanile with battlements or a dome), its clock faces turned to the square and its bell in the open belfry. A flag
 * with the town's arms flies before it.
 *
 * <p>Inside: an entrance hall with the stair and the clerk's desk and the records; upstairs the chamber with its long
 * table, the mayor's chair at its head, banners on the walls and lanterns over the table.
 *
 * <p>POIs: MEETING_POINT at the chamber's table, SEATs, NOTICE_BOARD by the door, STORAGE (the records), DOOR,
 * VIEWPOINT on the balcony.
 *
 * <p>Params:
 * <ul>
 *   <li>{@code banner} (string): the town's arms, {@code field;colour:pattern;...} (default by seed from the style's cloth)</li>
 *   <li>{@code tower} (string): {@code centre}, {@code corner} or {@code none} (default by tradition)</li>
 *   <li>{@code variant} (int): the style variant</li>
 * </ul>
 */
public final class TownHall implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("civic/town_hall"), ArchetypeCategory.CIVIC, Set.of(), 0,
		13, 19, 13, 19, 14, 36, SettlementTier.TOWN, Set.of(), Set.of(ArchetypeInfo.SiteFlag.CENTRAL), List.of(), 1, 1.0);

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
		// the timber-framing towns build their hall the Bohemian way (a radnice): white render, an arcade of round arches
		// on the square, a corner tower with a gallery under a dark helm or onion, a red tile roof with a show gable
		boolean boh = t == Tradition.TIMBER;
		String tw = d.param("tower", switch (t) {
			case MEDITERRANEAN, STONE, ADOBE, TIMBER -> "corner";
			default -> "centre";
		});
		boolean corner = tw.equals("corner");
		boolean centre = tw.equals("centre");
		boolean cornerRight = d.chance(0.5);

		// --- the plan: forecourt, the hall's block, a loggia inside its front, the tower
		int x0 = 1;
		int x1 = w - 2;
		int cx = (x0 + x1) / 2;
		int zF = raised ? 4 : 3;
		int z0 = zF;
		int md = Math.min(dp - zF - 1, 12);
		int z1 = z0 + md - 1;
		int loggia = 2;
		int h0 = boh ? 6 : 5;
		int h1 = maxH >= 22 ? 6 : 5;
		int base = raised ? Grounds.stiltBase(d, x0, z0, x1, z1) : Grounds.raisedBase(d, x0, x1, z0);
		Body body = new Body(x0, z0, x1, z1, base, new int[]{h0, h1}, false, false);
		d.body(x0, z0, x1, z1);
		int plate = body.plateY();
		d.eave(plate);
		int fy = body.floorY(1);
		int tsz = 5;
		int tx0 = centre ? cx - 2 : cornerRight ? x1 - tsz + 1 : x0;
		int tz0 = z0 - (centre ? 1 : 0);

		// --- ground and shell
		Storeys.ground(d, body, raised);
		Grounds.cut(d, x0, z0, x1, z0 + loggia, base);
		Grounds.forecourt(d, 0, w - 1, z0 - 1, !raised);
		Facade fac = Facade.plan(d, body, -1, 1, 1, 0.9);
		Storeys.walls(d, body, fac, 0, 1, boh ? new Habits.WallKind[]{Habits.WallKind.PLASTER, Habits.WallKind.PLASTER}
			: Halls.masonry(d) ? null : Storeys.stoneBase(d, 2), true);
		if (!Halls.masonry(d) && !raised && !boh) {
			Storeys.stoneStorey(d, body, 0);
			d.carve(x0 + 1, base, z0 + 1, x1 - 1, fy - 2, z1 - 1);
		}
		// the loggia: the front wall becomes an arcade, the hall's own front wall stands two rows in
		Side front = body.side(0, Dirs.FRONT);
		Arches.Kind ak = boh ? Arches.Kind.ROUND : Arches.kind(t);
		if (ak == Arches.Kind.POSTED) {
			ak = Arches.Kind.BRACED;
		}
		int[] lp = Storeys.oddPiers(0, front.last(), Arches.timber(ak) ? 3 : 4);
		d.clearBox(x0 + 1, base, z0 + 1, x1 - 1, fy - 2, z0 + loggia);
		// a Bohemian arcade is rendered with the walls, squat piers and round arches turned in dressed stone
		Arches.arcade(d, front, lp, base, fy - 2, ak, boh ? d.mat(Role.WALL).full() : Arches.pierPaint(d, ak), boh ? d.mat(Role.WALL).full()
			: Arches.timber(ak) ? null : d.mat(Role.WALL_ACCENT).full());
		int iz = z0 + loggia + 1;
		Body hall = new Body(x0, iz, x1, z1, base, new int[]{h0}, false, false);
		Side inner = hall.side(0, Dirs.FRONT);
		if (Halls.masonry(d) || raised || boh) {
			Walls.side(d, hall, 0, inner, new Facade(), boh ? Habits.WallKind.PLASTER : d.habits().walls().kind());
		} else {
			Storeys.stoneStorey(d, hall, 0);
			d.carve(x0 + 1, base, iz + 1, x1 - 1, fy - 2, z1 - 1);
		}
		Halls.floor(d, x0 + 1, z0 + 1, x1 - 1, z0 + loggia, base - 1, true);
		// the loggia's ceiling is the chamber's floor: beams across it
		try (var s = d.stage(StageKind.FRAME)) {
			for (int k = 1; k < lp.length - 1; k++) {
				int x = front.x(lp[k]);
				for (int z = z0 + 1; z < iz; z++) {
					d.set(x, fy - 2, z, d.mat(Role.BEAM).log(Direction.Axis.Z));
				}
			}
		}
		// windows: the chamber's tall ones, the hall's below
		if (boh) {
			// the chamber's windows framed in dressed stone, tall under pointed heads
			dev.voidmc.hearth.build.kit.landmark.Stone dress = Bohemian.dressing(d);
			for (Side s : body.sides(1)) {
				for (Facade.Opening o : fac.openings(s.out())) {
					if (s.contains(o.at()) && s.contains(o.at() + o.width() - 1) && s.index(o.at()) > 0 && s.index(o.at() + o.width() - 1) < s.last()) {
						framedWindow(d, s, Math.min(s.index(o.at()), s.index(o.at() + o.width() - 1)), o.width(), fy + 1, h1 >= 6 ? 3 : 2, dress);
					}
				}
			}
		} else {
			Storeys.windows(d, body, fac, 1, 1, h1 >= 6 ? 3 : 2);
		}
		int doorX = centre ? cx : cornerRight ? cx - 1 : cx + 1;
		Openings.door(d, inner, doorX, base, 1, new Openings.DoorOpts(false, true, true));
		for (Side s : new Side[]{hall.side(0, Dirs.LEFT), hall.side(0, Dirs.RIGHT), hall.side(0, Dirs.BACK)}) {
			for (Facade.Opening o : fac.openings(s.out())) {
				if (o.kind() == Facade.Kind.WINDOW && s.contains(o.at()) && s.index(o.at()) > 0 && s.index(o.at()) < s.last()) {
					Openings.window(d, s, o.at(), base + 1, o.width(), 2, Openings.WindowStyle.PLAIN);
				}
			}
		}
		for (int x : new int[]{doorX - 3, doorX + 3}) {
			if (x > x0 && x < x1 && Openings.isWallBlock(d, x, base + 1, iz)) {
				Openings.window(d, inner, x, base + 1, 1, 2, Openings.WindowStyle.PLAIN);
			}
		}

		// --- the roof (a hip or the culture's form), a terrace roof with battlements in the stone south
		RoofForm form = switch (t) {
			case ADOBE -> RoofForm.FLAT;
			case KARST -> RoofForm.PAGODA;
			case MEDITERRANEAN -> RoofForm.HIP;
			default -> h.roof().pick(d.random(), RoofForm.HIP, RoofForm.HALF_HIP, RoofForm.GABLE, RoofForm.HIP);
		};
		Storeys.Top top;
		if (t == Tradition.ADOBE) {
			// a crested parapet, the beam ends in a row, a shaded terrace on the roof; screens before the chamber's windows
			int[] tr = body.topRect();
			int deck = Adobe.roof(d, tr[0], tr[1], tr[2], tr[3], plate, true, !centre);
			top = new Storeys.Top(null, deck, deck + 2);
			Halls.roofTerrace(d, tr, deck, !cornerRight);
			Adobe.screens(d, body, body.storeys() - 1);
		} else if (boh && bohRoofFits(d, body)) {
			top = bohemianRoof(d, body, corner ? (cornerRight ? 1 : -1) : 0);
		} else {
			top = Storeys.roof(d, body, form, Direction.Axis.X, Math.max(1.0, h.roof().pitch()), centre || corner ? 12 : 1, true);
		}
		int skyline = top.ridge();
		if (form == RoofForm.FLAT && t != Tradition.ADOBE) {
			Towers.battlements(d, x0, z0, x1, z1, top.deck());
		}

		// --- the tower
		if (boh && corner && maxH - 1 - (plate + 3 + Bohemian.BELFRY) >= 6) {
			skyline = Math.max(skyline, bohemianTower(d, tx0, tz0, base, plate, fy - 1, maxH, tier, cornerRight, z0));
		} else if (centre || corner) {
			skyline = Math.max(skyline, tower(d, t, tx0, tz0, tsz, base, plate, top, maxH, tier, centre));
		} else if (top.roof() != null) {
			skyline = Math.max(skyline, ChapelWestern.turret(d, t, cx, (z0 + z1) / 2 - 1, plate, top.roof().ridgeY, maxH));
		}

		// --- the balcony over the loggia with the banners, the flag, the notice board, steps
		Signs.Arms arms = Signs.arms(d);
		Side upper = body.side(1, Dirs.FRONT);
		int bx0 = centre ? cx - 4 : cx - 1;
		int bx1 = centre ? cx + 4 : cx + 1;
		if (boh) {
			// an oriel on corbels over the arcade, under the show gable
			oriel(d, upper, bohGableCentre(body, corner ? (cornerRight ? 1 : -1) : 0), fy, h1);
		} else if (centre) {
			Porches.balcony(d, upper, Math.max(x0 + 1, bx0), cx - 3, fy);
			Porches.balcony(d, upper, cx + 3, Math.min(x1 - 1, bx1), fy);
		} else {
			Porches.balcony(d, upper, bx0, bx1, fy);
		}
		for (int x : boh ? new int[0] : centre ? new int[]{cx - 4, cx + 4} : new int[]{cx}) {
			if (x <= x0 || x >= x1) {
				continue;
			}
			int fx = x;
			d.inStage(StageKind.OPENINGS, () -> d.door(fx, fy, z0, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), Dirs.FRONT,
				net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT));
		}
		// banners hung from the balcony's rail over the loggia (from the arcade's piers in a Bohemian hall)
		if (boh) {
			for (int k = 0; k < lp.length; k++) {
				int x = front.x(lp[k]);
				if (x <= x0 || x >= x1 || corner && x >= tx0 - 1 && x <= tx0 + tsz) {
					continue;
				}
				if (z0 - 1 >= 0 && d.isEmpty(x, fy - 3, z0 - 1) && Openings.isWallBlock(d, x, fy - 3, z0)) {
					Signs.wallBanner(d, x, fy - 3, z0 - 1, Dirs.FRONT, arms.field(), arms.layers());
				}
			}
		}
		for (int x = x0 + 2; x <= x1 - 2 && !boh; x += 2) {
			if (centre && Math.abs(x - cx) <= 2) {
				continue;
			}
			int bz = z0 - 1;
			if (d.isEmpty(x, fy - 2, bz) && !d.isEmpty(x, fy - 1, bz) && (bz >= 0 || fy - 2 >= 3)) {
				Signs.wallBanner(d, x, fy - 2, bz, Dirs.FRONT, arms.field(), arms.layers());
			}
		}
		// the flag before the hall and the notice board by the loggia's mouth
		int fz = Math.max(0, z0 - 2);
		if (!raised && Groundwork.surface(d, cx + 3, fz) == 0 && cx + 3 < w) {
			Towers.flagpole(d, centre ? x0 : cx + 3, 0, fz, 5, Dirs.FRONT, arms.field(), arms.layers());
		}
		// the notice board in the loggia beside the door (or, failing room there, in the forecourt by the steps)
		boolean board = false;
		for (int off : new int[]{2, -2, 3, -3, 4, -4}) {
			int nbx = doorX + off;
			if (nbx > x0 && nbx < x1 && d.isEmpty(nbx, base, iz - 1) && d.isEmpty(nbx, base + 1, iz - 1) && d.isSolid(nbx, base - 1, iz - 1)
				&& d.isEmpty(nbx, base, iz - 2) && d.isEmpty(nbx, base + 1, iz - 2)) {
				Signs.noticeBoard(d, nbx, base, iz - 1, Dirs.FRONT);
				board = true;
				break;
			}
		}
		if (!board) {
			for (int x = 1; x < w - 1 && !board; x++) {
				int bz = Math.max(1, z0 - 2);
				if (Math.abs(x - cx) > 2 && d.isEmpty(x, 0, bz) && d.isEmpty(x, 1, bz) && Groundwork.surface(d, x, bz) == 0
					&& d.isEmpty(x, 0, bz - 1)) {
					Signs.noticeBoard(d, x, 0, bz, Dirs.FRONT);
					board = true;
				}
			}
		}
		if (base >= 1 && !raised) {
			Grounds.flight(d, x0, x1, z0 - 1, base, base);
		}
		int mx = front.x(lp[lp.length / 2 - (lp.length % 2 == 0 ? 1 : 0)]) + 1;
		int entryX = centre ? cx : (front.x(lp[lp.length / 2]) + front.x(lp[lp.length / 2 - 1])) / 2;
		d.entrance(entryX, base, z0 - 1);
		Grounds.approach(d, entryX, base, z0 - 1);
		if (!raised) {
			Halls.lamp(d, 0, 0, Math.max(0, z0 - 1), 2);
			Halls.lamp(d, w - 1, 0, Math.max(0, z0 - 1), 2);
		}

		// --- inside: the hall with its stair, desk and records; the chamber upstairs
		int ix0 = x0 + 1;
		int ix1 = x1 - 1;
		int iz0 = iz + 1;
		int iz1 = z1 - 1;
		// a stair against the back wall, climbing toward the side away from the door
		int rise = h0;
		boolean stairLeft = doorX > cx;
		int sx = stairLeft ? ix0 : ix1;
		Direction sdir = stairLeft ? Dirs.RIGHT : Dirs.LEFT;
		int[] land = Storeys.flight(d, body, 0, sx, iz1, sdir);
		Rooms.Room entry = new Rooms.Room(ix0, iz0, ix1, iz1, base, h0 - 1);
		entry.keep(doorX, iz0);
		entry.keepArea(sx, iz1, sx + Dirs.dx(sdir) * (rise - 1), iz1);
		entry.keep(sx, iz1 - 1);
		// the clerk's lectern with the town's book, the records beside it
		int lx = stairLeft ? ix1 : ix0;
		d.inStage(StageKind.INTERIOR, () -> d.set(lx, base, iz0, dev.voidmc.hearth.build.draft.States.facing(Blocks.LECTERN,
			stairLeft ? Dirs.LEFT : Dirs.RIGHT), dev.voidmc.hearth.build.draft.BlockEntities.lectern("Town Book", "The Clerk",
			List.of("Minutes of the council."))));
		d.poi(PoiType.LECTERN, lx + (stairLeft ? -1 : 1), base, iz0, stairLeft ? Dirs.RIGHT : Dirs.LEFT);
		Furniture.chest(d, stairLeft ? ix1 : ix0, base, iz0 + 1, stairLeft ? Dirs.LEFT : Dirs.RIGHT, List.of(new Object[]{"minecraft:paper", 24},
			new Object[]{"minecraft:writable_book", 2}));
		entry.block(stairLeft ? ix1 : ix0, iz0);
		entry.block(stairLeft ? ix1 : ix0, iz0 + 1);
		// a runner from the door toward the stair, benches along the walls for those waiting on the council
		Mat cloth = d.mat(Role.CLOTH);
		if (cloth.has(Form.CARPET)) {
			try (var s = d.stage(StageKind.INTERIOR)) {
				for (int z = iz0; z < iz1; z++) {
					if (d.isEmpty(doorX, base, z) && d.isSolid(doorX, base - 1, z)) {
						d.set(doorX, base, z, cloth.carpet());
					}
				}
			}
		}
		int benches = 0;
		for (int z = iz0 + 2; z < iz1 - 1 && benches < 4; z++) {
			for (int x : new int[]{stairLeft ? ix1 : ix0, stairLeft ? ix0 : ix1}) {
				Direction back = x == ix0 ? Dirs.LEFT : Dirs.RIGHT;
				if (benches < 4 && d.isEmpty(x, base, z) && d.isEmpty(x, base + 1, z) && entry.claim(x, z)) {
					Furniture.chair(d, x, base, z, back);
					benches++;
				}
			}
		}
		Rooms.furnish(d, entry, Rooms.Type.HALL, 0, JobType.NONE);
		Floors.joists(d, ix0, iz0, ix1, iz1, fy - 1, Direction.Axis.Z, 3, base);
		// the chamber: the long table down the middle, the mayor's chair at its head on a dais
		int[] up = body.rect(1);
		int cz0 = up[1] + 2;
		int cz1 = up[3] - 2;
		int tl = Math.max(3, Math.min(7, cz1 - cz0 - 1));
		int tz = cz0 + 1;
		Furniture.longTable(d, cx, fy, tz, Dirs.BACK, tl);
		for (int z = tz + 1; z < tz + tl - 1; z++) {
			for (int x : new int[]{cx - 1, cx + 1}) {
				if (d.isEmpty(x, fy, z)) {
					Furniture.chair(d, x, fy, z, x < cx ? Dirs.LEFT : Dirs.RIGHT);
				}
			}
		}
		int headZ = tz + tl;
		if (headZ < up[3] - 1 && d.isEmpty(cx, fy, headZ)) {
			Furniture.chair(d, cx, fy, headZ, Dirs.BACK);
		}
		d.poi(PoiType.MEETING_POINT, cx - 1 >= up[0] + 1 && d.isEmpty(cx - 2, fy, tz) ? cx - 2 : cx, fy, tz, Dirs.RIGHT);
		// banners of the arms round the chamber, lanterns over the table, the town chest
		for (int x : new int[]{up[0] + 1, up[2] - 1}) {
			for (int z = up[1] + 2; z <= up[3] - 2; z += 3) {
				if (d.isEmpty(x, fy + 2, z) && Openings.isWallBlock(d, x + (x == up[0] + 1 ? -1 : 1), fy + 2, z)) {
					Signs.wallBanner(d, x, fy + 2, z, x == up[0] + 1 ? Dirs.RIGHT : Dirs.LEFT, arms.field(), arms.layers());
				}
			}
		}
		for (int z = tz; z < tz + tl; z += 2) {
			Halls.lanternUnder(d, cx, fy, z, 2);
		}
		Furniture.chest(d, stairLeft ? up[2] - 1 : up[0] + 1, fy, up[3] - 1, stairLeft ? Dirs.LEFT : Dirs.RIGHT, List.of(new Object[]{"minecraft:gold_ingot", 4},
			new Object[]{"minecraft:map", 1}));
		// the hearth on a side wall, its chimney
		int hx = stairLeft ? up[2] - 1 : up[0] + 1;
		int hz = (up[1] + up[3]) / 2 + 1;
		if (d.isEmpty(hx, fy, hz) && top.roof() != null) {
			Furniture.hearth(d, hx, fy, hz, stairLeft ? Dirs.LEFT : Dirs.RIGHT, false);
			// (a rendered hall carries its flue inside, up through the roof, not as a brick stripe on its white gable)
			Chimneys.stack(d, boh ? hx : stairLeft ? up[2] : up[0], hz, fy + 1, top.roof(), top.roof().ridgeY);
		}
		if (h.snow()) {
			Garden.snowBanks(d, x0, z0, x1, z1, base, entryX);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, skyline + 2);
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	// ------------------------------------------------------------------------------------------------ the Bohemian hall

	/** The show gable's columns {from, width} over the front of a Bohemian hall, clear of a corner tower (-1 left, 1 right). */
	static int[] bohGable(Body body, int towerSide) {
		int[] r = body.rect(body.storeys() - 1);
		int f0 = towerSide < 0 ? r[0] + 6 : r[0] + 2;
		int f1 = towerSide > 0 ? r[2] - 6 : r[2] - 2;
		int room = f1 - f0 + 1;
		int width = room >= 7 ? 7 : room >= 5 ? 5 : 0;
		int c = (f0 + f1) / 2;
		return new int[]{c - width / 2, width};
	}

	/** The column under the show gable's middle (the middle of the front clear of the tower when there is none). */
	static int bohGableCentre(Body body, int towerSide) {
		int[] g = bohGable(body, towerSide);
		int[] r = body.rect(body.storeys() - 1);
		if (g[1] > 0) {
			return g[0] + g[1] / 2;
		}
		return towerSide < 0 ? (r[0] + 6 + r[2]) / 2 : towerSide > 0 ? (r[0] + r[2] - 6) / 2 : (r[0] + r[2]) / 2;
	}

	/** Whether a plain gable roof (a block per cell) fits over the body under the plot's height. */
	static boolean bohRoofFits(Draft d, Body body) {
		int[] tr = body.topRect();
		double half = (tr[3] - tr[1] + 1) / 2.0 + 1;
		return body.plateY() + 1 + half + 1 <= d.maxHeight() - 1;
	}

	/**
	 * The Bohemian hall's roof: a gable along the square at a block per cell, its ends rendered, and over the front a show
	 * gable (a cross gable carried up toward the main ridge) rendered with the walls, a dressed light in its peak.
	 */
	static Storeys.Top bohemianRoof(Draft d, Body body, int towerSide) {
		int[] tr = body.topRect();
		int plate = body.plateY();
		Roofs.Opts o = Roofs.Opts.of(d).withPitch(1.0).withEaves(1, 0);
		Roofs.Plan plan = Roofs.plan(d, tr[0], tr[1], tr[2], tr[3], plate, RoofForm.GABLE, Direction.Axis.X, o, false, false);
		int[] g = bohGable(body, towerSide);
		Side front = body.side(body.storeys() - 1, Dirs.FRONT);
		if (g[1] >= 5) {
			plan.crossGable(front, g[0], g[1], d);
		}
		Roofs.Built b = plan.draw(d);
		Roofs.Roof roof = b.roof();
		Mat wall = d.mat(Role.WALL);
		dev.voidmc.hearth.build.kit.landmark.Stone dress = Bohemian.dressing(d);
		// the gable walls in render: the two ends and the show gable (no timber in them)
		try (var st = d.stage(StageKind.WALLS)) {
			for (int z = tr[1]; z <= tr[3]; z++) {
				for (int x : new int[]{tr[0], tr[2]}) {
					renderUp(d, roof, x, z, plate, wall);
				}
			}
			for (int x = g[0]; x < g[0] + g[1] && g[1] >= 5; x++) {
				renderUp(d, roof, x, tr[1], plate, wall);
			}
		}
		// a light high in the show gable, dressed round: the attic over the chamber (no crow steps or pinnacles, which are
		// the Hanse's, not Bohemia's)
		if (g[1] >= 5) {
			int gc = g[0] + g[1] / 2;
			int z = tr[1];
			if (roof.covers(gc, z) && roof.bottomY(gc, z) - 1 >= plate + 3) {
				try (var st = d.stage(StageKind.OPENINGS)) {
					d.set(gc, plate + 2, z, d.mat(Role.GLASS).pane());
					d.set(gc, plate + 1, z, dress.full());
					if (roof.bottomY(gc, z) - 1 > plate + 3) {
						d.set(gc, plate + 3, z, dress.full());
					}
				}
			}
		}
		return new Storeys.Top(roof, Integer.MIN_VALUE, roof.ridgeY);
	}

	/**
	 * A window of a Bohemian hall: glass {@code width} by {@code h} from cell i0 of wall s at y, a head of the dressing over
	 * it and a dressed sill under it outside; the render round it left plain.
	 */
	static void framedWindow(Draft d, Side s, int i0, int width, int y, int h, dev.voidmc.hearth.build.kit.landmark.Stone dress) {
		Paint glass = d.mat(Role.GLASS).pane();
		try (var st = d.stage(StageKind.OPENINGS)) {
			for (int c = 0; c < width; c++) {
				int i = i0 + c;
				for (int yy = y; yy < y + h; yy++) {
					d.set(s.x(i), yy, s.z(i), glass);
				}
				if (Openings.isWallBlock(d, s.x(i), y + h, s.z(i))) {
					d.set(s.x(i), y + h, s.z(i), dress.full());
				}
				int ox = s.outX(i, 1);
				int oz = s.outZ(i, 1);
				if (Walls.inPlot(d, ox, oz) && d.isEmpty(ox, y - 1, oz) && (oz >= 0 || y - 1 >= 3)) {
					d.set(ox, y - 1, oz, dress.stairsOrSlab(s.out().getOpposite(), Half.TOP));
				}
			}
		}
	}

	/** A wall column from over the plate to the roof's underside, in render where it is wall (glass and lights kept). */
	private static void renderUp(Draft d, Roofs.Roof roof, int x, int z, int plate, Mat wall) {
		if (!roof.covers(x, z)) {
			return;
		}
		for (int y = plate + 1; y < roof.bottomY(x, z); y++) {
			if (Openings.isWallBlock(d, x, y, z)) {
				d.set(x, y, z, wall.full());
			}
		}
	}

	/**
	 * The Bohemian hall's corner tower ({@link Bohemian#tower}): its foot the arcade's corner bay (an arch toward the square
	 * and one into the loggia), its faces against the hall blind, clocks toward the square and along the street, the
	 * ringer's ladder against its back wall. Returns the top.
	 */
	static int bohemianTower(Draft d, int tx0, int tz0, int base, int plate, int doorTop, int maxH, int tier, boolean right, int z0) {
		Direction inner = right ? Dirs.LEFT : Dirs.RIGHT;
		Direction outer = inner.getOpposite();
		// the belfry clear of the roof where it meets the tower's back and inner faces
		int yb = Bohemian.belfryFloor(plate, plate + 6, maxH);
		int[] ladder = {right ? tx0 + 3 : tx0 + 1, tz0 + 3};
		int top = Bohemian.tower(d, tx0, tz0, base, doorTop, yb, Bohemian.BELFRY, maxH, tier, Set.of(Dirs.BACK, inner), Set.of(Dirs.FRONT, outer),
			ladder, Dirs.FRONT, true, Bohemian.townCap(d, tier), true);
		dev.voidmc.hearth.build.kit.landmark.Stone dress = Bohemian.dressing(d);
		Side f = Side.of(tx0, tz0, tx0 + 4, tz0 + 4, Dirs.FRONT);
		try (var st = d.stage(StageKind.WALLS)) {
			dev.voidmc.hearth.build.kit.landmark.Details.arch(d, f, 1, 3, base, 4, dress, null);
			Side in = Side.of(tx0, tz0, tx0 + 4, tz0 + 4, inner);
			int i0 = Math.min(in.index(z0 + 1), in.index(z0 + 2));
			dev.voidmc.hearth.build.kit.landmark.Details.arch(d, in, i0, 2, base, 4, dress, null);
		}
		return top;
	}

	/**
	 * An oriel on the upper front at column c: three cells a block proud of the wall on stepped corbels, a panel of the
	 * dressing at the sill, a band of glass, a dressed head and a little roof sloping off it; the wall behind opened so it
	 * is part of the chamber.
	 */
	static void oriel(Draft d, Side upper, int c, int fy, int h1) {
		int i = upper.index(c);
		if (i - 1 < 1 || i + 1 > upper.last() - 1) {
			return;
		}
		int oz = upper.outZ(i, 1);
		if (oz < 0 && fy - 2 < 3 || oz < -1) {
			return;
		}
		dev.voidmc.hearth.build.kit.landmark.Stone dress = Bohemian.dressing(d);
		Mat roof = d.mat(Role.ROOF);
		Paint glass = d.mat(Role.GLASS).pane();
		Direction in = upper.out().getOpposite();
		int h = Math.max(2, Math.min(3, h1 - 3));
		try (var st = d.stage(StageKind.WALLS)) {
			for (int k = -1; k <= 1; k++) {
				int x = upper.outX(i + k, 1);
				int z = upper.outZ(i + k, 1);
				d.set(x, fy - 1, z, dress.stairsOrSlab(in, Half.TOP));
				if (k == 0) {
					d.set(x, fy - 2, z, dress.stairsOrSlab(in, Half.TOP));
				}
				d.set(x, fy, z, dress.full());
				for (int y = fy + 1; y < fy + h; y++) {
					d.set(x, y, z, glass);
				}
				d.set(x, fy + h, z, dress.full());
				d.set(x, fy + h + 1, z, roof.has(Form.SLAB) ? roof.slab(SlabType.BOTTOM) : roof.stairsOrSlab(in, Half.BOTTOM));
			}
		}
		// the wall behind opened into the chamber over the sill
		for (int k = -1; k <= 1; k++) {
			for (int y = fy + 1; y < fy + h; y++) {
				d.set(upper.x(i + k), y, upper.z(i + k), Blocks.AIR.defaultBlockState());
			}
		}
	}

	/**
	 * The tower: five square from the ground (projecting a block from the front in the middle, or standing at a front
	 * corner), its shaft with quoins and string courses rising through the roof, clock faces under the belfry, the bell,
	 * and its cap. In the middle its ground floor is the arched way into the loggia. Returns the top.
	 */
	static int tower(Draft d, Tradition t, int x0, int z0, int size, int base, int plate, Storeys.Top roof, int maxH, int tier, boolean centre) {
		Groundwork.footings(d, x0, z0, x0 + size - 1, z0 + size - 1, base - 1);
		int ridge = roof.ridge();
		int bh = 4;
		Towers.Cap cap = t == Tradition.ADOBE ? Towers.Cap.DOME : t == Tradition.MEDITERRANEAN || t == Tradition.STONE && d.chance(0.5)
			? Towers.Cap.BATTLEMENT : Towers.cap(t, true);
		if (cap == Towers.Cap.BATTLEMENT && t == Tradition.MEDITERRANEAN) {
			cap = Towers.Cap.HIP;
		}
		int capRoom = cap == Towers.Cap.SPIRE ? 8 : cap == Towers.Cap.ONION ? 7 : cap == Towers.Cap.PAGODA ? 5 : 4;
		int yb = Math.max(ridge + 4, plate + 6);
		if (yb + bh + capRoom > maxH - 1) {
			yb = Math.max(plate + 2, maxH - 1 - bh - capRoom);
		}
		Towers.shaft(d, x0, z0, size, base, yb - 1, t, plate + 1);
		d.carve(x0 + 1, base, z0 + 1, x0 + size - 2, yb - 2, z0 + size - 2);
		Side f = Side.of(x0, z0, x0 + size - 1, z0 + size - 1, Dirs.FRONT);
		if (centre) {
			// the way in: an arch through the front and the back of the tower's foot
			Arches.Kind ak = Arches.timber(Arches.kind(t)) ? Arches.Kind.ROUND : Arches.kind(t);
			Arches.arcade(d, f, new int[]{0, size - 1}, base, base + 4, ak, d.mat(Role.WALL_ACCENT).full(), d.mat(Role.WALL_ACCENT).full());
			Side b = Side.of(x0, z0, x0 + size - 1, z0 + size - 1, Dirs.BACK);
			Arches.arcade(d, b, new int[]{0, size - 1}, base, base + 4, ak, d.mat(Role.WALL_ACCENT).full(), d.mat(Role.WALL_ACCENT).full());
			Floors.boards(d, x0 + 1, z0 + 1, x0 + size - 2, z0 + size - 2, base + 4);
			Halls.floor(d, x0, z0, x0 + size - 1, z0 + size - 1, base - 1, true);
			Halls.lanternUnder(d, x0 + 2, base, z0 + 2, 1);
		}
		// lights up the shaft, the town's banner on its front, clocks under the belfry
		for (int y = base + 7; y < yb - 3; y += 3) {
			for (Side s : Side.of(x0, z0, x0 + size - 1, z0 + size - 1)) {
				int yy = y;
				if (Openings.isWallBlock(d, s.x(2), yy, s.z(2)) && d.isEmpty(s.outX(2, 1), yy, s.outZ(2, 1))) {
					d.inStage(StageKind.OPENINGS, () -> d.set(s.x(2), yy, s.z(2), d.mat(Role.GLASS).pane()));
				}
			}
		}
		// the clock faces the square (and the back): dials on every face read as eyes from a corner
		for (Side s : Side.of(x0, z0, x0 + size - 1, z0 + size - 1)) {
			if (s.out() == Dirs.FRONT || s.out() == Dirs.BACK && yb - 2 > ridge) {
				Towers.clock(d, s, 2, yb - 2, false);
			}
		}
		Signs.Arms arms = Signs.arms(d);
		if (f.outZ(2, 1) >= 0 || base + 6 >= 3) {
			Signs.wallBanner(d, f.outX(2, 1), base + 7, f.outZ(2, 1), Dirs.FRONT, arms.field(), arms.layers());
		}
		Towers.belfry(d, x0, z0, size, yb, bh, t, true);
		Towers.lookout(d, x0 + 1, yb, z0 + 1, Dirs.FRONT, false);
		int top = Towers.cap(d, x0, z0, size, yb + bh, cap, maxH);
		if (cap == Towers.Cap.PAGODA && top + 4 < maxH - 1) {
			// a second, smaller tier of the pagoda over the first
			Towers.shaft(d, x0 + 1, z0 + 1, size - 2, top - 1, top + 1, t, -1);
			top = Towers.cap(d, x0 + 1, z0 + 1, size - 2, top + 1, Towers.Cap.PAGODA, maxH);
		}
		return top;
	}
}
