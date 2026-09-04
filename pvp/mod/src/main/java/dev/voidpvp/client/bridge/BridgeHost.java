package dev.voidpvp.client.bridge;

/**
 * The parts of a bridge call that need the game rather than just state: the
 * two calls in {@code bridge.json} that do something to the client itself.
 */
public interface BridgeHost {

    /** {@code void.closeMenu()} — closes VoidMenuScreen and re-grabs the mouse. */
    void closeMenu();

    /**
     * {@code void.openKeybindCapture(modId)} — takes over key input until the
     * next press. Java answers the call immediately with {@code returns: null},
     * which means <em>armed</em>, not <em>cancelled</em>; the captured key
     * reaches JS later as a call-result envelope on the push channel
     * ({@link VoidBridge#keybindScript}), and that is what resolves the Promise
     * the shim handed the caller.
     */
    void beginKeybindCapture(String modId);
}
