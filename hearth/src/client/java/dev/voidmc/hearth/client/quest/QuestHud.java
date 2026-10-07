package dev.voidmc.hearth.client.quest;

import dev.voidmc.hearth.quest.model.Objective;
import dev.voidmc.hearth.quest.model.PlayerQuests;
import dev.voidmc.hearth.quest.model.QuestInstance;
import dev.voidmc.hearth.quest.model.QuestStatus;
import dev.voidmc.hearth.quest.service.QuestStore;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * The tracked quest on the HUD, top left (top right belongs to effects and toasts): its title beside a small compass whose needle points to where the quest
 * wants you (the place, the landmark, the destination town, or the giver's town to hand in) with the distance, and the
 * open objectives under it with their progress. Drawn from the synced quest data only; it costs a few text draws.
 */
final class QuestHud implements HudElement {
	private static final int WIDTH = 168;
	private static final int MARGIN = 6;

	/** Where the panel is and what it shows, worked out the same way for drawing it and for telling a map its room. */
	record Layout(QuestInstance q, List<FormattedCharSequence> title, List<Objective> open, boolean compass, int x, int y, int h) {
		QuestHudHook.Rect rect() {
			return new QuestHudHook.Rect(this.x - 3, this.y - 3, this.x + WIDTH + 3, this.y + this.h);
		}
	}

	/** The panel as it is drawn now, or null when there is nothing to draw (no player, HUD hidden, no tracked quest). */
	static Layout layout(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.gui.hud.isHidden()) {
			return null;
		}
		PlayerQuests pq = QuestStore.get(player);
		if (pq.tracked == null) {
			return null;
		}
		QuestInstance q = pq.get(pq.tracked);
		if (q == null) {
			return null;
		}
		Font font = mc.font;
		int x = MARGIN + 3;
		int y = MARGIN + 3;
		// a map that already points at the quest covers the mini-compass: the text then takes its place
		boolean compass = !QuestHudHook.compassCovered();
		List<FormattedCharSequence> title = font.split(Component.literal(q.text.title()), WIDTH - (compass ? 26 : 10));
		List<Objective> open = q.currentObjectives();
		int lines = Math.min(title.size(), 2) + (q.status == QuestStatus.READY ? 1 : Math.min(open.size(), 3));
		int h = 6 + lines * (font.lineHeight + 1) + 4;
		return new Layout(q, title, open, compass, x, y, compass ? Math.max(28, h) : h);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Layout l = layout(mc);
		if (l == null) {
			return;
		}
		LocalPlayer player = mc.player;
		QuestInstance q = l.q();
		List<FormattedCharSequence> title = l.title();
		List<Objective> open = l.open();
		Font font = mc.font;
		int x = l.x();
		int y = l.y();
		int h = l.h();
		int tx = x + (l.compass() ? 20 : 4);
		int textWidth = WIDTH - (l.compass() ? 22 : 6);
		g.fill(x - 3, y - 3, x + WIDTH + 3, y + h, 0x70000000);
		g.fill(x - 3, y - 3, x - 2, y + h, 0xC0E0B050);
		g.fill(x + WIDTH + 2, y - 3, x + WIDTH + 3, y + h, 0x40E0B050);

		BlockPos target = QuestUi.waypoint(q);
		if (l.compass()) {
			this.compass(g, font, player, target, x + 8, y + 7, delta.getGameTimeDeltaPartialTick(false));
		}
		int ty = y;
		for (int i = 0; i < title.size() && i < 2; i++) {
			g.text(font, title.get(i), tx, ty, 0xFFFFD86B, true);
			ty += font.lineHeight + 1;
		}
		ty += 2;
		if (q.status == QuestStatus.READY) {
			String back = Component.translatable("hearth.quest.hud.return", NoticeBoardScreen.cap(q.giver.name()), q.settlementName).getString();
			g.text(font, font.plainSubstrByWidth(back, textWidth), tx, ty, 0xFF8EE08E, true);
			return;
		}
		for (int i = 0; i < open.size() && i < 3; i++) {
			String line = "• " + QuestUi.objectiveLine(open.get(i), player);
			g.text(font, font.plainSubstrByWidth(line, textWidth), tx, ty, 0xFFE8E8E8, true);
			ty += font.lineHeight + 1;
		}
	}

	/** A little dial: a needle toward the target relative to where the player faces, and the distance under it. */
	private void compass(GuiGraphicsExtractor g, Font font, LocalPlayer player, BlockPos target, int cx, int cy, float partial) {
		g.fill(cx - 7, cy - 7, cx + 7, cy + 7, 0xA0201810);
		g.fill(cx - 6, cy - 6, cx + 6, cy + 6, 0xC0E9DDBF);
		if (target == null) {
			g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFF3B2A1A);
			return;
		}
		double px = Mth.lerp(partial, player.xo, player.getX());
		double pz = Mth.lerp(partial, player.zo, player.getZ());
		double dx = target.getX() + 0.5 - px;
		double dz = target.getZ() + 0.5 - pz;
		double dist = Math.sqrt(dx * dx + dz * dz);
		float yaw = player.getViewYRot(partial) * Mth.DEG_TO_RAD;
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		float rel = (float) (Math.atan2(dz, dx) - Math.atan2(fz, fx));
		boolean here = dist < 4;
		int needle = here ? 0xFF2F6B2F : 0xFFB3262A;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		if (!here) {
			g.pose().rotate(rel);
		}
		g.fill(-1, -5, 1, 4, 0xFF3B2A1A);
		g.fill(-1, -6, 1, -5, needle);
		g.fill(-2, -5, 2, -4, needle);
		g.fill(-3, -4, 3, -3, needle);
		g.fill(-1, -3, 1, 0, needle);
		g.pose().popMatrix();
		String d = here ? "✔" : dist >= 1000 ? String.format(java.util.Locale.ROOT, "%.1fk", dist / 1000) : Integer.toString((int) dist);
		g.centeredText(font, d, cx, cy + 9, 0xFFE8E8E8);
	}
}
