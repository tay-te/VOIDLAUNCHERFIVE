package dev.voidmc.lod.source;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.LoggerFactory;

/**
 * VOID Hearth's settlements as its footprint API gives them ({@code dev.voidmc.hearth.sim.LodFootprints}): per column,
 * whether a finished building stands there, its wall and roof colours and its eave and ridge heights; versioned per
 * 1024-block region. Reached by reflection, so VOID LOD neither needs Hearth to build nor loads any of it without
 * Hearth; null where Hearth is not installed. Hearth's world may start after the far view does, so its footprints are
 * looked up again until there are some.
 */
public final class HearthFootprints implements HearthOverlay.Footprints {
	private static final String CLASS = "dev.voidmc.hearth.sim.LodFootprints";
	/** How often a level Hearth does not run in yet is asked again. */
	private static final long RETRY_NANOS = 5_000_000_000L;

	private final ServerLevel level;
	private final MethodHandle of;
	private final MethodHandle version;
	private final MethodHandle sample;
	/** Hearth's bulk read of a chunk's columns, if this Hearth has it (null: column by column). */
	private final MethodHandle chunk;
	/** Bumped by {@link #changed}: the workers' chunk caches older than this are dropped. */
	private volatile int epoch;
	private final ThreadLocal<Chunk> chunks = ThreadLocal.withInitial(Chunk::new);

	/** A worker's last chunk read in bulk: its key, whether a building stands in it, its columns, and when it was read. */
	private static final class Chunk {
		long key = Long.MIN_VALUE;
		boolean any;
		int epoch;
		final int[] columns = new int[768];
	}
	private volatile Object footprints;
	private volatile long askedNanos;

	private HearthFootprints(ServerLevel level, MethodHandle of, MethodHandle version, MethodHandle sample, MethodHandle chunk) {
		this.level = level;
		this.of = of;
		this.version = version;
		this.sample = sample;
		this.chunk = chunk;
	}

	/** Hearth's footprints in {@code level}, or null if Hearth is not installed (or its API is not this one). */
	public static HearthFootprints of(ServerLevel level) {
		if (level == null || !net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("hearth")) {
			return null;
		}
		try {
			Class<?> type = Class.forName(CLASS);
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			MethodHandle of = lookup.findStatic(type, "of", MethodType.methodType(type, ServerLevel.class));
			MethodHandle version = lookup.findVirtual(type, "version", MethodType.methodType(long.class, int.class, int.class));
			MethodHandle sample = lookup.findVirtual(type, "sample", MethodType.methodType(boolean.class, int.class, int.class, int[].class));
			MethodHandle chunk;
			try {
				chunk = lookup.findVirtual(type, "chunk", MethodType.methodType(boolean.class, int.class, int.class, int[].class));
			} catch (NoSuchMethodException e) {
				chunk = null;
			}
			LoggerFactory.getLogger("VOID LOD").info("VOID LOD: drawing VOID Hearth's settlements at the horizon");
			return new HearthFootprints(level, of, version, sample, chunk);
		} catch (ReflectiveOperationException | LinkageError e) {
			LoggerFactory.getLogger("VOID LOD").warn("VOID LOD: VOID Hearth is installed but its footprint API is not the one expected", e);
			return null;
		}
	}

	/** Hearth's footprints object for the level, looked up now and then until Hearth's world has started. */
	private Object footprints() {
		Object f = this.footprints;
		if (f != null) {
			return f;
		}
		long now = System.nanoTime();
		if (now - this.askedNanos < RETRY_NANOS && this.askedNanos != 0) {
			return null;
		}
		this.askedNanos = now;
		try {
			f = this.of.invoke(this.level);
		} catch (Throwable t) {
			f = null;
		}
		this.footprints = f;
		return f;
	}

	@Override
	public long version(int regionX, int regionZ) {
		Object f = this.footprints();
		if (f == null) {
			return 0;
		}
		try {
			return (long) this.version.invoke(f, regionX, regionZ);
		} catch (Throwable t) {
			return 0;
		}
	}

	@Override
	public void changed() {
		this.epoch++;
	}

	@Override
	public boolean sample(int x, int z, int[] out) {
		Object f = this.footprints();
		if (f == null) {
			return false;
		}
		if (this.chunk != null) {
			Chunk c = this.chunks.get();
			long key = (long) (x >> 4) << 32 ^ (z >> 4) & 0xFFFFFFFFL;
			int epoch = this.epoch;
			if (c.key != key || c.epoch != epoch) {
				java.util.Arrays.fill(c.columns, 0);
				try {
					c.any = (boolean) this.chunk.invoke(f, x >> 4, z >> 4, c.columns);
				} catch (Throwable t) {
					c.any = false;
				}
				c.key = key;
				c.epoch = epoch;
			}
			if (!c.any) {
				return false;
			}
			int i = ((z & 15) << 4 | x & 15) * 3;
			if (c.columns[i] == 0 && c.columns[i + 1] == 0 && c.columns[i + 2] == 0) {
				return false;
			}
			out[0] = c.columns[i];
			out[1] = c.columns[i + 1];
			out[2] = c.columns[i + 2];
			return true;
		}
		try {
			return (boolean) this.sample.invoke(f, x, z, out);
		} catch (Throwable t) {
			return false;
		}
	}
}
