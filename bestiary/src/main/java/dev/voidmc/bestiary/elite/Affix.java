package dev.voidmc.bestiary.elite;

import dev.voidmc.bestiary.Bestiary;
import java.util.Locale;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Stage 1's six elite affixes (BESTIARY.md §7). Each has a tell the player can read and a counter; the ids go to
 * Fray's {@code ProfileService} so the nameplate and target frame show their chips.
 */
public enum Affix {
	/** +6 armour, poise +60 %; can't be Staggered until its plate takes 3 blunt hits. Tell: a dull metal sheen. */
	IRONBOUND(0xFF9AA3AD, false),
	/** +25 % attack rate; enrages at half health (+25 % more). Tell: snarls; eyes redden at enrage. */
	FRENZIED(0xFFE5484D, false),
	/** Poison build-up on its hits; a poison cloud when hit hard. Tell: drips. */
	VENOMOUS(0xFF7BC74D, false),
	/** Regains 2 % health a second after 3 s unhurt; Burn or Blight stop it. */
	REGENERATING(0xFFE8C46A, false),
	/** Carries a shield: blocks from the front, parries lights. Humanoids only. */
	SHIELDED(0xFF6C8EBF, true),
	/** +30 % speed; sidesteps heavies. */
	SWIFT(0xFF8ED1E0, false);

	public static final Affix[] VALUES = values();

	public final String id;
	public final int colour;
	public final boolean humanoidOnly;

	Affix(int colour, boolean humanoidOnly) {
		this.id = this.name().toLowerCase(Locale.ROOT);
		this.colour = colour;
		this.humanoidOnly = humanoidOnly;
	}

	public Identifier key() {
		return Bestiary.id(this.id);
	}

	public String title() {
		return this.name().charAt(0) + this.id.substring(1);
	}

	public static @Nullable Affix byId(String id) {
		for (Affix a : VALUES) {
			if (a.id.equals(id)) {
				return a;
			}
		}
		return null;
	}
}
