package dev.voidpvp.client.perf;

/**
 * The culling thread's answer, stored on the thing it is about.
 *
 * <p>Implemented by {@code mixin/EntityCullableMixin} on every {@code Entity} and by
 * {@code mixin/BlockEntityCullableMixin} on every {@code BlockEntity}, which is how
 * EntityCulling does it too: a field on the object costs nothing to look up from the render pass,
 * where a map keyed by entity would cost a hash per entity per frame on the thread we are trying
 * to unload. Both fields are written by the culling thread and read by the render thread, so the
 * implementations declare them {@code volatile}.</p>
 */
public interface Cullable {

    /** Whether the last pass found no clear line from the camera. */
    boolean void$isOccluded();

    /** The pass number that answer came from; 0 means never tested. */
    int void$cullPass();

    /** Records one pass's answer. */
    void void$setOccluded(boolean occluded, int pass);
}
