package dev.voidmc.hearth.build.style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * A block family named in a style: one material that can be cut into the forms components ask for ({@link Form}).
 *
 * <p>Spec syntax (strings in the style JSON):
 * <pre>
 *   wood:minecraft:spruce      a wood: planks, log, wood, stripped logs, stairs, slab, fence, gate, door, trapdoor...
 *   dye:red                    a colour: wool, carpet, bed, banner, candle, stained glass and pane, terracotta
 *   minecraft:stone_bricks     a block and its cuts by vanilla naming (stone_brick_stairs, _slab, _wall)
 *   expanse:wisteria_planks|minecraft:cherry_planks   a fallback chain: the first that exists is used
 * </pre>
 * The namespace defaults to {@code minecraft}. Explicit per-form overrides ({@code "stairs": "..."}) come from the
 * style loader. Resolution is lazy (registries are complete only after every mod's init) and cached; an Expanse id
 * resolves to its fallback when Expanse is not installed.
 */
public final class Material {
	/** Resolves block ids; the default looks in the block registry and answers null for ids that are not registered. */
	public static final Function<Identifier, @Nullable Block> REGISTRY = id -> BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.getValue(id) : null;

	private final String spec;
	private final List<String> chain;
	private final Map<Form, String> overrides;
	private final Function<Identifier, @Nullable Block> lookup;
	private volatile @Nullable Map<Form, Block> resolved;
	private volatile @Nullable String resolvedFrom;

	private Material(String spec, Map<Form, String> overrides, Function<Identifier, @Nullable Block> lookup) {
		this.spec = spec;
		List<String> c = new ArrayList<>();
		for (String part : spec.split("\\|")) {
			String p = part.trim();
			if (!p.isEmpty()) {
				c.add(p);
			}
		}
		if (c.isEmpty()) {
			throw new IllegalArgumentException("empty material spec");
		}
		this.chain = List.copyOf(c);
		this.overrides = overrides.isEmpty() ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(overrides));
		this.lookup = lookup;
	}

	public static Material of(String spec) {
		return new Material(spec, Map.of(), REGISTRY);
	}

	public static Material of(String spec, Map<Form, String> overrides) {
		return new Material(spec, overrides, REGISTRY);
	}

	/** For tests and tools that resolve against something other than the live registry. */
	public static Material of(String spec, Map<Form, String> overrides, Function<Identifier, @Nullable Block> lookup) {
		return new Material(spec, overrides, lookup);
	}

	public String spec() {
		return this.spec;
	}

	/** The block that cuts this material in the given form, or null if the family has no such member. */
	public @Nullable Block block(Form form) {
		return forms().get(form);
	}

	public boolean has(Form form) {
		return forms().containsKey(form);
	}

	/** Which alternative of the fallback chain was used (for logs and the style report). */
	public String resolvedFrom() {
		forms();
		String r = this.resolvedFrom;
		return r == null ? "" : r;
	}

	/** True when the first choice resolved (no fallback used). */
	public boolean primary() {
		forms();
		return this.chain.get(0).equals(this.resolvedFrom);
	}

	private Map<Form, Block> forms() {
		Map<Form, Block> r = this.resolved;
		if (r == null) {
			synchronized (this) {
				r = this.resolved;
				if (r == null) {
					r = resolve();
					this.resolved = r;
				}
			}
		}
		return r;
	}

	private Map<Form, Block> resolve() {
		for (String alt : this.chain) {
			EnumMap<Form, Block> m = family(alt);
			if (m != null && !m.isEmpty()) {
				for (Map.Entry<Form, String> o : this.overrides.entrySet()) {
					Block b = find(o.getValue());
					if (b != null) {
						m.put(o.getKey(), b);
					}
				}
				this.resolvedFrom = alt;
				return Collections.unmodifiableMap(m);
			}
		}
		this.resolvedFrom = "";
		return Map.of();
	}

	private @Nullable Block find(String id) {
		Identifier key = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
		return key == null ? null : this.lookup.apply(key);
	}

	/** The family of one alternative, or null when its base block does not exist. */
	private @Nullable EnumMap<Form, Block> family(String alt) {
		String a = alt.toLowerCase(Locale.ROOT);
		if (a.startsWith("wood:")) {
			return wood(a.substring(5));
		}
		if (a.startsWith("dye:")) {
			return dye(a.substring(4));
		}
		return block(a);
	}

	private @Nullable EnumMap<Form, Block> wood(String id) {
		String ns = id.contains(":") ? id.substring(0, id.indexOf(':')) : "minecraft";
		String w = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		String p = ns + ":";
		Block planks = find(p + w + "_planks");
		if (planks == null) {
			return null;
		}
		EnumMap<Form, Block> m = new EnumMap<>(Form.class);
		m.put(Form.FULL, planks);
		m.put(Form.PLANKS, planks);
		boolean bamboo = w.equals("bamboo");
		putFirst(m, Form.LOG, bamboo ? new String[]{p + "bamboo_block"} : new String[]{p + w + "_log", p + w + "_stem"});
		putFirst(m, Form.WOOD, bamboo ? new String[]{p + "bamboo_block"} : new String[]{p + w + "_wood", p + w + "_hyphae", p + w + "_log", p + w + "_stem"});
		putFirst(m, Form.STRIPPED_LOG, bamboo ? new String[]{p + "stripped_bamboo_block"} : new String[]{p + "stripped_" + w + "_log", p + "stripped_" + w + "_stem"});
		putFirst(m, Form.STRIPPED_WOOD, bamboo ? new String[]{p + "stripped_bamboo_block"}
			: new String[]{p + "stripped_" + w + "_wood", p + "stripped_" + w + "_hyphae", p + "stripped_" + w + "_log", p + "stripped_" + w + "_stem"});
		String[][] cuts = {
			{"STAIRS", "_stairs"}, {"SLAB", "_slab"}, {"FENCE", "_fence"}, {"FENCE_GATE", "_fence_gate"}, {"DOOR", "_door"},
			{"TRAPDOOR", "_trapdoor"}, {"PRESSURE_PLATE", "_pressure_plate"}, {"BUTTON", "_button"}, {"SIGN", "_sign"},
			{"WALL_SIGN", "_wall_sign"}, {"HANGING_SIGN", "_hanging_sign"}, {"WALL_HANGING_SIGN", "_wall_hanging_sign"}, {"SHELF", "_shelf"}
		};
		for (String[] c : cuts) {
			Block b = find(p + w + c[1]);
			if (b != null) {
				m.put(Form.valueOf(c[0]), b);
			}
		}
		return m;
	}

	private @Nullable EnumMap<Form, Block> dye(String colour) {
		Block wool = find(colour + "_wool");
		if (wool == null) {
			return null;
		}
		EnumMap<Form, Block> m = new EnumMap<>(Form.class);
		m.put(Form.FULL, wool);
		String[][] cuts = {
			{"CARPET", "_carpet"}, {"BED", "_bed"}, {"BANNER", "_banner"}, {"WALL_BANNER", "_wall_banner"}, {"CANDLE", "_candle"},
			{"PANE", "_stained_glass_pane"}, {"GLASS", "_stained_glass"}, {"TERRACOTTA", "_terracotta"}, {"CONCRETE", "_concrete"},
			{"STAIRS", "_wool_stairs"}, {"SLAB", "_wool_slab"}
		};
		for (String[] c : cuts) {
			Block b = find(colour + c[1]);
			if (b != null) {
				m.put(Form.valueOf(c[0]), b);
			}
		}
		return m;
	}

	private @Nullable EnumMap<Form, Block> block(String id) {
		String full = id.contains(":") ? id : "minecraft:" + id;
		Block base = find(full);
		if (base == null) {
			return null;
		}
		String ns = full.substring(0, full.indexOf(':') + 1);
		String name = full.substring(full.indexOf(':') + 1);
		if (name.endsWith("_planks")) {
			EnumMap<Form, Block> w = wood(ns + name.substring(0, name.length() - "_planks".length()));
			if (w != null) {
				return w;
			}
		}
		EnumMap<Form, Block> m = new EnumMap<>(Form.class);
		m.put(Form.FULL, base);
		List<String> stems = new ArrayList<>();
		if (name.endsWith("bricks") || name.endsWith("tiles")) {
			stems.add(name.substring(0, name.length() - 1));
		}
		if (name.endsWith("_block")) {
			stems.add(name.substring(0, name.length() - "_block".length()));
		}
		stems.add(name);
		for (String stem : stems) {
			putIfAbsentFound(m, Form.STAIRS, ns + stem + "_stairs");
			putIfAbsentFound(m, Form.SLAB, ns + stem + "_slab");
			putIfAbsentFound(m, Form.WALL, ns + stem + "_wall");
			putIfAbsentFound(m, Form.FENCE, ns + stem + "_fence");
		}
		if (name.endsWith("_log") || name.endsWith("_stem")) {
			m.put(Form.LOG, base);
		}
		if (name.endsWith("_wood") || name.endsWith("_hyphae")) {
			m.put(Form.WOOD, base);
		}
		if (name.endsWith("glass")) {
			m.put(Form.GLASS, base);
			putIfAbsentFound(m, Form.PANE, ns + name + "_pane");
		}
		if (name.endsWith("_pane")) {
			m.put(Form.PANE, base);
			Block g = find(ns + name.substring(0, name.length() - "_pane".length()));
			if (g != null) {
				m.put(Form.GLASS, g);
			}
		}
		if (name.endsWith("_bars")) {
			m.put(Form.PANE, base);
		}
		return m;
	}

	private void putFirst(EnumMap<Form, Block> m, Form form, String[] ids) {
		for (String id : ids) {
			Block b = find(id);
			if (b != null) {
				m.put(form, b);
				return;
			}
		}
	}

	private void putIfAbsentFound(EnumMap<Form, Block> m, Form form, String id) {
		if (!m.containsKey(form)) {
			Block b = find(id);
			if (b != null) {
				m.put(form, b);
			}
		}
	}

	@Override
	public String toString() {
		return this.spec;
	}
}
