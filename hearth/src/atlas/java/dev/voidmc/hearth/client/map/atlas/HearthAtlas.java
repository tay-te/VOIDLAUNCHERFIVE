package dev.voidmc.hearth.client.map.atlas;

import dev.voidmc.atlas.api.AtlasApi;
import dev.voidmc.atlas.api.AtlasHud;
import dev.voidmc.atlas.api.AtlasMarkers;
import dev.voidmc.atlas.api.AtlasPlaces;
import dev.voidmc.atlas.api.AtlasPlugin;
import dev.voidmc.atlas.api.DiscoveryState;
import dev.voidmc.atlas.api.MapLayer;
import dev.voidmc.atlas.api.Marker;
import dev.voidmc.atlas.api.MarkerDetail;
import dev.voidmc.atlas.api.MarkerGlyph;
import dev.voidmc.atlas.api.MarkerQuery;
import dev.voidmc.atlas.api.Place;
import dev.voidmc.atlas.api.PlaceLook;
import dev.voidmc.atlas.api.Source;
import dev.voidmc.atlas.api.Tier;
import dev.voidmc.hearth.client.map.MapClient;
import dev.voidmc.hearth.client.quest.QuestClient;
import dev.voidmc.hearth.client.quest.QuestHudHook;
import dev.voidmc.hearth.map.MapPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Hearth on VOID Atlas's map (the {@code "atlas"} entrypoint; Fabric only loads it when Atlas is installed, and it is only
 * compiled when Atlas's build is at hand, see build.gradle). Everything here hands Atlas what the client already has:
 *
 * <ul>
 *   <li><b>Settlements are Atlas places.</b> The place provider {@code hearth:settlements} reports the settlements round
 *   the player ({@link MapClient}, sent by the server's map module) with {@link DiscoveryState#UNKNOWN}, so Atlas judges
 *   them sighted ("?") and discovered by distance as its own places, keeps that with its map of the world, and makes the
 *   toasts and journal lines. Each has a {@link PlaceLook} (house / village / town glyph by tier, folk, tier, culture,
 *   the player's own standing meter and title), and an area claim so Atlas's own village sighting at a Hearth town is
 *   left out.</li>
 *   <li><b>The tracked quest is a tracked marker.</b> {@code hearth:quest} shows where the tracked quest wants the
 *   player now as a {@link Marker.Style#QUEST} marker (the step and its progress), and Atlas is asked to track it
 *   (compass, minimap rim, beam) whenever it appears or changes quest.</li>
 * </ul>
 *
 * Looks and markers are made again only when the synced list, the quests or the language change.
 */
public final class HearthAtlas implements AtlasPlugin {
	public static final String SETTLEMENTS = "hearth:settlements";
	public static final String QUESTS = "hearth:quests";
	/** The map layer (a chip in the layer row) the settlement places are drawn on, sighted and discovered alike. */
	public static final String SETTLEMENTS_LAYER = "hearth:settlements";
	private static final String HUD_QUEST = "hearth:quest";
	static final String PLACE_PREFIX = "hearth:settlement:";
	private static final String OVERWORLD = "minecraft:overworld";
	/** Polls (about a second each) between claiming every settlement again, in case Atlas opened another map. */
	private static final int RECLAIM_POLLS = 30;
	/** Polls between looks at Atlas's places for Hearth settlements remembered from earlier visits. */
	private static final int REMEMBERED_POLLS = 10;

	private AtlasMarkers markers;
	private AtlasPlaces places;

	// settlements: what Atlas has been told this connection
	private final Map<String, PlaceLook> looked = new HashMap<>();
	private final Map<String, MapPayload.Entry> claimed = new HashMap<>();
	private long syncedVersion = -1;
	private String syncedLanguage = "";
	private int polls;

	// the tracked quest
	private Object questFrom;
	private Marker quest;
	private String trackedId;
	private boolean trackPending;
	private boolean hudReserved;
	private int trackTries;
	private int ticks;

	@Override
	public void onAtlasReady(AtlasMarkers markers) {
		this.markers = markers;
		// a translation key: Atlas shows it in the player's language
		markers.registerLayer(new MapLayer(SETTLEMENTS_LAYER, "hearth.map.layer.settlements", true, 14));
		markers.registerLayer(new MapLayer(QUESTS, "hearth.map.layer.quests", true, 16));
		markers.registerProvider("hearth:quest", 10, this::quest);
		ClientTickEvents.END_CLIENT_TICK.register(client -> this.tick());
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(this::reset));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(this::reset));
	}

	@Override
	public void onAtlasPlaces(AtlasPlaces places) {
		this.places = places;
		places.registerPlaceProvider(SETTLEMENTS, 0, this::settlements);
	}

	/** A new connection: Atlas's claims are per map, and the quest is tracked afresh. */
	private void reset() {
		this.claimed.clear();
		this.syncedVersion = -1;
		this.questFrom = null;
		this.quest = null;
		this.trackedId = null;
		this.trackPending = false;
		QuestHudHook.setCompassCovered(false);
	}

	// ---------------------------------------------------------------- settlements

	/** Atlas's place poll (client thread, about once a second, only while a map of the world is open). */
	private void settlements(String dimension, Consumer<Place> out) {
		long version = MapClient.version();
		String language = Minecraft.getInstance().getLanguageManager().getSelected();
		boolean changed = version != this.syncedVersion || !language.equals(this.syncedLanguage);
		boolean reclaim = ++this.polls % RECLAIM_POLLS == 0;
		List<MapPayload.Entry> list = MapClient.settlements();
		if (changed || reclaim) {
			for (MapPayload.Entry e : list) {
				String id = PLACE_PREFIX + e.id();
				this.setLook(id, look(e));
				MapPayload.Entry had = this.claimed.get(id);
				if (reclaim || had == null || had.x() != e.x() || had.z() != e.z() || had.radius() != e.radius() || !had.dimension().equals(e.dimension())) {
					this.places.claim(id, e.dimension(), e.x(), e.z(), e.radius() + 32);
					this.claimed.put(id, e);
				}
			}
			this.syncedVersion = version;
			this.syncedLanguage = language;
		}
		if (changed || this.polls % REMEMBERED_POLLS == 0) {
			this.remembered(dimension);
		}
		for (MapPayload.Entry e : list) {
			if (e.dimension().equals(dimension)) {
				out.accept(place(e));
			}
		}
	}

	private void setLook(String id, PlaceLook look) {
		if (!Objects.equals(this.looked.get(id), look)) {
			this.places.look(id, look);
			this.looked.put(id, look);
		}
	}

	/**
	 * Settlements Atlas remembers from earlier visits but the server has not sent this time (too far now): Atlas keeps
	 * their places, but not their looks across a restart, so they get a plain one from what the place says (its name and
	 * tier) until the server sends them again.
	 */
	private void remembered(String dimension) {
		for (Place p : this.places.places(dimension)) {
			if (p.id().startsWith(PLACE_PREFIX) && !this.looked.containsKey(p.id())) {
				String tier = p.kind().startsWith("hearth:") ? p.kind().substring("hearth:".length()) : "village";
				this.setLook(p.id(), new PlaceLook(glyph(tier), Marker.Style.SETTLEMENT, "", subtitle(tier), null, SETTLEMENTS_LAYER));
			}
		}
	}

	/** A settlement as an Atlas place, left to Atlas to judge sighted and discovered. */
	static Place place(MapPayload.Entry e) {
		// the source is left as Atlas's default: Atlas credits it to the provider ("HEARTH")
		return new Place(PLACE_PREFIX + e.id(), "hearth:" + e.tier(), e.name(), "VILLAGE", Tier.MAJOR, DiscoveryState.UNKNOWN, null, e.x(), e.y(), e.z(),
			e.dimension(), false, e.radius(), 0, 0);
	}

	/** How a discovered settlement looks: its tile, folk, and a card with its tier, culture and the player's standing. */
	static PlaceLook look(MapPayload.Entry e) {
		String tier = tr("hearth.tier." + e.tier());
		List<MarkerDetail.Readout> readouts = List.of(new MarkerDetail.Readout(String.valueOf(e.population()), tr("hearth.map.folk")));
		MarkerDetail.Meter meter = null;
		List<MarkerDetail.Row> rows = new ArrayList<>();
		// most important first: Atlas drops the last rows of a tall card (population is the readout, standing the meter)
		rows.add(new MarkerDetail.Row(tier, tr("hearth.map.tag.tier"), false));
		if (!e.title().isEmpty()) {
			String phrase = Component.translatable("hearth.standing.title_phrase." + e.title(), e.name()).getString();
			meter = new MarkerDetail.Meter(tr("hearth.map.standing"), e.standing() > 0 ? "+" + e.standing() : Integer.toString(e.standing()), -100, 100,
				e.standing(), capitalised(phrase));
			rows.add(new MarkerDetail.Row(capitalised(phrase), tr("hearth.map.tag.title"), true));
		}
		if (!e.culture().isEmpty()) {
			rows.add(new MarkerDetail.Row(tr("hearth.culture." + e.culture()), tr("hearth.map.tag.culture"), false));
		}
		if (e.owner()) {
			rows.add(new MarkerDetail.Row(tr("hearth.map.row.owner"), tr("hearth.map.tag.property"), true));
		}
		MarkerDetail detail = new MarkerDetail(readouts, meter, List.of(new MarkerDetail.Section(tr("hearth.map.section"), rows)));
		return new PlaceLook(glyph(e.tier()), Marker.Style.SETTLEMENT, String.valueOf(e.population()), subtitle(e.tier()), detail, SETTLEMENTS_LAYER);
	}

	private static String subtitle(String tier) {
		return Component.translatable("hearth.map.subtitle", tr("hearth.tier." + tier).toUpperCase(Locale.ROOT)).getString();
	}

	/**
	 * A house for a camp or hamlet, a village for a village, a town (roofs behind a wall) for a town or city. Atlas's keep
	 * stays for castles and seats, once Hearth has them as places of their own.
	 */
	static MarkerGlyph glyph(String tier) {
		return switch (tier) {
			case "village" -> MarkerGlyph.VILLAGE;
			case "town", "city" -> MarkerGlyph.TOWN;
			default -> MarkerGlyph.HOUSE;
		};
	}

	// ---------------------------------------------------------------- the tracked quest

	private void quest(MarkerQuery q, Consumer<Marker> out) {
		Marker m = this.currentQuest();
		if (m != null && m.dimension().equals(q.dimension())) {
			out.accept(m); // always: the minimap's rim and the list want it however far it is
		}
	}

	/** The tracked quest's marker, made again when the synced quests change (they are replaced, not edited). */
	private Marker currentQuest() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return null;
		}
		Object from = player.getAttached(dev.voidmc.hearth.quest.service.QuestStore.PLAYER);
		if (from != this.questFrom) {
			this.questFrom = from;
			this.quest = questMarker(QuestClient.tracked(player));
		}
		return this.quest;
	}

	/**
	 * Asks Atlas to track the quest's marker when it appears or becomes another quest's, and lets it go when it is gone.
	 * Once tracked, it stays so until the player tracks something else on the map. Twice a second at most.
	 */
	private void tick() {
		if (!this.hudReserved && AtlasApi.available()) {
			this.hudReserved = true;
			// the quest panel's room, asked again by Atlas each frame (null: no panel is showing)
			AtlasApi.get().hud().reserve(HUD_QUEST, () -> {
				QuestHudHook.Rect r = QuestHudHook.rect();
				return r == null ? null : AtlasHud.Rect.of(r.x0(), r.y0(), r.x1() - r.x0(), r.y1() - r.y0());
			});
		}
		if (this.markers == null || ++this.ticks % 10 != 0) {
			return;
		}
		Marker m = this.currentQuest();
		String want = m == null ? null : m.id();
		if (!Objects.equals(want, this.trackedId)) {
			if (this.trackedId != null) {
				this.markers.untrack(this.trackedId);
			}
			this.trackedId = want;
			this.trackPending = want != null;
			this.trackTries = 0;
			QuestHudHook.setCompassCovered(false);
		}
		if (this.trackPending) {
			// false while Atlas cannot see the marker yet (another dimension, no map open); after a while, try less often
			this.trackTries++;
			if (this.trackTries <= 20 || this.trackTries % 20 == 0) {
				this.trackPending = !this.markers.track(want);
				// Atlas's compass and beam now point at the quest: the HUD panel keeps its title and objectives only
				QuestHudHook.setCompassCovered(!this.trackPending);
			}
		}
	}

	private static Marker questMarker(QuestClient.Tracked t) {
		if (t == null || t.target() == null) {
			return null;
		}
		List<MarkerDetail.Row> rows = new ArrayList<>();
		for (int i = 0; i < t.objectives().size(); i++) {
			rows.add(new MarkerDetail.Row(t.objectives().get(i), "", i == 0));
		}
		MarkerDetail detail = new MarkerDetail(List.of(), null,
			List.of(new MarkerDetail.Section(tr(t.handIn() ? "hearth.map.quest.hand_in" : "hearth.map.quest.objectives"), rows)));
		String subtitle = Component.translatable(t.handIn() ? "hearth.map.quest.subtitle.hand_in" : "hearth.map.quest.subtitle",
			t.title().toUpperCase(Locale.ROOT)).getString();
		String progress = t.count() > 1 ? t.progress() + " / " + t.count() : "";
		// Hearth's quests are all in the overworld, where its settlements are
		return new Marker("hearth:quest:" + t.id(), QUESTS, t.step(), progress, subtitle, t.target().getX() + 0.5, t.target().getY(),
			t.target().getZ() + 0.5, OVERWORLD, MarkerGlyph.QUEST, Marker.Style.QUEST, 0, detail, Source.plugin("hearth:quest"), Tier.MAJOR);
	}

	// ---------------------------------------------------------------- words

	private static String tr(String key) {
		return Component.translatable(key).getString();
	}

	private static String capitalised(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
