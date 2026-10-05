package dev.voidmc.atlas.client;

import dev.voidmc.atlas.core.rules.Restrictions;
import dev.voidmc.atlas.core.rules.ServerRules;
import java.util.ArrayList;
import java.util.List;

/**
 * What the server Atlas is connected to allows: the minimap, the underground view, the radar (see
 * docs/SERVER_RULES.md). Reset on every connection; fed the server's MOTD and its system messages (never players'
 * chat), where the map mods' codes are sent. The defaults are the fair ones: on a server the radar is off until the
 * server opts in, the minimap and the underground view are on until it says no.
 */
public final class ServerRulesState {
	private final ServerRules rules = new ServerRules(true);
	private volatile Restrictions.Allowed allowed = Restrictions.NONE.allowed(true);
	private volatile Restrictions said = Restrictions.NONE;
	private volatile int version;

	public boolean minimapAllowed() {
		return this.allowed.minimap();
	}

	public boolean caveViewAllowed(boolean ceiling) {
		return this.allowed.caveViewIn(ceiling, false);
	}

	public boolean caveMapAllowed(boolean ceiling) {
		return this.allowed.caveViewIn(ceiling, true);
	}

	public boolean radarAllowed(boolean mobs) {
		return mobs ? this.allowed.radarMobs() : this.allowed.radarPlayers();
	}

	/** Bumped on every change, so a notice can show when the rules change. */
	public int version() {
		return this.version;
	}

	/** A new connection (or none): back to the defaults. */
	public void reset() {
		this.rules.clear();
		this.update();
	}

	/** A line from the server (its MOTD, a system message): any map codes in it apply. */
	public void accept(String text) {
		if (text == null || text.indexOf('§') < 0) {
			return;
		}
		if (!this.rules.accept(text).isEmpty()) {
			this.update();
		}
	}

	private void update() {
		Restrictions.Allowed next = this.rules.allowed();
		Restrictions said = this.rules.restrictions();
		if (!next.equals(this.allowed) || !said.equals(this.said)) {
			this.allowed = next;
			this.said = said;
			this.version++;
		}
	}

	/**
	 * What the server has turned off, in words ("cave view and radar"), or null if nothing: the radar being off by
	 * default is Atlas's own fairness, not the server's doing, so it is named only when the server said so.
	 */
	public String turnedOff() {
		Restrictions r = this.said;
		List<String> off = new ArrayList<>();
		if (r.minimap() == Restrictions.Say.DENY) {
			off.add("the minimap");
		}
		if (r.caveMap() == Restrictions.Say.DENY) {
			off.add("cave view");
		}
		if (r.radarPlayers() == Restrictions.Say.DENY || r.radarMobs() == Restrictions.Say.DENY) {
			off.add("radar");
		}
		if (off.isEmpty()) {
			return null;
		}
		return off.size() == 1 ? off.get(0) : String.join(", ", off.subList(0, off.size() - 1)) + " and " + off.get(off.size() - 1);
	}
}
