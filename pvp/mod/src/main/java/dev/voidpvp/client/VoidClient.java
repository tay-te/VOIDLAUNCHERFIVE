package dev.voidpvp.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.voidpvp.client.actuator.SprintLatch;
import dev.voidpvp.client.actuator.ZoomController;
import dev.voidpvp.client.bridge.BridgeHost;
import dev.voidpvp.client.bridge.VoidBridge;
import dev.voidpvp.client.input.EdgeKey;
import dev.voidpvp.client.input.KeyNames;
import dev.voidpvp.client.net.SessionStats;
import dev.voidpvp.client.net.VoidSocket;
import dev.voidpvp.client.render.CrosshairRenderer;
import dev.voidpvp.client.screen.VoidMenuScreen;
import dev.voidpvp.client.sensor.ArmorSlot;
import dev.voidpvp.client.sensor.KeyStateTracker;
import dev.voidpvp.client.sensor.PotionFx;
import dev.voidpvp.client.sensor.ServerWatcher;
import dev.voidpvp.client.sensor.TickCoalescer;
import dev.voidpvp.client.state.GlobalSettings;
import dev.voidpvp.client.state.LiveState;
import dev.voidpvp.client.state.Loadout;
import dev.voidpvp.client.ui.UiHost;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.Session;
import net.minecraft.client.util.Window;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The mod. Reads {@code -Dvoid.port} / {@code -Dvoid.token}, starts the WS
 * client, brings Ultralight up lazily on the first frame, and owns the wiring
 * between the sensors, the actuators, the bridge and the launcher.
 *
 * <p>Nothing here is configured from disk: state arrives in {@code init} and is
 * mirrored back on change (§6.1). If the launcher never answers, the defaults
 * in {@link LiveState} carry the session and everything is flushed when it
 * does.</p>
 */
public final class VoidClient implements ClientModInitializer, BridgeHost, VoidSocket.Listener {

    /** Kept in step with {@code mod_version} in gradle.properties. */
    public static final String MOD_VERSION = "0.1.0";
    public static final String MC_VERSION = "1.8.9";

    /**
     * The frame every screen in {@code design/} is drawn in (design/README.md: "11 frames,
     * each 1300 x 820"). The in-game UI is that fixed canvas, not a responsive layout — the
     * menu panel is a 960 x 600 box centred in it — so the view has to be given at least this
     * many CSS pixels or the panel is clipped by {@code .void-app}'s {@code overflow: hidden}.
     */
    // The canvas the UI lays out in, in CSS pixels. The menu is a *panel* centred inside it —
    // 1600 x 980, the source-of-truth frame size — with the game visible around the edges, so the
    // canvas is the panel plus that margin (1600 / 0.92, 980 / 0.92). The old 1300 x 820 was the
    // panel-era canvas and left the viewport 1459 CSS wide, which is narrower than the 1594 the
    // eight-column grid needs: the third column was being clipped behind a scrollbar.
    private static final double DESIGN_WIDTH = 1300;
    private static final double DESIGN_HEIGHT = 820;

    private static VoidClient instance;

    private final LiveState state = LiveState.get();
    private final VoidBridge bridge = new VoidBridge(state, this);
    private final UiHost ui = new UiHost(bridge);

    private final KeyStateTracker keys = new KeyStateTracker();
    private final TickCoalescer ticks = new TickCoalescer();
    private final ServerWatcher server = new ServerWatcher();
    private final SprintLatch sprint = new SprintLatch();
    private final SprintLatch sneak = new SprintLatch();
    private final ZoomController zoom = new ZoomController();

    private final EdgeKey menuKey = new EdgeKey();
    /**
     * Set by {@code VoidMenuScreen} when the menu key arrives as a key *event*.
     *
     * <p>{@link #pollHotkeys} samples that key once per frame, so its edge detector can only see
     * a press that straddles two samples — a window as long as one frame. That was fine at a few
     * hundred fps and is not fine with the menu open, where the frame is long enough to swallow
     * an ordinary tap whole and Right Shift simply does not close. Minecraft delivers real key
     * events to the open screen, so the screen latches the tap here and the poll consumes it;
     * detection stops depending on frame rate. The poll keeps ownership of open/close — the
     * screen must not close itself, or the still-held key reopens it on the next frame.</p>
     */
    private volatile boolean menuKeyTapped;
    /**
     * When the menu was last toggled, for the debounce below.
     *
     * <p>LWJGL 2 on macOS reports the two Shift keys inconsistently between its event stream
     * (which said {@code LSHIFT}) and its state API (which said {@code RSHIFT}), and auto-repeat
     * inside a {@code GuiScreen} multiplies whatever it gets wrong. Two fixes aimed at the key
     * itself both failed, so this one does not trust the key at all: whatever the platform
     * reports, the menu cannot toggle twice inside {@link #MENU_TOGGLE_DEBOUNCE_MS}. A person
     * cannot tap faster than that on purpose; a stuck or repeating key can.</p>
     */
    private long lastMenuToggleMs;

    /** Minimum gap between two menu toggles. Below a comfortable double-tap, above any repeat. */
    private static final long MENU_TOGGLE_DEBOUNCE_MS = 600L;

    /**
     * Whether the menu key is allowed to toggle again.
     *
     * <p>Disarmed by every toggle, re-armed only once the key has read *up* for
     * {@link #MENU_REARM_FRAMES} consecutive frames. The probe showed a single press producing
     * a close immediately followed by a re-open a few hundred ms later — outside the debounce
     * window, with the key still physically held — which is LWJGL 2's macOS modifier state
     * momentarily reading false and then true again and presenting as a fresh rising edge.
     * Requiring a *sustained* release means a glitch of a frame or two cannot re-arm it, and a
     * real release always can.</p>
     */
    private boolean menuArmed = true;

    /**
     * How long the key must read *up*, in milliseconds, before another toggle is allowed.
     *
     * <p>Frames were the wrong unit: four frames at 115 fps is 35 ms, far shorter than the
     * modifier glitch this exists to survive, and the gate re-armed in time for the same press
     * to reopen the menu. A stack trace proved the reopen came from this poll's own
     * {@code setScreen}. Wall-clock instead, so the gate does not get weaker as the game gets
     * faster — the thing it guards against is a hardware/driver artefact measured in
     * milliseconds, not in frames.</p>
     */
    private static final long MENU_REARM_UP_MS = 180L;

    /** When the key was last seen up, or 0 while it is held. */
    private long menuUpSinceMs;

    /** The other half of a left/right modifier pair, or 0 — LWJGL conflates them on macOS. */
    private static int siblingModifier(int code) {
        switch (code) {
            case 42: return 54;
            case 54: return 42;
            case 29: return 157;
            case 157: return 29;
            case 56: return 184;
            case 184: return 56;
            default: return 0;
        }
    }
    private final EdgeKey cycleKey = new EdgeKey();
    private final EdgeKey keystrokesKey = new EdgeKey();

    private VoidSocket socket;
    /**
     * Whether the page has been handed a loadout to render.
     *
     * <p>Only ever set by {@link #maybePushLocalState}. With a launcher behind it,
     * {@link #onInit} is normally the push and this stays false — unless that {@code init} never
     * arrives, which is what the grace below is for.</p>
     */
    private boolean localStatePushed;

    /** Ticks {@link #maybePushLocalState} has waited for a launcher-attached client's init. */
    private int initWaited;

    /**
     * How long a client with a launcher waits for {@code init} before falling back to its own
     * defaults: 100 ticks, so five seconds at the 20 Hz this is counted on.
     *
     * <p>Ticks rather than wall clock deliberately. This is counted while the world loads, which
     * is exactly when the game stalls and the launcher is merely slow rather than gone; a wall
     * clock would fire the fallback during that hitch, whereas a tick counter simply stops
     * counting and waits longer. Erring towards waiting is right — the cost of firing late is a
     * few more seconds of an unstyled panel, and the cost of firing early is the player's real
     * loadout being replaced on screen by the factory defaults.</p>
     */
    private static final int INIT_GRACE_TICKS = 100;

    private SessionStats stats;
    private Float savedGamma;
    /** Vanilla's cinematic-camera setting, saved while `zoom.cinematic` overrides it. */
    private Boolean savedSmoothCamera;
    /** Last value this mod wrote to the hitbox flag, so F3+B keeps ownership between changes. */
    private boolean hitboxesApplied;
    /** Whether the sprint / sneak KeyBinding is currently held down by our latch, not the player. */
    private final Flag sprintForced = new Flag();
    private final Flag sneakForced = new Flag();
    /**
     * Keybind capture, armed from the UI thread and read on the game thread.
     *
     * <p>{@code volatile} because {@code beginKeybindCapture} is one of the two bridge calls the
     * bridge runs inline (see the classification in {@link VoidBridge}): arming has to be visible
     * to {@code VoidMenuScreen.keyPressed} before the player's next key press, which is sooner
     * than the next drain. Two independent flags are enough here — {@code captureModId} is only
     * ever read while {@code captureActive} is true, and a capture is armed and consumed by one
     * player action at a time.</p>
     */
    private volatile String captureModId;
    private volatile boolean captureActive;
    private long lastFrameNanos;

    /**
     * Who is playing, built once and never rebuilt.
     *
     * <p>{@code volatile} because {@link #sessionJson()} is called from the UI thread through
     * {@link VoidBridge#pushWholeState()} while this is written on the game thread, and because a
     * reloaded document asks for it again long after start-up.</p>
     *
     * <p>Built lazily on first ask rather than in {@code onInitializeClient}: the session exists
     * by then, but reading it from the initializer would mean touching {@code MinecraftClient}
     * before the game has finished standing up, for a value nothing needs until a page asks.</p>
     */
    private volatile JsonObject session;

    public static VoidClient get() {
        return instance;
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        stats = new SessionStats(System.currentTimeMillis());

        int port = intProperty("void.port", 0);
        String token = System.getProperty("void.token", "");
        if (port <= 0 || token.isEmpty()) {
            VoidLog.warn("-Dvoid.port / -Dvoid.token not set; running without the launcher link");
        } else {
            socket = new VoidSocket(port, token, MC_VERSION, MOD_VERSION, this);
            state.setSink(socket);
            socket.start();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                shutdown();
            }
        }, "void-shutdown"));

        VoidLog.info("void-client " + MOD_VERSION + " ready");
    }

    private static int intProperty(String key, int fallback) {
        try {
            return Integer.parseInt(System.getProperty(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // -----------------------------------------------------------------
    // Accessors used by screen/ and mixin/
    // -----------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Minecraft's own {@code Session}, not the launcher's account: the game knows who is
     * signed in with or without a bridge, so the chip is right in a dev client. "Offline" is
     * derived rather than asked for — {@code Session.getAccountType()} answers LEGACY or MOJANG
     * and knows nothing about Microsoft or about an offline launch, whereas an offline UUID is
     * exactly {@code UUID.nameUUIDFromBytes("OfflinePlayer:<name>")} by construction, so
     * comparing against that is the one test that cannot be wrong about which it is.</p>
     */
    @Override
    public JsonObject sessionJson() {
        JsonObject cached = session;
        if (cached != null) {
            return cached;
        }
        MinecraftClient mc = minecraft();
        Session s = mc == null ? null : mc.getSession();
        if (s == null) {
            return null;
        }
        String name = s.getUsername() == null ? "" : s.getUsername();
        String uuid = s.getUuid() == null ? "" : s.getUuid();
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("uuid", uuid);
        o.addProperty("kind", isOfflineUuid(name, uuid) ? "offline" : "microsoft");
        session = o;
        return o;
    }

    /**
     * True unless this is a real signed-in account.
     *
     * <p>Two ways to be offline, and the dev client found the second one. The documented case is
     * the derived uuid — an offline launch uses
     * {@code UUID.nameUUIDFromBytes("OfflinePlayer:<name>")} by construction, so comparing
     * against that identifies it exactly. The case that was missed is a session whose uuid is
     * not a uuid at all: a Loom dev client hands over {@code Session("Player767", "Player767",
     * "", "legacy")}, and the first version of this reported that as Microsoft because a name is
     * not equal to a derived uuid either. An id that cannot be parsed as a uuid is not an
     * account, so it is offline — which is the honest reading and the one a dev client needs.</p>
     */
    private static boolean isOfflineUuid(String name, String uuid) {
        if (name.isEmpty() || uuid.isEmpty()) {
            return true;
        }
        String bare = uuid.replace("-", "");
        if (bare.length() != 32 || !bare.matches("[0-9a-fA-F]+")) {
            return true;
        }
        try {
            String offline = UUID.nameUUIDFromBytes(
                    ("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString();
            return offline.replace("-", "").equalsIgnoreCase(bare);
        } catch (RuntimeException e) {
            return true;
        }
    }

    public MinecraftClient minecraft() {
        return MinecraftClient.getInstance();
    }

    public UiHost ui() {
        return ui;
    }

    public LiveState state() {
        return state;
    }

    public VoidBridge bridge() {
        return bridge;
    }

    /** The FOV multiplier the zoom actuator wants this frame (§6.7). */
    public double zoomFactor() {
        return zoom.factor();
    }

    /** True when our crosshair replaces the vanilla pass this frame. */
    public boolean suppressesVanillaCrosshair() {
        return state.crosshairOn
                && !dev.voidpvp.client.render.CrosshairGeometry.keepsVanilla(state.crosshairStyle);
    }

    // -----------------------------------------------------------------
    // Frame
    // -----------------------------------------------------------------

    /**
     * End of {@code InGameHud.render}: bring the UI up if this is the first
     * frame, run it, then paint the HUD layer over the game.
     *
     * <p>This is where Ultralight is created, because it is the first place the
     * mod runs with Minecraft's GL context current (§6.2).</p>
     */
    public void onRenderOverlay() {
        MinecraftClient mc = minecraft();
        if (mc == null) {
            return;
        }
        // Bridge calls that need the game thread run here, first thing. This is the fast drain of
        // the two: a closeMenu the page made lands within a frame rather than within a tick, so
        // the menu still shuts on the click that asked for it and not up to 50 ms later. It runs
        // before pollHotkeys because the page's request is older than this frame's key sample.
        bridge.runGameThreadWork();
        // Once per game frame, before anything branches on whether the menu is up: the profiler's
        // game-frame line has to be the same measurement in every configuration, including the
        // ones where the UI paints nothing at all.
        ui.countGameFrame();
        // Hotkeys are sampled here rather than on the 20 Hz client tick. Edge detection over a
        // polled key can only see presses that straddle a sample, and at 20 Hz that misses a tap
        // shorter than 50 ms outright — which is why Right Shift sometimes did not close the menu
        // at all. This runs once per frame, so the window is a frame rather than a tick.
        pollHotkeys(mc);

        boolean menuOpen = mc.currentScreen instanceof VoidMenuScreen;
        Window window = new Window(mc);

        if (!menuOpen) {
            // The menu screen paints the same view itself, one layer up.
            pumpUi();
        }
        drawCrosshair(mc, window);
        if (!menuOpen) {
            ui.paint(window.getWidth(), window.getHeight());
        }
    }

    /** Creates the view if needed, delivers this frame's events, paints it. */
    public void pumpUi() {
        MinecraftClient mc = minecraft();
        if (mc == null) {
            return;
        }
        // Render every frame while the menu is up. On-demand rendering was tried as a way to
        // buy frames back and it is the wrong lever: the FPS/CPS readouts dirty the view every
        // frame anyway, so it saved almost nothing, and it made frame pacing uneven and let the
        // compositor show a stale surface — visible as flicker. The frames were not the paint
        // policy's to give: they came from enabling Retina (the raster was being upscaled 2x by
        // macOS) and from dropping the sub-pixel radii off the 486 cell-art divs. Input still
        // wakes the view via UiHost.wake(), which is what the HUD path needs.
        ui.setContinuous(mc.currentScreen instanceof VoidMenuScreen);
        int fbWidth = Math.max(1, mc.width);
        int fbHeight = Math.max(1, mc.height);
        // Scale the design canvas to fit, on whichever axis is tighter, instead of inheriting
        // Minecraft's GUI scale. That setting is 4 on Auto at large resolutions, which left the
        // view only 427 x 240 CSS pixels and rendered the menu three times oversized and clipped.
        // Fitting keeps the UI on-model at any window size; uiScale stays as the user's override.
        double fit = Math.min(fbWidth / DESIGN_WIDTH, fbHeight / DESIGN_HEIGHT);
        double scale = fit * state.uiScale;
        ui.ensure(fbWidth, fbHeight, scale);
        ui.frame();
        advanceZoom(mc);
    }

    private void drawCrosshair(MinecraftClient mc, Window window) {
        ClientPlayerEntity player = mc.player;
        CrosshairRenderer.draw(state, window.getWidth(), window.getHeight(),
                player != null && player.isSprinting());
    }

    private void advanceZoom(MinecraftClient mc) {
        long now = System.nanoTime();
        double frameFactor = lastFrameNanos == 0 ? 1
                : (now - lastFrameNanos) / 16_666_666.0;
        lastFrameNanos = now;
        boolean held = state.zoomOn && mc.currentScreen == null
                && state.zoomKeyCode != KeyNames.KEY_NONE
                && isKeyDown(state.zoomKeyCode);
        zoom.update(held, state.zoomFovDivisor, state.zoomSmooth, frameFactor);
    }

    /** {@code Minecraft.onResolutionChanged}: the view follows the framebuffer. */
    public void onResize() {
        ui.invalidateSize();
    }

    // -----------------------------------------------------------------
    // Client tick: sensors, actuators, hotkeys, telemetry
    // -----------------------------------------------------------------

    private boolean autoWorldDone;
    private int autoWorldWaited;
    private boolean autoMenuDone;
    private int autoMenuWaited;

    public void onClientTick() {
        MinecraftClient mc = minecraft();
        if (mc == null || mc.options == null) {
            return;
        }
        // The second drain point. onRenderOverlay is the fast one but it only runs while
        // InGameHud renders, i.e. with a world loaded; this is the beat that still runs at the
        // title screen, so nothing the page posted can sit in the queue indefinitely.
        bridge.runGameThreadWork();
        // Before anything reads mc.width/height: at the main menu the UI is not pumped, so this
        // is the only beat that runs from the first tick onwards.
        applyRetinaResolution(mc);
        maybePushLocalState();
        maybeAutoLoadWorld(mc);
        maybeAutoOpenMenu(mc);
        maybeAutoWalk(mc);
        // Tracing scaffolding, off unless VOID_UI_INPUTDRIVE is set. Started from the tick rather
        // than at init because it needs Display/Mouse/Keyboard to exist; a no-op after the first
        // successful call. See SyntheticInput for why input has to be driven from inside.
        dev.voidpvp.client.input.SyntheticInput.start();
        maybeCycleMenu(mc);
        refreshKeyBindings(mc);
        applyActuators(mc);
        pushTick(mc);
        pushSession(mc);
    }

    /**
     * Gives the page the loadout this client is actually running on, when nothing else will.
     *
     * <p>Every other {@code loadout} push comes from the launcher link — {@link #onInit} and
     * {@link #onLoadout} — or from a loadout switch. A client started without {@code -Dvoid.port}
     * has none of those, so the page's store held {@code loadout: null} for the whole session
     * while {@link LiveState} sat on a perfectly good {@code Loadout.defaults}. Nothing looked
     * broken: the properties panel falls back to the registry's defaults for display, so it drew
     * the right numbers — but the store's writer bails on a null loadout, so <em>every</em>
     * control in it was inert. Java stored each change and returned it (measured:
     * {@code setSetting keystrokes.corner_radius sent=20 applied=20}); the page then dropped it
     * on the floor and re-rendered the default. It read as "the meter cannot be dragged", and
     * equally as a toggle that will not toggle and a swatch that will not take.</p>
     *
     * <p>Once, and never once Rust's {@code init} has landed. A client that has a launcher waits
     * for that init rather than racing it, or this would briefly show the factory defaults over
     * the player's real loadout. Emitting early is safe because {@link VoidBridge} queues
     * envelopes until the page reports the shim is up — see {@code UiHost.frameOnUiThread}'s
     * {@code bridgeReady} probe — so the first tick's push is still delivered to a page that
     * mounts several seconds later.</p>
     *
     * <p><b>Waits, rather than defers forever.</b> The guard used to be {@code socket != null},
     * which reads as "someone else will push this" — and is wrong whenever the socket comes up but
     * the {@code init} does not. Two ways that happens in a shipped client: a protocol-version
     * mismatch, where {@link #onVersionMismatch} logs and applies nothing while the socket stays
     * up; and a bridge that dies after connecting. In both the page holds {@code loadout: null}
     * for the whole session, and the store's writer opens with {@code if (!loadout) return}, so
     * every control in the properties panel is silently inert — the same failure this method was
     * written to fix, reached by the other road. The condition is therefore what is actually being
     * waited for ({@link LiveState#isInitialised()}, set only by {@code applyInit}) plus a bounded
     * grace, not the mere presence of a socket.</p>
     */
    private void maybePushLocalState() {
        if (localStatePushed || state.isInitialised()) {
            return;
        }
        boolean attached = socket != null;
        if (attached && ++initWaited <= INIT_GRACE_TICKS) {
            return;
        }
        localStatePushed = true;
        // One definition of what a page needs, shared with onInit and with the re-push a reloaded
        // page gets (VoidBridge.pushWholeState) — the library, then the active loadout, then the
        // menu state.
        //
        // Straight onto the bridge rather than through emitLoadout(), which also calls
        // ui.requestRender(). That request would start the UI thread on the first client tick,
        // i.e. at the title screen, earlier than anything else does — and there is nothing for it
        // to render: the view does not exist yet, and the frames after it is created are forced
        // (UiHost.FORCED_RENDERS) so the first paint carries this state anyway.
        bridge.pushWholeState();
        if (attached) {
            // Warn, and say which of the two situations this is. A client with no launcher pushing
            // its own defaults is the ordinary dev path; a client whose launcher answered the
            // socket and then never sent init is a fault somewhere else, and this line is the only
            // place it will ever surface.
            VoidLog.warn("no init from the launcher after " + INIT_GRACE_TICKS
                    + " ticks, though its socket is up: falling back to the mod's own defaults so"
                    + " the page has a loadout to write to. Loadout '" + state.loadoutId() + "'.");
        } else {
            VoidLog.info("loadout '" + state.loadoutId()
                    + "' pushed to the page from the mod's own defaults (no launcher link)");
        }
    }

    /**
     * Test hook, off unless {@code VOID_UI_AUTOWORLD} is set: load the first singleplayer world
     * and open the menu, so the in-game UI can be profiled without a person at the keyboard.
     *
     * <p>This exists because the alternative is synthetic keyboard events at the window-server
     * level, which go wherever the focus happens to be — during this work a stray one landed in
     * the operator's terminal. Driving the client's own API cannot miss.</p>
     */
    private void maybeAutoLoadWorld(MinecraftClient mc) {
        if (System.getenv("VOID_UI_AUTOWORLD") == null || autoWorldDone) {
            return;
        }
        // The title screen has to exist first; before that the client is still coming up.
        if (mc.world != null || mc.currentScreen == null) {
            return;
        }
        if (++autoWorldWaited < 40) {
            return;
        }
        autoWorldDone = true;
        java.io.File saves = new java.io.File(mc.runDirectory, "saves");
        java.io.File[] worlds = saves.listFiles();
        if (worlds == null) {
            VoidLog.warn("autoworld: no saves directory at " + saves);
            return;
        }
        for (java.io.File world : worlds) {
            if (!world.isDirectory() || !new java.io.File(world, "level.dat").isFile()) {
                continue;
            }
            VoidLog.info("autoworld: loading " + world.getName());
            // A null LevelInfo means "open the existing world" — the same call the world list makes.
            mc.startIntegratedServer(world.getName(), world.getName(), null);
            return;
        }
        VoidLog.warn("autoworld: no world with a level.dat under " + saves);
    }

    /** Opens the menu once the world is in, completing the unattended path to the thing under test. */
    private int autoWalkTurn;

    /**
     * Test hook, off unless {@code VOID_UI_AUTOWALK} is set: third person, walking in a slow
     * circle. Sibling of {@code VOID_UI_AUTOWORLD}, and there for the same reason — driving the
     * client's own API cannot miss, where synthetic events at the window server go wherever
     * focus happens to be.
     *
     * <p>It exists because two things could not be verified from a still frame of a standing
     * player. The hitbox renderer's one untestable step is rebasing the entity's box into the
     * render pass's interpolated camera space, and a box that used the un-interpolated position
     * would look perfect on a stationary entity and lag a moving one by up to a tick — so the
     * proof needs an entity that is both close and moving, and in third person the player is
     * both. And {@code keystrokes.pressed_color} only paints a cap that is <em>down</em>, so a
     * screenshot with no key held cannot tell a working colour from an ignored one.</p>
     *
     * <p>{@code KeyBinding.setKeyPressed} rather than a synthetic event: it is the same call the
     * toggle-sprint actuator already makes every tick, so this adds no new mechanism, and it
     * feeds the {@code keys} sensor through {@code KeyBindingMixin} exactly as a real press
     * would.</p>
     */
    private void maybeAutoWalk(MinecraftClient mc) {
        if (System.getenv("VOID_UI_AUTOWALK") == null || mc.player == null
                || mc.currentScreen != null) {
            return;
        }
        // Third person, so the player's own entity — the closest one there can be — is in frame.
        mc.options.perspective = 1;
        KeyBinding.setKeyPressed(mc.options.forwardKey.getCode(), true);
        // A slow turn, so the player keeps moving instead of walking into one tree and stopping.
        mc.player.yaw = (autoWalkTurn++ % 360) * 1.5f;
    }

    private void maybeAutoOpenMenu(MinecraftClient mc) {
        // Only when something is going to drive the menu. Loading the world without opening it is
        // the control condition: the game and an idle HUD, nothing else, which is the only way to
        // tell the UI's share of a slow frame from the game's own.
        // VOID_UI_AUTOMENU is the same hook without the clicking, so "menu open and idle" can be
        // measured as its own condition rather than only "menu open and being driven".
        if (System.getenv("VOID_UI_AUTOWORLD") == null
                || (System.getenv("VOID_UI_SELFTEST") == null
                        && System.getenv("VOID_UI_AUTOMENU") == null)
                || autoMenuDone) {
            return;
        }
        // Not "no screen showing": an unfocused window puts 1.8.9 on its pause screen, and a
        // headless profiling run is never focused. Anything that is not already our menu is fair
        // game to replace.
        if (mc.world == null || mc.player == null || mc.currentScreen instanceof VoidMenuScreen) {
            return;
        }
        if (++autoMenuWaited < 40) {
            return;
        }
        autoMenuDone = true;
        VoidLog.info("autoworld: opening the menu");
        mc.setScreen(new VoidMenuScreen(this));
    }

    /**
     * Test hook, off unless {@code VOID_UI_MENUCYCLE} is set to a number of milliseconds: open and
     * close the menu on a timer.
     *
     * <p>{@link #maybeAutoOpenMenu} opens it exactly once, which is one sample of the open hitch
     * per client launch — and the open hitch is the thing that has to be measured before and
     * after, so one sample is not enough. This gives a run as many as it lasts for. It is
     * profiling scaffolding of the same kind as {@code VOID_UI_AUTOWORLD}: nothing reads the
     * variable in a shipped client.</p>
     */
    private static final long MENU_CYCLE_MS = menuCyclePeriod();
    private long menuCycleAtMs;

    private static long menuCyclePeriod() {
        String raw = System.getenv("VOID_UI_MENUCYCLE");
        if (raw == null) {
            return 0L;
        }
        try {
            return Math.max(0L, Long.parseLong(raw.trim()));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private void maybeCycleMenu(MinecraftClient mc) {
        if (MENU_CYCLE_MS <= 0 || mc.world == null || mc.player == null) {
            return;
        }
        long period = MENU_CYCLE_MS;
        long now = System.currentTimeMillis();
        if (menuCycleAtMs == 0L) {
            menuCycleAtMs = now;
            return;
        }
        if (now - menuCycleAtMs < period) {
            return;
        }
        menuCycleAtMs = now;
        if (mc.currentScreen instanceof VoidMenuScreen) {
            closeMenu();
        } else {
            // Anything that is not our menu is fair game to replace — an unfocused window sits on
            // the pause screen, and a profiling run is never focused.
            mc.setScreen(new VoidMenuScreen(this));
        }
    }

    private void refreshKeyBindings(MinecraftClient mc) {
        keys.setBindings(
                mc.options.forwardKey.getCode(),
                mc.options.leftKey.getCode(),
                mc.options.backKey.getCode(),
                mc.options.rightKey.getCode(),
                mc.options.attackKey.getCode(),
                mc.options.useKey.getCode(),
                mc.options.jumpKey.getCode(),
                mc.options.sneakKey.getCode());
    }

    /**
     * Puts the game on the display's real resolution (§13, {@link HiDpi}).
     *
     * <p>1.8.9 sets {@code width}/{@code height} straight from the launcher's window size at
     * startup and only routes through {@code onResolutionChanged} on an actual resize, so the
     * mixin that converts those arguments to pixels never runs until the player drags the window.
     * Driving one resolution change as soon as a mismatch is seen fixes that, and costs nothing
     * afterwards because the sizes then agree.</p>
     */
    private void applyRetinaResolution(MinecraftClient mc) {
        if (!HiDpi.active()) {
            return;
        }
        int wantWidth = HiDpi.toPixels(org.lwjgl.opengl.Display.getWidth());
        int wantHeight = HiDpi.toPixels(org.lwjgl.opengl.Display.getHeight());
        if (mc.width == wantWidth && mc.height == wantHeight) {
            return;
        }
        // The mixin rescales these, so pass the point size the resize path would have passed.
        ((dev.voidpvp.client.mixin.MinecraftClientInvoker) mc).void$onResolutionChanged(
                org.lwjgl.opengl.Display.getWidth(), org.lwjgl.opengl.Display.getHeight());
    }

    /**
     * Head of {@code MinecraftClient.runGameLoop}: hand the menu this frame's input.
     *
     * <p>1.8.9 drains LWJGL's input queues from {@code MinecraftClient.tick()}, at 20 Hz, and that
     * is a 50 ms quantisation floor under every click, drag and keystroke the menu receives — on a
     * client rendering at 110. The queues are refilled by {@code Display.update()} once per
     * rendered frame, so a per-frame drain is both strictly faster and the fastest this path can
     * be: any more often and it would find them empty. See
     * {@link VoidMenuScreen#pumpInput()} for the measurements and for why the head of the game
     * loop, rather than the screen's own render, is the place to do it.</p>
     *
     * <p>Only our own menu. A vanilla screen keeps vanilla's timing, because vanilla's screens
     * were written against it — {@code lastClicked}, the double-click windows in the world list,
     * and the tick-counted cursor blink all read the tick as their clock.</p>
     */
    public void pumpMenuInput() {
        MinecraftClient mc = minecraft();
        if (mc == null) {
            return;
        }
        if (mc.currentScreen instanceof VoidMenuScreen) {
            ((VoidMenuScreen) mc.currentScreen).pumpInput();
        }
    }

    /** Called by {@code VoidMenuScreen} when the menu key arrives as an event. */
    public void latchMenuKeyTap() {
        menuKeyTapped = true;
    }

    private void pollHotkeys(MinecraftClient mc) {
        boolean canOpen = mc.world != null && mc.player != null;
        boolean menuScreenOpen = mc.currentScreen instanceof VoidMenuScreen;
        boolean otherScreenOpen = mc.currentScreen != null && !menuScreenOpen;

        boolean menuDown = !otherScreenOpen && isKeyDown(state.menuKeyCode);
        boolean tapped = menuKeyTapped;
        menuKeyTapped = false;
        // Re-arm only after a sustained release, counting either half of the pair as held.
        int sibling = siblingModifier(state.menuKeyCode);
        boolean physicallyDown = isKeyDown(state.menuKeyCode)
                || (sibling != 0 && isKeyDown(sibling));
        long nowMs = System.currentTimeMillis();
        if (physicallyDown) {
            menuUpSinceMs = 0L;
        } else {
            if (menuUpSinceMs == 0L) {
                menuUpSinceMs = nowMs;
            } else if (nowMs - menuUpSinceMs >= MENU_REARM_UP_MS) {
                menuArmed = true;
            }
        }

        boolean edge = menuKey.pressed(menuDown) || tapped;
        if (tapped) {
            // AFTER pressed(), not before — and that ordering was the bug. assumeDown() sets
            // wasDown = true so the polled state cannot later read as a fresh press, but
            // pressed() overwrites wasDown with this frame's menuDown. The event routinely
            // arrives a frame before LWJGL's modifier state catches up, so wasDown was being
            // reset to false and the state turning true next frame looked like a new press —
            // reopening the menu the same tap had just closed. Primed here, it holds.
            menuKey.assumeDown();
        }
        long now = System.currentTimeMillis();
        if (edge && (!menuArmed || now - lastMenuToggleMs < MENU_TOGGLE_DEBOUNCE_MS)) {
            edge = false;
        }
        if (edge && canOpen) {
            lastMenuToggleMs = now;
            menuArmed = false;
            menuUpSinceMs = 0L;
            if (menuScreenOpen) {
                closeMenu();
            } else {
                mc.setScreen(new VoidMenuScreen(this));
            }
            reportHotkey(dev.voidpvp.client.net.Protocol.HOTKEY_OVERLAY);
        }

        boolean cycleDown = !otherScreenOpen && !menuScreenOpen
                && isKeyDown(state.cycleLoadoutKeyCode);
        if (cycleKey.pressed(cycleDown) && canOpen) {
            cycleLoadout();
        }

        // keystrokes.keybind: the one per-mod hotkey in the registry. It hides
        // and shows the overlay without opening the menu. One setting changed,
        // so it pushes the `setting` event rather than a whole loadout — the UI
        // applies it exactly as it applies the return value of setModSetting
        // (bridge.json, `setting_payload`).
        int keystrokesCode = state.keystrokesToggleCode;
        boolean keystrokesDown = keystrokesCode != KeyNames.KEY_NONE && !otherScreenOpen
                && !menuScreenOpen && isKeyDown(keystrokesCode);
        if (keystrokesKey.pressed(keystrokesDown) && canOpen) {
            // The mirror, not loadout().isOn(): this is sampled every frame, and the loadout's
            // maps are written by the UI thread under a monitor this path must not take.
            boolean on = state.keystrokesOn;
            com.google.gson.JsonElement stored = state.setModSetting("keystrokes", "on",
                    new JsonPrimitive(Boolean.valueOf(!on)));
            if (stored != null) {
                bridge.emitSetting("keystrokes", "on", stored);
                // This hotkey can take the last thing off the overlay — it hides a HUD widget
                // with no menu open — and a page with nothing left to draw submits no draw
                // commands, so the accelerated renderer would go on showing the widget that was
                // just hidden. Same reason as onMenuClosed; see UiHost.requestRender.
                ui.requestRender();
            }
        }
    }

    /** L: next loadout in library order, applied locally and told to Rust (§8.2). */
    public void cycleLoadout() {
        String next = state.nextLoadoutId();
        if (next != null && !next.equals(state.loadoutId())) {
            if (state.switchLoadout(next)) {
                emitLoadout();
                // The launcher's own pointer has to follow, or the tray and the next
                // launch disagree with the running game (protocol.json, `msg_hotkey`).
                reportHotkey(dev.voidpvp.client.net.Protocol.HOTKEY_LOADOUT_NEXT);
            }
        }
    }

    /** Tells the launcher a global hotkey fired. Best-effort: never queued (§6.9). */
    private void reportHotkey(String id) {
        if (socket != null) {
            socket.sendHotkey(id);
        }
    }

    /**
     * The player's own brightness, while Fullbright is overriding it; {@code null} when it is not.
     *
     * <p>Read by {@code GameOptionsMixin} so the override never reaches {@code options.txt}. It is
     * the same {@code savedGamma} the actuator restores when the mod is switched off — there is
     * one right answer to "what was the player's brightness", and two copies of it would drift on
     * exactly the path that makes this bug invisible.</p>
     */
    public Float playerGamma() {
        return savedGamma;
    }

    private void applyActuators(MinecraftClient mc) {
        // Fullbright: gammaSetting override, restored exactly when turned off.
        if (state.fullbrightOn) {
            if (savedGamma == null) {
                savedGamma = Float.valueOf(mc.options.gamma);
            }
            mc.options.gamma = state.fullbrightGamma;
        } else if (savedGamma != null) {
            mc.options.gamma = savedGamma.floatValue();
            savedGamma = null;
        }

        // Hitboxes: the same flag F3+B sets.
        //
        // Written on the edge, not every tick. Writing it unconditionally 20 times a second
        // meant the mod owned the flag outright: F3+B would toggle it and the next tick would
        // put it straight back, so vanilla's own debug key silently stopped working whenever
        // this mod was installed — including with the mod switched off, which is the case it
        // has no business touching at all. Now the mod only writes when *its own* setting
        // moved, and F3+B keeps the flag until it does.
        if (mc.getEntityRenderManager() != null && state.hitboxesOn != hitboxesApplied) {
            mc.getEntityRenderManager().setRenderHitboxes(state.hitboxesOn);
            hitboxesApplied = state.hitboxesOn;
        }

        // Zoom: cinematic camera while the zoom is engaged, saved and restored like gamma.
        //
        // `smoothCamera` is the vanilla "Cinematic camera" option, which is what this setting
        // has always named; the FOV divisor narrows the view and this damps the mouse, which is
        // the pair that makes a zoom usable at 4x. Keyed off `zoom.isActive()` rather than the
        // key being down so it stays on through the ease-out and leaves with it.
        boolean cinematic = state.zoomOn && state.zoomCinematic && zoom.isActive();
        if (cinematic) {
            if (savedSmoothCamera == null) {
                savedSmoothCamera = Boolean.valueOf(mc.options.smoothCameraEnabled);
            }
            mc.options.smoothCameraEnabled = true;
        } else if (savedSmoothCamera != null) {
            mc.options.smoothCameraEnabled = savedSmoothCamera.booleanValue();
            savedSmoothCamera = null;
        }

        // Toggle sprint: latch the sprint KeyBinding rather than the input.
        boolean canMove = mc.player != null && mc.currentScreen == null;
        int sprintCode = mc.options.sprintKey.getCode();
        boolean sprintHeld = sprint.update(state.toggleSprintOn, state.toggleSprintHold,
                isKeyDown(sprintCode), canMove);
        applyLatch(sprintCode, sprintHeld, sprintForced);
        sprintForced.value = latchWrote(state, sprintHeld);

        if (state.toggleSprintSneakToo) {
            int sneakCode = mc.options.sneakKey.getCode();
            boolean sneakHeld = sneak.update(state.toggleSprintOn, state.toggleSprintHold,
                    isKeyDown(sneakCode), canMove);
            applyLatch(sneakCode, sneakHeld, sneakForced);
            sneakForced.value = latchWrote(state, sneakHeld);
        } else if (sneakForced.value) {
            // The setting was turned off while the sneak latch was holding the key down.
            // Same release as below, by the same argument.
            KeyBinding.setKeyPressed(mc.options.sneakKey.getCode(), false);
            sneak.release();
            sneakForced.value = false;
        }
    }

    /** Whether the latch wants the key reported as held this tick. */
    private static boolean latchWrote(LiveState state, boolean held) {
        return state.toggleSprintOn && !state.toggleSprintHold && held;
    }

    /**
     * Applies one latch to one {@code KeyBinding}, and — the half that was missing —
     * <b>releases it when the latch lets go</b>.
     *
     * <p>{@code KeyBinding.pressed} is edge-written: Minecraft sets it on a key event and
     * nothing else touches it, which is exactly what makes the latch work — force it true and
     * it stays true with the key physically up. The same property is why dropping the latch is
     * not enough to stop sprinting. Ceasing to write {@code true} leaves the last {@code true}
     * standing, so turning the mod off, switching to {@code hold} mode, or switching to a
     * loadout where it is off all left the player sprinting until they next tapped the key
     * themselves — a mod that goes on acting after it is switched off.</p>
     *
     * <p>The release is conditional on <em>this</em> having been the writer ({@code forced}),
     * so a key the player is genuinely holding is never yanked out from under them.</p>
     */
    private void applyLatch(int code, boolean held, Flag forced) {
        if (latchWrote(state, held)) {
            KeyBinding.setKeyPressed(code, true);
        } else if (forced.value && !isKeyDown(code)) {
            KeyBinding.setKeyPressed(code, false);
        }
    }

    /** A mutable boolean, so {@link #applyLatch} can be shared by the two latches. */
    private static final class Flag {
        boolean value;
    }

    private void pushTick(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        if (player == null) {
            return;
        }
        int fps = dev.voidpvp.client.mixin.MinecraftClientAccessor.void$currentFps();
        int ping = latency(mc, player);
        List<ArmorSlot> armor = readArmor(player);
        List<PotionFx> fx = readEffects(player);
        JsonObject payload = ticks.build(fps, ping, player.x, player.y, player.z,
                player.yaw, armor, fx);
        // Now that every field is coalesced, a tick where nothing moved carries nothing. Emitting
        // it anyway would cross the bridge, parse, and run a reducer 20 times a second to conclude
        // there is no news.
        if (payload.entrySet().size() > 0) {
            bridge.emit(VoidBridge.EVENT_TICK, payload);
        }
        stats.sample(fps);
    }

    private static int latency(MinecraftClient mc, ClientPlayerEntity player) {
        try {
            if (mc.getNetworkHandler() == null) {
                return -1;
            }
            PlayerListEntry self = mc.getNetworkHandler()
                    .getPlayerListEntry(player.getGameProfile().getId());
            return self == null ? -1 : Math.max(-1, Math.min(60000, self.getLatency()));
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private List<ArmorSlot> readArmor(ClientPlayerEntity player) {
        List<ArmorSlot> out = new ArrayList<ArmorSlot>(5);
        ItemStack[] worn = player.inventory.armor;
        // 1.8.9 stores armor feet-first; the bridge reports it head-first.
        out.add(slot("helmet", worn.length > 3 ? worn[3] : null));
        out.add(slot("chestplate", worn.length > 2 ? worn[2] : null));
        out.add(slot("leggings", worn.length > 1 ? worn[1] : null));
        out.add(slot("boots", worn.length > 0 ? worn[0] : null));
        if (state.armorShowHeldItem) {
            out.add(slot("held", player.inventory.getMainHandStack()));
        }
        return out;
    }

    private static ArmorSlot slot(String name, ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return ArmorSlot.empty(name);
        }
        return new ArmorSlot(name, itemId(stack), stack.getDamage(), stack.getMaxDamage(),
                stack.count, stack.hasEnchantments());
    }

    /** {@code minecraft:diamond_sword} to {@code diamond_sword}. */
    private static String itemId(ItemStack stack) {
        Object id = net.minecraft.item.Item.REGISTRY.getIdentifier(stack.getItem());
        if (id == null) {
            return null;
        }
        String text = String.valueOf(id);
        int colon = text.indexOf(':');
        return colon >= 0 ? text.substring(colon + 1) : text;
    }

    private static List<PotionFx> readEffects(ClientPlayerEntity player) {
        List<PotionFx> out = new ArrayList<PotionFx>();
        Collection<?> effects = player.getStatusEffectInstances();
        if (effects == null) {
            return out;
        }
        for (Object raw : effects) {
            if (!(raw instanceof StatusEffectInstance)) {
                continue;
            }
            StatusEffectInstance effect = (StatusEffectInstance) raw;
            out.add(new PotionFx(
                    effect.getEffectId(),
                    effect.getTranslationKey(),
                    effect.getAmplifier(),
                    PotionFx.ticksToMs(effect.getDuration()),
                    effect.isAmbient()));
        }
        return out;
    }

    private void pushSession(MinecraftClient mc) {
        if (socket == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (stats.shouldReport(now)) {
            socket.sendSession(stats.fpsAverage(), stats.playedMs(now),
                    server.connected() ? server.host() : null, state.loadoutId());
        }
    }

    // -----------------------------------------------------------------
    // Sensor callbacks from mixin/
    // -----------------------------------------------------------------

    /** {@code KeyBinding.setKeyPressed}: edge-triggered {@code keys} event. */
    public void onKeyState(int keyCode, boolean pressed) {
        if (!keys.hasBindings()) {
            return;
        }
        if (keys.update(keyCode, pressed)) {
            bridge.emit(VoidBridge.EVENT_KEYS, keys.payload());
        }
    }

    /** {@code KeyBinding.releaseAllKeys}: everything comes up at once. */
    public void onKeysReleased() {
        if (keys.releaseAll()) {
            bridge.emit(VoidBridge.EVENT_KEYS, keys.payload());
        }
    }

    /** {@code MinecraftClient.connect}: a world arrived, or was torn down. */
    public void onWorldChanged(boolean hasWorld, String address) {
        boolean connected = hasWorld && address != null && !address.isEmpty();
        String host = ServerWatcher.stripPort(address);
        int port = ServerWatcher.portOf(address);
        if (!server.update(connected, host, port)) {
            return;
        }
        ticks.reset();
        bridge.emit(VoidBridge.EVENT_SERVER, server.payload());
        if (socket != null) {
            socket.sendServer(server.host(), server.connected(), server.port());
        }
    }

    // -----------------------------------------------------------------
    // BridgeHost
    // -----------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Game thread only. {@code setScreen(null)} runs {@code VoidMenuScreen.removed()}, which
     * deletes the backdrop and shadow textures, so this cannot run from the UI thread — {@link
     * VoidBridge} queues the bridge's calls and the hotkey poll calls it directly.</p>
     */
    @Override
    public void closeMenu() {
        MinecraftClient mc = minecraft();
        if (mc != null && mc.currentScreen instanceof VoidMenuScreen) {
            mc.setScreen(null);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Runs on the UI thread, inline. Two volatile writes and nothing else — deliberately no
     * Minecraft object is touched here, which is the only reason the bridge is allowed to skip the
     * queue for this call.</p>
     */
    @Override
    public void beginKeybindCapture(String modId) {
        captureModId = modId;
        captureActive = true;
    }

    /**
     * The page's report of where the shadowed surfaces are. Held here rather than pushed straight
     * at the renderer because it arrives on whatever frame the layout settled, which is not the
     * frame that draws — {@link dev.voidpvp.client.screen.VoidMenuScreen} reads it when it paints.
     */
    private volatile java.util.List<dev.voidpvp.client.render.EffectSurface> surfaces =
            java.util.Collections.emptyList();

    /** Volatile only so a lost update costs nothing worse than the line being logged twice. */
    private volatile boolean loggedSurfaces;

    /**
     * {@inheritDoc}
     *
     * <p>Runs on the UI thread, inline. Copies the list and publishes it through the volatile
     * field above; {@link dev.voidpvp.client.render.EffectSurface} is immutable, so the game
     * thread's {@link #surfaces()} either sees the whole previous set or the whole new one and
     * never a half-written list.</p>
     */
    @Override
    public void setSurfaces(java.util.List<dev.voidpvp.client.render.EffectSurface> next) {
        if (!loggedSurfaces && next != null && !next.isEmpty()) {
            loggedSurfaces = true;
            VoidLog.info("GL shadows: " + next.size() + " surface(s), first " + next.get(0));
        }
        surfaces = next == null
                ? java.util.Collections.<dev.voidpvp.client.render.EffectSurface>emptyList()
                : java.util.Collections.unmodifiableList(
                        new java.util.ArrayList<dev.voidpvp.client.render.EffectSurface>(next));
    }

    /** The surfaces to draw shadows behind, newest report wins. Never null. */
    public java.util.List<dev.voidpvp.client.render.EffectSurface> surfaces() {
        return surfaces;
    }

    public boolean captureActive() {
        return captureActive;
    }

    public String captureModId() {
        return captureModId;
    }

    /**
     * Ends a capture; {@code null} means the player pressed Escape.
     *
     * <p>Called on the game thread, from the menu's key and mouse handlers. The result is
     * <em>queued</em> onto the push channel rather than evaluated here: {@code evaluateScript}
     * enters JavaScriptCore, which belongs to the UI thread now, so the game thread must not call
     * it. The envelope rides the next frame's batch like any event — one frame of latency on a key
     * the player has already pressed.</p>
     */
    public void finishKeybindCapture(String keyName) {
        if (!captureActive) {
            return;
        }
        captureActive = false;
        captureModId = null;
        String name = keyName == null || !KeyNames.isValidKeybind(keyName) ? null : keyName;
        bridge.emitCallResult("openKeybindCapture",
                name == null ? null : new JsonPrimitive(name));
    }

    // -- menu events -----------------------------------------------------

    public void onMenuOpened() {
        // Before the emit: the clock this starts is the one the player experiences, which begins
        // when the game decides to open the menu and ends when the pixels change.
        ui.noteMenuOpening();
        // emitMenu, not emit(EVENT_MENU, ...): the bridge has to remember this, because it is the
        // one piece of the page's state that cannot be re-read from anywhere when a reloaded page
        // has to be told the session over again. See VoidBridge.pushWholeState.
        bridge.emitMenu(true);
        ui.requestRender();
    }

    public void onMenuClosed() {
        bridge.emitMenu(false);
        // The page is about to take the menu off screen; make sure the view actually repaints, or
        // the menu stays on screen as a stale frame.
        //
        // One call, not two. This used to arm a clear for right now *and* a second one 180 ms out,
        // on the reasoning that the frame whose content becomes nothing is the far side of the
        // fade rather than this one. The reasoning was right and the remedy was wrong: an armed
        // clear is spent on the next frame that renders whether or not that frame repaints
        // anything, so the immediate one blanked the whole overlay a frame after the keypress and
        // the fade then painted the menu back for two frames — the close flicker. The window
        // {@link UiHost#requestRender} now opens covers the fade and the unmount together and is
        // only ever spent on a frame the page itself is repainting, so one call says all of it.
        ui.requestRender();
    }

    private void emitLoadout() {
        // loadoutJson(), not loadout().toJson(): serialising walks every mod's settings map, and
        // outside the monitor the UI thread may be writing one of them mid-walk.
        bridge.emit(VoidBridge.EVENT_LOADOUT, state.loadoutJson());
        // The other way the page's content can empty. Turning the last HUD widget off leaves the
        // overlay with nothing to draw, and a page with nothing to draw submits no draw commands,
        // so the accelerated renderer would keep showing the widget that was just switched off.
        // Every loadout change comes through here, whoever made it — the page, a hotkey, a
        // loadout cycle — which is why the request goes here rather than at each of those.
        ui.requestRender();
    }

    /** The whole library, {@code bridge.json}'s {@code loadouts} event. */
    private void emitLoadouts() {
        bridge.emit(VoidBridge.EVENT_LOADOUTS, state.libraryJson());
    }

    // -----------------------------------------------------------------
    // VoidSocket.Listener
    // -----------------------------------------------------------------

    @Override
    public void onInit(Loadout loadout, List<Loadout> loadouts, GlobalSettings settings) {
        state.applyInit(loadout, loadouts, settings);
        // The same whole-state push a freshly loaded page gets: the library, then the active
        // loadout, then the menu. Sharing it with the start-up path and the reload path is what
        // keeps the three from drifting about what the page needs.
        bridge.pushWholeState();
        // What pushWholeState deliberately does not do — it is called from the UI thread too, and
        // the render request belongs to whoever changed the content.
        ui.requestRender();
        VoidLog.info("loadout '" + state.loadoutId() + "' applied from launcher ("
                + state.library().size() + " in library)");
    }

    @Override
    public void onLoadout(Loadout loadout) {
        if (loadout == null) {
            return;
        }
        // A switch made outside the game (launcher or tray, §8.2): apply it, and keep
        // the library copy current so the Loadouts frame does not show a stale card.
        state.applyRemoteLoadout(loadout);
        emitLoadouts();
        emitLoadout();
    }

    @Override
    public void onSettings(GlobalSettings settings) {
        state.applySettings(settings);
        // The page has to be told, or the launcher and the game disagree about the theme and the
        // UI scale until something else reloads the document. Read back out of LiveState rather
        // than serialised from the argument, so what the page is told is what was actually
        // applied — applySettings clamps, and this is the same object pushWholeState would send.
        //
        // onInit does not need this: applyInit calls applySettings and then pushWholeState, which
        // carries this channel. A second emit there would be a second definition of what a page
        // needs, which is the thing §9a exists to stop.
        bridge.emit(VoidBridge.EVENT_SETTINGS, state.settings().toJson());
        // The WS thread changed what the page draws, so it owes the render request — the same
        // split onInit makes, and the reason pushWholeState does not make one itself.
        ui.requestRender();
    }

    @Override
    public void onLinkChanged(boolean up) {
        if (up && server.connected() && socket != null) {
            socket.sendServer(server.host(), true, server.port());
        }
    }

    @Override
    public void onVersionMismatch(int launcherVersion) {
        VoidLog.error("protocol mismatch: launcher v" + launcherVersion + ", mod v"
                + dev.voidpvp.client.net.Protocol.VERSION
                + ". The launcher and the mod ship together; update both.");
    }

    // -----------------------------------------------------------------

    private void shutdown() {
        if (socket != null) {
            long now = System.currentTimeMillis();
            socket.sendSession(stats.fpsAverage(), stats.playedMs(now),
                    server.connected() ? server.host() : null, state.loadoutId());
            socket.stop();
        }
    }

    /** Keyboard and mouse behind one code space, as {@link KeyNames} defines it. */
    private static boolean isKeyDown(int code) {
        if (code == KeyNames.KEY_NONE) {
            return false;
        }
        if (KeyNames.isMouse(code)) {
            int button = code - KeyNames.MOUSE_BASE;
            return org.lwjgl.input.Mouse.isButtonDown(button);
        }
        try {
            return Keyboard.isKeyDown(code);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
