package dev.voidmc.hub.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmc.hub.layout.HubLayout;
import dev.voidmc.hub.model.ClickPlanner;
import dev.voidmc.hub.model.Json;
import dev.voidmc.hub.model.StackView;
import dev.voidmc.hub.screen.HubChestScreen;
import dev.voidmc.hub.screen.HubInventoryScreen;
import dev.voidmc.hub.screen.HubScreen;
import dev.voidmc.hub.screen.ItemFactsReader;
import dev.voidmc.hub.ui.HubView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Real clicks, real inventory. Every gesture goes in through {@code MouseHandler} /
 * {@code KeyboardHandler} at the positions the VOID layout drew, and every assertion reads the
 * menu back — on the client, and against the server's copy, so a desync fails the test too.
 */
public final class FunctionalTest {

	private final ClientGameTestContext context;
	private final TestSingleplayerContext world;
	private final TestWorld.Spot spot;
	private final Ui ui;
	private final List<String> passed = new ArrayList<>();

	public FunctionalTest(ClientGameTestContext context, TestSingleplayerContext world, TestWorld.Spot spot) {
		this.context = context;
		this.world = world;
		this.spot = spot;
		this.ui = new Ui(context, world);
	}

	// ---- reading the real menus -------------------------------------------------------------------

	record S(String item, int count) {
		static S of(ItemStack st) {
			return st.isEmpty() ? new S("air", 0) : new S(BuiltInRegistries.ITEM.getKey(st.getItem()).getPath(), st.getCount());
		}

		@Override
		public String toString() {
			return this.count == 0 ? "empty" : this.count + " " + this.item;
		}
	}

	S slot(int i) {
		return this.context.computeOnClient(mc -> S.of(mc.player.containerMenu.slots.get(i).getItem()));
	}

	S carried() {
		return this.context.computeOnClient(mc -> S.of(mc.player.containerMenu.getCarried()));
	}

	List<S> clientMenu() {
		return this.context.computeOnClient(mc -> {
			List<S> l = new ArrayList<>();
			for (var s : mc.player.containerMenu.slots) {
				l.add(S.of(s.getItem()));
			}
			return l;
		});
	}

	List<S> serverMenu() {
		return this.world.getServer().computeOnServer(server -> {
			AbstractContainerMenu m = server.getPlayerList().getPlayers().get(0).containerMenu;
			List<S> l = new ArrayList<>();
			for (var s : m.slots) {
				l.add(S.of(s.getItem()));
			}
			return l;
		});
	}

	int total(String item) {
		int n = 0;
		for (S s : this.clientMenu()) {
			if (s.item.equals(item)) {
				n += s.count;
			}
		}
		S c = this.carried();
		return n + (c.item.equals(item) ? c.count : 0);
	}

	/** Where the screen thinks the pointer is, for failure messages. */
	String where() {
		return this.context.computeOnClient(mc -> {
			var c = mc.gui.screen() instanceof HubScreen<?> h ? h.hoveredCell() : null;
			return "pointer " + Math.round(mc.mouseHandler.xpos()) + "," + Math.round(mc.mouseHandler.ypos()) + " over "
				+ (c == null ? "no well" : "slot " + c.slot()) + " on " + (mc.gui.screen() == null ? "no screen" : mc.gui.screen().getClass().getSimpleName());
		});
	}

	void expect(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError("[voidhub-functional] FAILED: " + what);
		}
	}

	void expectSlot(int i, String item, int count, String what) {
		S s = this.slot(i);
		this.expect(count == 0 ? s.count == 0 : s.item.equals(item) && s.count == count, what + " — slot " + i + " is " + s + ", expected "
			+ (count == 0 ? "empty" : count + " " + item));
	}

	/** Client and server agree on every slot of the open menu. */
	void expectInSync(String what) {
		this.ui.settle();
		List<S> c = this.clientMenu();
		List<S> s = this.serverMenu();
		this.expect(c.equals(s), what + " — client and server menus differ:\n client " + c + "\n server " + s);
	}

	void pass(String step) {
		this.passed.add(step);
		System.out.println("[voidhub-functional] ok  " + step);
	}

	int firstEmptyExcept(int from, int to, int except) {
		for (int i = from; i < to; i++) {
			if (i != except && this.slot(i).count == 0) {
				return i;
			}
		}
		throw new AssertionError("no empty slot in " + from + ".." + to);
	}

	int firstEmpty(int from, int to) {
		for (int i = from; i < to; i++) {
			if (this.slot(i).count == 0) {
				return i;
			}
		}
		throw new AssertionError("no empty slot in " + from + ".." + to);
	}

	// ---- the run ------------------------------------------------------------------------------------

	public void run() {
		// 1. E opens the VOID inventory over the real InventoryMenu.
		this.ui.pressInventory();
		this.context.waitForScreen(HubInventoryScreen.class);
		this.context.waitTicks(5);
		this.ui.parkMouse();
		this.context.waitTicks(2);
		this.context.takeScreenshot("voidhub_01_inventory");
		this.layoutAgrees("inventory @ default scale");
		this.pass("E opens the VOID inventory; page wells and Java hit-testing agree");

		// 2. Left click picks a whole stack up; a left click on an empty well puts it down.
		int a = 9;   // storage 1: 64 stone
		int empty1 = this.firstEmpty(9, 36);
		this.expectSlot(a, "stone", 64, "setup");
		this.ui.clickSlot(a, Ui.LEFT, 0);
		this.expect(this.carried().equals(new S("stone", 64)), "pickup: cursor holds " + this.carried());
		this.expectSlot(a, "", 0, "pickup leaves the slot empty");
		this.ui.hoverSlot(empty1);
		this.context.waitTicks(2);
		this.context.takeScreenshot("voidhub_02_carrying");
		this.ui.clickSlot(empty1, Ui.LEFT, 0);
		this.expectSlot(empty1, "stone", 64, "place");
		this.expect(this.carried().count == 0, "place empties the cursor");
		this.expectInSync("pickup/place");
		this.pass("left-click pickup and place");

		// 3. Right click on a stack takes half; right click with a stack places one.
		this.ui.clickSlot(empty1, Ui.RIGHT, 0);
		this.expect(this.carried().equals(new S("stone", 32)), "right-click split: cursor " + this.carried());
		this.expectSlot(empty1, "stone", 32, "split leaves half");
		int empty2 = this.firstEmptyExcept(9, 36, a);
		this.ui.clickSlot(empty2, Ui.RIGHT, 0);
		this.expectSlot(empty2, "stone", 1, "right-click places one");
		this.expect(this.carried().equals(new S("stone", 31)), "31 left on the cursor");
		this.ui.clickSlot(a, Ui.LEFT, 0);
		this.expectSlot(a, "stone", 31, "put the rest back");
		this.expectInSync("split");
		this.pass("right-click split / place one");

		// 4. Shift-click moves a hotbar stack into storage (vanilla quick-move).
		int torches = this.total("torch");
		this.expectSlot(40, "torch", 30, "setup hotbar 5");
		this.ui.clickSlot(40, Ui.LEFT, Ui.SHIFT);
		this.expectSlot(40, "", 0, "shift-click empties the hotbar slot");
		this.expect(this.total("torch") == torches, "shift-click keeps every torch");
		boolean inStorage = false;
		for (int i = 9; i < 36; i++) {
			if (this.slot(i).item.equals("torch") && this.slot(i).count >= 30) {
				inStorage = true;
			}
		}
		this.expect(inStorage, "the torches landed in storage");
		this.expectInSync("shift-click");
		this.pass("shift-click quick-move");

		// 5. Number key 1 over a slot swaps it with hotbar 1.
		int appleSlot = 17;
		this.expectSlot(appleSlot, "apple", 6, "setup apple");
		this.ui.hoverSlot(appleSlot);
		this.context.waitTicks(2);
		this.ui.pressKey(InputConstants.KEY_1);
		this.ui.settle();
		this.expectSlot(36, "apple", 6, "number key moved the apples to hotbar 1");
		this.expect(this.slot(appleSlot).item.equals("diamond_sword"), "and the sword to the hovered slot: " + this.slot(appleSlot));
		this.ui.pressKey(InputConstants.KEY_1);
		this.ui.settle();
		this.expect(this.slot(36).item.equals("diamond_sword"), "swap back");
		this.expectInSync("hotbar swap");
		this.pass("number-key hotbar swap");

		// 6. Q over a slot drops one item.
		this.expectSlot(13, "coal", 23, "setup coal");
		this.ui.hoverSlot(13);
		this.context.waitTicks(2);
		this.context.getInput().pressKey(o -> o.keyDrop);
		this.ui.settle();
		this.expectSlot(13, "coal", 22, "Q drops one");
		this.expectInSync("drop");
		this.pass("Q drops one from the hovered slot");

		// 7. 2x2 crafting: one oak log in the grid makes four planks; shift-click takes them.
		int planks = this.total("oak_planks");
		this.expectSlot(12, "oak_log", 48, "setup logs");
		this.ui.clickSlot(12, Ui.LEFT, 0);
		this.ui.clickSlot(1, Ui.RIGHT, 0);
		this.ui.clickSlot(12, Ui.LEFT, 0);
		this.expectSlot(1, "oak_log", 1, "one log in the crafting grid");
		this.expectSlot(12, "oak_log", 47, "47 logs back in storage");
		this.expectSlot(0, "oak_planks", 4, "the result well shows four planks");
		this.ui.hoverSlot(0);
		this.context.waitTicks(2);
		this.context.takeScreenshot("voidhub_03_crafting");
		this.ui.clickSlot(0, Ui.LEFT, Ui.SHIFT);
		this.expectSlot(1, "", 0, "crafting consumed the log");
		this.expectSlot(0, "", 0, "no result left");
		this.expect(this.total("oak_planks") == planks + 4, "four planks gained: " + this.total("oak_planks") + " vs " + planks);
		this.expectInSync("craft");
		this.pass("2x2 craft: log -> 4 planks, shift-click result");

		// 8. Drag-distribute (quick craft): 31 stone over three empty wells.
		this.ui.clickSlot(a, Ui.LEFT, 0);  // pick up 31 stone
		int[] targets = new int[3];
		int found = 0;
		for (int i = 9; i < 36 && found < 3; i++) {
			if (this.slot(i).count == 0) {
				targets[found++] = i;
			}
		}
		this.expect(found == 3, "three empty wells for the drag");
		this.ui.hoverSlot(targets[0]);
		this.ui.press(Ui.LEFT, 0);
		this.context.waitTicks(1);
		this.ui.nudge();  // a held button only reports drags on movement: start inside the first well
		this.context.waitTicks(1);
		this.ui.hoverSlot(targets[1]);
		this.context.waitTicks(1);
		this.ui.hoverSlot(targets[2]);
		this.context.waitTicks(2);
		this.context.takeScreenshot("voidhub_04_drag_distribute");
		this.ui.release(Ui.LEFT, 0);
		this.ui.settle();
		for (int t : targets) {
			this.expectSlot(t, "stone", 10, "drag split 31 stone evenly");
		}
		this.expect(this.carried().equals(new S("stone", 1)), "the remainder stays on the cursor: " + this.carried());
		this.ui.clickSlot(a, Ui.LEFT, 0);
		this.expectInSync("drag distribute");
		this.pass("drag-distribute across three wells");

		// 9. Double-click gathers every stone of the kind onto the cursor (up to a stack).
		int stoneTotal = this.total("stone");
		this.ui.doubleClickSlot(targets[0]);
		this.expect(this.carried().item.equals("stone") && this.carried().count == Math.min(64, stoneTotal),
			"double-click collected " + this.carried() + " of " + stoneTotal);
		this.ui.clickSlot(targets[0], Ui.LEFT, 0);
		this.expect(this.total("stone") == stoneTotal, "no stone lost");
		this.expectInSync("double-click collect");
		this.pass("double-click collect");

		// 10. Armour: take the helmet off, put it back on; offhand likewise.
		this.expectSlot(5, "iron_helmet", 1, "setup helmet");
		int spare = this.firstEmpty(9, 36);
		this.ui.clickSlot(5, Ui.LEFT, 0);
		this.expect(this.carried().equals(new S("iron_helmet", 1)), "picking the helmet up: cursor " + this.carried() + ", " + this.where());
		this.ui.clickSlot(spare, Ui.LEFT, 0);
		this.expectSlot(5, "", 0, "helmet off");
		this.ui.clickSlot(spare, Ui.LEFT, 0);
		this.ui.clickSlot(5, Ui.LEFT, 0);
		this.expectSlot(5, "iron_helmet", 1, "helmet back on");
		this.ui.clickSlot(45, Ui.LEFT, 0);
		this.ui.clickSlot(spare, Ui.LEFT, 0);
		this.expectSlot(45, "", 0, "shield out of the offhand");
		this.ui.clickSlot(spare, Ui.LEFT, 0);
		this.ui.clickSlot(45, Ui.LEFT, 0);
		this.expectSlot(45, "shield", 1, "shield back in the offhand");
		this.expectInSync("armour/offhand");
		this.pass("armour and offhand slots");

		// 11. Filter chips are page buttons that reach Java over the bridge.
		this.ui.clickPage(".chip[data-filter=tools]");
		this.expect("tools".equals(this.context.computeOnClient(mc -> ((HubScreen<?>) mc.gui.screen()).filter())), "Tools filter applied");
		this.ui.parkMouse();
		this.context.waitTicks(2);
		this.context.takeScreenshot("voidhub_05_filter_tools");
		this.ui.clickPage(".chip[data-filter=all]");
		this.pass("filter chips (page -> bridge -> Java)");

		// 12. Close with E, re-open with Right Shift, close with Esc.
		this.ui.pressInventory();
		this.context.waitForScreen(null);
		this.ui.pressKey(InputConstants.KEY_RSHIFT);
		this.context.waitForScreen(HubInventoryScreen.class);
		this.ui.pressKey(InputConstants.KEY_ESCAPE);
		this.context.waitForScreen(null);
		this.pass("E closes, R-Shift opens, Esc closes");

		// 13. Chest: the VOID chest over the real ChestMenu; Move all both ways; Sort.
		this.ui.openChest(this.spot.chest());
		this.context.waitForScreen(HubChestScreen.class);
		this.context.waitTicks(5);
		this.ui.hoverSlot(11);
		this.context.waitTicks(3);
		this.context.takeScreenshot("voidhub_06_chest");
		this.layoutAgrees("chest");
		Map<String, Integer> before = this.counts();
		int chestFilled = this.filled(0, 27);
		this.expect(chestFilled == 27, "the chest starts full: " + chestFilled);
		this.ui.clickPage("[data-move=inventory]");
		this.ui.settle();
		int moved = chestFilled - this.filled(0, 27);
		this.expect(moved > 0, "Move all to inventory moved something");
		this.expect(this.counts().equals(before), "Move all to inventory conserved every item");
		this.expectInSync("move all to inventory");
		this.context.takeScreenshot("voidhub_07_chest_after_move_to_inventory");
		this.pass("chest: Move all to inventory (" + moved + " stacks)");

		int storageBefore = this.filled(27, 54);
		this.ui.clickPage("[data-move=container]");
		this.ui.settle();
		this.expect(this.filled(27, 54) < storageBefore, "Move all to chest moved storage stacks");
		this.expect(this.counts().equals(before), "Move all to chest conserved every item");
		this.expectInSync("move all to chest");
		this.pass("chest: Move all to chest");

		this.ui.clickPage(".page-chest [data-sort=container]");
		this.ui.settle();
		this.expect(this.counts().equals(before), "Sort conserved every item");
		this.expectSorted(0, 27);
		this.expectInSync("sort");
		this.ui.parkMouse();
		this.context.waitTicks(2);
		this.context.takeScreenshot("voidhub_08_chest_sorted");
		this.pass("chest: Sort (client-side, legal clicks)");
		this.ui.pressKey(InputConstants.KEY_ESCAPE);
		this.context.waitForScreen(null);

		// 14. The layout agrees with the page at every GUI scale.
		for (int gs = 1; gs <= 4; gs++) {
			int scale = gs;
			this.context.runOnClient(mc -> {
				mc.options.guiScale().set(scale);
				mc.resizeGui();
			});
			this.ui.pressInventory();
			this.context.waitForScreen(HubInventoryScreen.class);
			this.context.waitTicks(3);
			this.layoutAgrees("inventory @ GUI scale " + gs);
			this.ui.parkMouse();
			this.context.waitTicks(2);
			this.context.takeScreenshot("voidhub_09_inventory_gui" + gs);
			this.ui.pressKey(InputConstants.KEY_ESCAPE);
			this.context.waitForScreen(null);
		}
		this.context.runOnClient(mc -> {
			mc.options.guiScale().set(0);
			mc.resizeGui();
		});
		this.pass("page wells match Java's layout at GUI scales 1-4");
		System.out.println("[voidhub-functional] ALL PASSED (" + this.passed.size() + " checks)");
	}

	/** Every well the page drew is where Java hit-tests it (within one device pixel). */
	@SuppressWarnings("unchecked")
	void layoutAgrees(String what) {
		String json = this.context.computeOnClient(mc -> HubView.get().wellRects());
		HubLayout l = this.ui.layout();
		List<Object> rects = (List<Object>) Json.parse(json);
		this.expect(rects.size() == l.cells().size(), what + ": page drew " + rects.size() + " wells, layout has " + l.cells().size());
		for (Object o : rects) {
			Map<String, Object> r = (Map<String, Object>) o;
			int slot = ((Double) r.get("i")).intValue();
			HubLayout.Cell c = l.cellForSlot(slot);
			this.expect(c != null, what + ": page well " + slot + " not in the layout");
			double x = (Double) r.get("x") * l.u;
			double y = (Double) r.get("y") * l.u;
			double w = (Double) r.get("w") * l.u;
			double h = (Double) r.get("h") * l.u;
			this.expect(Math.abs(x - c.cell().x()) <= 1 && Math.abs(y - c.cell().y()) <= 1 && Math.abs(w - c.cell().w()) <= 1
				&& Math.abs(h - c.cell().h()) <= 1, what + ": well " + slot + " drawn at " + x + "," + y + " " + w + "x" + h + " but hit-tested at " + c.cell());
		}
		System.out.println("[voidhub-functional] layout agrees (" + rects.size() + " wells, u=" + l.u + "): " + what);
	}

	Map<String, Integer> counts() {
		Map<String, Integer> m = new HashMap<>();
		for (S s : this.clientMenu()) {
			if (s.count > 0) {
				m.merge(s.item, s.count, Integer::sum);
			}
		}
		S c = this.carried();
		if (c.count > 0) {
			m.merge(c.item, c.count, Integer::sum);
		}
		return m;
	}

	int filled(int from, int to) {
		int n = 0;
		List<S> m = this.clientMenu();
		for (int i = from; i < to; i++) {
			if (m.get(i).count > 0) {
				n++;
			}
		}
		return n;
	}

	void expectSorted(int from, int to) {
		List<StackView> views = this.context.computeOnClient(mc -> {
			List<StackView> l = new ArrayList<>();
			for (int i = from; i < to; i++) {
				l.add(ItemFactsReader.view(mc.player.containerMenu.slots.get(i).getItem()));
			}
			return l;
		});
		boolean seenEmpty = false;
		for (int i = 0; i < views.size(); i++) {
			if (views.get(i).isEmpty()) {
				seenEmpty = true;
				continue;
			}
			this.expect(!seenEmpty, "sorted: no stack after an empty slot (slot " + (from + i) + ")");
			if (i > 0 && !views.get(i - 1).isEmpty()) {
				this.expect(ClickPlanner.ORDER.compare(views.get(i - 1), views.get(i)) <= 0, "sorted order at slot " + (from + i));
			}
		}
	}
}
