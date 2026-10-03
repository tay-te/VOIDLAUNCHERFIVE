package dev.voidmc.hearth.build.kit.landscape;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Lettered signs on monuments and boards: each line a translatable text component with its English as the fallback,
 * so the words read in the player's language where the lang file has them (keys {@code hearth.landscape.text.*} in
 * the landscape lang fragment) and in English everywhere else.
 */
public final class Inscriptions {
	private Inscriptions() {
	}

	/** A sign tag whose front lines are {key, english} pairs. */
	public static CompoundTag sign(List<String[]> lines, String color) {
		CompoundTag t = new CompoundTag();
		t.put("front_text", text(lines, color));
		t.put("back_text", text(List.of(), color));
		t.putBoolean("is_waxed", true);
		t.putBoolean("allow_op_features", false);
		return t;
	}

	private static CompoundTag text(List<String[]> lines, String color) {
		ListTag messages = new ListTag();
		for (int i = 0; i < 4; i++) {
			CompoundTag line = new CompoundTag();
			if (i < lines.size() && lines.get(i)[0] != null) {
				line.putString("translate", "hearth.landscape.text." + lines.get(i)[0]);
				line.putString("fallback", lines.get(i)[1]);
			} else {
				line.putString("text", i < lines.size() ? lines.get(i)[1] : "");
			}
			messages.add(line);
		}
		CompoundTag t = new CompoundTag();
		t.put("messages", messages);
		t.putString("color", color);
		t.putBoolean("has_glowing_text", false);
		return t;
	}

	/** A wall sign of the furniture wood on the wall behind (x, y, z), facing {@code out}, lettered with the lines. */
	public static void wallSign(Draft d, int x, int y, int z, Direction out, List<String[]> lines) {
		Block sign = d.mat(Role.FURNITURE).main(Form.WALL_SIGN);
		if (sign == null) {
			sign = Blocks.OAK_WALL_SIGN;
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, States.facing(sign, out), sign(lines, "black"));
		}
	}
}
