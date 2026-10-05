package dev.voidmc.atlas.core.rules;

/**
 * What a server has said about the map, feature by feature. Each feature is {@link Say#UNSET} (the server said
 * nothing), {@link Say#ALLOW} or {@link Say#DENY}. Only the features some real server convention covers are here:
 * see {@code docs/SERVER_RULES.md} for which code or packet sets which.
 *
 * <p>Merging is "most restrictive wins": DENY beats ALLOW beats UNSET, whatever the order. Only a reset code
 * (Xaero's) takes a DENY back, and it does that by starting over from {@link #NONE}.
 *
 * <p>{@link #allowed} turns this into what Atlas does, with Atlas's own defaults for what the server left unsaid:
 * in multiplayer the radar is off unless the server opted in, and everything else is on unless the server said no.
 *
 * @param minimap            the minimap overlay
 * @param worldMap           the full-screen map
 * @param caveMap            the underground view (both the minimap and the full-screen map)
 * @param netherCaveMinimap  the underground view on the minimap in the Nether, even when {@code caveMap} is denied
 * @param netherCaveWorldMap the same for the full-screen map
 * @param radarPlayers       other players on the map
 * @param radarMobs          mobs, animals and other creatures on the map
 * @param waypoints          waypoints
 */
public record Restrictions(Say minimap, Say worldMap, Say caveMap, Say netherCaveMinimap, Say netherCaveWorldMap, Say radarPlayers, Say radarMobs,
	Say waypoints) {
	public static final Restrictions NONE = new Restrictions(Say.UNSET, Say.UNSET, Say.UNSET, Say.UNSET, Say.UNSET, Say.UNSET, Say.UNSET, Say.UNSET);

	/** What the server said about one feature. */
	public enum Say {
		UNSET,
		ALLOW,
		DENY;

		/** The more restrictive of the two. */
		public Say and(Say other) {
			return this.ordinal() >= other.ordinal() ? this : other;
		}
	}

	public Restrictions {
		java.util.Objects.requireNonNull(minimap);
		java.util.Objects.requireNonNull(worldMap);
		java.util.Objects.requireNonNull(caveMap);
		java.util.Objects.requireNonNull(netherCaveMinimap);
		java.util.Objects.requireNonNull(netherCaveWorldMap);
		java.util.Objects.requireNonNull(radarPlayers);
		java.util.Objects.requireNonNull(radarMobs);
		java.util.Objects.requireNonNull(waypoints);
	}

	/** Feature by feature, the more restrictive of this and {@code other}. */
	public Restrictions merge(Restrictions other) {
		return new Restrictions(
			this.minimap.and(other.minimap),
			this.worldMap.and(other.worldMap),
			this.caveMap.and(other.caveMap),
			this.netherCaveMinimap.and(other.netherCaveMinimap),
			this.netherCaveWorldMap.and(other.netherCaveWorldMap),
			this.radarPlayers.and(other.radarPlayers),
			this.radarMobs.and(other.radarMobs),
			this.waypoints.and(other.waypoints));
	}

	public Restrictions withMinimap(Say say) {
		return new Restrictions(say, this.worldMap, this.caveMap, this.netherCaveMinimap, this.netherCaveWorldMap, this.radarPlayers, this.radarMobs, this.waypoints);
	}

	public Restrictions withWorldMap(Say say) {
		return new Restrictions(this.minimap, say, this.caveMap, this.netherCaveMinimap, this.netherCaveWorldMap, this.radarPlayers, this.radarMobs, this.waypoints);
	}

	public Restrictions withCaveMap(Say say) {
		return new Restrictions(this.minimap, this.worldMap, say, this.netherCaveMinimap, this.netherCaveWorldMap, this.radarPlayers, this.radarMobs, this.waypoints);
	}

	public Restrictions withNetherCaveMinimap(Say say) {
		return new Restrictions(this.minimap, this.worldMap, this.caveMap, say, this.netherCaveWorldMap, this.radarPlayers, this.radarMobs, this.waypoints);
	}

	public Restrictions withNetherCaveWorldMap(Say say) {
		return new Restrictions(this.minimap, this.worldMap, this.caveMap, this.netherCaveMinimap, say, this.radarPlayers, this.radarMobs, this.waypoints);
	}

	public Restrictions withRadarPlayers(Say say) {
		return new Restrictions(this.minimap, this.worldMap, this.caveMap, this.netherCaveMinimap, this.netherCaveWorldMap, say, this.radarMobs, this.waypoints);
	}

	public Restrictions withRadarMobs(Say say) {
		return new Restrictions(this.minimap, this.worldMap, this.caveMap, this.netherCaveMinimap, this.netherCaveWorldMap, this.radarPlayers, say, this.waypoints);
	}

	public Restrictions withWaypoints(Say say) {
		return new Restrictions(this.minimap, this.worldMap, this.caveMap, this.netherCaveMinimap, this.netherCaveWorldMap, this.radarPlayers, this.radarMobs, say);
	}

	/**
	 * What Atlas lets the player use. In single player (the integrated server) everything. In multiplayer: the radar
	 * only where the server said {@link Say#ALLOW} (an opt-in code); everything else unless the server said
	 * {@link Say#DENY}. The Nether's underground view follows the cave view, except that a Nether allowance turns it
	 * back on there when the cave view is denied.
	 */
	public Allowed allowed(boolean multiplayer) {
		if (!multiplayer) {
			return new Allowed(true, true, true, true, true, true, true, true);
		}
		boolean cave = this.caveMap != Say.DENY;
		return new Allowed(
			this.minimap != Say.DENY,
			this.worldMap != Say.DENY,
			cave,
			cave || this.netherCaveMinimap == Say.ALLOW,
			cave || this.netherCaveWorldMap == Say.ALLOW,
			this.radarPlayers == Say.ALLOW,
			this.radarMobs == Say.ALLOW,
			this.waypoints != Say.DENY);
	}

	/** What Atlas lets the player use, after the server's restrictions and Atlas's defaults. */
	public record Allowed(boolean minimap, boolean worldMap, boolean caveView, boolean netherCaveMinimap, boolean netherCaveWorldMap,
		boolean radarPlayers, boolean radarMobs, boolean waypoints) {
		/** Whether the underground view may show, in the Nether or not, on the full-screen map or the minimap. */
		public boolean caveViewIn(boolean nether, boolean onWorldMap) {
			if (!nether) {
				return this.caveView;
			}
			return onWorldMap ? this.netherCaveWorldMap : this.netherCaveMinimap;
		}
	}
}
