package dev.voidmc.atlas.client;

import dev.voidmc.atlas.api.AtlasSocialTransport;
import dev.voidmc.atlas.api.MapLayer;
import dev.voidmc.atlas.api.Marker;
import dev.voidmc.atlas.api.MarkerGlyph;
import dev.voidmc.atlas.api.Privacy;
import dev.voidmc.atlas.client.data.DimensionData;
import dev.voidmc.atlas.client.data.LayerData;
import dev.voidmc.atlas.client.data.MapSession;
import dev.voidmc.atlas.core.Coords;
import dev.voidmc.atlas.core.RegionRaw;
import dev.voidmc.atlas.core.social.FriendPositions;
import dev.voidmc.atlas.core.social.NoopTransport;
import dev.voidmc.atlas.core.social.Roster;
import dev.voidmc.atlas.core.social.ServerHash;
import dev.voidmc.atlas.core.social.SharedLayer;
import dev.voidmc.atlas.core.social.SharedTiles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * VOID friends on the map: friends' positions as markers (the Friends layer), and the land they explored, shared at
 * lower detail into its own layer ({@value #LAYER}) under the walked land, never over it, each column kept with who
 * mapped it. All of it through a transport another mod hands over ({@code AtlasPlugin.socialTransport()}, the VOID
 * launcher's bridge to begin with); with none, or none {@linkplain AtlasSocialTransport#available() available}, none
 * of it shows. Who shares with whom is the player's privacy setting (Friends, Party, Nobody; Party by default), enforced
 * here both ways.
 */
public final class Social {
	public static final String LAYER = "shared";
	/** Friends go on the Friends layer chip (the players' layer). */
	public static final String FRIENDS_LAYER = MapLayer.PLAYERS;
	/** A friend's marker id: this and their id on the transport. */
	public static final String FRIEND_PREFIX = "atlas:friend:";
	/** Shared tiles at 2 x 2 columns a sample: a friend's land, in less detail than one's own. */
	public static final int SHARE_LEVEL = 1;

	private final AtlasClient atlas;
	private final Roster roster = new Roster();
	private volatile AtlasSocialTransport transport = NoopTransport.INSTANCE;
	private FriendPositions positions;
	private SharedTiles.Outbox outbox;
	private SharedTiles.Inbox inbox;
	private final List<AutoCloseable> subscriptions = new ArrayList<>();
	private MapSession wiredTo;
	private String presenceKey = "";
	private boolean layerShown;
	private final Map<Long, Long> merged = new HashMap<>();
	private final AtomicBoolean merging = new AtomicBoolean();

	Social(AtlasClient atlas) {
		this.atlas = atlas;
	}

	/** A plugin's transport (start-up): the first available one wins. */
	void offer(AtlasSocialTransport t) {
		if (t != null && !this.transport.available()) {
			this.transport = t;
		}
	}

	/** Whether friend features show: a transport is there and up. */
	public boolean available() {
		try {
			return this.transport.available();
		} catch (RuntimeException e) {
			return false;
		}
	}

	public Privacy privacy() {
		return Privacy.parse(this.atlas.settings().sharePrivacy);
	}

	/** The friends' markers now (empty without a transport). */
	public List<FriendPositions.Marker> friends() {
		FriendPositions p = this.positions;
		return p == null || !this.available() ? List.of() : p.snapshot(System.currentTimeMillis());
	}

	/** Once a second (client thread). */
	void tick(MapSession session, LocalPlayer p, String dimension) {
		if (!this.available()) {
			this.unwire();
			return;
		}
		String me = this.transport.localPlayer();
		if (me == null) {
			return;
		}
		if (this.wiredTo != session) {
			this.wire(session, me);
		}
		this.roster.setFriends(this.transport.friends());
		this.roster.setParty(this.transport.party());
		Minecraft mc = Minecraft.getInstance();
		String server = this.atlas.multiplayer() && mc.getCurrentServer() != null ? ServerHash.of(mc.getCurrentServer().ip) : null;
		Privacy privacy = this.privacy();
		boolean allowed = server != null && this.atlas.rules().minimapAllowed();
		Privacy effective = allowed ? privacy : Privacy.NOBODY;
		String key = server + "|" + dimension + "|" + effective;
		if (!key.equals(this.presenceKey)) {
			this.presenceKey = key;
			this.positions.local(server, effective);
			this.outbox.local(server, effective);
			this.inbox.local(server, effective);
			this.transport.presence(new AtlasSocialTransport.Presence(true, true, server, dimension, effective));
		}
		SharedTiles.Outbox box = this.outbox;
		AtlasSocialTransport t = this.transport;
		if (this.merging.compareAndSet(false, true)) {
			try {
				session.workers.execute(() -> {
					try {
						if (!PerfGovernor.paused() && this.atlas.settings().shareMap) {
							// Light, or map sharing off: party tiles wait, both ways
							box.pump(t, System.currentTimeMillis());
							this.mergeShared(session, dimension);
						}
					} finally {
						this.merging.set(false);
					}
				});
			} catch (RuntimeException e) {
				this.merging.set(false);
			}
		}
	}

	/** Twice a second (client thread): where the player is, to those the privacy setting allows. */
	void positions(LocalPlayer p, String dimension) {
		FriendPositions fp = this.positions;
		if (fp == null || !this.available()) {
			return;
		}
		byte[] out = fp.outgoing(p.getName().getString(), dimension, p.getX(), p.getY(), p.getZ(), p.getYRot(), System.currentTimeMillis());
		if (out != null) {
			this.transport.publish(AtlasSocialTransport.TOPIC_POSITION, out);
		}
	}

	/** The transport's friends list (empty without one). */
	public List<AtlasSocialTransport.Friend> roster() {
		if (!this.available()) {
			return List.of();
		}
		try {
			return this.transport.roster();
		} catch (RuntimeException e) {
			return List.of();
		}
	}

	/** A friend's entry in the list, or null. */
	public AtlasSocialTransport.Friend friend(String id) {
		for (AtlasSocialTransport.Friend f : this.roster()) {
			if (f.id().equals(id)) {
				return f;
			}
		}
		return null;
	}

	/** A friend's Minecraft profile id (for the face), or null. */
	public java.util.UUID profileOf(String id) {
		AtlasSocialTransport.Friend f = this.friend(id);
		return f == null ? null : f.minecraft();
	}

	/** Where a friend is now (same world only), or null. */
	public FriendPositions.Marker position(String id) {
		for (FriendPositions.Marker m : this.friends()) {
			if (m.player().equals(id)) {
				return m;
			}
		}
		return null;
	}

	/** Asks to join a friend's party. */
	public boolean joinParty(String id) {
		try {
			return this.available() && this.transport.joinParty(id);
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** Sends a waypoint to friends (the share code), as the privacy setting allows. */
	public boolean shareWaypoint(dev.voidmc.atlas.api.Waypoint w) {
		Minecraft mc = Minecraft.getInstance();
		String me = this.available() ? this.transport.localPlayer() : null;
		if (me == null || mc.getCurrentServer() == null || this.privacy() == Privacy.NOBODY) {
			return false;
		}
		String server = ServerHash.of(mc.getCurrentServer().ip);
		this.transport.publish(AtlasSocialTransport.TOPIC_WAYPOINT, dev.voidmc.atlas.core.social.WaypointMessages.encode(
			new dev.voidmc.atlas.core.social.WaypointMessages.Message(me, server, List.of(dev.voidmc.atlas.core.waypoints.SharedWaypoint.from(w)))));
		return true;
	}

	/** A walked region changed (worker): it may go out to friends, within the byte budget. */
	void walkedChanged(String dimension, RegionRaw raw, int x0, int z0, int w, int h) {
		SharedTiles.Outbox box = this.outbox;
		if (box != null && this.available() && this.atlas.settings().shareMap) {
			box.changed(dimension, raw, x0, z0, w, h);
		}
	}

	private void wire(MapSession session, String me) {
		this.unwire();
		this.positions = new FriendPositions(me, this.roster);
		this.outbox = new SharedTiles.Outbox(me, session.blockNames, session.biomeNames).level(SHARE_LEVEL);
		this.inbox = new SharedTiles.Inbox(me, this.roster, session.blockNames, session.biomeNames);
		this.subscriptions.add(this.positions.listen(this.transport, System::currentTimeMillis));
		this.subscriptions.add(this.inbox.listen(this.transport));
		this.wiredTo = session;
		this.presenceKey = "";
		this.merged.clear();
		this.regionSenders.clear();
		this.sharedSummary = List.of();
		this.atlas.markers.registerProvider("atlas:friends", 0, (query, sink) -> {
			for (FriendPositions.Marker f : this.friends()) {
				if (f.dimension().equals(query.dimension())) {
					String meta = f.faded() ? "a while ago" : dev.voidmc.atlas.client.ui.MarkerPainter.distance(Math.hypot(f.x() - query.playerX(), f.z() - query.playerZ()));
					sink.accept(new Marker(FRIEND_PREFIX + f.player(), FRIENDS_LAYER, f.name(), meta, "FRIEND", f.x(), f.y(),
						f.z(), f.dimension(), MarkerGlyph.DOT, Marker.Style.PLAYER, 0xFF3DD68C, null));
				}
			}
		});
	}

	private void unwire() {
		if (this.wiredTo == null) {
			return;
		}
		for (AutoCloseable c : this.subscriptions) {
			try {
				c.close();
			} catch (Exception e) {
				// the transport is going away
			}
		}
		this.subscriptions.clear();
		this.atlas.markers.unregisterProvider("atlas:friends");
		this.wiredTo = null;
		this.positions = null;
		this.outbox = null;
		this.inbox = null;
	}

	/**
	 * Friends' land into the shared layer (worker): each shared region newer than last time, only where the walked map
	 * has nothing. Who mapped a column stays in the inbox ({@link #mappedBy}).
	 */
	private void mergeShared(MapSession session, String dimension) {
		SharedTiles.Inbox in = this.inbox;
		DimensionData d = session.dimensionIfOpen(dimension);
		if (in == null || d == null || d.ceiling) {
			return;
		}
		LayerData shared = d.layer(LAYER);
		LayerData walked = d.layer(DimensionData.SURFACE);
		for (SharedLayer s : in.layers(dimension)) {
			long key = Coords.pack(s.x, s.z);
			Long before = this.merged.get(key);
			if (before != null && before == s.version()) {
				continue;
			}
			this.merged.put(key, s.version());
			RegionRaw own = walked.raw(s.x, s.z, false);
			RegionRaw raw = shared.raw(s.x, s.z, true);
			boolean changed = false;
			Map<String, int[]> counts = new HashMap<>();
			for (int i = 0; i < SharedLayer.AREA; i++) {
				if (!s.known(i)) {
					continue;
				}
				String by = s.sender(i);
				if (by != null) {
					counts.computeIfAbsent(by, k -> new int[1])[0]++;
				}
				if (own != null && own.known(i)) {
					continue;
				}
				changed |= raw.set(i & (RegionRaw.SIZE - 1), i >> Coords.REGION_SHIFT, s.height[i], s.block[i], s.depth(i), s.biome[i],
					RegionRaw.KNOWN | s.flags[i] & RegionRaw.ROCK);
			}
			if (changed) {
				int bx = s.x * RegionRaw.SIZE;
				int bz = s.z * RegionRaw.SIZE;
				shared.dirty(bx, bz, bx + RegionRaw.SIZE, bz + RegionRaw.SIZE);
			}
			this.regionSenders.put(key, new RegionShare(s.x, s.z, counts, System.currentTimeMillis()));
		}
		// who shared what: one line each, at the middle of their land
		Map<String, double[]> by = new HashMap<>();
		for (RegionShare r : this.regionSenders.values()) {
			for (var e : r.columns().entrySet()) {
				double[] a = by.computeIfAbsent(e.getKey(), k -> new double[4]);
				int n = e.getValue()[0];
				a[0] += (r.rx() * RegionRaw.SIZE + RegionRaw.SIZE / 2.0) * n;
				a[1] += (r.rz() * RegionRaw.SIZE + RegionRaw.SIZE / 2.0) * n;
				a[2] += n;
				a[3] = Math.max(a[3], r.at());
			}
		}
		List<Shared> out = new ArrayList<>();
		for (var e : by.entrySet()) {
			double[] a = e.getValue();
			if (a[2] > 0) {
				out.add(new Shared(e.getKey(), this.nameOf(e.getKey()), a[0] / a[2], a[1] / a[2], (int) (a[2] / 256), (long) a[3], dimension));
			}
		}
		out.sort(java.util.Comparator.comparingInt((Shared x) -> -x.chunks()));
		this.sharedSummary = out;
	}

	/** One party member's land on this map: who, the middle of it, how many chunks, when it last came. */
	public record Shared(String player, String name, double x, double z, int chunks, long at, String dimension) {
	}

	private record RegionShare(int rx, int rz, Map<String, int[]> columns, long at) {
	}

	private final Map<Long, RegionShare> regionSenders = new java.util.concurrent.ConcurrentHashMap<>();
	private volatile List<Shared> sharedSummary = List.of();

	/** Each party member's land on the map in a dimension (largest first). */
	public List<Shared> shared(String dimension) {
		if (!this.available() || !this.atlas.settings().shareMap) {
			return List.of();
		}
		List<Shared> out = new ArrayList<>();
		for (Shared x : this.sharedSummary) {
			if (x.dimension().equals(dimension)) {
				out.add(x);
			}
		}
		return out;
	}

	/** A player's name for the map: the transport's, or the id itself. */
	public String nameOf(String player) {
		try {
			for (AtlasSocialTransport.Friend f : this.transport.roster()) {
				if (f.id().equals(player)) {
					return f.name();
				}
			}
		} catch (RuntimeException e) {
			// the transport's trouble: the id will do
		}
		return player;
	}

	/** Who mapped a shared column (for the readout), or null. */
	public String mappedBy(String dimension, int x, int z) {
		SharedTiles.Inbox in = this.inbox;
		SharedLayer s = in == null ? null : in.layer(dimension, Coords.region(x), Coords.region(z));
		return s == null ? null : s.sender(RegionRaw.index(Coords.inRegion(x), Coords.inRegion(z)));
	}
}
