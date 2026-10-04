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
import dev.voidmc.hearth.build.kit.landscape.Lamps;
import dev.voidmc.hearth.build.kit.landscape.Manner;
import dev.voidmc.hearth.build.kit.landscape.Paving;
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
 * square, and over it the culture's well head: posts carrying a little roof (a gable, a hip, a half-hip, a curved
 * karst roof) with a windlass and its crank, the bucket on a chain; a well sweep in the north and on the steppe (a tall
 * post, a long pole with a stone on its short arm and the bucket on its long one); a wrought-iron arch with a lantern
 * on its crown in the south; a domed kiosk on arched piers in the desert. Round it benches facing the curb, a trough,
 * flowers in pots and lanterns on posts at the corners.
 *
 * <p>Registers a MEETING_POINT at the curb and an entrance at the middle of the street front. Params: {@code head}
 * ({@code windlass}, {@code sweep}, {@code arch}, {@code kiosk}; default by culture).
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
		// what stands over the shaft, in the culture's manner
		int roofY = switch (variant(d)) {
			case SWEEP -> sweep(d, cx, cz, r, w, dp);
			case ARCH -> arch(d, cx, cz, r);
			case KIOSK -> kiosk(d, cx, cz, r);
			default -> windlass(d, h, cx, cz, r);
		};
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
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, roofY + 4));
	}

	enum Head {
		WINDLASS, SWEEP, ARCH, KIOSK
	}

	/** The well head a culture builds: a sweep in the north and on the steppe, an iron arch in the south, a domed kiosk in the desert. */
	static Head variant(Draft d) {
		String forced = d.param("head", "");
		for (Head v : Head.values()) {
			if (v.name().equalsIgnoreCase(forced)) {
				return v;
			}
		}
		return switch (Manner.culture(d)) {
			case NORTHERN, TUNDRA, STEPPE -> Head.SWEEP;
			case MEDITERRANEAN -> Head.ARCH;
			case DESERT, MESA -> Head.KIOSK;
			default -> Head.WINDLASS;
		};
	}

	/** Posts carrying a little roof in the culture's form, a windlass with its crank, the bucket on its chain. Returns the top. */
	private static int windlass(Draft d, Habits h, int cx, int cz, int r) {
		RoofForm form = h.roof().pick(d.random(), RoofForm.GABLE, RoofForm.HIP, RoofForm.PAGODA, RoofForm.HALF_HIP);
		int postH = 3;
		Mat post = d.mat(Role.FRAME);
		Mat fence = d.mat(Role.FENCE);
		boolean four = form == RoofForm.HIP || form == RoofForm.PAGODA;
		int[][] posts = four ? new int[][]{{cx - r, cz - r}, {cx + r, cz - r}, {cx - r, cz + r}, {cx + r, cz + r}} : new int[][]{{cx - r, cz}, {cx + r, cz}};
		try (var s = d.stage(StageKind.FRAME)) {
			for (int[] p : posts) {
				for (int y = 1; y <= postH + 1; y++) {
					d.set(p[0], y, p[1], post.log(Direction.Axis.Y).orElse(fence.fence()));
				}
			}
			Mat ringBeam = d.mat(Role.BEAM);
			for (int dz = -r; dz <= r; dz++) {
				for (int dx = -r; dx <= r; dx++) {
					if (Math.abs(dx) == r || Math.abs(dz) == r) {
						d.set(cx + dx, postH + 1, cz + dz, ringBeam.log(Math.abs(dz) == r ? Direction.Axis.X : Direction.Axis.Z));
					}
				}
			}
			// the windlass: a beam across with a crank handle out past one post, the chain and the bucket
			for (int x = cx - r; x <= cx + r; x++) {
				d.set(x, postH, cz, d.mat(Role.BEAM).log(Direction.Axis.X));
			}
			if (cx + r + 1 < d.frontage()) {
				d.set(cx + r + 1, postH, cz, Lamps.post(d));
			}
			d.set(cx, postH - 1, cz, States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
			d.set(cx, postH - 2, cz, Blocks.CAULDRON.defaultBlockState());
		}
		Roofs.Opts o = Roofs.Opts.of(d).withEaves(1, 1);
		Roofs.Built b = Roofs.build(d, cx - r, cz - r, cx + r, cz + r, postH + 1, form, Direction.Axis.X, o, false, false);
		return b.roof().ridgeY;
	}

	/**
	 * A well sweep: a post beside the curb, a long pole pivoting on it with a counterweight stone at the short end and the
	 * bucket on a chain over the shaft at the long one. Returns the top.
	 */
	private static int sweep(Draft d, int cx, int cz, int r, int w, int dp) {
		Mat post = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		int px = Math.min(w - 1, cx + r + 1);
		int pz = cz;
		int top = Math.min(d.maxHeight() - 2, 5);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int y = 0; y <= top; y++) {
				d.set(px, y, pz, post.log(Direction.Axis.Y).orElse(post.full()));
			}
			int pivot = top + 1;
			d.set(px, pivot, pz, beam.log(Direction.Axis.X).orElse(beam.full()));
			// the long arm dips toward the shaft
			for (int x = cx; x < px; x++) {
				d.set(x, x == px - 1 ? pivot : pivot - 1, pz, beam.log(Direction.Axis.X).orElse(beam.full()));
			}
			// the short arm rises past the post with a stone hung on it
			if (px + 1 < w) {
				d.set(px + 1, pivot, pz, beam.log(Direction.Axis.X).orElse(beam.full()));
				d.set(px + 1, pivot - 1, pz, Blocks.COBBLESTONE.defaultBlockState());
			}
			for (int y = pivot - 2; y >= 2; y--) {
				d.set(cx, y, cz, States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
			}
			d.set(cx, 1, cz, Blocks.CAULDRON.defaultBlockState());
		}
		return top + 1;
	}

	/** A wrought-iron arch over the curb with a lantern on its crown, the bucket on its chain. Returns the top. */
	private static int arch(Draft d, int cx, int cz, int r) {
		Mat stone = d.mat(Role.WALL_ACCENT);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int x : new int[]{cx - r, cx + r}) {
				d.set(x, 2, cz, Blocks.IRON_BARS.defaultBlockState());
				d.set(x, 3, cz, Blocks.IRON_BARS.defaultBlockState());
			}
			for (int x = cx - r; x <= cx + r; x++) {
				d.set(x, 4, cz, Blocks.IRON_BARS.defaultBlockState());
			}
			d.set(cx, 5, cz, States.lantern(Lamps.lantern(d), false));
			d.set(cx, 3, cz, States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
			d.set(cx, 2, cz, Blocks.CAULDRON.defaultBlockState());
		}
		return 5;
	}

	/** A domed kiosk: masonry piers with arched openings, a flat roof and a stepped cupola. Returns the top. */
	private static int kiosk(Draft d, int cx, int cz, int r) {
		Mat stone = Manner.masonry(d);
		Mat roof = d.mat(Role.ROOF);
		Mat accent = Paving.inlay(d);
		int x0 = cx - r - 1;
		int x1 = cx + r + 1;
		int z0 = cz - r - 1;
		int z1 = cz + r + 1;
		if (x0 < 0 || z0 < 0 || x1 >= d.frontage() || z1 >= d.depth()) {
			x0 = cx - r;
			x1 = cx + r;
			z0 = cz - r;
			z1 = cz + r;
		}
		int plate = 4;
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
				for (int y = 0; y < plate; y++) {
					d.set(c[0], y, c[1], stone.full());
				}
			}
			for (int[] c : Draft.ring(x0, z0, x1, z1)) {
				d.set(c[0], plate, c[1], stone.full());
			}
			if (stone.has(Form.STAIRS) && x1 - x0 >= 4) {
				for (int z : new int[]{z0, z1}) {
					d.set(x0 + 1, plate - 1, z, stone.stairs(Dirs.LEFT, Half.TOP));
					d.set(x1 - 1, plate - 1, z, stone.stairs(Dirs.RIGHT, Half.TOP));
				}
				for (int x : new int[]{x0, x1}) {
					d.set(x, plate - 1, z0 + 1, stone.stairs(Dirs.FRONT, Half.TOP));
					d.set(x, plate - 1, z1 - 1, stone.stairs(Dirs.BACK, Half.TOP));
				}
			}
		}
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = z0 + 1; z < z1; z++) {
				for (int x = x0 + 1; x < x1; x++) {
					d.set(x, plate, z, roof.full());
				}
			}
			for (int z = cz - 1; z <= cz + 1; z++) {
				for (int x = cx - 1; x <= cx + 1; x++) {
					if (x == cx && z == cz) {
						d.set(x, plate + 1, z, roof.full());
					} else if (x == cx || z == cz) {
						d.set(x, plate + 1, z, roof.stairsOrSlab(Dirs.nearest(cx - x, cz - z), Half.BOTTOM));
					} else {
						d.set(x, plate + 1, z, roof.slab(SlabType.BOTTOM));
					}
				}
			}
			d.set(cx, plate + 2, cz, accent.full());
		}
		try (var s = d.stage(StageKind.FRAME)) {
			d.set(cx, plate - 1, cz, States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
			d.set(cx, plate - 2, cz, Blocks.CAULDRON.defaultBlockState());
		}
		return plate + 2;
	}
}
