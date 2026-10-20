package dev.voidmc.hearth.client.overlay;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;

/**
 * Extra top-level sections of {@link HearthState}'s tree (docs/LIVING_WORLD_2.md §10, §11.2): B2's {@code people}, B4's
 * {@code household}. Each section builds its own JSON from what the server last sent and adds what it reads to the
 * change counter. Sections are additions only, so hosts that do not know a key ignore it. Register from client init;
 * client thread.
 */
public final class HearthStateSections {
	public interface Section {
		/** The section's JSON while in a world (JsonNull when there is nothing to show). */
		JsonElement build(Minecraft mc);

		/** A cheap value that changes whenever {@link #build} may have changed (e.g. its payload's identity hash). */
		long fingerprint(Minecraft mc);
	}

	private static final Map<String, Section> SECTIONS = new LinkedHashMap<>();

	private HearthStateSections() {
	}

	public static synchronized void register(String key, Section section) {
		if (SECTIONS.putIfAbsent(key, section) != null) {
			throw new IllegalStateException("hearth state section registered twice: " + key);
		}
	}

	static synchronized long fingerprint(long h, Minecraft mc) {
		for (Section s : SECTIONS.values()) {
			h = RevisionCounter.mix(h, s.fingerprint(mc));
		}
		return h;
	}

	/** Adds each section to the tree: built online, null offline. */
	static synchronized void addTo(JsonObject root, Minecraft mc, boolean online) {
		for (Map.Entry<String, Section> e : SECTIONS.entrySet()) {
			root.add(e.getKey(), online ? e.getValue().build(mc) : JsonNull.INSTANCE);
		}
	}
}
