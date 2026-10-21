package dev.voidmc.hearth.build.archetype.extract;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.blueprint.LocalFrame;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.extraction.Rock;
import dev.voidmc.hearth.build.kit.extraction.Spoil;
import dev.voidmc.hearth.build.kit.industry.Huts;
import dev.voidmc.hearth.build.kit.industry.Pois;
import dev.voidmc.hearth.build.kit.industry.Works;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Shed;
import dev.voidmc.hearth.build.kit.trade.Signs;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;

/**
 * {@code hearth:extract/great_pithead}: the Great Mine reopened (docs/LIVING_WORLD_2.md §6.1–6.2). Expanse's great mines
 * are old workings — a shaft five blocks square sunk two hundred blocks and more from a mountain's shoulder, a level off
 * it every thirty or so — left abandoned; where a town's land reaches one, this stands on the shaft instead of the ruin
 * Expanse would have left. On a levelled pithead yard: the deck of planks round the shaft's mouth, a stone collar and a
 * railing round the mouth open to the old ladder on its north wall, and over it the headframe — four great posts braced
 * twice, the sheave beam under the roofed head, the winding chain hanging down the shaft. At one side of the deck
 * the winch house, the windlass inside where the winchman stands; the miners' huts at the front corners of the yard (they
 * sleep there through the week), barrels of ore by the track, the spoil heap at the back.
 *
 * <p>Parts: 0 the yard, deck, headframe, winch house and huts; then one per old level the shaft passes (the param
 * {@code levels}, floors relative to this floor, highest first): a new gallery cut from the level's station room out
 * along the vein, its lantern, its work face at the end (MINER). The shaft itself is Expanse's and is never touched below
 * its mouth; without levels (anywhere but on a great mine) the pithead is the surface works alone.
 *
 * <p>Job sites (MINER): the winchman's place at the windlass, the shaft's mouth by the ladder, and the gallery faces
 * (WORK_FACE). Beds: one in each hut.
 */
public final class GreatPithead implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("extract/great_pithead"), ArchetypeCategory.EXTRACTION, Set.of(JobType.MINER), 2,
		25, 25, 25, 25, 18, 22, SettlementTier.VILLAGE, Set.of(), Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 1, 1.0);

	/** The shaft's half-width (open five blocks square), the deck's half-width (fifteen square). */
	static final int SHAFT = 2;
	static final int DECK = 7;
	/** How far out from the shaft's centre a new gallery runs (to the claimed square's edge), and its width and height. */
	static final int GALLERY_FROM = 6;
	static final int GALLERY_TO = 11;
	static final int GALLERY_SIDE = 4;

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int cx = w / 2;
		int cz = dp / 2;
		Rock rock = Rock.of(d);
		Pois pois = new Pois();
		Direction north = north(d, cx, cz);
		Mat floor = d.mat(Role.FLOOR);
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat fence = Hall.woodFence(d);
		Block lantern = d.mat(Role.LIGHT).mainOr(Form.FULL, Blocks.LANTERN);
		int top = Math.min(d.maxHeight() - 4, 16);

		try (var part = d.campaign(0)) {
			// the pithead yard, levelled round the shaft (never into it: the mouth stays open)
			Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true, (x, z) -> ch(x - cx, z - cz) > SHAFT);
			try (var st = d.stage(StageKind.EARTHWORK)) {
				for (int x = cx - SHAFT; x <= cx + SHAFT; x++) {
					for (int z = cz - SHAFT; z <= cz + SHAFT; z++) {
						for (int y = -1; y >= -3; y--) {
							d.set(x, y, z, Blocks.AIR.defaultBlockState());
						}
						for (int y = 0; y <= 3; y++) {
							d.set(x, y, z, Blocks.AIR.defaultBlockState());
						}
					}
				}
			}
			// the deck round the mouth, the collar ring of dressed stone at its lip
			try (var st = d.stage(StageKind.FLOORS)) {
				for (int x = cx - DECK; x <= cx + DECK; x++) {
					for (int z = cz - DECK; z <= cz + DECK; z++) {
						int c = ch(x - cx, z - cz);
						if (c > SHAFT + 1) {
							d.set(x, -1, z, floor.full());
						}
					}
				}
			}
			try (var st = d.stage(StageKind.FOUNDATION)) {
				for (int[] c : Draft.ring(cx - SHAFT - 1, cz - SHAFT - 1, cx + SHAFT + 1, cz + SHAFT + 1)) {
					d.set(c[0], -1, c[1], rock.dressed().defaultBlockState());
				}
			}
			// the railing round the mouth, open where the ladder comes up
			try (var st = d.stage(StageKind.EXTERIOR)) {
				for (int[] c : Draft.ring(cx - SHAFT - 1, cz - SHAFT - 1, cx + SHAFT + 1, cz + SHAFT + 1)) {
					int dx = c[0] - cx;
					int dz = c[1] - cz;
					boolean ladderSide = dx * Dirs.dx(north) + dz * Dirs.dz(north) == SHAFT + 1;
					boolean corner = Math.abs(dx) == SHAFT + 1 && Math.abs(dz) == SHAFT + 1;
					if (corner || !ladderSide) {
						if (!corner || !headframePost(dx, dz)) {
							d.set(c[0], 0, c[1], fence.fence());
						}
					}
				}
			}
			headframe(d, cx, cz, top, frame, beam, lantern);
			winch(d, pois, cx, cz, north, frame, fence);
			// the shaft's mouth: where the miners go down (the ladder's top, on the deck behind its opening)
			int lx = cx + Dirs.dx(north) * (SHAFT + 2);
			int lz = cz + Dirs.dz(north) * (SHAFT + 2);
			pois.siteIf(d, lx, 0, lz, north.getOpposite(), JobType.MINER);
			// the deck's railing, open on the front where the track comes up
			try (var st = d.stage(StageKind.EXTERIOR)) {
				for (int[] c : Draft.ring(cx - DECK, cz - DECK, cx + DECK, cz + DECK)) {
					boolean front = c[1] == cz - DECK && Math.abs(c[0] - cx) <= 1;
					if (!front && d.isEmpty(c[0], 0, c[1])) {
						d.set(c[0], 0, c[1], fence.fence());
					}
				}
			}
			// the miners' huts at the yard's front corners, their doors on the yard
			Huts.Kind kind = Huts.kind(d);
			for (int side : new int[]{-1, 1}) {
				int hx = side < 0 ? 2 : w - 3;
				int hz = 3;
				if (kind == Huts.Kind.CELL) {
					Huts.cell(d, hx - 2, hz - 2, hx + 2, hz + 2, hx);
				} else if (kind == Huts.Kind.ROUND_MUD) {
					Huts.mud(d, hx, hz, 2.0, side < 0 ? Dirs.RIGHT : Dirs.LEFT);
				} else {
					Huts.cone(d, hx, hz, side < 0 ? Dirs.RIGHT : Dirs.LEFT, d.chance(0.3));
				}
			}
			// barrels of ore by the track, a lamp at the deck's head and by the huts, the sign
			int bx = cx + 3;
			pois.store(d, bx, 0, 1, Dirs.FRONT, false, Works.items("minecraft:raw_iron", 24, "minecraft:coal", 16));
			pois.store(d, bx + 1, 0, 1, Dirs.FRONT, false, Works.items("minecraft:raw_copper", 24));
			Works.lampPost(d, cx - 3, 0, cz - DECK - 1);
			Works.lampPost(d, cx + 3, 0, cz - DECK - 1);
			Works.lampPost(d, cx - DECK, 0, cz + DECK);
			Works.lampPost(d, cx + DECK, 0, cz + DECK);
			Works.lampPost(d, cx, 0, dp - 2);
			int sx = cx - 3;
			if (d.isEmpty(sx, 0, 0) && d.isEmpty(sx, 1, 0)) {
				Signs.post(d, sx, 0, 0, Dirs.LEFT, 3, "great_mine", "Great Mine");
			}
			d.entrance(cx, 0, 0);
		}

		// the spoil of the new galleries, heaped at the back of the yard behind the deck
		int[] levels = levels(d);
		boolean[] tip = new boolean[w * dp];
		int cells = 0;
		for (int z = cz + DECK + 2; z <= dp - 2; z++) {
			for (int x = 1; x <= w - 2; x++) {
				if (Math.abs(x - cx) > 2 && !d.site().isWater(x, z)) {
					tip[z * w + x] = true;
					cells++;
				}
			}
		}
		if (cells >= 6) {
			Spoil heap = new Spoil(d, rock, 2.5, dp - 2.5, w - 3.5, dp - 2.5, 2.0, 0.9, tip);
			heap.grow(1, Math.max(1, levels.length + 1), 0);
			for (int k = 0; k < levels.length; k++) {
				heap.grow(k + 2, levels.length + 1, k + 1);
			}
		}

		// a gallery off each old level the shaft passes
		for (int k = 0; k < levels.length; k++) {
			gallery(d, k + 1, cx, cz, levels[k], Dirs.HORIZONTAL[Math.floorMod(k + (int) (d.seed() & 3), 4)], lantern);
		}

		try (var part = d.campaign(0)) {
			Works.keepDoors(d);
			pois.commit(d, INFO.jobs().toArray(JobType[]::new), new int[]{cx + Dirs.dx(north) * (SHAFT + 2), 0, cz + Dirs.dz(north) * (SHAFT + 2)},
				new int[]{cx, 0, cz - DECK + 1});
			Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, top + 3));
		}
	}

	/** Chebyshev distance from the shaft's centre. */
	static int ch(int dx, int dz) {
		return Math.max(Math.abs(dx), Math.abs(dz));
	}

	/** The headframe's posts stand at the corners three out from the shaft's centre. */
	private static boolean headframePost(int dx, int dz) {
		return Math.abs(dx) == SHAFT + 1 && Math.abs(dz) == SHAFT + 1;
	}

	/**
	 * Which local direction is the world's north (the side of the shaft its old ladder climbs): the plot is drawn as seen
	 * from the street, and the ladder stays where Expanse put it.
	 */
	static Direction north(Draft d, int cx, int cz) {
		LocalFrame f = d.frame();
		int wx = f.worldX(cx, cz);
		int wz = f.worldZ(cx, cz);
		for (Direction dir : Dirs.HORIZONTAL) {
			int x = cx + Dirs.dx(dir);
			int z = cz + Dirs.dz(dir);
			if (f.worldX(x, z) == wx && f.worldZ(x, z) == wz - 1) {
				return dir;
			}
		}
		return Dirs.BACK;
	}

	/** The levels' floors relative to this floor (below it), from the plot's params; none on ordinary ground. */
	static int[] levels(Draft d) {
		if (!d.hasParam("levels")) {
			return new int[0];
		}
		int[] raw = d.params().getIntArray("levels").orElse(new int[0]);
		int n = 0;
		int[] out = new int[raw.length];
		for (int v : raw) {
			// a level close under the deck, or above it, is no level to open a gallery from
			if (v <= -8) {
				out[n++] = v;
			}
		}
		return java.util.Arrays.copyOf(out, Math.min(n, 6));
	}

	/**
	 * The headframe over the shaft: four posts at its corners from the deck up, braced at mid-height and at the head, the
	 * sheave beam across the top under a little roof, the winding chain from the sheave down into the mouth.
	 */
	private static void headframe(Draft d, int cx, int cz, int top, Mat frame, Mat beam, Block lantern) {
		int r = SHAFT + 1;
		int mid = Math.max(4, top / 2);
		try (var st = d.stage(StageKind.FRAME)) {
			for (int sx : new int[]{-r, r}) {
				for (int sz : new int[]{-r, r}) {
					for (int y = 0; y <= top; y++) {
						d.set(cx + sx, y, cz + sz, frame.log(Direction.Axis.Y));
					}
				}
			}
			for (int[] c : Draft.ring(cx - r, cz - r, cx + r, cz + r)) {
				boolean corner = Math.abs(c[0] - cx) == r && Math.abs(c[1] - cz) == r;
				if (corner) {
					continue;
				}
				Direction.Axis axis = Math.abs(c[1] - cz) == r ? Direction.Axis.X : Direction.Axis.Z;
				d.set(c[0], mid, c[1], beam.log(axis));
				d.set(c[0], top, c[1], beam.log(axis));
			}
			// the sheave beam across the head
			for (int x = cx - r + 1; x <= cx + r - 1; x++) {
				d.set(x, top + 1, cz, beam.log(Direction.Axis.X));
			}
		}
		try (var st = d.stage(StageKind.ROOF)) {
			// the head roofed over its whole width, so the frame below reads as an open frame, not a roof with holes
			Mat roof = d.mat(Role.ROOF);
			for (int x = cx - r; x <= cx + r; x++) {
				for (int z = cz - r; z <= cz + r; z++) {
					d.set(x, top + 2, z, roof.slab(net.minecraft.world.level.block.state.properties.SlabType.BOTTOM));
				}
			}
		}
		try (var st = d.stage(StageKind.EXTERIOR)) {
			for (int y = top; y >= 1; y--) {
				d.set(cx, y, cz, States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
			}
			// lanterns hung under the braces, lighting the deck by night
			for (int sx : new int[]{-1, 1}) {
				d.set(cx + sx * r, mid - 1, cz, States.lantern(lantern, true));
			}
		}
	}

	/**
	 * The winch house at the deck's side away from the ladder: an open shed on posts, the windlass inside (an axle on two
	 * trestles, a crank at its end), the winchman's place beside it.
	 */
	private static void winch(Draft d, Pois pois, int cx, int cz, Direction north, Mat frame, Mat fence) {
		// across the shaft from the ladder, or to its side when that is the front (the track comes up there)
		Direction side = north.getOpposite();
		if (side == Dirs.FRONT) {
			side = Dirs.LEFT;
		}
		int ox = cx + Dirs.dx(side) * 5;
		int oz = cz + Dirs.dz(side) * 5;
		Direction along = Dirs.right(side);
		// three deep (toward the shaft and away from it) and five along
		int ex = along.getAxis() == Direction.Axis.X ? 2 : 1;
		int ez = along.getAxis() == Direction.Axis.Z ? 2 : 1;
		int ax0 = ox - ex;
		int ax1 = ox + ex;
		int az0 = oz - ez;
		int az1 = oz + ez;
		RoofForm form = Hall.pick(d, RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.HIP);
		Shed.open(d, ax0, az0, ax1, az1, 0, 3, form, along.getAxis(), 0);
		// the windlass along the shed's outer row: two trestles and the axle across them, a crank on its end
		int rx = ox + Dirs.dx(side);
		int rz = oz + Dirs.dz(side);
		try (var st = d.stage(StageKind.FRAME)) {
			d.set(rx - Dirs.dx(along), 0, rz - Dirs.dz(along), fence.fence());
			d.set(rx + Dirs.dx(along), 0, rz + Dirs.dz(along), fence.fence());
			for (int k = -1; k <= 1; k++) {
				d.set(rx + Dirs.dx(along) * k, 1, rz + Dirs.dz(along) * k, frame.log(along.getAxis()));
			}
		}
		try (var st = d.stage(StageKind.EXTERIOR)) {
			int kx = rx - Dirs.dx(side);
			int kz = rz - Dirs.dz(side);
			// the crank on the axle's shaft-side face, a hand's height up
			if (d.isEmpty(kx + Dirs.dx(along), 1, kz + Dirs.dz(along))) {
				d.set(kx + Dirs.dx(along), 1, kz + Dirs.dz(along), Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL)
					.setValue(LeverBlock.FACING, side.getOpposite()));
			}
		}
		// the winchman stands in the shed's middle, facing the windlass (the shed is entered from its open ends)
		pois.siteIf(d, ox, 0, oz, side, JobType.MINER);
	}

	/**
	 * A new gallery off an old level: from the station room's edge out to the claimed square's edge, two wide and three
	 * high, beside the old drift on that side (never into it), a lantern hung from its roof halfway, the face at its end.
	 */
	private static void gallery(Draft d, int part, int cx, int cz, int level, Direction run, Block lantern) {
		Direction side = Dirs.right(run);
		try (var ps = d.campaign(part)) {
			try (var st = d.stage(StageKind.EARTHWORK)) {
				for (int t = GALLERY_FROM; t <= GALLERY_TO; t++) {
					try (var o = d.order(t)) {
						for (int s = GALLERY_SIDE; s <= GALLERY_SIDE + 1; s++) {
							int x = cx + Dirs.dx(run) * t + Dirs.dx(side) * s;
							int z = cz + Dirs.dz(run) * t + Dirs.dz(side) * s;
							for (int y = level; y <= level + 2; y++) {
								d.set(x, y, z, Blocks.AIR.defaultBlockState());
							}
						}
					}
				}
			}
			int mt = (GALLERY_FROM + GALLERY_TO) / 2;
			int lx = cx + Dirs.dx(run) * mt + Dirs.dx(side) * GALLERY_SIDE;
			int lz = cz + Dirs.dz(run) * mt + Dirs.dz(side) * GALLERY_SIDE;
			int fx = cx + Dirs.dx(run) * GALLERY_TO + Dirs.dx(side) * GALLERY_SIDE;
			int fz = cz + Dirs.dz(run) * GALLERY_TO + Dirs.dz(side) * GALLERY_SIDE;
			try (var st = d.stage(StageKind.EXTERIOR)) {
				d.set(lx, level + 2, lz, States.lantern(lantern, true));
				d.poi(PoiType.WORK_FACE, fx, level, fz, run, JobType.MINER);
			}
		}
	}
}
