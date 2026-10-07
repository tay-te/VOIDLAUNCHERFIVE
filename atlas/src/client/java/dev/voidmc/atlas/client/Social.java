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
	public static final String FRIENDS_LAYER = "atlas:friends";
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
		long now = System.currentTimeMillis();
		byte[] out = this.positions.outgoing(p.getName().getString(), dimension, p.getX(), p.getY(), p.getZ(), p.getYRot(), now);
		if (out != null) {
			this.transport.publish(AtlasSocialTransport.TOPIC_POSITION, out);
		}
		if (!this.layerShown) {
			this.layerShown = true;
			this.atlas.markers.registerLayer(new MapLayer(FRIENDS_LAYER, "Friends", true, 5));
		}
		SharedTiles.Outbox box = this.outbox;
		AtlasSocialTransport t = this.transport;
		if (this.merging.compareAndSet(false, true)) {
			try {
				session.workers.execute(() -> {
					try {
						if (!PerfGovernor.paused()) {
							// Light: party tiles wait, both ways
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

	/** A walked region changed (worker): it may go out to friends, within the byte budget. */
	void walkedChanged(String dimension, RegionRaw raw, int x0, int z0, int w, int h) {
		SharedTiles.Outbox box = this.outbox;
		if (box != null && this.available()) {
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
		this.atlas.markers.registerProvider(FRIENDS_LAYER, 0, (query, sink) -> {
			for (FriendPositions.Marker f : this.friends()) {
				if (f.dimension().equals(query.dimension())) {
					sink.accept(new Marker("atlas:friend:" + f.player(), FRIENDS_LAYER, f.name(), f.faded() ? "a while ago" : "", "FRIEND", f.x(), f.y(),
						f.z(), f.dimension(), MarkerGlyph.DOT, Marker.Style.PLAYER, 0xFF9B8CFF, null));
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
		this.atlas.markers.unregisterProvider(FRIENDS_LAYER);
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
			for (int i = 0; i < SharedLayer.AREA; i++) {
				if (!s.known(i) || own != null && own.known(i)) {
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
		}
	}

	/** Who mapped a shared column (for the readout), or null. */
	public String mappedBy(String dimension, int x, int z) {
		SharedTiles.Inbox in = this.inbox;
		SharedLayer s = in == null ? null : in.layer(dimension, Coords.region(x), Coords.region(z));
		return s == null ? null : s.sender(RegionRaw.index(Coords.inRegion(x), Coords.inRegion(z)));
	}
}
