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
	private volatile Object footprints;
	private volatile long askedNanos;

	private HearthFootprints(ServerLevel level, MethodHandle of, MethodHandle version, MethodHandle sample) {
		this.level = level;
		this.of = of;
		this.version = version;
		this.sample = sample;
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
			LoggerFactory.getLogger("VOID LOD").info("VOID LOD: drawing VOID Hearth's settlements at the horizon");
			return new HearthFootprints(level, of, version, sample);
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
	public boolean sample(int x, int z, int[] out) {
		Object f = this.footprints();
		if (f == null) {
			return false;
		}
		try {
			return (boolean) this.sample.invoke(f, x, z, out);
		} catch (Throwable t) {
			return false;
		}
	}
}
