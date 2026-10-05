package dev.voidmc.hearth.build.style;

import dev.voidmc.hearth.build.style.MaterialFamilies.Grade;
import dev.voidmc.hearth.build.style.MaterialFamilies.Kind;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.Nullable;

/**
 * The materials a settlement's territory gives (docs/LIVING_WORLD.md §7.2): the plot param {@code local}, a compound of
 * keys to block ids the planner chooses from the town's resource sites, resolved into the culture's palettes so a town
 * by a birch wood builds in birch and a town over limestone in limestone.
 *
 * <pre>
 *   "local": {"wood": "minecraft:birch", "stone": "expanse:limestone"}          by kind: every role of that kind
 *   "local": {"wall": "minecraft:spruce_planks", "foundation": "minecraft:tuff"}  by role (Role.key()): that role
 * </pre>
 *
 * Kind keys are {@code wood} (or {@code timber}), {@code stone}, {@code clay} (or {@code brick}) and {@code thatch}; a
 * value is a block id, a wood's name ({@code birch}, {@code expanse:pine}) or a material spec. Role keys win over kind
 * keys.
 *
 * <p>How it blends, rather than painting a house one block:
 * <ul>
 *   <li>A kind key changes only the roles whose material is of that kind and that a builder would take from the land:
 *   timber for log and board walls, frames, floors, fences, stilts and furniture; stone for footings, plinths, rubble
 *   walls, quoins, chimneys, paving and carved accents. Roofs, doors, shutters, window joinery, railings, glass, light and
 *   cloth keep the culture's (slate, painted trim and glass are bought in); a role key may still name any of them.</li>
 *   <li>Stone keeps the culture's grading: its rubble footings become the local stone's rubble, its dressed plinth the
 *   local dressed stone, its chiselled accents carved local stone; moss and cracks the local stone lacks stay as the
 *   culture had them.</li>
 *   <li>Some of the culture's own material stays in the mix (the station's {@link StationFinish#localShare}: a hovel is
 *   all local, a lord's house mostly bought in), and per-building choices (one frame wood per house) take the local
 *   wood on most houses, not all.</li>
 *   <li>Walls are built of hewn logs (the local wood's stripped log), not of bark, and so are a light timber's posts and
 *   stakes (no birch bark on the surface); a light local timber (birch, pale oak, aspen...) never becomes frames and
 *   beams, which keep the culture's darker wood so a house keeps its lines (a frame against light infill, the corners
 *   of a log house).</li>
 *   <li>The culture's habits may refuse a kind or a role ({@link Habits.Local}).</li>
 * </ul>
 */
public final class LocalMaterials {
	/** One local material: what kind it is and how to cut it. */
	record Local(Kind kind, String spec, @Nullable String wood, MaterialFamilies.@Nullable Stone stone) {
	}

	private static final Set<Role> NEVER_BY_KIND = EnumSet.of(Role.ROOF, Role.ROOF_TRIM, Role.DOOR, Role.SHUTTER, Role.WINDOW, Role.RAILING,
		Role.GLASS, Role.LIGHT, Role.CLOTH, Role.GARDEN, Role.CROPS, Role.HEDGE, Role.TREE_LOG, Role.TREE_LEAVES, Role.GROUND, Role.FILL);
	private static final Set<Role> WOOD_ROLES = EnumSet.of(Role.WALL, Role.GABLE, Role.FRAME, Role.BEAM, Role.FLOOR, Role.CEILING, Role.STILT,
		Role.FENCE, Role.FURNITURE, Role.PAVING, Role.PLINTH, Role.FOUNDATION, Role.WALL_ACCENT);
	private static final Set<Role> STONE_ROLES = EnumSet.of(Role.FOUNDATION, Role.PLINTH, Role.WALL, Role.WALL_ACCENT, Role.GABLE, Role.CHIMNEY,
		Role.PAVING, Role.ACCENT, Role.FENCE, Role.PATH, Role.FLOOR);
	private static final Set<Role> CLAY_ROLES = EnumSet.of(Role.WALL, Role.GABLE, Role.CHIMNEY, Role.PLINTH, Role.FLOOR, Role.PAVING);
	private static final Map<String, Material> MATERIALS = new ConcurrentHashMap<>();

	private final Map<Role, Local> byRole;
	private final Map<Kind, Local> byKind;
	private final String signature;

	private LocalMaterials(Map<Role, Local> byRole, Map<Kind, Local> byKind, String signature) {
		this.byRole = byRole;
		this.byKind = byKind;
		this.signature = signature;
	}

	/** The local materials of a plot's {@code local} param, or null when it has none that resolve. */
	public static @Nullable LocalMaterials of(@Nullable CompoundTag tag) {
		if (tag == null || tag.isEmpty()) {
			return null;
		}
		Map<Role, Local> byRole = new EnumMap<>(Role.class);
		Map<Kind, Local> byKind = new EnumMap<>(Kind.class);
		TreeMap<String, String> sig = new TreeMap<>();
		for (String key : tag.keySet()) {
			String value = tag.getStringOr(key, "");
			if (value.isBlank()) {
				continue;
			}
			Local l = parse(value);
			if (l == null) {
				continue;
			}
			String k = key.toLowerCase(Locale.ROOT);
			Role r = Role.byKey(k);
			if (r != null) {
				byRole.put(r, l);
			} else {
				Kind kind = switch (k) {
					case "wood", "timber" -> Kind.WOOD;
					case "stone" -> Kind.STONE;
					case "clay", "brick" -> Kind.CLAY;
					case "thatch" -> Kind.THATCH;
					default -> null;
				};
				if (kind == null || kind != l.kind) {
					continue;
				}
				byKind.put(kind, l);
			}
			sig.put(k, l.spec);
		}
		if (byRole.isEmpty() && byKind.isEmpty()) {
			return null;
		}
		return new LocalMaterials(byRole, byKind, sig.toString());
	}

	static @Nullable Local parse(String value) {
		String v = value.trim().toLowerCase(Locale.ROOT);
		String wood = MaterialFamilies.wood(v);
		if (wood != null) {
			return new Local(Kind.WOOD, MaterialFamilies.woodSpec(wood), wood, null);
		}
		String first = v.contains("|") ? v.substring(0, v.indexOf('|')) : v;
		MaterialFamilies.Stone stone = MaterialFamilies.stone(first);
		if (stone != null) {
			return new Local(Kind.STONE, stone.name(), null, stone);
		}
		Material m = material(v);
		if (m.resolvedFrom().isEmpty()) {
			return null;
		}
		return new Local(MaterialFamilies.kind(m), v, null, null);
	}

	/** A stable key for caches (equal materials, equal key). */
	public String signature() {
		return this.signature;
	}

	static Material material(String spec) {
		return MATERIALS.computeIfAbsent(spec, MaterialFamilies::material);
	}

	/**
	 * A wood as walls are built of it: logs hewn of their bark (a birch wall is pale timber, not bark). A light timber's
	 * posts, columns and stakes are hewn too: birch bark (white with black flecks) on a post reads as a painted pole.
	 */
	static Material hewn(Local l) {
		String w = l.wood;
		String ns = w.substring(0, w.indexOf(':') + 1);
		String name = w.substring(w.indexOf(':') + 1);
		return MATERIALS.computeIfAbsent(l.spec + "#hewn", k -> Material.of(l.spec,
			Map.of(Form.LOG, ns + "stripped_" + name + "_log", Form.WOOD, ns + "stripped_" + name + "_wood")));
	}

	/**
	 * A role's palette with the territory's material blended in (unchanged when nothing local applies).
	 *
	 * @param share how much of the local material the household uses (0–1)
	 */
	public Palette apply(Role role, Palette p, Habits habits, double share) {
		Local l = this.byRole.get(role);
		boolean explicit = l != null;
		Kind mainKind = MaterialFamilies.kind(main(p).material());
		if (l == null) {
			if (NEVER_BY_KIND.contains(role)) {
				return p;
			}
			l = this.byKind.get(mainKind);
			if (l == null) {
				return p;
			}
			Set<Role> fits = switch (l.kind) {
				case WOOD -> WOOD_ROLES;
				case STONE -> STONE_ROLES;
				case CLAY -> CLAY_ROLES;
				case THATCH -> EnumSet.of(Role.THATCH);
				default -> Set.of();
			};
			if (!fits.contains(role)) {
				return p;
			}
			// a light timber makes a house without lines (a frame vanishes against light infill, a log house loses its dark
			// corners): its frames and beams stay the culture's, and the local wood goes into walls, floors and boards
			if (l.kind == Kind.WOOD && (role == Role.FRAME || role == Role.BEAM) && MaterialFamilies.tone(l.wood) == MaterialFamilies.Tone.LIGHT) {
				return p;
			}
		}
		if (!habits.local().allows(role, explicit ? l.kind : mainKind)) {
			return p;
		}
		double s = Math.clamp(share, 0, 1);
		boolean perBuilding = role.perBuilding();
		LinkedHashMap<String, double[]> out = new LinkedHashMap<>();
		Map<String, Material> mats = new LinkedHashMap<>();
		int mainIndex = p.entries().indexOf(main(p));
		for (int i = 0; i < p.entries().size(); i++) {
			Palette.Entry e = p.entries().get(i);
			Material target = target(l, e, i == mainIndex, explicit, role == Role.WALL || role == Role.GABLE);
			if (target == null) {
				// moss and cracks of the culture's stone kept in a local stone that has none of its own: fewer of them
				boolean weather = l.kind == Kind.STONE && (e.low() > 0 || e.north() > 0) && MaterialFamilies.kind(e.material()) == Kind.STONE;
				add(out, mats, e.material(), weather ? new Palette.Entry(e.material(), e.weight() * 0.5, e.low(), e.high(), e.north(), e.corner()) : e);
				continue;
			}
			double local = perBuilding ? 3 * s : s;
			add(out, mats, target, new Palette.Entry(target, e.weight() * local, e.low(), e.high(), e.north(), e.corner()));
			if (s < 1) {
				add(out, mats, e.material(), new Palette.Entry(e.material(), e.weight() * (1 - s), e.low(), e.high(), e.north(), e.corner()));
			}
		}
		List<Palette.Entry> entries = new ArrayList<>();
		for (Map.Entry<String, double[]> e : out.entrySet()) {
			double[] v = e.getValue();
			entries.add(new Palette.Entry(mats.get(e.getKey()), v[0], v[1], v[2], v[3], v[4]));
		}
		// the local material leads (a per-building role takes it first)
		entries.sort((a, b) -> Double.compare(b.weight(), a.weight()));
		return new Palette(entries, p.patches(), p.patchShare());
	}

	private static void add(Map<String, double[]> out, Map<String, Material> mats, Material m, Palette.Entry e) {
		String k = m.spec() + "#" + System.identityHashCode(m);
		mats.putIfAbsent(k, m);
		double[] v = out.computeIfAbsent(k, x -> new double[5]);
		v[0] += e.weight();
		v[1] = Math.max(v[1], e.low());
		v[2] = Math.max(v[2], e.high());
		v[3] = Math.max(v[3], e.north());
		v[4] = Math.max(v[4], e.corner());
	}

	/** The palette's leading material: the heaviest entry (the first on a tie). */
	static Palette.Entry main(Palette p) {
		Palette.Entry best = p.entries().get(0);
		for (Palette.Entry e : p.entries()) {
			if (e.weight() > best.weight()) {
				best = e;
			}
		}
		return best;
	}

	/** What an entry becomes in the local material, or null to keep it as it is. */
	private static @Nullable Material target(Local l, Palette.Entry e, boolean main, boolean explicit, boolean walling) {
		Material m = e.material();
		Kind k = MaterialFamilies.kind(m);
		switch (l.kind) {
			case WOOD -> {
				if (k == Kind.WOOD || main && explicit) {
					return walling || MaterialFamilies.tone(l.wood) == MaterialFamilies.Tone.LIGHT ? hewn(l) : material(l.spec);
				}
				return null;
			}
			case STONE -> {
				String from = m.resolvedFrom();
				Object[] g = from.isEmpty() ? null : MaterialFamilies.graded(from);
				if (g != null && k == Kind.STONE) {
					Grade grade = (Grade) g[1];
					// moss and cracks the local stone does not have stay as they were
					if ((grade == Grade.MOSSY_RUBBLE || grade == Grade.MOSSY_DRESSED || grade == Grade.CRACKED) && !l.stone.has(grade)
						&& !(grade == Grade.MOSSY_RUBBLE && l.stone.has(Grade.MOSSY_DRESSED))) {
						return null;
					}
					return material(l.stone.spec(grade));
				}
				return main && explicit ? material(l.stone.spec(Grade.NATURAL)) : null;
			}
			default -> {
				return main || k == l.kind ? material(l.spec) : null;
			}
		}
	}
}
