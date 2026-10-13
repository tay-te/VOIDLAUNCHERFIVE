package dev.voidmc.reel.client;

import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * VOID LOD's recording API (LodApi.setRecordingClock, setTileHold, setDetailScale), by reflection: Reel does not
 * depend on LOD. Set when a job starts, cleared when it ends. With an older LOD that lacks the API, the mixins into
 * its internals (LodClockMixin, LodFreezeMixin) stand in, and detail is not compensated for supersampling.
 */
public final class LodHooks {
	private static final Method CLOCK;
	private static final Method HOLD;
	private static final Method DETAIL;

	static {
		Method clock = null;
		Method hold = null;
		Method detail = null;
		try {
			Class<?> api = Class.forName("dev.voidmc.lod.api.LodApi");
			clock = api.getMethod("setRecordingClock", LongSupplier.class);
			hold = api.getMethod("setTileHold", BooleanSupplier.class);
			detail = api.getMethod("setDetailScale", double.class);
		} catch (ReflectiveOperationException | LinkageError ignored) {
			// no LOD, or one from before the recording API
		}
		CLOCK = clock;
		HOLD = hold;
		DETAIL = detail;
	}

	private LodHooks() {
	}

	/** Whether LOD takes the recording hooks through its API (then the fallback mixins stand aside). */
	public static boolean api() {
		return CLOCK != null;
	}

	static void begin(Director director, RenderJob job) {
		if (!api()) {
			return;
		}
		call(CLOCK, (LongSupplier) () -> Tuning.lodClock() && director.clockActive() ? director.lodNanos() : System.nanoTime());
		call(HOLD, (BooleanSupplier) () -> Tuning.lodFreeze() && director.midFrame());
		call(DETAIL, Tuning.lodCompensate ? (double) job.supersample() : 1.0);
	}

	static void end() {
		if (!api()) {
			return;
		}
		call(CLOCK, (Object) null);
		call(HOLD, (Object) null);
		call(DETAIL, 1.0);
	}

	private static void call(Method method, Object arg) {
		try {
			method.invoke(null, arg);
		} catch (ReflectiveOperationException e) {
			dev.voidmc.reel.Reel.LOG.warn("[reel] LOD hook {} failed", method.getName(), e);
		}
	}
}
