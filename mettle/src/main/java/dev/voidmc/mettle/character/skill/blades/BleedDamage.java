package dev.voidmc.mettle.character.skill.blades;

import dev.voidmc.mettle.Mettle;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

/** Bleed's own damage type: through armour, no knockback, its own death message, and Blades XP for a bleed-out. */
public final class BleedDamage {
	public static final ResourceKey<DamageType> BLEED = ResourceKey.create(Registries.DAMAGE_TYPE, Mettle.id("bleed"));

	private BleedDamage() {
	}
}
