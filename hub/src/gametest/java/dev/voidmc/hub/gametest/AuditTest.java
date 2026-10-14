package dev.voidmc.hub.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmc.hub.VoidHub;
import dev.voidmc.hub.layout.HubLayout;
import dev.voidmc.hub.layout.Rect;
import dev.voidmc.hub.mettle.MettleLink;
import dev.voidmc.hub.model.Bridge;
import dev.voidmc.hub.model.Json;
import dev.voidmc.hub.screen.HubInventoryScreen;
import dev.voidmc.hub.ui.HubStats;
import dev.voidmc.hub.ui.HubView;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Production-readiness audit of the Mettle card UI in the hub ({@code -Dvoidhub.test=audit}, {@code ./gradlew hubAudit}).
 * Every finding is logged as {@code AUDIT PASS|FAIL|NOTE|PERF ...} and the run goes on, so one game run covers resolutions
 * and GUI scales, extreme state, input edge cases and lifecycle; at the end any FAIL fails the run (the task exits 1).
 */
public final class AuditTest {

	private final ClientGameTestContext context;
	private final TestSingleplayerContext world;
	private final Ui ui;
	private final List<String> fails = new ArrayList<>();

	public AuditTest(ClientGameTestContext context, TestSingleplayerContext world) {
		this.context = context;
		this.world = world;
		this.ui = new Ui(context, world);
	}

	private static void log(String s) {
		VoidHub.LOGGER.info("AUDIT " + s);
	}

	private void check(boolean ok, String what) {
		if (ok) {
			log("PASS " + what);
		} else {
			log("FAIL " + what);
			this.fails.add(what);
		}
	}

	private void section(String name, Runnable r) {
		log("== " + name);
		try {
			r.run();
		} catch (Throwable t) {
			log("ERROR in " + name + ": " + t);
			VoidHub.LOGGER.error("AUDIT error in " + name, t);
			this.fails.add("ERROR " + name + ": " + t);
			try {
				this.context.takeScreenshot("audit_ERR_" + name.replaceAll("[^A-Za-z0-9]+", "_"));
			} catch (Throwable ignored) {
				// nothing more to learn
			}
		}
	}

	// ---- plumbing ------------------------------------------------------------------------------------------------

	private String js(String expr) {
		return this.context.computeOnClient(mc -> HubView.get().eval(expr));
	}

	private boolean waitPage(String cond, int ticks) {
		for (int i = 0; i < ticks; i++) {
			if ("true".equals(this.js("String(!!(" + cond + "))"))) {
				return true;
			}
			this.context.waitTicks(1);
		}
		return false;
	}

	private HubInventoryScreen hub() {
		return this.context.computeOnClient(mc -> mc.gui.screen() instanceof HubInventoryScreen h ? h : null);
	}

	private void openClasses() {
		this.context.runOnClient(mc -> mc.gui.setScreen(new HubInventoryScreen(mc.player, "classes")));
		this.context.waitTicks(10);
	}

	private void closeHub() {
		this.context.runOnClient(mc -> mc.gui.setScreen(null));
		this.context.waitTicks(3);
	}

	private void dismiss() {
		this.js("(function(){if(MC.ov.active())MC.ov.close(true);if(MC.st.menu){MC.st.menu=null;}MC.st.deck.pop=null;MC.textFocus=false;MC.render();return ''})()");
		this.context.waitTicks(2);
	}

	private static Object field(Object o, String name) {
		Class<?> c = o.getClass();
		while (c != null) {
			try {
				Field f = c.getDeclaredField(name);
				f.setAccessible(true);
				return f.get(o);
			} catch (NoSuchFieldException e) {
				c = c.getSuperclass();
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}
		throw new IllegalStateException("no field " + name + " on " + o.getClass());
	}

	@SuppressWarnings("unchecked")
	private List<Bridge.FxItem> javaFx(HubInventoryScreen s) {
		return this.context.computeOnClient(mc -> new ArrayList<>((List<Bridge.FxItem>) field(field(s, "fx"), "items")));
	}

	private boolean javaModal() {
		HubInventoryScreen s = this.hub();
		return s != null && this.context.computeOnClient(mc -> (Boolean) field(s, "pageModal"));
	}

	private boolean javaText() {
		HubInventoryScreen s = this.hub();
		return s != null && this.context.computeOnClient(mc -> (Boolean) field(s, "pageText"));
	}

	private String inbox() {
		return this.context.computeOnClient(mc -> {
			java.util.Queue<?> q = (java.util.Queue<?>) field(HubView.get(), "inbox");
			List<String> out = new ArrayList<>();
			for (Object m : q) {
				out.add(m.toString());
			}
			return q.size() + " " + out;
		});
	}

	private int textures() {
		return this.context.computeOnClient(mc -> ((Map<?, ?>) field(mc.getTextureManager(), "byPath")).size());
	}

	private long heapMb() {
		return this.context.computeOnClient(mc -> {
			System.gc();
			Runtime r = Runtime.getRuntime();
			return (r.totalMemory() - r.freeMemory()) / (1024 * 1024);
		});
	}

	private static long processMb() {
		java.lang.management.OperatingSystemMXBean os = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
		if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
			return sun.getCommittedVirtualMemorySize() / (1024 * 1024);
		}
		return -1;
	}

	private void shot(String name) {
		this.context.takeScreenshot(name);
	}

	private void hoverPage(String selector) {
		String json = this.js("(function(){var e=document.querySelector(" + Json.quote(selector) + ");if(!e)return '';var r=e.getBoundingClientRect();return JSON.stringify({x:r.left+r.width/2,y:r.top+r.height/2});})()");
		if (json == null || json.isEmpty()) {
			throw new AssertionError("page has no " + selector);
		}
		Map<String, Object> p = Json.parseObject(json);
		double u = this.ui.layout().u;
		this.ui.moveTo((Double) p.get("x") * u, (Double) p.get("y") * u);
	}

	/** Two press/release pairs in the same tick at a page element: a fast double click. */
	private void doubleClickPage(String selector) {
		this.hoverPage(selector);
		this.context.waitTicks(1);
		this.ui.press(Ui.LEFT, 0);
		this.ui.release(Ui.LEFT, 0);
		this.ui.press(Ui.LEFT, 0);
		this.ui.release(Ui.LEFT, 0);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> state() {
		return Json.parseObject(this.context.computeOnClient(mc -> MettleLink.state()));
	}

	@SuppressWarnings("unchecked")
	private long waiting() {
		return ((Number) ((Map<String, Object>) this.state().get("draft")).get("waiting")).longValue();
	}

	@SuppressWarnings("unchecked")
	private long rerolls() {
		return ((Number) ((Map<String, Object>) this.state().get("draft")).get("rerolls")).longValue();
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> binder() {
		Map<String, Object> cards = (Map<String, Object>) this.state().get("cards");
		return (List<Map<String, Object>>) ((Map<String, Object>) cards.get("binder")).get("cards");
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

	/** Makes sure a plain (no Mythic) draft hand is up and dealt; deals fresh Mining drafts when none wait. */
	private boolean ensureDraft() {
		if (this.waiting() == 0) {
			this.world.getServer().runOnServer(s -> MettleServer.freshDrafts(s.getPlayerList().getPlayers().getFirst(), "mining", 3));
			this.context.waitTicks(10);
		}
		if (!"draft".equals(this.js("String(MC.ov.kind())"))) {
			this.js("(function(){var l=MC.M.skills;for(var i=0;i<l.length;i++){var s=l[i];if(s.released&&s.drafts>0&&s.hand&&s.hand.length&&!(s.mythicOffer>=0)){MC.ov.draft(s.id);return 'ok'}}return 'none'})()");
		}
		boolean ok = this.waitPage("MC.ov.kind()==='draft' && document.querySelectorAll('.dr-hand .hc').length>0 && !document.getElementById('rv-stage')", 80);
		this.context.waitTicks(14);
		return ok;
	}

	/** Empties every skill's draft queue (a reopen of Classes deals what waits). */
	private void clearDrafts() {
		this.world.getServer().runOnServer(s -> {
			var p = s.getPlayerList().getPlayers().getFirst();
			for (String sk : List.of("mining", "marksmanship", "survival", "blades", "heavy_weapons", "defense", "athletics", "smithing")) {
				MettleServer.freshDrafts(p, sk, 0);
			}
		});
		this.context.waitTicks(10);
		this.dismiss();
	}

	/** A PERF line's numbers (ms): frames, painted frames, UI-thread p95 and max, render-thread p95 and max. */
	record Perf(int frames, int painted, double uiP95, double uiMax, double renderP95, double renderMax, double fps, double cpuPerPaint) {
		/**
		 * Whether the machine was busy (another game on it: the UI thread runs below the game's priority, so its wall-clock
		 * paint times measure the other game too). Then the CPU the UI thread spent per paint is what is gated.
		 */
		boolean contended() {
			return this.fps < 200;
		}

		/** UI-thread budget: p95 and worst paint (wall clock) on a quiet machine, the mean CPU per paint on a busy one. */
		boolean within(double p95, double max) {
			return this.contended() ? this.cpuPerPaint < p95 : this.uiP95 < p95 && this.uiMax < max;
		}
	}

	/** CPU time of the hub's UI thread ("VOID Hub UI"), ns; -1 when it cannot be read. */
	private static long uiCpuNs() {
		var mx = java.lang.management.ManagementFactory.getThreadMXBean();
		for (Thread t : Thread.getAllStackTraces().keySet()) {
			if ("VOID Hub UI".equals(t.getName())) {
				return mx.getThreadCpuTime(t.threadId());
			}
		}
		return -1;
	}

	private Perf perf(String what, Runnable actions) {
		long cpu0 = uiCpuNs();
		long wall0 = System.nanoTime();
		long from = this.context.computeOnClient(mc -> HubStats.INSTANCE.frameCount());
		actions.run();
		HubStats.Frame[] frames = this.context.computeOnClient(mc -> HubStats.INSTANCE.since(from));
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
		java.util.Arrays.sort(ui);
		java.util.Arrays.sort(rt);
		log(String.format(java.util.Locale.ROOT,
			"PERF %-26s frames %5d painted %4d  ui p50 %6.2f p95 %6.2f max %6.2f ms  render p50 %5.2f p95 %5.2f max %6.2f ms  upload %6.1f KB/frame",
			what, frames.length, painted, pct(ui, 50), pct(ui, 95), pct(ui, 100), pct(rt, 50), pct(rt, 95), pct(rt, 100), bytes / 1024.0 / Math.max(1, frames.length)));
		double seconds = (System.nanoTime() - wall0) / 1e9;
		long cpu = uiCpuNs() - cpu0;
		Perf perf = new Perf(frames.length, painted, pct(ui, 95), pct(ui, 100), pct(rt, 95), pct(rt, 100), frames.length / Math.max(1e-3, seconds),
			painted == 0 ? 0 : cpu / 1e6 / painted);
		log(String.format(java.util.Locale.ROOT, "PERF %-26s %.0f fps%s, UI thread CPU %.2f ms per paint", what, perf.fps(), perf.contended() ? " (machine busy: CPU per paint gated)" : "",
			perf.cpuPerPaint()));
		return perf;
	}

	private static double pct(long[] sorted, int p) {
		if (sorted.length == 0) {
			return 0;
		}
		int i = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
		return sorted[Math.max(0, Math.min(sorted.length - 1, i))] / 1e6;
	}

	// ---- page probes -------------------------------------------------------------------------------------------

	private static final String ALIGN_JS = "(function(){var o={cards:[],fx:[]};var cs=document.querySelectorAll('.dr-hand .hc');"
		+ "for(var i=0;i<cs.length;i++){var r=cs[i].getBoundingClientRect();o.cards.push({k:+cs[i].getAttribute('data-hand'),x:r.left,y:r.top,w:r.width,h:r.height});}"
		+ "var f=MC.fxDebug();o.fx=f?JSON.parse(f):[];var w=document.getElementById('win').getBoundingClientRect();o.win=[w.left,w.top,w.width,w.height];"
		+ "o.art=[];var as=document.querySelectorAll('.dr-hand .hc .hc-art .mc-img');for(var j=0;j<as.length;j++){var a=as[j].getBoundingClientRect();o.art.push([a.left,a.top,a.width,+as[j].getAttribute('data-tx')||0]);}"
		+ "o.font=99;var ds=document.querySelectorAll('.dr-hand .hc .hc-desc');for(var k=0;k<ds.length;k++)o.font=Math.min(o.font,parseFloat(getComputedStyle(ds[k]).fontSize));"
		+ "o.vw=window.innerWidth;o.vh=window.innerHeight;o.dpr=window.devicePixelRatio;return JSON.stringify(o);})()";

	private static final Pattern CARD_ID = Pattern.compile("^g\\d+c(\\d)");

	/** Page cards vs the native layers anchored on them (CSS and device px), and Java's set vs the page's. */
	@SuppressWarnings("unchecked")
	private void align(String tag) {
		HubInventoryScreen s = this.hub();
		if (s == null) {
			this.check(false, tag + ": no hub open for the alignment probe");
			return;
		}
		Map<String, Object> o = Json.parseObject(this.js(ALIGN_JS));
		double u = this.ui.layout().u;
		Map<Integer, double[]> cards = new java.util.HashMap<>();
		for (Object c : (List<Object>) o.get("cards")) {
			Map<String, Object> m = (Map<String, Object>) c;
			cards.put(((Number) m.get("k")).intValue(), new double[] {(Double) m.get("x"), (Double) m.get("y"), (Double) m.get("w"), (Double) m.get("h")});
		}
		double maxCss = 0;
		double maxDev = 0;
		int matched = 0;
		Set<String> pageIds = new TreeSet<>();
		for (Object f : (List<Object>) o.get("fx")) {
			Map<String, Object> m = (Map<String, Object>) f;
			String id = (String) m.get("i");
			pageIds.add(id);
			String k = (String) m.get("k");
			if (!(k.startsWith("edge-") || k.startsWith("embers") || k.equals("bfront") || k.equals("ash") || k.equals("lift") || k.equals("flare"))) {
				continue;
			}
			Matcher mm = CARD_ID.matcher(id);
			if (!mm.find()) {
				continue;
			}
			double[] r = cards.get(Integer.parseInt(mm.group(1)));
			if (r == null) {
				continue;
			}
			double x = m.get("x") instanceof Double d ? d : 0;
			double y = m.get("y") instanceof Double d ? d : 0;
			maxCss = Math.max(maxCss, Math.max(Math.abs(x - r[0]), Math.abs(y - r[1])));
			// Java draws at round(x*u); the page's card edge is at r*u device px.
			maxDev = Math.max(maxDev, Math.max(Math.abs(Math.round(x * u) - r[0] * u), Math.abs(Math.round(y * u) - r[1] * u)));
			matched++;
		}
		Set<String> javaIds = new TreeSet<>();
		for (Bridge.FxItem it : this.javaFx(s)) {
			javaIds.add(it.id());
		}
		log(String.format(java.util.Locale.ROOT, "ALIGN %s u=%.4f texel=%.3fpx dpr=%s win=%s cards=%d layers=%d maxCss=%.3f maxDev=%.3f", tag, u, 4 * u, o.get("dpr"),
			o.get("win"), cards.size(), matched, maxCss, maxDev));
		this.check(matched > 0 && maxDev <= 0.6, tag + ": CardFx layers sit on the page's cards within rounding, 0.6 device px (max " + String.format("%.2f", maxDev) + " px, "
			+ String.format("%.2f", maxCss) + " CSS px, " + matched + " layers)");
		// H9: the hand's 4 px texel grid lands on whole device px (u snapped to a quarter), so do the art's texels.
		boolean grid = u < 0.5 || Math.abs(4 * u - Math.rint(4 * u)) < 1e-6;
		double artPos = 0;
		double artTexel = 0;
		int arts = 0;
		for (Object a : (List<Object>) o.get("art")) {
			List<Object> v = (List<Object>) a;
			double ax = ((Number) v.get(0)).doubleValue() * u;
			double ay = ((Number) v.get(1)).doubleValue() * u;
			double aw = ((Number) v.get(2)).doubleValue() * u;
			double tx = ((Number) v.get(3)).doubleValue();
			artPos = Math.max(artPos, Math.max(Math.abs(ax - Math.rint(ax)), Math.abs(ay - Math.rint(ay))));
			if (tx > 0) {
				artTexel = Math.max(artTexel, Math.abs(aw / tx - Math.rint(aw / tx)));
				arts++;
			}
		}
		this.check(grid && arts > 0 && artPos < 0.05 && artTexel < 0.02, tag + ": card texels are whole device px (4u " + String.format("%.3f", 4 * u) + ", art origin off by "
			+ String.format("%.3f", artPos) + " px, texel off by " + String.format("%.3f", artTexel) + " px over " + arts + " arts)");
		// H12: the rules text is readable at any scale: at least 7 device px.
		double font = ((Number) o.get("font")).doubleValue();
		this.check(font * u >= 6.95, tag + ": rules text is at least 7 device px (" + String.format("%.2f", font) + " CSS px x u " + String.format("%.3f", u) + " = "
			+ String.format("%.2f", font * u) + ")");
		this.check(javaIds.equals(pageIds), tag + ": Java's CardFx holds the page's layer set (java " + javaIds.size() + ", page " + pageIds.size()
			+ (javaIds.equals(pageIds) ? "" : ", java-only " + diff(javaIds, pageIds) + ", page-only " + diff(pageIds, javaIds)) + ")");
	}

	private static String diff(Set<String> a, Set<String> b) {
		Set<String> d = new TreeSet<>(a);
		d.removeAll(b);
		return d.size() > 6 ? d.size() + " ids" : d.toString();
	}

	/** Elements of the Classes page drawn outside the window, and text clipped by its box. */
	private static final String OVERFLOW_JS = "(function(){var w=document.getElementById('win').getBoundingClientRect(),out=[],n=0,tx=[];"
		+ "var els=document.querySelectorAll('#classes *');for(var i=0;i<els.length;i++){var e=els[i],r=e.getBoundingClientRect();if(!r.width||!r.height)continue;"
		+ "var c=String(e.className&&e.className.baseVal!==undefined?e.className.baseVal:e.className||'');if(/dim|halo|ghost|glow/.test(c))continue;"
		+ "if(r.left<w.left-1||r.top<w.top-1||r.right>w.right+1||r.bottom>w.bottom+1){n++;if(out.length<6)out.push((e.id||c||e.tagName)+'@'+Math.round(r.left-w.left)+','+Math.round(r.top-w.top)+','+Math.round(r.right-w.left)+','+Math.round(r.bottom-w.top));}"
		+ "if(e.tagName!=='svg'&&e.children.length===0&&e.textContent&&(e.scrollWidth>e.clientWidth+1||e.scrollHeight>e.clientHeight+2)&&getComputedStyle(e).overflow!=='visible'){if(tx.length<10)tx.push((e.id||c)+':'+e.textContent.slice(0,28));}}"
		+ "return JSON.stringify({out:n,ex:out,clip:tx});})()";

	@SuppressWarnings("unchecked")
	private void overflow(String tag) {
		Map<String, Object> o = Json.parseObject(this.js(OVERFLOW_JS));
		int n = ((Number) o.get("out")).intValue();
		log("OVERFLOW " + tag + " outside-window=" + n + " " + o.get("ex") + " clipped-text=" + o.get("clip"));
		this.check(n == 0, tag + ": nothing on the Classes page is drawn outside the window (" + n + " " + o.get("ex") + ")");
	}

	// ---- the run -------------------------------------------------------------------------------------------------

	public void run() {
		this.check(this.context.computeOnClient(mc -> MettleLink.present()), "Mettle is loaded and its overlay API links");
		var server = this.world.getServer();

		// First join: the picker opens on Classes; begin as the Wanderer through the page.
		this.context.waitFor(mc -> mc.gui.screen() instanceof HubInventoryScreen h && "classes".equals(h.segment()), 400);
		this.context.waitTicks(20);
		this.ui.clickPage("[data-bg=\"mettle:wanderer\"]");
		this.context.waitTicks(2);
		for (int i = 0; i < 4; i++) {
			this.ui.clickPage("[data-pm=\"strength:1\"]");
		}
		this.ui.clickPage("[data-fav=\"mettle:mining\"]");
		this.ui.clickPage("[data-fav=\"mettle:marksmanship\"]");
		this.ui.clickPage("[data-begin]");
		this.context.waitTicks(30);
		this.check("chosen".equals(this.state().get("phase")), "the picker began the character");

		this.section("empty binder", () -> {
			this.dismiss();
			this.js("MC.nav.view='home';MC.nav.tab='cards';MC.render();''");
			this.context.waitTicks(4);
			this.ui.parkMouse();
			this.context.waitTicks(3);
			this.check(this.binder().isEmpty(), "the binder starts empty: " + this.binder().size());
			this.check("undefined".equals(this.js("String(window.__hubError)")), "no page error on the empty binder: " + this.js("String(window.__hubError)"));
			this.overflow("empty binder");
			this.shot("audit_01_empty_binder");
		});

		// Every skill levelled, 4 more drafts each: 30+ waiting.
		for (String sk : List.of("mining", "marksmanship", "survival", "blades", "heavy_weapons", "defense", "athletics", "smithing")) {
			server.runCommand("mettle xp mettle:" + sk + " 4000 Player0");
		}
		server.runCommand("mettle draft rerolls 5 Player0");
		this.context.waitTicks(10);
		this.world.getServer().runOnServer(s -> {
			var p = s.getPlayerList().getPlayers().getFirst();
			for (String sk : List.of("mining", "marksmanship", "survival", "blades", "heavy_weapons", "defense", "athletics", "smithing")) {
				MettleServer.freshDrafts(p, sk, 4);
			}
		});
		this.context.waitTicks(15);

		this.section("30+ drafts", () -> {
			long w = this.waiting();
			this.check(w >= 30, "30+ drafts wait: " + w);
			this.ensureDraft();
			this.ui.parkMouse();
			this.context.waitTicks(4);
			this.log2("badge " + this.js("(document.getElementById('dr-badge')||{}).textContent") + " / title " + this.js("(document.getElementById('dr-title')||{}).textContent"));
			this.shot("audit_02_draft_32_waiting");
		});

		this.section("resolution x GUI scale matrix", this::matrix);
		this.section("resize mid-burn", this::resizeMidBurn);
		this.section("GUI scale change with a draft open", this::scaleMidDraft);
		this.section("double actions", this::doubleActions);
		this.section("close mid-burn, reopen with K", this::closeMidBurn);
		this.section("segment switch mid-burn", this::segmentMidBurn);
		this.section("Mythics and close mid-reveal", this::mythics);
		this.section("search and number keys", this::searchKeys);
		this.section("Esc in nested popovers", this::escNested);
		this.section("keyboard: draft", this::keyboard);
		this.section("400-card binder", this::bigBinder);
		this.section("keyboard: binder and deck", this::keyboardBinder);
		this.section("longest names", this::longNames);
		this.section("other player starts a trade while a menu is open", this::incomingTrade);
		this.section("trade 9 cards + 9 stacks", this::bigTrade);
		this.section("players in reach", this::nearby);
		this.section("toasts held", this::toasts);
		this.section("resource reload", this::reload);
		this.section("without Mettle (or an incompatible one)", this::withoutMettle);
		this.section("rapid open/close", this::rapid);
		this.section("dimension change with the hub open", this::dimension);
		this.section("death with the hub open", this::death);

		log("SUMMARY " + this.fails.size() + " failing checks");
		for (String f : this.fails) {
			log("SUMMARY FAIL " + f);
		}
		if (!this.fails.isEmpty()) {
			throw new AssertionError("VOID Hub audit: " + this.fails.size() + " failing checks, first: " + this.fails.getFirst());
		}
	}

	private void log2(String s) {
		log("NOTE " + s);
	}

	// ---- 1. resolutions and scales ---------------------------------------------------------------------------------

	private void setWindow(int w, int h, int gs) {
		this.context.runOnClient(mc -> mc.options.guiScale().set(gs));
		this.context.getInput().resizeWindow(w, h);
		this.context.runOnClient(mc -> mc.resizeGui());
		this.context.waitTicks(8);
	}

	private String actual() {
		return this.context.computeOnClient(mc -> {
			var w = mc.getWindow();
			return w.getWidth() + "x" + w.getHeight() + " gs" + w.getGuiScale();
		});
	}

	private void matrix() {
		int[][] configs = {{854, 480, 0}, {854, 480, 1}, {1280, 720, 0}, {1280, 720, 2}, {1600, 900, 0}, {1920, 1080, 0}, {1920, 1080, 1}, {1920, 1080, 2},
			{1920, 1080, 3}, {1920, 1080, 4}, {2560, 1440, 0}, {3440, 1440, 0}, {3840, 2160, 0}};
		Set<String> shotAt = Set.of("854x480x0", "854x480x1", "1280x720x0", "1920x1080x0", "1920x1080x2", "1920x1080x4", "2560x1440x0", "3440x1440x0", "3840x2160x0");
		for (int[] c : configs) {
			String tag = c[0] + "x" + c[1] + "x" + c[2];
			this.setWindow(c[0], c[1], c[2]);
			if (this.hub() == null) {
				this.openClasses();
			}
			if (!this.ensureDraft()) {
				this.check(false, tag + ": a draft hand is up");
				continue;
			}
			this.hoverPage(".dr-hand .hc[data-hand=\"1\"] .hc-art");
			this.context.waitTicks(8);
			HubLayout l = this.ui.layout();
			String act = this.actual();
			boolean fits = l.window.x() >= 0 && l.window.y() >= 0 && l.window.right() <= l.fbW && l.window.bottom() <= l.fbH;
			boolean islands = true;
			for (Rect r : new Rect[] {l.mark, l.pages, l.close, l.strip}) {
				if (r != null && (r.x() < 0 || r.y() < 0 || r.right() > l.fbW || r.bottom() > l.fbH || r.intersects(l.window))) {
					islands = false;
				}
			}
			log(String.format(java.util.Locale.ROOT, "CONFIG %s asked, got %s, u=%.4f, window %s, strip %s", tag, act, l.u, l.window, l.strip));
			this.check(fits, tag + " (" + act + "): the window fits the framebuffer: " + l.window);
			this.check(islands, tag + " (" + act + "): islands inside the framebuffer and off the window");
			this.align(tag + " (" + act + ") draft hover");
			this.overflow(tag + " (" + act + ") draft");
			if (shotAt.contains(tag)) {
				this.shot("audit_03_draft_" + tag);
			}
		}
		this.setWindow(1920, 1080, 0);
	}

	private void resizeMidBurn() {
		this.setWindow(1920, 1080, 0);
		if (this.hub() == null) {
			this.openClasses();
		}
		this.ensureDraft();
		long w0 = this.waiting();
		this.hoverPage(".dr-hand .hc[data-hand=\"1\"] .hc-art");
		this.context.waitTicks(6);
		this.context.getInput().pressKey(InputConstants.KEY_2);
		this.context.waitTicks(3);
		this.context.getInput().resizeWindow(1280, 720);
		this.context.waitTicks(3);
		this.align("resize 1080p->720p mid-burn");
		this.shot("audit_04_resize_mid_burn");
		this.context.waitTicks(50);
		this.check(this.waiting() == w0 - 1, "the pick survived the resize: waiting " + w0 + " -> " + this.waiting());
		this.setWindow(1920, 1080, 0);
	}

	private void scaleMidDraft() {
		this.ensureDraft();
		this.hoverPage(".dr-hand .hc[data-hand=\"0\"] .hc-art");
		this.context.waitTicks(6);
		this.align("gs auto before change");
		this.context.runOnClient(mc -> {
			mc.options.guiScale().set(2);
			mc.resizeGui();
		});
		this.context.waitTicks(1);
		this.align("gs auto->2, 1 tick after");
		this.context.waitTicks(5);
		this.align("gs auto->2, 6 ticks after");
		this.shot("audit_05_gs_change_mid_draft");
		this.context.runOnClient(mc -> {
			mc.options.guiScale().set(0);
			mc.resizeGui();
		});
		this.context.waitTicks(6);
	}

	// ---- 2. double actions -------------------------------------------------------------------------------------

	private void doubleActions() {
		// Key 1 twice in one tick.
		this.ensureDraft();
		long w0 = this.waiting();
		int b0 = this.binder().size();
		this.context.getInput().pressKey(InputConstants.KEY_1);
		this.context.getInput().pressKey(InputConstants.KEY_1);
		this.context.waitTicks(45);
		this.check(this.waiting() == w0 - 1 && this.binder().size() == b0 + 1, "1 pressed twice keeps one card: waiting " + w0 + "->" + this.waiting() + ", binder " + b0 + "->" + this.binder().size());

		// A double click on a card.
		this.ensureDraft();
		w0 = this.waiting();
		b0 = this.binder().size();
		this.doubleClickPage(".dr-hand .hc[data-hand=\"0\"] .hc-art");
		this.context.waitTicks(45);
		this.check(this.waiting() == w0 - 1 && this.binder().size() == b0 + 1, "a double click on a card keeps one: waiting " + w0 + "->" + this.waiting() + ", binder " + b0 + "->" + this.binder().size());

		// A double click on Redeal all.
		this.ensureDraft();
		long r0 = this.rerolls();
		w0 = this.waiting();
		this.doubleClickPage("[data-redeal]");
		this.context.waitTicks(80);
		this.check(this.rerolls() == r0 - 1 && this.waiting() == w0, "a double click on Redeal all spends one reroll: " + r0 + "->" + this.rerolls() + ", waiting " + w0 + "->" + this.waiting());

		// A double click on Claim Mythic (Blades high enough to be dealt one).
		this.world.getServer().runCommand("mettle xp mettle:blades 20000 Player0");
		this.context.waitTicks(5);
		this.dismiss();
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
		this.js("(function(){MC.ov.draft('mettle:blades');return ''})()");
		if (this.waitPage("!!document.querySelector('[data-claim]')", 200)) {
			int m0 = this.mythicsOwned();
			w0 = this.waiting();
			this.doubleClickPage("[data-claim]");
			this.context.waitTicks(40);
			this.check(this.mythicsOwned() == m0 + 1 && this.waiting() == w0, "a double click on Claim Mythic claims once: mythics " + m0 + "->" + this.mythicsOwned() + ", waiting " + w0 + "->"
				+ this.waiting());
			this.waitPage("MC.ov.kind()==='draft' && !document.getElementById('rv-stage')", 100);
			this.shot("audit_06_after_double_claim");
		} else {
			this.check(false, "a Mythic offer settled with Claim (page: " + this.js("MC.debug()") + ")");
		}
		this.dismiss();
	}

	// ---- 3. lifecycle -----------------------------------------------------------------------------------------

	private void closeMidBurn() {
		this.dismiss();
		this.ensureDraft();
		long w0 = this.waiting();
		HubInventoryScreen first = this.hub();
		this.context.getInput().pressKey(InputConstants.KEY_2);
		this.context.waitTicks(3);
		this.closeHub();
		boolean fxOff = !this.context.computeOnClient(mc -> ((dev.voidmc.hub.screen.CardFx) field(first, "fx")).active());
		this.check(fxOff, "closing mid-burn drops the native layers");
		this.context.waitTicks(50);  // the next deal was due at 1.25 s: the page holds it while hidden
		String queued = this.inbox();
		log("NOTE page messages queued while the hub was closed: " + queued);
		this.check(queued.startsWith("0 "), "nothing (no card sound, no layers) is queued for the next screen while the hub is closed: " + queued);
		this.check("true".equals(this.js("String(MC.ov.paused())")), "the page's timers are paused while the hub is closed");
		this.check(this.waiting() == w0 - 1, "the pick made before closing counted once: " + w0 + "->" + this.waiting());
		String pageOv = this.js("String(MC.ov.kind())");
		log("NOTE after close, the page's overlay is still: " + pageOv);
		this.context.getInput().pressKey(InputConstants.KEY_K);
		this.context.waitTicks(12);
		boolean pageActive = "true".equals(this.js("String(MC.ov.active())"));
		boolean modal = this.javaModal();
		log("NOTE reopened with K: page overlay " + this.js("String(MC.ov.kind())") + ", Java pageModal " + modal);
		this.ui.parkMouse();
		this.context.waitTicks(3);
		this.shot("audit_07_reopen_k_after_close_mid_burn");
		this.check(pageActive == modal, "after reopening with K, Java's modal flag matches the page (page overlay " + pageActive + ", Java modal " + modal + ")");
		this.context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		this.context.waitTicks(4);
		boolean open = this.hub() != null;
		this.check(pageActive, "the draft is back up after the reopen (the burn's next deal resumed)");
		this.check(!pageActive || open, "Esc on the reopened draft closes the draft, not the hub (hub open after Esc: " + open + ")");
		if (!open) {
			this.openClasses();
		}
		this.dismiss();
	}

	private void segmentMidBurn() {
		this.ensureDraft();
		this.context.getInput().pressKey(InputConstants.KEY_2);
		this.context.waitTicks(2);
		HubInventoryScreen s = this.hub();
		this.ui.clickPage("[data-seg=\"inventory\"]");
		this.context.waitTicks(4);
		this.check("inventory".equals(s.segment()), "the Inventory segment opened mid-burn");
		this.check(this.javaFx(s).isEmpty(), "no native card layers over the Inventory segment: " + this.javaFx(s).size());
		this.check("false".equals(this.js("String(MC.ov.active())")), "the draft overlay closed with the segment");
		this.check(!this.javaModal(), "Java no longer treats the page as modal");
		this.ui.parkMouse();
		this.context.waitTicks(3);
		this.shot("audit_08_segment_switch_mid_burn");
		this.ui.clickPage("[data-seg=\"classes\"]");
		this.context.waitTicks(12);
		boolean pageActive = "true".equals(this.js("String(MC.ov.active())"));
		this.check(pageActive == this.javaModal(), "back on Classes, Java's modal flag matches the page (" + pageActive + ")");
		this.dismiss();
	}

	private void mythics() {
		this.dismiss();
		int m0 = this.mythicsOwned();
		this.world.getServer().runCommand("mettle cards mythic grant mettle:heart_of_the_mountain Player0");
		boolean up = this.waitPage("MC.ov.kind()==='reveal'", 80);
		this.check(up, "a granted Mythic is revealed on the open Classes page");
		this.context.waitTicks(22);
		this.closeHub();
		this.context.waitTicks(80);
		this.check("true".equals(this.js("String(MC.ov.paused())")), "the reveal's stages are paused while the hub is closed");
		this.openClasses();
		log("NOTE after closing mid-reveal and reopening: overlay " + this.js("String(MC.ov.kind())") + ", Java modal " + this.javaModal());
		this.check("true".equals(this.js("String(MC.ov.active())")) == this.javaModal(), "after a close mid-reveal, Java's modal flag matches the page");
		this.check(this.waitPage("!!document.querySelector('[data-close-ov]')", 20), "the reveal cut off by the close settles on reopen (Continue)");
		this.context.getInput().pressKey(InputConstants.KEY_TAB);
		this.context.waitTicks(3);
		this.check(this.hub() != null && "inventory".equals(this.hub().page()), "Tab with the reveal up does not switch to the Map under it");
		this.ui.parkMouse();
		this.context.waitTicks(3);
		this.shot("audit_09_reopen_after_close_mid_reveal");
		this.dismiss();
		this.world.getServer().runCommand("mettle cards mythic grant mettle:seven_league_stride Player0");
		if (this.waitPage("!!document.querySelector('[data-close-ov]')", 200)) {
			this.ui.clickPage("[data-close-ov]");
		}
		this.context.waitTicks(10);
		this.dismiss();
		this.check(this.mythicsOwned() >= m0 + 2, "two more Mythics owned: " + m0 + "->" + this.mythicsOwned());
		this.js("MC.nav.view='home';MC.nav.tab='hall';MC.render();''");
		this.context.waitTicks(4);
		this.ui.parkMouse();
		this.context.waitTicks(3);
		this.overflow("hall of firsts");
		this.shot("audit_10_hall_mythics");
	}

	// ---- 4. extreme state ---------------------------------------------------------------------------------------

	private void bigBinder() {
		this.dismiss();
		int before = this.binder().size();
		String[] perks = {"mining/prospector", "mining/quarryman", "mining/hard_hat", "mining/rock_hauler", "mining/tunneler"};
		String[] rar = {"COMMON", "RARE", "EPIC", "COMMON", "RARE", "CURSED"};
		String[] kws = {"hoard", "deep", "iron", "echo", "brutal"};
		String[] traits = {"mining/lucky=4", "mining/deepborn=9", "mining/stonehide=12", "mining/echoing=8"};
		String result = this.world.getServer().computeOnServer(s -> {
			var p = s.getPlayerList().getPlayers().getFirst();
			StringBuilder sb = new StringBuilder();
			for (String longOne : List.of("survival/living_off_the_land", "athletics/ox", "defense/roll_with_the_blow")) {
				try {
					MettleServer.giveExact(p, longOne, "EPIC", List.of("hoard", "deep"), List.of(), 3);
					sb.append(longOne).append(" ok; ");
				} catch (Throwable t) {
					sb.append(longOne).append(" refused (").append(t.getCause() == null ? t : t.getCause()).append("); ");
				}
			}
			int given = 0;
			int failed = 0;
			for (int i = 0; i < 400 && failed < 8; i++) {
				try {
					MettleServer.giveExact(p, perks[i % perks.length], rar[i % rar.length], List.of(kws[i % kws.length], kws[(i + 2) % kws.length]),
						List.of(traits[i % traits.length], traits[(i + 1) % traits.length]), 1 + i % 12);
					given++;
				} catch (Throwable t) {
					failed++;
					if (failed == 1) {
						sb.append("first refusal at ").append(i).append(": ").append(t.getCause() == null ? t : t.getCause()).append("; ");
					}
				}
			}
			return sb.append("given ").append(given).toString();
		});
		this.context.waitTicks(20);
		int size = this.binder().size();
		log("NOTE binder fill: " + result + "; binder " + before + " -> " + size);
		this.check(size >= 295, "the binder holds ~300 cards: " + size);

		// The render thread rebuilds and diffs Mettle's state every 10 ticks while the hub is open.
		String build = this.context.computeOnClient(mc -> {
			long t0 = System.nanoTime();
			String js = null;
			for (int i = 0; i < 10; i++) {
				js = MettleLink.state();
			}
			long t1 = System.nanoTime();
			for (int i = 0; i < 10; i++) {
				Json.parseObject(js);
			}
			long t2 = System.nanoTime();
			return String.format(java.util.Locale.ROOT, "MettleState.build %.2f ms, %d KB, Json.parse %.2f ms", (t1 - t0) / 10e6, js.length() / 1024, (t2 - t1) / 10e6);
		});
		log("PERF 400 cards, render thread: " + build);
		long heap0 = this.heapMb();
		long proc0 = processMb();
		this.js("MC.nav.view='home';MC.nav.tab='cards';var b=MC.st.binder;b.q='';b.skill='';b.rarity='';b.kw='';b.trait='';b.slot=false;b.att=false;b.scroll=0;b.sel=null;MC.render();''");
		this.context.waitTicks(10);
		String apply = this.js("(function(){var s=JSON.stringify(MC.M);var t0=performance.now();var o=JSON.parse(s);var t1=performance.now();hub.apply({type:'mettle',state:o});"
			+ "var t2=performance.now();MC.nav.tab='skills';MC.render();MC.nav.tab='cards';var t3=performance.now();MC.render();var t4=performance.now();"
			+ "return 'parse '+(t1-t0).toFixed(1)+' ms, apply '+(t2-t1).toFixed(1)+' ms, binder build '+(t4-t3).toFixed(1)+' ms, '+document.getElementsByTagName('*').length+' elements, '+(s.length>>10)+' KB'})()");
		log("PERF 400 cards, page (UI thread): " + apply);
		this.ui.parkMouse();
		this.context.waitTicks(5);
		this.overflow("binder 400");
		this.shot("audit_11_binder_400");
		int builds0 = dev.voidmc.hub.mettle.MettleSync.builds;
		Perf idle = this.perf("binder400 idle", () -> this.context.waitTicks(80));
		int idleBuilds = dev.voidmc.hub.mettle.MettleSync.builds - builds0;
		log("PERF binder400 idle: Mettle state built " + idleBuilds + " times in 80 ticks");
		this.check(idle.painted() == 0, "PERF binder400 idle paints nothing: " + idle);
		this.check(idleBuilds <= 1, "PERF binder400 idle: Mettle's state is not rebuilt while nothing changes (" + idleBuilds + " builds in 80 ticks)");
		this.check(idle.renderP95() < 0.5, "PERF binder400 idle: render thread p95 under 0.5 ms, pushExtra counted (" + idle + ")");
		Perf scroll = this.perf("binder400 wheel scroll", () -> {
			this.hoverPage("#bg-5");
			for (int k = 0; k < 20; k++) {
				this.context.getInput().scroll(-1);
				this.context.waitTicks(2);
			}
			for (int k = 0; k < 20; k++) {
				this.context.getInput().scroll(1);
				this.context.waitTicks(2);
			}
		});
		this.check(scroll.painted() >= 20 && scroll.within(10, 60), "PERF binder400 wheel scroll (the grid moved: 20+ paints): UI p95 under 10 ms and no paint over 60 ms: " + scroll);
		Perf hover = this.perf("binder400 hover sweep", () -> {
			for (int k = 0; k < 3; k++) {
				for (int t = 0; t < 12; t++) {
					if ("true".equals(this.js("String(!!document.getElementById('bg-" + t + "'))"))) {
						this.hoverPage("#bg-" + t);
						this.context.waitTicks(2);
					}
				}
			}
		});
		this.check(hover.painted() >= 20 && hover.within(10, 30), "PERF binder400 hover sweep (cards lifted: 20+ paints): UI p95 under 10 ms and no paint over 30 ms: " + hover);
		this.perf("binder400 filter+search", () -> {
			this.ui.clickPage("[data-bf=\"slot\"]");
			this.ui.clickPage("[data-bf=\"slot\"]");
			this.ui.clickPage("[data-search]");
			this.context.getInput().typeChars("pro");
			this.context.waitTicks(4);
			for (int k = 0; k < 3; k++) {
				this.context.getInput().pressKey(InputConstants.KEY_BACKSPACE);
				this.context.waitTicks(2);
			}
			this.context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			this.context.waitTicks(3);
		});
		this.check(this.hub() != null, "Esc after searching left the hub open");
		// Reopening Classes with the full binder: nothing changed, so nothing is rebuilt, re-sent or re-drawn. (No drafts
		// wait: an open deals what waits, by design, and that deal is not what this measures.)
		this.clearDrafts();
		this.js("MC.nav.view='home';MC.nav.tab='cards';MC.render();''");
		this.context.waitTicks(4);
		List<Double> reopen = new ArrayList<>();
		int builds1 = dev.voidmc.hub.mettle.MettleSync.builds;
		for (int i = 0; i < 10; i++) {
			this.closeHub();
			this.openClasses();
			reopen.add(this.context.computeOnClient(mc -> (HubStats.INSTANCE.lastScreenFirstFrameNs - HubStats.INSTANCE.lastScreenInitNs) / 1e6));
		}
		reopen.sort(null);
		int reopenBuilds = dev.voidmc.hub.mettle.MettleSync.builds - builds1;
		log(String.format(java.util.Locale.ROOT, "PERF binder400 reopen Classes x10: first frame p50 %.1f ms max %.1f ms, Mettle state built %d times", reopen.get(5), reopen.get(9), reopenBuilds));
		this.check(reopen.get(9) < 50, "PERF binder400 reopen: first frame under 50 ms (max " + reopen.get(9) + " ms)");
		this.check(reopenBuilds <= 1, "PERF binder400 reopen: Mettle's state is not rebuilt for an unchanged reopen (" + reopenBuilds + ")");
		long heap1 = this.heapMb();
		log("PERF 400 cards memory: heap " + heap0 + " -> " + heap1 + " MB (committed " + this.context.computeOnClient(mc -> Runtime.getRuntime().totalMemory() >> 20) + " MB), process committed " + proc0 + " -> " + processMb() + " MB");
		String missing = this.js("(function(){var need={},miss=[];JSON.stringify(MC.M,function(k,v){if((k==='icon'||k==='art')&&typeof v==='string'&&/\\.png$/.test(v))need[v]=1;return v});"
			+ "for(var k in need)if(!MC.icons[k])miss.push(k);return miss.length+' '+miss.slice(0,5).join(',')})()");
		this.check(missing.startsWith("0"), "every icon the state names reached the page: missing " + missing);
	}

	private void longNames() {
		this.dismiss();
		this.js("MC.nav.view='home';MC.nav.tab='cards';MC.render();''");
		this.context.waitTicks(3);
		// Real cards: Ox (the longest rules text Mettle has, 187 chars) as the binder holds it, given three traits and the
		// cursed price for the worst case; the Mythic with the longest effect and lore, as the Hall lists it.
		String real = this.js("(function(){var b=MC.M.cards.binder.cards,ox=null,longest=null;for(var i=0;i<b.length;i++){if(String(b[i].perk||b[i].id).indexOf('ox')>=0&&String(b[i].name).indexOf('Ox')===0)ox=b[i];"
			+ "if(!MC.isMythic(b[i])&&(!longest||String(b[i].description).length>String(longest.description).length))longest=b[i];}"
			+ "var all=MC.M.cards.mythics.all,my=null;for(var j=0;j<all.length;j++){var m=all[j],l=String(m.effect||'').length+String(m.lore||'').length;if(!my||l>String(my.effect||'').length+String(my.lore||'').length)my=m;}"
			+ "window.__auditCards={ox:ox||longest,longest:longest,mythic:my};return (ox?ox.name:'no Ox')+' '+String((ox||longest).description).length+' chars; longest '+longest.name+' '+String(longest.description).length+'; mythic '+(my?my.name+' '+(String(my.effect).length+String(my.lore||'').length):'none')})()");
		log("NOTE longest real cards: " + real);
		String r = this.js("(function(){var A=window.__auditCards,base=A.ox;var c=JSON.parse(JSON.stringify(base));"
			+ "c.name='Living off the Land III';c.baseName='Living off the Land';c.cursed=true;c.rarity='cursed';"
			+ "c.description='You can carry 38 kg more and your load slows you only half as much. Sneak and use an empty hand on a chest or barrel to pick it up whole, with everything inside (it weighs what it holds).';"
			+ "c.downside='Every swing costs a little hunger, and in the rain your tools wear twice as fast.';c.slotsAt='Slots at Survival 20';"
			+ "c.keywords=[{id:'mettle:hoard',name:'Hoard'},{id:'mettle:deep',name:'Deep'},{id:'mettle:echo',name:'Echo'}];"
			+ "c.traits=[{id:'a',name:'Swift-flighted',valueText:'+12.5 %',boostedText:'+18.75 %'},{id:'b',name:'Steady-handed',valueText:'+0.25 blocks',boostedText:'+0.4 blocks'},{id:'c',name:\"Game-hunter's\",valueText:'15 %',boostedText:'22 %'}];"
			+ "var ox=JSON.parse(JSON.stringify(A.ox));ox.traits=c.traits;"
			+ "var h=document.createElement('div');h.id='audit-host';h.style.cssText='position:absolute;left:0;top:24px;width:987px;height:606px;background:#0B0B0C;z-index:60';"
			+ "document.getElementById('classes').appendChild(h);"
			+ "var mi=MC.mythicInfo({perk:'mythic/'+A.mythic.id},null);mi.how='Dealt at Mining 30';"
			+ "h.innerHTML='<div style=\"position:absolute;left:12px;top:40px;width:248px;height:328px\">'+MC.hcard(c,0,null,{})+'</div>'"
			+ "+'<div style=\"position:absolute;left:268px;top:40px;width:248px;height:328px\">'+MC.hcard(ox,1,null,{})+'</div>'"
			+ "+'<div style=\"position:absolute;left:524px;top:40px;width:248px;height:328px\">'+MC.mythicCard(mi,2,'1 of 1 in this world')+'</div>'"
			+ "+'<div style=\"position:absolute;left:800px;top:40px;width:96px;height:128px\">'+MC.ccard(c,{})+'</div>'"
			+ "+'<div style=\"position:absolute;left:12px;top:400px;width:456px;height:52px\">'+MC.row(c,{style:'width:456px',glyphX:176,traitX:218,traitW:140,maxTraits:1})+'</div>';"
			+ "var cards=h.querySelectorAll('.hc');for(var i=0;i<cards.length;i++)MC.layoutCard(cards[i]);MC.fitIn(h);"
			+ "var out=[];for(var q=0;q<cards.length;q++){var hc=cards[q],fl=hc.querySelector('.hc-flow'),bt=hc.querySelector('.hc-bot');"
			+ "if(fl&&bt&&fl.offsetTop+fl.scrollHeight>bt.offsetTop+1)out.push('hand '+q+': rules run into the traits ('+(fl.offsetTop+fl.scrollHeight)+'>'+bt.offsetTop+')');"
			+ "if(bt&&bt.offsetTop+bt.offsetHeight>hc.offsetHeight-6)out.push('hand '+q+': traits past the bottom ('+(bt.offsetTop+bt.offsetHeight)+'/'+hc.offsetHeight+')');"
			+ "var mf=hc.querySelector('.my-flow');if(mf&&mf.scrollHeight>mf.clientHeight+1)out.push('mythic: text cut by its box ('+mf.scrollHeight+'/'+mf.clientHeight+')');"
			+ "var d=hc.querySelector('.hc-desc,.my-eff');if(d&&parseFloat(getComputedStyle(d).fontSize)*MC.u<6.95)out.push('card '+q+': rules text under 7 device px');}"
			+ "var all=h.querySelectorAll('*');for(var j=0;j<all.length;j++){var e=all[j];if(e.tagName==='svg'||e.tagName==='path'||e.children.length)continue;if(e.textContent&&(e.scrollWidth>e.clientWidth+1||e.scrollHeight>e.clientHeight+2)&&getComputedStyle(e).overflow!=='visible')out.push((e.className||e.tagName)+' clips \"'+e.textContent.slice(0,24)+'\" '+e.scrollWidth+'/'+e.clientWidth+'x'+e.scrollHeight+'/'+e.clientHeight);}"
			+ "for(var z=0;z<cards.length;z++){var R=cards[z].getBoundingClientRect(),kids=cards[z].querySelectorAll('*');for(var y=0;y<kids.length;y++){var K=kids[y].getBoundingClientRect();if(K.width&&K.height&&(K.right>R.right+1||K.bottom>R.bottom+1||K.left<R.left-1))out.push('card '+z+': '+(kids[y].className&&kids[y].className.baseVal!==undefined?'svg':kids[y].className)+' leaves the card');}}"
			+ "return out.join(' | ');})()");
		this.ui.parkMouse();
		this.context.waitTicks(4);
		this.shot("audit_12_longest_names");
		log("NOTE longest-text cards: " + (r.isEmpty() ? "all fit" : r));
		this.check(r.isEmpty(), "the longest real cards (Ox with three traits, the longest Mythic) and the worst case fit their hand, Mythic, collection and row faces: " + r);
		this.js("(function(){var h=document.getElementById('audit-host');if(h)h.parentNode.removeChild(h);return ''})()");
	}

	// ---- 5. input ---------------------------------------------------------------------------------------------

	private void searchKeys() {
		this.dismiss();
		this.js("MC.nav.view='home';MC.nav.tab='cards';MC.render();''");
		this.context.waitTicks(3);
		long w0 = this.waiting();
		int b0 = this.binder().size();
		this.ui.clickPage("[data-search]");
		this.context.waitTicks(2);
		this.check(this.javaText(), "clicking the search field gave it the keyboard (Java pageText)");
		// Real typing: a key press then its character, as GLFW delivers them.
		for (int[] k : new int[][] {{InputConstants.KEY_1, '1'}, {InputConstants.KEY_2, '2'}, {InputConstants.KEY_E, 'e'}, {InputConstants.KEY_R, 'r'}}) {
			this.context.getInput().pressKey(k[0]);
			this.context.getInput().typeChar(k[1]);
			this.context.waitTicks(1);
		}
		this.context.waitTicks(4);
		String q = this.js("MC.st.binder.q");
		this.check("12er".equals(q), "typing 1, 2, E, R into search types them: '" + q + "'");
		this.check(this.hub() != null, "E typed into search did not close the hub");
		this.check(this.waiting() == w0 && this.binder().size() == b0, "number keys typed into search picked nothing: waiting " + w0 + "->" + this.waiting());
		this.check("false".equals(this.js("String(MC.ov.active())")), "no draft opened while typing");
		// A level-up lands while the search field has the keyboard.
		this.world.getServer().runOnServer(s -> MettleServer.freshDrafts(s.getPlayerList().getPlayers().getFirst(), "mining", 2));
		this.context.waitTicks(30);
		String ov = this.js("String(MC.ov.kind())");
		boolean focus = "true".equals(this.js("String(MC.textFocus)"));
		log("NOTE a draft arriving while typing in search: overlay " + ov + ", page text focus " + focus + ", Java pageText " + this.javaText() + ", Java modal " + this.javaModal());
		this.ui.parkMouse();
		this.context.waitTicks(3);
		this.shot("audit_13_draft_while_typing");
		if ("draft".equals(ov)) {
			long w1 = this.waiting();
			this.context.getInput().pressKey(InputConstants.KEY_1);
			this.context.getInput().typeChar('1');
			this.context.waitTicks(40);
			this.check(this.waiting() == w1 - 1 || !focus, "with a draft dealt over the focused search, 1 still keeps a card (waiting " + w1 + "->" + this.waiting() + ", search now '"
				+ this.js("MC.st.binder.q") + "')");
		}
		this.context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		this.context.waitTicks(3);
		this.dismiss();
		this.check(this.hub() != null, "the hub is still open after the search");
	}

	private void escNested() {
		this.dismiss();
		// A dropdown menu: Esc closes it, not the hub.
		this.js("MC.nav.view='home';MC.nav.tab='cards';MC.render();''");
		this.context.waitTicks(3);
		this.ui.clickPage("[data-dd=\"sound\"]");
		this.context.waitTicks(3);
		this.check(!"".equals(this.js("MC.st.menu?MC.st.menu.id:''")), "the sound menu opened");
		this.check(this.javaModal(), "Java treats the open menu as modal (Esc is the page's)");
		this.context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		this.context.waitTicks(3);
		this.check(this.hub() != null && "".equals(this.js("MC.st.menu?'open':''")), "Esc closes the menu and keeps the hub");
		if (this.hub() == null) {
			this.openClasses();
		}
		// The deck's take-out popover over a slotted card (a Mining card to slot, given if the binder has none).
		String find = "(function(){var l=MC.M.cards.binder.cards;for(var i=0;i<l.length;i++){var c=l[i];if(c.skill==='mettle:mining'&&c.canSlot&&!MC.isMythic(c)&&c.attuned!==false&&c.place==='binder')return c.id}return ''})()";
		if (this.js(find).isEmpty()) {
			this.world.getServer().runOnServer(s -> MettleServer.giveExact(s.getPlayerList().getPlayers().getFirst(), "mining/prospector", "COMMON", List.of("deep"), List.of(), 1));
			this.waitPage("(" + find + ")!==''", 40);
		}
		String id = this.js("(function(){var l=MC.M.cards.binder.cards;for(var i=0;i<l.length;i++){var c=l[i];if(c.skill==='mettle:mining'&&c.canSlot&&!MC.isMythic(c)&&c.attuned!==false)return c.id}return ''})()");
		if (id.isEmpty()) {
			this.check(false, "a slottable Mining card for the popover test");
			return;
		}
		this.world.getServer().runOnServer(s -> MettleServer.call("dev.voidmc.mettle.character.cards.Cards", "slot", s.getPlayerList().getPlayers().getFirst(), Long.parseLong(id), 0));
		this.context.waitTicks(15);
		this.dismiss();
		this.js("MC.nav.view='skill';MC.nav.skill='mettle:mining';MC.nav.stab='deck';MC.render();''");
		this.context.waitTicks(4);
		this.ui.clickPage("[data-scard]");
		this.context.waitTicks(3);
		this.check(!"null".equals(this.js("String(MC.st.deck.pop)")), "clicking a slotted card opened its popover");
		this.check(this.javaModal(), "Java treats the take-out popover as modal (Esc is the page's)");
		this.ui.parkMouse();
		this.context.waitTicks(2);
		this.shot("audit_14_deck_popover");
		this.context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		this.context.waitTicks(4);
		boolean open = this.hub() != null;
		this.check(open, "Esc with the deck's take-out popover open closes the popover, not the hub (hub open: " + open + ")");
		this.check("null".equals(this.js("String(MC.st.deck.pop)")) && !this.javaModal(), "the popover is closed and Java's modal flag dropped with it");
		// A filter dropdown on the deck, too.
		if (open) {
			this.ui.clickPage("[data-dd=\"d-rarity\"]");
			this.context.waitTicks(3);
			this.context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			this.context.waitTicks(3);
			this.check(this.hub() != null && "".equals(this.js("MC.st.menu?'open':''")), "Esc closes the deck's rarity dropdown and keeps the hub");
		}
		if (!open) {
			this.openClasses();
		}
		this.dismiss();
	}

	// ---- 5b. keyboard (H11) ---------------------------------------------------------------------------------------

	private String focusKey() {
		return this.js("String(MC.focusKey())");
	}

	private void key(int k) {
		this.context.getInput().pressKey(k);
		this.context.waitTicks(2);
	}

	private void keyboard() {
		if (this.hub() == null) {
			this.openClasses();
		}
		this.ui.parkMouse();
		// A draft: Right twice chooses the middle card (focus ring and hot frame), Enter keeps it.
		this.check(this.ensureDraft(), "a draft is up for the keyboard");
		this.ui.parkMouse();
		this.context.waitTicks(3);
		long w0 = this.waiting();
		int b0 = this.binder().size();
		this.key(InputConstants.KEY_RIGHT);
		this.key(InputConstants.KEY_RIGHT);
		this.check("1".equals(this.js("String(MC.ov.hovered())")) && "true".equals(this.js("String(!!document.querySelector('.dr-hand .hc.kbd[data-hand=\"1\"]'))")),
			"Right, Right chooses the middle card with a focus ring (hovered " + this.js("String(MC.ov.hovered())") + ")");
		this.key(InputConstants.KEY_LEFT);
		this.check("0".equals(this.js("String(MC.ov.hovered())")), "Left steps back to the first card");
		this.key(InputConstants.KEY_RIGHT);
		this.shot("audit_14b_draft_keyboard");
		this.key(InputConstants.KEY_RETURN);
		this.context.waitTicks(40);
		this.check(this.waiting() == w0 - 1 && this.binder().size() == b0 + 1, "Enter keeps the chosen card: waiting " + w0 + "->" + this.waiting() + ", binder " + b0 + "->" + this.binder().size());
		// The numpad picks too.
		if (this.ensureDraft()) {
			long w1 = this.waiting();
			this.key(InputConstants.KEY_NUMPAD1);
			this.context.waitTicks(40);
			this.check(this.waiting() == w1 - 1, "numpad 1 keeps the first card: waiting " + w1 + "->" + this.waiting());
		}
		this.dismiss();
	}

	private void keyboardBinder() {
		this.dismiss();
		this.ui.parkMouse();
		// The binder: arrows bring up a focus ring on the first card, Down moves a row, Enter selects, Esc lets go.
		this.js("MC.nav.view='home';MC.nav.tab='cards';MC.st.binder.sel=null;MC.st.binder.scroll=0;MC.st.binder.q='';MC.st.binder.skill='';MC.render();''");
		this.context.waitTicks(3);
		this.key(InputConstants.KEY_RIGHT);
		String k0 = this.focusKey();
		this.check(k0.startsWith("[data-bcard=") && "block".equals(this.js("MC.$('c-focus').style.display")), "an arrow brings the focus ring up on the binder's first card: " + k0);
		this.check(this.javaModal(), "with the focus ring up, Java leaves Esc to the page");
		this.key(InputConstants.KEY_DOWN);
		String k1 = this.focusKey();
		this.check(k1.startsWith("[data-bcard=") && !k1.equals(k0), "Down moves the ring a row down: " + k0 + " -> " + k1);
		for (int i = 0; i < 3; i++) {
			this.key(InputConstants.KEY_DOWN);
		}
		this.check(!"0".equals(this.js("String(MC.st.binder.scroll)")), "Down past the last row scrolls the binder (scroll " + this.js("String(MC.st.binder.scroll)") + ")");
		this.key(InputConstants.KEY_RETURN);
		this.context.waitTicks(2);
		String sel = this.js("String(MC.st.binder.sel)");
		this.check(!"null".equals(sel) && this.focusKey().contains(sel), "Enter selects the focused card: " + sel + " (focus " + this.focusKey() + ")");
		this.shot("audit_14c_binder_keyboard");
		this.key(InputConstants.KEY_ESCAPE);
		this.check(this.hub() != null && "null".equals(this.focusKey()) && !this.javaModal(), "Esc lets go of the focus ring, the hub stays open");
		// The deck: select a binder card and slot it into an open slot, keys only.
		String skill = this.js("(function(){var d=MC.M.cards.decks;for(var i=0;i<d.length;i++){var open=0;for(var j=0;j<d[i].row.length;j++)if(d[i].row[j].open&&!d[i].row[j].card)open++;"
			+ "if(!open)continue;var l=MC.M.cards.binder.cards;for(var k=0;k<l.length;k++)if(l[k].skill===d[i].skill&&l[k].place==='binder'&&l[k].canSlot&&l[k].attuned!==false&&!MC.isMythic(l[k]))return d[i].skill;}return ''})()");
		if (skill.isEmpty()) {
			this.check(false, "a deck with an open slot and a slottable card for the keyboard");
			return;
		}
		this.js("MC.nav.view='skill';MC.nav.skill=" + Json.quote(skill) + ";MC.nav.stab='deck';MC.st.deck.sel=null;MC.st.deck.pop=null;MC.st.deck.slot=true;MC.render();''");
		this.context.waitTicks(4);
		this.key(InputConstants.KEY_DOWN);
		for (int i = 0; i < 8 && !this.focusKey().startsWith("[data-dcard="); i++) {
			this.key(InputConstants.KEY_DOWN);
		}
		String card = this.focusKey().replaceAll("[^0-9]", "");
		this.check(!card.isEmpty(), "the ring reaches the deck's binder: " + this.focusKey());
		int slotted0 = this.slottedCount();
		this.key(InputConstants.KEY_RETURN);
		this.check(card.equals(this.js("String(MC.st.deck.sel)")), "Enter selects the binder card for slotting (" + this.js("String(MC.st.deck.sel)") + ")");
		for (int i = 0; i < 12; i++) {
			String where = this.js("(function(){var e=MC.focusKey()&&document.querySelector('#cv '+MC.focusKey());return !e?'none':e.classList.contains('drop')?'drop':e.closest('#dk-row')?'row':'other'})()");
			if ("drop".equals(where)) {
				break;
			}
			this.key("row".equals(where) ? InputConstants.KEY_RIGHT : InputConstants.KEY_UP);
		}
		this.key(InputConstants.KEY_RETURN);
		this.context.waitTicks(15);
		this.check(this.slottedCount() == slotted0 + 1, "Enter on an open slot slots the card, keys only: slotted " + slotted0 + " -> " + this.slottedCount());
		this.key(InputConstants.KEY_ESCAPE);
		this.dismiss();
	}

	private int slottedCount() {
		int n = 0;
		for (Map<String, Object> c : this.binder()) {
			if ("slotted".equals(c.get("place"))) {
				n++;
			}
		}
		return n;
	}

	// ---- 6. trade -----------------------------------------------------------------------------------------------

	private static net.minecraft.server.level.ServerPlayer standIn(net.minecraft.server.MinecraftServer s) {
		return net.fabricmc.fabric.api.entity.FakePlayer.get(s.overworld(), new com.mojang.authlib.GameProfile(BREE, "Bree"));
	}

	private static final java.util.UUID BREE = java.util.UUID.randomUUID();

	private void incomingTrade() {
		this.clearDrafts();
		this.js("MC.nav.view='home';MC.nav.tab='cards';MC.st.binder.q='';MC.render();''");
		this.context.waitTicks(3);
		this.ui.clickPage("[data-search]");
		this.context.getInput().typeChars("mi");
		this.context.waitTicks(3);
		this.check(this.javaText(), "search has the keyboard before the trade is asked for");
		// Bree (a second player) asks to trade while the hub is open on Classes.
		Object asked = this.world.getServer().computeOnServer(s -> {
			var a = s.getPlayerList().getPlayers().getFirst();
			var b = standIn(s);
			b.snapTo(a.getX() + 2, a.getY(), a.getZ(), 0, 0);
			b.setHealth(b.getMaxHealth());
			MettleServer.trade("standIn", b);
			return MettleServer.trade("request", b, a);
		});
		log("NOTE Bree asks to trade: " + asked);
		boolean toast = this.waitPage("!!document.querySelector('#c-toast.ask [data-taccept]') && document.getElementById('c-toast').style.display!=='none'", 60);
		this.check(toast, "another player's trade request shows on the open Classes page with Accept: " + this.js("String((document.getElementById('c-toast')||{}).textContent)"));
		this.check("undefined".equals(this.js("String(window.__hubError)")) && this.hub() != null && "home-cards".equals(this.js("JSON.parse(MC.debug()).screen")),
			"the request did not break the open page (no error, still the binder)");
		this.check(this.javaText() && "mi".equals(this.js("MC.st.binder.q")), "the search keeps the keyboard and its text while the request waits");
		this.ui.parkMouse();
		this.context.waitTicks(3);
		this.shot("audit_15a_trade_request");
		// Bree's side opens the trade (as accepting would): the trade takes the page.
		this.world.getServer().runOnServer(s -> {
			var a = s.getPlayerList().getPlayers().getFirst();
			log("NOTE the trade opens: " + MettleServer.trade("open", standIn(s), a));
		});
		boolean shown = this.waitPage("document.getElementById('cv').className.indexOf('scr-trade')>=0 && !document.getElementById('tr-ask')", 60);
		this.check(shown, "a trade another player opened shows on the open Classes page");
		this.context.waitTicks(4);
		this.check(!this.javaText() && "false".equals(this.js("String(MC.textFocus)")), "the search field let go of the keyboard when the trade took the page (Java pageText " + this.javaText() + ")");
		this.context.getInput().pressKey(InputConstants.KEY_E);
		this.context.waitTicks(3);
		this.check(this.hub() == null || !this.javaText(), "E is not swallowed by a dead search field");
		if (this.hub() == null) {
			this.openClasses();
		}
		this.ui.parkMouse();
		this.context.waitTicks(3);
		this.shot("audit_15_incoming_trade");
		this.js("MC.act('tradeCancel','');''");
		this.context.waitTicks(10);
		// Accept on the request toast asks Mettle to accept (a stand-in cannot be accepted for real: Mettle looks the asker
		// up among the server's players).
		this.world.getServer().runOnServer(s -> MettleServer.trade("request", standIn(s), s.getPlayerList().getPlayers().getFirst()));
		if (this.waitPage("!!document.querySelector('#c-toast.ask [data-taccept]') && document.getElementById('c-toast').style.display!=='none'", 60)) {
			this.ui.clickPage("#c-toast [data-taccept]");
			this.context.waitTicks(3);
			this.check("tradeAccept ".equals(this.js("MC.lastAct")), "Accept on the request asks Mettle to accept the trade: " + this.js("MC.lastAct"));
		} else {
			this.check(false, "a second trade request shows Accept");
		}
		this.context.waitTicks(10);
	}

	@SuppressWarnings("unchecked")
	private void bigTrade() {
		this.dismiss();
		List<Long> mine = new ArrayList<>();
		for (Map<String, Object> c : this.binder()) {
			if (mine.size() < 9 && !"mythic".equals(c.get("rarity")) && !"mythic".equals(c.get("kind")) && !"slotted".equals(c.get("place")) && !Boolean.FALSE.equals(c.get("attuned"))) {
				mine.add(Long.parseLong((String) c.get("id")));
			}
		}
		String res = this.world.getServer().computeOnServer(s -> {
			var a = s.getPlayerList().getPlayers().getFirst();
			var b = standIn(s);
			b.snapTo(a.getX() + 2, a.getY(), a.getZ(), 0, 0);
			b.setHealth(b.getMaxHealth());
			a.setHealth(a.getMaxHealth());
			MettleServer.trade("standIn", b);
			MettleServer.clearCards(b);
			var items = List.of(Items.DIAMOND, Items.GOLDEN_APPLE, Items.EMERALD, Items.IRON_INGOT, Items.GOLD_INGOT, Items.ENCHANTED_GOLDEN_APPLE, Items.NETHERITE_SCRAP,
				Items.ARROW, Items.BREAD);
			for (int i = 0; i < 9; i++) {
				b.getInventory().setItem(i, new ItemStack(items.get(i), 1 + i * 7));
				a.getInventory().setItem(i, new ItemStack(items.get(8 - i), 2 + i * 7));
			}
			StringBuilder sb = new StringBuilder();
			sb.append("request ").append(MettleServer.trade("request", a, b)).append(" accept ").append(MettleServer.trade("accept", b));
			for (int i = 0; i < 9; i++) {
				long id = MettleServer.giveExact(b, i % 2 == 0 ? "mining/quarryman" : "mining/tunneler", i % 3 == 0 ? "EPIC" : "RARE", List.of("brutal", "deep"),
					List.of("mining/deepborn=8", "mining/lucky=5"), 3);
				Object r1 = MettleServer.trade("offerCard", b, id);
				Object r2 = MettleServer.trade("offerItem", b, i);
				if (!"Optional.empty".equals(r1.toString()) || !"Optional.empty".equals(r2.toString())) {
					sb.append(" | bree ").append(i).append(": ").append(r1).append(" ").append(r2);
				}
			}
			for (int i = 0; i < mine.size(); i++) {
				Object r1 = MettleServer.trade("offerCard", a, mine.get(i));
				Object r2 = MettleServer.trade("offerItem", a, i);
				if (!"Optional.empty".equals(r1.toString()) || !"Optional.empty".equals(r2.toString())) {
					sb.append(" | me ").append(i).append(": ").append(r1).append(" ").append(r2);
				}
			}
			return sb.toString();
		});
		log("NOTE 9x9 trade setup: " + res);
		this.check(this.waitPage("document.getElementById('cv').className.indexOf('scr-trade')>=0 && !document.getElementById('tr-ask')", 80), "the 9 x 9 trade shows");
		this.context.waitTicks(10);
		HubInventoryScreen s = this.hub();
		List<Bridge.ItemIcon> its = this.context.computeOnClient(mc -> s.pageItems());
		int overlaps = 0;
		for (int i = 0; i < its.size(); i++) {
			for (int j = i + 1; j < its.size(); j++) {
				Bridge.ItemIcon p = its.get(i);
				Bridge.ItemIcon q = its.get(j);
				if (p.x() < q.x() + q.w() && q.x() < p.x() + p.w() && p.y() < q.y() + q.h() && q.y() < p.y() + p.h()) {
					overlaps++;
				}
			}
		}
		Map<String, Object> st = this.state();
		Map<String, Object> t = (Map<String, Object>) ((Map<String, Object>) st.get("cards")).get("trade");
		int meCards = t == null ? -1 : ((List<Object>) ((Map<String, Object>) t.get("me")).get("cards")).size();
		int themCards = t == null ? -1 : ((List<Object>) ((Map<String, Object>) t.get("them")).get("cards")).size();
		log("NOTE trade sides: me " + meCards + " cards, them " + themCards + " cards, stacks drawn natively " + its.size());
		this.check(its.size() == 18, "18 stacks are drawn natively in the trade's wells: " + its.size());
		this.check(overlaps == 0, "no two trade stacks overlap: " + overlaps);
		// Each side: rows that show sit fully inside the list and above ITEMS, nothing overlaps (rows, the add pill, the
		// labels, the wells, the coin), and scrolling the list shows all nine cards.
		String probe = "(function(){var o=[];['me','them'].forEach(function(side){var col=document.getElementById('tr-'+side),list=document.getElementById('tr-'+side+'-list');if(!col||!list){o.push(side+' missing');return;}"
			+ "var L=list.getBoundingClientRect(),items=document.getElementById('tr-'+side+'-items').getBoundingClientRect(),boxes=[];var rs=list.querySelectorAll('.rw');"
			+ "for(var i=0;i<rs.length;i++){var r=rs[i].getBoundingClientRect();if(r.top<L.top-1||r.bottom>L.bottom+1)o.push(side+' row '+i+' cut by the list');boxes.push(['row'+i,r]);}"
			+ "var x=col.querySelectorAll('.tr-add,.tr-more,.it-slot,.coinf,.eb9,.lockp');for(var j=0;j<x.length;j++)boxes.push([x[j].className.split(' ')[0]+j,x[j].getBoundingClientRect()]);"
			+ "if(L.bottom>items.top)o.push(side+' list runs into ITEMS');"
			+ "var ad=col.querySelectorAll('.tr-add,.tr-more');for(var z=0;z<ad.length;z++)if(ad[z].scrollHeight>ad[z].clientHeight+1||ad[z].scrollWidth>ad[z].clientWidth+1)o.push(side+' '+ad[z].className+' text spills out of its box');"
			+ "for(var a=0;a<boxes.length;a++)for(var b=a+1;b<boxes.length;b++){var p=boxes[a][1],q=boxes[b][1];if(p.width&&q.width&&p.left<q.right-1&&q.left<p.right-1&&p.top<q.bottom-1&&q.top<p.bottom-1)o.push(side+' '+boxes[a][0]+' overlaps '+boxes[b][0]);}"
			+ "});return o.join(' | ')})()";
		String rowOverlaps = this.js(probe);
		log("NOTE trade columns: " + (rowOverlaps.isEmpty() ? "clean" : rowOverlaps));
		this.check(rowOverlaps.isEmpty(), "the 9 x 9 trade: card rows, the add pill, labels, wells and coin never overlap: " + rowOverlaps);
		for (String side : List.of("me", "them")) {
			Set<String> seen = new TreeSet<>();
			this.hoverPage("#tr-" + side + "-list");
			for (int k = 0; k < 12; k++) {
				for (String id : this.js("(function(){var o=[],r=document.querySelectorAll('#tr-" + side + "-list [data-tcard]');for(var i=0;i<r.length;i++)o.push(r[i].getAttribute('data-tcard'));return o.join(',')})()").split(",")) {
					if (!id.isEmpty()) {
						seen.add(id);
					}
				}
				this.context.getInput().scroll(-1);
				this.context.waitTicks(2);
			}
			String clean = this.js(probe);
			this.check(seen.size() == 9 && clean.isEmpty(), "scrolling " + side + " offer shows all 9 cards (" + seen.size() + "), still without overlaps: " + clean);
		}
		this.ui.parkMouse();
		this.context.waitTicks(3);
		this.shot("audit_16b_trade_9x9_scrolled");
		this.overflow("trade 9x9");
		String clipped = this.js("(function(){var o=[],e=document.querySelectorAll('#cv .rw-n,#cv .rw-s');for(var i=0;i<e.length;i++)if(e[i].scrollWidth>e[i].clientWidth+1)o.push(e[i].textContent);return o.join(' | ')})()");
		this.check(clipped.isEmpty(), "no card row's name or line is clipped in the trade: " + clipped);
		// Take one card back (nine is the most a side offers, so the add pill is gone), then: the card picker (+ Add a
		// card) holds Esc; the keyboard reaches it: Tab to the pill, Enter opens, Esc closes.
		this.ui.clickPage("#tr-me-list [data-ttake]");
		this.waitPage("!!document.querySelector('[data-tadd=\"1\"]')", 40);
		this.ui.clickPage("[data-tadd=\"1\"]");
		this.context.waitTicks(3);
		this.check("true".equals(this.js("String(MC.st.trade.adding)")) && this.javaModal(), "the trade's card picker opened and holds Esc");
		this.context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		this.context.waitTicks(3);
		this.check(this.hub() != null && "false".equals(this.js("String(MC.st.trade.adding)")), "Esc closes the trade's card picker, not the hub");
		this.ui.parkMouse();
		this.key(InputConstants.KEY_RIGHT);
		for (int i = 0; i < 30 && !this.focusKey().startsWith("[data-tadd="); i++) {
			this.context.getInput().pressKey(InputConstants.KEY_TAB);
			this.context.waitTicks(2);
		}
		this.check(this.focusKey().startsWith("[data-tadd="), "Tab moves the focus ring through the trade to + Add a card: " + this.focusKey());
		this.key(InputConstants.KEY_RETURN);
		this.check("true".equals(this.js("String(MC.st.trade.adding)")), "Enter on + Add a card opens the picker");
		this.key(InputConstants.KEY_ESCAPE);
		this.key(InputConstants.KEY_ESCAPE);
		this.check(this.hub() != null && "false".equals(this.js("String(MC.st.trade.adding)")), "Esc closes the picker, then the focus ring; the hub stays");
		this.ui.parkMouse();
		this.context.waitTicks(3);
		this.shot("audit_16_trade_9x9");
		// A double click on Lock.
		this.doubleClickPage("[data-tlock]");
		this.context.waitTicks(15);
		Map<String, Object> t2 = (Map<String, Object>) ((Map<String, Object>) this.state().get("cards")).get("trade");
		Object locked = t2 == null ? "no trade" : ((Map<String, Object>) t2.get("me")).get("locked");
		this.check(Boolean.TRUE.equals(locked), "a double click on Lock leaves the offer locked: " + locked);
		this.js("MC.act('tradeCancel','');''");
		this.context.waitTicks(15);
		this.check(this.context.computeOnClient(mc -> s.pageItems().isEmpty()), "no stacks drawn after the trade was cancelled");
	}

	private void nearby() {
		this.dismiss();
		this.js("MC.nav.view='home';MC.nav.tab='cards';MC.render();''");
		this.context.waitTicks(3);
		List<Map.Entry<String, Double>> many = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			many.add(Map.entry("Wanderer_Long" + i + "XY", 0.5 + i * 0.9));
		}
		this.context.runOnClient(mc -> HubInventoryScreen.testNearby = many);
		this.waitPage("MC.near.length===8", 40);
		this.ui.clickPage(".b-left [data-dd=\"trade-req\"]");
		this.context.waitTicks(3);
		String m = this.js("(function(){var d=document.querySelector('.ddm'),w=document.getElementById('win').getBoundingClientRect();if(!d)return 'no menu';var r=d.getBoundingClientRect();"
			+ "var rows=d.querySelectorAll('[data-ddv]'),cut=0;for(var i=0;i<rows.length;i++){var q=rows[i].getBoundingClientRect();if(q.bottom>r.bottom+1||q.bottom>w.bottom)cut++;}"
			+ "return rows.length+' rows, cut '+cut+', inside window '+(r.left>=w.left&&r.right<=w.right&&r.bottom<=w.bottom)})()");
		log("NOTE trade request menu with 8 players: " + m);
		this.check(m.startsWith("8 rows, cut 0, inside window true"), "8 players in reach all show in the request menu inside the window: " + m);
		this.ui.parkMouse();
		this.context.waitTicks(3);
		this.shot("audit_17_request_8_players");
		this.context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		this.context.waitTicks(3);
		this.context.runOnClient(mc -> HubInventoryScreen.testNearby = null);
		if (this.hub() == null) {
			this.openClasses();
		}
	}

	// ---- 7. toasts, reload, open/close, world changes -----------------------------------------------------------

	private void toasts() {
		this.context.runOnClient(mc -> mc.gui.toastManager().clear());
		if (this.hub() == null) {
			this.openClasses();
		}
		this.context.runOnClient(mc -> {
			for (int i = 0; i < 60; i++) {
				mc.gui.toastManager().addToast(new SystemToast(SystemToast.SystemToastId.PERIODIC_NOTIFICATION, Component.literal("Audit toast " + i), Component.literal("held")));
			}
		});
		this.context.waitTicks(40);
		String held = this.toastCounts();
		log("NOTE toasts while the hub is open: " + held);
		this.check(held.equals("visible 0 queued 60"), "60 toasts wait while the hub is open: " + held);
		this.closeHub();
		this.context.waitTicks(10);
		String after = this.toastCounts();
		log("NOTE toasts after closing: " + after);
		String[] p = after.split(" ");
		int vis = Integer.parseInt(p[1]);
		int q = Integer.parseInt(p[3]);
		this.check(vis > 0 && vis + q == 60, "after closing, the toasts show and none were lost: " + after);
		this.shot("audit_18_toasts_after_close");
		this.context.runOnClient(mc -> mc.gui.toastManager().clear());
	}

	private String toastCounts() {
		return this.context.computeOnClient(mc -> {
			ToastManager tm = mc.gui.toastManager();
			return "visible " + ((List<?>) field(tm, "visibleToasts")).size() + " queued " + ((java.util.Deque<?>) field(tm, "queued")).size();
		});
	}

	private void reload() {
		this.openClasses();
		this.ensureDraft();
		this.hoverPage(".dr-hand .hc[data-hand=\"1\"] .hc-art");
		this.context.waitTicks(6);
		Identifier edge = Identifier.fromNamespaceAndPath("voidhub", "cardfx/hand/edge_common_fill_px");
		Object hubTex0 = this.context.computeOnClient(mc -> mc.getTextureManager().getTexture(HubView.TEXTURE_ID));
		Object fx0 = this.context.computeOnClient(mc -> mc.getTextureManager().getTexture(edge));
		String iconId = this.js("(function(){for(var k in MC.icons)return k;return ''})()");
		Object icon0 = this.context.computeOnClient(mc -> MettleLink.icon(iconId));
		int reloads0 = dev.voidmc.hub.screen.CardFx.reloads;
		int tex0 = this.textures();
		CompletableFuture<Void> f = this.context.computeOnClient(mc -> mc.reloadResourcePacks());
		for (int i = 0; i < 600 && !f.isDone(); i += 5) {
			this.context.waitTicks(5);
		}
		this.context.waitTicks(20);
		this.check(f.isDone() && !f.isCompletedExceptionally(), "the resource reload finished with the hub open");
		Object hubTex1 = this.context.computeOnClient(mc -> mc.getTextureManager().getTexture(HubView.TEXTURE_ID));
		Object fx1 = this.context.computeOnClient(mc -> mc.getTextureManager().getTexture(edge));
		log("NOTE reload: hub texture kept " + (hubTex0 == hubTex1) + ", card strip texture kept " + (fx0 == fx1) + " (" + fx1.getClass().getSimpleName() + "), textures " + tex0 + " -> "
			+ this.textures() + ", hub still open " + (this.hub() != null));
		this.check(this.hub() != null && hubTex0 == hubTex1, "F3+T keeps the hub open and its texture");
		this.check(dev.voidmc.hub.screen.CardFx.reloads == reloads0 + 1 && fx1 != fx0, "F3+T reloads the card strips (a new texture, read from the new resources)");
		Object icon1 = this.context.computeOnClient(mc -> MettleLink.icon(iconId));
		this.check(!iconId.isEmpty() && icon1 != null && icon1 != icon0, "F3+T reads Mettle's icons again (" + iconId + ")");
		this.align("after F3+T");
		this.ui.parkMouse();
		this.hoverPage(".dr-hand .hc[data-hand=\"1\"] .hc-art");
		this.context.waitTicks(6);
		this.shot("audit_19_after_reload");
	}

	private void withoutMettle() {
		this.closeHub();
		this.context.runOnClient(mc -> MettleLink.forceAbsent = true);
		try {
			this.context.runOnClient(mc -> mc.gui.setScreen(new HubInventoryScreen(mc.player, "classes")));
			this.context.waitTicks(10);
			String segs = this.js("(function(){var o=[];var s=document.querySelectorAll('#segments .segment');for(var i=0;i<s.length;i++)if(s[i].offsetWidth>0)o.push(s[i].getAttribute('data-seg'));return o.join(',')})()");
			this.check("inventory".equals(segs) && this.hub() != null && "inventory".equals(this.hub().segment()),
				"without a linked Mettle the hub opens on the Inventory with no Classes or Gear tab: " + segs);
			this.shot("audit_20b_without_mettle");
		} finally {
			this.context.runOnClient(mc -> MettleLink.forceAbsent = false);
			this.closeHub();
		}
	}

	private void rapid() {
		this.closeHub();
		this.clearDrafts();
		this.closeHub();
		// One warm cycle first: a page's first open loads its strips and fonts once; what is measured is the growth after.
		for (String page : List.of("classes", "inventory", "gear", "classes", "map")) {
			this.context.runOnClient(mc -> mc.gui.setScreen(new HubInventoryScreen(mc.player, page)));
			this.context.waitTicks(3);
			this.context.runOnClient(mc -> mc.gui.setScreen(null));
			this.context.waitTicks(1);
		}
		this.context.waitTicks(10);
		long heap0 = this.heapMb();
		long proc0 = processMb();
		long committed0 = this.context.computeOnClient(mc -> Runtime.getRuntime().totalMemory() >> 20);
		int tex0 = this.textures();
		String dom0 = this.js("String(document.getElementsByTagName('*').length)");
		List<Double> opens = new ArrayList<>();
		StringBuilder byPage = new StringBuilder();
		String[] pages = {"classes", "inventory", "gear", "classes", "map"};
		for (int i = 0; i < 40; i++) {
			String page = pages[i % pages.length];
			this.context.runOnClient(mc -> mc.gui.setScreen(new HubInventoryScreen(mc.player, page)));
			this.context.waitTicks(1);
			double ms = this.context.computeOnClient(mc -> (HubStats.INSTANCE.lastScreenFirstFrameNs - HubStats.INSTANCE.lastScreenInitNs) / 1e6);
			opens.add(ms);
			byPage.append(String.format(java.util.Locale.ROOT, " %s %.0f", page, ms));
			if (i % 3 == 0) {
				this.context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			} else {
				this.context.runOnClient(mc -> mc.gui.setScreen(null));
			}
			this.context.waitTicks(1);
		}
		this.context.runOnClient(mc -> mc.gui.setScreen(null));
		this.context.waitTicks(10);
		long heap1 = this.heapMb();
		int tex1 = this.textures();
		String dom1 = this.js("String(document.getElementsByTagName('*').length)");
		opens.sort(null);
		log(String.format(java.util.Locale.ROOT, "PERF 40 rapid opens: first frame p50 %.1f ms max %.1f ms; heap %d -> %d MB, process %d -> %d MB, textures %d -> %d, page elements %s -> %s, inbox %s, error %s",
			opens.get(opens.size() / 2), opens.get(opens.size() - 1), heap0, heap1, proc0, processMb(), tex0, tex1, dom0, dom1, this.inbox(), this.js("String(window.__hubError)")));
		log("PERF 40 rapid opens, first frame by page (ms):" + byPage);
		this.check(tex1 == tex0, "no textures leak over 40 opens: " + tex0 + " -> " + tex1);
		long committed1 = this.context.computeOnClient(mc -> Runtime.getRuntime().totalMemory() >> 20);
		// The Java heap's committed size swings with G1 (not the hub's): what the hub could leak is the rest, native (Ultralight).
		long grew = processMb() - proc0 - (committed1 - committed0);
		log("PERF 40 rapid opens memory: Java heap committed " + committed0 + " -> " + committed1 + " MB, the rest of the process +" + grew + " MB");
		this.check(grew < 64, "native memory (process minus the Java heap) over 40 opens grows under 64 MB (process was +174 MB): +" + grew + " MB");
		this.check(opens.get(opens.size() - 1) < 150, "every open's first frame is under 150 ms (max " + opens.get(opens.size() - 1) + " ms)");
		this.check("undefined".equals(this.js("String(window.__hubError)")), "no page error after rapid opens");
		this.check(this.context.computeOnClient(mc -> mc.gui.screen() == null), "the hub is closed at the end");
	}

	private void dimension() {
		this.openClasses();
		this.ensureDraft();
		this.world.getServer().runCommand("execute in minecraft:the_nether run tp Player0 0 120 0");
		this.context.waitTicks(80);
		String what = this.context.computeOnClient(mc -> {
			var sc = mc.gui.screen();
			if (sc instanceof HubInventoryScreen h) {
				return "hub still open, its menu is the player's: " + (h.getMenu() == mc.player.inventoryMenu);
			}
			return "screen " + (sc == null ? "none" : sc.getClass().getSimpleName());
		});
		log("NOTE after a dimension change with the hub open: " + what);
		this.check(!what.contains("menu is the player's: false"), "the hub never stays open on the old player's menu after a dimension change: " + what);
		this.shot("audit_20_after_dimension_change");
		this.closeHub();
		this.world.getServer().runCommand("execute in minecraft:overworld run tp Player0 0 100 0");
		this.context.waitTicks(80);
	}

	private void death() {
		this.openClasses();
		this.ensureDraft();
		this.world.getServer().runCommand("kill Player0");
		this.context.waitTicks(20);
		String what = this.context.computeOnClient(mc -> mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName());
		log("NOTE after death with the hub open, the screen is " + what);
		this.context.runOnClient(mc -> mc.player.respawn());
		this.context.waitTicks(60);
		this.openClasses();
		this.ensureDraft();
		this.hoverPage(".dr-hand .hc[data-hand=\"0\"] .hc-art");
		this.context.waitTicks(6);
		this.align("after death and respawn");
		this.shot("audit_21_after_respawn");
		this.closeHub();
	}
}
