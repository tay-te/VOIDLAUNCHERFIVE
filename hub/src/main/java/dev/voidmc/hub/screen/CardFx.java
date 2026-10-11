package dev.voidmc.hub.screen;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import dev.voidmc.hub.VoidHub;
import dev.voidmc.hub.model.Bridge;
import dev.voidmc.hub.ui.HubView;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/**
 * The Mettle cards' moving parts, drawn natively over the page (the way item stacks are): burning edges, embers, the
 * hot frame, the pick's flare and burn-away, the Mythic's fire, rays, motes, ring, foil and blooms, the set moment's
 * rays and sparks, the collection hover flicker, the lift of a hovered card and the drop-slot pulse.
 *
 * <p>Ultralight repaints whatever a CSS animation touches, and a burning hand repainted ~8 times a second at ~30 ms a
 * paint. Here the page stays still and only repaints on real changes: it reports each animated layer once
 * ({@code Bridge.Fx}: kind, anchor, tint, alpha, delay) and Java steps the sprite strips from
 * {@code assets/voidhub/ui/cards/} itself, at the native texel size ({@code *_px.png}, NEAREST), tinting the white
 * masks with the vertex colour. Looping strips run on one global clock so every card's frames change together; a
 * one-shot starts when its id is first seen (plus its delay) and keeps that start across later messages.
 *
 * <p>Render thread only.
 */
public final class CardFx {

	private static final int FILL_GLOW = 0;
	private static final int COLOUR = 1;
	private static final int MASK = 2;

	/** A sprite strip: file stem, frames, frame size and offset from the anchor (CSS px), frame time, layers. */
	private record Strip(String path, int frames, int fw, int fh, int ox, int oy, int ms, int layers, boolean once) {
	}

	private static final Map<String, Strip> STRIPS = new HashMap<>();

	static {
		for (String r : List.of("common", "rare", "epic", "cursed", "wildcard")) {
			STRIPS.put("edge-" + r, new Strip("hand/edge_" + r, 4, 304, 384, -28, -28, 125, FILL_GLOW, false));
			STRIPS.put("hover-" + r, new Strip("collection/hover_" + r, 2, 112, 144, -8, -8, 166, FILL_GLOW, false));
		}
		STRIPS.put("edge-hot", new Strip("hand/edge_hover", 4, 304, 384, -28, -28, 125, FILL_GLOW, false));
		STRIPS.put("embers-sparse", new Strip("hand/embers_sparse", 6, 304, 384, -28, -28, 200, FILL_GLOW, false));
		STRIPS.put("embers-dense", new Strip("hand/embers_dense", 6, 304, 384, -28, -28, 200, FILL_GLOW, false));
		STRIPS.put("flare", new Strip("hand/flare", 4, 304, 384, -28, -28, 50, FILL_GLOW, true));
		STRIPS.put("bfront", new Strip("hand/burn_front", 8, 304, 384, -28, -28, 75, FILL_GLOW, true));
		STRIPS.put("ash", new Strip("hand/burn_ash", 8, 304, 384, -28, -28, 75, COLOUR, true));
		STRIPS.put("back", new Strip("hand/back", 1, 304, 384, -28, -28, 1000, COLOUR, false));
		STRIPS.put("my-edge", new Strip("mythic/edge", 6, 304, 384, -28, -28, 100, COLOUR, false));
		STRIPS.put("crack", new Strip("mythic/crack", 8, 304, 384, -28, -28, 100, COLOUR, true));
		STRIPS.put("burst", new Strip("mythic/burst", 10, 448, 448, -100, -60, 100, COLOUR, true));
		STRIPS.put("foil", new Strip("mythic/foil", 16, 248, 328, 0, 0, 100, COLOUR, false));
		STRIPS.put("set-rays", new Strip("set/rays", 12, 384, 256, 0, 0, 100, COLOUR, true));
		STRIPS.put("set-sparks", new Strip("set/sparks", 12, 384, 256, 0, 0, 100, MASK, true));
		for (String c : List.of("white", "gold", "cyan", "pink")) {
			STRIPS.put("mote-" + c, new Strip("mythic/mote_" + c, 12, 10, 10, 0, 0, 100, COLOUR, false));
		}
	}

	private record Tex(Identifier id, int w, int h) {
	}

	private static final Map<String, Optional<Tex>> TEXTURES = new HashMap<>();

	/** A texture under {@code assets/voidhub/ui/cards/}, loaded once (NEAREST for pixel art, LINEAR for soft art). */
	private static Tex tex(String file, boolean linear) {
		return TEXTURES.computeIfAbsent(file, f -> {
			Identifier res = Identifier.fromNamespaceAndPath(VoidHub.MOD_ID, "ui/cards/" + f);
			Optional<Resource> r = Minecraft.getInstance().getResourceManager().getResource(res);
			if (r.isEmpty()) {
				VoidHub.LOGGER.warn("VOID Hub: card texture {} is missing", f);
				return Optional.empty();
			}
			try (InputStream in = r.get().open()) {
				NativeImage img = NativeImage.read(in);
				int w = img.getWidth();
				int h = img.getHeight();
				Identifier id = Identifier.fromNamespaceAndPath(VoidHub.MOD_ID, "cardfx/" + f.replace(".png", ""));
				Minecraft.getInstance().getTextureManager().register(id, new FxTexture(f, img, linear));
				return Optional.of(new Tex(id, w, h));
			} catch (Exception e) {
				VoidHub.LOGGER.warn("VOID Hub: could not load card texture {}", f, e);
				return Optional.empty();
			}
		}).orElse(null);
	}

	/** Resource reloads drop the textures; they load again on first use. */
	public static void clearTextures() {
		TEXTURES.clear();
	}

	private static final class FxTexture extends DynamicTexture {
		FxTexture(String name, NativeImage image, boolean linear) {
			super(() -> "VOID Hub card fx " + name, image);
			this.sampler = RenderSystem.getSamplerCache().getClampToEdge(linear ? FilterMode.LINEAR : FilterMode.NEAREST);
		}
	}

	/** A lift on its way back down after the pointer left (it eases out from where it was). */
	private record Leaving(Bridge.FxItem item, long start, double from) {
	}

	private List<Bridge.FxItem> items = List.of();
	private final Map<String, Long> seen = new HashMap<>();
	private final List<Leaving> leaving = new ArrayList<>();
	private static final double LIFT_MS = 160;

	public boolean active() {
		return !this.items.isEmpty() || !this.leaving.isEmpty();
	}

	/** The page's new set of layers. Ids it kept keep their start; a lift it dropped eases back. */
	public void set(List<Bridge.FxItem> next) {
		long now = System.nanoTime();
		Map<String, Bridge.FxItem> kept = new HashMap<>();
		for (Bridge.FxItem it : next) {
			kept.put(it.id(), it);
		}
		for (Bridge.FxItem old : this.items) {
			if ("lift".equals(old.kind()) && !kept.containsKey(old.id())) {
				this.leaving.add(new Leaving(old, now, this.liftProgress(old.id(), now)));
			}
		}
		this.seen.keySet().retainAll(kept.keySet());
		for (Bridge.FxItem it : next) {
			this.seen.putIfAbsent(it.id(), now);
		}
		this.leaving.removeIf(l -> kept.containsKey(l.item().id()));
		this.items = next;
	}

	public void clear() {
		this.items = List.of();
		this.seen.clear();
		this.leaving.clear();
	}

	private static double ms(long nanos) {
		return nanos / 1_000_000.0;
	}

	private double liftProgress(String id, long now) {
		Long s = this.seen.get(id);
		return s == null ? 0 : easeOut(Math.min(1, ms(now - s) / LIFT_MS));
	}

	private static double easeOut(double t) {
		return 1 - (1 - t) * (1 - t) * (1 - t);
	}

	/**
	 * Draws everything over the page. {@code u}: physical px per CSS px; the hub texture's size for lifted copies.
	 * Called inside the screen's GUI pose; this scales to physical px itself.
	 */
	public void extract(GuiGraphicsExtractor g, double u, int gs, int hubW, int hubH) {
		if (!this.active()) {
			return;
		}
		long now = System.nanoTime();
		double clock = ms(now);
		g.pose().pushMatrix();
		g.pose().scale(1.0F / gs, 1.0F / gs);
		// Lifts on their way down first, then the current layers in the page's order.
		this.leaving.removeIf(l -> ms(now - l.start()) > LIFT_MS);
		for (Leaving l : this.leaving) {
			double e = l.from() * (1 - easeOut(Math.min(1, ms(now - l.start()) / LIFT_MS)));
			this.lift(g, l.item(), e, u, hubW, hubH);
		}
		Map<String, double[]> lifts = new HashMap<>();
		for (Bridge.FxItem it : this.items) {
			long start = this.seen.getOrDefault(it.id(), now);
			double age = ms(now - start) - it.delay();
			if (age < 0) {
				continue;
			}
			if ("lift".equals(it.kind())) {
				double e = easeOut(Math.min(1, age / LIFT_MS));
				lifts.put(it.id(), new double[] {e, it.x() + it.w() / 2, it.y() + it.h() / 2, it.p(), it.q()});
				this.lift(g, it, e, u, hubW, hubH);
				continue;
			}
			// A layer of a lifted card (id "<lift>-…") moves with it.
			double[] lift = null;
			int dash = it.id().indexOf('-');
			if (dash > 0) {
				lift = lifts.get(it.id().substring(0, dash));
			}
			g.pose().pushMatrix();
			if (lift != null) {
				float cx = (float) (lift[1] * u);
				float cy = (float) (lift[2] * u);
				float sc = (float) (1 + (lift[4] - 1) * lift[0]);
				g.pose().translate(cx, (float) (cy + lift[3] * lift[0] * u));
				g.pose().scale(sc, sc);
				g.pose().translate(-cx, -cy);
			}
			this.layer(g, it, age, clock, u);
			g.pose().popMatrix();
		}
		g.pose().popMatrix();
	}

	private static int argb(int rgb, double alpha) {
		return ((int) Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24) | (rgb & 0xFFFFFF);
	}

	private static double fade(Bridge.FxItem it, double age, double fadeMs) {
		return fadeMs <= 0 ? it.alpha() : it.alpha() * Math.min(1, age / fadeMs);
	}

	/** A copy of the page's own pixels in a box, lifted {@code p} CSS px and scaled {@code q} at progress {@code e}. */
	private void lift(GuiGraphicsExtractor g, Bridge.FxItem it, double e, double u, int hubW, int hubH) {
		int sx = (int) Math.round(it.x() * u);
		int sy = (int) Math.round(it.y() * u);
		int sw = (int) Math.round(it.w() * u);
		int sh = (int) Math.round(it.h() * u);
		if (sw <= 0 || sh <= 0 || sx < 0 || sy < 0 || sx + sw > hubW || sy + sh > hubH) {
			return;
		}
		float cx = sx + sw / 2.0F;
		float cy = sy + sh / 2.0F;
		float sc = (float) (1 + (it.q() - 1) * e);
		g.pose().pushMatrix();
		g.pose().translate(cx, (float) (cy + it.p() * e * u));
		g.pose().scale(sc, sc);
		g.pose().translate(-cx, -cy);
		g.blit(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA, HubView.TEXTURE_ID, sx, sy, sx, sy, sw, sh, sw, sh, hubW, hubH, 0xFFFFFFFF);
		g.pose().popMatrix();
	}

	private void layer(GuiGraphicsExtractor g, Bridge.FxItem it, double age, double clock, double u) {
		switch (it.kind()) {
			case "rect" -> {
				int a = argb(it.colour(), fade(it, age, it.q()));
				g.fill((int) Math.round(it.x() * u), (int) Math.round(it.y() * u), (int) Math.round((it.x() + it.w()) * u), (int) Math.round((it.y() + it.h()) * u), a);
			}
			case "pulse" -> {
				double a = it.alpha() * (0.35 + 0.65 * (0.5 + 0.5 * Math.sin(clock / 1000.0 * Math.PI * 2)));
				g.fill((int) Math.round((it.x() + 2) * u), (int) Math.round((it.y() + 2) * u), (int) Math.round((it.x() + it.w() - 2) * u),
					(int) Math.round((it.y() + it.h() - 2) * u), argb(it.colour(), a));
			}
			case "bloom", "prism" -> {
				Tex t = tex("fx/" + it.kind() + ".png", true);
				if (t != null) {
					int w = (int) Math.round(it.w() * u);
					int h = (int) Math.round(it.h() * u);
					int x = (int) Math.round(it.x() * u) - w / 2;
					int y = (int) Math.round(it.y() * u) - h / 2;
					double a = fade(it, age, it.q());
					if (it.p() > 0) {  // a flash: fades out over p ms, then gone
						if (age >= it.p()) {
							return;
						}
						a *= 1 - age / it.p();
					}
					g.blit(RenderPipelines.GUI_TEXTURED, t.id(), x, y, 0, 0, w, h, t.w(), t.h(), t.w(), t.h(), argb(it.colour(), a));
				}
			}
			case "rays" -> {
				Tex t = tex("mythic/rays_x2.png", true);
				if (t != null) {
					int s = (int) Math.round(512 * u);
					float cx = (float) (it.x() * u);
					float cy = (float) (it.y() * u);
					g.pose().pushMatrix();
					g.pose().translate(cx, cy);
					g.pose().rotate((float) Math.toRadians((it.p() < 0 ? -1 : 1) * clock / 1000.0 * 6.0 % 360.0));
					g.blit(RenderPipelines.GUI_TEXTURED, t.id(), -s / 2, -s / 2, 0, 0, s, s, t.w(), t.h(), t.w(), t.h(), argb(0xFFFFFF, fade(it, age, it.q())));
					g.pose().popMatrix();
				}
			}
			case "ring" -> {
				Tex t = tex("mythic/ring_px.png", false);
				if (t != null) {
					double ph = (clock % 2400.0) / 2400.0;
					float sc = (float) (0.95 + 0.13 * ph);
					int s = (int) Math.round(480 * u);
					float cx = (float) (it.x() * u);
					float cy = (float) (it.y() * u);
					g.pose().pushMatrix();
					g.pose().translate(cx, cy);
					g.pose().scale(sc, sc);
					g.blit(RenderPipelines.GUI_TEXTURED, t.id(), -s / 2, -s / 2, 0, 0, s, s, t.w(), t.h(), t.w(), t.h(), argb(0xFFFFFF, it.alpha() * 0.7 * (1 - ph)));
					g.pose().popMatrix();
				}
			}
			default -> this.strip(g, it, age, clock, u);
		}
	}

	private void strip(GuiGraphicsExtractor g, Bridge.FxItem it, double age, double clock, double u) {
		Strip s = STRIPS.get(it.kind());
		if (s == null) {
			return;
		}
		int frame;
		double x = it.x();
		double y = it.y();
		if (s.once()) {
			frame = (int) (age / s.ms());
			if (frame >= s.frames()) {
				return;
			}
		} else if ("foil".equals(it.kind())) {
			double t = age % 4000.0;  // a 16-frame sweep every four seconds
			if (t >= s.frames() * s.ms()) {
				return;
			}
			frame = (int) (t / s.ms());
		} else if (it.kind().startsWith("mote-")) {
			double t = clock + it.p();
			frame = (int) (t / s.ms()) % s.frames();
			double tri = (t % 6000.0) / 3000.0;  // drift up 12 px over 3 s and back
			y -= 12 * (tri <= 1 ? tri : 2 - tri);
		} else if ("back".equals(it.kind())) {
			frame = 0;
			if (it.p() > 0 && age < it.p()) {  // trembles until p ms
				int step = (int) (age / 40) % 3;
				x += step == 1 ? 1 : step == 2 ? -1 : 0;
				y += step == 1 ? -1 : step == 2 ? 1 : 0;
			}
		} else {
			frame = (int) (clock / s.ms()) % s.frames();
		}
		double alpha = "edge-hot".equals(it.kind()) ? fade(it, age, 120) : it.alpha();
		if (it.clip() != null) {
			double[] c = it.clip();
			g.enableScissor((int) Math.round(c[0] * u), (int) Math.round(c[1] * u), (int) Math.round((c[0] + c[2]) * u), (int) Math.round((c[1] + c[3]) * u));
		}
		boolean mirror = "set-sparks".equals(it.kind()) && it.p() > 0;
		if (s.layers() == FILL_GLOW) {
			this.frame(g, s, "_fill_px.png", x, y, frame, argb(it.colour(), alpha), mirror, u, it.h());
			this.frame(g, s, "_glow_px.png", x, y, frame, argb(0xFFFFFF, alpha), mirror, u, it.h());
		} else if (s.layers() == MASK) {
			this.frame(g, s, "_fill_px.png", x, y, frame, argb(it.colour(), alpha), mirror, u, it.h());
		} else {
			this.frame(g, s, "_px.png", x, y, frame, argb(0xFFFFFF, alpha), mirror, u, it.h());
		}
		if (it.clip() != null) {
			g.disableScissor();
		}
	}

	/**
	 * One frame of a strip. {@code cardH} > 0 on a hand-card strip (304 x 384 around a 248 x 328 card) fits it to a card
	 * of that height: the frame's top half and bottom half are drawn as they are and meet in the middle (the sides of a
	 * burning edge are the same all the way down), so the corners and the flames keep their pixels.
	 */
	private void frame(GuiGraphicsExtractor g, Strip s, String suffix, double x, double y, int frame, int colour, boolean mirror, double u, double cardH) {
		Tex t = tex(s.path() + suffix, false);
		if (t == null || (colour >>> 24) == 0) {
			return;
		}
		int sw = t.w() / s.frames();
		int X = (int) Math.round((x + s.ox()) * u);
		int Y = (int) Math.round((y + s.oy()) * u);
		int W = (int) Math.round(s.fw() * u);
		int H = (int) Math.round(s.fh() * u);
		if (mirror) {
			g.pose().pushMatrix();
			g.pose().translate(X + W / 2.0F, 0);
			g.pose().scale(-1, 1);
			g.pose().translate(-(X + W / 2.0F), 0);
		}
		double fitted = cardH - s.oy() * 2;  // the strip's height around a card of cardH
		if (cardH > 0 && s.fh() == 384 && s.oy() == -28 && Math.abs(fitted - s.fh()) >= 1) {
			double cssPerTexel = (double) s.fh() / t.h();
			int all = (int) Math.round(fitted / cssPerTexel);
			int top = Math.min(t.h(), all / 2);
			int bottom = Math.min(t.h(), all - top);
			int topH = (int) Math.round(top * cssPerTexel * u);
			int botY = (int) Math.round((y + s.oy() + top * cssPerTexel) * u);
			int botH = (int) Math.round((y + s.oy() + all * cssPerTexel) * u) - botY;
			g.blit(RenderPipelines.GUI_TEXTURED, t.id(), X, Y, frame * sw, 0, W, topH, sw, top, t.w(), t.h(), colour);
			g.blit(RenderPipelines.GUI_TEXTURED, t.id(), X, botY, frame * sw, t.h() - bottom, W, botH, sw, bottom, t.w(), t.h(), colour);
		} else {
			g.blit(RenderPipelines.GUI_TEXTURED, t.id(), X, Y, frame * sw, 0, W, H, sw, t.h(), t.w(), t.h(), colour);
		}
		if (mirror) {
			g.pose().popMatrix();
		}
	}
}
