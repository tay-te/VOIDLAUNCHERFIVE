package dev.voidmc.hearth.npc.crowd;

import java.util.Locale;

/**
 * Something about a player (or near one) that the folk round them notice (docs/LIVING_WORLD_2.md §5.1), most salient
 * first. The bark category for a stimulus is {@code crowd_<name>} ({@link #category}).
 */
public enum Stimulus {
	/** An outlaw in town: folk run indoors and shut their doors. */
	OUTLAW(100),
	/** A body on the ground near the player: screams, a crowd, a guard. */
	BODY(90),
	/** The player is fighting (hurt or hurting in the last five seconds): a ring gathers. */
	FIGHT(80),
	/** A weapon drawn in town: folk step back, a guard comes over if it stays drawn. */
	BLADE(70),
	/** A Hero of the town: folk turn, wave, cheer; children follow. */
	HERO(60),
	/** Wounded or bloodied. */
	BLOODIED(45),
	/** Heavy armour: guards remark, merchants eye it. */
	ARMOUR(40),
	/** Sneaking, or abroad in the dead of night. */
	PROWL(35),
	/** Distrusted: muttering, folk cross the lane. */
	DISTRUSTED(30),
	/** A Friend, Known or Burgher: a word of small talk now and then. */
	FRIEND(10);

	public static final Stimulus[] VALUES = values();

	private final int salience;

	Stimulus(int salience) {
		this.salience = salience;
	}

	/** The base salience (0–100): the higher, the more it draws the crowd. */
	public int salience() {
		return this.salience;
	}

	/** The serialised name ({@code blade}). */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The bark category ({@code crowd_blade}). */
	public String category() {
		return "crowd_" + id();
	}
}
