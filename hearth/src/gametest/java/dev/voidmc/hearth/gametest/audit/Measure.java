package dev.voidmc.hearth.gametest.audit;

import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.blueprint.LocalFrame;
import dev.voidmc.hearth.build.check.BlueprintChecks;
import dev.voidmc.hearth.build.check.LightCheck;
import dev.voidmc.hearth.build.kit.light.Grid;
import dev.voidmc.hearth.build.kit.light.LightField;
import dev.voidmc.hearth.build.kit.light.Spaces;
import dev.voidmc.hearth.gametest.render.Voxels;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;

/**
 * The measurements of one blueprint for the structure audit: playability (read off the light check's walk, which is the
 * walking rule of {@link BlueprintChecks}: a 1 x 2 mover that steps up one block, drops at most three, climbs ladders,
 * passes doors and gates, i.e. a player's 0.6 x 1.8 box) and detail (facades, roof, palette, furniture), into a column map.
 * Nothing here re-implements the invariant checks: {@link BlueprintChecks#all} runs as it is and its findings are parsed.
 */
final class Measure {
	/** Stages that make the building's body (gardens, yards, lamps and furniture aside). */
	static final Set<StageKind> STRUCTURE = EnumSet.of(StageKind.FOUNDATION, StageKind.FRAME, StageKind.WALLS, StageKind.FLOORS, StageKind.ROOF,
		StageKind.OPENINGS);
	private static final Pattern INT = Pattern.compile("(\\d+)");

	private Measure() {
	}

	// =========================================================================================== playability

	static void playability(BlueprintImpl bp, TerrainProbe probe, Map<String, Object> m) {
		PlotSpec spec = bp.spec();
		List<String> problems = BlueprintChecks.all(bp, probe);
		int floating = 0;
		int unsupported = 0;
		boolean mainUnreached = false;
		int bedsUnreached = 0;
		int jobsUnreached = 0;
		for (String p : problems) {
			Matcher mt = INT.matcher(p);
			int n = mt.find() ? Integer.parseInt(mt.group(1)) : 1;
			if (p.contains("float")) {
				floating += n;
			} else if (p.contains("lack the support")) {
				unsupported += n;
			} else if (p.contains("main entrance")) {
				mainUnreached = true;
			} else if (p.contains("cannot be walked to from a door")) {
				bedsUnreached += n;
				if (mt.find()) {
					jobsUnreached += Integer.parseInt(mt.group(1));
				}
			}
		}
		m.put("check_problems", problems.size());
		m.put("floating", floating);
		m.put("unsupported", unsupported);
		m.put("main_entrance_unreached", mainUnreached ? 1 : 0);
		m.put("beds_unreached_check", bedsUnreached);
		m.put("jobs_unreached_check", jobsUnreached);
		String text = String.join(" | ", problems);
		m.put("check_text", text.length() > 400 ? text.substring(0, 400) + "..." : text);

		LightCheck.Result r = LightCheck.evaluate(bp, probe);
		Grid g = r.grid();
		Spaces sp = r.spaces();
		LightField lf = r.light();
		int floor = spec.floorY();
		Map<String, String> where = new java.util.LinkedHashMap<>();
		Frame lf0 = new Frame(new LocalFrame(spec), floor);
		for (String dbgSpec : System.getProperty("audit.reach", "").split(";")) for (String dbgLv : dbgSpec.isEmpty() ? new String[0] : dbgSpec.substring(dbgSpec.lastIndexOf(',') + 1).split("/")) {
		String dbg = dbgSpec.substring(0, dbgSpec.lastIndexOf(',') + 1) + dbgLv;
		String terr = System.getProperty("audit.reachTerrain", "");
		if (!terr.isEmpty() && !spec.toString().contains(terr)) continue;
		if (!dbg.isEmpty() && dbg.startsWith(spec.archetype().getPath() + "," + spec.style().getPath() + "," + spec.seed() + "," + spec.facing().getSerializedName() + ",")) {
			// a walk map in the plot's frame at one standing level: r reached, + walkable, # solid, . air; front (z = -1) at the bottom
			int ly = Integer.parseInt(dbg.substring(dbg.lastIndexOf(',') + 1));
			LocalFrame lfr = new LocalFrame(spec);
			StringBuilder sb = new StringBuilder("walk map " + dbg + " (x across, back at top)" + System.lineSeparator());
			for (int lz = lfr.depth(); lz >= -2; lz--) {
				for (int lx = -1; lx <= lfr.frontage(); lx++) {
					int x = lfr.worldX(lx, lz);
					int z = lfr.worldZ(lx, lz);
					int y = floor + ly;
					BlockState st = g.at(x, y, z);
					sb.append(sp.reached(x, y, z) ? 'r' : sp.walkable(x, y, z) ? '+' : st.isAir() ? '.' : workstation(st) ? 'W' : st.getBlock() instanceof BedBlock ? 'B'
						: st.getBlock() instanceof DoorBlock ? 'D' : '#');
				}
				sb.append(System.lineSeparator());
			}
			System.out.println(sb);
			System.out.println(BlueprintChecks.reachMap(bp, probe, floor + ly) + "entrances " + bp.entrances() + " floor " + floor + " grid y0 " + g.y0 + " spec " + spec);
		}
		}

		// --- walkable cells in the plot, rooms (enclosed), open-roofed cells
		int walk = 0;
		int reached = 0;
		int roofedReached = 0;
		int openRoofed = 0;
		LongOpenHashSet room = new LongOpenHashSet();
		LongOpenHashSet unroofed = new LongOpenHashSet();
		LongOpenHashSet reachedPlot = new LongOpenHashSet();
		for (int y = g.y0 + 1; y < g.y0 + g.sy - 1; y++) {
			for (int z = g.z0; z < g.z0 + g.sz; z++) {
				for (int x = g.x0; x < g.x0 + g.sx; x++) {
					if (!g.inPlot(x, z) || !sp.walkable(x, y, z) || y > floor + spec.maxHeight()) {
						continue;
					}
					walk++;
					boolean rc = sp.reached(x, y, z);
					if (rc) {
						reached++;
						reachedPlot.add(BlockPos.asLong(x, y, z));
					}
					if (sp.wet(x, y, z)) {
						continue;
					}
					boolean door = sp.doorway(x, y, z);
					boolean roofed = sp.roofed(x, y, z);
					BlockState under = g.at(x, y - 1, z);
					boolean onFurniture = g.placed(x, y - 1, z) && !(under.getBlock() instanceof StairBlock) && furniture(under, 0);
					if (roofed && sp.enclosed(x, y, z) && !door && !onFurniture) {
						room.add(BlockPos.asLong(x, y, z));
					}
					if (roofed && rc) {
						roofedReached++;
						if (!sp.enclosed(x, y, z)) {
							openRoofed++;
						}
					}
					if (!roofed) {
						unroofed.add(BlockPos.asLong(x, y, z));
					}
				}
			}
		}
		m.put("walk_cells", walk);
		m.put("reached_cells", reached);
		m.put("room_cells", room.size());
		m.put("open_roofed_share", roofedReached == 0 ? Double.NaN : openRoofed / (double) roofedReached);

		// POIs by cell
		LongOpenHashSet poiCells = new LongOpenHashSet();
		for (Poi p : bp.pois()) {
			if (p.type() != PoiType.DOOR) {
				poiCells.add(p.pos().asLong());
			}
		}

		// --- rooms: 4-connected enclosed cells on one level
		int rooms = 0;
		int no3x3 = 0;
		int cramped = 0;
		int empty = 0;
		int purposeless = 0;
		int unreachedCells = 0;
		int unreachedUsed = 0;
		int pockets = 0;
		int roomCellsReached = 0;
		int furnitureTotal = 0;
		int tight = 0;
		int dark = 0;
		int lightMin = Integer.MAX_VALUE;
		long lightSum = 0;
		Map<Integer, int[]> levels = new TreeMap<>();
		LongOpenHashSet seen = new LongOpenHashSet();
		for (long start : room) {
			if (!seen.add(start)) {
				continue;
			}
			LongArrayList comp = new LongArrayList();
			LongArrayFIFOQueue q = new LongArrayFIFOQueue();
			q.enqueue(start);
			while (!q.isEmpty()) {
				long k = q.dequeueLong();
				comp.add(k);
				int x = BlockPos.getX(k);
				int y = BlockPos.getY(k);
				int z = BlockPos.getZ(k);
				for (Direction d : Direction.Plane.HORIZONTAL) {
					long n = BlockPos.asLong(x + d.getStepX(), y, z + d.getStepZ());
					if (room.contains(n) && seen.add(n)) {
						q.enqueue(n);
					}
				}
			}
			int y = BlockPos.getY(comp.getLong(0));
			boolean any = false;
			for (int i = 0; i < comp.size(); i++) {
				long k = comp.getLong(i);
				any |= sp.reached(BlockPos.getX(k), y, BlockPos.getZ(k));
			}
			int[] lv = levels.computeIfAbsent(y, k -> new int[2]);
			lv[0] += comp.size();
			if (!any) {
				unreachedCells += comp.size();
				if (comp.size() >= 4) {
					pockets++;
				}
				// furnished (furniture, a POI or a door beside it: meant to be used) or a sealed void under a roof
				boolean used = false;
				for (int i = 0; i < comp.size() && !used && comp.size() >= 3; i++) {
					long k = comp.getLong(i);
					int x = BlockPos.getX(k);
					int z = BlockPos.getZ(k);
					for (int dy = -1; dy <= 1 && !used; dy++) {
						used = poiCells.contains(BlockPos.asLong(x, y + dy, z));
					}
					for (int dx = -1; dx <= 1 && !used; dx++) {
						for (int dz = -1; dz <= 1 && !used; dz++) {
							BlockState fs = g.at(x + dx, y, z + dz);
							used = g.placed(x + dx, y, z + dz) && (furniture(fs, 0) && !(fs.getBlock() instanceof StairBlock)
								|| fs.getBlock() instanceof DoorBlock || fs.getBlock() instanceof BedBlock);
						}
					}
				}
				if (used) {
					unreachedUsed += comp.size();
					at(where, "unreached_furnished_room", lf0, comp.getLong(0));
				} else {
					at(where, "sealed_void", lf0, comp.getLong(0));
				}
				continue;
			}
			lv[1] += comp.size();
			if (comp.size() < 4) {
				continue;
			}
			rooms++;
			roomCellsReached += comp.size();
			int sq = maxSquare(comp);
			if (comp.size() >= 6 && sq < 3) {
				no3x3++;
			}
			// furniture round the room's cells at feet and head height, and what POIs it holds
			LongOpenHashSet furn = new LongOpenHashSet();
			int pois = 0;
			LongOpenHashSet exits = new LongOpenHashSet();
			LongOpenHashSet compSet = new LongOpenHashSet(comp);
			for (int i = 0; i < comp.size(); i++) {
				long k = comp.getLong(i);
				int x = BlockPos.getX(k);
				int z = BlockPos.getZ(k);
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						for (int dy = 0; dy <= 1; dy++) {
							int fx = x + dx;
							int fy = y + dy;
							int fz = z + dz;
							if (g.placed(fx, fy, fz) && furniture(g.at(fx, fy, fz), dy)) {
								furn.add(BlockPos.asLong(fx, fy, fz));
							}
						}
					}
				}
				for (int dy = -1; dy <= 1; dy++) {
					if (poiCells.contains(BlockPos.asLong(x, y + dy, z))) {
						pois++;
					}
				}
				for (Direction d : Direction.Plane.HORIZONTAL) {
					int nx = x + d.getStepX();
					int nz = z + d.getStepZ();
					long n = BlockPos.asLong(nx, y, nz);
					if (!compSet.contains(n)) {
						for (int dy = -1; dy <= 1; dy++) {
							if (sp.reached(nx, y + dy, nz)) {
								exits.add(BlockPos.asLong(nx, y + dy, nz));
								break;
							}
						}
					}
				}
				int l = lf.at(x, y, z);
				lightMin = Math.min(lightMin, l);
				lightSum += l;
				if (l < 8) {
					dark++;
				}
				int roofY = sp.roofY(x, y, z);
				if (roofY != Integer.MAX_VALUE && roofY - y <= 2) {
					tight++;
				}
			}
			furnitureTotal += furn.size();
			if (comp.size() >= 6 && furn.isEmpty()) {
				empty++;
			}
			if ((pois > 0 || furn.size() >= 2) && comp.size() < 6) {
				cramped++;
				at(where, "cramped_room", lf0, comp.getLong(0));
			}
			if (exits.size() <= 2 && pois == 0 && furn.size() <= 1) {
				purposeless++;
				at(where, "purposeless_room", lf0, comp.getLong(0));
			}
		}
		int unreachedLevels = 0;
		for (int[] lv : levels.values()) {
			if (lv[0] >= 6 && lv[1] == 0) {
				unreachedLevels++;
			}
		}
		m.put("unreached_room_cells", unreachedCells);
		m.put("unreached_furnished_cells", unreachedUsed);
		m.put("unreached_pockets", pockets);
		m.put("unreached_levels", unreachedLevels);
		m.put("rooms", rooms);
		m.put("rooms_no3x3", no3x3);
		m.put("cramped_rooms", cramped);
		m.put("empty_rooms", empty);
		m.put("purposeless_rooms", purposeless);
		m.put("tight_headroom_share", roomCellsReached == 0 ? Double.NaN : tight / (double) roomCellsReached);
		m.put("light_min_room", roomCellsReached == 0 ? Double.NaN : (double) lightMin);
		m.put("light_mean_room", roomCellsReached == 0 ? Double.NaN : lightSum / (double) roomCellsReached);
		m.put("dark_room_cells", dark);
		m.put("light_unmet", r.unmet().size());
		m.put("furniture_per_cell", roomCellsReached == 0 ? Double.NaN : furnitureTotal / (double) roomCellsReached);
		m.put("empty_room_ratio", rooms == 0 ? Double.NaN : empty / (double) rooms);

		// --- doors, flights, ladders, beds, workstations, hazards: one pass over what the building places in its plot
		int doors = 0;
		int blocked = 0;
		int facesWall = 0;
		int facesDrop = 0;
		int doorsUnreached = 0;
		int flights = 0;
		int badFlights = 0;
		int ladders = 0;
		int deadLadders = 0;
		int bedsBlocked = 0;
		int stations = 0;
		int stationsUnusable = 0;
		int lids = 0;
		int lava = 0;
		int waterRoofed = 0;
		for (int y = g.y0; y < g.y0 + g.sy; y++) {
			for (int z = g.z0; z < g.z0 + g.sz; z++) {
				for (int x = g.x0; x < g.x0 + g.sx; x++) {
					if (!g.placed(x, y, z) || !nearPlot(g, x, z)) {
						continue;
					}
					BlockState s = g.at(x, y, z);
					if (s.isAir()) {
						continue;
					}
					Block b = s.getBlock();
					if (b instanceof DoorBlock && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
						doors++;
						Direction f = s.getValue(DoorBlock.FACING);
						boolean bad = false;
						for (Direction d : new Direction[]{f, f.getOpposite()}) {
							int nx = x + d.getStepX();
							int nz = z + d.getStepZ();
							if (!doorSideOk(g, sp, nx, y, nz)) {
								bad = true;
								if (!Spaces.passable(g.at(nx, y, nz)) || !Spaces.passable(g.at(nx, y + 1, nz))) {
									facesWall++;
								} else {
									facesDrop++;
								}
							}
						}
						if (bad) {
							blocked++;
							at(where, "blocked_door", lf0, BlockPos.asLong(x, y, z));
						}
						if (!sp.reached(x, y, z)) {
							doorsUnreached++;
							at(where, "unreached_door", lf0, BlockPos.asLong(x, y, z));
						}
					} else if (b instanceof StairBlock && s.getValue(StairBlock.HALF) == Half.BOTTOM) {
						int fl = flight(g, sp, x, y, z, s);
						if (fl != 0) {
							flights++;
							if (fl < 0) {
								badFlights++;
								at(where, "bad_flight", lf0, BlockPos.asLong(x, y, z));
							}
						}
					} else if (b instanceof LadderBlock && !(g.at(x, y + 1, z).getBlock() instanceof LadderBlock)
						&& g.at(x, y - 1, z).getBlock() instanceof LadderBlock) {
						ladders++;
						if (!ladderExit(g, sp, x, y, z)) {
							deadLadders++;
							at(where, "dead_ladder", lf0, BlockPos.asLong(x, y, z));
						}
					} else if (b instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD) {
						Direction f = s.getValue(BedBlock.FACING);
						int fx = x - f.getStepX();
						int fz = z - f.getStepZ();
						boolean free = false;
						for (int[] half : new int[][]{{x, z}, {fx, fz}}) {
							for (Direction d : Direction.Plane.HORIZONTAL) {
								int nx = half[0] + d.getStepX();
								int nz = half[1] + d.getStepZ();
								if (nx == x && nz == z || nx == fx && nz == fz) {
									continue;
								}
								free |= sp.reached(nx, y, nz);
							}
						}
						if (!free) {
							bedsBlocked++;
							at(where, "bed_blocked", lf0, BlockPos.asLong(x, y, z));
						}
					} else if (workstation(s)) {
						stations++;
						boolean ok = false;
						for (Direction d : Direction.Plane.HORIZONTAL) {
							int nx = x + d.getStepX();
							int nz = z + d.getStepZ();
							ok |= sp.reached(nx, y, nz) || sp.reached(nx, y - 1, nz) || sp.reached(nx, y + 1, nz);
						}
						ok |= sp.reached(x, y + 1, z) && !(b instanceof net.minecraft.world.level.block.ChestBlock);
						if (!ok) {
							stationsUnusable++;
							at(where, "station_unusable:" + path(s), lf0, BlockPos.asLong(x, y, z));
						}
						if (b instanceof net.minecraft.world.level.block.ChestBlock && g.at(x, y + 1, z).isRedstoneConductor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
							lids++;
							at(where, "chest_lid", lf0, BlockPos.asLong(x, y, z));
						}
					} else if (s.getFluidState().is(FluidTags.LAVA)) {
						lava++;
						at(where, "lava", lf0, BlockPos.asLong(x, y, z));
					} else if (b instanceof LiquidBlock && s.getFluidState().is(FluidTags.WATER) && g.inPlot(x, z) && sp.roofed(x, y, z)) {
						waterRoofed++;
					}
				}
			}
		}
		m.put("doors", doors);
		m.put("blocked_doors", blocked);
		m.put("doors_facing_wall", facesWall);
		m.put("doors_facing_drop", facesDrop);
		m.put("unreached_doors", doorsUnreached);
		int entrancesUnreached = 0;
		for (BlockPos e : bp.entrances()) {
			boolean ok = false;
			for (int dy = -2; dy <= 1; dy++) {
				ok |= sp.reached(e.getX(), e.getY() + dy, e.getZ());
			}
			if (!ok) {
				entrancesUnreached++;
				at(where, "entrance_unreached", lf0, e.asLong());
			}
		}
		m.put("entrances_unreached", entrancesUnreached);
		m.put("flights", flights);
		m.put("bad_flights", badFlights);
		m.put("ladders", ladders);
		m.put("dead_ladders", deadLadders);
		m.put("beds_no_free_side", bedsBlocked);
		m.put("workstations", stations);
		m.put("workstations_unusable", stationsUnusable);
		m.put("chest_lids_blocked", lids);
		m.put("lava", lava);
		m.put("water_roofed", waterRoofed);

		// --- holes in roofs: small open-sky patches with roof all round them
		int holes = 0;
		LongOpenHashSet hseen = new LongOpenHashSet();
		for (long start : unroofed) {
			if (!hseen.add(start)) {
				continue;
			}
			LongArrayList comp = new LongArrayList();
			LongArrayFIFOQueue q = new LongArrayFIFOQueue();
			q.enqueue(start);
			boolean closed = true;
			while (!q.isEmpty()) {
				long k = q.dequeueLong();
				comp.add(k);
				int x = BlockPos.getX(k);
				int y = BlockPos.getY(k);
				int z = BlockPos.getZ(k);
				for (Direction d : Direction.Plane.HORIZONTAL) {
					int nx = x + d.getStepX();
					int nz = z + d.getStepZ();
					long n = BlockPos.asLong(nx, y, nz);
					if (unroofed.contains(n)) {
						if (hseen.add(n)) {
							q.enqueue(n);
						}
					} else if (!g.inPlot(nx, nz) || !sp.roofed(nx, y, nz)) {
						closed = false;
					}
				}
			}
			if (closed && comp.size() <= 6) {
				holes += comp.size();
				at(where, "roof_hole", lf0, comp.getLong(0));
			}
		}
		m.put("roof_hole_cells", holes);

		// --- edges with a fall of four or more (no rail, no water below)
		int falls = 0;
		for (long k : reachedPlot) {
			int x = BlockPos.getX(k);
			int y = BlockPos.getY(k);
			int z = BlockPos.getZ(k);
			if (sp.wet(x, y, z)) {
				continue;
			}
			for (Direction d : Direction.Plane.HORIZONTAL) {
				int nx = x + d.getStepX();
				int nz = z + d.getStepZ();
				if (!Spaces.passable(g.at(nx, y, nz)) || !Spaces.passable(g.at(nx, y + 1, nz))) {
					continue;
				}
				// how far someone stepping off here falls: passable cells below the feet until something stops them
				int drop = 0;
				boolean water = false;
				for (int dy = -1; dy >= -12 && g.contains(nx, y + dy, nz); dy--) {
					BlockState below = g.at(nx, y + dy, nz);
					if (!below.getFluidState().isEmpty()) {
						water = true;
						break;
					}
					if (!Spaces.passable(below) || below.getBlock() instanceof LadderBlock) {
						break;
					}
					drop++;
				}
				if (drop < 4) {
					continue;
				}
				if (!water) {
					falls++;
					at(where, "fall_edge", lf0, k);
					break;
				}
			}
		}
		m.put("fall_edge_cells", falls);
		m.put("where", String.join(" ", where.values()));
	}

	/** The plot frame and floor, to report findings in local coordinates (x across the front, y above the floor, z back). */
	private record Frame(LocalFrame f, int floor) {
	}

	private static void at(Map<String, String> where, String key, Frame fr, long k) {
		if (where.size() < 12) {
			int x = BlockPos.getX(k);
			int z = BlockPos.getZ(k);
			where.putIfAbsent(key, key + "@" + fr.f().localX(x, z) + "/" + (BlockPos.getY(k) - fr.floor()) + "/" + fr.f().localZ(x, z));
		}
	}

	private static boolean nearPlot(Grid g, int x, int z) {
		return g.inPlot(x, z) || g.inPlot(x + 1, z) || g.inPlot(x - 1, z) || g.inPlot(x, z + 1) || g.inPlot(x, z - 1);
	}

	private static boolean step(BlockState s) {
		return s.getBlock() instanceof StairBlock && s.getValue(StairBlock.HALF) == Half.BOTTOM
			|| s.getBlock() instanceof SlabBlock && s.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
	}

	/** Someone can stand on this side of a door: level, a step down, or a stair or slab up. */
	private static boolean doorSideOk(Grid g, Spaces sp, int x, int y, int z) {
		return sp.walkable(x, y, z) || sp.walkable(x, y - 1, z) && Spaces.passable(g.at(x, y, z)) || step(g.at(x, y, z)) && sp.walkable(x, y + 1, z)
			|| g.at(x, y, z).getBlock() instanceof DoorBlock;
	}

	/**
	 * A flight of stairs starting at this stair (two or more climbing the same way, one up and one on) that someone walks:
	 * 1 when it leads to a floor with headroom all the way, -1 when it ends against a wall or ceiling or has a step
	 * without headroom, 0 when this stair does not start a walked flight (a chair, a roof course, the middle of a flight).
	 */
	private static int flight(Grid g, Spaces sp, int x, int y, int z, BlockState s) {
		Direction f = s.getValue(StairBlock.FACING);
		BlockState before = g.at(x - f.getStepX(), y - 1, z - f.getStepZ());
		if (sameStair(before, f)) {
			return 0;
		}
		int cx = x;
		int cy = y;
		int cz = z;
		int len = 1;
		boolean headroom = sp.walkable(x, y + 1, z);
		while (sameStair(g.at(cx + f.getStepX(), cy + 1, cz + f.getStepZ()), f) && len < 64) {
			cx += f.getStepX();
			cy++;
			cz += f.getStepZ();
			len++;
			headroom &= sp.walkable(cx, cy + 1, cz);
		}
		if (len < 2) {
			return 0;
		}
		boolean walked = sp.reached(x, y + 1, z) || sp.reached(x - f.getStepX(), y, z - f.getStepZ()) || sp.reached(cx, cy + 1, cz);
		if (!walked || !g.inPlot(x, z) && !g.inPlot(cx, cz)) {
			return 0;
		}
		// the top: a floor ahead, or to either side (a turn, a landing)
		boolean out = false;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (d == f.getOpposite()) {
				continue;
			}
			int nx = cx + d.getStepX();
			int nz = cz + d.getStepZ();
			out |= sp.walkable(nx, cy + 1, nz) || sp.walkable(nx, cy + 2, nz) && Spaces.passable(g.at(cx, cy + 3, cz));
		}
		return out && headroom ? 1 : -1;
	}

	private static boolean sameStair(BlockState s, Direction f) {
		return s.getBlock() instanceof StairBlock && s.getValue(StairBlock.HALF) == Half.BOTTOM && s.getValue(StairBlock.FACING) == f;
	}

	/** The top of a ladder lets someone off: a floor beside it, at its top or a block up. */
	private static boolean ladderExit(Grid g, Spaces sp, int x, int y, int z) {
		if (sp.walkable(x, y + 1, z)) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				if (sp.walkable(x + d.getStepX(), y + 1, z + d.getStepZ()) || sp.walkable(x + d.getStepX(), y + 2, z + d.getStepZ())) {
					return true;
				}
			}
		}
		for (Direction d : Direction.Plane.HORIZONTAL) {
			int nx = x + d.getStepX();
			int nz = z + d.getStepZ();
			for (int dy = 0; dy <= 1; dy++) {
				if (sp.walkable(nx, y + dy, nz) && !(g.at(nx, y + dy, nz).getBlock() instanceof LadderBlock)) {
					return true;
				}
			}
		}
		return false;
	}

	/** The largest square of cells of one level (a room's clear floor). */
	private static int maxSquare(LongArrayList comp) {
		int x0 = Integer.MAX_VALUE;
		int z0 = Integer.MAX_VALUE;
		int x1 = Integer.MIN_VALUE;
		int z1 = Integer.MIN_VALUE;
		for (int i = 0; i < comp.size(); i++) {
			long k = comp.getLong(i);
			x0 = Math.min(x0, BlockPos.getX(k));
			x1 = Math.max(x1, BlockPos.getX(k));
			z0 = Math.min(z0, BlockPos.getZ(k));
			z1 = Math.max(z1, BlockPos.getZ(k));
		}
		int w = x1 - x0 + 1;
		int d = z1 - z0 + 1;
		boolean[] in = new boolean[w * d];
		for (int i = 0; i < comp.size(); i++) {
			long k = comp.getLong(i);
			in[(BlockPos.getZ(k) - z0) * w + BlockPos.getX(k) - x0] = true;
		}
		int[] dp = new int[w * d];
		int best = 0;
		for (int z = 0; z < d; z++) {
			for (int x = 0; x < w; x++) {
				int i = z * w + x;
				if (!in[i]) {
					continue;
				}
				dp[i] = x == 0 || z == 0 ? 1 : 1 + Math.min(dp[i - 1], Math.min(dp[i - w], dp[i - w - 1]));
				best = Math.max(best, dp[i]);
			}
		}
		return best;
	}

	static String path(BlockState s) {
		return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
	}

	/** A block a person works at or takes from (a job's block, a chest, a barrel). */
	static boolean workstation(BlockState s) {
		String p = path(s);
		return switch (p) {
			case "crafting_table", "furnace", "blast_furnace", "smoker", "grindstone", "stonecutter", "loom", "cartography_table", "fletching_table",
				 "smithing_table", "brewing_stand", "composter", "lectern", "chest", "trapped_chest", "barrel" -> true;
			default -> p.endsWith("anvil") || p.endsWith("cauldron");
		};
	}

	/** Furniture in a room: what a person uses or sits at, and what dresses a room (rugs, pots, shelves, banners). */
	static boolean furniture(BlockState s, int dy) {
		Block b = s.getBlock();
		if (s.isAir() || b instanceof DoorBlock || b instanceof IronBarsBlock || b instanceof LadderBlock || b instanceof FenceGateBlock || b instanceof LiquidBlock
			|| b instanceof TrapDoorBlock && dy > 0) {
			return false;
		}
		if (workstation(s) || b instanceof BedBlock || b instanceof CarpetBlock) {
			return true;
		}
		String p = path(s);
		if (p.contains("shelf") || p.endsWith("_table") || p.endsWith("pot") || p.contains("banner") || p.endsWith("_sign") || p.equals("jukebox")
			|| p.equals("note_block") || p.equals("bell") || p.endsWith("campfire") || p.contains("pressure_plate") || p.endsWith("cake")
			|| p.contains("candle") || p.equals("hay_block") || p.endsWith("_head") || p.endsWith("_skull") || p.equals("beehive") || p.equals("bee_nest")) {
			return true;
		}
		// chairs and benches (stairs), tables and counters (top slabs), table legs (fences) at feet height
		return dy == 0 && (b instanceof StairBlock || b instanceof SlabBlock && s.getValue(SlabBlock.TYPE) == SlabType.TOP || b instanceof FenceBlock
			|| b instanceof TrapDoorBlock);
	}

	// =========================================================================================== detail

	/** The building in its local frame: the last state per cell and whether the body's stages placed it. */
	static final class Local {
		final int w;
		final int dp;
		final int y0;
		final int h;
		final BlockState[] states;
		final boolean[] body;

		Local(BlueprintImpl bp) {
			PlotSpec spec = bp.spec();
			LocalFrame f = new LocalFrame(spec);
			this.w = f.frontage();
			this.dp = f.depth();
			var b = bp.bounds();
			this.y0 = b.minY() - spec.floorY();
			this.h = b.maxY() - b.minY() + 1;
			this.states = new BlockState[this.w * (this.dp + 1) * this.h];
			this.body = new boolean[this.states.length];
			for (int s = 0; s < bp.stageCount(); s++) {
				boolean structure = STRUCTURE.contains(bp.stageKind(s));
				for (int i = 0; i < bp.stepCount(s); i++) {
					int st = s;
					bp.step(s, i, (x, y, z, state, tag) -> {
						int lx = f.localX(x, z);
						int lz = f.localZ(x, z);
						int ly = y - spec.floorY();
						if (lx < 0 || lx >= this.w || lz < -1 || lz >= this.dp || ly < this.y0 || ly >= this.y0 + this.h) {
							return;
						}
						int k = idx(lx, ly, lz);
						this.states[k] = state;
						this.body[k] = structure && !state.isAir();
					});
				}
			}
		}

		int idx(int lx, int ly, int lz) {
			return ((ly - this.y0) * (this.dp + 1) + (lz + 1)) * this.w + lx;
		}

		BlockState body(int lx, int ly, int lz) {
			if (lx < 0 || lx >= this.w || lz < -1 || lz >= this.dp || ly < this.y0 || ly >= this.y0 + this.h) {
				return null;
			}
			int k = idx(lx, ly, lz);
			return this.body[k] ? this.states[k] : null;
		}

		BlockState any(int lx, int ly, int lz) {
			if (lx < 0 || lx >= this.w || lz < -1 || lz >= this.dp || ly < this.y0 || ly >= this.y0 + this.h) {
				return null;
			}
			return this.states[idx(lx, ly, lz)];
		}

		int top() {
			return this.y0 + this.h - 1;
		}
	}

	private static boolean glass(BlockState s) {
		return s.getBlock() instanceof IronBarsBlock || path(s).contains("glass");
	}

	private static boolean full(BlockState s) {
		return s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
	}

	/** One facade seen square-on from outside: per column u and height ly the first body block and its depth from the plot edge. */
	private record Facade(int n, int rows, int[] depth, BlockState[] hit) {
		int d(int u, int r) {
			return this.depth[r * this.n + u];
		}

		BlockState s(int u, int r) {
			return this.hit[r * this.n + u];
		}
	}

	private static Facade facade(Local v, int side) {
		int n = side < 2 ? v.w : v.dp;
		int far = side < 2 ? v.dp + 1 : v.w;
		int rows = Math.max(0, v.top() + 1);
		int[] depth = new int[n * rows];
		BlockState[] hit = new BlockState[n * rows];
		java.util.Arrays.fill(depth, -1);
		for (int r = 0; r < rows; r++) {
			for (int u = 0; u < n; u++) {
				for (int k = 0; k < far; k++) {
					int lx;
					int lz;
					switch (side) {
						case 0 -> {
							lx = u;
							lz = k - 1;
						}
						case 1 -> {
							lx = v.w - 1 - u;
							lz = v.dp - 1 - k;
						}
						case 2 -> {
							lx = k;
							lz = v.dp - 1 - u;
						}
						default -> {
							lx = v.w - 1 - k;
							lz = u;
						}
					}
					BlockState s = v.body(lx, r, lz);
					if (s != null) {
						depth[r * n + u] = k;
						hit[r * n + u] = s;
						break;
					}
				}
			}
		}
		return new Facade(n, rows, depth, hit);
	}

	static void detail(BlueprintImpl bp, Map<String, Object> m) {
		Local v = new Local(bp);
		double reliefSum = 0;
		int reliefN = 0;
		int patchMax = 0;
		double blocksSum = 0;
		double domSum = 0;
		int palN = 0;
		double winSum = 0;
		int winN = 0;
		int blind = 0;
		double overSum = 0;
		int overN = 0;
		double repSum = 0;
		int repN = 0;
		for (int side = 0; side < 4; side++) {
			Facade f = facade(v, side);
			if (f.rows() == 0) {
				continue;
			}
			// the wall plane: the commonest depth of full blocks
			Map<Integer, Integer> hist = new HashMap<>();
			for (int r = 0; r < f.rows(); r++) {
				for (int u = 0; u < f.n(); u++) {
					if (f.d(u, r) >= 0 && full(f.s(u, r)) && !glass(f.s(u, r))) {
						hist.merge(f.d(u, r), 1, Integer::sum);
					}
				}
			}
			if (hist.isEmpty()) {
				continue;
			}
			int plane = hist.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
			// rows of wall: at least 40% of the side at the plane (within a block)
			boolean[] zone = new boolean[f.rows()];
			int zoneTop = -1;
			int zoneRows = 0;
			for (int r = 0; r < f.rows(); r++) {
				int c = 0;
				for (int u = 0; u < f.n(); u++) {
					// the wall itself: blocks on the plane (glass and doors too), not eave courses of stairs and slabs
					if (f.d(u, r) == plane && !(f.s(u, r).getBlock() instanceof StairBlock) && !(f.s(u, r).getBlock() instanceof SlabBlock)) {
						c++;
					}
				}
				if (c >= 0.4 * f.n() && c >= 2) {
					zone[r] = true;
					zoneTop = r;
					zoneRows++;
				}
			}
			if (zoneRows == 0) {
				continue;
			}
			int cells = 0;
			int relief = 0;
			Map<Block, Integer> pal = new HashMap<>();
			boolean[] glassCell = new boolean[f.n() * f.rows()];
			boolean[] opening = new boolean[f.n() * f.rows()];
			for (int r = 0; r < f.rows(); r++) {
				if (!zone[r]) {
					continue;
				}
				for (int u = 0; u < f.n(); u++) {
					int d = f.d(u, r);
					BlockState s = f.s(u, r);
					if (d >= 0 && Math.abs(d - plane) <= 2 && glass(s)) {
						glassCell[r * f.n() + u] = true;
						continue;
					}
					if (d < 0 || d > plane + 2) {
						// a hole in the wall where the wall stands above and below it, or to both sides
						boolean vert = r > 0 && r + 1 < f.rows() && near(f, u, r - 1, plane) && near(f, u, r + 1, plane);
						boolean horiz = u > 0 && u + 1 < f.n() && near(f, u - 1, r, plane) && near(f, u + 1, r, plane);
						if (vert && horiz) {
							opening[r * f.n() + u] = true;
						}
						continue;
					}
					if (d < plane - 2 || s.getBlock() instanceof DoorBlock) {
						continue;
					}
					cells++;
					pal.merge(s.getBlock(), 1, Integer::sum);
					if (d != plane || !full(s)) {
						relief++;
					}
				}
			}
			if (cells >= 6) {
				reliefSum += relief / (double) cells;
				reliefN++;
				blocksSum += pal.size();
				domSum += pal.values().stream().mapToInt(Integer::intValue).max().orElse(0) / (double) cells;
				palN++;
			}
			// the largest flat patch of one block at the plane
			boolean[] seen = new boolean[f.n() * f.rows()];
			for (int r = 0; r < f.rows(); r++) {
				for (int u = 0; u < f.n(); u++) {
					int i = r * f.n() + u;
					if (seen[i] || !zone[r] || f.d(u, r) != plane || !full(f.s(u, r)) || glass(f.s(u, r))) {
						continue;
					}
					Block blk = f.s(u, r).getBlock();
					int size = 0;
					java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
					q.add(new int[]{u, r});
					seen[i] = true;
					while (!q.isEmpty()) {
						int[] c = q.poll();
						size++;
						for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
							int nu = c[0] + o[0];
							int nr = c[1] + o[1];
							if (nu < 0 || nr < 0 || nu >= f.n() || nr >= f.rows()) {
								continue;
							}
							int j = nr * f.n() + nu;
							if (!seen[j] && zone[nr] && f.d(nu, nr) == plane && f.s(nu, nr) != null && f.s(nu, nr).getBlock() == blk) {
								seen[j] = true;
								q.add(new int[]{nu, nr});
							}
						}
					}
					patchMax = Math.max(patchMax, size);
				}
			}
			// windows: groups of glass, and openings in the wall
			int windows = components(glassCell, f.n(), f.rows()) + components(opening, f.n(), f.rows());
			if (f.n() >= 5 && zoneRows >= 2) {
				winSum += windows;
				winN++;
				if (windows == 0) {
					blind++;
				}
			}
			// eaves: how far the roof above the wall stands out from the plane (median over the columns that have roof)
			List<Integer> over = new ArrayList<>();
			for (int u = 0; u < f.n(); u++) {
				int min = Integer.MAX_VALUE;
				for (int r = zoneTop + 1; r < f.rows(); r++) {
					if (f.d(u, r) >= 0) {
						min = Math.min(min, f.d(u, r));
					}
				}
				if (min != Integer.MAX_VALUE) {
					over.add(plane - min);
				}
			}
			if (!over.isEmpty()) {
				over.sort(null);
				overSum += Math.max(0, over.get(over.size() / 2));
			}
			overN++;
			// repeated modules: columns of the wall that repeat another column exactly
			Set<String> cols = new HashSet<>();
			int colN = 0;
			for (int u = 0; u < f.n(); u++) {
				StringBuilder sb = new StringBuilder();
				boolean anyHit = false;
				for (int r = 0; r < f.rows(); r++) {
					if (zone[r]) {
						int d = f.d(u, r);
						anyHit |= d >= 0;
						sb.append(d < 0 ? "-" : path(f.s(u, r)) + "@" + (d - plane)).append(';');
					}
				}
				if (anyHit) {
					colN++;
					cols.add(sb.toString());
				}
			}
			if (colN >= 4) {
				repSum += 1 - cols.size() / (double) colN;
				repN++;
			}
		}
		m.put("relief_share", reliefN == 0 ? Double.NaN : reliefSum / reliefN);
		m.put("flat_patch_max", patchMax);
		m.put("facade_blocks", palN == 0 ? Double.NaN : blocksSum / palN);
		m.put("dominant_share", palN == 0 ? Double.NaN : domSum / palN);
		m.put("windows_per_side", winN == 0 ? Double.NaN : winSum / winN);
		m.put("blind_sides", blind);
		m.put("overhang_mean", overN == 0 ? Double.NaN : overSum / overN);
		m.put("repeat_cols", repN == 0 ? Double.NaN : repSum / repN);

		// the roof seen from above: which ways its slopes face, its hips and valleys, its levels
		Set<Direction> facings = EnumSet.noneOf(Direction.class);
		int corners = 0;
		Set<Integer> levels = new HashSet<>();
		int bx0 = Integer.MAX_VALUE;
		int bx1 = Integer.MIN_VALUE;
		int bz0 = Integer.MAX_VALUE;
		int bz1 = Integer.MIN_VALUE;
		int top = -1;
		for (int lx = 0; lx < v.w; lx++) {
			for (int lz = 0; lz < v.dp; lz++) {
				for (int ly = v.top(); ly >= 0; ly--) {
					BlockState s = v.body(lx, ly, lz);
					if (s == null) {
						continue;
					}
					bx0 = Math.min(bx0, lx);
					bx1 = Math.max(bx1, lx);
					bz0 = Math.min(bz0, lz);
					bz1 = Math.max(bz1, lz);
					top = Math.max(top, ly);
					if (ly >= 2) {
						levels.add(ly);
						if (s.getBlock() instanceof StairBlock) {
							facings.add(s.getValue(StairBlock.FACING));
							if (s.getValue(StairBlock.SHAPE) != StairsShape.STRAIGHT) {
								corners++;
							}
						}
					}
					break;
				}
			}
		}
		m.put("roof_facings", facings.size());
		m.put("roof_corners", corners);
		m.put("roof_levels", levels.size());
		m.put("roof_complexity", facings.size() + Math.min(corners, 8) / 2.0 + Math.min(levels.size(), 9) / 3.0);
		m.put("height", top + 1);
		if (bx0 <= bx1) {
			m.put("height_ratio", (top + 1) / (double) Math.max(bx1 - bx0 + 1, bz1 - bz0 + 1));
			// mirror likeness, left against right, over the body
			int n = 0;
			int same = 0;
			for (int lx = bx0; lx <= bx1; lx++) {
				for (int lz = bz0; lz <= bz1; lz++) {
					for (int ly = 0; ly <= v.top(); ly++) {
						BlockState s = v.body(lx, ly, lz);
						if (s == null) {
							continue;
						}
						n++;
						BlockState o = v.body(bx0 + bx1 - lx, ly, lz);
						if (o != null && o.getBlock() == s.getBlock()) {
							same++;
						}
					}
				}
			}
			m.put("mirror_sym", n == 0 ? Double.NaN : same / (double) n);
		} else {
			m.put("height_ratio", Double.NaN);
			m.put("mirror_sym", Double.NaN);
		}
	}

	private static boolean near(Facade f, int u, int r, int plane) {
		int d = f.d(u, r);
		return d >= 0 && Math.abs(d - plane) <= 1;
	}

	private static int components(boolean[] on, int n, int rows) {
		boolean[] seen = new boolean[on.length];
		int c = 0;
		for (int i = 0; i < on.length; i++) {
			if (!on[i] || seen[i]) {
				continue;
			}
			c++;
			java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
			q.add(i);
			seen[i] = true;
			while (!q.isEmpty()) {
				int k = q.poll();
				int u = k % n;
				int r = k / n;
				int[][] os = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
				for (int[] o : os) {
					int nu = u + o[0];
					int nr = r + o[1];
					if (nu >= 0 && nr >= 0 && nu < n && nr < rows) {
						int j = nr * n + nu;
						if (on[j] && !seen[j]) {
							seen[j] = true;
							q.add(j);
						}
					}
				}
			}
		}
		return c;
	}

	/** The cells a blueprint fills in its plot's frame, as CultureSimilarity reads them (air and fluids aside). */
	static LongOpenHashSet shape(BlueprintImpl bp) {
		PlotSpec spec = bp.spec();
		int ox = spec.origin().getX();
		int oy = spec.floorY();
		int oz = spec.origin().getZ();
		LongOpenHashSet cells = new LongOpenHashSet();
		for (int st = 0; st < bp.stageCount(); st++) {
			for (int i = 0; i < bp.stepCount(st); i++) {
				bp.step(st, i, (x, y, z, state, tag) -> {
					long k = Voxels.key(x - ox, y - oy, z - oz);
					if (state.isAir() || !state.getFluidState().isEmpty()) {
						cells.remove(k);
					} else {
						cells.add(k);
					}
				});
			}
		}
		return cells;
	}

	static double jaccard(LongOpenHashSet a, LongOpenHashSet b) {
		if (a.isEmpty() && b.isEmpty()) {
			return 1;
		}
		LongOpenHashSet small = a.size() <= b.size() ? a : b;
		LongOpenHashSet large = small == a ? b : a;
		int both = 0;
		var it = small.iterator();
		while (it.hasNext()) {
			if (large.contains(it.nextLong())) {
				both++;
			}
		}
		return both / (double) (a.size() + b.size() - both);
	}

	static {
		// touch Blocks so the registries are bound before the first measure on a worker thread
		Blocks.AIR.defaultBlockState();
	}
}
