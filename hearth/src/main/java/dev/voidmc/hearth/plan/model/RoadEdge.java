package dev.voidmc.hearth.plan.model;

import dev.voidmc.hearth.plan.road.Polyline;
import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

/**
 * One road of the settlement's network, from one node to another (or to a dead end), planned in full and built from
 * its start outward: {@link #built} dense steps of it have been issued as plots so far. The centreline is a
 * {@link Polyline} (the infrastructure contract), split into runs that are graded road, stair street or bridge.
 */
public final class RoadEdge {
	public static final int GRADE = 0;
	public static final int STAIRS = 1;
	public static final int BRIDGE = 2;
	public static final int BOARDWALK = 3;

	public final int id;
	public final int from;
	public int to;
	public final Role role;
	public final RoadKind kind;
	public final int width;
	private final Polyline line;
	/** Runs as triples (first dense index, type, deck y for bridges), sorted by index; the first starts at 0. */
	private final int[] runs;
	/** Dense steps issued as plots, from the start. */
	public int built;
	/** How far the edge can be built (dense steps): its length, or less where something came to stand in its way. */
	public int limit = Integer.MAX_VALUE;
	/** The road this one leaves from and the dense index there, or -1 (arms from the heart, the square). */
	public int parent = -1;
	public int parentIndex = -1;
	/** Dense indices where other roads join this one, sorted. */
	private int[] junctions;
	private transient Polyline.@Nullable Dense dense;

	public RoadEdge(int id, int from, int to, Role role, RoadKind kind, int width, Polyline line, int[] runs, int built, int[] junctions) {
		this.id = id;
		this.from = from;
		this.to = to;
		this.role = role;
		this.kind = kind;
		this.width = width;
		this.line = line;
		this.runs = runs.length == 0 ? new int[]{0, GRADE, 0} : runs.clone();
		this.built = built;
		this.junctions = junctions.clone();
	}

	public Polyline line() {
		return this.line;
	}

	public Polyline.Dense dense() {
		Polyline.Dense d = this.dense;
		if (d == null) {
			d = this.line.dense();
			this.dense = d;
		}
		return d;
	}

	public int length() {
		return dense().length();
	}

	public boolean fullyBuilt() {
		return this.built >= Math.min(length(), this.limit);
	}

	/** Whether this edge is issued as road plots (the square's edge is not: the plaza paves it). */
	public boolean emits() {
		return this.role != Role.SQUARE && this.role != Role.WALL_LINE;
	}

	/** The run type at a dense index. */
	public int typeAt(int i) {
		int t = GRADE;
		for (int r = 0; r < this.runs.length; r += 3) {
			if (this.runs[r] <= i) {
				t = this.runs[r + 1];
			} else {
				break;
			}
		}
		return t;
	}

	public int[] runs() {
		return this.runs.clone();
	}

	public int runCount() {
		return this.runs.length / 3;
	}

	public int runStart(int r) {
		return this.runs[r * 3];
	}

	public int runEnd(int r) {
		return r + 1 < runCount() ? this.runs[(r + 1) * 3] : length();
	}

	public int runType(int r) {
		return this.runs[r * 3 + 1];
	}

	public int runDeck(int r) {
		return this.runs[r * 3 + 2];
	}

	public int[] junctions() {
		return this.junctions;
	}

	public void addJunction(int index) {
		int[] j = Arrays.copyOf(this.junctions, this.junctions.length + 1);
		j[j.length - 1] = index;
		Arrays.sort(j);
		this.junctions = j;
	}

	/** Dense steps from {@code index} to the nearest junction or end of this edge. */
	public int distanceToJunction(int index) {
		return Math.min(Math.min(index, length() - 1 - index), junctionClearance(index));
	}

	/** Dense steps from {@code index} to the nearest road joining this one along it (not counting its two ends). */
	public int junctionClearance(int index) {
		int d = Integer.MAX_VALUE;
		for (int j : this.junctions) {
			d = Math.min(d, Math.abs(j - index));
		}
		return d;
	}

	CompoundTag save() {
		CompoundTag t = new CompoundTag();
		t.putInt("id", this.id);
		t.putInt("from", this.from);
		t.putInt("to", this.to);
		t.putString("role", this.role.getSerializedName());
		t.putString("kind", this.kind.getSerializedName());
		t.putInt("width", this.width);
		t.putIntArray("points", this.line.points());
		t.putIntArray("runs", this.runs);
		t.putInt("built", this.built);
		t.putIntArray("junctions", this.junctions);
		t.putIntArray("parent", new int[]{this.parent, this.parentIndex});
		if (this.limit != Integer.MAX_VALUE) {
			t.putInt("limit", this.limit);
		}
		return t;
	}

	static RoadEdge load(CompoundTag t) {
		RoadEdge e = new RoadEdge(t.getIntOr("id", 0), t.getIntOr("from", -1), t.getIntOr("to", -1), Role.byName(t.getStringOr("role", "lane")),
			RoadKind.byName(t.getStringOr("kind", "lane")), t.getIntOr("width", 3), new Polyline(Nbt.ints(t, "points").orElseThrow()),
			Nbt.ints(t, "runs").orElse(new int[0]), t.getIntOr("built", 0), Nbt.ints(t, "junctions").orElse(new int[0]));
		int[] parent = Nbt.ints(t, "parent").orElse(new int[]{-1, -1});
		e.parent = parent[0];
		e.parentIndex = parent.length > 1 ? parent[1] : -1;
		e.limit = t.getIntOr("limit", Integer.MAX_VALUE);
		return e;
	}

	/** What a road is for, which decides how plots and later roads treat it. */
	public enum Role implements StringRepresentable {
		/** A main road out from the heart, planned at founding and built outward as the town grows along it. */
		ARM("arm"),
		/** A side street branching from another road. */
		LANE("lane"),
		/** A narrow passage in a dense core. */
		ALLEY("alley"),
		/** A ring road round the core (ring towns, inside walls). */
		RING("ring"),
		/** A long farm track to farmsteads and fields. */
		TRACK("track"),
		/** A stair street climbing a slope too steep to grade. */
		STAIR("stair"),
		/** A road climbing a mountainside in hairpins. */
		SWITCHBACK("switchback"),
		/** A boardwalk over the water of a stilt town. */
		BOARDWALK("boardwalk"),
		/** A short path from a road to a door or a field. */
		PATH("path"),
		/** The edge of the main square: plots front it, but the plaza paves it, so it is never issued as a road. */
		SQUARE("square"),
		/** The way up to a building on a rise: the chapel lane, the stair to a landmark. */
		APPROACH("approach"),
		/** Not a road: the line a walled town keeps for its wall from the founding, built as wall plots later. */
		WALL_LINE("wall_line"),
		/**
		 * A cart track out to a working or a far place (a quarry, a mine, a logging camp, the lord's hunting woods): no
		 * stairs, frontage only for what belongs out there, and outside the town's own road budget.
		 */
		HAUL("haul");

		public static final StringRepresentable.EnumCodec<Role> CODEC = StringRepresentable.fromEnum(Role::values);
		private final String name;

		Role(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}

		public static Role byName(String name) {
			for (Role r : values()) {
				if (r.name.equals(name)) {
					return r;
				}
			}
			return LANE;
		}
	}
}
