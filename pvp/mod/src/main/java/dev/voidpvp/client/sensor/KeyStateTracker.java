package dev.voidpvp.client.sensor;

import com.google.gson.JsonObject;

/**
 * The {@code keys} sensor (§6.6): edge-triggered key state, fed from
 * {@code KeyBinding.setKeyPressed} and pushed only when something changes.
 *
 * <p>In HUD mode Ultralight receives no input at all (§6.3); this is how the
 * player's keys reach the UI, as data. Values are the 0/1 integers of
 * {@code bridge.json#/definitions/key_state}, not booleans, so the UI can
 * index a sprite row.</p>
 *
 * <p>Plain class on purpose: the Mixin that calls it is three lines and cannot
 * be unit-tested, this can.</p>
 */
public final class KeyStateTracker {

    private static final int W = 0;
    private static final int A = 1;
    private static final int S = 2;
    private static final int D = 3;
    private static final int LMB = 4;
    private static final int RMB = 5;
    private static final int SPACE = 6;
    private static final int SHIFT = 7;
    private static final String[] NAMES = {"w", "a", "s", "d", "lmb", "rmb", "space", "shift"};

    /** Key codes of the eight bindings we watch, refreshed from GameOptions. */
    private final int[] codes = new int[8];
    private final boolean[] down = new boolean[8];
    private boolean bound;

    public void setBindings(int forward, int left, int back, int right,
                            int attack, int use, int jump, int sneak) {
        codes[W] = forward;
        codes[A] = left;
        codes[S] = back;
        codes[D] = right;
        codes[LMB] = attack;
        codes[RMB] = use;
        codes[SPACE] = jump;
        codes[SHIFT] = sneak;
        bound = true;
    }

    public boolean hasBindings() {
        return bound;
    }

    /**
     * Records one {@code setKeyPressed}.
     *
     * @return true when this changed a key we report, i.e. when a {@code keys}
     *         event is due
     */
    public boolean update(int keyCode, boolean pressed) {
        boolean changed = false;
        for (int i = 0; i < codes.length; i++) {
            if (codes[i] == keyCode && down[i] != pressed) {
                down[i] = pressed;
                changed = true;
            }
        }
        return changed;
    }

    /** Clears every key, for {@code KeyBinding.releaseAllKeys} and screen changes. */
    public boolean releaseAll() {
        boolean changed = false;
        for (int i = 0; i < down.length; i++) {
            if (down[i]) {
                down[i] = false;
                changed = true;
            }
        }
        return changed;
    }

    public boolean isDown(String name) {
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equals(name)) {
                return down[i];
            }
        }
        return false;
    }

    /** {@code bridge.json#/definitions/keys_payload}. */
    public JsonObject payload() {
        JsonObject o = new JsonObject();
        for (int i = 0; i < NAMES.length; i++) {
            o.addProperty(NAMES[i], Integer.valueOf(down[i] ? 1 : 0));
        }
        return o;
    }
}
