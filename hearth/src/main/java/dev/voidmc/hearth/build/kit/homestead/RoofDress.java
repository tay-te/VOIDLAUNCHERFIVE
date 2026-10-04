package dev.voidmc.hearth.build.kit.homestead;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.style.Form;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Finishing touches on a rasterised roof: barge boards (a row or column of the roof restated in the trim), so a gable's
 * rake reads as a crisp edge against the sky; carved horns and finials live with the archetypes that use them.
 */
public final class RoofDress {
	private RoofDress() {
	}

	/** Restates every roof block of row z (stairs as stairs, slabs as slabs) in {@code trim}. */
	public static void trimRow(Draft d, Roofs.Roof roof, int z, Mat trim) {
		for (int x = roof.x0; x <= roof.x1; x++) {
			trimColumn(d, roof, x, z, trim);
		}
	}

	/** Restates every roof block of column x in {@code trim}. */
	public static void trimCol(Draft d, Roofs.Roof roof, int x, Mat trim) {
		for (int z = roof.z0; z <= roof.z1; z++) {
			trimColumn(d, roof, x, z, trim);
		}
	}

	/** Restates the roof's blocks at (x, z) in {@code trim}. */
	public static void trimColumn(Draft d, Roofs.Roof roof, int x, int z, Mat trim) {
		if (!roof.covers(x, z)) {
			return;
		}
		try (var s = d.stage(StageKind.ROOF)) {
			for (int y = roof.bottomY(x, z); y <= roof.surfaceY(x, z); y++) {
				BlockState st = d.get(x, y, z);
				if (st == null || st.isAir()) {
					continue;
				}
				if (st.getBlock() instanceof StairBlock && trim.has(Form.STAIRS)) {
					d.set(x, y, z, trim.stairs(st.getValue(StairBlock.FACING), st.getValue(StairBlock.HALF)));
				} else if (st.getBlock() instanceof SlabBlock && trim.has(Form.SLAB)) {
					d.set(x, y, z, trim.slab(st.getValue(SlabBlock.TYPE)));
				} else if (!(st.getBlock() instanceof StairBlock) && !(st.getBlock() instanceof SlabBlock)) {
					d.set(x, y, z, trim.full());
				}
			}
		}
	}
}
