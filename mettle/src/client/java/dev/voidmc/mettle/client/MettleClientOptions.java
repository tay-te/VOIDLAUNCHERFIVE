package dev.voidmc.mettle.client;

import dev.voidmc.mettle.Mettle;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Client options (UI.md §10), kept in {@code config/mettle-client.properties}. There is no settings screen yet: edit the
 * file, or a host (the VOID Hub, the K screen's settings glyph) calls the setters, which save at once.
 */
public final class MettleClientOptions {
	private static final String FILE = "mettle-client.properties";
	private static boolean loaded;
	private static boolean toasts = true;
	private static boolean sounds = true;
	private static boolean pointsNudge = true;

	private MettleClientOptions() {
	}

	/** The progress toasts at the top of the screen (level-ups, perks, feats). Off: the action bar line returns. */
	public static boolean toasts() {
		load();
		return toasts;
	}

	/** The chimes for level-ups, tiers, perks and feats. */
	public static boolean sounds() {
		load();
		return sounds;
	}

	/** The "◆ 3 points · K" pip by the hotbar after a level-up. */
	public static boolean pointsNudge() {
		load();
		return pointsNudge;
	}

	public static void setToasts(boolean on) {
		load();
		toasts = on;
		save();
	}

	public static void setSounds(boolean on) {
		load();
		sounds = on;
		save();
	}

	public static void setPointsNudge(boolean on) {
		load();
		pointsNudge = on;
		save();
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE);
	}

	private static synchronized void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		Path path = path();
		if (!Files.exists(path)) {
			save();
			return;
		}
		Properties p = new Properties();
		try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			p.load(r);
		} catch (IOException e) {
			Mettle.LOGGER.warn("Could not read {}: {}", path, e.toString());
			return;
		}
		toasts = Boolean.parseBoolean(p.getProperty("toasts", "true"));
		sounds = Boolean.parseBoolean(p.getProperty("sounds", "true"));
		pointsNudge = Boolean.parseBoolean(p.getProperty("pointsNudge", "true"));
	}

	private static synchronized void save() {
		Properties p = new Properties();
		p.setProperty("toasts", Boolean.toString(toasts));
		p.setProperty("sounds", Boolean.toString(sounds));
		p.setProperty("pointsNudge", Boolean.toString(pointsNudge));
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				p.store(w, "VOID Mettle client options (docs/UI.md §10)");
			}
		} catch (IOException e) {
			Mettle.LOGGER.warn("Could not write {}: {}", path, e.toString());
		}
	}
}
