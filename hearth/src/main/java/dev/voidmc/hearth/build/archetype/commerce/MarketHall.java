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
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Chimneys;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Porches;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Walls;
import dev.voidmc.hearth.build.kit.civic.Adobe;
import dev.voidmc.hearth.build.kit.civic.Arches;
import dev.voidmc.hearth.build.kit.civic.Fam;
import dev.voidmc.hearth.build.kit.civic.Grounds;
import dev.voidmc.hearth.build.kit.civic.Halls;
import dev.voidmc.hearth.build.kit.civic.Signs;
import dev.voidmc.hearth.build.kit.civic.Storeys;
import dev.voidmc.hearth.build.kit.civic.Towers;
import dev.voidmc.hearth.build.kit.civic.Trade;
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
 * {@code hearth:commerce/market_hall}: the covered market of a town's square. An open ground floor of arcades (braced
 * oak posts under a jettied timber hall in the timber cultures, round stone arches under plaster in the stone and
 * mediterranean ones, pointed arches under a flat roof of little domes in the desert, lacquered columns with bracket
 * sets in the karst) shelters the merchants' counters; an enclosed bay at the back holds the stair and the stores; the
 * guild hall upstairs has its long table, its hearth and the town's banners; a cupola with a bell (and a clock in towns
 * and cities) stands over the middle of the roof.
 *
 * <p>POIs: COUNTER and MARKET_STALL at every counter, the MERCHANTs' JOB_SITEs behind them, STORAGE in the stores, a
 * MEETING_POINT at the hall's table, SEATs, a HEARTH, the DOOR at the middle arch.
 *
 * <p>Params:
 * <ul>
 *   <li>{@code roof} (string, a {@code RoofForm} name): the roof (default from the culture's habits)</li>
 *   <li>{@code cupola} (boolean): the bell cupola on the roof (default true where the plot's height allows)</li>
 *   <li>{@code variant} (int): the style variant</li>
 * </ul>
 */
public final class MarketHall implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("commerce/market_hall"), ArchetypeCategory.COMMERCE, Set.of(JobType.MERCHANT), 0,
		11, 17, 13, 21, 12, 24, SettlementTier.TOWN, Set.of(), Set.of(ArchetypeInfo.SiteFlag.CENTRAL), List.of(), 1, 1.0);

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
		Arches.Kind arch = Arches.kind(t);
		boolean timber = Arches.timber(arch);
		boolean jetty = t == Tradition.TIMBER || t == Tradition.LOG;

		// --- the plan: an arcaded body, the back bay enclosed
		int x0 = 1;
		int x1 = w - 2;
		int zF = dp >= 16 ? 2 : 1;
		int z0 = zF + (jetty ? 1 : 0);
		int z1 = dp - 2;
		int cx = (x0 + x1) / 2;
		int h0 = 5;
		int h1 = maxH >= 20 ? 5 : 4;
		int base = t.raised() ? 0 : Grounds.raisedBase(d, x0, x1, z0);
		Body body = new Body(x0, z0, x1, z1, base, new int[]{h0, h1}, false, false);
		if (jetty) {
			body.jetty(1, Dirs.FRONT).jetty(1, Dirs.LEFT).jetty(1, Dirs.RIGHT).jetty(1, Dirs.BACK);
		}
		d.body(x0, z0, x1, z1);
		int plate = body.plateY();
		d.eave(plate);
		int[] up = body.rect(1);

		// --- ground: footings under the arcade, the floor paved, the forecourt
		Groundwork.footings(d, x0, z0, x1, z1, base - 1);
		Groundwork.apron(d, x0, z0, x1, z1, base, 2);
		Grounds.cut(d, x0, z0, x1, z1, base);
		Halls.floor(d, x0, z0, x1, z1, base - 1, true);
		Grounds.forecourt(d, 0, w - 1, z0 - 1, true);

		// --- the arcades on three sides, the back bay walled
		int zb = z1 - 3;
		Paint pier = Arches.pierPaint(d, arch);
		int pitch = timber ? 3 : 4;
		int top = base + h0 - 1;
		Side front = Side.of(x0, z0, x1, z1, Dirs.FRONT);
		int[] fp = Storeys.oddPiers(0, front.last(), pitch);
		Arches.arcade(d, front, fp, base, top, arch, pier, null);
		for (Direction sd : new Direction[]{Dirs.LEFT, Dirs.RIGHT}) {
			Side s = Side.of(x0, z0, x1, zb, sd);
			Arches.arcade(d, s, Arches.piers(0, s.last(), pitch), base, top, arch, pier, null);
		}
		// the back bay: the walls of the style (or stone) round the stair and the stores
		Body backBay = new Body(x0, zb, x1, z1, base, new int[]{h0}, false, false);
		Facade bf = new Facade();
		if (timber) {
			Walls.storey(d, backBay, 0, bf);
		} else {
			Storeys.stoneStorey(d, backBay, 0);
		}
		d.carve(x0 + 1, base, zb + 1, x1 - 1, top - 1, z1 - 1);
		// a door from the market into the back bay, a window in each end
		Side inner = Side.of(x0, zb, x1, z1, Dirs.FRONT);
		int doorX = x1 - 2;
		Openings.door(d, inner, doorX, base, 1, new Openings.DoorOpts(false, false, false));
		// interior columns down the middle of a wide hall
		int[] midCols = Arches.piers(z0, zb, pitch);
		if (x1 - x0 >= 10) {
			try (var s = d.stage(timber ? StageKind.FRAME : StageKind.WALLS)) {
				for (int k = 1; k < midCols.length - 1; k++) {
					for (int y = base; y < top; y++) {
						d.set(cx, y, midCols[k], pier);
					}
				}
			}
		}
		// beams under the hall floor
		Mat beam = d.mat(Role.BEAM);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int k = 1; k < midCols.length - 1; k++) {
				for (int x = x0 + 1; x <= x1 - 1; x++) {
					if (d.isEmpty(x, top - 1, midCols[k])) {
						d.set(x, top, midCols[k], beam.log(Direction.Axis.X));
					}
				}
			}
		}

		// --- the hall upstairs
		Facade fac = Facade.plan(d, body, -1, 1, h.windows().tall() && t != Tradition.KARST ? 1 : 1, 0.85);
		Halls.floor(d, up[0] + 1, up[1] + 1, up[2] - 1, up[3] - 1, body.floorY(1) - 1, false);
		dev.voidmc.hearth.build.kit.Floors.boards(d, up[0], up[1], up[2], up[3], body.floorY(1) - 1);
		Walls.storey(d, body, 1, fac);
		d.carve(up[0] + 1, body.floorY(1), up[1] + 1, up[2] - 1, plate - 1, up[3] - 1);
		Storeys.windows(d, body, fac, 1, 1, h1 >= 5 ? 3 : 2);
		if (Adobe.is(d)) {
			Adobe.screens(d, body, 1);
		}
		if (jetty && d.chance(h.walls().beamEnds())) {
			Walls.beamEnds(d, body, 1, fac, Dirs.FRONT);
		}

		// --- the roof and the cupola
		RoofForm form = roofForm(d, t);
		Direction.Axis ridge = form == RoofForm.GABLE && d.chance(Math.max(0.4, h.roof().frontGable())) ? Direction.Axis.Z
			: up[2] - up[0] >= up[3] - up[1] ? Direction.Axis.X : Direction.Axis.Z;
		boolean cupola = d.param("cupola", true) && form != RoofForm.FLAT;
		Storeys.Top roofTop = Storeys.roof(d, body, form, ridge, Math.max(h.roof().pitch(), 1.0), cupola ? 8 : 1, true);
		int skyline = roofTop.ridge();
		int cz = (z0 + z1) / 2;
		if (form == RoofForm.FLAT) {
			skyline = Math.max(skyline, domes(d, up, roofTop.deck(), maxH));
		} else if (cupola && roofTop.roof() != null) {
			skyline = Math.max(skyline, cupola(d, t, cx, cz, plate, roofTop.roof(), maxH, tier));
		}

		// --- the stair up from the back bay, the stores
		int sx = x0 + 1;
		int[] land = Storeys.flight(d, body, 0, sx, z1 - 1, Dirs.RIGHT);
		Furniture.chest(d, x1 - 1, base, z1 - 1, Dirs.LEFT, List.of(new Object[]{"minecraft:wheat", 16}, new Object[]{"minecraft:emerald", 2}));
		Furniture.barrel(d, x1 - 1, base, zb + 1, Direction.UP);

		// --- the market floor: counters in the side bays, merchants behind them, customers in the aisle
		int lane = 1;
		for (Direction sd : new Direction[]{Dirs.LEFT, Dirs.RIGHT}) {
			int cxRow = sd == Dirs.LEFT ? x0 + 1 + lane : x1 - 1 - lane;
			Direction toAisle = sd == Dirs.LEFT ? Dirs.RIGHT : Dirs.LEFT;
			int[] sp = Arches.piers(z0, zb, pitch);
			for (int k = 0; k + 1 < sp.length; k++) {
				int a = sp[k] + 1;
				int b = sp[k + 1] - 1;
				if (b - a < 1 || a <= z0) {
					continue;
				}
				Trade.counter(d, cxRow, base, a, Dirs.BACK, b - a + 1, toAisle, JobType.MERCHANT);
			}
		}
		// lanterns hung from the hall's floor beams
		for (int k = 1; k < midCols.length - 1; k++) {
			Halls.lanternUnder(d, cx - 2, base, midCols[k], 1);
			Halls.lanternUnder(d, cx + 2, base, midCols[k], 1);
		}
		int mz = (z0 + zb) / 2;
		d.poi(PoiType.MEETING_POINT, cx, base, mz, Dirs.FRONT);

		// --- the hall: a long table, the hearth, banners, chests
		int fy = body.floorY(1);
		int tz0 = up[1] + 3;
		int tz1 = up[3] - 3;
		if (tz1 - tz0 >= 2) {
			Furniture.longTable(d, cx, fy, tz0, Dirs.BACK, tz1 - tz0 + 1);
			for (int z = tz0 + 1; z <= tz1 - 1; z += 1) {
				for (int x : new int[]{cx - 1, cx + 1}) {
					if (d.isEmpty(x, fy, z) && (z - tz0) % 2 == 1) {
						Furniture.chair(d, x, fy, z, x < cx ? Dirs.LEFT : Dirs.RIGHT);
					}
				}
			}
			d.poi(PoiType.MEETING_POINT, cx, fy, tz0 - 1, Dirs.BACK);
		}
		// the hearth on the back wall of the hall, its chimney through the roof
		int hx = cx + (up[2] - up[0] >= 8 ? 2 : 1);
		int hz = up[3] - 1;
		if (d.isEmpty(hx, fy, hz) && d.isEmpty(hx, fy, hz - 1)) {
			Furniture.hearth(d, hx, fy, hz, Dirs.FRONT, false);
			if (roofTop.roof() != null) {
				Chimneys.stack(d, hx, hz, fy + 2, roofTop.roof(), roofTop.roof().ridgeY - 1);
			}
		}
		List<String> colours = Signs.clothColours(d);
		Signs.Arms arms = Signs.arms(d);
		for (int x : new int[]{up[0] + 1, up[2] - 1}) {
			for (int z = up[1] + 2; z <= up[3] - 2; z += 3) {
				if (d.isEmpty(x, fy + 2, z) && Openings.isWallBlock(d, x + (x == up[0] + 1 ? -1 : 1), fy + 2, z)) {
					Signs.wallBanner(d, x, fy + 2, z, x == up[0] + 1 ? Dirs.RIGHT : Dirs.LEFT, arms.field(), arms.layers());
				}
			}
		}
		Furniture.chest(d, up[2] - 1, fy, up[1] + 1, Dirs.LEFT, List.of(new Object[]{"minecraft:paper", 12}, new Object[]{"minecraft:book", 3}));
		Halls.lanternUnder(d, cx, fy, (tz0 + tz1) / 2, 2);

		// --- the front: a flight across the arcade, the way in, lamps, a balcony with the banner in towns
		if (base == 1) {
			Grounds.flight(d, x0, x1, z0 - 1, base, 1);
		}
		d.entrance(cx, base, z0 - 1);
		d.poi(PoiType.DOOR, cx, base, z0 - 1, Dirs.BACK);
		Grounds.approach(d, cx, base, z0 - 1);
		if (z0 >= 2) {
			Halls.lamp(d, 0, 0, 0, 2);
			Halls.lamp(d, w - 1, 0, 0, 2);
		}
		if (tier >= 3 && !jetty) {
			Side upper = body.side(1, Dirs.FRONT);
			Porches.balcony(d, upper, cx - 1, cx + 1, fy);
		}
		Side upFront = body.side(1, Dirs.FRONT);
		int bannerY = fy + 1;
		if (d.isEmpty(upFront.outX(upFront.index(cx), 1), bannerY, upFront.outZ(upFront.index(cx), 1))) {
			Signs.wallBanner(d, upFront.outX(upFront.index(cx), 1), bannerY + 1, upFront.outZ(upFront.index(cx), 1), Dirs.FRONT, arms.field(), arms.layers());
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, skyline + 2);
	}

	/** The roof a culture raises over its market. */
	static RoofForm roofForm(Draft d, Tradition t) {
		String p = d.param("roof", "");
		if (!p.isEmpty()) {
			try {
				return RoofForm.valueOf(p.toUpperCase(java.util.Locale.ROOT));
			} catch (IllegalArgumentException e) {
				// fall through to the culture's choice
			}
		}
		Habits h = d.habits();
		return switch (t) {
			case ADOBE -> RoofForm.FLAT;
			case KARST -> RoofForm.PAGODA;
			case MEDITERRANEAN, STEPPE, STILT -> RoofForm.HIP;
			case TIMBER -> h.roof().pick(d.random(), RoofForm.HIP, RoofForm.HALF_HIP, RoofForm.GABLE);
			default -> h.roof().pick(d.random(), RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.HIP);
		};
	}

	/**
	 * The cupola over the middle of the roof: a shaft of three from the plate up through the roof, a clock on each face
	 * in towns, an open belfry with the market bell, a cap in the tradition's manner. Returns its top.
	 */
	static int cupola(Draft d, Tradition t, int cx, int cz, int plate, Roofs.Roof roof, int maxH, int tier) {
		int x0 = cx - 1;
		int z0 = cz - 1;
		int ridge = roof.ridgeY;
		int bh = 3;
		Towers.Cap cap = Towers.cap(t, false);
		int capRoom = cap == Towers.Cap.ONION ? 6 : cap == Towers.Cap.SPIRE ? 6 : 4;
		int yb = ridge + 2;
		if (yb + bh + capRoom > maxH - 1) {
			yb = Math.max(ridge, maxH - 1 - bh - capRoom);
		}
		// tie beams across the hall carry it
		try (var s = d.stage(StageKind.FRAME)) {
			for (int z = z0 - 1; z <= z0 + 3; z++) {
				for (int x = x0; x <= x0 + 2; x++) {
					if (d.isEmpty(x, plate, z) && z >= z0 - 1 && z <= z0 + 3 && (z == z0 - 1 || z == z0 + 3 || z >= z0 && z <= z0 + 2)) {
						d.set(x, plate, z, d.mat(Role.BEAM).log(Direction.Axis.Z));
					}
				}
			}
		}
		Towers.shaft(d, x0, z0, 3, plate + 1, yb - 1, t, yb - 1);
		if (tier >= 3) {
			for (Side s : Side.of(x0, z0, x0 + 2, z0 + 2)) {
				Towers.clock(d, s, 1, yb - 2 >= roof.surfaceY(s.x(1) + Dirs.dx(s.out()), s.z(1) + Dirs.dz(s.out())) + 1 ? yb - 2 : yb - 1, true);
			}
		}
		Towers.belfry(d, x0, z0, 3, yb, bh, t, true);
		return Towers.cap(d, x0, z0, 3, yb + bh, cap, maxH);
	}

	/** A row of small domes on drums over a flat-roofed hall (the covered souk). Returns the top. */
	static int domes(Draft d, int[] r, int deck, int maxH) {
		int top = deck;
		Fam dome = Fam.of(d, Role.ROOF, Form.FULL, StageKind.ROOF);
		if (dome == null) {
			return top;
		}
		int cx = (r[0] + r[2]) / 2;
		// as many as stand clear of each other (a dome is five across)
		int n = Math.max(1, Math.min(3, (r[3] - r[1] - 1) / 6));
		for (int k = 0; k < n; k++) {
			int cz = r[1] + (int) Math.round((k + 0.5) * (r[3] - r[1]) / n);
			// a drum of three lifting it over the parapet, lights in it, then the dome
			top = Math.max(top, Adobe.dome(d, cx, cz, deck, 1, maxH));
		}
		return top;
	}
}
