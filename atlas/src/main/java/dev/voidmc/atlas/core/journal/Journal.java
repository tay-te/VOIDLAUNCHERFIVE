package dev.voidmc.atlas.core.journal;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.atlas.api.DiscoveryState;
import dev.voidmc.atlas.api.Place;
import dev.voidmc.atlas.core.StructureNames;
import dev.voidmc.atlas.core.journal.JournalEvent.Kind;
import dev.voidmc.atlas.core.store.SafeFile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One world map's journal: a timeline of the player's firsts (a place discovered, a biome area entered, a dimension
 * visited, a new deepest Y, every kilometre travelled) and the numbers behind them (distance per dimension, deepest
 * and highest Y, places by kind and state, the game days played, how much of each biome area is explored).
 *
 * <p>Thread-safe: anything may be appended from any thread (chunk workers discover places, the client thread offers
 * the player's position about once a second, the screen reads pages and stats). One lock guards it all; every call is
 * short.
 *
 * <p>The timeline keeps at most {@link #cap()} events. Past that, the oldest are merged into one summary per game day
 * ("Day 12: 3 discoveries, 2 new areas, travelled 1 km"), so the history is shortened, never dropped. Each first
 * (a place, an area, a dimension) is remembered apart from the timeline, so it is never told twice even after its
 * event has been summarised.
 *
 * <p>Distance is horizontal only, summed from the positions offered: a step shorter than {@link #JITTER} is not counted
 * (standing still wobbles a little), and a step longer than {@link #TELEPORT} is not either (a teleport, a respawn, a
 * position offered after a long pause); nor is the first step in a dimension.
 *
 * <p>Kept in a {@code journal} file through {@link SafeFile} (checked, backed up, restored from a backup if damaged),
 * as compact JSON.
 */
public final class Journal {
	public static final String KIND = "journal";
	public static final int VERSION = 1;
	public static final int DEFAULT_CAP = 5000;
	/** A step between two positions longer than this many blocks is a teleport, not travel. */
	public static final double TELEPORT = 64;
	/** A step shorter than this many blocks is a wobble, not travel. */
	public static final double JITTER = 0.5;
	/** A distance event every this many blocks. */
	public static final double MILESTONE = 1000;
	/** How many points of one area's history are kept (one per game day, the oldest dropped). */
	public static final int HISTORY_KEPT = 256;
	/** How many areas {@link #stats()} lists. */
	public static final int TOP_AREAS = 10;
	private static final String OVERWORLD = "minecraft:overworld";

	private final int cap;
	/** Oldest first. */
	private final ArrayList<JournalEvent> events = new ArrayList<>();
	private final Set<String> placesFound = new HashSet<>();
	private final Set<String> areasEntered = new HashSet<>();
	private final LinkedHashSet<String> dimensions = new LinkedHashSet<>();
	private final Map<String, Double> distance = new LinkedHashMap<>();
	private double total;
	private long milestones;
	private final Map<String, Integer> deepest = new LinkedHashMap<>();
	private final Map<String, Integer> highest = new LinkedHashMap<>();
	private Map<String, Map<DiscoveryState, Integer>> places = Map.of();
	private long firstDay = -1;
	private long lastDay = -1;
	private final Map<String, Area> areas = new LinkedHashMap<>();

	/** Where travel is measured from: the last counted position (not persisted; a new session starts afresh). */
	private String anchorDimension;
	private double anchorX;
	private double anchorZ;

	private long version;
	private long savedVersion;
	private final Object saveLock = new Object();

	/** An area's latest counts and their history; guarded by the journal's lock. */
	private static final class Area {
		final String id;
		String name;
		String biome;
		int total;
		int explored;
		final ArrayList<AreaStat.Point> history = new ArrayList<>();

		Area(String id) {
			this.id = id;
		}

		AreaStat snapshot() {
			return new AreaStat(this.id, this.name, this.biome, this.total, this.explored, this.history);
		}
	}

	public Journal() {
		this(DEFAULT_CAP);
	}

	/** A journal keeping at most {@code cap} events before it summarises the oldest. */
	public Journal(int cap) {
		if (cap < 2) {
			throw new IllegalArgumentException("cap " + cap);
		}
		this.cap = cap;
	}

	public int cap() {
		return this.cap;
	}

	// ---------------------------------------------------------------- events

	/**
	 * A place came within reach. Returns its event, or null if this place was discovered before (each place is told
	 * once). {@code name} may be blank: the kind is named instead.
	 */
	public synchronized JournalEvent placeDiscovered(String placeId, String name, String kind, String dimension, int x, int y, int z, long day,
		long epochMs) {
		Objects.requireNonNull(placeId, "placeId");
		this.noteDay(day);
		if (!this.placesFound.add(placeId)) {
			return null;
		}
		String what = name != null && !name.isBlank() ? name : kind != null ? StructureNames.name(kind) : "a place";
		return this.append(JournalEvent.of(Kind.DISCOVERY, "Discovered " + what, day, epochMs, dim(dimension), x, y, z, placeId, kind));
	}

	/** {@link #placeDiscovered(String, String, String, String, int, int, int, long, long)} for a place as Atlas keeps it. */
	public JournalEvent placeDiscovered(Place place, long day, long epochMs) {
		return this.placeDiscovered(place.id(), place.name(), place.kind(), place.dimension(), place.x(), place.y(), place.z(), day, epochMs);
	}

	/**
	 * The player entered a biome area (a named stretch of one biome). Returns its event, or null if they had been in it
	 * before. {@code name} may be blank: the biome is named instead.
	 */
	public synchronized JournalEvent areaEntered(String areaId, String name, String biome, String dimension, int x, int y, int z, long day,
		long epochMs) {
		Objects.requireNonNull(areaId, "areaId");
		this.noteDay(day);
		if (!this.areasEntered.add(areaId)) {
			return null;
		}
		String what = name != null && !name.isBlank() ? name : biome != null ? pretty(biome) : "a new area";
		return this.append(JournalEvent.of(Kind.AREA, "Entered " + what, day, epochMs, dim(dimension), x, y, z, null, biome));
	}

	/**
	 * The player is in {@code dimension}: its event if this is the first time, else null. {@link #sample} calls this
	 * itself; it is here for callers that learn of a dimension change before the next position.
	 */
	public synchronized JournalEvent dimensionVisited(String dimension, int x, int y, int z, long day, long epochMs) {
		this.noteDay(day);
		String d = dim(dimension);
		if (!this.dimensions.add(d)) {
			return null;
		}
		return this.append(JournalEvent.of(Kind.DIMENSION, "Visited " + dimensionName(d), day, epochMs, d, x, y, z, null, d));
	}

	/**
	 * The player's position, offered about once a second. Counts the horizontal distance since the last counted
	 * position (see the class notes for what is not counted), keeps the deepest and highest Y, and returns the events it
	 * made: a first visit to the dimension, a new deepest Y, another kilometre. Usually none.
	 *
	 * <p>The first position in a dimension sets its deepest and highest Y without an event; after that a new deepest
	 * Y is an event ("Reached Y -54"). Digging down makes one event, not one per block: while the newest event is that
	 * dimension's depth record it is moved down instead of a new one added.
	 */
	public synchronized List<JournalEvent> sample(String dimension, double x, double y, double z, long day, long epochMs) {
		List<JournalEvent> out = new ArrayList<>(1);
		String d = dim(dimension);
		int bx = (int) Math.floor(x);
		int by = (int) Math.floor(y);
		int bz = (int) Math.floor(z);
		boolean changed = this.noteDay(day);
		JournalEvent visit = this.dimensionVisited(d, bx, by, bz, day, epochMs);
		if (visit != null) {
			out.add(visit);
		}

		Integer low = this.deepest.get(d);
		if (low == null) {
			this.deepest.put(d, by);
			this.highest.put(d, by);
			changed = true;
		} else {
			if (by > this.highest.get(d)) {
				this.highest.put(d, by);
				changed = true;
			}
			if (by < low) {
				this.deepest.put(d, by);
				changed = true;
				JournalEvent record = JournalEvent.of(Kind.DEPTH, "Reached Y " + by, day, epochMs, d, bx, by, bz, null, null);
				int last = this.events.size() - 1;
				if (last >= 0 && this.events.get(last).kind() == Kind.DEPTH && d.equals(this.events.get(last).dimension())) {
					this.events.set(last, record);
				} else {
					this.append(record);
				}
				out.add(record);
			}
		}

		if (!d.equals(this.anchorDimension)) {
			this.anchorDimension = d;
			this.anchorX = x;
			this.anchorZ = z;
		} else {
			double step = Math.hypot(x - this.anchorX, z - this.anchorZ);
			if (step > TELEPORT) {
				this.anchorX = x;
				this.anchorZ = z;
			} else if (step >= JITTER) {
				this.anchorX = x;
				this.anchorZ = z;
				this.distance.merge(d, step, Double::sum);
				this.total += step;
				changed = true;
				while (Math.floor(this.total / MILESTONE) > this.milestones) {
					this.milestones++;
					out.add(this.append(JournalEvent.of(Kind.DISTANCE, "Travelled " + this.milestones + " km", day, epochMs, d, bx, by, bz, null, null)));
				}
			}
		}
		if (changed) {
			this.version++;
		}
		return out;
	}

	private JournalEvent append(JournalEvent e) {
		this.events.add(e);
		this.compact();
		this.version++;
		return e;
	}

	/**
	 * Past the cap, merges the oldest events into one summary per game day until the timeline is a tenth under the
	 * cap (so this runs once every few hundred events, not on each). Only as many are merged as must be: the newest
	 * events are never summarised. Where no day has two events left to merge (a run of quiet days, or nothing but
	 * summaries), the two oldest lines are merged into one covering both their days.
	 */
	private void compact() {
		if (this.events.size() <= this.cap) {
			return;
		}
		int target = Math.max(1, this.cap - Math.max(1, this.cap / 10));
		while (this.events.size() > target) {
			int i = 0;
			while (i < this.events.size() && this.events.get(i).isSummary()) {
				i++;
			}
			if (i < this.events.size()) {
				// the oldest plain events of one day, as many as must go, and that day's summary if it is just before
				long day = this.events.get(i).day();
				int end = Math.min(this.events.size(), i + 1 + (this.events.size() - target));
				int j = i + 1;
				while (j < end && !this.events.get(j).isSummary() && this.events.get(j).day() == day) {
					j++;
				}
				boolean intoBefore = i > 0 && this.events.get(i - 1).day() == day && this.events.get(i - 1).lastDay() == day;
				if (j - i >= 2 || intoBefore) {
					JournalEvent summary = JournalEvent.summaryOf(this.events.get(i));
					for (int k = i + 1; k < j; k++) {
						summary = JournalEvent.merge(summary, this.events.get(k));
					}
					int start = i;
					if (intoBefore) {
						summary = JournalEvent.merge(this.events.get(i - 1), summary);
						start = i - 1;
					}
					this.events.subList(start + 1, j).clear();
					this.events.set(start, summary);
					continue;
				}
			}
			// nothing to merge within one day: the two oldest lines become one summary covering both their days
			this.events.set(0, JournalEvent.merge(this.events.get(0), this.events.get(1)));
			this.events.remove(1);
		}
	}

	private boolean noteDay(long day) {
		if (day < 0) {
			return false;
		}
		boolean changed = false;
		if (this.firstDay < 0 || day < this.firstDay) {
			this.firstDay = day;
			changed = true;
		}
		if (day > this.lastDay) {
			this.lastDay = day;
			changed = true;
		}
		if (changed) {
			this.version++;
		}
		return changed;
	}

	// ---------------------------------------------------------------- reading the timeline

	/** How many lines the timeline has (a summary is one line). */
	public synchronized int size() {
		return this.events.size();
	}

	/** How many events the timeline stands for, counting everything inside summaries. */
	public synchronized int eventCount() {
		int n = 0;
		for (JournalEvent e : this.events) {
			n += e.count();
		}
		return n;
	}

	/** Up to {@code limit} lines, newest first, skipping the newest {@code offset}. Empty past the end. */
	public synchronized List<JournalEvent> page(int offset, int limit) {
		if (offset < 0 || limit < 0) {
			throw new IllegalArgumentException("offset " + offset + ", limit " + limit);
		}
		int n = this.events.size();
		List<JournalEvent> out = new ArrayList<>(Math.max(0, Math.min(limit, n - offset)));
		for (int i = n - 1 - offset; i >= 0 && out.size() < limit; i--) {
			out.add(this.events.get(i));
		}
		return out;
	}

	/** The newest {@code limit} lines, newest first. */
	public List<JournalEvent> newest(int limit) {
		return this.page(0, limit);
	}

	/** The whole timeline, newest first. */
	public synchronized List<JournalEvent> all() {
		return this.page(0, this.events.size());
	}

	// ---------------------------------------------------------------- places and areas

	/** The places' counts by kind and state, as the caller keeps them (replaces the last ones given). */
	public synchronized void setPlaceCounts(Map<String, Map<DiscoveryState, Integer>> counts) {
		Map<String, Map<DiscoveryState, Integer>> copy = new HashMap<>();
		for (Map.Entry<String, Map<DiscoveryState, Integer>> e : counts.entrySet()) {
			Map<DiscoveryState, Integer> byState = new EnumMap<>(DiscoveryState.class);
			e.getValue().forEach((state, n) -> {
				if (state != null && n != null && n > 0) {
					byState.put(state, n);
				}
			});
			if (e.getKey() != null && !byState.isEmpty()) {
				copy.put(e.getKey(), Map.copyOf(byState));
			}
		}
		Map<String, Map<DiscoveryState, Integer>> next = Map.copyOf(copy);
		if (!next.equals(this.places)) {
			this.places = next;
			this.version++;
		}
	}

	/** Counts {@code all} by kind and state and keeps that (see {@link #setPlaceCounts}). */
	public void countPlaces(Collection<Place> all) {
		Map<String, Map<DiscoveryState, Integer>> counts = new HashMap<>();
		for (Place p : all) {
			counts.computeIfAbsent(p.kind(), k -> new EnumMap<>(DiscoveryState.class)).merge(p.state(), 1, Integer::sum);
		}
		this.setPlaceCounts(counts);
	}

	/**
	 * The map's latest count of a biome area's cells: {@code total} in it, {@code explored} of them seen. Keeps it as the
	 * area's latest and adds it to the area's history (one point per game day: a later count the same day replaces
	 * that day's point). Returns the area as it now stands.
	 */
	public synchronized AreaStat areaProgress(String areaId, String name, String biome, int total, int explored, long day, long epochMs) {
		Objects.requireNonNull(areaId, "areaId");
		this.noteDay(day);
		total = Math.max(0, total);
		explored = Math.max(0, Math.min(explored, total));
		Area a = this.areas.computeIfAbsent(areaId, Area::new);
		boolean changed = false;
		if (name != null && !name.isBlank() && !name.equals(a.name)) {
			a.name = name;
			changed = true;
		}
		if (biome != null && !biome.isBlank() && !biome.equals(a.biome)) {
			a.biome = biome;
			changed = true;
		}
		if (a.history.isEmpty() || a.total != total || a.explored != explored) {
			a.total = total;
			a.explored = explored;
			AreaStat.Point point = new AreaStat.Point(day, epochMs, explored, total);
			int last = a.history.size() - 1;
			if (last >= 0 && day >= 0 && a.history.get(last).day() == day) {
				a.history.set(last, point);
			} else {
				a.history.add(point);
				if (a.history.size() > HISTORY_KEPT) {
					a.history.subList(0, a.history.size() - HISTORY_KEPT).clear();
				}
			}
			changed = true;
		}
		if (changed) {
			this.version++;
		}
		return a.snapshot();
	}

	/** One area as it stands, or null if the map has not counted it. */
	public synchronized AreaStat area(String areaId) {
		Area a = this.areas.get(areaId);
		return a == null ? null : a.snapshot();
	}

	/** Every area counted, in the order first counted. */
	public synchronized List<AreaStat> areas() {
		List<AreaStat> out = new ArrayList<>(this.areas.size());
		for (Area a : this.areas.values()) {
			out.add(a.snapshot());
		}
		return out;
	}

	/** The {@code n} areas explored furthest by share (then by cells explored); empty areas are left out. */
	public synchronized List<AreaStat> topAreas(int n) {
		return this.areas.values().stream()
			.filter(a -> a.total > 0)
			.map(Area::snapshot)
			.sorted(Comparator.comparingDouble((AreaStat a) -> a.share()).reversed()
				.thenComparing(Comparator.comparingInt(AreaStat::explored).reversed())
				.thenComparing(AreaStat::id))
			.limit(Math.max(0, n))
			.toList();
	}

	// ---------------------------------------------------------------- stats

	public synchronized JournalStats stats() {
		Integer low = this.deepest.values().stream().min(Integer::compare).orElse(null);
		Integer high = this.highest.values().stream().max(Integer::compare).orElse(null);
		return new JournalStats(this.total, this.distance, low, high, this.deepest, this.highest, this.places, this.firstDay, this.lastDay,
			new ArrayList<>(this.dimensions), this.areasEntered.size(), this.eventCount(), this.topAreas(TOP_AREAS));
	}

	/** Goes up on every change; the screen can redraw when it moves. */
	public synchronized long version() {
		return this.version;
	}

	/** Whether there are changes not yet saved. */
	public synchronized boolean dirty() {
		return this.version != this.savedVersion;
	}

	// ---------------------------------------------------------------- persistence

	/** Saves to {@code file}: the old file goes to the backups first (if one is due), then the new one is written whole. */
	public void save(SafeFile files, Path file) throws IOException {
		synchronized (this.saveLock) {
			String json;
			long saving;
			synchronized (this) {
				json = this.toJson();
				saving = this.version;
			}
			files.backup(file);
			files.write(file, KIND, VERSION, json);
			synchronized (this) {
				this.savedVersion = saving;
			}
		}
	}

	/**
	 * Reads a journal from {@code file}. A damaged file is replaced by its newest readable backup (SafeFile does that,
	 * and tells its listener); with no file, or nothing readable, the journal starts empty.
	 */
	public static Journal load(SafeFile files, Path file) {
		return load(files, file, DEFAULT_CAP);
	}

	public static Journal load(SafeFile files, Path file, int cap) {
		Journal[] parsed = new Journal[1];
		SafeFile.Result r = files.read(file, KIND, VERSION, s -> {
			try {
				parsed[0] = fromJson(s, cap);
				return true;
			} catch (RuntimeException e) {
				return false;
			}
		});
		return r.present() && parsed[0] != null ? parsed[0] : new Journal(cap);
	}

	/** The journal as compact JSON (the payload of its file). */
	public synchronized String toJson() {
		JsonObject root = new JsonObject();
		root.addProperty("v", VERSION);
		JsonArray list = new JsonArray();
		for (JournalEvent e : this.events) {
			JsonArray a = new JsonArray();
			a.add(e.kind().name());
			a.add(e.text());
			a.add(e.day());
			a.add(e.epoch());
			a.add(e.dimension());
			a.add(e.x());
			a.add(e.y());
			a.add(e.z());
			a.add(e.placeId());
			a.add(e.subject());
			if (e.isSummary()) {
				a.add(e.lastDay());
				JsonObject tally = new JsonObject();
				for (Kind k : Kind.values()) {
					int n = e.tally().getOrDefault(k, 0);
					if (n > 0) {
						tally.addProperty(k.name(), n);
					}
				}
				a.add(tally);
			}
			list.add(a);
		}
		root.add("events", list);
		root.add("found", strings(this.placesFound));
		root.add("entered", strings(this.areasEntered));
		root.add("dims", strings(this.dimensions));
		JsonObject dist = new JsonObject();
		this.distance.forEach(dist::addProperty);
		root.add("dist", dist);
		JsonObject deep = new JsonObject();
		this.deepest.forEach(deep::addProperty);
		root.add("deep", deep);
		JsonObject high = new JsonObject();
		this.highest.forEach(high::addProperty);
		root.add("high", high);
		JsonObject placeCounts = new JsonObject();
		this.places.forEach((kind, byState) -> {
			JsonObject o = new JsonObject();
			for (DiscoveryState s : DiscoveryState.values()) {
				Integer n = byState.get(s);
				if (n != null) {
					o.addProperty(s.name(), n);
				}
			}
			placeCounts.add(kind, o);
		});
		root.add("places", placeCounts);
		JsonArray days = new JsonArray();
		days.add(this.firstDay);
		days.add(this.lastDay);
		root.add("days", days);
		JsonArray areaList = new JsonArray();
		for (Area a : this.areas.values()) {
			JsonObject o = new JsonObject();
			o.addProperty("id", a.id);
			if (a.name != null) {
				o.addProperty("name", a.name);
			}
			if (a.biome != null) {
				o.addProperty("biome", a.biome);
			}
			o.addProperty("t", a.total);
			o.addProperty("e", a.explored);
			JsonArray h = new JsonArray();
			for (AreaStat.Point p : a.history) {
				JsonArray pa = new JsonArray();
				pa.add(p.day());
				pa.add(p.epoch());
				pa.add(p.explored());
				pa.add(p.total());
				h.add(pa);
			}
			o.add("h", h);
			areaList.add(o);
		}
		root.add("areas", areaList);
		return new GsonBuilder().create().toJson(root);
	}

	/** A journal from its JSON. Throws on JSON that is not a journal; skips an event of a kind this build does not know. */
	public static Journal fromJson(String json, int cap) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		if (!root.has("events")) {
			throw new IllegalArgumentException("not a journal");
		}
		Journal j = new Journal(cap);
		for (JsonElement el : root.getAsJsonArray("events")) {
			JsonArray a = el.getAsJsonArray();
			Kind kind;
			try {
				kind = Kind.valueOf(a.get(0).getAsString());
			} catch (IllegalArgumentException e) {
				continue;
			}
			long day = a.get(2).getAsLong();
			long lastDay = day;
			Map<Kind, Integer> tally = Map.of();
			if (kind == Kind.SUMMARY && a.size() > 11) {
				lastDay = a.get(10).getAsLong();
				Map<Kind, Integer> t = new EnumMap<>(Kind.class);
				for (Map.Entry<String, JsonElement> e : a.get(11).getAsJsonObject().entrySet()) {
					try {
						t.put(Kind.valueOf(e.getKey()), e.getValue().getAsInt());
					} catch (IllegalArgumentException ignored) {
						// a kind this build does not know
					}
				}
				tally = t;
			}
			j.events.add(new JournalEvent(kind, str(a.get(1)), day, lastDay, a.get(3).getAsLong(), str(a.get(4)), a.get(5).getAsInt(), a.get(6).getAsInt(),
				a.get(7).getAsInt(), str(a.get(8)), str(a.get(9)), tally));
		}
		readStrings(root, "found", j.placesFound);
		readStrings(root, "entered", j.areasEntered);
		readStrings(root, "dims", j.dimensions);
		if (root.has("dist")) {
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("dist").entrySet()) {
				double d = e.getValue().getAsDouble();
				if (Double.isFinite(d) && d > 0) {
					j.distance.put(e.getKey(), d);
					j.total += d;
				}
			}
		}
		if (root.has("deep")) {
			root.getAsJsonObject("deep").entrySet().forEach(e -> j.deepest.put(e.getKey(), e.getValue().getAsInt()));
		}
		if (root.has("high")) {
			root.getAsJsonObject("high").entrySet().forEach(e -> j.highest.put(e.getKey(), e.getValue().getAsInt()));
		}
		for (String d : j.deepest.keySet()) {
			j.highest.putIfAbsent(d, j.deepest.get(d));
		}
		for (String d : j.highest.keySet()) {
			j.deepest.putIfAbsent(d, j.highest.get(d));
		}
		if (root.has("places")) {
			Map<String, Map<DiscoveryState, Integer>> counts = new HashMap<>();
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("places").entrySet()) {
				Map<DiscoveryState, Integer> byState = new EnumMap<>(DiscoveryState.class);
				for (Map.Entry<String, JsonElement> s : e.getValue().getAsJsonObject().entrySet()) {
					try {
						byState.put(DiscoveryState.valueOf(s.getKey()), s.getValue().getAsInt());
					} catch (IllegalArgumentException ignored) {
						// a state this build does not know
					}
				}
				counts.put(e.getKey(), byState);
			}
			j.setPlaceCounts(counts);
		}
		if (root.has("days")) {
			JsonArray days = root.getAsJsonArray("days");
			j.firstDay = days.get(0).getAsLong();
			j.lastDay = days.get(1).getAsLong();
		}
		if (root.has("areas")) {
			for (JsonElement el : root.getAsJsonArray("areas")) {
				JsonObject o = el.getAsJsonObject();
				Area a = new Area(o.get("id").getAsString());
				a.name = o.has("name") ? o.get("name").getAsString() : null;
				a.biome = o.has("biome") ? o.get("biome").getAsString() : null;
				a.total = Math.max(0, o.get("t").getAsInt());
				a.explored = Math.max(0, Math.min(o.get("e").getAsInt(), a.total));
				if (o.has("h")) {
					for (JsonElement p : o.getAsJsonArray("h")) {
						JsonArray pa = p.getAsJsonArray();
						a.history.add(new AreaStat.Point(pa.get(0).getAsLong(), pa.get(1).getAsLong(), pa.get(2).getAsInt(), pa.get(3).getAsInt()));
					}
				}
				j.areas.put(a.id, a);
			}
		}
		j.milestones = (long) Math.floor(j.total / MILESTONE);
		j.compact();
		j.savedVersion = j.version;
		return j;
	}

	private static JsonArray strings(Collection<String> values) {
		JsonArray a = new JsonArray();
		values.stream().sorted().forEach(a::add);
		return a;
	}

	private static void readStrings(JsonObject root, String key, Collection<String> into) {
		if (root.has(key)) {
			for (JsonElement el : root.getAsJsonArray(key)) {
				into.add(el.getAsString());
			}
		}
	}

	private static String str(JsonElement el) {
		return el == null || el.isJsonNull() ? null : el.getAsString();
	}

	// ---------------------------------------------------------------- names

	private static String dim(String dimension) {
		return dimension == null || dimension.isBlank() ? OVERWORLD : dimension;
	}

	/** "the Overworld", "the Nether", "the End"; any other dimension by its id's name ("Aether"). */
	static String dimensionName(String dimension) {
		return switch (dimension) {
			case OVERWORLD -> "the Overworld";
			case "minecraft:the_nether" -> "the Nether";
			case "minecraft:the_end" -> "the End";
			default -> pretty(dimension);
		};
	}

	/** {@code minecraft:dark_forest} to "Dark forest". */
	static String pretty(String id) {
		String path = id.substring(id.indexOf(':') + 1);
		int slash = path.lastIndexOf('/');
		if (slash >= 0) {
			path = path.substring(slash + 1);
		}
		String s = path.replace('_', ' ').strip();
		return s.isEmpty() ? id : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
	}
}
