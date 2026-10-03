package dev.voidmc.hearth.client.sim;

import dev.voidmc.hearth.sim.net.ArrivalPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * The banner that greets a player entering a settlement — "Ashbrook", and under it "a timber-framed village of 34
 * souls" — fading in, holding, and fading out, like a place name on an old map. Everything it shows came from the
 * server in an {@link ArrivalPayload}; the client decides nothing.
 */
public final class ArrivalBanner {
	private static final long FADE_IN = 600;
	private static final long HOLD = 4200;
	private static final long FADE_OUT = 1600;

	private static @Nullable ArrivalPayload current;
	private static @Nullable ArrivalPayload shown;
	private static long shownAt;
	private static long lastShownId = Long.MIN_VALUE;
	private static long lastShownTime;

	private ArrivalBanner() {
	}

	/** The settlement the player stands in, as the server last said (for the F3 line). */
	public static @Nullable ArrivalPayload current() {
		return current;
	}

	static void receive(ArrivalPayload payload) {
		current = payload.left() ? null : payload;
		if (payload.left()) {
			return;
		}
		long now = Util.getMillis();
		// stepping back and forth over the edge of town should not greet you every time
		if (payload.id() == lastShownId && now - lastShownTime < 60_000) {
			return;
		}
		shown = payload;
		shownAt = now;
		lastShownId = payload.id();
		lastShownTime = now;
	}

	static void reset() {
		current = null;
		shown = null;
		lastShownId = Long.MIN_VALUE;
	}

	static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		ArrivalPayload p = shown;
		Minecraft mc = Minecraft.getInstance();
		if (p == null) {
			return;
		}
		long age = Util.getMillis() - shownAt;
		if (age > FADE_IN + HOLD + FADE_OUT) {
			shown = null;
			return;
		}
		float alpha = age < FADE_IN ? age / (float) FADE_IN : age < FADE_IN + HOLD ? 1 : 1 - (age - FADE_IN - HOLD) / (float) FADE_OUT;
		int a = Math.max(5, Math.min(255, (int) (alpha * 255)));
		Font font = mc.font;
		int cx = g.guiWidth() / 2;
		int y = g.guiHeight() / 4;
		Component title = Component.literal(p.name());
		Component subtitle = Component.translatable(p.population() == 1 ? "hearth.sim.banner.subtitle.one" : "hearth.sim.banner.subtitle",
			Component.translatable("hearth.sim.banner.flavour." + p.culture()), Component.translatable("hearth.sim.banner.tier." + p.tier()), p.population());
		g.pose().pushMatrix();
		g.pose().translate(cx, y);
		g.pose().scale(2.5F, 2.5F);
		g.text(font, title, -font.width(title) / 2, -font.lineHeight, ARGB.color(a, 0xF2, 0xE6, 0xC8), true);
		g.pose().popMatrix();
		g.pose().pushMatrix();
		g.pose().translate(cx, y + 6);
		g.text(font, subtitle, -font.width(subtitle) / 2, 0, ARGB.color(a, 0xC8, 0xBC, 0xA0), true);
		g.pose().popMatrix();
	}
}
