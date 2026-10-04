package dev.voidmc.hearth.client.standing;

import dev.voidmc.hearth.api.StandingService.Title;
import dev.voidmc.hearth.standing.logic.Titles;
import dev.voidmc.hearth.standing.net.StandingPayloads;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The reputation page ({@code /hearth standing} or the Reputation key): how known the player is here, how they come
 * across (charisma and menace, and what folk notice), their company, and every town they have dealings with — title,
 * standing on a bar from outlaw to hero, distance. A sheet of parchment like the conversation screen; the server sends
 * it all.
 */
public final class ReputationScreen extends Screen {
	private static final int WIDTH = 330;
	private static final int ROW = 24;
	private static final int PAPER = 0xFFE9DAB6;
	private static final int PAPER_LIGHT = 0xFFF3E7CB;
	private static final int PAPER_DARK = 0xFFD6C192;
	private static final int EDGE = 0xFF5A4026;
	private static final int INK = 0xFF3A2A1A;
	private static final int INK_SOFT = 0xFF7A6040;

	private StandingPayloads.Page page;
	private int scroll;
	private int left;
	private int top;
	private int height0;
	private int listTop;
	private int rows;

	public ReputationScreen(StandingPayloads.Page page) {
		super(Component.translatable("hearth.standing.page.heading"));
		this.page = page;
	}

	public void update(StandingPayloads.Page page) {
		this.page = page;
		this.scroll = Math.min(this.scroll, Math.max(0, page.towns().size() - this.rows));
	}

	@Override
	protected void init() {
		this.height0 = Math.min(this.height - 16, 300);
		this.left = (this.width - WIDTH) / 2;
		this.top = (this.height - this.height0) / 2;
		this.listTop = this.top + 96;
		this.rows = Math.max(1, (this.top + this.height0 - 30 - this.listTop) / ROW);
		this.addRenderableWidget(Button.builder(Component.translatable("hearth.standing.page.refresh"),
			b -> ClientPlayNetworking.send(StandingPayloads.RequestPage.INSTANCE)).bounds(this.left + WIDTH - 82, this.top + this.height0 - 26, 70, 18).build());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		int x0 = this.left;
		int y0 = this.top;
		int x1 = x0 + WIDTH;
		int y1 = y0 + this.height0;
		g.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, EDGE);
		g.fill(x0, y0, x1, y1, PAPER);
		g.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, PAPER_LIGHT);
		g.fill(x0 + 4, y0 + 4, x1 - 4, y1 - 4, PAPER);
		g.fill(x0 + 4, y0 + 4, x1 - 4, y0 + 6, PAPER_DARK);
		g.fill(x0 + 4, y1 - 6, x1 - 4, y1 - 4, PAPER_DARK);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		int x = this.left + 12;
		int y = this.top + 12;
		g.text(this.font, Component.translatable("hearth.standing.page.heading").withStyle(ChatFormatting.BOLD), x, y, INK, false);
		y += 14;
		g.text(this.font, Component.translatable("hearth.standing.page.fame_line", ClientStanding.fame(this.page.fame(), this.page.renown())), x, y, INK, false);
		y += 11;
		g.text(this.font, Component.translatable("hearth.standing.page.charisma", ClientStanding.charisma(this.page.charisma()),
			String.format(java.util.Locale.ROOT, "%+.2f", this.page.charisma())), x, y, INK, false);
		g.text(this.font, Component.translatable("hearth.standing.page.menace", ClientStanding.menace(this.page.menace()),
			String.format(java.util.Locale.ROOT, "%.2f", this.page.menace())), x + WIDTH / 2, y, INK, false);
		y += 11;
		if (!this.page.notes().isEmpty()) {
			MutableComponent notes = Component.empty();
			for (int i = 0; i < this.page.notes().size(); i++) {
				if (i > 0) {
					notes.append(", ");
				}
				notes.append(Component.translatable("hearth.standing.look." + this.page.notes().get(i)));
			}
			g.text(this.font, this.font.plainSubstrByWidth(notes.getString(), WIDTH - 24), x, y, INK_SOFT, false);
		}
		y += 13;
		if (this.page.companies()) {
			Component company = this.page.company().isEmpty() ? Component.translatable("hearth.standing.page.no_company")
				: Component.translatable("hearth.standing.page.company_line", this.page.company(), String.join(", ", this.page.members()));
			g.text(this.font, this.font.plainSubstrByWidth(company.getString(), WIDTH - 24), x, y, INK_SOFT, false);
		}
		y = this.listTop - 14;
		g.text(this.font, Component.translatable("hearth.standing.page.towns").withStyle(ChatFormatting.BOLD), x, y, INK, false);
		g.fill(x, this.listTop - 3, this.left + WIDTH - 12, this.listTop - 2, PAPER_DARK);

		List<StandingPayloads.TownRow> towns = this.page.towns();
		if (towns.isEmpty()) {
			for (var line : this.font.split(Component.translatable("hearth.standing.page.empty"), WIDTH - 24)) {
				g.text(this.font, line, x, this.listTop + 4, INK_SOFT, false);
				break;
			}
			return;
		}
		int ry = this.listTop;
		for (int i = this.scroll; i < towns.size() && i < this.scroll + this.rows; i++) {
			row(g, towns.get(i), x, ry);
			ry += ROW;
		}
		if (towns.size() > this.rows) {
			String more = (this.scroll + 1) + "–" + Math.min(towns.size(), this.scroll + this.rows) + " / " + towns.size();
			g.text(this.font, more, x, this.top + this.height0 - 20, INK_SOFT, false);
		}
	}

	private void row(GuiGraphicsExtractor g, StandingPayloads.TownRow r, int x, int y) {
		Title title = ClientStanding.title(r.title());
		int right = this.left + WIDTH - 12;
		g.text(this.font, Component.literal(r.name()).withStyle(ChatFormatting.BOLD), x, y, INK, false);
		List<String> bits = new ArrayList<>();
		bits.add(Component.translatable("hearth.tier." + r.tier()).getString());
		bits.add(Component.translatable("hearth.culture." + r.culture()).getString());
		bits.add(r.here() ? Component.translatable("hearth.standing.page.here").getString()
			: r.distance() >= 0 ? Component.translatable("hearth.standing.page.distance", r.distance()).getString()
				: Component.translatable("hearth.standing.page.elsewhere").getString());
		if (r.owner()) {
			bits.add(Component.translatable("hearth.standing.page.owner").getString());
		}
		g.text(this.font, this.font.plainSubstrByWidth(String.join(" · ", bits), 170), x, y + 11, INK_SOFT, false);
		// Title, and a bar from outlaw (-100) to hero (100) with the player's mark.
		Component t = Component.translatable(title.translationKey()).append(" " + ClientStanding.signed(r.standing()));
		int bx0 = right - 110;
		int bx1 = right;
		g.text(this.font, t, bx1 - this.font.width(t), y, darker(Titles.colour(title)), false);
		int by = y + 13;
		g.fill(bx0, by, bx1, by + 4, EDGE);
		int mid = (bx0 + bx1) / 2;
		g.fill(bx0 + 1, by + 1, mid, by + 3, 0xFFB86A50);
		g.fill(mid, by + 1, bx1 - 1, by + 3, 0xFF7FA860);
		int mark = mid + Math.round((bx1 - bx0 - 2) / 2F * r.standing() / 100F);
		g.fill(mark - 1, by - 2, mark + 1, by + 6, INK);
		g.fill(x, y + ROW - 3, right, y + ROW - 2, 0x30000000);
	}

	/** Title colours are made for dark HUD backgrounds; on parchment they need more ink. */
	private static int darker(int argb) {
		int r = (argb >> 16 & 0xFF) * 55 / 100;
		int gr = (argb >> 8 & 0xFF) * 55 / 100;
		int b = (argb & 0xFF) * 55 / 100;
		return 0xFF000000 | r << 16 | gr << 8 | b;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		this.scroll = Math.max(0, Math.min(Math.max(0, this.page.towns().size() - this.rows), this.scroll - (int) Math.signum(scrollY)));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (StandingClient.isReputationKey(event)) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
