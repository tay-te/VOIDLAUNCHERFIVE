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
    private final double[] kick = new double[3];
    /** The renderer's model slots as the reactions name them. */
    private static final int[] REACT_SLOT = {Reaction.BACK, Reaction.HEAD, Reaction.FACE, Reaction.HALO};
    private float emissive = 1f;
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
        // A dying player's tilt is not in the Java modelview (playerModelview), so their glow
        // would hang in the air where they stood.
        boolean glowOn = live.cosmeticsGlow && dist < MID * q && !gui && p.deathTime == 0;
        int light = p.getLightmapCoordinates(td);
        float glowK = glowOn ? glowStrength(p, td) : 0;
        // Emissive texels: full strength in the dark, about half against daylight, where the lit
        // albedo underneath already carries the colour and an additive layer would wash to white.
        emissive = 0.42f + 0.58f * (1 - Math.max(0, Math.min(1, p.getBrightnessAtEyes(td))));

        motion(fx, p, td, dt, gui);
        // Fight reactions and secondary motion (Reactive, Secondary). The springs rest on the body
        // past the mid band (the LOD) and in the inventory preview.
        Reaction react = gui ? null : Reactive.peek(uuid);
        if (react != null) {
            react.update(Reactive.now(), Reactive.level());
        }
        fx.react = react;
        fx.sec.update(p, td, Secondary.frameDt, !gui && (self || dist < MID * q), react);

        if (glowOn) {
            playerModelview(mc, p, fx.uuid, td);
        }
        glow.clear();
        GlStateManager.color(1, 1, 1, 1);
        // The living-entity pass disables culling, but the held-item layer before this one can
        // switch it back on — and a mirrored wing has the opposite winding to its twin, so with
        // culling on one of the pair vanishes depending on which side it is seen from.
        GlStateManager.disableCull();

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
        long tg = System.nanoTime();
        Budget.split(1, tg - tm);
        if (glow.poolStrength > 0 && p.onGround && glowK > 0.6f) {
            EmotePose.root(uuid, root);
            pool(glow.poolRgb, Math.min(0.38f, glow.poolStrength * 0.45f * (glowK - 0.45f)), root[1]);
        }
        glow.flush();
        Budget.split(2, System.nanoTime() - tg);
        Gl.lightmap(light);
        GlStateManager.color(1, 1, 1, 1);

        String trailId = set.get("trail");
        if (trailId != null && !sleeping && !gui && !p.hasVehicle() && dist < MID * q) {
            double x = p.prevX + (p.x - p.prevX) * td;
            double y = p.prevY + (p.y - p.prevY) * td;
            double z = p.prevZ + (p.z - p.prevZ) * td;
            Trails.emit(fx, trailId, p, x, y, z, dt, p.onGround, slotIntensity(fx, Reaction.TRAIL));
        }
    }

    /** The reactive emissive multiplier for one slot of this player (1 at rest). */
    private static float slotIntensity(PlayerFx fx, int slot) {
        return fx.react == null ? 1f : fx.react.intensity(slot);
    }

    /** Albedo shade for low health: at most 15% darker, so the item never reads as a different one. */
    private static float albedoShade(PlayerFx fx) {
        return fx.react == null ? 1f : 1f - 0.5f * fx.react.dim;
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
        Reaction react = Reactive.peek(fx.uuid);
        float boost = 1f;
        if (react != null) {
            react.update(Reactive.now(), Reactive.level());
            boost = react.intensity(Reaction.TRAIL);
        }
        Trails.emit(fx, trail, p, p.prevX + (p.x - p.prevX) * td, p.prevY + (p.y - p.prevY) * td,
                p.prevZ + (p.z - p.prevZ) * td, dt, p.onGround, boost);
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
                // Kept under what lifts a sheet against gravity (drag 7 x wind ~2.3 b/s): a gust
                // stirs a standing cape, it never holds it out flat like a flag.
                cloth.windX = Math.sin(time * 0.7) * 0.9 + Math.sin(time * 1.9) * 0.35 + gust * 0.6;
                cloth.windZ = Math.cos(time * 0.5) * 0.8 + gust * 0.8;
                cloth.windY = 0;
                // Low-resolution capes step at 60 Hz (Cloth.step) and only on alternate frames;
                // the time carries over, and the mesh is reused between steps.
                // Secondary motion and reactions push the sheet: the body's acceleration
                // exaggerated (a whip on every change of direction), and one-shot kicks for a
                // hit taken or landed, a landing, a sprint-jump, and a kill's ripple.
                Secondary sc = fx.sec;
                if (sc.on) {
                    cape.frame.dirToWorld(sc.capeShotX, sc.capeShotY, sc.capeShotZ, kick);
                    cloth.kick(kick[0] + sc.capeKickX, kick[1] + sc.capeKickY, kick[2] + sc.capeKickZ, 1.0);
                    if (sc.capeWave > 0) {
                        cloth.wave(75.0 * sc.capeWave);
                    }
                }
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
        // Albedo, lit.
        GlStateManager.bindTexture(tex.glId);
        Gl.selectFrame(frame, tex.frames);
        // Fold shading where it can be seen: the high-resolution sheets close to the camera.
        float shade = albedoShade(fx);
        float ik = slotIntensity(fx, Reaction.CAPE);
        GlStateManager.color(shade, shade, shade, 1);
        cape.draw(cloth, false, fx.clothHi);
        GlStateManager.color(1, 1, 1, 1);
        Gl.resetFrame(tex.frames);
        // Tint mask.
        {
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
        }
        // Emissive, full-bright and additive.
        {
            CosmeticAssets.Tex em = bundle.emissive();
            if (em != null) {
                float e = emissive * ik;
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
                glow.add(eye[0], eye[1], eye[2], g[3] * Mat4.scaleOf(scratch), (int) g[2], g[4] * glowK * ik);
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

        float ik = slotIntensity(fx, REACT_SLOT[slot]);
        float shade = albedoShade(fx);
        boolean wantGlow = glowK > 0 && m.glow.length > 0;
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
        Secondary sc = fx.sec;
        if (sc.on && slot == SLOT_HEAD) {
            // A hat rides the head on a spring (Secondary): it rocks about the crown after a snap
            // turn, presses down on a landing and lifts a touch at the top of a jump.
            GlStateManager.translate(0, -8 + sc.hatLift, 0);
            GlStateManager.rotate(sc.hatRoll * 57.29578f, 0, 0, 1);
            GlStateManager.rotate(sc.hatPitchOut * 57.29578f, 1, 0, 0);
            GlStateManager.scale(1 + sc.hatSquash * 0.5f, 1 - sc.hatSquash, 1 + sc.hatSquash * 0.5f);
            GlStateManager.translate(0, 8, 0);
            if (wantGlow) {
                Mat4.translate(itemMv, 0, -8 + sc.hatLift, 0);
                Mat4.rotate(itemMv, sc.hatRoll, 0, 0, 1);
                Mat4.rotate(itemMv, sc.hatPitchOut, 1, 0, 0);
                Mat4.scale(itemMv, 1 + sc.hatSquash * 0.5f, 1 - sc.hatSquash, 1 + sc.hatSquash * 0.5f);
                Mat4.translate(itemMv, 0, 8, 0);
            }
        }
        int frame = tex.frame(time);
        GlStateManager.bindTexture(tex.glId);
        Gl.selectFrame(frame, tex.frames);
        GlStateManager.color(shade, shade, shade, 1);
        ModelDraw.draw(m, pose);
        GlStateManager.color(1, 1, 1, 1);
        Gl.resetFrame(tex.frames);

        {
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
        }
        {
            // The emissive pass doubles a model's draw calls; past the mid band it is a few
            // pixels of light on a dark sky, so only near players (or any player at night,
            // when it is the point) get it.
            CosmeticAssets.Tex em = emissiveFor ? bundle.emissive() : null;
            if (em != null) {
                float e = emissive * ik;
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
            ModelDraw.glow(m, pose, itemMv, glow, glowK * ik, scratch);
        }
        GlStateManager.popMatrix();
    }
}
