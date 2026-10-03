package dev.voidmc.hearth.build.style;

import dev.voidmc.hearth.api.Culture;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
 * <p>Variants are the fashions within a culture: whole alternative dressings (brick nogging instead of plaster, a red
 * painted house among the log ones) chosen per building by seed, each overriding some roles and habits of the base.
 * A tier's own overrides still win over a variant's (finish before fashion).
 */
public final class Style {
	private final Identifier id;
	private final @Nullable Culture culture;
	private final String name;
	private final Map<Role, Palette> base;
	private final Map<Integer, Map<Role, Palette>> tiers;
	private final Habits habits;
	private final double baseWeight;
	private final List<Variant> variants;
	@SuppressWarnings("unchecked")
	private final Map<Role, Palette>[] resolved = new Map[5];

	/** One fashion: a name, its weight against the base dressing's, and what it changes. */
	public record Variant(String name, double weight, Map<Role, Palette> roles, Habits habits) {
		public Variant {
			roles = Map.copyOf(roles);
		}
	}

	public Style(Identifier id, @Nullable Culture culture, String name, Map<Role, Palette> base, Map<Integer, Map<Role, Palette>> tiers, Habits habits) {
		this(id, culture, name, base, tiers, habits, 1.0, List.of());
	}

	public Style(Identifier id, @Nullable Culture culture, String name, Map<Role, Palette> base, Map<Integer, Map<Role, Palette>> tiers, Habits habits,
		double baseWeight, List<Variant> variants) {
		this.baseWeight = baseWeight;
		this.variants = List.copyOf(variants);
		this.id = id;
		this.culture = culture;
		this.name = name;
		this.base = Map.copyOf(base);
		this.tiers = Map.copyOf(tiers);
		this.habits = habits;
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

	private @Nullable Palette lookup(Role role, int tier) {
		int t = tier;
		while (true) {
			Map<Role, Palette> m = this.tiers.get(t);
			if (m != null && m.containsKey(role)) {
				return m.get(role);
			}
			if (t == 2) {
				break;
			}
			t += t > 2 ? -1 : 1;
		}
		Palette p = this.base.get(role);
		return p != null ? p : StyleDefaults.palette(role);
	}

	public Identifier id() {
		return this.id;
	}

	/** The culture this style is the default of, or null for an extra style. */
	public @Nullable Culture culture() {
		return this.culture;
	}

	public String name() {
		return this.name;
	}

	public Habits habits() {
		return this.habits;
	}

	public List<Variant> variants() {
		return this.variants;
	}

	/** A variant for a building: -1 for the base dressing, else an index into {@link #variants()}; by weight. */
	public int pickVariant(long seed) {
		if (this.variants.isEmpty()) {
			return -1;
		}
		double total = this.baseWeight;
		for (Variant v : this.variants) {
			total += v.weight();
		}
		double u = dev.voidmc.hearth.build.draft.Noise.unit(seed, 0x7A1, 0, 0) * total;
		if (u < this.baseWeight) {
			return -1;
		}
		u -= this.baseWeight;
		for (int i = 0; i < this.variants.size(); i++) {
			u -= this.variants.get(i).weight();
			if (u < 0) {
				return i;
			}
		}
		return this.variants.size() - 1;
	}

	/** The habits of a variant (-1 or out of range: the base). */
	public Habits habits(int variant) {
		return variant >= 0 && variant < this.variants.size() ? this.variants.get(variant).habits() : this.habits;
	}

	/**
	 * The palette of a role at a tier for a variant: the tier's own override if it has one, else the variant's, else
	 * the base's.
	 */
	public Palette palette(Role role, int tier, int variant) {
		if (variant >= 0 && variant < this.variants.size()) {
			int t = Math.clamp(tier, 0, 4);
			while (true) {
				Map<Role, Palette> m = this.tiers.get(t);
				if (m != null && m.containsKey(role)) {
					return m.get(role);
				}
				if (t == 2) {
					break;
				}
				t += t > 2 ? -1 : 1;
			}
			Palette p = this.variants.get(variant).roles().get(role);
			if (p != null) {
				return p;
			}
		}
		return palette(role, tier);
	}

	/** The palette of a role at a tier (0–4, clamped). Never null: missing roles fall back to vanilla defaults. */
	public Palette palette(Role role, int tier) {
		Palette p = this.resolved[Math.clamp(tier, 0, 4)].get(role);
		return p != null ? p : StyleDefaults.palette(role);
	}

	@Override
	public String toString() {
		return this.id.toString();
	}
}
