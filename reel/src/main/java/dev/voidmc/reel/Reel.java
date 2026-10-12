package dev.voidmc.reel;

import dev.voidmc.reel.shot.Shot;
import dev.voidmc.reel.shot.ShotParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * VOID Reel: shots written as files, filmed frame by frame. Shots are read from -Dreel.dir (the dev runs point it
 * at the mod's own shots/ folder), or &lt;game dir&gt;/reel/shots; renders go to -Dreel.out, or
 * &lt;game dir&gt;/reel/renders.
 */
public final class Reel {
	public static final String MOD_ID = "reel";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	private Reel() {
	}

	public static Path shots() {
		return folder("reel.dir", "shots");
	}

	public static Path renders() {
		return folder("reel.out", "renders");
	}

	private static Path folder(String property, String name) {
		String set = System.getProperty(property);
		Path path = set != null ? Path.of(set) : FabricLoader.getInstance().getGameDir().resolve(MOD_ID).resolve(name);
		try {
			Files.createDirectories(path);
		} catch (IOException e) {
			LOG.warn("[reel] cannot create {}", path, e);
		}
		return path;
	}

	/** Reads shots/&lt;name&gt;.json afresh, so an edited shot is filmed as it now stands. */
	public static Shot shot(String name) throws IOException {
		Path file = shots().resolve(name.endsWith(".json") ? name : name + ".json");
		if (!Files.isRegularFile(file)) {
			throw new IOException("no shot " + file);
		}
		return ShotParser.read(file);
	}

	public static List<String> shotNames() {
		try (Stream<Path> files = Files.list(shots())) {
			return files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json") && !n.startsWith("_"))
				.map(n -> n.substring(0, n.length() - 5)).sorted().toList();
		} catch (IOException e) {
			return List.of();
		}
	}
}
