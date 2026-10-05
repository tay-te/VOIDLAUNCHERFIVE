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
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.kit.extraction.Drift;
import dev.voidmc.hearth.build.kit.extraction.Pithead;
import dev.voidmc.hearth.build.kit.extraction.Rock;
import dev.voidmc.hearth.build.kit.extraction.Spoil;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * {@code hearth:extract/mine}: an adit driven into an ore hill (docs/LIVING_WORLD.md §7.3). From the street a small
 * levelled yard — the miners' lean-to with their bench and store, the ore heaped by the gate for the carts to the
 * smelter, a cart, the lamp — and behind it the way in: a cutting into the foot of the slope, lined with dry-stone where
 * it meets the hill, and the portal, a doubled timber frame under a hood with a lantern under its cap. Rails run out of
 * the dark across the yard. Inside, the drift goes straight back into the hill, three wide and timbered every three
 * blocks, lanterns under every other cap, stalls opened off its sides where the ore was followed. On level ground the
 * cutting goes down a block a step until there is land enough over the crown, and the rails climb it. The waste rock
 * the carts bring out is tipped beside the yard, the tip growing as the drift goes in.
 *
 * <p>Parts: 0 the yard and its pieces; then one per segment of the drift (about six rows), the first bringing the
 * cutting and the portal: its cuts slice by slice, its timber sets, rails and lanterns, a stall, the tip's growth, its
 * work faces (the heading, the stall's end).
 *
 * <p>Job sites: MINER at the bench in the lean-to, at every heading and stall (WORK_FACE). Storage: the lean-to's chest
 * and the ore heap. Params (§8.5): {@code length} (how far the adit runs from the yard; default the plot's depth),
 * {@code rock} (the host rock: wing walls, treads, spoil), {@code site}, {@code kind}, {@code face} (recorded; the
 * drift runs back from the street), {@code speciality} (an ore's block id for the heap, if the survey named one).
 */
public final class Mine implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("extract/mine"), ArchetypeCategory.EXTRACTION, Set.of(JobType.MINER), 0, 7, 11,
		16, 48, 6, 12, SettlementTier.HAMLET, Set.of(), Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS, ArchetypeInfo.SiteFlag.HILLSIDE), List.of(), 0, 1.2);

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
		int c = Math.clamp(ex, 2, w - 3);
		int yd = Pithead.yardDepth(d, ex, dp >= 24 ? 5 : 4);
		int[] base;
		try (var p = d.part(0)) {
			base = Pithead.levelYard(d, yd, ex);
		}
		int length = Math.max(8, d.param("length", dp));
		// where the yard could not be levelled in front of the drift (a street at the foot of a bank), the cutting starts at the street
		boolean yardOk = true;
		for (int z = 0; z <= yd; z++) {
			for (int x = c - 1; x <= c + 1; x++) {
				yardOk &= base[z * w + x] == 0;
			}
		}
		int z0 = yardOk ? yd + 1 : 0;
		Drift drift = new Drift(d, rock, c, z0, Math.min(dp - 2, z0 + length - 1), -12, 1, w - 2);

		Pithead head = new Pithead(d, rock, ex, yd, base);
		Block ore = ore(d, rock);
		try (var p = d.part(0)) {
			head.way(c);
			head.reserve(c - 1, yd, c + 1, yd);
			if (!yardOk) {
				head.keep(c - 1, 0, c + 1, yd);
			}
			if (yardOk) {
				head.shed(JobType.MINER);
			}
			head.lamp();
			if (yardOk) {
				// (where the cutting runs from the street through the yard, the yard's pieces would be cut off from the gate)
				oreHeap(d, head, rock, ore, yd);
				head.cart(ore.defaultBlockState());
			}
		}
		boolean shedLeft = head.shed != null && head.shed[0] < c;
		boolean tipLeft = head.shed == null ? d.chance(0.5) : !shedLeft;

		// the drift, segment by segment, stalls opened to alternate sides
		List<int[]> segs = merged(d, drift, drift.segments(6));
		int side = d.chance(0.5) ? 1 : -1;
		int part = 1;
		int[] parts = new int[segs.size()];
		for (int k = 0; k < segs.size(); k++) {
			int[] s = segs.get(k);
			drift.drive(part, s[0], s[1], k == 0 && drift.portal >= s[1] ? 0 : side, JobType.MINER);
			parts[k] = part++;
			side = -side;
		}

		// on land falling away from the street the drift never meets the hill: the miners sink a trial pit at its end
		if (!segs.isEmpty() && !cuts(d, drift)) {
			trialPit(d, drift, parts[parts.length - 1], rock);
		}

		// the waste tip beside the yard, tipped out from the rim toward the street
		boolean[] tipCells = new boolean[w * dp];
		int count = 0;
		int tz1 = Math.min(dp - 2, Math.min(drift.portal, drift.end) + 3);
		for (int z = 1; z <= tz1; z++) {
			for (int x = 1; x <= w - 2; x++) {
				boolean onSide = tipLeft ? x <= c - 2 : x >= c + 2;
				if (!onSide) {
					continue;
				}
				boolean ok = z <= yd ? head.free(x, z) && z >= 2 : Math.abs(x - c) >= 3;
				if (ok) {
					tipCells[z * w + x] = true;
					count++;
				}
			}
		}
		if (count >= 4 && !segs.isEmpty()) {
			double sx = tipLeft ? Math.max(1, (c - 2 + 1) / 2.0) : Math.min(w - 2, (c + 2 + w - 2) / 2.0);
			Spoil tip = new Spoil(d, rock, sx, yd + 1.5, sx, 1.5, 3.4, 0.85, tipCells);
			for (int k = 0; k < parts.length; k++) {
				tip.grow(k + 1, parts.length, parts[k]);
			}
		}
		try (var p = d.part(0)) {
			head.sign("mine", "Mine");
			Groundwork.clear(d, 0, 0, w - 1, yd, Math.min(d.maxHeight() - 1, 10));
		}
	}

	/**
	 * The segments with every one cutting the land: a stretch only embanked (the drift carried over low ground on a fill)
	 * goes in with the next segment, or the last with the one before.
	 */
	static List<int[]> merged(Draft d, Drift drift, List<int[]> segs) {
		List<int[]> out = new java.util.ArrayList<>();
		int[] carry = null;
		for (int[] s : segs) {
			int[] cur = carry == null ? s.clone() : new int[]{carry[0], s[1]};
			carry = null;
			boolean cuts = false;
			for (int z = cur[0]; z <= cur[1] && !cuts; z++) {
				cuts = d.site().maxGround(drift.c - 1, z, drift.c + 1, z) > drift.level(z);
			}
			if (cuts) {
				out.add(cur);
			} else {
				carry = cur;
			}
		}
		if (carry != null) {
			if (out.isEmpty()) {
				out.add(carry);
			} else {
				int[] last = out.remove(out.size() - 1);
				out.add(new int[]{last[0], carry[1]});
			}
		}
		return out;
	}

	/** Whether the drift cuts the land anywhere. */
	static boolean cuts(Draft d, Drift drift) {
		for (int z = drift.z0; z <= drift.end; z++) {
			if (d.site().maxGround(drift.c - 1, z, drift.c + 1, z) > drift.level(z)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A trial pit sunk at the drift's end: three across and three deep, a ladder down its back wall, a lantern on the
	 * floor beside it, the work face at its bottom.
	 */
	static void trialPit(Draft d, Drift drift, int part, Rock rock) {
		int z = drift.end;
		int l = drift.level(z);
		int c = drift.c;
		int bottom = Math.min(l - 3, d.site().minGround(c - 1, z, c + 1, z) - 2);
		try (var ps = d.part(part)) {
			try (var st = d.stage(StageKind.EARTHWORK)) {
				for (int y = l - 1; y >= bottom; y--) {
					try (var o = d.order(10_000 - y)) {
						for (int x = c - 1; x <= c + 1; x++) {
							d.set(x, y, z, Blocks.AIR.defaultBlockState());
						}
					}
				}
				// the rail's end and the treads over the pit's rim are taken up
				d.set(c, l, z, Blocks.AIR.defaultBlockState());
			}
			try (var st = d.stage(StageKind.EXTERIOR)) {
				for (int y = bottom; y <= l - 1; y++) {
					d.set(c, y, z, Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Dirs.FRONT));
				}
				dev.voidmc.hearth.build.kit.light.Lighting.lamp(d, c - 1, bottom, z);
				if (z - 1 >= drift.z0 && d.isEmpty(c - 1, drift.level(z - 1), z - 1)) {
					dev.voidmc.hearth.build.kit.light.Lighting.lamp(d, c - 1, drift.level(z - 1), z - 1);
				}
			}
			d.poi(dev.voidmc.hearth.api.PoiType.WORK_FACE, c + 1, bottom, z, Dirs.BACK, JobType.MINER);
		}
	}

	/** The ore the heap shows: the survey's speciality if it is an ore, else iron (copper and coal by seed), in the rock's kind. */
	static Block ore(Draft d, Rock rock) {
		String sp = d.param("speciality", "");
		if (sp.endsWith("_ore")) {
			Block b = dev.voidmc.hearth.build.draft.States.block(sp, Blocks.IRON_ORE);
			if (b != Blocks.AIR) {
				return b;
			}
		}
		boolean deep = rock.natural() == Blocks.DEEPSLATE;
		double u = Noise.unit(d.seed(), 9, 4, 1);
		if (u < 0.6) {
			return deep ? Blocks.DEEPSLATE_IRON_ORE : Blocks.IRON_ORE;
		}
		if (u < 0.85) {
			return deep ? Blocks.DEEPSLATE_COPPER_ORE : Blocks.COPPER_ORE;
		}
		return deep ? Blocks.DEEPSLATE_COAL_ORE : Blocks.COAL_ORE;
	}

	/** The ore heaped in the yard for the carts: a low pile of ore and broken rock, registered as the store. */
	static void oreHeap(Draft d, Pithead head, Rock rock, Block ore, int yd) {
		int w = d.frontage();
		for (int z = Math.max(2, yd - 1); z >= 2; z--) {
			for (int x = 1; x + 1 <= w - 2; x++) {
				if (!head.free(x, z) || !head.free(x + 1, z) || !head.free(x, z - 1) && !head.free(x + 1, z - 1)) {
					continue;
				}
				Props.heap(d, x, z, x + 1, z, 0, new Block[]{ore, rock.rough(), ore, Blocks.GRAVEL}, rock.slab());
				head.reserve(x, z, x + 1, z);
				if (z - 1 >= 1 && head.free(x, z - 1)) {
					head.store(x, z - 1, Dirs.BACK);
					head.reserve(x, z - 1, x, z - 1);
				}
				return;
			}
		}
	}
}
