package dev.voidmc.hearth.build.archetype.deep;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.BlockEntities;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.deep.DeepManner;
import dev.voidmc.hearth.build.kit.deep.DeepSite;
import dev.voidmc.hearth.build.kit.deep.Glow;
import dev.voidmc.hearth.build.kit.deep.Rock;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:deep/lift}: the shaft that joins a surface town to its deep town in the realm below (LIVING_WORLD.md
 * §8.2, §8.5) — the link that makes the deep town reachable. A seven-wide stone shaft from the realm's floor to the
 * street above. Inside, a stair climbs round and round an open lightwell, one step a cell, a rail post at every step
 * on the well's side; down the middle of the well hangs the hoist's chain, a lantern strung on it every six blocks, from
 * the winding beam at the head to the cage at the foot (so the whole stair is lit, and looking up the shaft is looking
 * up a string of lights). In the realm the shaft stands as a tower on a stepped plinth, slit windows glowing up its
 * faces and string courses every eight blocks, and meets the vault on a ring of corbels; above the vault it is a lined
 * shaft through the rock. At the head stands the winding house on the street — the surface town's own walls and roof
 * (nothing from below shows on the surface) — with a timber headframe and its sheave rising through the roof, and a
 * yard round it. The haulers work the cage at the foot and the windlass at the head.
 *
 * <p>Params (the planner's, PLANNING.md §8):
 * <ul>
 *   <li>{@code top_y} (world y): the street at the head; {@code street_y} the same; {@code bottom_y} = {@code origin.y},
 *   the realm floor at the foot; {@code deep_street_y}: the deep square at the lower door (default the floor)</li>
 *   <li>{@code deep_facing} (a horizontal direction's name, world): which way the lower door opens, onto the deep
 *   square (default: the plot's own facing)</li>
 *   <li>{@code ceiling_y} (world y): the vault's underside over the lift, where the tower meets the rock (default: sixteen
 *   under the street, the least cover a realm keeps)</li>
 *   <li>{@code character}, {@code local}, {@code realm}: the realm ({@link DeepSite})</li>
 *   <li>without {@code top_y} (built by hand on the surface, {@code /hearth build}): a pit-head and a shaft sunk
 *   {@code depth} (default 24) blocks into the ground to a landing chamber</li>
 * </ul>
 */
public final class DeepLift implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("deep/lift"), ArchetypeCategory.UNDERGROUND, Set.of(JobType.HAULER), 0,
		9, 11, 9, 11, 12, 320, SettlementTier.VILLAGE, Set.of(), Set.of(ArchetypeInfo.SiteFlag.VERTICAL), List.of(), 2, 1.0);

	/** The shaft's ring of stair cells round the well, as offsets from its centre, in climbing order (clockwise from above). */
	private static final int[][] RING = {{-2, -2}, {-1, -2}, {0, -2}, {1, -2}, {2, -2}, {2, -1}, {2, 0}, {2, 1}, {2, 2}, {1, 2}, {0, 2}, {-1, 2}, {-2, 2},
		{-2, 1}, {-2, 0}, {-2, -1}};
	private static final int LANTERN_EVERY = 6;

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public TerrainProbe terrain(PlotSpec spec, TerrainProbe probe) {
		// the lift reads its two levels from its params; the probe only says which level the caller is looking at
		return probe;
	}

	/** The lift's levels and plan. */
	private static final class Plan {
		int w;
		int dp;
		int cx;
		int cz;
		/** Shaft walls. */
		int sx0;
		int sx1;
		int sz0;
		int sz1;
		/** Standing levels: the foot (realm floor or the chamber) and the head (the street). */
		int foot;
		int head;
		/** The vault's underside: the tower below it, the lined shaft through the rock above. */
		int vault;
		boolean realm;
		/** The lower door's side (local), or null for the hand-built pit's closed chamber. */
		Direction lower;
		/** Ring index of the landing at the foot (inside the lower door) and where the stair arrives at the head. */
		int i0;
		int rise;
		int top;
	}

	@Override
	public void draw(Draft d) {
		Plan p = plan(d);
		Glow g = Glow.of(DeepSite.of(d).character());
		d.body(p.sx0, p.sz0, p.sx1, p.sz1);
		d.eave(p.head + 4);

		boolean realmProbe = Math.abs(d.site().ground(p.cx - p.sx0 + p.sx0, p.cz) - p.foot) < Math.abs(d.site().ground(p.cx, p.cz) - p.head);
		shaft(d, p);
		stair(d, p);
		well(d, p);
		if (p.realm) {
			tower(d, p, g);
		}
		// doors: the level the caller sees first is the main entrance
		if (realmProbe && p.lower != null) {
			lowerDoor(d, p, g);
			headHouse(d, p);
		} else {
			headHouse(d, p);
			if (p.lower != null) {
				lowerDoor(d, p, g);
			}
		}
		footChamber(d, p);
	}

	private static Plan plan(Draft d) {
		Plan p = new Plan();
		p.w = d.frontage();
		p.dp = d.depth();
		p.cx = p.w / 2;
		p.cz = p.dp / 2;
		p.sx0 = p.cx - 3;
		p.sx1 = p.cx + 3;
		p.sz0 = p.cz - 3;
		p.sz1 = p.cz + 3;
		int floorY = d.spec().floorY();
		p.realm = d.hasParam("top_y");
		if (p.realm) {
			p.head = d.param("street_y", d.param("top_y", floorY + 24)) - floorY;
			p.foot = d.param("deep_street_y", d.param("bottom_y", floorY)) - floorY;
			p.head = Math.max(p.head, p.foot + 12);
			int v = d.hasParam("ceiling_y") ? d.param("ceiling_y", 0) - floorY : p.head - 16;
			p.vault = Math.clamp(v, p.foot + 8, p.head - 4);
			String face = d.param("deep_facing", d.spec().facing().getSerializedName());
			Direction world = Direction.byName(face);
			if (world == null || world.getAxis() == Direction.Axis.Y) {
				world = d.spec().facing();
			}
			p.lower = d.frame().toLocal(world);
		} else {
			p.head = 0;
			p.foot = -Math.max(8, d.param("depth", 24));
			p.vault = p.foot;
			p.lower = null;
		}
		p.rise = p.head - p.foot;
		// the headframe stands tall over the winding house's eaves (a pit head read from across the town)
		p.top = Math.min(d.maxHeight() - 1, p.head + 13);
		// the landing inside the lower door (or at the front for the pit's chamber)
		Direction side = p.lower == null ? Dirs.FRONT : p.lower;
		int[] in = {Dirs.dx(side) * 2, Dirs.dz(side) * 2};
		for (int i = 0; i < RING.length; i++) {
			if (RING[i][0] == in[0] && RING[i][1] == in[1]) {
				p.i0 = i;
			}
		}
		return p;
	}

	private static int[] ring(Plan p, int i) {
		int[] o = RING[Math.floorMod(i, RING.length)];
		return new int[]{p.cx + o[0], p.cz + o[1]};
	}

	// ------------------------------------------------------------------------------------------------- the shaft

	/**
	 * The shaft's walls from the foot to the head's eaves: rough lining through the rock above the vault (and all of the
	 * hand-built pit), dressed courses in the realm (the tower's own masonry is drawn by {@link #tower}); the floors at
	 * the foot and the head; the air inside.
	 */
	private static void shaft(Draft d, Plan p) {
		Mat rough = d.mat(Role.FOUNDATION);
		Mat floor = d.mat(Role.PAVING);
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int y = Math.max(p.vault, p.foot - 1); y < p.head - 1; y++) {
				ringWall(d, p, y, rough.full());
			}
		}
		// the floors: the foot (all of it: the well's floor under the cage too) and the head's landing round the well
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int x = p.sx0 + 1; x < p.sx1; x++) {
				for (int z = p.sz0 + 1; z < p.sz1; z++) {
					d.set(x, p.foot - 1, z, floor.full());
					boolean centre = x == p.cx && z == p.cz;
					if (!centre) {
						d.set(x, p.head - 1, z, d.mat(Role.FLOOR).full());
					}
				}
			}
		}
		d.carve(p.sx0 + 1, p.foot, p.sz0 + 1, p.sx1 - 1, p.head - 2, p.sz1 - 1);
		// the hatch the chain runs through at the head
		try (var s = d.stage(StageKind.FLOORS)) {
			d.set(p.cx, p.head - 1, p.cz, Blocks.AIR.defaultBlockState());
		}
	}

	private static void ringWall(Draft d, Plan p, int y, Paint paint) {
		for (int x = p.sx0; x <= p.sx1; x++) {
			d.set(x, y, p.sz0, paint);
			d.set(x, y, p.sz1, paint);
		}
		for (int z = p.sz0 + 1; z < p.sz1; z++) {
			d.set(p.sx0, y, z, paint);
			d.set(p.sx1, y, z, paint);
		}
	}

	/**
	 * The stair: from beside the landing at the foot, one step a cell round the ring, rising a block a cell to the head;
	 * a rail post on the well's side of every step; the head's floor opened over the last steps.
	 */
	private static void stair(Draft d, Plan p) {
		Mat stone = Rock.dressing(d);
		Mat rail = d.mat(Role.RAILING).has(Form.FENCE) ? d.mat(Role.RAILING) : d.mat(Role.FURNITURE);
		for (int k = 0; k < p.rise; k++) {
			int[] c = ring(p, p.i0 + 1 + k);
			int[] n = ring(p, p.i0 + 2 + k);
			Direction dir = Dirs.of(Integer.signum(n[0] - c[0]), Integer.signum(n[1] - c[1]));
			if (dir == null) {
				dir = Dirs.FRONT;
			}
			int y = p.foot + k;
			try (var s = d.stage(StageKind.FLOORS)) {
				d.set(c[0], y, c[1], stone.stairsOrSlab(dir, Half.BOTTOM));
				for (int hh = 1; hh <= 2; hh++) {
					if (y + hh <= p.head - 2) {
						d.set(c[0], y + hh, c[1], Blocks.AIR.defaultBlockState());
					}
				}
				// the head's floor opened over the last steps (headroom: a climber stepping up from a step four under the
				// floor rises into it), the last step itself in the floor's plane
				if (y >= p.head - 4 && y <= p.head - 2) {
					d.set(c[0], p.head - 1, c[1], Blocks.AIR.defaultBlockState());
				}
			}
			// a rail post on the well's side (not at the ring's corners), at hand height
			boolean corner = Math.abs(c[0] - p.cx) == 2 && Math.abs(c[1] - p.cz) == 2;
			int ix = Math.abs(c[0] - p.cx) == 2 ? c[0] - Integer.signum(c[0] - p.cx) : c[0];
			int iz = Math.abs(c[1] - p.cz) == 2 && !(Math.abs(c[0] - p.cx) == 2) ? c[1] - Integer.signum(c[1] - p.cz) : c[1];
			if (!corner && y + 1 < p.head - 1 && y + 1 > p.foot + 2) {
				try (var s = d.stage(StageKind.INTERIOR)) {
					d.set(ix, y + 1, iz, rail.fence());
				}
			}
		}
	}

	/**
	 * The well: the hoist's chain from the winding beam down the middle to the cage at the foot, a lantern strung on it
	 * every six blocks; the cage (corner posts, a slab roof, the chain made fast to it); the haulers' goods at the foot.
	 */
	private static void well(Draft d, Plan p) {
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		// its roof an upper slab two over the floor (a man stands in it), low enough that the steps beside it never
		// stand four over the floor without their rail
		int cageTop = p.foot + 2;
		// the cage
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int[] o : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
				for (int y = p.foot; y < cageTop; y++) {
					d.set(p.cx + o[0], y, p.cz + o[1], frame.fence());
				}
			}
			for (int x = p.cx - 1; x <= p.cx + 1; x++) {
				for (int z = p.cz - 1; z <= p.cz + 1; z++) {
					// an upper slab: head room under it for the hauler at the barrel, its top three over the floor
					d.set(x, cageTop, z, d.mat(Role.FURNITURE).slab(SlabType.TOP));
				}
			}
			Furniture.barrel(d, p.cx, p.foot, p.cz, Direction.UP);
		}
		// the winding beam across the head, the chain and its lanterns down to the cage
		int beamY = p.head + 3;
		try (var s = d.stage(StageKind.FRAME)) {
			for (int x = p.sx0 + 1; x < p.sx1; x++) {
				d.set(x, beamY, p.cz, beam.log(Direction.Axis.X));
			}
		}
		Block lantern = Blocks.LANTERN;
		try (var s = d.stage(StageKind.INTERIOR)) {
			int k = 0;
			for (int y = beamY - 1; y > cageTop; y--) {
				boolean lamp = k % LANTERN_EVERY == LANTERN_EVERY - 1 && y < p.head - 1;
				d.set(p.cx, y, p.cz, lamp ? States.lantern(lantern, true) : States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
				k++;
			}
		}
		// the haulers' goods at the foot, round the landing on the side the stair leaves free
		int[] job = ring(p, p.i0 - 2);
		int[] goods = ring(p, p.i0 - 3);
		int[] chest = ring(p, p.i0 - 5);
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(goods[0], p.foot, goods[1], States.facing(Blocks.BARREL, Direction.UP), BlockEntities.container(List.of(new Object[]{"minecraft:raw_iron", 12},
				new Object[]{"minecraft:coal", 16})));
		}
		d.poi(PoiType.JOB_SITE, job[0], p.foot, job[1], Dirs.FRONT, JobType.HAULER);
		// the chest faces along the ring toward the landing, so its front is a stair-free cell of the ring
		int[] before = ring(p, p.i0 - 4);
		Direction along = Dirs.of(Integer.signum(before[0] - chest[0]), Integer.signum(before[1] - chest[1]));
		Furniture.chest(d, chest[0], p.foot, chest[1], along == null ? wallward(p, chest).getOpposite() : along,
			List.of(new Object[]{"minecraft:bread", 8}, new Object[]{"minecraft:torch", 16}));
	}

	/** The way out of the ring toward the nearer shaft wall from a ring cell. */
	private static Direction wallward(Plan p, int[] c) {
		int dx = c[0] - p.cx;
		int dz = c[1] - p.cz;
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx > 0 ? Dirs.RIGHT : Dirs.LEFT;
		}
		return dz > 0 ? Dirs.BACK : Dirs.FRONT;
	}

	// ------------------------------------------------------------------------------------------------- the tower

	/**
	 * The shaft standing in the realm as a tower: its walls in the realm's dressed stone with quoins, a string course every
	 * eight blocks, slit windows climbing each face; a stepped plinth round its foot, lamp posts of the realm's glow at
	 * the plinth's corners, a ring of corbels where it meets the vault.
	 */
	private static void tower(Draft d, Plan p, Glow g) {
		Mat wall = d.mat(Role.WALL);
		Mat dress = Rock.dressing(d);
		Mat plinth = d.mat(Role.PLINTH);
		int top = Math.min(p.vault, p.head - 1);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int y = p.foot - 1; y < top; y++) {
				int ry = y - p.foot;
				boolean course = ry > 2 && ry % 8 == 0;
				for (int x = p.sx0; x <= p.sx1; x++) {
					for (int z = p.sz0; z <= p.sz1; z++) {
						boolean edge = x == p.sx0 || x == p.sx1 || z == p.sz0 || z == p.sz1;
						if (!edge) {
							continue;
						}
						boolean corner = (x == p.sx0 || x == p.sx1) && (z == p.sz0 || z == p.sz1);
						boolean mid = course && (x == p.cx || z == p.cz);
						Paint paint = y <= p.foot ? plinth.full() : mid && ry % 16 == 8 ? Paint.of(g.accent) : course || corner ? dress.full() : wall.full();
						d.set(x, y, z, paint);
					}
				}
			}
		}
		// slits up each face, staggered face to face, glowing from the lanterns in the well, a sill under each
		for (int f = 0; f < 4; f++) {
			Direction face = Dirs.HORIZONTAL[f];
			for (int y = p.foot + 4 + f * 2; y < top - 4; y += 8) {
				int[] c = faceCell(p, face, (f & 1) == 0 ? -1 : 1);
				if ((y - p.foot) % 8 == 0) {
					continue;
				}
				try (var s = d.stage(StageKind.OPENINGS)) {
					for (int k = 0; k < 3; k++) {
						d.set(c[0], y + k, c[1], d.mat(Role.GLASS).pane());
					}
				}
				int ox = c[0] + Dirs.dx(face);
				int oz = c[1] + Dirs.dz(face);
				if (Rock.inPlot(d, ox, oz)) {
					try (var s = d.stage(StageKind.WALLS)) {
						d.set(ox, y - 1, oz, dress.slab(SlabType.TOP));
					}
				}
			}
		}
		// lanterns on brackets at the string courses, turning round the tower as it climbs
		for (int ry = 8, f = 0; p.foot + ry + 3 < top; ry += 8, f++) {
			Direction face = Dirs.HORIZONTAL[f % 4];
			int[] c = faceCell(p, face, 0);
			int ox = c[0] + Dirs.dx(face);
			int oz = c[1] + Dirs.dz(face);
			if (Rock.inPlot(d, ox, oz) && d.isAbsent(ox, p.foot + ry + 1, oz) && d.isAbsent(ox, p.foot + ry + 2, oz)) {
				Lighting.sconce(d, ox, p.foot + ry + 1, oz, face.getOpposite());
			}
		}
		// the stepped plinth round the foot, on the ring outside the shaft
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = p.sx0 - 1; x <= p.sx1 + 1; x++) {
				for (int z = p.sz0 - 1; z <= p.sz1 + 1; z++) {
					boolean outer = x == p.sx0 - 1 || x == p.sx1 + 1 || z == p.sz0 - 1 || z == p.sz1 + 1;
					if (!outer || !Rock.inPlot(d, x, z)) {
						continue;
					}
					if (doorFront(p, x, z)) {
						continue;
					}
					Direction out = outward(p, x, z);
					d.set(x, p.foot - 1, z, plinth.full());
					d.set(x, p.foot, z, out == null ? plinth.full() : dress.stairsOrSlab(out.getOpposite(), Half.BOTTOM));
				}
			}
			// the corbels under the vault: two courses stepping out from the shaft
			if (p.vault < p.head - 1 && p.vault - 2 > p.foot + 6) {
				for (int x = p.sx0 - 1; x <= p.sx1 + 1; x++) {
					for (int z = p.sz0 - 1; z <= p.sz1 + 1; z++) {
						boolean outer = x == p.sx0 - 1 || x == p.sx1 + 1 || z == p.sz0 - 1 || z == p.sz1 + 1;
						if (!outer || !Rock.inPlot(d, x, z)) {
							continue;
						}
						Direction out = outward(p, x, z);
						d.set(x, p.vault - 2, z, out == null ? dress.full() : dress.stairsOrSlab(out.getOpposite(), Half.TOP));
						d.set(x, p.vault - 1, z, wall.full());
					}
				}
			}
		}
		// lamp posts of the realm's glow at the plinth's front corners
		for (int[] c : new int[][]{{p.sx0 - 1, p.sz0 - 1}, {p.sx1 + 1, p.sz0 - 1}, {p.sx0 - 1, p.sz1 + 1}, {p.sx1 + 1, p.sz1 + 1}}) {
			if (Rock.inPlot(d, c[0], c[1]) && d.isAbsent(c[0], p.foot + 1, c[1])) {
				Rock.lampPost(d, c[0], p.foot + 1, c[1], true, g);
			}
		}
	}

	/** A cell of a shaft face, {@code k} cells from its middle along it (seen from outside). */
	private static int[] faceCell(Plan p, Direction face, int k) {
		Direction along = Dirs.right(face.getOpposite());
		int mx = p.cx + Dirs.dx(face) * 3;
		int mz = p.cz + Dirs.dz(face) * 3;
		return new int[]{mx + Dirs.dx(along) * k, mz + Dirs.dz(along) * k};
	}

	/** The outward direction of a cell of the ring outside the shaft (null at its corners). */
	private static Direction outward(Plan p, int x, int z) {
		boolean l = x < p.sx0;
		boolean r = x > p.sx1;
		boolean f = z < p.sz0;
		boolean b = z > p.sz1;
		if ((l || r) && (f || b)) {
			return null;
		}
		return l ? Dirs.LEFT : r ? Dirs.RIGHT : f ? Dirs.FRONT : b ? Dirs.BACK : null;
	}

	/** Whether a cell is the one before the lower door (kept clear of the plinth). */
	private static boolean doorFront(Plan p, int x, int z) {
		if (p.lower == null) {
			return false;
		}
		int[] c = {p.cx + Dirs.dx(p.lower) * 4, p.cz + Dirs.dz(p.lower) * 4};
		return Math.abs(x - c[0]) + Math.abs(z - c[1]) <= 1 && (p.lower.getAxis() == Direction.Axis.X ? x == c[0] : z == c[1]);
	}

	/** The lower door onto the deep square: an arched head with the realm's glow in its keystone, a lamp beside it. */
	private static void lowerDoor(Draft d, Plan p, Glow g) {
		Direction out = p.lower;
		int x = p.cx + Dirs.dx(out) * 3;
		int z = p.cz + Dirs.dz(out) * 3;
		int y = p.foot;
		d.air(x, y, z);
		d.air(x, y + 1, z);
		Rock.archedDoorHead(d, x, y, z, out, true, g);
		Block door = d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR);
		d.inStage(StageKind.OPENINGS, () -> d.door(x, y, z, door, out, DoorHingeSide.LEFT));
		int ex = x + Dirs.dx(out);
		int ez = z + Dirs.dz(out);
		d.entrance(ex, y, ez);
		d.poi(PoiType.DOOR, ex, y, ez, out.getOpposite());
		// the way out to the square cut level through any rise of the floor, paved, out to the plot's edge
		for (int k = 1; Rock.inPlot(d, x + Dirs.dx(out) * k, z + Dirs.dz(out) * k); k++) {
			int wx = x + Dirs.dx(out) * k;
			int wz = z + Dirs.dz(out) * k;
			try (var s = d.stage(StageKind.EXTERIOR)) {
				d.set(wx, y - 1, wz, d.mat(Role.PAVING).full());
			}
			for (int hh = 0; hh <= 2; hh++) {
				if (d.isAbsent(wx, y + hh, wz)) {
					d.air(wx, y + hh, wz);
				}
			}
		}
		if (Rock.inPlot(d, ex, ez)) {
			Direction along = Dirs.right(out.getOpposite());
			int lx = ex + Dirs.dx(along) * 2;
			int lz = ez + Dirs.dz(along) * 2;
			if (Rock.inPlot(d, lx, lz) && d.isAbsent(lx, y + 2, lz)) {
				Lighting.doorLamp(d, lx, y + 2, lz, out.getOpposite());
			}
		}
	}

	// ------------------------------------------------------------------------------------------------- the head

	/**
	 * The winding house on the street: walls of the surface town's own finish on a stone base, a door to the street, a
	 * window a side, a roof of the town's own covering; the headframe's legs rising through it to the sheave; the yard
	 * round it paved with a lamp; the windlass inside and the haulers' place there. Nothing from the realm shows here.
	 */
	private static void headHouse(Draft d, Plan p) {
		Mat stone = d.mat(Role.FOUNDATION);
		Paint home = DeepManner.home(d, Role.WALL).orElse(d.mat(Role.WALL).full());
		Paint quoin = DeepManner.home(d, Role.WALL_ACCENT).orElse(Rock.dressing(d).full());
		int h = p.head;
		int eave = h + 4;
		try (var s = d.stage(StageKind.WALLS)) {
			for (int y = h - 1; y < eave; y++) {
				for (int x = p.sx0; x <= p.sx1; x++) {
					for (int z = p.sz0; z <= p.sz1; z++) {
						boolean edge = x == p.sx0 || x == p.sx1 || z == p.sz0 || z == p.sz1;
						if (!edge) {
							continue;
						}
						boolean corner = (x == p.sx0 || x == p.sx1) && (z == p.sz0 || z == p.sz1);
						d.set(x, y, z, y <= h ? stone.full() : corner ? quoin : home);
					}
				}
			}
		}
		d.carve(p.sx0 + 1, h, p.sz0 + 1, p.sx1 - 1, eave - 1, p.sz1 - 1);
		// the yard round it at the street's level
		Mat paving = d.mat(Role.PATH).has(Form.FULL) ? d.mat(Role.PATH) : d.mat(Role.PAVING);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int x = 0; x < p.w; x++) {
				for (int z = 0; z < p.dp; z++) {
					if (x >= p.sx0 && x <= p.sx1 && z >= p.sz0 && z <= p.sz1) {
						continue;
					}
					d.set(x, h - 1, z, d.mat(Role.PAVING).full());
				}
			}
		}
		// the yard cut level into any rise of the ground round the head
		for (int x = 0; x < p.w; x++) {
			for (int z = 0; z < p.dp; z++) {
				if (x >= p.sx0 && x <= p.sx1 && z >= p.sz0 && z <= p.sz1) {
					continue;
				}
				for (int y = h; y <= h + 3; y++) {
					if (d.isAbsent(x, y, z) && d.site().ground(x, z) >= y) {
						d.air(x, y, z);
					}
				}
			}
		}
		// the street door and windows
		Block door = d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR);
		int dx = p.cx;
		int dz = p.sz0;
		d.air(dx, h, dz);
		d.air(dx, h + 1, dz);
		d.inStage(StageKind.OPENINGS, () -> d.door(dx, h, dz, door, Dirs.FRONT, DoorHingeSide.LEFT));
		try (var s = d.stage(StageKind.WALLS)) {
			d.set(dx, h + 2, dz, d.mat(Role.BEAM).log(Direction.Axis.X));
		}
		d.entrance(dx, h, dz - 1);
		d.poi(PoiType.DOOR, dx, h, dz - 1, Dirs.BACK);
		if (dz - 1 >= 0 && d.isAbsent(dx + 2, h + 2, dz - 1)) {
			Lighting.doorLamp(d, dx + 2, h + 2, dz - 1, Dirs.BACK);
		}
		for (Direction face : new Direction[]{Dirs.LEFT, Dirs.RIGHT, Dirs.BACK}) {
			int[] c = faceCell(p, face, 0);
			Rock.window(d, c[0], h + 1, c[1], face, 1, 2, false);
		}
		// the roof of the town's own covering over the winding house, the headframe through it
		Mat sheet = d.mat(Role.SHEET).has(Form.STAIRS) ? d.mat(Role.SHEET) : d.mat(Role.ROOF);
		Roofs.Field f = Roofs.hip(p.sx0 - 1, p.sz0 - 1, p.sx1 + 1, p.sz1 + 1, Roofs.Profile.straight(0.5));
		Roofs.Roof roof = Roofs.raster(d, f, eave, Math.max(0, p.sx0 - 1), Math.max(0, p.sz0 - 1), Math.min(p.w - 1, p.sx1 + 1), Math.min(p.dp - 1,
			p.sz1 + 1), sheet, d.mat(Role.ROOF_TRIM), Habits.Ridge.SLAB);
		if (!DeepManner.thatches(d)) {
			homeRoof(d, p, eave, roof);
		}
		headframe(d, p, eave);
		// the windlass over the hatch, its crank, the haulers' place beside it, light
		int[] job = ring(p, p.i0 + p.rise + 3);
		try (var s = d.stage(StageKind.INTERIOR)) {
			// the windlass a step back from the hatch, the hatch railed round on all four sides (nobody climbs the
			// windlass to stand over the drop)
			d.set(p.cx - 2, h, p.cz, States.attached(Blocks.GRINDSTONE, AttachFace.FLOOR, Dirs.RIGHT));
			Mat rail = d.mat(Role.RAILING).has(Form.FENCE) ? d.mat(Role.RAILING) : d.mat(Role.FURNITURE);
			for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				if (d.isEmpty(p.cx + o[0], h, p.cz + o[1])) {
					d.set(p.cx + o[0], h, p.cz + o[1], rail.fence());
				}
			}
		}
		d.poi(PoiType.JOB_SITE, job[0], h, job[1], Dirs.FRONT, JobType.HAULER);
		// lanterns hung from the winding beam either side of the hatch
		Lighting.chandelier(d, p.cx + 2, p.cz, h + 3, h + 2);
		Lighting.chandelier(d, p.cx - 2, p.cz, h + 3, h + 2);
		// a lamp in the yard's corners and a bench by the door
		for (int[] c : new int[][]{{0, 0}, {p.w - 1, 0}, {0, p.dp - 1}, {p.w - 1, p.dp - 1}}) {
			if (d.isAbsent(c[0], h, c[1])) {
				Lighting.post(d, c[0], h, c[1]);
			}
		}
		if (p.sz0 - 1 >= 0 && dx - 2 >= 0) {
			Rock.bench(d, dx - 2, h, p.sz0 - 1, Dirs.BACK, Dirs.LEFT, 1);
		}
	}

	/** Restates the winding house's roof in the surface town's own covering where it has the forms. */
	private static void homeRoof(Draft d, Plan p, int eave, Roofs.Roof roof) {
		Paint stairsN = DeepManner.home(d, Role.ROOF, Form.STAIRS, s -> s);
		Paint slab = DeepManner.home(d, Role.ROOF, Form.SLAB, s -> s);
		Paint full = DeepManner.home(d, Role.ROOF, Form.FULL, s -> s);
		for (int x = 0; x < p.w; x++) {
			for (int z = 0; z < p.dp; z++) {
				for (int y = eave; y <= eave + 6; y++) {
					BlockState s = d.get(x, y, z);
					if (s == null || !roof.covers(x, z)) {
						continue;
					}
					BlockState n = null;
					if (s.getBlock() instanceof net.minecraft.world.level.block.StairBlock) {
						BlockState b = stairsN.at(x, y, z);
						n = b == null ? null : b.getBlock().withPropertiesOf(s);
					} else if (s.getBlock() instanceof net.minecraft.world.level.block.SlabBlock) {
						BlockState b = slab.at(x, y, z);
						n = b == null ? null : b.getBlock().withPropertiesOf(s);
					} else if (s.isSolidRender()) {
						n = full.at(x, y, z);
					}
					if (n != null) {
						d.restate(x, y, z, n);
					}
				}
			}
		}
	}

	/**
	 * The headframe: four legs of the town's timber from the eaves, braced, a crown of beams, and the sheave (a wheel on
	 * its axle) over the hatch, the chain dropping from it through the roof into the well.
	 */
	private static void headframe(Draft d, Plan p, int eave) {
		Paint leg = DeepManner.home(d, Role.FRAME, Form.LOG, s -> s.trySetValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,
			Direction.Axis.Y)).orElse(d.mat(Role.FRAME).log(Direction.Axis.Y));
		Mat beam = d.mat(Role.BEAM);
		int crown = p.top - 1;
		if (crown - eave < 3) {
			return;
		}
		int[][] legs = {{p.cx - 2, p.cz - 2}, {p.cx + 2, p.cz - 2}, {p.cx - 2, p.cz + 2}, {p.cx + 2, p.cz + 2}};
		try (var s = d.stage(StageKind.FRAME)) {
			for (int[] l : legs) {
				for (int y = eave; y < crown; y++) {
					d.set(l[0], y, l[1], leg);
				}
			}
			for (int x = p.cx - 2; x <= p.cx + 2; x++) {
				d.set(x, crown, p.cz - 2, beam.log(Direction.Axis.X));
				d.set(x, crown, p.cz + 2, beam.log(Direction.Axis.X));
			}
			for (int z = p.cz - 1; z <= p.cz + 1; z++) {
				d.set(p.cx - 2, crown, z, beam.log(Direction.Axis.Z));
				d.set(p.cx + 2, crown, z, beam.log(Direction.Axis.Z));
			}
			// the sheave: a wheel standing over the hatch on an axle across the crown, braces from the legs
			for (int z = p.cz - 1; z <= p.cz + 1; z++) {
				d.set(p.cx, crown, z, beam.log(Direction.Axis.Z));
			}
			if (crown + 2 <= p.top) {
				for (int[] o : new int[][]{{-1, 0}, {1, 0}, {-1, 1}, {1, 1}, {-1, 2}, {0, 2}, {1, 2}}) {
					d.set(p.cx + o[0], crown + o[1], p.cz, beam.log(Direction.Axis.Z));
				}
				d.set(p.cx, crown + 1, p.cz, Blocks.IRON_BARS.defaultBlockState());
			}
			Mat brace = d.mat(Role.FRAME).has(Form.FENCE) ? d.mat(Role.FRAME) : d.mat(Role.FURNITURE);
			for (int[] l : legs) {
				int bx = l[0] + Integer.signum(p.cx - l[0]);
				d.set(bx, crown - 1, l[1], brace.stairsOrSlab(Integer.signum(p.cx - l[0]) > 0 ? Dirs.LEFT : Dirs.RIGHT, Half.TOP));
				int bz = l[1] + Integer.signum(p.cz - l[1]);
				d.set(l[0], crown - 1, bz, brace.stairsOrSlab(Integer.signum(p.cz - l[1]) > 0 ? Dirs.FRONT : Dirs.BACK, Half.TOP));
			}
			// a girt round the legs halfway up, with knee braces under it, where the frame stands tall enough: a trestle,
			// not four poles
			int girt = eave + (crown - eave) / 2;
			if (crown - eave >= 6 && girt > eave + 1) {
				for (int x = p.cx - 1; x <= p.cx + 1; x++) {
					for (int z : new int[]{p.cz - 2, p.cz + 2}) {
						if (d.isEmpty(x, girt, z)) {
							d.set(x, girt, z, beam.log(Direction.Axis.X));
						}
					}
				}
				for (int z = p.cz - 1; z <= p.cz + 1; z++) {
					for (int x : new int[]{p.cx - 2, p.cx + 2}) {
						if (d.isEmpty(x, girt, z)) {
							d.set(x, girt, z, beam.log(Direction.Axis.Z));
						}
					}
				}
				for (int[] l : legs) {
					int bx = l[0] + Integer.signum(p.cx - l[0]);
					if (d.isEmpty(bx, girt - 1, l[1])) {
						d.set(bx, girt - 1, l[1], brace.stairsOrSlab(Integer.signum(p.cx - l[0]) > 0 ? Dirs.LEFT : Dirs.RIGHT, Half.TOP));
					}
				}
			}
		}
		// the chain from the axle down through the roof to the winding beam
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int y = crown - 1; y > p.head + 3; y--) {
				d.set(p.cx, y, p.cz, States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
			}
		}
	}

	/** The pit's chamber at the foot (built by hand, no realm below): a lamp on the wall, a bench, the shaft's end. */
	private static void footChamber(Draft d, Plan p) {
		if (p.lower != null) {
			return;
		}
		int[] c = ring(p, p.i0);
		Lighting.sconce(d, c[0], p.foot + 2, c[1], wallward(p, c));
	}
}
