package dev.voidmc.hearth.build.age;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.kit.age.HouseScan;
import dev.voidmc.hearth.build.kit.age.LeanTo;
import dev.voidmc.hearth.build.kit.age.Settle;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

/**
 * Accretion: houses that grow over generations instead of only being rebuilt (docs/LIVING_WORLD.md §8.4). The plot param
 * {@code additions} adds to any housing archetype, after it has drawn, the things households really add:
 *
 * <ul>
 *   <li>{@code lean_to}: a one-storey room against a side or the back under a roof falling away from the house (a
 *   scullery, a store, the trade's workshop), or a woodshed where no door can be cut;</li>
 *   <li>{@code outshot}: the same across the whole back;</li>
 *   <li>{@code dormer}: a window bay cut into the roof over the attic floor ({@code kit.age.Dormer});</li>
 *   <li>{@code jetty}: the upper storeys rebuilt a block out over the street or the yard ({@code kit.age.Jetty});</li>
 *   <li>{@code oriel} (a glazed or latticed bay on corbels) and {@code gallery} (a deck along an upper storey, a door out
 *   onto it) ({@code kit.age.Bays});</li>
 *   <li>{@code storey}: the roof lifted whole and a storey built under it like the one below ({@code kit.age.Storey});</li>
 *   <li>for flat-roofed houses, {@code roof_room} (a room on the roof terrace, {@code kit.age.RoofRoom}) and
 *   {@code annex} (a lean-to under a flat deck).</li>
 * </ul>
 *
 * <p>The param is either a count (an int, 0 to 6): that many additions, chosen and ordered by the plot's seed among those
 * that suit the house and its culture; or a list of kinds (a list tag of strings, or one comma-separated string) tried in
 * that order. Additions are cumulative: a house's first {@code n + 1} additions are its first {@code n} and one more, so
 * a planner that counts up as a family prospers sees the house grow, never rebuilt. Each addition reads the house as the
 * last left it, is built in the house's own manner, keeps off party walls and the way in from the street, and is skipped
 * (the next kind tried) where it does not fit. Housing only; every other archetype ignores the param.
 */
public final class Accretion {
	/** Prints what each addition did (the env var HEARTH_AGE_LOG, for previews). */
	private static final boolean LOG = System.getenv("HEARTH_AGE_LOG") != null;

	/** The plot param. */
	public static final String PARAM = "additions";

	/** What a household adds. */
	public enum Kind {
		LEAN_TO, OUTSHOT, DORMER, GALLERY, ORIEL, JETTY, STOREY, ROOF_ROOM, ANNEX;

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		static Kind byKey(String s) {
			return valueOf(s.trim().toUpperCase(Locale.ROOT));
		}
	}

	private Accretion() {
	}

	/** Yard kept behind a house that will grow (rows), when its plot is that much deeper than the archetype needs. */
	static final int BACK_YARD = 4;
	/** Yard kept beside it (columns), on a side that is not a party wall. */
	static final int SIDE_YARD = 4;

	/**
	 * The part of the plot a house that will grow is first built on, as {x0, z0, x1, z1} in the plot's local frame, or
	 * null to build on the whole plot. A housing plot carrying {@value #PARAM} (any value, 0 included: the planner sets it
	 * from the house's first build, so later counts only add) keeps a yard to grow into: up to {@value #BACK_YARD} rows
	 * behind, where the plot is at least three rows deeper than the archetype's least depth, and up to
	 * {@value #SIDE_YARD} columns on one side that is not a party wall (chosen by the seed), where it is at least four
	 * wider than its least frontage. The front stays on the street. Houses on water, on terraces and stacked on roofs
	 * build on their whole plot.
	 */
	public static int @org.jspecify.annotations.Nullable [] reserve(ArchetypeInfo info, dev.voidmc.hearth.api.PlotSpec spec) {
		CompoundTag p = spec.params();
		if (info.category() != ArchetypeCategory.HOUSING || !p.contains(PARAM) || info.flags().contains(ArchetypeInfo.SiteFlag.ON_WATER)
			|| p.getBooleanOr("terraced", false) || p.getBooleanOr("stacked", false)) {
			return null;
		}
		int fr = spec.frontage();
		int dp = spec.depth();
		int back = dp - info.minDepth() >= 3 ? Math.min(BACK_YARD, dp - info.minDepth()) : 0;
		int side = fr - info.minFrontage() >= 4 ? Math.min(SIDE_YARD, fr - info.minFrontage()) : 0;
		// the side the yard is on: the plot params name the building's own sides (Draft.partyLocalLeft)
		boolean leftParty = p.getBooleanOr("party_right", false);
		boolean rightParty = p.getBooleanOr("party_left", false);
		boolean left = (Noise.hash(spec.seed(), 0x5A4D) & 1) == 0;
		if (left && leftParty || !left && rightParty) {
			left = !left;
		}
		if (left && leftParty || !left && rightParty) {
			side = 0;
		}
		if (back == 0 && side == 0) {
			return null;
		}
		return left ? new int[]{side, 0, fr - 1, dp - 1 - back} : new int[]{0, 0, fr - 1 - side, dp - 1 - back};
	}

	/** The params of the house as first built on the part {@link #reserve} gave: no party wall on a side moved off the plot's edge. */
	static CompoundTag innerParams(dev.voidmc.hearth.api.PlotSpec spec, int[] r) {
		CompoundTag p = spec.params();
		if (r[0] > 0) {
			p.remove("party_right");
		}
		if (r[2] < spec.frontage() - 1) {
			p.remove("party_left");
		}
		return p;
	}

	public static void apply(ArchetypeInfo info, Draft d) {
		if (!d.hasParam(PARAM) || info.category() != ArchetypeCategory.HOUSING) {
			return;
		}
		CompoundTag params = d.params();
		List<Kind> asked = new ArrayList<>();
		int count;
		var tag = params.get(PARAM);
		if (tag instanceof ListTag list) {
			for (int i = 0; i < list.size(); i++) {
				if (list.get(i) instanceof StringTag st) {
					asked.add(Kind.byKey(st.value()));
				}
			}
			count = asked.size();
		} else if (tag instanceof StringTag st) {
			String v = st.value().trim();
			if (v.matches("\\d+")) {
				count = Integer.parseInt(v);
			} else {
				for (String k : v.split(",")) {
					if (!k.isBlank()) {
						asked.add(Kind.byKey(k));
					}
				}
				count = asked.size();
			}
		} else {
			count = params.getIntOr(PARAM, 0);
		}
		count = Math.clamp(count, 0, 8);
		if (count == 0) {
			return;
		}
		LongOpenHashSet route = Access.route(d);
		List<Kind> order = asked.isEmpty() ? order(d) : asked;
		int done = 0;
		java.util.EnumSet<Kind> made = java.util.EnumSet.noneOf(Kind.class);
		// what the additions so far have placed (not the house as first built: a lifted roof leaves a lean-to where it is)
		LongOpenHashSet added = new LongOpenHashSet();
		for (Kind k : order) {
			if (done >= count) {
				break;
			}
			// a storey lifts the roof: never after what hangs on the old top storey or in its roof
			if (k == Kind.STOREY && (made.contains(Kind.DORMER) || made.contains(Kind.JETTY) || made.contains(Kind.ORIEL) || made.contains(Kind.GALLERY))) {
				continue;
			}
			HouseScan h = HouseScan.of(d, route, added);
			if (h == null) {
				return;
			}
			it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<Draft.Cell> before = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>(d.cells());
			List<Draft.LocalPoi> pois = new ArrayList<>(d.pois());
			LongOpenHashSet outOfReach = dev.voidmc.hearth.build.kit.age.Reach.unreached(d);
			boolean ok = add(d, h, k);
			if (ok) {
				Settle.around(d, Settle.changed(before, d));
				// nobody's bed, bench or store may end up where no one can walk to it
				for (long key : dev.voidmc.hearth.build.kit.age.Reach.unreached(d)) {
					if (!outOfReach.contains(key)) {
						ok = false;
						if (LOG) {
							System.out.println("ACCRETION " + k.key() + " leaves " + Draft.keyX(key) + "," + Draft.keyY(key) + "," + Draft.keyZ(key) + " out of reach");
						}
						break;
					}
				}
			}
			if (LOG) {
				System.out.println("ACCRETION " + d.spec().archetype() + " " + d.spec().style() + " seed " + d.seed() + " " + k.key() + (ok ? " built" : " skipped"));
			}
			if (!ok) {
				// what an addition began and could not finish is taken down again
				Settle.restore(d, before, pois);
				continue;
			}
			done++;
			made.add(k);
			added.addAll(Settle.changed(before, d));
		}
	}

	/** The kinds of addition a house tries, in the order its household makes them (seeded, weighted by its manner). */
	static List<Kind> order(Draft d) {
		List<Kind> pool = new ArrayList<>(List.of(Kind.values()));
		double[] w = new double[pool.size()];
		var habits = d.habits();
		boolean flatStyle = habits.roof().forms().getOrDefault(dev.voidmc.hearth.build.style.Habits.RoofForm.FLAT, 0.0) > 0.5;
		for (int i = 0; i < pool.size(); i++) {
			w[i] = switch (pool.get(i)) {
				case LEAN_TO -> flatStyle ? 0.3 : 3;
				case OUTSHOT -> flatStyle ? 0.2 : 1.6;
				case DORMER -> flatStyle ? 0 : 0.8 + 2 * habits.roof().dormers();
				case GALLERY -> 0.5 + (habits.windows().lattice() ? 1.0 : 0.3);
				case ORIEL -> 0.4 + 2 * habits.windows().bay();
				case JETTY -> flatStyle ? 0 : 0.2 + 3 * habits.walls().jetty();
				case STOREY -> 1.0;
				case ROOF_ROOM, ANNEX -> flatStyle ? 2.5 : 0;
			};
		}
		// what this manner of house never does is never tried
		for (int i = pool.size() - 1; i >= 0; i--) {
			if (w[i] <= 0) {
				pool.remove(i);
				for (int j = i; j < w.length - 1; j++) {
					w[j] = w[j + 1];
				}
			}
		}
		List<Kind> out = new ArrayList<>();
		long seed = Noise.hash(d.seed(), 0xACC2);
		while (!pool.isEmpty()) {
			double total = 0;
			for (int i = 0; i < pool.size(); i++) {
				total += w[i];
			}
			double t = Noise.unit(seed, out.size(), 0, 0) * total;
			int pick = pool.size() - 1;
			for (int i = 0; i < pool.size(); i++) {
				t -= w[i];
				if (t < 0) {
					pick = i;
					break;
				}
			}
			out.add(pool.remove(pick));
			for (int i = pick; i < w.length - 1; i++) {
				w[i] = w[i + 1];
			}
		}
		// a storey comes before what hangs on the top storey and its roof
		int st = out.indexOf(Kind.STOREY);
		int firstUpper = Integer.MAX_VALUE;
		for (Kind k : new Kind[]{Kind.DORMER, Kind.JETTY, Kind.ORIEL, Kind.GALLERY}) {
			int at = out.indexOf(k);
			if (at >= 0) {
				firstUpper = Math.min(firstUpper, at);
			}
		}
		if (st > firstUpper) {
			out.remove(st);
			out.add(firstUpper, Kind.STOREY);
		}
		// a second lean-to (or annex), on another side, after the rest
		out.add(flatStyle ? Kind.ANNEX : Kind.LEAN_TO);
		return out;
	}

	private static boolean add(Draft d, HouseScan h, Kind k) {
		long seed = Noise.hash(d.seed(), 0xADD0 + k.ordinal());
		return switch (k) {
			case LEAN_TO -> {
				List<Direction> sides = new ArrayList<>(List.of(Dirs.LEFT, Dirs.RIGHT, Dirs.BACK));
				java.util.Collections.rotate(sides, (int) Math.floorMod(seed, 3));
				for (Direction s : sides) {
					if (LeanTo.add(h, s, false)) {
						yield true;
					}
				}
				yield false;
			}
			case OUTSHOT -> LeanTo.add(h, Dirs.BACK, true);
			case ANNEX -> {
				List<Direction> sides = new ArrayList<>(List.of(Dirs.BACK, Dirs.LEFT, Dirs.RIGHT));
				java.util.Collections.rotate(sides, (int) Math.floorMod(seed, 3));
				for (Direction s : sides) {
					if (LeanTo.add(h, s, false, true)) {
						yield true;
					}
				}
				yield false;
			}
			case DORMER -> dev.voidmc.hearth.build.kit.age.Dormer.add(h, seed);
			case JETTY -> dev.voidmc.hearth.build.kit.age.Jetty.add(h, seed);
			case ORIEL -> dev.voidmc.hearth.build.kit.age.Bays.oriel(h, seed);
			case GALLERY -> dev.voidmc.hearth.build.kit.age.Bays.gallery(h, seed);
			case STOREY -> dev.voidmc.hearth.build.kit.age.Storey.add(h);
			case ROOF_ROOM -> dev.voidmc.hearth.build.kit.age.RoofRoom.add(h, seed);
		};
	}
}
