package dev.voidmc.atlas.client.data;

import dev.voidmc.atlas.core.detect.BlockSource;
import dev.voidmc.atlas.core.detect.Sig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * The client's loaded blocks as {@link Sig} codes, for the structure scan's look round an anchor and the underground
 * reveal, on a worker thread. Only what the client holds is read ({@link Sig#UNKNOWN} elsewhere); a block read while
 * the client thread changes it is read again when that change's own rescan comes. Keeps the last few chunks it used,
 * so a walk across a chunk border does not look the chunk up for every block. One per task.
 */
public final class LevelBlocks implements BlockSource {
	private final ClientLevel level;
	private final ChunkScanner coder;
	private final long[] keys = {Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE};
	private final LevelChunk[] chunks = new LevelChunk[4];
	private int next;

	public LevelBlocks(ClientLevel level, ChunkScanner coder) {
		this.level = level;
		this.coder = coder;
	}

	/** The chunk at chunk coordinates, if the client holds it. */
	public LevelChunk chunk(int cx, int cz) {
		long key = ChunkPos.pack(cx, cz);
		for (int i = 0; i < 4; i++) {
			if (this.keys[i] == key) {
				return this.chunks[i];
			}
		}
		LevelChunk c = this.level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
		int slot = this.next;
		this.next = (this.next + 1) & 3;
		this.keys[slot] = key;
		this.chunks[slot] = c;
		return c;
	}

	public BlockState state(int x, int y, int z) {
		LevelChunk c = this.chunk(x >> 4, z >> 4);
		if (c == null) {
			return null;
		}
		LevelChunkSection[] sections = c.getSections();
		int index = (y - c.getMinY()) >> 4;
		if (index < 0 || index >= sections.length) {
			return null;
		}
		return sections[index].getBlockState(x & 15, y & 15, z & 15);
	}

	@Override
	public int code(int x, int y, int z) {
		LevelChunk c = this.chunk(x >> 4, z >> 4);
		if (c == null) {
			return Sig.UNKNOWN;
		}
		LevelChunkSection[] sections = c.getSections();
		int index = (y - c.getMinY()) >> 4;
		if (index < 0) {
			// below the world: bedrock, as far as anyone can tell
			return Sig.code(Sig.NONE, Sig.SOLID);
		}
		if (index >= sections.length) {
			return Sig.code(Sig.NONE, Sig.OPEN);
		}
		return this.coder.code(sections[index].getBlockState(x & 15, y & 15, z & 15));
	}
}
