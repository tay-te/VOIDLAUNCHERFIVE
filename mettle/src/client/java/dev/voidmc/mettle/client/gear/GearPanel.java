package dev.voidmc.mettle.client.gear;

import dev.voidmc.mettle.api.GearSlot;
import dev.voidmc.mettle.client.mixin.gear.GearSlotAccessor;
import dev.voidmc.mettle.gear.GearInventory;
import dev.voidmc.mettle.gear.GearRules;
import dev.voidmc.mettle.gear.menu.GearSlots;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * Dev fallback for the gear panel (UI.md §5; the real one is the Ultralight overlay): the worn item's pockets as plain
 * vanilla slots in a small panel docked right of the inventory, one group per worn item, laid out again every frame so
 * it follows the recipe book and narrows when the screen is narrow. Functional, deliberately unstyled.
 */
public final class GearPanel {
	public static final int FRAME_WIDTH = 176;
	private static final int GAP = 4;
	private static final Identifier SLOT = Identifier.withDefaultNamespace("container/slot");
	private static final int LABEL = 0xFF404040;

	/** The panel's rectangle relative to the frame, {x, y, w, h}; null when no worn item has pockets. */
	private static int[] rect;

	private GearPanel() {
	}

	/** The three worn slots' backgrounds, inside the vanilla frame above the offhand. */
	public static void drawWornSlots(GuiGraphicsExtractor graphics, int left, int top, int[] xs, int[] ys) {
		for (int i = 0; i < 3; i++) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, left + xs[i] - 1, top + ys[i] - 1, 18, 18);
		}
	}

	/** Places the pocket slots for this frame and draws the panel behind them. */
	public static void layoutAndDraw(GuiGraphicsExtractor graphics, Font font, AbstractContainerMenu menu, Player player, int left, int top, int screenWidth) {
		GearInventory gear = GearInventory.of(player);
		int[] counts = new int[3];
		int most = 0;
		for (GearSlot slot : GearSlot.values()) {
			counts[slot.ordinal()] = gear.pocketCount(slot);
			most = Math.max(most, counts[slot.ordinal()]);
		}
		if (most == 0 || menu.slots.size() < GearRules.END) {
			rect = null;
			return;
		}
		int cols = 3;
		for (int c : new int[] {9, 6, 5, 4}) {
			if (left + FRAME_WIDTH + GAP + 14 + 18 * c <= screenWidth - 2) {
				cols = c;
				break;
			}
		}
		cols = Math.max(2, Math.min(cols, most));
		int px = FRAME_WIDTH + GAP;
		int width = 14 + 18 * cols;

		int y = 6;
		int[] labelY = new int[3];
		for (GearSlot slot : GearSlot.values()) {
			int count = counts[slot.ordinal()];
			if (count == 0) {
				continue;
			}
			labelY[slot.ordinal()] = y;
			y += 10;
			int start = GearRules.pocketStart(slot);
			for (int i = 0; i < count; i++) {
				move(menu.slots.get(start + i), px + 8 + (i % cols) * 18, y + 1 + (i / cols) * 18);
			}
			y += ((count + cols - 1) / cols) * 18 + 4;
		}
		int height = y + 3;
		rect = new int[] {px, 0, width, height};

		frame(graphics, left + px, top, width, height);
		for (GearSlot slot : GearSlot.values()) {
			int count = counts[slot.ordinal()];
			if (count == 0) {
				continue;
			}
			graphics.text(font, Component.translatable("mettle.gear.panel." + slot.name().toLowerCase(Locale.ROOT)),
				left + px + 8, top + labelY[slot.ordinal()], LABEL, false);
			int start = GearRules.pocketStart(slot);
			for (int i = 0; i < count; i++) {
				Slot s = menu.slots.get(start + i);
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, left + s.x - 1, top + s.y - 1, 18, 18);
			}
		}
	}

	/** Whether a screen point is on the panel (clicks there must not drop the carried stack). */
	public static boolean contains(double mouseX, double mouseY, int left, int top) {
		int[] r = rect;
		return r != null && mouseX >= left + r[0] && mouseX < left + r[0] + r[2] && mouseY >= top + r[1] && mouseY < top + r[1] + r[3];
	}

	public static void forget() {
		rect = null;
	}

	static void move(Slot slot, int x, int y) {
		if (slot.x != x || slot.y != y) {
			((GearSlotAccessor) slot).mettle$setX(x);
			((GearSlotAccessor) slot).mettle$setY(y);
		}
	}

	/** A vanilla-looking container frame: black edge, light face, white top-left, grey bottom-right. */
	private static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x + 2, y, x + w - 2, y + h, 0xFF000000);
		g.fill(x, y + 2, x + w, y + h - 2, 0xFF000000);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF000000);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6);
		g.fill(x + 1, y + 1, x + w - 3, y + 3, 0xFFFFFFFF);
		g.fill(x + 1, y + 1, x + 3, y + h - 3, 0xFFFFFFFF);
		g.fill(x + 3, y + h - 3, x + w - 1, y + h - 1, 0xFF555555);
		g.fill(x + w - 3, y + 3, x + w - 1, y + h - 1, 0xFF555555);
		g.fill(x + 1, y + 1, x + 2, y + 2, 0xFF000000);
		g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, 0xFF000000);
	}

	/** Creative's inventory tab: the worn slots go right of the armour, the pockets out of sight (they are not shown there). */
	public static final int[] CREATIVE_X = {127, 145, 163};
	public static final int[] CREATIVE_Y = {20, 20, 20};

	public static void placeForCreative(Slot wrapper) {
		int index = wrapper.getContainerSlot();
		if (index >= GearRules.WORN_START && index < GearRules.BACK_POCKETS) {
			move(wrapper, CREATIVE_X[index - GearRules.WORN_START], CREATIVE_Y[index - GearRules.WORN_START]);
		} else if (index >= GearRules.BACK_POCKETS && index < GearRules.END) {
			move(wrapper, -2000, -2000);
		}
	}

	public static final int[] WORN_X = {GearSlots.WORN_X, GearSlots.WORN_X, GearSlots.WORN_X};
}
