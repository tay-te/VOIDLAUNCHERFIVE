package dev.voidpvp.client.actuator;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Chat mod's decisions (§6.7, {@code schema/mods/chat.json}), out of the two Mixins so they
 * can be tested: which id a message is stored under, when it stacks, how the timestamp and the
 * count read, how dark the line band is, and which line the pointer is on.
 *
 * <p><b>Stacking rides on vanilla's own replace-by-id.</b> 1.8.9's
 * {@code ChatHud.addMessage(Text, int id, int tick, boolean refresh)} opens with
 * {@code if (id != 0) removeMessage(id)}, which deletes every visible line and the stored message
 * carrying that id. So with the mod on, every ordinary message (vanilla id 0) is given a fresh
 * synthetic id from a negative counter, and a repeat is re-added under the <em>previous</em>
 * message's id with its count appended — vanilla removes the old line itself, and the wrapped
 * lines, the scroll position and the history cap all stay vanilla's code. The ids are negative so
 * they cannot collide with an id another mod picks, which in practice are small positives.</p>
 *
 * <p>The same unique ids are what make {@code click_to_copy} copy a whole message: a wrapped
 * visible line carries its message's id, so the stored message can be found from any of its
 * lines. {@link #original} keeps each message's text as it arrived — before the timestamp and the
 * count were added — because that is what a player copying a message wants.</p>
 *
 * <p>Game-thread only, like the chat it serves.</p>
 */
public final class ChatTweaks {

    /** Where synthetic ids start; counts down from here, staying negative for a billion. */
    static final int FIRST_SYNTHETIC_ID = -1_000_000;

    /** More than the largest {@code history_length}, so every message still on screen is found. */
    static final int MAX_ORIGINALS = 5000;

    /** What one incoming message becomes. */
    public static final class Placement {
        /** The id to add it under: synthetic, the stacked-onto message's, or vanilla's own. */
        public final int id;
        /** 1 for a new message; 2, 3, … for a stacked repeat. */
        public final int count;

        Placement(int id, int count) {
            this.id = id;
            this.count = count;
        }
    }

    private int nextId = FIRST_SYNTHETIC_ID;
    private String lastKey;
    private int lastId;
    private int lastCount;
    private final Map<Integer, String> originals = new LinkedHashMap<Integer, String>(64, 0.75f,
            false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, String> eldest) {
            return size() > MAX_ORIGINALS;
        }
    };

    /**
     * Decides where one incoming message goes.
     *
     * @param key       its unformatted text ({@code Text.asUnformattedString}), before any
     *                  timestamp — two messages are repeats when this matches, whatever their
     *                  colours, and the timestamp would otherwise make every message unique
     * @param vanillaId the id the caller passed; non-zero ids belong to whoever chose them and
     *                  are passed through untouched — they already replace themselves, and
     *                  stacking onto one would fight its owner
     * @param stack     {@code stack_repeats}
     */
    public Placement place(String key, int vanillaId, boolean stack) {
        String k = key == null ? "" : key;
        if (vanillaId != 0) {
            // Something in between breaks a run: "a, b, a" is three lines, not "a (x2), b".
            lastKey = null;
            originals.put(Integer.valueOf(vanillaId), k);
            return new Placement(vanillaId, 1);
        }
        if (stack && k.equals(lastKey)) {
            lastCount++;
            return new Placement(lastId, lastCount);
        }
        int id = nextId--;
        if (nextId >= 0) {
            nextId = FIRST_SYNTHETIC_ID; // a billion messages later; never hand out 0 or a positive
        }
        lastKey = k;
        lastId = id;
        lastCount = 1;
        originals.put(Integer.valueOf(id), k);
        return new Placement(id, 1);
    }

    /**
     * Forgets the run, so the next message starts a new line. Called on {@code ChatHud.clear}
     * (the stacked-onto line is gone) and whenever the mod is off (a message vanilla stored is
     * one this class never saw, so a stale run would stack across it once the mod came back).
     */
    public void forget() {
        lastKey = null;
    }

    /** The message stored under {@code id} as it arrived, or null when it was never seen. */
    public String original(int id) {
        return originals.get(Integer.valueOf(id));
    }

    /** {@code "[14:03] "}: the prefix, with its trailing space. */
    public static String timestamp(int hour, int minute) {
        return "[" + two(hour) + ":" + two(minute) + "] ";
    }

    /** {@code " (x3)"}: the stack count suffix, with its leading space. */
    public static String stackSuffix(int count) {
        return " (x" + count + ")";
    }

    /**
     * {@code background_opacity} applied to the line band's colour.
     *
     * <p>Vanilla draws the band as {@code (alpha / 2) << 24} with {@code alpha} the line's own
     * fade (0-255), so the band is always half the text's opacity; the schema's 0.5 is that.
     * This scales the band's alpha by {@code opacity * 2} and is exactly the identity at 0.5,
     * which is what lets the redirect run unconditionally without moving a vanilla pixel.</p>
     */
    public static int backgroundColor(int vanillaColor, float opacity) {
        if (opacity == 0.5f) {
            return vanillaColor;
        }
        int alpha = (vanillaColor >>> 24) & 0xFF;
        float o = opacity < 0 ? 0 : (opacity > 1 ? 1 : opacity);
        int scaled = Math.round(alpha * o * 2f);
        if (scaled > 255) {
            scaled = 255;
        }
        return (scaled << 24) | (vanillaColor & 0xFFFFFF);
    }

    /**
     * The visible-line index under the pointer, or -1 — {@code ChatHud.getTextAt}'s coordinate
     * math, replicated from its bytecode so the copy picks the same line a click event would.
     *
     * <pre>
     *   x = floor((mouseX / scale - 3)  / chatScale)
     *   y = floor((mouseY / scale - 27) / chatScale)       // LWJGL y: up from the bottom
     *   x, y >= 0, x <= floor(width / chatScale), y < fontHeight * lines + lines
     *   index = y / fontHeight + scrolledLines,  lines = min(visibleLineCount, visibleSize)
     * </pre>
     *
     * <p>The {@code fontHeight * lines + lines} bound is vanilla's own (a pixel per line more
     * than the lines occupy) and is kept, not corrected, so the two never disagree at the
     * top edge.</p>
     */
    public static int lineIndexAt(int mouseX, int mouseY, int scaleFactor, float chatScale,
                                  int chatWidth, int fontHeight, int visibleLineCount,
                                  int visibleSize, int scrolledLines) {
        if (scaleFactor <= 0 || chatScale <= 0 || fontHeight <= 0) {
            return -1;
        }
        int x = floor((mouseX / scaleFactor - 3) / chatScale);
        int y = floor((mouseY / scaleFactor - 27) / chatScale);
        if (x < 0 || y < 0) {
            return -1;
        }
        int lines = Math.min(visibleLineCount, visibleSize);
        if (x > floor(chatWidth / chatScale) || y >= fontHeight * lines + lines) {
            return -1;
        }
        int index = y / fontHeight + scrolledLines;
        return index >= 0 && index < visibleSize ? index : -1;
    }

    /**
     * Drops {@code §x} formatting codes. {@code asUnformattedString} removes the structured
     * styles but not legacy codes embedded in a literal string, which servers still send.
     */
    public static String stripFormatting(String s) {
        if (s == null || s.indexOf('§') < 0) {
            return s;
        }
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§') {
                i++; // and the code letter after it
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static int floor(float v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private static String two(int v) {
        return v < 10 ? "0" + v : Integer.toString(v);
    }
}
