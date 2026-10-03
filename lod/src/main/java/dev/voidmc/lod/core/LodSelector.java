package dev.voidmc.lod.core;

/**
 * Chooses which tiles to draw this frame: a quadtree walk that splits a tile while its cells would look
 * bigger on screen than the detail budget allows, the screen-space-error rule. A cell of {@code c} blocks
 * at distance {@code d} covers about {@code c * scale / d} pixels, where {@code scale} is the projection's
 * pixels per unit at distance one; a tile splits while that exceeds {@code pixelsPerCell}. The detail
 * follows what the eye can see instead of fixed rings, so a mountain face close by gets one-block cells
 * and the horizon gets 512-block ones, and the work per frame stays roughly constant however far the
 * view reaches.
 *
 * <p>While tiles load, the walk never leaves a hole it can fill: if any child of a split tile is not ready,
 * it falls back to the tile itself when that is ready (dropping the children it had already chosen, so
 * the two never overlap), and otherwise keeps whatever children it has. Every tile the walk would have
 * liked but did not find is passed to {@link Store#want} with a priority, lower first: small screen error
 * first, and the coarse tiles at the top of the tree before everything, because one of them covers a
 * whole region at the cost of any other tile.
 */
public final class LodSelector {
	/** What the selector needs to know about tiles it does not own. */
	public interface Store {
		boolean ready(long key);

		/** The lowest and highest y of a built tile's mesh, or {@code false} if it has not been built. */
		boolean heightRange(long key, int[] out);

		void want(long key, float priority);

		/** Called for every tile the selection draws or falls back on, so the store keeps it. */
		void touch(long key);
	}

	/** Levels from the top that are always fetched first, for quick coverage of the whole view. */
	private static final int COVER_LEVELS = 2;

	private final LongList draw = new LongList();
	private final int[] range = new int[2];
	private double cx;
	private double cy;
	private double cz;
	private double radius;
	private double clip;
	private double splitScale;
	private int top;
	private Store store;

	/**
	 * Selects tiles around the camera.
	 *
	 * @param radius how far the LOD reaches, in blocks
	 * @param clip the radius around the camera that vanilla's chunks already draw: tiles wholly inside it
	 *        are skipped (the shader discards the parts of the others inside it)
	 * @param scale the projection's pixels per block at distance one: {@code viewportHeight / (2 tan(fovY / 2))}
	 * @param pixelsPerCell the detail budget: the largest a cell may look on screen before its tile splits
	 */
	public LongList select(Store store, double cx, double cy, double cz, double radius, double clip, double scale, double pixelsPerCell) {
		this.store = store;
		this.cx = cx;
		this.cy = cy;
		this.cz = cz;
		this.radius = radius;
		this.clip = clip;
		this.splitScale = scale / pixelsPerCell;
		this.draw.clear();
		int topLevel = 0;
		while (topLevel < TileKey.MAX_LEVEL && TileKey.span(topLevel) * 2 < radius) {
			topLevel++;
		}
		this.top = topLevel;
		int span = TileKey.span(topLevel);
		int tx0 = Math.floorDiv((int) Math.floor(cx - radius), span);
		int tx1 = Math.floorDiv((int) Math.floor(cx + radius), span);
		int tz0 = Math.floorDiv((int) Math.floor(cz - radius), span);
		int tz1 = Math.floorDiv((int) Math.floor(cz + radius), span);
		for (int tz = tz0; tz <= tz1; tz++) {
			for (int tx = tx0; tx <= tx1; tx++) {
				this.collect(TileKey.of(topLevel, tx, tz));
			}
		}
		this.store = null;
		return this.draw;
	}

	/** Collects the tiles that cover {@code key}'s area; true if the area is fully covered. */
	private boolean collect(long key) {
		int level = TileKey.level(key);
		int span = TileKey.span(level);
		double x0 = TileKey.minX(key);
		double z0 = TileKey.minZ(key);
		double x1 = x0 + span;
		double z1 = z0 + span;
		double hx = Math.max(0, Math.max(x0 - this.cx, this.cx - x1));
		double hz = Math.max(0, Math.max(z0 - this.cz, this.cz - z1));
		double horizontal = Math.sqrt(hx * hx + hz * hz);
		if (horizontal > this.radius) {
			return true;
		}
		// wholly inside the area vanilla draws: its farthest corner is within the clip radius
		double fx = Math.max(Math.abs(x0 - this.cx), Math.abs(x1 - this.cx));
		double fz = Math.max(Math.abs(z0 - this.cz), Math.abs(z1 - this.cz));
		if (fx * fx + fz * fz < this.clip * this.clip) {
			return true;
		}
		double dy = 0;
		if (this.store.heightRange(key, this.range)) {
			dy = Math.max(0, Math.max(this.range[0] - this.cy, this.cy - this.range[1]));
		}
		double distance = Math.sqrt(horizontal * horizontal + dy * dy);
		int cell = TileKey.cell(level);
		boolean split = level > 0 && distance < cell * this.splitScale;
		boolean ready = this.store.ready(key);
		float priority = (float) (distance / cell);
		if (!split) {
			if (ready) {
				this.draw.add(key);
				this.store.touch(key);
				return true;
			}
			this.store.want(key, level >= this.top - COVER_LEVELS + 1 ? -level : priority);
			return false;
		}
		if (!ready && level >= this.top - COVER_LEVELS + 1) {
			this.store.want(key, -level);
		}
		int mark = this.draw.size();
		boolean all = true;
		for (int i = 0; i < 4; i++) {
			all &= this.collect(TileKey.child(key, i));
		}
		if (all) {
			return true;
		}
		if (ready) {
			this.draw.truncate(mark);
			this.draw.add(key);
			this.store.touch(key);
			return true;
		}
		return false;
	}
}
