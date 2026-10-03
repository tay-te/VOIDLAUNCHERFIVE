package dev.voidmc.hearth.client.npc;

import dev.voidmc.hearth.api.QuestService;
import dev.voidmc.hearth.npc.talk.TalkPayloads;
import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;

/**
 * The quest markers this player sees over folk (the server works them out per player and sends only changes). Client
 * thread only; cleared on disconnect.
 */
final class FolkMarkers {
	private static final Int2ByteOpenHashMap MARKERS = new Int2ByteOpenHashMap();

	private FolkMarkers() {
	}

	static void accept(TalkPayloads.Markers payload) {
		for (int i = 0; i < payload.entities().length; i++) {
			if (payload.markers()[i] == 0) {
				MARKERS.remove(payload.entities()[i]);
			} else {
				MARKERS.put(payload.entities()[i], payload.markers()[i]);
			}
		}
	}

	static QuestService.Marker of(int entityId) {
		int v = MARKERS.get(entityId);
		QuestService.Marker[] all = QuestService.Marker.values();
		return v > 0 && v < all.length ? all[v] : QuestService.Marker.NONE;
	}

	static void clear() {
		MARKERS.clear();
	}
}
