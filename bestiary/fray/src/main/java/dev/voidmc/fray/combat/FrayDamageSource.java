package dev.voidmc.fray.combat;

import dev.voidmc.fray.Fray;
import dev.voidmc.fray.api.HitContext;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * A Fray hit handed to vanilla (pipeline stage 8). Its type {@code fray:combat} bypasses armour, enchantments, shields,
 * the damage cooldown and knockback (Fray did those itself), but not the Resistance effect. Status procs use
 * {@code fray:status}.
 */
public class FrayDamageSource extends DamageSource {
	public static final ResourceKey<DamageType> COMBAT = ResourceKey.create(Registries.DAMAGE_TYPE, Fray.id("combat"));
	public static final ResourceKey<DamageType> STATUS = ResourceKey.create(Registries.DAMAGE_TYPE, Fray.id("status"));

	public final @Nullable HitContext hit;

	public FrayDamageSource(Holder<DamageType> type, @Nullable Entity direct, @Nullable Entity causing, @Nullable HitContext hit) {
		super(type, direct, causing);
		this.hit = hit;
	}

	private static Object cachedAccess;
	private static Holder<DamageType> cachedCombat;

	public static FrayDamageSource combat(ServerLevel level, HitContext hit) {
		Object access = level.registryAccess();
		if (access != cachedAccess) {
			cachedCombat = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(COMBAT);
			cachedAccess = access;
		}
		return new FrayDamageSource(cachedCombat, hit.attacker(), hit.attacker(), hit);
	}

	public static FrayDamageSource status(ServerLevel level, @Nullable Entity source) {
		Holder<DamageType> type = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(STATUS);
		return new FrayDamageSource(type, source, source, null);
	}
}
