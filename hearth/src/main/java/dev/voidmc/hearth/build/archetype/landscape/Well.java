package dev.voidmc.hearth.build.archetype.landscape;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
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
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:landscape/well}: the village well, where folk meet. A stone curb round a deep shaft of water on a paved
 * square, two posts carrying a little roof in the culture's manner (a gable, a curved hip with lifted corners, a dome on
 * four posts in the desert) with a windlass beam and a bucket on a chain over the water, benches facing it, a trough,
 * flowers in pots and a lantern on a post at a corner.
 *
 * <p>Registers a MEETING_POINT at the curb and an entrance at the middle of the street front. Params: none.
 */
public final class Well implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("landscape/well"), ArchetypeCategory.LANDSCAPE, Set.of(), 0,
		5, 9, 5, 9, 6, 9, SettlementTier.CAMP, Set.of(), Set.of(ArchetypeInfo.SiteFlag.CENTRAL), List.of(), 0, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Habits h = d.habits();
		int cx = (w - 1) / 2;
		int cz = (dp - 1) / 2;
		boolean big = w >= 7 && dp >= 7;
		// folk come in from the street at the middle of the front
		d.entrance(cx, 0, -1);
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		// paving round the well, ragged at the edge
		Mat paving = d.mat(Role.PAVING);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 0; z < dp; z++) {
				for (int x = 0; x < w; x++) {
					int dist = Math.max(Math.abs(x - cx), Math.abs(z - cz));
					boolean edge = x == 0 || z == 0 || x == w - 1 || z == dp - 1;
					if (!edge || Noise.unit(d.seed(), x, 0, z) < 0.6) {
						d.set(x, -1, z, paving.full());
					}
				}
			}
		}
		// the curb and the shaft
		Mat curb = d.mat(Role.FOUNDATION);
		Mat coping = d.mat(Role.WALL_ACCENT);
		int r = big ? 1 : 1;
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int dz = -r; dz <= r; dz++) {
				for (int dx = -r; dx <= r; dx++) {
					int x = cx + dx;
					int z = cz + dz;
					boolean rim = Math.abs(dx) == r || Math.abs(dz) == r;
					if (rim) {
						for (int y = -5; y <= 0; y++) {
							d.set(x, y, z, curb.full());
						}
						boolean corner = Math.abs(dx) == r && Math.abs(dz) == r;
						d.set(x, 1, z, corner ? (coping.has(Form.WALL) ? coping.wall() : curb.wall()) : coping.slab(SlabType.BOTTOM));
					} else {
						d.set(x, -6, z, Blocks.GRAVEL.defaultBlockState());
						for (int y = -5; y <= -1; y++) {
							d.set(x, y, z, Blocks.WATER.defaultBlockState());
						}
						d.set(x, 0, z, Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		// the roof over it in the culture's manner
		RoofForm form = h.roof().pick(d.random(), RoofForm.GABLE, RoofForm.HIP, RoofForm.PAGODA, RoofForm.DOME, RoofForm.FLAT, RoofForm.CONE);
		int postH = 3;
		Mat post = d.mat(Role.FRAME);
		Mat fence = d.mat(Role.FENCE);
		boolean four = form == RoofForm.DOME || form == RoofForm.HIP || form == RoofForm.PAGODA || form == RoofForm.CONE || form == RoofForm.FLAT;
		int[][] posts = four ? new int[][]{{cx - r, cz - r}, {cx + r, cz - r}, {cx - r, cz + r}, {cx + r, cz + r}} : new int[][]{{cx - r, cz}, {cx + r, cz}};
		try (var s = d.stage(StageKind.FRAME)) {
			for (int[] p : posts) {
				for (int y = 1; y <= postH + 1; y++) {
					d.set(p[0], y, p[1], post.log(Direction.Axis.Y).orElse(fence.fence()));
				}
			}
			// a frame of beams round the top of the posts carries the roof
			Mat ringBeam = d.mat(Role.BEAM);
			for (int dz = -r; dz <= r; dz++) {
				for (int dx = -r; dx <= r; dx++) {
					if (Math.abs(dx) == r || Math.abs(dz) == r) {
						d.set(cx + dx, postH + 1, cz + dz, ringBeam.log(Math.abs(dz) == r ? Direction.Axis.X : Direction.Axis.Z));
					}
				}
			}
			// the windlass beam across, the chain and the bucket (a cauldron) over the water
			for (int x = cx - r; x <= cx + r; x++) {
				d.set(x, postH + 1, cz, d.mat(Role.BEAM).log(Direction.Axis.X));
			}
			d.set(cx, postH, cz, States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
			d.set(cx, postH - 1, cz, States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
			d.set(cx, postH - 2, cz, Blocks.CAULDRON.defaultBlockState());
		}
		int roofY = postH + 2;
		switch (form) {
			case DOME -> dome(d, cx, cz, r, roofY);
			case FLAT -> {
				Mat deck = d.mat(Role.ROOF);
				try (var s = d.stage(StageKind.ROOF)) {
					for (int dz = -r - 1; dz <= r + 1; dz++) {
						for (int dx = -r - 1; dx <= r + 1; dx++) {
							boolean rim = Math.abs(dx) == r + 1 || Math.abs(dz) == r + 1;
							d.set(cx + dx, roofY, cz + dz, rim ? deck.slab(SlabType.TOP) : deck.full());
						}
					}
				}
			}
			default -> {
				Roofs.Opts o = Roofs.Opts.of(d).withEaves(1, 1);
				RoofForm f = form == RoofForm.CONE ? RoofForm.HIP : form;
				Roofs.build(d, cx - r, cz - r, cx + r, cz + r, roofY - 1, f, Direction.Axis.X, o, false, false);
			}
		}
		d.poi(PoiType.MEETING_POINT, cx, 0, cz - r - 1, Dirs.BACK);
		// benches facing the well, a trough, pots and a lamp
		if (big) {
			Furniture.chair(d, cx - 1, 0, 0 == cz - r - 2 ? 0 : cz - r - 2, Dirs.FRONT);
			Furniture.chair(d, cx + 1, 0, cz - r - 2 < 0 ? 0 : cz - r - 2, Dirs.FRONT);
			Furniture.chair(d, cx, 0, Math.min(dp - 1, cz + r + 2), Dirs.BACK);
			Garden.waterButt(d, Math.min(w - 1, cx + r + 2), 0, cz);
			Garden.lampPost(d, 0, 0, 0, 2, null);
			Garden.lampPost(d, w - 1, 0, dp - 1, 2, null);
		} else {
			Garden.lampPost(d, 0, 0, dp - 1, 2, null);
		}
		for (int[] p : new int[][]{{0, cz}, {w - 1, cz}, {cx, dp - 1}}) {
			if (d.isAbsent(p[0], 0, p[1]) && d.chance(0.7)) {
				Garden.pot(d, p[0], 0, p[1]);
			}
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, roofY + 4);
	}

	/** A small dome on the four posts (desert wells): a ring of stairs, then slabs, then a finial. */
	private static void dome(Draft d, int cx, int cz, int r, int y) {
		Mat roof = d.mat(Role.ROOF);
		Mat accent = d.mat(Role.WALL_ACCENT);
		Roofs.Field dome = Roofs.dome(cx, cz, r + 1, 0.8);
		Roofs.Roof rr = Roofs.raster(d, dome, y, cx - r - 1, cz - r - 1, cx + r + 1, cz + r + 1, roof, accent, Habits.Ridge.FULL);
		// a drum over the ring beam carries the dome
		try (var s = d.stage(StageKind.ROOF)) {
			for (int dz = -r; dz <= r; dz++) {
				for (int dx = -r; dx <= r; dx++) {
					if (Math.abs(dx) != r && Math.abs(dz) != r || !rr.covers(cx + dx, cz + dz)) {
						continue;
					}
					for (int yy = y; yy < rr.bottomY(cx + dx, cz + dz); yy++) {
						d.set(cx + dx, yy, cz + dz, accent.full());
					}
				}
			}
		}
		try (var s = d.stage(StageKind.ROOF)) {
			d.set(cx, rr.ridgeY + 1, cz, accent.has(Form.WALL) ? accent.wall() : d.mat(Role.FENCE).fence());
		}
	}
}
