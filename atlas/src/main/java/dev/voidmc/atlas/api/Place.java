package dev.voidmc.atlas.api;

import java.util.Objects;

/**
 * Somewhere worth knowing about: a structure (a village, a ruined portal, an ancient city), a natural landmark (a peak,
 * a lake, a river, a waterfall, a cave mouth, a ravine), or a named region of the land ("Larkspur Meadow"). Atlas finds
 * them itself — from the blocks the client is sent ({@link Source.Kind#SEEN}), from the game's own structure data in
 * singleplayer ({@link Source.Kind#EXACT}) — and other mods and VOID servers add theirs through {@link AtlasPlaces}.
 *
 * @param id          stable and unique: the same place keeps its id across sessions
 * @param kind        what it is: a structure id or family ({@code minecraft:village}, {@code minecraft:ancient_city},
 *                    {@code expanse:karst_pagoda}) or one of Atlas's natural kinds ({@link #PEAK}, {@link #LAKE}, ...)
 * @param name        its name ("Plains village", "Greytooth Range", "Lake Merrow")
 * @param category    LANDMARK, BUILDING, VILLAGE, DUNGEON, SIGHT, NATURAL, WATER or REGION
 * @param tier        major (every zoom) or minor (zoomed in only)
 * @param state       how much the player knows of it; providers may pass {@link DiscoveryState#UNKNOWN} to let Atlas
 *                    decide by distance (sighted in view, discovered within reach)
 * @param source      where it comes from (the source chip)
 * @param x           its middle (for a region or a lake: where its label goes)
 * @param y           its height (the summit of a peak, the middle of a structure)
 * @param z           its middle
 * @param dimension   like {@code minecraft:overworld}
 * @param underground whether it lies below the surface (shown on the underground view)
 * @param radius      roughly how far it reaches from its middle, in blocks (0 for a point)
 * @param sighted     when the player first saw it, epoch milliseconds (0 if not yet)
 * @param discovered  when the player first came within reach, epoch milliseconds (0 if not yet)
 */
public record Place(String id, String kind, String name, String category, Tier tier, DiscoveryState state, Source source, int x, int y, int z,
	String dimension, boolean underground, int radius, long sighted, long discovered) {
	public static final String PEAK = "atlas:peak";
	public static final String LAKE = "atlas:lake";
	public static final String RIVER = "atlas:river";
	public static final String WATERFALL = "atlas:waterfall";
	public static final String CAVE_MOUTH = "atlas:cave_mouth";
	public static final String RAVINE = "atlas:ravine";
	/** A named region of one biome ("Larkspur Meadow"). */
	public static final String REGION = "atlas:region";

	public Place {
		Objects.requireNonNull(id, "id");
		kind = kind == null ? "atlas:place" : kind;
		name = name == null ? "" : name;
		category = category == null ? "SIGHT" : category;
		tier = tier == null ? Tier.MINOR : tier;
		state = state == null ? DiscoveryState.UNKNOWN : state;
		source = source == null ? Source.seen() : source;
		dimension = dimension == null ? "minecraft:overworld" : dimension;
	}

	public boolean isDiscovered() {
		return this.state == DiscoveryState.DISCOVERED;
	}

	/** Whether it is one of Atlas's natural kinds (a peak, a lake, a region...). */
	public boolean isNatural() {
		return this.kind.equals(PEAK) || this.kind.equals(LAKE) || this.kind.equals(RIVER) || this.kind.equals(WATERFALL) || this.kind.equals(CAVE_MOUTH)
			|| this.kind.equals(RAVINE) || this.kind.equals(REGION);
	}

	/** Whether it is a named region or body of water, drawn as a label on the land rather than as a marker. */
	public boolean isLabel() {
		return this.kind.equals(REGION) || this.kind.equals(LAKE) || this.kind.equals(RIVER);
	}

	/** This place moved on to {@code state} (never back), with the times filled in. */
	public Place withState(DiscoveryState state, long now) {
		DiscoveryState s = DiscoveryState.max(this.state, state);
		long sightedAt = this.sighted == 0 && s.atLeast(DiscoveryState.SIGHTED) ? now : this.sighted;
		long discoveredAt = this.discovered == 0 && s == DiscoveryState.DISCOVERED ? now : this.discovered;
		return new Place(this.id, this.kind, this.name, this.category, this.tier, s, this.source, this.x, this.y, this.z, this.dimension,
			this.underground, this.radius, sightedAt, discoveredAt);
	}

	public Place withName(String name) {
		return new Place(this.id, this.kind, name, this.category, this.tier, this.state, this.source, this.x, this.y, this.z, this.dimension,
			this.underground, this.radius, this.sighted, this.discovered);
	}

	public Place withSource(Source source) {
		return new Place(this.id, this.kind, this.name, this.category, this.tier, this.state, source, this.x, this.y, this.z, this.dimension,
			this.underground, this.radius, this.sighted, this.discovered);
	}

	public double distanceTo(double px, double pz) {
		return Math.hypot(this.x + 0.5 - px, this.z + 0.5 - pz);
	}
}
