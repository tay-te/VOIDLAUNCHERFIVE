package dev.voidmc.hearth.plan.model;

import dev.voidmc.hearth.plan.catalogue.Archetypes;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

/**
 * The signature settlements of ARCHITECTURE.md §7.1: what a town on a given Expanse land is known for. A signature
 * steers the layout (a stilt town on the bayou, a pueblo up the canyon wall), the landmark it raises, and its habits
 * (walkways between stacks, flat roofs stacked as terraces, dry-stone fields fanning out, quays).
 */
public enum Signature implements StringRepresentable {
	NONE("none", List.of(Archetypes.BELL_TOWER, Archetypes.CLOCK_TOWER, Archetypes.CATHEDRAL, Archetypes.TEMPLE), false, false, false, false),
	/** Jade Karst: houses on limestone sills climbing stone stacks, bamboo walkways and bridges, a pagoda. */
	KARST_CLIFF("karst_cliff", List.of(Archetypes.PAGODA, Archetypes.TEMPLE), true, true, false, false),
	/** Redwood Giants: log halls among the giants, raised walkways on trestles, a great hall. */
	FOREST_HALL("forest_hall", List.of(Archetypes.GREAT_HALL, Archetypes.BELL_TOWER), true, false, false, false),
	/** Willow Bayou, Boreal Muskeg: a stilt town on boardwalks over the water, docks and punts. */
	STILT_TOWN("stilt_town", List.of(Archetypes.TEMPLE, Archetypes.BELL_TOWER), false, false, false, true),
	/** Opal Dunes, Saguaro Flats: courtyard houses, flat roofs stacked as terraces, domes, a shaded market. */
	OASIS("oasis", List.of(Archetypes.TEMPLE, Archetypes.CLOCK_TOWER), false, true, false, false),
	/** Painted Canyons, badlands: a pueblo stacked up the canyon wall, ladders and roof terraces. */
	PUEBLO("pueblo", List.of(Archetypes.TEMPLE), false, true, false, false),
	/** Olive Groves: a dense, tall hill town of stair streets with a bell tower at the top. */
	HILL_TOWN("hill_town", List.of(Archetypes.BELL_TOWER, Archetypes.CATHEDRAL), true, false, false, false),
	/** Fjordlands, cool coasts: quays, boathouses, net lofts and a lighthouse. */
	HARBOUR("harbour", List.of(Archetypes.LIGHTHOUSE, Archetypes.BELL_TOWER), false, false, false, true),
	/** Verdant Peaks, meadows: chalets on terraces up a switchback road, a chapel spire. */
	ALPINE("alpine", List.of(Archetypes.BELL_TOWER, Archetypes.GREAT_HALL), false, false, false, false),
	/** Heather Moor: stone cottages and dry-stone walled fields fanning out. */
	MOOR("moor", List.of(Archetypes.GREAT_HALL, Archetypes.BELL_TOWER), false, false, true, false),
	/** Amber Steppe: round houses in rings round a market ring. */
	RING_TOWN("ring_town", List.of(Archetypes.TEMPLE), false, false, true, false),
	/** Wisteria Vale: timber-and-plaster lanes under blossom, canals and arched bridges, gardens; its pagoda is a garden tower, so its second landmark is no tower (requests/b-refine.md §4). */
	GARDEN_TOWN("garden_town", List.of(Archetypes.PAGODA, Archetypes.CATHEDRAL), true, false, false, true),
	/** Frostbloom Tundra: a longhouse steading round a fire hall. */
	STEADING("steading", List.of(Archetypes.GREAT_HALL), false, false, true, false),
	/** Palm and Coral Coast: a lagoon village on stilts with jetties. */
	LAGOON("lagoon", List.of(Archetypes.LIGHTHOUSE, Archetypes.TEMPLE), false, false, false, true);

	public static final StringRepresentable.EnumCodec<Signature> CODEC = StringRepresentable.fromEnum(Signature::values);

	private final String name;
	/** Landmarks in order of preference. */
	public final List<Identifier> landmarks;
	/** Joins tall buildings with walkways across streets and between stacks. */
	public final boolean walkways;
	/** Stacks buildings on terrace decks and roofs. */
	public final boolean stacks;
	/** Lays fields out in a fan of walled strips along tracks from the heart. */
	public final boolean fieldFan;
	/** Builds docks, jetties and quays along its water. */
	public final boolean docks;

	Signature(String name, List<Identifier> landmarks, boolean walkways, boolean stacks, boolean fieldFan, boolean docks) {
		this.name = name;
		this.landmarks = landmarks;
		this.walkways = walkways;
		this.stacks = stacks;
		this.fieldFan = fieldFan;
		this.docks = docks;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}

	public String translationKey() {
		return "hearth.plan.signature." + this.name;
	}

	public static Signature byName(String name) {
		for (Signature s : values()) {
			if (s.name.equals(name)) {
				return s;
			}
		}
		return NONE;
	}
}
