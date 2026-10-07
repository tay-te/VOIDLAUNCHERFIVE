package dev.voidmc.atlas.api;

/**
 * How another mod's place looks once discovered, for a place it reports ({@link AtlasPlaces#report}) and lets Atlas
 * judge sighted and discovered: its glyph, its marker style (a settlement's tile and chip, say), the meta after its
 * name ("84 folk"), the caps word for its kind ("HEARTH  ·  VILLAGE"), and the detail card. Sighted, it is Atlas's
 * hollow "?" like any place; discovered, it is drawn this way, and its discovery toast and journal line are Atlas's
 * own. Set with {@link AtlasPlaces#look}; Atlas keeps it with the place (in the world's place book), so a remembered
 * place keeps its look in a later session before its mod has said it again.
 *
 * @param glyph    the tile's glyph ({@link MarkerGlyph#TOWN}, {@link MarkerGlyph#VILLAGE} ...)
 * @param style    {@link Marker.Style#SETTLEMENT} or {@link Marker.Style#STRUCTURE}
 * @param meta     after the name on its chip, or "" (always labelled when the style is a settlement)
 * @param kindWord the caps line's first word(s), or null for Atlas's own
 * @param detail   the detail card, or null
 * @param layer    the map layer it is drawn on (one the mod registered, a "Settlements" chip say; sighted and
 *                 discovered alike), or null for Atlas's own (Landmarks for a major place, else Places)
 */
public record PlaceLook(MarkerGlyph glyph, Marker.Style style, String meta, String kindWord, MarkerDetail detail, String layer) {
	public PlaceLook {
		glyph = glyph == null ? MarkerGlyph.DOT : glyph;
		style = style == null ? Marker.Style.STRUCTURE : style;
		meta = meta == null ? "" : meta;
		layer = layer == null || layer.isBlank() ? null : layer;
	}

	/** A look on Atlas's own layers. */
	public PlaceLook(MarkerGlyph glyph, Marker.Style style, String meta, String kindWord, MarkerDetail detail) {
		this(glyph, style, meta, kindWord, detail, null);
	}

	/** This look on a layer of its mod's ({@code null}: Atlas's own). */
	public PlaceLook onLayer(String layer) {
		return new PlaceLook(this.glyph, this.style, this.meta, this.kindWord, this.detail, layer);
	}
}
