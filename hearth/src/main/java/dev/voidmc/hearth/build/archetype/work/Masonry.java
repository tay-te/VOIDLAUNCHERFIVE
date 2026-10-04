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
import dev.voidmc.hearth.build.kit.Furniture;
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

/**
 * {@code hearth:work/masonry}: the stone yard. A long banker shed along the back, its back and ends of dressed stone,
 * its front open on posts, the masons' stonecutters in a row beneath it; before it a paved yard of work in hand —
 * ashlar stacked on pallets, a column being carved from its base to its capital, a gantry swinging a block on its
 * chain, a heap of spalls — all inside a low dry-stone wall with the gate to the street and the sign on its post.
 *
 * <p>Job sites (MASON): the stonecutters, the column. Storage: the chest of dressed stone.
 *
 * <p>Params: {@code roof}, {@code variant}.
 */
public final class Masonry implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("work/masonry"), ArchetypeCategory.WORKPLACE, Set.of(JobType.MASON), 0,
		8, 12, 8, 12, 8, 13, SettlementTier.VILLAGE, Set.of(), Set.of(), List.of(), 0, 1.0);

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.HIP, RoofForm.PAGODA, RoofForm.SHED, RoofForm.SALTBOX,
		RoofForm.FLAT};

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		RoofForm form = Hall.pick(d, FORMS);
		int gate = w / 2;
		Yard.entrance(d);
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);

		// the banker shed along the back: stone back and ends, open front on posts
		int sz1 = dp - 1;
		int sz0 = dp - (dp >= 11 ? 5 : 4);
		Mat stone = d.mat(Role.PLINTH);
		Mat ashlar = d.mat(Role.WALL_ACCENT).has(Form.FULL) ? d.mat(Role.WALL_ACCENT) : stone;
		Groundwork.footings(d, 0, sz0, w - 1, sz1, -1);
		Yard.pave(d, 1, sz0 + 1, w - 2, sz1 - 1, 0);
		Roofs.Roof roof = Shed.open(d, 0, sz0, w - 1, sz1, 0, 4, form == RoofForm.FLAT ? RoofForm.SHED : form, Direction.Axis.X, 1, stone,
			Dirs.BACK, Dirs.LEFT, Dirs.RIGHT);
		d.carve(1, 0, sz0 + 1, w - 2, 3, sz1 - 1);
		d.body(0, sz0, w - 1, sz1);
		d.eave(4);
		// under it: stonecutters along the back wall, a chest, a rack of chisels
		int cutters = 0;
		boolean chest = false;
		for (int x = 1; x <= w - 2; x++) {
			int z = sz1 - 1;
			if (x == w / 2 - 1 && !chest || !chest && x == w - 2) {
				Props.store(d, x, 0, z, Dirs.FRONT, true, List.of(new Object[]{"minecraft:stone_bricks", 48}, new Object[]{"minecraft:chiseled_stone_bricks", 8},
					new Object[]{"minecraft:smooth_stone", 32}, new Object[]{"minecraft:iron_pickaxe", 1}));
				Props.toolRack(d, x, 2, z, Dirs.FRONT, List.of(new Object[]{"minecraft:iron_pickaxe", 1, 0}, new Object[]{"minecraft:brush", 1, 1},
					new Object[]{"minecraft:iron_shovel", 1, 2}));
				chest = true;
			} else if ((x - 1) % 2 == 0 && cutters < 4) {
				Props.station(d, Blocks.STONECUTTER, x, 0, z, Dirs.FRONT, JobType.MASON);
				cutters++;
			} else if (d.isEmpty(x, 0, z) && x + 1 != w / 2 - 1 && x + 1 <= w - 3) {
				Props.stonePallet(d, x, 0, z, ashlar, d.mat(Role.FLOOR));
				x++;
			}
		}
		// a lantern under the ridge
		int lx = w / 2;
		int lz = (sz0 + sz1) / 2;
		if (roof != null && roof.covers(lx, lz) && d.isEmpty(lx, roof.bottomY(lx, lz) - 1, lz) && roof.bottomY(lx, lz) - 1 >= 3) {
			Props.hangLantern(d, lx, roof.bottomY(lx, lz) - 1, lz);
		}

		// the yard: paving round the work, a column being carved, ashlar on pallets, a gantry, spalls
		yard(d, w, sz0, gate, ashlar, stone);
		// the low wall round it, the gate open to the street
		List<int[]> gaps = new ArrayList<>();
		gaps.add(new int[]{gate, 0});
		gaps.add(new int[]{gate - 1, 0});
		wall(d, w, sz0, gaps);
		Signs.post(d, gate + 1, 0, 0, Dirs.LEFT, 3, "masonry", "Mason");
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, (roof != null ? roof.ridgeY : 6) + 3);
	}

	private static void yard(Draft d, int w, int sz0, int gate, Mat ashlar, Mat stone) {
		Yard.trodden(d, 1, 1, w - 2, sz0 - 1, 0);
		// a path of paving from the gate to the shed
		for (int z = 0; z < sz0; z++) {
			Yard.pave(d, gate - 1, z, gate, z, 0);
		}
		int depth = sz0 - 1;
		boolean colLeft = d.chance(0.5);
		int cx = colLeft ? 2 : w - 3;
		int cz = Math.min(sz0 - 2, 2 + depth / 3);
		// the column: a base block, a shaft of wall posts, a capital, scaffold beside it
		Mat shaft = d.mat(Role.WALL_ACCENT);
		Block capital = Props.block("minecraft:chiseled_stone_bricks", Blocks.STONE_BRICKS);
		int top = Math.min(4, d.maxHeight() - 2);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(cx, 0, cz, ashlar.full());
			for (int y = 1; y < top; y++) {
				d.set(cx, y, cz, shaft.has(Form.WALL) ? shaft.wall() : stone.has(Form.WALL) ? stone.wall() : ashlar.full());
			}
			d.set(cx, top, cz, capital.defaultBlockState());
			int sx = cx + (colLeft ? 1 : -1);
			for (int y = 0; y < 2; y++) {
				d.set(sx, y, cz + 1, Blocks.SCAFFOLDING.defaultBlockState().setValue(net.minecraft.world.level.block.ScaffoldingBlock.DISTANCE, 0)
					.setValue(net.minecraft.world.level.block.ScaffoldingBlock.BOTTOM, false));
			}
		}
		d.poi(PoiType.JOB_SITE, cx, 0, cz - 1, Dirs.BACK, JobType.MASON);
		// ashlar on pallets along the other side
		int px = colLeft ? w - 4 : 1;
		if (depth >= 3) {
			Props.stonePallet(d, px, 0, 1, ashlar, d.mat(Role.FLOOR));
		}
		if (depth >= 5) {
			Props.stonePallet(d, px, 0, 4, stone, d.mat(Role.FLOOR));
		}
		// a gantry over the second pallet, lowering a block
		if (depth >= 5 && w >= 9) {
			int gx0 = px - 1;
			int gx1 = px + 2;
			if (gx0 >= 1 && gx1 <= w - 2 && Math.abs(gx0 - gate) > 1 && Math.abs(gx1 - gate) > 1 && Math.abs(gx0 - (gate - 1)) > 0) {
				Lifts.gantry(d, gx0, 3, gx1, 3, 0, 5, 2, ashlar.full().at(px, 2, 3));
			}
		}
		// spalls in a heap by the wall
		int hx = colLeft ? 1 : w - 2;
		if (depth >= 4) {
			Props.heap(d, hx, 1, hx, 1, 0, new Block[]{Blocks.COBBLESTONE, Blocks.GRAVEL, Blocks.ANDESITE}, Blocks.COBBLESTONE_SLAB);
		}
		// a bench for the masons' break
		int bx = colLeft ? cx + 2 : cx - 2;
		if (bx > 0 && bx < w - 1 && Math.abs(bx - gate) > 1 && d.isAbsent(bx, 0, sz0 - 1)) {
			Furniture.chair(d, bx, 0, sz0 - 1, Dirs.BACK);
		}
	}

	/** A low wall of the foundation stone round the yard, a course and a coping. */
	private static void wall(Draft d, int w, int sz0, List<int[]> gaps) {
		Mat stone = d.mat(Role.FOUNDATION);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] c : Draft.ring(0, 0, w - 1, sz0)) {
				if (c[1] == sz0) {
					continue;
				}
				boolean gap = false;
				for (int[] g : gaps) {
					gap |= g[0] == c[0] && g[1] == c[1];
				}
				if (gap || !d.isAbsent(c[0], 0, c[1])) {
					continue;
				}
				d.set(c[0], 0, c[1], stone.has(Form.WALL) ? stone.wall() : stone.full());
			}
		}
	}
}
