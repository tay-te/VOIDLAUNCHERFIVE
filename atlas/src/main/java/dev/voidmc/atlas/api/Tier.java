package dev.voidmc.atlas.api;

/**
 * How much a place matters on the map: a major landmark (a village, a monument, a mountain range's name) shows at every
 * zoom; a minor place (a ruined portal, a cave mouth, a waterfall) only when zoomed in, as something to explore later.
 */
public enum Tier {
	MAJOR, MINOR
}
