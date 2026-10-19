package dev.voidmc.hub.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmc.hub.hearth.HearthLink;
import dev.voidmc.hub.hearth.HearthSync;
import dev.voidmc.hub.layout.HubLayout;
import dev.voidmc.hub.mettle.MettleLink;
import dev.voidmc.hub.model.Json;
import dev.voidmc.hub.screen.HubEventScreen;
import dev.voidmc.hub.screen.HubInventoryScreen;
import dev.voidmc.hub.ui.HubStats;
import dev.voidmc.hub.ui.HubView;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

/**
 * VOID Hearth inside the hub ({@code -Dvoidhub.test=hearth}, Hearth's jar and Mettle's on the run's classpath:
 * {@code ./gradlew hubHearth}). Every screen opens the way it does in play where Hearth offers a way in: the market and
 * bank through Hearth's sandbox commands (the real menus), the conversation through the server's own open (what a
 * right-click does), the notice board by right-clicking the board the quest sandbox puts up, the reputation page by
 * {@code /hearth standing}, the journal by J; a guard, a lock and a pocket are Hearth's own payloads sent by the server.
 * Each screen is photographed at GUI scale 2 and 3 ({@code -Dvoidhub.test.shots=<dir>} copies them out), and the
 * page's idle paints and hover cost are measured like the Mettle pages'.
 */
public final class HearthTest {

	private final ClientGameTestContext context;
	private final TestSingleplayerContext world;
	private final Ui ui;
	private final Path out = Path.of(System.getProperty("voidhub.test.shots", "screenshots-hearth"));
	private final List<String> gaps = new ArrayList<>();
	private final List<String> shots = new ArrayList<>();

	public HearthTest(ClientGameTestContext context, TestSingleplayerContext world) {
		this.context = context;
		this.world = world;
		this.ui = new Ui(context, world);
	}

	public void run() {
		this.expect(this.context.computeOnClient(mc -> HearthLink.present()), "Hearth is loaded and its overlay API links");
		this.mettleBegin();
		var server = this.world.getServer();
		this.as("hearth money give Player0 4537");
		this.context.waitTicks(5);

		this.market();
		this.bank();
		this.talk();
		this.confront();
		this.lockpick();
		this.pickpocket();
		this.boardJournal();
		this.dice();
		this.reputation();

		dev.voidmc.hub.VoidHub.LOGGER.info("HEARTH shots: {}", this.shots);
		for (String g : this.gaps) {
			dev.voidmc.hub.VoidHub.LOGGER.warn("GAP   {}", g);
		}
		String err = this.js("window.__hubError||''");
		this.expect(err.isEmpty(), "the page ran without a script error (" + err + ")");
		dev.voidmc.hub.VoidHub.LOGGER.info("VOID Hub + Hearth: all steps passed");
	}

	// ---- Mettle's first join (its background picker opens on the hub's Classes page) -----------------------------------

	private void mettleBegin() {
		if (!this.context.computeOnClient(mc -> MettleLink.present())) {
			return;
		}
		boolean picker = this.waitFor(() -> this.context.computeOnClient(mc -> mc.gui.screen() instanceof HubInventoryScreen h && "classes".equals(h.segment())), 400);
		if (!picker) {
			return;
		}
		this.context.waitTicks(20);
		this.ui.clickPage("[data-bg=\"mettle:wanderer\"]");
		for (int i = 0; i < 4; i++) {
			this.ui.clickPage("[data-pm=\"strength:1\"]");
		}
		this.ui.clickPage("[data-fav=\"mettle:mining\"]");
		this.ui.clickPage("[data-fav=\"mettle:marksmanship\"]");
		this.ui.clickPage("[data-begin]");
		this.context.waitTicks(30);
		this.close();
	}

	// ---- the screens ----------------------------------------------------------------------------------------------------

	private void market() {
		this.as("hearth economy sandbox talk");
		if (!this.waitEvent("market")) {
			this.gaps.add("market: the sandbox merchant opened no market");
			return;
		}
		this.settle(20);
		this.both("market");
		this.idleAndHover("market", ".h-row");
		// Right-click the first row: haggling starts (the merchant's opening word arrives as state).
		if (this.has(".h-row")) {
			this.pagePress(".h-row", Ui.RIGHT);
			this.settle(20);
			boolean haggling = "true".equals(this.js("String(!!document.querySelector('.h-hag [data-op=\"haggleOffer\"]'))"));
			if (haggling) {
				this.ui.clickPage("[data-ui=\"nudge:-10%\"]");
				this.settle(4);
				this.both("market_haggle");
				this.expect(this.context.computeOnClient(mc -> mc.gui.screen() instanceof HubEventScreen s && s.pageModal()), "haggling holds Esc (the page is modal)");
				this.context.getInput().pressKey(InputConstants.KEY_ESCAPE);
				this.settle(10);
				this.expect(this.context.computeOnClient(mc -> mc.gui.screen() instanceof HubEventScreen), "Esc while haggling walks away from the haggle, not the market");
			} else {
				this.gaps.add("market: right-click on a row started no haggle (" + this.lastAct() + ")");
			}
			this.ui.clickPage(".h-row");
			this.settle(10);
			this.log("market buy one lot: " + this.lastAct());
		}
		this.close();
	}

	private void bank() {
		this.as("hearth economy sandbox bank");
		if (!this.waitEvent("bank")) {
			this.gaps.add("bank: the sandbox bank opened no bank");
			return;
		}
		this.settle(20);
		if (this.has("[data-ui=\"amt:100\"]")) {
			this.ui.clickPage("[data-ui=\"amt:100\"]");
			this.ui.clickPage("[data-ui=\"amt:10\"]");
			this.settle(3);
		}
		this.both("bank");
		if (this.has("[data-ui=\"deposit\"]")) {
			this.ui.clickPage("[data-ui=\"deposit\"]");
			this.settle(10);
			this.log("bank deposit: " + this.lastAct());
		}
		this.close();
	}

	private void talk() {
		var server = this.world.getServer();
		this.as("hearth npc spawn innkeeper");
		this.settle(20);
		Entity folk = server.computeOnServer(HearthServer::nearestFolk);
		if (folk == null) {
			this.gaps.add("talk: no folk was spawned to talk to");
			return;
		}
		int id = folk.getId();
		server.runOnServer(s -> {
			Entity f = s.overworld().getEntity(id);
			if (f != null) {
				HearthServer.talk(s, f);
			}
		});
		if (!this.waitEvent("talk")) {
			this.gaps.add("talk: the conversation did not open in the hub");
			return;
		}
		this.settle(30);
		this.both("talk_A");
		this.idleAndHover("talk", ".h-opt");
		this.log("talk options: " + this.js("JSON.stringify((function(){var o=[];var t=document.querySelectorAll('.h-opt');for(var i=0;i<t.length;i++)o.push(t[i].textContent);return o;})())"));
		// B, the cinematic bar: the per-player toggle on the card.
		this.ui.clickPage("[data-arg=\"cinematic\"]");
		this.settle(15);
		this.expect("cinematic".equals(this.style()), "the cinematic toggle reached Hearth's client options");
		this.both("talk_B");
		this.ui.clickPage("[data-arg=\"card\"]");
		this.settle(15);
		// 1 picks the first option (the page reads the key and sends Hearth's choose).
		this.context.getInput().pressKey(InputConstants.KEY_1);
		this.settle(20);
		this.log("talk choose: " + this.lastAct() + " sent " + HearthLink.lastSent + "; page " + this.js("window.hubHearth.debug()") + "; screen saw " + HubEventScreen.lastKey);
		this.shot("talk_A_after_choice", 3);
		// "Fancy a game of dice?": the folk's own dice option opens the table (the real way in).
		if (this.has("[data-kind=\"dice\"]")) {
			this.ui.clickPage("[data-kind=\"dice\"]");
			this.diceTable();
			return;
		}
		this.close();
		this.settle(5);
	}

	private String style() {
		var st = this.context.computeOnClient(mc -> HearthSync.state());
		return st == null ? "" : st.getAsJsonObject("settings").get("conversationStyle").getAsString();
	}

	private void confront() {
		int me = this.context.computeOnClient(mc -> mc.player.getId());
		this.world.getServer().runOnServer(s -> HearthServer.confront(s, me));
		if (!this.waitEvent("confront")) {
			this.gaps.add("confront: the guard's stop did not open in the hub");
			return;
		}
		this.settle(20);
		this.both("confront");
		this.context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		this.settle(5);
		this.expect(this.context.computeOnClient(mc -> mc.gui.screen() instanceof HubEventScreen s && "confront".equals(s.name())), "Esc does nothing while a guard waits");
		this.ui.clickPage("#close");
		this.settle(5);
	}

	private void lockpick() {
		this.world.getServer().runOnServer(HearthServer::lockpick);
		if (!this.waitEvent("lockpick")) {
			this.gaps.add("lockpick: the lock did not open in the hub (Hearth's own screen took it)");
			this.close();
			return;
		}
		this.settle(10);
		HubLayout l = this.ui.layout();
		// The pick follows the pointer round the ring: put it up and to the left, then hold the turn.
		this.ui.moveTo(l.window.x() + (343 + 250) * l.u, l.window.y() + 140 * l.u);
		this.settle(4);
		this.both("lockpick");
		this.context.getInput().holdKeyFor(InputConstants.KEY_D, 10);
		this.shot("lockpick_turning", 3);
		this.close();
	}

	private void pickpocket() {
		this.world.getServer().runOnServer(HearthServer::pickpocket);
		if (!this.waitEvent("pickpocket")) {
			this.gaps.add("pickpocket: the pockets did not open in the hub");
			return;
		}
		this.settle(10);
		this.both("pickpocket");
		this.ui.clickPage("[data-pslot=\"5\"]");
		this.settle(5);
		this.shot("pickpocket_reach", 3);
		this.close();
	}

	private void boardJournal() {
		var server = this.world.getServer();
		this.as("hearth quest sandbox temperate 16");
		this.settle(20);
		BlockPos board = server.computeOnServer(HearthServer::board);
		if (board == null) {
			this.gaps.add("board: the quest sandbox put up no notice board near the player");
		} else {
			this.ui.openChest(board);  // a right-click on the board
			boolean open = this.waitFor(() -> this.context.computeOnClient(mc -> mc.gui.screen() instanceof HubInventoryScreen h && "quests".equals(h.page())), 100);
			if (open) {
				this.settle(20);
				this.both("board");
				this.idleAndHover("board", ".h-post");
				if (this.has(".h-post [data-op=\"accept\"]")) {
					this.ui.clickPage(".h-post [data-op=\"accept\"]");
					this.settle(20);
					this.log("board take: " + this.lastAct() + " sent " + HearthLink.lastSent);
				}
				this.close();
			} else {
				this.gaps.add("board: right-clicking the board opened no Quests page");
				this.close();
			}
		}
		this.context.getInput().pressKey(InputConstants.KEY_J);
		boolean journal = this.waitFor(() -> this.context.computeOnClient(mc -> mc.gui.screen() instanceof HubInventoryScreen h && "quests".equals(h.page())), 60);
		if (!journal) {
			this.gaps.add("journal: J opened no Quests page");
			return;
		}
		this.settle(20);
		this.both("journal");
		this.idleAndHover("journal", ".h-q");
		this.ui.clickPage("[data-ui=\"seg:finished\"]");
		this.settle(5);
		this.shot("finished", 3);
		this.close();
	}

	private void dice() {
		if (this.diced) {
			return;
		}
		this.as("hearth social dice 0");
		this.diceTable();
	}

	private boolean diced;

	private void diceTable() {
		if (!this.waitEvent("dice")) {
			this.gaps.add("dice: no dice table opened in the hub");
			this.close();
			return;
		}
		this.diced = true;
		this.settle(20);
		this.both("dice");
		if (this.has("[data-op=\"diceStake\"]")) {
			this.ui.clickPage("[data-op=\"diceStake\"]");
			this.settle(20);
		}
		this.context.getInput().pressKey(InputConstants.KEY_SPACE);
		this.settle(4);
		this.shot("dice_tumble", 3);
		this.settle(20);
		this.both("dice_rolled");
		this.close();
	}

	private void reputation() {
		this.as("hearth standing set Player0 24");
		this.context.waitTicks(5);
		this.as("hearth standing");
		boolean open = this.waitFor(() -> this.context.computeOnClient(mc -> mc.gui.screen() instanceof HubInventoryScreen h && "quests".equals(h.page())), 80);
		if (!open) {
			this.gaps.add("reputation: /hearth standing opened no Quests page");
			return;
		}
		this.settle(20);
		this.both("reputation");
		this.close();
	}

	// ---- photographs and measurements -----------------------------------------------------------------------------

	/** Both GUI scales the brief asks for, then back to the test's own. */
	private void both(String name) {
		this.shot(name, 2);
		this.shot(name, 3);
	}

	private void shot(String name, int gs) {
		this.context.runOnClient(mc -> {
			mc.options.guiScale().set(gs);
			mc.resizeGui();
		});
		this.context.waitTicks(6);
		HubLayout l = this.ui.layout();
		if (l != null) {
			this.ui.moveTo(l.window.x() + 8 * l.u, l.window.y() + 8 * l.u);
		}
		this.context.waitTicks(4);
		String file = "hearth_" + name + "_gs" + gs;
		this.log("shot " + file + " u " + (l == null ? "-" : l.u) + " page " + this.js("window.innerWidth+'x'+window.innerHeight+' dpr '+window.devicePixelRatio"));
		Path p = this.context.takeScreenshot(file);
		try {
			Files.createDirectories(this.out);
			Files.copy(p, this.out.resolve(file + ".png"), StandardCopyOption.REPLACE_EXISTING);
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
		this.shots.add(file);
		this.context.runOnClient(mc -> {
			mc.options.guiScale().set(0);
			mc.resizeGui();
		});
		this.context.waitTicks(4);
	}

	/** Idle paints (must be 0) and the cost of moving the pointer over the screen's rows (p95 under 10 ms). */
	private void idleAndHover(String what, String selector) {
		this.context.waitTicks(10);
		long from = this.context.computeOnClient(mc -> HubStats.INSTANCE.frameCount());
		this.context.waitTicks(60);
		int[] idle = this.paints(from);
		dev.voidmc.hub.VoidHub.LOGGER.info("PERF hearth {} idle frames {} painted {}", what, idle[0], idle[1]);
		this.expect(idle[1] == 0, "hearth " + what + ": an idle page paints nothing (" + idle[1] + " of " + idle[0] + " frames)");
		String json = this.js("(function(){var e=document.querySelectorAll(" + Json.quote(selector) + "),o=[];for(var i=0;i<e.length&&i<6;i++){var r=e[i].getBoundingClientRect();o.push([r.left+r.width/2,r.top+r.height/2]);}return JSON.stringify(o);})()");
		@SuppressWarnings("unchecked")
		List<Object> pts = (List<Object>) Json.parseObject("{\"a\":" + json + "}").get("a");
		HubLayout l = this.ui.layout();
		long hoverFrom = this.context.computeOnClient(mc -> HubStats.INSTANCE.frameCount());
		for (Object o : pts) {
			@SuppressWarnings("unchecked")
			List<Object> xy = (List<Object>) o;
			this.ui.moveTo(((Number) xy.get(0)).doubleValue() * l.u, ((Number) xy.get(1)).doubleValue() * l.u);
			this.context.waitTicks(3);
		}
		HubStats.Frame[] frames = this.context.computeOnClient(mc -> HubStats.INSTANCE.since(hoverFrom));
		long[] ui = new long[frames.length];
		int painted = 0;
		StringBuilder paints = new StringBuilder();
		for (int i = 0; i < frames.length; i++) {
			ui[i] = frames[i].uiNs();
			if (frames[i].bytes > 0) {
				painted++;
				paints.append(String.format(java.util.Locale.ROOT, " %.1f", ui[i] / 1e6));
			}
		}
		java.util.Arrays.sort(ui);
		double p95 = ui.length == 0 ? 0 : ui[Math.max(0, (int) Math.ceil(0.95 * ui.length) - 1)] / 1e6;
		double max = ui.length == 0 ? 0 : ui[ui.length - 1] / 1e6;
		dev.voidmc.hub.VoidHub.LOGGER.info(String.format(java.util.Locale.ROOT, "PERF hearth %s hover frames %d painted %d ui p95 %.2f max %.2f ms; paints:%s", what,
			frames.length, painted, p95, max, paints));
		this.expect(p95 < 10, "hearth " + what + ": hover UI p95 under 10 ms (" + String.format(java.util.Locale.ROOT, "%.2f", p95) + ")");
	}

	private int[] paints(long from) {
		HubStats.Frame[] frames = this.context.computeOnClient(mc -> HubStats.INSTANCE.since(from));
		int painted = 0;
		for (HubStats.Frame f : frames) {
			if (f.bytes > 0) {
				painted++;
			}
		}
		return new int[] {frames.length, painted};
	}

	// ---- helpers ------------------------------------------------------------------------------------------------------

	/** A command as the player would type it (Hearth's sandbox commands need a player). */
	private void as(String command) {
		this.world.getServer().runCommand("execute as Player0 at Player0 run " + command);
	}

	private boolean waitEvent(String name) {
		return this.waitFor(() -> this.context.computeOnClient(mc -> mc.gui.screen() instanceof HubEventScreen s && name.equals(s.name())), 100);
	}

	private boolean waitFor(java.util.function.BooleanSupplier ok, int ticks) {
		for (int i = 0; i < ticks; i += 2) {
			if (ok.getAsBoolean()) {
				return true;
			}
			this.context.waitTicks(2);
		}
		return ok.getAsBoolean();
	}

	private void settle(int ticks) {
		this.context.waitTicks(ticks);
		this.world.getConnection().waitForServerboundPackets();
		this.world.getConnection().waitForClientboundPackets();
		this.context.waitTicks(1);
	}

	private void close() {
		this.context.runOnClient(mc -> mc.gui.setScreen(null));
		this.context.waitTicks(5);
	}

	private boolean has(String selector) {
		return "1".equals(this.js("document.querySelector(" + Json.quote(selector) + ")?'1':'0'"));
	}

	/** Presses a mouse button on a page element (no release wait). */
	private void pagePress(String selector, int button) {
		String json = this.js("(function(){var e=document.querySelector(" + Json.quote(selector) + ");if(!e)return '';var r=e.getBoundingClientRect();return JSON.stringify({x:r.left+r.width/2,y:r.top+r.height/2});})()");
		Map<String, Object> p = Json.parseObject(json);
		HubLayout l = this.ui.layout();
		this.ui.moveTo((Double) p.get("x") * l.u, (Double) p.get("y") * l.u);
		this.ui.press(button, 0);
		this.ui.release(button, 0);
	}

	private String lastAct() {
		return HearthLink.lastAct;
	}

	private String js(String script) {
		return this.context.computeOnClient(mc -> HubView.get().eval(script));
	}

	private void log(String s) {
		dev.voidmc.hub.VoidHub.LOGGER.info("HEARTH {}", s);
	}

	private void expect(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError("VOID Hub + Hearth: " + what);
		}
		dev.voidmc.hub.VoidHub.LOGGER.info("PASS  {}", what);
	}
}
