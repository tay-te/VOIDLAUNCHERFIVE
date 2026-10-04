package dev.voidmc.hearth.client.standing;

import dev.voidmc.hearth.api.StandingService.Title;
import dev.voidmc.hearth.standing.logic.Titles;
import dev.voidmc.hearth.standing.net.StandingPayloads;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

/**
 * Under the sim's arrival banner ("Ashbrook — a timber-framed village of 34 souls"), a line saying where the player stands
 * there — "a Friend of Ashbrook · standing +42" in the title's colour, and how known they are around here — fading in and
 * out with the banner. Drawn from the server's {@link StandingPayloads.Here} only.
 */
final class StandingHud {
	private static final long FADE_IN = 600;
	private static final long HOLD = 4200;
	private static final long FADE_OUT = 1600;
	/** Below the banner's title and subtitle (which sit at a quarter of the screen height, +6 for the subtitle). */
	private static final int OFFSET = 21;

	private StandingHud() {
	}

	static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		StandingPayloads.Here p = ClientStanding.shown();
		Minecraft mc = Minecraft.getInstance();
		if (p == null || mc.gui.hud.isHidden()) {
			return;
		}
		long age = Util.getMillis() - ClientStanding.shownAt();
		if (age > FADE_IN + HOLD + FADE_OUT) {
			ClientStanding.hide();
			return;
		}
		float alpha = age < FADE_IN ? age / (float) FADE_IN : age < FADE_IN + HOLD ? 1 : 1 - (age - FADE_IN - HOLD) / (float) FADE_OUT;
		int a = Math.max(5, Math.min(255, (int) (alpha * 255)));
		Font font = mc.font;
		Title title = ClientStanding.title(p.title());
		Component line = Component.translatable("hearth.standing.title_phrase." + title.getSerializedName(), p.town())
			.append(Component.literal("  ·  "))
			.append(Component.translatable("hearth.standing.hud.standing", ClientStanding.signed(p.standing())));
		int cx = g.guiWidth() / 2;
		int y = g.guiHeight() / 4 + OFFSET;
		int colour = Titles.colour(title);
		g.centeredText(font, line, cx, y, ARGB.color(a, ARGB.red(colour), ARGB.green(colour), ARGB.blue(colour)));
		if (p.fame() >= 5) {
			g.centeredText(font, ClientStanding.fame(p.fame(), p.renown()), cx, y + font.lineHeight + 2, ARGB.color(a, 0xB8, 0xAC, 0x90));
		}
	}
}
