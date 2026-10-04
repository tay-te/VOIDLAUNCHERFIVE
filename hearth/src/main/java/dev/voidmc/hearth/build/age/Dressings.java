package dev.voidmc.hearth.build.age;

import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.kit.age.BuiltView;
import dev.voidmc.hearth.build.kit.age.Rubble;
import dev.voidmc.hearth.build.kit.age.Trimmings;
import dev.voidmc.hearth.build.style.StyleRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Dressings: what a building puts on for a special day (docs/LIVING_WORLD.md §8.3), as extra steps that come and go
 * without touching the building's own. The social module (or anything else) asks for a plot's dressing on the day and
 * places it; when the day is over it takes the same steps down.
 *
 * <pre>
 *   List&lt;Dressings.Step&gt; steps = Dressings.steps(plot.spec(), blueprint, Dressings.Kind.FESTIVAL, day);
 * </pre>
 *
 * <table>
 *   <caption>the dressings</caption>
 *   <tr><th>kind</th><th>what goes up</th></tr>
 *   <tr><td>{@link Kind#MARKET}</td><td>striped awnings (a cloth in the town's two colours on a board frame) over the
 *   ground-floor windows and door of the front; barrels, crates and hay before the front with wares on them (melons,
 *   pumpkins, cakes, flowers) where the building has a forecourt</td></tr>
 *   <tr><td>{@link Kind#FESTIVAL}</td><td>a garland of leaves and blossom over the main door, banners in the culture's
 *   colours either side of it and between the upper windows, lanterns strung under the front eave, pots of flowers on
 *   the sills</td></tr>
 *   <tr><td>{@link Kind#WEDDING}</td><td>a garland heavy with blossom over the door, white ribbon banners either side, a
 *   white runner at the threshold, white and pink flowers on every sill</td></tr>
 *   <tr><td>{@link Kind#MOURNING}</td><td>black banners either side of the door, black cloth on the sills and the
 *   threshold (white in the karst and wisteria cultures, where mourning is white)</td></tr>
 *   <tr><td>{@link Kind#WINTER}</td><td>snow on every ledge the sky reaches (ridges, full roof courses, wall heads,
 *   chimney tops beside the fire, sills) and over the yard, drifted against the walls; in the cold cultures only (tundra,
 *   northern, alpine, moorland): elsewhere the list is empty</td></tr>
 * </table>
 *
 * <p>The contract: steps are world positions in placing order (supports first: a frame before the cloth on it, a
 * barrel before the melon on it); every step goes where the blueprint places air or nothing, inside the plot's
 * footprint and height (or over the street at three or more up, as eaves and awnings may), and rests on, hangs from or
 * clings to a block of the building's; doorways, the cells either side of each door and the entrances stay clear
 * (only a runner lies in the way in, and it is walked over). Deterministic in (spec, kind, day): the same day gives the
 * same dressing, another day may vary the details. Cheap (a pass over the blueprint's steps); safe from any thread.
 * Taking a dressing down is setting each step's position back to air if it still holds the step's block.
 */
public final class Dressings {
	private Dressings() {
	}

	/** The days a building dresses for. */
	public enum Kind implements StringRepresentable {
		MARKET, FESTIVAL, WEDDING, MOURNING, WINTER;

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Kind byName(String s) {
			return valueOf(s.toUpperCase(Locale.ROOT));
		}
	}

	/** One block of a dressing, in world coordinates; {@code tag} is block-entity data (a banner's pattern) or null. */
	public record Step(BlockPos pos, BlockState state, @Nullable CompoundTag tag) {
	}

	/** Whether a plot dresses for a kind of day at all (winter snow is only for the cold cultures). */
	public static boolean applies(PlotSpec spec, Kind kind) {
		if (kind != Kind.WINTER) {
			return true;
		}
		Culture c = culture(spec);
		return c == Culture.TUNDRA || c == Culture.NORTHERN || c == Culture.ALPINE || c == Culture.MOORLAND;
	}

	/** The steps that dress a plot's building for a day (empty where the kind does not apply or finds no place). */
	public static List<Step> steps(PlotSpec spec, Blueprint bp, Kind kind, long day) {
		return steps(spec, bp, kind, day, null);
	}

	/**
	 * As {@link #steps(PlotSpec, Blueprint, Kind, long)}, seeing the natural ground round the building too (the terrain
	 * the blueprint was made for), so goods can stand and snow can lie in a yard the building leaves as it is.
	 */
	public static List<Step> steps(PlotSpec spec, Blueprint bp, Kind kind, long day, @Nullable TerrainProbe terrain) {
		if (!applies(spec, kind)) {
			return List.of();
		}
		BuiltView v = new BuiltView(spec, bp).ground(terrain);
		long seed = Noise.hash(Noise.hash(spec.seed(), 0xD8E55L + kind.ordinal()), day);
		Trimmings t = new Trimmings(v, seed);
		Culture c = culture(spec);
		DyeColor[] colours = colours(c);
		List<BuiltView.Door> doors = v.doors();
		BuiltView.Door door = doors.isEmpty() ? null : doors.get(0);
		switch (kind) {
			case MARKET -> {
				Block board = door != null ? Rubble.doorLeaf(door.block()) : null;
				if (board == null || board == Blocks.IRON_TRAPDOOR) {
					board = Blocks.SPRUCE_TRAPDOOR;
				}
				t.awnings(board, colours[0], colours[1]);
				t.goods(8);
				// cloths laid out on the sills, to show wares on
				t.sillCloths(colours[0], colours[1]);
			}
			case FESTIVAL -> {
				if (door != null) {
					t.garland(door, Blocks.AZALEA_LEAVES, Blocks.FLOWERING_AZALEA_LEAVES, 0.45);
					t.doorBanners(door, colours[0], colours[1], "stripe_center");
					t.doorPots(door, new Block[]{Blocks.POTTED_POPPY, Blocks.POTTED_DANDELION, Blocks.POTTED_RED_TULIP});
				}
				t.frontBanners(colours[0], colours[2], "chevron");
				t.eaveLanterns(2 + (int) Math.floorMod(seed, 2));
				t.sillPots(new Block[]{Blocks.POTTED_POPPY, Blocks.POTTED_DANDELION, Blocks.POTTED_RED_TULIP, Blocks.POTTED_CORNFLOWER,
					Blocks.POTTED_OXEYE_DAISY});
			}
			case WEDDING -> {
				if (door != null) {
					t.garland(door, Blocks.AZALEA_LEAVES, Blocks.FLOWERING_AZALEA_LEAVES, 0.85);
					t.doorBanners(door, DyeColor.WHITE, DyeColor.PINK, "stripe_center");
					t.runner(door, DyeColor.WHITE, 2);
					t.doorPots(door, new Block[]{Blocks.POTTED_LILY_OF_THE_VALLEY, Blocks.POTTED_OXEYE_DAISY, Blocks.POTTED_WHITE_TULIP});
				}
				t.sillPots(new Block[]{Blocks.POTTED_LILY_OF_THE_VALLEY, Blocks.POTTED_OXEYE_DAISY, Blocks.POTTED_PINK_TULIP, Blocks.POTTED_AZURE_BLUET,
					Blocks.POTTED_WHITE_TULIP});
			}
			case MOURNING -> {
				DyeColor mourn = c == Culture.KARST || c == Culture.WISTERIA ? DyeColor.WHITE : DyeColor.BLACK;
				if (door != null) {
					t.doorBanners(door, mourn, null, "");
					t.runner(door, mourn, 1);
				}
				t.sillCloth(mourn);
			}
			case WINTER -> t.snow();
		}
		List<Step> out = new ArrayList<>(t.pieces().size());
		for (Trimmings.Piece p : t.pieces()) {
			out.add(new Step(v.toWorld(p.x(), p.y(), p.z()), v.toWorld(p.state()), p.tag()));
		}
		return Collections.unmodifiableList(out);
	}

	/** The colours a culture dresses its streets in: festival banners, market awnings (first two), garlands' ribbons. */
	public static DyeColor[] colours(@Nullable Culture c) {
		if (c == null) {
			return new DyeColor[]{DyeColor.RED, DyeColor.WHITE, DyeColor.YELLOW};
		}
		return switch (c) {
			case TEMPERATE -> new DyeColor[]{DyeColor.RED, DyeColor.WHITE, DyeColor.YELLOW};
			case WISTERIA -> new DyeColor[]{DyeColor.PURPLE, DyeColor.WHITE, DyeColor.YELLOW};
			case NORTHERN -> new DyeColor[]{DyeColor.BLUE, DyeColor.YELLOW, DyeColor.RED};
			case ALPINE -> new DyeColor[]{DyeColor.RED, DyeColor.WHITE, DyeColor.GREEN};
			case TUNDRA -> new DyeColor[]{DyeColor.BLUE, DyeColor.RED, DyeColor.YELLOW};
			case MOORLAND -> new DyeColor[]{DyeColor.BLUE, DyeColor.WHITE, DyeColor.GREEN};
			case MARITIME -> new DyeColor[]{DyeColor.BLUE, DyeColor.WHITE, DyeColor.RED};
			case TROPICAL -> new DyeColor[]{DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.GREEN};
			case DESERT -> new DyeColor[]{DyeColor.RED, DyeColor.WHITE, DyeColor.ORANGE};
			case MEDITERRANEAN -> new DyeColor[]{DyeColor.YELLOW, DyeColor.RED, DyeColor.BLUE};
			case STEPPE -> new DyeColor[]{DyeColor.RED, DyeColor.YELLOW, DyeColor.BLUE};
			case KARST -> new DyeColor[]{DyeColor.RED, DyeColor.YELLOW, DyeColor.ORANGE};
			case BAYOU -> new DyeColor[]{DyeColor.PURPLE, DyeColor.YELLOW, DyeColor.GREEN};
			case MESA -> new DyeColor[]{DyeColor.RED, DyeColor.CYAN, DyeColor.WHITE};
		};
	}

	private static @Nullable Culture culture(PlotSpec spec) {
		for (Culture c : Culture.values()) {
			if (StyleRegistry.styleId(c).equals(spec.style())) {
				return c;
			}
		}
		return null;
	}
}
