package dev.voidmc.expanse.client.atlas;

import dev.voidmc.atlas.api.AtlasMarkers;
import dev.voidmc.atlas.api.AtlasPlaces;
import dev.voidmc.atlas.api.AtlasPlugin;
import dev.voidmc.atlas.api.MapLayer;
import dev.voidmc.atlas.api.DiscoveryState;
import dev.voidmc.atlas.api.Marker;
import dev.voidmc.atlas.api.MarkerDetail;
import dev.voidmc.atlas.api.MarkerGlyph;
import dev.voidmc.atlas.api.Place;
import dev.voidmc.atlas.api.PlaceLook;
import dev.voidmc.atlas.api.Source;
import dev.voidmc.atlas.api.Tier;
import dev.voidmc.expanse.api.ExpanseSights;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;

/**
 * Expanse on the VOID Atlas map (the {@code "atlas"} entrypoint, called only when Atlas is installed): in an Expanse
 * world the map writes the lands' own names ({@link ExpanseNames}) in place of plain biome names, and the Expanse's own
 * structures (the chapels, keeps and gallows on the hills, the summit crosses, the pass refuges...) by names of their
 * own. A world is an Expanse world when its biomes include Expanse's (what the server sends, so this holds on servers
 * too); elsewhere the names are left to Atlas.
 *
 * <p>In singleplayer the great mines are reported too ({@link ExpanseSights#greatMines}): every few seconds the mines
 * within a few kilometres of the player are looked up off the client thread, and each new one is reported as an unknown
 * place, which Atlas then sights and discovers by distance as its own. The pithead is a mineshaft on the map, its card
 * telling how deep the shaft goes, its levels and where it breaks into the realm; the great drift's mouth in the realm
 * is a place of its own underground, at its depth. Both go on the map's "Great mines" layer.
 */
public final class ExpanseAtlas implements AtlasPlugin {
	/** How far round the player to look for great mines (blocks). */
	private static final int MINE_REACH = 3000;
	private static final String PROVIDER = "expanse:great_mines";
	private static final String LAYER = "expanse:mines";

	private static volatile boolean expanseWorld;
	private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
	private static final AtomicBoolean LOOKING = new AtomicBoolean();
	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Expanse Atlas mines");
		t.setDaemon(true);
		return t;
	});
	private static int ticks;

	@Override
	public void onAtlasReady(AtlasMarkers markers) {
		// the mines on a layer of their own, behind Atlas's "More" chip, so a player can switch them off apart
		markers.registerLayer(new MapLayer(LAYER, net.minecraft.client.resources.language.I18n.get("expanse.atlas.layer.mines"), true, 60));
	}

	@Override
	public void onAtlasPlaces(AtlasPlaces places) {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			expanseWorld = handler.registryAccess().lookupOrThrow(Registries.BIOME).keySet().stream().anyMatch(id -> id.getNamespace().equals("expanse"));
			REPORTED.clear();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> expanseWorld = false);
		places.registerNameProvider("expanse:names", 0, q -> !expanseWorld ? null
			: q.isStructure() ? ExpanseNames.structure(q.structure(), q.biome(), q.x(), q.z(), q.seedHash())
			: ExpanseNames.name(q.kind(), q.biome(), q.x(), q.z(), q.area(), q.seedHash()));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!expanseWorld || client.player == null || ++ticks % 100 != 0) {
				return;
			}
			var server = client.getSingleplayerServer();
			if (server == null || !client.player.level().dimension().equals(net.minecraft.world.level.Level.OVERWORLD) || !LOOKING.compareAndSet(false, true)) {
				return;
			}
			ServerLevel level = server.overworld();
			int px = client.player.getBlockX();
			int pz = client.player.getBlockZ();
			long seedHash = level.getSeed() * 0x9E3779B97F4A7C15L;
			WORKER.execute(() -> {
				try {
					List<ExpanseSights.GreatMine> found = ExpanseSights.greatMines(level, px, pz, MINE_REACH);
					List<Runnable> reports = new ArrayList<>();
					for (ExpanseSights.GreatMine mine : found) {
						String id = "expanse:great_mine/" + mine.pithead().getX() + "," + mine.pithead().getZ();
						if (REPORTED.add(id)) {
							reports.add(() -> report(places, id, mine, seedHash));
						}
					}
					if (!reports.isEmpty()) {
						Minecraft.getInstance().execute(() -> reports.forEach(Runnable::run));
					}
				} finally {
					LOOKING.set(false);
				}
			});
		});
	}

	/** The pithead as a place on the surface, and the great drift's mouth as one underground. */
	private static void report(AtlasPlaces places, String id, ExpanseSights.GreatMine mine, long seedHash) {
		var pit = mine.pithead();
		String name = ExpanseNames.structure("expanse:great_mine", "", pit.getX(), pit.getZ(), seedHash);
		int deep = pit.getY() - mine.shaftBottom();
		Source source = Source.plugin(PROVIDER);
		places.report(new Place(id, "expanse:great_mine", name, "SIGHT", Tier.MAJOR, DiscoveryState.UNKNOWN, source, pit.getX(), pit.getY(),
			pit.getZ(), "minecraft:overworld", false, 12, 0, 0));
		List<MarkerDetail.Row> rows = new ArrayList<>();
		rows.add(new MarkerDetail.Row("Shaft " + deep + " blocks deep, ladder on the north wall", null, false));
		for (int level : mine.levels()) {
			rows.add(new MarkerDetail.Row(level == mine.shaftBottom() ? "Great drift to the realm, y " + level : "Level at y " + level, null,
				level == mine.shaftBottom()));
		}
		places.look(id, new PlaceLook(MarkerGlyph.MINESHAFT, Marker.Style.STRUCTURE, "y " + pit.getY(), "Great mine",
			new MarkerDetail(List.of(new MarkerDetail.Readout(String.valueOf(deep), "deep"), new MarkerDetail.Readout(String.valueOf(mine.levels().size()),
				"levels")), null, List.of(new MarkerDetail.Section("The workings", rows)))).onLayer(LAYER));
		// the great drift's mouth, high in the realm's wall
		var mouth = mine.realmMouth();
		String mouthId = id + "/mouth";
		places.report(new Place(mouthId, "expanse:mine_mouth", name + " (the deep drift)", "SIGHT", Tier.MINOR, DiscoveryState.UNKNOWN, source,
			mouth.getX(), mouth.getY(), mouth.getZ(), "minecraft:overworld", true, 8, 0, 0));
		places.look(mouthId, new PlaceLook(MarkerGlyph.CAVE, Marker.Style.STRUCTURE, "y " + mouth.getY(), "Mine drift", null).onLayer(LAYER));
	}
}
