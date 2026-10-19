package dev.voidmc.hub.ui;

import dev.voidclient.ultralight.Renderer;
import dev.voidclient.ultralight.Ultralight;
import dev.voidclient.ultralight.View;
import dev.voidmc.hub.VoidHub;
import dev.voidmc.hub.model.Bridge;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * The one Ultralight view behind every VOID screen, and the texture it is shown through.
 *
 * <p><b>Why a CPU view.</b> Minecraft 26.3 draws through renderpearl, on OpenGL by default and on
 * Vulkan as an option. The binding's accelerated path is an OpenGL driver that renders into GL
 * textures directly, which has no meaning on the Vulkan backend. A CPU view rasterises into a
 * bitmap; each frame only its dirty rectangle is copied out (native, swizzled to RGBA in the same
 * pass) and uploaded into a renderpearl texture with {@code CommandEncoder.writeToTexture}, which
 * both backends implement. When nothing changed, nothing is copied or uploaded at all.
 *
 * <p><b>Two threads.</b> Ultralight runs on its own thread ("VOID Hub UI"): JavaScript, style,
 * layout and rasterisation — a few milliseconds whenever the page changes — never land on the
 * game's render thread. The render thread only posts messages to it and, once a frame, copies
 * whatever the UI thread has finished painting (a try-lock: if a paint is in progress it uploads
 * next frame instead of waiting). Messages the page sends back arrive on the UI thread and are
 * handed to the screen on the render thread. {@link #setSync} makes the render thread wait for
 * the UI thread every frame instead — the cost a single-threaded integration would pay, kept for
 * measurement.
 *
 * <p><b>Lifetime.</b> Created on first use (or pre-warmed when a world is joined) and kept for the
 * session: opening a screen again only sends it new state, so a re-open repaints what changed
 * instead of loading a page. The UI thread never exits (WebCore aborts the process when a thread
 * that used it terminates; the game leaves through System.exit).
 */
public final class HubView {

	public static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath(VoidHub.MOD_ID, "hub_view");
	private static final String PAGE = "file:///index.html";

	/** How the surface reaches the texture: the dirty rectangle (default) or a full readback. */
	public enum UploadMode {
		DIRTY, FULL
	}

	private static HubView instance;
	private static Throwable failure;

	// ---- UI thread only ----------------------------------------------------------------------------
	private Renderer renderer;
	private View view;

	// ---- shared ------------------------------------------------------------------------------------
	private final Thread uiThread;
	private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
	private final ConcurrentLinkedQueue<Bridge.Message> inbox = new ConcurrentLinkedQueue<>();
	private final AtomicLong posted = new AtomicLong();
	private volatile long processed;
	private final Object signal = new Object();
	private volatile boolean ready;
	private volatile Throwable uiFailure;
	private final AtomicLong uiUpdateNs = new AtomicLong();
	private final AtomicLong uiRenderNs = new AtomicLong();
	private final AtomicLong uiScriptNs = new AtomicLong();
	private final AtomicLong uiPasses = new AtomicLong();
	private volatile boolean sync = Boolean.getBoolean("voidhub.sync");
	private volatile UploadMode mode = UploadMode.valueOf(System.getProperty("voidhub.upload", "dirty").toUpperCase(java.util.Locale.ROOT));
	private final Object fullLock = new Object();
	private ByteBuffer fullBuf;
	private boolean fullReady;
	private int fullW;
	private int fullH;

	// ---- render thread only ------------------------------------------------------------------------
	private final HubStats stats = HubStats.INSTANCE;
	private HubTexture texture;
	private ByteBuffer staging;
	private final int[] rect = new int[6];
	private int width;
	private int height;
	private double scale = -1;
	private boolean forceFull = true;
	private Consumer<Bridge.Message> listener = m -> {
	};

	private final CountDownLatch started = new CountDownLatch(1);
	private final long createdNs = System.nanoTime();

	private HubView() {
		this.uiThread = new Thread(this::uiMain, "VOID Hub UI");
		this.uiThread.setDaemon(true);
		// Below the game's threads: under load the render thread wins, the card paints a bit later.
		this.uiThread.setPriority(Thread.MIN_PRIORITY);
		this.uiThread.start();
	}

	/** Waits for the UI thread to have Ultralight up and the page loaded. */
	private boolean awaitStarted() {
		try {
			if (!this.started.await(15, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Ultralight did not start within 15 s");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
		if (this.uiFailure != null) {
			throw new IllegalStateException("Ultralight failed to start", this.uiFailure);
		}
		return true;
	}

	/**
	 * The shared view, created on first call (blocking until Ultralight is up); null if
	 * Ultralight cannot run here.
	 */
	public static HubView get() {
		if (failure != null) {
			return null;
		}
		try {
			if (instance == null) {
				instance = new HubView();
			}
			if (instance.started.getCount() > 0 || instance.uiFailure != null) {
				instance.awaitStarted();
			}
			return instance;
		} catch (Throwable t) {
			failure = t;
			instance = null;
			VoidHub.LOGGER.error("VOID Hub: Ultralight is unavailable, keeping vanilla screens", t);
			return null;
		}
	}

	/**
	 * Starts Ultralight without waiting for it, and has it lay the inventory out at this size so
	 * fonts, glyphs and the first full paint are paid on its own thread while the world loads.
	 */
	public static void prewarm(int fbW, int fbH, double scale, String layoutJson) {
		if (instance != null || failure != null) {
			return;
		}
		instance = new HubView();
		instance.post(() -> {
			instance.uiResize(fbW, fbH, scale);
			instance.view.evaluateScript(Bridge.call(layoutJson));
			// A stand-in state and card build every meter and row the first real open would
			// otherwise create (element creation is the expensive part of a first paint).
			instance.view.evaluateScript(Bridge.call(WARM_STATE));
			instance.view.evaluateScript(Bridge.call(WARM_DETAIL));
		});
	}

	private static final String WARM_STATE = "{\"type\":\"state\",\"hover\":-1,\"held\":0,\"filter\":\"all\",\"carrying\":false,"
		+ "\"drag\":[],\"filled\":[],\"world\":\"\",\"day\":1,\"purse\":{\"copper\":0,\"iron\":0,\"gold\":0,\"hearth\":false},"
		+ "\"used\":0,\"total\":36,\"armor\":10,\"level\":1,\"xp\":0.5}";
	private static final String WARM_DETAIL = dev.voidmc.hub.model.TooltipModel.of(new dev.voidmc.hub.model.ItemFacts(
		"Diamond Sword", "rare", dev.voidmc.hub.model.ItemFacts.Category.WEAPON, 1, 1, 100, 1561, 7.0, 1.6, 0, 0,
		java.util.List.of(new dev.voidmc.hub.model.ItemFacts.Enchant("Sharpness", 4, 5), new dev.voidmc.hub.model.ItemFacts.Enchant("Looting", 2, 3),
			new dev.voidmc.hub.model.ItemFacts.Enchant("Unbreaking", 3, 3))), "HELD  ·  HOTBAR 1", 36);

	public static boolean available() {
		return get() != null;
	}

	public static boolean isCreated() {
		return instance != null;
	}

	private static String ms(long ns) {
		return String.format(java.util.Locale.ROOT, "%.1f", ns / 1e6);
	}

	// ---- the UI thread -----------------------------------------------------------------------------

	private void uiMain() {
		try {
			long t0 = System.nanoTime();
			Ultralight.load();
			long t1 = System.nanoTime();
			this.renderer = Ultralight.createRenderer("assets/" + VoidHub.MOD_ID + "/ui/");
			long t2 = System.nanoTime();
			this.view = this.renderer.createViewCpu(16, 16, true);
			this.view.setMessageHandler(this::onMessage);
			long t3 = System.nanoTime();
			this.view.loadUrl(PAGE);
			long deadline = t3 + 10_000_000_000L;
			while (!this.ready && System.nanoTime() < deadline) {
				this.renderer.update();
				this.renderer.render();
				LockSupport.parkNanos(200_000L);
			}
			long t4 = System.nanoTime();
			this.stats.loadNativesNs = t1 - t0;
			this.stats.createRendererNs = t2 - t1;
			this.stats.createViewNs = t3 - t2;
			this.stats.pageLoadNs = t4 - t3;
			this.stats.initTotalNs = t4 - this.createdNs;
			if (!this.ready) {
				VoidHub.LOGGER.warn("VOID Hub page did not report ready within 10 s; continuing");
			}
			VoidHub.LOGGER.info("VOID Hub: Ultralight {} (WebKit {}) up in {} ms on its own thread (natives {} ms, renderer {} ms, view {} ms, page {} ms)",
				Ultralight.version(), Ultralight.webKitVersion(), ms(this.stats.initTotalNs), ms(this.stats.loadNativesNs),
				ms(this.stats.createRendererNs), ms(this.stats.createViewNs), ms(this.stats.pageLoadNs));
			VoidHub.LOGGER.info("VOID Hub: {}", Ultralight.licenceNotice());
		} catch (Throwable t) {
			this.uiFailure = t;
			this.started.countDown();
			return;
		}
		this.started.countDown();
		while (true) {
			try {
				this.uiPass();
			} catch (Throwable t) {
				VoidHub.LOGGER.error("VOID Hub UI thread: pass failed", t);
			}
		}
	}

	private int uiW = -1;
	private int uiH = -1;
	private double uiScale = -1;

	/** UI thread: size the view, skipping no-op resizes (each one is a full repaint). */
	private void uiResize(int w, int h, double scale) {
		if (w != this.uiW || h != this.uiH) {
			this.view.resize(w, h);
			this.uiW = w;
			this.uiH = h;
		}
		if (scale != this.uiScale) {
			this.view.setDeviceScale(scale);
			this.uiScale = scale;
		}
	}

	private void uiPass() {
		long upTo = this.posted.get();
		boolean worked = false;
		Runnable task;
		while ((task = this.tasks.poll()) != null) {
			task.run();
			worked = true;
		}
		long t0 = System.nanoTime();
		this.renderer.update();
		this.renderer.refreshDisplay();
		long t1 = System.nanoTime();
		this.renderer.render();  // paints under the surface lock (native)
		long t2 = System.nanoTime();
		if (this.mode == UploadMode.FULL) {
			this.fullReadback();
		}
		this.uiUpdateNs.addAndGet(t1 - t0);
		this.uiRenderNs.addAndGet(t2 - t1);
		this.uiPasses.incrementAndGet();
		this.processed = upTo;
		synchronized (this.signal) {
			this.signal.notifyAll();
		}
		if (!worked && this.tasks.isEmpty()) {
			// Nothing to do: wake for the next message, or every 4 ms for page timers.
			LockSupport.parkNanos(this, 4_000_000L);
		}
	}

	/**
	 * The binding's original path, kept as the measured baseline: the whole surface, through
	 * readPixels (a fresh BGRA byte[] each call), swizzled in Java.
	 */
	private void fullReadback() {
		if (!this.view.isDirty()) {
			return;
		}
		byte[] bgra = this.view.readPixels();
		if (bgra == null) {
			return;
		}
		int w = this.view.width();
		int h = this.view.height();
		synchronized (this.fullLock) {
			if (this.fullBuf == null || this.fullBuf.capacity() < bgra.length) {
				this.fullBuf = ByteBuffer.allocateDirect(bgra.length).order(ByteOrder.nativeOrder());
			}
			this.fullBuf.clear();
			for (int i = 0; i < bgra.length; i += 4) {
				this.fullBuf.put(bgra[i + 2]).put(bgra[i + 1]).put(bgra[i]).put(bgra[i + 3]);
			}
			this.fullBuf.flip();
			this.fullW = w;
			this.fullH = h;
			this.fullReady = true;
		}
	}

	private String onMessage(String json) {
		Bridge.Message m = Bridge.decode(json);
		if (m instanceof Bridge.Ready) {
			this.ready = true;
		} else if (m instanceof Bridge.Invalid invalid) {
			VoidHub.LOGGER.warn("VOID Hub page sent an invalid message ({}): {}", invalid.reason(), json);
		} else if (this.attached || !screenOnly(m)) {
			this.inbox.add(m);
		}
		return Bridge.reply(m);
	}

	/**
	 * Messages that only mean something to the screen that is open: native layers, page stacks, keyboard claims, the
	 * tooltip's box and card sounds timed to an animation. With no screen open they are dropped, never queued for the
	 * next one (a stale deal sound would play when the hub next opens).
	 */
	static boolean screenOnly(Bridge.Message m) {
		return m instanceof Bridge.Fx || m instanceof Bridge.Items || m instanceof Bridge.MettleUi || m instanceof Bridge.Tip
			|| m instanceof Bridge.HearthFx || m instanceof Bridge.HearthUi
			|| m instanceof Bridge.MettleCall c && "cardSound".equals(c.op());
	}

	private void post(Runnable r) {
		this.tasks.add(r);
		this.posted.incrementAndGet();
		LockSupport.unpark(this.uiThread);
	}

	// ---- render thread API ---------------------------------------------------------------------

	/** Page-to-Java messages are delivered to this, on the render thread, during {@link #frame}. */
	public void setListener(Consumer<Bridge.Message> listener) {
		this.listener = listener == null ? m -> {
		} : listener;
		this.attached = listener != null;
	}

	/** A screen is open and takes the page's messages (UI thread reads it to drop screen-only ones when none is). */
	private volatile boolean attached;

	/**
	 * Hands every queued page message to {@code to} now (render thread), e.g. when a screen closes: the actions a
	 * player took in its last frame still count, its native layers and sounds do not reach the next screen.
	 */
	public void drainInbox(Consumer<Bridge.Message> to) {
		Bridge.Message m;
		while ((m = this.inbox.poll()) != null) {
			if (!screenOnly(m)) {
				to.accept(m);
			}
		}
	}

	/** Tests: the page messages waiting for the next frame. */
	public int inboxSize() {
		return this.inbox.size();
	}

	/**
	 * Tells the page whether a screen shows it. Hidden, the page pauses its timers (a draft's next deal, a reveal's
	 * stages) and sends nothing; shown again, it resumes and re-sends its keyboard claims to the new screen.
	 */
	public void setVisible(boolean on) {
		this.send("{\"type\":\"visible\",\"on\":" + on + "}");
	}

	public void setUploadMode(UploadMode mode) {
		this.mode = mode;
		this.forceFull = true;
	}

	public UploadMode uploadMode() {
		return this.mode;
	}

	/** True: the render thread waits for the UI thread every frame (single-thread equivalent). */
	public void setSync(boolean sync) {
		this.sync = sync;
	}

	public boolean sync() {
		return this.sync;
	}

	/**
	 * Makes the view and texture match the framebuffer, at {@code scale} physical px per CSS px.
	 * A size change recreates the texture and forces a full upload.
	 */
	public void ensureSize(int fbW, int fbH, double scale) {
		if (fbW != this.width || fbH != this.height) {
			long t0 = System.nanoTime();
			this.width = fbW;
			this.height = fbH;
			double s = scale;
			this.post(() -> this.uiResize(fbW, fbH, s));
			if (this.texture != null) {
				Minecraft.getInstance().getTextureManager().release(TEXTURE_ID);
			}
			this.texture = new HubTexture(fbW, fbH);
			Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, this.texture);
			this.staging = ByteBuffer.allocateDirect(fbW * fbH * 4).order(ByteOrder.nativeOrder());
			this.forceFull = true;
			this.stats.lastResizeNs = System.nanoTime() - t0;
		}
		if (scale != this.scale) {
			this.scale = scale;
			this.post(() -> this.uiResize(fbW, fbH, scale));
			this.forceFull = true;
		}
	}

	public int width() {
		return this.width;
	}

	public int height() {
		return this.height;
	}

	public HubTexture texture() {
		return this.texture;
	}

	/** Starts this frame's stats. */
	public void beginFrame() {
		this.stats.begin();
	}

	private final java.util.concurrent.ConcurrentHashMap<String, String> latest = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * Delivers the newest message of a kind (layout, state, detail) to the page. If an older one of
	 * the same kind has not reached the UI thread yet it is replaced, not queued behind: a fast
	 * pointer crossing ten wells in one UI pass costs one card repaint, not ten.
	 */
	public void sendLatest(String kind, String messageJson) {
		long t0 = System.nanoTime();
		if (this.latest.put(kind, messageJson) == null) {
			this.post(() -> {
				String json = this.latest.remove(kind);
				if (json != null) {
					long s0 = System.nanoTime();
					this.view.evaluateScript(Bridge.call(json));
					long s1 = System.nanoTime();
					// Paint each message on its own. Ultralight repaints the whole view (~50 ms at
					// 1920x1080) when one paint has to cover changes in several separate places —
					// e.g. two far-apart wells (hover moved) plus the card — while painting the
					// wells, then the card, costs a couple of ms each.
					this.renderer.update();
					this.renderer.render();
					long s2 = System.nanoTime();
					this.uiScriptNs.addAndGet(s1 - s0);
					this.uiRenderNs.addAndGet(s2 - s1);
				}
			});
		}
		this.stats.current().postNs += System.nanoTime() - t0;
	}

	/** Delivers one message to the page ({@code hub.apply(json)}), on the UI thread. */
	public void send(String messageJson) {
		long t0 = System.nanoTime();
		String script = Bridge.call(messageJson);
		this.post(() -> {
			long s0 = System.nanoTime();
			this.view.evaluateScript(script);
			this.uiScriptNs.addAndGet(System.nanoTime() - s0);
		});
		this.stats.current().postNs += System.nanoTime() - t0;
	}

	/** Runs script on the UI thread and waits for its string result (tests and diagnostics). */
	public String eval(String js) {
		CompletableFuture<String> result = new CompletableFuture<>();
		this.post(() -> result.complete(this.view.evaluateScript(js)));
		try {
			return result.get(5, TimeUnit.SECONDS);
		} catch (Exception e) {
			throw new IllegalStateException("UI thread did not answer", e);
		}
	}

	/** Diagnostics: where the page drew each well (CSS px), as JSON. */
	public String wellRects() {
		return this.eval("hub.wellRects()");
	}

	public void mouseMove(double cssX, double cssY) {
		int x = (int) Math.floor(cssX);
		int y = (int) Math.floor(cssY);
		this.post(() -> this.view.fireMouseEvent(View.MOUSE_MOVED, x, y, 0));
	}

	public void mouseButton(boolean down, double cssX, double cssY, int sdlButton) {
		int button = switch (sdlButton) {
			case 1 -> 1;
			case 2 -> 2;
			case 3 -> 3;
			default -> 0;
		};
		if (button != 0) {
			int x = (int) Math.floor(cssX);
			int y = (int) Math.floor(cssY);
			this.post(() -> this.view.fireMouseEvent(down ? View.MOUSE_DOWN : View.MOUSE_UP, x, y, button));
		}
	}

	/**
	 * Waits (render thread) until the UI thread has run everything posted so far and painted it.
	 * Used once when a screen opens, so its first frame is not the previous screen's pixels.
	 */
	public boolean awaitPainted(long timeoutNanos) {
		long target = this.posted.get();
		long deadline = System.nanoTime() + timeoutNanos;
		if (this.processed >= target) {
			return true;
		}
		// The render thread is now waiting on this paint: lift the UI thread out of its
		// background priority until it is done, so a busy machine cannot starve an open.
		this.uiThread.setPriority(Thread.MAX_PRIORITY);
		LockSupport.unpark(this.uiThread);
		try {
			while (this.processed < target) {
				long left = deadline - System.nanoTime();
				if (left <= 0) {
					return false;
				}
				synchronized (this.signal) {
					if (this.processed < target) {
						try {
							this.signal.wait(Math.max(1, left / 1_000_000L));
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
							return false;
						}
					}
				}
			}
			return true;
		} finally {
			this.uiThread.setPriority(Thread.MIN_PRIORITY);
		}
	}

	/**
	 * One game frame: deliver the page's messages, then upload whatever the UI thread has
	 * finished painting. Returns true if pixels were uploaded.
	 */
	public boolean frame() {
		HubStats.Frame f = this.stats.current();
		if (this.sync) {
			long w0 = System.nanoTime();
			this.awaitPainted(100_000_000L);
			f.waitNs = System.nanoTime() - w0;
		}
		Bridge.Message m;
		while ((m = this.inbox.poll()) != null) {
			this.listener.accept(m);
		}
		boolean uploaded = this.mode == UploadMode.FULL ? this.uploadFull(f) : this.uploadDirty(f);
		f.updateNs = this.uiUpdateNs.getAndSet(0);
		f.renderNs = this.uiRenderNs.getAndSet(0);
		f.scriptNs = this.uiScriptNs.getAndSet(0);
		f.uiPasses = (int) this.uiPasses.getAndSet(0);
		this.stats.end();
		return uploaded;
	}

	private boolean uploadDirty(HubStats.Frame f) {
		long t0 = System.nanoTime();
		this.staging.clear();
		int bytes = this.view.copyDirtyRgba(this.staging, this.forceFull, true, this.rect);
		if (bytes == -2) {
			f.copyNs = System.nanoTime() - t0;
			f.busy = true;
			return false;  // the UI thread is painting: take it next frame
		}
		if (bytes < 0) {
			f.copyNs = System.nanoTime() - t0;
			return false;
		}
		if (this.rect[4] != this.width || this.rect[5] != this.height) {
			// The UI thread has not applied the resize yet; a rect from the old surface must
			// not reach the new texture. Copy everything once it has.
			f.copyNs = System.nanoTime() - t0;
			this.forceFull = true;
			return false;
		}
		if (bytes == 0) {
			f.copyNs = System.nanoTime() - t0;
			this.stats.idleFrames++;
			return false;
		}
		this.staging.position(0).limit(bytes);
		long t1 = System.nanoTime();
		this.texture.upload(this.staging, this.rect[0], this.rect[1], this.rect[2], this.rect[3]);
		long t2 = System.nanoTime();
		this.forceFull = false;
		this.stats.dirtyUploads++;
		f.copyNs = t1 - t0;
		f.uploadNs = t2 - t1;
		f.bytes = bytes;
		f.rects = 1;
		return true;
	}

	private boolean uploadFull(HubStats.Frame f) {
		long t0 = System.nanoTime();
		synchronized (this.fullLock) {
			if (!this.fullReady || this.fullW != this.width || this.fullH != this.height) {
				f.copyNs = System.nanoTime() - t0;
				return false;
			}
			long t1 = System.nanoTime();
			this.texture.upload(this.fullBuf, 0, 0, this.fullW, this.fullH);
			long t2 = System.nanoTime();
			this.fullReady = false;
			this.stats.fullUploads++;
			f.copyNs = t1 - t0;
			f.uploadNs = t2 - t1;
			f.bytes = (long) this.fullW * this.fullH * 4;
			f.rects = 1;
		}
		return true;
	}

	public boolean ready() {
		return this.ready;
	}
}
