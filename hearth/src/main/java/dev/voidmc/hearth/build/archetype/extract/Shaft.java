package dev.voidmc.hearth.build.archetype.extract;

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
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.extraction.Pithead;
import dev.voidmc.hearth.build.kit.extraction.Rock;
import dev.voidmc.hearth.build.kit.extraction.Spoil;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Shed;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

/**
 * {@code hearth:extract/shaft}: a shaft sunk to ore under level ground (docs/LIVING_WORLD.md §7.3), the way a mine is
 * opened where there is no hillside to drive an adit into. On the surface the shaft house: an open timber house on four
 * corner posts over a stone collar, and under its roof the windlass — an axle on two trestles across the mouth, a crank
 * on its end — its chain going down the shaft to the kibble at the bottom. The shaft is two by two, its ladder way up
 * one wall; every lift sunk ends in a station, a short crosscut out of the back wall where the miners work the ore off
 * the shaft, lit by its own lantern; lanterns hang from bearers set across the shaft between. Spoil from the sinking is
 * heaped round the house, growing lift by lift.
 *
 * <p>Parts: 0 the yard and the shaft house with its windlass; then one per lift sunk, top down (the first bringing the
 * collar): its cuts course by course, its ladders, bearers and lanterns, its station, the chain let down through it, the
 * tip's growth, its work face (the station's end; at the last lift the shaft's bottom).
 *
 * <p>Job sites: MINER at the bench, at every station and at the bottom (WORK_FACE). Params (§8.5): {@code depth} (how far
 * down the shaft is sunk below the floor; default 16–32 by seed, at most 96), {@code realm_below} (recorded: a realm
 * the shaft reaches), {@code rock}, {@code site}, {@code kind}, {@code face}.
 */
public final class Shaft implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("extract/shaft"), ArchetypeCategory.EXTRACTION, Set.of(JobType.MINER), 0, 7, 11,
		7, 11, 10, 16, SettlementTier.VILLAGE, Set.of(), Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 0, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Rock rock = Rock.of(d);
		int ex = Yard.entrance(d);
		// the shaft: interior a..a+1 × b..b+1; the collar ring round it; the house's eaves a row beyond
		int a = w / 2 - 1;
		int b = Math.clamp((dp - 2) / 2, 2, dp - 5);
		int yd = Math.max(1, b - 3);
		int[] base;
		try (var p = d.campaign(0)) {
			base = Pithead.levelYard(d, yd, ex);
			// the pad round the house; where the yard could not be levelled (a street at the foot of a bank) the pad runs
			// out to the street
			boolean yardOk = true;
			for (int z = 0; z <= yd; z++) {
				yardOk &= base[z * w + Math.max(0, a - 2)] == 0 && base[z * w + ex] == 0;
			}
			pad(d, Math.min(a - 2, ex), yardOk ? yd + 1 : 0, Math.max(a + 3, ex), Math.min(dp - 1, b + 3));
			if (!yardOk) {
				for (int z = 0; z <= yd; z++) {
					for (int x = Math.max(0, Math.min(a - 2, ex)); x <= Math.min(w - 1, Math.max(a + 3, ex)); x++) {
						base[z * w + x] = 0;
					}
				}
			}
		}
		int depth = Math.clamp(d.param("depth", d.between(16, 32)), 12, 96);
		int bottom = -depth;
		int n = Math.clamp(Math.round(depth / 10f), 2, 9);
		int[] liftTop = new int[n];
		int[] liftBottom = new int[n];
		for (int k = 0; k < n; k++) {
			liftTop[k] = k == 0 ? -1 : liftBottom[k - 1] - 1;
			liftBottom[k] = k == n - 1 ? bottom : -1 - (int) Math.round((k + 1) * (depth - 1) / (double) n);
		}

		Pithead head = new Pithead(d, rock, ex, yd, base);
		try (var p = d.campaign(0)) {
			head.way(Math.max(0, a - 2));
			head.shed(JobType.MINER);
			head.lamp();
			house(d, a, b);
			windlass(d, a, b);
		}

		Block lantern = d.mat(Role.LIGHT).mainOr(Form.FULL, Blocks.LANTERN);
		Mat beam = d.mat(Role.BEAM);
		BlockState air = Blocks.AIR.defaultBlockState();
		int stationLen = Math.min(2, dp - 2 - (b + 1));
		for (int k = 0; k < n; k++) {
			int part = k + 1;
			int top = liftTop[k];
			int lo = liftBottom[k];
			boolean last = k == n - 1;
			try (var ps = d.campaign(part)) {
				try (var st = d.stage(StageKind.EARTHWORK)) {
					int key = 0;
					for (int y = top; y >= lo; y--, key++) {
						try (var o = d.order(key)) {
							for (int z = b; z <= b + 1; z++) {
								for (int x = a; x <= a + 1; x++) {
									d.set(x, y, z, air);
								}
							}
						}
					}
					// the station out of the back wall at the lift's foot
					if (!last && stationLen >= 1) {
						try (var o = d.order(key)) {
							for (int z = b + 2; z < b + 2 + stationLen; z++) {
								for (int x = a; x <= a + 1; x++) {
									for (int y = lo + 2; y >= lo; y--) {
										d.set(x, y, z, air);
									}
								}
							}
						}
					}
				}
				if (k == 0) {
					collar(d, rock, a, b);
				}
				try (var st = d.stage(StageKind.EXTERIOR)) {
					// the ladder way up the left wall
					for (int y = lo; y <= top; y++) {
						d.set(a, y, b + 1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST));
					}
				}
				try (var st = d.stage(StageKind.FRAME)) {
					// bearers across the back of the shaft every eight blocks, a lantern under each (not by a station)
					for (int y = top - 3; y > lo + 3; y -= 8) {
						d.set(a + 1, y + 1, b + 1, beam.log(Direction.Axis.X));
					}
					if (last) {
						d.set(a + 1, lo + 3, b + 1, beam.log(Direction.Axis.X));
					}
				}
				try (var st = d.stage(StageKind.EXTERIOR)) {
					for (int y = top - 3; y > lo + 3; y -= 8) {
						d.set(a + 1, y, b + 1, States.lantern(lantern, true));
					}
					if (last) {
						d.set(a + 1, lo + 2, b + 1, States.lantern(lantern, true));
						d.poi(PoiType.WORK_FACE, a + 1, lo, b + 1, Dirs.BACK, JobType.MINER);
					} else if (stationLen >= 1) {
						int sz = b + 1 + stationLen;
						d.set(a + 1, lo + 2, sz, States.lantern(lantern, true));
						d.poi(PoiType.WORK_FACE, a, lo, sz, Dirs.BACK, JobType.MINER);
					} else {
						d.poi(PoiType.WORK_FACE, a, lo, b + 1, Dirs.RIGHT, JobType.MINER);
					}
				}
			}
		}
		// the chain from the windlass down to the kibble over the bottom
		try (var st = d.stage(StageKind.EXTERIOR)) {
			for (int y = 0; y > bottom + 1; y--) {
				int part = 1;
				for (int k = 0; k < n; k++) {
					if (y <= liftTop[k] && y >= liftBottom[k]) {
						part = k + 1;
					}
				}
				try (var ps = d.campaign(part)) {
					d.set(a + 1, y, b, States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
				}
			}
			try (var ps = d.campaign(n)) {
				d.set(a + 1, bottom + 1, b, Blocks.BARREL.defaultBlockState());
			}
		}

		// the spoil round the house, growing with every lift
		boolean[] tipCells = new boolean[w * dp];
		int count = 0;
		for (int z = yd + 1; z <= dp - 2; z++) {
			for (int x = 1; x <= w - 2; x++) {
				boolean house = x >= a - 2 && x <= a + 3 && z >= b - 2 && z <= b + 3;
				boolean station = x >= a - 1 && x <= a + 2 && z >= b && z <= b + 2 + stationLen;
				if (!house && !station && !d.site().isWater(x, z)) {
					tipCells[z * w + x] = true;
					count++;
				}
			}
		}
		if (count >= 4) {
			boolean back = dp - 2 - (b + 3) >= 2;
			double sx = back ? (w - 1) / 2.0 : a + 4.5 < w - 1 ? Math.min(w - 2, a + 4.5) : 1;
			Spoil tip = back ? new Spoil(d, rock, 1.5, dp - 2.5, w - 2.5, dp - 2.5, 2.2, 0.9, tipCells)
				: new Spoil(d, rock, sx, yd + 1.5, sx, dp - 2.5, 2.2, 0.9, tipCells);
			for (int k = 0; k < n; k++) {
				tip.grow(k + 1, n, k + 1);
			}
		}
		try (var p = d.campaign(0)) {
			head.sign("shaft", "Shaft");
			Groundwork.clear(d, 0, 0, w - 1, yd, Math.min(d.maxHeight() - 1, 10));
		}
	}

	/**
	 * The house's pad levelled to the yard: {@link Groundwork#level} where the land allows, else (a bank rising over the
	 * plot's height behind it) cut down only as high as the plot reaches, made up with earth where it is low.
	 */
	private static void pad(Draft d, int x0, int z0, int x1, int z1) {
		if (d.site().maxGround(x0, z0, x1, z1) < d.maxHeight() - 1) {
			Groundwork.level(d, x0, z0, x1, z1, 0, true);
			return;
		}
		Mat ground = d.mat(Role.GROUND);
		Mat fill = d.mat(Role.FILL);
		try (var st = d.stage(StageKind.EARTHWORK)) {
			for (int z = Math.max(0, z0); z <= Math.min(d.depth() - 1, z1); z++) {
				for (int x = Math.max(0, x0); x <= Math.min(d.frontage() - 1, x1); x++) {
					int g = d.site().isWater(x, z) ? d.site().water(x, z) : d.site().ground(x, z);
					for (int y = Math.min(g, d.maxHeight()) - 1; y >= 0; y--) {
						d.set(x, y, z, Blocks.AIR.defaultBlockState());
					}
					for (int y = Math.min(g, 0) - 1 - (d.site().isWater(x, z) ? d.site().waterDepth(x, z) : 0); y < -1; y++) {
						d.set(x, y, z, fill.full());
					}
					d.set(x, -1, z, ground.full());
					if (d.site().isWater(x, z)) {
						d.drain(x, z);
					}
				}
			}
		}
	}

	/** The stone collar round the shaft's mouth: three courses, the top flush with the yard. */
	private static void collar(Draft d, Rock rock, int a, int b) {
		try (var st = d.stage(StageKind.FOUNDATION)) {
			for (int[] c : Draft.ring(a - 1, b - 1, a + 2, b + 2)) {
				for (int y = -3; y <= -1; y++) {
					double u = Noise.unit(d.seed() ^ 0xC011A, c[0], y, c[1]);
					Block blk = y == -1 ? rock.dressed() : u < 0.65 ? rock.rough() : rock.natural();
					d.set(c[0], y, c[1], blk.defaultBlockState());
				}
			}
		}
	}

	/**
	 * The shaft house: an open timber house over the collar on four corner posts, its roof in the culture's form where
	 * its eaves stay over the plot, else a pent roof.
	 */
	private static void house(Draft d, int a, int b) {
		int w = d.frontage();
		int margin = 1 + Math.max(1, Math.max(d.habits().roof().eaves(), d.habits().roof().gableOverhang()));
		boolean fits = a - 1 >= margin && a + 2 <= w - 1 - margin && b - 1 >= margin && b + 2 <= d.depth() - 1 - margin;
		if (fits) {
			RoofForm form = Hall.pick(d, RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.HIP);
			Shed.open(d, a - 1, b - 1, a + 2, b + 2, 0, 4, form, Direction.Axis.X, 0);
			return;
		}
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		try (var st = d.stage(StageKind.FRAME)) {
			for (int[] c : new int[][]{{a - 1, b - 1}, {a + 2, b - 1}, {a - 1, b + 2}, {a + 2, b + 2}}) {
				for (int y = 0; y < 4; y++) {
					d.set(c[0], y, c[1], frame.log(Direction.Axis.Y));
				}
			}
			for (int[] c : Draft.ring(a - 1, b - 1, a + 2, b + 2)) {
				boolean corner = (c[0] == a - 1 || c[0] == a + 2) && (c[1] == b - 1 || c[1] == b + 2);
				d.set(c[0], 4, c[1], corner ? frame.log(Direction.Axis.Y) : beam.log(c[1] == b - 1 || c[1] == b + 2 ? Direction.Axis.X : Direction.Axis.Z));
			}
		}
		Shed.pent(d, Math.max(0, a - 2), b - 2, Math.min(w - 1, a + 3), b + 3, Dirs.BACK, 5, 0.5, null);
	}

	/**
	 * The windlass across the mouth: an axle log on two trestles at the front of the shaft, a crank (a lever) on its left
	 * end, and a lantern hung from the house's plate.
	 */
	private static void windlass(Draft d, int a, int b) {
		Mat frame = d.mat(Role.FRAME);
		Mat fence = Hall.woodFence(d);
		try (var st = d.stage(StageKind.FRAME)) {
			d.set(a - 1, 0, b, fence.fence());
			d.set(a + 2, 0, b, fence.fence());
			for (int x = a - 1; x <= a + 2; x++) {
				d.set(x, 1, b, frame.log(Direction.Axis.X));
			}
		}
		try (var st = d.stage(StageKind.EXTERIOR)) {
			if (d.isEmpty(a - 2, 1, b)) {
				d.set(a - 2, 1, b, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL).setValue(LeverBlock.FACING, Dirs.LEFT));
			}
			Block lantern = d.mat(Role.LIGHT).mainOr(Form.FULL, Blocks.LANTERN);
			if (d.isSolid(a + 2, 4, b + 1) && d.isEmpty(a + 2, 3, b + 1)) {
				d.set(a + 2, 3, b + 1, States.lantern(lantern, true));
			}
		}
	}
}
