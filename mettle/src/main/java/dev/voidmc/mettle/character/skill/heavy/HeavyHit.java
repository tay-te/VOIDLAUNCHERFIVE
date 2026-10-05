package dev.voidmc.mettle.character.skill.heavy;

import dev.voidmc.mettle.character.skill.Combat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import org.jspecify.annotations.Nullable;

/**
 * One melee swing in progress, from the start of {@code Player#attack} to its end, on the server thread. Vanilla works
 * out charge, critical and smash inside that method and never hands them on, so the mixin notes them here for the
 * perks that act after the hit lands (Bone Breaker, Stagger, Earthshaker, Overhead Blow, Berserker's lifesteal).
 */
public final class HeavyHit {
	private static @Nullable HeavyHit current;

	public final ServerPlayer player;
	public final Entity target;
	public final Combat.@Nullable Weapon weapon;
	public final boolean fullStrength;
	/** A mace smash: read at the start, because the mace resets the fall distance during the hit. */
	public final boolean smash;
	public final boolean axe;
	public final float healthBefore;
	public boolean crit;
	/** Overhead Blow doubled this critical (and will daze the target). */
	public boolean overhead;
	public boolean hurt;
	/** What reached the target's hurtServer after this tree's multipliers (Heft, Berserker). */
	public float damage;

	private HeavyHit(ServerPlayer player, Entity target) {
		this.player = player;
		this.target = target;
		this.weapon = Combat.melee(player.getMainHandItem());
		this.fullStrength = player.getAttackStrengthScale(0.5f) > 0.9f;
		this.smash = player.getMainHandItem().is(Items.MACE) && MaceItem.canSmashAttack(player);
		this.axe = player.getMainHandItem().is(ItemTags.AXES);
		this.healthBefore = target instanceof LivingEntity living ? living.getHealth() : 0f;
	}

	public static HeavyHit begin(ServerPlayer player, Entity target) {
		current = new HeavyHit(player, target);
		return current;
	}

	public static @Nullable HeavyHit current() {
		return current;
	}

	/** The swing in progress if it is this player's swing at this target. */
	public static @Nullable HeavyHit of(ServerPlayer player, Entity target) {
		HeavyHit hit = current;
		return hit != null && hit.player == player && hit.target == target ? hit : null;
	}

	public static @Nullable HeavyHit end(ServerPlayer player) {
		HeavyHit hit = current;
		if (hit == null || hit.player != player) {
			return null;
		}
		current = null;
		return hit;
	}

	public boolean heavy() {
		return this.weapon == Combat.Weapon.HEAVY;
	}
}
