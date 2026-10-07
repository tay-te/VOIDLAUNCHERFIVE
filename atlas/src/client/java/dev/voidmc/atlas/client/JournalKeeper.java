package dev.voidmc.atlas.client;

import dev.voidmc.atlas.api.AtlasJournal;
import dev.voidmc.atlas.api.DiscoveryState;
import dev.voidmc.atlas.api.Place;
import dev.voidmc.atlas.client.data.DimensionData;
import dev.voidmc.atlas.client.data.MapSession;
import dev.voidmc.atlas.core.journal.AreaStat;
import dev.voidmc.atlas.core.journal.Journal;
import dev.voidmc.atlas.core.journal.JournalEvent;
import dev.voidmc.atlas.core.journal.JournalStats;
import dev.voidmc.atlas.core.journal.VisitedCells;
import dev.voidmc.atlas.core.landmarks.AreaIndex;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.client.player.LocalPlayer;

/**
 * Keeps the journal (client thread, once a second): where the player is (distance, depth, dimensions), the areas they
 * walk into, the places they discover, and every half minute, on a worker, how much of each area they have explored
 * themselves and how many places they know. Also the journal's face to other mods ({@link AtlasJournal}).
 */
final class JournalKeeper implements AtlasJournal {
	private static final long PROGRESS_MS = 30_000;

	private final AtlasClient atlas;
	private final List<Consumer<Entry>> listeners = new CopyOnWriteArrayList<>();
	private final AtomicBoolean progressRunning = new AtomicBoolean();
	private String lastDimension;
	private String lastArea;
	private long lastProgress;

	JournalKeeper(AtlasClient atlas) {
		this.atlas = atlas;
	}

	/** Once a second (client thread), while a map is open and the world is known. */
	void tick(MapSession session, LocalPlayer p, String dimension, long day) {
		long now = System.currentTimeMillis();
		Journal journal = session.journal;
		int x = p.getBlockX();
		int y = p.getBlockY();
		int z = p.getBlockZ();
		if (!dimension.equals(this.lastDimension)) {
			this.lastDimension = dimension;
			this.lastArea = null;
			this.tell(journal.dimensionVisited(dimension, x, y, z, day, now));
		}
		for (JournalEvent e : journal.sample(dimension, p.getX(), p.getY(), p.getZ(), day, now)) {
			this.tell(e);
		}
		if (!p.isSpectator()) {
			// what the player explores themselves: a spectator's flight is not exploring
			session.visited.visit(dimension, x, z);
		}
		DimensionData d = session.dimensionIfOpen(dimension);
		String area = d == null ? null : d.areas.regionAt(x, z);
		if (area != null && !area.equals(this.lastArea)) {
			Place place = session.places.peek(area);
			if (place != null && !place.name().isEmpty()) {
				this.tell(journal.areaEntered(area, place.name(), null, dimension, x, y, z, day, now));
			}
		}
		this.lastArea = area;
		if (now - this.lastProgress >= PROGRESS_MS && d != null && this.progressRunning.compareAndSet(false, true)) {
			this.lastProgress = now;
			try {
				session.workers.execute(() -> {
					try {
						this.progress(session, d, day, now);
					} finally {
						this.progressRunning.set(false);
					}
				});
			} catch (RuntimeException e) {
				this.progressRunning.set(false);
			}
		}
	}

	/** A place moved on (client thread): a discovery is a line, once. */
	void changed(MapSession session, Place place, long day) {
		if (place.isDiscovered() && !place.isLabel()) {
			this.tell(session.journal.placeDiscovered(place, day, System.currentTimeMillis()));
		}
	}

	/** Each named area's explored share, and the place counts (worker). */
	private void progress(MapSession session, DimensionData d, long day, long now) {
		Map<String, int[]> explored = new HashMap<>();
		session.visited.forEach(d.id, c -> {
			int bx = VisitedCells.cellX(c) * VisitedCells.CELL + VisitedCells.CELL / 2;
			int bz = VisitedCells.cellZ(c) * VisitedCells.CELL + VisitedCells.CELL / 2;
			String id = d.areas.regionAt(bx, bz);
			if (id != null) {
				explored.computeIfAbsent(id, k -> new int[1])[0]++;
			}
		});
		for (AreaIndex.Area a : d.areas.areas()) {
			int[] n = explored.get(a.id());
			if (n == null || !a.kind().equals(Place.REGION)) {
				continue;
			}
			Place place = session.places.peek(a.id());
			String name = place == null ? "" : place.name();
			int total = (int) Math.min(Integer.MAX_VALUE, a.cells());
			session.journal.areaProgress(a.id(), name, session.biomeNames.name(a.biome()), total, Math.min(total, n[0]), day, now);
		}
		session.journal.countPlaces(session.places.all());
	}

	private void tell(JournalEvent e) {
		if (e == null) {
			return;
		}
		Entry entry = entry(e);
		for (Consumer<Entry> l : this.listeners) {
			try {
				l.accept(entry);
			} catch (Throwable t) {
				AtlasClient.LOGGER.warn("VOID Atlas: a journal listener failed: {}", t.toString());
			}
		}
	}

	private static Entry entry(JournalEvent e) {
		return new Entry(e.kind().name().toLowerCase(Locale.ROOT), e.text(), e.day(), e.lastDay(), e.epoch(), e.dimension(), e.x(), e.y(), e.z(),
			e.placeId());
	}

	// --- AtlasJournal --------------------------------------------------------------------------------------------------

	@Override
	public List<Entry> newest(int limit) {
		MapSession s = this.atlas.session();
		if (s == null || limit <= 0) {
			return List.of();
		}
		List<Entry> out = new ArrayList<>();
		for (JournalEvent e : s.journal.newest(limit)) {
			out.add(entry(e));
		}
		return out;
	}

	@Override
	public Stats stats() {
		MapSession s = this.atlas.session();
		if (s == null) {
			return null;
		}
		JournalStats j = s.journal.stats();
		return new Stats(j.distance(), j.deepestY(), j.highestY(), j.firstDay(), j.lastDay(), j.areasEntered(), j.events(),
			j.places(DiscoveryState.DISCOVERED), j.places(DiscoveryState.SIGHTED));
	}

	@Override
	public List<AreaProgress> topAreas(int n) {
		MapSession s = this.atlas.session();
		if (s == null || n <= 0) {
			return List.of();
		}
		List<AreaProgress> out = new ArrayList<>();
		for (AreaStat a : s.journal.topAreas(n)) {
			out.add(new AreaProgress(a.id(), a.name(), a.explored(), a.total()));
		}
		return out;
	}

	@Override
	public void addListener(Consumer<Entry> listener) {
		if (listener != null) {
			this.listeners.add(listener);
		}
	}
}
