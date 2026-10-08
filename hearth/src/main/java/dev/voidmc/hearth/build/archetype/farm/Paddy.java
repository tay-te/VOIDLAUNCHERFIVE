package dev.voidmc.hearth.build.archetype.farm;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.homestead.Crops;
import dev.voidmc.hearth.build.kit.homestead.Sheds;
import dev.voidmc.hearth.build.kit.homestead.Spots;
import dev.voidmc.hearth.build.kit.homestead.Terraces;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:farm/paddy}: flooded rice terraces stepping down the hillside. Each terrace is a level sheet of shallow
 * water over mud, planted in rows of young rice (seagrass, the tall kind where it has grown), held at its downhill edge
 * by a stone retaining face with a raised bund of earth along its top; the bunds and a stepped path at one side are the
 * only dry ground. A thatched field shelter stands in a corner with the farmer's compost bin, a scarecrow keeps watch, and
 * a little shrine with a stone lantern guards the top terrace. On level ground the paddy is one or two flooded fields
 * bounded by bunds.
 *
 * <p>Job sites (FARMER): the shelter's compost bin and a stand on the bund path every few rows.
 *
 * <p>Params (all optional): {@code shelter} (boolean), {@code terraced} (boolean, from the planner).
 */
public final class Paddy implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("farm/paddy"), ArchetypeCategory.FARM, Set.of(JobType.FARMER), 0,
		9, 19, 9, 19, 6, 12, SettlementTier.CAMP, Set.of(Culture.KARST, Culture.TROPICAL),
		Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS, ArchetypeInfo.SiteFlag.HILLSIDE), List.of(), 0, 1.5);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		boolean leftSide = d.chance(0.5);
		int access = leftSide ? 1 : w - 2;
		Terraces t = Terraces.plan(d, 0, 0, w - 1, dp - 1, 3, 6, 3, access, w / 2);
		int path = t.alongX ? 1 : access;
		int gateX = t.alongX ? Math.clamp(w / 2, 2, w - 3) : access;
		d.entrance(gateX, Terraces.street(d, gateX), -1);
		t.shape(d, Terraces.Edge.BUND, path);
		t.accessPath(d);
		// the outer bund: a raised rim of earth round the whole paddy, open at the gate
		rim(d, t, gateX);
		// the flooded beds
		for (Terraces.Band b : t.bands) {
			int from = Math.max(1, t.plantFrom(b));
			int to = Math.min(t.alongX ? d.frontage() - 2 : dp - 2, t.plantTo(b));
			for (int a = from; a <= to; a++) {
				for (int c = t.acrossMin() + 1; c <= t.acrossMax() - 1; c++) {
					if (c == path) {
						continue;
					}
					int[] xz = t.cell(a, c);
					flood(d, xz[0], b.level(), xz[1], a, c);
				}
			}
		}
		// the shelter in a back corner, on the top terrace's dry rim
		int[] shed = null;
		if (d.param("shelter", w >= 11 && dp >= 11)) {
			shed = shelter(d, t, w, dp, leftSide);
		}
		if (shed == null) {
			// no corner for the shelter: the bin and the seed chest stand on the bund path instead
			PathStore.beside(d, t, path, JobType.FARMER, List.of(new Object[]{"minecraft:wheat_seeds", 16}, new Object[]{"minecraft:bone_meal", 6}));
		}
		// a shrine with a stone lantern at the top, a scarecrow over the biggest sheet
		shrine(d, t, w, dp, leftSide, shed);
		// stands on the path beside the beds
		Direction into = t.alongX ? Dirs.BACK : leftSide ? Dirs.RIGHT : Dirs.LEFT;
		for (Terraces.Band b : t.bands) {
			for (int a = t.plantFrom(b) + 1; a <= t.plantTo(b); a += 3) {
				int[] xz = t.cell(a, path);
				Spots.onGround(d, PoiType.JOB_SITE, xz[0], b.level(), xz[1], into, JobType.FARMER);
			}
		}
		// head room over every step through the banks
		StepRoom.clear(d, t.minLevel() - 3, t.maxLevel() + 1);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, t.maxLevel() + 6));
	}

	/** A sheet of water over mud with rice in rows: seagrass in most cells, the tall kind here and there, open water between the rows. */
	private static void flood(Draft d, int x, int level, int z, int a, int c) {
		BlockState here = d.get(x, level, z);
		if (here != null && !here.isAir()) {
			return;
		}
		double u = Noise.unit(d.seed() ^ 0x41CE, x, 0, z);
		boolean row = (c & 1) == 0;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, level - 2, z, Blocks.MUD.defaultBlockState());
			if (row && u < 0.85) {
				if (u < 0.12 && level < d.maxHeight()) {
					// grown rice: the tall plant in a hole puddled a block deeper, its head at the surface (it holds water
					// in both halves, so it must not stand out of the sheet or it would spill over the bunds)
					d.set(x, level - 3, z, Blocks.MUD.defaultBlockState());
					d.set(x, level - 2, z, Blocks.TALL_SEAGRASS.defaultBlockState()
						.setValue(net.minecraft.world.level.block.TallSeagrassBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
					d.set(x, level - 1, z, Blocks.TALL_SEAGRASS.defaultBlockState()
						.setValue(net.minecraft.world.level.block.TallSeagrassBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
				} else {
					d.set(x, level - 1, z, Blocks.SEAGRASS.defaultBlockState());
				}
			} else {
				d.set(x, level - 1, z, Blocks.WATER.defaultBlockState());
			}
		}
	}

	/** The outer bund: the plot's ring raised a block of earth over each terrace, a path gap at the gate. */
	private static void rim(Draft d, Terraces t, int gateX) {
		Mat ground = d.mat(Role.GROUND);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] c : dev.voidmc.hearth.build.draft.Draft.ring(t.x0, t.z0, t.x1, t.z1)) {
				int lv = t.level(c[0], c[1]);
				if (lv == Integer.MIN_VALUE || c[1] == 0 && Math.abs(c[0] - gateX) <= 0) {
					continue;
				}
				if (!d.isAbsent(c[0], lv, c[1]) && !d.isEmpty(c[0], lv, c[1])) {
					continue;
				}
				BlockState below = d.get(c[0], lv - 1, c[1]);
				if (below != null && below.getBlock() instanceof net.minecraft.world.level.block.StairBlock) {
					continue;
				}
				if (Noise.unit(d.seed() ^ 0xB0D, c[0], 1, c[1]) < 0.75) {
					d.set(c[0], lv, c[1], ground.full());
				} else {
					d.set(c[0], lv, c[1], Blocks.COARSE_DIRT.defaultBlockState());
				}
				if (Noise.unit(d.seed() ^ 0xB0E, c[0], 1, c[1]) < 0.2 && lv + 1 < d.maxHeight()) {
					d.set(c[0], lv + 1, c[1], Blocks.SHORT_GRASS.defaultBlockState());
				}
			}
		}
		// the gate's path cell at the street
		int lv = t.level(gateX, 0);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(gateX, lv - 1, 0, d.mat(Role.PATH).full());
			d.set(gateX, lv, 0, Blocks.AIR.defaultBlockState());
		}
	}

	/**
	 * A thatched shelter on short posts in a back corner, over a dry pad on the top terrace: the compost bin, sacks of
	 * seed rice, a lantern. Returns its rectangle.
	 */
	private static int[] shelter(Draft d, Terraces t, int w, int dp, boolean leftSide) {
		int sw = 4;
		int sd = 3;
		int z1 = dp - 2;
		int z0 = z1 - sd + 1;
		int x0 = leftSide ? w - 1 - sw : 1;
		int x1 = x0 + sw - 1;
		Terraces.Band b = t.band(x0, z0);
		if (b == null || t.alongX) {
			return null;
		}
		for (int z = z0 - 1; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				if (t.band(x, z) != b || z < t.plantFrom(b) || z > t.plantTo(b)) {
					return null;
				}
			}
		}
		int y = b.level();
		if (y + 6 >= d.maxHeight()) {
			return null;
		}
		// a dry pad: the beds there filled back to earth
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = z0 - 1; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					d.set(x, y - 2, z, d.mat(Role.FILL).full());
					d.set(x, y - 1, z, z == z0 - 1 ? d.mat(Role.PATH).full() : d.mat(Role.GROUND).full());
					d.air(x, y, z);
					d.air(x, y + 1, z);
				}
			}
		}
		Sheds.lean(d, x0, z0, x1, z1, y, Dirs.FRONT, 3, false);
		Furniture.workstation(d, JobType.FARMER, x0 + 1, y, z1 - 1, Dirs.FRONT);
		// the sacks of seed rice in a chest beside the bin
		Furniture.chest(d, x1 - 1, y, z1 - 1, Dirs.FRONT, List.of(new Object[]{"minecraft:wheat_seeds", 16}, new Object[]{"minecraft:bone_meal", 6}));
		// the pad's path joins the bund path
		return new int[]{x0, z0, x1, z1};
	}

	/**
	 * A shrine at the top: a stone lantern (a lantern on a post of wall stone under a cap) beside the path on the
	 * highest terrace, and a scarecrow standing in the water of the biggest sheet.
	 */
	private static void shrine(Draft d, Terraces t, int w, int dp, boolean leftSide, int[] shed) {
		Terraces.Band top = t.bands.get(0);
		for (Terraces.Band b : t.bands) {
			if (b.level() > top.level()) {
				top = b;
			}
		}
		Mat stone = d.mat(Role.FOUNDATION);
		// on the rim beside the path at the top terrace's far end
		int a = t.alongX ? (top.to() >= w - 2 ? top.to() - 1 : top.to()) : Math.min(dp - 2, top.to());
		int c = t.access() + (t.access() <= 1 ? 1 : -1);
		int[] xz = t.cell(a, c);
		int y = top.level();
		if (shed != null && xz[0] >= shed[0] - 1 && xz[0] <= shed[2] + 1 && xz[1] >= shed[1] - 2) {
			return;
		}
		if (y + 4 < d.maxHeight() && stone.has(Form.WALL)) {
			try (var s = d.stage(StageKind.EXTERIOR)) {
				d.set(xz[0], y - 2, xz[1], stone.full());
				d.set(xz[0], y - 1, xz[1], stone.full());
				d.set(xz[0], y, xz[1], stone.wall());
				d.set(xz[0], y + 1, xz[1], States.lantern(d.mat(Role.LIGHT).mainOr(Form.FULL, Blocks.LANTERN), false));
				d.set(xz[0], y + 2, xz[1], stone.has(Form.SLAB) ? stone.slab(SlabType.BOTTOM) : stone.full());
			}
		}
		// a scarecrow on a bund-built island in the middle of the biggest sheet
		Terraces.Band big = null;
		for (Terraces.Band b : t.bands) {
			if (big == null || b.width() > big.width()) {
				big = b;
			}
		}
		if (big != null && big.width() >= 4 && big.level() + 3 < d.maxHeight()) {
			int ma = (t.plantFrom(big) + t.plantTo(big)) / 2;
			int mc = (t.acrossMin() + t.acrossMax()) / 2 + (t.access() <= 1 ? 1 : -1);
			int[] m = t.cell(ma, mc);
			// not on the shelter's pad, where its arms would bar the way in
			if (shed != null && m[0] + 1 >= shed[0] && m[0] - 1 <= shed[2] && m[1] >= shed[1] - 2 && m[1] <= shed[3]) {
				return;
			}
			try (var s = d.stage(StageKind.EXTERIOR)) {
				d.set(m[0], big.level() - 1, m[1], Blocks.MUD.defaultBlockState());
				d.remove(m[0], big.level(), m[1]);
			}
			Crops.scarecrow(d, m[0], big.level(), m[1], Dirs.FRONT);
			try (var s = d.stage(StageKind.EXTERIOR)) {
				d.set(m[0], big.level() - 1, m[1], Blocks.MUD.defaultBlockState());
			}
		}
	}
}
