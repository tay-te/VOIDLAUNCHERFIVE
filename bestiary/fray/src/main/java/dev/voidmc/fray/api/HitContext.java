package dev.voidmc.fray.api;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Everything the pipeline knows about one hit (CONTRACTS.md §4.2). {@code situational} is the multiplier the action
 * already chose for stage 3 (riposte 2.0, plunge 1.5, a charged release's 1.6-2.4, ...); 1 means "let Fray decide from
 * the flags" (backstab and rolled crits are always decided by Fray).
 *
 * @since 0.1.0 (2026-10-21)
 */
public record HitContext(Entity attacker, @Nullable ItemStack weapon, Identifier action, DamageSpec spec, int flags, float charge,
	@Nullable Identifier hurtVolume, float situational, float poiseFactor) {
	public HitContext(Entity attacker, @Nullable ItemStack weapon, Identifier action, DamageSpec spec, int flags, float charge, @Nullable Identifier hurtVolume) {
		this(attacker, weapon, action, spec, flags, charge, hurtVolume, 1, 1);
	}

	public boolean has(int flag) {
		return (this.flags & flag) != 0;
	}

	public HitContext withFlags(int add) {
		return new HitContext(this.attacker, this.weapon, this.action, this.spec, this.flags | add, this.charge, this.hurtVolume, this.situational, this.poiseFactor);
	}

	public HitContext withSpec(DamageSpec spec) {
		return new HitContext(this.attacker, this.weapon, this.action, spec, this.flags, this.charge, this.hurtVolume, this.situational, this.poiseFactor);
	}
}
