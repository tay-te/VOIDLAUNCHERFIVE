package dev.voidmc.lod.source;

import dev.voidmc.expanse.world.terrain.Column;
import java.nio.ByteBuffer;

/**
 * What Expanse's terrain model says about one column, as far as the far view's looks go: the ground, the river, the
 * crater, the landform, how coastal and how uplifted. The singleplayer source reads it straight from the model; on a
 * server the server works it out and sends it ({@code net.FactsService}), and the client colours it with its own
 * textures ({@link ExpanseColours}): a dedicated server has none, and the seed never leaves it. Also the geometry both
 * sides agree on: where the water stands and which height the biome is read at. No client classes: the server loads
 * it. Mutable, reused per thread.
 */
public final class ExpanseFacts {
	/** Bytes one column takes encoded ({@link #write}), its biome and strata offset not included. */
	public static final int BYTES = 4 * 7 + 4 + 1 + 1;

	public float height;
	public float riverDist;
	public float riverHalfWidth;
	public boolean nearRiver;
	public boolean inChannel;
	public int waterTop;
	public int bed;
	public int landform;
	public float crater;
	public boolean craterLava;
	public boolean fjord;
	public float coast;
	public float uplift;

	// the geometry, worked out by geometry()
	public float ground;
	public int water;
	public boolean lava;
	/** The share of the cell a river narrower than it covers. */
	public float river;
	/** The rock under the soil: 0 stone, 1 sandstone, 2 limestone, 3 basalt ({@code Column.rock}). */
	public int rock;

	/** Takes column {@code c} of the model (read it before the model samples another). */
	public ExpanseFacts of(Column c) {
		this.height = c.height;
		this.riverDist = c.riverDist;
		this.riverHalfWidth = c.riverHalfWidth;
		this.nearRiver = c.nearRiver();
		this.inChannel = c.inChannel();
		this.waterTop = c.waterTop();
		this.bed = c.bed();
		this.landform = c.landform;
		this.crater = c.crater;
		this.craterLava = c.craterLava;
		this.fjord = c.fjord;
		this.coast = c.coast;
		this.uplift = c.uplift;
		this.rock = c.rock;
		return this;
	}

	/**
	 * Where the ground and the water stand for a {@code cell}-wide cell: a river narrower than most of the cell is drawn as
	 * the share of the cell it covers, at the valley floor's height, not as water at the one vertex that happens to fall
	 * in it; crater lakes and lava fill volcano tops.
	 */
	public void geometry(int cell, int seaLevel) {
		float ground = this.height;
		int water = Integer.MIN_VALUE;
		boolean lava = false;
		boolean narrow = cell > 1 && 2 * this.riverHalfWidth < 1.5F * cell;
		if (this.inChannel && !narrow) {
			water = this.waterTop;
			ground = Math.min(ground, this.bed);
		} else if (this.fjord || Math.ceil(ground) < seaLevel) {
			water = seaLevel;
		}
		if (this.crater > ground) {
			water = Math.max(water, (int) Math.ceil(this.crater));
			lava = this.craterLava;
		}
		float river = 0;
		if (water == Integer.MIN_VALUE && this.nearRiver && cell > 1) {
			float reach = this.riverHalfWidth + cell * 0.5F - this.riverDist;
			river = Math.max(0, Math.min(1, reach / cell)) * Math.min(1, 0.4F + 2 * this.riverHalfWidth / cell);
		}
		this.ground = ground;
		this.water = water;
		this.lava = lava;
		this.river = river;
	}

	/** The height the biome is read at, once {@link #geometry} has run: the surface, or the sea's. */
	public int biomeY(int seaLevel) {
		return Math.max((int) Math.ceil(this.ground), seaLevel);
	}

	public void write(ByteBuffer b) {
		b.putFloat(this.height).putFloat(this.riverDist).putFloat(this.riverHalfWidth).putFloat(this.crater).putFloat(this.coast)
			.putFloat(this.uplift).putFloat(this.bed).putInt(this.waterTop).put((byte) this.landform)
			.put((byte) ((this.nearRiver ? 1 : 0) | (this.inChannel ? 2 : 0) | (this.craterLava ? 4 : 0) | (this.fjord ? 8 : 0)));
	}

	public ExpanseFacts read(ByteBuffer b) {
		this.height = b.getFloat();
		this.riverDist = b.getFloat();
		this.riverHalfWidth = b.getFloat();
		this.crater = b.getFloat();
		this.coast = b.getFloat();
		this.uplift = b.getFloat();
		this.bed = Math.round(b.getFloat());
		this.waterTop = b.getInt();
		this.landform = b.get();
		int flags = b.get();
		this.nearRiver = (flags & 1) != 0;
		this.inChannel = (flags & 2) != 0;
		this.craterLava = (flags & 4) != 0;
		this.fjord = (flags & 8) != 0;
		return this;
	}
}
