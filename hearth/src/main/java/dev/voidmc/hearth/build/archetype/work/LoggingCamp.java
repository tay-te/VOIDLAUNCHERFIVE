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
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.industry.Cabin;
import dev.voidmc.hearth.build.kit.industry.Timber;
import dev.voidmc.hearth.build.kit.industry.Pois;
import dev.voidmc.hearth.build.kit.industry.Works;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Lifts;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.kit.trade.Shed;
import dev.voidmc.hearth.build.kit.trade.Signs;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * {@code hearth:work/logging_camp}: the foresters' landing at the wood's edge, where the felled timber comes out of
 * the forest and waits for the carts. The track runs into an open landing of trodden earth; along one side lie the log
 * decks, long trunks piled pyramid-wise on their skids with their butt ends to the track, the forest's yield counted by
 * the street at a glance. In the middle the saw pit: a trench boarded at its lips, a trunk across it on trestles, a
 * gantry over it lowering the next log on its chain, offcuts and slabwood about. At the back the bunkhouse, a log cabin
 * (the culture's own walls where it does not build in logs) with a boarded gable and a smoking chimney, the bunks and
 * the table inside; against its gable end the smithy corner under a lean-to, the anvil, the grindstone where the axes
 * are kept sharp and racks of axes. Cordwood for the stove, a chopping block, stumps of the trees already taken, a cart
 * loaded with a trunk for the sawmill.
 *
 * <p>Job sites: FORESTER at the saw pit (top and bottom sawyer), at the decks, at the grindstone; HAULER at the cart and
 * at the decks' loading end. Storage: the chest in the bunkhouse, the tool chest in the smithy corner. Beds: the bunks.
 *
 * <p>Params: {@code roof} (the bunkhouse's form), {@code variant}; extraction params ({@code site}, {@code kind},
 * {@code rock}, {@code face}) are accepted and unused.
 */
public final class LoggingCamp implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("work/logging_camp"), ArchetypeCategory.WORKPLACE,
		Set.of(JobType.FORESTER, JobType.HAULER), 0, 12, 17, 12, 17, 7, 11, SettlementTier.HAMLET, Set.of(), Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS),
		List.of(), 0, 1.0);

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.HIP, RoofForm.SALTBOX, RoofForm.PAGODA, RoofForm.FLAT};

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
		boolean houseLeft = d.chance(0.5);
		Direction toHouse = houseLeft ? Dirs.LEFT : Dirs.RIGHT;
		Direction toDecks = toHouse.getOpposite();
		Mat logs = Works.logs(d);
		RoofForm form = Hall.pick(d, FORMS);

		// the bunkhouse at the back on its side, the smithy corner leaning on its gable toward the middle
		int bw = Math.min(9, Math.max(6, w - 7));
		int bd = dp >= 15 ? 6 : 5;
		int bx0 = houseLeft ? 0 : w - bw;
		int bx1 = bx0 + bw - 1;
		int bz1 = dp - 1;
		int bz0 = bz1 - bd + 1;
		int doorAt = houseLeft ? bx1 - 2 : bx0 + 2;
		Hall bunk = Cabin.build(d, bx0, bz0, bx1, bz1, 4, Dirs.FRONT, doorAt, form, Direction.Axis.X, Cabin.logs(d));
		int door = bunk.door != null ? bunk.door : doorAt;
		int top = bunk.top;
		// the chimney on the outer gable
		int chx = houseLeft ? bx0 + 1 : bx1 - 1;
		int chz = (bz0 + bz1) / 2;
		int smoke = bunk.chimney(chx, chz, 0);
		if (smoke != Integer.MIN_VALUE) {
			top = Math.max(top, smoke);
		}
		bunkhouse(d, pois, bunk, door, chx, chz);

		// the smithy corner: a lean-to on the inner gable
		int sx0 = houseLeft ? bx1 + 1 : bx0 - 3;
		int sx1 = sx0 + 2;
		int sz0 = bz0 + 1;
		int sz1 = bz1;
		boolean smithy = Works.in(d, sx0, sz0) && Works.in(d, sx1, sz1) && Math.abs(ex - (sx0 + sx1) / 2) > 2;
		if (smithy) {
			Roofs.Roof lean = Shed.pent(d, sx0, sz0, sx1, sz1, toHouse, 3, 0.5, null);
			int outer = houseLeft ? sx1 : sx0;
			int inner = houseLeft ? sx0 : sx1;
			int mid = sx0 + 1;
			Shed.posts(d, lean, List.of(new int[]{outer, sz0}, new int[]{outer, sz1}), 0);
			pois.store(d, inner, 0, sz1, toDecks, true, Works.items("minecraft:iron_axe", 4, "minecraft:stone_axe", 2, "minecraft:iron_ingot", 6));
			pois.grindstone(d, inner, 0, sz1 - 2, toDecks, JobType.FORESTER);
			pois.anvil(d, mid, 0, sz0 + 1, Dirs.FRONT, JobType.FORESTER);
			Props.toolRack(d, inner, 2, sz1 - 1, toDecks, List.of(new Object[]{"minecraft:iron_axe", 1, 0},
				new Object[]{"minecraft:iron_axe", 1, 1}, new Object[]{"minecraft:shears", 1, 2}));
			if (lean.covers(mid, sz1 - 1) && lean.bottomY(mid, sz1 - 1) >= 3) {
				Props.hangLantern(d, mid, lean.bottomY(mid, sz1 - 1) - 1, sz1 - 1);
			}
		}

		// the log decks down the other side, butts to the track
		int dw = Math.min(4, Math.max(2, (w - bw) / 2));
		int dx0 = houseLeft ? w - dw : 0;
		int deckLen = Math.min(7, bz0 - 1);
		if (houseLeft ? dx0 > bx1 + 1 : dx0 + dw - 1 < bx0 - 1) {
			deckLen = dp - 2;
		}
		int d1 = Timber.deck(d, dx0, 0, 1, Direction.Axis.Z, deckLen, dw, 3, logs);
		top = Math.max(top, d1);
		int dsx = houseLeft ? dx0 - 1 : dx0 + dw;
		pois.siteIf(d, dsx, 0, 1 + deckLen / 2, toDecks, JobType.FORESTER);
		pois.siteIf(d, dsx, 0, 1, toDecks, JobType.HAULER);
		// the saw pit between the decks and the lane, the gantry over it
		int px = houseLeft ? Math.min(ex + 2, dx0 - 2) : Math.max(ex - 2, dx0 + dw + 1);
		if (houseLeft && px < ex + 2 || !houseLeft && px > ex - 2) {
			px = -1;
		}
		int pz0 = 2;
		int pz1 = Math.min(bz0 - 2, pz0 + 4);
		if (px >= 1 && px <= w - 2 && pz1 - pz0 >= 2) {
			sawPit(d, pois, px, pz0, pz1, logs, toHouse);
		}

		// a second deck inside the first where the landing is broad
		if (w >= 15) {
			int dx2 = houseLeft ? dx0 - dw - 1 : dx0 + dw + 1;
			boolean clear = px < 0 || px + 1 < Math.min(dx2, dx2 + dw - 2) || px - 1 > Math.max(dx2, dx2 + dw - 2);
			if (clear && Math.abs(dx2 + dw / 2 - ex) > 2) {
				Timber.deck(d, dx2, 0, 2, Direction.Axis.Z, Math.max(3, deckLen - 2), dw - 1, 2, logs);
			}
		}

		// the cart loaded with a trunk for the sawmill by the track; cordwood for the stove; a chopping block
		int cx = ex + (houseLeft ? -3 : 3);
		if (Works.in(d, cx - 1, 1) && Works.in(d, cx + 1, 1) && d.isEmpty(cx, 0, 1) && d.isEmpty(cx - 1, 0, 1) && d.isEmpty(cx + 1, 0, 1)
			&& d.isEmpty(cx, 0, 0)) {
			Props.cart(d, cx, 0, 1, Dirs.FRONT, logs.log(Direction.Axis.Z).at(cx, 1, 1));
			pois.siteIf(d, cx, 0, 2, Dirs.FRONT, JobType.HAULER);
		}
		int fx = houseLeft ? 0 : w - 1;
		if (bz0 - 2 >= 3) {
			Timber.cord(d, fx, 0, bz0 - 1, Dirs.FRONT, Math.min(3, bz0 - 3), 2, logs);
		}
		Props.choppingBlock(d, houseLeft ? 2 : w - 3, 0, bz0 - 2);
		for (int i = 0; i < 6; i++) {
			int x = d.between(0, w - 1);
			int z = d.between(1, dp - 1);
			if ((x == 0 || x == w - 1) && Math.abs(x - ex) > 1) {
				Timber.stump(d, x, 0, z, logs);
			}
		}

		// the gate posts with the sign, a lamp at the landing
		int signX = ex + (houseLeft ? 2 : -2);
		if (Works.in(d, signX, 0) && d.isEmpty(signX, 0, 0)) {
			Signs.post(d, signX, 0, 0, toDecks, 3, "logging_camp", "Logging Camp");
		}
		int lx = ex + (houseLeft ? -1 : 1) * 3;
		if (Works.in(d, lx, bz0 - 1) && d.isEmpty(lx, 0, bz0 - 1) && d.isEmpty(lx, 1, bz0 - 1)) {
			Lighting.post(d, lx, 0, bz0 - 1);
		}
		pois.commit(d, INFO.jobs().toArray(JobType[]::new), new int[]{w / 2, 0, 1}, new int[]{w / 2, 0, 0});
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, top + 2));
	}

	/** The bunkhouse: bunks along the back wall, a table and benches, the stove at the chimney, chests, lamps. */
	private static void bunkhouse(Draft d, Pois pois, Hall h, int door, int chx, int chz) {
		Floorplan fp = h.floor(0).blockHoles(d);
		int[] in = h.inner(0);
		fp.keep(door, in[1]);
		// the stove at the chimney's foot
		if (fp.inside(chx, chz)) {
			fp.block(chx, chz);
		}
		// the camp's chest in a back corner, kept clear of the bunks
		int cxs = door - in[0] > in[2] - door ? in[0] : in[2];
		if (fp.take(cxs, in[3], Dirs.FRONT)) {
			pois.store(d, cxs, 0, in[3], Dirs.FRONT, true, Works.items("minecraft:bread", 8, "minecraft:iron_axe", 2, "minecraft:spruce_log", 16));
		}
		Rooms.Room room = fp.room();
		Rooms.furnish(d, room, Rooms.Type.BEDROOM, Math.max(2, (in[2] - in[0]) / 2), JobType.NONE);
		Lighting.room(d, room);
	}

	/**
	 * The saw pit along z at column x from z0 to z1: a trench two deep boarded at its lips, a ladder at its far end, a
	 * trunk lying over it on trestles at both ends, offcuts about, and a gantry over it lowering the next trunk.
	 */
	private static void sawPit(Draft d, Pois pois, int x, int z0, int z1, Mat logs, Direction side) {
		Mat boards = d.mat(Role.FLOOR);
		Mat frame = d.mat(Role.FRAME);
		try (var s = d.stage(StageKind.EARTHWORK)) {
			for (int z = z0; z <= z1; z++) {
				d.set(x, -1, z, Blocks.AIR.defaultBlockState());
				d.set(x, -2, z, Blocks.AIR.defaultBlockState());
				d.set(x, -3, z, Blocks.COARSE_DIRT.defaultBlockState());
			}
		}
		try (var s = d.stage(StageKind.FOUNDATION)) {
			// boarded lips along both sides of the trench
			for (int z = z0; z <= z1; z++) {
				for (int k : new int[]{-1, 1}) {
					d.set(x + k, -1, z, boards.full());
					d.set(x + k, -2, z, boards.full());
				}
			}
			d.set(x, -1, z0 - 1, boards.full());
			d.set(x, -2, z0 - 1, boards.full());
			d.set(x, -1, z1 + 1, boards.full());
			d.set(x, -2, z1 + 1, boards.full());
		}
		// the trestles across the pit at both ends, the trunk along it
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z : new int[]{z0, z1}) {
				d.set(x - 1, 0, z, frame.log(Direction.Axis.X));
				d.set(x, 0, z, frame.log(Direction.Axis.X));
				d.set(x + 1, 0, z, frame.log(Direction.Axis.X));
			}
			for (int z = z0; z <= z1; z++) {
				d.set(x, 1, z, logs.log(Direction.Axis.Z));
			}
			// a ladder down at the far end for the pit sawyer
			d.set(x, -2, z1, Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Dirs.FRONT));
			d.set(x, -1, z1, Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Dirs.FRONT));
		}
		pois.siteIf(d, x + Dirs.dx(side), 0, (z0 + z1) / 2, side.getOpposite(), JobType.FORESTER);
		pois.siteIf(d, x - Dirs.dx(side), 0, (z0 + z1) / 2 + 1, side, JobType.FORESTER);
		// offcuts and slabwood about the pit
		List<int[]> cells = new ArrayList<>();
		for (int z = z0; z <= z1; z += 2) {
			cells.add(new int[]{x - 2, z});
			cells.add(new int[]{x + 2, z + 1});
		}
		Timber.offcuts(d, cells, 0, boards);
		// the gantry along the pit, the next trunk on its chain over the one being sawn
		Lifts.gantry(d, x, z0 - 1, x, z1 + 1, 0, 5, 3, logs.log(Direction.Axis.Z).at(x, 3, (z0 + z1) / 2));
	}
}
