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
import net.minecraft.client.util.Window;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
    private final EdgeKey cycleKey = new EdgeKey();
    private final EdgeKey keystrokesKey = new EdgeKey();

    private VoidSocket socket;
    private SessionStats stats;
    private Float savedGamma;
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
        maybeAutoLoadWorld(mc);
        maybeAutoOpenMenu(mc);
        refreshKeyBindings(mc);
        applyActuators(mc);
        pushTick(mc);
        pushSession(mc);
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
    private void maybeAutoOpenMenu(MinecraftClient mc) {
        // Only when something is going to drive the menu. Loading the world without opening it is
        // the control condition: the game and an idle HUD, nothing else, which is the only way to
        // tell the UI's share of a slow frame from the game's own.
        if (System.getenv("VOID_UI_AUTOWORLD") == null
                || System.getenv("VOID_UI_SELFTEST") == null
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

    private void pollHotkeys(MinecraftClient mc) {
        boolean canOpen = mc.world != null && mc.player != null;
        boolean menuScreenOpen = mc.currentScreen instanceof VoidMenuScreen;
        boolean otherScreenOpen = mc.currentScreen != null && !menuScreenOpen;

        boolean menuDown = !otherScreenOpen && isKeyDown(state.menuKeyCode);
        if (menuKey.pressed(menuDown) && canOpen) {
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
        if (mc.getEntityRenderManager() != null) {
            mc.getEntityRenderManager().setRenderHitboxes(state.hitboxesOn);
        }

        // Toggle sprint: latch the sprint KeyBinding rather than the input.
        boolean canMove = mc.player != null && mc.currentScreen == null;
        int sprintCode = mc.options.sprintKey.getCode();
        boolean sprintHeld = sprint.update(state.toggleSprintOn, state.toggleSprintHold,
                isKeyDown(sprintCode), canMove);
        if (state.toggleSprintOn && !state.toggleSprintHold && sprintHeld) {
            KeyBinding.setKeyPressed(sprintCode, true);
        }
        if (state.toggleSprintSneakToo) {
            int sneakCode = mc.options.sneakKey.getCode();
            boolean sneakHeld = sneak.update(state.toggleSprintOn, state.toggleSprintHold,
                    isKeyDown(sneakCode), canMove);
            if (state.toggleSprintOn && !state.toggleSprintHold && sneakHeld) {
                KeyBinding.setKeyPressed(sneakCode, true);
            }
        }
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
        bridge.emit(VoidBridge.EVENT_MENU, new JsonPrimitive(Boolean.TRUE));
    }

    public void onMenuClosed() {
        bridge.emit(VoidBridge.EVENT_MENU, new JsonPrimitive(Boolean.FALSE));
    }

    private void emitLoadout() {
        // loadoutJson(), not loadout().toJson(): serialising walks every mod's settings map, and
        // outside the monitor the UI thread may be writing one of them mid-walk.
        bridge.emit(VoidBridge.EVENT_LOADOUT, state.loadoutJson());
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
        // The library first, then the active loadout: `loadouts` is whole-state and the
        // `loadout` push is the live copy, so the UI store settles on the right one.
        emitLoadouts();
        emitLoadout();
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
