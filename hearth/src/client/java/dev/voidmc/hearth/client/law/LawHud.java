package dev.voidmc.hearth.client.law;

import dev.voidmc.hearth.law.net.LawPayloads;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The law on the HUD, drawn only from what the server sent:
 * <ul>
 *   <li>KCD's red hand beside the crosshair when it rests on something that belongs to someone ("Owned by Edda Miller"),
 *       with the lock (how good, locked or not) and whether you carry the key;</li>
 *   <li>the sneaking eye above the hotbar while crouched in a town: shut when unseen, opening as folk can see you,
 *       flashing red when a witness saw a crime;</li>
 *   <li>a line at the top while wanted, an outlaw, under suspicion, or serving a sentence.</li>
 * </ul>
 */
final class LawHud implements HudElement {
	static LawPayloads.Hint hint = LawPayloads.Hint.NONE;
	static LawPayloads.Status status = LawPayloads.Status.CLEAR;
	private static boolean stealthActive;
	private static float visibility;
	private static float shownVisibility;
	private static long noticedAt = Long.MIN_VALUE;
	private static long statusAt;
	private static float hintFade;

	static void stealth(LawPayloads.Stealth s) {
		stealthActive = s.active();
		visibility = s.visibility();
		if (s.noticed()) {
			noticedAt = System.currentTimeMillis();
		}
	}

	static void status(LawPayloads.Status s) {
		status = s;
		statusAt = System.currentTimeMillis();
	}

	static void reset() {
		hint = LawPayloads.Hint.NONE;
		status = LawPayloads.Status.CLEAR;
		stealthActive = false;
		visibility = 0;
		noticedAt = Long.MIN_VALUE;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gui.hud.isHidden() || mc.gui.screen() != null) {
			return;
		}
		Font font = mc.font;
		int w = g.guiWidth();
		int h = g.guiHeight();
		drawHint(g, font, w / 2, h / 2);
		drawEye(g, w / 2, h);
		drawStatus(g, font, w);
	}

	private static void drawHint(GuiGraphicsExtractor g, Font font, int cx, int cy) {
		boolean on = !hint.owner().getString().isEmpty();
		hintFade = Mth.approach(hintFade, on ? 1.0F : 0.0F, 0.2F);
		if (hintFade <= 0.02F || !on) {
			return;
		}
		int alpha = (int) (hintFade * 255) << 24;
		int x = cx + 9;
		int y = cy + 7;
		Component line;
		int color;
		if (hint.yours()) {
			line = Component.translatable("hearth.law.hud.yours");
			color = LawGui.GREEN;
		} else {
			LawGui.sprite(g, x - 2, y - 3, 0, 0, 16, 16);
			line = Component.translatable("hearth.law.hud.owned", hint.owner());
			color = 0xFFF0D8D0;
		}
		int tx = hint.yours() ? x : x + 16;
		g.fill(tx - 2, y, tx + font.width(line) + 2, y + 10, 0x60000000);
		g.text(font, line, tx, y + 1, color & 0xFFFFFF | alpha, true);
		if (hint.lockTier() > 0) {
			int ly = y + 12;
			Component tier = Component.translatable("hearth.law.lock.tier." + hint.lockTier());
			Component lockLine = hint.locked() ? Component.translatable("hearth.law.hud.locked", tier) : Component.translatable("hearth.law.hud.unlocked", tier);
			LawGui.sprite(g, tx, ly, hint.locked() ? 16 : 25, 0, 9, 11);
			g.text(font, lockLine, tx + 12, ly + 2, (hint.locked() ? LawGui.GOLD : LawGui.TEXT_DIM) & 0xFFFFFF | alpha, true);
			if (hint.hasKey()) {
				int ky = ly + 12;
				LawGui.sprite(g, tx, ky + 1, 34, 0, 9, 6);
				g.text(font, Component.translatable("hearth.law.hud.have_key"), tx + 12, ky, LawGui.GREEN & 0xFFFFFF | alpha, true);
			}
		}
	}

	private static void drawEye(GuiGraphicsExtractor g, int cx, int h) {
		long now = System.currentTimeMillis();
		boolean flashing = now - noticedAt < 1600;
		if (!stealthActive && !flashing) {
			shownVisibility = 0;
			return;
		}
		shownVisibility = Mth.lerp(0.25F, shownVisibility, visibility);
		int x = cx - 8;
		int y = h - 62;
		int frame;
		if (flashing && (now - noticedAt) / 200 % 2 == 0) {
			frame = 3;
		} else if (shownVisibility < 0.08F) {
			frame = 0;
		} else if (shownVisibility < 0.45F) {
			frame = 1;
		} else {
			frame = 2;
		}
		g.fill(x - 2, y - 1, x + 18, y + 13, 0x50000000);
		LawGui.sprite(g, x, y, frame * 16, 16, 16, 10);
		// how plainly they see you: a thin bar under the eye
		int bar = Math.round(16 * Math.clamp(shownVisibility, 0.0F, 1.0F));
		g.fill(x, y + 10, x + 16, y + 11, 0x80202020);
		g.fill(x, y + 10, x + bar, y + 11, LawGui.mix(0xFF6FBF5A, LawGui.RED, shownVisibility));
	}

	private static void drawStatus(GuiGraphicsExtractor g, Font font, int w) {
		LawPayloads.Status s = status;
		Component line = null;
		int color = LawGui.RED;
		if (s.jailTicks() > 0) {
			int left = Math.max(0, s.jailTicks() - (int) ((System.currentTimeMillis() - statusAt) / 50));
			line = Component.translatable(s.pillory() ? "hearth.law.hud.pillory" : "hearth.law.hud.jail", s.settlement(), LawGui.clock(left));
			color = LawGui.GOLD;
		} else if (s.outlaw()) {
			line = Component.translatable("hearth.law.hud.outlaw_in", s.settlement());
			color = 0xFFFF3030;
		} else if (s.bounty() > 0) {
			line = Component.translatable("hearth.law.hud.wanted", s.settlement(), s.bounty());
		} else if (s.suspicion() >= 40) {
			line = Component.translatable("hearth.law.hud.suspected", s.settlement());
			color = 0xFFE0A040;
		}
		if (line == null) {
			return;
		}
		int tw = font.width(line);
		int x = (w - tw) / 2;
		int y = 26;
		g.fill(x - 6, y - 3, x + tw + 6, y + 11, 0x90000000);
		g.fill(x - 6, y - 3, x + tw + 6, y - 2, color & 0xFFFFFF | 0xC0000000);
		g.text(font, line, x, y, color, true);
	}
}
