package dev.voidmc.fray.combat;

import dev.voidmc.fray.api.ResourceService;
import dev.voidmc.fray.combat.action.PlayerCombat;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** {@link ResourceService} over {@link PlayerCombat}'s stamina and {@link CombatState}'s poise. */
public final class Resources implements ResourceService {
	public static final Resources INSTANCE = new Resources();

	private Resources() {
	}

	private static PlayerCombat pc(ServerPlayer p) {
		return CombatState.of(p).player;
	}

	@Override
	public float stamina(ServerPlayer player) {
		return pc(player).stamina.value;
	}

	@Override
	public float maxStamina(ServerPlayer player) {
		return pc(player).maxStamina();
	}

	@Override
	public boolean spend(ServerPlayer player, float amount, Identifier action) {
		return pc(player).spend(amount, action);
	}

	@Override
	public void restore(ServerPlayer player, float amount) {
		pc(player).stamina.restore(amount);
	}

	@Override
	public boolean winded(ServerPlayer player) {
		return pc(player).winded();
	}

	@Override
	public float poise(LivingEntity entity) {
		return CombatState.of(entity).poise.value;
	}

	@Override
	public float maxPoise(LivingEntity entity) {
		return CombatState.of(entity).poise.max;
	}

	@Override
	public boolean inCombat(ServerPlayer player) {
		return pc(player).inCombat();
	}
}
