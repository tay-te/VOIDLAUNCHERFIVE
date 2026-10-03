package dev.voidmc.hearth.npc.routine;

/** What a resident is doing in a stretch of their day. The last three are overrides, never scheduled. */
public enum Activity {
	SLEEP,
	/** Getting up: dressing, a moment at home. */
	WAKE,
	/** A meal: breakfast and supper at home, lunch at home, the inn or the work site. */
	EAT,
	WORK,
	/** Free time: see {@link Leisure} for where. */
	LEISURE,
	/** Evening at home, by the fire. */
	HOME,
	/** Indoors out of the rain or the dark. */
	SHELTER,
	/** Running from something dangerous. */
	FLEE,
	/** The town is under attack: ring the bell, then hide at home. */
	ALARM;

	public boolean outdoors() {
		return this == WORK || this == LEISURE;
	}
}
