package dev.voidmc.hub.screen;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import dev.voidmc.hub.VoidHub;
import dev.voidmc.hub.model.Bridge;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/**
 * Moving parts drawn natively over a still page, mod-neutral: the page reports each animated layer once as a rect
 * ({@code Bridge.FxItem}: id, kind, box, tint, alpha, delay, two kind-specific numbers, a clip) and Java draws it every
 * frame, so Ultralight only repaints on real changes (docs/HUB_UI.md: never animate with CSS). A layer keeps its start
 * time across messages by its id; a subclass draws each kind ({@link #layer}).
 *
 * <p>VOID Mettle's cards ({@link CardFx}) and VOID Hearth's minigames ({@code HearthFx}) are the two users. Textures
 * come from the hub's own resources ({@link #tex}), loaded once, released on a resource reload. Render thread only.
 */
public abstract class NativeFx {

	/** A loaded texture and its size in texels. */
	public record Tex(Identifier id, int w, int h) {
	}

	private static final Map<String, Optional<Tex>> TEXTURES = new HashMap<>();

	/**
	 * A texture under {@code assets/voidhub/<folder>/}, registered as {@code voidhub:<idPrefix>/<file minus .png>},
	 * loaded once (NEAREST for pixel art, LINEAR for soft art); null when missing.
	 */
	protected static Tex tex(String folder, String idPrefix, String file, boolean linear) {
		return TEXTURES.computeIfAbsent(idPrefix + "|" + file, k -> {
			Identifier res = Identifier.fromNamespaceAndPath(VoidHub.MOD_ID, folder + "/" + file);
			Optional<Resource> r = Minecraft.getInstance().getResourceManager().getResource(res);
			if (r.isEmpty()) {
				VoidHub.LOGGER.warn("VOID Hub: fx texture {}/{} is missing", folder, file);
				return Optional.empty();
			}
			try (InputStream in = r.get().open()) {
				NativeImage img = NativeImage.read(in);
				int w = img.getWidth();
				int h = img.getHeight();
				Identifier id = Identifier.fromNamespaceAndPath(VoidHub.MOD_ID, idPrefix + "/" + file.replace(".png", ""));
				Minecraft.getInstance().getTextureManager().register(id, new FxTexture(file, img, linear));
				return Optional.of(new Tex(id, w, h));
			} catch (Exception e) {
				VoidHub.LOGGER.warn("VOID Hub: could not load fx texture {}/{}", folder, file, e);
				return Optional.empty();
			}
		}).orElse(null);
	}

	/** A resource reload (F3+T, a pack change): every fx texture is released and loads again on first use. */
	public static void clearAllTextures() {
		var manager = Minecraft.getInstance().getTextureManager();
		for (Optional<Tex> t : TEXTURES.values()) {
			t.ifPresent(tex -> manager.release(tex.id()));
		}
		TEXTURES.clear();
	}

	private static final class FxTexture extends DynamicTexture {
		FxTexture(String name, NativeImage image, boolean linear) {
			super(() -> "VOID Hub fx " + name, image);
			this.sampler = RenderSystem.getSamplerCache().getClampToEdge(linear ? FilterMode.LINEAR : FilterMode.NEAREST);
		}
	}

	/** The page's current layers, in its order. */
	protected List<Bridge.FxItem> items = List.of();
	/** When each id was first seen (nanoTime). */
	protected final Map<String, Long> seen = new HashMap<>();

	public boolean active() {
		return !this.items.isEmpty();
	}

	/** The page's new set of layers. Ids it kept keep their start. */
	public void set(List<Bridge.FxItem> next) {
		long now = System.nanoTime();
		Map<String, Bridge.FxItem> kept = new HashMap<>();
		for (Bridge.FxItem it : next) {
			kept.put(it.id(), it);
		}
		this.dropped(this.items, kept, now);
		this.seen.keySet().retainAll(kept.keySet());
		for (Bridge.FxItem it : next) {
			this.seen.putIfAbsent(it.id(), now);
		}
		this.items = next;
	}

	/** Hook: before {@link #set} forgets the layers the page dropped. */
	protected void dropped(List<Bridge.FxItem> old, Map<String, Bridge.FxItem> kept, long now) {
	}

	public void clear() {
		this.items = List.of();
		this.seen.clear();
	}

	/** The current layers (tests). */
	public List<Bridge.FxItem> items() {
		return this.items;
	}

	protected static double ms(long nanos) {
		return nanos / 1_000_000.0;
	}

	protected static int argb(int rgb, double alpha) {
		return ((int) Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24) | (rgb & 0xFFFFFF);
	}

	/** The layer's alpha faded in over {@code fadeMs} from its start. */
	protected static double fade(Bridge.FxItem it, double age, double fadeMs) {
		return fadeMs <= 0 ? it.alpha() : it.alpha() * Math.min(1, age / fadeMs);
	}

	/**
	 * Draws every layer over the page, in physical px ({@code u}: physical px per CSS px; the hub texture's size for
	 * kinds that copy the page's pixels). Called inside the screen's GUI pose; this scales to physical px itself.
	 */
	public void extract(GuiGraphicsExtractor g, double u, int gs, int hubW, int hubH) {
		if (!this.active()) {
			return;
		}
		long now = System.nanoTime();
		double clock = ms(now);
		g.pose().pushMatrix();
		g.pose().scale(1.0F / gs, 1.0F / gs);
		for (Bridge.FxItem it : this.items) {
			long start = this.seen.getOrDefault(it.id(), now);
			double age = ms(now - start) - it.delay();
			if (age < 0) {
				continue;
			}
			g.pose().pushMatrix();
			this.layer(g, it, age, clock, u);
			g.pose().popMatrix();
		}
		g.pose().popMatrix();
	}

	/** Draws one layer (physical px; {@code age} ms since it started, {@code clock} a global ms clock). */
	protected abstract void layer(GuiGraphicsExtractor g, Bridge.FxItem it, double age, double clock, double u);
}
