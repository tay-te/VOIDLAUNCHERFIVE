package dev.voidmc.hub.screen;

import dev.voidmc.hub.model.Bridge;
import dev.voidmc.hub.ui.HubView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

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
public final class CardFx extends NativeFx {

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

	/** A texture under {@code assets/voidhub/ui/cards/}, loaded once (NEAREST for pixel art, LINEAR for soft art). */
	private static Tex tex(String file, boolean linear) {
		return NativeFx.tex("ui/cards", "cardfx", file, linear);
	}

	/**
	 * A resource reload (F3+T, a pack change): the strips (and every other fx texture) are released and load again,
	 * from the new resources, on first use. Render thread.
	 */
	public static void clearTextures() {
		NativeFx.clearAllTextures();
		reloads++;
	}

	/** Tests: how many reloads dropped the strips. */
	public static volatile int reloads;

	/** A lift on its way back down after the pointer left (it eases out from where it was). */
	private record Leaving(Bridge.FxItem item, long start, double from) {
	}

	private final List<Leaving> leaving = new ArrayList<>();
	private static final double LIFT_MS = 160;

	@Override
	public boolean active() {
		return !this.items.isEmpty() || !this.leaving.isEmpty();
	}

	/** The page's new set of layers. Ids it kept keep their start; a lift it dropped eases back. */
	@Override
	public void set(List<Bridge.FxItem> next) {
		super.set(next);
		java.util.Set<String> kept = new java.util.HashSet<>();
		for (Bridge.FxItem it : next) {
			kept.add(it.id());
		}
		this.leaving.removeIf(l -> kept.contains(l.item().id()));
	}

	@Override
	protected void dropped(List<Bridge.FxItem> old, Map<String, Bridge.FxItem> kept, long now) {
		for (Bridge.FxItem o : old) {
			if ("lift".equals(o.kind()) && !kept.containsKey(o.id())) {
				this.leaving.add(new Leaving(o, now, this.liftProgress(o.id(), now)));
			}
		}
	}

	@Override
	public void clear() {
		super.clear();
		this.leaving.clear();
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
	@Override
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

	@Override
	protected void layer(GuiGraphicsExtractor g, Bridge.FxItem it, double age, double clock, double u) {
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
			this.frame(g, s, "_fill_px.png", x, y, frame, argb(it.colour(), alpha), mirror, u);
			this.frame(g, s, "_glow_px.png", x, y, frame, argb(0xFFFFFF, alpha), mirror, u);
		} else if (s.layers() == MASK) {
			this.frame(g, s, "_fill_px.png", x, y, frame, argb(it.colour(), alpha), mirror, u);
		} else {
			this.frame(g, s, "_px.png", x, y, frame, argb(0xFFFFFF, alpha), mirror, u);
		}
		if (it.clip() != null) {
			g.disableScissor();
		}
	}

	private void frame(GuiGraphicsExtractor g, Strip s, String suffix, double x, double y, int frame, int colour, boolean mirror, double u) {
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
		g.blit(RenderPipelines.GUI_TEXTURED, t.id(), X, Y, frame * sw, 0, W, H, sw, t.h(), t.w(), t.h(), colour);
		if (mirror) {
			g.pose().popMatrix();
		}
	}
}
