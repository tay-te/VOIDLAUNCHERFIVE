package dev.voidmc.hearth.client.overlay;

import dev.voidmc.hearth.Hearth;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Hearth's client options, kept in {@code config/hearth-client.properties}. Today one: how a host draws conversations
 * (docs/HUB_UI.md decisions): {@link #CARD} (direction A, the portrait card, the default) or {@link #CINEMATIC}
 * (direction B, the cinematic bar). A host's settings page calls {@link #setConversationStyle}, which saves at once.
 */
public final class HearthClientOptions {
	public static final String CARD = "card";
	public static final String CINEMATIC = "cinematic";
	private static final String FILE = "hearth-client.properties";
	private static boolean loaded;
	private static String conversationStyle = CARD;
	private static long revision;

	private HearthClientOptions() {
	}

	/** {@link #CARD} or {@link #CINEMATIC}. */
	public static String conversationStyle() {
		load();
		return conversationStyle;
	}

	/** Whether a value names a conversation style. */
	public static boolean isStyle(String style) {
		return CARD.equals(style) || CINEMATIC.equals(style);
	}

	/** Sets and saves the style; anything else than a known style is ignored (returns false). */
	public static boolean setConversationStyle(String style) {
		String s = style == null ? "" : style.trim().toLowerCase(Locale.ROOT);
		if (!isStyle(s)) {
			return false;
		}
		load();
		if (!s.equals(conversationStyle)) {
			conversationStyle = s;
			revision++;
			save();
		}
		return true;
	}

	/** Goes up whenever an option changes. */
	public static long revision() {
		return revision;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE);
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		try {
			Path f = file();
			if (!Files.isRegularFile(f)) {
				return;
			}
			Properties p = new Properties();
			try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
				p.load(r);
			}
			String style = p.getProperty("conversation_style", CARD).trim().toLowerCase(Locale.ROOT);
			conversationStyle = isStyle(style) ? style : CARD;
		} catch (IOException | RuntimeException e) {
			Hearth.LOG.warn("could not read {}", FILE, e);
		}
	}

	private static void save() {
		Properties p = new Properties();
		p.setProperty("conversation_style", conversationStyle);
		try {
			Path f = file();
			Files.createDirectories(f.getParent());
			try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
				p.store(w, "VOID Hearth client options: conversation_style = card (the portrait card) | cinematic (the cinematic bar)");
			}
		} catch (IOException | RuntimeException e) {
			Hearth.LOG.warn("could not write {}", FILE, e);
		}
	}
}
