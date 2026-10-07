package dev.voidmc.atlas.core;

import dev.voidmc.atlas.core.store.FileMoves;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * A map's files on disk: per layer ({@code surface}, {@code cave_-2}, ...) a raw file and an image file per region.
 * Writes go to a temporary file first and are moved into place, so a crash leaves the old file or the new one, never
 * half of one. A file that cannot be read (cut short, garbled, from a future version) is moved aside to
 * {@code .corrupt} and treated as missing: the region is simply seen again.
 */
public final class RegionStore {
	private final Path root;
	private final Consumer<String> warn;

	public RegionStore(Path root, Consumer<String> warn) {
		this.root = root;
		this.warn = warn;
	}

	public Path root() {
		return this.root;
	}

	public Path rawFile(String layer, int rx, int rz) {
		return this.root.resolve(layer).resolve("r." + rx + "." + rz + ".raw");
	}

	public Path imageFile(String layer, int rx, int rz) {
		return this.root.resolve(layer).resolve("r." + rx + "." + rz + ".img");
	}

	public boolean hasRaw(String layer, int rx, int rz) {
		return Files.isRegularFile(this.rawFile(layer, rx, rz));
	}

	/** The raw region, or null if there is none (or it was corrupt and has been moved aside). */
	public RegionRaw loadRaw(String layer, int rx, int rz) {
		Path file = this.rawFile(layer, rx, rz);
		byte[] data = read(file);
		if (data == null) {
			return null;
		}
		try {
			return RegionRaw.decode(rx, rz, data);
		} catch (IOException | RuntimeException e) {
			this.quarantine(file, e);
			return null;
		}
	}

	/**
	 * The image's levels in {@code levelMask}, or null if there is no image, it is corrupt, or it was drawn with
	 * another palette than {@code palette} (then it must be drawn again from the raw region).
	 */
	public RegionImage loadImage(String layer, int rx, int rz, int levelMask, int palette) {
		Path file = this.imageFile(layer, rx, rz);
		byte[] data = read(file);
		if (data == null) {
			return null;
		}
		if (RegionImage.paletteOf(data) != palette) {
			return null;
		}
		try {
			return RegionImage.decode(data, rx, rz, levelMask);
		} catch (IOException | RuntimeException e) {
			this.quarantine(file, e);
			return null;
		}
	}

	public void saveRaw(String layer, RegionRaw raw) throws IOException {
		long version = raw.version();
		write(this.rawFile(layer, raw.x, raw.z), raw.encode());
		raw.markSaved(version);
	}

	public void saveImage(String layer, RegionImage image) throws IOException {
		long version = image.version();
		write(this.imageFile(layer, image.x, image.z), image.encode());
		image.markSaved(version);
	}

	/** Every region with a raw file in a layer, as packed {@link Coords#pack} keys. */
	public long[] regions(String layer) {
		Path dir = this.root.resolve(layer);
		if (!Files.isDirectory(dir)) {
			return new long[0];
		}
		try (var files = Files.list(dir)) {
			return files.map(p -> p.getFileName().toString()).filter(n -> n.startsWith("r.") && n.endsWith(".raw")).mapToLong(n -> {
				String[] parts = n.split("\\.");
				try {
					return Coords.pack(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
				} catch (RuntimeException e) {
					return Long.MIN_VALUE;
				}
			}).filter(k -> k != Long.MIN_VALUE).toArray();
		} catch (IOException e) {
			return new long[0];
		}
	}

	private void quarantine(Path file, Exception why) {
		this.warn.accept("Atlas: " + file + " is unreadable (" + why.getMessage() + "); moving it aside and mapping that region afresh");
		try {
			FileMoves.move(file, file.resolveSibling(file.getFileName() + ".corrupt"));
		} catch (IOException e) {
			try {
				Files.deleteIfExists(file);
			} catch (IOException ignored) {
				// nothing more to do: it will be overwritten by the next save
			}
		}
	}

	private static byte[] read(Path file) {
		try {
			return Files.readAllBytes(file);
		} catch (NoSuchFileException e) {
			return null;
		} catch (IOException e) {
			return null;
		}
	}

	/**
	 * Writes a file whole: to a temporary file first, then moved into place (atomically where the file system allows;
	 * on Windows, waiting briefly for anyone reading the old file to let go, see {@link FileMoves#replace}).
	 */
	public static void write(Path file, byte[] data) throws IOException {
		Files.createDirectories(file.toAbsolutePath().getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try {
			Files.write(tmp, data);
			FileMoves.replace(tmp, file);
		} catch (IOException | RuntimeException e) {
			try {
				// unless a plain replace removed the old file and then failed: the .tmp is then the only copy left
				if (Files.exists(file)) {
					Files.deleteIfExists(tmp);
				}
			} catch (IOException ignored) {
				// a stale .tmp is overwritten by the next write
			}
			throw e;
		}
	}
}
