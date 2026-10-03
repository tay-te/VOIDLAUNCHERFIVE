package dev.voidmc.hearth.build.kit.trade;

import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.Rooms;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A floor being furnished by hand: which cells of a rectangle are taken, which must stay free (a door's inside cell, a
 * stair foot, the place a worker stands at a bench) and must stay reachable from the first of them. Trade buildings
 * put their benches, forges and racks exactly where the craft wants them; every piece is tried against the walkway
 * first ({@link #take}), so a workshop never ends up with a bench nobody can reach. When the hand-placed pieces are in,
 * {@link #room} hands the same floor to {@link Rooms#furnish} for the ordinary furniture round them.
 */
public final class Floorplan {
	public final int x0;
	public final int z0;
	public final int x1;
	public final int z1;
	public final int y;
	public final int height;
	private final boolean[] blocked;
	private final boolean[] reserved;
	private final List<int[]> reach = new ArrayList<>();

	public Floorplan(int x0, int z0, int x1, int z1, int y, int height) {
		this.x0 = Math.min(x0, x1);
		this.z0 = Math.min(z0, z1);
		this.x1 = Math.max(x0, x1);
		this.z1 = Math.max(z0, z1);
		this.y = y;
		this.height = height;
		int n = (this.x1 - this.x0 + 1) * (this.z1 - this.z0 + 1);
		this.blocked = new boolean[n];
		this.reserved = new boolean[n];
	}

	public boolean inside(int x, int z) {
		return x >= this.x0 && x <= this.x1 && z >= this.z0 && z <= this.z1;
	}

	private int idx(int x, int z) {
		return (z - this.z0) * (this.x1 - this.x0 + 1) + (x - this.x0);
	}

	/** A cell nothing stands on and nothing is promised to. */
	public boolean free(int x, int z) {
		return inside(x, z) && !this.blocked[idx(x, z)] && !this.reserved[idx(x, z)];
	}

	/** A cell a person can stand in (not taken). */
	public boolean open(int x, int z) {
		return inside(x, z) && !this.blocked[idx(x, z)];
	}

	/** Keeps a cell free and reachable (a door's inside cell, a stair foot). Ignored if the cell is already taken. */
	public Floorplan keep(int x, int z) {
		if (open(x, z)) {
			this.reserved[idx(x, z)] = true;
			this.reach.add(new int[]{x, z});
		}
		return this;
	}

	/** Keeps a rectangle free without requiring it to be reached (a stair run). */
	public Floorplan keepArea(int ax, int az, int bx, int bz) {
		for (int z = Math.min(az, bz); z <= Math.max(az, bz); z++) {
			for (int x = Math.min(ax, bx); x <= Math.max(ax, bx); x++) {
				if (inside(x, z)) {
					this.reserved[idx(x, z)] = true;
				}
			}
		}
		return this;
	}

	/** Marks a cell as taken by something already built (a post, a forge). */
	public Floorplan block(int x, int z) {
		if (inside(x, z)) {
			this.blocked[idx(x, z)] = true;
		}
		return this;
	}

	/** Blocks every cell of the rectangle that the draft already has something standing in at floor level. */
	public Floorplan blockDrawn(Draft d) {
		for (int z = this.z0; z <= this.z1; z++) {
			for (int x = this.x0; x <= this.x1; x++) {
				BlockState s = d.get(x, this.y, z);
				if (s != null && !s.isAir()) {
					block(x, z);
				}
			}
		}
		return this;
	}

	/**
	 * Blocks every cell nobody can stand in: something drawn at floor height, or no floor under it (a stairwell, a loft
	 * opened over the room below). Call once the stairs and holes are cut.
	 */
	public Floorplan blockHoles(Draft d) {
		for (int z = this.z0; z <= this.z1; z++) {
			for (int x = this.x0; x <= this.x1; x++) {
				BlockState s = d.get(x, this.y, z);
				BlockState below = d.get(x, this.y - 1, z);
				boolean hole = below == null ? this.y - 1 >= d.site().ground(x, z) : below.isAir();
				if (s != null && !s.isAir() || hole) {
					block(x, z);
				}
			}
		}
		return this;
	}

	/**
	 * Takes cells for a piece whose user stands at {@code front} (null for a piece nobody uses), if the floor stays
	 * connected: every kept cell, and the front, still reachable from the first kept cell. Answers whether it was taken.
	 */
	public boolean take(int[][] cells, int[] front) {
		for (int[] c : cells) {
			if (!free(c[0], c[1])) {
				return false;
			}
		}
		if (front != null && !open(front[0], front[1])) {
			return false;
		}
		for (int[] c : cells) {
			this.blocked[idx(c[0], c[1])] = true;
		}
		if (front != null) {
			this.reach.add(front);
		}
		if (!connected()) {
			for (int[] c : cells) {
				this.blocked[idx(c[0], c[1])] = false;
			}
			if (front != null) {
				this.reach.remove(this.reach.size() - 1);
			}
			return false;
		}
		if (front != null) {
			this.reserved[idx(front[0], front[1])] = true;
		}
		return true;
	}

	/** One cell, used from the cell beside it toward {@code toward} (null: nobody stands at it). */
	public boolean take(int x, int z, Direction toward) {
		int[] front = toward == null ? null : new int[]{x + Dirs.dx(toward), z + Dirs.dz(toward)};
		if (front != null && !inside(front[0], front[1])) {
			return false;
		}
		return take(new int[][]{{x, z}}, front);
	}

	/** Whether every cell that must be reached can be, from the first. */
	public boolean connected() {
		if (this.reach.isEmpty()) {
			return true;
		}
		boolean[] seen = new boolean[this.blocked.length];
		ArrayDeque<int[]> q = new ArrayDeque<>();
		int[] s = this.reach.get(0);
		if (!open(s[0], s[1])) {
			return false;
		}
		q.add(s);
		seen[idx(s[0], s[1])] = true;
		while (!q.isEmpty()) {
			int[] c = q.poll();
			for (Direction dir : Dirs.HORIZONTAL) {
				int nx = c[0] + Dirs.dx(dir);
				int nz = c[1] + Dirs.dz(dir);
				if (open(nx, nz) && !seen[idx(nx, nz)]) {
					seen[idx(nx, nz)] = true;
					q.add(new int[]{nx, nz});
				}
			}
		}
		for (int[] r : this.reach) {
			if (!inside(r[0], r[1]) || !seen[idx(r[0], r[1])]) {
				return false;
			}
		}
		return true;
	}

	/** The cells that must stay reachable so far. */
	public List<int[]> reachCells() {
		return List.copyOf(this.reach);
	}

	/**
	 * The same floor as a {@link Rooms.Room} for {@link Rooms#furnish}: every cell that must be reached kept, every
	 * reserved cell kept free, so the ordinary furniture fills in round the hand-placed pieces without blocking them.
	 */
	public Rooms.Room room() {
		Rooms.Room r = new Rooms.Room(this.x0, this.z0, this.x1, this.z1, this.y, this.height);
		for (int[] c : this.reach) {
			r.keep(c[0], c[1]);
		}
		for (int z = this.z0; z <= this.z1; z++) {
			for (int x = this.x0; x <= this.x1; x++) {
				if (this.reserved[idx(x, z)]) {
					r.keepArea(x, z, x, z);
				}
				if (this.blocked[idx(x, z)]) {
					r.block(x, z);
				}
			}
		}
		return r;
	}

	/** Free cells against the rectangle's edge on the given side, from one end to the other. */
	public List<int[]> along(Direction wall) {
		List<int[]> out = new ArrayList<>();
		switch (wall) {
			case SOUTH -> {
				for (int x = this.x0; x <= this.x1; x++) {
					out.add(new int[]{x, this.z0});
				}
			}
			case NORTH -> {
				for (int x = this.x0; x <= this.x1; x++) {
					out.add(new int[]{x, this.z1});
				}
			}
			case WEST -> {
				for (int z = this.z0; z <= this.z1; z++) {
					out.add(new int[]{this.x0, z});
				}
			}
			default -> {
				for (int z = this.z0; z <= this.z1; z++) {
					out.add(new int[]{this.x1, z});
				}
			}
		}
		return out;
	}
}
