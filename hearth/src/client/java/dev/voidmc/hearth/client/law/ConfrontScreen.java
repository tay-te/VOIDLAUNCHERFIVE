package dev.voidmc.hearth.client.law;

import dev.voidmc.hearth.law.net.LawPayloads;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * A guard stops you (or a witness demands an account): their figure, what they say, the bounty, and your choices with
 * their odds — pay, come quietly, the pillory, persuade, bribe, run, fight (or, for a witness, hush money, a threat,
 * walking away). Closing the screen is walking away, which a guard takes as running. The server decides everything.
 */
public class ConfrontScreen extends Screen {
	private static final int WIDTH = 300;
	private static final int FIGURE = 56;

	private final int entity;
	private String name;
	private Component speech;
	private long bounty;
	private List<LawPayloads.Choice> choices;
	private List<FormattedCharSequence> lines = List.of();
	private int left;
	private int top;
	private int panelHeight;
	private boolean answered;

	public ConfrontScreen(LawPayloads.Confront c) {
		super(Component.literal(c.name()));
		this.entity = c.entity();
		this.name = c.name();
		this.speech = c.speech();
		this.bounty = c.bounty();
		this.choices = c.choices();
	}

	public int entity() {
		return this.entity;
	}

	public void update(LawPayloads.Confront c) {
		this.name = c.name();
		this.speech = c.speech();
		this.bounty = c.bounty();
		this.choices = c.choices();
		this.answered = false;
		rebuildWidgets();
	}

	/** The server closed it (an answer was final): leave without counting it as walking away. */
	public void closeQuietly() {
		this.answered = true;
		this.onClose();
	}

	@Override
	protected void init() {
		this.lines = this.font.split(Component.literal("“" + this.speech.getString() + "”"), WIDTH - FIGURE - 30);
		int textHeight = Math.max(FIGURE + 4, 30 + this.lines.size() * 10);
		this.panelHeight = 12 + textHeight + 8 + this.choices.size() * 18 + 10;
		this.left = (this.width - WIDTH) / 2;
		this.top = Math.max(8, (this.height - this.panelHeight) / 2);
		int y = this.top + 12 + textHeight + 8;
		for (LawPayloads.Choice c : this.choices) {
			ChoiceEntry entry = new ChoiceEntry(this.left + 12, y, WIDTH - 24, 16, c);
			entry.active = c.enabled();
			this.addRenderableWidget(entry);
			y += 18;
		}
	}

	@Override
	public void tick() {
		super.tick();
		Entity e = this.minecraft.level == null ? null : this.minecraft.level.getEntity(this.entity);
		if (e == null || !e.isAlive()) {
			this.answered = true;
			this.onClose();
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		int x0 = this.left;
		int y0 = this.top;
		LawGui.parchment(g, x0, y0, x0 + WIDTH, y0 + this.panelHeight);
		// their figure, in a dark frame
		int fx = x0 + 12;
		int fy = y0 + 12;
		g.fill(fx - 2, fy - 2, fx + FIGURE + 2, fy + FIGURE + 2, 0xFF5A4026);
		g.fill(fx, fy, fx + FIGURE, fy + FIGURE, 0xFF2A2420);
		Entity e = this.minecraft.level == null ? null : this.minecraft.level.getEntity(this.entity);
		if (e instanceof LivingEntity living) {
			InventoryScreen.extractEntityInInventoryFollowsMouse(g, fx, fy, fx + FIGURE, fy + FIGURE, 30, 0.35F, mouseX, mouseY, living);
		}
		int tx = fx + FIGURE + 10;
		g.text(this.font, Component.literal(this.name).withStyle(ChatFormatting.BOLD), tx, fy + 2, LawGui.INK, false);
		if (this.bounty > 0) {
			g.text(this.font, Component.translatable("hearth.law.confront.bounty", this.bounty), tx, fy + 13, 0xFF9A2A1A, false);
		}
		int sy = fy + (this.bounty > 0 ? 28 : 16);
		for (FormattedCharSequence line : this.lines) {
			g.text(this.font, line, tx, sy, LawGui.INK, false);
			sy += 10;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** A confrontation is answered, not dismissed: running is one of the answers. */
	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void removed() {
		super.removed();
		dev.voidmc.hearth.client.overlay.HearthViews.confrontClosed(this.entity);
		if (!this.answered && Minecraft.getInstance().getConnection() != null) {
			ClientPlayNetworking.send(new LawPayloads.ConfrontChoice(this.entity, ""));
		}
	}

	private final class ChoiceEntry extends AbstractButton {
		private final LawPayloads.Choice choice;

		ChoiceEntry(int x, int y, int w, int h, LawPayloads.Choice choice) {
			super(x, y, w, h, choice.label());
			this.choice = choice;
		}

		@Override
		public void onPress(InputWithModifiers input) {
			ConfrontScreen.this.answered = true;
			ClientPlayNetworking.send(new LawPayloads.ConfrontChoice(ConfrontScreen.this.entity, this.choice.id()));
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			boolean hot = this.active && this.isHoveredOrFocused();
			if (hot) {
				g.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), LawGui.PARCHMENT_DARK);
			}
			int color = this.active ? LawGui.INK : 0xFFA89878;
			boolean danger = this.choice.id().equals("resist") || this.choice.id().equals("flee") || this.choice.id().equals("threaten");
			if (danger && this.active) {
				color = 0xFF8A2A1A;
			}
			g.text(ConfrontScreen.this.font, Component.literal(hot ? "▶ " : "• "), this.getX() + 4, this.getY() + 4, hot ? 0xFF5A4026 : LawGui.INK_SOFT, false);
			g.text(ConfrontScreen.this.font, this.getMessage(), this.getX() + 16, this.getY() + 4, color, false);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}
}
