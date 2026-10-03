package dev.voidmc.hearth.build.archetype.work;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Lifts;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.kit.trade.Shed;
import dev.voidmc.hearth.build.kit.trade.Signs;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * {@code hearth:work/quarry}: a stone pit at the edge of town. Its working face is cut in benches two blocks high and
 * two wide, stepping down to the floor where the quarrymen work the rock; on a hillside the benches eat back into the
 * slope and the face behind stands as a cliff of fresh stone. A ramp of steps leads down from the yard; a gantry over
 * the pit lowers a block on its chain; at the top the masons' shed holds the stonecutters, and blocks wait in stacks
 * by the spoil heaps for the carts. A fence keeps the rim.
 *
 * <p>Job sites: QUARRIER on the pit floor at the face, MASON at the stonecutters. Storage: the shed's chest and the
 * block stacks.
 *
 * <p>Params: {@code roof}, {@code variant}.
 */
public final class Quarry implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("work/quarry"), ArchetypeCategory.WORKPLACE,
		Set.of(JobType.QUARRIER, JobType.MASON), 0, 12, 20, 12, 20, 6, 12, SettlementTier.HAMLET, Set.of(),
		Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 0, 1.0);

	private static final int NONE = Integer.MIN_VALUE;

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int gate = w / 2;
		Yard.entrance(d);
		int fz = 4;
		// the yard at the top, level with the street
		Groundwork.level(d, 0, 0, w - 1, fz, 0, true);
		Yard.trodden(d, 1, 0, w - 2, fz, 0);

		// the pit: benches stepping down from its edge, a ramp from the yard
		int px0 = 1;
		int px1 = w - 2;
		int pz0 = fz + 1;
		int pz1 = dp - 2;
		int pw = px1 - px0 + 1;
		int pd = pz1 - pz0 + 1;
		int[][] lev = new int[pw][pd];
		int rings = Math.min(3, (Math.min(pw, pd) + 1) / 4 + 1);
		for (int x = 0; x < pw; x++) {
			for (int z = 0; z < pd; z++) {
				int dist = Math.min(Math.min(x, pw - 1 - x), Math.min(z, pd - 1 - z));
				int ring = Math.min(rings - 1, dist / 2);
				// a bench is cut only where the ground stands above it; where it falls away, the ground is left as it lies
				int g = d.site().ground(px0 + x, pz0 + z);
				lev[x][z] = d.site().isWater(px0 + x, pz0 + z) ? NONE : Math.min(-2 - 2 * ring, g);
			}
		}
		int bottom = -2 - 2 * (rings - 1);
		// the ramp down the middle from the yard's edge
		int rx = gate - px0;
		List<int[]> ramp = new ArrayList<>();
		for (int z = 0; z < pd; z++) {
			int l = -(z + 1);
			if (lev[rx][z] == NONE || l < lev[rx][z] || l >= d.site().ground(px0 + rx, pz0 + z)) {
				break;
			}
			lev[rx][z] = l;
			ramp.add(new int[]{px0 + rx, pz0 + z, l});
			if (l <= bottom) {
				break;
			}
		}
		Block[] rock = rock(d);
		dig(d, lev, px0, pz0, pw, pd, rock);
		Mat stairs = d.mat(Role.PAVING).has(Form.STAIRS) ? d.mat(Role.PAVING) : d.mat(Role.FOUNDATION);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] c : ramp) {
				d.set(c[0], c[2] - 1, c[1], stairs.stairsOrSlab(Dirs.FRONT, Half.BOTTOM));
			}
		}

		// the gantry across the pit, lowering a block to the floor
		int gz = pz0 + pd / 2;
		if (lev[0][gz - pz0] != NONE && lev[pw - 1][gz - pz0] != NONE && rings >= 2) {
			int gy = lev[0][gz - pz0];
			int top = Math.min(4, d.maxHeight() - 1) - gy;
			// the chain hangs clear of the ramp
			int gx1 = (px0 + px1) / 2 == px0 + rx ? px1 - 1 : px1;
			Lifts.gantry(d, px0, gz, gx1, gz, gy, top, bottom + 2, rock[1].defaultBlockState());
		}

		// the quarrymen work the face from the floor
		int sites = 0;
		for (int z = pd - 1; z >= 0 && sites < 2; z--) {
			for (int x = 0; x < pw && sites < 2; x++) {
				if (lev[x][z] != bottom || x == rx || !d.isEmpty(px0 + x, bottom, pz0 + z) || !d.isEmpty(px0 + x, bottom + 1, pz0 + z)) {
					continue;
				}
				for (Direction dir : Dirs.HORIZONTAL) {
					int nx = x + Dirs.dx(dir);
					int nz = z + Dirs.dz(dir);
					boolean face = nx < 0 || nz < 0 || nx >= pw || nz >= pd || lev[nx][nz] != NONE && lev[nx][nz] > bottom;
					if (face && (nx != rx || nz < 0)) {
						d.poi(PoiType.JOB_SITE, px0 + x, bottom, pz0 + z, dir, JobType.QUARRIER);
						sites++;
						break;
					}
				}
			}
		}
		// where the floor has no face of its own, the quarrymen work any face of the benches, or the stacks at the top
		for (int z = pd - 1; z >= 0 && sites < 2; z--) {
			for (int x = 0; x < pw && sites < 2; x++) {
				int l = lev[x][z];
				if (l == NONE || x == rx || l >= d.site().ground(px0 + x, pz0 + z) || !d.isEmpty(px0 + x, l, pz0 + z) || !d.isEmpty(px0 + x, l + 1, pz0 + z)) {
					continue;
				}
				for (Direction dir : Dirs.HORIZONTAL) {
					int nx = x + Dirs.dx(dir);
					int nz = z + Dirs.dz(dir);
					boolean in = nx >= 0 && nz >= 0 && nx < pw && nz < pd;
					if (in && lev[nx][nz] != NONE && lev[nx][nz] >= l + 2 && nx != rx) {
						d.poi(PoiType.JOB_SITE, px0 + x, l, pz0 + z, dir, JobType.QUARRIER);
						sites++;
						break;
					}
				}
			}
		}
		boolean yardSite = sites == 0;
		// a block being cut free on the floor, another on the bench
		int bx = px0 + (rx > pw / 2 ? 1 : pw - 2);
		int bz = pz0 + pd / 2;
		if (lev[bx - px0][bz - pz0] != NONE) {
			int by = lev[bx - px0][bz - pz0];
			d.inStage(StageKind.EXTERIOR, () -> d.set(bx, by, bz, rock[0].defaultBlockState()));
		}

		// the masons' shed in the yard, block stacks and spoil
		boolean shedLeft = rx > pw / 2 || d.chance(0.5);
		int sx0 = shedLeft ? 1 : w - 5;
		int sx1 = sx0 + 3;
		if (Math.abs(gate - sx0) <= 1 || Math.abs(gate - sx1) <= 1 || gate >= sx0 && gate <= sx1) {
			sx0 = shedLeft ? 1 : w - 4;
			sx1 = shedLeft ? Math.min(3, gate - 2) : w - 2;
		}
		Roofs.Roof roof = null;
		if (sx1 - sx0 >= 2) {
			roof = Shed.open(d, sx0, 1, sx1, fz - 1, 0, 3, Hall.pick(d, RoofForm.GABLE, RoofForm.HIP, RoofForm.SHED, RoofForm.PAGODA), Direction.Axis.X, 1,
				d.mat(Role.FOUNDATION), Dirs.BACK);
			d.carve(sx0 + 1, 0, 2, sx1 - 1, 2, fz - 2);
			Props.station(d, Blocks.STONECUTTER, sx0 + 1, 0, fz - 2, Dirs.FRONT, JobType.MASON);
			if (sx1 - sx0 >= 3) {
				Props.store(d, sx1 - 1, 0, fz - 2, Dirs.FRONT, true, List.of(new Object[]{"minecraft:cobblestone", 64}, new Object[]{"minecraft:stone", 32},
					new Object[]{"minecraft:iron_pickaxe", 2}));
			}
		} else {
			Props.station(d, Blocks.STONECUTTER, shedLeft ? 1 : w - 2, 0, 2, Dirs.FRONT, JobType.MASON);
		}
		int ox = shedLeft ? w - 4 : 1;
		if (Math.abs(ox - gate) > 2 && Math.abs(ox + 1 - gate) > 1) {
			Props.stonePallet(d, ox, 0, 2, d.mat(Role.WALL_ACCENT), d.mat(Role.FLOOR));
			d.poi(PoiType.STORAGE, ox, 0, 1, Dirs.BACK);
			if (yardSite) {
				d.poi(PoiType.JOB_SITE, ox + 1, 0, 1, Dirs.BACK, JobType.QUARRIER);
				yardSite = false;
			}
		}
		if (yardSite) {
			d.poi(PoiType.JOB_SITE, gate, 0, 2, Dirs.BACK, JobType.QUARRIER);
		}
		int hx = shedLeft ? w - 3 : 2;
		Props.heap(d, hx - 1, 3, hx + 1, fz, 0, new Block[]{Blocks.GRAVEL, rock[2], Blocks.COBBLESTONE}, null);

		// the fence round the plot, open at the gate
		List<int[]> gaps = new ArrayList<>();
		for (int x = gate - 1; x <= gate + 1; x++) {
			gaps.add(new int[]{x, 0});
		}
		Yard.enclose(d, 0, 0, w - 1, dp - 1, gaps);
		// a rail at the pit's edge either side of the ramp head
		Mat fence = Hall.woodFence(d);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int x = px0; x <= px1; x++) {
				if (Math.abs(x - gate) <= 0 || !d.isAbsent(x, 0, fz)) {
					continue;
				}
				d.set(x, 0, fz, fence.fence());
			}
		}
		Signs.post(d, gate + 2, 0, 0, Dirs.LEFT, 3, "quarry", "Quarry");
		Groundwork.clear(d, 0, 0, w - 1, fz, (roof != null ? roof.ridgeY : 4) + 3);
	}

	/** The rock the face is cut in: the culture's footing stone, or stone where its footings are timber. */
	private static Block[] rock(Draft d) {
		Mat found = d.mat(Role.FOUNDATION);
		Block b = found.mainOr(Form.FULL, Blocks.STONE);
		String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath();
		if (id.contains("planks") || id.contains("log") || id.contains("wood")) {
			return new Block[]{Blocks.STONE, Blocks.ANDESITE, Blocks.COBBLESTONE, Blocks.TUFF};
		}
		if (id.contains("sandstone") && id.contains("red")) {
			return new Block[]{Blocks.RED_SANDSTONE, Blocks.TERRACOTTA, Blocks.SMOOTH_RED_SANDSTONE, Blocks.DYED_TERRACOTTA.pick(net.minecraft.world.item.DyeColor.ORANGE)};
		}
		if (id.contains("sandstone")) {
			return new Block[]{Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SAND};
		}
		if (id.contains("limestone") || id.contains("diorite")) {
			return new Block[]{b, Props.block("expanse:limestone", Blocks.DIORITE), Blocks.STONE, Blocks.CALCITE};
		}
		if (id.contains("terracotta")) {
			return new Block[]{Blocks.TERRACOTTA, Blocks.RED_SANDSTONE, Blocks.DYED_TERRACOTTA.pick(net.minecraft.world.item.DyeColor.BROWN), Blocks.DYED_TERRACOTTA.pick(net.minecraft.world.item.DyeColor.ORANGE)};
		}
		return new Block[]{Blocks.STONE, Blocks.ANDESITE, Blocks.COBBLESTONE, b};
	}

	private static Block rockAt(Draft d, Block[] rock, int x, int y, int z) {
		double u = Noise.value(d.seed() ^ 0x40CC, x / 2.0, y / 1.5, z / 2.0);
		return rock[u < 0.45 ? 0 : u < 0.7 ? 1 : u < 0.88 ? 3 : 2];
	}

	/**
	 * Digs the benches: each cell of the pit down to its level (air from there up to the ground), its floor laid in
	 * rock, and every face where it meets higher ground lined with fresh-cut rock.
	 */
	private static void dig(Draft d, int[][] lev, int px0, int pz0, int pw, int pd, Block[] rock) {
		try (var s = d.stage(StageKind.EARTHWORK)) {
			for (int x = 0; x < pw; x++) {
				for (int z = 0; z < pd; z++) {
					int l = lev[x][z];
					if (l == NONE) {
						continue;
					}
					int wx = px0 + x;
					int wz = pz0 + z;
					int g = d.site().ground(wx, wz);
					if (l >= g) {
						continue;
					}
					for (int y = l; y < g; y++) {
						d.set(wx, y, wz, Blocks.AIR.defaultBlockState());
					}
					d.set(wx, l - 1, wz, rockAt(d, rock, wx, l - 1, wz).defaultBlockState());
				}
			}
			// the faces
			for (int x = 0; x < pw; x++) {
				for (int z = 0; z < pd; z++) {
					int l = lev[x][z];
					if (l == NONE) {
						continue;
					}
					for (Direction dir : Dirs.HORIZONTAL) {
						int nx = x + Dirs.dx(dir);
						int nz = z + Dirs.dz(dir);
						int wx = px0 + nx;
						int wz = pz0 + nz;
						boolean inside = nx >= 0 && nz >= 0 && nx < pw && nz < pd;
						int high;
						if (inside) {
							if (lev[nx][nz] == NONE || lev[nx][nz] <= l) {
								continue;
							}
							high = lev[nx][nz] - 1;
						} else {
							if (wx < 0 || wz < 0 || wx >= d.frontage() || wz >= d.depth()) {
								continue;
							}
							high = Groundwork.surface(d, wx, wz);
						}
						// the face up to the soil, which stays on the rim
						for (int y = l; y < high - (inside ? 0 : 1); y++) {
							if (y >= d.maxHeight()) {
								break;
							}
							var cur = d.get(wx, y, wz);
							if (cur == null || !cur.isAir()) {
								d.set(wx, y, wz, rockAt(d, rock, wx, y, wz).defaultBlockState());
							}
						}
					}
				}
			}
		}
	}
}
