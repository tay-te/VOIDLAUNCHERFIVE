package dev.voidmc.hearth.build.style;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What a builder knows about materials: which stones are one stone cut different ways (rubble, the natural block,
 * dressed bricks, polished ashlar, carved work, moss and cracks), how light a timber is, and what kind of thing a material
 * is (timber, stone, fired clay, render, thatch). Local materials ({@link LocalMaterials}) use it to put a territory's
 * stone in place of a culture's while keeping the culture's grading (its rubble footings, dressed plinths and carved
 * accents), and to keep a timber frame dark against light infill.
 *
 * <p>Expanse stones carry their vanilla stand-ins, so the families resolve with or without Expanse.
 */
public final class MaterialFamilies {
	private MaterialFamilies() {
	}

	/** What kind of material a block family is. */
	public enum Kind {
		WOOD, STONE, CLAY, RENDER, THATCH, OTHER
	}

	/** How a stone is worked. */
	public enum Grade {
		/** Rough rubble (cobblestone, cobbled deepslate). */
		RUBBLE,
		/** The stone as quarried (stone, tuff, sandstone, limestone). */
		NATURAL,
		/** Squared blocks laid in courses (stone bricks, tuff bricks, cut sandstone). */
		DRESSED,
		/** Polished or smooth ashlar. */
		FINE,
		/** Carved and chiselled work. */
		CARVED,
		/** Rubble with moss in it. */
		MOSSY_RUBBLE,
		/** Dressed stone with moss in it. */
		MOSSY_DRESSED,
		/** Cracked dressed stone. */
		CRACKED,
		/** Thin tiles (roofs, floors). */
		TILE
	}

	/** One stone and its cuts: a material spec per grade (with fallbacks for Expanse stones). */
	public record Stone(String name, Map<Grade, String> grades) {
		/** The spec for a grade, or the nearest grade the stone has. */
		public String spec(Grade g) {
			for (Grade n : nearest(g)) {
				String s = this.grades.get(n);
				if (s != null) {
					return s;
				}
			}
			return this.grades.values().iterator().next();
		}

		/** Whether the stone has this grade itself (not by substitution). */
		public boolean has(Grade g) {
			return this.grades.containsKey(g);
		}
	}

	private static final Map<String, Stone> STONES = new HashMap<>();
	/** Block path (namespace included) → (stone, grade). */
	private static final Map<String, Object[]> BY_BLOCK = new HashMap<>();

	private static void stone(String name, Object... gradeSpecs) {
		EnumMap<Grade, String> m = new EnumMap<>(Grade.class);
		for (int i = 0; i < gradeSpecs.length; i += 2) {
			m.put((Grade) gradeSpecs[i], (String) gradeSpecs[i + 1]);
		}
		Stone s = new Stone(name, m);
		STONES.put(name, s);
		for (Map.Entry<Grade, String> e : m.entrySet()) {
			for (String alt : e.getValue().replace("{", "").replace("}", "").split("\\|")) {
				String id = alt.contains(":") ? alt : "minecraft:" + alt;
				BY_BLOCK.putIfAbsent(id, new Object[]{s, e.getKey()});
			}
		}
	}

	static {
		stone("stone", Grade.RUBBLE, "minecraft:cobblestone", Grade.NATURAL, "minecraft:stone", Grade.DRESSED, "minecraft:stone_bricks",
			Grade.FINE, "minecraft:smooth_stone", Grade.CARVED, "minecraft:chiseled_stone_bricks", Grade.MOSSY_RUBBLE, "minecraft:mossy_cobblestone",
			Grade.MOSSY_DRESSED, "minecraft:mossy_stone_bricks", Grade.CRACKED, "minecraft:cracked_stone_bricks");
		stone("andesite", Grade.NATURAL, "minecraft:andesite", Grade.FINE, "minecraft:polished_andesite");
		stone("diorite", Grade.NATURAL, "minecraft:diorite", Grade.FINE, "minecraft:polished_diorite");
		stone("granite", Grade.NATURAL, "minecraft:granite", Grade.FINE, "minecraft:polished_granite");
		stone("deepslate", Grade.RUBBLE, "minecraft:cobbled_deepslate", Grade.DRESSED, "minecraft:deepslate_bricks", Grade.FINE,
			"minecraft:polished_deepslate", Grade.CARVED, "minecraft:chiseled_deepslate", Grade.CRACKED, "minecraft:cracked_deepslate_bricks",
			Grade.TILE, "minecraft:deepslate_tiles");
		BY_BLOCK.put("minecraft:deepslate", new Object[]{STONES.get("deepslate"), Grade.NATURAL});
		BY_BLOCK.put("minecraft:cracked_deepslate_tiles", new Object[]{STONES.get("deepslate"), Grade.TILE});
		stone("tuff", Grade.NATURAL, "minecraft:tuff", Grade.DRESSED, "minecraft:tuff_bricks", Grade.FINE, "minecraft:polished_tuff",
			Grade.CARVED, "minecraft:chiseled_tuff_bricks");
		BY_BLOCK.put("minecraft:chiseled_tuff", new Object[]{STONES.get("tuff"), Grade.CARVED});
		stone("blackstone", Grade.NATURAL, "minecraft:blackstone", Grade.DRESSED, "minecraft:polished_blackstone_bricks", Grade.FINE,
			"minecraft:polished_blackstone", Grade.CARVED, "minecraft:chiseled_polished_blackstone", Grade.CRACKED,
			"minecraft:cracked_polished_blackstone_bricks");
		// basalt has no cuts of its own: its stairs, slabs and walls are polished deepslate, the nearest grey
		stone("basalt", Grade.NATURAL, "{minecraft:smooth_basalt}", Grade.DRESSED, "{minecraft:polished_basalt}", Grade.FINE,
			"{minecraft:smooth_basalt}", Grade.CARVED, "minecraft:chiseled_polished_blackstone");
		BY_BLOCK.put("minecraft:basalt", new Object[]{STONES.get("basalt"), Grade.NATURAL});
		BY_BLOCK.put("minecraft:smooth_basalt", new Object[]{STONES.get("basalt"), Grade.NATURAL});
		BY_BLOCK.put("minecraft:polished_basalt", new Object[]{STONES.get("basalt"), Grade.DRESSED});
		stone("sandstone", Grade.NATURAL, "minecraft:sandstone", Grade.DRESSED, "minecraft:cut_sandstone", Grade.FINE, "minecraft:smooth_sandstone",
			Grade.CARVED, "minecraft:chiseled_sandstone");
		stone("red_sandstone", Grade.NATURAL, "minecraft:red_sandstone", Grade.DRESSED, "minecraft:cut_red_sandstone", Grade.FINE,
			"minecraft:smooth_red_sandstone", Grade.CARVED, "minecraft:chiseled_red_sandstone");
		stone("limestone", Grade.NATURAL, "expanse:limestone|minecraft:diorite", Grade.DRESSED, "expanse:limestone_bricks|minecraft:polished_diorite",
			Grade.FINE, "expanse:polished_limestone|minecraft:smooth_stone", Grade.CARVED, "expanse:chiseled_limestone|minecraft:chiseled_stone_bricks",
			Grade.MOSSY_DRESSED, "expanse:mossy_limestone|minecraft:mossy_stone_bricks");
		stone("opal_sandstone", Grade.NATURAL, "expanse:opal_sandstone|minecraft:sandstone", Grade.DRESSED,
			"expanse:cut_opal_sandstone|minecraft:cut_sandstone", Grade.FINE, "expanse:smooth_opal_sandstone|minecraft:smooth_sandstone", Grade.CARVED,
			"expanse:chiseled_opal_sandstone|minecraft:chiseled_sandstone");
		stone("mud", Grade.NATURAL, "minecraft:packed_mud", Grade.DRESSED, "minecraft:mud_bricks");
		stone("sulfur", Grade.NATURAL, "minecraft:sulfur", Grade.DRESSED, "minecraft:sulfur_bricks", Grade.FINE, "minecraft:polished_sulfur",
			Grade.CARVED, "minecraft:chiseled_sulfur");
		stone("cinnabar", Grade.NATURAL, "minecraft:cinnabar", Grade.DRESSED, "minecraft:cinnabar_bricks", Grade.FINE, "minecraft:polished_cinnabar",
			Grade.CARVED, "minecraft:chiseled_cinnabar");
		stone("prismarine", Grade.NATURAL, "minecraft:prismarine", Grade.DRESSED, "minecraft:prismarine_bricks", Grade.FINE,
			"minecraft:dark_prismarine");
		stone("quartz", Grade.NATURAL, "minecraft:quartz_block", Grade.DRESSED, "minecraft:quartz_bricks", Grade.FINE, "minecraft:smooth_quartz",
			Grade.CARVED, "minecraft:chiseled_quartz_block");
		stone("end_stone", Grade.NATURAL, "minecraft:end_stone", Grade.DRESSED, "minecraft:end_stone_bricks");
		stone("calcite", Grade.NATURAL, "{minecraft:calcite}", Grade.DRESSED, "minecraft:polished_diorite", Grade.FINE, "{minecraft:calcite}");
		BY_BLOCK.put("minecraft:calcite", new Object[]{STONES.get("calcite"), Grade.NATURAL});
	}

	/** The order a missing grade is substituted in. */
	static List<Grade> nearest(Grade g) {
		return switch (g) {
			case RUBBLE -> List.of(Grade.RUBBLE, Grade.NATURAL, Grade.DRESSED, Grade.FINE);
			case NATURAL -> List.of(Grade.NATURAL, Grade.RUBBLE, Grade.DRESSED, Grade.FINE);
			case DRESSED -> List.of(Grade.DRESSED, Grade.FINE, Grade.NATURAL, Grade.RUBBLE);
			case FINE -> List.of(Grade.FINE, Grade.DRESSED, Grade.NATURAL, Grade.RUBBLE);
			case CARVED -> List.of(Grade.CARVED, Grade.DRESSED, Grade.FINE, Grade.NATURAL);
			case MOSSY_RUBBLE -> List.of(Grade.MOSSY_RUBBLE, Grade.MOSSY_DRESSED, Grade.RUBBLE, Grade.NATURAL);
			case MOSSY_DRESSED -> List.of(Grade.MOSSY_DRESSED, Grade.MOSSY_RUBBLE, Grade.DRESSED, Grade.NATURAL);
			case CRACKED -> List.of(Grade.CRACKED, Grade.DRESSED, Grade.NATURAL);
			case TILE -> List.of(Grade.TILE, Grade.DRESSED, Grade.FINE, Grade.NATURAL);
		};
	}

	/** A stone by name ({@code limestone}) or by any of its blocks ({@code expanse:limestone_bricks}), or null. */
	public static @Nullable Stone stone(String nameOrId) {
		String k = nameOrId.toLowerCase(Locale.ROOT);
		Stone s = STONES.get(k);
		if (s != null) {
			return s;
		}
		Object[] e = BY_BLOCK.get(k.contains(":") ? k : "minecraft:" + k);
		return e == null ? null : (Stone) e[0];
	}

	/** The (stone, grade) a block id is, or null. */
	public static Object @Nullable [] graded(String id) {
		String k = id.toLowerCase(Locale.ROOT);
		return BY_BLOCK.get(k.contains(":") ? k : "minecraft:" + k);
	}

	/**
	 * A grade's spec as a material: plain ids as they are; ids in braces are blocks without cuts, whose stairs, slabs and
	 * walls borrow polished deepslate's.
	 */
	static Material material(String spec) {
		if (spec.startsWith("{") && spec.endsWith("}")) {
			String id = spec.substring(1, spec.length() - 1);
			Map<Form, String> o = new EnumMap<>(Form.class);
			if (id.contains("calcite")) {
				o.put(Form.STAIRS, "minecraft:polished_diorite_stairs");
				o.put(Form.SLAB, "minecraft:polished_diorite_slab");
				o.put(Form.WALL, "minecraft:diorite_wall");
			} else {
				o.put(Form.STAIRS, "minecraft:polished_deepslate_stairs");
				o.put(Form.SLAB, "minecraft:polished_deepslate_slab");
				o.put(Form.WALL, "minecraft:polished_deepslate_wall");
			}
			return Material.of(id, o);
		}
		return Material.of(spec);
	}

	// ------------------------------------------------------------------------------------------------ timber

	/** How light a timber's boards are: light woods lose a frame against white infill. */
	public enum Tone {
		LIGHT, MID, DARK
	}

	private static final Map<String, Tone> TONES = new HashMap<>();
	/** Expanse woods' vanilla stand-ins (the colour nearest the original). */
	private static final Map<String, String> STAND_INS = new HashMap<>();

	static {
		for (String w : new String[]{"birch", "pale_oak", "bamboo", "cherry"}) {
			TONES.put("minecraft:" + w, Tone.LIGHT);
		}
		for (String w : new String[]{"oak", "jungle", "acacia", "poplar"}) {
			TONES.put("minecraft:" + w, Tone.MID);
		}
		for (String w : new String[]{"spruce", "dark_oak", "mangrove", "crimson", "warped"}) {
			TONES.put("minecraft:" + w, Tone.DARK);
		}
		Object[][] expanse = {
			{"aspen", Tone.LIGHT, "birch"}, {"kapok", Tone.LIGHT, "birch"}, {"lumen", Tone.LIGHT, "birch"}, {"palm", Tone.LIGHT, "jungle"},
			{"pine", Tone.LIGHT, "oak"}, {"maple", Tone.MID, "oak"}, {"olive", Tone.MID, "oak"}, {"willow", Tone.MID, "oak"},
			{"baobab", Tone.MID, "acacia"}, {"beech", Tone.MID, "jungle"}, {"wisteria", Tone.MID, "cherry"}, {"teak", Tone.MID, "jungle"},
			{"eucalyptus", Tone.DARK, "mangrove"}, {"redwood", Tone.DARK, "spruce"}
		};
		for (Object[] e : expanse) {
			TONES.put("expanse:" + e[0], (Tone) e[1]);
			STAND_INS.put("expanse:" + e[0], "minecraft:" + e[2]);
		}
	}

	/** The tone of a wood ({@code minecraft:spruce}, {@code expanse:pine}); MID when unknown. */
	public static Tone tone(String wood) {
		return TONES.getOrDefault(wood.toLowerCase(Locale.ROOT), Tone.MID);
	}

	/**
	 * The wood a block or name means ({@code minecraft:birch_planks}, {@code birch_log}, {@code stripped_birch_wood},
	 * {@code wood:minecraft:birch}, {@code birch}) as {@code ns:name}, or null if it is not a wood.
	 */
	public static @Nullable String wood(String idOrName) {
		String s = idOrName.toLowerCase(Locale.ROOT).trim();
		if (s.startsWith("wood:")) {
			s = s.substring(5);
		}
		if (s.contains("|")) {
			s = s.substring(0, s.indexOf('|'));
			if (s.startsWith("wood:")) {
				s = s.substring(5);
			}
		}
		String ns = s.contains(":") ? s.substring(0, s.indexOf(':')) : "minecraft";
		String n = s.contains(":") ? s.substring(s.indexOf(':') + 1) : s;
		if (n.startsWith("stripped_")) {
			n = n.substring("stripped_".length());
		}
		for (String suffix : new String[]{"_planks", "_log", "_wood", "_stem", "_hyphae", "_block", "_slab", "_stairs", "_fence", "_door", "_trapdoor"}) {
			if (n.endsWith(suffix)) {
				n = n.substring(0, n.length() - suffix.length());
				break;
			}
		}
		String key = ns + ":" + n;
		return TONES.containsKey(key) ? key : null;
	}

	/** A wood as a material spec with its vanilla stand-in ({@code wood:expanse:pine|wood:minecraft:oak}). */
	public static String woodSpec(String wood) {
		String standIn = STAND_INS.get(wood);
		return standIn == null ? "wood:" + wood : "wood:" + wood + "|wood:" + standIn;
	}

	// ------------------------------------------------------------------------------------------------ kinds

	/** What kind of thing a resolved material is, from the alternative of its chain that resolved. */
	public static Kind kind(Material m) {
		String from = m.resolvedFrom().toLowerCase(Locale.ROOT);
		if (from.isEmpty()) {
			from = m.spec().toLowerCase(Locale.ROOT);
		}
		if (from.startsWith("wood:") || wood(from) != null) {
			return Kind.WOOD;
		}
		if (from.startsWith("dye:")) {
			return Kind.OTHER;
		}
		String id = from.contains(":") ? from : "minecraft:" + from;
		String path = id.substring(id.indexOf(':') + 1);
		if (path.contains("hay") || path.contains("thatch") || path.contains("dried_kelp")) {
			return Kind.THATCH;
		}
		if (path.equals("bricks") || path.contains("terracotta") || path.equals("mud_bricks") || path.equals("packed_mud") || path.contains("resin_brick")) {
			return Kind.CLAY;
		}
		if (path.contains("concrete") || path.equals("mushroom_stem") || path.equals("smooth_quartz") || path.equals("calcite")
			|| path.equals("quartz_block") || path.contains("wool")) {
			return Kind.RENDER;
		}
		if (graded(id) != null) {
			return Kind.STONE;
		}
		return Kind.OTHER;
	}
}
