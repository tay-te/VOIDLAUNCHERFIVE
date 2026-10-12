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
	private static boolean cardSounds = true;
	private static int cardSoundsVolume = 100;

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

	/** The card draft's sounds (docs/SOUNDS.md): deal, hovers, picks, the Mythic reveal. Off: the draft is silent. */
	public static boolean cardSounds() {
		load();
		return cardSounds;
	}

	/** How loud every card sound plays, 0-100 % (on top of the UI volume slider). */
	public static int cardSoundsVolume() {
		load();
		return cardSoundsVolume;
	}

	/** The factor every card cue's volume is multiplied by: 0 when card sounds are off. */
	public static float cardSoundsScale() {
		load();
		return cardSounds ? cardSoundsVolume / 100f : 0f;
	}

	public static void setCardSounds(boolean on) {
		load();
		cardSounds = on;
		save();
	}

	public static void setCardSoundsVolume(int percent) {
		load();
		cardSoundsVolume = clampPercent(percent);
		save();
	}

	static int clampPercent(int percent) {
		return Math.max(0, Math.min(100, percent));
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
		cardSounds = Boolean.parseBoolean(p.getProperty("cardSounds", "true"));
		try {
			cardSoundsVolume = clampPercent(Integer.parseInt(p.getProperty("cardSoundsVolume", "100").trim()));
		} catch (NumberFormatException e) {
			cardSoundsVolume = 100;
		}
		if (!p.containsKey("cardSounds") || !p.containsKey("cardSoundsVolume")) {
			save();
		}
	}

	private static synchronized void save() {
		Properties p = new Properties();
		p.setProperty("toasts", Boolean.toString(toasts));
		p.setProperty("sounds", Boolean.toString(sounds));
		p.setProperty("pointsNudge", Boolean.toString(pointsNudge));
		p.setProperty("cardSounds", Boolean.toString(cardSounds));
		p.setProperty("cardSoundsVolume", Integer.toString(cardSoundsVolume));
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
