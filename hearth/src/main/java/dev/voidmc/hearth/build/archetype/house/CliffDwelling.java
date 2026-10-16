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
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.townhome.Fronts;
import dev.voidmc.hearth.build.kit.townhome.Traits;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:house/cliff_dwelling}: rooms set back into a slope or a cliff behind a terrace, built so the next
 * dwelling can stand on their roof: the front of the plot is a walled forecourt (or, when the dwelling is raised on the
 * roof of the one below, a terrace with a parapet), the back holds the rooms under a flat roof deck at the top of the
 * plot's height, reached from the forecourt by a stair along the house front (or the pueblo's ladder, its poles standing
 * up past the parapet). Stacked three high up a canyon wall they make a pueblo; in the karst the rooms stand on a
 * limestone sill behind a bamboo gallery under a curved pent roof; in the mountains a stone house is dug into the slope
 * under a deep-eaved roof. Inside, one or two rooms: beds, a hearth in the corner, stores.
 *
 * <p>Params:
 * <ul>
 *   <li>{@code stacked} (boolean): raised on the roof of the dwelling below ({@code stacked_on}); the front half is a
 *   terrace on that roof, nothing is founded down through it</li>
 *   <li>{@code open_left}, {@code open_right} (boolean; the dwelling's own sides, looking out of its door, as party
 *   walls are named): the terrace runs on that way without a parapet, into the next dwelling's terrace along a cliff
 *   town's face or onto the landing of a stair street beside it</li>
 *   <li>{@code set} (int, with {@code stacked}): how far it stands behind the dwelling below (default half its depth;
 *   a cliff town's face sets each course back by the depth of the rooms, so they stand wholly on the rock behind)</li>
 *   <li>{@code variant} (int)</li>
 * </ul>
 * The dwelling reads the slope itself ({@code cut_into_slope} and {@code level} are not needed): walls where the ground
 * stands over them are left blind, the forecourt is cut and retained to the floor.
 * The roof deck's standing level is the plot's height (its deck block is the top course but one, under a one-course
 * parapet), so a dwelling stacked on this one (its floor at {@code floorY + maxHeight}) finds its terrace on the deck.
 */
public final class CliffDwelling implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("house/cliff_dwelling"), ArchetypeCategory.HOUSING, Set.of(), 2,
		6, 10, 6, 9, 5, 6, SettlementTier.CAMP, Set.of(Culture.MESA, Culture.KARST, Culture.ALPINE),
		Set.of(ArchetypeInfo.SiteFlag.HILLSIDE, ArchetypeInfo.SiteFlag.STACKABLE), List.of(), 0, 1.5);

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
		int maxH = d.maxHeight();
		Habits h = d.habits();
		Traits t = Traits.of(d);
		boolean stacked = d.param("stacked", false);
		boolean pueblo = t.pueblo() || t.walls() == WallKind.ADOBE;
		boolean eastern = t.eastern();

		// the rooms at the back, the forecourt (or terrace) in front
		int zb0 = Math.max(2, Math.min(dp / 2 - 1, dp - 4));
		int bz1 = dp - 1;
		int plate = maxH - 2;
		int[] heights = {plate + 1};
		Body body = new Body(0, zb0, w - 1, bz1, 0, heights, false, false);
		d.body(0, zb0, w - 1, bz1);
		int door = d.chance(0.5) ? Math.max(1, w / 2 - 1) : Math.min(w - 2, w / 2 + 1);

		// ground: the forecourt levelled (or the terrace deck on the roof below), the rooms cut into the slope
		Facade facade = Facade.plan(d, body, door, 1, 1, 0.5);
		Integer placed = HouseBuilder.doorColumn(facade);
		if (placed != null) {
			door = placed;
		}
		d.eave(body.plateY());
		if (stacked) {
			// on the roof below: footings only behind it, a course under the walls where it stands on the deck
			terraceDeck(d, zb0);
			int set = d.param("set", Math.max(2, dp / 2));
			int onDeck = dp - 1 - set;
			Mat found = d.mat(Role.FOUNDATION);
			try (var st = d.stage(StageKind.FOUNDATION)) {
				for (int z = zb0; z <= Math.min(onDeck, bz1); z++) {
					for (int x = 0; x < w; x++) {
						if (x == 0 || x == w - 1 || z == zb0) {
							d.set(x, -1, z, found.full());
						}
					}
				}
			}
			if (onDeck < bz1) {
				Groundwork.footings(d, 0, Math.max(zb0, onDeck + 1), w - 1, bz1, -1);
			}
		} else {
			Groundwork.footings(d, 0, zb0, w - 1, bz1, -1);
			forecourt(d, zb0, door, pueblo);
		}
		// the mountain tradition digs a rubble-stone house into the slope (its timber shows in the gallery and rail)
		boolean mountain = !pueblo && !eastern && (h.walls().upper() != null || h.roof().eaves() >= 2);
		TownHouses.shell(d, body, facade, mountain);
		openings(d, body, facade, t, door);
		if (eastern) {
			sill(d, body);
		}

		// roof: always a deck at the top of the height with its parapet, the terrace of the next dwelling up (a pitched
		// roof would leave it standing on a ridge); eaves round it in the karst and the mountains
		int[] tr = body.topRect();
		int deck = Roofs.flat(d, tr[0], tr[1], tr[2], tr[3], body.plateY(), true);
		if (eastern || mountain) {
			eaves(d, body);
		}
		{
			// the parapet round the deck is one course under the plot's height, no room for a coping over it: a solid course
			// would be a ledge one steps up onto from the deck and walks round over the drop. In the mountains (and wherever
			// the wall has no wall-top form) a timber rail stands round the deck instead; else the course is laid as a wall's
			// rounded top, which nobody stands on
			Mat rail = d.mat(Role.RAILING);
			Mat wall = d.mat(Role.WALL);
			boolean masonry = !mountain && wall.has(Form.WALL);
			try (var st = d.stage(masonry ? StageKind.ROOF : StageKind.EXTERIOR)) {
				for (Side side : Side.of(tr[0], tr[1], tr[2], tr[3])) {
					for (int i = 0; i < side.length(); i++) {
						if (d.get(side.x(i), deck, side.z(i)) != null && !d.isEmpty(side.x(i), deck, side.z(i))) {
							d.set(side.x(i), deck, side.z(i), masonry ? wall.wall() : rail.fence());
						}
					}
				}
			}
		}

		// the way up onto the roof deck (the terrace of the dwelling stacked above)
		if (pueblo) {
			ladder(d, body, door, deck);
		} else if (frontStair(d, body, door, deck) == Integer.MIN_VALUE) {
			ladder(d, body, door, deck);
		}
		roofLife(d, body, deck, pueblo);

		// inside: a corner hearth, beds, stores
		interior(d, body, null, door, pueblo, eastern);

		// the forecourt's furniture: the oven, pots, drying racks, a bench
		yardLife(d, zb0, door, pueblo, stacked);
		if (!stacked) {
			if (zb0 >= 1) {
				TownHouses.street(d, body, door, TownHouses.misfit(d, door) > 0, 0);
			}
		}
		if (h.snow()) {
			Garden.snowBanks(d, 0, zb0, w - 1, bz1, 0, door);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(maxH - 1, deck + 4));
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	/**
	 * The forecourt: the ground in front of the rooms levelled to the floor (a retaining wall where it is cut deep), a
	 * low wall round it with a gap at the gate, a paved path to the door.
	 */
	private static void forecourt(Draft d, int zb0, int door, boolean pueblo) {
		int w = d.frontage();
		if (d.site().anyWater(0, 0, w - 1, zb0 - 1)) {
			return;
		}
		Groundwork.level(d, 0, 0, w - 1, zb0 - 1, 0);
		Mat wall = d.mat(Role.WALL);
		Mat coping = d.mat(Role.WALL_ACCENT);
		Mat path = d.mat(Role.PAVING);
		int gate = door;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int x = 0; x < w; x++) {
				for (int z = 0; z < zb0; z++) {
					boolean edge = z == 0 || x == 0 || x == w - 1;
					if (!edge || x == gate && z == 0) {
						continue;
					}
					if (d.site().ground(x, z) > 1) {
						continue;
					}
					d.set(x, 0, z, wall.full());
					d.set(x, 1, z, coping.has(Form.SLAB) ? coping.slab(SlabType.BOTTOM) : wall.slab(SlabType.BOTTOM));
				}
			}
			for (int z = 0; z < zb0; z++) {
				if (d.isAbsent(gate, 0, z)) {
					d.set(gate, -1, z, path.full());
				}
			}
		}
	}

	/** The terrace of a stacked dwelling: a deck over the front rows (on the roof below) with a parapet, a gap at the gate. */
	private static void terraceDeck(Draft d, int zb0) {
		int w = d.frontage();
		Mat floor = d.mat(Role.ROOF);
		Mat wall = d.mat(Role.WALL);
		Mat coping = d.mat(Role.WALL_ACCENT);
		int gate = w / 2;
		// the sides the terrace runs on through (the viewer's left is the dwelling's own right)
		boolean openX0 = d.param("open_right", false);
		boolean openX1 = d.param("open_left", false);
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = 0; z < zb0; z++) {
				for (int x = 0; x < w; x++) {
					d.set(x, -1, z, floor.full());
				}
			}
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 0; z < zb0; z++) {
				for (int x = 0; x < w; x++) {
					boolean edge = x == 0 && !openX0 || x == w - 1 && !openX1;
					if (edge) {
						d.set(x, 0, z, coping.has(Form.SLAB) ? coping.slab(SlabType.BOTTOM) : wall.slab(SlabType.BOTTOM));
					}
				}
			}
		}
		// the gate in the front edge, where the way up from below arrives
		d.entrance(gate, 0, -1);
	}

	/** Few, small openings: the door, deep windows in the front, slits or nothing at the back where it is dug in. */
	private static void openings(Draft d, Body b, Facade f, Traits t, int door) {
		Habits.Windows hw = d.habits().windows();
		for (int k = 0; k < b.storeys(); k++) {
			if (b.height(k) < 3) {
				continue;
			}
			for (Side s : b.sides(k)) {
				for (Facade.Opening o : f.openings(s.out())) {
					if (!s.contains(o.at())) {
						continue;
					}
					int y = b.floorY(k);
					int i = s.index(o.at());
					int gx = s.outX(i, 1);
					int gz = s.outZ(i, 1);
					if (d.site().ground(gx, gz) > y + 1 && s.out() != Dirs.FRONT) {
						continue;
					}
					if (o.kind() == Facade.Kind.DOOR && k == 0) {
						Openings.door(d, s, o.at(), y, 1, new Openings.DoorOpts(true, false, true));
					} else if (o.kind() == Facade.Kind.WINDOW) {
						Openings.WindowStyle style = hw.lattice() ? Openings.WindowStyle.LATTICE : t.pueblo() ? Openings.WindowStyle.PLAIN
							: Openings.WindowStyle.DRESSED;
						Openings.window(d, s, o.at(), y + 1, 1, 1, style);
					}
				}
			}
		}
	}

	/** The karst house's limestone sill: the plinth carried a block out in front on corbels, a ledge to stand the walls on. */
	private static void sill(Draft d, Body b) {
		Side s = b.side(0, Dirs.FRONT);
		Mat stone = d.mat(Role.PLINTH);
		try (var st = d.stage(StageKind.FOUNDATION)) {
			for (int i = 0; i <= s.last(); i++) {
				int x = s.outX(i, 1);
				int z = s.outZ(i, 1);
				if (!Fronts.allowed(d, x, 0, z) || !d.isAbsent(x, 0, z) || d.site().ground(x, z) > 0) {
					continue;
				}
				d.set(x, -1, z, stone.full());
			}
		}
	}

	/** The karst gallery: a curved pent roof of the roof's tiles along the front over the door, on posts. */
	private static void eaves(Draft d, Body b) {
		Side s = b.side(0, Dirs.FRONT);
		int y = b.floorY(0) + 3;
		Fronts.pent(d, s, 0, s.last(), y, 2);
		Mat post = d.mat(Role.FRAME);
		try (var st = d.stage(StageKind.FRAME)) {
			for (int i : new int[]{0, s.last()}) {
				int x = s.outX(i, 2);
				int z = s.outZ(i, 2);
				if (!Fronts.allowed(d, x, 0, z) || !d.isAbsent(x, 0, z)) {
					continue;
				}
				for (int yy = 0; yy < y; yy++) {
					d.set(x, yy, z, post.log(Direction.Axis.Y));
				}
			}
		}
		// lanterns under the gallery
		for (int i = 1; i < s.last(); i += 3) {
			int x = s.outX(i, 1);
			int z = s.outZ(i, 1);
			if (d.get(x, y + 1, z) != null && !d.isEmpty(x, y + 1, z) && d.isAbsent(x, y, z)) {
				d.inStage(StageKind.EXTERIOR, () -> d.set(x, y, z, States.lantern(Blocks.LANTERN, true)));
			}
		}
	}

	/**
	 * A stair along the house front from the forecourt up to the roof deck, flying over the door (it rises past head
	 * height before it reaches it); arrives at a gap in the parapet. Returns the x it arrives at, or MIN_VALUE if it
	 * does not fit (the front is too narrow for the rise).
	 */
	private static int frontStair(Draft d, Body b, int door, int deck) {
		int[] r = b.rect(0);
		int z = r[1] - 1;
		int rise = deck;
		if (z < 0 || rise > r[2] - r[0] - 1) {
			return Integer.MIN_VALUE;
		}
		// from the side nearer the door, so the flight is high by the time it passes over the door
		boolean fromLeft = door - r[0] <= r[2] - door;
		int x0 = fromLeft ? r[0] + 1 : r[2] - 1;
		int doorStep = Math.abs(door - x0);
		if (doorStep < 3 && doorStep >= 0 && doorStep <= rise) {
			// the door would stand under a low step: start from the far end instead
			fromLeft = !fromLeft;
			x0 = fromLeft ? r[0] + 1 : r[2] - 1;
			doorStep = Math.abs(door - x0);
			if (doorStep < 3 && doorStep <= rise) {
				return Integer.MIN_VALUE;
			}
		}
		Direction dir = fromLeft ? Dirs.RIGHT : Dirs.LEFT;
		for (int i = 0; i < rise; i++) {
			int x = x0 + Dirs.dx(dir) * i;
			if (!d.isAbsent(x, i, z) && !d.isEmpty(x, i, z)) {
				return Integer.MIN_VALUE;
			}
		}
		Stairs.straight(d, x0, 0, z, dir, rise, 1);
		int topX = x0 + Dirs.dx(dir) * (rise - 1);
		// the step off the top of the flight onto the deck: a gap in the parapet behind the top step
		int px = topX;
		int pz = r[1];
		d.inStage(StageKind.ROOF, () -> {
			d.set(px, deck, pz, Blocks.AIR.defaultBlockState());
			d.set(px, deck + 1, pz, Blocks.AIR.defaultBlockState());
		});
		// a rail along the stair's open side is the forecourt; a post at its foot
		return topX;
	}

	/** The pueblo's ladder from the forecourt up to the roof, its poles standing past the parapet, a gap where it arrives. */
	private static void ladder(Draft d, Body b, int door, int deck) {
		int[] r = b.rect(0);
		int z = r[1] - 1;
		if (z < 0) {
			return;
		}
		boolean right = door - r[0] <= r[2] - door;
		int x = Integer.MIN_VALUE;
		for (int n = 1; n < r[2] - r[0] && x == Integer.MIN_VALUE; n++) {
			int c = right ? r[2] - n : r[0] + n;
			if (Math.abs(c - door) < 1) {
				continue;
			}
			boolean solid = true;
			for (int y = 0; y < deck && solid; y++) {
				solid = Openings.isWallBlock(d, c, y, r[1]);
			}
			if (solid && d.isEmpty(c, 0, z)) {
				x = c;
			}
		}
		if (x == Integer.MIN_VALUE) {
			return;
		}
		int lx = x;
		d.inStage(StageKind.FLOORS, () -> {
			for (int y = 0; y < deck; y++) {
				d.set(lx, y, z, States.facing(Blocks.LADDER, Dirs.FRONT));
			}
		});
		Mat fence = d.mat(Role.FENCE);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(lx, deck, r[1], Blocks.AIR.defaultBlockState());
			d.set(lx, deck + 1, r[1], Blocks.AIR.defaultBlockState());
			// the ladder's poles up past the parapet (they read from across the canyon)
			if (deck + 2 < d.maxHeight()) {
				for (int px : new int[]{lx - 1, lx + 1}) {
					if (px >= r[0] && px <= r[2] && Openings.isWallBlock(d, px, deck, r[1])) {
						d.set(px, deck + 1, r[1], fence.fence());
					}
				}
			}
		}
	}

	/** The roof deck's life: drying racks and a pot or two (the deck itself stays open for the terrace above). */
	private static void roofLife(Draft d, Body b, int deck, boolean pueblo) {
		int[] tr = b.topRect();
		// lanterns stood in the deck's corners, so the terrace is never dark under the stars
		for (int[] c : new int[][]{{tr[0] + 1, tr[1] + 1}, {tr[2] - 1, tr[3] - 1}}) {
			if (d.allowed(c[0], deck, c[1]) && d.isEmpty(c[0], deck, c[1]) && d.isSolid(c[0], deck - 1, c[1])) {
				dev.voidmc.hearth.build.kit.light.Lighting.lamp(d, c[0], deck, c[1]);
			}
		}
		if (deck + 1 >= d.maxHeight()) {
			return;
		}
		// a hatch of light: a pot and, for the pueblo, a drying rack of fence and hay at the back corner
		int x = tr[2] - 1;
		int z = tr[3] - 1;
		if (d.isEmpty(x, deck, z)) {
			if (pueblo) {
				d.inStage(StageKind.EXTERIOR, () -> d.set(x, deck, z, Blocks.HAY_BLOCK.defaultBlockState()));
			} else {
				Garden.pot(d, x, deck, z);
			}
		}
		if (d.isEmpty(tr[0] + 1, deck, z)) {
			Garden.pot(d, tr[0] + 1, deck, z);
		}
		d.poi(PoiType.VIEWPOINT, (tr[0] + tr[2]) / 2, deck, (tr[1] + tr[3]) / 2, Dirs.FRONT);
	}

	/** The rooms: a corner hearth (the pueblo's beehive fireplace), beds, a store; a second room above when tall. */
	private static void interior(Draft d, Body b, Roofs.Roof roof, int door, boolean pueblo, boolean eastern) {
		int[] r = b.rect(0);
		int ix0 = r[0] + 1;
		int ix1 = r[2] - 1;
		int iz0 = r[1] + 1;
		int iz1 = r[3] - 1;
		int y = b.floorY(0);
		int air = b.height(0) - 1;
		// the hearth in a back corner, away from the door's side
		int hx = door - r[0] > r[2] - door ? ix0 : ix1;
		int hz = iz1;
		Furniture.hearth(d, hx, y, hz, hx == ix0 ? Dirs.RIGHT : Dirs.LEFT, false);
		Mat stone = d.mat(Role.CHIMNEY);
		// its flue up through the roof deck (a little chimney pot on the deck)
		int top = roof != null && roof.covers(hx, hz) ? roof.surfaceY(hx, hz) + 1 : b.plateY() + 1;
		if (top + 1 < d.maxHeight()) {
			try (var s = d.stage(StageKind.ROOF)) {
				for (int yy = y + 2; yy <= top; yy++) {
					d.set(hx, yy, hz, stone.full());
				}
				d.set(hx, top + 1, hz, States.lit(Blocks.CAMPFIRE, true)
					.trySetValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SIGNAL_FIRE, false));
			}
		}
		Rooms.Room room = new Rooms.Room(ix0, iz0, ix1, iz1, y, Math.min(3, air));
		room.keep(door, iz0);
		room.block(hx, hz);
		room.keep(hx + (hx == ix0 ? 1 : -1), hz);
		if (roof != null) {
			TownHouses.room(d, b, b.storeys() - 1, roof);
		}
		TownHouses.bedroom(d, room, 2);
		Rooms.furnish(d, room, Rooms.Type.KITCHEN, 0, JobType.NONE);
		dev.voidmc.hearth.build.kit.homestead.Household.dress(d, room, room, null);
	}

	/** The forecourt's furniture: an oven (the pueblo's horno), pots, a woodpile or hay, a bench by the door. */
	private static void yardLife(Draft d, int zb0, int door, boolean pueblo, boolean stacked) {
		int w = d.frontage();
		if (zb0 < 2) {
			return;
		}
		int z = zb0 - 1;
		int ox = door <= w / 2 ? w - 2 : 1;
		if (d.isEmpty(ox, 0, z) && (stacked || d.isSolid(ox, -1, z) || Garden.groundLike(d, ox, -1, z))) {
			if (pueblo) {
				// the horno: a beehive oven of mud brick with its mouth to the court
				d.inStage(StageKind.EXTERIOR, () -> {
					d.set(ox, 0, z, States.facing(Blocks.SMOKER, Dirs.FRONT));
					if (d.isEmpty(ox, 1, z)) {
						d.set(ox, 1, z, d.mat(Role.CHIMNEY).slab(SlabType.BOTTOM));
					}
				});
			} else {
				Garden.barrels(d, ox, 0, z, Dirs.FRONT, 1);
			}
		}
		for (int x = 1; x < w - 1; x++) {
			if (Math.abs(x - door) <= 1 || x == ox || !d.isEmpty(x, 0, 1) || zb0 < 3) {
				continue;
			}
			double u = Noise.unit(d.seed() ^ 0xC11FF, x, 0, 1);
			if (u < 0.3 && (stacked || d.isSolid(x, -1, 1))) {
				int px = x;
				Block pot = pueblo ? Blocks.DECORATED_POT : Blocks.FLOWER_POT;
				if (pueblo) {
					d.inStage(StageKind.EXTERIOR, () -> d.set(px, 0, 1, pot.defaultBlockState()));
				} else {
					Garden.pot(d, px, 0, 1);
				}
			}
		}
	}
}
