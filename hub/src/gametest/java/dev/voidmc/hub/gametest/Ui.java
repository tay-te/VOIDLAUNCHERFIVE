package dev.voidmc.hub.gametest;

import dev.voidmc.hub.layout.HubLayout;
import dev.voidmc.hub.layout.Rect;
import dev.voidmc.hub.model.Json;
import dev.voidmc.hub.screen.HubScreen;
import dev.voidmc.hub.ui.HubView;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Drives the VOID screens like a player: pointer moves and button presses go through
 * {@code MouseHandler} (so double-clicks, drags and modifiers are vanilla's), keys through
 * {@code KeyboardHandler}. Positions come from the screen's own layout, in window px — which,
 * with the test window at 1:1, are framebuffer px.
 */
public final class Ui {

	/** SDL button numbers (Minecraft 26.x). */
	public static final int LEFT = 1;
	public static final int RIGHT = 3;
	/** SDL keymod: left shift. */
	public static final int SHIFT = 0x0001;

	private final ClientGameTestContext context;
	private final TestSingleplayerContext world;

	public Ui(ClientGameTestContext context, TestSingleplayerContext world) {
		this.context = context;
		this.world = world;
	}

	public HubLayout layout() {
		return this.context.computeOnClient(mc -> mc.gui.screen() instanceof HubScreen<?> s ? s.layout()
			: mc.gui.screen() instanceof dev.voidmc.hub.screen.HubEventScreen e ? e.layout() : null);
	}

	public Rect cell(int slot) {
		HubLayout l = this.layout();
		if (l == null) {
			throw new AssertionError("no VOID screen open");
		}
		HubLayout.Cell c = l.cellForSlot(slot);
		if (c == null) {
			throw new AssertionError("slot " + slot + " is not visible in the VOID layout");
		}
		return c.cell();
	}

	public void moveTo(double x, double y) {
		this.context.getInput().setCursorPos(x, y);
	}

	public void hoverSlot(int slot) {
		Rect r = this.cell(slot);
		this.moveTo(r.centerX(), r.centerY());
	}

	/** Moves the pointer by a device pixel (a held button turns that into a drag event). */
	public void nudge() {
		this.context.getInput().moveCursor(1, 1);
	}

	/** Pointer on the window background, on no slot. */
	public void parkMouse() {
		HubLayout l = this.layout();
		Rect w = l.window;
		this.moveTo(w.x() + w.w() * 0.5, w.y() + 12 * l.u);
	}

	public void press(int button, int mods) {
		this.context.runOnClient(mc -> mc.mouseHandler.onButton(mc.getWindow().handle(), new MouseButtonInfo(button, mods), 1));
	}

	public void release(int button, int mods) {
		this.context.runOnClient(mc -> mc.mouseHandler.onButton(mc.getWindow().handle(), new MouseButtonInfo(button, mods), 0));
	}

	/** A full click on a slot (press + release in one tick), then a tick for the server. */
	public void clickSlot(int slot, int button, int mods) {
		this.hoverSlot(slot);
		this.press(button, mods);
		this.release(button, mods);
		this.settle();
	}

	public void doubleClickSlot(int slot) {
		this.hoverSlot(slot);
		this.press(LEFT, 0);
		this.release(LEFT, 0);
		this.press(LEFT, 0);
		this.release(LEFT, 0);
		this.settle();
		// MouseHandler counts any click within 250 ms of the last as a double click, and vanilla
		// turns a double click on the same slot into "collect" again: a click on that slot right
		// after this one would be a triple click, not a place. Wait it out, as a player would.
		this.context.waitTicks(7);
	}

	/** Clicks the page element matching a CSS selector, where the page drew it. */
	public void clickPage(String selector) {
		String json = this.context.computeOnClient(mc -> HubView.get().eval(
			"(function(){var e=document.querySelector(" + Json.quote(selector) + ");if(!e)return '';var r=e.getBoundingClientRect();return JSON.stringify({x:r.left+r.width/2,y:r.top+r.height/2});})()"));
		if (json == null || json.isEmpty()) {
			throw new AssertionError("page has no element " + selector);
		}
		Map<String, Object> p = Json.parseObject(json);
		double u = this.layout().u;
		this.moveTo((Double) p.get("x") * u, (Double) p.get("y") * u);
		this.press(LEFT, 0);
		this.release(LEFT, 0);
		this.settle();
	}

	/** Lets the click packets reach the server and the slot updates come back. */
	public void settle() {
		this.context.waitTicks(2);
		this.world.getConnection().waitForServerboundPackets();
		this.world.getConnection().waitForClientboundPackets();
		this.context.waitTicks(1);
	}

	public void pressInventory() {
		this.context.getInput().pressKey(o -> o.keyInventory);
	}

	public void pressKey(int scancode) {
		this.context.getInput().pressKey(scancode);
	}

	/** Uses the block at {@code pos} without waiting afterwards (latency measurements). */
	public void openChestNoSettle(BlockPos pos) {
		this.context.getInput().lookAt(pos);
		this.context.runOnClient(mc -> {
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), net.minecraft.core.Direction.NORTH, pos, false);
			mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
		});
	}

	/** Uses the block at {@code pos} (opens a chest) the way a right click would. */
	public void openChest(BlockPos pos) {
		this.context.getInput().lookAt(pos);
		this.context.runOnClient(mc -> {
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), net.minecraft.core.Direction.NORTH, pos, false);
			mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
		});
		this.settle();
	}
}
