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
 *   <li>On a small plot: a bell frame in the culture's manner, the bell hung under it, its rope (a chain) hanging, a
 *   step of paving round it: two timber posts on stone feet under a beam and a little shingled gable (Bohemia, the
 *   Alps, the moors, the coast, the bayou); an A-frame of raking logs under a turf cap (the fjords, the tundra); a
 *   masonry arch with a stepped crest (desert, mesa, steppe); red posts under a curved roof with its ends turned up
 *   (the karst, the blossom vale); a bell gable, a rendered wall with the bell in an arched opening (the
 *   mediterranean); bamboo posts under a thatched cap (the tropics).</li>
 *   <li>On a larger plot: a bell tower in little, a raised stone podium with steps up the front, four posts carrying a
 *   roof in the culture's form (a curved roof in the karst, a gable or hip elsewhere, an arched sandstone frame with a
 *   cupola in the desert), the bell hanging in the middle, lanterns under the eaves, benches on the podium's
 *   sides.</li>
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

	/** The small frame of the culture. Returns its top. */
	private static int frame(Draft d, int w, int dp, int cx, int cz) {
		int r = w >= 5 ? 2 : 1;
		int top = switch (Manner.culture(d)) {
			case NORTHERN, TUNDRA -> aFrame(d, cx, cz, r);
			case DESERT, MESA, STEPPE -> arch(d, cx, cz, r);
			case KARST, WISTERIA -> curved(d, cx, cz, r, dp);
			case MEDITERRANEAN -> bellGable(d, cx, cz, r);
			case TROPICAL -> thatched(d, cx, cz, r, dp);
			default -> gable(d, w, dp, cx, cz, r);
		};
		d.poi(PoiType.MEETING_POINT, cx, 0, Math.max(0, cz - 1), Dirs.BACK);
		return top;
	}

	/** Two posts on stone feet with the bell between them under a beam (braced on a wide plot) and a little gable. */
	private static int gable(Draft d, int w, int dp, int cx, int cz, int r) {
		Mat post = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat foot = Manner.masonry(d);
		Mat roof = d.mat(Role.ROOF);
		int x0 = Math.max(0, cx - r);
		int x1 = Math.min(w - 1, cx + r);
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
			// knee braces under the beam where the posts stand wide
			if (r >= 2) {
				d.set(x0 + 1, h, cz, beam.stairsOrSlab(Dirs.LEFT, Half.TOP));
				d.set(x1 - 1, h, cz, beam.stairsOrSlab(Dirs.RIGHT, Half.TOP));
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
		return h + 2;
	}

	/** The fjords' bell frame: two pairs of logs raking in to an apex, the bell under it, a cap of turf over the top. */
	private static int aFrame(Draft d, int cx, int cz, int r) {
		Mat log = d.mat(Role.FRAME);
		Mat foot = Manner.masonry(d);
		int apex = Math.min(r + 2, d.maxHeight() - 3);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int side : new int[]{-1, 1}) {
				d.set(cx + side * r, 0, cz, foot.full());
				// the raking leg: upright at the foot, then stepping in a block a course to the apex
				for (int y = 1; y < apex; y++) {
					int x = cx + side * Math.max(1, r - (y - 1));
					d.set(x, y, cz, log.log(Direction.Axis.Y).orElse(log.full()));
				}
			}
			for (int x = cx - 1; x <= cx + 1; x++) {
				d.set(x, apex, cz, log.log(Direction.Axis.X).orElse(log.full()));
			}
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(cx, apex - 1, cz, bell(Dirs.FRONT, BellAttachType.CEILING));
		}
		try (var s = d.stage(StageKind.ROOF)) {
			// turf on the apex beam, its ends sloping off
			d.set(cx, apex + 1, cz, Blocks.MOSS_BLOCK.defaultBlockState());
			for (int side : new int[]{-1, 1}) {
				d.set(cx + side, apex + 1, cz, d.mat(Role.ROOF).stairsOrSlab(side < 0 ? Dirs.RIGHT : Dirs.LEFT, Half.BOTTOM));
			}
			if (apex + 2 < d.maxHeight()) {
				d.set(cx, apex + 2, cz, Blocks.MOSS_CARPET.defaultBlockState());
			}
		}
		return apex + 2;
	}

	/** The dry lands' bell arch: two piers of the masonry, the bell under the arch's head, a stepped crest over it. */
	private static int arch(Draft d, int cx, int cz, int r) {
		Mat mas = Manner.masonry(d);
		int h = Math.min(3, d.maxHeight() - 4);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int side : new int[]{-1, 1}) {
				for (int y = 0; y <= h; y++) {
					d.set(cx + side, y, cz, mas.full());
				}
				// a battered foot where the plot is wide
				if (r >= 2) {
					d.set(cx + side * 2, 0, cz, mas.stairsOrSlab(side < 0 ? Dirs.RIGHT : Dirs.LEFT, Half.BOTTOM));
				}
			}
			d.set(cx, h + 1, cz, mas.full());
			d.set(cx - 1, h + 1, cz, mas.full());
			d.set(cx + 1, h + 1, cz, mas.full());
			// the arch's head: shoulders in the opening under the lintel
			d.set(cx, h, cz, Blocks.AIR.defaultBlockState());
			// the crest: a step either side, the middle standing up
			d.set(cx - 1, h + 2, cz, mas.slab(SlabType.BOTTOM));
			d.set(cx + 1, h + 2, cz, mas.slab(SlabType.BOTTOM));
			d.set(cx, h + 2, cz, mas.full());
			if (h + 3 < d.maxHeight()) {
				d.set(cx, h + 3, cz, mas.slab(SlabType.BOTTOM));
			}
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(cx, h, cz, bell(Dirs.FRONT, BellAttachType.CEILING));
		}
		return h + 3;
	}

	/** The karst's bell frame: red posts, a beam, a little curved roof with its ends turned up. */
	private static int curved(Draft d, int cx, int cz, int r, int dp) {
		Mat post = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat roof = d.mat(Role.ROOF);
		Mat foot = Manner.masonry(d);
		int h = Math.min(3, d.maxHeight() - 3);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int side : new int[]{-1, 1}) {
				int x = cx + side * r;
				d.set(x, 0, cz, foot.slab(SlabType.TOP));
				for (int y = 1; y <= h; y++) {
					d.set(x, y, cz, post.log(Direction.Axis.Y).orElse(post.full()));
				}
			}
			for (int x = cx - r; x <= cx + r; x++) {
				d.set(x, h + 1, cz, beam.log(Direction.Axis.X).orElse(beam.full()));
			}
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(cx, h, cz, bell(Dirs.FRONT, BellAttachType.CEILING));
		}
		try (var s = d.stage(StageKind.ROOF)) {
			for (int x = cx - r; x <= cx + r; x++) {
				boolean end = Math.abs(x - cx) == r;
				for (int z = Math.max(0, cz - 1); z <= Math.min(dp - 1, cz + 1); z++) {
					if (z == cz) {
						d.set(x, h + 2, z, end ? roof.stairsOrSlab(x < cx ? Dirs.LEFT : Dirs.RIGHT, Half.BOTTOM) : roof.slab(SlabType.BOTTOM));
					} else {
						d.set(x, h + 1, z, roof.stairsOrSlab(z < cz ? Dirs.BACK : Dirs.FRONT, Half.BOTTOM));
						if (end) {
							// the eave's corner turned up
							d.set(x, h + 2, z, roof.slab(SlabType.BOTTOM));
						}
					}
				}
			}
		}
		return h + 2;
	}

	/** The mediterranean bell gable: a rendered wall with the bell in an arched opening, a pediment of tiles over it. */
	private static int bellGable(Draft d, int cx, int cz, int r) {
		Mat wall = d.mat(Role.WALL);
		Mat accent = d.mat(Role.WALL_ACCENT);
		Mat roof = d.mat(Role.ROOF);
		int h = Math.min(4, d.maxHeight() - 3);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = cx - 1; x <= cx + 1; x++) {
				for (int y = 0; y <= h; y++) {
					boolean opening = x == cx && y >= 1 && y <= h - 1;
					d.set(x, y, cz, opening ? Blocks.AIR.defaultBlockState() : y == 0 ? accent.full().at(x, y, cz) : wall.full().at(x, y, cz));
				}
			}
			// the opening's arched head, a sill of the dressing
			d.set(cx, h - 1, cz, Blocks.AIR.defaultBlockState());
			if (r >= 2) {
				// low wings of wall stepping down either side
				d.set(cx - 2, 0, cz, wall.full());
				d.set(cx + 2, 0, cz, wall.full());
				d.set(cx - 2, 1, cz, accent.stairsOrSlab(Dirs.RIGHT, Half.BOTTOM));
				d.set(cx + 2, 1, cz, accent.stairsOrSlab(Dirs.LEFT, Half.BOTTOM));
			}
		}
		try (var s = d.stage(StageKind.ROOF)) {
			d.set(cx - 1, h + 1, cz, roof.stairsOrSlab(Dirs.RIGHT, Half.BOTTOM));
			d.set(cx + 1, h + 1, cz, roof.stairsOrSlab(Dirs.LEFT, Half.BOTTOM));
			d.set(cx, h + 1, cz, roof.full());
			if (h + 2 < d.maxHeight()) {
				d.set(cx, h + 2, cz, roof.slab(SlabType.BOTTOM));
			}
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(cx, h - 1, cz, bell(Dirs.FRONT, BellAttachType.CEILING));
		}
		return h + 2;
	}

	/** The tropical bell: bamboo posts, a beam, a steep cap of thatch over it. */
	private static int thatched(Draft d, int cx, int cz, int r, int dp) {
		Mat post = d.mat(Role.FRAME);
		Mat roof = d.mat(Role.ROOF);
		Mat pole = post.has(Form.FENCE) ? post : d.mat(Role.FENCE);
		int h = Math.min(3, d.maxHeight() - 4);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int side : new int[]{-1, 1}) {
				for (int y = 0; y <= h; y++) {
					d.set(cx + side, y, cz, pole.fence());
				}
			}
			for (int x = cx - 1; x <= cx + 1; x++) {
				d.set(x, h + 1, cz, Blocks.HAY_BLOCK.defaultBlockState());
			}
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(cx, h, cz, bell(Dirs.FRONT, BellAttachType.CEILING));
		}
		try (var s = d.stage(StageKind.ROOF)) {
			for (int x = cx - r; x <= cx + r; x++) {
				for (int z = Math.max(0, cz - 1); z <= Math.min(dp - 1, cz + 1); z++) {
					if (z != cz) {
						d.set(x, h + 1, z, roof.stairsOrSlab(z < cz ? Dirs.BACK : Dirs.FRONT, Half.BOTTOM));
					} else if (Math.abs(x - cx) == r && r >= 2) {
						d.set(x, h + 1, z, roof.stairsOrSlab(x < cx ? Dirs.RIGHT : Dirs.LEFT, Half.BOTTOM));
					}
				}
				if (Math.abs(x - cx) <= 1) {
					d.set(x, h + 2, cz, Math.abs(x - cx) == 1 ? roof.stairsOrSlab(x < cx ? Dirs.RIGHT : Dirs.LEFT, Half.BOTTOM) : Paint.of(Blocks.HAY_BLOCK.defaultBlockState()));
				}
			}
			if (h + 3 < d.maxHeight()) {
				d.set(cx, h + 3, cz, roof.slab(SlabType.BOTTOM));
			}
		}
		return h + 3;
	}

	/** A raised podium with steps, four posts, a roof, the bell in the middle. */
	private static int pavilion(Draft d, int w, int dp, int cx, int cz) {
		return Pavilions.pavilion(d, 0, 0, w - 1, dp - 1, 0, true, true);
	}
}
