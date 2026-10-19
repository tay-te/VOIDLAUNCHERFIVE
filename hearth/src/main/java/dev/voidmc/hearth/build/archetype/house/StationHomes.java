package dev.voidmc.hearth.build.archetype.house;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.station.Mending;
import dev.voidmc.hearth.build.kit.townhome.Rooflines;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Site;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * What the homes by station (docs/LIVING_WORLD.md §8.1) share: roofs fitted under the plot's height, low walls for the
 * poor's huts in the culture's construction (mended where the household can afford no better), raised floors over water
 * or by habit, and earthen floors. Each archetype composes these with its own character.
 */
final class StationHomes {
	private StationHomes() {
	}

	/** Pitches tried for a roof, steepest first. */
	static final double[] PITCHES = {2.0, 1.5, 1.25, 1.0, 0.75, 0.5};

	/**
	 * The steepest pitch no steeper than {@code want} whose roof over the walls stays {@code spare} blocks under the
	 * plot's height (a dry run of the real plan). Falls back to the shallowest.
	 */
	static double fitPitch(Draft d, int x0, int z0, int x1, int z1, int plate, RoofForm form, Direction.Axis axis, Roofs.Opts o, boolean pl, boolean pr,
		double want, int spare) {
		double last = 0.5;
		for (double p : PITCHES) {
			if (p > want + 1e-9) {
				continue;
			}
			last = p;
			int ridge = Rooflines.ridgeY(d, x0, z0, x1, z1, plate, form, axis, o.withPitch(p), pl, pr);
			if (ridge + spare < d.maxHeight()) {
				return p;
			}
		}
		return last;
	}

	/**
	 * Whether a roof plan fits: its ridge {@code spare} under the plot's height at the given pitch.
	 */
	static boolean fits(Draft d, int x0, int z0, int x1, int z1, int plate, RoofForm form, Direction.Axis axis, Roofs.Opts o, boolean pl, boolean pr,
		int spare) {
		return Rooflines.ridgeY(d, x0, z0, x1, z1, plate, form, axis, o, pl, pr) + spare < d.maxHeight();
	}

	/** Whether this building stands raised on posts: water under its footprint or a culture that always builds raised. */
	static boolean raised(Draft d, int x0, int z0, int x1, int z1) {
		return d.habits().plan().stilts() == Habits.Stilts.ALWAYS || d.site().anyWater(x0, z0, x1, z1)
			&& d.habits().plan().stilts() != Habits.Stilts.NEVER || d.site().anyWater(x0, z0, x1, z1);
	}

	/**
	 * The standing level of a raised floor over a rectangle: clear of the ground by the culture's stilt height and of
	 * the water by two, never more than {@code cap} above the street (a stair has to reach it).
	 */
	static int raisedBase(Draft d, int x0, int z0, int x1, int z1, int cap) {
		int stiltH = Math.max(1, d.habits().plan().stiltHeight());
		int base = 0;
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				int wy = d.site().water(x, z);
				base = Math.max(base, Math.max(d.site().ground(x, z) + stiltH + 1, wy == Site.NONE ? 0 : wy + 2));
			}
		}
		return Math.min(base, cap);
	}

	/**
	 * An earthen floor of beaten clay at block row y over a rectangle (the floor of the poor): coarse earth and packed
	 * mud, trodden to a path where folk walk most, over whatever was dug or filled under it.
	 */
	static void earthFloor(Draft d, int x0, int z0, int x1, int z1, int y) {
		Paint earth = (x, yy, z) -> {
			double u = dev.voidmc.hearth.build.draft.Noise.value(d.seed() ^ 0xEA27, x * 0.6, 0, z * 0.6);
			return (u < 0.4 ? Blocks.COARSE_DIRT : u < 0.75 ? Blocks.PACKED_MUD : Blocks.DIRT_PATH).defaultBlockState();
		};
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
					if (d.isEmpty(x, y, z) || d.get(x, y, z).is(Blocks.DIRT) || d.get(x, y, z).is(Blocks.GRASS_BLOCK)) {
						d.set(x, y, z, earth);
					}
				}
			}
		}
	}

	/**
	 * Low walls round a body's single storey in the culture's construction, as the poor build them: a course of
	 * fieldstone at the foot, corner posts and a mid post on longer walls where the culture frames, a wall plate of timber
	 * at the head, and infill mended over about {@code mend} of it. Log walls are notched at the corners, stone and
	 * adobe walls have none of the timber. Party walls (a neighbour's) are solid and plain.
	 */
	static void lowWalls(Draft d, Body b, double mend) {
		WallKind kind = d.habits().walls().kind();
		int ya = b.baseY;
		int yg = b.plateY();
		Mat wall = d.mat(Role.WALL);
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat found = d.mat(Role.FOUNDATION);
		Mat gable = d.mat(Role.GABLE);
		Paint[] patches = Mending.patchesFor(d, kind);
		boolean framed = kind == WallKind.TIMBER_FRAME || kind == WallKind.PLASTER || kind == WallKind.PLANK || kind == WallKind.BAMBOO;
		for (Side s : b.sides(0)) {
			boolean party = b.isParty(s.out());
			Direction.Axis axis = s.axis();
			Paint infill = switch (kind) {
				case LOG -> wall.log(axis);
				case PLANK -> gable.full();
				default -> wall.full();
			};
			Paint mended = party ? infill : Mending.mended(d, infill, s.out().ordinal(), mend, patches);
			int mid = s.length() >= 6 ? s.middle() + (s.length() % 2 == 0 ? 1 : 0) : -1;
			try (var st = d.stage(StageKind.WALLS)) {
				for (int i = 0; i < s.length(); i++) {
					int x = s.x(i);
					int z = s.z(i);
					boolean corner = s.isCorner(i);
					for (int y = ya; y <= yg; y++) {
						Paint p;
						if (kind == WallKind.LOG) {
							p = corner ? frame.log((y & 1) == 0 ? axis : other(axis)) : y == ya ? found.full() : mended;
						} else if (kind == WallKind.STONE || kind == WallKind.ADOBE || kind == WallKind.BRICK) {
							p = y == ya && kind != WallKind.ADOBE ? found.full() : mended;
						} else if (corner || i == mid && !party) {
							p = y == ya ? found.full() : frame.log(Direction.Axis.Y);
						} else if (y == ya) {
							p = found.full();
						} else if (y == yg && framed) {
							p = beam.log(axis);
						} else {
							p = mended;
						}
						d.set(x, y, z, p);
					}
					// log corners run past the wall on alternate courses
					if (kind == WallKind.LOG && corner) {
						int ox = s.x(i) + (i == 0 ? -Dirs.dx(s.along()) : Dirs.dx(s.along()));
						int oz = s.z(i) + (i == 0 ? -Dirs.dz(s.along()) : Dirs.dz(s.along()));
						if (ox >= 0 && oz >= 0 && ox < d.frontage() && oz < d.depth()) {
							for (int y = ya + 1; y <= yg; y++) {
								if ((y & 1) == 0 && d.isEmpty(ox, y, oz)) {
									d.set(ox, y, oz, frame.log(axis));
								}
							}
						}
					}
				}
			}
		}
	}

	static Direction.Axis other(Direction.Axis a) {
		return a == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
	}

	/**
	 * Restates the gable ends of a pitched roof over walls {@code r} (x0, z0, x1, z1; plate at {@code plate}) in the
	 * given paint: every wall cell above the plate and under the roof on the two walls the ridge runs into.
	 */
	static void gableFill(Draft d, Roofs.Roof roof, Direction.Axis ridge, int[] r, int plate, Paint p) {
		boolean alongX = ridge == Direction.Axis.X;
		try (var s = d.stage(StageKind.WALLS)) {
			for (Side side : Side.of(r[0], r[1], r[2], r[3])) {
				if (side.axis() == ridge) {
					continue;
				}
				for (int i = 0; i < side.length(); i++) {
					int x = side.x(i);
					int z = side.z(i);
					if (!roof.covers(x, z)) {
						continue;
					}
					for (int y = plate + 1; y < roof.bottomY(x, z); y++) {
						if (d.isEmpty(x, y, z) || Openings.isWallBlock(d, x, y, z)) {
							d.set(x, y, z, p);
						}
					}
				}
			}
		}
		if (alongX) {
			return;
		}
	}

	/**
	 * The field of a hut's roof over walls {@code [x0..x1] × [z0..z1]}: {@code eaves} rows of overhang all round laid at
	 * half pitch (a kick at the foot, so a steep roof keeps its eave up at the wall head instead of dropping it to the
	 * doorway), then {@code pitch} up to the ridge along {@code axis}.
	 */
	static Roofs.Field hutField(int x0, int z0, int x1, int z1, RoofForm form, Direction.Axis axis, double pitch, int eaves) {
		return hutField(x0, z0, x1, z1, form, axis, pitch, eaves, null, 0);
	}

	/**
	 * The field of a hut's roof over walls {@code [x0..x1] × [z0..z1]}, in units over {@code plate - 1} (raster it from
	 * there): {@code eaves} rows of overhang all round, the eave row's stair at the wall head ({@code plate}) whatever the
	 * pitch, rising at {@code pitch} to the ridge along {@code axis}. With {@code lean} (a side the ridge runs parallel
	 * to), the slope on that side runs on past the eave at half pitch over {@code leanRows} more rows: a catslide over a
	 * lean-to. Pitches of 2, 1 and 0.5 lay clean (stair and block, stairs, stair and slab).
	 */
	static Roofs.Field hutField(int x0, int z0, int x1, int z1, RoofForm form, Direction.Axis axis, double pitch, int eaves,
		@org.jspecify.annotations.Nullable Direction lean, int leanRows) {
		Roofs.Profile p = dd -> 2 + pitch * dd;
		int nx0 = x0 - eaves;
		int nx1 = x1 + eaves;
		int nz0 = z0 - eaves;
		int nz1 = z1 + eaves;
		Roofs.Field main = switch (form) {
			case HIP, PAGODA, MANSARD -> Roofs.hip(nx0, nz0, nx1, nz1, p);
			case HALF_HIP -> Roofs.halfHip(nx0, nz0, nx1, nz1, axis, p, Math.max(1, ((axis == Direction.Axis.X ? nz1 - nz0 : nx1 - nx0) / 2) - 1));
			case SHED -> Roofs.shed(nx0, nz0, nx1, nz1, axis == Direction.Axis.X ? Dirs.BACK : Dirs.RIGHT, p);
			default -> Roofs.gable(nx0, nz0, nx1, nz1, axis, p);
		};
		if (lean == null || leanRows <= 0) {
			return main;
		}
		// the catslide: beyond the eave on the lean side, half a block lower a row
		int lx0 = lean == Dirs.LEFT ? nx0 - leanRows : lean == Dirs.RIGHT ? nx1 + 1 : nx0;
		int lx1 = lean == Dirs.LEFT ? nx0 - 1 : lean == Dirs.RIGHT ? nx1 + leanRows : nx1;
		int lz0 = lean == Dirs.FRONT ? nz0 - leanRows : lean == Dirs.BACK ? nz1 + 1 : nz0;
		int lz1 = lean == Dirs.FRONT ? nz0 - 1 : lean == Dirs.BACK ? nz1 + leanRows : nz1;
		Roofs.Field cat = (x, z) -> {
			if (x < lx0 || x > lx1 || z < lz0 || z > lz1) {
				return Double.NaN;
			}
			int beyond = switch (lean) {
				case WEST -> nx0 - x;
				case EAST -> x - nx1;
				case SOUTH -> nz0 - z;
				default -> z - nz1;
			};
			return 2 - 0.5 * beyond;
		};
		return main.max(cat);
	}

	/** The y the hut field's surface reaches at its highest (its ridge), without drawing. */
	static int hutRidge(Draft d, int x0, int z0, int x1, int z1, int plate, RoofForm form, Direction.Axis axis, double pitch, int eaves) {
		Roofs.Field f = hutField(x0, z0, x1, z1, form, axis, pitch, eaves);
		double top = 0;
		for (int z = Math.max(-1, z0 - eaves); z <= Math.min(d.depth() - 1, z1 + eaves); z++) {
			for (int x = Math.max(0, x0 - eaves); x <= Math.min(d.frontage() - 1, x1 + eaves); x++) {
				double h = f.h(x, z);
				if (!Double.isNaN(h)) {
					top = Math.max(top, h);
				}
			}
		}
		int t = Math.max(1, (int) Math.round(top * 2));
		return plate - 1 + (t + 1) / 2 - 1;
	}

	/** Pitches that lay clean in blocks, steepest first. */
	static final double[] CLEAN = {2.0, 1.0, 0.5};

	/** The steepest clean pitch up to {@code want} whose hut roof stays {@code spare} under the plot's height. */
	static double hutPitch(Draft d, int x0, int z0, int x1, int z1, int plate, RoofForm form, Direction.Axis axis, double want, int eaves, int spare) {
		double last = 0.5;
		for (double p : CLEAN) {
			if (p > want + 1e-9) {
				continue;
			}
			last = p;
			if (hutRidge(d, x0, z0, x1, z1, plate, form, axis, p, eaves) + spare < d.maxHeight()) {
				return p;
			}
		}
		return last;
	}

	/**
	 * A hut's roof over walls {@code [x0..x1] × [z0..z1]} whose head course is {@code plate}: the {@link #hutField}
	 * rasterised in {@code cover} with its eave row at the wall head, kept inside the plot (no overhang into the street),
	 * optionally running on as a catslide over a lean-to on side {@code lean}. The gable ends are not filled: callers
	 * fill them in their own walling ({@link #gableFill}). Returns the roof.
	 */
	static Roofs.Roof hutRoof(Draft d, int x0, int z0, int x1, int z1, int plate, RoofForm form, Direction.Axis axis, double pitch, int eaves, Mat cover,
		@org.jspecify.annotations.Nullable Direction lean, int leanRows) {
		Roofs.Field f = hutField(x0, z0, x1, z1, form, axis, pitch, eaves, lean, leanRows);
		int rx0 = Math.max(0, x0 - eaves - (lean == Dirs.LEFT ? leanRows : 0));
		int rx1 = Math.min(d.frontage() - 1, x1 + eaves + (lean == Dirs.RIGHT ? leanRows : 0));
		int rz0 = Math.max(0, z0 - eaves - (lean == Dirs.FRONT ? leanRows : 0));
		int rz1 = Math.min(d.depth() - 1, z1 + eaves + (lean == Dirs.BACK ? leanRows : 0));
		try (var s = d.stage(StageKind.ROOF)) {
			Mat trim = cover.main(Form.FULL) == Blocks.HAY_BLOCK || !d.mat(Role.ROOF_TRIM).has(Form.SLAB) ? cover : d.mat(Role.ROOF_TRIM);
			return Roofs.raster(d, f, plate - 1, rx0, rz0, rx1, rz1, cover, trim, trim == cover ? Habits.Ridge.SLAB : Habits.Ridge.TRIM);
		}
	}

	/**
	 * The one room of the poor, laid out round its fire: the fire pit against the back wall in the door's column, a bed
	 * either side of it with their heads to the back wall, the walk from the door to the fire kept clear between them.
	 * Returns the beds placed (the kit's bed placing makes up any the plan could not fit). {@code room}'s front row is
	 * inside the door; the caller furnishes the rest round them.
	 */
	static int fireAndBeds(Draft d, dev.voidmc.hearth.build.kit.Rooms.Room room, int door, int want, boolean fire) {
		return fireAndBeds(d, room, door, room.z0, want, fire);
	}

	/** {@link #fireAndBeds} with the cell inside the door at row {@code frontZ} (a round room's front is its door). */
	static int fireAndBeds(Draft d, dev.voidmc.hearth.build.kit.Rooms.Room room, int door, int frontZ, int want, boolean fire) {
		int y = room.y;
		// (every bed the house is counted for is put in: a household sleeps a bed each; the room keeps its walk from the
		// door to the fire and a free side to each bed, and roomToTurn clears what else crowds it)
		room.keep(door, frontZ);
		for (int z = frontZ + 1; z < room.z1; z++) {
			room.keep(door, z);
		}
		if (fire && room.claim(door, room.z1)) {
			dev.voidmc.hearth.build.kit.station.Fire.indoor(d, door, y, room.z1, Dirs.FRONT);
		}
		int beds = 0;
		for (int bx : new int[]{door - 1, door + 1}) {
			if (beds >= want || bx < room.x0 || bx > room.x1) {
				continue;
			}
			if (room.claim(bx, room.z1) && room.claim(bx, room.z1 - 1)) {
				dev.voidmc.hearth.build.kit.Furniture.bed(d, bx, y, room.z1, Dirs.FRONT);
				beds++;
			}
		}
		if (beds < want) {
			beds += dev.voidmc.hearth.build.kit.homestead.Beds.place(d, room, want - beds);
		}
		if (beds < want) {
			int before = TownHouses.bedsIn(d, room);
			TownHouses.bedroom(d, room, want - beds);
			beds += TownHouses.bedsIn(d, room) - before;
		}
		return beds;
	}

	/**
	 * Room to turn round in: a small home's one room keeps six cells someone can stand in, all of a piece (the bed and the
	 * fire are its own; a chest, a barrel or the trade's things beyond that crowd it), and no corner of its floor shut off
	 * behind them. What crowds it goes, first what stands between the open floor and a shut-off corner: the stores into a
	 * niche in the wall (a barrel set in the wall's thickness, opened from inside), the rest out. Beds, fires, ladders,
	 * doors and job sites stay. Answers how many cells were freed.
	 */
	static int roomToTurn(Draft d, dev.voidmc.hearth.build.kit.Rooms.Room room) {
		int freed = 0;
		for (int round = 0; round < 12; round++) {
			it.unimi.dsi.fastutil.longs.LongOpenHashSet main = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
			it.unimi.dsi.fastutil.longs.LongOpenHashSet pockets = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
			floor(d, room, main, pockets);
			if (main.size() >= 6 && pockets.isEmpty()) {
				break;
			}
			int[] pick = null;
			int best = Integer.MAX_VALUE;
			for (int z = room.z0; z <= room.z1; z++) {
				for (int x = room.x0; x <= room.x1; x++) {
					var st = d.get(x, room.y, z);
					int rank = st == null ? -1 : crowding(st);
					if (rank < 0 || jobAt(d, x, room.y, z)) {
						continue;
					}
					boolean byMain = false;
					boolean byPocket = false;
					for (Direction dir : Dirs.HORIZONTAL) {
						long n = dev.voidmc.hearth.build.draft.Draft.key(x + Dirs.dx(dir), room.y, z + Dirs.dz(dir));
						byMain |= main.contains(n);
						byPocket |= pockets.contains(n);
					}
					// (what opens a shut-off corner onto the floor first, then what stands on the floor's edge)
					int score = (byMain && byPocket ? 0 : byMain ? 10 : 20) + rank;
					if (main.size() >= 6 && !(byMain && byPocket)) {
						continue;
					}
					if (score < best) {
						best = score;
						pick = new int[]{x, z};
					}
				}
			}
			if (pick == null) {
				break;
			}
			int px = pick[0];
			int pz = pick[1];
			var st = d.get(px, room.y, pz);
			boolean store = st.getBlock() instanceof net.minecraft.world.level.block.ChestBlock || st.getBlock() instanceof net.minecraft.world.level.block.BarrelBlock;
			if (store) {
				niche(d, room, px, pz);
			}
			dev.voidmc.hearth.build.kit.townhome.Settle.lift(d, px, room.y, pz);
			d.retainPois(p -> !(p.x() == px && p.z() == pz && Math.abs(p.y() - room.y) <= 1 && p.type() == dev.voidmc.hearth.api.PoiType.STORAGE));
			freed++;
		}
		return freed;
	}

	/**
	 * The cells of a room someone can stand in (feet and head free, a floor under them): the biggest piece of them in
	 * {@code main}, the rest (corners shut off behind the furniture) in {@code pockets}.
	 */
	private static void floor(Draft d, dev.voidmc.hearth.build.kit.Rooms.Room room, it.unimi.dsi.fastutil.longs.LongOpenHashSet main,
		it.unimi.dsi.fastutil.longs.LongOpenHashSet pockets) {
		it.unimi.dsi.fastutil.longs.LongOpenHashSet all = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
		for (int z = room.z0; z <= room.z1; z++) {
			for (int x = room.x0; x <= room.x1; x++) {
				if (dev.voidmc.hearth.build.kit.townhome.Settle.walkable(d, x, room.y, z)) {
					all.add(dev.voidmc.hearth.build.draft.Draft.key(x, room.y, z));
				}
			}
		}
		it.unimi.dsi.fastutil.longs.LongOpenHashSet seen = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
		for (long start : all) {
			if (seen.contains(start)) {
				continue;
			}
			it.unimi.dsi.fastutil.longs.LongOpenHashSet comp = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
			it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue q = new it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue();
			q.enqueue(start);
			seen.add(start);
			while (!q.isEmpty()) {
				long k = q.dequeueLong();
				comp.add(k);
				for (Direction dir : Dirs.HORIZONTAL) {
					long n = dev.voidmc.hearth.build.draft.Draft.key(dev.voidmc.hearth.build.draft.Draft.keyX(k) + Dirs.dx(dir), room.y,
						dev.voidmc.hearth.build.draft.Draft.keyZ(k) + Dirs.dz(dir));
					if (all.contains(n) && seen.add(n)) {
						q.enqueue(n);
					}
				}
			}
			if (comp.size() > main.size()) {
				pockets.addAll(main);
				main.clear();
				main.addAll(comp);
			} else {
				pockets.addAll(comp);
			}
		}
	}

	/** How readily a thing on a small room's floor makes way (lower first), or -1 if it stays (beds, fires, ladders). */
	private static int crowding(net.minecraft.world.level.block.state.BlockState s) {
		var b = s.getBlock();
		if (s.isAir() || b instanceof net.minecraft.world.level.block.BedBlock || b instanceof net.minecraft.world.level.block.CampfireBlock
			|| b instanceof net.minecraft.world.level.block.LadderBlock || b instanceof net.minecraft.world.level.block.DoorBlock
			|| b instanceof net.minecraft.world.level.block.AbstractFurnaceBlock || b instanceof net.minecraft.world.level.block.CarpetBlock
			|| dev.voidmc.hearth.build.kit.townhome.Settle.passable(s)) {
			return -1;
		}
		if (b instanceof net.minecraft.world.level.block.FlowerPotBlock || b instanceof net.minecraft.world.level.block.DecoratedPotBlock
			|| b instanceof net.minecraft.world.level.block.LanternBlock || b instanceof net.minecraft.world.level.block.CandleBlock) {
			return 0;
		}
		if (b instanceof net.minecraft.world.level.block.StairBlock || b instanceof net.minecraft.world.level.block.SlabBlock
			|| b instanceof net.minecraft.world.level.block.FenceBlock) {
			// a stool, a bench, a table
			return 1;
		}
		if (b instanceof net.minecraft.world.level.block.ChestBlock || b instanceof net.minecraft.world.level.block.BarrelBlock) {
			return 2;
		}
		if (s.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO)
			&& !(b instanceof net.minecraft.world.level.block.CraftingTableBlock)) {
			// a hay bale, a log, a pumpkin: the trade's things
			return 3;
		}
		return 4;
	}

	/** Whether a job site (or a seat folk are sent to) stands at the cell. */
	private static boolean jobAt(Draft d, int x, int y, int z) {
		for (Draft.LocalPoi p : d.pois()) {
			if (p.x() == x && p.z() == z && Math.abs(p.y() - y) <= 1 && p.type() == dev.voidmc.hearth.api.PoiType.JOB_SITE) {
				return true;
			}
		}
		return false;
	}

	/** A barrel set into the wall beside a room cell (an outer wall cell, solid above and below, no opening). */
	private static void niche(Draft d, dev.voidmc.hearth.build.kit.Rooms.Room room, int x, int z) {
		for (Direction dir : Dirs.HORIZONTAL) {
			int wx = x + Dirs.dx(dir);
			int wz = z + Dirs.dz(dir);
			if (room.inside(wx, wz) || !d.isSolid(wx, room.y, wz) || !d.isSolid(wx, room.y + 1, wz) || !d.isSolid(wx, room.y - 1, wz)) {
				continue;
			}
			// never at a corner (where two walls meet: the wall's post) nor through to the outside's door
			int sx = wx + Dirs.dx(dir.getClockWise());
			int sz = wz + Dirs.dz(dir.getClockWise());
			int tx = wx - Dirs.dx(dir.getClockWise());
			int tz = wz - Dirs.dz(dir.getClockWise());
			if (!d.isSolid(sx, room.y, sz) || !d.isSolid(tx, room.y, tz) || !room.inside(sx - Dirs.dx(dir), sz - Dirs.dz(dir))
				|| !room.inside(tx - Dirs.dx(dir), tz - Dirs.dz(dir))) {
				continue;
			}
			d.inStage(StageKind.INTERIOR, () -> d.set(wx, room.y, wz, dev.voidmc.hearth.build.draft.States.facing(Blocks.BARREL, dir.getOpposite())));
			d.poi(dev.voidmc.hearth.api.PoiType.STORAGE, x, room.y, z, dir);
			return;
		}
	}

	/**
	 * A shuttered window in the middle of every wall of a small home's room five or more long that has none (a hut in the
	 * woods has a window by the bed and one by the hearth): glass in the wall at {@code y}, shutters either side.
	 */
	static void windows(Draft d, int x0, int z0, int x1, int z1, int y) {
		for (Side s : Side.of(x0, z0, x1, z1)) {
			// (never in a party wall)
			if (s.length() < 5 || hasOpening(d, s, y) || s.out() == Dirs.LEFT && d.partyLocalLeft() || s.out() == Dirs.RIGHT && d.partyLocalRight()) {
				continue;
			}
			for (int i : new int[]{s.middle(), s.middle() - 1, s.middle() + 1}) {
				if (i <= 0 || i >= s.last()) {
					continue;
				}
				int x = s.x(i);
				int z = s.z(i);
				int ox = s.outX(i, 1);
				int oz = s.outZ(i, 1);
				if (!d.isSolid(x, y, z) || !d.isSolid(x, y + 1, z) || !d.isSolid(x, y - 1, z) || !d.isAbsent(ox, y, oz) && !d.isEmpty(ox, y, oz)
					|| !d.isEmpty(x - Dirs.dx(s.out()), y, z - Dirs.dz(s.out())) && !(d.get(x - Dirs.dx(s.out()), y, z - Dirs.dz(s.out())).getBlock() instanceof net.minecraft.world.level.block.BedBlock)) {
					continue;
				}
				Openings.window(d, s, s.column(i), y, 1, 1, Openings.WindowStyle.PLAIN, new Openings.WindowOpts(true, false, true, false));
				break;
			}
		}
	}

	private static boolean hasOpening(Draft d, Side s, int y) {
		for (int i = 1; i < s.last(); i++) {
			for (int yy = y - 1; yy <= y + 1; yy++) {
				var st = d.get(s.x(i), yy, s.z(i));
				if (st != null && (st.isAir() || st.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock
					|| net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath().contains("glass"))) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Fills every wall line of {@code r} (x0, z0, x1, z1) above {@code plate} up to the roof's underside in {@code p}
	 * (the triangles of gables, the high side of a lean-to, the gap under a shallow eave), where nothing else is drawn.
	 */
	static void fillUnder(Draft d, Roofs.Roof roof, int[] r, int plate, Paint p) {
		try (var s = d.stage(StageKind.WALLS)) {
			for (Side side : Side.of(r[0], r[1], r[2], r[3])) {
				for (int i = 0; i < side.length(); i++) {
					int x = side.x(i);
					int z = side.z(i);
					if (!roof.covers(x, z)) {
						continue;
					}
					for (int y = plate + 1; y < roof.bottomY(x, z); y++) {
						if (d.isEmpty(x, y, z)) {
							d.set(x, y, z, p);
						}
					}
				}
			}
		}
	}

	/**
	 * Board-and-batten: a wall of planks of {@code boards} with an upright batten of {@code battens} (stripped timber, its
	 * grain running up) every other column, so a board wall reads as boards nailed up and not as a plank box.
	 */
	static Paint boardAndBatten(Draft d, Mat boards, Mat battens, Direction.Axis along) {
		Paint plank = boards.full();
		Paint batten = battens.has(Form.STRIPPED_LOG) ? battens.strippedLog(Direction.Axis.Y) : battens.log(Direction.Axis.Y);
		return (x, y, z) -> ((along == Direction.Axis.X ? x : z) & 1) == 0 ? batten.at(x, y, z) : plank.at(x, y, z);
	}

	/** A paint of one block state for every cell (a fixed choice). */
	static Paint of(net.minecraft.world.level.block.Block b) {
		return Paint.of(b.defaultBlockState());
	}

	/** Whether a role's material can be cut in a form (stairs, slab, fence...). */
	static boolean cuts(Draft d, Role role, Form form) {
		return d.mat(role).has(form);
	}

	/**
	 * A town house's door column: the row's pairing (away from a lone neighbour) where the street meets the floor there,
	 * else the column nearest the middle whose street meets it best (on a steep street the plot's floor is set where the
	 * street meets its middle, and a door at the corner would stand a storey off it).
	 */
	static int townDoor(Draft d, int x0, int x1, boolean pl, boolean pr) {
		// the kit's own rule now (TownHouses.doorColumn moves off a corner the street does not meet)
		return TownHouses.doorColumn(d, x0, x1, pl, pr);
	}

	/**
	 * Keeps a town stair free in a room of storey k ({@link dev.voidmc.hearth.build.kit.townhome.StairCore#keep}, which
	 * now keeps a straight stair's approach row too, as this family first did for itself).
	 */
	static void keepCore(dev.voidmc.hearth.build.kit.townhome.StairCore core, dev.voidmc.hearth.build.kit.Rooms.Room room, int k) {
		core.keep(room, k);
	}
}
