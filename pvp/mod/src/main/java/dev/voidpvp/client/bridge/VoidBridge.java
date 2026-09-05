package dev.voidpvp.client.bridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.voidpvp.client.state.HudItem;
import dev.voidpvp.client.state.Json;
import dev.voidpvp.client.state.LiveState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@code window.void}, the in-process seam between the mod and the in-game
 * React bundle — {@code schema/bridge.json}, PVP_ARCHITECTURE.md §6.5.
 *
 * <p>JS to Java is a call: the shim hands us a {@code {c, params}} envelope
 * through {@code window.__void_native(json)} and we answer with
 * {@code {c, returns}}. Ultralight runs inside the JVM, so the answer is the
 * state actually applied — there is no ack, no request id and no optimistic
 * UI. {@code openKeybindCapture} is the one exception and resolves later.</p>
 *
 * <p>Java to JS is a push. Events raised during a frame are queued here and
 * drained into a single {@code window.void.__emit([...])} evaluation, so a
 * frame costs one JS call however many sensors fired.</p>
 *
 * <h2>Threads</h2>
 *
 * <p>Ultralight no longer runs on Minecraft's render thread: it has its own UI
 * thread, and {@code setMessageHandler} fires there. <b>Every method below
 * whose name appears in {@code bridge.json}'s {@code call_name} enum therefore
 * runs on the UI thread</b>, while the sensors that call {@link #emit} run on
 * the game thread and the launcher link runs on {@code void-ws}. Three writers,
 * one bridge.</p>
 *
 * <p>The rule that makes that safe is asymmetric on purpose: <b>the UI thread
 * may wait on nothing, and the game thread may wait on nothing.</b> A bridge
 * call either answers out of state the UI thread can read for itself, or it
 * queues the work through {@link #post} and answers without it. There is no
 * hand-off in either direction that one thread blocks on, because the game
 * thread is a 16.6 ms budget (§10 spends 0.5 ms of it on the HUD) and the UI
 * thread is inside a JavaScript call the page is waiting on.</p>
 *
 * <h3>The classification — this is the contract the UI thread rests on</h3>
 *
 * <p>Everything reachable from {@link #dispatch}, and where it is allowed to
 * run. <b>PAGE</b> = page-owned, safe on any thread. <b>SHARED</b> = the game
 * loop reads it too, so it needs synchronisation but not a thread.
 * <b>GAME</b> = touches {@code MinecraftClient}, a screen, the world, the
 * player or GL, and must be queued.</p>
 *
 * <pre>
 * call                reaches                                            class   how
 * ------------------- -------------------------------------------------- ------- --------------------
 * setGameplay         LiveState.setGameplay                              SHARED  inline, synchronized
 *                       ModRegistry.isGameplay                           PAGE    immutable statics
 *                       Loadout.putSetting / ModRegistry.clamp           SHARED  under the LiveState monitor
 *                       LiveState.applyActuatorFields                    SHARED  volatile writes
 *                       Sink.state -> VoidSocket -> Netty/OutboundQueue   PAGE    both thread-safe
 * setHud              LiveState.setHud                                   SHARED  inline, synchronized
 *                       Loadout.putHud, HudItem.normalised               SHARED  under the LiveState monitor
 *                       Sink.hud -> VoidSocket                           PAGE    both thread-safe
 * setModSetting       LiveState.setModSetting                            SHARED  inline, synchronized
 *                       (as setGameplay)
 * switchLoadout       LiveState.switchLoadout                            SHARED  inline, synchronized
 *                       library lookup, applyLoadoutInternal, LoadoutDiff SHARED  under the LiveState monitor
 * setSurfaces         readSurface (parse) -&gt; EffectSurface               PAGE    inline; immutable values
 *                       BridgeHost.setSurfaces -> VoidClient.surfaces    SHARED  inline; volatile publish
 * closeMenu           BridgeHost.closeMenu -> mc.setScreen(null)         GAME    QUEUED via post()
 *                       -> VoidMenuScreen.removed -> GL deletes          GAME      (screen teardown frees textures)
 * openKeybindCapture  BridgeHost.beginKeybindCapture                     SHARED  inline; two volatile flags
 * (any)               VoidBridge.errors                                  SHARED  CopyOnWriteArrayList
 * (any)               VoidBridge.pending (the push queue)                SHARED  synchronized(pending)
 * </pre>
 *
 * <h3>Why nothing blocks for a return value</h3>
 *
 * <p>Three calls answer with a value the page uses: {@code setGameplay} a
 * boolean, {@code setModSetting} the stored element, {@code setHud} the clamped
 * item. None of them is a round trip, because <em>none of them needs the game
 * thread to compute its answer</em>: they are writes to {@link LiveState},
 * which is guarded by its own monitor and reachable from any thread, and the
 * answer is what the clamp produced. Making them optimistic would be strictly
 * worse — the page would then have to be told the real value on the
 * {@code setting} channel, which §6.5 explicitly does not push for a change the
 * page itself made, because that fights the control the player is holding.</p>
 *
 * <p>{@code closeMenu} is the one call that needs the game thread, and it
 * returns nothing, so queueing costs the page nothing at all. {@code
 * setSurfaces} answers with how many rectangles were accepted, which is a
 * property of the parse and not of the game. {@code openKeybindCapture} was
 * already asynchronous: its synchronous answer means <em>armed</em>, and the
 * key arrives later on the push channel.</p>
 */
public final class VoidBridge {

    /** The seven channels of {@code bridge.json#/definitions/event_name}. */
    public static final String EVENT_KEYS = "keys";
    public static final String EVENT_TICK = "tick";
    public static final String EVENT_SERVER = "server";
    public static final String EVENT_LOADOUT = "loadout";
    /** The whole loadout library, from {@code init.loadouts}. */
    public static final String EVENT_LOADOUTS = "loadouts";
    /** One mod setting changed outside a UI call — an in-game hotkey. */
    public static final String EVENT_SETTING = "setting";
    public static final String EVENT_MENU = "menu";

    /**
     * Most game-thread work to run in one drain.
     *
     * <p>The queue only ever holds {@code closeMenu}, so in practice a drain runs nothing or one
     * thing. The cap exists because a drain runs inside a frame: without it a page that called
     * {@code closeMenu} in a loop — or work that posted more work — could spend the whole 16.6 ms
     * budget here. Whatever is left over simply waits for the next drain, which is at most one
     * frame away.</p>
     */
    private static final int MAX_WORK_PER_DRAIN = 64;

    /**
     * How many dispatch failures to keep.
     *
     * <p>The list is copy-on-write, so every add copies it; unbounded, a page failing in a loop
     * would turn the log into quadratic work on the UI thread. Sixty-four is more than enough to
     * see what broke, and the first ones are the interesting ones anyway.</p>
     */
    private static final int MAX_ERRORS = 64;

    private final LiveState state;
    private final BridgeHost host;
    private final Deque<JsonObject> pending = new ArrayDeque<JsonObject>();
    /** Written from the UI thread, read from the game thread and from tests. */
    private final List<String> errors = new CopyOnWriteArrayList<String>();
    /**
     * Bridge work classified GAME above: posted on the UI thread, run on the game thread.
     *
     * <p>Concurrent rather than {@code synchronized} because the producer is inside a JavaScript
     * call and the consumer is inside a frame, and neither may be made to wait for the other even
     * for the length of an {@code add}.</p>
     */
    private final Queue<Runnable> gameThreadWork = new ConcurrentLinkedQueue<Runnable>();

    public VoidBridge(LiveState state, BridgeHost host) {
        this.state = state;
        this.host = host;
    }

    // -----------------------------------------------------------------
    // JS -> Java
    // -----------------------------------------------------------------

    /**
     * Handles one {@code {c, params}} envelope and returns the
     * {@code {c, returns}} answer as JSON text.
     *
     * <p>Never throws: the caller is JavaScript inside a paint, and an
     * exception there would take the frame with it. A malformed or unknown
     * call comes back with {@code returns: null}.</p>
     */
    public String dispatch(String requestJson) {
        String call = "";
        try {
            JsonObject req = Json.parseObject(requestJson);
            if (req == null || !req.has("c")) {
                return result("", JsonNull.INSTANCE);
            }
            call = req.get("c").getAsString();
            JsonArray params = req.has("params") && req.get("params").isJsonArray()
                    ? req.getAsJsonArray("params") : new JsonArray();
            return result(call, invoke(call, params));
        } catch (RuntimeException e) {
            recordError(call + ": " + e);
            return result(call, JsonNull.INSTANCE);
        }
    }

    private void recordError(String message) {
        if (errors.size() < MAX_ERRORS) {
            errors.add(message);
        }
    }

    private JsonElement invoke(String call, JsonArray p) {
        if ("setGameplay".equals(call)) {
            if (p.size() < 2) {
                return new JsonPrimitive(Boolean.FALSE);
            }
            boolean applied = state.setGameplay(p.get(0).getAsString(), p.get(1).getAsBoolean());
            return new JsonPrimitive(Boolean.valueOf(applied));
        }
        if ("setHud".equals(call)) {
            if (p.size() < 2 || !p.get(1).isJsonObject()) {
                return JsonNull.INSTANCE;
            }
            JsonObject placement = p.get(1).getAsJsonObject();
            Double scale = placement.has("scale") && !placement.get("scale").isJsonNull()
                    ? Double.valueOf(placement.get("scale").getAsDouble()) : null;
            HudItem stored = state.setHud(
                    p.get(0).getAsString(),
                    Json.string(placement, "anchor", "top-left"),
                    Json.number(placement, "dx", 0),
                    Json.number(placement, "dy", 0),
                    scale);
            return stored == null ? JsonNull.INSTANCE : stored.toJson();
        }
        if ("setModSetting".equals(call)) {
            if (p.size() < 3) {
                return JsonNull.INSTANCE;
            }
            JsonElement stored = state.setModSetting(
                    p.get(0).getAsString(), p.get(1).getAsString(), p.get(2));
            return stored == null ? JsonNull.INSTANCE : stored;
        }
        if ("switchLoadout".equals(call)) {
            if (p.size() < 1) {
                return new JsonPrimitive(Boolean.FALSE);
            }
            return new JsonPrimitive(Boolean.valueOf(state.switchLoadout(p.get(0).getAsString())));
        }
        if ("closeMenu".equals(call)) {
            // GAME. mc.setScreen(null) runs Screen.removed(), which frees the backdrop and shadow
            // textures, and it changes what the renderer is about to draw — neither is survivable
            // from the UI thread. Nothing is lost by deferring it: bridge.json gives closeMenu no
            // return value, so the page is told nothing either way, and the drain happens at the
            // top of the very next frame (onRenderOverlay) rather than a tick later.
            if (host != null) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        host.closeMenu();
                    }
                });
            }
            return JsonNull.INSTANCE;
        }
        if ("setSurfaces".equals(call)) {
            if (host == null || p.size() < 1 || !p.get(0).isJsonArray()) {
                return new JsonPrimitive(Integer.valueOf(0));
            }
            java.util.List<dev.voidpvp.client.render.EffectSurface> surfaces =
                    new java.util.ArrayList<dev.voidpvp.client.render.EffectSurface>();
            com.google.gson.JsonArray arr = p.get(0).getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                dev.voidpvp.client.render.EffectSurface s = readSurface(arr.get(i));
                if (s != null) {
                    surfaces.add(s);
                }
            }
            // Inline, not queued. The host only publishes the list through a volatile field; it
            // draws nothing here. Queueing it would put the geometry a frame behind the layout
            // that produced it, which during a resize is exactly when the shadow and the panel
            // visibly disagree. The count is a property of the parse above, so answering it needs
            // no game state.
            host.setSurfaces(surfaces);
            return new JsonPrimitive(Integer.valueOf(surfaces.size()));
        }
        if ("openKeybindCapture".equals(call)) {
            // Inline, not queued: arming is two volatile flags on the host and touches no
            // Minecraft object. Queueing it would leave the capture unarmed for up to a frame
            // after the page thinks it is open, and every key pressed in that window would be
            // swallowed by the menu instead of captured.
            if (host != null) {
                host.beginKeybindCapture(p.size() > 0 ? p.get(0).getAsString() : null);
            }
            // The synchronous answer only says the capture is armed. The key
            // itself arrives later as a call-result envelope on the push
            // channel — see keybindScript.
            return JsonNull.INSTANCE;
        }
        return JsonNull.INSTANCE;
    }

    // -----------------------------------------------------------------
    // Marshalling to the game thread
    // -----------------------------------------------------------------

    /**
     * Queues work classified GAME, to run on the game thread at the next drain.
     *
     * <p>Fire and forget in both directions. Nothing here reports back to the caller, because the
     * caller is a JavaScript call the page is blocked inside and the whole point of the queue is
     * that it never becomes a round trip.</p>
     */
    public void post(Runnable work) {
        if (work != null) {
            gameThreadWork.add(work);
        }
    }

    /**
     * Runs the queued game-thread work. <b>Call only from the game thread.</b>
     *
     * <p>{@code VoidClient} drains at the top of {@code onRenderOverlay}, so a {@code closeMenu}
     * lands within a frame, and again at the top of {@code onClientTick}, which is the only beat
     * that runs when no world is loaded and {@code InGameHud} therefore never renders.</p>
     *
     * @return how many pieces of work ran, for tests
     */
    public int runGameThreadWork() {
        int ran = 0;
        while (ran < MAX_WORK_PER_DRAIN) {
            Runnable work = gameThreadWork.poll();
            if (work == null) {
                break;
            }
            ran++;
            try {
                work.run();
            } catch (RuntimeException e) {
                // Same contract as dispatch: this runs inside a frame, and one bad piece of work
                // must not take the frame — or the rest of the queue — with it.
                recordError("gameThreadWork: " + e);
            }
        }
        return ran;
    }

    /** Whether anything is waiting for the game thread. Diagnostics and tests. */
    public boolean hasGameThreadWork() {
        return !gameThreadWork.isEmpty();
    }

    /**
     * One entry of {@code setSurfaces}. Returns null for anything malformed rather than throwing:
     * this arrives from the page every time the layout moves, and a bad entry must cost a missing
     * shadow, never a broken frame.
     */
    private static dev.voidpvp.client.render.EffectSurface readSurface(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject o = element.getAsJsonObject();
        if (!o.has("shadow") || !o.get("shadow").isJsonObject()) {
            return null;
        }
        JsonObject sh = o.getAsJsonObject("shadow");
        com.google.gson.JsonArray c = sh.has("color") && sh.get("color").isJsonArray()
                ? sh.getAsJsonArray("color")
                : null;
        if (c == null || c.size() < 4) {
            return null;
        }
        try {
            return new dev.voidpvp.client.render.EffectSurface(
                    o.has("id") ? o.get("id").getAsString() : "?",
                    num(o, "x"), num(o, "y"), num(o, "w"), num(o, "h"), num(o, "radius"),
                    num(sh, "dx"), num(sh, "dy"), num(sh, "blur"), num(sh, "spread"),
                    c.get(0).getAsFloat(), c.get(1).getAsFloat(),
                    c.get(2).getAsFloat(), c.get(3).getAsFloat());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static float num(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsFloat() : 0f;
    }

    private static String result(String call, JsonElement returns) {
        JsonObject o = new JsonObject();
        o.addProperty("c", call);
        o.add("returns", returns == null ? JsonNull.INSTANCE : returns);
        return o.toString();
    }

    /** Dispatch failures, for the log and for tests. */
    public List<String> errors() {
        return errors;
    }

    // -----------------------------------------------------------------
    // Java -> JS
    // -----------------------------------------------------------------

    /** Queues one {@code {e, payload}} envelope for this frame's push. */
    public void emit(String event, JsonElement payload) {
        JsonObject env = new JsonObject();
        env.addProperty("e", event);
        env.add("payload", payload == null ? JsonNull.INSTANCE : payload);
        synchronized (pending) {
            // `tick`, `keys`, `menu` and `server` carry whole state, so an
            // older envelope on the same channel is dead weight. `loadout` is
            // whole-state too but a switch is rare enough to keep in order.
            if (EVENT_TICK.equals(event) || EVENT_KEYS.equals(event) || EVENT_MENU.equals(event)
                    || EVENT_LOADOUTS.equals(event)) {
                dropChannel(event);
            }
            pending.addLast(env);
        }
    }

    private void dropChannel(String event) {
        java.util.Iterator<JsonObject> it = pending.iterator();
        while (it.hasNext()) {
            if (event.equals(Json.string(it.next(), "e", null))) {
                it.remove();
            }
        }
    }

    public boolean hasPending() {
        synchronized (pending) {
            return !pending.isEmpty();
        }
    }

    /**
     * Drains the frame's events into the one script that delivers them, or
     * {@code null} when nothing happened. The shim's {@code __emit} takes an
     * array of envelopes and fans them out to the {@code on} handlers.
     *
     * <p><b>Call only from the UI thread</b> — the returned script is fed straight to
     * {@code evaluateScript}. Producers ({@link #emit}, {@link #emitCallResult}) may be on any
     * thread, which is what the monitor on {@code pending} is for.</p>
     */
    public String drainScript() {
        JsonArray batch = new JsonArray();
        synchronized (pending) {
            if (pending.isEmpty()) {
                return null;
            }
            while (!pending.isEmpty()) {
                batch.add(pending.pollFirst());
            }
        }
        return "window.void.__emit(" + batch.toString() + ")";
    }

    /**
     * Queues a deferred call result onto the push channel — today only
     * {@code openKeybindCapture}.
     *
     * <p>This is how the captured key gets back to JS without the game thread ever entering the
     * view. The key is read in {@code VoidMenuScreen.keyPressed}, which is the game thread, and
     * {@code evaluateScript} enters JavaScriptCore, which now belongs to the UI thread; so the
     * envelope rides the frame batch instead, exactly as an event does. The shim's {@code __emit}
     * already accepts a {@code {c, returns}} entry inside the batch — that is what
     * {@link #keybindScript} sends as a batch of one — so nothing on the JS side changes. It costs
     * one frame of latency on a key the player has already pressed.</p>
     */
    public void emitCallResult(String call, JsonElement returns) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("c", call);
        envelope.add("returns", returns == null ? JsonNull.INSTANCE : returns);
        synchronized (pending) {
            // Not coalesced: two captures armed in a row resolve in the order they were armed,
            // which is the order the shim's FIFO of pending Promises expects.
            pending.addLast(envelope);
        }
    }

    /**
     * The script that resolves an {@code openKeybindCapture} promise.
     *
     * <p>A <em>call-result</em> envelope on the same {@code __emit} channel the
     * events use, which is what {@code bridge.json} specifies and what
     * {@code @void/protocol}'s reference shim listens for. The synchronous
     * answer to the call was {@code returns: null} and only meant "armed"; this
     * is the value the Promise actually settles with — the key, or null when
     * the player pressed Escape.</p>
     */
    public static String keybindScript(String keyName) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("c", "openKeybindCapture");
        envelope.add("returns", keyName == null ? JsonNull.INSTANCE : new JsonPrimitive(keyName));
        return "window.void.__emit(" + envelope.toString() + ")";
    }

    /** Queues the {@code setting} event for one mod setting Java changed itself. */
    public void emitSetting(String modId, String key, JsonElement value) {
        JsonObject payload = new JsonObject();
        payload.addProperty("id", modId);
        payload.addProperty("key", key);
        payload.add("value", value == null ? JsonNull.INSTANCE : value);
        emit(EVENT_SETTING, payload);
    }
}
