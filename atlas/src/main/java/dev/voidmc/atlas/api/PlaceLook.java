package dev.voidmc.atlas.api;

/**
 * How another mod's place looks once discovered, for a place it reports ({@link AtlasPlaces#report}) and lets Atlas
 * judge sighted and discovered: its glyph, its marker style (a settlement's tile and chip, say), the meta after its
 * name ("84 folk"), the caps word for its kind ("HEARTH  ·  VILLAGE"), and the detail card. Sighted, it is Atlas's
 * hollow "?" like any place; discovered, it is drawn this way, and its discovery toast and journal line are Atlas's
 * own. Set with {@link AtlasPlaces#look}.
 *
 * @param glyph    the tile's glyph ({@link MarkerGlyph#TOWN}, {@link MarkerGlyph#VILLAGE} ...)
 * @param style    {@link Marker.Style#SETTLEMENT} or {@link Marker.Style#STRUCTURE}
 * @param meta     after the name on its chip, or "" (always labelled when the style is a settlement)
 * @param kindWord the caps line's first word(s), or null for Atlas's own
 * @param detail   the detail card, or null
 */
public record PlaceLook(MarkerGlyph glyph, Marker.Style style, String meta, String kindWord, MarkerDetail detail) {
	public PlaceLook {
		glyph = glyph == null ? MarkerGlyph.DOT : glyph;
		style = style == null ? Marker.Style.STRUCTURE : style;
		meta = meta == null ? "" : meta;
	}
}
