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

    private VoidSocket socket;
    private SessionStats stats;
    private Float savedGamma;
    private String captureModId;
    private boolean captureActive;
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
        double scale = new Window(mc).getScaleFactor() * state.uiScale;
        ui.ensure(Math.max(1, mc.width), Math.max(1, mc.height), scale);
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

    public void onClientTick() {
        MinecraftClient mc = minecraft();
        if (mc == null || mc.options == null) {
            return;
        }
        refreshKeyBindings(mc);
        pollHotkeys(mc);
        applyActuators(mc);
        pushTick(mc);
        pushSession(mc);
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
        }

        boolean cycleDown = !otherScreenOpen && !menuScreenOpen
                && isKeyDown(state.cycleLoadoutKeyCode);
        if (cycleKey.pressed(cycleDown) && canOpen) {
            cycleLoadout();
        }
    }

    /** L: next loadout in library order, applied locally and told to Rust (§8.2). */
    public void cycleLoadout() {
        String next = state.nextLoadoutId();
        if (next != null && !next.equals(state.loadout().id())) {
            if (state.switchLoadout(next)) {
                emitLoadout();
            }
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
        bridge.emit(VoidBridge.EVENT_TICK, payload);
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
        if (state.loadout().boolSetting("armor_status", "show_held_item", true)) {
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
                    server.connected() ? server.host() : null, state.loadout().id());
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

    @Override
    public void closeMenu() {
        MinecraftClient mc = minecraft();
        if (mc != null && mc.currentScreen instanceof VoidMenuScreen) {
            mc.setScreen(null);
        }
    }

    @Override
    public void beginKeybindCapture(String modId) {
        captureModId = modId;
        captureActive = true;
    }

    public boolean captureActive() {
        return captureActive;
    }

    public String captureModId() {
        return captureModId;
    }

    /** Ends a capture; {@code null} means the player pressed Escape. */
    public void finishKeybindCapture(String keyName) {
        if (!captureActive) {
            return;
        }
        captureActive = false;
        captureModId = null;
        String name = keyName == null || !KeyNames.isValidKeybind(keyName) ? null : keyName;
        ui.evaluate(VoidBridge.keybindScript(name));
    }

    // -- menu events -----------------------------------------------------

    public void onMenuOpened() {
        bridge.emit(VoidBridge.EVENT_MENU, new JsonPrimitive(Boolean.TRUE));
    }

    public void onMenuClosed() {
        bridge.emit(VoidBridge.EVENT_MENU, new JsonPrimitive(Boolean.FALSE));
    }

    private void emitLoadout() {
        bridge.emit(VoidBridge.EVENT_LOADOUT, state.loadout().toJson());
    }

    // -----------------------------------------------------------------
    // VoidSocket.Listener
    // -----------------------------------------------------------------

    @Override
    public void onInit(Loadout loadout, List<JsonObject> summaries, GlobalSettings settings) {
        state.applyInit(loadout, summaries, settings);
        emitLoadout();
        VoidLog.info("loadout '" + state.loadout().id() + "' applied from launcher");
    }

    @Override
    public void onLoadout(Loadout loadout) {
        if (loadout == null) {
            return;
        }
        if (!state.cacheLoadout(loadout)) {
            state.applyRemoteLoadout(loadout);
        }
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
                    server.connected() ? server.host() : null, state.loadout().id());
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
