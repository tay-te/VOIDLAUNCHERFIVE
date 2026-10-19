package dev.voidmc.hearth.client.social;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmc.hearth.social.dice.DiceGame;
import dev.voidmc.hearth.social.dice.DiceMenu;
import dev.voidmc.hearth.social.dice.DicePayloads;
import dev.voidmc.hearth.social.dice.DiceView;
import dev.voidmc.hearth.social.dice.Farkle;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

/**
 * The dice table: a tavern table of green baize in a dark oak frame. Your six bone dice tumble across it when you
 * throw; click the scoring ones to pick them (they lift, edged in gold), then roll the rest or bank. Dice set aside this
 * turn wait in the tray at the top. Scores and the race to the target sit on brass plaques either side; the folk's words
 * and what just happened are written on the parchment strip below. Everything shown comes from the server.
 */
public class DiceScreen extends AbstractContainerScreen<DiceMenu> {
	static final int W = 320;
	static final int H = 214;
	static final int FELT_X0 = 10;
	static final int FELT_Y0 = 22;
	static final int FELT_X1 = W - 10;
	static final int FELT_Y1 = 150;
	static final int DIE = 22;
	static final int SMALL = 14;
	static final int TUMBLE_TICKS = 14;

	// colours
	static final int WOOD_DARK = 0xFF2E1C0E;
	static final int WOOD = 0xFF5C3A1E;
	static final int WOOD_LIGHT = 0xFF77502B;
	static final int FELT = 0xFF2F5D34;
	static final int FELT_DARK = 0xFF234628;
	static final int FELT_LIGHT = 0xFF3A6E40;
	static final int BONE = 0xFFF1EAD6;
	static final int BONE_SHADE = 0xFFD5CBB0;
	static final int PIP = 0xFF1E1813;
	static final int PIP_RED = 0xFFA3241B;
	static final int GOLD = 0xFFE8B83A;
	static final int PARCHMENT = 0xFFE9DAB6;
	static final int INK = 0xFF3A2A1A;
	static final int INK_SOFT = 0xFF7A6040;
	static final int ON_WOOD = 0xFFF4E6C4;

	private final List<Button> stakeButtons = new ArrayList<>();
	private Button roll;
	private Button bank;
	private Button pass;
	private Button loaded;
	private Button again;
	private Button rules;
	private boolean showRules;
	private int lastSerial = -1;
	private int tumble;
	private int ticks;
	private final Random wobble = new Random();

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

	public DiceScreen(DiceMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, W, H);
	}

	@Override
	protected void init() {
		super.init();
		if (handedToHost(dev.voidmc.hearth.client.overlay.HearthScreens.DICE)) {
			return;
		}
		this.stakeButtons.clear();
		int y = this.topPos + 188;
		int x = this.leftPos + 12;
		this.roll = addRenderableWidget(Button.builder(Component.translatable("hearth.social.dice.roll"), b -> send(DicePayloads.Action.ROLL, 0))
			.bounds(x, y, 70, 20).build());
		this.bank = addRenderableWidget(Button.builder(Component.translatable("hearth.social.dice.bank"), b -> send(DicePayloads.Action.BANK, 0))
			.bounds(x + 74, y, 70, 20).build());
		this.pass = addRenderableWidget(Button.builder(Component.translatable("hearth.social.dice.pass"), b -> send(DicePayloads.Action.PASS, 0))
			.bounds(x, y, 144, 20).build());
		this.again = addRenderableWidget(Button.builder(Component.translatable("hearth.social.dice.again"), b -> send(DicePayloads.Action.AGAIN, 0))
			.bounds(x, y, 90, 20).build());
		this.loaded = addRenderableWidget(Button.builder(Component.translatable("hearth.social.dice.loaded_off"),
			b -> send(DicePayloads.Action.LOADED, this.menu.view().loaded() == 2 ? 0 : 1)).bounds(this.leftPos + W - 12 - 24 - 4 - 96, y, 96, 20).build());
		this.rules = addRenderableWidget(Button.builder(Component.literal("?"), b -> this.showRules = !this.showRules)
			.bounds(this.leftPos + W - 12 - 24, y, 24, 20).build());
		List<Integer> stakes = this.menu.view().stakes();
		int n = stakes.size();
		int bw = Math.min(56, (W - 24 - 30 - (n - 1) * 4) / Math.max(1, n));
		for (int i = 0; i < n; i++) {
			final int index = i;
			int bits = stakes.get(i);
			Component label = bits == 0 ? Component.translatable("hearth.social.dice.for_honour") : Component.translatable("hearth.social.dice.stake_bits", bits);
			this.stakeButtons.add(addRenderableWidget(Button.builder(label, b -> send(DicePayloads.Action.STAKE, index))
				.bounds(x + i * (bw + 4), y, bits == 0 ? Math.max(bw, 90) : bw, 20).build()));
		}
		refreshButtons();
	}

	private void send(int action, int arg) {
		ClientPlayNetworking.send(new DicePayloads.Action(this.menu.containerId, action, arg));
	}

	/** A new view arrived from the server. */
	public void onView(DiceView v) {
		if (v.phaseEnum() == DiceGame.Phase.STAKE && v.stakes().size() != this.stakeButtons.size()) {
			rebuildWidgets();
		}
		refreshButtons();
	}

	private void refreshButtons() {
		DiceView v = this.menu.view();
		DiceGame.Phase p = v.phaseEnum();
		for (Button b : this.stakeButtons) {
			b.visible = p == DiceGame.Phase.STAKE;
		}
		boolean playing = p == DiceGame.Phase.PLAYER_ROLLED;
		this.roll.visible = playing;
		this.bank.visible = playing;
		this.roll.active = v.canRoll() && this.tumble <= 0;
		this.bank.active = v.canBank() && this.tumble <= 0;
		this.pass.visible = p == DiceGame.Phase.PLAYER_BUST;
		this.again.visible = p == DiceGame.Phase.OVER && !v.cheatCaught();
		this.loaded.visible = v.loaded() > 0 && p != DiceGame.Phase.OVER;
		this.loaded.setMessage(Component.translatable(v.loaded() == 2 ? "hearth.social.dice.loaded_on" : "hearth.social.dice.loaded_off"));
		this.rules.visible = true;
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		this.ticks++;
		DiceView v = this.menu.view();
		if (v.rollSerial() != this.lastSerial) {
			if (this.lastSerial >= 0 && v.rolledMask() != 0) {
				this.tumble = TUMBLE_TICKS;
			}
			this.lastSerial = v.rollSerial();
		}
		if (this.tumble > 0) {
			this.tumble--;
			if (this.tumble % 3 == 0) {
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BONE_BLOCK_HIT, 1.2F + this.wobble.nextFloat() * 0.6F, 0.4F));
			}
		}
		refreshButtons();
	}

	// ---- drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		int x0 = this.leftPos;
		int y0 = this.topPos;
		// the frame: dark edge, planks with grain
		g.fill(x0 - 2, y0 - 2, x0 + W + 2, y0 + H + 2, WOOD_DARK);
		g.fill(x0, y0, x0 + W, y0 + H, WOOD);
		for (int y = y0 + 6; y < y0 + H; y += 13) {
			g.fill(x0 + 2, y, x0 + W - 2, y + 1, WOOD_DARK & 0x60FFFFFF);
		}
		for (int i = 0; i < 40; i++) {
			int gx = x0 + 4 + (i * 73) % (W - 8);
			int gy = y0 + 3 + (i * 37) % (H - 6);
			g.fill(gx, gy, gx + 6 + i % 5, gy + 1, WOOD_LIGHT);
		}
		// the baize, with a darker rim and a little nap
		g.fill(x0 + FELT_X0 - 2, y0 + FELT_Y0 - 2, x0 + FELT_X1 + 2, y0 + FELT_Y1 + 2, WOOD_DARK);
		g.fill(x0 + FELT_X0, y0 + FELT_Y0, x0 + FELT_X1, y0 + FELT_Y1, FELT_DARK);
		g.fill(x0 + FELT_X0 + 2, y0 + FELT_Y0 + 2, x0 + FELT_X1 - 2, y0 + FELT_Y1 - 2, FELT);
		for (int i = 0; i < 160; i++) {
			int fx = x0 + FELT_X0 + 3 + (i * 97) % (FELT_X1 - FELT_X0 - 6);
			int fy = y0 + FELT_Y0 + 3 + (i * 53) % (FELT_Y1 - FELT_Y0 - 6);
			g.fill(fx, fy, fx + 1, fy + 1, (i & 1) == 0 ? FELT_LIGHT : FELT_DARK);
		}
		// the tray for dice set aside
		g.fill(x0 + 96, y0 + 50, x0 + W - 96, y0 + 70, FELT_DARK);
		// the parchment strip
		g.fill(x0 + 10, y0 + 154, x0 + W - 10, y0 + 183, 0xFF5A4026);
		g.fill(x0 + 11, y0 + 155, x0 + W - 11, y0 + 182, PARCHMENT);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		DiceView v = this.menu.view();
		g.text(this.font, this.title, 10, 7, ON_WOOD, true);
		Component stake = v.phaseEnum() == DiceGame.Phase.STAKE ? Component.translatable("hearth.social.dice.purse", v.balance())
			: v.stake() > 0 ? Component.translatable("hearth.social.dice.stake_shown", v.stake()) : Component.translatable("hearth.social.dice.no_stake");
		g.text(this.font, stake, W - 10 - this.font.width(stake), 7, ON_WOOD, true);

		plaque(g, FELT_X0 + 6, FELT_Y0 + 6, Component.translatable("hearth.social.dice.you"), v.playerScore(), v.target(),
			v.turn() == DiceGame.PLAYER && v.phaseEnum() != DiceGame.Phase.OVER);
		plaque(g, FELT_X1 - 6 - 96, FELT_Y0 + 6, Component.literal(v.folkName()), v.folkScore(), v.target(),
			v.turn() == DiceGame.FOLK && v.phaseEnum() != DiceGame.Phase.OVER);
		// the folk's temper, under their plaque
		Component temper = Component.translatable("hearth.social.dice.temper",
			Component.translatable("hearth.social.dice.personality." + v.personality())).withStyle(ChatFormatting.ITALIC);
		g.text(this.font, temper, FELT_X1 - 6 - this.font.width(temper), FELT_Y0 + 34, 0xFFBFD8BF, false);

		DiceGame.Phase p = v.phaseEnum();
		int mx = mouseX - this.leftPos;
		int my = mouseY - this.topPos;
		if (p == DiceGame.Phase.STAKE) {
			g.centeredText(this.font, Component.translatable("hearth.social.dice.choose_stake"), W / 2, 92, ON_WOOD);
			g.centeredText(this.font, Component.translatable("hearth.social.dice.first_to", v.target()), W / 2, 106, 0xFFBFD8BF);
		} else if (p != DiceGame.Phase.OVER) {
			drawTable(g, v, mx, my);
		} else {
			drawTray(g, v);
		}
		if (p == DiceGame.Phase.OVER) {
			boolean won = v.winner() == DiceGame.PLAYER;
			Component big = Component.translatable(v.cheatCaught() ? "hearth.social.dice.caught" : won ? "hearth.social.dice.you_win" : "hearth.social.dice.you_lose");
			g.fill(W / 2 - 80, 84, W / 2 + 80, 110, 0xC0201008);
			g.centeredText(this.font, big.copy().withStyle(ChatFormatting.BOLD), W / 2, 92, won ? GOLD : 0xFFE06050);
		}

		// what was said, and what happened
		List<FormattedCharSequence> said = this.font.split(v.line().copy().withStyle(ChatFormatting.ITALIC), W - 30);
		int ly = 158;
		if (!said.isEmpty()) {
			g.text(this.font, Component.literal("“"), 14, ly, INK_SOFT, false);
			g.text(this.font, said.get(0), 20, ly, INK, false);
			ly += 10;
		}
		if (!v.status().getString().isEmpty()) {
			List<FormattedCharSequence> st = this.font.split(v.status(), W - 30);
			g.text(this.font, st.get(0), 20, ly, INK_SOFT, false);
		}
		if (this.showRules) {
			drawRules(g);
		}
	}

	/** A brass plaque: name, score out of target, and a bar for the race. */
	private void plaque(GuiGraphicsExtractor g, int x, int y, Component name, int score, int target, boolean toMove) {
		int w = 96;
		g.fill(x - 1, y - 1, x + w + 1, y + 25, 0xFF6E5420);
		g.fill(x, y, x + w, y + 24, toMove ? 0xFFC9A34A : 0xFFAD8B3E);
		g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFFE6CB7A);
		String n = this.font.plainSubstrByWidth(name.getString(), w - 6);
		g.text(this.font, Component.literal(n).withStyle(name.getStyle()), x + 3, y + 3, 0xFF2A1C08, false);
		String sc = score + " / " + target;
		g.text(this.font, sc, x + 3, y + 13, 0xFF2A1C08, false);
		int bar = (int) Math.min(w - 6, (long) (w - 6) * score / Math.max(1, target));
		g.fill(x + 3, y + 21, x + w - 3, y + 23, 0xFF5A4318);
		g.fill(x + 3, y + 21, x + 3 + bar, y + 23, 0xFF3F8A3A);
		if (toMove) {
			g.fill(x - 4, y + 9, x - 2, y + 15, GOLD);
		}
	}

	private void drawTray(GuiGraphicsExtractor g, DiceView v) {
		int k = 0;
		for (int i = 0; i < Farkle.DICE; i++) {
			if ((v.keptMask() >> i & 1) != 0) {
				die(g, 100 + k * (SMALL + 3), 53, SMALL, v.dice()[i], false, false);
				k++;
			}
		}
	}

	private void drawTable(GuiGraphicsExtractor g, DiceView v, int mx, int my) {
		boolean playerPicks = v.phaseEnum() == DiceGame.Phase.PLAYER_ROLLED && this.tumble <= 0;
		// set aside this turn, in the tray
		int k = 0;
		for (int i = 0; i < Farkle.DICE; i++) {
			if ((v.keptMask() >> i & 1) != 0) {
				die(g, 100 + k * (SMALL + 3), 53, SMALL, v.dice()[i], false, false);
				k++;
			}
		}
		if (k == 0) {
			g.centeredText(this.font, Component.translatable("hearth.social.dice.tray"), W / 2, 56, 0x80BFD8BF);
		}
		// the dice on the baize
		for (int i = 0; i < Farkle.DICE; i++) {
			if ((v.keptMask() >> i & 1) != 0) {
				continue;
			}
			int[] at = diePos(v, i);
			boolean tumbling = this.tumble > 0 && (v.rolledMask() >> i & 1) != 0;
			int face = v.dice()[i];
			int x = at[0];
			int y = at[1];
			if (tumbling) {
				face = 1 + this.wobble.nextInt(6);
				x += this.wobble.nextInt(5) - 2;
				y += this.wobble.nextInt(5) - 2 - this.tumble / 3;
			}
			boolean picked = (v.selectedMask() >> i & 1) != 0;
			boolean scoring = playerPicks && (v.scoringMask() >> i & 1) != 0;
			boolean hover = playerPicks && mx >= x && mx < x + DIE && my >= y && my < y + DIE;
			if (picked) {
				y -= 4;
			}
			if (face > 0) {
				// a soft shadow on the baize
				g.fill(x + 2, y + DIE - 1 + (picked ? 4 : 0), x + DIE + 1, y + DIE + 2 + (picked ? 4 : 0), 0x50000000);
				die(g, x, y, DIE, face, picked, scoring || hover);
			}
		}
		int ty = 134;
		Component turn;
		if (v.phaseEnum() == DiceGame.Phase.FOLK_TURN) {
			turn = Component.translatable("hearth.social.dice.folk_turn", v.folkName(), v.turnPoints());
		} else if (v.selectionScore() > 0) {
			turn = Component.translatable("hearth.social.dice.turn_picked", v.turnPoints(), v.selectionScore());
		} else if (v.selectedMask() != 0) {
			turn = Component.translatable("hearth.social.dice.turn_invalid", v.turnPoints());
		} else {
			turn = Component.translatable("hearth.social.dice.turn", v.turnPoints());
		}
		g.centeredText(this.font, turn, W / 2, ty, ON_WOOD);
	}

	/** Where die i lies on the baize: a loose row, a little different after every throw. */
	private static int[] diePos(DiceView v, int i) {
		long h = (v.rollSerial() * 0x9E3779B97F4A7C15L) ^ (i * 0xC2B2AE3D27D4EB4FL);
		h ^= h >>> 29;
		int jx = (int) Math.floorMod(h, 9L) - 4;
		int jy = (int) Math.floorMod(h >> 8, 15L) - 7;
		int x = 44 + i * 40 + jx;
		int y = 92 + jy;
		return new int[] {x, y};
	}

	/** A bone die: rounded edge, a shaded side, square pips (the one is red). */
	private static void die(GuiGraphicsExtractor g, int x, int y, int size, int face, boolean picked, boolean glow) {
		if (picked) {
			g.fill(x - 2, y - 2, x + size + 2, y + size + 2, GOLD);
		} else if (glow) {
			g.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xA0FFF2B0);
		}
		g.fill(x + 1, y, x + size - 1, y + size, PIP);
		g.fill(x, y + 1, x + size, y + size - 1, PIP);
		g.fill(x + 1, y + 1, x + size - 1, y + size - 1, BONE_SHADE);
		g.fill(x + 1, y + 1, x + size - 2, y + size - 2, BONE);
		int p = Math.max(2, size / 6);
		int l = x + size / 4 - p / 2;
		int c = x + size / 2 - p / 2;
		int r = x + size * 3 / 4 - p / 2;
		int t = y + size / 4 - p / 2;
		int m = y + size / 2 - p / 2;
		int b = y + size * 3 / 4 - p / 2;
		switch (face) {
			case 1 -> pip(g, c, m, p + 1, PIP_RED);
			case 2 -> {
				pip(g, l, t, p, PIP);
				pip(g, r, b, p, PIP);
			}
			case 3 -> {
				pip(g, l, t, p, PIP);
				pip(g, c, m, p, PIP);
				pip(g, r, b, p, PIP);
			}
			case 4 -> {
				pip(g, l, t, p, PIP);
				pip(g, r, t, p, PIP);
				pip(g, l, b, p, PIP);
				pip(g, r, b, p, PIP);
			}
			case 5 -> {
				pip(g, l, t, p, PIP);
				pip(g, r, t, p, PIP);
				pip(g, c, m, p, PIP);
				pip(g, l, b, p, PIP);
				pip(g, r, b, p, PIP);
			}
			case 6 -> {
				pip(g, l, t, p, PIP);
				pip(g, r, t, p, PIP);
				pip(g, l, m, p, PIP);
				pip(g, r, m, p, PIP);
				pip(g, l, b, p, PIP);
				pip(g, r, b, p, PIP);
			}
			default -> {
			}
		}
	}

	private static void pip(GuiGraphicsExtractor g, int x, int y, int s, int color) {
		g.fill(x, y, x + s, y + s, color);
	}

	private void drawRules(GuiGraphicsExtractor g) {
		int x0 = 40;
		int y0 = 26;
		int x1 = W - 40;
		int y1 = 146;
		g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFF5A4026);
		g.fill(x0, y0, x1, y1, PARCHMENT);
		g.centeredText(this.font, Component.translatable("hearth.social.dice.rules.title").withStyle(ChatFormatting.BOLD), W / 2, y0 + 5, INK);
		String[] keys = {"single", "triple", "more", "run5", "run6", "flow"};
		int y = y0 + 20;
		for (String key : keys) {
			for (FormattedCharSequence line : this.font.split(Component.translatable("hearth.social.dice.rules." + key), x1 - x0 - 12)) {
				g.text(this.font, line, x0 + 6, y, INK, false);
				y += 10;
			}
			y += 2;
		}
	}

	// ---- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		DiceView v = this.menu.view();
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && v.phaseEnum() == DiceGame.Phase.PLAYER_ROLLED && this.tumble <= 0 && !this.showRules) {
			int mx = (int) event.x() - this.leftPos;
			int my = (int) event.y() - this.topPos;
			for (int i = 0; i < Farkle.DICE; i++) {
				if ((v.keptMask() >> i & 1) != 0) {
					continue;
				}
				int[] at = diePos(v, i);
				int y = at[1] - ((v.selectedMask() >> i & 1) != 0 ? 4 : 0);
				if (mx >= at[0] && mx < at[0] + DIE && my >= y && my < y + DIE) {
					Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BONE_BLOCK_HIT, 1.6F, 0.5F));
					send(DicePayloads.Action.TOGGLE, i);
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
}
