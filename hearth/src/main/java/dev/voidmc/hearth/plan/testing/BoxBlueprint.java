package dev.voidmc.hearth.plan.testing;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.Silhouette;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.api.StepSink;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.grid.PlotGeometry;
import dev.voidmc.hearth.plan.road.Polyline;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A plain stand-in blueprint: a box of the plot's footprint and a height within its archetype's range (footings down to
 * the ground, walls, a flat roof), a paved corridor for roads, a deck for bridges, a platform for terraces, tilled land
 * for farms. Enough for the planner's previews and the sim's tests to have real steps, beds and work to count; the build
 * module's blueprints are the real thing.
 */
public final class BoxBlueprint implements Blueprint {
	private static final StageKind[] STAGES = {StageKind.FOUNDATION, StageKind.WALLS, StageKind.ROOF};

	private final PlotSpec spec;
	private final ArchetypeInfo info;
	private final List<List<int[]>> steps = new ArrayList<>();
	private final List<List<BlockState>> states = new ArrayList<>();
	private final BoundingBox bounds;
	private final int height;

	public BoxBlueprint(PlotSpec spec, ArchetypeInfo info, TerrainProbe terrain) {
		this.spec = spec;
		this.info = info;
		for (int i = 0; i < STAGES.length; i++) {
			this.steps.add(new ArrayList<>());
			this.states.add(new ArrayList<>());
		}
		this.height = heightFor(spec, info);
		BlockState wall = wall(info.category());
		BlockState roof = roof(info.category());
		int y0 = spec.floorY();
		var id = spec.archetype();
		if (Archetypes.isLinear(id) || id.equals(Archetypes.WALKWAY)) {
			CompoundTag p = spec.params();
			int[] pts = p.getIntArray("points").orElse(new int[]{spec.origin().getX(), y0, spec.origin().getZ(), spec.origin().getX(), y0, spec.origin().getZ()});
			int width = p.getIntOr("width", 1);
			Polyline line = new Polyline(pts);
			Polyline.Dense d = line.dense();
			boolean deck = id.equals(Archetypes.BRIDGE) || id.equals(Archetypes.WALKWAY) || id.equals(Archetypes.DOCK);
			int deckY = p.getIntOr("deck_y", y0);
			BlockState surface = id.equals(Archetypes.WALL) || id.equals(Archetypes.PALISADE) ? Blocks.STONE_BRICKS.defaultBlockState()
				: deck ? Blocks.SPRUCE_PLANKS.defaultBlockState()
				: "path".equals(p.getStringOr("kind", "")) ? Blocks.DIRT_PATH.defaultBlockState()
				: "highway".equals(p.getStringOr("kind", "")) ? Blocks.GRAVEL.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
			int wallHeight = p.getIntOr("height", 0);
			Polyline.corridor(pts, width, (x, z) -> {
				int y = d.ys()[d.nearest(x, z)];
				if (deck) {
					y = id.equals(Archetypes.DOCK) ? y : deckY;
				}
				add(0, x, y - 1, z, surface);
				for (int k = 0; k < wallHeight; k++) {
					add(1, x, y + k, z, surface);
				}
			});
		} else if (id.equals(Archetypes.TERRACE)) {
			int level = spec.params().getIntOr("level", y0);
			for (int z = spec.origin().getZ(); z < spec.origin().getZ() + spec.sizeZ(); z++) {
				for (int x = spec.origin().getX(); x < spec.origin().getX() + spec.sizeX(); x++) {
					int g = terrain.groundY(x, z);
					for (int y = Math.min(g, level) - 1; y < level; y++) {
						add(0, x, y, z, y == level - 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
					}
					for (int y = level; y < g; y++) {
						add(0, x, y, z, Blocks.AIR.defaultBlockState());
					}
				}
			}
		} else if (info.category() == ArchetypeCategory.FARM || id.equals(Archetypes.PLAZA) || id.equals(Archetypes.GARDEN) || id.equals(Archetypes.PARK)) {
			BlockState top = id.equals(Archetypes.PLAZA) ? Blocks.STONE_BRICKS.defaultBlockState()
				: info.category() == ArchetypeCategory.FARM ? Blocks.FARMLAND.defaultBlockState() : Blocks.MOSS_BLOCK.defaultBlockState();
			BlockState crop = id.equals(Archetypes.ORCHARD) ? Blocks.OAK_LEAVES.defaultBlockState()
				: info.category() == ArchetypeCategory.FARM && !id.equals(Archetypes.PASTURE) ? Blocks.WHEAT.defaultBlockState() : null;
			for (int z = spec.origin().getZ(); z < spec.origin().getZ() + spec.sizeZ(); z++) {
				for (int x = spec.origin().getX(); x < spec.origin().getX() + spec.sizeX(); x++) {
					add(0, x, y0 - 1, z, id.equals(Archetypes.PASTURE) ? Blocks.GRASS_BLOCK.defaultBlockState() : top);
					if (crop != null && (x + z) % 2 == 0) {
						add(1, x, y0, z, crop);
					}
				}
			}
		} else {
			int x0 = spec.origin().getX();
			int z0 = spec.origin().getZ();
			int x1 = x0 + spec.sizeX() - 1;
			int z1 = z0 + spec.sizeZ() - 1;
			boolean stacked = spec.params().getBooleanOr("stacked", false);
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					int g = stacked ? y0 : Math.min(terrain.groundY(x, z), y0);
					for (int y = g - 1; y < y0; y++) {
						add(0, x, y, z, Blocks.COBBLESTONE.defaultBlockState());
					}
					boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
					for (int y = y0; y < y0 + this.height - 1; y++) {
						add(1, x, y, z, edge ? wall : Blocks.AIR.defaultBlockState());
					}
					add(2, x, y0 + this.height - 1, z, roof);
				}
			}
		}
		int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for (List<int[]> stage : this.steps) {
			for (int[] s : stage) {
				for (int k = 0; k < 3; k++) {
					box[k] = Math.min(box[k], s[k]);
					box[k + 3] = Math.max(box[k + 3], s[k]);
				}
			}
		}
		this.bounds = box[0] == Integer.MAX_VALUE ? new BoundingBox(spec.origin()) : new BoundingBox(box[0], box[1], box[2], box[3], box[4], box[5]);
	}

	/** The box's height: within the archetype's range and the plot's cap, chosen by the plot's seed. */
	public static int heightFor(PlotSpec spec, ArchetypeInfo info) {
		int range = Math.max(0, Math.min(spec.maxHeight(), info.maxHeight()) - info.minHeight());
		return Math.max(1, Math.min(spec.maxHeight(), info.minHeight() + (int) Math.floorMod(spec.seed(), range + 1)));
	}

	private void add(int stage, int x, int y, int z, BlockState state) {
		this.steps.get(stage).add(new int[]{x, y, z});
		this.states.get(stage).add(state);
	}

	private static BlockState wall(ArchetypeCategory c) {
		return switch (c) {
			case HOUSING -> Blocks.OAK_PLANKS.defaultBlockState();
			case WORKPLACE -> Blocks.SPRUCE_PLANKS.defaultBlockState();
			case COMMERCE -> Blocks.BIRCH_PLANKS.defaultBlockState();
			case CIVIC, LANDMARK -> Blocks.STONE_BRICKS.defaultBlockState();
			case STORAGE -> Blocks.BARREL.defaultBlockState();
			case DEFENSE -> Blocks.COBBLESTONE.defaultBlockState();
			default -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
		};
	}

	private static BlockState roof(ArchetypeCategory c) {
		return switch (c) {
			case HOUSING -> Blocks.BRICKS.defaultBlockState();
			case CIVIC, LANDMARK -> Blocks.DEEPSLATE_TILES.defaultBlockState();
			case COMMERCE -> Blocks.TERRACOTTA.defaultBlockState();
			default -> Blocks.DARK_OAK_PLANKS.defaultBlockState();
		};
	}

	@Override
	public PlotSpec spec() {
		return this.spec;
	}

	@Override
	public BoundingBox bounds() {
		return this.bounds;
	}

	@Override
	public int stageCount() {
		return STAGES.length;
	}

	@Override
	public StageKind stageKind(int stage) {
		return STAGES[stage];
	}

	@Override
	public int stepCount(int stage) {
		return this.steps.get(stage).size();
	}

	@Override
	public void step(int stage, int index, StepSink sink) {
		int[] s = this.steps.get(stage).get(index);
		sink.accept(s[0], s[1], s[2], this.states.get(stage).get(index), null);
	}

	@Override
	public void forEachInChunk(int chunkX, int chunkZ, int fromStage, int toStage, StepSink sink) {
		for (int stage = fromStage; stage < toStage; stage++) {
			List<int[]> list = this.steps.get(stage);
			for (int i = 0; i < list.size(); i++) {
				int[] s = list.get(i);
				if (s[0] >> 4 == chunkX && s[2] >> 4 == chunkZ) {
					sink.accept(s[0], s[1], s[2], this.states.get(stage).get(i), null);
				}
			}
		}
	}

	@Override
	public int work(int stage) {
		int n = 0;
		for (BlockState s : this.states.get(stage)) {
			if (!s.isAir()) {
				n++;
			}
		}
		return n;
	}

	@Override
	public int[] goods(int stage) {
		int[] g = new int[Goods.VALUES.length];
		g[stage == 0 ? Goods.STONE.ordinal() : Goods.WOOD.ordinal()] = work(stage);
		return g;
	}

	@Override
	public List<Poi> pois() {
		List<Poi> out = new ArrayList<>();
		int[] e = PlotGeometry.entrance(this.spec);
		out.add(new Poi(PoiType.DOOR, new BlockPos(e[0], this.spec.floorY(), e[1]), this.spec.facing(), null));
		BlockPos c = this.spec.origin().offset(this.spec.sizeX() / 2, 0, this.spec.sizeZ() / 2);
		for (int i = 0; i < beds(); i++) {
			out.add(new Poi(PoiType.BED, c, this.spec.facing(), null));
		}
		for (JobType job : this.info.jobs()) {
			out.add(new Poi(PoiType.JOB_SITE, c, this.spec.facing(), job));
		}
		return out;
	}

	@Override
	public List<BlockPos> entrances() {
		int[] e = PlotGeometry.entrance(this.spec);
		return List.of(new BlockPos(e[0], this.spec.floorY(), e[1]));
	}

	@Override
	public int beds() {
		return dev.voidmc.hearth.plan.growth.Needs.estimateBeds(this.info, this.spec);
	}

	@Override
	public Silhouette silhouette() {
		return new Silhouette(0xB08850, 0x8A3A2A, this.spec.floorY() + this.height - 1, this.spec.floorY() + this.height);
	}

	public int height() {
		return this.height;
	}
}
