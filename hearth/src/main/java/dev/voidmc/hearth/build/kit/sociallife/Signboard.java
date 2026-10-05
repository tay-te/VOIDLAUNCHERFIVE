package dev.voidmc.hearth.build.kit.sociallife;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.civic.Signs;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * How a house of drink, rest or charity names itself to the street: a painted board hung from a wrought bracket, the
 * ale-stake (a pole run out over the door with a bush tied to its end, the old sign that the ale is fresh), a carved
 * board over a door. Names are translatable ({@code hearth.build.sociallife.<kind>.<name>.1|2}) with English fallbacks.
 */
public final class Signboard {
	private Signboard() {
	}

	/** Tavern names: a key suffix and the English of the sign's two lines. */
	public static final String[][] TAVERNS = {
		{"tipsy_badger", "The Tipsy", "Badger"}, {"merry_fiddler", "The Merry", "Fiddler"}, {"lame_horse", "The Lame", "Horse"},
		{"black_cauldron", "The Black", "Cauldron"}, {"golden_tankard", "The Golden", "Tankard"}, {"thirsty_boar", "The Thirsty", "Boar"},
		{"rolling_dice", "The Rolling", "Dice"}, {"one_eyed_cat", "The One-Eyed", "Cat"}, {"jolly_miller", "The Jolly", "Miller"},
		{"crooked_spoon", "The Crooked", "Spoon"}, {"drunken_goat", "The Drunken", "Goat"}, {"three_hops", "Three", "Hops"},
		{"barley_mow", "The Barley", "Mow"}, {"bent_nail", "The Bent", "Nail"}, {"sleeping_dog", "The Sleeping", "Dog"},
		{"singing_kettle", "The Singing", "Kettle"}};

	/** Hostel names. */
	public static final String[][] HOSTELS = {
		{"pilgrims_rest", "Pilgrims'", "Rest"}, {"wayfarers", "Wayfarers'", "House"}, {"st_jodocus", "Hospice of", "St Jodok"},
		{"open_door", "The Open", "Door"}, {"staff_and_scrip", "Staff and", "Scrip"}, {"travellers", "Travellers'", "Hall"}};

	/** Almshouse names. */
	public static final String[][] ALMSHOUSES = {
		{"holy_spirit", "Hospital of", "the Holy Spirit"}, {"st_elizabeth", "St Elizabeth's", "Almshouse"}, {"poor_brethren", "House of the", "Poor Brethren"}, {"widows", "The Widows'", "House"}};

	/** Orphanage names. */
	public static final String[][] ORPHANAGES = {
		{"foundlings", "Foundlings'", "Home"}, {"st_nicholas", "St Nicholas's", "House"}, {"little_lambs", "The Little", "Lambs"}, {"good_shepherd", "The Good", "Shepherd"}};

	/** Infirmary names. */
	public static final String[][] INFIRMARIES = {
		{"st_lazarus", "Hospital of", "St Lazarus"}, {"healing_hand", "The Healing", "Hand"}, {"st_roch", "St Roch's", "Infirmary"}, {"mercy", "House of", "Mercy"}};

	/** Playhouse names. */
	public static final String[][] PLAYHOUSES = {
		{"swan", "The", "Swan"}, {"globe", "The", "Globe"}, {"rose", "The", "Rose"}, {"fortune", "The", "Fortune"}, {"red_lion", "The Red", "Lion"}};

	/** A name drawn from a list by the {@code name} param or the seed: the four lines of a sign, a flourish above and below. */
	public static List<String[]> name(Draft d, String kind, String[][] names) {
		int n = d.param("name", (int) (Noise.unit(d.seed(), 0x7A5, 0, 0) * names.length));
		String[] nm = names[Math.floorMod(n, names.length)];
		return List.of(new String[]{"", "~"}, new String[]{"hearth.build.sociallife." + kind + "." + nm[0] + ".1", nm[1]},
			new String[]{"hearth.build.sociallife." + kind + "." + nm[0] + ".2", nm[2]}, new String[]{"", "~"});
	}

	/**
	 * A painted board hung from a wrought bracket on wall {@code out}: from the wall cell (x, y, z) two fence posts run out
	 * at y (the arm), an upside-down stair braces it against the wall below, and the hanging sign swings from the arm's
	 * end on its chains, turned to be read by those coming along the street. Needs the two cells out from the wall free
	 * at y and y - 1. Answers whether it was hung.
	 */
	public static boolean bracket(Draft d, int x, int y, int z, Direction out, List<String[]> lines) {
		int x1 = x + Dirs.dx(out);
		int z1 = z + Dirs.dz(out);
		int x2 = x1 + Dirs.dx(out);
		int z2 = z1 + Dirs.dz(out);
		if (!d.isSolid(x, y, z) || !free(d, x1, y, z1) || !free(d, x2, y, z2) || !free(d, x1, y - 1, z1) || !free(d, x2, y - 1, z2)) {
			return false;
		}
		Mat iron = d.mat(Role.FURNITURE);
		Paint arm = iron.has(Form.FENCE) ? iron.fence() : d.mat(Role.FRAME).fence();
		Block sign = iron.mainOr(Form.HANGING_SIGN, Blocks.SPRUCE_HANGING_SIGN);
		int rot = switch (Dirs.right(out)) {
			case SOUTH -> 0;
			case WEST -> 4;
			case NORTH -> 8;
			default -> 12;
		};
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x1, y, z1, arm);
			d.set(x2, y, z2, arm);
			d.set(x1, y - 1, z1, iron.stairsOrSlab(out.getOpposite(), Half.TOP));
			d.set(x2, y - 1, z2, sign.defaultBlockState().trySetValue(BlockStateProperties.ROTATION_16, rot)
				.trySetValue(BlockStateProperties.ATTACHED, false), Signs.signTag(lines, "black"));
		}
		return true;
	}

	/**
	 * The ale-stake: a pole of the frame's timber run out {@code reach} cells from the wall cell (x, y, z) toward
	 * {@code out}, a garland of leaves (the bush) tied to its end and a lantern hung under the bush when there is room.
	 * Answers whether it was put up.
	 */
	public static boolean aleStake(Draft d, int x, int y, int z, Direction out, int reach) {
		if (!d.isSolid(x, y, z)) {
			return false;
		}
		for (int k = 1; k <= reach; k++) {
			if (!free(d, x + Dirs.dx(out) * k, y, z + Dirs.dz(out) * k)) {
				return false;
			}
		}
		Mat frame = d.mat(Role.FRAME);
		int ex = x + Dirs.dx(out) * reach;
		int ez = z + Dirs.dz(out) * reach;
		Paint pole = frame.has(Form.FENCE) ? frame.fence() : Paint.of(Blocks.SPRUCE_FENCE.defaultBlockState());
		Block bush = Noise.unit(d.seed(), x, y, z) < 0.5 ? Blocks.SPRUCE_LEAVES : Blocks.OAK_LEAVES;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int k = 1; k < reach; k++) {
				d.set(x + Dirs.dx(out) * k, y, z + Dirs.dz(out) * k, pole);
			}
			d.set(ex, y, ez, States.leaves(bush));
			if (free(d, ex, y - 1, ez) && free(d, ex, y - 2, ez) && y - 1 >= (ez < 0 ? 3 : 1)) {
				d.set(ex, y - 1, ez, States.lantern(Blocks.LANTERN, true));
			}
		}
		return true;
	}

	private static boolean free(Draft d, int x, int y, int z) {
		return d.allowed(x, y, z) && d.isEmpty(x, y, z);
	}
}
