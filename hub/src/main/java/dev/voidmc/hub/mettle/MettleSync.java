package dev.voidmc.hub.mettle;

import dev.voidmc.hub.sync.StateSync;
import dev.voidmc.hub.ui.HubView;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;

/**
 * Keeps the page's copy of Mettle's state current without paying for it every half second (the mod-neutral
 * {@link StateSync} with Mettle as its source).
 *
 * <p>Mettle's state message is big (about 720 KB with a full binder of ~400 cards) and building it costs tens of ms. So
 * the render thread only reads {@link MettleLink#signature}, a signature of what the message is built from, once a tick
 * (with Mettle 0.2.0's own {@link MettleLink#revision} counter for what only Mettle sees, within ten seconds);
 * when it changed, Mettle builds its state tree there (it reads the client's game state), and a worker thread writes
 * the text, compares it with what the page holds (ignoring the chips' ages), collects the icons the page has not seen
 * yet and hands the messages back. The page outlives the screens (one shared view), so what it holds is remembered here
 * across opens: reopening the hub with nothing changed sends nothing.
 */
public final class MettleSync {

	/** Chips and moments carry their age in ms: the comparison leaves it out (worker only). */
	private static final Pattern AGES = Pattern.compile("\"ageMs\":-?\\d+");
	private static final Pattern ICON = Pattern.compile("\"(?:icon|art)\":\"([^\"]{1,200}\\.png)\"");
	/** Mettle's counter alone (ages moving) rebuilds at most this often. */
	private static final long AGE_NS = 10_000_000_000L;

	private static final StateSync SYNC = new StateSync("Mettle", "mettle", new StateSync.Source() {
		@Override
		public long signature(Minecraft mc) {
			return MettleLink.signature(mc);
		}

		@Override
		public long revision(Minecraft mc) {
			return MettleLink.revision(mc);
		}

		@Override
		public int iconEpoch() {
			return MettleLink.iconEpoch();
		}

		@Override
		public Object stateTree() {
			return MettleLink.stateTree();
		}

		@Override
		public String icon(String id) {
			return MettleLink.icon(id);
		}

		@Override
		public int texels(String id) {
			return MettleLink.texels(id);
		}
	}, AGES, ICON, AGE_NS);

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
		SYNC.frame(hub, check);
		builds = SYNC.builds;
		sends = SYNC.sends;
	}

	/** Forgets what the page holds (tests; a page that lost its state). */
	public static void reset() {
		SYNC.reset();
	}
}
