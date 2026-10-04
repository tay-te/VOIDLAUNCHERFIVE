package dev.voidmc.hearth.build.archetype.farm;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.homestead.Bounds;
import dev.voidmc.hearth.build.kit.homestead.Crops;
import dev.voidmc.hearth.build.kit.homestead.Sheds;
import dev.voidmc.hearth.build.kit.homestead.Spots;
import dev.voidmc.hearth.build.kit.homestead.Terraces;
import dev.voidmc.hearth.build.kit.homestead.Trees;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code hearth:farm/field}: an arable field that a farmer really works. Farmland in level beds (a gentle slope is
 * stepped into beds along the contour, held by grassy banks or, where the culture walls its fields, by dry-stone), every
 * furrow within four blocks of a water channel, strips of the culture's crops each sown together and standing at its
 * own stage, a pumpkin or melon patch now and then, sugar cane on the channel banks where it is warm. A headland path
 * runs from the gate up the side with steps through the banks; a field shed stands in a back corner with the compost
 * bin (the farmer's job site), tools and seed; a scarecrow keeps the crows off; a hay rick or compost heap by the shed.
 * The boundary is the culture's: dry-stone walls on the moor and by the sea, hedges in the karst and the vale, rails
 * elsewhere, with a gate on the street.
 *
 * <p>Job sites (FARMER): the compost bin in the shed, and a stand on the headland path every few rows. Storage: the
 * shed's chest.
 *
 * <p>Params (all optional):
 * <ul>
 *   <li>{@code shed} (boolean): a field shed in a back corner (default: by seed, likely on fields 11+ deep)</li>
 *   <li>{@code boundary} (string: drystone, hedge, rails, bund, none): override the culture's boundary</li>
 *   <li>{@code terraced} (boolean, from the planner): stands on a terrace platform: one bed at the floor</li>
 * </ul>
 */
public final class Field implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("farm/field"), ArchetypeCategory.FARM, Set.of(JobType.FARMER), 0,
		9, 24, 9, 24, 6, 10, SettlementTier.CAMP, Set.of(), Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 0, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		// beds stepping down the slope; the plot's ring is shaped with them so channels stay in the ground. The bed the
		// gate opens into meets the street: the front bed at the headland's column, or the bed in the middle
		boolean leftSide = d.chance(0.5);
		int sideAccess = leftSide ? 1 : w - 2;
		Terraces t = Terraces.plan(d, 0, 0, w - 1, dp - 1, 4, 9, 2, sideAccess, w / 2);
		Bounds.Kind boundary = boundary(d);
		Terraces.Edge edge = boundary == Bounds.Kind.DRYSTONE ? Terraces.Edge.DRYSTONE : Terraces.Edge.BANK;
		// the headland path: up the side by the gate for beds along z, along the front inside the gate for beds along x
		int access = t.alongX ? 1 : sideAccess;
		int gateX = t.alongX ? gateColumn(t, w) : access;
		d.entrance(gateX, Terraces.street(d, gateX), -1);
		d.poi(PoiType.GATE, gateX, t.level(gateX, 0), 0, Dirs.BACK);
		t.shape(d, edge, access);
		t.accessPath(d);
		// the inside cell of the gate joins the path
		pathCell(d, gateX, 0, t.level(gateX, 0));
		if (t.alongX) {
			pathCell(d, gateX, 1, t.level(gateX, 1));
		}

		// a shed in a back corner
		int shedW = 4;
		int shedD = 3;
		boolean wantShed = d.param("shed", dp >= 11 ? d.chance(0.85) : d.chance(0.4));
		int[] shed = null;
		if (wantShed) {
			shed = shedSpot(d, t, w, dp, shedW, shedD, leftSide);
		}

		// the beds
		List<int[]> crops = new ArrayList<>();
		long salt = 0x0F1E1D;
		for (Terraces.Band b : t.bands) {
			int from = Math.max(1, t.plantFrom(b));
			int to = t.plantTo(b);
			int y = b.level();
			int bx0;
			int bx1;
			int bz0;
			int bz1;
			if (t.alongX) {
				bx0 = Math.max(1, from);
				bx1 = Math.min(w - 2, to);
				bz0 = 2;
				bz1 = dp - 2;
			} else {
				bz0 = Math.max(1, from);
				bz1 = Math.min(dp - 2, to);
				bx0 = access == 1 ? 2 : 1;
				bx1 = access == 1 ? w - 2 : w - 3;
			}
			if (bx1 - bx0 < 1 || bz1 - bz0 < 1) {
				continue;
			}
			Direction.Axis rows = t.alongX ? Direction.Axis.Z : Direction.Axis.X;
			if (t.bands.size() == 1) {
				rows = bx1 - bx0 >= bz1 - bz0 ? Direction.Axis.X : Direction.Axis.Z;
			}
			Crops.bed(d, bx0, bz0, bx1, bz1, y, rows, salt++);
			crops.add(new int[]{bx0, bz0, bx1, bz1, y});
		}

		if (shed != null) {
			fieldShed(d, shed, t);
		}

		// a scarecrow in the middle of the biggest bed, facing the street
		int[] big = null;
		for (int[] c : crops) {
			if (big == null || (c[2] - c[0]) * (c[3] - c[1]) > (big[2] - big[0]) * (big[3] - big[1])) {
				big = c;
			}
		}
		if (big != null && big[4] + 3 < d.maxHeight()) {
			int[] spot = scarecrowCell(d, (big[0] + big[2]) / 2, (big[1] + big[3]) / 2, big[4], shed);
			if (spot != null) {
				clearCrop(d, spot[0], big[4], spot[1]);
				Crops.scarecrow(d, spot[0], big[4], spot[1], Dirs.FRONT);
			}
			// a second on a big field
			if ((big[2] - big[0]) * (big[3] - big[1]) > 200) {
				int[] s2 = scarecrowCell(d, big[0] + (big[2] - big[0]) / 4, big[1] + 3 * (big[3] - big[1]) / 4, big[4], shed);
				if (s2 != null && (spot == null || Math.abs(s2[0] - spot[0]) + Math.abs(s2[1] - spot[1]) > 5)) {
					clearCrop(d, s2[0], big[4], s2[1]);
					Crops.scarecrow(d, s2[0], big[4], s2[1], Dirs.FRONT);
				}
			}
		}

		// job sites on the headland, one every few rows, looking into the beds
		jobStands(d, t, access, w, dp);

		// the boundary, with the gate
		List<int[]> gates = new ArrayList<>();
		gates.add(new int[]{gateX, 0});
		Bounds.around(d, 0, 0, w - 1, dp - 1, boundary, gates);
		// a tree or two standing in the boundary at the corners away from the gate
		corners(d, t, boundary, gateX, w, dp, shed);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, t.maxLevel() + 7));
	}

	/** The culture's boundary, or the param's. */
	static Bounds.Kind boundary(Draft d) {
		String p = d.param("boundary", "");
		if (!p.isEmpty()) {
			try {
				return Bounds.Kind.valueOf(p.toUpperCase(java.util.Locale.ROOT));
			} catch (IllegalArgumentException e) {
				// fall through to the culture's
			}
		}
		return Bounds.kind(d);
	}

	/** For beds along x: a column in the bed held at the street's level (the middle one), clear of its edges. */
	private static int gateColumn(Terraces t, int w) {
		Terraces.Band b = t.band(w / 2, 0);
		if (b == null) {
			return w / 2;
		}
		return Math.clamp(w / 2, Math.max(2, b.from() + 1), Math.max(Math.max(2, b.from() + 1), Math.min(w - 3, b.to() - 1)));
	}

	private static void pathCell(Draft d, int x, int z, int level) {
		if (level == Integer.MIN_VALUE) {
			return;
		}
		BlockState below = d.get(x, level - 1, z);
		if (below != null && below.getBlock() instanceof net.minecraft.world.level.block.StairBlock) {
			return;
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, level - 1, z, d.mat(Role.PATH).full());
			d.set(x, level, z, Blocks.AIR.defaultBlockState());
		}
	}

	/**
	 * Where the shed goes: a back corner (on the headland's side for beds along z) whose cells all lie in one band's
	 * planting rows; {x0, z0, x1, z1, level}, or null.
	 */
	private static int[] shedSpot(Draft d, Terraces t, int w, int dp, int sw, int sd, boolean leftSide) {
		int z1 = dp - 2;
		int z0 = z1 - sd + 1;
		int[] xs = t.alongX ? new int[]{leftSide ? 1 : w - 1 - sw, leftSide ? w - 1 - sw : 1} : new int[]{leftSide ? 2 : w - 2 - sw, leftSide ? w - 2 - sw : 2};
		for (int x0 : xs) {
			int x1 = x0 + sw - 1;
			if (x0 < 1 || x1 > w - 2) {
				continue;
			}
			Terraces.Band b = t.band(x0, z0);
			if (b == null) {
				continue;
			}
			boolean ok = true;
			// the row in front of the open side too: the shed opens onto its own bed, never onto a bank
			for (int z = z0 - 1; z <= z1 && ok; z++) {
				for (int x = x0; x <= x1 && ok; x++) {
					Terraces.Band c = t.band(x, z);
					int a = t.along(x, z);
					ok = c == b && a >= t.plantFrom(b) && a <= t.plantTo(b);
				}
			}
			if (ok && b.level() + 6 < d.maxHeight()) {
				return new int[]{x0, z0, x1, z1, b.level()};
			}
		}
		return null;
	}

	/** The shed: a lean-to open toward the field, the compost bin, a chest of seed and tools, barrels, a rick by it. */
	private static void fieldShed(Draft d, int[] s, Terraces t) {
		int x0 = s[0];
		int z0 = s[1];
		int x1 = s[2];
		int z1 = s[3];
		int y = s[4];
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				for (int yy = y; yy <= y + 3; yy++) {
					d.remove(x, yy, z);
				}
			}
		}
		Roofs.Roof roof = Sheds.lean(d, x0, z0, x1, z1, y, Dirs.FRONT, 3, false);
		int row = z1 - 1;
		Furniture.workstation(d, JobType.FARMER, x0 + 1, y, row, Dirs.FRONT);
		Furniture.chest(d, x1 - 1, y, row, Dirs.FRONT, List.of(new Object[]{"minecraft:wheat_seeds", 16}, new Object[]{"minecraft:iron_hoe", 1},
			new Object[]{"minecraft:bone_meal", 8}));
		if (x1 - x0 >= 4) {
			Sheds.stores(d, x0 + 2, y, row, Dirs.RIGHT, 1);
		}
		Furniture.hangingLantern(d, (x0 + x1) / 2, roof.bottomY((x0 + x1) / 2, z0 + 1), z0 + 1, 0);
		// a rick or a compost heap beside the shed's open front
		int fz = z0 - 1;
		for (int x : new int[]{x0 - 1, x1 + 1}) {
			if (x >= 1 && x <= d.frontage() - 2 && t.level(x, fz) == y && t.level(x, fz - 1) == y && cleared(d, x, fz, y) && cleared(d, x, fz - 1, y)) {
				clearCrop(d, x, y, fz);
				clearCrop(d, x, y, fz - 1);
				if (Noise.unit(d.seed(), x, 0, fz) < 0.5) {
					try (var st = d.stage(StageKind.EXTERIOR)) {
						d.set(x, y - 1, fz, Blocks.COARSE_DIRT.defaultBlockState());
						d.set(x, y - 1, fz - 1, Blocks.COARSE_DIRT.defaultBlockState());
					}
					Crops.compost(d, x, y, fz, Dirs.FRONT);
				} else {
					try (var st = d.stage(StageKind.EXTERIOR)) {
						d.set(x, y - 1, fz, Blocks.COARSE_DIRT.defaultBlockState());
					}
					d.inStage(StageKind.EXTERIOR, () -> d.set(x, y, fz, Blocks.HAY_BLOCK.defaultBlockState()));
				}
				break;
			}
		}
	}

	/** A cell holding a crop (not water, not a stem's fruit), or null. */
	private static boolean cleared(Draft d, int x, int z, int y) {
		BlockState below = d.get(x, y - 1, z);
		return below != null && below.is(Blocks.FARMLAND);
	}

	private static int[] cropCell(Draft d, int x, int z, int y) {
		for (int r = 0; r < 4; r++) {
			for (int dz = -r; dz <= r; dz++) {
				for (int dx = -r; dx <= r; dx++) {
					if (cleared(d, x + dx, z + dz, y)) {
						return new int[]{x + dx, z + dz};
					}
				}
			}
		}
		return null;
	}

	/**
	 * A cell for a scarecrow near (x, z): it and the cells its arms reach over hold crops, and it stands well clear of the
	 * shed and the way into it.
	 */
	private static int[] scarecrowCell(Draft d, int x, int z, int y, int[] shed) {
		for (int r = 0; r < 5; r++) {
			for (int dz = -r; dz <= r; dz++) {
				for (int dx = -r; dx <= r; dx++) {
					int cx = x + dx;
					int cz = z + dz;
					if (!cleared(d, cx, cz, y) || !cleared(d, cx - 1, cz, y) || !cleared(d, cx + 1, cz, y)) {
						continue;
					}
					if (shed != null && cx >= shed[0] - 2 && cx <= shed[2] + 2 && cz >= shed[1] - 3) {
						continue;
					}
					return new int[]{cx, cz};
				}
			}
		}
		return null;
	}

	private static void clearCrop(Draft d, int x, int y, int z) {
		d.remove(x, y, z);
		d.remove(x, y + 1, z);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y - 1, z, Blocks.GRASS_BLOCK.defaultBlockState());
		}
	}

	/** FARMER stands on the headland path, every four or five cells, facing the beds. */
	private static void jobStands(Draft d, Terraces t, int access, int w, int dp) {
		Direction into = t.alongX ? Dirs.BACK : (access == 1 ? Dirs.RIGHT : Dirs.LEFT);
		for (Terraces.Band b : t.bands) {
			int from = Math.max(t.alongX ? 1 : 2, t.plantFrom(b));
			int to = Math.min(t.alongX ? w - 2 : dp - 2, t.plantTo(b));
			for (int a = from + 1; a <= to; a += 4) {
				int[] xz = t.cell(a, access);
				BlockState below = d.get(xz[0], b.level() - 1, xz[1]);
				if (below != null && below.getBlock() instanceof net.minecraft.world.level.block.StairBlock) {
					continue;
				}
				Spots.onGround(d, PoiType.JOB_SITE, xz[0], b.level(), xz[1], into, JobType.FARMER);
			}
		}
	}

	/** Trees standing in the boundary at the corners (hedgerow oaks, or palms in sandy lands). */
	private static void corners(Draft d, Terraces t, Bounds.Kind boundary, int gateX, int w, int dp, int[] shed) {
		int[][] corners = {{1, dp - 2}, {w - 2, dp - 2}, {w - 2, 1}, {1, 1}};
		int planted = 0;
		boolean sandy = d.mat(Role.GROUND).main(Form.FULL) == Blocks.SAND || d.mat(Role.GROUND).main(Form.FULL) == Blocks.RED_SAND;
		for (int[] c : corners) {
			if (planted >= (boundary == Bounds.Kind.HEDGE ? 2 : 1) || Math.abs(c[0] - gateX) <= 2 && c[1] <= 2) {
				continue;
			}
			if (shed != null && c[0] >= shed[0] - 1 && c[0] <= shed[2] + 1 && c[1] >= shed[1] - 1) {
				continue;
			}
			int y = t.level(c[0], c[1]);
			if (y == Integer.MIN_VALUE || y + 7 >= d.maxHeight() || Noise.unit(d.seed() ^ 0xC0E, c[0], 0, c[1]) < 0.4) {
				continue;
			}
			// the corner cell is planted: take the crop out first
			BlockState below = d.get(c[0], y - 1, c[1]);
			if (below == null || !below.is(Blocks.FARMLAND) && !below.is(Blocks.GRASS_BLOCK) && !below.is(d.mat(Role.GROUND).mainOr(Form.FULL, Blocks.GRASS_BLOCK))) {
				continue;
			}
			clearCrop(d, c[0], y, c[1]);
			if (sandy) {
				Trees.fruit(d, c[0], y, c[1], Trees.Kind.PALM, 1);
			} else if (boundary == Bounds.Kind.HEDGE || boundary == Bounds.Kind.RAILS) {
				Bounds.hedgeTree(d, c[0], y, c[1]);
			} else {
				continue;
			}
			planted++;
		}
	}
}
