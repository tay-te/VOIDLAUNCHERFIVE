package dev.voidmc.hearth.build.archetype.house;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Chimneys;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Floors;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Porches;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.homestead.Spots;
import dev.voidmc.hearth.build.kit.homestead.Terraces;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import dev.voidmc.hearth.build.terrain.Site;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * {@code hearth:house/stilt_house}: a house raised on piles over the water (or over the wet ground of a marsh), entered
 * from the boardwalk across a deep gallery under its own roof. In the bayou: board-and-batten walls of willow or cypress
 * between mangrove posts under a roof of rusted tin, moss hanging from the eaves, rocking chairs on the gallery, a
 * stovepipe chimney. In the tropics: palm-wood and bamboo under a deep hipped thatch with latticed windows. By the sea:
 * a fisher's house of red boards or whitewash under slate, lobster pots on the landing. A boardwalk runs down one side
 * from the gallery to a landing stage behind the house, with a mooring post where a punt ties up (the dock); piles are
 * cross-braced where they stand tall. On dry ground the same house stands on posts with steps up to the gallery.
 *
 * <p>Params (all optional): {@code roof} (a RoofForm name), {@code side_walk} (boolean: the boardwalk down the side).
 */
public final class StiltHouse implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("house/stilt_house"), ArchetypeCategory.HOUSING, Set.of(), 2,
		7, 9, 7, 9, 9, 13, SettlementTier.CAMP, Set.of(Culture.BAYOU, Culture.TROPICAL, Culture.MARITIME), Set.of(ArchetypeInfo.SiteFlag.ON_WATER),
		List.of(), 0, 1.5);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Habits h = d.habits();
		// the side boardwalk takes a column where the plot is wide enough; the gallery the front two rows
		boolean walk = d.param("side_walk", w >= 8);
		boolean walkLeft = d.chance(0.5);
		int bx0 = walk && walkLeft ? 1 : 0;
		int bx1 = walk && !walkLeft ? w - 2 : w - 1;
		int bz1 = dp - 1 - (dp >= 8 ? 1 : 0);
		// the floor: on the boardwalk's level, raised over the water and a little over wet ground
		int base = floor(d, bx0, 0, bx1, bz1);
		// where the floor stands proud of the street, a row in front is left for the steps up to the gallery
		int front0 = base - Terraces.street(d, (bx0 + bx1) / 2) > 1 ? 1 : 0;
		int gallery = dp - front0 >= 8 ? 2 : 1;
		int bz0 = front0 + gallery;
		int sh = Math.max(4, h.walls().storeyHeight());
		RoofForm form = HouseBuilder.roofParam(d, h.roof().pick(d.random(), RoofForm.GABLE, RoofForm.HIP, RoofForm.HALF_HIP, RoofForm.SHED, RoofForm.PAGODA));
		Body body = new Body(bx0, bz0, bx1, bz1, base, new int[]{sh}, false, false);
		d.body(bx0, bz0, bx1, bz1);
		Facade facade = Facade.plan(d, body, (bx0 + bx1) / 2, 1, 1, 0.6);
		Integer doorCol = HouseBuilder.doorColumn(facade);
		int door = doorCol != null ? doorCol : (bx0 + bx1) / 2;
		d.eave(body.plateY());

		// piles under the house and the gallery, the floors over them
		Groundwork.stilts(d, bx0, front0, bx1, bz1, base - 1, 2);
		Floors.boards(d, bx0, front0, bx1, bz1, base - 1);
		HouseBuilder.shell(d, body, facade);
		HouseBuilder.openings(d, body, facade, false);
		// the gallery across the front under a roof of its own, open where the boardwalk comes in
		Side front = body.side(0, Dirs.FRONT);
		Porches.porch(d, front, bx0, bx1, gallery, base, 3, door);
		gallery(d, front, bx0, bx1, base, door, front0);
		// the roof
		Direction.Axis ridge = bx1 - bx0 >= bz1 - bz0 ? Direction.Axis.X : Direction.Axis.Z;
		Roofs.Opts o = Roofs.Opts.of(d);
		Roofs.Roof roof;
		if (form == RoofForm.SHED) {
			roof = Roofs.build(d, bx0, bz0, bx1, bz1, body.plateY(), RoofForm.SHED, Direction.Axis.X, o.withEaves(1, 1), false, false).roof();
		} else {
			roof = Roofs.build(d, bx0, bz0, bx1, bz1, body.plateY(), form, ridge, o, false, false).roof();
		}
		// a stove against the back wall with its pipe through the roof (where the culture keeps a fire)
		int[] stove = null;
		if (d.chance(Math.max(0.3, h.chimney())) && bx1 - bx0 >= 4) {
			int sx = walkLeft ? bx1 - 1 : bx0 + 1;
			int sz = bz1 - 1;
			Furniture.hearth(d, sx, base, sz, sx == bx0 + 1 ? Dirs.RIGHT : Dirs.LEFT, false);
			Chimneys.stack(d, sx, sz, base + 2, roof, roof.ridgeY + 1);
			stove = new int[]{sx, sz};
		}
		interior(d, body, door, stove);
		// the boardwalk down the side to the landing stage behind, the mooring
		if (walk) {
			sideWalk(d, walkLeft ? 0 : w - 1, base, bz1, dp);
		} else {
			landing(d, bx0, bx1, base, bz1, dp);
		}
		// the way from the street onto the gallery
		street(d, door, base, front0);
		moss(d, roof, body, base);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, roof.ridgeY + 3));
	}

	/** The floor level: the street's, at least a block over the water and two over the ground under the house. */
	private static int floor(Draft d, int x0, int z0, int x1, int z1) {
		int street = Terraces.street(d, (x0 + x1) / 2);
		int base = Math.max(0, street);
		// the piles lift the floor clear of the water (its stilt height), never more than two over the street
		int lift = Math.max(1, d.habits().plan().stiltHeight());
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				int wy = d.site().water(x, z);
				int g = d.site().ground(x, z);
				base = Math.max(base, wy == Site.NONE ? g + 1 : Math.min(wy + lift, Math.max(street, 0) + 2));
			}
		}
		// on dry ground it stands a little proud on its posts
		if (!d.site().anyWater(x0, z0, x1, z1)) {
			base = Math.max(base, d.site().meanGround(x0, z0, x1, z1) + 2);
		}
		// never more than two steps up from the street: the stair down has to fit along the front
		return Math.min(Math.min(base, Math.max(street, 0) + 2), 4);
	}

	/** The gallery's life: rocking chairs either side of the door, pots, a lantern, a barrel. */
	private static void gallery(Draft d, Side front, int x0, int x1, int base, int door, int front0) {
		int back = front.z(0) - 1;
		for (int x : new int[]{door - 2, door + 2}) {
			if (x > x0 && x < x1 && Spots.standable(d, x, base, back) && d.isAbsent(x, base, back)) {
				Furniture.chair(d, x, base, back, Dirs.BACK);
			}
		}
		int bx = door <= (x0 + x1) / 2 ? x1 - 1 : x0 + 1;
		if (d.isAbsent(bx, base, front0) && Spots.standable(d, bx, base, front0) && back > front0) {
			d.inStage(StageKind.EXTERIOR, () -> d.set(bx, base, front0, States.facing(Blocks.BARREL, Direction.UP)));
		}
		int lx = door + (door + 1 < x1 ? 1 : -1);
		if (d.isAbsent(lx, base + 2, back) && !d.isEmpty(lx, base + 3, back)) {
			Furniture.hangingLantern(d, lx, base + 3, back, 0);
		}
	}

	/** Two rooms: a kitchen and living room by the door, a bedroom behind; or one room on a small plot. */
	private static void interior(Draft d, Body b, int door, int[] stove) {
		int ix0 = b.x0 + 1;
		int ix1 = b.x1 - 1;
		int iz0 = b.z0 + 1;
		int iz1 = b.z1 - 1;
		Rooms.Room room = new Rooms.Room(ix0, iz0, ix1, iz1, b.baseY, b.height(0) - 1);
		room.keep(door, iz0);
		if (stove != null) {
			room.block(stove[0], stove[1]);
			room.keep(stove[0] + (stove[0] == ix0 ? 1 : -1), stove[1]);
		}
		Rooms.furnish(d, room, Rooms.Type.BEDROOM, (ix1 - ix0 + 1) * (iz1 - iz0 + 1) >= 15 ? 2 : 1, JobType.NONE);
		Rooms.furnish(d, room, Rooms.Type.KITCHEN, 0, JobType.NONE);
	}

	/**
	 * A boardwalk of planks on posts down the plot's side column from the gallery to the back, a landing stage at its
	 * end with a mooring post (the dock) and, by the sea, lobster pots.
	 */
	private static void sideWalk(Draft d, int x, int base, int bz1, int dp) {
		Mat floor = d.mat(Role.FLOOR);
		Mat stilt = d.mat(Role.STILT);
		Mat rail = d.mat(Role.RAILING);
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int z = 0; z < dp; z++) {
				int g = d.site().ground(x, z);
				if (z % 2 == 0 || z == dp - 1) {
					for (int y = g - 2; y < base - 1; y++) {
						d.set(x, y, z, stilt.log(Direction.Axis.Y));
					}
				} else if (g < base - 1) {
					// the walk's planks rest on a joist
					d.set(x, base - 2 >= g ? base - 2 : base - 1, z, d.mat(Role.BEAM).log(Direction.Axis.Z));
				}
				d.set(x, base - 1, z, floor.full());
			}
		}
		// the mooring at the end: a post with a lantern and a rope coil (a chain), the dock beside it
		int ez = dp - 1;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, base, ez, rail.fence());
			d.set(x, base + 1, ez, States.lantern(d.mat(Role.LIGHT).mainOr(Form.FULL, Blocks.LANTERN), false));
		}
		boolean water = d.site().isWater(x, ez) || d.site().isWater(x, ez - 1);
		d.poi(water ? PoiType.DOCK : PoiType.VIEWPOINT, x, base, ez - 1, Dirs.BACK);
		if (d.habits().yard().fence() == Habits.FenceKind.WALL && d.isAbsent(x, base, ez - 2)) {
			// lobster pots stacked by the post
			d.inStage(StageKind.EXTERIOR, () -> d.set(x, base, ez - 2, States.facing(Blocks.BARREL, Dirs.LEFT)));
		}
	}

	/** Without a side walk, a little landing stage off the back of the house with a mooring post. */
	private static void landing(Draft d, int x0, int x1, int base, int bz1, int dp) {
		if (bz1 + 1 >= dp) {
			return;
		}
		int z = bz1 + 1;
		int x = (x0 + x1) / 2;
		Mat floor = d.mat(Role.FLOOR);
		Mat stilt = d.mat(Role.STILT);
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int xx = x - 1; xx <= x + 1; xx++) {
				int g = d.site().ground(xx, z);
				for (int y = g - 2; y < base - 1; y++) {
					if (xx != x) {
						d.set(xx, y, z, stilt.log(Direction.Axis.Y));
					}
				}
				d.set(xx, base - 1, z, floor.full());
			}
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x + 1, base, z, d.mat(Role.RAILING).fence());
		}
		// a back door onto it
		d.inStage(StageKind.OPENINGS, () -> d.door(x, base, bz1, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), Dirs.BACK,
			net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT));
		boolean water = d.site().isWater(x, z);
		d.poi(water ? PoiType.DOCK : PoiType.VIEWPOINT, x, base, z, Dirs.BACK);
	}

	/** Steps from the street up onto the gallery where it stands proud of the boardwalk or the ground. */
	private static void street(Draft d, int door, int base, int front0) {
		int s = Terraces.street(d, door);
		if (base - s >= 1 || d.site().ground(door, 0) < base - 1) {
			Groundwork.approach(d, door, base, front0);
		}
	}

	/** Moss hanging from the eaves in the bayou (Spanish moss with Expanse, pale hanging moss without). */
	private static void moss(Draft d, Roofs.Roof roof, Body b, int base) {
		Block moss = d.mat(Role.ACCENT).main(Form.FULL) == Blocks.MOSS_BLOCK
			? States.block("expanse:spanish_moss", States.block("minecraft:pale_hanging_moss", Blocks.VINE)) : null;
		if (moss == null) {
			return;
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = roof.z0; z <= roof.z1; z++) {
				for (int x = roof.x0; x <= roof.x1; x++) {
					boolean outside = x < b.x0 || x > b.x1 || z < b.z0 || z > b.z1;
					if (!outside || z < b.z0 || !roof.covers(x, z) || Noise.unit(d.seed() ^ 0x3055, x, 0, z) > 0.45) {
						continue;
					}
					int y = roof.bottomY(x, z) - 1;
					if (y <= base + 1 || !d.isAbsent(x, y, z) || !d.isAbsent(x, y - 1, z)) {
						continue;
					}
					boolean tip = moss.defaultBlockState().hasProperty(BlockStateProperties.TIP);
					int len = Noise.unit(d.seed() ^ 0x3056, x, 0, z) < 0.5 ? 2 : 1;
					for (int k = 0; k < len; k++) {
						var st = moss.defaultBlockState();
						if (tip) {
							st = st.setValue(BlockStateProperties.TIP, k == len - 1);
						}
						d.set(x, y - k, z, st);
					}
				}
			}
		}
	}
}
