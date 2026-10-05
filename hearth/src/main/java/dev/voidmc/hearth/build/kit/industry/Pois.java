package dev.voidmc.hearth.build.kit.industry;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Garden;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.jspecify.annotations.Nullable;

/**
 * The places folk use in a works, gathered while it is drawn and registered at the end, when everything that could stand
 * in them has been placed: a job site or a store's user cell that a later heap, post or stack took is quietly dropped
 * rather than left pointing into a block. A yard is drawn piece by piece and its pieces crowd each other; this keeps
 * every place folk are sent to one they can stand in.
 */
public final class Pois {
	private final List<Entry> entries = new ArrayList<>();

	private record Entry(PoiType type, int x, int y, int z, Direction facing, @Nullable JobType job) {
	}

	/** A job site: the cell a worker stands in at (x, y, z), facing the work. */
	public Pois site(int x, int y, int z, Direction facing, JobType job) {
		this.entries.add(new Entry(PoiType.JOB_SITE, x, y, z, facing, job));
		return this;
	}

	/** A job site if its cell is one a person can stand in now (see {@link #standable}); answers whether it was added. */
	public boolean siteIf(Draft d, int x, int y, int z, Direction facing, JobType job) {
		if (!standable(d, x, y, z)) {
			return false;
		}
		site(x, y, z, facing, job);
		return true;
	}

	/** Any point of interest at the cell a person stands in. */
	public Pois poi(PoiType type, int x, int y, int z, Direction facing) {
		this.entries.add(new Entry(type, x, y, z, facing, null));
		return this;
	}

	/** A store (chest or barrel) placed now if its cell and its user's are free; its STORAGE registered at the end. */
	public boolean store(Draft d, int x, int y, int z, Direction user, boolean chest, List<Object[]> items) {
		int ux = x + Dirs.dx(user);
		int uz = z + Dirs.dz(user);
		if (!Works.in(d, x, z) || !Works.in(d, ux, uz) || !d.isEmpty(x, y, z) || !d.isEmpty(ux, y, uz) || !d.isEmpty(ux, y + 1, uz)) {
			return false;
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(x, y, z, States.facing(chest ? Blocks.CHEST : Blocks.BARREL, user), items.isEmpty() ? null
				: dev.voidmc.hearth.build.draft.BlockEntities.container(items));
		}
		this.entries.add(new Entry(PoiType.STORAGE, ux, y, uz, user.getOpposite(), null));
		return true;
	}

	/** A workstation block facing its user, the job site at the user's cell (registered at the end). */
	public boolean station(Draft d, Block block, int x, int y, int z, Direction user, JobType job) {
		if (!Works.in(d, x, z) || !d.isEmpty(x, y, z)) {
			return false;
		}
		d.inStage(StageKind.INTERIOR, () -> d.set(x, y, z, States.facing(block, user)));
		return site(x + Dirs.dx(user), y, z + Dirs.dz(user), user.getOpposite(), job) != null;
	}

	/** An anvil worked from {@code user}'s side. */
	public boolean anvil(Draft d, int x, int y, int z, Direction user, JobType job) {
		if (!Works.in(d, x, z) || !d.isEmpty(x, y, z)) {
			return false;
		}
		d.inStage(StageKind.INTERIOR, () -> d.set(x, y, z, States.facing(Blocks.ANVIL, Dirs.right(user))));
		site(x + Dirs.dx(user), y, z + Dirs.dz(user), user.getOpposite(), job);
		return true;
	}

	/** A grindstone worked from {@code user}'s side. */
	public boolean grindstone(Draft d, int x, int y, int z, Direction user, JobType job) {
		if (!Works.in(d, x, z) || !d.isEmpty(x, y, z)) {
			return false;
		}
		d.inStage(StageKind.INTERIOR, () -> d.set(x, y, z, States.attached(Blocks.GRINDSTONE, AttachFace.FLOOR, Dirs.right(user))));
		site(x + Dirs.dx(user), y, z + Dirs.dz(user), user.getOpposite(), job);
		return true;
	}

	/** How many job sites for a job have been gathered so far. */
	public int count(JobType job) {
		int n = 0;
		for (Entry e : this.entries) {
			if (e.type == PoiType.JOB_SITE && e.job == job) {
				n++;
			}
		}
		return n;
	}

	/**
	 * Registers every gathered place whose cell is free, with headroom and something to stand on. Job types left without
	 * any site get one at {@code fallback} cells in order (the first that is free), so every job a works offers has its
	 * place; a works left with fewer than two job sites gets more round the first fallback cell, and one left without a
	 * store gets a barrel there (the yard's crowding can squeeze them out). Returns how many were registered.
	 */
	public int commit(Draft d, JobType[] jobs, int[]... fallback) {
		int n = 0;
		int sites = 0;
		int stores = 0;
		List<Long> taken = new ArrayList<>();
		List<JobType> have = new ArrayList<>();
		for (Entry e : this.entries) {
			if (!standable(d, e.x, e.y, e.z)) {
				continue;
			}
			d.poi(e.type, e.x, e.y, e.z, e.facing, e.job);
			taken.add(key(e.x, e.y, e.z));
			if (e.job != null) {
				have.add(e.job);
			}
			sites += e.type == PoiType.JOB_SITE ? 1 : 0;
			stores += e.type == PoiType.STORAGE ? 1 : 0;
			n++;
		}
		for (JobType j : jobs) {
			if (have.contains(j)) {
				continue;
			}
			for (int[] f : fallback) {
				if (standable(d, f[0], f[1], f[2]) && !taken.contains(key(f[0], f[1], f[2]))) {
					d.poi(PoiType.JOB_SITE, f[0], f[1], f[2], Dirs.BACK, j);
					taken.add(key(f[0], f[1], f[2]));
					sites++;
					n++;
					break;
				}
			}
		}
		if (fallback.length == 0 || jobs.length == 0) {
			return n;
		}
		int[] a = fallback[0];
		// round the first fallback cell, nearest first: more standing places, then a barrel against something solid
		for (int r = 1; r <= 6 && (sites < 2 || stores < 1); r++) {
			for (int dz = -r; dz <= r && (sites < 2 || stores < 1); dz++) {
				for (int dx = -r; dx <= r && (sites < 2 || stores < 1); dx++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					int x = a[0] + dx;
					int z = a[2] + dz;
					int y = a[1];
					if (!standable(d, x, y, z) || taken.contains(key(x, y, z)) || Works.atDoor(d, x, z, 0)) {
						continue;
					}
					if (sites < 2) {
						d.poi(PoiType.JOB_SITE, x, y, z, Dirs.BACK, jobs[sites % jobs.length]);
						taken.add(key(x, y, z));
						sites++;
						n++;
						continue;
					}
					for (Direction toward : Direction.Plane.HORIZONTAL) {
						int bx = x + Dirs.dx(toward);
						int bz = z + Dirs.dz(toward);
						boolean backed = !Works.in(d, bx + Dirs.dx(toward), bz + Dirs.dz(toward)) || d.isSolid(bx + Dirs.dx(toward), y, bz + Dirs.dz(toward));
						if (backed && !taken.contains(key(bx, y, bz)) && !Works.atDoor(d, bx, bz, 1) && standable(d, bx, y, bz)) {
							try (var s = d.stage(StageKind.INTERIOR)) {
								d.set(bx, y, bz, States.facing(Blocks.BARREL, toward.getOpposite()));
							}
							d.poi(PoiType.STORAGE, x, y, z, toward, null);
							taken.add(key(x, y, z));
							taken.add(key(bx, y, bz));
							stores++;
							n++;
							break;
						}
					}
				}
			}
		}
		return n;
	}

	private static long key(int x, int y, int z) {
		return net.minecraft.core.BlockPos.asLong(x, y, z);
	}

	/** Whether a person can stand at (x, y, z): the cell and the one above free, something solid or ground below. */
	public static boolean standable(Draft d, int x, int y, int z) {
		if (!Works.in(d, x, z) || !d.isEmpty(x, y, z) || !d.isEmpty(x, y + 1, z)) {
			return false;
		}
		int g = d.site().ground(x, z);
		if (d.get(x, y, z) == null && y < g || d.get(x, y + 1, z) == null && y + 1 < g) {
			// inside the natural ground
			return false;
		}
		var below = d.get(x, y - 1, z);
		if (below == null) {
			return y == g;
		}
		return !below.isAir() && !below.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO).isEmpty()
			|| Garden.groundLike(d, x, y - 1, z);
	}
}
