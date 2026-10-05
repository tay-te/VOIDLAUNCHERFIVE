package dev.voidmc.hearth.build.archetype.landscape;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.landscape.Benches;
import dev.voidmc.hearth.build.kit.landscape.Lamps;
import dev.voidmc.hearth.build.kit.landscape.Manner;
import dev.voidmc.hearth.build.kit.landscape.Paving;
import dev.voidmc.hearth.build.kit.landscape.Pavilions;
import dev.voidmc.hearth.build.kit.landscape.Towers;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:landscape/bell}: the bell that calls the settlement together.
 *
 * <ul>
 *   <li>On a small plot (3 wide): a bell frame of two timber posts on stone feet, the bell hung between them under a
 *   crossbeam with a little roof, its rope (a chain) hanging, a step of paving round it.</li>
 *   <li>On a larger plot: a bell tower in little, a raised stone podium with steps up the front, four posts carrying a
 *   roof in the culture's form (a curved roof in the karst, a gable or hip elsewhere, an arched sandstone frame with a
 *   cupola in the desert), the bell hanging in the middle, lanterns under the eaves, benches on the podium's sides.</li>
 * </ul>
 *
 * <p>POIs: a MEETING_POINT in front of the bell (where folk gather when it rings), SEATs on the benches. Params: none.
 */
public final class Bell implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("landscape/bell"), ArchetypeCategory.LANDSCAPE, Set.of(), 0,
		3, 5, 3, 5, 5, 9, SettlementTier.CAMP, Set.of(), Set.of(ArchetypeInfo.SiteFlag.CENTRAL), List.of(), 1, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int cx = (w - 1) / 2;
		int cz = (dp - 1) / 2;
		d.entrance(cx, 0, -1);
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		Paving.lay(d, 0, 0, w - 1, dp - 1, 0, Paving.paint(d, Paving.Pattern.RINGS, cx, cz, 2, false));
		int top = w >= 5 && dp >= 5 ? pavilion(d, w, dp, cx, cz) : frame(d, w, dp, cx, cz);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, top + 1));
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	private static BlockState bell(Direction facing, BellAttachType attach) {
		return Blocks.BELL.defaultBlockState().setValue(BellBlock.FACING, facing).setValue(BellBlock.ATTACHMENT, attach);
	}

	/** Two posts with the bell between them under a beam and a little roof. */
	private static int frame(Draft d, int w, int dp, int cx, int cz) {
		Mat post = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat foot = Manner.masonry(d);
		Mat roof = d.mat(Role.ROOF);
		int x0 = Math.max(0, cx - 1);
		int x1 = Math.min(w - 1, cx + 1);
		int h = Math.min(3, d.maxHeight() - 3);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int x : new int[]{x0, x1}) {
				d.set(x, 0, cz, foot.full());
				for (int y = 1; y <= h; y++) {
					d.set(x, y, cz, post.log(Direction.Axis.Y).orElse(post.full()));
				}
			}
			for (int x = x0; x <= x1; x++) {
				d.set(x, h + 1, cz, beam.log(Direction.Axis.X).orElse(beam.full()));
			}
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(cx, h, cz, bell(Dirs.RIGHT, BellAttachType.DOUBLE_WALL));
			d.set(cx, h - 1, cz, States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
		}
		// the little roof: stairs either side of a ridge over the beam
		try (var s = d.stage(StageKind.ROOF)) {
			for (int x = x0; x <= x1; x++) {
				if (cz - 1 >= 0) {
					d.set(x, h + 1, cz - 1, roof.stairsOrSlab(Dirs.BACK, Half.BOTTOM));
				}
				if (cz + 1 < dp) {
					d.set(x, h + 1, cz + 1, roof.stairsOrSlab(Dirs.FRONT, Half.BOTTOM));
				}
				d.set(x, h + 2, cz, roof.slab(SlabType.BOTTOM));
			}
		}
		d.poi(PoiType.MEETING_POINT, cx, 0, Math.max(0, cz - 1), Dirs.BACK);
		return h + 2;
	}

	/** A raised podium with steps, four posts, a roof, the bell in the middle. */
	private static int pavilion(Draft d, int w, int dp, int cx, int cz) {
		return Pavilions.pavilion(d, 0, 0, w - 1, dp - 1, 0, true, true);
	}
}
