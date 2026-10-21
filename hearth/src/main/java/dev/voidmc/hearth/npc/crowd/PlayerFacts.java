package dev.voidmc.hearth.npc.crowd;

import dev.voidmc.hearth.api.StandingService.Title;

/**
 * What the crowd can tell about a player at a glance, gathered by the director from the player, the standing module (title,
 * menace) and the law (outlaw) once a pass, so {@link Stimuli} stays a pure function of it.
 *
 * @param weaponDrawn      a weapon in the main hand
 * @param drawnTicks       how long it has been in hand
 * @param armour           armour points worn (leather clothes are 7)
 * @param ticksSinceCombat ticks since the player last hurt or was hurt by something (large if never)
 * @param title            the player's title in this town
 * @param outlaw           an outlaw in the town's region (law)
 * @param menace           0 harmless … 1 terrifying (standing)
 * @param sneaking         crouched
 * @param night            the dead of night (22:00–05:00)
 * @param health           health as a fraction of the maximum
 * @param bodyNear         a body (a folk killed in the last minute) within 6 blocks
 */
public record PlayerFacts(boolean weaponDrawn, int drawnTicks, int armour, int ticksSinceCombat, Title title, boolean outlaw, float menace,
	boolean sneaking, boolean night, float health, boolean bodyNear) {

	/** A plain stranger in clean clothes, unarmed, at noon. */
	public static final PlayerFacts PLAIN = new PlayerFacts(false, 0, 0, Integer.MAX_VALUE, Title.STRANGER, false, 0, false, false, 1, false);

	public PlayerFacts withWeapon(int ticks) {
		return new PlayerFacts(true, ticks, armour, ticksSinceCombat, title, outlaw, menace, sneaking, night, health, bodyNear);
	}

	public PlayerFacts withArmour(int points) {
		return new PlayerFacts(weaponDrawn, drawnTicks, points, ticksSinceCombat, title, outlaw, menace, sneaking, night, health, bodyNear);
	}

	public PlayerFacts withCombat(int ticksAgo) {
		return new PlayerFacts(weaponDrawn, drawnTicks, armour, ticksAgo, title, outlaw, menace, sneaking, night, health, bodyNear);
	}

	public PlayerFacts withTitle(Title t) {
		return new PlayerFacts(weaponDrawn, drawnTicks, armour, ticksSinceCombat, t, outlaw, menace, sneaking, night, health, bodyNear);
	}

	public PlayerFacts withOutlaw(boolean o) {
		return new PlayerFacts(weaponDrawn, drawnTicks, armour, ticksSinceCombat, title, o, menace, sneaking, night, health, bodyNear);
	}

	public PlayerFacts withSneaking(boolean s, boolean atNight) {
		return new PlayerFacts(weaponDrawn, drawnTicks, armour, ticksSinceCombat, title, outlaw, menace, s, atNight, health, bodyNear);
	}

	public PlayerFacts withHealth(float h) {
		return new PlayerFacts(weaponDrawn, drawnTicks, armour, ticksSinceCombat, title, outlaw, menace, sneaking, night, h, bodyNear);
	}

	public PlayerFacts withBody(boolean b) {
		return new PlayerFacts(weaponDrawn, drawnTicks, armour, ticksSinceCombat, title, outlaw, menace, sneaking, night, health, b);
	}

	public PlayerFacts withMenace(float m) {
		return new PlayerFacts(weaponDrawn, drawnTicks, armour, ticksSinceCombat, title, outlaw, m, sneaking, night, health, bodyNear);
	}
}
