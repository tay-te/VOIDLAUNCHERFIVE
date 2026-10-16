package dev.voidmc.hearth.build.archetype.civic;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.civic.Halls;
import dev.voidmc.hearth.build.kit.civic.Towers;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.landmark.Details;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.kit.landscape.Benches;
import dev.voidmc.hearth.build.kit.learning.Sacra;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:civic/shrine}: the little holy place on the roads into a settlement, where folk stop to pray on their
 * way, built as each culture builds its wayside shrines:
 * <ul>
 *   <li><b>temperate, wisteria</b> (Bohemia): the <i>boží muka</i>, a slender limed or stone pillar on a stepped base
 *   carrying a little niche with a lamp under a gable and an iron cross; or the niche chapel, a white rendered
 *   block with quoins under a steep gable, the image in an arched niche lit by candles and a hanging lamp, an iron
 *   cross on the gable;</li>
 *   <li><b>alpine</b>: the <i>Wegkreuz</i>, a timber crucifix under its own little shingled roof on a stone socket, with
 *   a bench beside it; or the whitewashed <i>Bildstock</i> pillar;</li>
 *   <li><b>northern, tundra</b>: the roofed worship cross of the north (the three-barred cross on the tundra), on a
 *   cairn of fieldstones;</li>
 *   <li><b>moorland, maritime</b>: a Celtic high cross of dressed stone (ringed where there is width) on a stepped base;
 *   or a holy well under a corbelled stone hood with a cross on top;</li>
 *   <li><b>mediterranean, bayou</b>: the <i>edicola</i>, a whitewashed shrine pillar with an arched niche (glazed tiles
 *   behind the image) under a low tiled hip roof, a cypress beside it;</li>
 *   <li><b>desert</b>: the <i>qubba</i>, a white cube on a plinth with a niche and a little dome; <b>mesa</b>: an adobe
 *   <i>capilla</i> with a stepped bell wall, its bell hung in the opening, a timber cross over it;</li>
 *   <li><b>steppe</b>: an earthen stupa on a square terrace, a spire of mud brick and timber stakes (toron) standing out
 *   of the dome in the manner of the Sahel;</li>
 *   <li><b>karst</b>: a gate of two red posts and a double lintel with lifted ends before a small shrine house on a
 *   stone base, stone lanterns either side;</li>
 *   <li><b>tropical</b>: a spirit house, a little roofed shrine on a pedestal holding up a platform of offerings.</li>
 * </ul>
 * Every shrine levels its pad to the road, lights itself (a lamp in the niche, candles, stone lanterns), and puts flowers
 * and candles out as offerings.
 *
 * <p>POIs: ALTAR where folk stand to pray (in front of the shrine, facing it), DOOR at the way in, SEAT on the bench
 * where there is one.
 *
 * <p>Params: {@code form} ({@code "pillar"}, {@code "niche"}, {@code "cross"}, {@code "well"}: the form where a culture
 * has two; default by seed and plot), {@code variant} (int).
 */
public final class Shrine implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("civic/shrine"), ArchetypeCategory.CIVIC, Set.of(), 0, 3, 5, 3, 5, 6, 10,
		SettlementTier.HAMLET, Set.of(), Set.of(), List.of(), 0, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		// the pad levelled to the road (a bank embanked): a shrine stands where folk can step off the way to it
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		Tradition t = Tradition.of(d);
		Culture c = d.style().culture();
		String form = d.param("form", "");
		int top;
		switch (t) {
			case TIMBER -> top = form.equals("niche") || !form.equals("pillar") && d.chance(0.45) ? nicheChapel(d, false) : bozimuka(d);
			case ALPINE -> top = form.equals("pillar") || !form.equals("cross") && d.chance(0.35) ? pillar(d, true) : wayCross(d, false);
			case LOG -> top = wayCross(d, c == Culture.TUNDRA);
			case STONE -> top = form.equals("well") || !form.equals("cross") && d.chance(c == Culture.MARITIME ? 0.5 : 0.3) ? holyWell(d) : highCross(d);
			case MEDITERRANEAN -> top = edicola(d);
			case ADOBE -> top = c == Culture.MESA ? capilla(d) : qubba(d);
			case STEPPE -> top = stupa(d);
			case KARST -> top = toriiShrine(d);
			case STILT -> top = c == Culture.TROPICAL ? spiritHouse(d) : edicola(d);
			default -> top = pillar(d, false);
		}
		dev.voidmc.hearth.build.kit.civic.Halls.finish(d);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, top + 2));
	}

	// ------------------------------------------------------------------------------------------------ shared

	/** The column the shrine stands on: the middle (the left of the two middles on an even frontage). */
	private static int mid(Draft d) {
		return (d.frontage() - 1) / 2;
	}

	/**
	 * Where folk stand to pray: (x, 0, z) in front of the shrine, facing it (toward the back). Registers the ALTAR and the
	 * way in from the road, paves the way from the front row to it and puts a lamp beside it when nothing else lights it.
	 */
	private static void prayer(Draft d, int x, int z) {
		prayer(d, x, 0, z);
	}

	/** {@link #prayer(Draft, int, int)} standing at level y (on a terrace). */
	private static void prayer(Draft d, int x, int y, int z) {
		if (z < 0) {
			// the shrine fills the plot: folk pray from the road before it
			d.entrance(x, y, -1);
			d.poi(PoiType.ALTAR, x, y, -1, Dirs.BACK);
			return;
		}
		Mat path = d.mat(Role.PATH);
		// the way to the prayer spot through any parapet the levelling raised on a bank (a river's edge)
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int zz = 0; zz < z; zz++) {
				BlockState at = d.get(x, y, zz);
				if (at != null && !at.isAir() && at.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,
					net.minecraft.core.BlockPos.ZERO)) {
					d.set(x, y, zz, Blocks.AIR.defaultBlockState());
					d.set(x, y + 1, zz, Blocks.AIR.defaultBlockState());
					d.set(x, y - 1, zz, path.full());
				}
			}
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int zz = 0; zz <= z; zz++) {
				if (Groundwork.surface(d, x, zz) == 0 && d.isEmpty(x, 0, zz) && d.isEmpty(x, 1, zz) && !d.site().isWater(x, zz)) {
					d.set(x, -1, zz, path.full());
				}
			}
		}
		d.entrance(x, y, 0);
		d.poi(PoiType.DOOR, x, y, 0, Dirs.BACK);
		d.poi(PoiType.ALTAR, x, y, z, Dirs.BACK);
		// where the pad stands off the road (a bank, a terrace), steps down to it
		Groundwork.approach(d, x, y, z);
	}

	/** A foundation column under (x, z) from the ground to the floor block row (y = -1). */
	private static void socket(Draft d, int x, int z) {
		Mat found = d.mat(Role.FOUNDATION);
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int y = Math.min(-1, d.site().ground(x, z) - 2); y <= -1; y++) {
				d.set(x, y, z, found.full());
			}
		}
	}

	/** The plinth stone: the style's plinth where it is masonry, else its footing stone. */
	private static Stone plinth(Draft d) {
		return Stone.stony(d, Role.PLINTH) ? Stone.of(d, Role.PLINTH) : Stone.footing(d);
	}

	/** The walls' render, or masonry where the style's walls are not masonry (logs, boards). */
	private static Stone render(Draft d) {
		return Stone.stony(d, Role.WALL) ? Stone.of(d, Role.WALL) : Stone.masonry(d);
	}

	/** Flowers either side of the prayer spot on the ground where it is free. */
	private static void offerings(Draft d, int x, int z) {
		for (int k : new int[]{-1, 1}) {
			int fx = x + k;
			if (fx >= 0 && fx < d.frontage() && Groundwork.surface(d, fx, z) == 0 && d.isEmpty(fx, 0, z) && !d.site().isWater(fx, z)
				&& d.isEmpty(fx, 1, z)) {
				Garden.flower(d, fx, 0, z);
			}
		}
	}

	/** A bench along the side of the plot beside the shrine (frontage 5 only), looking at the road. */
	private static void bench(Draft d, int x, int z, Direction looks) {
		if (x < 0 || x >= d.frontage() || z < 0 || z >= d.depth() || !d.isEmpty(x, 0, z) || Groundwork.surface(d, x, z) != 0) {
			return;
		}
		Benches.bench(d, x, 0, z, looks, Dirs.FRONT, 1, false);
	}

	// ------------------------------------------------------------------------------------------------ the pillar

	/**
	 * The <i>boží muka</i> (or, alpine, the <i>Bildstock</i>): a step of the plinth round a socket stone, a rendered shaft,
	 * a ring of corbels carrying a head of niches three square (a lamp in the front niche, a pot and candles in the side
	 * ones), a dressed band, a little pyramid of the roof and an iron cross. Returns the top row.
	 */
	private static int pillar(Draft d, boolean alpine) {
		int w = d.frontage();
		int dp = d.depth();
		int cx = mid(d);
		int cz = dp - 2;
		int maxTop = d.maxHeight() - 1;
		Stone base = plinth(d);
		Stone body = render(d);
		Stone dress = Stone.dressing(d);
		// rows: the step (0), the shaft (1..s), corbels (s + 1), the niche row (s + 2), the band (s + 3), the roof (s + 4),
		// its cap or the cross over it
		int shaft = maxTop >= 10 ? 2 : 1;
		boolean cross = maxTop >= shaft + 8;
		int yc = shaft + 1;
		int yn = shaft + 2;
		int yb = shaft + 3;
		int yr = shaft + 4;
		boolean step = cz - 2 >= 0 && w >= 3;
		for (int z = cz - 1; z <= cz + 1; z++) {
			for (int x = cx - 1; x <= cx + 1; x++) {
				if (x >= 0 && x < w && z >= 0 && z < dp) {
					socket(d, x, z);
				}
			}
		}
		try (var s = d.stage(StageKind.WALLS)) {
			// the step round the socket: stairs climbing in, mitred at the corners by the shape resolver
			if (step) {
				for (int z = cz - 1; z <= cz + 1; z++) {
					for (int x = cx - 1; x <= cx + 1; x++) {
						if (x == cx && z == cz || x < 0 || x >= w) {
							continue;
						}
						Direction in = Dirs.nearest(cx - x, cz - z);
						d.set(x, 0, z, base.stairsOrSlab(in, Half.BOTTOM));
					}
				}
			}
			d.set(cx, 0, cz, base.full());
			for (int y = 1; y <= shaft; y++) {
				d.set(cx, y, cz, body.full());
			}
			// the corbels: upside-down stairs round the shaft's head, the sill of every niche
			for (int z = cz - 1; z <= cz + 1; z++) {
				for (int x = cx - 1; x <= cx + 1; x++) {
					if (x == cx && z == cz) {
						d.set(x, yc, z, dress.full());
						continue;
					}
					if (x < 0 || x >= w) {
						continue;
					}
					Direction out = Dirs.nearest(x - cx, z - cz);
					d.set(x, yc, z, dress.stairsOrSlab(out.getOpposite(), Half.TOP));
				}
			}
			// the head: rendered corners round a core, a niche in the middle of each face
			for (int z = cz - 1; z <= cz + 1; z++) {
				for (int x = cx - 1; x <= cx + 1; x++) {
					if (x < 0 || x >= w) {
						continue;
					}
					boolean corner = x != cx && z != cz;
					if (corner || x == cx && z == cz) {
						d.set(x, yn, z, body.full());
					}
					d.set(x, yb, z, x == cx || z == cz ? dress.full() : body.full());
				}
			}
		}
		// what stands in the niches: the lamp in front, a pot and candles at the sides, the back left blind
		Sacra.standingLantern(d, cx, yn, cz - 1, StageKind.EXTERIOR);
		if (cx - 1 >= 0) {
			Sacra.pot(d, cx - 1, yn, cz);
		}
		if (cx + 1 < w) {
			Sacra.candles(d, cx + 1, yn, cz, 2, StageKind.EXTERIOR);
		}
		try (var s = d.stage(StageKind.WALLS)) {
			d.set(cx, yn, cz + 1, body.full());
		}
		// the little roof: a pyramid of the roof's stairs on the band, a cap in the middle
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = cz - 1; z <= cz + 1; z++) {
				for (int x = cx - 1; x <= cx + 1; x++) {
					if (x < 0 || x >= w || x == cx && z == cz) {
						continue;
					}
					Direction in = Dirs.nearest(cx - x, cz - z);
					d.set(x, yr, z, Sacra.roofStair(d, in, Half.BOTTOM));
				}
			}
			d.set(cx, yr, cz, d.mat(Role.ROOF).full());
		}
		int top = yr;
		if (cross) {
			top = Sacra.woodCross(d, cx, yr + 1, cz, Direction.Axis.X);
		} else if (yr + 1 <= maxTop) {
			d.inStage(StageKind.ROOF, () -> d.set(cx, yr + 1, cz, Sacra.capSlab(d)));
			top = yr + 1;
		}
		// the praying spot: before the step, or (no room for one) in the shadow of the front corbel
		int pz = Math.max(0, step ? cz - 2 : cz - 1);
		prayer(d, cx, pz);
		offerings(d, cx, pz);
		// a bench beside it where there is room (the mountains' Marterl has one), else flowers
		if (w >= 5 && dp >= 4) {
			if (alpine || d.chance(0.5)) {
				bench(d, cx + 2, cz, Dirs.LEFT);
				bench(d, cx + 2, cz - 1, Dirs.LEFT);
			} else {
				for (int z = 0; z < dp; z++) {
					Sacra.flower(d, cx + 2, 0, z);
				}
			}
		}
		return Math.max(top, yr);
	}

	// ------------------------------------------------------------------------------------------------ the boží muka

	/**
	 * The Bohemian <i>boží muka</i>: a slender pillar, limed or in bare stone, on a stepped plinth, a dressed band corbelled
	 * out under its head, the head a little niche (a lamp in it, a back wall behind) under a gable of the roof's stairs a
	 * block wider than the shaft either side, a wrought-iron cross on the ridge. Returns the top row.
	 */
	private static int bozimuka(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int cx = mid(d);
		int cz = dp - 2;
		int maxTop = d.maxHeight() - 1;
		Stone base = plinth(d);
		Stone body = dev.voidmc.hearth.build.kit.civic.Bohemian.lime(d);
		Stone dress = Stone.dressing(d);
		// rows: the step (0), the shaft (1..s), the band (s + 1), the niche (s + 2), the gable (s + 3), the ridge (s + 4),
		// the cross over it
		int shaft = Math.clamp(maxTop - 7, 1, 3);
		int yb = shaft + 1;
		int yn = shaft + 2;
		int yg = shaft + 3;
		int yr = shaft + 4;
		boolean step = cz - 2 >= 0 && w >= 3;
		socket(d, cx, cz);
		socket(d, cx, cz + 1);
		try (var s = d.stage(StageKind.WALLS)) {
			if (step) {
				for (int z = cz - 1; z <= cz + 1; z++) {
					for (int x = cx - 1; x <= cx + 1; x++) {
						if (x == cx && z == cz || x < 0 || x >= w) {
							continue;
						}
						if (z == cz + 1 && x != cx) {
							continue;
						}
						Direction in = Dirs.nearest(cx - x, cz - z);
						d.set(x, 0, z, base.stairsOrSlab(in, Half.BOTTOM));
					}
				}
			}
			d.set(cx, 0, cz, base.full());
			for (int y = 1; y <= shaft; y++) {
				d.set(cx, y, cz, body.full());
			}
			// the band: a dressed block on the shaft, a sill corbelled out before the niche and a corbel carrying the back
			d.set(cx, yb, cz, dress.full());
			d.set(cx, yb, cz - 1, dress.stairsOrSlab(Dirs.BACK, Half.TOP));
			d.set(cx, yb, cz + 1, dress.stairsOrSlab(Dirs.FRONT, Half.TOP));
			// the niche's back wall, and the gable's head over the niche
			d.set(cx, yn, cz + 1, body.full());
			d.set(cx, yg, cz, body.full());
			d.set(cx, yg, cz + 1, body.full());
		}
		// the little roof: stairs either side of the gable's head, a block wider than the shaft, the ridge capped
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = cz; z <= cz + 1; z++) {
				if (cx - 1 >= 0) {
					d.set(cx - 1, yg, z, Sacra.roofStair(d, Dirs.RIGHT, Half.BOTTOM));
				}
				if (cx + 1 < w) {
					d.set(cx + 1, yg, z, Sacra.roofStair(d, Dirs.LEFT, Half.BOTTOM));
				}
				if (yr <= maxTop) {
					d.set(cx, yr, z, Sacra.capSlab(d));
				}
			}
		}
		// the lamp in the niche, candles on the sill before it
		Sacra.standingLantern(d, cx, yn, cz, StageKind.EXTERIOR);
		Sacra.candles(d, cx, yb + 1, cz - 1, 2, StageKind.EXTERIOR);
		int top = Math.min(yr, maxTop);
		if (yr + 3 <= maxTop) {
			top = Sacra.ironCross(d, cx, yr + 1, cz, Direction.Axis.X);
		}
		int pz = Math.max(0, step ? cz - 2 : cz - 1);
		prayer(d, cx, pz);
		offerings(d, cx, pz);
		// a bench beside it where there is room, or flowers along the plot's side
		if (w >= 5 && dp >= 4) {
			if (d.chance(0.5)) {
				bench(d, cx + 2, cz, Dirs.LEFT);
				bench(d, cx + 2, cz - 1, Dirs.LEFT);
			} else {
				for (int z = 0; z < dp; z++) {
					Sacra.flower(d, cx + 2, 0, z);
				}
			}
		}
		return top;
	}

	// ------------------------------------------------------------------------------------------------ the niche chapel

	/**
	 * The niche chapel (<i>výklenková kaple</i>): a block three wide, rendered between dressed quoins over a stone plinth,
	 * under a gable of the roof whose end faces the road (eaves over the sides where the plot has room, a lip over the
	 * front); the image in a niche with candles under a hanging lamp, a wrought-iron cross on the gable. Returns the top row.
	 */
	private static int nicheChapel(Draft d, boolean alpine) {
		int w = d.frontage();
		int dp = d.depth();
		int cx = mid(d);
		int bx0 = cx - 1;
		int bx1 = cx + 1;
		int depth = dp >= 5 ? 3 : 2;
		int bz0 = dp - depth;
		int bz1 = dp - 1;
		int maxTop = d.maxHeight() - 1;
		Stone base = plinth(d);
		Stone body = render(d);
		Stone dress = Stone.dressing(d);
		Mat roofM = d.mat(Role.ROOF);
		int eave = 4;
		boolean wide = w >= 5;
		int ez0 = Math.max(0, bz0 - 1);
		for (int z = bz0; z <= bz1; z++) {
			for (int x = bx0; x <= bx1; x++) {
				socket(d, x, z);
			}
		}
		// the walls: plinth, render, quoins at the corners every other course, a dressed band at the eaves in front, and
		// the gable's triangle (the middle column carried a course higher)
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = bz0; z <= bz1; z++) {
				for (int x = bx0; x <= bx1; x++) {
					int top = x == cx ? eave + 1 : eave;
					for (int y = 0; y <= top; y++) {
						boolean corner = (x == bx0 || x == bx1) && (z == bz0 || z == bz1);
						Paint p = y == 0 ? base.full() : corner && (y & 1) == 1 && y < eave ? dress.full() : body.full();
						d.set(x, y, z, p);
					}
				}
			}
			// the niche: a sill, two rows of opening, a relief at its back, a dressed head
			d.set(cx, 1, bz0, dress.full());
			d.set(cx, 2, bz0, Blocks.AIR.defaultBlockState());
			d.set(cx, 3, bz0, Blocks.AIR.defaultBlockState());
			d.set(cx, 2, bz0 + 1, d.mat(Role.ACCENT).full());
			d.set(cx, 3, bz0 + 1, d.mat(Role.ACCENT).full());
			d.set(cx, 4, bz0, dress.full());
		}
		// the roof: stairs climbing to the middle over each wall (and an eave course a row lower outside it on a wide
		// plot), the ridge capped, running from a lip over the front to the back
		int ridge = eave + 2;
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = ez0; z <= bz1; z++) {
				if (wide) {
					d.set(bx0 - 1, eave, z, Sacra.roofStair(d, Dirs.RIGHT, Half.BOTTOM));
					d.set(bx1 + 1, eave, z, Sacra.roofStair(d, Dirs.LEFT, Half.BOTTOM));
				}
				d.set(bx0, eave + 1, z, Sacra.roofStair(d, Dirs.RIGHT, Half.BOTTOM));
				d.set(bx1, eave + 1, z, Sacra.roofStair(d, Dirs.LEFT, Half.BOTTOM));
				if (z < bz0) {
					// the lip over the front: the gable's head carried out on a beam
					d.set(cx, eave + 1, z, d.mat(Role.BEAM).log(Direction.Axis.Z));
				}
				d.set(cx, ridge, z, Sacra.roofStair(d, Dirs.RIGHT, Half.BOTTOM));
				d.set(cx, ridge, z, roofM.has(Form.SLAB) ? roofM.slab(SlabType.BOTTOM) : Sacra.capSlab(d));
			}
		}
		int top = ridge;
		if (ridge + 3 <= maxTop) {
			top = Sacra.ironCross(d, cx, ridge + 1, ez0, Direction.Axis.X);
		}
		Sacra.candles(d, cx, 2, bz0, 3, StageKind.EXTERIOR);
		Sacra.hangingLantern(d, cx, 3, bz0, StageKind.EXTERIOR);
		int pz = Math.max(0, bz0 - 1);
		prayer(d, cx, pz);
		// pots at the foot of the walls either side of the kneeling spot
		for (int k : new int[]{-1, 1}) {
			int x = cx + k;
			if (pz >= 0 && d.isEmpty(x, 0, pz) && Groundwork.surface(d, x, pz) == 0) {
				try (var s = d.stage(StageKind.EXTERIOR)) {
					d.set(x, 0, pz, Blocks.DECORATED_POT.defaultBlockState());
				}
			}
		}
		if (wide) {
			// a lime tree behind on one side, flowers along the other
			int tx = d.chance(0.5) ? 0 : w - 1;
			for (int z = 0; z < bz0 - 1; z++) {
				Sacra.flower(d, w - 1 - tx, 0, z);
			}
			if (dp >= 4 && bz0 >= 2) {
				Sacra.flower(d, tx, 0, 0);
			}
		}
		return top;
	}

	// ------------------------------------------------------------------------------------------------ the wayside cross

	/**
	 * A timber cross under its own little roof on a stone socket: the alpine <i>Wegkreuz</i>, the roofed worship cross of
	 * the north, and on the tundra the three-barred cross. The roof is a small gable whose ridge runs along the arms, its
	 * eaves before and behind the crossing, so the arms show under it; a lamp hangs under the front eave, a cairn of
	 * fieldstones lies round the foot in the north. Returns the top row.
	 */
	private static int wayCross(Draft d, boolean threeBar) {
		int w = d.frontage();
		int dp = d.depth();
		int cx = mid(d);
		int cz = Math.max(1, dp - 2);
		int maxTop = d.maxHeight() - 1;
		Mat frame = d.mat(Role.FRAME);
		Stone base = Stone.footing(d);
		// rows: the socket (0), the post to postTop, the arms under the head, the roof's eaves at postTop, its ridge over
		int postTop = Math.min(threeBar ? 6 : 5, maxTop - 2);
		socket(d, cx, cz);
		try (var s = d.stage(StageKind.WALLS)) {
			d.set(cx, 0, cz, base.full());
		}
		Paint post = frame.log(Direction.Axis.Y);
		Paint arm = Sacra.fence(d);
		if (threeBar) {
			Sacra.northernCross(d, cx, 1, cz, postTop, Direction.Axis.X, post, arm);
		} else {
			try (var s = d.stage(StageKind.FRAME)) {
				for (int y = 1; y <= postTop; y++) {
					d.set(cx, y, cz, post);
				}
				for (int k : new int[]{-1, 1}) {
					if (cx + k >= 0 && cx + k < w) {
						d.set(cx + k, postTop - 2, cz, d.mat(Role.BEAM).log(Direction.Axis.X));
					}
				}
			}
		}
		// the roof: eaves before and behind the post, the ridge over it along the arms
		int ye = postTop;
		try (var s = d.stage(StageKind.ROOF)) {
			for (int x = cx - 1; x <= cx + 1; x++) {
				if (x < 0 || x >= w) {
					continue;
				}
				if (cz - 1 >= 0) {
					d.set(x, ye, cz - 1, Sacra.roofStair(d, Dirs.BACK, Half.BOTTOM));
				}
				if (cz + 1 < dp) {
					d.set(x, ye, cz + 1, Sacra.roofStair(d, Dirs.FRONT, Half.BOTTOM));
				}
				if (x != cx) {
					d.set(x, ye, cz, d.mat(Role.BEAM).log(Direction.Axis.X));
				}
				if (ye + 1 <= maxTop) {
					d.set(x, ye + 1, cz, Sacra.capSlab(d));
				}
			}
		}
		boolean north = d.style().culture() == Culture.NORTHERN || d.style().culture() == Culture.TUNDRA;
		if (north) {
			// a cairn of fieldstones round the foot, candles set on its stones
			Stone cairn = Stone.fixed(d, StageKind.WALLS, "minecraft:mossy_cobblestone", "minecraft:cobblestone");
			try (var s = d.stage(StageKind.WALLS)) {
				for (int[] o : new int[][]{{-1, 0}, {1, 0}, {0, 1}, {-1, 1}, {1, 1}}) {
					int x = cx + o[0];
					int z = cz + o[1];
					if (x >= 0 && x < w && z < dp && d.isEmpty(x, 0, z)) {
						socket(d, x, z);
						d.set(x, 0, z, o[1] == 0 ? cairn.stairsOrSlab(o[0] < 0 ? Dirs.RIGHT : Dirs.LEFT, Half.BOTTOM) : cairn.full());
					}
				}
			}
			if (cz + 1 < dp) {
				Sacra.candles(d, cx - 1 >= 0 ? cx - 1 : cx + 1, 1, cz + 1, 3, StageKind.EXTERIOR);
			}
		} else if (cz + 1 < dp) {
			Sacra.pot(d, cx, 0, cz + 1);
		}
		// the lamp on a stone beside the foot (on the cairn in the north)
		int lx = cx + 1 < w ? cx + 1 : cx - 1;
		if (lx >= 0 && lx < w) {
			socket(d, lx, cz);
			try (var s = d.stage(StageKind.WALLS)) {
				d.set(lx, 0, cz, base.full());
			}
			Sacra.standingLantern(d, lx, 1, cz, StageKind.EXTERIOR);
		}
		int pz = cz - 2 >= 0 ? cz - 2 : Math.max(0, cz - 1);
		prayer(d, cx, pz);
		offerings(d, cx, pz);
		if (w >= 5 && !north) {
			bench(d, cx + 2, cz, Dirs.LEFT);
			bench(d, cx + 2, cz + 1, Dirs.LEFT);
		}
		return Math.min(maxTop, ye + 1);
	}

	// ------------------------------------------------------------------------------------------------ stone crosses and wells

	/** A Celtic high cross on a stepped base of dressed stone, ringed where the plot is five wide. Returns the top row. */
	private static int highCross(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int cx = mid(d);
		int cz = Math.max(1, dp - 2);
		int maxTop = d.maxHeight() - 1;
		Stone dress = Stone.dressing(d);
		Stone base = plinth(d);
		int reach = w >= 5 && maxTop >= 8 ? 2 : 1;
		for (int z = cz - 1; z <= Math.min(dp - 1, cz + 1); z++) {
			for (int x = cx - 1; x <= cx + 1; x++) {
				if (x >= 0 && x < w && z >= 0) {
					socket(d, x, z);
				}
			}
		}
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = cz - 1; z <= Math.min(dp - 1, cz + 1); z++) {
				for (int x = cx - 1; x <= cx + 1; x++) {
					if (x < 0 || x >= w || z < 0 || x == cx && z == cz) {
						continue;
					}
					d.set(x, 0, z, base.stairsOrSlab(Dirs.nearest(cx - x, cz - z), Half.BOTTOM));
				}
			}
			d.set(cx, 0, cz, base.full());
		}
		int shaft = Math.max(2, Math.min(4, maxTop - 3 - (reach >= 2 ? 1 : 0)));
		int top = Sacra.highCross(d, cx, 1, cz, Direction.Axis.X, shaft, reach, dress);
		// the base's back row a ledge of whole stones with candles on it; lamps on posts where there is width
		if (cz + 1 < dp) {
			try (var s = d.stage(StageKind.WALLS)) {
				for (int x = cx - 1; x <= cx + 1; x++) {
					if (x >= 0 && x < w) {
						d.set(x, 0, cz + 1, base.full());
					}
				}
			}
			for (int x : new int[]{cx - 1, cx + 1}) {
				if (x >= 0 && x < w) {
					Sacra.candles(d, x, 1, cz + 1, 2, StageKind.EXTERIOR);
				}
			}
		}
		if (w >= 5) {
			Halls.lamp(d, 0, 0, Math.max(0, cz - 1), 1);
			Halls.lamp(d, w - 1, 0, Math.max(0, cz - 1), 1);
		} else if (reach == 1 && cx - 1 >= 0) {
			Sacra.hangingLantern(d, cx - 1, 1 + shaft - 1, cz, StageKind.EXTERIOR);
		}
		int pz = Math.max(0, cz - 2);
		prayer(d, cx, pz);
		offerings(d, cx, pz);
		return top;
	}

	/**
	 * A holy well under a corbelled hood of rubble: the spring in a stone basin, the hood three square drawing in course
	 * by course to a capstone with a cross, an opening toward the road with a dressed lintel, candles on its ledge.
	 * Returns the top row.
	 */
	private static int holyWell(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int cx = mid(d);
		int cz = Math.max(1, dp - 2);
		int maxTop = d.maxHeight() - 1;
		Stone rubble = Stone.footing(d);
		Stone dress = Stone.dressing(d);
		for (int z = cz - 1; z <= Math.min(dp - 1, cz + 1); z++) {
			for (int x = cx - 1; x <= cx + 1; x++) {
				if (x >= 0 && x < w && z >= 0) {
					socket(d, x, z);
				}
			}
		}
		try (var s = d.stage(StageKind.WALLS)) {
			// the basin: the spring a block down, held in stone
			d.set(cx, -2, cz, rubble.full());
			d.set(cx, -1, cz, Blocks.WATER.defaultBlockState());
			for (int z = cz - 1; z <= Math.min(dp - 1, cz + 1); z++) {
				for (int x = cx - 1; x <= cx + 1; x++) {
					if (x < 0 || x >= w || z < 0 || x == cx && z == cz) {
						continue;
					}
					boolean door = x == cx && z == cz - 1;
					for (int y = 0; y <= 1; y++) {
						d.set(x, y, z, door ? Paint.of(Blocks.AIR.defaultBlockState()) : rubble.full());
					}
					// the corbelled courses: each a ring of stairs leaning in
					d.set(x, 2, z, door ? dress.full() : rubble.stairsOrSlab(Dirs.nearest(cx - x, cz - z), Half.BOTTOM));
				}
			}
			d.set(cx, 0, cz, Blocks.AIR.defaultBlockState());
			d.set(cx, 1, cz, Blocks.AIR.defaultBlockState());
			d.set(cx, 2, cz, rubble.full());
			d.set(cx, 3, cz, rubble.full());
		}
		// the lamp hung inside over the water, candles on the lintel's ledge
		Sacra.hangingLantern(d, cx, 1, cz, StageKind.EXTERIOR);
		int top = 3;
		if (maxTop >= 7) {
			top = Sacra.cross(d, cx, 4, cz, Direction.Axis.X, dress.wallOrFull(), dress.wallOrFull(), 3);
		}
		int pz = Math.max(0, cz - 2);
		prayer(d, cx, pz);
		offerings(d, cx, pz);
		if (w >= 5) {
			Halls.lamp(d, 0, 0, Math.max(0, cz - 1), 1);
		}
		return top;
	}

	// ------------------------------------------------------------------------------------------------ the south

	/**
	 * The <i>edicola</i> (and the bayou's whitewashed grotto): a pillar three wide and two deep on a plinth, an arched
	 * niche with glazed tiles behind the image, candles and a hanging lamp in it, a low hip of the roof over a moulded
	 * cornice, a cypress beside where there is room. Returns the top row.
	 */
	private static int edicola(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int cx = mid(d);
		int bx0 = cx - 1;
		int bx1 = cx + 1;
		int bz0 = dp - 2;
		int bz1 = dp - 1;
		int maxTop = d.maxHeight() - 1;
		Stone base = plinth(d);
		Stone body = render(d);
		Stone dress = Stone.dressing(d);
		Mat accent = d.mat(Role.ACCENT);
		int head = Math.min(4, maxTop - 2);
		for (int z = bz0; z <= bz1; z++) {
			for (int x = bx0; x <= bx1; x++) {
				socket(d, x, z);
			}
		}
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = bz0; z <= bz1; z++) {
				for (int x = bx0; x <= bx1; x++) {
					for (int y = 0; y <= head; y++) {
						d.set(x, y, z, y == 0 ? base.full() : y == head ? dress.full() : body.full());
					}
				}
			}
			// the niche, its back in glazed tile, a rounded head
			for (int y = 1; y < head; y++) {
				d.set(cx, y, bz0, Blocks.AIR.defaultBlockState());
				d.set(cx, y, bz1, accent.full());
			}
		}
		Sacra.candles(d, cx, 1, bz0, 3, StageKind.EXTERIOR);
		Sacra.hangingLantern(d, cx, head - 1, bz0, StageKind.EXTERIOR);
		// the cornice: upside-down stairs round the head where the plot allows, then a low hip
		try (var s = d.stage(StageKind.WALLS)) {
			if (bz0 - 1 >= 0) {
				for (int x = bx0; x <= bx1; x++) {
					d.set(x, head, bz0 - 1, dress.stairsOrSlab(Dirs.BACK, Half.TOP));
				}
			}
		}
		// a little pyramid of the roof over the head and its cornice, the crown in the middle, a cross on it
		int ez0 = Math.max(0, bz0 - 1);
		int pcz = ez0 + 1 <= bz1 ? ez0 + 1 : bz1;
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = ez0; z <= bz1; z++) {
				for (int x = bx0; x <= bx1; x++) {
					if (x == cx && z == pcz) {
						d.set(x, head + 1, z, d.mat(Role.ROOF).full());
					} else {
						d.set(x, head + 1, z, Sacra.roofStair(d, Dirs.nearest(cx - x, pcz - z), Half.BOTTOM));
					}
				}
			}
			if (head + 2 <= maxTop) {
				d.set(cx, head + 2, pcz, Sacra.capSlab(d));
			}
		}
		int top = Math.min(maxTop, head + 2);
		if (head + 5 <= maxTop) {
			top = Sacra.woodCross(d, cx, head + 2, pcz, Direction.Axis.X);
		}
		int pz = Math.max(0, bz0 - 1);
		prayer(d, cx, pz);
		offerings(d, cx, pz);
		// a cypress where a column stands free of the roof
		if (w >= 4) {
			int[][] spots = {{0, bz1}, {w - 1, bz1}};
			for (int[] sp : spots) {
				if (Math.abs(sp[0] - cx) >= 2 && d.isAbsent(sp[0], 0, sp[1]) && d.isAbsent(sp[0], 2, sp[1]) && Groundwork.surface(d, sp[0], sp[1]) == 0) {
					Sacra.cypress(d, sp[0], 0, sp[1], Math.min(maxTop, 7));
					break;
				}
			}
		}
		return top;
	}

	/**
	 * The <i>qubba</i>: a squat cube three square on a plinth, a niche toward the road with a lamp in it, a little dome
	 * rising off its walls (shoulders of stairs round a crown) and a rod; candles and pots before it. Returns the top row.
	 */
	private static int qubba(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int cx = mid(d);
		int bx0 = cx - 1;
		int bz0 = dp >= 5 ? dp - 4 : dp - 3;
		int bz1 = bz0 + 2;
		int cz = bz0 + 1;
		// (on a plot three deep the cube fills it, and folk pray from the road)
		int maxTop = d.maxHeight() - 1;
		Stone base = plinth(d);
		Stone body = render(d);
		Stone dress = Stone.dressing(d);
		int head = 2;
		for (int z = bz0; z <= bz1; z++) {
			for (int x = bx0; x <= bx0 + 2; x++) {
				socket(d, x, z);
			}
		}
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = bz0; z <= bz1; z++) {
				for (int x = bx0; x <= bx0 + 2; x++) {
					for (int y = 0; y <= head; y++) {
						d.set(x, y, z, y == 0 ? base.full() : body.full());
					}
				}
			}
			// the niche: the image in the accent at its back, a dressed head over it
			d.set(cx, 1, bz0, Blocks.AIR.defaultBlockState());
			d.set(cx, 1, bz0 + 1, d.mat(Role.ACCENT).full());
			d.set(cx, 2, bz0, dress.full());
		}
		try (var s = d.stage(StageKind.ROOF)) {
			// the dome: the walls' top course drawn in round a crown
			int y1 = head + 1;
			for (int z = bz0; z <= bz1; z++) {
				for (int x = bx0; x <= bx0 + 2; x++) {
					if (x == cx && z == cz) {
						d.set(x, y1, z, body.full());
					} else {
						d.set(x, y1, z, body.stairsOrSlab(Dirs.nearest(cx - x, cz - z), Half.BOTTOM));
					}
				}
			}
			if (y1 + 1 <= maxTop) {
				d.set(cx, y1 + 1, cz, body.has(Form.SLAB) ? body.slab(SlabType.BOTTOM) : body.full());
			}
		}
		int top = head + 2;
		if (top + 1 <= maxTop) {
			top = Details.finial(d, cx, top + 1, cz, false);
		}
		Sacra.standingLantern(d, cx, 1, bz0, StageKind.EXTERIOR);
		int pz = bz0 - 1;
		prayer(d, cx, pz);
		for (int k : new int[]{-1, 1}) {
			int x = cx + 2 * k;
			if (x >= 0 && x < w && d.isEmpty(x, 0, pz) && Groundwork.surface(d, x, pz) == 0) {
				try (var s = d.stage(StageKind.EXTERIOR)) {
					d.set(x, 0, pz, Blocks.DECORATED_POT.defaultBlockState());
				}
			}
		}
		if (w >= 5 && bz1 + 1 < dp) {
			Sacra.cypress(d, 0, 0, bz1 + 1, Math.min(maxTop, 6));
		}
		return top;
	}

	/**
	 * The mesa's <i>nicho</i>: an adobe shrine three wide on a plinth, the image in a niche with candles and a lamp, a
	 * stepped crest over the front (higher in the middle) with a timber cross on it, vigas standing out under the coping
	 * either side, pots of flowers at its foot. Returns the top row.
	 */
	private static int capilla(Draft d) {
		int dp = d.depth();
		int cx = mid(d);
		int bx0 = cx - 1;
		int bx1 = cx + 1;
		int bz0 = dp - 2;
		int bz1 = dp - 1;
		int maxTop = d.maxHeight() - 1;
		Stone base = plinth(d);
		Stone body = render(d);
		Stone dress = Stone.dressing(d);
		Mat frame = d.mat(Role.FRAME);
		int head = 3;
		for (int z = bz0; z <= bz1; z++) {
			for (int x = bx0; x <= bx1; x++) {
				socket(d, x, z);
			}
		}
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = bz0; z <= bz1; z++) {
				for (int x = bx0; x <= bx1; x++) {
					for (int y = 0; y <= head; y++) {
						d.set(x, y, z, y == 0 ? base.full() : body.full());
					}
				}
			}
			for (int y = 1; y < head; y++) {
				d.set(cx, y, bz0, Blocks.AIR.defaultBlockState());
				d.set(cx, y, bz1, d.mat(Role.ACCENT).full());
			}
			// the crest: the front carried up a course, sloping off at the sides, a coped step in the middle
			for (int x = bx0; x <= bx1; x++) {
				d.set(x, head + 1, bz0, x == cx ? body.full() : dress.stairsOrSlab(x < cx ? Dirs.RIGHT : Dirs.LEFT, Half.BOTTOM));
				d.set(x, head + 1, bz1, dress.has(Form.SLAB) ? dress.slab(SlabType.BOTTOM) : dress.full());
			}
			if (head + 2 <= maxTop) {
				d.set(cx, head + 2, bz0, dress.has(Form.SLAB) ? dress.slab(SlabType.BOTTOM) : dress.full());
			}
			// vigas: the ends of the roof beams through the side walls under the coping
			for (int z = bz0; z <= bz1; z++) {
				for (int x : new int[]{bx0 - 1, bx1 + 1}) {
					if (x >= 0 && x < d.frontage()) {
						d.set(x, head, z, frame.log(Direction.Axis.X));
					}
				}
			}
		}
		int top = Math.min(maxTop, head + 2);
		if (head + 5 <= maxTop) {
			top = Sacra.cross(d, cx, head + 3, bz0, Direction.Axis.X, Sacra.fence(d), Sacra.fence(d), 3);
		}
		Sacra.candles(d, cx, 1, bz0, 3, StageKind.EXTERIOR);
		Sacra.hangingLantern(d, cx, head - 1, bz0, StageKind.EXTERIOR);
		int pz = Math.max(0, bz0 - 1);
		prayer(d, cx, pz);
		for (int k : new int[]{-1, 1}) {
			Sacra.pot(d, cx + k, 0, pz);
		}
		return top;
	}

	// ------------------------------------------------------------------------------------------------ the east

	/**
	 * An earthen stupa of the steppe: a square terrace of mud brick reached by a step from the front, a drum and dome drawn
	 * in course by course, a block of the accent for its relic box, a spire of the dressing crowned by a timber stake. A
	 * lamp and candles stand on the terrace's front corners; folk pray at the foot of the step. Returns the top row.
	 */
	private static int stupa(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int cx = mid(d);
		boolean big = w >= 5 && dp >= 5;
		int cz = dp - 2;
		int maxTop = d.maxHeight() - 1;
		Stone base = plinth(d);
		Stone body = render(d);
		Stone dress = Stone.dressing(d);
		Mat frame = d.mat(Role.FRAME);
		Paint stake = frame.has(Form.FENCE) ? frame.fence() : d.mat(Role.FENCE).fence();
		// the terrace: the drum's square and a ring round it (on a big plot), a step across its front
		int tx0 = big ? cx - 2 : cx - 1;
		int tx1 = big ? cx + 2 : cx + 1;
		int tz0 = Math.max(cz - 2, dp >= 4 ? 1 : 0);
		int tz1 = dp - 1;
		boolean terrace = dp >= 4;
		for (int z = tz0; z <= tz1; z++) {
			for (int x = tx0; x <= tx1; x++) {
				if (x >= 0 && x < w) {
					socket(d, x, z);
				}
			}
		}
		int y0 = terrace ? 1 : 0;
		int y;
		try (var s = d.stage(StageKind.WALLS)) {
			if (terrace) {
				for (int z = tz0; z <= tz1; z++) {
					for (int x = tx0; x <= tx1; x++) {
						if (x < 0 || x >= w) {
							continue;
						}
						boolean step = z == tz0 && z < cz - 1 && Math.abs(x - cx) <= 1;
						d.set(x, 0, z, step ? base.stairsOrSlab(Dirs.BACK, Half.BOTTOM) : base.full());
					}
				}
			}
			// the drum: three square, its upper course rounded at the corners; the dome's crown over it
			for (int z = cz - 1; z <= cz + 1; z++) {
				for (int x = cx - 1; x <= cx + 1; x++) {
					if (x < 0 || x >= w || z >= dp) {
						continue;
					}
					boolean corner = x != cx && z != cz;
					if (big) {
						d.set(x, y0, z, body.full());
					}
					int yr = big ? y0 + 1 : y0;
					d.set(x, yr, z, corner ? body.stairsOrSlab(Dirs.nearest(cx - x, 0), Half.BOTTOM) : body.full());
				}
			}
			int yc = big ? y0 + 2 : y0 + 1;
			d.set(cx, yc, cz, body.full());
			for (int[] o : new int[][]{{0, -1}, {0, 1}, {-1, 0}, {1, 0}}) {
				d.set(cx + o[0], yc, cz + o[1], body.stairsOrSlab(Dirs.nearest(-o[0], -o[1]), Half.BOTTOM));
			}
			y = yc + 1;
			// the relic box and the spire
			if (y <= maxTop) {
				d.set(cx, y, cz, d.mat(Role.WALL_ACCENT).full());
			}
			for (int k = 1; k <= 2 && y + k <= maxTop; k++) {
				d.set(cx, y + k, cz, dress.wallOrFull());
			}
		}
		int top = Math.min(maxTop, y + 2);
		if (top + 1 <= maxTop) {
			int ty = top + 1;
			d.inStage(StageKind.ROOF, () -> d.set(cx, ty, cz, stake));
			top = ty;
		}
		// the lamp and the candles: on the terrace's front corners, or on the ground either side of the dome
		int lz = terrace ? tz0 : Math.max(0, cz - 1);
		int ly = terrace ? 1 : 0;
		int[] xs = big ? new int[]{tx0, tx1} : new int[]{cx - 1, cx + 1};
		for (int i = 0; i < 2; i++) {
			int x = xs[i];
			if (x < 0 || x >= w || !d.isEmpty(x, ly, lz)) {
				continue;
			}
			if (i == 0) {
				Sacra.standingLantern(d, x, ly, lz, StageKind.EXTERIOR);
			} else {
				Sacra.candles(d, x, ly, lz, 3, StageKind.EXTERIOR);
			}
		}
		if (!terrace) {
			// no room before it on a shallow plot: the lamp stands in the dome's front, over the road
			try (var s = d.stage(StageKind.WALLS)) {
				d.set(cx, y0 + 1, cz - 1, Blocks.AIR.defaultBlockState());
			}
			Sacra.standingLantern(d, cx, y0 + 1, cz - 1, StageKind.EXTERIOR);
		}
		int pz = terrace ? tz0 - 1 : cz - 2;
		prayer(d, cx, Math.max(-1, pz));
		if (w >= 5 && !big) {
			// a prayer pole with its banner beside the terrace
			int px = w - 1;
			if (d.isEmpty(px, 0, 0) && Groundwork.surface(d, px, 0) == 0) {
				int h = Math.min(4, maxTop - 1);
				try (var s = d.stage(StageKind.EXTERIOR)) {
					for (int yy = 0; yy < h; yy++) {
						d.set(px, yy, 0, stake);
					}
				}
				Details.banner(d, px, h, 0, 1);
			}
		}
		return top;
	}

	/**
	 * The karst's shrine: a gate of two red posts on stone feet, a tie beam between them and a double lintel over them (the
	 * upper one dark, its ends lifting), before a small shrine house on a stone base under a curved roof (on a narrow plot
	 * a stone box with a lamp under its roof); stone lanterns either side of the walk where there is width. Returns the top
	 * row.
	 */
	private static int toriiShrine(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int cx = mid(d);
		int maxTop = d.maxHeight() - 1;
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat trim = d.mat(Role.ROOF_TRIM);
		Stone base = plinth(d);
		Stone stone = Stone.dressing(d);
		// the gate across the front row: on a wide plot the lintels reach a block past the posts
		int gz = 0;
		int px0 = w >= 5 ? Math.max(1, cx - 1) : 0;
		int px1 = w >= 5 ? Math.min(w - 2, cx + 1) : w - 1;
		int postTop = Math.max(3, Math.min(4, maxTop - 3));
		Stone kasagi = Stone.fixed(d, StageKind.FRAME, "minecraft:polished_blackstone");
		for (int x : new int[]{px0, px1}) {
			socket(d, x, gz);
		}
		int lx0 = Math.max(0, px0 - 1);
		int lx1 = Math.min(w - 1, px1 + 1);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int x : new int[]{px0, px1}) {
				d.set(x, 0, gz, base.full());
				for (int y = 1; y <= postTop; y++) {
					d.set(x, y, gz, frame.log(Direction.Axis.Y));
				}
			}
			for (int x = px0 + 1; x <= px1 - 1; x++) {
				d.set(x, postTop - 1, gz, beam.log(Direction.Axis.X));
			}
			for (int x = lx0; x <= lx1; x++) {
				d.set(x, postTop + 1, gz, frame.log(Direction.Axis.X));
			}
			if (postTop + 2 <= maxTop) {
				for (int x = lx0; x <= lx1; x++) {
					boolean end = x == lx0 || x == lx1;
					d.set(x, postTop + 2, gz, end ? kasagi.stairs(x < (lx0 + lx1) / 2.0 ? Dirs.LEFT : Dirs.RIGHT, Half.BOTTOM) : kasagi.slab(SlabType.BOTTOM));
				}
			}
		}
		// the shrine house at the back
		int hz = dp - 1;
		boolean house = w >= 5 && dp >= 4;
		int hz0 = house ? dp - 2 : dp - 1;
		int hx0 = house ? cx - 1 : cx;
		int hx1 = house ? cx + 1 : cx;
		for (int z = hz0; z <= hz; z++) {
			for (int x = hx0; x <= hx1; x++) {
				socket(d, x, z);
			}
		}
		Mat wall = d.mat(Role.WALL);
		Mat shutter = d.mat(Role.SHUTTER);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = hz0; z <= hz; z++) {
				for (int x = hx0; x <= hx1; x++) {
					d.set(x, 0, z, stone.full());
				}
			}
			if (house) {
				for (int z = hz0; z <= hz; z++) {
					for (int x = hx0; x <= hx1; x++) {
						boolean post = x == hx0 || x == hx1;
						d.set(x, 1, z, post ? frame.log(Direction.Axis.Y) : wall.full());
						d.set(x, 2, z, post ? frame.log(Direction.Axis.Y) : wall.full());
					}
				}
				// lattice doors toward the gate, the image glowing behind them
				d.set(cx, 1, hz0, shutter.trapdoor(Dirs.BACK, Half.BOTTOM, true));
				d.set(cx, 2, hz0, shutter.trapdoor(Dirs.BACK, Half.BOTTOM, true));
				d.set(cx, 1, hz, Blocks.AIR.defaultBlockState());
				d.set(cx, 2, hz, d.mat(Role.ACCENT).full());
			} else {
				d.set(cx, 1, hz, stone.full());
			}
		}
		if (house) {
			Sacra.candles(d, cx, 1, hz, 3, StageKind.EXTERIOR);
		} else {
			Sacra.standingLantern(d, cx, 2, hz, StageKind.EXTERIOR);
		}
		int ex0 = Math.max(0, hx0 - 1);
		int ex1 = Math.min(w - 1, hx1 + 1);
		int ez0 = Math.max(gz + 1, hz0 - 1);
		Roofs.Field f = Roofs.hip(ex0, ez0, ex1, hz, Roofs.Profile.curved(1.0));
		Roofs.Roof roof = Roofs.raster(d, f, 3, ex0, ez0, ex1, hz, d.mat(Role.ROOF), trim, Habits.Ridge.FULL);
		int top = Math.max(roof.ridgeY, postTop + 2);
		if (w >= 5 && dp >= 4) {
			int lz = Math.min(hz0 - 1, 2);
			if (lz >= 1) {
				Details.stoneLantern(d, Math.max(0, cx - 2), 0, lz, stone);
				Details.stoneLantern(d, Math.min(w - 1, cx + 2), 0, lz, stone);
				top = Math.max(top, 3);
			}
		}
		int pz = Math.max(gz + 1, hz0 - 1);
		if (pz >= hz0) {
			pz = gz;
		}
		prayer(d, cx, pz);
		return top;
	}

	/**
	 * A spirit house of the tropics: a pedestal of the walls' stone carrying a platform of boards on brackets, offerings
	 * of candles and flowers on its back corners, the little house itself a lamp under a stepped roof of the roof's stairs
	 * on two posts, with a spike. Returns the top row.
	 */
	private static int spiritHouse(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int cx = mid(d);
		int cz = Math.max(1, dp - 2);
		int maxTop = d.maxHeight() - 1;
		Stone body = render(d);
		Stone dress = Stone.dressing(d);
		Mat frame = d.mat(Role.FRAME);
		socket(d, cx, cz);
		int py = maxTop >= 8 ? 3 : 2;
		Paint board = frame.has(Form.SLAB) ? frame.slab(SlabType.TOP) : d.mat(Role.FLOOR).slab(SlabType.TOP);
		try (var s = d.stage(StageKind.WALLS)) {
			d.set(cx, 0, cz, dress.full());
			for (int y = 1; y < py; y++) {
				d.set(cx, y, cz, body.full());
			}
			for (int z = cz - 1; z <= Math.min(dp - 1, cz + 1); z++) {
				for (int x = cx - 1; x <= cx + 1; x++) {
					if (x < 0 || x >= w) {
						continue;
					}
					d.set(x, py, z, x == cx && z == cz ? dress.full() : board);
				}
			}
			// brackets under the platform's sides and back (the front kept clear for whoever kneels there)
			for (int[] o : new int[][]{{0, 1}, {-1, 0}, {1, 0}}) {
				int x = cx + o[0];
				int z = cz + o[1];
				if (x >= 0 && x < w && z < dp) {
					d.set(x, py - 1, z, frame.stairsOrSlab(Dirs.nearest(-o[0], -o[1]), Half.TOP));
				}
			}
		}
		Sacra.standingLantern(d, cx, py + 1, cz, StageKind.EXTERIOR);
		int ry = py + 2;
		try (var s = d.stage(StageKind.FRAME)) {
			for (int x : new int[]{cx - 1, cx + 1}) {
				if (x >= 0 && x < w) {
					d.set(x, py + 1, cz - 1, Sacra.fence(d));
				}
			}
		}
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = cz - 1; z <= Math.min(dp - 1, cz + 1); z++) {
				for (int x = cx - 1; x <= cx + 1; x++) {
					if (x < 0 || x >= w || ry > maxTop) {
						continue;
					}
					d.set(x, ry, z, x == cx && z == cz ? d.mat(Role.ROOF).full() : Sacra.roofStair(d, Dirs.nearest(cx - x, cz - z), Half.BOTTOM));
				}
			}
			if (ry + 1 <= maxTop) {
				d.set(cx, ry + 1, cz, d.mat(Role.ROOF).full());
			}
		}
		int top = Math.min(maxTop, ry + 1);
		if (top + 1 <= maxTop) {
			top = Details.finial(d, cx, top + 1, cz, false);
		}
		if (cz + 1 < dp) {
			if (cx - 1 >= 0) {
				Sacra.candles(d, cx - 1, py + 1, cz + 1, 3, StageKind.EXTERIOR);
			}
			if (cx + 1 < w) {
				Sacra.pot(d, cx + 1, py + 1, cz + 1);
			}
		}
		int pz = cz - 2 >= 0 ? cz - 2 : Math.max(0, cz - 1);
		prayer(d, cx, pz);
		offerings(d, cx, pz);
		return top;
	}
}
