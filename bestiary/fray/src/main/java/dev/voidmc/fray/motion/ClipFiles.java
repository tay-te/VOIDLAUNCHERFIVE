package dev.voidmc.fray.motion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.resources.Identifier;

/**
 * Finds baked motion files in the loaded mods' own jars: client clips under {@code assets/<ns>/fray/clip} and server
 * timelines under {@code data/<ns>/fray/timeline}.
 *
 * <p>Deliberately not through the resource manager: a resource pack or data pack replacing a clip would change
 * gameplay timing on one side only and break the login hash. Clips are part of the mod, like code.
 */
public final class ClipFiles {
	private ClipFiles() {
	}

	/** Every file under {@code <kind>/<ns>/<sub>/**.json} in every mod, by id ({@code ns:path} without {@code .json}). */
	public static Map<Identifier, byte[]> scan(String kind, String sub) {
		Map<Identifier, byte[]> found = new TreeMap<>();
		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			for (Path root : mod.getRootPaths()) {
				scanRoot(root, kind, sub, found);
			}
		}
		return found;
	}

	public static void scanRoot(Path root, String kind, String sub, Map<Identifier, byte[]> found) {
		Path base = root.resolve(kind);
		if (!Files.isDirectory(base)) {
			return;
		}
		try (Stream<Path> namespaces = Files.list(base)) {
			for (Path nsDir : namespaces.toList()) {
				Path dir = nsDir.resolve(sub);
				if (!Files.isDirectory(dir)) {
					continue;
				}
				String ns = nsDir.getFileName().toString().replace("/", "");
				List<Path> files;
				try (Stream<Path> walk = Files.walk(dir)) {
					files = walk.filter(p -> p.getFileName().toString().endsWith(".json")).toList();
				}
				for (Path f : files) {
					String rel = dir.relativize(f).toString().replace('\\', '/');
					Identifier id = Identifier.fromNamespaceAndPath(ns, rel.substring(0, rel.length() - 5));
					if (found.put(id, Files.readAllBytes(f)) != null) {
						throw new IllegalStateException("two mods ship the motion file " + id);
					}
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** One clip's entry in the set hash: its clip hash, plus its custom rig's hash (hit volumes depend on both). */
	public static String entryHash(String clipHash, String rigHash) {
		return rigHash == null || rigHash.isEmpty() ? clipHash : clipHash + "+" + rigHash;
	}

	/** The clip-set hash: SHA-256 over "id hash" lines in id order. Both sides build it from what they actually loaded. */
	public static String setHash(Map<Identifier, String> clipHashes) {
		StringBuilder s = new StringBuilder();
		for (Map.Entry<Identifier, String> e : new TreeMap<>(clipHashes).entrySet()) {
			s.append(e.getKey()).append(' ').append(e.getValue()).append('\n');
		}
		return ClipJson.sha256(ClipJson.utf8(s.toString()));
	}

	public static List<Identifier> order(Map<Identifier, ?> byId) {
		return new ArrayList<>(new TreeMap<>(byId).keySet());
	}
}
