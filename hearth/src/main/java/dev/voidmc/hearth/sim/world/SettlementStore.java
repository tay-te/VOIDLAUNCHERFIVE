package dev.voidmc.hearth.sim.world;

import com.mojang.serialization.DynamicOps;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One compressed NBT file per settlement, {@code <id>.dat} in the level's {@code data/hearth/settlements/} folder, so a
 * world with hundreds of towns writes only the ones that changed. Settlements are encoded on the server thread (a
 * consistent snapshot) and written on a background thread, each to a temporary file then moved over the old one, so a
 * crash mid-write leaves the previous save intact.
 */
public final class SettlementStore {
	private static final Logger LOG = LoggerFactory.getLogger("hearth");
	private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "hearth-io");
		t.setDaemon(true);
		return t;
	});

	public final LongAdder written = new LongAdder();
	public final LongAdder encodeNanos = new LongAdder();

	private final Path dir;
	private final Map<Long, CompletableFuture<Void>> pending = new ConcurrentHashMap<>();

	public SettlementStore(Path dir) {
		this.dir = dir;
	}

	public Path dir() {
		return this.dir;
	}

	private Path file(long id) {
		return this.dir.resolve(Long.toUnsignedString(id, 16) + ".dat");
	}

	/** Snapshots the settlement now and writes it in the background. */
	public void save(SimSettlement s, DynamicOps<Tag> ops) {
		long start = System.nanoTime();
		Tag tag = SimSettlement.CODEC.encodeStart(ops, s).resultOrPartial(e -> LOG.error("hearth: cannot save settlement {}: {}", s.id(), e))
			.orElse(null);
		this.encodeNanos.add(System.nanoTime() - start);
		if (!(tag instanceof CompoundTag compound)) {
			return;
		}
		s.clearDirty();
		Path target = file(s.id());
		CompletableFuture<Void> previous = this.pending.getOrDefault(s.id(), CompletableFuture.completedFuture(null));
		CompletableFuture<Void> next = previous.thenRunAsync(() -> write(compound, target), IO);
		this.pending.put(s.id(), next);
		next.whenComplete((v, t) -> this.pending.remove(s.id(), next));
	}

	private void write(CompoundTag tag, Path target) {
		try {
			Files.createDirectories(this.dir);
			Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
			NbtIo.writeCompressed(tag, tmp);
			try {
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
			}
			this.written.increment();
		} catch (IOException e) {
			LOG.error("hearth: could not write {}", target, e);
		}
	}

	/** Removes a settlement's file (after the settlement is gone from the world). */
	public void delete(long id) {
		Path target = file(id);
		CompletableFuture<Void> previous = this.pending.getOrDefault(id, CompletableFuture.completedFuture(null));
		this.pending.put(id, previous.thenRunAsync(() -> {
			try {
				Files.deleteIfExists(target);
			} catch (IOException e) {
				LOG.warn("hearth: could not delete {}", target, e);
			}
		}, IO));
	}

	/** Waits for every write in flight (at server stop, so nothing is lost). */
	public void flush() {
		for (CompletableFuture<Void> f : List.copyOf(this.pending.values())) {
			f.join();
		}
	}

	/** Reads every settlement file in the folder; a file that will not read is set aside, not deleted. */
	public List<SimSettlement> loadAll(DynamicOps<Tag> ops) {
		List<SimSettlement> out = new ArrayList<>();
		if (!Files.isDirectory(this.dir)) {
			return out;
		}
		try (DirectoryStream<Path> files = Files.newDirectoryStream(this.dir, "*.dat")) {
			for (Path f : files) {
				try {
					CompoundTag tag = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
					SimSettlement.CODEC.parse(ops, tag).resultOrPartial(e -> LOG.error("hearth: {}: {}", f.getFileName(), e)).ifPresentOrElse(out::add,
						() -> setAside(f));
				} catch (IOException | RuntimeException e) {
					LOG.error("hearth: could not read {}", f, e);
					setAside(f);
				}
			}
		} catch (IOException e) {
			LOG.error("hearth: could not list {}", this.dir, e);
		}
		return out;
	}

	private void setAside(Path f) {
		try {
			Files.move(f, f.resolveSibling(f.getFileName() + ".bad"), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			LOG.warn("hearth: could not set aside {}", f, e);
		}
	}
}
