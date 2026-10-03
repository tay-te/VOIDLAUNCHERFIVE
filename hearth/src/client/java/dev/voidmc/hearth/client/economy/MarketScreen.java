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
 * The market stall: an oak frame round two parchment pages — what the town sells on the left, what it will buy on the
 * right — the merchant's word underneath, and the player's inventory below. Rows are drawn from the server's
 * {@link MarketView}; a click sends the row's id back (shift-click: a stack, or all you carry).
 */
public class MarketScreen extends AbstractContainerScreen<MarketMenu> {
	private static final Identifier BACKGROUND = Hearth.id("textures/gui/economy/market.png");
	static final int W = 276;
	static final int H = 264;
	static final int PAGE_W = 130;
	static final int LEFT_X = 7;
	static final int RIGHT_X = W - 7 - PAGE_W;
	static final int ROWS_Y = 32;
	static final int ROW_H = 18;
	static final int ROWS = 5;
	static final int FOOTER_Y = 135;
	static final int NOTE_Y = 152;

	private int scrollSells;
	private int scrollBuys;

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
		g.text(this.font, this.title, 8, 5, EconomyGui.ON_WOOD, true);
		Component standing = Component.translatable("hearth.economy.screen.standing", EconomyGui.standing(v.reputation()));
		g.text(this.font, standing, W - 8 - this.font.width(standing), 5, EconomyGui.ON_WOOD, true);

		header(g, Component.translatable("hearth.economy.screen.for_sale"), LEFT_X);
		header(g, Component.translatable("hearth.economy.screen.wanted"), RIGHT_X);

		g.text(this.font, Component.translatable("hearth.economy.screen.purse"), LEFT_X + 4, FOOTER_Y, EconomyGui.INK, false);
		EconomyGui.coinsRight(g, this.font, LEFT_X + PAGE_W - 6, FOOTER_Y, v.balance(), EconomyGui.INK);
		g.text(this.font, Component.translatable("hearth.economy.screen.treasury"), RIGHT_X + 4, FOOTER_Y, EconomyGui.INK, false);
		EconomyGui.coinsRight(g, this.font, RIGHT_X + PAGE_W - 6, FOOTER_Y, v.treasury(), EconomyGui.INK);

		// the merchant's word, and what just happened
		int noteW = W - 24;
		List<FormattedCharSequence> lines = new ArrayList<>(this.font.split(v.note().copy().withStyle(ChatFormatting.ITALIC), noteW));
		boolean status = !v.status().getString().isEmpty();
		int noteLines = status ? 1 : 2;
		for (int i = 0; i < Math.min(noteLines, lines.size()); i++) {
			g.text(this.font, lines.get(i), 12, NOTE_Y + i * 10, EconomyGui.INK, false);
		}
		if (status) {
			List<FormattedCharSequence> s = this.font.split(v.status(), noteW);
			if (!s.isEmpty()) {
				g.text(this.font, s.getFirst(), 12, NOTE_Y + 10, EconomyGui.INK, false);
			}
		}
	}

	private void header(GuiGraphicsExtractor g, Component text, int pageX) {
		int w = this.font.width(text);
		g.text(this.font, text, pageX + (PAGE_W - w) / 2, 21, EconomyGui.INK, false);
		g.fill(pageX + 8, 30, pageX + PAGE_W - 8, 31, 0x50604020);
	}

	@Override
	public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractContents(g, mouseX, mouseY, a);
		MarketView v = this.menu.view();
		this.scrollSells = Mth.clamp(this.scrollSells, 0, Math.max(0, v.sells().size() - ROWS));
		this.scrollBuys = Mth.clamp(this.scrollBuys, 0, Math.max(0, v.buys().size() - ROWS));
		MarketView.Offer hovered = null;
		boolean hoveredBuy = false;
		MarketView.Offer h1 = rows(g, v.sells(), this.scrollSells, this.leftPos + LEFT_X, false, mouseX, mouseY);
		MarketView.Offer h2 = rows(g, v.buys(), this.scrollBuys, this.leftPos + RIGHT_X, true, mouseX, mouseY);
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
		if (hovered != null) {
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
		if (o.has(MarketView.REFUSED)) {
			lines.add(Component.translatable("hearth.economy.market.refused").withStyle(ChatFormatting.RED));
		} else if (o.has(MarketView.NO_ROOM)) {
			lines.add(Component.translatable("hearth.economy.screen.no_room").withStyle(ChatFormatting.RED));
		} else if (o.has(MarketView.TOWN_CANNOT_PAY)) {
			lines.add(Component.translatable("hearth.economy.market.town_cannot_pay").withStyle(ChatFormatting.RED));
		} else if (o.has(MarketView.CANNOT_AFFORD)) {
			lines.add(Component.translatable("hearth.economy.screen.cannot_afford").withStyle(ChatFormatting.RED));
		} else if (buying && o.owned() < o.stack().getCount()) {
			lines.add(Component.translatable("hearth.economy.screen.bring", o.stack().getCount()).withStyle(ChatFormatting.GRAY));
		}
		if (o.available() > 0) {
			lines.add(Component.translatable(buying ? "hearth.economy.screen.click_sell" : "hearth.economy.screen.click_buy").withStyle(ChatFormatting.DARK_GRAY));
		}
		return lines;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		MarketView v = this.menu.view();
		if (event.button() == 0) {
			MarketView.Offer o = rowAt(v.sells(), this.scrollSells, this.leftPos + LEFT_X, event.x(), event.y());
			if (o == null) {
				o = rowAt(v.buys(), this.scrollBuys, this.leftPos + RIGHT_X, event.x(), event.y());
			}
			if (o != null) {
				EconomyGui.click();
				ClientPlayNetworking.send(new EconomyActionPayload(this.menu.containerId, EconomyActionPayload.TRADE, o.id(), event.hasShiftDown() ? 1 : 0));
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
}
