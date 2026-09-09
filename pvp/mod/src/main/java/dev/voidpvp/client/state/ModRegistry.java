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
 * The closed registry of the twenty mods.
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
    private enum Type { BOOL, INT, NUMBER, ENUM, COLOR, KEYBIND }

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
        // --- HUD mods (15) — they read game state and draw -------------------------------------

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
                "show_host", bool(true));

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
                "background", enumOf("none", "none", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "tight", "normal", "roomy"),
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
                "background", enumOf("none", "none", "subtle", "solid"),
                "border", bool(false),
                "padding", enumOf("normal", "tight", "normal", "roomy"),
                // Which parts of the mark are drawn: `full` is the ring plus the VOID wordmark,
                // `mark` is the ring alone, `word` is the wordmark alone.
                "style", enumOf("full", "full", "mark", "word"));

        // --- Gameplay mods (5) — they mutate a client-side option ------------------------------

        // Toggle sprint — kind gameplay, pvp tab, §11 safe.
        // Latches sprint instead of holding the key.
        // Source: KeyBinding override in onLivingUpdate.
        // `show_status` used to be here and is gone. A sprint indicator is still wanted, but as
        // its own placeable HUD mod: a gameplay mod has no `hud[]` entry, so anything this drew
        // would have been the only fixed, un-movable thing on the HUD — the one property the HUD
        // editor exists to remove. Promoting this mod to `Kind.HUD` for one boolean is a
        // structural change deserving its own decision, and `Kind` and `Category` are already
        // independent, so the machinery supports it.
        mod("toggle_sprint", Kind.GAMEPLAY, Category.PVP, "Toggle sprint",
                // Whether toggle sprint is enabled.
                "on", bool(true),
                // `toggle` latches sprint until the key is pressed again; `hold` restores vanilla
                // hold-to-sprint but keeps the status readout.
                "mode", enumOf("toggle", "toggle", "hold"),
                // Whether the same latching behaviour is applied to sneak.
                "sneak_too", bool(false),
                // Key that toggles the mod in game, captured through
                // `void.openKeybindCapture('toggle_sprint')`. Distinct from the sprint key
                // itself, which is vanilla's and is what `mode` latches: this one turns the
                // latching off, for a fight where holding the key is what the hands expect.
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
                "cinematic", bool(false));

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

        // --- HUD mods (15) — they read game state and draw -------------------------------------

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
        // How many of the item in your hand you have left.
        // Source: `held_count` on the tick payload — the stack size of the held item.
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
                "low_threshold", integer(0, 64, 0));

        // --- The factory HUD layout (15) — where each widget starts ----------------------------

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
    }

    // =================================================================
    // END GENERATED DATA
    // =================================================================

    /** The twenty mod ids, in registry order. */
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
