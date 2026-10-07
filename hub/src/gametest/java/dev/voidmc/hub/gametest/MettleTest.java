package dev.voidmc.hub.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmc.hub.layout.HubLayout;
import dev.voidmc.hub.mettle.MettleLink;
import dev.voidmc.hub.model.Json;
import dev.voidmc.hub.screen.HubInventoryScreen;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.Slot;

/**
 * VOID Mettle inside the hub ({@code -Dvoidhub.test=mettle}, Mettle's jar on the run's classpath:
 * {@code ./gradlew hubMettle}). Everything goes through real input: gear is put on by shift-clicking
 * it in the VOID inventory (Mettle's own quick-move sends gear to its slot), the Gear page takes a
 * stack into the pack, and the Classes page learns a perk and places an attribute point by
 * clicking the page. Each step is photographed.
 */
public final class MettleTest {

	private static final int SHIFT = Ui.SHIFT;

	private final ClientGameTestContext context;
	private final TestSingleplayerContext world;
	private final Ui ui;

	public MettleTest(ClientGameTestContext context, TestSingleplayerContext world) {
		this.context = context;
		this.world = world;
		this.ui = new Ui(context, world);
	}

	public void run() {
		this.expect(this.context.computeOnClient(mc -> MettleLink.present()), "Mettle is loaded and its overlay API links");
		var server = this.world.getServer();
		server.runCommand("mettle background set mettle:ranger Player0");
		server.runCommand("mettle xp mettle:mining 2600 Player0");
		server.runCommand("mettle xp mettle:marksmanship 5200 Player0");
		server.runCommand("mettle xp mettle:survival 3100 Player0");
		server.runCommand("mettle xp mettle:blades 900 Player0");
		server.runCommand("give Player0 mettle:leather_pack");
		server.runCommand("give Player0 mettle:belt");
		server.runCommand("give Player0 mettle:quiver");
		server.runCommand("give Player0 minecraft:arrow 64");
		this.context.waitTicks(10);
		// A new character meets Mettle's background picker on join; the command above chose for them.
		this.context.runOnClient(mc -> mc.gui.setScreen(null));
		this.context.waitTicks(5);

		// 1. The inventory page has Back, Belt and Quiver wells beside the figure.
		this.ui.pressInventory();
		this.context.waitForScreen(HubInventoryScreen.class);
		this.context.waitTicks(10);
		this.expect(this.ui.layout().cellForSlot(MettleLink.BACK) != null, "the inventory page shows the Back well");
		this.ui.parkMouse();
		this.context.waitTicks(3);
		this.context.takeScreenshot("voidhub_mettle_01_inventory");

		// 2. Shift-click puts each piece of gear on.
		for (String gear : List.of("leather_pack", "belt", "quiver")) {
			int at = this.find(gear, 9, 45);
			this.expect(at >= 0, gear + " is in the inventory");
			this.ui.clickSlot(at, Ui.LEFT, SHIFT);
		}
		this.context.waitTicks(5);
		this.expect(this.item(MettleLink.BACK).equals("leather_pack"), "shift-click wore the pack: " + this.item(MettleLink.BACK));
		this.expect(this.item(MettleLink.BELT).equals("belt"), "shift-click wore the belt: " + this.item(MettleLink.BELT));
		this.expect(this.item(MettleLink.QUIVER).equals("quiver"), "shift-click wore the quiver: " + this.item(MettleLink.QUIVER));
		this.ui.parkMouse();
		this.context.waitTicks(3);
		this.context.takeScreenshot("voidhub_mettle_02_gear_worn");

		// 3. The Gear segment: pack pockets on top, the inventory below.
		this.ui.clickPage("[data-seg=\"gear\"]");
		this.context.waitTicks(5);
		this.expect(this.ui.layout().kind == HubLayout.Kind.GEAR, "the Gear segment switched the layout");
		int stone = this.find("cobblestone", 9, 45);
		if (stone < 0) {
			server.runCommand("give Player0 minecraft:cobblestone 64");
			this.context.waitTicks(5);
			stone = this.find("cobblestone", 9, 45);
		}
		this.ui.clickSlot(stone, Ui.LEFT, SHIFT);
		this.context.waitTicks(3);
		this.expect(this.find("cobblestone", MettleLink.PACK_FIRST, MettleLink.PACK_FIRST + MettleLink.PACK_SLOTS) >= 0,
			"shift-click on the Gear page sent the cobblestone into the pack");
		this.expect(this.activePockets(MettleLink.PACK_FIRST, MettleLink.PACK_SLOTS) == 18, "a Leather Pack has 18 pockets");
		this.ui.hoverSlot(this.find("cobblestone", MettleLink.PACK_FIRST, MettleLink.PACK_FIRST + MettleLink.PACK_SLOTS));
		this.context.waitTicks(15);
		this.context.takeScreenshot("voidhub_mettle_03_gear_page");

		// 4. Classes: the overview.
		this.ui.clickPage("[data-seg=\"classes\"]");
		this.context.waitTicks(25);
		this.expect(this.ui.layout().kind == HubLayout.Kind.CLASSES, "the Classes segment switched the layout");
		this.ui.parkMouse();
		this.context.waitTicks(3);
		this.context.takeScreenshot("voidhub_mettle_04_gallery");

		// 5. Place an attribute point by clicking +.
		int strength = this.attribute("strength");
		this.ui.clickPage("[data-attr=\"strength\"]");
		this.context.waitTicks(20);
		this.expect(this.attribute("strength") == strength + 1, "+ placed a point in Strength: " + strength + " -> " + this.attribute("strength"));

		// 6. A skill tree: open Mining from the gallery, pick Prospector, learn it.
		this.ui.clickPage("[data-skill=\"mettle:mining\"]");
		this.context.waitTicks(3);
		this.ui.clickPage("[data-perk=\"mettle:mining/prospector\"]");
		this.context.waitTicks(3);
		this.ui.parkMouse();
		this.context.waitTicks(3);
		this.context.takeScreenshot("voidhub_mettle_05_tree_before");
		this.ui.clickPage("[data-learn]");
		this.context.waitTicks(25);
		this.expect(this.rank("mettle:mining/prospector") == 1, "Learn bought Prospector's first rank: " + this.rank("mettle:mining/prospector"));
		this.ui.parkMouse();
		this.context.waitTicks(3);
		this.context.takeScreenshot("voidhub_mettle_06_tree_learned");
		this.ui.clickPage("[data-perk=\"mettle:mining/ore_sense\"]");
		this.context.waitTicks(3);
		this.ui.parkMouse();
		this.context.waitTicks(3);
		this.context.takeScreenshot("voidhub_mettle_07_tree_locked");
		this.ui.clickPage(".o-rail [data-skill=\"mettle:marksmanship\"]");
		this.context.waitTicks(3);
		this.ui.parkMouse();
		this.context.waitTicks(3);
		this.context.takeScreenshot("voidhub_mettle_08_tree_marksmanship");
		this.ui.clickPage("[data-back]");
		this.context.waitTicks(3);
		this.hoverPage("[data-skill=\"mettle:survival\"]");
		this.context.waitTicks(5);
		this.context.takeScreenshot("voidhub_mettle_09_gallery");

		// 7. K opens the hub on Classes.
		this.context.runOnClient(mc -> mc.gui.setScreen(null));
		this.context.waitTicks(5);
		this.context.getInput().pressKey(InputConstants.KEY_K);
		this.context.waitForScreen(HubInventoryScreen.class);
		this.context.waitTicks(10);
		this.expect("classes".equals(this.context.computeOnClient(mc -> ((HubInventoryScreen) mc.gui.screen()).page())), "K opened the hub on Classes");
		this.context.runOnClient(mc -> mc.gui.setScreen(null));
		this.context.waitTicks(3);
		dev.voidmc.hub.VoidHub.LOGGER.info("VOID Hub + Mettle: all steps passed");
	}

	/** Moves the pointer onto a page element, where the page drew it (no click). */
	private void hoverPage(String selector) {
		String json = this.context.computeOnClient(mc -> dev.voidmc.hub.ui.HubView.get().eval(
			"(function(){var e=document.querySelector(" + Json.quote(selector) + ");if(!e)return '';var r=e.getBoundingClientRect();return JSON.stringify({x:r.left+r.width/2,y:r.top+r.height/2});})()"));
		this.expect(json != null && !json.isEmpty(), "the page has " + selector);
		Map<String, Object> p = Json.parseObject(json);
		double u = this.ui.layout().u;
		this.ui.moveTo((Double) p.get("x") * u, (Double) p.get("y") * u);
	}

	private String item(int slot) {
		return this.context.computeOnClient(mc -> {
			Slot s = mc.player.inventoryMenu.slots.get(slot);
			return s.hasItem() ? BuiltInRegistries.ITEM.getKey(s.getItem().getItem()).getPath() : "";
		});
	}

	private int find(String path, int from, int to) {
		return this.context.computeOnClient(mc -> {
			for (int i = from; i < to; i++) {
				Slot s = mc.player.inventoryMenu.slots.get(i);
				if (s.isActive() && s.hasItem() && BuiltInRegistries.ITEM.getKey(s.getItem().getItem()).getPath().equals(path)) {
					return i;
				}
			}
			return -1;
		});
	}

	private int activePockets(int first, int n) {
		return this.context.computeOnClient(mc -> {
			int on = 0;
			for (int i = first; i < first + n; i++) {
				if (mc.player.inventoryMenu.slots.get(i).isActive()) {
					on++;
				}
			}
			return on;
		});
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> state() {
		return Json.parseObject(this.context.computeOnClient(mc -> MettleLink.state()));
	}

	@SuppressWarnings("unchecked")
	private int attribute(String id) {
		Map<String, Object> character = (Map<String, Object>) this.state().get("character");
		return ((Number) ((Map<String, Object>) character.get("attributes")).get(id)).intValue();
	}

	@SuppressWarnings("unchecked")
	private int rank(String perk) {
		for (Object o : (List<Object>) this.state().get("perks")) {
			Map<String, Object> p = (Map<String, Object>) o;
			if (perk.equals(p.get("id"))) {
				return ((Number) p.get("rank")).intValue();
			}
		}
		return -1;
	}

	private void expect(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError("VOID Hub + Mettle: " + what);
		}
		dev.voidmc.hub.VoidHub.LOGGER.info("PASS  {}", what);
	}
}
