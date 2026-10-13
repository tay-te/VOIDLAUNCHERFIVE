package dev.voidmc.reel.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What happened while a job was filmed, written next to its frames as render.json (or stills.json): how long it
 * took, how long the first view took to load, how often frames waited, and which frames were filmed with part of
 * the view still loading. It is how a render is judged without watching it.
 */
final class Report {
	private RenderJob job;
	private long prepareNanos;
	private int prefetchChunks;
	private long prefetchNanos;
	private int prepareFrames;
	private int frames;
	private int holds;
	private int catchUps;
	private int redos;
	private int backlogWaits;
	private final List<Integer> unready = new ArrayList<>();
	private final List<Integer> farBuilding = new ArrayList<>();
	private final List<String> notes = new ArrayList<>();
	private long totalNanos;
	private int written;
	private String failure;

	void reset(RenderJob job) {
		this.job = job;
		this.prepareNanos = 0;
		this.prefetchChunks = 0;
		this.prefetchNanos = 0;
		this.prepareFrames = 0;
		this.frames = 0;
		this.holds = 0;
		this.catchUps = 0;
		this.redos = 0;
		this.backlogWaits = 0;
		this.unready.clear();
		this.farBuilding.clear();
		this.notes.clear();
		this.totalNanos = 0;
		this.written = 0;
		this.failure = null;
	}

	void prefetched(int chunks, long nanos) {
		this.prefetchChunks = chunks;
		this.prefetchNanos = nanos;
	}

	void prepared(long nanos, int frames) {
		this.prepareNanos = nanos;
		this.prepareFrames = frames;
	}

	void frame() {
		this.frames++;
	}

	void hold() {
		this.holds++;
	}

	void redo() {
		this.redos++;
	}

	void catchUp() {
		this.catchUps++;
	}

	void backlogWait() {
		this.backlogWaits++;
	}

	void unready(int frame, String state) {
		if (this.unready.isEmpty() || this.unready.getLast() != frame) {
			this.unready.add(frame);
			if (this.notes.size() < 12) {
				this.notes.add("frame " + frame + " filmed loading: " + state);
			}
		}
	}

	void farBuilding(int frame) {
		if (this.farBuilding.isEmpty() || this.farBuilding.getLast() != frame) {
			this.farBuilding.add(frame);
		}
	}

	void note(String note) {
		this.notes.add(note);
	}

	void finished(long nanos, int written, String failure) {
		this.totalNanos = nanos;
		this.written = written;
		this.failure = failure;
	}

	String json() {
		StringBuilder b = new StringBuilder("{\n");
		b.append("  \"job\": \"").append(escape(this.job.describe())).append("\",\n");
		b.append("  \"rig\": \"").append(escape(this.job.shot().rig().describe())).append("\",\n");
		b.append(String.format(Locale.ROOT, "  \"seconds\": %.1f,%n", this.totalNanos / 1e9));
		b.append(String.format(Locale.ROOT, "  \"prepareSeconds\": %.1f,%n", this.prepareNanos / 1e9));
		b.append("  \"prepareFrames\": ").append(this.prepareFrames).append(",\n");
		b.append("  \"prefetchChunks\": ").append(this.prefetchChunks).append(",\n");
		b.append(String.format(Locale.ROOT, "  \"prefetchSeconds\": %.1f,%n", this.prefetchNanos / 1e9));
		b.append("  \"frames\": ").append(this.frames).append(",\n");
		b.append("  \"written\": ").append(this.written).append(",\n");
		b.append(String.format(Locale.ROOT, "  \"secondsPerFrame\": %.3f,%n", this.frames == 0 ? 0 : (this.totalNanos - this.prepareNanos) / 1e9 / this.frames));
		b.append("  \"holds\": ").append(this.holds).append(",\n");
		b.append("  \"catchUps\": ").append(this.catchUps).append(",\n");
		b.append("  \"redos\": ").append(this.redos).append(",\n");
		b.append("  \"backlogWaits\": ").append(this.backlogWaits).append(",\n");
		b.append("  \"framesFilmedLoading\": ").append(this.unready).append(",\n");
		b.append("  \"framesFarStillBuilding\": ").append(this.farBuilding.size()).append(",\n");
		b.append("  \"notes\": [");
		for (int i = 0; i < this.notes.size(); i++) {
			b.append(i == 0 ? "" : ", ").append('"').append(escape(this.notes.get(i))).append('"');
		}
		b.append("],\n");
		b.append("  \"failure\": ").append(this.failure == null ? "null" : "\"" + escape(this.failure) + "\"").append("\n}\n");
		return b.toString();
	}

	static String escape(String s) {
		StringBuilder b = new StringBuilder(s.length() + 8);
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '\\' -> b.append("\\\\");
				case '"' -> b.append("\\\"");
				case '\n' -> b.append("\\n");
				case '\r' -> b.append("\\r");
				case '\t' -> b.append("\\t");
				default -> {
					if (c < 0x20) {
						b.append(String.format("\\u%04x", (int) c));
					} else {
						b.append(c);
					}
				}
			}
		}
		return b.toString();
	}
}
