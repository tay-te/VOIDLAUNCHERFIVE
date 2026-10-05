package dev.voidmc.hearth.build.archetype.deep;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.deep.DeepManner;
import dev.voidmc.hearth.build.kit.deep.DeepSite;
import dev.voidmc.hearth.build.kit.deep.Glow;
import dev.voidmc.hearth.build.kit.deep.Rock;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:deep/glow_garden}: where the deep town grows its food under the vault (LIVING_WORLD.md §8.2): glowcaps,
 * mushrooms and glow berries in the realm's moss, lit by the realm's own light. A low wall of the realm's stone round
 * it, a gate to the street; four raised beds of moss and rooted earth in dressed kerbs, each with a rill of water down
 * it, planted in rows with the realm's glowcaps and mushrooms (azalea and ferns in the lush realms, crimson roots in
 * the ember realms); down the main walk a timber pergola dripping with glow berries; at the crossing of the walks a
 * giant mushroom over a bench; lamp posts of the realm's glow at the corners; a potting shed in a back corner with the
 * gardener's composter, his chest and barrels.
 *
 * <p>Params: {@code realm}, {@code ceiling_y}, {@code character}, {@code local} ({@link DeepSite}); {@code variant}.
 */
public final class GlowGarden implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("deep/glow_garden"), ArchetypeCategory.UNDERGROUND, Set.of(JobType.FARMER), 0,
		11, 19, 11, 19, 6, 9, SettlementTier.VILLAGE, Set.of(), Set.of(), List.of(), 0, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public TerrainProbe terrain(PlotSpec spec, TerrainProbe probe) {
		return DeepSite.probeFor(spec, probe);
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		DeepSite ds = DeepSite.of(d);
		Glow g = Glow.of(ds.character());
		int h = Math.max(5, Math.min(d.maxHeight(), ds.top() + 1));
		int cx = w / 2;
		int cz = dp / 2;
		d.body(0, 0, w - 1, dp - 1);
		d.eave(3);

		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		Groundwork.footings(d, 0, 0, w - 1, dp - 1, -1);
		Mat path = d.mat(Role.PATH).has(Form.FULL) ? d.mat(Role.PATH) : d.mat(Role.PAVING);
		Mat paving = d.mat(Role.PAVING);
		Mat kerb = Rock.dressing(d);
		Mat wall = d.mat(Role.WALL);
		Mat rough = d.mat(Role.FOUNDATION);

		// the walks: the main one up the middle (three wide under the pergola), the cross walk, a walk round inside the wall
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 0; z < dp; z++) {
				for (int x = 0; x < w; x++) {
					d.set(x, -1, z, path.full());
				}
			}
			for (int z = 0; z < dp; z++) {
				d.set(cx, -1, z, paving.full());
			}
		}
		// the wall round the garden: rough stone a block and a half high with a coping, piers at the corners and the gate
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = 0; x < w; x++) {
				for (int z = 0; z < dp; z++) {
					boolean edge = x == 0 || x == w - 1 || z == 0 || z == dp - 1;
					if (!edge || z == 0 && Math.abs(x - cx) <= 1) {
						continue;
					}
					boolean pier = (x == 0 || x == w - 1) && (z == 0 || z == dp - 1) || z == 0 && Math.abs(x - cx) == 2;
					d.set(x, 0, z, pier ? kerb.full() : rough.full());
					d.set(x, 1, z, pier ? kerb.full() : wall.has(Form.WALL) ? wall.wall() : kerb.slab(SlabType.BOTTOM));
				}
			}
		}
		// the beds: four quarters inside the walk round, kerbed, of the realm's soil, a rill down each
		int in0 = 2;
		int inX1 = w - 3;
		int inZ1 = dp - 3;
		int[][] quarters = {{in0, cx - 2, in0, cz - 1}, {cx + 2, inX1, in0, cz - 1}, {in0, cx - 2, cz + 1, inZ1}, {cx + 2, inX1, cz + 1, inZ1}};
		// the potting shed takes the back right quarter's far corner
		int shedX0 = Math.max(cx + 2, inX1 - 3);
		int shedZ0 = Math.max(cz + 1, inZ1 - 3);
		boolean shed = inX1 - shedX0 >= 3 && inZ1 - shedZ0 >= 3;
		for (int q = 0; q < 4; q++) {
			int[] b = quarters[q];
			bed(d, b[0], b[1], b[2], b[3], g, shed && q == 3 ? new int[]{shedX0 - 1, shedZ0 - 1} : null, q);
		}
		if (shed) {
			shed(d, shedX0, shedZ0, inX1 + 1, inZ1 + 1, g);
		} else {
			// no room for a shed: the gardener's composter and his chest stand at the head of the main walk
			Furniture.workstation(d, JobType.FARMER, cx, 0, dp - 2, Dirs.FRONT);
			Furniture.chest(d, cx + 2, 0, dp - 2, Dirs.LEFT, List.of(new Object[]{"minecraft:glow_berries", 16},
				new Object[]{"minecraft:brown_mushroom", 12}, new Object[]{"minecraft:bone_meal", 8}));
		}
		// the pergola down the main walk, glow berries hanging from it
		pergola(d, cx, 1, dp - 2, cz, Math.min(3, h - 3), g);
		// the giant mushroom at the crossing
		if (h >= 7) {
			mushroom(d, cx, cz, Math.min(h - 2, 5), g);
		} else {
			Rock.lampPost(d, cx, 0, cz, true, g);
		}
		// lamp posts at the inner corners and by the gate, the gate itself
		for (int[] c : new int[][]{{1, 1}, {w - 2, 1}, {1, dp - 2}, {w - 2, dp - 2}}) {
			if (d.isAbsent(c[0], 0, c[1]) || d.isEmpty(c[0], 0, c[1])) {
				if (!(shed && c[0] == w - 2 && c[1] == dp - 2)) {
					Rock.lampPost(d, c[0], 0, c[1], true, g);
				}
			}
		}
		Mat fence = d.mat(Role.FENCE);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(cx - 1, 0, 0, fence.gate(Dirs.FRONT, true));
			d.set(cx + 1, 0, 0, fence.gate(Dirs.FRONT, true));
		}
		d.entrance(cx, 0, -1);
		d.poi(PoiType.GATE, cx, 0, 0, Dirs.BACK);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, h));
	}

	/**
	 * A raised bed over a rectangle: a kerb of the dressing stone round it, the realm's soil inside, a rill of water down
	 * its long middle, plants of the realm in rows on the soil either side ({@code cut} keeps a corner clear for the shed).
	 */
	private static void bed(Draft d, int x0, int x1, int z0, int z1, Glow g, int[] cut, int q) {
		if (x1 - x0 < 1 || z1 - z0 < 1) {
			return;
		}
		Mat kerb = Rock.dressing(d);
		boolean alongZ = z1 - z0 >= x1 - x0;
		int mid = alongZ ? (x0 + x1) / 2 : (z0 + z1) / 2;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					if (cut != null && x >= cut[0] && z >= cut[1]) {
						continue;
					}
					boolean edge = x == x0 || x == x1 || z == z0 || z == z1 || cut != null && (x == cut[0] - 1 && z >= cut[1] || z == cut[1] - 1 && x >= cut[0]);
					int along = alongZ ? x : z;
					boolean rill = !edge && along == mid && (alongZ ? x1 - x0 : z1 - z0) >= 4;
					if (edge) {
						d.set(x, 0, z, kerb.full());
					} else if (rill) {
						d.set(x, 0, z, Blocks.WATER.defaultBlockState());
						d.set(x, -1, z, Blocks.MUD.defaultBlockState());
					} else {
						d.set(x, 0, z, g.soil.defaultBlockState());
						Block plant = plantFor(d, g, x, z, along - mid, q);
						if (plant != null) {
							d.set(x, 1, z, Glow.plantState(plant));
						}
					}
				}
			}
		}
	}

	/** What grows at a spot of a bed: rows of the realm's plants, a gap now and then. */
	private static Block plantFor(Draft d, Glow g, int x, int z, int fromRill, int q) {
		double u = Noise.unit(d.seed() ^ 0x6A4D, x, q, z);
		if (u < 0.15) {
			return null;
		}
		Block p = (Math.abs(fromRill) + q) % 2 == 0 ? g.plants[0] : g.plants[Math.min(g.plants.length - 1, 1 + (int) (u * (g.plants.length - 1)))];
		return Glow.grows(g.soil, p) ? p : Blocks.BROWN_MUSHROOM;
	}

	/**
	 * The pergola down the main walk: posts of the frame's timber either side every third cell, beams across them and
	 * rails along, glow berries hanging from the rails over the beds' edge and short over the walk.
	 */
	private static void pergola(Draft d, int cx, int z0, int z1, int cz, int top, Glow g) {
		Mat post = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat rail = d.mat(Role.FRAME).has(Form.FENCE) ? d.mat(Role.FRAME) : d.mat(Role.FURNITURE);
		int y = top;
		try (var s = d.stage(StageKind.FRAME)) {
			for (int z = z0 + 1; z <= z1 - 1; z++) {
				if (Math.abs(z - cz) <= 2) {
					continue;
				}
				boolean postRow = (z - z0) % 3 == 1;
				for (int x : new int[]{cx - 1, cx + 1}) {
					if (postRow) {
						for (int yy = 0; yy < y; yy++) {
							d.set(x, yy, z, post.log(Direction.Axis.Y));
						}
					}
					d.set(x, y, z, rail.fence());
				}
				if (postRow) {
					for (int x = cx - 1; x <= cx + 1; x++) {
						d.set(x, y, z, beam.log(Direction.Axis.X));
					}
				}
			}
		}
		for (int z = z0 + 1; z <= z1 - 1; z++) {
			if (Math.abs(z - cz) <= 2) {
				continue;
			}
			for (int x : new int[]{cx - 1, cx + 1}) {
				if (d.isAbsent(x, y - 1, z) && Noise.unit(d.seed() ^ 0x7E, x, 0, z) < 0.75) {
					int len = Math.min(y - 1, 1 + (int) (Noise.unit(d.seed() ^ 0x7F, x, 1, z) * 2));
					Glow.vine(d, x, y - 1, z, len);
				}
			}
			if ((z - z0) % 3 == 1 && d.isAbsent(cx, y - 1, z) && y - 1 >= 2) {
				Glow.vine(d, cx, y - 1, z, 1);
			}
		}
	}

	/** A giant mushroom: a stem of mushroom stem, a broad cap of the realm's kind, a bench round the stem's foot. */
	private static void mushroom(Draft d, int x, int z, int capY, Glow g) {
		Block cap = g.character.equals("ember") || g.character.equals("brimstone") ? Blocks.RED_MUSHROOM_BLOCK : Blocks.BROWN_MUSHROOM_BLOCK;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int y = 0; y < capY; y++) {
				d.set(x, y, z, Blocks.MUSHROOM_STEM.defaultBlockState());
			}
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
						continue;
					}
					d.set(x + dx, capY, z + dz, cap.defaultBlockState());
				}
			}
			// the cap's rim turned down at its edges, a glow under the middle
			for (int[] o : new int[][]{{-3, 0}, {3, 0}, {0, -3}, {0, 3}}) {
				if (Rock.inPlot(d, x + o[0], z + o[1])) {
					d.set(x + o[0], capY - 1, z + o[1], cap.defaultBlockState());
				}
			}
			d.set(x, capY - 1, z, g.lamp.defaultBlockState());
		}
		for (int[] o : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
			Direction back = Dirs.of(-o[0], 0);
			if (d.isAbsent(x + o[0], 0, z + o[1])) {
				Rock.bench(d, x + o[0], 0, z + o[1], back == null ? Dirs.LEFT : back, Dirs.FRONT, 1);
			}
		}
	}

	/** The potting shed: rough walls, a door to the walk, a lean-to roof of the culture's covering, the gardener's things. */
	private static void shed(Draft d, int x0, int z0, int x1, int z1, Glow g) {
		Mat rough = d.mat(Role.FOUNDATION);
		Paint home = DeepManner.home(d, Role.WALL).orElse(d.mat(Role.WALL).full());
		Mat frame = d.mat(Role.FRAME);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
					boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
					d.set(x, -1, z, d.mat(Role.FLOOR).full());
					for (int y = 0; y < 3; y++) {
						if (edge) {
							d.set(x, y, z, y == 0 ? rough.full() : corner ? frame.log(Direction.Axis.Y) : home);
						} else {
							d.set(x, y, z, Blocks.AIR.defaultBlockState());
						}
					}
				}
			}
		}
		Mat sheet = d.mat(Role.SHEET).has(Form.STAIRS) ? d.mat(Role.SHEET) : d.mat(Role.ROOF);
		Roofs.Field f = Roofs.shed(x0 - 1, z0, x1, z1, Dirs.RIGHT, Roofs.Profile.straight(0.5));
		Roofs.Roof roof = Roofs.raster(d, f, 3, x0, z0, x1, z1, sheet, d.mat(Role.ROOF_TRIM), Habits.Ridge.SLAB);
		if (!DeepManner.thatches(d)) {
			DeepMarket.homeCovering(d, x0, 3, z0, x1, 6, z1, sheet);
		}
		// the walls carried up under the roof's slope (its gable ends and high side)
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
					for (int y = 3; edge && roof.covers(x, z) && y < roof.bottomY(x, z); y++) {
						d.set(x, y, z, home);
					}
				}
			}
		}
		// the door toward the cross walk (the pergola's posts stand along the main walk), a window to the pergola
		Block door = d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR);
		int dx = (x0 + x1) / 2;
		int dz = (z0 + z1) / 2;
		d.air(dx, 0, z0);
		d.air(dx, 1, z0);
		d.inStage(StageKind.OPENINGS, () -> d.door(dx, 0, z0, door, Dirs.FRONT, DoorHingeSide.LEFT));
		d.poi(PoiType.DOOR, dx, 0, z0 - 1, Dirs.BACK);
		try (var s = d.stage(StageKind.OPENINGS)) {
			d.set(x0, 1, dz, d.mat(Role.GLASS).pane());
		}
		// inside: the composter (the gardener's job site), a chest of seed and spawn, barrels, a lantern
		Furniture.workstation(d, JobType.FARMER, x1 - 1, 0, z1 - 1, Dirs.LEFT);
		Furniture.chest(d, x1 - 1, 0, z0 + 1, Dirs.LEFT, List.of(new Object[]{"minecraft:glow_berries", 16}, new Object[]{"minecraft:brown_mushroom", 12},
			new Object[]{"minecraft:bone_meal", 8}));
		// a lantern hung from the roof where it is low enough, else stood on the chest
		if (d.isSolid(x0 + 1, 3, z0 + 1)) {
			Lighting.chandelier(d, x0 + 1, z0 + 1, 3, 2);
		} else if (d.isAbsent(x1 - 1, 1, z0 + 1)) {
			Lighting.lamp(d, x1 - 1, 1, z0 + 1);
		}
		if (d.isAbsent(dx + 1, 2, z0 - 1)) {
			Lighting.doorLamp(d, dx + 1, 2, z0 - 1, Dirs.BACK);
		}
	}
}
