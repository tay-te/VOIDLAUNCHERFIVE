package dev.voidmc.mettle.api;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * One card as other mods see it (docs/CARDS.md §2, §25): an immutable snapshot, made when asked or when an event fires.
 * Mettle's own card record stays internal; this carries what a partner needs to show, price or react to a card.
 *
 * <p>An <b>offer</b> (a card dealt into a hand, not yet kept) has {@code id} 0, place {@link Place#HAND} and no owner
 * history: it enters the world only when kept (§2.1).
 *
 * @param id       world-unique, never reused; 0 for an offer
 * @param code     the printed id ({@code MN-7Q4K-2F}); empty for an offer
 * @param skill    the skill whose deck it belongs to (a Mythic: its drawn skill, or {@code mettle:mythic})
 * @param perk     the perk it gives (a Mythic: {@code mettle:mythic/<id>})
 * @param rank     roots: I, II, III from the rarity; 1 for everything else
 * @param wildcard an offer from another skill's deck dealt into this hand (always false on a kept card)
 * @param keywords the native keyword, then a second if rolled; none on a Mythic
 * @param traits   0-3 rolled traits, values as rolled (before the holder's attributes)
 * @param needs    the skill level needed to slot it
 * @param mythic   the Mythic's id ({@code mettle:twin_edge}) for a Mythic card
 * @param owner    who holds it now (empty for an offer)
 * @param drawnBy  the name of the player who drew it
 * @param owners   how many players have held it
 * @param scars    how many times a rebuild scarred it
 * @param deck     the deck it is slotted in, when slotted (a Mythic may sit in any deck)
 * @param slot     its slot in that deck, or -1
 * @param dormant  slotted but doing nothing now (a Mastery without its side, a slot lost with Wits)
 * @param attuning received by trade lately: it can't be slotted, traded or listed yet
 */
public record CardView(
	long id,
	String code,
	ResourceKey<Skill> skill,
	ResourceKey<Perk> perk,
	Rarity rarity,
	int rank,
	boolean wildcard,
	List<Identifier> keywords,
	List<TraitValue> traits,
	int needs,
	Optional<Identifier> mythic,
	Optional<UUID> owner,
	String drawnBy,
	int owners,
	int scars,
	Place place,
	Optional<ResourceKey<Skill>> deck,
	int slot,
	boolean dormant,
	boolean attuning
) {
	public CardView {
		keywords = List.copyOf(keywords);
		traits = List.copyOf(traits);
	}

	/** A card's rarity. Cursed is the frame of the cursed perks; Mythic is a Mythic card (no rarity of its own). */
	public enum Rarity {
		COMMON, RARE, EPIC, CURSED, MYTHIC;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** Where a card is: dealt in a hand (an offer), the binder, a deck, held by a trade, listed (Hearth's broker), or gone. */
	public enum Place {
		HAND, BINDER, SLOTTED, TRADE, LISTED, RELEASED;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** A rolled trait ({@code mettle:mining/deepborn}) and its value, in the trait's unit. */
	public record TraitValue(Identifier trait, double value) {
	}

	/** Whether this is an offer in a hand rather than a card in the world. */
	public boolean offer() {
		return this.place == Place.HAND;
	}

	public boolean slotted() {
		return this.place == Place.SLOTTED;
	}

	public boolean isMythic() {
		return this.rarity == Rarity.MYTHIC;
	}
}
