package dev.voidmc.hearth.build.style;

import com.google.gson.JsonObject;
import dev.voidmc.hearth.api.Station;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * How one kind of building wears a style ({@link Style#dress}): a regional overlay over the culture's dressing, the
 * territory's materials blended in, the household's station applied. Answers palettes and habits for any tier and
 * variant, lazily, and remembers them.
 *
 * <p>Order of resolution for a role at a plot tier:
 * <ol>
 *   <li>the finish tier: the plot tier moved by the station ({@link StationFinish#tier});</li>
 *   <li>the region's own palette for the role at that tier, else the culture dressing's with the culture's matching
 *   overrides ({@link Style.RoleOverride}) over it;</li>
 *   <li>local materials blended in ({@link LocalMaterials#apply}, with the station's share);</li>
 *   <li>the station's own roles (a lord's copper lanterns) over all of that;</li>
 *   <li>the station's weathering, and a poor household's patched roof.</li>
 * </ol>
 */
final class Dressing {
	final Style root;
	final @Nullable Style overlay;
	final int overlayVariant;
	final @Nullable LocalMaterials local;
	final @Nullable Station station;
	final StationFinish finish;
	final List<Style.RoleOverride> overrides;
	private final ConcurrentHashMap<Long, Palette> palettes = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Integer, Habits> habits = new ConcurrentHashMap<>();

	Dressing(Style root, @Nullable Style overlay, int overlayVariant, @Nullable LocalMaterials local, @Nullable Station station,
		List<Style.RoleOverride> overrides) {
		this.overrides = List.copyOf(overrides);
		this.root = root;
		this.overlay = overlay;
		this.overlayVariant = overlayVariant;
		this.local = local;
		this.station = station;
		this.finish = station == null ? StationFinish.NONE : root.station(station);
	}

	Palette palette(Role role, int tier, int variant) {
		long key = ((long) role.ordinal() << 40) | ((long) (Math.clamp(tier, 0, 4)) << 32) | ((variant + 1) & 0xFFFFFFFFL);
		Palette p = this.palettes.get(key);
		if (p == null) {
			p = compute(role, tier, variant);
			this.palettes.putIfAbsent(key, p);
		}
		return p;
	}

	private Palette compute(Role role, int tier, int variant) {
		int t = this.finish.tier(tier);
		Palette p = this.overlay == null ? null : this.overlay.own(role, t, this.overlayVariant);
		if (p == null) {
			p = this.root.rawPalette(role, t, variant);
			for (Style.RoleOverride o : this.overrides) {
				Palette op = o.palette(role, t);
				if (op != null) {
					p = op;
				}
			}
		}
		if (this.local != null) {
			p = this.local.apply(role, p, habits(variant), this.finish.localShare());
		}
		if (this.station != null) {
			Palette own = this.finish.roles().get(role);
			if (own != null) {
				p = own;
			}
			p = p.weathered(this.finish.weathering());
			if (this.finish.patchedRoof() && (role == Role.ROOF || role == Role.THATCH)) {
				p = patched(p, role, variant);
			}
		}
		return p;
	}

	/** A roof mended in patches: the style's roughest roof where it differs, and a plain patch for the kind of roof. */
	private Palette patched(Palette p, Role role, int variant) {
		Material main = LocalMaterials.main(p).material();
		List<Palette.Entry> patches = new ArrayList<>();
		Material rough = this.root.rawPalette(role, 0, variant).entries().get(0).material();
		if (!rough.spec().equals(main.spec())) {
			patches.add(new Palette.Entry(rough, 1, 0, 0, 0, 0));
		}
		patches.add(new Palette.Entry(LocalMaterials.material(patchFor(main)), 1, 0, 0, 0, 0));
		patches.addAll(p.patches());
		return p.withPatches(patches, Math.max(0.3, p.patchShare()));
	}

	/** A patch that reads against a roof: boards on thatch and tin, rubble slate on slate, the other timber on shingles. */
	static String patchFor(Material roof) {
		String from = roof.resolvedFrom().toLowerCase(Locale.ROOT);
		if (from.startsWith("wood:")) {
			String wood = MaterialFamilies.wood(from);
			return wood != null && MaterialFamilies.tone(wood) == MaterialFamilies.Tone.DARK ? "wood:minecraft:oak" : "wood:minecraft:spruce";
		}
		if (from.contains("deepslate") || from.contains("blackstone") || from.contains("tuff")) {
			return "minecraft:cobbled_deepslate";
		}
		if (from.contains("brick") || from.contains("terracotta") || from.contains("granite")) {
			return from.contains("mud_brick") ? "minecraft:packed_mud" : "minecraft:mud_bricks";
		}
		if (from.contains("copper") || from.contains("hay") || from.contains("bamboo")) {
			return "wood:minecraft:spruce";
		}
		return "minecraft:mossy_cobblestone";
	}

	Habits habits(int variant) {
		if (this.overlay == null) {
			return this.root.rawHabits(variant);
		}
		JsonObject patch = this.overlay.habitsJson(this.overlayVariant);
		if (patch.isEmpty()) {
			return this.root.rawHabits(variant);
		}
		Habits h = this.habits.get(variant);
		if (h == null) {
			h = Habits.parse(StyleLoader.mergeHabits(this.root.habitsJson(variant), patch));
			this.habits.putIfAbsent(variant, h);
		}
		return h;
	}
}
