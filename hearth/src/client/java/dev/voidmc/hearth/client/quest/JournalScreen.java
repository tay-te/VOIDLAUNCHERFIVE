package dev.voidmc.hearth.client.quest;

import dev.voidmc.hearth.quest.model.Objective;
import dev.voidmc.hearth.quest.model.PlayerQuests;
import dev.voidmc.hearth.quest.model.QuestInstance;
import dev.voidmc.hearth.quest.model.QuestStatus;
import dev.voidmc.hearth.quest.net.QuestPayloads;
import dev.voidmc.hearth.quest.service.QuestStore;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

/**
 * The quest journal: a leather-bound book open on two pages. The left page lists the active quests (or the finished
 * ones); the right page shows the one picked: who asked and where, what they said, each objective with its progress,
 * the reward and any deadline, with buttons to track it on the HUD or abandon it. It reads the player's synced quest
 * data each frame, so it updates as quests progress.
 */
public class JournalScreen extends Screen {
	private static final int W = 320;
	private static final int H = 200;
	private static final int ROW = 22;
	private static final int ROWS = 7;
	private boolean history;
	private @Nullable UUID selected;
	private int scroll;
	private int left;
	private int top;
	private @Nullable Button track;
	private @Nullable Button abandon;
	private boolean confirmAbandon;

	public JournalScreen() {
		super(Component.translatable("item.hearth.journal"));
	}

	private PlayerQuests quests() {
		return this.minecraft.player == null ? new PlayerQuests() : QuestStore.get(this.minecraft.player);
	}

	@Override
	protected void init() {
		this.left = (this.width - W) / 2;
		this.top = (this.height - H) / 2;
		PlayerQuests pq = this.quests();
		if (this.selected == null || pq.get(this.selected) == null) {
			QuestInstance first = pq.trackedOrFirst();
			this.selected = first == null ? null : first.id;
		}
		int pageX = this.left + W / 2 + 6;
		this.track = this.addRenderableWidget(Button.builder(Component.translatable("hearth.quest.journal.track"), b -> this.act(QuestPayloads.JournalAction.Action.TRACK))
			.bounds(pageX + 4, this.top + H - 30, 66, 18).build());
		this.abandon = this.addRenderableWidget(Button.builder(Component.translatable("hearth.quest.journal.abandon"), b -> {
			if (this.confirmAbandon) {
				this.act(QuestPayloads.JournalAction.Action.ABANDON);
				this.confirmAbandon = false;
			} else {
				this.confirmAbandon = true;
			}
		}).bounds(pageX + 74, this.top + H - 30, 70, 18).build());
		this.addRenderableWidget(Button.builder(Component.translatable("hearth.quest.journal.active"), b -> {
			this.history = false;
			this.scroll = 0;
		}).bounds(this.left + 12, this.top + H - 30, 66, 18).build());
		this.addRenderableWidget(Button.builder(Component.translatable("hearth.quest.journal.history"), b -> {
			this.history = true;
			this.scroll = 0;
		}).bounds(this.left + 82, this.top + H - 30, 66, 18).build());
	}

	private void act(QuestPayloads.JournalAction.Action action) {
		if (this.selected != null) {
			ClientPlayNetworking.send(new QuestPayloads.JournalAction(this.selected, action));
		}
	}

	@Override
	public void tick() {
		PlayerQuests pq = this.quests();
		QuestInstance q = this.selected == null ? null : pq.get(this.selected);
		if (this.track != null && this.abandon != null) {
			boolean show = !this.history && q != null;
			this.track.visible = show;
			this.abandon.visible = show;
			if (q != null) {
				this.track.setMessage(Component.translatable(q.id.equals(pq.tracked) ? "hearth.quest.journal.untrack" : "hearth.quest.journal.track"));
				this.abandon.setMessage(Component.translatable(this.confirmAbandon ? "hearth.quest.journal.confirm" : "hearth.quest.journal.abandon"));
			}
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		// Leather boards, two cream pages and the gutter between them.
		g.fill(this.left - 4, this.top - 4, this.left + W + 4, this.top + H + 4, 0xFF3A2414);
		g.fill(this.left - 3, this.top - 3, this.left + W + 3, this.top + H + 3, 0xFF6B4226);
		g.fill(this.left - 1, this.top - 1, this.left + W + 1, this.top + H + 1, 0xFF57351E);
		int mid = this.left + W / 2;
		g.fill(this.left + 4, this.top + 4, mid - 1, this.top + H - 4, QuestUi.PAPER);
		g.fill(mid + 1, this.top + 4, this.left + W - 4, this.top + H - 4, QuestUi.PAPER);
		g.fillGradient(mid - 8, this.top + 4, mid - 1, this.top + H - 4, 0x00000000, 0x00000000);
		for (int i = 0; i < 6; i++) {
			int alpha = 0x22 - i * 5;
			g.fill(mid - 1 - i, this.top + 4, mid - i, this.top + H - 4, alpha << 24);
			g.fill(mid + 1 + i, this.top + 4, mid + 2 + i, this.top + H - 4, alpha << 24);
		}
		// Page edges at the outer sides.
		g.fill(this.left + 4, this.top + H - 5, mid - 1, this.top + H - 4, QuestUi.PAPER_EDGE);
		g.fill(mid + 1, this.top + H - 5, this.left + W - 4, this.top + H - 4, QuestUi.PAPER_EDGE);
		// A ribbon bookmark hanging out below the gutter.
		g.fill(mid - 2, this.top + H - 4, mid + 2, this.top + H + 12, 0xFF9E2328);
		g.fill(mid - 2, this.top + H + 12, mid, this.top + H + 14, 0xFF9E2328);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		PlayerQuests pq = this.quests();
		int lx = this.left + 12;
		int ly = this.top + 12;
		g.text(this.font, Component.translatable(this.history ? "hearth.quest.journal.history_title" : "hearth.quest.journal.active_title",
			this.history ? pq.history.size() : pq.active.size()).getString(), lx, ly, QuestUi.INK, false);
		g.fill(lx, ly + 11, this.left + W / 2 - 10, ly + 12, 0x403B2A1A);
		if (this.history) {
			this.drawHistory(g, pq, lx, ly + 16);
			return;
		}
		List<QuestInstance> active = pq.active;
		if (active.isEmpty()) {
			QuestUi.wrapped(g, this.font, Component.translatable("hearth.quest.journal.none").getString(), lx, ly + 20, W / 2 - 24, QuestUi.INK_FADED, 6);
		}
		for (int i = this.scroll; i < active.size() && i < this.scroll + ROWS; i++) {
			QuestInstance q = active.get(i);
			int y = ly + 16 + (i - this.scroll) * ROW;
			boolean sel = q.id.equals(this.selected);
			boolean hover = mouseX >= lx - 2 && mouseX < this.left + W / 2 - 8 && mouseY >= y - 2 && mouseY < y + ROW - 2;
			if (sel || hover) {
				g.fill(lx - 2, y - 2, this.left + W / 2 - 8, y + ROW - 3, sel ? 0x30805A2A : 0x18805A2A);
			}
			g.fakeItem(QuestUi.icon(q.type), lx, y);
			int color = q.status == QuestStatus.READY ? QuestUi.INK_GREEN : QuestUi.INK;
			g.text(this.font, this.font.plainSubstrByWidth(q.text.title(), W / 2 - 48), lx + 19, y + 1, color, false);
			String sub = q.status == QuestStatus.READY ? Component.translatable("hearth.quest.journal.ready").getString() : q.settlementName;
			g.text(this.font, this.font.plainSubstrByWidth(sub, W / 2 - 48), lx + 19, y + 10, QuestUi.INK_FADED, false);
			if (q.id.equals(pq.tracked)) {
				g.text(this.font, "▶", this.left + W / 2 - 18, y + 5, QuestUi.INK_GOLD, false);
			}
		}
		QuestInstance q = this.selected == null ? null : pq.get(this.selected);
		if (q != null) {
			this.drawQuest(g, q);
		}
	}

	private void drawQuest(GuiGraphicsExtractor g, QuestInstance q) {
		int x = this.left + W / 2 + 10;
		int w = W / 2 - 22;
		int y = this.top + 12;
		y = QuestUi.wrapped(g, this.font, q.text.title(), x, y, w, QuestUi.INK, 2) + 1;
		g.text(this.font, this.font.plainSubstrByWidth(NoticeBoardScreen.cap(q.giver.name()) + " · " + q.settlementName, w), x, y, QuestUi.INK_FADED, false);
		y += this.font.lineHeight + 4;
		String words = q.status == QuestStatus.READY
			? Component.translatable("hearth.quest.journal.return", NoticeBoardScreen.cap(q.giver.name()), q.settlementName).getString()
			: q.text.summary();
		y = QuestUi.wrapped(g, this.font, words, x, y, w, QuestUi.INK, 5) + 4;
		for (Objective o : q.objectives) {
			int color = o.done() ? QuestUi.INK_GREEN : QuestUi.INK;
			String box = o.done() ? "☑ " : "☐ ";
			y = QuestUi.wrapped(g, this.font, box + QuestUi.objectiveLine(o, this.minecraft.player), x, y, w, color, 3) + 1;
		}
		y += 3;
		if (!q.reward.text().isEmpty()) {
			y = QuestUi.wrapped(g, this.font, Component.translatable("hearth.quest.reward.line", q.reward.text()).getString(), x, y, w, QuestUi.INK_GREEN, 2);
		}
		String left = QuestUi.timeLeft(q, QuestUi.gameTime());
		if (!left.isEmpty() && y < this.top + H - 42) {
			g.text(this.font, left, x, y, QuestUi.INK_GOLD, false);
		}
	}

	private void drawHistory(GuiGraphicsExtractor g, PlayerQuests pq, int x, int y) {
		if (pq.history.isEmpty()) {
			QuestUi.wrapped(g, this.font, Component.translatable("hearth.quest.journal.nohistory").getString(), x, y + 4, W / 2 - 24, QuestUi.INK_FADED, 6);
		}
		int rows = 0;
		for (int i = this.scroll; i < pq.history.size() && rows < 16; i++, rows++) {
			PlayerQuests.QuestRecord r = pq.history.get(i);
			int color = switch (r.outcome()) {
				case COMPLETED -> QuestUi.INK_GREEN;
				case FAILED, ABANDONED -> QuestUi.INK_RED;
				default -> QuestUi.INK;
			};
			String mark = r.outcome() == QuestStatus.COMPLETED ? "✔ " : "✖ ";
			int row = y + rows * 10;
			if (row > this.top + H - 40) {
				break;
			}
			g.text(this.font, this.font.plainSubstrByWidth(mark + r.title(), W / 2 - 26), x, row, color, false);
		}
		// The right page sums the record up.
		int rx = this.left + W / 2 + 10;
		long done = pq.history.stream().filter(r -> r.outcome() == QuestStatus.COMPLETED).count();
		int total = pq.completions.values().stream().mapToInt(Integer::intValue).sum();
		int ry = QuestUi.wrapped(g, this.font, Component.translatable("hearth.quest.journal.record", total).getString(), rx, this.top + 12, W / 2 - 22,
			QuestUi.INK, 3) + 4;
		QuestUi.wrapped(g, this.font, Component.translatable("hearth.quest.journal.recent", done, pq.history.size()).getString(), rx, ry, W / 2 - 22,
			QuestUi.INK_FADED, 3);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (!this.history && event.button() == 0) {
			PlayerQuests pq = this.quests();
			int lx = this.left + 12;
			int ly = this.top + 28;
			for (int i = this.scroll; i < pq.active.size() && i < this.scroll + ROWS; i++) {
				int y = ly + (i - this.scroll) * ROW;
				if (event.x() >= lx - 2 && event.x() < this.left + W / 2 - 8 && event.y() >= y - 2 && event.y() < y + ROW - 2) {
					this.selected = pq.active.get(i).id;
					this.confirmAbandon = false;
					this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		int size = this.history ? this.quests().history.size() : this.quests().active.size();
		this.scroll = Math.max(0, Math.min(Math.max(0, size - ROWS), this.scroll - (int) Math.signum(scrollY)));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (QuestClient.isJournalKey(event)) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}
}
