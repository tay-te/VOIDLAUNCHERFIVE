package dev.voidmc.hearth.build.kit.landscape;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The settlement's notice board, where its requests are pinned: the quest module's {@code hearth:notice_board} block,
 * resolved by id when the blueprint is drawn (the build module does not depend on the quest module), or where it is
 * absent a lettered sign on a fence post. Registers the NOTICE_BOARD point of interest in front of it.
 */
public final class NoticeBoards {
	private static final Identifier ID = Identifier.fromNamespaceAndPath("hearth", "notice_board");

	private NoticeBoards() {
	}

	/** Whether the quest module's board block is registered. */
	public static boolean available() {
		return BuiltInRegistries.BLOCK.containsKey(ID);
	}

	/** A notice board at (x, y, z) facing {@code facing} (readers stand in front of it, one block that way). */
	public static void board(Draft d, int x, int y, int z, Direction facing) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			if (available()) {
				Block b = BuiltInRegistries.BLOCK.getValue(ID);
				d.set(x, y, z, b.defaultBlockState().trySetValue(BlockStateProperties.HORIZONTAL_FACING, facing));
			} else {
				d.set(x, y, z, d.mat(Role.FENCE).has(Form.FENCE) ? d.mat(Role.FENCE).fence() : d.mat(Role.FRAME).fence());
				Block sign = d.mat(Role.FURNITURE).main(Form.SIGN);
				if (sign == null) {
					sign = Blocks.OAK_SIGN;
				}
				int rot = switch (facing) {
					case SOUTH -> 0;
					case WEST -> 4;
					case NORTH -> 8;
					default -> 12;
				};
				d.set(x, y + 1, z, States.of(sign).trySetValue(BlockStateProperties.ROTATION_16, rot),
					Inscriptions.sign(List.of(new String[]{"board.1", "Notices"}, new String[]{"board.2", "Work wanted"},
						new String[]{"board.3", "Ask the folk"}), "black"));
			}
		}
		d.poi(PoiType.NOTICE_BOARD, x + Dirs.dx(facing), y, z + Dirs.dz(facing), facing.getOpposite());
	}
}
