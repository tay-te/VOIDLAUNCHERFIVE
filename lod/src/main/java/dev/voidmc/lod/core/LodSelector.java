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
	/** How much farther a tile out of view counts for the build queue's order. */
	static final float OUT_OF_VIEW_WAIT = 4;
	/** What the selector needs to know about tiles it does not own. */
	public interface Store {
		boolean ready(long key);

		/** The lowest and highest y of a built tile's mesh, or {@code false} if it has not been built. */
		boolean heightRange(long key, int[] out);

		void want(long key, float priority);

		/** Called for every tile the selection draws or falls back on, so the store keeps it. */
		void touch(long key);
	}

	/** Whether a box of the world is in view: tiles outside it need not be finer than a normal field of view asks. */
	public interface Visibility {
		boolean visible(double x0, double y0, double z0, double x1, double y1, double z1);
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
	/** The same for tiles whose children are smooth: those are cheap enough to keep finer. */
	private double farSplitScale;
	private Visibility visibility;
	/** What the split scales are multiplied by for tiles out of view: under 1 when zoomed in. */
	private double outOfView = 1;
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
		return this.select(store, cx, cy, cz, radius, clip, scale, pixelsPerCell, pixelsPerCell);
	}

	/**
	 * Selects tiles around the camera, with a separate detail budget for splits into smooth tiles: a smooth tile
	 * is a tenth of a blocky one's cost, so the far field can afford smaller cells than the blocky band near
	 * the chunks.
	 */
	public LongList select(Store store, double cx, double cy, double cz, double radius, double clip, double scale, double pixelsPerCell,
		double farPixelsPerCell) {
		return this.select(store, cx, cy, cz, radius, clip, scale, pixelsPerCell, farPixelsPerCell, null, 1);
	}

	/**
	 * As above, with the view: zoomed in (a spyglass, a narrow field of view), the land in view gets the detail
	 * its magnification asks for, and the land out of view only what a normal field of view would, scaled by
	 * {@code outOfView}, instead of the whole horizon refining for a glance through a lens.
	 */
	public LongList select(Store store, double cx, double cy, double cz, double radius, double clip, double scale, double pixelsPerCell,
		double farPixelsPerCell, Visibility visibility, double outOfView) {
		this.visibility = visibility;
		this.outOfView = outOfView;
		this.farSplitScale = scale / farPixelsPerCell;
		this.store = store;
		this.cx = cx;
		this.cy = cy;
		this.cz = cz;
		this.radius = radius;
		this.clip = clip;
		this.splitScale = scale / pixelsPerCell;
		this.draw.clear();
		int topLevel = 0;
		// through a shader pack a vertex's x and z are unsigned shorts from the tile's corner: no wider tiles than that holds
		int maxLevel = TileBuilder.packFormat ? TileBuilder.PACK_MAX_LEVEL : TileKey.MAX_LEVEL;
		while (topLevel < maxLevel && TileKey.span(topLevel) * 2 < radius) {
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
		this.visibility = null;
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
		// until its heights are known, as tall as worlds go (Expanse's reaches 639)
		int y0 = -64, y1 = 640;
		if (this.store.heightRange(key, this.range)) {
			dy = Math.max(0, Math.max(this.range[0] - this.cy, this.cy - this.range[1]));
			y0 = this.range[0];
			y1 = this.range[1];
		}
		double distance = Math.sqrt(horizontal * horizontal + dy * dy);
		int cell = TileKey.cell(level);
		double splitScale = level - 1 >= TileBuilder.smoothFrom ? this.farSplitScale : this.splitScale;
		boolean inView = this.visibility == null || this.visibility.visible(x0, y0, z0, x1, y1, z1);
		if (this.outOfView < 1 && !inView) {
			splitScale *= this.outOfView;
		}
		boolean split = level > 0 && distance < cell * splitScale;
		boolean ready = this.store.ready(key);
		// what the camera sees first: a tile out of view waits as if it were farther, so after a teleport the view
		// fills in before what is behind the player
		float priority = (float) (distance / cell) * (inView ? 1 : OUT_OF_VIEW_WAIT);
		if (!split) {
			if (ready) {
				this.draw.add(key);
				this.store.touch(key);
				return true;
			}
			this.store.want(key, level >= this.top - COVER_LEVELS + 1 ? -level : priority);
			return false;
		}
		if (ready) {
			// a split tile is its children's fallback: keep it while they show, or losing one of them would
			// leave a hole until the parent was built again
			this.store.touch(key);
		} else if (level >= this.top - COVER_LEVELS + 1) {
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
