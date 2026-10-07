package dev.voidmc.hub.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmc.hub.layout.HubLayout;
import dev.voidmc.hub.layout.Rect;
import dev.voidmc.hub.map.HubMap;
import dev.voidmc.hub.map.MapHost;
import dev.voidmc.hub.screen.HubInventoryScreen;
import dev.voidmc.hub.screen.HubScreen;
import dev.voidmc.hub.ui.HubView;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * The hub's Map page, through the real input path: with VOID Atlas installed (the dev run's
 * {@code -Patlas}) and without. Opening on the Map page, switching pages (Tab, Shift-Tab, the
 * pages island, E), panning and zooming the map, the strip's "Waypoint here", Esc going to the map
 * before the hub, and the widget being kept between opens. Screenshots of each state.
 */
public final class MapTest {

	private final ClientGameTestContext context;
	private final TestSingleplayerContext world;
	private final Ui ui;
	private int passed;

	public MapTest(ClientGameTestContext context, TestSingleplayerContext world) {
		this.context = context;
		this.world = world;
		this.ui = new Ui(context, world);
	}

	void expect(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError("[voidhub-map] FAILED: " + what);
		}
	}

	void pass(String step) {
		this.passed++;
		System.out.println("[voidhub-map] ok  " + step);
	}

	String page() {
		return this.context.computeOnClient(mc -> mc.gui.screen() instanceof HubScreen<?> s ? s.page() + (s.onMap() ? "/map-layout" : "") : "none");
	}

	boolean onMap() {
		return this.context.computeOnClient(mc -> mc.gui.screen() instanceof HubScreen<?> s && s.onMap());
	}

	String pageEval(String js) {
		return this.context.computeOnClient(mc -> HubView.get().eval(js));
	}

	boolean bodyHas(String cls) {
		return "true".equals(this.pageEval("String(document.body.classList.contains('" + cls + "'))"));
	}

	String activePage() {
		return this.pageEval("(function(){var e=document.querySelector('#pages .page.active');return e?e.getAttribute('data-page'):'';})()");
	}

	void waitFrames(int ticks) {
		this.context.waitTicks(ticks);
	}

	public void run() {
		boolean atlas = this.context.computeOnClient(mc -> HubMap.available());
		System.out.println("[voidhub-map] VOID Atlas " + (atlas ? "installed" : "not installed"));
		this.context.runOnClient(mc -> HubInventoryScreen.resetLastPage());
		if (atlas) {
			this.withAtlas();
		} else {
			this.withoutAtlas();
		}
		System.out.println("[voidhub-map] ALL PASSED (" + this.passed + " checks, Atlas " + (atlas ? "installed" : "not installed") + ")");
	}

	// ---- without Atlas: the stand-in ----------------------------------------------------------------

	private void withoutAtlas() {
		// R-Shift opens on the Inventory (a stand-in is not a first page); Tab shows the Map page.
		this.ui.pressKey(InputConstants.KEY_RSHIFT);
		this.context.waitForScreen(HubInventoryScreen.class);
		this.waitFrames(3);
		this.expect(this.page().equals("inventory"), "without Atlas R-Shift opens on the Inventory, not " + this.page());
		this.ui.pressKey(InputConstants.KEY_TAB);
		this.waitFrames(3);
		this.expect(this.onMap(), "Tab shows the Map page: " + this.page());
		this.expect(this.bodyHas("map") && !this.bodyHas("atlas"), "the page draws the map window without Atlas");
		this.expect("map".equals(this.activePage()), "the pages island marks Map: " + this.activePage());
		String standin = this.pageEval("getComputedStyle(document.querySelector('.page-map')).display");
		this.expect("block".equals(standin), "the stand-in shows: display " + standin);
		this.expect(this.context.computeOnClient(mc -> HubMap.existing()) == null, "no map widget without Atlas");
		this.ui.parkMouse();
		this.waitFrames(2);
		this.context.takeScreenshot("voidhub_map_10_standin");
		this.pass("Map page without Atlas: the stand-in, in the hub's window, islands around it");

		// The pill is dimmed and inert; the window takes no vanilla clicks.
		this.expect(this.bodyHas("map") && "true".equals(this.pageEval("String(document.getElementById('pill').classList.contains('off'))")),
			"the Waypoint pill is dimmed without Atlas");
		this.ui.clickPage("#pill");
		Rect w = this.ui.layout().window;
		this.ui.moveTo(w.centerX(), w.centerY());
		this.ui.press(Ui.LEFT, 0);
		this.ui.release(Ui.LEFT, 0);
		this.ui.settle();
		this.expect(this.onMap(), "still on the Map page after clicks on it");
		this.pass("Waypoint pill inert and the window click-safe without Atlas");

		this.switchPagesAndClose();
	}

	// ---- with Atlas: the real map -------------------------------------------------------------------

	private void withAtlas() {
		int createdBefore = this.context.computeOnClient(mc -> HubMap.created);
		this.ui.pressKey(InputConstants.KEY_RSHIFT);
		this.context.waitForScreen(HubInventoryScreen.class);
		this.waitFrames(5);
		this.expect(this.onMap(), "with Atlas R-Shift opens on the Map page, not " + this.page());
		this.expect(this.bodyHas("map") && this.bodyHas("atlas"), "the page draws the map window for Atlas");
		this.expect("map".equals(this.activePage()), "the pages island marks Map: " + this.activePage());
		this.expect("none".equals(this.pageEval("getComputedStyle(document.querySelector('.page-map')).display")), "no stand-in with Atlas");
		MapHost host = this.context.computeOnClient(mc -> HubMap.existing());
		this.expect(host != null, "the widget was made on the first Map frame");
		int created = this.context.computeOnClient(mc -> HubMap.created);
		this.expect(created == createdBefore + 1, "one widget made: " + created);
		double createMs = this.context.computeOnClient(mc -> HubMap.lastCreateNs / 1e6);
		System.out.printf(Locale.ROOT, "[voidhub-map] Atlas widget made in %.2f ms%n", createMs);
		// Let Atlas draw the tiles round the player.
		this.ui.parkMouse();
		this.waitFrames(60);
		this.context.takeScreenshot("voidhub_map_01_atlas");
		this.pass("R-Shift opens the hub on the Map page; Atlas's map in the hub's window");

		// Pan: a drag inside the map moves its centre (the map gets the drag, the page does not).
		Rect w = this.ui.layout().window;
		double cx = w.x() + w.w() * 0.38;
		double cy = w.y() + w.h() * 0.5;
		double[] before = this.view(host);
		this.ui.moveTo(cx, cy);
		this.waitFrames(1);
		this.ui.press(Ui.LEFT, 0);
		for (int i = 1; i <= 12; i++) {
			this.ui.moveTo(cx - 22 * i, cy - 12 * i);
			this.waitFrames(1);
		}
		this.ui.release(Ui.LEFT, 0);
		this.waitFrames(3);
		double[] after = this.view(host);
		this.expect(Math.abs(after[0] - before[0]) > 1 || Math.abs(after[1] - before[1]) > 1,
			"dragging panned the map: centre " + fmt(before) + " -> " + fmt(after));
		this.expect(this.onMap(), "panning kept the Map page");
		this.context.takeScreenshot("voidhub_map_02_panned");
		this.pass("drag pans the map " + fmt(before) + " -> " + fmt(after));

		// Zoom: the wheel over the map is the map's.
		this.ui.moveTo(cx, cy);
		this.waitFrames(1);
		double k0 = this.view(host)[2];
		this.context.getInput().scroll(1);
		this.waitFrames(3);
		double k1 = this.view(host)[2];
		this.context.getInput().scroll(-1);
		this.waitFrames(3);
		this.expect(k1 != k0, "scrolling zoomed the map: " + k0 + " -> " + k1);
		this.pass("scroll zooms the map (" + k0 + " -> " + k1 + " px/block)");

		// The strip's "Waypoint here" adds an Atlas waypoint at the player and selects it.
		int n0 = this.context.computeOnClient(mc -> host.waypointCount());
		this.ui.clickPage("#pill");
		this.waitFrames(3);
		int n1 = this.context.computeOnClient(mc -> host.waypointCount());
		this.expect(n1 == n0 + 1, "Waypoint here added one Atlas waypoint: " + n0 + " -> " + n1);
		String pill = this.pageEval("document.getElementById('pill-text').textContent");
		this.expect(pill.endsWith("added"), "the pill says so: " + pill);
		this.ui.moveTo(w.x() + w.w() * 0.6, w.y() + w.h() * 0.45);
		this.waitFrames(10);
		this.context.takeScreenshot("voidhub_map_03_waypoint_added");
		this.pass("Waypoint here adds an Atlas waypoint (" + n0 + " -> " + n1 + "): \"" + pill + "\"");

		// Esc goes to the map first (it closes the waypoint's card), then to the hub.
		this.ui.pressKey(InputConstants.KEY_ESCAPE);
		this.waitFrames(2);
		this.expect(this.context.computeOnClient(mc -> mc.gui.screen() instanceof HubInventoryScreen), "the first Esc closed the map's card, not the hub");
		this.pass("Esc reaches the map before the hub");

		// Pages: Tab, Shift-Tab, the island, E; the same widget throughout.
		this.ui.pressKey(InputConstants.KEY_TAB);
		this.waitFrames(3);
		this.expect(this.page().equals("inventory") && "inventory".equals(this.activePage()), "Tab -> Inventory: " + this.page());
		HubLayout inv = this.ui.layout();
		this.expect(inv.kind == HubLayout.Kind.INVENTORY && !inv.cells().isEmpty(), "the inventory layout is back");
		this.context.takeScreenshot("voidhub_map_04_tab_inventory");
		this.context.getInput().holdShift();
		this.ui.pressKey(InputConstants.KEY_TAB);
		this.context.getInput().releaseShift();
		this.waitFrames(3);
		this.expect(this.onMap(), "Shift-Tab -> Map: " + this.page());
		this.ui.clickPage("#pages .page[data-page=inventory]");
		this.waitFrames(2);
		this.expect(this.page().equals("inventory"), "clicking Inventory in the island: " + this.page());
		this.ui.clickPage("#pages .page[data-page=map]");
		this.waitFrames(2);
		this.expect(this.onMap(), "clicking Map in the island: " + this.page());
		this.context.getInput().pressKey(o -> o.keyInventory);
		this.waitFrames(2);
		this.expect(this.page().equals("inventory"), "E on the Map page shows the Inventory: " + this.page());
		this.ui.pressKey(InputConstants.KEY_TAB);
		this.waitFrames(2);
		this.expect(this.context.computeOnClient(mc -> HubMap.created) == created, "the widget is kept across page switches");
		this.pass("Tab / Shift-Tab / island / E switch pages; one widget");

		// R-Shift closes from the map and re-opens there, with the same widget.
		double[] left = this.view(host);
		this.ui.pressKey(InputConstants.KEY_RSHIFT);
		this.context.waitForScreen(null);
		this.ui.pressKey(InputConstants.KEY_RSHIFT);
		this.context.waitForScreen(HubInventoryScreen.class);
		this.waitFrames(3);
		this.expect(this.onMap(), "R-Shift re-opens where the hub was left: " + this.page());
		this.expect(this.context.computeOnClient(mc -> HubMap.existing()) == host && this.context.computeOnClient(mc -> HubMap.created) == created,
			"the widget is reused between opens");
		double[] kept = this.view(host);
		this.expect(Math.abs(kept[0] - left[0]) < 1e-6 && Math.abs(kept[1] - left[1]) < 1e-6, "the map is where it was left: " + fmt(kept) + " vs " + fmt(left));
		this.pass("R-Shift closes and re-opens on the map, widget and view kept");

		this.ui.parkMouse();
		this.waitFrames(2);
		this.context.takeScreenshot("voidhub_map_05_reopened");

		// Typing: with the map's search focused, keys are text (Tab and R-Shift included) and the
		// first Esc only leaves the field; the next one closes the hub.
		double unit = atlasUnit(w);
		this.ui.moveTo(w.right() - (28 + 21) * unit, w.y() + (31 + 21) * unit);
		this.waitFrames(1);
		this.ui.press(Ui.LEFT, 0);
		this.ui.release(Ui.LEFT, 0);
		this.waitFrames(2);
		this.expect(this.context.computeOnClient(mc -> host.typing()), "clicking the search icon focused Atlas's search");
		this.context.getInput().typeChars("ho");
		this.ui.pressKey(InputConstants.KEY_TAB);
		this.ui.pressKey(InputConstants.KEY_RSHIFT);
		this.waitFrames(3);
		this.expect(this.onMap(), "while typing, Tab and R-Shift are the search's: still on the map (" + this.page() + ")");
		this.context.takeScreenshot("voidhub_map_07_search");
		this.ui.pressKey(InputConstants.KEY_ESCAPE);
		this.waitFrames(2);
		this.expect(this.onMap() && !this.context.computeOnClient(mc -> host.typing()), "the first Esc left the search, the hub stays");
		this.ui.pressKey(InputConstants.KEY_ESCAPE);
		this.context.waitForScreen(null);
		this.pass("typing in Atlas's search keeps Tab / R-Shift; Esc leaves the field, then closes the hub");

		// Every GUI scale: the window and the widget's rectangle agree, screenshots of each.
		for (int gs = 1; gs <= 4; gs++) {
			int scale = gs;
			this.context.runOnClient(mc -> {
				mc.options.guiScale().set(scale);
				mc.resizeGui();
			});
			this.ui.pressKey(InputConstants.KEY_RSHIFT);
			this.context.waitForScreen(HubInventoryScreen.class);
			this.waitFrames(5);
			HubLayout l = this.ui.layout();
			this.expect(l.kind == HubLayout.Kind.MAP, "map page at GUI scale " + gs);
			String rect = this.pageEval("(function(){var r=document.getElementById('win').getBoundingClientRect();return [r.left,r.top,r.width,r.height].join(',');})()");
			String[] p = rect.split(",");
			double u = l.u;
			double x = Double.parseDouble(p[0]) * u;
			double y = Double.parseDouble(p[1]) * u;
			double ww = Double.parseDouble(p[2]) * u;
			double hh = Double.parseDouble(p[3]) * u;
			this.expect(Math.abs(x - l.window.x()) <= 1 && Math.abs(y - l.window.y()) <= 1 && Math.abs(ww - l.window.w()) <= 1 && Math.abs(hh - l.window.h()) <= 1,
				"GUI scale " + gs + ": page window " + rect + " (css) vs layout " + l.window);
			this.ui.parkMouse();
			this.waitFrames(3);
			this.context.takeScreenshot("voidhub_map_06_gui" + gs);
			this.ui.pressKey(InputConstants.KEY_ESCAPE);
			this.context.waitForScreen(null);
		}
		this.context.runOnClient(mc -> {
			mc.options.guiScale().set(0);
			mc.resizeGui();
		});
		this.pass("the page's window is the widget's rectangle at GUI scales 1-4");
	}

	/** Tab back and forth, the island, then Esc; shared by both runs. */
	private void switchPagesAndClose() {
		this.ui.pressKey(InputConstants.KEY_TAB);
		this.waitFrames(3);
		this.expect(this.page().equals("inventory"), "Tab back to the Inventory: " + this.page());
		this.ui.clickPage("#pages .page[data-page=map]");
		this.waitFrames(2);
		this.expect(this.onMap(), "clicking Map in the island: " + this.page());
		this.ui.clickPage("#pages .page[data-page=quests]");
		this.waitFrames(2);
		this.expect(this.onMap(), "Quests is not built: the page stays: " + this.page());
		this.ui.pressKey(InputConstants.KEY_RSHIFT);
		this.context.waitForScreen(null);
		this.ui.pressKey(InputConstants.KEY_RSHIFT);
		this.context.waitForScreen(HubInventoryScreen.class);
		this.waitFrames(2);
		this.expect(this.onMap(), "R-Shift re-opens where the hub was left: " + this.page());
		this.ui.pressKey(InputConstants.KEY_ESCAPE);
		this.context.waitForScreen(null);
		this.pass("Tab / island switch pages; R-Shift and Esc close; R-Shift re-opens on the last page");
	}

	/** The scale Atlas lays its page out at in a rectangle (its {@code Ui.fit}: the largest of its units that fits). */
	static double atlasUnit(Rect w) {
		double[] units = {0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0};
		double want = Math.min(w.w() / 1280.0, w.h() / 744.0);
		double best = units[0];
		for (double u : units) {
			if (u <= want + 1e-4) {
				best = u;
			}
		}
		return best;
	}

	private double[] view(MapHost host) {
		double[] v = this.context.computeOnClient(mc -> host.view());
		this.expect(v != null, "Atlas's map view could not be read (MapWidget has no view getter; the reflective read failed)");
		return v;
	}

	static String fmt(double[] v) {
		return String.format(Locale.ROOT, "(%.1f, %.1f @ %.3f)", v[0], v[1], v[2]);
	}
}
