package dev.voidmc.hearth.client.economy;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.economy.bank.BankMenu;
import dev.voidmc.hearth.economy.bank.BankView;
import dev.voidmc.hearth.economy.coin.CoinFormat;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The bank counter. Bank tab: the player's account (deposit and withdraw in marks and crowns, or everything) on the
 * left page, the money changer (emeralds for coin and back) on the right. Property tab: houses and plots for sale on
 * the left, the chosen one and its price on the right, with a button to buy its deed.
 */
public class BankScreen extends AbstractContainerScreen<BankMenu> {
	private static final Identifier BACKGROUND = Hearth.id("textures/gui/economy/bank.png");
	private static final int W = MarketScreen.W;
	private static final int H = MarketScreen.H;
	private static final int PAGE_W = MarketScreen.PAGE_W;
	private static final int LEFT_X = MarketScreen.LEFT_X;
	private static final int RIGHT_X = MarketScreen.RIGHT_X;
	private static final int ROW_H = 18;
	private static final int ROWS = 6;
	private static final int ROWS_Y = 32;
	private static final int BTN_H = 14;

	/** Deposit/withdraw amounts in bits; {@link EconomyActionPayload#ALL} for everything. */
	private static final long[] AMOUNTS = {10, 100, 1000, EconomyActionPayload.ALL};

	private final List<Button> buttons = new ArrayList<>();
	private int tab;
	private int scroll;
	private int selected = -1;

	public BankScreen(BankMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, W, H);
		this.tab = menu.view().tab();
	}

	/** A new view arrived from the server: follow its tab when it changes it (a purchase shows the property tab). */
	void onView(BankView view) {
		this.tab = view.hasBank() ? view.tab() : BankView.TAB_PROPERTY;
	}

	/** Dev tooling: choose a listed property, as a click would. */
	void devSelect(int index) {
		this.tab = BankView.TAB_PROPERTY;
		this.selected = index;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		g.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos, this.topPos, 0, 0, W, H, 512, 512);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
		BankView v = this.menu.view();
		g.text(this.font, this.title, 8, 5, EconomyGui.ON_WOOD, true);
		// tabs on the frame
		if (v.hasBank()) {
			int x = W - 8;
			Component prop = Component.translatable("hearth.economy.screen.tab_property");
			Component bank = Component.translatable("hearth.economy.screen.tab_bank");
			x -= this.font.width(prop);
			g.text(this.font, prop, x, 5, this.tab == BankView.TAB_PROPERTY ? 0xFFFFE9A0 : 0xFFB8A27A, true);
			x -= 10 + this.font.width(bank);
			g.text(this.font, bank, x, 5, this.tab == BankView.TAB_BANK ? 0xFFFFE9A0 : 0xFFB8A27A, true);
		}
		List<FormattedCharSequence> s = this.font.split(v.status(), W - 24);
		if (!s.isEmpty()) {
			g.text(this.font, s.getFirst(), 12, MarketScreen.NOTE_Y + 5, EconomyGui.INK, false);
		}
	}

	@Override
	public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractContents(g, mouseX, mouseY, a);
		BankView v = this.menu.view();
		this.buttons.clear();
		int x0 = this.leftPos;
		int y0 = this.topPos;
		if (this.tab == BankView.TAB_BANK && v.hasBank()) {
			account(g, v, x0 + LEFT_X, y0);
			changer(g, v, x0 + RIGHT_X, y0);
		} else {
			property(g, v, x0, y0, mouseX, mouseY);
		}
		for (Button b : this.buttons) {
			boolean hover = EconomyGui.inside(mouseX, mouseY, b.x, b.y, b.w, BTN_H);
			EconomyGui.button(g, this.font, b.x, b.y, b.w, BTN_H, b.label, b.enabled, hover);
			if (hover && b.tooltip != null) {
				g.setTooltipForNextFrame(this.font, List.of(b.tooltip), Optional.empty(), mouseX, mouseY);
			}
		}
	}

	private void title(GuiGraphicsExtractor g, Component text, int pageX, int y0) {
		int w = this.font.width(text);
		g.text(this.font, text, pageX + (PAGE_W - w) / 2, y0 + 21, EconomyGui.INK, false);
		g.fill(pageX + 8, y0 + 30, pageX + PAGE_W - 8, y0 + 31, 0x50604020);
	}

	private void account(GuiGraphicsExtractor g, BankView v, int x, int y0) {
		title(g, Component.translatable("hearth.economy.screen.account"), x, y0);
		int y = y0 + 36;
		g.text(this.font, Component.translatable("hearth.economy.screen.on_deposit"), x + 6, y, EconomyGui.INK, false);
		EconomyGui.coinsRight(g, this.font, x + PAGE_W - 6, y, v.deposit(), EconomyGui.INK_GREEN);
		y += 12;
		g.text(this.font, Component.translatable("hearth.economy.screen.carried"), x + 6, y, EconomyGui.INK, false);
		EconomyGui.coinsRight(g, this.font, x + PAGE_W - 6, y, v.carried(), EconomyGui.INK);
		y += 16;
		g.text(this.font, Component.translatable("hearth.economy.screen.deposit"), x + 6, y, EconomyGui.INK, false);
		y += 10;
		amountButtons(x, y, EconomyActionPayload.DEPOSIT, v.carried());
		y += 22;
		g.text(this.font, Component.translatable("hearth.economy.screen.withdraw"), x + 6, y, EconomyGui.INK, false);
		y += 10;
		amountButtons(x, y, EconomyActionPayload.WITHDRAW, v.deposit());
		List<FormattedCharSequence> hint = this.font.split(Component.translatable("hearth.economy.screen.deposit_hint").withStyle(ChatFormatting.ITALIC), PAGE_W - 12);
		for (int i = 0; i < Math.min(2, hint.size()); i++) {
			g.text(this.font, hint.get(i), x + 6, y0 + 124 + i * 10, EconomyGui.INK_FADED, false);
		}
	}

	private void amountButtons(int x, int y, int action, long available) {
		int bw = (PAGE_W - 12 - 6) / 4;
		for (int i = 0; i < AMOUNTS.length; i++) {
			long amount = AMOUNTS[i];
			Component label = amount == EconomyActionPayload.ALL ? Component.translatable("hearth.economy.screen.all") : shortAmount(amount);
			boolean enabled = amount == EconomyActionPayload.ALL ? available > 0 : available >= amount;
			Component tip = amount == EconomyActionPayload.ALL ? CoinFormat.format(available) : CoinFormat.format(amount);
			this.buttons.add(new Button(x + 6 + i * (bw + 2), y, bw, label, enabled, tip, action, 0, amount));
		}
	}

	private static Component shortAmount(long bits) {
		return bits >= 100 ? Component.translatable("hearth.economy.screen.crowns_short", bits / 100)
			: Component.translatable("hearth.economy.screen.marks_short", bits / 10);
	}

	private void changer(GuiGraphicsExtractor g, BankView v, int x, int y0) {
		title(g, Component.translatable("hearth.economy.screen.changer"), x, y0);
		int y = y0 + 36;
		g.item(new ItemStack(Items.EMERALD), x + 6, y - 4);
		g.text(this.font, Component.translatable("hearth.economy.screen.you_carry", v.emeralds()), x + 26, y, EconomyGui.INK, false);
		y += 18;
		g.text(this.font, Component.translatable("hearth.economy.screen.changer_buys"), x + 6, y, EconomyGui.INK, false);
		EconomyGui.coinsRight(g, this.font, x + PAGE_W - 6, y, v.emeraldBuy(), EconomyGui.INK_GREEN);
		y += 11;
		int bw = (PAGE_W - 12 - 4) / 3;
		this.buttons.add(new Button(x + 6, y, bw, Component.translatable("hearth.economy.screen.sell_n", 1), v.emeralds() >= 1, null,
			EconomyActionPayload.SELL_EMERALDS, 0, 1));
		this.buttons.add(new Button(x + 8 + bw, y, bw, Component.translatable("hearth.economy.screen.sell_n", 8), v.emeralds() >= 8, null,
			EconomyActionPayload.SELL_EMERALDS, 0, 8));
		this.buttons.add(new Button(x + 10 + bw * 2, y, bw, Component.translatable("hearth.economy.screen.all"), v.emeralds() >= 1, null,
			EconomyActionPayload.SELL_EMERALDS, 0, EconomyActionPayload.ALL));
		y += 22;
		g.text(this.font, Component.translatable("hearth.economy.screen.changer_sells"), x + 6, y, EconomyGui.INK, false);
		EconomyGui.coinsRight(g, this.font, x + PAGE_W - 6, y, v.emeraldSell(), EconomyGui.INK);
		y += 11;
		this.buttons.add(new Button(x + 6, y, bw, Component.translatable("hearth.economy.screen.buy_n", 1),
			v.emeraldReserve() >= 1 && v.carried() >= v.emeraldSell(), null, EconomyActionPayload.BUY_EMERALDS, 0, 1));
		this.buttons.add(new Button(x + 8 + bw, y, bw, Component.translatable("hearth.economy.screen.buy_n", 8),
			v.emeraldReserve() >= 8 && v.carried() >= 8L * v.emeraldSell(), null, EconomyActionPayload.BUY_EMERALDS, 0, 8));
		y += 18;
		g.text(this.font, Component.translatable("hearth.economy.screen.changer_left", v.emeraldReserve()), x + 6, y, EconomyGui.INK_FADED, false);
		g.text(this.font, Component.translatable("hearth.economy.screen.treasury"), x + 4, y0 + MarketScreen.FOOTER_Y, EconomyGui.INK, false);
		EconomyGui.coinsRight(g, this.font, x + PAGE_W - 6, y0 + MarketScreen.FOOTER_Y, v.treasury(), EconomyGui.INK);
	}

	private void property(GuiGraphicsExtractor g, BankView v, int x0, int y0, int mx, int my) {
		int lx = x0 + LEFT_X;
		int rx = x0 + RIGHT_X;
		title(g, Component.translatable("hearth.economy.screen.for_sale_property"), lx, y0);
		List<BankView.PlotOffer> plots = v.plots();
		this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, plots.size() - ROWS));
		if (this.selected >= plots.size()) {
			this.selected = -1;
		}
		if (plots.isEmpty()) {
			List<FormattedCharSequence> lines = this.font.split(Component.translatable("hearth.economy.screen.no_property").withStyle(ChatFormatting.ITALIC), PAGE_W - 16);
			for (int i = 0; i < lines.size(); i++) {
				g.centeredText(this.font, lines.get(i), lx + PAGE_W / 2, y0 + 52 + i * 10, EconomyGui.INK_FADED);
			}
		}
		for (int i = 0; i < ROWS && this.scroll + i < plots.size(); i++) {
			BankView.PlotOffer p = plots.get(this.scroll + i);
			int y = y0 + ROWS_Y + i * ROW_H;
			boolean hover = EconomyGui.inside(mx, my, lx + 2, y, PAGE_W - 8, ROW_H);
			if (this.scroll + i == this.selected) {
				g.fill(lx + 2, y, lx + PAGE_W - 6, y + ROW_H - 1, 0x50704A20);
			} else if (hover) {
				g.fill(lx + 2, y, lx + PAGE_W - 6, y + ROW_H - 1, 0x28704A20);
			}
			g.item(new ItemStack(p.built() ? Items.OAK_DOOR : Items.GRASS_BLOCK), lx + 3, y + 1);
			int priceW = EconomyGui.coinsWidth(this.font, p.price());
			boolean yours = p.has(BankView.PlotOffer.YOURS);
			String name = EconomyGui.trim(this.font, name(p).getString(), PAGE_W - 34 - priceW);
			g.text(this.font, name, lx + 22, y + 5, yours ? EconomyGui.INK_GREEN : EconomyGui.INK, false);
			if (!yours) {
				EconomyGui.coinsRight(g, this.font, lx + PAGE_W - 8, y + 5, p.price(), p.has(BankView.PlotOffer.LOW_STANDING) ? EconomyGui.INK_FADED : EconomyGui.INK);
			}
		}
		g.text(this.font, Component.translatable("hearth.economy.screen.purse"), lx + 4, y0 + MarketScreen.FOOTER_Y, EconomyGui.INK, false);
		EconomyGui.coinsRight(g, this.font, lx + PAGE_W - 6, y0 + MarketScreen.FOOTER_Y, v.carried(), EconomyGui.INK);

		title(g, Component.translatable("hearth.economy.screen.deed"), rx, y0);
		if (this.selected < 0) {
			List<FormattedCharSequence> lines = this.font.split(Component.translatable("hearth.economy.screen.pick_property").withStyle(ChatFormatting.ITALIC), PAGE_W - 16);
			for (int i = 0; i < lines.size(); i++) {
				g.centeredText(this.font, lines.get(i), rx + PAGE_W / 2, y0 + 52 + i * 10, EconomyGui.INK_FADED);
			}
			return;
		}
		BankView.PlotOffer p = plots.get(this.selected);
		int y = y0 + 36;
		List<FormattedCharSequence> nameLines = this.font.split(name(p).copy().withStyle(ChatFormatting.BOLD), PAGE_W - 12);
		for (FormattedCharSequence line : nameLines) {
			g.text(this.font, line, rx + 6, y, EconomyGui.INK, false);
			y += 10;
		}
		g.text(this.font, Component.translatable("hearth.economy.screen.footprint", p.sizeX(), p.sizeZ()), rx + 6, y, EconomyGui.INK, false);
		y += 10;
		if (p.built()) {
			g.text(this.font, Component.translatable("hearth.economy.screen.storeys", p.storeys()), rx + 6, y, EconomyGui.INK, false);
			y += 10;
		}
		g.text(this.font, Component.translatable("hearth.economy.screen.needs_standing", p.minReputation(), v.reputation()), rx + 6, y,
			p.has(BankView.PlotOffer.LOW_STANDING) ? EconomyGui.INK_RED : EconomyGui.INK, false);
		y += 14;
		if (p.has(BankView.PlotOffer.YOURS)) {
			g.text(this.font, Component.translatable("hearth.economy.screen.yours"), rx + 6, y, EconomyGui.INK_GREEN, false);
			return;
		}
		g.text(this.font, Component.translatable("hearth.economy.screen.price"), rx + 6, y, EconomyGui.INK, false);
		EconomyGui.coinsRight(g, this.font, rx + PAGE_W - 6, y, p.price(), EconomyGui.INK);
		y += 14;
		boolean can = !p.has(BankView.PlotOffer.LOW_STANDING) && !p.has(BankView.PlotOffer.CANNOT_AFFORD);
		Component why = p.has(BankView.PlotOffer.LOW_STANDING) ? Component.translatable("hearth.economy.property.standing", p.minReputation())
			: p.has(BankView.PlotOffer.CANNOT_AFFORD) ? Component.translatable("hearth.economy.screen.cannot_afford") : null;
		this.buttons.add(new Button(rx + 6, y, PAGE_W - 12, Component.translatable("hearth.economy.screen.buy_deed"), can, why,
			EconomyActionPayload.BUY_PLOT, p.plot(), 0));
	}

	private static Component name(BankView.PlotOffer p) {
		String path = p.building();
		String pretty = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
		pretty = pretty.isEmpty() ? path : Character.toUpperCase(pretty.charAt(0)) + pretty.substring(1);
		Component building = Component.translatableWithFallback("hearth.archetype." + path.replace('/', '.'), pretty);
		return p.built() ? building : Component.translatable("hearth.economy.screen.plot_for", building);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			double mx = event.x();
			double my = event.y();
			BankView v = this.menu.view();
			if (v.hasBank() && my >= this.topPos + 3 && my < this.topPos + 15) {
				int x = this.leftPos + W - 8;
				int pw = this.font.width(Component.translatable("hearth.economy.screen.tab_property"));
				int bw = this.font.width(Component.translatable("hearth.economy.screen.tab_bank"));
				if (mx >= x - pw && mx < x) {
					this.tab = BankView.TAB_PROPERTY;
					EconomyGui.click();
					return true;
				}
				if (mx >= x - pw - 10 - bw && mx < x - pw - 10) {
					this.tab = BankView.TAB_BANK;
					EconomyGui.click();
					return true;
				}
			}
			for (Button b : this.buttons) {
				if (b.enabled && EconomyGui.inside(mx, my, b.x, b.y, b.w, BTN_H)) {
					EconomyGui.click();
					ClientPlayNetworking.send(new EconomyActionPayload(this.menu.containerId, b.action, b.index, b.amount));
					return true;
				}
			}
			if (this.tab == BankView.TAB_PROPERTY || !v.hasBank()) {
				int lx = this.leftPos + LEFT_X;
				for (int i = 0; i < ROWS && this.scroll + i < v.plots().size(); i++) {
					if (EconomyGui.inside(mx, my, lx + 2, this.topPos + ROWS_Y + i * ROW_H, PAGE_W - 8, ROW_H)) {
						this.selected = this.scroll + i;
						EconomyGui.click();
						return true;
					}
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if ((this.tab == BankView.TAB_PROPERTY || !this.menu.view().hasBank())
			&& EconomyGui.inside(x, y, this.leftPos + LEFT_X, this.topPos + ROWS_Y, PAGE_W, ROWS * ROW_H)) {
			this.scroll -= (int) Math.signum(scrollY);
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	private record Button(int x, int y, int w, Component label, boolean enabled, Component tooltip, int action, int index, long amount) {
	}
}
