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
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.homestead.Beds;
import dev.voidmc.hearth.build.kit.homestead.Household;
import dev.voidmc.hearth.build.kit.homestead.Round;
import dev.voidmc.hearth.build.kit.homestead.Spots;
import dev.voidmc.hearth.build.kit.homestead.Terraces;
import dev.voidmc.hearth.build.kit.homestead.Verandas;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import dev.voidmc.hearth.build.terrain.Site;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:house/round_house}: the round house of the steppe and the tropics, truly round: a ring wall seven to
 * eleven cells across (always an odd ring where the plot allows, so the door, the fire and the cone's point line up),
 * under a cone that overhangs it all round.
 *
 * <ul>
 *   <li><b>Steppe</b> (a rondavel): a mud-plastered wall on a dark plinth, painted between plinth and head band with
 *   triangles, a stepped zigzag or lozenges in the ochre of the culture; a steep cone of thatch (or the culture's
 *   roofing) with a smoke hole at its point under a little cap; a stone bench round the foot of the wall, pots by the
 *   door.</li>
 *   <li><b>Tropical</b>: a veranda all round, raised on posts over water or wet ground (on a plinth elsewhere), under a
 *   two-tier roof: a skirt over the veranda on slender posts and a steep cone over the house.</li>
 * </ul>
 *
 * <p>Inside, the fire burns in the middle of the floor under the smoke hole; the beds lie with their heads to the wall,
 * chests, pots and a rug round them, a lantern hung from the cone. Other cultures (forced) build the steppe form in their
 * own materials.
 *
 * <p>Params (all optional): {@code roof} (string: thatch, tile), {@code stilts} (boolean), {@code veranda} (boolean,
 * default: the tropical form), {@code station}, {@code household} (see {@link Household}).
 */
public final class RoundHouse implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("house/round_house"), ArchetypeCategory.HOUSING, Set.of(), 2,
		9, 13, 9, 13, 10, 15, SettlementTier.CAMP, Set.of(Culture.STEPPE, Culture.TROPICAL), Set.of(), List.of(), 0, 1.5);

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
		Habits h = d.habits();
		boolean water = d.site().anyWater(0, 0, w - 1, dp - 1);
		boolean tropical = d.style().culture() == Culture.TROPICAL || d.style().culture() == null && h.windows().lattice();
		boolean stilted = d.param("stilts", h.plan().stilts() == Habits.Stilts.ALWAYS || water && h.plan().stilts() != Habits.Stilts.NEVER || water);
		// the ring: always an odd ring (a whole-cell centre: the door, the fire and the cone's point in line, and odd
		// circles are the round ones), centred across the plot (half a block to one side on an even frontage)
		double cx = (w & 1) == 1 ? (w - 1) / 2.0 : (w - 2) / 2.0 + (d.chance(0.5) ? 1 : 0);
		int room = (int) Math.min(cx, w - 1 - cx);
		if (stilted && !water && !d.hasParam("stilts") && Math.min((Math.min(w, dp - 2) - 3) / 2, room - 1) < 3
			&& Math.min((Math.min(w, dp) - 3) / 2, room - 1) >= 3) {
			// on dry ground a small plot would leave only a five-ring on its posts: a seven-ring on a plinth sleeps two
			stilted = false;
		}
		boolean wantVeranda = d.param("veranda", tropical || stilted);
		double r;
		double outer;
		// a raised house keeps rows in front for its stair (a row for each block it stands above the street); a veranda
		// only round a ring of seven or more (a smaller house on posts has a landing at its door instead)
		int spanV = Math.min(w, stilted ? dp - 3 : dp - 2);
		boolean veranda = wantVeranda && Math.min((spanV - 5) / 2, room - 2) >= 3;
		if (veranda) {
			// house ring, a deck two wide round it (the second ring the railing and the roof's posts)
			r = Math.clamp(Math.min((spanV - 5) / 2, room - 2), 3, 4);
			outer = r + 2;
		} else {
			int span = Math.min(w, stilted ? dp - 2 : dp);
			r = Math.clamp(Math.min((span - 3) / 2, room - 1), 2, 5);
			outer = r + 1;
		}
		// the centre back from the street: a row in front for the step or the stair, the roof's back eave on the plot
		double cz = dp - 1 - outer;
		Round.Ring ring = Round.ring(cx, cz, r);
		Round.Ring deck = Round.ring(cx, cz, outer);
		int street = Math.max(0, Terraces.street(d, (int) Math.round(cx)));
		int base = 0;
		if (stilted) {
			int lift = Math.max(1, h.plan().stiltHeight());
			for (int[] c : deck.disc()) {
				int wy = d.site().water(c[0], c[1]);
				base = Math.max(base, Math.max(d.site().ground(c[0], c[1]) + lift, wy == Site.NONE ? 0 : wy + lift));
			}
			base = Math.min(base, Math.min(street + 2, 4));
		} else if (veranda) {
			// a veranda on the ground stands a step up on its plinth
			base = Math.min(street + 1, Math.max(1, d.site().meanGround(deck.x0(), deck.z0(), deck.x1(), deck.z1()) + 1));
		}
		// a big rondavel stands taller, so its painted band shows under the eave
		int wallH = !veranda && r >= 4 && plateRoom(d, base, r) ? 5 : 4;
		int plate = base + wallH - 1;
		Mat cover = cover(d);
		double pitch = Math.max(0.9, h.roof().pitch());
		// one cone over everything: over a veranda its eaves sweep out two cells, shallow over the deck, steep above
		int eaves = veranda ? 2 : 1;
		while (pitch > 0.6 && plate + 1 + pitch * (r + eaves + 0.5) + 3 >= d.maxHeight()) {
			pitch -= 0.1;
		}
		d.body(ring.x0(), ring.z0(), ring.x1(), ring.z1());
		d.eave(plate);
		int[] door = ring.cellToward(Dirs.FRONT);
		int doorX = door[0];
		int doorZ = door[1];

		// ---- ground: the plinth or the posts, the deck
		if (stilted) {
			stilts(d, ring, deck, base);
		} else {
			footings(d, veranda ? deck : ring, ring, base);
			if (!d.site().anyWater(0, 0, w - 1, Math.max(0, deck.z0() - 1))) {
				Groundwork.level(d, 0, 0, w - 1, Math.max(0, deck.z0() - 1), 0);
			}
		}
		// ---- the wall, painted where the culture paints
		Round.walls(d, ring, base, plate, pattern(d, tropical));
		try (var s = d.stage(StageKind.OPENINGS)) {
			d.door(doorX, base, doorZ, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), Dirs.FRONT, DoorHingeSide.LEFT);
		}
		doorCase(d, ring, doorX, doorZ, base);
		d.entrance(doorX, base, doorZ - 1);
		d.poi(PoiType.DOOR, doorX, base, doorZ - 1, Dirs.BACK);
		windows(d, ring, base);
		// ---- roof: a skirt over the veranda and a cone over the house, or one cone with its eaves out over the wall
		Roofs.Roof roof = Round.cone(d, ring, plate, eaves, pitch, cover, true);
		Roofs.Roof skirt = roof;
		// ---- veranda: slender posts under the skirt's eave, the railing, the way in
		int[] gap = deck.cellToward(Dirs.FRONT);
		if (veranda) {
			verandaParts(d, ring, deck, skirt, base, gap);
		} else if (stilted) {
			landing(d, doorX, doorZ, base);
		}
		// ---- inside
		int[] fire = interior(d, ring, base, roof, doorX, doorZ);
		boolean cooks = fire != null;
		if (!cooks && veranda) {
			// a small house cooks on its veranda: a fire on a clay hearth at the back of the deck
			cooks = verandaFire(d, ring, deck, base);
		}
		if (!cooks) {
			// or out in the yard, on a ring of stones beside the house
			yardFire(d, ring, doorX);
		}
		// ---- the way in
		if (veranda) {
			if (base > street || gap[1] > 0) {
				Groundwork.approach(d, gap[0], base, gap[1]);
			}
		} else if (doorZ - 1 >= 0) {
			Groundwork.approach(d, doorX, base, doorZ - 1);
			if (!stilted) {
				doorstep(d, doorX, base, doorZ);
			}
		}
		if (!veranda) {
			bench(d, ring, base, doorX);
		}
		outside(d, veranda ? deck : ring, base, doorX, veranda);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, roof.ridgeY + 2));
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	/** Whether the plot is tall enough for a five-course wall under the cone. */
	private static boolean plateRoom(Draft d, int base, double r) {
		return base + 5 + r + 2 + 3 < d.maxHeight();
	}

	/** Painted in the steppe (and by seed in other rendered cultures); the tropics weave, they do not paint. */
	private static Round.Pattern pattern(Draft d, boolean tropical) {
		if (tropical) {
			return Round.Pattern.NONE;
		}
		boolean steppe = d.style().culture() == Culture.STEPPE;
		if (!steppe && !d.chance(0.3)) {
			return Round.Pattern.NONE;
		}
		return d.pick(Round.Pattern.TRIANGLES, Round.Pattern.STEPS, Round.Pattern.LOZENGES);
	}

	/** Thatch where it can be laid in courses (or the param asks), otherwise the culture's roofing. */
	private static Mat cover(Draft d) {
		String p = d.param("roof", "");
		Mat thatch = d.mat(Role.THATCH);
		if (p.equals("thatch")) {
			return thatch;
		}
		if (p.equals("tile")) {
			return d.mat(Role.ROOF);
		}
		if (d.habits().roof().thatch()) {
			return thatch;
		}
		return d.chance(thatch.has(Form.STAIRS) ? 0.7 : 0.45) ? thatch : d.mat(Role.ROOF);
	}

	/** Footings under the plinth (the ring, or the veranda's disc) and a floor: boards inside, the deck round it. */
	private static void footings(Draft d, Round.Ring plinth, Round.Ring ring, int base) {
		Mat found = d.mat(Role.FOUNDATION);
		Mat fill = d.mat(Role.FILL);
		Mat floor = d.mat(Role.FLOOR);
		for (int[] c : plinth.disc()) {
			int g = d.site().ground(c[0], c[1]);
			boolean wall = ring.isWall(c[0], c[1]);
			boolean in = ring.inside(c[0], c[1]);
			boolean edge = plinth.isWall(c[0], c[1]);
			try (var s = d.stage(StageKind.FOUNDATION)) {
				for (int y = Math.min(base - 1, g - 2); y < base - 1; y++) {
					d.set(c[0], y, c[1], wall || edge ? found.full() : fill.full());
				}
				d.set(c[0], base - 1, c[1], wall || edge && plinth != ring ? found.full() : floor.full());
			}
			if (in) {
				try (var s = d.stage(StageKind.EARTHWORK)) {
					for (int y = base; y < g; y++) {
						d.set(c[0], y, c[1], Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
	}

	/** Posts to the ground or the bed under the house and the deck, the floor boarded over them, a ring beam. */
	private static void stilts(Draft d, Round.Ring ring, Round.Ring deck, int base) {
		Mat floor = d.mat(Role.FLOOR);
		for (int[] c : deck.disc()) {
			boolean post = deck.isWall(c[0], c[1]) && Round.post(deck, c) || ring.isWall(c[0], c[1]) && Round.post(ring, c)
				|| c[0] == Math.round(ring.cx()) && c[1] == Math.round(ring.cz());
			if (post) {
				Verandas.pile(d, c[0], c[1], base - 2);
			}
			try (var s = d.stage(StageKind.FOUNDATION)) {
				d.set(c[0], base - 1, c[1], floor.full());
			}
		}
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int[] c : ring.wall()) {
				if (d.isEmpty(c[0], base - 2, c[1]) && base - 2 >= d.site().ground(c[0], c[1])) {
					d.set(c[0], base - 2, c[1], d.mat(Role.BEAM).log(Direction.Axis.X));
				}
			}
		}
	}

	/** The door case: posts either side in timber or the plinth's stone in a painted wall, a lintel over. */
	private static void doorCase(Draft d, Round.Ring ring, int x, int z, int base) {
		Mat frame = d.mat(Role.FRAME);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int dx : new int[]{-1, 1}) {
				if (ring.isWall(x + dx, z)) {
					for (int y = base; y <= base + 2; y++) {
						d.set(x + dx, y, z, frame.strippedLog(Direction.Axis.Y).orElse(frame.log(Direction.Axis.Y)));
					}
				}
			}
			d.set(x, base + 2, z, d.mat(Role.BEAM).log(Direction.Axis.X));
		}
	}

	/** Small windows at the quarters: latticed where the culture lattices, shuttered panes elsewhere, a sill outside. */
	private static void windows(Draft d, Round.Ring ring, int base) {
		boolean lattice = d.habits().windows().lattice();
		for (Direction dir : new Direction[]{Dirs.LEFT, Dirs.RIGHT, Dirs.BACK}) {
			int[] c = ring.cellToward(dir);
			if (c == null) {
				continue;
			}
			try (var s = d.stage(StageKind.OPENINGS)) {
				d.set(c[0], base + 1, c[1], lattice ? d.mat(Role.SHUTTER).trapdoor(dir.getOpposite(), Half.BOTTOM, true) : d.mat(Role.GLASS).pane());
			}
			int ox = c[0] + Dirs.dx(dir);
			int oz = c[1] + Dirs.dz(dir);
			if (!Verandas.in(d, ox, oz)) {
				continue;
			}
			try (var s = d.stage(StageKind.OPENINGS)) {
				if (d.isAbsent(ox, base, oz) && !lattice) {
					d.set(ox, base, oz, d.mat(Role.WINDOW).stairsOrSlab(dir.getOpposite(), Half.TOP));
				}
				// shutters folded back either side of the opening
				Direction along = Dirs.right(dir);
				for (int sgn : new int[]{-1, 1}) {
					int sx = ox + Dirs.dx(along) * sgn;
					int sz = oz + Dirs.dz(along) * sgn;
					if (!lattice && Verandas.in(d, sx, sz) && d.isAbsent(sx, base + 1, sz) && ring.isWall(c[0] + Dirs.dx(along) * sgn, c[1] + Dirs.dz(along) * sgn)) {
						d.set(sx, base + 1, sz, d.mat(Role.SHUTTER).trapdoor(dir, Half.BOTTOM, true));
					}
				}
			}
		}
	}

	/** The veranda: posts under the skirt's eave every few cells, the railing round the deck's edge, a hammock, pots. */
	private static void verandaParts(Draft d, Round.Ring ring, Round.Ring deck, Roofs.Roof skirt, int base, int[] gap) {
		Mat frame = d.mat(Role.FRAME);
		Mat post = frame.has(Form.FENCE) ? frame : d.mat(Role.RAILING);
		int n = 0;
		for (int[] c : deck.wall()) {
			if (c[0] == gap[0] && c[1] == gap[1] || ring.disc().stream().anyMatch(e -> e[0] == c[0] && e[1] == c[1])) {
				continue;
			}
			boolean isPost = Round.post(deck, c) || Math.abs(c[0] - gap[0]) == 1 && c[1] == gap[1];
			if (isPost && skirt.covers(c[0], c[1])) {
				int top = skirt.bottomY(c[0], c[1]) - 1;
				try (var s = d.stage(StageKind.FRAME)) {
					for (int y = base; y <= top; y++) {
						d.set(c[0], y, c[1], post.fence());
					}
				}
			} else {
				Verandas.rail(d, c[0], c[1], base);
			}
			n++;
		}
		d.poi(PoiType.VIEWPOINT, gap[0] + (gap[0] + 1 < d.frontage() ? 1 : -1), base, gap[1] + 1, Dirs.FRONT);
		// a hammock on the veranda's side, a pot at the steps, lanterns under the skirt
		int[] side = deck.cellToward(Noise.unit(d.seed(), 1, 2, 3) < 0.5 ? Dirs.LEFT : Dirs.RIGHT);
		if (side != null) {
			int sx = side[0] + (side[0] < ring.cx() ? 1 : -1);
			Verandas.hammock(d, sx, side[1] - 1, Dirs.BACK, 2, base);
		}
		for (int dx : new int[]{-2, 2}) {
			int x = gap[0] + dx;
			int z = gap[1] + 1;
			if (deck.inside(x, z) && !ring.disc().stream().anyMatch(e -> e[0] == x && e[1] == z)) {
				Verandas.pot(d, x, base, z);
			}
		}
		for (Direction dir : new Direction[]{Dirs.LEFT, Dirs.RIGHT, Dirs.BACK}) {
			int[] c = deck.cellToward(dir);
			if (c != null) {
				int lx = c[0] - Dirs.dx(dir);
				int lz = c[1] - Dirs.dz(dir);
				if (skirt.covers(lx, lz)) {
					Verandas.lantern(d, lx, skirt.bottomY(lx, lz) - 1, lz);
				}
			}
		}
		Verandas.lantern(d, gap[0], skirt.covers(gap[0], gap[1] + 1) ? skirt.bottomY(gap[0], gap[1] + 1) - 1 : base + 2, gap[1] + 1);
	}

	/**
	 * The room round the fire: the fire first in the middle under the smoke hole, then the beds with their heads to the
	 * wall, chests and pots, a lantern hung from the cone away from the smoke, the household's things.
	 */
	private static int[] interior(Draft d, Round.Ring ring, int base, Roofs.Roof roof, int doorX, int doorZ) {
		Rooms.Room room = Round.room(ring, base, 3);
		room.keep(doorX, doorZ + 1);
		// a small ring sleeps its household first and keeps its fire only if the floor can spare it
		boolean small = ring.r() < 3;
		int[] fire = small ? null : Round.fire(d, ring, room, base);
		int want = ring.r() >= 4 ? 3 : 2;
		int placed = Beds.place(d, room, want);
		if (placed < 2) {
			// the furnisher may still find room the deliberate placing did not
			Rooms.furnish(d, room, Rooms.Type.BEDROOM, 2 - placed, JobType.NONE);
		}
		if (small) {
			fire = null;
		}
		Rooms.furnish(d, room, Rooms.Type.BEDROOM, 0, JobType.NONE);
		Rooms.furnish(d, room, Rooms.Type.STORAGE, 0, JobType.NONE);
		Round.pots(d, ring, room, base, 3);
		Household.dress(d, room, room, fire);
		// lanterns hung from the cone either side of the fire
		int ax = (int) Math.round(ring.cx());
		int az = (int) Math.round(ring.cz());
		for (int[] o : new int[][]{{0, 1}, {0, -1}, {1, 0}, {-1, 0}}) {
			int lx = ax + o[0] * 2;
			int lz = az + o[1] * 2;
			int under = roof.bottomY(lx, lz);
			if (ring.inside(lx, lz) && under != Integer.MIN_VALUE && under - base > 3 && d.isEmpty(lx, under - 1, lz) && d.isEmpty(lx, under - 2, lz)) {
				Furniture.hangingLantern(d, lx, under, lz, Math.max(0, under - base - 4));
				break;
			}
		}
		return fire;
	}

	/** A landing on posts before the door of a raised house without a veranda: three boards wide, railed at its ends. */
	private static void landing(Draft d, int x, int doorZ, int base) {
		int z = doorZ - 1;
		for (int dx = -1; dx <= 1; dx++) {
			Verandas.deck(d, x + dx, z, base);
			if (dx != 0) {
				Verandas.pile(d, x + dx, z, base - 2);
				Verandas.rail(d, x + dx, z, base);
			}
		}
	}

	/** A cooking fire on a clay hearth at the back of the veranda, its HEARTH beside it on the deck. */
	private static boolean verandaFire(Draft d, Round.Ring ring, Round.Ring deck, int base) {
		for (Direction dir : new Direction[]{Dirs.BACK, Dirs.LEFT, Dirs.RIGHT}) {
			int[] c = deck.cellToward(dir);
			if (c == null) {
				continue;
			}
			// the deck cell just inside the railing, and the one beside it along the deck to stand at
			int fx = c[0] - Dirs.dx(dir);
			int fz = c[1] - Dirs.dz(dir);
			Direction along = Dirs.right(dir);
			int sx = fx + Dirs.dx(along);
			int sz = fz + Dirs.dz(along);
			if (ring.disc().stream().anyMatch(e -> e[0] == fx && e[1] == fz) || !Spots.standable(d, fx, base, fz) || !d.isAbsent(fx, base, fz)
				|| !Spots.standable(d, sx, base, sz) || !d.isAbsent(sx, base, sz)) {
				continue;
			}
			try (var s = d.stage(StageKind.INTERIOR)) {
				d.set(fx, base - 1, fz, d.mat(Role.CHIMNEY).full());
				d.set(fx, base, fz, States.lit(Blocks.CAMPFIRE, true).trySetValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SIGNAL_FIRE, false));
			}
			d.poi(PoiType.HEARTH, sx, base, sz, along.getOpposite());
			return true;
		}
		return false;
	}

	/**
	 * A cooking fire in the yard beside a small house (one with no room for a fire inside or a veranda to cook on): a
	 * campfire on a stone in a ring of stone slabs on the ground clear of the house and the way in, its HEARTH where the
	 * cook stands beside it.
	 */
	private static void yardFire(Draft d, Round.Ring ring, int doorX) {
		Mat stone = d.mat(Role.FOUNDATION);
		for (double rr : new double[]{ring.r() + 2.2, ring.r() + 3.2}) {
			for (int[] c : Round.ring(ring.cx(), ring.cz(), rr).wall()) {
				int fx = c[0];
				int fz = c[1];
				// beside or behind the house, never across the way in
				if (fz < ring.cz() - 1 || !Verandas.in(d, fx, fz) || fx <= 0 || fx >= d.frontage() - 1 || fz >= d.depth() - 1) {
					continue;
				}
				int y = Spots.level(d, fx, fz);
				if (y + 3 >= d.maxHeight() || !Spots.standable(d, fx, y, fz) || !d.isAbsent(fx, y, fz) || d.site().water(fx, fz) != Site.NONE) {
					continue;
				}
				// the cook's cell: toward the house, standable at the same level
				Direction in = Math.abs(fx - ring.cx()) >= Math.abs(fz - ring.cz()) ? (fx < ring.cx() ? Dirs.RIGHT : Dirs.LEFT) : Dirs.FRONT;
				int sx = fx + Dirs.dx(in);
				int sz = fz + Dirs.dz(in);
				if (Spots.level(d, sx, sz) != y || !Spots.standable(d, sx, y, sz) || !d.isAbsent(sx, y, sz)
					|| ring.disc().stream().anyMatch(e -> e[0] == sx && e[1] == sz)) {
					continue;
				}
				try (var s = d.stage(StageKind.EXTERIOR)) {
					d.set(fx, y - 1, fz, stone.full());
					d.set(fx, y, fz, States.lit(Blocks.CAMPFIRE, true).trySetValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SIGNAL_FIRE, false));
					// the ring of stones round it, where the ground is free (never on the cook's cell)
					for (Direction dir : Dirs.HORIZONTAL) {
						int ox = fx + Dirs.dx(dir);
						int oz = fz + Dirs.dz(dir);
						if ((ox != sx || oz != sz) && Verandas.in(d, ox, oz) && Spots.level(d, ox, oz) == y && Spots.standable(d, ox, y, oz) && d.isAbsent(ox, y, oz)
							&& stone.has(Form.SLAB) && Noise.unit(d.seed() ^ 0xF1E, ox, y, oz) < 0.6) {
							d.set(ox, y, oz, stone.slab(SlabType.BOTTOM));
						}
					}
				}
				d.poi(PoiType.HEARTH, sx, y, sz, in.getOpposite());
				return;
			}
		}
	}

	/** A low bench of the plinth's stone round the foot of the wall outside, broken at the door. */
	private static void bench(Draft d, Round.Ring ring, int base, int doorX) {
		Mat plinth = d.mat(Role.PLINTH);
		if (!plinth.has(Form.SLAB)) {
			return;
		}
		Round.Ring out = Round.ring(ring.cx(), ring.cz(), ring.r() + 1);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] c : out.wall()) {
				if (!Verandas.in(d, c[0], c[1]) || Math.abs(c[0] - doorX) <= 1 && c[1] < ring.cz()) {
					continue;
				}
				if (ring.disc().stream().anyMatch(e -> e[0] == c[0] && e[1] == c[1])) {
					continue;
				}
				if (Spots.level(d, c[0], c[1]) == base && d.isAbsent(c[0], base, c[1]) && Noise.unit(d.seed() ^ 0xBE4, c[0], 0, c[1]) < 0.55) {
					d.set(c[0], base, c[1], plinth.slab(SlabType.BOTTOM));
				}
			}
		}
	}

	/** Pots either side of the door. */
	private static void doorstep(Draft d, int x, int base, int doorZ) {
		int z = doorZ - 1;
		for (int dx : new int[]{-1, 1}) {
			int px = x + dx;
			if (px >= 0 && px < d.frontage() && z >= 0 && Spots.standable(d, px, base, z) && d.isAbsent(px, base, z) && Noise.unit(d.seed(), px, 0, z) < 0.8) {
				d.inStage(StageKind.EXTERIOR, () -> d.set(px, base, z, States.of(Blocks.DECORATED_POT)));
			}
		}
	}

	/** Outside: the culture's plants round the house where the ground is free, a woodpile. */
	private static void outside(Draft d, Round.Ring around, int base, int doorX, boolean veranda) {
		int w = d.frontage();
		for (int[] c : Round.ring(around.cx(), around.cz(), around.r() + 1.2).wall()) {
			if (!Verandas.in(d, c[0], c[1]) || Math.abs(c[0] - doorX) <= 1 && c[1] < around.cz()) {
				continue;
			}
			int y = Spots.level(d, c[0], c[1]);
			if (y + 2 < d.maxHeight() && Spots.standable(d, c[0], y, c[1]) && d.isAbsent(c[0], y, c[1]) && Noise.unit(d.seed() ^ 0x6A4, c[0], 0, c[1]) < 0.3
				&& Garden.groundLike(d, c[0], y - 1, c[1])) {
				Garden.garden(d, c[0], c[1], c[0], c[1], y);
			}
		}
		// a woodpile against the back corner
		if (d.chance(Math.max(0.4, d.habits().yard().woodpile()))) {
			int x = around.x1() + 1 < w ? around.x1() + 1 : around.x0() - 1;
			int z = (int) Math.round(around.cz()) + 1;
			if (Verandas.in(d, x, z) && Verandas.in(d, x, z + 1)) {
				int y = Spots.level(d, x, z);
				if (Spots.standable(d, x, y, z) && Spots.standable(d, x, y, z + 1) && d.isAbsent(x, y, z) && d.isAbsent(x, y, z + 1)) {
					Garden.woodpile(d, x, y, z, Dirs.BACK, 2);
				}
			}
		}
	}
}
