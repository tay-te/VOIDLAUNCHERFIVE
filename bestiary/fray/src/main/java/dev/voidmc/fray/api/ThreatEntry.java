package dev.voidmc.fray.api;

import net.minecraft.world.entity.LivingEntity;

/** @since 0.1.0 (2026-10-21) */
public record ThreatEntry(LivingEntity source, float threat) {
}
