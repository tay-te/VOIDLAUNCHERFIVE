package dev.voidmc.hearth.build.kit.age;

import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.build.blueprint.LocalFrame;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * A finished blueprint read back into its plot's local frame (front at z = 0, states turned back to the frame's labels,
 * stages kept), for passes that dress a building without touching its steps: where its doors, windows, wall faces,
 * eaves and roof tops are, and which cells it leaves free.
 */
public final class BuiltView {
	private final PlotSpec spec;
	private final LocalFrame frame;
	private final Rotation back;
	private final Long2ObjectOpenHashMap<BlockState> cells = new Long2ObjectOpenHashMap<>();
	private final Long2ObjectOpenHashMap<StageKind> stages = new Long2ObjectOpenHashMap<>();
	private final List<int[]> entrances = new ArrayList<>();

	/** The ground round the building, when the caller knows it (else only what the building lays counts as ground). */
	private @Nullable TerrainProbe probe;

	/** Lets the view see the natural ground (the yard the building leaves as it lies). */
	public BuiltView ground(@Nullable TerrainProbe p) {
		this.probe = p;
		return this;
	}

	/** Whether a cell the building leaves alone is natural ground (only known with a probe). */
	public boolean natural(int x, int y, int z) {
		if (this.probe == null || get(x, y, z) != null) {
			return false;
		}
		int wx = this.frame.worldX(x, z);
		int wz = this.frame.worldZ(x, z);
		return y + this.spec.floorY() < this.probe.groundY(wx, wz);
	}

	private boolean wet(int x, int y, int z) {
		if (this.probe == null) {
			return false;
		}
		int wx = this.frame.worldX(x, z);
		int wz = this.frame.worldZ(x, z);
		int wy = this.probe.waterY(wx, wz);
		return wy != TerrainProbe.NO_WATER && y + this.spec.floorY() < wy;
	}

	public BuiltView(PlotSpec spec, Blueprint bp) {
		this.spec = spec;
		this.frame = new LocalFrame(spec);
		this.back = switch (this.frame.rotation()) {
			case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
			case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
			default -> this.frame.rotation();
		};
		int fy = spec.floorY();
		for (int s = 0; s < bp.stageCount(); s++) {
			StageKind k = bp.stageKind(s);
			for (int i = 0; i < bp.stepCount(s); i++) {
				bp.step(s, i, (x, y, z, state, tag) -> {
					long key = Draft.key(this.frame.localX(x, z), y - fy, this.frame.localZ(x, z));
					this.cells.put(key, this.back == Rotation.NONE ? state : state.rotate(this.back));
					this.stages.put(key, k);
				});
			}
		}
		for (BlockPos e : bp.entrances()) {
			this.entrances.add(new int[]{this.frame.localX(e.getX(), e.getZ()), e.getY() - fy, this.frame.localZ(e.getX(), e.getZ())});
		}
	}

	public PlotSpec spec() {
		return this.spec;
	}

	public LocalFrame frame() {
		return this.frame;
	}

	public int frontage() {
		return this.frame.frontage();
	}

	public int depth() {
		return this.frame.depth();
	}

	public int maxHeight() {
		return this.spec.maxHeight();
	}

	public List<int[]> entrances() {
		return this.entrances;
	}

	/** The building's block at a local cell (null where it places nothing). */
	public @Nullable BlockState get(int x, int y, int z) {
		return this.cells.get(Draft.key(x, y, z));
	}

	public @Nullable StageKind stage(int x, int y, int z) {
		return this.stages.get(Draft.key(x, y, z));
	}

	/** Every cell the building places something at (local keys, {@link Draft#key}). */
	public Iterable<Long> keys() {
		return this.cells.keySet();
	}

	/** Whether something may be put at a cell by the plot's rules: in the footprint and height, or over the street at 3 up. */
	public boolean allowed(int x, int y, int z) {
		if (x < 0 || x >= frontage() || z >= depth() || z < -1 || y >= maxHeight()) {
			return false;
		}
		return z >= 0 || y >= 3;
	}

	/**
	 * Whether a dressing may take a cell: allowed by the plot, and the building leaves it air (an air step, or nothing at a
	 * cell above the floor that stands on or against something of the building's).
	 */
	public boolean free(int x, int y, int z) {
		if (!allowed(x, y, z)) {
			return false;
		}
		BlockState s = get(x, y, z);
		if (s != null) {
			return s.isAir();
		}
		if (this.probe != null) {
			return !natural(x, y, z) && !wet(x, y, z);
		}
		if (y < 0) {
			return false;
		}
		// nothing drawn: only where it is plainly open air (something of the building's below or beside it)
		BlockState below = get(x, y - 1, z);
		return below != null && !below.isAir() || y >= 1;
	}

	public boolean solid(int x, int y, int z) {
		BlockState s = get(x, y, z);
		return s != null && s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) && !(s.getBlock() instanceof LeavesBlock);
	}

	/** A block whose top is whole (things can lie on it): full blocks, top slabs, upside-down stairs. */
	public boolean sturdyTop(int x, int y, int z) {
		if (natural(x, y, z)) {
			return true;
		}
		BlockState s = get(x, y, z);
		if (s == null || s.isAir() || s.getBlock() instanceof LeavesBlock) {
			return false;
		}
		if (s.getBlock() instanceof SlabBlock) {
			return s.getValue(SlabBlock.TYPE) != SlabType.BOTTOM;
		}
		if (s.getBlock() instanceof StairBlock) {
			return s.getValue(StairBlock.HALF) == Half.TOP;
		}
		return s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
	}

	/** Whether a cell holds a wall of the building (a full block laid in the walls or frame stage). */
	public boolean wall(int x, int y, int z) {
		StageKind k = stage(x, y, z);
		return solid(x, y, z) && k != StageKind.CLEAR && k != StageKind.EARTHWORK && k != StageKind.EXTERIOR;
	}

	public boolean glass(int x, int y, int z) {
		BlockState s = get(x, y, z);
		return s != null && net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().contains("glass");
	}

	/** A door of the building: its lower half and the way it opens onto (the side its entrance is on). */
	public record Door(int x, int y, int z, Direction out, Block block) {
	}

	/** The building's doors, the one nearest the main entrance first. */
	public List<Door> doors() {
		List<Door> out = new ArrayList<>();
		for (var e : this.cells.long2ObjectEntrySet()) {
			BlockState s = e.getValue();
			if (s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
				int x = Draft.keyX(e.getLongKey());
				int y = Draft.keyY(e.getLongKey());
				int z = Draft.keyZ(e.getLongKey());
				out.add(new Door(x, y, z, outward(x, y, z, s), s.getBlock()));
			}
		}
		int[] main = this.entrances.isEmpty() ? new int[]{frontage() / 2, 0, -1} : this.entrances.get(0);
		out.sort((a, b) -> {
			int da = Math.abs(a.x - main[0]) + Math.abs(a.z - main[2]) + Math.abs(a.y - main[1]);
			int db = Math.abs(b.x - main[0]) + Math.abs(b.z - main[2]) + Math.abs(b.y - main[1]);
			return da != db ? Integer.compare(da, db) : a.x != b.x ? Integer.compare(a.x, b.x) : a.z != b.z ? Integer.compare(a.z, b.z) : Integer.compare(a.y, b.y);
		});
		return out;
	}

	/** The side of a door that is outside: toward the nearest entrance, else toward the street. */
	private Direction outward(int x, int y, int z, BlockState door) {
		Direction f = door.getValue(DoorBlock.FACING);
		Direction best = Dirs.FRONT;
		int bestD = Integer.MAX_VALUE;
		for (int[] e : this.entrances) {
			int d = Math.abs(e[0] - x) + Math.abs(e[2] - z);
			if (d < bestD) {
				bestD = d;
				best = Math.abs(e[0] - x) > Math.abs(e[2] - z) ? (e[0] > x ? Dirs.RIGHT : Dirs.LEFT) : (e[2] > z ? Dirs.BACK : Dirs.FRONT);
			}
		}
		if (bestD > 6) {
			// no entrance near it: the door's own facing, toward whichever side is open
			return free(x + Dirs.dx(f), y, z + Dirs.dz(f)) ? f : f.getOpposite();
		}
		return best;
	}

	/** The local state turned to the world, for a step. */
	public BlockState toWorld(BlockState local) {
		return this.frame.toWorld(local);
	}

	public BlockPos toWorld(int x, int y, int z) {
		return this.frame.toWorld(x, y, z);
	}
}
