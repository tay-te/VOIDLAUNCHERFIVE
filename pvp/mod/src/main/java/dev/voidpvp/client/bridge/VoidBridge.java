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
 * setGlobal           LiveState.setGlobal                                SHARED  inline, synchronized
 *                       LiveState.applySettings                          SHARED  volatile writes
 *                       (ui_scale) VoidClient.pumpUi -&gt; UiHost.ensure     SHARED  read next game frame
 * switchLoadout       LiveState.switchLoadout                            SHARED  inline, synchronized
 *                       library lookup, applyLoadoutInternal, LoadoutDiff SHARED  under the LiveState monitor
 *                       LiveState.loadoutJson -&gt; emit("loadout")         SHARED  synchronized read, queued push
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
 * <p>Four calls answer with a value the page uses: {@code setGameplay} a
 * boolean, {@code setModSetting} the stored element, {@code setHud} the clamped
 * item, {@code setGlobal} the stored global. None of them is a round trip, because <em>none of
 * them needs the game thread to compute its answer</em>: they are writes to {@link LiveState},
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

    /**
     * The seven channels of {@code bridge.json#/definitions/event_name}, plus {@code session}
     * and {@code settings}.
     */
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
     * Who is playing — name, uuid, and whether the account is offline or Microsoft.
     *
     * <p>The eighth channel, and the only one that carries something immutable. It exists because
     * the page has to be <em>told</em>: it has no network (Ultralight runs off the classpath) and
     * no other channel is about the player rather than the world. {@code server} was the obvious
     * host and is the wrong one — it is edge-triggered from {@code onWorldChanged}, so in a
     * single-player dev client it never fires at all, and the chip would be blank in exactly the
     * client somebody is debugging.</p>
     *
     * <p>Pushed only from {@link #pushWholeState()}, which is the one definition of what a fresh
     * page needs (§9a): a reloaded document gets it again, and nothing else has to remember to.</p>
     */
    public static final String EVENT_SESSION = "session";

    /**
     * The global settings, whole, as {@code protocol.json#/definitions/global_settings}.
     *
     * <p>The ninth channel. {@link dev.voidpvp.client.state.LiveState} has held
     * {@code menuKeyCode}, {@code uiScale} and {@code theme} since the beginning and Rust could
     * push them down, but the page could neither read nor write any of it: there was no channel
     * carrying them and no call to change them. This is the read half; {@code setGlobal} is the
     * write half.</p>
     *
     * <p>Pushed from {@link #pushWholeState()}, so a reloaded document gets it back, and from
     * {@code VoidClient.onSettings} when the launcher changes them under the game. <b>Not</b>
     * pushed as an echo of the page's own {@code setGlobal} — §6.5 does not push a change back to
     * the page that made it, and the call already returned the stored value.</p>
     *
     * <p>Whole-state, so {@link #emit} coalesces it: only the newest settings object in a frame
     * is worth delivering.</p>
     */
    public static final String EVENT_SETTINGS = "settings";

    /**
     * A key bound to one mod asked that mod to do something.
     *
     * <p>The tenth channel, and the first that is neither state nor a sensor reading: it is an
     * <em>input</em>. {@link #EVENT_SETTING} can say exactly one thing — this mod's {@code on}
     * moved — which covers every mod whose key is a switch and no mod whose key is a verb, and
     * that is why {@code stopwatch} was held out of the last wave ({@code schema/mods/
     * stopwatch.json}). Java owns the key, the widget owns what the key means.</p>
     *
     * <p>It stops here. {@code protocol.json} carries nothing for it and Rust is never told:
     * a {@code modaction} is a request that the page in this same JAR fulfils, not a change the
     * launcher's tray or its next launch has to agree with — see the {@code $comment} on
     * {@code modaction_payload}.</p>
     *
     * <p><b>Never coalesced</b>, unlike the whole-state channels above, and that is the whole
     * behaviour rather than an omission: two taps of a stopwatch's start key are a stop and a
     * start, so folding them into one would silently swallow half a player's input on a slow
     * frame.</p>
     */
    public static final String EVENT_MODACTION = "modaction";

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
    /**
     * The last value sent on the {@code menu} channel.
     *
     * <p>The one piece of the page's whole state that has no other home. {@code loadout} and
     * {@code loadouts} can be re-read from {@link LiveState} at any moment, and {@code tick},
     * {@code keys} and {@code server} re-arrive from their sensors within a tick — but whether
     * the menu is open exists only as something that was pushed, so {@link #pushWholeState} would
     * have nothing to say without this. Written on the game thread by {@link #emitMenu} and read
     * on the UI thread by {@link #pushWholeState}, hence volatile.</p>
     */
    private volatile boolean menuOpen;

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
        if ("setGlobal".equals(call)) {
            if (p.size() < 2) {
                return JsonNull.INSTANCE;
            }
            // SHARED, inline. Same shape as setModSetting and for the same reason: the answer is
            // what the clamp produced, and LiveState is reachable from this thread. ui_scale takes
            // effect without anything being queued — VoidClient.pumpUi re-reads state.uiScale on
            // every game frame and hands it to UiHost.ensure, whose UI-thread applySize() sees the
            // device scale move and resizes the view. Posting a resize from here would be both a
            // second path to the same place and a renderer call from the wrong thread.
            JsonElement stored = state.setGlobal(p.get(0).getAsString(), p.get(1));
            return stored == null ? JsonNull.INSTANCE : stored;
        }
        if ("switchLoadout".equals(call)) {
            if (p.size() < 1) {
                return new JsonPrimitive(Boolean.FALSE);
            }
            boolean switched = state.switchLoadout(p.get(0).getAsString());
            if (switched) {
                // bridge.json, `call_switchLoadout`: "A `loadout` event follows with the new
                // loadout, so the caller does not need the returned object" — and
                // `loadout_payload`: pushed "on every switch, whether the switch came from Rust,
                // from the tray, from the L key or from `void.switchLoadout`".
                //
                // It was not being pushed here, and LoadoutsScreen.tsx says in as many words
                // that it holds no optimistic state: it renders `store.loadout` and waits for
                // this event. So picking a card switched every actuator in Java and left the
                // whole page — the card's own selected state, the mods grid, the properties
                // panel, the HUD layer — rendering the loadout the player had just left. The
                // boolean return said `true` the entire time.
                //
                // Not `loadouts`: the library's membership has not changed, and the page's
                // `applyLoadout` already replaces the library's copy of this id. Not
                // `requestRender` either — every path that reaches this call is inside the open
                // menu, which UiHost renders continuously (VoidClient.pumpUi's setContinuous).
                //
                // `loadoutJson()` rather than the object: serialising walks every mod's settings
                // map, and this thread is not the one that owns it.
                emit(EVENT_LOADOUT, state.loadoutJson());
            }
            return new JsonPrimitive(Boolean.valueOf(switched));
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
            // `keys`, `menu`, `loadouts` and `settings` carry whole state, so an older envelope
            // on the same channel really is dead weight. `loadout` is whole-state too but a
            // switch is rare enough to keep in order.
            if (EVENT_KEYS.equals(event) || EVENT_MENU.equals(event)
                    || EVENT_LOADOUTS.equals(event) || EVENT_SETTINGS.equals(event)) {
                dropChannel(event);
            }
            // `tick` is the exception, and it used to be in the list above.
            //
            // It is the one channel that is *not* whole state: `TickCoalescer` puts a field on
            // the tick it changed on and never repeats it, which is the whole reason a tick where
            // nothing moved is not sent at all. So dropping an older queued tick does not discard
            // a stale copy of anything — it discards the only copy of every field that tick was
            // carrying. For `fps`, `ping` and `pos` that is invisible, because they change again a
            // moment later. For `armor` and `fx` it is permanent: a set of armour nobody is
            // hitting and a potion nobody has drunk never change, so the array goes out once and
            // its envelope is deleted by the next tick to arrive before the page drains.
            //
            // Which is exactly what the in-game audit found — the Armour widget blank from world
            // entry through sixty-five steps of a run, coming back only at the step that happened
            // to change the array's length. Merging keeps the queue at one tick, which is what
            // this was for, without the queue deciding that a field it cannot see again is
            // redundant.
            if (EVENT_TICK.equals(event)) {
                mergeOlderTicksInto(env);
            }
            pending.addLast(env);
        }
    }

    /**
     * Folds every queued {@code tick} into {@code newest} and removes them, newest field winning.
     *
     * <p>Order matters and is the reason this is not a `putIfAbsent` loop: the queue holds the
     * ticks oldest-first, so each one overwrites the fields of the tick before it, and the
     * envelope being queued overwrites all of them. A field's most recent value is the one the
     * page is handed; a field only an older tick carried survives.</p>
     */
    private void mergeOlderTicksInto(JsonObject newest) {
        JsonElement newestPayload = newest.get("payload");
        if (newestPayload == null || !newestPayload.isJsonObject()) {
            return;
        }
        JsonObject merged = new JsonObject();
        java.util.Iterator<JsonObject> it = pending.iterator();
        while (it.hasNext()) {
            JsonObject old = it.next();
            if (!EVENT_TICK.equals(Json.string(old, "e", null))) {
                continue;
            }
            JsonElement payload = old.get("payload");
            if (payload != null && payload.isJsonObject()) {
                for (java.util.Map.Entry<String, JsonElement> field
                        : payload.getAsJsonObject().entrySet()) {
                    merged.add(field.getKey(), field.getValue());
                }
            }
            it.remove();
        }
        if (merged.entrySet().isEmpty()) {
            return;
        }
        for (java.util.Map.Entry<String, JsonElement> field
                : newestPayload.getAsJsonObject().entrySet()) {
            merged.add(field.getKey(), field.getValue());
        }
        newest.add("payload", merged);
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

    /**
     * Everything a page that has just loaded needs in order to be showing this session.
     *
     * <p><b>This is the only definition of "what a fresh page needs", and it has three callers:
     * the first push at start-up, the launcher's {@code init}, and a document the host did not
     * load.</b> The third is the one that was missing. When the GL driver dies mid-session
     * {@link dev.voidpvp.client.ui.UltralightWebView} rebuilds the view on the CPU surface, and a
     * replacement view is a new view — the page reloads, and every envelope this bridge had
     * already delivered went to a document that no longer exists. Nothing re-sent it, so the menu
     * the player had open simply stopped being drawn, while the mod went on believing it was open
     * and painting an empty document eighty times a second.</p>
     *
     * <p>Keeping the three callers on one method is the point. Two paths that must agree about
     * what a fresh page needs is how one of them rots — a channel added for start-up and
     * forgotten here would come back as "the menu loses its X after a fallback", which is exactly
     * the shape of the bug this fixes.</p>
     *
     * <p><b>Read live, not replayed.</b> {@code loadout}, {@code loadouts} and {@code settings}
     * are re-serialised
     * from {@link LiveState} rather than resent from a remembered envelope, because a remembered
     * one would be stale: {@code setGameplay}, {@code setModSetting}, {@code setHud} and
     * {@code setGlobal} all change
     * the loadout without emitting anything (§6.5 deliberately does not push a change back to the
     * page that made it), so every toggle the player had flipped this session would be missing
     * from a snapshot. {@code tick}, {@code keys} and {@code server} are not sent at all: their
     * sensors push whole state every tick, so the queue refills with current values long before a
     * freshly loaded page is ready to receive anything.</p>
     *
     * <p>{@code session} is the exception that proves that rule and is sent first. It is immutable
     * and has no sensor behind it, so "the queue refills" is false for it: whatever misses it
     * never learns it. See {@link #EVENT_SESSION}.</p>
     *
     * <p>Safe from any thread — {@link #emit} takes the monitor on the queue, and both
     * {@link LiveState} serialisers are synchronized.</p>
     */
    public void pushWholeState() {
        // First, because it is the one thing here that will never be pushed again: no sensor
        // produces it and no change can. A page that missed it would carry a blank profile chip
        // for the life of the process.
        if (host != null) {
            JsonObject session = host.sessionJson();
            if (session != null) {
                emit(EVENT_SESSION, session);
            }
            // Nothing is emitted here: this asks the sensor to forget what it last reported, so
            // that the next tick carries `armor` and `fx` again. They belong in this method for
            // the same reason `session` is the line above it — a page that missed them is never
            // sent them again — but they cannot be *pushed* from here, because they are read off
            // the player on the game thread and a reloading page may be at the title screen.
            host.resendSensors();
        }
        // Second, and for the same reason as session: the globals are the page's chrome — the
        // theme it paints in and the scale it lays out at — so they want to be in hand before the
        // content arrives. Re-serialised from LiveState, never replayed: `setGlobal` writes the
        // record without emitting anything (§6.5), so a remembered envelope would hand a reloaded
        // page the scale the launcher sent at start-up rather than the one in force.
        emit(EVENT_SETTINGS, state.settings().toJson());
        // The library first, then the active loadout: the order onInit uses, so the page's store
        // settles on the live copy rather than the library's.
        emit(EVENT_LOADOUTS, state.libraryJson());
        emit(EVENT_LOADOUT, state.loadoutJson());
        // Last, so the screen the menu opens onto is drawn against data that is already there.
        emitMenu(menuOpen);
    }

    /**
     * Queues the {@code menu} event, and remembers it.
     *
     * <p>Every push on this channel goes through here rather than through {@link #emit} directly,
     * so that {@link #menuOpen} cannot drift from what the page was last told. A second way to
     * emit {@code menu} would be a second thing to keep in sync, which is the failure this whole
     * change is about.</p>
     */
    public void emitMenu(boolean open) {
        menuOpen = open;
        emit(EVENT_MENU, new JsonPrimitive(Boolean.valueOf(open)));
    }

    /** Queues the {@code setting} event for one mod setting Java changed itself. */
    public void emitSetting(String modId, String key, JsonElement value) {
        JsonObject payload = new JsonObject();
        payload.addProperty("id", modId);
        payload.addProperty("key", key);
        payload.add("value", value == null ? JsonNull.INSTANCE : value);
        emit(EVENT_SETTING, payload);
    }

    /**
     * Queues the {@code modaction} event for one mod-scoped key Java edged.
     *
     * <p>{@code action} names the <em>request</em> and never the key that made it —
     * {@code start_stop}, not {@code bound_key} — so a rebind cannot reach the page and the same
     * action can arrive from a menu button tomorrow. Nothing is stored and nothing is returned:
     * a page that is not listening loses the press and no state is left disagreeing with it.</p>
     *
     * @see #EVENT_MODACTION
     */
    public void emitModAction(String modId, String action) {
        JsonObject payload = new JsonObject();
        payload.addProperty("mod", modId);
        payload.addProperty("action", action);
        emit(EVENT_MODACTION, payload);
    }
}
