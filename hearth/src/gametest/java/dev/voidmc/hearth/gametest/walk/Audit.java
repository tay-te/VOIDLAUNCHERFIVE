package dev.voidmc.hearth.gametest.walk;

import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.gametest.walk.WalkModel.Cell;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.entity.FolkEntities;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.pathfinder.Path;
import org.jspecify.annotations.Nullable;

/**
 * One building's playability audit in the live world, on the server thread: the model's search from the street, rooms,
 * doors, stairs, head room, light, POIs, and the folk's own pathfinder to every bed, job site and store. The paths the
 * real player is then driven along are kept in {@link #walks}; the client fills {@link #walkResults}.
 */
public final class Audit {
	public final String label;
	public final String culture;
	public final String archetype;
	public final Blueprint bp;
	public final List<String[]> defects = new ArrayList<>();
	public final List<String> notes = new ArrayList<>();
	public final Map<String, List<double[]>> walks = new LinkedHashMap<>();
	public final Map<String, String> walkResults = new LinkedHashMap<>();
	public final List<double[]> stuckAt = new ArrayList<>();
	public final List<String> shots = new ArrayList<>();

	public @Nullable Cell street;
	public int rooms;
	public int roomsReached;
	public int entrances;
	public int entrancesReached;
	public final Map<PoiType, int[]> pois = new EnumMap<>(PoiType.class);
	public int folkPaths;
	public int folkPathsOk;
	public int flightSteps;
	public int badSteps;
	public int jumps;
	public int tight;
	public int interiorCells;
	public int dark0;
	public int dim;
	public int doors;
	public int badDoors;
	public int beds;
	public final List<String> roomLines = new ArrayList<>();

	/** For the cameras: the main room's view, the bedroom's, the stair's (feet x, y, z, look-at x, y, z). */
	public double @Nullable [] mainCam;
	public double @Nullable [] bedCam;
	public double @Nullable [] stairCam;

	private final List<BlockPos> opened = new ArrayList<>();
	private final List<BlockState> openedWas = new ArrayList<>();

	public Audit(String label, String culture, String archetype, Blueprint bp) {
		this.label = label;
		this.culture = culture;
		this.archetype = archetype;
		this.bp = bp;
	}

	public void defect(String severity, String text) {
		this.defects.add(new String[]{severity, text});
	}

	// ---------------------------------------------------------------- doors

	/** Opens every door, gate and ladder hatch a hand opens (as a player walking through would). */
	public void openDoors(ServerLevel level) {
		var b = this.bp.bounds();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int x = b.minX() - 1; x <= b.maxX() + 1; x++) {
			for (int z = b.minZ() - 1; z <= b.maxZ() + 1; z++) {
				for (int y = b.minY() - 1; y <= b.maxY() + 1; y++) {
					BlockState st = level.getBlockState(m.set(x, y, z));
					BlockState open = null;
					if (st.getBlock() instanceof DoorBlock door && door.type().canOpenByHand() && !st.getValue(DoorBlock.OPEN)) {
						open = st.setValue(DoorBlock.OPEN, true);
					} else if (st.getBlock() instanceof FenceGateBlock && !st.getValue(FenceGateBlock.OPEN)) {
						open = st.setValue(FenceGateBlock.OPEN, true);
					} else if (st.getBlock() instanceof TrapDoorBlock && st.is(BlockTags.WOODEN_TRAPDOORS) && !st.getValue(TrapDoorBlock.OPEN)
						&& (level.getBlockState(m.below()).is(BlockTags.CLIMBABLE) || level.getBlockState(m.above()).is(BlockTags.CLIMBABLE))) {
						open = st.setValue(TrapDoorBlock.OPEN, true);
					}
					if (open != null) {
						BlockPos p = m.immutable();
						this.opened.add(p);
						this.openedWas.add(st);
						level.setBlock(p, open, 2 | 16);
					}
				}
			}
		}
	}

	public void closeDoors(ServerLevel level) {
		for (int i = 0; i < this.opened.size(); i++) {
			level.setBlock(this.opened.get(i), this.openedWas.get(i), 2 | 16);
		}
		this.opened.clear();
		this.openedWas.clear();
	}

	// ---------------------------------------------------------------- the audit

	public void run(ServerLevel level) {
		WalkModel w = new WalkModel(level, this.bp);
		var spec = this.bp.spec();
		var o = spec.origin();
		int sx = spec.sizeX();
		int sz = spec.sizeZ();
		int fx;
		int fz;
		switch (spec.facing()) {
			case SOUTH -> {
				fx = o.getX() + sx / 2;
				fz = o.getZ() + sz + 1;
			}
			case NORTH -> {
				fx = o.getX() + sx / 2;
				fz = o.getZ() - 2;
			}
			case EAST -> {
				fx = o.getX() + sx + 1;
				fz = o.getZ() + sz / 2;
			}
			default -> {
				fx = o.getX() - 2;
				fz = o.getZ() + sz / 2;
			}
		}
		Cell street = w.standAt(fx, spec.floorY(), fz);
		if (street == null && !this.bp.entrances().isEmpty()) {
			BlockPos e = this.bp.entrances().getFirst();
			street = w.standAt(e.getX(), e.getY(), e.getZ());
		}
		if (street == null) {
			defect("HIGH", "no standing room in the street in front of the plot at " + fx + "," + spec.floorY() + "," + fz);
			return;
		}
		this.street = street;
		w.search(street);

		// entrances
		this.entrances = this.bp.entrances().size();
		int i = 0;
		for (BlockPos e : this.bp.entrances()) {
			Cell c = w.reachedNear(e, 0);
			if (c == null) {
				c = w.reachedNear(e, 1);
			}
			if (c != null) {
				this.entrancesReached++;
			} else {
				defect(i == 0 ? "HIGH" : "MED", (i == 0 ? "main" : "side") + " entrance at " + e.toShortString() + " cannot be walked to from the street");
			}
			i++;
		}

		// doors
		var b = this.bp.bounds();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int x = b.minX(); x <= b.maxX(); x++) {
			for (int z = b.minZ(); z <= b.maxZ(); z++) {
				for (int y = b.minY(); y <= b.maxY(); y++) {
					BlockState st = level.getBlockState(m.set(x, y, z));
					if (!(st.getBlock() instanceof DoorBlock door) || st.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
						continue;
					}
					this.doors++;
					BlockPos p = m.immutable();
					Direction f = st.getValue(DoorBlock.FACING);
					boolean aSide = side(w, p.relative(f));
					boolean bSide = side(w, p.relative(f.getOpposite()));
					Cell at = w.cell(p.getX(), p.getY(), p.getZ());
					if (!door.type().canOpenByHand()) {
						defect("LOW", "door at " + p.toShortString() + " (" + st.getBlock().getName().getString() + ") cannot be opened by hand and has no button");
					}
					if (!aSide || !bSide) {
						this.badDoors++;
						Direction bad = aSide ? f.getOpposite() : f;
						defect("HIGH", "door at " + p.toShortString() + " leads into a wall or a drop on its " + bad.getSerializedName() + " side ("
							+ column(level, p.relative(bad)) + ")");
					} else if (at == null) {
						this.badDoors++;
						defect("HIGH", "doorway at " + p.toShortString() + " has no head room to pass (open door)");
					} else if (!w.reached(at) && isReached(w, cellNear(w, p.relative(f))) != isReached(w, cellNear(w, p.relative(f.getOpposite())))) {
						this.badDoors++;
						defect("MED", "door at " + p.toShortString() + " is reached on one side but cannot be passed");
					}
				}
			}
		}

		// rooms
		List<List<Cell>> all = w.rooms();
		List<List<Cell>> rooms = new ArrayList<>();
		for (List<Cell> r : all) {
			if (r.size() >= 4) {
				rooms.add(r);
			}
		}
		this.rooms = rooms.size();
		List<Poi> pois = this.bp.pois();
		List<Cell> biggest = null;
		for (List<Cell> r : rooms) {
			int reached = 0;
			int min = 15;
			int lowCells = 0;
			double cx = 0;
			double cz = 0;
			for (Cell c : r) {
				cx += c.x() + 0.5;
				cz += c.z() + 0.5;
				if (w.reached(c)) {
					reached++;
					int l = w.light(c);
					min = Math.min(min, l);
					if (l < 8) {
						lowCells++;
					}
				}
			}
			cx /= r.size();
			cz /= r.size();
			Cell first = r.getFirst();
			int storeyY = first.y() - spec.floorY();
			Set<Long> keys = new HashSet<>();
			for (Cell c : r) {
				keys.add(c.key());
			}
			List<String> inside = new ArrayList<>();
			for (Poi p : pois) {
				if (near(keys, p.pos())) {
					inside.add(p.type().getSerializedName());
				}
			}
			if (reached > 0) {
				this.roomsReached++;
				if (biggest == null || r.size() > biggest.size()) {
					biggest = r;
				}
			} else {
				boolean matters = inside.stream().anyMatch(s -> !s.equals("seat") && !s.equals("table") && !s.equals("viewpoint"));
				defect(matters ? "HIGH" : r.size() >= 8 ? "MED" : "LOW", String.format(Locale.ROOT,
					"room of %d cells at y%+d round %.0f,%d,%.0f cannot be reached%s", r.size(), storeyY, cx, first.y(), cz,
					inside.isEmpty() ? " (nothing in it)" : " (has " + String.join(", ", inside) + ")"));
			}
			this.roomLines.add(String.format(Locale.ROOT, "y%+d %d cells round %.0f,%d,%.0f: %s, light min %s, %d cells under 8%s", storeyY, r.size(), cx,
				first.y(), cz, reached > 0 ? "reached" : "NOT reached", reached > 0 ? Integer.toString(min) : "-", lowCells,
				inside.isEmpty() ? "" : "; " + String.join(", ", inside)));
			if (reached > 0 && lowCells * 3 > reached && min < 8) {
				defect(min == 0 ? "MED" : "LOW", String.format(Locale.ROOT, "room at y%+d round %.0f,%d,%.0f is dim: %d of %d cells under light 8 (min %d)",
					storeyY, cx, first.y(), cz, lowCells, reached, min));
			}
		}

		// POIs: the model, then the folk's own pathfinder from the street
		Folk folk = FolkEntities.FOLK.create(level, EntitySpawnReason.COMMAND);
		int faces = 0;
		for (Poi p : pois) {
			int[] n = this.pois.computeIfAbsent(p.type(), k -> new int[2]);
			n[1]++;
			Cell c = w.poiReached(p);
			if (c != null) {
				n[0]++;
			}
			boolean key = p.type() == PoiType.BED || p.type() == PoiType.JOB_SITE || p.type() == PoiType.STORAGE || p.type() == PoiType.COUNTER
				|| p.type() == PoiType.WORK_FACE && faces++ < 4;
			if (p.type() == PoiType.BED) {
				this.beds++;
			}
			String what = p.type().getSerializedName() + (p.job() != null ? " (" + p.job().getSerializedName() + ")" : "") + " at " + p.pos().toShortString();
			if (c == null && key) {
				defect("HIGH", what + " cannot be walked to by a player");
			}
			if (key && folk != null) {
				this.folkPaths++;
				folk.snapTo(street.x() + 0.5, street.floor(), street.z() + 0.5, 0, 0);
				folk.setOnGround(true);
				// as a folk asks: for a spot beside the bed or bench (npc FolkSpots.approaches), with a wider search for another storey
				java.util.List<BlockPos> spots = dev.voidmc.hearth.npc.entity.FolkSpots.approaches(level, p.pos());
				folk.getNavigation().setRequiredPathLength(Math.abs(p.pos().getY() - street.floor()) >= 3 ? 96.0F : 48.0F);
				Path path = spots.isEmpty() ? folk.getNavigation().createPath(p.pos(), 1) : folk.getNavigation().createPath(new java.util.HashSet<>(spots), 0);
				if (path != null && path.canReach()) {
					this.folkPathsOk++;
				} else {
					var end = path == null ? null : path.getEndNode();
					defect(c == null || p.type() == PoiType.BED || p.type() == PoiType.JOB_SITE ? "HIGH" : "MED", "folk pathfinder finds no way from the street to the " + what
						+ (end == null ? "" : " (gets as far as " + end.x + "," + end.y + "," + end.z + ")"));
				}
			}
			if (c != null && key && this.walks.size() < 40) {
				this.walks.putIfAbsent(what, pack(w.path(c)));
			}
		}
		if (folk != null) {
			folk.discard();
		}
		for (List<Cell> r : rooms) {
			Cell best = null;
			double bd = Double.MAX_VALUE;
			double cx = 0;
			double cz = 0;
			for (Cell c : r) {
				cx += c.x();
				cz += c.z();
			}
			cx /= r.size();
			cz /= r.size();
			for (Cell c : r) {
				double d = (c.x() - cx) * (c.x() - cx) + (c.z() - cz) * (c.z() - cz);
				if (w.reached(c) && d < bd) {
					bd = d;
					best = c;
				}
			}
			if (best != null && this.walks.size() < 48) {
				this.walks.putIfAbsent("room " + best, pack(w.path(best)));
			}
		}

		// stairs: flights (two or more steps in a run) inside; a step whose foot is reached but whose top is not
		Cell stairFoot = null;
		Direction stairDir = null;
		int stairTop = 0;
		for (int x = b.minX(); x <= b.maxX(); x++) {
			for (int z = b.minZ(); z <= b.maxZ(); z++) {
				for (int y = b.minY(); y <= b.maxY(); y++) {
					BlockState st = level.getBlockState(m.set(x, y, z));
					if (!(st.getBlock() instanceof StairBlock) || st.getValue(StairBlock.HALF) != Half.BOTTOM) {
						continue;
					}
					Direction f = st.getValue(StairBlock.FACING);
					BlockPos p = m.immutable();
					boolean up = isStep(level.getBlockState(p.relative(f).above()), f);
					boolean down = isStep(level.getBlockState(p.relative(f.getOpposite()).below()), f);
					if (!up && !down) {
						continue;
					}
					Cell top = w.cell(x, y + 1, z);
					Cell foot = down ? w.cell(x - f.getStepX(), y, z - f.getStepZ()) : w.standAt(x - f.getStepX(), y, z - f.getStepZ());
					boolean footReached = foot != null && w.reached(foot);
					boolean topReached = top != null && w.reached(top);
					if (!footReached && !topReached) {
						continue;
					}
					this.flightSteps++;
					if (footReached && !topReached) {
						this.badSteps++;
						defect("MED", "stair step at " + p.toShortString() + " (going " + f.getSerializedName() + ") cannot be climbed: "
							+ (top == null ? "no head room over it (a ceiling within 1.8 of the tread)" : "blocked stepping onto it"));
					}
					if (topReached && top.clear() < 2.0) {
						this.tight++;
					}
					if (!down && footReached && (stairFoot == null || y < stairTop)) {
						stairFoot = foot;
						stairDir = f;
						stairTop = y;
					}
					if (topReached && this.walks.size() < 56 && !up) {
						this.walks.putIfAbsent("stair top " + top, pack(w.path(top)));
					}
				}
			}
		}

		// what the reached cells inside the building are like
		List<String> darkAt = new ArrayList<>();
		List<String> jumpAt = new ArrayList<>();
		List<String> tightAt = new ArrayList<>();
		Cell ladder = null;
		for (long k : w.reachedKeys()) {
			Cell c = w.byKey(k);
			if (c == null || !w.inside(c)) {
				continue;
			}
			if (c.climb() && (ladder == null || c.y() < ladder.y())) {
				ladder = c;
			}
			if (c.clear() < 1.9 && w.roofed(c)) {
				this.tight++;
				if (tightAt.size() < 6) {
					tightAt.add(c.toString());
				}
			}
			if (w.roofed(c)) {
				this.interiorCells++;
				int l = w.light(c);
				if (l == 0) {
					this.dark0++;
					if (darkAt.size() < 6) {
						darkAt.add(c.toString());
					}
				}
				if (l < 8) {
					this.dim++;
				}
			}
		}
		Set<String> jumped = new java.util.LinkedHashSet<>();
		for (List<double[]> path : this.walks.values()) {
			for (double[] st : path) {
				if ((int) st[4] == WalkModel.JUMP && b.isInside((int) st[0], (int) st[1], (int) st[2])) {
					jumped.add((int) st[0] + "," + (int) st[1] + "," + (int) st[2]);
				}
			}
		}
		this.jumps = jumped.size();
		jumpAt.addAll(jumped.stream().limit(6).toList());
		if (this.jumps > 0) {
			defect(this.jumps > 3 ? "MED" : "LOW", this.jumps + " full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): " + String.join("; ", jumpAt));
		}
		if (!tightAt.isEmpty()) {
			defect("LOW", "head room under 1.9 at " + this.tight + " cell(s) (head bumps): " + String.join("; ", tightAt));
		}
		if (this.dark0 > 0) {
			defect(this.dark0 >= 4 ? "MED" : "LOW", this.dark0 + " reached cell(s) under a roof at block light 0 (monsters can spawn): " + String.join("; ", darkAt));
		}

		// the cameras
		if (biggest != null) {
			this.mainCam = across(w, biggest, null);
		}
		for (Poi p : pois) {
			if (p.type() != PoiType.BED) {
				continue;
			}
			Cell c = w.poiReached(p);
			if (c == null) {
				continue;
			}
			for (List<Cell> r : rooms) {
				if (r.contains(c)) {
					this.bedCam = across(w, r, p.pos());
					break;
				}
			}
			if (this.bedCam == null) {
				this.bedCam = new double[]{c.x() + 0.5, c.floor(), c.z() + 0.5, p.pos().getX() + 0.5, p.pos().getY() + 0.4, p.pos().getZ() + 0.5};
			}
			break;
		}
		if (stairFoot != null) {
			Cell back = w.cell(stairFoot.x() - stairDir.getStepX(), stairFoot.y(), stairFoot.z() - stairDir.getStepZ());
			Cell cam = back != null && w.reached(back) ? back : stairFoot;
			this.stairCam = new double[]{cam.x() + 0.5, cam.floor(), cam.z() + 0.5, stairFoot.x() + 0.5 + stairDir.getStepX() * 3, stairFoot.floor() + 3.2,
				stairFoot.z() + 0.5 + stairDir.getStepZ() * 3};
		} else if (ladder != null) {
			this.stairCam = new double[]{ladder.x() + 0.5, ladder.floor(), ladder.z() + 0.5, ladder.x() + 0.5, ladder.floor() + 6, ladder.z() + 0.5};
			for (Direction d : Direction.Plane.HORIZONTAL) {
				Cell n = w.cell(ladder.x() + d.getStepX() * 2, ladder.y(), ladder.z() + d.getStepZ() * 2);
				if (n != null && w.reached(n)) {
					this.stairCam = new double[]{n.x() + 0.5, n.floor(), n.z() + 0.5, ladder.x() + 0.5, ladder.floor() + 4, ladder.z() + 0.5};
					break;
				}
			}
		}
	}

	/** The blocks in a column from one below to two above, bottom first, for the report. */
	static String column(ServerLevel level, BlockPos p) {
		StringBuilder b = new StringBuilder();
		for (int dy = -1; dy <= 2; dy++) {
			BlockState st = level.getBlockState(p.above(dy));
			if (b.length() > 0) {
				b.append(" / ");
			}
			b.append(dy).append(':').append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath());
		}
		return b.toString();
	}

	private static boolean isStep(BlockState st, Direction f) {
		return st.getBlock() instanceof StairBlock && st.getValue(StairBlock.HALF) == Half.BOTTOM && st.getValue(StairBlock.FACING) == f;
	}

	private static @Nullable Cell cellNear(WalkModel w, BlockPos p) {
		return w.standAt(p.getX(), p.getY(), p.getZ());
	}

	private static boolean isReached(WalkModel w, @Nullable Cell c) {
		return c != null && w.reached(c);
	}

	private static boolean side(WalkModel w, BlockPos p) {
		for (int dy = -1; dy <= 1; dy++) {
			if (w.cell(p.getX(), p.getY() + dy, p.getZ()) != null) {
				return true;
			}
		}
		// a step down outside (a door over a stoop)
		return w.cell(p.getX(), p.getY() - 2, p.getZ()) != null;
	}

	private static boolean near(Set<Long> keys, BlockPos p) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dy = -1; dy <= 1; dy++) {
					if (keys.contains(BlockPos.asLong(p.getX() + dx, p.getY() + dy, p.getZ() + dz))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** A view across a room: from its reached cell farthest from the target (its middle, or a point), toward it. */
	private static double @Nullable [] across(WalkModel w, List<Cell> room, @Nullable BlockPos at) {
		double tx = 0;
		double tz = 0;
		for (Cell c : room) {
			tx += c.x() + 0.5;
			tz += c.z() + 0.5;
		}
		tx /= room.size();
		tz /= room.size();
		double ty = room.getFirst().floor() + 1.2;
		if (at != null) {
			tx = at.getX() + 0.5;
			tz = at.getZ() + 0.5;
			ty = at.getY() + 0.4;
		}
		Cell best = null;
		double bd = -1;
		for (Cell c : room) {
			if (!w.reached(c) || c.clear() < 2.0) {
				continue;
			}
			double d = (c.x() + 0.5 - tx) * (c.x() + 0.5 - tx) + (c.z() + 0.5 - tz) * (c.z() + 0.5 - tz);
			if (d > bd) {
				bd = d;
				best = c;
			}
		}
		if (best == null) {
			return null;
		}
		return new double[]{best.x() + 0.5, best.floor(), best.z() + 0.5, tx, ty, tz};
	}

	/** A path as plain numbers for the client: x, y, z, floor, move, climb. */
	private static List<double[]> pack(List<WalkModel.Step> path) {
		List<double[]> out = new ArrayList<>();
		for (WalkModel.Step s : path) {
			Cell c = s.cell();
			out.add(new double[]{c.x(), c.y(), c.z(), c.floor(), s.edge(), c.climb() ? 1 : 0});
		}
		return out;
	}

	// ---------------------------------------------------------------- the verdict

	public String verdict() {
		boolean high = false;
		boolean med = false;
		for (String[] d : this.defects) {
			high |= d[0].equals("HIGH");
			med |= d[0].equals("MED");
		}
		return high ? "FAIL" : med ? "WARN" : "PASS";
	}

	public int walksOk() {
		int n = 0;
		for (String r : this.walkResults.values()) {
			if (r.equals("ok")) {
				n++;
			}
		}
		return n;
	}

	public String poiLine() {
		StringBuilder b = new StringBuilder();
		for (var e : this.pois.entrySet()) {
			if (b.length() > 0) {
				b.append(", ");
			}
			b.append(e.getKey().getSerializedName()).append(' ').append(e.getValue()[0]).append('/').append(e.getValue()[1]);
		}
		return b.toString();
	}

	public String row() {
		return String.join("\t", this.culture, this.archetype, verdict(), this.roomsReached + "/" + this.rooms, this.entrancesReached + "/" + this.entrances,
			poiLine(), this.folkPathsOk + "/" + this.folkPaths, walksOk() + "/" + this.walkResults.size(), Integer.toString(this.stuckAt.size()),
			this.badSteps + "/" + this.flightSteps, Integer.toString(this.jumps), Integer.toString(this.tight), this.dark0 + "/" + this.dim + "/" + this.interiorCells,
			this.badDoors + "/" + this.doors);
	}

	public String markdown() {
		StringBuilder b = new StringBuilder();
		var s = this.bp.spec();
		b.append("### ").append(this.label).append(" — ").append(verdict()).append("\n\n");
		b.append(String.format(Locale.ROOT, "`%s` %s, %dx%d facing %s, tier %d, origin %s, %d beds. Street start %s.%n%n", s.archetype(), s.style().getPath(),
			s.sizeX(), s.sizeZ(), s.facing().getSerializedName(), s.tier(), s.origin().toShortString(), this.beds, this.street));
		b.append(String.format(Locale.ROOT, "- rooms reached %d/%d; entrances %d/%d; doors bad %d/%d%n", this.roomsReached, this.rooms, this.entrancesReached,
			this.entrances, this.badDoors, this.doors));
		b.append("- POIs walked to (model): ").append(poiLine()).append("\n");
		b.append(String.format(Locale.ROOT, "- folk pathfinder street -> bed/job/store: %d/%d%n", this.folkPathsOk, this.folkPaths));
		b.append(String.format(Locale.ROOT, "- player driven (real movement & collision): %d/%d paths completed, %d stuck%n", walksOk(), this.walkResults.size(),
			this.stuckAt.size()));
		b.append(String.format(Locale.ROOT, "- stairs: %d flight steps, %d unclimbable; jumps needed %d; tight head room %d%n", this.flightSteps, this.badSteps,
			this.jumps, this.tight));
		b.append(String.format(Locale.ROOT, "- light under roof: %d cells, %d under 8, %d at 0%n", this.interiorCells, this.dim, this.dark0));
		for (String r : this.roomLines) {
			b.append("  - room ").append(r).append("\n");
		}
		for (var e : this.walkResults.entrySet()) {
			if (!e.getValue().equals("ok")) {
				b.append("  - walk to ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
			}
		}
		if (!this.defects.isEmpty()) {
			b.append("- defects:\n");
			for (String[] d : this.defects) {
				b.append("  - **").append(d[0]).append("** ").append(d[1]).append("\n");
			}
		}
		for (String n : this.notes) {
			b.append("- ").append(n).append("\n");
		}
		if (!this.shots.isEmpty()) {
			b.append("- shots: ").append(String.join(", ", this.shots)).append("\n");
		}
		b.append("\n");
		return b.toString();
	}
}
