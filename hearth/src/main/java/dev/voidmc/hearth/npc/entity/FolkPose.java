package dev.voidmc.hearth.npc.entity;

/**
 * What a folk's body is doing, synced to clients so the model can animate it. The server decides; the client only
 * draws. Sleeping uses the vanilla sleeping pose instead.
 */
public enum FolkPose {
	NONE,
	/** On a chair, bench or stair. */
	SIT,
	/** Hammer or tool strokes: smiths, builders, masons, carpenters. */
	HAMMER,
	/** Both hands busy at a bench or counter: tailors, bakers kneading, brewers. */
	CRAFT,
	/** Head down over a book. */
	READ,
	/** Head bowed, hands together. */
	PRAY,
	/** One hand raised: greeting someone. */
	WAVE,
	/** Gesturing while talking. */
	TALK,
	/** Bent to the ground: planting, harvesting, tending. */
	TEND,
	/** Crouched, arms over the head: hiding in a raid. */
	COWER,
	/** Arms up: children at play, a cheer. */
	CHEER;

	private static final FolkPose[] VALUES = values();

	public static FolkPose byId(int id) {
		return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
	}
}
