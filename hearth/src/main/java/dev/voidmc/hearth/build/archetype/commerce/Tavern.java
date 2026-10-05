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
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Chimneys;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Porches;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.civic.Adobe;
import dev.voidmc.hearth.build.kit.civic.Grounds;
import dev.voidmc.hearth.build.kit.civic.Halls;
import dev.voidmc.hearth.build.kit.civic.Storeys;
import dev.voidmc.hearth.build.kit.civic.Trade;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.homestead.Bohemia;
import dev.voidmc.hearth.build.kit.homestead.Carving;
import dev.voidmc.hearth.build.kit.landscape.Trees;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.sociallife.Cellar;
import dev.voidmc.hearth.build.kit.sociallife.RoomFrame;
import dev.voidmc.hearth.build.kit.sociallife.Signboard;
import dev.voidmc.hearth.build.kit.sociallife.Venue;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
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
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:commerce/tavern}: the drinking house, not the inn. A low broad house of one lofty storey whose tap room
 * is open to the rafters under tie beams, lanterns hung from them on chains; along its back wall the bar with the
 * casks racked behind the keeper's lane and stools before it; beside the bar the stair down to the cellar of casks; at
 * the far end the great hearth with its stone breast and the stack through the gable; in a corner the musicians' stage
 * with the culture's cloth hung behind it and a drum; dice tables of green baize where folk play the social module's
 * dice of an evening; settles along the walls and a trestle by the windows. Outside, the ale-stake (a pole with a bush
 * tied to its end) over the door, the painted board on its wrought bracket, a bench by the door, a barrel stood on end
 * for a table, and behind, a little garden with a shade tree, tables, the woodpile and the empties.
 *
 * <p>Two plans: the long house (the ridge along the street, the door in the front) and, on broad deep plots in the
 * timber, log, alpine and stone cultures, the gable to the street with a yard beside it, a pentice along the yard side
 * over the door and the outdoor tables. By culture: the Bohemian tavern is rendered white under a steep shingle or
 * thatch roof with boarded gables over their little pent; the north's is of logs with horns crossing over its gables;
 * the alpine has a balcony; the Mediterranean taverna a vine pergola over its tables; the dry south a flat roof with a
 * shaded roof terrace and an awning; the karst wine house a curved roof; the bayou and the tropics raise it on posts
 * with a veranda.
 *
 * <p>POIs: COUNTER, MARKET_STALL and the INNKEEPER's JOB_SITE at the bar; STAGE and the ENTERTAINER's JOB_SITE on the
 * stage; GAMING_TABLEs; TABLEs, SEATs, the HEARTH, STORAGE in the cellar, the DOOR.
 *
 * <p>Params:
 * <ul>
 *   <li>{@code gable_front} (boolean): the gable to the street with a yard beside it (default by seed, where the plot is
 *   12+ wide and 12+ deep and the culture builds so)</li>
 *   <li>{@code yard_left} (boolean): with {@code gable_front}, the yard on the left seen from the street (default by seed)</li>
 *   <li>{@code name} (int): which of the tavern names the board shows (default by seed)</li>
 *   <li>{@code variant} (int): the style variant</li>
 * </ul>
 */
public final class Tavern implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("commerce/tavern"), ArchetypeCategory.COMMERCE,
		Set.of(JobType.INNKEEPER, JobType.ENTERTAINER), 0, 10, 13, 10, 14, 9, 16, SettlementTier.VILLAGE, Set.of(), Set.of(), List.of(), 3, 1.0);

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
		boolean raised = t.raised();
		boolean flat = t.flatRoofed();
		// low walls under a big roof (the Bohemian proportion), the tap room open to the rafters over its tie beams; a
		// lofty room under a flat roof or a raised one
		int h0 = flat || raised ? 5 : 4;

		// --- the plan
		boolean gableCulture = t == Tradition.TIMBER || t == Tradition.LOG || t == Tradition.ALPINE || t == Tradition.STONE;
		boolean gable = !raised && !flat && gableCulture && w >= 12 && dp >= 12
			&& d.param("gable_front", d.chance(t == Tradition.STONE ? 0.35 : 0.6));
		boolean yardLeft = gable && d.param("yard_left", d.chance(0.5));
		// the yard beside a gabled tavern is where folk come in: only where it lies within a step of the street
		if (gable && !d.hasParam("gable_front")) {
			int yx0 = yardLeft ? 0 : 9;
			int yx1 = yardLeft ? w - 10 : w - 1;
			if (d.site().maxGround(yx0, 0, yx1, dp - 1) - d.site().minGround(yx0, 0, yx1, dp - 1) > 2 || d.site().anyWater(yx0, 0, yx1, dp - 1)) {
				gable = false;
				yardLeft = false;
			}
		}
		int bx0;
		int bx1;
		int zF;
		int zM;
		int base;
		if (gable) {
			int bw = 9;
			bx0 = yardLeft ? w - bw : 0;
			bx1 = bx0 + bw - 1;
			zF = 1;
			zM = Math.min(dp - 1, zF + 11);
			if (dp - 1 - zM < 2 && dp >= 14) {
				zM = dp - 3;
			}
			base = 0;
		} else {
			bx0 = 0;
			bx1 = w - 1;
			int md = Math.min(9, dp - 2);
			zF = dp - md >= 4 ? 2 : 1;
			if (raised) {
				zF = Math.min(dp - md, 1 + Grounds.stiltBase(d, bx0, 1, bx1, md));
				zF = Math.max(zF, 1);
			}
			zM = zF + md - 1;
			base = raised ? Grounds.stiltBase(d, bx0, zF, bx1, zM) : 0;
		}
		Body body = new Body(bx0, zF, bx1, zM, base, new int[]{h0}, false, false);
		d.body(bx0, zF, bx1, zM);
		int plate = body.plateY();
		d.eave(plate);
		int ix0 = bx0 + 1;
		int ix1 = bx1 - 1;
		int iz0 = zF + 1;
		int iz1 = zM - 1;
		Side front = body.side(0, Dirs.FRONT);
		Direction yardOut = yardLeft ? Dirs.LEFT : Dirs.RIGHT;

		// --- grounding and the shell
		Storeys.ground(d, body, raised);
		if (!gable) {
			Grounds.forecourt(d, 0, w - 1, zF - 1, !raised);
		} else {
			int yx0 = yardLeft ? 0 : bx1 + 1;
			int yx1 = yardLeft ? bx0 - 1 : w - 1;
			// the front row and the yard levelled to the street (the plan only chose a yard within two blocks of level), so the
			// way in from the street runs along the front and up the yard to the door
			Groundwork.level(d, 0, 0, w - 1, zF - 1, base);
			Groundwork.level(d, yx0, zF, yx1, zM, base);
			Grounds.forecourt(d, 0, w - 1, zF - 1, true);
		}
		// the door: in the front of the long house, off the middle; in the yard side of the gabled one, near the front
		int doorCol;
		Facade fac;
		Side doorSide;
		if (gable) {
			fac = Facade.plan(d, body, -1, 1, 1, 0.7);
			doorSide = body.side(0, yardOut);
			int dz = zF + 2;
			if (fac.posts(yardOut).contains(dz)) {
				dz++;
			}
			doorCol = dz;
			int dc = dz;
			List<Facade.Opening> list = fac.openings(yardOut);
			if (!list.isEmpty()) {
				list.removeIf(o -> o.at() <= dc + 1 && o.at() + o.width() - 1 >= dc - 1);
			}
			fac.add(yardOut, new Facade.Opening(Facade.Kind.DOOR, dz, 1));
		} else {
			boolean doorLeft = d.chance(0.5);
			int want = doorLeft ? bx0 + (w - 1) / 3 : bx1 - (w - 1) / 3;
			fac = Facade.plan(d, body, want, 1, w >= 12 ? 2 : 1, 0.8);
			doorSide = front;
			Integer placed = doorOf(fac);
			doorCol = placed == null ? want : placed;
			if (placed == null) {
				fac.add(Dirs.FRONT, new Facade.Opening(Facade.Kind.DOOR, want, 1));
			}
		}
		boolean stoneFloor = t == Tradition.STONE || t == Tradition.MEDITERRANEAN || t == Tradition.ADOBE || t == Tradition.ALPINE;
		Storeys.walls(d, body, fac, 0, 0, null, stoneFloor);
		Storeys.windows(d, body, fac, 0, 0, 2);
		if (Adobe.is(d)) {
			Adobe.screens(d, body, 0);
		}
		Openings.door(d, doorSide, doorCol, base, 1, new Openings.DoorOpts(false, true, true));
		int doorInX = doorSide.x(doorSide.index(doorCol)) - Dirs.dx(doorSide.out());
		int doorInZ = doorSide.z(doorSide.index(doorCol)) - Dirs.dz(doorSide.out());
		int doorOutX = doorSide.outX(doorSide.index(doorCol), 1);
		int doorOutZ = doorSide.outZ(doorSide.index(doorCol), 1);

		// --- the roof
		RoofForm form = flat ? RoofForm.FLAT : t == Tradition.KARST ? RoofForm.PAGODA
			: Bohemia.is(d) ? Bohemia.form(d) : h.roof().pick(d.random(), RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.HIP);
		Direction.Axis axis = gable ? Direction.Axis.Z : Direction.Axis.X;
		Roofs.Roof roof = null;
		int skyline;
		int deck = Integer.MIN_VALUE;
		if (form == RoofForm.FLAT || form == RoofForm.PAGODA) {
			Storeys.Top tp = Storeys.roof(d, body, form, axis, Math.max(1.0, h.roof().pitch()), 1, false);
			roof = tp.roof();
			deck = tp.deck();
			skyline = roof == null ? tp.deck() + 3 : tp.ridge();
		} else {
			roof = pitched(d, body, form, axis);
			skyline = roof == null ? plate + 2 : roof.ridgeY;
		}

		// --- tie beams across the tap room, open to the rafters over them
		boolean beams = roof != null;
		if (beams) {
			Mat beam = d.mat(Role.BEAM);
			try (var s = d.stage(StageKind.FRAME)) {
				if (axis == Direction.Axis.X) {
					for (int x = ix0 + 1; x <= ix1 - 1; x += 3) {
						for (int z = iz0; z <= iz1; z++) {
							d.set(x, plate, z, beam.log(Direction.Axis.Z));
						}
					}
				} else {
					for (int z = iz0 + 1; z <= iz1 - 1; z += 3) {
						for (int x = ix0; x <= ix1; x++) {
							d.set(x, plate, z, beam.log(Direction.Axis.X));
						}
					}
				}
			}
		} else {
			// under a flat roof, joists overhead
			dev.voidmc.hearth.build.kit.Floors.joists(d, ix0, iz0, ix1, iz1, plate, gable ? Direction.Axis.X : Direction.Axis.Z, 2, base);
		}

		// --- the tap room, laid out from its bar wall: the back wall of the long house, the far long wall of the gabled one
		RoomFrame fr;
		if (gable) {
			Direction barWall = yardOut.getOpposite();
			fr = RoomFrame.of(ix0, iz0, ix1, iz1, barWall, Dirs.FRONT);
		} else {
			// the stair's corner on the side away from the door
			boolean doorLeftHalf = doorInX <= (ix0 + ix1) / 2;
			fr = RoomFrame.of(ix0, iz0, ix1, iz1, Dirs.BACK, doorLeftHalf ? Dirs.RIGHT : Dirs.LEFT);
		}
		Floorplan plan = new Floorplan(ix0, iz0, ix1, iz1, base, h0 - 1);
		plan.keep(doorInX, doorInZ);
		// the cellar stair along the bar wall from its corner, the bar beyond it
		int stairLen = 4;
		boolean cellar = fr.uLen() >= stairLen + 3 && fr.vLen() >= 5 && !raised;
		int barU0 = cellar ? stairLen : 1;
		int barN = Math.min(5, fr.uLen() - barU0 - 1);
		if (cellar) {
			int[] s0 = fr.at(0, 0);
			int cu1 = Math.min(fr.uLen() - 1, barU0 + barN);
			int[] cr = fr.rect(0, 0, cu1, 2);
			Cellar.dig(d, cr[0], cr[1], cr[2], cr[3], base - 1);
			dev.voidmc.hearth.build.kit.Floors.boards(d, ix0, iz0, ix1, iz1, base - 1);
			Stairs.straight(d, s0[0], base - 1 - Cellar.HEADROOM, s0[1], fr.uDir(), Cellar.HEADROOM + 1, 1);
			// the rail along the stairwell's open side
			try (var s = d.stage(StageKind.INTERIOR)) {
				for (int u = 0; u < stairLen - 1; u++) {
					d.set(fr.x(u, 1), base, fr.z(u, 1), d.mat(Role.RAILING).fence());
				}
			}
			for (int u = 0; u < stairLen; u++) {
				plan.block(fr.x(u, 0), fr.z(u, 0));
				if (u < stairLen - 1) {
					plan.block(fr.x(u, 1), fr.z(u, 1));
				}
			}
			plan.keep(fr.x(stairLen - 1, 1), fr.z(stairLen - 1, 1));
			// the cellar's floor and stores
			int cy = base - 1 - Cellar.HEADROOM;
			Floorplan cp = new Floorplan(cr[0], cr[1], cr[2], cr[3], cy, Cellar.HEADROOM);
			int[] foot = fr.at(0, 1);
			cp.keep(foot[0], foot[1]);
			for (int u = 0; u < stairLen - 1; u++) {
				cp.block(fr.x(u, 0), fr.z(u, 0));
			}
			cp.keep(fr.x(0, 1), fr.z(0, 1));
			cp.keepArea(fr.x(stairLen - 1, 0), fr.z(stairLen - 1, 0), fr.x(stairLen - 1, 0), fr.z(stairLen - 1, 0));
			Cellar.stock(d, cp, base - 1, List.of(new Object[]{"minecraft:wheat", 12}, new Object[]{"minecraft:sugar", 6},
				new Object[]{"minecraft:glass_bottle", 8}, new Object[]{"minecraft:honey_bottle", 2}));
		}
		// the bar: casks against the wall, the keeper's lane, the counter facing the room
		int[] k0 = fr.at(barU0, 2);
		boolean bar = barN >= 3 && fr.vLen() >= 5;
		if (bar) {
			Trade.bar(d, k0[0], base, k0[1], fr.uDir(), barN, fr.vDir(), 2);
			for (int u = barU0; u < barU0 + barN; u++) {
				for (int v = 0; v <= 2; v++) {
					if (v == 1) {
						continue;
					}
					plan.block(fr.x(u, v), fr.z(u, v));
				}
			}
			// the lane stays clear; its open end is the way in behind the bar
			plan.keepArea(fr.x(barU0, 1), fr.z(barU0, 1), fr.x(barU0 + barN - 1, 1), fr.z(barU0 + barN - 1, 1));
			plan.keep(fr.x(barU0, 1), fr.z(barU0, 1));
			if (!cellar) {
				plan.keep(fr.x(barU0 - 1, 1), fr.z(barU0 - 1, 1));
			}
			// the drinkers' side before the counter
			for (int u = barU0; u < barU0 + barN; u++) {
				int[] c = fr.at(u, 3);
				if (d.isEmpty(c[0], base, c[1])) {
					plan.keepArea(c[0], c[1], c[0], c[1]);
				} else {
					plan.block(c[0], c[1]);
				}
			}
			plan.keep(fr.x(barU0 + barN / 2, 3), fr.z(barU0 + barN / 2, 3));
		} else {
			// a small room: a counter of casks along the wall
			Trade.counter(d, k0[0], base, fr.z(barU0, 0), fr.uDir(), Math.max(2, Math.min(3, fr.uLen() - barU0 - 1)), fr.vDir(), JobType.INNKEEPER);
			plan.blockDrawn(d);
		}

		if (System.getenv("HEARTH_DEBUG_TAVERN") != null) {
			System.err.println("TAVERN after bar connected=" + plan.connected() + " reach=" + plan.reachCells().stream().map(java.util.Arrays::toString).toList());
		}
		// --- the hearth: a breast of stone on an end wall, its stack through the roof
		int[] hearth = hearth(d, fr, plan, base, roof, body);
		if (hearth == null) {
			stove(d, plan, base);
		}
		// --- the stage in a corner, away from the door and the bar
		stage(d, fr, plan, base, doorInX, doorInZ);
		// --- dice tables in the open floor, then a trestle, then settles along the walls
		int area = (ix1 - ix0 + 1) * (iz1 - iz0 + 1);
		if (System.getenv("HEARTH_DEBUG_TAVERN") != null) {
			StringBuilder sb = new StringBuilder();
			for (int z = iz1; z >= iz0; z--) {
				for (int x = ix0; x <= ix1; x++) {
					sb.append(plan.free(x, z) ? '.' : plan.open(x, z) ? 'k' : '#');
				}
				sb.append('/');
			}
			System.err.println("TAVERN before games connected=" + plan.connected() + " " + sb);
		}
		int games = Venue.placeGaming(d, plan, base, area >= 70 ? 3 : area >= 42 ? 2 : 1);
		if (games == 0) {
			gamingAnywhere(d, plan, base);
		}
		trestles(d, plan, base, fr);
		settles(d, plan, base);
		plan.blockDrawn(d);
		dev.voidmc.hearth.build.kit.Rooms.furnish(d, plan.room(), dev.voidmc.hearth.build.kit.Rooms.Type.HALL, 0, JobType.NONE);

		// --- light by design: lanterns from the tie beams over the floor, sconces at the hearth, then the rest
		if (beams) {
			hangFromBeams(d, ix0, iz0, ix1, iz1, base, plate, axis);
		}
		if (hearth != null) {
			Direction toWall = Dirs.of(hearth[2], hearth[3]);
			Direction along = Dirs.right(toWall);
			for (int s = -2; s <= 2; s += 4) {
				int sx = hearth[0] + Dirs.dx(along) * s - Dirs.dx(toWall);
				int sz = hearth[1] + Dirs.dz(along) * s - Dirs.dz(toWall);
				int wx = sx + Dirs.dx(toWall);
				int wz = sz + Dirs.dz(toWall);
				if (plan.inside(sx, sz) && d.isEmpty(sx, base + 2, sz) && d.isEmpty(sx, base + 3, sz) && Openings.isWallBlock(d, wx, base + 3, wz)
					&& Openings.isWallBlock(d, wx, base + 2, wz)) {
					Lighting.sconce(d, sx, base + 2, sz, toWall);
				}
			}
		}
		Lighting.room(d, ix0, iz0, ix1, iz1, base);

		// --- outside
		exterior(d, t, body, roof, front, doorSide, doorCol, base, zF, zM, gable, yardLeft, deck, skyline);
		int outZ = doorOutZ;
		if (doorSide == front) {
			Grounds.approach(d, doorOutX, base, outZ);
		} else {
			Groundwork.approach(d, doorOutX, base, outZ);
		}
		Lighting.doorLamp(d, doorOutX + Dirs.dx(doorSide.along()), base + 2, doorOutZ + Dirs.dz(doorSide.along()), doorSide.out().getOpposite());
		garden(d, t, bx0, bx1, zM, base, gable, yardLeft);
		if (h.snow() && !gable) {
			Garden.snowBanks(d, bx0, zF, bx1, zM, base, doorCol);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(maxH - 1, skyline + 3));
	}

	// ------------------------------------------------------------------------------------------- roof

	/**
	 * The pitched roof: the Bohemian way (steep, two to one where the height allows, the gables boarded over their little
	 * pent) in the temperate culture, horns over the gables in the north, the culture's own pitch elsewhere, always under
	 * the plot's height. Null when nothing pitched fits (a flat roof is drawn instead).
	 */
	private static Roofs.Roof pitched(Draft d, Body body, RoofForm form, Direction.Axis axis) {
		int[] tr = body.topRect();
		int plate = body.plateY();
		Roofs.Opts o = Roofs.Opts.of(d);
		int span = axis == Direction.Axis.X ? tr[3] - tr[1] + 1 : tr[2] - tr[0] + 1;
		if (form == RoofForm.HIP) {
			span = Math.min(tr[3] - tr[1] + 1, tr[2] - tr[0] + 1);
		}
		double half = span / 2.0 + o.eaves();
		if (plate + 1 + 0.5 * half + 1 > d.maxHeight() - 1) {
			Roofs.flat(d, tr[0], tr[1], tr[2], tr[3], plate, plate + 2 <= d.maxHeight() - 1);
			return null;
		}
		double base = d.habits().roof().pitch();
		boolean hipped = form == RoofForm.HIP || form == RoofForm.HALF_HIP;
		double want = Bohemia.is(d) ? (hipped ? Math.max(1.0, base) : 2.0) : base < 0.75 ? base : Math.max(1.0, base);
		// the ridge three under the plot's height: a chimney at the ridge stands two over it
		double fits = (d.maxHeight() - 3 - (plate + 1)) / Math.max(1.0, half);
		double p = Math.min(want, Math.max(0.5, fits));
		p = p >= 2.0 ? 2.0 : p >= 1.5 ? 1.5 : p >= 1.0 ? 1.0 : 0.5;
		Roofs.Plan plan = Roofs.plan(d, tr[0], tr[1], tr[2], tr[3], plate, form, axis, o.withPitch(p), false, false);
		Roofs.Roof r = plan.draw(d).roof();
		if (Bohemia.is(d)) {
			Bohemia.carryRidge(d, r);
			if (form == RoofForm.GABLE) {
				Bohemia.gables(d, r, axis, tr, plate);
			}
		} else if (Tradition.of(d) == Tradition.LOG && form == RoofForm.GABLE) {
			Carving.hornsAtGables(d, r, axis);
		}
		return r;
	}

	// ------------------------------------------------------------------------------------------- the tap room

	/**
	 * The hearth on a wall of the room: a fire (an open fire in a stone surround) with stone cheeks either side and a stone
	 * breast over them to the tie beams, its flue running up in the wall to a stack through the roof. Tried on the end
	 * wall away from the stair first, then the other walls, wherever three cells of solid wall stand behind it. Answers
	 * {x, z, toWall dx, toWall dz} of the fire, or null.
	 */
	private static int[] hearth(Draft d, RoomFrame fr, Floorplan plan, int y, Roofs.Roof roof, Body body) {
		List<int[]> tries = new ArrayList<>();
		// the end wall at u = uLen - 1 (the far end from the stair), across its middle; then the wall opposite the bar wall
		for (int v : outward(2, fr.vLen() - 2, fr.vLen() / 2)) {
			tries.add(new int[]{fr.uLen() - 1, v, 0});
		}
		for (int u : outward(1, fr.uLen() - 2, fr.uLen() / 2)) {
			tries.add(new int[]{u, fr.vLen() - 1, 1});
		}
		Mat stone = d.mat(Role.CHIMNEY);
		for (int[] tc : tries) {
			int u = tc[0];
			int v = tc[1];
			Direction toWall = tc[2] == 0 ? fr.uDir() : fr.vDir();
			Direction along = Dirs.right(toWall);
			int fx = fr.x(u, v);
			int fz = fr.z(u, v);
			int[][] cells = {{fx - Dirs.dx(along), fz - Dirs.dz(along)}, {fx, fz}, {fx + Dirs.dx(along), fz + Dirs.dz(along)}};
			boolean ok = true;
			for (int[] c : cells) {
				if (!plan.free(c[0], c[1])) {
					ok = false;
					break;
				}
				for (int yy = y; yy <= y + 2; yy++) {
					if (!Openings.isWallBlock(d, c[0] + Dirs.dx(toWall), yy, c[1] + Dirs.dz(toWall))) {
						ok = false;
					}
				}
			}
			int[] frontCell = {fx - Dirs.dx(toWall), fz - Dirs.dz(toWall)};
			if (!ok || !plan.inside(frontCell[0], frontCell[1]) || !plan.take(cells, frontCell)) {
				continue;
			}
			int plate = body.plateY();
			try (var s = d.stage(StageKind.INTERIOR)) {
				d.set(fx, y, fz, States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.HORIZONTAL_FACING, toWall.getOpposite())
					.trySetValue(BlockStateProperties.SIGNAL_FIRE, false));
				for (int k : new int[]{0, 2}) {
					d.set(cells[k][0], y, cells[k][1], stone.full());
					d.set(cells[k][0], y + 1, cells[k][1], stone.stairsOrSlab(toWall.getOpposite(), Half.BOTTOM));
				}
				// the breast: a lintel of upside-down stairs over the fire, stone up to the beams
				d.set(fx, y + 2, fz, stone.stairsOrSlab(toWall.getOpposite(), Half.TOP));
				for (int k = 0; k < 3; k++) {
					for (int yy = y + 2 + (k == 1 ? 1 : 0); yy < plate; yy++) {
						if (k != 1 && yy == y + 2) {
							d.set(cells[k][0], yy, cells[k][1], stone.stairsOrSlab(toWall.getOpposite(), Half.TOP));
						} else {
							d.set(cells[k][0], yy, cells[k][1], stone.full());
						}
					}
				}
			}
			d.poi(PoiType.HEARTH, frontCell[0], y, frontCell[1], toWall);
			// the flue in the wall behind and the stack through the roof
			int wx = fx + Dirs.dx(toWall);
			int wz = fz + Dirs.dz(toWall);
			if (roof != null) {
				Chimneys.stack(d, wx, wz, y + 1, roof, roof.ridgeY + 1);
			} else {
				try (var s = d.stage(StageKind.ROOF)) {
					for (int yy = y + 1; yy <= plate + 2 && yy < d.maxHeight() - 1; yy++) {
						d.set(wx, yy, wz, stone.full());
					}
				}
			}
			return new int[]{fx, fz, Dirs.dx(toWall), Dirs.dz(toWall)};
		}
		return null;
	}

	/**
	 * Where no wall will take a chimney breast (a lattice or bamboo wall, a raised house of boards): a stove against a wall
	 * of the room, the HEARTH before it.
	 */
	private static void stove(Draft d, Floorplan plan, int y) {
		for (Direction wall : Dirs.HORIZONTAL) {
			for (int[] c : plan.along(wall)) {
				int fx = c[0] - Dirs.dx(wall);
				int fz = c[1] - Dirs.dz(wall);
				if (plan.free(c[0], c[1]) && plan.inside(fx, fz) && plan.take(c[0], c[1], wall.getOpposite())) {
					Furniture.hearth(d, c[0], y, c[1], wall.getOpposite(), false);
					return;
				}
			}
		}
	}

	/** The values lo..hi ordered by distance from {@code mid} (the middle first, then either side in turn). */
	private static List<Integer> outward(int lo, int hi, int mid) {
		List<Integer> out = new ArrayList<>();
		mid = Math.clamp(mid, lo, Math.max(lo, hi));
		for (int k = 0; k <= hi - lo + 1; k++) {
			for (int c : new int[]{mid - k, mid + k}) {
				if (c >= lo && c <= hi && !out.contains(c)) {
					out.add(c);
				}
			}
		}
		return out;
	}

	/** The musicians' stage in a corner of the room: three wide where the room allows, two deep, facing the floor. */
	private static void stage(Draft d, RoomFrame fr, Floorplan plan, int y, int doorX, int doorZ) {
		int U = fr.uLen();
		int V = fr.vLen();
		// corners away from the bar wall first (the far end, then the near end), facing into the room
		List<int[]> tries = new ArrayList<>();
		for (int wide : new int[]{3, 2}) {
			tries.add(new int[]{U - 2, V - wide, U - 1, V - 1, wide});
			tries.add(new int[]{U - wide, V - 2, U - 1, V - 1, wide});
			tries.add(new int[]{0, V - wide, 1, V - 1, wide});
			tries.add(new int[]{0, V - 2, wide - 1, V - 1, wide});
		}
		for (int[] c : tries) {
			if (c[0] < 0 || c[1] < 0) {
				continue;
			}
			int[] r = fr.rect(c[0], c[1], c[2], c[3]);
			// facing: away from the walls it stands against, along its short side
			boolean deepU = c[2] - c[0] + 1 == 2;
			Direction face;
			if (deepU) {
				face = c[0] == 0 ? fr.uDir() : fr.uDir().getOpposite();
			} else {
				face = fr.vDir().getOpposite();
			}
			List<int[]> cells = new ArrayList<>();
			boolean nearDoor = false;
			for (int z = r[1]; z <= r[3]; z++) {
				for (int x = r[0]; x <= r[2]; x++) {
					cells.add(new int[]{x, z});
					if (Math.abs(x - doorX) + Math.abs(z - doorZ) <= 1) {
						nearDoor = true;
					}
				}
			}
			if (nearDoor) {
				continue;
			}
			// the audience's floor before it: the row in front, kept open
			int[] mid = {(r[0] + r[2]) / 2 + Dirs.dx(face) * ((r[2] - r[0]) / 2 + 1), (r[1] + r[3]) / 2 + Dirs.dz(face) * ((r[3] - r[1]) / 2 + 1)};
			int fx = face.getAxis() == Direction.Axis.X ? (face == Dirs.RIGHT ? r[2] + 1 : r[0] - 1) : (r[0] + r[2]) / 2;
			int fz = face.getAxis() == Direction.Axis.Z ? (face == Dirs.FRONT ? r[1] - 1 : r[3] + 1) : (r[1] + r[3]) / 2;
			int[] step = Venue.stepCell(r[0], r[1], r[2], r[3], face);
			if (plan.free(step[0], step[1])) {
				cells.add(step);
			}
			if (!plan.inside(fx, fz) || !plan.take(cells.toArray(int[][]::new), new int[]{fx, fz})) {
				continue;
			}
			// the step at the front's end is on the floor in front: keep it free of furniture
			Venue.stage(d, r[0], r[1], r[2], r[3], y, face, true);
			for (int z = r[1] - 1; z <= r[3] + 1; z++) {
				for (int x = r[0] - 1; x <= r[2] + 1; x++) {
					if (plan.inside(x, z) && !d.isEmpty(x, y, z)) {
						plan.block(x, z);
					}
				}
			}
			return;
		}
	}

	/** A last try for a dice table anywhere it fits (along either axis), so every tavern has one. */
	private static void gamingAnywhere(Draft d, Floorplan plan, int y) {
		for (Direction run : new Direction[]{Dirs.RIGHT, Dirs.BACK}) {
			for (int z = plan.z0; z <= plan.z1; z++) {
				for (int x = plan.x0; x <= plan.x1; x++) {
					int[][] cells = Venue.gamingCells(x, z, run);
					if (!fits(plan, cells)) {
						continue;
					}
					Direction side = Dirs.right(run);
					int[] spot = {x + Dirs.dx(side), z + Dirs.dz(side)};
					if (!plan.inside(spot[0], spot[1])) {
						spot = new int[]{x - Dirs.dx(side), z - Dirs.dz(side)};
					}
					if (plan.inside(spot[0], spot[1]) && plan.take(cells, spot)) {
						Venue.gamingTable(d, x, y, z, run);
						return;
					}
				}
			}
		}
		// the smallest room: a single baize table without its stools
		for (int z = plan.z0; z <= plan.z1; z++) {
			for (int x = plan.x0; x <= plan.x1; x++) {
				if (plan.free(x, z) && plan.take(x, z, null)) {
					try (var s = d.stage(StageKind.INTERIOR)) {
						d.set(x, y, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
						d.set(x, y + 1, z, Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.GREEN).defaultBlockState());
					}
					d.poi(PoiType.GAMING_TABLE, x, y, z, Dirs.FRONT);
					return;
				}
			}
		}
	}

	private static boolean fits(Floorplan plan, int[][] cells) {
		for (int[] c : cells) {
			if (!plan.free(c[0], c[1])) {
				return false;
			}
		}
		return true;
	}

	/** A trestle table with its benches where a run of five by three is free (the long table of a drinking house). */
	private static void trestles(Draft d, Floorplan plan, int y, RoomFrame fr) {
		for (int v = fr.vLen() - 2; v >= 3; v--) {
			for (int u = 1; u + 2 <= fr.uLen() - 2; u++) {
				int[][] table = {fr.at(u, v), fr.at(u + 1, v), fr.at(u + 2, v)};
				List<int[]> all = new ArrayList<>(List.of(table));
				boolean ok = fits(plan, table);
				for (int k = 0; k < 3 && ok; k++) {
					for (int dv : new int[]{-1, 1}) {
						int[] c = fr.at(u + k, v + dv);
						if (!plan.free(c[0], c[1])) {
							ok = false;
						}
						all.add(c);
					}
				}
				if (!ok) {
					continue;
				}
				int[] end = fr.at(u + 3, v);
				int[] end2 = fr.at(u - 1, v);
				int[] spot = plan.free(end[0], end[1]) ? end : end2;
				if (!plan.inside(spot[0], spot[1]) || !plan.take(all.toArray(int[][]::new), spot)) {
					continue;
				}
				Venue.trestle(d, table[0][0], y, table[0][1], fr.uDir(), 3);
				Venue.candles(d, table[1][0], y + 1, table[1][1], 3);
				return;
			}
		}
	}

	/** Settles (benches against the walls) in runs of two where the wall is plain and the floor free. */
	private static void settles(Draft d, Floorplan plan, int y) {
		for (Direction wall : Dirs.HORIZONTAL) {
			List<int[]> run = plan.along(wall);
			for (int i = 0; i + 1 < run.size(); i++) {
				int[] a = run.get(i);
				int[] b = run.get(i + 1);
				int wx = a[0] + Dirs.dx(wall);
				int wz = a[1] + Dirs.dz(wall);
				int wx2 = b[0] + Dirs.dx(wall);
				int wz2 = b[1] + Dirs.dz(wall);
				if (!plan.free(a[0], a[1]) || !plan.free(b[0], b[1]) || !Openings.isWallBlock(d, wx, y, wz) || !Openings.isWallBlock(d, wx2, y, wz2)) {
					continue;
				}
				int[] fa = {a[0] - Dirs.dx(wall), a[1] - Dirs.dz(wall)};
				if (!plan.inside(fa[0], fa[1]) || !plan.take(new int[][]{a, b}, fa)) {
					continue;
				}
				Venue.bench(d, a[0], y, a[1], Dirs.of(b[0] - a[0], b[1] - a[1]), 2, wall);
				i += 2;
			}
		}
	}

	/** Lanterns hung on chains from the tie beams, over the floor (never over a stair or a stage). */
	private static void hangFromBeams(Draft d, int ix0, int iz0, int ix1, int iz1, int y, int plate, Direction.Axis ridge) {
		int lanternY = y + Math.max(2, plate - y - 2);
		for (int z = iz0; z <= iz1; z++) {
			for (int x = ix0; x <= ix1; x++) {
				boolean onBeam = ridge == Direction.Axis.X ? (x - ix0 - 1) % 3 == 0 && x >= ix0 + 1 : (z - iz0 - 1) % 3 == 0 && z >= iz0 + 1;
				if (!onBeam) {
					continue;
				}
				boolean mid = ridge == Direction.Axis.X ? (z - iz0) % 3 == 1 : (x - ix0) % 3 == 1;
				if (!mid || !d.isSolid(x, plate, z)) {
					continue;
				}
				boolean clear = true;
				for (int yy = y; yy < plate; yy++) {
					if (yy >= lanternY && !d.isEmpty(x, yy, z) || yy < y + 2 && !d.isEmpty(x, yy, z) && d.isSolid(x, yy, z)) {
						clear = false;
					}
				}
				if (!clear || !d.isSolid(x, y - 1, z) && !d.isEmpty(x, y, z)) {
					continue;
				}
				Lighting.chandelier(d, x, z, plate, lanternY);
			}
		}
	}

	// ------------------------------------------------------------------------------------------- outside

	private static void exterior(Draft d, Tradition t, Body body, Roofs.Roof roof, Side front, Side doorSide, int doorCol, int base, int zF, int zM, boolean gable,
		boolean yardLeft, int deck, int skyline) {
		List<String[]> name = Signboard.name(d, "tavern", Signboard.TAVERNS);
		int di = doorSide.index(doorCol);
		int dx = doorSide.x(di);
		int dz = doorSide.z(di);
		Direction out = doorSide.out();
		Direction along = doorSide.along();
		// the ale-stake over the door: a pole out from the wall above it, a bush at its end
		int stakeY = base + 3;
		if (!Signboard.aleStake(d, dx, stakeY, dz, out, 2) && Openings.isWallBlock(d, dx, stakeY - 1, dz)) {
			Signboard.aleStake(d, dx, stakeY - 1 + 1, dz, out, 1);
		}
		// the board on its bracket, two along from the door (on the side with more wall)
		boolean hung = false;
		for (int k : new int[]{-2, 2, -3, 3}) {
			int i = di + k;
			if (i < 1 || i > doorSide.last() - 1) {
				continue;
			}
			if (Signboard.bracket(d, doorSide.x(i), base + 3, doorSide.z(i), out, name)) {
				hung = true;
				break;
			}
		}
		if (!hung && gable) {
			// on the street gable, over the middle
			Side f = front;
			Signboard.bracket(d, f.x(f.middle()), base + 3, f.z(f.middle()), Dirs.FRONT, name);
		}
		// the bench by the door and a barrel stood on end for a table
		if (doorSide == front) {
			Bohemia.bench(d, doorCol, zF, base);
			barrelTable(d, front, doorCol, base, zF - 1);
		}
		if (gable) {
			// the pentice along the yard side over the door, carrying the eave out on posts, a table under it
			int yard = yardLeft ? body.x0 : d.frontage() - 1 - body.x1;
			if (yard >= 2) {
				int c0 = zF;
				int c1 = Math.min(zM, zF + 7);
				Roofs.Roof pent = dev.voidmc.hearth.build.kit.trade.Pentice.over(d, roof, doorSide, c0, c1, Math.min(3, yard), base, doorCol);
				if (pent != null) {
					int tz = doorCol + 3;
					int tx = doorSide.outX(doorSide.index(tz), 1);
					if (tz <= c1 - 1 && free(d, tx, base, tz)) {
						try (var s = d.stage(StageKind.EXTERIOR)) {
							d.set(tx, base, tz, States.facing(Blocks.BARREL, Direction.UP));
						}
						Venue.candles(d, tx, base + 1, tz, 2);
						d.poi(PoiType.TABLE, tx, base, tz, Dirs.FRONT);
						for (int sz : new int[]{tz - 1, tz + 1}) {
							if (free(d, tx, base, sz)) {
								Furniture.chair(d, tx, base, sz, sz < tz ? Dirs.FRONT : Dirs.BACK);
							}
						}
					}
				}
			}
		}
		switch (t) {
			case MEDITERRANEAN -> {
				if (zF >= 2) {
					Inn.pergola(d, front.x(1), front.x(front.last() - 1), zF - 1, base);
				}
			}
			case ADOBE -> {
				if (zF >= 1 && body.height(0) >= 4) {
					Adobe.awning(d, front, 1, front.last() - 1, base + 3);
				}
				if (deck != Integer.MIN_VALUE) {
					Halls.roofTerrace(d, body.topRect(), deck, yardLeft);
				}
			}
			case ALPINE, KARST -> {
				// a little balcony under the eaves of the long side, flower boxes along it
				if (!gable && body.height(0) >= 5) {
					Side b = body.side(0, Dirs.FRONT);
					int y = body.plateY();
					if (d.maxHeight() > y + 3) {
						Porches.balcony(d, b, b.column(1), b.column(Math.min(b.last() - 1, 3)), y);
					}
				}
			}
			case STILT -> {
				// the veranda along the front at the deck, steps down to the street
				if (zF >= 3) {
					Porches.porch(d, front, front.column(0), front.column(front.last()), 2, base, 3, doorCol);
				}
			}
			default -> {
			}
		}
		// a lamp at the street's edge in front of the tavern
		if (doorSide == front && zF >= 1) {
			int lx = doorCol + (doorCol > (front.column(0) + front.column(front.last())) / 2 ? -3 : 3);
			if (lx >= 0 && lx < d.frontage() && Groundwork.surface(d, lx, 0) == base && d.isEmpty(lx, base, 0) && d.isEmpty(lx, base + 1, 0)) {
				Lighting.post(d, lx, base, 0);
			}
		}
	}

	/** A barrel stood on end for a table in the forecourt, a stool either side, away from the door's path. */
	private static void barrelTable(Draft d, Side front, int doorCol, int y, int z) {
		if (z < 0) {
			return;
		}
		for (int k : new int[]{3, -3, 4, -4}) {
			int x = doorCol + k;
			if (x - 1 < 0 || x + 1 >= d.frontage()) {
				continue;
			}
			if (free(d, x, y, z) && free(d, x - 1, y, z) && free(d, x + 1, y, z)) {
				try (var s = d.stage(StageKind.EXTERIOR)) {
					d.set(x, y, z, States.facing(Blocks.BARREL, Direction.UP));
				}
				Venue.candles(d, x, y + 1, z, 1);
				Furniture.chair(d, x - 1, y, z, Dirs.LEFT);
				Furniture.chair(d, x + 1, y, z, Dirs.RIGHT);
				d.poi(PoiType.TABLE, x, y, z, Dirs.BACK);
				return;
			}
		}
	}

	private static boolean free(Draft d, int x, int y, int z) {
		return Venue.standable(d, x, y, z) && d.allowed(x, y + 1, z);
	}

	/**
	 * The garden behind (or the yard beside the gabled house): a shade tree with a table under it, the woodpile against
	 * the back wall, empty casks stacked, a wattle fence along the back where the culture weaves one.
	 */
	private static void garden(Draft d, Tradition t, int bx0, int bx1, int zM, int base, boolean gable, boolean yardLeft) {
		int w = d.frontage();
		int dp = d.depth();
		int gz0 = zM + 1;
		int gx0;
		int gx1;
		int gz1 = dp - 1;
		if (gable) {
			gx0 = yardLeft ? 0 : bx1 + 1;
			gx1 = yardLeft ? bx0 - 1 : w - 1;
			gz0 = Math.min(dp - 1, zM - 4);
		} else {
			gx0 = 0;
			gx1 = w - 1;
		}
		if (gz1 < gz0 || gx1 < gx0) {
			return;
		}
		boolean level = Grounds.gentle(d, gx0, Math.max(gz0, 0), gx1, gz1, base);
		if (!level) {
			return;
		}
		Groundwork.level(d, gx0, gz0, gx1, gz1, base);
		int rows = gz1 - gz0 + 1;
		int cols = gx1 - gx0 + 1;
		// the woodpile against the back wall, the empties beside it
		if (!gable && rows >= 1) {
			Garden.woodpile(d, bx0 + 1, base, gz0, Dirs.RIGHT, Math.min(3, Math.max(1, (bx1 - bx0) / 4)));
			Garden.barrels(d, bx1 - 2, base, gz0, Dirs.RIGHT, 2);
		}
		// a shade tree with a barrel table under it, on a deep enough garden
		if (rows >= 3 && cols >= 5) {
			int tx = gable ? (gx0 + gx1) / 2 : (bx0 + bx1) / 2 + (Noise.unit(d.seed(), 3, 0, 9) < 0.5 ? -2 : 2);
			int tz = gz1 - 1;
			if (tz > gz0 && d.isEmpty(tx, base, tz)) {
				Trees.tree(d, tx, base, tz, Math.min(6, d.maxHeight() - base - 1), Trees.kind(d), gx0, gz0, gx1, gz1);
				int bx = tx + 2 <= gx1 ? tx + 2 : tx - 2;
				if (free(d, bx, base, tz) && bx >= gx0 && bx <= gx1) {
					try (var s = d.stage(StageKind.EXTERIOR)) {
						d.set(bx, base, tz, States.facing(Blocks.BARREL, Direction.UP));
					}
					Venue.candles(d, bx, base + 1, tz, 2);
					d.poi(PoiType.TABLE, bx, base, tz, Dirs.FRONT);
					for (int sx : new int[]{bx - 1, bx + 1}) {
						if (sx != tx && free(d, sx, base, tz) && sx >= gx0 && sx <= gx1) {
							Furniture.chair(d, sx, base, tz, sx < bx ? Dirs.LEFT : Dirs.RIGHT);
						}
					}
				}
			}
		}
		// wattle along the back where the Bohemian village weaves its fences
		if (Bohemia.is(d) && rows >= 2 && !gable) {
			List<int[]> run = Garden.line(gx0, gz1, gx1, gz1);
			Garden.fence(d, run, -1, Dirs.FRONT);
			Bohemia.wattle(d, gx0, gz1, gx1, gz1);
		}
	}

	private static Integer doorOf(Facade f) {
		return f.openings(Dirs.FRONT).stream().filter(o -> o.kind() == Facade.Kind.DOOR).map(Facade.Opening::at).findFirst().orElse(null);
	}
}
