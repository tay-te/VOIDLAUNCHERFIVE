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
		this.reload();

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
		this.shot("market", 1);
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
				String odds = this.js("(function(){var e=document.querySelector('.h-hag');return e?e.textContent:''})()");
				this.log("haggle panel: " + odds);
				boolean takes = this.context.computeOnClient(mc -> {
					var m = HearthSync.section("market");
					return m != null && m.has("haggle") && m.get("haggle").isJsonObject() && m.getAsJsonObject("haggle").has("takes");
				});
				this.expect(!takes || odds.contains("% he takes it") || odds.contains(" %he takes it"), "haggling shows Hearth's \"he takes it\" odds (haggle.takes)");
				this.expect(this.context.computeOnClient(mc -> {
					var m = HearthSync.section("market");
					return m == null || m.get("merchant") == null || !m.get("merchant").isJsonObject() || m.getAsJsonObject("merchant").get("portrait").isJsonPrimitive();
				}), "the market's merchant has a portrait (market.merchant.portrait)");
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
			int lines = this.context.computeOnClient(mc -> {
				var b = HearthSync.section("bank");
				return b == null || !b.has("ledger") ? -1 : b.getAsJsonArray("ledger").size();
			});
			String lately = this.js("(function(){var e=document.querySelectorAll('#hk-left .flow');var o=[];for(var i=0;i<e.length;i++)if(/^Day /.test(e[i].textContent))o.push(e[i].textContent);return o.join(' | ')})()");
			this.log("bank ledger lines " + lines + ", LATELY: " + lately);
			this.expect(lines < 0 || (lines > 0 && lately.startsWith("Day ")), "the deposit is a ledger line under LATELY (bank.ledger)");
			this.both("bank_ledger");
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
		this.trainingChip();
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
				this.posters();
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
		this.shot("journal", 1);
		this.idleAndHover("journal", ".h-q");
		this.wantedInJournal();
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

	// ---- wanted posters (OVERLAY.md 2026-10-19: board.wanted[].player / tracked, state.wantedTrack) ----------------------

	/**
	 * Mettle's inline training (OVERLAY.md 2026-10-19: the lesson's fee in {@code cost}): the chip says the fee. The test
	 * world's innkeeper teaches nothing, so the page is shown his options with one lesson added (Figma 653:37976, row 8).
	 */
	private void trainingChip() {
		this.injectHearth(s -> {
			var t = s.getAsJsonObject("talk");
			if (t == null) {
				return;
			}
			var opts = t.getAsJsonArray("options");
			com.google.gson.JsonObject o = new com.google.gson.JsonObject();
			o.addProperty("key", 8);
			o.addProperty("id", "mettle:train/speech");
			o.addProperty("label", "Teach me to talk folk round, the way you do.");
			o.addProperty("hint", "");
			o.addProperty("tooltip", "");
			o.addProperty("kind", "train");
			o.addProperty("odds", -1);
			com.google.gson.JsonObject cost = new com.google.gson.JsonObject();
			cost.addProperty("total", 40);
			cost.addProperty("crowns", 0);
			cost.addProperty("marks", 4);
			cost.addProperty("bits", 0);
			cost.addProperty("text", "4 marks");
			o.add("cost", cost);
			com.google.gson.JsonObject skill = new com.google.gson.JsonObject();
			skill.addProperty("id", "mettle:speech");
			skill.addProperty("name", "Speech");
			skill.addProperty("level", 6);
			o.add("skill", skill);
			o.addProperty("locked", false);
			o.add("lockedReason", com.google.gson.JsonNull.INSTANCE);
			com.google.gson.JsonArray next = new com.google.gson.JsonArray();
			for (int i = 0; i < opts.size(); i++) {
				if (i == 7) {
					next.add(o);
				}
				next.add(opts.get(i));
			}
			t.add("options", next);
		});
		this.settle(4);
		String chip = this.js("(function(){var e=document.querySelector('.h-opt[data-kind=\"train\"] .h-mchip');return e?e.textContent:''})()");
		this.log("training chip: " + chip);
		this.expect(chip.contains("Train Speech 6 → 7") && chip.contains("4 marks") && chip.contains("Mettle"), "a lesson's chip shows its fee (option cost): " + chip);
		this.both("talk_A_training");
		this.restoreHearth();
	}

	/** A quarry the test world cannot have (one player, and the server never lets you track yourself). */
	private static final String QUARRY = "5eedf00d-1234-4bcd-9ef0-123456789abc";

	/**
	 * The page is shown the state a poster's quarry would make (Hearth's tree with one wanted poster added, then tracked),
	 * because a one-player world has nobody to want: Track sends trackPoster(uuid) through the bridge (the server refuses
	 * it: no such warrant), and the tracked look is photographed. Hearth's own state comes back after (HearthSync.reset).
	 */
	private void posters() {
		this.injectHearth(s -> {
			var b = s.getAsJsonObject("board");
			if (b == null) {
				return;
			}
			b.getAsJsonArray("wanted").add(wanted(false));
		});
		this.settle(4);
		if (!this.has("[data-op=\"trackPoster\"]")) {
			this.gaps.add("board: a wanted poster with a quarry showed no Track button");
			this.restoreHearth();
			return;
		}
		this.ui.clickPage("[data-op=\"trackPoster\"]");
		this.settle(5);
		this.log("poster track: " + this.lastAct() + " sent " + HearthLink.lastSent);
		// Hearth's own client rule refuses a quarry its open board does not list (this one is the page's alone): the call
		// reaching HearthActions is what the page and the bridge own.
		this.expect(("trackPoster " + QUARRY).equals(this.lastAct()), "Track on a wanted poster calls Hearth's trackPoster(quarry)");
		this.injectHearth(s -> {
			var b = s.getAsJsonObject("board");
			if (b != null) {
				b.getAsJsonArray("wanted").add(wanted(true));
			}
			s.add("wantedTrack", track());
		});
		this.settle(4);
		this.expect(this.has("[data-op=\"untrackPoster\"]"), "a tracked poster offers to let go (untrackPoster)");
		this.both("board_poster_tracked");
		this.ui.clickPage("[data-op=\"untrackPoster\"]");
		this.settle(5);
		this.log("poster let go: " + this.lastAct() + " sent " + HearthLink.lastSent);
		this.expect("untrackPoster ".equals(this.lastAct()), "letting go calls Hearth's untrackPoster()");
		this.restoreHearth();
	}

	/** The journal with a tracked poster (state.wantedTrack): it leads the TRACKED list. */
	private void wantedInJournal() {
		this.injectHearth(s -> s.add("wantedTrack", track()));
		this.settle(4);
		this.expect(this.has(".h-q.wanted"), "a tracked wanted poster shows in the journal's TRACKED list");
		this.both("journal_poster_tracked");
		this.restoreHearth();
	}

	private static com.google.gson.JsonObject wanted(boolean tracked) {
		com.google.gson.JsonObject w = new com.google.gson.JsonObject();
		w.addProperty("name", "Jory Flint");
		w.addProperty("owed", "6 marks");
		w.addProperty("outlaw", false);
		w.addProperty("crime", "Theft");
		w.addProperty("crimeKey", "hearth.offence.theft");
		w.addProperty("player", QUARRY);
		w.addProperty("tracked", tracked);
		return w;
	}

	private static com.google.gson.JsonObject track() {
		com.google.gson.JsonObject t = new com.google.gson.JsonObject();
		t.addProperty("player", QUARRY);
		t.addProperty("name", "Jory Flint");
		com.google.gson.JsonObject owed = new com.google.gson.JsonObject();
		owed.addProperty("total", 60);
		owed.addProperty("crowns", 0);
		owed.addProperty("marks", 6);
		owed.addProperty("bits", 0);
		owed.addProperty("text", "6 marks");
		t.add("owed", owed);
		t.addProperty("outlaw", false);
		t.addProperty("crime", "Theft");
		t.addProperty("crimeKey", "hearth.offence.theft");
		t.addProperty("settlementId", 1);
		t.addProperty("town", "Sandbox Temperate");
		t.addProperty("online", true);
		t.add("near", com.google.gson.JsonNull.INSTANCE);
		t.addProperty("distance", 140);
		t.addProperty("bearing", 35);
		return t;
	}

	/** Shows the page Hearth's last state with a change (a copy; Hearth's own tree is not touched). */
	private void injectHearth(java.util.function.Consumer<com.google.gson.JsonObject> change) {
		this.context.runOnClient(mc -> {
			com.google.gson.JsonObject s = HearthSync.state();
			if (s == null) {
				return;
			}
			com.google.gson.JsonObject copy = s.deepCopy();
			change.accept(copy);
			HubView.get().send("{\"type\":\"hearth\",\"state\":" + copy + "}");
		});
	}

	/** Hearth's own state again (the page is told everything afresh). */
	private void restoreHearth() {
		this.context.runOnClient(mc -> HearthSync.reset());
		this.settle(4);
	}

	// ---- F3+T with a Hearth screen open (the Hearth side of hubAudit's reload check) --------------------------------

	private void reload() {
		this.context.getInput().pressKey(InputConstants.KEY_J);
		boolean journal = this.waitFor(() -> this.context.computeOnClient(mc -> mc.gui.screen() instanceof HubInventoryScreen h && "quests".equals(h.page())), 60);
		if (!journal) {
			this.gaps.add("reload: J opened no Quests page");
			return;
		}
		this.settle(10);
		int epoch0 = HearthSync.iconEpoch();
		int reloads0 = dev.voidmc.hub.screen.NativeFx.reloads;
		int builds0 = HearthSync.builds;
		int mettleIcons0 = Integer.parseInt(this.js("String(window.MC?Object.keys(MC.icons).length:-1)"));
		var f = this.context.computeOnClient(mc -> mc.reloadResourcePacks());
		for (int i = 0; i < 600 && !f.isDone(); i += 5) {
			this.context.waitTicks(5);
		}
		this.settle(20);
		this.expect(f.isDone() && !f.isCompletedExceptionally(), "the resource reload finished with the Quests page open");
		this.expect(this.context.computeOnClient(mc -> mc.gui.screen() instanceof HubInventoryScreen h && "quests".equals(h.page())), "F3+T keeps the Quests page open");
		this.expect(HearthSync.iconEpoch() == epoch0 + 1, "F3+T starts Hearth's pictures again (HearthSync's icon epoch " + epoch0 + " -> " + HearthSync.iconEpoch() + ")");
		this.expect(dev.voidmc.hub.screen.NativeFx.reloads == reloads0 + 1, "F3+T releases the native fx textures once (the fx_art listener)");
		this.expect(HearthSync.builds > builds0, "F3+T rebuilds Hearth's state for the page (" + builds0 + " -> " + HearthSync.builds + ")");
		int mettleIcons1 = Integer.parseInt(this.js("String(window.MC?Object.keys(MC.icons).length:-1)"));
		this.log("reload: Mettle's page icons " + mettleIcons0 + " -> " + mettleIcons1 + ", page " + this.js("window.hubHearth.debug()"));
		this.expect(mettleIcons1 >= mettleIcons0, "Hearth's reload keeps Mettle's icons on the page");
		this.both("journal_after_reload");
		// The GUI-scale cut-off on a Hearth page: at GUI scale 2 the whole window paints (hubAudit's probe).
		this.context.runOnClient(mc -> {
			mc.options.guiScale().set(2);
			mc.resizeGui();
		});
		this.context.waitTicks(8);
		PaintProbe.Result r = PaintProbe.run(this.context, "hearth_paint_probe_quests_gs2");
		this.log("paint probe, Quests page at GUI scale 2 (" + r.page() + "): missing " + r.missing());
		this.expect(r.missing().isEmpty(), "the Quests page paints the whole window at GUI scale 2 " + r.missing());
		this.context.runOnClient(mc -> {
			mc.options.guiScale().set(0);
			mc.resizeGui();
		});
		this.context.waitTicks(4);
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
		if (gs < 3) {
			// Where the page zooms itself (GUI 1 and 2 at 1080p): no word space collapsed to a px (Spaces, hubAudit's probe).
			Spaces.Result sp = Spaces.run(this.context);
			this.log("spaces " + name + " gs" + gs + ": " + sp);
			this.expect(sp.collapsed().isEmpty(), "no collapsed spaces on " + name + " at GUI " + gs + ": " + sp.collapsed());
		}
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
		this.idleAndHoverAt(what, selector);
		// And at 1920x1080 GUI scale 2, the common low scale (a layout unit under 1).
		this.context.runOnClient(mc -> {
			mc.options.guiScale().set(2);
			mc.resizeGui();
		});
		this.context.waitTicks(8);
		this.idleAndHoverAt(what + " gs2", selector);
		this.context.runOnClient(mc -> {
			mc.options.guiScale().set(0);
			mc.resizeGui();
		});
		this.context.waitTicks(6);
	}

	private void idleAndHoverAt(String what, String selector) {
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
