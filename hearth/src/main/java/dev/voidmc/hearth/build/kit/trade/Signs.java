package dev.voidmc.hearth.build.kit.trade;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Trade signs: a hanging sign on its bracket out of a wall over the street, readable along it from both ways, or a
 * sign hung under a gate beam facing the street. The text is a translated name ({@code hearth.sign.<trade>} in the
 * trade lang fragment) with an English fallback, in light letters on dark woods and dark letters on light ones.
 */
public final class Signs {
	private Signs() {
	}

	/** The wood a sign is cut from: the style's furniture wood if it has hanging signs, else its frame's, else spruce. */
	static Block hangingSign(Draft d, boolean wall) {
		Form f = wall ? Form.WALL_HANGING_SIGN : Form.HANGING_SIGN;
		for (Role r : new Role[]{Role.FURNITURE, Role.FRAME, Role.DOOR}) {
			Block b = d.mat(r).main(f);
			if (b != null) {
				return b;
			}
		}
		return wall ? Blocks.SPRUCE_WALL_HANGING_SIGN : Blocks.SPRUCE_HANGING_SIGN;
	}

	private static String colour(Block sign) {
		String p = BuiltInRegistries.BLOCK.getKey(sign).getPath();
		boolean dark = p.startsWith("dark_oak") || p.startsWith("spruce") || p.startsWith("mangrove") || p.startsWith("crimson") || p.startsWith("warped")
			|| p.contains("redwood") || p.contains("willow");
		return dark ? "white" : "black";
	}

	/** Sign text: a translated trade name on the second line (fallback in English), both faces, waxed. */
	public static CompoundTag text(String key, String fallback, String colour) {
		CompoundTag t = new CompoundTag();
		t.put("front_text", face(key, fallback, colour));
		t.put("back_text", face(key, fallback, colour));
		t.putBoolean("is_waxed", true);
		return t;
	}

	private static CompoundTag face(String key, String fallback, String colour) {
		ListTag messages = new ListTag();
		for (int i = 0; i < 4; i++) {
			CompoundTag line = new CompoundTag();
			if (i == 1) {
				line.putString("translate", key);
				line.putString("fallback", fallback);
			} else {
				line.putString("text", "");
			}
			messages.add(line);
		}
		CompoundTag t = new CompoundTag();
		t.put("messages", messages);
		t.putString("color", colour);
		t.putBoolean("has_glowing_text", false);
		return t;
	}

	/**
	 * A hanging sign on its own bracket out of the wall at cell i of {@code wall}, at height y (at least 3 if it would
	 * hang over the street). The sign stands at right angles to the wall, read along the street. Returns whether it fit.
	 */
	public static boolean bracket(Draft d, Side wall, int i, int y, String trade, String fallback) {
		int x = wall.outX(i, 1);
		int z = wall.outZ(i, 1);
		if (i < 0 || i > wall.last() || !d.allowed(x, y, z) || !d.isAbsent(x, y, z) && !d.isEmpty(x, y, z)) {
			return false;
		}
		if (!dev.voidmc.hearth.build.kit.Openings.isWallBlock(d, wall.x(i), y, wall.z(i))) {
			return false;
		}
		Block sign = hangingSign(d, true);
		// facing along the wall: the wall is on the sign's clockwise or counter-clockwise side
		Direction facing = wall.along();
		d.inStage(StageKind.EXTERIOR, () -> d.set(x, y, z, States.facing(sign, facing), text("hearth.sign." + trade, fallback, colour(sign))));
		return true;
	}

	/**
	 * A hanging sign under the block at (x, y + 1, z) (a gate beam, a porch beam), facing {@code facing} (toward the
	 * street, usually). Returns whether it fit.
	 */
	public static boolean hang(Draft d, int x, int y, int z, Direction facing, String trade, String fallback) {
		if (!d.allowed(x, y, z) || !d.isEmpty(x, y, z) || d.get(x, y + 1, z) == null || d.get(x, y + 1, z).isAir()) {
			return false;
		}
		Block sign = hangingSign(d, false);
		int rot = switch (facing) {
			case SOUTH -> 0;
			case WEST -> 4;
			case NORTH -> 8;
			default -> 12;
		};
		var state = sign.defaultBlockState().trySetValue(BlockStateProperties.ROTATION_16, rot).trySetValue(BlockStateProperties.ATTACHED, true);
		d.inStage(StageKind.EXTERIOR, () -> d.set(x, y, z, state, text("hearth.sign." + trade, fallback, colour(sign))));
		return true;
	}

	/**
	 * A gate post sign: a post of fences at (x, z) with an arm reaching toward {@code arm} and the sign hanging from it,
	 * facing the street. The arm's cell must be free.
	 */
	public static boolean post(Draft d, int x, int y, int z, Direction arm, int height, String trade, String fallback) {
		Mat post = Hall.woodFence(d);
		int ax = x + Dirs.dx(arm);
		int az = z + Dirs.dz(arm);
		if (!d.allowed(ax, y + height - 1, az) || !d.isEmpty(ax, y + height - 2, az)) {
			return false;
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int k = 0; k < height; k++) {
				d.set(x, y + k, z, post.fence());
			}
			d.set(ax, y + height - 1, az, post.fence());
		}
		return hang(d, ax, y + height - 2, az, Dirs.FRONT, trade, fallback);
	}
}
