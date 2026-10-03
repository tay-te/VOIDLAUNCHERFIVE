package dev.voidmc.hearth.sim.dev;

import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementPlanner;
import dev.voidmc.hearth.api.TerrainProbe;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * A stand-in planner: one straight street east-west through the founding point, a well beside it, and slots of 9 × 9
 * along both sides filled with houses, a builders' yard, fields and storehouses as needs arise; the street is extended
 * 40 blocks at a time when the slots run out. Nothing about it is meant to be pretty — it exists so the sim runs end to
 * end before the plan module is merged. Stateless: everything is derived from the settlement's plots, so it needs no
 * data key and is safe on any thread.
 */
public final class PlaceholderPlanner implements SettlementPlanner {
	private static final int SLOT = 10;
	private static final int DEPTH = 9;
	private static final int SEGMENT = 40;
	/** Half-length the street may reach (a history keeps within its region; see the atlas). */
	private static final int MAX_REACH = 250;

	@Override
	public List<PlotSpec> found(FoundingContext ctx) {
		BlockPos c = ctx.center();
		TerrainProbe t = ctx.terrain();
		Identifier style = style(ctx.settlement());
		List<PlotSpec> out = new ArrayList<>();
		out.add(road(c, -20, 20, t, style, ctx.random().nextLong()));
		out.add(new PlotSpec(PlaceholderBlueprints.WELL, style, ctx.random().nextLong(), floor(c.getX() - 2, c.getZ() - 8, 5, 5, t), 5, 5,
			Direction.SOUTH, 5, 0, new CompoundTag()));
		Set<Integer> taken = new HashSet<>();
		taken.add(slotKey(-1, true));
		for (Identifier a : List.of(PlaceholderBlueprints.YARD, PlaceholderBlueprints.COTTAGE, PlaceholderBlueprints.COTTAGE)) {
			PlotSpec spec = slot(ctx.settlement(), c, a, style, taken, 20, t, ctx.random());
			if (spec != null) {
				out.add(spec);
			}
		}
		return out;
	}

	@Override
	public @Nullable GrowthProject next(Settlement s, TerrainProbe t, RandomSource random) {
		BlockPos c = s.center();
		Identifier style = style(s);
		int houses = 0;
		int fields = 0;
		int stores = 0;
		int yards = 0;
		int plannedBeds = 0;
		int west = 20;
		int east = 20;
		Set<Integer> taken = new HashSet<>();
		taken.add(slotKey(-1, true));
		for (PlotView p : s.plots()) {
			if (p.state() == PlotState.ABANDONED) {
				// its ground stays taken: someone built there
				taken.add(slotOf(c, p.spec()));
				continue;
			}
			Identifier a = p.spec().archetype();
			if (a.equals(PlaceholderBlueprints.ROAD)) {
				int[] pts = p.spec().params().getIntArray("points").orElse(new int[0]);
				for (int i = 0; i + 2 < pts.length; i += 3) {
					west = Math.max(west, c.getX() - pts[i]);
					east = Math.max(east, pts[i] - c.getX());
				}
				continue;
			}
			taken.add(slotOf(c, p.spec()));
			if (a.equals(PlaceholderBlueprints.COTTAGE) || a.equals(PlaceholderBlueprints.HUT)) {
				houses++;
				if (p.state() != PlotState.BUILT) {
					plannedBeds += 2;
				}
			} else if (a.equals(PlaceholderBlueprints.FIELD)) {
				fields++;
			} else if (a.equals(PlaceholderBlueprints.STOREHOUSE)) {
				stores++;
			} else if (a.equals(PlaceholderBlueprints.YARD)) {
				yards++;
			}
		}
		int pop = s.population();
		Identifier want;
		String reason;
		if (yards == 0) {
			want = PlaceholderBlueprints.YARD;
			reason = "builders need a yard";
		} else if (fields * 7 < pop) {
			want = PlaceholderBlueprints.FIELD;
			reason = "food: " + pop + " mouths, " + fields + " fields";
		} else if (stores < pop / 30) {
			want = PlaceholderBlueprints.STOREHOUSE;
			reason = "storage";
		} else if (s.beds() + plannedBeds < pop + 6 && houses < 80) {
			want = PlaceholderBlueprints.COTTAGE;
			reason = "housing: " + pop + " souls, " + s.beds() + " beds";
		} else {
			return null;
		}
		int reach = Math.min(west, east);
		PlotSpec spec = slot(s, c, want, style, taken, reach, t, random);
		if (spec != null) {
			return new GrowthProject(spec, -1, reason);
		}
		// no room along the street: lengthen it at its shorter end
		boolean eastward = east <= west;
		int from = eastward ? east + 1 : -west - 1;
		int to = eastward ? east + SEGMENT : -west - SEGMENT;
		if (Math.abs(to) > MAX_REACH) {
			return null;
		}
		return new GrowthProject(road(c, Math.min(from, to), Math.max(from, to), t, style, random.nextLong()), -1, "the street runs out of plots");
	}

	private static Identifier style(Settlement s) {
		return dev.voidmc.hearth.Hearth.id(s.culture().getSerializedName());
	}

	private static int slotKey(int j, boolean north) {
		return j * 2 + (north ? 1 : 0);
	}

	/** Slot j spans x in [c.x + 4 + 10j, c.x + 13 + 10j]; north slots face south onto the street, south slots face north. */
	private static int slotOf(BlockPos c, PlotSpec spec) {
		int j = Math.floorDiv(spec.origin().getX() - c.getX() - 4, SLOT);
		return slotKey(j, spec.origin().getZ() < c.getZ());
	}

	private static @Nullable PlotSpec slot(Settlement s, BlockPos c, Identifier archetype, Identifier style, Set<Integer> taken, int reach,
		TerrainProbe t, RandomSource random) {
		// nearest slots first, alternating sides and directions
		for (int n = 0; n < 2 * (MAX_REACH / SLOT); n++) {
			int j = (n % 2 == 0) ? n / 2 : -(n / 2) - 1;
			int x0 = c.getX() + 4 + SLOT * j;
			if (x0 < c.getX() - reach || x0 + SLOT - 1 > c.getX() + reach) {
				continue;
			}
			for (boolean north : new boolean[]{true, false}) {
				int key = slotKey(j, north);
				if (taken.contains(key)) {
					continue;
				}
				int z0 = north ? c.getZ() - 2 - DEPTH : c.getZ() + 3;
				if (wet(x0, z0, 9, DEPTH, t)) {
					taken.add(key);
					continue;
				}
				taken.add(key);
				int max = archetype.equals(PlaceholderBlueprints.FIELD) ? 3 : 8;
				return new PlotSpec(archetype, style, random.nextLong(), floor(x0, z0, 9, DEPTH, t), 9, DEPTH, north ? Direction.SOUTH : Direction.NORTH,
					max, Math.min(4, s.tier().ordinal()), new CompoundTag());
			}
		}
		return null;
	}

	private static boolean wet(int x0, int z0, int sx, int sz, TerrainProbe t) {
		return t.isWater(x0, z0) || t.isWater(x0 + sx - 1, z0) || t.isWater(x0, z0 + sz - 1) || t.isWater(x0 + sx - 1, z0 + sz - 1)
			|| t.isWater(x0 + sx / 2, z0 + sz / 2);
	}

	/** The footprint's origin with its floor at the rounded mean of the ground under it. */
	private static BlockPos floor(int x0, int z0, int sx, int sz, TerrainProbe t) {
		int sum = 0;
		int n = 0;
		for (int dx = 0; dx < sx; dx += Math.max(1, sx / 3)) {
			for (int dz = 0; dz < sz; dz += Math.max(1, sz / 3)) {
				sum += t.groundY(x0 + dx, z0 + dz);
				n++;
			}
		}
		return new BlockPos(x0, Math.round(sum / (float) n), z0);
	}

	/** A straight street segment along x from c.x + from to c.x + to, 3 wide, following the ground. */
	private static PlotSpec road(BlockPos c, int from, int to, TerrainProbe t, Identifier style, long seed) {
		List<Integer> pts = new ArrayList<>();
		int step = 8;
		for (int dx = from; ; dx = Math.min(to, dx + step)) {
			int x = c.getX() + dx;
			int y = (t.groundY(x, c.getZ() - 1) + t.groundY(x, c.getZ()) + t.groundY(x, c.getZ() + 1)) / 3;
			if (t.isWater(x, c.getZ())) {
				y = Math.max(y, t.waterY(x, c.getZ()));
			}
			pts.add(x);
			pts.add(y);
			pts.add(c.getZ());
			if (dx >= to) {
				break;
			}
		}
		int minY = Integer.MAX_VALUE;
		for (int i = 1; i < pts.size(); i += 3) {
			minY = Math.min(minY, pts.get(i));
		}
		CompoundTag params = new CompoundTag();
		params.putIntArray("points", pts.stream().mapToInt(Integer::intValue).toArray());
		params.putInt("width", 3);
		params.putString("kind", "street");
		int maxY = minY;
		for (int i = 1; i < pts.size(); i += 3) {
			maxY = Math.max(maxY, pts.get(i));
		}
		return new PlotSpec(PlaceholderBlueprints.ROAD, style, seed, new BlockPos(c.getX() + from, minY, c.getZ() - 1), to - from + 1, 3,
			Direction.SOUTH, maxY - minY + 4, 0, params);
	}
}
