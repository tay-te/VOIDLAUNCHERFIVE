package dev.voidmc.mettle.client.overlay;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Where Mettle meets the Ultralight overlay (docs/OVERLAY.md). Java pushes named events ({@code mettle},
 * {@code mettleResult}, {@code mettleFeedback}); the overlay calls back through the actions in {@code MettleActions}.
 * Until the overlay exists nothing listens and the native screens show the same information.
 */
public final class OverlayBridge {
	private static final List<BiConsumer<String, JsonObject>> LISTENERS = new ArrayList<>();

	private OverlayBridge() {
	}

	/** The overlay registers here when it starts. */
	public static void listen(BiConsumer<String, JsonObject> listener) {
		LISTENERS.add(listener);
	}

	public static boolean hasListener() {
		return !LISTENERS.isEmpty();
	}

	public static void push(String event, JsonObject payload) {
		for (BiConsumer<String, JsonObject> l : LISTENERS) {
			l.accept(event, payload);
		}
	}
}
