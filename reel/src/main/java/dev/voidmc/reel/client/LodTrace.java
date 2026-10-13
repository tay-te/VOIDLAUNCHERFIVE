package dev.voidmc.reel.client;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A per-render log of VOID LOD's state while filming (POST /set?lodTrace=true), written to
 * renders/&lt;shot&gt;/lod-trace.csv: for each captured sample, what LOD drew and what its engine was doing. For
 * matching odd frames (tools/flashes.py) to what the far view did at the time. By reflection; nothing without LOD.
 */
final class LodTrace {
	private static Writer out;

	private LodTrace() {
	}

	static void begin(RenderJob job) {
		end();
		if (!Tuning.lodTrace) {
			return;
		}
		try {
			Files.createDirectories(job.out());
			out = Files.newBufferedWriter(job.out().resolve("lod-trace.csv"));
			out.write("frame,sample,midFrame,lodNanosMs,engineFrame,tiles,quads,occluded,rebuilt,evicted,discarded,rebuilding,dirtyDrawn,queued,busy\n");
		} catch (IOException e) {
			out = null;
		}
	}

	static void sample(int frame, int sample, boolean midFrame, long lodNanos) {
		if (out == null) {
			return;
		}
		try {
			Class<?> lodClass = Class.forName("dev.voidmc.lod.VoidLod");
			Object lod = lodClass.getMethod("get").invoke(null);
			if (lod == null) {
				return;
			}
			Object engine = lodClass.getMethod("engine").invoke(lod);
			Object renderer = lodClass.getMethod("renderer").invoke(lod);
			StringBuilder b = new StringBuilder();
			b.append(frame).append(',').append(sample).append(',').append(midFrame).append(',').append(lodNanos / 1_000_000).append(',');
			b.append(call(engine, "frame")).append(',');
			b.append(field(renderer, "lastTiles")).append(',').append(field(renderer, "lastQuads")).append(',').append(field(renderer, "lastOccluded")).append(',');
			b.append(field(engine, "rebuilt")).append(',').append(field(engine, "evictedReady")).append(',').append(field(engine, "resultsDiscarded")).append(',');
			b.append(call(engine, "rebuilding")).append(',').append(call(engine, "dirtyDrawn")).append(',').append(call(engine, "queued")).append(',');
			b.append('"').append(String.valueOf(call(engine, "busy")).replace('"', '\'')).append("\"\n");
			out.write(b.toString());
		} catch (ReflectiveOperationException | IOException | RuntimeException e) {
			// a trace is best effort
		}
	}

	static void end() {
		if (out != null) {
			try {
				out.close();
			} catch (IOException ignored) {
				// nothing to do
			}
			out = null;
		}
	}

	private static Object call(Object target, String name) throws ReflectiveOperationException {
		Method m = target.getClass().getMethod(name);
		return m.invoke(target);
	}

	private static Object field(Object target, String name) throws ReflectiveOperationException {
		Field f = target.getClass().getField(name);
		return f.get(target);
	}
}
