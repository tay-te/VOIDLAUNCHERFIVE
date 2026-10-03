package dev.voidmc.hearth.npc.dev;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Silhouette;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.api.StepSink;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * Two small buildings for trying builders out without the build module: a timber cottage (low work, a bed, a bench,
 * a chest) and a stone watchtower twelve blocks high (high work: scaffolding). Not part of the game's architecture —
 * the build module's blueprints replace these at integration — but real {@link Blueprint}s that follow the contract:
 * staged, in build order, shapes resolved.
 */
public final class SandboxBlueprints {
	private SandboxBlueprints() {
	}

	public static PlotSpec spec(String kind, BlockPos origin, Direction facing, int size, int height) {
		return new PlotSpec(Hearth.id("dev/" + kind), Hearth.id("dev"), origin.asLong(), origin, size, size, facing, height, 1, new CompoundTag());
	}

	/** A 7×7 cottage with a hipped roof. Front faces north (−z). */
	public static Blueprint cottage(BlockPos origin) {
		PlotSpec spec = spec("cottage", origin, Direction.NORTH, 7, 8);
		Builder b = new Builder(spec);
		int ox = origin.getX();
		int oy = origin.getY();
		int oz = origin.getZ();
		for (int y = 0; y <= 6; y++) {
			for (int x = 0; x < 7; x++) {
				for (int z = 0; z < 7; z++) {
					b.add(StageKind.CLEAR, ox + x, oy + y, oz + z, Blocks.AIR.defaultBlockState());
				}
			}
		}
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 7; z++) {
				b.add(StageKind.FOUNDATION, ox + x, oy - 1, oz + z, x == 0 || z == 0 || x == 6 || z == 6 ? Blocks.COBBLESTONE.defaultBlockState()
					: Blocks.SPRUCE_PLANKS.defaultBlockState());
			}
		}
		for (int y = 0; y < 4; y++) {
			for (int[] c : new int[][]{{0, 0}, {6, 0}, {0, 6}, {6, 6}}) {
				b.add(StageKind.FRAME, ox + c[0], oy + y, oz + c[1], Blocks.OAK_LOG.defaultBlockState());
			}
		}
		for (int y = 0; y < 4; y++) {
			for (int i = 1; i < 6; i++) {
				boolean door = i == 3 && y < 2;
				boolean window = i == 3 && (y == 1 || y == 2);
				BlockState wall = y == 3 ? Blocks.STRIPPED_OAK_LOG.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState();
				if (!door) {
					b.add(StageKind.WALLS, ox + i, oy + y, oz, wall);
				}
				b.add(StageKind.WALLS, ox + i, oy + y, oz + 6, window ? Blocks.AIR.defaultBlockState() : wall);
				b.add(StageKind.WALLS, ox, oy + y, oz + i, window ? Blocks.AIR.defaultBlockState() : wall);
				b.add(StageKind.WALLS, ox + 6, oy + y, oz + i, window ? Blocks.AIR.defaultBlockState() : wall);
			}
		}
		// Hipped roof: three rings of stairs stepping in, planks at the corners, a slab ridge.
		for (int ring = 0; ring < 3; ring++) {
			int y = oy + 4 + ring;
			int lo = ring - 1;
			int hi = 7 - ring;
			for (int i = lo; i <= hi; i++) {
				for (int[] e : new int[][]{{i, lo, 0}, {i, hi, 2}, {lo, i, 3}, {hi, i, 1}}) {
					int x = e[0];
					int z = e[1];
					boolean corner = (x == lo || x == hi) && (z == lo || z == hi);
					BlockState s = corner ? Blocks.SPRUCE_PLANKS.defaultBlockState()
						: Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.from2DDataValue(e[2]));
					b.add(StageKind.ROOF, ox + x, y, oz + z, s);
				}
			}
			// The ceiling under the roof, and the top of the roof.
			if (ring != 1) {
				for (int x = lo + 1; x < hi; x++) {
					for (int z = lo + 1; z < hi; z++) {
						b.add(StageKind.ROOF, ox + x, y, oz + z, ring == 0 ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState());
					}
				}
			}
		}
		b.add(StageKind.ROOF, ox + 3, oy + 7, oz + 3, Blocks.SPRUCE_SLAB.defaultBlockState());
		// Door and windows.
		b.add(StageKind.OPENINGS, ox + 3, oy, oz, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH)
			.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		b.add(StageKind.OPENINGS, ox + 3, oy + 1, oz, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH)
			.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		for (int y = 1; y <= 2; y++) {
			b.add(StageKind.OPENINGS, ox + 3, oy + y, oz + 6, pane(true));
			b.add(StageKind.OPENINGS, ox, oy + y, oz + 3, pane(false));
			b.add(StageKind.OPENINGS, ox + 6, oy + y, oz + 3, pane(false));
		}
		// Bed, bench, chest, table, light.
		b.add(StageKind.INTERIOR, ox + 1, oy, oz + 5, Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH)
			.setValue(BedBlock.PART, BedPart.HEAD));
		b.add(StageKind.INTERIOR, ox + 1, oy, oz + 4, Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH)
			.setValue(BedBlock.PART, BedPart.FOOT));
		b.add(StageKind.INTERIOR, ox + 5, oy, oz + 5, Blocks.CHEST.defaultBlockState());
		b.add(StageKind.INTERIOR, ox + 5, oy, oz + 3, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
		b.add(StageKind.INTERIOR, ox + 4, oy, oz + 3, Blocks.OAK_FENCE.defaultBlockState());
		b.add(StageKind.INTERIOR, ox + 4, oy + 1, oz + 3, Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState());
		b.add(StageKind.INTERIOR, ox + 3, oy + 2, oz + 5, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH));
		b.add(StageKind.EXTERIOR, ox + 3, oy - 1, oz - 1, Blocks.DIRT_PATH.defaultBlockState());
		b.add(StageKind.EXTERIOR, ox + 2, oy, oz - 1, Blocks.POPPY.defaultBlockState());
		b.add(StageKind.EXTERIOR, ox + 4, oy, oz - 1, Blocks.CORNFLOWER.defaultBlockState());
		b.poi(PoiType.BED, new BlockPos(ox + 1, oy, oz + 5), Direction.SOUTH, null);
		b.poi(PoiType.SEAT, new BlockPos(ox + 5, oy, oz + 3), Direction.WEST, null);
		b.poi(PoiType.TABLE, new BlockPos(ox + 3, oy, oz + 3), Direction.EAST, null);
		b.poi(PoiType.STORAGE, new BlockPos(ox + 5, oy, oz + 4), Direction.SOUTH, null);
		b.poi(PoiType.DOOR, new BlockPos(ox + 3, oy, oz - 1), Direction.SOUTH, null);
		b.entrance(new BlockPos(ox + 3, oy, oz - 1));
		return b.build(0x9A7A50, 0x5A4630, oy + 4, oy + 7);
	}

	/** A 5×5 stone watchtower, 12 high with a crenellated top and a ladder inside: work out of reach of the ground. */
	public static Blueprint tower(BlockPos origin) {
		PlotSpec spec = spec("tower", origin, Direction.NORTH, 5, 14);
		Builder b = new Builder(spec);
		int ox = origin.getX();
		int oy = origin.getY();
		int oz = origin.getZ();
		for (int y = 0; y <= 13; y++) {
			for (int x = 0; x < 5; x++) {
				for (int z = 0; z < 5; z++) {
					b.add(StageKind.CLEAR, ox + x, oy + y, oz + z, Blocks.AIR.defaultBlockState());
				}
			}
		}
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				b.add(StageKind.FOUNDATION, ox + x, oy - 1, oz + z, Blocks.STONE_BRICKS.defaultBlockState());
			}
		}
		for (int y = 0; y < 11; y++) {
			for (int x = 0; x < 5; x++) {
				for (int z = 0; z < 5; z++) {
					boolean edge = x == 0 || z == 0 || x == 4 || z == 4;
					boolean door = z == 0 && x == 2 && y < 2;
					boolean slit = (y == 4 || y == 8) && (x == 2 || z == 2) && !(z == 0 && x == 2 && y < 2);
					if (edge && !door) {
						BlockState s = slit ? Blocks.AIR.defaultBlockState()
							: (x == 0 || x == 4) && (z == 0 || z == 4) ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
						if (y % 4 == 3 && !slit && !((x == 0 || x == 4) && (z == 0 || z == 4))) {
							s = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
						}
						b.add(StageKind.WALLS, ox + x, oy + y, oz + z, s);
					}
				}
			}
		}
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				boolean hatch = x == 1 && z == 3;
				b.add(StageKind.ROOF, ox + x, oy + 11, oz + z, hatch ? Blocks.AIR.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState());
				boolean edge = x == 0 || z == 0 || x == 4 || z == 4;
				if (edge && (x + z) % 2 == 0) {
					b.add(StageKind.ROOF, ox + x, oy + 12, oz + z, Blocks.STONE_BRICK_WALL.defaultBlockState());
				}
			}
		}
		b.add(StageKind.OPENINGS, ox + 2, oy, oz, Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH)
			.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		b.add(StageKind.OPENINGS, ox + 2, oy + 1, oz, Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH)
			.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		for (int y = 0; y < 12; y++) {
			b.add(StageKind.INTERIOR, ox + 1, oy + y, oz + 3, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
		}
		b.add(StageKind.EXTERIOR, ox + 2, oy + 12, oz + 2, Blocks.LANTERN.defaultBlockState());
		b.poi(PoiType.DOOR, new BlockPos(ox + 2, oy, oz - 1), Direction.SOUTH, null);
		b.poi(PoiType.GUARD_POST, new BlockPos(ox + 2, oy + 12, oz + 1), Direction.NORTH, JobType.GUARD);
		b.poi(PoiType.VIEWPOINT, new BlockPos(ox + 3, oy + 12, oz + 3), Direction.EAST, null);
		b.entrance(new BlockPos(ox + 2, oy, oz - 1));
		return b.build(0x8A8A86, 0x7A6A50, oy + 11, oy + 13);
	}

	/** No building: a scanned area of the world (a player-built village) the sandbox lives in. */
	public static Blueprint area(BoundingBox box) {
		BlockPos o = new BlockPos(box.minX(), box.minY() + 2, box.minZ());
		PlotSpec spec = new PlotSpec(Hearth.id("dev/area"), Hearth.id("dev"), o.asLong(), o, box.getXSpan(), box.getZSpan(), Direction.NORTH,
			box.getYSpan(), 1, new CompoundTag());
		return new Builder(spec).build(0, 0, box.maxY(), box.maxY());
	}

	private static BlockState pane(boolean eastWest) {
		BlockState s = Blocks.GLASS_PANE.defaultBlockState();
		return eastWest ? s.setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true)
			: s.setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true);
	}

	private record Step(int x, int y, int z, BlockState state) {
	}

	private static final class Builder {
		private final PlotSpec spec;
		private final Map<StageKind, List<Step>> stages = new EnumMap<>(StageKind.class);
		private final List<Poi> pois = new ArrayList<>();
		private final List<BlockPos> entrances = new ArrayList<>();

		Builder(PlotSpec spec) {
			this.spec = spec;
		}

		void add(StageKind stage, int x, int y, int z, BlockState state) {
			this.stages.computeIfAbsent(stage, k -> new ArrayList<>()).add(new Step(x, y, z, state));
		}

		void poi(PoiType type, BlockPos pos, Direction facing, @Nullable JobType job) {
			this.pois.add(new Poi(type, pos, facing, job));
		}

		void entrance(BlockPos pos) {
			this.entrances.add(pos);
		}

		Blueprint build(int wall, int roof, int eave, int ridge) {
			List<StageKind> kinds = new ArrayList<>(this.stages.keySet());
			List<List<Step>> steps = new ArrayList<>();
			BoundingBox box = new BoundingBox(this.spec.origin());
			for (StageKind k : kinds) {
				List<Step> list = this.stages.get(k);
				steps.add(List.copyOf(list));
				for (Step s : list) {
					box.encapsulate(new BlockPos(s.x, s.y, s.z));
				}
			}
			int beds = 0;
			for (Poi p : this.pois) {
				if (p.type() == PoiType.BED) {
					beds++;
				}
			}
			return new ListBlueprint(this.spec, box, kinds, steps, List.copyOf(this.pois), List.copyOf(this.entrances), beds,
				new Silhouette(wall, roof, eave, ridge));
		}
	}

	private record ListBlueprint(PlotSpec spec, BoundingBox bounds, List<StageKind> kinds, List<List<Step>> steps, List<Poi> pois,
		List<BlockPos> entrances, int beds, Silhouette silhouette) implements Blueprint {
		@Override
		public int stageCount() {
			return this.kinds.size();
		}

		@Override
		public StageKind stageKind(int stage) {
			return this.kinds.get(stage);
		}

		@Override
		public int stepCount(int stage) {
			return this.steps.get(stage).size();
		}

		@Override
		public void step(int stage, int index, StepSink sink) {
			Step s = this.steps.get(stage).get(index);
			sink.accept(s.x, s.y, s.z, s.state, null);
		}

		@Override
		public void forEachInChunk(int chunkX, int chunkZ, int fromStage, int toStage, StepSink sink) {
			for (int st = fromStage; st < toStage && st < this.steps.size(); st++) {
				for (Step s : this.steps.get(st)) {
					if (s.x >> 4 == chunkX && s.z >> 4 == chunkZ) {
						sink.accept(s.x, s.y, s.z, s.state, null);
					}
				}
			}
		}

		@Override
		public int work(int stage) {
			int n = 0;
			for (Step s : this.steps.get(stage)) {
				if (!s.state.isAir()) {
					n++;
				}
			}
			return n;
		}

		@Override
		public int[] goods(int stage) {
			return new int[dev.voidmc.hearth.api.Goods.VALUES.length];
		}
	}
}
