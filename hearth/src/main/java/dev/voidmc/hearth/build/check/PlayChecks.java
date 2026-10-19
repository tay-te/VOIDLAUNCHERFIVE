package dev.voidmc.hearth.build.check;

import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.blueprint.LocalFrame;
import dev.voidmc.hearth.build.kit.light.Grid;
import dev.voidmc.hearth.build.kit.light.Spaces;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The play rules of the audit gate (docs/review/AUDIT.md §5 and §7 lane L0; BUILDING_GUIDE.md §8): what the October 2026
 * structure audit found players and folk shut out of, checked on every blueprint as {@link BlueprintChecks} checks the
 * rest. Every rule reads the light check's walk ({@link Spaces}: a 1 x 2 mover that steps up one block, drops three,
 * climbs ladders, passes doors and gates, and never stands on a candle, a lantern, a pot, a cake or a skull), so the
 * gate, the audit's columns and the light rule agree on where people can stand.
 *
 * <ul>
 * <li>{@link Rule#WALK}: the invariant walk again, with the light check's footing: a bed, a job site, a store or the main
 * door reached only by climbing pots and candles is not reached (the courtyard house, the karst pagoda).</li>
 * <li>{@link Rule#DOOR_SIDES}: every door has a standable cell on both sides (the audit's {@code blocked_doors}).</li>
 * <li>{@link Rule#LADDER_TOP}: every ladder's top has a cell to step off onto ({@code dead_ladders}).</li>
 * <li>{@link Rule#FURNISHED_REACHED}: every furnished room is reached by the walk ({@code unreached_furnished_cells}).</li>
 * <li>{@link Rule#FLOATING_PIECE}: no piece of the building stands on nothing (art review 2's probe): structure rests
 * on the ground or on other structure, never on leaves, plants, carpets or lamps; leaves and fixtures hang on structure.</li>
 * <li>{@link Rule#ROOF_HOLE}: no gap in a roof over covered space, over a floor or not (the wisteria rectory).</li>
 * </ul>
 * Which of these fail the tests is the {@link Gate}'s business: while the fix lanes work they are reported, and
 * archetypes that fail them today are listed in {@code hearth/gate/known/}.
 */
public final class PlayChecks {
	private PlayChecks() {
	}

	/** The rules, by the id the known-failure lists and reports use. */
	public enum Rule {
		WALK("walk", "beds, job sites, stores or the main door reached only by standing on pots, candles, lanterns, cakes or skulls"),
		DOOR_SIDES("door-sides", "doors without a standable cell on both sides"),
		LADDER_TOP("ladder-top", "ladder tops with no cell to step off onto"),
		FURNISHED_REACHED("furnished-reached", "furnished room cells the walk never reaches"),
		FLOATING_PIECE("floating-piece", "pieces standing on nothing (not on the ground or on structure)"),
		ROOF_HOLE("roof-hole", "roof gaps over covered space");

		public final String id;
		public final String what;

		Rule(String id, String what) {
			this.id = id;
			this.what = what;
		}

		public static Rule byId(String id) {
			for (Rule r : values()) {
				if (r.id.equals(id)) {
					return r;
				}
			}
			return null;
		}
	}

	/** One rule broken in one blueprint: how many times, and where first (local x/y/z, as the audit's {@code where}). */
	public record Finding(Rule rule, int count, String where) {
		public String text() {
			return "gate " + this.rule.id + ": " + this.count + " " + this.rule.what + ", e.g. " + this.where;
		}
	}

	/** Every rule over a blueprint on its terrain (for tools: the audit, previews, the walk). */
	public static List<Finding> evaluate(BlueprintImpl bp, dev.voidmc.hearth.api.TerrainProbe probe) {
		BlueprintChecks.World w = new BlueprintChecks.World(bp, probe);
		List<String> lax = new ArrayList<>();
		BlueprintChecks.reach(bp, w, lax);
		return evaluate(bp, w, LightCheck.evaluate(bp, w), lax.isEmpty());
	}

	/** Every rule over a blueprint, from what {@link BlueprintChecks#all} has already read. */
	public static List<Finding> evaluate(BlueprintImpl bp, BlueprintChecks.World w, LightCheck.Result r, boolean laxReachOk) {
		List<Finding> out = new ArrayList<>();
		PlotSpec spec = bp.spec();
		Local loc = new Local(new LocalFrame(spec), spec.floorY());
		Grid g = r.grid();
		Spaces sp = r.spaces();
		// the walk with the light check's footing (when the lax walk passes; when it fails the invariants fail anyway)
		if (laxReachOk) {
			List<String> strict = new ArrayList<>();
			BlueprintChecks.reach(bp, w, strict, true);
			if (!strict.isEmpty()) {
				out.add(new Finding(Rule.WALK, strict.size(), strict.get(0)));
			}
		}
		int[] doors = {0};
		int[] ladders = {0};
		String[] doorAt = {null};
		String[] ladderAt = {null};
		forPlaced(g, (x, y, z, s) -> {
			Block b = s.getBlock();
			if (b instanceof DoorBlock && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
				if (blockedDoor(g, sp, x, y, z, s)) {
					doors[0]++;
					if (doorAt[0] == null) {
						doorAt[0] = loc.at(x, y, z) + " " + name(s);
					}
				}
			} else if (b instanceof LadderBlock && ladderTop(g, x, y, z) && !ladderExit(g, sp, x, y, z)) {
				ladders[0]++;
				if (ladderAt[0] == null) {
					ladderAt[0] = loc.at(x, y, z);
				}
			}
		});
		if (doors[0] > 0) {
			out.add(new Finding(Rule.DOOR_SIDES, doors[0], doorAt[0]));
		}
		if (ladders[0] > 0) {
			out.add(new Finding(Rule.LADDER_TOP, ladders[0], ladderAt[0]));
		}
		int unreached = 0;
		String roomAt = null;
		for (Room room : rooms(g, sp, spec, bp.pois())) {
			if (!room.reached() && room.furnished()) {
				unreached += room.cells().size();
				if (roomAt == null) {
					long k = room.cells().getLong(0);
					roomAt = loc.at(BlockPos.getX(k), BlockPos.getY(k), BlockPos.getZ(k));
				}
			}
		}
		if (unreached > 0) {
			out.add(new Finding(Rule.FURNISHED_REACHED, unreached, roomAt));
		}
		Pieces pieces = floatingPieces(w);
		if (pieces.count() > 0) {
			long k = pieces.first();
			out.add(new Finding(Rule.FLOATING_PIECE, pieces.count(), loc.at(BlockPos.getX(k), BlockPos.getY(k), BlockPos.getZ(k)) + " "
				+ name(w.placed.get(k)) + " (" + pieces.cells() + " blocks)"));
		}
		LongArrayList holes = roofHoles(g, sp, spec);
		if (!holes.isEmpty()) {
			long k = holes.getLong(0);
			out.add(new Finding(Rule.ROOF_HOLE, holes.size(), loc.at(BlockPos.getX(k), BlockPos.getY(k), BlockPos.getZ(k))));
		}
		return out;
	}

	/** World positions written in the plot's local frame: x across the front from the left, y above the floor, z back. */
	public record Local(LocalFrame f, int floor) {
		public String at(int x, int y, int z) {
			return this.f.localX(x, z) + "/" + (y - this.floor) + "/" + this.f.localZ(x, z);
		}
	}

	private static String name(BlockState s) {
		return s == null ? "?" : BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
	}

	@FunctionalInterface
	private interface CellVisitor {
		void visit(int x, int y, int z, BlockState s);
	}

	/** Every non-air block the building places in its plot or one column round it. */
	private static void forPlaced(Grid g, CellVisitor v) {
		for (int y = g.y0; y < g.y0 + g.sy; y++) {
			for (int z = g.z0; z < g.z0 + g.sz; z++) {
				for (int x = g.x0; x < g.x0 + g.sx; x++) {
					if (!g.placed(x, y, z) || !nearPlot(g, x, z)) {
						continue;
					}
					BlockState s = g.at(x, y, z);
					if (!s.isAir()) {
						v.visit(x, y, z, s);
					}
				}
			}
		}
	}

	/** In the plot or beside it. */
	public static boolean nearPlot(Grid g, int x, int z) {
		return g.inPlot(x, z) || g.inPlot(x + 1, z) || g.inPlot(x - 1, z) || g.inPlot(x, z + 1) || g.inPlot(x, z - 1);
	}

	// =========================================================================================== doors and ladders

	private static boolean step(BlockState s) {
		return s.getBlock() instanceof StairBlock && s.getValue(StairBlock.HALF) == Half.BOTTOM
			|| s.getBlock() instanceof SlabBlock && s.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
	}

	/** Someone can stand on this side of a door: level, a step down, a stair or slab up, or another door (a double door). */
	public static boolean doorSideOk(Grid g, Spaces sp, int x, int y, int z) {
		return sp.walkable(x, y, z) || sp.walkable(x, y - 1, z) && Spaces.passable(g.at(x, y, z)) || step(g.at(x, y, z)) && sp.walkable(x, y + 1, z)
			|| g.at(x, y, z).getBlock() instanceof DoorBlock;
	}

	/** A door (its lower half at x, y, z) with a wall, post, rail, furniture or drop on one of its two sides. */
	public static boolean blockedDoor(Grid g, Spaces sp, int x, int y, int z, BlockState s) {
		Direction f = s.getValue(DoorBlock.FACING);
		return !doorSideOk(g, sp, x + f.getStepX(), y, z + f.getStepZ()) || !doorSideOk(g, sp, x - f.getStepX(), y, z - f.getStepZ());
	}

	/** The top block of a ladder of two or more. */
	public static boolean ladderTop(Grid g, int x, int y, int z) {
		return !(g.at(x, y + 1, z).getBlock() instanceof LadderBlock) && g.at(x, y - 1, z).getBlock() instanceof LadderBlock;
	}

	/** The top of a ladder lets someone off: a floor beside it, at its top or a block up. */
	public static boolean ladderExit(Grid g, Spaces sp, int x, int y, int z) {
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

	// =========================================================================================== rooms

	/** A room: enclosed standing cells of one level, 4-connected; reached when the walk comes to any of them. */
	public record Room(LongArrayList cells, int y, boolean reached, boolean furnished) {
	}

	/**
	 * A standing cell of a room: in the plot, walkable and dry, under a roof with walls all round, not a doorway, not on
	 * furniture (a bench is not a floor), and not on a tie beam (a one-wide log across open space with air on both sides
	 * reads as a floor to the walk, as the tavern's lantern beams did).
	 */
	public static boolean roomCell(Grid g, Spaces sp, int x, int y, int z) {
		if (!g.inPlot(x, z) || !sp.walkable(x, y, z) || sp.wet(x, y, z) || sp.doorway(x, y, z) || !sp.roofed(x, y, z)) {
			return false;
		}
		BlockState under = g.at(x, y - 1, z);
		if (g.placed(x, y - 1, z) && !(under.getBlock() instanceof StairBlock) && furniture(under, 0)) {
			return false;
		}
		if (TIE_BEAMS && tieBeam(g, x, y - 1, z)) {
			return false;
		}
		return sp.enclosed(x, y, z);
	}

	/** Whether tie beams are told from floors ({@code -Dhearth.gate.tiebeams=false} turns it off, to compare with the October audit). */
	private static final boolean TIE_BEAMS = !"false".equals(System.getProperty("hearth.gate.tiebeams"));

	/** A log or a rail with open air under it and on both sides along one axis: a beam across a hall, not a floor. */
	public static boolean tieBeam(Grid g, int x, int y, int z) {
		// a beam is a log (or a fence rail) laid across; a one-wide loft of boards is a floor, however narrow
		Block b = g.at(x, y, z).getBlock();
		if (!(b instanceof net.minecraft.world.level.block.RotatedPillarBlock) && !(b instanceof FenceBlock)) {
			return false;
		}
		// air, not merely passable: a floor of trapdoors or carpets has its own kind on both sides
		if (!g.at(x, y - 1, z).isAir()) {
			return false;
		}
		boolean alongX = g.at(x + 1, y, z).isAir() && g.at(x - 1, y, z).isAir();
		boolean alongZ = g.at(x, y, z + 1).isAir() && g.at(x, y, z - 1).isAir();
		return alongX || alongZ;
	}

	/** Every room of a building (cells up to its height), with whether the walk reaches it and whether it is furnished. */
	public static List<Room> rooms(Grid g, Spaces sp, PlotSpec spec, Collection<Poi> pois) {
		int top = spec.floorY() + spec.maxHeight();
		LongOpenHashSet room = new LongOpenHashSet();
		for (int y = g.y0 + 1; y < g.y0 + g.sy - 1 && y <= top; y++) {
			for (int z = g.z0; z < g.z0 + g.sz; z++) {
				for (int x = g.x0; x < g.x0 + g.sx; x++) {
					if (roomCell(g, sp, x, y, z)) {
						room.add(BlockPos.asLong(x, y, z));
					}
				}
			}
		}
		LongOpenHashSet poiCells = new LongOpenHashSet();
		for (Poi p : pois) {
			if (p.type() != PoiType.DOOR) {
				poiCells.add(p.pos().asLong());
			}
		}
		List<Room> out = new ArrayList<>();
		LongOpenHashSet seen = new LongOpenHashSet();
		// iterate in a fixed order (the set's order is its hash order, which is fixed for fixed contents)
		long[] keys = room.toLongArray();
		java.util.Arrays.sort(keys);
		for (long start : keys) {
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
			for (int i = 0; i < comp.size() && !any; i++) {
				long k = comp.getLong(i);
				any = sp.reached(BlockPos.getX(k), y, BlockPos.getZ(k));
			}
			out.add(new Room(comp, y, any, furnished(g, poiCells, comp, y)));
		}
		return out;
	}

	/** A pocket of three or more cells with furniture, a POI, a door or a bed beside it: meant to be used, not a sealed void. */
	private static boolean furnished(Grid g, LongOpenHashSet poiCells, LongArrayList comp, int y) {
		if (comp.size() < 3) {
			return false;
		}
		for (int i = 0; i < comp.size(); i++) {
			long k = comp.getLong(i);
			int x = BlockPos.getX(k);
			int z = BlockPos.getZ(k);
			for (int dy = -1; dy <= 1; dy++) {
				if (poiCells.contains(BlockPos.asLong(x, y + dy, z))) {
					return true;
				}
			}
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockState fs = g.at(x + dx, y, z + dz);
					if (g.placed(x + dx, y, z + dz) && (furniture(fs, 0) && !(fs.getBlock() instanceof StairBlock) || fs.getBlock() instanceof DoorBlock
						|| fs.getBlock() instanceof BedBlock)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	static String path(BlockState s) {
		return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
	}

	/** A block a person works at or takes from (a job's block, a chest, a barrel, a cauldron). */
	public static boolean workstation(BlockState s) {
		String p = path(s);
		return switch (p) {
			case "crafting_table", "furnace", "blast_furnace", "smoker", "grindstone", "stonecutter", "loom", "cartography_table", "fletching_table",
				 "smithing_table", "brewing_stand", "composter", "lectern", "chest", "trapped_chest", "barrel" -> true;
			default -> p.endsWith("anvil") || p.endsWith("cauldron");
		};
	}

	/** Furniture in a room: what a person uses or sits at, and what dresses a room (rugs, pots, shelves, banners). */
	public static boolean furniture(BlockState s, int dy) {
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

	// =========================================================================================== floating pieces

	/** The pieces standing on nothing: how many, how many blocks, the first block of the first, and each piece (first block, size). */
	public record Pieces(int count, int cells, long first, List<long[]> each) {
	}

	/**
	 * What holds a building up: blocks with a collision shape that are not leaves, plants, carpets or snow, and not hung
	 * from above (a hanging lantern, a chain, a hanging sign, vines, roots). Structure rests on the ground or on other
	 * structure (sharing a face or an edge, as roof courses meet); leaves, plants and fixtures hang on structure. A standing
	 * lantern holds what stands on it (the cap of a stone lantern), and a chain carries what hangs from it (a chandelier, a
	 * hoist's hook); a fence held up by a hedge, a deck on a tree's crown, a trunk stood on air or a roof fragment held only
	 * by a lamp hanging from it is not held.
	 */
	public static boolean structural(BlockState s) {
		Block b = s.getBlock();
		if (s.isAir() || b instanceof LiquidBlock || b instanceof LeavesBlock || b instanceof CarpetBlock || b instanceof SnowLayerBlock
			|| b instanceof VegetationBlock || Spaces.hangs(s)) {
			return false;
		}
		return !s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
	}

	/** Pieces of a blueprint, on its terrain, that rest on neither the ground nor the rest of the building. */
	public static Pieces floatingPieces(BlueprintImpl bp, dev.voidmc.hearth.api.TerrainProbe probe) {
		return floatingPieces(new BlueprintChecks.World(bp, probe));
	}

	/** Pieces of a building that rest on neither the ground nor the rest of the building (art review 2's probe). */
	public static Pieces floatingPieces(BlueprintChecks.World w) {
		return floatingPieces(w.placed, k -> w.natural(BlockPos.getX(k), BlockPos.getY(k), BlockPos.getZ(k)));
	}

	/**
	 * The same over any set of placed blocks (keyed by {@link BlockPos#asLong}) and a test for natural ground: a whole
	 * town composed plot over plot, where a neighbour's cut can take the ground from under a piece.
	 */
	public static Pieces floatingPieces(Long2ObjectMap<BlockState> placed, java.util.function.LongPredicate natural) {
		LongOpenHashSet structure = new LongOpenHashSet();
		LongOpenHashSet hung = new LongOpenHashSet();
		for (Long2ObjectMap.Entry<BlockState> e : placed.long2ObjectEntrySet()) {
			BlockState s = e.getValue();
			if (s.isAir() || s.getBlock() instanceof LiquidBlock) {
				continue;
			}
			(structural(s) ? structure : hung).add(e.getLongKey());
		}
		LongOpenHashSet held = new LongOpenHashSet();
		LongOpenHashSet attached = new LongOpenHashSet();
		LongArrayFIFOQueue q = new LongArrayFIFOQueue();
		// structure and fixtures on the natural ground
		long[] sorted = structure.toLongArray();
		java.util.Arrays.sort(sorted);
		for (long k : sorted) {
			if (onGround(natural, k)) {
				held.add(k);
				q.enqueue(k);
			}
		}
		spread(q, held, structure);
		for (long k : hung) {
			if (onGround(natural, k)) {
				attached.add(k);
				q.enqueue(k);
			}
		}
		spread(q, attached, hung);
		while (true) {
			// what hangs on held structure (leaves on a trunk, a lamp on a beam, plants on a sill), and on what hangs on that
			for (long k : held) {
				q.enqueue(k);
			}
			spread(q, attached, hung);
			// structure carried by a chain or a hanging lamp above it, and all that rests on that
			for (long k : attached) {
				long below = BlockPos.asLong(BlockPos.getX(k), BlockPos.getY(k) - 1, BlockPos.getZ(k));
				if (Spaces.hangs(placed.get(k)) && structure.contains(below) && held.add(below)) {
					q.enqueue(below);
				}
			}
			if (q.isEmpty()) {
				break;
			}
			spread(q, held, structure);
		}
		// what is left, in pieces (structure and what hangs on it together)
		LongOpenHashSet loose = new LongOpenHashSet();
		for (long k : structure) {
			if (!held.contains(k)) {
				loose.add(k);
			}
		}
		for (long k : hung) {
			if (!attached.contains(k)) {
				loose.add(k);
			}
		}
		if (loose.isEmpty()) {
			return new Pieces(0, 0, 0, List.of());
		}
		long[] ls = loose.toLongArray();
		java.util.Arrays.sort(ls);
		LongOpenHashSet seen = new LongOpenHashSet();
		List<long[]> each = new ArrayList<>();
		for (long k : ls) {
			if (seen.add(k)) {
				int before = seen.size();
				q.enqueue(k);
				spread(q, seen, loose);
				each.add(new long[]{k, seen.size() - before + 1});
			}
		}
		return new Pieces(each.size(), loose.size(), ls[0], each);
	}

	/** A placed block sharing a face with the natural ground. */
	private static boolean onGround(java.util.function.LongPredicate natural, long k) {
		int x = BlockPos.getX(k);
		int y = BlockPos.getY(k);
		int z = BlockPos.getZ(k);
		for (Direction d : Direction.values()) {
			if (natural.test(BlockPos.asLong(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ()))) {
				return true;
			}
		}
		return false;
	}

	/** Floods from the queued cells into cells of {@code within} sharing a face or an edge, adding them to {@code into}. */
	private static void spread(LongArrayFIFOQueue q, LongOpenHashSet into, LongOpenHashSet within) {
		while (!q.isEmpty()) {
			long k = q.dequeueLong();
			int x = BlockPos.getX(k);
			int y = BlockPos.getY(k);
			int z = BlockPos.getZ(k);
			for (int[] o : BlueprintChecks.N18) {
				long n = BlockPos.asLong(x + o[0], y + o[1], z + o[2]);
				if (within.contains(n) && into.add(n)) {
					q.enqueue(n);
				}
			}
		}
	}

	// =========================================================================================== roof holes

	/**
	 * Gaps in roofs: open-sky air in the plot at one level, at most six cells, closed on every side by roof or by covered
	 * space, and opening onto covered space on two sides or more (so a chimney flue, closed by its own masonry, is not
	 * one). Each column counts once (its highest gap). Left out: a column over a fire (a smoke hole) and a column down to
	 * a ladder, a stair, a trapdoor or scaffolding (a hatch onto a roof terrace). Walkable or not: a gap over a wall head
	 * or an attic void lets the rain in as surely as one over a floor.
	 *
	 * <p>A roof is built and spans: what closes a gap is blocks the blueprint placed (never the hillside the plot is cut
	 * into), not rails (fences, walls, bars, panes: a scaffold's or a balcony's railing is no roof), and a gap is one only
	 * where open air lies under it (air over a floor is a deck, a yard or a platform: the gallows' drop, a light well's
	 * foot). Covered space beside it counts, and so does a closing block with air under it (a roof course over a room).
	 */
	public static LongArrayList roofHoles(Grid g, Spaces sp, PlotSpec spec) {
		int floor = spec.floorY();
		LongOpenHashSet cols = new LongOpenHashSet();
		LongArrayList out = new LongArrayList();
		LongOpenHashSet seen = new LongOpenHashSet();
		for (int y = g.y0 + g.sy - 2; y > floor; y--) {
			for (int z = g.z0; z < g.z0 + g.sz; z++) {
				for (int x = g.x0; x < g.x0 + g.sx; x++) {
					long start = BlockPos.asLong(x, y, z);
					if (seen.contains(start) || !open(g, sp, x, y, z)) {
						continue;
					}
					// the open cells of this level joined to this one, up to seven
					LongArrayList comp = new LongArrayList();
					LongArrayFIFOQueue q = new LongArrayFIFOQueue();
					seen.add(start);
					q.enqueue(start);
					boolean closed = true;
					boolean overAir = false;
					int covered = 0;
					while (!q.isEmpty()) {
						long k = q.dequeueLong();
						comp.add(k);
						int cx = BlockPos.getX(k);
						int cz = BlockPos.getZ(k);
						BlockState under = g.at(cx, y - 1, cz);
						overAir |= Spaces.passable(under) && !(under.getBlock() instanceof LiquidBlock);
						for (Direction d : Direction.Plane.HORIZONTAL) {
							int nx = cx + d.getStepX();
							int nz = cz + d.getStepZ();
							long n = BlockPos.asLong(nx, y, nz);
							if (open(g, sp, nx, y, nz)) {
								if (seen.add(n)) {
									q.enqueue(n);
								}
								continue;
							}
							if (!g.inPlot(nx, nz) || !g.contains(nx, y, nz)) {
								closed = false;
								continue;
							}
							BlockState ns = g.at(nx, y, nz);
							boolean roofedAir = Spaces.passable(ns) && sp.roofY(nx, y - 1, nz) != Integer.MAX_VALUE;
							if (roofedAir) {
								covered++;
							} else if (!Spaces.roofLike(ns) || !g.placed(nx, y, nz) || rail(ns)) {
								closed = false;
							} else if (Spaces.passable(g.at(nx, y - 1, nz))) {
								// a roof course spanning a space
								covered++;
							}
						}
					}
					// a gap in a roof opens onto covered space on two sides or more (a gap beside one beam end is not one)
					if (!closed || !overAir || covered < 2 || comp.size() > 6) {
						continue;
					}
					for (int i = 0; i < comp.size(); i++) {
						long k = comp.getLong(i);
						int cx = BlockPos.getX(k);
						int cz = BlockPos.getZ(k);
						if (!smokeHole(g, cx, y, cz, floor) && !hatch(g, cx, y, cz, floor) && cols.add(BlockPos.asLong(cx, 0, cz))) {
							out.add(k);
						}
					}
				}
			}
		}
		return out;
	}

	/** Air (or a plant) in the plot with no roof anywhere above it. */
	private static boolean open(Grid g, Spaces sp, int x, int y, int z) {
		if (!g.contains(x, y, z) || !g.inPlot(x, z)) {
			return false;
		}
		BlockState s = g.at(x, y, z);
		return Spaces.passable(s) && !(s.getBlock() instanceof LiquidBlock) && !(s.getBlock() instanceof DoorBlock) && !Spaces.roofLike(s)
			&& sp.roofY(x, y - 1, z) == Integer.MAX_VALUE;
	}

	/** A railing, not a roof: fences, walls, bars and panes. */
	private static boolean rail(BlockState s) {
		Block b = s.getBlock();
		return b instanceof FenceBlock || b instanceof net.minecraft.world.level.block.WallBlock || b instanceof IronBarsBlock;
	}

	/** A ladder, a stair, a trapdoor or scaffolding below in the open column: the gap is a hatch onto a roof or a deck. */
	private static boolean hatch(Grid g, int x, int y, int z, int floor) {
		for (int yy = y; yy >= Math.max(g.y0, floor - 2); yy--) {
			BlockState s = g.at(x, yy, z);
			Block b = s.getBlock();
			if (b instanceof LadderBlock || b instanceof StairBlock || b instanceof TrapDoorBlock || b instanceof net.minecraft.world.level.block.ScaffoldingBlock) {
				return true;
			}
			if (!Spaces.passable(s)) {
				return false;
			}
		}
		return false;
	}

	/** A fire or a campfire below in the column: the gap is its smoke hole. */
	private static boolean smokeHole(Grid g, int x, int y, int z, int floor) {
		for (int yy = y - 1; yy >= Math.max(g.y0, floor - 2); yy--) {
			Block b = g.at(x, yy, z).getBlock();
			if (b instanceof CampfireBlock || b instanceof net.minecraft.world.level.block.BaseFireBlock) {
				return true;
			}
		}
		return false;
	}

	/** A rule's findings as one line each (for tools that print them). */
	public static String lines(List<Finding> fs) {
		StringBuilder sb = new StringBuilder();
		for (Finding f : fs) {
			sb.append(String.format(Locale.ROOT, "%s%n", f.text()));
		}
		return sb.toString();
	}
}
