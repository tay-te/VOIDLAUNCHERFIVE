package dev.voidmc.atlas.api;

import java.util.Objects;

/**
 * Something drawn on the map: a waypoint, a discovered structure, a player, or anything another mod provides (a Hearth
 * settlement). Plain data; providers make new ones each time they are asked.
 *
 * @param id        unique among the provider's markers; selection and the detail card follow it
 * @param layer     the layer it belongs to ({@link MapLayer#id()}): hidden with it
 * @param name      the label
 * @param meta      a short second label, drawn dimmer beside the name (a population, "Y –42"); may be empty
 * @param subtitle  the caps line in lists and cards ("HEARTH · VILLAGE"); may be empty
 * @param dimension the dimension's id, like {@code minecraft:overworld}
 * @param glyph     its 5 x 5 glyph
 * @param style     how it is drawn on the map
 * @param colour    0xRRGGBB for the marker's accent (a waypoint pin, a player ring), or 0 for the default
 * @param detail    the detail card's contents, or null for the plain card
 */
public record Marker(
	String id,
	String layer,
	String name,
	String meta,
	String subtitle,
	double x,
	double y,
	double z,
	String dimension,
	MarkerGlyph glyph,
	Style style,
	int colour,
	MarkerDetail detail
) {
	public enum Style {
		/** A 26 px tile with the glyph and a label chip (settlements). */
		SETTLEMENT,
		/** A 20 px dark tile and a small dim label (structures, landmarks). */
		STRUCTURE,
		/** A pin whose tip is the spot (waypoints). */
		WAYPOINT,
		/** A ring with an initial (other players). */
		PLAYER
	}

	public Marker {
		Objects.requireNonNull(id, "id");
		layer = layer == null ? MapLayer.STRUCTURES : layer;
		name = name == null ? "" : name;
		meta = meta == null ? "" : meta;
		subtitle = subtitle == null ? "" : subtitle;
		dimension = dimension == null ? "minecraft:overworld" : dimension;
		glyph = glyph == null ? MarkerGlyph.DOT : glyph;
		style = style == null ? Style.STRUCTURE : style;
	}

	/** A marker with just the essentials: a structure-style marker on a layer. */
	public static Marker of(String id, String layer, String name, double x, double y, double z, String dimension, MarkerGlyph glyph) {
		return new Marker(id, layer, name, "", "", x, y, z, dimension, glyph, Style.STRUCTURE, 0, null);
	}

	public double distanceTo(double px, double pz) {
		return Math.hypot(this.x - px, this.z - pz);
	}
}
