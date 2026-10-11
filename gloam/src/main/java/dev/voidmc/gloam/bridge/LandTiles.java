package dev.voidmc.gloam.bridge;

import dev.voidmc.gloam.air.Air;
import dev.voidmc.gloam.air.Noise;
import dev.voidmc.gloam.compat.Expanse;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The near land tiles N0 and N1 (16 m cells, 256 x 256 round the camera), filled strip by strip as the camera moves.
 * The window of cells held is {@code centre - 127 .. centre + 128} on both axes; when the camera's cell moves one step
 * the window moves with it and only the strip that came in is worked out (256 cells, a column or a row), and a rolling
 * refresh redoes a few rows a second so the slow parts (how wet the ground is) follow. A jump further than
 * {@link #JUMP} cells (a teleport, a new world) clears the tiles and fills them again, nearest rows first.
 *
 * <p>The work happens on {@link #step}, which a worker thread calls; the strips it makes are queued for the render
 * thread ({@link #poll}), which writes them into the texture together with the centre they belong to, so the texture's
 * header centre always matches its data. Pure apart from the {@link CellSource}, so the rolling is unit-tested.
 */
public final class LandTiles {
	/** How far the camera's cell may move before the tiles are cleared and filled afresh, in cells. */
	static final int JUMP = 64;

	/** Works out one cell's N0 and N1 texels: {@code (long) n1 << 32 | n0}. Called on the worker. */
	@FunctionalInterface
	public interface CellSource {
		long cell(int cx, int cz);
	}

	/**
	 * Cells worked out for the texture: {@code column} a column at world cell x {@code at} running over cells z
	 * {@code from .. from + 255}, else a row at world cell z {@code at} over x {@code from .. from + 255}. {@code clear}
	 * empties both tiles first (and carries no cells). The tiles' centre is ({@code centreX}, {@code centreZ}) once this
	 * is written.
	 */
	public record Strip(boolean clear, boolean column, int at, int from, int[] n0, int[] n1, int centreX, int centreZ) {
	}

	private final ConcurrentLinkedQueue<Strip> done = new ConcurrentLinkedQueue<>();
	/** Rows still to fill after a clear, nearest the centre first (world cell z). */
	private final ArrayDeque<Integer> refill = new ArrayDeque<>();
	private CellSource source;
	private boolean have;
	private int centreX, centreZ;
	private int refreshRow;
	private long cells;

	/**
	 * Does the next piece of work toward a window round ({@code targetX}, {@code targetZ}) from {@code source}: a refill
	 * row, a one-cell step toward the target, or (when {@code refresh}) one row of the rolling refresh. Returns whether
	 * it did anything. A new source (another world) starts over.
	 */
	public boolean step(CellSource source, int targetX, int targetZ, boolean refresh) {
		if (source == null) {
			return false;
		}
		if (source != this.source || !this.have || Math.max(Math.abs(targetX - this.centreX), Math.abs(targetZ - this.centreZ)) > JUMP) {
			this.source = source;
			this.restart(targetX, targetZ);
			return true;
		}
		if (!this.refill.isEmpty()) {
			this.row(this.refill.poll());
			return true;
		}
		if (targetX != this.centreX) {
			int dx = Integer.signum(targetX - this.centreX);
			this.centreX += dx;
			this.column(dx > 0 ? this.centreX + 128 : this.centreX - 127);
			return true;
		}
		if (targetZ != this.centreZ) {
			int dz = Integer.signum(targetZ - this.centreZ);
			this.centreZ += dz;
			this.row(dz > 0 ? this.centreZ + 128 : this.centreZ - 127);
			return true;
		}
		if (refresh) {
			// round the window's rows, so every row is redone every 256 refreshes
			this.refreshRow = (this.refreshRow + 1) & 255;
			this.row(this.centreZ - 127 + this.refreshRow);
			return true;
		}
		return false;
	}

	/** Starts afresh round (cx, cz): a clear, then every row of the window, nearest first. */
	private void restart(int cx, int cz) {
		this.have = true;
		this.centreX = cx;
		this.centreZ = cz;
		this.refill.clear();
		List<Integer> rows = new ArrayList<>(256);
		for (int z = cz - 127; z <= cz + 128; z++) {
			rows.add(z);
		}
		rows.sort(Comparator.comparingInt(z -> Math.abs(z - cz)));
		this.refill.addAll(rows);
		this.done.add(new Strip(true, false, 0, 0, null, null, cx, cz));
	}

	/** Forgets the window (the world went away): the next step starts afresh. */
	public void reset() {
		this.have = false;
		this.source = null;
		this.refill.clear();
	}

	private void column(int x) {
		int from = this.centreZ - 127;
		int[] n0 = new int[256], n1 = new int[256];
		for (int i = 0; i < 256; i++) {
			long c = this.source.cell(x, from + i);
			n0[i] = (int) c;
			n1[i] = (int) (c >>> 32);
		}
		this.cells += 256;
		this.done.add(new Strip(false, true, x, from, n0, n1, this.centreX, this.centreZ));
	}

	private void row(int z) {
		int from = this.centreX - 127;
		int[] n0 = new int[256], n1 = new int[256];
		for (int i = 0; i < 256; i++) {
			long c = this.source.cell(from + i, z);
			n0[i] = (int) c;
			n1[i] = (int) (c >>> 32);
		}
		this.cells += 256;
		this.done.add(new Strip(false, false, z, from, n0, n1, this.centreX, this.centreZ));
	}

	/**
	 * One near-land cell from Expanse's raw land at its centre: ground (the sea's surface out at sea), lowness, water
	 * distance; the mist potential, temperature, humidity, haze kind and flags. Worker thread.
	 */
	public static long encode(Expanse.Raw r, int seaLevel, float wetness) {
		boolean sea = r.coastDistance() < 0;
		boolean water = sea || r.waterDistance() <= 0;
		float ground = sea ? Math.max(r.altitude(), seaLevel) : r.altitude();
		// as Expanse.lowness and Expanse.water, which the air at the camera reads the same land with
		float lowness = Noise.smoothstep(0.0F, 24.0F, r.lowness());
		float near = Math.max(1 - Noise.smoothstep(0.0F, 96.0F, r.waterDistance()), 1 - Noise.smoothstep(0.0F, 96.0F, r.coastDistance()));
		float dry = Noise.smoothstep(0.2F, 0.55F, r.temperature()) * (1 - Noise.smoothstep(0.3F, 0.5F, r.humidity()));
		float potential = Air.mistPotential(lowness, near, r.humidity(), wetness, dry);
		float waterDistance = sea ? 0 : Math.min(r.waterDistance(), Math.max(0, r.coastDistance()));
		int flags = Contract.CELL_PRESENT | Contract.CELL_EXPANSE | (r.temperature() < -0.45F ? Contract.CELL_SNOW : 0) | (water ? Contract.CELL_WATER : 0);
		int n0 = Contract.n0(ground, lowness, waterDistance);
		int n1 = Contract.n1(potential, r.temperature(), r.humidity(), Contract.hazeKind(r.haze()), flags);
		return (long) n1 << 32 | n0 & 0xFFFFFFFFL;
	}

	/** The next finished strip, or null. Render thread. */
	public Strip poll() {
		return this.done.poll();
	}

	/** Whether the tiles are still being filled after a clear. */
	public boolean filling() {
		return !this.refill.isEmpty();
	}

	/** How many cells have been worked out so far. */
	public long cells() {
		return this.cells;
	}

	/**
	 * Writes a strip into the texture's bytes (N0 into tile 0, N1 into tile 1, wrapped), or clears both tiles. Calls
	 * {@code dirty} with each texel line touched: ({@code tile}, {@code column}, the line's index in the tile) or, for a
	 * clear, (tile, false, -1) for the whole tile.
	 */
	public static void write(byte[] bytes, Strip s, Dirty dirty) {
		if (s.clear()) {
			for (int tile : new int[]{Contract.N0, Contract.N1}) {
				for (int z = 0; z < Contract.TILE; z++) {
					int o = Contract.offset(Contract.texelX(tile, 0), Contract.texelY(tile, z));
					java.util.Arrays.fill(bytes, o, o + Contract.TILE * 4, (byte) 0);
				}
				dirty.mark(tile, false, -1);
			}
			return;
		}
		for (int i = 0; i < 256; i++) {
			int cx = s.column() ? s.at() : s.from() + i;
			int cz = s.column() ? s.from() + i : s.at();
			put(bytes, Contract.offset(Contract.texelX(Contract.N0, cx), Contract.texelY(Contract.N0, cz)), s.n0()[i]);
			put(bytes, Contract.offset(Contract.texelX(Contract.N1, cx), Contract.texelY(Contract.N1, cz)), s.n1()[i]);
		}
		int line = s.at() & 255;
		dirty.mark(Contract.N0, s.column(), line);
		dirty.mark(Contract.N1, s.column(), line);
	}

	private static void put(byte[] bytes, int o, int rgba) {
		bytes[o] = (byte) rgba;
		bytes[o + 1] = (byte) (rgba >>> 8);
		bytes[o + 2] = (byte) (rgba >>> 16);
		bytes[o + 3] = (byte) (rgba >>> 24);
	}

	/** Told which lines of a tile changed. */
	@FunctionalInterface
	public interface Dirty {
		void mark(int tile, boolean column, int line);
	}
}
