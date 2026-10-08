package dev.voidmc.hearth.build.archetype.extract;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.extraction.Benches;
import dev.voidmc.hearth.build.kit.extraction.Pithead;
import dev.voidmc.hearth.build.kit.extraction.Rock;
import dev.voidmc.hearth.build.kit.extraction.Spoil;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:extract/clay_pit} and {@code hearth:extract/sand_pit}: a bank of clay or sand dug in shallow benches
 * (docs/LIVING_WORLD.md §7.3). Smaller and softer than a quarry: the lifts are two blocks deep, the faces stand back a
 * step a bench, and the diggers go down by short flights of boards pegged into the bank. The turf and topsoil stripped
 * off the clay go on a low tip along one side. At the head of the pit a levelled yard with the diggers' lean-to, a
 * barrow-cart and heaps of what the pit gives: for clay a trough where the clay is wetted and trodden and its loaves
 * stacked under a slab to sour; for sand a riddle on its trestle (the screen the sand is thrown through) and the sieved
 * sand heaped by the gate.
 *
 * <p>Parts: 0 the pit head; then one per lift, top down (cuts, the treads of the flight, the tip's growth, work faces).
 *
 * <p>Job sites: CLAY_DIGGER at the bench in the lean-to and at the foot of every lift's face (WORK_FACE). Storage: the
 * lean-to's chest. Params: {@code depth} (the planner's 3–4; the pit takes at least two lifts), {@code site},
 * {@code kind}, {@code face}, {@code rock} (recorded; the pit's material is its kind's).
 */
public final class SoftPit implements Archetype {
	public static final ArchetypeInfo CLAY = new ArchetypeInfo(Hearth.id("extract/clay_pit"), ArchetypeCategory.EXTRACTION, Set.of(JobType.CLAY_DIGGER), 0,
		9, 17, 9, 17, 6, 9, SettlementTier.CAMP, Set.of(), Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 0, 1.0);
	public static final ArchetypeInfo SAND = new ArchetypeInfo(Hearth.id("extract/sand_pit"), ArchetypeCategory.EXTRACTION, Set.of(JobType.CLAY_DIGGER), 0,
		9, 17, 9, 17, 6, 9, SettlementTier.CAMP, Set.of(), Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 0, 0.8);

	private final ArchetypeInfo info;
	private final boolean clay;

	private SoftPit(ArchetypeInfo info, boolean clay) {
		this.info = info;
		this.clay = clay;
	}

	public static SoftPit clay() {
		return new SoftPit(CLAY, true);
	}

	public static SoftPit sand() {
		return new SoftPit(SAND, false);
	}

	@Override
	public ArchetypeInfo info() {
		return this.info;
	}

	/** The pit's material, its treads boards of the culture's timber pegged into the bank. */
	private Rock rock(Draft d) {
		Rock base = Rock.named(this.clay ? "minecraft:clay" : redSand(d) ? "minecraft:red_sand" : "minecraft:sand");
		Block tread = d.mat(Role.BEAM).mainOr(Form.STAIRS, Blocks.SPRUCE_STAIRS);
		return new Rock(base.natural(), base.rough(), base.dressed(), tread, base.slab(), base.spoil(), base.kind());
	}

	private static boolean redSand(Draft d) {
		return d.style().culture() == dev.voidmc.hearth.api.Culture.MESA || d.param("rock", "").contains("red");
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Rock rock = rock(d);
		int ex = Yard.entrance(d);
		int yd = Pithead.yardDepth(d, ex, Math.clamp(dp / 5, 2, 3));
		int[] base;
		try (var p = d.campaign(0)) {
			base = Pithead.levelYard(d, yd, ex);
		}
		// the tip of topsoil along one side (a narrow plot keeps it to the back corner)
		boolean tipLeft = d.chance(0.5);
		int tipW = w >= 13 ? 3 : w >= 11 ? 2 : 0;
		int lx0 = tipLeft && tipW > 0 ? tipW + 1 : 1;
		int lx1 = tipLeft || tipW == 0 ? w - 2 : w - 2 - tipW;
		int[] limit = {lx0, yd + 1, lx1, dp - 2};
		// dug spit by spit (a spade's depth a lift) back toward the working face: the faces stand straight, the floor
		// steps down a spit a row from the side the diggers come in by
		int faceSide = QuarryPit.faceSide(d, limit, Benches.BACK);
		int open = faceSide == Benches.FRONT ? Benches.BACK : Benches.FRONT;
		int[] inset = {0, 0, 0, 0};
		inset[open] = QuarryPit.edgeMean(d, limit, open) <= -2 ? 0 : 1;
		if (inset[open] == 0) {
			inset[(faceSide + 1) % 4] = 1;
		}
		int h = 1;
		int[] rim = QuarryPit.pullBack(d, limit, inset, h);
		boolean[] keep = new boolean[w * dp];
		for (int z = 0; z < dp; z++) {
			for (int x = 0; x < w; x++) {
				keep[z * w + x] = z <= yd || x < lx0 || x > lx1 || z > limit[3];
			}
		}
		try (var p = d.campaign(0)) {
			Pithead.stairDown(d, base, keep, yd, Math.clamp(ex, lx0 + 1, lx1 - 1), lx0, lx1);
		}
		// a spit or three below the land: where the land falls away behind the yard the pit goes down from it, not the street
		int depth = Math.clamp(d.param("depth", d.between(3, 4)), 2, 5) + Math.max(0, -d.site().minGround(limit[0], limit[1], limit[2], limit[3]));
		Benches pit = new Benches(d, rim, h, inset, depth, limit, keep, new int[]{ex, 0, 0}, base, yd);
		pit.debris = false;
		pit.ladders = false;

		Pithead head = new Pithead(d, rock, ex, yd, base);
		try (var p = d.campaign(0)) {
			head.path(pit);
			head.shed(JobType.CLAY_DIGGER);
			if (this.clay) {
				trough(d, head, yd);
			} else {
				riddle(d, head, yd);
			}
			head.stacks(false);
			head.cart(rock.natural().defaultBlockState());
			head.lamp();
			head.fence(pit, tipLeft ? 0 : w - 1);
		}

		int[] parts = pit.cut(1, rock, JobType.CLAY_DIGGER);
		int n = 0;
		for (int p : parts) {
			if (p >= 0) {
				n++;
			}
		}
		// the topsoil tip along the side, a low bank (turf and earth, the odd stone)
		boolean[] tipCells = new boolean[w * dp];
		int tx0 = tipLeft ? 1 : w - 1 - tipW;
		int tx1 = tipLeft ? tipW : w - 2;
		for (int z = yd + 1; z <= dp - 2; z++) {
			for (int x = tx0; x <= tx1; x++) {
				tipCells[z * w + x] = true;
			}
		}
		double spineX = (tx0 + tx1) / 2.0;
		Spoil tip = tipW == 0 ? null : new Spoil(d, rock, spineX, dp - 2.5, spineX, yd + 2.5, Math.min(tipW * 0.75 + 0.6, 2.4), 1.1, tipCells);
		int k = 0;
		for (int p : parts) {
			if (p >= 0) {
				k++;
				if (tip != null) {
					tip.grow(k, n, p);
				}
			}
		}
		// a pit on land falling away may be reached at one cell only: the diggers stand wherever the walk reaches its cuts
		if (d.pois().stream().noneMatch(q -> q.type() == dev.voidmc.hearth.api.PoiType.WORK_FACE)) {
			outer:
			for (int z = 0; z < dp; z++) {
				for (int x = 0; x < w; x++) {
					if (pit.cut(x, z) && pit.reached(x, z) && d.isEmpty(x, pit.surface(x, z), z) && d.isEmpty(x, pit.surface(x, z) + 1, z)) {
						d.poi(dev.voidmc.hearth.api.PoiType.WORK_FACE, x, pit.surface(x, z), z, Dirs.BACK, JobType.CLAY_DIGGER);
						break outer;
					}
				}
			}
		}
		long faces = d.pois().stream().filter(q -> q.type() == dev.voidmc.hearth.api.PoiType.WORK_FACE).count();
		if (faces < Math.min(2, n) && head.head >= 0 && base[yd * w + head.head] == 0) {
			// a pit below land falling away from the yard: the diggers work its bank from the rim and the way to it
			d.poi(dev.voidmc.hearth.api.PoiType.WORK_FACE, head.head, 0, yd, Dirs.BACK, JobType.CLAY_DIGGER);
			if (faces == 0 && yd - 1 >= 1 && base[(yd - 1) * w + head.head] == 0) {
				d.poi(dev.voidmc.hearth.api.PoiType.WORK_FACE, head.head, 0, yd - 1, Dirs.BACK, JobType.CLAY_DIGGER);
			}
		}
		try (var p = d.campaign(0)) {
			rails(d, pit);
			head.sign(this.clay ? "clay_pit" : "sand_pit", this.clay ? "Clay" : "Sand");
			Groundwork.clear(d, 0, 0, w - 1, yd, Math.min(d.maxHeight() - 1, 8));
		}
	}

	/**
	 * A rail round the pit's rim (L6 item 4: the yard and bank faces): wherever someone standing on the land beside the
	 * pit (the yard, the bank, the tip, the top of a hedge) has the pit four or more below the next step, a post and
	 * rail of the culture's timber; a hedge along the rim gets its rail in the hedge line (a hedge one high is a step up,
	 * not a guard). The benches inside are the diggers' work and stay open.
	 */
	private static void rails(Draft d, Benches pit) {
		int w = d.frontage();
		int dp = d.depth();
		dev.voidmc.hearth.build.draft.Mat rail = dev.voidmc.hearth.build.kit.trade.Hall.woodFence(d);
		java.util.List<int[]> at = new java.util.ArrayList<>();
		for (int z = 0; z < dp; z++) {
			for (int x = 0; x < w; x++) {
				if (pit.cut(x, z)) {
					continue;
				}
				for (int y = -6; y <= 4; y++) {
					if (!open(d, x, y, z) || !open(d, x, y + 1, z) || open(d, x, y - 1, z)) {
						continue;
					}
					for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
						int nx = x + o[0];
						int nz = z + o[1];
						if (nx >= 0 && nz >= 0 && nx < w && nz < dp && pit.cut(nx, nz) && fall(d, nx, y, nz) >= 4) {
							at.add(new int[]{x, y, z});
							break;
						}
					}
				}
			}
		}
		// each rail only where the diggers still walk from the gate to every place they work with it standing (a pit
		// on falling land is reached along its bank)
		java.util.Set<Long> solid = new java.util.HashSet<>();
		java.util.List<int[]> fences = new java.util.ArrayList<>();
		int need = reached(d, solid);
		for (int[] c : at) {
			net.minecraft.world.level.block.state.BlockState under = d.get(c[0], c[1] - 1, c[2]);
			boolean hedge = under != null && under.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock;
			int[] f = hedge ? new int[]{c[0], c[1] - 1, c[2]} : c;
			if (!hedge && !d.allowed(c[0], c[1], c[2])) {
				continue;
			}
			long k = net.minecraft.core.BlockPos.asLong(f[0], f[1], f[2]);
			solid.add(k);
			if (reached(d, solid) < need) {
				solid.remove(k);
			} else {
				fences.add(f);
			}
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] f : fences) {
				d.set(f[0], f[1], f[2], rail.fence());
			}
		}
	}

	/** How many of the pit's places of work (its POIs) the diggers reach on foot from the gate, with {@code solid} railed. */
	private static int reached(Draft d, java.util.Set<Long> solid) {
		int w = d.frontage();
		int dp = d.depth();
		java.util.Set<Long> seen = new java.util.HashSet<>();
		java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
		for (int[] e : d.entrances()) {
			for (int dy = 1; dy >= -3; dy--) {
				if (stand(d, solid, e[0], e[1] + dy, e[2])) {
					if (seen.add(net.minecraft.core.BlockPos.asLong(e[0], e[1] + dy, e[2]))) {
						q.add(new int[]{e[0], e[1] + dy, e[2]});
					}
					break;
				}
			}
		}
		while (!q.isEmpty()) {
			int[] c = q.poll();
			for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				int nx = c[0] + o[0];
				int nz = c[2] + o[1];
				if (nx < 0 || nx >= w || nz < -1 || nz >= dp) {
					continue;
				}
				for (int dy = 1; dy >= -3; dy--) {
					int ny = c[1] + dy;
					if (dy == 1 && !pass(d, solid, c[0], c[1] + 2, c[2])) {
						continue;
					}
					if (dy < 0 && (!pass(d, solid, nx, c[1], nz) || !pass(d, solid, nx, c[1] + 1, nz))) {
						break;
					}
					if (stand(d, solid, nx, ny, nz)) {
						if (seen.add(net.minecraft.core.BlockPos.asLong(nx, ny, nz))) {
							q.add(new int[]{nx, ny, nz});
						}
						break;
					}
				}
			}
		}
		int n = 0;
		for (var p : d.pois()) {
			for (int dy = -1; dy <= 1; dy++) {
				if (seen.contains(net.minecraft.core.BlockPos.asLong(p.x(), p.y() + dy, p.z()))) {
					n++;
					break;
				}
			}
		}
		return n;
	}

	private static boolean pass(Draft d, java.util.Set<Long> solid, int x, int y, int z) {
		return !solid.contains(net.minecraft.core.BlockPos.asLong(x, y, z)) && open(d, x, y, z);
	}

	/** Someone can stand here: open with head room, on something that is not a rail (a fence is not stepped onto). */
	private static boolean stand(Draft d, java.util.Set<Long> solid, int x, int y, int z) {
		if (!pass(d, solid, x, y, z) || !pass(d, solid, x, y + 1, z) || open(d, x, y - 1, z) || solid.contains(net.minecraft.core.BlockPos.asLong(x, y - 1, z))) {
			return false;
		}
		net.minecraft.world.level.block.state.BlockState under = d.get(x, y - 1, z);
		return under == null || !(under.getBlock() instanceof net.minecraft.world.level.block.FenceBlock
			|| under.getBlock() instanceof net.minecraft.world.level.block.WallBlock);
	}

	/** Nothing built here that stops a person (or only what one walks through), above the natural ground. */
	private static boolean open(Draft d, int x, int y, int z) {
		net.minecraft.world.level.block.state.BlockState s = d.get(x, y, z);
		return s == null ? y >= d.site().ground(x, z)
			: s.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO).isEmpty();
	}

	/** How far someone stepping into (x, z) at level y falls (0 where it is not open). */
	private static int fall(Draft d, int x, int y, int z) {
		if (!open(d, x, y, z) || !open(d, x, y + 1, z)) {
			return 0;
		}
		int n = 0;
		for (int yy = y - 1; yy > y - 12 && open(d, x, yy, z); yy--) {
			n++;
		}
		return n;
	}

	/**
	 * The clay trough: a long box of planks with water in it, where the dug clay is wetted and trodden to a paste, and
	 * beside it the loaves of clay stacked to sour under a slab. In the yard's back row, along it.
	 */
	private static void trough(Draft d, Pithead head, int yd) {
		int w = d.frontage();
		int z = Math.max(1, yd - 1);
		for (int x = 1; x + 2 < w - 1; x++) {
			if (!head.free(x, z) || !head.free(x + 1, z) || !head.free(x + 2, z) || !head.free(x, z - 1) && !head.free(x + 1, z - 1)) {
				continue;
			}
			Props.trough(d, x, 0, z, Dirs.RIGHT, 2);
			try (var s = d.stage(StageKind.EXTERIOR)) {
				d.set(x + 2, 0, z, Blocks.CLAY.defaultBlockState());
				if (Noise.unit(d.seed() ^ 0xC1A, x, 1, z) < 0.6) {
					d.set(x + 2, 1, z, States.slab(Blocks.MUD_BRICK_SLAB, SlabType.BOTTOM));
				}
			}
			return;
		}
	}

	/**
	 * The riddle: a screen of slatted boards (a trapdoor) leaning on a trestle of two posts, the sand thrown through it
	 * heaped below. In the yard's back row.
	 */
	private static void riddle(Draft d, Pithead head, int yd) {
		int w = d.frontage();
		int z = Math.max(1, yd - 1);
		for (int x = 1; x + 1 < w - 1; x++) {
			if (!head.free(x, z) || !head.free(x + 1, z) || !head.free(x, z - 1) || !head.free(x + 1, z - 1)) {
				continue;
			}
			var post = dev.voidmc.hearth.build.kit.trade.Hall.woodFence(d);
			Block sand = d.style().culture() == dev.voidmc.hearth.api.Culture.MESA ? Blocks.RED_SAND : Blocks.SAND;
			try (var s = d.stage(StageKind.EXTERIOR)) {
				d.set(x, 0, z, post.fence());
				d.set(x, 1, z, d.mat(Role.BEAM).trapdoor(Direction.EAST, net.minecraft.world.level.block.state.properties.Half.TOP, false));
				d.set(x + 1, 0, z, sand.defaultBlockState());
			}
			return;
		}
	}
}
