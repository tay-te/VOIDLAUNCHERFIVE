package dev.voidmc.hearth.build.kit.civic;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Habits.WallKind;

/**
 * The building tradition a civic building is designed in: which way its arcades are made, how its towers are capped,
 * what its windows look like. The fourteen cultures fall into nine traditions (the cultures within one share how they
 * raise a hall or a tower, and differ in their materials, which still come from the style); a style without a culture (a
 * data pack's own) is placed by its habits.
 *
 * <p>Archetypes read materials from the style's roles and habits as usual; the tradition only decides the parts no role
 * describes: an arcade of braced oak posts or of round stone arches, a spire or an onion dome or a curved tiled roof.
 */
public enum Tradition {
	/** Temperate and wisteria: timber frame and plaster, steep gables, braced oak posts, spires and lanterns. */
	TIMBER,
	/** Northern and tundra: log and stave construction, stacked steep shingle roofs, carved ridges. */
	LOG,
	/** Alpine: whitewashed stone storey under timber, deep low roofs, onion-domed towers. */
	ALPINE,
	/** Moorland and maritime: rubble and dressed stone under slate, coped gables, squat towers with pyramids. */
	STONE,
	/** Mediterranean: whitewash and terracotta, round-arched loggias, campaniles and bell walls. */
	MEDITERRANEAN,
	/** Desert and mesa: battered adobe and sandstone, flat roofs and parapets, domes, pointed arches, bell walls. */
	ADOBE,
	/** Steppe: ochre plaster and timber, round plans, cone roofs. */
	STEPPE,
	/** Karst: limestone platforms, red columns with brackets, curved tiled roofs with lifted corners, lattices. */
	KARST,
	/** Bayou and tropical: raised on posts, deep hipped roofs, galleries with railings. */
	STILT;

	/** The tradition of the draft's style: by its culture, else guessed from its habits. */
	public static Tradition of(Draft d) {
		Culture c = d.style().culture();
		if (c != null) {
			return of(c);
		}
		return guess(d.habits());
	}

	public static Tradition of(Culture c) {
		return switch (c) {
			case TEMPERATE, WISTERIA -> TIMBER;
			case NORTHERN, TUNDRA -> LOG;
			case ALPINE -> ALPINE;
			case MOORLAND, MARITIME -> STONE;
			case MEDITERRANEAN -> MEDITERRANEAN;
			case DESERT, MESA -> ADOBE;
			case STEPPE -> STEPPE;
			case KARST -> KARST;
			case BAYOU, TROPICAL -> STILT;
		};
	}

	/** A tradition for a style without a culture, from what it builds. */
	static Tradition guess(Habits h) {
		if (h.plan().stilts() == Habits.Stilts.ALWAYS) {
			return STILT;
		}
		if (h.roof().weight(RoofForm.PAGODA) > h.roof().weight(RoofForm.GABLE)) {
			return KARST;
		}
		if (h.roof().weight(RoofForm.FLAT) > h.roof().weight(RoofForm.GABLE) + h.roof().weight(RoofForm.HIP)) {
			return ADOBE;
		}
		if (h.plan().round() >= 0.5 || h.roof().weight(RoofForm.CONE) > h.roof().weight(RoofForm.GABLE)) {
			return STEPPE;
		}
		WallKind k = h.walls().kind();
		return switch (k) {
			case LOG -> LOG;
			case STONE, BRICK -> STONE;
			case ADOBE -> ADOBE;
			case BAMBOO -> KARST;
			case PLASTER -> h.roof().pitch() < 0.8 ? MEDITERRANEAN : ALPINE;
			default -> TIMBER;
		};
	}

	/** Builds in masonry at heart (arches of stone rather than posts of timber). */
	public boolean masonry() {
		return this == STONE || this == MEDITERRANEAN || this == ADOBE;
	}

	/** Roofs are flat by habit (parapets and domes rather than pitched roofs). */
	public boolean flatRoofed() {
		return this == ADOBE;
	}

	/** Builds raised on posts. */
	public boolean raised() {
		return this == STILT;
	}
}
