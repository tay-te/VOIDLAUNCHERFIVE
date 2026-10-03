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
import dev.voidmc.hearth.build.kit.homestead.Round;
import dev.voidmc.hearth.build.kit.homestead.Spots;
import dev.voidmc.hearth.build.kit.homestead.Terraces;
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
 * {@code hearth:house/round_house}: the round house of the steppe and the tropics. A ring wall on a plinth, posts at the
 * quarters, a band of the culture's colour round its head; a cone roof of thatch, reed or tile overhanging it with a clay
 * pot on its point where the culture works clay; a door to the street between two posts, little shuttered or latticed
 * windows round the ring. Inside, the fire burns in the middle of the floor; beds, chests, a loom, pots and a rug keep to
 * the wall. Where the culture builds on stilts (the tropics, or over water) the house stands on posts over a veranda
 * that runs round it behind a railing, a stair down to the ground. Outside: pots by the door, a woodpile, a kitchen
 * patch or the culture's garden.
 *
 * <p>Params (all optional): {@code roof} (string: thatch, tile: the covering; default the culture's, thatch by seed),
 * {@code stilts} (boolean).
 */
public final class RoundHouse implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("house/round_house"), ArchetypeCategory.HOUSING, Set.of(), 2,
		7, 9, 7, 9, 9, 13, SettlementTier.CAMP, Set.of(Culture.STEPPE, Culture.TROPICAL), Set.of(), List.of(), 0, 1.5);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Habits h = d.habits();
		boolean water = d.site().anyWater(0, 0, w - 1, dp - 1);
		boolean stilted = d.param("stilts", h.plan().stilts() == Habits.Stilts.ALWAYS || water && h.plan().stilts() != Habits.Stilts.NEVER || water);
		int span = Math.min(w, dp);
		boolean veranda = stilted && span >= 9;
		double r = veranda ? 2 : span >= 9 ? 3 : 2;
		int eaves = veranda ? 2 : 1;
		double cx = (w - 1) / 2.0;
		// back on the plot, a row left in front of the veranda (or the door) for the steps down to the street
		double outer = veranda ? r + 1.3 : r;
		double cz = Math.max(outer + 1.0, dp - 1 - Math.max(outer, r + eaves - 0.5));
		Round.Ring ring = Round.ring(cx, cz, r);
		int stiltH = veranda ? Math.max(2, h.plan().stiltHeight()) : 1;
		int base = 0;
		if (stilted) {
			for (int[] c : ring.disc()) {
				int wy = d.site().water(c[0], c[1]);
				base = Math.max(base, Math.max(d.site().ground(c[0], c[1]) + stiltH + 1, wy == Site.NONE ? 0 : wy + 2));
			}
			base = Math.min(base, 4);
		}
		int wallH = stilted && !veranda ? 3 : 4;
		int plate = base + wallH - 1;
		Mat roofMat = cover(d);
		double pitch = 1.15;
		// a lower cone where the plot's height is tight
		double rr = r + eaves;
		while (pitch > 0.6 && plate + 1 + pitch * (rr + 0.5) + 2 >= d.maxHeight()) {
			pitch -= 0.1;
		}
		d.body(ring.x0(), ring.z0(), ring.x1(), ring.z1());
		d.eave(plate);
		int[] door = ring.cellToward(Dirs.FRONT);
		int doorX = door[0];
		int doorZ = door[1];

		// ground
		if (stilted) {
			stilts(d, ring, base, veranda);
		} else {
			footings(d, ring, base);
			if (!d.site().anyWater(0, 0, w - 1, Math.max(0, doorZ - 1))) {
				Groundwork.level(d, 0, 0, w - 1, Math.max(0, doorZ - 1), 0);
			}
		}
		Round.walls(d, ring, base, plate);
		// the door between two posts, windows round the ring
		try (var s = d.stage(StageKind.OPENINGS)) {
			d.door(doorX, base, doorZ, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), Dirs.FRONT, DoorHingeSide.LEFT);
		}
		try (var s = d.stage(StageKind.FRAME)) {
			for (int dx : new int[]{-1, 1}) {
				if (ring.r() >= 3 && ring.isWall(doorX + dx, doorZ)) {
					for (int y = base; y <= plate; y++) {
						d.set(doorX + dx, y, doorZ, d.mat(Role.FRAME).log(Direction.Axis.Y));
					}
				}
			}
			d.set(doorX, base + 2, doorZ, d.mat(Role.BEAM).log(Direction.Axis.X));
		}
		d.entrance(doorX, base, doorZ - 1);
		d.poi(PoiType.DOOR, doorX, base, doorZ - 1, Dirs.BACK);
		windows(d, ring, base);
		Roofs.Roof roof = Round.cone(d, ring, plate, eaves, pitch, roofMat);
		// inside
		interior(d, ring, base, roof, doorX, doorZ);
		// the way in
		if (stilted) {
			int[] gap = veranda ? Round.ring(ring.cx(), ring.cz(), ring.r() + 1.3).cellToward(Dirs.FRONT) : new int[]{doorX, doorZ};
			Groundwork.approach(d, doorX, base, gap[1] - 1);
		} else if (doorZ - 1 >= 0) {
			Groundwork.approach(d, doorX, base, doorZ - 1);
			doorstep(d, doorX, base, doorZ);
		}
		if (!stilted) {
			bench(d, ring, base, doorX);
		}
		outside(d, ring, base, doorX, doorZ, stilted);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, roof.ridgeY + 3));
	}

	/** Thatch (the culture's, or hay) by seed or param; else the culture's roofing (planks, mud tile). */
	private static Mat cover(Draft d) {
		String p = d.param("roof", "");
		if (p.equals("thatch")) {
			return d.mat(Role.THATCH);
		}
		if (p.equals("tile")) {
			return d.mat(Role.ROOF);
		}
		if (d.habits().roof().thatch()) {
			return d.mat(Role.ROOF);
		}
		return d.chance(0.2) ? d.mat(Role.THATCH) : d.mat(Role.ROOF);
	}

	/** Footings under the ring and a floor inside it: beaten earth with boards or the culture's floor. */
	private static void footings(Draft d, Round.Ring ring, int base) {
		Mat found = d.mat(Role.FOUNDATION);
		Mat fill = d.mat(Role.FILL);
		Mat floor = d.mat(Role.FLOOR);
		for (int[] c : ring.disc()) {
			int g = d.site().ground(c[0], c[1]);
			boolean wall = ring.isWall(c[0], c[1]);
			try (var s = d.stage(StageKind.FOUNDATION)) {
				for (int y = Math.min(base - 1, g - 2); y < base - 1; y++) {
					d.set(c[0], y, c[1], wall ? found.full() : fill.full());
				}
				d.set(c[0], base - 1, c[1], wall ? found.full() : floor.full());
			}
			if (!wall) {
				try (var s = d.stage(StageKind.EARTHWORK)) {
					for (int y = base; y < g; y++) {
						d.set(c[0], y, c[1], Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
	}

	/**
	 * Posts down to the ground (or the bed of the water) under the ring and the veranda, the floor boarded over them, the
	 * veranda's railing with a gap at the front for the stair.
	 */
	private static void stilts(Draft d, Round.Ring ring, int base, boolean veranda) {
		Mat stilt = d.mat(Role.STILT);
		Mat floor = d.mat(Role.FLOOR);
		Mat rail = d.mat(Role.RAILING);
		Round.Ring deck = veranda ? Round.ring(ring.cx(), ring.cz(), ring.r() + 1.3) : ring;
		for (int[] c : deck.disc()) {
			int g = d.site().ground(c[0], c[1]);
			boolean post = deck.isWall(c[0], c[1]) && Round.post(deck, c) || ring.isWall(c[0], c[1]) && Round.post(ring, c)
				|| c[0] == Math.round(ring.cx()) && c[1] == Math.round(ring.cz());
			try (var s = d.stage(StageKind.FOUNDATION)) {
				if (post) {
					for (int y = g - 2; y < base - 1; y++) {
						d.set(c[0], y, c[1], stilt.log(Direction.Axis.Y));
					}
				}
				d.set(c[0], base - 1, c[1], floor.full());
			}
		}
		// joists under the floor tie the posts: a ring beam under the wall
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int[] c : ring.wall()) {
				if (d.isEmpty(c[0], base - 2, c[1]) && base - 2 >= d.site().ground(c[0], c[1])) {
					d.set(c[0], base - 2, c[1], d.mat(Role.BEAM).log(Direction.Axis.X));
				}
			}
		}
		if (veranda) {
			int[] gap = deck.cellToward(Dirs.FRONT);
			try (var s = d.stage(StageKind.EXTERIOR)) {
				for (int[] c : deck.wall()) {
					if (c[0] == gap[0] && c[1] == gap[1] || ring.disc().stream().anyMatch(e -> e[0] == c[0] && e[1] == c[1])) {
						continue;
					}
					d.set(c[0], base, c[1], rail.fence());
				}
			}
			d.poi(PoiType.VIEWPOINT, gap[0] + 1, base, gap[1] + 1, Dirs.FRONT);
		}
	}

	/** Small windows round the ring at the quarters: latticed where the culture lattices, shuttered panes elsewhere. */
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
			// shutters folded back either side, a sill below, where it is outside the ring
			int ox = c[0] + Dirs.dx(dir);
			int oz = c[1] + Dirs.dz(dir);
			if (!lattice && ox >= 0 && oz >= 0 && ox < d.frontage() && oz < d.depth() && d.isAbsent(ox, base, oz)) {
				try (var s = d.stage(StageKind.OPENINGS)) {
					d.set(ox, base, oz, d.mat(Role.WINDOW).stairsOrSlab(dir.getOpposite(), Half.TOP));
				}
			}
		}
	}

	/** The room round the fire: beds against the wall, a chest, a loom or a work bench, pots, a rug, a lantern. */
	private static void interior(Draft d, Round.Ring ring, int base, Roofs.Roof roof, int doorX, int doorZ) {
		Rooms.Room room = Round.room(ring, base, 3);
		room.keep(doorX, doorZ + 1);
		// a big ring keeps its fire in the middle; a small one beds its folk first and fits the fire where it can
		if (ring.r() >= 3) {
			Round.fire(d, ring, room, base);
			Rooms.furnish(d, room, Rooms.Type.BEDROOM, 3, JobType.NONE);
		} else {
			Rooms.furnish(d, room, Rooms.Type.BEDROOM, 2, JobType.NONE);
			Round.fire(d, ring, room, base);
		}
		Rooms.furnish(d, room, Rooms.Type.STORAGE, 0, JobType.NONE);
		Round.pots(d, ring, room, base, 3);
		// a lantern from the roof's peak on a chain, away from the fire's smoke
		int ax = (int) Math.round(ring.cx());
		int az = (int) Math.round(ring.cz()) + 1;
		int under = roof.bottomY(ax, az);
		if (under != Integer.MIN_VALUE && under - base > 3 && d.isEmpty(ax, under - 1, az)) {
			Furniture.hangingLantern(d, ax, under, az, Math.max(0, under - base - 4));
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
				if (c[0] < 0 || c[1] < 0 || c[0] >= d.frontage() || c[1] >= d.depth() || Math.abs(c[0] - doorX) <= 1 && c[1] < ring.cz()) {
					continue;
				}
				if (ring.disc().stream().anyMatch(e -> e[0] == c[0] && e[1] == c[1])) {
					continue;
				}
				if (Spots.level(d, c[0], c[1]) == base && d.isAbsent(c[0], base, c[1]) && Noise.unit(d.seed() ^ 0xBE4, c[0], 0, c[1]) < 0.7) {
					d.set(c[0], base, c[1], plinth.slab(SlabType.BOTTOM));
				}
			}
		}
	}

	/** A step of the culture's paving at the door and pots either side of it. */
	private static void doorstep(Draft d, int x, int base, int doorZ) {
		int z = doorZ - 1;
		for (int dx : new int[]{-1, 1}) {
			int px = x + dx;
			if (px >= 0 && px < d.frontage() && z >= 0 && Spots.standable(d, px, base, z) && d.isAbsent(px, base, z) && Noise.unit(d.seed(), px, 0, z) < 0.7) {
				d.inStage(StageKind.EXTERIOR, () -> d.set(px, base, z, States.of(Blocks.DECORATED_POT)));
			}
		}
	}

	/** Outside: a woodpile or a kitchen patch to one side, the culture's plants round the ring. */
	private static void outside(Draft d, Round.Ring ring, int base, int doorX, int doorZ, boolean stilted) {
		int w = d.frontage();
		// garden plants round the wall foot where the ground is free
		for (int[] c : Round.ring(ring.cx(), ring.cz(), ring.r() + (stilted ? 2.6 : 1.4)).wall()) {
			if (c[0] < 0 || c[1] < 0 || c[0] >= w || c[1] >= d.depth() || Math.abs(c[0] - doorX) <= 1 && c[1] < ring.cz()) {
				continue;
			}
			int y = Spots.level(d, c[0], c[1]);
			if (y + 2 < d.maxHeight() && Spots.standable(d, c[0], y, c[1]) && d.isAbsent(c[0], y, c[1]) && Noise.unit(d.seed() ^ 0x6A4, c[0], 0, c[1]) < 0.35
				&& Garden.groundLike(d, c[0], y - 1, c[1])) {
				Garden.garden(d, c[0], c[1], c[0], c[1], y);
			}
		}
	}
}
