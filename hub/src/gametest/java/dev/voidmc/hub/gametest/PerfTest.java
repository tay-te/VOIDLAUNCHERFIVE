package dev.voidmc.hub.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.voidmc.hub.HubConfig;
import dev.voidmc.hub.layout.HubLayout;
import dev.voidmc.hub.layout.Rect;
import dev.voidmc.hub.map.HubMap;
import dev.voidmc.hub.model.Json;
import dev.voidmc.hub.screen.HubChestScreen;
import dev.voidmc.hub.screen.HubInventoryScreen;
import dev.voidmc.hub.ui.HubStats;
import dev.voidmc.hub.ui.HubView;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/**
 * The perf regression gate: the VOID inventory against vanilla's, on one graphics backend, with
 * vsync off and the frame rate uncapped so the true per-frame cost shows.
 *
 * <p>Scenarios run in interleaved rounds (vanilla, VOID, vanilla, VOID, ...) so that load from
 * anything else on the machine lands on both sides, not on whichever ran last.
 */
public final class PerfTest {

	// Budgets (VOID minus vanilla, same scenario shape).
	static final double BUDGET_AVG_MS = 1.0;
	static final double BUDGET_P99_MS = 2.0;
	static final double BUDGET_HITCH_MS = 33.0;
	static final double BUDGET_FIRST_OPEN_MS = 300.0;
	static final double BUDGET_REOPEN_MS = 150.0;
	// The Map page with VOID Atlas, against Atlas's own map screen panned the same way.
	static final double BUDGET_MAP_AVG_MS = 1.0;
	static final double BUDGET_MAP_P99_MS = 2.0;

	private final ClientGameTestContext context;
	private final TestSingleplayerContext world;
	private final TestWorld.Spot spot;
	private final Ui ui;
	private final String backend = System.getProperty("voidhub.test.backend", "opengl");
	private final double seconds = Double.parseDouble(System.getProperty("voidhub.test.seconds", "10"));
	private final int rounds = Integer.getInteger("voidhub.test.rounds", 4);
	private final Path out = Path.of(System.getProperty("voidhub.test.out", "perf"));
	private final Path docs = Path.of(System.getProperty("voidhub.test.docs", "docs"));
	/** VOID Atlas installed (the run's -Patlas): the Map page hosts its map, and is measured. */
	private boolean atlas;

	/** Accumulated raw frames per scenario across rounds. */
	private final Map<String, Samples> samples = new LinkedHashMap<>();

	public PerfTest(ClientGameTestContext context, TestSingleplayerContext world, TestWorld.Spot spot) {
		this.context = context;
		this.world = world;
		this.spot = spot;
		this.ui = new Ui(context, world);
	}

	static final class Samples {
		final List<Long> interval = new ArrayList<>();
		final List<Long> render = new ArrayList<>();
		final List<Long> gpu = new ArrayList<>();
		final List<Boolean> tick = new ArrayList<>();
		final List<HubStats.Frame> hub = new ArrayList<>();
		double wallSeconds;
		final List<Double> roundAvg = new ArrayList<>();
		final List<Dist> rounds = new ArrayList<>();

		long[] arr(List<Long> l) {
			return l.stream().mapToLong(Long::longValue).toArray();
		}
	}

	public void run() {
		System.out.println("[voidhub-perf] backend=" + this.backend + " seconds=" + this.seconds + " rounds=" + this.rounds);
		String gpuName = this.context.computeOnClient(mc -> { var d = RenderSystem.getDevice().getDeviceInfo(); return d.name() + ", " + d.backendName() + " (" + d.driverInfo() + ")"; });
		String api = this.context.computeOnClient(mc -> RenderSystem.getDevice().getClass().getSimpleName());
		int guiScale = this.context.computeOnClient(mc -> mc.getWindow().getGuiScale());
		HubConfig.setHideHud(false);  // like-for-like: vanilla's inventory draws the HUD under it
		this.atlas = this.context.computeOnClient(mc -> HubMap.available());
		System.out.println("[voidhub-perf] VOID Atlas " + (this.atlas ? "installed: the Map page is measured against Atlas's own map screen" : "not installed"));
		int loadBefore = Memory.cpuLoadPercent();

		// ---- one-off costs: Ultralight start-up and the first layout, then the first open ---------
		// The shipped config pre-warms when the world is joined: Ultralight starts and lays the
		// inventory out at the window size on its own thread. The perf run turns that off so it can
		// do the same thing here, measured, with process memory read before and after.
		long privBefore = Memory.privateBytes();
		long wsBefore = Memory.workingSet();
		long heapBefore = this.context.computeOnClient(mc -> Memory.heapUsed());
		boolean wasCreated = this.context.computeOnClient(mc -> HubView.isCreated());
		double prewarmMs = this.context.computeOnClient(mc -> {
			long t0 = System.nanoTime();
			var w = mc.getWindow();
			HubLayout l = HubLayout.inventory(w.getWidth(), w.getHeight(), w.getGuiScale());
			HubView.prewarm(w.getWidth(), w.getHeight(), l.u, l.toJson());
			HubView.get().awaitPainted(10_000_000_000L);
			return (System.nanoTime() - t0) / 1e6;
		});
		this.context.waitTicks(10);
		long privAfter = Memory.privateBytes();
		long wsAfter = Memory.workingSet();
		long heapAfter = this.context.computeOnClient(mc -> Memory.heapUsed());
		HubStats st = HubStats.INSTANCE;
		double initMs = st.initTotalNs / 1e6;
		double coldOpen = this.openLatency(() -> this.ui.pressInventory(), HubInventoryScreen.class);
		this.context.waitTicks(10);
		this.closeScreen();

		// ---- open / close latency ----------------------------------------------------------------
		List<Double> vanillaOpen = new ArrayList<>();
		List<Double> voidOpen = new ArrayList<>();
		List<Double> voidOpenRShift = new ArrayList<>();
		List<Double> voidInitToFrame = new ArrayList<>();
		List<Double> voidChestOpen = new ArrayList<>();
		List<Double> vanillaChestOpen = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			HubConfig.setReplaceInventory(false);
			vanillaOpen.add(this.openLatency(() -> this.ui.pressInventory(), InventoryScreen.class));
			this.closeScreen();
			HubConfig.setReplaceInventory(true);
			voidOpen.add(this.openLatency(() -> this.ui.pressInventory(), HubInventoryScreen.class));
			voidInitToFrame.add((st.lastScreenFirstFrameNs - st.lastScreenInitNs) / 1e6);
			this.closeScreen();
			voidOpenRShift.add(this.openLatency(() -> this.ui.pressKey(InputConstants.KEY_RSHIFT), HubInventoryScreen.class));
			this.closeScreen();
		}
		for (int i = 0; i < 5; i++) {
			HubConfig.setReplaceInventory(false);
			vanillaChestOpen.add(this.openLatency(() -> this.ui.openChestNoSettle(this.spot.chest()), ContainerScreen.class));
			this.closeScreen();
			HubConfig.setReplaceInventory(true);
			voidChestOpen.add(this.openLatency(() -> this.ui.openChestNoSettle(this.spot.chest()), HubChestScreen.class));
			this.closeScreen();
		}

		// ---- frame-time scenarios, interleaved ----------------------------------------------------
		double per = Math.max(2.0, this.seconds / this.rounds);
		for (int round = 0; round < this.rounds; round++) {
			this.scenario("idle", per, () -> {
			}, () -> {
			});
			this.scenario("vanilla_inventory", per, () -> this.openVanillaInventory(), this::closeScreen);
			this.scenario("void_inventory", per, () -> this.openVoidInventory(), this::closeScreen);
			this.scenario("vanilla_inventory_hover", per, () -> {
				this.openVanillaInventory();
				this.startVanillaSweep(166);
			}, () -> {
				FrameHooks.sweepPath = null;
				this.closeScreen();
			});
			this.scenario("void_inventory_hover", per, () -> {
				this.openVoidInventory();
				this.startSweep(false);
			}, () -> {
				FrameHooks.sweepPath = null;
				this.closeScreen();
			});
			this.scenario("vanilla_chest", per, () -> this.openChest(false), this::closeScreen);
			this.scenario("void_chest", per, () -> this.openChest(true), this::closeScreen);
			this.scenario("vanilla_chest_hover", per, () -> {
				this.openChest(false);
				this.startVanillaSweep(114 + 3 * 18);
			}, () -> {
				FrameHooks.sweepPath = null;
				this.closeScreen();
			});
			this.scenario("void_chest_hover", per, () -> {
				this.openChest(true);
				this.startSweep(true);
			}, () -> {
				FrameHooks.sweepPath = null;
				this.closeScreen();
			});
			if (!this.atlas) {
				this.scenario("void_map_standin", per, () -> this.openVoidMap(), this::closeScreen);
			}
		}
		// Baselines: Ultralight's work on the render thread (sync: what a single-threaded
		// integration pays), and on top of that the binding's original whole-surface readback.
		double baseline = Math.max(5.0, per);
		this.scenario("void_inventory_hover_sync", baseline, () -> {
			this.context.runOnClient(mc -> HubView.get().setSync(true));
			this.openVoidInventory();
			this.startSweep(false);
		}, () -> {
			FrameHooks.sweepPath = null;
			this.closeScreen();
			this.context.runOnClient(mc -> HubView.get().setSync(false));
		});
		this.scenario("void_inventory_hover_sync_fullreadback", baseline, () -> {
			this.context.runOnClient(mc -> {
				HubView.get().setSync(true);
				HubView.get().setUploadMode(HubView.UploadMode.FULL);
			});
			this.openVoidInventory();
			this.startSweep(false);
		}, () -> {
			FrameHooks.sweepPath = null;
			this.closeScreen();
			this.context.runOnClient(mc -> {
				HubView.get().setSync(false);
				HubView.get().setUploadMode(HubView.UploadMode.DIRTY);
			});
		});

		// ---- the Map page with VOID Atlas ---------------------------------------------------------
		MapNumbers map = this.atlas ? this.mapScenarios(per) : null;

		// ---- report ------------------------------------------------------------------------------
		Report r = new Report();
		r.atlas = this.atlas;
		r.map = map;
		r.cpuLoadBefore = loadBefore;
		r.cpuLoadAfter = Memory.cpuLoadPercent();
		r.backend = this.backend;
		r.api = api;
		r.gpu = gpuName;
		r.guiScale = guiScale;
		r.coldOpenMs = coldOpen;
		r.coldWasPrewarmed = wasCreated;
		r.initMs = initMs;
		r.initBreakdown = String.format(Locale.ROOT, "natives %.1f, renderer %.1f, view %.1f, page %.1f ms",
			st.loadNativesNs / 1e6, st.createRendererNs / 1e6, st.createViewNs / 1e6, st.pageLoadNs / 1e6);
		r.firstOpenMs = coldOpen;
		r.prewarmMs = prewarmMs;
		r.privateDeltaMb = (privAfter - privBefore) / 1048576.0;
		r.privateAfterMb = privAfter / 1048576.0;
		r.workingSetDeltaMb = (wsAfter - wsBefore) / 1048576.0;
		r.workingSetAfterMb = wsAfter / 1048576.0;
		r.heapDeltaMb = (heapAfter - heapBefore) / 1048576.0;
		r.vanillaOpen = Dist.of(toArray(vanillaOpen));
		r.voidOpen = Dist.of(toArray(voidOpen));
		r.voidOpenRShift = Dist.of(toArray(voidOpenRShift));
		r.voidInitToFrame = Dist.of(toArray(voidInitToFrame));
		r.vanillaChestOpen = Dist.of(toArray(vanillaChestOpen));
		r.voidChestOpen = Dist.of(toArray(voidChestOpen));
		for (Map.Entry<String, Samples> e : this.samples.entrySet()) {
			r.scenarios.put(e.getKey(), Scenario.of(e.getKey(), e.getValue()));
		}
		r.samples = this.samples;
		r.evaluate();
		try {
			r.write(this.out, this.docs);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		System.out.println(r.text());
		if (!r.failures.isEmpty() && !Boolean.getBoolean("voidhub.test.noGate")) {
			throw new AssertionError("VOID inventory over budget on " + this.backend + ": " + r.failures);
		}
	}

	/** What the Map page section measured (with Atlas). */
	record MapNumbers(double firstOpenMs, double widgetCreateMs, Dist reopen, Dist atlasScreenOpen) {
	}

	/**
	 * The Map page against Atlas's own map screen (M), both idle and both panned by the same drag
	 * (the left button held while the pointer circles over the map, every frame), interleaved round
	 * by round like the inventory scenarios. Atlas draws the same page in both; what differs is the
	 * hub around it (Ultralight's islands and window, the hub's input routing).
	 *
	 * <p>Like for like, Atlas's screen runs with its live backdrop on (the world drawn behind the map
	 * every frame, as the hub draws it behind every page); {@code atlas_map_pan_frozen} is Atlas's
	 * default (a still of the world, the world itself skipped), for reference, not gated.
	 */
	private MapNumbers mapScenarios(double per) {
		HubStats st = HubStats.INSTANCE;
		// First Map page: the widget is made on its first frame (lazily, never at hub open).
		this.context.runOnClient(mc -> HubInventoryScreen.rememberPage("map"));
		double first = this.openLatency(() -> this.ui.pressKey(InputConstants.KEY_RSHIFT), HubInventoryScreen.class);
		double createMs = this.context.computeOnClient(mc -> HubMap.lastCreateNs / 1e6);
		this.context.waitTicks(10);
		this.closeMap();
		List<Double> reopen = new ArrayList<>();
		List<Double> atlasOpen = new ArrayList<>();
		Class<? extends Screen> atlasScreen = AtlasHooks.atlasScreenClass();
		for (int i = 0; i < 10; i++) {
			this.context.runOnClient(mc -> HubInventoryScreen.rememberPage("map"));
			reopen.add(this.openLatency(() -> this.ui.pressKey(InputConstants.KEY_RSHIFT), HubInventoryScreen.class));
			this.closeMap();
			atlasOpen.add(this.openLatency(() -> this.context.runOnClient(mc -> AtlasHooks.openAtlasMap()), atlasScreen));
			this.closeMap();
		}
		double[][] path = this.panPath();
		boolean liveWas = this.context.computeOnClient(mc -> AtlasHooks.setLiveBackdrop(true));
		for (int round = 0; round < this.rounds; round++) {
			this.scenario("atlas_map", per, () -> this.openAtlasMap(atlasScreen), this::closeMap);
			this.scenario("void_map", per, () -> this.openVoidMap(), this::closeMap);
			this.scenario("atlas_map_pan", per, () -> {
				this.openAtlasMap(atlasScreen);
				this.startPan(path);
			}, this::stopPanAndClose);
			this.scenario("void_map_pan", per, () -> {
				this.openVoidMap();
				this.startPan(path);
			}, this::stopPanAndClose);
			this.scenario("atlas_map_pan_frozen", per, () -> {
				this.context.runOnClient(mc -> AtlasHooks.setLiveBackdrop(false));
				this.openAtlasMap(atlasScreen);
				this.startPan(path);
			}, () -> {
				this.stopPanAndClose();
				this.context.runOnClient(mc -> AtlasHooks.setLiveBackdrop(true));
			});
		}
		this.context.runOnClient(mc -> AtlasHooks.setLiveBackdrop(liveWas));
		return new MapNumbers(first, createMs, Dist.of(toArray(reopen)), Dist.of(toArray(atlasOpen)));
	}

	private void openVoidMap() {
		this.context.runOnClient(mc -> HubInventoryScreen.rememberPage("map"));
		this.ui.pressKey(InputConstants.KEY_RSHIFT);
		this.context.waitForScreen(HubInventoryScreen.class);
		HubLayout l = this.ui.layout();
		this.ui.moveTo(l.window.x() + 12 * l.u, l.window.y() + 12 * l.u);  // on the window, off the map
	}

	private void openAtlasMap(Class<? extends Screen> type) {
		this.context.runOnClient(mc -> AtlasHooks.openAtlasMap());
		this.context.waitForScreen(type);
		this.ui.moveTo(HubGameTest.WIDTH / 2.0, 30);
	}

	/**
	 * A circle over the map's left half, in window px: inside the map on the hub's Map page and on
	 * Atlas's own screen alike at this window size, so both are panned by the same pointer path.
	 */
	private double[][] panPath() {
		HubLayout l = this.context.computeOnClient(mc -> HubLayout.map(mc.getWindow().getWidth(), mc.getWindow().getHeight(), mc.getWindow().getGuiScale()));
		double cx = l.window.x() + l.window.w() * 0.38;
		double cy = l.window.y() + l.window.h() * 0.5;
		double r = l.window.h() * 0.15;
		double[][] path = new double[48][];
		for (int i = 0; i < path.length; i++) {
			double a = i * 2 * Math.PI / path.length;
			path[i] = new double[] {cx + r * Math.cos(a), cy + r * Math.sin(a)};
		}
		return path;
	}

	private void startPan(double[][] path) {
		this.ui.moveTo(path[0][0], path[0][1]);
		this.context.waitTicks(1);
		this.ui.press(Ui.LEFT, 0);
		FrameHooks.sweepRate = 30.0;
		FrameHooks.sweepPath = path;
	}

	private void stopPanAndClose() {
		FrameHooks.sweepPath = null;
		this.ui.release(Ui.LEFT, 0);
		this.context.waitTicks(1);
		this.closeMap();
	}

	/** Esc until no screen is open: the map's own first (a card it opened), then the screen. */
	private void closeMap() {
		for (int i = 0; i < 4; i++) {
			if (this.context.computeOnClient(mc -> mc.gui.screen() == null)) {
				break;
			}
			this.context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			this.context.waitTicks(2);
		}
		this.context.waitForScreen(null);
		this.context.waitTicks(2);
	}

	static double[] toArray(List<Double> l) {
		return l.stream().mapToDouble(Double::doubleValue).toArray();
	}

	// ---- scenario plumbing ----------------------------------------------------------------------

	private void scenario(String name, double secs, Runnable setup, Runnable teardown) {
		setup.run();
		this.context.waitTicks(10);  // let the first paints and any GC from opening settle
		long hubFrom = HubStats.INSTANCE.frameCount();
		this.context.runOnClient(mc -> FrameRecorder.INSTANCE.start());
		long t0 = System.nanoTime();
		this.context.waitTicks((int) Math.round(secs * 20));
		this.context.runOnClient(mc -> FrameRecorder.INSTANCE.stop());
		double wall = (System.nanoTime() - t0) / 1e9;
		FrameRecorder fr = FrameRecorder.INSTANCE;
		Samples s = this.samples.computeIfAbsent(name, k -> new Samples());
		long[] iv = this.context.computeOnClient(mc -> fr.intervals());
		long[] rd = this.context.computeOnClient(mc -> fr.renders());
		long[] gp = this.context.computeOnClient(mc -> fr.gpus());
		boolean[] tk = this.context.computeOnClient(mc -> fr.ticks());
		HubStats.Frame[] hub = this.context.computeOnClient(mc -> HubStats.INSTANCE.since(hubFrom));
		double sum = 0;
		for (int i = 0; i < iv.length; i++) {
			s.interval.add(iv[i]);
			s.render.add(rd[i]);
			s.gpu.add(gp[i]);
			s.tick.add(tk[i]);
			sum += iv[i];
		}
		s.roundAvg.add(iv.length == 0 ? 0 : sum / iv.length / 1e6);
		s.rounds.add(Dist.ofNanos(iv));
		s.hub.addAll(List.of(hub));
		s.wallSeconds += wall;
		System.out.printf(Locale.ROOT, "[voidhub-perf] %-32s %6d frames in %.1f s  avg %.3f ms  (%.0f fps)%n", name, iv.length, wall,
			iv.length == 0 ? 0 : sum / iv.length / 1e6, iv.length / Math.max(1e-9, sum / 1e9));
		teardown.run();
		this.context.waitTicks(5);
	}

	private void openVanillaInventory() {
		HubConfig.setReplaceInventory(false);
		this.ui.pressInventory();
		this.context.waitForScreen(InventoryScreen.class);
		this.ui.moveTo(HubGameTest.WIDTH / 2.0, 40);
	}

	private void openVoidInventory() {
		HubConfig.setReplaceInventory(true);
		this.ui.pressInventory();
		this.context.waitForScreen(HubInventoryScreen.class);
		this.ui.parkMouse();
	}

	private void openChest(boolean voidScreen) {
		HubConfig.setReplaceInventory(voidScreen);
		this.ui.openChest(this.spot.chest());
		this.context.waitForScreen(voidScreen ? HubChestScreen.class : ContainerScreen.class);
		if (voidScreen) {
			this.ui.parkMouse();
		} else {
			this.ui.moveTo(HubGameTest.WIDTH / 2.0, 40);
		}
	}

	private void closeScreen() {
		this.context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		this.context.waitForScreen(null);
		this.context.waitTicks(2);
	}

	/**
	 * The same sweep over vanilla's own slots (its panel centred, recipe book closed), so the
	 * hover comparison is like for like: vanilla draws its highlight and tooltip per slot.
	 */
	private void startVanillaSweep(int imageH) {
		double[][] path = this.context.computeOnClient(mc -> {
			var w = mc.getWindow();
			int gs = w.getGuiScale();
			int left = (w.getGuiScaledWidth() - 176) / 2;
			int top = (w.getGuiScaledHeight() - imageH) / 2;
			List<double[]> pts = new ArrayList<>();
			for (var slot : mc.player.containerMenu.slots) {
				pts.add(new double[] {(left + slot.x + 8) * gs, (top + slot.y + 8) * gs});
			}
			return pts.toArray(new double[0][]);
		});
		FrameHooks.sweepRate = 30.0;
		FrameHooks.sweepPath = path;
	}

	/** Sweeps the pointer through every well, row by row, ~30 wells a second. */
	private void startSweep(boolean chest) {
		HubLayout l = this.ui.layout();
		List<double[]> path = new ArrayList<>();
		for (HubLayout.Cell c : l.cells()) {
			Rect r = c.cell();
			path.add(new double[] {r.centerX(), r.centerY()});
		}
		FrameHooks.sweepRate = 30.0;
		FrameHooks.sweepPath = path.toArray(new double[0][]);
	}

	/** Key (or use) to the end of the first frame that shows a screen of {@code type}. */
	private double openLatency(Runnable press, Class<? extends Screen> type) {
		long[] t1 = {0};
		this.context.runOnClient(mc -> FrameRecorder.INSTANCE.afterFrame = () -> {
			Screen s = mc.gui.screen();
			if (t1[0] == 0 && s != null && s.getClass() == type) {
				t1[0] = System.nanoTime();
			}
		});
		long t0 = this.context.computeOnClient(mc -> System.nanoTime());
		press.run();
		this.context.waitFor(mc -> t1[0] != 0, 200);
		this.context.runOnClient(mc -> FrameRecorder.INSTANCE.afterFrame = null);
		return (t1[0] - t0) / 1e6;
	}

	// ---- results --------------------------------------------------------------------------------

	record Scenario(String name, int frames, double fps, Dist interval, Dist intervalNoTick, Dist render, Dist gpu, int hitches,
		int renderHitches, Dist hubRenderThread, double postMs, double waitMs, double copyMs, double uploadMs, double uiUpdateMs,
		double uiRenderMs, double uiScriptMs, Dist uiPerChange, long uploads, double uploadKbAvg, long busyFrames, List<Double> roundAvg,
		Dist mapDraw) {

		static Scenario of(String name, Samples s) {
			long[] iv = s.arr(s.interval);
			long[] rd = s.arr(s.render);
			long[] gp = s.arr(s.gpu);
			List<Long> noTick = new ArrayList<>();
			int hitches = 0;
			int renderHitches = 0;
			double sum = 0;
			for (int i = 0; i < iv.length; i++) {
				sum += iv[i];
				if (!s.tick.get(i)) {
					noTick.add(iv[i]);
				}
				if (iv[i] > BUDGET_HITCH_MS * 1e6) {
					hitches++;
				}
				if (rd[i] > BUDGET_HITCH_MS * 1e6) {
					renderHitches++;
				}
			}
			double post = 0;
			double wait = 0;
			double copy = 0;
			double up = 0;
			double uu = 0;
			double ur = 0;
			double us = 0;
			long uploads = 0;
			long busy = 0;
			double bytes = 0;
			long[] tot = new long[s.hub.size()];
			long[] mapNs = new long[s.hub.size()];
			List<Long> uiChanges = new ArrayList<>();
			for (int i = 0; i < s.hub.size(); i++) {
				HubStats.Frame f = s.hub.get(i);
				post += f.postNs;
				wait += f.waitNs;
				copy += f.copyNs;
				up += f.uploadNs;
				uu += f.updateNs;
				ur += f.renderNs;
				us += f.scriptNs;
				tot[i] = f.totalNs();
				mapNs[i] = f.mapNs;
				if (f.bytes > 0) {
					uploads++;
					bytes += f.bytes;
				}
				if (f.busy) {
					busy++;
				}
				if (f.scriptNs > 0) {
					uiChanges.add(f.uiNs());
				}
			}
			int hn = Math.max(1, s.hub.size());
			return new Scenario(name, iv.length, iv.length / Math.max(1e-9, sum / 1e9), Dist.ofNanos(iv),
				Dist.ofNanos(noTick.stream().mapToLong(Long::longValue).toArray()), Dist.ofNanos(rd), Dist.ofNanos(gp), hitches, renderHitches,
				s.hub.isEmpty() ? Dist.EMPTY : Dist.ofNanos(tot), post / hn / 1e6, wait / hn / 1e6, copy / hn / 1e6, up / hn / 1e6,
				uu / hn / 1e6, ur / hn / 1e6, us / hn / 1e6, Dist.ofNanos(uiChanges.stream().mapToLong(Long::longValue).toArray()),
				uploads, uploads == 0 ? 0 : bytes / uploads / 1024.0, busy, s.roundAvg, s.hub.isEmpty() ? Dist.EMPTY : Dist.ofNanos(mapNs));
		}

		String json() {
			Json.Writer w = Json.object();
			w.put("frames", this.frames).put("fps", Dist.r(this.fps));
			w.raw("frameMs", this.interval.json()).raw("frameMsNoTick", this.intervalNoTick.json());
			w.raw("renderCpuMs", this.render.json()).raw("gpuMs", this.gpu.json());
			w.put("hitchesOver33ms", this.hitches).put("renderHitchesOver33ms", this.renderHitches);
			w.raw("hubRenderThreadMs", this.hubRenderThread.json());
			w.raw("hubRenderThreadBreakdownMs", Json.object().put("post", Dist.r(this.postMs)).put("wait", Dist.r(this.waitMs))
				.put("copy", Dist.r(this.copyMs)).put("upload", Dist.r(this.uploadMs)).end());
			w.raw("ultralightUiThreadMsPerFrame", Json.object().put("update", Dist.r(this.uiUpdateMs)).put("render", Dist.r(this.uiRenderMs))
				.put("script", Dist.r(this.uiScriptMs)).end());
			w.raw("ultralightMsPerPageChange", this.uiPerChange.json());
			w.put("uploads", this.uploads).put("uploadKbAvg", Dist.r(this.uploadKbAvg)).put("busySkips", this.busyFrames);
			w.raw("atlasWidgetDrawMs", this.mapDraw.json());
			w.raw("roundAvgMs", "[" + String.join(",", this.roundAvg.stream().map(d -> Json.number(Dist.r(d))).toList()) + "]");
			return w.end();
		}
	}

	static final class Report {
		String backend;
		String api;
		String gpu;
		int guiScale;
		double coldOpenMs;
		boolean coldWasPrewarmed;
		double initMs;
		String initBreakdown;
		double firstOpenMs;
		double prewarmMs;
		double privateDeltaMb;
		double privateAfterMb;
		double workingSetDeltaMb;
		double workingSetAfterMb;
		double heapDeltaMb;
		Dist vanillaOpen;
		Dist voidOpen;
		Dist voidOpenRShift;
		Dist voidInitToFrame;
		Dist vanillaChestOpen;
		Dist voidChestOpen;
		final Map<String, Scenario> scenarios = new LinkedHashMap<>();
		final List<String> failures = new ArrayList<>();
		double avgDelta;
		double p99Delta;
		double chestAvgDelta;
		double chestP99Delta;
		double hoverAvgDelta;
		double hoverP99Delta;
		int cpuLoadBefore;
		int cpuLoadAfter;
		boolean atlas;
		MapNumbers map;
		double mapAvgDelta;
		double mapP99Delta;
		double mapPanAvgDelta;
		double mapPanP99Delta;

		/**
		 * VOID minus vanilla, round by round (the rounds interleave the two), and the median of
		 * those: a load spike from something else on the machine lands in one round, not in the
		 * verdict, while a real regression shows in every round.
		 */
		static double[] pairedMedian(Samples a, Samples b, boolean p99) {
			int n = Math.min(a.rounds.size(), b.rounds.size());
			double[] d = new double[n];
			for (int i = 0; i < n; i++) {
				d[i] = p99 ? a.rounds.get(i).p99() - b.rounds.get(i).p99() : a.rounds.get(i).avg() - b.rounds.get(i).avg();
			}
			double[] sorted = d.clone();
			java.util.Arrays.sort(sorted);
			double median = n == 0 ? 0 : n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2;
			double[] out = new double[n + 1];
			out[0] = median;
			System.arraycopy(d, 0, out, 1, n);
			return out;
		}

		Map<String, Samples> samples;
		final Map<String, double[]> paired = new LinkedHashMap<>();

		void evaluate() {
			Scenario van = this.scenarios.get("vanilla_inventory");
			Scenario vo = this.scenarios.get("void_inventory");
			Scenario hov = this.scenarios.get("void_inventory_hover");
			Scenario vch = this.scenarios.get("vanilla_chest");
			Scenario och = this.scenarios.get("void_chest");
			this.paired.put("inventoryAvg", pairedMedian(this.samples.get("void_inventory"), this.samples.get("vanilla_inventory"), false));
			this.paired.put("inventoryP99", pairedMedian(this.samples.get("void_inventory"), this.samples.get("vanilla_inventory"), true));
			this.paired.put("hoverAvg", pairedMedian(this.samples.get("void_inventory_hover"), this.samples.get("vanilla_inventory_hover"), false));
			this.paired.put("hoverP99", pairedMedian(this.samples.get("void_inventory_hover"), this.samples.get("vanilla_inventory_hover"), true));
			this.paired.put("chestAvg", pairedMedian(this.samples.get("void_chest"), this.samples.get("vanilla_chest"), false));
			this.paired.put("chestP99", pairedMedian(this.samples.get("void_chest"), this.samples.get("vanilla_chest"), true));
			this.avgDelta = this.paired.get("inventoryAvg")[0];
			this.p99Delta = this.paired.get("inventoryP99")[0];
			this.chestAvgDelta = this.paired.get("chestAvg")[0];
			this.chestP99Delta = this.paired.get("chestP99")[0];
			if (this.avgDelta > BUDGET_AVG_MS) {
				this.failures.add(String.format(Locale.ROOT, "avg frame +%.3f ms > %.1f", this.avgDelta, BUDGET_AVG_MS));
			}
			if (this.p99Delta > BUDGET_P99_MS) {
				this.failures.add(String.format(Locale.ROOT, "p99 frame +%.3f ms > %.1f", this.p99Delta, BUDGET_P99_MS));
			}
			Scenario vhov = this.scenarios.get("vanilla_inventory_hover");
			this.hoverAvgDelta = this.paired.get("hoverAvg")[0];
			this.hoverP99Delta = this.paired.get("hoverP99")[0];
			if (this.hoverAvgDelta > BUDGET_AVG_MS) {
				this.failures.add(String.format(Locale.ROOT, "hovering: avg frame +%.3f ms > %.1f", this.hoverAvgDelta, BUDGET_AVG_MS));
			}
			if (this.hoverP99Delta > BUDGET_P99_MS) {
				this.failures.add(String.format(Locale.ROOT, "hovering: p99 frame +%.3f ms > %.1f", this.hoverP99Delta, BUDGET_P99_MS));
			}
			// Hitches while hovering: any frame over 33 ms the VOID screen has that vanilla's, swept the
			// same way in the same interleaved rounds, did not. On a quiet machine vanilla has none,
			// so this is "no hitch at all"; on a busy one it does not blame the screen for the OS.
			if (hov.hitches > vhov.hitches) {
				this.failures.add(String.format(Locale.ROOT, "hover hitches: %d frames over 33 ms (vanilla while hovering: %d), worst %.1f ms",
					hov.hitches, vhov.hitches, hov.interval.max()));
			}
			if (this.firstOpenMs > BUDGET_FIRST_OPEN_MS) {
				this.failures.add(String.format(Locale.ROOT, "first open %.1f ms > %.0f", this.firstOpenMs, BUDGET_FIRST_OPEN_MS));
			}
			if (this.voidOpen.max() > BUDGET_REOPEN_MS) {
				this.failures.add(String.format(Locale.ROOT, "re-open worst %.1f ms > %.0f", this.voidOpen.max(), BUDGET_REOPEN_MS));
			}
			if (this.map != null) {
				this.evaluateMap();
			}
		}

		/** The Map page vs Atlas's own map screen, idle and panning; and its opens. */
		void evaluateMap() {
			this.paired.put("mapAvg", pairedMedian(this.samples.get("void_map"), this.samples.get("atlas_map"), false));
			this.paired.put("mapP99", pairedMedian(this.samples.get("void_map"), this.samples.get("atlas_map"), true));
			this.paired.put("mapPanAvg", pairedMedian(this.samples.get("void_map_pan"), this.samples.get("atlas_map_pan"), false));
			this.paired.put("mapPanP99", pairedMedian(this.samples.get("void_map_pan"), this.samples.get("atlas_map_pan"), true));
			this.mapAvgDelta = this.paired.get("mapAvg")[0];
			this.mapP99Delta = this.paired.get("mapP99")[0];
			this.mapPanAvgDelta = this.paired.get("mapPanAvg")[0];
			this.mapPanP99Delta = this.paired.get("mapPanP99")[0];
			if (this.mapPanAvgDelta > BUDGET_MAP_AVG_MS) {
				this.failures.add(String.format(Locale.ROOT, "map panning: avg frame +%.3f ms vs Atlas's screen > %.1f", this.mapPanAvgDelta, BUDGET_MAP_AVG_MS));
			}
			if (this.mapPanP99Delta > BUDGET_MAP_P99_MS) {
				this.failures.add(String.format(Locale.ROOT, "map panning: p99 frame +%.3f ms vs Atlas's screen > %.1f", this.mapPanP99Delta, BUDGET_MAP_P99_MS));
			}
			if (this.mapAvgDelta > BUDGET_MAP_AVG_MS) {
				this.failures.add(String.format(Locale.ROOT, "map idle: avg frame +%.3f ms vs Atlas's screen > %.1f", this.mapAvgDelta, BUDGET_MAP_AVG_MS));
			}
			Scenario pan = this.scenarios.get("void_map_pan");
			Scenario atlasPan = this.scenarios.get("atlas_map_pan");
			if (pan.hitches() > atlasPan.hitches()) {
				this.failures.add(String.format(Locale.ROOT, "map panning hitches: %d frames over 33 ms (Atlas's screen: %d), worst %.1f ms",
					pan.hitches(), atlasPan.hitches(), pan.interval().max()));
			}
			if (this.map.firstOpenMs() > BUDGET_FIRST_OPEN_MS) {
				this.failures.add(String.format(Locale.ROOT, "first Map page open %.1f ms > %.0f", this.map.firstOpenMs(), BUDGET_FIRST_OPEN_MS));
			}
			if (this.map.reopen().max() > BUDGET_REOPEN_MS) {
				this.failures.add(String.format(Locale.ROOT, "Map page re-open worst %.1f ms > %.0f", this.map.reopen().max(), BUDGET_REOPEN_MS));
			}
		}

		/** The report's name: the backend, and "-atlas" for the run with VOID Atlas installed. */
		String key() {
			return this.backend + (this.atlas ? "-atlas" : "");
		}

		String perRoundJson() {
			Json.Writer w = Json.object();
			for (Map.Entry<String, double[]> e : this.paired.entrySet()) {
				double[] v = e.getValue();
				StringBuilder sb = new StringBuilder("[");
				for (int i = 1; i < v.length; i++) {
					sb.append(i > 1 ? "," : "").append(Json.number(Dist.r(v[i])));
				}
				w.raw(e.getKey(), sb.append("]").toString());
			}
			return w.end();
		}

		String rounds(String key) {
			double[] v = this.paired.get(key);
			StringBuilder sb = new StringBuilder();
			for (int i = 1; i < v.length; i++) {
				sb.append(i > 1 ? ", " : "").append(signed(v[i]));
			}
			return sb.toString();
		}

		String verdict() {
			return this.failures.isEmpty() ? "PASS" : "FAIL";
		}

		void write(Path outDir, Path docsDir) throws IOException {
			Files.createDirectories(outDir);
			Json.Writer w = Json.object();
			w.put("backend", this.backend).put("device", this.api).put("gpu", this.gpu).put("guiScale", this.guiScale);
			w.put("window", HubGameTest.WIDTH + "x" + HubGameTest.HEIGHT);
			w.put("date", ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
			w.put("machineCpuLoadBefore", this.cpuLoadBefore).put("machineCpuLoadAfter", this.cpuLoadAfter);
			w.put("verdict", this.verdict());
			w.putStrings("failures", this.failures);
			w.raw("budget", Json.object().put("avgMs", BUDGET_AVG_MS).put("p99Ms", BUDGET_P99_MS).put("hitchMs", BUDGET_HITCH_MS)
				.put("firstOpenMs", BUDGET_FIRST_OPEN_MS).put("reopenMs", BUDGET_REOPEN_MS).end());
			w.raw("delta", Json.object().put("inventoryAvgMs", Dist.r(this.avgDelta)).put("inventoryP99Ms", Dist.r(this.p99Delta))
				.put("chestAvgMs", Dist.r(this.chestAvgDelta)).put("chestP99Ms", Dist.r(this.chestP99Delta))
				.put("inventoryHoverAvgMs", Dist.r(this.hoverAvgDelta)).put("inventoryHoverP99Ms", Dist.r(this.hoverP99Delta))
				.put("method", "median over interleaved rounds of (VOID round - vanilla round)").raw("perRound", this.perRoundJson()).end());
			w.raw("open", Json.object()
				.put("coldFirstOpenMs", Dist.r(this.coldOpenMs)).put("coldWasPrewarmed", this.coldWasPrewarmed)
				.put("ultralightInitMs", Dist.r(this.initMs)).put("initBreakdown", this.initBreakdown)
				.put("firstOpenMs", Dist.r(this.firstOpenMs)).put("prewarmMs", Dist.r(this.prewarmMs))
				.raw("vanillaE", this.vanillaOpen.json()).raw("voidE", this.voidOpen.json()).raw("voidRShift", this.voidOpenRShift.json())
				.raw("voidInitToFirstFrame", this.voidInitToFrame.json())
				.raw("vanillaChest", this.vanillaChestOpen.json()).raw("voidChest", this.voidChestOpen.json()).end());
			w.put("atlas", this.atlas);
			if (this.map != null) {
				w.raw("map", Json.object().put("firstOpenMs", Dist.r(this.map.firstOpenMs())).put("widgetCreateMs", Dist.r(this.map.widgetCreateMs()))
					.raw("reopen", this.map.reopen().json()).raw("atlasScreenOpen", this.map.atlasScreenOpen().json())
					.put("idleAvgDeltaMs", Dist.r(this.mapAvgDelta)).put("idleP99DeltaMs", Dist.r(this.mapP99Delta))
					.put("panAvgDeltaMs", Dist.r(this.mapPanAvgDelta)).put("panP99DeltaMs", Dist.r(this.mapPanP99Delta))
					.put("method", "median over interleaved rounds of (hub Map page round - Atlas map screen round)").end());
			}
			w.raw("memory", Json.object().put("privateBytesDeltaMb", Dist.r(this.privateDeltaMb)).put("privateBytesAfterMb", Dist.r(this.privateAfterMb))
				.put("workingSetDeltaMb", Dist.r(this.workingSetDeltaMb)).put("workingSetAfterMb", Dist.r(this.workingSetAfterMb))
				.put("heapDeltaMb", Dist.r(this.heapDeltaMb)).end());
			Json.Writer sc = Json.object();
			for (Map.Entry<String, Scenario> e : this.scenarios.entrySet()) {
				sc.raw(e.getKey(), e.getValue().json());
			}
			w.raw("scenarios", sc.end());
			Files.writeString(outDir.resolve(this.key() + ".json"), w.end(), StandardCharsets.UTF_8);
			this.updateDoc(docsDir.resolve("PERF.md"));
		}

		/** Rewrites this backend's generated section of docs/PERF.md, leaving the rest alone. */
		void updateDoc(Path doc) throws IOException {
			String begin = "<!-- perf:" + this.key() + ":begin -->";
			String end = "<!-- perf:" + this.key() + ":end -->";
			String section = begin + "\n" + this.markdown() + end;
			String text = Files.isRegularFile(doc) ? Files.readString(doc, StandardCharsets.UTF_8) : "# VOID Hub — performance\n\n";
			int a = text.indexOf(begin);
			int b = text.indexOf(end);
			if (a >= 0 && b > a) {
				text = text.substring(0, a) + section + text.substring(b + end.length());
			} else {
				text = text + "\n" + section + "\n";
			}
			Files.createDirectories(doc.getParent());
			Files.writeString(doc, text, StandardCharsets.UTF_8);
		}

		String markdown() {
			StringBuilder m = new StringBuilder();
			m.append("### ").append(this.backend.equals("vulkan") ? "Vulkan" : "OpenGL").append(this.atlas ? ", with VOID Atlas" : ", without VOID Atlas")
				.append(" — ").append(this.verdict())
				.append(" (generated ").append(ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append(")\n\n");
			m.append("Device: ").append(this.gpu).append(" · window ").append(HubGameTest.WIDTH).append("×").append(HubGameTest.HEIGHT)
				.append(" · GUI scale ").append(this.guiScale).append(" · machine CPU load before/after the run ")
				.append(this.cpuLoadBefore).append("% / ").append(this.cpuLoadAfter).append("% · vsync off, frame rate uncapped\n\n");
			if (!this.failures.isEmpty()) {
				m.append("**Over budget:** ").append(String.join("; ", this.failures)).append("\n\n");
			}
			m.append("| scenario | frames | fps | frame avg | p50 | p95 | p99 | max | render CPU avg | GPU avg | hitches >33 ms |\n");
			m.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
			for (Scenario s : this.scenarios.values()) {
				m.append(String.format(Locale.ROOT, "| %s | %d | %.0f | %.3f | %.3f | %.3f | %.3f | %.2f | %.3f | %s | %d |\n", s.name(), s.frames(),
					s.fps(), s.interval().avg(), s.interval().p50(), s.interval().p95(), s.interval().p99(), s.interval().max(),
					s.render().avg(), s.gpu().n() == 0 ? "n/a" : String.format(Locale.ROOT, "%.3f", s.gpu().avg()), s.hitches()));
			}
			m.append("\n**VOID vs vanilla** (median of the per-round differences; rounds in brackets): inventory avg ").append(signed(this.avgDelta)).append(" ms [").append(this.rounds("inventoryAvg")).append("], p99 ").append(signed(this.p99Delta))
				.append(" ms [").append(this.rounds("inventoryP99")).append("]")
				.append(" · inventory while hovering avg ").append(signed(this.hoverAvgDelta)).append(" ms [").append(this.rounds("hoverAvg"))
				.append("], p99 ").append(signed(this.hoverP99Delta)).append(" ms [").append(this.rounds("hoverP99")).append("]")
				.append(" · chest avg ").append(signed(this.chestAvgDelta)).append(" ms, p99 ").append(signed(this.chestP99Delta)).append(" ms\n\n");
			if (this.map != null) {
				m.append("**Map page vs Atlas's own map screen**, both drawing the live world behind the map (median of the per-round differences; `atlas_map_pan_frozen` is Atlas's default still backdrop, not gated): idle avg ").append(signed(this.mapAvgDelta))
					.append(" ms [").append(this.rounds("mapAvg")).append("], p99 ").append(signed(this.mapP99Delta)).append(" ms [").append(this.rounds("mapP99"))
					.append("] · panning avg ").append(signed(this.mapPanAvgDelta)).append(" ms [").append(this.rounds("mapPanAvg")).append("], p99 ")
					.append(signed(this.mapPanP99Delta)).append(" ms [").append(this.rounds("mapPanP99")).append("]\n\n");
			}
			m.append("| VOID cost (ms / frame) | render thread: avg / p99 / max | post | wait | copy | upload | uploads | avg upload | Ultralight on its thread | per page change: avg / p99 | Atlas widget draw avg / p99 |\n");
			m.append("|---|---|---:|---:|---:|---:|---:|---:|---:|---|---|\n");
			for (Scenario s : this.scenarios.values()) {
				if (!s.name().startsWith("void")) {
					continue;
				}
				m.append(String.format(Locale.ROOT, "| %s | %.3f / %.3f / %.2f | %.3f | %.3f | %.3f | %.3f | %d | %.0f KB | %.3f | %.2f / %.2f | %s |\n", s.name(),
					s.hubRenderThread().avg(), s.hubRenderThread().p99(), s.hubRenderThread().max(), s.postMs(), s.waitMs(), s.copyMs(), s.uploadMs(),
					s.uploads(), s.uploadKbAvg(), s.uiUpdateMs() + s.uiRenderMs() + s.uiScriptMs(), s.uiPerChange().avg(), s.uiPerChange().p99(),
					s.mapDraw().max() > 0 ? String.format(Locale.ROOT, "%.3f / %.3f", s.mapDraw().avg(), s.mapDraw().p99()) : "—"));
			}
			m.append("\n| open latency (ms, key → first painted frame) | avg | p50 | max |\n|---|---:|---:|---:|\n");
			m.append(row("vanilla inventory (E)", this.vanillaOpen)).append(row("VOID inventory (E)", this.voidOpen))
				.append(row("VOID inventory (R-Shift)", this.voidOpenRShift)).append(row("VOID screen init → first frame", this.voidInitToFrame))
				.append(row("vanilla chest", this.vanillaChestOpen)).append(row("VOID chest", this.voidChestOpen));
			if (this.map != null) {
				m.append(row("Map page (R-Shift), re-open", this.map.reopen())).append(row("Atlas's own map screen", this.map.atlasScreenOpen()));
				m.append(String.format(Locale.ROOT, "\nFirst Map page open: %.1f ms key → first painted frame (gated), of which making Atlas's widget %.2f ms "
					+ "(lazily, on the first Map frame; kept for every later open).\n", this.map.firstOpenMs(), this.map.widgetCreateMs()));
			}
			m.append(String.format(Locale.ROOT, "\n"
				+ "First open after pre-warm: %.1f ms (gated). Pre-warm, on Ultralight's own thread while the world loads: %.1f ms "
				+ "(start-up %.1f ms — %s — then the first layout and full paint at the window size). Without pre-warm the "
				+ "first open would wait for all of it.\n\n", this.firstOpenMs, this.prewarmMs, this.initMs, this.initBreakdown));
			m.append(String.format(Locale.ROOT, "Memory added by Ultralight, the view, the page and its texture: +%.1f MB private bytes, +%.1f MB working set, "
				+ "+%.1f MB Java heap (process after: %.0f MB private, %.0f MB working set).\n\n",
				this.privateDeltaMb, this.workingSetDeltaMb, this.heapDeltaMb, this.privateAfterMb, this.workingSetAfterMb));
			return m.toString();
		}

		static String row(String name, Dist d) {
			return String.format(Locale.ROOT, "| %s | %.1f | %.1f | %.1f |\n", name, d.avg(), d.p50(), d.max());
		}

		static String signed(double v) {
			return String.format(Locale.ROOT, "%+.3f", v);
		}

		String text() {
			return "[voidhub-perf] " + this.backend + " " + this.verdict() + (this.failures.isEmpty() ? "" : " " + this.failures) + "\n" + this.markdown();
		}
	}
}
