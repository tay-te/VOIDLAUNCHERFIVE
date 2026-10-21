package dev.voidmc.fray.combat.action;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The base movesets for vanilla weapon classes (COMBAT.md §4 table) and the shared special moves (§4.1, §6, Arts).
 * These are Fray's neutral defaults; Bestiary's moveset data and Arsenal's families replace them per family later
 * (a {@code fray:moveset} data registry). Numbers before Mettle's scaling.
 */
public final class Movesets {
	private static final Map<WeaponFamily, Moveset> SETS = new EnumMap<>(WeaponFamily.class);

	/** Light 0.5, heavy 0.2 movement while winding up and striking. */
	static final float LS = 0.5F, HS = 0.2F;

	static {
		// family, light W/A/R, combo length, finisher W/A/R ×, heavy W/A/R, charge +ticks → max, reach, arc, stamina L/H/C, poise factor
		put(WeaponFamily.UNARMED, 2, 2, 4, 3, 3, 2, 6, 1.3F, 8, 2, 10, 10, 2.0F, 2.5F, 30, 4, 12, 18, 0.6F);
		put(WeaponFamily.DAGGER, 2, 2, 5, 4, 4, 2, 8, 1.4F, 9, 2, 10, 12, 2.2F, 2.6F, 40, 5, 14, 20, 0.6F);
		put(WeaponFamily.SWORD, 4, 2, 6, 3, 6, 3, 9, 1.3F, 12, 3, 12, 20, 2.4F, 3.0F, 100, 8, 18, 28, 1.0F);
		put(WeaponFamily.GREATSWORD, 7, 3, 10, 3, 9, 3, 12, 1.4F, 16, 4, 16, 20, 2.4F, 3.5F, 140, 13, 26, 38, 1.6F);
		put(WeaponFamily.AXE, 5, 2, 8, 2, 6, 2, 10, 1.3F, 13, 3, 14, 20, 2.4F, 3.0F, 80, 10, 22, 32, 1.3F);
		put(WeaponFamily.MACE, 7, 2, 10, 2, 8, 3, 12, 1.4F, 16, 3, 16, 20, 2.4F, 3.0F, 60, 12, 26, 38, 1.8F);
		put(WeaponFamily.SPEAR, 4, 2, 7, 3, 6, 3, 9, 1.2F, 12, 3, 12, 16, 2.2F, 4.0F, 20, 9, 20, 30, 1.0F);
		put(WeaponFamily.STAFF, 5, 2, 8, 2, 5, 2, 8, 1.0F, 13, 3, 13, 20, 1.6F, 3.2F, 90, 8, 18, 18, 1.2F);
		// Bow bash: 2/2/6, blunt, short; heavy is the same bash a little slower.
		put(WeaponFamily.BOW, 2, 2, 6, 1, 2, 2, 6, 1.0F, 4, 2, 8, 10, 1.6F, 2.2F, 40, 4, 8, 10, 0.6F);
	}

	// Shared specials (COMBAT.md §4.1).
	public static final ActionDef GUARD_COUNTER = new ActionDef(3, 2, 6, 1.2F, 6, 3.0F, 100, LS, 0.3F);
	public static final ActionDef ROLL_ATTACK = new ActionDef(3, 2, 6, 1.1F, 6, 3.0F, 100, LS, 0.4F);
	public static final ActionDef EXECUTE = new ActionDef(6, 3, 14, 2.5F, 10, 3.0F, 100, 0, 0);
	// Dodge (COMBAT.md §6): roll 12 ticks, i-frames 1-7, recovery its last 4; backstep 8 ticks, i-frames 1-4.
	public static final ActionDef ROLL = new ActionDef(1, 7, 4, 0, 22, 0, 0, 1, 3.5F);
	public static final ActionDef BACKSTEP = new ActionDef(1, 4, 3, 0, 12, 0, 0, 1, 2.0F);
	// Arts (sneak + C): Whirlwind a 360° spin; Earthshaker a 4-block slam that staggers commons.
	public static final ActionDef WHIRLWIND = new ActionDef(4, 4, 10, 1.2F, 20, 3.0F, 360, 0.6F, 0);
	public static final ActionDef EARTHSHAKER = new ActionDef(14, 3, 16, 1.4F, 26, 4.0F, 360, HS, 0);
	public static final int WHIRLWIND_COOLDOWN = 160;
	public static final int EARTHSHAKER_COOLDOWN = 200;
	/** Dodge stamina by armour class (light, medium, heavy) and roll distance. */
	public static final float[] DODGE_COST = {18, 22, 28};
	public static final float[] DODGE_DISTANCE = {4.0F, 3.5F, 3.0F};
	public static final int SLIP_TICKS = 3;
	public static final float SLIP_REFUND = 12;
	public static final int SLIP_BONUS_TICKS = 20;
	public static final float SLIP_BONUS = 1.25F;
	public static final float DODGE_CANCEL_COST = 5;
	public static final float CHARGE_HOLD_DRAIN = 4;

	private Movesets() {
	}

	private static void put(WeaponFamily f, int lw, int la, int lr, int combo, int fw, int fa, int fr, float fm, int hw, int ha, int hr, int chargeTicks,
		float chargeMax, float reach, float arc, float sl, float sh, float sc, float poise) {
		List<ActionDef> lights = new ArrayList<>();
		for (int i = 0; i < combo - 1; i++) {
			lights.add(new ActionDef(lw, la, lr, 1.0F, sl, reach, arc, LS, 0.3F));
		}
		// The spear's finisher is a sweep (120°).
		lights.add(new ActionDef(fw, fa, fr, fm, sl, reach, f == WeaponFamily.SPEAR ? 120 : arc, LS, 0.3F));
		ActionDef heavy = new ActionDef(hw, ha, hr, 1.6F, sh, reach + (f == WeaponFamily.SPEAR ? 0.5F : 0), arc, HS, f == WeaponFamily.SPEAR ? 2.0F : 0.6F);
		SETS.put(f, new Moveset(f, List.copyOf(lights), heavy, chargeTicks, chargeMax, sc, poise));
	}

	public static Moveset of(WeaponFamily family) {
		return SETS.getOrDefault(family, SETS.get(WeaponFamily.UNARMED));
	}

	/** Every number a client must agree on, as text (hashed at login). */
	public static String canonical() {
		StringBuilder s = new StringBuilder("fray-combat-1\n");
		for (Moveset m : SETS.values()) {
			s.append(m.canonical()).append('\n');
		}
		for (ActionDef d : new ActionDef[] {GUARD_COUNTER, ROLL_ATTACK, EXECUTE, ROLL, BACKSTEP, WHIRLWIND, EARTHSHAKER}) {
			s.append(d.canonical()).append('\n');
		}
		return s.toString();
	}
}
