package dev.voidmc.atlas.api;

import java.util.List;

/**
 * What a marker's detail card shows below its name (the design's settlement card): a row of large readouts, an
 * optional meter (a standing, a reputation), and sections of rows with tags. Everything is optional; distance and
 * direction are added by Atlas.
 *
 * @param readouts big numbers with a caps label ("84 FOLK", "9 HOMES"), at most three are shown
 * @param meter    a cell meter, or null
 * @param sections labelled lists of rows ("WHAT THE TOWN NEEDS")
 */
public record MarkerDetail(List<Readout> readouts, Meter meter, List<Section> sections) {
	public MarkerDetail {
		readouts = readouts == null ? List.of() : List.copyOf(readouts);
		sections = sections == null ? List.of() : List.copyOf(sections);
	}

	public record Readout(String value, String label) {
	}

	/**
	 * A row of cells from {@code min} to {@code max} with {@code value} marked (violet: it is live), a value text on the
	 * right of the label and a caption under it.
	 */
	public record Meter(String label, String valueText, double min, double max, double value, String caption) {
	}

	public record Section(String label, List<Row> rows) {
		public Section {
			rows = rows == null ? List.of() : List.copyOf(rows);
		}
	}

	/** A row: text, an optional small tag on the right ("ACTIVE"), and whether it is the active one (brighter). */
	public record Row(String text, String tag, boolean active) {
	}
}
