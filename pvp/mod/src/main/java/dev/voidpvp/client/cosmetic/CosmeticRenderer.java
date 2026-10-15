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
    private static int hiClothCount;
    private static long hiClothFrame;

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
        String uuid = EquippedSet.normalise(String.valueOf(p.getUuid()));
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
        if (dist > FAR * q * 1.15 && !self) {
            return;
        }
        long now = System.nanoTime();
        PlayerFx fx = fx(p, uuid, now);
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

        if (glowOn) {
            Gl.modelview(modelview);
        }
        glow.clear();
        GlStateManager.color(1, 1, 1, 1);
        // The living-entity pass disables culling, but the held-item layer before this one can
        // switch it back on — and a mirrored wing has the opposite winding to its twin, so with
        // culling on one of the pair vanishes depending on which side it is seen from.
        GlStateManager.disableCull();

        String capeId = set.get("cape");
        if (capeId != null && !sleeping) {
            drawCape(p, uuid, fx, capeId, set.tint("cape"), td, dt, dist, q, gui, model, time, glowK, light);
        }
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
        glow.flush();
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

    /** Emits the local player's trail when they are not drawn (first person). Render thread. */
    public static void emitUnrendered(MinecraftClient mc, float td) {
        AbstractClientPlayerEntity p = mc.player;
        LiveState live = LiveState.get();
        if (p == null || !live.cosmeticsOn || !live.cosmeticsOwnFirstPerson || mc.options.perspective != 0
                || p.isInvisible() || p.isSpectator() || p.hasVehicle()) {
            return;
        }
        String uuid = EquippedSet.normalise(String.valueOf(p.getUuid()));
        EquippedSet set = CosmeticState.get().of(uuid);
        String trail = set == null ? null : set.get("trail");
        if (trail == null) {
            return;
        }
        long now = System.nanoTime();
        PlayerFx fx = fx(p, uuid, now);
        float dt = fx.lastNanos == 0 ? 0 : Math.min(0.1f, (now - fx.lastNanos) / 1e9f);
        fx.lastNanos = now;
        Trails.emit(fx, trail, p, p.prevX + (p.x - p.prevX) * td, p.prevY + (p.y - p.prevY) * td,
                p.prevZ + (p.z - p.prevZ) * td, dt, p.onGround);
    }

    private static PlayerFx fx(Entity p, String uuid, long now) {
        Integer key = Integer.valueOf(p.getEntityId());
        PlayerFx fx = FX.get(key);
        if (fx == null) {
            fx = new PlayerFx(p.getEntityId(), uuid);
            FX.put(key, fx);
        }
        fx.seenNanos = now;
        return fx;
    }

    /** Drops state for players not drawn lately. Once per tick. */
    public static void sweep() {
        long now = System.nanoTime();
        for (Iterator<PlayerFx> it = FX.values().iterator(); it.hasNext(); ) {
            if (now - it.next().seenNanos > FORGET_NANOS) {
                it.remove();
            }
        }
    }

    public static void clear() {
        FX.clear();
    }

    private static boolean helmet(AbstractClientPlayerEntity p) {
        return p.getArmorSlot(3) != null;
    }

    /** Glow reads strongest in the dark and stays faint by day, the way light does. */
    private static float glowStrength(Entity p, float td) {
        float b = p.getBrightnessAtEyes(td);
        return 1.0f - 0.55f * Math.max(0, Math.min(1, b));
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
        CapeStyle style = CosmeticAssets.cape(id);
        CosmeticAssets.Tex tex = CosmeticAssets.texture(id, "texture.png",
                style == null ? 1 : style.frames, style == null ? 0 : style.fps);
        if (tex == null) {
            return;
        }
        // Resolution, with hysteresis and a cap on how many hi-res capes a frame simulates.
        long frameId = System.nanoTime() / 4_000_000L;
        if (frameId != hiClothFrame) {
            hiClothFrame = frameId;
            hiClothCount = 0;
        }
        boolean self = p == MinecraftClient.getInstance().player;
        boolean wantHi = self || (fx.clothHi ? dist < NEAR * 1.4 * q : dist < NEAR * q);
        if (wantHi && !self && hiClothCount >= 6) {
            wantHi = false;
        }
        if (wantHi) {
            hiClothCount++;
        }
        if (fx.cloth == null || fx.clothHi != wantHi || !id.equals(fx.clothFor)) {
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
        cape.frame.set(x, y, z, bodyYaw, root[0], root[1], root[2]);
        part(cape.body, model.body);
        part(cape.rightLeg, model.rightLeg);
        part(cape.leftLeg, model.leftLeg);
        cape.armour = p.getArmorSlot(2) != null ? 1.1 : 0;

        if (gui) {
            if (!cloth.placed()) {
                return;
            }
        } else if (dist < FAR * q || !cloth.placed()) {
            cape.anchors(cloth, fx.ax, fx.ay, fx.az);
            cape.back(d3);
            boolean simulate = dist < MID * q || !cloth.placed();
            if (simulate) {
                cloth.iterations = fx.clothHi ? 5 : (dist < MID * q * 0.5 ? 3 : 2);
                // A breath of wind, so a cape at rest is never perfectly dead.
                cloth.windX = Math.sin(time * 0.7) * 0.6 + Math.sin(time * 1.9) * 0.25;
                cloth.windZ = Math.cos(time * 0.5) * 0.5;
                cloth.windY = 0;
                cloth.advance(dt, fx.ax, fx.ay, fx.az, cape.collider, d3[0], d3[2]);
            }
        }
        cape.prepare(cloth);

        GlStateManager.pushMatrix();
        GlStateManager.scale(0.0625f, 0.0625f, 0.0625f);
        int frame = tex.frame(time);
        // Albedo, lit.
        GlStateManager.bindTexture(tex.glId);
        Gl.selectFrame(frame, tex.frames);
        cape.draw(cloth, false);
        Gl.resetFrame(tex.frames);
        // Tint mask.
        if (CosmeticAssets.has(id, "tint.png")) {
            CosmeticAssets.Tex tm = CosmeticAssets.texture(id, "tint.png", 1, 0);
            if (tm != null) {
                int rgb = tint >= 0 ? tint : defaultTint(id);
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GlStateManager.depthFunc(GL11.GL_LEQUAL);
                GlStateManager.bindTexture(tm.glId);
                GlStateManager.color(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1);
                cape.draw(cloth, false);
                GlStateManager.color(1, 1, 1, 1);
                GlStateManager.disableBlend();
            }
        }
        // Emissive, full-bright and additive.
        if (CosmeticAssets.has(id, "emissive.png")) {
            CosmeticAssets.Tex em = CosmeticAssets.texture(id, "emissive.png", tex.frames, tex.fps);
            if (em != null) {
                emissiveBegin();
                GlStateManager.bindTexture(em.glId);
                Gl.selectFrame(frame, em.frames);
                cape.draw(cloth, true);
                Gl.resetFrame(em.frames);
                emissiveEnd(light);
            }
        }
        // Glow anchors on the outer face.
        if (glowK > 0 && style != null && style.glow.length > 0) {
            Mat4.copy(modelview, scratch);
            Mat4.scale(scratch, 0.0625f, 0.0625f, 0.0625f);
            for (float[] g : style.glow) {
                cape.pointOnOuter(cloth, g[0], g[1], d3);
                Mat4.transform(scratch, (float) d3[0], (float) d3[1], (float) d3[2], eye);
                glow.add(eye[0], eye[1], eye[2], g[3] * Mat4.scaleOf(scratch), (int) g[2], g[4] * glowK);
            }
        }
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

    private static int defaultTint(String id) {
        CosmeticCatalog.Item item = CosmeticCatalog.get(id);
        return item == null || item.defaultTint < 0 ? 0xFFFFFF : item.defaultTint;
    }

    private void emissiveBegin() {
        GlStateManager.color(emissive, emissive, emissive, 1);
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
        CosmeticModel m = CosmeticAssets.model(id);
        if (m == null) {
            return;
        }
        CosmeticAssets.Tex tex = CosmeticAssets.texture(id, "texture.png", m.frames, m.fps);
        if (tex == null) {
            return;
        }
        CosmeticModel.Pose pose = fx.pose(slot, m);
        m.animate("idle", time, pose);
        Procedural.apply(m, pose, fx, p, time, dt);

        GlStateManager.pushMatrix();
        if ("halo".equals(mode)) {
            // Follow the head's yaw fully and its pitch only partly: a halo that tipped forward
            // with every glance at the ground would read as stuck to the scalp.
            GlStateManager.translate(attach.pivotX * 0.0625f, attach.pivotY * 0.0625f, attach.pivotZ * 0.0625f);
            GlStateManager.rotate(attach.posY * 57.29578f, 0, 1, 0);
            GlStateManager.rotate(attach.posX * 57.29578f * 0.35f, 1, 0, 0);
        } else {
            attach.preRender(0.0625f);
        }
        GlStateManager.scale(0.0625f, 0.0625f, 0.0625f);
        if (helmet && m.helmetFit) {
            // Clear a helmet: grow about the head's centre by the armour layer's inflation.
            GlStateManager.translate(0, -4, 0);
            GlStateManager.scale(1.12f, 1.12f, 1.12f);
            GlStateManager.translate(0, 4, 0);
        } else if (helmet && "halo".equals(mode)) {
            GlStateManager.translate(0, -0.8f, 0);
        }
        int frame = tex.frame(time);
        GlStateManager.bindTexture(tex.glId);
        Gl.selectFrame(frame, tex.frames);
        ModelDraw.draw(m, pose);
        Gl.resetFrame(tex.frames);

        if (CosmeticAssets.has(id, "tint.png")) {
            CosmeticAssets.Tex tm = CosmeticAssets.texture(id, "tint.png", m.frames, m.fps);
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
        }
        if (CosmeticAssets.has(id, "emissive.png")) {
            CosmeticAssets.Tex em = CosmeticAssets.texture(id, "emissive.png", m.frames, m.fps);
            if (em != null) {
                emissiveBegin();
                GlStateManager.bindTexture(em.glId);
                Gl.selectFrame(frame, em.frames);
                ModelDraw.draw(m, pose);
                Gl.resetFrame(em.frames);
                emissiveEnd(light);
            }
        }
        if (glowK > 0 && m.glow.length > 0) {
            // The modelview here already carries the attachment; read it once for this item.
            ModelDraw.glow(m, pose, Gl.modelview(itemMv), glow, glowK, scratch);
        }
        GlStateManager.popMatrix();
    }
}
