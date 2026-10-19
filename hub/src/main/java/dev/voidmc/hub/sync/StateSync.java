package dev.voidmc.hub.sync;

import dev.voidmc.hub.VoidHub;
import dev.voidmc.hub.model.Json;
import dev.voidmc.hub.ui.HubView;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;

/**
 * Keeps the page's copy of a partner mod's state current without paying for it every frame, mod-neutral (VOID Mettle's
 * {@code MettleSync}, VOID Hearth's {@code HearthSync}).
 *
 * <p>The render thread only reads the source's {@link Source#signature}, a cheap signature of what the message is built
 * from, once a tick (and optionally the mod's own {@link Source#revision} counter, which alone rebuilds at most every
 * {@code ageNs}); when it changed, the mod builds its state tree there (it reads the client's game state), and a worker
 * thread writes the text, compares it with what the page holds (ignoring {@code ages}), collects the icons the page has
 * not seen yet and hands the messages back. The page outlives the screens (one shared view), so what it holds is
 * remembered here across opens: reopening with nothing changed sends nothing.
 */
public final class StateSync {

	/** Where the state comes from (a partner mod's overlay API, reached reflectively). Render thread. */
	public interface Source {
		/** Changes whenever the state could have; 0 when there is nothing to show. */
		long signature(Minecraft mc);

		/** The mod's own change counter, or Long.MIN_VALUE without one. */
		default long revision(Minecraft mc) {
			return Long.MIN_VALUE;
		}

		/** Changes with every resource reload (icons are read again). */
		default int iconEpoch() {
			return 0;
		}

		/** The state as the mod built it (its {@code toString} is the JSON text, written on the worker); null: none. */
		Object stateTree();

		/** An icon named by the state, as a data URL, or null (missing, or unreadable mid-reload). */
		default String icon(String id) {
			return null;
		}

		/** The icon's source width in texels, or 0. */
		default int texels(String id) {
			return 0;
		}

		/** Rewrites the state's text before it is compared and sent (worker thread): e.g. inline images pre-scaled. */
		default String transform(String json) {
			return json;
		}
	}

	private final String type;
	private final Source source;
	private final Pattern ages;
	private final Pattern icon;
	private final long ageNs;
	private final ExecutorService worker;

	/** Messages the worker produced, in order (icons before the state that names them). */
	private record Out(String kind, String json) {
	}

	private final ConcurrentLinkedQueue<Out> out = new ConcurrentLinkedQueue<>();
	/** The mod's counter and the signature of the state last handed to the worker, and when (render thread). */
	private long revision = Long.MIN_VALUE;
	private long signature = Long.MIN_VALUE;
	private long builtAt;
	private int epoch = Integer.MIN_VALUE;
	private volatile boolean busy;
	/** Worker only: the state the page holds, without ages, and the icons it has. */
	private String pageKey;
	private final Set<String> iconsSent = new HashSet<>();
	private int iconsEpoch = Integer.MIN_VALUE;
	/** Tests and the PERF lines: how many times the state was built, and sent. */
	public volatile int builds;
	public volatile int sends;

	/**
	 * @param name the worker thread's name ("VOID Hub " + name + " state")
	 * @param type the message type the page receives ({@code {"type": type, "state": ...}}) and its latest-only kind
	 * @param ages what the comparison leaves out (ages that move without the page redrawing), or null
	 * @param icon icon ids the state names (group 1), sent once each as {@code {"type":"icons",...}}, or null
	 * @param ageNs the mod's counter alone rebuilds at most this often
	 */
	public StateSync(String name, String type, Source source, Pattern ages, Pattern icon, long ageNs) {
		this.type = type;
		this.source = source;
		this.ages = ages;
		this.icon = icon;
		this.ageNs = ageNs;
		this.worker = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "VOID Hub " + name + " state");
			t.setDaemon(true);
			t.setPriority(Thread.NORM_PRIORITY - 1);
			return t;
		});
	}

	/**
	 * One frame (render thread): delivers what the worker finished, and once per tick ({@code check}) rebuilds the
	 * state if its signature moved. Cheap when nothing changed: a signature and an empty queue.
	 */
	public void frame(HubView hub, boolean check) {
		Out o;
		while ((o = this.out.poll()) != null) {
			if (this.type.equals(o.kind())) {
				hub.sendLatest(this.type, o.json());
				this.sends++;
			} else {
				hub.send(o.json());
			}
		}
		if (!check || this.busy) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		long sig = this.source.signature(mc);
		long rev = this.source.revision(mc);
		int ep = this.source.iconEpoch();
		long now = System.nanoTime();
		// What the state is built from changed (or icons were reloaded): rebuild now. The mod's own counter may also move
		// with ages the page does not redraw for: those alone rebuild at most every ageNs.
		boolean changed = sig != this.signature || ep != this.epoch;
		boolean aged = rev != this.revision && now - this.builtAt >= this.ageNs;
		if (!changed && !aged) {
			return;
		}
		Object tree = this.source.stateTree();
		if (tree == null) {
			return;
		}
		this.signature = sig;
		this.revision = rev;
		this.builtAt = now;
		this.epoch = ep;
		this.builds++;
		this.busy = true;
		this.worker.execute(() -> {
			try {
				this.write(tree, ep);
			} catch (Throwable t) {
				VoidHub.LOGGER.error("VOID Hub: writing the {} state failed", this.type, t);
			} finally {
				this.busy = false;
			}
		});
	}

	/** Worker: the text, the icons the page lacks, and the state if it differs from the page's. */
	private void write(Object tree, int ep) {
		String json = this.source.transform(String.valueOf(tree));
		if (this.icon != null) {
			if (ep != this.iconsEpoch) {
				this.iconsEpoch = ep;
				this.iconsSent.clear();
			}
			Json.Writer map = Json.object();
			Json.Writer texels = Json.object();
			int n = 0;
			Matcher m = this.icon.matcher(json);
			while (m.find()) {
				String id = m.group(1);
				if (this.iconsSent.contains(id)) {
					continue;
				}
				String url = this.source.icon(id);
				if (url == null) {
					continue;  // missing, or unreadable mid-reload: asked again with the next state
				}
				this.iconsSent.add(id);
				map.put(id, url);
				int t = this.source.texels(id);
				if (t > 0) {
					texels.put(id, t);
				}
				n++;
			}
			if (n > 0) {
				this.out.add(new Out("icons", Json.object().put("type", "icons").raw("map", map.end()).raw("texels", texels.end()).end()));
			}
		}
		String key = this.ages == null ? json : this.ages.matcher(json).replaceAll("");
		if (!key.equals(this.pageKey)) {
			this.pageKey = key;
			this.out.add(new Out(this.type, "{\"type\":\"" + this.type + "\",\"state\":" + json + "}"));
		}
	}

	/** Forgets what the page holds (tests; a page that lost its state). */
	public void reset() {
		this.revision = Long.MIN_VALUE;
		this.signature = Long.MIN_VALUE;
		this.epoch = Integer.MIN_VALUE;
		this.worker.execute(() -> {
			this.pageKey = null;
			this.iconsSent.clear();
		});
	}
}
