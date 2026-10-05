package dev.voidmc.atlas.core.places;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.atlas.api.Discovery;
import dev.voidmc.atlas.api.DiscoveryState;
import dev.voidmc.atlas.api.Place;
import dev.voidmc.atlas.api.Source;
import dev.voidmc.atlas.api.Tier;
import dev.voidmc.atlas.core.RegionStore;
import dev.voidmc.atlas.core.StructureNames;
import dev.voidmc.atlas.core.detect.Sighting;
import dev.voidmc.atlas.core.detect.StructureKind;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Every place one world's player knows of, with how far they have come with each: unknown, sighted, discovered (it only
 * ever moves forward), kept in {@code places.json}. Thread-safe: chunk scans add sightings from the workers, the
 * client thread moves places on as the player walks, the map reads snapshots.
 *
 * <p>Places come in from:
 * <ul>
 *   <li><b>sightings</b> in the blocks ({@link #sight}): a sighting close to a place of the same kind (within its
 *   kind's merge radius) is more of that place and moves its middle a little; one on the surface is sighted at once,
 *   one under the ground stays unknown until the player is near;</li>
 *   <li><b>exact</b> structure data ({@link #exact}, singleplayer): it wins. Sightings of the same kind near it, and any
 *   sighting of a structure inside its bounds, are folded into it (their state is kept), and later ones inside its
 *   bounds are ignored;</li>
 *   <li><b>natural</b> landmarks ({@link #replaceGroup}): peaks, lakes, regions..., replaced group by group as the
 *   land they come from is analysed again, keeping their states;</li>
 *   <li><b>reports</b> from other mods and servers ({@link #report}).</li>
 * </ul>
 *
 * <p>How a place moves on ({@link #proximity}): one on the surface is sighted once within view and discovered once the
 * player comes within {@value #SURFACE_REACH} blocks of it (of its bounds, for exact ones); one underground is sighted
 * within {@value #UNDERGROUND_SIGHT} blocks and discovered within {@value #UNDERGROUND_REACH} (in three dimensions: being
 * above a dungeon is not finding it). A region is discovered by walking into it ({@link #enter}).
 */
public final class PlaceBook {
	public static final int SURFACE_REACH = 24;
	public static final int UNDERGROUND_SIGHT = 32;
	public static final int UNDERGROUND_REACH = 16;
	/** How far round an exact structure's bounds a sighting still counts as part of it. */
	private static final int EXACT_MARGIN = 12;
	private static final int MAX_WEIGHT = 24;
	/** The group of the named areas (regions, lakes, rivers) of a dimension. */
	public static final long AREAS = Long.MAX_VALUE;
	/** The group of places reported by other mods and servers. */
	public static final long REPORTS = Long.MIN_VALUE;

	/** The group of the landmarks found in one region (never 0, {@link #AREAS} or {@link #REPORTS}). */
	public static long regionGroup(int rx, int rz) {
		return 1 + (((long) (rx + (1 << 20)) & 0x1FFFFF) << 21 | ((long) (rz + (1 << 20)) & 0x1FFFFF));
	}

	private static final class Entry {
		Place place;
		/** The sightings' running middle and how many there were (sightings only). */
		double sx;
		double sy;
		double sz;
		int weight;
		/** An exact structure's bounds: minX, minY, minZ, maxX, maxY, maxZ (exact only). */
		int[] box;
		/** The structure family, for matching sightings with exact data. */
		String family;
		/** The group a natural place belongs to (a region's key, or the dimension's areas), or 0. */
		long group;
		/** The game day it was sighted and discovered on (-1 if not, or not known). */
		long sightedDay = -1;
		long discoveredDay = -1;

		Entry(Place place, String family) {
			this.place = place;
			this.family = family;
		}
	}

	private final Path file;
	private final Consumer<String> warn;
	private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
	private final ConcurrentLinkedQueue<Place> changes = new ConcurrentLinkedQueue<>();
	private long version;
	private long savedVersion;
	private volatile boolean exactAvailable;
	/** Today's game day, for the days places are sighted and discovered on. */
	private volatile long gameDay = -1;
	private volatile Map<String, List<Place>> visible = Map.of();
	private volatile long visibleVersion = -1;
	/** Sightings folded into exact data of the same family (the heuristics were right) and of another (they were not). */
	private int confirmed;
	private int contradicted;

	public PlaceBook(Path file, Consumer<String> warn) {
		this.file = file;
		this.warn = warn;
	}

	/** Whether the game's own structure data is at hand (singleplayer): then it is what places are matched against. */
	public void setExactAvailable(boolean exact) {
		this.exactAvailable = exact;
	}

	public boolean exactAvailable() {
		return this.exactAvailable;
	}

	/** The game's day now (the client tells the book as the days pass). */
	public void setGameDay(long day) {
		this.gameDay = day;
	}

	/** The game days a place was sighted and discovered on, -1 where not (or from before days were kept). */
	public synchronized long[] days(String id) {
		Entry e = this.entries.get(id);
		return e == null ? new long[]{-1, -1} : new long[]{e.sightedDay, e.discoveredDay};
	}

	// --- reading ---------------------------------------------------------------------------------------------------

	public synchronized List<Place> all() {
		List<Place> out = new ArrayList<>(this.entries.size());
		for (Entry e : this.entries.values()) {
			out.add(e.place);
		}
		return out;
	}

	public synchronized Place get(String id) {
		Entry e = this.entries.get(id);
		return e == null ? null : e.place;
	}

	public synchronized int size() {
		return this.entries.size();
	}

	/**
	 * The places the player knows of (sighted or discovered) in a dimension: an unmodifiable snapshot, made again only
	 * when something changed, so the map can ask every frame.
	 */
	public List<Place> visible(String dimension) {
		Map<String, List<Place>> snapshot = this.visible;
		if (this.visibleVersion != this.versionNow()) {
			snapshot = this.rebuildVisible();
		}
		List<Place> list = snapshot.get(dimension);
		return list == null ? List.of() : list;
	}

	private synchronized long versionNow() {
		return this.version;
	}

	private synchronized Map<String, List<Place>> rebuildVisible() {
		Map<String, List<Place>> byDim = new HashMap<>();
		for (Entry e : this.entries.values()) {
			if (e.place.state().atLeast(DiscoveryState.SIGHTED)) {
				byDim.computeIfAbsent(e.place.dimension(), d -> new ArrayList<>()).add(e.place);
			}
		}
		Map<String, List<Place>> frozen = new HashMap<>();
		byDim.forEach((k, v) -> frozen.put(k, Collections.unmodifiableList(v)));
		this.visible = frozen;
		this.visibleVersion = this.version;
		return frozen;
	}

	/** Places that were sighted or discovered since the last call, oldest first (for the client thread's listeners). */
	public List<Place> drainChanges() {
		List<Place> out = new ArrayList<>();
		Place p;
		while ((p = this.changes.poll()) != null) {
			out.add(p);
		}
		return out;
	}

	/** The structures discovered, as the old {@link Discovery} records (for the API's {@code discoveries()}). */
	public synchronized List<Discovery> discoveries() {
		List<Discovery> out = new ArrayList<>();
		for (Entry e : this.entries.values()) {
			Place p = e.place;
			if (p.isDiscovered() && !p.isNatural()) {
				out.add(new Discovery(p.kind(), p.name(), p.category(), p.x(), p.y(), p.z(), p.dimension(), p.discovered(), p.underground()));
			}
		}
		return out;
	}

	/** How many sightings exact data confirmed (same kind) and contradicted (inside another structure). */
	public synchronized int[] precision() {
		return new int[]{this.confirmed, this.contradicted};
	}

	// --- sightings -------------------------------------------------------------------------------------------------

	private static String idFor(String kind, int x, int z, String dimension) {
		return kind + "@" + x + "," + z + (dimension.equals("minecraft:overworld") ? "" : "@" + dimension);
	}

	/** A structure seen in the blocks; returns its place (null if exact data already has it). */
	public synchronized Place sight(String dimension, Sighting s, long now) {
		StructureKind kind = s.kind();
		String family = kind.id;
		// exact data wins: a sighting inside a known structure is part of it, or a mistake
		for (Entry e : this.entries.values()) {
			if (e.box != null && e.place.dimension().equals(dimension) && inBox(e.box, s.x(), s.y(), s.z(), EXACT_MARGIN)) {
				return null;
			}
		}
		Entry best = null;
		double bestD = Double.MAX_VALUE;
		for (Entry e : this.entries.values()) {
			if (e.box == null && family.equals(e.family) && e.place.dimension().equals(dimension)) {
				double d = Math.hypot(e.place.x() - s.x(), e.place.z() - s.z());
				if (d <= kind.mergeRadius && d < bestD) {
					best = e;
					bestD = d;
				}
			}
		}
		if (best != null) {
			Entry e = best;
			int w = Math.min(MAX_WEIGHT, e.weight);
			e.sx = (e.sx * w + s.x()) / (w + 1);
			e.sy = (e.sy * w + s.y()) / (w + 1);
			e.sz = (e.sz * w + s.z()) / (w + 1);
			e.weight = w + 1;
			Place p = e.place;
			boolean underground = p.underground() && s.underground();
			DiscoveryState state = underground ? p.state() : DiscoveryState.max(p.state(), DiscoveryState.SIGHTED);
			Place moved = new Place(p.id(), p.kind(), p.name(), p.category(), p.tier(), p.state(), p.source(), (int) Math.round(e.sx),
				(int) Math.round(e.sy), (int) Math.round(e.sz), p.dimension(), underground, Math.max(p.radius(), (int) Math.round(bestD)), p.sighted(),
				p.discovered());
			this.set(e, moved.withState(state, now));
			return e.place;
		}
		Place p = new Place(idFor(family, s.x(), s.z(), dimension), family, kind.label, kind.category, kind.tier, DiscoveryState.UNKNOWN,
			Source.seen(), s.x(), s.y(), s.z(), dimension, s.underground(), 0, 0, 0);
		Entry e = new Entry(p, family);
		e.sx = s.x();
		e.sy = s.y();
		e.sz = s.z();
		e.weight = 1;
		this.entries.put(p.id(), e);
		// a new place is a change even while it stays unknown (set() alone would not count it)
		this.version++;
		this.set(e, s.underground() ? p : p.withState(DiscoveryState.SIGHTED, now));
		return e.place;
	}

	/**
	 * A structure from the game's own data (singleplayer): bounds {@code box} (minX, minY, minZ, maxX, maxY, maxZ),
	 * at least {@code state}. Folds in the sightings it explains. Returns its place.
	 */
	public synchronized Place exact(String dimension, String structureId, String family, int[] box, boolean underground, DiscoveryState state,
		long now) {
		int cx = (box[0] + box[3]) / 2;
		int cy = (box[1] + box[4]) / 2;
		int cz = (box[2] + box[5]) / 2;
		String id = idFor(structureId, cx, cz, dimension);
		Entry e = this.entries.get(id);
		if (e == null) {
			String category = StructureNames.category(structureId, family.equals("minecraft:village"));
			int radius = (int) Math.round(Math.hypot(box[3] - box[0], box[5] - box[2]) / 2);
			Place p = new Place(id, structureId, StructureNames.name(structureId), category, StructureNames.tier(structureId, category),
				DiscoveryState.UNKNOWN, Source.exact(), cx, underground ? cy : Math.max(cy, box[1]), cz, dimension, underground, radius, 0, 0);
			e = new Entry(p, family);
			e.box = box.clone();
			this.entries.put(id, e);
			this.version++;
		} else if (e.box == null) {
			e.box = box.clone();
			this.version++;
		}
		// fold in the sightings it explains, keeping how far the player had come with them
		DiscoveryState carried = DiscoveryState.UNKNOWN;
		long sighted = 0;
		long discovered = 0;
		for (Iterator<Entry> it = this.entries.values().iterator(); it.hasNext(); ) {
			Entry o = it.next();
			if (o == e || o.box != null || o.group != 0 || !o.place.source().kind().equals(Source.Kind.SEEN) || !o.place.dimension().equals(dimension)
				|| o.place.isNatural()) {
				continue;
			}
			boolean sameFamily = family.equals(o.family);
			boolean inside = inBox(box, o.place.x(), o.place.y(), o.place.z(), EXACT_MARGIN);
			boolean close = sameFamily && Math.hypot(o.place.x() - cx, o.place.z() - cz) <= mergeRadius(family);
			if (inside || close) {
				if (sameFamily) {
					this.confirmed++;
				} else {
					this.contradicted++;
				}
				carried = DiscoveryState.max(carried, o.place.state());
				sighted = sighted == 0 ? o.place.sighted() : o.place.sighted() == 0 ? sighted : Math.min(sighted, o.place.sighted());
				discovered = discovered == 0 ? o.place.discovered() : o.place.discovered() == 0 ? discovered : Math.min(discovered, o.place.discovered());
				it.remove();
				this.version++;
			}
		}
		Place p = e.place;
		DiscoveryState next = DiscoveryState.max(p.state(), DiscoveryState.max(state, carried));
		if (next != p.state()) {
			Place q = p.withState(next, now);
			if (sighted != 0 && (q.sighted() == 0 || sighted < q.sighted())) {
				q = new Place(q.id(), q.kind(), q.name(), q.category(), q.tier(), q.state(), q.source(), q.x(), q.y(), q.z(), q.dimension(), q.underground(),
					q.radius(), sighted, discovered != 0 && q.discovered() != 0 ? Math.min(discovered, q.discovered()) : q.discovered());
			}
			this.set(e, q);
		}
		return e.place;
	}

	private static int mergeRadius(String family) {
		StructureKind k = StructureKind.byId(family);
		return k == null ? 32 : k.mergeRadius;
	}

	private static boolean inBox(int[] box, int x, int y, int z, int margin) {
		return x >= box[0] - margin && x <= box[3] + margin && z >= box[2] - margin && z <= box[5] + margin && y >= box[1] - margin
			&& y <= box[4] + margin;
	}

	// --- natural places and reports --------------------------------------------------------------------------------

	/**
	 * Replaces the natural places of one group (the landmarks found in one region; the named areas of a dimension) with
	 * {@code places}, keeping the state, times and name of each place that is still there (by id, or failing that the
	 * nearest of the same kind within 32 blocks).
	 */
	public synchronized void replaceGroup(String dimension, long group, List<Place> places, long now) {
		List<Entry> old = new ArrayList<>();
		for (Entry e : this.entries.values()) {
			if (e.group == group && e.place.dimension().equals(dimension)) {
				old.add(e);
			}
		}
		List<Entry> kept = new ArrayList<>();
		// places still there by id claim their entries first, so a new place close by cannot take one's state
		for (Place p : places) {
			Entry same = this.entries.get(p.id());
			if (same != null && same.group == group) {
				kept.add(same);
			}
		}
		for (Place p : places) {
			Entry match = this.entries.get(p.id());
			if (match == null || match.group != group) {
				match = null;
				double best = 32;
				for (Entry o : old) {
					if (o.place.kind().equals(p.kind()) && !kept.contains(o)) {
						double d = Math.hypot(o.place.x() - p.x(), o.place.z() - p.z());
						if (d <= best) {
							best = d;
							match = o;
						}
					}
				}
			}
			Place merged = p;
			if (match != null) {
				Place q = match.place;
				merged = new Place(p.id(), p.kind(), q.name().isEmpty() ? p.name() : q.name(), p.category(), p.tier(), DiscoveryState.max(q.state(), p.state()),
					p.source(), p.x(), p.y(), p.z(), p.dimension(), p.underground(), p.radius(), q.sighted() != 0 ? q.sighted() : p.sighted(),
					q.discovered() != 0 ? q.discovered() : p.discovered());
				kept.add(match);
				if (!match.place.id().equals(p.id())) {
					this.entries.remove(match.place.id());
				}
			}
			Entry e = this.entries.get(p.id());
			if (e == null) {
				e = new Entry(merged, p.kind());
				e.group = group;
				this.stamp(e);
				this.entries.put(p.id(), e);
				this.version++;
				if (merged.state().atLeast(DiscoveryState.SIGHTED) && (match == null || !match.place.state().atLeast(merged.state()))) {
					this.changes.add(merged);
				}
			} else {
				e.group = group;
				this.set(e, merged);
			}
		}
		for (Entry o : old) {
			if (!kept.contains(o) && this.entries.get(o.place.id()) == o) {
				boolean stillWanted = false;
				for (Place p : places) {
					if (p.id().equals(o.place.id())) {
						stillWanted = true;
						break;
					}
				}
				if (!stillWanted) {
					this.entries.remove(o.place.id());
					this.version++;
				}
			}
		}
	}

	/** A place from another mod or a server; its state only moves forward. */
	public synchronized Place report(Place p, long now) {
		Entry e = this.entries.get(p.id());
		if (e == null) {
			// a reported place starts where the report says (or unknown, for Atlas to judge by distance)
			e = new Entry(p.state() == DiscoveryState.UNKNOWN ? p : p.withState(p.state(), now), p.kind());
			e.group = REPORTS;
			this.stamp(e);
			this.entries.put(p.id(), e);
			this.version++;
			if (e.place.state().atLeast(DiscoveryState.SIGHTED)) {
				this.changes.add(e.place);
			}
			return e.place;
		}
		Place q = e.place;
		Place merged = new Place(p.id(), p.kind(), p.name(), p.category(), p.tier(), DiscoveryState.max(q.state(), p.state()), p.source(), p.x(), p.y(),
			p.z(), p.dimension(), p.underground(), p.radius(), q.sighted() != 0 ? q.sighted() : p.sighted(), q.discovered() != 0 ? q.discovered() : p.discovered());
		e.group = REPORTS;
		this.set(e, merged.state() == q.state() ? merged : merged.withState(merged.state(), now));
		return e.place;
	}

	public synchronized boolean forget(String id) {
		if (this.entries.remove(id) != null) {
			this.version++;
			return true;
		}
		return false;
	}

	/** Renames a place (a provider's name arrived); names are kept with the world. */
	public synchronized void rename(String id, String name) {
		Entry e = this.entries.get(id);
		if (e != null && !e.place.name().equals(name)) {
			this.set(e, e.place.withName(name));
		}
	}

	// --- moving on -------------------------------------------------------------------------------------------------

	/**
	 * The player is at (px, py, pz) in a dimension and sees {@code viewBlocks} far: moves the places round them on.
	 * Returns the places that changed.
	 */
	public synchronized List<Place> proximity(String dimension, double px, double py, double pz, int viewBlocks, long now) {
		List<Place> changed = new ArrayList<>();
		for (Entry e : this.entries.values()) {
			Place p = e.place;
			if (p.state() == DiscoveryState.DISCOVERED || !p.dimension().equals(dimension) || p.kind().equals(Place.REGION)) {
				continue;
			}
			DiscoveryState next = p.state();
			if (p.underground()) {
				double d = this.distance3d(e, px, py, pz);
				if (d <= UNDERGROUND_REACH) {
					next = DiscoveryState.DISCOVERED;
				} else if (d <= UNDERGROUND_SIGHT) {
					next = DiscoveryState.max(next, DiscoveryState.SIGHTED);
				}
			} else {
				double d = this.distance2d(e, px, pz);
				if (d <= SURFACE_REACH) {
					next = DiscoveryState.DISCOVERED;
				} else if (d <= viewBlocks) {
					next = DiscoveryState.max(next, DiscoveryState.SIGHTED);
				}
			}
			if (next != p.state()) {
				this.set(e, p.withState(next, now));
				changed.add(e.place);
			}
		}
		return changed;
	}

	/** The player walked into a place (a region): it is discovered. */
	public synchronized Place enter(String id, long now) {
		Entry e = this.entries.get(id);
		if (e == null || e.place.state() == DiscoveryState.DISCOVERED) {
			return null;
		}
		this.set(e, e.place.withState(DiscoveryState.DISCOVERED, now));
		return e.place;
	}

	/** Horizontal distance from the player to a place: to its bounds, or to its middle less its reach. */
	private double distance2d(Entry e, double px, double pz) {
		if (e.box != null) {
			double dx = Math.max(Math.max(e.box[0] - px, 0), px - e.box[3] - 1);
			double dz = Math.max(Math.max(e.box[2] - pz, 0), pz - e.box[5] - 1);
			return Math.hypot(dx, dz);
		}
		Place p = e.place;
		return Math.max(0, Math.hypot(p.x() + 0.5 - px, p.z() + 0.5 - pz) - Math.min(p.radius(), 64));
	}

	private double distance3d(Entry e, double px, double py, double pz) {
		if (e.box != null) {
			double dx = Math.max(Math.max(e.box[0] - px, 0), px - e.box[3] - 1);
			double dy = Math.max(Math.max(e.box[1] - py, 0), py - e.box[4] - 1);
			double dz = Math.max(Math.max(e.box[2] - pz, 0), pz - e.box[5] - 1);
			return Math.sqrt(dx * dx + dy * dy + dz * dz);
		}
		Place p = e.place;
		double d = Math.sqrt(sq(p.x() + 0.5 - px) + sq(p.y() + 0.5 - py) + sq(p.z() + 0.5 - pz));
		return Math.max(0, d - Math.min(p.radius(), 48));
	}

	private static double sq(double v) {
		return v * v;
	}

	/** Notes today as the day a new entry was sighted (and discovered), as far as it has come. */
	private void stamp(Entry e) {
		if (e.place.state().atLeast(DiscoveryState.SIGHTED)) {
			e.sightedDay = this.gameDay;
		}
		if (e.place.state() == DiscoveryState.DISCOVERED) {
			e.discoveredDay = this.gameDay;
		}
	}

	/** Replaces an entry's place, queueing it for the listeners if it moved on. */
	private void set(Entry e, Place next) {
		Place before = e.place;
		if (before.equals(next)) {
			return;
		}
		e.place = next;
		this.version++;
		if (next.state().atLeast(DiscoveryState.SIGHTED) && e.sightedDay < 0) {
			e.sightedDay = this.gameDay;
		}
		if (next.state() == DiscoveryState.DISCOVERED && e.discoveredDay < 0) {
			e.discoveredDay = this.gameDay;
		}
		if (next.state().ordinal() > before.state().ordinal() && next.state().atLeast(DiscoveryState.SIGHTED)) {
			this.changes.add(next);
		}
	}

	// --- files -----------------------------------------------------------------------------------------------------

	public synchronized boolean dirty() {
		return this.version != this.savedVersion;
	}

	public synchronized long version() {
		return this.version;
	}

	/** Reads {@code places.json}; with none, the structures of an older Atlas's {@code discoveries.json} next to it. */
	public synchronized void load() {
		this.entries.clear();
		Path legacy = this.file.resolveSibling("discoveries.json");
		try {
			if (Files.isRegularFile(this.file)) {
				JsonObject root = JsonParser.parseString(Files.readString(this.file, StandardCharsets.UTF_8)).getAsJsonObject();
				for (JsonElement el : root.getAsJsonArray("places")) {
					this.read(el.getAsJsonObject());
				}
			} else if (Files.isRegularFile(legacy)) {
				JsonObject root = JsonParser.parseString(Files.readString(legacy, StandardCharsets.UTF_8)).getAsJsonObject();
				for (JsonElement el : root.getAsJsonArray("discoveries")) {
					JsonObject o = el.getAsJsonObject();
					String structure = o.get("structure").getAsString();
					int x = o.get("x").getAsInt();
					int z = o.get("z").getAsInt();
					String dim = o.get("dimension").getAsString();
					String category = o.get("category").getAsString();
					long when = o.get("discovered").getAsLong();
					Place p = new Place(idFor(structure, x, z, dim), structure, o.get("name").getAsString(), category,
						StructureNames.tier(structure, category), DiscoveryState.DISCOVERED, Source.exact(), x, o.get("y").getAsInt(), z, dim,
						o.get("underground").getAsBoolean(), 0, when, when);
					this.entries.put(p.id(), new Entry(p, StructureNames.family(structure)));
				}
				this.version++;
			}
		} catch (IOException | RuntimeException ex) {
			this.entries.clear();
			this.warn.accept("Atlas: " + this.file + " is unreadable (" + ex.getMessage() + "); kept as .corrupt");
			try {
				Files.move(this.file, this.file.resolveSibling(this.file.getFileName() + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// replaced by the next save
			}
		}
		this.savedVersion = Files.isRegularFile(this.file) ? this.version : this.version - 1;
	}

	private void read(JsonObject o) {
		JsonObject src = o.getAsJsonObject("source");
		Source source = new Source(Source.Kind.valueOf(src.get("kind").getAsString()), src.get("id").getAsString());
		Place p = new Place(o.get("id").getAsString(), o.get("kind").getAsString(), o.get("name").getAsString(), o.get("category").getAsString(),
			Tier.valueOf(o.get("tier").getAsString()), DiscoveryState.valueOf(o.get("state").getAsString()), source, o.get("x").getAsInt(),
			o.get("y").getAsInt(), o.get("z").getAsInt(), o.get("dimension").getAsString(), o.get("underground").getAsBoolean(), o.get("radius").getAsInt(),
			o.get("sighted").getAsLong(), o.get("discovered").getAsLong());
		Entry e = new Entry(p, o.has("family") ? o.get("family").getAsString() : p.kind());
		if (o.has("box")) {
			JsonArray b = o.getAsJsonArray("box");
			e.box = new int[6];
			for (int i = 0; i < 6; i++) {
				e.box[i] = b.get(i).getAsInt();
			}
		}
		e.weight = o.has("weight") ? o.get("weight").getAsInt() : 1;
		e.sx = p.x();
		e.sy = p.y();
		e.sz = p.z();
		e.group = o.has("group") ? o.get("group").getAsLong() : 0;
		e.sightedDay = o.has("sightedDay") ? o.get("sightedDay").getAsLong() : -1;
		e.discoveredDay = o.has("discoveredDay") ? o.get("discoveredDay").getAsLong() : -1;
		this.entries.put(p.id(), e);
	}

	public void save() throws IOException {
		JsonObject root = new JsonObject();
		root.addProperty("version", 2);
		JsonArray list = new JsonArray();
		long version;
		synchronized (this) {
			version = this.version;
			for (Entry e : this.entries.values()) {
				Place p = e.place;
				JsonObject o = new JsonObject();
				o.addProperty("id", p.id());
				o.addProperty("kind", p.kind());
				o.addProperty("family", e.family);
				o.addProperty("name", p.name());
				o.addProperty("category", p.category());
				o.addProperty("tier", p.tier().name());
				o.addProperty("state", p.state().name());
				JsonObject src = new JsonObject();
				src.addProperty("kind", p.source().kind().name());
				src.addProperty("id", p.source().id());
				o.add("source", src);
				o.addProperty("x", p.x());
				o.addProperty("y", p.y());
				o.addProperty("z", p.z());
				o.addProperty("dimension", p.dimension());
				o.addProperty("underground", p.underground());
				o.addProperty("radius", p.radius());
				o.addProperty("sighted", p.sighted());
				o.addProperty("discovered", p.discovered());
				if (e.box != null) {
					JsonArray b = new JsonArray();
					for (int v : e.box) {
						b.add(v);
					}
					o.add("box", b);
				}
				if (e.weight > 1) {
					o.addProperty("weight", e.weight);
				}
				if (e.group != 0) {
					o.addProperty("group", e.group);
				}
				if (e.sightedDay >= 0) {
					o.addProperty("sightedDay", e.sightedDay);
				}
				if (e.discoveredDay >= 0) {
					o.addProperty("discoveredDay", e.discoveredDay);
				}
				list.add(o);
			}
		}
		root.add("places", list);
		RegionStore.write(this.file, new GsonBuilder().create().toJson(root).getBytes(StandardCharsets.UTF_8));
		synchronized (this) {
			this.savedVersion = version;
		}
	}
}
