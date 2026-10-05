package dev.voidmc.hearth.build.kit.deep;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Palette;
import dev.voidmc.hearth.build.style.Role;
import java.util.function.UnaryOperator;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How each culture builds when it goes underground (LIVING_WORLD.md §8.2). The deep region dresses every culture's
 * deep town in the realm's stone, so what keeps a Bohemian mining town from a karst one below is the way it shapes that
 * stone and the timber it brings down: its arches, its galleries and oriels, its lattices, the roof it pitches over a
 * hall, its joinery (the culture's own doors, shutters, floors, railings and furniture, which the deep region leaves
 * alone) and how it lights (the culture's {@code Manner}: torches in the north, candle colours, wheels or rows).
 *
 * @param pointed   doorways and great arches pointed (Bohemian Gothic, the desert's arches) rather than round
 * @param gallery   timber galleries across upper storeys (the north, the Alps, the tropics, the bayou, the karst)
 * @param oriel     timber oriels over the street (Bohemia, the moors, the coasts)
 * @param lattice   lattice screens in openings (the karst and the glades, the desert's screens)
 * @param hallRoof  the roof a hall wears: {@code gable}, {@code steep} (stave and Bohemian), {@code flared} (karst),
 *                  {@code hip}, {@code flat} (terraces with parapets), {@code dome}
 * @param flatRoofs the culture roofs its houses flat by tradition (terraces everywhere)
 */
public record DeepManner(Culture culture, boolean pointed, boolean gallery, boolean oriel, boolean lattice, String hallRoof, boolean flatRoofs) {

	public static DeepManner of(Draft d) {
		Culture c = d.style().culture() == null ? Culture.TEMPERATE : d.style().culture();
		return of(c);
	}

	public static DeepManner of(Culture c) {
		return switch (c) {
			case TEMPERATE -> new DeepManner(c, true, false, true, false, "steep", false);
			case MOORLAND -> new DeepManner(c, false, false, true, false, "gable", false);
			case MARITIME -> new DeepManner(c, false, false, true, false, "gable", false);
			case ALPINE -> new DeepManner(c, false, true, false, false, "gable", false);
			case NORTHERN -> new DeepManner(c, false, true, false, false, "steep", false);
			case TUNDRA -> new DeepManner(c, false, false, false, false, "gable", false);
			case KARST, WISTERIA -> new DeepManner(c, false, true, false, true, "flared", false);
			case DESERT -> new DeepManner(c, true, false, false, true, "dome", true);
			case MESA -> new DeepManner(c, false, false, false, false, "flat", true);
			case STEPPE -> new DeepManner(c, false, false, false, false, "dome", true);
			case MEDITERRANEAN -> new DeepManner(c, false, false, false, false, "hip", true);
			case TROPICAL -> new DeepManner(c, false, true, false, false, "hip", false);
			case BAYOU -> new DeepManner(c, false, true, false, false, "hip", false);
		};
	}

	/**
	 * A role as the culture finishes it at home, undressed by the deep region: the lime plaster, sandstone render,
	 * terracotta, bamboo or boards its folk bring below for the upper storeys they raise on the realm's stone, so a
	 * Bohemian deep town is white and timbered over its dark arcades and a desert one sand-coloured. The building's own
	 * dressing (variant) and tier; mixed by position like any wall; null where the culture has nothing in that form.
	 */
	public static Paint home(Draft d, Role role, Form form, UnaryOperator<BlockState> props) {
		Palette all = d.style().root().palette(role, d.tier(), d.variant());
		long seed = Noise.hash(d.seed(), 0x40E0 + role.ordinal());
		Palette pal = role.perBuilding() ? all.chooseOne(seed) : all;
		double patch = role.patchiness();
		return new Paint() {
			@Override
			public BlockState at(int x, int y, int z) {
				Block b = pal.pick(form, seed, x, y, z, 0, 0, 0, 0, patch);
				return b == null ? null : props.apply(b.defaultBlockState());
			}

			@Override
			public dev.voidmc.hearth.api.StageKind stage() {
				return role.stage();
			}
		};
	}

	/** {@link #home} in full blocks. */
	public static Paint home(Draft d, Role role) {
		return home(d, role, Form.FULL, s -> s);
	}

	/** Whether the culture thatches at home (its roof is then no covering for a pent below ground). */
	public static boolean thatches(Draft d) {
		return d.style().root().habits(d.variant()).roof().thatch();
	}

	/** Upper storeys framed in timber over the stone (the timber traditions); the south builds them in stone. */
	public boolean timberUpper() {
		return switch (this.culture) {
			case DESERT, MESA, STEPPE, MEDITERRANEAN -> false;
			default -> true;
		};
	}

	/** Windows with round or pointed heads (every masonry tradition but the timber north and the pueblos). */
	public boolean archedWindows() {
		return switch (this.culture) {
			case NORTHERN, TUNDRA, MESA, TROPICAL, BAYOU -> false;
			default -> true;
		};
	}
}
