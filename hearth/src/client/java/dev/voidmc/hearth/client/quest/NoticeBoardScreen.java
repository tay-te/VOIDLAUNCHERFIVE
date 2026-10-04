package dev.voidmc.hearth.client.quest;

import dev.voidmc.hearth.quest.model.Objective;
import dev.voidmc.hearth.quest.model.QuestInstance;
import dev.voidmc.hearth.quest.net.QuestPayloads;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

/**
 * A settlement's notice board, up close: a timber board with the offers pinned to it as papers, each with its title,
 * who asks and what it pays. Click a paper to read it in full and take it. A hand-in button appears when the player
 * carries something for this town. Wanted posters (players the law wants here) hang in a column of their own at the
 * board's right. Everything shown came from the server; buttons send intents back.
 */
public class NoticeBoardScreen extends Screen {
	private static final int W = 336;
	private static final int H = 220;
	private static final int CARD_W = 96;
	private static final int CARD_H = 70;
	/** The poster column's width, when the board has wanted posters. */
	private static final int POSTERS_W = 92;
	private static final int POSTER_H = 56;
	private QuestPayloads.Board board;
	private QuestPayloads.Board.@Nullable Entry reading;
	private int left;
	private int top;

	public NoticeBoardScreen(QuestPayloads.Board board) {
		super(Component.literal(board.settlementName()));
		this.board = board;
	}

	boolean isFor(QuestPayloads.Board other) {
		return other.settlementId() == this.board.settlementId() && other.boardPos().equals(this.board.boardPos());
	}

	void update(QuestPayloads.Board fresh) {
		this.board = fresh;
		if (this.reading != null) {
			UUID id = this.reading.quest().id;
			this.reading = fresh.entries().stream().filter(e -> e.quest().id.equals(id)).findFirst().orElse(null);
		}
		this.rebuildWidgets();
	}

	/** Opens the first offer for reading (the dev tour's stand-in for a click). */
	void readFirst() {
		if (!this.board.entries().isEmpty()) {
			this.reading = this.board.entries().getFirst();
			this.rebuildWidgets();
		}
	}

	/** The board's full width: the offers, and the poster column when there are posters. */
	private int boardWidth() {
		return W + (this.board.posters().isEmpty() ? 0 : POSTERS_W);
	}

	@Override
	protected void init() {
		this.left = (this.width - this.boardWidth()) / 2;
		this.top = (this.height - H) / 2;
		int by = this.top + H - 26;
		if (this.reading == null) {
			if (!this.board.handIns().isEmpty()) {
				this.addRenderableWidget(Button.builder(Component.translatable("hearth.quest.board.handin", this.board.handIns().size()),
					b -> this.send(QuestPayloads.BoardAction.Action.HAND_IN, null)).bounds(this.left + 14, by, 120, 20).build());
			}
			this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
				.bounds(this.left + W - 94, by, 80, 20).build());
		} else {
			QuestInstance q = this.reading.quest();
			Button accept = Button.builder(Component.translatable("hearth.quest.board.accept"),
				b -> this.send(QuestPayloads.BoardAction.Action.ACCEPT, q.id)).bounds(this.left + W / 2 - 104, by, 100, 20).build();
			accept.active = this.reading.lock().isEmpty();
			this.addRenderableWidget(accept);
			this.addRenderableWidget(Button.builder(Component.translatable("hearth.quest.board.back"), b -> {
				this.reading = null;
				this.rebuildWidgets();
			}).bounds(this.left + W / 2 + 4, by, 100, 20).build());
		}
	}

	private void send(QuestPayloads.BoardAction.Action action, @Nullable UUID quest) {
		ClientPlayNetworking.send(new QuestPayloads.BoardAction(this.board.settlementId(), this.board.boardPos(), action, Optional.ofNullable(quest)));
		if (action == QuestPayloads.BoardAction.Action.ACCEPT) {
			this.reading = null;
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		// Frame of dark oak round a spruce board, a little roof strip on top: the board as it stands in the plaza.
		int bw = this.boardWidth();
		g.fill(this.left - 3, this.top - 3, this.left + bw + 3, this.top + H + 3, 0xAA000000);
		QuestUi.wood(g, QuestUi.FRAME, this.left, this.top, bw, H);
		QuestUi.wood(g, QuestUi.PLANKS, this.left + 8, this.top + 24, W - 16, H - 56);
		if (bw > W) {
			QuestUi.wood(g, QuestUi.PLANKS, this.left + W - 4, this.top + 24, POSTERS_W - 4, H - 56);
		}
		g.fill(this.left + 8, this.top + 24, this.left + W - 8, this.top + 25, 0x55000000);
		g.fill(this.left + 8, this.top + H - 33, this.left + W - 8, this.top + H - 32, 0x33FFFFFF);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		g.centeredText(this.font, Component.literal(this.board.settlementName()).withStyle(s -> s.withBold(true)), this.width / 2, this.top + 8, 0xFFF1E3C0);
		String title = this.board.title().isEmpty() ? standingName(this.board.reputation()) : this.board.title();
		String standing = Component.translatable("hearth.quest.board.standing", title, this.board.reputation()).getString();
		g.text(this.font, standing, this.left + this.boardWidth() - 12 - this.font.width(standing), this.top + 8, 0xFFCDB88E, false);
		this.drawPosters(g);
		g.text(this.font, Component.translatable("hearth.quest.tier." + this.board.tier()), this.left + 12, this.top + 8, 0xFFCDB88E, false);
		if (this.reading != null) {
			this.drawReading(g, this.reading);
			return;
		}
		List<QuestPayloads.Board.Entry> entries = this.board.entries();
		if (entries.isEmpty()) {
			g.centeredText(this.font, Component.translatable("hearth.quest.board.empty"), this.width / 2, this.top + H / 2 - 8, 0xFFF1E3C0);
		}
		for (int i = 0; i < entries.size() && i < 6; i++) {
			this.drawCard(g, entries.get(i), i, mouseX, mouseY);
		}
	}

	/** Wanted posters, one under another: WANTED in red, the name, what they owe (or OUTLAW). */
	private void drawPosters(GuiGraphicsExtractor g) {
		List<QuestPayloads.Board.Poster> posters = this.board.posters();
		int x = this.left + W;
		for (int i = 0; i < posters.size() && i < 3; i++) {
			QuestPayloads.Board.Poster p = posters.get(i);
			int y = this.top + 30 + i * (POSTER_H + 6);
			int w = POSTERS_W - 16;
			QuestUi.paper(g, x, y, w, POSTER_H);
			QuestUi.pin(g, x + w / 2, y + 3, true);
			Component head = Component.translatable(p.outlaw() ? "hearth.quest.poster.outlaw" : "hearth.quest.poster.wanted").withStyle(st -> st.withBold(true));
			g.centeredText(this.font, head, x + w / 2, y + 7, QuestUi.INK_RED);
			g.centeredText(this.font, Component.literal(this.font.plainSubstrByWidth(p.name(), w - 6)), x + w / 2, y + 19, QuestUi.INK);
			int line = y + 31;
			if (!p.crime().isEmpty()) {
				String crime = Component.translatable("hearth.quest.poster.for", Component.translatable(p.crime())).getString();
				g.centeredText(this.font, Component.literal(this.font.plainSubstrByWidth(crime, w - 6)), x + w / 2, line, QuestUi.INK);
				line += 12;
			}
			if (!p.owed().isEmpty()) {
				g.centeredText(this.font, Component.literal(this.font.plainSubstrByWidth(p.owed(), w - 6)), x + w / 2, line, QuestUi.INK_GOLD);
			}
		}
	}

	private int cardX(int i) {
		return this.left + 16 + (i % 3) * (CARD_W + 10);
	}

	private int cardY(int i) {
		return this.top + 32 + (i / 3) * (CARD_H + 10);
	}

	private void drawCard(GuiGraphicsExtractor g, QuestPayloads.Board.Entry e, int i, int mouseX, int mouseY) {
		int x = this.cardX(i);
		int y = this.cardY(i);
		boolean hover = mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + CARD_H;
		QuestInstance q = e.quest();
		// Each paper hangs a little askew, as pinned papers do; the hovered one straightens and lifts.
		float tilt = hover ? 0 : ((q.id.hashCode() >> 3) % 5 - 2) * 0.012F;
		g.pose().pushMatrix();
		g.pose().translate(x + CARD_W / 2F, y + 4);
		g.pose().rotate(tilt);
		g.pose().translate(-CARD_W / 2F, hover ? -2 : 0);
		QuestUi.paper(g, 0, 0, CARD_W, CARD_H);
		QuestUi.pin(g, CARD_W / 2, 3, !e.lock().isEmpty());
		g.fakeItem(QuestUi.icon(q.type), 4, 6);
		int ty = QuestUi.wrapped(g, this.font, q.text.title(), 22, 8, CARD_W - 26, QuestUi.INK, 2);
		ty = Math.max(ty, 24) + 2;
		g.text(this.font, this.font.plainSubstrByWidth(cap(q.giver.name()), CARD_W - 8), 4, ty, QuestUi.INK_FADED, false);
		ty += this.font.lineHeight + 1;
		if (!e.lock().isEmpty()) {
			QuestUi.wrapped(g, this.font, e.lock(), 4, ty, CARD_W - 8, QuestUi.INK_RED, 2);
		} else if (!q.reward.text().isEmpty()) {
			QuestUi.wrapped(g, this.font, q.reward.text(), 4, ty, CARD_W - 8, QuestUi.INK_GREEN, 2);
		}
		g.pose().popMatrix();
	}

	private void drawReading(GuiGraphicsExtractor g, QuestPayloads.Board.Entry e) {
		QuestInstance q = e.quest();
		int x = this.left + 24;
		int y = this.top + 30;
		int w = W - 48;
		QuestUi.paper(g, x, y, w, H - 66);
		QuestUi.pin(g, x + w / 2, y + 3, !e.lock().isEmpty());
		g.fakeItem(QuestUi.icon(q.type), x + 6, y + 6);
		int ty = QuestUi.wrapped(g, this.font, q.text.title(), x + 26, y + 10, w - 34, QuestUi.INK, 2) + 1;
		g.text(this.font, Component.translatable("hearth.quest.giver", cap(q.giver.name()), q.settlementName).getString(), x + 26, ty, QuestUi.INK_FADED, false);
		ty += this.font.lineHeight + 4;
		String words = q.text.offer().isEmpty() ? q.text.summary() : "“" + q.text.offer() + "”";
		ty = QuestUi.wrapped(g, this.font, words, x + 8, ty, w - 16, QuestUi.INK, 5) + 3;
		for (Objective o : q.objectives) {
			ty = QuestUi.wrapped(g, this.font, "• " + o.label, x + 10, ty, w - 20, QuestUi.INK, 2);
		}
		ty += 3;
		if (!q.reward.text().isEmpty()) {
			g.text(this.font, Component.translatable("hearth.quest.reward.line", q.reward.text()).getString(), x + 8, ty, QuestUi.INK_GREEN, false);
			ty += this.font.lineHeight;
		}
		if (q.timing.deadlineDays() > 0) {
			g.text(this.font, Component.translatable("hearth.quest.deadline", q.timing.deadlineDays()).getString(), x + 8, ty, QuestUi.INK_GOLD, false);
			ty += this.font.lineHeight;
		}
		if (!e.lock().isEmpty()) {
			g.text(this.font, e.lock(), x + 8, ty, QuestUi.INK_RED, false);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (this.reading == null && event.button() == 0) {
			List<QuestPayloads.Board.Entry> entries = this.board.entries();
			for (int i = 0; i < entries.size() && i < 6; i++) {
				int x = this.cardX(i);
				int y = this.cardY(i);
				if (event.x() >= x && event.x() < x + CARD_W && event.y() >= y && event.y() < y + CARD_H) {
					this.reading = entries.get(i);
					this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
					this.rebuildWidgets();
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}

	static String standingName(int rep) {
		String key = rep < -20 ? "distrusted" : rep < 10 ? "stranger" : rep < 30 ? "known" : rep < 60 ? "friend" : "hero";
		return Component.translatable("hearth.quest.standing." + key).getString();
	}

	static String cap(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
