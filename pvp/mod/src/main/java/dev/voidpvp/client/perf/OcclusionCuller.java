package dev.voidpvp.client.perf;

import dev.voidpvp.client.VoidLog;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LightningBoltEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.List;

/**
 * The {@code culling} mod's background thread: asks, a hundred-odd times a second, which
 * entities and block entities no straight line from the camera can reach, and writes the answer
 * onto each one ({@link Cullable}).
 *
 * <h2>Why a thread</h2>
 * <p>The ray test is cheap per box and expensive in bulk — a lobby has hundreds of hologram
 * stands and chests, each up to 27 rays of up to a hundred cells. Done on the render thread it
 * would spend much of what it saves. Done here, the render thread's whole cost is one volatile
 * read per entity ({@link #isOccluded}), and the answer it reads is at most one pass — a few
 * milliseconds — old. That lag is why {@code always_draw_within} exists: inside that radius the
 * answer is never consulted, so nobody stepping round a corner beside you can be a pass late.</p>
 *
 * <h2>Reading the world from another thread</h2>
 * <p>{@code ClientWorld}'s entity list and chunk map are written by the client thread and are not
 * thread-safe. This thread only reads them, indexes the lists rather than iterating (no
 * {@code ConcurrentModificationException}), and treats every exception during a lookup as "can
 * see it" — the answer that draws. The worst a torn read can do is draw one thing for one pass
 * that could have been skipped. This is the same bargain the EntityCulling mod has shipped on for
 * years.</p>
 *
 * <h2>What is never culled</h2>
 * <p>The camera entity, the player, whatever they ride; beacons (the beam is visible far above a
 * hidden base); lightning and the ender dragon (boxes that do not describe what is drawn); and
 * anything the game itself exempts from frustum culling ({@code ignoreCameraFrustum}). Occlusion
 * is skipped entirely outside first person — the rays start at the eye, and in third person the
 * camera is somewhere else — and when the eye is inside an opaque block.</p>
 */
public final class OcclusionCuller implements Runnable {

    private static final OcclusionCuller INSTANCE = new OcclusionCuller();

    /** Shortest gap between passes. Passes are a few ms; this keeps a core from spinning. */
    private static final long MIN_INTERVAL_NANOS = 8_000_000L;
    /** A pass answer older than this many passes is ignored (the entity is drawn). */
    static final int MAX_AGE_PASSES = 3;
    /** No camera update for this long means no world is being drawn: drop the reference. */
    private static final long CAMERA_TIMEOUT_NANOS = 1_000_000_000L;

    private final OpacityCache cache = new OpacityCache();
    private final WorldOpacity worldOpacity = new WorldOpacity();

    private volatile World world;
    private volatile double eyeX;
    private volatile double eyeY;
    private volatile double eyeZ;
    private volatile boolean firstPerson;
    private volatile long cameraStamp;
    private volatile Entity cameraEntity;

    private volatile int pass = 1;
    private volatile long lastPassNanos;
    private volatile int lastTested;
    private volatile int lastOccluded;

    private Thread thread;

    private OcclusionCuller() {
    }

    public static OcclusionCuller get() {
        return INSTANCE;
    }

    /** Starts the thread once. Safe to call repeatedly. */
    public synchronized void start() {
        if (thread != null) {
            return;
        }
        thread = new Thread(this, "void-culling");
        thread.setDaemon(true);
        thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
        thread.start();
    }

    /**
     * Called by the render thread once a frame, at the head of the entity pass, with the camera's
     * interpolated eye position.
     */
    public void updateCamera(World world, Entity camera, double x, double y, double z,
                             boolean firstPerson) {
        this.world = world;
        this.cameraEntity = camera;
        this.eyeX = x;
        this.eyeY = y;
        this.eyeZ = z;
        this.firstPerson = firstPerson;
        this.cameraStamp = System.nanoTime();
    }

    /**
     * The render thread's question: did a recent pass find this hidden? Stale or missing answers
     * say no, so something the thread has not reached yet — a fresh spawn, or every entity while
     * the thread is idle in third person — is simply drawn.
     */
    public boolean isOccluded(Object target) {
        if (!(target instanceof Cullable)) {
            return false;
        }
        Cullable c = (Cullable) target;
        int tested = c.void$cullPass();
        return tested != 0 && pass - tested <= MAX_AGE_PASSES && c.void$isOccluded();
    }

    /** {@code tested occluded passMs} of the last pass, for the debug log and the benchmark. */
    public String stats() {
        return lastTested + " tested, " + lastOccluded + " hidden, pass "
                + String.format(java.util.Locale.ROOT, "%.2f", lastPassNanos / 1e6) + " ms";
    }

    public int lastOccludedCount() {
        return lastOccluded;
    }

    @Override
    public void run() {
        while (true) {
            long started = System.nanoTime();
            try {
                if (!runPass(started)) {
                    sleep(50);
                    continue;
                }
            } catch (Throwable t) {
                // Never let a torn read kill the thread; the next pass starts from scratch.
                VoidLog.warn("culling pass failed, retrying: " + t);
                sleep(100);
                continue;
            }
            long spent = System.nanoTime() - started;
            lastPassNanos = spent;
            long wait = MIN_INTERVAL_NANOS - spent;
            sleep(Math.max(2, wait / 1_000_000L));
        }
    }

    /** One pass. Returns false when there was nothing to do (mod off, no world, third person). */
    private boolean runPass(long now) {
        World w = this.world;
        if (w == null || now - cameraStamp > CAMERA_TIMEOUT_NANOS) {
            this.world = null;
            this.cameraEntity = null;
            return false;
        }
        if (!PerfConfig.culling() || !firstPerson
                || !(PerfConfig.occludeEntities || PerfConfig.occludeBlockEntities)) {
            return false;
        }
        double ex = eyeX;
        double ey = eyeY;
        double ez = eyeZ;
        Entity camera = cameraEntity;
        worldOpacity.world = w;
        worldOpacity.forget();
        cache.reset(worldOpacity);
        if (cache.opaque(VoxelRaycast.floor(ex), VoxelRaycast.floor(ey), VoxelRaycast.floor(ez))) {
            // Spectating through a wall, or suffocating: nothing here is meaningful.
            return false;
        }
        int current = pass + 1;
        int tested = 0;
        int hidden = 0;
        double nearSq = PerfConfig.alwaysDrawWithinSq;

        if (PerfConfig.occludeEntities) {
            List<Entity> entities = w.entities;
            int n = entities.size();
            for (int i = 0; i < n; i++) {
                Entity e;
                try {
                    e = entities.get(i);
                } catch (RuntimeException gone) {
                    break;
                }
                if (e == null || !(e instanceof Cullable) || exempt(e, camera)) {
                    continue;
                }
                Box b = e.getBoundingBox();
                if (b == null) {
                    continue;
                }
                double cx = (b.minX + b.maxX) * 0.5 - ex;
                double cy = (b.minY + b.maxY) * 0.5 - ey;
                double cz = (b.minZ + b.maxZ) * 0.5 - ez;
                boolean occluded = cx * cx + cy * cy + cz * cz > nearSq
                        && !VoxelRaycast.boxVisible(cache, ex, ey, ez,
                        b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
                ((Cullable) e).void$setOccluded(occluded, current);
                tested++;
                if (occluded) {
                    hidden++;
                }
            }
        }
        if (PerfConfig.occludeBlockEntities) {
            List<BlockEntity> bes = w.blockEntities;
            int n = bes.size();
            for (int i = 0; i < n; i++) {
                BlockEntity be;
                try {
                    be = bes.get(i);
                } catch (RuntimeException gone) {
                    break;
                }
                if (be == null || !(be instanceof Cullable) || be instanceof BeaconBlockEntity) {
                    continue;
                }
                BlockPos p = be.getPos();
                if (p == null) {
                    continue;
                }
                double cx = p.getX() + 0.5 - ex;
                double cy = p.getY() + 0.5 - ey;
                double cz = p.getZ() + 0.5 - ez;
                // Half a block of margin on every side: banners hang below their block and stand
                // above it, skulls overhang, and a margin only ever errs towards drawing.
                boolean occluded = cx * cx + cy * cy + cz * cz > nearSq
                        && !VoxelRaycast.boxVisible(cache, ex, ey, ez,
                        p.getX() - 0.5, p.getY() - 0.5, p.getZ() - 0.5,
                        p.getX() + 1.5, p.getY() + 1.5, p.getZ() + 1.5);
                ((Cullable) be).void$setOccluded(occluded, current);
                tested++;
                if (occluded) {
                    hidden++;
                }
            }
        }
        pass = current;
        lastTested = tested;
        lastOccluded = hidden;
        return true;
    }

    /** Entities the ray test must never hide, whatever it would say. */
    static boolean exempt(Entity e, Entity camera) {
        if (e == camera || e.ignoreCameraFrustum) {
            return true;
        }
        if (camera != null && (e == camera.vehicle || e.vehicle == camera)) {
            return true;
        }
        return e instanceof LightningBoltEntity || e instanceof EnderDragonEntity;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** The world, as a {@link BlockOpacity}. Memoises the last chunk; every failure is "clear". */
    static final class WorldOpacity implements BlockOpacity {
        World world;
        private int chunkX = Integer.MIN_VALUE;
        private int chunkZ = Integer.MIN_VALUE;
        private Chunk chunk;

        void forget() {
            chunk = null;
            chunkX = Integer.MIN_VALUE;
            chunkZ = Integer.MIN_VALUE;
        }

        @Override
        public boolean opaque(int x, int y, int z) {
            if (y < 0 || y > 255) {
                return false;
            }
            try {
                int cx = x >> 4;
                int cz = z >> 4;
                Chunk c = chunk;
                if (c == null || cx != chunkX || cz != chunkZ) {
                    c = world.getChunk(cx, cz);
                    chunk = c;
                    chunkX = cx;
                    chunkZ = cz;
                }
                if (c == null || c.isEmpty()) {
                    return false;
                }
                Block b = c.method_9131(x, y, z);
                return b != null && b.isFullBlock();
            } catch (Throwable torn) {
                return false;
            }
        }
    }
}
