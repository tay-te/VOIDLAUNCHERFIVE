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
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.craft.Hatch;
import dev.voidmc.hearth.build.kit.craft.Rails;
import dev.voidmc.hearth.build.kit.homestead.Adobe;
import dev.voidmc.hearth.build.kit.industry.Works;
import dev.voidmc.hearth.build.kit.learning.Studies;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.kit.trade.Hall;
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
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:work/alchemist_tower}: the alchemist's house, a squat tower of three storeys by the library with its
 * laboratory beside it, built as each land builds its learned men's houses:
 * <ul>
 *   <li><b>stone tower</b> (Bohemia, the moor, the coasts, the Mediterranean, the bayou): a square tower of the
 *   culture's stone in a back corner of the plot under a steep pyramid roof oversailing its walls, and against its side
 *   the laboratory, a long low wing under its own gable with the athanor's great chimney at its far end; a herb garden
 *   before them;</li>
 *   <li><b>tower house</b> (the north, the tundra, the mountains): a tower in the culture's walls (log or boarded over a
 *   stone ground floor) in the middle of the plot under a steep gable, the still-room a lean-to against its flank with
 *   its own chimney, a woodpile and kitchen beds before it;</li>
 *   <li><b>walled laboratory</b> (the desert, the mesa, the steppe): a court behind a high wall with a gate, a squat
 *   flat-roofed tower in its back corner, the laboratory range along the back wall, and in the court the still under a
 *   reed shade, the water basin and the jars;</li>
 *   <li><b>pavilion tower</b> (the karst, the wisteria vale, the tropics): a tower of three tiers under the culture's
 *   tiered eaves in a back corner, before it on the other side an open shed over the elixir furnace with its cauldron,
 *   a little pool among stones.</li>
 * </ul>
 * In the tower, from the bottom: the <b>workshop</b> (stores, the grindstone, a crucible), the <b>laboratory</b>
 * (benches of brewing stands and bottles under shelves, the still on its barrel against a wall, the alchemist's lectern),
 * the <b>study</b> under the roof (his bed, a desk, books, a chest, a reading lectern), a turning stair (a ladder where it
 * will not fit) up through them. In the laboratory beside it, the athanor under the chimney and the crucible.
 *
 * <p>POIs: the SCHOLAR's JOB_SITE at the lectern and the still, LECTERNs, the BED, STORAGE at the chests, a TABLE, DOOR.
 *
 * <p>Params: {@code plan} ({@code stone}, {@code timber}, {@code court}, {@code pavilion}; default by the culture),
 * {@code tower_left} (boolean; default by seed), {@code variant}.
 */
public final class AlchemistTower implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("work/alchemist_tower"), ArchetypeCategory.WORKPLACE, Set.of(JobType.SCHOLAR), 1,
		11, 15, 10, 14, 14, 22, SettlementTier.TOWN, Set.of(), Set.of(ArchetypeInfo.SiteFlag.VERTICAL), List.of(), 1, 1.0);

	/** The house a land builds for its alchemist. */
	private enum Plan {
		STONE, TIMBER, COURT, PAVILION
	}

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	private static Plan plan(Draft d) {
		String p = d.param("plan", "");
		if (!p.isEmpty()) {
			try {
				return Plan.valueOf(p.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				// by the culture below
			}
		}
		return switch (Works.culture(d)) {
			case NORTHERN, TUNDRA, ALPINE -> Plan.TIMBER;
			case DESERT, MESA, STEPPE -> Plan.COURT;
			case KARST, WISTERIA, TROPICAL -> Plan.PAVILION;
			default -> Hall.flatCulture(d) ? Plan.COURT : Plan.STONE;
		};
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		boolean left = d.param("tower_left", d.chance(0.5));
		int ex = Yard.entrance(d);
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		int top = switch (plan(d)) {
			case STONE -> stone(d, w, dp, ex, left);
			case TIMBER -> timber(d, w, dp, ex, left);
			case COURT -> court(d, w, dp, ex, left);
			case PAVILION -> pavilion(d, w, dp, ex, left);
		};
		// a rail wherever the levelled yard ends over a drop
		dev.voidmc.hearth.build.kit.craft.Rails.yard(d);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, top + 2));
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	// ------------------------------------------------------------------------------------------------ the tower

	/** A tower built: its hall, the stair or ladder of each storey (k: from k up to k + 1), its front door's column. */
	private record Tower(Hall hall, int[][] wells, int[][] ladders, int door) {
	}

	/**
	 * A tower over [x0..x1] x [z0..z1]: as many of the storeys {@code want} as the plot's height takes under a roof of
	 * {@code form}, its door in the front wall near {@code doorAt}, windows round it, a turning stair in the back corner
	 * against {@code wellWall} storey by storey (a ladder through a hatch where it will not fit), its roof (a crowned
	 * deck when flat).
	 */
	private static Tower tower(Draft d, int x0, int z0, int x1, int z1, int[] want, RoofForm form, Direction.Axis ridge, int doorAt, Direction wellWall) {
		int[] heights = Hall.budget(d, x0, z0, x1, z1, 0, want, false, form, 1, ridge);
		Hall t = Hall.of(d, x0, z0, x1, z1, 0, heights, false, false);
		t.facade(doorAt, 1, 1, 0.45);
		int door = t.door != null ? t.door : doorAt;
		t.ground(false).shell().openings(true);
		int n = t.storeys();
		int[][] wells = new int[n][];
		int[][] ladders = new int[n][];
		for (int k = 0; k + 1 < n; k++) {
			wells[k] = t.switchback(k, wellWall);
		}
		t.roof(form, ridge, 1, null);
		if (t.roof == null) {
			t.crown();
		}
		for (int k = 0; k + 1 < n; k++) {
			if (wells[k] == null) {
				ladders[k] = Hatch.ladder(d, t, k + 1, null, Dirs.BACK, wellWall, wellWall.getOpposite());
			}
		}
		return new Tower(t, wells, ladders, door);
	}

	/**
	 * A storey's floor plan with the way through it kept: the door's inside cell (on the ground floor), the foot of the
	 * stair up and the head of the stair from below, the stairwells blocked (but for their feet), the ladders' cells.
	 */
	private static Floorplan floor(Draft d, Tower tw, int k, int[]... keep) {
		Hall t = tw.hall();
		Floorplan fp = t.floor(k).blockHoles(d);
		int[] in = t.inner(k);
		if (k == 0) {
			fp.keep(tw.door(), in[1]);
		}
		if (k > 0 && tw.wells()[k - 1] != null) {
			int[] b = tw.wells()[k - 1];
			fp.keep(b[2], b[3]);
			fp.keepArea(b[4], b[5], b[6], b[7]);
		}
		if (k > 0 && tw.ladders()[k - 1] != null) {
			Hatch.keep(fp, tw.ladders()[k - 1]);
		}
		if (tw.wells()[k] != null) {
			int[] wl = tw.wells()[k];
			for (int x = wl[4]; x <= wl[6]; x++) {
				for (int z = wl[5]; z <= wl[7]; z++) {
					if (x != wl[0] || z != wl[1]) {
						fp.block(x, z);
					}
				}
			}
			fp.keep(wl[0], wl[1]);
		}
		if (tw.ladders()[k] != null) {
			fp.keep(tw.ladders()[k][0], tw.ladders()[k][1]);
		}
		for (int[] c : keep) {
			fp.keep(c[0], c[1]);
		}
		return fp;
	}

	/**
	 * Furnishes the tower: workshop, laboratory, study, as many as it has storeys (the study before all else). With
	 * {@code athanor} (a wall), the athanor and its flue stand in the workshop (no laboratory wing to hold them).
	 */
	private static void furnish(Draft d, Tower tw, Direction athanor, int[]... groundKeep) {
		int n = tw.hall().storeys();
		for (int k = 0; k < n; k++) {
			Floorplan fp = floor(d, tw, k, k == 0 ? groundKeep : new int[0][]);
			int role = n == 1 ? 1 : k == 0 ? 0 : k == n - 1 ? 2 : 1;
			if (k == 0 && athanor != null) {
				athanorPieces(d, tw.hall(), fp, athanor);
			}
			if (n == 2 && k == 1) {
				// two storeys: the laboratory and the study share the upper floor
				laboratory(d, fp, 2);
				study(d, fp);
			} else if (role == 0) {
				workshop(d, fp);
			} else if (role == 1) {
				laboratory(d, fp, 4);
			} else {
				study(d, fp);
			}
			Rooms.furnish(d, fp.room(), role == 2 ? Rooms.Type.BEDROOM : Rooms.Type.WORKSHOP, 0, JobType.NONE);
		}
		// the stairwells railed on the floors above, round what stands beside them too
		for (int k = 0; k + 1 < n; k++) {
			Rails.well(d, tw.hall(), k, tw.wells()[k]);
		}
	}

	/** The workshop: chests and barrels of the stuff of the work, the grindstone, a crucible of water. */
	private static void workshop(Draft d, Floorplan fp) {
		int y = fp.y;
		boolean chest = false;
		boolean grind = false;
		boolean crucible = false;
		for (Direction wall : new Direction[]{Dirs.BACK, Dirs.LEFT, Dirs.RIGHT, Dirs.FRONT}) {
			Direction into = wall.getOpposite();
			for (int[] c : fp.along(wall)) {
				if (!chest && fp.take(c[0], c[1], into)) {
					Furniture.chest(d, c[0], y, c[1], into, List.of(new Object[]{"minecraft:coal", 16}, new Object[]{"minecraft:redstone", 8},
						new Object[]{"minecraft:glass_bottle", 12}, new Object[]{"minecraft:sugar", 6}));
					chest = true;
				} else if (!grind && fp.take(c[0], c[1], into)) {
					d.inStage(StageKind.INTERIOR, () -> d.set(c[0], y, c[1], States.attached(Blocks.GRINDSTONE,
						net.minecraft.world.level.block.state.properties.AttachFace.FLOOR, Dirs.right(into))));
					grind = true;
				} else if (!crucible && fp.take(c[0], c[1], into)) {
					Studies.crucible(d, c[0], y, c[1], true);
					crucible = true;
				} else if (fp.take(c[0], c[1], null)) {
					Furniture.barrel(d, c[0], y, c[1], Direction.UP);
				}
			}
		}
	}

	/**
	 * The laboratory: {@code benches} benches of brewing stands and jars along the walls with shelves of bottles over
	 * them, the still (a stand on a barrel) against a wall with room before it, the alchemist's lectern with the book of
	 * the Great Work: his place.
	 */
	private static void laboratory(Draft d, Floorplan fp, int benches) {
		int y = fp.y;
		boolean lectern = false;
		boolean still = false;
		int placed = 0;
		for (Direction wall : new Direction[]{Dirs.FRONT, Dirs.RIGHT, Dirs.LEFT, Dirs.BACK}) {
			Direction into = wall.getOpposite();
			for (int[] c : fp.along(wall)) {
				if (!lectern && fp.take(c[0], c[1], into)) {
					Studies.lectern(d, c[0], y, c[1], into, JobType.SCHOLAR, "Of the Great Work");
					lectern = true;
				} else if (!still && fp.take(c[0], c[1], into)) {
					// the still: a stand on its barrel, worked from the cell before it
					d.inStage(StageKind.INTERIOR, () -> d.set(c[0], y, c[1], States.facing(Blocks.BARREL, into)));
					Studies.brewingStand(d, c[0], y + 1, c[1]);
					d.poi(PoiType.JOB_SITE, c[0] + Dirs.dx(into), y, c[1] + Dirs.dz(into), into.getOpposite(), JobType.SCHOLAR);
					still = true;
				} else if (placed < benches && fp.take(c[0], c[1], into)) {
					Studies.counter(d, c[0], y, c[1], into, placed);
					if (fp.height >= 4) {
						Studies.bottleShelf(d, c[0], y + 2, c[1], into);
					}
					placed++;
				}
			}
		}
	}

	/** The study: the bed with a free side, a desk, books and a chest, a reading lectern. */
	private static void study(Draft d, Floorplan fp) {
		int y = fp.y;
		Block bed = d.mat(Role.CLOTH).mainOr(Form.BED, Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED));
		boolean slept = false;
		// the bed along a wall, its foot out, a free cell along its side
		for (Direction wall : new Direction[]{Dirs.BACK, Dirs.RIGHT, Dirs.LEFT}) {
			if (slept) {
				break;
			}
			Direction along = Dirs.right(wall);
			for (int[] c : fp.along(wall)) {
				int hx = c[0] + Dirs.dx(along);
				int hz = c[1] + Dirs.dz(along);
				int[] side = {c[0] - Dirs.dx(wall), c[1] - Dirs.dz(wall)};
				if (fp.take(new int[][]{{c[0], c[1]}, {hx, hz}}, side)) {
					d.inStage(StageKind.INTERIOR, () -> d.bed(c[0], y, c[1], bed, along));
					slept = true;
					break;
				}
			}
		}
		boolean desk = false;
		boolean books = false;
		boolean chest = false;
		boolean read = false;
		for (Direction wall : new Direction[]{Dirs.FRONT, Dirs.LEFT, Dirs.RIGHT, Dirs.BACK}) {
			Direction into = wall.getOpposite();
			for (int[] c : fp.along(wall)) {
				if (!desk && fp.take(c[0], c[1], into)) {
					Studies.desk(d, c[0], y, c[1], into, false);
					desk = true;
				} else if (!books && fp.take(c[0], c[1], null)) {
					Studies.books(d, c[0], y, c[1], Math.min(2, fp.height - 1), into);
					books = true;
				} else if (!chest && fp.take(c[0], c[1], into)) {
					Furniture.chest(d, c[0], y, c[1], into, List.of(new Object[]{"minecraft:book", 4}, new Object[]{"minecraft:paper", 12},
						new Object[]{"minecraft:ink_sac", 3}));
					chest = true;
				} else if (!read && fp.take(c[0], c[1], into)) {
					Studies.lectern(d, c[0], y, c[1], into, null, "Notes");
					read = true;
				}
			}
		}
	}

	/**
	 * The laboratory wing's hall: the athanor (a blast furnace and a furnace) against its far wall under the chimney, a
	 * crucible, a still on a barrel and benches of stands, a chest of coal. {@code end}: the far wall.
	 */
	private static void athanor(Draft d, Hall h, Floorplan fp, Direction end) {
		athanorPieces(d, h, fp, end);
		Rooms.furnish(d, fp.room(), Rooms.Type.WORKSHOP, 0, JobType.NONE);
	}

	/** {@link #athanor}'s pieces on a floor plan, without the ordinary furniture round them. */
	private static void athanorPieces(Draft d, Hall h, Floorplan fp, Direction end) {
		int[] in = h.inner(0);
		int y = fp.y;
		Direction into = end.getOpposite();
		int ax = end == Dirs.LEFT ? in[0] : end == Dirs.RIGHT ? in[2] : (in[0] + in[2]) / 2;
		int az = end == Dirs.BACK ? in[3] : end == Dirs.FRONT ? in[1] : (in[1] + in[3]) / 2 + 1;
		Direction along = Dirs.right(end);
		int bx = ax + Dirs.dx(along);
		int bz = az + Dirs.dz(along);
		if (fp.take(new int[][]{{ax, az}, {bx, bz}}, new int[]{ax + Dirs.dx(into), az + Dirs.dz(into)})) {
			d.inStage(StageKind.INTERIOR, () -> {
				d.set(ax, y, az, States.facing(Blocks.BLAST_FURNACE, into).trySetValue(BlockStateProperties.LIT, true));
				d.set(bx, y, bz, States.facing(Blocks.FURNACE, into));
			});
			d.poi(PoiType.JOB_SITE, ax + Dirs.dx(into), y, az + Dirs.dz(into), end, JobType.SCHOLAR);
			if (h.roof != null || h.deck != Integer.MIN_VALUE) {
				h.chimney(ax + Dirs.dx(end), az + Dirs.dz(end), y + 1);
			}
		}
		int benches = 0;
		boolean still = false;
		boolean chest = false;
		boolean crucible = false;
		for (Direction wall : new Direction[]{Dirs.BACK, Dirs.FRONT, end, end.getOpposite()}) {
			Direction in2 = wall.getOpposite();
			for (int[] c : fp.along(wall)) {
				if (!crucible && fp.take(c[0], c[1], in2)) {
					Studies.crucible(d, c[0], y, c[1], true);
					crucible = true;
				} else if (!still && fp.take(c[0], c[1], in2)) {
					d.inStage(StageKind.INTERIOR, () -> d.set(c[0], y, c[1], States.facing(Blocks.BARREL, in2)));
					Studies.brewingStand(d, c[0], y + 1, c[1]);
					still = true;
				} else if (!chest && fp.take(c[0], c[1], in2)) {
					Furniture.chest(d, c[0], y, c[1], in2, List.of(new Object[]{"minecraft:coal", 24}, new Object[]{"minecraft:charcoal", 16},
						new Object[]{"minecraft:raw_iron", 4}));
					chest = true;
				} else if (benches < 2 && fp.take(c[0], c[1], in2)) {
					Studies.counter(d, c[0], y, c[1], in2, benches + 2);
					benches++;
				}
			}
		}
	}

	/**
	 * Clears from a wing's rooms what the tower's walls put out on that side before the wing stood there (window sills,
	 * hoods, shutters): anything inside the wing's walls that is not a full block goes.
	 */
	private static void clearInside(Draft d, Hall wing) {
		int[] in = wing.inner(0);
		int top = wing.floorY(1) - 1;
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int x = in[0]; x <= in[2]; x++) {
				for (int z = in[1]; z <= in[3]; z++) {
					for (int y = wing.floorY(0); y < top; y++) {
						var st = d.get(x, y, z);
						if (st != null && !st.isAir() && !st.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO)) {
							d.set(x, y, z, Blocks.AIR.defaultBlockState());
						}
					}
				}
			}
		}
	}

	/** Herb beds before the house between the paths, a lamp by the gate, the fence along the street open at the paths. */
	private static void frontGarden(Draft d, int w, int z1, List<Integer> paths, int ex) {
		if (z1 >= 1) {
			int[] cols = new int[paths.size() + 1];
			for (int i = 0; i < paths.size(); i++) {
				cols[i] = paths.get(i);
			}
			cols[paths.size()] = ex;
			for (int x = 1; x <= w - 2; x++) {
				boolean path = false;
				for (int c : cols) {
					path |= Math.abs(x - c) <= 0;
				}
				if (!path && z1 - 1 >= 1) {
					Garden.kitchenRows(d, x, 1, x, z1 - 1, 0, Dirs.BACK);
				}
			}
		}
		List<int[]> gaps = new ArrayList<>();
		for (int c : paths) {
			gaps.add(new int[]{c, 0});
		}
		gaps.add(new int[]{ex, 0});
		Yard.enclose(d, 0, 0, w - 1, 0, gaps, Dirs.BACK, Dirs.LEFT, Dirs.RIGHT);
		Works.lampPost(d, ex + 1 < w - 1 ? ex + 1 : ex - 1, 0, 1);
	}

	/** A paved path from the street at column {@code from} to the door at (x, z) (the cell before it). */
	private static void path(Draft d, int from, int x, int z) {
		List<int[]> cells = new ArrayList<>();
		int zz = 0;
		for (; zz < z; zz++) {
			cells.add(new int[]{from, zz});
		}
		for (int xx = Math.min(from, x); xx <= Math.max(from, x); xx++) {
			cells.add(new int[]{xx, z - 1});
		}
		Garden.path(d, cells, 0);
	}

	// ------------------------------------------------------------------------------------------------ the stone tower

	private static int stone(Draft d, int w, int dp, int ex, boolean left) {
		int ts = w >= 13 && dp >= 11 ? 8 : 7;
		int tx0 = left ? 0 : w - ts;
		int tx1 = tx0 + ts - 1;
		int tz1 = dp - 1;
		int tz0 = dp - ts;
		RoofForm form = Hall.pick(d, RoofForm.HIP, RoofForm.HIP, RoofForm.PAGODA);
		Direction wellWall = left ? Dirs.LEFT : Dirs.RIGHT;
		Tower tw = tower(d, tx0, tz0, tx1, tz1, new int[]{5, 4, 4}, form, Direction.Axis.X, (tx0 + tx1) / 2, wellWall);
		Hall t = tw.hall();
		// the laboratory wing against the tower's flank, a row shallower, its far wall to the street side's end
		int wx0 = left ? tx1 : 0;
		int wx1 = left ? w - 1 : tx0;
		int wz0 = tz0 + 1;
		int wz1 = dp - 1;
		Hall wing = null;
		if (wx1 - wx0 >= 4) {
			wing = Hall.of(d, wx0, wz0, wx1, wz1, 0, Hall.budget(d, wx0, wz0, wx1, wz1, 0, new int[]{5}, false, RoofForm.GABLE, 1, Direction.Axis.X), left,
				!left);
			int[] win = wing.inner(0);
			wing.facade((win[0] + win[2]) / 2, 1, 1, 0.5);
			wing.ground(false).shell().openings(true);
			wing.roof(Hall.pick(d, RoofForm.GABLE, RoofForm.HALF_HIP), Direction.Axis.X, 1, null);
			if (wing.roof == null) {
				wing.crown();
			}
			clearInside(d, wing);
		}
		int top = t.top;
		int[] tin = t.inner(0);
		// the door between them, through the tower's flank
		int[] link = null;
		if (wing != null) {
			int[] win = wing.inner(0);
			int lz = Math.max(win[1] + 1, Math.min(tin[3] - 1, (win[1] + win[3]) / 2));
			if (wing.door(0, left ? Dirs.LEFT : Dirs.RIGHT, lz, 1, false, false)) {
				link = new int[]{left ? tin[2] : tin[0], lz};
			}
			Floorplan fp = wing.floor(0).blockHoles(d);
			int wd = wing.door != null ? wing.door : (win[0] + win[2]) / 2;
			fp.keep(wd, win[1]);
			if (link != null) {
				fp.keep(left ? win[0] : win[2], lz);
			}
			athanor(d, wing, fp, left ? Dirs.RIGHT : Dirs.LEFT);
			top = Math.max(top, wing.top);
		}
		Direction hearth = wing == null ? (left ? Dirs.RIGHT : Dirs.LEFT) : null;
		if (link != null) {
			furnish(d, tw, hearth, link);
		} else {
			furnish(d, tw, hearth);
		}
		// before them: the herb garden, paths to the doors, the sign over the tower door
		List<Integer> paths = new ArrayList<>();
		paths.add(tw.door());
		path(d, ex, tw.door(), tz0);
		if (wing != null && wing.door != null) {
			paths.add(wing.door);
			path(d, ex, wing.door, wz0);
		}
		frontGarden(d, w, Math.min(tz0, wz0) - 1, paths, ex);
		Signs.bracket(d, t.side(0, Dirs.FRONT), t.side(0, Dirs.FRONT).index(tw.door() + (left ? 1 : -1)), 3, "alchemist_tower", "Alchemist");
		return top;
	}

	// ------------------------------------------------------------------------------------------------ the tower house

	private static int timber(Draft d, int w, int dp, int ex, boolean left) {
		int ts = 7;
		int tx0 = (w - ts) / 2 + (left ? -1 : 1) * (w - ts >= 6 ? 1 : 0);
		int tx1 = tx0 + ts - 1;
		int tz1 = dp - 1;
		int tz0 = Math.max(2, dp - ts - 1);
		Direction wellWall = left ? Dirs.RIGHT : Dirs.LEFT;
		Tower tw = tower(d, tx0, tz0, tx1, tz1, new int[]{5, 4, 4}, RoofForm.GABLE, Direction.Axis.Z, (tx0 + tx1) / 2, wellWall);
		Hall t = tw.hall();
		int top = t.top;
		// the still-room: a lean-to against the flank away from the stair, its roof falling from the tower
		int sx0 = left ? 0 : tx1;
		int sx1 = left ? tx0 : w - 1;
		int[] link = null;
		boolean still = sx1 - sx0 >= 4;
		if (still) {
			int sz0 = tz0 + 2;
			Hall lean = Hall.of(d, sx0, sz0, sx1, tz1, 0, new int[]{4}, !left, left);
			int[] lin = lean.inner(0);
			lean.facade((lin[0] + lin[2]) / 2, 1, 1, 0.4);
			lean.ground(false).shell().openings(true);
			// a little gable of its own, ridge running back from the street (a shed roof fell to a door too low to pass)
			lean.roof(RoofForm.GABLE, Direction.Axis.Z, 1, null);
			if (lean.roof == null) {
				lean.crown();
			}
			clearInside(d, lean);
			int lz = (lin[1] + lin[3]) / 2;
			if (lean.door(0, left ? Dirs.RIGHT : Dirs.LEFT, lz, 1, false, false)) {
				int[] tin = t.inner(0);
				link = new int[]{left ? tin[0] : tin[2], lz};
			}
			Floorplan fp = lean.floor(0).blockHoles(d);
			fp.keep(lean.door != null ? lean.door : (lin[0] + lin[2]) / 2, lin[1]);
			if (link != null) {
				fp.keep(left ? lin[2] : lin[0], lz);
			}
			athanor(d, lean, fp, Dirs.BACK);
			top = Math.max(top, lean.top);
			if (lean.door != null) {
				path(d, ex, lean.door, sz0);
			}
		}
		Direction hearth = still ? null : (left ? Dirs.LEFT : Dirs.RIGHT);
		if (link != null) {
			furnish(d, tw, hearth, link);
		} else {
			furnish(d, tw, hearth);
		}
		path(d, ex, tw.door(), tz0);
		// a woodpile against the far flank, kitchen beds before the house, the fence along the street
		int fx = left ? tx1 + 1 : tx0 - 1;
		if (Works.in(d, fx, tz0 + 1) && d.isEmpty(fx, 0, tz0 + 1)) {
			Garden.woodpile(d, fx, 0, tz0 + 1, Dirs.BACK, Math.min(3, ts - 2));
		}
		List<Integer> paths = new ArrayList<>();
		paths.add(tw.door());
		frontGarden(d, w, tz0 - 1, paths, ex);
		Signs.bracket(d, t.side(0, Dirs.FRONT), t.side(0, Dirs.FRONT).index(tw.door() + (left ? 1 : -1)), 3, "alchemist_tower", "Alchemist");
		if (d.habits().snow()) {
			Garden.snowBanks(d, tx0, tz0, tx1, tz1, 0, tw.door());
		}
		return top;
	}

	// ------------------------------------------------------------------------------------------------ the walled laboratory

	private static int court(Draft d, int w, int dp, int ex, boolean left) {
		int ts = 7;
		int tx0 = left ? 1 : w - 1 - ts;
		int tx1 = tx0 + ts - 1;
		int tz1 = dp - 2;
		int tz0 = tz1 - ts + 1;
		Direction wellWall = left ? Dirs.LEFT : Dirs.RIGHT;
		Tower tw = tower(d, tx0, tz0, tx1, tz1, new int[]{4, 4, 4}, RoofForm.FLAT, Direction.Axis.X, (tx0 + tx1) / 2, wellWall);
		Hall t = tw.hall();
		int top = t.top;
		// the laboratory range along the back wall, against the tower's flank
		int rx0 = left ? tx1 : 1;
		int rx1 = left ? w - 2 : tx0;
		int rz0 = tz1 - 4;
		int[] link = null;
		Hall range = null;
		if (rx1 - rx0 >= 4) {
			range = Hall.of(d, rx0, rz0, rx1, tz1, 0, new int[]{4}, left, !left);
			int[] rin = range.inner(0);
			range.facade((rin[0] + rin[2]) / 2, 1, 1, 0.4);
			range.ground(false).shell().openings(true);
			range.roof(RoofForm.FLAT, Direction.Axis.X, 1, null);
			range.crown();
			clearInside(d, range);
			int lz = (rin[1] + rin[3]) / 2;
			if (range.door(0, left ? Dirs.LEFT : Dirs.RIGHT, lz, 1, false, false)) {
				int[] tin = t.inner(0);
				link = new int[]{left ? tin[2] : tin[0], lz};
			}
			Floorplan fp = range.floor(0).blockHoles(d);
			fp.keep(range.door != null ? range.door : (rin[0] + rin[2]) / 2, rin[1]);
			if (link != null) {
				fp.keep(left ? rin[0] : rin[2], lz);
			}
			athanor(d, range, fp, left ? Dirs.RIGHT : Dirs.LEFT);
			top = Math.max(top, range.top);
		}
		Direction hearth = range == null ? (left ? Dirs.RIGHT : Dirs.LEFT) : null;
		if (link != null) {
			furnish(d, tw, hearth, link);
		} else {
			furnish(d, tw, hearth);
		}
		// the court: the still under a reed shade by the range, the basin, jars, pots of herbs; paths from the gate
		int cz0 = 2;
		int cz1 = Math.min(tz0, rz0) - 2;
		path(d, ex, tw.door(), tz0);
		if (range != null && range.door != null) {
			path(d, ex, range.door, rz0);
		}
		int sx = left ? w - 4 : 1;
		if (cz1 - cz0 >= 2 && Adobe.ramada(d, sx, cz0, sx + 2, cz0 + 2, 0)) {
			still(d, sx + 1, cz0 + 2, Dirs.FRONT);
			if (d.isEmpty(sx, 0, cz0) && Math.abs(sx - ex) > 1) {
				Props.jars(d, sx, 0, cz0, Dirs.RIGHT, 1);
			}
		}
		int bx = left ? w - 3 : 2;
		int bz = Math.max(cz0, cz1);
		if (Works.in(d, bx, bz) && d.isEmpty(bx, 0, bz) && d.isEmpty(bx, 0, bz - 1) && Math.abs(bx - ex) > 1) {
			d.inStage(StageKind.EXTERIOR, () -> d.set(bx, 0, bz, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3)));
		}
		for (int x = 2; x <= w - 3; x += 3) {
			if (Math.abs(x - ex) > 1 && d.isEmpty(x, 0, 1) && d.isEmpty(x, 1, 1)) {
				Garden.pot(d, x, 0, 1);
			}
		}
		// the wall round the court, two high and coped, the gate at the street
		courtWall(d, w, dp, ex);
		return top;
	}

	/** The court's wall round the plot's edge, two courses and a coping, open at the gate (and where something stands). */
	private static void courtWall(Draft d, int w, int dp, int ex) {
		Mat wall = d.mat(Role.WALL);
		Mat cap = d.mat(Role.WALL_ACCENT).has(Form.SLAB) ? d.mat(Role.WALL_ACCENT) : wall;
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : Draft.ring(0, 0, w - 1, dp - 1)) {
				if (c[1] == 0 && Math.abs(c[0] - ex) <= 1) {
					continue;
				}
				if (!d.isEmpty(c[0], 0, c[1]) || !d.isEmpty(c[0], 1, c[1])) {
					continue;
				}
				d.set(c[0], -1, c[1], d.mat(Role.FOUNDATION).full());
				d.set(c[0], 0, c[1], wall.full());
				d.set(c[0], 1, c[1], wall.full());
				d.set(c[0], 2, c[1], cap.has(Form.SLAB) ? cap.slab(SlabType.BOTTOM) : cap.full());
			}
		}
		// the gate's posts and lamps
		for (int x : new int[]{ex - 2, ex + 2}) {
			if (Works.in(d, x, 0) && d.isSolid(x, 1, 0)) {
				Lighting.lamp(d, x, 3, 0);
			}
		}
	}

	/** A still in the open: a stand on a barrel worked from {@code user}'s side, the scholar's place. */
	private static void still(Draft d, int x, int z, Direction user) {
		if (!d.isEmpty(x, 0, z) || !d.isEmpty(x + Dirs.dx(user), 0, z + Dirs.dz(user))) {
			return;
		}
		d.inStage(StageKind.EXTERIOR, () -> d.set(x, 0, z, States.facing(Blocks.BARREL, user)));
		Studies.brewingStand(d, x, 1, z);
		d.poi(PoiType.JOB_SITE, x + Dirs.dx(user), 0, z + Dirs.dz(user), user.getOpposite(), JobType.SCHOLAR);
	}

	// ------------------------------------------------------------------------------------------------ the pavilion tower

	private static int pavilion(Draft d, int w, int dp, int ex, boolean left) {
		int ts = 7;
		int tx0 = left ? 0 : w - ts;
		int tx1 = tx0 + ts - 1;
		int tz1 = dp - 1;
		int tz0 = dp - ts;
		Direction wellWall = left ? Dirs.LEFT : Dirs.RIGHT;
		RoofForm form = Hall.pick(d, RoofForm.PAGODA, RoofForm.HIP);
		Tower tw = tower(d, tx0, tz0, tx1, tz1, new int[]{4, 4, 4}, form, Direction.Axis.X, (tx0 + tx1) / 2, wellWall);
		Hall t = tw.hall();
		furnish(d, tw, null);
		int top = t.top;
		// the elixir furnace under an open shed before the tower on the other side: a lit furnace on a stone hearth, the
		// cauldron over its fire, a bench with the still
		int sx0 = left ? tx1 + 2 : 1;
		int sx1 = left ? w - 2 : tx0 - 2;
		int sz0 = 2;
		int sz1 = Math.min(sz0 + 3, tz0 - 1);
		if (sx1 - sx0 >= 3 && sz1 - sz0 >= 2) {
			Shed.open(d, sx0, sz0, sx1, sz1, 0, 3, Hall.pick(d, RoofForm.PAGODA, RoofForm.HIP), Direction.Axis.X, 1, Dirs.BACK);
			int fx = left ? sx1 - 1 : sx0 + 1;
			int fz = sz1 - 1;
			Direction into = Dirs.FRONT;
			if (d.isEmpty(fx, 0, fz) && d.isEmpty(fx, 0, fz - 1)) {
				d.inStage(StageKind.INTERIOR, () -> d.set(fx, 0, fz, States.facing(Blocks.BLAST_FURNACE, into).trySetValue(BlockStateProperties.LIT, true)));
				d.poi(PoiType.JOB_SITE, fx, 0, fz - 1, Dirs.BACK, JobType.SCHOLAR);
			}
			int cx = fx + (left ? -1 : 1);
			if (d.isEmpty(cx, 0, fz) && d.isEmpty(cx, 0, fz - 1)) {
				d.inStage(StageKind.INTERIOR, () -> d.set(cx, 0, fz, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 2)));
			}
			int bx = cx + (left ? -1 : 1);
			if (d.isEmpty(bx, 0, fz) && d.isEmpty(bx, 0, fz - 1)) {
				still(d, bx, fz, Dirs.FRONT);
			}
			int lx = (sx0 + sx1) / 2;
			Props.hangLantern(d, lx, 3, sz0 + 1);
			top = Math.max(top, 6);
		}
		// a little pool among stones in the corner by the street
		int px = left ? w - 3 : 1;
		if (Works.in(d, px + 1, 1) && Math.abs(px - ex) > 2 && Math.abs(px + 1 - ex) > 2) {
			try (var s = d.stage(StageKind.EXTERIOR)) {
				for (int x = px; x <= px + 1; x++) {
					d.set(x, -1, 1, Blocks.WATER.defaultBlockState());
					d.set(x, -2, 1, d.mat(Role.FOUNDATION).full());
				}
			}
			for (int[] c : new int[][]{{px - 1, 1}, {px + 2, 1}, {px, 2}}) {
				if (Works.in(d, c[0], c[1]) && d.isEmpty(c[0], 0, c[1])) {
					d.inStage(StageKind.EXTERIOR, () -> d.set(c[0], 0, c[1], Blocks.MOSSY_COBBLESTONE_SLAB.defaultBlockState()));
				}
			}
		}
		path(d, ex, tw.door(), tz0);
		List<int[]> gaps = new ArrayList<>();
		gaps.add(new int[]{ex, 0});
		gaps.add(new int[]{tw.door(), 0});
		Yard.enclose(d, 0, 0, w - 1, 0, gaps, Dirs.BACK, Dirs.LEFT, Dirs.RIGHT);
		Works.lampPost(d, ex + 1 < w - 1 ? ex + 1 : ex - 1, 0, 1);
		Signs.bracket(d, t.side(0, Dirs.FRONT), t.side(0, Dirs.FRONT).index(tw.door() + (left ? 1 : -1)), 3, "alchemist_tower", "Alchemist");
		return top;
	}
}
