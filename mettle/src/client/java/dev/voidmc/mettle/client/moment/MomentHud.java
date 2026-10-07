package dev.voidmc.mettle.client.moment;

import dev.voidmc.mettle.character.moment.MomentKind;
import dev.voidmc.mettle.character.moment.MomentQueue;
import dev.voidmc.mettle.client.MettleClient;
import dev.voidmc.mettle.client.mixin.character.BossHealthOverlayAccessor;
import dev.voidmc.mettle.client.overlay.OverlayBridge;
import dev.voidmc.mettle.client.ui.kit.Ui;
import dev.voidmc.mettle.client.ui.kit.UiCanvas;
import dev.voidmc.mettle.client.ui.kit.UiCanvas.Weight;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.HumanoidArm;

/**
 * The two HUD pieces of progress moments (UI.md §6b), drawn at native resolution with the UI kit so Outfit stays crisp:
 * <ul>
 *   <li>the <b>toast</b>, top centre below any boss bars: a flat rounded dark panel, the skill's (or perk's) icon in a
 *   well tinted with its accent, a title ("Mining 9 · Apprentice Miner") and a small second line. It slides down and
 *   fades in, and fades out;</li>
 *   <li>the <b>points nudge</b>, just past the hotbar's right end: "◆ 3 points  K" for a while after a level-up.</li>
 * </ul>
 * Hidden while a screen is open (the queue waits) and while an overlay host draws the HUD instead.
 */
final class MomentHud {
	private MomentHud() {
	}

	static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.getConnection() == null || mc.gui.screen() != null || OverlayBridge.hasListener()) {
			return;
		}
		MomentQueue.Entry toast = MomentClient.QUEUE.current();
		float toastVis = MomentClient.QUEUE.visibility();
		float nudgeVis = MomentClient.nudgeVisibility(mc.player);
		if ((toast == null || toastVis <= 0.01f) && nudgeVis <= 0.01f) {
			return;
		}
		int screenH = mc.getWindow().getHeight();
		int m = Math.max(2, (int) Math.ceil(6f * screenH / 1080f - 0.15f));
		UiCanvas c = UiCanvas.begin(g, m / 6f);
		try {
			if (toast != null && toastVis > 0.01f) {
				toast(mc, c, toast, ease(toastVis));
				MomentClient.drawn();
			}
			if (nudgeVis > 0.01f) {
				nudge(mc, c, ease(nudgeVis));
			}
		} finally {
			c.end();
		}
	}

	// ---------------------------------------------------------------- toast

	private static void toast(Minecraft mc, UiCanvas c, MomentQueue.Entry e, float vis) {
		MomentText.Lines lines = MomentText.lines(mc, e.kind(), e.skill(), e.perk(), e.level(), e.tier(), e.rank());
		int screenW = mc.getWindow().getWidth();
		boolean strong = e.kind() == MomentKind.TIER || e.kind() == MomentKind.MASTERY || e.kind().earned();
		boolean twoLines = lines.subtitle() != null;

		float titleSize = 17f;
		float subSize = 13f;
		int pad = c.px(9);
		int well = c.px(twoLines ? 42 : 32);
		int h = well + 2 * pad;
		int textX = pad + well + c.px(12);
		int rightPad = c.px(18);
		int maxW = c.px(520);
		int minW = c.px(220);

		Component sep = Component.literal(" · ");
		int leadW = c.width(lines.lead(), Weight.MEDIUM, titleSize);
		int restW = lines.rest() == null ? 0 : c.width(sep, Weight.MEDIUM, titleSize) + c.width(lines.rest(), Weight.MEDIUM, titleSize);
		String sub = twoLines ? lines.subtitle().getString() : null;
		int subW = sub == null ? 0 : c.width(Component.literal(sub), Weight.REGULAR, subSize);
		int w = Math.clamp(textX + Math.max(leadW + restW, subW) + rightPad, minW, maxW);
		int textMax = w - textX - rightPad;
		if (sub != null && subW > textMax) {
			sub = ellipsize(c, sub, Weight.REGULAR, subSize, textMax);
		}

		int x = (screenW - w) / 2;
		int y = top(mc, c) - Math.round((1f - vis) * c.px(12));
		int r = c.px(14);

		if (strong) {
			// A halo around the panel for the bigger moments: a tier, a mastery, something earned.
			int o = c.px(3);
			c.stroke(x - o, y - o, w + 2 * o, h + 2 * o, r + o, Math.max(1, c.px(1.5)), fade(Ui.alpha(lines.accent(), 0.28f), vis));
		}
		c.round(x, y, w, h, r, fade(Ui.alpha(Ui.BASE, 0.94f), vis));
		c.stroke(x, y, w, h, r, Math.max(1, c.px(1)), fade(Ui.alpha(lines.accent(), strong ? 0.55f : 0.32f), vis));

		// The icon well, tinted with the accent and ringed with it.
		int wx = x + pad;
		int wy = y + pad;
		int wr = c.px(10);
		c.round(wx, wy, well, well, wr, fade(Ui.alpha(lines.accent(), 0.16f), vis));
		c.stroke(wx, wy, well, well, wr, Math.max(1, c.px(1)), fade(Ui.alpha(lines.accent(), 0.5f), vis));
		if (lines.icon() != null) {
			int k = Math.max(1, (int) Math.floor((well - c.px(8)) / 16f));
			int s = 16 * k;
			c.sprite(lines.icon(), wx + (well - s) / 2, wy + (well - s) / 2, s, s, 16, 16, fade(0xFFFFFFFF, vis));
		} else {
			diamond(c, wx + well / 2, wy + well / 2, Math.max(3, c.px(9)), fade(lines.accent(), vis));
		}

		if (vis < 0.08f) {
			return;
		}
		int tx = x + textX;
		int titleTop = twoLines ? y + c.px(9) : y + (h - c.fontPx(titleSize)) / 2 - c.px(1);
		int cx = tx + c.text(lines.lead(), Weight.MEDIUM, titleSize, tx, titleTop, fade(lines.accent(), vis));
		if (lines.rest() != null) {
			cx += c.text(sep, Weight.MEDIUM, titleSize, cx, titleTop, fade(Ui.TEXT_2, vis));
			Component rest = lines.rest();
			int room = x + w - rightPad - cx;
			if (c.width(rest, Weight.MEDIUM, titleSize) > room) {
				rest = Component.literal(ellipsize(c, rest.getString(), Weight.MEDIUM, titleSize, room));
			}
			c.text(rest, Weight.MEDIUM, titleSize, cx, titleTop, fade(Ui.TEXT, vis));
		}
		if (sub != null) {
			c.text(sub, Weight.REGULAR, subSize, tx, y + c.px(33), fade(Ui.TEXT_2, vis));
		}
	}

	/** Below the boss bars when there are any, else a little under the top edge. */
	private static int top(Minecraft mc, UiCanvas c) {
		int bars = ((BossHealthOverlayAccessor) mc.gui.hud.getBossOverlay()).mettle$events().size();
		if (bars == 0) {
			return c.px(16);
		}
		int guiH = mc.getWindow().getGuiScaledHeight();
		int bottomGui = Math.min(12 + 19 * (bars - 1) + 5, guiH / 3);
		return bottomGui * c.guiScale + c.px(10);
	}

	// ---------------------------------------------------------------- nudge

	private static void nudge(Minecraft mc, UiCanvas c, float vis) {
		int points = MomentClient.unspentPoints(mc.player);
		Component label = Component.translatable(points == 1 ? "mettle.moment.nudge.one" : "mettle.moment.nudge", points);
		Component key = MettleClient.characterKey.getTranslatedKeyMessage();
		int gs = c.guiScale;
		int guiW = mc.getWindow().getGuiScaledWidth();
		int guiH = mc.getWindow().getGuiScaledHeight();

		// Just past the hotbar's right end, clear of the offhand slot and the hotbar attack indicator when they sit there.
		HumanoidArm offhandArm = mc.player.getMainArm().getOpposite();
		int offset = 6;
		if (offhandArm == HumanoidArm.RIGHT && !mc.player.getOffhandItem().isEmpty()) {
			offset += 29;
		}
		if (offhandArm == HumanoidArm.LEFT && mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
			offset += 22;
		}
		int x = (guiW / 2 + 91 + offset) * gs;
		int centreY = (guiH - 11) * gs;

		float textSize = 14.5f;
		float keySize = 12f;
		int h = c.px(30);
		int padX = c.px(10);
		int d = Math.max(2, c.px(4.5));
		int labelW = c.width(label, Weight.MEDIUM, textSize);
		int keyW = Math.max(c.px(16), c.width(key, Weight.MEDIUM, keySize) + c.px(10));
		int keyH = c.px(19);
		int w = padX + 2 * d + c.px(7) + labelW + c.px(8) + keyW + c.px(6);
		int y = centreY - h / 2;

		c.round(x, y, w, h, c.px(8), fade(Ui.alpha(0x0A0B0C, 0.62f), vis));
		c.stroke(x, y, w, h, c.px(8), Math.max(1, c.px(1)), fade(Ui.white(0.08f), vis));
		int cx = x + padX;
		diamond(c, cx + d, centreY, d, fade(Ui.ACCENT, vis));
		cx += 2 * d + c.px(7);
		if (vis >= 0.08f) {
			c.text(label, Weight.MEDIUM, textSize, cx, centreY - c.fontPx(textSize) / 2 - c.px(1), fade(Ui.TEXT, vis));
		}
		cx += labelW + c.px(8);
		int ky = centreY - keyH / 2;
		c.round(cx, ky, keyW, keyH, c.px(5), fade(Ui.white(0.08f), vis));
		c.stroke(cx, ky, keyW, keyH, c.px(5), Math.max(1, c.px(1)), fade(Ui.white(0.28f), vis));
		if (vis >= 0.08f) {
			c.textCentred(key, Weight.MEDIUM, keySize, cx + keyW / 2, centreY - c.fontPx(keySize) / 2 - c.px(1), fade(Ui.TEXT_CHIP, vis));
		}
	}

	// ---------------------------------------------------------------- helpers

	/** A filled diamond of half-size {@code d} centred on (cx, cy), row by row (no rotated quads in the kit). */
	private static void diamond(UiCanvas c, int cx, int cy, int d, int argb) {
		for (int i = -d; i <= d; i++) {
			int half = d - Math.abs(i);
			c.fill(cx - half, cy + i, 2 * half + 1, 1, argb);
		}
	}

	private static String ellipsize(UiCanvas c, String text, Weight weight, float size, int maxWidth) {
		String dots = "...";
		for (int end = text.length(); end > 0; end--) {
			String s = text.substring(0, end).stripTrailing() + dots;
			if (c.width(Component.literal(s), weight, size) <= maxWidth) {
				return s;
			}
		}
		return dots;
	}

	private static int fade(int argb, float vis) {
		int a = (argb >>> 24) & 0xFF;
		return (Math.round(a * Math.clamp(vis, 0f, 1f)) << 24) | (argb & 0xFFFFFF);
	}

	private static float ease(float t) {
		t = Math.clamp(t, 0f, 1f);
		return t * t * (3f - 2f * t);
	}
}
