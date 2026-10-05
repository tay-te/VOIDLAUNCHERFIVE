package dev.voidmc.atlas.gametest;

import dev.voidmc.atlas.api.AtlasWorlds;
import dev.voidmc.atlas.client.AtlasClient;
import dev.voidmc.atlas.client.Perf;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

/**
 * The soak and world-switch test ({@code ./gradlew atlasSoak}): Atlas is played with for a long while the way people
 * do, and must not mix two worlds, grow without bound, or hitch.
 *
 * <ol>
 *   <li>Two dedicated servers in turn, behind the same address, with different seeds (a proxy's two worlds): joined,
 *   flown over, left; the first joined again. Each must get its own map, the first its old map back, and the two
 *   maps' files must stay apart. This exercises the client-only path (a real connection, no integrated server).</li>
 *   <li>A long singleplayer session: a flight with many dimension changes (Overworld, Nether, End and back), the map
 *   opened and closed, the world left and joined again; then memory after a collection, and the worst frame Atlas
 *   spent more than 33 ms of its own on.</li>
 * </ol>
 *
 * Writes {@code build/soak/soak.json}; the gradle task fails on any broken assertion.
 */
final class Soak {
	private static final Path OUT = Path.of(System.getProperty("atlas.test.out", "build/soak/soak.json"));
	private static final int SOAK_SECONDS = Integer.getInteger("atlas.soak.seconds", 240);

	private final List<String> failures = new ArrayList<>();
	private final List<String> notes = new ArrayList<>();

	void run(ClientGameTestContext context) {
		String idA = null;
		String idB = null;
		String idA2 = null;
		// 1. two worlds behind one address
		try {
			idA = this.serverWorld(context, "atlas-soak-a", 0);
			idB = this.serverWorld(context, "atlas-soak-b", 1);
			idA2 = this.serverWorld(context, "atlas-soak-a", 2);
		} catch (RuntimeException e) {
			this.failures.add("dedicated server part failed: " + e);
		}
		this.note("worlds: first " + idA + ", second " + idB + ", first again " + idA2);
		if (idA == null || idB == null || idA2 == null) {
			this.failures.add("a server world was never told apart");
		} else {
			if (idA.equals(idB)) {
				this.failures.add("two different worlds behind one address got the same map (" + idA + ")");
			}
			if (!idA.equals(idA2)) {
				this.failures.add("joining the first world again made a new map (" + idA2 + ") instead of " + idA);
			}
		}
		// 2. a long singleplayer session
		this.singleplayer(context);
		this.write();
	}

	/** Joins a dedicated server with a seed, flies a little, leaves; returns the map Atlas chose. */
	private String serverWorld(ClientGameTestContext context, String seed, int round) {
		Properties props = new Properties();
		props.setProperty("level-seed", seed);
		props.setProperty("online-mode", "false");
		props.setProperty("spawn-protection", "0");
		String id;
		try (TestDedicatedServerContext server = context.worldBuilder().createServer(props)) {
			try (TestDedicatedServerConnection connection = server.connect()) {
				connection.waitForChunksRender();
				server.runCommand("gamemode spectator @a");
				id = this.waitForWorld(context);
				// a little flight so there is a map to tell it apart by next time
				for (int i = 0; i < 6; i++) {
					server.runCommand(String.format(Locale.ROOT, "tp @a %d 140 %d", i * 48, i * 16));
					context.waitTicks(20);
				}
				context.waitTicks(60);
				String address = context.computeOnClient(mc -> mc.getCurrentServer() == null ? "?" : mc.getCurrentServer().ip);
				this.note("round " + round + " (" + seed + ") on " + address + ": map " + id);
				// the map's folder must be under the server's, apart from the others
				String key = context.computeOnClient(mc -> AtlasClient.get().session() == null ? "" : AtlasClient.get().session().worldKey);
				if (!key.endsWith("/" + id)) {
					this.failures.add("the open map " + key + " is not " + id);
				}
			}
		}
		context.waitTicks(20);
		return id;
	}

	/** Waits (up to 15 s) until Atlas has decided which map this level is; its id. */
	private String waitForWorld(ClientGameTestContext context) {
		for (int i = 0; i < 300; i++) {
			String id = context.computeOnClient(mc -> {
				var w = AtlasClient.get().worlds;
				if (w.finding()) {
					return null;
				}
				return w.current().map(AtlasWorlds.WorldInfo::id).orElse(null);
			});
			if (id != null) {
				return id;
			}
			context.waitTicks(1);
		}
		return null;
	}

	private void singleplayer(ClientGameTestContext context) {
		Runtime rt = Runtime.getRuntime();
		long[] heap = new long[2];
		double worstAtlas = 0;
		int frames = 0;
		for (int session = 0; session < 2; session++) {
			try (TestSingleplayerContext world = context.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(s -> {
					s.setSeed("atlas-soak");
					s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
					s.setAllowCommands(true);
				})
				.create()) {
				world.getServer().runCommand("gamemode spectator @a");
				world.getServer().runCommand("gamerule send_command_feedback false");
				context.waitTicks(40);
				if (session == 1) {
					System.gc();
					heap[0] = rt.totalMemory() - rt.freeMemory();
				}
				context.runOnClient(mc -> Perf.startRecording());
				String[] dims = {"minecraft:overworld", "minecraft:the_nether", "minecraft:overworld", "minecraft:the_end", "minecraft:overworld"};
				long until = System.currentTimeMillis() + SOAK_SECONDS * 1000L / 2;
				int step = 0;
				while (System.currentTimeMillis() < until) {
					String dim = dims[step % dims.length];
					int x = step * 96;
					world.getServer().runCommand(String.format(Locale.ROOT, "execute in %s run tp @a %d 120 %d", dim, x, step * 24));
					context.waitTicks(40);
					if (step % 4 == 3) {
						// the map opened and closed now and then, as people do
						context.runOnClient(mc -> mc.gui.setScreen(new dev.voidmc.atlas.client.ui.AtlasScreen(AtlasClient.get())));
						context.waitTicks(20);
						context.runOnClient(mc -> mc.gui.setScreen(null));
					}
					step++;
				}
				Perf.Recording r = context.computeOnClient(mc -> Perf.stopRecording());
				for (int i = 0; i < r.count(); i++) {
					double atlas = (r.ingest()[i] + r.minimap()[i] + r.map()[i] + r.beams()[i] + r.radar()[i]) / 1e6;
					worstAtlas = Math.max(worstAtlas, atlas);
				}
				frames += r.count();
				this.note("session " + session + ": " + step + " stops, " + r.count() + " frames");
			}
			context.waitTicks(20);
		}
		System.gc();
		heap[1] = rt.totalMemory() - rt.freeMemory();
		long growth = (heap[1] - heap[0]) >> 20;
		this.note(String.format(Locale.ROOT, "heap after the second session %d MB (grew %d MB); worst Atlas frame %.2f ms of %d", heap[1] >> 20, growth, worstAtlas,
			frames));
		if (growth > 400) {
			this.failures.add("memory grew " + growth + " MB over a session (more than 400 MB)");
		}
		if (worstAtlas > 33) {
			this.failures.add(String.format(Locale.ROOT, "Atlas spent %.1f ms of one frame (more than 33 ms)", worstAtlas));
		}
		this.worstAtlas = worstAtlas;
		this.heapMb = heap[1] >> 20;
		this.growthMb = growth;
	}

	private double worstAtlas;
	private long heapMb;
	private long growthMb;

	private void note(String s) {
		this.notes.add(s);
		System.out.println("[atlas-soak] " + s);
	}

	private void write() {
		StringBuilder b = new StringBuilder("{\n  \"passed\": ").append(this.failures.isEmpty()).append(",\n  \"failures\": [");
		for (int i = 0; i < this.failures.size(); i++) {
			b.append(i == 0 ? "" : ", ").append('"').append(this.failures.get(i).replace("\"", "'")).append('"');
		}
		b.append("],\n  \"notes\": [");
		for (int i = 0; i < this.notes.size(); i++) {
			b.append(i == 0 ? "" : ", ").append('"').append(this.notes.get(i).replace("\"", "'")).append('"');
		}
		b.append(String.format(Locale.ROOT, "],\n  \"worstAtlasFrameMs\": %.3f,\n  \"heapMb\": %d,\n  \"heapGrowthMb\": %d\n}\n", this.worstAtlas, this.heapMb, this.growthMb));
		try {
			Files.createDirectories(OUT.toAbsolutePath().getParent());
			Files.writeString(OUT, b.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.out.println("[atlas-soak] could not write " + OUT + ": " + e);
		}
		for (String f : this.failures) {
			System.out.println("[atlas-soak] FAILED: " + f);
		}
	}
}
