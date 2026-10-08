package dev.voidmc.hearth.build.kit.civic;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Long walls read in bays: every face of the finished drawing is looked at square-on from outside the plot, as someone
 * standing back in the street sees it, and wherever one material runs on in one plane over a wide field (more than
 * {@code limit} blocks with nothing to break it: no window, no course, no change of stone), the field is divided into
 * bays by pilaster strips on every fourth column: a dressed stone against rubble or plaster, a barked post against
 * stripped timber (or the reverse), a log post against boards. The strips stand in the wall's own plane, so nothing is
 * added to the walk or the roofs; doors, glass and lattices are never touched.
 */
public final class Bays {
	private Bays() {
	}

	/** The stages a wall is drawn in (what is seen of the building's body from outside). */
	private static final Set<StageKind> BODY = EnumSet.of(StageKind.FOUNDATION, StageKind.FRAME, StageKind.WALLS, StageKind.FLOORS, StageKind.ROOF,
		StageKind.OPENINGS);

	/** Divides every wide flat field of one material on the drawing's outer faces (see the class comment). */
	public static void divide(Draft d, int limit) {
		for (int round = 0; round < 3; round++) {
			boolean any = false;
			for (int side = 0; side < 4; side++) {
				any |= side(d, side, limit);
			}
			if (!any) {
				return;
			}
		}
	}

	/** One face; whether anything changed. */
	private static boolean side(Draft d, int side, int limit) {
		int w = d.frontage();
		int dp = d.depth();
		int n = side < 2 ? w : dp;
		int far = side < 2 ? dp + 1 : w;
		int[] yr = {Integer.MAX_VALUE, Integer.MIN_VALUE};
		d.forEach((x, y, z, c) -> {
			if (y >= 0) {
				yr[1] = Math.max(yr[1], y);
			}
		});
		int rows = yr[1] + 1;
		if (rows <= 0) {
			return false;
		}
		int[] depth = new int[n * rows];
		int[][] pos = new int[n * rows][];
		BlockState[] hit = new BlockState[n * rows];
		java.util.Arrays.fill(depth, -1);
		for (int r = 0; r < rows; r++) {
			for (int u = 0; u < n; u++) {
				for (int k = 0; k < far; k++) {
					int[] l = local(side, u, k, w, dp);
					Draft.Cell c = d.cell(l[0], r, l[1]);
					if (c != null && BODY.contains(c.stage()) && !c.state().isAir()) {
						depth[r * n + u] = k;
						hit[r * n + u] = c.state();
						pos[r * n + u] = new int[]{l[0], r, l[1]};
						break;
					}
				}
			}
		}
		// the wall plane: the commonest depth of full blocks; its rows: those where 40 % of the face stands on it
		Map<Integer, Integer> hist = new HashMap<>();
		for (int i = 0; i < hit.length; i++) {
			if (hit[i] != null && full(hit[i]) && !glass(hit[i])) {
				hist.merge(depth[i], 1, Integer::sum);
			}
		}
		if (hist.isEmpty()) {
			return false;
		}
		int plane = java.util.Collections.max(hist.entrySet(), Map.Entry.comparingByValue()).getKey();
		boolean[] zone = new boolean[rows];
		for (int r = 0; r < rows; r++) {
			int c = 0;
			for (int u = 0; u < n; u++) {
				BlockState s = hit[r * n + u];
				if (depth[r * n + u] == plane && s != null && !(s.getBlock() instanceof StairBlock) && !(s.getBlock() instanceof SlabBlock)) {
					c++;
				}
			}
			zone[r] = c >= 0.4 * n && c >= 2;
		}
		// the fields: four-connected cells of one block on the plane
		boolean[] seen = new boolean[n * rows];
		boolean changed = false;
		for (int r = 0; r < rows; r++) {
			for (int u = 0; u < n; u++) {
				int i = r * n + u;
				if (seen[i] || !zone[r] || depth[i] != plane || hit[i] == null || !full(hit[i]) || glass(hit[i])) {
					continue;
				}
				Block blk = hit[i].getBlock();
				List<int[]> field = new ArrayList<>();
				java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
				q.add(new int[]{u, r});
				seen[i] = true;
				while (!q.isEmpty()) {
					int[] c = q.poll();
					field.add(c);
					for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
						int nu = c[0] + o[0];
						int nr = c[1] + o[1];
						if (nu < 0 || nr < 0 || nu >= n || nr >= rows) {
							continue;
						}
						int j = nr * n + nu;
						if (!seen[j] && zone[nr] && depth[j] == plane && hit[j] != null && hit[j].getBlock() == blk) {
							seen[j] = true;
							q.add(new int[]{nu, nr});
						}
					}
				}
				if (field.size() > limit) {
					changed |= split(d, field, hit, pos, n, blk);
				}
			}
		}
		return changed;
	}

	/** The local (x, z) of column u at depth k on a face (as the structure audit reads faces). */
	private static int[] local(int side, int u, int k, int w, int dp) {
		return switch (side) {
			case 0 -> new int[]{u, k - 1};
			case 1 -> new int[]{w - 1 - u, dp - 1 - k};
			case 2 -> new int[]{k, dp - 1 - u};
			default -> new int[]{w - 1 - k, u};
		};
	}

	/** Strips on every fourth column of a field (the field's own columns, counted from its middle); whether any went in. */
	private static boolean split(Draft d, List<int[]> field, BlockState[] hit, int[][] pos, int n, Block blk) {
		int u0 = Integer.MAX_VALUE;
		int u1 = Integer.MIN_VALUE;
		Map<Integer, List<int[]>> byCol = new HashMap<>();
		for (int[] c : field) {
			u0 = Math.min(u0, c[0]);
			u1 = Math.max(u1, c[0]);
			byCol.computeIfAbsent(c[0], k -> new ArrayList<>()).add(c);
		}
		int mid = (u0 + u1) / 2;
		// what the strips touch, so the strip is of none of it
		Set<Block> around = new HashSet<>();
		around.add(blk);
		List<int[]> cells = new ArrayList<>();
		for (Map.Entry<Integer, List<int[]>> e : byCol.entrySet()) {
			int u = e.getKey();
			if (Math.floorMod(u - mid, 4) != 0) {
				continue;
			}
			for (int[] c : e.getValue()) {
				cells.add(pos[c[1] * n + c[0]]);
				for (int du : new int[]{-1, 1}) {
					int nu = c[0] + du;
					if (nu >= 0 && nu < n && hit[c[1] * n + nu] != null) {
						around.add(hit[c[1] * n + nu].getBlock());
					}
				}
			}
		}
		if (cells.isEmpty()) {
			return false;
		}
		BlockState sample = hit[field.get(0)[1] * n + field.get(0)[0]];
		boolean any = false;
		// timber against timber: the other face of the log (posts upright)
		Block other = sample.getBlock() instanceof RotatedPillarBlock ? otherFace(blk) : null;
		Paint stone = other == null ? stripStone(d, blk, around, cells.get(0)) : null;
		if (other == null && stone == null) {
			return false;
		}
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] p : cells) {
				BlockState now = d.get(p[0], p[1], p[2]);
				if (now == null || now.getBlock() != blk) {
					continue;
				}
				if (other != null) {
					d.set(p[0], p[1], p[2], other.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
				} else {
					d.set(p[0], p[1], p[2], stone);
				}
				any = true;
			}
		}
		return any;
	}

	/** A stone for strips in a field of {@code blk}: a dressed kin of the walling, else the style's dressing, accent, plinth or footing. */
	private static @Nullable Paint stripStone(Draft d, Block blk, Set<Block> around, int[] at) {
		List<Paint> cands = new ArrayList<>();
		// (the walling's own kin first, so a strip reads as dressed work in the same stone, not a stripe of another)
		for (String a : ashlars(blk)) {
			cands.add(Stone.fixed(d, StageKind.WALLS, a).full());
		}
		cands.add(Stone.dressing(d).full());
		for (Role role : new Role[]{Role.WALL_ACCENT, Role.PLINTH, Role.MASONRY, Role.FOUNDATION, Role.CHIMNEY}) {
			if (d.mat(role).has(Form.FULL)) {
				cands.add(d.mat(role).full());
			}
		}
		cands.add(Stone.footing(d).full());
		// (boards take a post of their own wood)
		String id = BuiltInRegistries.BLOCK.getKey(blk).getPath();
		if (id.endsWith("_planks")) {
			Block log = BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace(id.replace("_planks", id.startsWith("bamboo") ? "_block" : "_log")))
				.orElse(null);
			if (log != null) {
				cands.add(0, Paint.of(log.defaultBlockState()));
			}
		}
		for (Paint c : cands) {
			BlockState s = c.at(at[0], at[1], at[2]);
			if (s != null && full(s) && !glass(s) && !around.contains(s.getBlock())) {
				return c;
			}
		}
		return null;
	}

	/** Dressed stones that go with a walling block, nearest kin first. */
	private static List<String> ashlars(Block walling) {
		String id = BuiltInRegistries.BLOCK.getKey(walling).getPath();
		if (id.contains("mud")) {
			return List.of("minecraft:mud_bricks", "minecraft:packed_mud", "minecraft:cut_sandstone");
		}
		if (id.contains("red_sandstone")) {
			return List.of("minecraft:cut_red_sandstone", "minecraft:smooth_red_sandstone", "minecraft:chiseled_red_sandstone");
		}
		if (id.contains("sandstone")) {
			return List.of("minecraft:cut_sandstone", "minecraft:smooth_sandstone", "minecraft:chiseled_sandstone");
		}
		if (id.contains("terracotta")) {
			return List.of("minecraft:terracotta", "minecraft:brown_terracotta", "minecraft:white_terracotta", "minecraft:light_gray_terracotta");
		}
		if (id.contains("quartz") || id.contains("concrete") || id.contains("calcite")) {
			return List.of("minecraft:smooth_quartz", "minecraft:quartz_bricks", "minecraft:calcite", "minecraft:smooth_stone");
		}
		if (id.contains("deepslate")) {
			return List.of("minecraft:polished_deepslate", "minecraft:deepslate_bricks", "minecraft:deepslate_tiles");
		}
		return List.of("minecraft:polished_andesite", "minecraft:stone_bricks", "minecraft:smooth_stone", "minecraft:chiseled_stone_bricks");
	}

	/** The stripped log of a barked one, or the barked log of a stripped one (null where there is none). */
	static @Nullable Block otherFace(Block b) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(b);
		String path = id.getPath();
		if (!path.matches(".*_(log|stem|wood|hyphae)$")) {
			return null;
		}
		String alt = path.startsWith("stripped_") ? path.substring("stripped_".length()) : "stripped_" + path;
		return BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath(id.getNamespace(), alt)).orElse(null);
	}

	private static boolean full(BlockState s) {
		return s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
	}

	private static boolean glass(BlockState s) {
		return s.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock || BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().contains("glass");
	}
}
