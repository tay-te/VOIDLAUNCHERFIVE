package dev.voidmc.hub.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import dev.voidmc.hub.VoidHub;
import dev.voidmc.hub.hearth.HearthFx;
import dev.voidmc.hub.hearth.HearthLink;
import dev.voidmc.hub.hearth.HearthSync;
import dev.voidmc.hub.layout.HubLayout;
import dev.voidmc.hub.model.Bridge;
import dev.voidmc.hub.model.Json;
import dev.voidmc.hub.ui.HubStats;
import dev.voidmc.hub.ui.HubView;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * The lighter hub screen for events (docs/HUB_UI.md): a VOID Hearth conversation, a guard stopping the player, a market,
 * bank or dice table, lockpicking and pickpocketing. No menu slots and no pages: the VOID mark and the close island,
 * and one Hearth page in the hub's window (the page draws it from {@code HearthSync}'s state).
 *
 * <p>Modal rules: the page reports popovers and text focus ({@code hearthUi}); while one is up Esc goes to the page. The
 * screen name and the page's keyboard claims are sent again on every open (a new screen starts from none), the page
 * pauses its timers while no screen shows it, and native layers or keyboard claims the page queued for a closed screen
 * are dropped ({@code HubView.screenOnly}). The minigames' moving parts are drawn here natively ({@link HearthFx}) and
 * their input is read in this screen's own handlers.
 */
public class HubEventScreen extends Screen {

	private final String name;
	private final HubView hub;
	private HubLayout layout;
	private final HearthFx fx = new HearthFx();
	private List<Bridge.ItemIcon> stacks = List.of();
	private String sentLayout;
	private boolean firstFrame;
	private boolean check = true;
	private boolean pageModal;
	private boolean pageText;
	/** The event's section of the state was seen: when it goes null, the server ended it and this screen closes. */
	private boolean seen;
	private int ticks;
	private double lastCssX = Double.NaN;
	private double lastCssY = Double.NaN;

	/** {@code name}: a Hearth screen ("talk", "confront", "market", "bank", "dice", "lockpick", "pickpocket"). */
	public HubEventScreen(String name) {
		super(Component.literal("VOID Hearth"));
		this.name = name;
		this.hub = HubView.get();
	}

	/** The Hearth screen this shows. */
	public String name() {
		return this.name;
	}

	public HubLayout layout() {
		return this.layout;
	}

	/** Tests: the native layers and stacks the page reported. */
	public HearthFx fx() {
		return this.fx;
	}

	public List<Bridge.ItemIcon> stacks() {
		return this.stacks;
	}

	public boolean pageModal() {
		return this.pageModal;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		HubStats.INSTANCE.lastScreenInitNs = System.nanoTime();
		Window w = this.minecraft.getWindow();
		this.layout = HubLayout.hearth(w.getWidth(), w.getHeight(), w.getGuiScale());
		this.hub.setListener(this::onBridge);
		this.hub.setVisible(true);
		this.hub.ensureSize(w.getWidth(), w.getHeight(), this.layout.u);
		this.sentLayout = null;
		this.firstFrame = true;
		this.pageModal = false;
		this.pageText = false;
		Screen ns = HearthLink.nativeScreen();
		if (ns != null && ns != this) {
			ns.init(this.width, this.height);  // the lockpick's clock and centre; the pockets' panel
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// No vanilla dim or blur: the page draws the shell's flat dim itself.
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Window window = this.minecraft.getWindow();
		int gs = window.getGuiScale();
		this.hub.beginFrame();
		String json = this.layout.toJson();
		if (json != this.sentLayout) {
			this.hub.sendLatest("layout", json);
			this.sentLayout = json;
			// Every open (and every new size) tells the page which screen it is; the page answers with its native rects
			// and keyboard claims for this screen.
			this.hub.sendLatest("hearthScreen", Json.object().put("type", "hearthScreen").put("screen", this.name).end());
		}
		HearthSync.frame(this.hub, this.check);
		this.check = false;
		if (this.firstFrame) {
			this.hub.awaitPainted(200_000_000L);
		}
		graphics.pose().pushMatrix();
		graphics.pose().scale(1.0F / gs, 1.0F / gs);
		graphics.blit(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA, HubView.TEXTURE_ID, 0, 0, 0.0F, 0.0F,
			this.hub.width(), this.hub.height(), this.hub.texture().width(), this.hub.texture().height(),
			this.hub.texture().width(), this.hub.texture().height());
		graphics.pose().popMatrix();
		this.hub.frame();

		double gx = this.minecraft.mouseHandler.getScaledXPos(window);
		double gy = this.minecraft.mouseHandler.getScaledYPos(window);
		double cssX = this.layout.physToCss(gx * gs);
		double cssY = this.layout.physToCss(gy * gs);
		this.fx.pointer(cssX, cssY);
		if ("lockpick".equals(this.name)) {
			this.stepLock(gx, gy, gs);
		}
		this.extractStacks(graphics, gs);
		if (this.fx.active() && this.hub.texture() != null) {
			this.fx.extract(graphics, this.layout.u, gs, this.hub.texture().width(), this.hub.texture().height());
		}
		if (this.firstFrame) {
			this.firstFrame = false;
			HubStats.INSTANCE.lastScreenFirstFrameNs = System.nanoTime();
		}
	}

	/**
	 * Hearth's lockpick screen plays the lock (its model, sounds, timing and packets); the hub feeds it the pointer, mapped
	 * from the hub's lock centre onto the native screen's own centre, every frame, as its own render would.
	 */
	private void stepLock(double gx, double gy, int gs) {
		Screen ns = HearthLink.nativeScreen();
		HearthLink.LockParts parts = HearthLink.lockParts(ns);
		double[] c = this.fx.lockCentre();
		if (ns == null || parts == null || c == null) {
			return;
		}
		double myCx = c[0] * this.layout.u / gs;
		double myCy = c[1] * this.layout.u / gs;
		int nCx = (ns.width - 236) / 2 + 118;
		int nCy = (ns.height - 214) / 2 + 102;
		try {
			nCx = (int) HearthLink.field(ns.getClass(), "left").getInt(ns) + 118;
			nCy = (int) HearthLink.field(ns.getClass(), "top").getInt(ns) + 102;
		} catch (ReflectiveOperationException | RuntimeException e) {
			// the native screen's own layout constants
		}
		// Only the pick's angle round the centre matters to the native game.
		int nx = (int) Math.round(nCx + (gx - myCx));
		int ny = (int) Math.round(nCy + (gy - myCy));
		try {
			parts.step().invoke(ns, nx, ny);
		} catch (ReflectiveOperationException | RuntimeException e) {
			VoidHub.LOGGER.warn("VOID Hub: Hearth's lockpick step failed", e);
		}
	}

	/** Item stacks in the page's wells (market goods, pockets), at the wells' icon size, without counts. */
	private void extractStacks(GuiGraphicsExtractor graphics, int gs) {
		double u = this.layout.u;
		for (Bridge.ItemIcon it : this.stacks) {
			Identifier id = Identifier.tryParse(it.item());
			if (id == null || it.count() <= 0) {
				continue;
			}
			var item = BuiltInRegistries.ITEM.getOptional(id);
			if (item.isEmpty()) {
				continue;
			}
			ItemStack stack = new ItemStack(item.get(), it.count());
			int icon = HubLayout.fitIcon(Math.min(it.w(), it.h()) * u * 0.61, Math.min(it.w(), it.h()) * u);
			int px = (int) Math.round((it.x() + it.w() / 2.0) * u - icon / 2.0);
			int py = (int) Math.round((it.y() + it.h() / 2.0) * u - icon / 2.0);
			float sc = icon / (16.0F * gs);
			graphics.pose().pushMatrix();
			graphics.pose().translate(px / (float) gs, py / (float) gs);
			graphics.pose().scale(sc, sc);
			graphics.item(stack, 0, 0);
			graphics.pose().popMatrix();
		}
	}

	// ---- bridge ----------------------------------------------------------------------------------------------------

	private void onBridge(Bridge.Message m) {
		switch (m) {
			case Bridge.Close c -> {
				if ("confront".equals(this.name)) {
					HearthLink.act("confront", "");  // the close island walks away (Esc does not: he is waiting)
				}
				this.onClose();
			}
			case Bridge.HearthCall call -> {
				HearthLink.act(call.op(), call.arg());
				if ("close".equals(call.op()) || "slipAway".equals(call.op()) || "stopPicking".equals(call.op())) {
					this.onClose();
				}
			}
			case Bridge.HearthUi ui -> {
				this.pageModal = ui.modal();
				this.pageText = ui.text();
			}
			case Bridge.HearthFx f -> {
				this.fx.set(f.items());
				this.stacks = f.stacks();
			}
			default -> {
			}
		}
	}

	// ---- input -----------------------------------------------------------------------------------------------------

	private double css(double gui) {
		return this.layout.physToCss(gui * this.minecraft.getWindow().getGuiScale());
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double x = this.css(event.x());
		double y = this.css(event.y());
		// The page sees no modifiers: Shift (a stack, or all you carry) goes ahead of the click.
		this.hub.send(Json.object().put("type", "hmods").put("shift", event.hasShiftDown()).end());
		if ("dice".equals(this.name) && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			int die = this.fx.dieAt(x, y);
			if (die >= 0) {
				HearthLink.act("diceToggle", String.valueOf(die));
				return true;
			}
		}
		if ("lockpick".equals(this.name) && event.button() == InputConstants.MOUSE_BUTTON_LEFT && this.fx.onLock(x, y)) {
			Screen ns = HearthLink.nativeScreen();
			if (ns != null) {
				// Hold to turn. Hearth's screen (hearth-ui 7b167cc3) tests button 0, GLFW's left, where 26.x sends SDL's 1:
				// it gets both, so the turn works whichever it reads.
				ns.mouseClicked(event, doubleClick);
				ns.mouseClicked(new MouseButtonEvent(event.x(), event.y(), new net.minecraft.client.input.MouseButtonInfo(0, event.modifiers())), doubleClick);
			}
			return true;
		}
		this.hub.mouseButton(true, x, y, event.button());
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if ("lockpick".equals(this.name) && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			Screen ns = HearthLink.nativeScreen();
			if (ns != null) {
				ns.mouseReleased(event);
				ns.mouseReleased(new MouseButtonEvent(event.x(), event.y(), new net.minecraft.client.input.MouseButtonInfo(0, event.modifiers())));
			}
		}
		this.hub.mouseButton(false, this.css(event.x()), this.css(event.y()), event.button());
		return true;
	}

	@Override
	public void mouseMoved(double x, double y) {
		double cx = this.css(x);
		double cy = this.css(y);
		if (cx != this.lastCssX || cy != this.lastCssY) {
			this.lastCssX = cx;
			this.lastCssY = cy;
			this.hub.mouseMove(cx, cy);
		}
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		this.mouseMoved(event.x(), event.y());
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		this.hub.send(Json.object().put("type", "hwheel").put("x", this.css(x)).put("y", this.css(y)).put("dy", scrollY).end());
		return true;
	}

	/** Keys the Hearth pages use, by GLFW key code. */
	static String pageKey(int key) {
		if (key == InputConstants.KEY_ESCAPE) {
			return "Escape";
		}
		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			return "Enter";
		}
		if (key == InputConstants.KEY_SPACE) {
			return "Space";
		}
		if (key == InputConstants.KEY_B) {
			return "b";
		}
		if (key == InputConstants.KEY_UP) {
			return "Up";
		}
		if (key == InputConstants.KEY_DOWN) {
			return "Down";
		}
		if (key == InputConstants.KEY_LEFT) {
			return "Left";
		}
		if (key == InputConstants.KEY_RIGHT) {
			return "Right";
		}
		// SDL scancodes (26.x): 1-9 run on, 0 comes after 9.
		if (key == InputConstants.KEY_0 || key == InputConstants.KEY_NUMPAD0) {
			return "0";
		}
		if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_9) {
			return String.valueOf((char) ('1' + key - InputConstants.KEY_1));
		}
		if (key >= InputConstants.KEY_NUMPAD1 && key <= InputConstants.KEY_NUMPAD9) {
			return String.valueOf((char) ('1' + key - InputConstants.KEY_NUMPAD1));
		}
		return null;
	}

	private void sendKey(String key) {
		this.hub.send(Json.object().put("type", "hkey").put("key", key).end());
	}

	/** Tests: the last key this screen saw, and whether it went to the page. */
	public static volatile String lastKey = "";
	/** A digit keyPressed already passed on (its charTyped twin is then not sent again). */
	private String digitSent;

	@Override
	public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
		String ch = event.codepointAsString();
		if (ch.length() == 1 && Character.isDigit(ch.charAt(0))) {
			if (!ch.equals(this.digitSent)) {
				lastKey = "char " + ch;
				this.sendKey(ch);
			}
			this.digitSent = null;
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		lastKey = "key " + event.key();
		if (VoidHub.openHub != null && VoidHub.openHub.matches(event) && !this.pageText) {
			this.onClose();
			return true;
		}
		if ("lockpick".equals(this.name) && isTurnKey(event.key())) {
			Screen ns = HearthLink.nativeScreen();
			if (ns != null) {
				ns.keyPressed(event);
			}
			return true;
		}
		String key = pageKey(event.key());
		if (event.isEscape()) {
			if (this.pageModal) {
				this.sendKey("Escape");  // the popover closes, not the screen
			} else if (!"confront".equals(this.name)) {
				this.onClose();
			}
			return true;  // a guard waits for an answer: Esc does nothing
		}
		if (key != null) {
			this.sendKey(key);
			this.digitSent = Character.isDigit(key.charAt(0)) ? key : null;
		}
		return true;  // no hotbar keys, drops or inventory under an event screen
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if ("lockpick".equals(this.name) && isTurnKey(event.key())) {
			Screen ns = HearthLink.nativeScreen();
			if (ns != null) {
				ns.keyReleased(event);
			}
			return true;
		}
		return super.keyReleased(event);
	}

	private static boolean isTurnKey(int key) {
		return key == InputConstants.KEY_D || key == InputConstants.KEY_SPACE || key == InputConstants.KEY_RIGHT;
	}

	@Override
	public void tick() {
		super.tick();
		this.check = true;
		this.ticks++;
		Screen ns = HearthLink.nativeScreen();
		if (ns != null && ns != this && ("lockpick".equals(this.name) || "pickpocket".equals(this.name))) {
			ns.tick();  // its clock, and its own close a moment after the attempt ends (it closes this screen)
		}
		// The server ended the event (the folk left earshot, the guard is done, the menu closed): close with it.
		if (HearthSync.state() != null) {
			boolean there = HearthSync.section(this.name) != null;
			if (there) {
				this.seen = true;
			} else if (this.seen && this.minecraft.gui.screen() == this) {
				this.onClose();
			}
		}
	}

	@Override
	public void removed() {
		super.removed();
		this.hub.setListener(null);
		this.hub.setVisible(false);
		// What the page sent in this screen's last frame: an action still counts; native layers and keyboard claims were
		// this screen's and are dropped.
		this.hub.drainInbox(m -> {
			if (m instanceof Bridge.HearthCall call) {
				HearthLink.act(call.op(), call.arg());
			}
		});
		this.fx.clear();
		this.stacks = List.of();
		HearthLink.close(this.name);
	}
}
