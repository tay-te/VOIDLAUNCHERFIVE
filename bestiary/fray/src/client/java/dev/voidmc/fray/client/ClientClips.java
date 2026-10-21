package dev.voidmc.fray.client;

import dev.voidmc.fray.Fray;
import dev.voidmc.fray.motion.Clip;
import dev.voidmc.fray.motion.ClipFiles;
import dev.voidmc.fray.motion.ClipJson;
import dev.voidmc.fray.motion.FrayHandshake;
import dev.voidmc.fray.motion.MotionPayloads;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The clips this client draws, loaded from the mods' own jars ({@code assets/<ns>/fray/clip}), indexed in id order (the
 * order the server's {@code motion/play} indices use) and hashed exactly as loaded: the hash the client sends at login
 * is of the bytes it will actually play.
 */
public final class ClientClips {
	private static Map<Identifier, Clip> clips = Map.of();
	private static List<Identifier> order = List.of();
	private static String setHash = "";

	private ClientClips() {
	}

	/** (Re)loads the clips; {@code overrides} replaces some files' bytes (game tests: a client whose clip differs). */
	public static void load(@Nullable Map<Identifier, byte[]> overrides) {
		Map<Identifier, byte[]> files = ClipFiles.scan("assets", "fray/clip");
		if (overrides != null) {
			files.putAll(overrides);
		}
		Map<Identifier, Clip> map = new LinkedHashMap<>();
		Map<Identifier, String> hashes = new LinkedHashMap<>();
		for (Map.Entry<Identifier, byte[]> e : files.entrySet()) {
			map.put(e.getKey(), ClipJson.parse(e.getKey(), new String(e.getValue(), StandardCharsets.UTF_8)));
			hashes.put(e.getKey(), ClipJson.sha256(e.getValue()));
		}
		clips = map;
		order = ClipFiles.order(map);
		setHash = ClipFiles.setHash(hashes);
		Fray.LOGGER.info("Fray Motion: {} client clips, clip set {}{}", map.size(), FrayHandshake.shortHash(setHash),
			overrides == null || overrides.isEmpty() ? "" : " (with " + overrides.size() + " overridden)");
	}

	public static @Nullable Clip get(Identifier id) {
		return clips.get(id);
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
