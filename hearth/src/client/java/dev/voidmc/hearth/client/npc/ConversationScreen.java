package dev.voidmc.hearth.client.npc;

import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.talk.TalkPayloads;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Talking to a folk: a sheet of parchment with their face (drawn from their own composed skin), name, trade and town,
 * what they say (written out as they say it), and the options every module offers. The server decides all of it;
 * picking an option sends its id back.
 */
public class ConversationScreen extends Screen {
	private static final int WIDTH = 300;
	private static final int FACE = 40;
	private static final int PAPER = 0xFFE9DAB6;
	private static final int PAPER_LIGHT = 0xFFF3E7CB;
	private static final int PAPER_DARK = 0xFFD6C192;
	private static final int EDGE = 0xFF5A4026;
	private static final int INK = 0xFF3A2A1A;
	private static final int INK_SOFT = 0xFF7A6040;
	/** Characters of speech revealed per tick. */
	private static final float REVEAL = 2.2F;

	private final int entityId;
	private String name;
	private String title;
	private String settlement;
	private Component speech;
	private List<TalkPayloads.Option> options;
	private float revealed;
	private List<FormattedCharSequence> lines = List.of();
	private int left;
	private int top;
	private int panelHeight;

	public ConversationScreen(TalkPayloads.Show show) {
		super(Component.literal(show.name()));
		this.entityId = show.entity();
		this.name = show.name();
		this.title = show.title();
		this.settlement = show.settlement();
		this.speech = show.speech();
		this.options = show.options();
	}

	public int entityId() {
		return this.entityId;
	}

	/** New speech or options from the server for the open conversation. */
	public void update(TalkPayloads.Show show) {
		boolean newSpeech = !show.speech().getString().equals(this.speech.getString());
		this.name = show.name();
		this.title = show.title();
		this.settlement = show.settlement();
		this.speech = show.speech();
		this.options = show.options();
		if (newSpeech) {
			this.revealed = 0;
		}
		rebuildWidgets();
	}

	@Override
	protected void init() {
		int textWidth = WIDTH - 24;
		this.lines = this.font.split(Component.literal("“" + this.speech.getString() + "”"), textWidth);
		int speechHeight = Math.max(2, this.lines.size()) * 10;
		int optionsHeight = this.options.size() * 18;
		this.panelHeight = 14 + FACE + 10 + speechHeight + 12 + optionsHeight + 10;
		this.left = (this.width - WIDTH) / 2;
		this.top = Math.max(8, (this.height - this.panelHeight) / 2);
		int y = this.top + 14 + FACE + 10 + speechHeight + 12;
		for (TalkPayloads.Option o : this.options) {
			this.addRenderableWidget(new OptionEntry(this.left + 12, y, WIDTH - 24, 16, o));
			y += 18;
		}
	}

	@Override
	public void tick() {
		super.tick();
		this.revealed += REVEAL;
		Entity e = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(this.entityId);
		if (e == null || !e.isAlive() || this.minecraft.player == null || e.distanceToSqr(this.minecraft.player) > 10 * 10) {
			this.onClose();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		int x0 = this.left;
		int y0 = this.top;
		int x1 = x0 + WIDTH;
		int y1 = y0 + this.panelHeight;
		// A sheet of parchment: dark edge, paper, a lighter inner band, a few darker fibres.
		g.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, EDGE);
		g.fill(x0, y0, x1, y1, PAPER);
		g.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, PAPER_LIGHT);
		g.fill(x0 + 4, y0 + 4, x1 - 4, y1 - 4, PAPER);
		for (int i = 0; i < 9; i++) {
			int fx = x0 + 10 + (i * 97 + this.entityId * 13) % (WIDTH - 40);
			int fy = y0 + 8 + (i * 53 + this.entityId * 7) % Math.max(1, this.panelHeight - 16);
			g.fill(fx, fy, fx + 6 + (i % 3) * 5, fy + 1, PAPER_DARK);
		}
		// Torn corners.
		g.fill(x0 - 2, y0 - 2, x0 + 2, y0 + 2, 0);
		g.fill(x1 - 2, y1 - 2, x1 + 2, y1 + 2, 0);

		// The face, from the folk's own skin: head and hat layer.
		int fx = x0 + 12;
		int fy = y0 + 12;
		g.fill(fx - 2, fy - 2, fx + FACE + 2, fy + FACE + 2, EDGE);
		Identifier skin = skin();
		if (skin != null) {
			g.blit(RenderPipelines.GUI_TEXTURED, skin, fx, fy, 8.0F, 8.0F, FACE, FACE, 8, 8, 64, 64);
			g.blit(RenderPipelines.GUI_TEXTURED, skin, fx, fy, 40.0F, 8.0F, FACE, FACE, 8, 8, 64, 64);
		} else {
			g.fill(fx, fy, fx + FACE, fy + FACE, PAPER_DARK);
		}

		int tx = fx + FACE + 10;
		g.text(this.font, Component.literal(this.name).withStyle(ChatFormatting.BOLD), tx, fy + 4, INK, false);
		Component sub = Component.translatable(this.title).append(Component.literal(" of " + this.settlement));
		g.text(this.font, sub, tx, fy + 17, INK_SOFT, false);
		g.fill(tx, fy + 30, x1 - 12, fy + 31, PAPER_DARK);

		// What they say, written out as they say it.
		int sy = y0 + 14 + FACE + 10;
		int budget = (int) this.revealed;
		for (FormattedCharSequence line : this.lines) {
			if (budget <= 0) {
				break;
			}
			FormattedCharSequence shown = truncate(line, budget);
			budget -= length(line);
			g.text(this.font, shown, x0 + 12, sy, INK, false);
			sy += 10;
		}
	}

	private @Nullable Identifier skin() {
		if (Minecraft.getInstance().level == null) {
			return null;
		}
		Entity e = Minecraft.getInstance().level.getEntity(this.entityId);
		return e instanceof Folk folk ? FolkSkins.get(folk.look()) : null;
	}

	private static int length(FormattedCharSequence seq) {
		int[] n = {0};
		seq.accept((i, style, cp) -> {
			n[0]++;
			return true;
		});
		return n[0];
	}

	private static FormattedCharSequence truncate(FormattedCharSequence seq, int max) {
		return sink -> {
			int[] n = {0};
			return seq.accept((i, style, cp) -> n[0]++ < max ? sink.accept(i, style, cp) : false);
		};
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void removed() {
		super.removed();
		if (Minecraft.getInstance().getConnection() != null) {
			ClientPlayNetworking.send(new TalkPayloads.End(this.entityId));
		}
	}

	/** One option: a quill mark and the words, highlighted under the mouse. */
	private final class OptionEntry extends AbstractButton {
		private final TalkPayloads.Option option;

		OptionEntry(int x, int y, int w, int h, TalkPayloads.Option option) {
			super(x, y, w, h, option.label());
			this.option = option;
		}

		@Override
		public void onPress(InputWithModifiers input) {
			// Show the rest of the line at once if they click before it is written out.
			ConversationScreen.this.revealed = Float.MAX_VALUE / 2;
			ClientPlayNetworking.send(new TalkPayloads.Choose(ConversationScreen.this.entityId, this.option.id()));
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			boolean hot = this.isHoveredOrFocused();
			if (hot) {
				g.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), PAPER_DARK);
			}
			g.text(ConversationScreen.this.font, Component.literal(hot ? "▶ " : "• "), this.getX() + 4, this.getY() + 4, hot ? EDGE : INK_SOFT, false);
			g.text(ConversationScreen.this.font, this.getMessage(), this.getX() + 16, this.getY() + 4, INK, false);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}

	/** Lines of the current speech, for tests. */
	List<String> speechLines() {
		List<String> out = new ArrayList<>();
		for (FormattedCharSequence l : this.lines) {
			StringBuilder sb = new StringBuilder();
			l.accept((i, s, cp) -> {
				sb.appendCodePoint(cp);
				return true;
			});
			out.add(sb.toString());
		}
		return out;
	}
}
