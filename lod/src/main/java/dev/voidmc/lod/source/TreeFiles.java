package dev.voidmc.lod.source;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.LoggerFactory;

/**
 * The trees {@link TreeDecorator} worked out, kept in the world's folder so a horizon seen before stands its trees at
 * once. One file per 32 x 32 chunks; each chunk a record appended as it is worked out (its index in the region, then
 * its blocks deflated: position and block state id). A file starts with the identity of the world and the game it was
 * made for (seed, mods and their versions, as the generator cache's); another identity, and it starts again. Read a
 * chunk at a time through each region's index of where its records are; trimmed to {@value #KEEP_MB} MB, oldest
 * regions first.
 */
final class TreeFiles {
	private static final int MAGIC = 0x56545245;
	private static final int VERSION = 1;
	private static final int HEADER = 16;
	private static final int REGIONS_KEPT = 256;
	private static final long KEEP_MB = 128;

	private final Path dir;
	private final long identity;
	/** Per region, where each of its chunks' records starts (0: none yet). */
	private final Map<Long, long[]> indices = new LinkedHashMap<>(64, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, long[]> eldest) {
			return this.size() > REGIONS_KEPT;
		}
	};

	TreeFiles(Path dir, long identity) {
		this.dir = dir;
		this.identity = identity;
		try {
			Files.createDirectories(dir);
			trim(dir);
		} catch (IOException e) {
			LoggerFactory.getLogger("VOID LOD").warn("VOID LOD: cannot keep trees in {}", dir, e);
		}
	}

	private Path file(int rx, int rz) {
		return this.dir.resolve("r." + rx + "." + rz + ".vtree");
	}

	/** The chunk's trees as kept, or null if they never were. */
	synchronized TreePlacer.Placed read(int cx, int cz) {
		int rx = cx >> 5, rz = cz >> 5;
		long[] index = this.index(rx, rz);
		long at = index == null ? 0 : index[(cx & 31) + (cz & 31) * 32];
		if (at == 0) {
			return null;
		}
		try (RandomAccessFile f = new RandomAccessFile(this.file(rx, rz).toFile(), "r")) {
			f.seek(at);
			f.readInt();
			int compressed = f.readInt();
			int count = f.readInt();
			byte[] data = new byte[compressed];
			f.readFully(data);
			Inflater inflater = new Inflater();
			byte[] raw = new byte[count * 8];
			try {
				inflater.setInput(data);
				int n = 0;
				while (n < raw.length && !inflater.finished()) {
					n += inflater.inflate(raw, n, raw.length - n);
				}
			} finally {
				inflater.end();
			}
			ByteBuffer b = ByteBuffer.wrap(raw);
			long[] positions = new long[count];
			BlockState[] states = new BlockState[count];
			int kept = 0;
			for (int i = 0; i < count; i++) {
				int p = b.getInt();
				BlockState state = Block.BLOCK_STATE_REGISTRY.byId(b.getInt());
				if (state == null) {
					continue;
				}
				int x = cx * 16 + (p & 15), z = cz * 16 + (p >> 4 & 15), y = (p >> 8) - 4096;
				positions[kept] = net.minecraft.core.BlockPos.asLong(x, y, z);
				states[kept++] = state;
			}
			return new TreePlacer.Placed(Arrays.copyOf(positions, kept), Arrays.copyOf(states, kept));
		} catch (IOException | DataFormatException | RuntimeException e) {
			return null;
		}
	}

	/** Keeps the chunk's trees: appended to its region's file. */
	synchronized void write(int cx, int cz, TreePlacer.Placed placed) {
		int rx = cx >> 5, rz = cz >> 5;
		long[] index = this.index(rx, rz);
		if (index == null) {
			return;
		}
		ByteBuffer raw = ByteBuffer.allocate(placed.positions().length * 8);
		for (int i = 0; i < placed.positions().length; i++) {
			long p = placed.positions()[i];
			int x = net.minecraft.core.BlockPos.getX(p), y = net.minecraft.core.BlockPos.getY(p), z = net.minecraft.core.BlockPos.getZ(p);
			raw.putInt((x & 15) | (z & 15) << 4 | (y + 4096) << 8);
			raw.putInt(Block.BLOCK_STATE_REGISTRY.getId(placed.states()[i]));
		}
		Deflater deflater = new Deflater(Deflater.BEST_SPEED);
		ByteArrayOutputStream zipped = new ByteArrayOutputStream();
		try {
			deflater.setInput(raw.array(), 0, raw.position());
			deflater.finish();
			byte[] chunk = new byte[8192];
			while (!deflater.finished()) {
				zipped.write(chunk, 0, deflater.deflate(chunk));
			}
		} finally {
			deflater.end();
		}
		Path file = this.file(rx, rz);
		try (RandomAccessFile f = new RandomAccessFile(file.toFile(), "rw")) {
			long at = Math.max(HEADER, f.length());
			if (f.length() < HEADER) {
				f.setLength(0);
				f.writeInt(MAGIC);
				f.writeInt(VERSION);
				f.writeLong(this.identity);
				at = HEADER;
			}
			f.seek(at);
			f.writeInt((cx & 31) + (cz & 31) * 32);
			f.writeInt(zipped.size());
			f.writeInt(placed.positions().length);
			f.write(zipped.toByteArray());
			index[(cx & 31) + (cz & 31) * 32] = at;
		} catch (IOException e) {
			// not kept: worked out again next time
		}
	}

	/** The region's index, read from its file the first time (a file of another identity is started again); null if unreadable. */
	private long[] index(int rx, int rz) {
		long key = (long) rx << 32 ^ rz & 0xFFFFFFFFL;
		long[] index = this.indices.get(key);
		if (index != null) {
			return index;
		}
		index = new long[1024];
		Path file = this.file(rx, rz);
		if (Files.exists(file)) {
			try (RandomAccessFile f = new RandomAccessFile(file.toFile(), "rw")) {
				if (f.length() < HEADER || f.readInt() != MAGIC || f.readInt() != VERSION || f.readLong() != this.identity) {
					f.setLength(0);
				} else {
					long at = HEADER;
					long length = f.length();
					while (at + 12 <= length) {
						f.seek(at);
						int chunk = f.readInt();
						int compressed = f.readInt();
						f.readInt();
						if (chunk < 0 || chunk >= 1024 || compressed < 0 || at + 12 + compressed > length) {
							// a record cut short (the game stopped mid-write): what follows is not trusted
							f.setLength(at);
							break;
						}
						index[chunk] = at;
						at += 12 + compressed;
					}
				}
			} catch (IOException e) {
				return null;
			}
		}
		this.indices.put(key, index);
		return index;
	}

	/** Deletes the oldest region files while the folder holds more than {@value #KEEP_MB} MB. */
	private static void trim(Path dir) throws IOException {
		List<Path> files;
		try (Stream<Path> s = Files.list(dir)) {
			files = s.filter(p -> p.getFileName().toString().endsWith(".vtree")).sorted(Comparator.comparingLong(p -> p.toFile().lastModified())).toList();
		}
		long total = files.stream().mapToLong(p -> p.toFile().length()).sum();
		for (Path p : files) {
			if (total <= KEEP_MB << 20) {
				break;
			}
			total -= p.toFile().length();
			Files.deleteIfExists(p);
		}
	}
}
