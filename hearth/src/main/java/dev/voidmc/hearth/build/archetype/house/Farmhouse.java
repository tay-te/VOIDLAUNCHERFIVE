package dev.voidmc.hearth.build.archetype.house;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.archetype.work.Barn;
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
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.homestead.Barns;
import dev.voidmc.hearth.build.kit.homestead.Bounds;
import dev.voidmc.hearth.build.kit.homestead.Land;
import dev.voidmc.hearth.build.kit.homestead.Crops;
import dev.voidmc.hearth.build.kit.homestead.Round;
import dev.voidmc.hearth.build.kit.homestead.Sheds;
import dev.voidmc.hearth.build.kit.homestead.Spots;
import dev.voidmc.hearth.build.kit.homestead.Terraces;
import dev.voidmc.hearth.build.kit.homestead.Trees;
import dev.voidmc.hearth.build.kit.homestead.Yard;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:house/farmhouse}: a farmstead — the house, its barn or byre, and the working yard between them behind
 * a gate on the track. The layout is the culture's:
 * <ul>
 *   <li><b>steading</b> (timber and brick country): a two-storey house on the track, its barn beside it with the cart
 *   doors to the yard (or, on a smaller plot, a lean-to byre and cart shed behind), the yard with a well, a dung heap
 *   and a hen house, a kitchen garden behind the house;</li>
 *   <li><b>laithe</b> (stone and log country): house and barn in one long range under one roof, the house end with its
 *   chimney and its windows, the barn end with tall cart doors, before a walled yard;</li>
 *   <li><b>court</b> (the dry south, the karst): ranges round a paved court behind a wall and a gateway, the house at
 *   the back with its roof terrace, a stable and a store either side, an oven, a well and a tree in the court;</li>
 *   <li><b>tun</b> (the north): small log buildings round a yard — the house, a byre and a granary on staddle stones;</li>
 *   <li><b>compound</b> (the steppe): a round house, a round granary and a ring kraal inside a fence;</li>
 *   <li><b>raised</b> (bayou and tropics): the house on posts over its yard, a granary on posts, a garden and a pen.</li>
 * </ul>
 * Every layout levels its yard to the track, keeps its doors and gate clear, and bounds the plot with the culture's
 * boundary.
 *
 * <p>Params (all optional): {@code layout} (string: steading, laithe, court, tun, compound, raised), {@code roof} (a
 * RoofForm name for the house), {@code storeys} (int 1–2).
 */
public final class Farmhouse implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("house/farmhouse"), ArchetypeCategory.HOUSING, Set.of(), 2,
		11, 17, 11, 17, 11, 15, SettlementTier.HAMLET, Set.of(), Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 0, 1.0);

	enum Layout {
		STEADING, LAITHE, COURT, TUN, COMPOUND, RAISED
	}

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		raise(d);
		dev.voidmc.hearth.build.kit.townhome.Settle.finish(d);
	}

	/** The house as drawn, before its last walk-round ({@link dev.voidmc.hearth.build.kit.townhome.Settle}). */
	private void raise(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Layout layout = layout(d);
		// the gate on the track, and the yard levelled to it
		int gateX = layout == Layout.COURT ? w / 2 : Math.clamp(d.chance(0.5) ? w - 3 : 2, 2, w - 3);
		d.entrance(gateX, Terraces.street(d, gateX), -1);
		d.poi(PoiType.GATE, gateX, 0, 0, Dirs.BACK);
		// compact layouts stand on one levelled yard; the others level their yards and leave the rest as it lies
		if (layout == Layout.COURT || layout == Layout.TUN || layout == Layout.COMPOUND) {
			Land.yard(d, 0, 0, w - 1, dp - 1, 0);
		}
		int top = switch (layout) {
			case LAITHE -> laithe(d, w, dp, gateX);
			case COURT -> court(d, w, dp, gateX);
			case TUN -> tun(d, w, dp, gateX);
			case COMPOUND -> compound(d, w, dp, gateX);
			case RAISED -> raised(d, w, dp, gateX);
			default -> steading(d, w, dp, gateX);
		};
		// the gate's way in kept open and the boundary round the plot
		d.air(gateX, 0, 1);
		d.air(gateX, 1, 1);
		Bounds.around(d, 0, 0, w - 1, dp - 1, layout == Layout.COURT ? Bounds.Kind.NONE : Bounds.kind(d), List.of(new int[]{gateX, 0}));
		if (dev.voidmc.hearth.build.kit.homestead.Bohemia.is(d) && (layout == Layout.STEADING || layout == Layout.LAITHE)) {
			// the Bohemian farm's gate under its own little roof
			top = Math.max(top, dev.voidmc.hearth.build.kit.homestead.Bohemia.roofedGate(d, gateX, 0) ? 5 : 0);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, top + 2));
		// log walls peeled, the corner posts left in their bark
		dev.voidmc.hearth.build.kit.homestead.Logs.peelAll(d, d.maxHeight() - 1);
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	/** The culture's layout (or the param's). */
	private static Layout layout(Draft d) {
		Layout l = chosen(d);
		// a steading's barn stands behind its house across the yard: a shallow plot builds the range instead
		if (l == Layout.STEADING && d.depth() < 15) {
			return Layout.LAITHE;
		}
		return l;
	}

	private static Layout chosen(Draft d) {
		String p = d.param("layout", "");
		for (Layout l : Layout.values()) {
			if (l.name().equalsIgnoreCase(p)) {
				return l;
			}
		}
		Habits h = d.habits();
		WallKind k = h.walls().kind();
		double flat = h.roof().weight(RoofForm.FLAT);
		double pitched = h.roof().weight(RoofForm.GABLE) + h.roof().weight(RoofForm.HIP) + h.roof().weight(RoofForm.HALF_HIP)
			+ h.roof().weight(RoofForm.CROSS_GABLE) + h.roof().weight(RoofForm.PAGODA) + h.roof().weight(RoofForm.CONE);
		if (flat > pitched || d.chance(Math.min(0.7, h.plan().courtyard() * 1.2))) {
			return Layout.COURT;
		}
		if (h.plan().round() >= 0.5) {
			return d.chance(0.75) ? Layout.COMPOUND : Layout.STEADING;
		}
		if (h.plan().stilts() == Habits.Stilts.ALWAYS) {
			return Layout.RAISED;
		}
		if (k == WallKind.LOG) {
			return d.chance(0.55) ? Layout.TUN : Layout.LAITHE;
		}
		if (k == WallKind.STONE || h.yard().fence() == Habits.FenceKind.WALL) {
			return d.chance(0.65) ? Layout.LAITHE : Layout.STEADING;
		}
		return d.chance(0.65) ? Layout.STEADING : Layout.LAITHE;
	}

	// ------------------------------------------------------------------------------------------------ steading

	/**
	 * The steading: the house on the track to one side, its barn beside it (cart doors to the yard strip before it) on
	 * a wide plot or a byre and cart shed behind on a narrow one, the kitchen garden behind the house, the yard's well,
	 * coop and dung heap. Returns the top of what it built.
	 */
	private static int steading(Draft d, int w, int dp, int gateX) {
		boolean houseLeft = gateX > w / 2;
		int hw = Math.min(w >= 15 ? 9 : 8, w - 3);
		int hd = 7;
		int hx0 = houseLeft ? 1 : w - 1 - hw;
		int hx1 = hx0 + hw - 1;
		int hz0 = dp >= 13 ? 2 : 1;
		int hz1 = hz0 + hd - 1;
		// the yard: the strip along the track and the side beside the house levelled to the floor
		Land.yard(d, 0, 0, w - 1, hz0 - 1, 0);
		Land.yard(d, houseLeft ? hx1 + 1 : 1, 1, houseLeft ? w - 2 : hx0 - 1, dp - 2, 0);
		RoofForm form = FarmParts.form(d);
		int storeys = FarmParts.storeys(d, hw, hd, form, Math.clamp(d.param("storeys", 2), 1, 2));
		Direction.Axis ridge = d.chance(d.habits().roof().frontGable()) ? Direction.Axis.Z : Direction.Axis.X;
		FarmParts.House house = FarmParts.house(d, hx0, hz0, hx1, hz1, 0, storeys, form, ridge, (hx0 + hx1) / 2, true);
		keepDoor(d, house);
		int top = house.roof() != null ? house.roof().ridgeY : house.deck() + 3;
		// the barn across the back of the yard, its cart doors facing the house
		int bz1 = dp - 2;
		int bd = Math.clamp(dp - hz1 - 5, 4, 7);
		int bz0 = bz1 - bd + 1;
		int bx0 = 1;
		int bx1 = w - 2;
		if ((bx1 - bx0 + 1) % 2 == 0) {
			if (houseLeft) {
				bx1--;
			} else {
				bx0++;
			}
		}
		Land.yard(d, 1, hz1 + 1, w - 2, bz0 - 1, 0);
		top = Math.max(top, rangeBarn(d, bx0, bz0, bx1, bz1, 0));
		// the kitchen garden beside the house on the track, the yard between house and barn
		int gx0 = houseLeft ? hx1 + 2 : 1;
		int gx1 = houseLeft ? w - 2 : hx0 - 2;
		FarmParts.kitchenGarden(d, gx0, Math.max(1, hz0), gx1, Math.min(hz1 - 1, hz0 + 3), 0);
		yard(d, w, dp, gateX, 1, hz1 + 1, w - 2, bz0 - 1);
		front(d, house, hz0);
		return top;
	}

	/**
	 * A barn whose ridge runs along x, cart doors in the middle of its long front wall, a loft over stalls either side
	 * of the threshing floor, its gables boarded, a gambrel where the culture builds in timber. Returns its top.
	 */
	static int rangeBarn(Draft d, int x0, int z0, int x1, int z1, int y) {
		int depth = z1 - z0 + 1;
		int groundH = 4;
		int loftH = 2;
		RoofForm form = Barns.form(d, false);
		if (form == RoofForm.FLAT || form == RoofForm.CONE || form == RoofForm.DOME || form == RoofForm.CROSS_GABLE) {
			form = RoofForm.GABLE;
		}
		while (loftH > 1 && Barn.ridge(d, form, depth, y + groundH + loftH - 1) + 2 >= d.maxHeight()) {
			loftH--;
		}
		if (Barn.ridge(d, form, depth, y + groundH + loftH - 1) + 2 >= d.maxHeight()) {
			form = RoofForm.GABLE;
		}
		int doorW = x1 - x0 + 1 >= 9 ? 3 : 1;
		Barns.Hall hall = Barns.shell(d, x0, z0, x1, z1, y, groundH, loftH, null, doorW, false, false);
		Roofs.Opts o = Roofs.Opts.of(d).withEaves(1, 1);
		if (form == RoofForm.GAMBREL) {
			o = o.withPitch(Math.max(0.9, o.pitch()));
		} else if (dev.voidmc.hearth.build.kit.homestead.Bohemia.is(d)) {
			o = o.withPitch(dev.voidmc.hearth.build.kit.homestead.Bohemia.pitch(d, o.pitch(), hall.plateY(), depth / 2.0, form));
		}
		Roofs.Roof roof = Roofs.build(d, x0, z0, x1, z1, hall.plateY(), form, Direction.Axis.X, o, false, false).roof();
		if (dev.voidmc.hearth.build.kit.homestead.Bohemia.is(d)) {
			dev.voidmc.hearth.build.kit.homestead.Bohemia.carryRidge(d, roof);
		}
		Barns.Build bb = Barns.build(d);
		if (form != RoofForm.HIP && form != RoofForm.PAGODA) {
			try (var s = d.stage(StageKind.WALLS)) {
				for (int gx : new int[]{x0, x1}) {
					for (int z = z0 + 1; z < z1; z++) {
						for (int yy = hall.plateY() + 1; yy < roof.bottomY(gx, z); yy++) {
							d.set(gx, yy, z, bb == Barns.Build.MASONRY ? d.mat(Role.WALL).full() : bb == Barns.Build.LOGS
								? d.mat(Role.WALL).log(Direction.Axis.Z).orElse(d.mat(Role.WALL).full()) : Sheds.boards(d, Direction.Axis.Z));
						}
					}
				}
			}
		}
		for (int z = z0 + 1; z < z1; z++) {
			for (int x = x0 + 1; x < x1; x++) {
				int under = roof.bottomY(x, z);
				if (under - 1 > hall.plateY()) {
					d.carve(x, hall.plateY() + 1, z, x, under - 1, z);
				}
			}
		}
		Barns.cartDoor(d, Side.of(x0, z0, x1, z1, Dirs.FRONT), y, doorW);
		Barns.interior(d, hall, doorW, false);
		d.air(hall.midX(), y, z0 - 1);
		d.air(hall.midX(), y + 1, z0 - 1);
		return roof.ridgeY + 1;
	}

	// ------------------------------------------------------------------------------------------------ laithe

	/**
	 * The laithe: house and barn in one range along the back of the yard under one roof. The house end is two storeys
	 * with its door, windows and chimney; the barn end one tall storey with cart doors and a loft. Returns the top.
	 */
	private static int laithe(Draft d, int w, int dp, int gateX) {
		int x0 = 1;
		int x1 = w - 2;
		int depth = Math.clamp(dp - 5, 7, 8);
		int z1 = dp - 2;
		int z0 = z1 - depth + 1;
		int len = x1 - x0 + 1;
		int houseLen = Math.clamp(len / 2 + 1, 8, 9);
		boolean houseLeft = d.chance(0.5);
		// the yard before the range levelled to the floor
		Land.yard(d, 0, 0, w - 1, z0 - 1, 0);
		int hx0 = houseLeft ? x0 : x1 - houseLen + 1;
		int hx1 = hx0 + houseLen - 1;
		int bx0 = houseLeft ? hx1 + 1 : x0;
		int bx1 = houseLeft ? x1 : hx0 - 1;
		boolean bohemian = dev.voidmc.hearth.build.kit.homestead.Bohemia.is(d);
		RoofForm form = bohemian ? dev.voidmc.hearth.build.kit.homestead.Bohemia.form(d)
			: d.habits().roof().pick(d.random(), RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.HIP);
		form = HouseBuilder.roofParam(d, form);
		if (form != RoofForm.GABLE && form != RoofForm.HALF_HIP && form != RoofForm.HIP) {
			form = RoofForm.GABLE;
		}
		int sh = Math.max(4, d.habits().walls().storeyHeight());
		// (a Bohemian range is one long low storey and a loft over a knee wall under a steep roof: house, byre and barn
		// in a row)
		int storeys = !bohemian && Barn.ridge(d, RoofForm.GABLE, depth, 2 * sh - 1) + 2 < d.maxHeight() ? 2 : 1;
		int knee = bohemian ? 2 : 0;
		int plate = storeys * sh - 1 + knee;
		// the barn end first (its wall against the house is redrawn by the house)
		Barns.Hall barn = null;
		if (bx1 - bx0 + 1 >= 5) {
			int groundH = storeys == 2 ? sh + 1 : sh;
			// (a loft at least two high over the byre)
			barn = Barns.shell(d, bx0, z0, bx1, z1, 0, groundH, plate - groundH + 1, null, 3, houseLeft, !houseLeft);
			Barns.cartDoor(d, Side.of(bx0, z0, bx1, z1, Dirs.FRONT), 0, (bx1 - bx0 + 1) >= 7 ? 3 : 1);
		}
		// the house end
		int[] heights = new int[storeys + (knee > 0 ? 1 : 0)];
		java.util.Arrays.fill(heights, sh);
		if (knee > 0) {
			heights[storeys] = knee;
		}
		Body body = new Body(hx0, z0, hx1, z1, 0, heights, !houseLeft && barn != null, houseLeft && barn != null);
		d.body(x0, z0, x1, z1);
		d.eave(plate);
		Facade facade = Facade.plan(d, body, (hx0 + hx1) / 2, 1, 1, 0.5);
		Groundwork.footings(d, hx0, z0, hx1, z1, -1);
		HouseBuilder.shell(d, body, facade);
		HouseBuilder.openings(d, body, facade, true);
		// one roof over the whole range
		Roofs.Opts o = Roofs.Opts.of(d);
		if (bohemian) {
			o = o.withPitch(dev.voidmc.hearth.build.kit.homestead.Bohemia.pitch(d, o.pitch(), plate, depth / 2.0, form));
		}
		Roofs.Plan plan = Roofs.plan(d, x0, z0, x1, z1, plate, form, Direction.Axis.X, o, false, false);
		if (storeys == 1 && depth >= 7 && !bohemian && d.chance(0.6)) {
			// a dormer lighting the attic over the house end
			Side front = body.side(0, Dirs.FRONT);
			plan.dormer(front, (hx0 + hx1) / 2 + (houseLeft ? -1 : 1), 1, plate + 1);
		}
		Roofs.Roof roof = plan.draw(d).roof();
		if (bohemian) {
			dev.voidmc.hearth.build.kit.homestead.Bohemia.carryRidge(d, roof);
			if (form != RoofForm.HIP) {
				dev.voidmc.hearth.build.kit.homestead.Bohemia.gables(d, roof, Direction.Axis.X, new int[]{x0, z0, x1, z1}, plate);
			}
			dev.voidmc.hearth.build.kit.homestead.Bohemia.fieldstone(d, hx0, z0, hx1, z1, 0, Math.max(0, d.habits().walls().plinth() - 1));
		}
		// the barn end's gable boarded or walled as the barn is
		if (barn != null && form != RoofForm.HIP) {
			int gx = houseLeft ? x1 : x0;
			Barns.Build bb = Barns.build(d);
			try (var s = d.stage(StageKind.WALLS)) {
				for (int z = z0 + 1; z < z1; z++) {
					for (int y = plate + 1; y < roof.bottomY(gx, z); y++) {
						d.set(gx, y, z, bb == Barns.Build.MASONRY ? d.mat(Role.WALL).full() : bb == Barns.Build.LOGS
							? d.mat(Role.WALL).log(Direction.Axis.Z).orElse(d.mat(Role.WALL).full()) : Sheds.boards(d, Direction.Axis.Z));
					}
				}
			}
			// the loft door high in the barn's gable
			int gz = (z0 + z1) / 2;
			if (roof.bottomY(gx, gz) > plate + 3) {
				d.inStage(StageKind.OPENINGS, () -> {
					d.set(gx, plate + 1, gz, d.mat(Role.SHUTTER).trapdoor(houseLeft ? Dirs.LEFT : Dirs.RIGHT, Half.BOTTOM, true));
					d.set(gx, plate + 2, gz, Blocks.AIR.defaultBlockState());
				});
			}
		}
		// the open roof space over the barn
		if (barn != null) {
			for (int z = z0 + 1; z < z1; z++) {
				for (int x = bx0 + (houseLeft ? 0 : 1); x <= bx1 - (houseLeft ? 1 : 0); x++) {
					int under = roof.bottomY(x, z);
					if (under - 1 > plate) {
						d.carve(x, plate + 1, z, x, under - 1, z);
					}
				}
			}
			Barns.interior(d, barn, (bx1 - bx0 + 1) >= 7 ? 3 : 1, false);
		}
		// the house: hearth and stack on its gable end, stair and rooms
		Direction chimneySide = houseLeft ? Dirs.LEFT : Dirs.RIGHT;
		int[] hearth = HouseBuilder.hearthSpot(d, body, chimneySide, facade);
		if (hearth != null) {
			Furniture.hearth(d, hearth[0], 0, hearth[1], hearth[2] == 0 ? Dirs.RIGHT : hearth[2] == 1 ? Dirs.LEFT : Dirs.FRONT, false);
			Chimneys.stack(d, hearth[0], hearth[1], 2, roof, roof.ridgeY + 1);
		}
		HouseBuilder.interior(d, body, facade, storeys >= 2 && knee == 0 ? null : roof, hearth, null, FarmParts.stairRunsRight(d, body, hearth));
		Integer door = HouseBuilder.doorColumn(facade);
		FarmParts.ensureBeds(d, body, door, 2);
		FarmParts.House house = new FarmParts.House(body, facade, roof, Integer.MIN_VALUE, door);
		keepDoor(d, house);
		if (d.habits().snow()) {
			Garden.snowBanks(d, x0, z0, x1, z1, 0, door);
		}
		// the walled yard before the range
		yard(d, w, dp, gateX, 1, 1, w - 2, z0 - 1);
		FarmParts.kitchenGarden(d, houseLeft ? 1 : w - 5, 2, houseLeft ? 3 : w - 3, Math.min(z0 - 2, 4), 0);
		return roof.ridgeY + 1;
	}

	// ------------------------------------------------------------------------------------------------ court

	/**
	 * The court farm: a wall along the track with a gateway, the house range at the back (two storeys and a roof
	 * terrace where the roofs are flat), a stable range and a store range down the sides, all opening onto a paved court
	 * with a well, a bread oven and a tree. Returns the top.
	 */
	private static int court(Draft d, int w, int dp, int gateX) {
		RoofForm form = FarmParts.form(d);
		boolean flat = form == RoofForm.FLAT || d.habits().roof().weight(RoofForm.FLAT) > 2;
		if (flat) {
			form = RoofForm.FLAT;
		} else if (form == RoofForm.CROSS_GABLE || form == RoofForm.GAMBREL || form == RoofForm.SALTBOX) {
			form = RoofForm.GABLE;
		}
		// the house range across the back
		int hd = Math.clamp(dp / 3, 5, 7);
		int hz1 = dp - 2;
		int hz0 = hz1 - hd + 1;
		int hx0 = 1;
		int hx1 = w - 2;
		int storeys = FarmParts.storeys(d, hx1 - hx0 + 1, hd, form, 2);
		FarmParts.House house = FarmParts.house(d, hx0, hz0, hx1, hz1, 0, storeys, form, Direction.Axis.X, w / 2, false);
		keepDoor(d, house);
		int top = house.roof() != null ? house.roof().ridgeY : house.deck() + 3;
		// on a flat roof, a ramada in a back corner of the terrace: shade for the evening, pots in it
		if (house.roof() == null && house.deck() != Integer.MIN_VALUE) {
			int deck = house.deck();
			boolean first = d.chance(0.5);
			int rz1 = hz1 - 1;
			int rz0 = Math.max(hz0 + 1, rz1 - 2);
			// a back corner, the other, or the middle: wherever the terrace is clear (the stair head, the chimney)
			for (int k = 0; k < 3; k++) {
				boolean right = k == 2 ? false : (k == 0) == first;
				int rx0 = k == 2 ? Math.max(hx0 + 1, (hx0 + hx1) / 2 - 2) : right ? Math.max(hx0 + 1, hx1 - 4) : hx0 + 1;
				int rx1 = k == 2 ? Math.min(hx1 - 1, rx0 + 4) : right ? hx1 - 1 : Math.min(hx1 - 1, hx0 + 4);
				if (rx1 - rx0 >= 2 && dev.voidmc.hearth.build.kit.homestead.Sheds.ramada(d, rx0, rz0, rx1, rz1, deck, false)) {
					top = Math.max(top, deck + 3);
					int px = (rx0 + rx1) / 2;
					d.inStage(StageKind.EXTERIOR, () -> d.set(px, deck, rz1, States.of(Blocks.DECORATED_POT)));
					break;
				}
			}
		}
		// the side ranges: a stable on one side, a store on the other, one storey, opening onto the court
		int sw = 3;
		int sz0 = 2;
		int sz1 = hz0 - 2;
		boolean stableLeft = d.chance(0.5);
		if (sz1 - sz0 + 1 >= 4) {
			top = Math.max(top, range(d, stableLeft ? 1 : w - 1 - sw, sz0, stableLeft ? sw : w - 2, sz1, stableLeft ? Dirs.RIGHT : Dirs.LEFT, true, form));
			top = Math.max(top, range(d, stableLeft ? w - 1 - sw : 1, sz0, stableLeft ? w - 2 : sw, sz1, stableLeft ? Dirs.LEFT : Dirs.RIGHT, false, form));
		}
		// the wall along the track with its gateway
		frontWall(d, w, gateX);
		// the court: paving, a well, the oven, a tree, pots
		int cx0 = sw + 1;
		int cx1 = w - 2 - sw;
		int cz0 = 2;
		int cz1 = hz0 - 1;
		if (flat) {
			// the dry lands' court: an arcade before the house, rounded or crested parapets, a painted gate
			if (cx1 - cx0 >= 4 && hz0 - 2 - cz0 >= 3) {
				portico(d, cx0, cx1, hz0 - 2, hz0 - 1, house);
				top = Math.max(top, 6);
			}
			// the side ranges are plain farm buildings: their parapets rounded over
			var kind = dev.voidmc.hearth.build.kit.homestead.Adobe.Parapet.ROUNDED;
			if (sz1 - sz0 + 1 >= 4) {
				int rx0 = stableLeft ? 1 : w - 1 - sw;
				int rx1 = stableLeft ? sw : w - 2;
				int lx0 = stableLeft ? w - 1 - sw : 1;
				int lx1 = stableLeft ? w - 2 : sw;
				int deck = Math.max(4, d.habits().walls().storeyHeight()) - 1 + 1;
				dev.voidmc.hearth.build.kit.homestead.Adobe.parapet(d, rx0, sz0, rx1, sz1, deck, kind);
				dev.voidmc.hearth.build.kit.homestead.Adobe.parapet(d, lx0, sz0, lx1, sz1, deck, kind);
			}
			gate(d, gateX);
		}
		FarmParts.yardSurface(d, cx0, cz0, cx1, cz1, 0);
		courtThings(d, cx0, cz0, cx1, cz1, house);
		return top;
	}

	/**
	 * A one-storey range along a side of the court ({@code [x0..x1] × [z0..z1]}), its openings toward {@code open}: a
	 * stable (stalls, hay, the feed cauldron) or a store (barrels, sacks, chests). Flat-roofed or under the culture's roof
	 * sloping down to the court. Returns its top.
	 */
	private static int range(Draft d, int x0, int z0, int x1, int z1, Direction open, boolean stable, RoofForm form) {
		int sh = Math.max(4, d.habits().walls().storeyHeight());
		Body b = new Body(x0, z0, x1, z1, 0, new int[]{sh}, false, false);
		Groundwork.footings(d, x0, z0, x1, z1, -1);
		Facade f = Facade.plan(d, b, -1, 1, 1, 0.0);
		HouseBuilder.shell(d, b, f);
		Side court = b.side(0, open);
		// openings to the court: a door in the middle, small windows either side
		int mid = court.middle();
		dev.voidmc.hearth.build.kit.Openings.door(d, court, court.column(mid), 0, 1, new dev.voidmc.hearth.build.kit.Openings.DoorOpts(false, false, false));
		for (int i = 1; i < court.last(); i++) {
			if (Math.abs(i - mid) == 2) {
				FarmParts.slit(d, court, i, 1);
			}
		}
		int ex = court.outX(mid, 1);
		int ez = court.outZ(mid, 1);
		d.air(ex, 0, ez);
		d.air(ex, 1, ez);
		int top;
		if (form == RoofForm.FLAT) {
			int deck = Roofs.flat(d, x0, z0, x1, z1, b.plateY(), true);
			top = deck + 2;
		} else {
			// a lean-to roof falling toward the court
			Roofs.Field lean = Roofs.shed(x0 - (open == Dirs.LEFT ? 1 : 0), z0, x1 + (open == Dirs.RIGHT ? 1 : 0), z1, open.getOpposite(),
				Roofs.Profile.straight(0.5));
			Roofs.Roof r;
			try (var s = d.stage(StageKind.ROOF)) {
				r = Roofs.raster(d, lean, b.plateY() + 1, Math.max(0, x0 - (open == Dirs.LEFT ? 1 : 0)), z0,
					Math.min(d.frontage() - 1, x1 + (open == Dirs.RIGHT ? 1 : 0)), z1, d.mat(Role.ROOF), d.mat(Role.ROOF_TRIM), Habits.Ridge.SLAB);
			}
			try (var s = d.stage(StageKind.WALLS)) {
				for (Side side : b.sides(0)) {
					for (int i = 0; i <= side.last(); i++) {
						int x = side.x(i);
						int z = side.z(i);
						for (int y = b.plateY() + 1; y < r.bottomY(x, z); y++) {
							d.set(x, y, z, d.mat(Role.WALL).full());
						}
					}
				}
			}
			top = r.ridgeY + 1;
		}
		// inside
		Rooms.Room room = new Rooms.Room(x0 + 1, z0 + 1, x1 - 1, z1 - 1, 0, sh - 1);
		room.keep(court.x(mid) - Dirs.dx(open), court.z(mid) - Dirs.dz(open));
		if (stable) {
			try (var s = d.stage(StageKind.INTERIOR)) {
				for (int z = z0 + 1; z <= z1 - 1; z++) {
					int x = open == Dirs.RIGHT ? x0 + 1 : x1 - 1;
					if (room.claim(x, z)) {
						d.set(x, 0, z, Noise.unit(d.seed(), x, 0, z) < 0.5 ? Blocks.HAY_BLOCK.defaultBlockState() : Blocks.COMPOSTER.defaultBlockState());
					}
				}
			}
			int cx = court.x(mid) - Dirs.dx(open);
			int cz = court.z(mid) - Dirs.dz(open) + 1;
			if (cz <= z1 - 1 && room.claim(cx, cz)) {
				d.inStage(StageKind.INTERIOR, () -> d.set(cx, 0, cz, Blocks.CAULDRON.defaultBlockState()));
				Spots.at(d, PoiType.JOB_SITE, cx, 0, cz - 1, Dirs.BACK, JobType.HERDER);
			}
		} else {
			Rooms.furnish(d, room, Rooms.Type.STORAGE, 0, JobType.NONE);
		}
		return top;
	}

	/**
	 * An arcade before the house across the court: a row of piers and round-headed arches on {@code za}, a walk behind it
	 * on {@code zw} against the house's front, roofed by a deck with a low parapet (a riwaq, a portal). Its walk is kept
	 * clear; the house door opens onto it.
	 */
	private static void portico(Draft d, int x0, int x1, int za, int zw, FarmParts.House house) {
		Mat wall = d.mat(Role.WALL);
		Mat accent = d.mat(Role.WALL_ACCENT);
		Mat deck = d.mat(Role.ROOF);
		int h = 3;
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = x0; x <= x1; x++) {
				for (int y = Math.min(-1, d.site().ground(x, za) - 1); y < 0; y++) {
					d.set(x, y, za, d.mat(Role.FOUNDATION).full());
				}
				for (int y = 0; y <= h; y++) {
					d.set(x, y, za, wall.full());
				}
				d.set(x, h + 1, za, accent.has(Form.SLAB) ? accent.slab(SlabType.BOTTOM) : wall.slab(SlabType.BOTTOM));
			}
		}
		Side arc = Side.of(x0, za, x1, za, Dirs.FRONT);
		dev.voidmc.hearth.build.kit.townhome.Projections.arcade(d, arc, 1, arc.last() - 1, 0, h, 2, false);
		try (var s = d.stage(StageKind.ROOF)) {
			for (int x = x0; x <= x1; x++) {
				d.set(x, h, zw, deck.full());
				d.set(x, -1, zw, d.mat(Role.PAVING).full());
			}
		}
		// the walk stays clear; a lantern hangs in it
		for (int x = x0; x <= x1; x++) {
			d.air(x, 0, zw);
			d.air(x, 1, zw);
			d.air(x, 2, zw);
		}
		Furniture.hangingLantern(d, (x0 + x1) / 2, h, zw, 0);
		Furniture.hangingLantern(d, house.door() != null && Math.abs(house.door() - (x0 + x1) / 2) > 1 ? house.door() : x0 + 1, h, zw, 0);
	}

	/** A painted gate (the style's door) in the gateway, a tile set in its threshold. */
	private static void gate(Draft d, int gateX) {
		d.inStage(StageKind.OPENINGS, () -> d.door(gateX, 0, 1, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), Dirs.FRONT,
			net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT));
		dev.voidmc.hearth.build.kit.homestead.Adobe.threshold(d, gateX, 0, 0);
	}

	/** The wall along the track: the culture's wall with a coping, a gateway with posts and a lintel at the gate. */
	private static void frontWall(Draft d, int w, int gateX) {
		Mat wall = d.mat(Role.WALL);
		Mat accent = d.mat(Role.WALL_ACCENT);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = 0; x < w; x++) {
				if (Math.abs(x - gateX) <= 0) {
					continue;
				}
				int y0 = Spots.level(d, x, 1);
				for (int y = Math.min(-1, d.site().ground(x, 1) - 1); y <= y0 + 1; y++) {
					d.set(x, y, 1, y < 0 ? d.mat(Role.FOUNDATION).full() : wall.full());
				}
				d.set(x, y0 + 2, 1, accent.has(Form.SLAB) ? accent.slab(SlabType.BOTTOM) : wall.slab(SlabType.BOTTOM));
			}
			// the gateway: posts a block higher, a lintel across
			for (int x : new int[]{gateX - 1, gateX + 1}) {
				if (x >= 0 && x < w) {
					d.set(x, 2, 1, wall.full());
					d.set(x, 3, 1, accent.full());
				}
			}
			d.set(gateX, 3, 1, accent.full());
		}
		d.inStage(StageKind.EXTERIOR, () -> {
			d.set(gateX, -1, 1, d.mat(Role.PAVING).full());
			d.set(gateX, -1, 0, d.mat(Role.PAVING).full());
		});
		d.air(gateX, 0, 1);
		d.air(gateX, 1, 1);
		d.air(gateX, 2, 1);
		// a lantern over the gateway and one at the gate outside
		Furniture.hangingLantern(d, gateX, 3, 1, 0);
	}

	/** The court's furniture: a well in the middle, a bread oven in a corner, a tree in another, pots by the doors. */
	private static void courtThings(Draft d, int x0, int z0, int x1, int z1, FarmParts.House house) {
		FarmParts.yardThings(d, x0, z0 + 1, x1, z1, 0, true);
		// the oven: a dome of the culture's clay over a smoker, in a front corner
		int ox = x0;
		int oz = z0 + 1;
		if (FarmParts.freeYard(d, ox, 0, oz) && FarmParts.freeYard(d, ox + 1, 0, oz) && 3 < d.maxHeight()) {
			Mat clay = d.mat(Role.CHIMNEY);
			try (var s = d.stage(StageKind.EXTERIOR)) {
				d.set(ox, 0, oz, clay.full());
				d.set(ox + 1, 0, oz, States.facing(Blocks.SMOKER, Dirs.RIGHT));
				d.set(ox, 1, oz, clay.stairsOrSlab(Dirs.LEFT, Half.BOTTOM));
				d.set(ox + 1, 1, oz, clay.slab(SlabType.BOTTOM).orElse(clay.full()));
			}
			d.poi(PoiType.HEARTH, ox + 2, 0, oz, Dirs.LEFT);
		}
		// a tree in the opposite corner
		int tx = x1 - 1;
		int tz = z0 + 2;
		if (FarmParts.freeYard(d, tx, 0, tz) && Trees.top(d, tx, 0, tz, Trees.kind(d), 1) < d.maxHeight()) {
			d.air(tx, 0, tz);
			d.remove(tx, 0, tz);
			Trees.fruit(d, tx, 0, tz, Trees.kind(d), 1);
		}
	}

	// ------------------------------------------------------------------------------------------------ tun

	/**
	 * The tun: small log buildings round a yard — the house at the back on one side, the byre on the other, a granary on
	 * staddle stones by the gate. Returns the top.
	 */
	private static int tun(Draft d, int w, int dp, int gateX) {
		boolean houseLeft = gateX > w / 2;
		int hw = Math.min(8, w - 6);
		int hd = 7;
		int hx0 = houseLeft ? 1 : w - 1 - hw;
		int hx1 = hx0 + hw - 1;
		int hz1 = dp - 2;
		int hz0 = hz1 - hd + 1;
		RoofForm form = FarmParts.form(d);
		if (form == RoofForm.FLAT || form == RoofForm.CROSS_GABLE) {
			form = RoofForm.GABLE;
		}
		int storeys = FarmParts.storeys(d, hw, hd, form, 1);
		FarmParts.House house = FarmParts.house(d, hx0, hz0, hx1, hz1, 0, storeys, form, Direction.Axis.X, (hx0 + hx1) / 2, false);
		keepDoor(d, house);
		int top = house.roof() != null ? house.roof().ridgeY : 8;
		// the byre on the other side, its gable to the yard
		int bw = 7;
		int bx0 = houseLeft ? w - 1 - bw : 1;
		int bx1 = bx0 + bw - 1;
		int bz0 = Math.max(3, dp - 10);
		int bz1 = dp - 2;
		if ((houseLeft ? bx0 > hx1 + 1 : bx1 < hx0 - 1) && bz1 - bz0 + 1 >= 7) {
			int loftH = 2;
			while (loftH > 1 && Barn.ridge(d, RoofForm.GABLE, bw, 3 + loftH) + 2 >= d.maxHeight()) {
				loftH--;
			}
			Barns.Hall byre = Barns.shell(d, bx0, bz0, bx1, bz1, 0, 4, loftH, RoofForm.GABLE, 1, false, false);
			Barns.interior(d, byre, 1, false);
			d.air(byre.midX(), 0, bz0 - 1);
			d.air(byre.midX(), 1, bz0 - 1);
			if (byre.roof() != null) {
				top = Math.max(top, byre.roof().ridgeY);
			}
		}
		// the granary on staddles near the gate, on the house's side
		int gx0 = houseLeft ? 2 : w - 6;
		int gz0 = 2;
		if (hz0 - gz0 >= 5 && gx0 + 3 <= w - 2 && 9 < d.maxHeight()) {
			Roofs.Roof g = Sheds.granary(d, gx0, gz0, gx0 + 3, gz0 + 2, 0, Dirs.BACK);
			if (g != null) {
				top = Math.max(top, g.ridgeY);
			}
		}
		yard(d, w, dp, gateX, 1, 1, w - 2, dp - 2);
		return top;
	}

	// ------------------------------------------------------------------------------------------------ compound

	/**
	 * The compound: a round house at the back, a round granary raised on a stone base to one side, a ring kraal of
	 * fence to the other, a cooking fire between them, all inside the fence. Returns the top.
	 */
	private static int compound(Draft d, int w, int dp, int gateX) {
		boolean houseLeft = gateX > w / 2;
		double r = 3;
		double hcx = houseLeft ? 1 + r + 0.5 : w - 2 - r - 0.5;
		double hcz = dp - 2 - r - 1;
		Round.Ring ring = Round.ring(hcx, hcz, r);
		int base = 0;
		int plate = base + 3;
		footRing(d, ring, base);
		Round.walls(d, ring, base, plate);
		int[] door = ring.cellToward(Dirs.FRONT);
		d.inStage(StageKind.OPENINGS, () -> d.door(door[0], base, door[1], d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), Dirs.FRONT,
			net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT));
		d.entrance(door[0], base, door[1] - 1);
		d.poi(PoiType.DOOR, door[0], base, door[1] - 1, Dirs.BACK);
		d.air(door[0], base, door[1] - 1);
		d.air(door[0], base + 1, door[1] - 1);
		double pitch = 1.15;
		while (pitch > 0.6 && plate + 1 + pitch * (r + 1.5) + 2 >= d.maxHeight()) {
			pitch -= 0.1;
		}
		Roofs.Roof roof = Round.cone(d, ring, plate, 1, pitch, d.chance(0.25) ? d.mat(Role.THATCH) : d.mat(Role.ROOF));
		Rooms.Room room = Round.room(ring, base, 3);
		room.keep(door[0], door[1] + 1);
		Rooms.furnish(d, room, Rooms.Type.BEDROOM, 2, JobType.NONE);
		Round.fire(d, ring, room, base);
		Rooms.furnish(d, room, Rooms.Type.BEDROOM, 1, JobType.NONE);
		Rooms.furnish(d, room, Rooms.Type.STORAGE, 0, JobType.NONE);
		int top = roof.ridgeY + 2;
		// the granary: a small round store on a stone plinth, its floor a step up, under a cone
		double gr = 1.5;
		double gcx = houseLeft ? w - 2 - gr - 0.5 : 1 + gr + 0.5;
		double gcz = dp - 2 - gr - 0.5;
		Round.Ring gring = Round.ring(gcx, gcz, gr);
		if (gring.x0() >= 1 && gring.x1() <= w - 2 && gring.z1() <= dp - 2) {
			int gb = 1;
			footRing(d, gring, gb);
			Round.walls(d, gring, gb, gb + 2);
			int[] gd = gring.cellToward(Dirs.FRONT);
			d.inStage(StageKind.OPENINGS, () -> d.set(gd[0], gb + 1, gd[1], d.mat(Role.SHUTTER).trapdoor(Dirs.FRONT, Half.BOTTOM, true)));
			Roofs.Roof gr2 = Round.cone(d, gring, gb + 2, 1, 1.0, d.mat(Role.THATCH));
			top = Math.max(top, gr2.ridgeY + 2);
		}
		// the kraal: a ring of fence before the granary, a gate toward the yard; as big as the ground beside the house
		// allows, clear of the house's wall by a block (on a narrow plot it shrinks, or the herder works by the granary)
		int span0 = houseLeft ? ring.x1() + 2 : 1;
		int span1 = houseLeft ? w - 2 : ring.x0() - 2;
		double kr = Math.min(2.5, (span1 - span0) / 2.0);
		double kcx = (span0 + span1) / 2.0;
		double kcz = Math.max(kr + 1.5, gcz - gr - kr - 1.5);
		Round.Ring kraal = kr >= 1.5 ? Round.ring(kcx, kcz, kr) : null;
		if (kraal != null && (kraal.z0() < 2 || kraal.x0() < 1 || kraal.x1() > w - 2)) {
			kraal = null;
		}
		if (kraal != null) {
			Round.Ring k = kraal;
			int[] kg = k.cellToward(houseLeft ? Dirs.LEFT : Dirs.RIGHT);
			Mat fence = d.mat(Role.FENCE).has(Form.FENCE) ? d.mat(Role.FENCE) : d.mat(Role.FRAME);
			try (var s = d.stage(StageKind.EXTERIOR)) {
				for (int[] c : k.wall()) {
					if (c[0] < 1 || c[1] < 2 || c[0] > w - 2 || c[1] > dp - 2) {
						continue;
					}
					int y = Spots.level(d, c[0], c[1]);
					if (!Spots.free(d, c[0], y, c[1]) || !d.isAbsent(c[0], y, c[1])) {
						continue;
					}
					if (c[0] == kg[0] && c[1] == kg[1]) {
						d.set(c[0], y, c[1], fence.gate(houseLeft ? Dirs.LEFT : Dirs.RIGHT, false));
					} else {
						d.set(c[0], y, c[1], fence.fence());
					}
				}
			}
			int ox = kg[0] + (houseLeft ? -1 : 1);
			Spots.job(d, ox, kg[1], houseLeft ? Dirs.RIGHT : Dirs.LEFT, JobType.HERDER);
			int kx = (int) Math.round(k.cx());
			int kz = (int) Math.round(k.cz());
			Spots.pen(d, kx, Spots.level(d, kx, kz), kz, houseLeft ? Dirs.LEFT : Dirs.RIGHT);
		} else {
			int gz = Math.max(2, gring.z0() - 1);
			Spots.job(d, (int) Math.round(gcx), gz, Dirs.BACK, JobType.HERDER);
		}
		// a cooking fire in a ring of stones between the house and the gate
		cookingFire(d, (int) Math.round(hcx), Math.max(2, ring.z0() - 2), gateX, door[0], door[1] - 1);
		yard(d, w, dp, gateX, 1, 1, w - 2, dp - 2);
		return top;
	}

	/**
	 * A cooking fire in a ring of stones on the yard at (fx, fz), clear of the gate's column and of the cell (kx, kz)
	 * (the way to a door); the hearth's place beside it.
	 */
	private static void cookingFire(Draft d, int fx, int fz, int gateX, int kx, int kz) {
		if (!FarmParts.freeYard(d, fx, 0, fz) || Math.abs(fx - gateX) <= 1 || fx == kx && fz == kz) {
			return;
		}
		d.inStage(StageKind.EXTERIOR, () -> d.set(fx, 0, fz, States.lit(Blocks.CAMPFIRE, true).trySetValue(
			net.minecraft.world.level.block.state.properties.BlockStateProperties.SIGNAL_FIRE, false)));
		int px = fx;
		int pz = fz + 1 == kz && fx == kx ? fz - 1 : fz + 1;
		for (Direction dir : Dirs.HORIZONTAL) {
			int sx = fx + Dirs.dx(dir);
			int sz = fz + Dirs.dz(dir);
			if (FarmParts.freeYard(d, sx, 0, sz) && Noise.unit(d.seed(), sx, 0, sz) < 0.6 && !(sx == kx && sz == kz) && !(sx == px && sz == pz)) {
				d.inStage(StageKind.EXTERIOR, () -> d.set(sx, 0, sz, d.mat(Role.FOUNDATION).slab(SlabType.BOTTOM).orElse(d.mat(Role.FOUNDATION).full())));
			}
		}
		Spots.at(d, PoiType.HEARTH, px, 0, pz, Dirs.FRONT, null);
	}

	/** Footings and a floor under a ring at standing level y. */
	private static void footRing(Draft d, Round.Ring ring, int y) {
		for (int[] c : ring.disc()) {
			int g = d.site().ground(c[0], c[1]);
			boolean wall = ring.isWall(c[0], c[1]);
			try (var s = d.stage(StageKind.FOUNDATION)) {
				for (int yy = Math.min(y - 1, g - 2); yy < y; yy++) {
					d.set(c[0], yy, c[1], wall || yy < y - 1 ? d.mat(Role.FOUNDATION).full() : d.mat(Role.FLOOR).full());
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ raised

	/**
	 * The raised homestead: the house on posts at the back with a gallery and steps down, a granary on posts beside it,
	 * a garden and a fenced pen in the yard below. Returns the top.
	 */
	private static int raised(Draft d, int w, int dp, int gateX) {
		boolean houseLeft = gateX > w / 2;
		int hw = Math.min(8, w - 5);
		int hd = 6;
		int hx0 = houseLeft ? 1 : w - 1 - hw;
		int hx1 = hx0 + hw - 1;
		int hz1 = dp - 2;
		int hz0 = hz1 - hd + 1;
		int base = 2;
		int sh = Math.max(4, d.habits().walls().storeyHeight());
		Land.yard(d, 0, 0, w - 1, Math.max(0, hz0 - 3), 0);
		Body body = new Body(hx0, hz0, hx1, hz1, base, new int[]{sh}, false, false);
		d.body(hx0, hz0, hx1, hz1);
		Facade facade = Facade.plan(d, body, (hx0 + hx1) / 2, 1, 1, 0.6);
		Groundwork.stilts(d, hx0, hz0 - 2, hx1, hz1, base - 1, 2);
		dev.voidmc.hearth.build.kit.Floors.boards(d, hx0, hz0 - 2, hx1, hz1, base - 1);
		HouseBuilder.shell(d, body, facade);
		HouseBuilder.openings(d, body, facade, false);
		Integer door = HouseBuilder.doorColumn(facade);
		int dx = door != null ? door : (hx0 + hx1) / 2;
		dev.voidmc.hearth.build.kit.Porches.porch(d, body.side(0, Dirs.FRONT), hx0, hx1, 2, base, 3, dx);
		RoofForm form = HouseBuilder.roofParam(d, d.habits().roof().pick(d.random(), RoofForm.GABLE, RoofForm.HIP, RoofForm.HALF_HIP, RoofForm.SHED));
		if (form == RoofForm.SHED) {
			form = RoofForm.GABLE;
		}
		Roofs.Roof roof = Roofs.build(d, hx0, hz0, hx1, hz1, body.plateY(), form, Direction.Axis.X, Roofs.Opts.of(d), false, false).roof();
		// the household's beds before the stores and the kitchen fill the one room
		FarmParts.ensureBeds(d, body, door, 2);
		HouseBuilder.interior(d, body, facade, roof, null, null, d.chance(0.5));
		Groundwork.approach(d, dx, base, hz0 - 2);
		int top = roof.ridgeY + 2;
		// a granary on posts beside the house
		int gx0 = houseLeft ? w - 5 : 1;
		int gz0 = dp - 5;
		if ((houseLeft ? gx0 > hx1 + 1 : gx0 + 3 < hx0 - 1) && gz0 >= 2) {
			Roofs.Roof g = Sheds.granary(d, gx0, gz0, gx0 + 3, gz0 + 2, 0, Dirs.FRONT);
			if (g != null) {
				top = Math.max(top, g.ridgeY + 1);
			}
		}
		// the cooking fire out in the yard (no fire in a house of boards on posts), the garden and a pen
		cookingFire(d, houseLeft ? hx1 - 1 : hx0 + 1, Math.max(2, hz0 - 4), gateX, dx, hz0 - 3);
		yard(d, w, dp, gateX, 1, 1, w - 2, hz0 - 3);
		int px0 = houseLeft ? w - 6 : 1;
		pen(d, px0, 2, px0 + 4, Math.min(5, hz0 - 4));
		return top;
	}

	/** A small fenced pen with a gate toward the yard, a trough inside. */
	private static void pen(Draft d, int x0, int z0, int x1, int z1) {
		if (x1 - x0 < 3 || z1 - z0 < 2) {
			return;
		}
		Mat fence = d.mat(Role.FENCE).has(Form.FENCE) ? d.mat(Role.FENCE) : d.mat(Role.FRAME);
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				if (!FarmParts.freeYard(d, x, 0, z) || Spots.level(d, x, z) != 0) {
					return;
				}
			}
		}
		int gx = (x0 + x1) / 2;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] c : dev.voidmc.hearth.build.draft.Draft.ring(x0, z0, x1, z1)) {
				if (c[0] == gx && c[1] == z1) {
					d.set(c[0], 0, c[1], fence.gate(Dirs.FRONT, false));
				} else {
					d.set(c[0], 0, c[1], fence.fence());
				}
			}
			d.set(x0 + 1, 0, z0 + 1, Blocks.WATER_CAULDRON.defaultBlockState()
				.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_CAULDRON, 3));
			for (int z = z0 + 1; z < z1; z++) {
				for (int x = x0 + 1; x < x1; x++) {
					if (d.isAbsent(x, -1, z) || Garden.groundLike(d, x, -1, z)) {
						d.set(x, -1, z, Noise.unit(d.seed(), x, 0, z) < 0.5 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.PACKED_MUD.defaultBlockState());
					}
				}
			}
		}
		Spots.at(d, PoiType.JOB_SITE, gx, 0, z1 + 1, Dirs.BACK, JobType.HERDER);
		Spots.pen(d, gx, 0, (z0 + z1) / 2, Dirs.FRONT);
	}

	// ------------------------------------------------------------------------------------------------ shared

	/** Keeps the cell outside the house's front door (and the way to it) free of the yard's things. */
	private static void keepDoor(Draft d, FarmParts.House house) {
		if (house.door() == null) {
			return;
		}
		int[] r = house.body().rect(0);
		int x = house.door();
		for (int z = r[1] - 1; z >= Math.max(1, r[1] - 2); z--) {
			if (d.isAbsent(x, house.body().baseY, z)) {
				d.air(x, house.body().baseY, z);
				d.air(x, house.body().baseY + 1, z);
			}
		}
	}

	/**
	 * The working yard over a rectangle: its surface trodden and patched with paving, a dung heap and a hen house
	 * against a wall, a woodpile, barrels and a trough, a well on a big one, a lamp at the gate.
	 */
	private static void yard(Draft d, int w, int dp, int gateX, int x0, int z0, int x1, int z1) {
		if (x1 - x0 < 1 || z1 - z0 < 1) {
			return;
		}
		FarmParts.yardSurface(d, x0, z0, x1, z1, 0);
		FarmParts.yardThings(d, x0, z0 + 1, x1, z1, 0, (x1 - x0) * (z1 - z0) >= 40 && d.chance(0.5));
		// a hen house and a dung heap in the yard's back corners where free
		// (a coop at (x, z) facing the front stands on x - 1..x, z..z + 1, its ramp at z - 1)
		int[][] corners = {{x0 + 1, z1 - 1}, {x1, z1 - 1}, {x0 + 1, z0 + 2}, {x1, z0 + 2}};
		boolean coop = false;
		boolean dung = false;
		for (int[] c : corners) {
			boolean fits = Math.abs(c[0] - gateX) > 2;
			for (int[] cell : Sheds.coopCells(c[0], c[1], Dirs.FRONT)) {
				fits &= cell[0] >= x0 && cell[0] <= x1 && cell[1] >= z0 && cell[1] <= z1 && FarmParts.freeYard(d, cell[0], 0, cell[1]);
			}
			if (!coop && fits) {
				Sheds.coop(d, c[0], 0, c[1], Dirs.FRONT);
				coop = true;
				continue;
			}
			int hx = Math.min(c[0], x1 - 1);
			if (!dung && FarmParts.freeYard(d, hx, 0, c[1]) && FarmParts.freeYard(d, hx + 1, 0, c[1]) && Math.abs(hx - gateX) > 2) {
				Yard.dungHeap(d, hx, 0, c[1], Dirs.RIGHT);
				dung = true;
			}
		}
		FarmParts.gateLamp(d, gateX + (gateX + 1 < w - 1 ? 1 : -1), 0, 1);
		Spots.job(d, (x0 + x1) / 2, (z0 + z1) / 2, Dirs.FRONT, JobType.FARMER);
	}

	/** Before the house: a path from its door to the yard, flowers along its front, a bench. */
	private static void front(Draft d, FarmParts.House house, int hz0) {
		if (house.door() == null) {
			return;
		}
		int x = house.door();
		int[] r = house.body().rect(0);
		for (int xx = r[0]; xx <= r[2]; xx++) {
			int z = r[1] - 1;
			if (Math.abs(xx - x) >= 2 && FarmParts.freeYard(d, xx, 0, z) && Garden.groundLike(d, xx, -1, z) && 2 < d.maxHeight()
				&& Noise.unit(d.seed() ^ 0xF20, xx, 0, z) < 0.6) {
				Garden.garden(d, xx, z, xx, z, 0);
			}
		}
	}
}
