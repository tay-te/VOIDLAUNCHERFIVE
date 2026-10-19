package dev.voidmc.hearth.client.law;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmc.hearth.law.net.LawPayloads;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * The pickpocketing minigame (docs/LIVING_WORLD.md §4): a peek into someone's pockets. Click an item to start lifting
 * it (click again to let go); an item takes longer the heavier it is. All the while the mark's awareness fills — the eye
 * opens — faster while your hand is moving, slower if they sleep. Take what you can and slip away (Esc) before it is
 * full, or be caught. The server keeps the clock; this screen animates between its updates.
 */
public class PickpocketScreen extends Screen {
	private static final int COLS = 3;
	private static final int WIDTH = 236;

	private LawPayloads.Pocket state;
	private long stateAt;
	private int left;
	private int top;
	private int panelHeight;
	private int closeIn = -1;
	private float lastAwareness;

	public PickpocketScreen(LawPayloads.Pocket pocket) {
		super(Component.translatable("hearth.law.pocket.title", pocket.victim()));
		this.state = pocket;
		this.stateAt = System.currentTimeMillis();
	}

	public int session() {
		return this.state.session();
	}

	public void update(LawPayloads.Pocket pocket) {
		if (pocket.lifting() < 0 && this.state.lifting() >= 0 && pocket.items().size() == this.state.items().size()
			&& !this.state.items().get(this.state.lifting()).isEmpty() && pocket.items().get(this.state.lifting()).isEmpty()) {
			LawGui.sound(SoundEvents.BUNDLE_REMOVE_ONE, 1.2F, 0.6F);
		}
		this.state = pocket;
		this.stateAt = System.currentTimeMillis();
		if (pocket.state() == LawPayloads.Pocket.CAUGHT) {
			LawGui.sound(SoundEvents.VILLAGER_NO, 0.9F, 0.7F);
			this.closeIn = 30;
		} else if (pocket.state() == LawPayloads.Pocket.DONE && this.closeIn < 0) {
			this.closeIn = 1;
		}
	}

	@Override
	protected void init() {
		int rows = Math.max(1, (this.state.items().size() + COLS - 1) / COLS);
		this.panelHeight = 74 + rows * 20 + 26;
		this.left = (this.width - WIDTH) / 2;
		this.top = (this.height - this.panelHeight) / 2;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.closeIn > 0 && --this.closeIn == 0) {
			this.onClose();
		}
	}

	/** Awareness now, run forward from the last update at the rate the server gave. */
	private float awareness() {
		float since = (System.currentTimeMillis() - this.stateAt) / 1000.0F;
		if (this.state.state() != LawPayloads.Pocket.OPEN) {
			return this.state.awareness();
		}
		return Math.min(1.0F, this.state.awareness() + this.state.awarenessRate() * Math.min(since, 0.5F));
	}

	private float liftProgress() {
		float since = (System.currentTimeMillis() - this.stateAt) / 1000.0F;
		return Math.min(1.0F, this.state.liftProgress() + this.state.liftRate() * Math.min(since, 0.5F));
	}

	private int slotX(int i) {
		int cols = Math.min(COLS, Math.max(1, this.state.items().size()));
		int gridW = cols * 22 - 4;
		return this.left + (WIDTH - gridW) / 2 + i % COLS * 22;
	}

	private int slotY(int i) {
		return this.top + 66 + i / COLS * 22;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		LawGui.leather(g, this.left, this.top, this.left + WIDTH, this.top + this.panelHeight);
		float aw = awareness();
		// the mark's awareness: the eye opening, and a bar filling from calm to alarm
		int ex = this.left + 14;
		int ey = this.top + 36;
		int frame = aw < 0.25F ? 0 : aw < 0.6F ? 1 : aw < 0.95F ? 2 : 3;
		LawGui.sprite(g, ex, ey, frame * 16, 16, 16, 10);
		int bx = ex + 22;
		int bw = WIDTH - 50;
		g.fill(bx - 1, ey, bx + bw + 1, ey + 9, 0xFF1A0E06);
		g.fill(bx, ey + 1, bx + Math.round(bw * aw), ey + 8, LawGui.mix(0xFF6FBF5A, 0xFFD8402F, aw));
		g.fill(bx, ey + 1, bx + Math.round(bw * aw), ey + 3, 0x40FFFFFF);
		if (aw > 0.75F && aw > this.lastAwareness && (System.currentTimeMillis() / 150) % 2 == 0) {
			g.outline(bx - 2, ey - 1, bw + 4, 11, 0xFFE0402C);
		}
		this.lastAwareness = aw;

		List<ItemStack> items = this.state.items();
		for (int i = 0; i < items.size(); i++) {
			int x = slotX(i);
			int y = slotY(i);
			LawGui.slot(g, x, y);
			if (i == this.state.lifting()) {
				int fill = Math.round(16 * liftProgress());
				g.fill(x + 1, y + 17 - fill, x + 17, y + 17, 0x6060D060);
			}
		}
		if (this.state.state() == LawPayloads.Pocket.CAUGHT) {
			g.fill(this.left, this.top, this.left + WIDTH, this.top + this.panelHeight, 0x60D02020);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		int cx = this.left + WIDTH / 2;
		g.centeredText(this.font, this.title, cx, this.top + 9, 0xFFF3E7CB);
		Component sub = this.state.asleep() ? Component.translatable("hearth.law.pocket.asleep") : Component.translatable("hearth.law.pocket.awake");
		g.centeredText(this.font, sub, cx, this.top + 21, this.state.asleep() ? 0xFFB8D0F0 : 0xFFE0C090);
		List<ItemStack> items = this.state.items();
		ItemStack hovered = ItemStack.EMPTY;
		for (int i = 0; i < items.size(); i++) {
			ItemStack stack = items.get(i);
			int x = slotX(i);
			int y = slotY(i);
			if (!stack.isEmpty()) {
				g.item(stack, x + 1, y + 1);
				g.itemDecorations(this.font, stack, x + 1, y + 1);
				if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
					g.fill(x + 1, y + 1, x + 17, y + 17, 0x80FFFFFF);
					hovered = stack;
				}
			}
		}
		Component foot = switch (this.state.state()) {
			case LawPayloads.Pocket.CAUGHT -> Component.translatable("hearth.law.pocket.caught_short").withStyle(ChatFormatting.RED);
			default -> Component.translatable("hearth.law.pocket.help");
		};
		g.centeredText(this.font, foot, cx, this.top + this.panelHeight - 16, 0xFFE8DCC0);
		if (!hovered.isEmpty()) {
			g.setTooltipForNextFrame(this.font, hovered, mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (this.state.state() == LawPayloads.Pocket.OPEN) {
			List<ItemStack> items = this.state.items();
			for (int i = 0; i < items.size(); i++) {
				int x = slotX(i);
				int y = slotY(i);
				if (event.x() >= x && event.x() < x + 18 && event.y() >= y && event.y() < y + 18 && !items.get(i).isEmpty()) {
					boolean stop = i == this.state.lifting() || event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
					ClientPlayNetworking.send(new LawPayloads.PocketAction(this.state.session(), stop ? LawPayloads.PocketAction.STOP
						: LawPayloads.PocketAction.LIFT, i));
					LawGui.sound(SoundEvents.ARMOR_EQUIP_LEATHER, 1.6F, 0.25F);
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
	public void removed() {
		super.removed();
		dev.voidmc.hearth.client.overlay.HearthViews.pocketClosed();
		if (this.state.state() == LawPayloads.Pocket.OPEN && Minecraft.getInstance().getConnection() != null) {
			ClientPlayNetworking.send(new LawPayloads.PocketAction(this.state.session(), LawPayloads.PocketAction.LEAVE, -1));
		}
	}
}
