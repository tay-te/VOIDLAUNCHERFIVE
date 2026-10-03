package dev.voidmc.hearth.build.style;

import dev.voidmc.hearth.api.Culture;
import java.util.EnumMap;
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
 */
public final class Style {
	private final Identifier id;
	private final @Nullable Culture culture;
	private final String name;
	private final Map<Role, Palette> base;
	private final Map<Integer, Map<Role, Palette>> tiers;
	private final Habits habits;
	@SuppressWarnings("unchecked")
	private final Map<Role, Palette>[] resolved = new Map[5];

	public Style(Identifier id, @Nullable Culture culture, String name, Map<Role, Palette> base, Map<Integer, Map<Role, Palette>> tiers, Habits habits) {
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
