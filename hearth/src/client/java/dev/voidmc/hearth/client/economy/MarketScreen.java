package dev.voidmc.hearth.client.economy;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.economy.coin.CoinFormat;
import dev.voidmc.hearth.economy.market.MarketMenu;
import dev.voidmc.hearth.economy.market.MarketView;
import dev.voidmc.hearth.economy.net.EconomyActionPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * The market stall: an oak frame round two parchment pages — what the counter sells on the left, what it will buy on the
 * right — the merchant's word underneath, and the player's inventory below. Rows are drawn from the server's
 * {@link MarketView}; a click sends the row's id back (shift-click: a stack, or all you carry). Right-click haggles: a
 * parchment slip over the pages shows the merchant's last word, your offer (nudged with the buttons), and how much
 * patience the merchant has left.
 */
public class MarketScreen extends AbstractContainerScreen<MarketMenu> {
	private static final Identifier BACKGROUND = Hearth.id("textures/gui/economy/market.png");
	static final int W = 276;
	static final int H = 232;
	static final int PAGE_W = 130;
	static final int LEFT_X = 7;
	static final int RIGHT_X = W - 7 - PAGE_W;
	static final int ROWS_Y = 29;
	static final int ROW_H = 18;
	static final int ROWS = 5;
	static final int NOTE_Y = 127;
	/** The wooden margins left and right of the inventory, where purse and town chest are shown. */
	static final int PURSE_X = 6;
	static final int CHEST_X = W - 46;
	static final int MARGIN_Y = 153;
	/** How long the result of a trade replaces the merchant's word, in milliseconds. */
	static final long STATUS_MILLIS = 6000;
	/** The haggle slip, over both pages. */
	static final int SLIP_X = 22;
	static final int SLIP_Y = 24;
	static final int SLIP_W = W - 44;
	static final int SLIP_H = 96;
	static final int BTN_H = 14;

	private final List<Button> buttons = new ArrayList<>();
	private int scrollSells;
	private int scrollBuys;
	private String lastStatus = "";
	private long statusSince;
	/** The player's offer in the haggle under way, and which haggle it belongs to. */
	private long offer;
	private String haggleKey = "";

	public MarketScreen(MarketMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, W, H);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		g.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos, this.topPos, 0, 0, W, H, 512, 512);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
		MarketView v = this.menu.view();
		g.text(this.font, this.title, 8, 4, EconomyGui.ON_WOOD, true);
		Component standing = Component.translatable("hearth.economy.screen.standing", EconomyGui.standing(v.reputation()));
		g.text(this.font, standing, W - 8 - this.font.width(standing), 4, EconomyGui.ON_WOOD, true);

		header(g, Component.translatable("hearth.economy.screen.for_sale"), LEFT_X);
		header(g, Component.translatable("hearth.economy.screen.wanted"), RIGHT_X);

		EconomyGui.purseColumn(g, this.font, PURSE_X, MARGIN_Y, Component.translatable("hearth.economy.screen.purse"), v.balance());
		EconomyGui.purseColumn(g, this.font, CHEST_X, MARGIN_Y, Component.translatable(v.shop() ? "hearth.economy.screen.till" : "hearth.economy.screen.chest"),
			v.treasury());

		// the merchant's word, and what just happened
		String status = v.status().getString();
		if (!status.equals(this.lastStatus)) {
			this.lastStatus = status;
			this.statusSince = System.currentTimeMillis();
		}
		boolean showStatus = !status.isEmpty() && System.currentTimeMillis() - this.statusSince < STATUS_MILLIS;
		Component text = v.haggle() != null ? v.haggle().line().copy().withStyle(ChatFormatting.ITALIC)
			: showStatus ? v.status() : v.note().copy().withStyle(ChatFormatting.ITALIC);
		List<FormattedCharSequence> lines = this.font.split(text, W - 24);
		for (int i = 0; i < Math.min(2, lines.size()); i++) {
			g.text(this.font, lines.get(i), 12, NOTE_Y + i * 10, EconomyGui.INK, false);
		}
	}

	private void header(GuiGraphicsExtractor g, Component text, int pageX) {
		int w = this.font.width(text);
		g.text(this.font, text, pageX + (PAGE_W - w) / 2, 18, EconomyGui.INK, false);
		g.fill(pageX + 8, 27, pageX + PAGE_W - 8, 28, 0x50604020);
	}

	@Override
	public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractContents(g, mouseX, mouseY, a);
		MarketView v = this.menu.view();
		this.buttons.clear();
		this.scrollSells = Mth.clamp(this.scrollSells, 0, Math.max(0, v.sells().size() - ROWS));
		this.scrollBuys = Mth.clamp(this.scrollBuys, 0, Math.max(0, v.buys().size() - ROWS));
		boolean haggling = v.haggle() != null;
		int mx = haggling ? -1000 : mouseX;
		int my = haggling ? -1000 : mouseY;
		MarketView.Offer hovered = null;
		boolean hoveredBuy = false;
		MarketView.Offer h1 = rows(g, v.sells(), this.scrollSells, this.leftPos + LEFT_X, false, mx, my);
		MarketView.Offer h2 = rows(g, v.buys(), this.scrollBuys, this.leftPos + RIGHT_X, true, mx, my);
		if (h1 != null) {
			hovered = h1;
		} else if (h2 != null) {
			hovered = h2;
			hoveredBuy = true;
		}
		if (v.sells().isEmpty()) {
			empty(g, Component.translatable("hearth.economy.screen.nothing_for_sale"), this.leftPos + LEFT_X);
		}
		if (v.buys().isEmpty()) {
			empty(g, Component.translatable("hearth.economy.screen.nothing_wanted"), this.leftPos + RIGHT_X);
		}
		if (haggling) {
			slip(g, v.haggle(), mouseX, mouseY);
		} else if (hovered != null) {
			g.setTooltipForNextFrame(this.font, tooltip(hovered, hoveredBuy), Optional.empty(), mouseX, mouseY);
		}
	}

	private void empty(GuiGraphicsExtractor g, Component text, int x) {
		List<FormattedCharSequence> lines = this.font.split(text.copy().withStyle(ChatFormatting.ITALIC), PAGE_W - 16);
		for (int i = 0; i < lines.size(); i++) {
			g.centeredText(this.font, lines.get(i), x + PAGE_W / 2, this.topPos + ROWS_Y + 20 + i * 10, EconomyGui.INK_FADED);
		}
	}

	/** Draws one page of rows; returns the hovered row. */
	private MarketView.Offer rows(GuiGraphicsExtractor g, List<MarketView.Offer> offers, int scroll, int x, boolean buying, int mx, int my) {
		MarketView.Offer hovered = null;
		int y0 = this.topPos + ROWS_Y;
		for (int i = 0; i < ROWS && scroll + i < offers.size(); i++) {
			MarketView.Offer o = offers.get(scroll + i);
			int y = y0 + i * ROW_H;
			boolean usable = o.available() > 0;
			boolean hover = EconomyGui.inside(mx, my, x + 2, y, PAGE_W - 8, ROW_H);
			if (o.has(MarketView.NEEDED)) {
				g.fill(x + 2, y, x + PAGE_W - 6, y + ROW_H - 1, 0x3048802A);
			} else if (o.has(MarketView.STOLEN)) {
				g.fill(x + 2, y, x + PAGE_W - 6, y + ROW_H - 1, 0x308C1F14);
			} else if (o.has(MarketView.FENCED)) {
				g.fill(x + 2, y, x + PAGE_W - 6, y + ROW_H - 1, 0x30402850);
			}
			if (hover) {
				g.fill(x + 2, y, x + PAGE_W - 6, y + ROW_H - 1, usable ? 0x40704A20 : 0x20704A20);
				hovered = o;
			}
			g.item(o.stack(), x + 3, y + 1);
			g.itemDecorations(this.font, o.stack(), x + 3, y + 1);
			if (o.has(MarketView.NEEDED)) {
				EconomyGui.hammer(g, x + 13, y + 9);
			}
			int priceW = EconomyGui.coinsWidth(this.font, o.price());
			String name = EconomyGui.trim(this.font, o.stack().getHoverName().getString(), PAGE_W - 34 - priceW);
			int ink = usable ? EconomyGui.INK : EconomyGui.INK_FADED;
			g.text(this.font, name, x + 22, y + 5, ink, false);
			EconomyGui.coinsRight(g, this.font, x + PAGE_W - 8, y + 5, o.price(), usable ? (buying ? EconomyGui.INK_GREEN : EconomyGui.INK) : EconomyGui.INK_FADED);
		}
		// scroll bar
		if (offers.size() > ROWS) {
			int track = ROWS * ROW_H;
			int knob = Math.max(8, track * ROWS / offers.size());
			int top = y0 + (track - knob) * scroll / Math.max(1, offers.size() - ROWS);
			g.fill(x + PAGE_W - 5, y0, x + PAGE_W - 3, y0 + track, 0x30402810);
			g.fill(x + PAGE_W - 5, top, x + PAGE_W - 3, top + knob, 0xA0603A18);
		}
		return hovered;
	}

	private List<Component> tooltip(MarketView.Offer o, boolean buying) {
		List<Component> lines = new ArrayList<>();
		lines.add(o.stack().getHoverName().copy().withStyle(o.stack().getRarity().color()));
		lines.add(Component.translatable(buying ? "hearth.economy.screen.pays" : "hearth.economy.screen.asks", o.stack().getCount(),
			CoinFormat.format(o.price())).withStyle(ChatFormatting.GOLD));
		lines.add(Component.translatable("hearth.goods." + o.goods()).withStyle(ChatFormatting.DARK_GRAY));
		if (o.has(MarketView.NEEDED)) {
			lines.add(Component.translatable("hearth.economy.screen.builders_need").withStyle(ChatFormatting.GREEN));
		}
		if (buying) {
			lines.add(Component.translatable("hearth.economy.screen.you_have", o.owned()).withStyle(ChatFormatting.GRAY));
		} else {
			lines.add(Component.translatable("hearth.economy.screen.in_stock", o.available() * o.stack().getCount()).withStyle(ChatFormatting.GRAY));
		}
		if (o.has(MarketView.FENCED)) {
			lines.add(Component.translatable("hearth.economy.screen.fenced").withStyle(ChatFormatting.DARK_PURPLE));
		}
		if (o.has(MarketView.SOUR)) {
			lines.add(Component.translatable("hearth.economy.screen.sour").withStyle(ChatFormatting.RED));
		}
		if (o.has(MarketView.REFUSED)) {
			lines.add(Component.translatable("hearth.economy.market.refused").withStyle(ChatFormatting.RED));
		} else if (o.has(MarketView.STOLEN)) {
			lines.add(Component.translatable("hearth.economy.screen.stolen").withStyle(ChatFormatting.RED));
		} else if (o.has(MarketView.NO_ROOM)) {
			lines.add(Component.translatable("hearth.economy.screen.no_room").withStyle(ChatFormatting.RED));
		} else if (o.has(MarketView.TOWN_CANNOT_PAY)) {
			lines.add(Component.translatable(this.menu.view().shop() && !o.has(MarketView.NEEDED) ? "hearth.economy.market.shop_cannot_pay"
				: "hearth.economy.market.town_cannot_pay").withStyle(ChatFormatting.RED));
		} else if (o.has(MarketView.CANNOT_AFFORD)) {
			lines.add(Component.translatable("hearth.economy.screen.cannot_afford").withStyle(ChatFormatting.RED));
		} else if (buying && o.owned() < o.stack().getCount()) {
			lines.add(Component.translatable("hearth.economy.screen.bring", o.stack().getCount()).withStyle(ChatFormatting.GRAY));
		}
		if (o.available() > 0) {
			lines.add(Component.translatable(buying ? "hearth.economy.screen.click_sell" : "hearth.economy.screen.click_buy").withStyle(ChatFormatting.DARK_GRAY));
			if (!o.has(MarketView.SOUR)) {
				lines.add(Component.translatable("hearth.economy.screen.click_haggle").withStyle(ChatFormatting.DARK_GRAY));
			}
		}
		return lines;
	}

	// ---------------------------------------------------------------- the haggle slip

	private void slip(GuiGraphicsExtractor g, MarketView.HaggleView h, int mouseX, int mouseY) {
		String key = h.row() + ":" + h.list() + ":" + h.playerBuys();
		if (!key.equals(this.haggleKey)) {
			this.haggleKey = key;
			this.offer = Math.max(1, Math.round(h.counter() * (h.playerBuys() ? 0.85 : 1.15)));
		}
		// never offer worse than the merchant's own last word
		this.offer = h.playerBuys() ? Math.min(this.offer, h.counter()) : Math.max(this.offer, h.counter());
		this.offer = Math.max(0, this.offer);

		int x = this.leftPos + SLIP_X;
		int y = this.topPos + SLIP_Y;
		g.fill(x - 1, y - 1, x + SLIP_W + 1, y + SLIP_H + 1, 0xFF4A3420);
		g.fill(x, y, x + SLIP_W, y + SLIP_H, 0xFFEFE0BC);
		g.fill(x, y + SLIP_H - 2, x + SLIP_W, y + SLIP_H, 0xFFD6C39A);

		g.item(h.stack(), x + 6, y + 5);
		g.itemDecorations(this.font, h.stack(), x + 6, y + 5);
		Component what = Component.translatable(h.playerBuys() ? "hearth.economy.haggle.buying" : "hearth.economy.haggle.selling",
			h.stack().getCount(), h.stack().getHoverName());
		g.text(this.font, EconomyGui.trim(this.font, what.getString(), SLIP_W - 32), x + 26, y + 6, EconomyGui.INK, false);
		g.text(this.font, Component.translatable("hearth.economy.haggle.list"), x + 26, y + 16, EconomyGui.INK_FADED, false);
		EconomyGui.coinsRight(g, this.font, x + SLIP_W - 8, y + 16, h.list(), EconomyGui.INK_FADED);

		int ly = y + 30;
		g.text(this.font, Component.translatable(h.playerBuys() ? "hearth.economy.haggle.asks" : "hearth.economy.haggle.offers"), x + 8, ly,
			EconomyGui.INK, false);
		EconomyGui.coinsRight(g, this.font, x + SLIP_W - 8, ly, h.counter(), EconomyGui.INK);
		ly += 12;
		g.text(this.font, Component.translatable("hearth.economy.haggle.your_offer"), x + 8, ly, EconomyGui.INK, false);
		EconomyGui.coinsRight(g, this.font, x + SLIP_W - 8, ly, this.offer, EconomyGui.INK_GREEN);

		// nudges: a tenth, or a single bit
		ly += 11;
		int bw = 34;
		long tenth = Math.max(1, Math.round(h.list() * 0.1));
		this.buttons.add(new Button(x + 8, ly, bw, Component.literal("−10%"), true, -tenth, 0));
		this.buttons.add(new Button(x + 8 + (bw + 2), ly, bw, Component.literal("−1"), true, -1, 0));
		this.buttons.add(new Button(x + 8 + (bw + 2) * 2, ly, bw, Component.literal("+1"), true, 1, 0));
		this.buttons.add(new Button(x + 8 + (bw + 2) * 3, ly, bw, Component.literal("+10%"), true, tenth, 0));

		// patience
		int px = x + 8 + (bw + 2) * 4 + 6;
		int pw = x + SLIP_W - 8 - px;
		g.text(this.font, Component.translatable("hearth.economy.haggle.patience"), px, ly - 1, EconomyGui.INK_FADED, false);
		int barY = ly + 9;
		g.fill(px, barY, px + pw, barY + 3, 0x40402810);
		int fill = pw * Math.max(0, Math.min(100, h.patience())) / 100;
		int colour = h.patience() > 60 ? 0xFF4C7A2C : h.patience() > 30 ? 0xFFB08A2A : 0xFF9A2A1C;
		g.fill(px, barY, px + fill, barY + 3, colour);

		ly += 19;
		int third = (SLIP_W - 16 - 4) / 3;
		this.buttons.add(new Button(x + 8, ly, third, Component.translatable("hearth.economy.haggle.offer"), true, 0, EconomyActionPayload.HAGGLE_OFFER));
		this.buttons.add(new Button(x + 10 + third, ly, third, Component.translatable("hearth.economy.haggle.take", CoinFormat.format(h.counter())), true, 0,
			EconomyActionPayload.HAGGLE_ACCEPT));
		this.buttons.add(new Button(x + 12 + third * 2, ly, third, Component.translatable("hearth.economy.haggle.leave"), true, 0,
			EconomyActionPayload.HAGGLE_CANCEL));

		for (Button b : this.buttons) {
			boolean hover = EconomyGui.inside(mouseX, mouseY, b.x, b.y, b.w, BTN_H);
			EconomyGui.button(g, this.font, b.x, b.y, b.w, BTN_H, Component.literal(EconomyGui.trim(this.font, b.label.getString(), b.w - 4)), b.enabled, hover);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		MarketView v = this.menu.view();
		if (v.haggle() != null) {
			if (event.button() == 0) {
				for (Button b : this.buttons) {
					if (b.enabled && EconomyGui.inside(event.x(), event.y(), b.x, b.y, b.w, BTN_H)) {
						EconomyGui.click();
						if (b.action == 0) {
							long step = event.hasShiftDown() ? b.nudge * 5 : b.nudge;
							this.offer = Math.max(0, this.offer + step);
						} else {
							ClientPlayNetworking.send(new EconomyActionPayload(this.menu.containerId, b.action, v.haggle().row(),
								b.action == EconomyActionPayload.HAGGLE_OFFER ? this.offer : 0));
						}
						return true;
					}
				}
			}
			if (EconomyGui.inside(event.x(), event.y(), this.leftPos + SLIP_X, this.topPos + SLIP_Y, SLIP_W, SLIP_H)) {
				return true;
			}
			return super.mouseClicked(event, doubleClick);
		}
		if (event.button() == 0 || event.button() == 1) {
			MarketView.Offer o = rowAt(v.sells(), this.scrollSells, this.leftPos + LEFT_X, event.x(), event.y());
			if (o == null) {
				o = rowAt(v.buys(), this.scrollBuys, this.leftPos + RIGHT_X, event.x(), event.y());
			}
			if (o != null) {
				EconomyGui.click();
				int action = event.button() == 1 ? EconomyActionPayload.HAGGLE_START : EconomyActionPayload.TRADE;
				ClientPlayNetworking.send(new EconomyActionPayload(this.menu.containerId, action, o.id(), event.hasShiftDown() ? 1 : 0));
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private MarketView.Offer rowAt(List<MarketView.Offer> offers, int scroll, int x, double mx, double my) {
		int y0 = this.topPos + ROWS_Y;
		for (int i = 0; i < ROWS && scroll + i < offers.size(); i++) {
			if (EconomyGui.inside(mx, my, x + 2, y0 + i * ROW_H, PAGE_W - 8, ROW_H)) {
				return offers.get(scroll + i);
			}
		}
		return null;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (this.menu.view().haggle() != null) {
			return super.mouseScrolled(x, y, scrollX, scrollY);
		}
		int step = (int) -Math.signum(scrollY);
		if (EconomyGui.inside(x, y, this.leftPos + LEFT_X, this.topPos + ROWS_Y, PAGE_W, ROWS * ROW_H)) {
			this.scrollSells += step;
			return true;
		}
		if (EconomyGui.inside(x, y, this.leftPos + RIGHT_X, this.topPos + ROWS_Y, PAGE_W, ROWS * ROW_H)) {
			this.scrollBuys += step;
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	/** A slip button: a nudge to the offer ({@code action} 0) or an action sent to the server. */
	private record Button(int x, int y, int w, Component label, boolean enabled, long nudge, int action) {
	}
}
