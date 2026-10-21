package dev.voidmc.hearth.npc.work.field;

import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.place.ResidentPlaces;
import dev.voidmc.hearth.npc.routine.Activity;
import dev.voidmc.hearth.npc.routine.Schedule;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Where a remote worker who is only a record is in working hours (docs/LIVING_WORLD_2.md §6.3): on the track between home
 * (or the pen's gate) and the site while the way out or home takes, out on the meadow with the flock while it grazes.
 * {@code RoutinePlacement} asks here first, so a folk appears where the books say: a drove 200 blocks out on the meadow at
 * 10:00, miners strung out on the track at 06:30.
 */
public final class FieldPlacement {
	private FieldPlacement() {
	}

	/** The worker's place now, or null to leave it to the routine (not a remote worker, not their hours, at the face). */
	public static @Nullable BlockPos where(ServerLevel level, Settlement s, Resident r, ResidentPlaces p, int minute, Schedule.Block block) {
		if (block.activity() != Activity.WORK || !FieldWork.config().sites().enabled()) {
			return null;
		}
		RemoteSites.Site site = FieldSites.siteOf(s, r);
		if (site == null) {
			return null;
		}
		int[] w = site.window();
		if (w == null) {
			return null;
		}
		BlockPos from = from(site, p);
		BlockPos to = site.work();
		boolean far = Commute.distance(from, to) >= FieldWork.config().sites().commuteFrom();
		Commute.Where at = Commute.at(minute, w[0], w[1], FieldSites.travelMinutes(from, to));
		return switch (at.phase()) {
			case OUT, BACK -> far ? ground(level, Commute.along(from, to, at.fraction())) : null;
			// the flock's meadow; the miners are at their faces, which the routine finds
			case AT -> site.kind() == RemoteSites.Kind.PASTURE ? spread(level, site, r) : null;
			case NONE -> null;
		};
	}

	/** Where a worker sets out from: the pen's gate for a flock, their own door otherwise. */
	public static BlockPos from(RemoteSites.Site site, ResidentPlaces p) {
		if (site.from() != null) {
			return site.from();
		}
		return p.homeDoor != null ? p.homeDoor.pos() : p.home;
	}

	/** A herder's own spot on the meadow (two herders of one flock stand apart). */
	private static BlockPos spread(ServerLevel level, RemoteSites.Site site, Resident r) {
		long h = r.id.getLeastSignificantBits() * 0x9E3779B97F4A7C15L;
		int dx = (int) Math.floorMod(h, 9L) - 4;
		int dz = (int) Math.floorMod(h >>> 20, 9L) - 4;
		return ground(level, site.work().offset(dx, 0, dz));
	}

	/** The ground at a point where its chunk is in (else the point as given: the record's guess). */
	public static BlockPos ground(ServerLevel level, BlockPos p) {
		if (!level.isLoaded(p)) {
			return p;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ());
		return new BlockPos(p.getX(), y, p.getZ());
	}
}
