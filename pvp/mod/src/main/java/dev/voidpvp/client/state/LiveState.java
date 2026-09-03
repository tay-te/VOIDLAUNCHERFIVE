package dev.voidpvp.client.state;

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
 * {@code loadout} frame) and from the render thread (a bridge call), reads
 * happen on the render and client threads every frame. Every mutation is
 * {@code synchronized}; every field an actuator polls is {@code volatile}, so
 * an actuator never has to take a lock in the frame loop.</p>
 */
public final class LiveState {

    /** Where changes go once they have been applied locally. */
    public interface Sink {
        /** A live loadout delta, {@code protocol.json} {@code state}. */
        void state(String loadoutId, Map<String, JsonElement> patch);

        /** A whole HUD layout, {@code protocol.json} {@code hud}. */
        void hud(String loadoutId, List<HudItem> items);
    }

    /** A do-nothing sink, used before the socket exists and in tests. */
    public static final Sink NO_SINK = new Sink() {
        @Override
        public void state(String loadoutId, Map<String, JsonElement> patch) {
        }

        @Override
        public void hud(String loadoutId, List<HudItem> items) {
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
    public volatile boolean toggleSprintShowStatus;

    public volatile boolean fullbrightOn;
    public volatile float fullbrightGamma = 10f;

    public volatile boolean hitboxesOn;
    public volatile float hitboxLineWidth = 2f;
    public volatile int hitboxColor = 0xFFFFFFFF;
    public volatile boolean hitboxEyeLine;

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

    /** Optional in-game toggle for the keystrokes overlay; NONE means always on. */
    public volatile int keystrokesToggleCode;

    // -- global settings -------------------------------------------------
    public volatile int menuKeyCode = KeyDefaults.RSHIFT;
    public volatile int cycleLoadoutKeyCode = KeyDefaults.L;
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
    private final List<JsonObject> library = new ArrayList<JsonObject>();
    private final Map<String, Loadout> fullLoadouts = new LinkedHashMap<String, Loadout>();
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

    public synchronized Loadout loadout() {
        return active;
    }

    public synchronized GlobalSettings settings() {
        return settings;
    }

    /** Loadout summaries from {@code init.loadouts}, in library order. */
    public synchronized List<JsonObject> library() {
        return Collections.unmodifiableList(new ArrayList<JsonObject>(library));
    }

    // -----------------------------------------------------------------
    // Whole-state writes (from net/)
    // -----------------------------------------------------------------

    /** Applies {@code init}: the entire world of state the mod starts from. */
    public synchronized void applyInit(Loadout loadout, List<JsonObject> summaries,
                                       GlobalSettings globals) {
        library.clear();
        if (summaries != null) {
            library.addAll(summaries);
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
        fullLoadouts.put(loadout.id(), loadout);
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
        toggleSprintShowStatus = l.boolSetting("toggle_sprint", "show_status", true);

        fullbrightOn = l.isOn("fullbright");
        fullbrightGamma = (float) l.numberSetting("fullbright", "gamma", 10);

        hitboxesOn = l.isOn("hitboxes");
        hitboxLineWidth = (float) l.numberSetting("hitboxes", "line_width", 2);
        hitboxColor = parseColor(l.stringSetting("hitboxes", "color", "#FFFFFFFF"), 0xFFFFFFFF);
        hitboxEyeLine = l.boolSetting("hitboxes", "show_eye_line", false);

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

        keystrokesToggleCode = dev.voidpvp.client.input.KeyNames.codeOf(
                l.stringSetting("keystrokes", "keybind", "NONE"));
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
     * <p>Returns true when the id names a loadout in the library Java received
     * in {@code init.loadouts}. The switch applies immediately when the full
     * loadout is already known (it is the active one, or Rust has pushed it
     * this session); when only the summary is known the id is remembered and
     * the switch completes as soon as Rust pushes that loadout. The protocol
     * has no "fetch loadout" message, so this is the only way in.</p>
     */
    public synchronized boolean switchLoadout(String id) {
        if (id == null || !isInLibrary(id)) {
            return false;
        }
        if (id.equals(active.id())) {
            return true;
        }
        Loadout target = fullLoadouts.get(id);
        if (target == null) {
            pendingSwitch = id;
            return true;
        }
        pendingSwitch = null;
        applyLoadoutInternal(target, true);
        return true;
    }

    private String pendingSwitch;

    /** The id a {@link #switchLoadout} is still waiting on, or {@code null}. */
    public synchronized String pendingSwitch() {
        return pendingSwitch;
    }

    /** Caches a full loadout pushed by Rust and completes a pending switch. */
    public synchronized boolean cacheLoadout(Loadout loadout) {
        if (loadout == null) {
            return false;
        }
        fullLoadouts.put(loadout.id(), loadout);
        if (loadout.id().equals(pendingSwitch)) {
            pendingSwitch = null;
            applyLoadoutInternal(loadout, true);
            return true;
        }
        return false;
    }

    public synchronized boolean isInLibrary(String id) {
        if (active.id().equals(id)) {
            return true;
        }
        for (JsonObject o : library) {
            if (id.equals(Json.string(o, "id", null))) {
                return true;
            }
        }
        return false;
    }

    /** The id after {@code current} in library order, wrapping — the L key (§6.3). */
    public synchronized String nextLoadoutId() {
        List<String> ids = new ArrayList<String>();
        for (JsonObject o : library) {
            String id = Json.string(o, "id", null);
            if (id != null) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return null;
        }
        int at = ids.indexOf(active.id());
        return ids.get((at + 1) % ids.size());
    }
}
