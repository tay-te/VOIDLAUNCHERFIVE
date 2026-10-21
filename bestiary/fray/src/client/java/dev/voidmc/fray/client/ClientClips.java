package dev.voidmc.fray.client;

import dev.voidmc.fray.Fray;
import dev.voidmc.fray.motion.Clip;
import dev.voidmc.fray.motion.ClipFiles;
import dev.voidmc.fray.motion.ClipJson;
import dev.voidmc.fray.motion.FrayHandshake;
import dev.voidmc.fray.motion.MotionPayloads;
import dev.voidmc.fray.motion.Rig;
import dev.voidmc.fray.motion.RigJson;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The rigs and clips this client draws, loaded from the mods' own jars ({@code assets/<ns>/fray/rig},
 * {@code assets/<ns>/fray/clip}), indexed in id order (the order the server's {@code motion/play} indices use) and
 * hashed exactly as loaded: the hash the client sends at login is of the bytes it will actually play (a clip's entry
 * includes its custom rig's hash, since the server's hit volumes came from that rig).
 */
public final class ClientClips {
	private static Map<Identifier, Clip> clips = Map.of();
	private static List<Identifier> order = List.of();
	private static String setHash = "";
	/** Called when the set changes (the studio's reload: renderers rebuild custom rig models). */
	public static final List<Consumer<Map<Identifier, Rig>>> ON_RIGS = new CopyOnWriteArrayList<>();

	private ClientClips() {
	}

	/** (Re)loads rigs and clips; {@code overrides} replaces some clip files' bytes (game tests: a client whose clip differs). */
	public static void load(@Nullable Map<Identifier, byte[]> overrides) {
		Map<Identifier, Rig> rigs = new LinkedHashMap<>();
		for (Map.Entry<Identifier, byte[]> e : ClipFiles.scan("assets", "fray/rig").entrySet()) {
			Rig rig = RigJson.parse(new String(e.getValue(), StandardCharsets.UTF_8), ClipJson.sha256(e.getValue()));
			RigJson.register(rig);
			rigs.put(rig.id, rig);
		}
		Map<Identifier, byte[]> files = ClipFiles.scan("assets", "fray/clip");
		if (overrides != null) {
			files.putAll(overrides);
		}
		Map<Identifier, Clip> map = new LinkedHashMap<>();
		Map<Identifier, String> hashes = new LinkedHashMap<>();
		for (Map.Entry<Identifier, byte[]> e : files.entrySet()) {
			Clip c = ClipJson.parse(e.getKey(), new String(e.getValue(), StandardCharsets.UTF_8));
			map.put(e.getKey(), c);
			hashes.put(e.getKey(), ClipFiles.entryHash(ClipJson.sha256(e.getValue()), c.rig.hash));
		}
		set(map, hashes);
		Fray.LOGGER.info("Fray Motion: {} custom rigs, {} client clips, clip set {}{}", rigs.size(), map.size(), FrayHandshake.shortHash(setHash),
			overrides == null || overrides.isEmpty() ? "" : " (with " + overrides.size() + " overridden)");
		for (Consumer<Map<Identifier, Rig>> c : ON_RIGS) {
			c.accept(rigs);
		}
	}

	/** Swaps the whole set (the Motion Studio's live reload, with the rigs it registered first). */
	public static void replace(Map<Identifier, Clip> map, Map<Identifier, String> hashes, Map<Identifier, Rig> rigs) {
		set(map, hashes);
		for (Consumer<Map<Identifier, Rig>> c : ON_RIGS) {
			c.accept(rigs);
		}
	}

	private static synchronized void set(Map<Identifier, Clip> map, Map<Identifier, String> hashes) {
		clips = Map.copyOf(map);
		order = ClipFiles.order(map);
		setHash = ClipFiles.setHash(hashes);
	}

	public static @Nullable Clip get(Identifier id) {
		return clips.get(id);
	}

	public static Map<Identifier, Clip> all() {
		return clips;
	}

	public static @Nullable Clip byIndex(int i) {
		return i >= 0 && i < order.size() ? clips.get(order.get(i)) : null;
	}

	public static int indexOf(Identifier id) {
		return order.indexOf(id);
	}

	public static String setHash() {
		return setHash;
	}

	public static MotionPayloads.Hello hello() {
		return new MotionPayloads.Hello(FrayHandshake.PROTOCOL, Fray.version(), setHash, order.size());
	}

	/** The raw bytes of a clip file as shipped (for tests that make a modified copy). */
	public static byte[] shippedBytes(Identifier id) {
		return ClipFiles.scan("assets", "fray/clip").get(id);
	}
}
