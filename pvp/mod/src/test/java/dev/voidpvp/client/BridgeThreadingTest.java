package dev.voidpvp.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.voidpvp.client.bridge.BridgeHost;
import dev.voidpvp.client.bridge.VoidBridge;
import dev.voidpvp.client.render.EffectSurface;
import dev.voidpvp.client.state.Json;
import dev.voidpvp.client.state.LiveState;
import dev.voidpvp.client.state.Loadout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The threading half of {@code window.void}: Ultralight runs JavaScript on its own UI thread, so
 * every call in {@code bridge.json} arrives off the game thread.
 *
 * <p>These tests pin the classification recorded in {@link VoidBridge}: what may run inside the
 * call, what has to be queued for the game thread, and — the rule the whole design rests on — that
 * neither thread ever waits for the other. The game thread is stood in for by the test thread,
 * which is the only thread that ever calls {@code runGameThreadWork()}.</p>
 */
class BridgeThreadingTest {

    /**
     * A host that records <em>which thread</em> reached it, not just that something did. That is
     * the whole assertion: a game-thread method reached from the dispatching thread is the bug the
     * marshalling exists to prevent, and it is invisible to a test that only counts calls.
     */
    private static final class Host implements BridgeHost {
        final AtomicInteger closes = new AtomicInteger();
        final AtomicReference<Thread> closedOn = new AtomicReference<Thread>();
        final AtomicReference<Thread> armedOn = new AtomicReference<Thread>();
        final AtomicReference<Thread> surfacedOn = new AtomicReference<Thread>();
        volatile String captureModId;
        volatile List<EffectSurface> surfaces = Collections.emptyList();

        @Override
        public void closeMenu() {
            closes.incrementAndGet();
            closedOn.set(Thread.currentThread());
        }

        @Override
        public void beginKeybindCapture(String modId) {
            captureModId = modId;
            armedOn.set(Thread.currentThread());
        }

        @Override
        public void setSurfaces(List<EffectSurface> next) {
            surfaces = next;
            surfacedOn.set(Thread.currentThread());
        }

        /** Counted: `pushWholeState` asking the host to resend the once-only sensors. */
        int resendSensorsCalls;

        @Override
        public void resendSensors() {
            resendSensorsCalls++;
        }

        /** Not exercised here; the bridge only asks for it from pushWholeState. */
        @Override
        public com.google.gson.JsonObject sessionJson() {
            return null;
        }
    }

    /** The library from {@code protocol.json}'s init example, so switchLoadout has somewhere to go. */
    private static LiveState seededState() {
        JsonArray examples = Schemas.examples("protocol.json");
        JsonObject init = null;
        for (int i = 0; i < examples.size(); i++) {
            JsonObject o = examples.get(i).getAsJsonObject();
            if ("init".equals(o.get("t").getAsString())) {
                init = o;
            }
        }
        assertNotNull(init, "protocol.json lost its init example");
        List<Loadout> library = new ArrayList<Loadout>();
        JsonArray arr = init.getAsJsonArray("loadouts");
        for (int i = 0; i < arr.size(); i++) {
            library.add(Loadout.fromJson(arr.get(i).getAsJsonObject()));
        }
        LiveState state = new LiveState();
        state.applyInit(Loadout.fromJson(init.getAsJsonObject("loadout")), library,
                dev.voidpvp.client.state.GlobalSettings.fromJson(init.getAsJsonObject("settings")));
        return state;
    }

    /** Dispatches on a thread that is deliberately not this one, and waits for the answer only. */
    private static String dispatchOffThread(final VoidBridge bridge, final String request)
            throws InterruptedException {
        final AtomicReference<String> answer = new AtomicReference<String>();
        Thread ui = new Thread(new Runnable() {
            @Override
            public void run() {
                answer.set(bridge.dispatch(request));
            }
        }, "test-ui-thread");
        ui.start();
        // The UI thread answering the page is what we are waiting on; the game thread never does
        // this, which is exactly the asymmetry the bridge is built around.
        ui.join(5000);
        assertFalse(ui.isAlive(), "dispatch did not return — it blocked on the game thread");
        return answer.get();
    }

    // -----------------------------------------------------------------
    // Queued: work that touches the game
    // -----------------------------------------------------------------

    @Test
    @DisplayName("a queued action does not run until the game thread drains it")
    void queuedWorkWaitsForTheDrain() {
        Host host = new Host();
        VoidBridge bridge = new VoidBridge(seededState(), host);

        String answer = bridge.dispatch("{\"c\":\"closeMenu\",\"params\":[]}");
        assertEquals("{\"c\":\"closeMenu\",\"returns\":null}", answer,
                "the page still gets the documented answer, immediately");
        assertEquals(0, host.closes.get(), "nothing touched the game inside the call");
        assertTrue(bridge.hasGameThreadWork(), "it is waiting for the game thread");

        assertEquals(1, bridge.runGameThreadWork());
        assertEquals(1, host.closes.get());
        assertFalse(bridge.hasGameThreadWork());

        assertEquals(0, bridge.runGameThreadWork(), "a second drain runs it again");
        assertEquals(1, host.closes.get());
    }

    @Test
    @DisplayName("dispatch from a non-game thread never touches game state inline")
    void gameStateIsOnlyTouchedOnTheDrainingThread() throws InterruptedException {
        Host host = new Host();
        VoidBridge bridge = new VoidBridge(seededState(), host);

        String answer = dispatchOffThread(bridge, "{\"c\":\"closeMenu\",\"params\":[]}");
        assertEquals("{\"c\":\"closeMenu\",\"returns\":null}", answer);
        assertNull(host.closedOn.get(), "the UI thread did not reach the game-thread method");

        bridge.runGameThreadWork();
        assertSame(Thread.currentThread(), host.closedOn.get(),
                "closeMenu ran on the thread that drained, not the thread that dispatched");
    }

    @Test
    @DisplayName("queued work runs in the order it was posted, once each")
    void queuedWorkIsFifo() {
        VoidBridge bridge = new VoidBridge(seededState(), new Host());
        final StringBuilder order = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            final int at = i;
            bridge.post(new Runnable() {
                @Override
                public void run() {
                    order.append(at);
                }
            });
        }
        assertEquals(5, bridge.runGameThreadWork());
        assertEquals("01234", order.toString());
    }

    @Test
    @DisplayName("one drain cannot spend the whole frame")
    void aDrainIsBounded() {
        VoidBridge bridge = new VoidBridge(seededState(), new Host());
        final AtomicInteger ran = new AtomicInteger();
        Runnable work = new Runnable() {
            @Override
            public void run() {
                ran.incrementAndGet();
            }
        };
        for (int i = 0; i < 200; i++) {
            bridge.post(work);
        }
        // 64 is the cap in VoidBridge: a page that called closeMenu in a loop must not be able to
        // spend a 16.6 ms frame here. The rest simply waits for the next drain, a frame away.
        assertEquals(64, bridge.runGameThreadWork());
        assertEquals(64, ran.get());
        assertTrue(bridge.hasGameThreadWork(), "the remainder is still queued, not dropped");
        assertEquals(64, bridge.runGameThreadWork());
        assertEquals(64, bridge.runGameThreadWork());
        assertEquals(8, bridge.runGameThreadWork());
        assertEquals(200, ran.get());
    }

    @Test
    @DisplayName("work that throws costs its own turn and nothing else")
    void aThrowingTaskDoesNotTakeTheFrame() {
        VoidBridge bridge = new VoidBridge(seededState(), new Host());
        final AtomicInteger ran = new AtomicInteger();
        bridge.post(new Runnable() {
            @Override
            public void run() {
                throw new IllegalStateException("boom");
            }
        });
        bridge.post(new Runnable() {
            @Override
            public void run() {
                ran.incrementAndGet();
            }
        });
        assertEquals(2, bridge.runGameThreadWork());
        assertEquals(1, ran.get(), "the second piece of work still ran");
        assertEquals(1, bridge.errors().size(), "and the failure was recorded: " + bridge.errors());
    }

    // -----------------------------------------------------------------
    // Inline: work that only touches state the UI thread may read
    // -----------------------------------------------------------------

    @Test
    @DisplayName("the value-returning calls answer authoritatively without the game thread")
    void valueReturningCallsNeedNoRoundTrip() throws InterruptedException {
        LiveState state = seededState();
        Host host = new Host();
        VoidBridge bridge = new VoidBridge(state, host);

        // setGameplay returns the state actually applied. It is computed from LiveState, which the
        // UI thread may read for itself, so there is nothing optimistic about the answer and
        // nothing for the page to reconcile later.
        assertEquals("{\"c\":\"setGameplay\",\"returns\":true}",
                dispatchOffThread(bridge, "{\"c\":\"setGameplay\",\"params\":[\"fullbright\",true]}"));
        assertTrue(state.fullbrightOn, "the actuator field was written by the dispatching thread");

        // setModSetting returns the value stored after clamping — 9 is far outside opacity's 0..1.
        assertEquals("{\"c\":\"setModSetting\",\"returns\":1.0}", dispatchOffThread(bridge,
                "{\"c\":\"setModSetting\",\"params\":[\"keystrokes\",\"opacity\",9]}"));

        // setHud returns the placement after the snap grid, which the editor drops the element on.
        JsonObject hud = Json.parseObject(dispatchOffThread(bridge,
                "{\"c\":\"setHud\",\"params\":[\"fps\",{\"anchor\":\"top-left\",\"dx\":13,"
                        + "\"dy\":-7,\"scale\":1.25}]}"));
        assertEquals("fps", hud.getAsJsonObject("returns").get("id").getAsString());

        assertEquals("{\"c\":\"switchLoadout\",\"returns\":true}",
                dispatchOffThread(bridge, "{\"c\":\"switchLoadout\",\"params\":[\"bedwars\"]}"));
        assertEquals("bedwars", state.loadoutId());

        assertFalse(bridge.hasGameThreadWork(),
                "none of the four needed the game thread, so none of them queued anything");
        assertTrue(bridge.errors().isEmpty(), bridge.errors().toString());
    }

    @Test
    @DisplayName("setSurfaces and openKeybindCapture take effect inside the call")
    void latencySensitiveCallsAreNotQueued() throws InterruptedException {
        Host host = new Host();
        VoidBridge bridge = new VoidBridge(seededState(), host);

        // Both are deliberately inline. Queueing the shadow geometry would leave it a frame behind
        // the layout that measured it — visible as a shadow trailing the panel during a resize —
        // and queueing the arming would swallow any key the player pressed inside that frame.
        String surfaces = dispatchOffThread(bridge, "{\"c\":\"setSurfaces\",\"params\":[["
                + "{\"id\":\"panel\",\"x\":0,\"y\":0,\"w\":100,\"h\":50,\"radius\":8,"
                + "\"shadow\":{\"dx\":0,\"dy\":30,\"blur\":70,\"spread\":-20,"
                + "\"color\":[0,0,0,0.6]}}]]}");
        assertEquals("{\"c\":\"setSurfaces\",\"returns\":1}", surfaces);
        assertEquals(1, host.surfaces.size());
        assertNotNull(host.surfacedOn.get(), "the host was handed the geometry inside the call");
        assertFalse(Thread.currentThread().equals(host.surfacedOn.get()),
                "and on the dispatching thread, not the game thread");

        String armed = dispatchOffThread(bridge,
                "{\"c\":\"openKeybindCapture\",\"params\":[\"zoom\"]}");
        assertEquals("{\"c\":\"openKeybindCapture\",\"returns\":null}", armed);
        assertEquals("zoom", host.captureModId, "armed before the call returned");
        assertFalse(bridge.hasGameThreadWork(), "neither call queued anything");
    }

    // -----------------------------------------------------------------
    // The other direction
    // -----------------------------------------------------------------

    @Test
    @DisplayName("a deferred call result rides the push channel instead of a script")
    void keybindResultTravelsAsAnEnvelope() {
        VoidBridge bridge = new VoidBridge(seededState(), new Host());
        bridge.emit(VoidBridge.EVENT_MENU, new JsonPrimitive(Boolean.TRUE));
        bridge.emitCallResult("openKeybindCapture", new JsonPrimitive("V"));
        bridge.emitCallResult("openKeybindCapture", null);

        // One script, drained by the UI thread — which is the point: the game thread reads the
        // captured key but must never call evaluateScript to deliver it.
        String script = bridge.drainScript();
        JsonArray batch = Json.parse(script.substring("window.void.__emit(".length(),
                script.length() - 1)).getAsJsonArray();
        assertEquals(3, batch.size());
        JsonObject key = batch.get(1).getAsJsonObject();
        assertEquals("openKeybindCapture", key.get("c").getAsString());
        assertEquals("V", key.get("returns").getAsString());
        JsonObject cancelled = batch.get(2).getAsJsonObject();
        assertTrue(cancelled.get("returns").isJsonNull(), "Escape resolves with null, in order");
    }

    // -----------------------------------------------------------------
    // The state underneath
    // -----------------------------------------------------------------

    @Test
    @DisplayName("what leaves LiveState is a snapshot, not a window onto the live loadout")
    void whatEscapesTheMonitorIsACopy() {
        LiveState state = seededState();
        VoidBridge bridge = new VoidBridge(state, new Host());

        // Loadout is a plain mutable object guarded only by LiveState's monitor, so anything the
        // game thread keeps hold of past the call has to be a copy. This is the deterministic half
        // of the threading fix; the soak test below is the probabilistic half.
        List<dev.voidpvp.client.state.HudItem> before = state.loadout().hud();
        int count = before.size();
        bridge.dispatch("{\"c\":\"setHud\",\"params\":[\"coordinates\","
                + "{\"anchor\":\"bottom-right\",\"dx\":8,\"dy\":8,\"scale\":1}]}");
        assertEquals(count, before.size(),
                "the list handed out earlier did not grow when the page moved a HUD item");

        JsonObject snapshot = state.loadoutJson();
        String opacityBefore = snapshot.getAsJsonObject("mods")
                .getAsJsonObject("keystrokes").get("opacity").toString();
        bridge.dispatch("{\"c\":\"setModSetting\",\"params\":[\"keystrokes\",\"opacity\",0.25]}");
        assertEquals(opacityBefore, snapshot.getAsJsonObject("mods")
                        .getAsJsonObject("keystrokes").get("opacity").toString(),
                "the JSON handed to the push channel is not a live view of the loadout");
    }

    @Test
    @DisplayName("three writers and a reader do not deadlock or throw")
    void liveStateSurvivesThreeWriters() throws InterruptedException {
        final LiveState state = seededState();
        final VoidBridge bridge = new VoidBridge(state, new Host());
        final int rounds = 400;
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        // The UI thread: a slider being dragged, which is the realistic worst case — one
        // setModSetting per frame, each one rewriting a settings map.
        Thread ui = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    start.await();
                    for (int i = 0; i < rounds; i++) {
                        bridge.dispatch("{\"c\":\"setModSetting\",\"params\":[\"keystrokes\","
                                + "\"opacity\"," + (i % 100) / 100.0 + "]}");
                        bridge.dispatch("{\"c\":\"setGameplay\",\"params\":[\"fullbright\","
                                + (i % 2 == 0) + "]}");
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        }, "test-ui-thread");

        // The WS thread: the launcher pushing a loadout the player switched to from the tray.
        Thread ws = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    start.await();
                    for (int i = 0; i < rounds; i++) {
                        state.switchLoadout(i % 2 == 0 ? "bedwars" : "sword-pvp");
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        }, "test-ws-thread");

        ui.start();
        ws.start();
        start.countDown();

        // This thread is the game loop: the reads VoidClient actually makes every frame and tick.
        //
        // A soak test, and honest about it — a data race is not reliably reproducible, so this
        // cannot prove the reads are safe. What it does prove is what a review cannot see by
        // reading: the monitor discipline has no lock-order inversion (LiveState is always taken
        // before a Loadout is touched, never the other way), so three writers and a reader on the
        // same state do not deadlock, and none of the accessors the game loop uses throws.
        for (int i = 0; i < rounds; i++) {
            JsonElement json = state.loadoutJson();
            assertTrue(json.getAsJsonObject().has("mods"));
            state.libraryJson();
            assertNotNull(state.loadoutId());
            boolean unusedKeystrokes = state.keystrokesOn;
            boolean unusedArmor = state.armorShowHeldItem;
            assertTrue(unusedKeystrokes || !unusedKeystrokes);
            assertTrue(unusedArmor || !unusedArmor);
        }

        ui.join(10000);
        ws.join(10000);
        assertFalse(ui.isAlive() || ws.isAlive(), "a writer never finished");
        assertNull(failure.get(), "concurrent access threw: " + failure.get());
        assertTrue(bridge.errors().isEmpty(), "a dispatch failed: " + bridge.errors());
    }
}
