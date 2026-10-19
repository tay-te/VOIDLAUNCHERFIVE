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
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.homestead.Adobe;
import dev.voidmc.hearth.build.kit.industry.Clayworks;
import dev.voidmc.hearth.build.kit.industry.Fam;
import dev.voidmc.hearth.build.kit.industry.Heap;
import dev.voidmc.hearth.build.kit.industry.Pois;
import dev.voidmc.hearth.build.kit.industry.Stack;
import dev.voidmc.hearth.build.kit.industry.Timber;
import dev.voidmc.hearth.build.kit.industry.Works;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.kit.trade.Shed;
import dev.voidmc.hearth.build.kit.trade.Signs;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:work/brick_kiln}: the brickworks, built the way each land fires its brick.
 * <ul>
 *   <li><b>chamber</b> (Bohemia, the wisteria vale, the coasts, the bayou): at the back of the yard a long vaulted
 *   chamber of brick on buttressed walls, set full of green brick for the firing, its firemouths glowing in a row along
 *   the front under little hoods of brick over a stoking pit where the firers stand, the wicket in its side bricked up in
 *   raw mud brick until the firing is done, and its round chimney rising from the back of the vault, banded and
 *   corbelled, smoking. A pentice on posts shelters the stokers, cordwood stacked by them. Beside the kiln the long open
 *   drying shed, hacks of green brick drying in open lattices under it;</li>
 *   <li><b>clamp</b> (the north, the moor, the mountains, the tropics): no kiln at all, a season's green brick stacked
 *   into a battered block across the back of the yard and cased in clay, its fire eyes glowing along its foot and the
 *   firing smouldering through its top (under a thatched shade in the tropics), hacks drying in the open under board caps
 *   down both sides, cordwood by the clamp;</li>
 *   <li><b>beehive</b> (the dry lands and the Mediterranean): a domed kiln of mud brick (brick by the sea) at the back
 *   middle, its firemouth under a hood over a stoking pit, a vent smoking at its crown, a reed shade over the working
 *   floor before it, and the green brick laid out in rows in the sun to dry;</li>
 *   <li><b>dragon</b> (the karst): a climbing kiln, a vaulted tunnel of brick on an earth bank stepping up the yard from
 *   the firebox at its foot to the flue at its head, a stoking hole in every step of its side and a walk of beaten earth
 *   climbing beside it, all under one long roof; the drying shed on the other side.</li>
 * </ul>
 * By the gate, where there is room, the pug mill, its sweep over the tub of clay and the horse's round trodden about it,
 * a heap of clay from the pit, and stacks of fired brick on pallets waiting for the carts.
 *
 * <p>Job sites (KILN_WORKER): at each firemouth (in the stoking pit, on the climbing walk), at the pug mill, at the
 * moulding table, at the hacks. Storage: a chest of brick by the stacks, a barrel in the shed.
 *
 * <p>Params: {@code kiln} ({@code chamber}, {@code clamp}, {@code beehive}, {@code dragon}; default by the culture),
 * {@code roof} (the sheds' form), {@code variant}.
 */
public final class BrickKiln implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("work/brick_kiln"), ArchetypeCategory.WORKPLACE, Set.of(JobType.KILN_WORKER), 0,
		10, 14, 10, 14, 9, 15, SettlementTier.VILLAGE, Set.of(), Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 0, 1.0);

	/** How a land fires its brick. */
	private enum Kind {
		CHAMBER, CLAMP, BEEHIVE, DRAGON
	}

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	private static Kind kind(Draft d) {
		String p = d.param("kiln", "");
		if (!p.isEmpty()) {
			try {
				return Kind.valueOf(p.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				// by the culture below
			}
		}
		return switch (Works.culture(d)) {
			case NORTHERN, TUNDRA, ALPINE, MOORLAND, TROPICAL -> Kind.CLAMP;
			case DESERT, MESA, MEDITERRANEAN, STEPPE -> Kind.BEEHIVE;
			case KARST -> Kind.DRAGON;
			default -> Hall.flatCulture(d) ? Kind.BEEHIVE : Kind.CHAMBER;
		};
	}

	@Override
	public void draw(Draft d) {
		Pois pois = new Pois();
		int w = d.frontage();
		int dp = d.depth();
		int ex = Yard.entrance(d);
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		Yard.trodden(d, 0, 0, w - 1, dp - 1, 0);
		boolean left = d.chance(0.5);
		int top = switch (kind(d)) {
			case CHAMBER -> chamber(d, pois, w, dp, ex, left);
			case CLAMP -> clamp(d, pois, w, dp, ex, left);
			case BEEHIVE -> beehive(d, pois, w, dp, ex, left);
			case DRAGON -> dragon(d, pois, w, dp, ex, left);
		};

		// the yard's fence along the street with its gate, the sign, a lamp
		List<int[]> gaps = new ArrayList<>();
		for (int x = ex - 1; x <= ex + 1; x++) {
			gaps.add(new int[]{x, 0});
		}
		Yard.enclose(d, 0, 0, w - 1, 0, gaps, Dirs.BACK, Dirs.LEFT, Dirs.RIGHT);
		int signX = ex + (left ? -2 : 2);
		if (Works.in(d, signX, 0)) {
			Signs.post(d, signX, 0, 0, left ? Dirs.RIGHT : Dirs.LEFT, 3, "brick_kiln", "Brickworks");
		}
		Works.lampPost(d, ex + (left ? 2 : -2), 0, 1);
		Works.keepDoors(d);
		pois.commit(d, INFO.jobs().toArray(JobType[]::new), new int[]{w / 2, 0, 1}, new int[]{w / 2, 0, 0});
		// a rail wherever the levelled yard ends over a drop
		dev.voidmc.hearth.build.kit.craft.Rails.yard(d);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, top + 1);
	}

	/** A firer's place at every mouth of the kiln. */
	private static void mouths(Draft d, Pois pois, Clayworks.Kiln kiln) {
		for (Clayworks.Mouth m : kiln.mouths()) {
			pois.siteIf(d, m.x(), m.y(), m.z(), m.facing(), JobType.KILN_WORKER);
		}
	}

	// ------------------------------------------------------------------------------------------------ the chamber kiln

	private static int chamber(Draft d, Pois pois, int w, int dp, int ex, boolean kilnLeft) {
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
		// the stokers' pentice over the pit, cordwood by it
		int pz0 = Math.max(1, kz0 - 2);
		Roofs.Roof pent = Shed.pent(d, kx0 - (kilnLeft ? 1 : 0), pz0, kx1 + (kilnLeft ? 0 : 1), kz0 - 1, Dirs.BACK, 3, 0.5, null);
		List<int[]> posts = new ArrayList<>();
		posts.add(new int[]{kx0, pz0});
		posts.add(new int[]{kx1, pz0});
		Shed.posts(d, pent, posts, 0);
		mouths(d, pois, kiln);
		int cordX = kilnLeft ? kx1 + 1 : kx0 - 1;
		if (Works.in(d, cordX, kz0) && d.isEmpty(cordX, 0, kz0)) {
			Timber.cord(d, cordX, 0, kz0, Dirs.BACK, Math.min(3, kd - 2), 2, Works.logs(d));
		}
		if (pent.covers((kx0 + kx1) / 2, pz0) && pent.bottomY((kx0 + kx1) / 2, pz0) >= 3) {
			Props.hangLantern(d, (kx0 + kx1) / 2, pent.bottomY((kx0 + kx1) / 2, pz0) - 1, pz0);
		}
		int sx0 = kilnLeft ? kx1 + 2 : 0;
		int sx1 = kilnLeft ? w - 1 : kx0 - 2;
		int shedTop = dryingShed(d, pois, sx0, sx1, Math.max(4, pz0 + 1), dp - 1, kilnLeft);
		front(d, pois, w, ex, (sx0 + sx1) / 2, Math.max(4, pz0 + 1), kilnLeft ? kx0 : kx1 - 1, pz0, kilnLeft);
		return Math.max(top, Math.max(shedTop, kiln.top()));
	}

	/**
	 * The drying shed along one side, x0..x1 from z0 back to z1: hacks of green brick in long rows down it, an aisle
	 * between (in the open, under board caps, where the yard is too narrow for a shed), the moulding table at the head of
	 * the outer hack. {@code kilnLeft}: the kiln stands to the left of the shed. Returns the shed's top.
	 */
	private static int dryingShed(Draft d, Pois pois, int sx0, int sx1, int sz0, int sz1, boolean kilnLeft) {
		int w = d.frontage();
		if (sx1 - sx0 >= 3) {
			RoofForm form = Hall.pick(d, RoofForm.GABLE, RoofForm.HIP, RoofForm.HALF_HIP, RoofForm.PAGODA);
			Roofs.Roof shed = Shed.open(d, sx0, sz0, sx1, sz1, 0, 3, form, Direction.Axis.Z, 1);
			int outer = kilnLeft ? sx1 - 1 : sx0 + 1;
			int inner = kilnLeft ? sx0 + 1 : sx1 - 1;
			int aisle = sx1 - sx0 >= 5 ? (sx0 + sx1) / 2 : inner;
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
			return shed != null ? shed.ridgeY : 4;
		}
		int hx = kilnLeft ? w - 1 : 0;
		Clayworks.hack(d, hx, 0, sz0, Dirs.BACK, sz1 - sz0, true);
		pois.siteIf(d, hx + (kilnLeft ? -1 : 1), 0, sz0 + 1, kilnLeft ? Dirs.RIGHT : Dirs.LEFT, JobType.KILN_WORKER);
		return 3;
	}

	/**
	 * Before the works: the pug mill and its clay before the shed at column mx (where it keeps clear of the gate), and
	 * fired brick stacked on pallets with the chest of brick at column bx, before row limit.
	 */
	private static void front(Draft d, Pois pois, int w, int ex, int mx, int limit, int bx, int stackLimit, boolean left) {
		int mz = 2;
		if (Math.abs(mx - ex) >= 3 && limit - mz >= 2 && mx - 2 >= 0 && mx + 2 < w) {
			int[] stand = Clayworks.pugMill(d, mx, mz, left ? Dirs.LEFT : Dirs.RIGHT, 2);
			pois.siteIf(d, stand[0], 0, stand[1], left ? Dirs.LEFT : Dirs.RIGHT, JobType.KILN_WORKER);
			double hx = left ? w - 1.2 : 0.2;
			Heap.heap(d, hx, mz + 0.3, 0.9, 1.4, 1.4, 0, Clayworks.clay(), null, 71);
		} else {
			Heap.heap(d, left ? w - 2.2 : 1.2, 1.5, 1.3, 1.2, 1.4, 0, Clayworks.clay(), null, 71);
		}
		if (Math.abs(bx - ex) >= 2 && Math.abs(bx + 1 - ex) >= 2 && stackLimit - 1 >= 2) {
			Clayworks.stack(d, bx, 0, 1, Dirs.BACK, Math.min(2, stackLimit - 2));
			pois.store(d, bx + (left ? 2 : -1), 0, 1, left ? Dirs.RIGHT : Dirs.LEFT, true, Works.items("minecraft:brick", 64, "minecraft:bricks", 32));
		} else {
			pois.store(d, left ? 1 : w - 2, 0, 1, Dirs.BACK, true, Works.items("minecraft:brick", 64, "minecraft:bricks", 32));
		}
	}

	// ------------------------------------------------------------------------------------------------ the clamp

	private static int clamp(Draft d, Pois pois, int w, int dp, int ex, boolean left) {
		boolean shade = Works.tradition(d) == Works.Tradition.THATCH || Works.culture(d) == dev.voidmc.hearth.api.Culture.TROPICAL;
		// the clamp across the back of the yard, two rows clear of the back fence for the shade's posts
		int cw = Math.min(w - 4, 8);
		int cd = dp >= 13 ? 5 : 4;
		int cx0 = (w - cw) / 2;
		int cx1 = cx0 + cw - 1;
		int cz1 = dp - 2;
		int cz0 = cz1 - cd + 1;
		int h = Math.min(4, d.maxHeight() - 4);
		Clayworks.Kiln kiln = Clayworks.clamp(d, cx0, cz0, cx1, cz1, h);
		int top = kiln.top();
		if (shade) {
			// a shade of poles and thatch over the clamp against the rains
			Roofs.Roof r = Shed.open(d, cx0 - 1, cz0 - 1, cx1 + 1, dp - 1, 0, h + 2, RoofForm.HIP, Direction.Axis.X, 1);
			top = Math.max(top, r != null ? r.ridgeY : h + 3);
		}
		mouths(d, pois, kiln);
		// cordwood stacked along the clamp's ends for the eyes
		for (int x : new int[]{cx0 - 1, cx1 + 1}) {
			if (Works.in(d, x, cz0) && x != ex && d.isEmpty(x, 0, cz0) && !shade) {
				Timber.cord(d, x, 0, cz0, Dirs.BACK, Math.min(3, cd), 2, Works.logs(d));
			}
		}
		// the hacks down both sides of the yard, capped with boards, the moulding table at the head of one
		int hz0 = 2;
		int hz1 = cz0 - 3;
		int[] sides = {1, w - 2};
		for (int hx : sides) {
			if (hz1 - hz0 >= 1 && Math.abs(hx - ex) > 1) {
				Clayworks.hack(d, hx, 0, hz0, Dirs.BACK, hz1 - hz0 + 1, true);
				pois.siteIf(d, hx + (hx < w / 2 ? 1 : -1), 0, (hz0 + hz1) / 2, hx < w / 2 ? Dirs.LEFT : Dirs.RIGHT, JobType.KILN_WORKER);
			}
		}
		int tx = left ? 2 : w - 3;
		if (Math.abs(tx - ex) > 1 && cz0 - 2 >= 2) {
			Props.station(d, Blocks.CRAFTING_TABLE, tx, 0, cz0 - 2, left ? Dirs.RIGHT : Dirs.LEFT, null);
			pois.siteIf(d, tx + (left ? 1 : -1), 0, cz0 - 2, left ? Dirs.LEFT : Dirs.RIGHT, JobType.KILN_WORKER);
		}
		// the clay heap and the stack of fired brick with its chest at the front corners
		Heap.heap(d, left ? w - 2.2 : 1.2, 1.4, 1.2, 1.1, 1.3, 0, Clayworks.clay(), null, 73);
		int bx = left ? 2 : w - 4;
		if (Math.abs(bx - ex) >= 2 && Math.abs(bx + 1 - ex) >= 2) {
			Clayworks.stack(d, bx, 0, 1, Dirs.BACK, 1);
			pois.store(d, bx + (left ? 2 : -1), 0, 1, left ? Dirs.RIGHT : Dirs.LEFT, true, Works.items("minecraft:brick", 64, "minecraft:clay_ball", 32));
		} else {
			pois.store(d, left ? 1 : w - 2, 0, 1, Dirs.BACK, true, Works.items("minecraft:brick", 64, "minecraft:clay_ball", 32));
		}
		return top;
	}

	// ------------------------------------------------------------------------------------------------ the beehive kiln

	private static int beehive(Draft d, Pois pois, int w, int dp, int ex, boolean left) {
		Works.Tradition t = Works.tradition(d);
		Fam body = t == Works.Tradition.SOUTH ? Fam.brick() : Fam.mudBrick();
		double r = w >= 13 && dp >= 13 ? 3.0 : 2.5;
		double cx = (w - 1) / 2.0 + (left ? -1 : 1) * (w >= 12 ? 1 : 0);
		double cz = dp - 1.6 - r;
		double height = Math.min(r + 2.0, d.maxHeight() - 3);
		Clayworks.Kiln kiln = Clayworks.beehive(d, cx, cz, r, height, body);
		mouths(d, pois, kiln);
		int mx = (int) Math.round(cx);
		int fz = kiln.mouths().isEmpty() ? kiln.z0() : kiln.mouths().get(0).z();
		// the reed shade over the working floor before the mouth
		if (fz - 4 >= 2) {
			Adobe.ramada(d, mx - 2, fz - 4, mx + 2, fz - 2, 0);
		}
		// cordwood (brushwood in the dry lands) by the kiln's side
		int cordX = left ? kiln.x1() + 1 : kiln.x0() - 1;
		if (Works.in(d, cordX, kiln.z0() + 1) && d.isEmpty(cordX, 0, kiln.z0() + 1)) {
			Timber.cord(d, cordX, 0, kiln.z0() + 1, Dirs.BACK, Math.min(3, kiln.z1() - kiln.z0() - 1), 2, Works.logs(d));
		}
		// the green brick laid out to dry in the sun in rows along the other side, turned by the moulder
		int sx0 = left ? Math.min(w - 2, kiln.x1() + 2) : 1;
		int sx1 = left ? w - 2 : Math.max(1, kiln.x0() - 2);
		sunBricks(d, sx0, 3, sx1, dp - 2, ex);
		int stx = left ? sx1 : sx0;
		if (Works.in(d, stx, 2) && Math.abs(stx - ex) > 1) {
			Props.station(d, Blocks.CRAFTING_TABLE, stx, 0, 2, Dirs.FRONT, null);
			pois.siteIf(d, stx, 0, 1, Dirs.BACK, JobType.KILN_WORKER);
		}
		// a water jar for the clay by the shade, the clay heap and the stacks before the gate
		int jx = mx + 3;
		if (Works.in(d, jx, fz - 3) && d.isEmpty(jx, 0, fz - 3) && Math.abs(jx - ex) > 1) {
			Props.jars(d, jx, 0, fz - 3, Dirs.BACK, 1);
		}
		front(d, pois, w, ex, left ? 2 : w - 3, fz - 4, left ? w - 4 : 2, fz - 4, !left);
		return kiln.top();
	}

	/** Green brick laid flat in rows on the ground to dry (every other row, an aisle between), clear of the gate. */
	private static void sunBricks(Draft d, int x0, int z0, int x1, int z1, int ex) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z += 2) {
				for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
					if (Works.in(d, x, z) && Math.abs(x - ex) > 1 && d.isEmpty(x, 0, z) && d.isEmpty(x, 1, z)) {
						d.set(x, 0, z, States.slab(Blocks.MUD_BRICK_SLAB, SlabType.BOTTOM));
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ the dragon kiln

	private static int dragon(Draft d, Pois pois, int w, int dp, int ex, boolean kilnLeft) {
		int x = kilnLeft ? 2 : w - 5;
		// the foot two rows in: the roof's front posts stand on the row before the firebox, not in it
		int z0 = 3;
		int z1 = dp - 2;
		Direction stoke = kilnLeft ? Dirs.RIGHT : Dirs.LEFT;
		// one long roof over kiln and walk, on posts outside them both
		int rx0 = x - 2;
		int rx1 = x + 4;
		int height = Math.min(7, d.maxHeight() - 4);
		RoofForm form = Hall.pick(d, RoofForm.PAGODA, RoofForm.GABLE, RoofForm.HIP);
		Roofs.Roof roof = Shed.open(d, Math.max(0, rx0), z0 - 2, Math.min(w - 1, rx1), dp - 1, 0, height, form, Direction.Axis.Z, 1);
		Clayworks.Kiln kiln = Clayworks.dragon(d, x, z0, z1, stoke, Fam.brick());
		mouths(d, pois, kiln);
		int top = Math.max(kiln.top(), roof != null ? roof.ridgeY : height + 1);
		// the drying shed on the other side, a row clear of the long roof (its eave hung over the climbing walk)
		int sx0 = kilnLeft ? rx1 + 2 : 0;
		int sx1 = kilnLeft ? w - 1 : rx0 - 2;
		int shedTop = sx1 - sx0 >= 2 ? dryingShed(d, pois, sx0, sx1, 4, dp - 1, kilnLeft) : 0;
		front(d, pois, w, ex, (sx0 + sx1) / 2, 4, kilnLeft ? sx1 - 1 : sx0, 3, kilnLeft);
		return Math.max(top, shedTop);
	}
}
