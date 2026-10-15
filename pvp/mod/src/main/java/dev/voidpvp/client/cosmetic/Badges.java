package dev.voidpvp.client.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager;
import dev.voidpvp.client.state.LiveState;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.lwjgl.opengl.GL11;

/**
 * The nametag badge: a 9x9 pixel icon in its own small plate to the left of the name, drawn in
 * the label's own billboard space (one unit = one font pixel), so it is exactly as crisp as the
 * name and scales with it. A VOID player who wears no badge still gets the plain VOID ring —
 * the marker that says "this player sees your cosmetics too".
 *
 * <p>Drawn twice like the name itself: once through walls, faint; once depth-tested, full. That is
 * vanilla's own two-pass trick for nametags, and matching it is what makes the badge read as part
 * of the tag rather than a sticker on it.</p>
 */
public final class Badges {

    public static final String MARKER = "void-badge-player";

    private Badges() {
    }

    /** The badge id to show for a player, or null. */
    static String badgeFor(PlayerEntity p) {
        LiveState s = LiveState.get();
        if (!s.cosmeticsOn) {
            return null;
        }
        String uuid = EquippedSet.normalise(String.valueOf(p.getUuid()));
        CosmeticState state = CosmeticState.get();
        EquippedSet set = state.of(uuid);
        if (set == null) {
            return null;
        }
        boolean self = uuid.equals(state.self().uuid);
        if (!self && !s.cosmeticsOthers) {
            return null;
        }
        String id = set.get("badge");
        return id != null ? id : MARKER;
    }

    /**
     * Called from inside {@code EntityRenderer.renderLabelIfPresent}, after the name is drawn and
     * before the label's matrix is popped. {@code text} is the line just drawn: the score line
     * under a name goes through the same method, and only the name line gets a badge.
     */
    public static void draw(Entity entity, String text, TextRenderer font) {
        if (!(entity instanceof PlayerEntity) || text == null || font == null) {
            return;
        }
        PlayerEntity p = (PlayerEntity) entity;
        if (p.isSneaking() || p.isInvisible()) {
            return;
        }
        String name = p.getGameProfile() == null ? null : p.getGameProfile().getName();
        if (name == null || !text.contains(name)) {
            return;
        }
        String id = badgeFor(p);
        if (id == null) {
            return;
        }
        JsonObject meta = CosmeticAssets.badge(id);
        int frames = 1;
        float fps = 4;
        float glow = 0;
        if (meta != null) {
            JsonArray fr = meta.has("frames") ? meta.getAsJsonArray("frames") : null;
            if (fr != null) {
                frames = Math.max(1, fr.get(0).getAsInt());
                fps = fr.get(1).getAsFloat();
            }
            glow = meta.has("glow") ? meta.get("glow").getAsFloat() : 0;
        }
        CosmeticAssets.Tex icon = CosmeticAssets.texture(id, "icon.png", frames, fps);
        if (icon == null) {
            return;
        }
        CosmeticAssets.Tex emi = CosmeticAssets.has(id, "emissive.png")
                ? CosmeticAssets.texture(id, "emissive.png", frames, fps) : null;
        int yOff = "deadmau5".equals(text) ? -10 : 0;
        int half = font.getStringWidth(text) / 2;
        // Vanilla's plate spans [-half-1, half+1] x [-1, 8]. The badge plate sits a pixel to its left.
        float x1 = -half - 2, x0 = x1 - 11;
        float y0 = -1 + yOff, y1 = 8 + yOff;
        int frame = icon.frame(System.nanoTime() / 1e9);

        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        Tessellator t = Tessellator.getInstance();
        BufferBuilder buf = t.getBuffer();

        // The plate, the same 25% black as vanilla's, drawn once (it is behind both passes).
        GlStateManager.disableTexture();
        GlStateManager.depthMask(false);
        buf.begin(GL11.GL_QUADS, VertexFormats.POSITION_COLOR);
        buf.vertex(x0, y0, 0).color(0f, 0f, 0f, 0.25f).next();
        buf.vertex(x0, y1, 0).color(0f, 0f, 0f, 0.25f).next();
        buf.vertex(x1, y1, 0).color(0f, 0f, 0f, 0.25f).next();
        buf.vertex(x1, y0, 0).color(0f, 0f, 0f, 0.25f).next();
        t.draw();
        GlStateManager.enableTexture();

        float ix0 = x0 + 1, ix1 = ix0 + 9, iy0 = y0, iy1 = y0 + 9;
        // Pass 1: through walls, faint.
        GlStateManager.disableDepthTest();
        icon(buf, t, icon.glId, frame, frames, ix0, iy0, ix1, iy1, 0.32f);
        GlStateManager.enableDepthTest();
        GlStateManager.depthMask(true);
        // Pass 2: depth-tested, full.
        icon(buf, t, icon.glId, frame, frames, ix0, iy0, ix1, iy1, 1f);
        if (emi != null || glow > 0) {
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GlStateManager.depthMask(false);
            Gl.fullBright();
            if (emi != null) {
                icon(buf, t, emi.glId, frame, frames, ix0, iy0, ix1, iy1, 1f);
            }
            if (glow > 0 && LiveState.get().cosmeticsGlow) {
                float cx = (ix0 + ix1) / 2, cy = (iy0 + iy1) / 2, r = 9f;
                GlStateManager.bindTexture(Gl.bloomTexture());
                buf.begin(GL11.GL_QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                float a = glow * 0.6f;
                buf.vertex(cx - r, cy - r, -0.01).texture(0, 0).color(1f, 0.62f, 0.48f, a).next();
                buf.vertex(cx - r, cy + r, -0.01).texture(0, 1).color(1f, 0.62f, 0.48f, a).next();
                buf.vertex(cx + r, cy + r, -0.01).texture(1, 1).color(1f, 0.62f, 0.48f, a).next();
                buf.vertex(cx + r, cy - r, -0.01).texture(1, 0).color(1f, 0.62f, 0.48f, a).next();
                t.draw();
            }
            GlStateManager.depthMask(true);
            GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        }
        GlStateManager.color(1, 1, 1, 1);
    }

    private static void icon(BufferBuilder buf, Tessellator t, int tex, int frame, int frames,
                             float x0, float y0, float x1, float y1, float alpha) {
        GlStateManager.bindTexture(tex);
        float v0 = (float) frame / frames, v1 = (float) (frame + 1) / frames;
        buf.begin(GL11.GL_QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buf.vertex(x0, y0, 0).texture(0, v0).color(1f, 1f, 1f, alpha).next();
        buf.vertex(x0, y1, 0).texture(0, v1).color(1f, 1f, 1f, alpha).next();
        buf.vertex(x1, y1, 0).texture(1, v1).color(1f, 1f, 1f, alpha).next();
        buf.vertex(x1, y0, 0).texture(1, v0).color(1f, 1f, 1f, alpha).next();
        t.draw();
    }
}
