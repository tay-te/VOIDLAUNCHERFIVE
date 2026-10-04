package dev.voidmc.hearth.build.landscape;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.check.BlueprintChecks;
import dev.voidmc.hearth.build.style.StyleRegistry;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

/**
 * The landscape and defence family beyond the shared invariants: a plaza leaves what the planner stood on the square
 * alone and puts its notice board and benches round it; wall stretches cut from one line meet without a gap and leave
 * towers and gatehouses on the line to themselves, a tower's doorways meeting the walk; a gatehouse's passage is clear
 * for a cart; and the places folk gather (seats, meeting points, notice boards, guard posts, viewpoints) can be walked
 * to from the way in, in every culture.
 */
class LandscapeTest {
	static final BlueprintServiceImpl SERVICE;

	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		SERVICE = BuildModule.standalone();
	}

	static Identifier style(Culture c) {
		return StyleRegistry.styleId(c);
	}

	// ------------------------------------------------------------------------------------------------ the plaza

	@Test
	void plazaLeavesWhatStandsOnTheSquareAlone() {
		SyntheticTerrain flat = SyntheticTerrain.named("flat", 1);
		for (Culture c : Culture.values()) {
			// a 17 × 17 square at the origin, the well kept in its middle (world rectangle -2..2)
			CompoundTag p = new CompoundTag();
			p.putBoolean("heart", true);
			p.putIntArray("keep", new int[]{-2, -2, 2, 2});
			PlotSpec spec = new PlotSpec(Hearth.id("landscape/plaza"), style(c), 77L + c.ordinal(), new BlockPos(-8, 64, -8), 17, 17, Direction.SOUTH, 8, 2, p);
			BlueprintImpl bp = SERVICE.compiled(spec, flat);
			for (int s = 0; s < bp.stageCount(); s++) {
				for (int i = 0; i < bp.stepCount(s); i++) {
					BlockPos q = bp.pos(s, i);
					assertFalse(q.getX() >= -2 && q.getX() <= 2 && q.getZ() >= -2 && q.getZ() <= 2,
						c + ": the plaza builds " + bp.state(s, i) + " at " + q + " inside the kept well");
				}
			}
			assertTrue(bp.pois().stream().anyMatch(x -> x.type() == PoiType.NOTICE_BOARD), c + ": no notice board");
			assertTrue(bp.pois().stream().anyMatch(x -> x.type() == PoiType.MEETING_POINT), c + ": no meeting point");
			assertTrue(BlueprintChecks.all(bp, flat).isEmpty(), c + ": " + BlueprintChecks.all(bp, flat));
		}
	}

	@Test
	void junctionPlazaHasACentrepieceAndSeats() {
		SyntheticTerrain hills = SyntheticTerrain.named("hills", 3);
		for (Culture c : Culture.values()) {
			CompoundTag p = new CompoundTag();
			p.putBoolean("junction", true);
			PlotSpec spec = new PlotSpec(Hearth.id("landscape/plaza"), style(c), 5L + c.ordinal(), new BlockPos(-4, 64, -4), 9, 9, Direction.EAST, 8, 3, p);
			BlueprintImpl bp = SERVICE.compiled(spec, hills);
			assertTrue(bp.pois().stream().filter(x -> x.type() == PoiType.SEAT).count() >= 2, c + ": fewer than two seats");
		}
	}

	// ------------------------------------------------------------------------------------------------ walls

	/** A wall or palisade spec to the infrastructure contract (PLANNING.md §7, as the planner's Specs.wall makes it). */
	static PlotSpec wallSpec(String id, int[] pts, int width, int height, Culture c, CompoundTag extra) {
		int x0 = Integer.MAX_VALUE;
		int z0 = Integer.MAX_VALUE;
		int x1 = Integer.MIN_VALUE;
		int z1 = Integer.MIN_VALUE;
		int y0 = Integer.MAX_VALUE;
		int y1 = Integer.MIN_VALUE;
		double reach = (width + 0.5) / 2;
		for (int v = 0; v < pts.length / 3; v++) {
			y0 = Math.min(y0, pts[v * 3 + 1]);
			y1 = Math.max(y1, pts[v * 3 + 1]);
		}
		// the corridor's bounding box
		for (int x = minX(pts) - width; x <= maxX(pts) + width; x++) {
			for (int z = minZ(pts) - width; z <= maxZ(pts) + width; z++) {
				if (dist(pts, x, z) < reach) {
					x0 = Math.min(x0, x);
					z0 = Math.min(z0, z);
					x1 = Math.max(x1, x);
					z1 = Math.max(z1, z);
				}
			}
		}
		int n = pts.length / 3;
		int dx = pts[(n - 1) * 3] - pts[0];
		int dz = pts[(n - 1) * 3 + 2] - pts[2];
		Direction facing = Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? Direction.EAST : Direction.WEST) : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
		CompoundTag p = extra.copy();
		p.putIntArray("points", pts);
		p.putInt("width", width);
		p.putInt("height", height);
		return new PlotSpec(Hearth.id(id), style(c), 4242L + pts[0], new BlockPos(x0, y0, z0), x1 - x0 + 1, z1 - z0 + 1, facing, y1 - y0 + height + 3, 3, p);
	}

	static int minX(int[] p) {
		int m = Integer.MAX_VALUE;
		for (int i = 0; i < p.length; i += 3) {
			m = Math.min(m, p[i]);
		}
		return m;
	}

	static int maxX(int[] p) {
		int m = Integer.MIN_VALUE;
		for (int i = 0; i < p.length; i += 3) {
			m = Math.max(m, p[i]);
		}
		return m;
	}

	static int minZ(int[] p) {
		int m = Integer.MAX_VALUE;
		for (int i = 2; i < p.length; i += 3) {
			m = Math.min(m, p[i]);
		}
		return m;
	}

	static int maxZ(int[] p) {
		int m = Integer.MIN_VALUE;
		for (int i = 2; i < p.length; i += 3) {
			m = Math.max(m, p[i]);
		}
		return m;
	}

	static double dist(int[] p, int x, int z) {
		double best = Double.MAX_VALUE;
		for (int v = 0; v + 1 < p.length / 3; v++) {
			double ax = p[v * 3];
			double az = p[v * 3 + 2];
			double bx = p[v * 3 + 3];
			double bz = p[v * 3 + 5];
			double l2 = (bx - ax) * (bx - ax) + (bz - az) * (bz - az);
			double t = l2 == 0 ? 0 : Math.clamp(((x - ax) * (bx - ax) + (z - az) * (bz - az)) / l2, 0, 1);
			best = Math.min(best, Math.hypot(x - (ax + (bx - ax) * t), z - (az + (bz - az) * t)));
		}
		return best;
	}

	/** Dense centreline columns (the polyline rule). */
	static List<int[]> dense(int[] p) {
		List<int[]> out = new ArrayList<>();
		out.add(new int[]{p[0], p[1], p[2]});
		for (int v = 0; v + 1 < p.length / 3; v++) {
			int dx = p[v * 3 + 3] - p[v * 3];
			int dy = p[v * 3 + 4] - p[v * 3 + 1];
			int dz = p[v * 3 + 5] - p[v * 3 + 2];
			int n = Math.max(Math.abs(dx), Math.abs(dz));
			for (int i = 1; i <= n; i++) {
				out.add(new int[]{p[v * 3] + (int) Math.floor(dx * (double) i / n + 0.5), p[v * 3 + 1] + (int) Math.floor(dy * (double) i / n + 0.5),
					p[v * 3 + 2] + (int) Math.floor(dz * (double) i / n + 0.5)});
			}
		}
		return out;
	}

	/** Points of a dense run [a, b] at most eight apart, as the planner's Polyline.fromDense gives them. */
	static int[] points(List<int[]> dense, int a, int b) {
		List<Integer> out = new ArrayList<>();
		for (int i = a; i <= b; i += 6) {
			int[] c = dense.get(i);
			out.add(c[0]);
			out.add(c[1]);
			out.add(c[2]);
		}
		if ((b - a) % 6 != 0) {
			int[] c = dense.get(b);
			out.add(c[0]);
			out.add(c[1]);
			out.add(c[2]);
		}
		return out.stream().mapToInt(Integer::intValue).toArray();
	}

	static Long2ObjectOpenHashMap<BlockState> placed(BlueprintImpl... bps) {
		Long2ObjectOpenHashMap<BlockState> m = new Long2ObjectOpenHashMap<>();
		for (BlueprintImpl bp : bps) {
			for (int s = 0; s < bp.stageCount(); s++) {
				for (int i = 0; i < bp.stepCount(s); i++) {
					bp.step(s, i, (x, y, z, st, tag) -> m.putIfAbsent(BlockPos.asLong(x, y, z), st));
				}
			}
		}
		return m;
	}

	@Test
	void wallStretchesFromOneLineMeetWithoutAGap() {
		SyntheticTerrain hills = SyntheticTerrain.named("hills", 9);
		// a quarter round of wall, as the planner lays its rings, split where the planner splits long stretches
		List<int[]> ring = new ArrayList<>();
		for (int i = 0; i <= 12; i++) {
			double a = i * Math.PI / 24;
			int x = (int) Math.round(Math.cos(a) * 40);
			int z = (int) Math.round(Math.sin(a) * 40);
			ring.add(new int[]{x, hills.groundY(x, z), z});
		}
		int[] coarse = ring.stream().flatMapToInt(java.util.Arrays::stream).toArray();
		List<int[]> dense = dense(coarse);
		for (int i = 0; i < dense.size(); i++) {
			dense.get(i)[1] = hills.groundY(dense.get(i)[0], dense.get(i)[2]);
		}
		int split = dense.size() / 2;
		for (Culture c : new Culture[]{Culture.TEMPERATE, Culture.DESERT, Culture.KARST, Culture.BAYOU}) {
			CompoundTag a = new CompoundTag();
			a.putBoolean("join_end", true);
			CompoundTag b = new CompoundTag();
			b.putBoolean("join_start", true);
			BlueprintImpl first = SERVICE.compiled(wallSpec("defense/wall", points(dense, 0, split - 1), 3, 8, c, a), hills);
			BlueprintImpl second = SERVICE.compiled(wallSpec("defense/wall", points(dense, split, dense.size() - 1), 3, 8, c, b), hills);
			Long2ObjectOpenHashMap<BlockState> both = placed(first, second);
			// every column of the line has wall standing on it, at the join too
			for (int i = 1; i < dense.size() - 1; i++) {
				int[] col = dense.get(i);
				boolean standing = false;
				for (int y = col[1]; y < col[1] + 12; y++) {
					BlockState st = both.get(BlockPos.asLong(col[0], y, col[2]));
					standing |= st != null && !st.isAir();
				}
				assertTrue(standing, c + ": no wall at column " + col[0] + "," + col[2] + " (step " + i + " of " + dense.size() + ", join at " + split + ")");
			}
			for (BlueprintImpl bp : new BlueprintImpl[]{first, second}) {
				List<String> problems = BlueprintChecks.all(bp, hills);
				assertTrue(problems.isEmpty(), c + ": " + problems);
			}
		}
	}

	@Test
	void wallLeavesATowerOnItToTheTowerAndTheWalkMeetsItsDoors() {
		SyntheticTerrain flat = SyntheticTerrain.named("flat", 1);
		// a straight stretch east-west through a 7 × 7 tower at its middle; the town to the north (the heart's side)
		int[] pts = {-24, 64, 0, -16, 64, 0, -8, 64, 0, 0, 64, 0, 8, 64, 0, 16, 64, 0, 24, 64, 0};
		for (Culture c : new Culture[]{Culture.TEMPERATE, Culture.MEDITERRANEAN, Culture.NORTHERN}) {
			CompoundTag p = new CompoundTag();
			p.putIntArray("keep", new int[]{-3, -3, 3, 3});
			p.putIntArray("heart", new int[]{0, -40});
			BlueprintImpl wall = SERVICE.compiled(wallSpec("defense/wall", pts, 3, 8, c, p), flat);
			for (int s = 0; s < wall.stageCount(); s++) {
				for (int i = 0; i < wall.stepCount(s); i++) {
					BlockPos q = wall.pos(s, i);
					assertFalse(Math.abs(q.getX()) <= 3 && Math.abs(q.getZ()) <= 3, c + ": the wall builds into the tower at " + q);
				}
			}
			// the tower, facing the heart (north), on the wall
			CompoundTag tp = new CompoundTag();
			tp.putBoolean("on_wall", true);
			PlotSpec tower = new PlotSpec(Hearth.id("defense/watchtower"), style(c), 99L, new BlockPos(-3, 64, -3), 7, 7, Direction.NORTH, 22, 3, tp);
			BlueprintImpl tb = SERVICE.compiled(tower, flat);
			Long2ObjectOpenHashMap<BlockState> both = placed(wall, tb);
			// walk along the wall from its west stair, through the tower, out onto the east stretch
			World w = new World(both, flat);
			LongOpenHashSet reached = w.walk(wall.entrances());
			assertTrue(near(reached, new BlockPos(-4, 70, 0)), c + ": the walk does not reach the tower's west door; reached on the walk: "
				+ reached.longStream().mapToObj(BlockPos::of).filter(q -> q.getY() >= 69 && q.getZ() == 0).map(BlockPos::toShortString).toList()
				+ " entrances " + wall.entrances());
			assertTrue(near(reached, new BlockPos(4, 70, 0)), c + ": the walk does not come out of the tower on the east");
			for (Poi poi : tb.pois()) {
				if (poi.type() == PoiType.JOB_SITE || poi.type() == PoiType.GUARD_POST) {
					assertTrue(near(reached, poi.pos()), c + ": the tower's " + poi.type() + " at " + poi.pos() + " cannot be reached from the wall");
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ the gate

	@Test
	void gatehousePassageIsClearForACart() {
		SyntheticTerrain flat = SyntheticTerrain.named("flat", 1);
		for (Culture c : Culture.values()) {
			CompoundTag p = new CompoundTag();
			p.putBoolean("over_road", true);
			p.putInt("passage_width", 5);
			PlotSpec spec = new PlotSpec(Hearth.id("defense/gatehouse"), style(c), 31L + c.ordinal(), new BlockPos(-6, 64, -3), 13, 7, Direction.SOUTH, 22, 3, p);
			BlueprintImpl bp = SERVICE.compiled(spec, flat);
			Long2ObjectOpenHashMap<BlockState> m = placed(bp);
			// the passage: world x -2..2 (local 4..8 facing south), the whole depth, four clear blocks over the road
			for (int z = -3; z <= 3; z++) {
				for (int x = -1; x <= 1; x++) {
					for (int y = 64; y < 68; y++) {
						BlockState st = m.get(BlockPos.asLong(x, y, z));
						assertTrue(st == null || st.isAir() || st.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()
							|| st.getBlock() instanceof TrapDoorBlock, c + ": " + st + " blocks the passage at " + x + "," + y + "," + z);
					}
				}
			}
			assertTrue(bp.pois().stream().anyMatch(x -> x.type() == PoiType.GATE), c + ": no gate point");
		}
	}

	// ------------------------------------------------------------------------------------------------ reaching

	@Test
	void folkCanReachWhereTheyGather() {
		String[] ids = {"landscape/plaza", "landscape/well", "landscape/fountain", "landscape/statue", "landscape/bell", "landscape/lamp_post",
			"landscape/garden", "landscape/park", "defense/watchtower", "defense/gatehouse"};
		List<String> problems = new ArrayList<>();
		for (String terrain : new String[]{"flat", "slope", "hills"}) {
			SyntheticTerrain t = SyntheticTerrain.named(terrain, 11);
			for (String id : ids) {
				var info = SERVICE.archetype(Hearth.id(id));
				for (Culture c : Culture.values()) {
					int f = (info.minFrontage() + info.maxFrontage()) / 2 | 1;
					int dpt = (info.minDepth() + info.maxDepth()) / 2 | 1;
					int floor = t.groundY(0, -dpt / 2 - 1);
					PlotSpec spec = new PlotSpec(info.id(), style(c), 1234L + c.ordinal() * 7L, new BlockPos(-f / 2, floor, -dpt / 2), f, dpt, Direction.NORTH,
						Math.max(info.maxHeight(), 8), 3, new CompoundTag());
					BlueprintImpl bp = SERVICE.compiled(spec, t);
					World w = new World(placed(bp), t);
					LongOpenHashSet reached = w.walk(bp.entrances());
					for (Poi poi : bp.pois()) {
						boolean ok = switch (poi.type()) {
							case SEAT -> {
								boolean any = false;
								for (Direction d : Direction.Plane.HORIZONTAL) {
									any |= near(reached, poi.pos().relative(d));
								}
								yield any;
							}
							case MEETING_POINT, NOTICE_BOARD, GUARD_POST, VIEWPOINT, HEARTH, GATE -> near(reached, poi.pos());
							default -> true;
						};
						if (!ok) {
							problems.add(terrain + " " + id + " " + c.getSerializedName() + ": " + poi.type() + " at " + poi.pos());
						}
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " places folk cannot reach:\n  " + String.join("\n  ", problems));
	}

	static boolean near(LongOpenHashSet set, BlockPos p) {
		return set.contains(p.asLong()) || set.contains(p.above().asLong()) || set.contains(p.below().asLong());
	}

	/** Placed blocks over the natural terrain, and a person's walk over them (the shared checks' rules, in short). */
	static final class World {
		final Long2ObjectOpenHashMap<BlockState> placed;
		final TerrainProbe probe;

		final int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};

		World(Long2ObjectOpenHashMap<BlockState> placed, TerrainProbe probe) {
			this.placed = placed;
			this.probe = probe;
			for (long k : placed.keySet()) {
				this.box[0] = Math.min(this.box[0], BlockPos.getX(k) - 3);
				this.box[1] = Math.min(this.box[1], BlockPos.getY(k) - 3);
				this.box[2] = Math.min(this.box[2], BlockPos.getZ(k) - 3);
				this.box[3] = Math.max(this.box[3], BlockPos.getX(k) + 3);
				this.box[4] = Math.max(this.box[4], BlockPos.getY(k) + 3);
				this.box[5] = Math.max(this.box[5], BlockPos.getZ(k) + 3);
			}
		}

		BlockState at(int x, int y, int z) {
			BlockState s = this.placed.get(BlockPos.asLong(x, y, z));
			if (s != null) {
				return s;
			}
			if (y < this.probe.groundY(x, z)) {
				return Blocks.STONE.defaultBlockState();
			}
			int wy = this.probe.waterY(x, z);
			return wy != TerrainProbe.NO_WATER && y < wy ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
		}

		static boolean passable(BlockState s) {
			var b = s.getBlock();
			if (s.isAir() || b instanceof DoorBlock || b instanceof FenceGateBlock || b instanceof LiquidBlock) {
				return true;
			}
			if (b instanceof TrapDoorBlock) {
				return s.getValue(TrapDoorBlock.OPEN) || s.getValue(TrapDoorBlock.HALF) == net.minecraft.world.level.block.state.properties.Half.BOTTOM;
			}
			var shape = s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
			return shape.isEmpty() || !(b instanceof FenceBlock) && !(b instanceof WallBlock) && shape.max(Direction.Axis.Y) <= 0.2;
		}

		static boolean standable(BlockState s) {
			var b = s.getBlock();
			return !s.isAir() && !(b instanceof FenceBlock) && !(b instanceof WallBlock) && !(b instanceof FenceGateBlock)
				&& (b instanceof LiquidBlock || !s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty());
		}

		boolean walkable(int x, int y, int z) {
			return passable(at(x, y, z)) && passable(at(x, y + 1, z)) && standable(at(x, y - 1, z));
		}

		boolean step(BlockState s) {
			return s.getBlock() instanceof net.minecraft.world.level.block.StairBlock
				&& s.getValue(net.minecraft.world.level.block.StairBlock.HALF) == net.minecraft.world.level.block.state.properties.Half.BOTTOM
				|| s.getBlock() instanceof net.minecraft.world.level.block.SlabBlock
				&& s.getValue(net.minecraft.world.level.block.SlabBlock.TYPE) == net.minecraft.world.level.block.state.properties.SlabType.BOTTOM;
		}

		LongOpenHashSet walk(List<BlockPos> starts) {
			LongOpenHashSet seen = new LongOpenHashSet();
			LongArrayFIFOQueue q = new LongArrayFIFOQueue();
			for (BlockPos p : starts) {
				for (int dy = 1; dy >= -2; dy--) {
					if (walkable(p.getX(), p.getY() + dy, p.getZ())) {
						long k = BlockPos.asLong(p.getX(), p.getY() + dy, p.getZ());
						if (seen.add(k)) {
							q.enqueue(k);
						}
					}
				}
			}
			int guard = 0;
			while (!q.isEmpty() && guard++ < 400_000) {
				long k = q.dequeueLong();
				int x = BlockPos.getX(k);
				int y = BlockPos.getY(k);
				int z = BlockPos.getZ(k);
				for (Direction d : Direction.Plane.HORIZONTAL) {
					int nx = x + d.getStepX();
					int nz = z + d.getStepZ();
					if (nx < this.box[0] || nx > this.box[3] || nz < this.box[2] || nz > this.box[5]) {
						continue;
					}
					for (int dy = 1; dy >= -3; dy--) {
						int ny = y + dy;
						if (dy == 1 && !passable(at(x, y + 2, z))) {
							continue;
						}
						if (walkable(nx, ny, nz)) {
							long n = BlockPos.asLong(nx, ny, nz);
							if (seen.add(n)) {
								q.enqueue(n);
							}
							break;
						}
						if (dy == 0 && step(at(nx, ny, nz)) && walkable(nx, ny + 1, nz) && passable(at(x, y + 2, z))) {
							long n = BlockPos.asLong(nx, ny + 1, nz);
							if (seen.add(n)) {
								q.enqueue(n);
							}
							break;
						}
						if (!passable(at(nx, ny, nz)) || dy < 0 && !passable(at(nx, ny + 1, nz))) {
							if (dy <= 0) {
								break;
							}
						}
					}
				}
			}
			return seen;
		}
	}
}
