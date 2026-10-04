package dev.voidmc.hearth.client.law;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmc.hearth.law.item.LawItems;
import dev.voidmc.hearth.law.lock.LockpickModel;
import dev.voidmc.hearth.law.net.LawPayloads;
import java.util.Random;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * The lockpicking minigame (docs/LIVING_WORLD.md §4), KCD-style: an iron lock face with a brass cylinder; move the mouse
 * to work the pick round the top of the keyhole, hold the turn (left mouse, D or space) to put tension on the cylinder.
 * In the sweet spot it turns all the way and the pin sets with a click; off it, it turns part way and binds, the pick
 * shakes and reddens, and if you keep forcing it, it snaps. Harder locks have more pins, narrower sweet spots and
 * weaker picks. Played here (it has to be immediate); the server takes broken picks, checks the time a success took,
 * and opens the lock.
 */
public class LockpickScreen extends Screen {
	/** GUI pixels per art pixel. */
	private static final int PX = 2;
	private static final int FACE = 27;
	private static final int CYLINDER = 13;
	private static final int PICK_OUT = 38;
	private static final int PICK_IN = 3;
	private static final int WIDTH = 236;
	private static final int HEIGHT = 214;

	private final int session;
	private final int tier;
	private final Component what;
	private final LockpickModel model;
	private final Random shake = new Random();
	private int picks;
	private int left;
	private int top;
	private boolean mouseTurn;
	private boolean keyTurn;
	private float target = LockpickModel.ARC / 2;
	private long lastNanos;
	private int ticks;
	private float bindSound;
	private float flash;
	private int flashColor;
	private Component message = Component.empty();
	private int closeIn = -1;
	private boolean finished;
	/** Dev tour: hold the pose set by {@link #devPose} for a screenshot. */
	private boolean frozen;

	public LockpickScreen(LawPayloads.LockpickStart start) {
		super(Component.translatable("hearth.law.lockpick.title"));
		this.session = start.session();
		this.tier = start.tier();
		this.what = start.what();
		this.picks = start.picks();
		this.model = new LockpickModel(LockpickModel.spec(start.tier(), start.seed(), start.skill()));
	}

	public int session() {
		return this.session;
	}

	/** The server ended the attempt, or reported the picks left. */
	public void end(LawPayloads.LockpickEnd end) {
		this.picks = end.picks();
		switch (end.reason()) {
			case LawPayloads.LockpickEnd.PICKS -> {
			}
			case LawPayloads.LockpickEnd.SPOTTED -> finish(Component.translatable("hearth.law.lockpick.spotted").withStyle(ChatFormatting.RED), 0xFFD8402F, 24);
			case LawPayloads.LockpickEnd.NO_PICKS -> finish(Component.translatable("hearth.law.lockpick.no_picks").withStyle(ChatFormatting.GOLD), 0xFFE9B949, 24);
			default -> finish(Component.empty(), 0, 1);
		}
	}

	private void finish(Component message, int color, int ticks) {
		this.finished = true;
		this.message = message;
		this.flash = 1.0F;
		this.flashColor = color;
		this.closeIn = ticks;
	}

	@Override
	protected void init() {
		this.left = (this.width - WIDTH) / 2;
		this.top = (this.height - HEIGHT) / 2;
		this.lastNanos = System.nanoTime();
	}

	@Override
	public void tick() {
		super.tick();
		this.ticks++;
		if (this.closeIn > 0 && --this.closeIn == 0) {
			this.onClose();
		}
	}

	private int cx() {
		return this.left + WIDTH / 2;
	}

	private int cy() {
		return this.top + 102;
	}

	/** Steps the game by real time, every frame, so the pick moves as smoothly as the mouse. */
	private void step(int mouseX, int mouseY) {
		long now = System.nanoTime();
		float dt = Math.min(0.05F, (now - this.lastNanos) / 1.0e9F);
		this.lastNanos = now;
		this.flash = Math.max(0, this.flash - dt * 1.5F);
		if (this.finished || this.frozen) {
			return;
		}
		double dx = mouseX - cx();
		double dy = cy() - mouseY;
		if (dy >= 0 || Math.abs(dx) > 4) {
			double phi = Math.toDegrees(Math.atan2(Math.max(0, dy), dx));
			this.target = (float) (180.0 - phi);
		}
		LockpickModel.Event event = this.model.tick(this.target, this.mouseTurn || this.keyTurn, dt);
		switch (event) {
			case PIN_SET -> LawGui.sound(SoundEvents.TRIPWIRE_CLICK_ON, 1.7F, 0.7F);
			case PIN_DROPPED -> LawGui.sound(SoundEvents.TRIPWIRE_CLICK_OFF, 0.8F, 0.5F);
			case BROKE -> {
				LawGui.sound(SoundEvents.ITEM_BREAK, 1.5F, 0.8F);
				this.flash = 1.0F;
				this.flashColor = 0xFFD8402F;
				this.picks = Math.max(0, this.picks - 1);
				ClientPlayNetworking.send(new LawPayloads.LockpickEvent(this.session, LawPayloads.LockpickEvent.BROKE, this.ticks));
			}
			case OPENED -> {
				LawGui.sound(SoundEvents.IRON_TRAPDOOR_OPEN, 1.4F, 0.8F);
				ClientPlayNetworking.send(new LawPayloads.LockpickEvent(this.session, LawPayloads.LockpickEvent.OPENED, this.ticks));
				finish(Component.translatable("hearth.law.lockpick.opened").withStyle(ChatFormatting.GREEN), 0xFF7FCF6A, 14);
			}
			default -> {
			}
		}
		if (this.model.bound()) {
			this.bindSound -= dt;
			if (this.bindSound <= 0) {
				LawGui.sound(SoundEvents.CHEST_LOCKED, 1.6F + this.model.strain() * 0.4F, 0.12F + this.model.strain() * 0.15F);
				this.bindSound = 0.28F;
			}
		} else {
			this.bindSound = 0;
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		step(mouseX, mouseY);
		LawGui.woodPanel(g, this.left, this.top, this.left + WIDTH, this.top + HEIGHT);
		int cx = cx();
		int cy = cy();

		// the lock face: dark rim, iron face lit from the top left, four rivets
		LawGui.disc(g, cx, cy, FACE + 3, PX, 0xFF141518);
		LawGui.disc(g, cx, cy, FACE + 2, PX, 0xFF34373D);
		LawGui.disc(g, cx - PX, cy - PX, FACE, PX, 0xFF9AA0A8);
		LawGui.disc(g, cx, cy, FACE, PX, 0xFF6E737B);
		LawGui.disc(g, cx + PX, cy + PX, FACE - 2, PX, 0xFF61656D);
		LawGui.disc(g, cx, cy, FACE - 3, PX, 0xFF6A6F77);
		int r = (FACE - 5) * PX;
		for (int[] d : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
			int rx = cx + (int) (d[0] * r * 0.72);
			int ry = cy + (int) (d[1] * r * 0.72);
			g.fill(rx - PX, ry - PX, rx + PX, ry + PX, 0xFF3A3D43);
			g.fill(rx - PX, ry - PX, rx, ry, 0xFFC4C9D0);
		}
		// the travel of the pick: a faint arc of scratches over the top half
		for (int deg = 0; deg <= 180; deg += 6) {
			double t = Math.toRadians(deg);
			int sx = cx - (int) Math.round(Math.cos(t) * (FACE - 1) * PX);
			int sy = cy - (int) Math.round(Math.sin(t) * (FACE - 1) * PX);
			g.fill(sx - 1, sy - 1, sx + 1, sy + 1, 0x40000000);
		}

		// the brass cylinder, turning as the pins set
		float turn = this.model.rotation() * 90.0F;
		LawGui.disc(g, cx, cy, CYLINDER + 1, PX, 0xFF4A3818);
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().rotate((float) Math.toRadians(turn));
		LawGui.disc(g, 0, 0, CYLINDER, PX, 0xFFB08A3A);
		LawGui.disc(g, -PX, -PX, CYLINDER - 2, PX, 0xFFD2AE5C);
		LawGui.disc(g, 0, 0, CYLINDER - 2, PX, 0xFFBE9644);
		// keyhole: a round head and a slot
		LawGui.disc(g, 0, -3 * PX, 3, PX, 0xFF120E08);
		g.fill(-PX * 1, -3 * PX, PX * 1, 6 * PX, 0xFF120E08);
		// tension wrench in the bottom of the slot, its arm out to the right
		g.fill(-PX, 4 * PX, PX, 7 * PX, 0xFF2B2D31);
		g.fill(-PX, 6 * PX, 24 * PX, 8 * PX, 0xFF2B2D31);
		g.fill(-PX, 6 * PX, 24 * PX, 7 * PX, 0xFF4A4D53);
		g.pose().popMatrix();

		// the pick: from outside the face to the keyhole, at the hand's angle; shaking and reddening under strain
		double t = Math.toRadians(this.model.pick());
		double jx = 0;
		double jy = 0;
		if (this.model.bound()) {
			float amp = 0.6F + this.model.strain() * 2.2F;
			jx = (this.shake.nextFloat() - 0.5F) * amp * PX;
			jy = (this.shake.nextFloat() - 0.5F) * amp * PX;
		}
		double ox = cx - Math.cos(t) * PICK_OUT * PX + jx;
		double oy = cy - Math.sin(t) * PICK_OUT * PX + jy;
		double mx = cx - Math.cos(t) * (FACE + 6) * PX + jx;
		double my = cy - Math.sin(t) * (FACE + 6) * PX + jy;
		double ix = cx - Math.cos(t) * PICK_IN * PX + jx;
		double iy = cy - Math.sin(t) * PICK_IN * PX + jy;
		int steel = LawGui.mix(0xFFD6DAE0, 0xFFE0402C, this.model.strain());
		LawGui.line(g, ox + PX, oy + PX, mx + PX, my + PX, PX * 2, 0xFF2A1A0E);
		LawGui.line(g, ox, oy, mx, my, PX * 2, 0xFF8C5E34);
		LawGui.line(g, mx + 1, my + 1, ix + 1, iy + 1, PX, 0xFF1E1F22);
		LawGui.line(g, mx, my, ix, iy, PX, steel);
		// the hook at the tip
		g.fill((int) ix - PX, (int) iy - PX * 2, (int) ix + PX, (int) iy, steel);

		if (this.flash > 0) {
			int alpha = (int) (this.flash * 90) << 24;
			g.fill(this.left, this.top, this.left + WIDTH, this.top + HEIGHT, this.flashColor & 0xFFFFFF | alpha);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		int cx = cx();
		g.centeredText(this.font, this.what, cx, this.top + 6, LawGui.TEXT);
		Component tier = Component.translatable("hearth.law.lock.tier." + this.tier);
		g.centeredText(this.font, Component.translatable("hearth.law.lockpick.difficulty", tier), cx, this.top + 16, tierColor(this.tier));

		// pins: set ones in gold, the one being worked outlined
		int pins = this.model.spec().pins();
		int pw = 10;
		int px0 = cx - (pins * pw + (pins - 1) * 4) / 2;
		for (int i = 0; i < pins; i++) {
			int x = px0 + i * (pw + 4);
			int y = this.top + HEIGHT - 48;
			boolean set = i < this.model.setPins();
			g.fill(x, y, x + pw, y + 14, 0xFF111114);
			g.fill(x + 1, y + 1, x + pw - 1, y + 13, set ? 0xFFE0B84C : 0xFF4A4A52);
			if (set) {
				g.fill(x + 1, y + 1, x + pw - 1, y + 3, 0xFFF6DC8A);
			} else if (i == this.model.setPins() && !this.finished) {
				int pulse = (int) (Math.sin(this.ticks * 0.3) * 40 + 120);
				g.outline(x - 1, y - 1, pw + 2, 16, pulse << 24 | 0xE9B949);
			}
		}

		// strain on the pick
		int sx = this.left + WIDTH - 66;
		int sy = this.top + HEIGHT - 44;
		g.text(this.font, Component.translatable("hearth.law.lockpick.strain"), sx, sy - 10, LawGui.TEXT_DIM, false);
		g.fill(sx, sy, sx + 52, sy + 5, 0xFF111114);
		g.fill(sx + 1, sy + 1, sx + 1 + Math.round(50 * this.model.strain()), sy + 4, LawGui.mix(0xFFB0B4BA, 0xFFE0402C, this.model.strain()));

		// picks left
		int ix = this.left + 12;
		int iy = this.top + HEIGHT - 48;
		g.item(new ItemStack(LawItems.LOCKPICK), ix, iy);
		g.text(this.font, Component.literal("× " + this.picks), ix + 18, iy + 5, this.picks <= 1 ? LawGui.RED : LawGui.TEXT, true);

		if (!this.message.getString().isEmpty()) {
			g.centeredText(this.font, this.message, cx, this.top + 30, LawGui.TEXT);
		}
		g.centeredText(this.font, Component.translatable("hearth.law.lockpick.help"), cx, this.top + HEIGHT - 20, LawGui.TEXT_DIM);
		g.centeredText(this.font, Component.translatable("hearth.law.lockpick.help2"), cx, this.top + HEIGHT - 10, LawGui.TEXT_DIM);
	}

	/** Dev tour: forces the pick off the sweet spot under tension until it strains this much, then holds still. */
	void devPose(float give, float strain) {
		LockpickModel.Spec spec = this.model.spec();
		float sweet = spec.sweet()[Math.min(this.model.setPins(), spec.pins() - 1)];
		float off = spec.tolerance() + spec.falloff() * (1 - give);
		float target = sweet + off <= LockpickModel.ARC ? sweet + off : sweet - off;
		for (int i = 0; i < 400 && (Math.abs(this.model.pick() - target) > 0.5F || this.model.strain() < strain); i++) {
			this.model.tick(target, Math.abs(this.model.pick() - target) <= 0.5F, 0.02F);
		}
		this.frozen = true;
	}

	static int tierColor(int tier) {
		return switch (tier) {
			case 1 -> 0xFF8FD47A;
			case 2 -> 0xFFC8D86A;
			case 3 -> 0xFFE9B949;
			case 4 -> 0xFFE58A3A;
			default -> 0xFFE0402C;
		};
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			this.mouseTurn = true;
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == 0) {
			this.mouseTurn = false;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (isTurnKey(event.key())) {
			this.keyTurn = true;
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (isTurnKey(event.key())) {
			this.keyTurn = false;
			return true;
		}
		return super.keyReleased(event);
	}

	private static boolean isTurnKey(int key) {
		return key == InputConstants.KEY_D || key == InputConstants.KEY_SPACE || key == InputConstants.KEY_RIGHT;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void removed() {
		super.removed();
		if (!this.finished && Minecraft.getInstance().getConnection() != null) {
			ClientPlayNetworking.send(new LawPayloads.LockpickEvent(this.session, LawPayloads.LockpickEvent.CANCEL, this.ticks));
		}
	}
}
