package dev.voidmc.reel.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Shader pack options set from the director's desk: written into the active pack's settings file (the same file
 * Iris's own options screen writes, shaderpacks/&lt;pack&gt;.txt) and applied by reloading the pack through Iris,
 * by reflection (Reel does not depend on Iris). For filming, e.g. turning a pack's own TAA off where Reel's
 * jittered samples anti-alias instead.
 */
final class PackOptions {
	private PackOptions() {
	}

	static String apply(Map<String, String> options) {
		if (options.isEmpty()) {
			throw new IllegalArgumentException("give options as ?NAME=value");
		}
		Path game = FabricLoader.getInstance().getGameDir();
		try {
			Properties iris = new Properties();
			try (var in = Files.newBufferedReader(game.resolve("config").resolve("iris.properties"))) {
				iris.load(in);
			}
			String pack = iris.getProperty("shaderPack");
			if (pack == null || !"true".equals(iris.getProperty("enableShaders"))) {
				throw new IllegalArgumentException("no shader pack is on");
			}
			Path settings = game.resolve("shaderpacks").resolve(pack + ".txt");
			List<String> lines = Files.isRegularFile(settings) ? new ArrayList<>(Files.readAllLines(settings)) : new ArrayList<>();
			for (var option : options.entrySet()) {
				String line = option.getKey() + "=" + option.getValue();
				boolean found = false;
				for (int i = 0; i < lines.size(); i++) {
					if (lines.get(i).startsWith(option.getKey() + "=")) {
						lines.set(i, line);
						found = true;
					}
				}
				if (!found) {
					lines.add(line);
				}
			}
			Files.write(settings, lines);
			Class.forName("net.irisshaders.iris.Iris").getMethod("reload").invoke(null);
			return "{\"pack\":\"" + Report.escape(pack) + "\",\"set\":\"" + Report.escape(options.toString()) + "\",\"reloaded\":true}";
		} catch (IOException | ReflectiveOperationException e) {
			return "{\"error\":\"" + Report.escape(e.toString()) + "\"}";
		}
	}
}
