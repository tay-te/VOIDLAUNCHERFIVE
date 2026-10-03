package dev.voidmc.hearth.client.economy;

import dev.voidmc.hearth.economy.coin.Coin;
import dev.voidmc.hearth.economy.item.EconomyItems;
import dev.voidmc.hearth.economy.item.PouchContents;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;

/** The coin pouch's tooltip picture: each coin it holds, as a little pile with its count, largest first. */
public final class PouchTooltip implements ClientTooltipComponent {
	private static final int CELL = 22;
	private final PouchContents contents;

	public PouchTooltip(PouchContents contents) {
		this.contents = contents;
	}

	private int kinds() {
		int n = 0;
		for (Coin coin : Coin.VALUES) {
			if (this.contents.count(coin) > 0) {
				n++;
			}
		}
		return n;
	}

	@Override
	public int getHeight(Font font) {
		return CELL + 2;
	}

	@Override
	public int getWidth(Font font) {
		return Math.max(1, kinds()) * CELL;
	}

	@Override
	public boolean showTooltipWithItemInHand() {
		return true;
	}

	@Override
	public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor g) {
		int cx = x;
		for (Coin coin : Coin.DESCENDING) {
			int n = this.contents.count(coin);
			if (n <= 0) {
				continue;
			}
			// show the pile model (count picks the model), with the real count as text
			ItemStack stack = new ItemStack(EconomyItems.coin(coin), Math.min(n, 64));
			g.item(stack, cx + 2, y + 2);
			g.itemDecorations(font, stack, cx + 2, y + 2, Integer.toString(n));
			cx += CELL;
		}
	}
}
