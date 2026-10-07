package dev.voidmc.hearth.client.map.atlas;

import dev.voidmc.atlas.api.AtlasMarkers;
import dev.voidmc.atlas.api.AtlasPlugin;
import dev.voidmc.atlas.api.MapLayer;
import dev.voidmc.atlas.api.Marker;
import dev.voidmc.atlas.api.MarkerDetail;
import dev.voidmc.atlas.api.MarkerGlyph;
import dev.voidmc.atlas.api.MarkerQuery;
import dev.voidmc.atlas.api.Source;
import dev.voidmc.atlas.api.Tier;
import dev.voidmc.hearth.client.map.MapClient;
import dev.voidmc.hearth.client.quest.QuestClient;
import dev.voidmc.hearth.map.MapPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Hearth on VOID Atlas's map (the {@code "atlas"} entrypoint; Fabric only loads it when Atlas is installed, and it is only
 * compiled when Atlas's build is at hand, see build.gradle). Two providers, both handing over what the client already has:
 *
 * <ul>
 *   <li>{@code hearth:settlements} — the settlements the player knows of ({@link MapClient}, sent by the server's map
 *   module): a "?" while only sighted, then a settlement tile with its name, folk, tier and culture and the player's
 *   own standing and title there;</li>
 *   <li>{@code hearth:quest} — where the tracked quest wants the player now, with its open objectives.</li>
 * </ul>
 *
 * The markers are made again only when the list (or the language) changes; the quest marker when the quests do.
 */
public final class HearthAtlas implements AtlasPlugin {
	public static final String SETTLEMENTS = "hearth:settlements";
	public static final String QUESTS = "hearth:quests";
	private static final String OVERWORLD = "minecraft:overworld";
	/** Atlas's ochre waypoint colour, the closest to the quest HUD's gold. */
	private static final int QUEST_COLOUR = 0xC9A66B;

	private List<Marker> made = List.of();
	private long madeVersion = -1;
	private String madeLanguage = "";
	private Object questFrom;
	private Marker quest;

	@Override
	public void onAtlasReady(AtlasMarkers markers) {
		registerLayers(markers);
		// the language is not loaded yet when Atlas starts: name the chips again once in a world
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> registerLayers(markers)));
		markers.registerProvider(SETTLEMENTS, 0, this::settlements);
		markers.registerProvider("hearth:quest", 10, this::quest);
	}

	private static void registerLayers(AtlasMarkers markers) {
		markers.registerLayer(new MapLayer(SETTLEMENTS, tr("hearth.map.layer.settlements"), true, 15));
		markers.registerLayer(new MapLayer(QUESTS, tr("hearth.map.layer.quests"), true, 16));
	}

	// ---------------------------------------------------------------- settlements

	private void settlements(MarkerQuery q, Consumer<Marker> out) {
		long version = MapClient.version();
		String language = Minecraft.getInstance().getLanguageManager().getSelected();
		if (version != this.madeVersion || !language.equals(this.madeLanguage)) {
			List<Marker> list = new ArrayList<>();
			for (MapPayload.Entry e : MapClient.settlements()) {
				list.add(marker(e));
			}
			this.made = list;
			this.madeVersion = version;
			this.madeLanguage = language;
		}
		boolean all = q.purpose() == MarkerQuery.Purpose.LIST;
		for (Marker m : this.made) {
			if (m.dimension().equals(q.dimension()) && (all || q.contains(m.x(), m.z()))) {
				out.accept(m);
			}
		}
	}

	static Marker marker(MapPayload.Entry e) {
		String id = "hearth:settlement:" + e.id();
		Source source = Source.plugin(SETTLEMENTS);
		if (!e.discovered()) {
			return new Marker(id, SETTLEMENTS, tr("hearth.map.sighted"), "", tr("hearth.map.subtitle.sighted"), e.x() + 0.5, e.y(), e.z() + 0.5,
				e.dimension(), MarkerGlyph.UNKNOWN, Marker.Style.STRUCTURE, 0, null, source, Tier.MAJOR);
		}
		String tier = tr("hearth.tier." + e.tier());
		String subtitle = Component.translatable("hearth.map.subtitle", tier.toUpperCase(Locale.ROOT)).getString();
		List<MarkerDetail.Readout> readouts = List.of(new MarkerDetail.Readout(String.valueOf(e.population()), tr("hearth.map.folk")));
		MarkerDetail.Meter meter = null;
		List<MarkerDetail.Row> rows = new ArrayList<>();
		rows.add(new MarkerDetail.Row(tier, tr("hearth.map.tag.tier"), false));
		if (!e.culture().isEmpty()) {
			rows.add(new MarkerDetail.Row(tr("hearth.culture." + e.culture()), tr("hearth.map.tag.culture"), false));
		}
		if (!e.title().isEmpty()) {
			String phrase = Component.translatable("hearth.standing.title_phrase." + e.title(), e.name()).getString();
			meter = new MarkerDetail.Meter(tr("hearth.map.standing"), e.standing() > 0 ? "+" + e.standing() : Integer.toString(e.standing()), -100, 100,
				e.standing(), capitalised(phrase));
			rows.add(new MarkerDetail.Row(capitalised(phrase), tr("hearth.map.tag.title"), true));
		}
		if (e.owner()) {
			rows.add(new MarkerDetail.Row(tr("hearth.map.row.owner"), tr("hearth.map.tag.property"), true));
		}
		MarkerDetail detail = new MarkerDetail(readouts, meter, List.of(new MarkerDetail.Section(tr("hearth.map.section"), rows)));
		return new Marker(id, SETTLEMENTS, e.name(), String.valueOf(e.population()), subtitle, e.x() + 0.5, e.y(), e.z() + 0.5, e.dimension(),
			glyph(e.tier()), Marker.Style.SETTLEMENT, 0, detail, source, Tier.MAJOR);
	}

	/** A house for a camp or hamlet, a village for a village, a walled keep for a town or city. */
	static MarkerGlyph glyph(String tier) {
		return switch (tier) {
			case "village" -> MarkerGlyph.VILLAGE;
			case "town", "city" -> MarkerGlyph.KEEP;
			default -> MarkerGlyph.HOUSE;
		};
	}

	// ---------------------------------------------------------------- the tracked quest

	private void quest(MarkerQuery q, Consumer<Marker> out) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		// the synced quests are replaced (not edited) when they change, so their identity says whether to remake the marker
		Object from = player.getAttached(dev.voidmc.hearth.quest.service.QuestStore.PLAYER);
		if (from != this.questFrom) {
			this.questFrom = from;
			this.quest = questMarker(QuestClient.tracked(player));
		}
		Marker m = this.quest;
		if (m != null && m.dimension().equals(q.dimension())) {
			out.accept(m); // always: the minimap's rim and the list want it however far it is
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
		String subtitle = tr(t.handIn() ? "hearth.map.quest.subtitle.hand_in" : "hearth.map.quest.subtitle");
		// Hearth's quests are all in the overworld, where its settlements are
		return new Marker("hearth:quest:" + t.id(), QUESTS, t.title(), t.settlement(), subtitle, t.target().getX() + 0.5, t.target().getY(),
			t.target().getZ() + 0.5, OVERWORLD, MarkerGlyph.TRACK, Marker.Style.WAYPOINT, QUEST_COLOUR, detail, Source.plugin("hearth:quest"), Tier.MAJOR);
	}

	// ---------------------------------------------------------------- words

	private static String tr(String key) {
		return Component.translatable(key).getString();
	}

	private static String capitalised(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
