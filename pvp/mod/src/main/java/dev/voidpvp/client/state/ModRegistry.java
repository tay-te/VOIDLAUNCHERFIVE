package dev.voidpvp.client.state;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The closed registry of the 45 mods.
 *
 * <p><b>GENERATED — do not edit.</b> The table in the static initialiser below is written by
 * {@code scripts/gen-java-registry.mjs} from {@code schema/mods.json} (registry document
 * {@code examples[0]} plus each mod's settings sub-schema). Change a mod in
 * {@code schema/mods/<id>.json}, re-run {@code node schema/build.mjs} and then
 * {@code node scripts/gen-java-registry.mjs}. Everything outside the static block — the
 * {@link Setting} descriptors, {@link #clamp} and the accessors — is hand-written, and it is
 * hand-written <i>in the generator</i>, in its {@code JAVA} template.</p>
 *
 * <p>The mod ships no config files (PVP_ARCHITECTURE.md §6.1) and cannot read {@code schema/}
 * at runtime, so the parts of the registry the game actually needs — the id set, the
 * {@code kind} split, the factory defaults, the clamp ranges and the factory HUD layout —
 * have to be in the JAR.
 * They are checked in rather than produced by the Gradle build because there is no Node on
 * the build path, and a contract that only exists after a build step is not a contract.
 * {@code node scripts/gen-java-registry.mjs --check} is the CI gate that this committed copy
 * still matches the schema; {@code ModRegistryTest} is the same check expressed against the
 * schema's meaning rather than against the file's bytes, so it still fails if someone edits
 * this file by hand and forgets to re-run anything.</p>
 */
public final class ModRegistry {

    /** Data direction of a mod, {@code mods.json#/definitions/kind}. */
    public enum Kind { HUD, GAMEPLAY }

    /**
     * Mods-panel filter taxonomy, {@code mods.json#/definitions/category}.
     *
     * <p>Deliberately not derivable from {@link Kind}: kind is a data-direction
     * split, category is the product one the panel tabs across. Crosshair is
     * {@code GAMEPLAY} but {@code VISUAL}; Zoom is {@code GAMEPLAY} but
     * {@code UTILITY}. The mod itself never filters anything — it carries the
     * value so {@code ModRegistryTest} can prove the generated table matches the
     * schema the UI reads.</p>
     */
    public enum Category { HUD, PVP, VISUAL, UTILITY }

    private ModRegistry() {
    }

    // -----------------------------------------------------------------
    // Setting descriptors — hand-written machinery
    // -----------------------------------------------------------------

    /**
     * The six shapes a setting can have, one per shape the schema can express.
     *
     * <p>{@code COLOR} and {@code KEYBIND} are both "a string matching a regex" and are told
     * apart in the generator by the {@code pattern} itself, compared against
     * {@code mods.json#/definitions/hex_color} and {@code #/definitions/keybind} — not by the
     * property's name, which is {@code keybind} on one mod and {@code key} on another.</p>
     */
    private enum Type { BOOL, INT, NUMBER, ENUM, COLOR, COLOR_RGB, KEYBIND }

    private static final class Setting {
        final Type type;
        final double min;
        final double max;
        final Set<String> values;
        final JsonElement fallback;

        Setting(Type type, double min, double max, Set<String> values, JsonElement fallback) {
            this.type = type;
            this.min = min;
            this.max = max;
            this.values = values;
            this.fallback = fallback;
        }
    }

    /**
     * Where one HUD mod's widget starts, {@code mods.json#/definitions/hud_placement}.
     *
     * <p>Anchor plus offsets, never absolute pixels (PVP_ARCHITECTURE.md §8.1). Immutable and
     * public: {@link Loadout#defaults} copies it straight into a new loadout's {@code hud[]},
     * and the overlay's {@code Reset layout} restores the same numbers from its own generated
     * copy of this table.</p>
     */
    public static final class Placement {
        /** Screen anchor the offsets are measured from. */
        public final String anchor;
        /** Horizontal offset from the anchor, in the overlay's design-canvas pixels. */
        public final double dx;
        /** Vertical offset from the anchor, in the overlay's design-canvas pixels. */
        public final double dy;

        Placement(String anchor, double dx, double dy) {
            this.anchor = anchor;
            this.dx = dx;
            this.dy = dy;
        }
    }

    private static final Map<String, Kind> KINDS = new LinkedHashMap<String, Kind>();
    private static final Map<String, Category> CATEGORIES = new LinkedHashMap<String, Category>();
    private static final Map<String, String> LABELS = new LinkedHashMap<String, String>();
    private static final Map<String, Map<String, Setting>> SETTINGS =
            new LinkedHashMap<String, Map<String, Setting>>();
    private static final Map<String, Placement> PLACEMENTS =
            new LinkedHashMap<String, Placement>();

    private static final Pattern COLOR = Pattern.compile("^#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$");

    /** {@code hex_color_rgb} — six digits, for a setting whose alpha another one owns. */
    private static final Pattern COLOR_RGB = Pattern.compile("^#(?:[0-9a-fA-F]{6})$");

    private static Setting bool(boolean def) {
        return new Setting(Type.BOOL, 0, 0, null, new JsonPrimitive(Boolean.valueOf(def)));
    }

    private static Setting number(double min, double max, double def) {
        return new Setting(Type.NUMBER, min, max, null, new JsonPrimitive(Double.valueOf(def)));
    }

    private static Setting integer(double min, double max, long def) {
        return new Setting(Type.INT, min, max, null, new JsonPrimitive(Long.valueOf(def)));
    }

    private static Setting enumOf(String def, String... values) {
        return new Setting(Type.ENUM, 0, 0,
                new LinkedHashSet<String>(Arrays.asList(values)), new JsonPrimitive(def));
    }

    private static Setting color(String def) {
        return new Setting(Type.COLOR, 0, 0, null, new JsonPrimitive(def));
    }

    private static Setting colorRgb(String def) {
        return new Setting(Type.COLOR_RGB, 0, 0, null, new JsonPrimitive(def));
    }

    private static Setting keybind(String def) {
        return new Setting(Type.KEYBIND, 0, 0, null, new JsonPrimitive(def));
    }

    private static Map<String, Setting> mod(String id, Kind kind, Category category,
                                            String label, Object... pairs) {
        Map<String, Setting> map = new LinkedHashMap<String, Setting>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (Setting) pairs[i + 1]);
        }
        KINDS.put(id, kind);
        CATEGORIES.put(id, category);
        LABELS.put(id, label);
        SETTINGS.put(id, Collections.unmodifiableMap(map));
        return map;
    }

    private static void place(String id, String anchor, double dx, double dy) {
        PLACEMENTS.put(id, new Placement(anchor, dx, dy));
    }

    // =================================================================
    // BEGIN GENERATED DATA — scripts/gen-java-registry.mjs, from schema/mods.json
    //
    // Every comment below this line is the corresponding `description` from the schema,
    // reproduced so that the reasoning for a setting is readable where the setting is. It is
    // not the place to add reasoning: write it in schema/mods/<id>.json, where Rust and the
    // UI get it too, and re-generate.
    // =================================================================

    /**
     * {@code examples[0].version} of {@code schema/mods.json}: bumped whenever a mod is added,
     * removed or reclassified. Carried in a loadout share code ({@code ShareCode}) so a code
     * made against a newer registry can be recognised as such.
     */
    public static final int REGISTRY_VERSION = 13;

    static {
        // --- HUD mods (19) — they read game state and draw -------------------------------------

        // FPS display — kind hud, hud tab, §11 safe.
        // Frames per second, updated once per tick.
        // Source: Minecraft.debugFPS.
        mod("fps", Kind.HUD, Category.HUD, "FPS display",
                // Whether the FPS display is enabled.
                "on", bool(true),
                // The shared hud block, schema/mods/_shared.json#/hud — the same keys, with the
                // same meaning, on every hud mod.
                "scale", number(0.25, 4, 1),
                "opacity", number(0, 1, 1),
                "background", enumOf("none", "none", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "tight", "normal", "roomy"),
                // Text colour of the FPS readout.
                // Type: mods.json#/definitions/hex_color.
                "color", color("#FFFFFF"),
                // Whether to render the trailing "FPS" label after the number.
                "show_label", bool(true),
                // Whether the 1% low is drawn as a trailing aside. It is the figure that says
                // whether a frame rate is actually smooth, and it is also a third number on the
                // chip a player reads mid-match — so it is a switch rather than something that
                // appears whenever a low has been measured.
                "show_low", bool(true));

        // Keystrokes — kind hud, hud tab, §11 safe.
        // WASD, mouse and spacebar tiles that light up as you press them.
        // Source: KeyBinding.setKeyBindState, edge-triggered.
        mod("keystrokes", Kind.HUD, Category.HUD, "Keystrokes",
                // Whether the keystrokes overlay is enabled.
                "on", bool(true),
                // The shared hud block, schema/mods/_shared.json#/hud — the same keys, with the
                // same meaning, on every hud mod.
                "scale", number(0.25, 4, 1),
                // shared_overrides: ships 0.85 where every other hud mod ships 1. Alpha of the
                // key tiles when a key is not pressed.
                "opacity", number(0, 1, 0.85),
                "background", enumOf("none", "none", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "tight", "normal", "roomy"),
                // Optional key that toggles the keystrokes overlay on and off in game without
                // opening the menu. NONE leaves it always visible while the mod is on.
                // Type: mods.json#/definitions/keybind.
                "keybind", keybind("NONE"),
                // Whether to render the LMB and RMB tiles under the WASD block.
                "show_mouse", bool(true),
                // Whether to render the spacebar tile.
                "show_spacebar", bool(true),
                // Whether to render the sneak (shift) tile beside the space bar. The `keys` event
                // has always carried `shift` and the widget has always been handed it; this is
                // the switch that draws it. A sneak key is as much a part of reading a PvP
                // player's inputs as the space bar, which is why the two sit together.
                "show_sneak", bool(false),
                // Whether to print the current CPS inside the LMB and RMB tiles.
                "show_cps", bool(false),
                // Corner radius of a key tile in unscaled GUI pixels, drawn as the `Corner
                // radius` slider on the Mod settings frame (Figma 244:834). 0 is a square tile.
                "corner_radius", integer(0, 20, 8),
                // Background of an unpressed key tile, as one of the five named swatches the Mod
                // settings frame offers. A token name rather than a hex value, so the tile keeps
                // following the theme when the theme changes.
                // Type: mods.json#/definitions/key_swatch.
                "key_color", enumOf("shell", "shell", "raised", "pill", "sky", "teal"),
                // Fill of a pressed key tile, as one of the five named swatches the Mod settings
                // frame offers. `accent` follows the loadout accent, which is the frame's
                // default.
                // Type: mods.json#/definitions/pressed_swatch.
                "pressed_color", enumOf("accent", "accent", "sky", "warn", "fear", "teal"),
                // Whether keys ease between the resting and pressed treatment instead of
                // switching on the frame. The press itself is always immediate; this only shapes
                // the release.
                "animate", bool(true),
                // How long a released key takes to fade back to rest, in milliseconds, when
                // `animate` is on. Long fades read as a trail of recent presses; short ones as a
                // crisp tap.
                "fade_ms", integer(0, 500, 120));

        // CPS counter — kind hud, hud tab, §11 safe.
        // Clicks per second over a sliding window.
        // Source: derived from clicks in JS.
        mod("cps", Kind.HUD, Category.HUD, "CPS counter",
                // Whether the CPS counter is enabled.
                "on", bool(true),
                // The shared hud block, schema/mods/_shared.json#/hud — the same keys, with the
                // same meaning, on every hud mod.
                "scale", number(0.25, 4, 1),
                "opacity", number(0, 1, 1),
                "background", enumOf("none", "none", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "tight", "normal", "roomy"),
                // Which mouse buttons to count: left only, right only, or both shown side by
                // side.
                "mode", enumOf("left", "left", "right", "both"),
                // Whether to render the trailing "CPS" unit after the figures. Every other
                // readout on the HUD can drop its unit; this one could not, which left it the
                // widest chip on screen for a player who already knows what the number is.
                "show_label", bool(true),
                // Length of the sliding window in milliseconds over which clicks are counted
                // before being scaled to clicks per second.
                "window_ms", integer(200, 5000, 1000),
                // Whether the highest rate seen this session is drawn as a trailing aside.
                // `window_ms` already averages, so the live figure answers "how fast am I
                // clicking"; this answers "how fast can I", which is the number worth comparing
                // against and the one the live figure never sits still long enough to show.
                "show_peak", bool(false),
                // Whether only clicks the game acted on are counted. A left click swallowed by
                // 1.8's miss delay, or made while a screen was open, is a press of the button but
                // not a swing, and with this on it does not count. Left button only; right clicks
                // are always counted as pressed. Fed by `hits.swings` on the tick payload rather
                // than by the mouse edge, so a counted click is one `doAttack` ran for.
                "ignore_cancelled", bool(false));

        // Ping display — kind hud, hud tab, §11 safe.
        // Round-trip time to the current server.
        // Source: own NetworkPlayerInfo.responseTime.
        mod("ping", Kind.HUD, Category.HUD, "Ping display",
                // Whether the ping display is enabled.
                "on", bool(true),
                // The shared hud block, schema/mods/_shared.json#/hud — the same keys, with the
                // same meaning, on every hud mod.
                "scale", number(0.25, 4, 1),
                "opacity", number(0, 1, 1),
                "background", enumOf("none", "none", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "tight", "normal", "roomy"),
                // Whether to render the trailing "ms" unit after the number.
                "show_label", bool(true),
                // Ping at or below this many milliseconds renders in the good colour.
                "good_ms", integer(0, 1000, 60),
                // Ping at or above this many milliseconds renders in the bad colour. Must be
                // greater than `good_ms`; not enforced by the schema.
                "bad_ms", integer(0, 2000, 150),
                // Whether the shortened server name is drawn after the figure. A player who only
                // ever plays one server is being told something they already know, on the chip
                // they look at most often.
                "show_host", bool(true),
                // Whether the rolling average of the last `window` readings is drawn as a
                // trailing aside, `avg 54`.
                "show_average", bool(false),
                // Whether jitter is drawn as a trailing aside, `±7`: the mean change between
                // consecutive readings over the last `window`. A steady 90 ms plays better than a
                // 40 ms that swings by 30, and only this figure tells the two apart.
                "show_jitter", bool(false),
                // Jitter at or above this many milliseconds draws in the warn treatment. 0
                // disables the warning.
                "jitter_warn_ms", integer(0, 500, 20),
                // A reading this many milliseconds above the rolling average counts as a spike
                // and briefly marks the chip. 0 disables spike detection.
                "spike_ms", integer(0, 1000, 80),
                // How many readings the average, the jitter and the spike baseline are taken
                // over. The server sends a new latency every few seconds, so 10 readings is
                // roughly the last half minute.
                "window", integer(3, 60, 10));

        // Coordinates — kind hud, hud tab, §11 safe.
        // Player position and facing direction.
        // Source: EntityPlayerSP pos/yaw.
        mod("coordinates", Kind.HUD, Category.HUD, "Coordinates",
                // Whether the coordinates display is enabled.
                "on", bool(false),
                // The shared hud block, schema/mods/_shared.json#/hud — the same keys, with the
                // same meaning, on every hud mod.
                "scale", number(0.25, 4, 1),
                "opacity", number(0, 1, 1),
                "background", enumOf("none", "none", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "tight", "normal", "roomy"),
                // Number of decimal places printed for X, Y and Z. Capped at 2 because that is
                // what the wire carries: the `tick` sensor rounds the position to 2 dp before
                // publishing it, so a third place could only ever print a zero. Widening it is a
                // bridge change, not a settings change, and a costly one — 3 dp makes ten times
                // as many positions distinct, and every distinct position is a HUD repaint.
                // The "tick sensor" the description names is `sensor/TickCoalescer` on this side;
                // that is the class to change if 2 dp ever stops being enough.
                "decimals", integer(0, 2, 1),
                // Whether to append the cardinal direction derived from yaw.
                "show_direction", bool(true),
                // Whether X, Y and Z are stacked on three lines or printed on one. Defaults to
                // `inline`, which is what the HUD frame draws; `stacked` holds the three numbers
                // against one left edge, which is easier to read while moving.
                "layout", enumOf("inline", "stacked", "inline"),
                // Ink for the readings — the three axes and the cardinal. Not the separator
                // between them: quiet-cell §1 gives colour to the live value rather than to the
                // punctuation around it.
                // Type: mods.json#/definitions/hex_color.
                "color", color("#FFFFFF"));

        // Armor status — kind hud, hud tab, §11 safe.
        // Worn armor and held item with remaining durability.
        // Source: InventoryPlayer.armorInventory durability.
        mod("armor_status", Kind.HUD, Category.HUD, "Armor status",
                // Whether the armor status display is enabled.
                "on", bool(true),
                // The shared hud block, schema/mods/_shared.json#/hud — the same keys, with the
                // same meaning, on every hud mod.
                "scale", number(0.25, 4, 1),
                "opacity", number(0, 1, 1),
                "background", enumOf("none", "none", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "tight", "normal", "roomy"),
                // Whether armor pieces are laid out left to right or top to bottom.
                "orientation", enumOf("vertical", "horizontal", "vertical"),
                // Whether to print remaining durability as a number under each piece.
                "show_durability", bool(true),
                // Whether to include the currently held item as a sixth slot.
                "show_held_item", bool(true),
                // Fraction of maximum durability under which a piece's bar turns amber. A
                // threshold on a live value, the same species as `ping.good_ms`: the point at
                // which a player wants to be told their gear is going is a matter of how they
                // play, not a constant. 0 never warns.
                "warn_below", number(0, 1, 0.5));

        // Potion effects — kind hud, hud tab, §11 safe.
        // Active potion effects with amplifier and remaining duration.
        // Source: getActivePotionEffects.
        mod("potion_effects", Kind.HUD, Category.HUD, "Potion effects",
                // Whether the potion effects display is enabled.
                "on", bool(true),
                // The shared hud block, schema/mods/_shared.json#/hud — the same keys, with the
                // same meaning, on every hud mod.
                "scale", number(0.25, 4, 1),
                "opacity", number(0, 1, 1),
                "background", enumOf("none", "none", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "tight", "normal", "roomy"),
                // Whether to print the remaining duration next to each effect.
                "show_duration", bool(true),
                // Whether to print the roman-numeral amplifier next to each effect name.
                "show_amplifier", bool(true),
                // Whether to omit ambient effects such as a beacon aura from the list.
                "hide_ambient", bool(false),
                // Effects never listed, as a bit mask over vanilla's status effect ids: bit 1 is
                // Speed, bit 16 Night Vision, up to bit 23 Saturation. Set from the settings
                // page's effect chips rather than by hand. 0 lists everything.
                "hidden_effects", integer(0, 16777215, 0),
                // An effect with this many seconds or fewer left blinks, once a second, on the
                // tick that redraws its timer. 0 disables blinking.
                "blink_below_s", integer(0, 30, 5));

        // Watermark — kind hud, visual tab, §11 safe.
        // The VOID mark, drawn over the game.
        // Source: drawn by the overlay; no game field.
        // The only HUD mod with no game field behind it — every other widget reads something
        // (fps, ping, the armour slots) and this one is drawn from nothing but its own settings.
        // Java's whole job for it is that the id exists, that its settings clamp and that its HUD
        // placement round-trips; the overlay page draws it.
        mod("watermark", Kind.HUD, Category.VISUAL, "Watermark",
                // Whether the watermark is drawn.
                "on", bool(true),
                // The shared hud block, schema/mods/_shared.json#/hud — the same keys, with the
                // same meaning, on every hud mod.
                "scale", number(0.25, 4, 1),
                // shared_overrides: ships 0.9 where every other hud mod ships 1. Alpha of the
                // mark. Lower than the other HUD mods by default, so it sits behind the readouts
                // the player is actually reading.
                "opacity", number(0, 1, 0.9),
                "background", enumOf("none", "none", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "tight", "normal", "roomy"),
                // Which parts of the mark are drawn: `full` is the ring plus the VOID wordmark,
                // `mark` is the ring alone, `word` is the wordmark alone.
                "style", enumOf("full", "full", "mark", "word"));

        // --- Gameplay mods (26) — they mutate a client-side option -----------------------------

        // Toggle sprint — kind gameplay, pvp tab, §11 safe.
        // Latches sprint instead of holding the key.
        // Source: KeyBinding override in onLivingUpdate.
        // `sneak_too` used to be here and is gone too, and this one has a Java tail:
        // `LiveState.toggleSprintSneakToo` and its reader in `VoidClient` still exist, still
        // compile and now always read `false`, because `Loadout.boolSetting` takes a default
        // rather than failing on a key the registry dropped. Delete both — sneak is
        // `toggle_sneak` now, with its own bind. A stored loadout that still carries the key is
        // handled on the Rust side by `REMOVED_SETTINGS` in `void-loadout`'s `store.rs`, not
        // here: Gson is lenient, so this side never noticed either way. `show_status` used to be
        // here and is gone. A sprint indicator is still wanted, but as its own placeable HUD mod:
        // a gameplay mod has no `hud[]` entry, so anything this drew would have been the only
        // fixed, un-movable thing on the HUD — the one property the HUD editor exists to remove.
        // Promoting this mod to `Kind.HUD` for one boolean is a structural change deserving its
        // own decision, and `Kind` and `Category` are already independent, so the machinery
        // supports it.
        mod("toggle_sprint", Kind.GAMEPLAY, Category.PVP, "Toggle sprint",
                // Whether toggle sprint is enabled.
                "on", bool(true),
                // Key that toggles the mod in game, captured through
                // `void.openKeybindCapture('toggle_sprint')`. Distinct from the sprint key
                // itself, which is vanilla's and is the key this mod latches: this one turns the
                // latching off and on mid-game, for a fight where holding the key is what the
                // hands expect. With `mode` removed it is the only way to get vanilla
                // hold-to-sprint back without opening the menu — which is the job `mode` was
                // believed to be doing and never did.
                // Type: mods.json#/definitions/keybind.
                "keybind", keybind("NONE"));

        // Fullbright — kind gameplay, visual tab, §11 grey.
        // Raises gamma so caves and shadows are fully lit.
        // Source: gammaSetting override (client-side, Watchdog-tolerated).
        mod("fullbright", Kind.GAMEPLAY, Category.VISUAL, "Fullbright",
                // Whether fullbright is enabled.
                "on", bool(false),
                // Value written to `gammaSetting` while the mod is on. Vanilla's slider tops out
                // at 1; 10 is the conventional fullbright value.
                "gamma", number(1, 15, 10),
                // Key that toggles the mod in game, captured through
                // `void.openKeybindCapture('fullbright')`. The mod that most wants one:
                // brightness is something you change *because* of what is in front of you, and
                // reaching the menu to change it means the moment has passed.
                // Type: mods.json#/definitions/keybind.
                "keybind", keybind("NONE"));

        // Hitboxes — kind gameplay, pvp tab, §11 grey.
        // Draws entity bounding boxes.
        // Source: RenderManager.debugBoundingBox.
        mod("hitboxes", Kind.GAMEPLAY, Category.PVP, "Hitboxes",
                // Whether entity hitboxes are drawn.
                "on", bool(false),
                // GL line width used for the bounding box wireframe.
                "line_width", number(0.5, 5, 2),
                // Colour of the bounding box wireframe.
                // Type: mods.json#/definitions/hex_color.
                "color", color("#FFFFFFFF"),
                // Whether to draw the vanilla eye-direction ray along with the box.
                "show_eye_line", bool(false),
                // Colour of the eye-direction ray, when `show_eye_line` draws it. Its own colour
                // rather than the box's: the ray answers *where is he looking*, which is a
                // different question from the box's *where is he*, and one colour for both draws
                // a box with a spike on it rather than two readings.
                // Type: mods.json#/definitions/hex_color.
                "eye_line_color", color("#7ADFFFFF"),
                // Furthest an entity can be, in blocks, and still be drawn. The point of a box in
                // a fight is the entity you are fighting; every box past that is a wireframe over
                // the scenery. 64 is vanilla's own entity render distance, so the default draws
                // what the game was already drawing.
                "max_distance", number(4, 64, 64),
                // Key that toggles the mod in game, captured through
                // `void.openKeybindCapture('hitboxes')`. Boxes are worth having for one fight and
                // not the next, which is a decision made mid-match rather than in a menu.
                // Type: mods.json#/definitions/keybind.
                "keybind", keybind("NONE"),
                // Whether a box changes colour with what you can do to its entity:
                // `damaged_color` while it is in its hurt animation, `hittable_color` while your
                // crosshair is on it within attack range, and `color` otherwise.
                "state_colors", bool(false),
                // Box colour for the entity your crosshair is on, when `state_colors` is on.
                // Type: mods.json#/definitions/hex_color.
                "hittable_color", color("#2FB8A6FF"),
                // Box colour for an entity in its hurt animation, when `state_colors` is on.
                // Takes precedence over `hittable_color`.
                // Type: mods.json#/definitions/hex_color.
                "damaged_color", color("#FF5A5AFF"));

        // Zoom — kind gameplay, utility tab, §11 safe.
        // Narrows FOV while the zoom key is held; the mouse wheel adjusts it.
        // Source: FOV override while key held.
        mod("zoom", Kind.GAMEPLAY, Category.UTILITY, "Zoom",
                // Whether zoom is enabled.
                "on", bool(true),
                // Key held to zoom. Captured through `void.openKeybindCapture('zoom')`.
                // Type: mods.json#/definitions/keybind.
                "key", keybind("C"),
                // The player's FOV is divided by this factor while zoomed. 4 approximates the
                // familiar Optifine zoom.
                "fov_divisor", number(1.1, 10, 4),
                // Whether the FOV change is eased over a few frames rather than snapping.
                "smooth", bool(true),
                // Whether smooth-camera mouse damping is applied while zoomed.
                "cinematic", bool(false),
                // Whether the mouse wheel changes the zoom while the key is held, between 1.1x
                // and 10x in multiplicative steps. The hotbar does not scroll while this is in
                // use, and the level goes back to `fov_divisor` on release. Eased when `smooth`
                // is on.
                "scroll_adjust", bool(true),
                // Whether mouse sensitivity is scaled down with the zoom, so a flick moves the
                // crosshair across the same share of the screen zoomed as unzoomed. Off is
                // vanilla: at 4x every mouse movement turns the view four times as far across
                // what you can see.
                "scale_sensitivity", bool(true));

        // Crosshair — kind gameplay, visual tab, §11 safe.
        // Replaces the vanilla crosshair with a configurable one at the exact screen centre.
        // Source: replaces vanilla crosshair pass; drawn in GL at exact center.
        mod("crosshair", Kind.GAMEPLAY, Category.VISUAL, "Crosshair",
                // Whether the vanilla crosshair pass is replaced.
                "on", bool(false),
                // Shape drawn at the screen centre.
                "style", enumOf("cross", "default", "cross", "dot", "circle", "t_shape", "none"),
                // Half-length in pixels of each crosshair arm, before GUI scale.
                "size", integer(1, 20, 5),
                // Stroke thickness in pixels, before GUI scale.
                "thickness", integer(1, 5, 1),
                // Empty gap in pixels between the centre point and the start of each arm.
                "gap", integer(0, 10, 2),
                // Colour of the crosshair.
                // Type: mods.json#/definitions/hex_color.
                "color", color("#FFFFFFFF"),
                // Whether a one-pixel black outline is drawn around the shape for contrast.
                "outline", bool(true),
                // Whether the gap widens while the attack cooldown is not full and while
                // sprinting.
                "dynamic", bool(false),
                // Whether a dot of `thickness` square is drawn on the centre point, under
                // whatever `style` draws around it. `dot` is a style, so without this a player
                // must choose between a cross and a centre reference; every crosshair
                // configurator worth the name lets them have both. Ignored by `none`, and by
                // `default`, which is the vanilla pass.
                "center_dot", bool(false));

        // --- HUD mods (19) — they read game state and draw -------------------------------------

        // Direction — kind hud, hud tab, §11 safe.
        // Which way you are facing, as its own placeable readout.
        // Source: `pos.yaw` on the `tick` payload; no new sensor.
        mod("direction", Kind.HUD, Category.HUD, "Direction",
                // Whether the direction display is enabled.
                "on", bool(false),
                // The shared hud block, schema/mods/_shared.json#/hud — the same keys, with the
                // same meaning, on every hud mod.
                "scale", number(0.25, 4, 1),
                "opacity", number(0, 1, 1),
                "background", enumOf("none", "none", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "tight", "normal", "roomy"),
                // How the facing is written. `letter` is the compass abbreviation the Coordinates
                // mod already prints (`N`, `NE`, `SW`) and is what fits a small chip. `word`
                // spells it out (`North`), which is what a player reading at a glance across a
                // screen actually parses. `axis` prints the Minecraft world axis instead (`+X`,
                // `-Z`) — not a compass reading at all, and the one a player wants while running
                // a nether tunnel or lining up a build, because it is the notation coordinates
                // themselves are in.
                "style", enumOf("letter", "letter", "word", "axis"),
                // Whether the raw yaw angle is printed after the facing. Off by default: it is a
                // second number on a chip whose whole job is to be read without reading, and it
                // is only wanted by players aligning something precisely.
                "show_degrees", bool(false),
                // Ink for the facing. Not the degrees aside, when `show_degrees` draws it: that
                // is the same reading in another unit, and colouring both leaves the chip one
                // solid block with no hierarchy in it.
                // Type: mods.json#/definitions/hex_color.
                "color", color("#FFFFFF"));

        // Combo counter — kind hud, pvp tab, §11 safe.
        // Consecutive hits landed without being hit back.
        // Source: derived in JS from the monotonic `hits.dealt`/`hits.taken` counters on the tick
        // payload.
        mod("combo", Kind.HUD, Category.PVP, "Combo counter",
                // Whether the combo counter is enabled.
                "on", bool(true),
                // The shared hud block, schema/mods/_shared.json#/hud — the same keys, with the
                // same meaning, on every hud mod.
                "scale", number(0.25, 4, 1),
                "opacity", number(0, 1, 1),
                "background", enumOf("none", "none", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "tight", "normal", "roomy"),
                // How long without landing a hit before the count drops back to zero. This is the
                // timeout the sensor refuses to have — it sends counters, and the policy lives
                // here. 3000 ms because a 1.8 combo is bounded by knockback recovery rather than
                // by a clock: consecutive hits in a real chase are well under a second apart, so
                // three seconds forgives one whiffed swing and the sprint back into range, while
                // still clearing the chip before the fight it described is over. Lower makes the
                // counter honest about a broken chain; higher leaves a stale number on screen
                // after the target is already dead.
                "reset_ms", integer(500, 10000, 3000),
                // Whether the trailing "COMBO" unit is drawn after the figure. A bare number on a
                // chip of its own is ambiguous in a way the other readouts are not — there is no
                // unit that gives it away the way `ms` or `FPS` do — so this defaults on and is
                // worth turning off only once the chip's position has taught you what it is.
                "show_label", bool(true));

        // Saturation — kind hud, hud tab, §11 safe.
        // The hidden half of the hunger bar — what actually decides whether you regenerate.
        // Source: `FoodStats#getSaturationLevel`, via the tick sensor.
        mod("saturation", Kind.HUD, Category.HUD, "Saturation",
                // Whether the saturation readout is enabled.
                "on", bool(false),
                // The shared hud block, schema/mods/_shared.json#/hud — the same keys, with the
                // same meaning, on every hud mod.
                "scale", number(0.25, 4, 1),
                "opacity", number(0, 1, 1),
                "background", enumOf("none", "none", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "tight", "normal", "roomy"),
                // How the value is drawn. `number` prints the figure, and it is the default
                // because saturation is read against a *threshold* rather than as a quantity: in
                // 1.8 combat regeneration runs while saturation is above zero and stops the
                // instant it is not, so the only reading that matters is how close to zero you
                // are, and a bar cannot be read to that precision at a glance. `bar` draws it as
                // a fill, in the shape of the hunger row it is the hidden half of, for a player
                // who wants it to look like part of the vanilla HUD. `both` puts the figure
                // beside the fill for the player who wants the shape *and* the cliff.
                "style", enumOf("number", "number", "bar", "both"),
                // Decimal places on the figure. 1 by default, because saturation drains in
                // fractions of a point and the difference between `0.4` and `0` is the difference
                // between regenerating and not — rounding that to a whole number hides the one
                // transition the readout exists to show. 0 is for a player who only wants to know
                // roughly how much food is left in the tank and would rather the chip stopped
                // twitching. The bound is 0-2 rather than the 0-1 this readout would pick on its
                // own: `decimals` means the same thing in `coordinates` and in `momentum` and
                // both are capped at 2, and `packages/protocol`'s generator keys `SETTING_BOUNDS`
                // by property *name* precisely so that one name cannot mean two ranges — it
                // throws rather than hand one mod the other's slider. A second place is honest
                // here anyway (the sensor sends a raw float, unrounded), it is just rarely worth
                // the width.
                "decimals", integer(0, 2, 1),
                // Whether the trailing "SAT" unit is drawn after the figure. Saturation shares
                // its range with hunger (both 0-20) and sits near it on most layouts, so the
                // label is what stops the two being read as each other.
                "show_label", bool(true));

        // Momentum — kind hud, hud tab, §11 safe.
        // How fast you are actually travelling across the ground.
        // Source: horizontal ground speed (`speed`) on the tick payload.
        mod("momentum", Kind.HUD, Category.HUD, "Momentum",
                // Whether the momentum readout is enabled.
                "on", bool(false),
                // The shared hud block, schema/mods/_shared.json#/hud — the same keys, with the
                // same meaning, on every hud mod.
                "scale", number(0.25, 4, 1),
                "opacity", number(0, 1, 1),
                "background", enumOf("none", "none", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "tight", "normal", "roomy"),
                // Which unit the figure is printed in. `bps` — blocks per second — is the default
                // because the block is the unit every other thing a player reasons about is
                // already in: reach, knockback, sprint-jump distance, the gap you are trying to
                // clear. A number in blocks can be compared against the world without arithmetic.
                // `kmh` is the same reading multiplied by 3.6 and it exists because it is the
                // number players quote at each other; it reads as faster and it is genuinely
                // easier to see small differences in, which is what a movement-mechanics player
                // wants out of it.
                "unit", enumOf("bps", "bps", "kmh"),
                // Decimal places on the figure. 2 by default, and 2 is also the ceiling — the
                // sensor rounds to 2 dp before it sends, so a third place would be inventing
                // digits the wire never carried. 2 is where the differences a player is chasing
                // actually live: sprint-jumping and plain sprinting are about 0.4 blocks/second
                // apart, and ice, soul sand and a speed potion each move the last two places
                // rather than the first. 0 or 1 is for a player who wants the magnitude without a
                // chip that flickers every tick.
                "decimals", integer(0, 2, 2),
                // Whether the trailing unit (`bps` or `km/h`) is drawn after the figure. Worth
                // keeping while `unit` is anything but the one you always use: the two readings
                // differ by 3.6x and a bare number is silently ambiguous between them.
                "show_label", bool(true));

        // Memory — kind hud, hud tab, §11 safe.
        // JVM heap in use, against the ceiling the launcher gave the game.
        // Source: heap in use and `Runtime.maxMemory` (`memory`), via the tick sensor.
        mod("memory", Kind.HUD, Category.HUD, "Memory",
                // Whether the memory readout is enabled.
                "on", bool(false),
                // The shared hud block, schema/mods/_shared.json#/hud — the same keys, with the
                // same meaning, on every hud mod.
                "scale", number(0.25, 4, 1),
                "opacity", number(0, 1, 1),
                "background", enumOf("none", "none", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "tight", "normal", "roomy"),
                // What the chip prints. `used_of_max` — `1400/4096 MB` — is the default because a
                // heap figure on its own answers nothing: 1400 MB is idle on an 8 G allocation
                // and terminal on a 2 G one, so the ceiling is half the reading and the player is
                // the only one who knows which they launched with. `used` is the bare figure, for
                // a player who already knows their ceiling and wants the narrowest chip.
                // `percent` is the same comparison pre-done, which is the smallest form that
                // still means something, at the cost of the absolute numbers you would quote in a
                // bug report.
                "style", enumOf("used_of_max", "used", "used_of_max", "percent"),
                // Whether a fill bar is drawn under the figure. Off by default, and the reason is
                // the sensor: `memory` is rate-limited hard, so the bar would move in visible
                // steps rather than sweep, and a bar that jumps reads as a broken bar rather than
                // as a coarse one. A bar also invites watching, and heap is not a number worth
                // watching — the useful reading is a glance after a stutter, which the figure
                // alone already answers. On for a player who wants headroom legible without
                // parsing two numbers.
                "show_bar", bool(false),
                // Whether the trailing unit is drawn — `MB` on `used` and `used_of_max`, `%` on
                // `percent`. Off makes the chip narrower at the cost of leaving a four-digit
                // number with nothing to say what it counts.
                "show_label", bool(true));

        // Server address — kind hud, utility tab, §11 safe.
        // The host you are actually connected to.
        // Source: `host` on the `server` bridge event.
        mod("server_address", Kind.HUD, Category.UTILITY, "Server address",
                // Whether the server address is drawn.
                "on", bool(false),
                // The shared hud block, schema/mods/_shared.json#/hud — the same keys, with the
                // same meaning, on every hud mod.
                "scale", number(0.25, 4, 1),
                "opacity", number(0, 1, 1),
                "background", enumOf("none", "none", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "tight", "normal", "roomy"),
                // How much of the host is printed. `short` keeps the part players actually say
                // out loud — `hypixel` out of `mc.hypixel.net` — and is the default because on a
                // chip the leading `mc.` and the trailing `.net` are the two pieces that never
                // differ between the servers a player switches between, so they cost width and
                // carry no information. `full` prints the address exactly as it was connected to,
                // which is what you want on a network with several proxies, on a bare IP, or in a
                // screenshot that has to be reproducible by somebody else.
                "style", enumOf("short", "short", "full"));

        // Item counter — kind hud, pvp tab, §11 safe.
        // How many of the item in your hand, or a chosen item, you have left.
        // Source: `held_count` and `counts` on the tick payload: the held stack, or an
        // inventory-wide count.
        mod("item_counter", Kind.HUD, Category.PVP, "Item counter",
                // Whether the item counter is enabled.
                "on", bool(false),
                // The shared hud block, schema/mods/_shared.json#/hud — the same keys, with the
                // same meaning, on every hud mod.
                "scale", number(0.25, 4, 1),
                "opacity", number(0, 1, 1),
                "background", enumOf("none", "none", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "tight", "normal", "roomy"),
                // Whether the count is prefixed with the multiplication sign — `x12` rather than
                // `12`. On by default: the chip sits next to a CPS figure and above a keystrokes
                // block, so a bare integer in that corner is a number among numbers, and the `x`
                // is the cheapest thing that says it is a quantity of something rather than a
                // rate.
                "show_label", bool(true),
                // A count at or below this many draws in the warn treatment instead of the normal
                // ink. **0 disables the warning entirely, and 0 is the default**, because what
                // counts as low depends completely on what is in the hand: eight is nearly out of
                // blocks and a generous stash of pearls, and a client that guessed one number
                // would be wrong for every player who does not build the way it assumed. So VOID
                // does not guess — a player who knows what they are counting sets it, and
                // everybody else gets a chip that never cries wolf. The ceiling is 64: a full
                // stack, above which the warning would be permanently on.
                "low_threshold", integer(0, 64, 0),
                // What the chip counts. `held_stack` is the stack in your hand; `held_total` is
                // every stack in the inventory matching the held item; the rest count that item
                // across the whole inventory whatever you are holding. `wool` is every colour
                // together. Healing potions and soup are the Potion counter's.
                "track", enumOf("held_stack", "held_stack", "held_total", "ender_pearl", "golden_apple", "arrow", "snowball", "egg", "cobweb", "wool", "tnt", "fire_charge", "iron_ingot", "gold_ingot", "diamond", "emerald"));

        // Stopwatch — kind hud, utility tab, §11 safe.
        // A manual timer, started and zeroed from the keyboard.
        // Source: no game field — the overlay's own clock, driven by the `modaction` bridge
        // event.
        mod("stopwatch", Kind.HUD, Category.UTILITY, "Stopwatch",
                // Whether the stopwatch is enabled.
                "on", bool(false),
                // The shared hud block, schema/mods/_shared.json#/hud — the same keys, with the
                // same meaning, on every hud mod.
                "scale", number(0.25, 4, 1),
                "opacity", number(0, 1, 1),
                "background", enumOf("none", "none", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "tight", "normal", "roomy"),
                // Whether a fraction of a second is drawn after the seconds, as hundredths. Off
                // by default, because a digit that never stops moving is the most expensive thing
                // a HUD chip can do to a player's attention and a stopwatch is usually read
                // *after* it stops. Two places rather than three: the overlay repaints per frame,
                // so a thousandths digit would be a digit nobody can read and the chip would be
                // claiming a precision the page's own clock does not have. On for timing
                // something short enough that a whole second is too coarse — a potion window, a
                // bridge run, a respawn.
                "show_millis", bool(false),
                // How the elapsed time is written. `auto` grows the field as the clock does —
                // `4:07` until an hour has passed and `1:04:07` after — which keeps the chip as
                // narrow as the reading allows and is right for almost everyone. `mmss` pins it
                // to minutes and seconds and lets the minutes run past sixty (`64:07`), so the
                // chip never changes width mid-read, which is what a player timing repeated
                // attempts against each other wants. `hmmss` always prints the hour, for a
                // session timer that is meant to be read as a duration rather than as a count.
                "format", enumOf("auto", "auto", "mmss", "hmmss"),
                // Key that starts the clock and stops it again, captured through
                // `void.openKeybindCapture('stopwatch')`. Unlike the four per-mod `keybind`
                // settings Java already dispatches, this one does **not** flip the mod's `on`:
                // Java edges the key with `input/EdgeKey` and pushes `{"e": "modaction",
                // "payload": {"mod": "stopwatch", "action": "start_stop"}}`, and the page decides
                // what running means. One key for both verbs rather than two, because a stopwatch
                // has one hand on it and the state it is in is on screen. `NONE` by default:
                // there is no key a 1.8 PvP player is not already using, and a timer nobody bound
                // costs nothing but a chip that reads `0:00`.
                // Type: mods.json#/definitions/keybind.
                "start_key", keybind("NONE"),
                // Key that returns the clock to zero, captured the same way. Java pushes `action:
                // "reset"`; the page zeroes the elapsed time and leaves the run state alone, so a
                // reset while running is a lap restart and a reset while stopped clears the last
                // reading. Its own bind rather than a long press on `start_key`, because a hold
                // gesture on a key you also tap is the one input a fight reliably gets wrong —
                // and because the two actions are then independent, which is what lets a player
                // bind only the one they use.
                // Type: mods.json#/definitions/keybind.
                "reset_key", keybind("NONE"));

        // --- Gameplay mods (26) — they mutate a client-side option -----------------------------

        // FOV changer — kind gameplay, pvp tab, §11 safe.
        // Holds your field of view still, so sprint and speed stop punching the camera.
        // Source: GameOptions.fov, with the movement-speed multiplier suppressed.
        mod("fov", Kind.GAMEPLAY, Category.PVP, "FOV changer",
                // Whether the FOV override is enabled.
                "on", bool(false),
                // Field of view held while the mod is on, in degrees. The range is exactly
                // vanilla's own slider — 30 to 110 — and that is the whole §11 argument for
                // classing this mod `safe`: it moves a number the game already lets the player
                // move, where `fullbright.gamma` runs to 15 against a vanilla slider that stops
                // at 1. 90 by default rather than vanilla's 70 because a player who turns this on
                // is turning it on for peripheral vision in a duel, and 70 is the value they are
                // leaving.
                "fov", integer(30, 110, 90),
                // Whether the movement-speed FOV modifier is suppressed. This is the mod. Vanilla
                // scales the field of view by how fast the player is moving, so sprinting, a
                // speed potion and every knockback you take zoom the camera in the middle of a
                // fight — a change of framing you did not ask for, at the moment framing matters
                // most. On by default, because a player who wanted a different field of view and
                // *not* this would have used the vanilla slider and never opened the Mods panel.
                "lock_sprint", bool(true),
                // Whether the bow-pull zoom is suppressed as well. Off by default, and
                // deliberately a second switch rather than part of `lock_sprint`: the speed
                // modifier is noise, but the bow zoom is *feedback*. It is how far the shot is
                // drawn, and on 1.8 it is close to the only cue the client gives for a charge
                // that decides whether the arrow travels. On for a player who reads draw from the
                // arm animation instead and wants the camera to stop moving at all.
                "lock_bow", bool(false));

        // Toggle sneak — kind gameplay, pvp tab, §11 safe.
        // Latches sneak instead of holding the key.
        // Source: KeyBinding override in onLivingUpdate.
        mod("toggle_sneak", Kind.GAMEPLAY, Category.PVP, "Toggle sneak",
                // Whether toggle sneak is enabled.
                "on", bool(false),
                // `toggle` latches sneak until the key is pressed again, which is the mod. `hold`
                // restores hold-to-sneak on this mod's own bind — not a null setting, because the
                // bind below is *not* vanilla's sneak key: `hold` is how a player moves sneak
                // onto a key their hand can reach without giving up the latch behaviour of every
                // other key they have bound.
                "mode", enumOf("toggle", "toggle", "hold"),
                // Key this mod latches, captured through
                // `void.openKeybindCapture('toggle_sneak')`. Its own bind rather than vanilla's
                // sneak key, and that is the entire reason this is a mod rather than a boolean on
                // Toggle sprint: a player who latches sneak wants it under a thumb that is not
                // already holding shift, and one who latches sprint wants a different key again.
                // `NONE` by default, because a latch on a key nobody chose is a player stuck
                // crouched in a hole wondering what happened.
                // Type: mods.json#/definitions/keybind.
                "keybind", keybind("NONE"));

        // Overlay — kind gameplay, visual tab, §11 grey.
        // Turns off the vanilla overlays that sit between you and the fight.
        // Source: the first-person fire, pumpkin, own-armour and stuck-arrow render passes, plus
        // `GameSettings.viewBobbing`.
        mod("overlay", Kind.GAMEPLAY, Category.VISUAL, "Overlay",
                // Whether the overlay suppressions are enabled.
                "on", bool(false),
                // Whether the first-person fire overlay is drawn while you are burning. The one
                // the roster singles out: flames cover the middle of the screen for the duration
                // of a fire-aspect hit or a lava dip, which is precisely the window in which you
                // cannot afford to lose the other player. On by default, because a player
                // enabling this mod at all is enabling it for this. It is also the switch that
                // classes the whole mod `grey` — see the top of this file — and the honest
                // reading is that it removes a cost the game imposed rather than revealing
                // something the game hid.
                "hide_fire", bool(true),
                // How much the camera and the held item move as you walk. `vanilla` is the game's
                // own bob, unchanged, and is the default because bobbing is a motion cue for your
                // own speed and taking it away is a preference rather than an improvement.
                // `minimal` keeps the held item moving and holds the camera still, which is what
                // most players actually want out of the vanilla switch and cannot get from a
                // boolean. `off` is the vanilla switch off — both still, hand included.
                "view_bobbing", enumOf("vanilla", "vanilla", "minimal", "off"),
                // Whether your own armour is drawn on your own player model. Off by default,
                // because unlike the rest of this mod it changes nothing you see in a
                // first-person fight — your armour is on screen only in third person and in the
                // inventory preview — so it is a cosmetic preference for players who want to see
                // their skin, not a visibility fix. Included because it is one line in the same
                // render path and leaving it out means a second mod later.
                "hide_own_armor", bool(false),
                // Whether arrows stuck in your own model, and arrows lying where they landed, are
                // drawn. On by default: a bow exchange leaves a thicket of arrow entities around
                // the fight and several sticking out of you, and neither tells you anything a
                // moment after it lands. This is the one switch here that removes *information*
                // rather than an occluder, which is the direction §6.1 has no objection to — the
                // objection is to mods that add data the player could not otherwise have.
                "hide_stuck_arrows", bool(true),
                // Whether the carved-pumpkin blur is drawn while one is worn. On by default,
                // because a player who has put a pumpkin on has done it for the head slot and not
                // for the view. It is the second of the two switches that class this mod `grey`:
                // the blur is the price of the helmet, and removing the price locally is not
                // something Hypixel's allowlist has a category for.
                "hide_pumpkin", bool(true));

        // Freelook — kind gameplay, pvp tab, §11 safe.
        // Detaches the camera from your facing, so you can look around without turning.
        // Source: camera yaw and pitch detached from the player's own in GameRendererMixin; key
        // polled per frame as ZoomController's is.
        mod("freelook", Kind.GAMEPLAY, Category.PVP, "Freelook",
                // Whether freelook is enabled.
                "on", bool(false),
                // Key that engages freelook, captured through
                // `void.openKeybindCapture('freelook')`. `NONE` by default, for `toggle_sneak`'s
                // reason: a camera that detaches on a key nobody chose is a player who thinks the
                // game broke. It is its own bind and not vanilla's F5 because F5 is a cycle
                // through three states and this is a hold — binding a hold to a cycling key is
                // how you end up stuck in third person in the middle of a fight.
                // Type: mods.json#/definitions/keybind.
                "keybind", keybind("NONE"),
                // `hold` engages freelook while the key is down and ends it on release. That is
                // Snaplook (§3.2 #9) and it is the default, because it is the behaviour a fight
                // can afford: the camera comes back without a second decision from a player who
                // is already making several. `toggle` latches it until the key is pressed again,
                // for the case a hold is wrong for — crossing a bridge or running a chase while
                // watching what is behind you, which is a length of time no thumb wants to hold a
                // key for.
                "mode", enumOf("hold", "hold", "toggle"),
                // Where the camera sits while freelook is engaged. `third_back` and `third_front`
                // are vanilla's own two F5 offsets and nothing more — the same pivot, the same
                // distance, reached by a different key. `free` is an orbit about that same pivot
                // rather than a snap to either offset, and the top of this file pins what that
                // word may and may not mean, because the difference between an orbit and a
                // freecam is the difference between this mod and one VOID will not ship.
                // `third_back` by default: it is the view players already have muscle memory for,
                // and the one that keeps your own model out of the middle of the frame.
                "perspective", enumOf("third_back", "third_back", "third_front", "free"),
                // What happens to your facing when freelook ends. `true` restores the view to the
                // direction your body was already pointing, so the mod is a look and never a turn
                // — that is the "snap back" of Snaplook, and it is the default because a camera
                // that quietly rotated your aim while you were watching your back is the single
                // worst thing this mod could do in a duel. `false` keeps the direction the camera
                // ended on and brings the body round to match, which turns freelook into an input
                // for turning around rather than for looking around. Both are legitimate and only
                // one of them is safe to be surprised by, which is what picks the default rather
                // than which is more useful.
                "snap_back", bool(true));

        // Hit colour — kind gameplay, pvp tab, §11 safe.
        // Recolours the red flash the game draws on an entity you hit.
        // Source: the entity hurt overlay applied in RenderLivingBase#setBrightness.
        mod("hit_color", Kind.GAMEPLAY, Category.PVP, "Hit colour",
                // Whether the hit flash is recoloured.
                "on", bool(false),
                // Colour the hurt overlay is drawn in. `#2FB8A6` by default, and deliberately not
                // vanilla's red, for two reasons that are both about the rest of the screen. A
                // mod that turns on and changes nothing looks broken — that is the argument that
                // withdrew `old_animations` from the last wave — and a default of `#FF0000` would
                // be exactly that mod. More to the point, red is the worst available choice on a
                // 1.8 map: it is the nether, it is lava, it is red wool and red leather in
                // Bedwars, and as of this same wave it is the low-health vignette `damage_tint`
                // draws at the edge of the same frame. The two most decision-shaped cues on
                // screen should not be the same hue. Teal is far from all of them and is a colour
                // the product already owns (`--teal` in `packages/ui/src/tokens.css`), so the
                // default is a choice somebody already defended rather than a new hex. Six digits
                // and not eight: the alpha byte belongs to `intensity`, and if one is written
                // here it is dropped — see the top of this file. Expect players who live in one
                // mode to retune this, which is why it is a colour and not a switch.
                // Type: mods.json#/definitions/hex_color_rgb.
                "color", colorRgb("#2FB8A6"),
                // Whether the recolour applies only to entities you damaged, or to every entity
                // the game tints. On by default, because the mod is hit *confirmation* and a
                // confirmation that also fires when two other players hit each other across the
                // arena is not one — you would be reading somebody else's fight in your own
                // colour, in your peripheral vision, during yours. Off is for spectating and for
                // the team modes where knowing a teammate connected is worth the noise. It is not
                // what makes this mod `safe`, and the top of this file says why in as many words:
                // a setting everybody believes is load-bearing is a setting nobody dares change.
                "own_hits_only", bool(true),
                // Alpha of the recoloured overlay, as a fraction of the alpha vanilla already
                // draws it at — 1 is the game's own, 0 draws nothing. **Not an absolute
                // strength**, and that distinction is the entire §11 argument at the top of this
                // file: there is no value here that makes a landed hit more visible than
                // Minecraft made it, which is what keeps a recolour inside "purely aesthetic". 1
                // by default, because a player enabling this wants the flash they already had, in
                // a colour they can pick out. Lower values are for the player who finds a
                // full-strength tint on a model they are standing next to more distracting than
                // useful; 0 is the honest way to say "recolour nothing", and it costs you the cue
                // rather than buying you anything — which is the direction §6.1 has never had an
                // objection to.
                "intensity", number(0, 1, 1));

        // Damage tint — kind gameplay, pvp tab, §11 safe.
        // Vignettes the screen when your health is low, and owns the vanilla hurt-camera shake.
        // Source: EntityLivingBase#getHealth for the vignette; EntityRenderer#hurtCameraEffect
        // for the shake.
        mod("damage_tint", Kind.GAMEPLAY, Category.PVP, "Damage tint",
                // Whether the low-health vignette and the hurt-camera control are enabled.
                "on", bool(false),
                // Health at or below which the vignette draws, in half-hearts on vanilla's own
                // 0-20 scale — so 6 is three hearts and 20 is "always on". 6 by default because
                // §3.2 #7's whole case for this mod is that sentence about three hearts, and
                // three hearts is where a 1.8 fight stops being about landing damage and starts
                // being about whether you can still disengage. Compared against `getHealth()`,
                // which is a float rather than an integer, so the boundary is inclusive and a
                // player regenerating past it crosses out of the vignette at exactly the value
                // they set rather than half a heart later.
                "threshold", integer(1, 20, 6),
                // Peak alpha of the vignette, reached at zero health. 0.6 by default: enough to
                // be unmissable at the edge of vision, and not enough to cost you the corners of
                // the screen at the moment you most need them, which is what drawing this at 1
                // does. The vignette ramps from nothing at `threshold` to this value at zero
                // rather than switching on flat, and that is a design decision worth stating
                // because the two numbers alone do not imply it — a mark that pops on at one
                // value is a mark you stop seeing after an hour, and the slide from three hearts
                // to dead is exactly the interval this should be describing. A player who wants
                // the alarm rather than the gradient sets a low `threshold` instead; the ramp
                // then has nowhere to run and it is a pop.
                "strength", number(0, 1, 0.6),
                // What happens to vanilla's hurt-camera roll — up to fourteen degrees about the
                // view axis, oriented by `attackedAtYaw`, which is to say by the direction the
                // hit came from. `vanilla` is unchanged and is the default, deliberately and
                // against the grain of what most players think they want: the roll is a handicap
                // on your aim, but it is also close to the only thing 1.8.9's client tells you
                // about *where* you were hit from, and a player who deletes it from a settings
                // page without knowing that has traded a cue for a fraction of a degree of
                // accuracy. `reduced` keeps the direction and takes most of the amplitude, which
                // is what that player usually meant. `off` removes it entirely. Removing it is
                // not `overlay`'s grey trade and the top of this file is the argument for why — a
                // rotated frame hides nothing, where the fire overlay hid the other player.
                "camera_shake", enumOf("vanilla", "vanilla", "reduced", "off"));

        // Old animations — kind gameplay, pvp tab, §11 safe.
        // Puts the 1.7 blocking animation back: the sword moves with your swing instead of
        // freezing.
        // Source: HeldItemRenderer#renderArmHoldingItem and BiPedModel#setAngles.
        mod("old_animations", Kind.GAMEPLAY, Category.PVP, "Old animations",
                // Whether the 1.7 animation reverts are enabled.
                "on", bool(false),
                // Which version's blocking animation is drawn. `one_seven` is the mod and is the
                // default: while you are holding right-click with a sword, your swing still moves
                // the sword. 1.8.9 discards it — `renderArmHoldingItem`'s BLOCK branch calls
                // `applyEquipAndSwingOffset(equip, 0.0F)`, hard-coding the swing progress to zero
                // — so a 1.8 block-hit is a frozen arm with a hit landing somewhere behind it,
                // and that stationary sword is the thing 1.7 players say the client "feels wrong"
                // for. The fix is that one argument: pass the live
                // `getHandSwingProgress(tickDelta)` instead of `0.0F`. **Do not also call
                // `translateSwingProgress`** — 1.7 skipped that translate while an item was in
                // use exactly as 1.8.9 does, so adding it back overshoots 1.7 rather than
                // restoring it. `one_seven` also drops the −30° right-arm yaw (`rightArm.posY =
                // -0.5235988f`) that 1.8 added to the blocking pose in `BiPedModel.setAngles`,
                // which is how *other* players' blocking looks on your screen — your own body is
                // drawn by their client, so nothing about how you appear to anyone else changes.
                // That third-person half is folded in here rather than given a switch of its own
                // on purpose: it is the same revert in the other render path, no player wants 1.7
                // blocking in their hands and 1.8 blocking on the model in front of them, and a
                // boolean whose entire visible effect is a 30° arm rotation on an entity would
                // have to be drawn in `test/preview.test.tsx` or exempted from it. If it is ever
                // split, this sentence is the split list. `vanilla` leaves both alone and is what
                // a player picks to compare. One interaction worth knowing, because neither mod's
                // page can show it: with `old_input.use_while_digging` off — its factory state —
                // 1.8.9's `doUse` guard discards every right click made while you are mining, so
                // you cannot *raise* the sword mid-break at all, and this setting has nothing to
                // draw for that click. The two mods are separate on purpose (one is animation and
                // `safe`, the other is input and `grey`), which is exactly why the dependency has
                // to be written down rather than inferred from sitting next to each other in the
                // grid.
                "block_hit", enumOf("one_seven", "vanilla", "one_seven"),
                // Whether your arm still swings during the half-second of dead time 1.8 imposes
                // after a click that hit nothing. **Read the second half of this before assuming
                // what it does.** On a miss, 1.8.9's `doAttack` sets `attackCooldown = 10` in
                // survival, and for those ten ticks every further click returns at the top of the
                // method: no swing, no attack, nothing. 1.7.10's switch has no MISS entry at all
                // and falls straight to `return`, so a 1.7 player clicking into open air sees an
                // arm that keeps up with the mouse. Turning this on reproduces
                // `LivingEntity.swingHand()`'s body — the two public fields `handSwinging` and
                // `handSwingTicks` — **without** `ClientPlayerEntity.swingHand()`'s outbound
                // `HandSwingC2SPacket`. Nothing reaches the server and nothing about the fight
                // changes. So be plain about what is restored and what is not: **the click is
                // still swallowed.** A left click inside the dead window does not attack, does
                // not start mining and is not sent anywhere, whether this is on or off. Only the
                // animation comes back. That means this is not a 1.7 revert either — in 1.7 the
                // click also *worked* — it is a third behaviour that existed in no version, and
                // the honest description of it is "the arm agrees with the mouse". Off by default
                // for exactly that reason: an arm that swings on a click the game ate can be read
                // as a hit that landed. The half that actually restores 1.7's click cadence is
                // `old_input.no_miss_delay`, and it is in a `grey` mod because it changes how
                // many swing packets leave the client, which is the number a CPS-based anticheat
                // is watching. Note for whoever writes the mixin: `getMiningSpeedMultiplier()` is
                // **private** on `LivingEntity`, so the re-entry guard needs an `@Invoker` rather
                // than a straight copy.
                "swing_during_delay", bool(false));

        // Old input — kind gameplay, pvp tab, §11 grey.
        // Removes the input interlocks 1.8 added, so a click is not swallowed by what your other
        // hand is doing.
        // Source: MinecraftClient#doUse, #handleBlockBreaking and #doAttack.
        mod("old_input", Kind.GAMEPLAY, Category.PVP, "Old input",
                // Whether the 1.7 input behaviours are enabled.
                "on", bool(false),
                // Whether right-click is allowed to act while you are mining. 1.8.9's `doUse`
                // opens `if (this.interactionManager.isBreakingBlock()) return;` at offsets 0–10,
                // so every right click during a break is discarded: no block placed, no bow
                // drawn, no potion thrown, no block-hit started. 1.7.10 has no such guard and no
                // `isBreakingBlock()` accessor at all — only the private `breakingBlock` field
                // the 1.8 method was written to expose — so the two clicks were simply
                // independent. One `@Redirect` on that call is the entire feature, which is why
                // `docs/mod-roster.md` §7 calls it the one setting of the four that was ready and
                // provable. Off by default: it is the switch a Bedwars player turns on to bridge
                // out of a block they are still breaking, and it is a click reaching the server
                // that vanilla would have dropped.
                "use_while_digging", bool(false),
                // Whether left-click is allowed to keep mining while you are holding an item in
                // use. The mirror of the setting above, and it was not on any roster line — it
                // came out of reading the method next door. 1.8.9's `handleBlockBreaking` opens
                // `if (this.attackCooldown > 0 || this.player.isUsingItem()) return;`; 1.7.10 has
                // the identical line with only the cooldown term. So in 1.8 a drawn bow, a raised
                // sword or a potion at the lips stops your pickaxe, and in 1.7 it did not. Same
                // shape as `use_while_digging`, same one-term change, same classification, and in
                // a rush it is the half you notice more often — blocking with a sword is a
                // resting state, and mining through it is what 1.7 hands expect. Off by default
                // for the same reason as its mirror.
                "dig_while_using", bool(false),
                // Whether the half-second dead time after a whiffed click is removed. On a click
                // that hits nothing, 1.8.9's `doAttack` reaches `hasLimitedAttackSpeed()` and
                // sets `attackCooldown = 10`, and for those ten ticks every click returns at the
                // top of the method — a whiff costs you the next half second of clicking, and in
                // survival only. 1.7.10's switch has no MISS entry and falls through to `return`,
                // so a whiff cost nothing. **This is the setting the withdrawn draft got wrong**,
                // and the correction is worth keeping: that draft called it `always_swing` and
                // said 1.8 only swings when a click connects. It does not. Both versions call
                // `swingHand()` unconditionally at offset 12, before the hit result is even read,
                // so the arm swings on a miss in 1.8 exactly as it does in 1.7. The only
                // difference was ever this cooldown. Turning this on restores 1.7's click cadence
                // and, because a click that is not swallowed is a click that sends
                // `HandSwingC2SPacket`, raises outbound swing volume on whiffs in proportion to
                // CPS — which is what classes this mod `grey`, and is the reason the purely
                // local, packetless version of this lives in `old_animations.swing_during_delay`
                // instead. Off by default. Scope it to the MISS branch: 1.7 still set the same
                // cooldown on a null hit result and on a BLOCK hit that resolved to air, so
                // suppressing the field outright goes past 1.7 rather than back to it.
                "no_miss_delay", bool(false));

        // Entity culling — kind gameplay, utility tab, §11 safe.
        // Skips drawing entities and chests, signs and banners that are hidden behind walls or
        // too far away.
        // Source: an async voxel ray test against `EntityRenderDispatcher#shouldRender` and
        // `BlockEntityRenderDispatcher#renderEntity`.
        mod("culling", Kind.GAMEPLAY, Category.UTILITY, "Entity culling",
                // Whether entity and block-entity culling is enabled.
                "on", bool(true),
                // Whether mobs, players, items and other entities that no ray from the camera can
                // reach are skipped. The bulk of the win in a crowded lobby. Never culls the
                // entity you are riding or your own player.
                "occlude_entities", bool(true),
                // Whether chests, signs, banners, skulls and the other tile entities that no ray
                // reaches are skipped. Tile entities are the expensive half of a 1.8.9 frame in a
                // built-up map — every chest is a model drawn every frame — so this is on by
                // default.
                "occlude_block_entities", bool(true),
                // Radius in blocks inside which nothing is ever occlusion-culled. The ray test
                // runs on its own thread and its answer is one pass old by the time a frame reads
                // it; inside this radius that lag could show as a player appearing a moment after
                // stepping into view, so the test is not consulted at all. 4 covers melee range
                // with a margin.
                "always_draw_within", integer(1, 16, 4),
                // Whether a culled entity still draws its nametag. Vanilla draws a player's name
                // through walls (unless they sneak), so culling the body must not take the name
                // with it — on by default, which keeps exactly what vanilla shows. Off also skips
                // the label of anything culled, which is a little faster and shows less than
                // vanilla, never more.
                "nametags_through_walls", bool(true),
                // Farthest a tile entity is drawn, in blocks. 64 is vanilla's own limit, so the
                // default changes nothing; lower it on a weak machine in chest-heavy maps.
                "block_entity_distance", integer(16, 64, 64),
                // Farthest an armour stand is drawn, in blocks. Lobby holograms are stacks of
                // invisible armour stands with a name each, and hundreds of them is common. 64 is
                // vanilla; lowering it hides far hologram text along with the stands.
                "armor_stand_distance", integer(8, 64, 64),
                // Farthest the item inside an item frame is drawn, in blocks. The frame itself is
                // unaffected. 64 is vanilla.
                "item_frame_distance", integer(8, 64, 64),
                // Farthest a sign's text is drawn, in blocks; the board is drawn to the
                // tile-entity distance either way. Sign text is a font draw per line per sign per
                // frame, and past about 32 blocks a line of it is two or three pixels tall at
                // 1080p — unreadable, but still paid for. 32 by default for that reason; 64 is
                // vanilla.
                "sign_text_distance", integer(4, 64, 32));

        // Particle limiter — kind gameplay, utility tab, §11 safe.
        // Caps live particles, skips the ones outside your view, and thins out block-break and
        // explosion bursts.
        // Source: `ParticleManager#addParticle`, `#addBlockBreakParticles` and
        // `#renderParticles`.
        mod("particles", Kind.GAMEPLAY, Category.UTILITY, "Particle limiter",
                // Whether the particle limiter is enabled.
                "on", bool(true),
                // Whether particles outside the camera's view cone skip their draw. Invisible by
                // construction, which is why it is on by default and why it is a switch rather
                // than a number.
                "frustum_cull", bool(true),
                // Most particles alive per render layer; when full, the oldest goes first,
                // exactly as vanilla does at its own limit. 4000 is vanilla's limit, so the
                // default changes nothing — lower it if explosions stall your frame.
                "particle_cap", integer(100, 4000, 4000),
                // Fraction of block-break and block-hit particles kept. 1 is vanilla's 64-piece
                // burst; 0 breaks blocks with no debris. A bridging or Bedwars player breaks
                // hundreds of blocks a game and every one used to spawn 64 particles.
                "block_break_particles", number(0, 1, 1),
                // Fraction of explosion particles kept — TNT, fireballs and creepers. 1 is
                // vanilla. The blast itself, its sound and its knockback are untouched; only the
                // smoke is thinned.
                "explosion_particles", number(0, 1, 1));

        // Frame limiter — kind gameplay, utility tab, §11 safe.
        // Lowers the frame rate while the game is in the background, in a menu, or minimised.
        // Source: `MinecraftClient#getMaxFramerate`, which feeds vanilla's own `Display.sync`.
        mod("frame_limiter", Kind.GAMEPLAY, Category.UTILITY, "Frame limiter",
                // Whether the frame limiter is enabled.
                "on", bool(true),
                // Frame cap while the game window does not have focus. 30 keeps a game on a
                // second monitor watchable while giving most of the GPU back.
                "unfocused_fps", integer(5, 120, 30),
                // Frame cap on the title screen, the server list and the pause menu. Vanilla
                // already holds the title screen at 30; 60 is smoother than that and still a
                // fraction of an uncapped menu.
                "menu_fps", integer(15, 240, 60),
                // Whether a minimised window drops to 5 frames a second. Nothing is visible, so
                // nothing is lost; sound and the connection carry on.
                "idle_minimized", bool(true));

        // Block outline — kind gameplay, visual tab, §11 safe.
        // Recolours, thickens, fills or removes the outline on the block you are looking at.
        // Source: `WorldRenderer#drawBlockOutline`.
        mod("block_outline", Kind.GAMEPLAY, Category.VISUAL, "Block outline",
                // Whether the block outline is restyled.
                "on", bool(false),
                // What is drawn round the targeted block. `outline` is vanilla's box in your
                // colour; `fill` shades the faces and drops the lines; `both` does both; `none`
                // draws nothing, for players who aim off the crosshair alone.
                "outline_style", enumOf("outline", "outline", "fill", "both", "none"),
                // Colour of the outline, alpha included. Vanilla's is black at 40%, which
                // disappears against obsidian, coal and every dark wool — the near-white default
                // reads on all of them.
                // Type: mods.json#/definitions/hex_color.
                "outline_color", color("#EDEEEFCC"),
                // Line width in screen pixels. Vanilla's is 2.
                "outline_width", number(0.5, 6, 2.5),
                // Colour of the face shading when `outline_style` is `fill` or `both`, alpha
                // included. Keep the alpha low: a fill is drawn over the block's own texture.
                // Type: mods.json#/definitions/hex_color.
                "fill_color", color("#EDEEEF26"));

        // Shiny pots — kind gameplay, visual tab, §11 safe.
        // Gives every potion a strong enchantment glint so pots read at a glance in the hotbar.
        // Source: `ItemRenderer#renderItem` and its private glint pass.
        mod("shiny_pots", Kind.GAMEPLAY, Category.VISUAL, "Shiny pots",
                // Whether potions are drawn with a stronger glint.
                "on", bool(false),
                // Whether potions with no listed effect — water bottles, and server pots whose
                // effects live only in their lore — glint too. On by default: on most servers
                // that is exactly the pot you were trying to find.
                "all_potions", bool(true),
                // How many times the glint is laid over a potion. 1 is vanilla's strength; 2 is
                // the classic 1.7 look; 3 is as shiny as it gets before the item's own colour
                // washes out.
                "glint_passes", integer(1, 3, 2),
                // Whether the glint takes the potion's own liquid colour instead of vanilla's
                // purple, so a healing pot shimmers red and a speed pot blue.
                "tint_glint", bool(false));

        // Clear glass — kind gameplay, visual tab, §11 safe.
        // Draws glass as clear air, without a resource pack.
        // Source: `BlockRenderManager#renderBlock` during chunk builds.
        mod("clear_glass", Kind.GAMEPLAY, Category.VISUAL, "Clear glass",
                // Whether glass is drawn clear.
                "on", bool(false),
                // Whether glass panes are cleared as well as full glass blocks.
                "clear_panes", bool(true),
                // Whether stained glass is cleared too. Off by default: stained glass carries
                // team colour in Bedwars and bases, and clearing it would throw that away.
                "clear_stained", bool(false));

        // Nametags — kind gameplay, visual tab, §11 safe.
        // Shows your own nametag in third person and restyles every nametag's background, size
        // and shadow.
        // Source: `EntityRenderer#renderLabelIfPresent` and `LivingEntityRenderer#hasLabel`.
        mod("nametags", Kind.GAMEPLAY, Category.VISUAL, "Nametags",
                // Whether nametags are restyled.
                "on", bool(false),
                // Whether your own nametag is drawn over your head in third person. Vanilla never
                // draws it; on by default because that is the one thing players switch this mod
                // on for.
                "show_own", bool(true),
                // Whether other players' and named mobs' nametags are hidden. Armour stands are
                // exempt: lobby holograms are stacks of named stands, and they are world text
                // rather than nametags. Off by default — a name is how you tell a teammate from
                // an enemy — and on for recording and for crowded lobbies.
                "hide_others", bool(false),
                // Alpha of the dark plate behind each name. 0.25 is vanilla's; 0 draws the text
                // alone.
                "tag_background", number(0, 1, 0.25),
                // Size of every nametag as a multiple of vanilla's.
                "tag_scale", number(0.5, 1.5, 1),
                // Whether the name is drawn with Minecraft's text shadow, which keeps it legible
                // against sky and snow. Vanilla draws it without one.
                "tag_shadow", bool(true));

        // --- HUD mods (19) — they read game state and draw -------------------------------------

        // Scoreboard — kind hud, hud tab, §11 safe.
        // The server's sidebar scoreboard, redrawn as a HUD chip you can move, scale or hide.
        // Source: the sidebar `ScoreboardObjective`, via the `scoreboard` bridge channel.
        mod("scoreboard", Kind.HUD, Category.HUD, "Scoreboard",
                // Whether the scoreboard is drawn by VOID.
                "on", bool(false),
                // The shared hud block, schema/mods/_shared.json#/hud — the same keys, with the
                // same meaning, on every hud mod.
                "scale", number(0.25, 4, 1),
                "opacity", number(0, 1, 1),
                "background", enumOf("none", "none", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "tight", "normal", "roomy"),
                // Whether the scoreboard is hidden entirely — vanilla's and this one. For
                // recording, and for servers whose sidebar is an advert.
                "hidden", bool(false),
                // Whether each line's score is drawn at its right edge. Off by default: on nearly
                // every server the score is a sort key the server uses to order the lines, the
                // red numbers carry no meaning, and hiding them is the single most requested
                // scoreboard tweak.
                "show_numbers", bool(false),
                // Whether the objective's title is drawn above the lines.
                "show_title", bool(true),
                // Whether the server's own colour codes are honoured. On by default: on team
                // modes the colour is the information. Off draws every line in the HUD's own ink.
                "server_colors", bool(true));

        // --- Gameplay mods (26) — they mutate a client-side option -----------------------------

        // GUI scale — kind gameplay, visual tab, §11 safe.
        // Sizes the hotbar and inventories independently of Minecraft's GUI scale.
        // Source: `InGameHud#render` (hotbar, bars, experience, held-item name) and `Screen#init`
        // for container screens.
        mod("gui_scale", Kind.GAMEPLAY, Category.VISUAL, "GUI scale",
                // Whether the hotbar and inventories have their own scale.
                "on", bool(false),
                // GUI scale step for the hotbar and the health, hunger, armour and experience
                // bars above it, which move as one block. 0 follows Minecraft. 2 by default: a
                // compact hotbar at 1080p, where Minecraft's own auto scale is 4.
                "hotbar_scale", integer(0, 6, 2),
                // GUI scale step for the inventory, chests, furnaces and every other container
                // screen. 0 follows Minecraft. Other menus — chat, the pause menu, VOID's own —
                // are unaffected.
                "inventory_scale", integer(0, 6, 3));

        // Fog — kind gameplay, visual tab, §11 grey.
        // Reduces or removes distance, water and lava fog. Blindness is never touched.
        // Source: `GameRenderer#renderFog`.
        mod("fog", Kind.GAMEPLAY, Category.VISUAL, "Fog",
                // Whether fog is customised.
                "on", bool(false),
                // Fog at the edge of your render distance, and the thick fog of the nether.
                // `reduced` starts it at the last chunk rather than three quarters of the way
                // out.
                "distance_fog", enumOf("reduced", "vanilla", "reduced", "off"),
                // Fog while your head is under water. `reduced` is roughly what Respiration III
                // gives.
                "water_fog", enumOf("reduced", "vanilla", "reduced", "off"),
                // Fog while your head is in lava. `vanilla` by default: seeing out of lava is the
                // clearest advantage on this page.
                "lava_fog", enumOf("vanilla", "vanilla", "reduced", "off"));

        // Menu blur — kind gameplay, visual tab, §11 safe.
        // Blurs the world behind inventories, chests and the pause menu.
        // Source: `Screen#renderBackground`, through `screen/BlurBackdrop.java`.
        mod("menu_blur", Kind.GAMEPLAY, Category.VISUAL, "Menu blur",
                // Whether the world is blurred behind menus.
                "on", bool(false),
                // How soft the blur is, in passes of the Gaussian. 1 is a light softening; 10 is
                // unrecognisable.
                "blur_radius", integer(1, 10, 4),
                // Whether the blur eases in over a fifth of a second as the screen opens, rather
                // than snapping on.
                "blur_fade", bool(true),
                // How much of vanilla's dark wash is kept over the blur, as a fraction of
                // vanilla's own. 1 is vanilla's darkness on top of the blur; 0 is the blur alone.
                "menu_dim", number(0, 1, 0.5));

        // Time & weather — kind gameplay, visual tab, §11 safe.
        // Fixes the time of day and clears the rain, on your screen only.
        // Source: `World#getSkyAngle`, `#getRainGradient` and `#getThunderGradient` on the client
        // world.
        mod("time_weather", Kind.GAMEPLAY, Category.VISUAL, "Time & weather",
                // Whether time and weather are set client-side.
                "on", bool(false),
                // Whether the time of day is held at `time_ticks` instead of following the
                // server.
                "lock_time", bool(true),
                // Time of day to hold, in ticks: 0 is sunrise, 6000 noon, 12000 sunset, 18000
                // midnight.
                "time_ticks", integer(0, 23999, 6000),
                // Whether rain, snow and thunder are hidden. The rain also stops darkening the
                // sky.
                "clear_weather", bool(true));

        // --- HUD mods (19) — they read game state and draw -------------------------------------

        // Potion counter — kind hud, pvp tab, §11 safe.
        // Healing potions or soup left in your inventory.
        // Source: `counts.health_potion` and `counts.soup` on the tick payload.
        mod("potion_counter", Kind.HUD, Category.PVP, "Potion counter",
                // Whether the potion counter is enabled.
                "on", bool(false),
                // The shared hud block, schema/mods/_shared.json#/hud — the same keys, with the
                // same meaning, on every hud mod.
                "scale", number(0.25, 4, 1),
                "opacity", number(0, 1, 1),
                "background", enumOf("none", "none", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "tight", "normal", "roomy"),
                // What is counted: splash potions of Healing, bowls of mushroom stew, or both
                // side by side.
                "track", enumOf("health_potion", "health_potion", "soup", "both"),
                // Whether the count is followed by its unit, `pots` or `soup`. With `both` the
                // units are what tell the two figures apart, so they are always drawn there.
                "show_label", bool(true),
                // A count at or below this many draws in the warn treatment. Unlike the Item
                // counter this has a non-zero default, because what is in the slot is known:
                // three heals is low in every pot and soup mode. 0 disables the warning.
                "low_threshold", integer(0, 64, 3));

        // Reach display — kind hud, pvp tab, §11 grey.
        // How far away your last hit landed, in blocks.
        // Source: `reach` on the tick payload: eye-to-hit-point distance at the moment of each
        // landed attack.
        mod("reach", Kind.HUD, Category.PVP, "Reach display",
                // Whether the reach display is enabled.
                "on", bool(false),
                // The shared hud block, schema/mods/_shared.json#/hud — the same keys, with the
                // same meaning, on every hud mod.
                "scale", number(0.25, 4, 1),
                "opacity", number(0, 1, 1),
                "background", enumOf("none", "none", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "tight", "normal", "roomy"),
                // Digits after the decimal point. Two is the precision the measurement actually
                // has; one reads faster, and none is the whole-block figure.
                "decimals", integer(0, 2, 2),
                // Which figure is drawn: the last landed hit, or the mean of the last ten.
                "mode", enumOf("last", "last", "average"),
                // Whether the figure is followed by its unit, `blocks`.
                "show_label", bool(true),
                // Hide the chip this long after the last landed hit; 0 keeps it on screen. A
                // reach figure is only interesting while you are trading hits.
                "hide_after_ms", integer(0, 30000, 0));

        // --- Gameplay mods (26) — they mutate a client-side option -----------------------------

        // Chat — kind gameplay, utility tab, §11 safe.
        // Timestamps, stacked repeats, click to copy and a longer history.
        // Source: ChatHud: message add, line render and history cap; ChatScreen: mouse click.
        mod("chat", Kind.GAMEPLAY, Category.UTILITY, "Chat",
                // Whether the chat changes are enabled.
                "on", bool(false),
                // Whether each message is prefixed with the time it arrived, `[14:03]`, in grey.
                "timestamps", bool(true),
                // Whether a message identical to the one before it replaces it with a count,
                // `(x3)`, instead of adding another line. Lobby spam and repeated server notices
                // collapse to one line.
                "stack_repeats", bool(true),
                // Whether right-clicking a message in the open chat copies the whole message,
                // without colour codes, to the clipboard.
                "click_to_copy", bool(true),
                // Opacity of the dark band behind chat lines, as a fraction of the line's own
                // fade. 0.5 is vanilla; 0 draws text only.
                "background_opacity", number(0, 1, 0.5),
                // How many messages the chat keeps for scrolling back. Vanilla keeps 100.
                "history_length", integer(100, 5000, 1000));

        // Cosmetics — kind gameplay, visual tab, §11 safe.
        // Capes, wings, hats, halos, trails and emotes, on you and on other VOID players.
        // Source: the cosmetic renderer's player-model layer (cape, back, head, face, halo,
        // badge), the trail emitter, and the emote animator.
        mod("cosmetics", Kind.GAMEPLAY, Category.VISUAL, "Cosmetics",
                // Whether cosmetics are shown.
                "on", bool(true),
                // Whether other VOID players' cosmetics are drawn. On by default, because seeing
                // who else plays VOID is half the point. Off draws only your own — for a crowded
                // lobby, or for a player who wants the fight in front of them to look like
                // vanilla. Their emotes still animate their model; only what they wear is hidden.
                "others", bool(true),
                // Whether your own trail is drawn while you are in first person. Off by default:
                // a trail is something other people see you leave, and in first person it is
                // particles at your own feet, on the edge of the frame you aim through. On is for
                // the player who likes to watch it. Your cape, halo and the rest are never drawn
                // in first person — the game does not draw your model there either.
                "own_first_person", bool(false),
                // Whether emissive cosmetics glow — the soft bloom around halos, wing edges and
                // bright trails. On by default, because those items are designed around it. Off
                // draws the same shapes flat, which is cheaper on a weak GPU and easier on the
                // eyes in a dark cave.
                "glow", bool(true),
                // Whether the camera swings to third person and orbits slowly while you emote, so
                // you can see what you are doing. On by default. Off keeps your own view where it
                // is: other players still see the emote, you just do not watch it. The camera
                // never moves while you move — taking a step ends the emote and gives the view
                // straight back.
                "emote_camera", bool(true),
                // How much cosmetics react to the fight. `full` by default: your cape whips when
                // you are hit, your wings flare and your trail thickens as a combo builds, your
                // halo brightens a step per hit and pulses on a kill, and everything dims a
                // little when you are low on health — and other VOID players' cosmetics flinch
                // and flare the same way from what your client already sees of their fight (hurt
                // flashes, swings, deaths). `subtle` plays the same reactions at under half the
                // size. `off` keeps cosmetics on their idle motion whatever happens. Purely
                // visual either way: nothing here tells you anything the fight on screen does
                // not.
                "reactive", enumOf("full", "off", "subtle", "full"),
                // Hold to open the emote wheel in the world: the mouse picks a slot (look freezes
                // while it is held), 1-8 pick directly, releasing plays. `B` by default — unbound
                // in vanilla 1.8.9 and next to the movement keys, so the player keeps moving
                // while they choose. `NONE` turns the wheel key off; the wheel is still reachable
                // from the menu.
                // Type: mods.json#/definitions/keybind.
                "emote_key", keybind("B"));

        // --- The factory HUD layout (19) — where each widget starts ----------------------------

        // Where this mod's widget sits on a HUD nobody has touched — the layout of Figma frame
        // 244:1722, which is what a new loadout is seeded with and what the HUD editor's `Reset
        // layout` restores. Anchor plus `dx`/`dy`, exactly as
        // `loadout.json#/definitions/hud_item`, minus the `id` (it is the entry's own) and the
        // per-item `scale` (a factory layout is always 1). The numbers are in the **overlay's own
        // design-canvas pixels** — `VoidClient.pumpUi` fits the view to a 1300 x 820 canvas —
        // because that is the space the page actually lays out in, and they are on a **38-42 px
        // vertical rhythm**, which is what it takes to stack chips that are taller than that
        // without overlapping. They are therefore NOT the tighter offsets in
        // `crates/void-loadout`'s `defaults.rs` library or in `loadout.json`'s own `examples`,
        // whose 18-20 px rhythm belongs to hand-authored product loadouts rather than to the
        // factory layout. Mods that ship off (`coordinates`, `direction`) are placed too: a
        // placement is where a widget *would* go, not whether it is drawn — the `on` setting
        // decides that. Required on every `kind: hud` mod and forbidden on every `kind: gameplay`
        // mod; the per-mod `<id>_entry` definitions are where that is enforced, so a HUD mod with
        // no placement, or a gameplay mod with one, is a schema error rather than a silent
        // default.
        // Anchors: top-left | top | top-right | left | center | right | bottom-left | bottom | bottom-right.
        // Read by Loadout.defaults(), which seeds a new loadout's hud[] from it.
        place("fps", "top-left", 20, 20);
        place("keystrokes", "bottom-left", 20, -20);

        // CPS counter: Beside FPS in the top row, as the Cartridge fight HUD (Figma 630:2) draws
        // it: FPS 96 wide at 20, CPS 128 wide at 122, Ping at 256, a 6 px gap between 56 px
        // modules.
        place("cps", "top-left", 122, 20);
        place("ping", "top-left", 256, 20);
        place("coordinates", "top-left", 20, 128);
        place("armor_status", "bottom-right", -20, -20);
        place("potion_effects", "top-right", -20, 20);

        // Watermark: Under the top row of modules (56 px tall at 20, so 84 is the next row with
        // the 8 px rhythm the Cartridge HUD uses). Third thing in the top-left reading order,
        // where every PvP client puts its mark.
        place("watermark", "top-left", 20, 84);

        // Direction: Under Coordinates, one 64 px Cartridge row down: the mod a player confuses
        // with Coordinates, stacked so the difference — a position versus a facing — is visible
        // at a glance.
        place("direction", "top-left", 20, 192);

        // Combo counter: Just below and right of the crosshair, as the Cartridge fight HUD (Figma
        // 630:2) places it: the one number that means something only mid-fight belongs where the
        // eyes already are during one.
        place("combo", "center", 40, 24);
        place("saturation", "top-left", 20, 256);
        place("momentum", "top-left", 20, 320);

        // Memory: Above Armor status (160 tall at -20) in the bottom-right column, the corner
        // furthest from the crosshair: diagnostics are read when something is wrong, not while it
        // is going wrong.
        place("memory", "bottom-right", -20, -188);

        // Server address: The next 64 px row of the bottom-right column, over Memory: the other
        // diagnostic.
        place("server_address", "bottom-right", -20, -252);

        // Item counter: The second bottom-left column, right of Keystrokes (156 wide at 20, so
        // 184 with the 8 px gap): the hand-and-clicks corner, what the hand is going through.
        place("item_counter", "bottom-left", 184, -20);

        // Stopwatch: Top of the bottom-right column: read when a fight is over, never mid-swing,
        // so it shares the corner furthest from the crosshair.
        place("stopwatch", "bottom-right", -20, -316);

        // Scoreboard: Right edge, vertically centred — where vanilla draws the sidebar, so
        // turning the mod on restyles it in place before anyone moves it.
        place("scoreboard", "right", -20, 0);

        // Potion counter: Above the Item counter in the 184 column, one 64 px Cartridge row up:
        // bottom to top, what the hand holds and how many heals are left.
        place("potion_counter", "bottom-left", 184, -84);

        // Reach display: The left column's last row on the 64 px Cartridge rhythm, beside
        // Momentum: the numbers a fight produces read together. Combo, the other one, sits beside
        // the crosshair.
        place("reach", "top-left", 20, 384);
    }

    // =================================================================
    // END GENERATED DATA
    // =================================================================

    /** The 45 mod ids, in registry order. */
    public static List<String> modIds() {
        return Collections.unmodifiableList(new java.util.ArrayList<String>(KINDS.keySet()));
    }

    public static boolean isMod(String id) {
        return KINDS.containsKey(id);
    }

    public static Kind kind(String id) {
        return KINDS.get(id);
    }

    public static boolean isGameplay(String id) {
        return KINDS.get(id) == Kind.GAMEPLAY;
    }

    public static boolean isHud(String id) {
        return KINDS.get(id) == Kind.HUD;
    }

    /** Mods-panel filter category of a mod, or {@code null} for an unknown id. */
    public static Category category(String id) {
        return CATEGORIES.get(id);
    }

    /** Panel copy for a mod, e.g. {@code FPS display}; {@code null} for an unknown id. */
    public static String label(String id) {
        return LABELS.get(id);
    }

    /**
     * The factory HUD layout: every HUD mod's starting placement, in registry order.
     *
     * <p>The order is paint order for the {@code hud[]} array {@link Loadout#defaults} builds
     * from it, so it is the registry's and not a set's.</p>
     *
     * <p>Mods that ship off are in here too — a placement is where a widget <em>would</em> go,
     * not whether it is drawn. {@code on} decides that, in the page, in one place.</p>
     */
    public static Map<String, Placement> defaultHud() {
        return Collections.unmodifiableMap(PLACEMENTS);
    }

    /** Factory placement of one HUD mod, or {@code null} for a gameplay mod or unknown id. */
    public static Placement defaultPlacement(String id) {
        return PLACEMENTS.get(id);
    }

    /** Setting keys of a mod, in schema order. */
    public static Set<String> settingKeys(String modId) {
        Map<String, Setting> m = SETTINGS.get(modId);
        return m == null ? Collections.<String>emptySet() : m.keySet();
    }

    /** Factory defaults for one mod as a fresh mutable object. */
    public static JsonObject defaults(String modId) {
        JsonObject out = new JsonObject();
        Map<String, Setting> m = SETTINGS.get(modId);
        if (m == null) {
            return out;
        }
        for (Map.Entry<String, Setting> e : m.entrySet()) {
            out.add(e.getKey(), e.getValue().fallback);
        }
        return out;
    }

    /** Factory default of a single setting, or {@code null} if unknown. */
    public static JsonElement defaultOf(String modId, String key) {
        Map<String, Setting> m = SETTINGS.get(modId);
        if (m == null) {
            return null;
        }
        Setting s = m.get(key);
        return s == null ? null : s.fallback;
    }

    /**
     * Clamps an incoming setting value to what the schema allows.
     *
     * @return the value to store, or {@code null} when the mod, the key or the
     *         value's type make it unusable — the caller then keeps whatever it
     *         already had, which is what {@code setModSetting} returns
     *         ({@code bridge.json#/definitions/setModSetting_returns}).
     */
    public static JsonElement clamp(String modId, String key, JsonElement value) {
        Map<String, Setting> m = SETTINGS.get(modId);
        if (m == null || value == null || value.isJsonNull()) {
            return null;
        }
        Setting s = m.get(key);
        if (s == null || !value.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive p = value.getAsJsonPrimitive();
        switch (s.type) {
            case BOOL:
                if (p.isBoolean()) {
                    return p;
                }
                return null;
            case INT:
            case NUMBER: {
                if (!p.isNumber()) {
                    return null;
                }
                double d = p.getAsDouble();
                if (Double.isNaN(d)) {
                    return null;
                }
                d = Math.max(s.min, Math.min(s.max, d));
                if (s.type == Type.INT) {
                    return new JsonPrimitive(Long.valueOf(Math.round(d)));
                }
                return new JsonPrimitive(Double.valueOf(d));
            }
            case ENUM:
                if (p.isString() && s.values.contains(p.getAsString())) {
                    return p;
                }
                return null;
            case COLOR:
                if (p.isString() && COLOR.matcher(p.getAsString()).matches()) {
                    return p;
                }
                return null;
            case COLOR_RGB:
                // Rejected rather than truncated to six digits. A clamp that quietly rewrote the
                // value would hide the disagreement from the player *and* from the bridge, which
                // reports what was actually stored; refusing it makes the setting keep its last
                // good value, which is what every other rejected clamp here does.
                if (p.isString() && COLOR_RGB.matcher(p.getAsString()).matches()) {
                    return p;
                }
                return null;
            case KEYBIND:
                if (p.isString()) {
                    String up = p.getAsString().toUpperCase(java.util.Locale.ROOT);
                    if (dev.voidpvp.client.input.KeyNames.isValidKeybind(up)) {
                        return new JsonPrimitive(up);
                    }
                }
                return null;
            default:
                return null;
        }
    }

    /** {@code JsonNull} for absent values, so callers never hand out Java null. */
    public static JsonElement nullValue() {
        return JsonNull.INSTANCE;
    }
}
