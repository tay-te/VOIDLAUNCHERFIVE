package dev.voidmc.hearth.build.kit.civic;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.BlockEntities;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * What a civic building says about itself: a hanging sign over the street (the inn's name), banners in the town's
 * colours, the notice board by the town hall's door.
 */
public final class Signs {
	/** The quest module's board, placed by id so the build module never depends on it. */
	public static final String NOTICE_BOARD = "hearth:notice_board";

	/** Banner patterns a town's arms are drawn from. */
	private static final String[] CHARGES = {"stripe_center", "stripe_middle", "cross", "straight_cross", "half_horizontal", "half_vertical", "rhombus",
		"circle", "triangle_top", "triangles_bottom", "border", "curly_border", "flower", "diagonal_left", "stripe_downright", "bricks",
		"square_top_left", "globe"};

	private Signs() {
	}

	/**
	 * A wall banner at (x, y, z) hanging on the face of the block behind it (opposite {@code facing}), its cloth falling
	 * over the block below.
	 *
	 * @param colour   the field's dye colour (white, red...)
	 * @param patterns layers {pattern id, colour}
	 */
	public static void wallBanner(Draft d, int x, int y, int z, Direction facing, String colour, List<String[]> patterns) {
		Block banner = States.block("minecraft:" + colour + "_wall_banner", States.block("minecraft:white_wall_banner", Blocks.AIR));
		if (banner == Blocks.AIR) {
			return;
		}
		BlockState s = banner.defaultBlockState().trySetValue(BlockStateProperties.HORIZONTAL_FACING, facing);
		d.inStage(StageKind.EXTERIOR, () -> d.set(x, y, z, s, BlockEntities.banner(patterns)));
	}

	/** The town's arms: a field colour and two or three charges, from the param {@code banner} or the seed. */
	public static Arms arms(Draft d) {
		String p = d.param("banner", "");
		if (!p.isEmpty()) {
			// "red;white:cross;yellow:border"
			String[] parts = p.split(";");
			List<String[]> layers = new ArrayList<>();
			for (int i = 1; i < parts.length; i++) {
				String[] kv = parts[i].split(":");
				if (kv.length == 2) {
					layers.add(new String[]{kv[1], kv[0]});
				}
			}
			return new Arms(parts[0], layers);
		}
		List<String> colours = clothColours(d);
		long seed = Noise.hash(d.seed(), 0xA2A5);
		String field = colours.get(0);
		String charge = colours.size() > 1 ? colours.get(1) : "white";
		if (charge.equals(field)) {
			charge = field.equals("white") ? "black" : "white";
		}
		List<String[]> layers = new ArrayList<>();
		int n = 1 + (int) (Noise.unit(seed, 1, 0, 0) * 2.2);
		for (int i = 0; i < n; i++) {
			String c = CHARGES[(int) (Noise.unit(seed, 2, i, 0) * CHARGES.length)];
			layers.add(new String[]{c, i == 1 && colours.size() > 2 ? colours.get(2) : charge});
		}
		if (Noise.unit(seed, 3, 0, 0) < 0.5) {
			layers.add(new String[]{"border", charge});
		}
		return new Arms(field, layers);
	}

	/** A town's arms: a field and its layers. */
	public record Arms(String field, List<String[]> layers) {
	}

	/** The dye colours of the style's cloth, in order (white if none). */
	public static List<String> clothColours(Draft d) {
		List<String> out = new ArrayList<>();
		for (var e : d.mat(Role.CLOTH).palette().entries()) {
			Block wool = e.material().block(Form.FULL);
			if (wool == null) {
				continue;
			}
			String path = BuiltInRegistries.BLOCK.getKey(wool).getPath();
			if (path.endsWith("_wool")) {
				out.add(path.substring(0, path.length() - "_wool".length()));
			}
		}
		if (out.isEmpty()) {
			out.add("white");
		}
		return out;
	}

	/**
	 * A wall hanging sign at (x, y, z), sticking out from a wall beside it: {@code facing} is the way its text faces (along
	 * the street), and the block it hangs from is on its left or right of that. The lines are translation keys with
	 * English fallbacks ({key, fallback}), so every player reads it in their language.
	 */
	public static void hangingSign(Draft d, int x, int y, int z, Direction facing, List<String[]> lines) {
		Block sign = d.mat(Role.FURNITURE).mainOr(Form.WALL_HANGING_SIGN, Blocks.SPRUCE_WALL_HANGING_SIGN);
		BlockState s = sign.defaultBlockState().trySetValue(BlockStateProperties.HORIZONTAL_FACING, facing);
		d.inStage(StageKind.EXTERIOR, () -> d.set(x, y, z, s, signTag(lines, "black")));
	}

	/** A standing sign on a post at (x, y, z) (the post) facing {@code facing}, as the notice board stands in for. */
	public static void postSign(Draft d, int x, int y, int z, Direction facing, List<String[]> lines) {
		Block sign = d.mat(Role.FURNITURE).mainOr(Form.SIGN, Blocks.OAK_SIGN);
		int rot = switch (facing) {
			case SOUTH -> 0;
			case WEST -> 4;
			case NORTH -> 8;
			default -> 12;
		};
		BlockState s = sign.defaultBlockState().trySetValue(BlockStateProperties.ROTATION_16, rot);
		try (var st = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, Halls.post(d));
			d.set(x, y + 1, z, s, signTag(lines, "black"));
		}
	}

	/**
	 * The notice board ({@code hearth:notice_board}, the quest module's) at (x, y, z) facing {@code facing} (folk read it
	 * from the cell in front), or, without the quest module, a sign on a post. Registers the NOTICE_BOARD point.
	 */
	public static void noticeBoard(Draft d, int x, int y, int z, Direction facing) {
		Block board = States.block(NOTICE_BOARD, Blocks.AIR);
		if (board != Blocks.AIR) {
			BlockState s = board.defaultBlockState().trySetValue(BlockStateProperties.HORIZONTAL_FACING, facing);
			d.inStage(StageKind.EXTERIOR, () -> d.set(x, y, z, s));
		} else {
			postSign(d, x, y, z, facing, List.of(new String[]{"", ""}, new String[]{"hearth.build.civic.notices", "Notices"}));
		}
		d.poi(PoiType.NOTICE_BOARD, x + Dirs.dx(facing), y, z + Dirs.dz(facing), facing.getOpposite());
	}

	/** Sign data whose lines are translatable components with fallbacks, waxed. */
	public static CompoundTag signTag(List<String[]> lines, String colour) {
		CompoundTag t = new CompoundTag();
		t.put("front_text", text(lines, colour));
		t.put("back_text", text(lines, colour));
		t.putBoolean("is_waxed", true);
		return t;
	}

	private static CompoundTag text(List<String[]> lines, String colour) {
		ListTag messages = new ListTag();
		for (int i = 0; i < 4; i++) {
			CompoundTag line = new CompoundTag();
			if (i < lines.size() && !lines.get(i)[0].isEmpty()) {
				line.putString("translate", lines.get(i)[0]);
				line.putString("fallback", lines.get(i)[1]);
			} else {
				line.putString("text", i < lines.size() ? lines.get(i)[1] : "");
			}
			messages.add(line);
		}
		CompoundTag t = new CompoundTag();
		t.put("messages", messages);
		t.putString("color", colour);
		t.putBoolean("has_glowing_text", false);
		return t;
	}

	/** Whether a block id resolves (a mod's block is installed). */
	public static boolean exists(String id) {
		Identifier key = Identifier.tryParse(id);
		return key != null && BuiltInRegistries.BLOCK.containsKey(key);
	}
}
