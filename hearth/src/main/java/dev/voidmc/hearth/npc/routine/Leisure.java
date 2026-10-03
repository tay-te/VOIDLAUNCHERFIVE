package dev.voidmc.hearth.npc.routine;

/** Where a resident spends free time. Chosen per day from their traits, age and a seed. */
public enum Leisure {
	/** The square, the well or the bell: where people meet. */
	PLAZA,
	INN,
	/** A balcony, tower top or bench with a view. */
	VIEWPOINT,
	/** Calling at a friend's or relative's door. */
	VISIT,
	/** Children's games round the meeting point. */
	PLAY,
	/** A while at the altar. */
	PRAY,
	/** Sitting on a bench in the sun (elders' favourite). */
	BENCH,
	/** A stroll round the edges of town. */
	WANDER,
	/** Reading at a lectern or by the window at home. */
	READ,
	/** Pottering about the home: the garden, the doorstep. */
	DOORSTEP
}
