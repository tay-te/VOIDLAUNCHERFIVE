package dev.voidpvp.client.perf;

import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.platform.GlStateManager;
import dev.voidpvp.client.mixin.EntityRenderDispatcherAccessor;
import dev.voidpvp.client.mixin.EntityRendererInvoker;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.world.World;

/**
 * The render-thread half of the {@code culling} mod — what the Mixins call, kept out of
 * {@code mixin/} so the decisions read as plain code.
 *
 * <p>Every method here is a filter on something vanilla had already decided to draw: it can turn a
 * "draw" into a "skip", never the reverse. The pure part of each decision is {@link #verdict},
 * which the tests drive directly.</p>
 */
public final class CullingHooks {

    /** Draw it. */
    public static final int DRAW = 0;
    /** No ray reaches it; vanilla's depth test was hiding it anyway. */
    public static final int OCCLUDED = 1;
    /** Past a distance cut-off. */
    public static final int TOO_FAR = 2;

    /** The partial tick of the frame being drawn, captured at the head of the entity pass. */
    private static volatile float tickDelta;

    private CullingHooks() {
    }

    /**
     * Pure: what to do with one entity or block entity.
     *
     * @param distSq    squared distance from the camera eye
     * @param limitSq   the distance cut-off that applies to it, or {@code +inf}
     * @param occlude   whether occlusion culling applies to this kind of thing right now
     * @param occluded  the culling thread's (fresh) answer
     */
    public static int verdict(double distSq, double limitSq, boolean occlude, boolean occluded) {
        if (distSq > limitSq) {
            return TOO_FAR;
        }
        if (occlude && occluded && distSq > PerfConfig.alwaysDrawWithinSq) {
            return OCCLUDED;
        }
        return DRAW;
    }

    /**
     * Called at the head of {@code WorldRenderer.renderEntities}: hands the culling thread this
     * frame's eye position and remembers the partial tick for {@link #renderLabelOnly}.
     */
    public static void beginEntityPass(World world, Entity camera, float delta) {
        tickDelta = delta;
        if (!PerfConfig.culling() || camera == null) {
            return;
        }
        OcclusionCuller culler = OcclusionCuller.get();
        culler.start();
        double x = camera.prevTickX + (camera.x - camera.prevTickX) * delta;
        double y = camera.prevTickY + (camera.y - camera.prevTickY) * delta + camera.getEyeHeight();
        double z = camera.prevTickZ + (camera.z - camera.prevTickZ) * delta;
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean firstPerson = mc.options != null && mc.options.perspective == 0;
        culler.updateCamera(world, camera, x, y, z, firstPerson);
    }

    /** The verdict for an entity vanilla is about to draw. */
    public static int entityVerdict(Entity e) {
        if (!PerfConfig.culling() || e == null) {
            return DRAW;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        Entity camera = mc.getCameraEntity();
        if (camera == null || OcclusionCuller.exempt(e, camera) || e == mc.player) {
            return DRAW;
        }
        double dx = e.x - camera.x;
        double dy = e.y - camera.y;
        double dz = e.z - camera.z;
        double distSq = dx * dx + dy * dy + dz * dz;
        double limit = e instanceof ArmorStandEntity
                ? PerfConfig.armorStandDistanceSq : Double.POSITIVE_INFINITY;
        boolean occlude = PerfConfig.occludeEntities;
        return verdict(distSq, limit, occlude, occlude && OcclusionCuller.get().isOccluded(e));
    }

    /** The verdict for a block entity, given its squared distance from the camera. */
    public static int blockEntityVerdict(BlockEntity be, double distSq) {
        if (!PerfConfig.culling() || be == null) {
            return DRAW;
        }
        boolean occlude = PerfConfig.occludeBlockEntities && !(be instanceof BeaconBlockEntity);
        return verdict(distSq, PerfConfig.blockEntityDistanceSq, occlude,
                occlude && OcclusionCuller.get().isOccluded(be));
    }

    /**
     * Draws only the nametag of an entity the culling hid, so a player behind a wall keeps the
     * name vanilla would have shown through it. Goes through the entity renderer's own label
     * method, so every rule vanilla applies — sneaking, teams, invisibility, distance — still
     * decides whether anything is drawn.
     */
    @SuppressWarnings("unchecked")
    public static void renderLabelOnly(EntityRenderDispatcher dispatcher, Entity e) {
        if (!PerfConfig.nametagsThroughWalls) {
            return;
        }
        EntityRenderer<Entity> renderer = dispatcher.getRenderer(e);
        if (renderer == null) {
            return;
        }
        float delta = tickDelta;
        EntityRenderDispatcherAccessor camera = (EntityRenderDispatcherAccessor) dispatcher;
        double x = e.prevTickX + (e.x - e.prevTickX) * delta - camera.void$cameraX();
        double y = e.prevTickY + (e.y - e.prevTickY) * delta - camera.void$cameraY();
        double z = e.prevTickZ + (e.z - e.prevTickZ) * delta - camera.void$cameraZ();
        // The light vanilla's renderEntity would have set before the entity's own render.
        int light = e.getLightmapCoordinates(delta);
        GLX.gl13MultiTexCoord2f(GLX.lightmapTextureUnit, light % 65536, light / 65536);
        GlStateManager.color(1f, 1f, 1f, 1f);
        ((EntityRendererInvoker) renderer).void$renderName(e, x, y, z);
    }
}
