package dev.voidpvp.client.cosmetic;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.voidpvp.client.state.LiveState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The cosmetics feature layer, added to both player renderers (wide and slim arms). It runs after
 * the model is posed for this player and inside the player's own model transform, so every item
 * is lit, fogged and depth-tested like the skin it is worn on.
 *
 * <h2>What it respects</h2>
 * Invisible players draw nothing (vanilla's own cape rule). Spectators never reach a feature
 * layer. Sleeping players keep their head items and lose cape, back and trail. Sneaking folds
 * wings and tilts the cape with the torso, because the cape collides with the posed torso. The
 * {@code cosmetics} mod's switches: off draws nothing; {@code others} off draws only your own.
 * The inventory preview of yourself draws a resting cape and never advances the simulation, so the
 * world's cloth is not dragged round by the preview's fake body yaw.
 *
 * <h2>How it is lit</h2>
 * Every pass runs through {@link CosmeticShader}: the game's own entity lighting and lightmap,
 * contact occlusion against the torso or head, a rim of sky light and a material per part. An
 * outfit with an emissive part also writes the {@link Bloom} mask while it draws. The inventory
 * preview, a sleeping or dying player, and a context without GLSL draw the fixed-function way.
 *
 * <h2>What it costs</h2>
 * Display lists for models, one tessellated sheet per cape, a fixed particle pool. Distance LOD
 * (scaled by {@link Budget#quality}): within {@link #NEAR} blocks the cape is simulated at one
 * quad per texel and glows; to {@link #MID} at a quarter of that; to {@link #FAR} the cape holds its
 * last shape and only models draw; beyond, nothing but the badge. {@link Budget} measures this
 * layer's time per frame and shrinks the bands when twenty players start to cost more than the
 * half millisecond the layer is allowed.
 */
public final class CosmeticRenderer implements FeatureRenderer<AbstractClientPlayerEntity> {

    static final double NEAR = 10, MID = 28, FAR = 56;
    static final long FORGET_NANOS = 5_000_000_000L;
    private static final int SLOT_BACK = 0, SLOT_HEAD = 1, SLOT_FACE = 2, SLOT_HALO = 3;

    /** Set by the inventory-screen mixin while it draws the player preview. */
    public static volatile boolean inGuiPreview;

    private static final Map<Integer, PlayerFx> FX = new HashMap<Integer, PlayerFx>();
    private static final long EPOCH = System.nanoTime();
    /** Entity ids of the other players currently holding a high-resolution cape. */
    private static final java.util.Set<Integer> HI_HOLDERS = new java.util.HashSet<Integer>();

    private final PlayerEntityRenderer owner;
    private final CapeRenderer cape = new CapeRenderer();
    private final GlowBatch glow = new GlowBatch();
    private final float[] modelview = new float[16];
    private final float[] itemMv = new float[16];
    private final float[] eye = new float[3];
    private final float[] scratch = new float[16];
    private final float[] root = new float[3];
    private final double[] d3 = new double[3];
    private float emissive = 1f;
    /** This player's passes run through {@link CosmeticShader} / write the bloom mask. */
    private boolean shaded, mrt;
    /** This player's bloom gain (0 without the mask); each slot multiplies its reactive intensity in. */
    private float playerBloom;
    /**
     * Reaction slots, in the motion stream's order (its {@code Reaction} constants): the cape, then
     * the renderer's model slots back, head, face, halo.
     */
    private static final int REACT_CAPE = 0;
    private static final int[] REACT_SLOT = {1, 2, 3, 4};
    /** The bloom pass does the glow: anchors only feed the light pool, never get placed. */
    private boolean bloomGlow;
    private final float[] partMv = new float[16];
    private final float[] eyeToBody = new float[16];
    private final float[] eyeToHead = new float[16];
    private static int matrixCheck;
    /** -PvoidGlDebug: the worst difference seen between the rebuilt and GL's modelview. */
    static float matrixError = -1;
    /** Distance to the player being drawn, for the model passes' LOD. */
    private double modelDist;

    public CosmeticRenderer(PlayerEntityRenderer owner) {
        this.owner = owner;
    }

    @Override
    public boolean combineTextures() {
        return false;
    }

    @Override
    public void render(AbstractClientPlayerEntity p, float limbAngle, float limbDistance, float tickDelta,
                       float age, float headYaw, float headPitch, float scale) {
        long t0 = System.nanoTime();
        try {
            draw(p, tickDelta);
        } finally {
            Budget.add(System.nanoTime() - t0);
        }
    }

    private void draw(AbstractClientPlayerEntity p, float td) {
        LiveState live = LiveState.get();
        if (!live.cosmeticsOn || p.isInvisible()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        long now = System.nanoTime();
        PlayerFx fx = fx(p, now);
        String uuid = fx.uuid;
        CosmeticState state = CosmeticState.get();
        EquippedSet set = state.of(uuid);
        if (set == null || set.isEmpty()) {
            return;
        }
        boolean self = p == mc.player;
        if (!self && !live.cosmeticsOthers) {
            return;
        }
        Entity cam = mc.getCameraEntity();
        double dist = cam == null ? 0 : Math.sqrt(p.squaredDistanceTo(cam));
        float q = Budget.quality();
        modelDist = dist;
        if (dist > FAR * q * 1.15 && !self) {
            return;
        }
        float dt = fx.lastNanos == 0 ? 0 : Math.min(0.1f, (now - fx.lastNanos) / 1e9f);
        boolean gui = inGuiPreview && self;
        if (!gui) {
            fx.lastNanos = now;
        }
        float time = (now - EPOCH) / 1e9f + fx.phase;
        PlayerEntityModel model = owner.getModel();
        boolean sleeping = p.isSleeping();
        boolean glowOn = live.cosmeticsGlow && dist < MID * q && !gui;
        int light = p.getLightmapCoordinates(td);
        float glowK = glowOn ? glowStrength(p, td) : 0;
        // Emissive texels: full strength in the dark, about half against daylight, where the lit
        // albedo underneath already carries the colour and an additive layer would wash to white.
        emissive = 0.42f + 0.58f * (1 - Math.max(0, Math.min(1, p.getBrightnessAtEyes(td))));

        motion(fx, p, td, dt, gui);

        // Light and material (CosmeticShader) everywhere but the inventory preview, whose
        // matrices are the screen's; the bloom mask (Bloom) only for an outfit that glows.
        Bloom.level(live.cosmeticsGlowLevel);
        // A sleeping or dying player is turned in ways the rebuilt modelview does not follow.
        shaded = !gui && !sleeping && p.deathTime == 0 && CosmeticShader.ready();
        boolean bloom = shaded && Bloom.usable();
        bloomGlow = bloom;
        if (glowOn || shaded) {
            playerModelview(mc, p, fx.uuid, td);
        }
        glow.clear();
        GlStateManager.color(1, 1, 1, 1);
        // The living-entity pass disables culling, but the held-item layer before this one can
        // switch it back on — and a mirrored wing has the opposite winding to its twin, so with
        // culling on one of the pair vanishes depending on which side it is seen from.
        GlStateManager.disableCull();

        if (shaded) {
            if (dev.voidpvp.client.render.GlDebug.ON && (++matrixCheck % 240) == 1) {
                checkModelview();
            }
            partInverse(model.body, eyeToBody);
            partInverse(model.head, eyeToHead);
            // The mask first: setting it up the first time binds the bloom's own programs.
            mrt = bloom && glowOn && glows(set, sleeping) && Bloom.beginPlayer(mc);
            CosmeticShader.begin();
            CosmeticShader.parts(eyeToBody, eyeToHead);
            CosmeticShader.rim(0.4f);
            playerBloom = mrt ? bloomStrength(p, td, live.cosmeticsGlowLevel) : 0;
        }
        try {
            long tc = System.nanoTime();
            String capeId = set.get("cape");
            if (capeId != null && !sleeping) {
                drawCape(p, uuid, fx, capeId, set.tint("cape"), td, dt, dist, q, gui, model, time, glowK, light);
            }
            long tm = System.nanoTime();
            Budget.split(0, tm - tc);
            String backId = set.get("back");
            if (backId != null && !sleeping) {
                drawModel(fx, SLOT_BACK, backId, set.tint("back"), model.body, null, model, p, time, dt, glowK, light, false);
            }
            String headId = set.get("head");
            if (headId != null) {
                drawModel(fx, SLOT_HEAD, headId, set.tint("head"), model.head, null, model, p, time, dt, glowK, light, helmet(p));
            }
            String faceId = set.get("face");
            if (faceId != null) {
                drawModel(fx, SLOT_FACE, faceId, set.tint("face"), model.head, null, model, p, time, dt, glowK, light, helmet(p));
            }
            String haloId = set.get("halo");
            if (haloId != null) {
                drawModel(fx, SLOT_HALO, haloId, set.tint("halo"), model.head, "halo", model, p, time, dt, glowK, light, helmet(p));
            }
            Budget.split(1, System.nanoTime() - tm);
        } finally {
            if (shaded) {
                Bloom.endPlayer();
                CosmeticShader.end();
                mrt = false;
            }
        }
        long tg = System.nanoTime();
        if (glow.poolStrength > 0 && p.onGround && glowK > 0.6f) {
            EmotePose.root(uuid, root);
            pool(glow.poolRgb, Math.min(0.38f, glow.poolStrength * 0.45f * (glowK - 0.45f)), root[1]);
        }
        // With the bloom pass the anchors only size the light pool; the sprites are its fallback.
        glow.flush(!bloom);
        Budget.split(2, System.nanoTime() - tg);
        Gl.lightmap(light);
        GlStateManager.color(1, 1, 1, 1);

        String trailId = set.get("trail");
        if (trailId != null && !sleeping && !gui && !p.hasVehicle() && dist < MID * q) {
            double x = p.prevX + (p.x - p.prevX) * td;
            double y = p.prevY + (p.y - p.prevY) * td;
            double z = p.prevZ + (p.z - p.prevZ) * td;
            Trails.emit(fx, trailId, p, x, y, z, dt, p.onGround);
        }
    }

    /**
     * The modelview this player's features are drawn with, rebuilt in Java from the camera's view
     * matrix and the living-entity renderer's own transform (translate to the entity, turn to its
     * body yaw, the emote root, flip, the player scale, the model lift), so placing glow costs no
     * GL read-back. Not exact for a dying player's tilt, which has no glow worth placing.
     */
    private void playerModelview(MinecraftClient mc, AbstractClientPlayerEntity p, String uuid, float td) {
        Gl.view(modelview);
        Entity cam = mc.getCameraEntity();
        double cx = cam.prevTickX + (cam.x - cam.prevTickX) * td;
        double cy = cam.prevTickY + (cam.y - cam.prevTickY) * td;
        double cz = cam.prevTickZ + (cam.z - cam.prevTickZ) * td;
        double x = p.prevX + (p.x - p.prevX) * td - cx;
        double y = p.prevY + (p.y - p.prevY) * td - cy - (p.isSneaking() ? 0.125 : 0);
        double z = p.prevZ + (p.z - p.prevZ) * td - cz;
        float bodyYaw = p.prevBodyYaw + wrap(p.bodyYaw - p.prevBodyYaw) * td;
        Mat4.translate(modelview, (float) x, (float) y, (float) z);
        Mat4.rotate(modelview, (float) Math.toRadians(180f - bodyYaw), 0, 1, 0);
        if (EmotePose.root(uuid, root)) {
            float k = 0.0625f * 0.9375f;
            Mat4.translate(modelview, -root[0] * k, -root[1] * k, root[2] * k);
        }
        Mat4.scale(modelview, -0.9375f, -0.9375f, 0.9375f);
        Mat4.translate(modelview, 0, -1.5078125f, 0);
    }

    /** Emits the local player's trail when they are not drawn (first person). Render thread. */
    public static void emitUnrendered(MinecraftClient mc, float td) {
        AbstractClientPlayerEntity p = mc.player;
        LiveState live = LiveState.get();
        if (p == null || !live.cosmeticsOn || !live.cosmeticsOwnFirstPerson || mc.options.perspective != 0
                || p.isInvisible() || p.isSpectator() || p.hasVehicle()) {
            return;
        }
        long now = System.nanoTime();
        PlayerFx fx = fx(p, now);
        EquippedSet set = CosmeticState.get().of(fx.uuid);
        String trail = set == null ? null : set.get("trail");
        if (trail == null) {
            return;
        }
        float dt = fx.lastNanos == 0 ? 0 : Math.min(0.1f, (now - fx.lastNanos) / 1e9f);
        fx.lastNanos = now;
        Trails.emit(fx, trail, p, p.prevX + (p.x - p.prevX) * td, p.prevY + (p.y - p.prevY) * td,
                p.prevZ + (p.z - p.prevZ) * td, dt, p.onGround);
    }

    private static PlayerFx fx(Entity p, long now) {
        Integer key = Integer.valueOf(p.getEntityId());
        PlayerFx fx = FX.get(key);
        if (fx == null || !fx.uuidOf.equals(p.getUuid())) {
            // The uuid string is built once per player, never per frame.
            fx = new PlayerFx(p.getEntityId(), p.getUuid());
            FX.put(key, fx);
        }
        fx.seenNanos = now;
        return fx;
    }

    /** Drops state for players not drawn lately. Once per tick. */
    public static void sweep() {
        long now = System.nanoTime();
        for (Iterator<PlayerFx> it = FX.values().iterator(); it.hasNext(); ) {
            PlayerFx f = it.next();
            if (now - f.seenNanos > FORGET_NANOS) {
                CapeRenderer.release(f.cloth);
                HI_HOLDERS.remove(Integer.valueOf(f.entityId));
                it.remove();
            }
        }
    }

    public static void clear() {
        for (PlayerFx f : FX.values()) {
            CapeRenderer.release(f.cloth);
        }
        FX.clear();
        HI_HOLDERS.clear();
    }

    private static boolean helmet(AbstractClientPlayerEntity p) {
        return p.getArmorSlot(3) != null;
    }

    /**
     * Glow reads strongest in the dark and stays faint by day, the way light does: about a third
     * of its strength in full daylight, up to twice it on a moonless night.
     */
    private static float glowStrength(Entity p, float td) {
        float dark = 1 - Math.max(0, Math.min(1, p.getBrightnessAtEyes(td)));
        return 0.3f + 1.7f * (float) Math.pow(dark, 1.3);
    }

    /**
     * The reactive emissive multiplier for one slot of this player: 1 at rest. The fight-reaction
     * stream (pvp-cos-motion's {@code Reactive}) supplies the real value — below 1 at low health,
     * up to 2.5 through combos, hits and kills — and replaces this body when the branches meet.
     */
    private static float slotIntensity(PlayerFx fx, int slot) {
        return 1f;
    }

    /** Albedo shade for low health (the same stream's {@code Reaction.dim}): 1 at rest. */
    private static float albedoShade(PlayerFx fx) {
        return 1f;
    }

    /**
     * The bloom's gain for this player, written into the mask: subtle in daylight (under a tenth),
     * full on a dark night; {@code low} is a little over half of {@code high}.
     */
    private static float bloomStrength(Entity p, float td, int level) {
        float dark = 1 - Math.max(0, Math.min(1, p.getBrightnessAtEyes(td)));
        return (0.08f + 0.92f * (float) Math.pow(dark, 1.3)) * (level >= 2 ? 1f : 0.6f);
    }

    /** Whether anything this player wears and is drawn now has an emissive part. */
    private static boolean glows(EquippedSet set, boolean sleeping) {
        for (int i = 0; i < GLOW_SLOTS.length; i++) {
            String id = set.get(GLOW_SLOTS[i]);
            if (id != null && (!sleeping || i >= 2) && CosmeticAssets.bundle(id).glows) {
                return true;
            }
        }
        return false;
    }

    private static final String[] GLOW_SLOTS = {"cape", "back", "head", "face", "halo"};

    /**
     * Eye space to {@code part}'s own pixel space: the inverse of the player's modelview followed
     * by the part's {@code ModelPart.preRender} and the 1/16 pixel scale. The shader's contact
     * occlusion measures each vertex against the torso or head box in that space.
     */
    private void partInverse(ModelPart part, float[] out) {
        Mat4.copy(modelview, partMv);
        Mat4.translate(partMv, part.pivotX * 0.0625f, part.pivotY * 0.0625f, part.pivotZ * 0.0625f);
        Mat4.rotate(partMv, part.posZ, 0, 0, 1);
        Mat4.rotate(partMv, part.posY, 0, 1, 0);
        Mat4.rotate(partMv, part.posX, 1, 0, 0);
        Mat4.scale(partMv, 0.0625f, 0.0625f, 0.0625f);
        if (!Mat4.invertAffine(partMv, out)) {
            Mat4.identity(out);
        }
    }

    /** -PvoidGlDebug: how far the rebuilt player modelview is from GL's (one read-back, rarely). */
    private void checkModelview() {
        java.nio.FloatBuffer b = org.lwjgl.BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, b);
        float worst = 0;
        for (int i = 0; i < 16; i++) {
            worst = Math.max(worst, Math.abs(b.get(i) - modelview[i]));
        }
        matrixError = Math.max(matrixError, worst);
    }

    // ------------------------------------------------------------------------- motion

    /** Smoothed speed and vertical velocity, which wings and floating items react to. */
    private static void motion(PlayerFx fx, Entity p, float td, float dt, boolean gui) {
        if (gui || dt <= 0) {
            return;
        }
        double x = p.prevX + (p.x - p.prevX) * td;
        double y = p.prevY + (p.y - p.prevY) * td;
        double z = p.prevZ + (p.z - p.prevZ) * td;
        if (fx.hasLast) {
            double dx = x - fx.lastX, dy = y - fx.lastY, dz = z - fx.lastZ;
            double h = Math.sqrt(dx * dx + dz * dz) / dt;
            if (h < 50) {
                fx.speed.step((float) h, dt);
                fx.vy.step((float) (dy / dt), dt);
            }
        }
        fx.lastX = x;
        fx.lastY = y;
        fx.lastZ = z;
        fx.hasLast = true;
    }

    // --------------------------------------------------------------------------- cape

    private void drawCape(AbstractClientPlayerEntity p, String uuid, PlayerFx fx, String id, int tint, float td,
                          float dt, double dist, float q, boolean gui, PlayerEntityModel model,
                          float time, float glowK, int light) {
        CosmeticAssets.Bundle bundle = CosmeticAssets.bundle(id);
        CapeStyle style = bundle.cape;
        CosmeticAssets.Tex tex = bundle.albedo();
        if (tex == null) {
            return;
        }
        // Resolution, with hysteresis: a cape keeps its grid until it is clearly out of range, and
        // at most two other players hold a high-resolution sheet at once. Switching rebuilds the
        // cloth, so it must only happen on a real change, never flicker frame to frame.
        boolean self = p == MinecraftClient.getInstance().player;
        boolean wantHi;
        Integer key = Integer.valueOf(fx.entityId);
        if (self) {
            wantHi = true;
        } else if (fx.clothHi) {
            wantHi = dist < NEAR * 1.4 * q;
            if (!wantHi) {
                HI_HOLDERS.remove(key);
            }
        } else {
            wantHi = dist < NEAR * q && HI_HOLDERS.size() < 2;
            if (wantHi) {
                HI_HOLDERS.add(key);
            }
        }
        if (fx.cloth == null || fx.clothHi != wantHi || !id.equals(fx.clothFor)) {
            CapeRenderer.release(fx.cloth);
            fx.cloth = CapeRenderer.newCloth(wantHi, style);
            fx.clothHi = wantHi;
            fx.clothFor = id;
        }
        Cloth cloth = fx.cloth;

        // The frame this player is drawn in, and the parts the cloth collides with.
        double x = p.prevX + (p.x - p.prevX) * td;
        double y = p.prevY + (p.y - p.prevY) * td - (p.isSneaking() ? 0.125 : 0);
        double z = p.prevZ + (p.z - p.prevZ) * td;
        float bodyYaw = p.prevBodyYaw + wrap(p.bodyYaw - p.prevBodyYaw) * td;
        EmotePose.root(uuid, root);
        long q0 = System.nanoTime();
        cape.frame.set(x, y, z, bodyYaw, root[0], root[1], root[2]);
        part(cape.body, model.body);
        part(cape.rightLeg, model.rightLeg);
        part(cape.leftLeg, model.leftLeg);
        cape.armour = p.getArmorSlot(2) != null ? 1.1 : 0;

        if (gui) {
            if (!cloth.placed()) {
                return;
            }
        }
        boolean stepped = false;
        if (!gui && (dist < FAR * q || !cloth.placed())) {
            cape.anchors(cloth, fx.ax, fx.ay, fx.az);
            cape.back(d3);
            boolean simulate = dist < MID * q || !cloth.placed();
            if (simulate) {
                cloth.iterations = fx.clothHi ? 4 : 2;
                // A breath of wind, so a cape at rest is never perfectly dead.
                // An idle breeze with slow gusts, so a cape at rest sways and never hangs dead.
                double gust = Math.max(0, Math.sin(time * 0.31)) * Math.max(0, Math.sin(time * 1.13));
                cloth.windX = Math.sin(time * 0.7) * 0.9 + Math.sin(time * 1.9) * 0.35 + gust * 1.2;
                cloth.windZ = Math.cos(time * 0.5) * 0.8 + gust * 1.6;
                cloth.windY = 0;
                // Low-resolution capes step at 60 Hz (Cloth.step) and only on alternate frames;
                // the time carries over, and the mesh is reused between steps.
                fx.clothCarry += dt;
                if (fx.clothHi || ((++fx.clothFrame) & 1) == 0) {
                    long c0 = System.nanoTime();
                    cloth.advance(fx.clothCarry, fx.ax, fx.ay, fx.az, cape.collider, d3[0], d3[2]);
                    Budget.cloth(System.nanoTime() - c0);
                    fx.clothCarry = 0;
                    stepped = true;
                }
            }
        }
        long q1 = System.nanoTime();
        // Re-mesh only when the sheet moved. On a frame a low-resolution cape skips its step, or
        // while a distant one is frozen, the last mesh is drawn again: it is in the body's own
        // space, so it rides along rigidly instead of lagging behind for a frame.
        boolean glowWanted = glowK > 0 && style != null && style.glow.length > 0;
        if (glowWanted && bloomGlow) {
            for (float[] g : style.glow) {
                glow.note((int) g[2], g[4] * glowK);
            }
            glowWanted = false;
        }
        if (stepped || cloth.vbo < 0 || gui || fx.clothHi) {
            cape.prepare(cloth);
            cape.mesh(cloth);
            cape.upload(cloth);
            if (glowWanted) {
                cape.storeGlow(cloth, style.glow);
            }
        }
        long q2 = System.nanoTime();
        Budget.split(3, q1 - q0);
        Budget.split(4, q2 - q1);

        GlStateManager.pushMatrix();
        GlStateManager.scale(0.0625f, 0.0625f, 0.0625f);
        int frame = tex.frame(time);
        if (shaded) {
            // One pass: albedo, tint and emissive layers together through the material shader —
            // cloth (or the cape's material), per-texel materials from the mask, contact occlusion
            // against the torso (the lining darkens where it lies on the back), fold shading.
            CosmeticAssets.Tex tm = bundle.tint();
            CosmeticAssets.Tex em = bundle.emissive();
            // Fight reactions: the emissive layer and its bloom scale by the slot's intensity (one
            // pass: the shader adds above 1 without a second draw), the albedo dims at low health.
            float ik = slotIntensity(fx, REACT_CAPE);
            float shade = albedoShade(fx);
            CosmeticShader.tint(shade, shade, shade, 1);
            CosmeticShader.bloom(playerBloom * ik);
            layersOn(tm, em, tint, id, emissive * ik);
            GlStateManager.bindTexture(tex.glId);
            Gl.selectFrame(frame, tex.frames);
            CosmeticShader.lit(false, style == null ? CosmeticShader.CLOTH : style.material, bundle.materialMask);
            CosmeticShader.occluder(CosmeticShader.TORSO);
            cape.draw(cloth, false, true);
            Gl.resetFrame(tex.frames);
            if (em != null) {
                Bloom.drew();
            }
        } else {
            // Albedo, lit; fold shading where it can be seen: the high-resolution sheets close to
            // the camera.
            GlStateManager.bindTexture(tex.glId);
            Gl.selectFrame(frame, tex.frames);
            cape.draw(cloth, false, fx.clothHi);
            Gl.resetFrame(tex.frames);
            // Tint mask.
            CosmeticAssets.Tex tm = bundle.tint();
            if (tm != null) {
                int rgb = tint >= 0 ? tint : defaultTint(id);
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GlStateManager.depthFunc(GL11.GL_LEQUAL);
                GlStateManager.bindTexture(tm.glId);
                GlStateManager.color(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1);
                cape.draw(cloth, false, false);
                GlStateManager.color(1, 1, 1, 1);
                GlStateManager.disableBlend();
            }
            // Emissive, full-bright and additive.
            CosmeticAssets.Tex em = bundle.emissive();
            if (em != null) {
                float e = emissive * slotIntensity(fx, REACT_CAPE);
                emissiveBegin(Math.min(1f, e));
                GlStateManager.bindTexture(em.glId);
                Gl.selectFrame(frame, em.frames);
                cape.draw(cloth, true, false);
                if (e > 1.02f) {
                    // Brighter than one additive pass can add: the remainder, once more.
                    GlStateManager.color(e - 1f, e - 1f, e - 1f, 1);
                    cape.draw(cloth, true, false);
                }
                Gl.resetFrame(em.frames);
                emissiveEnd(light);
            }
        }
        // Glow anchors on the outer face.
        if (glowWanted && cloth.glowAt != null && cloth.glowAt.length == style.glow.length * 3) {
            Mat4.copy(modelview, scratch);
            Mat4.scale(scratch, 0.0625f, 0.0625f, 0.0625f);
            for (int k = 0; k < style.glow.length; k++) {
                float[] g = style.glow[k];
                Mat4.transform(scratch, cloth.glowAt[k * 3], cloth.glowAt[k * 3 + 1], cloth.glowAt[k * 3 + 2], eye);
                glow.add(eye[0], eye[1], eye[2], g[3] * Mat4.scaleOf(scratch), (int) g[2], g[4] * glowK);
            }
        }
        Budget.split(5, System.nanoTime() - q2);
        GlStateManager.popMatrix();
    }

    private static void part(PartFrame f, ModelPart m) {
        f.set(m.pivotX, m.pivotY, m.pivotZ, m.posX, m.posY, m.posZ);
    }

    private static float wrap(float deg) {
        deg %= 360f;
        if (deg >= 180f) {
            deg -= 360f;
        }
        if (deg < -180f) {
            deg += 360f;
        }
        return deg;
    }

    /**
     * A faint pool of the item's light on the ground under the player: an additive soft disc in
     * model space at the feet, drawn only at night and only when standing on something. It is
     * what makes a glowing item look like it gives off light rather than being painted bright.
     */
    private static void pool(int rgb, float alpha, float rootY) {
        if (alpha <= 0.01f) {
            return;
        }
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float y = (23.9f - rootY) * 0.0625f, s = 1.25f;
        GlStateManager.bindTexture(Gl.bloomTexture());
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GlStateManager.depthMask(false);
        GlStateManager.disableLighting();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0f);
        Gl.fullBright();
        net.minecraft.client.render.Tessellator t = net.minecraft.client.render.Tessellator.getInstance();
        net.minecraft.client.render.BufferBuilder buf = t.getBuffer();
        buf.begin(GL11.GL_QUADS, net.minecraft.client.render.VertexFormats.POSITION_TEXTURE_COLOR);
        buf.vertex(-s, y, -s).texture(0, 0).color(r, g, b, alpha).next();
        buf.vertex(-s, y, s).texture(0, 1).color(r, g, b, alpha).next();
        buf.vertex(s, y, s).texture(1, 1).color(r, g, b, alpha).next();
        buf.vertex(s, y, -s).texture(1, 0).color(r, g, b, alpha).next();
        t.draw();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);
        GlStateManager.enableLighting();
        GlStateManager.depthMask(true);
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableBlend();
        GlStateManager.color(1, 1, 1, 1);
    }

    private static int defaultTint(String id) {
        CosmeticCatalog.Item item = CosmeticCatalog.get(id);
        return item == null || item.defaultTint < 0 ? 0xFFFFFF : item.defaultTint;
    }

    private final float[] tintRgb = new float[3];

    /**
     * Binds an item's tint mask (unit 3) and emissive layer (unit 2) for the material shader's one
     * combined pass, and tells it which it has. Leaves unit 0 active for the albedo. Both bindings
     * go through {@code GlStateManager} so its cache stays true.
     */
    private void layersOn(CosmeticAssets.Tex tm, CosmeticAssets.Tex em, int tint, String id, float emissiveK) {
        if (em != null) {
            GlStateManager.activeTexture(com.mojang.blaze3d.platform.GLX.textureUnit + 2);
            GlStateManager.bindTexture(em.glId);
        }
        if (tm != null) {
            GlStateManager.activeTexture(com.mojang.blaze3d.platform.GLX.textureUnit + 3);
            GlStateManager.bindTexture(tm.glId);
            int rgb = tint >= 0 ? tint : defaultTint(id);
            tintRgb[0] = ((rgb >> 16) & 255) / 255f;
            tintRgb[1] = ((rgb >> 8) & 255) / 255f;
            tintRgb[2] = (rgb & 255) / 255f;
        }
        if (em != null || tm != null) {
            GlStateManager.activeTexture(com.mojang.blaze3d.platform.GLX.textureUnit);
        }
        CosmeticShader.layers(em != null ? emissiveK : 0, tm != null ? tintRgb : null);
    }

    /** The fixed-function emissive pass (the material shader draws the layer in its one pass). */
    private void emissiveBegin(float k) {
        GlStateManager.color(k, k, k, 1);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0f);
        Gl.fullBright();
    }

    private static void emissiveEnd(int light) {
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);
        GlStateManager.depthMask(true);
        GlStateManager.enableLighting();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableBlend();
        Gl.lightmap(light);
    }

    // -------------------------------------------------------------------------- models

    private void drawModel(PlayerFx fx, int slot, String id, int tint, ModelPart attach, String mode,
                           PlayerEntityModel model, AbstractClientPlayerEntity p, float time, float dt,
                           float glowK, int light, boolean helmet) {
        CosmeticAssets.Bundle bundle = CosmeticAssets.bundle(id);
        CosmeticModel m = bundle.model;
        if (m == null) {
            return;
        }
        CosmeticAssets.Tex tex = bundle.albedo();
        if (tex == null) {
            return;
        }
        CosmeticModel.Pose pose = fx.pose(slot, m);
        m.animate("idle", time, pose);
        Procedural.apply(m, pose, fx, p, time, dt);

        boolean wantGlow = glowK > 0 && m.glow.length > 0;
        if (wantGlow && bloomGlow) {
            for (CosmeticModel.Glow g : m.glow) {
                glow.note(g.rgb, g.strength * glowK);
            }
            wantGlow = false;
        }
        boolean emissiveFor = modelDist < MID * Budget.quality() * 0.7 || glowK > 0.9f;
        if (wantGlow) {
            Mat4.copy(modelview, itemMv);
        }
        GlStateManager.pushMatrix();
        if ("halo".equals(mode)) {
            // Follow the head's yaw fully and its pitch only partly: a halo that tipped forward
            // with every glance at the ground would read as stuck to the scalp.
            GlStateManager.translate(attach.pivotX * 0.0625f, attach.pivotY * 0.0625f, attach.pivotZ * 0.0625f);
            GlStateManager.rotate(attach.posY * 57.29578f, 0, 1, 0);
            GlStateManager.rotate(attach.posX * 57.29578f * 0.35f, 1, 0, 0);
            if (wantGlow) {
                Mat4.translate(itemMv, attach.pivotX * 0.0625f, attach.pivotY * 0.0625f, attach.pivotZ * 0.0625f);
                Mat4.rotate(itemMv, attach.posY, 0, 1, 0);
                Mat4.rotate(itemMv, attach.posX * 0.35f, 1, 0, 0);
            }
        } else {
            attach.preRender(0.0625f);
            if (wantGlow) {
                // ModelPart.preRender's transform, followed in Java: no glGet per item.
                Mat4.translate(itemMv, attach.pivotX * 0.0625f, attach.pivotY * 0.0625f, attach.pivotZ * 0.0625f);
                Mat4.rotate(itemMv, attach.posZ, 0, 0, 1);
                Mat4.rotate(itemMv, attach.posY, 0, 1, 0);
                Mat4.rotate(itemMv, attach.posX, 1, 0, 0);
            }
        }
        GlStateManager.scale(0.0625f, 0.0625f, 0.0625f);
        if (wantGlow) {
            Mat4.scale(itemMv, 0.0625f, 0.0625f, 0.0625f);
        }
        if (helmet && m.helmetFit) {
            // Clear a helmet: grow about the head's centre by the armour layer's inflation.
            GlStateManager.translate(0, -4, 0);
            GlStateManager.scale(1.12f, 1.12f, 1.12f);
            GlStateManager.translate(0, 4, 0);
            if (wantGlow) {
                Mat4.translate(itemMv, 0, -4, 0);
                Mat4.scale(itemMv, 1.12f, 1.12f, 1.12f);
                Mat4.translate(itemMv, 0, 4, 0);
            }
        } else if (helmet && "halo".equals(mode)) {
            GlStateManager.translate(0, -0.8f, 0);
            if (wantGlow) {
                Mat4.translate(itemMv, 0, -0.8f, 0);
            }
        }
        int frame = tex.frame(time);
        if (shaded) {
            // One pass for every layer, materials per cube, contact occlusion against the part the
            // item is worn on.
            CosmeticAssets.Tex tm = bundle.tint();
            // The emissive layer doubles nothing here, but past the mid band by day it is a few
            // pixels of light on a bright sky: left off there, as in the fixed-function path.
            CosmeticAssets.Tex em = emissiveFor ? bundle.emissive() : null;
            float ik = slotIntensity(fx, REACT_SLOT[slot]);
            float shade = albedoShade(fx);
            CosmeticShader.tint(shade, shade, shade, 1);
            CosmeticShader.bloom(playerBloom * ik);
            layersOn(tm, em, tint, id, emissive * ik);
            GlStateManager.bindTexture(tex.glId);
            Gl.selectFrame(frame, tex.frames);
            CosmeticShader.lit(true, CosmeticShader.MATTE, bundle.materialMask);
            CosmeticShader.occluder("halo".equals(mode) ? CosmeticShader.NONE
                    : attach == model.head ? CosmeticShader.HEAD : CosmeticShader.TORSO);
            if (m.hasGlass) {
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            }
            ModelDraw.draw(m, pose);
            if (m.hasGlass) {
                GlStateManager.disableBlend();
            }
            Gl.resetFrame(tex.frames);
            if (em != null || m.hasEmissiveMaterial) {
                Bloom.drew();
            }
        } else {
            GlStateManager.bindTexture(tex.glId);
            Gl.selectFrame(frame, tex.frames);
            ModelDraw.draw(m, pose);
            Gl.resetFrame(tex.frames);
            CosmeticAssets.Tex tm = bundle.tint();
            if (tm != null) {
                int rgb = tint >= 0 ? tint : defaultTint(id);
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GlStateManager.depthFunc(GL11.GL_LEQUAL);
                GlStateManager.bindTexture(tm.glId);
                GlStateManager.color(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1);
                Gl.selectFrame(frame, tm.frames);
                ModelDraw.draw(m, pose);
                Gl.resetFrame(tm.frames);
                GlStateManager.color(1, 1, 1, 1);
                GlStateManager.disableBlend();
            }
            // The emissive pass doubles a model's draw calls; past the mid band it is a few
            // pixels of light on a dark sky, so only near players (or any player at night,
            // when it is the point) get it.
            CosmeticAssets.Tex em = emissiveFor ? bundle.emissive() : null;
            if (em != null) {
                float e = emissive * slotIntensity(fx, REACT_SLOT[slot]);
                emissiveBegin(Math.min(1f, e));
                GlStateManager.bindTexture(em.glId);
                Gl.selectFrame(frame, em.frames);
                ModelDraw.draw(m, pose);
                if (e > 1.02f) {
                    GlStateManager.color(e - 1f, e - 1f, e - 1f, 1);
                    ModelDraw.draw(m, pose);
                }
                Gl.resetFrame(em.frames);
                emissiveEnd(light);
            }
        }
        if (wantGlow) {
            ModelDraw.glow(m, pose, itemMv, glow, glowK, scratch);
        }
        GlStateManager.popMatrix();
    }
}
