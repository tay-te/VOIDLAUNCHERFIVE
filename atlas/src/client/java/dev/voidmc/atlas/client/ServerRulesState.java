package dev.voidmc.atlas.client;

/**
 * What the server Atlas is connected to allows: the minimap, the underground view, the radar. Reset on every new
 * connection; filled in from what the server says (see docs/SERVER_RULES.md). The defaults are the fair ones: on a
 * server the radar is off until the server opts in, the minimap and the underground view are on until it says no.
 */
public final class ServerRulesState {
	private volatile boolean minimap = true;
	private volatile boolean caves = true;
	private volatile boolean netherCaves = true;
	private volatile boolean radarPlayers;
	private volatile boolean radarMobs;
	private volatile int version;

	public boolean minimapAllowed() {
		return this.minimap;
	}

	public boolean caveViewAllowed(boolean ceiling) {
		return ceiling ? this.netherCaves : this.caves;
	}

	public boolean radarAllowed(boolean mobs) {
		return mobs ? this.radarMobs : this.radarPlayers;
	}

	/** Bumped on every change, so a notice can show when the rules change. */
	public int version() {
		return this.version;
	}

	/** A new connection: back to the defaults. */
	public void reset() {
		this.set(true, true, true, false, false);
	}

	void set(boolean minimap, boolean caves, boolean netherCaves, boolean radarPlayers, boolean radarMobs) {
		if (minimap != this.minimap || caves != this.caves || netherCaves != this.netherCaves || radarPlayers != this.radarPlayers
			|| radarMobs != this.radarMobs) {
			this.minimap = minimap;
			this.caves = caves;
			this.netherCaves = netherCaves;
			this.radarPlayers = radarPlayers;
			this.radarMobs = radarMobs;
			this.version++;
		}
	}
}
