package dev.voidmc.fray.api;

import java.util.Locale;

/**
 * The ten damage kinds (CONTRACTS.md §5), fixed in code because each has a resistance attribute. Physical kinds go
 * through vanilla armour and toughness (pierce counts toughness double, blunt half); the others only through their
 * resistance.
 *
 * @since 0.1.0 (2026-10-21)
 */
public enum DamageKind {
	SLASH(Group.PHYSICAL, 1.0F, 0xFFE5484D),
	PIERCE(Group.PHYSICAL, 2.0F, 0xFFE8E2D0),
	BLUNT(Group.PHYSICAL, 0.5F, 0xFFD9C7A0),
	FIRE(Group.ELEMENTAL, 0, 0xFFFF8A3D),
	FROST(Group.ELEMENTAL, 0, 0xFF8EC5FF),
	SHOCK(Group.ELEMENTAL, 0, 0xFFF5D76E),
	POISON(Group.ELEMENTAL, 0, 0xFF8BD45E),
	ARCANE(Group.OCCULT, 0, 0xFFB07CFF),
	RADIANT(Group.OCCULT, 0, 0xFFFFF1B8),
	BLIGHT(Group.OCCULT, 0, 0xFF9A8F6A);

	public enum Group {
		PHYSICAL, ELEMENTAL, OCCULT
	}

	public static final DamageKind[] VALUES = values();

	public final Group group;
	/** How much armour toughness counts for this kind (0: armour doesn't apply). */
	public final float toughnessFactor;
	/** The kind's colour on the HUD (damage numbers). */
	public final int colour;

	DamageKind(Group group, float toughnessFactor, int colour) {
		this.group = group;
		this.toughnessFactor = toughnessFactor;
		this.colour = colour;
	}

	public boolean physical() {
		return this.group == Group.PHYSICAL;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	/** The status this kind builds up by default, or null (blunt dazes on crits instead; radiant smites). */
	public StatusType pairedStatus() {
		return switch (this) {
			case SLASH, PIERCE -> StatusType.BLEED;
			case FIRE -> StatusType.BURN;
			case FROST -> StatusType.CHILL;
			case SHOCK -> StatusType.SHOCK;
			case POISON -> StatusType.POISON;
			case ARCANE -> StatusType.HEX;
			case BLIGHT -> StatusType.BLIGHT;
			default -> null;
		};
	}

	public static DamageKind byId(String id) {
		for (DamageKind k : VALUES) {
			if (k.id().equals(id)) {
				return k;
			}
		}
		throw new IllegalArgumentException("unknown damage kind " + id);
	}
}
