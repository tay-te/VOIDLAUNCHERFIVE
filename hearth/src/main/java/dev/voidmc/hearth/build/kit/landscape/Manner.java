package dev.voidmc.hearth.build.kit.landscape;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

/**
 * The regional manner of the things between buildings: what a culture's lamps, benches, statues and squares are made
 * of and how they are shaped. The styles say what materials a culture builds with; this says which of those a lamp
 * post or a town wall takes, for the few street pieces the style JSON has no habit for. Everything else is read from
 * the style, so a data pack's style still dresses these pieces in its own materials.
 */
public final class Manner {
	private Manner() {
	}

	public static Culture culture(Draft d) {
		Culture c = d.style().culture();
		return c == null ? Culture.TEMPERATE : c;
	}

	/** Whether a role's main material is a wood family (planks, logs...), not stone or earth. */
	public static boolean wood(Mat m) {
		return m.main(Form.LOG) != null || m.main(Form.FENCE) != null && m.main(Form.WALL) == null && m.main(Form.FENCE_GATE) != null;
	}

	/** The path of a role's main block id ("stone_bricks", "palm_log"...), for telling kinds of material apart. */
	public static String path(Mat m, Form form) {
		Block b = m.main(form);
		if (b == null) {
			b = m.main(Form.FULL);
		}
		return b == null ? "" : BuiltInRegistries.BLOCK.getKey(b).getPath();
	}

	/** How a culture lights its streets. */
	public enum LampKind {
		/** A timber post on a stone foot, a crook arm, a lantern hanging from it (temperate, maritime, moorland). */
		CROOK,
		/** A post with a cross arm and two lanterns (towns of any culture at tier 3+). */
		DOUBLE,
		/** A stone lantern: shaft, firebox, a little roof (karst). */
		STONE_LANTERN,
		/** A fire basket on a stone pillar (northern, tundra, steppe). */
		BRAZIER,
		/** A torch on a bamboo pole (tropical). */
		TIKI,
		/** A lantern on a masonry pillar (desert, mesa, mediterranean). */
		PILLAR,
		/** A lantern on a chain from a bracketed log post (bayou). */
		HANGING,
		/** A lantern under a little roof on a post (alpine, wisteria). */
		CAPPED
	}

	public static LampKind lamp(Draft d) {
		return switch (culture(d)) {
			case KARST -> LampKind.STONE_LANTERN;
			case NORTHERN, TUNDRA, STEPPE -> LampKind.BRAZIER;
			case TROPICAL -> LampKind.TIKI;
			case DESERT, MESA, MEDITERRANEAN -> LampKind.PILLAR;
			case BAYOU -> LampKind.HANGING;
			case ALPINE, WISTERIA -> LampKind.CAPPED;
			default -> LampKind.CROOK;
		};
	}

	/** Whether the culture sits on stone benches rather than wooden ones. */
	public static boolean stoneBenches(Draft d) {
		return switch (culture(d)) {
			case DESERT, MESA, MEDITERRANEAN, KARST -> true;
			default -> false;
		};
	}

	/** Whether the style's walls are earth or masonry (adobe, rubble) rather than dressed: town walls take the wall role. */
	public static boolean earthWalls(Draft d) {
		WallKind k = d.habits().walls().kind();
		return k == WallKind.ADOBE || k == WallKind.STONE || culture(d) == Culture.STEPPE;
	}

	/**
	 * The masonry of fortifications and plinths: the style's plinth (dressed stone in most cultures), its walls where they
	 * are earth or rubble (adobe towns wall themselves in adobe), or its foundation stone.
	 */
	public static Mat masonry(Draft d) {
		if (earthWalls(d) && !wood(d.mat(Role.WALL))) {
			return d.mat(Role.WALL);
		}
		Mat plinth = d.mat(Role.PLINTH);
		if (!wood(plinth)) {
			return plinth;
		}
		Mat found = d.mat(Role.FOUNDATION);
		return wood(found) ? plinth : found;
	}

	/** Whether this culture's fortifications are timber (no stone in its building palette: bayou, tropical). */
	public static boolean timberForts(Draft d) {
		return wood(d.mat(Role.PLINTH)) && wood(d.mat(Role.FOUNDATION));
	}
}
