package dev.voidmc.hub.hearth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.voidmc.hub.sync.StateSync;
import dev.voidmc.hub.ui.HubView;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;

/**
 * Keeps the page's copy of Hearth's state current ({@link StateSync} with Hearth as its source): the render thread checks
 * {@code HearthState.revision()} once a tick, Hearth builds its tree only when that moved, a worker writes and diffs it,
 * and the page gets {@code {"type":"hearth","state":...}} only when it differs from what the page holds. The page
 * outlives the screens, so reopening with nothing changed sends nothing.
 *
 * <p>The last tree built is kept for the hub's own Java (the native minigame parts and the event screens' "the server
 * ended it" check) so it never builds the state twice. Render thread.
 */
public final class HearthSync {

	/** Jail seconds count down in the state once a second; the page does not draw them (the law HUD stays native). */
	private static final Pattern AGES = Pattern.compile("\"jailSeconds\":-?\\d+(?:\\.\\d+)?");

	private static JsonObject last;
	/** When {@link #last} was built (nanoTime). */
	private static long lastAt;

	private static final StateSync SYNC = new StateSync("Hearth", "hearth", new StateSync.Source() {
		@Override
		public long signature(Minecraft mc) {
			return HearthLink.revision();
		}

		@Override
		public int iconEpoch() {
			return iconEpoch;
		}

		@Override
		public Object stateTree() {
			Object tree = HearthLink.stateTree();
			if (tree instanceof JsonObject o) {
				last = o;
				lastAt = System.nanoTime();
			}
			return tree;
		}

		@Override
		public String transform(String json) {
			return portraits(json);
		}
	}, AGES, null, Long.MAX_VALUE);

	/** Hearth's 16 x 16 portraits, inline as PNG data URLs. */
	private static final Pattern PORTRAIT = Pattern.compile("\"portrait\":\"data:image/png;base64,([A-Za-z0-9+/=]{8,20000})\"");
	/** Portraits enlarged, by their source text (worker thread; a face is the same face every time). */
	private static final java.util.Map<String, String> BIG = new java.util.concurrent.ConcurrentHashMap<>();
	/** Whole multiple the 16 px portraits are enlarged by: 192 px, never enlarged again by the page (it shows 48-168). */
	static final int PORTRAIT_SCALE = 12;

	/**
	 * Pixel art pre-scaled nearest-neighbour (docs/HUB_UI.md): Ultralight smooths images it enlarges and has no
	 * {@code image-rendering: pixelated}, so each portrait reaches the page as a big crisp copy it only ever shrinks.
	 */
	public static String portraits(String json) {
		java.util.regex.Matcher m = PORTRAIT.matcher(json);
		if (!m.find()) {
			return json;
		}
		StringBuilder out = new StringBuilder(json.length() + 4096);
		m.reset();
		while (m.find()) {
			String src = m.group(1);
			String big = BIG.computeIfAbsent(src, HearthSync::enlarge);
			m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement("\"portrait\":\"data:image/png;base64," + big + "\""));
		}
		m.appendTail(out);
		if (BIG.size() > 512) {
			BIG.clear();  // a long session meets many faces: start the cache again rather than grow
		}
		return out.toString();
	}

	private static String enlarge(String base64) {
		try {
			byte[] png = java.util.Base64.getDecoder().decode(base64);
			try (com.mojang.blaze3d.platform.NativeImage src = com.mojang.blaze3d.platform.NativeImage.read(png)) {
				int w = src.getWidth();
				int h = src.getHeight();
				int k = PORTRAIT_SCALE;
				try (com.mojang.blaze3d.platform.NativeImage big = new com.mojang.blaze3d.platform.NativeImage(w * k, h * k, false)) {
					for (int y = 0; y < h * k; y++) {
						for (int x = 0; x < w * k; x++) {
							big.setPixel(x, y, src.getPixel(x / k, y / k));
						}
					}
					java.nio.file.Path tmp = java.nio.file.Files.createTempFile("voidhub-portrait", ".png");
					try {
						big.writeToFile(tmp);
						return java.util.Base64.getEncoder().encodeToString(java.nio.file.Files.readAllBytes(tmp));
					} finally {
						java.nio.file.Files.deleteIfExists(tmp);
					}
				}
			}
		} catch (Exception | LinkageError e) {
			return base64;  // unreadable: the small one, smoothed by the page
		}
	}

	/** Changes with every resource reload: the state is built and its pictures made again. */
	private static volatile int iconEpoch;

	/** Hearth's icons' epoch (tests). */
	public static int iconEpoch() {
		return iconEpoch;
	}

	/**
	 * A resource reload (F3+T, a pack change): Hearth's pictures (the enlarged portraits) are made again and the state is
	 * rebuilt with the new epoch. Render thread.
	 */
	public static void clearIcons() {
		BIG.clear();
		iconEpoch++;
	}

	/** Tests and the PERF lines. */
	public static volatile int builds;
	public static volatile int sends;

	private HearthSync() {
	}

	/** One frame (render thread): delivers what the worker finished; once per tick ({@code check}) rebuilds if Hearth moved. */
	public static void frame(HubView hub, boolean check) {
		if (!HearthLink.present()) {
			return;
		}
		SYNC.frame(hub, check);
		builds = SYNC.builds;
		sends = SYNC.sends;
	}

	/**
	 * The current state without a page (render thread): rebuilt now if Hearth's counter moved since the last build. Used
	 * by event screens before their first frame and by the native parts; never sends anything.
	 */
	public static JsonObject state() {
		return last;
	}

	/** When the last state was built (nanoTime), for animating between discrete updates. */
	public static long stateAt() {
		return lastAt;
	}

	/** One section of the last state ({@code "dice"}, {@code "talk"}...), or null when absent or null. */
	public static JsonObject section(String name) {
		JsonObject s = last;
		if (s == null) {
			return null;
		}
		JsonElement e = s.get(name);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
	}

	/** Forgets what the page holds (tests; a page that lost its state). */
	public static void reset() {
		SYNC.reset();
	}
}
