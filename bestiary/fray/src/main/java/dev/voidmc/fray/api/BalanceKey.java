package dev.voidmc.fray.api;

import net.minecraft.resources.Identifier;

/**
 * An identifier-backed balance key (CONTRACTS.md §13.4), so new keys are additive.
 *
 * @since 0.1.0 (2026-10-21)
 */
public record BalanceKey(Identifier id) {
	public static final BalanceKey ACTION_SPEED = of("action_speed");
	public static final BalanceKey STAMINA_COST = of("stamina_cost");
	public static final BalanceKey PARRY_WINDOW = of("parry_window");
	public static final BalanceKey IFRAME_TICKS = of("iframe_ticks");
	public static final BalanceKey GUARD_EFFICIENCY = of("guard_efficiency");
	public static final BalanceKey POISE_DAMAGE = of("poise_damage");
	public static final BalanceKey STATUS_BUILDUP = of("status_buildup");
	public static final BalanceKey TECHNIQUE_COOLDOWN = of("technique_cooldown");
	public static final BalanceKey SPELL_COST = of("spell_cost");
	public static final BalanceKey SPELL_COOLDOWN = of("spell_cooldown");
	public static final BalanceKey PERCEPTION = of("perception");

	public static BalanceKey of(String path) {
		return new BalanceKey(Identifier.fromNamespaceAndPath("fray", path));
	}
}
