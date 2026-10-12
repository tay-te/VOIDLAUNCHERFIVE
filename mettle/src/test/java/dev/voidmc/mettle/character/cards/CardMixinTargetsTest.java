package dev.voidmc.mettle.character.cards;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import java.util.Arrays;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Every method the card mixins ({@code mixin.cards}) inject into exists with the parameters they expect, so a renamed
 * or re-shaped vanilla method fails here rather than in a player's game (the client tour checks the injections apply).
 */
class CardMixinTargetsTest {
	static {
		TestCards.PERKS.size();
	}

	private static void has(Class<?> owner, String name, Class<?>... params) {
		Method found = null;
		for (Class<?> c = owner; c != null && found == null; c = c.getSuperclass()) {
			for (Method m : c.getDeclaredMethods()) {
				if (m.getName().equals(name) && Arrays.equals(m.getParameterTypes(), params)) {
					found = m;
				}
			}
		}
		assertNotNull(found, owner.getSimpleName() + "#" + name + Arrays.toString(params) + " is missing");
	}

	@Test
	void targetsExist() {
		has(LivingEntity.class, "hurtServer", ServerLevel.class, DamageSource.class, float.class);
		has(LivingEntity.class, "addEffect", MobEffectInstance.class, Entity.class);
		has(LivingEntity.class, "decreaseAirSupply", int.class);
		has(LivingEntity.class, "handleRelativeFrictionAndCalculateMovement", Vec3.class, float.class);
		has(LivingEntity.class, "canBeAffected", MobEffectInstance.class);
		has(LivingEntity.class, "dropExperience", ServerLevel.class, Entity.class);
		has(LivingEntity.class, "getVisibilityPercent", ServerLevel.class, Entity.class);
		has(LivingEntity.class, "actuallyHurt", ServerLevel.class, DamageSource.class, float.class);
		has(LivingEntity.class, "getDamageAfterMagicAbsorb", DamageSource.class, float.class);
		has(LivingEntity.class, "getJumpPower", float.class);
		has(net.minecraft.world.entity.projectile.Projectile.class, "shootFromRotation", Entity.class, float.class, float.class, float.class, float.class, float.class);
		has(Entity.class, "getBlockSpeedFactor");
		has(Entity.class, "makeStuckInBlock", BlockState.class, Vec3.class);
		has(Player.class, "getDestroySpeed", BlockState.class);
		has(Player.class, "causeFoodExhaustion", float.class);
		has(Player.class, "dropEquipment", ServerLevel.class);
		has(ItemStack.class, "hurtAndBreak", int.class, LivingEntity.class, EquipmentSlot.class);
		has(AbstractArrow.class, "setPierceLevel", byte.class);
		has(AbstractArrow.class, "isInGround");
		has(ServerGamePacketListenerImpl.class, "handlePunch", ServerboundPunchPacket.class);
		has(ServerLevel.class, "gameEvent", Holder.class, Vec3.class, GameEvent.Context.class);
		has(Mob.class, "finalizeSpawn", ServerLevelAccessor.class, DifficultyInstance.class, EntitySpawnReason.class, SpawnGroupData.class);
	}
}
