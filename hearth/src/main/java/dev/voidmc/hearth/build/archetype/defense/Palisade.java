package dev.voidmc.hearth.build.archetype.defense;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.landscape.Corridor;
import dev.voidmc.hearth.build.kit.landscape.Lamps;
import dev.voidmc.hearth.build.kit.landscape.Manner;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Site;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code hearth:defense/palisade}: a young settlement's stockade along the planner's line: stakes of the local tree
 * driven a block into the ground, set up in panels of three to five whose tops step down a slope panel by panel (not
 * stake by stake into a saw-tooth), their heights ragged by a block and many sharpened to a point, a heavier post every
 * eight or so, and tall gate posts with a lantern where the stretch ends (a lane or a road passes there). A palisade is
 * the line itself, one stake thick; it follows the ground up and down and stands in shallow water on its own feet.
 *
 * <p>Params (PLANNING.md §7): {@code points} (int array x,y,z,... world), {@code width} (1), {@code height} (5: stake
 * tops above the ground). {@code keep}, {@code join_start}, {@code join_end} and {@code heart} as for
 * {@code defense/wall}.
 */
public final class Palisade implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("defense/palisade"), ArchetypeCategory.DEFENSE, Set.of(), 0,
		1, 5, 10, 32, 5, 8, SettlementTier.HAMLET, Set.of(), Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(TownWall.INFO.id()), 0, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		Corridor c = new Corridor(d, 1);
		int n = c.length();
		int h = Math.clamp(d.param("height", 5), 3, 8);
		Mat log = Manner.fort(d, Role.TREE_LOG);
		Mat frame = Manner.fort(d, Role.FRAME);
		Mat fence = Manner.fort(d, Role.FENCE);
		Paint stake = log.log(Direction.Axis.Y).orElse(log.full());
		Paint stripped = log.has(Form.STRIPPED_LOG) ? log.strippedLog(Direction.Axis.Y) : stake;
		Paint post = frame.log(Direction.Axis.Y).orElse(stake);
		Paint point = fence.has(Form.FENCE) ? fence.fence() : frame.has(Form.FENCE) ? frame.fence() : null;
		boolean capStart = !c.open(true);
		boolean capEnd = !c.open(false);
		// the panels: the ground under each step, taken in runs whose tops are level, stepping with the slope
		int[] ground = new int[n];
		java.util.Arrays.fill(ground, Integer.MIN_VALUE);
		for (Corridor.Cell cell : c.cells) {
			ground[cell.index()] = Math.max(ground[cell.index()], d.site().ground(cell.x(), cell.z()));
		}
		for (int i = 0; i < n; i++) {
			if (ground[i] == Integer.MIN_VALUE) {
				ground[i] = c.ground[i];
			}
		}
		int[] panel = TownWall.crest(ground, 2);
		int top = 0;
		try (var s = d.stage(StageKind.FRAME)) {
			for (Corridor.Cell cell : c.cells) {
				// one stake thick: the line and its town-side neighbour where the line runs aslant
				if (cell.offset() < -0.01 || Math.abs(cell.offset()) > cell.walkBand()) {
					continue;
				}
				int x = cell.x();
				int z = cell.z();
				int g = d.site().ground(x, z);
				int i = cell.index();
				boolean end = capStart && i <= 0 || capEnd && i >= n - 1;
				boolean heavy = !end && Math.floorMod(Math.round(cell.t()), 8) == 3;
				double u = Noise.unit(d.seed() ^ 0x5A1E, x, 0, z);
				int height = (end ? h + 1 : heavy ? h : h - 1 + (u < 0.45 ? 1 : 0)) + panel[i] - g;
				height = Math.min(height, d.maxHeight() - 2 - g);
				Paint p = end || heavy ? post : u < 0.12 ? stripped : stake;
				for (int y = g - 1; y < g + height; y++) {
					d.set(x, y, z, p);
				}
				top = Math.max(top, g + height);
				if (end) {
					Lamps.lanternOn(d, x, g + height, z);
				} else if (point != null && (u > 0.3 || heavy)) {
					d.set(x, g + height, z, point);
				}
			}
		}
		int ex = Math.clamp(c.lx[n - 1], 0, d.frontage() - 1);
		d.entrance(ex, Math.max(d.site().ground(ex, -1), waterTop(d, ex, -1)), -1);
		try (var s = d.stage(StageKind.CLEAR)) {
			BlockState air = Blocks.AIR.defaultBlockState();
			for (Corridor.Cell cell : c.cells) {
				int g = d.site().ground(cell.x(), cell.z());
				int w = d.site().water(cell.x(), cell.z());
				for (int y = w == Site.NONE ? g : w; y <= Math.min(d.maxHeight() - 1, top + 1); y++) {
					if (d.isAbsent(cell.x(), y, cell.z())) {
						d.set(cell.x(), y, cell.z(), air);
					}
				}
			}
		}
	}

	private static int waterTop(Draft d, int x, int z) {
		int w = d.site().water(x, z);
		return w == Site.NONE ? Integer.MIN_VALUE : w;
	}
}
