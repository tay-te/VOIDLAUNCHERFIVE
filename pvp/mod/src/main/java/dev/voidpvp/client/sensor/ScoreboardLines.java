package dev.voidpvp.client.sensor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The plain half of the {@code scoreboard} sensor: vanilla's sidebar selection rules, applied to
 * a list of scores, producing the {@code scoreboard} bridge payload.
 *
 * <p>The rules are {@code InGameHud.renderScoreboardObjective}'s, read off the 1.8.9 bytecode:
 * scores arrive sorted ascending; names beginning with {@code #} are dropped (offset 22, the
 * {@code InGameHud$1} predicate); if more than fifteen remain, only the last fifteen are kept
 * (offsets 32-57, {@code Iterables.skip(size - 15)}); and they are drawn bottom-up, so the
 * highest score is the top line. {@link #payload} produces the lines top to bottom.</p>
 */
public final class ScoreboardLines {

    /** Vanilla's line limit. */
    public static final int MAX_LINES = 15;

    private ScoreboardLines() {
    }

    /** One score, as read off the scoreboard. */
    public static final class Entry {
        final String playerName;
        final String text;
        final int score;

        /**
         * @param playerName the raw score holder, for the {@code #} rule
         * @param text       the line as drawn: team prefix + name + team suffix
         */
        public Entry(String playerName, String text, int score) {
            this.playerName = playerName == null ? "" : playerName;
            this.text = text == null ? "" : text;
            this.score = score;
        }
    }

    /** The lines vanilla would draw, top to bottom, from scores sorted ascending. */
    public static List<Entry> topToBottom(List<Entry> ascending) {
        List<Entry> kept = new ArrayList<Entry>();
        for (Entry e : ascending) {
            if (!e.playerName.startsWith("#")) {
                kept.add(e);
            }
        }
        int from = Math.max(0, kept.size() - MAX_LINES);
        List<Entry> out = new ArrayList<Entry>(kept.size() - from);
        for (int i = kept.size() - 1; i >= from; i--) {
            out.add(kept.get(i));
        }
        return out;
    }

    /** The {@code scoreboard} payload for a visible sidebar. */
    public static JsonObject payload(String title, List<Entry> ascending) {
        JsonObject o = new JsonObject();
        o.addProperty("visible", true);
        o.addProperty("title", clip(title));
        JsonArray lines = new JsonArray();
        for (Entry e : topToBottom(ascending)) {
            JsonObject line = new JsonObject();
            line.addProperty("text", clip(e.text));
            line.addProperty("score", e.score);
            lines.add(line);
        }
        o.add("lines", lines);
        return o;
    }

    /** The payload for "no sidebar objective". */
    public static JsonObject hidden() {
        JsonObject o = new JsonObject();
        o.addProperty("visible", false);
        o.addProperty("title", "");
        o.add("lines", new JsonArray());
        return o;
    }

    /** {@code bridge.json} caps each string at 256; a server can send longer. */
    private static String clip(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 256 ? s : s.substring(0, 256);
    }
}
