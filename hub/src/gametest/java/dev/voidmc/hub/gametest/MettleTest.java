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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * VOID Mettle inside the hub ({@code -Dvoidhub.test=mettle}, Mettle's jar on the run's classpath:
 * {@code ./gradlew hubMettle}). Everything the player does goes through real input: gear is put on by shift-clicking it,
 * the Gear page takes a stack into the pack, and the Classes page (cards v2) is driven by clicks, keys, the wheel and a
 * drag: a draft (hover, 2 to keep, Redeal all, Esc), a Mythic dealt in a draft (the roll forced) and one earned outside
 * it, the Hall of Firsts, the binder, the deck builder (drag into a slot, the set moment, the take-out popover), the
 * tree, a Card Shrine (rebuild, fuse, release) and a trade with a stand-in partner. The server side Mettle offers no
 * command for (exact cards, the forced roll, the stand-in) goes through {@link MettleServer}. Each step is photographed;
 * PERF lines give the UI thread's paints and the render thread's cost ({@code -Pexplore=true} adds layer experiments).
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
		// 0. First join: Mettle asks for a background and the hub's picker opens on Classes. Choose the Wanderer,
		// which has to place 4 points and favour 2 skills, through the page.
		this.context.waitFor(mc -> mc.gui.screen() instanceof HubInventoryScreen h && "classes".equals(h.segment()), 400);
		this.context.waitTicks(20);
		this.ui.parkMouse();
		this.context.waitTicks(3);
		this.shot("voidhub_mettle_00_picker");
		this.ui.clickPage("[data-bg=\"mettle:wanderer\"]");
		this.context.waitTicks(2);
		for (int i = 0; i < 4; i++) {
			this.ui.clickPage("[data-pm=\"strength:1\"]");
		}
		this.ui.clickPage("[data-fav=\"mettle:mining\"]");
		this.ui.clickPage("[data-fav=\"mettle:marksmanship\"]");
		this.context.waitTicks(2);
		this.ui.parkMouse();
		this.context.waitTicks(3);
		this.shot("voidhub_mettle_00b_picker_wanderer");
		this.ui.clickPage("[data-begin]");
		this.context.waitTicks(30);
		this.expect("chosen".equals(this.state().get("phase")), "the hub's picker began the character: " + this.state().get("phase"));
		this.expect(this.attribute("strength") == 7, "the Wanderer's 4 points went to Strength: " + this.attribute("strength"));
		server.runCommand("mettle xp mettle:mining 2600 Player0");
		server.runCommand("mettle xp mettle:marksmanship 5200 Player0");
		server.runCommand("mettle xp mettle:survival 3100 Player0");
		server.runCommand("mettle xp mettle:blades 900 Player0");
		server.runCommand("give Player0 mettle:leather_pack");
		server.runCommand("give Player0 mettle:belt");
		server.runCommand("give Player0 mettle:quiver");
		server.runCommand("give Player0 minecraft:arrow 64");
		this.context.waitTicks(10);
		this.context.runOnClient(mc -> mc.gui.setScreen(null));
		this.context.waitTicks(5);

		// Particles hidden: the player's own effect swirls drift in front of the camera as big flat-tinted squares (Speed's
		// cyan), which the screenshots' outside-the-window scan would rightly flag as not-the-world.
		server.runCommand("effect give Player0 minecraft:speed 160 1 true");
		server.runCommand("effect give Player0 minecraft:night_vision 300 0 true");
		server.runCommand("effect give Player0 minecraft:regeneration 12 0 true");

		// 1. The inventory page has Back, Belt and Quiver wells beside the figure.
		this.ui.pressInventory();
		this.context.waitForScreen(HubInventoryScreen.class);
		this.context.waitTicks(10);
		this.expect(this.ui.layout().cellForSlot(MettleLink.BACK) != null, "the inventory page shows the Back well");
		this.ui.parkMouse();
		this.context.waitTicks(3);
		this.shot("voidhub_mettle_01_inventory");

		// 1b. Hovering a stack shows the VOID tooltip beside its well (the stacks under it are left out).
		server.runCommand("give Player0 minecraft:diamond_sword[enchantments={sharpness:3,unbreaking:2,looting:1}]");
		this.context.waitTicks(5);
		int sword = this.find("diamond_sword", 9, 45);
		this.expect(sword >= 0, "the sword is in the inventory");
		this.ui.hoverSlot(sword);
		this.context.waitTicks(6);
		this.shot("voidhub_mettle_01b_tooltip");
		this.ui.parkMouse();
		this.context.waitTicks(3);

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
		this.shot("voidhub_mettle_02_gear_worn");

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
		this.shot("voidhub_mettle_03_gear_page");

		// 4. Classes. The XP above levelled skills, so drafts wait: the page deals the first hand at once (Figma D1).
		server.runCommand("mettle draft rerolls 3 Player0");
		server.runCommand("give Player0 minecraft:emerald 40");
		this.context.waitTicks(5);
		this.ui.clickPage("[data-seg=\"classes\"]");
		this.context.waitTicks(5);
		this.expect(this.ui.layout().kind == HubLayout.Kind.CLASSES, "the Classes segment switched the layout");
		this.dumpState("classes");
		this.waitPage("MC.ov.kind()==='draft' && !!document.querySelector('.dr-hand .hc')", 100, "a draft hand dealt on the Classes page");
		this.ui.parkMouse();
		this.context.waitTicks(12);
		this.shot("voidhub_mettle_04_draft_hand");
		this.dumpState("draft");
		// The copy matches the hand, the badge says it counts every skill, cards fit their content, values carry units.
		String copy = this.js("(function(){var n=document.querySelectorAll('.dr-hand .hc').length,e=document.querySelector('#dr-title .ov-eb').textContent;"
			+ "return n+'|'+e+'|'+(document.querySelector('#dr-badge')?document.querySelector('#dr-badge').textContent:'');})()");
		String[] cp = copy.split(java.util.regex.Pattern.quote("|"), -1);
		this.expect(cp[1].contains("3".equals(cp[0]) ? "THE OTHER TWO BURN" : "2".equals(cp[0]) ? "THE OTHER ONE BURNS" : "KEEP IT"), "the draft copy matches a hand of " + cp[0] + ": " + cp[1]);
		this.expect(cp[2].isEmpty() || cp[2].contains("across your skills"), "the badge says it counts every skill: " + cp[2]);
		String fit = this.js("(function(){var h=[],cs=document.querySelectorAll('.dr-hand .hc');for(var i=0;i<cs.length;i++)h.push(cs[i].offsetHeight);"
			+ "var bare=[],v=document.querySelectorAll('.hc-tr .v');for(var j=0;j<v.length;j++)if(/^[+-]?[0-9.]+$/.test(v[j].textContent.trim()))bare.push(v[j].textContent);"
			+ "return h.join(',')+'|'+bare.join(',');})()");
		String[] fp = fit.split(java.util.regex.Pattern.quote("|"), -1);
		this.expect(java.util.Arrays.stream(fp[0].split(",")).allMatch("328"::equals), "every hand card keeps the design's 248 x 328: " + fp[0]);
		String lay = this.js("(function(){var o=[],cs=document.querySelectorAll('.dr-hand .hc');for(var i=0;i<cs.length;i++){var a=cs[i].querySelector('.hc-art'),"
			+ "t=cs[i].querySelector('.hc-flow'),b=cs[i].querySelector('.hc-bot');o.push((t.offsetTop-a.offsetTop-a.offsetHeight)+':'+(b.offsetTop+b.offsetHeight)+':'"
			+ "+(t.offsetTop+t.scrollHeight<=b.offsetTop+1));}return o.join(',');})()");
		for (String c : lay.split(",")) {
			String[] v = c.split(":");
			this.expect("8".equals(v[0]) && Integer.parseInt(v[1]) <= 300 && "true".equals(v[2]), "a card's name sits under its art and its traits on the bottom safe area: " + lay);
		}
		this.expect(fp[1].isEmpty(), "every trait value shows a unit: " + fp[1]);
		this.perf("draft hand idle", () -> this.context.waitTicks(60));

		// 5. Hover: the card lifts onto its hot frame, the others cool down (D3).
		int hand = Integer.parseInt(this.js("String(document.querySelectorAll('.dr-hand .hc').length)"));
		this.perf("draft hover sweep", () -> {
			for (int k = 0; k < 3; k++) {
				for (int c = 0; c < hand; c++) {
					this.hoverPage(".dr-hand .hc[data-hand=\"" + c + "\"] .hc-art");
					this.context.waitTicks(4);
				}
			}
		});
		this.hoverPage(".dr-hand .hc[data-hand=\"1\"] .hc-art");
		this.context.waitTicks(8);
		this.expect("1".equals(this.js("String(MC.ov.hovered())")), "hovering the middle card made it the hovered one");
		this.expect("true".equals(this.js("String(MC.fxDebug().indexOf('\"k\":\"lift\"') >= 0 && MC.fxDebug().indexOf('\"k\":\"edge-hot\"') >= 0)")),
			"the hovered card lifts onto its hot frame natively: " + this.js("String(MC.fxDebug()).slice(0, 300)"));
		this.shot("voidhub_mettle_05_draft_hover");

		// 6. Pick with the 2 key: the kept card flares, the other two burn away, the next draft deals (D4).
		String skillId = this.js("String(MC.ov && document.querySelector('#dr-chip .nm') ? document.querySelector('#dr-chip .nm').textContent : '')");
		int binderBefore = this.binderCount();
		long waitingBefore = this.waiting();
		this.perf("draft pick", () -> {
			this.context.getInput().pressKey(InputConstants.KEY_2);
			this.context.waitTicks(3);
			this.shot("voidhub_mettle_06a_draft_pick_flare");
			this.context.waitTicks(5);
			this.shot("voidhub_mettle_06b_draft_pick_burn");
			this.context.waitTicks(6);
			this.shot("voidhub_mettle_06c_draft_pick_ash");
			this.context.waitTicks(26);
		});
		this.expect(this.binderCount() == binderBefore + 1, "the pick kept one card (" + skillId + "): binder " + binderBefore + " -> " + this.binderCount());
		this.expect(this.waiting() == waitingBefore - 1, "the pick used one draft: " + waitingBefore + " -> " + this.waiting());
		this.waitPage("MC.ov.kind()==='draft' && !document.querySelector('.dr-hand .hc.kept') && !!document.querySelector('.dr-hand .hc')", 80, "the next draft dealt after the pick");
		this.ui.parkMouse();
		this.context.waitTicks(10);
		this.shot("voidhub_mettle_07_draft_next");

		// 7. Redeal all spends a reroll and deals a fresh hand.
		long rerolls = this.rerolls();
		this.ui.clickPage("[data-redeal]");
		this.context.waitTicks(30);
		this.expect(this.rerolls() == rerolls - 1, "Redeal all spent a reroll: " + rerolls + " -> " + this.rerolls());
		this.ui.parkMouse();
		this.context.waitTicks(6);
		this.shot("voidhub_mettle_08_draft_redealt");

		// 8. Esc keeps the draft for later: the window stays open on the gallery.
		long keep = this.waiting();
		this.context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		this.context.waitTicks(5);
		this.expect(this.context.computeOnClient(mc -> mc.gui.screen() instanceof HubInventoryScreen), "Esc closed the draft, not the window");
		this.expect("false".equals(this.js("String(MC.ov.active())")), "Esc closed the draft overlay");
		this.expect(this.waiting() == keep, "Esc kept the drafts waiting: " + keep + " -> " + this.waiting());
		this.ui.parkMouse();
		this.context.waitTicks(5);
		this.shot("voidhub_mettle_09_gallery");
		this.perf("gallery idle", () -> this.context.waitTicks(40));
		this.perf("gallery hover sweep", () -> {
			for (int k = 0; k < 2; k++) {
				for (String sk : List.of("mining", "blades", "heavy_weapons", "marksmanship", "defense", "athletics", "survival", "smithing")) {
					this.hoverPage(".sk-wrap [data-skill=\"mettle:" + sk + "\"]");
					this.context.waitTicks(3);
				}
			}
		});

		// 9. A Mythic dealt in a draft (the 0.5 % roll forced): the room darkens, the card cracks, flips into a burst (M2, M3).
		server.runCommand("mettle xp mettle:blades 20000 Player0");
		this.context.waitTicks(5);
		this.world.getServer().runOnServer(s -> {
			var p = s.getPlayerList().getPlayers().getFirst();
			double was = MettleServer.mythicChance(1.0);
			try {
				MettleServer.freshDrafts(p, "blades", 1);
			} finally {
				MettleServer.mythicChance(was);
			}
		});
		this.context.waitTicks(15);
		if (!"true".equals(this.js("String(MC.ov.active())"))) {
			this.ui.clickPage("[data-deal=\"mettle:blades\"]");
		}
		this.waitPage("!!document.getElementById('rv-stage')", 40, "a Mythic in the Blades hand turns over");
		this.ui.parkMouse();
		this.perf("mythic reveal", () -> {
			this.context.waitTicks(26);
			this.shot("voidhub_mettle_10_mythic_cracks");
			this.context.waitTicks(25);
			this.shot("voidhub_mettle_11_mythic_burst");
			this.context.waitTicks(30);
		});
		this.waitPage("!!document.querySelector('[data-claim]')", 60, "the Mythic settled with Claim");
		this.shot("voidhub_mettle_12_mythic_settled");
		this.perf("mythic settled idle", () -> this.context.waitTicks(40));
		// Claim Mythic: Mettle's claimMythic keeps it without using the draft, so the hand stays open for a keep.
		int mythicsBefore = this.mythicsOwned();
		long draftsBeforeClaim = this.waiting();
		this.ui.clickPage("[data-claim]");
		this.context.waitTicks(6);
		this.shot("voidhub_mettle_12b_mythic_claimed");
		this.waitPage("MC.ov.kind()==='draft' && !document.getElementById('rv-stage') && !!document.querySelector('.dr-hand .hc:not(.faded)')", 100,
			"after the claim the hand stays open with the other cards");
		this.expect(this.mythicsOwned() == mythicsBefore + 1, "Claim kept the Mythic: " + mythicsBefore + " -> " + this.mythicsOwned());
		this.expect(this.waiting() == draftsBeforeClaim, "the claim did not use the draft: " + draftsBeforeClaim + " -> " + this.waiting());
		this.expect("-1".equals(this.js("String((MC.M.skills.filter(function(s){return s.id==='mettle:blades';})[0]||{}).mythicOffer)")),
			"the Mythic left the hand (mythicOffer -1)");
		this.expect(this.js("document.querySelector('#dr-title .ov-eb').textContent").contains("MYTHIC CLAIMED"), "after the claim the title says so: "
			+ this.js("document.querySelector('#dr-title').textContent"));
		this.expect("visible".equals(this.js("(function(){var f=document.getElementById('dr-fr');return f&&f.textContent&&getComputedStyle(f).display!=='none'?'visible':'hidden';})()")),
			"the draft's right footer shows after the claim");
		this.ui.parkMouse();
		this.context.waitTicks(10);
		this.shot("voidhub_mettle_12c_mythic_then_keep");
		int binderBeforeKeep = this.binderCount();
		this.context.getInput().pressKey(InputConstants.KEY_1);
		this.context.waitTicks(40);
		this.expect(this.binderCount() == binderBeforeKeep + 1, "the keep after the claim kept a card: " + binderBeforeKeep + " -> " + this.binderCount());
		this.expect(this.waiting() == draftsBeforeClaim - 1, "the keep used the draft: " + draftsBeforeClaim + " -> " + this.waiting());
		if ("true".equals(this.js("String(MC.ov.active())"))) {
			this.context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			this.context.waitTicks(3);
		}

		// 10. A world first earned outside a draft: the reveal on its own (cards.reveal), then Continue.
		server.runCommand("mettle cards mythic grant mettle:wardens_hush Player0");
		this.waitPage("MC.ov.kind()==='reveal'", 60, "a Mythic earned outside a draft is revealed");
		this.waitPage("!!document.querySelector('[data-close-ov]')", 120, "the reveal settled");
		this.ui.parkMouse();
		this.context.waitTicks(4);
		this.shot("voidhub_mettle_13_reveal_world_first");
		this.ui.clickPage("[data-close-ov]");
		this.context.waitTicks(3);

		// 11. The Hall of Firsts and the binder.
		this.ui.clickPage("[data-tab=\"hall\"]");
		this.context.waitTicks(4);
		this.ui.parkMouse();
		this.context.waitTicks(3);
		this.shot("voidhub_mettle_14_hall_of_firsts");
		this.world.getServer().runOnServer(s -> {
			var p = s.getPlayerList().getPlayers().getFirst();
			String[][] more = {{"mining/prospector", "COMMON"}, {"mining/quarryman", "RARE"}, {"mining/hard_hat", "EPIC"}, {"mining/rock_hauler", "COMMON"},
				{"mining/tunneler", "RARE"}, {"mining/prospector", "RARE"}, {"mining/quarryman", "COMMON"}, {"mining/hard_hat", "COMMON"}};
			for (String[] m : more) {
				MettleServer.giveExact(p, m[0], m[1], List.of("hoard"), List.of("mining/lucky=4"), 2);
			}
		});
		this.context.waitTicks(15);
		this.ui.clickPage("[data-tab=\"cards\"]");
		this.context.waitTicks(4);
		this.ui.clickPage("#bg-0 [data-bcard]");
		this.context.waitTicks(4);
		this.ui.parkMouse();
		this.context.waitTicks(3);
		this.shot("voidhub_mettle_15_binder");
		this.perf("binder scroll and hover", () -> {
			for (int k = 0; k < 3; k++) {
				this.hoverPage("#bg-5");
				this.context.getInput().scroll(-1);
				this.context.waitTicks(4);
				for (int t = 0; t < 4; t++) {
					this.hoverPage("#bg-" + (t * 3));
					this.context.waitTicks(3);
				}
				this.hoverPage("#bg-5");
				this.context.getInput().scroll(1);
				this.context.waitTicks(4);
			}
		});

		// 12. The deck builder: drag a binder card into an open slot; a second Deep card completes the Deep 2 set (2g-6).
		long[] deep = new long[2];
		this.world.getServer().runOnServer(s -> {
			var p = s.getPlayerList().getPlayers().getFirst();
			deep[0] = MettleServer.giveExact(p, "mining/tunneler", "RARE", List.of("deep", "echo"), List.of("mining/deepborn=9", "mining/echoing=8"), 2);
			deep[1] = MettleServer.giveExact(p, "mining/hard_hat", "RARE", List.of("deep", "iron"), List.of("mining/stonehide=15"), 2);
		});
		this.context.waitTicks(3);
		server.runCommand("execute as Player0 run mettle cards slot " + deep[0]);
		this.context.waitTicks(15);
		this.ui.clickPage("[data-tab=\"skills\"]");
		this.context.waitTicks(3);
		this.ui.clickPage(".sk-wrap [data-skill=\"mettle:mining\"]");
		this.context.waitTicks(6);
		this.ui.parkMouse();
		this.context.waitTicks(4);
		this.expect(this.slotted(deep[0]), "the first Deep card is slotted");
		this.shot("voidhub_mettle_16_deck");
		this.dumpState("deck");
		String from = "[data-dcard=\"" + deep[1] + "\"]";
		this.waitPage("!!document.querySelector('" + from.replace("\"", "\\\"") + "')", 20, "the second Deep card is in the deck's binder");
		this.perf("deck builder drag", () -> {
			double[] a = this.pageCentre(from);
			double[] b = this.pageCentre(".dk-row .slot.empty");
			this.ui.moveTo(a[0], a[1]);
			this.context.waitTicks(2);
			this.ui.press(Ui.LEFT, 0);
			for (int i = 1; i <= 12; i++) {
				this.ui.moveTo(a[0] + (b[0] - a[0]) * i / 12.0, a[1] + (b[1] - a[1]) * i / 12.0);
				this.context.waitTicks(2);
				if (i == 8) {
					this.shot("voidhub_mettle_17_deck_drag");
				}
			}
			this.ui.release(Ui.LEFT, 0);
			this.context.waitTicks(4);
		});
		this.ui.settle();
		this.context.waitTicks(6);
		this.expect(this.slotted(deep[1]), "dragging the card onto an open slot slotted it");
		this.waitPage("MC.ov.kind()==='set'", 40, "two Deep cards completed the Deep 2 set: the moment plays");
		this.context.waitTicks(6);
		this.shot("voidhub_mettle_18_set_complete");
		this.ui.clickPage("#c-ov");
		this.context.waitTicks(3);
		this.ui.clickPage("[data-scard=\"" + deep[0] + "\"]");
		this.context.waitTicks(3);
		this.ui.parkMouse();
		this.context.waitTicks(3);
		this.shot("voidhub_mettle_19_deck_take_out");
		this.ui.clickPage("[data-popclose]");
		this.ui.clickPage("[data-stab=\"tree\"]");
		this.context.waitTicks(4);
		this.ui.parkMouse();
		this.context.waitTicks(3);
		this.shot("voidhub_mettle_20_tree");
		this.ui.clickPage("[data-stab=\"deck\"]");
		this.context.waitTicks(3);

		// 13. A Card Shrine: used as a player would, the hub shows the sitting; rebuild one card, fuse three, release.
		this.world.getServer().runOnServer(s -> {
			var p = s.getPlayerList().getPlayers().getFirst();
			MettleServer.freeSwaps(p, 0);
			ItemStack draught = MettleServer.draught("mining");
			if (!p.getInventory().add(draught)) {
				p.getInventory().setItem(35, draught);
			}
			for (int i = 0; i < 3; i++) {
				MettleServer.giveExact(p, "mining/prospector", "COMMON", List.of("hoard"), List.of("mining/lucky=" + (3 + i)), 2);
			}
			BlockPos at = p.blockPosition().relative(p.getDirection(), 2);
			s.overworld().setBlockAndUpdate(at, BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("mettle", "card_shrine")).defaultBlockState());
			s.overworld().getBlockState(at).useWithoutItem(s.overworld(), p, new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
		});
		this.waitPage("document.getElementById('cv').className.indexOf('scr-shrine')>=0", 40, "the Card Shrine sitting shows on the Classes page");
		this.ui.clickPage("[data-rbskill=\"mettle:mining\"]");
		this.context.waitTicks(3);
		this.ui.clickPage("[data-rbpick=\"" + deep[0] + "\"]");
		this.context.waitTicks(3);
		this.ui.parkMouse();
		this.context.waitTicks(3);
		this.shot("voidhub_mettle_21_shrine_rebuild");
		this.ui.clickPage("[data-rebuild]");
		this.context.waitTicks(20);
		this.expect(!this.slotted(deep[0]), "the shrine rebuild took the Tunneler out");
		this.ui.clickPage("[data-shtab=\"fuse\"]");
		this.context.waitTicks(3);
		for (int i = 0; i < 3; i++) {
			this.ui.clickPage("#fu-r" + i + " [data-fupick]");
			this.context.waitTicks(2);
		}
		this.ui.parkMouse();
		this.context.waitTicks(3);
		this.shot("voidhub_mettle_22_shrine_fuse");
		int cardsBefore = this.binderCount();
		this.ui.clickPage("[data-fuse]");
		this.context.waitTicks(20);
		this.expect(this.binderCount() == cardsBefore - 2, "fusing three cards left one: " + cardsBefore + " -> " + this.binderCount());
		this.ui.clickPage("[data-shtab=\"release\"]");
		this.context.waitTicks(3);
		for (int i = 0; i < 3; i++) {
			this.ui.clickPage("#rl-r" + i + " [data-rlpick]");
			this.context.waitTicks(2);
		}
		this.ui.parkMouse();
		this.context.waitTicks(3);
		this.shot("voidhub_mettle_23_shrine_release");
		this.ui.clickPage("[data-closesit]");
		this.context.waitTicks(10);
		this.expect(this.js("document.getElementById('cv').className").indexOf("scr-shrine") < 0, "Leave ended the sitting");

		// 14. A trade with a stand-in partner (Bree): both sides, lock, the countdown, the swap (2h-4).
		long[] theirs = new long[1];
		this.context.waitTicks(105);  // nobody trades within 5 s of being hurt
		this.world.getServer().runOnServer(s -> {
			var a = s.getPlayerList().getPlayers().getFirst();
			var b = standIn(s);
			b.snapTo(a.getX() + 2, a.getY(), a.getZ(), 0, 0);
			b.setHealth(b.getMaxHealth());
			a.setHealth(a.getMaxHealth());
			MettleServer.trade("standIn", b);
			MettleServer.clearCards(b);
			theirs[0] = MettleServer.giveExact(b, "mining/quarryman", "EPIC", List.of("brutal", "deep"), List.of("mining/deepborn=8"), 9);
			Object asked = MettleServer.trade("request", a, b);
			Object accepted = MettleServer.trade("accept", b);
			this.expect(asked.toString().equals("Optional.empty") && accepted.toString().equals("Optional.empty"), "the trade opened: " + asked + " / " + accepted);
			MettleServer.trade("offerCard", b, theirs[0]);
		});
		this.waitPage("document.getElementById('cv').className.indexOf('scr-trade')>=0 && !document.getElementById('tr-ask')", 60, "the trade shows on the Classes page");
		this.ui.clickPage("[data-tadd=\"1\"]");
		this.context.waitTicks(3);
		this.ui.parkMouse();
		this.context.waitTicks(2);
		this.shot("voidhub_mettle_24_trade_add");
		this.ui.clickPage(".tr-pick [data-toffer]");
		this.context.waitTicks(4);
		this.ui.clickPage("[data-tadd=\"0\"]");
		this.context.waitTicks(3);
		this.ui.clickPage("[data-tlock]");
		this.context.waitTicks(4);
		this.world.getServer().runOnServer(s -> MettleServer.trade("lock", standIn(s)));
		this.context.waitTicks(5);
		this.ui.parkMouse();
		this.context.waitTicks(2);
		this.shot("voidhub_mettle_25_trade_locked");
		this.dumpState("trade");
		this.ui.clickPage("[data-tconfirm]");
		this.context.waitTicks(22);
		this.shot("voidhub_mettle_26_trade_countdown");
		this.context.waitTicks(45);
		// The countdown runs in Mettle's server tick for real players; a stand-in's trade is finished the way Mettle's own
		// TradeJourney does it, once the countdown is over.
		this.world.getServer().runOnServer(s -> MettleServer.trade("finishNow", s, s.getPlayerList().getPlayers().getFirst()));
		this.context.waitTicks(15);
		this.expect(this.ownsCard(theirs[0]), "the swap brought Bree's card into the binder");
		this.expect(this.js("document.getElementById('cv').className").indexOf("scr-trade") < 0, "the trade screen closed after the swap");

		// 15. K opens the hub on Classes.
		this.context.runOnClient(mc -> mc.gui.setScreen(null));
		this.context.waitTicks(5);
		this.context.getInput().pressKey(InputConstants.KEY_K);
		this.context.waitForScreen(HubInventoryScreen.class);
		this.context.waitTicks(10);
		this.expect("classes".equals(this.context.computeOnClient(mc -> ((HubInventoryScreen) mc.gui.screen()).segment())), "K opened the hub on Classes");
		if ("true".equals(this.js("String(MC.ov.active())"))) {
			this.context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			this.context.waitTicks(3);
		}
		// Vanilla toasts wait while the hub is open (they would cover the close island) and show once it closes.
		this.context.runOnClient(mc -> net.minecraft.client.gui.components.toasts.SystemToast.add(mc.gui.toastManager(),
			net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.NARRATOR_TOGGLE, net.minecraft.network.chat.Component.literal("VOID Hub test"),
			net.minecraft.network.chat.Component.literal("A toast held while the hub was open")));
		this.context.waitTicks(20);
		this.shot("voidhub_mettle_27_toast_held");
		this.context.runOnClient(mc -> mc.gui.setScreen(null));
		this.context.waitTicks(20);
		this.shot("voidhub_mettle_28_toast_after_close");
		this.expect(this.flatRuns.isEmpty(), "no flat untextured colour outside the window in any screenshot: " + this.flatRuns);
		dev.voidmc.hub.VoidHub.LOGGER.info("VOID Hub + Mettle: all steps passed");
	}

	private static net.minecraft.server.level.ServerPlayer standIn(net.minecraft.server.MinecraftServer s) {
		return net.fabricmc.fabric.api.entity.FakePlayer.get(s.overworld(), new com.mojang.authlib.GameProfile(BREE, "Bree"));
	}

	private static final java.util.UUID BREE = java.util.UUID.randomUUID();

	/** Writes Mettle's state message next to the perf reports (page work outside the game uses it). */
	private void dumpState(String name) {
		String json = this.context.computeOnClient(mc -> MettleLink.state());
		try {
			java.nio.file.Path dir = java.nio.file.Path.of(System.getProperty("voidhub.test.out", "build/perf"));
			java.nio.file.Files.createDirectories(dir);
			java.nio.file.Files.writeString(dir.resolve("mettle-state-" + name + ".json"), json == null ? "null" : json);
		} catch (java.io.IOException e) {
			dev.voidmc.hub.VoidHub.LOGGER.warn("could not write the state dump", e);
		}
	}

	/** Runs script in the page and returns its string result. */
	/** Flat-colour runs found outside the window, per screenshot (see shot). */
	private final java.util.List<String> flatRuns = new java.util.ArrayList<>();

	/**
	 * Takes a screenshot and scans the world around the window for long runs of one flat colour (a texture drawn
	 * where none belongs; the world and the held item are textured). Logs what the player holds, for context.
	 */
	private void shot(String name) {
		java.nio.file.Path p = this.context.takeScreenshot(name);
		String held = this.context.computeOnClient(mc -> mc.player == null ? "-" : "slot " + mc.player.getInventory().getSelectedSlot() + " "
			+ net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()) + " / "
			+ net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mc.player.getOffhandItem().getItem()));
		HubLayout l = this.ui.layout();
		try {
			java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(p.toFile());
			java.util.List<dev.voidmc.hub.layout.Rect> keep = new java.util.ArrayList<>();
			if (l != null) {
				for (dev.voidmc.hub.layout.Rect r : new dev.voidmc.hub.layout.Rect[] {l.window, l.mark, l.pages, l.close, l.strip}) {
					if (r != null) {
						keep.add(r);
					}
				}
			}
			// A 48 px square of one exact, saturated colour outside the window is not the world: blocks are textured
			// and smooth-lit, stone, dirt and wood are greys and browns, foliage is dark. (Effect particles in front of
			// the camera were exactly that: Speed's cyan swirls, flat-tinted, ~70 px squares.)
			if (keep.isEmpty()) {
				return;  // not a hub screen (the picker, the world after closing): nothing to compare the outside with
			}
			int runs = 0;
			java.util.Map<Integer, Integer> colours = new java.util.TreeMap<>();
			int B = 48;
			for (int y = 0; y + B <= img.getHeight(); y += 24) {
				for (int x = 0; x + B <= img.getWidth(); x += 24) {
					if (overlaps(keep, x, y, B)) {
						continue;
					}
					int c = img.getRGB(x, y) & 0xFFFFFF;
					boolean flat = true;
					for (int dy = 0; dy < B && flat; dy += 4) {
						for (int dx = 0; dx < B && flat; dx += 4) {
							flat = (img.getRGB(x + dx, y + dy) & 0xFFFFFF) == c;
						}
					}
					float[] hsv = java.awt.Color.RGBtoHSB(c >> 16 & 0xFF, c >> 8 & 0xFF, c & 0xFF, null);
					if (flat && hsv[1] >= 0.55F && hsv[2] >= 0.5F) {
						runs++;
						colours.merge(c, 1, Integer::sum);
					}
				}
			}
			StringBuilder top = new StringBuilder();
			colours.entrySet().stream().sorted((x1, x2) -> x2.getValue() - x1.getValue()).limit(4)
				.forEach(e -> top.append(String.format("#%06X x%d ", e.getKey(), e.getValue())));
			dev.voidmc.hub.VoidHub.LOGGER.info("SCAN " + name + " held " + held + " flat squares " + runs + " " + top);
			if (runs > 0) {
				this.flatRuns.add(name + " (" + runs + ": " + top.toString().trim() + ")");
			}
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	/** A B-px square touching the window or an island (screenshot pixels are the layout's physical pixels). */
	private static boolean overlaps(java.util.List<dev.voidmc.hub.layout.Rect> rs, int x, int y, int b) {
		for (dev.voidmc.hub.layout.Rect r : rs) {
			if (x + b > r.x() - 6 && x < r.right() + 6 && y + b > r.y() - 6 && y < r.y() + r.h() + 6) {
				return true;
			}
		}
		return false;
	}

	private String js(String expr) {
		return this.context.computeOnClient(mc -> dev.voidmc.hub.ui.HubView.get().eval(expr));
	}

	/** Waits (ticks) until a page expression is true. */
	private void waitPage(String cond, int ticks, String what) {
		for (int i = 0; i < ticks; i++) {
			if ("true".equals(this.js("String(!!(" + cond + "))"))) {
				this.expect(true, what);
				return;
			}
			this.context.waitTicks(1);
		}
		this.shot("voidhub_mettle_FAILED");
		throw new AssertionError("VOID Hub + Mettle: " + what + " (page: " + this.js("String(document.getElementById('cv') && document.getElementById('cv').className) + ' err=' + window.__hubError + ' ' + MC.debug()") + ")");
	}

	private double[] pageCentre(String selector) {
		String json = this.js("(function(){var e=document.querySelector(" + Json.quote(selector) + ");if(!e)return '';var r=e.getBoundingClientRect();return JSON.stringify({x:r.left+r.width/2,y:r.top+r.height/2});})()");
		this.expect(json != null && !json.isEmpty(), "the page has " + selector);
		Map<String, Object> p = Json.parseObject(json);
		double u = this.ui.layout().u;
		return new double[] {(Double) p.get("x") * u, (Double) p.get("y") * u};
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> cards() {
		return (Map<String, Object>) this.state().get("cards");
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> binder() {
		return (List<Map<String, Object>>) ((Map<String, Object>) this.cards().get("binder")).get("cards");
	}

	private int binderCount() {
		return this.binder().size();
	}

	private int mythicsOwned() {
		int n = 0;
		for (Map<String, Object> c : this.binder()) {
			if ("mythic".equals(c.get("kind")) || "mythic".equals(c.get("rarity"))) {
				n++;
			}
		}
		return n;
	}

	private boolean ownsCard(long id) {
		for (Map<String, Object> c : this.binder()) {
			if (String.valueOf(id).equals(c.get("id"))) {
				return true;
			}
		}
		return false;
	}

	private boolean slotted(long id) {
		for (Map<String, Object> c : this.binder()) {
			if (String.valueOf(id).equals(c.get("id"))) {
				return "slotted".equals(c.get("place"));
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	private long waiting() {
		return ((Number) ((Map<String, Object>) this.state().get("draft")).get("waiting")).longValue();
	}

	@SuppressWarnings("unchecked")
	private long rerolls() {
		return ((Number) ((Map<String, Object>) this.state().get("draft")).get("rerolls")).longValue();
	}

	/** Runs {@code actions} and logs the frames' UI-thread paint and render-thread cost (p50 / p95 / max, ms). */
	private void perf(String what, Runnable actions) {
		long from = this.context.computeOnClient(mc -> dev.voidmc.hub.ui.HubStats.INSTANCE.frameCount());
		actions.run();
		dev.voidmc.hub.ui.HubStats.Frame[] frames = this.context.computeOnClient(mc -> dev.voidmc.hub.ui.HubStats.INSTANCE.since(from));
		long[] ui = new long[frames.length];
		long[] rt = new long[frames.length];
		long bytes = 0;
		int painted = 0;
		for (int i = 0; i < frames.length; i++) {
			ui[i] = frames[i].uiNs();
			rt[i] = frames[i].totalNs();
			bytes += frames[i].bytes;
			if (frames[i].bytes > 0) {
				painted++;
			}
		}
		StringBuilder paints = new StringBuilder();
		for (int i = 0; i < frames.length; i++) {
			if (frames[i].bytes > 0) {
				paints.append(String.format(java.util.Locale.ROOT, " %.1f", ui[i] / 1e6));
			}
		}
		dev.voidmc.hub.VoidHub.LOGGER.info("PERF " + what + " painted ui ms:" + paints);
		java.util.Arrays.sort(ui);
		java.util.Arrays.sort(rt);
		dev.voidmc.hub.VoidHub.LOGGER.info(String.format(java.util.Locale.ROOT,
			"PERF %-20s frames %4d painted %4d  ui p50 %6.2f p95 %6.2f max %6.2f ms  render p50 %5.2f p95 %5.2f max %5.2f ms  upload %6.1f KB/frame",
			what, frames.length, painted, pct(ui, 50), pct(ui, 95), pct(ui, 100), pct(rt, 50), pct(rt, 95), pct(rt, 100), bytes / 1024.0 / Math.max(1, frames.length)));
	}

	private static double pct(long[] sorted, int p) {
		if (sorted.length == 0) {
			return 0;
		}
		return sorted[Math.min(sorted.length - 1, (int) Math.ceil(p / 100.0 * sorted.length) - 1 < 0 ? 0 : (int) Math.ceil(p / 100.0 * sorted.length) - 1)] / 1e6;
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
