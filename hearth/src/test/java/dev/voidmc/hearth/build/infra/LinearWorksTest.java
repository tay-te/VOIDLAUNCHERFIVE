package dev.voidmc.hearth.build.infra;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.check.BlueprintChecks;
import dev.voidmc.hearth.build.kit.infra.Course;
import dev.voidmc.hearth.build.style.StyleRegistry;
import dev.voidmc.hearth.plan.road.Polyline;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

/**
 * Infrastructure built to real planner geometry (specs made the way {@code plan.road.Specs} makes them): every step in
 * the corridor the planner reserved and nowhere else, nothing floating, stages in order, the walking levels the planner
 * gave kept along the centreline (a block of rise at most between steps), bridge ends on the banks' levels.
 */
class LinearWorksTest {
	static final BlueprintServiceImpl SERVICE;

	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		SERVICE = BuildModule.standalone();
	}

	/** A linear plot spec as the planner makes it: bounding box of the corridor, facing first → last, lowest level. */
	static PlotSpec linear(String archetype, Culture c, long seed, int[] points, int width, CompoundTag params, int low, int height, int tier) {
		int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		Polyline.corridor(points, width, (x, z) -> {
			box[0] = Math.min(box[0], x);
			box[1] = Math.min(box[1], z);
			box[2] = Math.max(box[2], x);
			box[3] = Math.max(box[3], z);
		});
		int n = points.length / 3;
		int dx = points[(n - 1) * 3] - points[0];
		int dz = points[(n - 1) * 3 + 2] - points[2];
		Direction facing = Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? Direction.EAST : Direction.WEST) : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
		CompoundTag p = params.copy();
		p.putIntArray("points", points);
		p.putInt("width", width);
		return new PlotSpec(Hearth.id(archetype), StyleRegistry.styleId(c), seed, new BlockPos(box[0], low, box[1]), box[2] - box[0] + 1, box[3] - box[1] + 1,
			facing, Math.max(1, height), tier, p);
	}

	static int minY(int[] pts) {
		int m = Integer.MAX_VALUE;
		for (int i = 1; i < pts.length; i += 3) {
			m = Math.min(m, pts[i]);
		}
		return m;
	}

	static int maxY(int[] pts) {
		int m = Integer.MIN_VALUE;
		for (int i = 1; i < pts.length; i += 3) {
			m = Math.max(m, pts[i]);
		}
		return m;
	}

	/** A graded route over the land: smoothed ground, at most a block a step and {@code grade} over a run. */
	static int[] route(TerrainProbe t, int[] xs, int[] zs, double grade) {
		int n = xs.length;
		double[] y = new double[n];
		for (int i = 0; i < n; i++) {
			y[i] = t.groundY(xs[i], zs[i]);
		}
		for (int pass = 0; pass < 2; pass++) {
			for (int i = 1; i < n; i++) {
				y[i] = Math.max(y[i - 1] - grade, Math.min(y[i - 1] + grade, y[i]));
			}
			for (int i = n - 2; i >= 0; i--) {
				y[i] = Math.max(y[i + 1] - grade, Math.min(y[i + 1] + grade, y[i]));
			}
		}
		int[] pts = new int[n * 3];
		for (int i = 0; i < n; i++) {
			pts[i * 3] = xs[i];
			pts[i * 3 + 1] = (int) Math.floor(y[i] + 0.5);
			pts[i * 3 + 2] = zs[i];
		}
		for (int i = 1; i < n; i++) {
			pts[i * 3 + 1] = Math.max(pts[(i - 1) * 3 + 1] - 1, Math.min(pts[(i - 1) * 3 + 1] + 1, pts[i * 3 + 1]));
		}
		return pts;
	}

	/** Every step of a blueprint stands on a column of the corridor (optionally not in the end caps). */
	static List<String> outsideCorridor(BlueprintImpl bp, int[] pts, int width, boolean noCaps) {
		Course c = new Course(pts, width);
		Set<Long> cols = new HashSet<>();
		for (Course.Col col : c.cols()) {
			if (!noCaps || !col.beyond()) {
				cols.add(Course.key(col.x(), col.z()));
			}
		}
		List<String> out = new ArrayList<>();
		for (int s = 0; s < bp.stageCount(); s++) {
			for (int i = 0; i < bp.stepCount(s); i++) {
				BlockPos p = bp.pos(s, i);
				if (!cols.contains(Course.key(p.getX(), p.getZ()))) {
					out.add(bp.state(s, i) + " at " + p);
					if (out.size() > 3) {
						return out;
					}
				}
			}
		}
		return out;
	}

	@Test
	void roadsAndStairsKeepToTheirCorridorAndLevels() {
		List<String> problems = new ArrayList<>();
		String[] kinds = {"street", "lane", "alley", "path", "highway"};
		for (String terrainName : new String[]{"hills", "slope", "steep"}) {
			SyntheticTerrain t = SyntheticTerrain.named(terrainName, 11);
			int run = 0;
			for (Culture c : Culture.values()) {
				for (int tier = 0; tier <= 3; tier += 3) {
					run++;
					// a bending route of a few runs, as the router makes them (8 directions)
					int[][] legs = {{0, 0}, {14, 3}, {24, 12}, {30, 26}};
					int ox = run * 7 - 40;
					int oz = run * 3 - 20;
					List<Integer> xs = new ArrayList<>();
					List<Integer> zs = new ArrayList<>();
					for (int v = 0; v + 1 < legs.length; v++) {
						int[][] r = Course.run(ox + legs[v][0], oz + legs[v][1], ox + legs[v + 1][0], oz + legs[v + 1][1]);
						for (int i = v == 0 ? 0 : 1; i < r[0].length; i++) {
							xs.add(r[0][i]);
							zs.add(r[1][i]);
						}
					}
					boolean stairs = terrainName.equals("steep");
					int[] pts = route(t, xs.stream().mapToInt(Integer::intValue).toArray(), zs.stream().mapToInt(Integer::intValue).toArray(), stairs ? 1.0 : 0.3);
					String kind = kinds[(run + tier) % kinds.length];
					int width = stairs ? (run % 2 == 0 ? 5 : 3) : kind.equals("street") || kind.equals("highway") ? 5 : kind.equals("path") ? 1 + 2 * (run % 2) : 3;
					CompoundTag params = new CompoundTag();
					params.putString("kind", kind);
					String archetype = stairs ? "infra/stairs" : "infra/road";
					PlotSpec spec = linear(archetype, c, 1000L + run, pts, width, params, minY(pts), maxY(pts) - minY(pts) + 5, tier);
					BlueprintImpl bp = SERVICE.compiled(spec, t);
					String tag = terrainName + " " + c.getSerializedName() + " " + archetype + " " + kind + " w" + width + " tier " + tier + ": ";
					for (String p : outsideCorridor(bp, pts, width, false)) {
						problems.add(tag + "outside the corridor " + p);
					}
					List<String> checks = new ArrayList<>();
					BlueprintChecks.bounds(bp, checks);
					BlueprintChecks.chunks(bp, checks);
					BlueprintChecks.order(bp, checks);
					for (String p : checks) {
						problems.add(tag + p);
					}
					problems.addAll(levels(bp, pts, width, tag));
					problems.addAll(bills(bp, tag));
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems.subList(0, Math.min(30, problems.size()))));
	}

	/** Every stage's bill of goods stays within what a builder's yard carts in one go (a few thousand units). */
	static List<String> bills(BlueprintImpl bp, String tag) {
		List<String> out = new ArrayList<>();
		for (int s = 0; s < bp.stageCount(); s++) {
			int sum = 0;
			for (int g : bp.goods(s)) {
				sum += g;
			}
			if (sum > 5000) {
				out.add(tag + "stage " + bp.stageKind(s) + " bills " + sum + " goods");
			}
		}
		return out;
	}

	/** Along the centreline the surface's top is at the planner's walking level (or half a block under it). */
	static List<String> levels(BlueprintImpl bp, int[] pts, int width, String tag) {
		Course c = new Course(pts, width);
		java.util.Map<Long, Double> top = new java.util.HashMap<>();
		for (int s = 0; s < bp.stageCount(); s++) {
			for (int i = 0; i < bp.stepCount(s); i++) {
				BlockPos p = bp.pos(s, i);
				BlockState st = bp.state(s, i);
				if (st.isAir() || st.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) {
					continue;
				}
				double h = p.getY() + st.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).max(Direction.Axis.Y);
				long key = Course.key(p.getX(), p.getZ());
				// the surface: the highest solid top at or below walking level
				int k = c.nearest(p.getX(), p.getZ());
				if (h <= c.dy(k) + 0.01) {
					top.merge(key, h, Math::max);
				}
			}
		}
		List<String> out = new ArrayList<>();
		for (int k = 0; k < c.steps(); k++) {
			Double h = top.get(Course.key(c.dx(k), c.dz(k)));
			if (h == null || h < c.dy(k) - 1.0 - 0.01) {
				out.add(tag + "no surface under the walking level " + c.dy(k) + " at step " + k + " (" + c.dx(k) + "," + c.dz(k) + "), top " + h);
				if (out.size() > 3) {
					break;
				}
			}
		}
		return out;
	}

	@Test
	void bridgesLandOnTheBanksInEveryMake() {
		SyntheticTerrain t = SyntheticTerrain.named("river", 42);
		List<String> problems = new ArrayList<>();
		String[] makes = {"arch", "beam", "trestle", "covered", "rope", "clapper", null};
		int run = 0;
		for (int zc : new int[]{-60, -20, 25, 70}) {
			// across the river at this z: find its banks
			int wet0 = Integer.MAX_VALUE;
			int wet1 = Integer.MIN_VALUE;
			for (int x = -60; x <= 60; x++) {
				if (t.waterY(x, zc) != TerrainProbe.NO_WATER) {
					wet0 = Math.min(wet0, x);
					wet1 = Math.max(wet1, x);
				}
			}
			int a = wet0 - 1;
			int b = wet1 + 1;
			int water = Integer.MIN_VALUE;
			for (int x = wet0; x <= wet1; x++) {
				water = Math.max(water, t.waterY(x, zc));
			}
			int bankA = t.groundY(a, zc);
			int bankB = t.groundY(b, zc);
			int deck = Math.max(water + 2, Math.max(bankA, bankB));
			int[] pts = {a, bankA, zc, a + 1, deck, zc, b - 1, deck, zc, b, bankB, zc};
			for (Culture c : Culture.values()) {
				for (String make : makes) {
					run++;
					int width = run % 3 == 0 ? 3 : 5;
					CompoundTag params = new CompoundTag();
					params.putInt("deck_y", deck);
					params.putInt("water_y", water);
					params.putString("kind", "span");
					params.putString("road_kind", "street");
					if (make != null) {
						params.putString("make", make);
					}
					int low = Math.min(minY(pts), water);
					PlotSpec spec = linear("infra/bridge", c, 77L + run, pts, width, params, low, Math.max(8, deck - low + 8), 1 + run % 3);
					BlueprintImpl bp = SERVICE.compiled(spec, t);
					String tag = "z " + zc + " " + c.getSerializedName() + " " + make + " w" + width + ": ";
					for (String p : outsideCorridor(bp, pts, width, false)) {
						problems.add(tag + "outside the corridor " + p);
					}
					List<String> checks = new ArrayList<>();
					BlueprintChecks.bounds(bp, checks);
					BlueprintChecks.chunks(bp, checks);
					BlueprintChecks.order(bp, checks);
					for (String p : checks) {
						problems.add(tag + p);
					}
					problems.addAll(bills(bp, tag));
					// both ends at the banks' levels: the approach roads walk straight on
					for (String p : levels(bp, pts, width, tag)) {
						if (p.contains("step 0 ") || p.contains("step " + (new Course(pts, width).steps() - 1) + " ")) {
							problems.add(p);
						}
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems.subList(0, Math.min(30, problems.size()))));
	}

	/** Degenerate geometry (a single column, two columns, a vertical jump) draws without throwing in every culture. */
	@Test
	void degenerateGeometryDrawsWithoutThrowing() {
		SyntheticTerrain t = SyntheticTerrain.named("river", 42);
		int[][] cases = {{0, 64, 0, 0, 64, 0}, {0, 64, 0, 1, 64, 0}, {0, 64, 0, 1, 70, 1}, {3, 64, 3, 3, 66, 9}};
		for (Culture c : Culture.values()) {
			for (int[] pts : cases) {
				for (String id0 : new String[]{"infra/road", "infra/stairs", "infra/walkway", "infra/dock", "infra/bridge:arch", "infra/bridge:beam",
					"infra/bridge:trestle", "infra/bridge:covered", "infra/bridge:rope", "infra/bridge:clapper", "infra/bridge:piers", "infra/bridge:boardwalk"}) {
					String id = id0.contains(":") ? id0.substring(0, id0.indexOf(':')) : id0;
					CompoundTag params = new CompoundTag();
					if (id0.contains(":")) {
						String make = id0.substring(id0.indexOf(':') + 1);
						if (make.equals("boardwalk")) {
							params.putString("kind", "boardwalk");
						} else {
							params.putString("make", make);
						}
					}
					params.putInt("deck_y", maxY(pts) + 2);
					params.putInt("street_y", minY(pts) - 8);
					PlotSpec spec = linear(id, c, 3L, pts, 3, params, minY(pts) - 8, 20, 2);
					var style = SERVICE.styles().getOrDefault(spec.style());
					var draft = new dev.voidmc.hearth.build.draft.Draft(spec, style, new dev.voidmc.hearth.build.terrain.Site(spec, t));
					SERVICE.registry().get(spec.archetype()).draw(draft);
					dev.voidmc.hearth.build.blueprint.Compiler.compile(draft);
				}
			}
		}
	}

	/** A long road plot (48 steps, diagonal, five wide) draws and compiles in milliseconds, like a house. */
	@Test
	void aLongRoadIsCheapToDraw() {
		SyntheticTerrain t = SyntheticTerrain.named("hills", 3);
		int[][] r = Course.run(0, 0, 34, 34);
		int[][] r2 = Course.run(34, 34, 48, 34);
		int[] xs = new int[r[0].length + r2[0].length - 1];
		int[] zs = new int[xs.length];
		System.arraycopy(r[0], 0, xs, 0, r[0].length);
		System.arraycopy(r[1], 0, zs, 0, r[1].length);
		System.arraycopy(r2[0], 1, xs, r[0].length, r2[0].length - 1);
		System.arraycopy(r2[1], 1, zs, r[0].length, r2[1].length - 1);
		int[] pts = route(t, xs, zs, 0.3);
		CompoundTag params = new CompoundTag();
		params.putString("kind", "street");
		long best = Long.MAX_VALUE;
		for (int i = 0; i < 6; i++) {
			PlotSpec spec = linear("infra/road", Culture.TEMPERATE, 500L + i, pts, 5, params, minY(pts), maxY(pts) - minY(pts) + 5, 3);
			long t0 = System.nanoTime();
			dev.voidmc.hearth.build.blueprint.Compiler.compile(SERVICE.draw(spec, t));
			best = Math.min(best, System.nanoTime() - t0);
		}
		assertTrue(best < 150_000_000L, "a 48-step street took " + best / 1e6 + " ms");
	}

	/** Walkways rest on the facades and stand on posts at their ends and every fourth step (review 2 item 5), nothing else in the street. */
	@Test
	void walkwaysRestOnTheFacadesAndTakeNoGround() {
		SyntheticTerrain t = SyntheticTerrain.named("flat", 1);
		List<String> problems = new ArrayList<>();
		int run = 0;
		for (Culture c : Culture.values()) {
			for (int gap : new int[]{3, 5, 8}) {
				run++;
				int street = 64;
				int deck = street + 8;
				int[] pts = run % 2 == 0 ? new int[]{0, deck, 0, 0, deck, gap + 1} : new int[]{0, deck, 0, gap + 1, deck, 0};
				CompoundTag params = new CompoundTag();
				params.putInt("deck_y", deck);
				params.putInt("street_y", street);
				PlotSpec spec = linear("infra/walkway", c, 5L + run, pts, 3, params, street, deck - street + 5, 3);
				BlueprintImpl bp = SERVICE.compiled(spec, t);
				String tag = c.getSerializedName() + " gap " + gap + ": ";
				for (String p : outsideCorridor(bp, pts, 3, true)) {
					problems.add(tag + "outside the corridor or in a facade " + p);
				}
				for (int s = 0; s < bp.stageCount(); s++) {
					for (int i = 0; i < bp.stepCount(s); i++) {
						// (the posts stand in the corridor's edge columns, off the line between the two doors)
						boolean edge = pts[0] == pts[3] ? bp.pos(s, i).getX() != pts[0] : bp.pos(s, i).getZ() != pts[2];
						if (bp.pos(s, i).getY() < street + 3 && !bp.state(s, i).isAir() && !edge) {
							problems.add(tag + "stands in the street: " + bp.state(s, i) + " at " + bp.pos(s, i));
							i = bp.stepCount(s);
						}
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems.subList(0, Math.min(30, problems.size()))));
	}

	@Test
	void aBuildingOnATerraceStandsOnItsDeck() {
		SyntheticTerrain t = SyntheticTerrain.named("steep", 42);
		List<String> problems = new ArrayList<>();
		for (Culture c : Culture.values()) {
			int x0 = 4 + c.ordinal() * 13;
			int z0 = -6;
			int w = 9;
			int d = 9;
			int level = t.groundY(x0 + w / 2, z0 + d / 2);
			CompoundTag tp = new CompoundTag();
			tp.putInt("level", level);
			PlotSpec terrace = new PlotSpec(Hearth.id("infra/terrace"), StyleRegistry.styleId(c), 9L, new BlockPos(x0, level, z0), w, d, Direction.SOUTH, 12, 2, tp);
			CompoundTag hp = new CompoundTag();
			hp.putBoolean("terraced", true);
			PlotSpec house = new PlotSpec(Hearth.id("house/cottage"), StyleRegistry.styleId(c), 9L, new BlockPos(x0, level, z0), w, d, Direction.SOUTH, 12, 2, hp);
			BlueprintImpl tb = SERVICE.compiled(terrace, t);
			BlueprintImpl hb = SERVICE.compiled(house, t);
			// the deck: a solid block at level - 1 under every column of the footprint
			Set<Long> deck = new HashSet<>();
			for (int s = 0; s < tb.stageCount(); s++) {
				for (int i = 0; i < tb.stepCount(s); i++) {
					BlockPos p = tb.pos(s, i);
					if (p.getY() == level - 1 && tb.state(s, i).isSolidRender()) {
						deck.add(Course.key(p.getX(), p.getZ()));
					}
				}
			}
			if (deck.size() < w * d) {
				problems.add(c.getSerializedName() + ": the terrace's deck covers " + deck.size() + " of " + w * d + " columns");
			}
			// the house trusts it: nothing of it reaches more than two below its floor inside the footprint
			for (int s = 0; s < hb.stageCount(); s++) {
				for (int i = 0; i < hb.stepCount(s); i++) {
					BlockPos p = hb.pos(s, i);
					boolean inside = p.getX() >= x0 && p.getX() < x0 + w && p.getZ() >= z0 && p.getZ() < z0 + d;
					if (inside && p.getY() < level - 2) {
						problems.add(c.getSerializedName() + ": the cottage digs or founds through the terrace at " + p + " (" + hb.state(s, i) + ")");
						i = hb.stepCount(s);
						s = hb.stageCount();
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}
}
