package dev.voidmc.hearth.client.economy;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.economy.bank.BankMenu;
import dev.voidmc.hearth.economy.bank.BankView;
import dev.voidmc.hearth.economy.coin.CoinFormat;
import dev.voidmc.hearth.economy.net.EconomyActionPayload;
import dev.voidmc.hearth.economy.property.PropertyNames;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The counter at the bank or the town hall. Bank: the player's account (deposit and withdraw in marks and crowns, or
 * everything) and the money changer (emeralds for coin and back). Property: houses, plots and businesses for sale on
 * the left, the chosen one on the right with a button to buy its deed (or, for your own business, to hire a hand).
 * Works: public buildings to pay toward. Contracts: supply contracts to take and deliver.
 */
public class BankScreen extends AbstractContainerScreen<BankMenu> {
	private static final Identifier BACKGROUND = Hearth.id("textures/gui/economy/bank.png");
	private static final int W = MarketScreen.W;
	private static final int H = MarketScreen.H;
	private static final int PAGE_W = MarketScreen.PAGE_W;
	private static final int LEFT_X = MarketScreen.LEFT_X;
	private static final int RIGHT_X = MarketScreen.RIGHT_X;
	private static final int ROW_H = 18;
	private static final int ROWS = 5;
	private static final int ROWS_Y = MarketScreen.ROWS_Y;
	private static final int BTN_H = 14;
	private static final String[] TAB_KEYS = {"hearth.economy.screen.tab_bank", "hearth.economy.screen.tab_property", "hearth.economy.screen.tab_works",
		"hearth.economy.screen.tab_contracts"};

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

	/** Whether a host (the VOID Hub) was offered this screen yet: once, on the first init (docs/OVERLAY.md §openers). */
	private boolean offered;

	/** On the first init, a host that registered an opener takes over (the menu stays open under its screen). */
	private boolean handedToHost(String screen) {
		if (this.offered) {
			return false;
		}
		this.offered = true;
		return dev.voidmc.hearth.client.overlay.HearthScreens.offer(screen, this);
	}

	@Override
	protected void init() {
		super.init();
		handedToHost(dev.voidmc.hearth.client.overlay.HearthScreens.BANK);
	}

	/** A new view arrived from the server: follow its tab (a purchase shows the property tab, a gift the works...). */
	void onView(BankView view) {
		if (view.tab() != this.tab) {
			this.tab = view.tab();
			this.scroll = 0;
			this.selected = -1;
		}
	}

	/** Dev tooling: choose a listed property, as a click would. */
	void devSelect(int index) {
		this.tab = BankView.TAB_PROPERTY;
		this.selected = index;
	}

	/** Dev tooling: show a tab, as a click would. */
	void devTab(int tab, int select) {
		this.tab = tab;
		this.selected = select;
	}

	private List<Integer> tabs(BankView v) {
		List<Integer> t = new ArrayList<>();
		if (v.hasBank()) {
			t.add(BankView.TAB_BANK);
		}
		t.add(BankView.TAB_PROPERTY);
		if (v.hasWorks()) {
			t.add(BankView.TAB_WORKS);
			t.add(BankView.TAB_CONTRACTS);
		}
		return t;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		g.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos, this.topPos, 0, 0, W, H, 512, 512);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
		BankView v = this.menu.view();
		if (!tabs(v).contains(this.tab)) {
			this.tab = tabs(v).getFirst();
		}
		List<Integer> tabs = tabs(v);
		int x = W - 8;
		int titleRoom = x;
		if (tabs.size() > 1) {
			for (int i = tabs.size() - 1; i >= 0; i--) {
				Component label = Component.translatable(TAB_KEYS[tabs.get(i)]);
				x -= this.font.width(label);
				g.text(this.font, label, x, 4, this.tab == tabs.get(i) ? 0xFFFFE9A0 : 0xFFB8A27A, true);
				x -= 8;
			}
			titleRoom = x - 8;
		}
		g.text(this.font, EconomyGui.trim(this.font, this.title.getString(), titleRoom - 8), 8, 4, EconomyGui.ON_WOOD, true);
		EconomyGui.purseColumn(g, this.font, MarketScreen.PURSE_X, MarketScreen.MARGIN_Y, Component.translatable("hearth.economy.screen.purse"), v.carried());
		EconomyGui.purseColumn(g, this.font, MarketScreen.CHEST_X, MarketScreen.MARGIN_Y, Component.translatable("hearth.economy.screen.chest"), v.treasury());
		Component note = !v.status().getString().isEmpty() ? v.status() : Component.translatable(switch (this.tab) {
			case BankView.TAB_BANK -> "hearth.economy.screen.deposit_hint";
			case BankView.TAB_WORKS -> "hearth.economy.screen.works_hint";
			case BankView.TAB_CONTRACTS -> "hearth.economy.screen.contracts_hint";
			default -> "hearth.economy.screen.property_hint";
		}).withStyle(ChatFormatting.ITALIC);
		List<FormattedCharSequence> lines = this.font.split(note, W - 24);
		for (int i = 0; i < Math.min(2, lines.size()); i++) {
			g.text(this.font, lines.get(i), 12, MarketScreen.NOTE_Y + i * 10, EconomyGui.INK, false);
		}
	}

	@Override
	public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractContents(g, mouseX, mouseY, a);
		BankView v = this.menu.view();
		this.buttons.clear();
		int x0 = this.leftPos;
		int y0 = this.topPos;
		switch (this.tab) {
			case BankView.TAB_BANK -> {
				if (v.hasBank()) {
					account(g, v, x0 + LEFT_X, y0);
					changer(g, v, x0 + RIGHT_X, y0);
				}
			}
			case BankView.TAB_WORKS -> works(g, v, x0, y0, mouseX, mouseY);
			case BankView.TAB_CONTRACTS -> contracts(g, v, x0, y0, mouseX, mouseY);
			default -> property(g, v, x0, y0, mouseX, mouseY);
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
		g.text(this.font, text, pageX + (PAGE_W - w) / 2, y0 + 18, EconomyGui.INK, false);
		g.fill(pageX + 8, y0 + 27, pageX + PAGE_W - 8, y0 + 28, 0x50604020);
	}

	private void hint(GuiGraphicsExtractor g, Component text, int pageX, int y0) {
		List<FormattedCharSequence> lines = this.font.split(text.copy().withStyle(ChatFormatting.ITALIC), PAGE_W - 16);
		for (int i = 0; i < lines.size(); i++) {
			g.centeredText(this.font, lines.get(i), pageX + PAGE_W / 2, y0 + 52 + i * 10, EconomyGui.INK_FADED);
		}
	}

	/** Draws a list's rows (icon, name, amount); returns nothing, selection is by click. */
	private void list(GuiGraphicsExtractor g, int size, int lx, int y0, int mx, int my, RowPainter painter) {
		this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, size - ROWS));
		if (this.selected >= size) {
			this.selected = -1;
		}
		for (int i = 0; i < ROWS && this.scroll + i < size; i++) {
			int y = y0 + ROWS_Y + i * ROW_H;
			boolean hover = EconomyGui.inside(mx, my, lx + 2, y, PAGE_W - 8, ROW_H);
			if (this.scroll + i == this.selected) {
				g.fill(lx + 2, y, lx + PAGE_W - 6, y + ROW_H - 1, 0x50704A20);
			} else if (hover) {
				g.fill(lx + 2, y, lx + PAGE_W - 6, y + ROW_H - 1, 0x28704A20);
			}
			painter.paint(this.scroll + i, lx, y);
		}
		if (size > ROWS) {
			int track = ROWS * ROW_H;
			int knob = Math.max(8, track * ROWS / size);
			int top = y0 + ROWS_Y + (track - knob) * this.scroll / Math.max(1, size - ROWS);
			g.fill(lx + PAGE_W - 5, y0 + ROWS_Y, lx + PAGE_W - 3, y0 + ROWS_Y + track, 0x30402810);
			g.fill(lx + PAGE_W - 5, top, lx + PAGE_W - 3, top + knob, 0xA0603A18);
		}
	}

	// ---------------------------------------------------------------- bank

	private void account(GuiGraphicsExtractor g, BankView v, int x, int y0) {
		title(g, Component.translatable("hearth.economy.screen.account"), x, y0);
		int y = y0 + 33;
		g.text(this.font, Component.translatable("hearth.economy.screen.on_deposit"), x + 6, y, EconomyGui.INK, false);
		EconomyGui.coinsRight(g, this.font, x + PAGE_W - 6, y, v.deposit(), EconomyGui.INK_GREEN);
		y += 12;
		g.text(this.font, Component.translatable("hearth.economy.screen.carried"), x + 6, y, EconomyGui.INK, false);
		EconomyGui.coinsRight(g, this.font, x + PAGE_W - 6, y, v.carried(), EconomyGui.INK);
		y += 16;
		g.text(this.font, Component.translatable("hearth.economy.screen.deposit"), x + 6, y, EconomyGui.INK, false);
		y += 10;
		amountButtons(x, y, EconomyActionPayload.DEPOSIT, v.carried());
		y += 20;
		g.text(this.font, Component.translatable("hearth.economy.screen.withdraw"), x + 6, y, EconomyGui.INK, false);
		y += 10;
		amountButtons(x, y, EconomyActionPayload.WITHDRAW, v.deposit());
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
		int y = y0 + 34;
		g.item(new ItemStack(Items.EMERALD), x + 6, y - 4);
		g.text(this.font, Component.translatable("hearth.economy.screen.you_carry", v.emeralds()), x + 26, y, EconomyGui.INK, false);
		y += 16;
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
		y += 19;
		g.text(this.font, Component.translatable("hearth.economy.screen.changer_sells"), x + 6, y, EconomyGui.INK, false);
		EconomyGui.coinsRight(g, this.font, x + PAGE_W - 6, y, v.emeraldSell(), EconomyGui.INK);
		y += 11;
		this.buttons.add(new Button(x + 6, y, bw, Component.translatable("hearth.economy.screen.buy_n", 1),
			v.emeraldReserve() >= 1 && v.carried() >= v.emeraldSell(), null, EconomyActionPayload.BUY_EMERALDS, 0, 1));
		this.buttons.add(new Button(x + 8 + bw, y, bw, Component.translatable("hearth.economy.screen.buy_n", 8),
			v.emeraldReserve() >= 8 && v.carried() >= 8L * v.emeraldSell(), null, EconomyActionPayload.BUY_EMERALDS, 0, 8));
		y += 16;
		g.text(this.font, Component.translatable("hearth.economy.screen.changer_left", v.emeraldReserve()), x + 6, y, EconomyGui.INK_FADED, false);
	}

	// ---------------------------------------------------------------- property and businesses

	private void property(GuiGraphicsExtractor g, BankView v, int x0, int y0, int mx, int my) {
		int lx = x0 + LEFT_X;
		int rx = x0 + RIGHT_X;
		title(g, Component.translatable("hearth.economy.screen.for_sale_property"), lx, y0);
		List<BankView.PlotOffer> plots = v.plots();
		if (plots.isEmpty()) {
			hint(g, Component.translatable("hearth.economy.screen.no_property"), lx, y0);
		}
		list(g, plots.size(), lx, y0, mx, my, (i, x, y) -> {
			BankView.PlotOffer p = plots.get(i);
			boolean business = p.has(BankView.PlotOffer.BUSINESS);
			g.item(new ItemStack(business ? Items.SMITHING_TABLE : p.built() ? Items.OAK_DOOR : Items.GRASS_BLOCK), x + 3, y + 1);
			boolean yours = p.has(BankView.PlotOffer.YOURS);
			int priceW = yours ? 0 : EconomyGui.coinsWidth(this.font, p.price());
			String name = EconomyGui.trim(this.font, PropertyNames.building(p.building()).getString(), PAGE_W - 34 - priceW);
			g.text(this.font, name, x + 22, y + 5, yours ? EconomyGui.INK_GREEN : EconomyGui.INK, false);
			if (!yours) {
				EconomyGui.coinsRight(g, this.font, x + PAGE_W - 8, y + 5, p.price(), p.has(BankView.PlotOffer.LOW_STANDING) ? EconomyGui.INK_FADED : EconomyGui.INK);
			}
		});

		title(g, Component.translatable("hearth.economy.screen.deed"), rx, y0);
		if (this.selected < 0) {
			hint(g, Component.translatable("hearth.economy.screen.pick_property"), rx, y0);
			return;
		}
		BankView.PlotOffer p = plots.get(this.selected);
		boolean business = p.has(BankView.PlotOffer.BUSINESS);
		int y = y0 + 32;
		for (FormattedCharSequence line : this.font.split(name(p).copy().withStyle(ChatFormatting.BOLD), PAGE_W - 12)) {
			g.text(this.font, line, rx + 6, y, EconomyGui.INK, false);
			y += 10;
		}
		g.text(this.font, Component.translatable("hearth.economy.screen.footprint", p.sizeX(), p.sizeZ()), rx + 6, y, EconomyGui.INK, false);
		y += 10;
		if (business) {
			g.text(this.font, Component.translatable("hearth.economy.screen.workers", p.workers()), rx + 6, y, EconomyGui.INK, false);
			y += 10;
			g.text(this.font, Component.translatable(p.has(BankView.PlotOffer.YOURS) ? "hearth.economy.screen.earned_yesterday" : "hearth.economy.screen.earns"),
				rx + 6, y, EconomyGui.INK, false);
			EconomyGui.coinsRight(g, this.font, rx + PAGE_W - 6, y, p.income(), EconomyGui.INK_GREEN);
			y += 10;
		} else if (p.built()) {
			g.text(this.font, Component.translatable("hearth.economy.screen.storeys", p.storeys()), rx + 6, y, EconomyGui.INK, false);
			y += 10;
		}
		if (p.has(BankView.PlotOffer.YOURS)) {
			g.text(this.font, Component.translatable("hearth.economy.screen.yours"), rx + 6, y, EconomyGui.INK_GREEN, false);
			y += 12;
			if (business) {
				Component why = p.has(BankView.PlotOffer.CAN_HIRE) ? CoinFormat.format(p.price()) : Component.translatable("hearth.economy.screen.cannot_hire");
				this.buttons.add(new Button(rx + 6, y, PAGE_W - 12, Component.translatable("hearth.economy.screen.hire"), p.has(BankView.PlotOffer.CAN_HIRE)
					&& v.carried() >= p.price(), why, EconomyActionPayload.HIRE, p.plot(), 0));
			}
			return;
		}
		g.text(this.font, Component.translatable("hearth.economy.screen.needs_standing", p.minReputation(), v.reputation()), rx + 6, y,
			p.has(BankView.PlotOffer.LOW_STANDING) ? EconomyGui.INK_RED : EconomyGui.INK, false);
		y += 12;
		g.text(this.font, Component.translatable("hearth.economy.screen.price"), rx + 6, y, EconomyGui.INK, false);
		EconomyGui.coinsRight(g, this.font, rx + PAGE_W - 6, y, p.price(), EconomyGui.INK);
		y += 11;
		boolean can = !p.has(BankView.PlotOffer.LOW_STANDING) && !p.has(BankView.PlotOffer.CANNOT_AFFORD);
		Component why = p.has(BankView.PlotOffer.LOW_STANDING) ? Component.translatable("hearth.economy.property.standing", p.minReputation())
			: p.has(BankView.PlotOffer.CANNOT_AFFORD) ? Component.translatable("hearth.economy.screen.cannot_afford") : null;
		this.buttons.add(new Button(rx + 6, Math.min(y, y0 + 104), PAGE_W - 12, Component.translatable("hearth.economy.screen.buy_deed"), can, why,
			EconomyActionPayload.BUY_PLOT, p.plot(), 0));
	}

	private static Component name(BankView.PlotOffer p) {
		if (p.has(BankView.PlotOffer.BUSINESS)) {
			return Component.translatable("hearth.economy.screen.business", PropertyNames.lower(p.building()));
		}
		return p.built() ? PropertyNames.building(p.building()) : Component.translatable("hearth.economy.screen.plot_for", PropertyNames.lower(p.building()));
	}

	// ---------------------------------------------------------------- works

	private void works(GuiGraphicsExtractor g, BankView v, int x0, int y0, int mx, int my) {
		int lx = x0 + LEFT_X;
		int rx = x0 + RIGHT_X;
		title(g, Component.translatable("hearth.economy.screen.works"), lx, y0);
		List<BankView.ProjectOffer> projects = v.projects();
		if (projects.isEmpty()) {
			hint(g, Component.translatable("hearth.economy.screen.no_works"), lx, y0);
		}
		list(g, projects.size(), lx, y0, mx, my, (i, x, y) -> {
			BankView.ProjectOffer p = projects.get(i);
			g.item(new ItemStack(p.rising() ? Items.SCAFFOLDING : Items.PAPER), x + 3, y + 1);
			String name = EconomyGui.trim(this.font, PropertyNames.building(p.building()).getString(), PAGE_W - 34 - (p.given() > 0 ? 10 : 0));
			g.text(this.font, name, x + 22, y + 5, p.yours() > 0 ? EconomyGui.INK_GREEN : EconomyGui.INK, false);
			if (p.given() > 0) {
				EconomyGui.hammer(g, x + PAGE_W - 18, y + 4);
			}
		});

		title(g, Component.translatable("hearth.economy.screen.project"), rx, y0);
		if (this.selected < 0) {
			hint(g, Component.translatable("hearth.economy.screen.pick_project"), rx, y0);
			return;
		}
		BankView.ProjectOffer p = projects.get(this.selected);
		int y = y0 + 32;
		for (FormattedCharSequence line : this.font.split(PropertyNames.building(p.building()).copy().withStyle(ChatFormatting.BOLD), PAGE_W - 12)) {
			g.text(this.font, line, rx + 6, y, EconomyGui.INK, false);
			y += 10;
		}
		g.text(this.font, p.rising() ? Component.translatable("hearth.economy.screen.stage", p.stage() + 1, Math.max(1, p.stages()))
			: Component.translatable("hearth.economy.screen.planned"), rx + 6, y, p.stalled() ? EconomyGui.INK_RED : EconomyGui.INK, false);
		y += 10;
		if (p.cost() > 0) {
			g.text(this.font, Component.translatable("hearth.economy.screen.materials"), rx + 6, y, EconomyGui.INK, false);
			EconomyGui.coinsRight(g, this.font, rx + PAGE_W - 6, y, p.cost(), EconomyGui.INK);
			y += 10;
		}
		g.text(this.font, Component.translatable("hearth.economy.screen.given"), rx + 6, y, EconomyGui.INK, false);
		EconomyGui.coinsRight(g, this.font, rx + PAGE_W - 6, y, p.given(), EconomyGui.INK_GREEN);
		y += 10;
		if (p.yours() > 0) {
			g.text(this.font, Component.translatable("hearth.economy.screen.given_yours"), rx + 6, y, EconomyGui.INK_FADED, false);
			EconomyGui.coinsRight(g, this.font, rx + PAGE_W - 6, y, p.yours(), EconomyGui.INK_FADED);
			y += 10;
		}
		y = Math.max(y + 2, y0 + 90);
		int bw = (PAGE_W - 12 - 4) / 3;
		long rest = Math.max(0, p.cost() - p.given());
		this.buttons.add(new Button(rx + 6, y, bw, shortAmount(100), v.carried() >= 100, CoinFormat.format(100), EconomyActionPayload.FUND, p.plot(), 100));
		this.buttons.add(new Button(rx + 8 + bw, y, bw, shortAmount(1000), v.carried() >= 1000, CoinFormat.format(1000), EconomyActionPayload.FUND, p.plot(),
			1000));
		this.buttons.add(new Button(rx + 10 + bw * 2, y, bw, Component.translatable("hearth.economy.screen.the_rest"), rest > 0 && v.carried() >= rest,
			rest > 0 ? CoinFormat.format(rest) : Component.translatable("hearth.economy.screen.no_estimate"), EconomyActionPayload.FUND, p.plot(), rest));
	}

	// ---------------------------------------------------------------- contracts

	private void contracts(GuiGraphicsExtractor g, BankView v, int x0, int y0, int mx, int my) {
		int lx = x0 + LEFT_X;
		int rx = x0 + RIGHT_X;
		title(g, Component.translatable("hearth.economy.screen.contracts"), lx, y0);
		List<BankView.ContractOffer> contracts = v.contracts();
		if (contracts.isEmpty()) {
			hint(g, Component.translatable("hearth.economy.screen.no_contracts"), lx, y0);
		}
		list(g, contracts.size(), lx, y0, mx, my, (i, x, y) -> {
			BankView.ContractOffer c = contracts.get(i);
			g.item(new ItemStack(icon(c.goods())), x + 3, y + 1);
			Component name = Component.translatable("hearth.economy.screen.contract_row", c.units(), Component.translatable("hearth.goods." + c.goods()));
			g.text(this.font, EconomyGui.trim(this.font, name.getString(), PAGE_W - 30), x + 22, y + 5,
				c.has(BankView.ContractOffer.TAKEN) ? EconomyGui.INK_GREEN : EconomyGui.INK, false);
		});

		title(g, Component.translatable("hearth.economy.screen.contract"), rx, y0);
		if (this.selected < 0) {
			hint(g, Component.translatable("hearth.economy.screen.pick_contract"), rx, y0);
			return;
		}
		BankView.ContractOffer c = contracts.get(this.selected);
		boolean taken = c.has(BankView.ContractOffer.TAKEN);
		int y = y0 + 32;
		Component what = Component.translatable("hearth.economy.screen.contract_what", c.units(), Component.translatable("hearth.goods." + c.goods()));
		for (FormattedCharSequence line : this.font.split(what.copy().withStyle(ChatFormatting.BOLD), PAGE_W - 12)) {
			g.text(this.font, line, rx + 6, y, EconomyGui.INK, false);
			y += 10;
		}
		g.text(this.font, Component.translatable("hearth.economy.screen.contract_pays"), rx + 6, y, EconomyGui.INK, false);
		EconomyGui.coinsRight(g, this.font, rx + PAGE_W - 6, y, c.total(), EconomyGui.INK_GREEN);
		y += 10;
		if (taken) {
			g.text(this.font, Component.translatable("hearth.economy.screen.contract_done", c.delivered(), c.units()), rx + 6, y, EconomyGui.INK, false);
			y += 10;
		}
		g.text(this.font, Component.translatable(taken ? "hearth.economy.screen.contract_due" : "hearth.economy.screen.contract_offered", c.daysLeft()), rx + 6, y,
			taken && c.daysLeft() <= 1 ? EconomyGui.INK_RED : EconomyGui.INK, false);
		y = Math.max(y + 12, y0 + 90);
		if (taken) {
			this.buttons.add(new Button(rx + 6, y, PAGE_W - 12, Component.translatable("hearth.economy.screen.deliver"), c.has(BankView.ContractOffer.CARRYING),
				c.has(BankView.ContractOffer.CARRYING) ? null : Component.translatable("hearth.economy.screen.carry_none"), EconomyActionPayload.DELIVER, c.id(), 0));
		} else {
			this.buttons.add(new Button(rx + 6, y, PAGE_W - 12, Component.translatable("hearth.economy.screen.accept"), true, null,
				EconomyActionPayload.ACCEPT_CONTRACT, c.id(), 0));
		}
	}

	/** A picture for a goods in the contract list. */
	static Item icon(String goods) {
		return switch (goods) {
			case "food" -> Items.BREAD;
			case "wood" -> Items.OAK_PLANKS;
			case "stone" -> Items.COBBLESTONE;
			case "clay" -> Items.CLAY;
			case "glass" -> Items.GLASS;
			case "metal" -> Items.IRON_INGOT;
			case "cloth" -> Items.CARPET.white();
			case "fuel" -> Items.CHARCOAL;
			case "tools" -> Items.IRON_PICKAXE;
			case "luxury" -> Items.AMETHYST_SHARD;
			case "logs" -> Items.OAK_LOG;
			case "ore" -> Items.RAW_IRON;
			case "dressed_stone" -> Items.STONE_BRICKS;
			case "brick" -> Items.BRICKS;
			case "sand" -> Items.SAND;
			case "grain" -> Items.WHEAT;
			case "flour" -> Items.SUGAR;
			case "meat" -> Items.BEEF;
			case "fish" -> Items.COD;
			case "wool" -> Items.WOOL.white();
			case "leather" -> Items.LEATHER;
			case "wax" -> Items.HONEYCOMB;
			case "candles" -> Items.CANDLE;
			case "paper" -> Items.PAPER;
			case "books" -> Items.BOOK;
			case "dye" -> Items.DYE.red();
			case "salt" -> Items.SUGAR;
			case "produce" -> Items.APPLE;
			default -> Items.COCOA_BEANS;
		};
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			double mx = event.x();
			double my = event.y();
			BankView v = this.menu.view();
			List<Integer> tabs = tabs(v);
			if (tabs.size() > 1 && my >= this.topPos + 2 && my < this.topPos + 14) {
				int x = this.leftPos + W - 8;
				for (int i = tabs.size() - 1; i >= 0; i--) {
					int w = this.font.width(Component.translatable(TAB_KEYS[tabs.get(i)]));
					if (mx >= x - w && mx < x) {
						this.tab = tabs.get(i);
						this.scroll = 0;
						this.selected = -1;
						EconomyGui.click();
						return true;
					}
					x -= w + 8;
				}
			}
			for (Button b : this.buttons) {
				if (b.enabled && EconomyGui.inside(mx, my, b.x, b.y, b.w, BTN_H)) {
					EconomyGui.click();
					ClientPlayNetworking.send(new EconomyActionPayload(this.menu.containerId, b.action, b.index, b.amount));
					return true;
				}
			}
			if (this.tab != BankView.TAB_BANK) {
				int size = switch (this.tab) {
					case BankView.TAB_WORKS -> v.projects().size();
					case BankView.TAB_CONTRACTS -> v.contracts().size();
					default -> v.plots().size();
				};
				int lx = this.leftPos + LEFT_X;
				for (int i = 0; i < ROWS && this.scroll + i < size; i++) {
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
		if (this.tab != BankView.TAB_BANK && EconomyGui.inside(x, y, this.leftPos + LEFT_X, this.topPos + ROWS_Y, PAGE_W, ROWS * ROW_H)) {
			this.scroll -= (int) Math.signum(scrollY);
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@FunctionalInterface
	private interface RowPainter {
		void paint(int index, int x, int y);
	}

	private record Button(int x, int y, int w, Component label, boolean enabled, Component tooltip, int action, int index, long amount) {
	}
}
