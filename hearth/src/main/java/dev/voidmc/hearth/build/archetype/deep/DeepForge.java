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
import dev.voidmc.hearth.build.draft.BlockEntities;
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
import dev.voidmc.hearth.build.kit.trade.Forge;
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
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:deep/forge}: the deep town's forge by a vent of the realm's fire, where the ore from the mines is
 * smelted and worked (LIVING_WORLD.md §8.2). A smithy hall of rough, soot-dark stone open to the street under a great
 * timber beam on stone corbels; inside, set in the floor at the back, a pool of lava fenced with iron bars and rimmed
 * with magma (the vent the forge was built on), beside it the smelting furnace — blast furnaces between stone cheeks
 * under a hood, its broad stack rising through the roof — and down the hall the smith's anvils, grindstone and smithing
 * table, a quench trough, a crucible of lava, ore heaped in bins against the side wall with a rail line running in to
 * them from the street, coal, tools on the racks, chests. A gable roof with a louvre along its ridge lets the heat out.
 *
 * <p>Params: {@code realm}, {@code ceiling_y}, {@code character}, {@code local} ({@link DeepSite}); {@code variant}.
 */
public final class DeepForge implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("deep/forge"), ArchetypeCategory.UNDERGROUND, Set.of(JobType.SMITH, JobType.SMELTER),
		0, 9, 13, 9, 13, 8, 14, SettlementTier.VILLAGE, Set.of(), Set.of(), List.of(), 0, 1.0);

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
		int h = Math.max(8, Math.min(d.maxHeight(), ds.top() + 1));
		int wallTop = Math.min(5, h - 4);
		int zf = 1;
		int zb = dp - 1;
		d.body(0, zf, w - 1, zb);
		d.eave(wallTop + 1);

		// the yard and the open front's row levelled together: a cut into rising ground is retained inside the walls (where
		// the footings dig it out), not across the open front
		Groundwork.level(d, 0, 0, w - 1, zf, 0, true);
		Groundwork.footings(d, 0, zf, w - 1, zb, -1);
		Mat rough = d.mat(Role.FOUNDATION);
		Mat wall = d.mat(Role.WALL);
		Mat plinth = d.mat(Role.PLINTH);
		Mat dress = Rock.dressing(d);
		Mat paving = d.mat(Role.PAVING);
		Mat beam = d.mat(Role.BEAM);

		// the shell: rough stone walls (dressed quoins), a paved floor, the front open under a beam
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = zf; z <= zb; z++) {
				for (int x = 0; x < w; x++) {
					boolean side = x == 0 || x == w - 1;
					boolean back = z == zb;
					boolean front = z == zf;
					if (!(side || back || front)) {
						continue;
					}
					boolean corner = side && (front || back);
					for (int y = -1; y <= wallTop; y++) {
						if (front && !side && y < wallTop) {
							continue;
						}
						Paint p = y == -1 ? plinth.full() : corner && (y & 1) == 0 ? dress.full() : y >= wallTop ? wall.full() : rough.full();
						d.set(x, y, z, p);
					}
				}
			}
			// the front's great beam on corbels
			for (int x = 1; x < w - 1; x++) {
				d.set(x, wallTop - 1, zf, beam.log(Direction.Axis.X));
			}
			d.set(1, wallTop - 2, zf, dress.stairsOrSlab(Dirs.LEFT, Half.TOP));
			d.set(w - 2, wallTop - 2, zf, dress.stairsOrSlab(Dirs.RIGHT, Half.TOP));
		}
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = 0; z < zb; z++) {
				for (int x = 1; x < w - 1; x++) {
					d.set(x, -1, z, paving.full());
				}
			}
			d.set(0, -1, 0, paving.full());
			d.set(w - 1, -1, 0, paving.full());
		}
		d.carve(1, 0, zf, w - 2, wallTop - 2, zb - 1);
		d.carve(1, wallTop - 1, zf + 1, w - 2, wallTop, zb - 1);

		// the roof by the culture (art review: not one deepslate bunker in fourteen towns): a steep gable with its frame
		// showing in the gables where the north and Bohemia build in timber, a flared hip on wide eaves in the karst and the
		// wet south, a flat deck behind a parapet in the dry lands; the culture's own covering, tie beams across
		int cx = w / 2;
		DeepManner manner = DeepManner.of(d);
		String form = switch (manner.culture()) {
			case KARST, WISTERIA, TROPICAL, BAYOU -> "hip";
			case DESERT, MESA, STEPPE, MEDITERRANEAN -> "flat";
			default -> "gable";
		};
		Mat roofM = d.mat(Role.ROOF);
		Mat trim = d.mat(Role.ROOF_TRIM);
		Roofs.Roof roof = null;
		if (form.equals("flat")) {
			flat(d, w, zf, zb, wallTop);
		} else {
			double pitch = Math.min(form.equals("gable") ? 1.0 : 0.75, (h - wallTop - 3) / ((w + 1) / 2.0));
			Roofs.Field f = form.equals("gable") ? Roofs.gable(0, zf, w - 1, zb, Direction.Axis.Z, Roofs.Profile.straight(Math.max(0.5, pitch)))
				: Roofs.flared(Roofs.hip(0, zf, w - 1, zb, Roofs.Profile.curved(Math.max(0.5, pitch))), 0, zf, w - 1, zb, 1.0, 2);
			roof = Roofs.raster(d, f, wallTop + 1, 0, zf, w - 1, zb, roofM, trim, Habits.Ridge.TRIM);
			if (form.equals("gable")) {
				gables(d, w, zf, zb, wallTop, roof, wall);
			}
			DeepManner.rehome(d, 0, wallTop + 1, 0, w - 1, d.maxHeight() - 1, dp - 1, roofM, Role.ROOF);
		}
		try (var s = d.stage(StageKind.FRAME)) {
			for (int z = zf + 2; z < zb; z += 3) {
				for (int x = 1; x < w - 1; x++) {
					d.set(x, wallTop, z, beam.log(Direction.Axis.X));
				}
			}
		}
		if (roof != null) {
			louvre(d, cx, zf + 2, zb - 2, roof, h);
		}
		// vents high in the side walls between the tie beams, to let the heat and the smoke out
		try (var s = d.stage(StageKind.OPENINGS)) {
			for (int z = zf + 3; z < zb - 1; z += 3) {
				for (int x : new int[]{0, w - 1}) {
					d.set(x, wallTop - 1, z, Blocks.IRON_BARS.defaultBlockState());
				}
			}
		}

		// the vent: a pool of lava in the floor at a back corner, rimmed in magma, fenced with bars
		int px = w >= 11 ? 2 : 1;
		int vz = zb - 2;
		vent(d, px, vz, w);
		// the smelting furnace against the back wall: blast furnaces between cheeks under a hood, the stack
		int fx = cx;
		Mat cheek = d.mat(Role.CHIMNEY);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = fx - 1; x <= fx + 2; x++) {
				d.set(x, 2, zb - 1, cheek.full());
			}
			d.set(fx - 1, 0, zb - 1, cheek.full());
			d.set(fx - 1, 1, zb - 1, cheek.full());
			d.set(fx + 2, 0, zb - 1, cheek.full());
			d.set(fx + 2, 1, zb - 1, cheek.full());
			for (int x = fx - 1; x <= fx + 2; x++) {
				d.set(x, 3, zb - 2, cheek.stairsOrSlab(Dirs.BACK, Half.TOP));
			}
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(fx, 0, zb - 1, States.facing(Blocks.BLAST_FURNACE, Dirs.FRONT).trySetValue(BlockStateProperties.LIT, true));
			d.set(fx + 1, 0, zb - 1, States.facing(Blocks.BLAST_FURNACE, Dirs.FRONT).trySetValue(BlockStateProperties.LIT, true));
			d.set(fx, 1, zb - 1, States.facing(Blocks.FURNACE, Dirs.FRONT).trySetValue(BlockStateProperties.LIT, true));
			d.set(fx + 1, 1, zb - 1, Blocks.LAVA_CAULDRON.defaultBlockState());
		}
		d.poi(PoiType.JOB_SITE, fx, 0, zb - 2, Dirs.BACK, JobType.SMELTER);
		d.poi(PoiType.JOB_SITE, fx + 1, 0, zb - 2, Dirs.BACK, JobType.SMELTER);
		Forge.stack(d, new int[][]{{fx, zb}, {fx + 1, zb}, {fx, zb - 1}, {fx + 1, zb - 1}}, 3, form.equals("flat") ? wallTop + 4 : h - 1, roof);
		// the smith's place: anvils, the smithing table (his job site), a grindstone, the quench trough
		int sz = zf + 2;
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(cx - 1, 0, sz + 1, States.facing(Blocks.ANVIL, Dirs.RIGHT));
			d.set(cx + 1, 0, sz + 1, States.facing(Blocks.CHIPPED_ANVIL, Dirs.FRONT));
			d.set(w - 2, 0, sz, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
			d.set(w - 2, 0, sz + 2, States.attached(Blocks.GRINDSTONE, AttachFace.FLOOR, Dirs.FRONT));
		}
		Furniture.workstation(d, JobType.SMITH, w - 2, 0, sz + 1, Dirs.LEFT);
		// the striker's place between the anvils
		d.poi(PoiType.JOB_SITE, cx, 0, sz + 1, Dirs.RIGHT, JobType.SMITH);
		// ore in bins along the left wall, the rail line in from the street to them
		ore(d, 1, zf + 1, vz - 2, g);
		rails(d, 2, 0, Math.max(zf + 2, vz - 3));
		// stores and racks along the right wall behind the smith
		Furniture.chest(d, w - 2, 0, Math.min(zb - 2, sz + 4), Dirs.LEFT, List.of(new Object[]{"minecraft:iron_ingot", 16},
			new Object[]{"minecraft:coal", 24}, new Object[]{"minecraft:raw_iron", 12}));
		Block shelf = d.mat(Role.FURNITURE).main(Form.SHELF);
		if (shelf != null) {
			try (var s = d.stage(StageKind.INTERIOR)) {
				d.set(w - 2, 2, sz + 3, States.facing(shelf, Dirs.LEFT), BlockEntities.shelf(List.of(new Object[]{"minecraft:iron_pickaxe", 1, 0},
					new Object[]{"minecraft:iron_sword", 1, 1}, new Object[]{"minecraft:shears", 1, 2})));
			}
		}
		// light: lanterns from the tie beams, the furnace's glow, the vent's
		for (int z = zf + 2; z < zb; z += 3) {
			Lighting.chandelier(d, cx, z, wallTop, wallTop - 1);
		}
		Lighting.room(d, 1, zf + 1, w - 2, zb - 1, 0);
		// the yard before the front: a lamp post, a block of stone for cold work, a water butt
		if (d.isAbsent(0, 0, 0)) {
			Rock.lampPost(d, 0, 0, 0, false, g);
		}
		d.entrance(cx, 0, -1);
		d.poi(PoiType.DOOR, cx, 0, 0, Dirs.BACK);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, h));
	}

	/**
	 * The gables of a timber culture's forge: the frame showing over the stone walls (a collar beam under the ridge, posts
	 * at the quarters, the culture's own infill between), the gable's foot a beam on the wall head.
	 */
	private static void gables(Draft d, int w, int zf, int zb, int wallTop, Roofs.Roof roof, Mat wall) {
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Paint infill = DeepManner.home(d, Role.WALL).orElse(wall.full());
		int cx = w / 2;
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z : new int[]{zf, zb}) {
				for (int x = 1; x < w - 1; x++) {
					int under = roof.bottomY(x, z);
					for (int y = wallTop + 1; y < under; y++) {
						boolean post = x == cx || x == (1 + cx) / 2 || x == (cx + w - 2 + 1) / 2;
						d.set(x, y, z, y == wallTop + 1 ? beam.log(Direction.Axis.X) : post ? frame.log(Direction.Axis.Y) : infill);
					}
				}
			}
		}
	}

	/**
	 * The dry lands' forge roof: a deck of the roofing on the walls' heads, a parapet round it of the walls' stone
	 * capped with a slab (too high to step over), water spouts of beam ends through it at the sides.
	 */
	private static void flat(Draft d, int w, int zf, int zb, int wallTop) {
		Paint deck = DeepManner.home(d, Role.ROOF).orElse(d.mat(Role.ROOF).full());
		Paint render = DeepManner.home(d, Role.WALL).orElse(d.mat(Role.WALL).full());
		Mat dress = Rock.dressing(d);
		Mat beam = d.mat(Role.BEAM);
		int y = wallTop + 1;
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = zf; z <= zb; z++) {
				for (int x = 0; x < w; x++) {
					boolean edge = x == 0 || x == w - 1 || z == zf || z == zb;
					d.set(x, y, z, edge ? render : deck);
					if (edge && y + 1 < d.maxHeight()) {
						d.set(x, y + 1, z, render);
						if (y + 2 < d.maxHeight()) {
							d.set(x, y + 2, z, dress.slab(SlabType.BOTTOM));
						}
					}
				}
			}
		}
		try (var s = d.stage(StageKind.FRAME)) {
			for (int z = zf + 2; z < zb; z += 3) {
				d.set(0, y, z, beam.log(Direction.Axis.X));
				d.set(w - 1, y, z, beam.log(Direction.Axis.X));
			}
		}
	}

	/** A louvre along the ridge: a little roof of slabs raised on posts over a slot in the ridge, to let the heat out. */
	private static void louvre(Draft d, int x, int z0, int z1, Roofs.Roof roof, int h) {
		Mat post = d.mat(Role.FRAME);
		Mat trim = d.mat(Role.ROOF_TRIM);
		int y = roof.ridgeY;
		if (y + 2 >= h || z1 - z0 < 2) {
			return;
		}
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = z0; z <= z1; z++) {
				boolean end = z == z0 || z == z1;
				if (end) {
					d.set(x, y + 1, z, post.fence());
				}
				d.set(x, y + 2, z, trim.has(Form.SLAB) ? trim.slab(SlabType.BOTTOM) : d.mat(Role.ROOF).slab(SlabType.BOTTOM));
			}
		}
	}

	/**
	 * The vent: a pool of lava two by two sunk in the floor (stone under it, the floor's blocks round it), magma
	 * round the pool, iron bars fencing it at the floor, a dressed kerb.
	 */
	private static void vent(Draft d, int x0, int z0, int w) {
		Mat plinth = d.mat(Role.PLINTH);
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int x = x0 - 1; x <= x0 + 2; x++) {
				for (int z = z0 - 1; z <= z0 + 1; z++) {
					boolean pool = x >= x0 && x <= x0 + 1 && z >= z0 && z <= z0;
					if (x < 1 || x > w - 2) {
						continue;
					}
					d.set(x, -2, z, plinth.full());
					d.set(x, -1, z, pool ? Blocks.LAVA.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState());
					if (!pool) {
						d.set(x, 0, z, Blocks.IRON_BARS.defaultBlockState());
					}
				}
			}
		}
	}

	/** Ore in bins along a wall: raw iron, copper and gold heaped, coal, barrels of ore, the realm's mineral. */
	private static void ore(Draft d, int x, int z0, int z1, Glow g) {
		BlockState[] heap = {Blocks.RAW_IRON_BLOCK.defaultBlockState(), Blocks.COAL_BLOCK.defaultBlockState(), Blocks.RAW_COPPER_BLOCK.defaultBlockState(),
			g.mineral.defaultBlockState(), Blocks.RAW_GOLD_BLOCK.defaultBlockState()};
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int z = z0, i = 0; z <= z1; z++, i++) {
				BlockState b = heap[i % heap.length];
				d.set(x, 0, z, b);
				if (Noise.unit(d.seed() ^ 0x0E, x, 1, z) < 0.5) {
					d.set(x, 1, z, i % 2 == 0 ? States.facing(Blocks.BARREL, Direction.UP) : heap[(i + 1) % heap.length]);
				}
			}
		}
	}

	/** A rail line from the street in to the ore bins, ending at a buffer. */
	private static void rails(Draft d, int x, int z0, int z1) {
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int z = z0; z <= z1; z++) {
				d.set(x, 0, z, Blocks.RAIL.defaultBlockState().setValue(BlockStateProperties.RAIL_SHAPE, RailShape.NORTH_SOUTH));
			}
			if (d.isAbsent(x, 0, z1 + 1)) {
				d.set(x, 0, z1 + 1, Blocks.HAY_BLOCK.defaultBlockState());
			}
		}
	}
}
