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
 * The tracked quest on the HUD, top right: its title beside a small compass whose needle points to where the quest
 * wants you (the place, the landmark, the destination town, or the giver's town to hand in) with the distance, and the
 * open objectives under it with their progress. Drawn from the synced quest data only; it costs a few text draws.
 */
final class QuestHud implements HudElement {
	private static final int WIDTH = 168;
	private static final int MARGIN = 6;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.gui.hud.isHidden()) {
			return;
		}
		PlayerQuests pq = QuestStore.get(player);
		if (pq.tracked == null) {
			return;
		}
		QuestInstance q = pq.get(pq.tracked);
		if (q == null) {
			return;
		}
		Font font = mc.font;
		int x = g.guiWidth() - WIDTH - MARGIN;
		int y = MARGIN + 18;
		List<FormattedCharSequence> title = font.split(Component.literal(q.text.title()), WIDTH - 26);
		List<Objective> open = q.currentObjectives();
		int lines = Math.min(title.size(), 2) + (q.status == QuestStatus.READY ? 1 : Math.min(open.size(), 3));
		int h = Math.max(28, 6 + lines * (font.lineHeight + 1) + 4);
		g.fill(x - 3, y - 3, x + WIDTH + 3, y + h, 0x70000000);
		g.fill(x - 3, y - 3, x - 2, y + h, 0xC0E0B050);

		BlockPos target = QuestUi.waypoint(q);
		this.compass(g, font, player, target, x + 8, y + 7, delta.getGameTimeDeltaPartialTick(false));
		int ty = y;
		for (int i = 0; i < title.size() && i < 2; i++) {
			g.text(font, title.get(i), x + 20, ty, 0xFFFFD86B, true);
			ty += font.lineHeight + 1;
		}
		ty += 2;
		if (q.status == QuestStatus.READY) {
			String back = Component.translatable("hearth.quest.hud.return", NoticeBoardScreen.cap(q.giver.name()), q.settlementName).getString();
			g.text(font, font.plainSubstrByWidth(back, WIDTH - 4), x + 2, ty, 0xFF8EE08E, true);
			return;
		}
		for (int i = 0; i < open.size() && i < 3; i++) {
			String line = "• " + QuestUi.objectiveLine(open.get(i), player);
			g.text(font, font.plainSubstrByWidth(line, WIDTH - 4), x + 2, ty, 0xFFE8E8E8, true);
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
