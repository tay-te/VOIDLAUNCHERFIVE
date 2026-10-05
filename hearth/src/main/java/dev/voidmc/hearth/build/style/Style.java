package dev.voidmc.hearth.build.style;

import com.google.gson.JsonObject;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.build.draft.Noise;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * A building style: the materials that play each {@link Role} and the culture's {@link Habits}. Loaded from
 * {@code data/hearth/hearth_style/<name>.json} (see {@link StyleLoader} for the format). Immutable.
 *
 * <p>Tiers change finish: the base {@code roles} are tier 2 (an ordinary village house); a style may override roles for
 * other tiers, and a tier inherits from its neighbours toward 2 (tier 4 looks at 4, then 3, then the base; tier 0 at 0,
 * then 1, then the base), so "3" upgrades also dress tier 4 and "1" roughness also roughens tier 0.
 *
 * <p>Variants are the dressings within a culture, chosen per building ({@link #pickVariant(long, StyleContext)}):
 * <ul>
 *   <li><b>fashions</b>: alternative dressings of the culture's common manner (brick nogging instead of plaster, a red
 *   painted house among the log ones), each overriding some roles and habits of the base; the culture's tier overrides
 *   still win over a fashion's roles (finish before fashion) except the fashion's fabric ({@link #isFabric}: the walls,
 *   gables, quoins and frame it names stay its own), and a fashion's own {@code tiers} win over both;</li>
 *   <li><b>sub-styles</b>: the culture's other manners (burgher brick, Norman stone, the palazzo, the scholar's
 *   courtyard), each a whole dressing with its own tiers and its own fashions, flattened into the variant list after the
 *   fashions; a sub-style's tiers win over its roles (but not over the fabric its fashions name, as above; a sub-style's
 *   fashion may carry {@code tiers} of its own), the culture's tiers do not reach it.</li>
 * </ul>
 * Any dressing may carry a {@link When} (who and where it is for: stations, categories, tiers, biomes, landforms...);
 * the base dressing's is {@code base_when}. A building draws by weight among the dressings whose condition holds for
 * its {@link StyleContext}; with no conditions anywhere the draw is the old seeded one, unchanged.
 *
 * <p>A style may also be a <b>region</b> ({@link #region()}): a regional overlay (old forest, volcanic, fjord...) worn over a
 * culture's dressing where its condition holds (a biome, a landform, the {@code region} param). It names only the roles
 * and habits it changes. Culture styles carry the regions they may wear ({@link #regions()}).
 *
 * <p>{@link #dress} gives the <b>dressed view</b> a building actually wears: the same style with its region worn, its
 * territory's {@link LocalMaterials} blended in and its station's {@link StationFinish} applied. Every reader of the
 * view ({@link #palette}, {@link #habits(int)}) gets the dressed answer, so components that read the style directly
 * agree with {@code Draft.mat}.
 */
public final class Style {
	private final Identifier id;
	private final @Nullable Culture culture;
	private final String name;
	private final String baseName;
	private final Map<Role, Palette> base;
	private final Map<Integer, Map<Role, Palette>> tiers;
	private final Habits habits;
	private final JsonObject habitsJson;
	private final double baseWeight;
	private final @Nullable When baseWhen;
	private final List<Variant> variants;
	private final Map<Station, StationFinish> stations;
	private final @Nullable Region region;
	private final List<RoleOverride> overrides;
	private final List<Style> regions;
	@SuppressWarnings("unchecked")
	private final Map<Role, Palette>[] resolved = new Map[5];
	/** For a dressed view: the style it dresses, and how. */
	private final @Nullable Style root;
	private final @Nullable Dressing dressing;
	private final Map<String, Style> dressed = new ConcurrentHashMap<>();

	/**
	 * One dressing: a name, its weight among the others, what it changes, and where it belongs.
	 *
	 * @param when       where it may be chosen (null: anywhere)
	 * @param substyle   the sub-style it belongs to (null for a fashion of the culture's base)
	 * @param tiers      a sub-style's own tier overrides (empty for fashions)
	 * @param habitsJson its habits as JSON (the base's merged with its own), for regional overlays; do not modify
	 */
	public record Variant(String name, double weight, Map<Role, Palette> roles, Habits habits, @Nullable When when, @Nullable String substyle,
		Map<Integer, Map<Role, Palette>> tiers, JsonObject habitsJson) {
		public Variant {
			roles = Map.copyOf(roles);
			tiers = Map.copyOf(tiers);
		}

		public Variant(String name, double weight, Map<Role, Palette> roles, Habits habits) {
			this(name, weight, roles, habits, null, null, Map.of(), new JsonObject());
		}
	}

	/**
	 * Roles a culture dresses its own way for some purposes or places, whatever dressing the building wears (debarked
	 * stakes for the steppe's palisades, stained glass in every chapel): applied over the dressing and over a region.
	 *
	 * @param when  where it applies
	 * @param roles the roles it changes
	 * @param tiers its tier overrides (win over its roles)
	 */
	public record RoleOverride(When when, Map<Role, Palette> roles, Map<Integer, Map<Role, Palette>> tiers) {
		public RoleOverride {
			roles = Map.copyOf(roles);
			tiers = Map.copyOf(tiers);
		}

		@org.jspecify.annotations.Nullable Palette palette(Role role, int tier) {
			Palette p = tierChain(this.tiers, role, tier);
			return p != null ? p : this.roles.get(role);
		}
	}

	/**
	 * What makes a style a regional overlay.
	 *
	 * @param name     the region's name (the {@code region} param forces it)
	 * @param when     where it is worn
	 * @param priority which of several matching regions wins (higher first)
	 * @param aliases  other names the {@code region} param may give it (a region renamed: {@code lumen} is now
	 *                 {@code old_forest})
	 */
	public record Region(String name, @Nullable When when, int priority, List<String> aliases) {
		public Region {
			aliases = List.copyOf(aliases);
		}

		public Region(String name, @Nullable When when, int priority) {
			this(name, when, priority, List.of());
		}

		/** Whether the {@code region} param names this region. */
		public boolean named(String n) {
			return this.name.equals(n) || this.aliases.contains(n);
		}
	}

	public Style(Identifier id, @Nullable Culture culture, String name, Map<Role, Palette> base, Map<Integer, Map<Role, Palette>> tiers, Habits habits) {
		this(id, culture, name, base, tiers, habits, 1.0, List.of());
	}

	public Style(Identifier id, @Nullable Culture culture, String name, Map<Role, Palette> base, Map<Integer, Map<Role, Palette>> tiers, Habits habits,
		double baseWeight, List<Variant> variants) {
		this(id, culture, name, "base", base, tiers, habits, new JsonObject(), baseWeight, null, variants, Map.of(), null, List.of(), List.of(), null,
			null);
	}

	/** The full form (see {@link StyleLoader}). */
	public Style(Identifier id, @Nullable Culture culture, String name, String baseName, Map<Role, Palette> base, Map<Integer, Map<Role, Palette>> tiers,
		Habits habits, JsonObject habitsJson, double baseWeight, @Nullable When baseWhen, List<Variant> variants, Map<Station, StationFinish> stations,
		@Nullable Region region, List<RoleOverride> overrides) {
		this(id, culture, name, baseName, base, tiers, habits, habitsJson, baseWeight, baseWhen, variants, stations, region, overrides, List.of(), null,
			null);
	}

	private Style(Identifier id, @Nullable Culture culture, String name, String baseName, Map<Role, Palette> base, Map<Integer, Map<Role, Palette>> tiers,
		Habits habits, JsonObject habitsJson, double baseWeight, @Nullable When baseWhen, List<Variant> variants, Map<Station, StationFinish> stations,
		@Nullable Region region, List<RoleOverride> overrides, List<Style> regions, @Nullable Style root, @Nullable Dressing dressing) {
		this.id = id;
		this.culture = culture;
		this.name = name;
		this.baseName = baseName;
		this.base = Map.copyOf(base);
		this.tiers = Map.copyOf(tiers);
		this.habits = habits;
		this.habitsJson = habitsJson;
		this.baseWeight = baseWeight;
		this.baseWhen = baseWhen;
		this.variants = List.copyOf(variants);
		this.stations = Map.copyOf(stations);
		this.region = region;
		this.overrides = List.copyOf(overrides);
		this.regions = List.copyOf(regions);
		this.root = root;
		this.dressing = dressing;
		for (int t = 0; t <= 4; t++) {
			EnumMap<Role, Palette> m = new EnumMap<>(Role.class);
			for (Role r : Role.values()) {
				Palette p = lookup(r, t);
				if (p != null) {
					m.put(r, p);
				}
			}
			this.resolved[t] = Map.copyOf(m);
		}
	}

	/** This style able to wear the given regional overlays. */
	public Style withRegions(List<Style> regions) {
		List<Style> sorted = new ArrayList<>(regions);
		sorted.removeIf(r -> r.region == null);
		sorted.sort(Comparator.comparingInt((Style r) -> -r.region.priority()).thenComparing(r -> r.region.name()));
		return new Style(this.id, this.culture, this.name, this.baseName, this.base, this.tiers, this.habits, this.habitsJson, this.baseWeight,
			this.baseWhen, this.variants, this.stations, this.region, this.overrides, sorted, null, null);
	}

	private @Nullable Palette lookup(Role role, int tier) {
		Palette p = tierChain(this.tiers, role, tier);
		if (p != null) {
			return p;
		}
		p = this.base.get(role);
		return p != null ? p : StyleDefaults.palette(role);
	}

	/** A role's override in a tier map, inheriting toward tier 2, or null. */
	private static @Nullable Palette tierChain(Map<Integer, Map<Role, Palette>> tiers, Role role, int tier) {
		if (tiers.isEmpty()) {
			return null;
		}
		int t = Math.clamp(tier, 0, 4);
		while (true) {
			Map<Role, Palette> m = tiers.get(t);
			if (m != null && m.containsKey(role)) {
				return m.get(role);
			}
			if (t == 2) {
				return null;
			}
			t += t > 2 ? -1 : 1;
		}
	}

	public Identifier id() {
		return this.id;
	}

	/** The culture this style is the default of, or null for an extra style or a region. */
	public @Nullable Culture culture() {
		return this.culture;
	}

	public String name() {
		return this.name;
	}

	/** The name of the base dressing (the culture's common manner). */
	public String baseName() {
		return this.baseName;
	}

	/** The base dressing's habits (dressed, for a dressed view). */
	public Habits habits() {
		return habits(-1);
	}

	public List<Variant> variants() {
		return this.variants;
	}

	/** The base dressing's weight among the variants. */
	public double baseWeight() {
		return this.baseWeight;
	}

	/** The base dressing's condition, or null (always). */
	public @Nullable When baseWhen() {
		return this.baseWhen;
	}

	/** The culture's own role overrides by purpose or place. */
	public List<RoleOverride> overrides() {
		return this.overrides;
	}

	/** Non-null when this style is a regional overlay. */
	public @Nullable Region region() {
		return this.region;
	}

	/** The regional overlays this style may wear, highest priority first. */
	public List<Style> regions() {
		return this.root != null ? this.root.regions : this.regions;
	}

	/** The undressed style (itself unless this is a dressed view). */
	public Style root() {
		return this.root != null ? this.root : this;
	}

	/** The region this view wears, or null. */
	public @Nullable Style worn() {
		return this.dressing == null ? null : this.dressing.overlay;
	}

	/** The variant of the worn region, or -1. */
	public int wornVariant() {
		return this.dressing == null ? -1 : this.dressing.overlayVariant;
	}

	/** The name of a dressing: the base's or a variant's. */
	public String dressingName(int variant) {
		return variant >= 0 && variant < this.variants.size() ? this.variants.get(variant).name() : this.baseName;
	}

	/** How a station dresses its house in this style (the defaults with the style's own overrides). */
	public StationFinish station(Station station) {
		StationFinish f = root().stations.get(station);
		return f != null ? f : StationFinish.defaults(station);
	}

	// ------------------------------------------------------------------------------------------------ choosing

	/** A variant for a building, ignoring every condition's subject (the old seeded draw): see {@link #pickVariant(long, StyleContext)}. */
	public int pickVariant(long seed) {
		return pickVariant(seed, StyleContext.NONE);
	}

	/**
	 * A dressing for a building: -1 for the base, else an index into {@link #variants()}; drawn by weight among those whose
	 * condition holds for the context. When none holds, the base.
	 */
	public int pickVariant(long seed, StyleContext context) {
		if (this.variants.isEmpty()) {
			return -1;
		}
		boolean baseOk = When.holds(this.baseWhen, context);
		double[] w = weights(context);
		double total = baseOk ? this.baseWeight : 0;
		for (double x : w) {
			total += x;
		}
		if (total <= 0) {
			return -1;
		}
		double u = Noise.unit(seed, 0x7A1, 0, 0) * total;
		if (baseOk) {
			if (u < this.baseWeight) {
				return -1;
			}
			u -= this.baseWeight;
		}
		int last = -1;
		for (int i = 0; i < this.variants.size(); i++) {
			if (w[i] <= 0) {
				continue;
			}
			last = i;
			u -= w[i];
			if (u < 0) {
				return i;
			}
		}
		return last;
	}

	/**
	 * The weight of each variant for a context (0 where its condition fails). A sub-style competes with its whole weight
	 * wherever any of its dressings may be chosen: the shares of its fashions whose own conditions fail (the church
	 * fashion, for a house) go to those that hold. Fashions of the common manner keep their weights as they are.
	 */
	public double[] weights(StyleContext context) {
		double[] w = new double[this.variants.size()];
		Map<String, double[]> sums = new java.util.HashMap<>();
		for (int i = 0; i < w.length; i++) {
			Variant v = this.variants.get(i);
			boolean ok = When.holds(v.when(), context);
			w[i] = ok ? v.weight() : 0;
			if (v.substyle() != null) {
				double[] s = sums.computeIfAbsent(v.substyle(), k -> new double[2]);
				s[0] += v.weight();
				s[1] += w[i];
			}
		}
		for (int i = 0; i < w.length; i++) {
			Variant v = this.variants.get(i);
			if (v.substyle() != null && w[i] > 0) {
				double[] s = sums.get(v.substyle());
				w[i] *= s[0] / s[1];
			}
		}
		return w;
	}

	/** The indices of the dressings that could be chosen for a context (the base as -1). */
	public List<Integer> eligible(StyleContext context) {
		List<Integer> out = new ArrayList<>();
		if (When.holds(this.baseWhen, context) && this.baseWeight > 0) {
			out.add(-1);
		}
		for (int i = 0; i < this.variants.size(); i++) {
			if (When.holds(this.variants.get(i).when(), context) && this.variants.get(i).weight() > 0) {
				out.add(i);
			}
		}
		return out;
	}

	/**
	 * The regional overlay a context wears: the one its {@code region} param names ({@code none}: no region), else the
	 * highest-priority region whose condition holds, or null.
	 */
	public @Nullable Style regionFor(StyleContext context) {
		List<Style> rs = regions();
		if (context.region() != null) {
			if (context.region().equals("none")) {
				return null;
			}
			for (Style r : rs) {
				if (r.region.named(context.region())) {
					return r;
				}
			}
		}
		for (Style r : rs) {
			if (r.region.when() != null && r.region.when().test(context)) {
				return r;
			}
		}
		return null;
	}

	/**
	 * The view a building wears: this style with its region (chosen for the context; its own variant drawn from the seed),
	 * its local materials and its explicit station applied. The undressed style itself when none applies, so old plots
	 * build exactly as before. Views are cached.
	 */
	public Style dress(StyleContext context, long seed, @Nullable LocalMaterials local) {
		Style r = root();
		Style overlay = r.regionFor(context);
		int ov = overlay == null ? -1 : overlay.pickVariant(Noise.hash(seed, 0x2E61), context);
		Station station = context.explicitStation() ? context.station() : null;
		List<RoleOverride> over = new ArrayList<>();
		StringBuilder ok = new StringBuilder();
		for (int i = 0; i < r.overrides.size(); i++) {
			if (r.overrides.get(i).when().test(context)) {
				over.add(r.overrides.get(i));
				ok.append(i).append(',');
			}
		}
		if (overlay == null && local == null && station == null && over.isEmpty()) {
			return r;
		}
		String key = (overlay == null ? "-" : overlay.id() + "/" + ov) + "|" + (local == null ? "-" : local.signature()) + "|"
			+ (station == null ? "-" : station.getSerializedName()) + "|" + ok;
		Style view = r.dressed.get(key);
		if (view == null) {
			if (r.dressed.size() > 1024) {
				r.dressed.clear();
			}
			view = new Style(r.id, r.culture, r.name, r.baseName, r.base, r.tiers, r.habits, r.habitsJson, r.baseWeight, r.baseWhen, r.variants,
				r.stations, r.region, r.overrides, List.of(), r, new Dressing(r, overlay, ov, local, station, over));
			Style prev = r.dressed.putIfAbsent(key, view);
			if (prev != null) {
				view = prev;
			}
		}
		return view;
	}

	// ------------------------------------------------------------------------------------------------ reading

	/** The habits of a variant (-1 or out of range: the base), dressed for a dressed view. */
	public Habits habits(int variant) {
		if (this.dressing != null) {
			return this.dressing.habits(variant);
		}
		return rawHabits(variant);
	}

	Habits rawHabits(int variant) {
		return variant >= 0 && variant < this.variants.size() ? this.variants.get(variant).habits() : this.habits;
	}

	/**
	 * The roles that are what a building is built of (walls, gables, quoins, the timber frame): a fashion that names one
	 * keeps it at every tier, so a brick house stays brick and a log cottage logs as it prospers or decays, while the
	 * finish (roofs, chimneys, glass, plinths, doors) still follows the tier. A fashion's own {@code tiers} may still
	 * change them.
	 */
	public static boolean isFabric(Role role) {
		return role == Role.WALL || role == Role.GABLE || role == Role.WALL_ACCENT || role == Role.FRAME || role == Role.BEAM;
	}

	/** The habits JSON of a variant (do not modify). */
	JsonObject habitsJson(int variant) {
		return variant >= 0 && variant < this.variants.size() ? this.variants.get(variant).habitsJson() : this.habitsJson;
	}

	/**
	 * The palette of a role at a tier for a variant. Fashions: the tier's own override if the style has one, else the
	 * fashion's, else the base's. Sub-styles: the sub-style's tier override, else its roles, else the culture's. Dressed
	 * for a dressed view.
	 */
	public Palette palette(Role role, int tier, int variant) {
		if (this.dressing != null) {
			return this.dressing.palette(role, tier, variant);
		}
		return rawPalette(role, tier, variant);
	}

	Palette rawPalette(Role role, int tier, int variant) {
		if (variant >= 0 && variant < this.variants.size()) {
			Variant v = this.variants.get(variant);
			if (v.substyle() != null) {
				Palette p = tierChain(v.tiers(), role, tier);
				if (p == null) {
					p = v.roles().get(role);
				}
				if (p != null) {
					return p;
				}
			} else {
				Palette p = tierChain(v.tiers(), role, tier);
				if (p == null && !(isFabric(role) && v.roles().containsKey(role))) {
					p = tierChain(this.tiers, role, tier);
				}
				if (p != null) {
					return p;
				}
				p = v.roles().get(role);
				if (p != null) {
					return p;
				}
			}
		}
		Palette p = this.resolved[Math.clamp(tier, 0, 4)].get(role);
		return p != null ? p : StyleDefaults.palette(role);
	}

	/** The palette of a role at a tier (0–4, clamped), base dressing. Never null: missing roles fall back to vanilla defaults. */
	public Palette palette(Role role, int tier) {
		return palette(role, tier, -1);
	}

	/**
	 * What a region (or any style) itself says about a role, without vanilla defaults: its tier override, else its
	 * variant's role, else its base role, else null (the culture's stands).
	 */
	public @Nullable Palette own(Role role, int tier, int variant) {
		Palette p = tierChain(this.tiers, role, tier);
		if (p != null) {
			return p;
		}
		if (variant >= 0 && variant < this.variants.size()) {
			Variant v = this.variants.get(variant);
			p = tierChain(v.tiers(), role, tier);
			if (p == null) {
				p = v.roles().get(role);
			}
			if (p != null) {
				return p;
			}
		}
		return this.base.get(role);
	}

	/** Whether the style's own JSON names a role (in its base, a tier or any variant). */
	public boolean names(Role role) {
		if (this.base.containsKey(role)) {
			return true;
		}
		for (Map<Role, Palette> m : this.tiers.values()) {
			if (m.containsKey(role)) {
				return true;
			}
		}
		for (Variant v : this.variants) {
			if (v.roles().containsKey(role)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public String toString() {
		return this.id.toString();
	}
}
