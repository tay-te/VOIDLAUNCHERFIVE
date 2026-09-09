package dev.voidpvp.client.state;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The mod's authoritative live state (§6.1): the active loadout, the library
 * it can cycle through, the global settings, and the plain fields every
 * actuator Mixin reads.
 *
 * <p>Java owns this. A toggle applies in-process the moment it is written and
 * Rust is told afterwards through {@link Sink}; if the launcher is unreachable
 * the game keeps working and the changes are replayed on reconnect (§6.1).</p>
 *
 * <p>Threading: writes arrive from the WS thread (an {@code init} or
 * {@code loadout} frame) and from the UI thread (a bridge call — Ultralight no
 * longer shares Minecraft's render thread), reads happen on the game thread
 * every frame. Every mutation is {@code synchronized}; every field the game
 * loop polls is {@code volatile}, so it never has to take a lock in the frame
 * loop.</p>
 *
 * <p><b>{@link Loadout} itself is not thread-safe, and does not need to be:
 * every instance this class holds is read and written only under this monitor.
 * </b> That is why the accessors that hand a caller something derived from the
 * active loadout — {@link #loadoutId}, {@link #loadoutJson}, {@link
 * #libraryJson} — exist at all, and why the two settings the game loop reads
 * per frame are mirrored into fields below rather than looked up through
 * {@link #loadout}. A caller that keeps the object {@code loadout()} returns
 * and reads it after the monitor is released is reading a map another thread
 * may be writing.</p>
 */
public final class LiveState {

    /** Where changes go once they have been applied locally. */
    public interface Sink {
        /** A live loadout delta, {@code protocol.json} {@code state}. */
        void state(String loadoutId, Map<String, JsonElement> patch);

        /** A whole HUD layout, {@code protocol.json} {@code hud}. */
        void hud(String loadoutId, List<HudItem> items);

        /**
         * Globals written in game, {@code protocol.json} {@code globals}.
         *
         * <p>The non-loadout half of {@link #state}, and the only reason a global written
         * in game outlives the process: without it {@link #setGlobal} is in-process only
         * and {@code hud_editor_grid} is back at the factory 4 on the next launch.</p>
         *
         * <p>A <b>delta</b>, never the whole object. {@code global_settings} is
         * {@code additionalProperties: true} so the launcher may add a global without a
         * protocol bump, and {@link GlobalSettings} here is five fixed fields that cannot
         * carry one — a whole-object echo would erase it.</p>
         */
        void globals(Map<String, JsonElement> patch);
    }

    /** A do-nothing sink, used before the socket exists and in tests. */
    public static final Sink NO_SINK = new Sink() {
        @Override
        public void state(String loadoutId, Map<String, JsonElement> patch) {
        }

        @Override
        public void hud(String loadoutId, List<HudItem> items) {
        }

        @Override
        public void globals(Map<String, JsonElement> patch) {
        }
    };

    private static final LiveState INSTANCE = new LiveState();

    /** The singleton the Mixins read. Tests build their own instances. */
    public static LiveState get() {
        return INSTANCE;
    }

    // -- actuator fields, read every frame by mixin/ ---------------------
    public volatile boolean toggleSprintOn;
    public volatile boolean toggleSprintHold;
    public volatile boolean toggleSprintSneakToo;

    public volatile boolean fullbrightOn;
    public volatile float fullbrightGamma = 10f;

    public volatile boolean hitboxesOn;
    public volatile float hitboxLineWidth = 2f;
    public volatile int hitboxColor = 0xFFFFFFFF;
    public volatile boolean hitboxEyeLine;
    public volatile int hitboxEyeLineColor = 0xFF7ADFFF;
    /** `hitboxes.max_distance`, in blocks, squared — the mixin compares against a squared length. */
    public volatile double hitboxMaxDistanceSq = 64d * 64d;

    public volatile boolean zoomOn;
    public volatile int zoomKeyCode;
    public volatile double zoomFovDivisor = 4;
    public volatile boolean zoomSmooth = true;
    public volatile boolean zoomCinematic;

    public volatile boolean crosshairOn;
    public volatile String crosshairStyle = "cross";
    public volatile int crosshairSize = 5;
    public volatile int crosshairThickness = 1;
    public volatile int crosshairGap = 2;
    public volatile int crosshairColor = 0xFFFFFFFF;
    public volatile boolean crosshairOutline = true;
    public volatile boolean crosshairDynamic;
    public volatile boolean crosshairCenterDot;

    /** Optional in-game toggle for the keystrokes overlay; NONE means always on. */
    public volatile int keystrokesToggleCode;

    /**
     * The other three `keybind` settings: a key that flips its own mod's `on`, NONE for none.
     *
     * Four mods now carry one, and `keystrokes` was the first — which is why the naming is its
     * and not the other way round. The three below are the ones a player reaches for mid-match:
     * brightness because it depends on where you are standing, boxes because they are worth
     * having for one fight and not the next, and the sprint latch because a chase is exactly
     * when hold-to-sprint is what the hands expect.
     */
    public volatile int fullbrightToggleCode;
    public volatile int hitboxesToggleCode;
    public volatile int toggleSprintToggleCode;

    // -- HUD-mod settings the game loop polls ----------------------------
    //
    // These two are drawn by the page, not by Java, so they would normally live only in the
    // loadout. They are mirrored here because the game loop asks for them on a hot path — the
    // keystrokes hotkey once a frame, the held-item slot once a tick — and reaching through
    // loadout() for them means either taking this monitor 20-60 times a second or reading a
    // Loadout's maps while the UI thread writes them. A volatile boolean is neither.
    public volatile boolean keystrokesOn = true;
    public volatile boolean armorShowHeldItem = true;

    // -- global settings -------------------------------------------------
    public volatile int menuKeyCode = KeyDefaults.RSHIFT;
    public volatile int cycleLoadoutKeyCode = KeyDefaults.L;
    /**
     * Extra multiplier on the in-game UI, on top of {@code VoidClient.pumpUi}'s fit scale.
     *
     * <p><b>Written by the launcher, never by the in-game page.</b> {@code setGlobal} accepts it
     * from either side and clamps to 0.5-3, but the overlay's Settings page deliberately offers
     * no control for it: a control that resizes the surface it lives on can feed its own input in
     * Ultralight, because the relayout moves the control under a stationary pointer, that arrives
     * as a mouse move, and {@code MouseEvent.buttons} reads 0 for an entire drag there — so a
     * drag cannot be told apart from a hover by button state. Observed in game before the control
     * was removed: the scale walked to the 3.0 clamp with 76 resizes in fourteen seconds. Two
     * clients shared the run directory at the time, so the cause was never isolated.
     *
     * <p>So if you are looking for the writer, it is Rust: {@code GlobalSettings} over the WS
     * link, through {@link #applySettings}. This field is live and read every frame.</p>
     */
    public volatile double uiScale = 1;
    public volatile int hudEditorGrid = 4;
    public volatile String theme = "void-dark";

    private static final class KeyDefaults {
        static final int RSHIFT = dev.voidpvp.client.input.KeyNames.codeOf("RSHIFT");
        static final int L = dev.voidpvp.client.input.KeyNames.codeOf("L");
    }

    // -- guarded state ---------------------------------------------------
    private Loadout active = Loadout.defaults("default", "Default");
    private GlobalSettings settings = GlobalSettings.defaults();
    /**
     * The library, in library order, as whole loadouts.
     *
     * <p>{@code init.loadouts} carries complete loadouts (protocol.json,
     * {@code msg_init}), so every entry can be applied on its own. That is what
     * makes {@link #switchLoadout} a sub-frame local operation with no round
     * trip and no "waiting for Rust to push it" state (§8.2).</p>
     */
    private final Map<String, Loadout> library = new LinkedHashMap<String, Loadout>();
    private volatile Sink sink = NO_SINK;
    private volatile boolean initialised;

    public LiveState() {
        applyActuatorFields(active);
    }

    public void setSink(Sink sink) {
        this.sink = sink == null ? NO_SINK : sink;
    }

    /** True once Rust's {@code init} has landed. */
    public boolean isInitialised() {
        return initialised;
    }

    /**
     * The live active loadout.
     *
     * <p><b>The object is mutable and is not guarded once this returns.</b> Use it only for a read
     * that finishes before another thread could write — in practice, only inside this class and in
     * tests. Callers on the game thread want {@link #loadoutId}, {@link #loadoutJson} or one of the
     * mirrored fields above.</p>
     */
    public synchronized Loadout loadout() {
        return active;
    }

    /**
     * The active loadout's id, read under the monitor.
     *
     * <p>Safe where {@code loadout().id()} is not: {@code id} is final, but the reference this
     * dereferences is not, and a switch may replace it between the two calls.</p>
     */
    public synchronized String loadoutId() {
        return active.id();
    }

    /** The active loadout as the {@code loadout} bridge event carries it. */
    public synchronized JsonObject loadoutJson() {
        return active.toJson();
    }

    public synchronized GlobalSettings settings() {
        return settings;
    }

    /** The whole library from {@code init.loadouts}, in library order. */
    public synchronized List<Loadout> library() {
        return Collections.unmodifiableList(new ArrayList<Loadout>(library.values()));
    }

    /** The library as the {@code loadouts} bridge event carries it. */
    public synchronized JsonArray libraryJson() {
        JsonArray arr = new JsonArray();
        for (Loadout l : library.values()) {
            arr.add(l.toJson());
        }
        return arr;
    }

    // -----------------------------------------------------------------
    // Whole-state writes (from net/)
    // -----------------------------------------------------------------

    /** Applies {@code init}: the entire world of state the mod starts from. */
    public synchronized void applyInit(Loadout loadout, List<Loadout> loadouts,
                                       GlobalSettings globals) {
        library.clear();
        if (loadouts != null) {
            for (Loadout l : loadouts) {
                if (l != null) {
                    library.put(l.id(), l);
                }
            }
        }
        applySettings(globals);
        applyLoadoutInternal(loadout, false);
        initialised = true;
    }

    /** Applies a loadout switched outside the game (launcher or tray, §8.2). */
    public synchronized void applyRemoteLoadout(Loadout loadout) {
        applyLoadoutInternal(loadout, false);
    }

    public synchronized void applySettings(GlobalSettings globals) {
        if (globals == null) {
            return;
        }
        this.settings = globals;
        this.menuKeyCode = dev.voidpvp.client.input.KeyNames.codeOf(globals.menuKey);
        this.cycleLoadoutKeyCode = dev.voidpvp.client.input.KeyNames.codeOf(globals.cycleLoadoutKey);
        this.uiScale = globals.uiScale;
        this.hudEditorGrid = globals.hudEditorGrid;
        this.theme = globals.theme;
    }

    private void applyLoadoutInternal(Loadout loadout, boolean report) {
        if (loadout == null) {
            return;
        }
        Loadout previous = active;
        active = loadout;
        // The active loadout is part of the library, and this copy is the live one.
        library.put(loadout.id(), loadout);
        applyActuatorFields(loadout);
        if (report) {
            Map<String, JsonElement> patch = LoadoutDiff.diff(previous, loadout);
            if (!patch.isEmpty()) {
                sink.state(loadout.id(), patch);
            }
        }
    }

    /** Writes every actuator field from the loadout — the whole hot-swap (§8.2). */
    private void applyActuatorFields(Loadout l) {
        toggleSprintOn = l.isOn("toggle_sprint");
        toggleSprintHold = "hold".equals(l.stringSetting("toggle_sprint", "mode", "toggle"));
        toggleSprintSneakToo = l.boolSetting("toggle_sprint", "sneak_too", false);

        fullbrightOn = l.isOn("fullbright");
        fullbrightGamma = (float) l.numberSetting("fullbright", "gamma", 10);

        hitboxesOn = l.isOn("hitboxes");
        hitboxLineWidth = (float) l.numberSetting("hitboxes", "line_width", 2);
        hitboxColor = parseColor(l.stringSetting("hitboxes", "color", "#FFFFFFFF"), 0xFFFFFFFF);
        hitboxEyeLine = l.boolSetting("hitboxes", "show_eye_line", false);
        hitboxEyeLineColor = parseColor(
                l.stringSetting("hitboxes", "eye_line_color", "#7ADFFFFF"), 0xFF7ADFFF);
        // Squared once here rather than per entity per frame: `renderHitbox` runs for every
        // entity the dispatcher draws, and the comparison it wants is against a squared length.
        double maxDistance = l.numberSetting("hitboxes", "max_distance", 64);
        hitboxMaxDistanceSq = maxDistance * maxDistance;

        zoomOn = l.isOn("zoom");
        zoomKeyCode = dev.voidpvp.client.input.KeyNames.codeOf(
                l.stringSetting("zoom", "key", "C"));
        zoomFovDivisor = l.numberSetting("zoom", "fov_divisor", 4);
        zoomSmooth = l.boolSetting("zoom", "smooth", true);
        zoomCinematic = l.boolSetting("zoom", "cinematic", false);

        crosshairOn = l.isOn("crosshair");
        crosshairStyle = l.stringSetting("crosshair", "style", "cross");
        crosshairSize = (int) l.numberSetting("crosshair", "size", 5);
        crosshairThickness = (int) l.numberSetting("crosshair", "thickness", 1);
        crosshairGap = (int) l.numberSetting("crosshair", "gap", 2);
        crosshairColor = parseColor(l.stringSetting("crosshair", "color", "#FFFFFFFF"), 0xFFFFFFFF);
        crosshairOutline = l.boolSetting("crosshair", "outline", true);
        crosshairDynamic = l.boolSetting("crosshair", "dynamic", false);
        crosshairCenterDot = l.boolSetting("crosshair", "center_dot", false);

        keystrokesToggleCode = dev.voidpvp.client.input.KeyNames.codeOf(
                l.stringSetting("keystrokes", "keybind", "NONE"));
        // The other three `keybind` settings, read exactly the same way. Each is a key that
        // flips its own mod's `on`; see `VoidClient.pollHotkeys`, which walks one table rather
        // than repeating the block per mod.
        fullbrightToggleCode = dev.voidpvp.client.input.KeyNames.codeOf(
                l.stringSetting("fullbright", "keybind", "NONE"));
        hitboxesToggleCode = dev.voidpvp.client.input.KeyNames.codeOf(
                l.stringSetting("hitboxes", "keybind", "NONE"));
        toggleSprintToggleCode = dev.voidpvp.client.input.KeyNames.codeOf(
                l.stringSetting("toggle_sprint", "keybind", "NONE"));

        keystrokesOn = l.isOn("keystrokes");
        armorShowHeldItem = l.boolSetting("armor_status", "show_held_item", true);
    }

    /** {@code #RRGGBB} / {@code #RRGGBBAA} to packed ARGB. */
    public static int parseColor(String hex, int fallback) {
        if (hex == null || hex.isEmpty() || hex.charAt(0) != '#') {
            return fallback;
        }
        try {
            String body = hex.substring(1);
            if (body.length() == 6) {
                return 0xFF000000 | (int) Long.parseLong(body, 16);
            }
            if (body.length() == 8) {
                long rgba = Long.parseLong(body, 16);
                int rgb = (int) (rgba >>> 8);
                int a = (int) (rgba & 0xFF);
                return (a << 24) | rgb;
            }
        } catch (NumberFormatException e) {
            return fallback;
        }
        return fallback;
    }

    // -----------------------------------------------------------------
    // Bridge calls (from bridge/, on the render thread)
    // -----------------------------------------------------------------

    /**
     * {@code void.setGameplay(id, on)} — writes the field the actuator Mixin
     * reads and reports the state actually applied (§6.5).
     */
    public synchronized boolean setGameplay(String modId, boolean on) {
        if (!ModRegistry.isGameplay(modId)) {
            return false;
        }
        JsonElement stored = active.putSetting(modId, "on", new com.google.gson.JsonPrimitive(
                Boolean.valueOf(on)));
        applyActuatorFields(active);
        boolean applied = stored != null && stored.isJsonPrimitive() && stored.getAsBoolean();
        sink.state(active.id(), LoadoutDiff.single(modId, "on", stored));
        return applied;
    }

    /**
     * {@code void.setModSetting(id, key, value)} — the generic writer behind
     * every control. Clamps rather than throws; returns the value stored.
     */
    public synchronized JsonElement setModSetting(String modId, String key, JsonElement value) {
        if (!ModRegistry.isMod(modId)) {
            return null;
        }
        JsonElement before = active.setting(modId, key);
        JsonElement stored = active.putSetting(modId, key, value);
        if (stored == null) {
            return null;
        }
        applyActuatorFields(active);
        if (!Json.same(before, stored)) {
            sink.state(active.id(), LoadoutDiff.single(modId, key, stored));
        }
        return stored;
    }

    /**
     * {@code void.setGlobal(key, value)} — the page's writer for the globals of
     * {@code protocol.json#/definitions/global_settings}.
     *
     * <p>The same contract as {@link #setModSetting}: clamp rather than throw, and
     * <b>return what was stored</b> — or {@code null} for a key this build does not keep and for
     * a value it cannot use. The page binds to the return and not to what it sent, which is what
     * lets a slider snap back to 3 when the player drags it to 7.</p>
     *
     * <p>Every accepted key goes through {@link #applySettings}, so the mirrored fields the game
     * loop polls and the {@link GlobalSettings} record a later {@code settings} push serialises
     * cannot disagree — there is one write path, not two. <b>Nothing is emitted from here</b>
     * (§6.5): a change the page made is already known to the page, and pushing it back fights the
     * control the player is holding.</p>
     *
     * <p>Every key is honest — each is read by something:</p>
     * <ul>
     *   <li>{@code ui_scale} 0.5..3 — {@code VoidClient.pumpUi} multiplies it into the view
     *       scale every frame, so the write alone resizes the view; see the note there.</li>
     *   <li>{@code menu_key} — a keybind name, stored as {@link #menuKeyCode}, which
     *       {@code pollHotkeys} reads every frame. {@code NONE} is refused: it is a legal
     *       keybind but it would leave the player with no way to open the menu again, and
     *       {@code isKeyDown} answers false for it forever.</li>
     *   <li>{@code cycle_loadout_key} — same machinery, {@link #cycleLoadoutKeyCode}.
     *       {@code NONE} <em>is</em> allowed here: it disables the L-key cycle, which is a
     *       thing a player may reasonably want and is not a way to get stuck.</li>
     *   <li>{@code hud_editor_grid} 0..64 — {@link #setHud} snaps against it on the next drop.</li>
     *   <li>{@code theme} — the only one Java stores without reading: it is the page's to
     *       apply, and the page gets it back both from this return and from the {@code settings}
     *       channel after a reload, which is exactly what storing it is for.</li>
     * </ul>
     *
     * @return the stored value, or {@code null} for an unknown key or an unusable value
     */
    public synchronized JsonElement setGlobal(String key, JsonElement value) {
        if (key == null || value == null || !value.isJsonPrimitive()) {
            return null;
        }
        com.google.gson.JsonPrimitive p = value.getAsJsonPrimitive();
        GlobalSettings s = settings;
        String menuKey = s.menuKey;
        String cycleKey = s.cycleLoadoutKey;
        String themeName = s.theme;
        double scale = s.uiScale;
        int grid = s.hudEditorGrid;
        JsonElement stored;

        if ("ui_scale".equals(key)) {
            double d = numberOf(p);
            if (Double.isNaN(d)) {
                return null;
            }
            scale = Math.max(0.5, Math.min(3, d));
            stored = Json.number(scale);
        } else if ("hud_editor_grid".equals(key)) {
            double d = numberOf(p);
            if (Double.isNaN(d)) {
                return null;
            }
            grid = (int) Math.round(Math.max(0, Math.min(64, d)));
            stored = new com.google.gson.JsonPrimitive(Integer.valueOf(grid));
        } else if ("menu_key".equals(key)) {
            String name = keybindOf(p);
            if (name == null || "NONE".equals(name)) {
                return null;
            }
            menuKey = name;
            stored = new com.google.gson.JsonPrimitive(name);
        } else if ("cycle_loadout_key".equals(key)) {
            String name = keybindOf(p);
            if (name == null) {
                return null;
            }
            cycleKey = name;
            stored = new com.google.gson.JsonPrimitive(name);
        } else if ("theme".equals(key)) {
            if (!p.isString()) {
                return null;
            }
            String name = p.getAsString();
            // protocol.json: minLength 1, maxLength 32. A theme nothing can name is not a theme.
            if (name.isEmpty() || name.length() > 32) {
                return null;
            }
            themeName = name;
            stored = new com.google.gson.JsonPrimitive(name);
        } else {
            return null;
        }

        // The value as it is actually persisted, read before the write. `toJson()` uses
        // the schema's own property names, which are exactly the keys `setGlobal` takes,
        // so this compares like with like without a second table to fall out of step.
        JsonElement before = settings.toJson().get(key);
        applySettings(new GlobalSettings(menuKey, cycleKey, themeName, scale, grid));
        // Nothing is pushed back to the *page* (§6.5) — but Rust is not the page. It is
        // the only thing that can write settings.json, and it is told the same way every
        // other in-game change is told: after the fact, as a delta.
        if (!Json.same(before, stored)) {
            sink.globals(Collections.singletonMap(key, stored));
        }
        return stored;
    }

    /** A finite number from a primitive, or {@code NaN} for anything else. */
    private static double numberOf(com.google.gson.JsonPrimitive p) {
        if (!p.isNumber()) {
            return Double.NaN;
        }
        double d = p.getAsDouble();
        return Double.isInfinite(d) ? Double.NaN : d;
    }

    /** An upper-cased, schema-valid keybind name, or {@code null}. */
    private static String keybindOf(com.google.gson.JsonPrimitive p) {
        if (!p.isString()) {
            return null;
        }
        String name = p.getAsString().toUpperCase(java.util.Locale.ROOT);
        return dev.voidpvp.client.input.KeyNames.isValidKeybind(name) ? name : null;
    }

    /**
     * {@code void.setHud(id, placement)} — applies the drop to the live layout
     * and mirrors the whole layout to Rust, which is what persists it.
     */
    public synchronized HudItem setHud(String modId, String anchor, double dx, double dy,
                                       Double scale) {
        if (!ModRegistry.isHud(modId)) {
            return null;
        }
        HudItem existing = active.hudItem(modId);
        double s = scale != null ? scale.doubleValue()
                : (existing != null ? existing.scale : 1);
        HudItem item = new HudItem(modId, anchor, dx, dy, s).normalised(hudEditorGrid);
        active.putHud(item);
        sink.hud(active.id(), active.hud());
        return item;
    }

    /**
     * {@code void.switchLoadout(id)} — hot-swaps in-process (§8.2).
     *
     * <p>Always immediate. {@code init.loadouts} carries whole loadouts, so
     * every id in the library is one the mod can apply on the spot: there is no
     * round trip, no pending state and no "fetch loadout" message in the
     * protocol, which is exactly why the whole library is sent up front.</p>
     *
     * @return true when the id named a loadout in the library
     */
    public synchronized boolean switchLoadout(String id) {
        if (id == null) {
            return false;
        }
        if (id.equals(active.id())) {
            return true;
        }
        Loadout target = library.get(id);
        if (target == null) {
            return false;
        }
        applyLoadoutInternal(target, true);
        return true;
    }

    /** Stores a loadout Rust pushed, without switching to it. */
    public synchronized void cacheLoadout(Loadout loadout) {
        if (loadout != null) {
            library.put(loadout.id(), loadout);
        }
    }

    public synchronized boolean isInLibrary(String id) {
        return active.id().equals(id) || library.containsKey(id);
    }

    /** The id after {@code current} in library order, wrapping — the L key (§6.3). */
    public synchronized String nextLoadoutId() {
        List<String> ids = new ArrayList<String>(library.keySet());
        if (ids.isEmpty()) {
            return null;
        }
        int at = ids.indexOf(active.id());
        return ids.get((at + 1) % ids.size());
    }
}
