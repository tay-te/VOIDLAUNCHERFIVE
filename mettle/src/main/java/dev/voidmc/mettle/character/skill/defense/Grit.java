package dev.voidmc.mettle.character.skill.defense;

import dev.voidmc.mettle.character.FeatCounters;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.DefenseSkill;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.item.ItemStack;

/**
 * Refusing to go down: Last Stand (the "any two" mastery) and the two Defense feats, Still Standing and Pincushion,
 * with the quiet tallies that earn them.
 */
public final class Grit {
	public static final String BLOCKED_PROJECTILES = "block_projectile";
	public static final String SURVIVED_BIG_HITS = "survive_big_hit";

	private Grit() {
	}

	/**
	 * Last Stand: a killing blow leaves you at one heart with four seconds of Resistance IV, once every ten minutes.
	 * Called from ALLOW_DEATH; true means the player lives. A totem in hand goes first and Last Stand waits, and
	 * nothing defies /kill or the void.
	 */
	public static boolean lastStand(ServerPlayer player, DamageSource source) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !Perks.has(player, DefenseSkill.LAST_STAND)
			|| !Cooldowns.ready(player, Cooldowns.LAST_STAND) || holdsTotem(player)) {
			return false;
		}
		Cooldowns.start(player, Cooldowns.LAST_STAND, DefenseRules.LAST_STAND_COOLDOWN);
		player.setHealth(DefenseRules.LAST_STAND_HEALTH);
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, DefenseRules.LAST_STAND_RESISTANCE_TICKS, 3));
		// The blow is forgiven entirely: nothing Roll with the Blow held back comes due afterwards.
		Wounds.forget(player.getUUID());
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6f, 0.6f);
		DefenseSkill.feedback(player, DefenseSkill.LAST_STAND, Component.translatable(DefenseSkill.chip(DefenseSkill.LAST_STAND)));
		return true;
	}

	private static boolean holdsTotem(ServerPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			if (player.getItemInHand(hand).has(DataComponents.DEATH_PROTECTION)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Still Standing (feat): the first hit over 10 each minute is cut by a third. Skipped while the player is still
	 * flashing from the last hit, so a blow that would mostly bounce off does not spend it.
	 */
	public static float stillStanding(ServerPlayer player, DamageSource source, float damage) {
		if (damage <= DefenseRules.STILL_STANDING_OVER || player.damageCooldownTime > 10 || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
			|| !Perks.has(player, DefenseSkill.STILL_STANDING) || !Cooldowns.ready(player, Cooldowns.STILL_STANDING)) {
			return damage;
		}
		Cooldowns.start(player, Cooldowns.STILL_STANDING, DefenseRules.STILL_STANDING_COOLDOWN);
		float reduced = DefenseRules.stillStanding(damage);
		DefenseSkill.feedback(player, DefenseSkill.STILL_STANDING, Component.translatable(DefenseSkill.chip(DefenseSkill.STILL_STANDING),
			String.format(java.util.Locale.ROOT, "%.1f", damage - reduced)));
		return reduced;
	}

	public static void afterHurt(ServerPlayer player, DamageSource source, float baseTaken, boolean blocked) {
		// Still Standing is earned by living through one hit of 7½ hearts that no shield softened.
		if (!blocked && baseTaken >= DefenseRules.STILL_STANDING_FEAT && player.isAlive()) {
			FeatCounters.add(player, SURVIVED_BIG_HITS, 1);
		}
		if (!blocked || !(source.getDirectEntity() instanceof Projectile projectile)) {
			return;
		}
		FeatCounters.add(player, BLOCKED_PROJECTILES, 1);
		// Pincushion: half the arrows a shield catches are kept. Arrows only (never a trident, which would duplicate),
		// and skeletons' arrows too: the point is keeping what you catch.
		if ((projectile instanceof Arrow || projectile instanceof SpectralArrow) && projectile.isAlive()
			&& ((AbstractArrow) projectile).pickup != AbstractArrow.Pickup.CREATIVE_ONLY
			&& Perks.has(player, DefenseSkill.PINCUSHION) && player.getRandom().nextFloat() < DefenseRules.PINCUSHION_CHANCE) {
			ItemStack kept = ((AbstractArrow) projectile).getPickupItemStackOrigin().copyWithCount(1);
			projectile.discard();
			if (!player.getInventory().add(kept)) {
				player.spawnAtLocation(player.level(), kept);
			}
			DefenseSkill.feedback(player, DefenseSkill.PINCUSHION, Component.translatable(DefenseSkill.chip(DefenseSkill.PINCUSHION)), 20);
		}
	}
}
