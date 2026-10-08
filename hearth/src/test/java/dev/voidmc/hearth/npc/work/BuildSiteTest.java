package dev.voidmc.hearth.npc.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.Silhouette;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.api.StepSink;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.BuildModule;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The building-site rules (docs/FOLK.md §6): which steps a crew may place ahead of the plot's counter, how the counter
 * then moves, and how many builders a site takes.
 */
class BuildSiteTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	// ---------------------------------------------------------------- the step plan

	/** A blueprint of one stage from a list of (pos, state). */
	private record ListBlueprint(List<BlockPos> pos, List<BlockState> states) implements Blueprint {
		@Override
		public PlotSpec spec() {
			return new PlotSpec(Hearth.id("house/test"), Hearth.id("temperate"), 1, BlockPos.ZERO, 8, 8, Direction.NORTH, 8, 1, new CompoundTag());
		}

		@Override
		public BoundingBox bounds() {
			return new BoundingBox(0, 0, 0, 8, 8, 8);
		}

		@Override
		public int stageCount() {
			return 1;
		}

		@Override
		public StageKind stageKind(int stage) {
			return StageKind.WALLS;
		}

		@Override
		public int stepCount(int stage) {
			return this.pos.size();
		}

		@Override
		public void step(int stage, int index, StepSink sink) {
			BlockPos p = this.pos.get(index);
			sink.accept(p.getX(), p.getY(), p.getZ(), this.states.get(index), null);
		}

		@Override
		public void forEachInChunk(int chunkX, int chunkZ, int fromStage, int toStage, StepSink sink) {
		}

		@Override
		public int work(int stage) {
			return this.pos.size();
		}

		@Override
		public int[] goods(int stage) {
			return new int[Goods.VALUES.length];
		}

		@Override
		public List<Poi> pois() {
			return List.of();
		}

		@Override
		public List<BlockPos> entrances() {
			return List.of();
		}

		@Override
		public int beds() {
			return 0;
		}

		@Override
		public Silhouette silhouette() {
			return null;
		}
	}

	private static StepPlan.Scan scan(Blueprint bp, int stage, int from, BitSet placed, Map<Long, BlockState> world) {
		return StepPlan.scan(bp, stage, from, placed, pos -> world.getOrDefault(pos, Blocks.AIR.defaultBlockState()), 4096, 4096);
	}

	private static boolean ready(StepPlan.Scan scan, int index) {
		for (StepPlan.Pending p : scan.pending()) {
			if (p.index() == index) {
				return p.ready();
			}
		}
		throw new AssertionError("step " + index + " not pending");
	}

	@Test
	void stepsAheadOfTheCounterWaitForTheirSupports() {
		BlockState stone = Blocks.STONE.defaultBlockState();
		BlockState torch = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST);
		ListBlueprint bp = new ListBlueprint(
			List.of(new BlockPos(0, 0, 0), new BlockPos(0, 1, 0), new BlockPos(1, 1, 0), new BlockPos(6, 0, 6), new BlockPos(6, 0, 6), new BlockPos(3, 0, 0)),
			List.of(stone, stone, torch, stone, Blocks.OAK_PLANKS.defaultBlockState(), Blocks.OAK_STAIRS.defaultBlockState()));
		Map<Long, BlockState> world = new HashMap<>();
		BitSet placed = new BitSet();
		StepPlan.Scan s = scan(bp, 0, 0, placed, world);
		assertEquals(0, s.donePrefix());
		assertEquals(6, s.pending().size());
		assertTrue(ready(s, 0), "the cursor's own step is always ready");
		assertFalse(ready(s, 1), "a block waits for the one under it");
		assertFalse(ready(s, 2), "a torch waits for the wall beside it");
		assertTrue(ready(s, 3), "a block elsewhere, resting on nothing still to do, may go in ahead");
		assertFalse(ready(s, 4), "a later step at the same place waits for the earlier one");
		assertTrue(ready(s, 5), "a stair standing on its own may go in ahead");

		// the far block and the stair placed ahead of the counter: the counter does not move over them yet
		world.put(new BlockPos(6, 0, 6).asLong(), stone);
		placed.set(3);
		world.put(new BlockPos(3, 0, 0).asLong(), Blocks.OAK_STAIRS.defaultBlockState());
		placed.set(5);
		s = scan(bp, 0, 0, placed, world);
		assertEquals(0, s.donePrefix());
		assertTrue(ready(s, 4), "with the earlier step there, the one replacing it may go in");

		// the cursor's step: now the counter runs over it, and the block on it may go in
		world.put(new BlockPos(0, 0, 0).asLong(), stone);
		placed.set(0);
		s = scan(bp, 0, 0, placed, world);
		assertEquals(1, s.donePrefix());
		assertTrue(ready(s, 1));
		assertFalse(ready(s, 2));
		world.put(new BlockPos(0, 1, 0).asLong(), stone);
		placed.set(1);
		s = scan(bp, 0, 1, placed, world);
		assertEquals(1, s.donePrefix(), "steps 1 done; 2 (the torch) still to do");
		assertTrue(ready(s, 2));
	}

	@Test
	void aStepAlreadyInTheWorldOrUnderSomebodysChestIsDone() {
		BlockState stone = Blocks.STONE.defaultBlockState();
		ListBlueprint bp = new ListBlueprint(List.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), new BlockPos(2, 0, 0)),
			List.of(stone, Blocks.AIR.defaultBlockState(), stone));
		Map<Long, BlockState> world = new HashMap<>();
		world.put(new BlockPos(0, 0, 0).asLong(), stone);
		world.put(new BlockPos(2, 0, 0).asLong(), Blocks.CHEST.defaultBlockState());
		StepPlan.Scan s = scan(bp, 0, 0, new BitSet(), world);
		assertEquals(3, s.donePrefix(), "in place, air over air, a chest never built over");
		assertTrue(s.pending().isEmpty());
		assertTrue(s.complete());
	}

	/**
	 * A real cottage raised by a crew that places every ready step it reaches, round by round: the house comes out exactly
	 * as one builder placing the steps one by one would leave it, no block ever goes in before an earlier step under it,
	 * at its place or (for a hanging block) beside it, the counter reaches the end of every stage only over done steps,
	 * and the crew needs far fewer rounds than there are steps.
	 */
	@Test
	void aCrewPlacingAheadLeavesTheHouseAStrictBuilderWould() {
		var service = BuildModule.standalone();
		SyntheticTerrain flat = new SyntheticTerrain(SyntheticTerrain.Kind.FLAT, 3);
		int floor = flat.groundY(0, 0);
		PlotSpec spec = new PlotSpec(Hearth.id("house/cottage"), Hearth.id("temperate"), 7, new BlockPos(0, floor, 0), 11, 9, Direction.NORTH, 12, 1,
			new CompoundTag());
		Blueprint bp = service.blueprint(spec, flat);
		// the strict builder's result
		Map<Long, BlockState> strict = new HashMap<>();
		int total = 0;
		for (int st = 0; st < bp.stageCount(); st++) {
			for (int i = 0; i < bp.stepCount(st); i++) {
				bp.step(st, i, (x, y, z, state, tag) -> strict.put(BlockPos.asLong(x, y, z), state));
			}
			total += bp.stepCount(st);
		}
		// the crew
		Map<Long, BlockState> world = new HashMap<>();
		int rounds = 0;
		int placedAhead = 0;
		int advanced = 0;
		for (int st = 0; st < bp.stageCount(); st++) {
			// every step's position, and where each position is touched in this stage, to check supports
			int n = bp.stepCount(st);
			long[] pos = new long[n];
			BlockState[] want = new BlockState[n];
			Long2ObjectOpenHashMap<List<Integer>> at = new Long2ObjectOpenHashMap<>();
			for (int i = 0; i < n; i++) {
				int k = i;
				bp.step(st, i, (x, y, z, state, tag) -> {
					pos[k] = BlockPos.asLong(x, y, z);
					want[k] = state;
				});
				at.computeIfAbsent(pos[i], q -> new ArrayList<>()).add(i);
			}
			BitSet placed = new BitSet();
			int cursor = 0;
			for (int guard = 0; cursor < n; guard++) {
				assertTrue(guard < n + 10, "the crew stalls in stage " + st);
				StepPlan.Scan s = scan(bp, st, cursor, placed, world);
				cursor += s.donePrefix();
				advanced += s.donePrefix();
				if (cursor >= n) {
					break;
				}
				rounds++;
				List<StepPlan.Pending> now = new ArrayList<>();
				for (StepPlan.Pending p : s.pending()) {
					if (p.ready()) {
						now.add(p);
					}
				}
				assertFalse(now.isEmpty(), "something is always ready (the cursor's step)");
				for (StepPlan.Pending p : now) {
					// supports: no earlier step still to do at its place, under it, or (hanging) beside it
					for (long q : supportsOf(p.pos(), StepPlan.hangs(p.state()))) {
						for (int i : at.getOrDefault(q, List.of())) {
							if (i < p.index() && !StepPlan.done(world.getOrDefault(pos[i], Blocks.AIR.defaultBlockState()), want[i], placed.get(i))) {
								throw new AssertionError("stage " + st + " step " + p.index() + " " + p.state() + " placed before step " + i + " " + want[i]);
							}
						}
					}
					world.put(p.pos(), p.state());
					placed.set(p.index());
					if (p.index() > cursor) {
						placedAhead++;
					}
				}
			}
		}
		for (Map.Entry<Long, BlockState> e : strict.entrySet()) {
			BlockState got = world.getOrDefault(e.getKey(), Blocks.AIR.defaultBlockState());
			assertTrue(StepPlan.sameAs(got, e.getValue()), "at " + BlockPos.of(e.getKey()) + ": " + got + " but a strict builder leaves " + e.getValue());
		}
		assertEquals(total, advanced, "the counter ran over every step exactly once");
		assertTrue(placedAhead > total / 4, "much is placed ahead of the counter: " + placedAhead + " of " + total);
		assertTrue(rounds < total / 4, "a crew placing all it may each round needs far fewer rounds than steps: " + rounds + " for " + total);
	}

	private static long[] supportsOf(long p, boolean hangs) {
		if (!hangs) {
			return new long[]{p, BlockPos.offset(p, Direction.DOWN)};
		}
		long[] out = new long[7];
		out[0] = p;
		int k = 1;
		for (Direction d : Direction.values()) {
			out[k++] = BlockPos.offset(p, d);
		}
		return out;
	}

	/**
	 * One builder standing still with a player's reach places, in one stint, every step it may within reach; the steps
	 * beyond reach are left for when they move along.
	 */
	@Test
	void aBuilderPlacesEveryReadyStepWithinReachBeforeMovingOn() {
		BlockState stone = Blocks.STONE.defaultBlockState();
		List<BlockPos> wall = new ArrayList<>();
		List<BlockState> states = new ArrayList<>();
		// a wall 12 long and 4 high, course by course
		for (int y = 0; y < 4; y++) {
			for (int x = 0; x < 12; x++) {
				wall.add(new BlockPos(x, y, 0));
				states.add(stone);
			}
		}
		ListBlueprint bp = new ListBlueprint(wall, states);
		Map<Long, BlockState> world = new HashMap<>();
		BitSet placed = new BitSet();
		Vec3 eye = new Vec3(2.5, 1.62, 2.5);
		int cursor = 0;
		int stint = 0;
		for (int round = 0; round < 10; round++) {
			StepPlan.Scan s = scan(bp, 0, cursor, placed, world);
			cursor += s.donePrefix();
			int n = 0;
			for (StepPlan.Pending p : s.pending()) {
				if (p.ready() && Vec3.atCenterOf(p.blockPos()).distanceToSqr(eye) <= BuilderWork.REACH * BuilderWork.REACH) {
					world.put(p.pos(), p.state());
					placed.set(p.index());
					n++;
				}
			}
			if (n == 0) {
				break;
			}
			stint += n;
		}
		// within reach, and so is everything under it (a block whose support is out of reach waits for the next spot)
		int inReach = 0;
		for (BlockPos p : wall) {
			boolean ok = true;
			for (int y = 0; y <= p.getY(); y++) {
				ok &= Vec3.atCenterOf(new BlockPos(p.getX(), y, p.getZ())).distanceToSqr(eye) <= BuilderWork.REACH * BuilderWork.REACH;
			}
			if (ok) {
				inReach++;
			}
		}
		assertEquals(inReach, stint, "every step within reach (with its supports) is placed from one spot");
		assertTrue(inReach >= 16, "a stretch of several courses is in reach: " + inReach);
		assertTrue(cursor > 0 && cursor < wall.size(), "the counter runs over the done start of the first course: " + cursor);
	}

	// ---------------------------------------------------------------- crews

	private static PlotSpec spec(String archetype, int sx, int sz) {
		return new PlotSpec(Hearth.id(archetype), Hearth.id("temperate"), 1, new BlockPos(0, 64, 0), sx, sz, Direction.NORTH, 12, 1, new CompoundTag());
	}

	@Test
	void aSiteTakesACrewToItsSize() {
		assertEquals(2, BuilderWork.crewCap(spec("house/cottage", 7, 7)));
		assertEquals(3, BuilderWork.crewCap(spec("house/cottage", 11, 9)));
		assertEquals(5, BuilderWork.crewCap(spec("civic/hall", 20, 14)));
		assertEquals(10, BuilderWork.crewCap(spec("noble/castle", 48, 48)));
		assertEquals(1, BuilderWork.crewCap(spec("market/stall", 3, 3)));
	}

	/**
	 * The tour's case: twenty-one builders all told to the same 7×7 cottage. Two keep it; the rest go to the hall (five)
	 * and the remaining fourteen find no room (they carry materials instead); nobody stands in a crew over its cap.
	 */
	@Test
	void twentyOneBuildersOnACottageSpreadToTheCaps() {
		SimpleSettlement s = new SimpleSettlement(1, "Crewton", Culture.TEMPERATE, BlockPos.ZERO, 0);
		var cottage = s.addPlot(spec("house/cottage", 7, 7));
		var hall = s.addPlot(spec("civic/hall", 20, 14));
		var done = s.addPlot(spec("house/cottage", 7, 7));
		cottage.state = PlotState.BUILDING;
		hall.state = PlotState.BUILDING;
		done.state = PlotState.BUILT;
		Map<UUID, Integer> claims = new HashMap<>();
		List<UUID> crew = new ArrayList<>();
		for (int i = 0; i < 21; i++) {
			UUID id = new UUID(0x1234, i * 7919L % 101);
			crew.add(id);
			claims.put(id, cottage.id());
		}
		// each builder thinks in turn, a few times over
		int carrying = 0;
		for (int pass = 0; pass < 3; pass++) {
			carrying = 0;
			for (UUID id : crew) {
				PlotView p = BuilderWork.pick(s, claims, id, BlockPos.ZERO);
				if (p == null) {
					claims.remove(id);
					carrying++;
				} else {
					claims.put(id, p.id());
				}
			}
		}
		assertEquals(2, count(claims, cottage.id()), "the cottage keeps two");
		assertEquals(5, count(claims, hall.id()), "the hall takes five");
		assertEquals(0, count(claims, done.id()));
		assertEquals(14, carrying, "the rest carry");
		// those kept on the cottage are its first two by id
		List<UUID> on = new ArrayList<>();
		claims.forEach((k, v) -> {
			if (v == cottage.id()) {
				on.add(k);
			}
		});
		List<UUID> sorted = new ArrayList<>(crew);
		sorted.sort(null);
		assertTrue(on.containsAll(sorted.subList(0, 2)), "the first two by id stay: " + on);
		// a builder left without a site carries for the nearest one
		assertNotNull(BuilderWork.nearestSite(s, BlockPos.ZERO));
		hall.state = PlotState.BUILT;
		cottage.waitingForGoods = true;
		assertNull(BuilderWork.nearestSite(s, BlockPos.ZERO), "nothing buildable: nothing to carry for");
	}

	private static int count(Map<UUID, Integer> claims, int plot) {
		int n = 0;
		for (int p : claims.values()) {
			if (p == plot) {
				n++;
			}
		}
		return n;
	}

	// ---------------------------------------------------------------- the ring

	@Test
	void highWorkAlongOneWallRaisesTheRingAlongThatWallOnly() {
		// a 9 x 11 cottage on ground at y 64: only its north gable is still to do, high up
		Staging st = Staging.flat(0, 8, 0, 10, 65, 80, 0);
		assertFalse(st.raising(), "nothing asked yet");
		for (int x = 2; x <= 6; x++) {
			st.need(x, 0);
		}
		assertTrue(st.require(72));
		int north = 0;
		int south = 0;
		for (int c = 0; c < st.n; c++) {
			if (st.isShort(c)) {
				if (st.zs[c] <= 1) {
					north++;
				}
				if (st.zs[c] >= 9) {
					south++;
				}
			}
		}
		assertTrue(north >= 9, "the whole north side goes up, " + north);
		assertEquals(0, south, "nothing along the south wall");
		assertFalse(st.allNeeded());
		// walls going up all round: the whole ring
		for (int x = 0; x <= 8; x++) {
			st.need(x, 0);
			st.need(x, 10);
		}
		for (int z = 0; z <= 10; z++) {
			st.need(0, z);
			st.need(8, z);
		}
		assertTrue(st.allNeeded());
		for (int c = 0; c < st.n; c++) {
			assertTrue(st.isShort(c), "column " + c + " wanted");
		}
	}

	@Test
	void aShortGapBetweenWantedColumnsIsBridged() {
		Staging st = Staging.flat(0, 20, 0, 4, 65, 80, 0);
		// two stretches of the north wall, eleven blocks apart: two columns between them left out
		st.need(2, 0);
		st.need(13, 0);
		int x7 = st.cellAt(7, -1);
		int x8 = st.cellAt(8, -1);
		assertTrue(st.needed[x7] && st.needed[x8], "the gap between the stretches is walked over");
		Staging far = Staging.flat(0, 30, 0, 4, 65, 80, 0);
		far.need(2, 0);
		far.need(25, 0);
		assertFalse(far.needed[far.cellAt(14, -1)], "a long gap stays a gap");
	}

	@Test
	void theSitesLedgerIdIsItsOwn() {
		assertEquals(BuildSite.ledgerId(5), new BuildSite(5).ledgerId);
		assertFalse(BuildSite.ledgerId(5).equals(BuildSite.ledgerId(6)));
	}
}
