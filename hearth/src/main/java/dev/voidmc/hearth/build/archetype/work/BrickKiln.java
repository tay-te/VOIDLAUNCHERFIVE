package dev.voidmc.hearth.build.archetype.work;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.industry.Clayworks;
import dev.voidmc.hearth.build.kit.industry.Fam;
import dev.voidmc.hearth.build.kit.industry.Heap;
import dev.voidmc.hearth.build.kit.industry.Stack;
import dev.voidmc.hearth.build.kit.industry.Timber;
import dev.voidmc.hearth.build.kit.industry.Pois;
import dev.voidmc.hearth.build.kit.industry.Works;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.kit.trade.Shed;
import dev.voidmc.hearth.build.kit.trade.Signs;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * {@code hearth:work/brick_kiln}: the brickworks. At the back of the yard the kiln: a long vaulted chamber of brick
 * on buttressed walls, set full of green brick for the firing, its firemouths glowing in a row along the front under
 * little hoods of brick, the wicket in its side bricked up in raw mud brick until the firing is done, and its chimney
 * rising from the back of the vault, round-cornered, banded and corbelled, smoking. A pentice on posts shelters the
 * stokers at the firemouths, cordwood stacked by them. Beside the kiln the long open drying shed, its roof on posts,
 * hacks of green brick drying in open lattices under it (in the open, under board caps, where the yard is narrow). By
 * the gate the pug mill, its sweep over the tub of clay and the horse's round trodden about it, a heap of clay from the
 * pit, and stacks of fired brick on pallets waiting for the carts. Earthen cultures build the kiln of mud brick with a
 * brick throat; everyone else of brick.
 *
 * <p>Job sites (KILN_WORKER): before each firemouth (stoking), at the pug mill, at the moulding table, at the hacks.
 * Storage: a chest of brick by the stacks, a barrel in the shed.
 *
 * <p>Params: {@code roof} (the shed's form), {@code variant}.
 */
public final class BrickKiln implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("work/brick_kiln"), ArchetypeCategory.WORKPLACE, Set.of(JobType.KILN_WORKER), 0,
		10, 14, 10, 14, 9, 15, SettlementTier.VILLAGE, Set.of(), Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 0, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		Pois pois = new Pois();
		int w = d.frontage();
		int dp = d.depth();
		int ex = Yard.entrance(d);
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		Yard.trodden(d, 0, 0, w - 1, dp - 1, 0);
		boolean kilnLeft = d.chance(0.5);
		Works.Tradition t = Works.tradition(d);
		boolean earthen = t == Works.Tradition.EARTH || t == Works.Tradition.THATCH;
		Fam body = earthen ? Fam.mudBrick() : Fam.brick();
		Fam seal = earthen ? Fam.brick() : Fam.mudBrick();

		// the kiln at the back on its side: five wide, five or six deep
		int kw = 5;
		int kd = dp >= 12 ? 6 : 5;
		int kx0 = kilnLeft ? 1 : w - 1 - kw;
		int kx1 = kx0 + kw - 1;
		int kz1 = dp - 2;
		int kz0 = kz1 - kd + 1;
		Clayworks.Kiln kiln = Clayworks.kiln(d, kx0, kz0, kx1, kz1, body, seal, kilnLeft ? Dirs.RIGHT : Dirs.LEFT);
		// the chimney from the back of the vault
		int scx = (kx0 + kx1) / 2;
		int scz = kz1 - 1;
		int top = Stack.round(d, scx, scz, 4, d.maxHeight() - 1, Fam.brick(), Fam.brick(), null, false);
		if (top == Integer.MIN_VALUE) {
			top = kiln.top();
		}
		// the stokers' pentice before the firemouths, cordwood by it
		int pz0 = Math.max(1, kz0 - 2);
		Roofs.Roof pent = Shed.pent(d, kx0 - (kilnLeft ? 1 : 0), pz0, kx1 + (kilnLeft ? 0 : 1), kz0 - 1, Dirs.BACK, 3, 0.5, null);
		List<int[]> posts = new ArrayList<>();
		posts.add(new int[]{kx0, pz0});
		posts.add(new int[]{kx1, pz0});
		Shed.posts(d, pent, posts, 0);
		for (int[] m : kiln.mouths()) {
			if (m[1] >= pz0) {
				pois.siteIf(d, m[0], 0, m[1], Dirs.BACK, JobType.KILN_WORKER);
			}
		}
		int cordX = kilnLeft ? kx1 + 1 : kx0 - 1;
		if (Works.in(d, cordX, kz0) && d.isEmpty(cordX, 0, kz0)) {
			Timber.cord(d, cordX, 0, kz0, Dirs.BACK, Math.min(3, kd - 2), 2, Works.logs(d));
		}
		if (pent.covers((kx0 + kx1) / 2, pz0) && pent.bottomY((kx0 + kx1) / 2, pz0) >= 3) {
			Props.hangLantern(d, (kx0 + kx1) / 2, pent.bottomY((kx0 + kx1) / 2, pz0) - 1, pz0);
		}

		// the drying shed along the other side: hacks of green brick in long rows down it, an aisle between (in the open,
		// under board caps, where the yard is too narrow for a shed)
		int sx0 = kilnLeft ? kx1 + 2 : 0;
		int sx1 = kilnLeft ? w - 1 : kx0 - 2;
		int sz0 = Math.max(4, pz0 + 1);
		int sz1 = dp - 1;
		int shedTop = 0;
		int aisle;
		if (sx1 - sx0 >= 3) {
			RoofForm form = Hall.pick(d, RoofForm.GABLE, RoofForm.HIP, RoofForm.HALF_HIP, RoofForm.PAGODA);
			Roofs.Roof shed = Shed.open(d, sx0, sz0, sx1, sz1, 0, 3, form, Direction.Axis.Z, 1);
			shedTop = shed != null ? shed.ridgeY : 4;
			int outer = kilnLeft ? sx1 - 1 : sx0 + 1;
			int inner = kilnLeft ? sx0 + 1 : sx1 - 1;
			aisle = sx1 - sx0 >= 5 ? (sx0 + sx1) / 2 : inner;
			Clayworks.hack(d, outer, 0, sz0 + 1, Dirs.BACK, sz1 - sz0 - 1, false);
			if (inner != aisle) {
				Clayworks.hack(d, inner, 0, sz0 + 2, Dirs.BACK, sz1 - sz0 - 2, false);
			}
			pois.siteIf(d, aisle, 0, (sz0 + sz1) / 2, kilnLeft ? Dirs.RIGHT : Dirs.LEFT, JobType.KILN_WORKER);
			pois.store(d, aisle, 0, sz1 - 1, Dirs.FRONT, false, Works.items("minecraft:clay_ball", 64, "minecraft:brick", 32));
			if (shed != null && shed.covers(aisle, (sz0 + sz1) / 2 + 1) && shed.bottomY(aisle, (sz0 + sz1) / 2 + 1) >= 3) {
				Props.hangLantern(d, aisle, shed.bottomY(aisle, (sz0 + sz1) / 2 + 1) - 1, (sz0 + sz1) / 2 + 1);
			}
			// the moulding table at the head of the outer hack, worked from the aisle
			Props.station(d, Blocks.CRAFTING_TABLE, outer, 0, sz0, kilnLeft ? Dirs.LEFT : Dirs.RIGHT, null);
			if (!pois.siteIf(d, outer + (kilnLeft ? -1 : 1), 0, sz0, kilnLeft ? Dirs.RIGHT : Dirs.LEFT, JobType.KILN_WORKER)) {
				pois.siteIf(d, outer, 0, sz0 - 1, Dirs.BACK, JobType.KILN_WORKER);
			}
		} else {
			int hx = kilnLeft ? w - 1 : 0;
			aisle = hx + (kilnLeft ? -1 : 1);
			Clayworks.hack(d, hx, 0, sz0, Dirs.BACK, sz1 - sz0, true);
			pois.siteIf(d, aisle, 0, sz0 + 1, kilnLeft ? Dirs.RIGHT : Dirs.LEFT, JobType.KILN_WORKER);
		}

		// before the shed: the pug mill and its clay; before the kiln: stacks of fired brick
		int mx = (sx0 + sx1) / 2;
		int mz = 2;
		if (Math.abs(mx - ex) >= 3 && sz0 - mz >= 2) {
			int[] stand = Clayworks.pugMill(d, mx, mz, kilnLeft ? Dirs.LEFT : Dirs.RIGHT, 2);
			pois.siteIf(d, stand[0], 0, stand[1], kilnLeft ? Dirs.LEFT : Dirs.RIGHT, JobType.KILN_WORKER);
			double hx = kilnLeft ? w - 1.2 : 0.2;
			Heap.heap(d, hx, mz + 0.3, 0.9, 1.4, 1.4, 0, Clayworks.clay(), null, 71);
		} else {
			Heap.heap(d, kilnLeft ? w - 2.2 : 1.2, 1.5, 1.3, 1.2, 1.4, 0, Clayworks.clay(), null, 71);
		}
		int bx = kilnLeft ? kx0 : kx1 - 1;
		if (Math.abs(bx - ex) >= 2 && Math.abs(bx + 1 - ex) >= 2 && pz0 - 1 >= 2) {
			Clayworks.stack(d, bx, 0, 1, Dirs.BACK, Math.min(2, pz0 - 2));
			pois.store(d, bx + (kilnLeft ? 2 : -1), 0, 1, kilnLeft ? Dirs.RIGHT : Dirs.LEFT, true, Works.items("minecraft:brick", 64, "minecraft:bricks", 32));
		} else {
			pois.store(d, kilnLeft ? 1 : w - 2, 0, 1, Dirs.BACK, true, Works.items("minecraft:brick", 64, "minecraft:bricks", 32));
		}

		// the yard's fence along the street with its gate, the sign, a lamp
		List<int[]> gaps = new ArrayList<>();
		for (int x = ex - 1; x <= ex + 1; x++) {
			gaps.add(new int[]{x, 0});
		}
		Yard.enclose(d, 0, 0, w - 1, 0, gaps, Dirs.BACK, Dirs.LEFT, Dirs.RIGHT);
		int signX = ex + (kilnLeft ? -2 : 2);
		if (Works.in(d, signX, 0)) {
			Signs.post(d, signX, 0, 0, kilnLeft ? Dirs.RIGHT : Dirs.LEFT, 3, "brick_kiln", "Brickworks");
		}
		Lighting.post(d, ex + (kilnLeft ? 2 : -2), 0, 1);
		pois.commit(d, INFO.jobs().toArray(JobType[]::new), new int[]{w / 2, 0, 1}, new int[]{w / 2, 0, 0});
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.max(top, Math.max(shedTop, kiln.top())) + 1);
	}
}
