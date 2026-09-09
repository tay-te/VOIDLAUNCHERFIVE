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
 * The closed registry of the 33 mods.
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
                "background", enumOf("subtle", "bare", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "none", "tight", "normal", "roomy", "wide"),
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
                "background", enumOf("subtle", "bare", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "none", "tight", "normal", "roomy", "wide"),
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
                "pressed_color", enumOf("accent", "accent", "sky", "warn", "fear", "teal"));

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
                "background", enumOf("subtle", "bare", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "none", "tight", "normal", "roomy", "wide"),
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
                "show_peak", bool(false));

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
                "background", enumOf("subtle", "bare", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "none", "tight", "normal", "roomy", "wide"),
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
                // Whether the mean change between consecutive readings is drawn as a trailing
                // aside — `· ± 8 ms`. Derived in JS from the `ping` field over a ~1.5 s window,
                // the way `fps.show_low` and the whole CPS counter are; the wire carries
                // readings, and a statistic over readings is a policy over them (`bridge.json`,
                // `hits`, makes the same argument about the combo). **It is the reading the
                // figure beside it cannot give.** 40 ms that never moves plays better than 25
                // that swings, and a single latency number says nothing about which you have —
                // which is why "my ping says 30 and it feels awful" is a complaint every client
                // gets and none of them answer. `docs/mod-roster.md` §8 names ping jitter as one
                // of three items in its third gap: depth on mods that already exist, which never
                // shows up on a feature-count comparison and is what a returning player notices
                // in the first ten minutes. The statistic is the mean absolute *step*, not a
                // standard deviation about the mean, and the distinction is the point rather than
                // a detail: a link that sits at 30 for a second and then at 90 has a large
                // deviation and feels fine, while one alternating 30, 90, 30, 90 has the same
                // deviation and is unplayable. What a player feels is the step. RFC 3550 defines
                // interarrival jitter the same way for the same reason. Off by default. The
                // figure is the reading and this is the gloss on it; a chip that ships with both
                // has decided for you, which is the argument `memory.show_bar` and
                // `hit_trade.show_bar` already make.
                "show_jitter", bool(false));

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
                "background", enumOf("subtle", "bare", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "none", "tight", "normal", "roomy", "wide"),
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
                "background", enumOf("subtle", "bare", "subtle", "solid"),
                // shared_overrides: ships true where every other hud mod ships false. Whether a
                // hairline is drawn around the armor row, at the system's own `--border-panel`
                // alpha. Ships **on** for this mod, unlike the other fifteen. This is a panel
                // rather than a chip — 150 px of rows over live game — and the sheet has always
                // drawn it with a `--border-dock` edge, which is what separates a list of rows
                // from the world behind it. That edge used to be written into the rule
                // unconditionally, so the setting existed and could not turn it off; moving it
                // onto the shared `border` switch is what makes the control real, and this
                // default is what stops that fix from silently stripping the edge off every
                // loadout on disk.
                "border", bool(true),
                "padding", enumOf("normal", "none", "tight", "normal", "roomy", "wide"),
                // Whether armor pieces are laid out left to right or top to bottom.
                "orientation", enumOf("horizontal", "horizontal", "vertical"),
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
                "background", enumOf("subtle", "bare", "subtle", "solid"),
                // shared_overrides: ships true where every other hud mod ships false. Whether a
                // hairline is drawn around the effect list, at the system's own `--border-panel`
                // alpha. Ships **on** for this mod, unlike the other fifteen. This is a panel
                // rather than a chip — 150 px of rows over live game — and the sheet has always
                // drawn it with a `--border-dock` edge, which is what separates a list of rows
                // from the world behind it. That edge used to be written into the rule
                // unconditionally, so the setting existed and could not turn it off; moving it
                // onto the shared `border` switch is what makes the control real, and this
                // default is what stops that fix from silently stripping the edge off every
                // loadout on disk.
                "border", bool(true),
                "padding", enumOf("normal", "none", "tight", "normal", "roomy", "wide"),
                // Whether to print the remaining duration next to each effect.
                "show_duration", bool(true),
                // Whether to print the roman-numeral amplifier next to each effect name.
                "show_amplifier", bool(true),
                // Whether to omit ambient effects such as a beacon aura from the list.
                "hide_ambient", bool(false));

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
                "background", enumOf("subtle", "bare", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "none", "tight", "normal", "roomy", "wide"),
                // Which parts of the mark are drawn: `full` is the ring plus the VOID wordmark,
                // `mark` is the ring alone, `word` is the wordmark alone.
                "style", enumOf("full", "full", "mark", "word"));

        // --- Gameplay mods (14) — they mutate a client-side option -----------------------------

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
                "keybind", keybind("NONE"));

        // Zoom — kind gameplay, utility tab, §11 safe.
        // Narrows FOV while the zoom key is held.
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
                // Mouse sensitivity while the zoom is engaged, as a fraction of your normal
                // sensitivity. 1 leaves it alone, which is the default because it is what the mod
                // did before this existed. **Why it needs a setting at all.** Zoom divides the
                // field of view without changing what a mouse count does to your yaw, so at 4x
                // every millimetre of desk turns you four times as far *across the visible scene*
                // — the aim that lands a bow shot at 1x is unusable at 4x. `docs/mod-roster.md`
                // §3.4 #1 names exactly this: "the absence of sensitivity scaling is felt every
                // single time you zoom", and files it under finishing a mod that already exists
                // rather than under a new one. **Why the honest default is not `1 /
                // fov_divisor`.** That is the value which keeps the *on-screen* angular rate
                // identical, and it is what a player who has never tried it asks for; in practice
                // it is too slow, because at 4x you are also making a smaller correction. The
                // range bottoms out at 0.2 so that answer is reachable at every divisor the mod
                // offers, and the default stays out of the way. It multiplies vanilla's own
                // sensitivity rather than replacing it, so a player who has already tuned their
                // sensitivity keeps that tuning and scales it. Applied where the game computes
                // its look step — `GameRenderer.render`'s read of `GameOptions.sensitivity`, the
                // one the cubic curve is built from — so this is a fraction of the *setting*, not
                // of the resulting angle, and it eases in and out with the zoom rather than
                // snapping when the key goes down.
                "sensitivity", number(0.2, 1, 1));

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
                "background", enumOf("subtle", "bare", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "none", "tight", "normal", "roomy", "wide"),
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
                "background", enumOf("subtle", "bare", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "none", "tight", "normal", "roomy", "wide"),
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
                "background", enumOf("subtle", "bare", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "none", "tight", "normal", "roomy", "wide"),
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
                "background", enumOf("subtle", "bare", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "none", "tight", "normal", "roomy", "wide"),
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
                "background", enumOf("subtle", "bare", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "none", "tight", "normal", "roomy", "wide"),
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
                "background", enumOf("subtle", "bare", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "none", "tight", "normal", "roomy", "wide"),
                // How much of the host is printed. `short` keeps the part players actually say
                // out loud — `hypixel` out of `mc.hypixel.net` — and is the default because on a
                // chip the leading `mc.` and the trailing `.net` are the two pieces that never
                // differ between the servers a player switches between, so they cost width and
                // carry no information. `full` prints the address exactly as it was connected to,
                // which is what you want on a network with several proxies, on a bare IP, or in a
                // screenshot that has to be reproducible by somebody else.
                "style", enumOf("short", "short", "full"));

        // Item counter — kind hud, pvp tab, §11 safe.
        // How many of the item in your hand you have left.
        // Source: `held_count` on the tick payload — the stack size of the held item.
        mod("item_counter", Kind.HUD, Category.PVP, "Item counter",
                // Whether the item counter is enabled.
                "on", bool(false),
                // The shared hud block, schema/mods/_shared.json#/hud — the same keys, with the
                // same meaning, on every hud mod.
                "scale", number(0.25, 4, 1),
                "opacity", number(0, 1, 1),
                "background", enumOf("subtle", "bare", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "none", "tight", "normal", "roomy", "wide"),
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
                "low_threshold", integer(0, 64, 0));

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
                "background", enumOf("subtle", "bare", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "none", "tight", "normal", "roomy", "wide"),
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

        // --- Gameplay mods (14) — they mutate a client-side option -----------------------------

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

        // --- HUD mods (19) — they read game state and draw -------------------------------------

        // Trade counter — kind hud, pvp tab, §11 safe.
        // Hits you have landed against hits you have taken, this session.
        // Source: the monotonic `hits.dealt` / `hits.taken` counters, via the tick sensor.
        mod("hit_trade", Kind.HUD, Category.PVP, "Trade counter",
                // Whether the trade counter is enabled.
                "on", bool(false),
                // The shared hud block, schema/mods/_shared.json#/hud — the same keys, with the
                // same meaning, on every hud mod.
                "scale", number(0.25, 4, 1),
                "opacity", number(0, 1, 1),
                "background", enumOf("subtle", "bare", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "none", "tight", "normal", "roomy", "wide"),
                // What the chip prints. `traded` — `12 / 4` — is the default because both figures
                // are the reading: a ratio of 3 is the same number at 3/1 as at 30/10, and only
                // one of those is a session worth reviewing. `ratio` is that comparison pre-done,
                // which is the narrowest form that still means something and the right one for a
                // player who already knows roughly how long they have been playing. `dealt` is
                // the bare count of landed hits, for a player who wants the chip to be one figure
                // wide.
                "style", enumOf("traded", "traded", "ratio", "dealt"),
                // Whether a fill bar is drawn under the figure, showing the share of hits in the
                // session that were yours — `dealt / (dealt + taken)`. Off by default because the
                // figures are the reading and the bar is the gloss on it, and a HUD that ships
                // with both is a HUD that has decided for you. On, it is the fastest form there
                // is: half full is an even session, and which side of half you are on is legible
                // without reading a digit. Monochrome, like the two chips that already draw this
                // bar: a share has no threshold, so there is no state for a colour to mark
                // (`design/quiet-cell-system.md` §1).
                "show_bar", bool(false),
                // Whether the trailing `TRADE` unit is drawn. Off makes the chip narrower at the
                // cost of leaving `12 / 4` with nothing to say what it counts — which on a HUD
                // that may also be carrying a combo count and a CPS pair is a real ambiguity, so
                // this ships on.
                "show_label", bool(true));

        // Clock — kind hud, utility tab, §11 safe.
        // The real-world time, for a session with somewhere to be after it.
        // Source: the machine's own clock, in the page — no sensor.
        mod("clock", Kind.HUD, Category.UTILITY, "Clock",
                // Whether the clock is enabled.
                "on", bool(false),
                // The shared hud block, schema/mods/_shared.json#/hud — the same keys, with the
                // same meaning, on every hud mod.
                "scale", number(0.25, 4, 1),
                "opacity", number(0, 1, 1),
                "background", enumOf("subtle", "bare", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "none", "tight", "normal", "roomy", "wide"),
                // How the time is written. `h24` — `21:41` — is the default because it is one
                // width at every hour, which is what a readout anchored by its corner wants: a
                // twelve-hour chip changes width at one o'clock and at ten, and a HUD item that
                // reflows twice a day is a HUD item that moves. `h12` is `9:41 PM`, for a player
                // who reads that form faster than they can subtract twelve.
                "format", enumOf("h24", "h24", "h12"),
                // Whether seconds are drawn. Off by default, and the reason is the repaint rather
                // than the width: with seconds off the drawn figure changes once a *minute*, so
                // the chip's timer is armed for the next minute boundary and sleeps through the
                // other fifty-nine seconds. On, it repaints once a second — still the cheapest
                // live readout on the HUD, but sixty times the cost of a figure nobody is
                // watching tick.
                "show_seconds", bool(false));

        // CPS graph — kind hud, pvp tab, §11 safe.
        // The shape of your clicking over the last few seconds, not just the current rate.
        // Source: the same click edges as the CPS counter, summarised once a second in the page.
        mod("cps_graph", Kind.HUD, Category.PVP, "CPS graph",
                // Whether the CPS graph is enabled.
                "on", bool(false),
                // The shared hud block, schema/mods/_shared.json#/hud — the same keys, with the
                // same meaning, on every hud mod.
                "scale", number(0.25, 4, 1),
                "opacity", number(0, 1, 1),
                "background", enumOf("subtle", "bare", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "none", "tight", "normal", "roomy", "wide"),
                // Which button the graph plots. `left` is the default because it is the attack
                // button and the one a fight is fought with; `right` is for a player training a
                // block-hit or a bow rhythm; `both` sums them, which is the only honest way to
                // draw two hands in one row of columns — two overlaid series in one 60px-wide
                // widget is two shapes nobody can separate over live game pixels.
                "mode", enumOf("left", "left", "right", "both"),
                // How many seconds the graph covers, one column per second. 20 is the default
                // because it is about the length of a fight: 10 is a burst and shows nothing
                // about whether a rate held, and 60 puts a whole minute into a widget narrow
                // enough to sit on a HUD, where each column is under a pixel of information. The
                // columns get narrower as this grows rather than the widget wider — a HUD item
                // that changed width with a setting would move under an anchor that is a corner.
                "window_s", integer(10, 60, 20),
                // Whether the current rate is drawn as a figure beside the graph. On by default:
                // the shape is what this mod adds and the figure is what makes the shape
                // readable, since a column chart with no scale on it is a picture of a rate
                // rather than a reading of one. Off for a player who already has the CPS counter
                // on their HUD and does not want the number twice.
                "show_figure", bool(true));

        // --- Gameplay mods (14) — they mutate a client-side option -----------------------------

        // Scoreboard — kind gameplay, hud tab, §11 safe.
        // Hide, shrink or move the server's sidebar, which vanilla nails to the right of the
        // screen.
        // Source: vanilla's own `InGameHud.renderScoreboardObjective`, wrapped.
        mod("scoreboard", Kind.GAMEPLAY, Category.HUD, "Scoreboard",
                // Whether the scoreboard customiser is enabled.
                "on", bool(false),
                // Whether the sidebar is drawn at all. Off by default, because a scoreboard is
                // the only thing telling you the score in half the game modes it appears in and a
                // mod that ships hiding it would be a mod that breaks Bedwars for anyone who
                // enables it without reading. On, nothing is drawn — the whole method is skipped,
                // so it costs less than vanilla rather than more.
                "hide", bool(false),
                // Size of the sidebar, as a multiplier on vanilla's. 1 is untouched. Applied
                // about the sidebar's own anchor — the point on the right edge at half height
                // that the method hangs it from — so shrinking it keeps it in its corner rather
                // than sliding it towards the middle. The floor is 0.5 because vanilla's font is
                // a bitmap: below half size the glyphs stop resolving into letters, and a
                // scoreboard nobody can read is `hide` with extra steps.
                "sidebar_scale", number(0.5, 1.5, 1),
                // Horizontal nudge in scaled screen pixels, positive to the right. The useful
                // direction is negative — pulling the sidebar in off the edge — but both are
                // offered because a player who has shrunk it may want it flush again.
                "offset_x", integer(-200, 200, 0),
                // Vertical nudge in scaled screen pixels, positive downwards. This is the setting
                // most players actually want: the sidebar sits at half height, which on a 16:9
                // screen is straight through the middle of a fight, and moving it up puts it
                // above the horizon where a bow arc lives instead. It is a row rather than a drag
                // handle on a preview, and that is a stated limitation rather than an oversight —
                // see this mod's `$comment`. The HUD editor places *this client's* widgets, and
                // every pixel here is vanilla's.
                "offset_y", integer(-200, 200, 0));

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
        place("fps", "top-left", 23, 23);
        place("keystrokes", "bottom-left", 31, -109);
        place("cps", "bottom-left", 175, -108);
        place("ping", "top-left", 23, 65);
        place("coordinates", "top-left", 23, 103);
        place("armor_status", "top-right", -25, 299);
        place("potion_effects", "top-right", -25, 23);

        // Watermark: The next row of the left column, under Coordinates at 103. **This number is
        // deliberately not `loadout.json`'s.** The Sword PvP example there places the mark at
        // `top-left 20,58`, under an fps at `dy 20` and a ping at `dy 38` — an 18-20 px rhythm,
        // which is a hand-authored product loadout rather than the factory layout. This table's
        // rhythm is 38-42 px, and 58 here would land the mark on top of the ping chip at 65
        // rather than under it. Same intent — third in the top-left stack, which is where every
        // PvP client puts its mark — expressed in the space the page actually lays out in.
        place("watermark", "top-left", 23, 141);

        // Direction: The next row of the same column, on this table's 38 px rhythm. Under
        // Coordinates on purpose: it is the mod a player confuses with Coordinates, and stacking
        // them makes the difference — a position, versus a facing — visible at a glance rather
        // than argued about.
        place("direction", "top-left", 23, 179);

        // Combo counter: The sixth and last row of the left column, 38 px under Direction on this
        // table's rhythm. NOT "under Coordinates, the mod players confuse it with" — that
        // argument belongs to Direction and Direction has held `dy 179` since it was added. What
        // this row actually is, is the *bottom* of the column the eye already sweeps: the five
        // above it are ambient readouts checked between fights, and the combo is the one that
        // means nothing except during one. Last in the stack puts a fight-time number somewhere
        // already looked at without dropping it into the middle of that sweep.
        place("combo", "top-left", 23, 217);
        place("saturation", "top-left", 23, 255);
        place("momentum", "top-left", 23, 293);

        // Memory: Opens the bottom-right corner, which the factory layout does not otherwise use.
        // Both mods that live there — this and Server address, 38 px above it — are
        // *diagnostics*: you read them when something is wrong, not while it is going wrong.
        // Keeping them opposite the top-left reference stack means the glance for "is the client
        // healthy" never crosses the column holding the glance for "where am I". The insets match
        // the corners already in use: 25 px in from the right edge as the top-right stack is, 23
        // px up from the bottom as the top-left stack is down from the top.
        place("memory", "bottom-right", -25, -23);
        place("server_address", "bottom-right", -25, -61);

        // Item counter: Above the CPS chip, in the hand-and-clicks corner, and in the **175
        // column rather than the 31 one**. The bottom-left corner has two columns because
        // Keystrokes at `dx 31` is a *tall* widget: a WASD block with a mouse row and a space bar
        // under it is over a hundred design-canvas pixels of column, so 31 is spoken for far
        // above its own `dy -109`, and anything stacked there lands on the caps. 175 is the
        // column that already answers the same question this mod does — CPS at `dy -108` is how
        // fast the hand is going, and the held stack directly above it at -146 is what the hand
        // is going *through*. The 38 px gap is this table's rhythm.
        place("item_counter", "bottom-left", 175, -146);

        // Stopwatch: Third row of the bottom-right corner, 38 px above Server address (`-61`) and
        // 76 above Memory (`-23`), continuing upward the rhythm those two opened. It is not a
        // diagnostic like its two neighbours, and that is the argument for putting it there
        // rather than against: Memory and Server address are read when something is wrong, and a
        // stopwatch is read when a fight is over. Neither is a glance you take mid-swing, so both
        // belong in the corner furthest from the crosshair, and the top-left column stays what it
        // is — the things you sweep while playing.
        place("stopwatch", "bottom-right", -25, -99);

        // Trade counter: Under Combo counter in the top-left reference stack, 38 px below it on
        // the same column rhythm, because the two are read as a pair: the fight, and the session
        // it is part of. The same argument put Combo under Coordinates — a mod goes next to the
        // mod it will be confused with, so the difference is visible rather than inferred.
        place("hit_trade", "top-left", 23, 255);

        // Clock: Top-right, above Ping display's 20px inset, on the same right-edge column as the
        // two network readouts. That corner is where a glance goes for "the world outside this
        // fight" — which server, how far away, and now what time it is — and keeping the clock
        // out of the top-left reference stack means it never sits between two numbers a player
        // reads mid-match.
        place("clock", "top-right", -25, 62);

        // CPS graph: Under CPS counter in the top-left reference stack, 38 px below it on the
        // same column rhythm. The two are one measurement drawn twice and are placed as a pair
        // for the reason Combo and Trade counter are: a mod goes next to the mod it will be
        // confused with, so the difference is visible rather than inferred.
        place("cps_graph", "top-left", 23, 293);
    }

    // =================================================================
    // END GENERATED DATA
    // =================================================================

    /** The 33 mod ids, in registry order. */
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
