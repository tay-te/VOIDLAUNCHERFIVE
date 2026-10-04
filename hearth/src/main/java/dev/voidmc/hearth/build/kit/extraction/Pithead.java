package dev.voidmc.hearth.build.kit.extraction;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.kit.trade.Shed;
import dev.voidmc.hearth.build.kit.trade.Signs;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * The head of an open working: the levelled yard between the street and the pit (rows 0 to {@code rim}, the last of them
 * the rim the rig stands on), and what stands on it — the way in from the gate to the head of the flight down, the
 * lifting rig at the rim, the diggers' open shed with their bench, their tools and their store, stacks of what the pit
 * gives waiting for the carts, a cart, the lamp at the gate and its sign, and the fence along the rim and round the plot.
 * Each piece takes cells of the yard and leaves the way in clear; a piece that finds no room is left out.
 */
public final class Pithead {
	private final Draft d;
	private final Rock rock;
	private final int w;
	/** The gate's column on the street row. */
	public final int gate;
	/** The rim row (the yard is rows 0 to rim). */
	public final int rim;
	private final boolean[] taken;
	/** Cells kept clear (the way in, where folk stand at the benches): the fence leaves them open. */
	private final boolean[] clear;
	/** The column where the way in reaches the rim (the head of the flight down), or -1. */
	public int head = -1;
	/** The shed's rectangle {x0, z0, x1, z1}, or null. */
	public int @Nullable [] shed;
	private Rigs.@Nullable Hook hook;
	private int rigX = -1;

	public Pithead(Draft d, Rock rock, int gate, int rim, int[] base) {
		this.d = d;
		this.rock = rock;
		this.w = d.frontage();
		this.gate = gate;
		this.rim = rim;
		this.taken = new boolean[this.w * (rim + 1)];
		this.clear = new boolean[this.w * (rim + 1)];
		// the yard is where it was levelled; ground left as it lies takes nothing
		for (int z = 0; z <= rim; z++) {
			for (int x = 0; x < this.w; x++) {
				if (base[z * this.w + x] == Benches.NONE) {
					this.taken[z * this.w + x] = true;
				}
			}
		}
	}

	/**
	 * Levels the yard (rows 0 to {@code rim}) to the street, over the run of columns round the gate whose ground lies
	 * within four blocks of the street (on a steep side-slope the rest is left as it lies, the yard a terrace cut into
	 * it with a retaining wall). Returns each column's standing level (w × dp; NONE where the ground is left).
	 */
	public static int[] levelYard(Draft d, int rim, int gate) {
		int w = d.frontage();
		int[] base = new int[w * d.depth()];
		java.util.Arrays.fill(base, Benches.NONE);
		boolean[] ok = new boolean[w];
		for (int x = 0; x < w; x++) {
			ok[x] = yardColumn(d, x, rim);
		}
		int xa = gate;
		int xb = gate;
		if (!ok[gate]) {
			// the street runs at the foot of a bank: no yard (the way in starts on the ground as it lies)
			return base;
		}
		while (xa - 1 >= 0 && ok[xa - 1]) {
			xa--;
		}
		while (xb + 1 < w && ok[xb + 1]) {
			xb++;
		}
		Groundwork.level(d, xa, 0, xb, rim, 0, true);
		for (int z = 0; z <= rim; z++) {
			for (int x = xa; x <= xb; x++) {
				base[z * w + x] = 0;
			}
		}
		return base;
	}

	private boolean inYard(int x, int z) {
		return x >= 0 && x < this.w && z >= 0 && z <= this.rim;
	}

	/** A yard cell nothing has taken and nothing stands on. */
	public boolean free(int x, int z) {
		return inYard(x, z) && !this.taken[z * this.w + x] && this.d.isEmpty(x, 0, z) && this.d.isEmpty(x, 1, z);
	}

	private void take(int x, int z) {
		if (inYard(x, z)) {
			this.taken[z * this.w + x] = true;
		}
	}

	private void keepClear(int x, int z) {
		take(x, z);
		if (inYard(x, z)) {
			this.clear[z * this.w + x] = true;
		}
	}

	private boolean freeRect(int x0, int z0, int x1, int z1) {
		for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
			for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
				if (!free(x, z)) {
					return false;
				}
			}
		}
		return true;
	}

	private void takeRect(int x0, int z0, int x1, int z1) {
		for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
			for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
				take(x, z);
			}
		}
	}

	/**
	 * The way in: from the gate back to the rim at the head of the flight down into the pit (the rim cell beside its
	 * first step), kept clear; the whole yard trodden. Call first.
	 */
	public void path(Benches pit) {
		int hx = this.gate;
		for (Benches.Flight f : pit.flights) {
			if (f.from() < 0) {
				int[] c = f.cells()[0];
				if (c[1] == this.rim + 1) {
					hx = c[0];
					break;
				}
			}
		}
		this.head = hx;
		// in from the gate, along the front of the yard, then straight back to every opening of the rim (the head of the
		// flight down, where the ground runs on or a stair goes down)
		int along = Math.min(1, this.rim);
		for (int z = 0; z <= along; z++) {
			keepClear(this.gate, z);
		}
		// the lane along the front of the yard, from side to side: everything opens onto it
		for (int x = 1; x < this.w - 1; x++) {
			keepClear(x, along);
		}
		List<Integer> heads = new ArrayList<>();
		heads.add(hx);
		for (int x = 0; x < this.w; x++) {
			if (pit.rimOpen(x) && !heads.contains(x)) {
				heads.add(x);
			}
		}
		for (int h : heads) {
			for (int x = Math.min(this.gate, h); x <= Math.max(this.gate, h); x++) {
				keepClear(x, along);
			}
			for (int z = along; z <= this.rim; z++) {
				keepClear(h, z);
			}
		}
		// the gate's sides stay open for the lamp and the sign
		take(this.gate - 1, 0);
		take(this.gate + 1, 0);
		Yard.trodden(this.d, 0, 0, this.w - 1, this.rim, 0);
	}

	/**
	 * The lifting rig at the rim, reaching back over the pit where its rope can come down clear to a bench: tried along
	 * the rim from the middle out; a narrow yard gets the smaller rigs. Returns its hook, or null.
	 */
	public Rigs.@Nullable Hook rig(Benches pit, Rigs.Kind preferred) {
		List<Rigs.Kind> kinds = new ArrayList<>(List.of(preferred));
		for (Rigs.Kind k : Rigs.Kind.values()) {
			if (!kinds.contains(k)) {
				kinds.add(k);
			}
		}
		int mid = this.w / 2;
		for (Rigs.Kind kind : kinds) {
			for (int k = 0; k < this.w; k++) {
				int x = mid + ((k % 2 == 0) ? k / 2 : -(k + 1) / 2);
				for (int side : new int[]{1, -1}) {
					if (kind != Rigs.Kind.TREADWHEEL && side < 0) {
						continue;
					}
					int[] fp = Rigs.footprint(kind, side);
					// the footprint in the yard: a from fp[0] to fp[1] (toward the pit is +z), b to the right (+x)
					int x0 = x + fp[2];
					int x1 = x + fp[3];
					int z0 = this.rim + fp[0];
					int z1 = this.rim + fp[1];
					if (z0 < 1 || x0 < 0 || x1 >= this.w || !freeRect(x0, z0, x1, z1)) {
						continue;
					}
					// the jib reaches over cut ground, its rope clear of the flights, the land under it lower than the jib
					int len = 0;
					int jibY = this.d.maxHeight() - 3;
					for (int l = 5; l >= 2; l--) {
						if (!pit.cut(x, this.rim + l) || !pit.cut(x, this.rim + 1) || onFlight(pit, x, this.rim + l)) {
							continue;
						}
						boolean clear = true;
						for (int a = 1; a <= l; a++) {
							clear &= this.d.site().ground(x, this.rim + a) < Math.min(jibY, 5);
						}
						if (clear) {
							len = l;
							break;
						}
					}
					if (len == 0) {
						continue;
					}
					Rigs.Hook hk = Rigs.raise(this.d, kind, x, 0, this.rim, Dirs.BACK, len, side, (gx, gz) -> inYard(gx, gz) && !this.taken[gz * this.w + gx],
						this.rock.natural().defaultBlockState());
					if (hk == null) {
						continue;
					}
					takeRect(x0, z0, x1, z1);
					this.hook = hk;
					this.rigX = x;
					return hk;
				}
			}
		}
		return null;
	}

	private static boolean onFlight(Benches pit, int x, int z) {
		for (Benches.Flight f : pit.flights) {
			for (int[] c : f.cells()) {
				if (c[0] == x && c[1] == z) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * The diggers' open shed on the side of the yard away from the rig: posts and a roof, closed at the back and the
	 * outer end, the trade's bench (a stonecutter, a crafting table), a grindstone for the tools, the store, a lantern
	 * under the roof. Registers the job site and the store.
	 */
	public void shed(JobType job) {
		// the eaves must stay over the plot (a flared roof reaches two out)
		int margin = 1 + Math.max(1, Math.max(this.d.habits().roof().eaves(), this.d.habits().roof().gableOverhang()));
		int z1 = this.rim - 1;
		int z0 = Math.max(margin, z1 - 3);
		int depth = z1 - z0 + 1;
		if (depth < 3) {
			workbench(job);
			return;
		}
		boolean left = this.rigX < 0 ? this.gate > this.w / 2 : this.rigX > this.gate;
		int width = Math.clamp(this.w / 4, depth >= 4 ? 4 : 5, 6);
		int[] rect = null;
		for (int off = 0; off < this.w && rect == null; off++) {
			int x0 = left ? margin + off : this.w - 1 - margin - width + 1 - off;
			int x1 = x0 + width - 1;
			if (x0 < margin || x1 > this.w - 1 - margin) {
				break;
			}
			if (freeRect(x0, z0, x1, z1)) {
				rect = new int[]{x0, z0, x1, z1};
			}
		}
		if (rect == null) {
			workbench(job);
			return;
		}
		this.shed = rect;
		takeRect(rect[0], rect[1], rect[2], rect[3]);
		// the way from the lane to the shed's open front
		for (int z = 2; z < rect[1]; z++) {
			keepClear((rect[0] + rect[2]) / 2, z);
		}
		RoofForm form = Hall.pick(this.d, RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.SHED, RoofForm.HIP);
		Direction outer = left ? Dirs.LEFT : Dirs.RIGHT;
		Roofs.Roof roof = Shed.open(this.d, rect[0], rect[1], rect[2], rect[3], 0, 3, form, Direction.Axis.X, 1, Dirs.BACK, outer);
		Mat floor = this.d.mat(Role.FLOOR);
		try (var s = this.d.stage(StageKind.FLOORS)) {
			for (int z = rect[1] + 1; z <= rect[3] - 1; z++) {
				for (int x = rect[0] + 1; x <= rect[2] - 1; x++) {
					this.d.set(x, -1, z, this.rock.dressed().defaultBlockState());
				}
			}
		}
		this.d.carve(rect[0] + 1, 0, rect[1] + 1, rect[2] - 1, 2, rect[3] - 1);
		// the trade's bench, the store and the grindstone: against the back wall facing out where the shed is deep
		// enough to stand before them, else at the ends of its one row facing along it
		int bz = rect[3] - 1;
		int inner = left ? rect[2] - 1 : rect[0] + 1;
		int outerX = left ? rect[0] + 1 : rect[2] - 1;
		boolean deep = depth >= 4;
		Block bench = job == JobType.QUARRIER || job == JobType.MASON ? Blocks.STONECUTTER : Blocks.CRAFTING_TABLE;
		List<Object[]> stock = List.of(new Object[]{"minecraft:iron_pickaxe", 2}, new Object[]{"minecraft:iron_shovel", 1},
			new Object[]{net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(this.rock.rough()).toString(), 48}, new Object[]{"minecraft:bread", 4});
		Direction inward = left ? Dirs.RIGHT : Dirs.LEFT;
		Props.station(this.d, bench, inner, 0, bz, deep ? Dirs.FRONT : inward.getOpposite(), job);
		Props.store(this.d, outerX, 0, bz, deep ? Dirs.FRONT : inward, true, stock);
		if (deep && rect[2] - rect[0] >= 4) {
			int gx = (rect[0] + rect[2]) / 2;
			if (this.d.isEmpty(gx, 0, bz)) {
				Props.grindstone(this.d, gx, 0, bz, Dirs.FRONT, job);
			}
		}
		// a lantern hung under the roof
		int lx = (rect[0] + rect[2]) / 2;
		int lz = (rect[1] + rect[3]) / 2;
		if (roof != null && roof.covers(lx, lz) && roof.bottomY(lx, lz) >= 4 && this.d.isEmpty(lx, 3, lz)) {
			Lighting.chandelier(this.d, lx, lz, roof.bottomY(lx, lz), 3);
		}
	}

	/** No room for a shed: the bench and the store in the open by the rim. */
	private void workbench(JobType job) {
		int z = this.rim - 1;
		if (z < 2) {
			return;
		}
		for (int x = 1; x < this.w - 2; x++) {
			if (freeRect(x, z, x + 1, z) && free(x, z - 1) && free(x + 1, z - 1)) {
				Block bench = job == JobType.QUARRIER || job == JobType.MASON ? Blocks.STONECUTTER : Blocks.CRAFTING_TABLE;
				Props.station(this.d, bench, x, 0, z, Dirs.FRONT, job);
				Props.store(this.d, x + 1, 0, z, Dirs.FRONT, false, List.<Object[]>of(new Object[]{"minecraft:iron_shovel", 2}));
				takeRect(x, z, x + 1, z);
				keepClear(x, z - 1);
				keepClear(x + 1, z - 1);
				return;
			}
		}
	}

	/**
	 * Stacks of what the pit gives, waiting for the carts beside the rig's landing: blanks of the rock on pallets, a
	 * stack of dressed blocks where {@code dressed}, else heaps (clay, sand) under a slab.
	 */
	public void stacks(boolean dressed) {
		int placed = 0;
		Mat pallet = this.d.mat(Role.FLOOR);
		int[] order = new int[this.w];
		int c = this.rigX >= 0 ? this.rigX : this.gate;
		for (int k = 0; k < this.w; k++) {
			order[k] = c + ((k % 2 == 0) ? k / 2 : -(k + 1) / 2);
		}
		for (int z = this.rim - 1; z >= 1 && placed < 2; z--) {
			for (int x : order) {
				if (placed >= 2) {
					break;
				}
				if (!freeRect(x, z, x + 1, z + 0) || !free(x, z - 1) && !free(x + 1, z - 1) || x + 1 >= this.w - 1 || x < 1) {
					continue;
				}
				try (var s = this.d.stage(StageKind.EXTERIOR)) {
					for (int dx = 0; dx < 2; dx++) {
						this.d.set(x + dx, 0, z, pallet.slab(SlabType.BOTTOM));
						Block b = dressed && placed == 1 ? this.rock.dressed() : this.rock.natural();
						if (Noise.unit(this.d.seed() ^ 0x57AC, x + dx, 1, z) < 0.85) {
							this.d.set(x + dx, 1, z, b.defaultBlockState());
							if (dx == 0) {
								this.d.set(x + dx, 2, z, States.slab(this.rock.slab(), SlabType.BOTTOM));
							}
						}
					}
				}
				takeRect(x, z, x + 1, z);
				placed++;
				if (placed == 1 && dressed && free(x + 2, z) && free(x + 2, z - 1) && x + 2 < this.w - 1) {
					// the banker beside the stack: a block on a log trestle, being dressed
					try (var s = this.d.stage(StageKind.EXTERIOR)) {
						this.d.set(x + 2, 0, z, this.d.mat(Role.FRAME).log(Direction.Axis.X));
						this.d.set(x + 2, 1, z, this.rock.natural().defaultBlockState());
					}
					take(x + 2, z);
				}
			}
		}
	}

	/** A cart loaded with what the pit gives, by the gate. */
	public void cart(BlockState load) {
		for (int z = 2; z <= this.rim - 2; z++) {
			for (int k = 2; k < this.w; k++) {
				for (int x : new int[]{this.gate + k, this.gate - k}) {
					// three across for the axle row, the shaft toward the gate's side
					// the shaft rests on the lane (a half block, stepped over)
					if (x - 1 < 1 || x + 1 > this.w - 2 || !freeRect(x - 1, z, x + 1, z) || z - 1 < 1 || !this.d.isEmpty(x, 0, z - 1)) {
						continue;
					}
					Props.cart(this.d, x, 0, z, Dirs.FRONT, load);
					takeRect(x - 1, z, x + 1, z);
					return;
				}
			}
		}
	}

	/** The culture's lamp at the gate (its light reaches the street side of the entrance). */
	public void lamp() {
		// the nearest spot by the gate whose lamp (two above its foot) still lights the street cell before the gate to 8
		int bestX = 0;
		int bestY = 0;
		int bestZ = 0;
		int best = Integer.MAX_VALUE;
		for (int z = 0; z <= Math.min(2, this.rim); z++) {
			for (int x = this.gate - 4; x <= this.gate + 4; x++) {
				if (!inYard(x, z) || this.clear[z * this.w + x]) {
					continue;
				}
				int y = standing(x, z);
				if (y == Integer.MIN_VALUE || !this.d.isEmpty(x, y, z) || !this.d.isEmpty(x, y + 1, z) || !this.d.isEmpty(x, y + 2, z)) {
					continue;
				}
				int dist = Math.abs(x - this.gate) + z + 1 + Math.abs(y + 2 - Yard.street(this.d, this.gate));
				int score = dist * 4 + (z == 0 ? 0 : 1) + Math.abs(y);
				if (dist <= 6 && score < best) {
					best = score;
					bestX = x;
					bestY = y;
					bestZ = z;
				}
			}
		}
		if (best != Integer.MAX_VALUE) {
			Lighting.post(this.d, bestX, bestY, bestZ);
			take(bestX, bestZ);
		}
	}

	/** Where a post can stand on a yard cell: its standing level over solid ground, or MIN_VALUE. */
	private int standing(int x, int z) {
		if (!inYard(x, z) || this.d.site().isWater(x, z) && !this.d.drained(x, z)) {
			return Integer.MIN_VALUE;
		}
		int y = Groundwork.surface(this.d, x, z);
		return dev.voidmc.hearth.build.kit.Garden.groundLike(this.d, x, y - 1, z) ? y : Integer.MIN_VALUE;
	}

	/** A sign on a post by the gate (the trade's name). */
	public void sign(String key, String fallback) {
		for (int[] o : new int[][]{{2, 0}, {-2, 0}, {3, 0}, {-3, 0}}) {
			int x = this.gate + o[0];
			int y = standing(x, 0);
			if (y != Integer.MIN_VALUE && !this.clear[x] && Math.abs(y) <= 1 && this.d.isEmpty(x, y, 0) && this.d.isEmpty(x, y + 1, 0)
				&& this.d.isEmpty(x, y + 2, 0)) {
				Signs.post(this.d, x, y, 0, o[0] > 0 ? Dirs.LEFT : Dirs.RIGHT, 2, key, fallback);
				take(x, 0);
				return;
			}
		}
	}

	/**
	 * The fence: along the rim between the yard and the drop (open at the head of the flight and where the rig stands),
	 * and round the plot, open at the gate.
	 */
	public void fence(Benches pit, int skipX) {
		Mat rail = Hall.woodFence(this.d);
		try (var s = this.d.stage(StageKind.EXTERIOR)) {
			for (int x = 1; x < this.w - 1; x++) {
				if (pit.rimOpen(x) || this.clear[this.rim * this.w + x] || !free(x, this.rim)) {
					continue;
				}
				this.d.set(x, 0, this.rim, rail.fence());
			}
		}
		List<int[]> line = new ArrayList<>();
		for (int[] c : Draft.ring(0, 0, this.w - 1, this.d.depth() - 1)) {
			boolean open = c[1] == 0 && Math.abs(c[0] - this.gate) <= 1 || inYard(c[0], c[1]) && this.clear[c[1] * this.w + c[0]];
			if (!open && Groundwork.surface(this.d, c[0], c[1]) < this.d.maxHeight() - 2) {
				line.add(c);
			}
		}
		dev.voidmc.hearth.build.kit.Garden.fence(this.d, line, -1, Dirs.FRONT);
	}

	/**
	 * Where the land behind the rim lies more than a step below the yard all along (a pit on falling ground), a stair of
	 * the culture's paving on stone footings goes down from the rim at column {@code x} to the ground below; its cells
	 * are kept from the cutting and given their standing level in {@code base}. Returns whether it was built.
	 */
	public static boolean stairDown(Draft d, int[] base, boolean[] keep, int rim, int x, int x0, int x1) {
		int w = d.frontage();
		if (rim + 1 >= d.depth() || base[rim * w + x] == Benches.NONE || d.site().maxGround(x0, rim + 1, x1, rim + 1) >= -1) {
			return false;
		}
		Mat stone = d.mat(Role.FOUNDATION);
		Mat paving = d.mat(Role.PAVING);
		for (int z = rim + 1, s = 0; z < d.depth() - 1; z++, s--) {
			int g = d.site().ground(x, z);
			if (g >= s || d.site().isWater(x, z)) {
				break;
			}
			try (var st = d.stage(StageKind.FOUNDATION)) {
				for (int y = g - 1; y < s - 1; y++) {
					d.set(x, y, z, stone.full());
				}
			}
			try (var st = d.stage(StageKind.EXTERIOR)) {
				d.set(x, s - 1, z, paving.has(dev.voidmc.hearth.build.style.Form.STAIRS) ? paving.stairs(Dirs.FRONT, Half.BOTTOM)
					: stone.stairsOrSlab(Dirs.FRONT, Half.BOTTOM));
			}
			base[z * w + x] = s;
			keep[z * w + x] = true;
		}
		return true;
	}

	/** Whether a column of the yard can be levelled to the street (its ground within four of it, its neighbours under the plot's height). */
	private static boolean yardColumn(Draft d, int x, int rim) {
		for (int z = 0; z <= rim + 1; z++) {
			// water is embanked and filled (a shallow edge only)
			boolean wet = d.site().isWater(x, z);
			int g = wet ? d.site().water(x, z) - 1 : d.site().ground(x, z);
			if (wet && d.site().waterDepth(x, z) > 4) {
				return false;
			}
			if (Math.abs(g) > 4 || g >= d.maxHeight() - 2 || d.site().ground(x - 1, z) >= d.maxHeight() - 1 || d.site().ground(x + 1, z) >= d.maxHeight() - 1) {
				return false;
			}
		}
		return true;
	}

	/** How deep a yard the gate's column allows (at most {@code want} rows behind the street row, at least 2). */
	public static int yardDepth(Draft d, int gate, int want) {
		for (int rim = want; rim > 2; rim--) {
			if (yardColumn(d, gate, rim)) {
				return rim;
			}
		}
		return 2;
	}

	/** The fence (wall, hedge) round the plot, open at the gate, following the ground where it stands under the plot's height. */
	public static void enclose(Draft d, int gate) {
		List<int[]> line = new ArrayList<>();
		for (int[] c : Draft.ring(0, 0, d.frontage() - 1, d.depth() - 1)) {
			if (c[1] == 0 && Math.abs(c[0] - gate) <= 1) {
				continue;
			}
			if (Groundwork.surface(d, c[0], c[1]) >= d.maxHeight() - 2) {
				continue;
			}
			line.add(c);
		}
		dev.voidmc.hearth.build.kit.Garden.fence(d, line, -1, Dirs.FRONT);
	}

	/** Steps cut of the rock (a tread facing {@code up}) at (x, y, z). */
	public BlockState tread(Direction up) {
		return States.stairs(this.rock.stairs(), up, Half.BOTTOM);
	}

	/** The rig's hook, if one stands. */
	public Rigs.@Nullable Hook hook() {
		return this.hook;
	}

	/** Registers a store at a cell (the stacks, a heap). */
	public void store(int x, int z, Direction toward) {
		this.d.poi(PoiType.STORAGE, x, 0, z, toward);
	}
}
