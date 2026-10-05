package dev.voidmc.hearth.build.archetype.law;

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
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Floors;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.civic.Signs;
import dev.voidmc.hearth.build.kit.civic.Storeys;
import dev.voidmc.hearth.build.kit.civic.Towers;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.landmark.Details;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.kit.landscape.Manner;
import dev.voidmc.hearth.build.kit.lawhouse.Fenestration;
import dev.voidmc.hearth.build.kit.lawhouse.Masonry;
import dev.voidmc.hearth.build.kit.lawhouse.Timber;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * {@code hearth:law/toll_gate}: the toll house over a road into town, where traffic coming in pays. Two masonry piers
 * stand either side of the road; over the road between them the toll keeper's room in the town's walls (timber framing,
 * logs, render, adobe), its gable turned to the road both ways (a flat roof behind battlements in the south, a curved
 * roof in the karst), a window over the road each way to watch it. The road passes under it through arched mouths
 * (timber-lintelled in the timber traditions), lanterns hung in the passage, the setts kerbed. In the left pier the
 * toll booth: the keeper's counter at a hatch on the road, his strongbox, a door at the back; in the right pier the
 * ladder up to his room. Across the road's mouth on the field side the barrier: a boom banded red and white,
 * pivoted on the booth's pier at head height, with its counterweight. A sign of the toll on the pier.
 *
 * <p>POIs: GATE in the passage, JOB_SITE (GUARD: the toll keeper) at the counter, GUARD_POST at the barrier, BED, STORAGE,
 * DOOR.
 *
 * <p>Params: {@code over_road} (boolean), {@code passage_width} (int, the road's width: 3 or 5; default by frontage),
 * {@code street_y}.
 */
public final class TollGate implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("law/toll_gate"), ArchetypeCategory.LAW, Set.of(JobType.GUARD), 1,
		11, 13, 6, 8, 14, 17, SettlementTier.TOWN, Set.of(), Set.of(), List.of(), 2, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int maxH = d.maxHeight();
		Tradition t = Tradition.of(d);
		int c = (w - 1) / 2;
		int pw = Math.clamp(d.param("passage_width", w >= 12 ? 5 : 3), 3, Math.max(3, w - 6));
		if ((pw & 1) == 0) {
			pw--;
		}
		int px0 = c - pw / 2;
		int px1 = px0 + pw - 1;
		int lx1 = px0 - 1;
		int rx0 = px1 + 1;
		int clear = 4;
		Stone dress = Stone.dressing(d);
		Stone wall = Masonry.walling(d);
		Stone plinth = Masonry.plinth(d);
		Stone pier = Stone.stony(d, Role.PLINTH) ? Stone.of(d, Role.PLINTH) : Stone.masonry(d);
		Timber tim = Timber.frame(d);
		boolean timber = !Stone.masonryWalls(d);

		// the road outside is the way in; the passage is level with it
		d.entrance(c, 0, -1);
		d.poi(PoiType.GATE, c, 0, dp / 2, Dirs.FRONT);
		Groundwork.level(d, px0, 0, px1, dp - 1, 0, true);
		pave(d, px0, px1, dp, dress);
		Groundwork.footings(d, 0, 0, lx1, dp - 1, -1);
		Groundwork.footings(d, rx0, 0, w - 1, dp - 1, -1);

		// --- the piers: masonry, quoined, a plinth course
		Body left = new Body(0, 0, lx1, dp - 1, 0, new int[]{clear + 1}, false, false);
		Body right = new Body(rx0, 0, w - 1, dp - 1, 0, new int[]{clear + 1}, false, false);
		Masonry.storey(d, left, 0, pier, dress, plinth, true);
		Masonry.storey(d, right, 0, pier, dress, plinth, true);
		// --- the toll keeper's room over the road
		int fy = clear + 1;
		Body room = new Body(0, 0, w - 1, dp - 1, fy, new int[]{4}, false, false);
		d.body(0, 0, w - 1, dp - 1);
		Facade fac = Facade.plan(d, room, -1, 1, 1, 0.6);
		Floors.boards(d, 0, 0, w - 1, dp - 1, fy - 1);
		Storeys.walls(d, room, fac, 0, 0, null, false);
		int plate = room.plateY();
		d.eave(plate);
		// the passage's vault (its ceiling is the room's floor) and its mouths
		try (var st = d.stage(StageKind.WALLS)) {
			for (int z = 0; z < dp; z++) {
				for (int x = px0; x <= px1; x++) {
					for (int y = 0; y < clear; y++) {
						d.set(x, y, z, Blocks.AIR.defaultBlockState());
					}
					d.set(x, clear, z, timber ? tim.beam(Direction.Axis.Z).at(x, clear, z) : pier.full().at(x, clear, z));
				}
			}
			for (int z : new int[]{0, dp - 1}) {
				for (int x = px0; x <= px1; x++) {
					d.set(x, clear, z, timber ? tim.beam(Direction.Axis.X) : dress.full());
				}
				if (!timber) {
					// round shoulders: the arch springs a block below the vault
					d.set(px0, clear - 1, z, dress.stairsOrSlab(Dirs.RIGHT, net.minecraft.world.level.block.state.properties.Half.TOP));
					d.set(px1, clear - 1, z, dress.stairsOrSlab(Dirs.LEFT, net.minecraft.world.level.block.state.properties.Half.TOP));
				} else {
					d.set(px0, clear - 1, z, tim.stairs(Dirs.RIGHT, net.minecraft.world.level.block.state.properties.Half.TOP));
					d.set(px1, clear - 1, z, tim.stairs(Dirs.LEFT, net.minecraft.world.level.block.state.properties.Half.TOP));
				}
			}
		}
		// windows over the road both ways, small ones in the flanks
		for (Side s : room.sides(0)) {
			if (s.out() == Dirs.FRONT || s.out() == Dirs.BACK) {
				Openings.window(d, s, c, fy + 1, 1, 2, Openings.WindowStyle.DRESSED);
				for (int off : new int[]{-2, 2}) {
					int x = c + off;
					if (s.index(x) >= 1 && s.index(x) <= s.last() - 1) {
						Openings.window(d, s, x, fy + 1, 1, 2, Openings.WindowStyle.DRESSED);
					}
				}
			} else {
				Openings.window(d, s, s.column(s.middle()), fy + 1, 1, 1, Openings.WindowStyle.DRESSED);
			}
		}
		// --- the roof: gables to the road both ways, or the culture's form
		Storeys.Top top;
		if (t == Tradition.ADOBE) {
			top = Storeys.roof(d, room, RoofForm.FLAT, Direction.Axis.X, 1.0, 1);
			Towers.battlements(d, 0, 0, w - 1, dp - 1, top.deck());
		} else {
			RoofForm form = t == Tradition.KARST ? RoofForm.PAGODA : t == Tradition.MEDITERRANEAN || t == Tradition.STILT ? RoofForm.HIP : RoofForm.GABLE;
			top = Storeys.roof(d, room, form, Direction.Axis.Z, Math.max(1.0, d.habits().roof().pitch()), 1);
		}
		int skyline = top.ridge();

		// --- the booth in the left pier: the counter at a hatch on the road, the keeper's chest, a door at the back
		int bx = lx1 - 1;
		Side boothRoad = Side.of(0, 0, lx1, dp - 1, Dirs.RIGHT);
		int hz = 1;
		try (var st = d.stage(StageKind.OPENINGS)) {
			d.set(lx1, 1, hz, Blocks.AIR.defaultBlockState());
		}
		try (var st = d.stage(StageKind.INTERIOR)) {
			d.set(lx1, 0, hz, d.mat(Role.FURNITURE).slab(net.minecraft.world.level.block.state.properties.SlabType.TOP));
			d.set(lx1, 2, hz, tim.trapdoor(Dirs.RIGHT, net.minecraft.world.level.block.state.properties.Half.TOP, true));
		}
		d.poi(PoiType.JOB_SITE, bx, 0, hz, Dirs.RIGHT, JobType.GUARD);
		Openings.door(d, boothRoad, dp - 2, 0, 1, new Openings.DoorOpts(true, false, false));
		Rooms.Room booth = new Rooms.Room(1, 1, lx1 - 1, dp - 2, 0, clear);
		booth.keep(bx, hz);
		booth.keep(bx, dp - 2);
		if (booth.claim(1, dp - 3) || booth.claim(bx, dp - 3)) {
			int chx = booth.inside(1, dp - 3) && !(1 == bx) ? 1 : bx;
			Furniture.chest(d, chx, 0, dp - 3, chx == bx ? Dirs.BACK : Dirs.RIGHT, List.of(new Object[]{"minecraft:gold_nugget", 18},
				new Object[]{"minecraft:emerald", 1}));
		}
		Lighting.room(d, booth);
		// --- the right pier: a door from the passage, the ladder up to the room
		int rx = rx0 + 1;
		Side pierRoad = Side.of(rx0, 0, w - 1, dp - 1, Dirs.LEFT);
		Openings.door(d, pierRoad, dp - 2, 0, 1, new Openings.DoorOpts(false, false, false));
		Stairs.ladder(d, rx, 1, 0, fy - 1, Dirs.BACK);
		Lighting.room(d, rx0 + 1, 1, w - 2, dp - 2, 0);
		// --- the keeper's room
		Rooms.Room keeper = new Rooms.Room(1, 1, w - 2, dp - 2, fy, 3);
		keeper.keep(rx, 1);
		keeper.keep(rx, 2);
		Rooms.furnish(d, keeper, Rooms.Type.BEDROOM, 1, JobType.NONE);
		Lighting.room(d, keeper);

		// --- the barrier across the road's mouth on the field side: a striped boom at head height, its counterweight
		boom(d, px0, px1, 2, 0, lx1);
		d.poi(PoiType.GUARD_POST, px0, 0, 0, Dirs.FRONT);
		// lanterns in the passage, the sign of the toll on the pier, a lamp at the booth's door
		Lighting.chandelier(d, c, 1, clear, clear - 1);
		Lighting.chandelier(d, c, dp - 2, clear, clear - 1);
		int sz = -1;
		if (d.isEmpty(lx1 - 1, 3, sz)) {
			Signs.hangingSign(d, lx1 - 1, 3, sz, Dirs.LEFT, List.of(new String[]{"", ""}, new String[]{"hearth.build.law.toll", "Toll"}, new String[]{"", ""}));
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(maxH - 1, skyline + 2));
	}

	/** Setts down the passage, kerbed along each side in the dressing. */
	static void pave(Draft d, int px0, int px1, int dp, Stone dress) {
		var paving = Manner.wood(d.mat(Role.PAVING)) ? Manner.masonry(d) : d.mat(Role.PAVING);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 0; z < dp; z++) {
				for (int x = px0; x <= px1; x++) {
					d.set(x, -1, z, x == px0 || x == px1 ? dress.full() : paving.full());
				}
			}
		}
	}

	/**
	 * The barrier: a boom across the road's mouth at row z, its pole at height y painted in bands as booms are, red and
	 * white (black and white where the town's cloth is black and not red, or now and then by seed), its counterweight a
	 * block of the masonry on the pier's side.
	 */
	static void boom(Draft d, int px0, int px1, int y, int z, int pierX) {
		List<String> colours = Signs.clothColours(d);
		boolean black = colours.contains("black") && !colours.contains("red") || !colours.contains("red") && !colours.contains("black") && d.chance(0.3);
		Paint pa = Paint.of(Blocks.WOOL.pick(black ? net.minecraft.world.item.DyeColor.BLACK : net.minecraft.world.item.DyeColor.RED).defaultBlockState());
		Paint pb = Paint.of(Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState());
		try (var st = d.stage(StageKind.EXTERIOR)) {
			for (int x = px0; x <= px1; x++) {
				d.set(x, y, z, ((x - px0) & 1) == 0 ? pa : pb);
			}
		}
	}
}
