package dev.voidmc.gloam.bridge;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import java.nio.ByteBuffer;
import java.util.BitSet;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.lwjgl.system.MemoryUtil;

/**
 * The bridge's data texture ({@code gloam:pack/data}): RGBA8, {@link Contract#WIDTH} x {@link Contract#HEIGHT}, one
 * mip level, registered with the texture manager so a shader pack can name it ({@code customTexture.void_data}). The
 * texels live on the CPU ({@link #bytes}); {@link #upload} sends only what changed: the used part of the header, and
 * per tile the lines (rows or columns) written since, or the whole tile when many are. Iris binds it with its own
 * nearest sampler, so the pack reads it with {@code texelFetch} only.
 */
final class DataTexture extends AbstractTexture {
	/** The header's texel columns that hold anything (row 0's fields and the mirror's slots). */
	private static final int HEADER_USED = 64;
	/**
	 * More dirty lines than this in a tile and the whole tile goes up instead. Runs of neighbouring lines go up as one
	 * rectangle, so a band of rows (the weather tiles' rebuild, 64 rows a tick) is one call, not 64.
	 */
	private static final int WHOLE_TILE = 160;

	final byte[] bytes = new byte[Contract.WIDTH * Contract.HEIGHT * 4];
	private final BitSet[] rows = new BitSet[Contract.TILES], columns = new BitSet[Contract.TILES];
	private final boolean[] whole = new boolean[Contract.TILES];
	private boolean header = true;
	private ByteBuffer staging = MemoryUtil.memAlloc(Contract.TILE * Contract.TILE * 4);
	/** What the last upload sent: bytes and calls. */
	int lastBytes, lastCalls;

	DataTexture() {
		GpuDevice device = RenderSystem.getDevice();
		this.texture = device.createTexture("VOID Gloam pack data", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
			GpuFormat.RGBA8_UNORM, Contract.WIDTH, Contract.HEIGHT, 1, 1);
		this.textureView = device.createTextureView(this.texture);
		this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST);
		for (int t = 0; t < Contract.TILES; t++) {
			this.rows[t] = new BitSet(Contract.TILE);
			this.columns[t] = new BitSet(Contract.TILE);
		}
		this.all();
	}

	/** Marks a tile's line (or, with {@code line} -1, the whole tile) as changed. */
	void mark(int tile, boolean column, int line) {
		if (line < 0) {
			this.whole[tile] = true;
		} else {
			(column ? this.columns : this.rows)[tile].set(line);
		}
	}

	void markHeader() {
		this.header = true;
	}

	/** Everything to go up again (a pack was switched on after uploads were skipped). */
	void all() {
		this.header = true;
		java.util.Arrays.fill(this.whole, true);
	}

	/** Sends what changed. Render thread. */
	void upload() {
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		int sent = 0, calls = 0;
		if (this.header) {
			this.header = false;
			sent += this.send(encoder, 0, 0, HEADER_USED, Contract.HEADER_ROWS);
			calls++;
		}
		for (int t = 0; t < Contract.TILES; t++) {
			BitSet r = this.rows[t], c = this.columns[t];
			int x0 = (t & 3) * Contract.TILE, y0 = Contract.HEADER_ROWS + (t >> 2) * Contract.TILE;
			if (this.whole[t] || r.cardinality() + c.cardinality() > WHOLE_TILE) {
				sent += this.send(encoder, x0, y0, Contract.TILE, Contract.TILE);
				calls++;
			} else {
				for (int i = r.nextSetBit(0); i >= 0; ) {
					int end = r.nextClearBit(i);
					sent += this.send(encoder, x0, y0 + i, Contract.TILE, end - i);
					calls++;
					i = r.nextSetBit(end);
				}
				for (int i = c.nextSetBit(0); i >= 0; ) {
					int end = c.nextClearBit(i);
					sent += this.send(encoder, x0 + i, y0, end - i, Contract.TILE);
					calls++;
					i = c.nextSetBit(end);
				}
			}
			this.whole[t] = false;
			r.clear();
			c.clear();
		}
		this.lastBytes = sent;
		this.lastCalls = calls;
	}

	/** Copies the rectangle out of {@link #bytes} into the staging buffer, tightly packed, and writes it. */
	private int send(CommandEncoder encoder, int x, int y, int w, int h) {
		ByteBuffer b = this.staging;
		b.clear();
		for (int row = 0; row < h; row++) {
			int o = Contract.offset(x, y + row);
			b.put(this.bytes, o, w * 4);
		}
		b.flip();
		encoder.writeToTexture(this.texture, b, 0, 0, x, y, w, h);
		return w * h * 4;
	}

	@Override
	public void close() {
		super.close();
		if (this.staging != null) {
			MemoryUtil.memFree(this.staging);
			this.staging = null;
		}
	}
}
