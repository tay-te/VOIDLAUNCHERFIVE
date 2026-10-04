package dev.voidmc.hearth.standing.appearance;

import dev.voidmc.hearth.standing.logic.Curves;
import java.util.ArrayList;
import java.util.List;

/**
 * How a player looks right now, reduced to what folk notice (docs/LIVING_WORLD.md §3, KCD's charisma sized for
 * Minecraft): how clean they are, whether they have just bathed, blood from a fight, a weapon in hand, how much armour
 * they wear, whether their clothes are the local dress, and their name for violence. {@link #charisma} and
 * {@link #menace} are pure functions of it, so speech checks and prices read the same numbers the screens show.
 *
 * @param cleanliness  1 just washed … 0 filthy
 * @param bathed       had a proper bath recently (a bathhouse)
 * @param bloodied     blood from a fight still shows
 * @param weaponDrawn  a weapon in hand
 * @param armour       armour points worn (vanilla's 0–20+; leather clothes are 7)
 * @param localPieces  pieces of dyed clothing in the local culture's colours
 * @param foreignPieces pieces of dyed clothing in colours foreign here
 * @param plainPieces  pieces of plain, undyed leather (a peasant's working clothes)
 * @param violence     the player's reputation for violence, 0…100
 */
public record Look(double cleanliness, boolean bathed, boolean bloodied, boolean weaponDrawn, int armour, int localPieces, int foreignPieces,
	int plainPieces, double violence) {

	/** Leather clothes give this much armour: anything above it is real armour. */
	public static final int CLOTHES_ARMOUR = 7;

	public static final Look NEUTRAL = new Look(0.5, false, false, false, 0, 0, 0, 0, 0);

	/** −1 repellent … 1 charming. */
	public double charisma() {
		double c = 0;
		double clean = this.cleanliness - 0.5;
		c += clean >= 0 ? clean * 0.5 : clean * 0.7;
		if (this.bathed) {
			c += 0.10;
		}
		c += Math.min(4, this.localPieces) * 0.07;
		c += Math.min(4, this.plainPieces) * 0.02;
		c -= Math.min(4, this.foreignPieces) * 0.03;
		if (this.armour > CLOTHES_ARMOUR) {
			c -= Math.min(1, (this.armour - CLOTHES_ARMOUR) / 13.0) * 0.25;
		}
		if (this.weaponDrawn) {
			c -= 0.20;
		}
		if (this.bloodied) {
			c -= 0.35;
		}
		return Curves.clamp(c, -1, 1);
	}

	/** 0 harmless … 1 terrifying. */
	public double menace() {
		double m = Math.min(1, this.armour / 20.0) * 0.40;
		if (this.weaponDrawn) {
			m += 0.30;
		}
		if (this.bloodied) {
			m += 0.15;
		}
		m += Math.min(1, this.violence / 100) * 0.25;
		return Curves.clamp(m, 0, 1);
	}

	/**
	 * What folk notice, as translation keys under {@code hearth.standing.look.}: the parts that move charisma or menace
	 * the most, for the reputation page.
	 */
	public List<String> notes() {
		List<String> out = new ArrayList<>();
		if (this.bathed) {
			out.add("bathed");
		} else if (this.cleanliness >= 0.75) {
			out.add("clean");
		} else if (this.cleanliness < 0.15) {
			out.add("filthy");
		} else if (this.cleanliness < 0.4) {
			out.add("grubby");
		}
		if (this.bloodied) {
			out.add("bloodied");
		}
		if (this.weaponDrawn) {
			out.add("armed");
		}
		if (this.armour > 14) {
			out.add("heavy_armour");
		} else if (this.armour > CLOTHES_ARMOUR) {
			out.add("armour");
		}
		if (this.localPieces >= 2) {
			out.add("local_dress");
		} else if (this.foreignPieces >= 2) {
			out.add("foreign_dress");
		} else if (this.plainPieces >= 2) {
			out.add("plain_dress");
		}
		if (this.violence >= 40) {
			out.add("violent");
		}
		return out;
	}
}
