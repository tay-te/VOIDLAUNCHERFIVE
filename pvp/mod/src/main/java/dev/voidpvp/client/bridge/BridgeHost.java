package dev.voidpvp.client.bridge;

/**
 * The parts of a bridge call that need the game rather than just state: the
 * three calls in {@code bridge.json} that do something to the client itself.
 *
 * <p><b>Which thread calls what is part of this interface, not an implementation
 * detail.</b> Ultralight has its own UI thread, so a bridge call arrives there;
 * {@link VoidBridge} decides per call whether the host is invoked inline or from
 * the game-thread drain, and the javadoc below says which. An implementation
 * that touches {@code MinecraftClient} from a method marked "UI thread" is a
 * crash, not a race — see the classification table in {@link VoidBridge}.</p>
 */
public interface BridgeHost {

    /**
     * {@code void.closeMenu()} — closes VoidMenuScreen and re-grabs the mouse.
     *
     * <p><b>Game thread.</b> {@link VoidBridge} queues this through
     * {@link VoidBridge#post} and it runs at the next drain, so the
     * implementation may touch screens and GL freely. It is also called
     * directly by the game thread's own hotkey poll and by the menu's Escape
     * handler, which is why the queueing lives in the bridge rather than
     * here — otherwise those paths would take a needless frame of delay.</p>
     */
    void closeMenu();

    /**
     * {@code void.openKeybindCapture(modId)} — takes over key input until the
     * next press. Java answers the call immediately with {@code returns: null},
     * which means <em>armed</em>, not <em>cancelled</em>; the captured key
     * reaches JS later as a call-result envelope on the push channel
     * ({@link VoidBridge#emitCallResult}), and that is what resolves the Promise
     * the shim handed the caller.
     *
     * <p><b>UI thread, inline.</b> Arming has to take effect before the player's
     * next key press, which is sooner than the next drain, so this one is not
     * queued: the implementation may only set flags the game thread reads, and
     * those flags must be {@code volatile}.</p>
     */
    void beginKeybindCapture(String modId);

    /**
     * The page reported where the surfaces that carry a shadow are, so the host can draw those
     * shadows in GL rather than making the CPU rasteriser blur them. Replaces the previous set.
     *
     * <p><b>UI thread, inline.</b> The geometry has to be visible to the very next frame or the
     * shadow trails the panel it belongs to during a resize, so the implementation publishes the
     * list — through a {@code volatile} field — and draws nothing here. {@link
     * dev.voidpvp.client.render.EffectSurface} is immutable, so publishing the list is enough to
     * hand the whole set over safely.</p>
     *
     * @param surfaces every surface to draw, in CSS pixels of the view
     */
    void setSurfaces(java.util.List<dev.voidpvp.client.render.EffectSurface> surfaces);
}
