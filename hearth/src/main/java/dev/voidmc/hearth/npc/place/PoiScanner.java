package dev.voidmc.hearth.npc.place;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.npc.entity.FolkSpots;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * Finds the places folk use by looking at the blocks of a built area, for buildings without blueprint POIs: a house
 * a player built, a vanilla village, the dev sandbox. Beds, workstations by trade, chests, chairs (bottom stairs and
 * slabs with room above), tables (a carpet or plate on a post), fireplaces, bells, lecterns, altars and doors.
 *
 * <p>Reads only loaded chunks; callers bound the box (a building, not a town) and budget how often they scan.
 */
public final class PoiScanner {
	/** Larger boxes are clamped round their centre: a scan never reads more than this many blocks. */
	public static final int MAX_VOLUME = 48 * 48 * 24;

	private PoiScanner() {
	}

	public static List<Poi> scan(ServerLevel level, BoundingBox box) {
		box = clamp(box);
		List<Poi> out = new ArrayList<>();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				p.set(x, box.minY(), z);
				if (!level.isLoaded(p)) {
					continue;
				}
				for (int y = box.minY(); y <= box.maxY(); y++) {
					p.setY(y);
					BlockState s = level.getBlockState(p);
					if (s.isAir()) {
						continue;
					}
					Poi poi = classify(level, p.immutable(), s);
					if (poi != null) {
						out.add(poi);
					}
				}
			}
		}
		return out;
	}

	private static @Nullable Poi classify(ServerLevel level, BlockPos pos, BlockState s) {
		Block b = s.getBlock();
		if (b instanceof AbstractBedBlock) {
			if (s.hasProperty(AbstractBedBlock.PART) && s.getValue(AbstractBedBlock.PART) == BedPart.HEAD) {
				return new Poi(PoiType.BED, pos, s.hasProperty(AbstractBedBlock.FACING) ? s.getValue(AbstractBedBlock.FACING) : Direction.NORTH, null);
			}
			return null;
		}
		JobType job = jobOf(b);
		if (job != null) {
			return useFrom(level, pos, PoiType.JOB_SITE, job);
		}
		if (b == Blocks.CHEST || b == Blocks.BARREL || b == Blocks.TRAPPED_CHEST) {
			return useFrom(level, pos, PoiType.STORAGE, null);
		}
		if (b == Blocks.BELL) {
			return useFrom(level, pos, PoiType.MEETING_POINT, null);
		}
		if (b == Blocks.LECTERN) {
			return useFrom(level, pos, PoiType.LECTERN, JobType.SCHOLAR);
		}
		if (b == Blocks.ENCHANTING_TABLE) {
			return useFrom(level, pos, PoiType.ALTAR, JobType.PRIEST);
		}
		if (s.is(BlockTags.CAMPFIRES) || b == Blocks.FURNACE && isIndoorsHearth(level, pos)) {
			return useFrom(level, pos, PoiType.HEARTH, null);
		}
		if (s.is(BlockTags.STAIRS) && s.getValue(StairBlock.HALF) == Half.BOTTOM && seatRoom(level, pos)) {
			// A stair chair: sit facing away from its back.
			return new Poi(PoiType.SEAT, pos, s.getValue(StairBlock.FACING).getOpposite(), null);
		}
		if ((s.is(BlockTags.FENCES) || s.is(BlockTags.WALLS)) && isTableTop(level.getBlockState(pos.above()))) {
			return useFrom(level, pos, PoiType.TABLE, null);
		}
		if (s.is(BlockTags.DOORS) && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
			return outsideOfDoor(level, pos, s);
		}
		return null;
	}

	/** Workstations by trade, the vanilla ones. */
	public static @Nullable JobType jobOf(Block b) {
		if (b == Blocks.SMITHING_TABLE || b == Blocks.ANVIL || b == Blocks.CHIPPED_ANVIL || b == Blocks.BLAST_FURNACE || b == Blocks.GRINDSTONE) {
			return JobType.SMITH;
		}
		if (b == Blocks.SMOKER) {
			return JobType.BAKER;
		}
		if (b == Blocks.CRAFTING_TABLE) {
			return JobType.CARPENTER;
		}
		if (b == Blocks.STONECUTTER) {
			return JobType.MASON;
		}
		if (b == Blocks.LOOM) {
			return JobType.TAILOR;
		}
		if (b == Blocks.BREWING_STAND || b == Blocks.CAULDRON || b == Blocks.WATER_CAULDRON) {
			return JobType.BREWER;
		}
		if (b == Blocks.COMPOSTER) {
			return JobType.FARMER;
		}
		if (b == Blocks.CARTOGRAPHY_TABLE) {
			return JobType.COURIER;
		}
		if (b == Blocks.FLETCHING_TABLE) {
			return JobType.HUNTER;
		}
		return null;
	}

	/** A POI for a block used by standing beside it: the nearest standable side, facing the block. */
	private static @Nullable Poi useFrom(ServerLevel level, BlockPos block, PoiType type, @Nullable JobType job) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos stand = block.relative(d);
			if (FolkSpots.standable(level, stand)) {
				return new Poi(type, stand, d.getOpposite(), job);
			}
			BlockPos down = stand.below();
			if (FolkSpots.standable(level, down)) {
				return new Poi(type, down, d.getOpposite(), job);
			}
		}
		// Blocks used from above (a campfire in a hearth, a bell overhead) or wholly enclosed: stand on top or nearby.
		BlockPos up = block.above();
		return FolkSpots.standable(level, up) ? new Poi(type, up, Direction.NORTH, job) : null;
	}

	private static Poi outsideOfDoor(ServerLevel level, BlockPos door, BlockState s) {
		Direction facing = s.getValue(DoorBlock.FACING);
		BlockPos a = door.relative(facing);
		BlockPos b = door.relative(facing.getOpposite());
		// Outside is the side under open sky.
		boolean aSky = level.canSeeSky(a);
		boolean bSky = level.canSeeSky(b);
		BlockPos outside = aSky || !bSky ? a : b;
		Direction look = outside == a ? facing.getOpposite() : facing;
		return new Poi(PoiType.DOOR, outside, look, null);
	}

	private static boolean seatRoom(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
			&& level.getBlockState(pos.above(2)).getCollisionShape(level, pos.above(2)).isEmpty();
	}

	private static boolean isTableTop(BlockState above) {
		return above.is(BlockTags.WOOL_CARPETS) || above.is(BlockTags.PRESSURE_PLATES) || above.is(Blocks.MOSS_CARPET);
	}

	/** A furnace under a roof with a chimney-ish block above reads as a home's stove. */
	private static boolean isIndoorsHearth(ServerLevel level, BlockPos pos) {
		return !level.canSeeSky(pos.above());
	}

	private static BoundingBox clamp(BoundingBox box) {
		long volume = (long) box.getXSpan() * box.getYSpan() * box.getZSpan();
		if (volume <= MAX_VOLUME) {
			return box;
		}
		BlockPos c = box.getCenter();
		int hx = Math.min(box.getXSpan(), 48) / 2;
		int hz = Math.min(box.getZSpan(), 48) / 2;
		int hy = Math.min(box.getYSpan(), 24) / 2;
		return new BoundingBox(c.getX() - hx, c.getY() - hy, c.getZ() - hz, c.getX() + hx, c.getY() + hy, c.getZ() + hz);
	}
}
