package dev.voidmc.hearth.build.style;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.util.RandomSource;

/**
 * A culture's building habits: how it frames walls, what roofs it raises and how steep, how deep its eaves are, whether
 * it jetties upper storeys, stands on stilts, builds round, flat or domed, and what its yards look like. Archetypes read
 * these to choose among the forms they can build, so each culture's buildings come out in its own manner. Every field
 * has a default, so a style JSON only says what is particular to it.
 */
public record Habits(Walls walls, Roof roof, Windows windows, Plan plan, Yard yard, double chimney, double porch, double balcony, boolean snow,
	Local local) {
	public static final Habits DEFAULT = new Habits(Walls.DEFAULT, Roof.DEFAULT, Windows.DEFAULT, Plan.DEFAULT, Yard.DEFAULT, 0.7, 0.4, 0.3, false,
		Local.DEFAULT);

	/** Habits without a {@code local} section (the defaults allow every local material). */
	public Habits(Walls walls, Roof roof, Windows windows, Plan plan, Yard yard, double chimney, double porch, double balcony, boolean snow) {
		this(walls, roof, windows, plan, yard, chimney, porch, balcony, snow, Local.DEFAULT);
	}

	/** How walls are made. */
	public enum WallKind {
		/** Posts and beams of timber with plaster or brick infill. */
		TIMBER_FRAME,
		/** Horizontal logs with notched, overrunning corners. */
		LOG,
		/** Rubble or dressed stone with quoins. */
		STONE,
		/** Rendered walls on a plinth, with dressed corners. */
		PLASTER,
		/** Mud brick and terracotta, battered and rounded, with vigas through the wall head. */
		ADOBE,
		/** Bamboo and planks on a stone sill. */
		BAMBOO,
		BRICK,
		/** Vertical or horizontal boards on a frame (sheds, stilt houses). */
		PLANK
	}

	public enum RoofForm {
		GABLE, CROSS_GABLE, HIP, HALF_HIP, GAMBREL, MANSARD, FLAT, DOME, CONE, PAGODA, SHED, SALTBOX
	}

	/** How a ridge is capped: slabs of the roof, slabs of the trim (a darker line), full blocks, or stairs. */
	public enum Ridge {
		SLAB, TRIM, STAIRS, FULL, WALL
	}

	public enum Stilts {
		NEVER, WATER, ALWAYS
	}

	public enum GardenKind {
		/** Mixed flowers, a bench, a tree: the English cottage garden. */
		COTTAGE,
		/** Rows of crops, a compost bin, a water butt. */
		KITCHEN,
		/** Dry gravel, cacti, dead bushes, pots. */
		XERIC,
		/** Raked gravel, stones, bamboo, a lantern, moss. */
		ZEN,
		/** Ferns, big leaves, bushes. */
		TROPICAL,
		/** Heather, rocks, dry-stone edges. */
		ROCK,
		NONE
	}

	public enum FenceKind {
		FENCE, WALL, HEDGE, NONE
	}

	/**
	 * @param kind          how walls are built
	 * @param storeyHeight  floor-to-floor height (floor block included), 4 = rooms 3 high
	 * @param plinth        courses of plinth above the ground under the main wall (0 for none)
	 * @param jetty         chance an upper storey oversails the one below by a block
	 * @param postSpacing   timber frames: blocks between posts
	 * @param braces        timber frames: chance of diagonal braces in a bay
	 * @param recessed      infill set back a block behind the frame where the plot allows (deep, crafted walls)
	 * @param beamEnds      chance floor beams run through the wall and show their ends outside
	 * @param upper         how storeys above the ground floor are built (alpine chalets: timber over stone), or null
	 *                      for the same as the ground floor
	 */
	public record Walls(WallKind kind, int storeyHeight, int plinth, double jetty, int postSpacing, double braces, boolean recessed, double beamEnds,
		WallKind upper) {
		public static final Walls DEFAULT = new Walls(WallKind.TIMBER_FRAME, 4, 1, 0.4, 3, 0.3, false, 0.5, null);

		/** The wall kind of storey k. */
		public WallKind kind(int k) {
			return k > 0 && this.upper != null ? this.upper : this.kind;
		}

		static Walls parse(JsonObject o, Walls d) {
			return new Walls(
				Json.enumOr(o, "kind", WallKind.class, d.kind),
				Json.intOr(o, "storey_height", d.storeyHeight),
				Json.intOr(o, "plinth", d.plinth),
				Json.doubleOr(o, "jetty", d.jetty),
				Json.intOr(o, "post_spacing", d.postSpacing),
				Json.doubleOr(o, "braces", d.braces),
				Json.boolOr(o, "recessed", d.recessed),
				Json.doubleOr(o, "beam_ends", d.beamEnds),
				o.has("upper") ? Json.enumOr(o, "upper", WallKind.class, WallKind.TIMBER_FRAME) : d.upper);
		}
	}

	/**
	 * @param forms         relative preference among roof forms
	 * @param pitch         rise per block: 0.5 is a low slab roof, 1 is 45°, 1.5 and 2 are steep
	 * @param eaves         overhang at the eaves, in blocks (0–2)
	 * @param gableOverhang overhang at the gable ends
	 * @param ridge         how the ridge is capped
	 * @param flare         up-turned eave corners (curved tile roofs)
	 * @param brackets      upside-down stair brackets under the eaves
	 * @param dormers       chance of dormers where a roof storey has room
	 * @param thatch        roofs are thatch (thick, rounded edges)
	 * @param parapet       flat roofs carry a parapet
	 * @param frontGable    chance the ridge runs away from the street, the gable end facing it (chalets, town gables)
	 */
	public record Roof(Map<RoofForm, Double> forms, double pitch, int eaves, int gableOverhang, Ridge ridge, boolean flare,
		boolean brackets, double dormers, boolean thatch, boolean parapet, double frontGable) {
		public static final Roof DEFAULT = new Roof(Map.of(RoofForm.GABLE, 3.0, RoofForm.CROSS_GABLE, 1.0, RoofForm.HIP, 1.0), 1.0, 1, 1, Ridge.SLAB,
			false, true, 0.4, false, false, 0.0);

		/** A form drawn from the preferences, limited to those the caller can build. */
		public RoofForm pick(RandomSource random, RoofForm... allowed) {
			double total = 0;
			for (RoofForm f : allowed) {
				total += this.forms.getOrDefault(f, 0.0);
			}
			if (total <= 0) {
				return allowed[0];
			}
			double r = random.nextDouble() * total;
			for (RoofForm f : allowed) {
				r -= this.forms.getOrDefault(f, 0.0);
				if (r < 0) {
					return f;
				}
			}
			return allowed[allowed.length - 1];
		}

		public double weight(RoofForm form) {
			return this.forms.getOrDefault(form, 0.0);
		}

		static Roof parse(JsonObject o, Roof d) {
			Map<RoofForm, Double> forms = d.forms;
			if (o.has("forms")) {
				EnumMap<RoofForm, Double> m = new EnumMap<>(RoofForm.class);
				for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("forms").entrySet()) {
					m.put(RoofForm.valueOf(e.getKey().toUpperCase(Locale.ROOT)), e.getValue().getAsDouble());
				}
				forms = Map.copyOf(m);
			}
			return new Roof(forms,
				Json.doubleOr(o, "pitch", d.pitch),
				Json.intOr(o, "eaves", d.eaves),
				Json.intOr(o, "gable_overhang", d.gableOverhang),
				Json.enumOr(o, "ridge", Ridge.class, d.ridge),
				Json.boolOr(o, "flare", d.flare),
				Json.boolOr(o, "brackets", d.brackets),
				Json.doubleOr(o, "dormers", d.dormers),
				Json.boolOr(o, "thatch", d.thatch),
				Json.boolOr(o, "parapet", d.parapet),
				Json.doubleOr(o, "front_gable", d.frontGable));
		}
	}

	/**
	 * @param shutters chance a window has shutters
	 * @param boxes    chance a window has a flower box
	 * @param arched   chance windows are arched
	 * @param lattice  windows are lattice (trapdoor grilles) instead of shutters
	 * @param bay      chance of a bay window on a ground floor
	 * @param tall     windows are two blocks tall (otherwise one, in small or stone buildings)
	 */
	public record Windows(double shutters, double boxes, double arched, boolean lattice, double bay, boolean tall) {
		public static final Windows DEFAULT = new Windows(0.6, 0.4, 0.0, false, 0.2, true);

		static Windows parse(JsonObject o, Windows d) {
			return new Windows(
				Json.doubleOr(o, "shutters", d.shutters),
				Json.doubleOr(o, "boxes", d.boxes),
				Json.doubleOr(o, "arched", d.arched),
				Json.boolOr(o, "lattice", d.lattice),
				Json.doubleOr(o, "bay", d.bay),
				Json.boolOr(o, "tall", d.tall));
		}
	}

	/**
	 * @param round     chance small buildings are round
	 * @param courtyard chance larger houses wrap round a court
	 * @param stilts    when buildings stand on stilts
	 * @param stiltHeight clearance under a stilt house's floor
	 * @param tower     chance of a tower or turret where the archetype allows
	 * @param terraces  flat roofs are used as terraces (stairs up, railings, furniture)
	 */
	public record Plan(double round, double courtyard, Stilts stilts, int stiltHeight, double tower, boolean terraces) {
		public static final Plan DEFAULT = new Plan(0, 0, Stilts.WATER, 2, 0.2, false);

		static Plan parse(JsonObject o, Plan d) {
			return new Plan(
				Json.doubleOr(o, "round", d.round),
				Json.doubleOr(o, "courtyard", d.courtyard),
				Json.enumOr(o, "stilts", Stilts.class, d.stilts),
				Json.intOr(o, "stilt_height", d.stiltHeight),
				Json.doubleOr(o, "tower", d.tower),
				Json.boolOr(o, "terraces", d.terraces));
		}
	}

	/**
	 * @param garden   what gardens grow
	 * @param fence    how yards are bounded
	 * @param hedge    chance of hedges along a garden
	 * @param woodpile chance of a woodpile by the house
	 */
	public record Yard(GardenKind garden, FenceKind fence, double hedge, double woodpile) {
		public static final Yard DEFAULT = new Yard(GardenKind.COTTAGE, FenceKind.FENCE, 0.3, 0.5);

		static Yard parse(JsonObject o, Yard d) {
			return new Yard(
				Json.enumOr(o, "garden", GardenKind.class, d.garden),
				Json.enumOr(o, "fence", FenceKind.class, d.fence),
				Json.doubleOr(o, "hedge", d.hedge),
				Json.doubleOr(o, "woodpile", d.woodpile));
		}
	}

	/**
	 * Which of a territory's materials ({@link LocalMaterials}, the plot param {@code local}) the culture builds with:
	 * its timber, its stone, its clay (bricks and tiles), its thatch, and per role ({@code "roles": {"wall": false}}: a
	 * whitewashed town renders its walls whatever stone lies under them).
	 */
	public record Local(boolean wood, boolean stone, boolean clay, boolean thatch, Map<Role, Boolean> roles) {
		public static final Local DEFAULT = new Local(true, true, true, true, Map.of());

		public Local {
			roles = Map.copyOf(roles);
		}

		/** Whether a role may take a local material of a kind. */
		public boolean allows(Role role, MaterialFamilies.Kind kind) {
			Boolean r = this.roles.get(role);
			if (r != null) {
				return r;
			}
			return switch (kind) {
				case WOOD -> this.wood;
				case STONE -> this.stone;
				case CLAY -> this.clay;
				case THATCH -> this.thatch;
				default -> true;
			};
		}

		static Local parse(JsonObject o, Local d) {
			Map<Role, Boolean> roles = d.roles;
			if (o.has("roles")) {
				EnumMap<Role, Boolean> m = new EnumMap<>(Role.class);
				for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("roles").entrySet()) {
					Role r = Role.byKey(e.getKey());
					if (r == null) {
						throw new IllegalArgumentException("unknown role '" + e.getKey() + "' in local habits");
					}
					m.put(r, e.getValue().getAsBoolean());
				}
				roles = m;
			}
			return new Local(Json.boolOr(o, "wood", d.wood), Json.boolOr(o, "stone", d.stone), Json.boolOr(o, "clay", d.clay),
				Json.boolOr(o, "thatch", d.thatch), roles);
		}
	}

	public static Habits parse(JsonObject o) {
		Habits d = DEFAULT;
		return new Habits(
			o.has("walls") ? Walls.parse(o.getAsJsonObject("walls"), d.walls) : d.walls,
			o.has("roof") ? Roof.parse(o.getAsJsonObject("roof"), d.roof) : d.roof,
			o.has("windows") ? Windows.parse(o.getAsJsonObject("windows"), d.windows) : d.windows,
			o.has("plan") ? Plan.parse(o.getAsJsonObject("plan"), d.plan) : d.plan,
			o.has("yard") ? Yard.parse(o.getAsJsonObject("yard"), d.yard) : d.yard,
			Json.doubleOr(o, "chimney", d.chimney),
			Json.doubleOr(o, "porch", d.porch),
			Json.doubleOr(o, "balcony", d.balcony),
			Json.boolOr(o, "snow", d.snow),
			o.has("local") ? Local.parse(o.getAsJsonObject("local"), d.local) : d.local);
	}

	/** Small JSON readers with defaults. */
	static final class Json {
		private Json() {
		}

		static int intOr(JsonObject o, String k, int d) {
			return o.has(k) ? o.get(k).getAsInt() : d;
		}

		static double doubleOr(JsonObject o, String k, double d) {
			return o.has(k) ? o.get(k).getAsDouble() : d;
		}

		static boolean boolOr(JsonObject o, String k, boolean d) {
			return o.has(k) ? o.get(k).getAsBoolean() : d;
		}

		static <E extends Enum<E>> E enumOr(JsonObject o, String k, Class<E> type, E d) {
			return o.has(k) ? Enum.valueOf(type, o.get(k).getAsString().toUpperCase(Locale.ROOT)) : d;
		}
	}
}
