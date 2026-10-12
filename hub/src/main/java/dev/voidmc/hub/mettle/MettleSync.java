package dev.voidmc.hub.mettle;

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
 * Keeps the page's copy of Mettle's state current without paying for it every half second.
 *
 * <p>Mettle's state message is big (about 720 KB with a full binder of ~400 cards) and building it costs tens of ms. So
 * the render thread only reads {@link MettleLink#revision}, a signature of what the message is built from, once a tick;
 * when it changed, Mettle builds its state tree there (it reads the client's game state), and a worker thread writes
 * the text, compares it with what the page holds (ignoring the chips' ages), collects the icons the page has not seen
 * yet and hands the messages back. The page outlives the screens (one shared view), so what it holds is remembered here
 * across opens: reopening the hub with nothing changed sends nothing.
 */
public final class MettleSync {

	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "VOID Hub Mettle state");
		t.setDaemon(true);
		t.setPriority(Thread.NORM_PRIORITY - 1);
		return t;
	});

	/** Chips and moments carry their age in ms: the comparison leaves it out (worker only). */
	private static final Pattern AGES = Pattern.compile("\"ageMs\":-?\\d+");
	private static final Pattern ICON = Pattern.compile("\"(?:icon|art)\":\"([^\"]{1,200}\\.png)\"");

	/** Messages the worker produced, in order (icons before the state that names them). */
	private record Out(String kind, String json) {
	}

	private static final ConcurrentLinkedQueue<Out> OUT = new ConcurrentLinkedQueue<>();
	/** The signature of the state last handed to the worker (render thread). */
	private static long revision = Long.MIN_VALUE;
	private static int epoch = Integer.MIN_VALUE;
	private static volatile boolean busy;
	/** Worker only: the state the page holds, without ages, and the icons it has. */
	private static String pageKey;
	private static final Set<String> ICONS_SENT = new HashSet<>();
	private static int iconsEpoch = Integer.MIN_VALUE;
	/** Tests and the PERF lines: how many times the state was built, and sent. */
	public static volatile int builds;
	public static volatile int sends;

	private MettleSync() {
	}

	/**
	 * One frame (render thread): delivers what the worker finished, and once per tick ({@code check}) rebuilds the
	 * state if its signature moved. Cheap when nothing changed: a signature and an empty queue.
	 */
	public static void frame(HubView hub, boolean check) {
		Out o;
		while ((o = OUT.poll()) != null) {
			if ("mettle".equals(o.kind())) {
				hub.sendLatest("mettle", o.json());
				sends++;
			} else {
				hub.send(o.json());
			}
		}
		if (!check || busy) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		long rev = MettleLink.revision(mc);
		int ep = MettleLink.iconEpoch();
		if (rev == revision && ep == epoch) {
			return;
		}
		Object tree = MettleLink.stateTree();
		if (tree == null) {
			return;
		}
		revision = rev;
		epoch = ep;
		builds++;
		busy = true;
		WORKER.execute(() -> {
			try {
				write(tree, ep);
			} catch (Throwable t) {
				VoidHub.LOGGER.error("VOID Hub: writing VOID Mettle's state failed", t);
			} finally {
				busy = false;
			}
		});
	}

	/** Worker: the text, the icons the page lacks, and the state if it differs from the page's. */
	private static void write(Object tree, int ep) {
		String json = String.valueOf(tree);
		if (ep != iconsEpoch) {
			iconsEpoch = ep;
			ICONS_SENT.clear();
		}
		Json.Writer map = Json.object();
		Json.Writer texels = Json.object();
		int n = 0;
		Matcher m = ICON.matcher(json);
		while (m.find()) {
			String id = m.group(1);
			if (ICONS_SENT.contains(id)) {
				continue;
			}
			String url = MettleLink.icon(id);
			if (url == null) {
				continue;  // missing, or unreadable mid-reload: asked again with the next state
			}
			ICONS_SENT.add(id);
			map.put(id, url);
			int t = MettleLink.texels(id);
			if (t > 0) {
				texels.put(id, t);
			}
			n++;
		}
		if (n > 0) {
			OUT.add(new Out("icons", Json.object().put("type", "icons").raw("map", map.end()).raw("texels", texels.end()).end()));
		}
		String key = AGES.matcher(json).replaceAll("");
		if (!key.equals(pageKey)) {
			pageKey = key;
			OUT.add(new Out("mettle", "{\"type\":\"mettle\",\"state\":" + json + "}"));
		}
	}

	/** Forgets what the page holds (tests; a page that lost its state). */
	public static void reset() {
		revision = Long.MIN_VALUE;
		epoch = Integer.MIN_VALUE;
		WORKER.execute(() -> {
			pageKey = null;
			ICONS_SENT.clear();
		});
	}
}
